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
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemSrkCPA.
   * </p>
   */
  public SystemSrkCPA() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkCPA.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPA(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkCPA.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPA(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);

    // Recreates phases created in super constructor SystemSrkEos
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPA();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);
    commonInitialization();

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
  public void addComponent(String componentName, double moles) {
    // if (componentName.equals("Ca++") || componentName.equals("Na+") ||
    // componentName.equals("Cl-")) {
    // componentName = "NaCl";
    // }
    super.addComponent(componentName, moles);
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
}
