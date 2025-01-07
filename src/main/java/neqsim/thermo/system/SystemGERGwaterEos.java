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
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemGERGwaterEos.
   * </p>
   */
  public SystemGERGwaterEos() {
    this(298.15, 1.0, false);
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
    this(T, P, false);
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
    super(T, P, checkForSolids);
    modelName = "GERG-water-EOS";
    attractiveTermNumber = 10;

    // Recreates phases created in super constructor SystemPrEos
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
  public SystemGERGwaterEos clone() {
    SystemGERGwaterEos clonedSystem = null;
    try {
      clonedSystem = (SystemGERGwaterEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
