package neqsim.process.equipment.stream;

/**
 * Shared rotating shaft that balances mechanical power producers and loads.
 *
 * <p>
 * In steady state the inherited bus residual is positive when generation exceeds demand and negative when the shaft is
 * under-powered. In transient mode {@link #advanceTransient(double)} integrates rotational kinetic energy,
 * {@code d(0.5 J omega^2)/dt = Pnet}, with optional friction, speed, acceleration, and trip limits.
 * </p>
 *
 * @author NeqSim
 * @version 2.0
 */
public class MechanicalShaft extends EnergyBus {
  private static final long serialVersionUID = 1000L;

  private double speed = 0.0;
  private double momentOfInertia = 0.0;
  private double mechanicalEfficiency = 1.0;
  private double frictionLoss = 0.0;
  private double maximumSpeed = Double.POSITIVE_INFINITY;
  private double maximumAcceleration = Double.POSITIVE_INFINITY;
  private double maximumDeceleration = Double.POSITIVE_INFINITY;
  private boolean tripped = false;

  /** Creates an unnamed shaft. */
  public MechanicalShaft() {
    this("");
  }

  /**
   * Creates a named shaft.
   *
   * @param name shaft name
   */
  public MechanicalShaft(String name) {
    super(name, EnergyType.SHAFT_WORK);
  }

  /**
   * Sets generated shaft power in watts.
   *
   * @param participant producer name
   * @param power generated power in W
   */
  public void setGeneratedPower(String participant, double power) {
    setContribution("producer:" + participant, Math.abs(power) * mechanicalEfficiency);
  }

  /**
   * Sets consumed shaft power in watts.
   *
   * @param participant load name
   * @param power consumed power in W
   */
  public void setConsumedPower(String participant, double power) {
    setContribution("load:" + participant, -Math.abs(power));
  }

  /**
   * Gets shaft speed.
   *
   * @return speed in rpm
   */
  public double getSpeed() {
    return speed;
  }

  /**
   * Sets shaft speed.
   *
   * @param speed speed in rpm
   */
  public void setSpeed(double speed) {
    if (!Double.isFinite(speed) || speed < 0.0) {
      throw new IllegalArgumentException("Shaft speed must be finite and non-negative");
    }
    this.speed = Math.min(speed, maximumSpeed);
    if (this.speed > 0.0) {
      getQuality().setShaftSpeed(this.speed);
    } else {
      getQuality().setShaftSpeed(Double.NaN);
    }
  }

  /**
   * Gets rotating moment of inertia.
   *
   * @return moment of inertia in kg m2
   */
  public double getMomentOfInertia() {
    return momentOfInertia;
  }

  /**
   * Sets rotating moment of inertia.
   *
   * @param momentOfInertia moment of inertia in kg m2
   */
  public void setMomentOfInertia(double momentOfInertia) {
    if (!Double.isFinite(momentOfInertia) || momentOfInertia < 0.0) {
      throw new IllegalArgumentException("Shaft moment of inertia must be finite and non-negative");
    }
    this.momentOfInertia = momentOfInertia;
  }

  /**
   * Gets mechanical transfer efficiency.
   *
   * @return efficiency in the range (0, 1]
   */
  public double getMechanicalEfficiency() {
    return mechanicalEfficiency;
  }

  /**
   * Sets mechanical transfer efficiency applied to generated power.
   *
   * @param mechanicalEfficiency efficiency in the range (0, 1]
   */
  public void setMechanicalEfficiency(double mechanicalEfficiency) {
    if (!Double.isFinite(mechanicalEfficiency) || mechanicalEfficiency <= 0.0 || mechanicalEfficiency > 1.0) {
      throw new IllegalArgumentException("Shaft efficiency must be in (0, 1]");
    }
    this.mechanicalEfficiency = mechanicalEfficiency;
  }

  /**
   * Gets speed-dependent aggregate friction loss.
   *
   * @return friction loss in W
   */
  public double getFrictionLoss() {
    return frictionLoss;
  }

  /**
   * Sets aggregate friction loss.
   *
   * @param frictionLoss friction loss in W
   */
  public void setFrictionLoss(double frictionLoss) {
    if (!Double.isFinite(frictionLoss) || frictionLoss < 0.0) {
      throw new IllegalArgumentException("Shaft friction loss must be non-negative and finite");
    }
    this.frictionLoss = frictionLoss;
  }

