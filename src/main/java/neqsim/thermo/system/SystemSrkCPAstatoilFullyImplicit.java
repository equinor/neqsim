package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAfullyImplicit;

/**
 * Thermodynamic system using the fully implicit CPA-EOS algorithm.
 *
 * <p>
 * Uses the fully implicit algorithm from Igben et al. (2026) for simultaneous solution of molar
 * volume and association site fractions. Falls back to standard nested approach if the implicit
 * solver does not converge.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemSrkCPAstatoilFullyImplicit extends SystemSrkCPAstatoil {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor of a fluid object using the fully implicit CPA-EoS.
   */
  public SystemSrkCPAstatoilFullyImplicit() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the fully implicit CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPAstatoilFullyImplicit(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the fully implicit CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPAstatoilFullyImplicit(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS-statoil-FullyImplicit";

    // Recreate phases with fully implicit CPA phase
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAfullyImplicit();
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
  public SystemSrkCPAstatoilFullyImplicit clone() {
    SystemSrkCPAstatoilFullyImplicit clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAstatoilFullyImplicit) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
