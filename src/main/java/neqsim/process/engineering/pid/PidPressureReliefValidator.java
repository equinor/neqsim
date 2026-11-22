package neqsim.process.engineering.pid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;

/**
 * Binds passive relief configuration, actual routes and executed sampled evidence to canonical P&amp;ID identities.
 *
 * <p>
 * This contract supports an exact native SafetyReliefValve on a real two-outlet splitter. It does not establish
 * certified capacity, flare adequacy, installed independence, SIL, continuous safety or standards approval. A required
 * criterion FAIL remains an observed FAIL.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PidPressureReliefValidator {
  /** Prevents utility construction. */
  private PidPressureReliefValidator() {
  }

  /** Detached immutable native-copy configuration receipt. */
  public static final class ConfiguredRelief {
    /** Frozen JSON including canonical identity and actual configuration. */
    private final String json;
    /** Content fingerprint. */
    private final String fingerprint;

    /**
     * Freezes validated readback.
     * 
     * @param values detached source/configuration values
     */
    private ConfiguredRelief(Map<String, Object> values) {
      json = new Gson().toJson(values);
      fingerprint = sha256(json);
    }

    /** @return exact validated configuration SHA-256 */
    public String getFingerprint() {
      return fingerprint;
    }

    /** @return required scenario evidence reference */
    public String getEvidenceReference() {
      return "pid-pressure-relief-config-sha256:" + fingerprint;
    }

    /** @return detached receipt and explicit approval limits */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap() {
      Map<String, Object> map = new Gson().fromJson(json, Map.class);
      map.put("configurationFingerprint", fingerprint);
      return map;
    }

    /**
     * Rejects stale factories or changed canonical/native settings.
     * 
     * @param actual repeated readback inside the actual isolated scenario factory
     * @throws IllegalStateException if configuration changed
     */
    public void assertSameConfiguration(ConfiguredRelief actual) {
      if (actual == null || !fingerprint.equals(actual.fingerprint)) {
        throw new IllegalStateException("Configured passive relief differs from the frozen scenario definition");
      }
    }
  }

  /**
   * Applies supported canonical mechanical settings to the exact valve in an isolated process copy.
   *
   * <p>
   * Only scalar pressure/lift/Cv settings are applied. Other native hydraulic settings must already equal the governed
   * readback and cannot be silently changed. Revalidate before execution.
   * </p>
   *
   * @param project governed source project
   * @param model canonical proposal graph
   * @param bindings fresh bindings for this graph
   * @param process actual isolated configured copy, never the project source
   * @param reliefTag canonical relief tag
   * @return validated configuration after exact native setter/readback
   * @throws IllegalArgumentException if settings, native type, route or readback disagree
   */
  public static ConfiguredRelief applyCanonicalConfiguration(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem process, String reliefTag) {
    return applyCanonicalConfiguration(project, model, bindings, process, reliefTag,
        Collections.<String, Object>emptyMap(), null);
  }

  /**
   * Applies the healthy canonical settings plus explicit bounded case faults or backpressure disturbance.
   * 
   * @param project governed source project
   * @param model canonical proposal graph
   * @param bindings current source/graph bindings
   * @param process pristine isolated native copy
   * @param reliefTag canonical relief tag
   * @param configurationOverrides declared motion/capacity/response faults, never silently inferred
   * @param backpressureOverrideBara optional explicit case boundary disturbance
   * @return exact case-specific configured receipt
   * @throws IllegalArgumentException if a fault key or native readback is unsupported
   */
  public static ConfiguredRelief applyCanonicalConfiguration(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem process, String reliefTag,
      Map<String, Object> configurationOverrides, Double backpressureOverrideBara) {
    bindings.assertCurrent(project, model);
    if (process == project.getEngineeringProcessSystem()) {
      throw new IllegalArgumentException("Canonical settings must be applied to an isolated copy");
    }
    PidElement element = byTag(model, reliefTag);
    SafetyReliefValve valve = boundValve(bindings, process, reliefTag);
    if (valve.getResponseElapsedSeconds() != 0.0) {
      throw new IllegalArgumentException("Canonical relief configuration requires a pristine isolated response state");
    }
    Map<?, ?> c = overriddenConfiguration(map(attribute(element, "simulationPressureReliefConfiguration")),
        configurationOverrides);
    if (backpressureOverrideBara != null) {
      valve.setOutletPressure(positiveBoundary(backpressureOverrideBara));
    }
    if (!"bara".equals(c.get("pressureUnit")) || !"US".equals(c.get("cvUnit"))) {
      throw new IllegalArgumentException("Pressure and capacity settings require explicit bara and US Cv units");
    }
    valve.setSetPressureBar(number(c, "setPressureBar"));
    valve.setOverpressureFrac(number(c, "overpressureFraction"));
    valve.setBlowdownFrac(number(c, "blowdownFraction"));
    valve.setRatedCv(number(c, "ratedCv"));
    valve.setKd(number(c, "kd"));
    valve.setKbMax(number(c, "kbMax"));
    valve.setBackpressureSensitivity(number(c, "backpressureSensitivity"));
    valve.setMinStableOpenFrac(number(c, "minimumStableCommandFraction"));
    valve.setValveType(SafetyReliefValve.ValveType.valueOf(String.valueOf(c.get("valveType"))));
    valve.setOpeningLaw(SafetyReliefValve.OpeningLaw.valueOf(String.valueOf(c.get("openingLaw"))));
    valve.setTauOpenSec(number(c, "tauOpenSeconds"));
    valve.setTauCloseSec(number(c, "tauCloseSeconds"));
    valve.setMinOpenTimeSec(number(c, "minimumOpenDwellSeconds"));
    valve.setMinCloseTimeSec(number(c, "minimumClosedDwellSeconds"));
    valve.setMaxLiftRatePerSec(number(c, "maximumLiftRatePerSecond"));
    valve.setSeatToleranceFraction(number(c, "seatToleranceFraction"));
    if (!(c.get("liftMotionAvailable") instanceof Boolean)) {
      throw new IllegalArgumentException("Explicit native lift-motion availability is required");
    }
    valve.setLiftMotionAvailable(((Boolean) c.get("liftMotionAvailable")).booleanValue());
    valve.initializeLiftState(number(c, "initialLiftFraction"));
    return validateConfigured(project, model, bindings, process, reliefTag, configurationOverrides,
        backpressureOverrideBara);
  }

  /**
   * Validates the exact actual valve, mechanical readback, registered basis and physical branch.
   *
   * @param project governed source project
   * @param model canonical proposal graph
   * @param bindings fresh source/graph bindings
   * @param process actual configured process copy
   * @param reliefTag canonical relief tag
   * @return immutable receipt containing no live object references
   * @throws IllegalArgumentException if any governed/native identity or value differs
   */
  public static ConfiguredRelief validateConfigured(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem process, String reliefTag) {
    return validateConfigured(project, model, bindings, process, reliefTag, Collections.<String, Object>emptyMap(),
        null);
  }

  /**
   * Validates separately declared case faults while preserving the healthy canonical definition.
   * 
   * @param project governed project
   * @param model canonical proposal graph
   * @param bindings current source/graph bindings
   * @param process configured isolated copy
   * @param reliefTag canonical relief tag
   * @param configurationOverrides declared bounded mechanical case faults
   * @param backpressureOverrideBara optional explicit case boundary disturbance
   * @return exact case-specific receipt including override values
   * @throws IllegalArgumentException if actual values do not match the declared design plus overrides
   */
  public static ConfiguredRelief validateConfigured(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem process, String reliefTag,
      Map<String, Object> configurationOverrides, Double backpressureOverrideBara) {
    bindings.assertCurrent(project, model);
    PidElement element = byTag(model, reliefTag);
    if (element.getType() != PidElementType.SAFETY_RELIEF_VALVE || !element.getConnectedElementIds().isEmpty()) {
      throw new IllegalArgumentException("Passive relief requires a relief-valve proposal without PLC signal edges");
    }
    for (PidElement candidate : model.getElements()) {
      if (candidate.getConnectedElementIds().contains(element.getId())) {
        throw new IllegalArgumentException("Passive relief cannot acquire an incoming PLC signal edge");
      }
    }
    SafetyReliefValve valve = boundValve(bindings, process, reliefTag);
    Map<?, ?> declaredRoute = map(attribute(element, "simulationPressureReliefRoute"));
    Map<String, Object> actualRoute = route(process, valve, element.getEquipmentTag(),
        String.valueOf(declaredRoute.get("teeTag")));
    Map<String, Object> expectedRoute = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> row : declaredRoute.entrySet()) {
      expectedRoute.put(String.valueOf(row.getKey()), row.getValue());
    }
    if (backpressureOverrideBara != null) {
      expectedRoute.put("backpressureBara", Double.valueOf(positiveBoundary(backpressureOverrideBara)));
      expectedRoute.put("currentDisposalPressureBara", Double.valueOf(positiveBoundary(backpressureOverrideBara)));
    }
    same(expectedRoute, actualRoute, "actual relief branch");
    if (!element.getLineTag().equals(actualRoute.get("inletStreamTag"))) {
      throw new IllegalArgumentException("Canonical relief line is not the actual branch inlet");
    }
    Map<String, Object> nativeValues = nativeConfiguration(valve);
    same(overriddenConfiguration(map(attribute(element, "simulationPressureReliefConfiguration")),
        configurationOverrides), nativeValues, "mechanical configuration");
    same(attribute(element, "nativeSourceFingerprint"), bindings.toMap().get("sourceFingerprint"), "source graph");
    EngineeringEvidenceRecord basis = capacityBasis(project,
        String.valueOf(attribute(element, "capacityBasisEvidenceId")), reliefTag);
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("schemaVersion", "neqsim_pid_pressure_relief_configuration.v1");
    values.put("projectId", project.getProjectId());
    values.put("revision", project.getRevision());
    values.put("pidElementId", element.getId());
    values.put("pidTag", element.getTag());
    values.put("simulationName", valve.getName());
    values.put("simulationClass", valve.getClass().getName());
    values.put("nativeResponseMode",
        valve.getCalculateSteadyState() ? "STEADY_NATIVE_INSTANTANEOUS" : "FINITE_NATIVE_TRANSIENT");
    values.put("pidFingerprint", bindings.toMap().get("pidFingerprint"));
    values.put("sourceFingerprint", bindings.toMap().get("sourceFingerprint"));
    values.put("configuredGraphFingerprint", ProcessDiagramGraphAdapter
        .fromProcessSystem(process, project.getProjectId(), project.getRevision()).getFingerprint());
    List<Map<String, String>> order = new ArrayList<Map<String, String>>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      Map<String, String> row = new LinkedHashMap<String, String>();
      row.put("name", unit.getName());
      row.put("class", unit.getClass().getName());
      order.add(row);
    }
    values.put("configuredExecutionOrder", order);
    Object atmosphere = element.getAttributes().get("simulationAtmosphericReferenceBara");
    Map<String, Object> gauge = new LinkedHashMap<String, Object>();
    if (atmosphere == null) {
      gauge.put("status", "HOLD_ATMOSPHERIC_REFERENCE_REQUIRED");
    } else {
      if (!(atmosphere instanceof Number)) {
        throw new IllegalArgumentException("Atmospheric reference requires bara");
      }
      double reference = positiveBoundary(Double.valueOf(((Number) atmosphere).doubleValue()));
      double setting = valve.getSetPressureBar() - reference;
      if (setting < 0.0) {
        throw new IllegalArgumentException("Gauge activation pressure must be nonnegative");
      }
      gauge.put("status", "EXPLICIT_REFERENCE_CONVERSION");
      gauge.put("atmosphericReferenceBara", Double.valueOf(reference));
      gauge.put("setPressureGaugeBar", Double.valueOf(setting));
      gauge.put("nativeUnit", "Core/PhysicalQuantities.PressureGaugeUnit.Bar");
      gauge.put("basis", "CALLER_DECLARED_REFERENCE_NOT_MEASURED_ATMOSPHERE");
    }
    values.put("nativeGaugePressureMapping", gauge);
    values.put("nativeConfiguration", nativeValues);
    values.put("physicalRoute", actualRoute);
    values.put("capacityBasis", basis.toMap());
    values.put("scenarioConfigurationOverrides", new LinkedHashMap<String, Object>(configurationOverrides));
    values.put("scenarioBackpressureOverrideBara", backpressureOverrideBara);
    values.put("qualificationStatus", "CONTROLLED_RELIEF_DESIGN_REVIEW_REQUIRED");
    values.put("fitnessForConstruction", Boolean.FALSE);
    return new ConfiguredRelief(values);
  }

  /**
   * Attaches actual passive-response evidence, preserving required FAILs and missing/full-window status.
   *
   * @param configured exact repeated configured-copy receipt
   * @param scenario declared strict-native scenario with this receipt reference
   * @param result actual native result from the governed strict runner
   * @param maintainedCriteria required full post-event sampled safety-envelope criterion IDs
   * @return detached traceable feedback; this never assigns engineering approval
   * @throws IllegalArgumentException if configuration, definition or criterion evidence disagrees
   */
  public static Map<String, Object> attachResult(ConfiguredRelief configured, DynamicSafetyScenario scenario,
      DynamicSafetyScenarioResult result, Set<String> maintainedCriteria) {
    if (configured != null && !"FINITE_NATIVE_TRANSIENT".equals(configured.toMap().get("nativeResponseMode"))) {
      throw new IllegalArgumentException("Dynamic relief evidence requires actual FINITE_NATIVE_TRANSIENT mode");
    }
    if (configured == null || scenario == null || result == null || maintainedCriteria == null
        || maintainedCriteria.isEmpty() || !scenario.getId().equals(result.getScenarioId())
        || !scenario.getEvidenceReferences().contains(configured.getEvidenceReference())) {
      throw new IllegalArgumentException("Exact configured receipt, scenario/result and full-window criteria required");
    }
    Map<String, Object> c = configured.toMap();
    Map<String, Object> evidence = reliefEvidence(result, String.valueOf(c.get("simulationName")));
    if (evidence != null) {
      same(c.get("nativeConfiguration"), evidence.get("configuration"), "executed native relief configuration");
    }
    for (DynamicScenarioCriterion criterion : scenario.getCriteria()) {
      DynamicSafetyScenarioResult.CriterionResult observed = result.getCriterionResults().get(criterion.getId());
      if (observed == null) {
        throw new IllegalArgumentException("Missing declared native criterion");
      }
      same(criterion.toMap(), observed.toMap().get("criterion"), "native criterion definition");
    }
    boolean bounds = true;
    List<?> expectedTimes = null;
    int monitoredCount = 0;
    List<Map<String, Object>> constraints = new ArrayList<Map<String, Object>>();
    for (String id : maintainedCriteria) {
      DynamicSafetyScenarioResult.CriterionResult observed = result.getCriterionResults().get(id);
      if (observed == null) {
        throw new IllegalArgumentException("Missing maintained criterion " + id);
      }
      Map<String, Object> row = observed.toMap();
      Map<?, ?> definition = map(row.get("criterion"));
      if (!"MAINTAIN_BOUNDS".equals(definition.get("evaluationMode"))
          || !Boolean.TRUE.equals(definition.get("required")) || Math.abs(number(definition, "deadlineSeconds")
              - (scenario.getDurationSeconds() - scenario.getTriggerTimeSeconds())) > 1.0e-9) {
        throw new IllegalArgumentException("Required envelope must monitor the entire post-event sampled window");
      }
      bounds &= observed.isPassed();
      if (!(row.get("timeSeconds") instanceof List)) {
        throw new IllegalArgumentException("Native maintained criterion must retain its sampled timestamps");
      }
      List<?> times = (List<?>) row.get("timeSeconds");
      if (expectedTimes == null) {
        expectedTimes = times;
      }
      if (!equivalent(expectedTimes, times)) {
        throw new IllegalArgumentException("Maintained native criteria use inconsistent sampled windows");
      }
      monitoredCount = Math.max(monitoredCount, (int) number(row, "monitoredSampleCount"));
      constraints.add(row);
    }
    boolean full = Math.abs(result.getCompletedDurationSeconds() - scenario.getDurationSeconds()) <= 1.0e-9
        && Math.abs(result.getRequestedDurationSeconds() - scenario.getDurationSeconds()) <= 1.0e-9
        && Math.abs(result.getTriggerTimeSeconds() - scenario.getTriggerTimeSeconds()) <= 1.0e-9;
    boolean nativeFull = full;
    boolean executed = evidence != null && evidence.get("trace") instanceof List
        && !((List<?>) evidence.get("trace")).isEmpty();
    boolean demand = false;
    boolean lift = false;
    boolean flow = false;
    boolean reseat = false;
    boolean traceComplete = false;
    int traceCount = 0;
    double traceStart = Double.NaN;
    double traceEnd = Double.NaN;
    if (executed) {
      List<?> trace = (List<?>) evidence.get("trace");
      traceCount = trace.size();
      traceComplete = expectedTimes != null && trace.size() == expectedTimes.size() && trace.size() == monitoredCount;
      double previous = Double.NEGATIVE_INFINITY;
      for (int index = 0; index < trace.size(); index++) {
        Object item = trace.get(index);
        Map<?, ?> sample = map(item);
        double time = number(sample, "timeSeconds");
        if (time <= previous || number(sample, "pressureBara") <= 0.0 || number(sample, "backpressureBara") <= 0.0) {
          throw new IllegalArgumentException("Relief trace requires ordered times and finite positive pressures");
        }
        if (index == 0) {
          traceStart = time;
        }
        traceEnd = time;
        previous = time;
        if (expectedTimes == null || index >= expectedTimes.size() || !(expectedTimes.get(index) instanceof Number)
            || Math.abs(
                time - scenario.getTriggerTimeSeconds() - ((Number) expectedTimes.get(index)).doubleValue()) > 1.0e-9) {
          traceComplete = false;
        }
        double position = number(sample, "liftFraction");
        double relievingFlow = number(sample, "relievingMassFlowKgPerS");
        if (position < 0.0 || position > 1.0 || relievingFlow < -1.0e-12) {
          throw new IllegalArgumentException("Relief trace has invalid lift or reverse flow for supported disposal");
        }
        if (!(sample.get("pressureDemandActive") instanceof Boolean)) {
          throw new IllegalArgumentException("Native relief trace must retain actual pressure-demand state");
        }
        demand |= Boolean.TRUE.equals(sample.get("pressureDemandActive"));
        lift |= position > 1.0e-9;
        flow |= relievingFlow > 1.0e-9;
        reseat |= lift && position <= 1.0e-9 && Math.abs(relievingFlow) <= 1.0e-9;
      }
      traceComplete &= Math.abs(traceStart - scenario.getTriggerTimeSeconds()) <= 1.0e-9
          && Math.abs(traceEnd - scenario.getDurationSeconds()) <= 1.0e-9;
    }
    full &= traceComplete;
    String status = !executed ? "NOT_EXECUTED"
        : !full ? "INCOMPLETE"
            : !result.isPassed() || !bounds ? "OBSERVED_FAIL"
                : !demand ? flow ? "UNDEMANDED_POSITIVE_RELIEF_FLOW" : "MONITORED_NO_DEMAND"
                    : !lift ? "DEMAND_WITHOUT_LIFT"
                        : !flow ? "LIFT_WITHOUT_POSITIVE_RELIEF_FLOW"
                            : reseat ? "RELIEF_AND_RESEAT_OBSERVED" : "RELIEF_FLOW_OBSERVED";
    Map<String, Object> feedback = new LinkedHashMap<String, Object>();
    feedback.put("schemaVersion", "neqsim_pid_pressure_relief_evidence.v1");
    feedback.put("configuredRelief", c);
    feedback.put("scenarioId", result.getScenarioId());
    feedback.put("scenarioDefinitionFingerprint", sha256(new Gson().toJson(scenario.toMap())));
    feedback.put("resultFingerprint", sha256(result.toJson()));
    feedback.put("executionStatus", status);
    feedback.put("nativeRequiredCriteriaPassed", Boolean.valueOf(result.isPassed()));
    feedback.put("sampledBoundsPassed", Boolean.valueOf(bounds));
    feedback.put("nativeFullHorizonCompleted", Boolean.valueOf(nativeFull));
    feedback.put("fullHorizonCompleted", Boolean.valueOf(full));
    Map<String, Object> traceCoverage = new LinkedHashMap<String, Object>();
    traceCoverage.put("status", traceComplete ? "COMPLETE_SAMPLED_EVENT_WINDOW" : "INCOMPLETE");
    traceCoverage.put("timeBasis", "SCENARIO_ELAPSED_SECONDS");
    traceCoverage.put("expectedStartSeconds", Double.valueOf(scenario.getTriggerTimeSeconds()));
    traceCoverage.put("expectedEndSeconds", Double.valueOf(scenario.getDurationSeconds()));
    traceCoverage.put("observedStartSeconds", Double.isFinite(traceStart) ? Double.valueOf(traceStart) : null);
    traceCoverage.put("observedEndSeconds", Double.isFinite(traceEnd) ? Double.valueOf(traceEnd) : null);
    traceCoverage.put("traceSampleCount", Integer.valueOf(traceCount));
    traceCoverage.put("nativeMonitoredSampleCount", Integer.valueOf(monitoredCount));
    traceCoverage.put("samplingPolicy", "ONE_DEDUPLICATED_NATIVE_SAMPLE_PER_OBSERVED_CLOCK_INCLUDING_BOTH_ENDPOINTS");
    feedback.put("traceCoverage", traceCoverage);
    feedback.put("maintainedCriteria", constraints);
    feedback.put("nativeReliefEvidence", evidence);
    feedback.put("nativePressureDemandObserved", Boolean.valueOf(demand));
    feedback.put("positiveLiftObserved", Boolean.valueOf(lift));
    feedback.put("positiveRelievingFlowObserved", Boolean.valueOf(flow));
    feedback.put("reseatAfterOpeningObserved", Boolean.valueOf(reseat));
    feedback.put("observationCoverage", "SAMPLED_ONLY_NO_INTERPOLATION");
    feedback.put("capacityQualification", "SYNTHETIC_CV_RESPONSE_ONLY_NOT_CERTIFIED_RELIEVING_CAPACITY");
    feedback.put("engineeringApprovalRequired", Boolean.TRUE);
    feedback.put("fitnessForConstruction", Boolean.FALSE);
    return feedback;
  }

  /**
   * Merges only bounded explicit faults into a detached healthy design configuration.
   * 
   * @param healthy canonical healthy mechanical settings
   * @param overrides explicit case fault settings
   * @return complete expected native case configuration
   */
  private static Map<String, Object> overriddenConfiguration(Map<?, ?> healthy, Map<String, Object> overrides) {
    if (overrides == null) {
      throw new IllegalArgumentException("Explicit fault override map is required");
    }
    Map<String, Object> expected = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> row : healthy.entrySet()) {
      expected.put(String.valueOf(row.getKey()), row.getValue());
    }
    for (Map.Entry<String, Object> row : overrides.entrySet()) {
      String key = row.getKey();
      if (!("liftMotionAvailable".equals(key) || "ratedCv".equals(key) || "tauOpenSeconds".equals(key)
          || "maximumLiftRatePerSecond".equals(key) || "initialLiftFraction".equals(key))) {
        throw new IllegalArgumentException("Unsupported undeclared mechanical fault key " + key);
      }
      if ("liftMotionAvailable".equals(key)) {
        if (!(row.getValue() instanceof Boolean)) {
          throw new IllegalArgumentException("Motion fault requires boolean");
        }
      } else {
        double scalar = number(overrides, key);
        if (scalar < 0.0 || "initialLiftFraction".equals(key) && scalar > 1.0
            || "ratedCv".equals(key) && scalar <= 0.0) {
          throw new IllegalArgumentException("Invalid declared mechanical fault value " + key);
        }
      }
      expected.put(key, row.getValue());
    }
    return expected;
  }

  /**
   * Reads an explicit positive finite disposal boundary without changing units.
   * 
   * @param pressure declared bara
   * @return positive finite bara
   */
  private static double positiveBoundary(Double pressure) {
    if (pressure == null || !Double.isFinite(pressure.doubleValue()) || pressure.doubleValue() <= 0.0) {
      throw new IllegalArgumentException("Explicit positive finite disposal backpressure in bara required");
    }
    return pressure.doubleValue();
  }

  /**
   * Reads one exact supported native valve.
   * 
   * @param process configured process
   * @param tag actual valve name
   * @return exact actual valve
   */
  static SafetyReliefValve actualValve(ProcessSystem process, String tag) {
    ProcessEquipmentInterface unit = process.getUnit(tag);
    if (unit == null || unit.getClass() != SafetyReliefValve.class || !process.getUnitOperations().contains(unit)) {
      throw new IllegalArgumentException("An exact registered native SafetyReliefValve is required");
    }
    return (SafetyReliefValve) unit;
  }

  /**
   * Resolves the exact binding in the actual configured copy.
   * 
   * @param bindings current canonical bindings
   * @param process configured process
   * @param tag canonical valve tag
   * @return actual native valve
   */
  private static SafetyReliefValve boundValve(PidSimulationBindings bindings, ProcessSystem process, String tag) {
    ProcessEquipmentInterface resolved = bindings.resolveEquipment(tag, process);
    SafetyReliefValve actual = actualValve(process, resolved.getName());
    if (resolved != actual) {
      throw new IllegalArgumentException("Valve binding is not its actual copy reference");
    }
    return actual;
  }

  /**
   * Resolves a real protected-separator/tee/relief branch and explicit discharge battery limit.
   * 
   * @param process configured process
   * @param valve actual relief valve
   * @param ownerTag protected separator name
   * @param teeTag actual splitter name
   * @return route identity and governed backpressure in bara
   */
  static Map<String, Object> route(ProcessSystem process, SafetyReliefValve valve, String ownerTag, String teeTag) {
    ProcessEquipmentInterface owner = process.getUnit(ownerTag);
    ProcessEquipmentInterface tee = process.getUnit(teeTag);
    if (!(owner instanceof Separator) || tee == null || tee.getClass() != Splitter.class
        || tee.getInletStreams().size() != 1 || tee.getOutletStreams().size() != 2
        || valve.getInletStreams().size() != 1 || valve.getOutletStreams().size() != 1
        || ((Separator) owner).getGasOutStream() != tee.getInletStreams().get(0)
        || !tee.getOutletStreams().contains(valve.getInletStream())) {
      throw new IllegalArgumentException("Relief route requires an actual protected gas outlet and two-outlet tee");
    }
    StreamInterface inlet = valve.getInletStream();
    StreamInterface outlet = valve.getOutletStream();
    int consumers = 0;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && unit.getInletStreams().contains(inlet)) {
        consumers++;
      }
      if (!(unit instanceof StreamInterface) && unit.getInletStreams().contains(outlet)) {
        throw new IllegalArgumentException("Initial relief backend supports only an explicit disposal battery limit");
      }
    }
    if (consumers != 1) {
      throw new IllegalArgumentException("Relief inlet has ambiguous actual consumers");
    }
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("protectedEquipmentTag", ownerTag);
    values.put("teeTag", teeTag);
    values.put("sourceStreamTag", tee.getInletStreams().get(0).getName());
    values.put("inletStreamTag", inlet.getName());
    values.put("outletStreamTag", outlet.getName());
    values.put("disposalBoundaryTag", outlet.getName());
    if (!Double.isFinite(valve.getOutletPressure()) || valve.getOutletPressure() <= 0.0) {
      throw new IllegalArgumentException("Relief disposal backpressure must be positive finite bara");
    }
    values.put("backpressureBara", Double.valueOf(valve.getOutletPressure()));
    values.put("currentDisposalPressureBara", Double.valueOf(outlet.getPressure("bara")));
    values.put("pressureUnit", "bara");
    values.put("destinationScope", "ACTUAL_STREAM_BATTERY_LIMIT_NOT_A_QUALIFIED_FLARE_NETWORK");
    return values;
  }

  /**
   * Reads bounded complete native settings; rejects secondary controllers/actuators and opaque trim.
   * 
   * @param valve actual supported valve
   * @return detached native mechanical readback
   */
  static Map<String, Object> nativeConfiguration(SafetyReliefValve valve) {
    if (!valve.isGasValve() || !valve.isAllowChoked()
        || valve.getValveType() != SafetyReliefValve.ValveType.CONVENTIONAL || !valve.isPhysicalStepIdempotencyEnabled()
        || valve.getController() != null || valve.getTravelModel() != ValveTravelModel.NONE
        || !"linear".equals(String.valueOf(valve.getPressureReliefConfiguration().get("valveCharacteristic")))
        || valve.getMechanicalDesign().getValveCharacterizationMethod()
            .getClass() != neqsim.process.mechanicaldesign.valve.LinearCharacteristic.class
        || valve.getMechanicalDesign().getValveSizingMethod()
            .getClass() != neqsim.process.mechanicaldesign.valve.ControlValveSizing.class) {
      throw new IllegalArgumentException(
          "Passive gas backend requires enabled native choking, a conventional valve, native linear trim/sizing, UUID idempotency and no secondary controller/actuator");
    }
    return valve.getPressureReliefConfiguration();
  }

  /**
   * Requires a registered revision-controlled assumption or capacity basis linked to this actual valve.
   * 
   * @param project governed project
   * @param id evidence identifier
   * @param tag actual relief tag
   * @return registered evidence, with approval status unchanged
   */
  static EngineeringEvidenceRecord capacityBasis(EngineeringProject project, String id, String tag) {
    for (EngineeringEvidenceRecord item : project.getEvidenceRecords()) {
      if (item.getDocumentId().equals(id) && item.getEquipmentTags().contains(tag)) {
        return item;
      }
    }
    throw new IllegalArgumentException("Capacity/Cv basis must be registered and linked to the actual relief valve");
  }

  /**
   * Finds exactly one executed relief collector entry without inventing a PLC function.
   * 
   * @param result native strict result
   * @param tag actual relief name
   * @return actual native evidence or null if unexecuted
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> reliefEvidence(DynamicSafetyScenarioResult result, String tag) {
    Map<String, Object> match = null;
    for (Map<String, Object> owner : result.getLogicEvidence().values()) {
      Object entries = owner.get("pressureReliefEvidence");
      if (!(entries instanceof List)) {
        continue;
      }
      for (Object entry : (List<?>) entries) {
        Map<String, Object> row = (Map<String, Object>) entry;
        if ("native_pressure_relief_evidence.v1".equals(row.get("schemaVersion")) && tag.equals(row.get("name"))) {
          if (match != null) {
            throw new IllegalArgumentException("Duplicate executed relief evidence");
          }
          match = row;
        }
      }
    }
    return match;
  }

  /**
   * Resolves an exact canonical tag.
   * 
   * @param model canonical graph
   * @param tag canonical tag
   * @return proposal
   */
  private static PidElement byTag(PidDesignModel model, String tag) {
    for (PidElement item : model.getElements()) {
      if (item.getTag().equals(tag)) {
        return item;
      }
    }
    throw new IllegalArgumentException("Unknown canonical relief tag " + tag);
  }

  /**
   * Reads a mandatory canonical attribute.
   * 
   * @param item canonical proposal
   * @param key attribute key
   * @return governed value
   */
  private static Object attribute(PidElement item, String key) {
    Object value = item.getAttributes().get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing governed relief setting " + key);
    }
    return value;
  }

  /**
   * Requires map-shaped native/canonical data.
   * 
   * @param value data
   * @return map data
   */
  private static Map<?, ?> map(Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Relief configuration requires a map");
    }
    return (Map<?, ?>) value;
  }

  /**
   * Reads one finite scalar without unit conversion.
   * 
   * @param values native/canonical map
   * @param key scalar key
   * @return finite value
   */
  private static double number(Map<?, ?> values, String key) {
    Object value = values.get(key);
    if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
      throw new IllegalArgumentException("Finite native relief scalar required: " + key);
    }
    return ((Number) value).doubleValue();
  }

  /**
   * Requires exact JSON-equivalent structure and finite numeric readbacks.
   * 
   * @param expected governed value
   * @param actual native value
   * @param field diagnostic name
   */
  private static void same(Object expected, Object actual, String field) {
    if (expected == null || !equivalent(expected, actual)) {
      throw new IllegalArgumentException("Canonical/native pressure-relief mismatch: " + field);
    }
  }

  /**
   * Compares finite native scalars with a small absolute rounding tolerance, preserving all map keys.
   * 
   * @param expected governed structure
   * @param actual native readback structure
   * @return true only for equivalent complete values
   */
  private static boolean equivalent(Object expected, Object actual) {
    if (expected instanceof Number && actual instanceof Number) {
      double a = ((Number) expected).doubleValue();
      double b = ((Number) actual).doubleValue();
      return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= 1.0e-12;
    }
    if (expected instanceof Map && actual instanceof Map) {
      Map<?, ?> a = (Map<?, ?>) expected;
      Map<?, ?> b = (Map<?, ?>) actual;
      if (!a.keySet().equals(b.keySet())) {
        return false;
      }
      for (Object key : a.keySet()) {
        if (!equivalent(a.get(key), b.get(key))) {
          return false;
        }
      }
      return true;
    }
    if (expected instanceof List && actual instanceof List) {
      List<?> a = (List<?>) expected;
      List<?> b = (List<?>) actual;
      if (a.size() != b.size()) {
        return false;
      }
      for (int i = 0; i < a.size(); i++) {
        if (!equivalent(a.get(i), b.get(i))) {
          return false;
        }
      }
      return true;
    }
    return expected == null ? actual == null : expected.equals(actual);
  }

  /**
   * Hashes exact UTF-8 JSON bytes.
   * 
   * @param value retained JSON
   * @return lowercase SHA-256
   */
  private static String sha256(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder text = new StringBuilder();
      for (byte b : bytes) {
        text.append(String.format(java.util.Locale.ROOT, "%02x", Integer.valueOf(b & 255)));
      }
      return text.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
