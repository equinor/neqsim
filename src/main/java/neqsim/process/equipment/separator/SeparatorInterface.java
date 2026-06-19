/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */

package neqsim.process.equipment.separator;

import neqsim.process.SimulationInterface;
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

  /**
   * Set heat input to the separator (e.g., from flare radiation, external heating).
   *
   * @param heatInput heat duty in watts
   */
  public void setHeatInput(double heatInput);

  /**
   * Set heat input to the separator with specified unit.
   *
   * @param heatInput heat duty value
   * @param unit heat duty unit (W, kW, MW, J/s, etc.)
   */
  public void setHeatInput(double heatInput, String unit);

  /**
   * Get heat input in watts.
   *
   * @return heat input in watts
   */
  public double getHeatInput();

  /**
   * Get heat input in specified unit.
   *
   * @param unit desired unit (W, kW, MW)
   * @return heat input in specified unit
   */
  public double getHeatInput(String unit);

  /**
   * Check if heat input is set.
   *
   * @return true if heat input is explicitly set
   */
  public boolean isSetHeatInput();
}
