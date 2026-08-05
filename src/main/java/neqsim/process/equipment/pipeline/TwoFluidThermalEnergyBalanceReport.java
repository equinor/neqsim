package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Discrete sensible-energy balance for one accepted {@link TwoFluidPipe} transient call.
 *
 * <p>
 * The report covers the post-step thermal model: fluid sensible energy, simple-wall or radial-layer thermal energy,
 * conservative-face sensible advection, the optional Joule-Thomson source, and ambient heat loss. Its signed residual
 * is
 * </p>
 *
 * <pre>
 * residual = deltaFluid + deltaWall - advection - jouleThomson + ambientLoss
 * </pre>
 *
 * <p>
 * Positive advection and Joule-Thomson terms add energy to the domain; positive ambient loss removes energy. This
 * report is intended for closed-domain validation of the sensible-energy closure represented by the current temperature
 * model. For open-boundary transients, the stored-energy terms do not include energy changes caused solely by net mass
 * inventory changes, so the residual is not a complete control-volume energy balance. The report is also not a full
 * compositional enthalpy audit across flash-driven phase transfer.
 * </p>
 */
public final class TwoFluidThermalEnergyBalanceReport implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double RELATIVE_SCALE_FLOOR_J = 1.0e-12;

  private final double elapsedTimeSeconds;
  private final int acceptedSubsteps;
  private final double fluidEnergyChangeJ;
  private final double wallEnergyChangeJ;
  private final double sensibleAdvectionEnergyJ;
  private final double jouleThomsonEnergyJ;
  private final double ambientHeatLossJ;

  /**
   * Create a report from time-integrated thermal-model terms.
   *
   * @param elapsedTimeSeconds accepted elapsed time in seconds
   * @param acceptedSubsteps number of accepted internal time steps
   * @param fluidEnergyChangeJ fluid sensible-energy change in joules
   * @param wallEnergyChangeJ simple-wall or radial-layer energy change in joules
   * @param sensibleAdvectionEnergyJ net sensible energy added by conservative face advection in joules
   * @param jouleThomsonEnergyJ net Joule-Thomson energy added in joules
   * @param ambientHeatLossJ energy transferred from the wall or outer layer to ambient in joules
   */
  TwoFluidThermalEnergyBalanceReport(double elapsedTimeSeconds, int acceptedSubsteps, double fluidEnergyChangeJ,
      double wallEnergyChangeJ, double sensibleAdvectionEnergyJ, double jouleThomsonEnergyJ, double ambientHeatLossJ) {
    if (!Double.isFinite(elapsedTimeSeconds) || elapsedTimeSeconds < 0.0) {
      throw new IllegalArgumentException("elapsedTimeSeconds must be finite and non-negative");
    }
    if (acceptedSubsteps < 0) {
      throw new IllegalArgumentException("acceptedSubsteps must be non-negative");
    }
    requireFinite(fluidEnergyChangeJ, "fluidEnergyChangeJ");
    requireFinite(wallEnergyChangeJ, "wallEnergyChangeJ");
    requireFinite(sensibleAdvectionEnergyJ, "sensibleAdvectionEnergyJ");
    requireFinite(jouleThomsonEnergyJ, "jouleThomsonEnergyJ");
    requireFinite(ambientHeatLossJ, "ambientHeatLossJ");
    this.elapsedTimeSeconds = elapsedTimeSeconds;
    this.acceptedSubsteps = acceptedSubsteps;
    this.fluidEnergyChangeJ = fluidEnergyChangeJ;
    this.wallEnergyChangeJ = wallEnergyChangeJ;
    this.sensibleAdvectionEnergyJ = sensibleAdvectionEnergyJ;
    this.jouleThomsonEnergyJ = jouleThomsonEnergyJ;
    this.ambientHeatLossJ = ambientHeatLossJ;
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /**
   * Get accepted elapsed time.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedTimeSeconds() {
    return elapsedTimeSeconds;
  }

  /**
   * Get number of accepted internal time steps.
   *
   * @return accepted substep count
   */
  public int getAcceptedSubsteps() {
    return acceptedSubsteps;
  }

  /**
   * Get the time-integrated fluid sensible-energy change.
   *
   * @return final-minus-initial fluid sensible energy in joules
   */
  public double getFluidEnergyChangeJ() {
    return fluidEnergyChangeJ;
  }

  /**
   * Get the time-integrated wall or radial-layer energy change.
   *
   * @return final-minus-initial wall energy in joules
   */
  public double getWallEnergyChangeJ() {
    return wallEnergyChangeJ;
  }

  /**
   * Get the net sensible energy added by conservative face advection.
   *
   * @return advective energy in joules
   */
  public double getSensibleAdvectionEnergyJ() {
    return sensibleAdvectionEnergyJ;
  }

  /**
   * Get the net Joule-Thomson source contribution.
   *
   * @return Joule-Thomson energy in joules
   */
  public double getJouleThomsonEnergyJ() {
    return jouleThomsonEnergyJ;
  }

  /**
   * Get energy transferred from the wall or outer layer to ambient.
   *
   * @return ambient heat loss in joules, positive when energy leaves the domain
   */
  public double getAmbientHeatLossJ() {
    return ambientHeatLossJ;
  }

  /**
   * Get the fluid-plus-wall energy change.
   *
   * @return combined energy change in joules
   */
  public double getStoredEnergyChangeJ() {
    return fluidEnergyChangeJ + wallEnergyChangeJ;
  }

  /**
   * Get the signed discrete balance residual.
   *
   * @return residual in joules
   */
  public double getResidualJ() {
    return getStoredEnergyChangeJ() - sensibleAdvectionEnergyJ - jouleThomsonEnergyJ + ambientHeatLossJ;
  }

  /**
   * Get the absolute residual relative to the largest stored-energy or integrated-transfer term.
   *
   * @return non-negative relative residual
   */
  public double getRelativeResidual() {
    double scale = Math.max(Math.abs(fluidEnergyChangeJ), Math.abs(wallEnergyChangeJ));
    scale = Math.max(scale, Math.abs(getStoredEnergyChangeJ()));
    scale = Math.max(scale, Math.abs(sensibleAdvectionEnergyJ));
    scale = Math.max(scale, Math.abs(jouleThomsonEnergyJ));
    scale = Math.max(scale, Math.abs(ambientHeatLossJ));
    scale = Math.max(scale, RELATIVE_SCALE_FLOOR_J);
    return Math.abs(getResidualJ()) / scale;
  }

  /**
   * Check an absolute-or-relative balance tolerance.
   *
   * @param absoluteToleranceJ absolute tolerance in joules
   * @param relativeTolerance relative tolerance
   * @return true when either tolerance is met
   */
  public boolean isWithinTolerance(double absoluteToleranceJ, double relativeTolerance) {
    if (absoluteToleranceJ < 0.0 || relativeTolerance < 0.0) {
      throw new IllegalArgumentException("Thermal-energy tolerances must be non-negative");
    }
    return Math.abs(getResidualJ()) <= absoluteToleranceJ || getRelativeResidual() <= relativeTolerance;
  }
}
