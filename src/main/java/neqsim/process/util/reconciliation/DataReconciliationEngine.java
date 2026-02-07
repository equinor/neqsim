package neqsim.process.util.reconciliation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.simple.SimpleMatrix;

/**
 * Data reconciliation engine using weighted least squares (WLS) with linear constraints.
 *
 * <p>
 * Adjusts measured process variables so that mass (and optionally energy) balance constraints are
 * exactly satisfied, while minimizing the weighted sum of squared adjustments. The weights are
 * inversely proportional to the measurement variance (1/sigma^2).
 * </p>
 *
 * <p>
 * The engine uses the classic linear WLS data reconciliation formula:
 * </p>
 *
 * <p>
 * Given measurements y, covariance V = diag(sigma_i^2), and constraints A*x = 0:
 * </p>
 *
 * $$x_{adj} = y - V \cdot A^T \cdot (A \cdot V \cdot A^T)^{-1} \cdot A \cdot y$$
 *
 * <p>
 * After reconciliation, a global chi-square test and per-variable normalized residual test are
 * applied to detect gross measurement errors at a configurable confidence level.
 * </p>
 *
 * <p>
 * <b>Typical usage from Python (via jneqsim):</b>
 * </p>
 *
 * <pre>
 * engine = DataReconciliationEngine()
 *
 * # Add measured variables with name, measured value, uncertainty (sigma)
 * engine.addVariable(ReconciliationVariable("feed", 1000.0, 20.0))
 * engine.addVariable(ReconciliationVariable("product", 600.0, 15.0))
 * engine.addVariable(ReconciliationVariable("waste", 380.0, 10.0))
 *
 * # Add constraint: feed - product - waste = 0 (mass balance)
 * engine.addConstraint([1.0, -1.0, -1.0])
 *
 * # Reconcile and check results
 * result = engine.reconcile()
 * print(result.toReport())
 * </pre>
 *
 * @author Process Optimization Team
 * @version 1.0
 */
