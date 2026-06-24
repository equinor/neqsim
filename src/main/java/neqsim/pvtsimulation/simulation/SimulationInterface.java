package neqsim.pvtsimulation.simulation;

import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;

/**
 * SimulationInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SimulationInterface {
  /**
   * getThermoSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getThermoSystem();

  /**
   * setThermoSystem.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermoSystem(SystemInterface thermoSystem);

  /**
   * getBaseThermoSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getBaseThermoSystem();

  /**
   * run.
   */
  public void run();

  /**
   * getOptimizer.
   *
   * @return a {@link neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt} object
   */
  public LevenbergMarquardt getOptimizer();

  /**
   * Setter for the field <code>temperature</code>.
   *
   * @param temperature the temperature to set
   * @param temperatureUnit the unit of temperature as string
   */
  public void setTemperature(double temperature, String temperatureUnit);
}
