package neqsim.process.safety.depressurization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.flare.Flare.CapacityCheckResult;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource.BlowdownSource;
import neqsim.process.safety.depressurization.MultiVesselBlowdownStudy.MultiVesselBlowdownResult;
import neqsim.process.safety.rupture.SafetyStudyReadiness;
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.process.util.fire.ReliefValveSizing.PSVSizingResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Readiness-gated dynamic blowdown, PSV sizing, and flare-load study runner.
 *
 * <p>
 * The runner converts a governed {@link DynamicBlowdownFlareStudyDataSource} into NeqSim transient calculations. It
 * runs each source through {@link DepressurizationSimulator}, superimposes source loads with
 * {@link MultiVesselBlowdownStudy}, sizes PSV orifices where a PSV basis is supplied, and builds a compact flare-load
 * handoff with peak heat duty, cumulative emissions, radiation distance, and capacity checks.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class DynamicBlowdownFlareStudyRunner implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final double DEFAULT_TIME_STEP_SECONDS = 1.0;
  private static final double DEFAULT_MAX_TIME_SECONDS = 900.0;
  private static final double DEFAULT_RADIATION_THRESHOLD_W_PER_M2 = 4000.0;

  private final double timeStepSeconds;
  private final double maxTimeSeconds;
  private final double radiationThresholdWPerM2;

  /**
   * Creates a runner.
   *
   * @param builder populated builder
   */
  private DynamicBlowdownFlareStudyRunner(Builder builder) {
    builder.validate();
    this.timeStepSeconds = builder.timeStepSeconds;
    this.maxTimeSeconds = builder.maxTimeSeconds;
    this.radiationThresholdWPerM2 = builder.radiationThresholdWPerM2;
  }

  /**
   * Creates a builder.
   *
   * @return runner builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Runs a governed dynamic blowdown and flare-load study.
   *
   * @param dataSource study data source
   * @return versioned study handoff
   */
  public DynamicBlowdownFlareStudyHandoff run(DynamicBlowdownFlareStudyDataSource dataSource) {
    DynamicBlowdownFlareStudyDataSource source = dataSource == null
        ? DynamicBlowdownFlareStudyDataSource.builder("missing-data-source")
            .addGap("DynamicBlowdownFlareStudyDataSource was null.").build()
        : dataSource;
    SafetyStudyReadiness calculationReadiness = source.readiness();
    Map<String, Object> result = null;
    Map<String, Object> flareLoadHandoff = null;
    if (calculationReadiness.isReadyForCalculation()) {
      result = runCalculation(source);
      flareLoadHandoff = buildFlareLoadHandoff(source, result);
    }
    SafetyStudyReadiness standardsReadiness = standardsReadiness(source, result);
    return DynamicBlowdownFlareStudyHandoff.builder(source).calculationReadiness(calculationReadiness)
        .standardsReadiness(standardsReadiness).result(result).flareLoadHandoff(flareLoadHandoff).build();
  }

  /**
   * Executes all transient calculations.
   *
   * @param dataSource study data source
   * @return JSON-friendly result map
   */
  private Map<String, Object> runCalculation(DynamicBlowdownFlareStudyDataSource dataSource) {
    Map<String, DepressurizationResult> sourceResults = new LinkedHashMap<String, DepressurizationResult>();
    MultiVesselBlowdownStudy multiVesselStudy = new MultiVesselBlowdownStudy().setGridStep(timeStepSeconds)
        .setMaxAllowableMach(dataSource.getMaxAllowableHeaderMach());
    if (Double.isFinite(dataSource.getHeaderDiameterM()) && dataSource.getHeaderDiameterM() > 0.0) {
      multiVesselStudy.setHeader(dataSource.getHeaderDiameterM(), dataSource.getHeaderPressureBara(),
          dataSource.getHeaderTemperatureK(), dataSource.getHeaderMolarMassKgPerMol(), dataSource.getHeaderGamma());
    }
    Map<String, Object> psvSizing = new LinkedHashMap<String, Object>();
    for (BlowdownSource source : dataSource.getSources()) {
      DepressurizationSimulator simulator = createSimulator(source);
      DepressurizationResult result = simulator.run();
      sourceResults.put(source.getSourceId(), result);
      multiVesselStudy.addSourceResult(source.getSourceId(), result);
      psvSizing.put(source.getSourceId(), psvSizing(source, result));
    }
    MultiVesselBlowdownResult combined = multiVesselStudy.run();
    Map<String, Object> flareLoad = flareLoadMap(dataSource, combined);

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "dynamic_blowdown_flare_result.v1");
    map.put("studyId", dataSource.getStudyId());
    map.put("timeStepSeconds", Double.valueOf(timeStepSeconds));
    map.put("maxTimeSeconds", Double.valueOf(maxTimeSeconds));
    map.put("sourceResults", sourceResultMaps(sourceResults));
    map.put("combinedLoad", combinedLoadMap(combined));
    map.put("psvSizing", psvSizing);
    map.put("flareLoad", flareLoad);
    return map;
  }

  /**
   * Creates a configured depressurization simulator.
   *
   * @param source blowdown source
   * @return configured simulator
   */
  private DepressurizationSimulator createSimulator(BlowdownSource source) {
    SystemInterface fluid = source.getFluid().clone();
    DepressurizationSimulator simulator = new DepressurizationSimulator(fluid, source.getVesselVolumeM3(),
        source.getOrificeDiameterM(), source.getDischargeCoefficient(), source.getBackPressureBara() * 1.0e5)
        .setTimeStep(timeStepSeconds).setMaxTime(maxTimeSeconds).setStopPressure(source.getStopPressureBara() * 1.0e5)
        .setFireHeatInput(source.getEffectiveFireHeatInputW());
    if (source.hasWallModel()) {
      simulator.setWall(source.getWallMassKg(), source.getWallAreaM2(), source.getWallSpecificHeatJPerKgK(),
          source.getWallHeatTransferCoeffWPerM2K());
    }
    return simulator;
  }

  /**
   * Builds PSV sizing result for one source.
   *
   * @param source blowdown source
   * @param result depressurization result
   * @return PSV sizing map
   */
  private Map<String, Object> psvSizing(BlowdownSource source, DepressurizationResult result) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("sourceId", source.getSourceId());
    map.put("bdvEquivalentDiameterM", Double.valueOf(source.getOrificeDiameterM()));
    map.put("bdvEquivalentAreaM2",
        Double.valueOf(Math.PI * source.getOrificeDiameterM() * source.getOrificeDiameterM() / 4.0));
    map.put("bdvDischargeCoefficient", Double.valueOf(source.getDischargeCoefficient()));
    if (!source.hasPsvSizingBasis()) {
      map.put("status", "not_sized_missing_set_pressure");
      return map;
    }
    double peakMassFlow = max(result.massFlowKgPerS);
    if (peakMassFlow <= 0.0) {
      map.put("status", "not_sized_zero_relief_rate");
      return map;
    }
    SystemInterface fluid = initializedClone(source.getFluid());
    double gamma = gamma(fluid);
    double z = fluid.getZ();
    if (!Double.isFinite(z) || z <= 0.0) {
      z = 1.0;
    }
    PSVSizingResult sizing = ReliefValveSizing.calculateRequiredArea(peakMassFlow,
        source.getPsvSetPressureBara() * 1.0e5, source.getPsvOverpressureFraction(),
        source.getBackPressureBara() * 1.0e5, fluid.getTemperature(), fluid.getMolarMass(), z, gamma,
        source.isBalancedBellowsPsv(), source.isRuptureDiskInstalled());
    map.put("status", "sized");
    map.put("basisMassFlowKgPerS", Double.valueOf(peakMassFlow));
    map.put("setPressureBara", Double.valueOf(source.getPsvSetPressureBara()));
    map.put("overpressureFraction", Double.valueOf(source.getPsvOverpressureFraction()));
    map.put("requiredAreaM2", Double.valueOf(sizing.getRequiredArea()));
    map.put("requiredAreaIn2", Double.valueOf(sizing.getRequiredAreaIn2()));
    map.put("recommendedOrifice", sizing.getRecommendedOrifice());
    map.put("selectedAreaM2", Double.valueOf(sizing.getSelectedArea()));
    map.put("selectedAreaIn2", Double.valueOf(sizing.getSelectedAreaIn2()));
    map.put("backPressureFraction", Double.valueOf(sizing.getBackPressureFraction()));
    map.put("dischargeCoefficient", Double.valueOf(sizing.getDischargeCoefficient()));
    map.put("backPressureCorrectionFactor", Double.valueOf(sizing.getBackPressureCorrectionFactor()));
    map.put("combinationCorrectionFactor", Double.valueOf(sizing.getCombinationCorrectionFactor()));
    map.put("validationMessage", ReliefValveSizing.validateSizing(sizing, true));
    return map;
  }

  /**
   * Builds flare load map from the combined load result.
   *
   * @param dataSource study data source
   * @param combined combined blowdown result
   * @return flare load map
   */
  private Map<String, Object> flareLoadMap(DynamicBlowdownFlareStudyDataSource dataSource,
      MultiVesselBlowdownResult combined) {
    double peakMassFlow = combined.getPeakTotalMassFlowKgPerS();
    SystemInterface flareFluid = representativeFluid(dataSource);
    Flare flare = representativeFlare(dataSource, flareFluid, peakMassFlow);
    double peakHeatDutyW = flare.getHeatDuty();
    double peakMolarFlow = flareFluid.getMolarMass() > 0.0 ? peakMassFlow / flareFluid.getMolarMass() : 0.0;
    double co2RateKgPerS = flare.getCO2Emission();
    double totalMassKg = integrate(combined.getTimeS(), combined.getTotalMassFlowKgPerS());
    double heatPerKg = peakMassFlow > 1.0e-12 ? peakHeatDutyW / peakMassFlow : 0.0;
    double co2PerKg = peakMassFlow > 1.0e-12 ? co2RateKgPerS / peakMassFlow : 0.0;
    CapacityCheckResult capacity = flare.evaluateCapacity(peakHeatDutyW, peakMassFlow, peakMolarFlow);

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("peakMassFlowKgPerS", Double.valueOf(peakMassFlow));
    map.put("peakTimeS", Double.valueOf(combined.getPeakTimeS()));
    map.put("peakHeatDutyW", Double.valueOf(peakHeatDutyW));
    map.put("peakHeatDutyMW", Double.valueOf(peakHeatDutyW * 1.0e-6));
    map.put("peakCO2EmissionKgPerS", Double.valueOf(co2RateKgPerS));
    map.put("totalMassToFlareKg", Double.valueOf(totalMassKg));
    map.put("cumulativeHeatReleasedGJ", Double.valueOf(totalMassKg * heatPerKg * 1.0e-9));
    map.put("cumulativeCO2EmissionKg", Double.valueOf(totalMassKg * co2PerKg));
    map.put("radiationHeatFluxAt30mWPerM2", Double.valueOf(flare.estimateRadiationHeatFlux(peakHeatDutyW, 30.0)));
    map.put("radiationDistanceForThresholdM",
        Double.valueOf(flare.radiationDistanceForFlux(peakHeatDutyW, radiationThresholdWPerM2)));
    map.put("radiationThresholdWPerM2", Double.valueOf(radiationThresholdWPerM2));
    map.put("capacity", capacityMap(capacity));
    return map;
  }

  /**
   * Creates a representative flare at peak load.
   *
   * @param dataSource data source
   * @param flareFluid flare fluid
   * @param peakMassFlow peak mass flow in kg/s
   * @return configured and run flare
   */
  private Flare representativeFlare(DynamicBlowdownFlareStudyDataSource dataSource, SystemInterface flareFluid,
      double peakMassFlow) {
    Stream stream = new Stream("peak flare load", flareFluid);
    stream.setPressure(dataSource.getHeaderPressureBara(), "bara");
    stream.setTemperature(dataSource.getHeaderTemperatureK(), "K");
    stream.setFlowRate(Math.max(peakMassFlow, 1.0e-9), "kg/sec");
    stream.run();
    Flare flare = new Flare("Emergency Flare", stream);
    flare.setTipDiameter(dataSource.getFlareTipDiameterM());
    flare.setFlameHeight(dataSource.getFlareFlameHeightM());
    flare.setRadiantFraction(dataSource.getFlareRadiantFraction());
    if (Double.isFinite(dataSource.getFlareDesignHeatDutyW()) && dataSource.getFlareDesignHeatDutyW() > 0.0) {
      flare.setDesignHeatDutyCapacity(dataSource.getFlareDesignHeatDutyW(), "W");
    }
    if (Double.isFinite(dataSource.getFlareDesignMassFlowKgPerS()) && dataSource.getFlareDesignMassFlowKgPerS() > 0.0) {
      flare.setDesignMassFlowCapacity(dataSource.getFlareDesignMassFlowKgPerS(), "kg/sec");
    }
    if (Double.isFinite(dataSource.getFlareDesignMolarFlowMolePerS())
        && dataSource.getFlareDesignMolarFlowMolePerS() > 0.0) {
      flare.setDesignMolarFlowCapacity(dataSource.getFlareDesignMolarFlowMolePerS(), "mole/sec");
    }
    flare.run();
    return flare;
  }

  /**
   * Builds a compact flare-load handoff.
   *
   * @param dataSource data source
   * @param result result map
   * @return flare-load handoff
   */
  private Map<String, Object> buildFlareLoadHandoff(DynamicBlowdownFlareStudyDataSource dataSource,
      Map<String, Object> result) {
    Map<String, Object> handoff = new LinkedHashMap<String, Object>();
    handoff.put("schemaVersion", "dynamic_blowdown_flare_load_handoff.v1");
    handoff.put("studyId", dataSource.getStudyId());
    handoff.put("basis", "NeqSim dynamic depressurization sources aggregated to common flare header.");
    handoff.put("combinedLoad", result == null ? null : result.get("combinedLoad"));
    handoff.put("psvSizing", result == null ? null : result.get("psvSizing"));
    handoff.put("flareLoad", result == null ? null : result.get("flareLoad"));
    handoff.put("humanReviewRequired", Boolean.TRUE);
    return handoff;
  }

  /**
   * Creates standards readiness from data and result.
   *
   * @param dataSource data source
   * @param result result map or null
   * @return standards readiness
   */
  private SafetyStudyReadiness standardsReadiness(DynamicBlowdownFlareStudyDataSource dataSource,
      Map<String, Object> result) {
    SafetyStudyReadiness.Builder readiness = SafetyStudyReadiness.builder();
    if (!dataSource.isStandardsReviewed()) {
      readiness.addWarning("standards", "Standards basis is not marked reviewed.",
          "Review API 520/521, ISO 23251, NORSOK S-001/P-002, piping specifications, and project flare design criteria.");
    }
    if (result == null) {
      readiness.addBlocker("calculation", "Dynamic calculation was not run.",
          "Resolve calculation-readiness blockers before standards validation.");
      return readiness.build();
    }
    Map<String, Object> combined = mapValue(result.get("combinedLoad"));
    Object headerMachAcceptable = combined.get("headerMachAcceptable");
    if (Boolean.FALSE.equals(headerMachAcceptable)) {
      readiness.addWarning("flare_header", "Calculated flare-header Mach exceeds the configured limit.",
          "Increase header size, stagger blowdown, reduce BDV area, or verify detailed flare hydraulics.");
    }
    if (combined.get("headerMach") == null) {
      readiness.addWarning("flare_header", "Header Mach was not calculated because header diameter is missing.",
          "Provide flare-header internal diameter and gas basis for API 521/NORSOK P-002 disposal-system checks.");
    }
    Map<String, Object> flareLoad = mapValue(result.get("flareLoad"));
    Map<String, Object> capacity = mapValue(flareLoad.get("capacity"));
    if (Boolean.TRUE.equals(capacity.get("overloaded"))) {
      readiness.addWarning("flare_capacity", "Configured flare design capacity is exceeded by the peak load.",
          "Check flare-tip capacity, radiation limits, and simultaneous-blowdown assumptions.");
    }
    Map<String, Object> psvSizing = mapValue(result.get("psvSizing"));
    if (!anyPsvSized(psvSizing)) {
      readiness.addWarning("psv", "No PSV orifice was sized because no source had a PSV set-pressure basis.",
          "Provide PSV set pressure and correction basis for API 520/521 relief-valve sizing output.");
    }
    return readiness.build();
  }

  /**
   * Converts source results to maps.
   *
   * @param sourceResults source result map
   * @return JSON-friendly map
   */
  private Map<String, Object> sourceResultMaps(Map<String, DepressurizationResult> sourceResults) {
    Map<String, Object> maps = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, DepressurizationResult> entry : sourceResults.entrySet()) {
      maps.put(entry.getKey(), sourceResultMap(entry.getValue()));
    }
    return maps;
  }

  /**
   * Converts one depressurization result to a map.
   *
   * @param result depressurization result
   * @return result map
   */
  private Map<String, Object> sourceResultMap(DepressurizationResult result) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("initialPressureBara", Double.valueOf(result.initialPressureBara));
    map.put("timeToHalfPressureS", finiteOrNull(result.timeToHalfPressure));
    map.put("timeTo7BargS", finiteOrNull(result.timeTo7BargS));
    map.put("minFluidTemperatureK", finiteOrNull(result.minFluidTemperatureK));
    map.put("minWallTemperatureK", finiteOrNull(result.minWallTemperatureK));
    map.put("peakMassFlowKgPerS", Double.valueOf(max(result.massFlowKgPerS)));
    map.put("totalMassDischargedKg", Double.valueOf(totalDischarged(result)));
    map.put("halfPressureCriterionMet", Boolean.valueOf(result.halfPressureCriterionMet));
    map.put("sevenBargCriterionMet", Boolean.valueOf(result.sevenBargCriterionMet));
    Map<String, Object> profile = new LinkedHashMap<String, Object>();
    profile.put("timeS", result.time);
    profile.put("pressureBara", result.pressureBara);
    profile.put("temperatureK", result.temperatureK);
    profile.put("massKg", result.massKg);
    profile.put("wallTemperatureK", result.wallTempK);
    profile.put("massFlowKgPerS", result.massFlowKgPerS);
    map.put("profile", profile);
    return map;
  }

  /**
   * Converts combined load result to a map.
   *
   * @param result combined blowdown result
   * @return result map
   */
  private Map<String, Object> combinedLoadMap(MultiVesselBlowdownResult result) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("peakTotalMassFlowKgPerS", Double.valueOf(result.getPeakTotalMassFlowKgPerS()));
    map.put("peakTimeS", Double.valueOf(result.getPeakTimeS()));
    map.put("peakContributionKgPerS", result.getPeakContributionKgPerS());
    map.put("headerVelocityMPerS", finiteOrNull(result.getHeaderVelocityMPerS()));
    map.put("headerMach", finiteOrNull(result.getHeaderMach()));
    map.put("maxAllowableMach", Double.valueOf(result.getMaxAllowableMach()));
    map.put("headerMachAcceptable", Boolean.valueOf(result.isHeaderMachAcceptable()));
    map.put("timeS", result.getTimeS());
    map.put("totalMassFlowKgPerS", result.getTotalMassFlowKgPerS());
    map.put("sourceMassFlowKgPerS", result.getSourceMassFlowKgPerS());
    return map;
  }

  /**
   * Converts capacity result to a map.
   *
   * @param capacity capacity result
   * @return capacity map
   */
  private Map<String, Object> capacityMap(CapacityCheckResult capacity) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("heatDutyW", Double.valueOf(capacity.getHeatDutyW()));
    map.put("designHeatDutyW", finiteOrNull(capacity.getDesignHeatDutyW()));
    map.put("heatUtilization", finiteOrNull(capacity.getHeatUtilization()));
    map.put("massRateKgPerS", Double.valueOf(capacity.getMassRateKgS()));
    map.put("designMassRateKgPerS", finiteOrNull(capacity.getDesignMassRateKgS()));
    map.put("massUtilization", finiteOrNull(capacity.getMassUtilization()));
    map.put("molarRateMolePerS", Double.valueOf(capacity.getMolarRateMoleS()));
    map.put("designMolarRateMolePerS", finiteOrNull(capacity.getDesignMolarRateMoleS()));
    map.put("molarUtilization", finiteOrNull(capacity.getMolarUtilization()));
    map.put("overloaded", Boolean.valueOf(capacity.isOverloaded()));
    return map;
  }

  /**
   * Gets a representative fluid for flare-load conversion.
   *
   * @param dataSource data source
   * @return initialized fluid clone
   */
  private SystemInterface representativeFluid(DynamicBlowdownFlareStudyDataSource dataSource) {
    return initializedClone(dataSource.getSources().get(0).getFluid());
  }

  /**
   * Creates and initializes a fluid clone.
   *
   * @param fluid source fluid
   * @return initialized clone
   */
  private SystemInterface initializedClone(SystemInterface fluid) {
    SystemInterface clone = fluid.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(clone);
    ops.TPflash();
    clone.initProperties();
    return clone;
  }

  /**
   * Calculates gas gamma from a fluid.
   *
   * @param fluid initialized fluid
   * @return gamma value
   */
  private double gamma(SystemInterface fluid) {
    double cp = fluid.getCp("J/molK");
    double cv = fluid.getCv("J/molK");
    double gamma = cv > 0.0 ? cp / cv : 1.3;
    if (!Double.isFinite(gamma) || gamma <= 1.0) {
      return 1.3;
    }
    return gamma;
  }

  /**
   * Integrates a time-series by trapezoidal rule.
   *
   * @param timeS time values in s
   * @param values values to integrate
   * @return integral in value-seconds
   */
  private double integrate(List<Double> timeS, List<Double> values) {
    double sum = 0.0;
    for (int i = 1; i < timeS.size() && i < values.size(); i++) {
      double dt = timeS.get(i) - timeS.get(i - 1);
      if (dt > 0.0) {
        sum += 0.5 * (values.get(i) + values.get(i - 1)) * dt;
      }
    }
    return sum;
  }

  /**
   * Finds max value in a list.
   *
   * @param values values to scan
   * @return maximum finite value, or 0 if none
   */
  private double max(List<Double> values) {
    double max = 0.0;
    for (Double value : values) {
      if (value != null && Double.isFinite(value.doubleValue())) {
        max = Math.max(max, value.doubleValue());
      }
    }
    return max;
  }

  /**
   * Computes total discharged mass from a result.
   *
   * @param result result to inspect
   * @return discharged mass in kg
   */
  private double totalDischarged(DepressurizationResult result) {
    if (result.massKg.isEmpty()) {
      return 0.0;
    }
    return Math.max(0.0, result.massKg.get(0) - result.massKg.get(result.massKg.size() - 1));
  }

  /**
   * Converts finite values to boxed values and non-finite values to null.
   *
   * @param value numeric value
   * @return boxed value or null
   */
  private Object finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }

  /**
   * Casts an object to a map when possible.
   *
   * @param value value to cast
   * @return map or empty map
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> mapValue(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
  }

  /**
   * Checks whether any PSV sizing result completed.
   *
   * @param psvSizing PSV sizing map
   * @return true if at least one source was sized
   */
  private boolean anyPsvSized(Map<String, Object> psvSizing) {
    for (Object value : psvSizing.values()) {
      Map<String, Object> map = mapValue(value);
      if ("sized".equals(map.get("status"))) {
        return true;
      }
    }
    return false;
  }

  /** Builder for {@link DynamicBlowdownFlareStudyRunner}. */
  public static final class Builder {
    private double timeStepSeconds = DEFAULT_TIME_STEP_SECONDS;
    private double maxTimeSeconds = DEFAULT_MAX_TIME_SECONDS;
    private double radiationThresholdWPerM2 = DEFAULT_RADIATION_THRESHOLD_W_PER_M2;

    /** Creates a builder. */
    private Builder() {
    }

    /**
     * Sets integration time step.
     *
     * @param timeStepSeconds time step in seconds
     * @return this builder
     */
    public Builder timeStepSeconds(double timeStepSeconds) {
      this.timeStepSeconds = timeStepSeconds;
      return this;
    }

    /**
     * Sets maximum simulation time.
     *
     * @param maxTimeSeconds maximum time in seconds
     * @return this builder
     */
    public Builder maxTimeSeconds(double maxTimeSeconds) {
      this.maxTimeSeconds = maxTimeSeconds;
      return this;
    }

    /**
     * Sets radiation threshold used for distance reporting.
     *
     * @param radiationThresholdWPerM2 radiation threshold in W/m2
     * @return this builder
     */
    public Builder radiationThresholdWPerM2(double radiationThresholdWPerM2) {
      this.radiationThresholdWPerM2 = radiationThresholdWPerM2;
      return this;
    }

    /**
     * Builds the runner.
     *
     * @return dynamic blowdown/flare runner
     */
    public DynamicBlowdownFlareStudyRunner build() {
      return new DynamicBlowdownFlareStudyRunner(this);
    }

    /**
     * Validates builder state.
     *
     * @throws IllegalArgumentException if settings are invalid
     */
    private void validate() {
      validatePositive(timeStepSeconds, "timeStepSeconds");
      validatePositive(maxTimeSeconds, "maxTimeSeconds");
      validatePositive(radiationThresholdWPerM2, "radiationThresholdWPerM2");
      if (maxTimeSeconds < timeStepSeconds) {
        throw new IllegalArgumentException("maxTimeSeconds must be at least timeStepSeconds");
      }
    }

    /**
     * Validates a positive finite value.
     *
     * @param value value to validate
     * @param name parameter name
     * @throws IllegalArgumentException if invalid
     */
    private static void validatePositive(double value, String name) {
      if (value <= 0.0 || !Double.isFinite(value)) {
        throw new IllegalArgumentException(name + " must be positive and finite");
      }
    }
  }
}
