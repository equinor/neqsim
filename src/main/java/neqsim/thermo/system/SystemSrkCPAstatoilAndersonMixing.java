package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAandersonMixing;

/**
 * Thermodynamic system using the Anderson-accelerated nested CPA-EOS algorithm.
 *
 * <p>
 * Retains the Halley outer loop for molar volume but replaces the inner successive substitution
 * loop for site fractions with Anderson acceleration (mixing depth m=3), achieving superlinear
 * convergence in 3-5 inner iterations instead of 5-15 for plain SS.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemSrkCPAstatoilAndersonMixing extends SystemSrkCPAstatoil {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor of a fluid object using the Anderson-accelerated CPA-EoS.
   */
  public SystemSrkCPAstatoilAndersonMixing() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the Anderson-accelerated CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPAstatoilAndersonMixing(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the Anderson-accelerated CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPAstatoilAndersonMixing(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS-statoil-AndersonMixing";

    // Recreate phases with Anderson-accelerated CPA phase
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAandersonMixing();
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
  public SystemSrkCPAstatoilAndersonMixing clone() {
    SystemSrkCPAstatoilAndersonMixing clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAstatoilAndersonMixing) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
