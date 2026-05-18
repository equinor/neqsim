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
  /** Matrix inside-out strategy. */
  private static final ColumnSolver MATRIX_INSIDE_OUT = new MatrixInsideOutSolver();
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
  /** Automatic strategy selector. */
  private static final ColumnSolver AUTO = new AutoSolver();

  /** Utility class constructor. */
  private ColumnSolverFactory() {}

  /**
   * Create the strategy for a solver type.
   *
   * @param solverType requested solver type
   * @return solver strategy for the requested type
   */
  static ColumnSolver create(DistillationColumn.SolverType solverType) {
    if (solverType == null) {
      return DIRECT;
    }
    switch (solverType) {
      case AUTO:
        return AUTO;
      case DAMPED_SUBSTITUTION:
        return DAMPED;
      case INSIDE_OUT:
        return INSIDE_OUT;
      case MATRIX_INSIDE_OUT:
        return MATRIX_INSIDE_OUT;
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

  /** Automatic solver selector. */
  private static final class AutoSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      DistillationColumn.SolverType[] candidates = selectCandidateSolvers(column);
      DistillationColumn bestCandidate = null;
      ColumnSolveResult bestResult = null;
      DistillationColumn.SolverType bestSolver = null;
      double bestScore = Double.POSITIVE_INFINITY;

      for (int index = 0; index < candidates.length; index++) {
        DistillationColumn.SolverType candidateSolver = candidates[index];
        DistillationColumn candidate = createAutoCandidate(column);
        if (candidate == null) {
          return runAutoFallbackOnLiveColumn(column, id);
        }
        candidate.setSolverType(candidateSolver);
        candidate.setDoInitializion(true);
        try {
          ColumnSolveResult result =
              ColumnSolverFactory.create(candidateSolver).solve(candidate, id);
          double score = scoreResult(result);
          if (score < bestScore) {
            bestScore = score;
            bestCandidate = candidate;
            bestResult = result;
            bestSolver = candidateSolver;
          }
          if (result.isSolved()) {
            column.acceptAutoSolverCandidate(candidate, candidateSolver);
            return ColumnSolveResult.from(column, candidateSolver);
          }
        } catch (RuntimeException exception) {
          DistillationColumn.logger.debug("AUTO distillation solver candidate {} failed for {}.",
              candidateSolver, column.getName(), exception);
        }
      }

      if (bestCandidate != null && bestResult != null && bestSolver != null
          && bestResult.isSolved()) {
        column.acceptAutoSolverCandidate(bestCandidate, bestSolver);
        return ColumnSolveResult.from(column, bestResult.getSolverType());
      }
      return runAutoFallbackOnLiveColumn(column, id);
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.AUTO;
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

  /** Matrix inside-out adapter. */
  private static final class MatrixInsideOutSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      boolean fallbackApplied = false;
      try {
        column.solveMatrixInsideOut(id);
      } catch (RuntimeException exception) {
        applyDampedFallback(column, null, id, "Matrix inside-out failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, null, id,
            "Matrix inside-out required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !column.solved()) {
        applyDampedFallback(column, null, id,
            "Matrix inside-out did not satisfy convergence criteria", null);
      }
      return ColumnSolveResult.from(column, getSolverType());
    }

    /** {@inheritDoc} */
    @Override
    public DistillationColumn.SolverType getSolverType() {
      return DistillationColumn.SolverType.MATRIX_INSIDE_OUT;
    }
  }

  /** Wegstein adapter. */
  private static final class WegsteinSolver implements ColumnSolver {
    /** {@inheritDoc} */
    @Override
    public ColumnSolveResult solve(DistillationColumn column, UUID id) {
      DistillationColumn fallbackCandidate = createValidationFallbackCandidate(column);
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
      DistillationColumn fallbackCandidate = createValidationFallbackCandidate(column);
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
      DistillationColumn fallbackCandidate = createValidationFallbackCandidate(column);
      boolean fallbackApplied = false;
      try {
        column.solveNaphtaliSandholm(id);
      } catch (RuntimeException exception) {
        applyDampedFallback(column, fallbackCandidate, id, "Naphtali-Sandholm failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Naphtali-Sandholm required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Naphtali-Sandholm did not satisfy convergence criteria", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied) {
        validateAcceleratedProductSplit(column, fallbackCandidate, id, "Naphtali-Sandholm");
      }
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
      DistillationColumn fallbackCandidate = createValidationFallbackCandidate(column);
      boolean fallbackApplied = false;
      try {
        column.solveMeshResidual(id);
      } catch (RuntimeException exception) {
        applyDampedFallback(column, fallbackCandidate, id, "MESH residual solve failed",
            exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "MESH residual solve required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "MESH residual solve did not satisfy convergence criteria", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied) {
        validateAcceleratedProductSplit(column, fallbackCandidate, id, "MESH residual");
      }
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
   * Create a damped-substitution validation candidate unless fast mode has disabled that check.
   *
   * @param column column being solved
   * @return copied validation candidate, or {@code null} when validation is skipped or copy fails
   */
  private static DistillationColumn createValidationFallbackCandidate(DistillationColumn column) {
    if (column.isFastSolverMode()) {
      return null;
    }
    return createDampedFallbackCandidate(column);
  }

  /**
   * Create a candidate copy for the automatic selector.
   *
   * @param column source column
   * @return copied column, or {@code null} if copying is unavailable
   */
  private static DistillationColumn createAutoCandidate(DistillationColumn column) {
    return createDampedFallbackCandidate(column);
  }

  /**
   * Select candidate solvers for automatic mode.
   *
   * @param column column being solved
   * @return ordered candidate solver types, excluding {@link DistillationColumn.SolverType#AUTO}
   */
  private static DistillationColumn.SolverType[] selectCandidateSolvers(
      DistillationColumn column) {
    if (column.isReactive()) {
      return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.NAPHTALI_SANDHOLM,
          DistillationColumn.SolverType.MESH_RESIDUAL,
          DistillationColumn.SolverType.DAMPED_SUBSTITUTION};
    }
    if (column.hasCondenser && column.hasReboiler) {
      if (column.numberOfTrays >= 12) {
        return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.MATRIX_INSIDE_OUT,
            DistillationColumn.SolverType.MESH_RESIDUAL,
            DistillationColumn.SolverType.DAMPED_SUBSTITUTION};
      }
      if (column.numberOfTrays >= 6) {
        return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.MESH_RESIDUAL,
            DistillationColumn.SolverType.DAMPED_SUBSTITUTION};
      }
    }
    return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.DAMPED_SUBSTITUTION,
        DistillationColumn.SolverType.DIRECT_SUBSTITUTION};
  }

  /**
   * Run the robust fallback directly on the live column when trial copies are unavailable.
   *
   * @param column live column
   * @param id calculation identifier
   * @return solve result from the fallback solver
   */
  private static ColumnSolveResult runAutoFallbackOnLiveColumn(DistillationColumn column, UUID id) {
    ColumnSolveResult result = DAMPED.solve(column, id);
    return ColumnSolveResult.from(column, result.getSolverType());
  }

  /**
   * Score a candidate result for best-effort automatic fallback.
   *
   * @param result candidate result
   * @return finite scalar score where lower is better
   */
  private static double scoreResult(ColumnSolveResult result) {
    double score = residualScore(result.getTemperatureResidual())
        + residualScore(result.getMassResidual()) + residualScore(result.getEnergyResidual())
        + residualScore(result.getProductDrawResidualNorm());
    if (Double.isFinite(result.getMeshResidualNorm())) {
      score += residualScore(result.getMeshResidualNorm());
    }
    if (!result.isSolved()) {
      score += 1.0e6;
    }
    return score;
  }

  /**
   * Convert a residual to a finite scoring contribution.
   *
   * @param residual candidate residual value
   * @return finite non-negative residual score
   */
  private static double residualScore(double residual) {
    if (!Double.isFinite(residual)) {
      return 1.0e9;
    }
    return Math.abs(residual);
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
