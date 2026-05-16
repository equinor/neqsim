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
  /** Naphtali-Sandholm simultaneous MESH strategy. */
  private static final ColumnSolver NAPHTALI_SANDHOLM = new NaphtaliSandholmColumnSolver();
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
      case NAPHTALI_SANDHOLM:
        return NAPHTALI_SANDHOLM;
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
      DistillationColumn fallbackCandidate = createDampedFallbackCandidate(column);
      boolean fallbackApplied = false;
      try {
        column.solveNewton(id);
      } catch (RuntimeException exception) {
        applyDampedFallback(column, fallbackCandidate, id, "Newton failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Newton required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Newton did not satisfy convergence criteria", null);
      }
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.NEWTON;
    }

    /**
     * Create a clean damped-substitution fallback candidate before Newton changes tray state.
     *
     * @param column column to copy
     * @return copied column, or {@code null} if the copy fails
     */
    private DistillationColumn createDampedFallbackCandidate(DistillationColumn column) {
      try {
        return (DistillationColumn) column.copy();
      } catch (RuntimeException exception) {
        return null;
      } catch (StackOverflowError error) {
        return null;
      }
    }

    /**
     * Apply damped substitution from a pre-Newton candidate when available.
     *
     * @param column live column to update
     * @param fallbackCandidate clean fallback candidate, or {@code null} if unavailable
     * @param id calculation identifier
     * @param reason rejection reason
     * @param exception optional accelerator exception
     */
    private void applyDampedFallback(DistillationColumn column,
        DistillationColumn fallbackCandidate, UUID id, String reason, RuntimeException exception) {
      if (fallbackCandidate != null) {
        try {
          fallbackCandidate.setDoInitializion(true);
          fallbackCandidate.solveDampedSubstitution(id);
          column.acceptDampedFallbackCandidate(fallbackCandidate, reason);
          return;
        } catch (RuntimeException fallbackException) {
          column.solveDampedFallbackAfterAcceleratorFailure(id, fallbackException);
          return;
        }
      }
      if (exception != null) {
        column.solveDampedFallbackAfterAcceleratorFailure(id, exception);
      } else {
        column.solveDampedFallbackAfterRejectedAccelerator(id, reason);
      }
    }
  }

  /** Naphtali-Sandholm simultaneous MESH adapter. */
  private static final class NaphtaliSandholmColumnSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      column.solveNaphtaliSandholm(id);
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.NAPHTALI_SANDHOLM;
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
