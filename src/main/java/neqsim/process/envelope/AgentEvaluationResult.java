package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a single evaluation cycle of the Operating Envelope Agent.
 *
 * <p>
 * An {@code AgentEvaluationResult} captures the complete output of one evaluation cycle: all
 * operating margins ranked by criticality, trip predictions, recommended mitigation actions, and
 * the overall process status. This object is the primary output interface for operator dashboards,
 * logging, and higher-level decision support systems.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * AgentEvaluationResult result = agent.evaluate();
 * if (result.getOverallStatus() == ProcessOperatingEnvelope.EnvelopeStatus.CRITICAL) {
 *   for (TripPrediction pred : result.getTripPredictions()) {
 *     System.out.println("Trip predicted: " + pred);
 *   }
 *   for (MitigationAction action : result.getMitigationActions()) {
 *     System.out.println("Recommendation: " + action);
 *   }
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AgentEvaluationResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final long timestampMillis;
  private final double evaluationTimeSeconds;
  private final ProcessOperatingEnvelope.EnvelopeStatus overallStatus;
  private final List<OperatingMargin> rankedMargins;
  private final List<TripPrediction> tripPredictions;
  private final List<MitigationAction> mitigationActions;
  private final CompositionChangeAnalyzer.ImpactReport compositionImpact;
  private final int evaluationCycleNumber;
  private final String summaryMessage;

  /**
   * Creates an evaluation result using the builder.
   *
   * @param builder the builder containing all result data
   */
  private AgentEvaluationResult(Builder builder) {
    this.timestampMillis = builder.timestampMillis;
    this.evaluationTimeSeconds = builder.evaluationTimeSeconds;
    this.overallStatus = builder.overallStatus;
    this.rankedMargins =
        Collections.unmodifiableList(new ArrayList<OperatingMargin>(builder.rankedMargins));
    this.tripPredictions =
        Collections.unmodifiableList(new ArrayList<TripPrediction>(builder.tripPredictions));
    this.mitigationActions =
        Collections.unmodifiableList(new ArrayList<MitigationAction>(builder.mitigationActions));
    this.compositionImpact = builder.compositionImpact;
    this.evaluationCycleNumber = builder.evaluationCycleNumber;
    this.summaryMessage = builder.summaryMessage;
  }

  /**
   * Returns the timestamp when this evaluation was performed (epoch millis).
   *
   * @return timestamp in milliseconds since epoch
   */
  public long getTimestampMillis() {
    return timestampMillis;
  }

  /**
   * Returns the time taken to perform the evaluation in seconds.
   *
   * @return evaluation time in seconds
   */
  public double getEvaluationTimeSeconds() {
    return evaluationTimeSeconds;
  }

  /**
   * Returns the overall process envelope status.
   *
   * @return envelope status
   */
  public ProcessOperatingEnvelope.EnvelopeStatus getOverallStatus() {
    return overallStatus;
  }

  /**
   * Returns all operating margins ranked by criticality (most critical first).
   *
   * @return unmodifiable list of ranked margins
   */
  public List<OperatingMargin> getRankedMargins() {
    return rankedMargins;
  }

  /**
   * Returns the trip predictions, sorted by severity.
   *
   * @return unmodifiable list of trip predictions
   */
  public List<TripPrediction> getTripPredictions() {
    return tripPredictions;
  }

  /**
   * Returns the recommended mitigation actions, sorted by priority.
   *
   * @return unmodifiable list of mitigation actions
   */
  public List<MitigationAction> getMitigationActions() {
    return mitigationActions;
  }

  /**
   * Returns the composition impact report, if composition analysis was performed.
   *
   * @return impact report, or null if no composition analysis was done
   */
  public CompositionChangeAnalyzer.ImpactReport getCompositionImpact() {
    return compositionImpact;
  }

  /**
   * Returns the evaluation cycle number (monotonically increasing).
   *
   * @return cycle number
   */
  public int getEvaluationCycleNumber() {
    return evaluationCycleNumber;
  }

  /**
   * Returns a human-readable summary message of the evaluation.
   *
   * @return summary message
   */
  public String getSummaryMessage() {
    return summaryMessage;
  }

  /**
   * Returns the number of margins that are in WARNING, CRITICAL, or VIOLATED status.
   *
   * @return count of margins requiring attention
   */
  public int getCriticalMarginCount() {
    int count = 0;
    for (OperatingMargin m : rankedMargins) {
      OperatingMargin.Status s = m.getStatus();
      if (s == OperatingMargin.Status.WARNING || s == OperatingMargin.Status.CRITICAL
          || s == OperatingMargin.Status.VIOLATED) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns true if any trip is predicted within the next 10 minutes.
   *
   * @return true if imminent or high-severity trip is predicted
   */
  public boolean hasImminentTrip() {
    for (TripPrediction pred : tripPredictions) {
      if (pred.getSeverity() == TripPrediction.Severity.IMMINENT
          || pred.getSeverity() == TripPrediction.Severity.HIGH) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if any mitigation action has IMMEDIATE priority.
   *
   * @return true if immediate action is recommended
   */
  public boolean hasImmediateActions() {
    for (MitigationAction action : mitigationActions) {
      if (action.getPriority() == MitigationAction.Priority.IMMEDIATE) {
        return true;
      }
    }
    return false;
  }

  /**
   * Exports the evaluation result as a JSON string.
   *
   * <p>
   * This produces a structured JSON object suitable for dashboard consumption, logging, or
   * integration with external systems. The format uses standard JSON without external library
   * dependencies.
   * </p>
   *
   * @return JSON string representation
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"timestamp\": ").append(timestampMillis).append(",\n");
    sb.append("  \"cycleNumber\": ").append(evaluationCycleNumber).append(",\n");
    sb.append("  \"evaluationTimeSeconds\": ").append(evaluationTimeSeconds).append(",\n");
    sb.append("  \"overallStatus\": \"").append(overallStatus).append("\",\n");
    sb.append("  \"summary\": \"").append(escapeJson(summaryMessage)).append("\",\n");
    sb.append("  \"criticalMarginCount\": ").append(getCriticalMarginCount()).append(",\n");
    sb.append("  \"totalMarginCount\": ").append(rankedMargins.size()).append(",\n");
    sb.append("  \"tripPredictionCount\": ").append(tripPredictions.size()).append(",\n");
    sb.append("  \"mitigationActionCount\": ").append(mitigationActions.size()).append(",\n");
    sb.append("  \"hasImminentTrip\": ").append(hasImminentTrip()).append(",\n");

    // Margins array
    sb.append("  \"margins\": [\n");
    for (int i = 0; i < rankedMargins.size(); i++) {
      OperatingMargin m = rankedMargins.get(i);
      sb.append("    {\"key\": \"").append(escapeJson(m.getKey()));
      sb.append("\", \"status\": \"").append(m.getStatus());
      sb.append("\", \"marginPercent\": ").append(String.format("%.2f", m.getMarginPercent()));
      sb.append(", \"currentValue\": ").append(m.getCurrentValue());
      sb.append(", \"limitValue\": ").append(m.getLimitValue());
      sb.append(", \"unit\": \"").append(escapeJson(m.getUnit())).append("\"}");
      if (i < rankedMargins.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");

    // Trip predictions array
    sb.append("  \"tripPredictions\": [\n");
    for (int i = 0; i < tripPredictions.size(); i++) {
      TripPrediction p = tripPredictions.get(i);
      sb.append("    {\"equipment\": \"").append(escapeJson(p.getEquipmentName()));
      sb.append("\", \"marginType\": \"").append(p.getMarginType());
      sb.append("\", \"severity\": \"").append(p.getSeverity());
      sb.append("\", \"timeToTripMin\": ")
          .append(String.format("%.1f", p.getEstimatedTimeToTripMinutes()));
      sb.append(", \"confidence\": ").append(String.format("%.2f", p.getConfidence()));
      sb.append("}");
      if (i < tripPredictions.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");

    // Mitigation actions array
    sb.append("  \"mitigationActions\": [\n");
    for (int i = 0; i < mitigationActions.size(); i++) {
      MitigationAction a = mitigationActions.get(i);
      sb.append("    {\"priority\": \"").append(a.getPriority());
      sb.append("\", \"description\": \"").append(escapeJson(a.getDescription()));
      sb.append("\", \"equipment\": \"").append(escapeJson(a.getTargetEquipment()));
      sb.append("\", \"variable\": \"").append(escapeJson(a.getTargetVariable()));
      sb.append("\", \"suggestedValue\": ").append(a.getSuggestedValue());
      sb.append(", \"unit\": \"").append(escapeJson(a.getUnit())).append("\"}");
      if (i < mitigationActions.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");

    sb.append("}");
    return sb.toString();
  }

  /**
   * Escapes special characters for JSON strings.
   *
   * @param input the string to escape
   * @return escaped string
   */
  private String escapeJson(String input) {
    if (input == null) {
      return "";
    }
    return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        .replace("\r", "\\r").replace("\t", "\\t");
  }

  /**
   * Returns a formatted summary string.
   *
   * @return summary
   */
  @Override
  public String toString() {
    return String.format(
        "AgentEvaluationResult[cycle=%d, status=%s, margins=%d (critical=%d), "
            + "trips=%d, actions=%d]",
        evaluationCycleNumber, overallStatus, rankedMargins.size(), getCriticalMarginCount(),
        tripPredictions.size(), mitigationActions.size());
  }

  /**
   * Builder for creating {@link AgentEvaluationResult} instances.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Builder {
    private long timestampMillis;
    private double evaluationTimeSeconds;
    private ProcessOperatingEnvelope.EnvelopeStatus overallStatus;
    private List<OperatingMargin> rankedMargins;
    private List<TripPrediction> tripPredictions;
    private List<MitigationAction> mitigationActions;
    private CompositionChangeAnalyzer.ImpactReport compositionImpact;
    private int evaluationCycleNumber;
    private String summaryMessage;

    /**
     * Creates a builder with required fields.
     *
     * @param overallStatus the overall envelope status
     * @param cycleNumber the evaluation cycle number
     */
    public Builder(ProcessOperatingEnvelope.EnvelopeStatus overallStatus, int cycleNumber) {
      this.overallStatus = overallStatus;
      this.evaluationCycleNumber = cycleNumber;
      this.timestampMillis = System.currentTimeMillis();
      this.evaluationTimeSeconds = 0.0;
      this.rankedMargins = new ArrayList<OperatingMargin>();
      this.tripPredictions = new ArrayList<TripPrediction>();
      this.mitigationActions = new ArrayList<MitigationAction>();
      this.summaryMessage = "";
    }

    /**
     * Sets the evaluation timestamp.
     *
     * @param timestampMillis epoch millis
     * @return this builder
     */
    public Builder timestamp(long timestampMillis) {
      this.timestampMillis = timestampMillis;
      return this;
    }

    /**
     * Sets the evaluation time.
     *
     * @param seconds evaluation duration
     * @return this builder
     */
    public Builder evaluationTime(double seconds) {
      this.evaluationTimeSeconds = seconds;
      return this;
    }

    /**
     * Sets the ranked margins list.
     *
     * @param margins margins sorted by criticality
     * @return this builder
     */
    public Builder margins(List<OperatingMargin> margins) {
      this.rankedMargins = margins;
      return this;
    }

    /**
     * Sets the trip predictions list.
     *
     * @param predictions trip predictions
     * @return this builder
     */
    public Builder tripPredictions(List<TripPrediction> predictions) {
      this.tripPredictions = predictions;
      return this;
    }

    /**
     * Sets the mitigation actions list.
     *
     * @param actions recommended actions
     * @return this builder
     */
    public Builder mitigationActions(List<MitigationAction> actions) {
      this.mitigationActions = actions;
      return this;
    }

    /**
     * Sets the composition impact report.
     *
     * @param impact composition analysis result
     * @return this builder
     */
    public Builder compositionImpact(CompositionChangeAnalyzer.ImpactReport impact) {
      this.compositionImpact = impact;
      return this;
    }

    /**
     * Sets the summary message.
     *
     * @param message human-readable summary
     * @return this builder
     */
    public Builder summary(String message) {
      this.summaryMessage = message;
      return this;
    }

    /**
     * Builds the immutable evaluation result.
     *
     * @return the evaluation result
     */
    public AgentEvaluationResult build() {
      return new AgentEvaluationResult(this);
    }
  }
}
