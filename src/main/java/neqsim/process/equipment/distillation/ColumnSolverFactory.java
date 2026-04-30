package neqsim.process.equipment.distillation;

import java.util.UUID;

/**
 * Factory for the built-in distillation column solver strategies.
 *
 * <p>
 * This class keeps enum dispatch outside {@link DistillationColumn}. The first implementation is a
 * thin adapter layer around the existing numerical methods so that the public behavior remains
 * unchanged while future rigorous solvers can be added as separate strategies.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
final class ColumnSolverFactory {
  /** Direct substitution strategy. */
  private static final ColumnSolver DIRECT = new DirectSubstitutionSolver();
  /** Damped substitution strategy. */
  private static final ColumnSolver DAMPED = new DampedSubstitutionSolver();
  /** Inside-out strategy. */
  private static final ColumnSolver INSIDE_OUT = new InsideOutSolver();
  /** Wegstein strategy. */
  private static final ColumnSolver WEGSTEIN = new WegsteinSolver();
  /** Sum-rates strategy. */
  private static final ColumnSolver SUM_RATES = new SumRatesSolver();
  /** Temperature Newton strategy. */
  private static final ColumnSolver NEWTON = new TemperatureNewtonSolver();
  /** MESH residual-monitored strategy. */
  private static final ColumnSolver MESH_RESIDUAL = new MeshResidualSolver();

  /** Utility class constructor. */
  private ColumnSolverFactory() {}

  /**
   * Create the strategy for a solver type.
   *
   * @param solverType requested solver type
   * @return solver strategy for the requested type
   */
  static ColumnSolver create(DistillationColumn.SolverType solverType) {
    switch (solverType) {
      case DAMPED_SUBSTITUTION:
        return DAMPED;
      case INSIDE_OUT:
        return INSIDE_OUT;
      case WEGSTEIN:
        return WEGSTEIN;
      case SUM_RATES:
        return SUM_RATES;
      case NEWTON:
        return NEWTON;
      case MESH_RESIDUAL:
        return MESH_RESIDUAL;
      case DIRECT_SUBSTITUTION:
      default:
        return DIRECT;
    }
  }

  /** Direct substitution adapter. */
  private static final class DirectSubstitutionSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      column.solveDirectSubstitution(id);
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.DIRECT_SUBSTITUTION;
    }
  }

  /** Damped substitution adapter. */
  private static final class DampedSubstitutionSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      column.solveDampedSubstitution(id);
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.DAMPED_SUBSTITUTION;
    }
  }

  /** Inside-out adapter. */
  private static final class InsideOutSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      column.solveInsideOut(id);
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.INSIDE_OUT;
    }
  }

  /** Wegstein adapter. */
  private static final class WegsteinSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      column.solveWegstein(id);
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.WEGSTEIN;
    }
  }

  /** Sum-rates adapter. */
  private static final class SumRatesSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      column.solveSumRates(id);
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.SUM_RATES;
    }
  }

  /** Temperature-Newton adapter. */
  private static final class TemperatureNewtonSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      column.solveNewton(id);
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.NEWTON;
    }
  }

  /** MESH residual-monitored adapter. */
  private static final class MeshResidualSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      column.solveMeshResidual(id);
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.MESH_RESIDUAL;
    }
  }
}
