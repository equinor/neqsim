package neqsim.process.operations.envelope;

import com.google.gson.JsonObject;
import java.io.Serializable;

/**
 * Advisory prediction that a margin may reach its limit within a time horizon.
 *
 * @author ESOL
 * @version 1.0
 */
public final class TripPrediction implements Serializable, Comparable<TripPrediction> {
  private static final long serialVersionUID = 1L;

  /** Prediction severity based on estimated time to limit. */
  public enum Severity {
    /** Limit is predicted after more than 30 minutes. */
    LOW(1),
    /** Limit is predicted in 10 to 30 minutes. */
    MEDIUM(2),
    /** Limit is predicted in 2 to 10 minutes. */
    HIGH(3),
    /** Limit is predicted within 2 minutes or already reached. */
    IMMINENT(4);

    private final int rank;

    /**
     * Creates a severity with a ranking value.
     *
     * @param rank ranking value where higher values are more severe
     */
    Severity(int rank) {
      this.rank = rank;
    }

    /**
     * Returns the ranking value.
     *
     * @return severity rank
     */
    public int getRank() {
      return rank;
    }
  }

  private final String marginKey;
  private final String equipmentName;
  private final String constraintName;
  private final double currentMarginPercent;
  private final double estimatedTimeToLimitSeconds;
  private final double confidence;
  private final Severity severity;
  private final String trendDescription;

  /**
   * Creates a trip prediction.
   *
   * @param margin margin that is trending toward a limit
   * @param estimatedTimeToLimitSeconds estimated time to zero margin in seconds
   * @param confidence confidence from 0.0 to 1.0
   * @param trendDescription short trend description
   */
  public TripPrediction(OperationalMargin margin, double estimatedTimeToLimitSeconds,
      double confidence, String trendDescription) {
    this.marginKey = margin.getKey();
    this.equipmentName = margin.getEquipmentName();
    this.constraintName = margin.getConstraintName();
    this.currentMarginPercent = margin.getMarginPercent();
    this.estimatedTimeToLimitSeconds = estimatedTimeToLimitSeconds;
    this.confidence = Math.max(0.0, Math.min(1.0, confidence));
    this.severity = classify(estimatedTimeToLimitSeconds);
    this.trendDescription = trendDescription == null ? "" : trendDescription;
  }

  /**
   * Classifies a prediction by estimated time to limit.
   *
   * @param seconds estimated time to limit in seconds
   * @return prediction severity
   */
  public static Severity classify(double seconds) {
    if (seconds <= 120.0) {
      return Severity.IMMINENT;
    }
    if (seconds <= 600.0) {
      return Severity.HIGH;
    }
    if (seconds <= 1800.0) {
      return Severity.MEDIUM;
    }
    return Severity.LOW;
  }

  /**
   * Returns the margin key.
   *
   * @return margin key
   */
  public String getMarginKey() {
    return marginKey;
  }

  /**
   * Returns the equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Returns the constraint name.
   *
   * @return constraint name
   */
  public String getConstraintName() {
    return constraintName;
  }

  /**
   * Returns the current margin.
   *
   * @return current margin in percent
   */
  public double getCurrentMarginPercent() {
    return currentMarginPercent;
  }

  /**
   * Returns the estimated time to limit.
   *
   * @return time to limit in seconds
   */
  public double getEstimatedTimeToLimitSeconds() {
    return estimatedTimeToLimitSeconds;
  }

  /**
   * Returns the estimated time to limit.
   *
   * @return time to limit in minutes
   */
  public double getEstimatedTimeToLimitMinutes() {
    return estimatedTimeToLimitSeconds / 60.0;
  }

  /**
   * Returns prediction confidence.
   *
   * @return confidence from 0.0 to 1.0
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Returns prediction severity.
   *
   * @return prediction severity
   */
  public Severity getSeverity() {
    return severity;
  }

  /**
   * Returns the trend description.
   *
   * @return trend description
   */
  public String getTrendDescription() {
    return trendDescription;
  }

  /**
   * Converts the prediction to JSON.
   *
   * @return JSON object representation
   */
  public JsonObject toJsonObject() {
    JsonObject json = new JsonObject();
    json.addProperty("marginKey", marginKey);
    json.addProperty("equipmentName", equipmentName);
    json.addProperty("constraintName", constraintName);
    json.addProperty("currentMarginPercent", currentMarginPercent);
    json.addProperty("estimatedTimeToLimitSeconds", estimatedTimeToLimitSeconds);
    json.addProperty("estimatedTimeToLimitMinutes", getEstimatedTimeToLimitMinutes());
    json.addProperty("confidence", confidence);
    json.addProperty("severity", severity.name());
    json.addProperty("trend", trendDescription);
    return json;
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(TripPrediction other) {
    int severityCompare = other.severity.getRank() - severity.getRank();
    if (severityCompare != 0) {
      return severityCompare;
    }
    return Double.compare(estimatedTimeToLimitSeconds, other.estimatedTimeToLimitSeconds);
  }
}