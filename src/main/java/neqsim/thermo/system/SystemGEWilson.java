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
   * <p>Constructor for SystemGEWilson.</p>
   */
  public SystemGEWilson() {
    super();
    modelName = "UNIFAC-GE-model";
    attractiveTermNumber = 0;
    phaseArray[0] = new PhaseSrkEos();
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseGEWilson();
    }
  }

  /**
   * <p>
   * Constructor for SystemGEWilson.
   * </p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemGEWilson(double T, double P) {
    super(T, P);
    attractiveTermNumber = 0;
    modelName = "UNIFAC-GE-model";
    phaseArray[0] = new PhaseSrkEos();
    phaseArray[0].setTemperature(T);
    phaseArray[0].setPressure(P);
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseGEWilson();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /**
   * <p>
   * Constructor for SystemGEWilson.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param solidCheck a boolean
   */
  public SystemGEWilson(double T, double P, boolean solidCheck) {
    this(T, P);
    attractiveTermNumber = 0;
    setNumberOfPhases(4);
    modelName = "UNIFAC-GE-model";
    solidPhaseCheck = solidCheck;

    phaseArray[0] = new PhaseSrkEos();
    phaseArray[0].setTemperature(T);
    phaseArray[0].setPressure(P);
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseGEWilson();
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

    // for(int i = 0; i < numberOfPhases; i++) {
    // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
    // }

    return clonedSystem;
  }
}
