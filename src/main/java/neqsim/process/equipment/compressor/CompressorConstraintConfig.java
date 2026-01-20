package neqsim.process.equipment.compressor;

import java.io.Serializable;
import neqsim.process.equipment.compressor.driver.DriverCurve;

/**
 * Configuration class for compressor operational constraints.
 *
 * <p>
 * This class consolidates all configurable constraint parameters for compressor operation
 * including:
 * </p>
 * <ul>
 * <li>Surge and stonewall margins</li>
 * <li>Speed limits (minimum and maximum)</li>
 * <li>Power limits</li>
 * <li>Temperature limits</li>
 * <li>Anti-surge control settings</li>
 * <li>Driver constraints</li>
 * </ul>
 *
 * <p><strong>Example Usage</strong></p>
 * 
 * <pre>
 * CompressorConstraintConfig config = new CompressorConstraintConfig();
 * config.setMinSurgeMargin(0.10); // 10% minimum surge margin
 * config.setMinStonewallMargin(0.05); // 5% minimum stonewall margin
 * config.setMaxPowerUtilization(0.95); // 95% max power
 * config.setMaxDischargeTemperature(473.15); // 200°C max
 * 
 * compressor.setConstraintConfig(config);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorConstraintConfig implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // Surge and stonewall constraints
  /** Minimum surge margin as fraction of surge flow (0-1). */
  private double minSurgeMargin = 0.10;

  /** Minimum stonewall margin as fraction of stonewall flow (0-1). */
  private double minStonewallMargin = 0.05;

  /** Anti-surge control line margin above surge line (0-1). */
  private double antiSurgeControlMargin = 0.15;

  /** Anti-surge recycle valve maximum opening (0-1). */
  private double maxRecycleValveOpening = 1.0;

  // Speed constraints
  /** Minimum speed as fraction of rated speed. */
  private double minSpeedRatio = 0.7;

  /** Maximum speed as fraction of rated speed. */
  private double maxSpeedRatio = 1.05;

  /** Rated speed in RPM. */
  private double ratedSpeed = 10000.0;

  // Power constraints
  /** Maximum power utilization as fraction of available power (0-1). */
  private double maxPowerUtilization = 0.95;

  /** Maximum power in kW (absolute limit). */
  private double maxPower = Double.MAX_VALUE;

  /** Minimum power in kW (for stable operation). */
  private double minPower = 0.0;

  // Temperature constraints
  /** Maximum discharge temperature in Kelvin. */
  private double maxDischargeTemperature = 473.15; // 200°C

  /** Maximum temperature rise per stage in Kelvin. */
  private double maxTemperatureRisePerStage = 100.0;

  /** Maximum suction temperature in Kelvin. */
  private double maxSuctionTemperature = 373.15; // 100°C

  // Pressure constraints
  /** Maximum discharge pressure in bara. */
  private double maxDischargePressure = Double.MAX_VALUE;

  /** Minimum suction pressure in bara. */
  private double minSuctionPressure = 0.0;

  /** Maximum pressure ratio per stage. */
  private double maxPressureRatioPerStage = 4.0;

  // Driver constraints
  /** Associated driver curve (may be null). */
  private transient DriverCurve driverCurve;

  /** Driver power margin required (0-1). */
  private double driverPowerMargin = 0.05;

  // Mechanical constraints
  /** Maximum shaft torque in Nm. */
  private double maxShaftTorque = Double.MAX_VALUE;

  /** Maximum vibration amplitude in mm/s. */
  private double maxVibration = 4.5; // API 617 limit

  // Control settings
  /** Whether to use anti-surge control. */
  private boolean useAntiSurgeControl = true;

  /** Whether to allow operation near surge (within control margin). */
  private boolean allowNearSurgeOperation = false;

  /** Whether to enforce hard limits (vs soft warnings). */
  private boolean enforceHardLimits = true;

  /**
   * Default constructor with industry-standard defaults.
   */
  public CompressorConstraintConfig() {}

  /**
   * Constructor with surge and stonewall margins.
   *
   * @param surgeMargin minimum surge margin (0-1)
   * @param stonewallMargin minimum stonewall margin (0-1)
   */
  public CompressorConstraintConfig(double surgeMargin, double stonewallMargin) {
    this.minSurgeMargin = surgeMargin;
    this.minStonewallMargin = stonewallMargin;
  }

  /**
   * Creates a config with aggressive (minimal) margins for maximum capacity.
   *
   * @return config with minimal margins
   */
  public static CompressorConstraintConfig createAggressiveConfig() {
    CompressorConstraintConfig config = new CompressorConstraintConfig();
    config.setMinSurgeMargin(0.05);
    config.setMinStonewallMargin(0.03);
    config.setMaxPowerUtilization(0.98);
    config.setAllowNearSurgeOperation(true);
    return config;
  }

  /**
   * Creates a config with conservative margins for safe operation.
   *
   * @return config with conservative margins
   */
  public static CompressorConstraintConfig createConservativeConfig() {
    CompressorConstraintConfig config = new CompressorConstraintConfig();
    config.setMinSurgeMargin(0.15);
    config.setMinStonewallMargin(0.10);
    config.setMaxPowerUtilization(0.90);
    config.setAntiSurgeControlMargin(0.20);
    config.setAllowNearSurgeOperation(false);
    return config;
  }

  /**
   * Creates a config based on API 617 requirements.
   *
   * @return API 617 compliant config
   */
  public static CompressorConstraintConfig createAPI617Config() {
    CompressorConstraintConfig config = new CompressorConstraintConfig();
    config.setMinSurgeMargin(0.10);
    config.setMaxSpeedRatio(1.05); // 5% overspeed
    config.setMaxVibration(4.5); // mm/s peak
    return config;
  }

  // Surge and stonewall getters/setters

  /**
   * Gets the minimum surge margin.
   *
   * @return surge margin as fraction (0-1)
   */
  public double getMinSurgeMargin() {
    return minSurgeMargin;
  }

  /**
   * Sets the minimum surge margin.
   *
   * @param margin surge margin as fraction (0-1)
   */
  public void setMinSurgeMargin(double margin) {
    this.minSurgeMargin = margin;
  }

  /**
   * Gets the minimum stonewall margin.
   *
   * @return stonewall margin as fraction (0-1)
   */
  public double getMinStonewallMargin() {
    return minStonewallMargin;
  }

  /**
   * Sets the minimum stonewall margin.
   *
   * @param margin stonewall margin as fraction (0-1)
   */
  public void setMinStonewallMargin(double margin) {
    this.minStonewallMargin = margin;
  }

  /**
   * Gets the anti-surge control margin.
   *
   * @return control margin as fraction (0-1)
   */
  public double getAntiSurgeControlMargin() {
    return antiSurgeControlMargin;
  }

  /**
   * Sets the anti-surge control margin.
   *
   * @param margin control margin as fraction (0-1)
   */
  public void setAntiSurgeControlMargin(double margin) {
    this.antiSurgeControlMargin = margin;
  }

  /**
   * Gets the maximum recycle valve opening.
   *
   * @return max opening as fraction (0-1)
   */
  public double getMaxRecycleValveOpening() {
    return maxRecycleValveOpening;
  }

  /**
   * Sets the maximum recycle valve opening.
   *
   * @param opening max opening as fraction (0-1)
   */
  public void setMaxRecycleValveOpening(double opening) {
    this.maxRecycleValveOpening = opening;
  }

  // Speed getters/setters

  /**
   * Gets the minimum speed ratio.
   *
   * @return min speed as fraction of rated
   */
  public double getMinSpeedRatio() {
    return minSpeedRatio;
  }

  /**
   * Sets the minimum speed ratio.
   *
   * @param ratio min speed as fraction of rated
   */
  public void setMinSpeedRatio(double ratio) {
    this.minSpeedRatio = ratio;
  }

  /**
   * Gets the maximum speed ratio.
   *
   * @return max speed as fraction of rated
   */
  public double getMaxSpeedRatio() {
    return maxSpeedRatio;
  }

  /**
   * Sets the maximum speed ratio.
   *
   * @param ratio max speed as fraction of rated
   */
  public void setMaxSpeedRatio(double ratio) {
    this.maxSpeedRatio = ratio;
  }

  /**
   * Gets the rated speed.
   *
   * @return rated speed in RPM
   */
  public double getRatedSpeed() {
    return ratedSpeed;
  }

  /**
   * Sets the rated speed.
   *
   * @param speed rated speed in RPM
   */
  public void setRatedSpeed(double speed) {
    this.ratedSpeed = speed;
  }

  /**
   * Gets the minimum speed in RPM.
   *
   * @return minimum speed
   */
  public double getMinSpeed() {
    return ratedSpeed * minSpeedRatio;
  }

  /**
   * Gets the maximum speed in RPM.
   *
   * @return maximum speed
   */
  public double getMaxSpeed() {
    return ratedSpeed * maxSpeedRatio;
  }

  // Power getters/setters

  /**
   * Gets the maximum power utilization.
   *
   * @return max utilization as fraction (0-1)
   */
  public double getMaxPowerUtilization() {
    return maxPowerUtilization;
  }

  /**
   * Sets the maximum power utilization.
   *
   * @param utilization max utilization as fraction (0-1)
   */
  public void setMaxPowerUtilization(double utilization) {
    this.maxPowerUtilization = utilization;
  }

  /**
   * Gets the maximum power.
   *
   * @return max power in kW
   */
  public double getMaxPower() {
    return maxPower;
  }

  /**
   * Sets the maximum power.
   *
   * @param power max power in kW
   */
  public void setMaxPower(double power) {
    this.maxPower = power;
  }

  /**
   * Gets the minimum power.
   *
   * @return min power in kW
   */
  public double getMinPower() {
    return minPower;
  }

  /**
   * Sets the minimum power.
   *
   * @param power min power in kW
   */
  public void setMinPower(double power) {
    this.minPower = power;
  }

  // Temperature getters/setters

  /**
   * Gets the maximum discharge temperature.
   *
   * @return max discharge temp in Kelvin
   */
  public double getMaxDischargeTemperature() {
    return maxDischargeTemperature;
  }

  /**
   * Sets the maximum discharge temperature.
   *
   * @param tempKelvin max discharge temp in Kelvin
   */
  public void setMaxDischargeTemperature(double tempKelvin) {
    this.maxDischargeTemperature = tempKelvin;
  }

  /**
   * Sets the maximum discharge temperature in Celsius.
   *
   * @param tempCelsius max discharge temp in Celsius
   */
  public void setMaxDischargeTemperatureCelsius(double tempCelsius) {
    this.maxDischargeTemperature = tempCelsius + 273.15;
  }

  /**
   * Gets the maximum temperature rise per stage.
   *
   * @return max temp rise in Kelvin
   */
  public double getMaxTemperatureRisePerStage() {
    return maxTemperatureRisePerStage;
  }

  /**
   * Sets the maximum temperature rise per stage.
   *
   * @param tempRise max temp rise in Kelvin
   */
  public void setMaxTemperatureRisePerStage(double tempRise) {
    this.maxTemperatureRisePerStage = tempRise;
  }

  /**
   * Gets the maximum suction temperature.
   *
   * @return max suction temp in Kelvin
   */
  public double getMaxSuctionTemperature() {
    return maxSuctionTemperature;
  }

  /**
   * Sets the maximum suction temperature.
   *
   * @param tempKelvin max suction temp in Kelvin
   */
  public void setMaxSuctionTemperature(double tempKelvin) {
    this.maxSuctionTemperature = tempKelvin;
  }

  // Pressure getters/setters

  /**
   * Gets the maximum discharge pressure.
   *
   * @return max discharge pressure in bara
   */
  public double getMaxDischargePressure() {
    return maxDischargePressure;
  }

  /**
   * Sets the maximum discharge pressure.
   *
   * @param pressure max discharge pressure in bara
   */
  public void setMaxDischargePressure(double pressure) {
    this.maxDischargePressure = pressure;
  }

  /**
   * Gets the minimum suction pressure.
   *
   * @return min suction pressure in bara
   */
  public double getMinSuctionPressure() {
    return minSuctionPressure;
  }

  /**
   * Sets the minimum suction pressure.
   *
   * @param pressure min suction pressure in bara
   */
  public void setMinSuctionPressure(double pressure) {
    this.minSuctionPressure = pressure;
  }

  /**
   * Gets the maximum pressure ratio per stage.
   *
   * @return max pressure ratio
   */
  public double getMaxPressureRatioPerStage() {
    return maxPressureRatioPerStage;
  }

  /**
   * Sets the maximum pressure ratio per stage.
   *
   * @param ratio max pressure ratio
   */
  public void setMaxPressureRatioPerStage(double ratio) {
    this.maxPressureRatioPerStage = ratio;
  }

  // Driver getters/setters

  /**
   * Gets the driver curve.
   *
   * @return driver curve or null if not set
   */
  public DriverCurve getDriverCurve() {
    return driverCurve;
  }

  /**
   * Sets the driver curve.
   *
   * @param driver driver curve
   */
  public void setDriverCurve(DriverCurve driver) {
    this.driverCurve = driver;
  }

  /**
   * Gets the driver power margin.
   *
   * @return power margin as fraction (0-1)
   */
  public double getDriverPowerMargin() {
    return driverPowerMargin;
  }

  /**
   * Sets the driver power margin.
   *
   * @param margin power margin as fraction (0-1)
   */
  public void setDriverPowerMargin(double margin) {
    this.driverPowerMargin = margin;
  }

  // Mechanical getters/setters

  /**
   * Gets the maximum shaft torque.
   *
   * @return max torque in Nm
   */
  public double getMaxShaftTorque() {
    return maxShaftTorque;
  }

  /**
   * Sets the maximum shaft torque.
   *
   * @param torque max torque in Nm
   */
  public void setMaxShaftTorque(double torque) {
    this.maxShaftTorque = torque;
  }

  /**
   * Gets the maximum vibration.
   *
   * @return max vibration in mm/s
   */
  public double getMaxVibration() {
    return maxVibration;
  }

  /**
   * Sets the maximum vibration.
   *
   * @param vibration max vibration in mm/s
   */
  public void setMaxVibration(double vibration) {
    this.maxVibration = vibration;
  }

  // Control settings getters/setters

  /**
   * Checks if anti-surge control is enabled.
   *
   * @return true if enabled
   */
  public boolean isUseAntiSurgeControl() {
    return useAntiSurgeControl;
  }

  /**
   * Sets whether to use anti-surge control.
   *
   * @param use true to enable
   */
  public void setUseAntiSurgeControl(boolean use) {
    this.useAntiSurgeControl = use;
  }

  /**
   * Checks if near-surge operation is allowed.
   *
   * @return true if allowed
   */
  public boolean isAllowNearSurgeOperation() {
    return allowNearSurgeOperation;
  }

  /**
   * Sets whether near-surge operation is allowed.
   *
   * @param allow true to allow
   */
  public void setAllowNearSurgeOperation(boolean allow) {
    this.allowNearSurgeOperation = allow;
  }

  /**
   * Checks if hard limits are enforced.
   *
   * @return true if enforced
   */
  public boolean isEnforceHardLimits() {
    return enforceHardLimits;
  }

  /**
   * Sets whether hard limits are enforced.
   *
   * @param enforce true to enforce
   */
  public void setEnforceHardLimits(boolean enforce) {
    this.enforceHardLimits = enforce;
  }

  /**
   * Creates a copy of this configuration.
   *
   * @return copy of config
   */
  public CompressorConstraintConfig copy() {
    CompressorConstraintConfig copy = new CompressorConstraintConfig();
    copy.minSurgeMargin = this.minSurgeMargin;
    copy.minStonewallMargin = this.minStonewallMargin;
    copy.antiSurgeControlMargin = this.antiSurgeControlMargin;
    copy.maxRecycleValveOpening = this.maxRecycleValveOpening;
    copy.minSpeedRatio = this.minSpeedRatio;
    copy.maxSpeedRatio = this.maxSpeedRatio;
    copy.ratedSpeed = this.ratedSpeed;
    copy.maxPowerUtilization = this.maxPowerUtilization;
    copy.maxPower = this.maxPower;
    copy.minPower = this.minPower;
    copy.maxDischargeTemperature = this.maxDischargeTemperature;
    copy.maxTemperatureRisePerStage = this.maxTemperatureRisePerStage;
    copy.maxSuctionTemperature = this.maxSuctionTemperature;
    copy.maxDischargePressure = this.maxDischargePressure;
    copy.minSuctionPressure = this.minSuctionPressure;
    copy.maxPressureRatioPerStage = this.maxPressureRatioPerStage;
    copy.driverCurve = this.driverCurve;
    copy.driverPowerMargin = this.driverPowerMargin;
    copy.maxShaftTorque = this.maxShaftTorque;
    copy.maxVibration = this.maxVibration;
    copy.useAntiSurgeControl = this.useAntiSurgeControl;
    copy.allowNearSurgeOperation = this.allowNearSurgeOperation;
    copy.enforceHardLimits = this.enforceHardLimits;
    return copy;
  }
}
