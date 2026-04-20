package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Main orchestrator for the Operating Envelope and Trip Prevention system.
 *
 * <p>
 * The {@code OperatingEnvelopeAgent} is the central coordinator that ties together all envelope
 * components. Each evaluation cycle it:
 * </p>
 * <ol>
 * <li>Re-runs the process model (if auto-run is enabled)</li>
 * <li>Evaluates all operating margins via {@link ProcessOperatingEnvelope}</li>
 * <li>Tracks margin trends via {@link MarginTracker}</li>
 * <li>Generates trip predictions for degrading margins</li>
 * <li>Looks up mitigation actions from {@link MitigationStrategy}</li>
 * <li>Optionally analyzes composition changes via {@link CompositionChangeAnalyzer}</li>
 * <li>Produces an {@link AgentEvaluationResult}</li>
 * </ol>
 *
 * <p>
 * <strong>IMPORTANT: This agent is advisory only.</strong> It never writes to the control system or
 * modifies process setpoints. All recommendations are for operator decision support.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * ProcessSystem process = buildAndRunProcess();
 *
 * OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
 * agent.setCompositionBaseline(designFluid);
 *
 * // In a monitoring loop:
 * while (monitoring) {
 *   AgentEvaluationResult result = agent.evaluate();
 *   if (result.hasImminentTrip()) {
 *     alertOperator(result);
 *   }
 *   publishToDashboard(result.toJson());
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class OperatingEnvelopeAgent implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Default trend confidence threshold for generating trip predictions. */
  private static final double DEFAULT_TREND_CONFIDENCE_THRESHOLD = 0.3;
  /** Default minimum R-squared for trend to be considered reliable. */
  private static final double DEFAULT_R_SQUARED_THRESHOLD = 0.5;
  /** Margin percent below which a trip prediction is generated regardless of trend. */
  private static final double STATIC_TRIP_THRESHOLD_PERCENT = 5.0;

  private final ProcessSystem processSystem;
  private final ProcessOperatingEnvelope envelope;
  private final MitigationStrategy mitigationStrategy;
  private final Map<String, MarginTracker> trackers;

  private CompositionChangeAnalyzer compositionAnalyzer;
  private SystemInterface currentFeedFluid;
  private double operatingTempC;
  private double operatingPressBar;

  private int cycleNumber;
  private boolean autoRunProcess;
  private double trendConfidenceThreshold;
  private double rSquaredThreshold;
  private int trackerWindowSize;

  /**
   * Creates an OperatingEnvelopeAgent for the given process system.
   *
   * @param processSystem the process system to monitor
   */
  public OperatingEnvelopeAgent(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.envelope = new ProcessOperatingEnvelope(processSystem);
    this.mitigationStrategy = new MitigationStrategy();
    this.trackers = new HashMap<String, MarginTracker>();
    this.cycleNumber = 0;
    this.autoRunProcess = false;
    this.trendConfidenceThreshold = DEFAULT_TREND_CONFIDENCE_THRESHOLD;
    this.rSquaredThreshold = DEFAULT_R_SQUARED_THRESHOLD;
    this.trackerWindowSize = 60;
    this.operatingTempC = 25.0;
    this.operatingPressBar = 50.0;
  }

  /**
   * Runs a single evaluation cycle and returns the complete result.
   *
   * <p>
   * This is the primary method called by monitoring loops. Each call increments the cycle counter,
   * evaluates all margins, updates trends, generates predictions and mitigations, and packages
   * everything into an immutable {@link AgentEvaluationResult}.
   * </p>
   *
   * @return complete evaluation result for this cycle
   */
  public AgentEvaluationResult evaluate() {
    long startTime = System.currentTimeMillis();
    cycleNumber++;

    // Step 1: Re-run process if configured
    if (autoRunProcess) {
      try {
        processSystem.run();
      } catch (Exception e) {
        return buildErrorResult("Process run failed: " + e.getMessage(), startTime);
      }
    }

    // Step 2: Evaluate all margins
    double timestampSeconds = cycleNumber * 1.0;
    envelope.evaluateAndTrack(timestampSeconds);

    // Step 3: Update our local trackers
    updateTrackers(timestampSeconds);

    // Step 4: Generate trip predictions from trends
    List<TripPrediction> predictions = generateTripPredictions();

    // Step 5: Look up mitigation actions for degrading margins
    List<MitigationAction> actions = generateMitigationActions();

    // Step 6: Composition analysis (if configured)
    CompositionChangeAnalyzer.ImpactReport compImpact = null;
    if (compositionAnalyzer != null && currentFeedFluid != null) {
      compImpact =
          compositionAnalyzer.analyzeImpact(currentFeedFluid, operatingTempC, operatingPressBar);
    }

    // Step 7: Build result
    List<OperatingMargin> rankedMargins = envelope.getAllMargins();
    ProcessOperatingEnvelope.EnvelopeStatus status = envelope.getOverallStatus();

    double evalTime = (System.currentTimeMillis() - startTime) / 1000.0;
    String summary = buildSummaryMessage(status, rankedMargins, predictions);

    return new AgentEvaluationResult.Builder(status, cycleNumber).timestamp(startTime)
        .evaluationTime(evalTime).margins(rankedMargins).tripPredictions(predictions)
        .mitigationActions(actions).compositionImpact(compImpact).summary(summary).build();
  }

  /**
   * Updates margin trackers for all current margins.
   *
   * @param timestampSeconds current timestamp
   */
  private void updateTrackers(double timestampSeconds) {
    for (OperatingMargin margin : envelope.getAllMargins()) {
      String key = margin.getKey();
      MarginTracker tracker = trackers.get(key);
      if (tracker == null) {
        tracker = new MarginTracker(margin, trackerWindowSize);
        trackers.put(key, tracker);
      }
      tracker.recordSample(timestampSeconds);
    }
  }

  /**
   * Generates trip predictions based on margin trends and current values.
   *
   * @return list of trip predictions sorted by severity
   */
  private List<TripPrediction> generateTripPredictions() {
    List<TripPrediction> predictions = new ArrayList<TripPrediction>();

    for (Map.Entry<String, MarginTracker> entry : trackers.entrySet()) {
      MarginTracker tracker = entry.getValue();
      OperatingMargin margin = tracker.getMargin();

      // Skip margins that are comfortably within range
      if (margin.getStatus() == OperatingMargin.Status.NORMAL && margin.getMarginPercent() > 20.0) {
        continue;
      }

      // Generate prediction if trend is degrading with sufficient confidence
      if (tracker.isTrendValid() && tracker.getTrendRSquared() >= rSquaredThreshold
          && (tracker.getTrendDirection() == MarginTracker.TrendDirection.DEGRADING
              || tracker.getTrendDirection() == MarginTracker.TrendDirection.RAPIDLY_DEGRADING)) {

        double ttb = tracker.getTimeToBreachSeconds();
        if (!Double.isInfinite(ttb) && ttb > 0) {
          double confidence =
              Math.min(tracker.getTrendRSquared(), 1.0 - (margin.getMarginPercent() / 100.0));
          confidence = Math.max(trendConfidenceThreshold, confidence);

          String trendDesc = String.format("%s margin trending %s at %.2e/s (R^2=%.2f)",
              margin.getMarginType(), tracker.getTrendDirection(), tracker.getMarginRateOfChange(),
              tracker.getTrendRSquared());

          predictions.add(new TripPrediction(margin.getEquipmentName(), margin.getMarginType(),
              margin.getMarginPercent(), ttb, confidence, trendDesc));
        }
      }

      // Static prediction for very low margins regardless of trend
      if (margin.getMarginPercent() <= STATIC_TRIP_THRESHOLD_PERCENT
          && margin.getStatus() != OperatingMargin.Status.NORMAL) {
        boolean alreadyPredicted = false;
        for (TripPrediction existing : predictions) {
          if (existing.getEquipmentName().equals(margin.getEquipmentName())
              && existing.getMarginType() == margin.getMarginType()) {
            alreadyPredicted = true;
            break;
          }
        }
        if (!alreadyPredicted) {
          predictions.add(new TripPrediction(margin.getEquipmentName(), margin.getMarginType(),
              margin.getMarginPercent(), 300.0, 0.6, TripPrediction.Severity.HIGH,
              "Static: margin below " + STATIC_TRIP_THRESHOLD_PERCENT + "%"));
        }
      }
    }

    Collections.sort(predictions);
    return predictions;
  }

  /**
   * Generates mitigation actions for margins approaching their limits.
   *
   * @return list of actions sorted by priority
   */
  private List<MitigationAction> generateMitigationActions() {
    List<MitigationAction> allActions = new ArrayList<MitigationAction>();

    for (OperatingMargin margin : envelope.getAllMargins()) {
      // Only generate actions for margins in ADVISORY or worse status
      if (margin.getStatus() == OperatingMargin.Status.NORMAL) {
        continue;
      }

      List<MitigationAction> playbook = mitigationStrategy.getActionsForMargin(margin);
      for (MitigationAction action : playbook) {
        action.setTriggeringMarginKey(margin.getKey());
        // Adjust confidence based on margin severity
        double conf = 0.5;
        switch (margin.getStatus()) {
          case VIOLATED:
            conf = 0.95;
            break;
          case CRITICAL:
            conf = 0.85;
            break;
          case WARNING:
            conf = 0.70;
            break;
          case ADVISORY:
            conf = 0.50;
            break;
          default:
            conf = 0.30;
        }
        action.setConfidenceLevel(conf);
        allActions.add(action);
      }
    }

    // Add composition-specific actions if significant drift detected
    if (compositionAnalyzer != null && currentFeedFluid != null) {
      try {
        CompositionChangeAnalyzer.ImpactReport impact =
            compositionAnalyzer.analyzeImpact(currentFeedFluid, operatingTempC, operatingPressBar);
        if (impact.hasSignificantImpact()) {
          List<MitigationAction> compActions =
              mitigationStrategy.getPlaybook(MitigationStrategy.COMPOSITION_DRIFT);
          for (MitigationAction action : compActions) {
            action.setTriggeringMarginKey("composition.drift");
            action.setConfidenceLevel(0.7);
            allActions.add(action);
          }
        }
      } catch (Exception e) {
        // Composition analysis failure — non-fatal
      }
    }

    Collections.sort(allActions);
    return allActions;
  }

  /**
   * Builds a human-readable summary message for the evaluation.
   *
   * @param status overall status
   * @param margins all margins
   * @param predictions trip predictions
   * @return summary string
   */
  private String buildSummaryMessage(ProcessOperatingEnvelope.EnvelopeStatus status,
      List<OperatingMargin> margins, List<TripPrediction> predictions) {
    StringBuilder sb = new StringBuilder();
    sb.append("Cycle ").append(cycleNumber).append(": ");

    int criticalCount = 0;
    for (OperatingMargin m : margins) {
      if (m.getStatus() == OperatingMargin.Status.CRITICAL
          || m.getStatus() == OperatingMargin.Status.VIOLATED) {
        criticalCount++;
      }
    }

    switch (status) {
      case NORMAL:
        sb.append("All ").append(margins.size()).append(" margins within normal range.");
        break;
      case NARROWING:
        sb.append("Operating envelope narrowing. Monitor closely.");
        break;
      case WARNING:
        sb.append(criticalCount).append(" margin(s) in warning range. Preventive action advised.");
        break;
      case CRITICAL:
        sb.append(criticalCount).append(" margin(s) critical. Immediate attention required.");
        break;
      case VIOLATED:
        sb.append("ENVELOPE VIOLATED. ").append(criticalCount).append(" limit(s) exceeded.");
        break;
      default:
        sb.append("Status: ").append(status);
    }

    if (!predictions.isEmpty()) {
      sb.append(" ").append(predictions.size()).append(" trip prediction(s).");
    }

    return sb.toString();
  }

  /**
   * Builds an error result when the process fails to run.
   *
   * @param errorMessage the error message
   * @param startTime when the evaluation started
   * @return error result
   */
  private AgentEvaluationResult buildErrorResult(String errorMessage, long startTime) {
    double evalTime = (System.currentTimeMillis() - startTime) / 1000.0;
    return new AgentEvaluationResult.Builder(ProcessOperatingEnvelope.EnvelopeStatus.VIOLATED,
        cycleNumber).timestamp(startTime).evaluationTime(evalTime).summary("ERROR: " + errorMessage)
            .build();
  }

  /**
   * Sets the baseline fluid composition for drift analysis.
   *
   * @param baselineFluid the design or reference composition
   */
  public void setCompositionBaseline(SystemInterface baselineFluid) {
    this.compositionAnalyzer = new CompositionChangeAnalyzer(baselineFluid);
  }

  /**
   * Updates the current feed fluid for composition analysis.
   *
   * @param feedFluid the current feed composition
   */
  public void updateFeedFluid(SystemInterface feedFluid) {
    this.currentFeedFluid = feedFluid;
  }

  /**
   * Sets the operating conditions for composition impact calculations.
   *
   * @param tempC temperature in degrees C
   * @param pressBar pressure in bara
   */
  public void setOperatingConditions(double tempC, double pressBar) {
    this.operatingTempC = tempC;
    this.operatingPressBar = pressBar;
  }

  /**
   * Enables or disables automatic process re-run before each evaluation.
   *
   * @param autoRun true to re-run the process model each cycle
   */
  public void setAutoRunProcess(boolean autoRun) {
    this.autoRunProcess = autoRun;
  }

  /**
   * Sets the minimum R-squared value for trend-based predictions.
   *
   * @param threshold R-squared threshold (0.0 to 1.0)
   */
  public void setRSquaredThreshold(double threshold) {
    this.rSquaredThreshold = Math.max(0.0, Math.min(1.0, threshold));
  }

  /**
   * Sets the trend confidence threshold for generating trip predictions.
   *
   * @param threshold confidence threshold (0.0 to 1.0)
   */
  public void setTrendConfidenceThreshold(double threshold) {
    this.trendConfidenceThreshold = Math.max(0.0, Math.min(1.0, threshold));
  }

  /**
   * Sets the window size for margin trackers (number of samples for trend fitting).
   *
   * @param windowSize window size (minimum 2)
   */
  public void setTrackerWindowSize(int windowSize) {
    this.trackerWindowSize = Math.max(2, windowSize);
  }

  /**
   * Enables or disables hydrate formation checking.
   *
   * @param enabled true to enable hydrate checks
   */
  public void setHydrateCheckEnabled(boolean enabled) {
    envelope.setHydrateCheckEnabled(enabled);
  }

  /**
   * Enables or disables hydrocarbon dew point checking.
   *
   * @param enabled true to enable dew point checks
   */
  public void setDewPointCheckEnabled(boolean enabled) {
    envelope.setDewPointCheckEnabled(enabled);
  }

  /**
   * Adds a custom operating margin to the envelope.
   *
   * @param margin the custom margin to add
   */
  public void addCustomMargin(OperatingMargin margin) {
    envelope.addCustomMargin(margin);
  }

  /**
   * Registers a custom mitigation strategy for a threat type.
   *
   * @param threatType the threat type identifier
   * @param actions list of mitigation actions
   */
  public void registerMitigationPlaybook(String threatType, List<MitigationAction> actions) {
    mitigationStrategy.registerStrategy(threatType, actions);
  }

  /**
   * Returns the underlying process operating envelope.
   *
   * @return the envelope
   */
  public ProcessOperatingEnvelope getEnvelope() {
    return envelope;
  }

  /**
   * Returns the mitigation strategy library.
   *
   * @return the strategy library
   */
  public MitigationStrategy getMitigationStrategy() {
    return mitigationStrategy;
  }

  /**
   * Returns the underlying process system.
   *
   * @return process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Returns the current evaluation cycle number.
   *
   * @return cycle number
   */
  public int getCycleNumber() {
    return cycleNumber;
  }

  /**
   * Resets the agent state (cycle counter, trackers, composition baseline).
   */
  public void reset() {
    cycleNumber = 0;
    trackers.clear();
    envelope.resetTrackers();
    if (compositionAnalyzer != null) {
      compositionAnalyzer.resetBaseline();
    }
  }
}
