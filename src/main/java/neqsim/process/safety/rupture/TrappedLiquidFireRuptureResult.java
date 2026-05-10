package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.barrier.SafetySystemCategory;
import neqsim.process.safety.barrier.SafetySystemDemand;
import neqsim.process.safety.release.LeakModel;
import neqsim.process.safety.release.ReleaseOrientation;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Result from a trapped-liquid fire rupture screening study.
 *
 * <p>
 * The result records time histories and key event times so the same calculation can feed safety
 * reports, PFP demand checks, and release/source-term handoffs for detailed consequence analysis.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TrappedLiquidFireRuptureResult implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Limiting outcome for the study. */
  public enum FailureMode {
    /** No failure or demand reached within the simulated time. */
    NONE,
    /** Pressure reached the configured relief set pressure. */
    RELIEF_SET_PRESSURE,
    /** Vapor phase appeared in the trapped liquid envelope. */
    VAPOR_POCKET,
    /** Pipe wall stress exceeded temperature-reduced rupture strength. */
    PIPE_RUPTURE,
    /** Flange pressure rating was exceeded after temperature reduction. */
    FLANGE_FAILURE
  }

  private final String segmentId;
  private final double pipeInternalDiameterM;
  private final double inventoryVolumeM3;
  private final List<Double> timeSeconds;
  private final List<Double> pressureBara;
  private final List<Double> liquidTemperatureK;
  private final List<Double> innerWallTemperatureK;
  private final List<Double> outerWallTemperatureK;
  private final List<Double> vonMisesStressMPa;
  private final List<Double> allowableStressMPa;
  private final List<Double> flangeRatingBara;
  private final List<String> standardsApplied;
  private final List<String> warnings;
  private final List<String> recommendations;
  private final FailureMode limitingFailureMode;
  private final double timeToReliefSetSeconds;
  private final double timeToVaporPocketSeconds;
  private final double timeToPipeRuptureSeconds;
  private final double timeToFlangeFailureSeconds;
  private final double finalPressureBara;
  private final double finalLiquidTemperatureK;
  private final double finalOuterWallTemperatureK;

  /**
   * Creates a rupture study result.
   *
   * @param builder populated result builder
   */
  private TrappedLiquidFireRuptureResult(Builder builder) {
    this.segmentId = builder.segmentId;
    this.pipeInternalDiameterM = builder.pipeInternalDiameterM;
    this.inventoryVolumeM3 = builder.inventoryVolumeM3;
    this.timeSeconds = immutableCopy(builder.timeSeconds);
    this.pressureBara = immutableCopy(builder.pressureBara);
    this.liquidTemperatureK = immutableCopy(builder.liquidTemperatureK);
    this.innerWallTemperatureK = immutableCopy(builder.innerWallTemperatureK);
    this.outerWallTemperatureK = immutableCopy(builder.outerWallTemperatureK);
    this.vonMisesStressMPa = immutableCopy(builder.vonMisesStressMPa);
    this.allowableStressMPa = immutableCopy(builder.allowableStressMPa);
    this.flangeRatingBara = immutableCopy(builder.flangeRatingBara);
    this.standardsApplied = Collections.unmodifiableList(new ArrayList<String>(
        builder.standardsApplied));
    this.warnings = Collections.unmodifiableList(new ArrayList<String>(builder.warnings));
    this.recommendations = Collections.unmodifiableList(new ArrayList<String>(
        builder.recommendations));
    this.limitingFailureMode = builder.limitingFailureMode;
    this.timeToReliefSetSeconds = builder.timeToReliefSetSeconds;
    this.timeToVaporPocketSeconds = builder.timeToVaporPocketSeconds;
    this.timeToPipeRuptureSeconds = builder.timeToPipeRuptureSeconds;
    this.timeToFlangeFailureSeconds = builder.timeToFlangeFailureSeconds;
    this.finalPressureBara = lastOrNaN(this.pressureBara);
    this.finalLiquidTemperatureK = lastOrNaN(this.liquidTemperatureK);
    this.finalOuterWallTemperatureK = lastOrNaN(this.outerWallTemperatureK);
  }

  /**
   * Creates a result builder.
   *
   * @param segmentId segment identifier
   * @return result builder
   */
  static Builder builder(String segmentId) {
    return new Builder(segmentId);
  }

  /**
   * Gets the segment identifier.
   *
   * @return segment identifier
   */
  public String getSegmentId() {
    return segmentId;
  }

  /**
   * Gets the limiting failure mode.
   *
   * @return limiting failure mode
   */
  public FailureMode getLimitingFailureMode() {
    return limitingFailureMode;
  }

  /**
   * Gets whether pipe or flange failure was predicted.
   *
   * @return true if pipe rupture or flange failure occurred within the simulated time
   */
  public boolean isRupturePredicted() {
    return limitingFailureMode == FailureMode.PIPE_RUPTURE
        || limitingFailureMode == FailureMode.FLANGE_FAILURE;
  }

  /**
   * Gets the time to relief set pressure.
   *
   * @return time in s, or NaN when not reached
   */
  public double getTimeToReliefSetSeconds() {
    return timeToReliefSetSeconds;
  }

  /**
   * Gets the time to vapor-pocket indication.
   *
   * @return time in s, or NaN when not reached
   */
  public double getTimeToVaporPocketSeconds() {
    return timeToVaporPocketSeconds;
  }

  /**
   * Gets the time to pipe rupture.
   *
   * @return time in s, or NaN when not reached
   */
  public double getTimeToPipeRuptureSeconds() {
    return timeToPipeRuptureSeconds;
  }

  /**
   * Gets the time to flange failure.
   *
   * @return time in s, or NaN when not reached
   */
  public double getTimeToFlangeFailureSeconds() {
    return timeToFlangeFailureSeconds;
  }

  /**
   * Gets the earliest pipe or flange failure time.
   *
   * @return failure time in s, or NaN when no pipe/flange failure occurred
   */
  public double getMinimumFailureTimeSeconds() {
    return minimumFinite(timeToPipeRuptureSeconds, timeToFlangeFailureSeconds);
  }

  /**
   * Gets final pressure.
   *
   * @return final pressure in bara
   */
  public double getFinalPressureBara() {
    return finalPressureBara;
  }

  /**
   * Gets final liquid temperature.
   *
   * @return final liquid temperature in K
   */
  public double getFinalLiquidTemperatureK() {
    return finalLiquidTemperatureK;
  }

  /**
   * Gets final outer wall temperature.
   *
   * @return final outer wall temperature in K
   */
  public double getFinalOuterWallTemperatureK() {
    return finalOuterWallTemperatureK;
  }

  /**
   * Gets pressure history.
   *
   * @return pressure history in bara
   */
  public List<Double> getPressureBara() {
    return pressureBara;
  }

  /**
   * Gets time history.
   *
   * @return time history in s
   */
  public List<Double> getTimeSeconds() {
    return timeSeconds;
  }

  /**
   * Gets warning messages.
   *
   * @return immutable warning list
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Gets recommendations.
   *
   * @return immutable recommendation list
   */
  public List<String> getRecommendations() {
    return recommendations;
  }

  /**
   * Creates a passive fire protection demand case from this result.
   *
   * @param demandId stable demand identifier
   * @param requiredEnduranceSeconds required PFP endurance in seconds
   * @return safety-system demand record
   */
  public SafetySystemDemand toPassiveFireProtectionDemand(String demandId,
      double requiredEnduranceSeconds) {
    SafetySystemDemand demand = new SafetySystemDemand(demandId).setEquipmentTag(segmentId)
        .setScenario("Trapped liquid fire rupture exposure").setCategory(
            SafetySystemCategory.PASSIVE_FIRE_PROTECTION).setDemandValue(requiredEnduranceSeconds)
        .setDemandUnit("s").setCapacityValue(getMinimumFailureTimeSeconds());
    if (Double.isFinite(getMinimumFailureTimeSeconds())) {
      demand.setRequiredResponseTimeSeconds(requiredEnduranceSeconds);
      demand.setActualResponseTimeSeconds(getMinimumFailureTimeSeconds());
    }
    return demand;
  }

  /**
   * Creates a full-bore source term using the result state at limiting failure.
   *
   * @param fluid representative fluid to clone and set to failure state
   * @param orientation release orientation; defaults to horizontal when null
   * @param durationSeconds source-term duration in s; must be positive
   * @param timeStepSeconds source-term time step in s; must be positive
   * @return source term result from the existing release model
   */
  public SourceTermResult createRuptureSourceTerm(SystemInterface fluid,
      ReleaseOrientation orientation, double durationSeconds, double timeStepSeconds) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (durationSeconds <= 0.0 || timeStepSeconds <= 0.0) {
      throw new IllegalArgumentException("duration and time step must be positive");
    }
    SystemInterface sourceFluid = fluid.clone();
    sourceFluid.setPressure(finalPressureBara, "bara");
    sourceFluid.setTemperature(finalLiquidTemperatureK);
    ThermodynamicOperations operations = new ThermodynamicOperations(sourceFluid);
    operations.TPflash();
    sourceFluid.initProperties();
    ReleaseOrientation selectedOrientation = orientation == null ? ReleaseOrientation.HORIZONTAL
        : orientation;
    return LeakModel.builder().fluid(sourceFluid).holeDiameter(pipeInternalDiameterM)
        .orientation(selectedOrientation).vesselVolume(inventoryVolumeM3).backPressure(1.01325,
            "bar").scenarioName("Full-bore rupture from " + segmentId).build()
        .calculateSourceTerm(durationSeconds, timeStepSeconds);
  }

  /**
   * Converts the result to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("segmentId", segmentId);
    map.put("limitingFailureMode", limitingFailureMode.name());
    map.put("rupturePredicted", isRupturePredicted());
    map.put("timeToReliefSetSeconds", finiteOrNull(timeToReliefSetSeconds));
    map.put("timeToVaporPocketSeconds", finiteOrNull(timeToVaporPocketSeconds));
    map.put("timeToPipeRuptureSeconds", finiteOrNull(timeToPipeRuptureSeconds));
    map.put("timeToFlangeFailureSeconds", finiteOrNull(timeToFlangeFailureSeconds));
    map.put("minimumFailureTimeSeconds", finiteOrNull(getMinimumFailureTimeSeconds()));
    map.put("finalPressureBara", finalPressureBara);
    map.put("finalLiquidTemperatureK", finalLiquidTemperatureK);
    map.put("finalOuterWallTemperatureK", finalOuterWallTemperatureK);
    map.put("standardsApplied", standardsApplied);
    map.put("warnings", warnings);
    map.put("recommendations", recommendations);
    map.put("timeSeries", timeSeriesMap());
    return map;
  }

  /**
   * Converts the result to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toMap());
  }

  /**
   * Creates a JSON-friendly time-series map.
   *
   * @return ordered time-series map
   */
  private Map<String, Object> timeSeriesMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("timeSeconds", timeSeconds);
    map.put("pressureBara", pressureBara);
    map.put("liquidTemperatureK", liquidTemperatureK);
    map.put("innerWallTemperatureK", innerWallTemperatureK);
    map.put("outerWallTemperatureK", outerWallTemperatureK);
    map.put("vonMisesStressMPa", vonMisesStressMPa);
    map.put("allowableStressMPa", allowableStressMPa);
    map.put("flangeRatingBara", flangeRatingBara);
    return map;
  }

  /**
   * Copies a list to an immutable list.
   *
   * @param values source list
   * @return immutable copy
   */
  private static List<Double> immutableCopy(List<Double> values) {
    return Collections.unmodifiableList(new ArrayList<Double>(values));
  }

  /**
   * Gets the last value in a list or NaN.
   *
   * @param values value list
   * @return last value, or NaN when empty
   */
  private static double lastOrNaN(List<Double> values) {
    return values.isEmpty() ? Double.NaN : values.get(values.size() - 1);
  }

  /**
   * Converts finite numbers to themselves and non-finite numbers to null.
   *
   * @param value numeric value
   * @return finite number or null
   */
  private static Object finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }

  /**
   * Gets the minimum finite value from two candidates.
   *
   * @param first first value
   * @param second second value
   * @return minimum finite value, or NaN when neither value is finite
   */
  private static double minimumFinite(double first, double second) {
    if (Double.isFinite(first) && Double.isFinite(second)) {
      return Math.min(first, second);
    }
    if (Double.isFinite(first)) {
      return first;
    }
    if (Double.isFinite(second)) {
      return second;
    }
    return Double.NaN;
  }

  /** Builder used by the study implementation. */
  static final class Builder {
    private final String segmentId;
    private double pipeInternalDiameterM;
    private double inventoryVolumeM3;
    private final List<Double> timeSeconds = new ArrayList<Double>();
    private final List<Double> pressureBara = new ArrayList<Double>();
    private final List<Double> liquidTemperatureK = new ArrayList<Double>();
    private final List<Double> innerWallTemperatureK = new ArrayList<Double>();
    private final List<Double> outerWallTemperatureK = new ArrayList<Double>();
    private final List<Double> vonMisesStressMPa = new ArrayList<Double>();
    private final List<Double> allowableStressMPa = new ArrayList<Double>();
    private final List<Double> flangeRatingBara = new ArrayList<Double>();
    private final List<String> standardsApplied = new ArrayList<String>();
    private final List<String> warnings = new ArrayList<String>();
    private final List<String> recommendations = new ArrayList<String>();
    private FailureMode limitingFailureMode = FailureMode.NONE;
    private double timeToReliefSetSeconds = Double.NaN;
    private double timeToVaporPocketSeconds = Double.NaN;
    private double timeToPipeRuptureSeconds = Double.NaN;
    private double timeToFlangeFailureSeconds = Double.NaN;

    /**
     * Creates a builder.
     *
     * @param segmentId segment identifier
     */
    private Builder(String segmentId) {
      this.segmentId = segmentId;
    }

    /**
     * Sets geometry needed for source-term handoff.
     *
     * @param pipeInternalDiameterM pipe internal diameter in m
     * @param inventoryVolumeM3 inventory volume in m3
     * @return this builder
     */
    Builder geometry(double pipeInternalDiameterM, double inventoryVolumeM3) {
      this.pipeInternalDiameterM = pipeInternalDiameterM;
      this.inventoryVolumeM3 = inventoryVolumeM3;
      return this;
    }

    /**
     * Adds one time-series point.
     *
     * @param timeS time in s
     * @param pressureBar pressure in bara
     * @param liquidTemperature temperature in K
     * @param innerWallTemperature inner wall temperature in K
     * @param outerWallTemperature outer wall temperature in K
     * @param stressMpa von Mises stress in MPa
     * @param allowableMpa allowable stress in MPa
     * @param flangeRatingBar flange rating in bara
     * @return this builder
     */
    Builder addPoint(double timeS, double pressureBar, double liquidTemperature,
        double innerWallTemperature, double outerWallTemperature, double stressMpa,
        double allowableMpa, double flangeRatingBar) {
      timeSeconds.add(timeS);
      pressureBara.add(pressureBar);
      liquidTemperatureK.add(liquidTemperature);
      innerWallTemperatureK.add(innerWallTemperature);
      outerWallTemperatureK.add(outerWallTemperature);
      vonMisesStressMPa.add(stressMpa);
      allowableStressMPa.add(allowableMpa);
      flangeRatingBara.add(flangeRatingBar);
      return this;
    }

    /**
     * Records the first relief set pressure demand.
     *
     * @param timeS event time in s
     */
    void recordReliefSet(double timeS) {
      if (!Double.isFinite(timeToReliefSetSeconds)) {
        timeToReliefSetSeconds = timeS;
      }
    }

    /**
     * Records the first vapor pocket indication.
     *
     * @param timeS event time in s
     */
    void recordVaporPocket(double timeS) {
      if (!Double.isFinite(timeToVaporPocketSeconds)) {
        timeToVaporPocketSeconds = timeS;
      }
    }

    /**
     * Records pipe rupture.
     *
     * @param timeS event time in s
     */
    void recordPipeRupture(double timeS) {
      if (!Double.isFinite(timeToPipeRuptureSeconds)) {
        timeToPipeRuptureSeconds = timeS;
      }
    }

    /**
     * Records flange failure.
     *
     * @param timeS event time in s
     */
    void recordFlangeFailure(double timeS) {
      if (!Double.isFinite(timeToFlangeFailureSeconds)) {
        timeToFlangeFailureSeconds = timeS;
      }
    }

    /**
     * Adds a warning message.
     *
     * @param warning warning text
     */
    void addWarning(String warning) {
      warnings.add(warning);
    }

    /**
     * Adds a recommendation.
     *
     * @param recommendation recommendation text
     */
    void addRecommendation(String recommendation) {
      recommendations.add(recommendation);
    }

    /**
     * Adds an applied standard reference.
     *
     * @param standard standard text
     */
    void addStandard(String standard) {
      standardsApplied.add(standard);
    }

    /**
     * Builds the result.
     *
     * @return rupture study result
     */
    TrappedLiquidFireRuptureResult build() {
      limitingFailureMode = determineLimitingFailureMode();
      return new TrappedLiquidFireRuptureResult(this);
    }

    /**
     * Determines the limiting failure mode from event times.
     *
     * @return limiting failure mode
     */
    private FailureMode determineLimitingFailureMode() {
      double bestTime = Double.POSITIVE_INFINITY;
      FailureMode mode = FailureMode.NONE;
      if (Double.isFinite(timeToPipeRuptureSeconds) && timeToPipeRuptureSeconds < bestTime) {
        bestTime = timeToPipeRuptureSeconds;
        mode = FailureMode.PIPE_RUPTURE;
      }
      if (Double.isFinite(timeToFlangeFailureSeconds) && timeToFlangeFailureSeconds < bestTime) {
        bestTime = timeToFlangeFailureSeconds;
        mode = FailureMode.FLANGE_FAILURE;
      }
      if (mode == FailureMode.NONE && Double.isFinite(timeToReliefSetSeconds)) {
        mode = FailureMode.RELIEF_SET_PRESSURE;
      }
      if (mode == FailureMode.NONE && Double.isFinite(timeToVaporPocketSeconds)) {
        mode = FailureMode.VAPOR_POCKET;
      }
      return mode;
    }
  }
}
