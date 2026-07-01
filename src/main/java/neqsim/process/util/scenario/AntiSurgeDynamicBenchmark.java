package neqsim.process.util.scenario;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reproducible dynamic benchmark for the {@link AntiSurgeController} recycle-valve loop.
 *
 * <p>
 * HYSYS/UniSim Dynamics are the industrial reference for compressor trip and anti-surge tuning because they couple
 * proven valve/actuator dynamics to the gas-path mass-storage. NeqSim's {@link AntiSurgeController} is a tested PI
 * anti-surge law; what was missing was a documented, repeatable transient case in which the controller is exercised
 * against a moving surge margin so its dynamic response (proportional kick, integral action, anti-windup, valve
 * actuation) can be verified and tuned before field commissioning.
 * </p>
 *
 * <p>
 * This benchmark provides exactly that. It drives a real {@link AntiSurgeController} and a real {@link ThrottlingValve}
 * against a transparent first-order gas-path surrogate: a flow-reduction (trip-like) disturbance steadily erodes the
 * compressor distance to surge, while opening the recycle valve restores it. The plant model is
 * </p>
 *
 * $$ m_{k+1} = m_k - \dot d\,\Delta t + a\,\frac{u_k}{100}\,\Delta t $$
 *
 * <p>
 * where \(m\) is the distance to surge, \(\dot d\) is the disturbance rate (per second), \(a\) is the recycle authority
 * (per second at fully open valve) and \(u\) is the valve opening in percent. This is the same class of lumped
 * surrogate used to tune anti-surge controllers ahead of a full dynamic study; it is <em>not</em> validated field data
 * and is not a substitute for a vendor dynamic model, but it gives a deterministic, inspectable benchmark with an
 * analytically known expected behaviour (the closed loop must hold the margin near the set point; the open loop must
 * surge).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AntiSurgeDynamicBenchmark {
  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(AntiSurgeDynamicBenchmark.class);

  /** Compressor surrogate exposing a settable distance to surge. */
  private final BenchmarkCompressor compressor;

  /** Recycle valve actuated by the controller. */
  private final ThrottlingValve recycleValve;

  /** Anti-surge controller under test. */
  private final AntiSurgeController controller;

  /** Initial distance to surge at the start of the transient. */
  private double initialMargin = 0.30;

  /** Disturbance rate eroding the surge margin, per second. */
  private double disturbanceRate = 0.020;

  /** Recycle authority restoring the margin at a fully open valve, per second. */
  private double recycleAuthority = 0.060;

  /** Integration time step in seconds. */
  private double timeStep = 1.0;

  /** Number of integration steps in the transient. */
  private int numberOfSteps = 120;

  /** Recorded distance-to-surge history. */
  private double[] surgeMarginTrace = null;

  /** Recorded valve-opening history in percent. */
  private double[] valveOpeningTrace = null;

  /** Recorded target valve-opening history before actuator limits in percent. */
  private double[] targetValveOpeningTrace = null;

  /** Recorded predicted distance-to-surge history. */
  private double[] predictedSurgeMarginTrace = null;

  /** Minimum distance to surge observed during the last run. */
  private double minimumSurgeMargin = Double.NaN;

  /** Maximum recycle valve opening observed during the last run, in percent. */
  private double maximumValveOpening = Double.NaN;

  /**
   * Builds the benchmark with a default 10% surge-margin set point and representative PI tuning.
   */
  public AntiSurgeDynamicBenchmark() {
    SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule(2);
    Stream suction = new Stream("benchmark suction", gas);
    suction.setFlowRate(100000.0, "kg/hr");
    suction.setTemperature(25.0, "C");
    suction.setPressure(50.0, "bara");

    this.compressor = new BenchmarkCompressor("benchmark compressor", initialMargin);
    this.recycleValve = new ThrottlingValve("benchmark recycle valve", suction);
    this.recycleValve.setPercentValveOpening(0.0);

    this.controller = new AntiSurgeController("benchmark anti-surge", compressor, recycleValve);
    this.controller.setSurgeMarginSetPoint(0.10);
    this.controller.setProportionalGain(450.0);
    this.controller.setIntegralTime(18.0);
    this.controller.setOpeningRange(0.0, 100.0);
    this.recycleValve.addController("anti-surge", controller);
  }

  /**
   * Runs the transient and records the surge-margin and valve-opening histories.
   *
   * @param controllerActive whether the anti-surge controller is allowed to act; when {@code false} the recycle valve
   * stays closed (open-loop reference case)
   */
  public void run(boolean controllerActive) {
    controller.setActive(controllerActive);
    controller.reset();
    compressor.setDistanceToSurge(initialMargin);
    recycleValve.setPercentValveOpening(0.0);

    surgeMarginTrace = new double[numberOfSteps + 1];
    valveOpeningTrace = new double[numberOfSteps + 1];
    targetValveOpeningTrace = new double[numberOfSteps + 1];
    predictedSurgeMarginTrace = new double[numberOfSteps + 1];
    surgeMarginTrace[0] = initialMargin;
    valveOpeningTrace[0] = 0.0;
    targetValveOpeningTrace[0] = 0.0;
    predictedSurgeMarginTrace[0] = initialMargin;
    minimumSurgeMargin = initialMargin;
    maximumValveOpening = 0.0;

    double margin = initialMargin;
    for (int k = 1; k <= numberOfSteps; k++) {
      double opening = controllerActive ? controller.getValveOpening() : 0.0;
      // first-order gas-path surrogate: disturbance erodes the margin, recycle restores it
      margin = margin - disturbanceRate * timeStep + recycleAuthority * (opening / 100.0) * timeStep;
      compressor.setDistanceToSurge(margin);

      if (controllerActive) {
        controller.runTransient(controller.getValveOpening(), timeStep, UUID.randomUUID());
      }

      double appliedOpening = controllerActive ? recycleValve.getPercentValveOpening() : 0.0;
      surgeMarginTrace[k] = margin;
      valveOpeningTrace[k] = appliedOpening;
      targetValveOpeningTrace[k] = controllerActive ? controller.getTargetValveOpening() : 0.0;
      predictedSurgeMarginTrace[k] = controllerActive ? controller.getPredictedMargin() : Double.NaN;
      if (margin < minimumSurgeMargin) {
        minimumSurgeMargin = margin;
      }
      if (appliedOpening > maximumValveOpening) {
        maximumValveOpening = appliedOpening;
      }
    }
    logger.debug("AntiSurgeDynamicBenchmark finished: minMargin={}, maxOpening={}", minimumSurgeMargin,
        maximumValveOpening);
  }

  /**
   * Indicates whether the machine was kept out of surge during the last run.
   *
   * @return {@code true} if the minimum distance to surge stayed at or above zero
   */
  public boolean isSurgeAvoided() {
    return !Double.isNaN(minimumSurgeMargin) && minimumSurgeMargin >= 0.0;
  }

  /**
   * Gets the minimum distance to surge observed during the last run.
   *
   * @return the minimum distance to surge (dimensionless), or {@code NaN} if no run has executed
   */
  public double getMinimumSurgeMargin() {
    return minimumSurgeMargin;
  }

  /**
   * Gets the maximum recycle valve opening observed during the last run.
   *
   * @return the maximum valve opening in percent, or {@code NaN} if no run has executed
   */
  public double getMaximumValveOpening() {
    return maximumValveOpening;
  }

  /**
   * Gets the recorded distance-to-surge history of the last run.
   *
   * @return a copy of the surge-margin trace, or {@code null} if no run has executed
   */
  public double[] getSurgeMarginTrace() {
    return surgeMarginTrace == null ? null : java.util.Arrays.copyOf(surgeMarginTrace, surgeMarginTrace.length);
  }

  /**
   * Gets the recorded valve-opening history of the last run.
   *
   * @return a copy of the valve-opening trace in percent, or {@code null} if no run has executed
   */
  public double[] getValveOpeningTrace() {
    return valveOpeningTrace == null ? null : java.util.Arrays.copyOf(valveOpeningTrace, valveOpeningTrace.length);
  }

  /**
   * Gets the requested valve-opening history before actuator limits.
   *
   * @return a copy of the target valve-opening trace in percent, or {@code null} if no run has executed
   */
  public double[] getTargetValveOpeningTrace() {
    return targetValveOpeningTrace == null ? null
        : java.util.Arrays.copyOf(targetValveOpeningTrace, targetValveOpeningTrace.length);
  }

  /**
   * Gets the predicted distance-to-surge history of the last run.
   *
   * @return a copy of the predicted surge-margin trace, or {@code null} if no run has executed
   */
  public double[] getPredictedSurgeMarginTrace() {
    return predictedSurgeMarginTrace == null ? null
        : java.util.Arrays.copyOf(predictedSurgeMarginTrace, predictedSurgeMarginTrace.length);
  }

  /**
   * Gets the anti-surge controller under test.
   *
   * @return the anti-surge controller
   */
  public AntiSurgeController getController() {
    return controller;
  }

  /**
   * Sets the initial distance to surge at the start of the transient.
   *
   * @param initialMargin the initial distance to surge (dimensionless, should be positive)
   */
  public void setInitialMargin(double initialMargin) {
    this.initialMargin = initialMargin;
  }

  /**
   * Sets the disturbance rate eroding the surge margin.
   *
   * @param disturbanceRate the disturbance rate per second (must be non-negative)
   * @throws IllegalArgumentException if the rate is negative
   */
  public void setDisturbanceRate(double disturbanceRate) {
    if (disturbanceRate < 0.0) {
      throw new IllegalArgumentException("disturbanceRate must be non-negative");
    }
    this.disturbanceRate = disturbanceRate;
  }

  /**
   * Sets the recycle authority restoring the margin at a fully open valve.
   *
   * @param recycleAuthority the recycle authority per second (must be positive)
   * @throws IllegalArgumentException if the authority is not positive
   */
  public void setRecycleAuthority(double recycleAuthority) {
    if (recycleAuthority <= 0.0) {
      throw new IllegalArgumentException("recycleAuthority must be positive");
    }
    this.recycleAuthority = recycleAuthority;
  }

  /**
   * Sets the integration time step.
   *
   * @param timeStep the time step in seconds (must be positive)
   * @throws IllegalArgumentException if the time step is not positive
   */
  public void setTimeStep(double timeStep) {
    if (timeStep <= 0.0) {
      throw new IllegalArgumentException("timeStep must be positive");
    }
    this.timeStep = timeStep;
  }

  /**
   * Sets the number of integration steps in the transient.
   *
   * @param numberOfSteps the number of steps (must be at least 1)
   * @throws IllegalArgumentException if fewer than one step is requested
   */
  public void setNumberOfSteps(int numberOfSteps) {
    if (numberOfSteps < 1) {
      throw new IllegalArgumentException("numberOfSteps must be at least 1");
    }
    this.numberOfSteps = numberOfSteps;
  }

  /**
   * Minimal {@link Compressor} subclass that exposes a directly settable distance to surge so the benchmark can impose
   * a transparent gas-path surrogate while still driving the production {@link AntiSurgeController} through its real
   * surge-margin interface.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static final class BenchmarkCompressor extends Compressor {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Imposed distance to surge. */
    private double imposedDistanceToSurge;

    /**
     * Constructs a benchmark compressor with an initial distance to surge.
     *
     * @param name the equipment name
     * @param initialMargin the initial distance to surge
     */
    private BenchmarkCompressor(String name, double initialMargin) {
      super(name);
      this.imposedDistanceToSurge = initialMargin;
    }

    /**
     * Sets the imposed distance to surge.
     *
     * @param distanceToSurge the distance to surge to report to the controller
     */
    private void setDistanceToSurge(double distanceToSurge) {
      this.imposedDistanceToSurge = distanceToSurge;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Returns the benchmark-imposed distance to surge rather than evaluating a compressor map.
     * </p>
     */
    @Override
    public double getDistanceToSurge() {
      return imposedDistanceToSurge;
    }
  }
}
