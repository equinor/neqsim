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
    setCalculationIdentifier(id);
  }
}
