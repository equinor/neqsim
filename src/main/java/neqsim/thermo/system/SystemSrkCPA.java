package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPA;

/**
 * This class defines a thermodynamic system using the CPA EoS equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkCPA extends SystemSrkEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>Constructor for SystemSrkCPA.</p>
   */
  public SystemSrkCPA() {
    super();
    modelName = "CPA-SRK-EOS";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPA();
    }
    this.useVolumeCorrection(true);
    commonInitialization();
  }

  /**
   * <p>
   * Constructor for SystemSrkCPA.
   * </p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemSrkCPA(double T, double P) {
    super(T, P);
    modelName = "CPA-SRK-EOS";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPA();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);
    commonInitialization();
  }

  /**
   * <p>
   * Constructor for SystemSrkCPA.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param solidCheck a boolean
   */
  public SystemSrkCPA(double T, double P, boolean solidCheck) {
    super(T, P, solidCheck);
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPA();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);
    commonInitialization();

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
  public SystemSrkCPA clone() {
    SystemSrkCPA clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPA) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    // for(int i = 0; i < numberOfPhases; i++) {
    // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
    // }

    return clonedSystem;
  }

  /**
   * <p>
   * commonInitialization.
   * </p>
   */
  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(true);
    setImplementedPressureDeriativesofFugacity(true);
    setImplementedTemperatureDeriativesofFugacity(true);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double moles) {
    // if (componentName.equals("Ca++") || componentName.equals("Na+") ||
    // componentName.equals("Cl-")) {
    // componentName = "NaCl";
    // }
    super.addComponent(componentName, moles);
  }
}
