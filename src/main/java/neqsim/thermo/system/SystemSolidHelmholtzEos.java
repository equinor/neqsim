package neqsim.thermo.system;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseSolidHelmholtzEos;
import neqsim.thermo.util.solid.SolidHelmholtzEquation;

/**
 * One-phase thermodynamic system for a pure solid represented by a fundamental Helmholtz EOS.
 *
 * <p>
 * The system participates in NeqSim's {@link SystemEos} hierarchy while deliberately containing only a
 * {@link PhaseSolidHelmholtzEos}. Substance-specific equations and coefficients are supplied through
 * {@link SolidHelmholtzEquation}.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class SystemSolidHelmholtzEos extends SystemEos {
  private static final long serialVersionUID = 1000L;

  private final String solidComponentName;

  /**
   * Construct a pure-solid Helmholtz system containing one mole of the specified component.
   *
   * @param temperature temperature in K; must be non-negative
   * @param pressure pressure in bara; must be non-negative
   * @param componentName component name or database alias
   * @param solidEquation substance-specific fundamental Helmholtz equation
   */
  public SystemSolidHelmholtzEos(double temperature, double pressure, String componentName,
      SolidHelmholtzEquation solidEquation) {
    super(temperature, pressure, false);
    solidComponentName = ComponentInterface.getComponentNameFromAlias(componentName);
    if (solidComponentName == null || solidComponentName.trim().isEmpty()) {
      throw new IllegalArgumentException("Solid component name cannot be empty.");
    }

    modelName = "Solid-Helmholtz-EOS";
    setMaxNumberOfPhases(1);
    setNumberOfPhases(1);
    phaseArray[0] = new PhaseSolidHelmholtzEos(solidEquation);
    phaseArray[0].setTemperature(temperature);
    phaseArray[0].setPressure(pressure);
    useVolumeCorrection(false);
    addComponent(solidComponentName, 1.0);
    setImplementedCompositionDeriativesofFugacity(false);
    setImplementedPressureDeriativesofFugacity(false);
    setImplementedTemperatureDeriativesofFugacity(false);
  }

  /** {@inheritDoc} */
  @Override
  public SystemSolidHelmholtzEos clone() {
    return (SystemSolidHelmholtzEos) super.clone();
  }

  /**
   * Add inventory of the single component supported by this pure-solid system.
   *
   * @param componentName component name or database alias
   * @param moles amount to add in mol
   * @throws IllegalArgumentException if a different component is requested
   */
  @Override
  public void addComponent(String componentName, double moles) {
    String canonicalName = ComponentInterface.getComponentNameFromAlias(componentName);
    if (!solidComponentName.equals(canonicalName)) {
      throw new IllegalArgumentException("SystemSolidHelmholtzEos supports only " + solidComponentName + ".");
    }
    super.addComponent(canonicalName, moles);
  }

  /**
   * Add inventory of the single component supported by this pure-solid system.
   *
   * @param inComponent component to add
   * @throws IllegalArgumentException if a different component is requested
   */
  @Override
  public void addComponent(ComponentInterface inComponent) {
    String canonicalName = ComponentInterface.getComponentNameFromAlias(inComponent.getComponentName());
    if (!solidComponentName.equals(canonicalName)) {
      throw new IllegalArgumentException("SystemSolidHelmholtzEos supports only " + solidComponentName + ".");
    }
    super.addComponent(inComponent);
  }
}