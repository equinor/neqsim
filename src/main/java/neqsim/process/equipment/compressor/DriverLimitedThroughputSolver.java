package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.function.DoubleConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Finds the highest throughput a power-limited common-shaft train can deliver at a fixed discharge-pressure target.
 *
 * <p>
 * For each trial throughput the caller-supplied setter changes the flow into the train (for example the feed stream
 * flow rate), the shaft common speed is solved for the target discharge pressure of the reference member with
 * {@link CompressorShaft#solveSpeed}, and the trial is feasible when the solve is feasible: speed inside the bounds,
 * driver power not exceeded at the site ambient temperature, no surge and no stonewall. The throughput is searched by
 * bisection between a feasible lower flow and an upper flow, assuming feasibility is lost monotonically as the flow
 * rises. The binding limit is read from the solve status of the first infeasible trial. The flowsheet is left at the
 * highest feasible throughput.
 * </p>
 *
 * <pre>{@code
 * DriverLimitedThroughputSolver solver = new DriverLimitedThroughputSolver(shaft, lastStage, 85.0, "bara",
 *     flow -> feed.setFlowRate(flow, "kg/hr"), () -> process.run());
 * DriverLimitedThroughputSolver.Result r = solver.solve(20000.0, 90000.0);
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DriverLimitedThroughputSolver {
  private static final Logger logger = LogManager.getLogger(DriverLimitedThroughputSolver.class);

  /** The limit that stops a further throughput increase. */
  public enum Binding {
    /** Feasible at the upper throughput: nothing binds inside the search range. */
    NONE,
    /** The driver cannot deliver the shaft power. */
    DRIVER_POWER,
    /** The target pressure needs more than the maximum shaft speed. */
    MAX_SPEED,
    /** A member reaches stonewall. */
    STONEWALL,
    /** A member reaches surge. */
    SURGE,
    /** Another infeasibility, see the status text of the result. */
    OTHER,
    /** Not even the lowest throughput is feasible. */
    INFEASIBLE_AT_MIN_THROUGHPUT
  }

  /**
   * Outcome of a throughput search.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    private final double maxThroughput;
    private final double speedRpm;
    private final double requiredPowerW;
    private final double availablePowerW;
    private final Binding binding;
    private final String status;
    private final int evaluations;

    /**
     * Constructor.
     *
     * @param maxThroughput highest feasible throughput in the caller's units
     * @param speedRpm common shaft speed at that throughput [rpm]
     * @param requiredPowerW driver power demand at that throughput [W]
     * @param availablePowerW driver power available [W], 0 if unlimited
     * @param binding limit that stops further increase
     * @param status solve status text of the limiting trial
     * @param evaluations number of shaft solves used
     */
    Result(double maxThroughput, double speedRpm, double requiredPowerW, double availablePowerW, Binding binding,
        String status, int evaluations) {
      this.maxThroughput = maxThroughput;
      this.speedRpm = speedRpm;
      this.requiredPowerW = requiredPowerW;
      this.availablePowerW = availablePowerW;
      this.binding = binding;
      this.status = status;
      this.evaluations = evaluations;
    }

    /**
     * Get the highest feasible throughput.
     *
     * @return throughput in the caller's units
     */
    public double getMaxThroughput() {
      return maxThroughput;
    }

    /**
     * Get the common shaft speed at the highest feasible throughput.
     *
     * @return speed [rpm]
     */
    public double getSpeedRpm() {
      return speedRpm;
    }

    /**
     * Get the driver power demand at the highest feasible throughput.
     *
     * @return power [W]
     */
    public double getRequiredPowerW() {
      return requiredPowerW;
    }

    /**
     * Get the driver power available.
     *
     * @return power [W], 0 when unlimited
     */
    public double getAvailablePowerW() {
      return availablePowerW;
    }

    /**
     * Get the binding limit.
     *
     * @return the limit
     */
    public Binding getBinding() {
      return binding;
    }

    /**
     * Get the solve status text of the limiting trial.
     *
     * @return status text
     */
    public String getStatus() {
      return status;
    }

    /**
     * Get the number of shaft solves used.
     *
     * @return count
     */
    public int getEvaluations() {
      return evaluations;
    }

    /**
     * Serialize to compact JSON.
     *
     * @return JSON string
     */
    public String toJson() {
      return "{\"maxThroughput\":" + CompressorShaftReport.num(maxThroughput) + ",\"speedRpm\":"
          + CompressorShaftReport.num(speedRpm) + ",\"requiredPowerW\":" + CompressorShaftReport.num(requiredPowerW)
          + ",\"availablePowerW\":" + CompressorShaftReport.num(availablePowerW) + ",\"binding\":\"" + binding
          + "\",\"status\":\"" + status + "\",\"evaluations\":" + evaluations + "}";
    }
  }

  private final CompressorShaft shaft;
  private final Compressor reference;
  private final double targetPressure;
  private final String pressureUnit;
  private final DoubleConsumer throughputSetter;
  private final Runnable processRun;
  private double relativeTolerance = 0.002;
  private int maxIterations = 30;
  private int evaluations = 0;
  private CompressorShaft.SolveStatus lastStatus = CompressorShaft.SolveStatus.NOT_CONFIGURED;

  /**
   * Constructor.
   *
   * @param shaft the shaft with its driver limit and speed bounds already configured
   * @param reference the member whose discharge pressure is the target (null selects the last active member)
   * @param targetPressure the discharge pressure to hold
   * @param pressureUnit pressure unit, e.g. "bara"
   * @param throughputSetter sets the train throughput in the flowsheet (e.g. the feed flow rate)
   * @param processRun re-solves the flowsheet, or {@code null} to run only the member compressors
   */
  public DriverLimitedThroughputSolver(CompressorShaft shaft, Compressor reference, double targetPressure,
      String pressureUnit, DoubleConsumer throughputSetter, Runnable processRun) {
    if (shaft == null || throughputSetter == null) {
      throw new IllegalArgumentException("shaft and throughputSetter are required");
    }
    this.shaft = shaft;
    this.reference = reference;
    this.targetPressure = targetPressure;
    this.pressureUnit = pressureUnit;
    this.throughputSetter = throughputSetter;
    this.processRun = processRun;
  }

  /**
   * Set the relative tolerance on the throughput.
   *
   * @param tolerance relative tolerance, default 0.002
   */
  public void setRelativeTolerance(double tolerance) {
    this.relativeTolerance = tolerance;
  }

  /**
   * Set the maximum number of bisection steps.
   *
   * @param iterations maximum steps, default 30
   */
  public void setMaxIterations(int iterations) {
    this.maxIterations = iterations;
  }

  /**
   * Evaluate one throughput.
   *
   * @param throughput trial throughput
   * @return true if the shaft solve is feasible
   */
  private boolean feasibleAt(double throughput) {
    throughputSetter.accept(throughput);
    shaft.solveSpeed(reference, targetPressure, pressureUnit, processRun);
    evaluations++;
    CompressorShaft.SolveResult r = shaft.getLastSolveResult();
    lastStatus = r == null ? CompressorShaft.SolveStatus.NOT_CONFIGURED : r.getStatus();
    return r == null || r.isFeasible();
  }

  /**
   * Search the highest feasible throughput in a range.
   *
   * @param minThroughput lower throughput, expected to be feasible
   * @param maxThroughput upper throughput of the search range
   * @return the result; the flowsheet is left at the highest feasible throughput
   */
  public Result solve(double minThroughput, double maxThroughput) {
    evaluations = 0;
    if (!(maxThroughput > minThroughput)) {
      throw new IllegalArgumentException("maxThroughput must exceed minThroughput");
    }
    if (!feasibleAt(minThroughput)) {
      logger.warn("DriverLimitedThroughputSolver: lowest throughput {} is not feasible ({})", minThroughput,
          lastStatus);
      return result(minThroughput, Binding.INFEASIBLE_AT_MIN_THROUGHPUT, lastStatus.name());
    }
    if (feasibleAt(maxThroughput)) {
      return result(maxThroughput, Binding.NONE, lastStatus.name());
    }
    Binding binding = bindingOf(lastStatus);
    String status = lastStatus.name();
    double lo = minThroughput;
    double hi = maxThroughput;
    for (int i = 0; i < maxIterations && (hi - lo) > relativeTolerance * hi; i++) {
      double mid = 0.5 * (lo + hi);
      if (feasibleAt(mid)) {
        lo = mid;
      } else {
        hi = mid;
        binding = bindingOf(lastStatus);
        status = lastStatus.name();
      }
    }
    feasibleAt(lo);
    return result(lo, binding, status);
  }

  /**
   * Map a shaft solve status to the binding limit.
   *
   * @param status solve status of an infeasible trial
   * @return the binding limit
   */
  private static Binding bindingOf(CompressorShaft.SolveStatus status) {
    switch (status) {
    case OVER_POWER:
      return Binding.DRIVER_POWER;
    case PRESSURE_ABOVE_MAX_SPEED:
      return Binding.MAX_SPEED;
    case STONEWALL:
      return Binding.STONEWALL;
    case SURGE:
      return Binding.SURGE;
    default:
      return Binding.OTHER;
    }
  }

  /**
   * Assemble the result at the present flowsheet state.
   *
   * @param throughput throughput to report
   * @param binding binding limit
   * @param status status text
   * @return the result
   */
  private Result result(double throughput, Binding binding, String status) {
    return new Result(throughput, shaft.getSpeed(), shaft.getRequiredDriverPower("W"),
        shaft.getAvailableShaftPower("W"), binding, status, evaluations);
  }
}
