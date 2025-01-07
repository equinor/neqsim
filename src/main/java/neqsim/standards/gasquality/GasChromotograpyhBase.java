package neqsim.standards.gasquality;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GasChromotograpyhBase class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class GasChromotograpyhBase extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  String unit = "mol%";

  /**
   * <p>
   * Constructor for GasChromotograpyhBase.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public GasChromotograpyhBase(SystemInterface thermoSystem) {
    super("gas chromotography", "Gas composition", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    thermoSystem.init(0);
    thermoSystem.init(0);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String componentName, String returnUnit) {
    unit = returnUnit;
    if (returnUnit.equals("mol%")) {
      return 100 * thermoSystem.getPhase(0).getComponent(componentName).getz();
    }
    if (returnUnit.equals("mg/m3")) {
      return thermoSystem.getPhase(0).getComponent(componentName).getz() * 1.0e6;
    } else {
      return thermoSystem.getPhase(0).getComponent(componentName).getz();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String componentName) {
    return thermoSystem.getPhase(0).getComponent(componentName).getz();
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    return unit;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return true;
  }
}
