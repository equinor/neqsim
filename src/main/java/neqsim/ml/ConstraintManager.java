package neqsim.ml;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unified constraint management for process equipment.
 *
 * <p>
 * Provides centralized constraint handling for:
 * <ul>
 * <li>Safe RL exploration (action space projection)</li>
 * <li>Multi-agent coordination (global constraint satisfaction)</li>
 * <li>Explainable control (constraint violation explanations)</li>
 * <li>Safety system integration (HIPPS, ESD triggers)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ConstraintManager implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<String, Constraint> constraints;
  private final List<ConstraintViolationListener> listeners;

  /**
   * Listener interface for constraint violation events.
   */
  public interface ConstraintViolationListener {
    /**
     * Called when a constraint is violated.
     *
     * @param constraint the violated constraint
     */
    void onViolation(Constraint constraint);
  }

  /**
   * Create an empty constraint manager.
   */
  public ConstraintManager() {
    this.constraints = new LinkedHashMap<>();
    this.listeners = new ArrayList<>();
  }

  /**
   * Add a constraint.
   *
   * @param constraint constraint to add
   * @return this ConstraintManager for chaining
   */
  public ConstraintManager add(Constraint constraint) {
    constraints.put(constraint.getName(), constraint);
    return this;
  }

  /**
   * Add a hard upper-bound constraint.
   *
   * @param name constraint name
   * @param variableName variable to constrain
   * @param maxValue maximum allowed value
   * @param unit physical unit
   * @return this ConstraintManager for chaining
   */
  public ConstraintManager addHardUpperBound(String name, String variableName, double maxValue,
      String unit) {
    return add(Constraint.upperBound(name, variableName, maxValue, unit, Constraint.Type.HARD));
  }

  /**
   * Add a hard lower-bound constraint.
   *
   * @param name constraint name
   * @param variableName variable to constrain
   * @param minValue minimum allowed value
   * @param unit physical unit
   * @return this ConstraintManager for chaining
   */
  public ConstraintManager addHardLowerBound(String name, String variableName, double minValue,
      String unit) {
    return add(Constraint.lowerBound(name, variableName, minValue, unit, Constraint.Type.HARD));
  }

  /**
   * Add a hard range constraint.
   *
   * @param name constraint name
   * @param variableName variable to constrain
   * @param minValue minimum allowed value
   * @param maxValue maximum allowed value
   * @param unit physical unit
   * @return this ConstraintManager for chaining
   */
  public ConstraintManager addHardRange(String name, String variableName, double minValue,
      double maxValue, String unit) {
    return add(
        Constraint.range(name, variableName, minValue, maxValue, unit, Constraint.Type.HARD));
  }

  /**
   * Add a soft range constraint.
   *
   * @param name constraint name
   * @param variableName variable to constrain
   * @param minValue minimum preferred value
   * @param maxValue maximum preferred value
   * @param unit physical unit
   * @return this ConstraintManager for chaining
   */
  public ConstraintManager addSoftRange(String name, String variableName, double minValue,
      double maxValue, String unit) {
    return add(
        Constraint.range(name, variableName, minValue, maxValue, unit, Constraint.Type.SOFT));
  }

  /**
   * Register a violation listener.
   *
   * @param listener listener to notify on violations
   */
  public void addViolationListener(ConstraintViolationListener listener) {
    listeners.add(listener);
  }

  /**
   * Evaluate all constraints against a state vector.
   *
   * @param state current state
   * @return list of violated constraints
   */
  public List<Constraint> evaluate(StateVector state) {
    List<Constraint> violations = new ArrayList<>();

    for (Constraint c : constraints.values()) {
      double value = state.getValue(c.getVariableName());
      c.evaluate(value);

      if (c.isViolated()) {
        violations.add(c);
        for (ConstraintViolationListener listener : listeners) {
          listener.onViolation(c);
        }
      }
    }

    return violations;
  }

  /**
   * Check if any hard constraints are violated.
   *
   * @return true if any hard constraint is violated
   */
  public boolean hasHardViolation() {
    return constraints.values().stream().anyMatch(c -> c.isHard() && c.isViolated());
  }

  /**
   * Get total violation penalty (for RL reward shaping).
   *
   * @return sum of normalized violations
   */
  public double getTotalViolationPenalty() {
    double penalty = 0.0;
    for (Constraint c : constraints.values()) {
      double violation = c.getNormalizedViolation();
      if (c.isHard()) {
        penalty += 10.0 * violation; // Hard constraints penalized more
      } else {
        penalty += violation;
      }
    }
    return penalty;
  }

  /**
   * Get minimum margin to any hard constraint.
   *
   * @return smallest margin (negative if violated)
   */
  public double getMinHardMargin() {
    return constraints.values().stream().filter(Constraint::isHard)
        .mapToDouble(Constraint::getMargin).min().orElse(Double.POSITIVE_INFINITY);
  }

  /**
   * Get all violated constraints.
   *
   * @return list of currently violated constraints
   */
  public List<Constraint> getViolations() {
    return constraints.values().stream().filter(Constraint::isViolated)
        .collect(Collectors.toList());
  }

  /**
   * Get violations by category.
   *
   * @param category constraint category to filter
   * @return list of violated constraints in category
   */
  public List<Constraint> getViolationsByCategory(Constraint.Category category) {
    return constraints.values().stream().filter(c -> c.isViolated() && c.getCategory() == category)
        .collect(Collectors.toList());
  }

  /**
   * Generate human-readable explanation of current violations.
   *
   * @return explanation string
   */
  public String explainViolations() {
    List<Constraint> violations = getViolations();
    if (violations.isEmpty()) {
      return "All constraints satisfied.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Constraint violations:\n");

    for (Constraint c : violations) {
      sb.append("  - ").append(c.getName()).append(": ");
      sb.append(c.getVariableName()).append(" = ");
      sb.append(String.format("%.2f %s", c.getCurrentValue(), c.getUnit()));

      if (c.getCurrentValue() < c.getLowerBound()) {
        sb.append(String.format(" < min %.2f", c.getLowerBound()));
      } else {
        sb.append(String.format(" > max %.2f", c.getUpperBound()));
      }

      sb.append(" [").append(c.getType()).append("]\n");
    }

    return sb.toString();
  }

  /**
   * Get constraint by name.
   *
   * @param name constraint name
   * @return constraint or null if not found
   */
  public Constraint get(String name) {
    return constraints.get(name);
  }

  /**
   * Get all constraints.
   *
   * @return list of all constraints
   */
  public List<Constraint> getAll() {
    return new ArrayList<>(constraints.values());
  }

  /**
   * Get number of constraints.
   *
   * @return constraint count
   */
  public int size() {
    return constraints.size();
  }

  /**
   * Clear all constraints.
   */
  public void clear() {
    constraints.clear();
  }

  @Override
  public String toString() {
    int total = constraints.size();
    long violated = constraints.values().stream().filter(Constraint::isViolated).count();
    return String.format("ConstraintManager[%d constraints, %d violated]", total, violated);
  }
}
