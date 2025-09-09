package neqsim.thermo.system;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseSpanWagnerEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * Thermodynamic system using the Span-Wagner reference equation for CO2.
 */
public class SystemSpanWagnerEos extends SystemEos {
  private static final long serialVersionUID = 1000;

  public SystemSpanWagnerEos() {
    this(298.15, 1.0, false);
  }

  public SystemSpanWagnerEos(double T, double P) {
    this(T, P, false);
  }

  public SystemSpanWagnerEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Span-Wagner";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSpanWagnerEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
      phaseArray[i].setType(phaseType[i]);
    }
    if (solidPhaseCheck) {
      setNumberOfPhases(4);
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
    this.useVolumeCorrection(false);
    addComponent("CO2", 1.0);
    commonInitialization();
  }

  @Override
  public SystemSpanWagnerEos clone() {
    return (SystemSpanWagnerEos) super.clone();
  }

  @Override
  public void addComponent(String componentName, double moles) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);
    if (!"CO2".equals(componentName)) {
      throw new RuntimeException("SystemSpanWagnerEos supports only CO2");
    }
    super.addComponent(componentName, moles);
  }

  @Override
  public void addComponent(ComponentInterface inComponent) {
    String name = ComponentInterface.getComponentNameFromAlias(inComponent.getComponentName());
    if (!"CO2".equals(name)) {
      throw new RuntimeException("SystemSpanWagnerEos supports only CO2");
    }
    super.addComponent(inComponent);
  }

  /** Perform common initialisation tasks. */
  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(false);
    setImplementedPressureDeriativesofFugacity(false);
    setImplementedTemperatureDeriativesofFugacity(false);
  }
}
