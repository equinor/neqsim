package neqsim.process.equipment.capacity;

import java.io.Serializable;
import java.util.function.DoubleSupplier;

/**
 * Represents a capacity constraint for process equipment.
 *
 * <p>
 * A capacity constraint defines a limit on equipment operation, such as maximum speed, flow rate, or load factor. It
 * tracks the current value, design value, and maximum allowable value, and calculates utilization as a percentage of
 * design capacity.
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
public class CapacityConstraint implements Serializable {
  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /**
   * Maximum utilization value returned by {@link #getUtilization()}. Caps extreme values (e.g., when design value is
   * near zero or current value is far beyond design) to prevent unbounded utilization percentages that confuse
   * optimization and reporting.
   */
  private static final double MAX_UTILIZATION = 9.99;

  /**
   * Enum defining the type of capacity constraint.
   */
  public enum ConstraintType {
    /**
     * Hard constraint that cannot be exceeded (e.g., max speed, surge limit). Exceeding this constraint causes
     * equipment failure or trip.
     */
    HARD,

    /**
     * Soft constraint that can be temporarily exceeded (e.g., design flow rate). Exceeding this constraint may reduce
     * equipment life or efficiency.
     */
    SOFT,

    /**
     * Design basis value for information only. Used for reporting and optimization guidance.
     */
    DESIGN
  }

  /**
   * Enum defining the severity level of constraint violations.
   *
   * <p>
   * Used by the optimizer to determine how to handle constraint violations:
   * <ul>
   * <li>CRITICAL: Equipment damage or safety hazard - optimization must stop</li>
   * <li>HARD: Exceeds design limits - marks solution as infeasible</li>
   * <li>SOFT: Exceeds recommended limits - applies penalty to objective</li>
   * <li>ADVISORY: Information only - no impact on optimization</li>
   * </ul>
   */
  public enum ConstraintSeverity {
    /**
     * Critical violation - exceeding causes equipment damage or safety hazard. Examples: surge, overspeed,
     * over-temperature. Optimizer must stop immediately when this is violated.
     */
    CRITICAL,

    /**
     * Hard violation - exceeding means solution is infeasible. Examples: design capacity, maximum power. Optimizer
     * marks solution as infeasible.
     */
    HARD,

    /**
     * Soft violation - exceeding is undesirable but acceptable. Examples: efficiency targets, recommended operating
     * range. Optimizer applies penalty to objective function.
     */
    SOFT,

    /**
     * Advisory only - information for reporting. Examples: turndown ratio, design point deviation. No impact on
     * optimization.
     */
    ADVISORY
  }

  /** Name of the constraint (e.g., "speed", "gasLoadFactor"). */
  private final String name;

  /** Unit of measurement (e.g., "RPM", "m/s", "kW"). */
  private final String unit;

  /** Override unit for mutable unit changes. */
  private String unitOverride = null;

  /** Type of constraint (HARD, SOFT, or DESIGN). */
  private final ConstraintType type;

  /** Severity level for optimization (CRITICAL, HARD, SOFT, ADVISORY). */
  private ConstraintSeverity severity = ConstraintSeverity.HARD;

  /** Design/rated value for this constraint. */
  private double designValue = Double.MAX_VALUE;

  /** Absolute maximum value (for HARD constraints). */
  private double maxValue = Double.MAX_VALUE;

  /** Minimum required value (for constraints like residence time). */
  private double minValue = 0.0;

  /** Fraction of design value that triggers a warning (e.g., 0.9 = 90%). */
  private double warningThreshold = 0.9;

  /**
   * Supplier function to get current value from equipment. Marked transient because lambdas/method references are not
   * serializable. After deserialization, this will be null and the cached currentValue will be used instead.
   */
  private transient DoubleSupplier valueSupplier;

  /** Cached current value (updated when getCurrentValue() is called). */
  private double currentValue = 0.0;

  /** Description of the constraint for documentation. */
  private String description = "";

  /** Whether this constraint is enabled for capacity analysis. */
  private boolean enabled = true;

  /**
   * Describes the source of the design value used in this constraint.
   *
   * <p>
   * Typical values:
   * </p>
   * <ul>
   * <li>"equipment" — set directly on the equipment object via API</li>
   * <li>"designCapacities" — supplied via JSON designCapacities input</li>
   * <li>"mechanicalDesign" — derived from mechanical design calculations</li>
   * <li>"default" — a strategy-level default (not from actual equipment data)</li>
   * <li>"not_set" — no design value has been provided</li>
   * </ul>
   */
  private String dataSource = "not_set";

  /** Whether a confidence value has been explicitly assigned. */
  private boolean confidenceSet = false;

  /**
   * Confidence in the engineering basis of this constraint, on a scale from zero to one.
   *
   * <p>
   * This is evidence-quality metadata, not a probability that the equipment is safe or that the constraint will be
   * satisfied. Use {@link #hasConfidence()} to distinguish an explicitly assigned value from legacy or unset data.
   * </p>
   */
  private double confidence = Double.NaN;

  /** Whether a validity range has been explicitly assigned. */
  private boolean validityRangeSet = false;

  /** Lower bound of the validity range, in the constraint unit. */
  private double validityMinimum = Double.NaN;

  /** Upper bound of the validity range, in the constraint unit. */
  private double validityMaximum = Double.NaN;

  /**
   * Marginal economic value (shadow price) of relaxing this constraint.
   *
   * <p>
   * The shadow price is the incremental objective improvement obtained per unit of additional capacity when this
   * constraint is binding — for example currency-per-day gained per extra unit of design rate, or per percentage point
   * of utilisation headroom unlocked. It is zero by default and is populated by a debottlenecking study (see
   * {@code neqsim.process.optimization.valuechain.DebottleneckingAdvisor}). A non-binding constraint has a shadow price
   * of zero.
   * </p>
   */
  private double shadowPrice = 0.0;

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
   * Creates a new capacity constraint with default type SOFT and empty unit.
   *
   * <p>
   * This constructor is a convenience for building constraints where the unit and type can be set later using the
   * fluent API.
   * </p>
   *
   * @param name the name of the constraint
   */
  public CapacityConstraint(String name) {
    this.name = name;
    this.unit = "";
    this.type = ConstraintType.SOFT;
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
   * Sets the severity level for this constraint.
   *
   * <p>
   * Severity affects how the optimizer handles violations:
   * <ul>
   * <li>CRITICAL: Optimizer must stop immediately</li>
   * <li>HARD: Solution marked as infeasible</li>
   * <li>SOFT: Penalty applied to objective</li>
   * <li>ADVISORY: Information only</li>
   * </ul>
   *
   * @param severity the severity level
   * @return this constraint for method chaining
   */
  public CapacityConstraint setSeverity(ConstraintSeverity severity) {
    this.severity = severity;
    return this;
  }

  /**
   * Gets the severity level for this constraint.
   *
   * @return the severity level
   */
  public ConstraintSeverity getSeverity() {
    return severity;
  }

  /**
   * Checks if this is a critical violation that requires immediate action.
   *
   * <p>
   * Critical violations indicate equipment damage or safety hazard. The optimizer should stop immediately when this
   * returns true.
   * </p>
   *
   * @return true if constraint is CRITICAL severity and violated
   */
  public boolean isCriticalViolation() {
    return severity == ConstraintSeverity.CRITICAL && isViolated();
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
   * Sets the current value directly. Use this when you want to set the value manually rather than using a supplier
   * function.
   *
   * <p>
   * Note: If a value supplier is set, it will override this value when getCurrentValue() is called.
   * </p>
   *
   * @param value the current value to set
   * @return this constraint for method chaining
   */
  public CapacityConstraint setCurrentValue(double value) {
    this.currentValue = value;
    return this;
  }

  /**
   * Sets the unit of measurement for this constraint.
   *
   * <p>
   * This is a convenience method for cases where the unit needs to be changed after construction.
   * </p>
   *
   * @param unit the unit of measurement
   * @return this constraint for method chaining
   */
  public CapacityConstraint setUnit(String unit) {
    // The constructor-assigned unit field is final; store the override in a mutable field
    this.unitOverride = unit;
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
   * For normal constraints (higher is worse), returns current/design. For minimum constraints (lower is worse), returns
   * design/current.
   * </p>
   *
   * @return utilization as fraction (1.0 = 100% of design)
   */
  public double getUtilization() {
    return getUtilization(getCurrentValue());
  }

  /**
   * Gets utilization for an explicitly snapshotted current value without invoking the configured value supplier.
   *
   * <p>
   * This overload is useful when a caller must report the same scalar value used to calculate utilization.
   * </p>
   *
   * @param currentValue snapshotted current constraint value
   * @return utilization as fraction (1.0 = 100% of design)
   */
  public double getUtilization(double currentValue) {
    if (minValue > 0 && designValue == Double.MAX_VALUE) {
      // This is a minimum constraint (e.g., residence time)
      if (currentValue <= 0) {
        return MAX_UTILIZATION;
      }
      return Math.min(minValue / currentValue, MAX_UTILIZATION);
    }
    if (designValue <= 0 || designValue == Double.MAX_VALUE) {
      return 0.0;
    }
    return Math.min(currentValue / designValue, MAX_UTILIZATION);
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
   * Checks whether this HARD constraint exceeds its absolute limit.
   *
   * @return true if a maximum constraint is above its maximum or a minimum constraint is below its minimum
   */
  public boolean isHardLimitExceeded() {
    if (type != ConstraintType.HARD) {
      return false;
    }
    if (isMinimumConstraint()) {
      return getCurrentValue() < minValue;
    }
    if (maxValue == Double.MAX_VALUE) {
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
   * @return the unit (or unitOverride if set)
   */
  public String getUnit() {
    return unitOverride != null ? unitOverride : unit;
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
   * Gets the display design value for reporting purposes. For minimum constraints (where designValue is MAX_VALUE),
   * this returns the minValue instead.
   *
   * @return the design value for display purposes
   */
  public double getDisplayDesignValue() {
    if (minValue > 0 && designValue == Double.MAX_VALUE) {
      return minValue;
    }
    return designValue;
  }

  /**
   * Checks if this is a minimum constraint (where being above the minimum is good).
   *
   * @return true if this is a minimum constraint
   */
  public boolean isMinimumConstraint() {
    return minValue > 0 && designValue == Double.MAX_VALUE;
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

  /**
   * Checks if this constraint is enabled for capacity analysis.
   *
   * <p>
   * Disabled constraints are excluded from bottleneck detection and optimization. They still track values but don't
   * contribute to utilization summaries.
   * </p>
   *
   * @return true if the constraint is enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Enables or disables this constraint for capacity analysis.
   *
   * <p>
   * When disabled, this constraint is excluded from:
   * <ul>
   * <li>Bottleneck detection</li>
   * <li>Capacity utilization summaries</li>
   * <li>Optimization constraints</li>
   * <li>Near-limit warnings</li>
   * </ul>
   * <p>
   * The constraint still tracks its current value and can be queried directly.
   * </p>
   *
   * @param enabled true to enable, false to disable
   * @return this constraint for method chaining
   */
  public CapacityConstraint setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Gets the data source that provided the design value for this constraint.
   *
   * <p>
   * The data source indicates where the design/limit value came from, helping operators and agents understand the basis
   * of utilization calculations. Common values: "equipment", "designCapacities", "mechanicalDesign", "default",
   * "not_set".
   * </p>
   *
   * @return the data source string
   */
  public String getDataSource() {
    return dataSource;
  }

  /**
   * Sets the data source that provided the design value for this constraint.
   *
   * @param dataSource the data source string (e.g., "equipment", "designCapacities", "default")
   * @return this constraint for method chaining
   */
  public CapacityConstraint setDataSource(String dataSource) {
    this.dataSource = dataSource != null ? dataSource : "not_set";
    return this;
  }

  /**
   * Sets confidence in the engineering basis of this constraint.
   *
   * <p>
   * Confidence is evidence-quality metadata on a scale from zero to one. It is not a probability of safe operation,
   * constraint satisfaction, or model accuracy, and it does not change utilization or feasibility calculations.
   * </p>
   *
   * @param confidence confidence score from 0.0 to 1.0, inclusive
   * @return this constraint for method chaining
   * @throws IllegalArgumentException if confidence is non-finite or outside [0, 1]
   */
  public CapacityConstraint setConfidence(double confidence) {
    if (Double.isNaN(confidence) || Double.isInfinite(confidence) || confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException("confidence must be finite and in the range [0, 1]");
    }
    this.confidence = confidence;
    this.confidenceSet = true;
    return this;
  }

  /**
   * Checks whether confidence has been explicitly assigned.
   *
   * @return true when {@link #setConfidence(double)} has been called with a valid value
   */
  public boolean hasConfidence() {
    return confidenceSet;
  }

  /**
   * Gets confidence in the engineering basis of this constraint.
   *
   * @return confidence from 0.0 to 1.0, or {@link Double#NaN} when unset
   */
  public double getConfidence() {
    return confidenceSet ? confidence : Double.NaN;
  }

  /**
   * Clears explicitly assigned confidence metadata.
   *
   * @return this constraint for method chaining
   */
  public CapacityConstraint clearConfidence() {
    confidence = Double.NaN;
    confidenceSet = false;
    return this;
  }

  /**
   * Sets the scalar operating range for which this constraint basis is considered applicable.
   *
   * <p>
   * Both bounds use {@link #getUnit()}, the same unit as the constraint's current value. This metadata does not alter
   * utilization or feasibility; callers should inspect {@link #isCurrentValueWithinValidityRange()} before relying on
   * the limit outside its evidenced range.
   * </p>
   *
   * @param minimum lower inclusive validity bound in the constraint unit
   * @param maximum upper inclusive validity bound in the constraint unit
   * @return this constraint for method chaining
   * @throws IllegalArgumentException if either bound is non-finite or minimum exceeds maximum
   */
  public CapacityConstraint setValidityRange(double minimum, double maximum) {
    if (Double.isNaN(minimum) || Double.isInfinite(minimum) || Double.isNaN(maximum) || Double.isInfinite(maximum)) {
      throw new IllegalArgumentException("validity range bounds must be finite");
    }
    if (minimum > maximum) {
      throw new IllegalArgumentException("validity range minimum must not exceed maximum");
    }
    validityMinimum = minimum;
    validityMaximum = maximum;
    validityRangeSet = true;
    return this;
  }

  /**
   * Checks whether a validity range has been explicitly assigned.
   *
   * @return true when a valid range has been assigned
   */
  public boolean hasValidityRange() {
    return validityRangeSet;
  }

  /**
   * Gets the lower inclusive validity bound.
   *
   * @return lower bound in {@link #getUnit()}, or {@link Double#NaN} when unset
   */
  public double getValidityMinimum() {
    return validityRangeSet ? validityMinimum : Double.NaN;
  }

  /**
   * Gets the upper inclusive validity bound.
   *
   * @return upper bound in {@link #getUnit()}, or {@link Double#NaN} when unset
   */
  public double getValidityMaximum() {
    return validityRangeSet ? validityMaximum : Double.NaN;
  }

  /**
   * Checks whether the current constraint value lies inside the assigned validity range.
   *
   * @return true when a range is assigned and the current value lies within both inclusive bounds; false when no range
   * is assigned
   */
  public boolean isCurrentValueWithinValidityRange() {
    if (!validityRangeSet) {
      return false;
    }
    double value = getCurrentValue();
    return value >= validityMinimum && value <= validityMaximum;
  }

  /**
   * Clears the assigned validity range.
   *
   * @return this constraint for method chaining
   */
  public CapacityConstraint clearValidityRange() {
    validityMinimum = Double.NaN;
    validityMaximum = Double.NaN;
    validityRangeSet = false;
    return this;
  }

  /**
   * Gets the marginal economic value (shadow price) of relaxing this constraint.
   *
   * <p>
   * Returns the incremental objective improvement obtainable per unit of additional capacity while this constraint is
   * binding. The value is zero unless it has been populated by a debottlenecking study. See
   * {@code neqsim.process.optimization.valuechain.DebottleneckingAdvisor} for the companion analysis that computes and
   * sets this value.
   * </p>
   *
   * @return the shadow price (objective units per unit of relaxed capacity); zero if not set
   */
  public double getShadowPrice() {
    return shadowPrice;
  }

  /**
   * Sets the marginal economic value (shadow price) of relaxing this constraint.
   *
   * @param shadowPrice the shadow price in objective units per unit of relaxed capacity
   * @return this constraint for method chaining
   */
  public CapacityConstraint setShadowPrice(double shadowPrice) {
    this.shadowPrice = shadowPrice;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(": ");
    sb.append(String.format("%.2f", getCurrentValue())).append(" ").append(unit);
    // For min constraints (designValue=MAX_VALUE), show minValue as the constraint value
    if (minValue > 0 && designValue == Double.MAX_VALUE) {
      sb.append(String.format(" (%.1f%% of min %.2f)", getUtilizationPercent(), minValue));
    } else {
      sb.append(String.format(" (%.1f%% of design %.2f)", getUtilizationPercent(), designValue));
    }
    if (isViolated()) {
      sb.append(" [EXCEEDED]");
    } else if (isNearLimit()) {
      sb.append(" [NEAR LIMIT]");
    }
    return sb.toString();
  }
}
