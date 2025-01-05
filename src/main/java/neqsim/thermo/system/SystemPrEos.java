package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the Peng Robinson equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPrEos extends SystemEos {
  private static final long serialVersionUID = 1000;

  /**
   * Constructor of a fluid object using the SRK equation of state.
   */
  public SystemPrEos() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the PR-EoS (Peng Robinson).
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPrEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the PR-EoS (Peng Robinson).
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPrEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "PR-EOS";
    getCharacterization().setTBPModel("PedersenPR");
    attractiveTermNumber = 1;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEos();
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
  public SystemPrEos clone() {
    SystemPrEos clonedSystem = null;
    try {
      clonedSystem = (SystemPrEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
