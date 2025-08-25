package neqsim.process.equipment;

import neqsim.process.SimulationInterface;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * <p>
 * ProcessEquipmentInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ProcessEquipmentInterface extends SimulationInterface {
  /**
   * <p>
   * reportResults.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] reportResults();

  /**
   * <p>
   * Initialize a <code>initMechanicalDesign</code> for the equipment.
   * </p>
   */
  default void initMechanicalDesign() {}

  /**
   * <p>
   * Get a <code>mechanicalDesign</code> for the equipment.
   * </p>
   *
   * @return a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public MechanicalDesign getMechanicalDesign();

  /**
   * <p>
   * Check if process equipment needs recalculating.
   * </p>
   *
   * @return true or false
   */
  public default boolean needRecalculation() {
    return true;
  }

  /**
   * <p>
   * getSpecification.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getSpecification();

  /**
   * <p>
   * setSpecification.
   * </p>
   *
   * @param specification a {@link java.lang.String} object
   */
  public void setSpecification(String specification);

  /**
   * <p>
   * displayResult.
   * </p>
   */
  public void displayResult();

  /**
   * <p>
   * setRegulatorOutSignal.
   * </p>
   *
   * @param signal a double
   */
  public void setRegulatorOutSignal(double signal);

  /**
   * <p>
   * setController.
   * </p>
   *
   * @param controller a {@link neqsim.process.controllerdevice.ControllerDeviceInterface} object
   */
  public void setController(ControllerDeviceInterface controller);

  /**
   * <p>
   * getController.
   * </p>
   *
   * @return a {@link neqsim.process.controllerdevice.ControllerDeviceInterface} object
   */
  public ControllerDeviceInterface getController();

  /**
   * <p>
   * getFluid.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public default SystemInterface getFluid() {
    return getThermoSystem();
  }

  /**
   * <p>
   * getThermoSystem.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getThermoSystem();

  /**
   * <p>
   * Retrieves the mass balance of the process equipment in specified unit.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return The mass balance of the process equipment.
   */
  public double getMassBalance(String unit);

  /**
   * <p>
   * Getter for the field <code>pressure</code>.
   * </p>
   *
   * @return Pressure in bara
   */
  public double getPressure();

  /**
   * <p>
   * Getter for the field <code>pressure</code> converted to specified unit.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPressure(String unit);

  /**
   * <p>
   * Getter for the field <code>temperature</code> converted to specified unit.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getTemperature(String unit);

  /**
   * <p>
   * Getter for the field <code>temperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getTemperature();

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   */
  public void setPressure(double pressure);

  /**
   * <p>
   * Setter for the field <code>temperature</code>.
   * </p>
   *
   * @param temperature Temperature in Kelvin
   */
  public void setTemperature(double temperature);

  /**
   * <p>
   * runConditionAnalysis.
   * </p>
   *
   * @param refExchanger a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void runConditionAnalysis(ProcessEquipmentInterface refExchanger);

  /**
   * <p>
   * getConditionAnalysisMessage.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getConditionAnalysisMessage();

  /**
   * Get entropy production of the process equipment.
   *
   * @param unit Supported units are J/K and kJ/K
   * @return entropy in specified unit
   */
  public double getEntropyProduction(String unit);

  /**
   * Get exergy change production of the process equipment.
   *
   * @param unit Supported units are J and kJ
   * @param surroundingTemperature The surrounding temperature in Kelvin
   * @return change in exergy in specified unit
   */
  public double getExergyChange(String unit, double surroundingTemperature);

  /**
   * <p>
   * getResultTable.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable();

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();

  /**
   * <p>
   * Serializes the Process Equipment along with its state to a JSON string.
   * </p>
   *
   * @return json string.
   */
  public String toJson();

  /**
   * Serializes the Process Equipment with configurable level of detail.
   *
   * @param cfg report configuration
   * @return json string
   */
  public default String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    return toJson();
  }

  /** {@inheritDoc} */
  @Override
  public String getReport_json();
}
