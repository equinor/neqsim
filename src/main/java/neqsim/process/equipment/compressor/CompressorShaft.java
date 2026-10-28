package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.equipment.compressor.driver.DriverCurve;
import neqsim.process.equipment.energy.ElectricMotor;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineUnit;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Groups several {@link Compressor} bodies that sit on ONE common shaft (driven by a single gas turbine or motor) so
 * they must all turn at the SAME speed.
 *
 * <p>
 * A multi-body compressor string (e.g. a recompression train BCL508-5 + BCL408 + BCL405/B on one gas-turbine shaft)
 * cannot have each body solve its own speed independently: physically there is a single shaft speed, and only the
 * string's final discharge pressure is a controlled target. The intermediate inter-body pressures therefore
 * <em>float</em> — they are whatever each body produces at the common speed on its performance chart.
 * </p>
 *
 * <p>
 * This class enforces that constraint. All member compressors are put in fixed-speed, chart-based mode
 * ({@code setSolveSpeed(false)} + a common {@code setSpeed}) so each produces its discharge pressure from its chart at
 * the shared speed. {@link #solveSpeed} then does a one-variable false-position (Illinois) secant on the common speed
 * until the reference (last) body's outlet pressure matches the target, re-solving the surrounding flowsheet between
 * guesses via a caller-supplied callback.
 * </p>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * CompressorShaft shaft = new CompressorShaft("23-KA train");
 * shaft.addCompressor(rc1); // 1st body
 * shaft.addCompressor(rc2); // 2nd body
 * shaft.addCompressor(rc3); // 3rd body (reference)
 * shaft.setSpeedBounds(6000.0, 15000.0);
 * shaft.setMaxShaftPower(24.0, "MW"); // or shaft.setTurbineDriver(gasTurbine)
 * // re-run the whole flowsheet between speed guesses so inter-body streams update
 * shaft.solveSpeed(rc3, 49.0, "bara", () -> process.run());
 * double rpm = shaft.getSpeed();
 * double util = shaft.getPowerUtilization(); // whole-string power vs the driver limit
 * }</pre>
 *
 * <h2>Driver power limit</h2>
 *
 * <p>
 * A common shaft has ONE driver, so the power limit belongs to the shaft rather than to any single body. Set it either
 * explicitly with {@link #setMaxShaftPower(double, String)}, or from a driver model: {@link #setTurbineDriver} attaches
 * a {@link GasTurbine} (the bodies become its driven loads, so its fuel gas and its own power utilization track the
 * string duty, and its rated power limits the shaft) and {@link #setDriver} attaches a {@link CompressorDriver} whose
 * speed-dependent, ambient-derated curve is evaluated at the solved common speed. Gearbox losses are covered by
 * {@link #setMechanicalEfficiency(double)}. {@link #getPowerUtilization()} then reports the whole-string utilization
 * and {@link #solveSpeed} returns {@link SolveStatus#OVER_POWER} when the driver cannot deliver.
 * </p>
 *
 * <h2>Gear ratios, idle members, ambient site rating and control targets</h2>
 *
 * <p>
 * Members can sit behind a gearbox: {@link #addCompressor(Compressor, double)} sets the member rpm per shaft rpm, so a
 * body with ratio 1.2 runs at 1.2 x the common speed on its own chart. A member that is out of service (train off,
 * parallel machine not running) is set idle with {@link #setMemberActive(Compressor, boolean)}: it is locked inactive,
 * leaves the shaft power sum and the surge/stonewall checks, and a shaft with no active member reports
 * {@link SolveStatus#SHAFT_IDLE}. {@link #setGasTurbineUnitDriver(GasTurbineUnit)} makes a site-rated
 * {@link GasTurbineUnit} (power versus ambient temperature, exhaust-temperature limit, fuel and exhaust heat) the
 * driver, and {@link #setAmbientTemperature(double, String)} moves its rating. {@link #solveSpeedToTarget} controls the
 * common speed from the discharge pressure, the suction pressure or the inlet flow of ONE controlling member, and
 * {@link #getOperatingReport()} lists, for every member, the speed, power, surge/stonewall margins and the recycle
 * fraction that anti-surge would need. {@link #findMaxSpeedWithinPowerLimit(Runnable)} gives the highest common speed
 * the driver can hold, and {@link DriverLimitedThroughputSolver} the highest throughput.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.1
 */
public class CompressorShaft implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CompressorShaft.class);

  /** Shaft name. */
  private final String name;

  /** Compressor bodies on this shaft (in flow order). */
  private final List<Compressor> compressors = new ArrayList<Compressor>();

  /** Common shaft speed in rpm. */
  private double speed = 10000.0;

  /** Explicit installed shaft power limit in watts (0 = not set, resolve from the driver instead). */
  private double maxShaftPowerW = 0.0;

  /** Gearbox / coupling efficiency between the driver output and the compressor bodies. */
  private double mechanicalEfficiency = 1.0;

  /** Optional driver model (motor or gas turbine) giving a speed- and ambient-dependent power limit. */
  private CompressorDriver driver = null;

  /** Optional gas turbine driving this shaft. */
  private GasTurbine turbineDriver = null;

  /** Optional site-rated catalogue gas turbine driving this shaft. */
  private GasTurbineUnit gasTurbineUnitDriver = null;

  /** Gear ratio (member rpm per shaft rpm) per member; absent means 1.0. */
  private final Map<Compressor, Double> gearRatios = new IdentityHashMap<Compressor, Double>();

  /** Members that are out of service. */
  private final Map<Compressor, Boolean> idleMembers = new IdentityHashMap<Compressor, Boolean>();

  /** Electrical supply cap on shaft power in watts (0 = none), e.g. power-from-shore or generator capacity. */
  private double supplyShaftPowerLimitW = 0.0;

  /** Ambient air temperature in K shared with the driver models. */
  private double ambientTemperatureK = 288.15;

  /** Lower speed bound in rpm. */
  private double minSpeed = 500.0;

  /** Upper speed bound in rpm. */
  private double maxSpeed = 25000.0;

  /** Driver performance curve (for example an {@code ElectricMotorDriver} with VFD) giving speed band and power. */
  private DriverCurve driverCurve = null;

  /** Electric motor process unit whose performance model is the driver curve. */
  private ElectricMotor motorDriverUnit = null;

  /** True while the speed bounds follow the driver curve; an explicit {@link #setSpeedBounds} switches it off. */
  private boolean speedBandFromDriver = false;

  /** Recycle (anti-surge) valve per member. */
  private final Map<Compressor, ThrottlingValve> antiSurgeValves = new IdentityHashMap<Compressor, ThrottlingValve>();

  /** Anti-surge controller per member, as an alternative way to reach the recycle valve. */
  private final Map<Compressor, AntiSurgeController> antiSurgeControllers = new IdentityHashMap<Compressor, AntiSurgeController>();

  /** Members whose built-in anti-surge was activated by {@link #applyRequiredRecycle()}. */
  private final Map<Compressor, Boolean> recycleAutoActivated = new IdentityHashMap<Compressor, Boolean>();

  /** Recycle fraction at which a recycle valve is fully open (linear opening, screening). */
  private double recycleValveFullOpenFraction = 0.5;

  /** Maximum root-finding iterations. */
  private int maxIterations = 60;

  /** Convergence tolerance on the reference outlet pressure in bar. */
  private double pressureToleranceBar = 1.0e-3;

  /** Pressure-control action applied when the target is below the minimum-speed capability. */
  private PressureControl pressureControl = PressureControl.NONE;

  /** Result of the most recent {@link #solveSpeed} call ({@code null} until it is called). */
  private SolveResult lastResult = null;

  /**
   * Feasibility status of a shaft speed solve. Mirrors how professional tools (e.g. eCalc) classify a compressor-train
   * operating point: reachable, reachable only with pressure control, or infeasible with a reason.
   */
  public enum SolveStatus {
    /** Target reached within tolerance by adjusting the common speed. */
    FEASIBLE,
    /** Target was below the minimum-speed capability and met by a pressure-control action (choke / recycle). */
    PRESSURE_CONTROLLED,
    /** Target above the head the string makes at its maximum-speed curve (genuinely infeasible). */
    PRESSURE_ABOVE_MAX_SPEED,
    /** Target below the head the string makes at its minimum-speed curve and no pressure control was configured. */
    PRESSURE_BELOW_MIN_SPEED,
    /** A body exceeded its installed driver power at the solved speed. */
    OVER_POWER,
    /** A body ran past its stonewall (maximum-flow) limit at the solved speed. */
    STONEWALL,
    /** A body ran below its surge (minimum-flow) limit at the solved speed. */
    SURGE,
    /** No compressors / no reference body configured. */
    NOT_CONFIGURED,
    /** No active member on the shaft: the shaft is not turning and no power is drawn. */
    SHAFT_IDLE,
    /** The suction-pressure or flow target is not bracketed by the speed bounds. */
    TARGET_OUT_OF_RANGE
  }

  /** Quantity of the controlling member that {@link #solveSpeedToTarget} holds by moving the common speed. */
  public enum ControlTarget {
    /** Discharge pressure of the controlling member. */
    DISCHARGE_PRESSURE,
    /** Suction (inlet) pressure of the controlling member. */
    SUCTION_PRESSURE,
    /** Inlet flow of the controlling member (unit given to the solve, e.g. "kg/hr" or "m3/hr"). */
    INLET_FLOW
  }

  /**
   * Pressure-control action for the "target below the minimum-speed capability" case, i.e. when the string makes more
   * head than requested even at minimum speed. Mirrors eCalc's pressure-control options. At this screening level all
   * non-{@code NONE} options net to delivering the requested pressure at minimum-speed power (the surplus head is
   * shed).
   */
  public enum PressureControl {
    /**
     * No pressure control: the too-low case is reported as infeasible ({@link SolveStatus#PRESSURE_BELOW_MIN_SPEED}).
     */
    NONE,
    /** A choke valve downstream of the string drops the surplus discharge to the requested pressure. */
    DOWNSTREAM_CHOKE,
    /** A choke valve upstream of the string lowers the suction so the discharge drops to the requested pressure. */
    UPSTREAM_CHOKE,
    /** Anti-surge recycle is opened to consume the surplus head. */
    ASV_RECYCLE
  }

  /**
   * Immutable outcome of a {@link #solveSpeed} call: whether the duty is feasible, why, the target / achieved / min- /
   * max-achievable discharge pressures, and the solved common speed. Never thrown — an infeasible duty is reported here
   * so a caller can gate on it instead of silently proceeding on a saturated speed.
   */
  public static class SolveResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    private final boolean feasible;
    private final SolveStatus status;
    private final double targetPressure;
    private final double achievedPressure;
    private final double minAchievablePressure;
    private final double maxAchievablePressure;
    private final double speed;
    private final String pressureUnit;
    private final String message;

    /**
     * Constructor.
     *
     * @param feasible whether the duty is feasible (or feasible with pressure control)
     * @param status the status / reason code
     * @param targetPressure the requested reference outlet pressure
     * @param achievedPressure the reference outlet pressure actually delivered
     * @param minAchievablePressure the reference outlet pressure at the minimum-speed bound
     * @param maxAchievablePressure the reference outlet pressure at the maximum-speed bound
     * @param speed the solved common shaft speed in rpm
     * @param pressureUnit the pressure unit for all pressures
     * @param message a human-readable summary
     */
    public SolveResult(boolean feasible, SolveStatus status, double targetPressure, double achievedPressure,
        double minAchievablePressure, double maxAchievablePressure, double speed, String pressureUnit, String message) {
      this.feasible = feasible;
      this.status = status;
      this.targetPressure = targetPressure;
      this.achievedPressure = achievedPressure;
      this.minAchievablePressure = minAchievablePressure;
      this.maxAchievablePressure = maxAchievablePressure;
      this.speed = speed;
      this.pressureUnit = pressureUnit;
      this.message = message;
    }

    /**
     * @return {@code true} if the target is reachable (possibly with pressure control)
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * @return the status / reason code
     */
    public SolveStatus getStatus() {
      return status;
    }

    /**
     * @return the requested reference outlet pressure
     */
    public double getTargetPressure() {
      return targetPressure;
    }

    /**
     * @return the reference outlet pressure actually delivered
     */
    public double getAchievedPressure() {
      return achievedPressure;
    }

    /**
     * @return the reference outlet pressure at the minimum-speed bound
     */
    public double getMinAchievablePressure() {
      return minAchievablePressure;
    }

    /**
     * @return the reference outlet pressure at the maximum-speed bound
     */
    public double getMaxAchievablePressure() {
      return maxAchievablePressure;
    }

    /**
     * @return the solved common shaft speed in rpm
     */
    public double getSpeed() {
      return speed;
    }

    /**
     * @return the pressure unit for all pressures in this result
     */
    public String getPressureUnit() {
      return pressureUnit;
    }

    /**
     * @return a human-readable summary of the solve outcome
     */
    public String getMessage() {
      return message;
    }

    /**
     * Serialize the result to a compact JSON object.
     *
     * @return a JSON string
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"feasible\":").append(feasible);
      sb.append(",\"status\":\"").append(status).append("\"");
      sb.append(",\"targetPressure\":").append(targetPressure);
      sb.append(",\"achievedPressure\":").append(achievedPressure);
      sb.append(",\"minAchievablePressure\":").append(minAchievablePressure);
      sb.append(",\"maxAchievablePressure\":").append(maxAchievablePressure);
      sb.append(",\"speed\":").append(speed);
      sb.append(",\"pressureUnit\":\"").append(pressureUnit).append("\"");
      sb.append(",\"message\":\"").append(message == null ? "" : message.replace("\"", "'")).append("\"}");
      return sb.toString();
    }
  }

  /**
   * Constructor.
   *
   * @param name the shaft name
   */
  public CompressorShaft(String name) {
    this.name = name;
  }

  /**
   * Get the pressure-control action used for a target below the minimum-speed capability.
   *
   * @return the configured pressure-control action
   */
  public PressureControl getPressureControl() {
    return pressureControl;
  }

  /**
   * Set the pressure-control action used when the requested pressure is below what the string makes at minimum speed.
   *
   * @param control the pressure-control action (must not be {@code null})
   */
  public void setPressureControl(PressureControl control) {
    if (control != null) {
      this.pressureControl = control;
    }
  }

  /**
   * Get the result of the most recent {@link #solveSpeed} call.
   *
   * @return the last solve result, or {@code null} if {@link #solveSpeed} has not been called
   */
  public SolveResult getLastSolveResult() {
    return lastResult;
  }

  /**
   * Whether the most recent {@link #solveSpeed} call reached a feasible operating point.
   *
   * @return {@code true} if no solve has run yet or the last solve was feasible; {@code false} otherwise
   */
  public boolean isFeasible() {
    return lastResult == null || lastResult.isFeasible();
  }

  /**
   * Add a compressor body to this shaft. Bodies should be added in flow order.
   *
   * @param compressor the compressor to add (must not be {@code null})
   */
  public void addCompressor(Compressor compressor) {
    if (compressor == null) {
      throw new IllegalArgumentException("CompressorShaft " + name + ": compressor must not be null.");
    }
    compressors.add(compressor);
    registerDrivenLoad(compressor);
  }

  /**
   * Add a compressor body behind a gearbox.
   *
   * @param compressor the compressor to add (must not be {@code null})
   * @param gearRatio member rpm per shaft rpm (positive); 1.0 for a direct-coupled body
   * @throws IllegalArgumentException if the compressor is null or the ratio is not positive
   */
  public void addCompressor(Compressor compressor, double gearRatio) {
    addCompressor(compressor);
    setGearRatio(compressor, gearRatio);
  }

  /**
   * Set the gear ratio of a member.
   *
   * @param compressor a member of this shaft
   * @param gearRatio member rpm per shaft rpm (positive)
   * @throws IllegalArgumentException if the ratio is not positive or the compressor is not on the shaft
   */
  public void setGearRatio(Compressor compressor, double gearRatio) {
    if (!(gearRatio > 0.0)) {
      throw new IllegalArgumentException("CompressorShaft " + name + ": gear ratio must be positive.");
    }
    requireMember(compressor);
    gearRatios.put(compressor, Double.valueOf(gearRatio));
  }

  /**
   * Get the gear ratio of a member.
   *
   * @param compressor a member of this shaft
   * @return member rpm per shaft rpm (1.0 when none was set)
   */
  public double getGearRatio(Compressor compressor) {
    Double r = gearRatios.get(compressor);
    return r == null ? 1.0 : r.doubleValue();
  }

  /**
   * Get the speed of a member at the current common speed.
   *
   * @param compressor a member of this shaft
   * @return member rpm, gear ratio times the common speed
   */
  public double getMemberSpeed(Compressor compressor) {
    return getGearRatio(compressor) * speed;
  }

  /**
   * Put a member in or out of service. An idle member (train off, parallel machine not running) is locked inactive so
   * the flowsheet does not run it, and it is excluded from the shaft power, the speed application and the limit checks.
   * The outlet stream of an idle compressor is not updated, so give an idle train a bypass line or zero flow in the
   * flowsheet.
   *
   * @param compressor a member of this shaft
   * @param active true for in service, false for idle
   * @throws IllegalArgumentException if the compressor is not on the shaft
   */
  public void setMemberActive(Compressor compressor, boolean active) {
    requireMember(compressor);
    if (active) {
      if (idleMembers.remove(compressor) != null) {
        compressor.setLockedInactive(false);
      }
    } else {
      idleMembers.put(compressor, Boolean.TRUE);
      compressor.setLockedInactive(true);
    }
  }

  /**
   * Whether a member is in service.
   *
   * @param compressor a member of this shaft
   * @return true if the member is not idle
   */
  public boolean isMemberActive(Compressor compressor) {
    return !idleMembers.containsKey(compressor);
  }

  /**
   * Get the members that are in service.
   *
   * @return list of active compressors in flow order
   */
  public List<Compressor> getActiveCompressors() {
    List<Compressor> active = new ArrayList<Compressor>();
    for (Compressor compressor : compressors) {
      if (isMemberActive(compressor)) {
        active.add(compressor);
      }
    }
    return active;
  }

  /**
   * Whether any member is in service, i.e. the shaft turns.
   *
   * @return true if at least one member is active
   */
  public boolean isTurning() {
    return !getActiveCompressors().isEmpty();
  }

  /**
   * Remove a member from the shaft, for example when a stage is decoupled onto its own driver in a later year. The
   * member is unlocked if it was idle, and unregistered from a gas-turbine driver's load list.
   *
   * @param compressor a member of this shaft
   * @throws IllegalArgumentException if the compressor is not on the shaft
   */
  public void removeCompressor(Compressor compressor) {
    requireMember(compressor);
    if (!isMemberActive(compressor)) {
      setMemberActive(compressor, true);
    }
    compressors.remove(compressor);
    gearRatios.remove(compressor);
    requiredDischargeBara.remove(compressor);
    antiSurgeValves.remove(compressor);
    antiSurgeControllers.remove(compressor);
    if (recycleAutoActivated.remove(compressor) != null) {
      compressor.getAntiSurge().setActive(false);
    }
    if (turbineDriver != null) {
      turbineDriver.getDrivenLoads().remove(compressor);
    }
  }

  /**
   * Check that a compressor is on this shaft.
   *
   * @param compressor the compressor to check
   * @throws IllegalArgumentException if it is null or not a member
   */
  private void requireMember(Compressor compressor) {
    for (Compressor member : compressors) {
      if (member == compressor) {
        return;
      }
    }
    throw new IllegalArgumentException("CompressorShaft " + name + ": compressor is not a member of this shaft.");
  }

  /**
   * Set an explicit installed shaft power limit for the whole string (all bodies plus gearbox losses). This is the
   * simplest way to get a shaft utilization: no driver model is needed.
   *
   * @param maxPower the installed shaft power limit (0 or negative clears the limit)
   * @param unit power unit: "W", "kW" or "MW"
   */
  public void setMaxShaftPower(double maxPower, String unit) {
    this.maxShaftPowerW = maxPower > 0.0 ? maxPower * powerFactor(unit) : 0.0;
  }

  /**
   * Get the explicit installed shaft power limit.
   *
   * @param unit power unit: "W", "kW" or "MW"
   * @return the configured shaft power limit, or 0 if none was set
   */
  public double getMaxShaftPower(String unit) {
    return maxShaftPowerW / powerFactor(unit);
  }

  /**
   * Set the gearbox / coupling efficiency between the driver output and the compressor bodies. The driver must deliver
   * the sum of the body powers divided by this efficiency.
   *
   * @param efficiency the mechanical efficiency in the range (0, 1]
   */
  public void setMechanicalEfficiency(double efficiency) {
    if (efficiency <= 0.0 || efficiency > 1.0) {
      throw new IllegalArgumentException("mechanical efficiency must be in the range (0, 1]");
    }
    this.mechanicalEfficiency = efficiency;
  }

  /**
   * Get the gearbox / coupling efficiency.
   *
   * @return the mechanical efficiency
   */
  public double getMechanicalEfficiency() {
    return mechanicalEfficiency;
  }

  /**
   * Attach a driver model (electric motor or gas turbine) whose speed-dependent and ambient-derated power curve limits
   * the shaft. Used when no explicit {@link #setMaxShaftPower(double, String)} limit is configured.
   *
   * @param driver the driver model, or {@code null} to detach
   */
  public void setDriver(CompressorDriver driver) {
    this.driver = driver;
  }

  /**
   * Get the attached driver model.
   *
   * @return the driver model, or {@code null}
   */
  public CompressorDriver getDriver() {
    return driver;
  }

  /**
   * Attach a {@link GasTurbine} as the driver of this shaft. Every body already on the shaft (and every body added
   * later) is registered as a driven load on the turbine, so the turbine's fuel gas and its own power utilization track
   * the string duty. The turbine's rated power also becomes the shaft power limit when no explicit
   * {@link #setMaxShaftPower(double, String)} limit is set.
   *
   * @param turbine the driving gas turbine, or {@code null} to detach
   */
  public void setTurbineDriver(GasTurbine turbine) {
    this.turbineDriver = turbine;
    if (turbine != null) {
      for (Compressor compressor : compressors) {
        registerDrivenLoad(compressor);
      }
    }
  }

  /**
   * Get the gas turbine driving this shaft.
   *
   * @return the driving gas turbine, or {@code null}
   */
  public GasTurbine getTurbineDriver() {
    return turbineDriver;
  }

  /**
   * Register a body as a driven load on the turbine driver (no-op when no turbine is attached or the body is already
   * registered, so the aggregated load is never double counted).
   *
   * @param compressor the body to register
   */
  private void registerDrivenLoad(Compressor compressor) {
    if (turbineDriver != null && !turbineDriver.getDrivenLoads().contains(compressor)) {
      turbineDriver.addDrivenLoad(compressor);
    }
  }

  /**
   * Get the shaft power the driver must deliver: the sum of the body powers divided by the mechanical (gearbox)
   * efficiency.
   *
   * @param unit power unit: "W", "kW" or "MW"
   * @return the required driver power in the requested unit
   */
  public double getRequiredDriverPower(String unit) {
    return getTotalPower() / mechanicalEfficiency / powerFactor(unit);
  }

  /**
   * Get the power the driver can deliver at the current shaft speed and ambient conditions.
   *
   * <p>
   * Resolution order: an explicit {@link #setMaxShaftPower(double, String)} limit wins, then a {@link CompressorDriver}
   * curve evaluated at the current speed (ambient-derated for a gas-turbine driver), then a driver curve or electric
   * motor ({@link #setDriverCurve(DriverCurve)}), then the rated power of an attached {@link GasTurbine}.
   * </p>
   *
   * @param unit power unit: "W", "kW" or "MW"
   * @return the available shaft power in the requested unit, or 0 if no limit is defined
   */
  public double getAvailableShaftPower(String unit) {
    double availableW = 0.0;
    if (maxShaftPowerW > 0.0) {
      availableW = maxShaftPowerW;
    } else if (driver != null && driver.getMaxAvailablePowerAtSpeed(speed) > 0.0) {
      availableW = driver.getMaxAvailablePowerAtSpeed(speed) * 1.0e3;
    } else if (driverCurvePowerW() > 0.0) {
      availableW = driverCurvePowerW();
    } else if (turbineDriver != null && turbineDriver.getRatedPower() > 0.0) {
      availableW = turbineDriver.getRatedPower();
    } else if (gasTurbineUnitDriver != null) {
      availableW = gasTurbineUnitDriver.getAvailablePowerAtAmbient(ambientTemperatureK);
    }
    if (supplyShaftPowerLimitW > 0.0) {
      availableW = availableW > 0.0 ? Math.min(availableW, supplyShaftPowerLimitW) : supplyShaftPowerLimitW;
    }
    return availableW / powerFactor(unit);
  }

  /**
   * Power the driver curve (or the electric motor with its environmental derating) can deliver at the common speed.
   *
   * @return power in W, 0 when no curve is attached or the speed is outside the curve
   */
  private double driverCurvePowerW() {
    if (driverCurve == null || !(speed > 0.0)) {
      return 0.0;
    }
    double w;
    if (motorDriverUnit != null && motorDriverUnit.hasPerformanceModel()) {
      motorDriverUnit.setOperatingSpeed(speed);
      w = motorDriverUnit.getAvailableShaftPower();
    } else {
      w = driverCurve.getAvailablePower(speed) * 1.0e3;
    }
    return Double.isFinite(w) && w > 0.0 ? w : 0.0;
  }

  /**
   * Attach a site-rated catalogue gas turbine as the driver. Its available power follows the shaft ambient temperature
   * ({@link #setAmbientTemperature(double, String)}), including the vendor site-rating table and the exhaust
   * temperature limit of the unit. After a solve, {@link #updateDriverOperatingPoint()} runs the unit at the shaft
   * power demand to give fuel, exhaust and waste-heat figures.
   *
   * @param unit the gas turbine unit, or {@code null} to detach
   */
  public void setGasTurbineUnitDriver(GasTurbineUnit unit) {
    this.gasTurbineUnitDriver = unit;
    if (unit != null) {
      unit.setAmbientTemperatureK(ambientTemperatureK);
    }
  }

  /**
   * Get the site-rated gas turbine unit driving this shaft.
   *
   * @return the unit, or {@code null}
   */
  public GasTurbineUnit getGasTurbineUnitDriver() {
    return gasTurbineUnitDriver;
  }

  /**
   * Set the ambient air temperature seen by the driver; it moves the power of an attached {@link GasTurbineUnit} and of
   * a {@link CompressorDriver}.
   *
   * @param temperature the ambient temperature
   * @param unit "C" or "K"
   * @throws IllegalArgumentException if the unit is not "C" or "K"
   */
  public void setAmbientTemperature(double temperature, String unit) {
    if ("C".equals(unit)) {
      this.ambientTemperatureK = temperature + 273.15;
    } else if ("K".equals(unit)) {
      this.ambientTemperatureK = temperature;
    } else {
      throw new IllegalArgumentException("unsupported temperature unit '" + unit + "' (use C or K).");
    }
    if (gasTurbineUnitDriver != null) {
      gasTurbineUnitDriver.setAmbientTemperatureK(ambientTemperatureK);
    }
    if (driver != null) {
      driver.setAmbientTemperature(ambientTemperatureK);
    }
  }

  /**
   * Get the ambient air temperature used for the driver rating.
   *
   * @param unit "C" or "K"
   * @return the ambient temperature
   */
  public double getAmbientTemperature(String unit) {
    return "C".equals(unit) ? ambientTemperatureK - 273.15 : ambientTemperatureK;
  }

  /**
   * Cap the shaft power by an electrical supply: the supply power times the motor/drive efficiency. Use it for an
   * electric motor driver limited by power-from-shore or generator capacity. The cap combines with the other limits by
   * taking the smaller one.
   *
   * @param supplyPower the electrical power available to the drive (0 or negative clears the cap)
   * @param unit "W", "kW" or "MW"
   * @param drivetrainEfficiency electrical-to-shaft efficiency in (0, 1]
   * @throws IllegalArgumentException if the efficiency is outside (0, 1]
   */
  public void setElectricSupplyLimit(double supplyPower, String unit, double drivetrainEfficiency) {
    if (!(drivetrainEfficiency > 0.0 && drivetrainEfficiency <= 1.0)) {
      throw new IllegalArgumentException("drivetrain efficiency must be in the range (0, 1]");
    }
    this.supplyShaftPowerLimitW = supplyPower > 0.0 ? supplyPower * powerFactor(unit) * drivetrainEfficiency : 0.0;
  }

  /**
   * Run the attached {@link GasTurbineUnit} at the present shaft power demand so that its fuel flow, exhaust flow and
   * temperature, waste-heat duty and emissions describe the solved operating point. No-op without such a driver.
   */
  public void updateDriverOperatingPoint() {
    if (gasTurbineUnitDriver != null) {
      gasTurbineUnitDriver.setAmbientTemperatureK(ambientTemperatureK);
      gasTurbineUnitDriver.setDemandedPower(getRequiredDriverPower("W"));
      gasTurbineUnitDriver.run();
    }
  }

  /**
   * Get the shaft power utilization: required driver power divided by available driver power.
   *
   * @return the utilization as a fraction (1.0 = at the limit), or 0 when no power limit is configured
   */
  public double getPowerUtilization() {
    double available = getAvailableShaftPower("W");
    if (available <= 0.0) {
      return 0.0;
    }
    double required = getRequiredDriverPower("W");
    if (Double.isNaN(required) || required <= 0.0) {
      return 0.0;
    }
    return required / available;
  }

  /**
   * Whether the string draws more power than the driver can deliver at the current speed.
   *
   * @return {@code true} when a power limit is configured and exceeded
   */
  public boolean isOverPower() {
    return getPowerUtilization() > 1.0;
  }

  /**
   * Convert a power unit to its factor relative to watts.
   *
   * @param unit power unit: "W", "kW" or "MW"
   * @return the number of watts per unit
   */
  private static double powerFactor(String unit) {
    if ("kW".equals(unit)) {
      return 1.0e3;
    }
    if ("MW".equals(unit)) {
      return 1.0e6;
    }
    if ("W".equals(unit) || unit == null) {
      return 1.0;
    }
    throw new IllegalArgumentException("unsupported power unit '" + unit + "' (use W, kW or MW).");
  }

  /**
   * Get the compressor bodies on this shaft.
   *
   * @return an unmodifiable list of the compressors
   */
  public List<Compressor> getCompressors() {
    return Collections.unmodifiableList(compressors);
  }

  /**
   * Get the common shaft speed.
   *
   * @return the shaft speed in rpm
   */
  public double getSpeed() {
    return speed;
  }

  /**
   * Set the common shaft speed and apply it to every body (fixed-speed, chart-based mode).
   *
   * @param shaftSpeed the common shaft speed in rpm
   */
  public void setSpeed(double shaftSpeed) {
    this.speed = shaftSpeed;
    applySpeed();
  }

  /**
   * Set the speed search bounds used by {@link #solveSpeed}. An explicit call overrides a speed band derived from a
   * driver ({@link #setDriverCurve(DriverCurve)}, {@link #setMotorDriverUnit(ElectricMotor)}).
   *
   * @param min the lower speed bound in rpm
   * @param max the upper speed bound in rpm
   */
  public void setSpeedBounds(double min, double max) {
    this.minSpeed = min;
    this.maxSpeed = max;
    this.speedBandFromDriver = false;
  }

  /**
   * Whether the speed bounds follow a driver (VFD range) attached to the shaft.
   *
   * @return true while the bounds are derived from the driver
   */
  public boolean isSpeedBandFromDriver() {
    return speedBandFromDriver;
  }

  /**
   * Get the lower speed bound in force.
   *
   * @return the lower bound in rpm
   */
  public double getMinSpeed() {
    return minSpeed;
  }

  /**
   * Get the upper speed bound in force.
   *
   * @return the upper bound in rpm
   */
  public double getMaxSpeed() {
    return maxSpeed;
  }

  /**
   * Attach a driver performance curve and take the speed band from it. For an electric motor with a variable-frequency
   * drive this is the VFD range (minimum speed ratio to maximum speed ratio times the rated speed, including a cell
   * bypass derating of the drive); for a fixed-speed motor it is the narrow band around the rated speed. The band is
   * re-read at the start of every speed solve, so a change of the drive configuration is picked up. The curve also
   * supplies the available shaft power at the common speed when no explicit limit, {@link CompressorDriver} or turbine
   * is configured. The driver speed is the shaft speed: member speeds follow from the gear ratios.
   *
   * @param curve the driver performance curve, or {@code null} to detach it (the speed bounds stay as they are)
   */
  public void setDriverCurve(DriverCurve curve) {
    this.driverCurve = curve;
    if (curve == null) {
      this.speedBandFromDriver = false;
      return;
    }
    this.speedBandFromDriver = true;
    refreshSpeedBandFromDriver();
  }

  /**
   * Get the driver performance curve.
   *
   * @return the curve, or {@code null}
   */
  public DriverCurve getDriverCurve() {
    return driverCurve;
  }

  /**
   * Attach an electric motor process unit as the shaft driver. Its performance model (for example an
   * {@code ElectricMotorDriver} with VFD data) becomes the driver curve, so the speed band is derived from the VFD
   * automatically, and the available shaft power is evaluated at the common speed including the environmental derating
   * of the motor. A motor without a performance model gives no speed band and no power limit.
   *
   * @param motor the motor, or {@code null} to detach it
   */
  public void setMotorDriverUnit(ElectricMotor motor) {
    this.motorDriverUnit = motor;
    if (motor != null && motor.hasPerformanceModel()) {
      setDriverCurve(motor.getPerformanceModel());
    } else if (motor == null) {
      setDriverCurve(null);
    }
  }

  /**
   * Get the electric motor driving this shaft.
   *
   * @return the motor, or {@code null}
   */
  public ElectricMotor getMotorDriverUnit() {
    return motorDriverUnit;
  }

  /**
   * Re-read the speed bounds from the driver curve. Does nothing when no curve is attached or after an explicit
   * {@link #setSpeedBounds(double, double)}. A non-positive minimum speed of the curve keeps the present lower bound; a
   * curve with a maximum below the minimum is ignored.
   */
  public void refreshSpeedBandFromDriver() {
    if (driverCurve == null || !speedBandFromDriver) {
      return;
    }
    double lo = driverCurve.getMinSpeed();
    double hi = driverCurve.getMaxSpeed();
    if (!Double.isFinite(hi) || hi <= 0.0) {
      return;
    }
    if (Double.isFinite(lo) && lo > 0.0 && lo > hi) {
      return;
    }
    if (Double.isFinite(lo) && lo > 0.0) {
      minSpeed = lo;
    }
    maxSpeed = hi;
  }

  /**
   * Set the maximum number of root-finding iterations for {@link #solveSpeed}.
   *
   * @param iterations the maximum number of iterations
   */
  public void setMaxIterations(int iterations) {
    this.maxIterations = iterations;
  }

  /**
   * Set the convergence tolerance on the reference outlet pressure.
   *
   * @param toleranceBar the tolerance in bar
   */
  public void setPressureTolerance(double toleranceBar) {
    this.pressureToleranceBar = toleranceBar;
  }

  /**
   * Put every body into fixed-speed, chart-based mode at the current common speed. Each body then produces its
   * discharge pressure from its performance chart at the shared speed (intermediate pressures float).
   */
  public void applySpeed() {
    for (Compressor compressor : compressors) {
      if (!isMemberActive(compressor)) {
        continue;
      }
      compressor.setSolveSpeed(false);
      compressor.setSpeed(speed * getGearRatio(compressor));
      if (compressor.getCompressorChart() != null) {
        compressor.getCompressorChart().setUseCompressorChart(true);
      }
      compressor.setUsePolytropicCalc(true);
    }
  }

  /**
   * Solve the single common shaft speed so that, after re-running the flowsheet, the reference body's outlet pressure
   * equals the target. Uses a bracketed false-position (Illinois) secant on speed; higher speed gives higher head and
   * therefore higher discharge pressure. Intermediate inter-body pressures float.
   *
   * @param reference the body whose outlet pressure is the controlled target (usually the last)
   * @param targetOutletPressure the required outlet pressure of the reference body
   * @param unit the pressure unit (e.g. "bara")
   * @param processRun a callback that re-solves the surrounding flowsheet (so inter-body streams, scrubbers and mixers
   * update between speed guesses); may be {@code null} to just run the member compressors in series
   * @return the solved common shaft speed in rpm
   */
  public double solveSpeed(Compressor reference, double targetOutletPressure, String unit, Runnable processRun) {
    if (compressors.isEmpty()) {
      throw new RuntimeException("CompressorShaft " + name + ": no compressors on the shaft.");
    }
    refreshSpeedBandFromDriver();
    if (!isTurning()) {
      return recordIdle("solveSpeed");
    }
    if (reference == null || !isMemberActive(reference)) {
      List<Compressor> active = getActiveCompressors();
      reference = active.get(active.size() - 1);
    }
    // Bracket the root at the speed bounds. Discharge pressure rises monotonically with speed,
    // so we expect fa < 0 < fb; then converge with a false-position (Illinois) secant, which is
    // superlinear on this smooth map and needs far fewer flowsheet solves than bisection. The
    // two bracket evaluations also give the min- and max-achievable discharge pressure for free.
    double a = minSpeed;
    double b = maxSpeed;
    double fa = evalOutletError(a, reference, targetOutletPressure, unit, processRun);
    double minAch = fa + targetOutletPressure;
    if (Math.abs(fa) < pressureToleranceBar) {
      double solved = finalizeSpeed(a, processRun);
      return record(SolveStatus.FEASIBLE, true, targetOutletPressure, reference.getOutletStream().getPressure(unit),
          minAch, Double.NaN, solved, unit, "target reached at the minimum-speed bound");
    }
    double fb = evalOutletError(b, reference, targetOutletPressure, unit, processRun);
    double maxAch = fb + targetOutletPressure;
    if (Math.abs(fb) < pressureToleranceBar) {
      double solved = finalizeSpeed(b, processRun);
      return record(SolveStatus.FEASIBLE, true, targetOutletPressure, reference.getOutletStream().getPressure(unit),
          minAch, maxAch, solved, unit, "target reached at the maximum-speed bound");
    }
    if (fa * fb > 0.0) {
      if (fa > 0.0) {
        // Target below the minimum-speed capability: the string overshoots even at min speed.
        double solved = finalizeSpeed(a, processRun);
        if (pressureControl != PressureControl.NONE) {
          applyPressureControl(reference, targetOutletPressure, unit, processRun);
          logger.info(
              "CompressorShaft {}: target {} {} below min-speed capability ({} {}); met by {} (pressure control)", name,
              targetOutletPressure, unit, minAch, unit, pressureControl);
          return record(SolveStatus.PRESSURE_CONTROLLED, true, targetOutletPressure,
              reference.getOutletStream().getPressure(unit), minAch, maxAch, solved, unit,
              "surplus head shed by " + pressureControl);
        }
        logger.warn(
            "CompressorShaft {}: target {} {} is BELOW the minimum-speed discharge ({} {}); "
                + "saturating at min speed. Set a PressureControl to shed the surplus head.",
            name, targetOutletPressure, unit, minAch, unit);
        return record(SolveStatus.PRESSURE_BELOW_MIN_SPEED, false, targetOutletPressure, minAch, minAch, maxAch, solved,
            unit, "target below minimum-speed capability; no pressure control");
      }
      // Target above the maximum-speed capability: genuinely infeasible.
      double solved = finalizeSpeed(b, processRun);
      logger.warn("CompressorShaft {}: target {} {} is ABOVE the maximum-speed discharge ({} {}); "
          + "saturating at max speed. Duty is infeasible.", name, targetOutletPressure, unit, maxAch, unit);
      return record(SolveStatus.PRESSURE_ABOVE_MAX_SPEED, false, targetOutletPressure, maxAch, minAch, maxAch, solved,
          unit, "target above maximum-speed capability");
    }
    double best = 0.5 * (a + b);
    for (int i = 0; i < maxIterations; i++) {
      // False-position (secant within the current bracket).
      double x = b - fb * (b - a) / (fb - fa);
      double xlo = Math.min(a, b);
      double xhi = Math.max(a, b);
      if (!(x > xlo && x < xhi)) {
        x = 0.5 * (a + b); // guard: fall back to bisection if the secant leaves the bracket
      }
      double fx = evalOutletError(x, reference, targetOutletPressure, unit, processRun);
      best = x;
      if (Math.abs(fx) < pressureToleranceBar) {
        break;
      }
      // Update the bracket; halve the retained endpoint's value (Illinois) to avoid stalling.
      if (fx * fa < 0.0) {
        b = x;
        fb = fx;
        fa *= 0.5;
      } else {
        a = x;
        fa = fx;
        fb *= 0.5;
      }
    }
    double solved = finalizeSpeed(best, processRun);
    double achieved = reference.getOutletStream().getPressure(unit);
    // The target is bracketed and reached; still flag if a body ended up over power / past a chart edge.
    SolveStatus limit = checkBodyLimits();
    boolean feasible = limit == SolveStatus.FEASIBLE;
    return record(limit, feasible, targetOutletPressure, achieved, minAch, maxAch, solved, unit,
        feasible ? "target reached by adjusting the common speed"
            : "target reached but a body hit a " + limit + " limit");
  }

  /**
   * Store the solve result and return the solved speed.
   *
   * @param status the status / reason code
   * @param feasible whether the duty is feasible
   * @param target the requested reference outlet pressure
   * @param achieved the reference outlet pressure actually delivered
   * @param minAch the reference outlet pressure at the minimum-speed bound
   * @param maxAch the reference outlet pressure at the maximum-speed bound
   * @param solvedSpeed the solved common shaft speed in rpm
   * @param unit the pressure unit
   * @param message a human-readable summary
   * @return the solved common shaft speed in rpm
   */
  private double record(SolveStatus status, boolean feasible, double target, double achieved, double minAch,
      double maxAch, double solvedSpeed, String unit, String message) {
    this.lastResult = new SolveResult(feasible, status, target, achieved, minAch, maxAch, solvedSpeed, unit, message);
    return solvedSpeed;
  }

  /**
   * Apply the configured pressure-control action to deliver the requested pressure when the string overshoots at
   * minimum speed. Screening representation: the reference discharge is set to the target (the surplus head is shed);
   * the compressor power stays at its minimum-speed value.
   *
   * @param reference the reference body
   * @param targetOutletPressure the requested outlet pressure
   * @param unit the pressure unit
   * @param processRun the flowsheet re-run callback, or {@code null}
   */
  private void applyPressureControl(Compressor reference, double targetOutletPressure, String unit,
      Runnable processRun) {
    try {
      reference.getOutletStream().setPressure(targetOutletPressure, unit);
      reference.getOutletStream().run();
    } catch (RuntimeException ex) {
      logger.warn("CompressorShaft {}: pressure control could not set the reference outlet: {}", name, ex.getMessage());
    }
  }

  /**
   * Check every charted body for a surge / stonewall / over-power condition at the solved speed.
   *
   * @return the first limit hit, or {@link SolveStatus#FEASIBLE} if all bodies are within limits
   */
  private SolveStatus checkBodyLimits() {
    if (isOverPower()) {
      return SolveStatus.OVER_POWER;
    }
    for (Compressor compressor : compressors) {
      if (!isMemberActive(compressor) || compressor.getCompressorChart() == null) {
        continue;
      }
      try {
        if (compressor.isSurge()) {
          return SolveStatus.SURGE;
        }
      } catch (RuntimeException ex) {
        // chart edge / undefined surge distance — treat as no surge flag
      }
      try {
        if (compressor.isStoneWall()) {
          return SolveStatus.STONEWALL;
        }
      } catch (RuntimeException ex) {
        // chart edge — treat as no stonewall flag
      }
      try {
        double maxPower = (compressor.getMechanicalDesign() != null) ? compressor.getMechanicalDesign().maxDesignPower
            : 0.0;
        if (maxPower > 0.0 && compressor.getPower("kW") > maxPower) {
          return SolveStatus.OVER_POWER;
        }
      } catch (RuntimeException ex) {
        // power/mechanical design unavailable — skip the power check
      }
    }
    return SolveStatus.FEASIBLE;
  }

  /**
   * Apply a trial common speed, re-solve, and return the reference body's outlet-pressure error (actual minus target).
   *
   * @param trialSpeed the trial common shaft speed in rpm
   * @param reference the reference body
   * @param targetOutletPressure the target outlet pressure
   * @param unit the pressure unit
   * @param processRun the flowsheet re-run callback, or {@code null}
   * @return the outlet-pressure error in the given unit
   */
  private double evalOutletError(double trialSpeed, Compressor reference, double targetOutletPressure, String unit,
      Runnable processRun) {
    this.speed = trialSpeed;
    applySpeed();
    runOnce(processRun);
    return reference.getOutletStream().getPressure(unit) - targetOutletPressure;
  }

  /**
   * Set the solved common speed, re-solve once, and return it.
   *
   * @param solvedSpeed the solved common shaft speed in rpm
   * @param processRun the flowsheet re-run callback, or {@code null}
   * @return the solved common shaft speed in rpm
   */
  private double finalizeSpeed(double solvedSpeed, Runnable processRun) {
    this.speed = solvedSpeed;
    applySpeed();
    runOnce(processRun);
    updateDriverOperatingPoint();
    logger.info("CompressorShaft {} solved common speed {} rpm", name, solvedSpeed);
    return solvedSpeed;
  }

  /**
   * Run every body at a locked, fixed shaft speed with NO speed iteration. This is the correct mode for a
   * constant-speed driver (electric motor at line frequency, no variable-speed drive): the shaft cannot change rpm, so
   * each body's discharge pressure floats off its chart at the fixed speed and any pressure spec must be met by
   * anti-surge recycle, suction throttling or IGVs rather than by moving speed.
   *
   * @param fixedShaftSpeed the locked shaft speed in rpm
   * @param processRun a callback that re-solves the surrounding flowsheet, or {@code null} to run the member
   * compressors in series
   */
  public void runAtFixedSpeed(double fixedShaftSpeed, Runnable processRun) {
    this.speed = fixedShaftSpeed;
    applySpeed();
    runOnce(processRun);
    logger.info("CompressorShaft {} running at fixed speed {} rpm", name, fixedShaftSpeed);
  }

  /**
   * Re-solve the flowsheet (or the member compressors in series if no callback is supplied).
   *
   * @param processRun the flowsheet re-run callback, or {@code null}
   */
  private void runOnce(Runnable processRun) {
    if (processRun != null) {
      processRun.run();
    } else {
      for (Compressor compressor : compressors) {
        if (isMemberActive(compressor)) {
          compressor.run();
        }
      }
    }
  }

  /**
   * Get the total shaft power (sum of the body powers).
   *
   * @return the total shaft power in watts (W)
   */
  public double getTotalPower() {
    double sum = 0.0;
    for (Compressor compressor : compressors) {
      if (isMemberActive(compressor)) {
        sum += compressor.getPower();
      }
    }
    return sum;
  }

  /**
   * Get the total shaft power (sum of the body powers) in the requested unit.
   *
   * @param unit power unit: "W", "kW" or "MW"
   * @return the total shaft power in the requested unit
   */
  public double getTotalPower(String unit) {
    return getTotalPower() / powerFactor(unit);
  }

  /** Process discharge-pressure need per member in bara, used for the report margins. */
  private final Map<Compressor, Double> requiredDischargeBara = new IdentityHashMap<Compressor, Double>();

  /**
   * Set the discharge pressure the process needs from a member (for example the next stage's suction requirement). The
   * report then shows the margin of the chart-delivered discharge pressure over this need: a negative margin means the
   * common speed is too low for the member, a clearly positive margin means excess head to be throttled or recycled.
   *
   * @param compressor a member of this shaft
   * @param pressure required discharge pressure
   * @param unit pressure unit, e.g. "bara"
   * @throws IllegalArgumentException if the compressor is not on the shaft
   */
  public void setMemberRequiredDischargePressure(Compressor compressor, double pressure, String unit) {
    requireMember(compressor);
    requiredDischargeBara.put(compressor, Double.valueOf(convertToBara(pressure, unit)));
  }

  /**
   * Convert a pressure to bara.
   *
   * @param pressure pressure value
   * @param unit "bara", "barg", "Pa", "kPa", "MPa" or "psia"
   * @return pressure in bara
   * @throws IllegalArgumentException if the unit is not supported
   */
  private static double convertToBara(double pressure, String unit) {
    if (unit == null || "bara".equals(unit) || "bar".equals(unit)) {
      return pressure;
    } else if ("barg".equals(unit)) {
      return pressure + 1.01325;
    } else if ("Pa".equals(unit)) {
      return pressure / 1.0e5;
    } else if ("kPa".equals(unit)) {
      return pressure / 100.0;
    } else if ("MPa".equals(unit)) {
      return pressure * 10.0;
    } else if ("psia".equals(unit)) {
      return pressure * 0.0689476;
    }
    throw new IllegalArgumentException("unsupported pressure unit '" + unit + "'.");
  }

  /**
   * Record the outcome for a shaft with no active member.
   *
   * @param what name of the calling solve, for the message
   * @return 0 rpm (the shaft is not turning)
   */
  private double recordIdle(String what) {
    this.speed = 0.0;
    this.lastResult = new SolveResult(true, SolveStatus.SHAFT_IDLE, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0,
        "bara", what + ": no active member, shaft not turning");
    return 0.0;
  }

  /**
   * Solve the common speed so that one controlling member holds a target, with every other member floating on its chart
   * at the same speed (times its gear ratio). This is the "controlling stage sets the speed" pattern: the member whose
   * process flow and discharge pressure are fixed sets the shaft speed, the other members follow that speed and their
   * surplus or deficit is read from {@link #getOperatingReport()}.
   *
   * <p>
   * {@link ControlTarget#DISCHARGE_PRESSURE} is {@link #solveSpeed}. For {@link ControlTarget#SUCTION_PRESSURE} and
   * {@link ControlTarget#INLET_FLOW} the speed is bracketed on the speed bounds and found by false position; the
   * direction of the response is not assumed, but the target must lie between the values at the two bounds, otherwise
   * the status is {@link SolveStatus#TARGET_OUT_OF_RANGE} and the speed saturates at the closer bound. The result
   * fields of {@link SolveResult} carry the controlled quantity in the given unit.
   * </p>
   *
   * @param controlling the controlling member (null selects the last active member)
   * @param target the quantity to hold
   * @param value the target value
   * @param unit pressure unit for the pressure targets, flow unit (e.g. "kg/hr", "m3/hr") for the flow target
   * @param processRun callback re-solving the flowsheet between speed guesses, or {@code null}
   * @return the solved common shaft speed in rpm
   */
  public double solveSpeedToTarget(Compressor controlling, ControlTarget target, double value, String unit,
      Runnable processRun) {
    if (target == null || target == ControlTarget.DISCHARGE_PRESSURE) {
      return solveSpeed(controlling, value, unit, processRun);
    }
    refreshSpeedBandFromDriver();
    if (compressors.isEmpty()) {
      throw new RuntimeException("CompressorShaft " + name + ": no compressors on the shaft.");
    }
    if (!isTurning()) {
      return recordIdle("solveSpeedToTarget");
    }
    final Compressor ctl;
    if (controlling == null || !isMemberActive(controlling)) {
      List<Compressor> active = getActiveCompressors();
      ctl = active.get(active.size() - 1);
    } else {
      ctl = controlling;
    }
    final ControlTarget kind = target;
    final String u = unit;
    final Runnable run = processRun;
    final double goal = value;
    DoubleUnaryOperator error = new DoubleUnaryOperator() {
      @Override
      public double applyAsDouble(double trialSpeed) {
        speed = trialSpeed;
        applySpeed();
        runOnce(run);
        double measured = kind == ControlTarget.SUCTION_PRESSURE ? ctl.getInletStream().getPressure(u)
            : ctl.getInletStream().getFlowRate(u);
        return measured - goal;
      }
    };
    double tol = kind == ControlTarget.SUCTION_PRESSURE ? pressureToleranceBar : Math.max(1e-9, 1e-4 * Math.abs(goal));
    double a = minSpeed;
    double b = maxSpeed;
    double fa = error.applyAsDouble(a);
    if (Math.abs(fa) < tol) {
      double solved = finalizeSpeed(a, processRun);
      return record(checkBodyLimits(), isFeasibleAfterLimits(), goal, goal + fa, goal + fa, Double.NaN, solved, unit,
          "target reached at the minimum-speed bound");
    }
    double fb = error.applyAsDouble(b);
    if (Math.abs(fb) < tol) {
      double solved = finalizeSpeed(b, processRun);
      return record(checkBodyLimits(), isFeasibleAfterLimits(), goal, goal + fb, goal + fa, goal + fb, solved, unit,
          "target reached at the maximum-speed bound");
    }
    if (fa * fb > 0.0) {
      boolean lowerIsCloser = Math.abs(fa) < Math.abs(fb);
      double solved = finalizeSpeed(lowerIsCloser ? a : b, processRun);
      logger.warn("CompressorShaft {}: {} target {} {} is not bracketed by the speed bounds ({} .. {})", name, kind,
          goal, unit, goal + fa, goal + fb);
      return record(SolveStatus.TARGET_OUT_OF_RANGE, false, goal, goal + (lowerIsCloser ? fa : fb), goal + fa,
          goal + fb, solved, unit, kind + " target is outside the range reachable by the speed bounds");
    }
    double best = 0.5 * (a + b);
    for (int i = 0; i < maxIterations; i++) {
      double x = b - fb * (b - a) / (fb - fa);
      if (!(x > Math.min(a, b) && x < Math.max(a, b))) {
        x = 0.5 * (a + b);
      }
      double fx = error.applyAsDouble(x);
      best = x;
      if (Math.abs(fx) < tol) {
        break;
      }
      if (fx * fa < 0.0) {
        b = x;
        fb = fx;
        fa *= 0.5;
      } else {
        a = x;
        fa = fx;
        fb *= 0.5;
      }
    }
    double solved = finalizeSpeed(best, processRun);
    double achieved = kind == ControlTarget.SUCTION_PRESSURE ? ctl.getInletStream().getPressure(unit)
        : ctl.getInletStream().getFlowRate(unit);
    SolveStatus limit = checkBodyLimits();
    return record(limit, limit == SolveStatus.FEASIBLE, goal, achieved, Double.NaN, Double.NaN, solved, unit,
        limit == SolveStatus.FEASIBLE ? kind + " target reached by adjusting the common speed"
            : kind + " target reached but a body hit a " + limit + " limit");
  }

  /**
   * Whether the body limits are all inside after the last run.
   *
   * @return true if no surge, stonewall or over-power was detected
   */
  private boolean isFeasibleAfterLimits() {
    return checkBodyLimits() == SolveStatus.FEASIBLE;
  }

  /**
   * Find the highest common speed the driver can hold: the speed at which the required driver power equals the power
   * available at the shaft ambient temperature (the speed bounds limit the search). Required power rises with speed, so
   * a bisection on the power balance is used. The flowsheet is left at the returned speed. The result fields of
   * {@link SolveResult} carry the available driver power (target) and the required driver power (achieved) in W.
   *
   * @param processRun callback re-solving the flowsheet between speed guesses, or {@code null}
   * @return the highest feasible common speed in rpm, the minimum speed if even that overloads the driver, the maximum
   * speed if the driver is never limiting or has no configured limit, and 0 for an idle shaft
   */
  public double findMaxSpeedWithinPowerLimit(Runnable processRun) {
    if (compressors.isEmpty()) {
      throw new RuntimeException("CompressorShaft " + name + ": no compressors on the shaft.");
    }
    refreshSpeedBandFromDriver();
    if (!isTurning()) {
      return recordIdle("findMaxSpeedWithinPowerLimit");
    }
    double lo = minSpeed;
    double hi = maxSpeed;
    double gLo = powerMargin(lo, processRun);
    if (gLo < 0.0) {
      double solved = finalizeSpeed(lo, processRun);
      this.lastResult = new SolveResult(false, SolveStatus.OVER_POWER, getAvailableShaftPower("W"),
          getRequiredDriverPower("W"), Double.NaN, Double.NaN, solved, "W",
          "driver power is exceeded even at the minimum speed");
      return solved;
    }
    double gHi = powerMargin(hi, processRun);
    if (gHi >= 0.0 || getAvailableShaftPower("W") <= 0.0) {
      double solved = finalizeSpeed(hi, processRun);
      this.lastResult = new SolveResult(true, SolveStatus.FEASIBLE, getAvailableShaftPower("W"),
          getRequiredDriverPower("W"), Double.NaN, Double.NaN, solved, "W",
          "driver is not limiting up to the maximum speed");
      return solved;
    }
    for (int i = 0; i < 40 && (hi - lo) > 1.0e-4 * hi; i++) {
      double mid = 0.5 * (lo + hi);
      if (powerMargin(mid, processRun) >= 0.0) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    double solved = finalizeSpeed(lo, processRun);
    this.lastResult = new SolveResult(true, SolveStatus.FEASIBLE, getAvailableShaftPower("W"),
        getRequiredDriverPower("W"), Double.NaN, Double.NaN, solved, "W", "speed limited by driver power");
    return solved;
  }

  /**
   * Available minus required driver power at a trial speed.
   *
   * @param trialSpeed common shaft speed in rpm
   * @param processRun flowsheet callback or {@code null}
   * @return power margin in W (negative when overloaded)
   */
  private double powerMargin(double trialSpeed, Runnable processRun) {
    this.speed = trialSpeed;
    applySpeed();
    runOnce(processRun);
    double available = getAvailableShaftPower("W");
    return available <= 0.0 ? Double.POSITIVE_INFINITY : available - getRequiredDriverPower("W");
  }

  /**
   * Register the recycle (anti-surge) valve of a member so that {@link #applyRequiredRecycle()} can position it.
   *
   * @param compressor a member of this shaft
   * @param valve the member's recycle valve, or {@code null} to remove the registration
   * @throws IllegalArgumentException if the compressor is not on the shaft
   */
  public void setAntiSurgeValve(Compressor compressor, ThrottlingValve valve) {
    requireMember(compressor);
    if (valve == null) {
      antiSurgeValves.remove(compressor);
    } else {
      antiSurgeValves.put(compressor, valve);
    }
  }

  /**
   * Register the anti-surge controller of a member. Its recycle valve is positioned by {@link #applyRequiredRecycle()}
   * (steady-state set point; the controller itself acts only in a transient run).
   *
   * @param compressor a member of this shaft
   * @param controller the member's anti-surge controller, or {@code null} to remove the registration
   * @throws IllegalArgumentException if the compressor is not on the shaft
   */
  public void setAntiSurgeController(Compressor compressor, AntiSurgeController controller) {
    requireMember(compressor);
    if (controller == null) {
      antiSurgeControllers.remove(compressor);
    } else {
      antiSurgeControllers.put(compressor, controller);
    }
  }

  /**
   * Set the recycle fraction at which a registered recycle valve is fully open. The valve opening is taken as linear in
   * the recycle fraction, which is a screening relation; calibrate it to the valve and line when the opening matters.
   *
   * @param fraction recycle fraction of the suction flow at 100 percent opening, in (0, 1]
   * @throws IllegalArgumentException if the fraction is outside (0, 1]
   */
  public void setRecycleValveFullOpenFraction(double fraction) {
    if (!(fraction > 0.0 && fraction <= 1.0)) {
      throw new IllegalArgumentException("full-open recycle fraction must be in the range (0, 1]");
    }
    this.recycleValveFullOpenFraction = fraction;
  }

  /**
   * Feed the recycle fraction each member needs (from {@link #getOperatingReport()}, the distance of the operating
   * point to the anti-surge control line) into the member's anti-surge: the built-in {@link AntiSurge} of the
   * compressor is activated with the control line of the member (surge control factor from its surge control margin)
   * and its valve position is set, and a registered recycle valve
   * ({@link #setAntiSurgeValve(Compressor, ThrottlingValve)} or {@link #setAntiSurgeController}) is opened in
   * proportion to the fraction. The built-in anti-surge acts inside the compressor run when the operating point is left
   * of the surge line; between the surge line and the control line the recycle is carried by the valve opening and by
   * the recycle power of the report. Re-run the flowsheet afterwards.
   *
   * <p>
   * The call is idempotent: the fractions are a function of the current operating point only, so repeating it without a
   * change of that point gives the same state. A member that needs no recycle (or is idle) has its recycle set to zero,
   * and a built-in anti-surge that this method switched on is switched off again; an anti-surge the user had activated
   * stays active.
   * </p>
   *
   * @return applied recycle fraction per member name, in flow order
   */
  public Map<String, Double> applyRequiredRecycle() {
    CompressorShaftReport report = getOperatingReport();
    Map<String, Double> applied = new LinkedHashMap<String, Double>();
    for (int i = 0; i < compressors.size(); i++) {
      Compressor c = compressors.get(i);
      CompressorShaftReport.MemberReport mr = report.getMembers().get(i);
      double f = mr.isActive() ? Math.max(0.0, Math.min(0.99, mr.getRequiredRecycleFraction())) : 0.0;
      if (Double.isNaN(f)) {
        f = 0.0;
      }
      applyRecycle(c, f);
      applied.put(c.getName(), Double.valueOf(f));
    }
    return applied;
  }

  /**
   * Apply one recycle fraction to the anti-surge of a member.
   *
   * @param c the member
   * @param f the recycle fraction of the suction flow, in [0, 1)
   */
  private void applyRecycle(Compressor c, double f) {
    AntiSurge as = c.getAntiSurge();
    double opening = Math.min(1.0, f / recycleValveFullOpenFraction);
    if (f > 1.0e-6) {
      if (!as.isActive()) {
        as.setActive(true);
        recycleAutoActivated.put(c, Boolean.TRUE);
      }
      if (recycleAutoActivated.containsKey(c) && c.isSurgeControlMarginSet()) {
        as.setSurgeControlFactor(1.0 + c.getSurgeControlMargin());
      }
      as.setValvePosition(opening);
      as.setTargetValvePosition(opening);
    } else {
      as.setValvePosition(0.0);
      as.setTargetValvePosition(0.0);
      if (recycleAutoActivated.remove(c) != null) {
        as.setActive(false);
        as.setCurrentSurgeFraction(0.0);
      }
    }
    ThrottlingValve valve = antiSurgeValves.get(c);
    if (valve == null && antiSurgeControllers.containsKey(c)) {
      valve = antiSurgeControllers.get(c).getRecycleValve();
    }
    if (valve != null) {
      valve.setPercentValveOpening(100.0 * opening);
    }
  }

  /**
   * Build the operating-point report at the present state of the flowsheet: member speeds, powers, surge and stonewall
   * margins, discharge-pressure margins against the process need and the anti-surge recycle fraction each member needs.
   *
   * @return the report
   */
  public CompressorShaftReport getOperatingReport() {
    List<CompressorShaftReport.MemberReport> reports = new ArrayList<CompressorShaftReport.MemberReport>();
    for (Compressor c : compressors) {
      reports.add(buildMemberReport(c));
    }
    double available = getAvailableShaftPower("W");
    double fuel = Double.NaN;
    double exhaustT = Double.NaN;
    if (gasTurbineUnitDriver != null) {
      fuel = gasTurbineUnitDriver.getFuelMassFlowKgPerHr();
      exhaustT = gasTurbineUnitDriver.getExhaustTemperatureK();
    }
    return new CompressorShaftReport(name, isTurning(), speed, ambientTemperatureK, getTotalPower(),
        getRequiredDriverPower("W"), available, getPowerUtilization(), isOverPower(),
        lastResult == null ? "NONE" : lastResult.getStatus().name(), isFeasible(), fuel, exhaustT, reports);
  }

  /**
   * Build the report of one member.
   *
   * @param c the member
   * @return the member report
   */
  private CompressorShaftReport.MemberReport buildMemberReport(Compressor c) {
    List<String> flags = new ArrayList<String>();
    boolean active = isMemberActive(c);
    double ratio = getGearRatio(c);
    if (!active) {
      flags.add("idle");
      return new CompressorShaftReport.MemberReport(c.getName(), false, ratio, 0.0, 0.0, 0.0, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN, Double.NaN, 0.0, 0.0, false, false, true, flags);
    }
    double flow = safe(c, "flow");
    double pIn = c.getInletStream().getPressure("bara");
    double pOut = c.getOutletStream().getPressure("bara");
    Double need = requiredDischargeBara.get(c);
    boolean hasChart = c.getCompressorChart() != null && c.getCompressorChart().isUseCompressorChart();
    double dSurge = hasChart ? safe(c, "surge") : Double.NaN;
    double dStone = hasChart ? safe(c, "stone") : Double.NaN;
    double recycle = hasChart ? safe(c, "recycle") : 0.0;
    if (Double.isNaN(recycle)) {
      recycle = 0.0;
    }
    boolean surge = false;
    boolean stone = false;
    if (hasChart) {
      try {
        surge = c.isSurge();
      } catch (RuntimeException ex) {
        surge = false;
      }
      try {
        stone = c.isStoneWall();
      } catch (RuntimeException ex) {
        stone = false;
      }
    }
    boolean inRange = true;
    try {
      inRange = c.isSpeedWithinRange();
    } catch (RuntimeException ex) {
      inRange = true;
    }
    if (surge) {
      flags.add("surge");
    }
    if (stone) {
      flags.add("stonewall");
    }
    if (recycle > 0.0) {
      flags.add("recycle required");
    }
    if (need != null) {
      double margin = pOut - need.doubleValue();
      if (margin < -0.01 * need.doubleValue()) {
        flags.add("speed too low for the discharge need");
      } else if (margin > 0.03 * need.doubleValue()) {
        flags.add("excess head (throttle or recycle)");
      }
    }
    if (!inRange) {
      flags.add("outside member speed range");
    }
    return new CompressorShaftReport.MemberReport(c.getName(), true, ratio, c.getSpeed(), c.getPower(), flow, pIn, pOut,
        need == null ? Double.NaN : need.doubleValue(), dSurge, dStone, recycle, recycle * c.getPower(), surge, stone,
        inRange, flags);
  }

  /**
   * Evaluate a chart-dependent member quantity, returning NaN when the chart cannot supply it.
   *
   * @param c the member
   * @param what "flow", "surge", "stone" or "recycle"
   * @return the value or NaN
   */
  private static double safe(Compressor c, String what) {
    try {
      if ("flow".equals(what)) {
        return c.getInletStream().getFlowRate("m3/hr");
      } else if ("surge".equals(what)) {
        return c.getDistanceToSurge();
      } else if ("stone".equals(what)) {
        return c.getDistanceToStoneWall();
      }
      return c.getRequiredRecycleFractionToControlLine();
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  /**
   * Get the shaft name.
   *
   * @return the shaft name
   */
  public String getName() {
    return name;
  }
}
