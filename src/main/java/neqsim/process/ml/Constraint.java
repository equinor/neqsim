package neqsim.process.ml;

import java.io.Serializable;

/**
 * Represents a physical or operational constraint for process equipment.
 *
 * <p>
 * Constraints are used for:
 * <ul>
 * <li>Safe RL exploration (projecting actions to feasible space)</li>
 * <li>Multi-agent coordination (respecting global limits)</li>
 * <li>Explainable control (understanding why actions are limited)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Constraint implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Constraint type enumeration.
   */
  public enum Type {
    /** Hard constraint - must never be violated (safety). */
    HARD,
    /** Soft constraint - can be violated with penalty. */
    SOFT,
    /** Informational - for monitoring only. */
    INFO
  }

  /**
   * Constraint category for grouping.
   */
  public enum Category {
    /** Physical limits (pressure, temperature, flow). */
    PHYSICAL,
    /** Safety system limits (HIPPS, ESD, PSV). */
    SAFETY,
    /** Equipment capacity limits. */
    EQUIPMENT,
    /** Operational limits (ramp rates, stability). */
    OPERATIONAL,
    /** Economic/environmental limits. */
    ECONOMIC
  }

  private final String name;
  private final String description;
  private final Type type;
  private final Category category;
  private final String variableName;
  private final double lowerBound;
  private final double upperBound;
  private final String unit;
  private double currentValue;
  private boolean violated;

  /**
   * Create a constraint.
   *
   * @param name short identifier
   * @param description human-readable description
   * @param type constraint type (HARD, SOFT, INFO)
   * @param category constraint category
   * @param variableName state variable this constrains
   * @param lowerBound lower limit
   * @param upperBound upper limit
   * @param unit physical unit
   */
  public Constraint(String name, String description, Type type, Category category,
      String variableName, double lowerBound, double upperBound, String unit) {
    this.name = name;
    this.description = description;
    this.type = type;
    this.category = category;
    this.variableName = variableName;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.unit = unit;
    this.currentValue = Double.NaN;
    this.violated = false;
  }

  /**
   * Create a simple upper-bound constraint.
   *
   * @param name constraint name
   * @param variableName variable to constrain
   * @param maxValue maximum allowed value
   * @param unit physical unit
   * @param type constraint type
   * @return new Constraint
   */
  public static Constraint upperBound(String name, String variableName, double maxValue,
      String unit, Type type) {
    return new Constraint(name, name + " upper limit", type, Category.PHYSICAL, variableName,
        Double.NEGATIVE_INFINITY, maxValue, unit);
  }

  /**
   * Create a simple lower-bound constraint.
   *
   * @param name constraint name
   * @param variableName variable to constrain
   * @param minValue minimum allowed value
   * @param unit physical unit
   * @param type constraint type
   * @return new Constraint
   */
  public static Constraint lowerBound(String name, String variableName, double minValue,
      String unit, Type type) {
    return new Constraint(name, name + " lower limit", type, Category.PHYSICAL, variableName,
        minValue, Double.POSITIVE_INFINITY, unit);
  }

  /**
   * Create a range constraint.
   *
   * @param name constraint name
   * @param variableName variable to constrain
   * @param minValue minimum allowed value
   * @param maxValue maximum allowed value
   * @param unit physical unit
   * @param type constraint type
   * @return new Constraint
   */
  public static Constraint range(String name, String variableName, double minValue, double maxValue,
      String unit, Type type) {
    return new Constraint(name, name + " range limit", type, Category.PHYSICAL, variableName,
        minValue, maxValue, unit);
  }

  /**
   * Evaluate constraint with current value.
   *
   * @param value current value of the constrained variable
   * @return this Constraint (updated)
   */
  public Constraint evaluate(double value) {
    this.currentValue = value;
    this.violated = value < lowerBound || value > upperBound;
    return this;
  }

  /**
   * Get constraint violation amount (0 if satisfied).
   *
   * @return violation magnitude (positive = violated)
   */
  public double getViolation() {
    if (Double.isNaN(currentValue)) {
      return 0.0;
    }
    if (currentValue < lowerBound) {
      return lowerBound - currentValue;
    }
    if (currentValue > upperBound) {
      return currentValue - upperBound;
    }
    return 0.0;
  }

  /**
   * Get normalized violation in [0, 1] range.
   *
   * @return normalized violation (0 = satisfied, 1 = severely violated)
   */
  public double getNormalizedViolation() {
    double violation = getViolation();
    if (violation <= 0) {
      return 0.0;
    }
    double range = upperBound - lowerBound;
    if (Double.isInfinite(range) || range <= 0) {
      return violation > 0 ? 1.0 : 0.0;
    }
    return Math.min(1.0, violation / range);
  }

  /**
   * Get margin to nearest bound (negative if violated).
   *
   * @return distance to nearest constraint boundary
   */
  public double getMargin() {
    if (Double.isNaN(currentValue)) {
      return Double.POSITIVE_INFINITY;
    }
    double marginLower =
        Double.isInfinite(lowerBound) ? Double.POSITIVE_INFINITY : currentValue - lowerBound;
    double marginUpper =
        Double.isInfinite(upperBound) ? Double.POSITIVE_INFINITY : upperBound - currentValue;
    return Math.min(marginLower, marginUpper);
  }

  /**
   * Project a value to the feasible range.
   *
   * @param value value to project
   * @return value clamped to [lowerBound, upperBound]
   */
  public double project(double value) {
    if (value < lowerBound) {
      return lowerBound;
    }
    if (value > upperBound) {
      return upperBound;
    }
    return value;
  }

  // Getters

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Type getType() {
    return type;
  }

  public Category getCategory() {
    return category;
  }

  public String getVariableName() {
    return variableName;
  }

  public double getLowerBound() {
    return lowerBound;
  }

  public double getUpperBound() {
    return upperBound;
  }

  public String getUnit() {
    return unit;
  }

  public double getCurrentValue() {
    return currentValue;
  }

  public boolean isViolated() {
    return violated;
  }

  public boolean isHard() {
    return type == Type.HARD;
  }

  @Override
  public String toString() {
    String status = violated ? "VIOLATED" : "OK";
    return String.format("Constraint[%s: %s in [%.2f, %.2f] %s = %.2f (%s)]", name, variableName,
        lowerBound, upperBound, unit, currentValue, status);
  }
}
