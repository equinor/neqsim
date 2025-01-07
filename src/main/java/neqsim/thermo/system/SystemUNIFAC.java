package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * This class defines a thermodynamic system using the Unifac for liquids with SRK equation of state
 * for gas.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUNIFAC extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemUNIFAC.
   * </p>
   */
  public SystemUNIFAC() {
    this(273.15, 0);
  }

  /**
   * <p>
   * Constructor for SystemUNIFAC.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUNIFAC(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemUNIFAC.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemUNIFAC(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "UNIFAC-GE-model";
    attractiveTermNumber = 0;

    solidPhaseCheck = checkForSolids;

    phaseArray[0] = new PhaseSrkEos();
    phaseArray[0].setTemperature(T);
    phaseArray[0].setPressure(P);
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseGEUnifac();
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
  public SystemUNIFAC clone() {
    SystemUNIFAC clonedSystem = null;
    try {
      clonedSystem = (SystemUNIFAC) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
