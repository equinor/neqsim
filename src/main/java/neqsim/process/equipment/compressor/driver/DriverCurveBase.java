package neqsim.process.equipment.compressor.driver;

import java.io.Serializable;

/**
 * Abstract base class for driver curve implementations.
 *
 * <p>
 * Provides common functionality for driver power, torque, and efficiency calculations. Subclasses
 * implement driver-specific behavior.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public abstract class DriverCurveBase implements DriverCurve, Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Rated power in kW. */
  protected double ratedPower;

  /** Rated speed in RPM. */
  protected double ratedSpeed;

  /** Minimum speed in RPM. */
  protected double minSpeed;

  /** Maximum speed in RPM. */
  protected double maxSpeed;

  /** Design efficiency at rated conditions. */
  protected double designEfficiency;

  /** Ambient temperature in Celsius. */
  protected double ambientTemperature = 15.0; // ISO standard

  /** Altitude in meters above sea level. */
  protected double altitude = 0.0;

  /**
   * Default constructor.
   */
  protected DriverCurveBase() {}

  /**
   * Constructor with basic parameters.
   *
   * @param ratedPower rated power in kW
   * @param ratedSpeed rated speed in RPM
   * @param designEfficiency design efficiency (0-1)
   */
  protected DriverCurveBase(double ratedPower, double ratedSpeed, double designEfficiency) {
    this.ratedPower = ratedPower;
    this.ratedSpeed = ratedSpeed;
    this.designEfficiency = designEfficiency;
    this.minSpeed = 0.0;
    this.maxSpeed = ratedSpeed * 1.05; // 5% overspeed typically allowed
  }

  /** {@inheritDoc} */
  @Override
  public double getRatedPower() {
    return ratedPower;
  }

  /** {@inheritDoc} */
  @Override
  public double getRatedSpeed() {
    return ratedSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public double getMinSpeed() {
    return minSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxSpeed() {
    return maxSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailableTorque(double speed) {
    if (speed <= 0) {
      return 0.0;
    }
    double power = getAvailablePower(speed);
    // Torque (Nm) = Power (W) / omega (rad/s) = Power (kW) * 1000 / (2 * PI * speed / 60)
    return power * 1000.0 * 60.0 / (2.0 * Math.PI * speed);
  }

  /** {@inheritDoc} */
  @Override
  public boolean canSupplyPower(double requiredPower, double speed) {
    if (speed < minSpeed || speed > maxSpeed) {
      return false;
    }
    return getAvailablePower(speed) >= requiredPower;
  }

  /** {@inheritDoc} */
  @Override
  public void setAmbientTemperature(double temperatureCelsius) {
    this.ambientTemperature = temperatureCelsius;
  }

  /** {@inheritDoc} */
  @Override
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setAltitude(double altitudeMeters) {
    this.altitude = altitudeMeters;
  }

  /** {@inheritDoc} */
  @Override
  public double getAltitude() {
    return altitude;
  }

  /**
   * Sets the rated power.
   *
   * @param ratedPower rated power in kW
   */
  public void setRatedPower(double ratedPower) {
    this.ratedPower = ratedPower;
  }

  /**
   * Sets the rated speed.
   *
   * @param ratedSpeed rated speed in RPM
   */
  public void setRatedSpeed(double ratedSpeed) {
    this.ratedSpeed = ratedSpeed;
  }

  /**
   * Sets the minimum speed.
   *
   * @param minSpeed minimum speed in RPM
   */
  public void setMinSpeed(double minSpeed) {
    this.minSpeed = minSpeed;
  }

  /**
   * Sets the maximum speed.
   *
   * @param maxSpeed maximum speed in RPM
   */
  public void setMaxSpeed(double maxSpeed) {
    this.maxSpeed = maxSpeed;
  }

  /**
   * Gets the design efficiency.
   *
   * @return design efficiency (0-1)
   */
  public double getDesignEfficiency() {
    return designEfficiency;
  }

  /**
   * Sets the design efficiency.
   *
   * @param designEfficiency design efficiency (0-1)
   */
  public void setDesignEfficiency(double designEfficiency) {
    this.designEfficiency = designEfficiency;
  }
}
