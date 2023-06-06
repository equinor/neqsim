package neqsim.thermo.system;

import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseRK;

/**
 * This class defines a thermodynamic system using the RK equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemRKEos extends SystemEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemRKEos.
   * </p>
   */
  public SystemRKEos() {
    super();
    modelName = "RK-EOS";
    attractiveTermNumber = 5;
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseRK();
      phaseArray[i].setTemperature(298.15);
      phaseArray[i].setPressure(1.0);
    }
  }

  /**
   * <p>
   * Constructor for SystemRKEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemRKEos(double T, double P) {
    super(T, P);
    attractiveTermNumber = 5;
    modelName = "RK-EOS";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseRK();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /**
   * <p>
   * Constructor for SystemRKEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemRKEos(double T, double P, boolean checkForSolids) {
    this(T, P);
    attractiveTermNumber = 5;
    setNumberOfPhases(4);
    modelName = "RK-EOS";
    solidPhaseCheck = checkForSolids;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseRK();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemRKEos clone() {
    SystemRKEos clonedSystem = null;
    try {
      clonedSystem = (SystemRKEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
