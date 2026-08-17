package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.util.optimizer.ProcessModelAllocationOptimizer.AllocationSearchResult;
import neqsim.process.util.optimizer.ProcessModelAllocationOptimizer.CandidateRecord;
import neqsim.process.util.optimizer.ProcessModelAllocationOptimizer.ObjectiveSnapshot;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicConstraintSnapshot;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateObjectiveEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.BottleneckStatus;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;

/**
 * Derives trace-qualified bottleneck-relief opportunities from a completed fixed-total allocation search.
 *
 * <p>
 * The analyzer is read-only: it consumes immutable {@link AllocationSearchResult} evidence and performs no simulations,
 * action writes, or process-model mutation. It retains only candidates whose selected objective improves on the best
 * feasible sampled allocation by more than the search's declared objective tolerance and whose complete baseline
 * restoration was verified. A general constraint reports the non-negative magnitude of its sampled negative margin. An
 * installed-capacity constraint uses the immutable current and applicable-limit snapshot that also supplied normalized
 * candidate feasibility. Relief therefore remains available for every discovered installed constraint, not only the
 * subset selected as required hydraulic bindings; it is unavailable when that complete evidence is invalid.
 * </p>
 *
 * <p>
 * A reported opportunity is an association in the finite sampled trace. It is not proof of causation, global
 * optimality, a shadow price, production loss, economic value, or engineering approval. Relief values with different
 * units are never aggregated or compared.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelAllocationBottleneckAnalyzer {
  /** Stable analyzer identifier. */
  private final String id;

  /** Human-readable analyzer name. */
  private final String name;

  /** Engineering provenance for this analysis configuration. */
  private final String provenance;

  /**
   * Creates a trace-only bottleneck analyzer.
   *
   * @param id stable analyzer identifier
   * @param name human-readable analyzer name
   * @param provenance engineering source for this analysis configuration
   */
  public ProcessModelAllocationBottleneckAnalyzer(String id, String name, String provenance) {
    this.id = requireText(id, "Allocation bottleneck analyzer identifier");
    this.name = requireText(name, "Allocation bottleneck analyzer name");
    this.provenance = requireText(provenance, "Allocation bottleneck analyzer provenance");
  }

  /** @return stable analyzer identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable analyzer name */
  public String getName() {
    return name;
  }

  /** @return engineering provenance for this analysis configuration */
  public String getProvenance() {
    return provenance;
  }

  /**
   * Analyzes a completed search without evaluating or mutating its process model.
   *
   * @param search immutable completed allocation search
   * @return immutable serializable trace analysis
   * @throws IllegalArgumentException when search is null
   */
  public BottleneckAnalysisResult analyze(AllocationSearchResult search) {
    if (search == null) {
      throw new IllegalArgumentException("Allocation search result must not be null");
    }

    List<String> diagnostics = new ArrayList<String>();
    diagnostics.add("Results are sampled associations, not causal attribution, global optimality, shadow prices, "
        + "production-loss estimates, economic values, or engineering approval");
    CandidateRecord baseline = search.getBestFeasibleCandidate();
    AnalysisOutcome outcome;
    List<BottleneckReliefOpportunity> opportunities = new ArrayList<BottleneckReliefOpportunity>();

    if (!search.isModelRecovered()) {
      outcome = AnalysisOutcome.MODEL_RECOVERY_FAILED;
      diagnostics.add("The allocation search did not verify complete model recovery; no opportunity was reported");
    } else if (!isUsableBaseline(baseline)) {
      outcome = AnalysisOutcome.NO_FEASIBLE_BASELINE;
      diagnostics.add("No finite, fully recovered feasible sampled allocation is available as the comparison baseline");
    } else {
      for (CandidateRecord candidate : search.getCandidates()) {
        BottleneckReliefOpportunity opportunity = createOpportunity(search, baseline, candidate, diagnostics);
        if (opportunity != null) {
          opportunities.add(opportunity);
        }
      }
      sortOpportunities(opportunities);
      outcome = opportunities.isEmpty() ? AnalysisOutcome.NO_IMPROVING_CONSTRAINED_CANDIDATE
          : AnalysisOutcome.OPPORTUNITIES_IDENTIFIED;
      if (opportunities.isEmpty()) {
        diagnostics.add("No fully recovered candidate combined a direction-aware objective improvement above tolerance "
            + "with an exact finite hard-constraint violation");
      }
    }

    return new BottleneckAnalysisResult(id, name, provenance, search, outcome, baseline, opportunities, diagnostics);
  }

  /** Creates one opportunity or returns null when the candidate does not meet the evidence contract. */
  private static BottleneckReliefOpportunity createOpportunity(AllocationSearchResult search, CandidateRecord baseline,
      CandidateRecord candidate, List<String> diagnostics) {
    if (candidate == null || candidate.getSequenceIndex() == baseline.getSequenceIndex()) {
      return null;
    }
    CandidateSetEvaluationResult evaluation = candidate.getEvaluation();
    if (evaluation == null || !evaluation.isCandidateSimulationConverged() || !evaluation.isBaselineRestored()
        || !evaluation.isBaselineSimulationConverged() || !isFinite(candidate.getRawObjective())) {
      return null;
    }

    double objectiveGain = objectiveGain(search.getObjective(), baseline.getRawObjective(),
        candidate.getRawObjective());
    if (!(objectiveGain > search.getObjectiveImprovementTolerance())) {
      return null;
    }

    List<HydraulicConstraintSnapshot> hydraulicEvidence = evaluation.getHydraulicConstraints();
    List<InstalledEquipmentCapacityEvidence> installedCapacityEvidence = evaluation
        .getInstalledEquipmentCapacityEvidence();
    Map<String, InstalledEquipmentCapacityEvidence> installedCapacityByAddress = indexInstalledCapacityEvidence(
        installedCapacityEvidence);
    List<ConstraintReliefEvidence> violations = new ArrayList<ConstraintReliefEvidence>();
    for (CandidateConstraintEvidence constraint : evaluation.getConstraintEvidence()) {
      if (constraint.isHard() && isFinite(constraint.getMargin()) && constraint.getMargin() < 0.0) {
        violations.add(createConstraintRelief(constraint, installedCapacityByAddress));
      }
    }
    if (violations.isEmpty()) {
      return null;
    }

    EvidenceClass evidenceClass = classifyEvidence(violations, hydraulicEvidence);
    if (evidenceClass == EvidenceClass.EVIDENCE_LIMITED) {
      diagnostics.add("Candidate " + candidate.getSequenceIndex()
          + " has incomplete, non-finite, or out-of-validity required hydraulic evidence");
    }
    double[] baselineValues = baseline.getCandidateValues();
    double[] candidateValues = candidate.getCandidateValues();
    double[] actionDeltas = new double[candidateValues.length];
    for (int index = 0; index < candidateValues.length; index++) {
      actionDeltas[index] = candidateValues[index] - baselineValues[index];
    }

    CandidateObjectiveEvidence selectedEvidence = findObjectiveEvidence(evaluation, search.getObjective().getIndex());
    return new BottleneckReliefOpportunity(candidate.getSequenceIndex(), candidateValues, actionDeltas,
        candidate.isAcceptedAsIncumbent(), candidate.getRawObjective(), objectiveGain, search.getObjective(),
        selectedEvidence, evidenceClass, violations, hydraulicEvidence, installedCapacityEvidence);
  }

  /** Uses the same immutable installed-capacity row that supplied normalized candidate feasibility. */
  private static ConstraintReliefEvidence createConstraintRelief(CandidateConstraintEvidence constraint,
      Map<String, InstalledEquipmentCapacityEvidence> installedCapacityByAddress) {
    if (!constraint.isCapacityConstraint()) {
      return new ConstraintReliefEvidence(constraint, -constraint.getMargin(), constraint.getUnit(), false, null);
    }
    InstalledEquipmentCapacityEvidence matching = installedCapacityByAddress.get(capacityAddress(constraint));
    if (matching == null || !matching.hasFiniteEvidence() || !isFinite(matching.getRequiredRelief())) {
      return new ConstraintReliefEvidence(constraint, Double.NaN,
          matching == null ? constraint.getPhysicalUnit() : matching.getPhysicalUnit(), false, matching);
    }
    return new ConstraintReliefEvidence(constraint, matching.getRequiredRelief(), matching.getPhysicalUnit(), true,
        matching);
  }

  /** Indexes complete candidate capacity evidence without joining to the selected hydraulic-binding subset. */
  private static Map<String, InstalledEquipmentCapacityEvidence> indexInstalledCapacityEvidence(
      List<InstalledEquipmentCapacityEvidence> evidence) {
    Map<String, InstalledEquipmentCapacityEvidence> indexed = new HashMap<String, InstalledEquipmentCapacityEvidence>();
    for (InstalledEquipmentCapacityEvidence snapshot : evidence) {
      indexed.put(snapshot.getQualifiedConstraintName(), snapshot);
    }
    return indexed;
  }

  /** Returns the area-qualified address used by required hydraulic bindings. */
  private static String capacityAddress(CandidateConstraintEvidence constraint) {
    if (constraint.getAreaName() == null || constraint.getEquipmentName() == null
        || constraint.getEquipmentConstraintName() == null) {
      return "";
    }
    return constraint.getAreaName() + "::" + constraint.getEquipmentName() + "/"
        + constraint.getEquipmentConstraintName();
  }

  /** Returns selected objective evidence, or null for an older/incomplete serialized trace. */
  private static CandidateObjectiveEvidence findObjectiveEvidence(CandidateSetEvaluationResult evaluation, int index) {
    for (CandidateObjectiveEvidence evidence : evaluation.getObjectiveEvidence()) {
      if (evidence.getIndex() == index) {
        return evidence;
      }
    }
    return null;
  }

  /** Classifies coupled hard violations and the completeness of stored capacity evidence. */
  private static EvidenceClass classifyEvidence(List<ConstraintReliefEvidence> violations,
      List<HydraulicConstraintSnapshot> hydraulicEvidence) {
    for (ConstraintReliefEvidence violation : violations) {
      InstalledEquipmentCapacityEvidence capacityEvidence = violation.getInstalledCapacityEvidence();
      if (violation.getConstraint().isCapacityConstraint()
          && (!violation.isDerivedFromInstalledCapacityEvidence() || capacityEvidence == null || capacityEvidence
              .getEvidenceApplicability() == InstalledEquipmentCapacityEvidence.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE)) {
        return EvidenceClass.EVIDENCE_LIMITED;
      }
    }
    if (hydraulicEvidence.isEmpty()) {
      return EvidenceClass.EVIDENCE_LIMITED;
    }
    for (HydraulicConstraintSnapshot snapshot : hydraulicEvidence) {
      if (!snapshot.isPresent() || !snapshot.hasFiniteValue()
          || snapshot.getEvidenceApplicability() == BottleneckStatus.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE) {
        return EvidenceClass.EVIDENCE_LIMITED;
      }
    }
    return violations.size() == 1 ? EvidenceClass.ISOLATED : EvidenceClass.COUPLED;
  }

  /** Sorts by direction-aware gain, evidence class, sequence, then exact constraint identity. */
  private static void sortOpportunities(List<BottleneckReliefOpportunity> opportunities) {
    Collections.sort(opportunities, new Comparator<BottleneckReliefOpportunity>() {
      @Override
      public int compare(BottleneckReliefOpportunity first, BottleneckReliefOpportunity second) {
        int gainOrder = Double.compare(second.getObjectiveGain(), first.getObjectiveGain());
        if (gainOrder != 0) {
          return gainOrder;
        }
        int evidenceOrder = Integer.compare(first.getEvidenceClass().ordinal(), second.getEvidenceClass().ordinal());
        if (evidenceOrder != 0) {
          return evidenceOrder;
        }
        int sequenceOrder = Integer.compare(first.getCandidateSequenceIndex(), second.getCandidateSequenceIndex());
        if (sequenceOrder != 0) {
          return sequenceOrder;
        }
        return first.getConstraintRelief().get(0).getConstraint().getName()
            .compareTo(second.getConstraintRelief().get(0).getConstraint().getName());
      }
    });
  }

  /** Returns the direction-aware positive raw-objective improvement. */
  private static double objectiveGain(ObjectiveSnapshot objective, double baseline, double candidate) {
    return objective.getDirection() == ObjectiveDefinition.Direction.MAXIMIZE ? candidate - baseline
        : baseline - candidate;
  }

  /** Returns true for a completely usable best-feasible trace record. */
  private static boolean isUsableBaseline(CandidateRecord baseline) {
    return baseline != null && baseline.getEvaluation() != null && baseline.getEvaluation().isFeasible()
        && baseline.getEvaluation().isBaselineRestored() && baseline.getEvaluation().isBaselineSimulationConverged()
        && isFinite(baseline.getRawObjective());
  }

  /** Validates and trims required text. */
  private static String requireText(String value, String description) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(description + " must not be blank");
    }
    return value.trim();
  }

  /** Returns true for a finite scalar on Java 8. */
  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  /** Terminal analysis classification. */
  public enum AnalysisOutcome {
    /** One or more trace-qualified opportunities were identified. */
    OPPORTUNITIES_IDENTIFIED,
    /** The search did not contain a fully evidenced feasible comparison point. */
    NO_FEASIBLE_BASELINE,
    /** The search stopped without verified complete model recovery. */
    MODEL_RECOVERY_FAILED,
    /** No sampled point satisfied the objective-improvement and hard-violation evidence filters. */
    NO_IMPROVING_CONSTRAINED_CANDIDATE
  }

  /** Strength of attribution supported by one sampled candidate. */
  public enum EvidenceClass {
    /** Exactly one hard violation with complete applicable required hydraulic evidence. */
    ISOLATED,
    /** More than one hard violation with complete applicable required hydraulic evidence. */
    COUPLED,
    /** Required hydraulic evidence is missing, non-finite, or explicitly outside its validity range. */
    EVIDENCE_LIMITED
  }

  /** Immutable required relief for one exact hard constraint at one sampled candidate. */
  public static final class ConstraintReliefEvidence implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Exact violated constraint evidence. */
    private final CandidateConstraintEvidence constraint;
    /** Required relief to reach zero sampled margin. */
    private final double requiredMarginRelief;
    /** Engineering unit for the relief. */
    private final String unit;
    /** Whether the evaluator's complete installed-capacity snapshot supplied the relief. */
    private final boolean derivedFromInstalledCapacityEvidence;
    /** Exact installed-capacity row, or null for a general or unavailable constraint. */
    private final InstalledEquipmentCapacityEvidence installedCapacityEvidence;

    /** Creates exact in-unit margin-relief evidence. */
    private ConstraintReliefEvidence(CandidateConstraintEvidence constraint, double requiredMarginRelief, String unit,
        boolean derivedFromInstalledCapacityEvidence, InstalledEquipmentCapacityEvidence installedCapacityEvidence) {
      this.constraint = constraint;
      this.requiredMarginRelief = requiredMarginRelief;
      this.unit = unit;
      this.derivedFromInstalledCapacityEvidence = derivedFromInstalledCapacityEvidence;
      this.installedCapacityEvidence = installedCapacityEvidence;
    }

    /** @return immutable exact constraint identity, definition, value, and margin */
    public CandidateConstraintEvidence getConstraint() {
      return constraint;
    }

    /** @return non-negative relief required to reach zero margin, in the constraint's own unit */
    public double getRequiredMarginRelief() {
      return requiredMarginRelief;
    }

    /** @return relief unit inherited from the exact constraint */
    public String getUnit() {
      return unit;
    }

    /** @return true when relief came from the evaluator's complete installed-capacity snapshot */
    public boolean isDerivedFromInstalledCapacityEvidence() {
      return derivedFromInstalledCapacityEvidence;
    }

    /**
     * Compatibility alias for earlier hydraulic-subset evidence.
     *
     * @return true when exact installed-capacity evidence supplied the relief
     * @deprecated use {@link #isDerivedFromInstalledCapacityEvidence()}
     */
    @Deprecated
    public boolean isDerivedFromHydraulicEvidence() {
      return derivedFromInstalledCapacityEvidence;
    }

    /** @return exact installed-capacity evidence, or null for a general or unavailable constraint */
    public InstalledEquipmentCapacityEvidence getInstalledCapacityEvidence() {
      return installedCapacityEvidence;
    }
  }

  /** Immutable sampled opportunity linking objective gain, action deltas, and exact violated constraints. */
  public static final class BottleneckReliefOpportunity implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Source trace sequence index. */
    private final int candidateSequenceIndex;
    /** Sampled action values. */
    private final double[] candidateValues;
    /** Candidate-minus-best-feasible action deltas. */
    private final double[] actionDeltasFromBestFeasible;
    /** Whether the source search accepted the point as a feasible incumbent. */
    private final boolean acceptedAsIncumbent;
    /** Selected raw objective value. */
    private final double rawObjective;
    /** Direction-aware improvement over the best feasible sample. */
    private final double objectiveGain;
    /** Frozen selected-objective identity. */
    private final ObjectiveSnapshot objective;
    /** Candidate-local selected-objective evidence. */
    private final CandidateObjectiveEvidence objectiveEvidence;
    /** Strength of sampled attribution evidence. */
    private final EvidenceClass evidenceClass;
    /** Exact violated hard constraints and required relief. */
    private final List<ConstraintReliefEvidence> constraintRelief;
    /** Required hydraulic evidence retained from the candidate. */
    private final List<HydraulicConstraintSnapshot> hydraulicEvidence;
    /** Complete installed-capacity evidence retained from the candidate. */
    private final List<InstalledEquipmentCapacityEvidence> installedCapacityEvidence;

    /** Creates one immutable opportunity. */
    private BottleneckReliefOpportunity(int candidateSequenceIndex, double[] candidateValues,
        double[] actionDeltasFromBestFeasible, boolean acceptedAsIncumbent, double rawObjective, double objectiveGain,
        ObjectiveSnapshot objective, CandidateObjectiveEvidence objectiveEvidence, EvidenceClass evidenceClass,
        List<ConstraintReliefEvidence> constraintRelief, List<HydraulicConstraintSnapshot> hydraulicEvidence,
        List<InstalledEquipmentCapacityEvidence> installedCapacityEvidence) {
      this.candidateSequenceIndex = candidateSequenceIndex;
      this.candidateValues = candidateValues.clone();
      this.actionDeltasFromBestFeasible = actionDeltasFromBestFeasible.clone();
      this.acceptedAsIncumbent = acceptedAsIncumbent;
      this.rawObjective = rawObjective;
      this.objectiveGain = objectiveGain;
      this.objective = objective;
      this.objectiveEvidence = objectiveEvidence;
      this.evidenceClass = evidenceClass;
      this.constraintRelief = Collections.unmodifiableList(new ArrayList<ConstraintReliefEvidence>(constraintRelief));
      this.hydraulicEvidence = Collections
          .unmodifiableList(new ArrayList<HydraulicConstraintSnapshot>(hydraulicEvidence));
      this.installedCapacityEvidence = Collections
          .unmodifiableList(new ArrayList<InstalledEquipmentCapacityEvidence>(installedCapacityEvidence));
    }

    /** @return source trace sequence index */
    public int getCandidateSequenceIndex() {
      return candidateSequenceIndex;
    }

    /** @return defensive sampled allocation vector */
    public double[] getCandidateValues() {
      return candidateValues.clone();
    }

    /** @return defensive candidate-minus-best-feasible action deltas in action order */
    public double[] getActionDeltasFromBestFeasible() {
      return actionDeltasFromBestFeasible.clone();
    }

    /** @return whether the source search accepted this point as a feasible incumbent */
    public boolean isAcceptedAsIncumbent() {
      return acceptedAsIncumbent;
    }

    /** @return selected raw objective at this candidate */
    public double getRawObjective() {
      return rawObjective;
    }

    /** @return direction-aware improvement over the best feasible sample in the objective unit */
    public double getObjectiveGain() {
      return objectiveGain;
    }

    /** @return immutable selected-objective identity from the search */
    public ObjectiveSnapshot getObjective() {
      return objective;
    }

    /** @return candidate-local selected-objective evidence, or null for an incomplete older trace */
    public CandidateObjectiveEvidence getObjectiveEvidence() {
      return objectiveEvidence;
    }

    /** @return isolated, coupled, or evidence-limited trace classification */
    public EvidenceClass getEvidenceClass() {
      return evidenceClass;
    }

    /** @return fresh immutable exact hard-constraint relief evidence */
    public List<ConstraintReliefEvidence> getConstraintRelief() {
      return Collections.unmodifiableList(new ArrayList<ConstraintReliefEvidence>(constraintRelief));
    }

    /** @return fresh immutable required hydraulic evidence retained from the candidate */
    public List<HydraulicConstraintSnapshot> getHydraulicEvidence() {
      return Collections.unmodifiableList(new ArrayList<HydraulicConstraintSnapshot>(hydraulicEvidence));
    }

    /** @return fresh immutable complete installed-capacity evidence retained from the candidate */
    public List<InstalledEquipmentCapacityEvidence> getInstalledCapacityEvidence() {
      return Collections.unmodifiableList(new ArrayList<InstalledEquipmentCapacityEvidence>(installedCapacityEvidence));
    }
  }

  /** Immutable serializable analysis result suitable for Java and JPype/Python inspection. */
  public static final class BottleneckAnalysisResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Stable analyzer identifier. */
    private final String id;
    /** Human-readable analyzer name. */
    private final String name;
    /** Engineering provenance for this analysis. */
    private final String provenance;
    /** Source allocation-search identifier. */
    private final String sourceSearchId;
    /** Source allocation-search name. */
    private final String sourceSearchName;
    /** Source allocation-search provenance. */
    private final String sourceSearchProvenance;
    /** Stable source action-set identifier. */
    private final String actionSetId;
    /** Source action-set name. */
    private final String actionSetName;
    /** Source action-set provenance. */
    private final String actionSetProvenance;
    /** Terminal analysis outcome. */
    private final AnalysisOutcome outcome;
    /** Frozen selected-objective identity. */
    private final ObjectiveSnapshot objective;
    /** Direction-aware objective improvement threshold. */
    private final double objectiveImprovementTolerance;
    /** Engineering provenance for the objective threshold. */
    private final String objectiveToleranceProvenance;
    /** Best feasible sampled allocation. */
    private final double[] bestFeasibleAllocation;
    /** Best feasible sampled raw objective. */
    private final double bestFeasibleRawObjective;
    /** Ranked sampled opportunities. */
    private final List<BottleneckReliefOpportunity> opportunities;
    /** Immutable analysis diagnostics. */
    private final List<String> diagnostics;

    /** Creates immutable trace-analysis evidence. */
    private BottleneckAnalysisResult(String id, String name, String provenance, AllocationSearchResult search,
        AnalysisOutcome outcome, CandidateRecord baseline, List<BottleneckReliefOpportunity> opportunities,
        List<String> diagnostics) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.sourceSearchId = search.getId();
      this.sourceSearchName = search.getName();
      this.sourceSearchProvenance = search.getProvenance();
      this.actionSetId = search.getActionSetId();
      this.actionSetName = search.getActionSetName();
      this.actionSetProvenance = search.getActionSetProvenance();
      this.outcome = outcome;
      this.objective = search.getObjective();
      this.objectiveImprovementTolerance = search.getObjectiveImprovementTolerance();
      this.objectiveToleranceProvenance = search.getObjectiveToleranceProvenance();
      this.bestFeasibleAllocation = baseline == null ? new double[0] : baseline.getCandidateValues();
      this.bestFeasibleRawObjective = baseline == null ? Double.NaN : baseline.getRawObjective();
      this.opportunities = Collections.unmodifiableList(new ArrayList<BottleneckReliefOpportunity>(opportunities));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** @return stable analyzer identifier */
    public String getId() {
      return id;
    }

    /** @return human-readable analyzer name */
    public String getName() {
      return name;
    }

    /** @return engineering provenance for this analysis */
    public String getProvenance() {
      return provenance;
    }

    /** @return source allocation-search identifier */
    public String getSourceSearchId() {
      return sourceSearchId;
    }

    /** @return source allocation-search name */
    public String getSourceSearchName() {
      return sourceSearchName;
    }

    /** @return source allocation-search provenance */
    public String getSourceSearchProvenance() {
      return sourceSearchProvenance;
    }

    /** @return stable source action-set identifier */
    public String getActionSetId() {
      return actionSetId;
    }

    /** @return source action-set name */
    public String getActionSetName() {
      return actionSetName;
    }

    /** @return source action-set provenance */
    public String getActionSetProvenance() {
      return actionSetProvenance;
    }

    /** @return terminal analysis outcome */
    public AnalysisOutcome getOutcome() {
      return outcome;
    }

    /** @return immutable selected-objective identity */
    public ObjectiveSnapshot getObjective() {
      return objective;
    }

    /** @return direction-aware objective improvement threshold */
    public double getObjectiveImprovementTolerance() {
      return objectiveImprovementTolerance;
    }

    /** @return engineering/numerical provenance of the objective threshold */
    public String getObjectiveToleranceProvenance() {
      return objectiveToleranceProvenance;
    }

    /** @return defensive best-feasible sampled allocation, or an empty array */
    public double[] getBestFeasibleAllocation() {
      return bestFeasibleAllocation.clone();
    }

    /** @return best-feasible sampled raw objective, or NaN */
    public double getBestFeasibleRawObjective() {
      return bestFeasibleRawObjective;
    }

    /** @return fresh immutable ranked opportunities */
    public List<BottleneckReliefOpportunity> getOpportunities() {
      return Collections.unmodifiableList(new ArrayList<BottleneckReliefOpportunity>(opportunities));
    }

    /** @return fresh immutable analysis diagnostics */
    public List<String> getDiagnostics() {
      return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }
  }
}
