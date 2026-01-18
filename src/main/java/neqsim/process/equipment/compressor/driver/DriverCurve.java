package neqsim.process.equipment.compressor.driver;

/**
 * Interface for compressor and pump driver performance curves.
 *
 * <p>
 * This interface defines the contract for modeling driver characteristics including power
 * availability, torque limits, and efficiency at various operating conditions. Driver curves are
 * used to determine if a compressor or pump can achieve the required duty within the mechanical
 * constraints of its driver.
 * </p>
 *
 * <h3>Supported Driver Types</h3>
 * <ul>
 * <li>Gas Turbines - Power varies with ambient temperature, altitude, and speed</li>
 * <li>Electric Motors - Constant torque (VFD) or fixed speed characteristics</li>
 * <li>Steam Turbines - Power varies with steam conditions and extraction</li>
 * </ul>
 *
 * <h3>Example Usage</h3>
 * 
 * <pre>
 * DriverCurve driver = new GasTurbineDriver(10000, 0.35); // 10 MW, 35% efficiency
 * driver.setAmbientTemperature(35.0); // 35°C ambient
 * 
 * double availablePower = driver.getAvailablePower(8000); // at 8000 RPM
 * double efficiency = driver.getEfficiency(8000, 0.8); // at 80% load
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public interface DriverCurve {

  /**
   * Gets the type of driver.
   *
   * @return driver type string
   */
  String getDriverType();

  /**
   * Gets the rated (nameplate) power of the driver.
   *
   * @return rated power in kW
   */
  double getRatedPower();

  /**
   * Gets the rated speed of the driver.
   *
   * @return rated speed in RPM
   */
  double getRatedSpeed();

  /**
   * Gets the available power at the specified speed.
   *
   * <p>
   * For gas turbines, this accounts for ambient temperature derating. For electric motors with VFD,
   * this may be constant torque (power proportional to speed) or constant power.
   * </p>
   *
   * @param speed operating speed in RPM
   * @return available power in kW
   */
  double getAvailablePower(double speed);

  /**
   * Gets the available torque at the specified speed.
   *
   * @param speed operating speed in RPM
   * @return available torque in Nm
   */
  double getAvailableTorque(double speed);

  /**
   * Gets the driver efficiency at the specified speed and load.
   *
   * @param speed operating speed in RPM
   * @param loadFraction load as fraction of available power (0-1)
   * @return efficiency as fraction (0-1)
   */
  double getEfficiency(double speed, double loadFraction);

  /**
   * Gets the minimum operating speed.
   *
   * @return minimum speed in RPM
   */
  double getMinSpeed();

  /**
   * Gets the maximum operating speed.
   *
   * @return maximum speed in RPM
   */
  double getMaxSpeed();

  /**
   * Checks if the driver can provide the required power at the specified speed.
   *
   * @param requiredPower required power in kW
   * @param speed operating speed in RPM
   * @return true if driver can provide the power
   */
  boolean canSupplyPower(double requiredPower, double speed);

  /**
   * Gets the fuel or energy consumption for the given power output.
   *
   * <p>
   * For gas turbines, returns fuel gas consumption in kg/hr or kW. For electric motors, returns
   * electrical power input in kW.
   * </p>
   *
   * @param powerOutput power output in kW
   * @param speed operating speed in RPM
   * @return fuel or energy consumption
   */
  double getFuelConsumption(double powerOutput, double speed);

  /**
   * Sets the ambient temperature for gas turbine derating.
   *
   * @param temperatureCelsius ambient temperature in Celsius
   */
  void setAmbientTemperature(double temperatureCelsius);

  /**
   * Gets the ambient temperature.
   *
   * @return ambient temperature in Celsius
   */
  double getAmbientTemperature();

  /**
   * Sets the altitude for gas turbine derating.
   *
   * @param altitudeMeters altitude in meters above sea level
   */
  void setAltitude(double altitudeMeters);

  /**
   * Gets the altitude.
   *
   * @return altitude in meters
   */
  double getAltitude();

  /**
   * Gets the power derating factor due to ambient conditions.
   *
   * <p>
   * Returns a factor between 0 and 1 that is applied to the rated power to get the available power
   * at current ambient conditions.
   * </p>
   *
   * @return derating factor (0-1)
   */
  double getAmbientDeratingFactor();

  /**
   * Gets the current power margin.
   *
   * <p>
   * Returns the difference between available power and current load as a fraction of available
   * power.
   * </p>
   *
   * @param currentLoad current power load in kW
   * @param speed operating speed in RPM
   * @return power margin as fraction (positive = headroom available)
   */
  default double getPowerMargin(double currentLoad, double speed) {
    double available = getAvailablePower(speed);
    if (available <= 0) {
      return -1.0;
    }
    return (available - currentLoad) / available;
  }
}
