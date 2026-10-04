package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Immutable diagnostics from a {@link TwoFluidPipe} steady-state initialization.
 *
 * <p>
 * Every residual is dimensionless. The mass-flux residual is compared with {@link #getMassFluxTolerance()}, and the
 * other residuals with {@link #getTolerance()}. Holdup and liquid-split residuals are absolute volume-fraction changes.
 * Pressure-update, thermodynamic-property, pressure-drop, and pressure-momentum residuals are relative changes. A
 * report is converged only when every applicable residual is below the tolerance and the mandatory final
 * thermodynamic/holdup consistency pass has also settled.
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
  private final double massFluxResidual;
  private final double massFluxTolerance;

  /**
   * Create a legacy steady-state convergence report without a mass-flux measurement. The mass-flux getters return
   * {@code NaN}; the supplied termination reason retains its original meaning.
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
    this(terminationReason, iterations, tolerance, pressureMomentumResidual, pressureUpdateResidual,
        liquidHoldupResidual, liquidSplitResidual, thermodynamicResidual, pressureDropResidual, Double.NaN, Double.NaN);
  }

  /**
   * Create a steady-state convergence report including source-free total mass transport.
   *
   * @param terminationReason reason the solver stopped
   * @param iterations number of refinement sweeps performed
   * @param tolerance dimensionless tolerance for the hydraulic and thermodynamic residuals
   * @param pressureMomentumResidual accumulated pressure-march residual normalized by pressure drop
   * @param pressureUpdateResidual maximum relative pressure correction
   * @param liquidHoldupResidual maximum absolute total-liquid-holdup correction
   * @param liquidSplitResidual maximum absolute water-holdup correction
   * @param thermodynamicResidual maximum relative thermodynamic-property correction
   * @param pressureDropResidual relative total-pressure-drop correction between sweeps
   * @param massFluxResidual maximum section total mass-flux error normalized by absolute inlet mass flow
   * @param massFluxTolerance dimensionless tolerance for the total mass-flux residual
   */
  public SteadyStateConvergenceReport(TerminationReason terminationReason, int iterations, double tolerance,
      double pressureMomentumResidual, double pressureUpdateResidual, double liquidHoldupResidual,
      double liquidSplitResidual, double thermodynamicResidual, double pressureDropResidual, double massFluxResidual,
      double massFluxTolerance) {
    this.terminationReason = terminationReason;
    this.iterations = iterations;
    this.tolerance = tolerance;
    this.pressureMomentumResidual = pressureMomentumResidual;
    this.pressureUpdateResidual = pressureUpdateResidual;
    this.liquidHoldupResidual = liquidHoldupResidual;
    this.liquidSplitResidual = liquidSplitResidual;
    this.thermodynamicResidual = thermodynamicResidual;
    this.pressureDropResidual = pressureDropResidual;
    this.massFluxResidual = massFluxResidual;
    this.massFluxTolerance = massFluxTolerance;
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

  /**
   * @return maximum relative total phase mass-flux error over all sections, or {@code NaN} for a legacy report without
   * this measurement; the inlet normalization has a 1e-12 kg/s floor
   */
  public double getMassFluxResidual() {
    return massFluxTolerance > 0.0 ? massFluxResidual : Double.NaN;
  }

  /** @return dimensionless mass-flux tolerance, or {@code NaN} for a legacy report without this check */
  public double getMassFluxTolerance() {
    return massFluxTolerance > 0.0 ? massFluxTolerance : Double.NaN;
  }

  /** @return true only when the solver converged and any recorded mass-flux check passed */
  public boolean isConverged() {
    return terminationReason == TerminationReason.CONVERGED
        && (!(massFluxTolerance > 0.0) || (Double.isFinite(massFluxResidual) && massFluxResidual < massFluxTolerance));
  }
}
