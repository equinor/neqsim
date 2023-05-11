package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the PR Danesh equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPrDanesh extends SystemPrEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPrDanesh.
   * </p>
   */
  public SystemPrDanesh() {
    super();
    modelName = "PR-Danesh-EOS";
    attractiveTermNumber = 9;
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEos();
      phaseArray[i].setTemperature(298.15);
      phaseArray[i].setPressure(1.0);
    }
  }

  /**
   * <p>
   * Constructor for SystemPrDanesh.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPrDanesh(double T, double P) {
    super(T, P);
    modelName = "PR-Danesh-EOS";
    attractiveTermNumber = 9;
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /**
   * <p>
   * Constructor for SystemPrDanesh.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPrDanesh(double T, double P, boolean checkForSolids) {
    this(T, P);
    modelName = "PR-Danesh-EOS";
    attractiveTermNumber = 9;
    setNumberOfPhases(5);
    solidPhaseCheck = checkForSolids;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      // System.out.println("here first");
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }

    if (hydrateCheck) {
      // System.out.println("here first");
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemPrDanesh clone() {
    SystemPrDanesh clonedSystem = null;
    try {
      clonedSystem = (SystemPrDanesh) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    // clonedSystem.phaseArray = (PhaseInterface[]) phaseArray.clone();
    // for(int i = 0; i < numberOfPhases; i++) {
    // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
    // }

    return clonedSystem;
  }
}
