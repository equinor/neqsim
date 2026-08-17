package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ActionState;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ApplicationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicConstraintBinding;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicConstraintSnapshot;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.BottleneckStatus;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ConstraintDefinition;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.EvaluationResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;

/**
 * Evaluates an ordered set of reversible operating actions as one steady-state transaction.
 *
 * <p>
 * Every action state is captured before the first write. Candidate values are then applied in declaration order. If a
 * later action is rejected, every captured action is restored in reverse order and the process-model evaluator is not
 * run at the partial candidate. A complete candidate is simulated once, exact required hydraulic constraints are
 * snapshotted, and every action is restored in reverse order before the baseline is rerun.
 * </p>
 *
 * <p>
 * The action-set identifier, name, provenance, ordered action definitions, per-action write/read-back evidence,
 * objective and constraint arrays, hydraulic and complete installed-capacity evidence, restoration status, and
 * diagnostics are retained in an immutable serializable result suitable for JPype/Python. Action identifiers and
 * automation addresses must be unique so one transaction cannot write the same control target twice under different
 * metadata.
 * </p>
 *
 * <p>
 * This class composes existing NeqSim actions, process runs, and equipment constraints. It does not solve allocation,
 * change routing or topology, add hydraulic correlations, or constitute operating or safety approval. Calls mutate the
 * supplied model and are synchronized; use independent {@link ProcessModel} instances for parallel candidates.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelOperatingActionSetEvaluator {
  /** Fail-closed outcome for a coupled candidate and baseline recovery. */
  public enum Outcome {
    /** Candidate converged, satisfied the evaluator, and passed every required hydraulic constraint. */
    FEASIBLE,
    /** Candidate vector was null or had the wrong length. */
    CANDIDATE_VECTOR_INVALID,
    /** Current model state could not establish a converged baseline. */
    BASELINE_SIMULATION_FAILED,
    /** At least one action target could not be captured at the converged baseline. */
    BASELINE_ACTION_UNAVAILABLE,
    /** At least one candidate was outside its action domain or failed verified read-back. */
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
    /** At least one captured action value could not be restored and verified. */
    RESTORATION_FAILED,
    /** All action values were restored, but the baseline model did not reconverge. */
    RESTORED_BASELINE_SIMULATION_FAILED
  }

  /** Stable action-set identifier. */
  private final String id;

  /** Human-readable action-set name. */
  private final String name;

  /** Engineering source for grouping these actions into one candidate. */
  private final String provenance;

  /** Configured process-model evaluator. */
  private final ProcessModelSimulationEvaluator evaluator;

  /** Immutable actions in declaration order. */
  private final List<ProcessModelOperatingAction> actions;

  /** Exact required hydraulic constraints in declaration order. */
  private final List<HydraulicConstraintBinding> bindings = new ArrayList<HydraulicConstraintBinding>();

  /**
   * Creates a coupled transactional evaluator and registers enabled equipment capacities.
   *
   * @param id stable action-set identifier
   * @param name human-readable action-set name
   * @param provenance engineering source for treating the actions as one candidate
   * @param evaluator configured evaluator with objectives, optional non-equipment constraints, and no parameters
   * @param actions non-empty ordered action list with unique IDs and automation addresses
   * @throws IllegalArgumentException for invalid metadata, actions, duplicate IDs, or duplicate addresses
   * @throws IllegalStateException if the evaluator has no model or already has parameters
   */
  public ProcessModelOperatingActionSetEvaluator(String id, String name, String provenance,
      ProcessModelSimulationEvaluator evaluator, List<ProcessModelOperatingAction> actions) {
    this.id = requireText(id, "Operating-action set identifier");
    this.name = requireText(name, "Operating-action set name");
    this.provenance = requireText(provenance, "Operating-action set provenance");
    if (evaluator == null) {
      throw new IllegalArgumentException("Process-model simulation evaluator must not be null");
    }
    if (evaluator.getProcessModel() == null) {
      throw new IllegalStateException("Process-model simulation evaluator must contain a process model");
    }
    if (evaluator.getParameterCount() != 0) {
      throw new IllegalStateException(
          "Transactional operating-action evaluation requires an evaluator with no registered parameters");
    }
    if (actions == null || actions.isEmpty()) {
      throw new IllegalArgumentException("Operating-action set must contain at least one action");
    }
    List<ProcessModelOperatingAction> validated = new ArrayList<ProcessModelOperatingAction>();
    Set<String> identifiers = new HashSet<String>();
    Set<String> addresses = new HashSet<String>();
    for (ProcessModelOperatingAction action : actions) {
      if (action == null) {
        throw new IllegalArgumentException("Operating-action set must not contain null actions");
      }
      if (!identifiers.add(action.getId())) {
        throw new IllegalArgumentException("Duplicate operating-action identifier: " + action.getId());
      }
      if (!addresses.add(action.getAddress())) {
        throw new IllegalArgumentException("Duplicate operating-action address: " + action.getAddress());
      }
      validated.add(action);
    }
    this.evaluator = evaluator;
    this.actions = Collections.unmodifiableList(validated);
    evaluator.addEquipmentCapacityConstraints();
  }

  /** @return stable action-set identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable action-set name */
  public String getName() {
    return name;
  }

  /** @return engineering source for grouping these actions */
  public String getProvenance() {
    return provenance;
  }

  /** @return fresh immutable action list in declaration order */
  public List<ProcessModelOperatingAction> getActions() {
    return Collections.unmodifiableList(new ArrayList<ProcessModelOperatingAction>(actions));
  }

  /** @return underlying configured simulation evaluator */
  public ProcessModelSimulationEvaluator getSimulationEvaluator() {
    return evaluator;
  }

  /**
   * Requires one exact hydraulic capacity constraint for every coupled candidate.
   *
   * @param role engineering role of the constraint
   * @param areaName exact process area name
   * @param equipmentName exact equipment name
   * @param constraintName exact capacity-constraint name
   * @param constraintProvenance source explaining why this constraint is required
   * @return this evaluator for chaining
   */
  public ProcessModelOperatingActionSetEvaluator requireHydraulicConstraint(HydraulicLimitRole role, String areaName,
      String equipmentName, String constraintName, String constraintProvenance) {
    HydraulicConstraintBinding candidate = new HydraulicConstraintBinding(role, areaName, equipmentName, constraintName,
        constraintProvenance);
    for (HydraulicConstraintBinding binding : bindings) {
      if (binding.hasSameAddress(candidate)) {
        throw new IllegalArgumentException(
            "Hydraulic constraint binding is already registered: " + candidate.getQualifiedConstraintName());
      }
    }
    bindings.add(candidate);
    return this;
  }

  /** @return fresh immutable required-constraint list */
  public List<HydraulicConstraintBinding> getRequiredHydraulicConstraints() {
    return Collections.unmodifiableList(new ArrayList<HydraulicConstraintBinding>(bindings));
  }

  /**
   * Evaluates one ordered candidate vector and returns after attempted complete baseline recovery.
   *
   * @param candidateValues values in action declaration order and in each action's declared unit
   * @return immutable serializable candidate, constraint, and restoration evidence
   */
  public synchronized CandidateSetEvaluationResult evaluate(double[] candidateValues) {
    double[] candidates = candidateValues == null ? new double[0]
        : Arrays.copyOf(candidateValues, candidateValues.length);
    List<String> diagnostics = new ArrayList<String>();
    if (candidateValues == null || candidateValues.length != actions.size()) {
      diagnostics.add("Candidate vector length must equal the declared action count " + actions.size());
      return CandidateSetEvaluationResult.empty(id, name, provenance, actions, candidates,
          Outcome.CANDIDATE_VECTOR_INVALID, diagnostics);
    }
    if (bindings.isEmpty()) {
      diagnostics.add("At least one exact hydraulic capacity constraint must be required before evaluation");
      return CandidateSetEvaluationResult.empty(id, name, provenance, actions, candidates,
          Outcome.REQUIRED_CONSTRAINT_MISSING, diagnostics);
    }

    ProcessModel model = evaluator.getProcessModel();
    try {
      model.run();
    } catch (RuntimeException exception) {
      diagnostics.add("Baseline process-model run failed: " + safeMessage(exception));
      return CandidateSetEvaluationResult.empty(id, name, provenance, actions, candidates,
          Outcome.BASELINE_SIMULATION_FAILED, diagnostics);
    }
    if (!model.isModelConverged()) {
      diagnostics.add("Baseline process model did not report convergence");
      return CandidateSetEvaluationResult.empty(id, name, provenance, actions, candidates,
          Outcome.BASELINE_SIMULATION_FAILED, diagnostics);
    }

    List<ActionState> baselines = new ArrayList<ActionState>();
    try {
      for (ProcessModelOperatingAction action : actions) {
        baselines.add(action.capture(model));
      }
    } catch (RuntimeException exception) {
      diagnostics.add("Baseline action state is unavailable: " + safeMessage(exception));
      return CandidateSetEvaluationResult.empty(id, name, provenance, actions, candidates,
          Outcome.BASELINE_ACTION_UNAVAILABLE, diagnostics);
    }

    List<ApplicationResult> applications = new ArrayList<ApplicationResult>();
    boolean allApplied = true;
    for (int index = 0; index < actions.size(); index++) {
      ApplicationResult application = actions.get(index).apply(model, candidates[index]);
      applications.add(application);
      diagnostics.add("Action " + actions.get(index).getId() + ": " + application.getDiagnostic());
      if (!application.isApplied()) {
        allApplied = false;
        break;
      }
    }

    Outcome outcome = Outcome.ACTION_REJECTED;
    EvaluationResult candidateEvaluation = null;
    List<CandidateObjectiveEvidence> objectiveEvidence = Collections.emptyList();
    List<CandidateConstraintEvidence> constraintEvidence = Collections.emptyList();
    List<HydraulicConstraintSnapshot> constraintSnapshots = Collections.emptyList();
    if (allApplied) {
      candidateEvaluation = evaluator.evaluate(new double[0]);
      objectiveEvidence = CandidateSetEvaluationResult.snapshotObjectives(evaluator.getObjectives(),
          candidateEvaluation);
      constraintEvidence = CandidateSetEvaluationResult.snapshotConstraints(evaluator.getConstraints(),
          candidateEvaluation);
      if (!candidateEvaluation.isSimulationConverged()) {
        outcome = Outcome.CANDIDATE_SIMULATION_FAILED;
        diagnostics.add(candidateEvaluation.getErrorMessage() == null ? "Candidate process model did not converge"
            : "Candidate process-model evaluation failed: " + candidateEvaluation.getErrorMessage());
      } else {
        constraintSnapshots = snapshotRequiredConstraints(candidateEvaluation, diagnostics);
        outcome = classifyCandidate(candidateEvaluation, constraintSnapshots);
      }
    } else {
      diagnostics.add("Partial candidate was not simulated because at least one action was rejected");
    }

    List<ApplicationResult> restorations = emptyApplicationResults(actions.size());
    boolean allRestored = true;
    for (int index = actions.size() - 1; index >= 0; index--) {
      ApplicationResult restoration = actions.get(index).restore(model, baselines.get(index));
      restorations.set(index, restoration);
      diagnostics.add("Restore " + actions.get(index).getId() + ": " + restoration.getDiagnostic());
      if (!restoration.isApplied()) {
        allRestored = false;
      }
    }

    boolean baselineSimulationConverged = false;
    if (!allRestored) {
      outcome = Outcome.RESTORATION_FAILED;
    } else {
      try {
        model.run();
        baselineSimulationConverged = model.isModelConverged();
      } catch (RuntimeException exception) {
        diagnostics.add("Restored baseline process-model run failed: " + safeMessage(exception));
      }
      if (!baselineSimulationConverged) {
        diagnostics.add("All action values were restored, but the baseline process model did not reconverge");
        outcome = Outcome.RESTORED_BASELINE_SIMULATION_FAILED;
      } else {
        diagnostics.add("All action values were restored in reverse order and the baseline process model reconverged");
      }
    }

    List<ActionCandidateEvidence> actionEvidence = createActionEvidence(actions, baselines, candidates, applications,
        restorations);
    return CandidateSetEvaluationResult.from(id, name, provenance, actions, candidates, actionEvidence, outcome,
        candidateEvaluation, objectiveEvidence, constraintEvidence, constraintSnapshots, allRestored,
        baselineSimulationConverged, diagnostics);
  }

  /** Creates a fixed-size list that accepts indexed restoration results. */
  private static List<ApplicationResult> emptyApplicationResults(int size) {
    List<ApplicationResult> results = new ArrayList<ApplicationResult>();
    for (int index = 0; index < size; index++) {
      results.add(null);
    }
    return results;
  }

  /** Creates immutable per-action evidence in declaration order. */
  private static List<ActionCandidateEvidence> createActionEvidence(List<ProcessModelOperatingAction> actions,
      List<ActionState> baselines, double[] candidates, List<ApplicationResult> applications,
      List<ApplicationResult> restorations) {
    List<ActionCandidateEvidence> evidence = new ArrayList<ActionCandidateEvidence>();
    for (int index = 0; index < actions.size(); index++) {
      ApplicationResult application = index < applications.size() ? applications.get(index) : null;
      evidence.add(new ActionCandidateEvidence(index, actions.get(index), baselines.get(index).getValue(),
          candidates[index], application, restorations.get(index)));
    }
    return evidence;
  }

  /** Creates immutable snapshots for each exact binding. */
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
  private static BottleneckStatus findExactConstraint(List<BottleneckStatus> available,
      HydraulicConstraintBinding binding) {
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
  private static Outcome classifyCandidate(EvaluationResult evaluation, List<HydraulicConstraintSnapshot> snapshots) {
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

  /** Immutable write/read-back and restoration evidence for one action. */
  public static final class ActionCandidateEvidence implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Declaration-order index. */
    private final int index;

    /** Complete immutable action definition. */
    private final ProcessModelOperatingAction action;

    /** Captured baseline value. */
    private final double baselineValue;

    /** Requested candidate value. */
    private final double requestedValue;

    /** Whether application was attempted. */
    private final boolean applicationAttempted;

    /** Whether application was verified. */
    private final boolean applied;

    /** Candidate read-back value. */
    private final double readBackValue;

    /** Candidate absolute read-back residual. */
    private final double readBackResidual;

    /** Candidate allowed read-back tolerance. */
    private final double readBackTolerance;

    /** Candidate read-back tolerance provenance. */
    private final String readBackToleranceProvenance;

    /** Whether the action rolled itself back after a failed write. */
    private final boolean rolledBackAfterFailure;

    /** Whether explicit transaction restoration was attempted. */
    private final boolean restorationAttempted;

    /** Whether explicit transaction restoration was verified. */
    private final boolean restored;

    /** Restored read-back value. */
    private final double restorationReadBackValue;

    /** Restored absolute read-back residual. */
    private final double restorationReadBackResidual;

    /** Application diagnostic. */
    private final String applicationDiagnostic;

    /** Restoration diagnostic. */
    private final String restorationDiagnostic;

    /** Creates immutable evidence. */
    private ActionCandidateEvidence(int index, ProcessModelOperatingAction action, double baselineValue,
        double requestedValue, ApplicationResult application, ApplicationResult restoration) {
      this.index = index;
      this.action = action;
      this.baselineValue = baselineValue;
      this.requestedValue = requestedValue;
      applicationAttempted = application != null;
      applied = application != null && application.isApplied();
      readBackValue = application == null ? Double.NaN : application.getReadBackValue();
      readBackResidual = application == null ? Double.NaN : application.getReadBackResidual();
      readBackTolerance = application == null
          ? Math.max(action.getReadBackAbsoluteTolerance(),
              action.getReadBackRelativeTolerance() * Math.max(1.0, Math.abs(requestedValue)))
          : application.getReadBackTolerance();
      readBackToleranceProvenance = application == null ? action.getReadBackToleranceProvenance()
          : application.getReadBackToleranceProvenance();
      rolledBackAfterFailure = application != null && application.isRolledBackAfterFailure();
      restorationAttempted = restoration != null;
      restored = restoration != null && restoration.isApplied();
      restorationReadBackValue = restoration == null ? Double.NaN : restoration.getReadBackValue();
      restorationReadBackResidual = restoration == null ? Double.NaN : restoration.getReadBackResidual();
      applicationDiagnostic = application == null ? "Application not attempted after an earlier action rejection"
          : application.getDiagnostic();
      restorationDiagnostic = restoration == null ? "Restoration not attempted" : restoration.getDiagnostic();
    }

    /** @return declaration-order index */
    public int getIndex() {
      return index;
    }

    /** @return complete immutable action definition */
    public ProcessModelOperatingAction getAction() {
      return action;
    }

    /** @return captured baseline value */
    public double getBaselineValue() {
      return baselineValue;
    }

    /** @return requested candidate value */
    public double getRequestedValue() {
      return requestedValue;
    }

    /** @return true when application was attempted */
    public boolean isApplicationAttempted() {
      return applicationAttempted;
    }

    /** @return true when application was verified */
    public boolean isApplied() {
      return applied;
    }

    /** @return candidate read-back value, or NaN */
    public double getReadBackValue() {
      return readBackValue;
    }

    /** @return candidate absolute read-back residual, or NaN */
    public double getReadBackResidual() {
      return readBackResidual;
    }

    /** @return allowed candidate read-back tolerance in the action unit */
    public double getReadBackTolerance() {
      return readBackTolerance;
    }

    /** @return evidence source for the candidate read-back tolerance */
    public String getReadBackToleranceProvenance() {
      return readBackToleranceProvenance;
    }

    /** @return true when a failed write was rolled back by the action */
    public boolean isRolledBackAfterFailure() {
      return rolledBackAfterFailure;
    }

    /** @return true when explicit transaction restoration was attempted */
    public boolean isRestorationAttempted() {
      return restorationAttempted;
    }

    /** @return true when explicit transaction restoration was verified */
    public boolean isRestored() {
      return restored;
    }

    /** @return restored read-back value, or NaN */
    public double getRestorationReadBackValue() {
      return restorationReadBackValue;
    }

    /** @return restored absolute read-back residual, or NaN */
    public double getRestorationReadBackResidual() {
      return restorationReadBackResidual;
    }

    /** @return application diagnostic */
    public String getApplicationDiagnostic() {
      return applicationDiagnostic;
    }

    /** @return restoration diagnostic */
    public String getRestorationDiagnostic() {
      return restorationDiagnostic;
    }
  }

  /** Immutable objective identity and sampled value for one candidate simulation. */
  public static final class CandidateObjectiveEvidence implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Evaluator registration index. */
    private final int index;
    /** Objective name. */
    private final String name;
    /** Optimization direction. */
    private final ObjectiveDefinition.Direction direction;
    /** Objective unit. */
    private final String unit;
    /** Scalarization weight metadata. */
    private final double weight;
    /** Sampled raw value. */
    private final double rawValue;
    /** Sampled minimizer-sign value. */
    private final double minimizerValue;

    /** Creates frozen objective evidence in evaluator registration order. */
    private CandidateObjectiveEvidence(int index, ObjectiveDefinition definition, double rawValue,
        double minimizerValue) {
      this.index = index;
      this.name = definition.getName();
      this.direction = definition.getDirection();
      this.unit = definition.getUnit();
      this.weight = definition.getWeight();
      this.rawValue = rawValue;
      this.minimizerValue = minimizerValue;
    }

    /** @return evaluator registration index */
    public int getIndex() {
      return index;
    }

    /** @return objective name */
    public String getName() {
      return name;
    }

    /** @return optimization direction */
    public ObjectiveDefinition.Direction getDirection() {
      return direction;
    }

    /** @return objective unit, possibly null when not declared */
    public String getUnit() {
      return unit;
    }

    /** @return scalarization weight metadata */
    public double getWeight() {
      return weight;
    }

    /** @return sampled raw objective value */
    public double getRawValue() {
      return rawValue;
    }

    /** @return sampled minimizer-sign objective value */
    public double getMinimizerValue() {
      return minimizerValue;
    }
  }

  /** Immutable registered-constraint identity and sampled margin for one candidate simulation. */
  public static final class CandidateConstraintEvidence implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Evaluator registration index. */
    private final int index;
    /** Registered constraint name. */
    private final String name;
    /** Constraint type. */
    private final ConstraintDefinition.Type type;
    /** Unit of the registered value and margin. */
    private final String unit;
    /** Separate physical installed-capacity unit, or null for a general constraint. */
    private final String physicalUnit;
    /** Whether violation makes the point infeasible. */
    private final boolean hard;
    /** Soft-penalty weight metadata. */
    private final double penaltyWeight;
    /** Lower bound or equality target. */
    private final double lowerBound;
    /** Upper bound. */
    private final double upperBound;
    /** Equality tolerance. */
    private final double equalityTolerance;
    /** Whether generated from installed equipment capacity. */
    private final boolean capacityConstraint;
    /** Capacity process-area origin. */
    private final String areaName;
    /** Capacity equipment origin. */
    private final String equipmentName;
    /** Original equipment constraint name. */
    private final String equipmentConstraintName;
    /** Sampled evaluator value. */
    private final double value;
    /** Sampled signed margin. */
    private final double margin;

    /** Creates frozen constraint evidence in evaluator registration order. */
    private CandidateConstraintEvidence(int index, ConstraintDefinition definition, double value, double margin) {
      this.index = index;
      this.name = definition.getName();
      this.type = definition.getType();
      this.unit = definition.getUnit();
      this.physicalUnit = definition.getCapacityPhysicalUnit();
      this.hard = definition.isHard();
      this.penaltyWeight = definition.getPenaltyWeight();
      this.lowerBound = definition.getLowerBound();
      this.upperBound = definition.getUpperBound();
      this.equalityTolerance = definition.getEqualityTolerance();
      this.capacityConstraint = definition.isCapacityConstraint();
      this.areaName = definition.getAreaName();
      this.equipmentName = definition.getEquipmentName();
      this.equipmentConstraintName = definition.getEquipmentConstraintName();
      this.value = value;
      this.margin = margin;
    }

    /** @return evaluator registration index */
    public int getIndex() {
      return index;
    }

    /** @return registered constraint name */
    public String getName() {
      return name;
    }

    /** @return constraint type */
    public ConstraintDefinition.Type getType() {
      return type;
    }

    /** @return constraint and margin unit, possibly null when not declared */
    public String getUnit() {
      return unit;
    }

    /**
     * Returns the physical engineering unit for installed-capacity evidence.
     *
     * @return physical unit, or null for a general constraint
     */
    public String getPhysicalUnit() {
      return physicalUnit;
    }

    /** @return true when violation makes the evaluator point infeasible */
    public boolean isHard() {
      return hard;
    }

    /** @return configured soft-penalty weight metadata */
    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    /** @return configured lower bound or equality target */
    public double getLowerBound() {
      return lowerBound;
    }

    /** @return configured upper bound */
    public double getUpperBound() {
      return upperBound;
    }

    /** @return configured equality tolerance */
    public double getEqualityTolerance() {
      return equalityTolerance;
    }

    /** @return true when generated from installed equipment capacity metadata */
    public boolean isCapacityConstraint() {
      return capacityConstraint;
    }

    /** @return process-area origin, or null for a general model constraint */
    public String getAreaName() {
      return areaName;
    }

    /** @return equipment origin, or null for a general model constraint */
    public String getEquipmentName() {
      return equipmentName;
    }

    /** @return original equipment constraint name, or null */
    public String getEquipmentConstraintName() {
      return equipmentConstraintName;
    }

    /** @return sampled constraint value */
    public double getValue() {
      return value;
    }

    /** @return sampled margin; non-negative is satisfied */
    public double getMargin() {
      return margin;
    }

    /** @return true when the sampled margin is finite and non-negative */
    public boolean isSatisfied() {
      return !Double.isNaN(margin) && !Double.isInfinite(margin) && margin >= 0.0;
    }
  }

  /** Immutable serializable result for one coupled candidate. */
  public static final class CandidateSetEvaluationResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Stable action-set identifier. */
    private final String id;

    /** Human-readable action-set name. */
    private final String name;

    /** Action-set provenance. */
    private final String provenance;

    /** Complete immutable action definitions. */
    private final List<ProcessModelOperatingAction> actions;

    /** Requested candidate values. */
    private final double[] candidateValues;

    /** Per-action evidence. */
    private final List<ActionCandidateEvidence> actionEvidence;

    /** Fail-closed outcome. */
    private final Outcome outcome;

    /** Candidate convergence. */
    private final boolean candidateSimulationConverged;

    /** Candidate feasibility across every registered evaluator constraint. */
    private final boolean candidateEvaluatorFeasible;

    /** Whether every action was restored and verified. */
    private final boolean baselineRestored;

    /** Whether the restored baseline reconverged. */
    private final boolean baselineSimulationConverged;

    /** Raw objective values at the candidate. */
    private final double[] rawObjectives;

    /** Sign-adjusted objective values at the candidate. */
    private final double[] objectives;

    /** Frozen objective definitions and sampled values. */
    private final List<CandidateObjectiveEvidence> objectiveEvidence;

    /** Registered constraint values at the candidate. */
    private final double[] constraintValues;

    /** Registered constraint margins at the candidate. */
    private final double[] constraintMargins;

    /** Frozen constraint definitions and sampled values. */
    private final List<CandidateConstraintEvidence> constraintEvidence;

    /** Required hydraulic snapshots. */
    private final List<HydraulicConstraintSnapshot> hydraulicConstraints;

    /** Complete unit-safe installed-capacity evidence from the candidate operating point. */
    private final List<InstalledEquipmentCapacityEvidence> installedEquipmentCapacityEvidence;

    /** Immutable diagnostics. */
    private final List<String> diagnostics;

    /** Creates an immutable result. */
    private CandidateSetEvaluationResult(String id, String name, String provenance,
        List<ProcessModelOperatingAction> actions, double[] candidateValues,
        List<ActionCandidateEvidence> actionEvidence, Outcome outcome, boolean candidateSimulationConverged,
        boolean candidateEvaluatorFeasible, boolean baselineRestored, boolean baselineSimulationConverged,
        double[] rawObjectives, double[] objectives, List<CandidateObjectiveEvidence> objectiveEvidence,
        double[] constraintValues, double[] constraintMargins, List<CandidateConstraintEvidence> constraintEvidence,
        List<HydraulicConstraintSnapshot> hydraulicConstraints,
        List<InstalledEquipmentCapacityEvidence> installedEquipmentCapacityEvidence, List<String> diagnostics) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.actions = Collections.unmodifiableList(new ArrayList<ProcessModelOperatingAction>(actions));
      this.candidateValues = Arrays.copyOf(candidateValues, candidateValues.length);
      this.actionEvidence = Collections.unmodifiableList(new ArrayList<ActionCandidateEvidence>(actionEvidence));
      this.outcome = outcome;
      this.candidateSimulationConverged = candidateSimulationConverged;
      this.candidateEvaluatorFeasible = candidateEvaluatorFeasible;
      this.baselineRestored = baselineRestored;
      this.baselineSimulationConverged = baselineSimulationConverged;
      this.rawObjectives = Arrays.copyOf(rawObjectives, rawObjectives.length);
      this.objectives = Arrays.copyOf(objectives, objectives.length);
      this.objectiveEvidence = Collections
          .unmodifiableList(new ArrayList<CandidateObjectiveEvidence>(objectiveEvidence));
      this.constraintValues = Arrays.copyOf(constraintValues, constraintValues.length);
      this.constraintMargins = Arrays.copyOf(constraintMargins, constraintMargins.length);
      this.constraintEvidence = Collections
          .unmodifiableList(new ArrayList<CandidateConstraintEvidence>(constraintEvidence));
      this.hydraulicConstraints = Collections
          .unmodifiableList(new ArrayList<HydraulicConstraintSnapshot>(hydraulicConstraints));
      this.installedEquipmentCapacityEvidence = Collections
          .unmodifiableList(new ArrayList<InstalledEquipmentCapacityEvidence>(installedEquipmentCapacityEvidence));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** Creates a result before baseline capture or candidate simulation. */
    private static CandidateSetEvaluationResult empty(String id, String name, String provenance,
        List<ProcessModelOperatingAction> actions, double[] candidateValues, Outcome outcome,
        List<String> diagnostics) {
      return new CandidateSetEvaluationResult(id, name, provenance, actions, candidateValues,
          Collections.<ActionCandidateEvidence>emptyList(), outcome, false, false, false, false, new double[0],
          new double[0], Collections.<CandidateObjectiveEvidence>emptyList(), new double[0], new double[0],
          Collections.<CandidateConstraintEvidence>emptyList(), Collections.<HydraulicConstraintSnapshot>emptyList(),
          Collections.<InstalledEquipmentCapacityEvidence>emptyList(), diagnostics);
    }

    /** Creates a result from an optional candidate simulation. */
    private static CandidateSetEvaluationResult from(String id, String name, String provenance,
        List<ProcessModelOperatingAction> actions, double[] candidateValues,
        List<ActionCandidateEvidence> actionEvidence, Outcome outcome, EvaluationResult evaluation,
        List<CandidateObjectiveEvidence> objectiveEvidence, List<CandidateConstraintEvidence> constraintEvidence,
        List<HydraulicConstraintSnapshot> hydraulicConstraints, boolean baselineRestored,
        boolean baselineSimulationConverged, List<String> diagnostics) {
      if (evaluation == null) {
        return new CandidateSetEvaluationResult(id, name, provenance, actions, candidateValues, actionEvidence, outcome,
            false, false, baselineRestored, baselineSimulationConverged, new double[0], new double[0],
            Collections.<CandidateObjectiveEvidence>emptyList(), new double[0], new double[0],
            Collections.<CandidateConstraintEvidence>emptyList(), hydraulicConstraints,
            Collections.<InstalledEquipmentCapacityEvidence>emptyList(), diagnostics);
      }
      return new CandidateSetEvaluationResult(id, name, provenance, actions, candidateValues, actionEvidence, outcome,
          evaluation.isSimulationConverged(), evaluation.isFeasible(), baselineRestored, baselineSimulationConverged,
          copy(evaluation.getObjectivesRaw()), copy(evaluation.getObjectives()), objectiveEvidence,
          copy(evaluation.getConstraintValues()), copy(evaluation.getConstraintMargins()), constraintEvidence,
          hydraulicConstraints, evaluation.getInstalledEquipmentCapacityEvidence(), diagnostics);
    }

    /** Snapshots objective definitions and sampled values without retaining evaluator callbacks. */
    private static List<CandidateObjectiveEvidence> snapshotObjectives(List<ObjectiveDefinition> definitions,
        EvaluationResult evaluation) {
      if (evaluation == null) {
        return Collections.emptyList();
      }
      double[] rawValues = copy(evaluation.getObjectivesRaw());
      double[] minimizerValues = copy(evaluation.getObjectives());
      List<CandidateObjectiveEvidence> evidence = new ArrayList<CandidateObjectiveEvidence>();
      for (int index = 0; index < definitions.size(); index++) {
        evidence.add(new CandidateObjectiveEvidence(index, definitions.get(index), valueAt(rawValues, index),
            valueAt(minimizerValues, index)));
      }
      return Collections.unmodifiableList(evidence);
    }

    /** Snapshots constraint definitions and sampled margins without retaining evaluator callbacks. */
    private static List<CandidateConstraintEvidence> snapshotConstraints(List<ConstraintDefinition> definitions,
        EvaluationResult evaluation) {
      if (evaluation == null) {
        return Collections.emptyList();
      }
      double[] values = copy(evaluation.getConstraintValues());
      double[] margins = copy(evaluation.getConstraintMargins());
      List<CandidateConstraintEvidence> evidence = new ArrayList<CandidateConstraintEvidence>();
      for (int index = 0; index < definitions.size(); index++) {
        evidence.add(new CandidateConstraintEvidence(index, definitions.get(index), valueAt(values, index),
            valueAt(margins, index)));
      }
      return Collections.unmodifiableList(evidence);
    }

    /** Returns an indexed scalar or NaN when a result array is shorter than its definition list. */
    private static double valueAt(double[] values, int index) {
      return index < values.length ? values[index] : Double.NaN;
    }

    /** Returns a defensive array or an empty array for null. */
    private static double[] copy(double[] values) {
      return values == null ? new double[0] : Arrays.copyOf(values, values.length);
    }

    /** @return stable action-set identifier */
    public String getId() {
      return id;
    }

    /** @return human-readable action-set name */
    public String getName() {
      return name;
    }

    /** @return action-set provenance */
    public String getProvenance() {
      return provenance;
    }

    /** @return fresh immutable action definitions in declaration order */
    public List<ProcessModelOperatingAction> getActions() {
      return Collections.unmodifiableList(new ArrayList<ProcessModelOperatingAction>(actions));
    }

    /** @return defensive requested-candidate array */
    public double[] getCandidateValues() {
      return Arrays.copyOf(candidateValues, candidateValues.length);
    }

    /** @return defensive baseline array in action order, or an empty array before capture */
    public double[] getBaselineValues() {
      double[] values = new double[actionEvidence.size()];
      for (int index = 0; index < actionEvidence.size(); index++) {
        values[index] = actionEvidence.get(index).getBaselineValue();
      }
      return values;
    }

    /** @return fresh immutable per-action evidence in declaration order */
    public List<ActionCandidateEvidence> getActionEvidence() {
      return Collections.unmodifiableList(new ArrayList<ActionCandidateEvidence>(actionEvidence));
    }

    /** @return fail-closed outcome */
    public Outcome getOutcome() {
      return outcome;
    }

    /** @return true only for a fully evidenced feasible coupled candidate */
    public boolean isFeasible() {
      return outcome == Outcome.FEASIBLE;
    }

    /** @return true when the complete candidate process model converged */
    public boolean isCandidateSimulationConverged() {
      return candidateSimulationConverged;
    }

    /** @return true when all registered evaluator constraints were feasible */
    public boolean isCandidateEvaluatorFeasible() {
      return candidateEvaluatorFeasible;
    }

    /** @return true when every captured action was restored and verified */
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

    /** @return fresh immutable objective identity and sampled-value evidence */
    public List<CandidateObjectiveEvidence> getObjectiveEvidence() {
      return Collections.unmodifiableList(new ArrayList<CandidateObjectiveEvidence>(objectiveEvidence));
    }

    /** @return defensive registered-constraint value array */
    public double[] getConstraintValues() {
      return Arrays.copyOf(constraintValues, constraintValues.length);
    }

    /** @return defensive registered-constraint margin array */
    public double[] getConstraintMargins() {
      return Arrays.copyOf(constraintMargins, constraintMargins.length);
    }

    /** @return fresh immutable registered-constraint identity and sampled-value evidence */
    public List<CandidateConstraintEvidence> getConstraintEvidence() {
      return Collections.unmodifiableList(new ArrayList<CandidateConstraintEvidence>(constraintEvidence));
    }

    /** @return fresh immutable hydraulic evidence in binding order */
    public List<HydraulicConstraintSnapshot> getHydraulicConstraints() {
      return Collections.unmodifiableList(new ArrayList<HydraulicConstraintSnapshot>(hydraulicConstraints));
    }

    /**
     * Returns every enabled installed-equipment capacity sampled at the candidate point.
     *
     * @return fresh immutable descending-utilization evidence
     */
    public List<InstalledEquipmentCapacityEvidence> getInstalledEquipmentCapacityEvidence() {
      return Collections
          .unmodifiableList(new ArrayList<InstalledEquipmentCapacityEvidence>(installedEquipmentCapacityEvidence));
    }

    /** @return fresh immutable diagnostics */
    public List<String> getDiagnostics() {
      return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }
  }
}
