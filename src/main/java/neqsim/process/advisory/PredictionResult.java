package neqsim.process.advisory;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a look-ahead prediction for advisory systems.
 *
 * <p>
 * This class provides structured output for predictive simulations that support real-time advisory
 * systems. Key features:
 * <ul>
 * <li><b>Time Horizon:</b> Predictions from minutes to days ahead</li>
 * <li><b>Uncertainty Bounds:</b> Confidence intervals for predicted values</li>
 * <li><b>Constraint Checking:</b> Which limits may be violated and when</li>
 * <li><b>Explanation:</b> Human-readable description of prediction drivers</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * // Run look-ahead simulation
 * PredictionResult prediction = processSystem.predictAhead(Duration.ofHours(2));
 *
 * // Check for issues
 * if (prediction.hasViolations()) {
 *   System.out.println("Warning: " + prediction.getViolationSummary());
 * }
 *
 * // Get predicted values with uncertainty
 * PredictedValue pressure = prediction.getValue("separator.pressure");
 * System.out.println("Predicted pressure: " + pressure.getMean() + " ± "
 *     + pressure.getStandardDeviation() + " " + pressure.getUnit());
 *
 * // Use in advisory system
 * String advice = prediction.getAdvisoryRecommendation();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PredictionResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Instant predictionTime;
  private final Duration horizon;
  private final String scenarioName;
  private final Map<String, PredictedValue> predictedValues;
  private final List<ConstraintViolation> violations;
  private final List<String> assumptions;
  private String explanation;
  private double overallConfidence = 1.0;
  private PredictionStatus status = PredictionStatus.SUCCESS;

  /**
   * Status of the prediction calculation.
   */
  public enum PredictionStatus {
    /** Prediction completed successfully. */
    SUCCESS,
    /** Prediction completed with warnings. */
    WARNING,
    /** Prediction failed to converge. */
    FAILED,
    /** Input data quality issues. */
    DATA_QUALITY_ISSUE
  }

  /**
   * Creates a new prediction result.
   *
   * @param horizon the prediction time horizon
   * @param scenarioName name of the scenario being predicted
   */
  public PredictionResult(Duration horizon, String scenarioName) {
    this.predictionTime = Instant.now();
    this.horizon = horizon;
    this.scenarioName = scenarioName;
    this.predictedValues = new HashMap<>();
    this.violations = new ArrayList<>();
    this.assumptions = new ArrayList<>();
  }

  /**
   * Creates a simple prediction result with default settings.
   *
   * @param horizon the prediction time horizon
   */
  public PredictionResult(Duration horizon) {
    this(horizon, "Base Case");
  }

  /**
   * Adds a predicted value to the result.
   *
   * @param variableName the variable name (e.g., "separator.pressure")
   * @param value the predicted value with uncertainty
   */
  public void addPredictedValue(String variableName, PredictedValue value) {
    predictedValues.put(variableName, value);
  }

  /**
   * Records a constraint violation prediction.
   *
   * @param violation the predicted violation
   */
  public void addViolation(ConstraintViolation violation) {
    violations.add(violation);
    if (status == PredictionStatus.SUCCESS) {
      status = PredictionStatus.WARNING;
    }
  }

  /**
   * Adds an assumption used in the prediction.
   *
   * @param assumption description of the assumption
   */
  public void addAssumption(String assumption) {
    assumptions.add(assumption);
  }

  /**
   * Gets a predicted value by variable name.
   *
   * @param variableName the variable to retrieve
   * @return the predicted value, or null if not found
   */
  public PredictedValue getValue(String variableName) {
    return predictedValues.get(variableName);
  }

  /**
   * Checks if any constraint violations are predicted.
   *
   * @return true if violations are expected
   */
  public boolean hasViolations() {
    return !violations.isEmpty();
  }

  /**
   * Gets a summary of predicted violations.
   *
   * @return human-readable summary
   */
  public String getViolationSummary() {
    if (violations.isEmpty()) {
      return "No constraint violations predicted within " + formatDuration(horizon);
    }

    StringBuilder sb = new StringBuilder();
    sb.append(violations.size()).append(" potential violation(s) within ")
        .append(formatDuration(horizon)).append(":\n");

    for (ConstraintViolation v : violations) {
      sb.append("  - ").append(v.getDescription()).append("\n");
    }

    return sb.toString();
  }

  /**
   * Gets an advisory recommendation based on the prediction.
   *
   * @return recommendation for operators
   */
  public String getAdvisoryRecommendation() {
    if (violations.isEmpty()) {
      return "No action required. Process operating within normal bounds.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("ADVISORY: ").append(violations.size()).append(" potential issue(s) predicted.\n\n");

    for (ConstraintViolation v : violations) {
      sb.append("Issue: ").append(v.constraintName).append("\n");
      sb.append("  Expected: ").append(formatDuration(v.timeToViolation)).append(" from now\n");
      sb.append("  Severity: ").append(v.severity).append("\n");
      if (v.suggestedAction != null) {
        sb.append("  Suggested action: ").append(v.suggestedAction).append("\n");
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  private String formatDuration(Duration d) {
    if (d.toMinutes() < 60) {
      return d.toMinutes() + " minutes";
    } else if (d.toHours() < 24) {
      return d.toHours() + " hours";
    } else {
      return d.toDays() + " days";
    }
  }

  // Getters and setters

  public Instant getPredictionTime() {
    return predictionTime;
  }

  public Duration getHorizon() {
    return horizon;
  }

  public String getScenarioName() {
    return scenarioName;
  }

  public Map<String, PredictedValue> getAllPredictedValues() {
    return predictedValues;
  }

  public List<ConstraintViolation> getViolations() {
    return violations;
  }

  public List<String> getAssumptions() {
    return assumptions;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String explanation) {
    this.explanation = explanation;
  }

  public double getOverallConfidence() {
    return overallConfidence;
  }

  public void setOverallConfidence(double confidence) {
    this.overallConfidence = Math.max(0.0, Math.min(1.0, confidence));
  }

  public PredictionStatus getStatus() {
    return status;
  }

  public void setStatus(PredictionStatus status) {
    this.status = status;
  }

  /**
   * A predicted value with uncertainty bounds.
   */
  public static class PredictedValue implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double mean;
    private final double standardDeviation;
    private final double lower95;
    private final double upper95;
    private final String unit;
    private final double confidence;

    /**
     * Creates a predicted value with uncertainty.
     *
     * @param mean expected value
     * @param standardDeviation uncertainty (standard deviation)
     * @param unit engineering unit
     */
    public PredictedValue(double mean, double standardDeviation, String unit) {
      this.mean = mean;
      this.standardDeviation = standardDeviation;
      this.lower95 = mean - 1.96 * standardDeviation;
      this.upper95 = mean + 1.96 * standardDeviation;
      this.unit = unit;
      this.confidence = 1.0;
    }

    /**
     * Creates a predicted value with explicit bounds.
     *
     * @param mean expected value
     * @param lower95 lower 95% confidence bound
     * @param upper95 upper 95% confidence bound
     * @param unit engineering unit
     * @param confidence overall confidence (0-1)
     */
    public PredictedValue(double mean, double lower95, double upper95, String unit,
        double confidence) {
      this.mean = mean;
      this.lower95 = lower95;
      this.upper95 = upper95;
      this.standardDeviation = (upper95 - lower95) / (2 * 1.96);
      this.unit = unit;
      this.confidence = confidence;
    }

    /**
     * Creates a deterministic predicted value (no uncertainty).
     *
     * @param value the predicted value
     * @param unit engineering unit
     * @return a PredictedValue with zero uncertainty
     */
    public static PredictedValue deterministic(double value, String unit) {
      return new PredictedValue(value, 0.0, unit);
    }

    public double getMean() {
      return mean;
    }

    public double getStandardDeviation() {
      return standardDeviation;
    }

    public double getLower95() {
      return lower95;
    }

    public double getUpper95() {
      return upper95;
    }

    public String getUnit() {
      return unit;
    }

    public double getConfidence() {
      return confidence;
    }

    @Override
    public String toString() {
      if (standardDeviation < 1e-10) {
        return String.format("%.4f %s", mean, unit);
      }
      return String.format("%.4f ± %.4f %s (95%% CI: [%.4f, %.4f])", mean, standardDeviation, unit,
          lower95, upper95);
    }
  }

  /**
   * A predicted constraint violation.
   */
  public static class ConstraintViolation implements Serializable {
    private static final long serialVersionUID = 1000L;

    /**
     * Severity levels for violations.
     */
    public enum Severity {
      /** Informational - minor deviation expected. */
      LOW,
      /** Warning - significant deviation, attention needed. */
      MEDIUM,
      /** Critical - safety or equipment limits may be exceeded. */
      HIGH,
      /** Emergency - immediate action required. */
      CRITICAL
    }

    private final String constraintName;
    private final String variableName;
    private final double predictedValue;
    private final double limitValue;
    private final String unit;
    private final Duration timeToViolation;
    private final Severity severity;
    private String suggestedAction;

    /**
     * Creates a constraint violation prediction.
     *
     * @param constraintName name of the constraint
     * @param variableName affected variable
     * @param predictedValue predicted value at violation
     * @param limitValue the limit being violated
     * @param unit engineering unit
     * @param timeToViolation time until violation expected
     * @param severity severity level
     */
    public ConstraintViolation(String constraintName, String variableName, double predictedValue,
        double limitValue, String unit, Duration timeToViolation, Severity severity) {
      this.constraintName = constraintName;
      this.variableName = variableName;
      this.predictedValue = predictedValue;
      this.limitValue = limitValue;
      this.unit = unit;
      this.timeToViolation = timeToViolation;
      this.severity = severity;
    }

    /**
     * Gets a human-readable description of the violation.
     *
     * @return description string
     */
    public String getDescription() {
      return String.format("%s: %s expected to reach %.2f %s (limit: %.2f) in %s", constraintName,
          variableName, predictedValue, unit, limitValue, formatDurationShort(timeToViolation));
    }

    private String formatDurationShort(Duration d) {
      if (d.toMinutes() < 60) {
        return d.toMinutes() + " min";
      } else if (d.toHours() < 24) {
        return d.toHours() + " hr";
      } else {
        return d.toDays() + " days";
      }
    }

    public String getConstraintName() {
      return constraintName;
    }

    public String getVariableName() {
      return variableName;
    }

    public double getPredictedValue() {
      return predictedValue;
    }

    public double getLimitValue() {
      return limitValue;
    }

    public String getUnit() {
      return unit;
    }

    public Duration getTimeToViolation() {
      return timeToViolation;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getSuggestedAction() {
      return suggestedAction;
    }

    public void setSuggestedAction(String action) {
      this.suggestedAction = action;
    }
  }
}
