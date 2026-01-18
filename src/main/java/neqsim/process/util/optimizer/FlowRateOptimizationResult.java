package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a flow rate optimization for pressure-constrained process simulation.
 *
 * <p>
 * This class encapsulates the results of finding the flow rate required to achieve a specified
 * pressure drop across process equipment. It includes:
 * </p>
 * <ul>
 * <li>The optimal flow rate found (or NaN if infeasible)</li>
 * <li>Achieved inlet and outlet pressures</li>
 * <li>Feasibility status with detailed reason if infeasible</li>
 * <li>Constraint violation details</li>
 * <li>Convergence information</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * FlowRateOptimizer optimizer = new FlowRateOptimizer(pipeline);
 * FlowRateOptimizationResult result = optimizer.findFlowRate(100.0, 80.0, "bara");
 * 
 * if (result.isFeasible()) {
 *   System.out.println("Optimal flow rate: " + result.getFlowRate() + " kg/hr");
 * } else {
 *   System.out.println("Infeasible: " + result.getInfeasibilityReason());
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class FlowRateOptimizationResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Status of the optimization result.
   */
  public enum Status {
    /**
     * Optimization converged to a feasible solution.
     */
    OPTIMAL,

    /**
     * Target pressure drop cannot be achieved at any flow rate. Pressure at minimum flow is already
     * below target, or pressure at maximum flow is above target.
     */
    INFEASIBLE_PRESSURE,

    /**
     * A constraint (e.g., velocity limit) is violated at all feasible flow rates.
     */
    INFEASIBLE_CONSTRAINT,

    /**
     * Optimization did not converge within the maximum iterations.
     */
    NOT_CONVERGED,

    /**
     * An error occurred during optimization.
     */
    ERROR
  }

  /**
   * Details of a constraint violation.
   */
  public static class ConstraintViolation implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String constraintName;
    private final String equipmentName;
    private final double currentValue;
    private final double limitValue;
    private final String unit;
    private final boolean isHardViolation;

    /**
     * Creates a new constraint violation record.
     *
     * @param constraintName name of the violated constraint
     * @param equipmentName name of the equipment with the violation
     * @param currentValue current value of the constrained variable
     * @param limitValue limit value that was exceeded
     * @param unit unit of measurement
     * @param isHardViolation true if this is a hard constraint violation
     */
    public ConstraintViolation(String constraintName, String equipmentName, double currentValue,
        double limitValue, String unit, boolean isHardViolation) {
      this.constraintName = constraintName;
      this.equipmentName = equipmentName;
      this.currentValue = currentValue;
      this.limitValue = limitValue;
      this.unit = unit;
      this.isHardViolation = isHardViolation;
    }

    /**
     * Gets the constraint name.
     *
     * @return constraint name
     */
    public String getConstraintName() {
      return constraintName;
    }

    /**
     * Gets the equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the current value.
     *
     * @return current value
     */
    public double getCurrentValue() {
      return currentValue;
    }

    /**
     * Gets the limit value.
     *
     * @return limit value
     */
    public double getLimitValue() {
      return limitValue;
    }

    /**
     * Gets the unit.
     *
     * @return unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Checks if this is a hard constraint violation.
     *
     * @return true if hard violation
     */
    public boolean isHardViolation() {
      return isHardViolation;
    }

    @Override
    public String toString() {
      return String.format("%s.%s: %.4f %s (limit: %.4f %s)%s", equipmentName, constraintName,
          currentValue, unit, limitValue, unit, isHardViolation ? " [HARD]" : "");
    }
  }

  private Status status = Status.OPTIMAL;
  private double flowRate = Double.NaN;
  private String flowRateUnit = "kg/hr";
  private double inletPressure = Double.NaN;
  private double outletPressure = Double.NaN;
  private double targetInletPressure = Double.NaN;
  private double targetOutletPressure = Double.NaN;
  private String pressureUnit = "bara";
  private String infeasibilityReason = "";
  private List<ConstraintViolation> constraintViolations = new ArrayList<ConstraintViolation>();
  private int iterationCount = 0;
  private double convergenceError = Double.NaN;
  private long computationTimeMs = 0;

  /**
   * Creates an empty optimization result.
   */
  public FlowRateOptimizationResult() {}

  /**
   * Creates a successful optimization result.
   *
   * @param flowRate optimal flow rate
   * @param flowRateUnit unit of flow rate
   * @param inletPressure achieved inlet pressure
   * @param outletPressure achieved outlet pressure
   * @param pressureUnit unit of pressure
   * @return the result
   */
  public static FlowRateOptimizationResult success(double flowRate, String flowRateUnit,
      double inletPressure, double outletPressure, String pressureUnit) {
    FlowRateOptimizationResult result = new FlowRateOptimizationResult();
    result.status = Status.OPTIMAL;
    result.flowRate = flowRate;
    result.flowRateUnit = flowRateUnit;
    result.inletPressure = inletPressure;
    result.outletPressure = outletPressure;
    result.pressureUnit = pressureUnit;
    return result;
  }

  /**
   * Creates an infeasible result due to pressure constraints.
   *
   * @param reason description of why pressure target cannot be achieved
   * @return the result
   */
  public static FlowRateOptimizationResult infeasiblePressure(String reason) {
    FlowRateOptimizationResult result = new FlowRateOptimizationResult();
    result.status = Status.INFEASIBLE_PRESSURE;
    result.infeasibilityReason = reason;
    return result;
  }

  /**
   * Creates an infeasible result due to constraint violations.
   *
   * @param reason description of constraint violation
   * @param violations list of constraint violations
   * @return the result
   */
  public static FlowRateOptimizationResult infeasibleConstraint(String reason,
      List<ConstraintViolation> violations) {
    FlowRateOptimizationResult result = new FlowRateOptimizationResult();
    result.status = Status.INFEASIBLE_CONSTRAINT;
    result.infeasibilityReason = reason;
    result.constraintViolations = new ArrayList<ConstraintViolation>(violations);
    return result;
  }

  /**
   * Creates a not-converged result.
   *
   * @param iterations number of iterations performed
   * @param lastError last convergence error
   * @return the result
   */
  public static FlowRateOptimizationResult notConverged(int iterations, double lastError) {
    FlowRateOptimizationResult result = new FlowRateOptimizationResult();
    result.status = Status.NOT_CONVERGED;
    result.iterationCount = iterations;
    result.convergenceError = lastError;
    result.infeasibilityReason =
        String.format("Did not converge after %d iterations (error: %.6f)", iterations, lastError);
    return result;
  }

  /**
   * Creates an error result.
   *
   * @param errorMessage error message
   * @return the result
   */
  public static FlowRateOptimizationResult error(String errorMessage) {
    FlowRateOptimizationResult result = new FlowRateOptimizationResult();
    result.status = Status.ERROR;
    result.infeasibilityReason = errorMessage;
    return result;
  }

  /**
   * Checks if the optimization found a feasible solution.
   *
   * @return true if status is OPTIMAL
   */
  public boolean isFeasible() {
    return status == Status.OPTIMAL;
  }

  /**
   * Gets the optimization status.
   *
   * @return the status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Sets the optimization status.
   *
   * @param status the status
   */
  public void setStatus(Status status) {
    this.status = status;
  }

  /**
   * Gets the optimal flow rate.
   *
   * @return flow rate, or NaN if infeasible
   */
  public double getFlowRate() {
    return flowRate;
  }

  /**
   * Sets the flow rate.
   *
   * @param flowRate the flow rate
   */
  public void setFlowRate(double flowRate) {
    this.flowRate = flowRate;
  }

  /**
   * Gets the flow rate unit.
   *
   * @return flow rate unit
   */
  public String getFlowRateUnit() {
    return flowRateUnit;
  }

  /**
   * Sets the flow rate unit.
   *
   * @param flowRateUnit the unit
   */
  public void setFlowRateUnit(String flowRateUnit) {
    this.flowRateUnit = flowRateUnit;
  }

  /**
   * Gets the achieved inlet pressure.
   *
   * @return inlet pressure
   */
  public double getInletPressure() {
    return inletPressure;
  }

  /**
   * Sets the inlet pressure.
   *
   * @param inletPressure the pressure
   */
  public void setInletPressure(double inletPressure) {
    this.inletPressure = inletPressure;
  }

  /**
   * Gets the achieved outlet pressure.
   *
   * @return outlet pressure
   */
  public double getOutletPressure() {
    return outletPressure;
  }

  /**
   * Sets the outlet pressure.
   *
   * @param outletPressure the pressure
   */
  public void setOutletPressure(double outletPressure) {
    this.outletPressure = outletPressure;
  }

  /**
   * Gets the target inlet pressure.
   *
   * @return target inlet pressure
   */
  public double getTargetInletPressure() {
    return targetInletPressure;
  }

  /**
   * Sets the target inlet pressure.
   *
   * @param targetInletPressure the target
   */
  public void setTargetInletPressure(double targetInletPressure) {
    this.targetInletPressure = targetInletPressure;
  }

  /**
   * Gets the target outlet pressure.
   *
   * @return target outlet pressure
   */
  public double getTargetOutletPressure() {
    return targetOutletPressure;
  }

  /**
   * Sets the target outlet pressure.
   *
   * @param targetOutletPressure the target
   */
  public void setTargetOutletPressure(double targetOutletPressure) {
    this.targetOutletPressure = targetOutletPressure;
  }

  /**
   * Gets the pressure unit.
   *
   * @return pressure unit
   */
  public String getPressureUnit() {
    return pressureUnit;
  }

  /**
   * Sets the pressure unit.
   *
   * @param pressureUnit the unit
   */
  public void setPressureUnit(String pressureUnit) {
    this.pressureUnit = pressureUnit;
  }

  /**
   * Gets the infeasibility reason message.
   *
   * @return reason message, or empty string if feasible
   */
  public String getInfeasibilityReason() {
    return infeasibilityReason;
  }

  /**
   * Sets the infeasibility reason.
   *
   * @param infeasibilityReason the reason
   */
  public void setInfeasibilityReason(String infeasibilityReason) {
    this.infeasibilityReason = infeasibilityReason;
  }

  /**
   * Gets the list of constraint violations.
   *
   * @return constraint violations
   */
  public List<ConstraintViolation> getConstraintViolations() {
    return constraintViolations;
  }

  /**
   * Adds a constraint violation.
   *
   * @param violation the violation to add
   */
  public void addConstraintViolation(ConstraintViolation violation) {
    constraintViolations.add(violation);
  }

  /**
   * Checks if there are any hard constraint violations.
   *
   * @return true if any hard constraint is violated
   */
  public boolean hasHardViolations() {
    for (ConstraintViolation v : constraintViolations) {
      if (v.isHardViolation()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the number of iterations performed.
   *
   * @return iteration count
   */
  public int getIterationCount() {
    return iterationCount;
  }

  /**
   * Sets the iteration count.
   *
   * @param iterationCount the count
   */
  public void setIterationCount(int iterationCount) {
    this.iterationCount = iterationCount;
  }

  /**
   * Gets the final convergence error.
   *
   * @return convergence error
   */
  public double getConvergenceError() {
    return convergenceError;
  }

  /**
   * Sets the convergence error.
   *
   * @param convergenceError the error
   */
  public void setConvergenceError(double convergenceError) {
    this.convergenceError = convergenceError;
  }

  /**
   * Gets the computation time in milliseconds.
   *
   * @return computation time
   */
  public long getComputationTimeMs() {
    return computationTimeMs;
  }

  /**
   * Sets the computation time.
   *
   * @param computationTimeMs time in milliseconds
   */
  public void setComputationTimeMs(long computationTimeMs) {
    this.computationTimeMs = computationTimeMs;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FlowRateOptimizationResult {\n");
    sb.append(String.format("  status: %s%n", status));

    if (isFeasible()) {
      sb.append(String.format("  flowRate: %.4f %s%n", flowRate, flowRateUnit));
      sb.append(String.format("  inletPressure: %.4f %s%n", inletPressure, pressureUnit));
      sb.append(String.format("  outletPressure: %.4f %s%n", outletPressure, pressureUnit));
    } else {
      sb.append(String.format("  reason: %s%n", infeasibilityReason));
    }

    if (!constraintViolations.isEmpty()) {
      sb.append("  constraintViolations:\n");
      for (ConstraintViolation v : constraintViolations) {
        sb.append(String.format("    - %s%n", v));
      }
    }

    sb.append(String.format("  iterations: %d%n", iterationCount));
    sb.append(String.format("  computationTime: %d ms%n", computationTimeMs));
    sb.append("}");
    return sb.toString();
  }
}
