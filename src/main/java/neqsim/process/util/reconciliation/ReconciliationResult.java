package neqsim.process.util.reconciliation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a data reconciliation run.
 *
 * <p>
 * Contains the reconciled variable values, constraint residuals, global and per-variable
 * statistical tests, and gross error detection results. Produced by
 * {@link DataReconciliationEngine#reconcile()}.
 * </p>
 *
 * @author Process Optimization Team
 * @version 1.0
 */
public class ReconciliationResult implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  /** All reconciliation variables with their adjusted values. */
  private final List<ReconciliationVariable> variables;

  /** Weighted sum of squared adjustments (objective function value). */
  private double objectiveValue;

  /** Chi-square test statistic for the global test. */
  private double chiSquareStatistic;

  /** Degrees of freedom = number of redundant measurements. */
  private int degreesOfFreedom;

  /**
   * Whether the global chi-square test passed. True means no evidence of gross errors at the
   * specified confidence level.
   */
  private boolean globalTestPassed;

  /** Variables flagged as gross errors. */
  private final List<ReconciliationVariable> grossErrors;

  /** Constraint residuals before reconciliation. */
  private double[] constraintResidualsBefore;

  /** Constraint residuals after reconciliation. */
  private double[] constraintResidualsAfter;

  /** Computation time in milliseconds. */
  private long computeTimeMs;

  /** Whether the reconciliation converged successfully. */
  private boolean converged;

  /** Error message if reconciliation failed. */
  private String errorMessage = "";

  /**
   * Creates a reconciliation result.
   *
   * @param variables the list of reconciled variables
   */
  public ReconciliationResult(List<ReconciliationVariable> variables) {
    this.variables = new ArrayList<ReconciliationVariable>(variables);
    this.grossErrors = new ArrayList<ReconciliationVariable>();
    this.converged = true;
  }

  /**
   * Returns all reconciliation variables.
   *
   * @return unmodifiable list of variables with reconciled values
   */
  public List<ReconciliationVariable> getVariables() {
    return Collections.unmodifiableList(variables);
  }

  /**
   * Returns the weighted sum of squared adjustments (objective value).
   *
   * @return the objective, should be close to degrees of freedom if no gross errors
   */
  public double getObjectiveValue() {
    return objectiveValue;
  }

  /**
   * Sets the objective value.
   *
   * @param objectiveValue the weighted sum of squares
   */
  public void setObjectiveValue(double objectiveValue) {
    this.objectiveValue = objectiveValue;
  }

  /**
   * Returns the chi-square test statistic.
   *
   * @return chi-square value for the global test
   */
  public double getChiSquareStatistic() {
    return chiSquareStatistic;
  }

  /**
   * Sets the chi-square test statistic.
   *
   * @param chiSquareStatistic the computed statistic
   */
  public void setChiSquareStatistic(double chiSquareStatistic) {
    this.chiSquareStatistic = chiSquareStatistic;
  }

  /**
   * Returns the degrees of freedom (number of constraints / redundant measurements).
   *
   * @return degrees of freedom
   */
  public int getDegreesOfFreedom() {
    return degreesOfFreedom;
  }

  /**
   * Sets the degrees of freedom.
   *
   * @param degreesOfFreedom number of constraints
   */
  public void setDegreesOfFreedom(int degreesOfFreedom) {
    this.degreesOfFreedom = degreesOfFreedom;
  }

  /**
   * Returns whether the global chi-square test passed.
   *
   * @return true if no evidence of gross errors at the specified confidence
   */
  public boolean isGlobalTestPassed() {
    return globalTestPassed;
  }

  /**
   * Sets the global test result.
   *
   * @param globalTestPassed true if passed
   */
  public void setGlobalTestPassed(boolean globalTestPassed) {
    this.globalTestPassed = globalTestPassed;
  }

  /**
   * Returns variables flagged as gross errors.
   *
   * @return unmodifiable list of gross error variables
   */
  public List<ReconciliationVariable> getGrossErrors() {
    return Collections.unmodifiableList(grossErrors);
  }

  /**
   * Adds a variable to the gross errors list.
   *
   * @param variable the variable flagged as a gross error
   */
  public void addGrossError(ReconciliationVariable variable) {
    grossErrors.add(variable);
  }

  /**
   * Returns whether any gross errors were detected.
   *
   * @return true if at least one variable was flagged
   */
  public boolean hasGrossErrors() {
    return !grossErrors.isEmpty();
  }

  /**
   * Returns constraint residuals before reconciliation.
   *
   * @return array of A*y values (should be non-zero if balances don't close)
   */
  public double[] getConstraintResidualsBefore() {
    return constraintResidualsBefore != null ? constraintResidualsBefore.clone() : new double[0];
  }

  /**
   * Sets constraint residuals before reconciliation.
   *
   * @param residuals the pre-reconciliation residuals
   */
  public void setConstraintResidualsBefore(double[] residuals) {
    this.constraintResidualsBefore = residuals != null ? residuals.clone() : null;
  }

  /**
   * Returns constraint residuals after reconciliation.
   *
   * @return array of A*x_adj values (should be near-zero)
   */
  public double[] getConstraintResidualsAfter() {
    return constraintResidualsAfter != null ? constraintResidualsAfter.clone() : new double[0];
  }

  /**
   * Sets constraint residuals after reconciliation.
   *
   * @param residuals the post-reconciliation residuals
   */
  public void setConstraintResidualsAfter(double[] residuals) {
    this.constraintResidualsAfter = residuals != null ? residuals.clone() : null;
  }

  /**
   * Returns the computation time.
   *
   * @return time in milliseconds
   */
  public long getComputeTimeMs() {
    return computeTimeMs;
  }

  /**
   * Sets the computation time.
   *
   * @param computeTimeMs time in milliseconds
   */
  public void setComputeTimeMs(long computeTimeMs) {
    this.computeTimeMs = computeTimeMs;
  }

  /**
   * Returns whether the reconciliation converged successfully.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Sets the convergence flag.
   *
   * @param converged true if converged
   */
  public void setConverged(boolean converged) {
    this.converged = converged;
  }

  /**
   * Returns the error message if reconciliation failed.
   *
   * @return error message, or empty string if successful
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Sets the error message.
   *
   * @param errorMessage the error description
   */
  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  /**
   * Returns a JSON representation of the reconciliation result.
   *
   * @return JSON string with all variables, statistics, and gross errors
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"converged\": ").append(converged).append(",\n");
    sb.append("  \"objectiveValue\": ").append(objectiveValue).append(",\n");
    sb.append("  \"chiSquareStatistic\": ").append(chiSquareStatistic).append(",\n");
    sb.append("  \"degreesOfFreedom\": ").append(degreesOfFreedom).append(",\n");
    sb.append("  \"globalTestPassed\": ").append(globalTestPassed).append(",\n");
    sb.append("  \"computeTimeMs\": ").append(computeTimeMs).append(",\n");
    if (!errorMessage.isEmpty()) {
      sb.append("  \"errorMessage\": \"").append(errorMessage).append("\",\n");
    }
    sb.append("  \"variables\": [\n");
    for (int i = 0; i < variables.size(); i++) {
      ReconciliationVariable v = variables.get(i);
      sb.append("    {");
      sb.append("\"name\": \"").append(v.getName()).append("\", ");
      sb.append("\"measured\": ").append(v.getMeasuredValue()).append(", ");
      sb.append("\"reconciled\": ").append(v.getReconciledValue()).append(", ");
      sb.append("\"adjustment\": ").append(v.getAdjustment()).append(", ");
      sb.append("\"uncertainty\": ").append(v.getUncertainty()).append(", ");
      sb.append("\"normalizedResidual\": ").append(v.getNormalizedResidual()).append(", ");
      sb.append("\"grossError\": ").append(v.isGrossError());
      if (!v.getUnit().isEmpty()) {
        sb.append(", \"unit\": \"").append(v.getUnit()).append("\"");
      }
      sb.append("}");
      if (i < variables.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Returns a human-readable summary report.
   *
   * @return formatted text report
   */
  public String toReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Data Reconciliation Report ===\n");
    sb.append(String.format("Converged: %s\n", converged));
    sb.append(String.format("Objective (weighted SSQ): %.4f\n", objectiveValue));
    sb.append(String.format("Chi-square statistic: %.4f (df=%d)\n", chiSquareStatistic,
        degreesOfFreedom));
    sb.append(String.format("Global test passed: %s\n", globalTestPassed));
    sb.append(String.format("Compute time: %d ms\n\n", computeTimeMs));

    sb.append(String.format("%-20s %12s %12s %12s %8s %8s\n", "Variable", "Measured", "Reconciled",
        "Adjustment", "|r_norm|", "Flag"));
    sb.append(String.format("%-20s %12s %12s %12s %8s %8s\n", "--------", "--------", "----------",
        "----------", "--------", "----"));
    for (ReconciliationVariable v : variables) {
      sb.append(String.format("%-20s %12.4f %12.4f %12.4f %8.3f %8s\n", v.getName(),
          v.getMeasuredValue(), v.getReconciledValue(), v.getAdjustment(),
          Math.abs(v.getNormalizedResidual()), v.isGrossError() ? "**GE**" : "ok"));
    }

    if (!grossErrors.isEmpty()) {
      sb.append("\nGross Errors Detected:\n");
      for (ReconciliationVariable ge : grossErrors) {
        sb.append(String.format("  %s: |r|=%.3f (threshold exceeded)\n", ge.getName(),
            Math.abs(ge.getNormalizedResidual())));
      }
    }
    return sb.toString();
  }

  /**
   * Returns a summary string.
   *
   * @return brief summary of the result
   */
  @Override
  public String toString() {
    return String.format(
        "ReconciliationResult[converged=%s, obj=%.4f, chi2=%.4f, df=%d, grossErrors=%d]", converged,
        objectiveValue, chiSquareStatistic, degreesOfFreedom, grossErrors.size());
  }
}
