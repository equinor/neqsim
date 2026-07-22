package neqsim.process.equipment.heatexchanger;

import neqsim.process.SimulationInterface;

/**
 * HeaterInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface HeaterInterface extends SimulationInterface {
  /**
   * setdT.
   *
   * @param dT a double
   */
  public void setdT(double dT);

  /**
   * Set the outlet temperature and pressure of the heater.
   *
   * @param temperature Temperature in Kelvin
   * @param pressure Pressure in bara
   */
  public void setOutTP(double temperature, double pressure);

  /**
   * Set the outlet temperature of the heater in a specified unit.
   *
   * @param temperature Outlet temperature.
   * @param unit a {@link java.lang.String} object
   */
  public void setOutletTemperature(double temperature, String unit);

  /**
   * Set the outlet pressure of the heater in a specified unit.
   *
   * @param pressure Outlet pressure.
   * @param unit a {@link java.lang.String} object
   */
  public void setOutletPressure(double pressure, String unit);
}
