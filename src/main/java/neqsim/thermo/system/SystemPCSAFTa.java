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
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPCSAFTa.
   * </p>
   */
  public SystemPCSAFTa() {
    this(298.15, 1.0, false);
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
    this(T, P, false);
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
    super(T, P, checkForSolids);
    modelName = "PCSAFTa-EOS";
    attractiveTermNumber = 0;

    // Recreates phases created in super constructor SystemSrkEos
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePCSAFTa();
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

    return clonedSystem;
  }
}
