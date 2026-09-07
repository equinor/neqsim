package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Immutable diagnostics from a {@link TwoFluidPipe} steady-state initialization.
 *
 * <p>
 * Every residual is dimensionless and is compared with {@link #getTolerance()}. Holdup and liquid-split residuals are
 * absolute volume-fraction changes. Pressure-update, thermodynamic-property, pressure-drop, and pressure-momentum
 * residuals are relative changes. A report is converged only when every applicable residual is below the tolerance and
 * the mandatory final thermodynamic/holdup consistency pass has also settled.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class SteadyStateConvergenceReport implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Reason the steady-state refinement loop stopped. */
  public enum TerminationReason {
    /** No steady-state solve has run. */
    NOT_RUN,
    /** Every applicable residual met the configured tolerance. */
    CONVERGED,
    /** The configured iteration budget was exhausted. */
    ITERATION_LIMIT,
    /** The wall-clock guard stopped the solve. */
    WALL_CLOCK_LIMIT,
    /** At least one section reached the numerical pressure floor. */
    PRESSURE_FLOOR_LIMIT
  }

  private final TerminationReason terminationReason;
  private final int iterations;
  private final double tolerance;
  private final double pressureMomentumResidual;
  private final double pressureUpdateResidual;
  private final double liquidHoldupResidual;
  private final double liquidSplitResidual;
  private final double thermodynamicResidual;
  private final double pressureDropResidual;

  /**
   * Create a steady-state convergence report.
   *
   * @param terminationReason reason the solver stopped
   * @param iterations number of refinement sweeps performed
   * @param tolerance dimensionless convergence tolerance
   * @param pressureMomentumResidual accumulated pressure-march residual normalized by pressure drop
   * @param pressureUpdateResidual maximum relative pressure correction
   * @param liquidHoldupResidual maximum absolute total-liquid-holdup correction
   * @param liquidSplitResidual maximum absolute water-holdup correction
   * @param thermodynamicResidual maximum relative thermodynamic-property correction
   * @param pressureDropResidual relative total-pressure-drop correction between sweeps
   */
  public SteadyStateConvergenceReport(TerminationReason terminationReason, int iterations, double tolerance,
      double pressureMomentumResidual, double pressureUpdateResidual, double liquidHoldupResidual,
      double liquidSplitResidual, double thermodynamicResidual, double pressureDropResidual) {
    this.terminationReason = terminationReason;
    this.iterations = iterations;
    this.tolerance = tolerance;
    this.pressureMomentumResidual = pressureMomentumResidual;
    this.pressureUpdateResidual = pressureUpdateResidual;
    this.liquidHoldupResidual = liquidHoldupResidual;
    this.liquidSplitResidual = liquidSplitResidual;
    this.thermodynamicResidual = thermodynamicResidual;
    this.pressureDropResidual = pressureDropResidual;
  }

  /** @return reason the steady-state refinement stopped */
  public TerminationReason getTerminationReason() {
    return terminationReason;
  }

  /** @return number of refinement sweeps performed */
  public int getIterations() {
    return iterations;
  }

  /** @return dimensionless convergence tolerance */
  public double getTolerance() {
    return tolerance;
  }

  /** @return accumulated pressure-march residual normalized by total pressure drop */
  public double getPressureMomentumResidual() {
    return pressureMomentumResidual;
  }

  /** @return maximum relative pressure correction */
  public double getPressureUpdateResidual() {
    return pressureUpdateResidual;
  }

  /** @return maximum absolute total-liquid-holdup correction */
  public double getLiquidHoldupResidual() {
    return liquidHoldupResidual;
  }

  /** @return maximum absolute water-holdup correction */
  public double getLiquidSplitResidual() {
    return liquidSplitResidual;
  }

  /** @return maximum relative thermodynamic-property correction */
  public double getThermodynamicResidual() {
    return thermodynamicResidual;
  }

  /** @return relative total-pressure-drop correction between refinement sweeps */
  public double getPressureDropResidual() {
    return pressureDropResidual;
  }

  /** @return true only when the solver stopped because every residual met the tolerance */
  public boolean isConverged() {
    return terminationReason == TerminationReason.CONVERGED;
  }
}
