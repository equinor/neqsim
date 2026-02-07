package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Reusable penalty calculator for constrained process optimization.
 *
 * <p>
 * This utility class exposes the adaptive penalty logic used internally by
 * {@link ProductionOptimizer} so that external optimizers can apply the same penalty formulation
 * without reimplementing it.
 * </p>
 *
 * <p>
 * <strong>Usage with an external optimizer (e.g., SciPy):</strong>
 * </p>
 *
 * <pre>
 * // Build constraints once
 * ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();
 * calc.addEquipmentCapacityConstraints(processSystem);
 * calc.addConstraint(myCustomConstraint);
 *
 * // Inside optimizer objective:
 * double rawObjective = computeObjective(processSystem);
 * double penalizedObjective = calc.penalize(rawObjective, processSystem);
 * // penalizedObjective equals rawObjective if feasible, worse if infeasible
 * </pre>
 *
 * <p>
 * <strong>Penalty formulation:</strong>
 * </p>
 * <ul>
 * <li>Adaptively scaled by the magnitude of the raw objective to be unit-independent</li>
 * <li>Hard/critical constraint violations: linear-in-margin penalty scaled by objective</li>
 * <li>Soft violations: quadratic penalty proportional to margin squared</li>
 * <li>Result is always worse than any feasible objective value</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ProcessConstraint
 * @see ConstraintSeverityLevel
 */
