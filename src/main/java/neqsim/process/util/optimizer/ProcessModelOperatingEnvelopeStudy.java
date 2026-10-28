package neqsim.process.util.optimizer;

import static neqsim.util.util.ArrayUtils.copyArray;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
  /** Largest normalized feasible margin classified as near-active in the cross-slice summary. */
  private double nearActiveConstraintNormalizedMargin = 0.10;
  /** Engineering source of the near-active classification threshold. */
  private String nearActiveConstraintThresholdProvenance = "default normalized remaining-margin screening threshold";

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
   * Sets the dimensionless feasible-margin threshold used by the cross-slice constraint summary.
   *
   * <p>
   * Only exact feasible evidence with a finite normalized margin at or below this value is classified as near-active.
   * Rejecting evidence is retained independently of this threshold. Physical margins with unlike units are never ranked
   * against one another.
   * </p>
   *
   * @param maximumNormalizedMargin finite non-negative normalized remaining margin
   * @param thresholdProvenance engineering source and screening purpose of the threshold
   * @return this study
   */
  public ProcessModelOperatingEnvelopeStudy setNearActiveConstraintNormalizedMargin(double maximumNormalizedMargin,
      String thresholdProvenance) {
    if (!Double.isFinite(maximumNormalizedMargin) || maximumNormalizedMargin < 0.0) {
      throw new IllegalArgumentException("Near-active normalized margin must be finite and non-negative");
    }
    nearActiveConstraintNormalizedMargin = maximumNormalizedMargin;
    nearActiveConstraintThresholdProvenance = requireText(thresholdProvenance,
        "Near-active constraint threshold provenance");
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
   * Builds a deterministic cross-slice summary from exact sampled and refined-endpoint constraint evidence.
   *
   * <p>
   * Every slice must be complete, originate from this exact runtime study, use unique ordered axes, and share one
   * identical anchor vector. The summary never reruns the model. Rejecting constraints rank before feasible near-active
   * constraints. Rejecting rows are ordered by occurrence counts; feasible rows are ordered only by their finite
   * dimensionless normalized margin. Physical margins with unlike units are retained as evidence but never compared.
   * </p>
   *
   * @param slices one or more complete slices from this runtime study
   * @return immutable ranked cross-slice constraint summary
   */
  public CrossSliceConstraintSummary summarizeConstraints(SliceResult... slices) {
    validateSummarySlices(slices);
    return buildConstraintSummary(slices);
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
    evidence.put("schema_version", Integer.valueOf(2));
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
    evidence.put("cross_slice_constraint_summary", buildConstraintSummary(slices).toEvidenceMap());
    evidence.put("advisory", Boolean.TRUE);
    evidence.put("interpretation",
        "Sampled operating evidence; not a continuous boundary, global optimum, shadow price, or operating approval");
    return Collections.unmodifiableMap(evidence);
  }

  /**
   * Validates slices for a read-only cross-slice summary.
   *
   * @param slices one or more slices to validate
   */
  private void validateSummarySlices(SliceResult... slices) {
    if (slices == null || slices.length == 0) {
      throw new IllegalArgumentException("Cross-slice constraint summary requires at least one slice");
    }
    List<String[]> axisSignatures = new ArrayList<String[]>();
    double[] anchor = null;
    for (SliceResult slice : slices) {
      if (slice == null || !slice.isFromStudy(this)) {
        throw new IllegalStateException("Constraint summary requires slices from this exact runtime study");
      }
      if (!slice.isComplete()) {
        throw new IllegalStateException("Constraint summary requires complete slices with safe baseline recovery");
      }
      if (anchor == null) {
        anchor = slice.getAnchorValues();
      } else if (!sameVector(anchor, slice.getAnchorValues())) {
        throw new IllegalStateException("Constraint summary slices do not share one exact anchor vector");
      }
      String[] signature = slice.getAxisActionIds();
      if (containsAxisSignature(axisSignatures, signature)) {
        throw new IllegalArgumentException(
            "Constraint summary contains duplicate slice axes: " + Arrays.toString(signature));
      }
      axisSignatures.add(signature);
    }
  }

  /**
   * Builds the summary after slice ownership, completion, axes, and anchors have been validated.
   *
   * @param slices validated complete slices
   * @return immutable cross-slice constraint summary
   */
  private CrossSliceConstraintSummary buildConstraintSummary(SliceResult... slices) {
    List<ConstraintSummaryAccumulator> accumulators = new ArrayList<ConstraintSummaryAccumulator>();
    for (SliceResult slice : slices) {
      String[] axes = slice.getAxisActionIds();
      for (Point point : slice.getPoints()) {
        observeConstraints(accumulators, point.getConstraintEvidence(), ConstraintEvidenceRole.SAMPLED_POINT, axes);
      }
      for (OperatingRegionBoundaryRefinement refinement : slice.getBoundaryRefinements()) {
        observeConstraints(accumulators, refinement.getFeasibleBoundaryConstraintEvidence(),
            ConstraintEvidenceRole.FEASIBLE_BOUNDARY, axes);
        observeConstraints(accumulators, refinement.getInfeasibleBoundaryConstraintEvidence(),
            ConstraintEvidenceRole.INFEASIBLE_BOUNDARY, axes);
      }
    }
    List<ConstraintSummaryAccumulator> ranked = new ArrayList<ConstraintSummaryAccumulator>();
    for (ConstraintSummaryAccumulator accumulator : accumulators) {
      if (accumulator.getRejectingEvidenceCount() > 0
          || accumulator.isNearActive(nearActiveConstraintNormalizedMargin)) {
        ranked.add(accumulator);
      }
    }
    Collections.sort(ranked, new ConstraintSummaryAccumulatorComparator());
    List<ConstraintSummaryEntry> entries = new ArrayList<ConstraintSummaryEntry>();
    for (int index = 0; index < ranked.size(); index++) {
      entries.add(ranked.get(index).toEntry(index + 1));
    }
    return new CrossSliceConstraintSummary(nearActiveConstraintNormalizedMargin,
        nearActiveConstraintThresholdProvenance, accumulators.size(), entries);
  }

  /**
   * Adds one exact constraint row to its compatible aggregate identity.
   *
   * @param accumulators mutable compatible-identity aggregates
   * @param evidence exact constraint row, or null
   * @param role sampled or refined-endpoint role
   * @param axes ordered slice axes
   */
  private static void observeConstraint(List<ConstraintSummaryAccumulator> accumulators,
      LeadingConstraintEvidence evidence, ConstraintEvidenceRole role, String[] axes) {
    if (evidence == null) {
      return;
    }
    for (ConstraintSummaryAccumulator accumulator : accumulators) {
      if (accumulator.matches(evidence)) {
        accumulator.observe(evidence, role, axes);
        return;
      }
    }
    ConstraintSummaryAccumulator accumulator = new ConstraintSummaryAccumulator(evidence);
    accumulator.observe(evidence, role, axes);
    accumulators.add(accumulator);
  }

  /** Adds every exact constraint row from one sampled or refined endpoint. */
  private static void observeConstraints(List<ConstraintSummaryAccumulator> accumulators,
      List<LeadingConstraintEvidence> evidenceRows, ConstraintEvidenceRole role, String[] axes) {
    for (LeadingConstraintEvidence evidence : evidenceRows) {
      observeConstraint(accumulators, evidence, role, axes);
    }
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
          leading(evaluation, installed), installed, constraintEvidence(evaluation));
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
        infeasible.getLeadingConstraint(), feasible.getConstraintEvidence(), infeasible.getConstraintEvidence(), status,
        steps);
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
    return new Point(sequence, gridIndexes, coordinates, candidate, result, leading(result, installed), installed,
        constraintEvidence(result));
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

  /**
   * Freezes the complete normalized constraint ladder needed for near-active and rejecting summaries.
   *
   * <p>
   * Feasible points retain every finite required-hydraulic, installed-equipment, and typed plant constraint. Rejected
   * points retain every exact fail-closed source that matches the evaluator outcome. This is a read-only traversal of
   * evidence already produced by the evaluator; it does not rerun the process model or relax any acceptance rule.
   * </p>
   */
  private static List<LeadingConstraintEvidence> constraintEvidence(CandidateSetEvaluationResult result) {
    List<LeadingConstraintEvidence> evidenceRows = new ArrayList<LeadingConstraintEvidence>();
    if (result.isFeasible()) {
      for (HydraulicConstraintSnapshot snapshot : result.getHydraulicConstraints()) {
        if (snapshot.isPresent() && snapshot.hasFiniteValue() && snapshot.isFeasible() && snapshot
            .getEvidenceApplicability() != ProcessModelSimulationEvaluator.BottleneckStatus.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE) {
          evidenceRows.add(LeadingConstraintEvidence.fromHydraulic(snapshot));
        }
      }
      for (InstalledEquipmentCapacityEvidence evidence : result.getInstalledEquipmentCapacityEvidence()) {
        if (evidence.isEnabled() && evidence.hasFiniteEvidence()) {
          evidenceRows.add(LeadingConstraintEvidence.fromInstalled(evidence));
        }
      }
      for (PlantConstraintEvidence evidence : result.getPlantConstraintEvidence()) {
        if (evidence.getDefinition().isEnabled() && evidence.hasAvailableEvidence()
            && Double.isFinite(evidence.getNormalizedUtilization())) {
          evidenceRows.add(LeadingConstraintEvidence.fromPlant(evidence));
        }
      }
      return Collections.unmodifiableList(evidenceRows);
    }

    switch (result.getOutcome()) {
    case REQUIRED_CONSTRAINT_MISSING:
    case CONSTRAINT_VALUE_UNAVAILABLE:
    case HYDRAULIC_CONSTRAINT_VIOLATED:
    case EVIDENCE_OUTSIDE_VALIDITY_RANGE:
      for (HydraulicConstraintSnapshot snapshot : result.getHydraulicConstraints()) {
        if (matchesHydraulicOutcome(result.getOutcome(), snapshot)) {
          evidenceRows.add(LeadingConstraintEvidence.fromHydraulic(snapshot));
        }
      }
      break;
    case OTHER_MODEL_CONSTRAINT_VIOLATED:
      for (InstalledEquipmentCapacityEvidence evidence : result.getInstalledEquipmentCapacityEvidence()) {
        if (evidence.isEnabled() && (!evidence.hasFiniteEvidence() || !evidence.isFeasible())) {
          evidenceRows.add(LeadingConstraintEvidence.fromInstalled(evidence));
        }
      }
      for (PlantConstraintEvidence evidence : result.getPlantConstraintEvidence()) {
        if (evidence.getDefinition().isEnabled() && (!evidence.hasAvailableEvidence() || !evidence.isFeasible())) {
          evidenceRows.add(LeadingConstraintEvidence.fromPlant(evidence));
        }
      }
      for (CandidateConstraintEvidence evidence : result.getConstraintEvidence()) {
        if (evidence.isHard() && !evidence.isSatisfied() && evidence.getPhysicalUnit() == null) {
          evidenceRows.add(LeadingConstraintEvidence.fromRegisteredConstraint(evidence));
        }
      }
      break;
    case FEASIBLE:
    default:
      break;
    }
    return Collections.unmodifiableList(evidenceRows);
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
      for (InstalledEquipmentCapacityEvidence evidence : result.getInstalledEquipmentCapacityEvidence()) {
        if (evidence.isEnabled() && (!evidence.hasFiniteEvidence() || !evidence.isFeasible())) {
          return LeadingConstraintEvidence.fromInstalled(evidence);
        }
      }
      for (PlantConstraintEvidence evidence : result.getPlantConstraintEvidence()) {
        if (evidence.getDefinition().isEnabled() && (!evidence.hasAvailableEvidence() || !evidence.isFeasible())) {
          return LeadingConstraintEvidence.fromPlant(evidence);
        }
      }
      for (CandidateConstraintEvidence evidence : result.getConstraintEvidence()) {
        if (evidence.isHard() && !evidence.isSatisfied() && evidence.getPhysicalUnit() == null) {
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

  /** Converts exact constraint rows to a deeply immutable JSON-friendly list. */
  private static List<Map<String, Object>> constraintEvidenceMaps(List<LeadingConstraintEvidence> rows) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (LeadingConstraintEvidence row : rows) {
      result.add(row.toEvidenceMap());
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

  /** Role of one exact row in the cross-slice summary. */
  private enum ConstraintEvidenceRole {
    /** Leading constraint at a sampled grid point. */
    SAMPLED_POINT,
    /** Leading constraint at the final feasible refined endpoint. */
    FEASIBLE_BOUNDARY,
    /** Rejecting constraint at the final infeasible refined endpoint. */
    INFEASIBLE_BOUNDARY
  }

  /** Mutable internal aggregate used only while constructing a summary. */
  private static final class ConstraintSummaryAccumulator {
    private final LeadingConstraintEvidence identity;
    private final List<List<String>> sliceAxes = new ArrayList<List<String>>();
    private int sampledPointCount;
    private int feasibleBoundaryCount;
    private int infeasibleBoundaryCount;
    private int feasibleEvidenceCount;
    private int rejectingEvidenceCount;
    private double minimumNormalizedMargin = Double.NaN;
    private LeadingConstraintEvidence representativeFeasibleConstraint;
    private LeadingConstraintEvidence representativeRejectingConstraint;

    /**
     * Creates an empty aggregate with the exact compatibility identity of one row.
     *
     * @param evidence first exact row for this identity
     */
    private ConstraintSummaryAccumulator(LeadingConstraintEvidence evidence) {
      identity = evidence;
    }

    /**
     * Returns true when one row has the exact grouping identity of this aggregate.
     *
     * @param evidence exact row to compare
     * @return true when every engineering-basis identity field matches
     */
    private boolean matches(LeadingConstraintEvidence evidence) {
      return identity.getSource() == evidence.getSource()
          && identity.getQualifiedConstraintName().equals(evidence.getQualifiedConstraintName())
          && Double.doubleToLongBits(identity.getApplicableLimit()) == Double
              .doubleToLongBits(evidence.getApplicableLimit())
          && identity.getPhysicalUnit().equals(evidence.getPhysicalUnit())
          && identity.getApplicableLimitRole().equals(evidence.getApplicableLimitRole())
          && identity.getApplicableLimitProvenance().equals(evidence.getApplicableLimitProvenance());
    }

    /**
     * Retains one exact occurrence and its evidence role.
     *
     * @param evidence exact compatible row
     * @param role sampled or refined-endpoint role
     * @param axes ordered slice axes
     */
    private void observe(LeadingConstraintEvidence evidence, ConstraintEvidenceRole role, String[] axes) {
      List<String> axisSignature = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(axes)));
      if (!sliceAxes.contains(axisSignature)) {
        sliceAxes.add(axisSignature);
      }
      switch (role) {
      case SAMPLED_POINT:
        sampledPointCount++;
        break;
      case FEASIBLE_BOUNDARY:
        feasibleBoundaryCount++;
        break;
      case INFEASIBLE_BOUNDARY:
        infeasibleBoundaryCount++;
        break;
      default:
        throw new IllegalStateException("Unsupported constraint evidence role: " + role);
      }
      if (evidence.isFeasible()) {
        feasibleEvidenceCount++;
        if (representativeFeasibleConstraint == null || isSmallerFiniteMargin(evidence.getNormalizedMargin(),
            representativeFeasibleConstraint.getNormalizedMargin())) {
          representativeFeasibleConstraint = evidence;
        }
        if (Double.isFinite(evidence.getNormalizedMargin()) && (!Double.isFinite(minimumNormalizedMargin)
            || evidence.getNormalizedMargin() < minimumNormalizedMargin)) {
          minimumNormalizedMargin = evidence.getNormalizedMargin();
        }
      } else {
        rejectingEvidenceCount++;
        if (representativeRejectingConstraint == null) {
          representativeRejectingConstraint = evidence;
        }
      }
    }

    /**
     * Returns true when the feasible aggregate is inside the configured normalized-margin screen.
     *
     * @param maximumNormalizedMargin inclusive dimensionless screen
     * @return true when a finite feasible margin passes the screen
     */
    private boolean isNearActive(double maximumNormalizedMargin) {
      return Double.isFinite(minimumNormalizedMargin) && minimumNormalizedMargin <= maximumNormalizedMargin;
    }

    /** @return number of exact rejecting rows in this aggregate */
    private int getRejectingEvidenceCount() {
      return rejectingEvidenceCount;
    }

    /**
     * Converts this aggregate to one immutable ranked entry.
     *
     * @param rank one-based deterministic rank
     * @return immutable summary entry
     */
    private ConstraintSummaryEntry toEntry(int rank) {
      ConstraintSummaryEntry.Classification classification = rejectingEvidenceCount > 0
          ? ConstraintSummaryEntry.Classification.REJECTING
          : ConstraintSummaryEntry.Classification.NEAR_ACTIVE;
      return new ConstraintSummaryEntry(rank, classification, identity.getSource(),
          identity.getQualifiedConstraintName(), identity.getApplicableLimit(), identity.getPhysicalUnit(),
          identity.getApplicableLimitRole(), identity.getApplicableLimitProvenance(), sliceAxes, sampledPointCount,
          feasibleBoundaryCount, infeasibleBoundaryCount, feasibleEvidenceCount, rejectingEvidenceCount,
          minimumNormalizedMargin, representativeFeasibleConstraint, representativeRejectingConstraint);
    }

    /**
     * Returns true when the first margin is the preferred finite representative.
     *
     * @param first candidate margin
     * @param second current representative margin
     * @return true when the candidate is finite and smaller
     */
    private static boolean isSmallerFiniteMargin(double first, double second) {
      return Double.isFinite(first) && (!Double.isFinite(second) || first < second);
    }
  }

  /** Deterministic ordering for rejecting and feasible near-active aggregates. */
  private static final class ConstraintSummaryAccumulatorComparator
      implements Comparator<ConstraintSummaryAccumulator>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Orders rejecting evidence first, then normalized feasible headroom, then exact identity.
     *
     * @param first first accumulator
     * @param second second accumulator
     * @return comparator result
     */
    @Override
    public int compare(ConstraintSummaryAccumulator first, ConstraintSummaryAccumulator second) {
      boolean firstRejecting = first.rejectingEvidenceCount > 0;
      boolean secondRejecting = second.rejectingEvidenceCount > 0;
      if (firstRejecting != secondRejecting) {
        return firstRejecting ? -1 : 1;
      }
      int comparison;
      if (firstRejecting) {
        comparison = Integer.compare(second.rejectingEvidenceCount, first.rejectingEvidenceCount);
        if (comparison == 0) {
          comparison = Integer.compare(second.infeasibleBoundaryCount, first.infeasibleBoundaryCount);
        }
      } else {
        comparison = Double.compare(first.minimumNormalizedMargin, second.minimumNormalizedMargin);
        if (comparison == 0) {
          comparison = Integer.compare(second.feasibleBoundaryCount, first.feasibleBoundaryCount);
        }
        if (comparison == 0) {
          comparison = Integer.compare(second.feasibleEvidenceCount, first.feasibleEvidenceCount);
        }
      }
      if (comparison != 0) {
        return comparison;
      }
      comparison = first.identity.getQualifiedConstraintName().compareTo(second.identity.getQualifiedConstraintName());
      if (comparison == 0) {
        comparison = first.identity.getSource().name().compareTo(second.identity.getSource().name());
      }
      if (comparison == 0) {
        comparison = Double.compare(first.identity.getApplicableLimit(), second.identity.getApplicableLimit());
      }
      if (comparison == 0) {
        comparison = first.identity.getPhysicalUnit().compareTo(second.identity.getPhysicalUnit());
      }
      if (comparison == 0) {
        comparison = first.identity.getApplicableLimitRole().compareTo(second.identity.getApplicableLimitRole());
      }
      if (comparison == 0) {
        comparison = first.identity.getApplicableLimitProvenance()
            .compareTo(second.identity.getApplicableLimitProvenance());
      }
      return comparison;
    }
  }

  /** Immutable qualified paired-relief receipt bound to one accepted operating point. */
  public static final class QualifiedReliefStudyEvidence implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String qualifiedConstraintName;
    private final Map<String, Object> evidence;

    /** Creates one deeply immutable receipt after complete validation. */
    private QualifiedReliefStudyEvidence(String qualifiedConstraintName, Map<String, Object> evidence) {
      this.qualifiedConstraintName = qualifiedConstraintName;
      this.evidence = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(evidence));
    }

    /** @return exact installed constraint identity */
    public String getQualifiedConstraintName() {
      return qualifiedConstraintName;
    }

    /** @return deeply immutable JSON-friendly receipt */
    public Map<String, Object> toEvidenceMap() {
      return evidence;
    }
  }

  /** Immutable ranked collection of exact qualified paired-relief receipts. */
  public static final class QualifiedReliefPortfolioEvidence implements Serializable {
    private static final long serialVersionUID = 1L;
    private final ProcessModelDebottleneckRanking.ObjectiveRankingResult rankingResult;
    private final List<QualifiedReliefStudyEvidence> rankedStudies;

    /**
     * Creates one immutable qualified portfolio.
     *
     * @param rankingResult exact objective ranking result
     * @param rankedStudies receipts in deterministic portfolio-rank order
     */
    private QualifiedReliefPortfolioEvidence(ProcessModelDebottleneckRanking.ObjectiveRankingResult rankingResult,
        List<QualifiedReliefStudyEvidence> rankedStudies) {
      this.rankingResult = rankingResult;
      this.rankedStudies = Collections.unmodifiableList(new ArrayList<QualifiedReliefStudyEvidence>(rankedStudies));
    }

    /** @return immutable objective-ranking result */
    public ProcessModelDebottleneckRanking.ObjectiveRankingResult getRankingResult() {
      return rankingResult;
    }

    /** @return exact qualified receipts in deterministic portfolio-rank order */
    public List<QualifiedReliefStudyEvidence> getRankedStudies() {
      return Collections.unmodifiableList(new ArrayList<QualifiedReliefStudyEvidence>(rankedStudies));
    }
  }

  /** Immutable exact receipt for one jointly evaluated qualified-relief combination. */
  public static final class QualifiedReliefCombinationEvidence implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> evidence;

    /** Creates one deeply immutable combination receipt after complete validation. */
    private QualifiedReliefCombinationEvidence(Map<String, Object> evidence) {
      this.evidence = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(evidence));
    }

    /** @return deeply immutable JSON-friendly non-additive combination receipt */
    public Map<String, Object> toEvidenceMap() {
      return evidence;
    }
  }

  /**
   * Qualifies one existing paired debottleneck result against this study's accepted operating point.
   *
   * <p>
   * The paired study must use the exact transactional evaluator that produced the operating envelope. Its baseline
   * action vector and objective evidence must bitwise match the independently replayed accepted point. Both paired
   * scenarios must be converged, feasible, finite and completely restored, and the installed capacity state must be
   * exactly recovered before a receipt is returned.
   * </p>
   *
   * @param search independently replayed accepted operating point
   * @param pairedStudy existing paired installed-capacity study
   * @param result immutable result returned by {@code pairedStudy.evaluate()}
   * @return qualified receipt suitable for attachment to the exact opportunity-screen row
   * @throws IllegalArgumentException if the study or result belongs to another evaluator or definition
   * @throws IllegalStateException if any convergence, finiteness, binding, feasibility, or restoration gate fails
   */
  public QualifiedReliefStudyEvidence qualifyReliefStudy(OperatingPointSearchResult search,
      ProcessModelDebottleneckStudy pairedStudy, ProcessModelDebottleneckStudy.StudyResult result) {
    double[] acceptedValues = validateAcceptedSearch(search);
    if (pairedStudy == null || result == null) {
      throw new IllegalArgumentException("Paired relief study and result must not be null");
    }
    if (pairedStudy.getOperatingActionEvaluator() != evaluator) {
      throw new IllegalArgumentException("Paired relief study must use the exact operating-envelope evaluator");
    }
    if (!sameText(result.getId(), pairedStudy.getId()) || !sameText(result.getName(), pairedStudy.getName())
        || !sameText(result.getProvenance(), pairedStudy.getProvenance())
        || result.getAlternativeDefinition() != pairedStudy.getAlternative()) {
      throw new IllegalArgumentException("Paired relief result does not belong to the supplied study definition");
    }
    if (result.getOutcome() != ProcessModelDebottleneckStudy.StudyOutcome.COMPLETED || !result.isCapacityRestored()
        || !result.isProcessStateRestored() || !result.isRecoverySimulationConverged() || result.getBaseline() == null
        || result.getAlternative() == null || !result.getBaseline().isQualified()
        || !result.getAlternative().isQualified() || !result.isObjectiveDeltaCalculable()
        || !Double.isFinite(result.getObjectiveDelta())) {
      throw new IllegalStateException("Paired relief result is not completely qualified and restored");
    }
    if (!sameVector(acceptedValues, result.getBaseline().getSelectedParameters())) {
      throw new IllegalStateException("Paired relief baseline does not match the accepted operating point exactly");
    }
    int objectiveIndex = pairedStudy.getObjectiveIndex();
    List<ProcessModelDebottleneckStudy.ObjectiveEvidence> baselineObjectives = result.getBaseline().getObjectives();
    List<ProcessModelDebottleneckStudy.ObjectiveEvidence> alternativeObjectives = result.getAlternative()
        .getObjectives();
    List<CandidateObjectiveEvidence> acceptedObjectives = search.getAcceptedPointReplay().getEvaluation()
        .getObjectiveEvidence();
    if (objectiveIndex >= baselineObjectives.size() || objectiveIndex >= alternativeObjectives.size()
        || objectiveIndex >= acceptedObjectives.size()) {
      throw new IllegalStateException("Paired relief result lacks complete objective evidence");
    }
    ProcessModelDebottleneckStudy.ObjectiveEvidence baseline = baselineObjectives.get(objectiveIndex);
    ProcessModelDebottleneckStudy.ObjectiveEvidence alternative = alternativeObjectives.get(objectiveIndex);
    CandidateObjectiveEvidence accepted = acceptedObjectives.get(objectiveIndex);
    double objectiveBindingTolerance = Math.max(search.getObjectiveImprovementTolerance(),
        Math.abs(accepted.getRawValue()) * search.getReplayRelativeTolerance());
    double objectiveBindingResidual = Math.abs(baseline.getRawValue() - accepted.getRawValue());
    if (baseline.getIndex() != accepted.getIndex() || !sameText(baseline.getName(), accepted.getName())
        || baseline.getDirection() != accepted.getDirection() || !sameText(baseline.getUnit(), accepted.getUnit())
        || !sameDouble(baseline.getWeight(), accepted.getWeight()) || !Double.isFinite(objectiveBindingResidual)
        || objectiveBindingResidual > objectiveBindingTolerance || !Double.isFinite(alternative.getRawValue())
        || !sameDouble(result.getObjectiveDelta(), alternative.getRawValue() - baseline.getRawValue())) {
      throw new IllegalStateException("Paired relief objective evidence is stale, non-finite, or incompatible: "
          + "baseline=" + baseline.getRawValue() + ", accepted=" + accepted.getRawValue() + ", alternative="
          + alternative.getRawValue() + ", delta=" + result.getObjectiveDelta() + ", baselineName=" + baseline.getName()
          + ", acceptedName=" + accepted.getName());
    }
    ProcessModelDebottleneckStudy.CapacityAlternative capacity = result.getAlternativeDefinition();
    ProcessModelDebottleneckStudy.CapacityState original = result.getOriginalCapacityState();
    ProcessModelDebottleneckStudy.CapacityState applied = result.getAppliedCapacityState();
    if (original == null || applied == null || !sameText(original.getUnit(), capacity.getUnit())
        || !sameText(applied.getUnit(), capacity.getUnit())
        || !sameDouble(applied.getApplicableLimit(), capacity.getProposedLimit())) {
      throw new IllegalStateException("Paired relief capacity-state evidence is incomplete or incompatible");
    }

    List<String> actionIds = new ArrayList<String>();
    for (ProcessModelOperatingAction action : evaluator.getActions()) {
      actionIds.add(action.getId());
    }
    Map<String, Object> objective = new LinkedHashMap<String, Object>();
    objective.put("index", Integer.valueOf(objectiveIndex));
    objective.put("name", baseline.getName());
    objective.put("direction", baseline.getDirection().name());
    objective.put("unit", baseline.getUnit());
    objective.put("weight", Double.valueOf(baseline.getWeight()));
    objective.put("baseline_value", Double.valueOf(accepted.getRawValue()));
    objective.put("paired_baseline_value", Double.valueOf(baseline.getRawValue()));
    objective.put("baseline_binding_residual", Double.valueOf(objectiveBindingResidual));
    objective.put("baseline_binding_tolerance", Double.valueOf(objectiveBindingTolerance));
    objective.put("baseline_binding_tolerance_provenance",
        search.getObjectiveToleranceProvenance() + "; " + search.getReplayToleranceProvenance());
    objective.put("alternative_value", Double.valueOf(alternative.getRawValue()));
    objective.put("delta", Double.valueOf(result.getObjectiveDelta()));
    Map<String, Object> receipt = new LinkedHashMap<String, Object>();
    receipt.put("receipt_version", Integer.valueOf(1));
    receipt.put("study_id", result.getId());
    receipt.put("study_name", result.getName());
    receipt.put("study_provenance", result.getProvenance());
    receipt.put("alternative_id", capacity.getId());
    receipt.put("alternative_name", capacity.getName());
    receipt.put("alternative_provenance", capacity.getProvenance());
    receipt.put("action_set_id", evaluator.getId());
    receipt.put("accepted_action_ids", Collections.unmodifiableList(actionIds));
    receipt.put("baseline_action_values", doubleList(acceptedValues));
    receipt.put("alternative_action_values", doubleList(result.getAlternative().getSelectedParameters()));
    receipt.put("qualified_constraint_name", capacity.getQualifiedConstraintName());
    receipt.put("original_applicable_limit", Double.valueOf(original.getApplicableLimit()));
    receipt.put("proposed_applicable_limit", Double.valueOf(capacity.getProposedLimit()));
    receipt.put("physical_unit", capacity.getUnit());
    receipt.put("limit_direction", capacity.getLimitDirection().name());
    receipt.put("proposal_source", capacity.getProposalSource());
    receipt.put("objective", Collections.unmodifiableMap(objective));
    receipt.put("study_outcome", result.getOutcome().name());
    receipt.put("capacity_restored", Boolean.TRUE);
    receipt.put("process_state_restored", Boolean.TRUE);
    receipt.put("recovery_simulation_converged", Boolean.TRUE);
    receipt.put("qualification_state", "QUALIFIED_PAIRED_SAMPLED_RELIEF");
    receipt.put("advisory", Boolean.TRUE);
    receipt.put("interpretation",
        "Paired converged sampled evidence for the declared capacity alternative; not causal attribution, a shadow price, design approval, investment approval, permission to change a limit, or operating approval");
    return new QualifiedReliefStudyEvidence(capacity.getQualifiedConstraintName(), receipt);
  }

  /**
   * Qualifies and ranks multiple independent paired-relief studies against one accepted operating point.
   *
   * <p>
   * Every study first passes the exact single-study binding and restoration gates. The existing
   * {@link ProcessModelDebottleneckRanking} then compares one declared raw objective across the common paired baseline.
   * Partial portfolios are rejected so a published rank cannot silently omit an invalid candidate. Every returned
   * receipt carries the same portfolio identity and candidate count plus its deterministic competition rank.
   * </p>
   *
   * @param search independently replayed accepted operating point
   * @param ranking existing debottleneck portfolio ranking configuration
   * @param objectivePolicy exact single-objective comparison policy
   * @param pairedStudies independent paired studies in deterministic submission order
   * @param results immutable results corresponding one-to-one with {@code pairedStudies}
   * @return exact qualified receipts in deterministic portfolio-rank order
   * @throws IllegalArgumentException if inputs are incomplete, duplicated, or not paired one-to-one
   * @throws IllegalStateException if any study, accepted-point binding, restoration, or ranking gate fails
   */
  public QualifiedReliefPortfolioEvidence qualifyReliefPortfolio(OperatingPointSearchResult search,
      ProcessModelDebottleneckRanking ranking, ProcessModelDebottleneckRanking.ObjectiveRankingPolicy objectivePolicy,
      List<ProcessModelDebottleneckStudy> pairedStudies, List<ProcessModelDebottleneckStudy.StudyResult> results) {
    if (ranking == null || objectivePolicy == null) {
      throw new IllegalArgumentException("Debottleneck ranking and objective policy must not be null");
    }
    if (pairedStudies == null || results == null || pairedStudies.size() < 2
        || pairedStudies.size() != results.size()) {
      throw new IllegalArgumentException("At least two one-to-one paired studies and results are required");
    }
    Map<String, QualifiedReliefStudyEvidence> receiptsByStudy = new LinkedHashMap<String, QualifiedReliefStudyEvidence>();
    for (int index = 0; index < pairedStudies.size(); index++) {
      ProcessModelDebottleneckStudy pairedStudy = pairedStudies.get(index);
      ProcessModelDebottleneckStudy.StudyResult result = results.get(index);
      QualifiedReliefStudyEvidence receipt = qualifyReliefStudy(search, pairedStudy, result);
      if (receiptsByStudy.put(result.getId(), receipt) != null) {
        throw new IllegalArgumentException("Duplicate paired relief study identifier: " + result.getId());
      }
    }

    ProcessModelDebottleneckRanking.ObjectiveRankingResult rankingResult = ranking.rankObjectives(results,
        objectivePolicy);
    if (rankingResult.getOutcome() != ProcessModelDebottleneckRanking.RankingOutcome.COMPLETED
        || rankingResult.getRankedCandidates().size() != results.size()
        || !rankingResult.getRejectedCandidates().isEmpty()) {
      throw new IllegalStateException(
          "Qualified relief portfolio requires every submitted study to rank: " + rankingResult.getOutcome());
    }

    int candidateCount = rankingResult.getRankedCandidates().size();
    List<QualifiedReliefStudyEvidence> rankedReceipts = new ArrayList<QualifiedReliefStudyEvidence>();
    for (ProcessModelDebottleneckRanking.ObjectiveCandidateEvidence candidate : rankingResult.getRankedCandidates()) {
      QualifiedReliefStudyEvidence receipt = receiptsByStudy.remove(candidate.getStudyResult().getId());
      if (receipt == null || !sameDouble(candidate.getDelta(),
          ((Number) ((Map<?, ?>) receipt.toEvidenceMap().get("objective")).get("delta")).doubleValue())) {
        throw new IllegalStateException(
            "Objective ranking is not bound to the qualified paired receipt: " + candidate.getStudyResult().getId());
      }
      Map<String, Object> portfolio = new LinkedHashMap<String, Object>();
      portfolio.put("portfolio_id", rankingResult.getId());
      portfolio.put("portfolio_name", rankingResult.getName());
      portfolio.put("portfolio_provenance", rankingResult.getProvenance());
      portfolio.put("policy_id", objectivePolicy.getId());
      portfolio.put("policy_name", objectivePolicy.getName());
      portfolio.put("policy_provenance", objectivePolicy.getProvenance());
      portfolio.put("objective_index", Integer.valueOf(objectivePolicy.getObjectiveIndex()));
      portfolio.put("objective_name", objectivePolicy.getObjectiveName());
      portfolio.put("objective_direction", objectivePolicy.getObjectiveDirection().name());
      portfolio.put("objective_unit", objectivePolicy.getUnit());
      portfolio.put("objective_weight", Double.valueOf(objectivePolicy.getWeight()));
      portfolio.put("tie_tolerance", Double.valueOf(objectivePolicy.getTieTolerance()));
      portfolio.put("baseline_relative_tolerance", Double.valueOf(objectivePolicy.getBaselineRelativeTolerance()));
      portfolio.put("candidate_count", Integer.valueOf(candidateCount));
      portfolio.put("rank", Integer.valueOf(candidate.getRank()));
      portfolio.put("input_index", Integer.valueOf(candidate.getInputIndex()));
      portfolio.put("alternative_id", candidate.getAlternativeDefinition().getId());
      portfolio.put("qualified_constraint_name", candidate.getAlternativeDefinition().getQualifiedConstraintName());
      portfolio.put("objective_delta", Double.valueOf(candidate.getDelta()));
      portfolio.put("ranking_outcome", rankingResult.getOutcome().name());
      portfolio.put("qualification_state", "QUALIFIED_PAIRED_OBJECTIVE_PORTFOLIO_RANK");
      portfolio.put("advisory", Boolean.TRUE);
      portfolio.put("interpretation",
          "Deterministic rank of compatible paired sampled objective deltas; not causal attribution, a shadow price, "
              + "global optimality, investment approval, permission to change a limit, or operating approval");
      Map<String, Object> enriched = new LinkedHashMap<String, Object>(receipt.toEvidenceMap());
      enriched.put("receipt_version", Integer.valueOf(2));
      enriched.put("portfolio_ranking", Collections.unmodifiableMap(portfolio));
      rankedReceipts.add(new QualifiedReliefStudyEvidence(receipt.getQualifiedConstraintName(), enriched));
    }
    if (!receiptsByStudy.isEmpty()) {
      throw new IllegalStateException("Qualified relief portfolio lost a ranked study receipt");
    }
    return new QualifiedReliefPortfolioEvidence(rankingResult, rankedReceipts);
  }

  /**
   * Qualifies one jointly simulated combination against the independently ranked portfolio and accepted point.
   *
   * <p>
   * The combination study must apply every portfolio member together through the same transactional evaluator and
   * search policy. Its observed objective delta is retained separately from the sum of independent paired deltas; the
   * difference is reported as an interaction diagnostic, never as an assumed additive benefit. Exact installed capacity
   * evidence identifies the most utilized baseline and combined constraints so a bottleneck migration is reported from
   * simulator evidence rather than inferred from rank order.
   * </p>
   *
   * @param search independently replayed accepted operating point
   * @param portfolio exact independently qualified portfolio
   * @param combinationStudy paired study applying all portfolio members together
   * @param combinationResult immutable result returned by {@code combinationStudy.evaluate()}
   * @return exact non-additive combination and bottleneck-migration receipt
   * @throws IllegalArgumentException if definitions or membership are inconsistent
   * @throws IllegalStateException if convergence, finiteness, binding, or restoration evidence is incomplete
   */
  public QualifiedReliefCombinationEvidence qualifyReliefCombination(OperatingPointSearchResult search,
      QualifiedReliefPortfolioEvidence portfolio, ProcessModelDebottleneckStudy combinationStudy,
      ProcessModelDebottleneckStudy.StudyResult combinationResult) {
    double[] acceptedValues = validateAcceptedSearch(search);
    if (portfolio == null || combinationStudy == null || combinationResult == null) {
      throw new IllegalArgumentException("Portfolio, combination study, and result must not be null");
    }
    if (combinationStudy.getOperatingActionEvaluator() != evaluator) {
      throw new IllegalArgumentException("Combination study must use the exact operating-envelope evaluator");
    }
    if (!sameText(combinationStudy.getId(), combinationResult.getId())
        || !sameText(combinationStudy.getName(), combinationResult.getName())
        || !sameText(combinationStudy.getProvenance(), combinationResult.getProvenance())) {
      throw new IllegalArgumentException("Combination result does not belong to the supplied study definition");
    }
    List<ProcessModelDebottleneckStudy.CapacityAlternative> combinationAlternatives = combinationStudy
        .getAlternatives();
    if (combinationAlternatives.size() < 2
        || combinationAlternatives.size() != combinationResult.getAlternativeDefinitions().size()
        || combinationAlternatives.size() != portfolio.getRankedStudies().size()) {
      throw new IllegalArgumentException("Combination membership must exactly match the qualified portfolio");
    }
    Map<String, Map<String, Object>> portfolioMembers = new LinkedHashMap<String, Map<String, Object>>();
    for (QualifiedReliefStudyEvidence study : portfolio.getRankedStudies()) {
      Map<String, Object> receipt = study.toEvidenceMap();
      portfolioMembers.put((String) receipt.get("alternative_id"), receipt);
    }
    for (ProcessModelDebottleneckStudy.CapacityAlternative capacity : combinationAlternatives) {
      Map<String, Object> receipt = portfolioMembers.remove(capacity.getId());
      if (receipt == null
          || !sameText(capacity.getQualifiedConstraintName(), (String) receipt.get("qualified_constraint_name"))
          || !sameDouble(capacity.getProposedLimit(), ((Number) receipt.get("proposed_applicable_limit")).doubleValue())
          || !sameText(capacity.getUnit(), (String) receipt.get("physical_unit"))) {
        throw new IllegalArgumentException(
            "Combination member is not identical to its qualified portfolio receipt: " + capacity.getId());
      }
    }
    if (!portfolioMembers.isEmpty()) {
      throw new IllegalArgumentException("Combination omitted qualified portfolio members");
    }
    if (combinationResult.getOutcome() != ProcessModelDebottleneckStudy.StudyOutcome.COMPLETED
        || !combinationResult.isCapacityRestored() || !combinationResult.isProcessStateRestored()
        || !combinationResult.isRecoverySimulationConverged() || combinationResult.getBaseline() == null
        || combinationResult.getAlternative() == null || !combinationResult.getBaseline().isQualified()
        || !combinationResult.getAlternative().isQualified() || !combinationResult.isObjectiveDeltaCalculable()
        || !Double.isFinite(combinationResult.getObjectiveDelta())
        || combinationResult.getOriginalCapacityStates().size() != combinationAlternatives.size()
        || combinationResult.getAppliedCapacityStates().size() != combinationAlternatives.size()) {
      throw new IllegalStateException("Combined relief result is not completely qualified and restored");
    }
    if (!sameVector(acceptedValues, combinationResult.getBaseline().getSelectedParameters())) {
      throw new IllegalStateException("Combined relief baseline does not match the accepted operating point exactly");
    }
    ProcessModelDebottleneckRanking.ObjectiveRankingResult rankingResult = portfolio.getRankingResult();
    ProcessModelDebottleneckRanking.ObjectiveRankingPolicy policy = rankingResult.getPolicy();
    int objectiveIndex = policy.getObjectiveIndex();
    List<ProcessModelDebottleneckStudy.ObjectiveEvidence> baselineObjectives = combinationResult.getBaseline()
        .getObjectives();
    List<ProcessModelDebottleneckStudy.ObjectiveEvidence> alternativeObjectives = combinationResult.getAlternative()
        .getObjectives();
    List<CandidateObjectiveEvidence> acceptedObjectives = search.getAcceptedPointReplay().getEvaluation()
        .getObjectiveEvidence();
    if (objectiveIndex >= baselineObjectives.size() || objectiveIndex >= alternativeObjectives.size()
        || objectiveIndex >= acceptedObjectives.size()) {
      throw new IllegalStateException("Combined relief result lacks complete objective evidence");
    }
    ProcessModelDebottleneckStudy.ObjectiveEvidence baseline = baselineObjectives.get(objectiveIndex);
    ProcessModelDebottleneckStudy.ObjectiveEvidence combined = alternativeObjectives.get(objectiveIndex);
    CandidateObjectiveEvidence accepted = acceptedObjectives.get(objectiveIndex);
    double bindingTolerance = Math.max(search.getObjectiveImprovementTolerance(),
        Math.abs(accepted.getRawValue()) * search.getReplayRelativeTolerance());
    double bindingResidual = Math.abs(baseline.getRawValue() - accepted.getRawValue());
    if (baseline.getIndex() != objectiveIndex || !sameText(baseline.getName(), policy.getObjectiveName())
        || baseline.getDirection() != policy.getObjectiveDirection() || !sameText(baseline.getUnit(), policy.getUnit())
        || !sameDouble(baseline.getWeight(), policy.getWeight()) || !Double.isFinite(bindingResidual)
        || bindingResidual > bindingTolerance || !Double.isFinite(combined.getRawValue())
        || !sameDouble(combinationResult.getObjectiveDelta(), combined.getRawValue() - baseline.getRawValue())) {
      throw new IllegalStateException("Combined relief objective evidence is stale, non-finite, or incompatible");
    }
    double independentDeltaSum = 0.0;
    for (ProcessModelDebottleneckRanking.ObjectiveCandidateEvidence candidate : rankingResult.getRankedCandidates()) {
      independentDeltaSum += candidate.getDelta();
    }
    double interactionDelta = combinationResult.getObjectiveDelta() - independentDeltaSum;
    if (!Double.isFinite(independentDeltaSum) || !Double.isFinite(interactionDelta)) {
      throw new IllegalStateException("Combined relief interaction evidence is non-finite");
    }

    List<Map<String, Object>> members = new ArrayList<Map<String, Object>>();
    for (int index = 0; index < combinationAlternatives.size(); index++) {
      ProcessModelDebottleneckStudy.CapacityAlternative capacity = combinationAlternatives.get(index);
      ProcessModelDebottleneckStudy.CapacityState original = combinationResult.getOriginalCapacityStates().get(index);
      ProcessModelDebottleneckStudy.CapacityState applied = combinationResult.getAppliedCapacityStates().get(index);
      if (!sameText(original.getUnit(), capacity.getUnit()) || !sameText(applied.getUnit(), capacity.getUnit())
          || !sameDouble(applied.getApplicableLimit(), capacity.getProposedLimit())) {
        throw new IllegalStateException("Combined relief capacity-state evidence is incomplete: " + capacity.getId());
      }
      Map<String, Object> member = new LinkedHashMap<String, Object>();
      member.put("alternative_id", capacity.getId());
      member.put("alternative_name", capacity.getName());
      member.put("alternative_provenance", capacity.getProvenance());
      member.put("qualified_constraint_name", capacity.getQualifiedConstraintName());
      member.put("original_applicable_limit", Double.valueOf(original.getApplicableLimit()));
      member.put("proposed_applicable_limit", Double.valueOf(applied.getApplicableLimit()));
      member.put("physical_unit", capacity.getUnit());
      member.put("limit_direction", capacity.getLimitDirection().name());
      member.put("proposal_source", capacity.getProposalSource());
      members.add(Collections.unmodifiableMap(member));
    }
    Map<String, Object> objective = new LinkedHashMap<String, Object>();
    objective.put("index", Integer.valueOf(objectiveIndex));
    objective.put("name", policy.getObjectiveName());
    objective.put("direction", policy.getObjectiveDirection().name());
    objective.put("unit", policy.getUnit());
    objective.put("weight", Double.valueOf(policy.getWeight()));
    objective.put("baseline_value", Double.valueOf(accepted.getRawValue()));
    objective.put("paired_baseline_value", Double.valueOf(baseline.getRawValue()));
    objective.put("baseline_binding_residual", Double.valueOf(bindingResidual));
    objective.put("baseline_binding_tolerance", Double.valueOf(bindingTolerance));
    objective.put("combined_value", Double.valueOf(combined.getRawValue()));
    objective.put("combined_delta", Double.valueOf(combinationResult.getObjectiveDelta()));
    objective.put("independent_delta_sum", Double.valueOf(independentDeltaSum));
    objective.put("interaction_delta", Double.valueOf(interactionDelta));
    Map<String, Object> migration = new LinkedHashMap<String, Object>();
    Map<String, Object> baselineBottleneck = mostUtilizedInstalledConstraint(
        combinationResult.getBaseline().getInstalledCapacityEvidence());
    Map<String, Object> combinedBottleneck = mostUtilizedInstalledConstraint(
        combinationResult.getAlternative().getInstalledCapacityEvidence());
    migration.put("baseline_bottleneck", baselineBottleneck);
    migration.put("combined_bottleneck", combinedBottleneck);
    migration.put("changed", Boolean.valueOf(!sameText((String) baselineBottleneck.get("qualified_constraint_name"),
        (String) combinedBottleneck.get("qualified_constraint_name"))));
    migration.put("basis", "highest finite normalized installed-capacity utilization at each qualified point");

    List<String> actionIds = new ArrayList<String>();
    for (ProcessModelOperatingAction action : evaluator.getActions()) {
      actionIds.add(action.getId());
    }
    Map<String, Object> receipt = new LinkedHashMap<String, Object>();
    receipt.put("receipt_version", Integer.valueOf(1));
    receipt.put("study_id", combinationResult.getId());
    receipt.put("study_name", combinationResult.getName());
    receipt.put("study_provenance", combinationResult.getProvenance());
    receipt.put("portfolio_id", rankingResult.getId());
    receipt.put("policy_id", policy.getId());
    receipt.put("action_set_id", evaluator.getId());
    receipt.put("accepted_action_ids", Collections.unmodifiableList(actionIds));
    receipt.put("baseline_action_values", doubleList(acceptedValues));
    receipt.put("combined_action_values", doubleList(combinationResult.getAlternative().getSelectedParameters()));
    receipt.put("members", Collections.unmodifiableList(members));
    receipt.put("objective", Collections.unmodifiableMap(objective));
    receipt.put("bottleneck_migration", Collections.unmodifiableMap(migration));
    receipt.put("study_outcome", combinationResult.getOutcome().name());
    receipt.put("capacity_restored", Boolean.TRUE);
    receipt.put("process_state_restored", Boolean.TRUE);
    receipt.put("recovery_simulation_converged", Boolean.TRUE);
    receipt.put("qualification_state", "QUALIFIED_PAIRED_NON_ADDITIVE_RELIEF_COMBINATION");
    receipt.put("advisory", Boolean.TRUE);
    receipt.put("interpretation",
        "Jointly simulated paired combination with explicit interaction and bottleneck migration; not an additive "
            + "benefit assumption, causal attribution, shadow price, design approval, investment approval, permission "
            + "to change a limit, or operating approval");
    return new QualifiedReliefCombinationEvidence(receipt);
  }

  /** Returns exact evidence for the most utilized finite installed constraint at one qualified point. */
  private static Map<String, Object> mostUtilizedInstalledConstraint(
      List<InstalledEquipmentCapacityEvidence> evidence) {
    InstalledEquipmentCapacityEvidence selected = null;
    for (InstalledEquipmentCapacityEvidence candidate : evidence) {
      if (!candidate.isEnabled() || !Double.isFinite(candidate.getNormalizedUtilization())) {
        continue;
      }
      if (selected == null || candidate.getNormalizedUtilization() > selected.getNormalizedUtilization()
          || sameDouble(candidate.getNormalizedUtilization(), selected.getNormalizedUtilization())
              && candidate.getQualifiedConstraintName().compareTo(selected.getQualifiedConstraintName()) < 0) {
        selected = candidate;
      }
    }
    if (selected == null) {
      throw new IllegalStateException("Qualified relief scenario lacks finite installed-capacity evidence");
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("qualified_constraint_name", selected.getQualifiedConstraintName());
    result.put("current_value", Double.valueOf(selected.getCurrentValue()));
    result.put("applicable_limit", Double.valueOf(selected.getApplicableLimit()));
    result.put("normalized_utilization", Double.valueOf(selected.getNormalizedUtilization()));
    result.put("physical_unit", selected.getPhysicalUnit());
    result.put("feasible", Boolean.valueOf(selected.isFeasible()));
    return Collections.unmodifiableMap(result);
  }

  /** Converts a primitive vector to an immutable JSON-friendly number list. */
  private static List<Double> doubleList(double[] values) {
    List<Double> result = new ArrayList<Double>();
    for (double value : values) {
      if (!Double.isFinite(value)) {
        throw new IllegalStateException("Qualified relief evidence contains a non-finite action value");
      }
      result.add(Double.valueOf(value));
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Immutable ranked cross-slice near-active and rejecting constraint summary.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class CrossSliceConstraintSummary implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double maximumNormalizedMargin;
    private final String thresholdProvenance;
    private final int observedConstraintCount;
    private final List<ConstraintSummaryEntry> rankedConstraints;

    /**
     * Creates one immutable cross-slice summary.
     *
     * @param maximumNormalizedMargin inclusive dimensionless feasible screen
     * @param thresholdProvenance engineering provenance for the screen
     * @param observedConstraintCount compatible identities observed before screening
     * @param rankedConstraints screened deterministic entries
     */
    private CrossSliceConstraintSummary(double maximumNormalizedMargin, String thresholdProvenance,
        int observedConstraintCount, List<ConstraintSummaryEntry> rankedConstraints) {
      this.maximumNormalizedMargin = maximumNormalizedMargin;
      this.thresholdProvenance = thresholdProvenance;
      this.observedConstraintCount = observedConstraintCount;
      this.rankedConstraints = Collections.unmodifiableList(new ArrayList<ConstraintSummaryEntry>(rankedConstraints));
    }

    /** @return configured maximum normalized margin for feasible near-active rows */
    public double getMaximumNormalizedMargin() {
      return maximumNormalizedMargin;
    }

    /** @return engineering source of the near-active screen */
    public String getThresholdProvenance() {
      return thresholdProvenance;
    }

    /** @return number of compatible exact constraint identities observed */
    public int getObservedConstraintCount() {
      return observedConstraintCount;
    }

    /** @return immutable deterministic ranked constraint list */
    public List<ConstraintSummaryEntry> getRankedConstraints() {
      return Collections.unmodifiableList(new ArrayList<ConstraintSummaryEntry>(rankedConstraints));
    }

    /** @return number of observed identities excluded by the feasible near-active screen */
    public int getExcludedConstraintCount() {
      return observedConstraintCount - rankedConstraints.size();
    }

    /**
     * Builds a deterministic physical-headroom screen for follow-up constraint studies.
     *
     * <p>
     * Rejecting rows are labelled as current constraint investigations and feasible rows as next-bottleneck candidates.
     * Every row retains one exact sampled or refined representative, the applicable limit and its provenance. The
     * screen does not estimate production gain or authorize a limit change; a separately qualified paired relief study
     * remains required before reporting debottleneck benefit.
     * </p>
     *
     * @return deeply immutable JSON-friendly opportunity screen
     * @throws IllegalStateException if a ranked row lacks finite physical representative evidence
     */
    public Map<String, Object> toConstraintOpportunityEvidenceMap() {
      return toConstraintOpportunityEvidenceMap(Collections.<QualifiedReliefStudyEvidence>emptyList());
    }

    /**
     * Builds the opportunity screen and attaches only exact qualified paired-relief receipts.
     *
     * @param qualifiedReliefStudies receipts produced by {@link ProcessModelOperatingEnvelopeStudy#qualifyReliefStudy}
     * @return deeply immutable JSON-friendly opportunity screen
     * @throws IllegalArgumentException if receipts are duplicated or do not match an installed opportunity row
     * @throws IllegalStateException if a ranked row lacks finite physical representative evidence
     */
    public Map<String, Object> toConstraintOpportunityEvidenceMap(
        List<QualifiedReliefStudyEvidence> qualifiedReliefStudies) {
      List<QualifiedReliefStudyEvidence> receipts = qualifiedReliefStudies == null
          ? Collections.<QualifiedReliefStudyEvidence>emptyList()
          : qualifiedReliefStudies;
      Map<String, QualifiedReliefStudyEvidence> receiptsByConstraint = new LinkedHashMap<String, QualifiedReliefStudyEvidence>();
      for (QualifiedReliefStudyEvidence receipt : receipts) {
        if (receipt == null) {
          throw new IllegalArgumentException("Qualified relief receipts must not contain null entries");
        }
        if (receiptsByConstraint.put(receipt.getQualifiedConstraintName(), receipt) != null) {
          throw new IllegalArgumentException(
              "Duplicate qualified relief receipt for " + receipt.getQualifiedConstraintName());
        }
      }
      List<Map<String, Object>> candidates = new ArrayList<Map<String, Object>>();
      for (ConstraintSummaryEntry entry : rankedConstraints) {
        LeadingConstraintEvidence representative = entry
            .getClassification() == ConstraintSummaryEntry.Classification.REJECTING
                ? entry.getRepresentativeRejectingConstraint()
                : entry.getRepresentativeFeasibleConstraint();
        if (representative == null || !Double.isFinite(representative.getCurrentValue())
            || !Double.isFinite(representative.getApplicableLimit())
            || !Double.isFinite(representative.getPhysicalMargin()) || representative.getPhysicalUnit().trim().isEmpty()
            || representative.getApplicableLimitRole().trim().isEmpty()
            || representative.getApplicableLimitProvenance().trim().isEmpty()) {
          throw new IllegalStateException(
              "Constraint opportunity evidence requires finite physical headroom and qualified limit provenance: "
                  + entry.getQualifiedConstraintName());
        }
        Map<String, Object> candidate = new LinkedHashMap<String, Object>();
        candidate.put("rank", Integer.valueOf(entry.getRank()));
        candidate.put("screening_classification",
            entry.getClassification() == ConstraintSummaryEntry.Classification.REJECTING
                ? "CURRENT_REJECTING_CONSTRAINT"
                : "NEXT_BOTTLENECK_CANDIDATE");
        candidate.put("source", entry.getSource().name());
        candidate.put("qualified_constraint_name", entry.getQualifiedConstraintName());
        candidate.put("current_value", Double.valueOf(representative.getCurrentValue()));
        candidate.put("applicable_limit", Double.valueOf(entry.getApplicableLimit()));
        candidate.put("physical_headroom", Double.valueOf(representative.getPhysicalMargin()));
        candidate.put("physical_unit", entry.getPhysicalUnit());
        candidate.put("applicable_limit_role", entry.getApplicableLimitRole());
        candidate.put("applicable_limit_provenance", entry.getApplicableLimitProvenance());
        candidate.put("slice_axes", entry.getSliceAxes());
        candidate.put("feasible_evidence_count", Integer.valueOf(entry.getFeasibleEvidenceCount()));
        candidate.put("rejecting_evidence_count", Integer.valueOf(entry.getRejectingEvidenceCount()));
        candidate.put("qualification_state", "EXACT_SAMPLED_OR_REFINED_EVIDENCE");
        QualifiedReliefStudyEvidence receipt = receiptsByConstraint.remove(entry.getQualifiedConstraintName());
        if (receipt != null) {
          if (entry.getSource() != LeadingConstraintEvidence.Source.INSTALLED_EQUIPMENT) {
            throw new IllegalArgumentException(
                "Paired installed-capacity relief receipt cannot qualify a non-installed opportunity row: "
                    + entry.getQualifiedConstraintName());
          }
          Map<String, Object> receiptMap = receipt.toEvidenceMap();
          if (!(receiptMap.get("original_applicable_limit") instanceof Number)
              || !sameDouble(entry.getApplicableLimit(),
                  ((Number) receiptMap.get("original_applicable_limit")).doubleValue())
              || !sameText(entry.getPhysicalUnit(), String.valueOf(receiptMap.get("physical_unit")))) {
            throw new IllegalArgumentException(
                "Paired relief receipt does not match the screened installed limit and unit: "
                    + entry.getQualifiedConstraintName());
          }
          candidate.put("paired_relief_study_status", "QUALIFIED");
          candidate.put("paired_relief_study_required", Boolean.FALSE);
          candidate.put("paired_relief_study_receipt", receiptMap);
        } else {
          candidate.put("paired_relief_study_status", "NOT_PROVIDED");
          candidate.put("paired_relief_study_required", Boolean.TRUE);
        }
        candidates.add(Collections.unmodifiableMap(candidate));
      }
      if (!receiptsByConstraint.isEmpty()) {
        throw new IllegalArgumentException("Qualified relief receipt does not match an exact screened opportunity row: "
            + receiptsByConstraint.keySet().iterator().next());
      }
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("summary_version", Integer.valueOf(receipts.isEmpty() ? 1 : 2));
      evidence.put("source_constraint_summary_version", Integer.valueOf(2));
      evidence.put("candidate_count", Integer.valueOf(candidates.size()));
      evidence.put("candidates", Collections.unmodifiableList(candidates));
      evidence.put("advisory", Boolean.TRUE);
      evidence.put("interpretation",
          "Exact constraint follow-up screen; not causal attribution, a shadow price, predicted relief benefit, "
              + "permission to change a limit, or operating approval");
      return Collections.unmodifiableMap(evidence);
    }

    /** Converts this summary to a deeply immutable JSON-friendly map. */
    public Map<String, Object> toEvidenceMap() {
      List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
      for (ConstraintSummaryEntry entry : rankedConstraints) {
        entries.add(entry.toEvidenceMap());
      }
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("summary_version", Integer.valueOf(2));
      evidence.put("maximum_normalized_margin", Double.valueOf(maximumNormalizedMargin));
      evidence.put("threshold_provenance", thresholdProvenance);
      evidence.put("observed_constraint_count", Integer.valueOf(observedConstraintCount));
      evidence.put("ranked_constraint_count", Integer.valueOf(rankedConstraints.size()));
      evidence.put("excluded_constraint_count", Integer.valueOf(getExcludedConstraintCount()));
      evidence.put("ranked_constraints", Collections.unmodifiableList(entries));
      evidence.put("advisory", Boolean.TRUE);
      evidence.put("interpretation",
          "Complete sampled/refined constraint ladder; not a shadow price, causal attribution, "
              + "global active set, or approval");
      return Collections.unmodifiableMap(evidence);
    }
  }

  /**
   * Immutable ranked aggregate for one exact constraint identity and engineering basis.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class ConstraintSummaryEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Ranked screening classification. */
    public enum Classification {
      /** At least one exact sampled or refined row rejected its candidate. */
      REJECTING,
      /** Feasible exact evidence fell inside the configured normalized-margin screen. */
      NEAR_ACTIVE
    }

    private final int rank;
    private final Classification classification;
    private final LeadingConstraintEvidence.Source source;
    private final String qualifiedConstraintName;
    private final double applicableLimit;
    private final String physicalUnit;
    private final String applicableLimitRole;
    private final String applicableLimitProvenance;
    private final List<List<String>> sliceAxes;
    private final int sampledPointCount;
    private final int feasibleBoundaryCount;
    private final int infeasibleBoundaryCount;
    private final int feasibleEvidenceCount;
    private final int rejectingEvidenceCount;
    private final double minimumNormalizedMargin;
    private final LeadingConstraintEvidence representativeFeasibleConstraint;
    private final LeadingConstraintEvidence representativeRejectingConstraint;

    /**
     * Creates one immutable ranked aggregate.
     *
     * @param rank one-based deterministic rank
     * @param classification rejecting or feasible near-active classification
     * @param source evidence layer
     * @param qualifiedConstraintName stable constraint identity
     * @param applicableLimit exact physical limit
     * @param physicalUnit physical engineering unit
     * @param applicableLimitRole engineering role of the limit
     * @param applicableLimitProvenance provenance of the limit
     * @param sliceAxes ordered axes where this identity was observed
     * @param sampledPointCount sampled-point occurrence count
     * @param feasibleBoundaryCount final feasible-endpoint occurrence count
     * @param infeasibleBoundaryCount final infeasible-endpoint occurrence count
     * @param feasibleEvidenceCount exact feasible-row count
     * @param rejectingEvidenceCount exact rejecting-row count
     * @param minimumNormalizedMargin minimum finite feasible normalized margin
     * @param representativeFeasibleConstraint representative exact feasible row
     * @param representativeRejectingConstraint representative exact rejecting row
     */
    private ConstraintSummaryEntry(int rank, Classification classification, LeadingConstraintEvidence.Source source,
        String qualifiedConstraintName, double applicableLimit, String physicalUnit, String applicableLimitRole,
        String applicableLimitProvenance, List<List<String>> sliceAxes, int sampledPointCount,
        int feasibleBoundaryCount, int infeasibleBoundaryCount, int feasibleEvidenceCount, int rejectingEvidenceCount,
        double minimumNormalizedMargin, LeadingConstraintEvidence representativeFeasibleConstraint,
        LeadingConstraintEvidence representativeRejectingConstraint) {
      this.rank = rank;
      this.classification = classification;
      this.source = source;
      this.qualifiedConstraintName = qualifiedConstraintName;
      this.applicableLimit = applicableLimit;
      this.physicalUnit = physicalUnit;
      this.applicableLimitRole = applicableLimitRole;
      this.applicableLimitProvenance = applicableLimitProvenance;
      this.sliceAxes = immutableAxes(sliceAxes);
      this.sampledPointCount = sampledPointCount;
      this.feasibleBoundaryCount = feasibleBoundaryCount;
      this.infeasibleBoundaryCount = infeasibleBoundaryCount;
      this.feasibleEvidenceCount = feasibleEvidenceCount;
      this.rejectingEvidenceCount = rejectingEvidenceCount;
      this.minimumNormalizedMargin = minimumNormalizedMargin;
      this.representativeFeasibleConstraint = representativeFeasibleConstraint;
      this.representativeRejectingConstraint = representativeRejectingConstraint;
    }

    /** @return one-based deterministic rank */
    public int getRank() {
      return rank;
    }

    /** @return rejecting or feasible near-active classification */
    public Classification getClassification() {
      return classification;
    }

    /** @return evidence layer */
    public LeadingConstraintEvidence.Source getSource() {
      return source;
    }

    /** @return stable or area-qualified constraint identity */
    public String getQualifiedConstraintName() {
      return qualifiedConstraintName;
    }

    /** @return exact applicable limit retained by this compatible evidence identity */
    public double getApplicableLimit() {
      return applicableLimit;
    }

    /** @return physical unit shared by every row in this aggregate */
    public String getPhysicalUnit() {
      return physicalUnit;
    }

    /** @return engineering role of the applicable limit */
    public String getApplicableLimitRole() {
      return applicableLimitRole;
    }

    /** @return provenance of the applicable limit */
    public String getApplicableLimitProvenance() {
      return applicableLimitProvenance;
    }

    /** @return immutable ordered axes on which this identity was observed */
    public List<List<String>> getSliceAxes() {
      return sliceAxes;
    }

    /** @return number of sampled-point occurrences */
    public int getSampledPointCount() {
      return sampledPointCount;
    }

    /** @return number of final feasible-boundary occurrences */
    public int getFeasibleBoundaryCount() {
      return feasibleBoundaryCount;
    }

    /** @return number of final infeasible-boundary occurrences */
    public int getInfeasibleBoundaryCount() {
      return infeasibleBoundaryCount;
    }

    /** @return number of exact feasible rows */
    public int getFeasibleEvidenceCount() {
      return feasibleEvidenceCount;
    }

    /** @return number of exact rejecting rows */
    public int getRejectingEvidenceCount() {
      return rejectingEvidenceCount;
    }

    /** @return minimum finite normalized feasible margin, or NaN */
    public double getMinimumNormalizedMargin() {
      return minimumNormalizedMargin;
    }

    /** @return smallest-normalized-margin feasible row, or null */
    public LeadingConstraintEvidence getRepresentativeFeasibleConstraint() {
      return representativeFeasibleConstraint;
    }

    /** @return first exact rejecting row in deterministic slice traversal, or null */
    public LeadingConstraintEvidence getRepresentativeRejectingConstraint() {
      return representativeRejectingConstraint;
    }

    /** Converts this ranked aggregate to a deeply immutable JSON-friendly map. */
    public Map<String, Object> toEvidenceMap() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("rank", Integer.valueOf(rank));
      evidence.put("classification", classification.name());
      evidence.put("source", source.name());
      evidence.put("qualified_constraint_name", qualifiedConstraintName);
      evidence.put("applicable_limit", finiteOrNull(applicableLimit));
      evidence.put("physical_unit", physicalUnit);
      evidence.put("applicable_limit_role", applicableLimitRole);
      evidence.put("applicable_limit_provenance", applicableLimitProvenance);
      evidence.put("slice_count", Integer.valueOf(sliceAxes.size()));
      evidence.put("slice_axes", sliceAxes);
      evidence.put("sampled_point_count", Integer.valueOf(sampledPointCount));
      evidence.put("feasible_boundary_count", Integer.valueOf(feasibleBoundaryCount));
      evidence.put("infeasible_boundary_count", Integer.valueOf(infeasibleBoundaryCount));
      evidence.put("feasible_evidence_count", Integer.valueOf(feasibleEvidenceCount));
      evidence.put("rejecting_evidence_count", Integer.valueOf(rejectingEvidenceCount));
      evidence.put("minimum_normalized_margin", finiteOrNull(minimumNormalizedMargin));
      evidence.put("representative_feasible_constraint",
          representativeFeasibleConstraint == null ? null : representativeFeasibleConstraint.toEvidenceMap());
      evidence.put("representative_rejecting_constraint",
          representativeRejectingConstraint == null ? null : representativeRejectingConstraint.toEvidenceMap());
      return Collections.unmodifiableMap(evidence);
    }

    /**
     * Deep-copies axis signatures into immutable lists.
     *
     * @param axes ordered mutable axis signatures
     * @return deeply immutable axis signatures
     */
    private static List<List<String>> immutableAxes(List<List<String>> axes) {
      List<List<String>> copy = new ArrayList<List<String>>();
      for (List<String> axis : axes) {
        copy.add(Collections.unmodifiableList(new ArrayList<String>(axis)));
      }
      return Collections.unmodifiableList(copy);
    }
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
      String sourceReference = evidence.getApplicableLimitSourceReference();
      if (sourceReference == null || sourceReference.trim().isEmpty()) {
        sourceReference = evidence.getApplicableLimitSource().name() + " constraint source (no reference declared)";
      }
      return new LeadingConstraintEvidence(Source.INSTALLED_EQUIPMENT, evidence.getQualifiedConstraintName(),
          evidence.getNormalizedUtilization(), evidence.getNormalizedMargin(), evidence.getCurrentValue(),
          evidence.getApplicableLimit(), evidence.getPhysicalMargin(), evidence.getPhysicalUnit(),
          evidence.isFeasible(), evidence.getEvidenceStatus().name(), evidence.getApplicableLimitRole().name(),
          sourceReference);
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
          "DECLARATION_ORDER_HARD_CONSTRAINT", "REGISTERED_MODEL_CONSTRAINT",
          "registered evaluator constraint definition");
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
    private final List<LeadingConstraintEvidence> constraintEvidence;

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
     * @param constraintEvidence complete normalized feasible or exact rejecting constraint ladder
     */
    private Point(int sequenceIndex, int[] gridIndexes, double[] axisValues, double[] candidateValues,
        CandidateSetEvaluationResult evaluation, LeadingConstraintEvidence leadingConstraint,
        InstalledEquipmentCapacityEvidence leadingInstalledConstraint,
        List<LeadingConstraintEvidence> constraintEvidence) {
      this.sequenceIndex = sequenceIndex;
      this.gridIndexes = Arrays.copyOf(gridIndexes, gridIndexes.length);
      this.axisValues = Arrays.copyOf(axisValues, axisValues.length);
      this.candidateValues = Arrays.copyOf(candidateValues, candidateValues.length);
      this.evaluation = evaluation;
      this.leadingConstraint = leadingConstraint;
      this.leadingInstalledConstraint = leadingInstalledConstraint;
      this.constraintEvidence = Collections
          .unmodifiableList(new ArrayList<LeadingConstraintEvidence>(constraintEvidence));
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

    /** @return complete immutable normalized feasible or exact rejecting constraint ladder */
    public List<LeadingConstraintEvidence> getConstraintEvidence() {
      return Collections.unmodifiableList(new ArrayList<LeadingConstraintEvidence>(constraintEvidence));
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
      List<Map<String, Object>> constraintRows = new ArrayList<Map<String, Object>>();
      for (LeadingConstraintEvidence constraint : constraintEvidence) {
        constraintRows.add(constraint.toEvidenceMap());
      }
      evidence.put("constraint_evidence", Collections.unmodifiableList(constraintRows));
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
    private final List<LeadingConstraintEvidence> feasibleBoundaryConstraintEvidence;
    private final List<LeadingConstraintEvidence> infeasibleBoundaryConstraintEvidence;
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
     * @param feasibleBoundaryConstraintEvidence complete normalized evidence at the final feasible endpoint
     * @param infeasibleBoundaryConstraintEvidence complete exact rejecting evidence at the final infeasible endpoint
     * @param status terminal refinement status
     * @param steps complete midpoint evidence
     */
    private OperatingRegionBoundaryRefinement(OperatingRegionTransition transition, double actionLowerBound,
        double actionUpperBound, double relativeTolerance, double absoluteTolerance, String toleranceProvenance,
        int maximumEvaluations, double feasibleAxisValue, double infeasibleAxisValue,
        LeadingConstraintEvidence feasibleBoundaryConstraint, LeadingConstraintEvidence infeasibleBoundaryConstraint,
        List<LeadingConstraintEvidence> feasibleBoundaryConstraintEvidence,
        List<LeadingConstraintEvidence> infeasibleBoundaryConstraintEvidence, Status status, List<Step> steps) {
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
      this.feasibleBoundaryConstraintEvidence = Collections
          .unmodifiableList(new ArrayList<LeadingConstraintEvidence>(feasibleBoundaryConstraintEvidence));
      this.infeasibleBoundaryConstraintEvidence = Collections
          .unmodifiableList(new ArrayList<LeadingConstraintEvidence>(infeasibleBoundaryConstraintEvidence));
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

    /** @return complete immutable normalized evidence at the final feasible endpoint */
    public List<LeadingConstraintEvidence> getFeasibleBoundaryConstraintEvidence() {
      return Collections.unmodifiableList(new ArrayList<LeadingConstraintEvidence>(feasibleBoundaryConstraintEvidence));
    }

    /** @return complete immutable exact rejecting evidence at the final infeasible endpoint */
    public List<LeadingConstraintEvidence> getInfeasibleBoundaryConstraintEvidence() {
      return Collections
          .unmodifiableList(new ArrayList<LeadingConstraintEvidence>(infeasibleBoundaryConstraintEvidence));
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
      evidence.put("feasible_boundary_constraint_evidence", constraintEvidenceMaps(feasibleBoundaryConstraintEvidence));
      evidence.put("infeasible_boundary_constraint_evidence",
          constraintEvidenceMaps(infeasibleBoundaryConstraintEvidence));
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
      private final List<LeadingConstraintEvidence> constraintEvidence;

      /** Creates one immutable midpoint evaluation from a complete evaluated point. */
      private Step(int sequenceIndex, double axisValue, double[] candidateValues,
          ProcessModelOperatingActionSetEvaluator.Outcome outcome, boolean feasible,
          boolean candidateSimulationConverged, boolean candidateEvaluatorFeasible, boolean baselineRestored,
          boolean baselineSimulationConverged, LeadingConstraintEvidence leadingConstraint,
          List<LeadingConstraintEvidence> constraintEvidence) {
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
        this.constraintEvidence = Collections
            .unmodifiableList(new ArrayList<LeadingConstraintEvidence>(constraintEvidence));
      }

      /** Creates one step from a complete runtime point. */
      private static Step fromPoint(int sequenceIndex, Point point) {
        CandidateSetEvaluationResult evaluation = point.getEvaluation();
        return new Step(sequenceIndex, point.getAxisValues()[0], point.getCandidateValues(), evaluation.getOutcome(),
            evaluation.isFeasible(), evaluation.isCandidateSimulationConverged(),
            evaluation.isCandidateEvaluatorFeasible(), evaluation.isBaselineRestored(),
            evaluation.isBaselineSimulationConverged(), point.getLeadingConstraint(), point.getConstraintEvidence());
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

      /** @return complete immutable normalized feasible or exact rejecting constraint ladder */
      public List<LeadingConstraintEvidence> getConstraintEvidence() {
        return Collections.unmodifiableList(new ArrayList<LeadingConstraintEvidence>(constraintEvidence));
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
        evidence.put("constraint_evidence", constraintEvidenceMaps(constraintEvidence));
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
