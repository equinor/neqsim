package neqsim.process.equipment.compressor.driver;

/**
 * Electric motor driver model with VFD support.
 *
 * <p>
 * Models electric motor performance including:
 * </p>
 * <ul>
 * <li>Fixed speed or variable frequency drive (VFD) operation</li>
 * <li>Constant torque or constant power speed ranges</li>
 * <li>Part-load efficiency characteristics</li>
 * <li>Motor temperature limits</li>
 * </ul>
 *
 * <h3>VFD Operation Modes</h3>
 * <ul>
 * <li><strong>Constant Torque:</strong> Torque is constant up to base speed, power proportional to
 * speed</li>
 * <li><strong>Constant Power:</strong> Above base speed, power is constant, torque decreases</li>
 * </ul>
 *
 * <h3>Efficiency Characteristics</h3>
 * <p>
 * Electric motors have high efficiency at rated load (typically 90-97% for large motors) with
 * efficiency dropping at part load due to fixed losses (core losses, friction).
 * </p>
 *
 * <h3>Example Usage</h3>
 * 
 * <pre>
 * ElectricMotorDriver motor = new ElectricMotorDriver(5000, 3600, 0.95);
 * motor.setHasVFD(true);
 * motor.setMinSpeedRatio(0.3); // 30% minimum speed with VFD
 * 
 * double availablePower = motor.getAvailablePower(2500); // At 2500 RPM
 * double efficiency = motor.getEfficiency(3000, 0.8); // At 80% load
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ElectricMotorDriver extends DriverCurveBase {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  /** Whether motor has a variable frequency drive. */
  private boolean hasVFD = false;

  /** Minimum speed ratio with VFD (fraction of rated speed). */
  private double minSpeedRatio = 0.0;

  /** Base speed for field weakening region (fraction of rated speed). */
  private double baseSpeedRatio = 1.0;

  /** Maximum speed ratio (typically 1.2-1.5 with field weakening). */
  private double maxSpeedRatio = 1.0;

  /** Motor efficiency class (IE1, IE2, IE3, IE4). */
  private String efficiencyClass = "IE3";

  /** Service factor (continuous overload capability). */
  private double serviceFactor = 1.15;

  /** Insulation class temperature limit in Celsius. */
  private double insulationTempLimit = 155.0; // Class F

  /** Part-load efficiency at 75% load (fraction of full load efficiency). */
  private double efficiency75 = 0.98;

  /** Part-load efficiency at 50% load (fraction of full load efficiency). */
  private double efficiency50 = 0.95;

  /** Part-load efficiency at 25% load (fraction of full load efficiency). */
  private double efficiency25 = 0.85;

  /**
   * Default constructor.
   */
  public ElectricMotorDriver() {
    super();
  }

  /**
   * Constructor with rated power and efficiency.
   *
   * @param ratedPowerKW rated mechanical output power in kW
   * @param designEfficiency design efficiency at rated conditions (0-1)
   */
  public ElectricMotorDriver(double ratedPowerKW, double designEfficiency) {
    super(ratedPowerKW, 3600.0, designEfficiency); // Typical 2-pole motor at 60Hz
    this.minSpeed = 0.0; // Fixed speed motor
    this.maxSpeed = ratedSpeed;
  }

  /**
   * Constructor with full parameters.
   *
   * @param ratedPowerKW rated mechanical output power in kW
   * @param ratedSpeedRPM rated speed in RPM
   * @param designEfficiency design efficiency at rated conditions (0-1)
   */
  public ElectricMotorDriver(double ratedPowerKW, double ratedSpeedRPM, double designEfficiency) {
    super(ratedPowerKW, ratedSpeedRPM, designEfficiency);
    this.minSpeed = ratedSpeed * 0.98; // Fixed speed motor (slip range)
    this.maxSpeed = ratedSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public String getDriverType() {
    return hasVFD ? "ElectricMotor_VFD" : "ElectricMotor_Fixed";
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailablePower(double speed) {
    if (!hasVFD) {
      // Fixed speed motor - only operates near rated speed
      if (Math.abs(speed - ratedSpeed) / ratedSpeed > 0.05) {
        return 0.0;
      }
      return ratedPower;
    }

    // VFD operation
    if (speed < minSpeed || speed > maxSpeed) {
      return 0.0;
    }

    double speedRatio = speed / ratedSpeed;

    if (speedRatio <= baseSpeedRatio) {
      // Constant torque region - power proportional to speed
      return ratedPower * speedRatio / baseSpeedRatio;
    } else {
      // Field weakening region - constant power
      return ratedPower;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getAvailableTorque(double speed) {
    if (!hasVFD) {
      // Fixed speed motor
      if (Math.abs(speed - ratedSpeed) / ratedSpeed > 0.05) {
        return 0.0;
      }
      return super.getAvailableTorque(ratedSpeed);
    }

    // VFD operation
    if (speed < minSpeed || speed > maxSpeed) {
      return 0.0;
    }

    double ratedTorque = ratedPower * 1000.0 * 60.0 / (2.0 * Math.PI * ratedSpeed);
    double speedRatio = speed / ratedSpeed;

    if (speedRatio <= baseSpeedRatio) {
      // Constant torque region
      return ratedTorque;
    } else {
      // Field weakening region - torque decreases
      return ratedTorque * baseSpeedRatio / speedRatio;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getEfficiency(double speed, double loadFraction) {
    if (!hasVFD) {
      // Fixed speed motor efficiency
      return getPartLoadEfficiency(loadFraction);
    }

    // VFD motor efficiency (includes VFD losses)
    double motorEff = getPartLoadEfficiency(loadFraction);

    // VFD efficiency (typically 96-98%)
    double speedRatio = speed / ratedSpeed;
    double vfdEff = 0.97;
    if (speedRatio < 0.5) {
      // VFD efficiency drops at low speeds
      vfdEff = 0.92 + 0.1 * speedRatio;
    }

    return motorEff * vfdEff;
  }

  /**
   * Gets the part-load efficiency.
   *
   * @param loadFraction load as fraction of rated power (0-1)
   * @return efficiency (0-1)
   */
  private double getPartLoadEfficiency(double loadFraction) {
    if (loadFraction <= 0 || loadFraction > 1.2) {
      return 0.0;
    }

    // Interpolate efficiency based on load points
    double eff;
    if (loadFraction >= 1.0) {
      eff = designEfficiency;
    } else if (loadFraction >= 0.75) {
      eff = designEfficiency * interpolate(loadFraction, 0.75, 1.0, efficiency75, 1.0);
    } else if (loadFraction >= 0.50) {
      eff = designEfficiency * interpolate(loadFraction, 0.50, 0.75, efficiency50, efficiency75);
    } else if (loadFraction >= 0.25) {
      eff = designEfficiency * interpolate(loadFraction, 0.25, 0.50, efficiency25, efficiency50);
    } else {
      // Below 25% load, efficiency drops rapidly
      eff = designEfficiency * efficiency25 * (loadFraction / 0.25);
    }

    return eff;
  }

  /**
   * Linear interpolation helper.
   *
   * @param x the value to interpolate at
   * @param x1 first x coordinate
   * @param x2 second x coordinate
   * @param y1 first y coordinate
   * @param y2 second y coordinate
   * @return the interpolated y value at x
   */
  private double interpolate(double x, double x1, double x2, double y1, double y2) {
    return y1 + (y2 - y1) * (x - x1) / (x2 - x1);
  }

  /** {@inheritDoc} */
  @Override
  public double getAmbientDeratingFactor() {
    // Electric motors derate based on ambient temperature above 40°C
    double baseAmbient = 40.0;
    if (ambientTemperature <= baseAmbient) {
      return 1.0;
    }

    // Typical derating: reduce to ~90% at 50°C, ~80% at 55°C
    double tempRise = ambientTemperature - baseAmbient;
    double deratingFactor = 1.0 - 0.02 * tempRise;

    // Altitude derating (above 1000m)
    if (altitude > 1000.0) {
      double altDerating = 1.0 - 0.01 * (altitude - 1000.0) / 100.0;
      deratingFactor *= altDerating;
    }

    return Math.max(0.5, Math.min(1.0, deratingFactor));
  }

  /** {@inheritDoc} */
  @Override
  public double getFuelConsumption(double powerOutput, double speed) {
    if (powerOutput <= 0) {
      return 0.0;
    }

    double availablePower = getAvailablePower(speed);
    if (availablePower <= 0) {
      return 0.0;
    }

    double loadFraction = Math.min(powerOutput / availablePower, 1.2);
    double efficiency = getEfficiency(speed, loadFraction);

    if (efficiency <= 0) {
      return 0.0;
    }

    // Return electrical power input in kW
    return powerOutput / efficiency;
  }

  /**
   * Gets the electrical power input for given mechanical output.
   *
   * @param mechanicalPowerKW mechanical output power in kW
   * @param speed operating speed in RPM
   * @return electrical input power in kW
   */
  public double getElectricalInput(double mechanicalPowerKW, double speed) {
    return getFuelConsumption(mechanicalPowerKW, speed);
  }

  // VFD configuration methods

  /**
   * Checks if motor has VFD.
   *
   * @return true if motor has VFD
   */
  public boolean hasVFD() {
    return hasVFD;
  }

  /**
   * Sets whether motor has VFD.
   *
   * @param hasVFD true to enable VFD operation
   */
  public void setHasVFD(boolean hasVFD) {
    this.hasVFD = hasVFD;
    if (hasVFD) {
      this.minSpeed = ratedSpeed * minSpeedRatio;
      this.maxSpeed = ratedSpeed * maxSpeedRatio;
    } else {
      this.minSpeed = ratedSpeed * 0.98;
      this.maxSpeed = ratedSpeed;
    }
  }

  /**
   * Gets the minimum speed ratio.
   *
   * @return minimum speed as fraction of rated speed
   */
  public double getMinSpeedRatio() {
    return minSpeedRatio;
  }

  /**
   * Sets the minimum speed ratio for VFD operation.
   *
   * @param ratio minimum speed as fraction of rated speed (0-1)
   */
  public void setMinSpeedRatio(double ratio) {
    this.minSpeedRatio = ratio;
    if (hasVFD) {
      this.minSpeed = ratedSpeed * ratio;
    }
  }

  /**
   * Gets the base speed ratio (transition to field weakening).
   *
   * @return base speed as fraction of rated speed
   */
  public double getBaseSpeedRatio() {
    return baseSpeedRatio;
  }

  /**
   * Sets the base speed ratio.
   *
   * @param ratio base speed as fraction of rated speed
   */
  public void setBaseSpeedRatio(double ratio) {
    this.baseSpeedRatio = ratio;
  }

  /**
   * Gets the maximum speed ratio.
   *
   * @return maximum speed as fraction of rated speed
   */
  public double getMaxSpeedRatio() {
    return maxSpeedRatio;
  }

  /**
   * Sets the maximum speed ratio.
   *
   * @param ratio maximum speed as fraction of rated speed
   */
  public void setMaxSpeedRatio(double ratio) {
    this.maxSpeedRatio = ratio;
    if (hasVFD) {
      this.maxSpeed = ratedSpeed * ratio;
    }
  }

  /**
   * Gets the service factor.
   *
   * @return service factor
   */
  public double getServiceFactor() {
    return serviceFactor;
  }

  /**
   * Sets the service factor.
   *
   * @param serviceFactor service factor (typically 1.0-1.25)
   */
  public void setServiceFactor(double serviceFactor) {
    this.serviceFactor = serviceFactor;
  }

  /**
   * Gets the efficiency class.
   *
   * @return efficiency class string (IE1, IE2, IE3, IE4)
   */
  public String getEfficiencyClass() {
    return efficiencyClass;
  }

  /**
   * Sets the efficiency class and updates efficiency characteristics.
   *
   * @param efficiencyClass efficiency class (IE1, IE2, IE3, IE4)
   */
  public void setEfficiencyClass(String efficiencyClass) {
    this.efficiencyClass = efficiencyClass;
    // Adjust part-load characteristics based on class
    if ("IE4".equals(efficiencyClass)) {
      this.efficiency75 = 0.99;
      this.efficiency50 = 0.97;
      this.efficiency25 = 0.90;
    } else if ("IE3".equals(efficiencyClass)) {
      this.efficiency75 = 0.98;
      this.efficiency50 = 0.95;
      this.efficiency25 = 0.85;
    } else if ("IE2".equals(efficiencyClass)) {
      this.efficiency75 = 0.97;
      this.efficiency50 = 0.93;
      this.efficiency25 = 0.80;
    } else {
      this.efficiency75 = 0.95;
      this.efficiency50 = 0.90;
      this.efficiency25 = 0.75;
    }
  }
}
