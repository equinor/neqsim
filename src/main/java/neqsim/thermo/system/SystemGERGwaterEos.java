package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the GERG water equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemGERGwaterEos extends SystemPrEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemGERGwaterEos.
   * </p>
   */
  public SystemGERGwaterEos() {
    super();
    modelName = "GERG-water-EOS";
    attractiveTermNumber = 10;
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEos();
      phaseArray[i].setTemperature(298.15);
      phaseArray[i].setPressure(1.0);
    }
  }

  /**
   * <p>
   * Constructor for SystemGERGwaterEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemGERGwaterEos(double T, double P) {
    super(T, P);
    modelName = "GERG-water-EOS";
    attractiveTermNumber = 10;
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /**
   * <p>
   * Constructor for SystemGERGwaterEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemGERGwaterEos(double T, double P, boolean checkForSolids) {
    this(T, P);
    modelName = "GERG-water-EOS";
    attractiveTermNumber = 10;
    setNumberOfPhases(5);
    solidPhaseCheck = checkForSolids;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
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
  public SystemGERGwaterEos clone() {
    SystemGERGwaterEos clonedSystem = null;
    try {
      clonedSystem = (SystemGERGwaterEos) super.clone();
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
