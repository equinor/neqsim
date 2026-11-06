package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerPerformanceMetrics;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport.EnergyVerdict;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.LevelControlValve;
import neqsim.process.equipment.valve.PressureControlValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Runs a revision-bound, instrumented separator pressure/level control acceptance study.
 *
 * <p>
 * The study resolves actual P&amp;ID bindings on an isolated process copy, applies declared US-Cv candidates, ramps the
 * physical inlet boundary and records one deterministic sample per accepted physical step. Response criteria and the
 * separator's explicit cumulative-energy policy remain separate. A passing result is simulation evidence for the
 * declared case only; it does not infer SIL/IPL credit, vendor valve performance, hazard completeness, standards
 * conformance or construction approval.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PidSeparatorControlStudy {
  private static final Gson GSON = new Gson();

  /** Prevents utility construction. */
  private PidSeparatorControlStudy() {
  }

  /**
   * Executes the declared study on a fresh isolated process copy.
   *
   * @param project governed source project
   * @param model current canonical P&amp;ID model
   * @param bindings current explicit P&amp;ID-to-runtime bindings
   * @param definition controlled study definition
   * @param limits explicit response and numerical acceptance limits
   * @return immutable trace, metrics, conservation and criterion evidence
   */
  public static Result run(EngineeringProject project, PidDesignModel model, PidSimulationBindings bindings,
      Definition definition, Limits limits) {
    requireInputs(project, model, bindings, definition, limits);
    bindings.assertCurrent(project, model);
    bindings.requireBindings(definition.boundPidTags());
    ProcessSystem process = project.getEngineeringProcessSystem().copy();
    Runtime runtime = Runtime.resolve(process, bindings, definition);
    runtime.validateTopology();
    runtime.pressureValve.setCv(definition.pressureValveCvUs);
    runtime.levelValve.setCv(definition.levelValveCvUs);
    runtime.separator.setConservativeTransientEnergyIntegrationEnabled(true);
    runtime.separator.setTransientEnergyBalanceTrackingEnabled(true);
    runtime.pressureController.resetPerformanceMetrics();
    runtime.levelController.resetPerformanceMetrics();

    Map<String, Object> configuration = configuration(project, model, bindings, definition, runtime);
    String configurationFingerprint = sha256(GSON.toJson(configuration));
    double initialEnergyJ = runtime.separator.getThermoSystem().getInternalEnergy();
    double oracleEnergyJ = initialEnergyJ;
    double maximumComponentResidualMol = 0.0;
    double maximumRelativeVolumeResidual = 0.0;
    double minimumPressureBara = runtime.separator.getPressure("bara");
    double maximumPressureBara = minimumPressureBara;
    double minimumLevelFraction = runtime.separator.getLiquidLevel();
    double maximumLevelFraction = minimumLevelFraction;
    double minimumPressureValveOpeningPercent = runtime.pressureValve.getPercentValveOpening();
    double maximumPressureValveOpeningPercent = minimumPressureValveOpeningPercent;
    double minimumLevelValveOpeningPercent = runtime.levelValve.getPercentValveOpening();
    double maximumLevelValveOpeningPercent = minimumLevelValveOpeningPercent;
    List<Sample> samples = new ArrayList<Sample>();

    int stepCount = definition.stepCount();
    for (int step = 0; step < stepCount; step++) {
      double startTimeSeconds = step * definition.timeStepSeconds;
      double endTimeSeconds = (step + 1) * definition.timeStepSeconds;
      double inletFlowKgPerSecond = definition.inletFlowAt(endTimeSeconds);
      UUID physicalStepId = deterministicId(configurationFingerprint, step);
      runtime.feed.setFlowRate(inletFlowKgPerSecond, "kg/sec");
      runtime.feed.run(physicalStepId);

      double[] expectedMoles = componentMoles(runtime.separator.getThermoSystem());
      SystemInterface inlet = runtime.feed.getThermoSystem();
      SystemInterface gasOutlet = runtime.separator.getGasOutStream().getThermoSystem();
      SystemInterface liquidOutlet = runtime.separator.getLiquidOutStream().getThermoSystem();
      for (int component = 0; component < expectedMoles.length; component++) {
        expectedMoles[component] += definition.timeStepSeconds
            * (inlet.getComponent(component).getNumberOfmoles() - gasOutlet.getComponent(component).getNumberOfmoles()
                - liquidOutlet.getComponent(component).getNumberOfmoles());
      }
      oracleEnergyJ += definition.timeStepSeconds
          * (inlet.getEnthalpy() - gasOutlet.getEnthalpy() - liquidOutlet.getEnthalpy());

      process.runTransient(definition.timeStepSeconds, physicalStepId);
      maximumComponentResidualMol = Math.max(maximumComponentResidualMol,
          maximumDifference(expectedMoles, componentMoles(runtime.separator.getThermoSystem())));
      SeparatorTransientEnergyBalanceReport energy = runtime.separator.getLastTransientEnergyBalanceReport();
      if (energy == null) {
        throw new IllegalStateException("Separator energy tracking did not produce accepted-step evidence");
      }
      maximumRelativeVolumeResidual = Math.max(maximumRelativeVolumeResidual, energy.getRelativeVolumeResidual());

      double pressureBara = runtime.separator.getPressure("bara");
      double levelFraction = runtime.separator.getLiquidLevel();
      double pressureOpening = runtime.pressureValve.getPercentValveOpening();
      double levelOpening = runtime.levelValve.getPercentValveOpening();
      requireFinitePhysicalState(pressureBara, levelFraction, pressureOpening, levelOpening);
      minimumPressureBara = Math.min(minimumPressureBara, pressureBara);
      maximumPressureBara = Math.max(maximumPressureBara, pressureBara);
      minimumLevelFraction = Math.min(minimumLevelFraction, levelFraction);
      maximumLevelFraction = Math.max(maximumLevelFraction, levelFraction);
      minimumPressureValveOpeningPercent = Math.min(minimumPressureValveOpeningPercent, pressureOpening);
      maximumPressureValveOpeningPercent = Math.max(maximumPressureValveOpeningPercent, pressureOpening);
      minimumLevelValveOpeningPercent = Math.min(minimumLevelValveOpeningPercent, levelOpening);
      maximumLevelValveOpeningPercent = Math.max(maximumLevelValveOpeningPercent, levelOpening);
      samples.add(new Sample(step, startTimeSeconds, endTimeSeconds, physicalStepId, inletFlowKgPerSecond, pressureBara,
          runtime.pressureTransmitter.getTransientSample("bara"), levelFraction,
          runtime.levelTransmitter.getTransientSample(), pressureOpening, levelOpening,
          energy.getRelativeCumulativeResidual(), energy.getRelativeVolumeResidual()));
    }

    SeparatorTransientEnergyBalanceReport energy = runtime.separator.getLastTransientEnergyBalanceReport();
    double oracleDifferenceJ = energy.getExpectedEnergyJ() - oracleEnergyJ;
    double actualOracleResidual = Math.abs(runtime.separator.getThermoSystem().getInternalEnergy() - oracleEnergyJ)
        / Math.max(Math.abs(initialEnergyJ), 1.0);
    ControllerPerformanceMetrics pressureMetrics = runtime.pressureController.getPerformanceMetrics();
    ControllerPerformanceMetrics levelMetrics = runtime.levelController.getPerformanceMetrics();
    Map<String, Check> checks = checks(limits, runtime, energy, pressureMetrics, levelMetrics,
        maximumComponentResidualMol, maximumRelativeVolumeResidual, minimumPressureBara, maximumPressureBara,
        minimumLevelFraction, maximumLevelFraction, minimumPressureValveOpeningPercent,
        maximumPressureValveOpeningPercent, minimumLevelValveOpeningPercent, maximumLevelValveOpeningPercent,
        actualOracleResidual, oracleDifferenceJ, samples.size());
    return new Result(definition, limits, configuration, configurationFingerprint, samples, checks, pressureMetrics,
        levelMetrics, minimumPressureBara, maximumPressureBara, minimumLevelFraction, maximumLevelFraction,
        minimumPressureValveOpeningPercent, maximumPressureValveOpeningPercent, minimumLevelValveOpeningPercent,
        maximumLevelValveOpeningPercent, maximumComponentResidualMol, maximumRelativeVolumeResidual,
        actualOracleResidual, oracleDifferenceJ, energy);
  }

  /**
   * Compares coarse and refined results for the same controlled definition except timestep and study ID.
   *
   * @param coarse coarse-step result
   * @param refined half-step or otherwise refined result
   * @param limits explicit allowable response differences
   * @return structured refinement evidence
   */
  public static RefinementResult compareRefinement(Result coarse, Result refined, RefinementLimits limits) {
    if (coarse == null || refined == null || limits == null) {
      throw new IllegalArgumentException("coarse, refined and limits are required");
    }
    if (!coarse.definition.samePhysicalCase(refined.definition)
        || refined.definition.timeStepSeconds >= coarse.definition.timeStepSeconds) {
      throw new IllegalArgumentException("Refinement requires the same physical case and a smaller timestep");
    }
    Map<String, Check> checks = new LinkedHashMap<String, Check>();
    addMaximum(checks, "FINAL_PRESSURE_DIFFERENCE",
        Math.abs(coarse.getFinalSample().pressureBara - refined.getFinalSample().pressureBara),
        limits.maximumFinalPressureDifferenceBar, "bar");
    addMaximum(checks, "PEAK_PRESSURE_DIFFERENCE", Math.abs(coarse.maximumPressureBara - refined.maximumPressureBara),
        limits.maximumPeakPressureDifferenceBar, "bar");
    addMaximum(checks, "FINAL_LEVEL_DIFFERENCE",
        Math.abs(coarse.getFinalSample().levelFraction - refined.getFinalSample().levelFraction),
        limits.maximumFinalLevelDifferenceFraction, "fraction");
    addMaximum(checks, "PEAK_LEVEL_DIFFERENCE", Math.abs(coarse.maximumLevelFraction - refined.maximumLevelFraction),
        limits.maximumPeakLevelDifferenceFraction, "fraction");
    addMaximum(checks, "FINAL_PCV_OPENING_DIFFERENCE",
        Math.abs(
            coarse.getFinalSample().pressureValveOpeningPercent - refined.getFinalSample().pressureValveOpeningPercent),
        limits.maximumFinalValveDifferencePercent, "%");
    addMaximum(checks, "FINAL_LCV_OPENING_DIFFERENCE",
        Math.abs(coarse.getFinalSample().levelValveOpeningPercent - refined.getFinalSample().levelValveOpeningPercent),
        limits.maximumFinalValveDifferencePercent, "%");
    return new RefinementResult(coarse.resultFingerprint, refined.resultFingerprint, checks);
  }

  /**
   * Creates detached P&amp;ID/package evidence for one executed result and its refinement comparison.
   *
   * @param result executed native result
   * @param refinement timestep comparison, or null when not yet executed
   * @return immutable-compatible evidence map for report/package export
   */
  public static Map<String, Object> evidence(Result result, RefinementResult refinement) {
    if (result == null) {
      throw new IllegalArgumentException("result is required");
    }
    Map<String, Object> output = new LinkedHashMap<String, Object>();
    output.put("schemaVersion", "neqsim_pid_separator_control_evidence.v1");
    output.put("studyId", result.definition.studyId);
    output.put("configurationFingerprint", result.configurationFingerprint);
    output.put("resultFingerprint", result.resultFingerprint);
    output.put("executionStatus", result.passed ? "EXECUTED_PASS" : "EXECUTED_FAIL");
    output.put("boundPidTags", result.definition.boundPidTags());
    output.put("definition", result.definition.toMap());
    output.put("limits", result.limits.toMap());
    output.put("checks", checkMaps(result.checks));
    output.put("responseMetrics", result.responseMetrics());
    output.put("conservation", result.conservation());
    output.put("refinement", refinement == null ? null : refinement.toMap());
    output.put("observationCoverage", "ONE_NATIVE_SAMPLE_PER_ACCEPTED_PHYSICAL_STEP");
    output.put("candidateQualification", "DECLARED_US_CV_AND_INLET_RAMP_ONLY_NOT_GLOBAL_OPTIMUM_OR_VENDOR_RATING");
    output.put("engineeringApprovalRequired", Boolean.TRUE);
    output.put("silIplSrsCreditEstablished", Boolean.FALSE);
    output.put("hazardCompletenessEstablished", Boolean.FALSE);
    output.put("fitnessForConstruction", Boolean.FALSE);
    return output;
  }

  /** Validates required inputs before source-copy creation. */
  private static void requireInputs(EngineeringProject project, PidDesignModel model, PidSimulationBindings bindings,
      Definition definition, Limits limits) {
    if (project == null || model == null || bindings == null || definition == null || limits == null) {
      throw new IllegalArgumentException("project, model, bindings, definition and limits are required");
    }
  }

  /** Builds the frozen source/configuration receipt included in every result. */
  private static Map<String, Object> configuration(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, Definition definition, Runtime runtime) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_pid_separator_control_configuration.v1");
    result.put("projectId", project.getProjectId());
    result.put("projectRevision", project.getRevision());
    result.put("pidProfileId", model.getProfileId());
    result.put("bindings", bindings.toMap());
    result.put("definition", definition.toMap());
    result.put("separatorClass", runtime.separator.getClass().getName());
    result.put("pressureControllerClass", runtime.pressureController.getClass().getName());
    result.put("levelControllerClass", runtime.levelController.getClass().getName());
    result.put("pressureSetPointBara", Double.valueOf(runtime.pressureController.getControllerSetPoint()));
    result.put("levelSetPointFraction", Double.valueOf(runtime.levelController.getControllerSetPoint()));
    result.put("pressureValveTravelTimeSeconds", Double.valueOf(runtime.pressureValve.getTravelTimeConstant()));
    result.put("levelValveTravelTimeSeconds", Double.valueOf(runtime.levelValve.getTravelTimeConstant()));
    result.put("conservativeEnergyIntegration", Boolean.TRUE);
    result.put("energyTracking", Boolean.TRUE);
    return result;
  }

  /** Creates every response and numerical criterion without hiding a tolerance. */
  private static Map<String, Check> checks(Limits limits, Runtime runtime, SeparatorTransientEnergyBalanceReport energy,
      ControllerPerformanceMetrics pressureMetrics, ControllerPerformanceMetrics levelMetrics,
      double maximumComponentResidualMol, double maximumRelativeVolumeResidual, double minimumPressureBara,
      double maximumPressureBara, double minimumLevelFraction, double maximumLevelFraction,
      double minimumPressureValveOpeningPercent, double maximumPressureValveOpeningPercent,
      double minimumLevelValveOpeningPercent, double maximumLevelValveOpeningPercent, double actualOracleResidual,
      double oracleDifferenceJ, int sampleCount) {
    Map<String, Check> checks = new LinkedHashMap<String, Check>();
    addMinimum(checks, "MINIMUM_PRESSURE", minimumPressureBara, limits.minimumPressureBara, "bara");
    addMaximum(checks, "MAXIMUM_PRESSURE", maximumPressureBara, limits.maximumPressureBara, "bara");
    addMinimum(checks, "MINIMUM_LEVEL", minimumLevelFraction, limits.minimumLevelFraction, "fraction");
    addMaximum(checks, "MAXIMUM_LEVEL", maximumLevelFraction, limits.maximumLevelFraction, "fraction");
    addMaximum(checks, "PRESSURE_IAE", pressureMetrics.getIntegralAbsoluteError(), limits.maximumPressureIaeBarSeconds,
        "bar*s");
    addMaximum(checks, "LEVEL_IAE", levelMetrics.getIntegralAbsoluteError(), limits.maximumLevelIaeFractionSeconds,
        "fraction*s");
    addMaximum(checks, "PRESSURE_PEAK_ERROR", pressureMetrics.getPeakAbsoluteError(),
        limits.maximumPressurePeakErrorBar, "bar");
    addMaximum(checks, "LEVEL_PEAK_ERROR", levelMetrics.getPeakAbsoluteError(), limits.maximumLevelPeakErrorFraction,
        "fraction");
    addMaximum(checks, "PRESSURE_OUTPUT_TRAVEL", pressureMetrics.getControllerOutputTravel(),
        limits.maximumPressureOutputTravelPercent, "%");
    addMaximum(checks, "LEVEL_OUTPUT_TRAVEL", levelMetrics.getControllerOutputTravel(),
        limits.maximumLevelOutputTravelPercent, "%");
    addMaximum(checks, "PRESSURE_SETTLING_TIME", pressureMetrics.getSettlingTime(),
        limits.maximumPressureSettlingTimeSeconds, "s");
    addMaximum(checks, "LEVEL_SETTLING_TIME", levelMetrics.getSettlingTime(), limits.maximumLevelSettlingTimeSeconds,
        "s");
    addRange(checks, "PRESSURE_VALVE_OPENING", minimumPressureValveOpeningPercent, maximumPressureValveOpeningPercent,
        limits.minimumValveOpeningPercent, limits.maximumValveOpeningPercent, "%");
    addRange(checks, "LEVEL_VALVE_OPENING", minimumLevelValveOpeningPercent, maximumLevelValveOpeningPercent,
        limits.minimumValveOpeningPercent, limits.maximumValveOpeningPercent, "%");
    addMaximum(checks, "COMPONENT_INVENTORY_RESIDUAL", maximumComponentResidualMol, limits.maximumComponentResidualMol,
        "mol");
    addMaximum(checks, "VOLUME_RESIDUAL", maximumRelativeVolumeResidual, limits.maximumRelativeVolumeResidual,
        "fraction");
    addMaximum(checks, "CUMULATIVE_ENERGY_RESIDUAL", energy.getMaximumRelativeCumulativeResidual(),
        limits.maximumRelativeCumulativeEnergyResidual, "fraction");
    addMaximum(checks, "INDEPENDENT_ENERGY_ORACLE_RESIDUAL", actualOracleResidual,
        limits.maximumRelativeCumulativeEnergyResidual, "fraction");
    addMaximum(checks, "TRACKER_ORACLE_DIFFERENCE", Math.abs(oracleDifferenceJ), limits.maximumTrackerOracleDifferenceJ,
        "J");
    checks.put("ENERGY_COVERAGE",
        new Check("ENERGY_COVERAGE", energy.isComplete() ? 1.0 : 0.0, 1.0, 1.0, "boolean", energy.isComplete()));
    checks.put("ENERGY_POLICY",
        new Check("ENERGY_POLICY",
            runtime.separator.assessTransientEnergyBalance(
                limits.maximumRelativeCumulativeEnergyResidual) == EnergyVerdict.PASS ? 1.0 : 0.0,
            1.0, 1.0, "boolean", runtime.separator
                .assessTransientEnergyBalance(limits.maximumRelativeCumulativeEnergyResidual) == EnergyVerdict.PASS));
    int expectedSamples = runtime.pressureController.getPerformanceMetrics().getSampleCount();
    checks.put("COMPLETE_SCAN_COUNT",
        new Check("COMPLETE_SCAN_COUNT", sampleCount, Double.valueOf(sampleCount), Double.valueOf(sampleCount), "count",
            expectedSamples == sampleCount && levelMetrics.getSampleCount() == sampleCount));
    return checks;
  }

  /** Adds a maximum-value check. */
  private static void addMaximum(Map<String, Check> checks, String id, double observed, double maximum, String unit) {
    checks.put(id,
        new Check(id, observed, null, Double.valueOf(maximum), unit, Double.isFinite(observed) && observed <= maximum));
  }

  /** Adds a minimum-value check. */
  private static void addMinimum(Map<String, Check> checks, String id, double observed, double minimum, String unit) {
    checks.put(id,
        new Check(id, observed, Double.valueOf(minimum), null, unit, Double.isFinite(observed) && observed >= minimum));
  }

  /** Adds a range check using the observed extrema. */
  private static void addRange(Map<String, Check> checks, String id, double observedMinimum, double observedMaximum,
      double minimum, double maximum, String unit) {
    checks.put(id,
        new Check(id, observedMaximum, Double.valueOf(minimum), Double.valueOf(maximum), unit,
            Double.isFinite(observedMinimum) && Double.isFinite(observedMaximum) && observedMinimum >= minimum
                && observedMaximum <= maximum));
  }

  /** Converts ordered checks into JSON-compatible maps. */
  private static Map<String, Object> checkMaps(Map<String, Check> checks) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Check> entry : checks.entrySet()) {
      result.put(entry.getKey(), entry.getValue().toMap());
    }
    return result;
  }

  /** Reads total component inventory in stable system component order. */
  private static double[] componentMoles(SystemInterface system) {
    int count = system.getPhase(0).getNumberOfComponents();
    double[] result = new double[count];
    for (int component = 0; component < count; component++) {
      result[component] = system.getComponent(component).getNumberOfmoles();
    }
    return result;
  }

  /** Returns the largest absolute component difference. */
  private static double maximumDifference(double[] expected, double[] actual) {
    if (expected.length != actual.length) {
      throw new IllegalStateException("Separator component inventory order changed during the study");
    }
    double result = 0.0;
    for (int component = 0; component < expected.length; component++) {
      result = Math.max(result, Math.abs(expected[component] - actual[component]));
    }
    return result;
  }

  /** Rejects nonphysical or nonfinite state before it can receive acceptance credit. */
  private static void requireFinitePhysicalState(double pressureBara, double levelFraction,
      double pressureValveOpeningPercent, double levelValveOpeningPercent) {
    if (!Double.isFinite(pressureBara) || pressureBara <= 0.0 || !Double.isFinite(levelFraction) || levelFraction < 0.0
        || levelFraction > 1.0 || !Double.isFinite(pressureValveOpeningPercent) || pressureValveOpeningPercent < 0.0
        || pressureValveOpeningPercent > 100.0 || !Double.isFinite(levelValveOpeningPercent)
        || levelValveOpeningPercent < 0.0 || levelValveOpeningPercent > 100.0) {
      throw new IllegalStateException("Separator control study produced a nonphysical state");
    }
  }

  /** Creates a deterministic child physical-step identity from the configuration and index. */
  private static UUID deterministicId(String configurationFingerprint, int step) {
    return UUID.nameUUIDFromBytes(
        ("pid-separator-control:" + configurationFingerprint + ":" + step).getBytes(StandardCharsets.UTF_8));
  }

  /** Calculates a lowercase SHA-256 digest. */
  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        result.append(String.format("%02x", item & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  /** Resolved native objects in one isolated process copy. */
  private static final class Runtime {
    private final ProcessSystem process;
    private final Stream feed;
    private final Separator separator;
    private final PressureTransmitter pressureTransmitter;
    private final ControllerDeviceBaseClass pressureController;
    private final PressureControlValve pressureValve;
    private final LevelTransmitter levelTransmitter;
    private final ControllerDeviceBaseClass levelController;
    private final LevelControlValve levelValve;

    /** Stores resolved, strongly typed identities. */
    private Runtime(ProcessSystem process, Stream feed, Separator separator, PressureTransmitter pressureTransmitter,
        ControllerDeviceBaseClass pressureController, PressureControlValve pressureValve,
        LevelTransmitter levelTransmitter, ControllerDeviceBaseClass levelController, LevelControlValve levelValve) {
      this.process = process;
      this.feed = feed;
      this.separator = separator;
      this.pressureTransmitter = pressureTransmitter;
      this.pressureController = pressureController;
      this.pressureValve = pressureValve;
      this.levelTransmitter = levelTransmitter;
      this.levelController = levelController;
      this.levelValve = levelValve;
    }

    /** Resolves every declared actual object through the copied process and binding register. */
    private static Runtime resolve(ProcessSystem process, PidSimulationBindings bindings, Definition definition) {
      Object feed = process.getUnit(definition.feedSimulationName);
      Object separator = process.getUnit(definition.separatorSimulationName);
      Object pressureMeasurement = bindings.resolveMeasurement(definition.pressureTransmitterPidTag, process);
      Object pressureController = bindings.resolveController(definition.pressureControllerPidTag, process);
      Object pressureValve = bindings.resolveEquipment(definition.pressureValvePidTag, process);
      Object levelMeasurement = bindings.resolveMeasurement(definition.levelTransmitterPidTag, process);
      Object levelController = bindings.resolveController(definition.levelControllerPidTag, process);
      Object levelValve = bindings.resolveEquipment(definition.levelValvePidTag, process);
      if (!(feed instanceof Stream) || separator == null || separator.getClass() != Separator.class
          || !(pressureMeasurement instanceof PressureTransmitter)
          || !(pressureController instanceof ControllerDeviceBaseClass)
          || !(pressureValve instanceof PressureControlValve) || !(levelMeasurement instanceof LevelTransmitter)
          || !(levelController instanceof ControllerDeviceBaseClass) || !(levelValve instanceof LevelControlValve)) {
        throw new IllegalArgumentException(
            "Study requires exact Stream/Separator/PT/PIC/PCV/LT/LIC/LCV native object types");
      }
      return new Runtime(process, (Stream) feed, (Separator) separator, (PressureTransmitter) pressureMeasurement,
          (ControllerDeviceBaseClass) pressureController, (PressureControlValve) pressureValve,
          (LevelTransmitter) levelMeasurement, (ControllerDeviceBaseClass) levelController,
          (LevelControlValve) levelValve);
    }

    /** Validates wiring and complete transactional coverage before numerical execution. */
    private void validateTopology() {
      if (pressureController.getTransmitter() != pressureTransmitter
          || levelController.getTransmitter() != levelTransmitter || pressureValve.getController() != pressureController
          || levelValve.getController() != levelController
          || pressureValve.getInletStream() != separator.getGasOutStream()
          || levelValve.getInletStream() != separator.getLiquidOutStream()
          || !process.getTransientTransactionCoverage().isComplete()) {
        throw new IllegalArgumentException(
            "Actual control wiring, outlet identities or transaction coverage is incomplete");
      }
      if (!pressureTransmitter.isTransientSamplingEnabled() || !levelTransmitter.isTransientSamplingEnabled()
          || !pressureValve.isPhysicalStepIdempotencyEnabled() || !levelValve.isPhysicalStepIdempotencyEnabled()) {
        throw new IllegalArgumentException("Physical scan and actuator idempotency must be explicitly enabled");
      }
    }
  }

  /** Immutable controlled input, identity and candidate definition. */
  public static final class Definition implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String studyId;
    private final String feedSimulationName;
    private final String separatorSimulationName;
    private final String pressureTransmitterPidTag;
    private final String pressureControllerPidTag;
    private final String pressureValvePidTag;
    private final String levelTransmitterPidTag;
    private final String levelControllerPidTag;
    private final String levelValvePidTag;
    private final double initialInletFlowKgPerSecond;
    private final double finalInletFlowKgPerSecond;
    private final double rampDurationSeconds;
    private final double durationSeconds;
    private final double timeStepSeconds;
    private final double pressureValveCvUs;
    private final double levelValveCvUs;

    /**
     * Creates one explicit separator control candidate and inlet-ramp case.
     */
    public Definition(String studyId, String feedSimulationName, String separatorSimulationName,
        String pressureTransmitterPidTag, String pressureControllerPidTag, String pressureValvePidTag,
        String levelTransmitterPidTag, String levelControllerPidTag, String levelValvePidTag,
        double initialInletFlowKgPerSecond, double finalInletFlowKgPerSecond, double rampDurationSeconds,
        double durationSeconds, double timeStepSeconds, double pressureValveCvUs, double levelValveCvUs) {
      this.studyId = requireText(studyId, "studyId");
      this.feedSimulationName = requireText(feedSimulationName, "feedSimulationName");
      this.separatorSimulationName = requireText(separatorSimulationName, "separatorSimulationName");
      this.pressureTransmitterPidTag = requireText(pressureTransmitterPidTag, "pressureTransmitterPidTag");
      this.pressureControllerPidTag = requireText(pressureControllerPidTag, "pressureControllerPidTag");
      this.pressureValvePidTag = requireText(pressureValvePidTag, "pressureValvePidTag");
      this.levelTransmitterPidTag = requireText(levelTransmitterPidTag, "levelTransmitterPidTag");
      this.levelControllerPidTag = requireText(levelControllerPidTag, "levelControllerPidTag");
      this.levelValvePidTag = requireText(levelValvePidTag, "levelValvePidTag");
      requirePositiveFinite(initialInletFlowKgPerSecond, "initialInletFlowKgPerSecond");
      requirePositiveFinite(finalInletFlowKgPerSecond, "finalInletFlowKgPerSecond");
      requirePositiveFinite(rampDurationSeconds, "rampDurationSeconds");
      requirePositiveFinite(durationSeconds, "durationSeconds");
      requirePositiveFinite(timeStepSeconds, "timeStepSeconds");
      requirePositiveFinite(pressureValveCvUs, "pressureValveCvUs");
      requirePositiveFinite(levelValveCvUs, "levelValveCvUs");
      if (rampDurationSeconds > durationSeconds
          || Math.abs(durationSeconds / timeStepSeconds - Math.rint(durationSeconds / timeStepSeconds)) > 1.0e-9) {
        throw new IllegalArgumentException("Ramp must fit the duration and duration must contain whole physical steps");
      }
      this.initialInletFlowKgPerSecond = initialInletFlowKgPerSecond;
      this.finalInletFlowKgPerSecond = finalInletFlowKgPerSecond;
      this.rampDurationSeconds = rampDurationSeconds;
      this.durationSeconds = durationSeconds;
      this.timeStepSeconds = timeStepSeconds;
      this.pressureValveCvUs = pressureValveCvUs;
      this.levelValveCvUs = levelValveCvUs;
    }

    /** @return ordered P&amp;ID tag scope exercised by this case */
    public List<String> boundPidTags() {
      List<String> tags = new ArrayList<String>();
      Collections.addAll(tags, pressureTransmitterPidTag, pressureControllerPidTag, pressureValvePidTag,
          levelTransmitterPidTag, levelControllerPidTag, levelValvePidTag);
      return Collections.unmodifiableList(tags);
    }

    /** @return deterministic JSON-compatible definition */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("studyId", studyId);
      result.put("feedSimulationName", feedSimulationName);
      result.put("separatorSimulationName", separatorSimulationName);
      result.put("boundPidTags", boundPidTags());
      result.put("initialInletFlowKgPerSecond", Double.valueOf(initialInletFlowKgPerSecond));
      result.put("finalInletFlowKgPerSecond", Double.valueOf(finalInletFlowKgPerSecond));
      result.put("rampDurationSeconds", Double.valueOf(rampDurationSeconds));
      result.put("durationSeconds", Double.valueOf(durationSeconds));
      result.put("timeStepSeconds", Double.valueOf(timeStepSeconds));
      result.put("pressureValveCvUs", Double.valueOf(pressureValveCvUs));
      result.put("levelValveCvUs", Double.valueOf(levelValveCvUs));
      return result;
    }

    /** Returns linearly ramped inlet flow at an accepted-step endpoint. */
    private double inletFlowAt(double timeSeconds) {
      double fraction = Math.min(Math.max(timeSeconds / rampDurationSeconds, 0.0), 1.0);
      return initialInletFlowKgPerSecond + fraction * (finalInletFlowKgPerSecond - initialInletFlowKgPerSecond);
    }

    /** @return exact physical-step count */
    private int stepCount() {
      return (int) Math.round(durationSeconds / timeStepSeconds);
    }

    /** Compares all physical inputs except study identity and timestep. */
    private boolean samePhysicalCase(Definition other) {
      return feedSimulationName.equals(other.feedSimulationName)
          && separatorSimulationName.equals(other.separatorSimulationName)
          && pressureTransmitterPidTag.equals(other.pressureTransmitterPidTag)
          && pressureControllerPidTag.equals(other.pressureControllerPidTag)
          && pressureValvePidTag.equals(other.pressureValvePidTag)
          && levelTransmitterPidTag.equals(other.levelTransmitterPidTag)
          && levelControllerPidTag.equals(other.levelControllerPidTag)
          && levelValvePidTag.equals(other.levelValvePidTag)
          && initialInletFlowKgPerSecond == other.initialInletFlowKgPerSecond
          && finalInletFlowKgPerSecond == other.finalInletFlowKgPerSecond
          && rampDurationSeconds == other.rampDurationSeconds && durationSeconds == other.durationSeconds
          && pressureValveCvUs == other.pressureValveCvUs && levelValveCvUs == other.levelValveCvUs;
    }
  }

  /** Explicit response and numerical acceptance policy. */
  public static final class Limits implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double minimumPressureBara;
    private final double maximumPressureBara;
    private final double minimumLevelFraction;
    private final double maximumLevelFraction;
    private final double maximumPressureIaeBarSeconds;
    private final double maximumLevelIaeFractionSeconds;
    private final double maximumPressurePeakErrorBar;
    private final double maximumLevelPeakErrorFraction;
    private final double maximumPressureOutputTravelPercent;
    private final double maximumLevelOutputTravelPercent;
    private final double maximumPressureSettlingTimeSeconds;
    private final double maximumLevelSettlingTimeSeconds;
    private final double minimumValveOpeningPercent;
    private final double maximumValveOpeningPercent;
    private final double maximumComponentResidualMol;
    private final double maximumRelativeVolumeResidual;
    private final double maximumRelativeCumulativeEnergyResidual;
    private final double maximumTrackerOracleDifferenceJ;

    /** Creates a fully explicit policy; no limit is inferred by the study. */
    public Limits(double minimumPressureBara, double maximumPressureBara, double minimumLevelFraction,
        double maximumLevelFraction, double maximumPressureIaeBarSeconds, double maximumLevelIaeFractionSeconds,
        double maximumPressurePeakErrorBar, double maximumLevelPeakErrorFraction,
        double maximumPressureOutputTravelPercent, double maximumLevelOutputTravelPercent,
        double maximumPressureSettlingTimeSeconds, double maximumLevelSettlingTimeSeconds,
        double minimumValveOpeningPercent, double maximumValveOpeningPercent, double maximumComponentResidualMol,
        double maximumRelativeVolumeResidual, double maximumRelativeCumulativeEnergyResidual,
        double maximumTrackerOracleDifferenceJ) {
      requireFiniteRange(minimumPressureBara, maximumPressureBara, "pressure");
      requireFiniteRange(minimumLevelFraction, maximumLevelFraction, "level");
      requireFiniteRange(minimumValveOpeningPercent, maximumValveOpeningPercent, "valve opening");
      double[] nonnegative = {maximumPressureIaeBarSeconds, maximumLevelIaeFractionSeconds, maximumPressurePeakErrorBar,
          maximumLevelPeakErrorFraction, maximumPressureOutputTravelPercent, maximumLevelOutputTravelPercent,
          maximumPressureSettlingTimeSeconds, maximumLevelSettlingTimeSeconds, maximumComponentResidualMol,
          maximumRelativeVolumeResidual, maximumRelativeCumulativeEnergyResidual, maximumTrackerOracleDifferenceJ};
      for (double value : nonnegative) {
        if (!Double.isFinite(value) || value < 0.0) {
          throw new IllegalArgumentException("Acceptance maxima must be finite and nonnegative");
        }
      }
      this.minimumPressureBara = minimumPressureBara;
      this.maximumPressureBara = maximumPressureBara;
      this.minimumLevelFraction = minimumLevelFraction;
      this.maximumLevelFraction = maximumLevelFraction;
      this.maximumPressureIaeBarSeconds = maximumPressureIaeBarSeconds;
      this.maximumLevelIaeFractionSeconds = maximumLevelIaeFractionSeconds;
      this.maximumPressurePeakErrorBar = maximumPressurePeakErrorBar;
      this.maximumLevelPeakErrorFraction = maximumLevelPeakErrorFraction;
      this.maximumPressureOutputTravelPercent = maximumPressureOutputTravelPercent;
      this.maximumLevelOutputTravelPercent = maximumLevelOutputTravelPercent;
      this.maximumPressureSettlingTimeSeconds = maximumPressureSettlingTimeSeconds;
      this.maximumLevelSettlingTimeSeconds = maximumLevelSettlingTimeSeconds;
      this.minimumValveOpeningPercent = minimumValveOpeningPercent;
      this.maximumValveOpeningPercent = maximumValveOpeningPercent;
      this.maximumComponentResidualMol = maximumComponentResidualMol;
      this.maximumRelativeVolumeResidual = maximumRelativeVolumeResidual;
      this.maximumRelativeCumulativeEnergyResidual = maximumRelativeCumulativeEnergyResidual;
      this.maximumTrackerOracleDifferenceJ = maximumTrackerOracleDifferenceJ;
    }

    /** @return deterministic policy map with explicit units in field names */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("minimumPressureBara", Double.valueOf(minimumPressureBara));
      result.put("maximumPressureBara", Double.valueOf(maximumPressureBara));
      result.put("minimumLevelFraction", Double.valueOf(minimumLevelFraction));
      result.put("maximumLevelFraction", Double.valueOf(maximumLevelFraction));
      result.put("maximumPressureIaeBarSeconds", Double.valueOf(maximumPressureIaeBarSeconds));
      result.put("maximumLevelIaeFractionSeconds", Double.valueOf(maximumLevelIaeFractionSeconds));
      result.put("maximumPressurePeakErrorBar", Double.valueOf(maximumPressurePeakErrorBar));
      result.put("maximumLevelPeakErrorFraction", Double.valueOf(maximumLevelPeakErrorFraction));
      result.put("maximumPressureOutputTravelPercent", Double.valueOf(maximumPressureOutputTravelPercent));
      result.put("maximumLevelOutputTravelPercent", Double.valueOf(maximumLevelOutputTravelPercent));
      result.put("maximumPressureSettlingTimeSeconds", Double.valueOf(maximumPressureSettlingTimeSeconds));
      result.put("maximumLevelSettlingTimeSeconds", Double.valueOf(maximumLevelSettlingTimeSeconds));
      result.put("minimumValveOpeningPercent", Double.valueOf(minimumValveOpeningPercent));
      result.put("maximumValveOpeningPercent", Double.valueOf(maximumValveOpeningPercent));
      result.put("maximumComponentResidualMol", Double.valueOf(maximumComponentResidualMol));
      result.put("maximumRelativeVolumeResidual", Double.valueOf(maximumRelativeVolumeResidual));
      result.put("maximumRelativeCumulativeEnergyResidual", Double.valueOf(maximumRelativeCumulativeEnergyResidual));
      result.put("maximumTrackerOracleDifferenceJ", Double.valueOf(maximumTrackerOracleDifferenceJ));
      return result;
    }
  }

  /** Timestep-refinement acceptance policy. */
  public static final class RefinementLimits implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double maximumFinalPressureDifferenceBar;
    private final double maximumPeakPressureDifferenceBar;
    private final double maximumFinalLevelDifferenceFraction;
    private final double maximumPeakLevelDifferenceFraction;
    private final double maximumFinalValveDifferencePercent;

    /** Creates explicit coarse/refined difference limits. */
    public RefinementLimits(double maximumFinalPressureDifferenceBar, double maximumPeakPressureDifferenceBar,
        double maximumFinalLevelDifferenceFraction, double maximumPeakLevelDifferenceFraction,
        double maximumFinalValveDifferencePercent) {
      double[] values = {maximumFinalPressureDifferenceBar, maximumPeakPressureDifferenceBar,
          maximumFinalLevelDifferenceFraction, maximumPeakLevelDifferenceFraction, maximumFinalValveDifferencePercent};
      for (double value : values) {
        if (!Double.isFinite(value) || value < 0.0) {
          throw new IllegalArgumentException("Refinement limits must be finite and nonnegative");
        }
      }
      this.maximumFinalPressureDifferenceBar = maximumFinalPressureDifferenceBar;
      this.maximumPeakPressureDifferenceBar = maximumPeakPressureDifferenceBar;
      this.maximumFinalLevelDifferenceFraction = maximumFinalLevelDifferenceFraction;
      this.maximumPeakLevelDifferenceFraction = maximumPeakLevelDifferenceFraction;
      this.maximumFinalValveDifferencePercent = maximumFinalValveDifferencePercent;
    }
  }

  /** One accepted native physical-step sample. */
  public static final class Sample implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int stepIndex;
    private final double startTimeSeconds;
    private final double endTimeSeconds;
    private final UUID physicalStepId;
    private final double inletFlowKgPerSecond;
    private final double pressureBara;
    private final double pressureSampleBara;
    private final double levelFraction;
    private final double levelSampleFraction;
    private final double pressureValveOpeningPercent;
    private final double levelValveOpeningPercent;
    private final double relativeCumulativeEnergyResidual;
    private final double relativeVolumeResidual;

    /** Stores one immutable trace row. */
    private Sample(int stepIndex, double startTimeSeconds, double endTimeSeconds, UUID physicalStepId,
        double inletFlowKgPerSecond, double pressureBara, double pressureSampleBara, double levelFraction,
        double levelSampleFraction, double pressureValveOpeningPercent, double levelValveOpeningPercent,
        double relativeCumulativeEnergyResidual, double relativeVolumeResidual) {
      this.stepIndex = stepIndex;
      this.startTimeSeconds = startTimeSeconds;
      this.endTimeSeconds = endTimeSeconds;
      this.physicalStepId = physicalStepId;
      this.inletFlowKgPerSecond = inletFlowKgPerSecond;
      this.pressureBara = pressureBara;
      this.pressureSampleBara = pressureSampleBara;
      this.levelFraction = levelFraction;
      this.levelSampleFraction = levelSampleFraction;
      this.pressureValveOpeningPercent = pressureValveOpeningPercent;
      this.levelValveOpeningPercent = levelValveOpeningPercent;
      this.relativeCumulativeEnergyResidual = relativeCumulativeEnergyResidual;
      this.relativeVolumeResidual = relativeVolumeResidual;
    }

    /** @return deterministic trace map */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("stepIndex", Integer.valueOf(stepIndex));
      result.put("startTimeSeconds", Double.valueOf(startTimeSeconds));
      result.put("endTimeSeconds", Double.valueOf(endTimeSeconds));
      result.put("physicalStepId", physicalStepId.toString());
      result.put("inletFlowKgPerSecond", Double.valueOf(inletFlowKgPerSecond));
      result.put("pressureBara", Double.valueOf(pressureBara));
      result.put("pressureSampleBara", Double.valueOf(pressureSampleBara));
      result.put("levelFraction", Double.valueOf(levelFraction));
      result.put("levelSampleFraction", Double.valueOf(levelSampleFraction));
      result.put("pressureValveOpeningPercent", Double.valueOf(pressureValveOpeningPercent));
      result.put("levelValveOpeningPercent", Double.valueOf(levelValveOpeningPercent));
      result.put("relativeCumulativeEnergyResidual", Double.valueOf(relativeCumulativeEnergyResidual));
      result.put("relativeVolumeResidual", Double.valueOf(relativeVolumeResidual));
      return result;
    }
  }

  /** One explicit observed-versus-limit comparison. */
  public static final class Check implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final double observed;
    private final Double minimum;
    private final Double maximum;
    private final String unit;
    private final boolean passed;

    /** Stores one criterion result. */
    private Check(String id, double observed, Double minimum, Double maximum, String unit, boolean passed) {
      this.id = id;
      this.observed = observed;
      this.minimum = minimum;
      this.maximum = maximum;
      this.unit = unit;
      this.passed = passed;
    }

    /** @return deterministic criterion map */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("observed", Double.valueOf(observed));
      result.put("minimum", minimum);
      result.put("maximum", maximum);
      result.put("unit", unit);
      result.put("passed", Boolean.valueOf(passed));
      return result;
    }

    /** @return criterion verdict */
    public boolean isPassed() {
      return passed;
    }
  }

  /** Immutable executed study result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Definition definition;
    private final Limits limits;
    private final Map<String, Object> configuration;
    private final String configurationFingerprint;
    private final List<Sample> samples;
    private final Map<String, Check> checks;
    private final double pressureIaeBarSeconds;
    private final double pressurePeakErrorBar;
    private final double pressureOutputTravelPercent;
    private final double pressureSettlingTimeSeconds;
    private final double levelIaeFractionSeconds;
    private final double levelPeakErrorFraction;
    private final double levelOutputTravelPercent;
    private final double levelSettlingTimeSeconds;
    private final double minimumPressureBara;
    private final double maximumPressureBara;
    private final double minimumLevelFraction;
    private final double maximumLevelFraction;
    private final double minimumPressureValveOpeningPercent;
    private final double maximumPressureValveOpeningPercent;
    private final double minimumLevelValveOpeningPercent;
    private final double maximumLevelValveOpeningPercent;
    private final double maximumComponentResidualMol;
    private final double maximumRelativeVolumeResidual;
    private final double independentEnergyOracleResidual;
    private final double trackerOracleDifferenceJ;
    private final double maximumRelativeCumulativeEnergyResidual;
    private final boolean energyCoverageComplete;
    private final boolean passed;
    private final String resultFingerprint;

    /** Freezes execution evidence and computes its deterministic result fingerprint. */
    private Result(Definition definition, Limits limits, Map<String, Object> configuration,
        String configurationFingerprint, List<Sample> samples, Map<String, Check> checks,
        ControllerPerformanceMetrics pressureMetrics, ControllerPerformanceMetrics levelMetrics,
        double minimumPressureBara, double maximumPressureBara, double minimumLevelFraction,
        double maximumLevelFraction, double minimumPressureValveOpeningPercent,
        double maximumPressureValveOpeningPercent, double minimumLevelValveOpeningPercent,
        double maximumLevelValveOpeningPercent, double maximumComponentResidualMol,
        double maximumRelativeVolumeResidual, double independentEnergyOracleResidual, double trackerOracleDifferenceJ,
        SeparatorTransientEnergyBalanceReport energy) {
      this.definition = definition;
      this.limits = limits;
      this.configuration = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(configuration));
      this.configurationFingerprint = configurationFingerprint;
      this.samples = Collections.unmodifiableList(new ArrayList<Sample>(samples));
      this.checks = Collections.unmodifiableMap(new LinkedHashMap<String, Check>(checks));
      pressureIaeBarSeconds = pressureMetrics.getIntegralAbsoluteError();
      pressurePeakErrorBar = pressureMetrics.getPeakAbsoluteError();
      pressureOutputTravelPercent = pressureMetrics.getControllerOutputTravel();
      pressureSettlingTimeSeconds = pressureMetrics.getSettlingTime();
      levelIaeFractionSeconds = levelMetrics.getIntegralAbsoluteError();
      levelPeakErrorFraction = levelMetrics.getPeakAbsoluteError();
      levelOutputTravelPercent = levelMetrics.getControllerOutputTravel();
      levelSettlingTimeSeconds = levelMetrics.getSettlingTime();
      this.minimumPressureBara = minimumPressureBara;
      this.maximumPressureBara = maximumPressureBara;
      this.minimumLevelFraction = minimumLevelFraction;
      this.maximumLevelFraction = maximumLevelFraction;
      this.minimumPressureValveOpeningPercent = minimumPressureValveOpeningPercent;
      this.maximumPressureValveOpeningPercent = maximumPressureValveOpeningPercent;
      this.minimumLevelValveOpeningPercent = minimumLevelValveOpeningPercent;
      this.maximumLevelValveOpeningPercent = maximumLevelValveOpeningPercent;
      this.maximumComponentResidualMol = maximumComponentResidualMol;
      this.maximumRelativeVolumeResidual = maximumRelativeVolumeResidual;
      this.independentEnergyOracleResidual = independentEnergyOracleResidual;
      this.trackerOracleDifferenceJ = trackerOracleDifferenceJ;
      maximumRelativeCumulativeEnergyResidual = energy.getMaximumRelativeCumulativeResidual();
      energyCoverageComplete = energy.isComplete();
      boolean allPassed = true;
      for (Check check : checks.values()) {
        allPassed &= check.passed;
      }
      passed = allPassed;
      resultFingerprint = sha256(GSON.toJson(toMap(false)));
    }

    /** @return true only when every declared response and numerical check passed */
    public boolean isPassed() {
      return passed;
    }

    /** @return SHA-256 of the complete ordered result excluding the digest field itself */
    public String getResultFingerprint() {
      return resultFingerprint;
    }

    /** @return immutable accepted-step trace */
    public List<Sample> getSamples() {
      return samples;
    }

    /** @return immutable ordered criterion results */
    public Map<String, Check> getChecks() {
      return checks;
    }

    /** @return last accepted sample */
    public Sample getFinalSample() {
      return samples.get(samples.size() - 1);
    }

    /** @return JSON-compatible result including trace and fingerprints */
    public Map<String, Object> toMap() {
      return toMap(true);
    }

    /** @return pretty JSON report */
    public String toJson() {
      return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }

    /** Builds a result map with optional digest field. */
    private Map<String, Object> toMap(boolean includeFingerprint) {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_pid_separator_control_result.v1");
      result.put("configuration", configuration);
      result.put("configurationFingerprint", configurationFingerprint);
      result.put("definition", definition.toMap());
      result.put("limits", limits.toMap());
      List<Map<String, Object>> trace = new ArrayList<Map<String, Object>>();
      for (Sample sample : samples) {
        trace.add(sample.toMap());
      }
      result.put("samples", trace);
      result.put("checks", checkMaps(checks));
      result.put("responseMetrics", responseMetrics());
      result.put("conservation", conservation());
      result.put("passed", Boolean.valueOf(passed));
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      result.put("fitnessForConstruction", Boolean.FALSE);
      if (includeFingerprint) {
        result.put("resultFingerprint", resultFingerprint);
      }
      return result;
    }

    /** Returns response extrema and controller KPIs. */
    private Map<String, Object> responseMetrics() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("minimumPressureBara", Double.valueOf(minimumPressureBara));
      result.put("maximumPressureBara", Double.valueOf(maximumPressureBara));
      result.put("minimumLevelFraction", Double.valueOf(minimumLevelFraction));
      result.put("maximumLevelFraction", Double.valueOf(maximumLevelFraction));
      result.put("minimumPressureValveOpeningPercent", Double.valueOf(minimumPressureValveOpeningPercent));
      result.put("maximumPressureValveOpeningPercent", Double.valueOf(maximumPressureValveOpeningPercent));
      result.put("minimumLevelValveOpeningPercent", Double.valueOf(minimumLevelValveOpeningPercent));
      result.put("maximumLevelValveOpeningPercent", Double.valueOf(maximumLevelValveOpeningPercent));
      result.put("pressureIaeBarSeconds", Double.valueOf(pressureIaeBarSeconds));
      result.put("pressurePeakErrorBar", Double.valueOf(pressurePeakErrorBar));
      result.put("pressureOutputTravelPercent", Double.valueOf(pressureOutputTravelPercent));
      result.put("pressureSettlingTimeSeconds", Double.valueOf(pressureSettlingTimeSeconds));
      result.put("levelIaeFractionSeconds", Double.valueOf(levelIaeFractionSeconds));
      result.put("levelPeakErrorFraction", Double.valueOf(levelPeakErrorFraction));
      result.put("levelOutputTravelPercent", Double.valueOf(levelOutputTravelPercent));
      result.put("levelSettlingTimeSeconds", Double.valueOf(levelSettlingTimeSeconds));
      return result;
    }

    /** Returns conservation, energy-coverage and independent-oracle evidence. */
    private Map<String, Object> conservation() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("maximumComponentResidualMol", Double.valueOf(maximumComponentResidualMol));
      result.put("maximumRelativeVolumeResidual", Double.valueOf(maximumRelativeVolumeResidual));
      result.put("maximumRelativeCumulativeEnergyResidual", Double.valueOf(maximumRelativeCumulativeEnergyResidual));
      result.put("independentEnergyOracleResidual", Double.valueOf(independentEnergyOracleResidual));
      result.put("trackerOracleDifferenceJ", Double.valueOf(trackerOracleDifferenceJ));
      result.put("energyCoverageComplete", Boolean.valueOf(energyCoverageComplete));
      return result;
    }
  }

  /** Immutable coarse/refined comparison. */
  public static final class RefinementResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String coarseResultFingerprint;
    private final String refinedResultFingerprint;
    private final Map<String, Check> checks;
    private final boolean passed;

    /** Stores one deterministic refinement comparison. */
    private RefinementResult(String coarseResultFingerprint, String refinedResultFingerprint,
        Map<String, Check> checks) {
      this.coarseResultFingerprint = coarseResultFingerprint;
      this.refinedResultFingerprint = refinedResultFingerprint;
      this.checks = Collections.unmodifiableMap(new LinkedHashMap<String, Check>(checks));
      boolean allPassed = true;
      for (Check check : checks.values()) {
        allPassed &= check.passed;
      }
      passed = allPassed;
    }

    /** @return true when every declared sensitivity comparison passed */
    public boolean isPassed() {
      return passed;
    }

    /** @return deterministic comparison map */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_pid_separator_control_refinement.v1");
      result.put("coarseResultFingerprint", coarseResultFingerprint);
      result.put("refinedResultFingerprint", refinedResultFingerprint);
      result.put("checks", checkMaps(checks));
      result.put("passed", Boolean.valueOf(passed));
      return result;
    }
  }

  /** Requires nonblank text. */
  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  /** Requires a positive finite number. */
  private static void requirePositiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /** Requires an ordered finite range. */
  private static void requireFiniteRange(double minimum, double maximum, String name) {
    if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum) {
      throw new IllegalArgumentException(name + " range must be finite and ordered");
    }
  }
}
