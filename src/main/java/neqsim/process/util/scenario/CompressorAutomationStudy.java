package neqsim.process.util.scenario;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChart;
import neqsim.process.equipment.compressor.CompressorState;
import neqsim.process.equipment.compressor.SafeSplineSurgeCurve;
import neqsim.process.equipment.compressor.ShutdownProfile;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.CheckValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.TripCompressorAction;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.measurementdevice.CompressorSurgeMarginMeasurement;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.measurementdevice.SensorFaultType;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.TransientMeasurementDevice;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioRunner;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioEvidenceProvider;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reusable synthetic compressor automation train with actual equipment, sampled instruments and native trip actions.
 *
 * <p>
 * The two finite separators own process inventory. The compressor is a quasi-steady synthetic map element. The cooler
 * uses native effectiveness, cooling-utility actuation and thermal lag; that lag is not a separate metal or coolant
 * energy inventory. Valve demands use native choked gas-Cv hydraulics. The explicitly ordered fixed-step runner solves
 * compressor flow against current discharge inventory pressure before either vessel consumes its boundary. It is not a
 * distributed surge, acoustic, rotor-inertia or certified protection model.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CompressorAutomationStudy {
  /** Prevents instantiation of the factory. */
  private CompressorAutomationStudy() {
  }

  /**
   * Builds and initializes a composable native process from explicit synthetic design data.
   *
   * @param design design assumptions, copied before use
   * @return initialized train exposing the actual native objects
   */
  public static Train create(Design design) {
    return new Train(design);
  }

  /**
   * Native scenario profiles with explicit physical or signal-fault initiating mechanisms.
   *
   * @author NeqSim
   * @version 1.0
   */
  public enum Profile {
    NORMAL, LOAD_REJECTION, SLOW_RECYCLE, STUCK_RECYCLE, FT_BIASED_HIGH, FT_FROZEN, COOLING_LOSS, MANUAL_ESD,
    DRIVER_TRIP_FAILED, ISOLATION_FAILED, ASV_TRIP_FAILED, LEVEL_SIGNAL_HIGH, DISCHARGE_LEVEL_SIGNAL_HIGH,
    PRESSURE_SIGNAL_HIGH
  }

  /**
   * Immutable fixed-step acceptance case definition; fault overrides are separate from healthy design settings.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class CaseDefinition implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Profile profile;
    private final double timeStepSeconds;
    private final double durationSeconds;
    private final double eventSeconds;
    private final List<String> evidenceReferences = new ArrayList<String>();

    /**
     * Creates a 20-second case with its initiating event at two seconds.
     *
     * @param profile exact profile enum name
     * @param timeStepSeconds physical timestep in seconds
     */
    public CaseDefinition(String profile, double timeStepSeconds) {
      this(Profile.valueOf(profile), timeStepSeconds, 20.0, 2.0);
    }

    /**
     * Creates an explicit physical timing contract.
     *
     * @param profile physical or signal-fault profile
     * @param timeStepSeconds physical timestep in seconds
     * @param durationSeconds total simulation duration in seconds
     * @param eventSeconds initiating event time in seconds
     * @throws IllegalArgumentException when timing is invalid
     */
    public CaseDefinition(Profile profile, double timeStepSeconds, double durationSeconds, double eventSeconds) {
      if (profile == null || !Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0
          || !Double.isFinite(durationSeconds) || !Double.isFinite(eventSeconds) || eventSeconds < 0.0
          || durationSeconds <= eventSeconds + 5.0) {
        throw new IllegalArgumentException(
            "A profile, positive dt and at least five seconds after the event are required");
      }
      this.profile = profile;
      this.timeStepSeconds = timeStepSeconds;
      this.durationSeconds = durationSeconds;
      this.eventSeconds = eventSeconds;
    }

    /** @return declared profile */
    public Profile getProfile() {
      return profile;
    }

    /** @return physical timestep in seconds */
    public double getTimeStepSeconds() {
      return timeStepSeconds;
    }

    /** @return total duration in seconds */
    public double getDurationSeconds() {
      return durationSeconds;
    }

    /** @return initiating event time in seconds */
    public double getEventSeconds() {
      return eventSeconds;
    }

    /**
     * Returns a revised definition carrying an explicit governed configuration evidence reference.
     *
     * @param reference nonblank frozen evidence reference
     * @return new definition preserving timing and existing references
     */
    public CaseDefinition withEvidenceReference(String reference) {
      if (reference == null || reference.trim().isEmpty()) {
        throw new IllegalArgumentException("Evidence reference is required");
      }
      CaseDefinition revised = new CaseDefinition(profile, timeStepSeconds, durationSeconds, eventSeconds);
      revised.evidenceReferences.addAll(evidenceReferences);
      revised.evidenceReferences.add(reference);
      return revised;
    }

    /** @return explicit governed configuration references */
    public List<String> getEvidenceReferences() {
      return Collections.unmodifiableList(evidenceReferences);
    }

    /** @return explicit fault overrides, never substituted into healthy source configuration */
    public Map<String, Object> getFaultOverrides() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("profile", profile.name());
      if (profile == Profile.SLOW_RECYCLE) {
        result.put("asvOpeningTravelSeconds", 5.0);
      }
      if (profile == Profile.STUCK_RECYCLE || profile == Profile.ASV_TRIP_FAILED) {
        result.put("asvActuatorAvailable", Boolean.FALSE);
      }
      if (profile == Profile.ISOLATION_FAILED) {
        result.put("inletActuatorAvailable", Boolean.FALSE);
      }
      if (profile == Profile.DRIVER_TRIP_FAILED) {
        result.put("driverCommandAvailable", Boolean.FALSE);
      }
      return Collections.unmodifiableMap(result);
    }
  }

  /** Actual configured-copy observation hook, called before initiating events or transient stepping. */
  public interface ConfigurationObserver extends Serializable {
    /**
     * Inspects the exact configured train that the native runner will execute.
     *
     * @param train actual isolated configured train
     * @param definition declared timing and fault profile
     */
    void inspect(Train train, CaseDefinition definition);
  }

  /**
   * Runtime case bundle retaining the exact native scenario, copy and result rather than rebuilding a second graph.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class CaseBundle {
    private final Train source;
    private final CaseDefinition definition;
    private final ConfigurationObserver observer;
    private final DynamicSafetyScenario scenario;
    private Train configured;
    private DynamicSafetyScenarioResult nativeResult;

    /**
     * Creates the native scenario definition without executing a physical step.
     *
     * @param source actual pristine canonical source train
     * @param definition explicit case definition
     * @param observer optional configured-copy inspection hook
     */
    private CaseBundle(Train source, CaseDefinition definition, ConfigurationObserver observer) {
      this.source = source;
      this.definition = definition;
      this.observer = observer;
      String id = "COMPRESSOR-" + definition.profile + "-DT-" + definition.timeStepSeconds;
      DynamicSafetyScenario.Builder builder = DynamicSafetyScenario.builder(id, id)
          .durationSeconds(definition.durationSeconds).timeStepSeconds(definition.timeStepSeconds)
          .triggerTimeSeconds(definition.eventSeconds).eventExactTiming(true)
          .configureCase("CompressorAutomationStudy.configure.v1." + definition.profile, process -> {
            Train train = fromProcess(process);
            train.applyFaultOverrides(definition);
          })
          .initiatingEvent("CompressorAutomationStudy.event.v1." + definition.profile,
              process -> fromProcess(process).initiate(definition))
          .addLogic("CompressorAutomationStudy.collector.v1", process -> {
            configured = fromProcess(process);
            configured.initializeDynamics();
            if (observer != null) {
              observer.inspect(configured, definition);
            }
            return new EvidenceCollector(configured, definition);
          });
      boolean controlCase = controlCase(definition.profile);
      double window = definition.durationSeconds - definition.eventSeconds;
      builder.addCriterion(DynamicScenarioCriterion
          .builder("DISCHARGE_ENVELOPE", "Sampled receiver pressure", "bara",
              "CompressorAutomationStudy.discharge-pressure.v1",
              process -> fromProcess(process).dischargeScrubber.getPressure())
          .acceptanceRange(1.0, 68.0).deadlineSeconds(window)
          .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).build());
      if (controlCase) {
        builder.addCriterion(DynamicScenarioCriterion.builder("SURGE_MARGIN", "Actual in-envelope map margin",
            "fraction", "CompressorAutomationStudy.map-margin.v1", process -> {
              Train train = fromProcess(process);
              return train.physicalMarginQualified() ? train.compressor.getValidatedDistanceToSurge() : -1.0;
            }).acceptanceRange(0.0, null).deadlineSeconds(window)
            .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).build());
        builder.addCriterion(DynamicScenarioCriterion
            .builder("NO_UNWANTED_TRIP", "No control-case shutdown", "boolean", "CompressorAutomationStudy.no-trip.v1",
                process -> fromProcess(process).inletValve.isEnergized() ? 1.0 : 0.0)
            .acceptanceRange(1.0, 1.0).deadlineSeconds(window)
            .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).build());
        if (definition.profile != Profile.NORMAL) {
          builder.addCriterion(DynamicScenarioCriterion
              .builder("RECYCLE_FLOW", "Actual positive recycle demand response", "kg/sec",
                  "CompressorAutomationStudy.recycle-flow.v1",
                  process -> fromProcess(process).antiSurgeValve.getOutletStream().getFlowRate("kg/sec"))
              .acceptanceRange(0.01, null).deadlineSeconds(5.0).build());
        }
      } else {
        builder.addCriterion(DynamicScenarioCriterion
            .builder("SHAFT_STOP", "Actual native shaft stop", "rpm", "CompressorAutomationStudy.shaft-speed.v1",
                process -> fromProcess(process).compressor.getSpeed())
            .acceptanceRange(0.0, 1.0).deadlineSeconds(4.0).build());
        builder.addCriterion(DynamicScenarioCriterion
            .builder("INLET_ISOLATED", "Actual feed isolation", "percent",
                "CompressorAutomationStudy.inlet-position.v1",
                process -> fromProcess(process).inletValve.getPercentValveOpening())
            .acceptanceRange(0.0, 0.1).deadlineSeconds(2.0).build());
        builder.addCriterion(DynamicScenarioCriterion
            .builder("EXPORT_ISOLATED", "Actual export isolation", "percent",
                "CompressorAutomationStudy.export-position.v1",
                process -> fromProcess(process).exportValve.getPercentValveOpening())
            .acceptanceRange(0.0, 0.1).deadlineSeconds(2.0).build());
        builder.addCriterion(DynamicScenarioCriterion
            .builder("RECYCLE_FAIL_OPEN", "Actual fail-open recycle", "percent",
                "CompressorAutomationStudy.asv-trip-position.v1",
                process -> fromProcess(process).antiSurgeValve.getPercentValveOpening())
            .acceptanceRange(99.9, 100.0).deadlineSeconds(2.0).build());
        builder.addCriterion(
            DynamicScenarioCriterion.builder("HOLD_FINAL_ELEMENTS", "Hold actual shutdown state after four seconds",
                "boolean", "CompressorAutomationStudy.post-deadline-hold.v1", process -> {
                  Train train = fromProcess(process);
                  if (process.getTime() < definition.eventSeconds + 4.0 - 1.0e-9) {
                    return 1.0;
                  }
                  return train.compressor.getSpeed() <= 1.0 && train.inletValve.getPercentValveOpening() <= 0.1
                      && train.exportValve.getPercentValveOpening() <= 0.1
                      && train.antiSurgeValve.getPercentValveOpening() >= 99.9 ? 1.0 : 0.0;
                }).acceptanceRange(1.0, 1.0).deadlineSeconds(window)
                .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).build());
      }
      builder.addCriterion(DynamicScenarioCriterion
          .builder("GAS_CV_APPLICABILITY", "Actual gas-valve inlet phase applicability", "boolean",
              "CompressorAutomationStudy.gas-phase.v1", process -> fromProcess(process).gasCvApplicable() ? 1.0 : 0.0)
          .acceptanceRange(1.0, 1.0).deadlineSeconds(window)
          .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).build());
      builder
          .addCriterion(DynamicScenarioCriterion
              .builder("SUCTION_ENERGY", "Separate native suction cumulative energy", "boolean",
                  "CompressorAutomationStudy.suction-energy.v1",
                  process -> fromProcess(process).suctionScrubber.assessTransientEnergyBalance(1.0e-5).name()
                      .equals("PASS") ? 1.0 : 0.0)
              .acceptanceRange(1.0, 1.0).deadlineSeconds(window).required(false)
              .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).build());
      builder.addCriterion(DynamicScenarioCriterion
          .builder("DISCHARGE_ENERGY", "Separate native discharge cumulative energy", "boolean",
              "CompressorAutomationStudy.discharge-energy.v1",
              process -> fromProcess(process).dischargeScrubber.assessTransientEnergyBalance(1.0e-5).name()
                  .equals("PASS") ? 1.0 : 0.0)
          .acceptanceRange(1.0, 1.0).deadlineSeconds(window).required(false)
          .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).build());
      for (String reference : definition.evidenceReferences) {
        builder.addEvidenceReference(reference);
      }
      for (ProcessEquipmentInterface unit : source.process.getUnitOperations()) {
        builder.addEquipmentTag(unit.getName());
      }
      scenario = builder.build();
    }

    /** @return exact native scenario definition */
    public DynamicSafetyScenario getScenario() {
      return scenario;
    }

    /** @return actual isolated configured copy, available after the runner creates it */
    public Train getConfiguredTrain() {
      return configured;
    }

    /** @return actual native result, available after execution */
    public DynamicSafetyScenarioResult getNativeResult() {
      return nativeResult;
    }

    /**
     * Accepts the exact native result from a coordinated EngineeringSimulationRunner without rerunning physics.
     *
     * @param result actual completed native scenario result
     * @return typed study result with the same actual configured-copy samples
     */
    public Result complete(DynamicSafetyScenarioResult result) {
      if (result == null || !scenario.getId().equals(result.getScenarioId()) || configured == null) {
        throw new IllegalArgumentException(
            "The matching executed native scenario and actual configured copy are required");
      }
      nativeResult = result;
      return new Result(this);
    }

    /**
     * Executes the existing strict native scenario runner against the actual source process.
     *
     * @return typed native result and actual physical samples
     */
    public Result run() {
      nativeResult = DynamicSafetyScenarioRunner.runStrict(source.process, scenario);
      return new Result(this);
    }
  }

  /**
   * Actual native scenario response and source-backed physical samples; no expected outcome is substituted.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class Result {
    private final CaseBundle bundle;

    /**
     * Retains the actual executed native case.
     *
     * @param bundle executed native case
     */
    private Result(CaseBundle bundle) {
      this.bundle = bundle;
    }

    /** @return native response, with errors and intentional physical failures retained */
    public DynamicSafetyScenarioResult getNativeResult() {
      return bundle.nativeResult;
    }

    /** @return immutable physical sample list */
    public List<Map<String, Object>> getSamples() {
      return bundle.configured == null ? Collections.<Map<String, Object>>emptyList()
          : Collections.unmodifiableList(bundle.configured.samples);
    }

    /** @return actual isolated executed train */
    public Train getConfiguredTrain() {
      return bundle.configured;
    }

    /** @return serialization-safe raw study evidence */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_compressor_automation_study.v1");
      result.put("profile", bundle.definition.profile.name());
      result.put("timeStepSeconds", bundle.definition.timeStepSeconds);
      result.put("durationSeconds", bundle.definition.durationSeconds);
      result.put("eventSeconds", bundle.definition.eventSeconds);
      result.put("faultOverrides", bundle.definition.getFaultOverrides());
      result.put("nativeResult", bundle.nativeResult.toMap());
      result.put("samples", getSamples());
      result.put("qualification", "SYNTHETIC_MAP_AND_DRIVER_PROFILE; CONTROLLED_STANDARDS_VENDOR_SIL_APPROVAL_HOLD");
      return jsonSafe(result);
    }
  }

  /**
   * Creates a native case without rebuilding or discarding the canonical-configured source train.
   *
   * @param source actual canonical-configured source train
   * @param definition declared case timing and faults
   * @param observer optional exact configured-copy inspection
   * @return native case bundle
   */
  public static CaseBundle createCase(Train source, CaseDefinition definition, ConfigurationObserver observer) {
    if (source == null || definition == null) {
      throw new IllegalArgumentException("Source and definition are required");
    }
    return new CaseBundle(source, definition, observer);
  }

  /**
   * Runs one case using the existing strict native scenario runner.
   *
   * @param source actual canonical-configured source train
   * @param definition declared case timing and faults
   * @return actual native result and physical samples
   */
  public static Result run(Train source, CaseDefinition definition) {
    return createCase(source, definition, null).run();
  }

  /** @return all declared profiles, without inferring that fault cases pass protection checks */
  public static List<CaseDefinition> defaultCases() {
    List<CaseDefinition> result = new ArrayList<CaseDefinition>();
    for (Profile profile : Profile.values()) {
      result.add(new CaseDefinition(profile.name(), 0.1));
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Finds the actual train from the owned native process type, rejecting a name-based replacement.
   *
   * @param process actual native process
   * @return owned train
   */
  public static Train fromProcess(ProcessSystem process) {
    if (!(process instanceof TrainProcess)) {
      throw new IllegalArgumentException("Actual compressor train process is required");
    }
    return ((TrainProcess) process).owner;
  }

  /**
   * Identifies cases testing BPCS anti-surge response rather than a commanded complete shutdown.
   *
   * @param profile declared test profile
   * @return true for actual running-control cases
   */
  private static boolean controlCase(Profile profile) {
    return profile == Profile.NORMAL || profile == Profile.LOAD_REJECTION || profile == Profile.SLOW_RECYCLE
        || profile == Profile.STUCK_RECYCLE || profile == Profile.FT_BIASED_HIGH || profile == Profile.FT_FROZEN;
  }

  /**
   * Evidence-only native runner logic: physical stepping owns SIF scans, so this collector never executes them twice.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static final class EvidenceCollector implements ProcessLogic, DynamicSafetyScenarioEvidenceProvider {
    private static final long serialVersionUID = 1L;
    private final Train train;
    private final CaseDefinition definition;
    private boolean active;

    /**
     * Retains actual execution objects for evidence collection only.
     *
     * @param train actual executed train
     * @param definition actual timing and faults
     */
    private EvidenceCollector(Train train, CaseDefinition definition) {
      this.train = train;
      this.definition = definition;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
      return "30-COMPRESSOR-EVIDENCE";
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
    }

    /** {@inheritDoc} */
    @Override
    public void deactivate() {
      active = false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean reset() {
      active = false;
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(double dt) {
      /* Evidence only; the train owns the physical and logic scans. */ }

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
      return train.process.getUnitOperations();
    }

    /** {@inheritDoc} */
    @Override
    public String getStatusDescription() {
      return "Native source-backed compressor evidence collector";
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getDynamicSafetyScenarioEvidence() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      List<Map<String, Object>> functions = new ArrayList<Map<String, Object>>();
      for (ClosedLoopSafetyFunction function : train.safetyFunctions) {
        functions.add(function.getDynamicSafetyScenarioEvidence());
      }
      result.put("nativeFunctions", functions);
      result.put("profile", definition.profile.name());
      result.put("faultOverrides", definition.getFaultOverrides());
      result.put("samples", train.samples);
      return jsonSafe(result);
    }
  }

  /**
   * Frozen physical assumptions for the standard synthetic train, using absolute pressure and SI flow.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class Design implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Double> composition = new LinkedHashMap<String, Double>();
    private double suctionDiameterM = 1.5;
    private double suctionLengthM = 4.0;
    private double dischargeDiameterM = 1.5;
    private double dischargeLengthM = 4.0;
    private double suctionInitialLevelFraction = 0.0;
    private double dischargeInitialLevelFraction = 0.25;
    private double feedFlowKgPerSecond = 20000.0 / 3600.0;
    private double feedPressureBara = 32.0;
    private double suctionPressureBara = 30.0;
    private double dischargePressureBara = 60.0;
    private double exportPressureBara = 50.0;
    private double feedTemperatureK = 333.15;
    private double coolerTemperatureK = 303.15;
    private double coolingMediumTemperatureK = 293.15;
    private double coolerActuatorTimeConstantSeconds = 0.5;
    private double coolerThermalTimeConstantSeconds = 1.0;
    private double antiSurgeCv = 100.0;
    private double antiSurgeOpeningSeconds = 0.3;
    private double antiSurgeClosingSeconds = 2.0;
    private double isolationStrokeSeconds = 0.5;
    private double surgeMarginSetPoint = 0.20;
    private double antiSurgeProportionalGain = 400.0;
    private double antiSurgeIntegralTimeSeconds = 2.0;
    private double capacityPressureGainRpmPerBar = 100.0;
    private double capacityPressureIntegralTimeSeconds = 5.0;
    private double driverRampRpmPerSecond = 5000.0;
    private double pressureTripBara = 65.0;
    private double temperatureTripK = 350.0;
    private double levelTripFraction = 0.80;

    /** Creates the declared standard synthetic design. */
    public Design() {
      composition.put("methane", 0.82);
      composition.put("ethane", 0.08);
      composition.put("propane", 0.05);
      composition.put("n-butane", 0.025);
      composition.put("n-pentane", 0.01);
      composition.put("n-hexane", 0.01);
      composition.put("n-heptane", 0.005);
    }

    /**
     * Validates all governed scalar assumptions before building native equipment.
     *
     * @throws IllegalArgumentException when physical assumptions are invalid
     */
    private void validate() {
      if (composition.isEmpty()) {
        throw new IllegalArgumentException("A nonempty mole-fraction composition is required");
      }
      double sum = 0.0;
      for (Map.Entry<String, Double> item : composition.entrySet()) {
        if (item.getKey() == null || item.getKey().trim().isEmpty() || item.getValue() == null
            || !Double.isFinite(item.getValue()) || item.getValue() < 0.0) {
          throw new IllegalArgumentException("Component names and nonnegative finite mole fractions are required");
        }
        sum += item.getValue();
      }
      if (!Double.isFinite(sum) || sum <= 0.0) {
        throw new IllegalArgumentException("Composition sum must be positive and finite");
      }
      for (double value : new double[] {suctionDiameterM, suctionLengthM, dischargeDiameterM, dischargeLengthM,
          feedFlowKgPerSecond, feedPressureBara, suctionPressureBara, dischargePressureBara, exportPressureBara,
          feedTemperatureK, coolerTemperatureK, coolingMediumTemperatureK, coolerActuatorTimeConstantSeconds,
          coolerThermalTimeConstantSeconds, antiSurgeCv, antiSurgeOpeningSeconds, antiSurgeClosingSeconds,
          isolationStrokeSeconds, driverRampRpmPerSecond, pressureTripBara, temperatureTripK, surgeMarginSetPoint}) {
        if (!Double.isFinite(value) || value <= 0.0) {
          throw new IllegalArgumentException("Design values must be finite and positive");
        }
      }
      if (!(feedPressureBara > suctionPressureBara && dischargePressureBara > exportPressureBara
          && exportPressureBara > suctionPressureBara && pressureTripBara > dischargePressureBara)) {
        throw new IllegalArgumentException("Design pressure ordering or trip envelope is invalid");
      }
      if (!Double.isFinite(suctionInitialLevelFraction) || suctionInitialLevelFraction < 0.0
          || suctionInitialLevelFraction >= 1.0 || !Double.isFinite(dischargeInitialLevelFraction)
          || dischargeInitialLevelFraction < 0.0 || dischargeInitialLevelFraction >= 1.0) {
        throw new IllegalArgumentException("Initial liquid fractions must be finite in [0,1)");
      }
      if (!Double.isFinite(levelTripFraction) || levelTripFraction <= 0.0 || levelTripFraction >= 1.0) {
        throw new IllegalArgumentException("Level trip fraction must lie strictly between zero and one");
      }
    }

    /**
     * Returns caller mole-fraction inputs before explicit normalization.
     *
     * @return defensive composition copy
     */
    public Map<String, Double> getComposition() {
      return Collections.unmodifiableMap(new LinkedHashMap<String, Double>(composition));
    }

    /**
     * Supplies mole-fraction inputs; the factory validates and normalizes their positive sum.
     *
     * @param values nonempty component-to-mole-fraction inputs
     */
    public void setComposition(Map<String, Double> values) {
      if (values == null) {
        throw new IllegalArgumentException("Composition is required");
      }
      composition.clear();
      composition.putAll(values);
    }

    /** Returns suction vessel internal diameter in m. @return suction vessel internal diameter in m */
    public double getSuctionDiameterM() {
      return suctionDiameterM;
    }

    /** Sets suction vessel internal diameter in m. @param value suction vessel internal diameter in m */
    public void setSuctionDiameterM(double value) {
      suctionDiameterM = value;
    }

    /** Returns suction vessel length in m. @return suction vessel length in m */
    public double getSuctionLengthM() {
      return suctionLengthM;
    }

    /** Sets suction vessel length in m. @param value suction vessel length in m */
    public void setSuctionLengthM(double value) {
      suctionLengthM = value;
    }

    /** Returns discharge vessel internal diameter in m. @return discharge vessel internal diameter in m */
    public double getDischargeDiameterM() {
      return dischargeDiameterM;
    }

    /** Sets discharge vessel internal diameter in m. @param value discharge vessel internal diameter in m */
    public void setDischargeDiameterM(double value) {
      dischargeDiameterM = value;
    }

    /** Returns discharge vessel length in m. @return discharge vessel length in m */
    public double getDischargeLengthM() {
      return dischargeLengthM;
    }

    /** Sets discharge vessel length in m. @param value discharge vessel length in m */
    public void setDischargeLengthM(double value) {
      dischargeLengthM = value;
    }

    /** Returns initial suction liquid fraction. @return initial suction liquid fraction */
    public double getSuctionInitialLevelFraction() {
      return suctionInitialLevelFraction;
    }

    /** Sets initial suction liquid fraction. @param value initial suction liquid fraction */
    public void setSuctionInitialLevelFraction(double value) {
      suctionInitialLevelFraction = value;
    }

    /** Returns initial discharge liquid fraction. @return initial discharge liquid fraction */
    public double getDischargeInitialLevelFraction() {
      return dischargeInitialLevelFraction;
    }

    /** Sets initial discharge liquid fraction. @param value initial discharge liquid fraction */
    public void setDischargeInitialLevelFraction(double value) {
      dischargeInitialLevelFraction = value;
    }

    /** @return feed mass rate in kg/s */
    public double getFeedFlowKgPerSecond() {
      return feedFlowKgPerSecond;
    }

    /** @param value feed mass rate in kg/s */
    public void setFeedFlowKgPerSecond(double value) {
      feedFlowKgPerSecond = value;
    }

    /** @return source pressure in bara */
    public double getFeedPressureBara() {
      return feedPressureBara;
    }

    /** @param value source pressure in bara */
    public void setFeedPressureBara(double value) {
      feedPressureBara = value;
    }

    /** @return suction design pressure in bara */
    public double getSuctionPressureBara() {
      return suctionPressureBara;
    }

    /** @param value suction design pressure in bara */
    public void setSuctionPressureBara(double value) {
      suctionPressureBara = value;
    }

    /** @return discharge design pressure in bara */
    public double getDischargePressureBara() {
      return dischargePressureBara;
    }

    /** @param value discharge design pressure in bara */
    public void setDischargePressureBara(double value) {
      dischargePressureBara = value;
    }

    /** @return imposed export pressure in bara */
    public double getExportPressureBara() {
      return exportPressureBara;
    }

    /** @param value imposed export pressure in bara */
    public void setExportPressureBara(double value) {
      exportPressureBara = value;
    }

    /** @return source temperature in K */
    public double getFeedTemperatureK() {
      return feedTemperatureK;
    }

    /** @param value source temperature in K */
    public void setFeedTemperatureK(double value) {
      feedTemperatureK = value;
    }

    /** @return cooler outlet specification in K */
    public double getCoolerTemperatureK() {
      return coolerTemperatureK;
    }

    /** @param value cooler outlet specification in K */
    public void setCoolerTemperatureK(double value) {
      coolerTemperatureK = value;
    }

    /** Returns the cooling-medium supply temperature in K. @return configured value */
    public double getCoolingMediumTemperatureK() {
      return coolingMediumTemperatureK;
    }

    /** Sets the cooling-medium supply temperature in K. @param value configured value */
    public void setCoolingMediumTemperatureK(double value) {
      coolingMediumTemperatureK = value;
    }

    /** Returns the lumped cooling-valve time constant in seconds. @return configured value */
    public double getCoolerActuatorTimeConstantSeconds() {
      return coolerActuatorTimeConstantSeconds;
    }

    /** Sets the lumped cooling-valve time constant in seconds. @param value configured value */
    public void setCoolerActuatorTimeConstantSeconds(double value) {
      coolerActuatorTimeConstantSeconds = value;
    }

    /** Returns the lumped process thermal time constant in seconds. @return configured value */
    public double getCoolerThermalTimeConstantSeconds() {
      return coolerThermalTimeConstantSeconds;
    }

    /** Sets the lumped process thermal time constant in seconds. @param value configured value */
    public void setCoolerThermalTimeConstantSeconds(double value) {
      coolerThermalTimeConstantSeconds = value;
    }

    /** @return synthetic full-open US Cv */
    public double getAntiSurgeCv() {
      return antiSurgeCv;
    }

    /** @param value synthetic full-open US Cv */
    public void setAntiSurgeCv(double value) {
      antiSurgeCv = value;
    }

    /** @return normal opening full-stroke time in seconds */
    public double getAntiSurgeOpeningSeconds() {
      return antiSurgeOpeningSeconds;
    }

    /** @param value normal opening full-stroke time in seconds */
    public void setAntiSurgeOpeningSeconds(double value) {
      antiSurgeOpeningSeconds = value;
    }

    /** @return normal closing full-stroke time in seconds */
    public double getAntiSurgeClosingSeconds() {
      return antiSurgeClosingSeconds;
    }

    /** @param value normal closing full-stroke time in seconds */
    public void setAntiSurgeClosingSeconds(double value) {
      antiSurgeClosingSeconds = value;
    }

    /** @return isolation full stroke in seconds */
    public double getIsolationStrokeSeconds() {
      return isolationStrokeSeconds;
    }

    /** @param value isolation full stroke in seconds */
    public void setIsolationStrokeSeconds(double value) {
      isolationStrokeSeconds = value;
    }

    /** @return Qactual/Qsurge minus one set point */
    public double getSurgeMarginSetPoint() {
      return surgeMarginSetPoint;
    }

    /** @param value Qactual/Qsurge minus one set point */
    public void setSurgeMarginSetPoint(double value) {
      surgeMarginSetPoint = value;
    }

    /**
     * Returns the anti-surge proportional gain in percent per margin fraction.
     *
     * @return proportional gain
     */
    public double getAntiSurgeProportionalGain() {
      return antiSurgeProportionalGain;
    }

    /**
     * Sets the anti-surge proportional gain.
     *
     * @param value percent per margin fraction
     */
    public void setAntiSurgeProportionalGain(double value) {
      antiSurgeProportionalGain = value;
    }

    /**
     * Returns the native discharge-pressure proportional gain in rpm/bar.
     *
     * @return configured policy value
     */
    public double getCapacityPressureGainRpmPerBar() {
      return capacityPressureGainRpmPerBar;
    }

    /**
     * Sets the native discharge-pressure proportional gain in rpm/bar.
     *
     * @param value configured policy value
     */
    public void setCapacityPressureGainRpmPerBar(double value) {
      capacityPressureGainRpmPerBar = value;
    }

    /**
     * Returns the native discharge-pressure integral time in seconds.
     *
     * @return configured policy value
     */
    public double getCapacityPressureIntegralTimeSeconds() {
      return capacityPressureIntegralTimeSeconds;
    }

    /**
     * Sets the native discharge-pressure integral time in seconds.
     *
     * @param value configured policy value
     */
    public void setCapacityPressureIntegralTimeSeconds(double value) {
      capacityPressureIntegralTimeSeconds = value;
    }

    /**
     * Returns the native anti-surge integral time.
     *
     * @return integral time in seconds
     */
    public double getAntiSurgeIntegralTimeSeconds() {
      return antiSurgeIntegralTimeSeconds;
    }

    /**
     * Sets the native anti-surge integral time.
     *
     * @param value integral time in seconds
     */
    public void setAntiSurgeIntegralTimeSeconds(double value) {
      antiSurgeIntegralTimeSeconds = value;
    }

    /** @return explicitly programmed rundown rate in rpm/s */
    public double getDriverRampRpmPerSecond() {
      return driverRampRpmPerSecond;
    }

    /** @param value explicitly programmed rundown rate in rpm/s */
    public void setDriverRampRpmPerSecond(double value) {
      driverRampRpmPerSecond = value;
    }

    /** @return discharge pressure trip setting in bara */
    public double getPressureTripBara() {
      return pressureTripBara;
    }

    /** @param value discharge pressure trip setting in bara */
    public void setPressureTripBara(double value) {
      pressureTripBara = value;
    }

    /** @return cooled discharge temperature trip setting in K */
    public double getTemperatureTripK() {
      return temperatureTripK;
    }

    /** @param value cooled discharge temperature trip setting in K */
    public void setTemperatureTripK(double value) {
      temperatureTripK = value;
    }

    /** @return suction high-high level setting as a fraction */
    public double getLevelTripFraction() {
      return levelTripFraction;
    }

    /** @param value suction high-high level setting as a fraction */
    public void setLevelTripFraction(double value) {
      levelTripFraction = value;
    }
  }

  /**
   * Actual coupled objects and explicit physical-step execution contract for one train.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class Train implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Design design;
    private final TrainProcess process;
    private final Stream feed;
    private final ESDValve inletValve;
    private final Mixer suctionMixer;
    private final Separator suctionScrubber;
    private final Compressor compressor;
    private final Cooler aftercooler;
    private final CheckValve dischargeCheckValve;
    private final Separator dischargeScrubber;
    private final Splitter dischargeTee;
    private final ESDValve exportValve;
    private final ESDValve antiSurgeValve;
    private final ThrottlingValve suctionDrain;
    private final ThrottlingValve dischargeDrain;
    private final AntiSurgeController antiSurgeController;
    private final ControllerDeviceBaseClass capacityPressureController;
    private final ControllerDeviceBaseClass coolerTemperatureController;
    private final CompressorSurgeMarginMeasurement surgeMeasurement;
    private final List<ClosedLoopSafetyFunction> safetyFunctions = new ArrayList<ClosedLoopSafetyFunction>();
    private final List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
    private final List<TripCompressorAction> driverActions = new ArrayList<TripCompressorAction>();
    private final double referenceFlowM3PerHour;
    private final double referenceHeadKjPerKg;
    private double compressorFlowResidualKgPerSecond;
    private double lastCompressorMassFlow;
    private int lastRootIterations;
    private boolean forwardCompressorFlow;
    private boolean dynamicsInitialized;
    private UUID lastStepId;
    private Map<String, Object> lastBoundary = Collections.emptyMap();

    /**
     * Creates actual physical objects, then instruments the same objects.
     *
     * @param assumptions copied design assumptions
     */
    private Train(Design assumptions) {
      design = copyDesign(assumptions);
      design.validate();
      process = new TrainProcess(this);
      process.setName("Synthetic natural-gas compressor automation train");
      SystemInterface gas = new SystemSrkEos(design.feedTemperatureK, design.feedPressureBara);
      double compositionSum = 0.0;
      for (double fraction : design.composition.values()) {
        compositionSum += fraction;
      }
      for (Map.Entry<String, Double> item : design.composition.entrySet()) {
        if (item.getValue() > 0.0) {
          gas.addComponent(item.getKey(), item.getValue() / compositionSum);
        }
      }
      gas.setMixingRule("classic");
      gas.setMultiPhaseCheck(true);
      feed = new Stream("30-FEED-001", gas);
      feed.setFlowRate(design.feedFlowKgPerSecond, "kg/sec");
      feed.run();
      inletValve = isolation("30-ESDV-100", feed, design.suctionPressureBara, 70.0, 0.0);
      inletValve.run();
      Stream seed = new Stream("30-RECYCLE-BOOTSTRAP", inletValve.getOutletStream().getFluid().clone());
      seed.getFluid().setTotalNumberOfMoles(0.0);
      suctionMixer = new Mixer("30-MIX-100");
      suctionMixer.addStream(inletValve.getOutletStream());
      suctionMixer.addStream(seed);
      suctionMixer.run();
      suctionScrubber = vessel("30-V-100", suctionMixer.getOutletStream());
      suctionScrubber.run();
      compressor = new Compressor("30-K-100", suctionScrubber.getGasOutStream());
      compressor.setUsePolytropicCalc(true);
      compressor.setPolytropicEfficiency(0.80);
      compressor.setOutletPressure(design.dischargePressureBara + 0.2, "bara");
      compressor.setSpeed(10000.0);
      compressor.run();
      referenceFlowM3PerHour = compressor.getInletStream().getFlowRate("m3/hr");
      referenceHeadKjPerKg = compressor.getPolytropicFluidHead();
      installSyntheticMap();
      compressor.getAntiSurge().setActive(false);
      compressor.setOperatingState(CompressorState.RUNNING);
      compressor.setMaxDecelerationRate(design.driverRampRpmPerSecond);
      ShutdownProfile profile = new ShutdownProfile(ShutdownProfile.ShutdownType.EMERGENCY, compressor.getSpeed());
      profile.setEmergencyRampRate(design.driverRampRpmPerSecond);
      profile.setOpenAntisurgeOnShutdown(false);
      profile.setShutdownType(ShutdownProfile.ShutdownType.EMERGENCY, compressor.getSpeed());
      compressor.setShutdownProfile(profile);
      compressor.run();
      dischargeCheckValve = new CheckValve("30-NRV-100", compressor.getOutletStream());
      dischargeCheckValve.setCrackingPressure(0.0);
      dischargeCheckValve.setOutletPressure(design.dischargePressureBara, "bara");
      dischargeCheckValve.setCv(10000.0);
      dischargeCheckValve.setAllowChoked(true);
      dischargeCheckValve.run();
      dischargeCheckValve.autoSize(1.0, 100.0);
      aftercooler = new Cooler("30-E-100", dischargeCheckValve.getOutletStream());
      aftercooler.setOutTemperature(design.coolerTemperatureK);
      aftercooler.run();
      dischargeScrubber = vessel("30-V-101", aftercooler.getOutletStream());
      dischargeScrubber.run();
      dischargeTee = new Splitter("30-TEE-101", dischargeScrubber.getGasOutStream(), 2);
      dischargeTee.setSplitFactors(new double[] {1.0, 0.0});
      dischargeTee.run();
      exportValve = isolation("30-PCV-101", dischargeTee.getSplitStream(0), design.exportPressureBara, 70.0, 0.0);
      antiSurgeValve = isolation("30-ASV-100", dischargeTee.getSplitStream(1), design.suctionPressureBara, 0.0, 100.0);
      antiSurgeValve.setCv(design.antiSurgeCv);
      antiSurgeValve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
      antiSurgeValve.setOpeningTravelTime(design.antiSurgeOpeningSeconds);
      antiSurgeValve.setClosingTravelTime(design.antiSurgeClosingSeconds);
      antiSurgeValve.setStrokeTime(design.antiSurgeOpeningSeconds);
      suctionDrain = drain("30-LCV-100", suctionScrubber.getLiquidOutStream());
      dischargeDrain = drain("30-LCV-101", dischargeScrubber.getLiquidOutStream());
      exportValve.run();
      antiSurgeValve.run();
      suctionDrain.run();
      dischargeDrain.run();
      suctionMixer.replaceStream(1, antiSurgeValve.getOutletStream());
      inletValve.getOutletStream().setName("30-SUCT-FEED");
      suctionMixer.getOutletStream().setName("30-SUCT-MIXED");
      suctionScrubber.getGasOutStream().setName("30-COMP-SUCTION");
      suctionScrubber.getLiquidOutStream().setName("30-SUCT-CONDENSATE");
      compressor.getOutletStream().setName("30-COMP-DISCHARGE");
      dischargeCheckValve.getOutletStream().setName("30-DISCHARGE-CHECKED");
      aftercooler.getOutletStream().setName("30-COOLED-DISCHARGE");
      dischargeScrubber.getGasOutStream().setName("30-DISCHARGE-GAS");
      dischargeScrubber.getLiquidOutStream().setName("30-DISCH-CONDENSATE");
      dischargeTee.getSplitStream(0).setName("30-EXPORT-FEED");
      dischargeTee.getSplitStream(1).setName("30-RECYCLE-FEED");
      exportValve.getOutletStream().setName("30-GAS-EXPORT");
      antiSurgeValve.getOutletStream().setName("30-COLD-RECYCLE");
      suctionDrain.getOutletStream().setName("30-SUCT-LIQ-EXPORT");
      dischargeDrain.getOutletStream().setName("30-DISCH-LIQ-EXPORT");
      for (ThrottlingValve valve : Arrays.asList(inletValve, exportValve, suctionDrain, dischargeDrain)) {
        valve.autoSize(1.0, valve == suctionDrain || valve == dischargeDrain ? 50.0 : 70.0);
      }
      for (ProcessEquipmentInterface unit : Arrays.<ProcessEquipmentInterface>asList(feed, inletValve, suctionMixer,
          suctionScrubber, compressor, aftercooler, dischargeCheckValve, dischargeScrubber, dischargeTee, exportValve,
          antiSurgeValve, suctionDrain, dischargeDrain)) {
        process.add(unit);
      }
      pressure("30-PT-100", suctionScrubber.getGasOutStream());
      pressure("30-PT-101", dischargeScrubber.getGasOutStream());
      pressure("30-PT-102", dischargeScrubber.getGasOutStream());
      temperature("30-TT-100", compressor.getInletStream());
      temperature("30-TT-101", compressor.getOutletStream());
      temperature("30-TT-102", aftercooler.getOutletStream());
      temperature("30-TT-103", aftercooler.getOutletStream());
      VolumeFlowTransmitter actualFlow = flow("30-FT-100", compressor.getInletStream(), "m^3/hr");
      flow("30-FT-101", antiSurgeValve.getOutletStream(), "kg/hr");
      flow("30-FT-102", exportValve.getOutletStream(), "kg/hr");
      LevelTransmitter suctionLevel = level("30-LT-100", suctionScrubber);
      LevelTransmitter dischargeLevel = level("30-LT-101", dischargeScrubber);
      level("30-LT-102", suctionScrubber);
      level("30-LT-103", dischargeScrubber);
      levelController("30-LIC-100", suctionDrain, suctionLevel);
      levelController("30-LIC-101", dischargeDrain, dischargeLevel);
      surgeMeasurement = new CompressorSurgeMarginMeasurement("30-XY-100", compressor, actualFlow);
      surgeMeasurement.setTransientSamplingEnabled(true);
      process.add(surgeMeasurement);
      antiSurgeController = new AntiSurgeController("30-ASC-100", compressor, antiSurgeValve);
      antiSurgeController.setSurgeMarginMeasurement(surgeMeasurement);
      antiSurgeController.setSurgeMarginSetPoint(design.surgeMarginSetPoint);
      antiSurgeController.setProportionalGain(design.antiSurgeProportionalGain);
      antiSurgeController.setIntegralTime(design.antiSurgeIntegralTimeSeconds);
      antiSurgeController.setActuatorDynamics(Double.POSITIVE_INFINITY, 0.0);
      antiSurgeController.setEmergencyAction(0.0, 100.0);
      antiSurgeValve.setController(antiSurgeController);
      process.add(antiSurgeController);
      capacityPressureController = new ControllerDeviceBaseClass("30-PIC-101");
      capacityPressureController.setTransmitter(process.getMeasurementDevice("30-PT-101"));
      capacityPressureController.setControllerSetPoint(design.dischargePressureBara, "bara");
      capacityPressureController.setReverseActing(true);
      capacityPressureController.setControllerParameters(design.capacityPressureGainRpmPerBar,
          design.capacityPressureIntegralTimeSeconds, 0.0);
      capacityPressureController.setOutputLimits(7000.0, 11000.0);
      compressor.setMinimumSpeed(7000.0);
      compressor.setMaximumSpeed(11000.0);
      compressor.setMaxAccelerationRate(500.0);
      compressor.setControllerSpeedRateLimitEnabled(true);
      compressor.setController(capacityPressureController);
      process.add(capacityPressureController);
      aftercooler.configureDynamicTemperatureControl(aftercooler.getInletStream().getFlowRate("kg/hr"),
          aftercooler.getInletStream().getTemperature("K"), design.coolerTemperatureK, design.coolingMediumTemperatureK,
          "K");
      aftercooler.setDynamicTimeConstants(design.coolerActuatorTimeConstantSeconds,
          design.coolerThermalTimeConstantSeconds);
      coolerTemperatureController = new ControllerDeviceBaseClass("30-TIC-102");
      coolerTemperatureController.setTransmitter(process.getMeasurementDevice("30-TT-102"));
      coolerTemperatureController.setControllerSetPoint(design.coolerTemperatureK, "K");
      coolerTemperatureController.setControllerParameters(3.0, 2.0, 0.0);
      coolerTemperatureController.setReverseActing(false);
      coolerTemperatureController.setOutputLimits(0.0, 100.0);
      aftercooler.setController(coolerTemperatureController);
      process.add(coolerTemperatureController);
      process.add(new PushButton("30-HS-100"));
      installSafetyFunctions();
      steady(UUID.randomUUID());
      forwardCompressorFlow = true;
      lastCompressorMassFlow = compressor.getInletStream().getFlowRate("kg/sec");
    }

    /**
     * Constructs a native valve with declared pressure, fail position and choking policy.
     *
     * @param name actual valve tag
     * @param inlet actual inlet stream
     * @param pressureBara downstream pressure in bara
     * @param opening initial opening percentage
     * @param failPosition fail opening percentage
     * @return actual native valve
     */
    private ESDValve isolation(String name, StreamInterface inlet, double pressureBara, double opening,
        double failPosition) {
      ESDValve valve = new ESDValve(name, inlet);
      valve.setOutletPressure(pressureBara, "bara");
      valve.setPercentValveOpening(opening);
      valve.setFailSafePosition(failPosition);
      valve.setStrokeTime(design.isolationStrokeSeconds);
      valve.setAllowChoked(true);
      valve.setPhysicalStepIdempotencyEnabled(true);
      return valve;
    }

    /**
     * Creates a finite vertical vessel with explicit geometric assumptions.
     *
     * @param name actual vessel tag
     * @param inlet actual incoming stream
     * @return native separator
     */
    private Separator vessel(String name, StreamInterface inlet) {
      Separator vessel = new Separator(name, inlet);
      vessel.setOrientation("vertical");
      boolean suction = "30-V-100".equals(name);
      vessel.getMechanicalDesign().setInnerDiameter(suction ? design.suctionDiameterM : design.dischargeDiameterM);
      vessel.getMechanicalDesign().setTantanLength(suction ? design.suctionLengthM : design.dischargeLengthM);
      vessel.getMechanicalDesign().setDesign();
      return vessel;
    }

    /**
     * Creates a liquid drain to an explicit 10 bara battery limit.
     *
     * @param name actual valve tag
     * @param inlet vessel liquid outlet
     * @return native valve
     */
    private ThrottlingValve drain(String name, StreamInterface inlet) {
      ThrottlingValve valve = new ThrottlingValve(name, inlet);
      valve.setOutletPressure(10.0, "bara");
      valve.setPercentValveOpening(50.0);
      valve.setPhysicalStepIdempotencyEnabled(true);
      valve.setAllowChoked(true);
      return valve;
    }

    /** Installs a declared synthetic fan-law map with below-surge points inside its geometric lookup domain. */
    private void installSyntheticMap() {
      double[] speeds = {100.0, 1000.0, 3000.0, 5000.0, 7000.0, 8500.0, 10000.0, 11000.0};
      double[] flowFactors = {0.01, 0.20, 0.38, 0.60, 0.80, 1.0, 1.20, 1.42};
      double[] headFactors = {1.19998, 1.192, 1.17112, 1.128, 1.072, 1.0, 0.912, 0.79672};
      double[][] flows = new double[speeds.length][flowFactors.length];
      double[][] heads = new double[speeds.length][flowFactors.length];
      double[][] efficiencies = new double[speeds.length][flowFactors.length];
      double[] surgeFlows = new double[speeds.length];
      double[] surgeHeads = new double[speeds.length];
      for (int i = 0; i < speeds.length; i++) {
        double ratio = speeds[i] / 10000.0;
        for (int j = 0; j < flowFactors.length; j++) {
          flows[i][j] = referenceFlowM3PerHour * ratio * flowFactors[j];
          heads[i][j] = referenceHeadKjPerKg * ratio * ratio * headFactors[j];
          efficiencies[i][j] = 80.0;
        }
        surgeFlows[i] = referenceFlowM3PerHour * ratio * 0.70;
        surgeHeads[i] = referenceHeadKjPerKg * ratio * ratio * 1.102;
      }
      CompressorChart chart = new CompressorChart();
      chart.setCurves(new double[] {compressor.getInletStream().getFluid().getMolarMass()}, speeds, flows, heads,
          efficiencies);
      chart.setHeadUnit("kJ/kg");
      chart.setUseCompressorChart(true);
      chart.setSurgeCurve(new SafeSplineSurgeCurve(surgeFlows, surgeHeads));
      compressor.setCompressorChart(chart);
    }

    /**
     * Registers an actual held absolute-pressure instrument.
     *
     * @param name actual tag
     * @param stream actual source
     */
    private void pressure(String name, StreamInterface stream) {
      PressureTransmitter measurement = new PressureTransmitter(name, stream);
      measurement.setUnit("bara");
      measurement.setTransientSamplingEnabled(true);
      process.add(measurement);
    }

    /**
     * Registers an actual held temperature instrument.
     *
     * @param name actual tag
     * @param stream actual source
     */
    private void temperature(String name, StreamInterface stream) {
      TemperatureTransmitter measurement = new TemperatureTransmitter(name, stream);
      measurement.setUnit("K");
      measurement.setTransientSamplingEnabled(true);
      process.add(measurement);
    }

    /**
     * Registers an actual held flow instrument.
     *
     * @param name actual tag
     * @param stream actual source
     * @param unit explicit actual-volume or mass unit
     * @return registered instrument
     */
    private VolumeFlowTransmitter flow(String name, StreamInterface stream, String unit) {
      VolumeFlowTransmitter measurement = new VolumeFlowTransmitter(name, stream);
      measurement.setUnit(unit);
      measurement.setTransientSamplingEnabled(true);
      process.add(measurement);
      return measurement;
    }

    /**
     * Registers an actual held level instrument.
     *
     * @param name actual tag
     * @param vessel actual source
     * @return registered instrument
     */
    private LevelTransmitter level(String name, Separator vessel) {
      LevelTransmitter measurement = new LevelTransmitter(name, vessel);
      measurement.setTransientSamplingEnabled(true);
      process.add(measurement);
      return measurement;
    }

    /**
     * Attaches a native level PI loop to the actual drain valve.
     *
     * @param name controller tag
     * @param valve drain final element
     * @param measurement actual held input
     */
    private void levelController(String name, ThrottlingValve valve, LevelTransmitter measurement) {
      ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(name);
      controller.setTransmitter(measurement);
      controller.setControllerSetPoint(0.25, "fraction");
      controller.setControllerParameters(1.0, 10.0, 0.0);
      controller.setReverseActing(false);
      controller.setOutputLimits(0.0, 100.0);
      valve.setController(controller);
      process.add(controller);
    }

    /** Installs actual high-pressure, hot-export, high-level and manual native protection functions. */
    private void installSafetyFunctions() {
      for (String type : Arrays.asList("PRESSURE", "TEMPERATURE", "LEVEL", "DISCHARGE_LEVEL", "MANUAL")) {
        ESDLogic finalElements = new ESDLogic("30-ESD-" + type);
        ParallelActionGroup group = new ParallelActionGroup(
            "Trip actual compressor, isolate feed and export, open ASV");
        group.addAction(new TripValveAction(inletValve));
        group.addAction(new TripValveAction(exportValve));
        group.addAction(new TripValveAction(antiSurgeValve));
        TripCompressorAction driverAction = new TripCompressorAction(compressor);
        driverActions.add(driverAction);
        group.addAction(driverAction);
        finalElements.addAction(group, 0.0);
        String sif = "30-SIF-" + type;
        ClosedLoopSafetyFunction.Builder builder = ClosedLoopSafetyFunction
            .builder(sif, sif, process, VotingPattern.ONE_OUT_OF_ONE, finalElements).logicSolverDelaySeconds(0.05);
        if ("PRESSURE".equals(type)) {
          builder.addChannel(SafetyFunctionChannel
              .highTripFromMeasurement(process.getMeasurementDevice("30-PT-102"), "bara", design.pressureTripBara)
              .responseDelaySeconds(0.05).build());
        } else if ("TEMPERATURE".equals(type)) {
          builder.addChannel(SafetyFunctionChannel
              .highTripFromMeasurement(process.getMeasurementDevice("30-TT-103"), "K", design.temperatureTripK)
              .responseDelaySeconds(0.05).build());
        } else if (type.contains("LEVEL")) {
          builder.addChannel(SafetyFunctionChannel
              .highTripFromMeasurement(process.getMeasurementDevice("LEVEL".equals(type) ? "30-LT-102" : "30-LT-103"),
                  "fraction", design.levelTripFraction)
              .responseDelaySeconds(0.05).build());
        } else {
          builder.addChannel(SafetyFunctionChannel
              .manualTripFromPushButton((PushButton) process.getMeasurementDevice("30-HS-100")).build());
        }
        ClosedLoopSafetyFunction function = builder.build();
        function.activate();
        safetyFunctions.add(function);
      }
    }

    /**
     * Applies explicit case mechanical failures before frozen configured-copy inspection.
     *
     * @param definition declared physical test profile
     */
    public void applyFaultOverrides(CaseDefinition definition) {
      if (definition.profile == Profile.SLOW_RECYCLE) {
        antiSurgeValve.setOpeningTravelTime(5.0);
      }
      if (definition.profile == Profile.STUCK_RECYCLE || definition.profile == Profile.ASV_TRIP_FAILED) {
        antiSurgeValve.setActuatorAvailable(false);
      }
      if (definition.profile == Profile.ISOLATION_FAILED) {
        inletValve.setActuatorAvailable(false);
      }
      if (definition.profile == Profile.DRIVER_TRIP_FAILED) {
        for (TripCompressorAction action : driverActions) {
          action.setCommandAvailable(false);
        }
      }
    }

    /**
     * Applies the actual physical boundary change or a separately labeled field-signal test.
     *
     * @param definition declared case
     */
    private void initiate(CaseDefinition definition) {
      if (controlCase(definition.profile) && definition.profile != Profile.NORMAL) {
        exportValve.setOutletPressure(70.0, "bara");
      }
      if (definition.profile == Profile.FT_BIASED_HIGH) {
        ((MeasurementDeviceBaseClass) process.getMeasurementDevice("30-FT-100")).setFault(SensorFaultType.BIAS,
            referenceFlowM3PerHour);
      } else if (definition.profile == Profile.FT_FROZEN) {
        ((MeasurementDeviceBaseClass) process.getMeasurementDevice("30-FT-100"))
            .setFault(SensorFaultType.STUCK_AT_VALUE, referenceFlowM3PerHour);
      } else if (definition.profile == Profile.COOLING_LOSS) {
        aftercooler.setDynamicCoolingUtilityAvailable(false);
      } else if (definition.profile == Profile.PRESSURE_SIGNAL_HIGH) {
        ((MeasurementDeviceBaseClass) process.getMeasurementDevice("30-PT-102"))
            .setFault(SensorFaultType.STUCK_AT_VALUE, design.pressureTripBara + 2.0);
      } else if (definition.profile == Profile.DISCHARGE_LEVEL_SIGNAL_HIGH) {
        ((MeasurementDeviceBaseClass) process.getMeasurementDevice("30-LT-103"))
            .setFault(SensorFaultType.STUCK_AT_VALUE, 0.9);
      } else if (definition.profile == Profile.LEVEL_SIGNAL_HIGH) {
        ((MeasurementDeviceBaseClass) process.getMeasurementDevice("30-LT-102"))
            .setFault(SensorFaultType.STUCK_AT_VALUE, 0.9);
      } else if (!controlCase(definition.profile)) {
        ((PushButton) process.getMeasurementDevice("30-HS-100")).push();
      }
    }

    /**
     * Checks actual current inputs and stopped shaft before rearming bookkeeping; it issues no restart command.
     *
     * @return true only when pressure, cooled temperature, levels and manual demand are clear and shaft is stopped
     */
    public boolean resetPermitted() {
      return !((PushButton) process.getMeasurementDevice("30-HS-100")).isPushed()
          && process.getMeasurementDevice("30-PT-102").getMeasuredValue("bara") < design.pressureTripBara
          && process.getMeasurementDevice("30-TT-103").getMeasuredValue("K") < design.temperatureTripK
          && process.getMeasurementDevice("30-LT-102").getMeasuredValue("fraction") < design.levelTripFraction
          && process.getMeasurementDevice("30-LT-103").getMeasuredValue("fraction") < design.levelTripFraction
          && Double.isFinite(compressor.getSpeed()) && compressor.getSpeed() <= 1.0;
    }

    /**
     * Rearms native logic only after actual clear-input permissives; valve position and shaft speed are preserved.
     *
     * @return false while a demand persists, true after successful bookkeeping reset
     */
    public boolean resetProtection() {
      if (!resetPermitted()) {
        return false;
      }
      for (ClosedLoopSafetyFunction function : safetyFunctions) {
        function.reset();
        function.activate();
      }
      return true;
    }

    /**
     * Evaluates the declared steady design flow using the same actual equipment objects.
     *
     * @param id calculation identity
     */
    private void steady(UUID id) {
      if (dynamicsInitialized) {
        throw new IllegalStateException("Create or copy a pristine train for a new steady initialization");
      }
      for (int i = 0; i < 3; i++) {
        feed.run(id);
        inletValve.run(id);
        suctionMixer.run(id);
        suctionScrubber.run(id);
        compressor.run(id);
        dischargeCheckValve.run();
        aftercooler.run(id);
        dischargeScrubber.run(id);
        dischargeTee.run(id);
        exportValve.run(id);
        antiSurgeValve.run(id);
        suctionDrain.run(id);
        dischargeDrain.run(id);
      }
    }

    /** Initializes both finite inventories without reinitializing them on later calls. */
    public void initializeDynamics() {
      if (dynamicsInitialized) {
        return;
      }
      for (Separator vessel : Arrays.asList(suctionScrubber, dischargeScrubber)) {
        vessel.setLiquidLevel(
            vessel == suctionScrubber ? design.suctionInitialLevelFraction : design.dischargeInitialLevelFraction);
        vessel.setCalculateSteadyState(false);
        vessel.initializeTransientCalculation();
        vessel.setConservativeTransientEnergyIntegrationEnabled(true);
        vessel.setTransientEnergyBalanceTrackingEnabled(true);
      }
      for (ThrottlingValve valve : Arrays.asList(inletValve, exportValve, antiSurgeValve, suctionDrain, dischargeDrain,
          dischargeCheckValve)) {
        valve.setCalculateSteadyState(false);
        valve.setPhysicalStepIdempotencyEnabled(true);
      }
      aftercooler.setCalculateSteadyState(false);
      dynamicsInitialized = true;
    }

    /**
     * Solves actual compressor map pressure against the current finite discharge boundary.
     *
     * @param id physical-step identity shared by algebraic evaluations
     */
    private void solveCompressor(UUID id) {
      SystemInterface gas = suctionScrubber.getGasOutStream().getFluid();
      // A previous seated NRV may leave zero extensive throughput. Recover its valid intensive
      // composition before density/map queries; the converged root publishes the actual withdrawal.
      gas.setTotalNumberOfMoles(1.0);
      gas.initProperties();
      double ratio = compressor.getSpeed() / 10000.0;
      double density = gas.getDensity("kg/m3");
      double low = referenceFlowM3PerHour * ratio * 0.01 / 3600.0 * density;
      double high = referenceFlowM3PerHour * ratio * 1.42 / 3600.0 * density;
      double target = dischargeScrubber.getPressure();
      if (compressor.getSpeed() < 100.0) {
        publishNoForwardFlow(id);
        return;
      }
      double lowResidual = compressorDemandResidual(low, id);
      double highResidual = compressorDemandResidual(high, id);
      if (lowResidual < 0.0) {
        publishNoForwardFlow(id);
        return;
      }
      if (highResidual > 0.0) {
        throw new IllegalStateException("Compressor flow lies above the declared synthetic map domain");
      }
      boolean converged = false;
      double trial = Math.max(low, Math.min(high, lastCompressorMassFlow));
      if (!(trial > low && trial < high)) {
        trial = (low + high) * 0.5;
      }
      for (int iteration = 0; iteration < 24; iteration++) {
        double residual = compressorDemandResidual(trial, id);
        lastRootIterations = iteration + 1;
        lastCompressorMassFlow = trial;
        compressorFlowResidualKgPerSecond = residual;
        if (Math.abs(residual) <= 1.0e-8) {
          converged = true;
          break;
        }
        if (residual > 0.0) {
          low = trial;
          lowResidual = residual;
        } else {
          high = trial;
          highResidual = residual;
        }
        double increment = Math.max(1.0e-7, Math.abs(trial) * 1.0e-5);
        double nearby = Math.min(high, trial + increment);
        if (nearby == trial) {
          nearby = Math.max(low, trial - increment);
        }
        double slope = (compressorDemandResidual(nearby, id) - residual) / (nearby - trial);
        double next = trial - residual / slope;
        if (!Double.isFinite(next) || next <= low || next >= high) {
          next = (low + high) * 0.5;
        }
        trial = next;
      }
      if (!converged) {
        throw new IllegalStateException("Native compressor/NRV root failed at t=" + process.getTime() + " s: residual="
            + compressorFlowResidualKgPerSecond + " kg/s, receiver=" + target + " bara, speed=" + compressor.getSpeed()
            + " rpm");
      }
      forwardCompressorFlow = true;
      dischargeCheckValve.runTransient(0.0, id);
      aftercooler.run(id);
    }

    /**
     * Performs one actual native chart evaluation at an intensive-state-preserving flow scale.
     *
     * @param massFlowKgPerSecond candidate actual compressor mass flow
     * @param id physical-step identity
     * @return native NRV mass capacity minus candidate flow in kg/s
     */
    private double compressorDemandResidual(double massFlowKgPerSecond, UUID id) {
      scaleMolar(compressor.getInletStream(), massFlowKgPerSecond);
      compressor.run(id);
      dischargeCheckValve.setOutletPressure(dischargeScrubber.getPressure(), "bara");
      compressor.getOutletStream().getFluid().initProperties();
      return dischargeCheckValve.calculateMolarFlow() * compressor.getOutletStream().getFluid().getMolarMass()
          - massFlowKgPerSecond;
    }

    /**
     * Publishes a physical no-forward-flow boundary when the native map cannot overcome receiver pressure.
     *
     * @param id physical-step identity
     */
    private void publishNoForwardFlow(UUID id) {
      forwardCompressorFlow = false;
      lastCompressorMassFlow = 0.0;
      compressorFlowResidualKgPerSecond = Double.NaN;
      scaleMolar(compressor.getInletStream(), 0.0);
      compressor.run(id);
      dischargeCheckValve.setOutletPressure(dischargeScrubber.getPressure(), "bara");
      dischargeCheckValve.runTransient(0.0, id);
      aftercooler.run(id);
    }

    /**
     * Advances one explicitly ordered native fixed step and retains the consumed boundary before inventory changes.
     *
     * @param dt physical timestep in seconds
     * @param id unique physical-step identity
     */
    private void advance(double dt, UUID id) {
      if (!Double.isFinite(dt) || dt <= 0.0 || id == null) {
        throw new IllegalArgumentException("A finite positive dt and physical UUID are required");
      }
      if (id.equals(lastStepId)) {
        return;
      }
      initializeDynamics();
      process.setTimeStep(dt);
      process.increaseTime(dt);
      if (process.getEventScheduler() != null) {
        process.getEventScheduler().fireDueEvents(process.getTime());
      }
      for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
        if (measurement instanceof TransientMeasurementDevice
            && (!(measurement instanceof CompressorSurgeMarginMeasurement) || forwardCompressorFlow)) {
          ((TransientMeasurementDevice) measurement).sampleTransient(dt, id);
        }
      }
      for (ClosedLoopSafetyFunction function : safetyFunctions) {
        function.execute(dt);
      }
      if (compressor.getOperatingState() == CompressorState.SHUTDOWN) {
        compressor.updateDynamicState(dt);
      } else if (compressor.getOperatingState() == CompressorState.RUNNING && inletValve.isEnergized()) {
        compressor.runController(dt, id);
      }
      feed.run(id);
      inletValve.getOutletStream().setPressure(suctionScrubber.getPressure(), "bara");
      inletValve.runTransient(dt, id);
      antiSurgeValve.runTransient(dt, id);
      exportValve.runTransient(dt, id);
      suctionDrain.runTransient(dt, id);
      dischargeDrain.runTransient(dt, id);
      // Vessel material integration consumes its own source terminals. Publish the actual
      // downstream drain withdrawals there, including exact zero for a gas-only vessel.
      suctionScrubber.getLiquidOutStream().getFluid()
          .setTotalNumberOfMoles(suctionDrain.getOutletStream().getFluid().getTotalNumberOfMoles());
      dischargeScrubber.getLiquidOutStream().getFluid()
          .setTotalNumberOfMoles(dischargeDrain.getOutletStream().getFluid().getTotalNumberOfMoles());
      dischargeTee.getInletStream().getFluid().initProperties();
      for (int i = 0; i < 2; i++) {
        StreamInterface branch = dischargeTee.getSplitStream(i);
        branch.setFluid(dischargeScrubber.getGasOutStream().getFluid().clone());
        branch.getFluid().setTotalNumberOfMoles(1.0);
      }
      antiSurgeValve.getOutletStream().setPressure(suctionScrubber.getPressure(), "bara");
      antiSurgeValve.runTransient(0.0, id);
      exportValve.runTransient(0.0, id);
      double outletMoles = exportValve.getOutletStream().getFluid().getTotalNumberOfMoles()
          + antiSurgeValve.getOutletStream().getFluid().getTotalNumberOfMoles();
      dischargeScrubber.getGasOutStream().getFluid().setTotalNumberOfMoles(outletMoles);
      solveCompressor(id);
      aftercooler.runTransient(dt, id);
      suctionMixer.run(id);
      Map<String, Object> boundary = new LinkedHashMap<String, Object>();
      boolean fluidWorkApplicable = forwardCompressorFlow && lastCompressorMassFlow > 0.0;
      boundary.put("compressorNativePowerDiagnosticW", compressor.getPower());
      boundary.put("compressorNativeInletEnthalpyReferenceW", compressor.inletEnthalpy);
      boundary.put("compressorNativeDutyReferenceW", compressor.dH);
      boundary.put("compressorPowerConsumedW", fluidWorkApplicable ? compressor.getPower() : 0.0);
      boundary.put("compressorFluidWorkApplicable", fluidWorkApplicable);
      boundary.put("compressorFluidWorkReason", fluidWorkApplicable ? "NATIVE_FORWARD_GAS_ENTHALPY_RISE"
          : "ZERO_FORWARD_MASS_NO_FLUID_WORK_MODEL; ROTOR_CHURNING_AND_REVERSE_FLOW_UNMODELLED");
      boundary.put("compressorInlet", stream(compressor.getInletStream()));
      boundary.put("compressorOutlet", stream(compressor.getOutletStream()));
      boundary.put("coolerOutlet", stream(aftercooler.getOutletStream()));
      boundary.put("suctionBefore", inventory(suctionScrubber));
      boundary.put("dischargeBefore", inventory(dischargeScrubber));
      boundary.put("suctionInlet", stream(suctionMixer.getOutletStream()));
      boundary.put("suctionGasOutlet", stream(suctionScrubber.getGasOutStream()));
      boundary.put("suctionLiquidOutlet", stream(suctionDrain.getOutletStream()));
      boundary.put("dischargeInlet", stream(aftercooler.getOutletStream()));
      boundary.put("dischargeGasOutlet", stream(dischargeScrubber.getGasOutStream()));
      boundary.put("dischargeLiquidOutlet", stream(dischargeDrain.getOutletStream()));
      boundary.put("feed", stream(inletValve.getOutletStream()));
      boundary.put("recycle", stream(antiSurgeValve.getOutletStream()));
      boundary.put("export", stream(exportValve.getOutletStream()));
      suctionScrubber.runTransient(dt, id);
      dischargeScrubber.runTransient(dt, id);
      consumedLedger(boundary, "suction", suctionScrubber.getLastTransientEnergyBalanceReport());
      consumedLedger(boundary, "discharge", dischargeScrubber.getLastTransientEnergyBalanceReport());
      boundary.put("suctionAfter", inventory(suctionScrubber));
      boundary.put("dischargeAfter", inventory(dischargeScrubber));
      lastBoundary = boundary;
      solveCompressor(id);
      lastStepId = id;
      process.setCalculationIdentifier(id);
      samples.add(Collections.unmodifiableMap(snapshot()));
    }

    /**
     * Returns a detached copy of the governed design assumptions.
     *
     * @return copied design
     */
    public Design getDesign() {
      return copyDesign(design);
    }

    /** @return actual native process, preserving the equipment and instrumentation identity graph */
    public ProcessSystem getProcess() {
      return process;
    }

    /** @return actual source stream */
    public Stream getFeed() {
      return feed;
    }

    /** @return actual feed isolation valve */
    public ESDValve getInletValve() {
      return inletValve;
    }

    /** @return actual suction mixer with actual cold-recycle inlet */
    public Mixer getSuctionMixer() {
      return suctionMixer;
    }

    /** @return finite suction scrubber */
    public Separator getSuctionScrubber() {
      return suctionScrubber;
    }

    /** @return actual chart compressor */
    public Compressor getCompressor() {
      return compressor;
    }

    /** @return actual algebraic aftercooler */
    public Cooler getAftercooler() {
      return aftercooler;
    }

    /** @return actual native non-return valve */
    public CheckValve getDischargeCheckValve() {
      return dischargeCheckValve;
    }

    /** @return finite discharge separator and gas receiver */
    public Separator getDischargeScrubber() {
      return dischargeScrubber;
    }

    /** @return actual native two-outlet tee */
    public Splitter getDischargeTee() {
      return dischargeTee;
    }

    /** @return actual export valve */
    public ESDValve getExportValve() {
      return exportValve;
    }

    /** @return actual fail-open recycle valve */
    public ESDValve getAntiSurgeValve() {
      return antiSurgeValve;
    }

    /** @return actual suction drain valve */
    public ThrottlingValve getSuctionDrain() {
      return suctionDrain;
    }

    /** @return actual discharge drain valve */
    public ThrottlingValve getDischargeDrain() {
      return dischargeDrain;
    }

    /** @return actual anti-surge controller */
    public AntiSurgeController getAntiSurgeController() {
      return antiSurgeController;
    }

    /**
     * Returns the actual BPCS temperature controller manipulating the embedded native cooling utility opening.
     *
     * @return actual native controller
     */
    public ControllerDeviceBaseClass getCoolerTemperatureController() {
      return coolerTemperatureController;
    }

    /**
     * Returns the actual BPCS discharge-pressure controller manipulating native compressor speed.
     *
     * @return actual native controller
     */
    public ControllerDeviceBaseClass getCapacityPressureController() {
      return capacityPressureController;
    }

    /** @return actual held calculated-margin channel consuming the actual inlet FT */
    public CompressorSurgeMarginMeasurement getSurgeMeasurement() {
      return surgeMeasurement;
    }

    /** @return actual registered measurement objects */
    public List<MeasurementDeviceInterface> getMeasurements() {
      return process.getMeasurementDevices();
    }

    /** @return actual registered controller objects */
    public List<ControllerDeviceInterface> getControllers() {
      return process.getControllerDevices();
    }

    /** @return actual native protection functions */
    public List<ClosedLoopSafetyFunction> getSafetyFunctions() {
      return Collections.unmodifiableList(safetyFunctions);
    }

    /** @return actual native compressor shutdown actions */
    public List<TripCompressorAction> getDriverActions() {
      return Collections.unmodifiableList(driverActions);
    }

    /** @return consumed flux and inventory evidence for the last physical step */
    public Map<String, Object> getLastBoundary() {
      return Collections.unmodifiableMap(lastBoundary);
    }

    /**
     * Checks physical-flow, main-map and surge-reference domains before protection-margin credit.
     *
     * @return true only inside both declared synthetic domains with positive forward flow
     */
    private boolean gasCvApplicable() {
      for (ThrottlingValve valve : Arrays.asList(inletValve, dischargeCheckValve, antiSurgeValve, exportValve)) {
        if (!singleGas(valve.getInletStream().getFluid())) {
          return false;
        }
      }
      return true;
    }

    /**
     * Checks physical-flow, main-map and surge-reference domains before protection-margin credit.
     *
     * @return true only inside both declared synthetic domains with positive forward flow
     */
    private boolean physicalMarginQualified() {
      if (!forwardCompressorFlow || compressor.isChartExtrapolated()
          || !compressor.getCompressorChart().getSurgeCurve().isActive()) {
        return false;
      }
      double[] heads = ((SafeSplineSurgeCurve) compressor.getCompressorChart().getSurgeCurve()).getSortedHead();
      double actual = compressor.getPolytropicFluidHead();
      return heads.length >= 2 && Double.isFinite(actual) && actual >= heads[0] && actual <= heads[heads.length - 1];
    }

    /**
     * Creates an identity-preserving native deep copy, including all source/controller references.
     *
     * @return independently mutable train
     */
    public Train copy() {
      return ((TrainProcess) process.copy()).owner;
    }

    /**
     * Captures actual current values without advancing any equipment or controller.
     *
     * @return scalar and source-backed configuration evidence
     */
    public Map<String, Object> snapshot() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("timeSeconds", process.getTime());
      result.put("physicalStepId", lastStepId == null ? null : lastStepId.toString());
      result.put("suctionPressureBara", suctionScrubber.getPressure());
      result.put("dischargePressureBara", dischargeScrubber.getPressure());
      result.put("suctionLevelFraction", suctionScrubber.getLiquidLevel());
      result.put("dischargeLevelFraction", dischargeScrubber.getLiquidLevel());
      result.put("coolerOutletTemperatureK", aftercooler.getOutletStream().getTemperature("K"));
      result.put("coolerUtilityOpeningPercent", aftercooler.getCoolingValveOpening());
      result.put("coolerUtilityAvailable", aftercooler.isDynamicCoolingUtilityAvailable());
      result.put("speedRpm", compressor.getSpeed());
      result.put("capacitySpeedCommandRpm", capacityPressureController.getResponse());
      result.put("compressorState", compressor.getOperatingState().name());
      result.put("forwardCompressorFlow", forwardCompressorFlow);
      result.put("marginApplicable",
          forwardCompressorFlow && compressor.getOperatingState() == CompressorState.RUNNING);
      result.put("rootIterations", lastRootIterations);
      result.put("compressorMassFlowKgS", lastCompressorMassFlow);
      result.put("compressorFlowM3Hr", forwardCompressorFlow ? compressor.getInletStream().getFlowRate("m3/hr") : 0.0);
      result.put("compressorHeadKjKg", compressor.getPolytropicFluidHead());
      result.put("compressorNativePowerW", compressor.getPower());
      result.put("compressorFlowClosureResidualKgS",
          Double.isFinite(compressorFlowResidualKgPerSecond) ? compressorFlowResidualKgPerSecond : null);
      result.put("rawMargin", forwardCompressorFlow ? compressor.getDistanceToSurge() : null);
      result.put("validatedMargin", physicalMarginQualified() ? compressor.getValidatedDistanceToSurge() : null);
      result.put("marginValidityReason", physicalMarginQualified() ? "INSIDE_DECLARED_SYNTHETIC_DOMAINS"
          : "NO_FORWARD_FLOW_OR_OUTSIDE_DECLARED_MAP_DOMAIN");
      result.put("chartExtrapolated", forwardCompressorFlow ? compressor.isChartExtrapolated() : null);
      result.put("inletValveOpeningPercent", inletValve.getPercentValveOpening());
      result.put("exportValveOpeningPercent", exportValve.getPercentValveOpening());
      result.put("suctionDrainOpeningPercent", suctionDrain.getPercentValveOpening());
      result.put("dischargeDrainOpeningPercent", dischargeDrain.getPercentValveOpening());
      result.put("asvOpeningPercent", antiSurgeValve.getPercentValveOpening());
      result.put("asvCommandPercent", antiSurgeValve.getTargetPercentValveOpening());
      result.put("asvCvUs", antiSurgeValve.getCv());
      result.put("recycleMassFlowKgS", antiSurgeValve.getOutletStream().getFlowRate("kg/sec"));
      result.put("exportMassFlowKgS", exportValve.getOutletStream().getFlowRate("kg/sec"));
      result.put("checkValveOpen", dischargeCheckValve.isOpen());
      result.put("boundary", lastBoundary);
      result.put("gasCvApplicable", gasCvApplicable());
      result.put("gasCvApplicabilityReason",
          gasCvApplicable() ? "SINGLE_GAS_NATIVE_INLETS" : "HOLD_MIXED_PHASE_GAS_CV");
      Map<String, Object> valveInlets = new LinkedHashMap<String, Object>();
      for (ThrottlingValve valve : Arrays.asList(inletValve, dischargeCheckValve, antiSurgeValve, exportValve,
          suctionDrain, dischargeDrain)) {
        valveInlets.put(valve.getName(), stream(valve.getInletStream()));
      }
      result.put("valveInlets", valveInlets);
      result.put("surgeReferenceAtSample", surgeMeasurement.getLastReferenceEvidence());
      Map<String, Object> measurements = new LinkedHashMap<String, Object>();
      for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
        if (measurement instanceof CompressorSurgeMarginMeasurement && !forwardCompressorFlow) {
          measurements.put(measurement.getName(), null);
        } else if (measurement instanceof TransientMeasurementDevice && lastStepId != null
            && ((TransientMeasurementDevice) measurement).hasTransientSample(lastStepId)) {
          measurements.put(measurement.getName(),
              ((TransientMeasurementDevice) measurement).getTransientSample(measurement.getUnit()));
        } else {
          measurements.put(measurement.getName(), measurement.getMeasuredValue());
        }
      }
      result.put("measurements", measurements);
      Map<String, Object> functions = new LinkedHashMap<String, Object>();
      for (ClosedLoopSafetyFunction function : safetyFunctions) {
        functions.put(function.getName(), function.getState().name());
      }
      result.put("safetyFunctions", functions);
      return jsonSafe(result);
    }
  }

  /**
   * Native process subtype with a deliberately fixed execution order for this cyclic finite-volume template.
   *
   * <p>
   * All actual physical units remain registered for ordinary engineering lookup and P&amp;ID projection. Generic
   * adaptive/parallel execution and addition of unrelated equipment are outside this template contract.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class TrainProcess extends ProcessSystem {
    private static final long serialVersionUID = 1L;
    private final Train owner;

    /**
     * Binds this process to its actual owned train graph.
     *
     * @param owner owned physical train
     */
    private TrainProcess(Train owner) {
      this.owner = owner;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void run(UUID id) {
      owner.steady(id);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void runTransient(double dt, UUID id) {
      owner.advance(dt, id);
    }
  }

  /**
   * Copies design scalars so external setter calls cannot silently modify an existing train.
   *
   * @param source input design
   * @return copied design
   */
  private static Design copyDesign(Design source) {
    if (source == null) {
      throw new IllegalArgumentException("Design is required");
    }
    Design result = new Design();
    result.composition.clear();
    result.composition.putAll(source.composition);
    result.suctionDiameterM = source.suctionDiameterM;
    result.suctionLengthM = source.suctionLengthM;
    result.dischargeDiameterM = source.dischargeDiameterM;
    result.dischargeLengthM = source.dischargeLengthM;
    result.suctionInitialLevelFraction = source.suctionInitialLevelFraction;
    result.dischargeInitialLevelFraction = source.dischargeInitialLevelFraction;
    result.feedFlowKgPerSecond = source.feedFlowKgPerSecond;
    result.feedPressureBara = source.feedPressureBara;
    result.suctionPressureBara = source.suctionPressureBara;
    result.dischargePressureBara = source.dischargePressureBara;
    result.exportPressureBara = source.exportPressureBara;
    result.feedTemperatureK = source.feedTemperatureK;
    result.coolerTemperatureK = source.coolerTemperatureK;
    result.coolingMediumTemperatureK = source.coolingMediumTemperatureK;
    result.coolerActuatorTimeConstantSeconds = source.coolerActuatorTimeConstantSeconds;
    result.coolerThermalTimeConstantSeconds = source.coolerThermalTimeConstantSeconds;
    result.antiSurgeCv = source.antiSurgeCv;
    result.antiSurgeOpeningSeconds = source.antiSurgeOpeningSeconds;
    result.antiSurgeClosingSeconds = source.antiSurgeClosingSeconds;
    result.isolationStrokeSeconds = source.isolationStrokeSeconds;
    result.surgeMarginSetPoint = source.surgeMarginSetPoint;
    result.antiSurgeProportionalGain = source.antiSurgeProportionalGain;
    result.antiSurgeIntegralTimeSeconds = source.antiSurgeIntegralTimeSeconds;
    result.capacityPressureGainRpmPerBar = source.capacityPressureGainRpmPerBar;
    result.capacityPressureIntegralTimeSeconds = source.capacityPressureIntegralTimeSeconds;
    result.driverRampRpmPerSecond = source.driverRampRpmPerSecond;
    result.pressureTripBara = source.pressureTripBara;
    result.temperatureTripK = source.temperatureTripK;
    result.levelTripFraction = source.levelTripFraction;
    return result;
  }

  /**
   * Changes throughput while preserving a valid flashed intensive phase state.
   *
   * @param stream actual stream
   * @param massKgPerSecond actual physical mass rate in kg/s
   */
  private static void scaleMolar(StreamInterface stream, double massKgPerSecond) {
    stream.getFluid().setTotalNumberOfMoles(massKgPerSecond / stream.getFluid().getMolarMass());
  }

  /**
   * Captures a native consumed stream boundary in consistent SI units.
   *
   * @param source actual consumed source stream
   * @return raw boundary values
   */
  private static Map<String, Object> stream(StreamInterface source) {
    SystemInterface fluid = source.getFluid();
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("name", source.getName());
    result.put("pressureBara", source.getPressure("bara"));
    result.put("temperatureK", source.getTemperature("K"));
    result.put("massKgS", source.getFlowRate("kg/sec"));
    result.put("molesPerSecond", fluid.getTotalNumberOfMoles());
    result.put("rawCachedEnthalpyW", fluid.getEnthalpy());
    double initializedEnthalpy = 0.0;
    if (fluid.getTotalNumberOfMoles() > 0.0) {
      // Flow rescaling preserves intensive composition but does not refresh cached extensive H.
      // Match the native compressor initialization on a detached workspace, without changing
      // any consumed port, inventory, UUID, controller state or phase-equilibrium specification.
      SystemInterface initialized = fluid.clone();
      initialized.init(2);
      initializedEnthalpy = initialized.getEnthalpy();
    }
    result.put("enthalpyW", initializedEnthalpy);
    result.put("enthalpySource", "DETACHED_NATIVE_PROPERTY_INIT2; ZERO_MASS_HAS_ZERO_ENTHALPY_FLUX");
    result.put("phaseCount", fluid.getNumberOfPhases());
    List<Map<String, Object>> phases = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      Map<String, Object> phase = new LinkedHashMap<String, Object>();
      phase.put("type", fluid.getPhase(i).getType().name());
      phase.put("moleFraction", fluid.getPhase(i).getBeta());
      phases.add(phase);
    }
    result.put("phases", phases);
    result.put("singleGas", singleGas(fluid));
    Map<String, Double> components = new LinkedHashMap<String, Double>();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      components.put(fluid.getComponent(i).getComponentName(), fluid.getComponent(i).getNumberOfmoles());
    }
    result.put("componentMolesPerSecond", components);
    return result;
  }

  /**
   * Checks the native phase applicability of positive-flow gas capacity calculations.
   *
   * @param fluid native inlet fluid
   * @return true for zero withdrawal or one actual gas phase
   */
  private static boolean singleGas(SystemInterface fluid) {
    return fluid.getTotalNumberOfMoles() <= 0.0
        || fluid.getNumberOfPhases() == 1 && "GAS".equals(fluid.getPhase(0).getType().name());
  }

  /**
   * Reconciles boundary enthalpy with the exact initialized rates consumed by the finite native update.
   *
   * @param boundary captured pre-consumption stream metadata
   * @param prefix vessel boundary prefix
   * @param report native accepted-step energy ledger
   */
  @SuppressWarnings("unchecked")
  private static void consumedLedger(Map<String, Object> boundary, String prefix,
      SeparatorTransientEnergyBalanceReport report) {
    String[] suffix = {"Inlet", "GasOutlet", "LiquidOutlet"};
    double[] enthalpy = {report.getInletEnthalpyRateW(), report.getGasOutletEnthalpyRateW(),
        report.getLiquidOutletEnthalpyRateW()};
    double[] moles = {report.getInletMolarRateMolPerSecond(), report.getGasOutletMolarRateMolPerSecond(),
        report.getLiquidOutletMolarRateMolPerSecond()};
    for (int i = 0; i < suffix.length; i++) {
      Map<String, Object> flux = (Map<String, Object>) boundary.get(prefix + suffix[i]);
      flux.put("initializedBoundaryEnthalpyW", flux.get("enthalpyW"));
      flux.put("enthalpyW", enthalpy[i]);
      flux.put("consumedMolesPerSecond", moles[i]);
      flux.put("enthalpySource", "NATIVE_SEPARATOR_CONSUMED_LEDGER");
    }
    Map<String, Object> ledger = new LinkedHashMap<String, Object>();
    ledger.put("timeStepSeconds", report.getTimeStepSeconds());
    ledger.put("acceptedStepCount", report.getAcceptedStepCount());
    ledger.put("attemptedStepCount", report.getAttemptedStepCount());
    ledger.put("complete", report.isComplete());
    ledger.put("coverageIssue", report.getCoverageIssue().name());
    ledger.put("liquidOutletTermApplied", report.isLiquidOutletTermApplied());
    ledger.put("componentRemovalLimited", report.isComponentRemovalLimited());
    ledger.put("heatInputW", report.getHeatInputRateW());
    ledger.put("initialEnergyJ", report.getInitialEnergyJ());
    ledger.put("netIntegratedEnergyJ", report.getNetIntegratedEnergyJ());
    ledger.put("cumulativeDebtJ", report.getCumulativeDebtJ());
    ledger.put("localVuDefectJ", report.getLocalVuDefectJ());
    ledger.put("nonVuEnergyDifferenceJ", report.getNonVuEnergyDifferenceJ());
    ledger.put("relativeCumulativeResidual", report.getRelativeCumulativeResidual());
    boundary.put(prefix + "EnergyLedger", ledger);
  }

  /**
   * Copies an evidence map into strict JSON values while retaining every non-finite numeric path explicitly.
   *
   * @param input native evidence map
   * @return safe detached values with diagnostic paths; no invalid value is replaced by zero
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> jsonSafe(Map<String, Object> input) {
    List<Map<String, String>> invalid = new ArrayList<Map<String, String>>();
    Map<String, Object> result = (Map<String, Object>) jsonValue(input, "$", invalid);
    if (!invalid.isEmpty()) {
      result.put("nonFiniteValues", invalid);
    }
    return result;
  }

  /**
   * Recursively retains JSON structure and explicit invalid-number provenance.
   *
   * @param value native value
   * @param path evidence path
   * @param invalid accumulated invalid values
   * @return JSON-safe value
   */
  private static Object jsonValue(Object value, String path, List<Map<String, String>> invalid) {
    if (value instanceof Number && !Double.isFinite(((Number) value).doubleValue())) {
      Map<String, String> entry = new LinkedHashMap<String, String>();
      entry.put("path", path);
      entry.put("value", value.toString());
      invalid.add(entry);
      return null;
    }
    if (value instanceof Map<?, ?>) {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        String key = String.valueOf(entry.getKey());
        result.put(key, jsonValue(entry.getValue(), path + "." + key, invalid));
      }
      return result;
    }
    if (value instanceof Iterable<?>) {
      List<Object> result = new ArrayList<Object>();
      int index = 0;
      for (Object element : (Iterable<?>) value) {
        result.add(jsonValue(element, path + "[" + index++ + "]", invalid));
      }
      return result;
    }
    return value;
  }

  /**
   * Captures a finite native separator inventory without treating flow as holdup.
   *
   * @param vessel actual separator
   * @return inventory and energy values
   */
  private static Map<String, Object> inventory(Separator vessel) {
    SystemInterface fluid = vessel.getThermoSystem();
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("massKg", fluid.getTotalNumberOfMoles() * fluid.getMolarMass());
    result.put("internalEnergyJ", fluid.getInternalEnergy());
    result.put("pressureBara", fluid.getPressure());
    result.put("temperatureK", fluid.getTemperature());
    result.put("levelFraction", vessel.getLiquidLevel());
    Map<String, Double> components = new LinkedHashMap<String, Double>();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      components.put(fluid.getComponent(i).getComponentName(), fluid.getComponent(i).getNumberOfmoles());
    }
    result.put("componentMoles", components);
    return result;
  }
}
