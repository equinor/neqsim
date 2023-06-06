package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the PR EoS version of Delft (1998) equation of
 * state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPrEosDelft1998 extends SystemPrEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPrEosDelft1998.
   * </p>
   */
  public SystemPrEosDelft1998() {
    super();
    modelName = "PR Delft1998 EOS";
    attractiveTermNumber = 7;
  }

  /**
   * <p>
   * Constructor for SystemPrEosDelft1998.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPrEosDelft1998(double T, double P) {
    super(T, P);
    modelName = "PR Delft1998 EOS";
    attractiveTermNumber = 7;
  }

  /**
   * <p>
   * Constructor for SystemPrEosDelft1998.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPrEosDelft1998(double T, double P, boolean checkForSolids) {
    this(T, P);
    attractiveTermNumber = 7;
    modelName = "PR Delft1998 EOS";

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
  public SystemPrEosDelft1998 clone() {
    SystemPrEosDelft1998 clonedSystem = null;
    try {
      clonedSystem = (SystemPrEosDelft1998) super.clone();
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
