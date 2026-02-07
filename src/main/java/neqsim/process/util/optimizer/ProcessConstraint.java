package neqsim.process.util.optimizer;

import neqsim.process.processmodel.ProcessSystem;

/**
 * Unified constraint interface for process optimization.
 *
 * <p>
 * This interface provides a common contract for constraints used by both internal NeqSim optimizers
 * ({@link ProductionOptimizer}, {@link ProcessOptimizationEngine}) and external optimizers (SciPy,
 * NLopt, Pyomo, etc.) through {@link ProcessSimulationEvaluator}.
 * </p>
 *
 * <p>
 * All constraint types in the system implement this interface:
 * </p>
 * <ul>
 * <li>{@link ProductionOptimizer.OptimizationConstraint} — functional constraints on process-level
 * metrics</li>
 * <li>{@link ProcessSimulationEvaluator.ConstraintDefinition} — NLP-style bounds for external
 * optimizers</li>
 * <li>Equipment capacity constraints via {@link CapacityConstraintAdapter}</li>
 * </ul>
 *
 * <p>
 * <strong>Convention:</strong> margin is positive when satisfied, negative when violated.
 * {@code margin(process) &gt;= 0} means the constraint is satisfied.
 * </p>
 *
 * <p>
 * <strong>Example for external SciPy optimizer:</strong>
 * </p>
 *
 * <pre>
 * // Java side: build constraint vector from ProcessConstraint list
 * List&lt;ProcessConstraint&gt; allConstraints = evaluator.getAllProcessConstraints();
 * double[] margins = new double[allConstraints.size()];
 * for (int i = 0; i &lt; allConstraints.size(); i++) {
 *   margins[i] = allConstraints.get(i).margin(process);
 * }
 * // margins array is the g(x) &gt;= 0 vector for NLP solvers
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ProductionOptimizer.OptimizationConstraint
 * @see ProcessSimulationEvaluator.ConstraintDefinition
 * @see CapacityConstraintAdapter
 * @see ConstraintPenaltyCalculator
 */
public interface ProcessConstraint {

  /**
   * Returns the name of this constraint.
   *
   * @return constraint name, never null
   */
  String getName();

  /**
   * Computes the constraint margin for the given process state.
   *
   * <p>
   * Convention:
   * </p>
   * <ul>
   * <li>margin &gt;= 0 means the constraint is satisfied</li>
   * <li>margin &lt; 0 means the constraint is violated (more negative = worse)</li>
   * </ul>
   *
   * @param process the process system in its current state (already run)
   * @return constraint margin (positive = satisfied, negative = violated)
   */
  double margin(ProcessSystem process);

  /**
   * Checks if this constraint is satisfied for the given process state.
   *
   * <p>
   * Default implementation returns {@code margin(process) >= 0}.
   * </p>
   *
   * @param process the process system
   * @return true if satisfied
   */
  default boolean isSatisfied(ProcessSystem process) {
    return margin(process) >= 0.0;
  }

  /**
   * Returns the severity of this constraint.
   *
   * <p>
   * Uses the unified {@link ConstraintSeverityLevel} enum that maps across all constraint types.
   * </p>
   *
   * @return constraint severity level
   */
  ConstraintSeverityLevel getSeverityLevel();

  /**
   * Returns the penalty weight for this constraint.
   *
   * <p>
   * Higher weight means constraint violations are penalized more strongly in penalty-based
   * optimization methods.
   * </p>
   *
   * @return penalty weight (non-negative)
   */
  double getPenaltyWeight();

  /**
   * Computes the penalty for the current constraint violation.
   *
   * <p>
   * Returns 0 when satisfied, positive value proportional to violation magnitude when violated.
   * Default implementation uses quadratic penalty: {@code weight * margin^2}.
   * </p>
   *
   * @param process the process system
   * @return penalty value (0 if satisfied, positive if violated)
   */
  default double penalty(ProcessSystem process) {
    double m = margin(process);
    if (m >= 0.0) {
      return 0.0;
    }
    return getPenaltyWeight() * m * m;
  }

  /**
   * Returns a human-readable description of this constraint.
   *
   * @return constraint description, may be empty but never null
   */
  default String getDescription() {
    return "";
  }

  /**
   * Checks if this is a hard constraint that makes the solution infeasible when violated.
   *
   * @return true if CRITICAL or HARD severity
   */
  default boolean isHard() {
    ConstraintSeverityLevel level = getSeverityLevel();
    return level == ConstraintSeverityLevel.CRITICAL || level == ConstraintSeverityLevel.HARD;
  }
}
