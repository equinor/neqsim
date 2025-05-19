package neqsim.util.unit;

/**
 * <p>
 * NeqSimUnitSet class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class NeqSimUnitSet {
  private String temperatureUnit = "K";

  private String pressureUnit = "bara";

  private String flowRateUnit = "mol/sec";

  private String componentConcentrationUnit = "molefraction";

  /**
   * <p>
   * Getter for the field <code>componentConcentrationUnit</code>.
   * </p>
   *
   * @return the componentConcentrationUnit
   */
  public String getComponentConcentrationUnit() {
    return componentConcentrationUnit;
  }

  /**
   * <p>
   * Getter for the field <code>flowRateUnit</code>.
   * </p>
   *
   * @return the flowRateUnit
   */
  public String getFlowRateUnit() {
    return flowRateUnit;
  }

  /**
   * <p>
   * Getter for the field <code>pressureUnit</code>.
   * </p>
   *
   * @return the pressureUnit
   */
  public String getPressureUnit() {
    return pressureUnit;
  }

  /**
   * <p>
   * Getter for the field <code>temperatureUnit</code>.
   * </p>
   *
   * @return the temperatureUnit
   */
  public String getTemperatureUnit() {
    return temperatureUnit;
  }

  /**
   * <p>
   * Setter for the field <code>componentConcentrationUnit</code>.
   * </p>
   *
   * @param componentConcentrationUnit the componentConcentrationUnit to set
   */
  public void setComponentConcentrationUnit(String componentConcentrationUnit) {
    this.componentConcentrationUnit = componentConcentrationUnit;
  }

  /**
   * <p>
   * Setter for the field <code>flowRateUnit</code>.
   * </p>
   *
   * @param flowRateUnit the flowRateUnit to set
   */
  public void setFlowRateUnit(String flowRateUnit) {
    this.flowRateUnit = flowRateUnit;
  }

  /**
   * <p>
   * Setter for the field <code>pressureUnit</code>.
   * </p>
   *
   * @param pressureUnit the pressureUnit to set
   */
  public void setPressureUnit(String pressureUnit) {
    this.pressureUnit = pressureUnit;
  }

  /**
   * <p>
   * Setter for the field <code>temperatureUnit</code>.
   * </p>
   *
   * @param temperatureUnit the temperatureUnit to set
   */
  public void setTemperatureUnit(String temperatureUnit) {
    this.temperatureUnit = temperatureUnit;
  }

  /**
   * Sets the global NeqSim unit system.
   *
   * @param unitSystem the unit system to activate. Options: "default", "field", "SI".
   */
  public static void setNeqSimUnits(String unitSystem) {
    if (unitSystem == null || unitSystem.equalsIgnoreCase("default")) {
      Units.activateDefaultUnits();
    } else if (unitSystem.equalsIgnoreCase("field")) {
      Units.activateFieldUnits();
    } else if (unitSystem.equalsIgnoreCase("SI")) {
      Units.activateSIUnits();
    } else {
      System.err.println("Warning: Unknown unit system '" + unitSystem + "'. Using default units.");
      Units.activateDefaultUnits();
    }
  }
}