public class ConstraintPenaltyCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** All registered constraints. */
  private final List<ProcessConstraint> constraints = new ArrayList<ProcessConstraint>();

  /**
   * Creates an empty penalty calculator.
   */
  public ConstraintPenaltyCalculator() {}

  /**
   * Adds a single constraint.
   *
   * @param constraint the constraint to add
   * @return this calculator for chaining
   */
  public ConstraintPenaltyCalculator addConstraint(ProcessConstraint constraint) {
    if (constraint == null) {
      throw new IllegalArgumentException("constraint must not be null");
    }
    constraints.add(constraint);
    return this;
  }

  /**
   * Adds all constraints from a list.
   *
   * @param constraintList list of constraints to add
   * @return this calculator for chaining
   */
  public ConstraintPenaltyCalculator addConstraints(
      List<? extends ProcessConstraint> constraintList) {
    for (ProcessConstraint c : constraintList) {
      addConstraint(c);
    }
    return this;
  }

  /**
   * Auto-discovers and adds equipment capacity constraints from a process system.
   *
   * <p>
   * Uses {@link EquipmentCapacityStrategyRegistry} to find all equipment constraints across the
   * process and wraps them as {@link CapacityConstraintAdapter} instances. This ensures external
   * optimizers get the same physical limits that internal optimizers discover automatically.
   * </p>
   *
   * @param process the process system to scan for equipment constraints
   * @return this calculator for chaining
   */
  public ConstraintPenaltyCalculator addEquipmentCapacityConstraints(ProcessSystem process) {
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    List<ProcessEquipmentInterface> units = process.getUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);
      EquipmentCapacityStrategy strategy = registry.findStrategy(equipment);
      if (strategy != null) {
        Map<String, CapacityConstraint> equipConstraints = strategy.getConstraints(equipment);
        for (Map.Entry<String, CapacityConstraint> entry : equipConstraints.entrySet()) {
          CapacityConstraint cc = entry.getValue();
          if (cc.isEnabled()) {
            String qualifiedName = equipment.getName() + "/" + entry.getKey();
            constraints.add(new CapacityConstraintAdapter(qualifiedName, cc));
          }
        }
      }
    }
    return this;
  }

  /**
   * Returns all registered constraints.
   *
   * @return unmodifiable view of constraints
   */
  public List<ProcessConstraint> getConstraints() {
    return java.util.Collections.unmodifiableList(constraints);
  }

  /**
   * Returns the number of registered constraints.
   *
   * @return constraint count
   */
  public int getConstraintCount() {
    return constraints.size();
  }

  /**
   * Evaluates all constraints and returns a constraint margin vector.
   *
   * <p>
   * The returned array is suitable for NLP solvers that expect a {@code g(x) >= 0} constraint
   * vector. Each element corresponds to a constraint in registration order. Positive values mean
   * satisfied; negative means violated.
   * </p>
   *
   * @param process the process system (must have been run)
   * @return array of constraint margins (same order as {@link #getConstraints()})
   */
  public double[] evaluateMargins(ProcessSystem process) {
    double[] margins = new double[constraints.size()];
    for (int i = 0; i < constraints.size(); i++) {
      margins[i] = constraints.get(i).margin(process);
    }
    return margins;
  }

  /**
   * Checks if all hard constraints are satisfied.
   *
   * @param process the process system
   * @return true if no hard/critical constraint is violated
   */
  public boolean isFeasible(ProcessSystem process) {
    for (ProcessConstraint c : constraints) {
      if (c.isHard() && !c.isSatisfied(process)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Computes total penalty for all constraint violations.
   *
   * <p>
   * Returns 0 when all constraints are satisfied. Each violated constraint contributes its
   * individual penalty (typically quadratic in the margin).
   * </p>
   *
   * @param process the process system
   * @return total penalty (0 if fully feasible)
   */
  public double totalPenalty(ProcessSystem process) {
    double total = 0.0;
    for (ProcessConstraint c : constraints) {
      total += c.penalty(process);
    }
    return total;
  }

  /**
   * Applies the adaptive penalty formulation to a raw objective value.
   *
   * <p>
   * This is the same formulation used by {@link ProductionOptimizer}: the penalty is scaled by the
   * magnitude of the raw objective to be unit-independent. For a maximization problem, the
   * penalized objective is always less than any feasible value when constraints are violated.
   * </p>
   *
   * <p>
   * Penalty components:
   * </p>
   * <ul>
   * <li><strong>Hard/Critical violations:</strong> {@code -penaltyBase * (1 + |margin|)} per
   * constraint</li>
   * <li><strong>Soft violations:</strong> {@code -penaltyBase * weight * margin^2} per
   * constraint</li>
   * </ul>
   *
   * @param rawObjective the raw (unpenalized) objective value
   * @param process the process system (must have been run)
   * @return penalized objective (equals rawObjective if feasible, worse if infeasible)
   */
  public double penalize(double rawObjective, ProcessSystem process) {
    List<Double> hardList = new ArrayList<Double>();
    List<Double> softList = new ArrayList<Double>();
    List<Double> weightList = new ArrayList<Double>();

    for (ProcessConstraint c : constraints) {
      double m = c.margin(process);
      if (c.isHard()) {
        hardList.add(m);
      } else {
        softList.add(m);
        weightList.add(c.getPenaltyWeight());
      }
    }

    double[] hardMargins = new double[hardList.size()];
    for (int i = 0; i < hardMargins.length; i++) {
      hardMargins[i] = hardList.get(i);
    }
    double[] softMargins = new double[softList.size()];
    double[] softWeights = new double[weightList.size()];
    for (int i = 0; i < softMargins.length; i++) {
      softMargins[i] = softList.get(i);
      softWeights[i] = weightList.get(i);
    }

    return applyPenaltyFormula(rawObjective, hardMargins, softMargins, softWeights);
  }

  /**
   * Returns a detailed evaluation report for all constraints.
   *
   * @param process the process system
   * @return list of constraint evaluation snapshots
   */
  public List<ConstraintEvaluation> evaluate(ProcessSystem process) {
    List<ConstraintEvaluation> results = new ArrayList<ConstraintEvaluation>();
    for (ProcessConstraint c : constraints) {
      double m = c.margin(process);
      results.add(new ConstraintEvaluation(c.getName(), c.getSeverityLevel(), m, m >= 0.0,
          c.penalty(process), c.getDescription()));
    }
    return results;
  }

  /**
   * Clears all registered constraints.
   */
  public void clear() {
    constraints.clear();
  }

  // ============================================================================
  // Shared penalty formula
  // ============================================================================

  /**
   * Applies the shared adaptive penalty formula to a raw objective value given pre-evaluated
   * constraint margins and severity flags.
   *
   * <p>
   * This is the single source of truth for the penalty computation used by both
   * {@link ConstraintPenaltyCalculator#penalize(double, ProcessSystem)} and
   * {@link ProductionOptimizer}'s internal feasibility scoring. Extracting it here prevents the two
   * formulations from diverging.
   * </p>
   *
   * @param rawObjective the raw (unpenalized) objective value
   * @param hardMargins array of margins for hard/critical constraints (negative = violated)
   * @param softMargins array of margins for soft/advisory constraints (negative = violated)
   * @param softWeights array of penalty weights for each soft constraint (same length as
   *        softMargins)
   * @return penalized objective (equals rawObjective if all margins &gt;= 0)
   */
  public static double applyPenaltyFormula(double rawObjective, double[] hardMargins,
      double[] softMargins, double[] softWeights) {
    double penaltyBase = Math.max(Math.abs(rawObjective), 1.0);
    double penalty = 0.0;
    boolean anyViolation = false;

    for (int i = 0; i < hardMargins.length; i++) {
      if (hardMargins[i] < 0.0) {
        anyViolation = true;
        penalty -= penaltyBase * (1.0 + Math.abs(hardMargins[i]));
      }
    }
    for (int i = 0; i < softMargins.length; i++) {
      if (softMargins[i] < 0.0) {
        anyViolation = true;
        penalty -= penaltyBase * softWeights[i] * softMargins[i] * softMargins[i];
      }
    }
    if (!anyViolation) {
      return rawObjective;
    }
    return Math.min(-penaltyBase, rawObjective + penalty);
  }

  // ============================================================================
  // Result class
  // ============================================================================

  /**
   * Snapshot of a single constraint evaluation.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class ConstraintEvaluation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final ConstraintSeverityLevel severity;
    private final double margin;
    private final boolean satisfied;
    private final double penalty;
    private final String description;

    /**
     * Constructs a constraint evaluation snapshot.
     *
     * @param name constraint name
     * @param severity severity level
     * @param margin constraint margin
     * @param satisfied whether the constraint is satisfied
     * @param penalty computed penalty
     * @param description constraint description
     */
    public ConstraintEvaluation(String name, ConstraintSeverityLevel severity, double margin,
        boolean satisfied, double penalty, String description) {
      this.name = name;
      this.severity = severity;
      this.margin = margin;
      this.satisfied = satisfied;
      this.penalty = penalty;
      this.description = description;
    }

    /**
     * Gets the constraint name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the severity level.
     *
     * @return severity
     */
    public ConstraintSeverityLevel getSeverity() {
      return severity;
    }

    /**
     * Gets the constraint margin.
     *
     * @return margin (positive = satisfied)
     */
    public double getMargin() {
      return margin;
    }

    /**
     * Checks if the constraint is satisfied.
     *
     * @return true if satisfied
     */
    public boolean isSatisfied() {
      return satisfied;
    }

    /**
     * Gets the penalty value.
     *
     * @return penalty (0 if satisfied)
     */
    public double getPenalty() {
      return penalty;
    }

    /**
     * Gets the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return name + " [" + severity + "] margin=" + String.format("%.4f", margin)
          + (satisfied ? " OK" : " VIOLATED penalty=" + String.format("%.2f", penalty));
    }
  }
}
