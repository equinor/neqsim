package neqsim.process.operations.envelope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Evaluates the operational envelope of a process from registered capacity constraints.
 *
 * <p>
 * The evaluator is intentionally a thin layer above {@link EquipmentCapacityStrategyRegistry}. It
 * ranks margins and produces advisory predictions, while equipment packages remain the source of
 * truth for physical constraints.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalEnvelopeEvaluator {

  /** Default prediction horizon of one hour. */
  public static final double DEFAULT_PREDICTION_HORIZON_SECONDS = 3600.0;

  /** Minimum trend confidence needed for a trip prediction. */
  public static final double DEFAULT_MIN_PREDICTION_CONFIDENCE = 0.15;

  /**
   * Private constructor for utility class.
   */
  private OperationalEnvelopeEvaluator() {}

  /**
   * Evaluates a process with default prediction settings.
   *
   * @param process process system to evaluate
   * @return operational envelope report
   */
  public static OperationalEnvelopeReport evaluate(ProcessSystem process) {
    return evaluate(process, Collections.<String, MarginTrendTracker>emptyMap(),
        DEFAULT_PREDICTION_HORIZON_SECONDS, true);
  }

  /**
   * Evaluates a process with optional margin history.
   *
   * @param process process system to evaluate
   * @param history margin history keyed by margin key
   * @param predictionHorizonSeconds maximum time horizon for predictions in seconds
   * @param includeMitigations true to include generic mitigation suggestions
   * @return operational envelope report
   */
  public static OperationalEnvelopeReport evaluate(ProcessSystem process,
      Map<String, MarginTrendTracker> history, double predictionHorizonSeconds,
      boolean includeMitigations) {
    requireProcess(process);
    long start = System.currentTimeMillis();
    List<OperationalMargin> margins = collectMargins(process);
    Map<String, MarginTrendTracker> trackers = copyHistory(history);
    List<TripPrediction> predictions = buildPredictions(margins, trackers,
        predictionHorizonSeconds, start / 1000.0);
    List<MitigationSuggestion> suggestions = includeMitigations ? buildMitigations(margins)
        : new ArrayList<MitigationSuggestion>();
    double elapsed = (System.currentTimeMillis() - start) / 1000.0;
    return new OperationalEnvelopeReport(System.currentTimeMillis(), elapsed, margins, predictions,
        suggestions);
  }

  /**
   * Collects operational margins from all process units.
   *
   * @param process process system to inspect
   * @return ranked margin list
   */
  public static List<OperationalMargin> collectMargins(ProcessSystem process) {
    requireProcess(process);
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    List<OperationalMargin> margins = new ArrayList<OperationalMargin>();
    List<ProcessEquipmentInterface> units = process.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      Map<String, CapacityConstraint> constraints = registry.getConstraints(unit);
      for (CapacityConstraint constraint : constraints.values()) {
        if (constraint != null && constraint.isEnabled()) {
          margins.add(OperationalMargin.fromConstraint(unit.getName(), constraint));
        }
      }
    }
    Collections.sort(margins);
    return margins;
  }

  /**
   * Builds trip predictions from margin history.
   *
   * @param margins current margin list
   * @param trackers margin history trackers
   * @param predictionHorizonSeconds maximum prediction horizon in seconds
   * @param currentTimestampSeconds current timestamp in seconds
   * @return sorted prediction list
   */
  private static List<TripPrediction> buildPredictions(List<OperationalMargin> margins,
      Map<String, MarginTrendTracker> trackers, double predictionHorizonSeconds,
      double currentTimestampSeconds) {
    List<TripPrediction> predictions = new ArrayList<TripPrediction>();
    double horizon = predictionHorizonSeconds > 0.0 ? predictionHorizonSeconds
        : DEFAULT_PREDICTION_HORIZON_SECONDS;
    for (OperationalMargin margin : margins) {
      MarginTrendTracker tracker = trackers.get(margin.getKey());
      if (tracker == null) {
        continue;
      }
      tracker.addCurrentMargin(nextTimestampForTracker(tracker, currentTimestampSeconds), margin);
      double timeToLimit = tracker.estimateTimeToLimitSeconds();
      double confidence = tracker.estimateConfidence();
      if (!Double.isNaN(timeToLimit) && timeToLimit <= horizon
          && confidence >= DEFAULT_MIN_PREDICTION_CONFIDENCE) {
        predictions.add(new TripPrediction(margin, timeToLimit, confidence,
            tracker.getTrendDescription()));
      }
    }
    Collections.sort(predictions);
    return predictions;
  }

  /**
   * Builds mitigation suggestions for warning or worse margins.
   *
   * @param margins current margin list
   * @return sorted mitigation suggestions
   */
  private static List<MitigationSuggestion> buildMitigations(List<OperationalMargin> margins) {
    List<MitigationSuggestion> suggestions = new ArrayList<MitigationSuggestion>();
    for (OperationalMargin margin : margins) {
      if (margin.getStatus().getRank() >= OperationalMargin.Status.NARROWING.getRank()) {
        suggestions.add(MitigationSuggestion.fromMargin(margin));
      }
    }
    Collections.sort(suggestions);
    return suggestions;
  }

  /**
   * Selects a timestamp for the current sample without mixing relative and epoch histories.
   *
   * @param tracker trend tracker with optional existing samples
   * @param fallbackTimestampSeconds fallback timestamp in seconds
   * @return timestamp for the current sample
   */
  private static double nextTimestampForTracker(MarginTrendTracker tracker,
      double fallbackTimestampSeconds) {
    MarginTrendTracker.MarginSample latest = tracker.getLatestSample();
    if (latest == null) {
      return fallbackTimestampSeconds;
    }
    return latest.getTimestampSeconds() + 1.0;
  }

  /**
   * Copies history into a mutable map.
   *
   * @param history source history, may be null
   * @return mutable tracker map
   */
  private static Map<String, MarginTrendTracker> copyHistory(
      Map<String, MarginTrendTracker> history) {
    if (history == null) {
      return new LinkedHashMap<String, MarginTrendTracker>();
    }
    return new LinkedHashMap<String, MarginTrendTracker>(history);
  }

  /**
   * Validates a process argument.
   *
   * @param process process system to validate
   * @throws IllegalArgumentException if the process is null
   */
  private static void requireProcess(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
  }
}