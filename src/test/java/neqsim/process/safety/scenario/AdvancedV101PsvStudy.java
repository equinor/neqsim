package neqsim.process.safety.scenario;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.dynamics.DynamicCapability;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.TransientMeasurementDevice;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;

/**
 * Opt-in native PSV extension of the actual two-inventory advanced study, preserving the original study unchanged.
 *
 * <p>
 * The PSV branches from the protected gas outlet through an actual native splitter. A runtime-only algebraic helper
 * refreshes branch intensive state; valves solve first, the tee sums their actual demands, then V101 consumes that
 * single withdrawal. Synthetic Cv and pressure/lift laws are not vendor capacity, API/ISO certification, SIL or
 * construction approval.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AdvancedV101PsvStudy {
  private static final String[] VESSELS = {"10-V-100", "20-V-101"};
  private static final String[] COMPONENTS = {"methane", "ethane", "propane", "n-butane", "i-butane", "n-pentane",
      "n-hexane", "CO2", "nitrogen"};

  /** Prevents construction of the static fixture API. */
  private AdvancedV101PsvStudy() {
  }

  /** @return explicit design inputs, independently mutable */
  public static Map<String, Double> defaultSettings() {
    Map<String, Double> settings = AdvancedV101SafetyStudy.defaultSettings();
    settings.put("psvSetPressureBara", 42.2);
    settings.put("psvOverpressureFraction", 0.10);
    settings.put("psvBlowdownFraction", 0.03);
    settings.put("psvRatedCv", 80.0);
    settings.put("psvKd", 0.975);
    settings.put("psvKbMax", 1.0);
    settings.put("psvBackpressureSensitivity", 0.15);
    settings.put("psvTauOpenSeconds", 0.15);
    settings.put("psvTauCloseSeconds", 0.40);
    settings.put("psvMinimumOpenDwellSeconds", 0.50);
    settings.put("psvMinimumClosedDwellSeconds", 0.20);
    settings.put("psvMaximumLiftRatePerSecond", 3.0);
    settings.put("psvMinimumStableCommandFraction", 0.03);
    settings.put("psvSeatToleranceFraction", 1.0e-8);
    settings.put("psvBackpressureBara", 1.2);
    settings.put("gasPathChokingEnabled", 1.0);
    return settings;
  }

  /**
   * Adds an actual tee, passive relief valve and backpressure disposal boundary to the original physical source.
   *
   * @param settings explicit inputs
   *
   * @return native physical source without numerical helper units
   */
  public static ProcessSystem buildSource(Map<String, Double> settings) {
    ProcessSystem process = AdvancedV101SafetyStudy.buildSource(settings);
    for (String tag : new String[] {"20-ESDV-001", "20-HIPPS-XV-001", "20-PCV-001"}) {
      valve(process, tag).setAllowChoked(settings.get("gasPathChokingEnabled") == 1.0);
      // Explicit opt-in gas-valve calibration uses the same original design margin/opening,
      // now under the corrected choking policy. PSV Cv remains a frozen studied alternative.
      valve(process, tag).autoSize(1.0, 50.0);
      valve(process, tag).setPercentValveOpening(50.0);
    }
    Splitter tee = new Splitter("20-TEE-PSV-001", vessel(process, "20-V-101").getGasOutStream(), 2);
    tee.setSplitFactors(new double[] {0.999, 0.001});
    tee.run();
    tee.getSplitStream(0).setName("20-PCV-FEED");
    tee.getSplitStream(1).setName("20-PSV-FEED");
    ThrottlingValve pcv = valve(process, "20-PCV-001");
    pcv.setInletStream(tee.getSplitStream(0));
    pcv.setOutletPressure(35.0, "bara");
    SafetyReliefValve psv = new SafetyReliefValve("20-PSV-001", tee.getSplitStream(1));
    applySettings(psv, settings);
    psv.setOutletPressure(settings.get("psvBackpressureBara"), "bara");
    psv.getOutletStream().setName("20-PSV-DISPOSAL");
    // Native hydraulic initialization records the PSV's own closed zero-demand state.
    // The final steady topology assigns all actual throughput to the PCV, with no relief leakage.
    psv.run();
    tee.setSplitFactors(new double[] {1.0, 0.0});
    process.add(5, tee);
    process.add(psv);
    process.run();
    return process;
  }

  /**
   * Sets the declared healthy model's native mechanics; no certificate or vendor curve is inferred.
   *
   * @param psv actual relief valve
   *
   * @param settings scalar healthy settings
   */
  public static void applySettings(SafetyReliefValve psv, Map<String, Double> settings) {
    psv.configureConventionalSnap(settings.get("psvSetPressureBara"), settings.get("psvOverpressureFraction"),
        settings.get("psvBlowdownFraction"), settings.get("psvRatedCv"));
    psv.setKd(settings.get("psvKd"));
    psv.setKbMax(settings.get("psvKbMax"));
    psv.setBackpressureSensitivity(settings.get("psvBackpressureSensitivity"));
    psv.setTauOpenSec(settings.get("psvTauOpenSeconds"));
    psv.setTauCloseSec(settings.get("psvTauCloseSeconds"));
    psv.setMinOpenTimeSec(settings.get("psvMinimumOpenDwellSeconds"));
    psv.setMinCloseTimeSec(settings.get("psvMinimumClosedDwellSeconds"));
    psv.setMaxLiftRatePerSec(settings.get("psvMaximumLiftRatePerSecond"));
    psv.setMinStableOpenFrac(settings.get("psvMinimumStableCommandFraction"));
    psv.setSeatToleranceFraction(settings.get("psvSeatToleranceFraction"));
    psv.setPhysicalStepIdempotencyEnabled(true);
    psv.setLiftMotionAvailable(true);
    psv.setAllowChoked(settings.get("gasPathChokingEnabled") == 1.0);
    psv.getMechanicalDesign().setValveCharacterization("linear");
  }

  /**
   * Prepares the exact copied inventories and an explicit, conservative pressure-driven branch order.
   *
   * @param process actual isolated process
   *
   * @param settings healthy model inputs
   */
  public static void configureAfterSteady(ProcessSystem process, Map<String, Double> settings) {
    Splitter tee = (Splitter) process.getUnit("20-TEE-PSV-001");
    SafetyReliefValve psv = psv(process);
    AdvancedV101SafetyStudy.configureAfterSteady(process, settings);
    applySettings(psv, settings);
    psv.setCalculateSteadyState(false);
    psv.initializeLiftState(0.0);
    tee.setCalculateSteadyState(true);
    tee.setTransientSplitMode(Splitter.TransientSplitMode.DOWNSTREAM_DEMAND_ALWAYS);
    List<ProcessEquipmentInterface> order = new ArrayList<ProcessEquipmentInterface>(
        process.getUnitOperations().subList(0, 6));
    order.add(new BranchStateRefresh(tee));
    order.add(process.getUnit("20-PCV-001"));
    order.add(psv);
    order.add(tee);
    order.add(process.getUnit("20-V-101"));
    order.add(process.getUnit("10-LCV-001"));
    order.add(process.getUnit("20-LCV-001"));
    process.getUnitOperations().clear();
    for (ProcessEquipmentInterface unit : order) {
      process.add(unit);
    }
  }

  /** @return all explicitly declared nominal, demand, failure and cycle profiles */
  public static List<String> modes() {
    return Arrays.asList("PSV_NORMAL", "SIS_STANDBY", "PSV_RELIEF", "HIPPS_FAILED_PSV", "PSV_FAILED_CLOSED",
        "PSV_STUCK_OPEN", "PSV_UNDERSIZED", "PSV_HIGH_BACKPRESSURE", "PSV_SLOW", "PSV_CYCLE");
  }

  /**
   * Exposes intentional fault overrides separately from the healthy canonical PSV.
   *
   * @param mode declared case mode
   *
   * @return independently mutable configuration delta
   */
  public static Map<String, Object> configurationOverrides(String mode) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    if (mode.equals("PSV_FAILED_CLOSED")) {
      values.put("liftMotionAvailable", Boolean.FALSE);
    }
    if (mode.equals("PSV_STUCK_OPEN")) {
      values.put("initialLiftFraction", 1.0);
      values.put("liftMotionAvailable", Boolean.FALSE);
    }
    if (mode.equals("PSV_UNDERSIZED")) {
      values.put("ratedCv", 4.0);
    }
    if (mode.equals("PSV_SLOW")) {
      values.put("tauOpenSeconds", 4.0);
      values.put("maximumLiftRatePerSecond", 0.2);
    }
    return values;
  }

  /**
   * @param mode declared case mode
   * @return explicit boundary override, or null for the normal boundary
   */
  public static Double backpressureOverride(String mode) {
    return mode.equals("PSV_HIGH_BACKPRESSURE") ? 44.0 : null;
  }

  /** Configures canonical readback/evidence after intentional deltas, before any physical scan. */
  public interface ConfigurationObserver extends Serializable {
    /**
     * @param process exact copy
     * @param psv actual relief valve
     * @param settings healthy settings
     * @param mode explicit mode
     */
    void inspect(ProcessSystem process, SafetyReliefValve psv, Map<String, Double> settings, String mode);
  }

  /**
   * @param source canonical physical source
   * @param settings healthy inputs
   * @param mode case mode
   * @param dt seconds
   * @return executable case
   */
  public static CaseBundle createCase(ProcessSystem source, Map<String, Double> settings, String mode, double dt) {
    return createCase(source, settings, mode, dt, Collections.<String>emptyList());
  }

  /**
   * @param source canonical source
   * @param settings healthy inputs
   * @param mode case mode
   * @param dt seconds
   * @param refs controlled receipts
   * @return case
   */
  public static CaseBundle createCase(ProcessSystem source, Map<String, Double> settings, String mode, double dt,
      List<String> refs) {
    if (!modes().contains(mode)) {
      throw new IllegalArgumentException("Unknown PSV mode " + mode);
    }
    return new CaseBundle(source, settings, mode, dt, refs);
  }

  /**
   * One strict native case with actual passive response, typed criteria and complete physical event-window trace.
   * 
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class CaseBundle {
    private final ProcessSystem source;
    private final Map<String, Double> settings;
    private final String mode;
    private final double dt;
    private final double duration;
    private final String id;
    private final DynamicSafetyScenario scenario;
    private final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
    private ProcessSystem configured;
    private List<ClosedLoopSafetyFunction> functions;
    private Map<String, Object> initialConfiguration;
    private ConfigurationObserver observer;
    private DynamicSafetyScenarioResult nativeResult;
    private double origin;
    private double lastTime = Double.NaN;
    private double relievedMass;
    private boolean recovery;
    private boolean reblocked;

    /**
     * @param source physical source
     * @param settings inputs
     * @param mode mode
     * @param dt timestep
     * @param refs controlled evidence
     */
    private CaseBundle(ProcessSystem source, Map<String, Double> settings, String mode, double dt, List<String> refs) {
      this.source = source;
      this.settings = new LinkedHashMap<String, Double>(settings);
      this.mode = mode;
      this.dt = dt;
      duration = mode.equals("PSV_CYCLE") ? 30.0 : 20.0;
      id = "ADV-PSV-" + mode + "-DT-" + Double.toString(dt);
      DynamicSafetyScenario.Builder builder = DynamicSafetyScenario.builder(id, "Synthetic " + mode)
          .durationSeconds(duration).timeStepSeconds(dt).triggerTimeSeconds(1.0).eventExactTiming(true)
          .addLogic("ADV-PSV-NATIVE-BRANCH-CHOKED-v2", process -> initialize(process))
          .initiatingEvent("ADV-PSV-" + mode + "-EVENT-v1", process -> initiate(process));
      double window = duration - 1.0;
      builder.addCriterion(DynamicScenarioCriterion
          .builder("V101-PRESSURE-ENVELOPE", "Full sampled vessel envelope", "bara", "ADV-PSV-V101-PRESSURE-v1",
              process -> sample(process))
          .acceptanceRange(1.0, settings.get("pressureLimitV101Bara"))
          .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).deadlineSeconds(window).build());
      builder.addCriterion(DynamicScenarioCriterion
          .builder("V100-PRESSURE-ENVELOPE", "Upstream full sampled envelope", "bara", "ADV-PSV-V100-PRESSURE-v1",
              process -> vessel(process, "10-V-100").getPressure())
          .acceptanceRange(1.0, settings.get("pressureLimitV100Bara"))
          .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).deadlineSeconds(window).build());
      for (String tag : VESSELS) {
        final String target = tag;
        builder.addCriterion(DynamicScenarioCriterion
            .builder(tag + "-NUMERICAL-ENERGY", "Separate frozen numerical gate", "fraction", "ADV-PSV-ENERGY-v1",
                process -> vessel(process, target).getLastTransientEnergyBalanceReport()
                    .getMaximumRelativeCumulativeResidual())
            .acceptanceRange(0.0, settings.get("relativeEnergyLimit"))
            .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).deadlineSeconds(window)
            .required(false).build());
      }
      if (noReliefExpected(mode) || mode.equals("PSV_STUCK_OPEN")) {
        builder.addCriterion(DynamicScenarioCriterion
            .builder("NO-UNWANTED-RELIEF", "No unintended actual relief", "kg/s", "ADV-PSV-ACTUAL-FLOW-v1",
                process -> psv(process).getOutletStream().getFlowRate("kg/sec"))
            .acceptanceRange(0.0, 1.0e-8).evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS)
            .deadlineSeconds(window).build());
      } else {
        builder.addCriterion(DynamicScenarioCriterion
            .builder("RELIEF-OBSERVED", "Actual pop and positive native discharge", "binary", "ADV-PSV-POP-AND-FLOW-v1",
                process -> psv(process).getOpeningCycleCount() > 0L && psv(process).getOpenFraction() > 0.0
                    && psv(process).getOutletStream().getFlowRate("kg/sec") > 1.0e-4 ? 1.0 : 0.0)
            .acceptanceRange(1.0, 1.0).deadlineSeconds(8.0).build());
      }
      if (mode.equals("SIS_STANDBY")) {
        builder.addCriterion(DynamicScenarioCriterion
            .builder("HIPPS-PHYSICAL-CLOSURE", "Upstream protection contains demand", "%", "ADV-PSV-HIPPS-ACTUAL-v1",
                process -> valve(process, "20-HIPPS-XV-001").getPercentValveOpening())
            .acceptanceRange(0.0, 0.0).deadlineSeconds(3.0).build());
      }
      if (mode.equals("PSV_CYCLE")) {
        builder.addCriterion(DynamicScenarioCriterion
            .builder("POP-RESEAT-REPOP", "Observed exact-seat cycle and reopening", "binary", "ADV-PSV-ACTUAL-CYCLE-v1",
                process -> psv(process).getOpeningCycleCount() >= 2L && psv(process).getReseatCycleCount() >= 1L ? 1.0
                    : 0.0)
            .acceptanceRange(1.0, 1.0).deadlineSeconds(window).build());
      }
      for (ProcessEquipmentInterface unit : source.getUnitOperations()) {
        builder.addEquipmentTag(unit.getName());
      }
      for (MeasurementDeviceInterface tx : source.getMeasurementDevices()) {
        builder.addEquipmentTag(tx.getName());
      }
      for (String ref : refs) {
        builder.addEvidenceReference(ref);
      }
      scenario = builder.build();
    }

    /** @return actual native scenario definition */
    public DynamicSafetyScenario getScenario() {
      return scenario;
    }

    /** @return actual factory-configured process after execution starts */
    public ProcessSystem getConfiguredProcess() {
      return configured;
    }

    /** @return actual native safety functions, without membership edits */
    public List<ClosedLoopSafetyFunction> getFunctions() {
      return Collections.unmodifiableList(functions);
    }

    /** @return actual event-window physical rows */
    public List<Map<String, Object>> getRows() {
      return Collections.unmodifiableList(rows);
    }

    /** @return explicit profile name */
    public String getMode() {
      return mode;
    }

    /** @return independently mutable healthy scalar model inputs */
    public Map<String, Double> getSettings() {
      return new LinkedHashMap<String, Double>(settings);
    }

    /** @param value governed preflight callback */
    public void setConfigurationObserver(ConfigurationObserver value) {
      observer = value;
    }

    /** @return actual strict typed result; intentional failures are retained */
    public DynamicSafetyScenarioResult runStrict() {
      nativeResult = DynamicSafetyScenarioRunner.runStrict(source, scenario);
      return nativeResult;
    }

    /**
     * @param process actual copied source
     * @return native coordinating logic
     */
    private ProcessLogic initialize(ProcessSystem process) {
      configured = process;
      rows.clear();
      events.clear();
      relievedMass = 0.0;
      lastTime = Double.NaN;
      recovery = false;
      reblocked = false;
      configureAfterSteady(process, settings);
      origin = process.getTime();
      SafetyReliefValve psv = psv(process);
      if (mode.equals("PSV_FAILED_CLOSED")) {
        psv.setLiftMotionAvailable(false);
      }
      if (mode.equals("PSV_STUCK_OPEN")) {
        psv.initializeLiftState(1.0);
        psv.setLiftMotionAvailable(false);
      }
      if (mode.equals("PSV_UNDERSIZED")) {
        psv.setRatedCv(4.0);
      }
      if (mode.equals("PSV_SLOW")) {
        psv.setTauOpenSec(4.0);
        psv.setMaxLiftRatePerSec(0.2);
      }
      if (backpressureOverride(mode) != null) {
        psv.setOutletPressure(backpressureOverride(mode), "bara");
      }
      if (mode.equals("HIPPS_FAILED_PSV")) {
        ((ESDValve) process.getUnit("20-HIPPS-XV-001")).setActuatorAvailable(false);
      }
      functions = AdvancedV101SafetyStudy.createFunctions(process, settings, "TRUE_DEMAND");
      if (observer != null) {
        observer.inspect(process, psv, settings, mode);
      }
      initialConfiguration = new LinkedHashMap<String, Object>(psv.getPressureReliefConfiguration());
      return new Coordinator(this);
    }

    /** @param process actual copied source */
    private void initiate(ProcessSystem process) {
      if (!mode.equals("PSV_NORMAL") && !mode.equals("PSV_STUCK_OPEN")) {
        valve(process, "20-PCV-001").getController().setActive(false);
        valve(process, "20-PCV-001").setPercentValveOpening(0.0);
      }
      event("INITIATING_EVENT", mode);
    }

    /**
     * @param action native operator action
     * @param reason declared reason
     */
    private void event(String action, String reason) {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("timeSeconds", configured.getTime() - origin);
      value.put("action", action);
      value.put("reason", reason);
      events.add(value);
    }

    /**
     * @param process actual executing graph
     * @return actual protected pressure, bara
     */
    private double sample(ProcessSystem process) {
      double time = process.getTime() - origin;
      if (Double.isNaN(lastTime) || time != lastTime) {
        SafetyReliefValve psv = psv(process);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("timeSeconds", time);
        row.put("eventRelativeSeconds", time - 1.0);
        row.put("physicalStepSeconds", process.getTimeStep());
        row.put("physicalStepId", process.getCalculationIdentifier().toString());
        row.put("pressureBara", psv.getInletStream().getPressure("bara"));
        row.put("vesselPressureBara", vessel(process, "20-V-101").getPressure());
        row.put("backpressureBara", psv.getOutletStream().getPressure("bara"));
        row.put("liftFraction", psv.getOpenFraction());
        row.put("commandedLiftFraction", psv.getLastCommandedLiftFraction());
        row.put("pressureDemandActive", psv.isPressureDemandActive());
        row.put("relievingMassFlowKgPerS", psv.getOutletStream().getFlowRate("kg/sec"));
        if (!Double.isNaN(lastTime)) {
          relievedMass += psv.getOutletStream().getFlowRate("kg/sec") * process.getTimeStep();
        }
        row.put("relievedMassEventWindowKg", relievedMass);
        row.put("openingCycleCount", psv.getOpeningCycleCount());
        row.put("reseatCycleCount", psv.getReseatCycleCount());
        row.put("timeSinceOpenSeconds", psv.getTimeSinceOpenSec());
        row.put("timeSinceClosedSeconds", psv.getTimeSinceCloseSec());
        row.put("responseElapsedSeconds", psv.getResponseElapsedSeconds());
        row.put("effectiveCapacityCv", psv.getCurrentEffectiveCapacityCv());
        row.put("backpressureFactor", psv.getCurrentBackpressureFactor());
        for (String tag : VESSELS) {
          Separator vessel = vessel(process, tag);
          row.put(tag + ".pressureBara", vessel.getPressure());
          row.put(tag + ".temperatureK", vessel.getThermoSystem().getTemperature());
          row.put(tag + ".levelFraction", vessel.getLiquidLevel());
          row.put(tag + ".massKg", vessel.getThermoSystem().getMass("kg"));
          row.put(tag + ".internalEnergyJ", vessel.getThermoSystem().getInternalEnergy());
          for (String component : COMPONENTS) {
            row.put(tag + "." + component + "Mol", vessel.getThermoSystem().getComponent(component).getNumberOfmoles());
          }
        }
        for (String tag : new String[] {"20-PCV-001", "20-PSV-001", "20-HIPPS-XV-001", "20-ESDV-001", "10-LCV-001",
            "20-LCV-001"}) {
          ThrottlingValve valve = valve(process, tag);
          row.put(tag + ".openingPercent", valve.getPercentValveOpening());
          row.put(tag + ".inletMassFlowKgPerS", valve.getInletStream().getFlowRate("kg/sec"));
          row.put(tag + ".outletMassFlowKgPerS", valve.getOutletStream().getFlowRate("kg/sec"));
        }
        boolean fresh = true;
        for (MeasurementDeviceInterface tx : process.getMeasurementDevices()) {
          if (tx instanceof TransientMeasurementDevice
              && ((TransientMeasurementDevice) tx).isTransientSamplingEnabled()) {
            TransientMeasurementDevice held = (TransientMeasurementDevice) tx;
            fresh &= held.hasTransientSample(process.getCalculationIdentifier());
            row.put(tx.getName() + ".heldValue", held.getTransientSample());
          }
        }
        row.put("allHeldSamplesCurrentStep", fresh);
        rows.add(row);
        lastTime = time;
      }
      return vessel(process, "20-V-101").getPressure();
    }

    /** @return actual immutable-source configuration and raw execution evidence */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "advanced_v101_psv_study.v1");
      result.put("scenarioId", id);
      result.put("mode", mode);
      result.put("settings", new LinkedHashMap<String, Double>(settings));
      result.put("dtSeconds", dt);
      result.put("durationSeconds", duration);
      result.put("observedEventWindowStartSeconds", 1.0);
      result.put("nativeResult", nativeResult == null ? null : nativeResult.toMap());
      result.put("physicalTrace", new ArrayList<Map<String, Object>>(rows));
      result.put("nativeResultOrigin",
          nativeResult == null ? "EXTERNAL_PROJECT_RUN_RESULT_STORED_SEPARATELY" : "BUNDLE_RUN_STRICT");
      result.put("configurationOverrides", configurationOverrides(mode));
      result.put("backpressureOverrideBara", backpressureOverride(mode));
      result.put("operatorEvents", new ArrayList<Map<String, Object>>(events));
      result.put("initialPressureReliefConfiguration", initialConfiguration);
      result.put("energy", energy());
      result.put("engineeringQualification", "HOLD");
      result.put("fitnessForConstruction", false);
      result.put("independentBoundaryAudit", "SEPARATE_ORDERED_PRE_CONSUMPTION_REFERENCE_REQUIRED");
      result.put("transactionScope",
          "Connected branch subgraph only; original SetPoint full-train transactions unsupported");
      result.put("disposalBoundary",
          "Fixed-pressure sink; no flare inventory/header dynamics or certified rated capacity");
      Map<String, Object> choking = new LinkedHashMap<String, Object>();
      if (configured != null) {
        for (String tag : new String[] {"20-ESDV-001", "20-HIPPS-XV-001", "20-PCV-001", "20-PSV-001"}) {
          choking.put(tag, valve(configured, tag).isAllowChoked());
        }
      }
      result.put("actualGasPathChoking", choking);
      result.put("designBasisRevision", "EXPLICIT_NATIVE_GAS_CHOKING_AND_FIXED_SYNTHETIC_CV_ALTERNATIVE_STUDY_V2");
      result.put("relievedMassIntegralConvention",
          "Post-step actual PSV outlet rate integrated over recorded event window only; independent pre-consume full-horizon audit reported separately");
      return result;
    }

    /** @return separately reported per-vessel numerical coverage and frozen energy verdict */
    private Map<String, Object> energy() {
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      if (configured != null) {
        for (String tag : VESSELS) {
          Separator vessel = vessel(configured, tag);
          SeparatorTransientEnergyBalanceReport report = vessel.getLastTransientEnergyBalanceReport();
          Map<String, Object> one = new LinkedHashMap<String, Object>();
          one.put("status", vessel.getTransientEnergyBalanceTrackingStatus().name());
          one.put("verdict", vessel.assessTransientEnergyBalance(settings.get("relativeEnergyLimit")).name());
          if (report != null) {
            one.put("initialEnergyJ", report.getInitialEnergyJ());
            one.put("maxRelativeCumulativeResidual", report.getMaximumRelativeCumulativeResidual());
            one.put("maxAbsoluteCumulativeDebtJ", report.getMaximumAbsoluteCumulativeDebtJ());
            one.put("acceptedStepCount", report.getAcceptedStepCount());
          }
          values.put(tag, one);
        }
      }
      return values;
    }

    /**
     * @param directory destination directory
     * @throws IOException if actual output cannot be written
     */
    public void export(Path directory) throws IOException {
      Files.createDirectories(directory);
      Files.write(directory.resolve(id + ".json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(toMap()).getBytes(StandardCharsets.UTF_8));
      List<String> columns = new ArrayList<String>();
      for (Map<String, Object> row : rows) {
        for (String key : row.keySet()) {
          if (!columns.contains(key)) {
            columns.add(key);
          }
        }
      }
      StringBuilder csv = new StringBuilder();
      csvRow(csv, new ArrayList<Object>(columns));
      for (Map<String, Object> row : rows) {
        List<Object> values = new ArrayList<Object>();
        for (String key : columns) {
          values.add(row.get(key));
        }
        csvRow(csv, values);
      }
      Files.write(directory.resolve(id + ".csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * Coordinates actual native safety functions and explicit pressure-recovery operating actions.
   * 
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Coordinator implements ProcessLogic, DynamicSafetyScenarioEvidenceProvider {
    private static final long serialVersionUID = 1L;
    private final CaseBundle owner;
    private boolean active;

    /** @param owner actual case state */
    private Coordinator(CaseBundle owner) {
      this.owner = owner;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
      return "ADV-PSV-NATIVE-COORDINATOR";
    }

    /** {@inheritDoc} */
    @Override
    public LogicState getState() {
      return active ? LogicState.RUNNING : LogicState.IDLE;
    }

    /** {@inheritDoc} */
    @Override
    public void activate() {
      active = true;
      boolean enabled = owner.mode.equals("PSV_NORMAL") || owner.mode.equals("SIS_STANDBY")
          || owner.mode.equals("HIPPS_FAILED_PSV") || owner.mode.equals("PSV_STUCK_OPEN");
      for (ClosedLoopSafetyFunction function : owner.functions) {
        function.activate();
        if (!enabled) {
          function.deactivate();
        }
      }
      if (!enabled) {
        owner.event("SIS_INHIBITED", "Explicit PSV-only layer test; no independent-layer credit");
      }
    }

    /** {@inheritDoc} */
    @Override
    public void deactivate() {
      active = false;
      for (ClosedLoopSafetyFunction function : owner.functions) {
        function.deactivate();
      }
    }

    /** {@inheritDoc} */
    @Override
    public boolean reset() {
      active = false;
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive() {
      return active;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isComplete() {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public List<ProcessEquipmentInterface> getTargetEquipment() {
      return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
    public String getStatusDescription() {
      return "Bounded actual PSV and native SIS observation";
    }

    /** {@inheritDoc} */
    @Override
    public void execute(double step) {
      for (ClosedLoopSafetyFunction function : owner.functions) {
        if (function.isActive()) {
          function.execute(step);
        }
      }
      double relative = owner.configured.getTime() - owner.origin - 1.0;
      if (owner.mode.equals("PSV_CYCLE") && !owner.recovery && relative >= 5.0 - 1e-9) {
        valve(owner.configured, "20-PCV-001").setPercentValveOpening(100.0);
        owner.recovery = true;
        owner.event("GAS_OUTLET_RECOVERY", "Actual PCV reopened; PSV must physically seat below reseat");
      }
      if (owner.mode.equals("PSV_CYCLE") && !owner.reblocked && relative >= 16.0 - 1e-9) {
        valve(owner.configured, "20-PCV-001").setPercentValveOpening(0.0);
        owner.reblocked = true;
        owner.event("SECOND_BLOCKED_GAS_OUTLET", "Actual second demand; no forced PSV lift");
      }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getDynamicSafetyScenarioEvidence() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "advanced_psv_runtime_evidence.v1");
      Map<String, Object> relief = new LinkedHashMap<String, Object>();
      relief.put("schemaVersion", "native_pressure_relief_evidence.v1");
      relief.put("name", "20-PSV-001");
      relief.put("configuration", owner.initialConfiguration);
      relief.put("trace", new ArrayList<Map<String, Object>>(owner.rows));
      relief.put("scope", owner.mode);
      relief.put("engineeringQualification", "HOLD");
      result.put("pressureReliefEvidence", Collections.singletonList(relief));
      List<Map<String, Object>> functions = new ArrayList<Map<String, Object>>();
      for (ClosedLoopSafetyFunction function : owner.functions) {
        functions.add(function.getDynamicSafetyScenarioEvidence());
      }
      result.put("nativeFunctions", functions);
      result.put("operatorEvents", new ArrayList<Map<String, Object>>(owner.events));
      return result;
    }
  }

  /**
   * Stateless algebraic native transport helper; its branch mutations are owned by the separately captured splitter.
   * 
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class BranchStateRefresh extends ProcessEquipmentBaseClass
      implements TransientStateParticipant<BranchStateRefresh.State> {
    private static final long serialVersionUID = 1L;
    private final Splitter target;
    private final String stateIdentity = UUID.randomUUID().toString();

    /** @param target actual native splitter */
    public BranchStateRefresh(Splitter target) {
      super("V101-PSV-BRANCH-INTENSIVE-COUPLING");
      this.target = target;
    }

    /** {@inheritDoc} */
    @Override
    public DynamicCapability getDynamicCapability() {
      return DynamicCapability.ALGEBRAIC;
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      target.refreshTransientBranchIntensiveState();
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt, UUID id) {
      run(id);
    }

    /** {@inheritDoc} */
    @Override
    public String getTransientStateIdentity() {
      return "helper:branch-refresh:" + stateIdentity;
    }

    /** {@inheritDoc} */
    @Override
    public String getTransientStateCoverageIssue() {
      String issue = getBaseTransientStateCoverageIssue();
      return issue != null ? issue : target.getTransientStateCoverageIssue();
    }

    /** {@inheritDoc} */
    @Override
    public State captureTransientState() {
      if (getTransientStateCoverageIssue() != null) {
        throw new IllegalStateException(getTransientStateCoverageIssue());
      }
      return new State(this);
    }

    /** {@inheritDoc} */
    @Override
    public void restoreTransientState(State state) {
      if (!getTransientStateIdentity().equals(state.identity)
          || !target.getTransientStateIdentity().equals(state.targetIdentity)) {
        throw new IllegalArgumentException("Helper target changed");
      }
      restoreBaseTransientState(state.base);
    }

    /**
     * Captures helper metadata only; no inventory, demands or branch state is waived.
     * 
     * @author Even Solbraa
     * @version 1.0
     */
    public static final class State implements Serializable {
      private static final long serialVersionUID = 1L;
      private final String identity;
      private final String targetIdentity;
      private final ProcessEquipmentTransientState base;

      /** @param source actual helper */
      private State(BranchStateRefresh source) {
        identity = source.getTransientStateIdentity();
        targetIdentity = source.target.getTransientStateIdentity();
        base = source.captureBaseTransientState();
      }
    }
  }

  /**
   * @param mode case mode
   * @return whether this case requires no passive relief
   */
  private static boolean noReliefExpected(String mode) {
    return mode.equals("PSV_NORMAL") || mode.equals("SIS_STANDBY");
  }

  /**
   * @param process actual graph
   * @return actual passive relief valve
   */
  private static SafetyReliefValve psv(ProcessSystem process) {
    return (SafetyReliefValve) process.getUnit("20-PSV-001");
  }

  /**
   * @param process actual graph
   * @param tag native name
   * @return actual vessel
   */
  private static Separator vessel(ProcessSystem process, String tag) {
    return (Separator) process.getUnit(tag);
  }

  /**
   * @param process actual graph
   * @param tag native name
   * @return actual valve
   */
  private static ThrottlingValve valve(ProcessSystem process, String tag) {
    return (ThrottlingValve) process.getUnit(tag);
  }

  /**
   * @param csv output buffer
   * @param values actual row scalars
   */
  private static void csvRow(StringBuilder csv, List<Object> values) {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        csv.append(',');
      }
      Object value = values.get(i);
      if (value != null) {
        csv.append('"').append(value.toString().replace("\"", "\"\"")).append('"');
      }
    }
    csv.append('\n');
  }
}
