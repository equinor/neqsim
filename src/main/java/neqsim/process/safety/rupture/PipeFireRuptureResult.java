package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result from a blowdown pipe fire-rupture strain-rate study.
 *
 * <p>
 * The result keeps the calculation trace needed for an engineering review: pressure, mean and surface temperatures,
 * heat flux, stresses, strain rate, accumulated strain, rupture limit, and a spreadsheet-style release estimate at the
 * limiting condition.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PipeFireRuptureResult implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Limiting status for the rupture calculation. */
  public enum Status {
    /** Rupture strain was not reached within the simulated time. */
    NO_RUPTURE,
    /** Accumulated strain exceeded the temperature-dependent rupture strain limit. */
    RUPTURE
  }

  private final PipeFireRuptureInput input;
  private final PipeFireRuptureMaterial material;
  private final PipeFireRuptureScenario scenario;
  private final BlowdownPressureProfile pressureProfile;
  private final List<Double> timeSeconds;
  private final List<Double> pressureBarg;
  private final List<Double> meanWallTemperatureC;
  private final List<Double> outerSurfaceTemperatureC;
  private final List<Double> heatFluxKWPerM2;
  private final List<Double> vonMisesStressMPa;
  private final List<Double> strainRatePerMinute;
  private final List<Double> accumulatedStrain;
  private final List<Double> ruptureStrainLimit;
  private final List<String> warnings;
  private final List<String> recommendations;
  private final Status status;
  private final double ruptureTimeSeconds;
  private final double rupturePressureBarg;
  private final double ruptureMeanWallTemperatureC;
  private final double ruptureOuterSurfaceTemperatureC;
  private final double ruptureAccumulatedStrain;
  private final double ruptureStrainLimitValue;
  private final ReleaseEstimate releaseEstimate;

  /**
   * Creates a result object.
   *
   * @param builder populated builder
   */
  private PipeFireRuptureResult(Builder builder) {
    this.input = builder.input;
    this.material = builder.material;
    this.scenario = builder.scenario;
    this.pressureProfile = builder.pressureProfile;
    this.timeSeconds = immutableCopy(builder.timeSeconds);
    this.pressureBarg = immutableCopy(builder.pressureBarg);
    this.meanWallTemperatureC = immutableCopy(builder.meanWallTemperatureC);
    this.outerSurfaceTemperatureC = immutableCopy(builder.outerSurfaceTemperatureC);
    this.heatFluxKWPerM2 = immutableCopy(builder.heatFluxKWPerM2);
    this.vonMisesStressMPa = immutableCopy(builder.vonMisesStressMPa);
    this.strainRatePerMinute = immutableCopy(builder.strainRatePerMinute);
    this.accumulatedStrain = immutableCopy(builder.accumulatedStrain);
    this.ruptureStrainLimit = immutableCopy(builder.ruptureStrainLimit);
    this.warnings = Collections.unmodifiableList(new ArrayList<String>(builder.warnings));
    this.recommendations = Collections.unmodifiableList(new ArrayList<String>(builder.recommendations));
    this.status = builder.status;
    this.ruptureTimeSeconds = builder.ruptureTimeSeconds;
    this.rupturePressureBarg = builder.rupturePressureBarg;
    this.ruptureMeanWallTemperatureC = builder.ruptureMeanWallTemperatureC;
    this.ruptureOuterSurfaceTemperatureC = builder.ruptureOuterSurfaceTemperatureC;
    this.ruptureAccumulatedStrain = builder.ruptureAccumulatedStrain;
    this.ruptureStrainLimitValue = builder.ruptureStrainLimitValue;
    this.releaseEstimate = builder.releaseEstimate;
  }

  /**
   * Creates a result builder.
   *
   * @param input pipe input object
   * @param material material curve
   * @param scenario fire scenario
   * @param pressureProfile blowdown pressure profile
   * @return result builder
   */
  static Builder builder(PipeFireRuptureInput input, PipeFireRuptureMaterial material, PipeFireRuptureScenario scenario,
      BlowdownPressureProfile pressureProfile) {
    return new Builder(input, material, scenario, pressureProfile);
  }

  /**
   * Gets the study status.
   *
   * @return status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Checks whether rupture was predicted.
   *
   * @return true if rupture was predicted
   */
  public boolean isRupturePredicted() {
    return status == Status.RUPTURE;
  }

  /**
   * Gets rupture time.
   *
   * @return rupture time in seconds, or NaN if no rupture occurred
   */
  public double getRuptureTimeSeconds() {
    return ruptureTimeSeconds;
  }

  /**
   * Gets rupture pressure.
   *
   * @return rupture pressure in barg, or NaN if no rupture occurred
   */
  public double getRupturePressureBarg() {
    return rupturePressureBarg;
  }

  /**
   * Gets rupture mean wall temperature.
   *
   * @return rupture mean wall temperature in degrees Celsius
   */
  public double getRuptureMeanWallTemperatureC() {
    return ruptureMeanWallTemperatureC;
  }

  /**
   * Gets rupture outer surface temperature.
   *
   * @return rupture outer surface temperature in degrees Celsius
   */
  public double getRuptureOuterSurfaceTemperatureC() {
    return ruptureOuterSurfaceTemperatureC;
  }

  /**
   * Gets rupture accumulated strain.
   *
   * @return accumulated strain at rupture
   */
  public double getRuptureAccumulatedStrain() {
    return ruptureAccumulatedStrain;
  }

  /**
   * Gets rupture strain limit.
   *
   * @return rupture strain limit at rupture
   */
  public double getRuptureStrainLimitValue() {
    return ruptureStrainLimitValue;
  }

  /**
   * Gets release estimate at rupture.
   *
   * @return release estimate
   */
  public ReleaseEstimate getReleaseEstimate() {
    return releaseEstimate;
  }

  /**
   * Gets time history.
   *
   * @return immutable time history in seconds
   */
  public List<Double> getTimeSeconds() {
    return timeSeconds;
  }

  /**
   * Gets pressure history.
   *
   * @return immutable pressure history in barg
   */
  public List<Double> getPressureBarg() {
    return pressureBarg;
  }

  /**
   * Gets warnings.
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
   * Converts the result to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "pipe_fire_rupture_result.v1");
    map.put("segmentId", input.getSegmentId());
    map.put("status", status.name());
    map.put("rupturePredicted", Boolean.valueOf(isRupturePredicted()));
    map.put("ruptureTimeSeconds", finiteOrNull(ruptureTimeSeconds));
    map.put("ruptureTimeMinutes", finiteOrNull(ruptureTimeSeconds / 60.0));
    map.put("rupturePressureBarg", finiteOrNull(rupturePressureBarg));
    map.put("ruptureMeanWallTemperatureC", finiteOrNull(ruptureMeanWallTemperatureC));
    map.put("ruptureOuterSurfaceTemperatureC", finiteOrNull(ruptureOuterSurfaceTemperatureC));
    map.put("ruptureAccumulatedStrain", finiteOrNull(ruptureAccumulatedStrain));
    map.put("ruptureStrainLimit", finiteOrNull(ruptureStrainLimitValue));
    map.put("input", input.toMap());
    map.put("material", material.toMap());
    map.put("scenario", scenario.toMap());
    map.put("pressureProfile", pressureProfile.toMap());
    map.put("releaseEstimate", releaseEstimate == null ? null : releaseEstimate.toMap());
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
    map.put("pressureBarg", pressureBarg);
    map.put("meanWallTemperatureC", meanWallTemperatureC);
    map.put("outerSurfaceTemperatureC", outerSurfaceTemperatureC);
    map.put("heatFluxKWPerM2", heatFluxKWPerM2);
    map.put("vonMisesStressMPa", vonMisesStressMPa);
    map.put("strainRatePerMinute", strainRatePerMinute);
    map.put("accumulatedStrain", accumulatedStrain);
    map.put("ruptureStrainLimit", ruptureStrainLimit);
    return map;
  }

  /**
   * Copies a list to an immutable list.
   *
   * @param values list values
   * @return immutable copy
   */
  private static List<Double> immutableCopy(List<Double> values) {
    return Collections.unmodifiableList(new ArrayList<Double>(values));
  }

  /**
   * Converts finite values to JSON numbers and non-finite values to null.
   *
   * @param value numeric value
   * @return boxed value or null
   */
  private static Object finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }

  /** Spreadsheet-style release estimate at rupture. */
  public static final class ReleaseEstimate implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double longPipeGasTwoSidesKgPerS;
    private final double longPipeGasOneSideKgPerS;
    private final double shortPipeGasOneSideKgPerS;
    private final double liquidOneSideKgPerS;
    private final double gasTemperatureC;
    private final String basis;

    /**
     * Creates a release estimate.
     *
     * @param longPipeGasTwoSidesKgPerS long-pipe gas release from two sides in kg/s
     * @param longPipeGasOneSideKgPerS long-pipe gas release from one side in kg/s
     * @param shortPipeGasOneSideKgPerS short-pipe gas release from one side in kg/s
     * @param liquidOneSideKgPerS liquid release from one side in kg/s
     * @param gasTemperatureC gas temperature used in the release estimate in degrees Celsius
     * @param basis calculation basis text
     */
    public ReleaseEstimate(double longPipeGasTwoSidesKgPerS, double longPipeGasOneSideKgPerS,
        double shortPipeGasOneSideKgPerS, double liquidOneSideKgPerS, double gasTemperatureC, String basis) {
      this.longPipeGasTwoSidesKgPerS = longPipeGasTwoSidesKgPerS;
      this.longPipeGasOneSideKgPerS = longPipeGasOneSideKgPerS;
      this.shortPipeGasOneSideKgPerS = shortPipeGasOneSideKgPerS;
      this.liquidOneSideKgPerS = liquidOneSideKgPerS;
      this.gasTemperatureC = gasTemperatureC;
      this.basis = basis == null ? "" : basis;
    }

    /**
     * Gets long-pipe gas release from two sides.
     *
     * @return mass flow in kg/s
     */
    public double getLongPipeGasTwoSidesKgPerS() {
      return longPipeGasTwoSidesKgPerS;
    }

    /**
     * Gets long-pipe gas release from one side.
     *
     * @return mass flow in kg/s
     */
    public double getLongPipeGasOneSideKgPerS() {
      return longPipeGasOneSideKgPerS;
    }

    /**
     * Gets short-pipe gas release from one side.
     *
     * @return mass flow in kg/s
     */
    public double getShortPipeGasOneSideKgPerS() {
      return shortPipeGasOneSideKgPerS;
    }

    /**
     * Gets liquid release from one side.
     *
     * @return mass flow in kg/s
     */
    public double getLiquidOneSideKgPerS() {
      return liquidOneSideKgPerS;
    }

    /**
     * Converts the release estimate to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("longPipeGasTwoSidesKgPerS", longPipeGasTwoSidesKgPerS);
      map.put("longPipeGasOneSideKgPerS", longPipeGasOneSideKgPerS);
      map.put("shortPipeGasOneSideKgPerS", shortPipeGasOneSideKgPerS);
      map.put("liquidOneSideKgPerS", liquidOneSideKgPerS);
      map.put("gasTemperatureC", gasTemperatureC);
      map.put("basis", basis);
      return map;
    }
  }

  /** Builder used by {@link PipeFireRuptureStudy}. */
  static final class Builder {
    private final PipeFireRuptureInput input;
    private final PipeFireRuptureMaterial material;
    private final PipeFireRuptureScenario scenario;
    private final BlowdownPressureProfile pressureProfile;
    private final List<Double> timeSeconds = new ArrayList<Double>();
    private final List<Double> pressureBarg = new ArrayList<Double>();
    private final List<Double> meanWallTemperatureC = new ArrayList<Double>();
    private final List<Double> outerSurfaceTemperatureC = new ArrayList<Double>();
    private final List<Double> heatFluxKWPerM2 = new ArrayList<Double>();
    private final List<Double> vonMisesStressMPa = new ArrayList<Double>();
    private final List<Double> strainRatePerMinute = new ArrayList<Double>();
    private final List<Double> accumulatedStrain = new ArrayList<Double>();
    private final List<Double> ruptureStrainLimit = new ArrayList<Double>();
    private final List<String> warnings = new ArrayList<String>();
    private final List<String> recommendations = new ArrayList<String>();
    private Status status = Status.NO_RUPTURE;
    private double ruptureTimeSeconds = Double.NaN;
    private double rupturePressureBarg = Double.NaN;
    private double ruptureMeanWallTemperatureC = Double.NaN;
    private double ruptureOuterSurfaceTemperatureC = Double.NaN;
    private double ruptureAccumulatedStrain = Double.NaN;
    private double ruptureStrainLimitValue = Double.NaN;
    private ReleaseEstimate releaseEstimate;

    /**
     * Creates a builder.
     *
     * @param input pipe input object
     * @param material material curve
     * @param scenario fire scenario
     * @param pressureProfile pressure profile
     */
    private Builder(PipeFireRuptureInput input, PipeFireRuptureMaterial material, PipeFireRuptureScenario scenario,
        BlowdownPressureProfile pressureProfile) {
      this.input = input;
      this.material = material;
      this.scenario = scenario;
      this.pressureProfile = pressureProfile;
    }

    /**
     * Adds one time-series point.
     *
     * @param timeS time in seconds
     * @param pressure pressure in barg
     * @param meanTemperature mean wall temperature in degrees Celsius
     * @param surfaceTemperature outer surface temperature in degrees Celsius
     * @param heatFlux heat flux in kW/m2
     * @param stress von Mises stress in MPa
     * @param strainRate strain rate per minute
     * @param strain accumulated strain
     * @param strainLimit rupture strain limit
     */
    void addPoint(double timeS, double pressure, double meanTemperature, double surfaceTemperature, double heatFlux,
        double stress, double strainRate, double strain, double strainLimit) {
      timeSeconds.add(Double.valueOf(timeS));
      pressureBarg.add(Double.valueOf(pressure));
      meanWallTemperatureC.add(Double.valueOf(meanTemperature));
      outerSurfaceTemperatureC.add(Double.valueOf(surfaceTemperature));
      heatFluxKWPerM2.add(Double.valueOf(heatFlux));
      vonMisesStressMPa.add(Double.valueOf(stress));
      strainRatePerMinute.add(Double.valueOf(strainRate));
      accumulatedStrain.add(Double.valueOf(strain));
      ruptureStrainLimit.add(Double.valueOf(strainLimit));
    }

    /**
     * Records rupture at the current state.
     *
     * @param timeS rupture time in seconds
     * @param pressure rupture pressure in barg
     * @param meanTemperature mean wall temperature in degrees Celsius
     * @param surfaceTemperature surface temperature in degrees Celsius
     * @param strain accumulated strain
     * @param strainLimit rupture strain limit
     */
    void recordRupture(double timeS, double pressure, double meanTemperature, double surfaceTemperature, double strain,
        double strainLimit) {
      if (status == Status.NO_RUPTURE) {
        status = Status.RUPTURE;
        ruptureTimeSeconds = timeS;
        rupturePressureBarg = pressure;
        ruptureMeanWallTemperatureC = meanTemperature;
        ruptureOuterSurfaceTemperatureC = surfaceTemperature;
        ruptureAccumulatedStrain = strain;
        ruptureStrainLimitValue = strainLimit;
      }
    }

    /**
     * Adds a warning.
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
     * Sets release estimate.
     *
     * @param releaseEstimate release estimate
     */
    void releaseEstimate(ReleaseEstimate releaseEstimate) {
      this.releaseEstimate = releaseEstimate;
    }

    /**
     * Builds the result.
     *
     * @return pipe fire-rupture result
     */
    PipeFireRuptureResult build() {
      return new PipeFireRuptureResult(this);
    }
  }
}
