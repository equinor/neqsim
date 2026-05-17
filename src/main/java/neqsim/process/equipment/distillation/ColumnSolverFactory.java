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
      boolean fallbackApplied = false;
      try {
        column.solveInsideOut(id);
      } catch (RuntimeException exception) {
        applyDampedFallback(column, null, id, "Inside-out failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, null, id,
            "Inside-out required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !column.solved()) {
        applyDampedFallback(column, null, id, "Inside-out did not satisfy convergence criteria",
            null);
      }
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
      DistillationColumn fallbackCandidate = createDampedFallbackCandidate(column);
      boolean fallbackApplied = false;
      try {
        column.solveWegstein(id);
      } catch (RuntimeException exception) {
        applyDampedFallback(column, fallbackCandidate, id, "Wegstein failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Wegstein required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Wegstein did not satisfy convergence criteria", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied) {
        validateAcceleratedProductSplit(column, fallbackCandidate, id, "Wegstein");
      }
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
      DistillationColumn fallbackCandidate = createDampedFallbackCandidate(column);
      boolean fallbackApplied = false;
      try {
        column.solveSumRates(id);
      } catch (RuntimeException exception) {
        applyDampedFallback(column, fallbackCandidate, id, "Sum-rates failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Sum-rates required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Sum-rates did not satisfy convergence criteria", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied) {
        validateAcceleratedProductSplit(column, fallbackCandidate, id, "Sum-rates");
      }
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
      boolean fallbackApplied = false;
      try {
        column.solveNewton(id);
      } catch (RuntimeException exception) {
        applyDampedFallback(column, null, id, "Newton failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, null, id, "Newton required guarded feed-flash product fallback",
            null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !column.solved()) {
        applyDampedFallback(column, null, id, "Newton did not satisfy convergence criteria", null);
      }
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.NEWTON;
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

  /**
   * Create a clean damped-substitution fallback candidate before an accelerator changes tray state.
   *
   * @param column column to copy
   * @return copied column, or {@code null} if the copy fails
   */
  private static DistillationColumn createDampedFallbackCandidate(DistillationColumn column) {
    try {
      return (DistillationColumn) column.copy();
    } catch (RuntimeException exception) {
      return null;
    } catch (StackOverflowError error) {
      return null;
    }
  }

  /**
   * Apply damped substitution from a clean candidate when an accelerator is rejected.
   *
   * @param column live column to update
   * @param fallbackCandidate clean fallback candidate, or {@code null} if unavailable
   * @param id calculation identifier
   * @param reason rejection reason
   * @param exception optional accelerator exception
   */
  private static void applyDampedFallback(DistillationColumn column,
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

  /**
   * Validate an accelerator result against a damped-substitution candidate.
   *
   * @param column accelerated result
   * @param fallbackCandidate clean damped candidate, or {@code null} if unavailable
   * @param id calculation identifier
   * @param solverName name of the accelerator being validated
   */
  private static void validateAcceleratedProductSplit(DistillationColumn column,
      DistillationColumn fallbackCandidate, UUID id, String solverName) {
    if (fallbackCandidate == null) {
      return;
    }
    try {
      fallbackCandidate.setDoInitializion(true);
      fallbackCandidate.solveDampedSubstitution(id);
    } catch (RuntimeException exception) {
      return;
    }
    if (productSplitDiffers(column, fallbackCandidate)) {
      column.acceptDampedFallbackCandidate(fallbackCandidate,
          solverName + " product split differs from damped substitution validation candidate");
    }
  }

  /**
   * Check whether two solved columns expose materially different product splits.
   *
   * @param accelerated accelerated result
   * @param reference reference damped-substitution result
   * @return {@code true} when gas or liquid product flows differ beyond tolerance
   */
  private static boolean productSplitDiffers(DistillationColumn accelerated,
      DistillationColumn reference) {
    double acceleratedGas = accelerated.getGasOutStream().getFlowRate("kg/hr");
    double referenceGas = reference.getGasOutStream().getFlowRate("kg/hr");
    double acceleratedLiquid = accelerated.getLiquidOutStream().getFlowRate("kg/hr");
    double referenceLiquid = reference.getLiquidOutStream().getFlowRate("kg/hr");
    return differsByMoreThanProductTolerance(acceleratedGas, referenceGas)
        || differsByMoreThanProductTolerance(acceleratedLiquid, referenceLiquid);
  }

  /**
   * Compare two product flow values with a relative tolerance.
   *
   * @param actual accelerated value
   * @param reference reference value
   * @return {@code true} when the values differ materially
   */
  private static boolean differsByMoreThanProductTolerance(double actual, double reference) {
    if (!Double.isFinite(actual) || !Double.isFinite(reference)) {
      return true;
    }
    double tolerance = Math.max(1.0e-8, Math.abs(reference) * 2.0e-2);
    return Math.abs(actual - reference) > tolerance;
  }
}
