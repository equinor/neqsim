package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all optimization results in the NeqSim optimization framework.
 *
 * <p>
 * This class provides a unified structure for optimization results, including:
 * <ul>
 * <li>Convergence status and iteration count</li>
 * <li>Optimal values and objective function value</li>
 * <li>Constraint violation tracking</li>
 * <li>Sensitivity information</li>
 * <li>Performance metrics (timing, evaluations)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Specialized result classes should extend this base to add domain-specific fields.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class OptimizationResultBase implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Status of the optimization run.
   */
  public enum Status {
    /** Optimization converged successfully. */
    CONVERGED,
    /** Optimization did not converge within iteration limit. */
    MAX_ITERATIONS_REACHED,
    /** No feasible solution found (all constraints violated). */
    INFEASIBLE,
    /** Optimization failed due to error. */
    FAILED,
    /** Optimization was cancelled. */
    CANCELLED,
    /** Optimization is still running. */
    IN_PROGRESS,
    /** Not yet started. */
    NOT_STARTED
  }

  // Core result fields
  private Status status = Status.NOT_STARTED;
  private boolean converged = false;
  private String objective;
  private double optimalValue;
  private double objectiveValue;
  private String errorMessage;

  // Iteration tracking
  private int iterations = 0;
  private int functionEvaluations = 0;
  private int constraintEvaluations = 0;

  // Timing
  private long startTimeMillis = 0;
  private long endTimeMillis = 0;

  // Constraint tracking
  private List<ConstraintViolation> constraintViolations = new ArrayList<>();
  private Map<String, Double> constraintMargins = new HashMap<>();
  private String bottleneckEquipment;
  private String bottleneckConstraint;

  // Sensitivity information
  private Map<String, Double> sensitivities = new HashMap<>();
  private Map<String, Double> shadowPrices = new HashMap<>();

  // Multi-variable support
  private Map<String, Double> optimalValues = new HashMap<>();
  private Map<String, Double> initialValues = new HashMap<>();

  /**
   * Represents a constraint violation.
   */
  public static class ConstraintViolation implements Serializable {
    private static final long serialVersionUID = 1L;
    private String equipmentName;
    private String constraintName;
    private double currentValue;
    private double limitValue;
    private String unit;
    private boolean isHardConstraint;

    /**
     * Default constructor.
     */
    public ConstraintViolation() {}

    /**
     * Constructor with all fields.
     *
     * @param equipmentName name of equipment
     * @param constraintName name of constraint
     * @param currentValue current value
     * @param limitValue limit value
     * @param unit unit of measurement
     * @param isHardConstraint whether this is a hard constraint
     */
    public ConstraintViolation(String equipmentName, String constraintName, double currentValue,
        double limitValue, String unit, boolean isHardConstraint) {
      this.equipmentName = equipmentName;
      this.constraintName = constraintName;
      this.currentValue = currentValue;
      this.limitValue = limitValue;
      this.unit = unit;
      this.isHardConstraint = isHardConstraint;
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
      this.equipmentName = equipmentName;
    }

    public String getConstraintName() {
      return constraintName;
    }

    public void setConstraintName(String constraintName) {
      this.constraintName = constraintName;
    }

    public double getCurrentValue() {
      return currentValue;
    }

    public void setCurrentValue(double currentValue) {
      this.currentValue = currentValue;
    }

    public double getLimitValue() {
      return limitValue;
    }

    public void setLimitValue(double limitValue) {
      this.limitValue = limitValue;
    }

    public String getUnit() {
      return unit;
    }

    public void setUnit(String unit) {
      this.unit = unit;
    }

    public boolean isHardConstraint() {
      return isHardConstraint;
    }

    public void setHardConstraint(boolean hardConstraint) {
      isHardConstraint = hardConstraint;
    }

    /**
     * Gets the violation amount (how much over the limit).
     *
     * @return violation amount
     */
    public double getViolationAmount() {
      return currentValue - limitValue;
    }

    /**
     * Gets the violation as a percentage of the limit.
     *
     * @return violation percentage
     */
    public double getViolationPercent() {
      if (limitValue == 0) {
        return currentValue > 0 ? Double.POSITIVE_INFINITY : 0.0;
      }
      return (currentValue - limitValue) / limitValue * 100.0;
    }

    @Override
    public String toString() {
      return String.format("%s/%s: %.2f > %.2f %s (%.1f%% over)", equipmentName, constraintName,
          currentValue, limitValue, unit != null ? unit : "", getViolationPercent());
    }
  }

  /**
   * Default constructor.
   */
  public OptimizationResultBase() {}

  // Status methods

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public boolean isConverged() {
    return converged;
  }

  public void setConverged(boolean converged) {
    this.converged = converged;
    if (converged) {
      this.status = Status.CONVERGED;
    }
  }

  public String getObjective() {
    return objective;
  }

  public void setObjective(String objective) {
    this.objective = objective;
  }

  public double getOptimalValue() {
    return optimalValue;
  }

  public void setOptimalValue(double optimalValue) {
    this.optimalValue = optimalValue;
  }

  public double getObjectiveValue() {
    return objectiveValue;
  }

  public void setObjectiveValue(double objectiveValue) {
    this.objectiveValue = objectiveValue;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  // Iteration tracking

  public int getIterations() {
    return iterations;
  }

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  public void incrementIterations() {
    this.iterations++;
  }

  public int getFunctionEvaluations() {
    return functionEvaluations;
  }

  public void setFunctionEvaluations(int functionEvaluations) {
    this.functionEvaluations = functionEvaluations;
  }

  public void incrementFunctionEvaluations() {
    this.functionEvaluations++;
  }

  public int getConstraintEvaluations() {
    return constraintEvaluations;
  }

  public void setConstraintEvaluations(int constraintEvaluations) {
    this.constraintEvaluations = constraintEvaluations;
  }

  public void incrementConstraintEvaluations() {
    this.constraintEvaluations++;
  }

  // Timing

  /**
   * Marks the start of optimization.
   */
  public void markStart() {
    this.startTimeMillis = System.currentTimeMillis();
    this.status = Status.IN_PROGRESS;
  }

  /**
   * Marks the end of optimization.
   */
  public void markEnd() {
    this.endTimeMillis = System.currentTimeMillis();
  }

  public long getStartTimeMillis() {
    return startTimeMillis;
  }

  public long getEndTimeMillis() {
    return endTimeMillis;
  }

  /**
   * Gets the elapsed time in milliseconds.
   *
   * @return elapsed time
   */
  public long getElapsedTimeMillis() {
    if (endTimeMillis > 0) {
      return endTimeMillis - startTimeMillis;
    } else if (startTimeMillis > 0) {
      return System.currentTimeMillis() - startTimeMillis;
    }
    return 0;
  }

  /**
   * Gets the elapsed time in seconds.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedTimeSeconds() {
    return getElapsedTimeMillis() / 1000.0;
  }

  // Constraint tracking

  public List<ConstraintViolation> getConstraintViolations() {
    return Collections.unmodifiableList(constraintViolations);
  }

  public void setConstraintViolations(List<ConstraintViolation> violations) {
    this.constraintViolations = new ArrayList<>(violations);
  }

  public void addConstraintViolation(ConstraintViolation violation) {
    this.constraintViolations.add(violation);
  }

  public void addConstraintViolation(String equipment, String constraint, double current,
      double limit, String unit, boolean hard) {
    this.constraintViolations
        .add(new ConstraintViolation(equipment, constraint, current, limit, unit, hard));
  }

  public boolean hasViolations() {
    return !constraintViolations.isEmpty();
  }

  public boolean hasHardViolations() {
    for (ConstraintViolation v : constraintViolations) {
      if (v.isHardConstraint()) {
        return true;
      }
    }
    return false;
  }

  public Map<String, Double> getConstraintMargins() {
    return Collections.unmodifiableMap(constraintMargins);
  }

  public void setConstraintMargins(Map<String, Double> margins) {
    this.constraintMargins = new HashMap<>(margins);
  }

  public void addConstraintMargin(String name, double margin) {
    this.constraintMargins.put(name, margin);
  }

  public String getBottleneckEquipment() {
    return bottleneckEquipment;
  }

  public void setBottleneckEquipment(String bottleneckEquipment) {
    this.bottleneckEquipment = bottleneckEquipment;
  }

  public String getBottleneckConstraint() {
    return bottleneckConstraint;
  }

  public void setBottleneckConstraint(String bottleneckConstraint) {
    this.bottleneckConstraint = bottleneckConstraint;
  }

  // Sensitivity information

  public Map<String, Double> getSensitivities() {
    return Collections.unmodifiableMap(sensitivities);
  }

  public void setSensitivities(Map<String, Double> sensitivities) {
    this.sensitivities = new HashMap<>(sensitivities);
  }

  public void addSensitivity(String name, double value) {
    this.sensitivities.put(name, value);
  }

  public Map<String, Double> getShadowPrices() {
    return Collections.unmodifiableMap(shadowPrices);
  }

  public void setShadowPrices(Map<String, Double> shadowPrices) {
    this.shadowPrices = new HashMap<>(shadowPrices);
  }

  public void addShadowPrice(String constraint, double price) {
    this.shadowPrices.put(constraint, price);
  }

  // Multi-variable support

  public Map<String, Double> getOptimalValues() {
    return Collections.unmodifiableMap(optimalValues);
  }

  public void setOptimalValues(Map<String, Double> optimalValues) {
    this.optimalValues = new HashMap<>(optimalValues);
  }

  public void addOptimalValue(String name, double value) {
    this.optimalValues.put(name, value);
  }

  public Map<String, Double> getInitialValues() {
    return Collections.unmodifiableMap(initialValues);
  }

  public void setInitialValues(Map<String, Double> initialValues) {
    this.initialValues = new HashMap<>(initialValues);
  }

  public void addInitialValue(String name, double value) {
    this.initialValues.put(name, value);
  }

  /**
   * Gets a summary string of the result.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Status: ").append(status).append("\n");
    sb.append("Converged: ").append(converged).append("\n");
    if (objective != null) {
      sb.append("Objective: ").append(objective).append("\n");
    }
    sb.append("Optimal Value: ").append(optimalValue).append("\n");
    sb.append("Iterations: ").append(iterations).append("\n");
    sb.append("Function Evaluations: ").append(functionEvaluations).append("\n");
    sb.append("Elapsed Time: ").append(String.format("%.3f", getElapsedTimeSeconds()))
        .append(" s\n");

    if (bottleneckEquipment != null) {
      sb.append("Bottleneck: ").append(bottleneckEquipment);
      if (bottleneckConstraint != null) {
        sb.append(" (").append(bottleneckConstraint).append(")");
      }
      sb.append("\n");
    }

    if (!constraintViolations.isEmpty()) {
      sb.append("Violations:\n");
      for (ConstraintViolation v : constraintViolations) {
        sb.append("  - ").append(v.toString()).append("\n");
      }
    }

    if (errorMessage != null) {
      sb.append("Error: ").append(errorMessage).append("\n");
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return getSummary();
  }
}
