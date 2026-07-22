package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorShaft;

/**
 * Process-integrated common-speed controller for a {@link CompressorShaft}.
 *
 * <p>
 * Several compressor bodies on ONE driver shaft must turn at the same speed, and only the string's final discharge
 * pressure is a controlled target (the intermediate inter-body pressures float). The stand-alone
 * {@link CompressorShaft#solveSpeed} re-solves the whole flowsheet many times through an external callback. This
 * calculator instead converges the common speed <em>inside</em> the normal
 * {@link neqsim.process.processmodel.ProcessSystem#run()} iteration: like {@link AntiSurgeCalculator}, it runs on every
 * internal pass and takes one damped secant step on the common speed toward the target discharge, so the shaft speed
 * converges together with the recycle loops in a single {@code run()} — no separate N full-field solves.
 * </p>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * CompressorShaft shaft = new CompressorShaft("23-KA train");
 * shaft.addCompressor(rc1);
 * shaft.addCompressor(rc2);
 * shaft.addCompressor(rc3); // reference (last body)
 * CompressorShaftCalculator shaftCalc = new CompressorShaftCalculator("23-KA shaft speed", shaft, rc3, 49.0, "bara");
 * process.add(shaftCalc);
 * process.run(); // shaft speed converges with the recycles
 * double rpm = shaftCalc.getSpeed();
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorShaftCalculator extends Calculator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Compressor bodies on the shaft (in flow order). */
  private final List<Compressor> bodies = new ArrayList<Compressor>();

  /** The body whose outlet pressure is the controlled target. */
  private Compressor reference;

  /** Target outlet pressure of the reference body. */
  private double targetPressure;

  /** Pressure unit for the target and read-back. */
  private String pressureUnit = "bara";

  /** Lower speed bound in rpm. */
  private double minSpeed = 500.0;

  /** Upper speed bound in rpm. */
  private double maxSpeed = 25000.0;

  /** Maximum fractional speed change per iteration (damping for stability). */
  private double maxStepFraction = 0.10;

  /** Current common shaft speed in rpm. */
  private double speed = 10000.0;

  /** Previous speed (for the secant step). */
  private double prevSpeed = Double.NaN;

  /** Previous outlet-pressure error (for the secant step). */
  private double prevError = Double.NaN;

  /** Tolerance (in the pressure unit) used to classify the solve as feasible / saturated. */
  private double pressureTolerance = 0.05;

  /** Pressure-control action for a target below the minimum-speed capability. */
  private CompressorShaft.PressureControl pressureControl = CompressorShaft.PressureControl.NONE;

  /** Feasibility result of the most recent {@link #run(UUID)} pass ({@code null} until first run). */
  private CompressorShaft.SolveResult lastResult = null;

  /**
   * Constructor for CompressorShaftCalculator.
   *
   * @param name the calculator name
   */
  public CompressorShaftCalculator(String name) {
    super(name);
  }

  /**
   * Constructor with explicit wiring from a {@link CompressorShaft}.
   *
   * @param name the calculator name
   * @param shaft the shaft whose bodies share a common speed
   * @param reference the body whose outlet pressure is the controlled target; {@code null} uses the last body added to
   * the shaft
   * @param targetPressure the required outlet pressure of the reference body
   * @param unit the pressure unit (e.g. "bara")
   */
  public CompressorShaftCalculator(String name, CompressorShaft shaft, Compressor reference, double targetPressure,
      String unit) {
    super(name);
    for (Compressor compressor : shaft.getCompressors()) {
      bodies.add(compressor);
      addInputVariable(compressor);
    }
    if (reference != null) {
      this.reference = reference;
    } else if (!bodies.isEmpty()) {
      this.reference = bodies.get(bodies.size() - 1);
    }
    this.targetPressure = targetPressure;
    this.pressureUnit = unit;
    this.speed = shaft.getSpeed();
  }

  /**
   * Set the speed search bounds.
   *
   * @param min the lower speed bound in rpm
   * @param max the upper speed bound in rpm
   */
  public void setSpeedBounds(double min, double max) {
    this.minSpeed = min;
    this.maxSpeed = max;
  }

  /**
   * Set the maximum fractional speed change allowed per iteration (damping).
   *
   * @param fraction the maximum fractional step (e.g. 0.10 for 10%)
   */
  public void setMaxStepFraction(double fraction) {
    this.maxStepFraction = fraction;
  }

  /**
   * Get the current common shaft speed.
   *
   * @return the shaft speed in rpm
   */
  public double getSpeed() {
    return speed;
  }

  /**
   * Set the pressure-control action used when the requested pressure is below what the string makes at minimum speed.
   *
   * @param control the pressure-control action (must not be {@code null})
   */
  public void setPressureControl(CompressorShaft.PressureControl control) {
    if (control != null) {
      this.pressureControl = control;
    }
  }

  /**
   * Get the pressure-control action used for a target below the minimum-speed capability.
   *
   * @return the configured pressure-control action
   */
  public CompressorShaft.PressureControl getPressureControl() {
    return pressureControl;
  }

  /**
   * Set the tolerance (in the pressure unit) used to classify the solve as feasible / saturated.
   *
   * @param tolerance the tolerance (must be positive)
   */
  public void setPressureTolerance(double tolerance) {
    if (tolerance > 0.0) {
      this.pressureTolerance = tolerance;
    }
  }

  /**
   * Get the feasibility result of the most recent {@link #run(UUID)} pass.
   *
   * @return the last solve result, or {@code null} if the calculator has not run yet
   */
  public CompressorShaft.SolveResult getLastSolveResult() {
    return lastResult;
  }

  /**
   * Whether the most recent pass reached (or is converging to) a feasible operating point.
   *
   * @return {@code true} if no pass has run yet or the last pass was feasible; {@code false} if the target is saturated
   * above the maximum speed (or below the minimum speed with no pressure control)
   */
  public boolean isFeasible() {
    return lastResult == null || lastResult.isFeasible();
  }

  /**
   * Put every body into fixed-speed, chart-forward mode at the current common speed.
   */
  private void applySpeed() {
    for (Compressor compressor : bodies) {
      compressor.setSolveSpeed(false);
      compressor.setSpeed(speed);
      if (compressor.getCompressorChart() != null) {
        compressor.getCompressorChart().setUseCompressorChart(true);
      }
      compressor.setUsePolytropicCalc(true);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (bodies.isEmpty() || reference == null) {
      return;
    }
    double pout = reference.getOutletStream().getPressure(pressureUnit);
    double error = pout - targetPressure; // drive to zero
    double newSpeed;
    if (Double.isNaN(prevError) || Math.abs(error - prevError) < 1.0e-9) {
      // First step (or a stalled secant): proportional bump. Higher speed raises discharge, so a
      // negative error (pout below target) increases speed.
      double frac = -error / Math.max(Math.abs(targetPressure), 1.0);
      frac = Math.max(-maxStepFraction, Math.min(maxStepFraction, frac));
      newSpeed = speed * (1.0 + frac);
    } else {
      // Secant step on the (speed -> error) relation.
      newSpeed = speed - error * (speed - prevSpeed) / (error - prevError);
    }
    // Damp the step and clamp to the speed bounds for stability inside the recycle iteration.
    double maxStep = Math.abs(speed) * maxStepFraction;
    if (newSpeed > speed + maxStep) {
      newSpeed = speed + maxStep;
    }
    if (newSpeed < speed - maxStep) {
      newSpeed = speed - maxStep;
    }
    if (newSpeed < minSpeed) {
      newSpeed = minSpeed;
    }
    if (newSpeed > maxSpeed) {
      newSpeed = maxSpeed;
    }
    prevSpeed = speed;
    prevError = error;
    speed = newSpeed;
    applySpeed();
    updateFeasibility();
    setCalculationIdentifier(id);
  }

  /**
   * Classify the current pass as feasible / pressure-controlled / infeasible and store the result. The target is
   * saturated when the common speed is pinned at a bound and the reference discharge still misses the target: above the
   * maximum-speed capability (infeasible) or below the minimum-speed capability (infeasible unless a
   * {@link CompressorShaft.PressureControl} sheds the surplus head).
   */
  private void updateFeasibility() {
    double pout = reference.getOutletStream().getPressure(pressureUnit);
    double error = pout - targetPressure;
    boolean atMax = speed >= maxSpeed - 1.0e-6;
    boolean atMin = speed <= minSpeed + 1.0e-6;
    if (atMax && error < -pressureTolerance) {
      lastResult = new CompressorShaft.SolveResult(false, CompressorShaft.SolveStatus.PRESSURE_ABOVE_MAX_SPEED,
          targetPressure, pout, Double.NaN, pout, speed, pressureUnit, "target above maximum-speed capability");
    } else if (atMin && error > pressureTolerance) {
      if (pressureControl != CompressorShaft.PressureControl.NONE) {
        try {
          reference.getOutletStream().setPressure(targetPressure, pressureUnit);
          reference.getOutletStream().run();
        } catch (RuntimeException ex) {
          // best-effort screening choke; leave the discharge as produced if it cannot be set
        }
        lastResult = new CompressorShaft.SolveResult(true, CompressorShaft.SolveStatus.PRESSURE_CONTROLLED,
            targetPressure, targetPressure, pout, Double.NaN, speed, pressureUnit,
            "surplus head shed by " + pressureControl);
      } else {
        lastResult = new CompressorShaft.SolveResult(false, CompressorShaft.SolveStatus.PRESSURE_BELOW_MIN_SPEED,
            targetPressure, pout, pout, Double.NaN, speed, pressureUnit,
            "target below minimum-speed capability; no pressure control");
      }
    } else {
      boolean reached = Math.abs(error) < pressureTolerance;
      lastResult = new CompressorShaft.SolveResult(true, CompressorShaft.SolveStatus.FEASIBLE, targetPressure, pout,
          Double.NaN, Double.NaN, speed, pressureUnit, reached ? "target reached" : "converging toward target");
    }
  }
}
