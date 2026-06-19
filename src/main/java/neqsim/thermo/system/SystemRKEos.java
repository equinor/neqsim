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
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemRKEos.
   * </p>
   */
  public SystemRKEos() {
    this(298.15, 1.0, false);
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
    this(T, P, false);
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
    super(T, P, checkForSolids);
    modelName = "RK-EOS";
    attractiveTermNumber = 5;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseRK();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      setNumberOfPhases(4);
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
