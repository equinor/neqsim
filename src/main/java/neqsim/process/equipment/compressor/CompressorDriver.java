package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * Models the driver (motor, turbine, engine) for a compressor.
 *
 * <p>
 * The driver model includes power limits, efficiency curves, and dynamic response characteristics
 * that affect compressor operation during transient simulations.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class CompressorDriver implements Serializable {
  private static final long serialVersionUID = 1L;

  private DriverType driverType = DriverType.ELECTRIC_MOTOR;
  private double ratedPower = 1000.0; // kW
  private double maxPower = 1100.0; // kW (typically 110% of rated)
  private double minPower = 0.0; // kW
  private double driverEfficiency = 0.95;
  private double responseTime = 1.0; // seconds for speed change response
  private double inertia = 10.0; // kg⋅m² (combined driver + compressor)
  private double maxAcceleration = 100.0; // RPM/s
  private double maxDeceleration = 200.0; // RPM/s
  private double ambientTemperature = 288.15; // K (affects gas turbine performance)
  private double ambientPressure = 1.01325; // bara (affects gas turbine performance)
  private boolean overloadProtectionEnabled = true;
  private double overloadTripDelay = 5.0; // seconds before trip on overload
  private double currentOverloadTime = 0.0;

  // Speed limits
  private double minSpeed = 0.0; // RPM
  private double maxSpeed = 10000.0; // RPM
  private double ratedSpeed = 5000.0; // RPM

  // For VFD motors - efficiency vs speed curve coefficients
  private double[] vfdEfficiencyCoeffs = {0.90, 0.05, -0.02}; // η = a + b*(N/Nrated) +
                                                              // c*(N/Nrated)²

  // For gas turbines - ambient temperature derating
  private double isoTemperature = 288.15; // K (ISO conditions)
  private double temperatureDerateFactor = 0.005; // power reduction per K above ISO

  /**
   * Default constructor.
   */
  public CompressorDriver() {}

  /**
   * Constructor with driver type.
   *
   * @param type the driver type
   */
  public CompressorDriver(DriverType type) {
    this.driverType = type;
    this.driverEfficiency = type.getTypicalEfficiency();
    this.responseTime = type.getTypicalResponseTime();
  }

  /**
   * Constructor with driver type and rated power.
   *
   * @param type the driver type
   * @param ratedPower rated power in kW
   */
  public CompressorDriver(DriverType type, double ratedPower) {
    this(type);
    this.ratedPower = ratedPower;
    this.maxPower = ratedPower * 1.1; // 10% overload margin
  }

  /**
   * Get the available power at current conditions.
   *
   * <p>
   * For gas turbines, this accounts for ambient temperature derating. For electric motors, this
   * returns rated power.
   * </p>
   *
   * @return available power in kW
   */
  public double getAvailablePower() {
    if (driverType == DriverType.GAS_TURBINE) {
      double tempDelta = ambientTemperature - isoTemperature;
      double derateFactor = 1.0 - Math.max(0, tempDelta * temperatureDerateFactor);
      return ratedPower * derateFactor;
    }
    return ratedPower;
  }

  /**
   * Get the maximum available power at current conditions.
   *
   * @return maximum power in kW
   */
  public double getMaxAvailablePower() {
    if (driverType == DriverType.GAS_TURBINE) {
      double tempDelta = ambientTemperature - isoTemperature;
      double derateFactor = 1.0 - Math.max(0, tempDelta * temperatureDerateFactor);
      return maxPower * derateFactor;
    }
    return maxPower;
  }

  /**
   * Calculate driver efficiency at given speed.
   *
   * @param speed current speed in RPM
   * @return efficiency as ratio (0-1)
   */
  public double getEfficiencyAtSpeed(double speed) {
    if (driverType == DriverType.VFD_MOTOR && ratedSpeed > 0) {
      double speedRatio = speed / ratedSpeed;
      return vfdEfficiencyCoeffs[0] + vfdEfficiencyCoeffs[1] * speedRatio
          + vfdEfficiencyCoeffs[2] * speedRatio * speedRatio;
    }
    return driverEfficiency;
  }

  /**
   * Calculate required driver power for given compressor shaft power.
   *
   * @param shaftPower compressor shaft power in kW
   * @param speed current speed in RPM
   * @return required driver power in kW
   */
  public double getRequiredDriverPower(double shaftPower, double speed) {
    double efficiency = getEfficiencyAtSpeed(speed);
    if (efficiency <= 0) {
      efficiency = 0.01; // Prevent division by zero
    }
    return shaftPower / efficiency;
  }

  /**
   * Check if the driver can deliver the required power.
   *
   * @param requiredPower required power in kW
   * @return true if power can be delivered
   */
  public boolean canDeliverPower(double requiredPower) {
    return requiredPower <= getMaxAvailablePower();
  }

  /**
   * Get the power margin (remaining power capacity).
   *
   * @param currentPower current power demand in kW
   * @return power margin in kW
   */
  public double getPowerMargin(double currentPower) {
    return getMaxAvailablePower() - currentPower;
  }

  /**
   * Get the power margin as a ratio.
   *
   * @param currentPower current power demand in kW
   * @return power margin ratio (available/max)
   */
  public double getPowerMarginRatio(double currentPower) {
    double maxPwr = getMaxAvailablePower();
    if (maxPwr <= 0) {
      return 0.0;
    }
    return (maxPwr - currentPower) / maxPwr;
  }

  /**
   * Calculate the maximum acceleration at current speed considering torque limits.
   *
   * @param currentSpeed current speed in RPM
   * @param currentPower current power in kW
   * @return maximum acceleration in RPM/s
   */
  public double getMaxAccelerationAtConditions(double currentSpeed, double currentPower) {
    double powerMargin = getPowerMargin(currentPower);
    if (powerMargin <= 0 || currentSpeed <= 0) {
      return 0.0;
    }

    // Power margin limits torque available for acceleration
    // P = τω → τ = P/ω, and τ = I⋅α → α = τ/I = P/(I⋅ω)
    double omega = currentSpeed * Math.PI / 30.0; // Convert RPM to rad/s
    double torqueAvailable = powerMargin * 1000.0 / omega; // N⋅m
    double alphaMax = torqueAvailable / inertia; // rad/s²
    double rpmPerSecMax = alphaMax * 30.0 / Math.PI; // RPM/s

    return Math.min(rpmPerSecMax, maxAcceleration);
  }

  /**
   * Calculate speed change over a time step.
   *
   * @param currentSpeed current speed in RPM
   * @param targetSpeed target speed in RPM
   * @param currentPower current power demand in kW
   * @param timeStep time step in seconds
   * @return new speed in RPM
   */
  public double calculateSpeedChange(double currentSpeed, double targetSpeed, double currentPower,
      double timeStep) {
    double speedDiff = targetSpeed - currentSpeed;

    if (Math.abs(speedDiff) < 0.1) {
      return targetSpeed; // Close enough
    }

    double maxChange;
    if (speedDiff > 0) {
      // Accelerating
      maxChange = getMaxAccelerationAtConditions(currentSpeed, currentPower) * timeStep;
    } else {
      // Decelerating
      maxChange = -maxDeceleration * timeStep;
    }

    double actualChange;
    if (speedDiff > 0) {
      actualChange = Math.min(speedDiff, maxChange);
    } else {
      actualChange = Math.max(speedDiff, maxChange);
    }

    double newSpeed = currentSpeed + actualChange;

    // Apply speed limits
    if (newSpeed < minSpeed) {
      newSpeed = minSpeed;
    }
    if (newSpeed > maxSpeed) {
      newSpeed = maxSpeed;
    }

    return newSpeed;
  }

  /**
   * Update overload tracking and check for trip.
   *
   * @param currentPower current power in kW
   * @param timeStep time step in seconds
   * @return true if driver should trip due to overload
   */
  public boolean checkOverloadTrip(double currentPower, double timeStep) {
    if (!overloadProtectionEnabled) {
      return false;
    }

    if (currentPower > getMaxAvailablePower()) {
      currentOverloadTime += timeStep;
      if (currentOverloadTime >= overloadTripDelay) {
        return true;
      }
    } else {
      currentOverloadTime = 0.0;
    }

    return false;
  }

  /**
   * Reset the overload timer.
   */
  public void resetOverloadTimer() {
    currentOverloadTime = 0.0;
  }

  // Getters and setters

  /**
   * Get the driver type.
   *
   * @return driver type
   */
  public DriverType getDriverType() {
    return driverType;
  }

  /**
   * Set the driver type.
   *
   * @param driverType the driver type
   */
  public void setDriverType(DriverType driverType) {
    this.driverType = driverType;
    this.driverEfficiency = driverType.getTypicalEfficiency();
    this.responseTime = driverType.getTypicalResponseTime();
  }

  /**
   * Get the rated power.
   *
   * @return rated power in kW
   */
  public double getRatedPower() {
    return ratedPower;
  }

  /**
   * Set the rated power.
   *
   * @param ratedPower rated power in kW
   */
  public void setRatedPower(double ratedPower) {
    this.ratedPower = ratedPower;
  }

  /**
   * Get the maximum power.
   *
   * @return maximum power in kW
   */
  public double getMaxPower() {
    return maxPower;
  }

  /**
   * Set the maximum power.
   *
   * @param maxPower maximum power in kW
   */
  public void setMaxPower(double maxPower) {
    this.maxPower = maxPower;
  }

  /**
   * Get the minimum power.
   *
   * @return minimum power in kW
   */
  public double getMinPower() {
    return minPower;
  }

  /**
   * Set the minimum power.
   *
   * @param minPower minimum power in kW
   */
  public void setMinPower(double minPower) {
    this.minPower = minPower;
  }

  /**
   * Get the driver efficiency.
   *
   * @return efficiency as ratio (0-1)
   */
  public double getDriverEfficiency() {
    return driverEfficiency;
  }

  /**
   * Set the driver efficiency.
   *
   * @param driverEfficiency efficiency as ratio (0-1)
   */
  public void setDriverEfficiency(double driverEfficiency) {
    this.driverEfficiency = driverEfficiency;
  }

  /**
   * Get the response time.
   *
   * @return response time in seconds
   */
  public double getResponseTime() {
    return responseTime;
  }

  /**
   * Set the response time.
   *
   * @param responseTime response time in seconds
   */
  public void setResponseTime(double responseTime) {
    this.responseTime = responseTime;
  }

  /**
   * Get the combined inertia.
   *
   * @return inertia in kg⋅m²
   */
  public double getInertia() {
    return inertia;
  }

  /**
   * Set the combined inertia.
   *
   * @param inertia inertia in kg⋅m²
   */
  public void setInertia(double inertia) {
    this.inertia = inertia;
  }

  /**
   * Get the maximum acceleration.
   *
   * @return max acceleration in RPM/s
   */
  public double getMaxAcceleration() {
    return maxAcceleration;
  }

  /**
   * Set the maximum acceleration.
   *
   * @param maxAcceleration max acceleration in RPM/s
   */
  public void setMaxAcceleration(double maxAcceleration) {
    this.maxAcceleration = maxAcceleration;
  }

  /**
   * Get the maximum deceleration.
   *
   * @return max deceleration in RPM/s
   */
  public double getMaxDeceleration() {
    return maxDeceleration;
  }

  /**
   * Set the maximum deceleration.
   *
   * @param maxDeceleration max deceleration in RPM/s
   */
  public void setMaxDeceleration(double maxDeceleration) {
    this.maxDeceleration = maxDeceleration;
  }

  /**
   * Get the ambient temperature.
   *
   * @return temperature in K
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Set the ambient temperature.
   *
   * @param ambientTemperature temperature in K
   */
  public void setAmbientTemperature(double ambientTemperature) {
    this.ambientTemperature = ambientTemperature;
  }

  /**
   * Get the ambient pressure.
   *
   * @return pressure in bara
   */
  public double getAmbientPressure() {
    return ambientPressure;
  }

  /**
   * Set the ambient pressure.
   *
   * @param ambientPressure pressure in bara
   */
  public void setAmbientPressure(double ambientPressure) {
    this.ambientPressure = ambientPressure;
  }

  /**
   * Check if overload protection is enabled.
   *
   * @return true if enabled
   */
  public boolean isOverloadProtectionEnabled() {
    return overloadProtectionEnabled;
  }

  /**
   * Set overload protection enabled.
   *
   * @param enabled true to enable
   */
  public void setOverloadProtectionEnabled(boolean enabled) {
    this.overloadProtectionEnabled = enabled;
  }

  /**
   * Get the overload trip delay.
   *
   * @return delay in seconds
   */
  public double getOverloadTripDelay() {
    return overloadTripDelay;
  }

  /**
   * Set the overload trip delay.
   *
   * @param delay delay in seconds
   */
  public void setOverloadTripDelay(double delay) {
    this.overloadTripDelay = delay;
  }

  /**
   * Get the minimum speed.
   *
   * @return minimum speed in RPM
   */
  public double getMinSpeed() {
    return minSpeed;
  }

  /**
   * Set the minimum speed.
   *
   * @param minSpeed minimum speed in RPM
   */
  public void setMinSpeed(double minSpeed) {
    this.minSpeed = minSpeed;
  }

  /**
   * Get the maximum speed.
   *
   * @return maximum speed in RPM
   */
  public double getMaxSpeed() {
    return maxSpeed;
  }

  /**
   * Set the maximum speed.
   *
   * @param maxSpeed maximum speed in RPM
   */
  public void setMaxSpeed(double maxSpeed) {
    this.maxSpeed = maxSpeed;
  }

  /**
   * Get the rated speed.
   *
   * @return rated speed in RPM
   */
  public double getRatedSpeed() {
    return ratedSpeed;
  }

  /**
   * Set the rated speed.
   *
   * @param ratedSpeed rated speed in RPM
   */
  public void setRatedSpeed(double ratedSpeed) {
    this.ratedSpeed = ratedSpeed;
  }

  /**
   * Set VFD efficiency curve coefficients.
   *
   * @param a constant term
   * @param b linear term coefficient
   * @param c quadratic term coefficient
   */
  public void setVfdEfficiencyCoefficients(double a, double b, double c) {
    this.vfdEfficiencyCoeffs = new double[] {a, b, c};
  }

  /**
   * Set gas turbine temperature derate factor.
   *
   * @param factor power reduction per K above ISO (typically 0.003-0.007)
   */
  public void setTemperatureDerateFactor(double factor) {
    this.temperatureDerateFactor = factor;
  }

  @Override
  public String toString() {
    return String.format(
        "CompressorDriver[%s, rated=%.0f kW, max=%.0f kW, η=%.1f%%, response=%.1f s]",
        driverType.getDisplayName(), ratedPower, maxPower, driverEfficiency * 100, responseTime);
  }
}
