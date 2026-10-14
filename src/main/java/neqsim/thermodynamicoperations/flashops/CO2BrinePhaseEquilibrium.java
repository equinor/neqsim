package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Compatibility adapter for the general frozen-species TP flash.
 *
 * <p>
 * The original CO2/water composition whitelist and CO2-rich trial assumptions have been removed. Molecular components
 * partition through the shared solver, and phase participation follows physical species constraints. New code should
 * use the ordinary TP-flash entry point.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 * @deprecated use {@link TPflash} for fluid equilibrium
 */
@Deprecated
public final class CO2BrinePhaseEquilibrium {
  private final SystemInterface system;
  private double minimumTrialDistance = Double.NaN;
  private PhaseType acceptedRoot;

  /**
   * Creates a compatibility calculation without changing the input fluid.
   *
   * @param system non-reactive fluid
   */
  public CO2BrinePhaseEquilibrium(SystemInterface system) {
    this.system = system;
  }

  /**
   * Checks the frozen-species and physical-phase scope without a component whitelist.
   *
   * @param fluid fluid to inspect
   * @return whether a non-reactive fluid can use this compatibility entry point
   */
  public static boolean isApplicable(SystemInterface fluid) {
    return !fluid.isChemicalSystem() && hasSupportedComposition(fluid);
  }

  /**
   * Checks physical phase capabilities retained by the compatibility entry point.
   *
   * @param fluid fluid to inspect
   * @return whether freely selected fluid phases are supported
   */
  static boolean hasSupportedComposition(SystemInterface fluid) {
    return fluid.getNumberOfComponents() > 0 && !fluid.doSolidPhaseCheck() && !fluid.isForcePhaseTypes()
        && !fluid.isMultiphaseWaxCheck();
  }

  /**
   * Runs the general flash on a detached fluid and adopts only a qualified result.
   *
   * @throws IllegalArgumentException if the physical phase or reaction scope is unsupported
   * @throws IllegalStateException if the general flash fails physical acceptance
   */
  public void run() {
    minimumTrialDistance = Double.NaN;
    acceptedRoot = null;
    if (!isApplicable(system)) {
      throw new IllegalArgumentException("Frozen-species fluid equilibrium requires freely selected fluid phases");
    }
    double[] originalMoles = FluidEquilibriumChecks.inventory(system);
    SystemInterface work = system.clone();
    work.setMultiPhaseCheck(true);
    TPflash flash = new TPflash(work);
    flash.run();
    FluidEquilibriumChecks.validate(work, originalMoles);
    for (double distance : flash.getLastTangentPlaneDistances()) {
      if (Double.isFinite(distance)) {
        minimumTrialDistance = Double.isNaN(minimumTrialDistance) ? distance : Math.min(minimumTrialDistance, distance);
      }
    }
    acceptedRoot = work.getPhaseType(0);
    FlashPhaseState.copyInitializedPhases(work, system);
  }

  /**
   * Returns the requested root of the accepted first material phase.
   *
   * @return configured root, or null before a successful calculation
   */
  PhaseType getAcceptedRoot() {
    return acceptedRoot;
  }

  /**
   * Returns normalized stability evidence from the general fluid calculation.
   *
   * @return minimum molecular trial distance, or NaN when no trial was evaluated
   */
  public double getMinimumTrialDistance() {
    return minimumTrialDistance;
  }
}
