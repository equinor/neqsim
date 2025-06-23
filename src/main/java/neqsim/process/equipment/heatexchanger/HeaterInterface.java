package neqsim.process.equipment.heatexchanger;

import neqsim.process.SimulationInterface;

/**
 * <p>
 * HeaterInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface HeaterInterface extends SimulationInterface {
  /**
   * <p>
   * setdT.
   * </p>
   *
   * @param dT a double
   */
  public void setdT(double dT);

  /**
   * <p>
   * Set the outlet temperature and pressure of the heater.
   * </p>
   *
   * @param temperature Temperature in Kelvin
   * @param pressure Pressure in bara
   */
  public void setOutTP(double temperature, double pressure);

  /**
   * <p>
   * Set the outlet temperature of the heater in a specified unit.
   * </p>
   *
   * @param temperature Outlet temperature.
   * @param unit a {@link java.lang.String} object
   */
  public void setOutTemperature(double temperature, String unit);

  /**
   * <p>
   * Set the outlet pressure of the heater in a specified unit.
   * </p>
   *
   * @param pressure Outlet pressure.
   * @param unit a {@link java.lang.String} object
   */
  public void setOutPressure(double pressure, String unit);
}
