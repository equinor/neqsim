package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.Arrays;

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

  // Speed-dependent max power curve coefficients
  // P_max(N) = maxPower * (a + b*(N/N_rated) + c*(N/N_rated)²)
  private double[] maxPowerCurveCoeffs = null; // null means constant max power
  private boolean useMaxPowerCurve = false;

  // Tabular max power curve (discrete data points)
  private double[] maxPowerCurveSpeeds = null; // RPM values
  private double[] maxPowerCurvePowers = null; // kW values (or MW if specified)
  private boolean useMaxPowerCurveTable = false;

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
   * <p>
   * This method returns a constant max power. Use {@link #getMaxAvailablePowerAtSpeed(double)} if a
   * speed-dependent max power curve has been configured.
   * </p>
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
   * Get the maximum available power at a specific speed.
   *
   * <p>
   * If a tabular max power curve has been set using
   * {@link #setMaxPowerSpeedCurve(double[], double[])}, the power will be linearly interpolated
   * from the curve data.
   * </p>
   *
   * <p>
   * Alternatively, if polynomial coefficients have been configured using
   * {@link #setMaxPowerCurveCoefficients(double, double, double)}, the max power will vary with
   * speed according to: P_max(N) = maxPower * (a + b*(N/N_rated) + c*(N/N_rated)²)
   * </p>
   *
   * <p>
   * For gas turbines, ambient temperature derating is also applied.
   * </p>
   *
   * @param speed current speed in RPM
   * @return maximum power in kW at the given speed
   */
  public double getMaxAvailablePowerAtSpeed(double speed) {
    double basePower = maxPower;

    // First priority: use tabular data if available
    if (useMaxPowerCurveTable && maxPowerCurveSpeeds != null && maxPowerCurvePowers != null
        && maxPowerCurveSpeeds.length > 0) {
      basePower = interpolateMaxPower(speed);
    } else if (useMaxPowerCurve && maxPowerCurveCoeffs != null && ratedSpeed > 0) {
      // Second priority: polynomial curve
      double speedRatio = speed / ratedSpeed;
      double curveFactor = maxPowerCurveCoeffs[0] + maxPowerCurveCoeffs[1] * speedRatio
          + maxPowerCurveCoeffs[2] * speedRatio * speedRatio;
      // Ensure factor stays positive and reasonable (0.1 to 1.5)
      curveFactor = Math.max(0.1, Math.min(1.5, curveFactor));
      basePower = maxPower * curveFactor;
    }

    // Apply gas turbine temperature derating if applicable
    if (driverType == DriverType.GAS_TURBINE) {
      double tempDelta = ambientTemperature - isoTemperature;
      double derateFactor = 1.0 - Math.max(0, tempDelta * temperatureDerateFactor);
      basePower = basePower * derateFactor;
    }

    return basePower;
  }

  /**
   * Interpolates max power from the tabular curve data.
   *
   * @param speed current speed in RPM
   * @return interpolated max power in kW
   */
  private double interpolateMaxPower(double speed) {
    int n = maxPowerCurveSpeeds.length;

    // Handle edge cases
    if (speed <= maxPowerCurveSpeeds[0]) {
      return maxPowerCurvePowers[0];
    }
    if (speed >= maxPowerCurveSpeeds[n - 1]) {
      return maxPowerCurvePowers[n - 1];
    }

    // Find the interval containing speed and interpolate
    for (int i = 0; i < n - 1; i++) {
      if (speed >= maxPowerCurveSpeeds[i] && speed <= maxPowerCurveSpeeds[i + 1]) {
        double fraction = (speed - maxPowerCurveSpeeds[i])
            / (maxPowerCurveSpeeds[i + 1] - maxPowerCurveSpeeds[i]);
        return maxPowerCurvePowers[i]
            + fraction * (maxPowerCurvePowers[i + 1] - maxPowerCurvePowers[i]);
      }
    }

    // Fallback (should not reach here)
    return maxPower;
  }

  /**
   * Check if the driver can deliver the required power at a specific speed.
   *
   * @param requiredPower required power in kW
   * @param speed current speed in RPM
   * @return true if power can be delivered at this speed
   */
  public boolean canDeliverPowerAtSpeed(double requiredPower, double speed) {
    return requiredPower <= getMaxAvailablePowerAtSpeed(speed);
  }

  /**
   * Get the power margin (remaining power capacity) at a specific speed.
   *
   * @param currentPower current power demand in kW
   * @param speed current speed in RPM
   * @return power margin in kW
   */
  public double getPowerMarginAtSpeed(double currentPower, double speed) {
    return getMaxAvailablePowerAtSpeed(speed) - currentPower;
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

  /**
   * Set max power curve coefficients for speed-dependent max power.
   *
   * <p>
   * The max power at a given speed is calculated as: P_max(N) = maxPower * (a + b*(N/N_rated) +
   * c*(N/N_rated)²)
   *
   * <p>
   * Example coefficients:
   * </p>
   * <ul>
   * <li>Constant power: a=1.0, b=0.0, c=0.0 (default)</li>
   * <li>Linear increase: a=0.5, b=0.5, c=0.0 (50% at 0 speed, 100% at rated)</li>
   * <li>Typical VFD motor: a=0.0, b=1.0, c=0.0 (power proportional to speed)</li>
   * <li>With torque limit: a=0.0, b=0.8, c=0.2 (slight curve)</li>
   * </ul>
   *
   * @param a constant term (dimensionless)
   * @param b linear term coefficient (dimensionless)
   * @param c quadratic term coefficient (dimensionless)
   */
  public void setMaxPowerCurveCoefficients(double a, double b, double c) {
    this.maxPowerCurveCoeffs = new double[] {a, b, c};
    this.useMaxPowerCurve = true;
  }

  /**
   * Get the max power curve coefficients.
   *
   * @return array of coefficients [a, b, c] or null if not set
   */
  public double[] getMaxPowerCurveCoefficients() {
    if (maxPowerCurveCoeffs != null) {
      return new double[] {maxPowerCurveCoeffs[0], maxPowerCurveCoeffs[1], maxPowerCurveCoeffs[2]};
    }
    return null;
  }

  /**
   * Check if speed-dependent max power curve is enabled.
   *
   * @return true if max power varies with speed
   */
  public boolean isMaxPowerCurveEnabled() {
    return useMaxPowerCurve;
  }

  /**
   * Disable the speed-dependent max power curve.
   *
   * <p>
   * After calling this method, {@link #getMaxAvailablePowerAtSpeed(double)} will return constant
   * max power regardless of speed.
   * </p>
   */
  public void disableMaxPowerCurve() {
    this.useMaxPowerCurve = false;
  }

  /**
   * Enable the speed-dependent max power curve.
   *
   * <p>
   * This method enables the curve if coefficients have been set. Use
   * {@link #setMaxPowerCurveCoefficients(double, double, double)} first.
   * </p>
   */
  public void enableMaxPowerCurve() {
    if (maxPowerCurveCoeffs != null) {
      this.useMaxPowerCurve = true;
    }
  }

  /**
   * Set the max power vs speed curve using tabular data with linear interpolation.
   *
   * <p>
   * This method allows specifying discrete data points for the max power curve. The power at any
   * speed is determined by linear interpolation between the provided points. For speeds outside the
   * data range, the boundary values are used.
   * </p>
   *
   * <p>
   * Example usage for a typical VFD electric motor:
   * </p>
   * 
   * <pre>
   * double[] speeds = {4922, 5500, 6000, 6500, 7000, 7383}; // RPM
   * double[] powers = {21.8, 27.5, 32.0, 37.0, 42.0, 44.4}; // MW
   * driver.setMaxPowerSpeedCurve(speeds, powers, "MW");
   * </pre>
   *
   * @param speeds array of speed values in RPM (must be in ascending order)
   * @param powers array of max power values (same length as speeds)
   * @param powerUnit unit of power values: "kW", "MW", or "W"
   * @throws IllegalArgumentException if arrays have different lengths or speeds not in order
   */
  public void setMaxPowerSpeedCurve(double[] speeds, double[] powers, String powerUnit) {
    if (speeds == null || powers == null) {
      throw new IllegalArgumentException("Speed and power arrays cannot be null");
    }
    if (speeds.length != powers.length) {
      throw new IllegalArgumentException("Speed and power arrays must have the same length");
    }
    if (speeds.length < 2) {
      throw new IllegalArgumentException("At least 2 data points are required");
    }

    // Verify speeds are in ascending order
    for (int i = 1; i < speeds.length; i++) {
      if (speeds[i] <= speeds[i - 1]) {
        throw new IllegalArgumentException("Speed values must be in strictly ascending order");
      }
    }

    // Convert power to kW based on unit
    double conversionFactor = 1.0;
    if ("MW".equalsIgnoreCase(powerUnit)) {
      conversionFactor = 1000.0; // MW to kW
    } else if ("W".equalsIgnoreCase(powerUnit)) {
      conversionFactor = 0.001; // W to kW
    }

    this.maxPowerCurveSpeeds = Arrays.copyOf(speeds, speeds.length);
    this.maxPowerCurvePowers = new double[powers.length];
    for (int i = 0; i < powers.length; i++) {
      this.maxPowerCurvePowers[i] = powers[i] * conversionFactor;
    }

    this.useMaxPowerCurveTable = true;
    this.useMaxPowerCurve = false; // Disable polynomial curve when using tabular

    // Update maxPower to the maximum value in the curve
    double maxVal = 0;
    for (double p : this.maxPowerCurvePowers) {
      maxVal = Math.max(maxVal, p);
    }
    this.maxPower = maxVal;
  }

  /**
   * Check if tabular max power curve is enabled.
   *
   * @return true if using tabular interpolation for max power
   */
  public boolean isMaxPowerCurveTableEnabled() {
    return useMaxPowerCurveTable;
  }

  /**
   * Disable the tabular max power curve.
   */
  public void disableMaxPowerCurveTable() {
    this.useMaxPowerCurveTable = false;
  }

  @Override
  public String toString() {
    return String.format(
        "CompressorDriver[%s, rated=%.0f kW, max=%.0f kW, η=%.1f%%, response=%.1f s]",
        driverType.getDisplayName(), ratedPower, maxPower, driverEfficiency * 100, responseTime);
  }
}
