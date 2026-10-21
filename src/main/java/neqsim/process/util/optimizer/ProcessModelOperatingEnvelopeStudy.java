package neqsim.process.util.optimizer;

import static neqsim.util.util.ArrayUtils.copyArray;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicConstraintBinding;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicConstraintSnapshot;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateObjectiveEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingPointOptimizer.OperatingPointSearchResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ConstraintDefinition;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;

/**
 * Samples one- or two-dimensional operating-envelope slices with the existing fail-closed transactional action
 * evaluator.
 *
 * <p>
 * Every point is a complete action vector evaluated by {@link ProcessModelOperatingActionSetEvaluator}. Infeasible
 * candidates remain in the trace when baseline recovery succeeds. Sampling stops immediately when restoration or
 * restored-baseline convergence fails. Leading-bottleneck transitions compare the evaluator's finite normalized
 * installed-equipment and typed plant/shared/coupled evidence for feasible points. Infeasible points retain the exact
 * fail-closed rejection source: first the applicable required hydraulic binding, then typed plant/shared/coupled
 * evidence, then the first violated hard registered model constraint in declaration order. Physical values with unlike
 * units are never numerically ranked against each other. Transitions are sampled identity changes, not proof of a
 * continuous boundary, an optimizer active set, or operating approval.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelOperatingEnvelopeStudy {
  /** Terminal study state. */
  public enum Outcome {
    /** Every requested point was evaluated safely. */
    COMPLETE,
    /** Sampling stopped because the mutable process baseline was not safely recovered. */
    MODEL_RECOVERY_FAILED
  }

  private final String id;
  private final String name;
  private final String provenance;
  private final ProcessModelOperatingActionSetEvaluator evaluator;
  /** Maximum fail-closed midpoint evaluations for each eligible continuous transition. */
  private int maximumBoundaryRefinementEvaluations;
  /** Final refined bracket width relative to the continuous action range. */
  private double boundaryRefinementRelativeTolerance = 1.0e-3;
  /** Engineering source of the refinement tolerance. */
  private String boundaryRefinementToleranceProvenance = "diagnostic continuous-action range fraction";

  /**
   * Creates a study around an existing transactional evaluator.
   *
   * @param id stable study identifier
   * @param name human-readable name
   * @param provenance engineering basis for the slice
   * @param evaluator configured action-set evaluator
   */
  public ProcessModelOperatingEnvelopeStudy(String id, String name, String provenance,
      ProcessModelOperatingActionSetEvaluator evaluator) {
    this.id = requireText(id, "Study identifier");
    this.name = requireText(name, "Study name");
    this.provenance = requireText(provenance, "Study provenance");
    if (evaluator == null) {
      throw new IllegalArgumentException("Candidate evaluator must not be null");
    }
    this.evaluator = evaluator;
  }

  /** @return stable study identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable study name */
  public String getName() {
    return name;
  }

  /** @return study provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return configured transactional evaluator */
  public ProcessModelOperatingActionSetEvaluator getCandidateEvaluator() {
    return evaluator;
  }

  /**
   * Enables fail-closed bisection of eligible continuous feasibility transitions.
   *
   * <p>
   * Refinement is disabled by default. Each midpoint runs through the complete transactional candidate evaluator and is
   * retained only when candidate convergence, feasibility classification, finite candidate values, baseline
   * restoration, and restored-baseline convergence are explicit. Discrete actions and non-feasibility transition kinds
   * are never refined.
   * </p>
   *
   * @param maximumEvaluations non-negative maximum midpoint evaluations per eligible transition
   * @return this study
   */
  public ProcessModelOperatingEnvelopeStudy setMaximumBoundaryRefinementEvaluations(int maximumEvaluations) {
    if (maximumEvaluations < 0) {
      throw new IllegalArgumentException("Maximum boundary-refinement evaluations must be non-negative");
    }
    this.maximumBoundaryRefinementEvaluations = maximumEvaluations;
    return this;
  }

  /**
   * Sets the terminal refined bracket width relative to the traversed continuous action range.
   *
   * @param relativeTolerance finite positive range fraction
   * @param toleranceProvenance engineering source of the tolerance
   * @return this study
   */
  public ProcessModelOperatingEnvelopeStudy setBoundaryRefinementRelativeTolerance(double relativeTolerance,
      String toleranceProvenance) {
    if (!Double.isFinite(relativeTolerance) || relativeTolerance <= 0.0) {
      throw new IllegalArgumentException("Boundary-refinement relative tolerance must be finite and positive");
    }
    this.boundaryRefinementRelativeTolerance = relativeTolerance;
    this.boundaryRefinementToleranceProvenance = requireText(toleranceProvenance,
        "Boundary-refinement tolerance provenance");
    return this;
  }

  /**
   * Samples one action while holding all other actions at anchor values.
   *
   * @param actionId exact action identifier
   * @param values ordered axis values
   * @param anchorValues full action vector for non-axis values
   * @return immutable slice result
   */
  public SliceResult evaluateOneDimensional(String actionId, double[] values, double[] anchorValues) {
    return evaluate(new String[] {actionId}, new double[][] {copyArray(values)}, anchorValues);
  }

  /**
   * Samples one action around the accepted and replayed point from a compatible mixed operating-point search.
   *
   * <p>
   * The search result must retain a feasible, finite, completely evidenced accepted-point replay from the same
   * unchanged evaluator definition. The current process baseline must still be converged and match the replay's
   * captured baseline action values. Validation is read-only and completes before any slice point is evaluated.
   * </p>
   *
   * @param actionId exact action identifier
   * @param values ordered axis values
   * @param search completed mixed operating-point search
   * @return immutable slice anchored at the accepted replay values
   */
  public SliceResult evaluateOneDimensional(String actionId, double[] values, OperatingPointSearchResult search) {
    return evaluateOneDimensional(actionId, values, validateAcceptedSearch(search));
  }

  /**
   * Samples two distinct actions in deterministic row-major order.
   *
   * @param firstActionId first-axis action identifier
   * @param firstValues first-axis values
   * @param secondActionId second-axis action identifier
   * @param secondValues second-axis values
   * @param anchorValues full action vector for non-axis values
   * @return immutable slice result
   */
  public SliceResult evaluateTwoDimensional(String firstActionId, double[] firstValues, String secondActionId,
      double[] secondValues, double[] anchorValues) {
    return evaluate(new String[] {firstActionId, secondActionId},
        new double[][] {copyArray(firstValues), copyArray(secondValues)}, anchorValues);
  }

  /**
   * Samples two actions around the accepted and replayed point from a compatible mixed operating-point search.
   *
   * @param firstActionId first-axis action identifier
   * @param firstValues first-axis values
   * @param secondActionId second-axis action identifier
   * @param secondValues second-axis values
   * @param search completed mixed operating-point search
   * @return immutable slice anchored at the accepted replay values
   */
  public SliceResult evaluateTwoDimensional(String firstActionId, double[] firstValues, String secondActionId,
      double[] secondValues, OperatingPointSearchResult search) {
    return evaluateTwoDimensional(firstActionId, firstValues, secondActionId, secondValues,
        validateAcceptedSearch(search));
  }

  /**
   * Reports the leading bottleneck retained by a compatible accepted-point replay without rerunning the model.
   *
   * <p>
   * The selected row uses the same unit-safe ranking and exact fail-closed rejection identity as sampled envelope
   * points. A null result means the replay contains no applicable installed or rejecting constraint evidence. This is
   * sampled operating evidence, not a shadow price, causal attribution, global active set, or operating approval.
   * </p>
   *
   * @param search completed mixed operating-point search
   * @return immutable leading constraint evidence, or null when no applicable row exists
   */
  public LeadingConstraintEvidence getAcceptedPointBottleneck(OperatingPointSearchResult search) {
    validateAcceptedSearch(search);
    CandidateSetEvaluationResult replay = search.getAcceptedPointReplay().getEvaluation();
    return leading(replay, leadingInstalled(replay));
  }

  /**
   * Builds one compact machine-readable evidence block for an accepted point and its completed envelope slices.
   *
   * <p>
   * Every slice must have been produced by this exact runtime study, be anchored at the accepted candidate, and have
   * completed with safe baseline recovery. Deserialized or foreign slices cannot be combined into new current-cycle
   * evidence. The returned map contains the existing optimizer guard block, accepted-point bottleneck, per-slice points
   * and transitions, and measured evaluation-count/runtime provenance. Building the map does not rerun or mutate the
   * process model.
   * </p>
   *
   * @param cycleId current living-task or engineering-cycle identifier
   * @param search completed mixed operating-point search
   * @param slices one or more completed slices anchored at the accepted point
   * @return deeply immutable JSON-friendly evidence map
   */
  public Map<String, Object> getAcceptedPointEnvelopeEvidence(String cycleId, OperatingPointSearchResult search,
      SliceResult... slices) {
    String currentCycleId = requireText(cycleId, "Cycle identifier");
    double[] accepted = validateAcceptedSearch(search);
    if (slices == null || slices.length == 0) {
      throw new IllegalArgumentException("Accepted-point envelope evidence requires at least one slice");
    }

    List<Map<String, Object>> sliceEvidence = new ArrayList<Map<String, Object>>();
    List<String[]> axisSignatures = new ArrayList<String[]>();
    long envelopeEvaluationCount = 0L;
    double envelopeRuntimeSeconds = 0.0;
    int requestedPointCount = 0;
    int completedPointCount = 0;
    for (SliceResult slice : slices) {
      if (slice == null || !slice.isFromStudy(this)) {
        throw new IllegalStateException("Envelope evidence requires slices from this exact runtime study");
      }
      if (!slice.isComplete()) {
        throw new IllegalStateException("Envelope evidence requires complete slices with safe baseline recovery");
      }
      if (!sameVector(accepted, slice.getAnchorValues())) {
        throw new IllegalStateException("Envelope slice is not anchored at the accepted candidate");
      }
      String[] axisSignature = slice.getAxisActionIds();
      if (containsAxisSignature(axisSignatures, axisSignature)) {
        throw new IllegalArgumentException(
            "Envelope evidence contains duplicate slice axes: " + Arrays.toString(axisSignature));
      }
      axisSignatures.add(axisSignature);
      envelopeEvaluationCount += slice.getEvaluationCount();
      envelopeRuntimeSeconds += slice.getRuntimeSeconds();
      requestedPointCount = Math.addExact(requestedPointCount, slice.getRequestedPointCount());
      completedPointCount = Math.addExact(completedPointCount, slice.getCompletedPointCount());
      sliceEvidence.add(slice.toEvidenceMap());
    }
    if (!Double.isFinite(envelopeRuntimeSeconds)) {
      throw new IllegalStateException("Envelope runtime evidence is not finite");
    }

    CandidateSetEvaluationResult replay = search.getAcceptedPointReplay().getEvaluation();
    LeadingConstraintEvidence acceptedBottleneck = leading(replay, leadingInstalled(replay));
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("schema_version", Integer.valueOf(1));
    evidence.put("cycle_id", currentCycleId);
    evidence.put("study_id", id);
    evidence.put("study_name", name);
    evidence.put("study_provenance", provenance);
    evidence.put("candidate_evaluator_id", evaluator.getId());
    evidence.put("optimizer_evidence", search.getOptimizerEvidence(currentCycleId));
    List<String> acceptedActionIds = new ArrayList<String>();
    for (ProcessModelOperatingAction action : evaluator.getActions()) {
      acceptedActionIds.add(action.getId());
    }
    evidence.put("accepted_action_ids", Collections.unmodifiableList(acceptedActionIds));
    evidence.put("accepted_candidate_values", toFiniteDoubleList(accepted));
    evidence.put("accepted_point_bottleneck", acceptedBottleneck == null ? null : acceptedBottleneck.toEvidenceMap());
    evidence.put("slice_count", Integer.valueOf(slices.length));
    evidence.put("requested_point_count", Integer.valueOf(requestedPointCount));
    evidence.put("completed_point_count", Integer.valueOf(completedPointCount));
    evidence.put("envelope_evaluation_count", Long.valueOf(envelopeEvaluationCount));
    evidence.put("envelope_runtime_seconds", Double.valueOf(envelopeRuntimeSeconds));
    evidence.put("total_evaluation_count", Long.valueOf(search.getEvaluationCount() + envelopeEvaluationCount));
    evidence.put("slices", Collections.unmodifiableList(sliceEvidence));
    evidence.put("advisory", Boolean.TRUE);
    evidence.put("interpretation",
        "Sampled operating evidence; not a continuous boundary, global optimum, shadow price, or operating approval");
    return Collections.unmodifiableMap(evidence);
  }

  /** Returns true when the ordered axis identifier vector already exists. */
  private static boolean containsAxisSignature(List<String[]> signatures, String[] candidate) {
    for (String[] signature : signatures) {
      if (Arrays.equals(signature, candidate)) {
        return true;
      }
    }
    return false;
  }

  /** Returns true when two finite vectors are bitwise identical. */
  private static boolean sameVector(double[] first, double[] second) {
    if (first.length != second.length) {
      return false;
    }
    for (int index = 0; index < first.length; index++) {
      if (!sameDouble(first[index], second[index])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validates that a replayed accepted point is complete, definition-compatible, and current for this study.
   *
   * @param search completed mixed operating-point search
   * @return defensive accepted action vector
   */
  private double[] validateAcceptedSearch(OperatingPointSearchResult search) {
    if (search == null) {
      throw new IllegalArgumentException("Operating-point search result must not be null");
    }
    if (!search.isAcceptedPointReplayed() || !search.isCandidateFinite() || !search.isActionsComplete()
        || !search.isConstraintEvidenceComplete()) {
      throw new IllegalStateException(
          "Operating-envelope anchoring requires a finite, completely evidenced accepted-point replay");
    }
    CandidateSetEvaluationResult replay = search.getAcceptedPointReplay().getEvaluation();
    if (!replay.isCandidateSimulationConverged() || !replay.isFeasible() || !replay.isBaselineRestored()
        || !replay.isBaselineSimulationConverged()) {
      throw new IllegalStateException("Accepted-point replay is not feasible with complete converged recovery");
    }
    if (!search.isFromEvaluator(evaluator)) {
      throw new IllegalStateException(
          "Operating-envelope anchoring requires the exact runtime evaluator that produced the replay");
    }
    if (!sameText(search.getActionSetId(), evaluator.getId()) || !sameText(replay.getId(), evaluator.getId())
        || !sameText(replay.getName(), evaluator.getName())
        || !sameText(replay.getProvenance(), evaluator.getProvenance())) {
      throw new IllegalArgumentException("Operating-point search belongs to a different action-set evaluator");
    }
    if (!sameActions(replay.getActions(), evaluator.getActions())
        || !sameObjectives(replay.getObjectiveEvidence(), evaluator.getSimulationEvaluator().getObjectives())
        || !sameConstraints(replay.getConstraintEvidence(), evaluator.getSimulationEvaluator().getConstraints(),
            replay.getInstalledEquipmentCapacityEvidence())
        || !sameHydraulicBindings(replay.getHydraulicConstraints(), evaluator.getRequiredHydraulicConstraints())) {
      throw new IllegalStateException("Operating-point evaluator definition changed after accepted-point replay");
    }
    if (!evaluator.getSimulationEvaluator().getProcessModel().isModelConverged()) {
      throw new IllegalStateException("Current process baseline is not converged");
    }
    double[] baseline = replay.getBaselineValues();
    List<ProcessModelOperatingAction> actions = evaluator.getActions();
    if (baseline.length != actions.size()) {
      throw new IllegalStateException("Accepted-point replay does not contain a complete baseline action vector");
    }
    for (int index = 0; index < actions.size(); index++) {
      ProcessModelOperatingAction action = actions.get(index);
      ProcessModelOperatingAction.CapabilityAssessment capability = action
          .inspectCapability(evaluator.getSimulationEvaluator().getProcessModel());
      if (!capability.isAvailable() || !capability.isCurrentValueWithinDomain()
          || !sameActionValue(action, baseline[index], capability.getCurrentValue())) {
        throw new IllegalStateException("Current process baseline is stale for action " + action.getId());
      }
    }
    return validateAnchor(actions, search.getAcceptedCandidateValues());
  }

  /** Returns true when two frozen action lists have identical declaration-order definitions. */
  private static boolean sameActions(List<ProcessModelOperatingAction> frozen,
      List<ProcessModelOperatingAction> current) {
    if (frozen.size() != current.size()) {
      return false;
    }
    for (int index = 0; index < frozen.size(); index++) {
      ProcessModelOperatingAction first = frozen.get(index);
      ProcessModelOperatingAction second = current.get(index);
      if (!sameText(first.getId(), second.getId()) || !sameText(first.getName(), second.getName())
          || !sameText(first.getAddress(), second.getAddress()) || !sameText(first.getUnit(), second.getUnit())
          || !sameText(first.getProvenance(), second.getProvenance())
          || first.getValueSemantics() != second.getValueSemantics()
          || !sameDouble(first.getLowerBound(), second.getLowerBound())
          || !sameDouble(first.getUpperBound(), second.getUpperBound())
          || !Arrays.equals(first.getAllowedValues(), second.getAllowedValues())
          || !sameDouble(first.getReadBackAbsoluteTolerance(), second.getReadBackAbsoluteTolerance())
          || !sameDouble(first.getReadBackRelativeTolerance(), second.getReadBackRelativeTolerance())
          || !sameText(first.getReadBackToleranceProvenance(), second.getReadBackToleranceProvenance())) {
        return false;
      }
    }
    return true;
  }

  /** Returns true when frozen objective identities match the current evaluator definition. */
  private static boolean sameObjectives(List<CandidateObjectiveEvidence> frozen, List<ObjectiveDefinition> current) {
    if (frozen.size() != current.size()) {
      return false;
    }
    for (int index = 0; index < frozen.size(); index++) {
      CandidateObjectiveEvidence first = frozen.get(index);
      ObjectiveDefinition second = current.get(index);
      if (first.getIndex() != index || !sameText(first.getName(), second.getName())
          || first.getDirection() != second.getDirection() || !sameText(first.getUnit(), second.getUnit())
          || !sameDouble(first.getWeight(), second.getWeight())) {
        return false;
      }
    }
    return true;
  }

  /** Returns true when frozen constraint identities match the current evaluator definition. */
  private static boolean sameConstraints(List<CandidateConstraintEvidence> frozen, List<ConstraintDefinition> current,
      List<InstalledEquipmentCapacityEvidence> installedEvidence) {
    if (frozen.size() != current.size()) {
      return false;
    }
    for (int index = 0; index < frozen.size(); index++) {
      CandidateConstraintEvidence first = frozen.get(index);
      ConstraintDefinition second = current.get(index);
      String currentPhysicalUnit = second.isPlantConstraint() ? second.getPlantConstraintDefinition().getUnit()
          : second.getCapacityPhysicalUnit();
      if (first.getIndex() != index || !sameText(first.getName(), second.getName())
          || first.getType() != second.getType() || !sameText(first.getUnit(), second.getUnit())
          || !sameText(first.getPhysicalUnit(), currentPhysicalUnit) || first.isHard() != second.isHard()
          || !sameDouble(first.getPenaltyWeight(), second.getPenaltyWeight())
          || !sameDouble(first.getLowerBound(), second.getLowerBound())
          || !sameDouble(first.getUpperBound(), second.getUpperBound())
          || !sameDouble(first.getEqualityTolerance(), second.getEqualityTolerance())
          || first.isCapacityConstraint() != second.isCapacityConstraint()
          || !sameText(first.getAreaName(), second.getAreaName())
          || !sameText(first.getEquipmentName(), second.getEquipmentName())
          || !sameText(first.getEquipmentConstraintName(), second.getEquipmentConstraintName())
          || !sameCapacityDefinition(first, second, installedEvidence)) {
        return false;
      }
    }
    return true;
  }

  /** Returns true when a mutable installed-capacity definition still matches its replay snapshot. */
  private static boolean sameCapacityDefinition(CandidateConstraintEvidence frozen, ConstraintDefinition current,
      List<InstalledEquipmentCapacityEvidence> installedEvidence) {
    if (!current.isCapacityConstraint()) {
      return true;
    }
    CapacityConstraint definition = current.getCapturedCapacityConstraint();
    InstalledEquipmentCapacityEvidence snapshot = findInstalledEvidence(frozen, installedEvidence);
    return definition != null && snapshot != null && definition.getType() == snapshot.getConstraintType()
        && definition.getSeverity() == snapshot.getSeverity() && definition.isEnabled() == snapshot.isEnabled()
        && definition.isMinimumConstraint() == snapshot.isMinimumConstraint()
        && sameText(definition.getUnit(), snapshot.getPhysicalUnit())
        && sameDouble(definition.getDesignValue(), snapshot.getDesignValue())
        && sameDouble(definition.getMinValue(), snapshot.getMinimumValue())
        && sameDouble(definition.getMaxValue(), snapshot.getMaximumValue())
        && sameDouble(definition.getApplicableLimit(), snapshot.getApplicableLimit())
        && sameDouble(definition.getWarningThreshold(), snapshot.getWarningThreshold())
        && sameSafeText(definition.getDescription(), snapshot.getDescription())
        && sameText(definition.getDataSource() == null ? "not_set" : definition.getDataSource(),
            snapshot.getDataSource())
        && sameSafeText(definition.getBasis(), snapshot.getDesignBasis())
        && definition.getSource() == snapshot.getDesignSource()
        && sameSafeText(definition.getSourceReference(), snapshot.getDesignSourceReference())
        && definition.hasOperatingLimit() == snapshot.hasOperatingLimit()
        && (!definition.hasOperatingLimit() || sameDouble(definition.getOperatingLimit(), snapshot.getOperatingLimit())
            && definition.getOperatingLimitSource() == snapshot.getOperatingLimitSource()
            && sameSafeText(definition.getOperatingLimitSourceReference(), snapshot.getOperatingLimitSourceReference()))
        && definition.getApplicableLimitRole() == snapshot.getApplicableLimitRole()
        && definition.getApplicableLimitSource() == snapshot.getApplicableLimitSource()
        && sameSafeText(definition.getApplicableLimitSourceReference(), snapshot.getApplicableLimitSourceReference())
        && definition.hasApplicableLimitConfidence() == snapshot.hasConfidence()
        && (!definition.hasApplicableLimitConfidence()
            || sameDouble(definition.getApplicableLimitConfidence(), snapshot.getConfidence()))
        && definition.hasApplicableLimitValidityRange() == snapshot.hasValidityRange()
        && (!definition.hasApplicableLimitValidityRange()
            || sameDouble(definition.getApplicableLimitValidityMinimum(), snapshot.getValidityMinimum())
                && sameDouble(definition.getApplicableLimitValidityMaximum(), snapshot.getValidityMaximum()));
  }

  /** Finds the exact installed-capacity snapshot corresponding to one registered constraint. */
  private static InstalledEquipmentCapacityEvidence findInstalledEvidence(CandidateConstraintEvidence constraint,
      List<InstalledEquipmentCapacityEvidence> evidence) {
    String qualifiedName = constraint.getAreaName() + "::" + constraint.getEquipmentName() + "/"
        + constraint.getEquipmentConstraintName();
    for (InstalledEquipmentCapacityEvidence snapshot : evidence) {
      if (qualifiedName.equals(snapshot.getQualifiedConstraintName())) {
        return snapshot;
      }
    }
    return null;
  }

  /** Returns true when required hydraulic bindings remain identical and in declaration order. */
  private static boolean sameHydraulicBindings(List<HydraulicConstraintSnapshot> frozen,
      List<HydraulicConstraintBinding> current) {
    if (frozen.size() != current.size()) {
      return false;
    }
    for (int index = 0; index < frozen.size(); index++) {
      HydraulicConstraintBinding first = frozen.get(index).getBinding();
      HydraulicConstraintBinding second = current.get(index);
      if (first.getRole() != second.getRole() || !sameText(first.getAreaName(), second.getAreaName())
          || !sameText(first.getEquipmentName(), second.getEquipmentName())
          || !sameText(first.getConstraintName(), second.getConstraintName())
          || !sameText(first.getProvenance(), second.getProvenance())) {
        return false;
      }
    }
    return true;
  }

  /** Returns true when the current baseline action value matches the replay-captured baseline. */
  private static boolean sameActionValue(ProcessModelOperatingAction action, double expected, double actual) {
    if (action.isDiscrete()) {
      return sameDouble(expected, actual);
    }
    double tolerance = Math.max(action.getReadBackAbsoluteTolerance(),
        action.getReadBackRelativeTolerance() * Math.max(1.0, Math.abs(expected)));
    return Math.abs(actual - expected) <= tolerance;
  }

  /** Returns true for equal nullable text values. */
  private static boolean sameText(String first, String second) {
    return first == null ? second == null : first.equals(second);
  }

  /** Returns true when nullable definition text matches snapshot text normalized to empty. */
  private static boolean sameSafeText(String first, String second) {
    return (first == null ? "" : first).equals(second == null ? "" : second);
  }

  /** Returns true for bitwise-identical doubles, including infinities used by constraint bounds. */
  private static boolean sameDouble(double first, double second) {
    return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
  }

  /**
   * Samples a validated one- or two-dimensional grid.
   *
   * @param axisIds axis action identifiers
   * @param axes requested axis values
   * @param anchorValues full action vector
   * @return immutable complete or fail-closed partial result
   */
  private SliceResult evaluate(String[] axisIds, double[][] axes, double[] anchorValues) {
    List<ProcessModelOperatingAction> actions = evaluator.getActions();
    double[] anchor = validateAnchor(actions, anchorValues);
    int[] actionIndexes = validateAxes(actions, axisIds, axes);
    ProcessModelOperatingAction.ValueSemantics[] axisActionSemantics = new ProcessModelOperatingAction.ValueSemantics[axisIds.length];
    for (int axis = 0; axis < axisIds.length; axis++) {
      axisActionSemantics[axis] = actions.get(actionIndexes[axis]).getValueSemantics();
    }
    int evaluationCountBefore = evaluator.getSimulationEvaluator().getEvaluationCount();
    long startedNanos = System.nanoTime();
    List<Point> points = new ArrayList<Point>();
    List<Transition> transitions = new ArrayList<Transition>();
    List<OperatingRegionTransition> operatingRegionTransitions = new ArrayList<OperatingRegionTransition>();
    List<OperatingRegionBoundaryRefinement> boundaryRefinements = new ArrayList<OperatingRegionBoundaryRefinement>();
    List<String> diagnostics = new ArrayList<String>();
    Outcome outcome = Outcome.COMPLETE;

    if (axisIds.length == 1) {
      for (int i = 0; i < axes[0].length; i++) {
        Point point = evaluatePoint(anchor, actionIndexes, axes, new int[] {i}, points.size());
        points.add(point);
        if (i > 0) {
          transition(transitions, axisIds[0], points.get(i - 1), point, 0);
          operatingRegionTransition(operatingRegionTransitions, actions.get(actionIndexes[0]), points.get(i - 1), point,
              0);
        }
        if (!safeRecovery(point)) {
          outcome = Outcome.MODEL_RECOVERY_FAILED;
          diagnostics.add("Stopped after point " + point.getSequenceIndex()
              + " because baseline restoration or reconvergence failed");
          break;
        }
      }
    } else {
      int secondCount = axes[1].length;
      boolean stop = false;
      for (int i = 0; i < axes[0].length && !stop; i++) {
        for (int j = 0; j < secondCount; j++) {
          Point point = evaluatePoint(anchor, actionIndexes, axes, new int[] {i, j}, points.size());
          points.add(point);
          if (j > 0) {
            transition(transitions, axisIds[1], points.get(points.size() - 2), point, 1);
            operatingRegionTransition(operatingRegionTransitions, actions.get(actionIndexes[1]),
                points.get(points.size() - 2), point, 1);
          }
          if (i > 0) {
            transition(transitions, axisIds[0], points.get((i - 1) * secondCount + j), point, 0);
            operatingRegionTransition(operatingRegionTransitions, actions.get(actionIndexes[0]),
                points.get((i - 1) * secondCount + j), point, 0);
          }
          if (!safeRecovery(point)) {
            outcome = Outcome.MODEL_RECOVERY_FAILED;
            diagnostics.add("Stopped after point " + point.getSequenceIndex()
                + " because baseline restoration or reconvergence failed");
            stop = true;
            break;
          }
        }
      }
    }

    if (outcome == Outcome.COMPLETE && maximumBoundaryRefinementEvaluations > 0) {
      for (OperatingRegionTransition transition : operatingRegionTransitions) {
        if (!eligibleForBoundaryRefinement(transition)) {
          continue;
        }
        int actionIndex = findAction(actions, transition.getAxisActionId());
        OperatingRegionBoundaryRefinement refinement = refineBoundary(actions.get(actionIndex), actionIndex, transition,
            points.get(transition.getFromSequenceIndex()), points.get(transition.getToSequenceIndex()));
        boundaryRefinements.add(refinement);
        if (refinement.getStatus() == OperatingRegionBoundaryRefinement.Status.MODEL_RECOVERY_FAILED) {
          outcome = Outcome.MODEL_RECOVERY_FAILED;
          diagnostics.add("Stopped during continuous operating-region boundary refinement because baseline "
              + "restoration or reconvergence failed");
          break;
        }
      }
    }

    if (outcome == Outcome.COMPLETE) {
      diagnostics.add("All requested points completed with safe baseline recovery");
    }
    diagnostics.add(
        "Transitions use unit-safe installed evidence at feasible points and exact fail-closed rejection identity at infeasible points");
    diagnostics.add(
        "Operating-region transitions are sampled adjacent-edge diagnostics; discrete actions are never interpolated");
    if (maximumBoundaryRefinementEvaluations > 0) {
      diagnostics.add("Eligible continuous feasibility transitions use fail-closed midpoint refinement; refined "
          + "brackets remain advisory and require a separately accepted operating-point replay");
    }
    int evaluationCount = evaluator.getSimulationEvaluator().getEvaluationCount() - evaluationCountBefore;
    double runtimeSeconds = Math.max(0.0, (System.nanoTime() - startedNanos) / 1.0e9);
    return new SliceResult(id, name, provenance, evaluator.getId(), axisIds, axisActionSemantics, axes, anchor,
        pointCount(axes), outcome, points, transitions, operatingRegionTransitions, boundaryRefinements, diagnostics,
        evaluationCount, runtimeSeconds, maximumBoundaryRefinementEvaluations, boundaryRefinementRelativeTolerance,
        boundaryRefinementToleranceProvenance, this);
  }

  /** Returns true only for a continuous sampled feasibility transition eligible for bisection. */
  private static boolean eligibleForBoundaryRefinement(OperatingRegionTransition transition) {
    ProcessModelOperatingActionSetEvaluator.Outcome infeasibleOutcome = transition.isFromFeasible()
        ? transition.getToOutcome()
        : transition.getFromOutcome();
    ProcessModelOperatingActionSetEvaluator.Outcome feasibleOutcome = transition.isFromFeasible()
        ? transition.getFromOutcome()
        : transition.getToOutcome();
    return !transition.isDiscreteAction() && feasibleOutcome == ProcessModelOperatingActionSetEvaluator.Outcome.FEASIBLE
        && refinableOutcome(infeasibleOutcome)
        && (transition.getKind() == OperatingRegionTransition.Kind.BECAME_FEASIBLE
            || transition.getKind() == OperatingRegionTransition.Kind.BECAME_INFEASIBLE);
  }

  /**
   * Refines one continuous feasible/infeasible bracket without changing any physical acceptance rule.
   *
   * @param action traversed continuous action
   * @param actionIndex action position in the complete candidate vector
   * @param transition sampled transition being refined
   * @param from sampled source point
   * @param to sampled destination point
   * @return immutable refinement receipt
   */
  private OperatingRegionBoundaryRefinement refineBoundary(ProcessModelOperatingAction action, int actionIndex,
      OperatingRegionTransition transition, Point from, Point to) {
    Point feasible = from.isFeasible() ? from : to;
    Point infeasible = from.isFeasible() ? to : from;
    double actionRange = action.getUpperBound() - action.getLowerBound();
    if (!Double.isFinite(actionRange) || actionRange <= 0.0) {
      throw new IllegalStateException("Continuous action range must be finite and positive for boundary refinement");
    }
    double absoluteTolerance = boundaryRefinementRelativeTolerance * actionRange;
    List<OperatingRegionBoundaryRefinement.Step> steps = new ArrayList<OperatingRegionBoundaryRefinement.Step>();
    OperatingRegionBoundaryRefinement.Status status = null;

    for (int iteration = 0; iteration < maximumBoundaryRefinementEvaluations; iteration++) {
      double feasibleValue = feasible.getCandidateValues()[actionIndex];
      double infeasibleValue = infeasible.getCandidateValues()[actionIndex];
      if (Math.abs(infeasibleValue - feasibleValue) <= absoluteTolerance) {
        status = OperatingRegionBoundaryRefinement.Status.CONVERGED;
        break;
      }
      double midpoint = feasibleValue + 0.5 * (infeasibleValue - feasibleValue);
      if (!Double.isFinite(midpoint) || Double.compare(midpoint, feasibleValue) == 0
          || Double.compare(midpoint, infeasibleValue) == 0) {
        status = OperatingRegionBoundaryRefinement.Status.NUMERICAL_STAGNATION;
        break;
      }
      double[] candidate = feasible.getCandidateValues();
      candidate[actionIndex] = midpoint;
      CandidateSetEvaluationResult evaluation = evaluator.evaluate(candidate);
      InstalledEquipmentCapacityEvidence installed = leadingInstalled(evaluation);
      Point evaluated = new Point(iteration, new int[0], new double[] {midpoint}, candidate, evaluation,
          leading(evaluation, installed), installed);
      steps.add(OperatingRegionBoundaryRefinement.Step.fromPoint(iteration, evaluated));
      if (!safeRecovery(evaluated)) {
        status = OperatingRegionBoundaryRefinement.Status.MODEL_RECOVERY_FAILED;
        break;
      }
      if (eligibleRefinementFeasible(evaluated)) {
        feasible = evaluated;
      } else if (refinableRejection(evaluated)) {
        infeasible = evaluated;
      } else {
        status = OperatingRegionBoundaryRefinement.Status.INELIGIBLE_EVALUATION;
        break;
      }
    }
    if (status == null) {
      double width = Math
          .abs(infeasible.getCandidateValues()[actionIndex] - feasible.getCandidateValues()[actionIndex]);
      status = width <= absoluteTolerance ? OperatingRegionBoundaryRefinement.Status.CONVERGED
          : OperatingRegionBoundaryRefinement.Status.MAXIMUM_EVALUATIONS_REACHED;
    }
    return new OperatingRegionBoundaryRefinement(transition, action.getLowerBound(), action.getUpperBound(),
        boundaryRefinementRelativeTolerance, absoluteTolerance, boundaryRefinementToleranceProvenance,
        maximumBoundaryRefinementEvaluations, feasible.getCandidateValues()[actionIndex],
        infeasible.getCandidateValues()[actionIndex], feasible.getLeadingConstraint(),
        infeasible.getLeadingConstraint(), status, steps);
  }

  /** Returns true only for a finite, converged, feasible midpoint with complete recovery. */
  private static boolean eligibleRefinementFeasible(Point point) {
    return finiteVector(point.getCandidateValues()) && point.getEvaluation().isCandidateSimulationConverged()
        && point.getEvaluation().isCandidateEvaluatorFeasible() && point.getEvaluation().isFeasible()
        && safeRecovery(point);
  }

  /** Returns true only for an explicit hard/validity rejection suitable for a physical feasibility bracket. */
  private static boolean refinableRejection(Point point) {
    if (!finiteVector(point.getCandidateValues()) || !point.getEvaluation().isCandidateSimulationConverged()
        || point.getEvaluation().isFeasible() || !safeRecovery(point)) {
      return false;
    }
    return refinableOutcome(point.getEvaluation().getOutcome());
  }

  /** Returns true only for a hard physical/validity rejection accepted by the shared refinement policy. */
  private static boolean refinableOutcome(ProcessModelOperatingActionSetEvaluator.Outcome pointOutcome) {
    return pointOutcome == ProcessModelOperatingActionSetEvaluator.Outcome.HYDRAULIC_CONSTRAINT_VIOLATED
        || pointOutcome == ProcessModelOperatingActionSetEvaluator.Outcome.OTHER_MODEL_CONSTRAINT_VIOLATED
        || pointOutcome == ProcessModelOperatingActionSetEvaluator.Outcome.EVIDENCE_OUTSIDE_VALIDITY_RANGE;
  }

  /** Returns true when every vector value is finite. */
  private static boolean finiteVector(double[] values) {
    for (double value : values) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Evaluates one complete candidate at one grid coordinate.
   *
   * @param anchor full anchor vector
   * @param actionIndexes action index for each axis
   * @param axes axis values
   * @param gridIndexes coordinate index for each axis
   * @param sequence sequence index
   * @return immutable point evidence
   */
  private Point evaluatePoint(double[] anchor, int[] actionIndexes, double[][] axes, int[] gridIndexes, int sequence) {
    double[] candidate = Arrays.copyOf(anchor, anchor.length);
    double[] coordinates = new double[actionIndexes.length];
    for (int axis = 0; axis < actionIndexes.length; axis++) {
      coordinates[axis] = axes[axis][gridIndexes[axis]];
      candidate[actionIndexes[axis]] = coordinates[axis];
    }
    CandidateSetEvaluationResult result = evaluator.evaluate(candidate);
    InstalledEquipmentCapacityEvidence installed = leadingInstalled(result);
    return new Point(sequence, gridIndexes, coordinates, candidate, result, leading(result, installed), installed);
  }

  /**
   * Checks whether an evaluated point restored and reconverged the baseline.
   *
   * @param point evaluated point
   * @return true only for safe recovery
   */
  private static boolean safeRecovery(Point point) {
    return point.getEvaluation().isBaselineRestored() && point.getEvaluation().isBaselineSimulationConverged();
  }

  /**
   * Selects the leading finite installed-equipment constraint.
   *
   * @param result completed candidate result
   * @return leading evidence or null
   */
  private static InstalledEquipmentCapacityEvidence leadingInstalled(CandidateSetEvaluationResult result) {
    for (InstalledEquipmentCapacityEvidence evidence : result.getInstalledEquipmentCapacityEvidence()) {
      if (evidence.isEnabled() && evidence.hasFiniteEvidence()) {
        return evidence;
      }
    }
    return null;
  }

  /** Selects the leading finite enabled plant/shared/coupled constraint by normalized utilization. */
  private static PlantConstraintEvidence leadingPlant(CandidateSetEvaluationResult result) {
    PlantConstraintEvidence leading = null;
    for (PlantConstraintEvidence evidence : result.getPlantConstraintEvidence()) {
      if (!evidence.getDefinition().isEnabled() || !evidence.hasAvailableEvidence()
          || !Double.isFinite(evidence.getNormalizedUtilization())) {
        continue;
      }
      if (leading == null || evidence.getNormalizedUtilization() > leading.getNormalizedUtilization()) {
        leading = evidence;
      }
    }
    return leading;
  }

  /** Selects the unit-safe leading constraint or exact fail-closed rejection source. */
  private static LeadingConstraintEvidence leading(CandidateSetEvaluationResult result,
      InstalledEquipmentCapacityEvidence installed) {
    switch (result.getOutcome()) {
    case REQUIRED_CONSTRAINT_MISSING:
    case CONSTRAINT_VALUE_UNAVAILABLE:
    case HYDRAULIC_CONSTRAINT_VIOLATED:
    case EVIDENCE_OUTSIDE_VALIDITY_RANGE:
      for (HydraulicConstraintSnapshot snapshot : result.getHydraulicConstraints()) {
        if (matchesHydraulicOutcome(result.getOutcome(), snapshot)) {
          return LeadingConstraintEvidence.fromHydraulic(snapshot);
        }
      }
      break;
    case OTHER_MODEL_CONSTRAINT_VIOLATED:
      for (PlantConstraintEvidence evidence : result.getPlantConstraintEvidence()) {
        if (evidence.getDefinition().isEnabled() && (!evidence.hasAvailableEvidence() || !evidence.isFeasible())) {
          return LeadingConstraintEvidence.fromPlant(evidence);
        }
      }
      for (CandidateConstraintEvidence evidence : result.getConstraintEvidence()) {
        if (evidence.isHard() && !evidence.isSatisfied()) {
          return LeadingConstraintEvidence.fromRegisteredConstraint(evidence);
        }
      }
      break;
    case FEASIBLE:
    default:
      break;
    }
    PlantConstraintEvidence plant = leadingPlant(result);
    if (installed == null) {
      return plant == null ? null : LeadingConstraintEvidence.fromPlant(plant);
    }
    if (plant != null && plant.getNormalizedUtilization() > installed.getNormalizedUtilization()) {
      return LeadingConstraintEvidence.fromPlant(plant);
    }
    return LeadingConstraintEvidence.fromInstalled(installed);
  }

  /** Matches one required-hydraulic snapshot to the evaluator's fail-closed outcome. */
  private static boolean matchesHydraulicOutcome(ProcessModelOperatingActionSetEvaluator.Outcome outcome,
      HydraulicConstraintSnapshot snapshot) {
    switch (outcome) {
    case REQUIRED_CONSTRAINT_MISSING:
      return !snapshot.isPresent();
    case CONSTRAINT_VALUE_UNAVAILABLE:
      return snapshot.isPresent() && !snapshot.hasFiniteValue();
    case HYDRAULIC_CONSTRAINT_VIOLATED:
      return snapshot.hasFiniteValue() && !snapshot.isFeasible();
    case EVIDENCE_OUTSIDE_VALIDITY_RANGE:
      return snapshot
          .getEvidenceApplicability() == ProcessModelSimulationEvaluator.BottleneckStatus.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE;
    default:
      return false;
    }
  }

  /**
   * Records a changed leading constraint across one adjacent grid edge.
   *
   * @param transitions mutable transition list
   * @param axisId traversed action identifier
   * @param from source point
   * @param to destination point
   * @param axisPosition coordinate position
   */
  private static void transition(List<Transition> transitions, String axisId, Point from, Point to, int axisPosition) {
    LeadingConstraintEvidence first = from.getLeadingConstraint();
    LeadingConstraintEvidence second = to.getLeadingConstraint();
    if (first == null || second == null
        || first.getQualifiedConstraintName().equals(second.getQualifiedConstraintName())) {
      return;
    }
    transitions
        .add(new Transition(axisId, from.getSequenceIndex(), to.getSequenceIndex(), from.getAxisValues()[axisPosition],
            to.getAxisValues()[axisPosition], first.getQualifiedConstraintName(), second.getQualifiedConstraintName()));
  }

  /** Records a sampled operating-region change across one adjacent grid edge. */
  private static void operatingRegionTransition(List<OperatingRegionTransition> transitions,
      ProcessModelOperatingAction action, Point from, Point to, int axisPosition) {
    LeadingConstraintEvidence first = from.getLeadingConstraint();
    LeadingConstraintEvidence second = to.getLeadingConstraint();
    String firstConstraint = first == null ? null : first.getQualifiedConstraintName();
    String secondConstraint = second == null ? null : second.getQualifiedConstraintName();
    ProcessModelOperatingActionSetEvaluator.Outcome firstOutcome = from.getEvaluation().getOutcome();
    ProcessModelOperatingActionSetEvaluator.Outcome secondOutcome = to.getEvaluation().getOutcome();
    OperatingRegionTransition.Kind kind;
    if (from.isFeasible() && !to.isFeasible()) {
      kind = OperatingRegionTransition.Kind.BECAME_INFEASIBLE;
    } else if (!from.isFeasible() && to.isFeasible()) {
      kind = OperatingRegionTransition.Kind.BECAME_FEASIBLE;
    } else if (firstOutcome != secondOutcome) {
      kind = OperatingRegionTransition.Kind.EVALUATION_OUTCOME_CHANGED;
    } else if (!sameNullableText(firstConstraint, secondConstraint) || !sameConstraintSource(first, second)) {
      kind = OperatingRegionTransition.Kind.LEADING_CONSTRAINT_CHANGED;
    } else {
      return;
    }
    transitions
        .add(new OperatingRegionTransition(action.getId(), action.getValueSemantics(), kind, from.getSequenceIndex(),
            to.getSequenceIndex(), from.getAxisValues()[axisPosition], to.getAxisValues()[axisPosition],
            from.isFeasible(), to.isFeasible(), firstOutcome, secondOutcome, first == null ? null : first.getSource(),
            second == null ? null : second.getSource(), firstConstraint, secondConstraint));
  }

  /** Returns true when nullable constraint identities are equal. */
  private static boolean sameNullableText(String first, String second) {
    return first == null ? second == null : first.equals(second);
  }

  /** Returns true when nullable leading-constraint sources are equal. */
  private static boolean sameConstraintSource(LeadingConstraintEvidence first, LeadingConstraintEvidence second) {
    return first == null ? second == null : second != null && first.getSource() == second.getSource();
  }

  /**
   * Validates and copies the full action anchor.
   *
   * @param actions evaluator actions
   * @param anchorValues requested anchor
   * @return validated defensive copy
   */
  private static double[] validateAnchor(List<ProcessModelOperatingAction> actions, double[] anchorValues) {
    if (anchorValues == null || anchorValues.length != actions.size()) {
      throw new IllegalArgumentException("Anchor length must equal action count " + actions.size());
    }
    double[] anchor = Arrays.copyOf(anchorValues, anchorValues.length);
    for (int i = 0; i < actions.size(); i++) {
      if (!actions.get(i).accepts(anchor[i])) {
        throw new IllegalArgumentException("Anchor outside action domain: " + actions.get(i).getId());
      }
    }
    return anchor;
  }

  /**
   * Validates axes and resolves action indexes.
   *
   * @param actions evaluator actions
   * @param axisIds axis action identifiers
   * @param axes axis values
   * @return action indexes by axis
   */
  private static int[] validateAxes(List<ProcessModelOperatingAction> actions, String[] axisIds, double[][] axes) {
    if (axisIds == null || axes == null || axisIds.length < 1 || axisIds.length > 2 || axisIds.length != axes.length) {
      throw new IllegalArgumentException("Operating-envelope slices require one or two matching axes");
    }
    int[] indexes = new int[axisIds.length];
    for (int axis = 0; axis < axisIds.length; axis++) {
      String id = requireText(axisIds[axis], "Axis action identifier");
      if (axis > 0 && id.equals(axisIds[0])) {
        throw new IllegalArgumentException("Operating-envelope axes must use distinct actions");
      }
      indexes[axis] = findAction(actions, id);
      if (indexes[axis] < 0) {
        throw new IllegalArgumentException("Unknown action identifier: " + id);
      }
      if (axes[axis] == null || axes[axis].length == 0) {
        throw new IllegalArgumentException("Axis must contain at least one value: " + id);
      }
      for (double value : axes[axis]) {
        if (!actions.get(indexes[axis]).accepts(value)) {
          throw new IllegalArgumentException("Axis value outside action domain for " + id + ": " + value);
        }
      }
    }
    return indexes;
  }

  /**
   * Finds one action by stable identifier.
   *
   * @param actions evaluator actions
   * @param id requested identifier
   * @return index or -1
   */
  private static int findAction(List<ProcessModelOperatingAction> actions, String id) {
    for (int i = 0; i < actions.size(); i++) {
      if (id.equals(actions.get(i).getId())) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Computes requested grid size.
   *
   * @param axes validated axis arrays
   * @return point count
   */
  private static int pointCount(double[][] axes) {
    long count = 1L;
    for (double[] axis : axes) {
      count *= axis.length;
      if (count > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Operating-envelope grid is too large");
      }
    }
    return (int) count;
  }

  /** Converts finite doubles to an immutable JSON-friendly list. */
  private static List<Object> toFiniteDoubleList(double[] values) {
    List<Object> result = new ArrayList<Object>();
    for (double value : values) {
      result.add(finiteOrNull(value));
    }
    return Collections.unmodifiableList(result);
  }

  /** Converts a two-dimensional double array to nested immutable JSON-friendly lists. */
  private static List<List<Object>> toFiniteDoubleLists(double[][] values) {
    List<List<Object>> result = new ArrayList<List<Object>>();
    for (double[] row : values) {
      result.add(toFiniteDoubleList(row));
    }
    return Collections.unmodifiableList(result);
  }

  /** Converts integers to an immutable JSON-friendly list. */
  private static List<Integer> toIntegerList(int[] values) {
    List<Integer> result = new ArrayList<Integer>();
    for (int value : values) {
      result.add(Integer.valueOf(value));
    }
    return Collections.unmodifiableList(result);
  }

  /** Copies text values to an immutable JSON-friendly list. */
  private static List<String> toStringList(String[] values) {
    return Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(values)));
  }

  /** Converts action semantics to an immutable JSON-friendly name list. */
  private static List<String> toStringList(ProcessModelOperatingAction.ValueSemantics[] values) {
    List<String> result = new ArrayList<String>();
    for (ProcessModelOperatingAction.ValueSemantics value : values) {
      result.add(value.name());
    }
    return Collections.unmodifiableList(result);
  }

  /** Returns a JSON-safe finite number or null for unavailable numerical evidence. */
  private static Double finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }

  /**
   * Requires non-blank text.
   *
   * @param value candidate value
   * @param label field label
   * @return trimmed text
   */
  private static String requireText(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value.trim();
  }

  /** Immutable unit-safe identity and headroom for one leading or rejecting constraint. */
  public static final class LeadingConstraintEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Evidence layer that supplied the selected constraint. */
    public enum Source {
      /** Utilization-ranked installed equipment evidence at a feasible point. */
      INSTALLED_EQUIPMENT,
      /** Exact required hydraulic binding that rejected the point. */
      REQUIRED_HYDRAULIC,
      /** Typed plant-wide, shared-resource, or coupled-equipment evidence. */
      PLANT_CONSTRAINT,
      /** Ordinary hard evaluator constraint selected in declaration order. */
      REGISTERED_MODEL_CONSTRAINT
    }

    private final Source source;
    private final String qualifiedConstraintName;
    private final double normalizedUtilization;
    private final double normalizedMargin;
    private final double currentValue;
    private final double applicableLimit;
    private final double physicalMargin;
    private final String physicalUnit;
    private final boolean feasible;
    private final String diagnostic;
    private final String applicableLimitRole;
    private final String applicableLimitProvenance;

    /**
     * Creates immutable leading-constraint evidence.
     *
     * @param source evidence layer
     * @param qualifiedConstraintName stable constraint identity
     * @param normalizedUtilization dimensionless utilization
     * @param normalizedMargin dimensionless remaining margin
     * @param currentValue sampled physical value
     * @param applicableLimit applicable physical limit
     * @param physicalMargin signed physical headroom
     * @param physicalUnit engineering unit
     * @param feasible whether the selected evidence is feasible
     * @param diagnostic concise diagnostic
     * @param applicableLimitRole role or basis of the applicable limit
     * @param applicableLimitProvenance applicable-limit provenance
     */
    private LeadingConstraintEvidence(Source source, String qualifiedConstraintName, double normalizedUtilization,
        double normalizedMargin, double currentValue, double applicableLimit, double physicalMargin,
        String physicalUnit, boolean feasible, String diagnostic, String applicableLimitRole,
        String applicableLimitProvenance) {
      this.source = source;
      this.qualifiedConstraintName = qualifiedConstraintName;
      this.normalizedUtilization = normalizedUtilization;
      this.normalizedMargin = normalizedMargin;
      this.currentValue = currentValue;
      this.applicableLimit = applicableLimit;
      this.physicalMargin = physicalMargin;
      this.physicalUnit = physicalUnit == null ? "" : physicalUnit;
      this.feasible = feasible;
      this.diagnostic = diagnostic == null ? "" : diagnostic;
      this.applicableLimitRole = applicableLimitRole == null ? "" : applicableLimitRole;
      this.applicableLimitProvenance = applicableLimitProvenance == null ? "" : applicableLimitProvenance;
    }

    /** Creates evidence from one installed equipment constraint. */
    private static LeadingConstraintEvidence fromInstalled(InstalledEquipmentCapacityEvidence evidence) {
      return new LeadingConstraintEvidence(Source.INSTALLED_EQUIPMENT, evidence.getQualifiedConstraintName(),
          evidence.getNormalizedUtilization(), evidence.getNormalizedMargin(), evidence.getCurrentValue(),
          evidence.getApplicableLimit(), evidence.getPhysicalMargin(), evidence.getPhysicalUnit(),
          evidence.isFeasible(), evidence.getEvidenceStatus().name(), evidence.getApplicableLimitRole().name(),
          evidence.getApplicableLimitSourceReference());
    }

    /** Creates evidence from one required hydraulic snapshot. */
    private static LeadingConstraintEvidence fromHydraulic(HydraulicConstraintSnapshot evidence) {
      double physicalMargin = evidence.isMinimumConstraint() ? evidence.getCurrentValue() - evidence.getDesignValue()
          : evidence.getDesignValue() - evidence.getCurrentValue();
      return new LeadingConstraintEvidence(Source.REQUIRED_HYDRAULIC,
          evidence.getBinding().getQualifiedConstraintName(), evidence.getUtilization(), evidence.getMargin(),
          evidence.getCurrentValue(), evidence.getDesignValue(), physicalMargin, evidence.getUnit(),
          evidence.isFeasible(), evidence.isPresent() ? evidence.getEvidenceApplicability().name() : "MISSING",
          "REQUIRED_HYDRAULIC", evidence.getDataSource());
    }

    /** Creates evidence from one typed plant constraint row. */
    private static LeadingConstraintEvidence fromPlant(PlantConstraintEvidence evidence) {
      PlantConstraintSample sample = evidence.getSample();
      return new LeadingConstraintEvidence(Source.PLANT_CONSTRAINT, evidence.getQualifiedConstraintId(),
          evidence.getNormalizedUtilization(), -evidence.getNormalizedResidual(),
          sample == null ? Double.NaN : sample.getSampledValue(),
          sample == null ? Double.NaN : sample.getApplicableLimit(),
          sample == null ? Double.NaN : sample.getPhysicalMargin(), evidence.getDefinition().getUnit(),
          evidence.isFeasible(), evidence.getDiagnostic(), evidence.getDefinition().getBasis(),
          sample == null ? evidence.getDefinition().getProvenance() : sample.getProvenance());
    }

    /** Creates evidence from one ordinary registered hard constraint. */
    private static LeadingConstraintEvidence fromRegisteredConstraint(CandidateConstraintEvidence evidence) {
      double limit;
      switch (evidence.getType()) {
      case LOWER_BOUND:
      case EQUALITY:
        limit = evidence.getLowerBound();
        break;
      case UPPER_BOUND:
        limit = evidence.getUpperBound();
        break;
      case RANGE:
      default:
        limit = Math.abs(evidence.getValue() - evidence.getLowerBound()) <= Math
            .abs(evidence.getUpperBound() - evidence.getValue()) ? evidence.getLowerBound() : evidence.getUpperBound();
        break;
      }
      String physicalUnit = evidence.getPhysicalUnit() == null ? evidence.getUnit() : evidence.getPhysicalUnit();
      return new LeadingConstraintEvidence(Source.REGISTERED_MODEL_CONSTRAINT, evidence.getName(), Double.NaN,
          Double.NaN, evidence.getValue(), limit, evidence.getMargin(), physicalUnit, evidence.isSatisfied(),
          "DECLARATION_ORDER_HARD_CONSTRAINT", "REGISTERED_MODEL_CONSTRAINT", "");
    }

    /** @return evidence layer that supplied the selected constraint */
    public Source getSource() {
      return source;
    }

    /** @return stable or area-qualified constraint identity */
    public String getQualifiedConstraintName() {
      return qualifiedConstraintName;
    }

    /** @return normalized utilization, or NaN when the source is not normalized */
    public double getNormalizedUtilization() {
      return normalizedUtilization;
    }

    /** @return normalized remaining margin, or NaN when unavailable */
    public double getNormalizedMargin() {
      return normalizedMargin;
    }

    /** @return current physical value, or NaN when unavailable */
    public double getCurrentValue() {
      return currentValue;
    }

    /** @return applicable physical limit, or NaN when unavailable */
    public double getApplicableLimit() {
      return applicableLimit;
    }

    /** @return signed physical headroom in {@link #getPhysicalUnit()}, or NaN */
    public double getPhysicalMargin() {
      return physicalMargin;
    }

    /** @return physical engineering unit, possibly empty */
    public String getPhysicalUnit() {
      return physicalUnit;
    }

    /** @return true when the selected evidence is feasible */
    public boolean isFeasible() {
      return feasible;
    }

    /** @return concise evidence diagnostic */
    public String getDiagnostic() {
      return diagnostic;
    }

    /** @return role or basis of the selected physical limit */
    public String getApplicableLimitRole() {
      return applicableLimitRole;
    }

    /** @return provenance or reference supporting the selected physical limit */
    public String getApplicableLimitProvenance() {
      return applicableLimitProvenance;
    }

    /**
     * Converts this constraint row to a compact JSON-friendly map.
     *
     * @return immutable evidence map with null for unavailable numerical fields
     */
    public Map<String, Object> toEvidenceMap() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("source", source.name());
      evidence.put("qualified_constraint_name", qualifiedConstraintName);
      evidence.put("normalized_utilization", finiteOrNull(normalizedUtilization));
      evidence.put("normalized_margin", finiteOrNull(normalizedMargin));
      evidence.put("current_value", finiteOrNull(currentValue));
      evidence.put("applicable_limit", finiteOrNull(applicableLimit));
      evidence.put("physical_margin", finiteOrNull(physicalMargin));
      evidence.put("physical_unit", physicalUnit);
      evidence.put("feasible", Boolean.valueOf(feasible));
      evidence.put("diagnostic", diagnostic);
      evidence.put("applicable_limit_role", applicableLimitRole);
      evidence.put("applicable_limit_provenance", applicableLimitProvenance);
      return Collections.unmodifiableMap(evidence);
    }
  }

  /**
   * Immutable evidence for one sampled operating point.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class Point implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int sequenceIndex;
    private final int[] gridIndexes;
    private final double[] axisValues;
    private final double[] candidateValues;
    private final CandidateSetEvaluationResult evaluation;
    private final LeadingConstraintEvidence leadingConstraint;
    private final InstalledEquipmentCapacityEvidence leadingInstalledConstraint;

    /**
     * Creates one immutable point.
     *
     * @param sequenceIndex deterministic sequence
     * @param gridIndexes grid coordinates
     * @param axisValues physical axis values
     * @param candidateValues complete candidate
     * @param evaluation complete evaluator evidence
     * @param leadingConstraint unit-safe leading or rejecting constraint
     * @param leadingInstalledConstraint leading installed evidence or null
     */
    private Point(int sequenceIndex, int[] gridIndexes, double[] axisValues, double[] candidateValues,
        CandidateSetEvaluationResult evaluation, LeadingConstraintEvidence leadingConstraint,
        InstalledEquipmentCapacityEvidence leadingInstalledConstraint) {
      this.sequenceIndex = sequenceIndex;
      this.gridIndexes = Arrays.copyOf(gridIndexes, gridIndexes.length);
      this.axisValues = Arrays.copyOf(axisValues, axisValues.length);
      this.candidateValues = Arrays.copyOf(candidateValues, candidateValues.length);
      this.evaluation = evaluation;
      this.leadingConstraint = leadingConstraint;
      this.leadingInstalledConstraint = leadingInstalledConstraint;
    }

    /** @return deterministic sequence index */
    public int getSequenceIndex() {
      return sequenceIndex;
    }

    /** @return defensive grid coordinates */
    public int[] getGridIndexes() {
      return Arrays.copyOf(gridIndexes, gridIndexes.length);
    }

    /** @return defensive physical axis values */
    public double[] getAxisValues() {
      return Arrays.copyOf(axisValues, axisValues.length);
    }

    /** @return defensive complete candidate vector */
    public double[] getCandidateValues() {
      return Arrays.copyOf(candidateValues, candidateValues.length);
    }

    /** @return complete immutable candidate evaluation */
    public CandidateSetEvaluationResult getEvaluation() {
      return evaluation;
    }

    /** @return unit-safe leading or exact rejecting constraint, or null */
    public LeadingConstraintEvidence getLeadingConstraint() {
      return leadingConstraint;
    }

    /** @return leading finite installed-equipment evidence or null */
    public InstalledEquipmentCapacityEvidence getLeadingInstalledConstraint() {
      return leadingInstalledConstraint;
    }

    /** @return true only for a fully feasible evaluator result */
    public boolean isFeasible() {
      return evaluation.isFeasible();
    }

    /**
     * Converts this sampled point to a compact JSON-friendly map.
     *
     * @return immutable evidence map
     */
    public Map<String, Object> toEvidenceMap() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("sequence_index", Integer.valueOf(sequenceIndex));
      evidence.put("grid_indexes", toIntegerList(gridIndexes));
      evidence.put("axis_values", toFiniteDoubleList(axisValues));
      evidence.put("candidate_values", toFiniteDoubleList(candidateValues));
      evidence.put("outcome", evaluation.getOutcome().name());
      evidence.put("feasible", Boolean.valueOf(evaluation.isFeasible()));
      evidence.put("candidate_simulation_converged", Boolean.valueOf(evaluation.isCandidateSimulationConverged()));
      evidence.put("candidate_evaluator_feasible", Boolean.valueOf(evaluation.isCandidateEvaluatorFeasible()));
      evidence.put("baseline_restored", Boolean.valueOf(evaluation.isBaselineRestored()));
      evidence.put("baseline_simulation_converged", Boolean.valueOf(evaluation.isBaselineSimulationConverged()));
      evidence.put("raw_objectives", toFiniteDoubleList(evaluation.getRawObjectives()));
      evidence.put("leading_constraint", leadingConstraint == null ? null : leadingConstraint.toEvidenceMap());
      evidence.put("diagnostics", Collections.unmodifiableList(new ArrayList<String>(evaluation.getDiagnostics())));
      return Collections.unmodifiableMap(evidence);
    }
  }

  /**
   * Immutable sampled leading-bottleneck identity change across one adjacent grid edge.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class Transition implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String axisActionId;
    private final int fromSequenceIndex;
    private final int toSequenceIndex;
    private final double fromAxisValue;
    private final double toAxisValue;
    private final String fromConstraint;
    private final String toConstraint;

    /**
     * Creates one sampled transition.
     *
     * @param axisActionId traversed action
     * @param fromSequenceIndex source point
     * @param toSequenceIndex destination point
     * @param fromAxisValue source action value
     * @param toAxisValue destination action value
     * @param fromConstraint source qualified constraint identity
     * @param toConstraint destination qualified constraint identity
     */
    private Transition(String axisActionId, int fromSequenceIndex, int toSequenceIndex, double fromAxisValue,
        double toAxisValue, String fromConstraint, String toConstraint) {
      this.axisActionId = axisActionId;
      this.fromSequenceIndex = fromSequenceIndex;
      this.toSequenceIndex = toSequenceIndex;
      this.fromAxisValue = fromAxisValue;
      this.toAxisValue = toAxisValue;
      this.fromConstraint = fromConstraint;
      this.toConstraint = toConstraint;
    }

    /** @return traversed action identifier */
    public String getAxisActionId() {
      return axisActionId;
    }

    /** @return source point sequence index */
    public int getFromSequenceIndex() {
      return fromSequenceIndex;
    }

    /** @return destination point sequence index */
    public int getToSequenceIndex() {
      return toSequenceIndex;
    }

    /** @return source action value */
    public double getFromAxisValue() {
      return fromAxisValue;
    }

    /** @return destination action value */
    public double getToAxisValue() {
      return toAxisValue;
    }

    /** @return source qualified constraint identity */
    public String getFromConstraint() {
      return fromConstraint;
    }

    /** @return destination qualified constraint identity */
    public String getToConstraint() {
      return toConstraint;
    }

    /**
     * Converts this sampled bottleneck change to a compact JSON-friendly map.
     *
     * @return immutable evidence map
     */
    public Map<String, Object> toEvidenceMap() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("axis_action_id", axisActionId);
      evidence.put("from_sequence_index", Integer.valueOf(fromSequenceIndex));
      evidence.put("to_sequence_index", Integer.valueOf(toSequenceIndex));
      evidence.put("from_axis_value", finiteOrNull(fromAxisValue));
      evidence.put("to_axis_value", finiteOrNull(toAxisValue));
      evidence.put("from_constraint", fromConstraint);
      evidence.put("to_constraint", toConstraint);
      return Collections.unmodifiableMap(evidence);
    }
  }

  /**
   * Immutable sampled operating-region change across one adjacent grid edge.
   *
   * <p>
   * This diagnostic preserves the exact endpoint outcomes and leading-constraint identities. It does not locate a
   * continuous boundary. In particular, an edge traversing a discrete action records two evaluated line-ups and never
   * authorizes interpolation between them.
   * </p>
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class OperatingRegionTransition implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Sampled change classification. */
    public enum Kind {
      /** The destination point is infeasible while the source point is feasible. */
      BECAME_INFEASIBLE,
      /** The destination point is feasible while the source point is infeasible. */
      BECAME_FEASIBLE,
      /** Both endpoints share feasibility, but the exact evaluator outcome changed. */
      EVALUATION_OUTCOME_CHANGED,
      /** Feasibility and outcome are unchanged, but the selected evidence identity or layer changed. */
      LEADING_CONSTRAINT_CHANGED
    }

    private final String axisActionId;
    private final ProcessModelOperatingAction.ValueSemantics actionValueSemantics;
    private final Kind kind;
    private final int fromSequenceIndex;
    private final int toSequenceIndex;
    private final double fromAxisValue;
    private final double toAxisValue;
    private final boolean fromFeasible;
    private final boolean toFeasible;
    private final ProcessModelOperatingActionSetEvaluator.Outcome fromOutcome;
    private final ProcessModelOperatingActionSetEvaluator.Outcome toOutcome;
    private final LeadingConstraintEvidence.Source fromConstraintSource;
    private final LeadingConstraintEvidence.Source toConstraintSource;
    private final String fromConstraint;
    private final String toConstraint;

    /** Creates one immutable adjacent-edge diagnostic. */
    private OperatingRegionTransition(String axisActionId,
        ProcessModelOperatingAction.ValueSemantics actionValueSemantics, Kind kind, int fromSequenceIndex,
        int toSequenceIndex, double fromAxisValue, double toAxisValue, boolean fromFeasible, boolean toFeasible,
        ProcessModelOperatingActionSetEvaluator.Outcome fromOutcome,
        ProcessModelOperatingActionSetEvaluator.Outcome toOutcome,
        LeadingConstraintEvidence.Source fromConstraintSource, LeadingConstraintEvidence.Source toConstraintSource,
        String fromConstraint, String toConstraint) {
      this.axisActionId = axisActionId;
      this.actionValueSemantics = actionValueSemantics;
      this.kind = kind;
      this.fromSequenceIndex = fromSequenceIndex;
      this.toSequenceIndex = toSequenceIndex;
      this.fromAxisValue = fromAxisValue;
      this.toAxisValue = toAxisValue;
      this.fromFeasible = fromFeasible;
      this.toFeasible = toFeasible;
      this.fromOutcome = fromOutcome;
      this.toOutcome = toOutcome;
      this.fromConstraintSource = fromConstraintSource;
      this.toConstraintSource = toConstraintSource;
      this.fromConstraint = fromConstraint;
      this.toConstraint = toConstraint;
    }

    /** @return traversed action identifier */
    public String getAxisActionId() {
      return axisActionId;
    }

    /** @return continuous or discrete action semantics */
    public ProcessModelOperatingAction.ValueSemantics getActionValueSemantics() {
      return actionValueSemantics;
    }

    /** @return sampled change classification */
    public Kind getKind() {
      return kind;
    }

    /** @return source point sequence index */
    public int getFromSequenceIndex() {
      return fromSequenceIndex;
    }

    /** @return destination point sequence index */
    public int getToSequenceIndex() {
      return toSequenceIndex;
    }

    /** @return source action value */
    public double getFromAxisValue() {
      return fromAxisValue;
    }

    /** @return destination action value */
    public double getToAxisValue() {
      return toAxisValue;
    }

    /** @return absolute sampled interval width in the action unit */
    public double getSampledIntervalWidth() {
      return Math.abs(toAxisValue - fromAxisValue);
    }

    /** @return source feasibility */
    public boolean isFromFeasible() {
      return fromFeasible;
    }

    /** @return destination feasibility */
    public boolean isToFeasible() {
      return toFeasible;
    }

    /** @return exact source evaluator outcome */
    public ProcessModelOperatingActionSetEvaluator.Outcome getFromOutcome() {
      return fromOutcome;
    }

    /** @return exact destination evaluator outcome */
    public ProcessModelOperatingActionSetEvaluator.Outcome getToOutcome() {
      return toOutcome;
    }

    /** @return source evidence layer, or null when no leading row exists */
    public LeadingConstraintEvidence.Source getFromConstraintSource() {
      return fromConstraintSource;
    }

    /** @return destination evidence layer, or null when no leading row exists */
    public LeadingConstraintEvidence.Source getToConstraintSource() {
      return toConstraintSource;
    }

    /** @return source constraint identity, or null */
    public String getFromConstraint() {
      return fromConstraint;
    }

    /** @return destination constraint identity, or null */
    public String getToConstraint() {
      return toConstraint;
    }

    /** @return true for a discrete line-up edge that must not be interpolated */
    public boolean isDiscreteAction() {
      return actionValueSemantics == ProcessModelOperatingAction.ValueSemantics.DISCRETE;
    }

    /** Converts this sampled change to a compact JSON-friendly map. */
    public Map<String, Object> toEvidenceMap() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("axis_action_id", axisActionId);
      evidence.put("action_value_semantics", actionValueSemantics.name());
      evidence.put("kind", kind.name());
      evidence.put("from_sequence_index", Integer.valueOf(fromSequenceIndex));
      evidence.put("to_sequence_index", Integer.valueOf(toSequenceIndex));
      evidence.put("from_axis_value", finiteOrNull(fromAxisValue));
      evidence.put("to_axis_value", finiteOrNull(toAxisValue));
      evidence.put("sampled_interval_width", finiteOrNull(getSampledIntervalWidth()));
      evidence.put("from_feasible", Boolean.valueOf(fromFeasible));
      evidence.put("to_feasible", Boolean.valueOf(toFeasible));
      evidence.put("from_outcome", fromOutcome.name());
      evidence.put("to_outcome", toOutcome.name());
      evidence.put("from_constraint_source", fromConstraintSource == null ? null : fromConstraintSource.name());
      evidence.put("to_constraint_source", toConstraintSource == null ? null : toConstraintSource.name());
      evidence.put("from_constraint", fromConstraint);
      evidence.put("to_constraint", toConstraint);
      evidence.put("interpolated", Boolean.FALSE);
      evidence.put("sampled_event_only", Boolean.TRUE);
      return Collections.unmodifiableMap(evidence);
    }
  }

  /**
   * Immutable fail-closed bisection receipt for one continuous sampled feasibility transition.
   *
   * <p>
   * The result is a bracket between independently evaluated feasible and infeasible action values. It is diagnostic
   * evidence, not an interpolated set point or accepted operating point.
   * </p>
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class OperatingRegionBoundaryRefinement implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Terminal refinement classification. */
    public enum Status {
      /** The independently evaluated bracket met the configured width tolerance. */
      CONVERGED,
      /** The configured midpoint budget ended before tolerance was met. */
      MAXIMUM_EVALUATIONS_REACHED,
      /** A recovered midpoint did not provide eligible feasible or hard-rejection evidence. */
      INELIGIBLE_EVALUATION,
      /** Floating-point resolution prevented another distinct midpoint. */
      NUMERICAL_STAGNATION,
      /** A midpoint failed complete baseline restoration or restored-baseline convergence. */
      MODEL_RECOVERY_FAILED
    }

    private final String axisActionId;
    private final int fromSequenceIndex;
    private final int toSequenceIndex;
    private final double sampledFromAxisValue;
    private final double sampledToAxisValue;
    private final double actionLowerBound;
    private final double actionUpperBound;
    private final double relativeTolerance;
    private final double absoluteTolerance;
    private final String toleranceProvenance;
    private final int maximumEvaluations;
    private final double feasibleAxisValue;
    private final double infeasibleAxisValue;
    private final LeadingConstraintEvidence feasibleBoundaryConstraint;
    private final LeadingConstraintEvidence infeasibleBoundaryConstraint;
    private final Status status;
    private final List<Step> steps;

    /**
     * Creates one immutable refinement receipt.
     *
     * @param transition sampled transition being refined
     * @param actionLowerBound configured action lower bound
     * @param actionUpperBound configured action upper bound
     * @param relativeTolerance terminal tolerance relative to action range
     * @param absoluteTolerance terminal tolerance in action units
     * @param toleranceProvenance engineering source of the tolerance
     * @param maximumEvaluations configured midpoint budget
     * @param feasibleAxisValue final independently evaluated feasible value
     * @param infeasibleAxisValue final independently evaluated infeasible value
     * @param feasibleBoundaryConstraint leading constraint at the final feasible endpoint
     * @param infeasibleBoundaryConstraint rejecting constraint at the final infeasible endpoint
     * @param status terminal refinement status
     * @param steps complete midpoint evidence
     */
    private OperatingRegionBoundaryRefinement(OperatingRegionTransition transition, double actionLowerBound,
        double actionUpperBound, double relativeTolerance, double absoluteTolerance, String toleranceProvenance,
        int maximumEvaluations, double feasibleAxisValue, double infeasibleAxisValue,
        LeadingConstraintEvidence feasibleBoundaryConstraint, LeadingConstraintEvidence infeasibleBoundaryConstraint,
        Status status, List<Step> steps) {
      this.axisActionId = transition.getAxisActionId();
      this.fromSequenceIndex = transition.getFromSequenceIndex();
      this.toSequenceIndex = transition.getToSequenceIndex();
      this.sampledFromAxisValue = transition.getFromAxisValue();
      this.sampledToAxisValue = transition.getToAxisValue();
      this.actionLowerBound = actionLowerBound;
      this.actionUpperBound = actionUpperBound;
      this.relativeTolerance = relativeTolerance;
      this.absoluteTolerance = absoluteTolerance;
      this.toleranceProvenance = toleranceProvenance;
      this.maximumEvaluations = maximumEvaluations;
      this.feasibleAxisValue = feasibleAxisValue;
      this.infeasibleAxisValue = infeasibleAxisValue;
      this.feasibleBoundaryConstraint = feasibleBoundaryConstraint;
      this.infeasibleBoundaryConstraint = infeasibleBoundaryConstraint;
      this.status = status;
      this.steps = Collections.unmodifiableList(new ArrayList<Step>(steps));
    }

    /** @return traversed continuous action identifier */
    public String getAxisActionId() {
      return axisActionId;
    }

    /** @return sampled source point sequence */
    public int getFromSequenceIndex() {
      return fromSequenceIndex;
    }

    /** @return sampled destination point sequence */
    public int getToSequenceIndex() {
      return toSequenceIndex;
    }

    /** @return sampled source action value */
    public double getSampledFromAxisValue() {
      return sampledFromAxisValue;
    }

    /** @return sampled destination action value */
    public double getSampledToAxisValue() {
      return sampledToAxisValue;
    }

    /** @return configured continuous action lower bound */
    public double getActionLowerBound() {
      return actionLowerBound;
    }

    /** @return configured continuous action upper bound */
    public double getActionUpperBound() {
      return actionUpperBound;
    }

    /** @return terminal relative range tolerance */
    public double getRelativeTolerance() {
      return relativeTolerance;
    }

    /** @return terminal absolute tolerance in the action unit */
    public double getAbsoluteTolerance() {
      return absoluteTolerance;
    }

    /** @return engineering source of the tolerance */
    public String getToleranceProvenance() {
      return toleranceProvenance;
    }

    /** @return configured maximum midpoint evaluations */
    public int getMaximumEvaluations() {
      return maximumEvaluations;
    }

    /** @return final independently evaluated feasible action value */
    public double getFeasibleAxisValue() {
      return feasibleAxisValue;
    }

    /** @return final independently evaluated infeasible action value */
    public double getInfeasibleAxisValue() {
      return infeasibleAxisValue;
    }

    /** @return unit-safe leading constraint at the final feasible endpoint, or null */
    public LeadingConstraintEvidence getFeasibleBoundaryConstraint() {
      return feasibleBoundaryConstraint;
    }

    /** @return exact rejecting constraint at the final infeasible endpoint, or null */
    public LeadingConstraintEvidence getInfeasibleBoundaryConstraint() {
      return infeasibleBoundaryConstraint;
    }

    /** @return final bracket width in the action unit */
    public double getBracketWidth() {
      return Math.abs(infeasibleAxisValue - feasibleAxisValue);
    }

    /** @return terminal refinement status */
    public Status getStatus() {
      return status;
    }

    /** @return fresh immutable midpoint-evaluation list */
    public List<Step> getSteps() {
      return Collections.unmodifiableList(new ArrayList<Step>(steps));
    }

    /** @return true only when the independently evaluated bracket met tolerance */
    public boolean isConverged() {
      return status == Status.CONVERGED && getBracketWidth() <= absoluteTolerance;
    }

    /** Converts this refinement receipt to a compact JSON-friendly map. */
    public Map<String, Object> toEvidenceMap() {
      List<Map<String, Object>> stepEvidence = new ArrayList<Map<String, Object>>();
      for (Step step : steps) {
        stepEvidence.add(step.toEvidenceMap());
      }
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("axis_action_id", axisActionId);
      evidence.put("action_value_semantics", ProcessModelOperatingAction.ValueSemantics.CONTINUOUS.name());
      evidence.put("from_sequence_index", Integer.valueOf(fromSequenceIndex));
      evidence.put("to_sequence_index", Integer.valueOf(toSequenceIndex));
      evidence.put("sampled_from_axis_value", finiteOrNull(sampledFromAxisValue));
      evidence.put("sampled_to_axis_value", finiteOrNull(sampledToAxisValue));
      evidence.put("action_lower_bound", finiteOrNull(actionLowerBound));
      evidence.put("action_upper_bound", finiteOrNull(actionUpperBound));
      evidence.put("relative_tolerance", finiteOrNull(relativeTolerance));
      evidence.put("absolute_tolerance", finiteOrNull(absoluteTolerance));
      evidence.put("tolerance_provenance", toleranceProvenance);
      evidence.put("maximum_evaluations", Integer.valueOf(maximumEvaluations));
      evidence.put("evaluation_count", Integer.valueOf(steps.size()));
      evidence.put("feasible_axis_value", finiteOrNull(feasibleAxisValue));
      evidence.put("infeasible_axis_value", finiteOrNull(infeasibleAxisValue));
      evidence.put("bracket_width", finiteOrNull(getBracketWidth()));
      evidence.put("feasible_boundary_constraint",
          feasibleBoundaryConstraint == null ? null : feasibleBoundaryConstraint.toEvidenceMap());
      evidence.put("infeasible_boundary_constraint",
          infeasibleBoundaryConstraint == null ? null : infeasibleBoundaryConstraint.toEvidenceMap());
      evidence.put("status", status.name());
      evidence.put("converged", Boolean.valueOf(isConverged()));
      evidence.put("steps", Collections.unmodifiableList(stepEvidence));
      evidence.put("interpolated", Boolean.FALSE);
      evidence.put("advisory", Boolean.TRUE);
      return Collections.unmodifiableMap(evidence);
    }

    /**
     * Immutable complete midpoint evaluation used by one refinement receipt.
     *
     * @author NeqSim Development Team
     * @version 1.0
     */
    public static final class Step implements Serializable {
      private static final long serialVersionUID = 1L;
      private final int sequenceIndex;
      private final double axisValue;
      private final double[] candidateValues;
      private final ProcessModelOperatingActionSetEvaluator.Outcome outcome;
      private final boolean feasible;
      private final boolean candidateSimulationConverged;
      private final boolean candidateEvaluatorFeasible;
      private final boolean baselineRestored;
      private final boolean baselineSimulationConverged;
      private final LeadingConstraintEvidence leadingConstraint;

      /** Creates one immutable midpoint evaluation from a complete evaluated point. */
      private Step(int sequenceIndex, double axisValue, double[] candidateValues,
          ProcessModelOperatingActionSetEvaluator.Outcome outcome, boolean feasible,
          boolean candidateSimulationConverged, boolean candidateEvaluatorFeasible, boolean baselineRestored,
          boolean baselineSimulationConverged, LeadingConstraintEvidence leadingConstraint) {
        this.sequenceIndex = sequenceIndex;
        this.axisValue = axisValue;
        this.candidateValues = Arrays.copyOf(candidateValues, candidateValues.length);
        this.outcome = outcome;
        this.feasible = feasible;
        this.candidateSimulationConverged = candidateSimulationConverged;
        this.candidateEvaluatorFeasible = candidateEvaluatorFeasible;
        this.baselineRestored = baselineRestored;
        this.baselineSimulationConverged = baselineSimulationConverged;
        this.leadingConstraint = leadingConstraint;
      }

      /** Creates one step from a complete runtime point. */
      private static Step fromPoint(int sequenceIndex, Point point) {
        CandidateSetEvaluationResult evaluation = point.getEvaluation();
        return new Step(sequenceIndex, point.getAxisValues()[0], point.getCandidateValues(), evaluation.getOutcome(),
            evaluation.isFeasible(), evaluation.isCandidateSimulationConverged(),
            evaluation.isCandidateEvaluatorFeasible(), evaluation.isBaselineRestored(),
            evaluation.isBaselineSimulationConverged(), point.getLeadingConstraint());
      }

      /** @return zero-based midpoint sequence */
      public int getSequenceIndex() {
        return sequenceIndex;
      }

      /** @return independently evaluated action value */
      public double getAxisValue() {
        return axisValue;
      }

      /** @return defensive complete candidate vector */
      public double[] getCandidateValues() {
        return Arrays.copyOf(candidateValues, candidateValues.length);
      }

      /** @return exact evaluator outcome */
      public ProcessModelOperatingActionSetEvaluator.Outcome getOutcome() {
        return outcome;
      }

      /** @return full evaluator feasibility */
      public boolean isFeasible() {
        return feasible;
      }

      /** @return candidate-process convergence */
      public boolean isCandidateSimulationConverged() {
        return candidateSimulationConverged;
      }

      /** @return candidate-evaluator feasibility */
      public boolean isCandidateEvaluatorFeasible() {
        return candidateEvaluatorFeasible;
      }

      /** @return baseline restoration success */
      public boolean isBaselineRestored() {
        return baselineRestored;
      }

      /** @return restored-baseline convergence */
      public boolean isBaselineSimulationConverged() {
        return baselineSimulationConverged;
      }

      /** @return unit-safe leading or rejecting evidence, or null */
      public LeadingConstraintEvidence getLeadingConstraint() {
        return leadingConstraint;
      }

      /** Converts this midpoint evaluation to a compact JSON-friendly map. */
      public Map<String, Object> toEvidenceMap() {
        Map<String, Object> evidence = new LinkedHashMap<String, Object>();
        evidence.put("sequence_index", Integer.valueOf(sequenceIndex));
        evidence.put("axis_value", finiteOrNull(axisValue));
        evidence.put("candidate_values", toFiniteDoubleList(candidateValues));
        evidence.put("outcome", outcome.name());
        evidence.put("feasible", Boolean.valueOf(feasible));
        evidence.put("candidate_simulation_converged", Boolean.valueOf(candidateSimulationConverged));
        evidence.put("candidate_evaluator_feasible", Boolean.valueOf(candidateEvaluatorFeasible));
        evidence.put("baseline_restored", Boolean.valueOf(baselineRestored));
        evidence.put("baseline_simulation_converged", Boolean.valueOf(baselineSimulationConverged));
        evidence.put("leading_constraint", leadingConstraint == null ? null : leadingConstraint.toEvidenceMap());
        return Collections.unmodifiableMap(evidence);
      }
    }
  }

  /**
   * Immutable serializable result for an operating-envelope slice.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class SliceResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String name;
    private final String provenance;
    private final String candidateEvaluatorId;
    private final String[] axisActionIds;
    private final ProcessModelOperatingAction.ValueSemantics[] axisActionSemantics;
    private final double[][] axisValues;
    private final double[] anchorValues;
    private final int requestedPointCount;
    private final Outcome outcome;
    private final List<Point> points;
    private final List<Transition> transitions;
    private final List<OperatingRegionTransition> operatingRegionTransitions;
    private final List<OperatingRegionBoundaryRefinement> boundaryRefinements;
    private final List<String> diagnostics;
    private final int evaluationCount;
    private final double runtimeSeconds;
    private final int maximumBoundaryRefinementEvaluations;
    private final double boundaryRefinementRelativeTolerance;
    private final String boundaryRefinementToleranceProvenance;
    private final transient ProcessModelOperatingEnvelopeStudy sourceStudy;

    /**
     * Creates an immutable result.
     *
     * @param id study identifier
     * @param name study name
     * @param provenance study provenance
     * @param candidateEvaluatorId evaluator identifier
     * @param axisActionIds axis action identifiers
     * @param axisActionSemantics axis action value semantics
     * @param axisValues requested grids
     * @param anchorValues complete anchor vector
     * @param requestedPointCount requested point count
     * @param outcome terminal outcome
     * @param points completed points
     * @param transitions sampled transitions
     * @param operatingRegionTransitions sampled feasibility/outcome/evidence transitions
     * @param boundaryRefinements fail-closed continuous feasibility-boundary receipts
     * @param diagnostics study diagnostics
     * @param evaluationCount simulator evaluations used by this slice
     * @param runtimeSeconds measured wall-clock runtime
     * @param maximumBoundaryRefinementEvaluations maximum midpoint evaluations per eligible transition
     * @param boundaryRefinementRelativeTolerance terminal bracket tolerance relative to the action range
     * @param boundaryRefinementToleranceProvenance engineering source of the tolerance
     * @param sourceStudy exact runtime study that produced this result
     */
    private SliceResult(String id, String name, String provenance, String candidateEvaluatorId, String[] axisActionIds,
        ProcessModelOperatingAction.ValueSemantics[] axisActionSemantics, double[][] axisValues, double[] anchorValues,
        int requestedPointCount, Outcome outcome, List<Point> points, List<Transition> transitions,
        List<OperatingRegionTransition> operatingRegionTransitions,
        List<OperatingRegionBoundaryRefinement> boundaryRefinements, List<String> diagnostics, int evaluationCount,
        double runtimeSeconds, int maximumBoundaryRefinementEvaluations, double boundaryRefinementRelativeTolerance,
        String boundaryRefinementToleranceProvenance, ProcessModelOperatingEnvelopeStudy sourceStudy) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.candidateEvaluatorId = candidateEvaluatorId;
      this.axisActionIds = Arrays.copyOf(axisActionIds, axisActionIds.length);
      this.axisActionSemantics = Arrays.copyOf(axisActionSemantics, axisActionSemantics.length);
      this.axisValues = deepCopy(axisValues);
      this.anchorValues = Arrays.copyOf(anchorValues, anchorValues.length);
      this.requestedPointCount = requestedPointCount;
      this.outcome = outcome;
      this.points = Collections.unmodifiableList(new ArrayList<Point>(points));
      this.transitions = Collections.unmodifiableList(new ArrayList<Transition>(transitions));
      this.operatingRegionTransitions = Collections
          .unmodifiableList(new ArrayList<OperatingRegionTransition>(operatingRegionTransitions));
      this.boundaryRefinements = Collections
          .unmodifiableList(new ArrayList<OperatingRegionBoundaryRefinement>(boundaryRefinements));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
      this.evaluationCount = evaluationCount;
      this.runtimeSeconds = runtimeSeconds;
      this.maximumBoundaryRefinementEvaluations = maximumBoundaryRefinementEvaluations;
      this.boundaryRefinementRelativeTolerance = boundaryRefinementRelativeTolerance;
      this.boundaryRefinementToleranceProvenance = boundaryRefinementToleranceProvenance;
      this.sourceStudy = sourceStudy;
    }

    /** @return study identifier */
    public String getId() {
      return id;
    }

    /** @return study name */
    public String getName() {
      return name;
    }

    /** @return study provenance */
    public String getProvenance() {
      return provenance;
    }

    /** @return candidate evaluator identifier */
    public String getCandidateEvaluatorId() {
      return candidateEvaluatorId;
    }

    /** @return defensive axis action identifiers */
    public String[] getAxisActionIds() {
      return Arrays.copyOf(axisActionIds, axisActionIds.length);
    }

    /** @return defensive axis action value semantics */
    public ProcessModelOperatingAction.ValueSemantics[] getAxisActionSemantics() {
      if (axisActionSemantics == null) {
        return new ProcessModelOperatingAction.ValueSemantics[0];
      }
      return Arrays.copyOf(axisActionSemantics, axisActionSemantics.length);
    }

    /** @return defensive requested axis grids */
    public double[][] getAxisValues() {
      return deepCopy(axisValues);
    }

    /** @return defensive complete anchor vector */
    public double[] getAnchorValues() {
      return Arrays.copyOf(anchorValues, anchorValues.length);
    }

    /** @return requested point count */
    public int getRequestedPointCount() {
      return requestedPointCount;
    }

    /** @return completed point count */
    public int getCompletedPointCount() {
      return points.size();
    }

    /** @return terminal outcome */
    public Outcome getOutcome() {
      return outcome;
    }

    /** @return true only when every requested point completed safely */
    public boolean isComplete() {
      return outcome == Outcome.COMPLETE && points.size() == requestedPointCount;
    }

    /** @return fresh immutable completed-point list */
    public List<Point> getPoints() {
      return Collections.unmodifiableList(new ArrayList<Point>(points));
    }

    /** @return fresh immutable feasible-point list */
    public List<Point> getFeasiblePoints() {
      List<Point> feasible = new ArrayList<Point>();
      for (Point point : points) {
        if (point.isFeasible()) {
          feasible.add(point);
        }
      }
      return Collections.unmodifiableList(feasible);
    }

    /** @return fresh immutable sampled-transition list */
    public List<Transition> getBottleneckTransitions() {
      return Collections.unmodifiableList(new ArrayList<Transition>(transitions));
    }

    /** @return fresh immutable sampled operating-region transition list */
    public List<OperatingRegionTransition> getOperatingRegionTransitions() {
      if (operatingRegionTransitions == null) {
        return Collections.emptyList();
      }
      return Collections.unmodifiableList(new ArrayList<OperatingRegionTransition>(operatingRegionTransitions));
    }

    /** @return fresh immutable fail-closed continuous-boundary refinement list */
    public List<OperatingRegionBoundaryRefinement> getBoundaryRefinements() {
      if (boundaryRefinements == null) {
        return Collections.emptyList();
      }
      return Collections.unmodifiableList(new ArrayList<OperatingRegionBoundaryRefinement>(boundaryRefinements));
    }

    /** @return fresh immutable study diagnostics */
    public List<String> getDiagnostics() {
      return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** @return simulator evaluations used by this slice */
    public int getEvaluationCount() {
      return evaluationCount;
    }

    /** @return measured wall-clock runtime in seconds */
    public double getRuntimeSeconds() {
      return runtimeSeconds;
    }

    /** @return maximum midpoint evaluations per eligible transition */
    public int getMaximumBoundaryRefinementEvaluations() {
      return maximumBoundaryRefinementEvaluations;
    }

    /** @return terminal bracket tolerance relative to the continuous action range */
    public double getBoundaryRefinementRelativeTolerance() {
      return boundaryRefinementRelativeTolerance;
    }

    /** @return engineering source of the terminal bracket tolerance */
    public String getBoundaryRefinementToleranceProvenance() {
      return boundaryRefinementToleranceProvenance;
    }

    /** Returns true only for the exact runtime study that produced this result. */
    private boolean isFromStudy(ProcessModelOperatingEnvelopeStudy study) {
      return sourceStudy == study;
    }

    /**
     * Converts this slice to a compact machine-readable evidence map.
     *
     * <p>
     * Non-finite numerical evidence is represented as null so the returned structure can be encoded as strict JSON. The
     * complete immutable Java result remains available through the typed getters.
     * </p>
     *
     * @return deeply immutable JSON-friendly evidence map
     */
    public Map<String, Object> toEvidenceMap() {
      List<Map<String, Object>> pointEvidence = new ArrayList<Map<String, Object>>();
      for (Point point : points) {
        pointEvidence.add(point.toEvidenceMap());
      }
      List<Map<String, Object>> transitionEvidence = new ArrayList<Map<String, Object>>();
      for (Transition transition : transitions) {
        transitionEvidence.add(transition.toEvidenceMap());
      }
      List<Map<String, Object>> operatingTransitionEvidence = new ArrayList<Map<String, Object>>();
      for (OperatingRegionTransition transition : getOperatingRegionTransitions()) {
        operatingTransitionEvidence.add(transition.toEvidenceMap());
      }
      List<Map<String, Object>> boundaryRefinementEvidence = new ArrayList<Map<String, Object>>();
      for (OperatingRegionBoundaryRefinement refinement : getBoundaryRefinements()) {
        boundaryRefinementEvidence.add(refinement.toEvidenceMap());
      }
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("study_id", id);
      evidence.put("study_name", name);
      evidence.put("study_provenance", provenance);
      evidence.put("candidate_evaluator_id", candidateEvaluatorId);
      evidence.put("axis_action_ids", toStringList(axisActionIds));
      evidence.put("axis_action_semantics", toStringList(getAxisActionSemantics()));
      evidence.put("axis_values", toFiniteDoubleLists(axisValues));
      evidence.put("anchor_values", toFiniteDoubleList(anchorValues));
      evidence.put("requested_point_count", Integer.valueOf(requestedPointCount));
      evidence.put("completed_point_count", Integer.valueOf(points.size()));
      evidence.put("feasible_point_count", Integer.valueOf(getFeasiblePoints().size()));
      evidence.put("evaluation_count", Integer.valueOf(evaluationCount));
      evidence.put("runtime_seconds", Double.valueOf(runtimeSeconds));
      evidence.put("outcome", outcome.name());
      evidence.put("complete", Boolean.valueOf(isComplete()));
      evidence.put("points", Collections.unmodifiableList(pointEvidence));
      evidence.put("bottleneck_transitions", Collections.unmodifiableList(transitionEvidence));
      evidence.put("operating_region_transitions", Collections.unmodifiableList(operatingTransitionEvidence));
      evidence.put("maximum_boundary_refinement_evaluations", Integer.valueOf(maximumBoundaryRefinementEvaluations));
      evidence.put("boundary_refinement_relative_tolerance", finiteOrNull(boundaryRefinementRelativeTolerance));
      evidence.put("boundary_refinement_tolerance_provenance", boundaryRefinementToleranceProvenance);
      evidence.put("operating_region_boundary_refinements", Collections.unmodifiableList(boundaryRefinementEvidence));
      evidence.put("diagnostics", Collections.unmodifiableList(new ArrayList<String>(diagnostics)));
      return Collections.unmodifiableMap(evidence);
    }

    /**
     * Deep-copies a two-dimensional array.
     *
     * @param values source arrays
     * @return defensive deep copy
     */
    private static double[][] deepCopy(double[][] values) {
      double[][] result = new double[values.length][];
      for (int i = 0; i < values.length; i++) {
        result[i] = Arrays.copyOf(values[i], values[i].length);
      }
      return result;
    }
  }
}
