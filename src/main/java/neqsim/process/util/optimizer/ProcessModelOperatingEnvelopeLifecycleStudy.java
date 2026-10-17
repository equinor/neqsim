package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.LeadingConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.SliceResult;
import neqsim.process.util.optimizer.ProcessModelOperatingPointOptimizer.OperatingPointSearchResult;

/**
 * Aggregates independently validated accepted-point envelope studies across ordered lifecycle periods.
 *
 * <p>
 * Every period is admitted only through
 * {@link ProcessModelOperatingEnvelopeStudy#getAcceptedPointEnvelopeEvidence(String, OperatingPointSearchResult, SliceResult...)},
 * so a lifecycle report cannot bypass accepted-point replay, finite-candidate, complete-constraint, restoration, or
 * slice-completion checks. Periods must use the same ordered action identifiers, but may use independently configured
 * process models, constraints, equipment availability and operating limits. This keeps lifecycle comparison separate
 * from the physical models that own those definitions.
 * </p>
 *
 * <p>
 * The result reports sampled accepted bottleneck changes between adjacent periods. It is advisory evidence, not a
 * continuous forecast, equipment qualification, shadow price, operating approval, or substitute for period-specific
 * convergence and constraint evidence.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelOperatingEnvelopeLifecycleStudy {
  private final String id;
  private final String name;
  private final String provenance;
  private final String timeUnit;
  private final List<PeriodEvidence> periods = new ArrayList<PeriodEvidence>();
  private List<String> acceptedActionIds;
  private List<String> acceptedStateMetricIds;
  private List<String> acceptedStateMetricUnits;

  /**
   * Creates an ordered lifecycle study.
   *
   * @param id stable lifecycle-study identifier
   * @param name human-readable study name
   * @param provenance engineering basis for the period definitions
   * @param timeUnit common elapsed-time unit used by every period
   */
  public ProcessModelOperatingEnvelopeLifecycleStudy(String id, String name, String provenance, String timeUnit) {
    this.id = requireText(id, "Lifecycle-study identifier");
    this.name = requireText(name, "Lifecycle-study name");
    this.provenance = requireText(provenance, "Lifecycle-study provenance");
    this.timeUnit = requireText(timeUnit, "Lifecycle-study time unit");
  }

  /** @return stable lifecycle-study identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable lifecycle-study name */
  public String getName() {
    return name;
  }

  /** @return lifecycle-study provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return common elapsed-time unit */
  public String getTimeUnit() {
    return timeUnit;
  }

  /**
   * Adds one completely validated period in strictly increasing elapsed-time order.
   *
   * <p>
   * The process/evaluator may differ between periods, allowing reservoir state, equipment availability, operating
   * limits and qualified map inputs to change. The ordered action identifiers must remain identical so accepted
   * operating vectors retain a stable meaning. The source study performs all model-state and envelope validation before
   * this method mutates the lifecycle builder.
   * </p>
   *
   * @param periodId unique stable period identifier
   * @param periodName human-readable period name
   * @param elapsedTime non-negative elapsed time from the lifecycle origin
   * @param configurationProvenance period-specific reservoir, availability and operating-limit basis
   * @param study exact runtime envelope study
   * @param search accepted mixed operating-point search from the same evaluator
   * @param slices one or more complete accepted-point slices from the same study
   * @return this study
   */
  public ProcessModelOperatingEnvelopeLifecycleStudy addPeriod(String periodId, String periodName, double elapsedTime,
      String configurationProvenance, ProcessModelOperatingEnvelopeStudy study, OperatingPointSearchResult search,
      SliceResult... slices) {
    return addPeriod(periodId, periodName, elapsedTime, configurationProvenance,
        Collections.<AcceptedStateMetric>emptyList(), study, search, slices);
  }

  /**
   * Adds one completely validated period together with accepted physical-state evidence.
   *
   * <p>
   * State metrics describe the independently accepted reservoir, network, host, or shared-resource state used to define
   * this period. Metric identifiers, ordering, and units must remain identical across every period. Values must be
   * finite. This method does not rerun or qualify the source physical model; callers must supply only values from an
   * already accepted state and retain that source in each metric's provenance.
   * </p>
   *
   * @param periodId unique stable period identifier
   * @param periodName human-readable period name
   * @param elapsedTime non-negative elapsed time from the lifecycle origin
   * @param configurationProvenance period-specific reservoir, availability and operating-limit basis
   * @param acceptedStateMetrics ordered accepted physical-state metrics
   * @param study exact runtime envelope study
   * @param search accepted mixed operating-point search from the same evaluator
   * @param slices one or more complete accepted-point slices from the same study
   * @return this study
   */
  public ProcessModelOperatingEnvelopeLifecycleStudy addPeriod(String periodId, String periodName, double elapsedTime,
      String configurationProvenance, List<AcceptedStateMetric> acceptedStateMetrics,
      ProcessModelOperatingEnvelopeStudy study, OperatingPointSearchResult search, SliceResult... slices) {
    String validatedId = requireText(periodId, "Lifecycle period identifier");
    String validatedName = requireText(periodName, "Lifecycle period name");
    String validatedConfiguration = requireText(configurationProvenance, "Lifecycle period configuration provenance");
    if (!Double.isFinite(elapsedTime) || elapsedTime < 0.0) {
      throw new IllegalArgumentException("Lifecycle elapsed time must be finite and non-negative");
    }
    for (PeriodEvidence period : periods) {
      if (period.getPeriodId().equals(validatedId)) {
        throw new IllegalArgumentException("Duplicate lifecycle period identifier: " + validatedId);
      }
    }
    if (!periods.isEmpty() && elapsedTime <= periods.get(periods.size() - 1).getElapsedTime()) {
      throw new IllegalArgumentException("Lifecycle elapsed time must increase strictly between periods");
    }
    if (study == null) {
      throw new IllegalArgumentException("Lifecycle period study must not be null");
    }
    Map<String, Object> envelopeEvidence = study.getAcceptedPointEnvelopeEvidence(validatedId, search, slices);
    LeadingConstraintEvidence bottleneck = study.getAcceptedPointBottleneck(search);
    List<String> actionIds = stringList(envelopeEvidence.get("accepted_action_ids"), "accepted action identifiers");
    if (acceptedActionIds != null && !acceptedActionIds.equals(actionIds)) {
      throw new IllegalStateException("Lifecycle periods must retain identical ordered action identifiers");
    }
    Map<String, Object> frozenEnvelopeEvidence = freezeMap(envelopeEvidence);
    List<AcceptedStateMetric> stateMetrics = validateStateMetrics(acceptedStateMetrics);
    if (acceptedActionIds == null) {
      acceptedActionIds = actionIds;
    }

    PeriodEvidence period = new PeriodEvidence(validatedId, validatedName, elapsedTime, timeUnit,
        validatedConfiguration, study.getId(), study.getCandidateEvaluator().getId(), bottleneck,
        frozenEnvelopeEvidence, search.getAcceptedCandidateValues(), stateMetrics, slices);
    periods.add(period);
    return this;
  }

  /** @return immutable snapshot of accepted lifecycle periods */
  public List<PeriodEvidence> getPeriods() {
    return Collections.unmodifiableList(new ArrayList<PeriodEvidence>(periods));
  }

  /**
   * Reports changes in the accepted leading constraint between adjacent periods.
   *
   * @return immutable ordered bottleneck-transition list
   */
  public List<LifecycleBottleneckTransition> getBottleneckTransitions() {
    List<LifecycleBottleneckTransition> transitions = new ArrayList<LifecycleBottleneckTransition>();
    for (int index = 1; index < periods.size(); index++) {
      PeriodEvidence from = periods.get(index - 1);
      PeriodEvidence to = periods.get(index);
      if (!sameText(from.getBottleneckQualifiedConstraint(), to.getBottleneckQualifiedConstraint())) {
        transitions.add(new LifecycleBottleneckTransition(from.getPeriodId(), to.getPeriodId(), from.getElapsedTime(),
            to.getElapsedTime(), timeUnit, from.getBottleneckQualifiedConstraint(),
            to.getBottleneckQualifiedConstraint()));
      }
    }
    return Collections.unmodifiableList(transitions);
  }

  /**
   * Reports exact sampled changes between each pair of adjacent lifecycle periods.
   *
   * <p>
   * Diagnostics compare accepted action values and slices with identical ordered action axes. Points are comparable
   * only when their physical axis-value vectors are exactly equal. Added or removed grids remain explicit rather than
   * being interpolated. The diagnostics therefore describe only the sampled evidence and do not infer a continuous
   * envelope boundary or causal constraint change.
   * </p>
   *
   * @return deeply immutable ordered period-change diagnostics
   */
  public List<Map<String, Object>> getPeriodChangeDiagnostics() {
    List<Map<String, Object>> diagnostics = new ArrayList<Map<String, Object>>();
    for (int index = 1; index < periods.size(); index++) {
      diagnostics.add(periodChange(periods.get(index - 1), periods.get(index)));
    }
    return Collections.unmodifiableList(diagnostics);
  }

  /**
   * Builds deeply immutable machine-readable lifecycle evidence without rerunning any process model.
   *
   * @param cycleId current living-task or engineering-cycle identifier
   * @return deeply immutable JSON-friendly lifecycle evidence
   */
  public Map<String, Object> toEvidenceMap(String cycleId) {
    String currentCycleId = requireText(cycleId, "Cycle identifier");
    if (periods.isEmpty()) {
      throw new IllegalStateException("Lifecycle evidence requires at least one validated period");
    }

    List<Map<String, Object>> periodMaps = new ArrayList<Map<String, Object>>();
    long evaluationCount = 0L;
    double runtimeSeconds = 0.0;
    int requestedPointCount = 0;
    int completedPointCount = 0;
    for (PeriodEvidence period : periods) {
      periodMaps.add(period.toEvidenceMap());
      Map<String, Object> envelope = period.getEnvelopeEvidence();
      evaluationCount = Math.addExact(evaluationCount,
          number(envelope.get("total_evaluation_count"), "total evaluation count").longValue());
      runtimeSeconds += number(envelope.get("envelope_runtime_seconds"), "envelope runtime").doubleValue();
      requestedPointCount = Math.addExact(requestedPointCount,
          number(envelope.get("requested_point_count"), "requested point count").intValue());
      completedPointCount = Math.addExact(completedPointCount,
          number(envelope.get("completed_point_count"), "completed point count").intValue());
    }
    if (!Double.isFinite(runtimeSeconds)) {
      throw new IllegalStateException("Lifecycle runtime evidence is not finite");
    }

    List<Map<String, Object>> transitionMaps = new ArrayList<Map<String, Object>>();
    for (LifecycleBottleneckTransition transition : getBottleneckTransitions()) {
      transitionMaps.add(transition.toEvidenceMap());
    }
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    boolean hasStateMetrics = acceptedStateMetricIds != null && !acceptedStateMetricIds.isEmpty();
    evidence.put("schema_version", Integer.valueOf(hasStateMetrics ? 3 : 2));
    evidence.put("cycle_id", currentCycleId);
    evidence.put("lifecycle_study_id", id);
    evidence.put("lifecycle_study_name", name);
    evidence.put("lifecycle_study_provenance", provenance);
    evidence.put("time_unit", timeUnit);
    evidence.put("period_count", Integer.valueOf(periods.size()));
    evidence.put("accepted_action_ids", Collections.unmodifiableList(new ArrayList<String>(acceptedActionIds)));
    if (hasStateMetrics) {
      evidence.put("accepted_state_metric_ids",
          Collections.unmodifiableList(new ArrayList<String>(acceptedStateMetricIds)));
    }
    evidence.put("requested_point_count", Integer.valueOf(requestedPointCount));
    evidence.put("completed_point_count", Integer.valueOf(completedPointCount));
    evidence.put("total_evaluation_count", Long.valueOf(evaluationCount));
    evidence.put("envelope_runtime_seconds", Double.valueOf(runtimeSeconds));
    evidence.put("periods", Collections.unmodifiableList(periodMaps));
    evidence.put("bottleneck_transitions", Collections.unmodifiableList(transitionMaps));
    evidence.put("period_change_diagnostics", getPeriodChangeDiagnostics());
    evidence.put("advisory", Boolean.TRUE);
    evidence.put("interpretation",
        "Independent sampled lifecycle periods; not a continuous forecast, equipment qualification, shadow price, or operating approval");
    return Collections.unmodifiableMap(evidence);
  }

  /** Immutable accepted physical-state metric for one lifecycle period. */
  public static final class AcceptedStateMetric implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String name;
    private final double value;
    private final String unit;
    private final String provenance;

    /**
     * Creates one accepted physical-state metric.
     *
     * @param id stable metric identifier retained across periods
     * @param name human-readable metric name
     * @param value finite accepted-state value
     * @param unit explicit engineering unit
     * @param provenance accepted calculation, fixture, or evidence source
     */
    public AcceptedStateMetric(String id, String name, double value, String unit, String provenance) {
      this.id = requireText(id, "Accepted-state metric identifier");
      this.name = requireText(name, "Accepted-state metric name");
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Accepted-state metric value must be finite");
      }
      this.value = value;
      this.unit = requireText(unit, "Accepted-state metric unit");
      this.provenance = requireText(provenance, "Accepted-state metric provenance");
    }

    /** @return stable metric identifier */
    public String getId() {
      return id;
    }

    /** @return human-readable metric name */
    public String getName() {
      return name;
    }

    /** @return finite accepted-state value */
    public double getValue() {
      return value;
    }

    /** @return explicit engineering unit */
    public String getUnit() {
      return unit;
    }

    /** @return accepted calculation, fixture, or evidence source */
    public String getProvenance() {
      return provenance;
    }

    /** @return immutable JSON-friendly metric evidence */
    public Map<String, Object> toEvidenceMap() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("metric_id", id);
      evidence.put("metric_name", name);
      evidence.put("value", Double.valueOf(value));
      evidence.put("unit", unit);
      evidence.put("provenance", provenance);
      return Collections.unmodifiableMap(evidence);
    }
  }

  /** Immutable accepted-period evidence. */
  public static final class PeriodEvidence implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String periodId;
    private final String periodName;
    private final double elapsedTime;
    private final String timeUnit;
    private final String configurationProvenance;
    private final String envelopeStudyId;
    private final String candidateEvaluatorId;
    private final LeadingConstraintEvidence acceptedBottleneck;
    private final Map<String, Object> envelopeEvidence;
    private final double[] acceptedCandidateValues;
    private final List<AcceptedStateMetric> acceptedStateMetrics;
    private final List<SliceResult> slices;

    private PeriodEvidence(String periodId, String periodName, double elapsedTime, String timeUnit,
        String configurationProvenance, String envelopeStudyId, String candidateEvaluatorId,
        LeadingConstraintEvidence acceptedBottleneck, Map<String, Object> envelopeEvidence,
        double[] acceptedCandidateValues, List<AcceptedStateMetric> acceptedStateMetrics, SliceResult[] slices) {
      this.periodId = periodId;
      this.periodName = periodName;
      this.elapsedTime = elapsedTime;
      this.timeUnit = timeUnit;
      this.configurationProvenance = configurationProvenance;
      this.envelopeStudyId = envelopeStudyId;
      this.candidateEvaluatorId = candidateEvaluatorId;
      this.acceptedBottleneck = acceptedBottleneck;
      this.envelopeEvidence = envelopeEvidence;
      this.acceptedCandidateValues = Arrays.copyOf(acceptedCandidateValues, acceptedCandidateValues.length);
      this.acceptedStateMetrics = Collections
          .unmodifiableList(new ArrayList<AcceptedStateMetric>(acceptedStateMetrics));
      this.slices = Collections.unmodifiableList(new ArrayList<SliceResult>(Arrays.asList(slices)));
    }

    /** @return stable period identifier */
    public String getPeriodId() {
      return periodId;
    }

    /** @return human-readable period name */
    public String getPeriodName() {
      return periodName;
    }

    /** @return elapsed lifecycle time */
    public double getElapsedTime() {
      return elapsedTime;
    }

    /** @return elapsed-time unit */
    public String getTimeUnit() {
      return timeUnit;
    }

    /** @return period-specific configuration provenance */
    public String getConfigurationProvenance() {
      return configurationProvenance;
    }

    /** @return envelope-study identifier */
    public String getEnvelopeStudyId() {
      return envelopeStudyId;
    }

    /** @return candidate-evaluator identifier */
    public String getCandidateEvaluatorId() {
      return candidateEvaluatorId;
    }

    /** @return accepted leading constraint, or null when none is applicable */
    public LeadingConstraintEvidence getAcceptedBottleneck() {
      return acceptedBottleneck;
    }

    /** @return accepted leading qualified constraint, or null */
    public String getBottleneckQualifiedConstraint() {
      return acceptedBottleneck == null ? null : acceptedBottleneck.getQualifiedConstraintName();
    }

    /** @return deeply immutable accepted-point envelope evidence */
    public Map<String, Object> getEnvelopeEvidence() {
      return envelopeEvidence;
    }

    /** @return immutable accepted physical-state metrics */
    public List<AcceptedStateMetric> getAcceptedStateMetrics() {
      return acceptedStateMetrics;
    }

    /** @return defensive accepted action-value vector */
    private double[] getAcceptedCandidateValues() {
      return Arrays.copyOf(acceptedCandidateValues, acceptedCandidateValues.length);
    }

    /** @return immutable exact-runtime slice list */
    private List<SliceResult> getSlices() {
      return slices;
    }

    /** @return deeply immutable JSON-friendly period evidence */
    public Map<String, Object> toEvidenceMap() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("period_id", periodId);
      evidence.put("period_name", periodName);
      evidence.put("elapsed_time", Double.valueOf(elapsedTime));
      evidence.put("time_unit", timeUnit);
      evidence.put("configuration_provenance", configurationProvenance);
      evidence.put("envelope_study_id", envelopeStudyId);
      evidence.put("candidate_evaluator_id", candidateEvaluatorId);
      evidence.put("accepted_bottleneck",
          acceptedBottleneck == null ? null : freezeMap(acceptedBottleneck.toEvidenceMap()));
      if (!acceptedStateMetrics.isEmpty()) {
        List<Map<String, Object>> metrics = new ArrayList<Map<String, Object>>();
        for (AcceptedStateMetric metric : acceptedStateMetrics) {
          metrics.add(metric.toEvidenceMap());
        }
        evidence.put("accepted_state_metrics", Collections.unmodifiableList(metrics));
      }
      evidence.put("envelope_evidence", envelopeEvidence);
      return Collections.unmodifiableMap(evidence);
    }
  }

  /** Builds one immutable adjacent-period sampled-change diagnostic. */
  private Map<String, Object> periodChange(PeriodEvidence from, PeriodEvidence to) {
    List<Map<String, Object>> actionChanges = new ArrayList<Map<String, Object>>();
    double[] fromValues = from.getAcceptedCandidateValues();
    double[] toValues = to.getAcceptedCandidateValues();
    for (int index = 0; index < acceptedActionIds.size(); index++) {
      Map<String, Object> change = new LinkedHashMap<String, Object>();
      change.put("action_id", acceptedActionIds.get(index));
      change.put("from_value", Double.valueOf(fromValues[index]));
      change.put("to_value", Double.valueOf(toValues[index]));
      change.put("delta", Double.valueOf(toValues[index] - fromValues[index]));
      actionChanges.add(Collections.unmodifiableMap(change));
    }

    List<List<String>> removedAxes = new ArrayList<List<String>>();
    List<List<String>> addedAxes = new ArrayList<List<String>>();
    List<Map<String, Object>> sliceChanges = new ArrayList<Map<String, Object>>();
    for (SliceResult fromSlice : from.getSlices()) {
      SliceResult toSlice = findSlice(to.getSlices(), fromSlice.getAxisActionIds());
      if (toSlice == null) {
        removedAxes.add(axisList(fromSlice.getAxisActionIds()));
      } else {
        sliceChanges.add(sliceChange(fromSlice, toSlice));
      }
    }
    for (SliceResult toSlice : to.getSlices()) {
      if (findSlice(from.getSlices(), toSlice.getAxisActionIds()) == null) {
        addedAxes.add(axisList(toSlice.getAxisActionIds()));
      }
    }

    Map<String, Object> diagnostic = new LinkedHashMap<String, Object>();
    diagnostic.put("from_period_id", from.getPeriodId());
    diagnostic.put("to_period_id", to.getPeriodId());
    diagnostic.put("from_elapsed_time", Double.valueOf(from.getElapsedTime()));
    diagnostic.put("to_elapsed_time", Double.valueOf(to.getElapsedTime()));
    diagnostic.put("time_unit", timeUnit);
    diagnostic.put("accepted_action_changes", Collections.unmodifiableList(actionChanges));
    if (!from.getAcceptedStateMetrics().isEmpty()) {
      diagnostic.put("accepted_state_changes", acceptedStateChanges(from, to));
    }
    diagnostic.put("comparable_slice_count", Integer.valueOf(sliceChanges.size()));
    diagnostic.put("removed_slice_axes", Collections.unmodifiableList(removedAxes));
    diagnostic.put("added_slice_axes", Collections.unmodifiableList(addedAxes));
    diagnostic.put("slice_changes", Collections.unmodifiableList(sliceChanges));
    diagnostic.put("advisory", Boolean.TRUE);
    return Collections.unmodifiableMap(diagnostic);
  }

  /** Builds exact accepted physical-state deltas for one adjacent period pair. */
  private static List<Map<String, Object>> acceptedStateChanges(PeriodEvidence from, PeriodEvidence to) {
    List<Map<String, Object>> changes = new ArrayList<Map<String, Object>>();
    List<AcceptedStateMetric> fromMetrics = from.getAcceptedStateMetrics();
    List<AcceptedStateMetric> toMetrics = to.getAcceptedStateMetrics();
    for (int index = 0; index < fromMetrics.size(); index++) {
      AcceptedStateMetric fromMetric = fromMetrics.get(index);
      AcceptedStateMetric toMetric = toMetrics.get(index);
      Map<String, Object> change = new LinkedHashMap<String, Object>();
      change.put("metric_id", fromMetric.getId());
      change.put("unit", fromMetric.getUnit());
      change.put("from_value", Double.valueOf(fromMetric.getValue()));
      change.put("to_value", Double.valueOf(toMetric.getValue()));
      change.put("delta", Double.valueOf(toMetric.getValue() - fromMetric.getValue()));
      changes.add(Collections.unmodifiableMap(change));
    }
    return Collections.unmodifiableList(changes);
  }

  /** Builds one exact sampled-grid comparison for matching slice axes. */
  private static Map<String, Object> sliceChange(SliceResult from, SliceResult to) {
    int comparable = 0;
    int becameFeasible = 0;
    int becameInfeasible = 0;
    int constraintChanges = 0;
    List<ProcessModelOperatingEnvelopeStudy.Point> fromPoints = from.getPoints();
    List<ProcessModelOperatingEnvelopeStudy.Point> toPoints = to.getPoints();
    for (ProcessModelOperatingEnvelopeStudy.Point fromPoint : fromPoints) {
      ProcessModelOperatingEnvelopeStudy.Point toPoint = findPoint(toPoints, fromPoint.getAxisValues());
      if (toPoint == null) {
        continue;
      }
      comparable++;
      if (!fromPoint.isFeasible() && toPoint.isFeasible()) {
        becameFeasible++;
      } else if (fromPoint.isFeasible() && !toPoint.isFeasible()) {
        becameInfeasible++;
      }
      if (!sameText(constraintName(fromPoint), constraintName(toPoint))) {
        constraintChanges++;
      }
    }

    Map<String, Object> change = new LinkedHashMap<String, Object>();
    change.put("axis_action_ids", axisList(from.getAxisActionIds()));
    change.put("grid_identical", Boolean.valueOf(sameGrid(from.getAxisValues(), to.getAxisValues())));
    change.put("from_requested_point_count", Integer.valueOf(from.getRequestedPointCount()));
    change.put("to_requested_point_count", Integer.valueOf(to.getRequestedPointCount()));
    int fromFeasible = from.getFeasiblePoints().size();
    int toFeasible = to.getFeasiblePoints().size();
    change.put("from_feasible_point_count", Integer.valueOf(fromFeasible));
    change.put("to_feasible_point_count", Integer.valueOf(toFeasible));
    change.put("feasible_point_count_delta", Integer.valueOf(toFeasible - fromFeasible));
    change.put("comparable_point_count", Integer.valueOf(comparable));
    change.put("from_only_point_count", Integer.valueOf(fromPoints.size() - comparable));
    change.put("to_only_point_count", Integer.valueOf(toPoints.size() - comparable));
    change.put("became_feasible_count", Integer.valueOf(becameFeasible));
    change.put("became_infeasible_count", Integer.valueOf(becameInfeasible));
    change.put("leading_constraint_change_count", Integer.valueOf(constraintChanges));
    change.put("from_bottleneck_transition_count", Integer.valueOf(from.getBottleneckTransitions().size()));
    change.put("to_bottleneck_transition_count", Integer.valueOf(to.getBottleneckTransitions().size()));
    return Collections.unmodifiableMap(change);
  }

  /** Finds the slice with one exact ordered action-axis signature. */
  private static SliceResult findSlice(List<SliceResult> slices, String[] actionIds) {
    for (SliceResult slice : slices) {
      if (Arrays.equals(slice.getAxisActionIds(), actionIds)) {
        return slice;
      }
    }
    return null;
  }

  /** Finds a sampled point with one exact physical axis-value vector. */
  private static ProcessModelOperatingEnvelopeStudy.Point findPoint(
      List<ProcessModelOperatingEnvelopeStudy.Point> points, double[] axisValues) {
    for (ProcessModelOperatingEnvelopeStudy.Point point : points) {
      if (sameVector(point.getAxisValues(), axisValues)) {
        return point;
      }
    }
    return null;
  }

  /** Returns the sampled leading-constraint identity, or null. */
  private static String constraintName(ProcessModelOperatingEnvelopeStudy.Point point) {
    LeadingConstraintEvidence evidence = point.getLeadingConstraint();
    return evidence == null ? null : evidence.getQualifiedConstraintName();
  }

  /** Copies one action-axis signature into an immutable list. */
  private static List<String> axisList(String[] actionIds) {
    return Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(actionIds)));
  }

  /** Returns true only when two sampled grids are bitwise identical. */
  private static boolean sameGrid(double[][] first, double[][] second) {
    if (first.length != second.length) {
      return false;
    }
    for (int index = 0; index < first.length; index++) {
      if (!sameVector(first[index], second[index])) {
        return false;
      }
    }
    return true;
  }

  /** Returns true only when two finite vectors are bitwise identical. */
  private static boolean sameVector(double[] first, double[] second) {
    if (first.length != second.length) {
      return false;
    }
    for (int index = 0; index < first.length; index++) {
      if (Double.doubleToLongBits(first[index]) != Double.doubleToLongBits(second[index])) {
        return false;
      }
    }
    return true;
  }

  /** Immutable adjacent-period bottleneck identity change. */
  public static final class LifecycleBottleneckTransition implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String fromPeriodId;
    private final String toPeriodId;
    private final double fromElapsedTime;
    private final double toElapsedTime;
    private final String timeUnit;
    private final String fromConstraint;
    private final String toConstraint;

    private LifecycleBottleneckTransition(String fromPeriodId, String toPeriodId, double fromElapsedTime,
        double toElapsedTime, String timeUnit, String fromConstraint, String toConstraint) {
      this.fromPeriodId = fromPeriodId;
      this.toPeriodId = toPeriodId;
      this.fromElapsedTime = fromElapsedTime;
      this.toElapsedTime = toElapsedTime;
      this.timeUnit = timeUnit;
      this.fromConstraint = fromConstraint;
      this.toConstraint = toConstraint;
    }

    /** @return source period identifier */
    public String getFromPeriodId() {
      return fromPeriodId;
    }

    /** @return destination period identifier */
    public String getToPeriodId() {
      return toPeriodId;
    }

    /** @return source qualified leading constraint, or null */
    public String getFromConstraint() {
      return fromConstraint;
    }

    /** @return destination qualified leading constraint, or null */
    public String getToConstraint() {
      return toConstraint;
    }

    /** @return JSON-friendly transition evidence */
    public Map<String, Object> toEvidenceMap() {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("from_period_id", fromPeriodId);
      evidence.put("to_period_id", toPeriodId);
      evidence.put("from_elapsed_time", Double.valueOf(fromElapsedTime));
      evidence.put("to_elapsed_time", Double.valueOf(toElapsedTime));
      evidence.put("time_unit", timeUnit);
      evidence.put("from_constraint", fromConstraint);
      evidence.put("to_constraint", toConstraint);
      return Collections.unmodifiableMap(evidence);
    }
  }

  /** Validates metric identity, ordering, units, and uniqueness across lifecycle periods. */
  private List<AcceptedStateMetric> validateStateMetrics(List<AcceptedStateMetric> metrics) {
    if (metrics == null) {
      throw new IllegalArgumentException("Accepted-state metrics must not be null");
    }
    List<AcceptedStateMetric> copy = new ArrayList<AcceptedStateMetric>();
    List<String> ids = new ArrayList<String>();
    List<String> units = new ArrayList<String>();
    for (AcceptedStateMetric metric : metrics) {
      if (metric == null) {
        throw new IllegalArgumentException("Accepted-state metrics must not contain null entries");
      }
      if (ids.contains(metric.getId())) {
        throw new IllegalArgumentException("Duplicate accepted-state metric identifier: " + metric.getId());
      }
      ids.add(metric.getId());
      units.add(metric.getUnit());
      copy.add(metric);
    }
    if (acceptedStateMetricIds == null) {
      acceptedStateMetricIds = Collections.unmodifiableList(new ArrayList<String>(ids));
      acceptedStateMetricUnits = Collections.unmodifiableList(new ArrayList<String>(units));
    } else if (!acceptedStateMetricIds.equals(ids) || !acceptedStateMetricUnits.equals(units)) {
      throw new IllegalStateException(
          "Lifecycle periods must retain identical ordered accepted-state metric identifiers and units");
    }
    return Collections.unmodifiableList(copy);
  }

  /** Deep-freezes a JSON-friendly map and rejects non-finite numeric values. */
  private static Map<String, Object> freezeMap(Map<?, ?> source) {
    Map<String, Object> frozen = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (!(entry.getKey() instanceof String)) {
        throw new IllegalStateException("Lifecycle evidence contains a non-string map key");
      }
      frozen.put((String) entry.getKey(), freezeValue(entry.getValue()));
    }
    return Collections.unmodifiableMap(frozen);
  }

  /** Deep-freezes one JSON-friendly value. */
  private static Object freezeValue(Object value) {
    if (value == null || value instanceof String || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Number) {
      if ((value instanceof Double || value instanceof Float) && !Double.isFinite(((Number) value).doubleValue())) {
        throw new IllegalStateException("Lifecycle evidence contains a non-finite numeric value");
      }
      return value;
    }
    if (value instanceof Map<?, ?>) {
      return freezeMap((Map<?, ?>) value);
    }
    if (value instanceof List<?>) {
      List<Object> frozen = new ArrayList<Object>();
      for (Object item : (List<?>) value) {
        frozen.add(freezeValue(item));
      }
      return Collections.unmodifiableList(frozen);
    }
    throw new IllegalStateException("Lifecycle evidence contains an unsupported value type: " + value.getClass());
  }

  /** Validates and copies a string list. */
  private static List<String> stringList(Object value, String name) {
    if (!(value instanceof List<?>)) {
      throw new IllegalStateException("Lifecycle period is missing " + name);
    }
    List<String> result = new ArrayList<String>();
    for (Object item : (List<?>) value) {
      result.add(requireText(item instanceof String ? (String) item : null, name));
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("Lifecycle period requires at least one " + name);
    }
    return Collections.unmodifiableList(result);
  }

  /** Requires one numeric evidence value. */
  private static Number number(Object value, String name) {
    if (!(value instanceof Number)) {
      throw new IllegalStateException("Lifecycle period is missing " + name);
    }
    return (Number) value;
  }

  /** Requires non-empty trimmed text. */
  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  /** Null-safe text equality. */
  private static boolean sameText(String first, String second) {
    return first == null ? second == null : first.equals(second);
  }
}
