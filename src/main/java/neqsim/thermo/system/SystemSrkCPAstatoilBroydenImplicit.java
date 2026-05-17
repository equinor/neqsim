package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPABroydenImplicit;

/**
 * Thermodynamic system using the Broyden quasi-Newton implicit CPA-EOS algorithm.
 *
 * <p>
 * Uses Broyden rank-1 updates of the inverse Jacobian after the first full Newton step to reduce
 * per-iteration cost from O(n_s^3) to O(n_s^2). Falls back to standard nested approach if the
 * solver does not converge.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemSrkCPAstatoilBroydenImplicit extends SystemSrkCPAstatoil {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor of a fluid object using the Broyden implicit CPA-EoS.
   */
  public SystemSrkCPAstatoilBroydenImplicit() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the Broyden implicit CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPAstatoilBroydenImplicit(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the Broyden implicit CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPAstatoilBroydenImplicit(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS-statoil-BroydenImplicit";

    // Recreate phases with Broyden implicit CPA phase
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPABroydenImplicit();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }

    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkCPAstatoilBroydenImplicit clone() {
    SystemSrkCPAstatoilBroydenImplicit clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAstatoilBroydenImplicit) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
