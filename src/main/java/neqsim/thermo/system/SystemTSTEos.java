package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseTSTEos;

/**
 * This class defines a thermodynamic system using the TST equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemTSTEos extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  double[][] TBPfractionCoefs = {{73.404, 97.356, 0.61874, -2059.3, 0.0},
      {0.072846, 2.1881, 163.91, -4043.4, 1.0 / 3.0}, {0.37377, 0.005493, 0.011793, -4.9e-6, 0.0}};

  /**
   * <p>
   * Constructor for SystemTSTEos.
   * </p>
   */
  public SystemTSTEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemTSTEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemTSTEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemTSTEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemTSTEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "TST-EOS";
    attractiveTermNumber = 14;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseTSTEos();
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
  public SystemTSTEos clone() {
    SystemTSTEos clonedSystem = null;
    try {
      clonedSystem = (SystemTSTEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
