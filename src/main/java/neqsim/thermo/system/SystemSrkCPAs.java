package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAsOld;

/**
 * This class defines a thermodynamic system using the sCPA-EOS equation of state.
 * 
 * @author Even Solbraa
 */
public class SystemSrkCPAs extends SystemSrkCPA {
  private static final long serialVersionUID = 1000;

  public SystemSrkCPAs() {
    super();
    this.useVolumeCorrection(true);
    modelName = "CPAs-SRK-EOS";
  }

  /**
   * <p>
   * Constructor for SystemSrkCPAs.
   * </p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemSrkCPAs(double T, double P) {
    super(T, P);
    modelName = "CPAs-SRK-EOS";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAsOld();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);
  }

  /**
   * <p>
   * Constructor for SystemSrkCPAs.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param solidCheck a boolean
   */
  public SystemSrkCPAs(double T, double P, boolean solidCheck) {
    super(T, P, solidCheck);
    modelName = "CPAs-SRK-EOS";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAsOld();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);

    if (solidPhaseCheck) {
      // System.out.println("here first");
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }

    if (hydrateCheck) {
      // System.out.println("here first");
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkCPAs clone() {
    SystemSrkCPAs clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAs) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    // for(int i = 0; i < numberOfPhases; i++) {
    // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
    // }

    return clonedSystem;
  }
}