  /**
   * Sets maximum shaft speed.
   *
   * @param maximumSpeed maximum speed in rpm
   */
  public void setMaximumSpeed(double maximumSpeed) {
    if (Double.isNaN(maximumSpeed) || maximumSpeed <= 0.0) {
      throw new IllegalArgumentException("Maximum shaft speed must be positive");
    }
    this.maximumSpeed = maximumSpeed;
    setSpeed(Math.min(speed, maximumSpeed));
  }

  /**
   * Gets maximum shaft speed.
   *
   * @return maximum speed in rpm
   */
  public double getMaximumSpeed() {
    return maximumSpeed;
  }

  /**
   * Sets shaft acceleration and deceleration limits.
   *
   * @param maximumAcceleration maximum speed increase in rpm/s
   * @param maximumDeceleration maximum speed decrease in rpm/s
   */
  public void setAccelerationLimits(double maximumAcceleration, double maximumDeceleration) {
    if (Double.isNaN(maximumAcceleration) || maximumAcceleration <= 0.0 || Double.isNaN(maximumDeceleration)
        || maximumDeceleration <= 0.0) {
      throw new IllegalArgumentException("Shaft acceleration limits must be positive");
    }
    this.maximumAcceleration = maximumAcceleration;
    this.maximumDeceleration = maximumDeceleration;
  }

  /**
   * Trips or resets shaft generation.
   *
   * <p>
   * While tripped, positive net power is ignored and the shaft can only coast down through load and friction.
   * </p>
   *
   * @param tripped trip state
   */
  public void setTripped(boolean tripped) {
    this.tripped = tripped;
  }

  /**
   * Checks shaft trip state.
   *
   * @return {@code true} when tripped
   */
  public boolean isTripped() {
    return tripped;
  }

  /**
   * Gets current net shaft torque.
   *
   * @return torque in N m, or zero at standstill
   */
  public double getNetTorque() {
    double angularSpeed = speed * 2.0 * Math.PI / 60.0;
    if (angularSpeed <= 0.0) {
      return 0.0;
    }
    return (getEffectiveNetPower() - frictionLoss) / angularSpeed;
  }

  /**
   * Advances shaft speed from the current energy-network imbalance.
   *
   * @param dt timestep in seconds
   * @return updated speed in rpm
   */
  public double advanceTransient(double dt) {
    if (!Double.isFinite(dt) || dt < 0.0) {
      throw new IllegalArgumentException("Transient shaft timestep must be non-negative and finite");
    }
    if (dt == 0.0) {
      return speed;
    }
    if (momentOfInertia <= 0.0) {
      throw new IllegalStateException("A positive shaft moment of inertia is required for transient integration");
    }

    double oldSpeed = speed;
    double oldAngularSpeed = oldSpeed * 2.0 * Math.PI / 60.0;
    double netPower = getEffectiveNetPower();
    if (oldSpeed > 0.0 || netPower > 0.0) {
      netPower -= frictionLoss;
    }
    double angularSpeedSquared = Math.max(0.0,
        oldAngularSpeed * oldAngularSpeed + 2.0 * netPower * dt / momentOfInertia);
    double targetSpeed = Math.sqrt(angularSpeedSquared) * 60.0 / (2.0 * Math.PI);
    targetSpeed = Math.min(targetSpeed, maximumSpeed);

    double delta = targetSpeed - oldSpeed;
    double allowedIncrease = maximumAcceleration * dt;
    double allowedDecrease = maximumDeceleration * dt;
    if (delta > allowedIncrease) {
      targetSpeed = oldSpeed + allowedIncrease;
    } else if (-delta > allowedDecrease) {
      targetSpeed = Math.max(0.0, oldSpeed - allowedDecrease);
    }
    setSpeed(targetSpeed);
    return speed;
  }

  /**
   * Alias for {@link #advanceTransient(double)}.
   *
   * @param dt timestep in seconds
   * @return updated speed in rpm
   */
  public double runTransient(double dt) {
    return advanceTransient(dt);
  }

  /**
   * Gets net power after applying trip logic.
   *
   * @return effective net power in W
   */
  private double getEffectiveNetPower() {
    if (!tripped) {
      return getNetPower();
    }
    double loadPower = Math.min(0.0, duty);
    for (Double contribution : getContributions().values()) {
      loadPower += Math.min(0.0, contribution.doubleValue());
    }
    return loadPower;
  }
}
