package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;

/**
 * Samples one- or two-dimensional operating-envelope slices with the existing fail-closed transactional action
 * evaluator.
 *
 * <p>
 * Every point is a complete action vector evaluated by {@link ProcessModelOperatingActionSetEvaluator}. Infeasible
 * candidates remain in the trace when baseline recovery succeeds. Sampling stops immediately when restoration or
 * restored-baseline convergence fails. Leading-bottleneck transitions use only the evaluator's already ranked, finite
 * installed-equipment evidence; they are sampled identity changes, not proof of a continuous boundary, an optimizer
 * active set, or operating approval.
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
   * Samples one action while holding all other actions at anchor values.
   *
   * @param actionId exact action identifier
   * @param values ordered axis values
   * @param anchorValues full action vector for non-axis values
   * @return immutable slice result
   */
  public SliceResult evaluateOneDimensional(String actionId, double[] values, double[] anchorValues) {
    return evaluate(new String[] {actionId}, new double[][] {copy(values)}, anchorValues);
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
        new double[][] {copy(firstValues), copy(secondValues)}, anchorValues);
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
    List<Point> points = new ArrayList<Point>();
    List<Transition> transitions = new ArrayList<Transition>();
    List<String> diagnostics = new ArrayList<String>();
    Outcome outcome = Outcome.COMPLETE;

    if (axisIds.length == 1) {
      for (int i = 0; i < axes[0].length; i++) {
        Point point = evaluatePoint(anchor, actionIndexes, axes, new int[] {i}, points.size());
        points.add(point);
        if (i > 0) {
          transition(transitions, axisIds[0], points.get(i - 1), point, 0);
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
          }
          if (i > 0) {
            transition(transitions, axisIds[0], points.get((i - 1) * secondCount + j), point, 0);
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

    if (outcome == Outcome.COMPLETE) {
      diagnostics.add("All requested points completed with safe baseline recovery");
    }
    diagnostics.add("Transitions are sampled installed-equipment bottleneck changes only");
    return new SliceResult(id, name, provenance, evaluator.getId(), axisIds, axes, anchor, pointCount(axes), outcome,
        points, transitions, diagnostics);
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
    return new Point(sequence, gridIndexes, coordinates, candidate, result, leading(result));
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
  private static InstalledEquipmentCapacityEvidence leading(CandidateSetEvaluationResult result) {
    for (InstalledEquipmentCapacityEvidence evidence : result.getInstalledEquipmentCapacityEvidence()) {
      if (evidence.isEnabled() && evidence.hasFiniteEvidence()) {
        return evidence;
      }
    }
    return null;
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
    InstalledEquipmentCapacityEvidence first = from.getLeadingInstalledConstraint();
    InstalledEquipmentCapacityEvidence second = to.getLeadingInstalledConstraint();
    if (first == null || second == null
        || first.getQualifiedConstraintName().equals(second.getQualifiedConstraintName())) {
      return;
    }
    transitions
        .add(new Transition(axisId, from.getSequenceIndex(), to.getSequenceIndex(), from.getAxisValues()[axisPosition],
            to.getAxisValues()[axisPosition], first.getQualifiedConstraintName(), second.getQualifiedConstraintName()));
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

  /**
   * Copies an input vector.
   *
   * @param values source values
   * @return defensive copy or null
   */
  private static double[] copy(double[] values) {
    return values == null ? null : Arrays.copyOf(values, values.length);
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
    private final InstalledEquipmentCapacityEvidence leadingInstalledConstraint;

    /**
     * Creates one immutable point.
     *
     * @param sequenceIndex deterministic sequence
     * @param gridIndexes grid coordinates
     * @param axisValues physical axis values
     * @param candidateValues complete candidate
     * @param evaluation complete evaluator evidence
     * @param leadingInstalledConstraint leading installed evidence or null
     */
    private Point(int sequenceIndex, int[] gridIndexes, double[] axisValues, double[] candidateValues,
        CandidateSetEvaluationResult evaluation, InstalledEquipmentCapacityEvidence leadingInstalledConstraint) {
      this.sequenceIndex = sequenceIndex;
      this.gridIndexes = Arrays.copyOf(gridIndexes, gridIndexes.length);
      this.axisValues = Arrays.copyOf(axisValues, axisValues.length);
      this.candidateValues = Arrays.copyOf(candidateValues, candidateValues.length);
      this.evaluation = evaluation;
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

    /** @return leading finite installed-equipment evidence or null */
    public InstalledEquipmentCapacityEvidence getLeadingInstalledConstraint() {
      return leadingInstalledConstraint;
    }

    /** @return true only for a fully feasible evaluator result */
    public boolean isFeasible() {
      return evaluation.isFeasible();
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
    private final double[][] axisValues;
    private final double[] anchorValues;
    private final int requestedPointCount;
    private final Outcome outcome;
    private final List<Point> points;
    private final List<Transition> transitions;
    private final List<String> diagnostics;

    /**
     * Creates an immutable result.
     *
     * @param id study identifier
     * @param name study name
     * @param provenance study provenance
     * @param candidateEvaluatorId evaluator identifier
     * @param axisActionIds axis action identifiers
     * @param axisValues requested grids
     * @param anchorValues complete anchor vector
     * @param requestedPointCount requested point count
     * @param outcome terminal outcome
     * @param points completed points
     * @param transitions sampled transitions
     * @param diagnostics study diagnostics
     */
    private SliceResult(String id, String name, String provenance, String candidateEvaluatorId, String[] axisActionIds,
        double[][] axisValues, double[] anchorValues, int requestedPointCount, Outcome outcome, List<Point> points,
        List<Transition> transitions, List<String> diagnostics) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.candidateEvaluatorId = candidateEvaluatorId;
      this.axisActionIds = Arrays.copyOf(axisActionIds, axisActionIds.length);
      this.axisValues = deepCopy(axisValues);
      this.anchorValues = Arrays.copyOf(anchorValues, anchorValues.length);
      this.requestedPointCount = requestedPointCount;
      this.outcome = outcome;
      this.points = Collections.unmodifiableList(new ArrayList<Point>(points));
      this.transitions = Collections.unmodifiableList(new ArrayList<Transition>(transitions));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
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

    /** @return fresh immutable study diagnostics */
    public List<String> getDiagnostics() {
      return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
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
