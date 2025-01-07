package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkPenelouxEos;

/**
 * This class defines a thermodynamic system using the SRK Peneloux equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkPenelouxEos extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor of a fluid object using the SRK-EoS.
   */
  public SystemSrkPenelouxEos() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the SRK-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkPenelouxEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the SRK-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkPenelouxEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "SRK-Peneloux-EOS";
    getCharacterization().setTBPModel("PedersenSRK");
    attractiveTermNumber = 0;

    // Recreates phases created in super constructor SystemSrkEos
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkPenelouxEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      setNumberOfPhases(5);
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
  public SystemSrkPenelouxEos clone() {
    SystemSrkPenelouxEos clonedSystem = null;
    try {
      clonedSystem = (SystemSrkPenelouxEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