public class DataReconciliationEngine implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(DataReconciliationEngine.class);

  /** Registered reconciliation variables. */
  private final List<ReconciliationVariable> variables;

  /** Constraint rows (each double[] has length = number of variables). */
  private final List<double[]> constraintRows;

  /** Optional constraint names for reporting. */
  private final List<String> constraintNames;

  /**
   * Gross error detection threshold (z-value). Default 1.96 for 95% confidence. Set to 2.576 for
   * 99%.
   */
  private double grossErrorThreshold = 1.96;

  /**
   * Creates an empty data reconciliation engine.
   */
  public DataReconciliationEngine() {
    this.variables = new ArrayList<ReconciliationVariable>();
    this.constraintRows = new ArrayList<double[]>();
    this.constraintNames = new ArrayList<String>();
  }

  /**
   * Adds a variable to the reconciliation problem.
   *
   * @param variable the reconciliation variable with measured value and uncertainty
   * @return this engine for chaining
   */
  public DataReconciliationEngine addVariable(ReconciliationVariable variable) {
    variables.add(variable);
    return this;
  }

  /**
   * Adds a linear constraint row: sum(coefficients[i] * variable[i]) = 0.
   *
   * <p>
   * For a mass balance around a mixer with feed1, feed2 going in and product going out:
   * {@code addConstraint(new double[]{1.0, 1.0, -1.0})} means feed1 + feed2 - product = 0.
   * </p>
   *
   * @param coefficients array of coefficients, one per variable in order added. Use +1 for inlets,
   *        -1 for outlets.
   * @return this engine for chaining
   * @throws IllegalArgumentException if coefficients length does not match variable count
   */
  public DataReconciliationEngine addConstraint(double[] coefficients) {
    return addConstraint(coefficients, "Constraint_" + (constraintRows.size() + 1));
  }

  /**
   * Adds a named linear constraint row: sum(coefficients[i] * variable[i]) = 0.
   *
   * @param coefficients array of coefficients, one per variable
   * @param name descriptive name for this constraint (e.g., "Separator mass balance")
   * @return this engine for chaining
   * @throws IllegalArgumentException if coefficients length does not match variable count
   */
  public DataReconciliationEngine addConstraint(double[] coefficients, String name) {
    if (coefficients.length != variables.size()) {
      throw new IllegalArgumentException("Constraint length " + coefficients.length
          + " does not match variable count " + variables.size());
    }
    constraintRows.add(coefficients.clone());
    constraintNames.add(name);
    return this;
  }

  /**
   * Returns the current list of variables.
   *
   * @return unmodifiable list of registered variables
   */
  public List<ReconciliationVariable> getVariables() {
    return Collections.unmodifiableList(variables);
  }

  /**
   * Returns the number of variables.
   *
   * @return variable count
   */
  public int getVariableCount() {
    return variables.size();
  }

  /**
   * Returns the number of constraints.
   *
   * @return constraint count
   */
  public int getConstraintCount() {
    return constraintRows.size();
  }

  /**
   * Returns the gross error detection threshold (z-value).
   *
   * @return threshold value (default 1.96 for 95% confidence)
   */
  public double getGrossErrorThreshold() {
    return grossErrorThreshold;
  }

  /**
   * Sets the gross error detection threshold (z-value).
   *
   * @param threshold z-value, e.g., 1.96 (95%), 2.576 (99%), 3.291 (99.9%)
   * @return this engine for chaining
   */
  public DataReconciliationEngine setGrossErrorThreshold(double threshold) {
    this.grossErrorThreshold = threshold;
    return this;
  }

  /**
   * Clears all variables and constraints.
   */
  public void clear() {
    variables.clear();
    constraintRows.clear();
    constraintNames.clear();
  }

  /**
   * Clears only the constraints, keeping variables.
   */
  public void clearConstraints() {
    constraintRows.clear();
    constraintNames.clear();
  }

  /**
   * Performs the data reconciliation using weighted least squares with linear constraints.
   *
   * <p>
   * The closed-form solution is:
   * </p>
   *
   * <pre>
   * x_adj = y - V * A ^ T * (A * V * A ^ T) ^ (-1) * A * y
   * </pre>
   *
   * <p>
   * where y = measurement vector, V = diag(sigma^2), A = constraint matrix.
   * </p>
   *
   * <p>
   * After solving, computes normalized residuals and flags gross errors.
   * </p>
   *
   * @return reconciliation result with adjusted values and statistical tests
   */
  public ReconciliationResult reconcile() {
    long startTime = System.currentTimeMillis();
    int n = variables.size();
    int m = constraintRows.size();

    // Validation
    if (n == 0) {
      ReconciliationResult result = new ReconciliationResult(variables);
      result.setConverged(false);
      result.setErrorMessage("No variables added");
      return result;
    }
    if (m == 0) {
      ReconciliationResult result = new ReconciliationResult(variables);
      result.setConverged(false);
      result.setErrorMessage("No constraints added");
      return result;
    }
    if (m >= n) {
      ReconciliationResult result = new ReconciliationResult(variables);
      result.setConverged(false);
      result.setErrorMessage(
          "Need more variables (" + n + ") than constraints (" + m + ") for reconciliation");
      return result;
    }

    try {
      return solveWLS(n, m, startTime);
    } catch (Exception ex) {
      logger.error("Data reconciliation failed", ex);
      ReconciliationResult result = new ReconciliationResult(variables);
      result.setConverged(false);
      result.setErrorMessage("Solver exception: " + ex.getMessage());
      result.setComputeTimeMs(System.currentTimeMillis() - startTime);
      return result;
    }
  }

  /**
   * Solves the weighted least squares reconciliation problem.
   *
   * <p>
   * Builds the measurement vector y, covariance matrix V, and constraint matrix A, then applies the
   * closed-form WLS formula. Computes constraint residuals before and after reconciliation,
   * objective value, global chi-square test, and per-variable gross error detection.
   * </p>
   *
   * @param n number of variables
   * @param m number of constraints
   * @param startTime system time when reconciliation started, for timing measurement
   * @return the reconciliation result
   */
  private ReconciliationResult solveWLS(int n, int m, long startTime) {
    // Build measurement vector y (n x 1)
    SimpleMatrix y = new SimpleMatrix(n, 1);
    for (int i = 0; i < n; i++) {
      y.set(i, 0, variables.get(i).getMeasuredValue());
    }

    // Build covariance matrix V = diag(sigma^2) (n x n)
    SimpleMatrix bigV = new SimpleMatrix(n, n);
    for (int i = 0; i < n; i++) {
      double sigma = variables.get(i).getUncertainty();
      bigV.set(i, i, sigma * sigma);
    }

    // Build constraint matrix A (m x n)
    SimpleMatrix bigA = new SimpleMatrix(m, n);
    for (int i = 0; i < m; i++) {
      double[] row = constraintRows.get(i);
      for (int j = 0; j < n; j++) {
        bigA.set(i, j, row[j]);
      }
    }

    // Constraint residuals before: r_before = A * y
    SimpleMatrix rBefore = bigA.mult(y);
    double[] residualsBefore = new double[m];
    for (int i = 0; i < m; i++) {
      residualsBefore[i] = rBefore.get(i, 0);
    }

    // Solve: x_adj = y - V * A^T * (A * V * A^T)^(-1) * A * y
    // Step 1: A * V * A^T (m x m)
    SimpleMatrix avat = bigA.mult(bigV).mult(bigA.transpose());

    // Step 2: (A * V * A^T)^(-1) * A * y = solve(AVAT, A*y) for numerical stability
    SimpleMatrix avatInv = avat.invert();

    // Step 3: correction = V * A^T * avatInv * A * y
    SimpleMatrix correction = bigV.mult(bigA.transpose()).mult(avatInv).mult(bigA).mult(y);

    // Step 4: reconciled = y - correction
    SimpleMatrix xAdj = y.minus(correction);

    // Constraint residuals after: r_after = A * x_adj (should be ~0)
    SimpleMatrix rAfter = bigA.mult(xAdj);
    double[] residualsAfter = new double[m];
    for (int i = 0; i < m; i++) {
      residualsAfter[i] = rAfter.get(i, 0);
    }

    // Compute objective value: sum((x_adj - y)^2 / sigma^2)
    double objectiveValue = 0.0;
    for (int i = 0; i < n; i++) {
      double adj = xAdj.get(i, 0) - y.get(i, 0);
      double sigma = variables.get(i).getUncertainty();
      objectiveValue += (adj * adj) / (sigma * sigma);
    }

    // Update variables with reconciled values
    for (int i = 0; i < n; i++) {
      variables.get(i).setReconciledValue(xAdj.get(i, 0));
    }

    // Gross error detection via normalized residuals
    // Covariance of adjustments: V_adj = V - V * A^T * (AVAT)^(-1) * A * V
    SimpleMatrix projectionM = bigV.mult(bigA.transpose()).mult(avatInv).mult(bigA).mult(bigV);
    SimpleMatrix vAdj = bigV.minus(projectionM);

    detectGrossErrors(n, vAdj);

    // Build result
    ReconciliationResult result = new ReconciliationResult(variables);
    result.setObjectiveValue(objectiveValue);
    result.setChiSquareStatistic(objectiveValue);
    result.setDegreesOfFreedom(m);
    result.setConstraintResidualsBefore(residualsBefore);
    result.setConstraintResidualsAfter(residualsAfter);

    // Global test: chi-square at given confidence level
    // Use approximate critical values (chi-square table)
    double chiCritical = getChiSquareCritical(m);
    result.setGlobalTestPassed(objectiveValue <= chiCritical);

    // Collect gross errors
    for (ReconciliationVariable v : variables) {
      if (v.isGrossError()) {
        result.addGrossError(v);
      }
    }

    result.setComputeTimeMs(System.currentTimeMillis() - startTime);
    logger.info("Data reconciliation completed in {} ms. Objective: {}, Gross errors: {}",
        result.getComputeTimeMs(), objectiveValue, result.getGrossErrors().size());

    return result;
  }

  /**
   * Detects gross errors using normalized residual tests.
   *
   * <p>
   * For each variable i, the normalized residual is:
   * </p>
   *
   * <pre>
   * r_i = (x_adj_i - y_i) / sqrt(V_ii - V_adj_ii)
   * </pre>
   *
   * <p>
   * If |r_i| exceeds the gross error threshold (z-value), the variable is flagged.
   * </p>
   *
   * @param n number of variables
   * @param vAdj covariance matrix of reconciled adjustments (V - V*A^T*(AVA^T)^-1*A*V)
   */
  private void detectGrossErrors(int n, SimpleMatrix vAdj) {
    for (int i = 0; i < n; i++) {
      ReconciliationVariable v = variables.get(i);
      double sigma = v.getUncertainty();
      double vii = sigma * sigma;
      double vadjii = vAdj.get(i, i);
      double denominator = vii - vadjii;

      double normalizedResidual = 0.0;
      if (denominator > 1e-20) {
        normalizedResidual =
            (v.getReconciledValue() - v.getMeasuredValue()) / Math.sqrt(denominator);
      }
      v.setNormalizedResidual(normalizedResidual);
      v.setGrossError(Math.abs(normalizedResidual) > grossErrorThreshold);

      if (v.isGrossError()) {
        logger.warn("Gross error detected: {} (|r|={} > {})", v.getName(),
            Math.abs(normalizedResidual), grossErrorThreshold);
      }
    }
  }

  /**
   * Returns an approximate chi-square critical value at 95% confidence.
   *
   * <p>
   * Uses the Wilson-Hilferty approximation for the chi-square distribution inverse CDF at the 95th
   * percentile.
   * </p>
   *
   * @param dof degrees of freedom (must be positive)
   * @return approximate chi-square critical value at 95% confidence
   */
  private double getChiSquareCritical(int dof) {
    if (dof <= 0) {
      return 0.0;
    }
    // Wilson-Hilferty approximation for chi-square inverse CDF at 95% (z = 1.645)
    double z = 1.645;
    double term = 1.0 - 2.0 / (9.0 * dof) + z * Math.sqrt(2.0 / (9.0 * dof));
    return dof * term * term * term;
  }

  /**
   * Performs reconciliation with iterative gross error elimination.
   *
   * <p>
   * After each reconciliation, the variable with the largest normalized residual exceeding the
   * threshold is removed (its uncertainty is set to a very large value, effectively ignoring it).
   * The reconciliation is repeated until no gross errors remain or the maximum elimination count is
   * reached.
   * </p>
   *
   * @param maxEliminations maximum number of variables to eliminate
   * @return reconciliation result with the final set of gross errors identified
   */
  public ReconciliationResult reconcileWithGrossErrorElimination(int maxEliminations) {
    // Save original uncertainties
    double[] originalUncertainties = new double[variables.size()];
    for (int i = 0; i < variables.size(); i++) {
      originalUncertainties[i] = variables.get(i).getUncertainty();
    }

    List<ReconciliationVariable> allGrossErrors = new ArrayList<ReconciliationVariable>();
    ReconciliationResult result = null;

    for (int iter = 0; iter < maxEliminations; iter++) {
      result = reconcile();
      if (!result.isConverged() || !result.hasGrossErrors()) {
        break;
      }

      // Find worst gross error
      ReconciliationVariable worst = null;
      double worstResidual = 0.0;
      for (ReconciliationVariable v : variables) {
        if (v.isGrossError() && Math.abs(v.getNormalizedResidual()) > worstResidual) {
          worst = v;
          worstResidual = Math.abs(v.getNormalizedResidual());
        }
      }

      if (worst == null) {
        break;
      }

      // Effectively remove this variable: set uncertainty very high
      logger.info("Eliminating gross error: {} (|r|={})", worst.getName(), worstResidual);
      worst.setUncertainty(1e12);
      allGrossErrors.add(worst);
    }

    // Restore original uncertainties and flag all identified gross errors
    for (int i = 0; i < variables.size(); i++) {
      variables.get(i).setUncertainty(originalUncertainties[i]);
    }
    for (ReconciliationVariable ge : allGrossErrors) {
      ge.setGrossError(true);
    }

    // Re-run final reconciliation with original uncertainties for reporting
    if (result != null && result.isConverged()) {
      result = reconcile();
      // Re-flag the eliminated variables
      for (ReconciliationVariable ge : allGrossErrors) {
        ge.setGrossError(true);
        if (!result.getGrossErrors().contains(ge)) {
          result.addGrossError(ge);
        }
      }
    }

    return result;
  }

  /**
   * Convenience method to add a mass balance constraint around a node.
   *
   * <p>
   * Specifies which variables are inlets (+1) and which are outlets (-1) by their names. All other
   * variables get coefficient 0.
   * </p>
   *
   * @param name constraint name (e.g., "Separator mass balance")
   * @param inletNames names of inlet variables
   * @param outletNames names of outlet variables
   * @return this engine for chaining
   * @throws IllegalArgumentException if a variable name is not found
   */
  public DataReconciliationEngine addMassBalanceConstraint(String name, List<String> inletNames,
      List<String> outletNames) {
    double[] coefficients = new double[variables.size()];
    Arrays.fill(coefficients, 0.0);

    for (String inlet : inletNames) {
      int idx = findVariableIndex(inlet);
      if (idx < 0) {
        throw new IllegalArgumentException("Variable not found: " + inlet);
      }
      coefficients[idx] = 1.0;
    }

    for (String outlet : outletNames) {
      int idx = findVariableIndex(outlet);
      if (idx < 0) {
        throw new IllegalArgumentException("Variable not found: " + outlet);
      }
      coefficients[idx] = -1.0;
    }

    return addConstraint(coefficients, name);
  }

  /**
   * Convenience method to add a mass balance constraint using arrays of names.
   *
   * @param name constraint name
   * @param inletNames inlet variable names
   * @param outletNames outlet variable names
   * @return this engine for chaining
   * @throws IllegalArgumentException if a variable name is not found
   */
  public DataReconciliationEngine addMassBalanceConstraint(String name, String[] inletNames,
      String[] outletNames) {
    return addMassBalanceConstraint(name, Arrays.asList(inletNames), Arrays.asList(outletNames));
  }

  /**
   * Finds the index of a variable by name.
   *
   * @param name variable name to search for
   * @return index in the variables list, or -1 if not found
   */
  private int findVariableIndex(String name) {
    for (int i = 0; i < variables.size(); i++) {
      if (variables.get(i).getName().equals(name)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns a variable by name.
   *
   * @param name the variable name
   * @return the variable, or null if not found
   */
  public ReconciliationVariable getVariable(String name) {
    int idx = findVariableIndex(name);
    return idx >= 0 ? variables.get(idx) : null;
  }
}
