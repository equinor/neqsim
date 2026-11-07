package neqsim.process.safety.scenario;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.SensorFaultType;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.TransientMeasurementDevice;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Public synthetic two-inventory V101 safety-study fixture, executed by native strict scenario tests.
 *
 * <p>
 * The HIPPS candidate is a native two-out-of-three closed-loop function driving a native finite-stroke ESDValve. It
 * does not claim the immediate-command legacy HIPPS classes represent finite travel, installed independence, SIL,
 * standards compliance or construction approval. Native failed protection results are retained.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AdvancedV101SafetyStudy {
  private static final Logger logger = LogManager.getLogger(AdvancedV101SafetyStudy.class);
  private static final String[] COMPONENTS = {"methane", "ethane", "propane", "n-butane", "i-butane", "n-pentane",
      "n-hexane", "CO2", "nitrogen"};
  private static final double[] FRACTIONS = {.780, .080, .050, .025, .015, .025, .015, .008, .002};
  private static final String[] HIPPS_SENSORS = {"20-PT-101A", "20-PT-101B", "20-PT-101C"};

  /** Prevents construction of the fixture's static API. */
  private AdvancedV101SafetyStudy() {
  }

  /**
   * Executes selected actual native profiles and exports their response and separate numerical evidence.
   *
   * @param args output directory, optional timestep seconds, and optional profile names; the default runs all profiles
   * @throws IOException if actual result export fails
   */
  public static void main(String[] args) throws IOException {
    Path directory = Paths.get(args.length > 0 ? args[0] : "target/advanced-safety-study");
    double dt = args.length > 1 ? Double.parseDouble(args[1]) : 0.1;
    List<String> selected = args.length > 2 ? Arrays.asList(Arrays.copyOfRange(args, 2, args.length)) : modes();
    Map<String, Double> settings = defaultSettings();
    ProcessSystem source = buildSource(settings);
    for (String mode : selected) {
      CaseBundle bundle = createCase(source, settings, mode, dt);
      DynamicSafetyScenarioResult result = bundle.runStrict();
      bundle.export(directory);
      logger.info("{} native response passed={}; numerical and qualification gates remain separate", mode,
          Boolean.valueOf(result.isPassed()));
    }
  }

  /** @return independently mutable explicit synthetic design settings, with units encoded in keys */
  public static Map<String, Double> defaultSettings() {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    result.put("hippsTripBara", Double.valueOf(41.0));
    result.put("esdV101TripBara", Double.valueOf(41.8));
    result.put("esdV100TripBara", Double.valueOf(44.5));
    result.put("sensorQualificationSeconds", Double.valueOf(0.1));
    result.put("logicDelaySeconds", Double.valueOf(0.1));
    result.put("hippsStrokeSeconds", Double.valueOf(0.5));
    result.put("esdStrokeSeconds", Double.valueOf(0.5));
    result.put("pressureKpPercentPerBar", Double.valueOf(16.0));
    result.put("pressureTiSeconds", Double.valueOf(20.0));
    result.put("levelKpPercentPerPercent", Double.valueOf(4.0));
    result.put("levelTiSeconds", Double.valueOf(60.0));
    result.put("pressureLimitV101Bara", Double.valueOf(43.0));
    result.put("pressureLimitV100Bara", Double.valueOf(46.0));
    result.put("closureDeadlineSeconds", Double.valueOf(3.0));
    result.put("relativeEnergyLimit", Double.valueOf(1.0e-5));
    return result;
  }

  /**
   * Constructs the actual canonical physical process and registered instrumentation before isolated case setup.
   *
   * @param settings explicit design settings
   * @return independently initialized native source process
   */
  public static ProcessSystem buildSource(Map<String, Double> settings) {
    SystemInterface fluid = new SystemSrkEos(308.15, 45.0);
    for (int i = 0; i < COMPONENTS.length; i++) {
      fluid.addComponent(COMPONENTS[i], FRACTIONS[i]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    ESDValve esd = new ESDValve("20-ESDV-001", feed);
    esd.setOutletPressure(44.0, "bara");
    esd.getOutletStream().setName("10-V100-FEED");
    Separator v100 = new Separator("10-V-100", esd.getOutletStream());
    geometry(v100, 0.6, 1.5, 0.3);
    v100.getGasOutStream().setName("10-V100-GAS");
    v100.getLiquidOutStream().setName("10-V100-LIQUID");
    ESDValve hipps = new ESDValve("20-HIPPS-XV-001", v100.getGasOutStream());
    hipps.setOutletPressure(40.0, "bara");
    hipps.getOutletStream().setName("20-V101-FEED");
    Separator v101 = new Separator("20-V-101", hipps.getOutletStream());
    geometry(v101, 1.0, 3.0, 0.5);
    v101.getGasOutStream().setName("20-S-02");
    v101.getLiquidOutStream().setName("20-S-07");
    ThrottlingValve pcv = new ThrottlingValve("20-PCV-001", v101.getGasOutStream());
    pcv.setOutletPressure(35.0, "bara");
    pcv.getOutletStream().setName("20-GAS-EXPORT");
    ThrottlingValve l100 = new ThrottlingValve("10-LCV-001", v100.getLiquidOutStream());
    l100.setOutletPressure(20.0, "bara");
    l100.getOutletStream().setName("10-LIQUID-EXPORT");
    ThrottlingValve l101 = new ThrottlingValve("20-LCV-001", v101.getLiquidOutStream());
    l101.setOutletPressure(20.0, "bara");
    l101.getOutletStream().setName("20-LIQUID-EXPORT");
    ProcessSystem process = new ProcessSystem();
    process.setName("Synthetic advanced V100/V101 safety design");
    for (ProcessEquipmentInterface unit : Arrays.asList(feed, esd, v100, hipps, v101, pcv, l100, l101)) {
      process.add(unit);
    }
    process.run();
    for (ThrottlingValve valve : Arrays.asList(esd, hipps, pcv, l100, l101)) {
      valve.autoSize(1.0, 50.0);
      valve.setPercentValveOpening(50.0);
    }
    process.run();
    if (!process.solved()) {
      throw new IllegalStateException("Synthetic source steady initialization did not converge");
    }
    pressure(process, "10-PT-001", v100.getGasOutStream());
    pressure(process, "10-PT-002", v100.getGasOutStream());
    pressure(process, "20-PT-001", v101.getGasOutStream());
    pressure(process, "20-PT-002", v101.getGasOutStream());
    for (String tag : HIPPS_SENSORS) {
      pressure(process, tag, v101.getGasOutStream());
    }
    LevelTransmitter lt100 = new LevelTransmitter("10-LT-001", v100);
    lt100.setTransientSamplingEnabled(true);
    process.add(lt100);
    LevelTransmitter lt101 = new LevelTransmitter("20-LT-001", v101);
    lt101.setTransientSamplingEnabled(true);
    process.add(lt101);
    temperature(process, "10-TT-001", v100.getGasOutStream());
    temperature(process, "20-TT-001", v101.getGasOutStream());
    flow(process, "10-FT-001", esd.getOutletStream());
    flow(process, "20-FT-001", hipps.getOutletStream());
    flow(process, "20-FT-002", pcv.getOutletStream());
    flow(process, "10-FT-002", l100.getOutletStream());
    flow(process, "20-FT-003", l101.getOutletStream());
    process.add(new PushButton("20-HS-001"));
    controller(process, "20-PIC-001", pcv, process.getMeasurementDevice("20-PT-001"), 40.0, "bara",
        value(settings, "pressureKpPercentPerBar"), value(settings, "pressureTiSeconds"));
    controller(process, "10-LIC-001", l100, lt100, 0.3, "", value(settings, "levelKpPercentPerPercent"),
        value(settings, "levelTiSeconds"));
    controller(process, "20-LIC-001", l101, lt101, 0.5, "", value(settings, "levelKpPercentPerPercent"),
        value(settings, "levelTiSeconds"));
    return process;
  }

  /**
   * Initializes the actual isolated copy after its steady run, then installs a conservative explicit inventory order.
   *
   * @param process actual isolated native process copy
   * @param settings explicit design settings consumed by this case
   */
  public static void configureAfterSteady(ProcessSystem process, Map<String, Double> settings) {
    Separator v100 = vessel(process, "10-V-100");
    Separator v101 = vessel(process, "20-V-101");
    for (Separator vessel : Arrays.asList(v100, v101)) {
      vessel.setCalculateSteadyState(false);
      vessel.setLiquidLevel(vessel == v100 ? 0.3 : 0.5);
      vessel.initializeTransientCalculation();
      vessel.setTransientEnergyBalanceTrackingEnabled(true);
    }
    ESDValve esd = isolation(process, "20-ESDV-001");
    ESDValve hipps = isolation(process, "20-HIPPS-XV-001");
    for (ESDValve valve : Arrays.asList(esd, hipps)) {
      valve.setCalculateSteadyState(false);
      valve.setFailSafePosition(0.0);
      valve.setActuatorAvailable(true);
      valve.energize();
      valve.setPercentValveOpening(50.0);
      valve.setPhysicalStepIdempotencyEnabled(true);
    }
    esd.setStrokeTime(value(settings, "esdStrokeSeconds"));
    hipps.setStrokeTime(value(settings, "hippsStrokeSeconds"));
    for (String tag : new String[] {"20-PCV-001", "10-LCV-001", "20-LCV-001"}) {
      ThrottlingValve valve = valve(process, tag);
      valve.setCalculateSteadyState(false);
      valve.setPercentValveOpening(50.0);
      valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
      valve.setTravelTime(3.0);
      valve.setPhysicalStepIdempotencyEnabled(true);
      valve.getController().setActive(true);
    }
    SetPoint sourcePressure = new SetPoint("V100-INLET-PRESSURE-COUPLING", esd, "pressure", v100.getGasOutStream());
    SetPoint transferPressure = new SetPoint("V101-INLET-PRESSURE-COUPLING", hipps, "pressure", v101.getGasOutStream());
    List<ProcessEquipmentInterface> order = Arrays.asList(process.getUnit("20-FEED-001"), sourcePressure, esd,
        transferPressure, hipps, v100, v101, process.getUnit("20-PCV-001"), process.getUnit("10-LCV-001"),
        process.getUnit("20-LCV-001"));
    process.getUnitOperations().clear();
    for (ProcessEquipmentInterface unit : order) {
      process.add(unit);
    }
    process.setParallelTransientEnabled(false);
  }

  /**
   * Creates native ESD, HIPPS candidate and manual functions on the exact configured copy.
   *
   * @param process actual configured process copy
   * @param settings consumed design settings
   * @param mode explicit test/fault profile, separate from the normal design settings
   * @return three actual native safety functions
   */
  public static List<ClosedLoopSafetyFunction> createFunctions(ProcessSystem process, Map<String, Double> settings,
      String mode) {
    ESDLogic esd = new ESDLogic("V100/V101 automatic ESD final element");
    esd.addAction(new TripValveAction(isolation(process, "20-ESDV-001")), 0.0);
    ClosedLoopSafetyFunction.Builder esdFunction = ClosedLoopSafetyFunction
        .builder("SIF-ESD-001", "SIF-ESD-001", process, VotingPattern.ONE_OUT_OF_TWO, esd)
        .logicSolverDelaySeconds(value(settings, "logicDelaySeconds"));
    esdFunction.addChannel(SafetyFunctionChannel
        .highTripFromMeasurement(process.getMeasurementDevice("10-PT-002"), "bara", value(settings, "esdV100TripBara"))
        .responseDelaySeconds(value(settings, "sensorQualificationSeconds")).build());
    esdFunction.addChannel(SafetyFunctionChannel
        .highTripFromMeasurement(process.getMeasurementDevice("20-PT-002"), "bara", value(settings, "esdV101TripBara"))
        .responseDelaySeconds(value(settings, "sensorQualificationSeconds")).build());
    ESDLogic hipps = new ESDLogic("HIPPS candidate finite-stroke final element");
    hipps.addAction(new TripValveAction(isolation(process, "20-HIPPS-XV-001")), 0.0);
    ClosedLoopSafetyFunction.Builder hippsFunction = ClosedLoopSafetyFunction
        .builder("SIF-HIPPS-001", "SIF-HIPPS-001", process, VotingPattern.TWO_OUT_OF_THREE, hipps)
        .logicSolverDelaySeconds(value(settings, "logicDelaySeconds"));
    for (int i = 0; i < HIPPS_SENSORS.length; i++) {
      SafetyFunctionChannel.Builder channel = SafetyFunctionChannel
          .highTripFromMeasurement(process.getMeasurementDevice(HIPPS_SENSORS[i]), "bara",
              value(settings, "hippsTripBara"))
          .responseDelaySeconds(value(settings, "sensorQualificationSeconds"));
      if ((mode.equals("ONE_BYPASSED") && i == 0) || (mode.equals("TWO_BYPASSED") && i < 2)) {
        channel.faultMode(SafetyFunctionChannel.FaultMode.BYPASSED);
      }
      hippsFunction.addChannel(channel.build());
    }
    ESDLogic manual = new ESDLogic("Manual emergency trip final elements");
    ParallelActionGroup simultaneous = new ParallelActionGroup("Trip both actual isolation valves in parallel");
    simultaneous.addAction(new TripValveAction(isolation(process, "20-ESDV-001")));
    simultaneous.addAction(new TripValveAction(isolation(process, "20-HIPPS-XV-001")));
    manual.addAction(simultaneous, 0.0);
    ClosedLoopSafetyFunction manualFunction = ClosedLoopSafetyFunction
        .builder("SIF-MANUAL-001", "SIF-MANUAL-001", process, VotingPattern.ONE_OUT_OF_ONE, manual)
        .addChannel(SafetyFunctionChannel
            .manualTripFromPushButton((PushButton) process.getMeasurementDevice("20-HS-001")).build())
        .build();
    return Arrays.asList(esdFunction.build(), hippsFunction.build(), manualFunction);
  }

  /** @return explicitly named profiles; fault cases are retained rather than optimized away */
  public static List<String> modes() {
    return Arrays.asList("NORMAL", "CONTROL_RAMP", "ONE_FALSE_HIGH", "TWO_FALSE_HIGH", "TRUE_DEMAND",
        "ONE_STUCK_NORMAL", "TWO_STUCK_NORMAL", "ONE_BYPASSED", "TWO_BYPASSED", "HIPPS_FAILED", "HIPPS_SLOW",
        "HIPPS_INHIBITED", "ALL_INHIBITED", "ESD_ONLY", "MANUAL", "PAUSE_RESUME", "ASYNC_QUALIFIED_PULSES",
        "MANUAL_RESET_RETRIP");
  }

  /**
   * Inspects actual configured native objects before strict preflight or any initiating event.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public interface ConfigurationObserver extends Serializable {
    /**
     * Inspects the same process and functions that the native runner will execute.
     *
     * @param process actual isolated configured process
     * @param functions actual native functions
     * @param settings consumed design values
     * @param mode explicit fault/test profile
     */
    void inspect(ProcessSystem process, List<ClosedLoopSafetyFunction> functions, Map<String, Double> settings,
        String mode);
  }

  /**
   * Defines an isolated native strict case against the actual canonical source graph.
   *
   * @param source actual canonical source process
   * @param settings explicit governed design values
   * @param mode declared scenario profile
   * @param dt physical timestep in seconds
   * @return case bundle retaining actual execution objects and native results
   */
  public static CaseBundle createCase(ProcessSystem source, Map<String, Double> settings, String mode, double dt) {
    return createCase(source, settings, mode, dt, Collections.<String>emptyList());
  }

  /**
   * Defines the same actual case with precomputed governed configuration evidence references.
   *
   * @param source actual canonical source process
   * @param settings explicit governed design values
   * @param mode declared scenario profile
   * @param dt physical timestep in seconds
   * @param configurationEvidenceReferences exact controlled configuration receipts
   * @return case bundle with evidence frozen into its immutable native scenario
   */
  public static CaseBundle createCase(ProcessSystem source, Map<String, Double> settings, String mode, double dt,
      List<String> configurationEvidenceReferences) {
    if (!modes().contains(mode) || !Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Unknown mode or invalid timestep");
    }
    return new CaseBundle(source, settings, mode, dt, configurationEvidenceReferences);
  }

  /**
   * One executable case and its actual native instance evidence; factories are recreated on every run.
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
    private final List<Map<String, Object>> operatorEvents = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> priorEpochs = new ArrayList<Map<String, Object>>();
    private final Map<String, Integer> lastTraceSizes = new LinkedHashMap<String, Integer>();
    private ProcessSystem configured;
    private List<ClosedLoopSafetyFunction> functions;
    private ConfigurationObserver observer;
    private DynamicSafetyScenarioResult nativeResult;
    private double clockOrigin;
    private double lastRowTime = Double.NaN;
    private boolean pauseIssued;
    private boolean resumeIssued;
    private boolean resetDeniedRecorded;
    private boolean rearmed;
    private boolean secondManualIssued;
    private double maximumWholeEnergyDebt;
    private double maximumInternalEnergyDifference;

    /**
     * Freezes the bounded native scenario, including explicit invariant-versus-reach criteria.
     *
     * @param source canonical source process
     * @param settings governed scalar design values
     * @param mode explicit test profile
     * @param dt physical timestep, seconds
     * @param configurationEvidenceReferences exact controlled configuration receipts
     */
    private CaseBundle(ProcessSystem source, Map<String, Double> settings, String mode, double dt,
        List<String> configurationEvidenceReferences) {
      this.source = source;
      this.settings = new LinkedHashMap<String, Double>(settings);
      this.mode = mode;
      this.dt = dt;
      duration = mode.equals("CONTROL_RAMP") ? 60.0 : 12.0;
      id = "ADV-V101-" + mode + "-DT-" + Double.toString(dt);
      DynamicSafetyScenario.Builder builder = DynamicSafetyScenario.builder(id, "Synthetic " + mode)
          .durationSeconds(duration).timeStepSeconds(dt).triggerTimeSeconds(1.0).eventExactTiming(true)
          .addLogic("ADV-V100-V101-CONSERVATIVE-HELD-SIF-v1", process -> initialize(process))
          .initiatingEvent("ADV-V101-" + mode + "-EVENT-v1", process -> initiate(process));
      double window = duration - 1.0;
      builder.addCriterion(DynamicScenarioCriterion
          .builder("V101-PRESSURE-ENVELOPE", "Full sampled V101 envelope", "bara", "ADV-V101-ACTUAL-PRESSURE-v1",
              process -> sample(process, "20-V-101"))
          .acceptanceRange(Double.valueOf(1.0), settings.get("pressureLimitV101Bara"))
          .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).deadlineSeconds(window).build());
      builder.addCriterion(DynamicScenarioCriterion
          .builder("V100-PRESSURE-ENVELOPE", "Full sampled V100 envelope", "bara", "ADV-V100-ACTUAL-PRESSURE-v1",
              process -> vessel(process, "10-V-100").getPressure())
          .acceptanceRange(Double.valueOf(1.0), settings.get("pressureLimitV100Bara"))
          .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).deadlineSeconds(window).build());
      for (String vesselTag : new String[] {"10-V-100", "20-V-101"}) {
        final String targetVessel = vesselTag;
        builder.addCriterion(DynamicScenarioCriterion
            .builder(targetVessel + "-NUMERICAL-ENERGY", "Separate numerical energy gate; not a response credit",
                "fraction", "ADV-NATIVE-ENERGY-MAX-v1",
                process -> vessel(process, targetVessel).getLastTransientEnergyBalanceReport()
                    .getMaximumRelativeCumulativeResidual())
            .acceptanceRange(Double.valueOf(0.0), settings.get("relativeEnergyLimit"))
            .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).deadlineSeconds(window)
            .required(false).build());
      }
      if (noTripExpected(mode)) {
        for (String valveTag : new String[] {"20-ESDV-001", "20-HIPPS-XV-001"}) {
          final String target = valveTag;
          builder.addCriterion(DynamicScenarioCriterion
              .builder(target + "-NO-UNINTENDED-TRIP", "Maintain normal isolation position", "%",
                  "ADV-ACTUAL-ISOLATION-POSITION-v1", process -> isolation(process, target).getPercentValveOpening())
              .acceptanceRange(Double.valueOf(49.9), Double.valueOf(100.0))
              .evaluationMode(DynamicScenarioCriterion.EvaluationMode.MAINTAIN_BOUNDS).deadlineSeconds(window).build());
        }
      } else {
        String target = mode.equals("ESD_ONLY") ? "20-ESDV-001" : "20-HIPPS-XV-001";
        builder.addCriterion(DynamicScenarioCriterion
            .builder("ISOLATION-CLOSURE", "Actual final-element closure", "%", "ADV-ACTUAL-ISOLATION-POSITION-v1",
                process -> isolation(process, target).getPercentValveOpening())
            .acceptanceRange(Double.valueOf(0.0), Double.valueOf(0.0))
            .deadlineSeconds(value(settings, "closureDeadlineSeconds")).build());
        if (mode.equals("MANUAL") || mode.equals("MANUAL_RESET_RETRIP")) {
          builder.addCriterion(DynamicScenarioCriterion
              .builder("MANUAL-UPSTREAM-ISOLATION-CLOSURE", "Actual second manual final element closes", "%",
                  "ADV-ACTUAL-ESDV-POSITION-v1", process -> isolation(process, "20-ESDV-001").getPercentValveOpening())
              .acceptanceRange(Double.valueOf(0.0), Double.valueOf(0.0))
              .deadlineSeconds(value(settings, "closureDeadlineSeconds")).build());
        }
      }
      for (ProcessEquipmentInterface unit : source.getUnitOperations()) {
        builder.addEquipmentTag(unit.getName());
      }
      for (MeasurementDeviceInterface measurement : source.getMeasurementDevices()) {
        builder.addEquipmentTag(measurement.getName());
      }
      for (neqsim.process.controllerdevice.ControllerDeviceInterface controller : source.getControllerDevices()) {
        builder.addEquipmentTag(controller.getName());
      }
      for (String reference : configurationEvidenceReferences) {
        builder.addEvidenceReference(reference);
      }
      scenario = builder.build();
    }

    /** @return actual native scenario definition, suitable for the governed engineering project */
    public DynamicSafetyScenario getScenario() {
      return scenario;
    }

    /** @return actual configured isolated process, available after native factory construction */
    public ProcessSystem getConfiguredProcess() {
      return configured;
    }

    /** @return unmodifiable list of actual native functions executed on this case copy */
    public List<ClosedLoopSafetyFunction> getFunctions() {
      return functions == null ? Collections.<ClosedLoopSafetyFunction>emptyList()
          : Collections.unmodifiableList(functions);
    }

    /** @return deep-independent scalar design settings consumed by this definition */
    public Map<String, Double> getSettings() {
      return new LinkedHashMap<String, Double>(settings);
    }

    /** @return explicit test profile, distinct from the normal design configuration */
    public String getMode() {
      return mode;
    }

    /** @return actual recorded physical rows; values are not fabricated from command times */
    public List<Map<String, Object>> getRows() {
      return Collections.unmodifiableList(rows);
    }

    /**
     * Installs an actual-object inspection before strict preflight; it never supplies process signals.
     *
     * @param observer actual-object inspector
     */
    public void setConfigurationObserver(ConfigurationObserver observer) {
      this.observer = observer;
    }

    /** @return actual strict native result, with simulation FAILs preserved */
    public DynamicSafetyScenarioResult runStrict() {
      nativeResult = DynamicSafetyScenarioRunner.runStrict(source, scenario);
      return nativeResult;
    }

    /**
     * Initializes a fresh case copy and actual functions; repeated project execution discards prior mutable evidence.
     *
     * @param process actual isolated native process copy
     * @return coordinating native-logic adapter
     */
    private ProcessLogic initialize(ProcessSystem process) {
      rows.clear();
      operatorEvents.clear();
      priorEpochs.clear();
      lastTraceSizes.clear();
      pauseIssued = resumeIssued = resetDeniedRecorded = rearmed = secondManualIssued = false;
      lastRowTime = Double.NaN;
      maximumWholeEnergyDebt = maximumInternalEnergyDifference = 0.0;
      configured = process;
      clockOrigin = process.getTime();
      configureAfterSteady(process, settings);
      if (mode.equals("HIPPS_FAILED")) {
        isolation(process, "20-HIPPS-XV-001").setActuatorAvailable(false);
      } else if (mode.equals("HIPPS_SLOW")) {
        isolation(process, "20-HIPPS-XV-001").setStrokeTime(6.0);
      }
      functions = createFunctions(process, settings, mode);
      if (observer != null) {
        observer.inspect(process, Collections.unmodifiableList(functions), getSettings(), mode);
      }
      return new Coordinator(this);
    }

    /**
     * Applies only the explicitly declared physical event and native signal-fault configuration.
     *
     * @param process actual isolated process copy
     */
    private void initiate(ProcessSystem process) {
      if (blockedGas(mode)) {
        valve(process, "20-PCV-001").getController().setActive(false);
        valve(process, "20-PCV-001").setPercentValveOpening(0.0);
      }
      if (mode.equals("ONE_FALSE_HIGH") || mode.equals("TWO_FALSE_HIGH") || mode.equals("PAUSE_RESUME")) {
        fault(process, HIPPS_SENSORS[0], 42.0);
        if (!mode.equals("ONE_FALSE_HIGH")) {
          fault(process, HIPPS_SENSORS[1], 42.0);
        }
      }
      if (mode.equals("ONE_STUCK_NORMAL") || mode.equals("TWO_STUCK_NORMAL")) {
        fault(process, HIPPS_SENSORS[0], 40.0);
        if (mode.equals("TWO_STUCK_NORMAL")) {
          fault(process, HIPPS_SENSORS[1], 40.0);
        }
      }
      if (mode.equals("MANUAL") || mode.equals("MANUAL_RESET_RETRIP")) {
        ((PushButton) process.getMeasurementDevice("20-HS-001")).push();
      }
      event("INITIATING_EVENT", "Declared " + mode, true);
    }

    /**
     * Samples actual state once per event-window boundary and returns the requested native pressure criterion.
     *
     * @param process actual isolated process
     * @param vesselTag pressure source
     * @return actual pressure, bara
     */
    private double sample(ProcessSystem process, String vesselTag) {
      double time = process.getTime() - clockOrigin;
      if (Double.isNaN(lastRowTime) || time != lastRowTime) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("time_s", Double.valueOf(time));
        row.put("event_relative_time_s", Double.valueOf(time - 1.0));
        row.put("dt_s", Double.valueOf(process.getTimeStep()));
        UUID identifier = process.getCalculationIdentifier();
        row.put("physical_step_id", identifier.toString());
        for (String tag : new String[] {"10-V-100", "20-V-101"}) {
          Separator vessel = vessel(process, tag);
          row.put(tag + ".pressure_bara", Double.valueOf(vessel.getPressure()));
          row.put(tag + ".temperature_K", Double.valueOf(vessel.getThermoSystem().getTemperature()));
          row.put(tag + ".level_fraction", Double.valueOf(vessel.getLiquidLevel()));
          row.put(tag + ".mass_kg", Double.valueOf(vessel.getThermoSystem().getMass("kg")));
          row.put(tag + ".internal_energy_J", Double.valueOf(vessel.getThermoSystem().getInternalEnergy()));
          for (String component : COMPONENTS) {
            row.put(tag + "." + component + "_mol",
                Double.valueOf(vessel.getThermoSystem().getComponent(component).getNumberOfmoles()));
          }
        }
        for (String tag : new String[] {"20-ESDV-001", "20-HIPPS-XV-001", "20-PCV-001", "10-LCV-001", "20-LCV-001"}) {
          ThrottlingValve valve = valve(process, tag);
          row.put(tag + ".opening_pct", Double.valueOf(valve.getPercentValveOpening()));
          row.put(tag + ".inlet_flow_kg_s", Double.valueOf(valve.getInletStream().getFlowRate("kg/sec")));
          row.put(tag + ".outlet_flow_kg_s", Double.valueOf(valve.getOutletStream().getFlowRate("kg/sec")));
        }
        boolean fresh = true;
        for (MeasurementDeviceInterface measurement : process.getMeasurementDevices()) {
          if (measurement instanceof TransientMeasurementDevice) {
            TransientMeasurementDevice held = (TransientMeasurementDevice) measurement;
            if (held.isTransientSamplingEnabled()) {
              fresh &= held.hasTransientSample(identifier);
              row.put(measurement.getName() + ".held_value", Double.valueOf(held.getTransientSample()));
            }
          }
        }
        row.put("sensor_samples_current_step", Boolean.valueOf(fresh));
        row.put("20-HS-001.pushed",
            Boolean.valueOf(((PushButton) process.getMeasurementDevice("20-HS-001")).isPushed()));
        for (ClosedLoopSafetyFunction function : functions) {
          Map<String, Object> evidence = function.getDynamicSafetyScenarioEvidence();
          List<?> trace = (List<?>) evidence.get("trace");
          Integer previous = lastTraceSizes.get(function.getName());
          row.put(function.getName() + ".trace_sample_fresh",
              Boolean.valueOf(previous == null ? !trace.isEmpty() : trace.size() > previous.intValue()));
          lastTraceSizes.put(function.getName(), Integer.valueOf(trace.size()));
          row.put(function.getName() + ".state", function.getState().name());
          if (!trace.isEmpty()) {
            Map<?, ?> last = (Map<?, ?>) trace.get(trace.size() - 1);
            row.put(function.getName() + ".active_elapsed_s", last.get("timeSeconds"));
            row.put(function.getName() + ".vote", last.get("vote"));
            row.put(function.getName() + ".tripped_channels", last.get("trippedChannelCount"));
          }
        }
        energyRow(process, row);
        rows.add(row);
        lastRowTime = time;
      }
      return vessel(process, vesselTag).getPressure();
    }

    /**
     * Records consumed native energy accounting without mislabelling it as an independent raw-flux oracle.
     *
     * @param process actual isolated process
     * @param row actual current row
     */
    private void energyRow(ProcessSystem process, Map<String, Object> row) {
      SeparatorTransientEnergyBalanceReport a = vessel(process, "10-V-100").getLastTransientEnergyBalanceReport();
      SeparatorTransientEnergyBalanceReport b = vessel(process, "20-V-101").getLastTransientEnergyBalanceReport();
      if (a == null || b == null) {
        throw new IllegalStateException("Accepted native energy reports are unavailable");
      }
      double external = a.getInletEnergyJ() - a.getLiquidOutletEnergyJ() - b.getGasOutletEnergyJ()
          - b.getLiquidOutletEnergyJ();
      double debt = a.getPublishedEnergyJ() + b.getPublishedEnergyJ() - a.getInitialEnergyJ() - b.getInitialEnergyJ()
          - external;
      double internal = a.getGasOutletEnergyJ() - b.getInletEnergyJ();
      maximumWholeEnergyDebt = Math.max(maximumWholeEnergyDebt, Math.abs(debt));
      maximumInternalEnergyDifference = Math.max(maximumInternalEnergyDifference, Math.abs(internal));
      row.put("native_whole_energy_debt_J", Double.valueOf(debt));
      row.put("native_internal_energy_transfer_difference_J", Double.valueOf(internal));
      row.put("V100_native_energy_complete", Boolean.valueOf(a.isComplete()));
      row.put("V101_native_energy_complete", Boolean.valueOf(b.isComplete()));
      row.put("V100_native_max_relative_energy_residual", Double.valueOf(a.getMaximumRelativeCumulativeResidual()));
      row.put("V101_native_max_relative_energy_residual", Double.valueOf(b.getMaximumRelativeCumulativeResidual()));
    }

    /**
     * Records a real operator command or permissive decision, not an invented process response.
     *
     * @param action declared action
     * @param reason explicit reason
     * @param accepted whether the application accepted the request
     */
    private void event(String action, String reason, boolean accepted) {
      Map<String, Object> event = new LinkedHashMap<String, Object>();
      event.put("timeSeconds", Double.valueOf(configured.getTime() - clockOrigin));
      event.put("action", action);
      event.put("reason", reason);
      event.put("accepted", Boolean.valueOf(accepted));
      operatorEvents.add(event);
    }

    /** @return native configuration, execution, raw rows and separately scoped numerical dispositions */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "advanced_v100_v101_safety_study.v1");
      result.put("scenarioId", id);
      result.put("mode", mode);
      result.put("settings", getSettings());
      result.put("dtSeconds", Double.valueOf(dt));
      result.put("durationSeconds", Double.valueOf(duration));
      result.put("observedEventWindowStartSeconds", Double.valueOf(1.0));
      result.put("nativeResult", nativeResult == null ? null : nativeResult.toMap());
      result.put("nativeResultOrigin", nativeResult == null
          ? "External project runner owns authoritative typed result; bundle contains actual factory/trace evidence only"
          : "DynamicSafetyScenarioRunner.runStrict invoked by this bundle");
      result.put("physicalTrace", new ArrayList<Map<String, Object>>(rows));
      result.put("operatorEvents", new ArrayList<Map<String, Object>>(operatorEvents));
      result.put("priorFunctionEpochs", new ArrayList<Map<String, Object>>(priorEpochs));
      result.put("wholeEnergyMaxObservedEventWindowJ", Double.valueOf(maximumWholeEnergyDebt));
      result.put("internalConsumedEnergyMaxObservedDifferenceJ", Double.valueOf(maximumInternalEnergyDifference));
      Map<String, Object> energy = new LinkedHashMap<String, Object>();
      if (configured != null) {
        for (String tag : new String[] {"10-V-100", "20-V-101"}) {
          Separator vessel = vessel(configured, tag);
          SeparatorTransientEnergyBalanceReport report = vessel.getLastTransientEnergyBalanceReport();
          Map<String, Object> one = new LinkedHashMap<String, Object>();
          one.put("status", vessel.getTransientEnergyBalanceTrackingStatus().name());
          one.put("verdict", vessel.assessTransientEnergyBalance(value(settings, "relativeEnergyLimit")).name());
          if (report != null) {
            one.put("initialEnergyJ", Double.valueOf(report.getInitialEnergyJ()));
            one.put("maxRelativeCumulativeResidual", Double.valueOf(report.getMaximumRelativeCumulativeResidual()));
            one.put("maxAbsoluteCumulativeDebtJ", Double.valueOf(report.getMaximumAbsoluteCumulativeDebtJ()));
            one.put("cumulativeAbsoluteLocalVuDefectJ", Double.valueOf(report.getCumulativeAbsoluteLocalVuDefectJ()));
            one.put("acceptedElapsedSeconds", Double.valueOf(report.getElapsedTimeSeconds()));
            one.put("acceptedStepCount", Long.valueOf(report.getAcceptedStepCount()));
          }
          energy.put(tag, one);
        }
      }
      result.put("nativeEnergyAccounting", energy);
      result.put("externalIndependentAudit", "SEPARATE_ORDERED_REFERENCE_REQUIRED");
      result.put("valvePortConvention", "Solve-instant continuity; post-vessel endpoint streams may be staggered");
      result.put("channelSemantics", "Delay-qualified channels latch until reset; fixed m-out-of-n under bypass");
      result.put("engineeringQualification", "HOLD");
      result.put("fitnessForConstruction", Boolean.FALSE);
      return result;
    }

    /**
     * Exports actual result JSON and complete event-window physical CSV.
     *
     * @param directory output directory
     * @throws IOException if output cannot be written
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
      appendCsvRow(csv, new ArrayList<Object>(columns));
      for (Map<String, Object> row : rows) {
        List<Object> values = new ArrayList<Object>();
        for (String column : columns) {
          values.add(row.get(column));
        }
        appendCsvRow(csv, values);
      }
      Files.write(directory.resolve(id + ".csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * Coordinates actual native functions and explicit operator events; it never fabricates sensor/process values.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Coordinator implements ProcessLogic, DynamicSafetyScenarioEvidenceProvider {
    private static final long serialVersionUID = 1L;
    private final CaseBundle owner;
    private boolean active;

    /**
     * Binds this adapter to the actual configured native case.
     *
     * @param owner actual case owner
     */
    private Coordinator(CaseBundle owner) {
      this.owner = owner;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
      return "ADVANCED-V100-V101-COORDINATOR";
    }

    /** {@inheritDoc} */
    @Override
    public LogicState getState() {
      return active ? LogicState.RUNNING : LogicState.IDLE;
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
    public void activate() {
      active = true;
      for (ClosedLoopSafetyFunction function : owner.functions) {
        function.activate();
      }
      if (owner.mode.equals("HIPPS_INHIBITED") || owner.mode.equals("ESD_ONLY")) {
        owner.functions.get(1).deactivate();
      }
      if (owner.mode.equals("ALL_INHIBITED") || owner.mode.equals("MANUAL_RESET_RETRIP")) {
        owner.functions.get(0).deactivate();
        owner.functions.get(1).deactivate();
        if (owner.mode.equals("ALL_INHIBITED")) {
          owner.functions.get(2).deactivate();
        }
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
      boolean result = true;
      for (ClosedLoopSafetyFunction function : owner.functions) {
        result &= function.reset();
      }
      return result;
    }

    /** {@inheritDoc} */
    @Override
    public List<ProcessEquipmentInterface> getTargetEquipment() {
      return Arrays.<ProcessEquipmentInterface>asList(isolation(owner.configured, "20-ESDV-001"),
          isolation(owner.configured, "20-HIPPS-XV-001"));
    }

    /** {@inheritDoc} */
    @Override
    public String getStatusDescription() {
      return "Bounded actual-function " + owner.mode;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(double timeStep) {
      double relative = owner.configured.getTime() - owner.clockOrigin - 1.0;
      if (owner.mode.equals("CONTROL_RAMP")) {
        double time = relative + 1.0;
        isolation(owner.configured, "20-ESDV-001")
            .setPercentValveOpening(50.0 + 10.0 * Math.min(1.0, Math.max(0.0, (time - 5.0) / 5.0)));
      }
      if (owner.mode.equals("ASYNC_QUALIFIED_PULSES")) {
        for (int i = 0; i < 2; i++) {
          PressureTransmitter transmitter = (PressureTransmitter) owner.configured
              .getMeasurementDevice(HIPPS_SENSORS[i]);
          boolean high = i == 0 ? relative < 0.15 : relative >= 0.3 && relative < 0.45;
          if (high) {
            transmitter.setFault(SensorFaultType.STUCK_AT_VALUE, 42.0);
          } else {
            transmitter.clearFault();
          }
        }
      }
      if (owner.mode.equals("PAUSE_RESUME") && owner.pauseIssued && !owner.resumeIssued && relative >= 1.0 - 1.0e-9) {
        owner.functions.get(1).activate();
        owner.resumeIssued = true;
        owner.event("RESUME_HIPPS_LOGIC", "Resume existing native timer state", true);
      }
      if (owner.mode.equals("MANUAL_RESET_RETRIP")) {
        manualLifecycle(relative);
      }
      for (ClosedLoopSafetyFunction function : owner.functions) {
        if (function.isActive()) {
          function.execute(timeStep);
        }
      }
      if (owner.mode.equals("PAUSE_RESUME") && !owner.pauseIssued) {
        owner.functions.get(1).deactivate();
        owner.pauseIssued = true;
        owner.event("PAUSE_HIPPS_LOGIC", "Pause after first native evaluation", true);
      }
    }

    /**
     * Uses actual current held inputs for an explicit synthetic operator permissive and real physical rearm.
     *
     * @param relative event-relative physical time, seconds
     */
    private void manualLifecycle(double relative) {
      PushButton button = (PushButton) owner.configured.getMeasurementDevice("20-HS-001");
      if (relative >= 1.0 && !owner.resetDeniedRecorded) {
        owner.event("RESET_REQUEST", "Actual held-input and native manual-input permissive",
            resetPermitted(owner.configured, owner.settings));
        owner.resetDeniedRecorded = true;
      }
      if (relative >= 3.0 && !owner.rearmed) {
        button.reset();
        if (resetPermitted(owner.configured, owner.settings)) {
          for (ClosedLoopSafetyFunction function : owner.functions) {
            owner.priorEpochs.add(function.getDynamicSafetyScenarioEvidence());
            function.reset();
          }
          for (String tag : new String[] {"20-ESDV-001", "20-HIPPS-XV-001"}) {
            isolation(owner.configured, tag).reset();
            isolation(owner.configured, tag).setPercentValveOpening(50.0);
          }
          owner.functions.get(2).activate();
          owner.lastTraceSizes.clear();
          owner.rearmed = true;
          owner.event("OPERATOR_REARM", "Actual held inputs below setpoints and manual input released", true);
        }
      }
      if (relative >= 8.0 && owner.rearmed && !owner.secondManualIssued) {
        button.push();
        owner.secondManualIssued = true;
        owner.event("SECOND_MANUAL_TRIP", "Actual native push button pressed again", true);
      }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getDynamicSafetyScenarioEvidence() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "advanced_native_safety_coordinator.v1");
      List<Map<String, Object>> evidence = new ArrayList<Map<String, Object>>();
      for (ClosedLoopSafetyFunction function : owner.functions) {
        evidence.add(function.getDynamicSafetyScenarioEvidence());
      }
      result.put("nativeFunctions", evidence);
      result.put("operatorEvents", new ArrayList<Map<String, Object>>(owner.operatorEvents));
      result.put("priorFunctionEpochs", new ArrayList<Map<String, Object>>(owner.priorEpochs));
      result.put("mode", owner.mode);
      result.put("loopClock",
          "Native loop time accumulates active execution only; physical CSV retains wall simulation time");
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }

  /**
   * Reads actual native held inputs; it does not use stale latched channel decisions as reset permissives.
   *
   * @param process actual configured copy
   * @param settings explicit trip values
   * @return true only while every observed trip input is clear and the manual input is released
   */
  public static boolean resetPermitted(ProcessSystem process, Map<String, Double> settings) {
    if (((PushButton) process.getMeasurementDevice("20-HS-001")).isPushed()) {
      return false;
    }
    if (((TransientMeasurementDevice) process.getMeasurementDevice("10-PT-002"))
        .getTransientSample("bara") >= value(settings, "esdV100TripBara")) {
      return false;
    }
    if (((TransientMeasurementDevice) process.getMeasurementDevice("20-PT-002"))
        .getTransientSample("bara") >= value(settings, "esdV101TripBara")) {
      return false;
    }
    for (String tag : HIPPS_SENSORS) {
      if (((TransientMeasurementDevice) process.getMeasurementDevice(tag)).getTransientSample("bara") >= value(settings,
          "hippsTripBara")) {
        return false;
      }
    }
    return true;
  }

  /**
   * Installs a native stuck-at signal fault; no indication is fabricated after the process step.
   *
   * @param process actual isolated process
   * @param tag actual transmitter tag
   * @param indicated fixed indicated pressure, bara
   */
  private static void fault(ProcessSystem process, String tag, double indicated) {
    ((PressureTransmitter) process.getMeasurementDevice(tag)).setFault(SensorFaultType.STUCK_AT_VALUE, indicated);
  }

  /**
   * Identifies profiles with no physical demand and no intended isolation command.
   *
   * @param mode explicit profile
   * @return true for required whole-window no-trip cases
   */
  private static boolean noTripExpected(String mode) {
    return mode.equals("NORMAL") || mode.equals("CONTROL_RAMP") || mode.equals("ONE_FALSE_HIGH");
  }

  /**
   * Identifies profiles whose declared physical initiating event blocks the gas outlet.
   *
   * @param mode explicit profile
   * @return true when actual gas withdrawal must be blocked
   */
  private static boolean blockedGas(String mode) {
    return Arrays.asList("TRUE_DEMAND", "ONE_STUCK_NORMAL", "TWO_STUCK_NORMAL", "ONE_BYPASSED", "TWO_BYPASSED",
        "HIPPS_FAILED", "HIPPS_SLOW", "HIPPS_INHIBITED", "ALL_INHIBITED", "ESD_ONLY").contains(mode);
  }

  /**
   * Writes one standard quoted CSV row without changing the measured values.
   *
   * @param target output builder
   * @param values actual row values
   */
  private static void appendCsvRow(StringBuilder target, List<Object> values) {
    for (int i = 0; i < values.size(); i++) {
      if (i != 0) {
        target.append(',');
      }
      Object value = values.get(i);
      if (value != null) {
        target.append('"').append(value.toString().replace("\"", "\"\"")).append('"');
      }
    }
    target.append('\n');
  }

  /**
   * Assigns explicit process geometry through its native mechanical-design owner without inventing wall/material data.
   *
   * @param vessel actual native separator
   * @param diameter inner diameter, m
   * @param length cylinder length, m
   * @param level initial height fraction
   */
  private static void geometry(Separator vessel, double diameter, double length, double level) {
    vessel.getMechanicalDesign().setInnerDiameter(diameter);
    vessel.getMechanicalDesign().setTantanLength(length);
    vessel.getMechanicalDesign().setDesign();
    vessel.setLiquidLevel(level);
  }

  /**
   * Registers a real pressure channel with one held engineering-unit sample per native physical step.
   *
   * @param process native model owner
   * @param tag actual transmitter tag
   * @param stream actual process source
   */
  private static void pressure(ProcessSystem process, String tag, StreamInterface stream) {
    PressureTransmitter transmitter = new PressureTransmitter(tag, stream);
    transmitter.setUnit("bara");
    transmitter.setMinimumValue(0.0);
    transmitter.setMaximumValue(60.0);
    transmitter.setTransientSamplingEnabled(true);
    process.add(transmitter);
  }

  /**
   * Registers a real sampled temperature monitor.
   *
   * @param process native model owner
   * @param tag actual transmitter tag
   * @param stream actual process source
   */
  private static void temperature(ProcessSystem process, String tag, StreamInterface stream) {
    TemperatureTransmitter transmitter = new TemperatureTransmitter(tag, stream);
    transmitter.setUnit("C");
    transmitter.setTransientSamplingEnabled(true);
    process.add(transmitter);
  }

  /**
   * Registers a real sampled mass-flow monitor.
   *
   * @param process native model owner
   * @param tag actual transmitter tag
   * @param stream actual process source
   */
  private static void flow(ProcessSystem process, String tag, StreamInterface stream) {
    VolumeFlowTransmitter transmitter = new VolumeFlowTransmitter(tag, stream);
    transmitter.setUnit("kg/hr");
    transmitter.setTransientSamplingEnabled(true);
    process.add(transmitter);
  }

  /**
   * Attaches and registers an actual native engineering-unit PI controller.
   *
   * @param process controller owner
   * @param tag actual controller tag
   * @param valve actual final control valve
   * @param measurement actual input transmitter
   * @param setpoint engineering-unit target
   * @param unit engineering unit, empty for native normalized level
   * @param kp proportional gain
   * @param ti integral time, seconds
   */
  private static void controller(ProcessSystem process, String tag, ThrottlingValve valve,
      MeasurementDeviceInterface measurement, double setpoint, String unit, double kp, double ti) {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(tag);
    controller.setTransmitter(measurement);
    controller.setControllerSetPoint(setpoint, unit);
    controller.setReverseActing(false);
    controller.setControllerParameters(kp, ti, 0.0);
    controller.setOutputLimits(0.0, 100.0);
    valve.setController(controller);
    process.add(controller);
  }

  /**
   * Reads an explicit finite design value without silently supplying missing proposal data.
   *
   * @param settings explicit settings
   * @param key expected value name
   * @return finite configured value
   */
  private static double value(Map<String, Double> settings, String key) {
    Double result = settings.get(key);
    if (result == null || !Double.isFinite(result.doubleValue())) {
      throw new IllegalArgumentException("Missing/nonfinite advanced-study setting: " + key);
    }
    return result.doubleValue();
  }

  /**
   * Resolves an actual native vessel by its controlled tag.
   *
   * @param process native model owner
   * @param tag actual tag
   * @return actual separator
   */
  private static Separator vessel(ProcessSystem process, String tag) {
    return (Separator) process.getUnit(tag);
  }

  /**
   * Resolves an actual native valve by its controlled tag.
   *
   * @param process native model owner
   * @param tag actual tag
   * @return actual valve
   */
  private static ThrottlingValve valve(ProcessSystem process, String tag) {
    return (ThrottlingValve) process.getUnit(tag);
  }

  /**
   * Resolves an actual finite-stroke isolation valve.
   *
   * @param process native model owner
   * @param tag actual tag
   * @return actual ESDValve
   */
  private static ESDValve isolation(ProcessSystem process, String tag) {
    return (ESDValve) process.getUnit(tag);
  }
}
