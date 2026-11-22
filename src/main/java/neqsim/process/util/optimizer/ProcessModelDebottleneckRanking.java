package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.CapacityAlternative;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.ConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.MetricComparison;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.MetricEvidence;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.MetricKind;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.ObjectiveEvidence;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.ScenarioEvidence;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.StudyOutcome;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.StudyResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;

/**
 * Ranks independently completed paired debottleneck studies on one explicitly compatible metric.
 *
 * <p>
 * The ranking consumes immutable {@link StudyResult} objects and never reruns or mutates a process model. A candidate
 * is rankable only when its paired study completed with verified state recovery, its declared metric identity and
 * engineering basis match the ranking policy exactly, and its baseline is identical to the first qualified baseline.
 * Unlike units, bases, provenance, effective periods, search policies, parameter vectors, objective evidence, or
 * constraint evidence are never normalized or compared.
 * </p>
 *
 * <p>
 * One ranking uses one physical or screening metric. Production, power, energy, emissions, and economics remain
 * separate. Results are sampled screening evidence, not causal production loss, global optimality, a KKT multiplier,
 * design approval, certified emissions, or investment approval.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelDebottleneckRanking implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Direction in which the declared ranking-metric delta improves. */
  public enum RankingDirection {
    /** Larger alternative-minus-baseline deltas rank first. */
    MAXIMIZE,
    /** Smaller alternative-minus-baseline deltas rank first. */
    MINIMIZE
  }

  /** Qualification state of one submitted paired study. */
  public enum CandidateStatus {
    /** The study and metric are comparable and included in the ranking. */
    QUALIFIED,
    /** The paired study did not complete successfully. */
    STUDY_NOT_COMPLETED,
    /** Installed-capacity or process-state recovery was not verified. */
    RECOVERY_NOT_VERIFIED,
    /** Baseline or alternative scenario evidence is absent or unqualified. */
    SCENARIO_NOT_QUALIFIED,
    /** The declared ranking metric is absent. */
    RANKING_METRIC_MISSING,
    /** The declared ranking metric is unavailable or non-finite. */
    RANKING_METRIC_NOT_CALCULABLE,
    /** Metric identity, unit, kind, basis, provenance, or period differs from the policy. */
    METRIC_METADATA_MISMATCH,
    /** Alternative evidence confidence is absent or below the declared floor. */
    ALTERNATIVE_CONFIDENCE_TOO_LOW,
    /** Metric evidence confidence is absent or below the declared floor. */
    METRIC_CONFIDENCE_TOO_LOW,
    /** The deterministic baseline differs from the reference baseline. */
    BASELINE_INCOMPATIBLE,
    /** The declared objective index is absent from baseline or alternative evidence. */
    OBJECTIVE_MISSING,
    /** Objective identity, direction, unit, or weight differs from the policy. */
    OBJECTIVE_METADATA_MISMATCH,
    /** Baseline, alternative, or delta objective evidence is unavailable or non-finite. */
    OBJECTIVE_NOT_CALCULABLE
  }

  /** Outcome of the complete ranking operation. */
  public enum RankingOutcome {
    /** Every submitted study was qualified and ranked. */
    COMPLETED,
    /** At least one study was ranked and at least one was rejected. */
    PARTIAL,
    /** No submitted study satisfied the fail-closed comparison policy. */
    NO_QUALIFIED_ALTERNATIVE
  }

  /** Immutable single-metric comparison policy. */
  public static final class RankingPolicy implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String provenance;
    private final String metricId;
    private final String metricName;
    private final MetricKind metricKind;
    private final String unit;
    private final String basis;
    private final String metricProvenance;
    private final String effectivePeriod;
    private final RankingDirection direction;
    private final double tieTolerance;
    private final double baselineRelativeTolerance;
    private final boolean alternativeConfidenceFloorSet;
    private final double minimumAlternativeConfidence;
    private final boolean metricConfidenceFloorSet;
    private final double minimumMetricConfidence;

    /**
     * Creates a fully qualified single-metric ranking policy.
     *
     * @param id stable policy identifier
     * @param name human-readable policy name
     * @param provenance source and assumptions for the ranking policy
     * @param metricId exact registered metric identifier
     * @param metricName exact registered metric name
     * @param metricKind engineering role of the metric
     * @param unit exact engineering unit of the metric and its deltas
     * @param basis exact physical or commercial metric basis
     * @param metricProvenance exact metric source or factor provenance
     * @param effectivePeriod exact metric effective period
     * @param direction direction in which the paired delta improves
     * @param tieTolerance non-negative tie tolerance in the metric unit
     * @param baselineRelativeTolerance dimensionless relative tolerance in [0, 1] for repeated simulator values in an
     * otherwise identical baseline
     * @param minimumAlternativeConfidence confidence floor in [0, 1], or NaN when unset
     * @param minimumMetricConfidence confidence floor in [0, 1], or NaN when unset
     */
    public RankingPolicy(String id, String name, String provenance, String metricId, String metricName,
        MetricKind metricKind, String unit, String basis, String metricProvenance, String effectivePeriod,
        RankingDirection direction, double tieTolerance, double baselineRelativeTolerance,
        double minimumAlternativeConfidence, double minimumMetricConfidence) {
      this.id = requireText(id, "Ranking policy identifier");
      this.name = requireText(name, "Ranking policy name");
      this.provenance = requireText(provenance, "Ranking policy provenance");
      this.metricId = requireText(metricId, "Ranking metric identifier");
      this.metricName = requireText(metricName, "Ranking metric name");
      if (metricKind == null) {
        throw new IllegalArgumentException("Ranking metric kind is required");
      }
      this.metricKind = metricKind;
      this.unit = requireText(unit, "Ranking metric unit");
      this.basis = requireText(basis, "Ranking metric basis");
      this.metricProvenance = requireText(metricProvenance, "Ranking metric provenance");
      this.effectivePeriod = requireText(effectivePeriod, "Ranking metric effective period");
      if (direction == null) {
        throw new IllegalArgumentException("Ranking direction is required");
      }
      this.direction = direction;
      if (!isFinite(tieTolerance) || tieTolerance < 0.0) {
        throw new IllegalArgumentException("Ranking tie tolerance must be finite and non-negative");
      }
      this.tieTolerance = tieTolerance;
      if (!isFinite(baselineRelativeTolerance) || baselineRelativeTolerance < 0.0 || baselineRelativeTolerance > 1.0) {
        throw new IllegalArgumentException("Baseline relative tolerance must be finite and in [0, 1]");
      }
      this.baselineRelativeTolerance = baselineRelativeTolerance;
      alternativeConfidenceFloorSet = !Double.isNaN(minimumAlternativeConfidence);
      this.minimumAlternativeConfidence = validateConfidence(minimumAlternativeConfidence,
          "Minimum alternative confidence");
      metricConfidenceFloorSet = !Double.isNaN(minimumMetricConfidence);
      this.minimumMetricConfidence = validateConfidence(minimumMetricConfidence, "Minimum metric confidence");
    }

    /** @return stable ranking-policy identifier */
    public String getId() {
      return id;
    }

    /** @return human-readable ranking-policy name */
    public String getName() {
      return name;
    }

    /** @return policy source and assumptions */
    public String getProvenance() {
      return provenance;
    }

    /** @return exact registered ranking-metric identifier */
    public String getMetricId() {
      return metricId;
    }

    /** @return exact registered ranking-metric name */
    public String getMetricName() {
      return metricName;
    }

    /** @return engineering role of the ranking metric */
    public MetricKind getMetricKind() {
      return metricKind;
    }

    /** @return engineering unit of ranking values and deltas */
    public String getUnit() {
      return unit;
    }

    /** @return exact physical or commercial metric basis */
    public String getBasis() {
      return basis;
    }

    /** @return exact metric source or factor provenance */
    public String getMetricProvenance() {
      return metricProvenance;
    }

    /** @return exact metric effective period */
    public String getEffectivePeriod() {
      return effectivePeriod;
    }

    /** @return direction in which the paired delta improves */
    public RankingDirection getDirection() {
      return direction;
    }

    /** @return tie tolerance in the ranking metric unit */
    public double getTieTolerance() {
      return tieTolerance;
    }

    /** @return dimensionless relative tolerance for repeated baseline simulator values */
    public double getBaselineRelativeTolerance() {
      return baselineRelativeTolerance;
    }

    /** @return true when an alternative-confidence floor is enforced */
    public boolean hasMinimumAlternativeConfidence() {
      return alternativeConfidenceFloorSet;
    }

    /** @return alternative-confidence floor, or NaN when unset */
    public double getMinimumAlternativeConfidence() {
      return alternativeConfidenceFloorSet ? minimumAlternativeConfidence : Double.NaN;
    }

    /** @return true when a metric-confidence floor is enforced */
    public boolean hasMinimumMetricConfidence() {
      return metricConfidenceFloorSet;
    }

    /** @return metric-confidence floor, or NaN when unset */
    public double getMinimumMetricConfidence() {
      return metricConfidenceFloorSet ? minimumMetricConfidence : Double.NaN;
    }
  }

  /** Immutable single-objective ranking policy for transactional paired studies. */
  public static final class ObjectiveRankingPolicy implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String provenance;
    private final int objectiveIndex;
    private final String objectiveName;
    private final ObjectiveDefinition.Direction objectiveDirection;
    private final String unit;
    private final double weight;
    private final double tieTolerance;
    private final double baselineRelativeTolerance;

    /**
     * Creates an exact single-objective comparison policy.
     *
     * @param id stable policy identifier
     * @param name human-readable policy name
     * @param provenance source and assumptions for the policy
     * @param objectiveIndex exact registered objective index
     * @param objectiveName exact registered objective name
     * @param objectiveDirection direction in which the raw objective improves
     * @param unit exact engineering unit of objective values and deltas
     * @param weight exact registered objective weight
     * @param tieTolerance non-negative tie tolerance in the objective unit
     * @param baselineRelativeTolerance relative tolerance in [0, 1] for repeated baseline values
     */
    public ObjectiveRankingPolicy(String id, String name, String provenance, int objectiveIndex, String objectiveName,
        ObjectiveDefinition.Direction objectiveDirection, String unit, double weight, double tieTolerance,
        double baselineRelativeTolerance) {
      this.id = requireText(id, "Objective ranking policy identifier");
      this.name = requireText(name, "Objective ranking policy name");
      this.provenance = requireText(provenance, "Objective ranking policy provenance");
      if (objectiveIndex < 0) {
        throw new IllegalArgumentException("Objective ranking index must be non-negative");
      }
      this.objectiveIndex = objectiveIndex;
      this.objectiveName = requireText(objectiveName, "Objective ranking name");
      if (objectiveDirection == null) {
        throw new IllegalArgumentException("Objective ranking direction is required");
      }
      this.objectiveDirection = objectiveDirection;
      this.unit = requireText(unit, "Objective ranking unit");
      if (!isFinite(weight)) {
        throw new IllegalArgumentException("Objective ranking weight must be finite");
      }
      this.weight = weight;
      if (!isFinite(tieTolerance) || tieTolerance < 0.0) {
        throw new IllegalArgumentException("Objective ranking tie tolerance must be finite and non-negative");
      }
      this.tieTolerance = tieTolerance;
      if (!isFinite(baselineRelativeTolerance) || baselineRelativeTolerance < 0.0 || baselineRelativeTolerance > 1.0) {
        throw new IllegalArgumentException("Objective baseline relative tolerance must be finite and in [0, 1]");
      }
      this.baselineRelativeTolerance = baselineRelativeTolerance;
    }

    /** @return stable policy identifier */
    public String getId() {
      return id;
    }

    /** @return human-readable policy name */
    public String getName() {
      return name;
    }

    /** @return policy source and assumptions */
    public String getProvenance() {
      return provenance;
    }

    /** @return exact objective index */
    public int getObjectiveIndex() {
      return objectiveIndex;
    }

    /** @return exact objective name */
    public String getObjectiveName() {
      return objectiveName;
    }

    /** @return raw-objective improvement direction */
    public ObjectiveDefinition.Direction getObjectiveDirection() {
      return objectiveDirection;
    }

    /** @return engineering unit of objective values and deltas */
    public String getUnit() {
      return unit;
    }

    /** @return exact registered objective weight */
    public double getWeight() {
      return weight;
    }

    /** @return tie tolerance in the objective unit */
    public double getTieTolerance() {
      return tieTolerance;
    }

    /** @return relative tolerance for independently repeated baseline values */
    public double getBaselineRelativeTolerance() {
      return baselineRelativeTolerance;
    }
  }

  /** Immutable qualification and ranking evidence for one submitted study. */
  public static final class CandidateEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int inputIndex;
    private final int rank;
    private final CandidateStatus status;
    private final StudyResult studyResult;
    private final MetricEvidence baselineMetric;
    private final MetricEvidence alternativeMetric;
    private final double delta;
    private final List<String> diagnostics;

    private CandidateEvidence(CandidateDraft draft) {
      inputIndex = draft.inputIndex;
      rank = draft.rank;
      status = draft.status;
      studyResult = draft.studyResult;
      baselineMetric = draft.baselineMetric;
      alternativeMetric = draft.alternativeMetric;
      delta = draft.delta;
      diagnostics = immutableStrings(draft.diagnostics);
    }

    /** @return zero-based submission order */
    public int getInputIndex() {
      return inputIndex;
    }

    /** @return one-based competition rank, or zero when rejected */
    public int getRank() {
      return rank;
    }

    /** @return qualification or rejection status */
    public CandidateStatus getStatus() {
      return status;
    }

    /** @return true when the candidate is included in the ranking */
    public boolean isQualified() {
      return status == CandidateStatus.QUALIFIED;
    }

    /** @return complete immutable paired-study evidence */
    public StudyResult getStudyResult() {
      return studyResult;
    }

    /** @return immutable installed-capacity alternative definition */
    public CapacityAlternative getAlternativeDefinition() {
      return studyResult.getAlternativeDefinition();
    }

    /** @return declared metric evidence at the installed baseline */
    public MetricEvidence getBaselineMetric() {
      return baselineMetric;
    }

    /** @return declared metric evidence for the alternative */
    public MetricEvidence getAlternativeMetric() {
      return alternativeMetric;
    }

    /** @return alternative-minus-baseline metric delta, or NaN when unavailable */
    public double getDelta() {
      return delta;
    }

    /** @return unmodifiable qualification and ranking diagnostics */
    public List<String> getDiagnostics() {
      return immutableStrings(diagnostics);
    }
  }

  /** Immutable objective qualification and ranking evidence for one submitted study. */
  public static final class ObjectiveCandidateEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int inputIndex;
    private final int rank;
    private final CandidateStatus status;
    private final StudyResult studyResult;
    private final ObjectiveEvidence baselineObjective;
    private final ObjectiveEvidence alternativeObjective;
    private final double delta;
    private final List<String> diagnostics;

    /** Creates immutable evidence from one internal draft. */
    private ObjectiveCandidateEvidence(ObjectiveCandidateDraft draft) {
      inputIndex = draft.inputIndex;
      rank = draft.rank;
      status = draft.status;
      studyResult = draft.studyResult;
      baselineObjective = draft.baselineObjective;
      alternativeObjective = draft.alternativeObjective;
      delta = draft.delta;
      diagnostics = immutableStrings(draft.diagnostics);
    }

    /** @return zero-based submission order */
    public int getInputIndex() {
      return inputIndex;
    }

    /** @return one-based competition rank, or zero when rejected */
    public int getRank() {
      return rank;
    }

    /** @return qualification or rejection status */
    public CandidateStatus getStatus() {
      return status;
    }

    /** @return true when the candidate is included in the objective ranking */
    public boolean isQualified() {
      return status == CandidateStatus.QUALIFIED;
    }

    /** @return complete immutable paired-study evidence */
    public StudyResult getStudyResult() {
      return studyResult;
    }

    /** @return immutable installed-capacity alternative definition */
    public CapacityAlternative getAlternativeDefinition() {
      return studyResult.getAlternativeDefinition();
    }

    /** @return selected objective evidence at the installed baseline */
    public ObjectiveEvidence getBaselineObjective() {
      return baselineObjective;
    }

    /** @return selected objective evidence for the alternative */
    public ObjectiveEvidence getAlternativeObjective() {
      return alternativeObjective;
    }

    /** @return alternative-minus-baseline raw-objective delta, or NaN when rejected */
    public double getDelta() {
      return delta;
    }

    /** @return unmodifiable qualification and ranking diagnostics */
    public List<String> getDiagnostics() {
      return immutableStrings(diagnostics);
    }
  }

  /** Immutable result of one exact single-objective portfolio ranking. */
  public static final class ObjectiveRankingResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String provenance;
    private final ObjectiveRankingPolicy policy;
    private final RankingOutcome outcome;
    private final List<ObjectiveCandidateEvidence> rankedCandidates;
    private final List<ObjectiveCandidateEvidence> rejectedCandidates;
    private final List<ObjectiveCandidateEvidence> candidatesInInputOrder;
    private final List<String> diagnostics;

    /** Creates one immutable objective-ranking result. */
    private ObjectiveRankingResult(String id, String name, String provenance, ObjectiveRankingPolicy policy,
        RankingOutcome outcome, List<ObjectiveCandidateEvidence> rankedCandidates,
        List<ObjectiveCandidateEvidence> rejectedCandidates, List<ObjectiveCandidateEvidence> candidatesInInputOrder,
        List<String> diagnostics) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.policy = policy;
      this.outcome = outcome;
      this.rankedCandidates = immutableList(rankedCandidates);
      this.rejectedCandidates = immutableList(rejectedCandidates);
      this.candidatesInInputOrder = immutableList(candidatesInInputOrder);
      this.diagnostics = immutableStrings(diagnostics);
    }

    /** @return stable portfolio identifier */
    public String getId() {
      return id;
    }

    /** @return human-readable portfolio name */
    public String getName() {
      return name;
    }

    /** @return portfolio source and assumptions */
    public String getProvenance() {
      return provenance;
    }

    /** @return immutable exact objective-ranking policy */
    public ObjectiveRankingPolicy getPolicy() {
      return policy;
    }

    /** @return complete, partial, or no-qualified-alternative outcome */
    public RankingOutcome getOutcome() {
      return outcome;
    }

    /** @return unmodifiable candidates in deterministic objective-rank order */
    public List<ObjectiveCandidateEvidence> getRankedCandidates() {
      return immutableList(rankedCandidates);
    }

    /** @return unmodifiable rejected candidates in submission order */
    public List<ObjectiveCandidateEvidence> getRejectedCandidates() {
      return immutableList(rejectedCandidates);
    }

    /** @return complete unmodifiable audit trail in submission order */
    public List<ObjectiveCandidateEvidence> getCandidatesInInputOrder() {
      return immutableList(candidatesInInputOrder);
    }

    /** @return highest-ranked candidate, or null when none qualified */
    public ObjectiveCandidateEvidence getBestCandidate() {
      return rankedCandidates.isEmpty() ? null : rankedCandidates.get(0);
    }

    /** @return unmodifiable portfolio-level diagnostics */
    public List<String> getDiagnostics() {
      return immutableStrings(diagnostics);
    }
  }

  /** Immutable result of one portfolio ranking. */
  public static final class RankingResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String provenance;
    private final RankingPolicy policy;
    private final RankingOutcome outcome;
    private final List<CandidateEvidence> rankedCandidates;
    private final List<CandidateEvidence> rejectedCandidates;
    private final List<CandidateEvidence> candidatesInInputOrder;
    private final List<String> diagnostics;

    private RankingResult(String id, String name, String provenance, RankingPolicy policy, RankingOutcome outcome,
        List<CandidateEvidence> rankedCandidates, List<CandidateEvidence> rejectedCandidates,
        List<CandidateEvidence> candidatesInInputOrder, List<String> diagnostics) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.policy = policy;
      this.outcome = outcome;
      this.rankedCandidates = immutableList(rankedCandidates);
      this.rejectedCandidates = immutableList(rejectedCandidates);
      this.candidatesInInputOrder = immutableList(candidatesInInputOrder);
      this.diagnostics = immutableStrings(diagnostics);
    }

    /** @return stable portfolio identifier */
    public String getId() {
      return id;
    }

    /** @return human-readable portfolio name */
    public String getName() {
      return name;
    }

    /** @return portfolio source and assumptions */
    public String getProvenance() {
      return provenance;
    }

    /** @return immutable single-metric ranking policy */
    public RankingPolicy getPolicy() {
      return policy;
    }

    /** @return complete, partial, or no-qualified-alternative outcome */
    public RankingOutcome getOutcome() {
      return outcome;
    }

    /** @return unmodifiable candidates in deterministic rank order */
    public List<CandidateEvidence> getRankedCandidates() {
      return immutableList(rankedCandidates);
    }

    /** @return unmodifiable rejected candidates in submission order */
    public List<CandidateEvidence> getRejectedCandidates() {
      return immutableList(rejectedCandidates);
    }

    /** @return complete unmodifiable audit trail in submission order */
    public List<CandidateEvidence> getCandidatesInInputOrder() {
      return immutableList(candidatesInInputOrder);
    }

    /** @return highest-ranked candidate, or null when none qualified */
    public CandidateEvidence getBestCandidate() {
      return rankedCandidates.isEmpty() ? null : rankedCandidates.get(0);
    }

    /** @return unmodifiable portfolio-level diagnostics */
    public List<String> getDiagnostics() {
      return immutableStrings(diagnostics);
    }
  }

  /** Mutable internal row used only while assigning qualification and ranks. */
  private static final class CandidateDraft {
    private final int inputIndex;
    private final StudyResult studyResult;
    private int rank;
    private CandidateStatus status;
    private MetricEvidence baselineMetric;
    private MetricEvidence alternativeMetric;
    private double delta = Double.NaN;
    private final List<String> diagnostics = new ArrayList<String>();

    private CandidateDraft(int inputIndex, StudyResult studyResult) {
      this.inputIndex = inputIndex;
      this.studyResult = studyResult;
    }
  }

  /** Mutable internal row used only while assigning objective qualification and ranks. */
  private static final class ObjectiveCandidateDraft {
    private final int inputIndex;
    private final StudyResult studyResult;
    private int rank;
    private CandidateStatus status;
    private ObjectiveEvidence baselineObjective;
    private ObjectiveEvidence alternativeObjective;
    private double delta = Double.NaN;
    private final List<String> diagnostics = new ArrayList<String>();

    /** Creates one objective-ranking draft. */
    private ObjectiveCandidateDraft(int inputIndex, StudyResult studyResult) {
      this.inputIndex = inputIndex;
      this.studyResult = studyResult;
    }
  }

  /** Frozen reference evidence that all later qualified baselines must reproduce exactly. */
  private static final class BaselineReference {
    private final ScenarioEvidence baseline;
    private final MetricEvidence metric;

    private BaselineReference(ScenarioEvidence baseline, MetricEvidence metric) {
      this.baseline = baseline;
      this.metric = metric;
    }
  }

  private final String id;
  private final String name;
  private final String provenance;
  private final RankingPolicy policy;

  /**
   * Creates an immutable portfolio-ranking configuration.
   *
   * @param id stable portfolio identifier
   * @param name human-readable portfolio name
   * @param provenance source and assumptions for the portfolio
   * @param policy exact single-metric comparison policy
   */
  public ProcessModelDebottleneckRanking(String id, String name, String provenance, RankingPolicy policy) {
    this.id = requireText(id, "Portfolio identifier");
    this.name = requireText(name, "Portfolio name");
    this.provenance = requireText(provenance, "Portfolio provenance");
    if (policy == null) {
      throw new IllegalArgumentException("Ranking policy is required");
    }
    this.policy = policy;
  }

  /**
   * Creates a portfolio identity for objective-ranking calls.
   *
   * <p>
   * Callers using this constructor must invoke {@link #rankObjectives(List, ObjectiveRankingPolicy)}. The separate
   * policy argument keeps transactional objective evidence from being misrepresented as a post-restoration metric.
   * </p>
   *
   * @param id stable portfolio identifier
   * @param name human-readable portfolio name
   * @param provenance source and assumptions for the portfolio
   */
  public ProcessModelDebottleneckRanking(String id, String name, String provenance) {
    this.id = requireText(id, "Portfolio identifier");
    this.name = requireText(name, "Portfolio name");
    this.provenance = requireText(provenance, "Portfolio provenance");
    this.policy = null;
  }

  /** @return stable portfolio identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable portfolio name */
  public String getName() {
    return name;
  }

  /** @return portfolio source and assumptions */
  public String getProvenance() {
    return provenance;
  }

  /** @return immutable single-metric ranking policy, or null for an objective-only portfolio */
  public RankingPolicy getPolicy() {
    return policy;
  }

  /**
   * Qualifies and ranks immutable paired-study results.
   *
   * @param studies completed paired studies in deterministic submission order
   * @return immutable ranking and rejection evidence
   */
  public RankingResult rank(List<StudyResult> studies) {
    if (policy == null) {
      throw new IllegalStateException(
          "Metric ranking requires the constructor with a RankingPolicy; use rankObjectives for objective evidence");
    }
    if (studies == null || studies.isEmpty()) {
      throw new IllegalArgumentException("At least one paired study result is required");
    }
    validateIdentities(studies);

    List<CandidateDraft> drafts = new ArrayList<CandidateDraft>();
    BaselineReference reference = null;
    for (int index = 0; index < studies.size(); index++) {
      CandidateDraft draft = assess(index, studies.get(index));
      if (draft.status == CandidateStatus.QUALIFIED) {
        if (reference == null) {
          reference = new BaselineReference(draft.studyResult.getBaseline(), draft.baselineMetric);
          draft.diagnostics.add("Established the deterministic portfolio baseline with relative numeric tolerance "
              + policy.getBaselineRelativeTolerance());
        } else if (!baselineMatches(reference, draft.studyResult.getBaseline(), draft.baselineMetric)) {
          draft.status = CandidateStatus.BASELINE_INCOMPATIBLE;
          draft.delta = Double.NaN;
          draft.diagnostics.add("Baseline search, parameters, metric metadata, or simulator values differ from the "
              + "portfolio reference baseline beyond relative tolerance " + policy.getBaselineRelativeTolerance());
        }
      }
      drafts.add(draft);
    }

    List<CandidateDraft> qualified = new ArrayList<CandidateDraft>();
    List<CandidateDraft> rejected = new ArrayList<CandidateDraft>();
    for (CandidateDraft draft : drafts) {
      if (draft.status == CandidateStatus.QUALIFIED) {
        qualified.add(draft);
      } else {
        rejected.add(draft);
      }
    }
    assignRanks(qualified);

    List<CandidateEvidence> rankedEvidence = new ArrayList<CandidateEvidence>();
    for (CandidateDraft draft : qualified) {
      rankedEvidence.add(new CandidateEvidence(draft));
    }
    List<CandidateEvidence> rejectedEvidence = new ArrayList<CandidateEvidence>();
    for (CandidateDraft draft : rejected) {
      rejectedEvidence.add(new CandidateEvidence(draft));
    }
    List<CandidateEvidence> inputEvidence = new ArrayList<CandidateEvidence>();
    for (CandidateDraft draft : drafts) {
      inputEvidence.add(findEvidence(draft.inputIndex, rankedEvidence, rejectedEvidence));
    }

    RankingOutcome outcome = rankedEvidence.isEmpty() ? RankingOutcome.NO_QUALIFIED_ALTERNATIVE
        : rejectedEvidence.isEmpty() ? RankingOutcome.COMPLETED : RankingOutcome.PARTIAL;
    List<String> diagnostics = new ArrayList<String>();
    diagnostics.add("Ranked " + rankedEvidence.size() + " compatible alternatives and rejected "
        + rejectedEvidence.size() + " alternatives");
    diagnostics.add(
        "Ranking uses one declared metric and does not aggregate unlike physical, emission, or " + "economic units");
    diagnostics.add("Results are sampled screening evidence, not causal value, global optimality, design approval, "
        + "certified emissions, or investment approval");
    return new RankingResult(id, name, provenance, policy, outcome, rankedEvidence, rejectedEvidence, inputEvidence,
        diagnostics);
  }

  /**
   * Convenience overload for Java arrays and JPype callers.
   *
   * @param studies completed paired-study result array
   * @return immutable ranking and rejection evidence
   */
  public RankingResult rank(StudyResult[] studies) {
    if (studies == null) {
      throw new IllegalArgumentException("Paired study result array must not be null");
    }
    return rank(Arrays.asList(Arrays.copyOf(studies, studies.length)));
  }

  /**
   * Qualifies and ranks transactional paired studies by one exact registered raw objective.
   *
   * <p>
   * This mode is intended for operating-action studies that cannot safely sample callbacks after each candidate,
   * because the transactional evaluator restores model state before returning. It ranks only immutable objective
   * evidence already frozen in each {@link StudyResult}; it never reruns the process model. Baseline action vectors
   * remain bitwise exact, while independently repeated simulator values may differ only inside the declared relative
   * tolerance.
   * </p>
   *
   * @param studies completed paired studies in deterministic submission order
   * @param objectivePolicy exact single-objective comparison policy
   * @return immutable objective ranking and rejection evidence
   */
  public ObjectiveRankingResult rankObjectives(List<StudyResult> studies, ObjectiveRankingPolicy objectivePolicy) {
    if (studies == null || studies.isEmpty()) {
      throw new IllegalArgumentException("At least one paired study result is required");
    }
    if (objectivePolicy == null) {
      throw new IllegalArgumentException("Objective ranking policy is required");
    }
    validateIdentities(studies);

    List<ObjectiveCandidateDraft> drafts = new ArrayList<ObjectiveCandidateDraft>();
    ScenarioEvidence referenceBaseline = null;
    for (int index = 0; index < studies.size(); index++) {
      ObjectiveCandidateDraft draft = assessObjective(index, studies.get(index), objectivePolicy);
      if (draft.status == CandidateStatus.QUALIFIED) {
        if (referenceBaseline == null) {
          referenceBaseline = draft.studyResult.getBaseline();
          draft.diagnostics.add("Established the deterministic objective-portfolio baseline with relative numeric "
              + "tolerance " + objectivePolicy.getBaselineRelativeTolerance());
        } else if (!baselineMatchesObjective(referenceBaseline, draft.studyResult.getBaseline(), objectivePolicy)) {
          draft.status = CandidateStatus.BASELINE_INCOMPATIBLE;
          draft.delta = Double.NaN;
          draft.diagnostics.add("Baseline search, action vector, objectives, constraints, or simulator values differ "
              + "from the objective-portfolio reference baseline beyond relative tolerance "
              + objectivePolicy.getBaselineRelativeTolerance());
        }
      }
      drafts.add(draft);
    }

    List<ObjectiveCandidateDraft> qualified = new ArrayList<ObjectiveCandidateDraft>();
    List<ObjectiveCandidateDraft> rejected = new ArrayList<ObjectiveCandidateDraft>();
    for (ObjectiveCandidateDraft draft : drafts) {
      if (draft.status == CandidateStatus.QUALIFIED) {
        qualified.add(draft);
      } else {
        rejected.add(draft);
      }
    }
    assignObjectiveRanks(qualified, objectivePolicy);

    List<ObjectiveCandidateEvidence> rankedEvidence = new ArrayList<ObjectiveCandidateEvidence>();
    for (ObjectiveCandidateDraft draft : qualified) {
      rankedEvidence.add(new ObjectiveCandidateEvidence(draft));
    }
    List<ObjectiveCandidateEvidence> rejectedEvidence = new ArrayList<ObjectiveCandidateEvidence>();
    for (ObjectiveCandidateDraft draft : rejected) {
      rejectedEvidence.add(new ObjectiveCandidateEvidence(draft));
    }
    List<ObjectiveCandidateEvidence> inputEvidence = new ArrayList<ObjectiveCandidateEvidence>();
    for (ObjectiveCandidateDraft draft : drafts) {
      inputEvidence.add(findObjectiveEvidence(draft.inputIndex, rankedEvidence, rejectedEvidence));
    }

    RankingOutcome outcome = rankedEvidence.isEmpty() ? RankingOutcome.NO_QUALIFIED_ALTERNATIVE
        : rejectedEvidence.isEmpty() ? RankingOutcome.COMPLETED : RankingOutcome.PARTIAL;
    List<String> diagnostics = new ArrayList<String>();
    diagnostics.add("Ranked " + rankedEvidence.size() + " compatible objective alternatives and rejected "
        + rejectedEvidence.size() + " alternatives");
    diagnostics.add(
        "Ranking uses one exact raw objective and does not aggregate unlike engineering or economic " + "quantities");
    diagnostics.add("Results are paired sampled evidence, not causal value, a shadow price, global optimality, "
        + "design approval, investment approval, permission to change a limit, or operating approval");
    return new ObjectiveRankingResult(id, name, provenance, objectivePolicy, outcome, rankedEvidence, rejectedEvidence,
        inputEvidence, diagnostics);
  }

  /**
   * Convenience overload for Java arrays and JPype callers.
   *
   * @param studies completed paired-study result array
   * @param objectivePolicy exact single-objective comparison policy
   * @return immutable objective ranking and rejection evidence
   */
  public ObjectiveRankingResult rankObjectives(StudyResult[] studies, ObjectiveRankingPolicy objectivePolicy) {
    if (studies == null) {
      throw new IllegalArgumentException("Paired study result array must not be null");
    }
    return rankObjectives(Arrays.asList(Arrays.copyOf(studies, studies.length)), objectivePolicy);
  }

  /** Assesses one study against the exact objective policy without mutating the model. */
  private ObjectiveCandidateDraft assessObjective(int index, StudyResult study,
      ObjectiveRankingPolicy objectivePolicy) {
    ObjectiveCandidateDraft draft = new ObjectiveCandidateDraft(index, study);
    if (study.getOutcome() != StudyOutcome.COMPLETED) {
      draft.status = CandidateStatus.STUDY_NOT_COMPLETED;
      draft.diagnostics.add("Study outcome is " + study.getOutcome());
      return draft;
    }
    if (!study.isCapacityRestored() || !study.isProcessStateRestored() || !study.isRecoverySimulationConverged()) {
      draft.status = CandidateStatus.RECOVERY_NOT_VERIFIED;
      draft.diagnostics.add("Installed-capacity and process-state recovery must all be verified");
      return draft;
    }
    if (study.getBaseline() == null || study.getAlternative() == null || !study.getBaseline().isQualified()
        || !study.getAlternative().isQualified()) {
      draft.status = CandidateStatus.SCENARIO_NOT_QUALIFIED;
      draft.diagnostics.add("Both baseline and alternative scenarios must be qualified");
      return draft;
    }
    int objectiveIndex = objectivePolicy.getObjectiveIndex();
    if (objectiveIndex >= study.getBaseline().getObjectives().size()
        || objectiveIndex >= study.getAlternative().getObjectives().size()) {
      draft.status = CandidateStatus.OBJECTIVE_MISSING;
      draft.diagnostics.add("Objective index was not found: " + objectiveIndex);
      return draft;
    }
    draft.baselineObjective = study.getBaseline().getObjectives().get(objectiveIndex);
    draft.alternativeObjective = study.getAlternative().getObjectives().get(objectiveIndex);
    if (!objectiveMetadataMatches(draft.baselineObjective, objectivePolicy)
        || !objectiveMetadataMatches(draft.alternativeObjective, objectivePolicy)) {
      draft.status = CandidateStatus.OBJECTIVE_METADATA_MISMATCH;
      draft.diagnostics.add("Objective metadata does not exactly match the declared policy");
      return draft;
    }
    double calculatedDelta = draft.alternativeObjective.getRawValue() - draft.baselineObjective.getRawValue();
    if (!study.isObjectiveDeltaCalculable() || !isFinite(draft.baselineObjective.getRawValue())
        || !isFinite(draft.alternativeObjective.getRawValue()) || !isFinite(calculatedDelta)
        || !relativeEqual(study.getObjectiveDelta(), calculatedDelta, objectivePolicy.getBaselineRelativeTolerance())) {
      draft.status = CandidateStatus.OBJECTIVE_NOT_CALCULABLE;
      draft.diagnostics.add("Objective baseline, alternative, or delta is unavailable, non-finite, or inconsistent");
      return draft;
    }
    draft.status = CandidateStatus.QUALIFIED;
    draft.delta = calculatedDelta;
    draft.diagnostics.add("Paired raw-objective delta is compatible and finite in " + objectivePolicy.getUnit());
    return draft;
  }

  /** Returns true when one objective row exactly matches the declared metadata. */
  private static boolean objectiveMetadataMatches(ObjectiveEvidence objective, ObjectiveRankingPolicy objectivePolicy) {
    return objective != null && objective.getIndex() == objectivePolicy.getObjectiveIndex()
        && objectivePolicy.getObjectiveName().equals(objective.getName())
        && objective.getDirection() == objectivePolicy.getObjectiveDirection()
        && objectivePolicy.getUnit().equals(objective.getUnit())
        && bitsEqual(objective.getWeight(), objectivePolicy.getWeight());
  }

  /** Returns true when two independently executed objective baselines remain compatible. */
  private boolean baselineMatchesObjective(ScenarioEvidence expected, ScenarioEvidence actual,
      ObjectiveRankingPolicy objectivePolicy) {
    double tolerance = objectivePolicy.getBaselineRelativeTolerance();
    return expected.getSearchId().equals(actual.getSearchId())
        && expected.getSearchName().equals(actual.getSearchName())
        && expected.getSearchProvenance().equals(actual.getSearchProvenance())
        && arraysEqual(expected.getSelectedParameters(), actual.getSelectedParameters())
        && objectivesEqual(expected.getObjectives(), actual.getObjectives(), tolerance)
        && constraintsEqual(expected.getConstraints(), actual.getConstraints(), tolerance);
  }

  /** Assigns deterministic competition ranks to compatible raw-objective deltas. */
  private void assignObjectiveRanks(List<ObjectiveCandidateDraft> qualified,
      final ObjectiveRankingPolicy objectivePolicy) {
    Collections.sort(qualified, new Comparator<ObjectiveCandidateDraft>() {
      @Override
      public int compare(ObjectiveCandidateDraft left, ObjectiveCandidateDraft right) {
        int comparison = Double.compare(left.delta, right.delta);
        if (objectivePolicy.getObjectiveDirection() == ObjectiveDefinition.Direction.MAXIMIZE) {
          comparison = -comparison;
        }
        return comparison != 0 ? comparison : Integer.compare(left.inputIndex, right.inputIndex);
      }
    });
    int start = 0;
    while (start < qualified.size()) {
      double groupLeader = qualified.get(start).delta;
      int end = start + 1;
      while (end < qualified.size()
          && Math.abs(qualified.get(end).delta - groupLeader) <= objectivePolicy.getTieTolerance()) {
        end++;
      }
      List<ObjectiveCandidateDraft> tieGroup = qualified.subList(start, end);
      Collections.sort(tieGroup, new Comparator<ObjectiveCandidateDraft>() {
        @Override
        public int compare(ObjectiveCandidateDraft left, ObjectiveCandidateDraft right) {
          return Integer.compare(left.inputIndex, right.inputIndex);
        }
      });
      int rank = start + 1;
      for (ObjectiveCandidateDraft draft : tieGroup) {
        draft.rank = rank;
        draft.diagnostics.add("Assigned competition rank " + rank + " using tie tolerance "
            + objectivePolicy.getTieTolerance() + " " + objectivePolicy.getUnit());
      }
      start = end;
    }
  }

  /** Finds immutable objective evidence for one original submission index. */
  private ObjectiveCandidateEvidence findObjectiveEvidence(int inputIndex, List<ObjectiveCandidateEvidence> ranked,
      List<ObjectiveCandidateEvidence> rejected) {
    for (ObjectiveCandidateEvidence evidence : ranked) {
      if (evidence.getInputIndex() == inputIndex) {
        return evidence;
      }
    }
    for (ObjectiveCandidateEvidence evidence : rejected) {
      if (evidence.getInputIndex() == inputIndex) {
        return evidence;
      }
    }
    throw new IllegalStateException("Objective portfolio candidate evidence was lost");
  }

  private CandidateDraft assess(int index, StudyResult study) {
    CandidateDraft draft = new CandidateDraft(index, study);
    if (study.getOutcome() != StudyOutcome.COMPLETED) {
      draft.status = CandidateStatus.STUDY_NOT_COMPLETED;
      draft.diagnostics.add("Study outcome is " + study.getOutcome());
      return draft;
    }
    if (!study.isCapacityRestored() || !study.isProcessStateRestored() || !study.isRecoverySimulationConverged()) {
      draft.status = CandidateStatus.RECOVERY_NOT_VERIFIED;
      draft.diagnostics.add("Installed-capacity and process-state recovery must all be verified");
      return draft;
    }
    if (study.getBaseline() == null || study.getAlternative() == null || !study.getBaseline().isQualified()
        || !study.getAlternative().isQualified()) {
      draft.status = CandidateStatus.SCENARIO_NOT_QUALIFIED;
      draft.diagnostics.add("Both baseline and alternative scenarios must be qualified");
      return draft;
    }

    MetricComparison comparison = findRankingMetric(study);
    if (comparison == null) {
      draft.status = CandidateStatus.RANKING_METRIC_MISSING;
      draft.diagnostics.add("Ranking metric was not found: " + policy.getMetricId());
      return draft;
    }
    draft.baselineMetric = comparison.getBaseline();
    draft.alternativeMetric = comparison.getAlternative();
    if (!metadataMatches(draft.baselineMetric) || !metadataMatches(draft.alternativeMetric)) {
      draft.status = CandidateStatus.METRIC_METADATA_MISMATCH;
      draft.diagnostics.add("Ranking metric metadata does not exactly match the declared policy");
      return draft;
    }
    if (!comparison.isCalculable() || !isFinite(comparison.getDelta())) {
      draft.status = CandidateStatus.RANKING_METRIC_NOT_CALCULABLE;
      draft.diagnostics.add("Ranking metric comparison is unavailable or non-finite");
      return draft;
    }
    CapacityAlternative alternative = study.getAlternativeDefinition();
    if (policy.hasMinimumAlternativeConfidence()
        && (!alternative.hasConfidence() || alternative.getConfidence() < policy.getMinimumAlternativeConfidence())) {
      draft.status = CandidateStatus.ALTERNATIVE_CONFIDENCE_TOO_LOW;
      draft.diagnostics.add("Alternative confidence is absent or below the declared floor");
      return draft;
    }
    if (policy.hasMinimumMetricConfidence()
        && (!meetsConfidence(draft.baselineMetric, policy.getMinimumMetricConfidence())
            || !meetsConfidence(draft.alternativeMetric, policy.getMinimumMetricConfidence()))) {
      draft.status = CandidateStatus.METRIC_CONFIDENCE_TOO_LOW;
      draft.diagnostics.add("Metric confidence is absent or below the declared floor");
      return draft;
    }
    draft.status = CandidateStatus.QUALIFIED;
    draft.delta = comparison.getDelta();
    draft.diagnostics.add("Paired metric delta is compatible and finite in " + policy.getUnit());
    return draft;
  }

  private MetricComparison findRankingMetric(StudyResult study) {
    MetricComparison found = null;
    for (MetricComparison comparison : study.getMetricComparisons()) {
      MetricEvidence baseline = comparison.getBaseline();
      if (baseline != null && policy.getMetricId().equals(baseline.getId())) {
        if (found != null) {
          throw new IllegalArgumentException("Study contains duplicate ranking metric identifiers: " + study.getId());
        }
        found = comparison;
      }
    }
    return found;
  }

  private boolean metadataMatches(MetricEvidence evidence) {
    return evidence != null && policy.getMetricId().equals(evidence.getId())
        && policy.getMetricName().equals(evidence.getName()) && policy.getMetricKind() == evidence.getKind()
        && policy.getUnit().equals(evidence.getUnit()) && policy.getBasis().equals(evidence.getBasis())
        && policy.getMetricProvenance().equals(evidence.getProvenance())
        && policy.getEffectivePeriod().equals(evidence.getEffectivePeriod());
  }

  private boolean baselineMatches(BaselineReference reference, ScenarioEvidence baseline, MetricEvidence metric) {
    ScenarioEvidence expected = reference.baseline;
    return expected.getSearchId().equals(baseline.getSearchId())
        && expected.getSearchName().equals(baseline.getSearchName())
        && expected.getSearchProvenance().equals(baseline.getSearchProvenance())
        && arraysEqual(expected.getSelectedParameters(), baseline.getSelectedParameters())
        && metricEvidenceEqual(reference.metric, metric)
        && objectivesEqual(expected.getObjectives(), baseline.getObjectives())
        && constraintsEqual(expected.getConstraints(), baseline.getConstraints());
  }

  private boolean metricEvidenceEqual(MetricEvidence left, MetricEvidence right) {
    return metadataMatches(left) && metadataMatches(right) && left.getStatus() == right.getStatus()
        && left.isRequired() == right.isRequired() && left.hasConfidence() == right.hasConfidence()
        && (!left.hasConfidence() || bitsEqual(left.getConfidence(), right.getConfidence()))
        && relativeEqual(left.getValue(), right.getValue());
  }

  private boolean objectivesEqual(List<ObjectiveEvidence> left, List<ObjectiveEvidence> right) {
    return objectivesEqual(left, right, policy.getBaselineRelativeTolerance());
  }

  /** Returns true when two objective lists match inside one declared numeric tolerance. */
  private static boolean objectivesEqual(List<ObjectiveEvidence> left, List<ObjectiveEvidence> right,
      double tolerance) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int index = 0; index < left.size(); index++) {
      ObjectiveEvidence a = left.get(index);
      ObjectiveEvidence b = right.get(index);
      if (a.getIndex() != b.getIndex() || !a.getName().equals(b.getName()) || a.getDirection() != b.getDirection()
          || !a.getUnit().equals(b.getUnit()) || !bitsEqual(a.getWeight(), b.getWeight())
          || !relativeEqual(a.getRawValue(), b.getRawValue(), tolerance)
          || !relativeEqual(a.getMinimizerValue(), b.getMinimizerValue(), tolerance)) {
        return false;
      }
    }
    return true;
  }

  private boolean constraintsEqual(List<ConstraintEvidence> left, List<ConstraintEvidence> right) {
    return constraintsEqual(left, right, policy.getBaselineRelativeTolerance());
  }

  /** Returns true when two constraint lists match inside one declared numeric tolerance. */
  private static boolean constraintsEqual(List<ConstraintEvidence> left, List<ConstraintEvidence> right,
      double tolerance) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int index = 0; index < left.size(); index++) {
      ConstraintEvidence a = left.get(index);
      ConstraintEvidence b = right.get(index);
      if (a.getIndex() != b.getIndex() || !a.getName().equals(b.getName()) || a.getType() != b.getType()
          || !a.getUnit().equals(b.getUnit()) || a.isHard() != b.isHard()
          || !relativeEqual(a.getValue(), b.getValue(), tolerance)
          || !relativeEqual(a.getMargin(), b.getMargin(), tolerance)) {
        return false;
      }
    }
    return true;
  }

  private void assignRanks(List<CandidateDraft> qualified) {
    Collections.sort(qualified, new Comparator<CandidateDraft>() {
      @Override
      public int compare(CandidateDraft left, CandidateDraft right) {
        int comparison = Double.compare(left.delta, right.delta);
        if (policy.getDirection() == RankingDirection.MAXIMIZE) {
          comparison = -comparison;
        }
        return comparison != 0 ? comparison : Integer.compare(left.inputIndex, right.inputIndex);
      }
    });

    int start = 0;
    while (start < qualified.size()) {
      double groupLeader = qualified.get(start).delta;
      int end = start + 1;
      while (end < qualified.size() && Math.abs(qualified.get(end).delta - groupLeader) <= policy.getTieTolerance()) {
        end++;
      }
      List<CandidateDraft> tieGroup = qualified.subList(start, end);
      Collections.sort(tieGroup, new Comparator<CandidateDraft>() {
        @Override
        public int compare(CandidateDraft left, CandidateDraft right) {
          return Integer.compare(left.inputIndex, right.inputIndex);
        }
      });
      int rank = start + 1;
      for (CandidateDraft draft : tieGroup) {
        draft.rank = rank;
        draft.diagnostics.add("Assigned competition rank " + rank + " using tie tolerance " + policy.getTieTolerance()
            + " " + policy.getUnit());
      }
      start = end;
    }
  }

  private void validateIdentities(List<StudyResult> studies) {
    Set<String> studyIds = new HashSet<String>();
    Set<String> alternativeIds = new HashSet<String>();
    for (StudyResult study : studies) {
      if (study == null || study.getAlternativeDefinition() == null) {
        throw new IllegalArgumentException("Every submitted study and alternative definition is required");
      }
      if (!studyIds.add(study.getId())) {
        throw new IllegalArgumentException("Duplicate study identifier: " + study.getId());
      }
      String alternativeId = study.getAlternativeDefinition().getId();
      if (!alternativeIds.add(alternativeId)) {
        throw new IllegalArgumentException("Duplicate alternative identifier: " + alternativeId);
      }
    }
  }

  private CandidateEvidence findEvidence(int inputIndex, List<CandidateEvidence> ranked,
      List<CandidateEvidence> rejected) {
    for (CandidateEvidence evidence : ranked) {
      if (evidence.getInputIndex() == inputIndex) {
        return evidence;
      }
    }
    for (CandidateEvidence evidence : rejected) {
      if (evidence.getInputIndex() == inputIndex) {
        return evidence;
      }
    }
    throw new IllegalStateException("Portfolio candidate evidence was lost");
  }

  private static boolean meetsConfidence(MetricEvidence evidence, double minimum) {
    return evidence.hasConfidence() && evidence.getConfidence() >= minimum;
  }

  private static double validateConfidence(double value, String label) {
    if (Double.isNaN(value)) {
      return Double.NaN;
    }
    if (!isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(label + " must be in [0, 1] or NaN");
    }
    return value;
  }

  private static boolean arraysEqual(double[] left, double[] right) {
    if (left.length != right.length) {
      return false;
    }
    for (int index = 0; index < left.length; index++) {
      if (!bitsEqual(left[index], right[index])) {
        return false;
      }
    }
    return true;
  }

  private static boolean bitsEqual(double left, double right) {
    return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
  }

  private boolean relativeEqual(double left, double right) {
    return relativeEqual(left, right, policy.getBaselineRelativeTolerance());
  }

  /** Returns true when finite values agree inside the declared relative tolerance. */
  private static boolean relativeEqual(double left, double right, double tolerance) {
    if (bitsEqual(left, right)) {
      return true;
    }
    if (!isFinite(left) || !isFinite(right)) {
      return false;
    }
    double scale = Math.max(Math.abs(left), Math.abs(right));
    return scale > 0.0 && Math.abs(left - right) <= tolerance * scale;
  }

  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  private static String requireText(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }

  private static <T> List<T> immutableList(List<T> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<T>(values));
  }

  private static List<String> immutableStrings(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }
}
