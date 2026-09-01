package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicConstraintSnapshot;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;

/**
 * Performs a deterministic fixed-total allocation search through an atomic operating-action set.
 *
 * <p>
 * The optimizer transfers a shrinking quantity between pairs of continuous actions. Every trial is evaluated by
 * {@link ProcessModelOperatingActionSetEvaluator}, so all action states are captured before the first write, shared
 * constraints are evaluated on the complete vector, and the baseline is restored before the next trial. Search stops
 * immediately if a candidate cannot restore and reconverge the model.
 * </p>
 *
 * <p>
 * Results retain immutable objective identity, bounds, tolerances, every candidate evaluation, sampled objective
 * opportunity, and utilization-ranked hydraulic evidence. Pattern search is a local derivative-free method: convergence
 * means that no improving feasible pair transfer was found above the declared step tolerance. It is not proof of a
 * global optimum, a shadow price, production loss, or operational approval.
 * </p>
 *
 * <p>
 * All actions must be continuous and use the same unit as the fixed total. Independent optimizer, evaluator, and
 * {@code ProcessModel} instances are required for parallel searches.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelAllocationOptimizer {
  /** Terminal search classification. */
  public enum SearchOutcome {
    /** A feasible incumbent was found and the transfer step met the declared tolerance. */
    CONVERGED_WITH_FEASIBLE_CANDIDATE,
    /** The evaluation budget ended after at least one feasible candidate was found. */
    BUDGET_EXHAUSTED_WITH_FEASIBLE_CANDIDATE,
    /** The transfer step met tolerance without any feasible finite-objective candidate. */
    CONVERGED_WITHOUT_FEASIBLE_CANDIDATE,
    /** The evaluation budget ended without a feasible finite-objective candidate. */
    BUDGET_EXHAUSTED_WITHOUT_FEASIBLE_CANDIDATE,
    /** A candidate failed complete baseline restoration or restored-baseline convergence. */
    MODEL_RECOVERY_FAILED
  }

  /** Stable optimizer identifier. */
  private final String id;

  /** Human-readable optimizer name. */
  private final String name;

  /** Engineering basis for the allocation search. */
  private final String provenance;

  /** Atomic candidate evaluator. */
  private final ProcessModelOperatingActionSetEvaluator candidateEvaluator;

  /** Fixed shared total. */
  private final double fixedTotal;

  /** Fixed-total and action unit. */
  private final String unit;

  /** Objective index. */
  private int objectiveIndex;

  /** Optional caller seed. */
  private double[] initialAllocation;

  /** Maximum candidate evaluations. */
  private int maximumEvaluations = 200;

  /** Initial transfer step divided by the fixed total. */
  private double initialStepFraction = 0.25;

  /** Final transfer step divided by the fixed total. */
  private double relativeStepTolerance = 1.0e-4;

  /** Absolute objective improvement required to replace the incumbent. */
  private double objectiveImprovementTolerance;

  /** Engineering/numerical source of the objective tolerance. */
  private String objectiveToleranceProvenance = "exact raw-objective comparison";

  /**
   * Creates a fixed-total allocation optimizer.
   *
   * @param id stable optimizer identifier
   * @param name human-readable optimizer name
   * @param provenance engineering basis for the fixed-total allocation
   * @param candidateEvaluator atomic coupled-action evaluator
   * @param fixedTotal shared total retained by every candidate
   * @param unit shared-total unit; must exactly match every action unit
   * @throws IllegalArgumentException for invalid metadata, total, unit, actions, or action bounds
   */
  public ProcessModelAllocationOptimizer(String id, String name, String provenance,
      ProcessModelOperatingActionSetEvaluator candidateEvaluator, double fixedTotal, String unit) {
    this.id = requireText(id, "Allocation optimizer identifier");
    this.name = requireText(name, "Allocation optimizer name");
    this.provenance = requireText(provenance, "Allocation optimizer provenance");
    if (candidateEvaluator == null) {
      throw new IllegalArgumentException("Atomic candidate evaluator must not be null");
    }
    if (!isFinite(fixedTotal) || fixedTotal <= 0.0) {
      throw new IllegalArgumentException("Fixed allocation total must be finite and positive");
    }
    this.unit = requireText(unit, "Fixed allocation total unit");
    this.candidateEvaluator = candidateEvaluator;
    this.fixedTotal = fixedTotal;
    validateActions();
    validateFeasibleTotal();
    if (candidateEvaluator.getSimulationEvaluator().getObjectiveCount() == 0) {
      throw new IllegalArgumentException("Allocation search requires at least one registered objective");
    }
  }

  /** @return stable optimizer identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable optimizer name */
  public String getName() {
    return name;
  }

  /** @return engineering basis for the allocation search */
  public String getProvenance() {
    return provenance;
  }

  /** @return atomic candidate evaluator */
  public ProcessModelOperatingActionSetEvaluator getCandidateEvaluator() {
    return candidateEvaluator;
  }

  /** @return fixed shared total */
  public double getFixedTotal() {
    return fixedTotal;
  }

  /** @return fixed-total and action unit */
  public String getUnit() {
    return unit;
  }

  /**
   * Selects one registered raw objective for incumbent comparisons.
   *
   * @param objectiveIndex zero-based registered objective index
   * @return this optimizer
   * @throws IllegalArgumentException if the index is unavailable
   */
  public ProcessModelAllocationOptimizer setObjectiveIndex(int objectiveIndex) {
    if (objectiveIndex < 0 || objectiveIndex >= candidateEvaluator.getSimulationEvaluator().getObjectiveCount()) {
      throw new IllegalArgumentException("Objective index is outside the registered objective range");
    }
    this.objectiveIndex = objectiveIndex;
    return this;
  }

  /**
   * Sets the deterministic search seed.
   *
   * @param initialAllocation values in action declaration order and the declared common unit
   * @return this optimizer
   * @throws IllegalArgumentException if the vector violates shape, bounds, finiteness, or total
   */
  public ProcessModelAllocationOptimizer setInitialAllocation(double[] initialAllocation) {
    this.initialAllocation = validateAndCopyAllocation(initialAllocation, "Initial allocation");
    return this;
  }

  /**
   * Sets the hard candidate-evaluation budget.
   *
   * @param maximumEvaluations positive maximum, including the seed evaluation
   * @return this optimizer
   */
  public ProcessModelAllocationOptimizer setMaximumEvaluations(int maximumEvaluations) {
    if (maximumEvaluations <= 0) {
      throw new IllegalArgumentException("Maximum evaluations must be positive");
    }
    this.maximumEvaluations = maximumEvaluations;
    return this;
  }

  /**
   * Sets the initial pair-transfer step as a fraction of the fixed total.
   *
   * @param initialStepFraction value in (0, 1]
   * @return this optimizer
   */
  public ProcessModelAllocationOptimizer setInitialStepFraction(double initialStepFraction) {
    if (!isFinite(initialStepFraction) || initialStepFraction <= 0.0 || initialStepFraction > 1.0) {
      throw new IllegalArgumentException("Initial step fraction must be finite and in (0, 1]");
    }
    this.initialStepFraction = initialStepFraction;
    return this;
  }

  /**
   * Sets the convergence threshold on pair transfers relative to the fixed total.
   *
   * @param relativeStepTolerance finite positive relative threshold
   * @return this optimizer
   */
  public ProcessModelAllocationOptimizer setRelativeStepTolerance(double relativeStepTolerance) {
    if (!isFinite(relativeStepTolerance) || relativeStepTolerance <= 0.0) {
      throw new IllegalArgumentException("Relative step tolerance must be finite and positive");
    }
    this.relativeStepTolerance = relativeStepTolerance;
    return this;
  }

  /**
   * Sets the absolute raw-objective improvement needed to replace a feasible incumbent.
   *
   * @param tolerance finite non-negative tolerance in the selected objective unit
   * @param toleranceProvenance source or numerical basis for the tolerance
   * @return this optimizer
   */
  public ProcessModelAllocationOptimizer setObjectiveImprovementTolerance(double tolerance,
      String toleranceProvenance) {
    if (!isFinite(tolerance) || tolerance < 0.0) {
      throw new IllegalArgumentException("Objective improvement tolerance must be finite and non-negative");
    }
    this.objectiveImprovementTolerance = tolerance;
    this.objectiveToleranceProvenance = requireText(toleranceProvenance, "Objective improvement tolerance provenance");
    return this;
  }

  /**
   * Executes a deterministic transfer-based allocation search.
   *
   * @return immutable serializable search definition, candidate trace, optimum, and bottleneck evidence
   */
  public synchronized AllocationSearchResult optimize() {
    validateConfiguration();
    ObjectiveDefinition objective = candidateEvaluator.getSimulationEvaluator().getObjectives().get(objectiveIndex);
    ObjectiveSnapshot objectiveSnapshot = new ObjectiveSnapshot(objectiveIndex, objective);
    double[] seed = initialAllocation == null ? createDefaultAllocation() : initialAllocation.clone();
    List<CandidateRecord> records = new ArrayList<CandidateRecord>();
    List<String> diagnostics = new ArrayList<String>();
    CandidateRecord incumbent = evaluateCandidate(seed, objectiveSnapshot, records, true);
    CandidateRecord bestSampledObjective = isFiniteObjective(incumbent) ? incumbent : null;
    if (!isEligibleIncumbent(incumbent)) {
      incumbent = null;
    }
    if (hasUnsafeRecovery(records.get(records.size() - 1))) {
      diagnostics.add("Search stopped because the seed did not restore and reconverge the process model");
      return createResult(objectiveSnapshot, seed, SearchOutcome.MODEL_RECOVERY_FAILED, records, incumbent,
          bestSampledObjective, initialStepFraction * fixedTotal, false, diagnostics);
    }

    double step = initialStepFraction * fixedTotal;
    double threshold = relativeStepTolerance * fixedTotal;
    boolean unsafeRecovery = false;
    while (step > threshold && records.size() < maximumEvaluations && !unsafeRecovery) {
      boolean improved = false;
      double[] reference = incumbent == null ? seed : incumbent.getCandidateValues();
      for (int receiver = 0; receiver < reference.length && records.size() < maximumEvaluations; receiver++) {
        for (int donor = 0; donor < reference.length && records.size() < maximumEvaluations; donor++) {
          if (receiver == donor || !canTransfer(reference, receiver, donor, step)) {
            continue;
          }
          double[] trial = reference.clone();
          trial[receiver] += step;
          trial[donor] -= step;
          CandidateRecord candidate = evaluateCandidate(trial, objectiveSnapshot, records, false);
          if (hasUnsafeRecovery(candidate)) {
            diagnostics.add("Search stopped after candidate " + candidate.getSequenceIndex()
                + " because complete baseline recovery failed");
            unsafeRecovery = true;
            break;
          }
          if (isFiniteObjective(candidate)
              && (bestSampledObjective == null || isBetter(candidate, bestSampledObjective, objectiveSnapshot, 0.0))) {
            bestSampledObjective = candidate;
          }
          if (isEligibleIncumbent(candidate) && (incumbent == null
              || isBetter(candidate, incumbent, objectiveSnapshot, objectiveImprovementTolerance))) {
            candidate = candidate.withAcceptedAsIncumbent(true);
            records.set(records.size() - 1, candidate);
            incumbent = candidate;
            reference = candidate.getCandidateValues();
            improved = true;
          }
        }
      }
      if (!improved && !unsafeRecovery) {
        step *= 0.5;
      }
    }

    SearchOutcome outcome;
    if (unsafeRecovery) {
      outcome = SearchOutcome.MODEL_RECOVERY_FAILED;
    } else if (records.size() >= maximumEvaluations && step > threshold) {
      outcome = incumbent == null ? SearchOutcome.BUDGET_EXHAUSTED_WITHOUT_FEASIBLE_CANDIDATE
          : SearchOutcome.BUDGET_EXHAUSTED_WITH_FEASIBLE_CANDIDATE;
      diagnostics.add("Candidate-evaluation budget was exhausted before the transfer step converged");
    } else {
      outcome = incumbent == null ? SearchOutcome.CONVERGED_WITHOUT_FEASIBLE_CANDIDATE
          : SearchOutcome.CONVERGED_WITH_FEASIBLE_CANDIDATE;
      diagnostics.add("Pair-transfer step met the declared relative tolerance");
    }
    if (incumbent == null) {
      diagnostics.add("No candidate combined feasibility, a finite selected objective, and complete recovery");
    }
    return createResult(objectiveSnapshot, seed, outcome, records, incumbent, bestSampledObjective, step,
        !unsafeRecovery && step <= threshold, diagnostics);
  }

  /** Creates one immutable candidate record and appends it to the trace. */
  private CandidateRecord evaluateCandidate(double[] allocation, ObjectiveSnapshot objective,
      List<CandidateRecord> records, boolean acceptedAsIncumbent) {
    CandidateSetEvaluationResult evaluation = candidateEvaluator.evaluate(allocation);
    double rawObjective = valueAt(evaluation.getRawObjectives(), objective.getIndex());
    double minimizerObjective = valueAt(evaluation.getObjectives(), objective.getIndex());
    boolean accepted = acceptedAsIncumbent && evaluation.isFeasible() && evaluation.isBaselineRestored()
        && evaluation.isBaselineSimulationConverged() && isFinite(rawObjective);
    CandidateRecord record = new CandidateRecord(records.size(), allocation, rawObjective, minimizerObjective, accepted,
        evaluation);
    records.add(record);
    return record;
  }

  /** Creates the final immutable result. */
  private AllocationSearchResult createResult(ObjectiveSnapshot objective, double[] seed, SearchOutcome outcome,
      List<CandidateRecord> records, CandidateRecord incumbent, CandidateRecord bestSampledObjective, double finalStep,
      boolean converged, List<String> diagnostics) {
    return new AllocationSearchResult(id, name, provenance, candidateEvaluator.getId(), candidateEvaluator.getName(),
        candidateEvaluator.getProvenance(), fixedTotal, unit, lowerBounds(), upperBounds(), seed, maximumEvaluations,
        initialStepFraction, relativeStepTolerance, objectiveImprovementTolerance, objectiveToleranceProvenance,
        objective, outcome, records, incumbent, bestSampledObjective, finalStep, converged, diagnostics);
  }

  /** Returns whether a transfer respects both affected action bounds. */
  private boolean canTransfer(double[] allocation, int receiver, int donor, double step) {
    List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
    return allocation[receiver] + step <= actions.get(receiver).getUpperBound() + totalTolerance()
        && allocation[donor] - step >= actions.get(donor).getLowerBound() - totalTolerance();
  }

  /** Returns true when a candidate is safe and eligible to become the incumbent. */
  private static boolean isEligibleIncumbent(CandidateRecord candidate) {
    return candidate != null && candidate.getEvaluation().isFeasible() && candidate.getEvaluation().isBaselineRestored()
        && candidate.getEvaluation().isBaselineSimulationConverged() && isFinite(candidate.getRawObjective());
  }

  /** Returns true for a candidate that has a sampled finite objective. */
  private static boolean isFiniteObjective(CandidateRecord candidate) {
    return candidate != null && candidate.getEvaluation().isCandidateSimulationConverged()
        && isFinite(candidate.getRawObjective());
  }

  /** Returns true when continuing would risk using a mutated or unconverged model baseline. */
  private static boolean hasUnsafeRecovery(CandidateRecord candidate) {
    return !candidate.getEvaluation().isBaselineRestored()
        || !candidate.getEvaluation().isBaselineSimulationConverged();
  }

  /** Compares selected raw objectives using frozen direction metadata. */
  private static boolean isBetter(CandidateRecord candidate, CandidateRecord reference, ObjectiveSnapshot objective,
      double tolerance) {
    if (objective.getDirection() == ObjectiveDefinition.Direction.MAXIMIZE) {
      return candidate.getRawObjective() > reference.getRawObjective() + tolerance;
    }
    return candidate.getRawObjective() < reference.getRawObjective() - tolerance;
  }

  /** Returns one objective value or NaN when a candidate did not produce the selected row. */
  private static double valueAt(double[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : Double.NaN;
  }

  /** Validates continuous common-unit actions and finite bounds. */
  private void validateActions() {
    for (ProcessModelOperatingAction action : candidateEvaluator.getActions()) {
      if (action.isDiscrete()) {
        throw new IllegalArgumentException(
            "Fixed-total allocation search requires continuous actions: " + action.getId());
      }
      if (!unit.equals(action.getUnit())) {
        throw new IllegalArgumentException("Action unit must exactly match fixed-total unit for " + action.getId()
            + ": " + action.getUnit() + " != " + unit);
      }
      if (!isFinite(action.getLowerBound()) || !isFinite(action.getUpperBound())) {
        throw new IllegalArgumentException("Allocation action bounds must be finite: " + action.getId());
      }
    }
  }

  /** Validates that at least one bound-respecting fixed-total allocation exists. */
  private void validateFeasibleTotal() {
    double minimum = sum(lowerBounds());
    double maximum = sum(upperBounds());
    if (fixedTotal < minimum - totalTolerance() || fixedTotal > maximum + totalTolerance()) {
      throw new IllegalArgumentException(
          "Fixed total is outside the aggregate action bounds [" + minimum + ", " + maximum + "] " + unit);
    }
  }

  /** Validates cross-field configuration immediately before a search. */
  private void validateConfiguration() {
    setObjectiveIndex(objectiveIndex);
    if (relativeStepTolerance >= initialStepFraction) {
      throw new IllegalStateException("Relative step tolerance must be smaller than the initial step fraction");
    }
  }

  /** Creates a deterministic water-filled seed from action lower bounds. */
  private double[] createDefaultAllocation() {
    double[] allocation = lowerBounds();
    double remaining = fixedTotal - sum(allocation);
    List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
    while (remaining > totalTolerance()) {
      int available = 0;
      for (int i = 0; i < allocation.length; i++) {
        if (allocation[i] < actions.get(i).getUpperBound() - totalTolerance()) {
          available++;
        }
      }
      if (available == 0) {
        break;
      }
      double share = remaining / available;
      double distributed = 0.0;
      for (int i = 0; i < allocation.length; i++) {
        double headroom = actions.get(i).getUpperBound() - allocation[i];
        if (headroom > totalTolerance()) {
          double addition = Math.min(share, headroom);
          allocation[i] += addition;
          distributed += addition;
        }
      }
      remaining -= distributed;
    }
    correctTotalRounding(allocation);
    return validateAndCopyAllocation(allocation, "Generated initial allocation");
  }

  /** Validates and defensively copies a fixed-total allocation. */
  private double[] validateAndCopyAllocation(double[] allocation, String description) {
    if (allocation == null || allocation.length != candidateEvaluator.getActions().size()) {
      throw new IllegalArgumentException(description + " length must equal the action count");
    }
    double[] copy = allocation.clone();
    List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
    for (int i = 0; i < copy.length; i++) {
      if (!isFinite(copy[i])) {
        throw new IllegalArgumentException(description + " contains a non-finite value at index " + i);
      }
      if (copy[i] < actions.get(i).getLowerBound() - totalTolerance()
          || copy[i] > actions.get(i).getUpperBound() + totalTolerance()) {
        throw new IllegalArgumentException(description + " violates action bounds at index " + i);
      }
    }
    if (Math.abs(sum(copy) - fixedTotal) > totalTolerance()) {
      throw new IllegalArgumentException(
          description + " must sum to " + fixedTotal + " " + unit + " within " + totalTolerance() + " " + unit);
    }
    correctTotalRounding(copy);
    return copy;
  }

  /** Corrects only roundoff-scale total residual on one action with available room. */
  private void correctTotalRounding(double[] allocation) {
    double residual = fixedTotal - sum(allocation);
    if (residual == 0.0) {
      return;
    }
    List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
    for (int i = allocation.length - 1; i >= 0; i--) {
      double corrected = allocation[i] + residual;
      if (corrected >= actions.get(i).getLowerBound() - totalTolerance()
          && corrected <= actions.get(i).getUpperBound() + totalTolerance()) {
        allocation[i] = corrected;
        return;
      }
    }
  }

  /** @return action lower bounds in declaration order */
  private double[] lowerBounds() {
    List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
    double[] values = new double[actions.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = actions.get(i).getLowerBound();
    }
    return values;
  }

  /** @return action upper bounds in declaration order */
  private double[] upperBounds() {
    List<ProcessModelOperatingAction> actions = candidateEvaluator.getActions();
    double[] values = new double[actions.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = actions.get(i).getUpperBound();
    }
    return values;
  }

  /** Returns fixed-total comparison tolerance. */
  private double totalTolerance() {
    return Math.max(1.0e-12, Math.abs(fixedTotal) * 1.0e-12);
  }

  /** Sums one array in declaration order. */
  private static double sum(double[] values) {
    double result = 0.0;
    for (double value : values) {
      result += value;
    }
    return result;
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

  /** Immutable selected-objective identity. */
  public static final class ObjectiveSnapshot implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Registered objective index. */
    private final int index;

    /** Objective name. */
    private final String name;

    /** Objective direction. */
    private final ObjectiveDefinition.Direction direction;

    /** Objective unit. */
    private final String unit;

    /** Objective weight metadata. */
    private final double weight;

    /** Creates a frozen objective record. */
    private ObjectiveSnapshot(int index, ObjectiveDefinition definition) {
      this.index = index;
      this.name = definition.getName();
      this.direction = definition.getDirection();
      this.unit = definition.getUnit();
      this.weight = definition.getWeight();
    }

    /** @return registered objective index */
    public int getIndex() {
      return index;
    }

    /** @return objective name */
    public String getName() {
      return name;
    }

    /** @return objective direction */
    public ObjectiveDefinition.Direction getDirection() {
      return direction;
    }

    /** @return objective unit, possibly null when not declared */
    public String getUnit() {
      return unit;
    }

    /** @return objective weight metadata */
    public double getWeight() {
      return weight;
    }
  }

  /** Immutable evidence for one evaluated allocation. */
  public static final class CandidateRecord implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Zero-based evaluation sequence. */
    private final int sequenceIndex;

    /** Fixed-total candidate. */
    private final double[] candidateValues;

    /** Selected raw objective. */
    private final double rawObjective;

    /** Selected minimizer-sign objective. */
    private final double minimizerObjective;

    /** Whether this candidate replaced the feasible incumbent. */
    private final boolean acceptedAsIncumbent;

    /** Complete atomic evaluation evidence. */
    private final CandidateSetEvaluationResult evaluation;

    /** Creates an immutable candidate record. */
    private CandidateRecord(int sequenceIndex, double[] candidateValues, double rawObjective, double minimizerObjective,
        boolean acceptedAsIncumbent, CandidateSetEvaluationResult evaluation) {
      this.sequenceIndex = sequenceIndex;
      this.candidateValues = candidateValues.clone();
      this.rawObjective = rawObjective;
      this.minimizerObjective = minimizerObjective;
      this.acceptedAsIncumbent = acceptedAsIncumbent;
      this.evaluation = evaluation;
    }

    /** Creates a copy with updated incumbent acceptance. */
    private CandidateRecord withAcceptedAsIncumbent(boolean accepted) {
      return new CandidateRecord(sequenceIndex, candidateValues, rawObjective, minimizerObjective, accepted,
          evaluation);
    }

    /** @return zero-based evaluation sequence */
    public int getSequenceIndex() {
      return sequenceIndex;
    }

    /** @return defensive fixed-total candidate array */
    public double[] getCandidateValues() {
      return candidateValues.clone();
    }

    /** @return selected raw objective, or NaN when unavailable */
    public double getRawObjective() {
      return rawObjective;
    }

    /** @return selected minimizer-sign objective, or NaN when unavailable */
    public double getMinimizerObjective() {
      return minimizerObjective;
    }

    /** @return whether this candidate replaced the feasible incumbent */
    public boolean isAcceptedAsIncumbent() {
      return acceptedAsIncumbent;
    }

    /** @return complete atomic candidate evidence */
    public CandidateSetEvaluationResult getEvaluation() {
      return evaluation;
    }
  }

  /** Immutable allocation-search result for Java serialization and JPype inspection. */
  public static final class AllocationSearchResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String provenance;
    private final String actionSetId;
    private final String actionSetName;
    private final String actionSetProvenance;
    private final double fixedTotal;
    private final String unit;
    private final double[] lowerBounds;
    private final double[] upperBounds;
    private final double[] initialAllocation;
    private final int maximumEvaluations;
    private final double initialStepFraction;
    private final double relativeStepTolerance;
    private final double objectiveImprovementTolerance;
    private final String objectiveToleranceProvenance;
    private final ObjectiveSnapshot objective;
    private final SearchOutcome outcome;
    private final List<CandidateRecord> candidates;
    private final CandidateRecord bestFeasibleCandidate;
    private final CandidateRecord bestSampledObjectiveCandidate;
    private final double finalTransferStep;
    private final boolean converged;
    private final List<String> diagnostics;

    /** Creates immutable search evidence. */
    private AllocationSearchResult(String id, String name, String provenance, String actionSetId, String actionSetName,
        String actionSetProvenance, double fixedTotal, String unit, double[] lowerBounds, double[] upperBounds,
        double[] initialAllocation, int maximumEvaluations, double initialStepFraction, double relativeStepTolerance,
        double objectiveImprovementTolerance, String objectiveToleranceProvenance, ObjectiveSnapshot objective,
        SearchOutcome outcome, List<CandidateRecord> candidates, CandidateRecord bestFeasibleCandidate,
        CandidateRecord bestSampledObjectiveCandidate, double finalTransferStep, boolean converged,
        List<String> diagnostics) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.actionSetId = actionSetId;
      this.actionSetName = actionSetName;
      this.actionSetProvenance = actionSetProvenance;
      this.fixedTotal = fixedTotal;
      this.unit = unit;
      this.lowerBounds = lowerBounds.clone();
      this.upperBounds = upperBounds.clone();
      this.initialAllocation = initialAllocation.clone();
      this.maximumEvaluations = maximumEvaluations;
      this.initialStepFraction = initialStepFraction;
      this.relativeStepTolerance = relativeStepTolerance;
      this.objectiveImprovementTolerance = objectiveImprovementTolerance;
      this.objectiveToleranceProvenance = objectiveToleranceProvenance;
      this.objective = objective;
      this.outcome = outcome;
      this.candidates = Collections.unmodifiableList(new ArrayList<CandidateRecord>(candidates));
      this.bestFeasibleCandidate = bestFeasibleCandidate;
      this.bestSampledObjectiveCandidate = bestSampledObjectiveCandidate;
      this.finalTransferStep = finalTransferStep;
      this.converged = converged;
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
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

    /** @return stable atomic action-set identifier */
    public String getActionSetId() {
      return actionSetId;
    }

    /** @return atomic action-set name */
    public String getActionSetName() {
      return actionSetName;
    }

    /** @return engineering basis for the atomic action set */
    public String getActionSetProvenance() {
      return actionSetProvenance;
    }

    /** @return fixed total conserved by every candidate */
    public double getFixedTotal() {
      return fixedTotal;
    }

    /** @return common action and fixed-total unit */
    public String getUnit() {
      return unit;
    }

    /** @return defensive action lower bounds */
    public double[] getLowerBounds() {
      return lowerBounds.clone();
    }

    /** @return defensive action upper bounds */
    public double[] getUpperBounds() {
      return upperBounds.clone();
    }

    /** @return defensive actual seed allocation */
    public double[] getInitialAllocation() {
      return initialAllocation.clone();
    }

    /** @return configured maximum candidate evaluations */
    public int getMaximumEvaluations() {
      return maximumEvaluations;
    }

    /** @return initial transfer step divided by fixed total */
    public double getInitialStepFraction() {
      return initialStepFraction;
    }

    /** @return final transfer-step threshold divided by fixed total */
    public double getRelativeStepTolerance() {
      return relativeStepTolerance;
    }

    /** @return absolute raw-objective improvement tolerance */
    public double getObjectiveImprovementTolerance() {
      return objectiveImprovementTolerance;
    }

    /** @return engineering/numerical source of the objective tolerance */
    public String getObjectiveToleranceProvenance() {
      return objectiveToleranceProvenance;
    }

    /** @return immutable selected-objective identity */
    public ObjectiveSnapshot getObjective() {
      return objective;
    }

    /** @return terminal search classification */
    public SearchOutcome getOutcome() {
      return outcome;
    }

    /** @return true only when the transfer step met its declared threshold */
    public boolean isConverged() {
      return converged;
    }

    /** @return true when every evaluated candidate restored and reconverged the baseline */
    public boolean isModelRecovered() {
      return outcome != SearchOutcome.MODEL_RECOVERY_FAILED;
    }

    /** @return number of simulator candidate evaluations */
    public int getEvaluationCount() {
      return candidates.size();
    }

    /** @return final absolute transfer step in the declared common unit */
    public double getFinalTransferStep() {
      return finalTransferStep;
    }

    /** @return fresh immutable complete candidate trace */
    public List<CandidateRecord> getCandidates() {
      return Collections.unmodifiableList(new ArrayList<CandidateRecord>(candidates));
    }

    /** @return best feasible finite-objective candidate, or null */
    public CandidateRecord getBestFeasibleCandidate() {
      return bestFeasibleCandidate;
    }

    /** @return best sampled objective candidate under the declared direction, feasible or not */
    public CandidateRecord getBestSampledObjectiveCandidate() {
      return bestSampledObjectiveCandidate;
    }

    /**
     * Returns the non-negative sampled objective gap between the best feasible and best sampled candidates.
     *
     * <p>
     * This is a diagnostic over sampled points, not a global production-loss or economic-shadow-value estimate.
     * </p>
     *
     * @return sampled gap in the raw objective unit, or NaN when either candidate is unavailable
     */
    public double getSampledObjectiveOpportunityGap() {
      if (bestFeasibleCandidate == null || bestSampledObjectiveCandidate == null) {
        return Double.NaN;
      }
      if (objective.getDirection() == ObjectiveDefinition.Direction.MAXIMIZE) {
        return Math.max(0.0, bestSampledObjectiveCandidate.getRawObjective() - bestFeasibleCandidate.getRawObjective());
      }
      return Math.max(0.0, bestFeasibleCandidate.getRawObjective() - bestSampledObjectiveCandidate.getRawObjective());
    }

    /** @return utilization-ranked hydraulic constraints at the best feasible candidate */
    public List<HydraulicConstraintSnapshot> getRankedHydraulicConstraintsAtBestFeasible() {
      return rankConstraints(bestFeasibleCandidate);
    }

    /** @return utilization-ranked constraints at the best sampled objective candidate */
    public List<HydraulicConstraintSnapshot> getRankedHydraulicConstraintsAtBestSampledObjective() {
      return rankConstraints(bestSampledObjectiveCandidate);
    }

    /** @return complete installed-capacity evidence at the best feasible candidate */
    public List<InstalledEquipmentCapacityEvidence> getInstalledCapacityEvidenceAtBestFeasible() {
      return installedCapacityEvidence(bestFeasibleCandidate);
    }

    /** @return complete installed-capacity evidence at the best sampled objective candidate */
    public List<InstalledEquipmentCapacityEvidence> getInstalledCapacityEvidenceAtBestSampledObjective() {
      return installedCapacityEvidence(bestSampledObjectiveCandidate);
    }

    /** Returns one candidate's fresh immutable installed-capacity evidence. */
    private static List<InstalledEquipmentCapacityEvidence> installedCapacityEvidence(CandidateRecord candidate) {
      if (candidate == null) {
        return Collections.emptyList();
      }
      return candidate.getEvaluation().getInstalledEquipmentCapacityEvidence();
    }

    /** @return fresh immutable search diagnostics */
    public List<String> getDiagnostics() {
      return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** Creates stable descending-utilization evidence without mutating candidate results. */
    private static List<HydraulicConstraintSnapshot> rankConstraints(CandidateRecord candidate) {
      if (candidate == null) {
        return Collections.emptyList();
      }
      List<HydraulicConstraintSnapshot> ranked = new ArrayList<HydraulicConstraintSnapshot>(
          candidate.getEvaluation().getHydraulicConstraints());
      Collections.sort(ranked, new Comparator<HydraulicConstraintSnapshot>() {
        @Override
        public int compare(HydraulicConstraintSnapshot first, HydraulicConstraintSnapshot second) {
          boolean firstFinite = first.hasFiniteValue();
          boolean secondFinite = second.hasFiniteValue();
          if (firstFinite != secondFinite) {
            return firstFinite ? -1 : 1;
          }
          if (firstFinite) {
            int utilizationOrder = Double.compare(second.getUtilization(), first.getUtilization());
            if (utilizationOrder != 0) {
              return utilizationOrder;
            }
          }
          return first.getBinding().getQualifiedConstraintName()
              .compareTo(second.getBinding().getQualifiedConstraintName());
        }
      });
      return Collections.unmodifiableList(ranked);
    }
  }
}
