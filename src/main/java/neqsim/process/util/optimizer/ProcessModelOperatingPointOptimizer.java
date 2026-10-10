package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.ActionCandidateEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;

/**
 * Performs a bounded deterministic mixed continuous/discrete operating-point search.
 *
 * <p>
 * This optimizer composes {@link ProcessModelOperatingActionSetEvaluator}; it does not bypass or duplicate its
 * convergence, constraint, action-read-back, or reverse-restoration gates. Continuous actions are explored in both
 * directions using a shrinking fraction of their declared range. Discrete actions are explored only at their exact
 * declared values. The best feasible finite-objective move is accepted after each complete coordinate neighborhood.
 * </p>
 *
 * <p>
 * Search stops immediately if any evaluation does not restore and reconverge the mutable baseline. A selected point is
 * evaluated once more from the restored baseline before it is exposed as accepted. The result retains every trial,
 * elapsed time, evaluation count, complete constraint/action evidence flags, and the exact evidence keys consumed by
 * the continuous production-optimization guard. This local coordinate search is not proof of a global optimum or
 * operating approval.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelOperatingPointOptimizer {
  /** Terminal search classification. */
  public enum SearchOutcome {
    /** Step tolerance was reached and the selected feasible point replayed successfully. */
    CONVERGED_WITH_REPLAYED_FEASIBLE_CANDIDATE,
    /** The budget ended and the selected feasible point replayed successfully. */
    BUDGET_EXHAUSTED_WITH_REPLAYED_FEASIBLE_CANDIDATE,
    /** Step tolerance was reached without a feasible finite-objective point. */
    CONVERGED_WITHOUT_FEASIBLE_CANDIDATE,
    /** The budget ended without a feasible finite-objective point. */
    BUDGET_EXHAUSTED_WITHOUT_FEASIBLE_CANDIDATE,
    /** A trial failed complete baseline restoration or restored-baseline convergence. */
    MODEL_RECOVERY_FAILED,
    /** The selected point did not replay as the same feasible finite operating point. */
    ACCEPTED_POINT_REPLAY_FAILED
  }

  /** Stable optimizer identifier. */
  private final String id;
  /** Human-readable optimizer name. */
  private final String name;
  /** Engineering basis for the search. */
  private final String provenance;
  /** Atomic fail-closed candidate evaluator. */
  private final ProcessModelOperatingActionSetEvaluator candidateEvaluator;
  /** Registered objective index. */
  private int objectiveIndex;
  /** Optional deterministic initial candidate. */
  private double[] initialCandidate;
  /** Maximum evaluations including the mandatory accepted-point replay. */
  private int maximumEvaluations = 200;
  /** Initial step as a fraction of each continuous range. */
  private double initialStepFraction = 0.25;
  /** Final relative continuous-range step. */
  private double relativeStepTolerance = 1.0e-3;
  /** Absolute objective improvement required for replacement. */
  private double objectiveImprovementTolerance;
  /** Source of the objective tolerance. */
  private String objectiveToleranceProvenance = "exact raw-objective comparison";
  /** Relative tolerance used to prove that the selected operating point replayed. */
  private double replayRelativeTolerance = 1.0e-9;
  /** Source of the replay tolerance. */
  private String replayToleranceProvenance = "deterministic simulator replay tolerance";

  /**
   * Creates a mixed operating-point optimizer.
   *
   * @param id stable optimizer identifier
   * @param name human-readable optimizer name
   * @param provenance engineering basis for the search
   * @param candidateEvaluator atomic fail-closed candidate evaluator
   */
  public ProcessModelOperatingPointOptimizer(String id, String name, String provenance,
      ProcessModelOperatingActionSetEvaluator candidateEvaluator) {
    this.id = requireText(id, "Operating-point optimizer identifier");
    this.name = requireText(name, "Operating-point optimizer name");
    this.provenance = requireText(provenance, "Operating-point optimizer provenance");
    if (candidateEvaluator == null) {
      throw new IllegalArgumentException("Atomic candidate evaluator must not be null");
    }
    if (candidateEvaluator.getSimulationEvaluator().getObjectiveCount() == 0) {
      throw new IllegalArgumentException("Operating-point search requires at least one registered objective");
    }
    this.candidateEvaluator = candidateEvaluator;
  }

  /** @return stable optimizer identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable optimizer name */
  public String getName() {
    return name;
  }

  /** @return engineering basis for the search */
  public String getProvenance() {
    return provenance;
  }

  /** @return atomic candidate evaluator */
  public ProcessModelOperatingActionSetEvaluator getCandidateEvaluator() {
    return candidateEvaluator;
  }

  /**
   * Selects one registered raw objective for candidate comparisons.
   *
   * @param objectiveIndex zero-based objective index
   * @return this optimizer
   */
  public ProcessModelOperatingPointOptimizer setObjectiveIndex(int objectiveIndex) {
    if (objectiveIndex < 0 || objectiveIndex >= candidateEvaluator.getSimulationEvaluator().getObjectiveCount()) {
      throw new IllegalArgumentException("Objective index is outside the registered objective range");
    }
    this.objectiveIndex = objectiveIndex;
    return this;
  }

  /**
   * Sets the deterministic seed in action declaration order.
   *
   * @param candidate bounded continuous and exact discrete values
   * @return this optimizer
   */
  public ProcessModelOperatingPointOptimizer setInitialCandidate(double[] candidate) {
    this.initialCandidate = validateCandidate(candidate, "Initial candidate");
    return this;
  }

  /**
   * Sets the hard evaluation budget, including final replay.
   *
   * @param maximumEvaluations value of at least two
   * @return this optimizer
   */
  public ProcessModelOperatingPointOptimizer setMaximumEvaluations(int maximumEvaluations) {
    if (maximumEvaluations < 2) {
      throw new IllegalArgumentException("Maximum evaluations must be at least two to reserve accepted-point replay");
    }
    this.maximumEvaluations = maximumEvaluations;
    return this;
  }

  /**
   * Sets the initial continuous step as a fraction of each action range.
   *
   * @param initialStepFraction finite value in (0, 1]
   * @return this optimizer
   */
  public ProcessModelOperatingPointOptimizer setInitialStepFraction(double initialStepFraction) {
    if (!isFinite(initialStepFraction) || initialStepFraction <= 0.0 || initialStepFraction > 1.0) {
      throw new IllegalArgumentException("Initial step fraction must be finite and in (0, 1]");
    }
    this.initialStepFraction = initialStepFraction;
    return this;
  }

  /**
   * Sets the final continuous step relative to each action range.
   *
   * @param relativeStepTolerance finite positive fraction
   * @return this optimizer
   */
  public ProcessModelOperatingPointOptimizer setRelativeStepTolerance(double relativeStepTolerance) {
    if (!isFinite(relativeStepTolerance) || relativeStepTolerance <= 0.0) {
      throw new IllegalArgumentException("Relative step tolerance must be finite and positive");
    }
    this.relativeStepTolerance = relativeStepTolerance;
    return this;
  }

  /**
   * Sets the absolute raw-objective improvement needed to replace the incumbent.
   *
   * @param tolerance finite non-negative tolerance in objective units
   * @param toleranceProvenance source of the tolerance
   * @return this optimizer
   */
  public ProcessModelOperatingPointOptimizer setObjectiveImprovementTolerance(double tolerance,
      String toleranceProvenance) {
    if (!isFinite(tolerance) || tolerance < 0.0) {
      throw new IllegalArgumentException("Objective improvement tolerance must be finite and non-negative");
    }
    this.objectiveImprovementTolerance = tolerance;
    this.objectiveToleranceProvenance = requireText(toleranceProvenance, "Objective tolerance provenance");
    return this;
  }

  /**
   * Sets the relative raw-objective tolerance for final deterministic replay.
   *
   * @param tolerance finite non-negative relative tolerance
   * @param toleranceProvenance source of the replay tolerance
   * @return this optimizer
   */
  public ProcessModelOperatingPointOptimizer setReplayRelativeTolerance(double tolerance, String toleranceProvenance) {
    if (!isFinite(tolerance) || tolerance < 0.0) {
      throw new IllegalArgumentException("Replay tolerance must be finite and non-negative");
    }
    this.replayRelativeTolerance = tolerance;
    this.replayToleranceProvenance = requireText(toleranceProvenance, "Replay tolerance provenance");
    return this;
  }

  /**
   * Executes a deterministic mixed coordinate search and final selected-point replay.
   *
   * @return immutable search trace, acceptance evidence, and performance provenance
   */
  public synchronized OperatingPointSearchResult optimize() {
    validateConfiguration();
    long startedNanos = System.nanoTime();
    ObjectiveSnapshot objective = new ObjectiveSnapshot(objectiveIndex,
        candidateEvaluator.getSimulationEvaluator().getObjectives().get(objectiveIndex));
    double[] seed = initialCandidate == null ? captureInitialCandidate() : initialCandidate.clone();
    List<CandidateRecord> records = new ArrayList<CandidateRecord>();
    List<String> diagnostics = new ArrayList<String>();
    CandidateRecord incumbent = evaluate(seed, objective, records, false, false);
    if (unsafeRecovery(incumbent)) {
      diagnostics.add("Search stopped because the seed did not restore and reconverge the process model");
      return result(objective, seed, SearchOutcome.MODEL_RECOVERY_FAILED, records, null, initialStepFraction, false,
          startedNanos, diagnostics);
    }
    if (!eligible(incumbent)) {
      incumbent = null;
    } else {
      incumbent = incumbent.withAcceptedAsIncumbent(true);
      records.set(0, incumbent);
    }

    double stepFraction = initialStepFraction;
    boolean recoveryFailed = false;
    while (stepFraction > relativeStepTolerance && records.size() < maximumEvaluations - 1 && !recoveryFailed) {
      double[] reference = incumbent == null ? seed : incumbent.getCandidateValues();
      CandidateRecord bestMove = null;
      List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
      for (int actionIndex = 0; actionIndex < actions.size()
          && records.size() < maximumEvaluations - 1; actionIndex++) {
        for (double trialValue : trialValues(actions.get(actionIndex), reference[actionIndex], stepFraction)) {
          if (records.size() >= maximumEvaluations - 1) {
            break;
          }
          double[] trial = reference.clone();
          trial[actionIndex] = trialValue;
          CandidateRecord candidate = evaluate(trial, objective, records, false, false);
          if (unsafeRecovery(candidate)) {
            diagnostics.add("Search stopped after candidate " + candidate.getSequenceIndex()
                + " because complete baseline recovery failed");
            recoveryFailed = true;
            break;
          }
          if (eligible(candidate) && (bestMove == null || better(candidate, bestMove, objective, 0.0))) {
            bestMove = candidate;
          }
        }
        if (recoveryFailed) {
          break;
        }
      }
      if (recoveryFailed) {
        break;
      }
      if (bestMove != null
          && (incumbent == null || better(bestMove, incumbent, objective, objectiveImprovementTolerance))) {
        bestMove = bestMove.withAcceptedAsIncumbent(true);
        records.set(bestMove.getSequenceIndex(), bestMove);
        incumbent = bestMove;
      } else {
        stepFraction *= 0.5;
      }
    }

    if (recoveryFailed) {
      return result(objective, seed, SearchOutcome.MODEL_RECOVERY_FAILED, records, null, stepFraction, false,
          startedNanos, diagnostics);
    }
    boolean converged = stepFraction <= relativeStepTolerance;
    boolean budgetExhausted = records.size() >= maximumEvaluations - 1 && !converged;
    if (incumbent == null) {
      diagnostics.add("No candidate combined feasibility, a finite selected objective, and complete recovery");
      SearchOutcome outcome = budgetExhausted ? SearchOutcome.BUDGET_EXHAUSTED_WITHOUT_FEASIBLE_CANDIDATE
          : SearchOutcome.CONVERGED_WITHOUT_FEASIBLE_CANDIDATE;
      return result(objective, seed, outcome, records, null, stepFraction, converged, startedNanos, diagnostics);
    }

    CandidateRecord replay = evaluate(incumbent.getCandidateValues(), objective, records, true, true);
    if (unsafeRecovery(replay)) {
      diagnostics.add("Accepted-point replay did not restore and reconverge the baseline");
      return result(objective, seed, SearchOutcome.MODEL_RECOVERY_FAILED, records, null, stepFraction, false,
          startedNanos, diagnostics);
    }
    if (!eligible(replay) || !sameObjective(incumbent.getRawObjective(), replay.getRawObjective())) {
      diagnostics.add("Selected candidate did not replay as the same feasible finite operating point");
      return result(objective, seed, SearchOutcome.ACCEPTED_POINT_REPLAY_FAILED, records, null, stepFraction, false,
          startedNanos, diagnostics);
    }
    diagnostics.add("Selected feasible candidate was replayed from the restored baseline");
    SearchOutcome outcome = budgetExhausted ? SearchOutcome.BUDGET_EXHAUSTED_WITH_REPLAYED_FEASIBLE_CANDIDATE
        : SearchOutcome.CONVERGED_WITH_REPLAYED_FEASIBLE_CANDIDATE;
    return result(objective, seed, outcome, records, replay, stepFraction, converged, startedNanos, diagnostics);
  }

  /** Creates one candidate record and appends it to the trace. */
  private CandidateRecord evaluate(double[] candidate, ObjectiveSnapshot objective, List<CandidateRecord> records,
      boolean accepted, boolean replay) {
    CandidateSetEvaluationResult evaluation = candidateEvaluator.evaluate(candidate);
    double rawObjective = valueAt(evaluation.getRawObjectives(), objective.getIndex());
    return append(records, new CandidateRecord(records.size(), candidate, rawObjective, accepted, replay, evaluation));
  }

  /** Appends and returns one immutable record. */
  private CandidateRecord append(List<CandidateRecord> records, CandidateRecord record) {
    records.add(record);
    return record;
  }

  /** Creates the immutable terminal result. */
  private OperatingPointSearchResult result(ObjectiveSnapshot objective, double[] seed, SearchOutcome outcome,
      List<CandidateRecord> records, CandidateRecord acceptedReplay, double finalStepFraction, boolean converged,
      long startedNanos, List<String> diagnostics) {
    double runtimeSeconds = Math.max(0.0, (System.nanoTime() - startedNanos) / 1.0e9);
    return new OperatingPointSearchResult(id, name, provenance, candidateEvaluator.getId(), objective, seed,
        maximumEvaluations, initialStepFraction, relativeStepTolerance, objectiveImprovementTolerance,
        objectiveToleranceProvenance, replayRelativeTolerance, replayToleranceProvenance, outcome, records,
        acceptedReplay, finalStepFraction, converged, runtimeSeconds, diagnostics);
  }

  /** Returns deterministic trial values for one action at one reference point. */
  private List<Double> trialValues(ProcessModelOperatingAction action, double reference, double stepFraction) {
    List<Double> values = new ArrayList<Double>();
    if (action.isDiscrete()) {
      for (double allowed : action.getAllowedValues()) {
        if (Double.compare(allowed, reference) != 0) {
          values.add(allowed);
        }
      }
      return values;
    }
    double step = stepFraction * (action.getUpperBound() - action.getLowerBound());
    double lower = reference - step;
    double upper = reference + step;
    if (lower >= action.getLowerBound() && Double.compare(lower, reference) != 0) {
      values.add(lower);
    }
    if (upper <= action.getUpperBound() && Double.compare(upper, reference) != 0) {
      values.add(upper);
    }
    return values;
  }

  /** Captures and validates the model's current action values as the seed. */
  private double[] captureInitialCandidate() {
    List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
    double[] candidate = new double[actions.size()];
    for (int index = 0; index < actions.size(); index++) {
      ProcessModelOperatingAction.CapabilityAssessment capability = actions.get(index)
          .inspectCapability(candidateEvaluator.getSimulationEvaluator().getProcessModel());
      if (!capability.isAvailable() || !capability.isCurrentValueWithinDomain()) {
        throw new IllegalStateException("Current value is not an eligible search seed for action "
            + actions.get(index).getId() + ": " + capability.getDiagnostics());
      }
      candidate[index] = capability.getCurrentValue();
    }
    return candidate;
  }

  /** Validates an action-domain candidate. */
  private double[] validateCandidate(double[] candidate, String description) {
    List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
    if (candidate == null || candidate.length != actions.size()) {
      throw new IllegalArgumentException(description + " length must equal the action count");
    }
    double[] copy = candidate.clone();
    for (int index = 0; index < copy.length; index++) {
      if (!actions.get(index).accepts(copy[index])) {
        throw new IllegalArgumentException(description + " violates action domain at index " + index);
      }
    }
    return copy;
  }

  /** Validates cross-field search configuration. */
  private void validateConfiguration() {
    setObjectiveIndex(objectiveIndex);
    if (relativeStepTolerance >= initialStepFraction) {
      throw new IllegalStateException("Relative step tolerance must be smaller than the initial step fraction");
    }
  }

  /** Returns true only for feasible, finite, fully restored candidates. */
  private static boolean eligible(CandidateRecord record) {
    return record != null && record.getEvaluation().isFeasible() && record.getEvaluation().isBaselineRestored()
        && record.getEvaluation().isBaselineSimulationConverged() && isFinite(record.getRawObjective());
  }

  /** Returns true when further evaluation would risk stale model state. */
  private static boolean unsafeRecovery(CandidateRecord record) {
    return !record.getEvaluation().isBaselineRestored() || !record.getEvaluation().isBaselineSimulationConverged();
  }

  /** Compares raw objectives using frozen direction metadata. */
  private static boolean better(CandidateRecord candidate, CandidateRecord reference, ObjectiveSnapshot objective,
      double tolerance) {
    if (objective.getDirection() == ObjectiveDefinition.Direction.MAXIMIZE) {
      return candidate.getRawObjective() > reference.getRawObjective() + tolerance;
    }
    return candidate.getRawObjective() < reference.getRawObjective() - tolerance;
  }

  /** Returns true when replay retained the selected raw objective within relative tolerance. */
  private boolean sameObjective(double selected, double replayed) {
    if (!isFinite(selected) || !isFinite(replayed)) {
      return false;
    }
    double scale = Math.max(1.0, Math.max(Math.abs(selected), Math.abs(replayed)));
    return Math.abs(selected - replayed) <= replayRelativeTolerance * scale;
  }

  /** Returns an indexed value or NaN when unavailable. */
  private static double valueAt(double[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : Double.NaN;
  }

  /** Validates required metadata. */
  private static String requireText(String value, String description) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(description + " must not be blank");
    }
    return value.trim();
  }

  /** Returns true for a finite Java 8 scalar. */
  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  /** Immutable selected-objective identity. */
  public static final class ObjectiveSnapshot implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Registered index. */
    private final int index;
    /** Objective name. */
    private final String name;
    /** Objective direction. */
    private final ObjectiveDefinition.Direction direction;
    /** Objective unit. */
    private final String unit;

    /** Creates a frozen objective definition. */
    private ObjectiveSnapshot(int index, ObjectiveDefinition definition) {
      this.index = index;
      this.name = definition.getName();
      this.direction = definition.getDirection();
      this.unit = definition.getUnit();
    }

    /** @return registered index */
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

    /** @return objective unit, possibly null */
    public String getUnit() {
      return unit;
    }
  }

  /** Immutable trace row for one complete candidate transaction. */
  public static final class CandidateRecord implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Evaluation sequence index. */
    private final int sequenceIndex;
    /** Candidate values. */
    private final double[] candidateValues;
    /** Selected raw objective. */
    private final double rawObjective;
    /** Whether the row became an incumbent. */
    private final boolean acceptedAsIncumbent;
    /** Whether the row is the mandatory terminal replay. */
    private final boolean acceptedPointReplay;
    /** Complete candidate evidence. */
    private final CandidateSetEvaluationResult evaluation;

    /** Creates one trace row. */
    private CandidateRecord(int sequenceIndex, double[] candidateValues, double rawObjective,
        boolean acceptedAsIncumbent, boolean acceptedPointReplay, CandidateSetEvaluationResult evaluation) {
      this.sequenceIndex = sequenceIndex;
      this.candidateValues = candidateValues.clone();
      this.rawObjective = rawObjective;
      this.acceptedAsIncumbent = acceptedAsIncumbent;
      this.acceptedPointReplay = acceptedPointReplay;
      this.evaluation = evaluation;
    }

    /** Creates the same row marked as an accepted incumbent. */
    private CandidateRecord withAcceptedAsIncumbent(boolean accepted) {
      return new CandidateRecord(sequenceIndex, candidateValues, rawObjective, accepted, acceptedPointReplay,
          evaluation);
    }

    /** @return zero-based evaluation sequence */
    public int getSequenceIndex() {
      return sequenceIndex;
    }

    /** @return defensive candidate values */
    public double[] getCandidateValues() {
      return candidateValues.clone();
    }

    /** @return selected raw objective */
    public double getRawObjective() {
      return rawObjective;
    }

    /** @return true when this row was accepted during coordinate search */
    public boolean isAcceptedAsIncumbent() {
      return acceptedAsIncumbent;
    }

    /** @return true when this row is the terminal accepted-point replay */
    public boolean isAcceptedPointReplay() {
      return acceptedPointReplay;
    }

    /** @return complete fail-closed candidate evaluation */
    public CandidateSetEvaluationResult getEvaluation() {
      return evaluation;
    }
  }

  /** Immutable terminal mixed operating-point search result. */
  public static final class OperatingPointSearchResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Stable optimizer identifier. */
    private final String id;
    /** Human-readable optimizer name. */
    private final String name;
    /** Engineering basis for the search. */
    private final String provenance;
    /** Atomic action-set identifier. */
    private final String actionSetId;
    /** Frozen selected-objective identity. */
    private final ObjectiveSnapshot objective;
    /** Search seed. */
    private final double[] initialCandidate;
    /** Hard evaluation budget including replay. */
    private final int maximumEvaluations;
    /** Initial continuous range fraction. */
    private final double initialStepFraction;
    /** Final continuous range-fraction tolerance. */
    private final double relativeStepTolerance;
    /** Required raw-objective improvement. */
    private final double objectiveImprovementTolerance;
    /** Source of the objective tolerance. */
    private final String objectiveToleranceProvenance;
    /** Relative selected-objective replay tolerance. */
    private final double replayRelativeTolerance;
    /** Source of the replay tolerance. */
    private final String replayToleranceProvenance;
    /** Terminal search classification. */
    private final SearchOutcome outcome;
    /** Complete candidate trace. */
    private final List<CandidateRecord> candidates;
    /** Successful terminal accepted-point replay, or null. */
    private final CandidateRecord acceptedPointReplay;
    /** Terminal continuous range fraction. */
    private final double finalStepFraction;
    /** Whether the continuous step met tolerance. */
    private final boolean converged;
    /** Measured wall-clock runtime in seconds. */
    private final double runtimeSeconds;
    /** Immutable terminal diagnostics. */
    private final List<String> diagnostics;

    /** Creates an immutable terminal result. */
    private OperatingPointSearchResult(String id, String name, String provenance, String actionSetId,
        ObjectiveSnapshot objective, double[] initialCandidate, int maximumEvaluations, double initialStepFraction,
        double relativeStepTolerance, double objectiveImprovementTolerance, String objectiveToleranceProvenance,
        double replayRelativeTolerance, String replayToleranceProvenance, SearchOutcome outcome,
        List<CandidateRecord> candidates, CandidateRecord acceptedPointReplay, double finalStepFraction,
        boolean converged, double runtimeSeconds, List<String> diagnostics) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.actionSetId = actionSetId;
      this.objective = objective;
      this.initialCandidate = initialCandidate.clone();
      this.maximumEvaluations = maximumEvaluations;
      this.initialStepFraction = initialStepFraction;
      this.relativeStepTolerance = relativeStepTolerance;
      this.objectiveImprovementTolerance = objectiveImprovementTolerance;
      this.objectiveToleranceProvenance = objectiveToleranceProvenance;
      this.replayRelativeTolerance = replayRelativeTolerance;
      this.replayToleranceProvenance = replayToleranceProvenance;
      this.outcome = outcome;
      this.candidates = Collections.unmodifiableList(new ArrayList<CandidateRecord>(candidates));
      this.acceptedPointReplay = acceptedPointReplay;
      this.finalStepFraction = finalStepFraction;
      this.converged = converged;
      this.runtimeSeconds = runtimeSeconds;
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** @return stable optimizer identifier */
    public String getId() {
      return id;
    }

    /** @return optimizer name */
    public String getName() {
      return name;
    }

    /** @return engineering provenance */
    public String getProvenance() {
      return provenance;
    }

    /** @return atomic action-set identifier */
    public String getActionSetId() {
      return actionSetId;
    }

    /** @return selected objective metadata */
    public ObjectiveSnapshot getObjective() {
      return objective;
    }

    /** @return defensive initial candidate */
    public double[] getInitialCandidate() {
      return initialCandidate.clone();
    }

    /** @return configured maximum evaluations */
    public int getMaximumEvaluations() {
      return maximumEvaluations;
    }

    /** @return configured initial range fraction */
    public double getInitialStepFraction() {
      return initialStepFraction;
    }

    /** @return configured relative step tolerance */
    public double getRelativeStepTolerance() {
      return relativeStepTolerance;
    }

    /** @return absolute objective improvement tolerance */
    public double getObjectiveImprovementTolerance() {
      return objectiveImprovementTolerance;
    }

    /** @return objective tolerance provenance */
    public String getObjectiveToleranceProvenance() {
      return objectiveToleranceProvenance;
    }

    /** @return relative replay tolerance */
    public double getReplayRelativeTolerance() {
      return replayRelativeTolerance;
    }

    /** @return replay tolerance provenance */
    public String getReplayToleranceProvenance() {
      return replayToleranceProvenance;
    }

    /** @return terminal search classification */
    public SearchOutcome getOutcome() {
      return outcome;
    }

    /** @return fresh immutable trace */
    public List<CandidateRecord> getCandidates() {
      return Collections.unmodifiableList(new ArrayList<CandidateRecord>(candidates));
    }

    /** @return number of complete simulator evaluations including replay */
    public int getEvaluationCount() {
      return candidates.size();
    }

    /** @return successful terminal replay, or null */
    public CandidateRecord getAcceptedPointReplay() {
      return acceptedPointReplay;
    }

    /** @return defensive accepted action vector, or an empty array */
    public double[] getAcceptedCandidateValues() {
      return acceptedPointReplay == null ? new double[0] : acceptedPointReplay.getCandidateValues();
    }

    /** @return final continuous range fraction */
    public double getFinalStepFraction() {
      return finalStepFraction;
    }

    /** @return true when the continuous step met tolerance */
    public boolean isConverged() {
      return converged;
    }

    /** @return measured wall-clock runtime in seconds */
    public double getRuntimeSeconds() {
      return runtimeSeconds;
    }

    /** @return fresh immutable diagnostics */
    public List<String> getDiagnostics() {
      return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** @return true only when final accepted-point replay succeeded */
    public boolean isAcceptedPointReplayed() {
      return acceptedPointReplay != null;
    }

    /** @return true when every accepted action was applied and restored with evidence */
    public boolean isActionsComplete() {
      if (acceptedPointReplay == null) {
        return false;
      }
      CandidateSetEvaluationResult evaluation = acceptedPointReplay.getEvaluation();
      List<ActionCandidateEvidence> evidence = evaluation.getActionEvidence();
      return evidence.size() == evaluation.getActions().size() && evaluation.isBaselineRestored();
    }

    /** @return true when the replay contains finite objective and complete finite constraint evidence */
    public boolean isConstraintEvidenceComplete() {
      if (acceptedPointReplay == null) {
        return false;
      }
      CandidateSetEvaluationResult evaluation = acceptedPointReplay.getEvaluation();
      for (CandidateConstraintEvidence row : evaluation.getConstraintEvidence()) {
        if (!isFinite(row.getValue()) || !isFinite(row.getMargin())) {
          return false;
        }
      }
      for (InstalledEquipmentCapacityEvidence row : evaluation.getInstalledEquipmentCapacityEvidence()) {
        if (!row.hasFiniteEvidence() || !isFinite(row.getNormalizedUtilization())) {
          return false;
        }
      }
      for (ProcessBoundaryConstraintEvidence row : evaluation.getProcessBoundaryConstraintEvidence()) {
        if (!row.isCalculable()) {
          return false;
        }
      }
      for (PlantConstraintEvidence row : evaluation.getPlantConstraintEvidence()) {
        if (!row.hasAvailableEvidence()) {
          return false;
        }
      }
      return !evaluation.getConstraintEvidence().isEmpty();
    }

    /** @return true when accepted setpoints and objective are finite */
    public boolean isCandidateFinite() {
      if (acceptedPointReplay == null || !isFinite(acceptedPointReplay.getRawObjective())) {
        return false;
      }
      for (double value : acceptedPointReplay.getCandidateValues()) {
        if (!isFinite(value)) {
          return false;
        }
      }
      return true;
    }

    /**
     * Returns exact continuous-task optimizer evidence keys for one current cycle.
     *
     * @param cycleId current living-task cycle identifier
     * @return ordered serializable guard evidence
     */
    public Map<String, Object> getOptimizerEvidence(String cycleId) {
      Map<String, Object> evidence = new LinkedHashMap<String, Object>();
      evidence.put("cycle_id", requireText(cycleId, "Cycle identifier"));
      CandidateSetEvaluationResult replay = acceptedPointReplay == null ? null : acceptedPointReplay.getEvaluation();
      evidence.put("simulation_converged", replay != null && replay.isCandidateSimulationConverged());
      evidence.put("candidate_feasible", replay != null && replay.isFeasible());
      evidence.put("candidate_finite", isCandidateFinite());
      evidence.put("constraint_evidence_complete", isConstraintEvidenceComplete());
      evidence.put("state_restore_complete",
          replay != null && replay.isBaselineRestored() && replay.isBaselineSimulationConverged());
      evidence.put("accepted_point_replayed", isAcceptedPointReplayed());
      evidence.put("actions_complete", isActionsComplete());
      evidence.put("evaluation_count", getEvaluationCount());
      evidence.put("runtime_seconds", runtimeSeconds);
      return Collections.unmodifiableMap(evidence);
    }
  }
}
