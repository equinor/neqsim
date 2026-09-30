package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Stateful advisory monitor for a tandem dry-gas-seal support system.
 *
 * <p>
 * Supply-process differential pressure, primary vent, intermediate buffer gas and bearing-side separation gas are
 * distinct measurements. All limits are caller supplied; this model does not infer an OEM failure diagnosis, issue
 * plant commands or establish a safety-integrity level. Run one instance per seal and call it once per non-overlapping
 * sample interval.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DryGasSealMonitor implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Independently observable support-system faults, not unique root-cause diagnoses. */
  public enum Fault {
    /** Missing, stale or nonphysical measurements. */
    INVALID_DATA,
    /** Seal supply cannot maintain its specified pressure margin above process. */
    LOW_SUPPLY_DIFFERENTIAL_PRESSURE,
    /** Primary vent normal-volume flow exceeds the caller's leakage threshold. */
    HIGH_PRIMARY_VENT_FLOW,
    /** Excessive primary vent backpressure. */
    HIGH_PRIMARY_VENT_PRESSURE,
    /** Insufficient intermediate buffer gas. */
    LOW_BUFFER_FLOW,
    /** Insufficient bearing-side separation pressure barrier. */
    LOW_SEPARATION_DIFFERENTIAL_PRESSURE,
    /** Independently assessed gas quality is unacceptable or unresolved. */
    GAS_QUALITY
  }

  /** Immutable limits; pressures are absolute except explicitly named differential pressures. */
  public static final class Limits implements Serializable {
    private static final long serialVersionUID = 1000L;
    /** Minimum supply-process differential pressure in bar. */
    private final double minSupplyDifferentialBar;
    /** Maximum primary vent flow, ideal normal litres/min at 273.15 K and 1.01325 bara. */
    private final double maxPrimaryVentFlowNLmin;
    /** Maximum primary vent absolute pressure in bara. */
    private final double maxPrimaryVentPressureBara;
    /** Minimum buffer flow on the same normal-volume basis. */
    private final double minBufferFlowNLmin;
    /** Minimum separation-to-bearing differential pressure in bar. */
    private final double minSeparationDifferentialBar;
    /** Maximum sample age in seconds. */
    private final double maxSampleAgeSeconds;
    /** Required continuous fault duration in seconds. */
    private final double confirmationDelaySeconds;

    /**
     * Creates OEM/project-defined limits without assumed universal default values.
     * 
     * @param minSupplyDifferentialBar Minimum supply-process differential pressure in bar.
     * @param maxPrimaryVentFlowNLmin Maximum primary vent flow, ideal normal litres/min at 273.15 K and 1.01325 bara.
     * @param maxPrimaryVentPressureBara Maximum primary vent absolute pressure in bara.
     * @param minBufferFlowNLmin Minimum buffer flow on the same normal-volume basis.
     * @param minSeparationDifferentialBar Minimum separation-to-bearing differential pressure in bar.
     * @param maxSampleAgeSeconds Maximum sample age in seconds.
     * @param confirmationDelaySeconds Required continuous fault duration in seconds.
     * @throws IllegalArgumentException if a limit is nonfinite, negative or vent pressure is zero
     */
    public Limits(double minSupplyDifferentialBar, double maxPrimaryVentFlowNLmin, double maxPrimaryVentPressureBara,
        double minBufferFlowNLmin, double minSeparationDifferentialBar, double maxSampleAgeSeconds,
        double confirmationDelaySeconds) {
      requireNonNegative(minSupplyDifferentialBar, "minSupplyDifferentialBar");
      this.minSupplyDifferentialBar = minSupplyDifferentialBar;
      requireNonNegative(maxPrimaryVentFlowNLmin, "maxPrimaryVentFlowNLmin");
      this.maxPrimaryVentFlowNLmin = maxPrimaryVentFlowNLmin;
      requireNonNegative(maxPrimaryVentPressureBara, "maxPrimaryVentPressureBara");
      this.maxPrimaryVentPressureBara = maxPrimaryVentPressureBara;
      requireNonNegative(minBufferFlowNLmin, "minBufferFlowNLmin");
      this.minBufferFlowNLmin = minBufferFlowNLmin;
      requireNonNegative(minSeparationDifferentialBar, "minSeparationDifferentialBar");
      this.minSeparationDifferentialBar = minSeparationDifferentialBar;
      requireNonNegative(maxSampleAgeSeconds, "maxSampleAgeSeconds");
      this.maxSampleAgeSeconds = maxSampleAgeSeconds;
      requireNonNegative(confirmationDelaySeconds, "confirmationDelaySeconds");
      this.confirmationDelaySeconds = confirmationDelaySeconds;
      if (maxPrimaryVentPressureBara == 0.0) {
        throw new IllegalArgumentException("Maximum vent pressure must be positive");
      }
    }
  }

  /** Immutable, caller-normalized sensor scan. */
  public static final class Sample implements Serializable {
    private static final long serialVersionUID = 1000L;
    /** Seal supply absolute pressure in bara. */
    private final double supplyPressureBara;
    /** Process-side absolute pressure in bara. */
    private final double processPressureBara;
    /** Primary vent normal flow in NL/min. */
    private final double primaryVentFlowNLmin;
    /** Primary vent absolute pressure in bara. */
    private final double primaryVentPressureBara;
    /** Intermediate buffer normal flow in NL/min. */
    private final double bufferFlowNLmin;
    /** Bearing-side separation pressure minus bearing cavity pressure in bar. */
    private final double separationDifferentialBar;
    /** Age of the oldest required measurement in seconds. */
    private final double ageSeconds;
    /** True when all required sensors have valid quality flags. */
    private final boolean instrumentsValid;
    /** True only when independent gas-quality evidence passes. */
    private final boolean gasQualityAcceptable;

    /**
     * Creates a scan. Invalid/nonfinite values are retained and reported by the monitor.
     * 
     * @param supplyPressureBara Seal supply absolute pressure in bara.
     * @param processPressureBara Process-side absolute pressure in bara.
     * @param primaryVentFlowNLmin Primary vent normal flow in NL/min.
     * @param primaryVentPressureBara Primary vent absolute pressure in bara.
     * @param bufferFlowNLmin Intermediate buffer normal flow in NL/min.
     * @param separationDifferentialBar Bearing-side separation pressure minus bearing cavity pressure in bar.
     * @param ageSeconds Age of the oldest required measurement in seconds.
     * @param instrumentsValid True when all required sensors have valid quality flags.
     * @param gasQualityAcceptable True only when independent gas-quality evidence passes.
     */
    public Sample(double supplyPressureBara, double processPressureBara, double primaryVentFlowNLmin,
        double primaryVentPressureBara, double bufferFlowNLmin, double separationDifferentialBar, double ageSeconds,
        boolean instrumentsValid, boolean gasQualityAcceptable) {
      this.supplyPressureBara = supplyPressureBara;
      this.processPressureBara = processPressureBara;
      this.primaryVentFlowNLmin = primaryVentFlowNLmin;
      this.primaryVentPressureBara = primaryVentPressureBara;
      this.bufferFlowNLmin = bufferFlowNLmin;
      this.separationDifferentialBar = separationDifferentialBar;
      this.ageSeconds = ageSeconds;
      this.instrumentsValid = instrumentsValid;
      this.gasQualityAcceptable = gasQualityAcceptable;
    }
  }

  /** Detached scan result, including current faults and latched confirmed recommendations. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Set<Fault> activeFaults;
    private final Set<Fault> confirmedFaults;
    private final Map<Fault, Double> elapsedSeconds;

    /**
     * Copies monitor state into an immutable result.
     *
     * @param active current faults
     * @param confirmed latched faults
     * @param elapsed continuous duration per fault
     */
    private Result(EnumSet<Fault> active, EnumSet<Fault> confirmed, EnumMap<Fault, Double> elapsed) {
      activeFaults = Collections.unmodifiableSet(active.clone());
      confirmedFaults = Collections.unmodifiableSet(confirmed.clone());
      elapsedSeconds = Collections.unmodifiableMap(new EnumMap<Fault, Double>(elapsed));
    }

    /**
     * Gets simultaneous current faults.
     *
     * @return immutable set
     */
    public Set<Fault> getActiveFaults() {
      return activeFaults;
    }

    /**
     * Gets confirmed fault recommendations retained until explicit reset.
     *
     * @return immutable set
     */
    public Set<Fault> getConfirmedFaults() {
      return confirmedFaults;
    }

    /**
     * Gets continuous fault duration; invalid scans reset unconfirmed physical timers.
     *
     * @return immutable map in seconds
     */
    public Map<Fault, Double> getElapsedSeconds() {
      return elapsedSeconds;
    }

    /**
     * Checks whether this scan's required measurement evidence is valid.
     *
     * @return false for stale, absent or invalid data
     */
    public boolean isDataValid() {
      return !activeFaults.contains(Fault.INVALID_DATA);
    }

    /**
     * Returns a simulated trip recommendation after persistent faults or invalid evidence.
     *
     * @return true if at least one confirmed fault is latched
     */
    public boolean isTripRecommended() {
      return !confirmedFaults.isEmpty();
    }
  }

  private final Limits limits;
  private final EnumMap<Fault, Double> elapsed = new EnumMap<>(Fault.class);
  private final EnumSet<Fault> confirmed = EnumSet.noneOf(Fault.class);

  /**
   * Creates one per-seal monitor.
   *
   * @param limits caller-supplied limits
   * @throws IllegalArgumentException if limits is null
   */
  public DryGasSealMonitor(Limits limits) {
    if (limits == null) {
      throw new IllegalArgumentException("Limits must not be null");
    }
    this.limits = limits;
  }

  /**
   * Evaluates one scan and advances continuous-fault timers. Invalid evidence is confirmed immediately; it cannot be
   * interpreted as healthy or as confirmed physical seal damage. Other faults must persist for the configured delay.
   * Recommendations latch until reset.
   *
   * @param sample sensor scan, or null for missing evidence
   * @param intervalSeconds duration represented by this scan, finite and positive
   * @return detached advisory evidence
   * @throws IllegalArgumentException if the interval is nonfinite or nonpositive
   */
  public Result evaluate(Sample sample, double intervalSeconds) {
    if (!Double.isFinite(intervalSeconds) || intervalSeconds <= 0.0) {
      throw new IllegalArgumentException("Scan interval must be finite and positive");
    }
    EnumSet<Fault> active = EnumSet.noneOf(Fault.class);
    if (!validSample(sample)) {
      active.add(Fault.INVALID_DATA);
      confirmed.add(Fault.INVALID_DATA);
    } else {
      if (sample.supplyPressureBara - sample.processPressureBara < limits.minSupplyDifferentialBar) {
        active.add(Fault.LOW_SUPPLY_DIFFERENTIAL_PRESSURE);
      }
      if (sample.primaryVentFlowNLmin > limits.maxPrimaryVentFlowNLmin) {
        active.add(Fault.HIGH_PRIMARY_VENT_FLOW);
      }
      if (sample.primaryVentPressureBara > limits.maxPrimaryVentPressureBara) {
        active.add(Fault.HIGH_PRIMARY_VENT_PRESSURE);
      }
      if (sample.bufferFlowNLmin < limits.minBufferFlowNLmin) {
        active.add(Fault.LOW_BUFFER_FLOW);
      }
      if (sample.separationDifferentialBar < limits.minSeparationDifferentialBar) {
        active.add(Fault.LOW_SEPARATION_DIFFERENTIAL_PRESSURE);
      }
      if (!sample.gasQualityAcceptable) {
        active.add(Fault.GAS_QUALITY);
      }
    }
    for (Fault fault : Fault.values()) {
      double duration = active.contains(fault) ? elapsed.getOrDefault(fault, 0.0) + intervalSeconds : 0.0;
      elapsed.put(fault, duration);
      if (active.contains(fault) && duration >= limits.confirmationDelaySeconds) {
        confirmed.add(fault);
      }
    }
    return new Result(active, confirmed, elapsed);
  }

  /** Resets all latched recommendations and timers; an active fault is detected on the next scan. */
  public void reset() {
    elapsed.clear();
    confirmed.clear();
  }

  /**
   * Checks sensor quality, physical ranges and freshness. A negative differential is a real barrier failure, whereas a
   * negative absolute pressure or flow is invalid instrumentation.
   *
   * @param sample sensor scan
   * @return true for complete valid evidence
   */
  private boolean validSample(Sample sample) {
    return sample != null && sample.instrumentsValid && Double.isFinite(sample.supplyPressureBara)
        && sample.supplyPressureBara > 0 && Double.isFinite(sample.processPressureBara)
        && sample.processPressureBara > 0 && Double.isFinite(sample.primaryVentPressureBara)
        && sample.primaryVentPressureBara > 0 && finiteNonNegative(sample.primaryVentFlowNLmin)
        && finiteNonNegative(sample.bufferFlowNLmin) && Double.isFinite(sample.separationDifferentialBar)
        && finiteNonNegative(sample.ageSeconds) && sample.ageSeconds <= limits.maxSampleAgeSeconds;
  }

  /**
   * Checks a sensor scalar for finite nonnegative range.
   *
   * @param value measured value
   * @return true if valid
   */
  private static boolean finiteNonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  /**
   * Validates a limit.
   *
   * @param value configured limit
   * @param name field name
   * @throws IllegalArgumentException if invalid
   */
  private static void requireNonNegative(double value, String name) {
    if (!finiteNonNegative(value)) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }
}
