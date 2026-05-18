package neqsim.process.equipment.distillation;

import java.util.UUID;
import neqsim.util.validation.ValidationResult;

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

  /**
   * Whether accelerated solvers (Wegstein, Sum-Rates, Naphtali-Sandholm, MESH residual) should run
   * a damped-substitution validation solve after a successful solve and roll back to the damped
   * result if product splits differ materially. Off by default because the validation solve is
   * typically as expensive as the accelerator itself, halving the wallclock speedup. The
   * accelerators already fall back internally when {@link DistillationColumn#solved()} is not
   * satisfied, so this extra layer is only useful for regression auditing.
   */
  private static volatile boolean verifyAcceleratedResults = false;

  /**
   * Thread-local marker used while AUTO probes candidate solvers. Candidate probes should report
   * their own state without spending another full damped-substitution run inside each rejected
   * candidate; AUTO applies the robust fallback once after ranking the probes.
   */
  private static final ThreadLocal<Boolean> autoCandidateProbeMode =
      new ThreadLocal<Boolean>() {
        /** {@inheritDoc} */
        @Override
        protected Boolean initialValue() {
          return Boolean.FALSE;
        }
      };

  /** Utility class constructor. */
  private ColumnSolverFactory() {}

  /**
   * Enable or disable the damped-substitution verification step run after successful accelerated
   * solves. Disabled by default.
   *
   * @param enabled {@code true} to verify every accelerated result against a damped fallback
   */
  static void setVerifyAcceleratedResults(boolean enabled) {
    verifyAcceleratedResults = enabled;
  }

  /**
   * Check whether accelerated result verification is currently enabled.
   *
   * @return {@code true} if every accelerated solve is verified against a damped solve
   */
  static boolean isVerifyAcceleratedResults() {
    return verifyAcceleratedResults;
  }

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
      StringBuilder summary = new StringBuilder();

      ValidationResult feasibility = column.screenSpecificationFeasibility();
      column.setLastAutoFeasibilityReport(feasibility.getReport());
      column.recordAutoSolverEvent("feasibility pre-screen valid=" + feasibility.isValid()
          + ", warnings=" + feasibility.hasWarnings());
      appendAutoFeasibilitySummary(summary, feasibility);

      DistillationColumn candidateSource = createAutoCandidate(column);
      if (candidateSource == null) {
        appendAutoCandidateSummary(summary, DistillationColumn.SolverType.DAMPED_SUBSTITUTION,
            null, "pipeline seed copy failed; live damped fallback used");
        return runAutoFallbackOnLiveColumn(column, id, summary);
      }
      boolean shortcutApplied = candidateSource.tryAutomaticShortcutInitialization(summary);
      if (shortcutApplied) {
        column.recordAutoSolverEvent("shortcut seed applied");
        column.setLastInitializationReport(candidateSource.getLastInitializationReport());
      } else if (candidateSource.tryThermodynamicProfileInitialization(summary)) {
        column.recordAutoSolverEvent("thermodynamic profile seed applied");
        column.setLastInitializationReport(candidateSource.getLastInitializationReport());
      } else {
        column.setLastInitializationReport(candidateSource.getLastInitializationReport());
      }
      ColumnSolveResult warmBaseResult = runAutoWarmBase(candidateSource, id, summary);
      column.recordAutoSolverEvent("relaxed base solve solved=" + warmBaseResult.isSolved()
          + ", used=" + warmBaseResult.getSolverType());
      double warmBaseScore = scoreResult(warmBaseResult, candidateSource);
      if (warmBaseScore < bestScore) {
        bestScore = warmBaseScore;
        bestCandidate = candidateSource;
        bestResult = warmBaseResult;
        bestSolver = warmBaseResult.getSolverType();
      }

      for (int index = 0; index < candidates.length; index++) {
        DistillationColumn.SolverType candidateSolver = candidates[index];
        DistillationColumn candidate = createAutoCandidate(candidateSource);
        if (candidate == null) {
          appendAutoCandidateSummary(summary, candidateSolver, null,
              "candidate copy failed; live damped fallback used");
          return runAutoFallbackOnLiveColumn(column, id, summary);
        }
        if (candidateSolver == DistillationColumn.SolverType.DAMPED_SUBSTITUTION) {
          appendAutoCandidateSummary(summary, candidateSolver, warmBaseResult,
              "relaxed base reused; duplicate damped probe skipped");
          continue;
        }
        prepareAutoCandidate(candidate, candidateSolver);
        try {
          ColumnSolveResult result = runAutoProbeCandidate(candidate, candidateSolver, id);
          appendAutoCandidateSummary(summary, candidateSolver, result,
              autoProbeNote(candidate, result));
          double score = scoreResult(result, candidate);
          if (score < bestScore) {
            bestScore = score;
            bestCandidate = candidate;
            bestResult = result;
            bestSolver = result.getSolverType();
          }
          if (isAcceptableAutoCandidate(candidate, result)) {
            column.acceptAutoSolverCandidate(candidate, result.getSolverType());
            column.setLastAutoSolverSummary(summary.toString());
            column.recordAutoSolverEvent("selected " + result.getSolverType());
            return ColumnSolveResult.from(column, result.getSolverType());
          }
        } catch (RuntimeException exception) {
          appendAutoCandidateSummary(summary, candidateSolver, null,
              "failed: " + exception.getMessage());
          DistillationColumn.logger.debug("AUTO distillation solver candidate {} failed for {}.",
              candidateSolver, column.getName(), exception);
        }
      }

      if (bestCandidate != null && bestResult != null && bestSolver != null
          && isAcceptableAutoCandidate(bestCandidate, bestResult)) {
        column.acceptAutoSolverCandidate(bestCandidate, bestSolver);
        column.setLastAutoSolverSummary(summary.toString());
        column.recordAutoSolverEvent("selected best available " + bestSolver);
        return ColumnSolveResult.from(column, bestResult.getSolverType());
      }
      return runAutoFallbackOnLiveColumn(column, id, summary);
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
      column.markSolverTypeUsed(getSolverType());
      column.solveDirectSubstitution(id);
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
      column.markSolverTypeUsed(getSolverType());
      column.solveDampedSubstitution(id);
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
      column.markSolverTypeUsed(getSolverType());
      boolean fallbackApplied = false;
      try {
        column.solveInsideOut(id);
      } catch (RuntimeException exception) {
        if (isAutoCandidateProbeMode()) {
          throw exception;
        }
        applyDampedFallback(column, null, id, "Inside-out failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, null, id,
            "Inside-out required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && !column.solved()) {
        applyDampedFallback(column, null, id, "Inside-out did not satisfy convergence criteria",
            null);
      }
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
      column.markSolverTypeUsed(getSolverType());
      boolean fallbackApplied = false;
      try {
        column.solveMatrixInsideOut(id);
      } catch (RuntimeException exception) {
        if (isAutoCandidateProbeMode()) {
          throw exception;
        }
        applyDampedFallback(column, null, id, "Matrix inside-out failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, null, id,
            "Matrix inside-out required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && !column.solved()) {
        applyDampedFallback(column, null, id,
            "Matrix inside-out did not satisfy convergence criteria", null);
      }
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
      column.markSolverTypeUsed(getSolverType());
      DistillationColumn fallbackCandidate =
          shouldPrepareAcceleratedFallback() ? createDampedFallbackCandidate(column) : null;
      boolean fallbackApplied = false;
      try {
        column.solveWegstein(id);
      } catch (RuntimeException exception) {
        if (isAutoCandidateProbeMode()) {
          throw exception;
        }
        applyDampedFallback(column, fallbackCandidate, id, "Wegstein failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Wegstein required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Wegstein did not satisfy convergence criteria", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && verifyAcceleratedResults) {
        validateAcceleratedProductSplit(column, fallbackCandidate, id, "Wegstein");
      }
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
      column.markSolverTypeUsed(getSolverType());
      DistillationColumn fallbackCandidate =
          shouldPrepareAcceleratedFallback() ? createDampedFallbackCandidate(column) : null;
      boolean fallbackApplied = false;
      try {
        column.solveSumRates(id);
      } catch (RuntimeException exception) {
        if (isAutoCandidateProbeMode()) {
          throw exception;
        }
        applyDampedFallback(column, fallbackCandidate, id, "Sum-rates failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Sum-rates required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Sum-rates did not satisfy convergence criteria", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && verifyAcceleratedResults) {
        validateAcceleratedProductSplit(column, fallbackCandidate, id, "Sum-rates");
      }
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
      column.markSolverTypeUsed(getSolverType());
      boolean fallbackApplied = false;
      try {
        column.solveNewton(id);
      } catch (RuntimeException exception) {
        if (isAutoCandidateProbeMode()) {
          throw exception;
        }
        applyDampedFallback(column, null, id, "Newton failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, null, id, "Newton required guarded feed-flash product fallback",
            null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && !column.solved()) {
        applyDampedFallback(column, null, id, "Newton did not satisfy convergence criteria", null);
      }
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
      column.markSolverTypeUsed(getSolverType());
      DistillationColumn fallbackCandidate =
          shouldPrepareAcceleratedFallback() ? createDampedFallbackCandidate(column) : null;
      boolean fallbackApplied = false;
      try {
        column.solveNaphtaliSandholm(id);
      } catch (RuntimeException exception) {
        if (isAutoCandidateProbeMode()) {
          throw exception;
        }
        applyDampedFallback(column, fallbackCandidate, id, "Naphtali-Sandholm failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Naphtali-Sandholm required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "Naphtali-Sandholm did not satisfy convergence criteria", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && verifyAcceleratedResults) {
        validateAcceleratedProductSplit(column, fallbackCandidate, id, "Naphtali-Sandholm");
      }
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
      column.markSolverTypeUsed(getSolverType());
      DistillationColumn fallbackCandidate =
          shouldPrepareAcceleratedFallback() ? createDampedFallbackCandidate(column) : null;
      boolean fallbackApplied = false;
      try {
        column.solveMeshResidual(id);
      } catch (RuntimeException exception) {
        if (isAutoCandidateProbeMode()) {
          throw exception;
        }
        applyDampedFallback(column, fallbackCandidate, id, "MESH residual solve failed", exception);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && column.wasFeedFlashFallbackApplied()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "MESH residual solve required guarded feed-flash product fallback", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && !isAutoCandidateProbeMode() && !column.solved()) {
        applyDampedFallback(column, fallbackCandidate, id,
            "MESH residual solve did not satisfy convergence criteria", null);
        fallbackApplied = true;
      }
      if (!fallbackApplied && verifyAcceleratedResults) {
        validateAcceleratedProductSplit(column, fallbackCandidate, id, "MESH residual");
      }
      return ColumnSolveResult.from(column, column.getLastSolverTypeUsed());
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
   * Create a candidate copy for the automatic selector.
   *
   * @param column source column
   * @return copied column, or {@code null} if copying is unavailable
   */
  private static DistillationColumn createAutoCandidate(DistillationColumn column) {
    return createDampedFallbackCandidate(column);
  }

  /**
   * Prepare an automatic-solver candidate with robust defaults.
   *
   * @param candidate candidate column copy to prepare
   * @param candidateSolver solver to apply to the candidate
   */
  private static void prepareAutoCandidate(DistillationColumn candidate,
      DistillationColumn.SolverType candidateSolver) {
    candidate.setSolverType(candidateSolver);
    candidate.setDoInitializion(true);
  }

  /**
   * Select candidate solvers for automatic mode.
   *
   * @param column column being solved
   * @return ordered candidate solver types, excluding {@link DistillationColumn.SolverType#AUTO}
   */
  private static DistillationColumn.SolverType[] selectCandidateSolvers(DistillationColumn column) {
    if (column.isReactive()) {
      return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.NAPHTALI_SANDHOLM,
          DistillationColumn.SolverType.MESH_RESIDUAL,
          DistillationColumn.SolverType.DAMPED_SUBSTITUTION};
    }
    if (hasAdjustableProductSpecification(column)) {
      if (column.numberOfTrays >= 12) {
        return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.MATRIX_INSIDE_OUT,
            DistillationColumn.SolverType.NAPHTALI_SANDHOLM,
            DistillationColumn.SolverType.MESH_RESIDUAL,
            DistillationColumn.SolverType.DAMPED_SUBSTITUTION};
      }
      return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.INSIDE_OUT,
          DistillationColumn.SolverType.NAPHTALI_SANDHOLM,
          DistillationColumn.SolverType.MESH_RESIDUAL,
          DistillationColumn.SolverType.DAMPED_SUBSTITUTION};
    }
    if (column.hasCondenser && column.hasReboiler) {
      if (column.numberOfTrays >= 12) {
        return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.MATRIX_INSIDE_OUT,
            DistillationColumn.SolverType.INSIDE_OUT,
            DistillationColumn.SolverType.MESH_RESIDUAL,
            DistillationColumn.SolverType.DAMPED_SUBSTITUTION};
      }
      if (column.numberOfTrays >= 6) {
        return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.INSIDE_OUT,
            DistillationColumn.SolverType.MESH_RESIDUAL,
            DistillationColumn.SolverType.DAMPED_SUBSTITUTION};
      }
    }
    if (!column.hasCondenser || !column.hasReboiler) {
      return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.SUM_RATES,
          DistillationColumn.SolverType.DAMPED_SUBSTITUTION,
          DistillationColumn.SolverType.DIRECT_SUBSTITUTION};
    }
    return new DistillationColumn.SolverType[] {DistillationColumn.SolverType.DAMPED_SUBSTITUTION,
        DistillationColumn.SolverType.DIRECT_SUBSTITUTION};
  }

  /**
   * Check whether the column has a product specification that benefits from continuation.
   *
   * @param column column to inspect
   * @return {@code true} when a purity, recovery, or product-flow specification is active
   */
  private static boolean hasAdjustableProductSpecification(DistillationColumn column) {
    return isAdjustableProductSpecification(column.getTopSpecification())
        || isAdjustableProductSpecification(column.getBottomSpecification());
  }

  /**
   * Check whether one specification is adjusted through the outer temperature loop.
   *
   * @param specification specification to inspect
   * @return {@code true} for purity, recovery, or product-flow specifications
   */
  private static boolean isAdjustableProductSpecification(ColumnSpecification specification) {
    if (specification == null) {
      return false;
    }
    return specification.getType() != ColumnSpecification.SpecificationType.REFLUX_RATIO
        && specification.getType() != ColumnSpecification.SpecificationType.DUTY;
  }

  /**
   * Run the relaxed base stage used by automatic solver mode.
   *
   * @param candidateSource candidate source column to warm start
   * @param id calculation identifier
   * @param summary automatic solver summary builder
   * @return solve result from the relaxed base stage
   */
  private static ColumnSolveResult runAutoWarmBase(DistillationColumn candidateSource, UUID id,
      StringBuilder summary) {
    prepareAutoCandidate(candidateSource, DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    try {
      ColumnSolveResult result = DAMPED.solve(candidateSource, id);
      appendAutoCandidateSummary(summary, DistillationColumn.SolverType.DAMPED_SUBSTITUTION,
          result, "relaxed base solve");
      return result;
    } catch (RuntimeException exception) {
      appendAutoCandidateSummary(summary, DistillationColumn.SolverType.DAMPED_SUBSTITUTION, null,
          "relaxed base solve failed: " + exception.getMessage());
      DistillationColumn.logger.debug("AUTO relaxed base solve failed for {}.",
          candidateSource.getName(), exception);
      return ColumnSolveResult.from(candidateSource,
          DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    }
  }

  /**
   * Run one AUTO candidate probe without allowing the candidate wrapper to perform its own damped
   * fallback solve.
   *
   * @param candidate candidate column copy
   * @param candidateSolver solver to probe
   * @param id calculation identifier
   * @return result reported by the probed solver before AUTO-level fallback
   */
  private static ColumnSolveResult runAutoProbeCandidate(DistillationColumn candidate,
      DistillationColumn.SolverType candidateSolver, UUID id) {
    Boolean previousMode = autoCandidateProbeMode.get();
    autoCandidateProbeMode.set(Boolean.TRUE);
    try {
      return ColumnSolverFactory.create(candidateSolver).solve(candidate, id);
    } finally {
      autoCandidateProbeMode.set(previousMode);
    }
  }

  /**
   * Check whether the current strategy call is an AUTO probe.
   *
   * @return {@code true} when damped fallback work should be deferred to AUTO
   */
  private static boolean isAutoCandidateProbeMode() {
    return autoCandidateProbeMode.get().booleanValue();
  }

  /**
   * Check whether an accelerator should prepare a damped validation/fallback copy.
   *
   * @return {@code true} when accelerated-result verification is enabled outside AUTO probing
   */
  private static boolean shouldPrepareAcceleratedFallback() {
    return verifyAcceleratedResults && !isAutoCandidateProbeMode();
  }

  /**
   * Build an explanatory note for an AUTO candidate probe.
   *
   * @param candidate candidate column after the probe
   * @param result probe result
   * @return note for the AUTO summary, or {@code null} when no note is needed
   */
  private static String autoProbeNote(DistillationColumn candidate, ColumnSolveResult result) {
    if (candidate.wasFeedFlashFallbackApplied()) {
      return "damped fallback deferred; candidate used guarded feed-flash products";
    }
    if (!result.isSolved()) {
      return "damped fallback deferred; candidate did not satisfy convergence criteria";
    }
    return null;
  }

  /**
   * Check whether an AUTO candidate can be accepted without running the live fallback.
   *
   * @param candidate candidate column after the probe
   * @param result probe result
   * @return {@code true} when the candidate is solved and did not rely on fallback products
   */
  private static boolean isAcceptableAutoCandidate(DistillationColumn candidate,
      ColumnSolveResult result) {
    return result != null && result.isSolved() && !candidate.wasFeedFlashFallbackApplied();
  }

  /**
   * Append feasibility pre-screen status to the automatic solver trace.
   *
   * @param summary summary builder receiving a one-line status
   * @param feasibility feasibility validation result
   */
  private static void appendAutoFeasibilitySummary(StringBuilder summary,
      ValidationResult feasibility) {
    if (summary.length() > 0) {
      summary.append("\n");
    }
    summary.append("    - FEASIBILITY_SCREEN: valid=").append(feasibility.isValid())
        .append(", warnings=").append(feasibility.hasWarnings()).append(", issues=")
        .append(feasibility.getIssues().size());
  }

  /**
   * Run the robust fallback directly on the live column when trial copies are unavailable.
   *
   * @param column live column
   * @param id calculation identifier
   * @return solve result from the fallback solver
   */
  private static ColumnSolveResult runAutoFallbackOnLiveColumn(DistillationColumn column, UUID id,
      StringBuilder summary) {
    ColumnSolveResult result = DAMPED.solve(column, id);
    appendAutoCandidateSummary(summary, DistillationColumn.SolverType.DAMPED_SUBSTITUTION, result,
        "live fallback");
    column.setLastAutoSolverSummary(summary.toString());
    column.recordAutoSolverEvent("selected live damped fallback");
    return ColumnSolveResult.from(column, result.getSolverType());
  }

  /**
   * Append one candidate attempt to the automatic solver trace.
   *
   * @param summary summary builder to append to
   * @param candidateSolver solver requested for the candidate
   * @param result candidate result, or {@code null} when no result is available
   * @param note optional explanatory note
   */
  private static void appendAutoCandidateSummary(StringBuilder summary,
      DistillationColumn.SolverType candidateSolver, ColumnSolveResult result, String note) {
    if (summary.length() > 0) {
      summary.append("\n");
    }
    summary.append("    - ").append(candidateSolver).append(": ");
    if (result == null) {
      summary.append(note == null ? "no result" : note);
      return;
    }
    summary.append("solved=").append(result.isSolved()).append(", used=")
        .append(result.getSolverType()).append(", iterations=").append(result.getIterationCount())
        .append(", time=").append(result.getSolveTimeSeconds()).append(" s, tempResidual=")
        .append(result.getTemperatureResidual()).append(", massResidual=")
        .append(result.getMassResidual()).append(", meshResidual=")
        .append(result.getMeshResidualNorm());
    if (note != null && !note.trim().isEmpty()) {
      summary.append(", note=").append(note);
    }
  }

  /**
   * Score a candidate result for best-effort automatic fallback.
   *
   * @param result candidate result
   * @param candidate candidate column, or {@code null} if only the result is available
   * @return finite scalar score where lower is better
   */
  private static double scoreResult(ColumnSolveResult result, DistillationColumn candidate) {
    double score = residualScore(result.getTemperatureResidual())
        + residualScore(result.getMassResidual()) + residualScore(result.getEnergyResidual())
        + residualScore(result.getProductDrawResidualNorm());
    if (Double.isFinite(result.getMeshResidualNorm())) {
      score += residualScore(result.getMeshResidualNorm());
    }
    if (!result.isSolved()) {
      score += 1.0e6;
    }
    if (candidate != null && candidate.wasFeedFlashFallbackApplied()) {
      score += 5.0e5;
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
        fallbackCandidate.markSolverTypeUsed(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
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
      fallbackCandidate.markSolverTypeUsed(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
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
