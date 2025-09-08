package neqsim.thermo.system;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseVegaEos;

/**
 * This class defines a thermodynamic system using the VegaEos equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemVegaEos extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemVegaEos.
   * </p>
   */
  public SystemVegaEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemVegaEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemVegaEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemVegaEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemVegaEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Vega-EOS";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseVegaEos();
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

    // What could set hydratecheck? Will never be true
    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
    this.useVolumeCorrection(false);
    addComponent("helium", 1.0);
    commonInitialization();
  }

  /** {@inheritDoc} */
  @Override
  public SystemVegaEos clone() {
    SystemVegaEos clonedSystem = null;
    try {
      clonedSystem = (SystemVegaEos) super.clone();
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
    setImplementedCompositionDeriativesofFugacity(false);
    setImplementedPressureDeriativesofFugacity(false);
    setImplementedTemperatureDeriativesofFugacity(false);
  }

  @Override
  public void addComponent(String componentName, double moles) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);
    if (!"helium".equals(componentName)) {
      throw new RuntimeException("SystemVegaEos supports only helium");
    }
    super.addComponent(componentName, moles);
  }

  @Override
  public void addComponent(ComponentInterface inComponent) {
    String name = ComponentInterface.getComponentNameFromAlias(inComponent.getComponentName());
    if (!"helium".equals(name)) {
      throw new RuntimeException("SystemVegaEos supports only helium");
    }
    super.addComponent(inComponent);
  }
}
