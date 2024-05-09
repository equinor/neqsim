package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPASalt;

/**
 * This class defines a thermodynamic system using the CPA-EOS of Equinor equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkCPASalt extends SystemSrkCPAs {
  private static final long serialVersionUID = 1000;

  /**
   * Constructor of a fluid object using the CPA-EoS version of Equinor.
   */
  public SystemSrkCPASalt() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the CPA-EoS version of Equinor.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPASalt(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the CPA-EoS version of Equinor.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPASalt(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS-statoil Salt modified";
    attractiveTermNumber = 15;

    // Recreates phases created in super constructor SystemSrkCPAs
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPASalt();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);

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
  public SystemSrkCPASalt clone() {
    SystemSrkCPASalt clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPASalt) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
