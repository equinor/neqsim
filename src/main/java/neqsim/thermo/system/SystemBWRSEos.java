package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseBWRSEos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the BWRS equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemBWRSEos extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  double[][] TBPfractionCoefs = {{163.12, 86.052, 0.43475, -1877.4, 0.0},
      {-0.13408, 2.5019, 208.46, -3987.2, 1.0}, {0.7431, 0.004812, 0.009671, -3.7e-6, 0.0}};

  /**
   * <p>
   * Constructor for SystemBWRSEos.
   * </p>
   */
  public SystemBWRSEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemBWRSEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemBWRSEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemBWRSEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemBWRSEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "BWRS-EOS";
    attractiveTermNumber = 0;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseBWRSEos();
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
  public SystemBWRSEos clone() {
    SystemBWRSEos clonedSystem = null;
    try {
      clonedSystem = (SystemBWRSEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
