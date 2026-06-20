package neqsim.thermo.system;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseAmmoniaEos;

/**
 * Thermodynamic system using a simplified ammonia reference equation of state.
 *
 * @author esol
 */
public class SystemAmmoniaEos extends SystemEos {
  private static final long serialVersionUID = 1000L;

  /**
   * <p>
   * Constructor for SystemAmmoniaEos.
   * </p>
   */
  public SystemAmmoniaEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemAmmoniaEos.
   * </p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemAmmoniaEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemAmmoniaEos.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param checkForSolids a boolean
   */
  public SystemAmmoniaEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Ammonia-EOS";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseAmmoniaEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(false);
    addComponent("ammonia", 1.0);
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i].getPhysicalProperties().setViscosityModel("PFCT");
      phaseArray[i].getPhysicalProperties().setConductivityModel("PFCT");
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemAmmoniaEos clone() {
    SystemAmmoniaEos cloned = null;
    try {
      cloned = (SystemAmmoniaEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return cloned;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String componentName, double moles) {
    componentName = ComponentInterface.getComponentNameFromAlias(componentName);
    if (!"ammonia".equals(componentName)) {
      throw new RuntimeException("SystemAmmoniaEos supports only ammonia");
    }
    super.addComponent(componentName, moles);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(ComponentInterface inComponent) {
    String name = ComponentInterface.getComponentNameFromAlias(inComponent.getComponentName());
    if (!"ammonia".equals(name)) {
      throw new RuntimeException("SystemAmmoniaEos supports only ammonia");
    }
    super.addComponent(inComponent);
  }
}
