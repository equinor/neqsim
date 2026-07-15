package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * // re-run the whole flowsheet between speed guesses so inter-body streams update
 * shaft.solveSpeed(rc3, 49.0, "bara", () -> process.run());
 * double rpm = shaft.getSpeed();
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
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

  /** Lower speed bound in rpm. */
  private double minSpeed = 500.0;

  /** Upper speed bound in rpm. */
  private double maxSpeed = 25000.0;

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
    NOT_CONFIGURED
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
   * Set the speed search bounds used by {@link #solveSpeed}.
   *
   * @param min the lower speed bound in rpm
   * @param max the upper speed bound in rpm
   */
  public void setSpeedBounds(double min, double max) {
    this.minSpeed = min;
    this.maxSpeed = max;
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
      compressor.setSolveSpeed(false);
      compressor.setSpeed(speed);
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
    if (reference == null) {
      reference = compressors.get(compressors.size() - 1);
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
    for (Compressor compressor : compressors) {
      if (compressor.getCompressorChart() == null) {
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
        compressor.run();
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
      sum += compressor.getPower();
    }
    return sum;
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
