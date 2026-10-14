package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Experimental rigid-pig mechanics under prescribed, piecewise-constant pressure and inclination.
 *
 * <p>
 * Positive position and velocity follow the pipe coordinate. Pressure differential is upstream minus downstream
 * pressure in Pa; inclination is positive uphill in radians. The applied force is {@code area * deltaP - mass * g *
 * sin(inclination)}. Static friction holds a resting pig through equality with its specified breakaway force. Moving
 * friction opposes velocity. A zero-velocity event is resolved exactly before deciding whether to stick or reverse. No
 * velocity floor, time-step-dependent friction or empirical pig parameters are supplied.
 * </p>
 *
 * <p>
 * This stateless submodel does not solve fluid inventories, bypass, pressure waves, pig receipt, bends or changing
 * diameter. Callers must split steps at prescribed pressure/geometry changes. A future hydraulic coupling must balance
 * the pressure work and displaced volume on both sides of the pig; prescribing pressure here does not provide that
 * coupling.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PigMotion implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double GRAVITY = 9.80665;
  private final double massKg;
  private final double pressureAreaM2;
  private final double staticFrictionN;
  private final double kineticFrictionN;

  /**
   * Creates mechanics with explicit mechanical parameters, without vendor defaults.
   *
   * @param massKg pig mass in kg, positive
   * @param pressureAreaM2 effective differential-pressure area in square metres, positive
   * @param staticFrictionN maximum static resistance in N, nonnegative
   * @param kineticFrictionN moving resistance in N, between zero and static resistance
   */
  public PigMotion(double massKg, double pressureAreaM2, double staticFrictionN, double kineticFrictionN) {
    requireFinite(massKg, "massKg");
    requireFinite(pressureAreaM2, "pressureAreaM2");
    requireFinite(staticFrictionN, "staticFrictionN");
    requireFinite(kineticFrictionN, "kineticFrictionN");
    if (massKg <= 0.0 || pressureAreaM2 <= 0.0 || kineticFrictionN < 0.0 || staticFrictionN < kineticFrictionN) {
      throw new IllegalArgumentException("Require mass > 0, area > 0 and static friction >= kinetic friction >= 0");
    }
    this.massKg = massKg;
    this.pressureAreaM2 = pressureAreaM2;
    this.staticFrictionN = staticFrictionN;
    this.kineticFrictionN = kineticFrictionN;
  }

  /**
   * Advances the mechanical state analytically through one constant-input interval.
   *
   * @param positionM initial axial position in m; signed coordinates are allowed
   * @param velocityMps initial signed axial velocity in m/s
   * @param differentialPressurePa upstream minus downstream pressure in Pa
   * @param inclinationRadians pipe angle from horizontal in radians, in [-pi/2, pi/2]
   * @param durationSeconds interval duration in s, nonnegative
   * @return immutable motion and mechanical work ledger for this interval
   */
  public Step advance(double positionM, double velocityMps, double differentialPressurePa, double inclinationRadians,
      double durationSeconds) {
    requireFinite(positionM, "positionM");
    requireFinite(velocityMps, "velocityMps");
    requireFinite(differentialPressurePa, "differentialPressurePa");
    requireFinite(inclinationRadians, "inclinationRadians");
    requireFinite(durationSeconds, "durationSeconds");
    if (Math.abs(inclinationRadians) > Math.PI / 2.0 || durationSeconds < 0.0) {
      throw new IllegalArgumentException("Inclination must be in [-pi/2, pi/2] and duration nonnegative");
    }
    double pressureForce = pressureAreaM2 * differentialPressurePa;
    double gravityForce = -massKg * GRAVITY * Math.sin(inclinationRadians);
    double drivingForce = pressureForce + gravityForce;
    requireFinite(pressureForce, "pressure force");
    requireFinite(gravityForce, "gravity force");
    requireFinite(drivingForce, "driving force");
    double velocity = velocityMps;
    double displacement = 0.0;
    double distance = 0.0;
    double movingSeconds = 0.0;
    double stopTime = -1.0;
    double remaining = durationSeconds;

    if (velocity != 0.0 && remaining > 0.0) {
      double acceleration = (drivingForce - Math.copySign(kineticFrictionN, velocity)) / massKg;
      requireFinite(acceleration, "acceleration");
      double timeToStop = Math.signum(acceleration) == -Math.signum(velocity) ? -velocity / acceleration
          : Double.POSITIVE_INFINITY;
      double movingInterval = Math.min(remaining, timeToStop);
      double nextVelocity = timeToStop <= remaining ? 0.0 : velocity + acceleration * movingInterval;
      double motion = 0.5 * (velocity + nextVelocity) * movingInterval;
      displacement += motion;
      distance += Math.abs(motion);
      movingSeconds += movingInterval;
      remaining -= movingInterval;
      velocity = nextVelocity;
      if (timeToStop <= durationSeconds) {
        stopTime = timeToStop;
      }
    }
    if (velocity == 0.0 && remaining > 0.0 && Math.abs(drivingForce) > staticFrictionN) {
      double acceleration = (drivingForce - Math.copySign(kineticFrictionN, drivingForce)) / massKg;
      requireFinite(acceleration, "acceleration");
      velocity = acceleration * remaining;
      double motion = 0.5 * velocity * remaining;
      displacement += motion;
      distance += Math.abs(motion);
      movingSeconds += remaining;
    }

    double pressureWork = pressureForce * displacement;
    double gravityWork = gravityForce * displacement;
    double frictionHeat = kineticFrictionN * distance;
    double kineticChange = 0.5 * massKg * (velocity - velocityMps) * (velocity + velocityMps);
    return new Step(durationSeconds, positionM + displacement, velocity, displacement, distance, movingSeconds,
        stopTime, pressureWork, gravityWork, frictionHeat, kineticChange);
  }

  /**
   * Runs a prescribed schedule, splitting exactly at every input event.
   *
   * <p>
   * Inclination is prescribed in time, not looked up from a spatial route. This method cannot detect arrival at a
   * receiver or a terrain breakpoint. Returned samples include the initial state and every interval endpoint.
   * </p>
   *
   * @param positionM initial axial position in m
   * @param velocityMps initial axial velocity in m/s
   * @param durationsSeconds nonempty positive interval durations in s
   * @param pressureDifferencesPa pressure differentials in Pa, one per interval
   * @param inclinationsRadians inclinations in radians, one per interval
   * @return immutable trajectory with mechanical ledgers and defensive sample arrays
   */
  public Trajectory simulate(double positionM, double velocityMps, double[] durationsSeconds,
      double[] pressureDifferencesPa, double[] inclinationsRadians) {
    if (durationsSeconds == null || pressureDifferencesPa == null || inclinationsRadians == null
        || durationsSeconds.length == 0 || durationsSeconds.length != pressureDifferencesPa.length
        || durationsSeconds.length != inclinationsRadians.length) {
      throw new IllegalArgumentException("Schedule arrays must be nonempty and have identical lengths");
    }
    double[] durations = durationsSeconds.clone();
    double[] pressures = pressureDifferencesPa.clone();
    double[] angles = inclinationsRadians.clone();
    double[] times = new double[durations.length + 1];
    double[] positions = new double[times.length];
    double[] velocities = new double[times.length];
    Step[] steps = new Step[durations.length];
    positions[0] = positionM;
    velocities[0] = velocityMps;
    for (int i = 0; i < durations.length; i++) {
      if (!Double.isFinite(durations[i]) || durations[i] <= 0.0) {
        throw new IllegalArgumentException("Scheduled durations must be positive and finite");
      }
      times[i + 1] = times[i] + durations[i];
      requireFinite(times[i + 1], "elapsed time");
      if (times[i + 1] <= times[i]) {
        throw new IllegalArgumentException("Scheduled time increment is below elapsed-time resolution");
      }
      steps[i] = advance(positions[i], velocities[i], pressures[i], angles[i], durations[i]);
      positions[i + 1] = steps[i].getPositionM();
      velocities[i + 1] = steps[i].getVelocityMps();
    }
    return new Trajectory(times, positions, velocities, steps);
  }

  /**
   * Rejects nonfinite inputs and arithmetic overflow without changing caller state.
   *
   * @param value value to check
   * @param name diagnostic quantity name
   */
  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite; check inputs and arithmetic range");
    }
  }

  /**
   * Immutable result of a mechanical step; work is positive into the pig and friction heat is nonnegative.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double durationSeconds;
    private final double positionM;
    private final double velocityMps;
    private final double displacementM;
    private final double distanceM;
    private final double movingSeconds;
    private final double stopTimeSeconds;
    private final double pressureWorkJ;
    private final double gravityWorkJ;
    private final double frictionHeatJ;
    private final double kineticEnergyChangeJ;

    /**
     * Creates and checks the complete step ledger.
     *
     * @param duration interval duration in s
     * @param position final position in m
     * @param velocity final velocity in m/s
     * @param displacement signed distance in m
     * @param distance total travelled distance in m
     * @param moving moving time in s
     * @param stop first moving-to-rest event time in s, or -1 if absent
     * @param pressureWork pressure work in J
     * @param gravityWork gravity work in J
     * @param heat dissipated friction energy in J
     * @param kineticChange kinetic energy change in J
     */
    private Step(double duration, double position, double velocity, double displacement, double distance, double moving,
        double stop, double pressureWork, double gravityWork, double heat, double kineticChange) {
      for (double value : new double[] {duration, position, velocity, displacement, distance, moving, stop,
          pressureWork, gravityWork, heat, kineticChange, kineticChange - pressureWork - gravityWork + heat}) {
        requireFinite(value, "step result");
      }
      durationSeconds = duration;
      positionM = position;
      velocityMps = velocity;
      displacementM = displacement;
      distanceM = distance;
      movingSeconds = moving;
      stopTimeSeconds = stop;
      pressureWorkJ = pressureWork;
      gravityWorkJ = gravityWork;
      frictionHeatJ = heat;
      kineticEnergyChangeJ = kineticChange;
    }

    /** @return interval duration in s */
    public double getDurationSeconds() {
      return durationSeconds;
    }

    /** @return final signed axial position in m */
    public double getPositionM() {
      return positionM;
    }

    /** @return final signed axial velocity in m/s */
    public double getVelocityMps() {
      return velocityMps;
    }

    /** @return signed axial displacement in m */
    public double getDisplacementM() {
      return displacementM;
    }

    /** @return total travelled distance in m, including both legs of a reversal */
    public double getDistanceM() {
      return distanceM;
    }

    /** @return time spent moving in s */
    public double getMovingSeconds() {
      return movingSeconds;
    }

    /** @return first moving-to-rest event time relative to interval start in s, or -1 if absent */
    public double getStopTimeSeconds() {
      return stopTimeSeconds;
    }

    /** @return work done by differential pressure in J */
    public double getPressureWorkJ() {
      return pressureWorkJ;
    }

    /** @return work done by gravity in J */
    public double getGravityWorkJ() {
      return gravityWorkJ;
    }

    /** @return nonnegative energy dissipated by sliding friction in J */
    public double getFrictionHeatJ() {
      return frictionHeatJ;
    }

    /** @return change in pig kinetic energy in J */
    public double getKineticEnergyChangeJ() {
      return kineticEnergyChangeJ;
    }

    /** @return kinetic change minus applied work plus friction heat in J; roundoff-level for this model */
    public double getEnergyResidualJ() {
      return kineticEnergyChangeJ - pressureWorkJ - gravityWorkJ + frictionHeatJ;
    }
  }

  /**
   * Immutable prescribed-input trajectory, without a coupled fluid mass or energy balance.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Trajectory implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[] timesSeconds;
    private final double[] positionsM;
    private final double[] velocitiesMps;
    private final Step[] steps;

    /**
     * Retains arrays owned exclusively by the schedule calculation.
     *
     * @param times sample times in s
     * @param positions axial positions in m
     * @param velocities axial velocities in m/s
     * @param ledgers immutable per-interval ledgers
     */
    private Trajectory(double[] times, double[] positions, double[] velocities, Step[] ledgers) {
      timesSeconds = times;
      positionsM = positions;
      velocitiesMps = velocities;
      steps = ledgers;
    }

    /** @return defensive copy of sample times in s, including zero */
    public double[] getTimesSeconds() {
      return timesSeconds.clone();
    }

    /** @return defensive copy of positions in m, including the initial state */
    public double[] getPositionsM() {
      return positionsM.clone();
    }

    /** @return defensive copy of velocities in m/s, including the initial state */
    public double[] getVelocitiesMps() {
      return velocitiesMps.clone();
    }

    /** @return defensive array of immutable interval ledgers */
    public Step[] getSteps() {
      return steps.clone();
    }
  }
}
