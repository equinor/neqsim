package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ActionState;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicConstraintBinding;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;

/**
 * Immutable fail-closed plan for repeated transactional {@link ProcessModel} evaluations.
 *
 * <p>
 * Compilation qualifies the current converged baseline through the supplied
 * {@link ProcessModelOperatingActionSetEvaluator}, then freezes area order and structure versions, action and
 * hydraulic-binding definitions, evaluator configuration, and exact installed-capacity and process-boundary coverage.
 * Candidate evaluation is refused before mutation when any frozen definition has changed. A delegated candidate is
 * accepted only when the full model converged, every action was restored, the restored baseline reconverged, and the
 * exact expected evidence remains finite and complete.
 * </p>
 *
 * <p>
 * The plan deliberately does not schedule candidates concurrently because all candidates share mutable equipment. Use
 * independently cloned models and independently compiled plans for parallel optimization. The class adds no equipment
 * physics and does not infer missing capacity ratings.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelCompiledEvaluationPlan implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** JSON schema version. */
  private static final String SCHEMA_VERSION = "1.0";

  /** Stable plan identifier. */
  private final String id;

  /** Human-readable plan name. */
  private final String name;

  /** Engineering provenance for compilation. */
  private final String provenance;

  /** Exact source baseline supplied by the caller. */
  private final String sourceBaseline;

  /** Mutable evaluator authority used only through synchronized evaluation. */
  private final ProcessModelOperatingActionSetEvaluator evaluator;

  /** Frozen area names in model insertion order. */
  private final List<String> areaNames;

  /** Frozen ProcessSystem structure versions in area order. */
  private final List<Long> areaStructureVersions;

  /** Frozen action definitions in declaration order. */
  private final List<String> actionDefinitions;

  /** Frozen hydraulic-binding definitions in declaration order. */
  private final List<String> hydraulicBindingDefinitions;

  /** Frozen evaluator definition JSON. */
  private final String evaluatorDefinitionJson;

  /** Exact expected installed-capacity identities in deterministic order. */
  private final List<String> expectedInstalledCapacityIdentities;

  /** Frozen installed-capacity definitions in deterministic identity order. */
  private final List<String> expectedInstalledCapacityDefinitions;

  /** Exact expected process-boundary identities in deterministic order. */
  private final List<String> expectedBoundaryIdentities;

  /** Baseline qualification evidence retained without mutable callbacks. */
  private final CandidateSetEvaluationResult compilationEvidence;

  /** Result classification for a compiled candidate. */
  public enum Outcome {
    /** Candidate passed every frozen-plan and delegated evaluation gate. */
    ACCEPTED,
    /** Candidate vector was null, incorrectly sized, or non-finite. */
    CANDIDATE_VECTOR_INVALID,
    /** Topology, actions, bindings, evaluator configuration, or installed ratings changed. */
    PLAN_STALE,
    /** The transactional evaluator rejected the candidate. */
    CANDIDATE_REJECTED,
    /** Candidate evidence did not contain the exact finite compiled coverage. */
    EVIDENCE_INCOMPLETE,
    /** Candidate actions or the converged baseline were not completely restored. */
    RESTORATION_FAILED
  }

  /** Creates a compiled plan from already qualified immutable evidence. */
  private ProcessModelCompiledEvaluationPlan(String id, String name, String provenance, String sourceBaseline,
      ProcessModelOperatingActionSetEvaluator evaluator, CandidateSetEvaluationResult compilationEvidence) {
    this.id = id;
    this.name = name;
    this.provenance = provenance;
    this.sourceBaseline = sourceBaseline;
    this.evaluator = evaluator;
    ProcessModel model = evaluator.getSimulationEvaluator().getProcessModel();
    this.areaNames = immutableStrings(model.getProcessSystemNames());
    this.areaStructureVersions = captureAreaStructureVersions(model, areaNames);
    this.actionDefinitions = actionDefinitions(evaluator.getActions());
    this.hydraulicBindingDefinitions = bindingDefinitions(evaluator.getRequiredHydraulicConstraints());
    this.evaluatorDefinitionJson = evaluator.getSimulationEvaluator().toJson();
    this.expectedInstalledCapacityIdentities = installedIdentities(
        compilationEvidence.getInstalledEquipmentCapacityEvidence());
    this.expectedInstalledCapacityDefinitions = installedDefinitions(
        compilationEvidence.getInstalledEquipmentCapacityEvidence());
    this.expectedBoundaryIdentities = boundaryIdentities(compilationEvidence.getProcessBoundaryConstraintEvidence());
    this.compilationEvidence = compilationEvidence;
  }

  /**
   * Compiles and qualifies a transactional evaluation plan at the current baseline.
   *
   * <p>
   * The current action values are evaluated as a no-change candidate. Compilation fails unless that candidate is fully
   * feasible, all actions restore, the baseline reconverges, and at least one installed-capacity constraint is present.
   * </p>
   *
   * @param id stable plan identifier
   * @param name human-readable plan name
   * @param provenance engineering source for the selected actions and required evidence
   * @param sourceBaseline exact source or configuration baseline identifier
   * @param evaluator configured transactional evaluator
   * @return immutable compiled plan
   */
  public static ProcessModelCompiledEvaluationPlan compile(String id, String name, String provenance,
      String sourceBaseline, ProcessModelOperatingActionSetEvaluator evaluator) {
    String safeId = requireText(id, "Compiled-plan identifier");
    String safeName = requireText(name, "Compiled-plan name");
    String safeProvenance = requireText(provenance, "Compiled-plan provenance");
    String safeBaseline = requireText(sourceBaseline, "Compiled-plan source baseline");
    if (evaluator == null) {
      throw new IllegalArgumentException("Transactional evaluator is required");
    }
    if (evaluator.getRequiredHydraulicConstraints().isEmpty()) {
      throw new IllegalStateException("At least one exact hydraulic constraint is required before compilation");
    }
    ProcessModel model = evaluator.getSimulationEvaluator().getProcessModel();
    if (model == null) {
      throw new IllegalStateException("Transactional evaluator must contain a process model");
    }
    try {
      model.run();
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Compiled-plan baseline run failed: " + safeMessage(exception), exception);
    }
    if (!model.isModelConverged()) {
      throw new IllegalStateException("Compiled-plan baseline did not converge");
    }
    List<ProcessModelOperatingAction> actions = evaluator.getActions();
    double[] baselineValues = new double[actions.size()];
    for (int index = 0; index < actions.size(); index++) {
      ActionState state = actions.get(index).capture(model);
      baselineValues[index] = state.getValue();
      if (!Double.isFinite(baselineValues[index])) {
        throw new IllegalStateException("Compiled-plan baseline action is non-finite: " + actions.get(index).getId());
      }
    }
    CandidateSetEvaluationResult qualification = evaluator.evaluate(baselineValues);
    if (!qualification.isFeasible() || !qualification.isCandidateSimulationConverged()
        || !qualification.isBaselineRestored() || !qualification.isBaselineSimulationConverged()) {
      throw new IllegalStateException("Compiled-plan baseline qualification failed: " + qualification.getOutcome() + " "
          + qualification.getDiagnostics());
    }
    if (qualification.getInstalledEquipmentCapacityEvidence().isEmpty()) {
      throw new IllegalStateException("Compiled plan requires at least one installed-capacity evidence row");
    }
    List<String> diagnostics = validateEvidence(qualification,
        installedIdentities(qualification.getInstalledEquipmentCapacityEvidence()),
        boundaryIdentities(qualification.getProcessBoundaryConstraintEvidence()));
    if (!diagnostics.isEmpty()) {
      throw new IllegalStateException("Compiled-plan baseline evidence is incomplete: " + diagnostics);
    }
    return new ProcessModelCompiledEvaluationPlan(safeId, safeName, safeProvenance, safeBaseline, evaluator,
        qualification);
  }

  /**
   * Evaluates one candidate against the frozen plan and complete transactional replay.
   *
   * @param candidateValues values in action declaration order and declared units
   * @return immutable fail-closed result
   */
  public synchronized EvaluationResult evaluate(double[] candidateValues) {
    double[] candidates = candidateValues == null ? new double[0]
        : Arrays.copyOf(candidateValues, candidateValues.length);
    List<String> diagnostics = new ArrayList<String>();
    if (candidateValues == null || candidates.length != actionDefinitions.size()) {
      diagnostics.add("Candidate vector length must equal the compiled action count " + actionDefinitions.size());
      return EvaluationResult.rejected(this, candidates, Outcome.CANDIDATE_VECTOR_INVALID, null, diagnostics);
    }
    for (int index = 0; index < candidates.length; index++) {
      if (!Double.isFinite(candidates[index])) {
        diagnostics.add("Candidate value is non-finite at action index " + index);
        return EvaluationResult.rejected(this, candidates, Outcome.CANDIDATE_VECTOR_INVALID, null, diagnostics);
      }
    }
    diagnostics.addAll(inspectStaleness());
    if (!diagnostics.isEmpty()) {
      return EvaluationResult.rejected(this, candidates, Outcome.PLAN_STALE, null, diagnostics);
    }

    CandidateSetEvaluationResult candidate = evaluator.evaluate(candidates);
    if (!candidate.isBaselineRestored() || !candidate.isBaselineSimulationConverged()) {
      diagnostics.add("Transactional evaluator did not completely restore a converged baseline");
      diagnostics.addAll(candidate.getDiagnostics());
      return EvaluationResult.rejected(this, candidates, Outcome.RESTORATION_FAILED, candidate, diagnostics);
    }
    if (!candidate.isFeasible()) {
      diagnostics.add("Transactional evaluator rejected the candidate: " + candidate.getOutcome());
      diagnostics.addAll(candidate.getDiagnostics());
      return EvaluationResult.rejected(this, candidates, Outcome.CANDIDATE_REJECTED, candidate, diagnostics);
    }

    diagnostics.addAll(validateEvidence(candidate, expectedInstalledCapacityIdentities, expectedBoundaryIdentities));
    if (!expectedInstalledCapacityDefinitions
        .equals(installedDefinitions(candidate.getInstalledEquipmentCapacityEvidence()))) {
      diagnostics.add("Installed-capacity definitions differ from the compiled rating snapshot");
    }
    if (!diagnostics.isEmpty()) {
      return EvaluationResult.rejected(this, candidates, Outcome.EVIDENCE_INCOMPLETE, candidate, diagnostics);
    }
    return EvaluationResult.accepted(this, candidates, candidate);
  }

  /** Returns diagnostics when any frozen plan definition has changed. */
  private List<String> inspectStaleness() {
    List<String> diagnostics = new ArrayList<String>();
    ProcessModel model = evaluator.getSimulationEvaluator().getProcessModel();
    if (model == null) {
      diagnostics.add("Compiled process model is no longer available");
      return diagnostics;
    }
    List<String> currentAreas = model.getProcessSystemNames();
    if (!areaNames.equals(currentAreas)) {
      diagnostics.add("ProcessModel area order or identity changed after compilation");
      return diagnostics;
    }
    List<Long> currentVersions = captureAreaStructureVersions(model, currentAreas);
    if (!areaStructureVersions.equals(currentVersions)) {
      diagnostics.add("At least one ProcessSystem structure version changed after compilation");
    }
    if (!actionDefinitions.equals(actionDefinitions(evaluator.getActions()))) {
      diagnostics.add("Operating-action definitions changed after compilation");
    }
    if (!hydraulicBindingDefinitions.equals(bindingDefinitions(evaluator.getRequiredHydraulicConstraints()))) {
      diagnostics.add("Required hydraulic bindings changed after compilation");
    }
    if (!evaluatorDefinitionJson.equals(evaluator.getSimulationEvaluator().toJson())) {
      diagnostics.add("Simulation evaluator definition changed after compilation");
    }
    List<InstalledEquipmentCapacityEvidence> currentCapacity = evaluator.getSimulationEvaluator()
        .snapshotInstalledEquipmentCapacityEvidence(model);
    if (!expectedInstalledCapacityDefinitions.equals(installedDefinitions(currentCapacity))) {
      diagnostics.add("Installed-capacity availability or rating changed after compilation");
    }
    return diagnostics;
  }

  /** Verifies exact identities and finite evidence for a completed feasible candidate. */
  private static List<String> validateEvidence(CandidateSetEvaluationResult result,
      List<String> expectedInstalledIdentities, List<String> expectedBoundaryIdentities) {
    List<String> diagnostics = new ArrayList<String>();
    List<InstalledEquipmentCapacityEvidence> installed = result.getInstalledEquipmentCapacityEvidence();
    List<String> installedIds = installedIdentities(installed);
    if (!expectedInstalledIdentities.equals(installedIds)) {
      diagnostics.add("Installed-capacity coverage differs from the compiled exact identity set");
    }
    for (InstalledEquipmentCapacityEvidence evidence : installed) {
      if (!evidence.hasFiniteEvidence() || !Double.isFinite(evidence.getPhysicalMargin())
          || !Double.isFinite(evidence.getRequiredRelief())) {
        diagnostics.add("Installed-capacity evidence is unavailable: " + evidence.getQualifiedConstraintName());
      }
    }
    List<ProcessBoundaryConstraintEvidence> boundaries = result.getProcessBoundaryConstraintEvidence();
    List<String> boundaryIds = boundaryIdentities(boundaries);
    if (!expectedBoundaryIdentities.equals(boundaryIds)) {
      diagnostics.add("Process-boundary coverage differs from the compiled exact identity set");
    }
    for (ProcessBoundaryConstraintEvidence evidence : boundaries) {
      if (!evidence.isCalculable() || !Double.isFinite(evidence.getSampledValue())
          || !Double.isFinite(evidence.getSignedMargin())) {
        diagnostics.add("Process-boundary evidence is unavailable: " + evidence.getQualifiedConstraintName());
      }
    }
    return diagnostics;
  }

  /** Captures ProcessSystem structure versions in exact area order. */
  private static List<Long> captureAreaStructureVersions(ProcessModel model, List<String> names) {
    List<Long> versions = new ArrayList<Long>();
    for (String areaName : names) {
      ProcessSystem area = model.get(areaName);
      versions.add(Long.valueOf(area == null ? Long.MIN_VALUE : area.getStructureVersion()));
    }
    return Collections.unmodifiableList(versions);
  }

  /** Creates deterministic action definition strings. */
  private static List<String> actionDefinitions(List<ProcessModelOperatingAction> actions) {
    List<String> definitions = new ArrayList<String>();
    for (ProcessModelOperatingAction action : actions) {
      definitions.add(
          action.getId() + "|" + action.getName() + "|" + action.getAddress() + "|" + action.getValueSemantics().name()
              + "|" + Double.toString(action.getLowerBound()) + "|" + Double.toString(action.getUpperBound()) + "|"
              + Arrays.toString(action.getAllowedValues()) + "|" + action.getUnit() + "|" + action.getProvenance() + "|"
              + Double.toString(action.getReadBackAbsoluteTolerance()) + "|"
              + Double.toString(action.getReadBackRelativeTolerance()) + "|" + action.getReadBackToleranceProvenance());
    }
    return immutableStrings(definitions);
  }

  /** Creates deterministic hydraulic-binding definition strings. */
  private static List<String> bindingDefinitions(List<HydraulicConstraintBinding> bindings) {
    List<String> definitions = new ArrayList<String>();
    for (HydraulicConstraintBinding binding : bindings) {
      definitions
          .add(binding.getRole().name() + "|" + binding.getQualifiedConstraintName() + "|" + binding.getProvenance());
    }
    return immutableStrings(definitions);
  }

  /** Returns deterministic installed-capacity identities. */
  private static List<String> installedIdentities(List<InstalledEquipmentCapacityEvidence> evidence) {
    List<String> identities = new ArrayList<String>();
    for (InstalledEquipmentCapacityEvidence row : evidence) {
      identities.add(row.getQualifiedConstraintName());
    }
    Collections.sort(identities);
    return immutableStrings(identities);
  }

  /** Returns deterministic installed-capacity definition signatures without sampled load. */
  private static List<String> installedDefinitions(List<InstalledEquipmentCapacityEvidence> evidence) {
    List<String> definitions = new ArrayList<String>();
    for (InstalledEquipmentCapacityEvidence row : evidence) {
      definitions.add(
          row.getQualifiedConstraintName() + "|" + row.getEquipmentClassName() + "|" + row.getReferenceDesignation()
              + "|" + row.getConstraintOrigin().name() + "|" + row.getConstraintType().name() + "|"
              + row.getSeverity().name() + "|" + row.isEnabled() + "|" + row.getLimitDirection().name() + "|"
              + Double.toString(row.getDesignValue()) + "|" + Double.toString(row.getMinimumValue()) + "|"
              + Double.toString(row.getMaximumValue()) + "|" + Double.toString(row.getApplicableLimit()) + "|"
              + Double.toString(row.getWarningThreshold()) + "|" + row.getPhysicalUnit() + "|" + row.getDataSource()
              + "|" + row.hasConfidence() + "|" + Double.toString(row.getConfidence()) + "|" + row.hasValidityRange()
              + "|" + Double.toString(row.getValidityMinimum()) + "|" + Double.toString(row.getValidityMaximum()));
    }
    Collections.sort(definitions);
    return immutableStrings(definitions);
  }

  /** Returns deterministic process-boundary identities. */
  private static List<String> boundaryIdentities(List<ProcessBoundaryConstraintEvidence> evidence) {
    List<String> identities = new ArrayList<String>();
    for (ProcessBoundaryConstraintEvidence row : evidence) {
      identities.add(row.getQualifiedConstraintName());
    }
    Collections.sort(identities);
    return immutableStrings(identities);
  }

  /** Returns an immutable defensive string list. */
  private static List<String> immutableStrings(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  /** Requires non-empty trimmed text. */
  private static String requireText(String value, String label) {
    if (value == null || value.trim().length() == 0) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  /** Returns a safe exception message. */
  private static String safeMessage(RuntimeException exception) {
    return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
  }

  /** @return stable compiled-plan identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable compiled-plan name */
  public String getName() {
    return name;
  }

  /** @return engineering provenance for compilation */
  public String getProvenance() {
    return provenance;
  }

  /** @return exact source baseline supplied by the caller */
  public String getSourceBaseline() {
    return sourceBaseline;
  }

  /** @return fresh immutable area names in compiled order */
  public List<String> getAreaNames() {
    return immutableStrings(areaNames);
  }

  /** @return fresh immutable ProcessSystem structure versions in area order */
  public List<Long> getAreaStructureVersions() {
    return Collections.unmodifiableList(new ArrayList<Long>(areaStructureVersions));
  }

  /** @return fresh immutable operating-action definitions in declaration order */
  public List<String> getActionDefinitions() {
    return immutableStrings(actionDefinitions);
  }

  /** @return fresh immutable required hydraulic-binding definitions in declaration order */
  public List<String> getHydraulicBindingDefinitions() {
    return immutableStrings(hydraulicBindingDefinitions);
  }

  /** @return fresh immutable exact installed-capacity identities */
  public List<String> getExpectedInstalledCapacityIdentities() {
    return immutableStrings(expectedInstalledCapacityIdentities);
  }

  /** @return fresh immutable exact process-boundary identities */
  public List<String> getExpectedBoundaryIdentities() {
    return immutableStrings(expectedBoundaryIdentities);
  }

  /** @return immutable no-change baseline qualification evidence */
  public CandidateSetEvaluationResult getCompilationEvidence() {
    return compilationEvidence;
  }

  /** @return true when every frozen definition still matches the attached model and evaluator */
  public boolean isCurrent() {
    return inspectStaleness().isEmpty();
  }

  /** @return fresh immutable staleness diagnostics */
  public List<String> getStalenessDiagnostics() {
    return immutableStrings(inspectStaleness());
  }

  /**
   * Returns a deterministic JSON-friendly compiled-plan definition.
   *
   * @return fresh ordered definition map
   */
  public Map<String, Object> getDefinition() {
    Map<String, Object> definition = new LinkedHashMap<String, Object>();
    definition.put("schemaVersion", SCHEMA_VERSION);
    definition.put("type", "ProcessModelCompiledEvaluationPlan");
    definition.put("id", id);
    definition.put("name", name);
    definition.put("provenance", provenance);
    definition.put("sourceBaseline", sourceBaseline);
    definition.put("areaNames", getAreaNames());
    definition.put("areaStructureVersions", getAreaStructureVersions());
    definition.put("actions", getActionDefinitions());
    definition.put("hydraulicBindings", getHydraulicBindingDefinitions());
    definition.put("evaluator", sanitize(JsonParser.parseString(evaluatorDefinitionJson)));
    definition.put("expectedInstalledCapacityIdentities", getExpectedInstalledCapacityIdentities());
    definition.put("expectedBoundaryIdentities", getExpectedBoundaryIdentities());
    Gson evidenceGson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    definition.put("compilationEvidence", sanitize(evidenceGson.toJsonTree(compilationEvidence)));
    definition.put("current", Boolean.valueOf(isCurrent()));
    definition.put("stalenessDiagnostics", getStalenessDiagnostics());
    return definition;
  }

  /** @return strict JSON plan definition with no non-finite numeric tokens */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(getDefinition());
  }

  /** Immutable result from one compiled-plan evaluation. */
  public static final class EvaluationResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Stable compiled-plan identifier. */
    private final String planId;

    /** Human-readable compiled-plan name. */
    private final String planName;

    /** Engineering provenance retained from compilation. */
    private final String planProvenance;

    /** Exact source baseline retained from compilation. */
    private final String sourceBaseline;

    /** Requested candidate values. */
    private final double[] candidateValues;

    /** Fail-closed outcome. */
    private final Outcome outcome;

    /** Complete delegated evidence, or null when rejected before model mutation. */
    private final CandidateSetEvaluationResult candidateEvidence;

    /** Exact expected installed-capacity coverage retained for external audit. */
    private final List<String> expectedInstalledCapacityIdentities;

    /** Exact expected process-boundary coverage retained for external audit. */
    private final List<String> expectedBoundaryIdentities;

    /** Immutable diagnostics. */
    private final List<String> diagnostics;

    /** Creates an immutable result. */
    private EvaluationResult(ProcessModelCompiledEvaluationPlan plan, double[] candidateValues, Outcome outcome,
        CandidateSetEvaluationResult candidateEvidence, List<String> diagnostics) {
      this.planId = plan.id;
      this.planName = plan.name;
      this.planProvenance = plan.provenance;
      this.sourceBaseline = plan.sourceBaseline;
      this.candidateValues = Arrays.copyOf(candidateValues, candidateValues.length);
      this.outcome = outcome;
      this.candidateEvidence = candidateEvidence;
      this.expectedInstalledCapacityIdentities = immutableStrings(plan.expectedInstalledCapacityIdentities);
      this.expectedBoundaryIdentities = immutableStrings(plan.expectedBoundaryIdentities);
      this.diagnostics = immutableStrings(diagnostics);
    }

    /** Creates an accepted result. */
    private static EvaluationResult accepted(ProcessModelCompiledEvaluationPlan plan, double[] candidateValues,
        CandidateSetEvaluationResult evidence) {
      List<String> diagnostics = new ArrayList<String>();
      diagnostics.add("Candidate passed the compiled plan and complete transactional replay");
      return new EvaluationResult(plan, candidateValues, Outcome.ACCEPTED, evidence, diagnostics);
    }

    /** Creates a rejected result. */
    private static EvaluationResult rejected(ProcessModelCompiledEvaluationPlan plan, double[] candidateValues,
        Outcome outcome, CandidateSetEvaluationResult evidence, List<String> diagnostics) {
      return new EvaluationResult(plan, candidateValues, outcome, evidence, diagnostics);
    }

    /** @return stable compiled-plan identifier */
    public String getPlanId() {
      return planId;
    }

    /** @return human-readable compiled-plan name */
    public String getPlanName() {
      return planName;
    }

    /** @return compiled-plan provenance */
    public String getPlanProvenance() {
      return planProvenance;
    }

    /** @return exact source baseline supplied at compilation */
    public String getSourceBaseline() {
      return sourceBaseline;
    }

    /** @return defensive candidate array */
    public double[] getCandidateValues() {
      return Arrays.copyOf(candidateValues, candidateValues.length);
    }

    /** @return fail-closed outcome */
    public Outcome getOutcome() {
      return outcome;
    }

    /** @return true only when every compiled and delegated acceptance gate passed */
    public boolean isAccepted() {
      return outcome == Outcome.ACCEPTED;
    }

    /** @return complete delegated immutable evidence, or null for a preflight rejection */
    public CandidateSetEvaluationResult getCandidateEvidence() {
      return candidateEvidence;
    }

    /** @return fresh immutable expected installed-capacity identities */
    public List<String> getExpectedInstalledCapacityIdentities() {
      return immutableStrings(expectedInstalledCapacityIdentities);
    }

    /** @return fresh immutable expected process-boundary identities */
    public List<String> getExpectedBoundaryIdentities() {
      return immutableStrings(expectedBoundaryIdentities);
    }

    /** @return fresh immutable diagnostics */
    public List<String> getDiagnostics() {
      return immutableStrings(diagnostics);
    }

    /** @return strict JSON with complete delegated evidence and non-finite values represented as null */
    public String toJson() {
      Gson evidenceGson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", SCHEMA_VERSION);
      result.put("type", "ProcessModelCompiledEvaluationResult");
      result.put("planId", planId);
      result.put("planName", planName);
      result.put("planProvenance", planProvenance);
      result.put("sourceBaseline", sourceBaseline);
      result.put("candidateValues", candidateValues);
      result.put("outcome", outcome.name());
      result.put("accepted", Boolean.valueOf(isAccepted()));
      result.put("expectedInstalledCapacityIdentities", expectedInstalledCapacityIdentities);
      result.put("expectedBoundaryIdentities", expectedBoundaryIdentities);
      result.put("diagnostics", diagnostics);
      JsonElement resultTree = evidenceGson.toJsonTree(result);
      JsonObject root = sanitize(resultTree).getAsJsonObject();
      root.add("candidateEvidence", sanitize(evidenceGson.toJsonTree(candidateEvidence)));
      return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }
  }

  /** Rebuilds a JSON tree with non-finite numeric primitives represented as JSON null. */
  private static JsonElement sanitize(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return JsonNull.INSTANCE;
    }
    if (element.isJsonPrimitive()) {
      JsonPrimitive primitive = element.getAsJsonPrimitive();
      if (primitive.isNumber() && !Double.isFinite(primitive.getAsDouble())) {
        return JsonNull.INSTANCE;
      }
      if (primitive.isString() && isNonFiniteToken(primitive.getAsString())) {
        return JsonNull.INSTANCE;
      }
      return primitive;
    }
    if (element.isJsonArray()) {
      JsonArray clean = new JsonArray();
      for (JsonElement value : element.getAsJsonArray()) {
        clean.add(sanitize(value));
      }
      return clean;
    }
    JsonObject clean = new JsonObject();
    for (Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
      clean.add(entry.getKey(), sanitize(entry.getValue()));
    }
    return clean;
  }

  /** Returns true for exact non-finite tokens emitted by existing evaluator JSON views. */
  private static boolean isNonFiniteToken(String value) {
    return "NaN".equals(value) || "Infinity".equals(value) || "-Infinity".equals(value);
  }
}
