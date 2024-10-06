/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */

package neqsim.processsimulation.processequipment.separator;

import neqsim.processsimulation.SimulationInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SeparatorInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SeparatorInterface extends SimulationInterface {
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
   * setInternalDiameter.
   * </p>
   *
   * @param diam a double
   */
  public void setInternalDiameter(double diam);

  /**
   * <p>
   * Setter for the field <code>liquidLevel</code>.
   * </p>
   *
   * @param liquidlev a double
   */
  public void setLiquidLevel(double liquidlev);
}
