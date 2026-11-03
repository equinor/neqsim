package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * Speed limiter that models how a power- or temperature-limited driver takes a compressor down in speed when the load
 * rises, and how it lets the speed recover when the load falls.
 *
 * <p>
 * A gas-turbine train at maximum gas-generator speed or exhaust temperature, or a variable-speed motor at its current
 * limit, cannot deliver more shaft power. When the suction pressure rises, the gas density and the compressor power
 * demand rise with it, and the driver responds by letting the shaft speed fall. The result is a speed that moves in
 * anti-phase with the suction pressure, a compressor mass flow that no longer follows a pressure controller, and a flow
 * stiffness (change of mass flow per change of suction pressure) that is a fraction of the fixed-speed value. This
 * class reproduces that behaviour in a dynamic simulation so that the consequences for a pressure controller can be
 * studied.
 * </p>
 *
 * <p>
 * The speed command is the lowest of the master demand and the speed ceiling. If the required power exceeds the power
 * limit, the speed is reduced at a rate proportional to the relative overload; otherwise it rises towards the command
 * at the configured ramp rate, but only while the power headroom exceeds a small hysteresis so that the limiter does
 * not chatter around the limit.
 * </p>
 *
 * <pre>
 * CompressorLoadLimiter lim = new CompressorLoadLimiter(1783.0, 22000.0);
 * lim.setLimiterGain(400.0);
 * double speed = lim.update(1783.0, currentSpeed, shaftPowerKW, dt);
 * boolean limited = lim.isPowerLimited();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class CompressorLoadLimiter implements Serializable {
  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  private double maxSpeed;
  private double minSpeed = 0.0;
  private double maxPowerKW;
  private double limiterGain = 200.0;
  private double speedRampRate = 2.0;
  private double hysteresis = 0.002;
  private boolean powerLimited = false;
  private boolean speedLimited = false;
  private double overloadFraction = 0.0;

  /**
   * Creates a limiter.
   *
   * @param maxSpeed speed ceiling in the unit of the speed signals used (for example rpm)
   * @param maxPowerKW power limit in kW, in the same basis as the power passed to {@link #update}
   * @throws IllegalArgumentException if the speed ceiling or power limit is not positive
   */
  public CompressorLoadLimiter(double maxSpeed, double maxPowerKW) {
    if (!(maxSpeed > 0.0) || !(maxPowerKW > 0.0)) {
      throw new IllegalArgumentException("speed ceiling and power limit must be positive");
    }
    this.maxSpeed = maxSpeed;
    this.maxPowerKW = maxPowerKW;
  }

  /**
   * Sets the speed reduction rate per unit of relative power overload.
   *
   * @param gain speed units per second for an overload of 100 % (1.0)
   */
  public void setLimiterGain(double gain) {
    this.limiterGain = Math.max(0.0, gain);
  }

  /**
   * Sets the rate at which speed recovers towards the command when there is power headroom.
   *
   * @param ratePerSecond speed units per second
   */
  public void setSpeedRampRate(double ratePerSecond) {
    this.speedRampRate = Math.max(0.0, ratePerSecond);
  }

  /**
   * Sets the relative power headroom required before speed may rise again.
   *
   * @param fraction headroom as fraction of the power limit, for example 0.002
   */
  public void setHysteresis(double fraction) {
    this.hysteresis = Math.max(0.0, fraction);
  }

  /**
   * Sets the lowest speed the limiter will command.
   *
   * @param minSpeed minimum speed
   */
  public void setMinSpeed(double minSpeed) {
    this.minSpeed = Math.max(0.0, minSpeed);
  }

  /**
   * Updates the power limit, for example when ambient conditions or the driver curve change.
   *
   * @param maxPowerKW new power limit in kW, must be positive
   * @throws IllegalArgumentException if the value is not positive
   */
  public void setMaxPowerKW(double maxPowerKW) {
    if (!(maxPowerKW > 0.0)) {
      throw new IllegalArgumentException("power limit must be positive");
    }
    this.maxPowerKW = maxPowerKW;
  }

  /**
   * Gets the power limit.
   *
   * @return power limit in kW
   */
  public double getMaxPowerKW() {
    return maxPowerKW;
  }

  /**
   * Gets the speed ceiling.
   *
   * @return speed ceiling
   */
  public double getMaxSpeed() {
    return maxSpeed;
  }

  /**
   * Advances the limiter one time step.
   *
   * @param demandSpeed speed demanded by the master (pressure or flow) controller
   * @param currentSpeed present shaft speed
   * @param requiredPowerKW power demanded by the compressor at the present speed and suction conditions, in kW
   * @param dt time step in seconds
   * @return the new speed command after applying the ceiling and the power limit
   */
  public double update(double demandSpeed, double currentSpeed, double requiredPowerKW, double dt) {
    double command = Math.min(demandSpeed, maxSpeed);
    overloadFraction = (requiredPowerKW - maxPowerKW) / maxPowerKW;
    double next = currentSpeed;
    if (overloadFraction > 0.0) {
      next = currentSpeed - limiterGain * overloadFraction * dt;
      powerLimited = true;
    } else if (currentSpeed < command && overloadFraction <= -hysteresis) {
      next = Math.min(command, currentSpeed + speedRampRate * dt);
      powerLimited = false;
    } else if (currentSpeed > command) {
      next = Math.max(command, currentSpeed - Math.max(speedRampRate, limiterGain * 0.01) * dt);
      powerLimited = false;
    } else {
      powerLimited = overloadFraction > -hysteresis;
    }
    speedLimited = demandSpeed >= maxSpeed && next >= maxSpeed - 1.0e-9;
    next = Math.min(maxSpeed, Math.max(minSpeed, next));
    return next;
  }

  /**
   * Advances the limiter for a compressor, using its speed, shaft power and driver model when present.
   *
   * <p>
   * If the compressor has a {@link CompressorDriver}, the driver power at the present speed is used as the limit
   * ({@link CompressorDriver#getMaxAvailablePowerAtSpeed(double)}) and the required driver power includes the driver
   * efficiency. Otherwise the limit given to the constructor and the shaft power are used.
   * </p>
   *
   * @param compressor compressor whose speed is limited
   * @param demandSpeed speed demanded by the master controller
   * @param dt time step in seconds
   * @return the new speed command; the caller applies it with {@link Compressor#setSpeed(double)}
   */
  public double update(Compressor compressor, double demandSpeed, double dt) {
    double speed = compressor.getSpeed();
    double power = compressor.getPower("kW");
    CompressorDriver driver = compressor.getDriver();
    if (driver != null && driver.getMaxPower() > 0.0) {
      setMaxPowerKW(driver.getMaxAvailablePowerAtSpeed(speed));
      power = driver.getRequiredDriverPower(power, speed);
    }
    return update(demandSpeed, speed, power, dt);
  }

  /**
   * Whether the power limit is active.
   *
   * @return true when the compressor is held by the power limit
   */
  public boolean isPowerLimited() {
    return powerLimited;
  }

  /**
   * Whether the speed ceiling is active.
   *
   * @return true when the speed is at the ceiling with a demand at or above it
   */
  public boolean isSpeedLimited() {
    return speedLimited;
  }

  /**
   * Relative power overload at the last update, negative when there is headroom.
   *
   * @return (required power - limit) / limit
   */
  public double getOverloadFraction() {
    return overloadFraction;
  }

  /**
   * Relative power headroom at the last update, suitable as an anti-windup or load-sharing signal for the master loop.
   *
   * @return (limit - required power) / limit
   */
  public double getPowerHeadroomFraction() {
    return -overloadFraction;
  }
}
