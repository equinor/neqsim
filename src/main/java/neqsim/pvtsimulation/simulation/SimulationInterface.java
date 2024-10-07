package neqsim.pvtsimulation.simulation;

import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SimulationInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SimulationInterface {
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
   * setThermoSystem.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermoSystem(SystemInterface thermoSystem);

  /**
   * <p>
   * getBaseThermoSystem.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getBaseThermoSystem();

  /**
   * <p>
   * run.
   * </p>
   */
  public void run();

  /**
   * <p>
   * getOptimizer.
   * </p>
   *
   * @return a
   *         {@link neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt}
   *         object
   */
  public LevenbergMarquardt getOptimizer();

  /**
   * <p>
   * Setter for the field <code>temperature</code>.
   * </p>
   *
   * @param temperature the temperature to set
   * @param temperatureUnit the unit of temperature as string
   */
  public void setTemperature(double temperature, String temperatureUnit);
}
