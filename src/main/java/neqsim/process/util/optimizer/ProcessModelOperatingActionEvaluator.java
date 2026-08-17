package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ActionState;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ApplicationResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.BottleneckStatus;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.EvaluationResult;

/**
 * Evaluates one reversible operating action against explicitly bound hydraulic capacity constraints.
 *
 * <p>
 * The evaluator runs the supplied {@link ProcessModel} at its current baseline, captures the action state, applies one
 * candidate, runs the configured {@link ProcessModelSimulationEvaluator}, snapshots the selected constraint evidence,
 * and restores and reruns the baseline before returning. The configured simulation evaluator must have no registered
 * parameters: this class owns the single candidate write so an optimizer parameter setter cannot silently overwrite the
 * captured baseline or prevent restoration of an existing off-domain brownfield state.
 * </p>
 *
 * <p>
 * Constraint bindings use exact area, equipment, and constraint names. Missing constraints, non-finite utilization,
 * violations, and operation outside an explicitly supplied validity range fail closed with distinct outcomes. An absent
 * validity range remains visible as not assessed and does not by itself reject a candidate. Evidence confidence is
 * retained as engineering-basis metadata; it is not interpreted as a probability of safe operation.
 * </p>
 *
 * <p>
 * This class composes existing well, gathering, and pipeline calculations. It does not add a hydraulic correlation,
 * infer routing changes, or provide mechanical, safety, or operating approval. The evaluator mutates and reruns the
 * supplied model during a call, so {@link #evaluate(double)} is synchronized. Independent model instances are required
 * for parallel candidate evaluation.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelOperatingActionEvaluator {
  /** Classification of the hydraulic constraint represented by a binding. */
  public enum HydraulicLimitRole {
    /** Reservoir deliverability or pressure-depletion limit. */
    RESERVOIR_DELIVERABILITY,
    /** Well inflow, outflow, bottom-hole pressure, or drawdown limit. */
    WELL_INFLOW_OUTFLOW,
    /** Manifold, flowline, or gathering-system hydraulic limit. */
    GATHERING_HYDRAULICS,
    /** Riser, transport-pipeline, or export-pipeline hydraulic limit. */
    PIPELINE_HYDRAULICS
  }

  /** Fail-closed outcome for one candidate and subsequent baseline restoration. */
  public enum Outcome {
    /** Candidate converged, satisfied the evaluator, and passed every required hydraulic constraint. */
    FEASIBLE,
    /** Current model state could not establish a converged baseline. */
    BASELINE_SIMULATION_FAILED,
    /** Action target could not be captured at the converged baseline. */
    BASELINE_ACTION_UNAVAILABLE,
    /** Candidate was outside the action domain or its automation write could not be verified. */
    ACTION_REJECTED,
    /** Candidate process simulation did not converge or raised an error. */
    CANDIDATE_SIMULATION_FAILED,
    /** A required exact hydraulic capacity constraint was not present. */
    REQUIRED_CONSTRAINT_MISSING,
    /** A required hydraulic constraint produced a non-finite value or utilization. */
    CONSTRAINT_VALUE_UNAVAILABLE,
    /** At least one required hydraulic constraint exceeded its limit. */
    HYDRAULIC_CONSTRAINT_VIOLATED,
    /** A required constraint was sampled outside its explicitly documented validity range. */
    EVIDENCE_OUTSIDE_VALIDITY_RANGE,
    /** The configured evaluator rejected another hard process constraint. */
    OTHER_MODEL_CONSTRAINT_VIOLATED,
    /** The captured action value could not be restored and verified. */
    RESTORATION_FAILED,
    /** The action value was restored, but the baseline model did not reconverge. */
    RESTORED_BASELINE_SIMULATION_FAILED
  }

  /** Configured process-model evaluator. */
  private final ProcessModelSimulationEvaluator evaluator;

  /** Reversible operating action under evaluation. */
  private final ProcessModelOperatingAction action;

  /** Exact required hydraulic constraints in declaration order. */
  private final List<HydraulicConstraintBinding> bindings = new ArrayList<HydraulicConstraintBinding>();

  /**
   * Creates a transactional action evaluator and registers all enabled equipment capacities with the supplied
   * simulation evaluator.
   *
   * @param evaluator configured evaluator with objectives, optional non-equipment constraints, and no parameters
   * @param action reversible operating action
   * @throws IllegalArgumentException if either argument is null
   * @throws IllegalStateException if the evaluator already has parameters or has no process model
   */
  public ProcessModelOperatingActionEvaluator(ProcessModelSimulationEvaluator evaluator,
      ProcessModelOperatingAction action) {
    if (evaluator == null) {
      throw new IllegalArgumentException("Process-model simulation evaluator must not be null");
    }
    if (action == null) {
      throw new IllegalArgumentException("Process-model operating action must not be null");
    }
    if (evaluator.getProcessModel() == null) {
      throw new IllegalStateException("Process-model simulation evaluator must contain a process model");
    }
    if (evaluator.getParameterCount() != 0) {
      throw new IllegalStateException(
          "Transactional operating-action evaluation requires an evaluator with no registered parameters");
    }
    this.evaluator = evaluator;
    this.action = action;
    evaluator.addEquipmentCapacityConstraints();
  }

  /** @return configured operating action */
  public ProcessModelOperatingAction getAction() {
    return action;
  }

  /** @return underlying configured simulation evaluator */
  public ProcessModelSimulationEvaluator getSimulationEvaluator() {
    return evaluator;
  }

  /**
   * Requires one exact hydraulic capacity constraint for every candidate.
   *
   * @param role engineering role of the constraint
   * @param areaName exact process area name
   * @param equipmentName exact equipment name
   * @param constraintName exact capacity-constraint name
   * @param provenance source explaining why this constraint is required for the action
   * @return this evaluator for chaining
   */
  public ProcessModelOperatingActionEvaluator requireHydraulicConstraint(HydraulicLimitRole role, String areaName,
      String equipmentName, String constraintName, String provenance) {
    HydraulicConstraintBinding candidate = new HydraulicConstraintBinding(role, areaName, equipmentName, constraintName,
        provenance);
    for (HydraulicConstraintBinding binding : bindings) {
      if (binding.hasSameAddress(candidate)) {
        throw new IllegalArgumentException(
            "Hydraulic constraint binding is already registered: " + candidate.getQualifiedConstraintName());
      }
    }
    bindings.add(candidate);
    return this;
  }

  /**
   * Gets immutable required-constraint metadata in declaration order.
   *
   * @return immutable defensive binding list
   */
  public List<HydraulicConstraintBinding> getRequiredHydraulicConstraints() {
    return Collections.unmodifiableList(new ArrayList<HydraulicConstraintBinding>(bindings));
  }

  /**
   * Evaluates one candidate and restores and reruns the captured baseline before returning.
   *
   * @param candidateValue candidate in {@link ProcessModelOperatingAction#getUnit()}
   * @return immutable serializable candidate and restoration evidence
   */
  public synchronized CandidateEvaluationResult evaluate(double candidateValue) {
    ProcessModel model = evaluator.getProcessModel();
    List<String> diagnostics = new ArrayList<String>();
    if (bindings.isEmpty()) {
      diagnostics.add("At least one exact hydraulic capacity constraint must be required before evaluation");
      return CandidateEvaluationResult.empty(action, candidateValue, Outcome.REQUIRED_CONSTRAINT_MISSING, diagnostics);
    }

    try {
      model.run();
    } catch (RuntimeException exception) {
      diagnostics.add("Baseline process-model run failed: " + safeMessage(exception));
      return CandidateEvaluationResult.empty(action, candidateValue, Outcome.BASELINE_SIMULATION_FAILED, diagnostics);
    }
    if (!model.isModelConverged()) {
      diagnostics.add("Baseline process model did not report convergence");
      return CandidateEvaluationResult.empty(action, candidateValue, Outcome.BASELINE_SIMULATION_FAILED, diagnostics);
    }

    final ActionState baseline;
    try {
      baseline = action.capture(model);
    } catch (RuntimeException exception) {
      diagnostics.add("Baseline action state is unavailable: " + safeMessage(exception));
      return CandidateEvaluationResult.empty(action, candidateValue, Outcome.BASELINE_ACTION_UNAVAILABLE, diagnostics);
    }

    Outcome outcome = Outcome.ACTION_REJECTED;
    EvaluationResult candidateEvaluation = null;
    List<HydraulicConstraintSnapshot> constraintSnapshots = Collections.emptyList();
    ApplicationResult application = action.apply(model, candidateValue);
    diagnostics.add(application.getDiagnostic());
    if (application.isApplied()) {
      candidateEvaluation = evaluator.evaluate(new double[0]);
      if (!candidateEvaluation.isSimulationConverged()) {
        outcome = Outcome.CANDIDATE_SIMULATION_FAILED;
        diagnostics.add(candidateEvaluation.getErrorMessage() == null ? "Candidate process model did not converge"
            : "Candidate process-model evaluation failed: " + candidateEvaluation.getErrorMessage());
      } else {
        constraintSnapshots = snapshotRequiredConstraints(candidateEvaluation, diagnostics);
        outcome = classifyCandidate(candidateEvaluation, constraintSnapshots);
      }
    }

    ApplicationResult restoration = action.restore(model, baseline);
    boolean baselineSimulationConverged = false;
    boolean baselineRestored = restoration.isApplied();
    diagnostics.add(restoration.getDiagnostic());
    if (!restoration.isApplied()) {
      outcome = Outcome.RESTORATION_FAILED;
    } else {
      try {
        model.run();
        baselineSimulationConverged = model.isModelConverged();
      } catch (RuntimeException exception) {
        diagnostics.add("Restored baseline process-model run failed: " + safeMessage(exception));
      }
      if (!baselineSimulationConverged) {
        diagnostics.add("Captured action value was restored, but the baseline process model did not reconverge");
        outcome = Outcome.RESTORED_BASELINE_SIMULATION_FAILED;
      } else {
        diagnostics.add("Captured action value was restored and the baseline process model reconverged");
      }
    }

    return CandidateEvaluationResult.from(action, baseline.getValue(), candidateValue, outcome, candidateEvaluation,
        constraintSnapshots, baselineRestored, baselineSimulationConverged, diagnostics);
  }

  /** Creates immutable snapshots for each exact binding in declaration order. */
  private List<HydraulicConstraintSnapshot> snapshotRequiredConstraints(EvaluationResult evaluation,
      List<String> diagnostics) {
    List<HydraulicConstraintSnapshot> snapshots = new ArrayList<HydraulicConstraintSnapshot>();
    for (HydraulicConstraintBinding binding : bindings) {
      BottleneckStatus match = findExactConstraint(evaluation.getRankedCapacityConstraints(), binding);
      if (match == null) {
        snapshots.add(HydraulicConstraintSnapshot.missing(binding));
        diagnostics.add("Required hydraulic constraint is missing: " + binding.getQualifiedConstraintName());
      } else {
        HydraulicConstraintSnapshot snapshot = new HydraulicConstraintSnapshot(binding, match);
        snapshots.add(snapshot);
        if (!snapshot.hasFiniteValue()) {
          diagnostics.add(
              "Required hydraulic constraint returned non-finite evidence: " + binding.getQualifiedConstraintName());
        } else if (!snapshot.isFeasible()) {
          diagnostics.add("Required hydraulic constraint is violated: " + binding.getQualifiedConstraintName());
        } else if (snapshot
            .getEvidenceApplicability() == BottleneckStatus.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE) {
          diagnostics.add("Required hydraulic constraint is outside its evidence validity range: "
              + binding.getQualifiedConstraintName());
        }
      }
    }
    return Collections.unmodifiableList(snapshots);
  }

  /** Finds an exact area/equipment/constraint match. */
  private BottleneckStatus findExactConstraint(List<BottleneckStatus> available, HydraulicConstraintBinding binding) {
    for (BottleneckStatus status : available) {
      if (binding.getAreaName().equals(status.getAreaName())
          && binding.getEquipmentName().equals(status.getEquipmentName())
          && binding.getConstraintName().equals(status.getConstraintName())) {
        return status;
      }
    }
    return null;
  }

  /** Applies fail-closed outcome precedence after a converged candidate run. */
  private Outcome classifyCandidate(EvaluationResult evaluation, List<HydraulicConstraintSnapshot> snapshots) {
    for (HydraulicConstraintSnapshot snapshot : snapshots) {
      if (!snapshot.isPresent()) {
        return Outcome.REQUIRED_CONSTRAINT_MISSING;
      }
    }
    for (HydraulicConstraintSnapshot snapshot : snapshots) {
      if (!snapshot.hasFiniteValue()) {
        return Outcome.CONSTRAINT_VALUE_UNAVAILABLE;
      }
    }
    for (HydraulicConstraintSnapshot snapshot : snapshots) {
      if (!snapshot.isFeasible()) {
        return Outcome.HYDRAULIC_CONSTRAINT_VIOLATED;
      }
    }
    for (HydraulicConstraintSnapshot snapshot : snapshots) {
      if (snapshot.getEvidenceApplicability() == BottleneckStatus.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE) {
        return Outcome.EVIDENCE_OUTSIDE_VALIDITY_RANGE;
      }
    }
    return evaluation.isFeasible() ? Outcome.FEASIBLE : Outcome.OTHER_MODEL_CONSTRAINT_VIOLATED;
  }

  /** Returns a useful exception message. */
  private static String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.trim().isEmpty() ? exception.getClass().getSimpleName() : message;
  }

  /** Validates and trims required text. */
  private static String requireText(String value, String description) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(description + " must not be blank");
    }
    return value.trim();
  }

  /** Immutable exact hydraulic-constraint requirement. */
  public static final class HydraulicConstraintBinding implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Engineering role. */
    private final HydraulicLimitRole role;

    /** Exact process area. */
    private final String areaName;

    /** Exact equipment name. */
    private final String equipmentName;

    /** Exact constraint name. */
    private final String constraintName;

    /** Source explaining why the constraint is required. */
    private final String provenance;

    /** Creates a validated binding. */
    HydraulicConstraintBinding(HydraulicLimitRole role, String areaName, String equipmentName, String constraintName,
        String provenance) {
      if (role == null) {
        throw new IllegalArgumentException("Hydraulic limit role must not be null");
      }
      this.role = role;
      this.areaName = requireText(areaName, "Hydraulic constraint area name");
      this.equipmentName = requireText(equipmentName, "Hydraulic constraint equipment name");
      this.constraintName = requireText(constraintName, "Hydraulic constraint name");
      this.provenance = requireText(provenance, "Hydraulic constraint binding provenance");
    }

    /** @return engineering role */
    public HydraulicLimitRole getRole() {
      return role;
    }

    /** @return exact process area name */
    public String getAreaName() {
      return areaName;
    }

    /** @return exact equipment name */
    public String getEquipmentName() {
      return equipmentName;
    }

    /** @return exact capacity-constraint name */
    public String getConstraintName() {
      return constraintName;
    }

    /** @return source explaining why this constraint is required */
    public String getProvenance() {
      return provenance;
    }

    /** @return area-qualified equipment and constraint address */
    public String getQualifiedConstraintName() {
      return areaName + "::" + equipmentName + "/" + constraintName;
    }

    /** Checks exact address equality while allowing different explanatory provenance to be rejected as a duplicate. */
    boolean hasSameAddress(HydraulicConstraintBinding other) {
      return areaName.equals(other.areaName) && equipmentName.equals(other.equipmentName)
          && constraintName.equals(other.constraintName);
    }
  }

  /** Immutable hydraulic constraint evidence at one simulated candidate. */
  public static final class HydraulicConstraintSnapshot implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Immutable requirement metadata. */
    private final HydraulicConstraintBinding binding;

    /** Whether the exact constraint was present. */
    private final boolean present;

    /** Utilization fraction. */
    private final double utilization;

    /** Remaining normalized capacity margin. */
    private final double margin;

    /** Current engineering value. */
    private final double currentValue;

    /** Design or minimum limit. */
    private final double designValue;

    /** True when values below the limit are worse. */
    private final boolean minimumConstraint;

    /** Constraint unit. */
    private final String unit;

    /** Underlying equipment/design provenance. */
    private final String dataSource;

    /** Whether confidence is supplied. */
    private final boolean confidenceSet;

    /** Evidence confidence, or NaN. */
    private final double confidence;

    /** Whether a scalar validity range is supplied. */
    private final boolean validityRangeSet;

    /** Inclusive validity minimum. */
    private final double validityMinimum;

    /** Inclusive validity maximum. */
    private final double validityMaximum;

    /** Evidence applicability at the candidate. */
    private final BottleneckStatus.EvidenceApplicability evidenceApplicability;

    /** Capacity feasibility. */
    private final boolean feasible;

    /** Creates a present snapshot. */
    HydraulicConstraintSnapshot(HydraulicConstraintBinding binding, BottleneckStatus status) {
      this.binding = binding;
      present = true;
      utilization = status.getUtilization();
      margin = 1.0 - utilization;
      currentValue = status.getCurrentValue();
      designValue = status.getDesignValue();
      minimumConstraint = status.isMinimumConstraint();
      unit = status.getUnit();
      dataSource = status.getDataSource();
      confidenceSet = status.hasConfidence();
      confidence = status.getConfidence();
      validityRangeSet = status.hasValidityRange();
      validityMinimum = status.getValidityMinimum();
      validityMaximum = status.getValidityMaximum();
      evidenceApplicability = status.getEvidenceApplicability();
      feasible = status.isFeasible();
    }

    /** Creates explicit missing evidence. */
    private HydraulicConstraintSnapshot(HydraulicConstraintBinding binding) {
      this.binding = binding;
      present = false;
      utilization = Double.NaN;
      margin = Double.NaN;
      currentValue = Double.NaN;
      designValue = Double.NaN;
      minimumConstraint = false;
      unit = "";
      dataSource = "not_set";
      confidenceSet = false;
      confidence = Double.NaN;
      validityRangeSet = false;
      validityMinimum = Double.NaN;
      validityMaximum = Double.NaN;
      evidenceApplicability = BottleneckStatus.EvidenceApplicability.NOT_ASSESSED;
      feasible = false;
    }

    /** Creates missing evidence. */
    static HydraulicConstraintSnapshot missing(HydraulicConstraintBinding binding) {
      return new HydraulicConstraintSnapshot(binding);
    }

    /** @return immutable binding metadata */
    public HydraulicConstraintBinding getBinding() {
      return binding;
    }

    /** @return true when the exact constraint was present */
    public boolean isPresent() {
      return present;
    }

    /** @return utilization fraction, or NaN when unavailable */
    public double getUtilization() {
      return utilization;
    }

    /** @return remaining normalized capacity margin, or NaN when unavailable */
    public double getMargin() {
      return margin;
    }

    /** @return current engineering value, or NaN when unavailable */
    public double getCurrentValue() {
      return currentValue;
    }

    /** @return design or minimum limit, or NaN when unavailable */
    public double getDesignValue() {
      return designValue;
    }

    /** @return true when values below the reported limit are worse */
    public boolean isMinimumConstraint() {
      return minimumConstraint;
    }

    /** @return constraint unit */
    public String getUnit() {
      return unit;
    }

    /** @return underlying equipment/design provenance */
    public String getDataSource() {
      return dataSource;
    }

    /** @return true when evidence confidence is supplied */
    public boolean hasConfidence() {
      return confidenceSet;
    }

    /** @return evidence confidence, or NaN when unavailable */
    public double getConfidence() {
      return confidenceSet ? confidence : Double.NaN;
    }

    /** @return true when a scalar validity range is supplied */
    public boolean hasValidityRange() {
      return validityRangeSet;
    }

    /** @return inclusive validity minimum, or NaN */
    public double getValidityMinimum() {
      return validityRangeSet ? validityMinimum : Double.NaN;
    }

    /** @return inclusive validity maximum, or NaN */
    public double getValidityMaximum() {
      return validityRangeSet ? validityMaximum : Double.NaN;
    }

    /** @return evidence applicability at this candidate */
    public BottleneckStatus.EvidenceApplicability getEvidenceApplicability() {
      return evidenceApplicability;
    }

    /** @return true when current value and utilization are finite */
    public boolean hasFiniteValue() {
      return present && Double.isFinite(currentValue) && Double.isFinite(utilization);
    }

    /** @return true when the exact capacity constraint is present and satisfied */
    public boolean isFeasible() {
      return present && feasible;
    }
  }

  /** Immutable, serializable candidate and restoration result suitable for JPype/Python access. */
  public static final class CandidateEvaluationResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Complete immutable action identity. */
    private final ProcessModelOperatingAction action;

    /** Captured baseline value. */
    private final double baselineValue;

    /** Requested candidate value. */
    private final double candidateValue;

    /** Fail-closed outcome. */
    private final Outcome outcome;

    /** Candidate convergence. */
    private final boolean candidateSimulationConverged;

    /** Candidate feasibility across all registered evaluator constraints. */
    private final boolean candidateEvaluatorFeasible;

    /** Baseline action read-back restoration. */
    private final boolean baselineRestored;

    /** Baseline reconvergence after restoration. */
    private final boolean baselineSimulationConverged;

    /** Raw objective values at the candidate. */
    private final double[] rawObjectives;

    /** Sign-adjusted minimizer objective values at the candidate. */
    private final double[] objectives;

    /** Registered constraint values at the candidate. */
    private final double[] constraintValues;

    /** Registered constraint margins at the candidate. */
    private final double[] constraintMargins;

    /** Required hydraulic snapshots in binding order. */
    private final List<HydraulicConstraintSnapshot> hydraulicConstraints;

    /** Qualified boundary evidence from the candidate operating point. */
    private final List<ProcessBoundaryConstraintEvidence> processBoundaryConstraintEvidence;

    /** Immutable diagnostics. */
    private final List<String> diagnostics;

    /** Creates an immutable result. */
    private CandidateEvaluationResult(ProcessModelOperatingAction action, double baselineValue, double candidateValue,
        Outcome outcome, boolean candidateSimulationConverged, boolean candidateEvaluatorFeasible,
        boolean baselineRestored, boolean baselineSimulationConverged, double[] rawObjectives, double[] objectives,
        double[] constraintValues, double[] constraintMargins, List<HydraulicConstraintSnapshot> hydraulicConstraints,
        List<ProcessBoundaryConstraintEvidence> processBoundaryConstraintEvidence, List<String> diagnostics) {
      this.action = action;
      this.baselineValue = baselineValue;
      this.candidateValue = candidateValue;
      this.outcome = outcome;
      this.candidateSimulationConverged = candidateSimulationConverged;
      this.candidateEvaluatorFeasible = candidateEvaluatorFeasible;
      this.baselineRestored = baselineRestored;
      this.baselineSimulationConverged = baselineSimulationConverged;
      this.rawObjectives = Arrays.copyOf(rawObjectives, rawObjectives.length);
      this.objectives = Arrays.copyOf(objectives, objectives.length);
      this.constraintValues = Arrays.copyOf(constraintValues, constraintValues.length);
      this.constraintMargins = Arrays.copyOf(constraintMargins, constraintMargins.length);
      this.hydraulicConstraints = Collections
          .unmodifiableList(new ArrayList<HydraulicConstraintSnapshot>(hydraulicConstraints));
      this.processBoundaryConstraintEvidence = Collections
          .unmodifiableList(new ArrayList<ProcessBoundaryConstraintEvidence>(processBoundaryConstraintEvidence));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** Creates a result without a candidate simulation. */
    private static CandidateEvaluationResult empty(ProcessModelOperatingAction action, double candidateValue,
        Outcome outcome, List<String> diagnostics) {
      return new CandidateEvaluationResult(action, Double.NaN, candidateValue, outcome, false, false, false, false,
          new double[0], new double[0], new double[0], new double[0],
          Collections.<HydraulicConstraintSnapshot>emptyList(),
          Collections.<ProcessBoundaryConstraintEvidence>emptyList(), diagnostics);
    }

    /** Creates a result from one optional candidate simulation. */
    private static CandidateEvaluationResult from(ProcessModelOperatingAction action, double baselineValue,
        double candidateValue, Outcome outcome, EvaluationResult evaluation,
        List<HydraulicConstraintSnapshot> hydraulicConstraints, boolean baselineRestored,
        boolean baselineSimulationConverged, List<String> diagnostics) {
      if (evaluation == null) {
        return new CandidateEvaluationResult(action, baselineValue, candidateValue, outcome, false, false,
            baselineRestored, baselineSimulationConverged, new double[0], new double[0], new double[0], new double[0],
            hydraulicConstraints, Collections.<ProcessBoundaryConstraintEvidence>emptyList(), diagnostics);
      }
      return new CandidateEvaluationResult(action, baselineValue, candidateValue, outcome,
          evaluation.isSimulationConverged(), evaluation.isFeasible(), baselineRestored, baselineSimulationConverged,
          copy(evaluation.getObjectivesRaw()), copy(evaluation.getObjectives()), copy(evaluation.getConstraintValues()),
          copy(evaluation.getConstraintMargins()), hydraulicConstraints,
          evaluation.getProcessBoundaryConstraintEvidence(), diagnostics);
    }

    /** Returns a defensive array or an empty array for null. */
    private static double[] copy(double[] values) {
      return values == null ? new double[0] : Arrays.copyOf(values, values.length);
    }

    /** @return complete immutable action definition */
    public ProcessModelOperatingAction getAction() {
      return action;
    }

    /** @return captured baseline value, or NaN when unavailable */
    public double getBaselineValue() {
      return baselineValue;
    }

    /** @return requested candidate value */
    public double getCandidateValue() {
      return candidateValue;
    }

    /** @return fail-closed outcome */
    public Outcome getOutcome() {
      return outcome;
    }

    /** @return true only for a fully evidenced feasible candidate */
    public boolean isFeasible() {
      return outcome == Outcome.FEASIBLE;
    }

    /** @return true when the candidate process model converged */
    public boolean isCandidateSimulationConverged() {
      return candidateSimulationConverged;
    }

    /** @return true when all registered evaluator constraints were feasible */
    public boolean isCandidateEvaluatorFeasible() {
      return candidateEvaluatorFeasible;
    }

    /** @return true when the action value was restored and verified */
    public boolean isBaselineRestored() {
      return baselineRestored;
    }

    /** @return true when the restored baseline process model reconverged */
    public boolean isBaselineSimulationConverged() {
      return baselineSimulationConverged;
    }

    /** @return defensive raw-objective array */
    public double[] getRawObjectives() {
      return Arrays.copyOf(rawObjectives, rawObjectives.length);
    }

    /** @return defensive sign-adjusted objective array */
    public double[] getObjectives() {
      return Arrays.copyOf(objectives, objectives.length);
    }

    /** @return defensive registered-constraint value array */
    public double[] getConstraintValues() {
      return Arrays.copyOf(constraintValues, constraintValues.length);
    }

    /** @return defensive registered-constraint margin array */
    public double[] getConstraintMargins() {
      return Arrays.copyOf(constraintMargins, constraintMargins.length);
    }

    /** @return fresh immutable snapshot of hydraulic evidence in binding order */
    public List<HydraulicConstraintSnapshot> getHydraulicConstraints() {
      return Collections.unmodifiableList(new ArrayList<HydraulicConstraintSnapshot>(hydraulicConstraints));
    }

    /** @return fresh immutable qualified boundary evidence in constraint registration order */
    public List<ProcessBoundaryConstraintEvidence> getProcessBoundaryConstraintEvidence() {
      return Collections
          .unmodifiableList(new ArrayList<ProcessBoundaryConstraintEvidence>(processBoundaryConstraintEvidence));
    }

    /** @return fresh immutable snapshot of diagnostics */
    public List<String> getDiagnostics() {
      return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }
  }
}
