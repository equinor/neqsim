package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.operations.ControllerTuningResult;
import neqsim.process.operations.ControllerTuningStudy;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Runs source-bound PID candidate studies on independent copies of a real two-circuit thermal process.
 *
 * <p>
 * The declared target, sample time, horizon, final hold window, tolerance and candidate gains are study inputs. Each
 * candidate executes the native controller, actual water valve hydraulics and conservative fluid/wall dynamics. Raw
 * failures and traces remain visible. A recommendation is the lowest-IAE candidate satisfying the declared hold and
 * thermal-balance limits; it is neither a globally optimal tuning nor hardware/plant qualification.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class HeatExchangerControllerTuning {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /** Prevents utility construction. */
  private HeatExchangerControllerTuning() {
  }

  /**
   * Executes every declared candidate without changing the governed source model or its controller history.
   *
   * @param project current governed project
   * @param pid actual canonical control graph
   * @param bindings explicit current native object bindings
   * @param definition finite, bounded study inputs
   * @return immutable traces, candidate verdicts and an optional recommendation
   */
  public static Result tune(EngineeringProject project, PidDesignModel pid, PidSimulationBindings bindings,
      Definition definition) {
    if (project == null || pid == null || bindings == null || definition == null) {
      throw new IllegalArgumentException("Project, canonical model, bindings and study definition are required");
    }
    bindings.assertCurrent(project, pid);
    PidNativeControlSystem.validateConfigured(project, pid, bindings, project.getEngineeringProcessSystem().copy());
    Runtime source = resolve(project.getEngineeringProcessSystem(), pid, bindings, definition.controllerTag);
    String basis = basisFingerprint(project, pid, source);
    String sourcePolicy = GSON.toJson(source.controller.getControlConfiguration());
    List<Map<String, Object>> candidates = new ArrayList<Map<String, Object>>();
    double bestError = Double.POSITIVE_INFINITY;
    int best = -1;
    for (int candidate = 0; candidate < definition.parameters.length; candidate++) {
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("candidateIndex", candidate);
      row.put("Kp", definition.parameters[candidate][0]);
      row.put("TiSeconds", definition.parameters[candidate][1]);
      row.put("TdSeconds", definition.parameters[candidate][2]);
      List<Map<String, Object>> trace = new ArrayList<Map<String, Object>>();
      row.put("samples", trace);
      try {
        ProcessSystem process = project.getEngineeringProcessSystem().copy();
        PidNativeControlSystem.applyCanonicalConfiguration(project, pid, bindings, process);
        Runtime runtime = resolve(process, pid, bindings, definition.controllerTag);
        runtime.controller.setControllerParameters(definition.parameters[candidate][0],
            definition.parameters[candidate][1], definition.parameters[candidate][2]);
        runtime.controller.setControllerSetPoint(definition.targetC, "C");
        runtime.controller.resetPerformanceMetrics();
        int count = definition.stepCount();
        double[] times = new double[count + 1];
        double[] values = new double[count + 1];
        double[] openings = new double[count + 1];
        double maximumEnergyResidual = 0.0;
        boolean finalWindowHeld = true;
        for (int step = 0; step <= count; step++) {
          if (step > 0) {
            process.runTransient(definition.timeStepSeconds,
                UUID.nameUUIDFromBytes((basis + ":" + candidate + ":" + step).getBytes(StandardCharsets.UTF_8)));
          }
          times[step] = step * definition.timeStepSeconds;
          values[step] = runtime.exchanger.getOutStream(0).getTemperature("C");
          openings[step] = runtime.valve.getPercentValveOpening();
          if (!Double.isFinite(values[step]) || !Double.isFinite(openings[step]) || openings[step] < 5.0
              || openings[step] > 100.0) {
            throw new IllegalStateException("Non-finite response or actuator outside declared 5-100% range");
          }
          double energyResidual = step == 0 ? 0.0 : runtime.exchanger.getLastThermalBalance().getResidualJ();
          if (!Double.isFinite(energyResidual)) {
            throw new IllegalStateException("Non-finite thermal first-law residual");
          }
          maximumEnergyResidual = Math.max(maximumEnergyResidual, Math.abs(energyResidual));
          if (times[step] >= definition.durationSeconds - definition.holdSeconds) {
            finalWindowHeld &= Math.abs(values[step] - definition.targetC) <= definition.toleranceC;
          }
          Map<String, Object> sample = new LinkedHashMap<String, Object>();
          sample.put("timeSeconds", times[step]);
          sample.put("gasOutletC", values[step]);
          sample.put("waterFlowKgPerSecond", runtime.exchanger.getInStream(1).getFlowRate("kg/sec"));
          sample.put("valveOpeningPercent", openings[step]);
          sample.put("wallTemperatureK", runtime.exchanger.getWallTemperature());
          sample.put("thermalResidualJ", energyResidual);
          trace.add(sample);
        }
        ControllerTuningResult metrics = ControllerTuningStudy.evaluateStepResponse(definition.controllerTag,
            definition.targetC, times, values, openings, 5.0, 100.0, definition.toleranceC);
        row.put("integralAbsoluteErrorCSeconds", metrics.getIntegralAbsoluteError());
        row.put("integralSquaredError", metrics.getIntegralSquaredError());
        row.put("overshootPercent", metrics.getOvershootPercent());
        row.put("settlingTimeSeconds",
            Double.isFinite(metrics.getSettlingTimeSeconds()) ? Double.valueOf(metrics.getSettlingTimeSeconds())
                : null);
        row.put("outputSaturationFraction", metrics.getOutputSaturationFraction());
        row.put("maximumThermalResidualJ", maximumEnergyResidual);
        row.put("finalWindowHeld", finalWindowHeld);
        boolean passed = finalWindowHeld && maximumEnergyResidual <= definition.maximumThermalResidualJ;
        row.put("status", passed ? "EXECUTED_PASS" : "EXECUTED_FAIL");
        if (passed && metrics.getIntegralAbsoluteError() < bestError) {
          bestError = metrics.getIntegralAbsoluteError();
          best = candidate;
        }
      } catch (RuntimeException ex) {
        row.put("status", "EXECUTION_FAILED");
        row.put("failure", ex.getClass().getName() + ": " + ex.getMessage());
      }
      candidates.add(row);
    }
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("schemaVersion", "neqsim_heat_exchanger_pid_tuning.v1");
    report.put("projectId", project.getProjectId());
    report.put("sourceRevision", project.getRevision());
    report.put("sourcePidFingerprint", bindings.toMap().get("pidFingerprint"));
    report.put("sourceBindings", bindings.toMap());
    report.put("plantBasisFingerprint", basis);
    report.put("basisScope", "FULL_SERIALIZED_RUNTIME_SAME_BUILD; ONLY_SELECTED_PID_GAINS_AND_SETPOINT_NORMALIZED");
    report.put("controllerTag", definition.controllerTag);
    report.put("exchangerTag", source.exchanger.getName());
    report.put("utilityValveTag", source.valve.getName());
    report.put("measurementTag", source.measurement.getName());
    report.put("definition", definition.toMap());
    report.put("candidates", candidates);
    report.put("recommendedCandidateIndex", best < 0 ? null : Integer.valueOf(best));
    report.put("status", best < 0 ? "NO_ACCEPTABLE_CANDIDATE" : "TESTED_RECOMMENDATION");
    report.put("qualification", "DECLARED_LUMPED_THERMAL_CASE_ONLY; TIMESTEP_AND_PLANT_QUALIFICATION_SEPARATE");
    report.put("fitnessForConstruction", Boolean.FALSE);
    return new Result(GSON.toJson(report), basis, sourcePolicy, definition.controllerTag, definition.targetC,
        best < 0 ? null : definition.parameters[best]);
  }

  /**
   * Applies a tested recommendation only to the unchanged source basis, under an explicit new project revision.
   *
   * @param project source project to revise
   * @param pid current pre-tuning canonical model
   * @param bindings current pre-tuning bindings
   * @param result tested source-bound recommendation
   * @param revision explicit new revision, different from the current revision
   * @return freshly imported canonical graph; rebuild bindings before further work
   */
  public static PidDesignModel applyRecommendation(EngineeringProject project, PidDesignModel pid,
      PidSimulationBindings bindings, Result result, String revision) {
    if (result == null || !result.hasRecommendation() || revision == null || revision.trim().isEmpty()
        || revision.equals(project.getRevision())) {
      throw new IllegalArgumentException("A tested recommendation and explicit new revision are required");
    }
    bindings.assertCurrent(project, pid);
    Runtime runtime = resolve(project.getEngineeringProcessSystem(), pid, bindings, result.controllerTag);
    if (!basisFingerprint(project, pid, runtime).equals(result.basis)
        || !GSON.toJson(runtime.controller.getControlConfiguration()).equals(result.sourcePolicy)) {
      throw new IllegalArgumentException("Tuning evidence is stale for the current source model or controller policy");
    }
    runtime.controller.setControllerParameters(result.parameters[0], result.parameters[1], result.parameters[2]);
    runtime.controller.setControllerSetPoint(result.targetC, "C");
    project.setRevision(revision);
    return PidNativeControlSystem.fromProcess(project);
  }

  /**
   * Binds a report to actual currently exported settings, without claiming XML-only PLC parameter transfer.
   *
   * @param project current project
   * @param pid freshly imported current canonical graph
   * @param bindings current native bindings
   * @param result executed source-bound study
   * @return detached export receipt including applied/proposed policy readback
   */
  public static Map<String, Object> exportEvidence(EngineeringProject project, PidDesignModel pid,
      PidSimulationBindings bindings, Result result) {
    bindings.assertCurrent(project, pid);
    Runtime runtime = resolve(project.getEngineeringProcessSystem(), pid, bindings, result.controllerTag);
    if (!basisFingerprint(project, pid, runtime).equals(result.basis)) {
      throw new IllegalArgumentException("Thermal process or instrumentation changed after the tuning study");
    }
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("study", JsonParser.parseString(result.json));
    evidence.put("exportRevision", project.getRevision());
    evidence.put("exportPidFingerprint", bindings.toMap().get("pidFingerprint"));
    evidence.put("actualControllerConfiguration", runtime.controller.getControlConfiguration());
    boolean applied = result.hasRecommendation() && runtime.controller.getKp() == result.parameters[0]
        && runtime.controller.getTi() == result.parameters[1] && runtime.controller.getTd() == result.parameters[2]
        && runtime.controller.getControllerSetPoint() == result.targetC;
    evidence.put("applicationStatus", applied ? "MATCHES_TESTED_CANDIDATE" : "RECOMMENDATION_NOT_APPLIED");
    evidence.put("parameterTransfer", "NEQSIM_CANONICAL_SIDECAR_AND_SAVED_NATIVE_MODEL; NOT_XML_ONLY_PLC_TRANSFER");
    return evidence;
  }

  /**
   * Resolves the exact TT-controller-valve-exchanger causal path on a source or isolated process.
   *
   * @param process actual process instance
   * @param pid current graph
   * @param bindings authoritative object associations
   * @param controllerTag canonical controller tag
   * @return checked actual native objects
   */
  private static Runtime resolve(ProcessSystem process, PidDesignModel pid, PidSimulationBindings bindings,
      String controllerTag) {
    ControllerDeviceBaseClass controller = (ControllerDeviceBaseClass) bindings.resolveController(controllerTag,
        process);
    ThrottlingValve valve = null;
    HeatExchanger exchanger = null;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof ThrottlingValve && unit.getController() == controller) {
        if (valve != null) {
          throw new IllegalArgumentException("Controller must have one actual utility valve target");
        }
        valve = (ThrottlingValve) unit;
      }
    }
    if (valve != null) {
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        if (unit.getClass() == HeatExchanger.class && ((HeatExchanger) unit).isDynamicModelEnabled()
            && ((HeatExchanger) unit).getInStream(1) == valve.getOutletStream()) {
          if (exchanger != null) {
            throw new IllegalArgumentException("Utility valve must have one actual exchanger consumer");
          }
          exchanger = (HeatExchanger) unit;
        }
      }
    }
    if (controller == null || valve == null || exchanger == null
        || !(controller.getTransmitter() instanceof TemperatureTransmitter)
        || ((TemperatureTransmitter) controller.getTransmitter()).getStream() != exchanger.getOutStream(0)
        || valve.getCalculateSteadyState() || controller.isReverseActing() || !"C".equals(controller.getUnit())) {
      throw new IllegalArgumentException(
          "An actual Celsius TT-to-PID-to-utility-valve-to-dynamic-exchanger loop is required");
    }
    Map<String, Object> policy = controller.getControlConfiguration();
    if (!controller.isActive() || !"AUTO".equals(policy.get("mode"))
        || !((List<?>) policy.get("gainSchedule")).isEmpty() || !Double.valueOf(5.0).equals(policy.get("minimumOutput"))
        || !Double.valueOf(100.0).equals(policy.get("maximumOutput")) || valve.getMinimumValveOpening() != 5.0
        || valve.getMaximumValveOpening() != 100.0) {
      throw new IllegalArgumentException(
          "Candidate tuning requires active AUTO, fixed gains and 5-100% PID/valve limits");
    }
    TemperatureTransmitter measurement = (TemperatureTransmitter) controller.getTransmitter();
    bindings.requireBindings(Arrays.asList(controllerTag, valve.getName(), measurement.getName()));
    if (bindings.resolveEquipment(valve.getName(), process) != valve
        || bindings.resolveMeasurement(measurement.getName(), process) != measurement
        || !connected(pid, measurement.getName(), controllerTag) || !connected(pid, controllerTag, valve.getName())) {
      throw new IllegalArgumentException("Canonical signal graph must match the exact actual measurement and actuator");
    }
    return new Runtime(controller, valve, exchanger, measurement);
  }

  /**
   * Resolves a canonical directed signal by actual element IDs rather than similar tag spelling.
   *
   * @param pid canonical graph
   * @param source source tag
   * @param target target tag
   * @return true for a declared source-ID to target-ID connection
   */
  private static boolean connected(PidDesignModel pid, String source, String target) {
    PidElement from = null;
    PidElement to = null;
    for (PidElement element : pid.getElements()) {
      if (source.equals(element.getTag())) {
        from = element;
      }
      if (target.equals(element.getTag())) {
        to = element;
      }
    }
    return from != null && to != null && from.getConnectedElementIds().contains(to.getId());
  }

  /**
   * Freezes physical input states, actual instrument/valve policy and the initialized thermal inventory.
   *
   * @param project governed source project
   * @param pid canonical actual devices
   * @param runtime resolved causal thermal loop
   * @return SHA-256 basis excluding only the deliberately tunable controller settings and revision label
   */
  private static String basisFingerprint(EngineeringProject project, PidDesignModel pid, Runtime runtime) {
    Map<String, Object> basis = new LinkedHashMap<String, Object>();
    basis.put("projectId", project.getProjectId());
    basis.put("exchangerTag", runtime.exchanger.getName());
    basis.put("thermalConfiguration", runtime.exchanger.getThermalConfiguration());
    basis.put("thermalEnergyJ", runtime.exchanger.getThermalInventoryEnergyJ());
    basis.put("wallTemperatureK", runtime.exchanger.getWallTemperature());
    // Freeze all serialized plant, thermodynamic, PID and sampled-sensor histories.
    // Normalize only the four deliberately tuned settings on an isolated copy.
    ProcessSystem frozen = project.getEngineeringProcessSystem().copy();
    ControllerDeviceBaseClass frozenController = null;
    for (ProcessEquipmentInterface unit : frozen.getUnitOperations()) {
      if (unit.getController() != null && runtime.controller.getName().equals(unit.getController().getName())) {
        frozenController = (ControllerDeviceBaseClass) unit.getController();
      }
    }
    if (frozenController == null) {
      throw new IllegalStateException("Controlled valve missing from isolated tuning basis");
    }
    frozenController.setControllerParameters(1.0, 1.0, 0.0);
    frozenController.setControllerSetPoint(0.0, "C");
    basis.put("frozenRuntimeAndPropertyState", digest(SerializationUtils.serialize(frozen)));
    List<Object> devices = new ArrayList<Object>();
    for (PidElement element : pid.getElements()) {
      if (element.getType() != PidElementType.CONTROLLER) {
        devices.add(element.toMap());
      }
    }
    basis.put("actualDevicesAndPolicies", devices);
    List<Object> streams = new ArrayList<Object>();
    for (StreamInterface stream : Arrays.asList(runtime.exchanger.getInStream(0), runtime.exchanger.getInStream(1),
        runtime.exchanger.getOutStream(0), runtime.exchanger.getOutStream(1), runtime.valve.getInletStream())) {
      Map<String, Object> state = new LinkedHashMap<String, Object>();
      state.put("tag", stream.getName());
      state.put("fluidClass", stream.getFluid().getClass().getName());
      state.put("temperatureK", stream.getTemperature());
      state.put("pressureBara", stream.getPressure("bara"));
      state.put("flowKgPerSecond", stream.getFlowRate("kg/sec"));
      List<Double> composition = new ArrayList<Double>();
      for (int component = 0; component < stream.getFluid().getNumberOfComponents(); component++) {
        composition.add(stream.getFluid().getComponent(component).getz());
      }
      state.put("moleFractions", composition);
      streams.add(state);
    }
    basis.put("streams", streams);
    basis.put("valveOpeningPercent", runtime.valve.getPercentValveOpening());
    return digest(GSON.toJson(basis).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Hashes an exact detached state representation without exposing its binary content.
   *
   * @param bytes deterministic state representation
   * @return SHA-256 hexadecimal digest
   */
  private static String digest(byte[] bytes) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder result = new StringBuilder();
      for (byte value : hash) {
        result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Required SHA-256 digest is unavailable", ex);
    }
  }

  /**
   * Resolved runtime references are local to one study and never escape to callers.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Runtime {
    private final ControllerDeviceBaseClass controller;
    private final ThrottlingValve valve;
    private final HeatExchanger exchanger;
    private final TemperatureTransmitter measurement;

    /**
     * Holds the exact causal objects for one isolated process.
     *
     * @param controller actual native PID
     * @param valve actual utility valve
     * @param exchanger actual conservative thermal equipment
     * @param measurement actual controlled outlet TT
     */
    private Runtime(ControllerDeviceBaseClass controller, ThrottlingValve valve, HeatExchanger exchanger,
        TemperatureTransmitter measurement) {
      this.controller = controller;
      this.valve = valve;
      this.exchanger = exchanger;
      this.measurement = measurement;
    }
  }

  /**
   * Explicit finite candidate search and dynamic acceptance window.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Definition {
    private final String controllerTag;
    private final double targetC;
    private final double timeStepSeconds;
    private final double durationSeconds;
    private final double holdSeconds;
    private final double toleranceC;
    private final double maximumThermalResidualJ;
    private final double[][] parameters;

    /**
     * Defines a temperature setpoint-step study with caller-selected candidate PID settings.
     *
     * @param tag canonical actual controller tag
     * @param target outlet-temperature target in C
     * @param timeStep positive physical timestep in seconds
     * @param duration simulation horizon in seconds, an integral number of steps
     * @param finalHold required final in-tolerance window in seconds
     * @param tolerance positive temperature tolerance in C
     * @param maximumResidual maximum accepted interval thermal residual in J
     * @param candidates finite positive Kp/Ti and nonnegative Td rows; at most 100 candidates
     */
    public Definition(String tag, double target, double timeStep, double duration, double finalHold, double tolerance,
        double maximumResidual, double[][] candidates) {
      if (tag == null || tag.trim().isEmpty() || !Double.isFinite(target) || !Double.isFinite(timeStep) || timeStep <= 0
          || !Double.isFinite(duration) || duration < timeStep || duration / timeStep > 10000
          || Math.abs(duration / timeStep - Math.rint(duration / timeStep)) > 1e-8 || !Double.isFinite(finalHold)
          || finalHold < timeStep || finalHold > duration || !Double.isFinite(tolerance) || tolerance <= 0
          || !Double.isFinite(maximumResidual) || maximumResidual <= 0 || candidates == null || candidates.length == 0
          || candidates.length > 100) {
        throw new IllegalArgumentException(
            "Finite bounded study timing, hold/tolerance and candidate inputs are required");
      }
      parameters = new double[candidates.length][];
      for (int row = 0; row < candidates.length; row++) {
        if (candidates[row] == null || candidates[row].length != 3 || !Double.isFinite(candidates[row][0])
            || candidates[row][0] <= 0 || !Double.isFinite(candidates[row][1]) || candidates[row][1] <= 0
            || !Double.isFinite(candidates[row][2]) || candidates[row][2] < 0) {
          throw new IllegalArgumentException("Each candidate requires positive Kp/Ti and nonnegative Td");
        }
        parameters[row] = candidates[row].clone();
      }
      controllerTag = tag;
      targetC = target;
      timeStepSeconds = timeStep;
      durationSeconds = duration;
      holdSeconds = finalHold;
      toleranceC = tolerance;
      maximumThermalResidualJ = maximumResidual;
    }

    /**
     * Calculates the validated horizon length.
     *
     * @return integral number of physical simulation intervals
     */
    private int stepCount() {
      return (int) Math.rint(durationSeconds / timeStepSeconds);
    }

    /**
     * Exports the declared search and acceptance inputs.
     *
     * @return detached, unit-explicit study inputs
     */
    public Map<String, Object> toMap() {
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("targetC", targetC);
      values.put("timeStepSeconds", timeStepSeconds);
      values.put("durationSeconds", durationSeconds);
      values.put("finalHoldSeconds", holdSeconds);
      values.put("toleranceC", toleranceC);
      values.put("maximumThermalResidualJ", maximumThermalResidualJ);
      values.put("candidateCount", parameters.length);
      return values;
    }
  }

  /**
   * Immutable executed candidate evidence and optional source-bound recommendation.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String json;
    private final String basis;
    private final String sourcePolicy;
    private final String controllerTag;
    private final double targetC;
    private final double[] parameters;

    /**
     * Freezes raw study evidence without retaining a mutable process or canonical graph.
     *
     * @param json full study and candidate traces
     * @param basis input basis fingerprint
     * @param sourcePolicy source controller policy before candidate testing
     * @param tag actual canonical controller tag
     * @param target controlled study target in C
     * @param recommendation selected PID row, or null when every candidate failed
     */
    private Result(String json, String basis, String sourcePolicy, String tag, double target, double[] recommendation) {
      this.json = json;
      this.basis = basis;
      this.sourcePolicy = sourcePolicy;
      controllerTag = tag;
      targetC = target;
      parameters = recommendation == null ? null : recommendation.clone();
    }

    /**
     * Reports whether any tested candidate met the declared acceptance criteria.
     *
     * @return true only when at least one candidate passed the final hold and thermal checks
     */
    public boolean hasRecommendation() {
      return parameters != null;
    }

    /**
     * Reads the best accepted candidate without exposing mutable study state.
     *
     * @return detached recommended Kp, Ti seconds and Td seconds
     * @throws IllegalStateException if no candidate met the acceptance criteria
     */
    public double[] getRecommendedParameters() {
      if (parameters == null) {
        throw new IllegalStateException("No candidate satisfied the declared study acceptance limits");
      }
      return parameters.clone();
    }

    /**
     * Exports all raw candidate outcomes and traces.
     *
     * @return complete JSON evidence including every executed or failed candidate
     */
    public String toJson() {
      return json;
    }
  }
}
