package neqsim.process.equipment.capacity;

import java.util.function.DoubleSupplier;

/**
 * Represents a capacity constraint for process equipment.
 *
 * <p>
 * A capacity constraint defines a limit on equipment operation, such as maximum speed, flow rate,
 * or load factor. It tracks the current value, design value, and maximum allowable value, and
 * calculates utilization as a percentage of design capacity.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * CapacityConstraint speedConstraint = new CapacityConstraint("speed", "RPM", ConstraintType.HARD)
 *     .setDesignValue(10000.0).setMaxValue(11000.0).setWarningThreshold(0.9)
 *     .setValueSupplier(() -&gt; compressor.getSpeed());
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CapacityConstraint {

  /**
   * Enum defining the type of capacity constraint.
   */
  public enum ConstraintType {
    /**
     * Hard constraint that cannot be exceeded (e.g., max speed, surge limit). Exceeding this
     * constraint causes equipment failure or trip.
     */
    HARD,

    /**
     * Soft constraint that can be temporarily exceeded (e.g., design flow rate). Exceeding this
     * constraint may reduce equipment life or efficiency.
     */
    SOFT,

    /**
     * Design basis value for information only. Used for reporting and optimization guidance.
     */
    DESIGN
  }

  /** Name of the constraint (e.g., "speed", "gasLoadFactor"). */
  private final String name;

  /** Unit of measurement (e.g., "RPM", "m/s", "kW"). */
  private final String unit;

  /** Type of constraint (HARD, SOFT, or DESIGN). */
  private final ConstraintType type;

  /** Design/rated value for this constraint. */
  private double designValue = Double.MAX_VALUE;

  /** Absolute maximum value (for HARD constraints). */
  private double maxValue = Double.MAX_VALUE;

  /** Minimum required value (for constraints like residence time). */
  private double minValue = 0.0;

  /** Fraction of design value that triggers a warning (e.g., 0.9 = 90%). */
  private double warningThreshold = 0.9;

  /** Supplier function to get current value from equipment. */
  private DoubleSupplier valueSupplier;

  /** Cached current value (updated when getCurrentValue() is called). */
  private double currentValue = 0.0;

  /** Description of the constraint for documentation. */
  private String description = "";

  /**
   * Creates a new capacity constraint.
   *
   * @param name the name of the constraint
   * @param unit the unit of measurement
   * @param type the constraint type (HARD, SOFT, or DESIGN)
   */
  public CapacityConstraint(String name, String unit, ConstraintType type) {
    this.name = name;
    this.unit = unit;
    this.type = type;
  }

  /**
   * Sets the design/rated value for this constraint.
   *
   * @param designValue the design value
   * @return this constraint for method chaining
   */
  public CapacityConstraint setDesignValue(double designValue) {
    this.designValue = designValue;
    return this;
  }

  /**
   * Sets the maximum allowable value (for HARD constraints).
   *
   * @param maxValue the maximum value
   * @return this constraint for method chaining
   */
  public CapacityConstraint setMaxValue(double maxValue) {
    this.maxValue = maxValue;
    return this;
  }

  /**
   * Sets the minimum required value (for constraints like residence time).
   *
   * @param minValue the minimum value
   * @return this constraint for method chaining
   */
  public CapacityConstraint setMinValue(double minValue) {
    this.minValue = minValue;
    return this;
  }

  /**
   * Sets the warning threshold as a fraction of design value.
   *
   * @param warningThreshold fraction (0.0 to 1.0) at which to warn
   * @return this constraint for method chaining
   */
  public CapacityConstraint setWarningThreshold(double warningThreshold) {
    this.warningThreshold = warningThreshold;
    return this;
  }

  /**
   * Sets the supplier function to get the current value from equipment.
   *
   * @param supplier the value supplier function
   * @return this constraint for method chaining
   */
  public CapacityConstraint setValueSupplier(DoubleSupplier supplier) {
    this.valueSupplier = supplier;
    return this;
  }

  /**
   * Sets a description for this constraint.
   *
   * @param description the description text
   * @return this constraint for method chaining
   */
  public CapacityConstraint setDescription(String description) {
    this.description = description;
    return this;
  }

  /**
   * Gets the current value from the equipment.
   *
   * @return the current value, or 0.0 if no supplier is set
   */
  public double getCurrentValue() {
    if (valueSupplier != null) {
      currentValue = valueSupplier.getAsDouble();
    }
    return currentValue;
  }

  /**
   * Gets the utilization as a fraction of design value.
   *
   * <p>
   * For normal constraints (higher is worse), returns current/design. For minimum constraints
   * (lower is worse), returns design/current.
   * </p>
   *
   * @return utilization as fraction (1.0 = 100% of design)
   */
  public double getUtilization() {
    double current = getCurrentValue();
    if (minValue > 0 && designValue == Double.MAX_VALUE) {
      // This is a minimum constraint (e.g., residence time)
      if (current <= 0) {
        return Double.MAX_VALUE;
      }
      return minValue / current;
    }
    if (designValue <= 0 || designValue == Double.MAX_VALUE) {
      return 0.0;
    }
    return current / designValue;
  }

  /**
   * Gets the utilization as a percentage of design value.
   *
   * @return utilization as percentage (100.0 = 100% of design)
   */
  public double getUtilizationPercent() {
    return getUtilization() * 100.0;
  }

  /**
   * Checks if this constraint is violated (exceeds design capacity).
   *
   * @return true if utilization exceeds 100%
   */
  public boolean isViolated() {
    return getUtilization() > 1.0;
  }

  /**
   * Checks if this constraint exceeds the absolute maximum (for HARD constraints).
   *
   * @return true if current value exceeds max value
   */
  public boolean isHardLimitExceeded() {
    if (type != ConstraintType.HARD || maxValue == Double.MAX_VALUE) {
      return false;
    }
    return getCurrentValue() > maxValue;
  }

  /**
   * Checks if this constraint is near its limit (above warning threshold).
   *
   * @return true if utilization exceeds warning threshold
   */
  public boolean isNearLimit() {
    return getUtilization() > warningThreshold;
  }

  /**
   * Gets the margin to design capacity.
   *
   * @return remaining capacity as fraction (0.2 = 20% margin remaining)
   */
  public double getMargin() {
    return 1.0 - getUtilization();
  }

  /**
   * Gets the margin to design capacity as a percentage.
   *
   * @return remaining capacity as percentage
   */
  public double getMarginPercent() {
    return getMargin() * 100.0;
  }

  // Getters

  /**
   * Gets the constraint name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the unit of measurement.
   *
   * @return the unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets the constraint type.
   *
   * @return the type
   */
  public ConstraintType getType() {
    return type;
  }

  /**
   * Gets the design value.
   *
   * @return the design value
   */
  public double getDesignValue() {
    return designValue;
  }

  /**
   * Gets the maximum allowable value.
   *
   * @return the max value
   */
  public double getMaxValue() {
    return maxValue;
  }

  /**
   * Gets the minimum required value.
   *
   * @return the min value
   */
  public double getMinValue() {
    return minValue;
  }

  /**
   * Gets the warning threshold.
   *
   * @return the warning threshold as fraction
   */
  public double getWarningThreshold() {
    return warningThreshold;
  }

  /**
   * Gets the description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(": ");
    sb.append(String.format("%.2f", getCurrentValue())).append(" ").append(unit);
    sb.append(String.format(" (%.1f%% of design %.2f)", getUtilizationPercent(), designValue));
    if (isViolated()) {
      sb.append(" [EXCEEDED]");
    } else if (isNearLimit()) {
      sb.append(" [NEAR LIMIT]");
    }
    return sb.toString();
  }
}
