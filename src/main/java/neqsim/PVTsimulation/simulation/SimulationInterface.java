package neqsim.PVTsimulation.simulation;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
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
   *         {@link neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt}
   *         object
   */
  public LevenbergMarquardt getOptimizer();
}
