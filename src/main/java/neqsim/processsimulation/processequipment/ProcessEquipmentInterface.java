package neqsim.processsimulation.processequipment;

import neqsim.processsimulation.SimulationInterface;
import neqsim.processsimulation.controllerdevice.ControllerDeviceInterface;
import neqsim.processsimulation.mechanicaldesign.MechanicalDesign;
import neqsim.thermo.system.SystemInterface;

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
   * @return a {@link neqsim.processsimulation.mechanicaldesign.MechanicalDesign} object
   */
  public MechanicalDesign getMechanicalDesign();

  /**
   * <p>
   * Check if process module needs recalculating.
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
   * @param controller a {@link neqsim.processsimulation.controllerdevice.ControllerDeviceInterface}
   *        object
   */
  public void setController(ControllerDeviceInterface controller);

  /**
   * <p>
   * getController.
   * </p>
   *
   * @return a {@link neqsim.processsimulation.controllerdevice.ControllerDeviceInterface} object
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
   * getMassBalance.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getMassBalance(String unit);

  /**
   * <p>
   * Getter for the field <code>pressure</code>.
   * </p>
   *
   * @return a double
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
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   */
  public void setPressure(double pressure);

  /**
   * <p>
   * runConditionAnalysis.
   * </p>
   *
   * @param refExchanger a
   *        {@link neqsim.processsimulation.processequipment.ProcessEquipmentInterface} object
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
   * method to return entropy production of the unit operation.
   *
   * @param unit Supported units are J/K and kJ/K
   * @return entropy in specified unit
   */
  public double getEntropyProduction(String unit);

  /**
   * Get exergy change production of the unit operation.
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
   * toJson.
   * </p>
   *
   * @return a String
   */
  public String toJson();

  /**
   * <p>
   * getReport_json
   * </p>
   * Return results of simulation in json format
   *
   * @return a String
   */
  @Override
  public String getReport_json();
}
