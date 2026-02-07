package neqsim.process.util.optimizer;

import neqsim.process.equipment.capacity.CapacityConstraint;

/**
 * Unified severity level for all constraint types in the optimization framework.
 *
 * <p>
 * This enum provides a common 4-level severity classification that maps between:
 * </p>
 * <ul>
 * <li>{@link CapacityConstraint.ConstraintSeverity} — equipment-level (CRITICAL/HARD/SOFT/ADVISORY)
 * </li>
 * <li>{@link ProductionOptimizer.ConstraintSeverity} — optimizer-level (HARD/SOFT)</li>
 * <li>{@link ProcessSimulationEvaluator.ConstraintDefinition#isHard()} — boolean flag</li>
 * </ul>
 *
 * <p>
 * <strong>Optimizer behavior by level:</strong>
 * </p>
 * <table>
 * <caption>Constraint severity levels and optimizer behavior</caption>
 * <tr>
 * <th>Level</th>
 * <th>Optimizer Impact</th>
 * <th>Example</th>
 * </tr>
 * <tr>
 * <td>CRITICAL</td>
 * <td>Solution rejected; optimizer may abort</td>
 * <td>Compressor surge, overspeed trip</td>
 * </tr>
 * <tr>
 * <td>HARD</td>
 * <td>Solution marked infeasible</td>
 * <td>Design capacity, max power</td>
 * </tr>
 * <tr>
 * <td>SOFT</td>
 * <td>Penalty applied to objective</td>
 * <td>Recommended operating range</td>
 * </tr>
 * <tr>
 * <td>ADVISORY</td>
 * <td>No optimization impact; reporting only</td>
 * <td>Turndown ratio, design deviation</td>
 * </tr>
 * </table>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ProcessConstraint
 */
public enum ConstraintSeverityLevel {

  /**
   * Critical violation — equipment damage or safety hazard. Optimizer must stop or reject solution
   * immediately.
   */
  CRITICAL,

  /**
   * Hard violation — solution is infeasible if violated. Optimizer marks solution as infeasible.
   */
  HARD,

  /**
   * Soft violation — undesirable but acceptable. Optimizer applies penalty to objective function.
   */
  SOFT,

  /**
   * Advisory — informational only with no optimization impact. Used for reporting and monitoring.
   */
  ADVISORY;

  /**
   * Converts from {@link CapacityConstraint.ConstraintSeverity} to unified severity.
   *
   * @param severity the equipment-level severity
   * @return the equivalent unified severity level
   */
  public static ConstraintSeverityLevel fromCapacitySeverity(
      CapacityConstraint.ConstraintSeverity severity) {
    if (severity == null) {
      return HARD;
    }
    switch (severity) {
      case CRITICAL:
        return CRITICAL;
      case HARD:
        return HARD;
      case SOFT:
        return SOFT;
      case ADVISORY:
        return ADVISORY;
      default:
        return HARD;
    }
  }

  /**
   * Converts from {@link ProductionOptimizer.ConstraintSeverity} to unified severity.
   *
   * @param severity the optimizer-level severity (HARD or SOFT)
   * @return the equivalent unified severity level
   */
  public static ConstraintSeverityLevel fromOptimizerSeverity(
      ProductionOptimizer.ConstraintSeverity severity) {
    if (severity == null) {
      return HARD;
    }
    switch (severity) {
      case HARD:
        return HARD;
      case SOFT:
        return SOFT;
      default:
        return HARD;
    }
  }

  /**
   * Converts from the boolean {@code isHard} flag used in
   * {@link ProcessSimulationEvaluator.ConstraintDefinition} to unified severity.
   *
   * @param isHard true for hard constraints, false for soft
   * @return HARD if isHard is true, SOFT otherwise
   */
  public static ConstraintSeverityLevel fromIsHard(boolean isHard) {
    return isHard ? HARD : SOFT;
  }

  /**
   * Converts this unified severity to the 2-level {@link ProductionOptimizer.ConstraintSeverity}.
   *
   * <p>
   * CRITICAL maps to HARD (since the 2-level enum has no critical level). ADVISORY maps to SOFT.
   * </p>
   *
   * @return the equivalent optimizer severity
   */
  public ProductionOptimizer.ConstraintSeverity toOptimizerSeverity() {
    switch (this) {
      case CRITICAL:
      case HARD:
        return ProductionOptimizer.ConstraintSeverity.HARD;
      case SOFT:
      case ADVISORY:
        return ProductionOptimizer.ConstraintSeverity.SOFT;
      default:
        return ProductionOptimizer.ConstraintSeverity.HARD;
    }
  }

  /**
   * Converts this unified severity to the 4-level {@link CapacityConstraint.ConstraintSeverity}.
   *
   * @return the equivalent equipment-level severity
   */
  public CapacityConstraint.ConstraintSeverity toCapacitySeverity() {
    switch (this) {
      case CRITICAL:
        return CapacityConstraint.ConstraintSeverity.CRITICAL;
      case HARD:
        return CapacityConstraint.ConstraintSeverity.HARD;
      case SOFT:
        return CapacityConstraint.ConstraintSeverity.SOFT;
      case ADVISORY:
        return CapacityConstraint.ConstraintSeverity.ADVISORY;
      default:
        return CapacityConstraint.ConstraintSeverity.HARD;
    }
  }

  /**
   * Converts this unified severity to a boolean hard/soft flag.
   *
   * @return true for CRITICAL and HARD; false for SOFT and ADVISORY
   */
  public boolean toIsHard() {
    return this == CRITICAL || this == HARD;
  }
}
