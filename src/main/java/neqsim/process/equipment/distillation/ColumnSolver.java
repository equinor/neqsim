package neqsim.process.equipment.distillation;

import java.util.UUID;

/**
 * Internal strategy interface for distillation column solvers.
 *
 * <p>
 * The interface is package-private to keep the public {@link DistillationColumn} API stable while
 * solver implementations are split out of the column class.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
interface ColumnSolver {

  /**
   * Solve the supplied distillation column.
   *
   * @param column the column to solve
   * @param id calculation identifier for this solve
   * @return summary metrics from the completed solve
   */
  ColumnSolveResult solve(DistillationColumn column, UUID id);

  /**
   * Get the solver type represented by this strategy.
   *
   * @return the matching {@link DistillationColumn.SolverType}
   */
  DistillationColumn.SolverType getSolverType();
}
