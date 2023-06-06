package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePCSAFTa;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the PC-SAFT with association equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPCSAFTa extends SystemSrkEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPCSAFTa.
   * </p>
   */
  public SystemPCSAFTa() {
    super();
    modelName = "PCSAFTa-EOS";
    attractiveTermNumber = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePCSAFTa();
      phaseArray[i].setTemperature(298.15);
      phaseArray[i].setPressure(1.0);
    }
    this.useVolumeCorrection(false);
  }

  /**
   * <p>
   * Constructor for SystemPCSAFTa.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPCSAFTa(double T, double P) {
    super(T, P);
    modelName = "PCSAFTa-EOS";
    attractiveTermNumber = 0;
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePCSAFTa();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(false);
  }

  /**
   * <p>
   * Constructor for SystemPCSAFTa.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPCSAFTa(double T, double P, boolean checkForSolids) {
    this(T, P);
    modelName = "PCSAFTa-EOS";
    attractiveTermNumber = 0;
    setNumberOfPhases(5);
    solidPhaseCheck = checkForSolids;
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePCSAFTa();
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
    this.useVolumeCorrection(false);
  }

  /** {@inheritDoc} */
  @Override
  public SystemPCSAFTa clone() {
    SystemPCSAFTa clonedSystem = null;
    try {
      clonedSystem = (SystemPCSAFTa) super.clone();
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
