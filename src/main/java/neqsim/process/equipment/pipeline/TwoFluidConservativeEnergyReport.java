package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Accepted fluid total-energy accounting for the opt-in conservative thermodynamic recovery route.
 *
 * <p>
 * Stored energy includes phase internal and kinetic energy, with the equation of state's caloric reference. Positive
 * advected energy, gravitational work and wall heat add energy to the fluid. Advected energy includes the accepted
 * pressure-correction face transfers. Wall heat includes any direct electrical heating deposited in the fluid; wall
 * storage and ambient exchange are outside this fluid control volume. Neither Joule-Thomson nor latent heat is an
 * independent energy source when phase enthalpies and internal energies already represent these effects.
 * </p>
 *
 * <p>
 * This discrete ledger and its independent published-state residuals establish numerical consistency for the accepted
 * interval. They do not establish experimental qualification of the phase-transfer or hydrodynamic closures.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TwoFluidConservativeEnergyReport implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double RELATIVE_SCALE_FLOOR_J = 1.0e-12;

  private final double elapsedTimeSeconds;
  private final double initialEnergyJ;
  private final double finalEnergyJ;
  private final double inletAdvectedEnergyJ;
  private final double outletAdvectedEnergyJ;
  private final double gravitationalWorkJ;
  private final double wallHeatToFluidJ;
  private final double maxRelativeVolumeResidual;
  private final double maxRelativeThermodynamicEnergyResidual;

  /**
   * Construct an accepted-interval fluid energy ledger.
   *
   * @param elapsedTimeSeconds accepted elapsed time in seconds
   * @param initialEnergyJ initial integrated internal plus kinetic energy in joules
   * @param finalEnergyJ final integrated internal plus kinetic energy in joules
   * @param netAdvectedEnergyJ accepted net total-enthalpy and kinetic energy entering through external faces, in joules
   * @param gravitationalWorkJ integrated gravitational work on the fluid in joules
   * @param wallHeatToFluidJ integrated heat added to the fluid, including electrical heating, in joules
   * @param maxRelativeVolumeResidual maximum absolute occupied-volume residual relative to cell volume
   * @param maxRelativeThermodynamicEnergyResidual maximum absolute discrepancy between conservative and reconstructed
   * thermodynamic total energy, relative to the recovery solver's energy scale
   * @throws IllegalArgumentException if a quantity is nonfinite or a time/residual is negative
   */
  public TwoFluidConservativeEnergyReport(double elapsedTimeSeconds, double initialEnergyJ, double finalEnergyJ,
      double netAdvectedEnergyJ, double gravitationalWorkJ, double wallHeatToFluidJ, double maxRelativeVolumeResidual,
      double maxRelativeThermodynamicEnergyResidual) {
    this(elapsedTimeSeconds, initialEnergyJ, finalEnergyJ, netAdvectedEnergyJ, 0.0, gravitationalWorkJ,
        wallHeatToFluidJ, maxRelativeVolumeResidual, maxRelativeThermodynamicEnergyResidual);
  }

  /**
   * Construct an accepted-interval fluid energy ledger with boundary-resolved advection.
   *
   * <p>
   * Boundary transfers retain their finite-volume sign convention: positive inlet energy enters from the upstream
   * boundary and positive outlet energy leaves through the downstream boundary. Their difference is the net advected
   * energy added to the pipe. Keeping the two transfers separate allows a network junction to route the exact accepted
   * branch energy without reconstructing it from an end state.
   * </p>
   *
   * @param elapsedTimeSeconds accepted elapsed time in seconds
   * @param initialEnergyJ initial integrated internal plus kinetic energy in joules
   * @param finalEnergyJ final integrated internal plus kinetic energy in joules
   * @param inletAdvectedEnergyJ signed accepted energy through the inlet face in joules
   * @param outletAdvectedEnergyJ signed accepted energy through the outlet face in joules
   * @param gravitationalWorkJ integrated gravitational work on the fluid in joules
   * @param wallHeatToFluidJ integrated heat added to the fluid, including electrical heating, in joules
   * @param maxRelativeVolumeResidual maximum absolute occupied-volume residual relative to cell volume
   * @param maxRelativeThermodynamicEnergyResidual maximum absolute discrepancy between conservative and reconstructed
   * thermodynamic total energy, relative to the recovery solver's energy scale
   * @throws IllegalArgumentException if a quantity is nonfinite or a time/residual is negative
   */
  public TwoFluidConservativeEnergyReport(double elapsedTimeSeconds, double initialEnergyJ, double finalEnergyJ,
      double inletAdvectedEnergyJ, double outletAdvectedEnergyJ, double gravitationalWorkJ, double wallHeatToFluidJ,
      double maxRelativeVolumeResidual, double maxRelativeThermodynamicEnergyResidual) {
    requireNonnegativeFinite(elapsedTimeSeconds, "elapsedTimeSeconds");
    requireFinite(initialEnergyJ, "initialEnergyJ");
    requireFinite(finalEnergyJ, "finalEnergyJ");
    requireFinite(inletAdvectedEnergyJ, "inletAdvectedEnergyJ");
    requireFinite(outletAdvectedEnergyJ, "outletAdvectedEnergyJ");
    requireFinite(gravitationalWorkJ, "gravitationalWorkJ");
    requireFinite(wallHeatToFluidJ, "wallHeatToFluidJ");
    requireNonnegativeFinite(maxRelativeVolumeResidual, "maxRelativeVolumeResidual");
    requireNonnegativeFinite(maxRelativeThermodynamicEnergyResidual, "maxRelativeThermodynamicEnergyResidual");
    this.elapsedTimeSeconds = elapsedTimeSeconds;
    this.initialEnergyJ = initialEnergyJ;
    this.finalEnergyJ = finalEnergyJ;
    this.inletAdvectedEnergyJ = inletAdvectedEnergyJ;
    this.outletAdvectedEnergyJ = outletAdvectedEnergyJ;
    this.gravitationalWorkJ = gravitationalWorkJ;
    this.wallHeatToFluidJ = wallHeatToFluidJ;
    this.maxRelativeVolumeResidual = maxRelativeVolumeResidual;
    this.maxRelativeThermodynamicEnergyResidual = maxRelativeThermodynamicEnergyResidual;
  }

  /**
   * Require a finite scalar.
   *
   * @param value value to validate
   * @param name diagnostic field name
   * @throws IllegalArgumentException if the value is nonfinite
   */
  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /**
   * Require a finite, nonnegative scalar.
   *
   * @param value value to validate
   * @param name diagnostic field name
   * @throws IllegalArgumentException if the value is negative or nonfinite
   */
  private static void requireNonnegativeFinite(double value, String name) {
    requireFinite(value, name);
    if (value < 0.0) {
      throw new IllegalArgumentException(name + " must be nonnegative");
    }
  }

  /** @return accepted elapsed time in seconds */
  public double getElapsedTimeSeconds() {
    return elapsedTimeSeconds;
  }

  /** @return initial integrated internal plus phase kinetic energy in joules */
  public double getInitialEnergyJ() {
    return initialEnergyJ;
  }

  /** @return final integrated internal plus phase kinetic energy in joules */
  public double getFinalEnergyJ() {
    return finalEnergyJ;
  }

  /** @return final-minus-initial fluid total energy in joules */
  public double getStoredEnergyChangeJ() {
    return finalEnergyJ - initialEnergyJ;
  }

  /** @return signed total-enthalpy and kinetic energy entering through the inlet face, in joules */
  public double getInletAdvectedEnergyJ() {
    return inletAdvectedEnergyJ;
  }

  /** @return signed total-enthalpy and kinetic energy leaving through the outlet face, in joules */
  public double getOutletAdvectedEnergyJ() {
    return outletAdvectedEnergyJ;
  }

  /** @return net external-face total-enthalpy and kinetic energy entering the fluid, in joules */
  public double getNetAdvectedEnergyJ() {
    return inletAdvectedEnergyJ - outletAdvectedEnergyJ;
  }

  /** @return gravitational work added to the fluid in joules */
  public double getGravitationalWorkJ() {
    return gravitationalWorkJ;
  }

  /** @return heat added to the fluid, including electrical heating, in joules */
  public double getWallHeatToFluidJ() {
    return wallHeatToFluidJ;
  }

  /** @return maximum absolute cell-volume residual divided by cell volume */
  public double getMaxRelativeVolumeResidual() {
    return maxRelativeVolumeResidual;
  }

  /** @return maximum relative conservative-versus-published-state energy discrepancy */
  public double getMaxRelativeThermodynamicEnergyResidual() {
    return maxRelativeThermodynamicEnergyResidual;
  }

  /** @return signed fluid first-law residual in joules */
  public double getResidualJ() {
    return getStoredEnergyChangeJ() - getNetAdvectedEnergyJ() - gravitationalWorkJ - wallHeatToFluidJ;
  }

  /**
   * Scale the absolute first-law residual by the largest integrated transfer or storage change. Absolute stored energy
   * is deliberately excluded so the arbitrary caloric reference cannot hide an energy-balance error.
   *
   * @return nonnegative relative first-law residual
   */
  public double getRelativeResidual() {
    double scale = Math.max(Math.abs(getStoredEnergyChangeJ()), Math.abs(getNetAdvectedEnergyJ()));
    scale = Math.max(scale, Math.abs(gravitationalWorkJ));
    scale = Math.max(scale, Math.abs(wallHeatToFluidJ));
    return Math.abs(getResidualJ()) / Math.max(scale, RELATIVE_SCALE_FLOOR_J);
  }

  /**
   * Check an absolute-or-relative first-law tolerance. Published-state energy and volume residuals are separately
   * available and must also be checked when qualifying thermodynamic recovery.
   *
   * @param absoluteToleranceJ nonnegative absolute first-law tolerance in joules
   * @param relativeTolerance nonnegative relative first-law tolerance
   * @return true if either first-law tolerance is met
   * @throws IllegalArgumentException if either tolerance is negative or nonfinite
   */
  public boolean isWithinTolerance(double absoluteToleranceJ, double relativeTolerance) {
    requireNonnegativeFinite(absoluteToleranceJ, "absoluteToleranceJ");
    requireNonnegativeFinite(relativeTolerance, "relativeTolerance");
    return Math.abs(getResidualJ()) <= absoluteToleranceJ || getRelativeResidual() <= relativeTolerance;
  }
}
