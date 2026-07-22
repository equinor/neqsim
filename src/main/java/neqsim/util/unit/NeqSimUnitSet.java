package neqsim.util.unit;

/**
 * NeqSimUnitSet class.
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
   * Getter for the field <code>componentConcentrationUnit</code>.
   *
   * @return the componentConcentrationUnit
   */
  public String getComponentConcentrationUnit() {
    return componentConcentrationUnit;
  }

  /**
   * Getter for the field <code>flowRateUnit</code>.
   *
   * @return the flowRateUnit
   */
  public String getFlowRateUnit() {
    return flowRateUnit;
  }

  /**
   * Getter for the field <code>pressureUnit</code>.
   *
   * @return the pressureUnit
   */
  public String getPressureUnit() {
    return pressureUnit;
  }

  /**
   * Getter for the field <code>temperatureUnit</code>.
   *
   * @return the temperatureUnit
   */
  public String getTemperatureUnit() {
    return temperatureUnit;
  }

  /**
   * Setter for the field <code>componentConcentrationUnit</code>.
   *
   * @param componentConcentrationUnit the componentConcentrationUnit to set
   */
  public void setComponentConcentrationUnit(String componentConcentrationUnit) {
    this.componentConcentrationUnit = componentConcentrationUnit;
  }

  /**
   * Setter for the field <code>flowRateUnit</code>.
   *
   * @param flowRateUnit the flowRateUnit to set
   */
  public void setFlowRateUnit(String flowRateUnit) {
    this.flowRateUnit = flowRateUnit;
  }

  /**
   * Setter for the field <code>pressureUnit</code>.
   *
   * @param pressureUnit the pressureUnit to set
   */
  public void setPressureUnit(String pressureUnit) {
    this.pressureUnit = pressureUnit;
  }

  /**
   * Setter for the field <code>temperatureUnit</code>.
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
