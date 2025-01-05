package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGEWilson;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * This class defines a thermodynamic system using the Wilson GE model.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemGEWilson extends SystemEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemGEWilson.
   * </p>
   */
  public SystemGEWilson() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemGEWilson.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemGEWilson(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemGEWilson.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemGEWilson(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "UNIFAC-GE-model";
    attractiveTermNumber = 0;

    phaseArray[0] = new PhaseSrkEos();
    phaseArray[0].setTemperature(T);
    phaseArray[0].setPressure(P);
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseGEWilson();
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
  public SystemGEWilson clone() {
    SystemGEWilson clonedSystem = null;
    try {
      clonedSystem = (SystemGEWilson) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
