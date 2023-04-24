package neqsim.processSimulation.processEquipment;

import neqsim.processSimulation.SimulationInterface;
import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
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
   * Get a <code>mechanicalDesign</code> for the equipment.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
   */
  public MechanicalDesign getMechanicalDesign();

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
   * @param controller a {@link neqsim.processSimulation.controllerDevice.ControllerDeviceInterface}
   *        object
   */
  public void setController(ControllerDeviceInterface controller);

  /**
   * <p>
   * getController.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.controllerDevice.ControllerDeviceInterface} object
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
   * getPressure.
   * </p>
   *
   * @return a double
   */
  public double getPressure();

  /**
   * <p>
   * getPressure.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPressure(String unit);

  /**
   * <p>
   * setPressure.
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
   *        {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} object
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
}
