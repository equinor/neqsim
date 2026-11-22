package neqsim.process.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Screens single flowmeter biases and unmetered outflows against synchronized inventory balances. Control volumes are
 * identified by their inventory signal (kg). Meters are oriented edges (kg/s). Each candidate must explain every
 * control volume within its absolute error budget.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessBalanceDiagnosis {
  /** Outcome of screening the listed single faults. */
  public enum Status {
    /** One joint set of healthy measurement errors explains the observations. */
    WITHIN_BOUNDS,
    /** Exactly one of the enumerated single faults fits all balances. */
    SINGLE_CANDIDATE,
    /** Multiple single faults fit the observed balances. */
    AMBIGUOUS,
    /** All enumerated single faults conflict with at least one balance. */
    UNEXPLAINED
  }

  /** Physical interpretation of the candidate rate. */
  public enum Kind {
    /** Additive measurement bias above the healthy error allowance. */
    METER_BIAS,
    /** Unknown outward mass flow from one control volume. */
    UNMETERED_OUTFLOW
  }

  /** One oriented flow measurement shared by its two endpoints. */
  public static final class Meter {
    /** Signal identifier used in the historian window. */
    private final String tag;
    /** Negative incidence endpoint; null is outside the monitored system. */
    private final String from;
    /** Positive incidence endpoint; null is outside the monitored system. */
    private final String to;
    /** Maximum healthy mean flow-rate error in kg/s. */
    private final double errorKgPerS;

    /**
     * Defines positive flow from upstream to downstream. Reverse measurements may be negative.
     *
     * @param tag flow signal in kg/s
     * @param from upstream inventory tag, or null for an external source
     * @param to downstream inventory tag, or null for an external sink
     * @param errorKgPerS maximum absolute error in kg/s under the healthy-meter assumption
     */
    public Meter(String tag, String from, String to, double errorKgPerS) {
      if (tag == null || tag.trim().isEmpty() || (from == null && to == null) || (from != null && from.equals(to))) {
        throw new IllegalArgumentException("meter needs a tag and two distinct endpoints including a control volume");
      }
      this.tag = tag;
      this.from = from;
      this.to = to;
      this.errorKgPerS = requireBound(errorKgPerS);
    }

    /**
     * Returns the flowmeter tag.
     *
     * @return flowmeter tag
     */
    String getTag() {
      return tag;
    }

    /**
     * Returns the upstream inventory tag or null.
     *
     * @return upstream inventory tag or null
     */
    String getFrom() {
      return from;
    }

    /**
     * Returns the downstream inventory tag or null.
     *
     * @return downstream inventory tag or null
     */
    String getTo() {
      return to;
    }

    /**
     * Returns the healthy absolute measurement error bound in kg/s.
     *
     * @return healthy absolute measurement error bound in kg/s
     */
    double getErrorKgPerS() {
      return errorKgPerS;
    }
  }

  /** Signed mean-rate balance and the absolute error budget for one control volume. */
  public static final class Residual {
    /** Time-weighted measured inflow less outflow in kg/s. */
    private final double netInflowKgPerS;
    /** Signed rate of change of stored mass in kg/s. */
    private final double accumulationKgPerS;
    /** Net inflow less storage accumulation in kg/s. */
    private final double residualKgPerS;
    /** Maximum absolute error of this individual balance in kg/s. */
    private final double boundKgPerS;

    /**
     * Constructs a control-volume balance.
     *
     * @param net measured mean net inflow in kg/s
     * @param accumulation measured inventory change divided by duration in kg/s
     * @param bound total absolute error budget in kg/s
     */
    private Residual(double net, double accumulation, double bound) {
      this.netInflowKgPerS = net;
      this.accumulationKgPerS = accumulation;
      this.residualKgPerS = net - accumulation;
      this.boundKgPerS = bound;
      if (!Double.isFinite(Math.abs(residualKgPerS) + boundKgPerS)) {
        throw new IllegalArgumentException("balance or uncertainty interval exceeds finite range");
      }
    }

    /**
     * Returns the measured mean net inflow in kg/s.
     *
     * @return measured mean net inflow in kg/s
     */
    public double getNetInflowKgPerS() {
      return netInflowKgPerS;
    }

    /**
     * Returns the mean accumulation in kg/s.
     *
     * @return mean accumulation in kg/s
     */
    public double getAccumulationKgPerS() {
      return accumulationKgPerS;
    }

    /**
     * Returns the measured mean inflow minus outflow minus accumulation in kg/s.
     *
     * @return measured mean inflow minus outflow minus accumulation in kg/s
     */
    public double getResidualKgPerS() {
      return residualKgPerS;
    }

    /**
     * Returns the absolute screening bound in kg/s.
     *
     * @return absolute screening bound in kg/s
     */
    public double getBoundKgPerS() {
      return boundKgPerS;
    }
  }

  /** Single-fault signature compatible with the joint measurement error budgets. */
  public static final class Candidate {
    /** Physical meaning of the compatible rate interval. */
    private final Kind kind;
    /** Signal identifier used in the historian window. */
    private final String tag;
    /** Smallest compatible additional fault rate in kg/s. */
    private final double minimumKgPerS;
    /** Largest compatible additional fault rate in kg/s. */
    private final double maximumKgPerS;

    /**
     * Records an interval for mean outflow or additive meter bias (measured minus true).
     *
     * @param kind interpretation
     * @param tag affected inventory or meter tag
     * @param minimum lower inclusive limit in kg/s
     * @param maximum upper inclusive limit in kg/s
     */
    private Candidate(Kind kind, String tag, double minimum, double maximum) {
      this.kind = kind;
      this.tag = tag;
      this.minimumKgPerS = minimum;
      this.maximumKgPerS = maximum;
    }

    /**
     * Returns the interpretation of this rate.
     *
     * @return interpretation of this rate
     */
    public Kind getKind() {
      return kind;
    }

    /**
     * Returns the meter or inventory tag.
     *
     * @return meter or inventory tag
     */
    public String getTag() {
      return tag;
    }

    /**
     * Returns the inclusive lower limit in kg/s.
     *
     * @return inclusive lower limit in kg/s
     */
    public double getMinimumKgPerS() {
      return minimumKgPerS;
    }

    /**
     * Returns the inclusive upper limit in kg/s.
     *
     * @return inclusive upper limit in kg/s
     */
    public double getMaximumKgPerS() {
      return maximumKgPerS;
    }
  }

  /** Elapsed time between first and last sample in seconds. */
  private final double durationSeconds;
  /** Measured balance terms indexed by inventory signal. */
  private final Map<String, Residual> residuals;
  /** Single faults compatible with the complete network. */
  private final List<Candidate> candidates;
  /** Conclusion conditional on the supplied data, budgets and hypotheses. */
  private final Status status;

  /**
   * Freezes the results after screening all candidates.
   *
   * @param durationSeconds integration duration in seconds
   * @param residuals owned map of balances by inventory tag
   * @param candidates owned list of compatible candidates
   * @param status screening outcome
   */
  private ProcessBalanceDiagnosis(double durationSeconds, Map<String, Residual> residuals, List<Candidate> candidates,
      Status status) {
    this.durationSeconds = durationSeconds;
    this.residuals = Collections.unmodifiableMap(residuals);
    this.candidates = Collections.unmodifiableList(candidates);
    this.status = status;
  }

  /**
   * Integrates instantaneous meter readings by the trapezoidal rule over the common window. Bounds add conservatively,
   * including systematic errors. The inventory-change budget may include model, synchronization and quadrature
   * allowances expressed in kg.
   *
   * @param window synchronized, equally spaced samples with common start and end times
   * @param inventoryChangeBoundsKg maximum absolute error in each inventory change, in kg
   * @param meters all measured boundary flows, each supplied once
   * @return evidence and all surviving single-fault candidates
   * @throws IllegalArgumentException for invalid budgets, topology, samples or numerical overflow
   */
  public static ProcessBalanceDiagnosis assess(RcaProcessWindow window, Map<String, Double> inventoryChangeBoundsKg,
      List<Meter> meters) {
    if (window == null || inventoryChangeBoundsKg == null || inventoryChangeBoundsKg.isEmpty() || meters == null
        || meters.isEmpty()) {
      throw new IllegalArgumentException("supply a window, inventories and boundary meters");
    }
    double duration = window.getSampleIntervalSeconds() * (window.getSampleCount() - 1);
    if (!Double.isFinite(duration)) {
      throw new IllegalArgumentException("non-finite window duration");
    }
    Map<String, Double> meanRates = new LinkedHashMap<String, Double>();
    Set<String> connected = new HashSet<String>();
    for (Meter meter : meters) {
      if (meter == null || inventoryChangeBoundsKg.containsKey(meter.tag) || meanRates.containsKey(meter.tag)) {
        throw new IllegalArgumentException("each meter must have a distinct flow tag");
      }
      for (String endpoint : new String[] {meter.from, meter.to}) {
        if (endpoint != null && !inventoryChangeBoundsKg.containsKey(endpoint)) {
          throw new IllegalArgumentException("unknown inventory endpoint: " + endpoint);
        }
        if (endpoint != null) {
          connected.add(endpoint);
        }
      }
      meanRates.put(meter.tag, trapezoidalMean(window.getSignal(meter.tag)));
    }
    if (!connected.equals(inventoryChangeBoundsKg.keySet())) {
      throw new IllegalArgumentException("each inventory must be connected to a measured boundary flow");
    }
    Map<String, Residual> residuals = new LinkedHashMap<String, Residual>();
    Map<String, Double> localBounds = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, Double> entry : inventoryChangeBoundsKg.entrySet()) {
      String tag = entry.getKey();
      double[] inventory = window.getSignal(tag);
      for (double mass : inventory) {
        if (mass < 0.0) {
          throw new IllegalArgumentException("negative inventory for " + tag);
        }
      }
      double net = 0.0;
      if (entry.getValue() == null) {
        throw new IllegalArgumentException("missing inventory-change bound for " + tag);
      }
      double bound = requireBound(entry.getValue()) / duration;
      double localBound = bound;
      for (Meter meter : meters) {
        int sign = incidence(tag, meter.from, meter.to);
        if (sign != 0) {
          net += sign * meanRates.get(meter.tag);
          bound += meter.errorKgPerS;
          if (meter.from == null || meter.to == null) {
            localBound += meter.errorKgPerS;
          }
        }
      }
      double accumulation = (inventory[inventory.length - 1] - inventory[0]) / duration;
      Residual residual = new Residual(net, accumulation, bound);
      residuals.put(tag, residual);
      localBounds.put(tag, localBound);
    }

    List<Candidate> candidates = new ArrayList<Candidate>();
    JointBalanceScreen joint = new JointBalanceScreen(residuals, meters, localBounds);
    if (joint.withinBounds()) {
      return new ProcessBalanceDiagnosis(duration, residuals, candidates, Status.WITHIN_BOUNDS);
    }
    for (Meter meter : meters) {
      screen(residuals, joint, Kind.METER_BIAS, meter.tag, meter.from, meter.to, candidates);
    }
    for (String tag : residuals.keySet()) {
      screen(residuals, joint, Kind.UNMETERED_OUTFLOW, tag, null, tag, candidates);
    }
    Status status = Status.AMBIGUOUS;
    if (candidates.isEmpty()) {
      status = Status.UNEXPLAINED;
    } else if (candidates.size() == 1) {
      status = Status.SINGLE_CANDIDATE;
    }
    return new ProcessBalanceDiagnosis(duration, residuals, candidates, status);
  }

  /**
   * Checks an absolute bounded-error input.
   *
   * @param value supplied bound
   * @return validated bound
   */
  private static double requireBound(double value) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException("absolute error bounds must be finite and nonnegative");
    }
    return value;
  }

  /**
   * Calculates mean rate using half weight for the first and last point.
   *
   * @param values equally spaced instantaneous rates in kg/s
   * @return time-weighted mean rate in kg/s
   */
  private static double trapezoidalMean(double[] values) {
    double sum = 0.5 * values[0] + 0.5 * values[values.length - 1];
    for (int i = 1; i < values.length - 1; i++) {
      sum += values[i];
    }
    return sum / (values.length - 1);
  }

  /**
   * Returns the signed sensitivity of one balance to a flow measurement.
   *
   * @param tag inventory tag
   * @param from upstream endpoint
   * @param to downstream endpoint
   * @return positive for inflow, negative for outflow, zero otherwise
   */
  private static int incidence(String tag, String from, String to) {
    if (tag.equals(to)) {
      return 1;
    }
    return tag.equals(from) ? -1 : 0;
  }

  /**
   * Intersects the allowed signed rate intervals across all control volumes.
   *
   * @param residuals all control volumes on this time window
   * @param joint joint measurement-error feasibility screen
   * @param kind type of candidate
   * @param tag candidate identifier
   * @param from negative sensitivity endpoint or null
   * @param to positive sensitivity endpoint or null
   * @param candidates destination for compatible candidates
   */
  private static void screen(Map<String, Residual> residuals, JointBalanceScreen joint, Kind kind, String tag,
      String from, String to, List<Candidate> candidates) {
    double minimum = kind == Kind.UNMETERED_OUTFLOW ? 0.0 : Double.NEGATIVE_INFINITY;
    double maximum = Double.POSITIVE_INFINITY;
    for (Map.Entry<String, Residual> entry : residuals.entrySet()) {
      int sign = incidence(entry.getKey(), from, to);
      Residual residual = entry.getValue();
      if (sign == 0) {
        if (Math.abs(residual.residualKgPerS) > residual.boundKgPerS) {
          return;
        }
      } else {
        double rate = sign * residual.residualKgPerS;
        minimum = Math.max(minimum, rate - residual.boundKgPerS);
        maximum = Math.min(maximum, rate + residual.boundKgPerS);
      }
    }
    if (minimum <= maximum) {
      double[] interval = joint.interval(from, to, minimum, maximum);
      if (interval != null) {
        candidates.add(new Candidate(kind, tag, interval[0], interval[1]));
      }
    }
  }

  /**
   * Returns the integration duration in seconds.
   *
   * @return integration duration in seconds
   */
  public double getDurationSeconds() {
    return durationSeconds;
  }

  /**
   * Returns the control-volume balances keyed by inventory tag.
   *
   * @return control-volume balances keyed by inventory tag
   */
  public Map<String, Residual> getResiduals() {
    return residuals;
  }

  /**
   * Returns all compatible candidates in input order (meters first, then volumes).
   *
   * @return all compatible candidates in input order (meters first, then volumes)
   */
  public List<Candidate> getCandidates() {
    return candidates;
  }

  /**
   * Returns the screening outcome.
   *
   * @return screening outcome
   */
  public Status getStatus() {
    return status;
  }
}
