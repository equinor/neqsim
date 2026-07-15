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

  /**
   * Constructor.
   *
   * @param name the shaft name
   */
  public CompressorShaft(String name) {
    this.name = name;
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
    // superlinear on this smooth map and needs far fewer flowsheet solves than bisection.
    double a = minSpeed;
    double b = maxSpeed;
    double fa = evalOutletError(a, reference, targetOutletPressure, unit, processRun);
    if (Math.abs(fa) < pressureToleranceBar) {
      return finalizeSpeed(a, processRun);
    }
    double fb = evalOutletError(b, reference, targetOutletPressure, unit, processRun);
    if (Math.abs(fb) < pressureToleranceBar) {
      return finalizeSpeed(b, processRun);
    }
    if (fa * fb > 0.0) {
      // Target not reachable within the speed bounds; use the closer bound.
      double bound = Math.abs(fa) <= Math.abs(fb) ? a : b;
      logger.warn(
          "CompressorShaft {}: target {} {} not bracketed within speed bounds [{}, {}] rpm; " + "using nearest bound.",
          name, targetOutletPressure, unit, a, b);
      return finalizeSpeed(bound, processRun);
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
    return finalizeSpeed(best, processRun);
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
