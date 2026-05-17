package neqsim.process.equipment.distillation;

/**
 * Immutable summary of a distillation column solve.
 *
 * <p>
 * The result captures convergence metrics immediately after a solver strategy completes. It is an
 * internal boundary object used by {@link ColumnSolver}; public callers should continue to use the
 * existing metric getters on {@link DistillationColumn}.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
final class ColumnSolveResult {
  /** Solver used for the run. */
  private final DistillationColumn.SolverType solverType;
  /** Whether the column satisfies its convergence contract. */
  private final boolean solved;
  /** Number of outer iterations run by the solver. */
  private final int iterationCount;
  /** Latest temperature residual in Kelvin. */
  private final double temperatureResidual;
  /** Latest relative mass residual. */
  private final double massResidual;
  /** Latest relative energy residual. */
  private final double energyResidual;
  /** Solver wall time in seconds. */
  private final double solveTimeSeconds;

  /**
   * Create a solve result.
   *
   * @param solverType solver used for the run
   * @param solved whether the column satisfies its convergence contract
   * @param iterationCount number of iterations used
   * @param temperatureResidual latest temperature residual in Kelvin
   * @param massResidual latest relative mass residual
   * @param energyResidual latest relative energy residual
   * @param solveTimeSeconds solver wall time in seconds
   */
  private ColumnSolveResult(DistillationColumn.SolverType solverType, boolean solved,
      int iterationCount, double temperatureResidual, double massResidual, double energyResidual,
      double solveTimeSeconds) {
    this.solverType = solverType;
    this.solved = solved;
    this.iterationCount = iterationCount;
    this.temperatureResidual = temperatureResidual;
    this.massResidual = massResidual;
    this.energyResidual = energyResidual;
    this.solveTimeSeconds = solveTimeSeconds;
  }

  /**
   * Build a result from the current column diagnostics.
   *
   * @param column solved column to summarize
   * @param solverType solver used for the run
   * @return immutable solve result
   */
  static ColumnSolveResult from(DistillationColumn column,
      DistillationColumn.SolverType solverType) {
    return new ColumnSolveResult(solverType, column.solved(), column.getLastIterationCount(),
        column.getLastTemperatureResidual(), column.getLastMassResidual(),
        column.getLastEnergyResidual(), column.getLastSolveTimeSeconds());
  }

  /**
   * Get the solver type used for this result.
   *
   * @return solver type
   */
  DistillationColumn.SolverType getSolverType() {
    return solverType;
  }

  /**
   * Check whether the column satisfied its convergence contract.
   *
   * @return {@code true} if the solve converged
   */
  boolean isSolved() {
    return solved;
  }

  /**
   * Get the iteration count.
   *
   * @return number of solver iterations
   */
  int getIterationCount() {
    return iterationCount;
  }

  /**
   * Get the latest temperature residual.
   *
   * @return residual in Kelvin
   */
  double getTemperatureResidual() {
    return temperatureResidual;
  }

  /**
   * Get the latest relative mass residual.
   *
   * @return mass residual
   */
  double getMassResidual() {
    return massResidual;
  }

  /**
   * Get the latest relative energy residual.
   *
   * @return energy residual
   */
  double getEnergyResidual() {
    return energyResidual;
  }

  /**
   * Get the solver wall time.
   *
   * @return solve time in seconds
   */
  double getSolveTimeSeconds() {
    return solveTimeSeconds;
  }
}
