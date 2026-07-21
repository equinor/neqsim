package neqsim.process.design;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Result container for design optimization.
 *
 * <p>
 * Holds the results from a design optimization run, including the optimized process, equipment sizes, constraint
 * status, and performance metrics.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DesignResult {

  /** Describes what work produced this result without conflating validation with optimization. */
  public enum ExecutionStatus {
    /** No workflow has run. */
    NOT_RUN,
    /** A baseline simulation and constraint validation ran, but no search ran. */
    VALIDATED,
    /** Equipment auto-sizing and validation ran, but no search ran. */
    AUTO_SIZED,
    /** A configured optimization search returned a feasible operating point. */
    OPTIMIZED,
    /** A configured search completed without finding a feasible operating point. */
    INFEASIBLE,
    /** The workflow failed before it could return an engineering result. */
    FAILED
  }

  private ProcessSystem process;
  private ExecutionStatus executionStatus = ExecutionStatus.NOT_RUN;
  private boolean converged;
  private int iterations;
  private double objectiveValue;

  private Map<String, Double> optimizedFlowRates = new HashMap<>();
  private Map<String, Double> decisionVariables = new HashMap<>();
  private Map<String, Map<String, Double>> equipmentSizes = new HashMap<>();
  private Map<String, ConstraintStatus> constraintStatus = new HashMap<>();
  private List<String> warnings = new ArrayList<>();
  private List<String> violations = new ArrayList<>();

  /**
   * Create a design result.
   *
   * @param process the optimized process
   */
  public DesignResult(ProcessSystem process) {
    this.process = process;
  }

  /**
   * Get the optimized process.
   *
   * @return the process system
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /**
   * Get the workflow status.
   *
   * @return status distinguishing validation, sizing, optimization, infeasibility, and failure
   */
  public ExecutionStatus getExecutionStatus() {
    return executionStatus;
  }

  /**
   * Set the workflow status.
   *
   * @param executionStatus workflow status
   */
  public void setExecutionStatus(ExecutionStatus executionStatus) {
    this.executionStatus = executionStatus;
  }

  /**
   * Check if optimization converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Set convergence status.
   *
   * @param converged convergence status
   */
  public void setConverged(boolean converged) {
    this.converged = converged;
  }

  /**
   * Get the number of iterations.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Set the iteration count.
   *
   * @param iterations number of iterations
   */
  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  /**
   * Get the objective function value.
   *
   * @return objective value
   */
  public double getObjectiveValue() {
    return objectiveValue;
  }

  /**
   * Set the objective value.
   *
   * @param value objective value
   */
  public void setObjectiveValue(double value) {
    this.objectiveValue = value;
  }

  /**
   * Record an optimized flow rate.
   *
   * @param streamName stream name
   * @param flowRate flow rate in kg/hr
   */
  public void addOptimizedFlowRate(String streamName, double flowRate) {
    optimizedFlowRates.put(streamName, flowRate);
  }

  /**
   * Get all optimized flow rates.
   *
   * @return map of stream name to flow rate
   */
  public Map<String, Double> getOptimizedFlowRates() {
    return new HashMap<>(optimizedFlowRates);
  }

  /**
   * Record a decision-variable value returned by an actual optimizer search.
   *
   * @param name decision-variable name
   * @param value selected value
   */
  public void addDecisionVariable(String name, double value) {
    decisionVariables.put(name, value);
  }

  /**
   * Get the decision variables returned by an optimizer search.
   *
   * @return defensive map of decision-variable names and selected values
   */
  public Map<String, Double> getDecisionVariables() {
    return new HashMap<>(decisionVariables);
  }

  /**
   * Record equipment size.
   *
   * @param equipmentName equipment name
   * @param sizeName size parameter name (e.g., "diameter", "length")
   * @param value size value
   */
  public void addEquipmentSize(String equipmentName, String sizeName, double value) {
    equipmentSizes.computeIfAbsent(equipmentName, k -> new HashMap<>()).put(sizeName, value);
  }

  /**
   * Get sizes for an equipment.
   *
   * @param equipmentName equipment name
   * @return map of size name to value
   */
  public Map<String, Double> getEquipmentSizes(String equipmentName) {
    Map<String, Double> sizes = equipmentSizes.get(equipmentName);
    return sizes == null ? new HashMap<String, Double>() : new HashMap<String, Double>(sizes);
  }

  /**
   * Record constraint status.
   *
   * @param equipmentName equipment name
   * @param constraintName constraint name
   * @param currentValue current value
   * @param limitValue limit value
   * @param utilized utilization fraction (0-1)
   */
  public void addConstraintStatus(String equipmentName, String constraintName, double currentValue, double limitValue,
      double utilized) {
    String key = equipmentName + "." + constraintName;
    constraintStatus.put(key,
        new ConstraintStatus(constraintName, currentValue, limitValue, utilized, utilized <= 1.0));
  }

  /**
   * Get all constraint statuses.
   *
   * @return map of constraint key to status
   */
  public Map<String, ConstraintStatus> getConstraintStatus() {
    return new HashMap<>(constraintStatus);
  }

  /**
   * Add a warning message.
   *
   * @param warning warning message
   */
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  /**
   * Get all warnings.
   *
   * @return list of warnings
   */
  public List<String> getWarnings() {
    return new ArrayList<>(warnings);
  }

  /**
   * Add a violation message.
   *
   * @param violation violation message
   */
  public void addViolation(String violation) {
    violations.add(violation);
  }

  /**
   * Get all violations.
   *
   * @return list of violations
   */
  public List<String> getViolations() {
    return new ArrayList<>(violations);
  }

  /**
   * Check if there are any violations.
   *
   * @return true if there are violations
   */
  public boolean hasViolations() {
    return !violations.isEmpty();
  }

  /**
   * Check if there are any warnings.
   *
   * @return true if there are warnings
   */
  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  /**
   * Get an equipment from the optimized process.
   *
   * @param name equipment name
   * @return the equipment
   */
  public ProcessEquipmentInterface getEquipment(String name) {
    return process == null ? null : process.getUnit(name);
  }

  /**
   * Get the optimized flow rate for a stream.
   *
   * @param streamName stream name
   * @return flow rate in kg/hr or 0 if not found
   */
  public double getOptimizedFlowRate(String streamName) {
    return optimizedFlowRates.getOrDefault(streamName, 0.0);
  }

  /**
   * Generate a summary report.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Design Result ===\n");
    sb.append("Status: ").append(executionStatus).append("\n");
    sb.append("Converged: ").append(converged).append("\n");
    sb.append("Iterations: ").append(iterations).append("\n");
    sb.append("Objective: ").append(String.format("%.4f", objectiveValue)).append("\n\n");

    if (!optimizedFlowRates.isEmpty()) {
      sb.append("Optimized Flow Rates:\n");
      for (Map.Entry<String, Double> entry : optimizedFlowRates.entrySet()) {
        sb.append("  ").append(entry.getKey()).append(": ").append(String.format("%.2f", entry.getValue()))
            .append(" kg/hr\n");
      }
      sb.append("\n");
    }

    if (!constraintStatus.isEmpty()) {
      sb.append("Constraint Status:\n");
      for (Map.Entry<String, ConstraintStatus> entry : constraintStatus.entrySet()) {
        ConstraintStatus status = entry.getValue();
        sb.append("  ").append(entry.getKey()).append(": ")
            .append(String.format("%.1f%%", status.getUtilization() * 100))
            .append(status.isSatisfied() ? " [OK]" : " [VIOLATED]").append("\n");
      }
      sb.append("\n");
    }

    if (!violations.isEmpty()) {
      sb.append("VIOLATIONS:\n");
      for (String v : violations) {
        sb.append("  - ").append(v).append("\n");
      }
      sb.append("\n");
    }

    if (!warnings.isEmpty()) {
      sb.append("Warnings:\n");
      for (String w : warnings) {
        sb.append("  - ").append(w).append("\n");
      }
    }

    return sb.toString();
  }

  /**
   * Status of a single constraint.
   */
  public static class ConstraintStatus {
    private String name;
    private double currentValue;
    private double limitValue;
    private double utilization;
    private boolean satisfied;

    /**
     * Create constraint status.
     *
     * @param name constraint name
     * @param currentValue current value
     * @param limitValue limit value
     * @param utilization utilization (0-1)
     * @param satisfied whether satisfied
     */
    public ConstraintStatus(String name, double currentValue, double limitValue, double utilization,
        boolean satisfied) {
      this.name = name;
      this.currentValue = currentValue;
      this.limitValue = limitValue;
      this.utilization = utilization;
      this.satisfied = satisfied;
    }

    /**
     * Get constraint name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Get current value.
     *
     * @return current value
     */
    public double getCurrentValue() {
      return currentValue;
    }

    /**
     * Get limit value.
     *
     * @return limit value
     */
    public double getLimitValue() {
      return limitValue;
    }

    /**
     * Get utilization (current/limit).
     *
     * @return utilization fraction
     */
    public double getUtilization() {
      return utilization;
    }

    /**
     * Check if constraint is satisfied.
     *
     * @return true if satisfied
     */
    public boolean isSatisfied() {
      return satisfied;
    }
  }
}
