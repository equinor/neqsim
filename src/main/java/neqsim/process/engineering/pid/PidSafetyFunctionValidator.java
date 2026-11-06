package neqsim.process.engineering.pid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;

/**
 * Validates canonical P&amp;ID safety-function settings against actual objects in an isolated process copy.
 *
 * <p>
 * This is a binding and evidence-integrity check, not a safety lifecycle assessment. Physical independence, SIL,
 * continuous-time constraint satisfaction, controlled standards compliance and engineering approval are not inferred.
 * Call configuration validation again inside the scenario logic factory and compare its fingerprint before execution.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PidSafetyFunctionValidator {
  /** Prevents utility construction. */
  private PidSafetyFunctionValidator() {
  }

  /** Immutable configured-copy validation receipt with no live process references. */
  public static final class ConfiguredFunction {
    private final String json;
    private final String fingerprint;

    /**
     * Freezes one validated configuration.
     *
     * @param values source and native readback values
     */
    private ConfiguredFunction(Map<String, Object> values) {
      json = new Gson().toJson(values);
      fingerprint = sha256(json);
    }

    /** @return SHA-256 of the exact validated configuration JSON */
    public String getFingerprint() {
      return fingerprint;
    }

    /** @return scenario evidence reference required for later result attachment */
    public String getEvidenceReference() {
      return "pid-safety-config-sha256:" + fingerprint;
    }

    /** @return detached configuration and explicit qualification limits */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap() {
      Map<String, Object> result = new Gson().fromJson(json, Map.class);
      result.put("configurationFingerprint", fingerprint);
      return result;
    }

    /**
     * Rejects scenario construction that changed any governed or native readback value.
     *
     * @param actual validation repeated in the configured scenario copy
     */
    public void assertSameConfiguration(ConfiguredFunction actual) {
      if (actual == null || !fingerprint.equals(actual.fingerprint)) {
        throw new IllegalStateException("Configured safety function differs from the frozen scenario configuration");
      }
    }
  }

  /**
   * Validates a healthy configured function against its canonical P&amp;ID proposal.
   *
   * @param project governed source project
   * @param model canonical proposal graph
   * @param bindings current explicit bindings
   * @param process configured isolated process copy
   * @param functionTag canonical function tag
   * @param function actual native closed-loop function
   * @return immutable configuration receipt
   */
  public static ConfiguredFunction validateConfigured(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem process, String functionTag, ClosedLoopSafetyFunction function) {
    return validateConfigured(project, model, bindings, process, functionTag, function,
        Collections.<String, SafetyFunctionChannel.FaultMode>emptyMap());
  }

  /**
   * Validates explicit scenario fault overrides while retaining the healthy design settings.
   *
   * <p>
   * Required function attributes are {@code simulationSifId}, {@code safetyFunctionRole}, {@code simulationVoting} and
   * {@code simulationLogicDelaySeconds}. Incoming instrument attributes are {@code simulationTripUnit},
   * {@code simulationTripSetpoint}, {@code simulationTripDirection}, and {@code simulationResponseDelaySeconds}. Actual
   * ESD-valve targets require {@code simulationStrokeSeconds} and {@code simulationFailSafePosition}. Attributes govern
   * configuration only when the caller reads them into the native factory and this check succeeds.
   * </p>
   *
   * @param project governed source project
   * @param model canonical proposal graph
   * @param bindings current explicit bindings
   * @param process configured isolated process copy
   * @param functionTag canonical function tag
   * @param function actual native closed-loop function
   * @param faultOverrides explicitly declared per-instrument scenario fault modes
   * @return immutable configured-copy receipt
   * @throws IllegalArgumentException if settings, connectivity or actual object identities disagree
   */
  public static ConfiguredFunction validateConfigured(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, ProcessSystem process, String functionTag, ClosedLoopSafetyFunction function,
      Map<String, SafetyFunctionChannel.FaultMode> faultOverrides) {
    if (bindings == null || process == null || function == null || faultOverrides == null) {
      throw new IllegalArgumentException("bindings, configured process, function and fault overrides are required");
    }
    bindings.assertCurrent(project, model);
    if (!(function.getFinalElementLogic() instanceof ESDLogic)) {
      throw new IllegalArgumentException("Final-element actions require a supported native ESD trip policy");
    }
    Map<String, Object> actionPolicy = ((ESDLogic) function.getFinalElementLogic()).getTripActionConfiguration();
    if (!Boolean.TRUE.equals(actionPolicy.get("supportedTripPolicy"))) {
      throw new IllegalArgumentException("Final-element action graph is not a supported native trip policy");
    }
    if (((ESDLogic) function.getFinalElementLogic()).getTargetEquipmentCoverageIssue() != null) {
      throw new IllegalArgumentException("Final-element actions require complete native target identity coverage");
    }
    PidElement proposal = byTag(model, functionTag);
    if (proposal.getType() != PidElementType.TRIP && proposal.getType() != PidElementType.SAFETY_FUNCTION) {
      throw new IllegalArgumentException("Safety function requires a canonical TRIP or SAFETY_FUNCTION element");
    }
    Map<String, Object> configuration = nativeConfiguration(function.getDynamicSafetyScenarioEvidence());
    if (!new Gson().toJsonTree(attribute(proposal, "simulationFinalElementActionConfiguration"))
        .equals(new Gson().toJsonTree(actionPolicy))
        || !new Gson().toJsonTree(actionPolicy)
            .equals(new Gson().toJsonTree(configuration.get("finalElementActionConfiguration")))) {
      throw new IllegalArgumentException("Canonical/native final-element command policy differs or is unreported");
    }
    same(proposal.getTag(), function.getName(), "function tag/name");
    same(attribute(proposal, "simulationSifId"), function.getSifId(), "SIF identity");
    String role = String.valueOf(attribute(proposal, "safetyFunctionRole"));
    if (!"ESD".equals(role) && !"HIPPS".equals(role) && !"MANUAL_ESD".equals(role)) {
      throw new IllegalArgumentException("Unsupported explicit safety-function role: " + role);
    }
    same(attribute(proposal, "simulationVoting"), configuration.get("votingPattern"), "voting pattern");
    equalNumber(attribute(proposal, "simulationLogicDelaySeconds"), configuration.get("logicSolverDelaySeconds"),
        "logic solver delay");
    List<PidElement> inputs = new ArrayList<PidElement>();
    for (PidElement candidate : model.getElements()) {
      if (candidate.getConnectedElementIds().contains(proposal.getId())) {
        if (candidate.getType() != PidElementType.MEASUREMENT && candidate.getType() != PidElementType.INDICATOR) {
          throw new IllegalArgumentException("Safety input must be an explicitly bound instrument");
        }
        inputs.add(candidate);
      }
    }
    if (inputs.size() != function.getChannels().size()) {
      throw new IllegalArgumentException("Canonical input count differs from the native voting channels");
    }
    Set<MeasurementDeviceInterface> sources = Collections
        .newSetFromMap(new IdentityHashMap<MeasurementDeviceInterface, Boolean>());
    Set<String> matchedFaultOverrides = new LinkedHashSet<String>();
    List<Map<String, Object>> channelBindings = new ArrayList<Map<String, Object>>();
    List<?> channelConfiguration = (List<?>) configuration.get("channelConfiguration");
    for (int index = 0; index < function.getChannels().size(); index++) {
      SafetyFunctionChannel channel = function.getChannels().get(index);
      MeasurementDeviceInterface actual = channel.getMeasurementSource();
      if (actual == null || !sources.add(actual)) {
        throw new IllegalArgumentException("Every safety channel requires a distinct actual native measurement");
      }
      PidElement input = null;
      for (PidElement candidate : inputs) {
        if (bindings.resolveMeasurement(candidate.getTag(), process) == actual) {
          if (input != null) {
            throw new IllegalArgumentException("Multiple canonical instruments alias one actual measurement");
          }
          input = candidate;
        }
      }
      if (input == null) {
        throw new IllegalArgumentException("Channel source is not its bound measurement in this process copy");
      }
      Map<?, ?> values = (Map<?, ?>) channelConfiguration.get(index);
      same(actual.getName(), channel.getTag(), "actual channel tag");
      same(attribute(input, "simulationTripUnit"), values.get("unit"), "trip unit");
      same(attribute(input, "simulationTripDirection"), values.get("tripDirection"), "trip direction");
      equalNumber(attribute(input, "simulationTripSetpoint"), values.get("setpoint"), "trip setpoint");
      equalNumber(attribute(input, "simulationResponseDelaySeconds"), values.get("responseDelaySeconds"),
          "channel qualification delay");
      SafetyFunctionChannel.FaultMode fault = faultOverrides.get(input.getTag());
      if (fault == SafetyFunctionChannel.FaultMode.BIASED) {
        throw new IllegalArgumentException("A biased channel requires a separately governed bias-magnitude contract");
      }
      if (fault != null) {
        matchedFaultOverrides.add(input.getTag());
      }
      same(fault == null ? "HEALTHY" : fault.name(), values.get("faultMode"), "declared channel fault mode");
      equalNumber(Double.valueOf(0.0), values.get("bias"), "declared zero channel bias");
      String status = String.valueOf(values.get("inputIdentityStatus"));
      if (!("NATIVE_HELD_MEASUREMENT".equals(status) || "NATIVE_DISCRETE_PUSH_BUTTON".equals(status))) {
        throw new IllegalArgumentException("Arbitrary extractor does not establish native measurement identity");
      }
      if ("MANUAL_ESD".equals(role) != "NATIVE_DISCRETE_PUSH_BUTTON".equals(status)) {
        throw new IllegalArgumentException("Manual and measured pressure roles must remain distinct");
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("pidElementId", input.getId());
      row.put("pidTag", input.getTag());
      row.put("simulationName", actual.getName());
      row.put("simulationClass", actual.getClass().getName());
      channelBindings.add(row);
    }
    if (!matchedFaultOverrides.equals(faultOverrides.keySet())) {
      throw new IllegalArgumentException("Fault overrides must identify only actual inputs to this function");
    }
    List<Map<String, Object>> targetBindings = targets(model, bindings, process, proposal, function);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_pid_safety_function_binding.v1");
    result.put("projectId", project.getProjectId());
    result.put("revision", project.getRevision());
    result.put("pidFingerprint", bindings.toMap().get("pidFingerprint"));
    result.put("sourceFingerprint", bindings.toMap().get("sourceFingerprint"));
    result.put("configuredTopologyFingerprint", ProcessDiagramGraphAdapter
        .fromProcessSystem(process, project.getProjectId(), project.getRevision()).getFingerprint());
    List<Map<String, String>> order = new ArrayList<Map<String, String>>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      Map<String, String> row = new LinkedHashMap<String, String>();
      row.put("name", unit.getName());
      row.put("class", unit.getClass().getName());
      order.add(row);
    }
    result.put("configuredExecutionOrder", order);
    result.put("pidFunctionId", proposal.getId());
    result.put("pidFunctionTag", proposal.getTag());
    result.put("safetyFunctionRole", role);
    result.put("channelBindings", channelBindings);
    result.put("targetBindings", targetBindings);
    result.put("nativeConfiguration", configuration);
    Map<String, String> overrides = new LinkedHashMap<String, String>();
    for (Map.Entry<String, SafetyFunctionChannel.FaultMode> entry : faultOverrides.entrySet()) {
      overrides.put(entry.getKey(), entry.getValue().name());
    }
    result.put("scenarioFaultOverrides", overrides);
    result.put("hardwareIndependence", "NOT_ESTABLISHED_BY_OBJECT_IDENTITY");
    result.put("approvalStatus", "ENGINEERING_REVIEW_REQUIRED");
    result.put("silTargetInferred", Boolean.FALSE);
    result.put("fitnessForConstruction", Boolean.FALSE);
    return new ConfiguredFunction(result);
  }

  /**
   * Checks actual target identity, graph coverage and actuator timing readback.
   *
   * @param model canonical graph
   * @param bindings current explicit bindings
   * @param process configured process
   * @param proposal canonical safety function
   * @param function native implementation
   * @return target identity and actual settings
   */
  private static List<Map<String, Object>> targets(PidDesignModel model, PidSimulationBindings bindings,
      ProcessSystem process, PidElement proposal, ClosedLoopSafetyFunction function) {
    Set<ProcessEquipmentInterface> expected = Collections
        .newSetFromMap(new IdentityHashMap<ProcessEquipmentInterface, Boolean>());
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (String id : proposal.getConnectedElementIds()) {
      PidElement target = byId(model, id);
      ProcessEquipmentInterface actual = bindings.resolveEquipment(target.getTag(), process);
      if (!expected.add(actual) || !(actual instanceof ESDValve)) {
        throw new IllegalArgumentException("Distinct bound native ESD-valve targets are required");
      }
      ESDValve valve = (ESDValve) actual;
      if (Math.abs(valve.getFailSafePosition()) > 1.0e-12) {
        throw new IllegalArgumentException(
            "This isolation-function contract requires actual fail-safe closure at zero percent");
      }
      equalNumber(attribute(target, "simulationStrokeSeconds"), Double.valueOf(valve.getStrokeTime()),
          "final-element stroke time");
      equalNumber(attribute(target, "simulationFailSafePosition"), Double.valueOf(valve.getFailSafePosition()),
          "final-element fail-safe position");
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("pidElementId", target.getId());
      row.put("pidTag", target.getTag());
      row.put("simulationName", actual.getName());
      row.put("simulationClass", actual.getClass().getName());
      row.put("strokeSeconds", Double.valueOf(valve.getStrokeTime()));
      row.put("failSafePosition", Double.valueOf(valve.getFailSafePosition()));
      row.put("actuatorAvailable", Boolean.valueOf(valve.isActuatorAvailable()));
      List<String> shared = new ArrayList<String>();
      for (PidElement candidate : model.getElements()) {
        if (!candidate.getId().equals(proposal.getId()) && candidate.getConnectedElementIds().contains(id)
            && (candidate.getType() == PidElementType.TRIP || candidate.getType() == PidElementType.SAFETY_FUNCTION)) {
          shared.add(candidate.getTag());
        }
      }
      row.put("sharedWithSafetyFunctions", shared);
      row.put("independentBarrierCredit", "NOT_ESTABLISHED");
      rows.add(row);
    }
    Set<ProcessEquipmentInterface> actual = Collections
        .newSetFromMap(new IdentityHashMap<ProcessEquipmentInterface, Boolean>());
    actual.addAll(function.getTargetEquipment());
    if (expected.isEmpty() || !expected.equals(actual)) {
      throw new IllegalArgumentException("Native final-element targets differ from the canonical function graph");
    }
    return rows;
  }

  /**
   * Attaches actual executed evidence only when its native configuration and declared full-window constraints match.
   *
   * @param configured frozen configured-copy receipt
   * @param scenario controlled scenario definition carrying the receipt reference
   * @param result native strict result
   * @param maintainedCriteria required full post-event sampled-bound criterion IDs
   * @return detached traceable evidence with distinct execution and native criterion verdicts
   */
  public static Map<String, Object> attachResult(ConfiguredFunction configured, DynamicSafetyScenario scenario,
      DynamicSafetyScenarioResult result, Set<String> maintainedCriteria) {
    if (configured == null || scenario == null || result == null || maintainedCriteria == null
        || maintainedCriteria.isEmpty()) {
      throw new IllegalArgumentException("Configured receipt, scenario, result and full-window criteria are required");
    }
    if (!scenario.getId().equals(result.getScenarioId())
        || !scenario.getEvidenceReferences().contains(configured.getEvidenceReference())) {
      throw new IllegalArgumentException("Scenario/result do not identify the frozen safety configuration");
    }
    for (DynamicScenarioCriterion criterion : scenario.getCriteria()) {
      DynamicSafetyScenarioResult.CriterionResult observed = result.getCriterionResults().get(criterion.getId());
      if (observed == null
          || !new Gson().toJson(criterion.toMap()).equals(new Gson().toJson(observed.toMap().get("criterion")))) {
        throw new IllegalArgumentException("Result criterion differs from the controlled scenario definition");
      }
    }
    Map<String, Object> receipt = configured.toMap();
    String tag = String.valueOf(receipt.get("pidFunctionTag"));
    Map<String, Object> evidence = functionEvidence(result, tag);
    boolean executed = evidence != null && evidence.get("trace") instanceof List
        && !((List<?>) evidence.get("trace")).isEmpty();
    if (evidence != null && !new Gson().toJsonTree(receipt.get("nativeConfiguration"))
        .equals(new Gson().toJsonTree(nativeConfiguration(evidence)))) {
      throw new IllegalArgumentException("Result contains a different native safety-function configuration");
    }
    boolean fullHorizon = Math.abs(result.getCompletedDurationSeconds() - scenario.getDurationSeconds()) <= 1.0e-9
        && Math.abs(result.getRequestedDurationSeconds() - scenario.getDurationSeconds()) <= 1.0e-9
        && Math.abs(result.getTriggerTimeSeconds() - scenario.getTriggerTimeSeconds()) <= 1.0e-9;
    List<Map<String, Object>> constraints = new ArrayList<Map<String, Object>>();
    boolean boundsPassed = true;
    for (String criterionId : maintainedCriteria) {
      DynamicSafetyScenarioResult.CriterionResult actual = result.getCriterionResults().get(criterionId);
      if (actual == null) {
        throw new IllegalArgumentException("Missing declared maintained criterion: " + criterionId);
      }
      Map<String, Object> value = actual.toMap();
      Map<?, ?> criterion = (Map<?, ?>) value.get("criterion");
      if (!"MAINTAIN_BOUNDS".equals(criterion.get("evaluationMode"))
          || !Boolean.TRUE.equals(criterion.get("required"))) {
        throw new IllegalArgumentException("Safety envelope requires required MAINTAIN_BOUNDS criteria");
      }
      equalNumber(Double.valueOf(scenario.getDurationSeconds() - scenario.getTriggerTimeSeconds()),
          criterion.get("deadlineSeconds"), "maintained post-event window");
      boundsPassed &= actual.isPassed();
      constraints.add(value);
    }
    String status = !executed ? "NOT_EXECUTED"
        : !fullHorizon ? "INCOMPLETE"
            : !result.isPassed() || !boundsPassed ? "OBSERVED_FAIL"
                : evidence.get("firstVoteSeconds") == null ? "MONITORED_NO_DEMAND"
                    : evidence.get("finalElementActuationSeconds") == null ? "DEMAND_WITHOUT_ACTUATION"
                        : !"COMPLETED".equals(evidence.get("finalElementState")) ? "ACTUATED_RESPONSE_INCOMPLETE"
                            : "DEMAND_RESPONSE_OBSERVED";
    Map<String, Object> output = new LinkedHashMap<String, Object>();
    output.put("schemaVersion", "neqsim_pid_safety_function_evidence.v1");
    output.put("configuredFunction", receipt);
    output.put("scenarioId", result.getScenarioId());
    output.put("scenarioDefinitionFingerprint", sha256(new Gson().toJson(scenario.toMap())));
    output.put("resultFingerprint", sha256(result.toJson()));
    output.put("executionStatus", status);
    output.put("nativeRequiredCriteriaPassed", Boolean.valueOf(result.isPassed()));
    output.put("sampledBoundsPassed", Boolean.valueOf(boundsPassed));
    output.put("fullHorizonCompleted", Boolean.valueOf(fullHorizon));
    output.put("maintainedCriteria", constraints);
    Map<String, Object> execution = new LinkedHashMap<String, Object>();
    for (String key : new String[] {"firstVoteSeconds", "finalElementActuationSeconds", "finalElementState"}) {
      execution.put(key, evidence == null ? null : evidence.get(key));
    }
    output.put("nativeFunctionExecution", execution);
    output.put("deadlineQualificationScope", "ONLY_EXPLICIT_DECLARED_CRITERIA_NOT_ALL_FUNCTIONS_OR_HAZARDS");
    output.put("faultModeQualification",
        ((Map<?, ?>) receipt.get("scenarioFaultOverrides")).isEmpty() ? "NORMAL_DESIGN_CHANNEL_MODES"
            : "DECLARED_FAULT_MODES_ONLY");
    output.put("engineeringApprovalRequired", Boolean.TRUE);
    output.put("silTargetInferred", Boolean.FALSE);
    output.put("fitnessForConstruction", Boolean.FALSE);
    return output;
  }

  /**
   * Resolves one native function from direct or explicitly collected native evidence without accepting duplicates.
   *
   * @param result native result
   * @param tag exact configured function tag/name
   * @return matching native function evidence, or null when not executed
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> functionEvidence(DynamicSafetyScenarioResult result, String tag) {
    Map<String, Object> match = null;
    for (Map<String, Object> owner : result.getLogicEvidence().values()) {
      List<Map<String, Object>> candidates = new ArrayList<Map<String, Object>>();
      candidates.add(owner);
      if (owner.get("nativeFunctions") instanceof List) {
        for (Object item : (List<?>) owner.get("nativeFunctions")) {
          if (item instanceof Map) {
            candidates.add((Map<String, Object>) item);
          }
        }
      }
      for (Map<String, Object> candidate : candidates) {
        if ("closed_loop_sif_evidence.v1".equals(candidate.get("schemaVersion")) && tag.equals(candidate.get("name"))) {
          if (match != null) {
            throw new IllegalArgumentException("Duplicate native evidence for configured safety function " + tag);
          }
          match = candidate;
        }
      }
    }
    return match;
  }

  /**
   * Selects stable native settings while excluding execution trace and changing state.
   *
   * @param evidence native closed-loop readback
   * @return actual native configuration
   */
  private static Map<String, Object> nativeConfiguration(Map<String, Object> evidence) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (String key : new String[] {"sifId", "name", "votingPattern", "logicSolverDelaySeconds", "channelConfiguration",
        "finalElementLogic", "finalElementLogicClass", "finalElementActionConfiguration", "finalElementTargets"}) {
      result.put(key, evidence.get(key));
    }
    return result;
  }

  /**
   * Reads a mandatory governed attribute.
   *
   * @param element canonical proposal
   * @param key attribute key
   * @return non-null declared value
   */
  private static Object attribute(PidElement element, String key) {
    Object value = element.getAttributes().get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing " + key + " on " + element.getTag());
    }
    return value;
  }

  /**
   * Requires matching values without silently changing units or voting semantics.
   *
   * @param expected canonical value
   * @param actual native value
   * @param field diagnostic field
   */
  private static void same(Object expected, Object actual, String field) {
    if (expected == null || !expected.equals(actual)) {
      throw new IllegalArgumentException("Canonical/native mismatch: " + field);
    }
  }

  /**
   * Requires equal finite native scalar readback.
   *
   * @param expected canonical scalar
   * @param actual native scalar
   * @param field diagnostic field
   */
  private static void equalNumber(Object expected, Object actual, String field) {
    if (!(expected instanceof Number) || !(actual instanceof Number)
        || !Double.isFinite(((Number) expected).doubleValue()) || !Double.isFinite(((Number) actual).doubleValue())
        || Math.abs(((Number) expected).doubleValue() - ((Number) actual).doubleValue()) > 1.0e-12) {
      throw new IllegalArgumentException("Canonical/native scalar mismatch: " + field);
    }
  }

  /**
   * Resolves an exact unique canonical tag.
   *
   * @param model canonical graph
   * @param tag element tag
   * @return matching element
   */
  private static PidElement byTag(PidDesignModel model, String tag) {
    for (PidElement element : model.getElements()) {
      if (element.getTag().equals(tag)) {
        return element;
      }
    }
    throw new IllegalArgumentException("Unknown canonical safety tag: " + tag);
  }

  /**
   * Resolves an exact canonical element ID.
   *
   * @param model canonical graph
   * @param id element ID
   * @return matching element
   */
  private static PidElement byId(PidDesignModel model, String id) {
    for (PidElement element : model.getElements()) {
      if (element.getId().equals(id)) {
        return element;
      }
    }
    throw new IllegalArgumentException("Unknown canonical safety element ID: " + id);
  }

  /**
   * Hashes exact retained JSON bytes.
   *
   * @param text retained text
   * @return lowercase SHA-256
   */
  private static String sha256(String text) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte value : digest) {
        result.append(String.format(java.util.Locale.ROOT, "%02x", Integer.valueOf(value & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
