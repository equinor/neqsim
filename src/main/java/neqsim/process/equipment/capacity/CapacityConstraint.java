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

  /**
   * Enum describing the provenance of a constraint limit - the kind of authority that backs the number.
   *
   * <p>
   * This is a separate axis from {@link ConstraintSeverity}: severity says <em>how a violation is handled</em>, whereas
   * the source says <em>why the limit exists and who stands behind it</em>. A vendor surge limit and a company standard
   * may both be HARD, but they are not renegotiable on the same terms.
   * </p>
   *
   * <p>
   * It is also distinct from {@link CapacityConstraint#getDataSource()}. The data source is a free-text label
   * identifying which part of the model supplied the number ("equipment", "mechanicalDesign",
   * "installed_capacity_table"); the constraint source classifies the underlying authority. A limit with
   * {@code dataSource = "mechanicalDesign"} may have a source of {@link #CONFORMITY_STANDARD} or
   * {@link #VENDOR_DATASHEET} depending on how the design was set.
   * </p>
   *
   * <p>
   * Recommended pairings with severity:
   * </p>
   * <ul>
   * <li>{@link #CONFORMITY_STANDARD} - typically SOFT or ADVISORY, unless company policy makes it binding</li>
   * <li>{@link #USER_RULE} - severity chosen by operations</li>
   * <li>{@link #PROCESS_EMPIRICAL} - typically HARD, calibrated against observed plant behaviour</li>
   * <li>{@link #VENDOR_DATASHEET} - typically HARD or CRITICAL for mechanical and surge limits</li>
   * <li>{@link #AUTO_SIZE} - design point produced by sizing code; usually SOFT</li>
   * <li>{@link #DEFAULT} - built-in constraint with no declared provenance</li>
   * </ul>
   */
  public enum ConstraintSource {
    /**
     * Limit comes from an industry or company standard (API 12J, NORSOK P-002, ISO, ASME, DNV, or a company technical
     * requirement). The specific document is recorded in {@link CapacityConstraint#getSourceReference()}.
     */
    CONFORMITY_STANDARD,

    /**
     * Limit defined by the user, operations, or the asset team. Examples: a maximum compressor speed imposed for
     * vibration management, or a separator load factor derived from operating experience.
     */
    USER_RULE,

    /**
     * Limit derived from empirical observation of plant behaviour. Typically a correlation between an operating
     * variable (rate, pressure) and a downstream consequence (carry-over, fouling, scaling) fitted to historian data.
     * See {@link EmpiricalCarryOverConstraint}.
     */
    PROCESS_EMPIRICAL,

    /**
     * Limit taken from a vendor or OEM datasheet - compressor surge curve, valve trim Cv, mechanical overspeed, sealing
     * pressure.
     */
    VENDOR_DATASHEET,

    /**
     * Limit generated automatically by equipment sizing code, such as an {@code autoSize()} call.
     */
    AUTO_SIZE,

    /**
     * Default fallback, used by built-in constraints where no provenance has been declared.
     */
    DEFAULT
  }

  /**
   * Role of the physical limit currently used for utilization and feasibility.
   *
   * <p>
   * This is deliberately separate from {@link ConstraintSource}. The source records who or what supports a number,
   * while the role records whether the active number is a qualified design/rated value, a conservative screening
   * default, or a facility-specific operating override.
   * </p>
   */
  public enum ApplicableLimitRole {
    /** A built-in or otherwise unqualified conservative screening limit. */
    DEFAULT_SCREENING,
    /** The declared design, rated, datasheet, standard, empirical, or auto-sized limit. */
    DESIGN_RATED,
    /** A separately configured operating limit that leaves the design/default basis intact. */
    CONFIGURED_OPERATING
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

  /** Provenance of the limit - the kind of authority that backs it. */
  private ConstraintSource source = ConstraintSource.DEFAULT;

  /**
   * Free-text reference identifying the source: the standard number ("API 12J"), the team that imposed the rule, or the
   * historian tag set used to fit an empirical correlation. Optional.
   */
  private String sourceReference = "";

  /** Design/rated value for this constraint. */
  private double designValue = Double.MAX_VALUE;

  /** Absolute maximum value (for HARD constraints). */
  private double maxValue = Double.MAX_VALUE;

  /** Minimum required value (for constraints like residence time). */
  private double minValue = 0.0;

  /** Whether a separate facility-specific operating limit is configured. */
  private boolean operatingLimitSet = false;

  /** Facility-specific operating limit in {@link #getUnit()}. */
  private double operatingLimit = Double.NaN;

  /** Authority backing the facility-specific operating limit. */
  private ConstraintSource operatingLimitSource = ConstraintSource.USER_RULE;

  /** Free-text reference for the facility-specific operating limit. */
  private String operatingLimitSourceReference = "";

  /** Whether confidence was explicitly assigned to the operating override. */
  private boolean operatingLimitConfidenceSet = false;

  /** Evidence-quality confidence assigned to the operating override. */
  private double operatingLimitConfidence = Double.NaN;

  /** Whether an applicability range was explicitly assigned to the operating override. */
  private boolean operatingLimitValidityRangeSet = false;

  /** Lower inclusive applicability bound for the operating override. */
  private double operatingLimitValidityMinimum = Double.NaN;

  /** Upper inclusive applicability bound for the operating override. */
  private double operatingLimitValidityMaximum = Double.NaN;

  /** Fraction of design value that triggers a warning (e.g., 0.9 = 90%). */
  private double warningThreshold = 0.9;

  /**
   * Supplier function to get current value from equipment. Marked transient because lambdas/method references are not
   * serializable. After deserialization, this will be null and the cached currentValue will be used instead.
   */
  private transient DoubleSupplier valueSupplier;

  /** Cached current value (updated when getCurrentValue() is called). */
  private double currentValue = 0.0;

  /** Whether a current value was explicitly assigned or successfully sampled. */
  private boolean currentValueSet = false;

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
   * Configures a facility-specific operating limit without replacing the declared design/default value or its
   * provenance.
   *
   * <p>
   * The override follows the existing constraint direction. For a minimum constraint it is the minimum acceptable
   * operating value; otherwise it is the maximum acceptable operating value. Calling this method clears any previously
   * configured override confidence and validity range so stale metadata cannot silently follow a changed limit.
   * </p>
   *
   * @param limit finite positive operating limit in {@link #getUnit()}
   * @param source authority backing the override; {@code null} is treated as {@link ConstraintSource#USER_RULE}
   * @param sourceReference free-text facility rule, operating procedure, or data reference
   * @return this constraint for method chaining
   * @throws IllegalArgumentException if the limit is non-finite or not positive
   */
  public CapacityConstraint setOperatingLimit(double limit, ConstraintSource source, String sourceReference) {
    if (!Double.isFinite(limit) || limit <= 0.0) {
      throw new IllegalArgumentException("operating limit must be finite and positive");
    }
    operatingLimit = limit;
    operatingLimitSet = true;
    operatingLimitSource = source == null ? ConstraintSource.USER_RULE : source;
    operatingLimitSourceReference = sourceReference == null ? "" : sourceReference;
    clearOperatingLimitConfidence();
    clearOperatingLimitValidityRange();
    return this;
  }

  /**
   * Clears the facility-specific operating override and restores the design/default limit as the applicable limit.
   *
   * @return this constraint for method chaining
   */
  public CapacityConstraint clearOperatingLimit() {
    operatingLimit = Double.NaN;
    operatingLimitSet = false;
    operatingLimitSource = ConstraintSource.USER_RULE;
    operatingLimitSourceReference = "";
    clearOperatingLimitConfidence();
    clearOperatingLimitValidityRange();
    return this;
  }

  /**
   * Sets evidence-quality confidence for the configured operating override.
   *
   * @param confidence confidence from zero to one, inclusive
   * @return this constraint for method chaining
   * @throws IllegalStateException if no operating limit is configured
   * @throws IllegalArgumentException if confidence is non-finite or outside [0, 1]
   */
  public CapacityConstraint setOperatingLimitConfidence(double confidence) {
    requireOperatingLimit();
    validateConfidence(confidence);
    operatingLimitConfidence = confidence;
    operatingLimitConfidenceSet = true;
    return this;
  }

  /**
   * Clears explicitly assigned operating-override confidence.
   *
   * @return this constraint for method chaining
   */
  public CapacityConstraint clearOperatingLimitConfidence() {
    operatingLimitConfidence = Double.NaN;
    operatingLimitConfidenceSet = false;
    return this;
  }

  /**
   * Sets the scalar applicability range for the configured operating override.
   *
   * @param minimum lower inclusive bound in {@link #getUnit()}
   * @param maximum upper inclusive bound in {@link #getUnit()}
   * @return this constraint for method chaining
   * @throws IllegalStateException if no operating limit is configured
   * @throws IllegalArgumentException if either bound is non-finite or minimum exceeds maximum
   */
  public CapacityConstraint setOperatingLimitValidityRange(double minimum, double maximum) {
    requireOperatingLimit();
    validateValidityRange(minimum, maximum);
    operatingLimitValidityMinimum = minimum;
    operatingLimitValidityMaximum = maximum;
    operatingLimitValidityRangeSet = true;
    return this;
  }

  /**
   * Clears the configured operating-override applicability range.
   *
   * @return this constraint for method chaining
   */
  public CapacityConstraint clearOperatingLimitValidityRange() {
    operatingLimitValidityMinimum = Double.NaN;
    operatingLimitValidityMaximum = Double.NaN;
    operatingLimitValidityRangeSet = false;
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
   * Sets the provenance of this constraint.
   *
   * @param source the kind of authority backing the limit; {@code null} is treated as {@link ConstraintSource#DEFAULT}
   * @return this constraint for method chaining
   */
  public CapacityConstraint setSource(ConstraintSource source) {
    this.source = source == null ? ConstraintSource.DEFAULT : source;
    return this;
  }

  /**
   * Sets the provenance together with a free-text reference identifying the specific source.
   *
   * @param source the kind of authority backing the limit; {@code null} is treated as {@link ConstraintSource#DEFAULT}
   * @param sourceReference free-text identifier such as a standard number, an owning team, or the historian tag set
   * used to fit the limit; {@code null} is stored as an empty string
   * @return this constraint for method chaining
   */
  public CapacityConstraint setSource(ConstraintSource source, String sourceReference) {
    this.source = source == null ? ConstraintSource.DEFAULT : source;
    this.sourceReference = sourceReference == null ? "" : sourceReference;
    return this;
  }

  /**
   * Sets only the free-text source reference, leaving the {@link ConstraintSource} unchanged. Useful for constraint
   * subclasses that already declare their own source in the constructor.
   *
   * @param sourceReference free-text identifier such as a standard number, an owning team, or the historian tag set
   * used to fit the limit; {@code null} is stored as an empty string
   * @return this constraint for method chaining
   */
  public CapacityConstraint setSourceReference(String sourceReference) {
    this.sourceReference = sourceReference == null ? "" : sourceReference;
    return this;
  }

  /**
   * Gets the provenance of this constraint.
   *
   * @return the source, never {@code null}; defaults to {@link ConstraintSource#DEFAULT}
   */
  public ConstraintSource getSource() {
    return source == null ? ConstraintSource.DEFAULT : source;
  }

  /**
   * Gets the free-text source reference, such as a standard number, owning team, or historian tag set.
   *
   * @return the reference string, empty if none has been set
   */
  public String getSourceReference() {
    return sourceReference == null ? "" : sourceReference;
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
    this.currentValueSet = true;
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
      currentValueSet = true;
    }
    return currentValue;
  }

  /**
   * Checks whether a value can be sampled or an explicit value has been retained.
   *
   * <p>
   * This method does not invoke the supplier or assert that its value is finite. The legacy getter still returns zero
   * for an unset value; evidence consumers can use this method to distinguish that default from an explicitly assigned
   * zero. Older serialized constraints have no assignment flag, so their cached values require reassignment before they
   * qualify as current evidence.
   * </p>
   *
   * @return true when a supplier or an explicitly assigned or sampled value exists
   */
  public boolean hasCurrentValue() {
    return valueSupplier != null || currentValueSet;
  }

  /**
   * Gets the raw operating value before normalization by the design limit.
   *
   * @return current operating value in {@link #getUnit()}
   */
  public double getRawValue() {
    return getCurrentValue();
  }

  /**
   * Returns the denominator used for utilization, including minimum requirements.
   *
   * @return design limit in {@link #getUnit()}, or Double.MAX_VALUE if unset
   */
  public double getDesignLimit() {
    return isMinimumConstraint() ? minValue : designValue;
  }

  /**
   * Returns the physical limit currently used for utilization and feasibility.
   *
   * @return configured operating override when present, otherwise the design/default limit
   */
  public double getApplicableLimit() {
    return operatingLimitSet ? operatingLimit : getDesignLimit();
  }

  /**
   * Returns the role of the physical limit currently used for utilization and feasibility.
   *
   * @return configured operating, qualified design/rated, or default screening role
   */
  public ApplicableLimitRole getApplicableLimitRole() {
    if (operatingLimitSet) {
      return ApplicableLimitRole.CONFIGURED_OPERATING;
    }
    if (getSource() == ConstraintSource.DEFAULT && ("default".equals(dataSource) || "not_set".equals(dataSource))) {
      return ApplicableLimitRole.DEFAULT_SCREENING;
    }
    return ApplicableLimitRole.DESIGN_RATED;
  }

  /** @return true when a separate facility-specific operating limit is active */
  public boolean hasOperatingLimit() {
    return operatingLimitSet;
  }

  /**
   * Gets the configured operating override.
   *
   * @return operating limit in {@link #getUnit()}, or NaN when unset
   */
  public double getOperatingLimit() {
    return operatingLimitSet ? operatingLimit : Double.NaN;
  }

  /** @return authority backing the operating override, or null when unset */
  public ConstraintSource getOperatingLimitSource() {
    return operatingLimitSet ? operatingLimitSource == null ? ConstraintSource.USER_RULE : operatingLimitSource : null;
  }

  /** @return operating-override reference, empty when unset */
  public String getOperatingLimitSourceReference() {
    return operatingLimitSet && operatingLimitSourceReference != null ? operatingLimitSourceReference : "";
  }

  /** @return true when confidence is explicitly assigned to the operating override */
  public boolean hasOperatingLimitConfidence() {
    return operatingLimitSet && operatingLimitConfidenceSet;
  }

  /** @return operating-override confidence, or NaN when unset */
  public double getOperatingLimitConfidence() {
    return hasOperatingLimitConfidence() ? operatingLimitConfidence : Double.NaN;
  }

  /** @return true when an applicability range is assigned to the operating override */
  public boolean hasOperatingLimitValidityRange() {
    return operatingLimitSet && operatingLimitValidityRangeSet;
  }

  /** @return lower operating-override applicability bound, or NaN */
  public double getOperatingLimitValidityMinimum() {
    return hasOperatingLimitValidityRange() ? operatingLimitValidityMinimum : Double.NaN;
  }

  /** @return upper operating-override applicability bound, or NaN */
  public double getOperatingLimitValidityMaximum() {
    return hasOperatingLimitValidityRange() ? operatingLimitValidityMaximum : Double.NaN;
  }

  /** @return authority backing the applicable limit */
  public ConstraintSource getApplicableLimitSource() {
    return operatingLimitSet ? getOperatingLimitSource() : getSource();
  }

  /** @return reference supporting the applicable limit */
  public String getApplicableLimitSourceReference() {
    return operatingLimitSet ? getOperatingLimitSourceReference() : getSourceReference();
  }

  /** @return true when confidence is assigned to the applicable limit */
  public boolean hasApplicableLimitConfidence() {
    return operatingLimitSet ? operatingLimitConfidenceSet : confidenceSet;
  }

  /** @return applicable-limit confidence, or NaN when unset */
  public double getApplicableLimitConfidence() {
    return operatingLimitSet ? (operatingLimitConfidenceSet ? operatingLimitConfidence : Double.NaN)
        : (confidenceSet ? confidence : Double.NaN);
  }

  /** @return true when an applicability range is assigned to the applicable limit */
  public boolean hasApplicableLimitValidityRange() {
    return operatingLimitSet ? operatingLimitValidityRangeSet : validityRangeSet;
  }

  /** @return lower applicable-limit validity bound, or NaN */
  public double getApplicableLimitValidityMinimum() {
    return operatingLimitSet ? (operatingLimitValidityRangeSet ? operatingLimitValidityMinimum : Double.NaN)
        : (validityRangeSet ? validityMinimum : Double.NaN);
  }

  /** @return upper applicable-limit validity bound, or NaN */
  public double getApplicableLimitValidityMaximum() {
    return operatingLimitSet ? (operatingLimitValidityRangeSet ? operatingLimitValidityMaximum : Double.NaN)
        : (validityRangeSet ? validityMaximum : Double.NaN);
  }

  /**
   * Returns the declared engineering basis without inferring vendor certification.
   *
   * @return default, datasheet, autoSize, standard, empirical, or custom
   */
  public String getBasis() {
    ConstraintSource resolvedSource = getSource();
    if (resolvedSource == ConstraintSource.VENDOR_DATASHEET) {
      return "datasheet";
    }
    if (resolvedSource == ConstraintSource.AUTO_SIZE) {
      return "autoSize";
    }
    if (resolvedSource == ConstraintSource.CONFORMITY_STANDARD) {
      return "standard";
    }
    if (resolvedSource == ConstraintSource.PROCESS_EMPIRICAL) {
      return "empirical";
    }
    if (resolvedSource == ConstraintSource.USER_RULE
        || (!"default".equals(dataSource) && !"not_set".equals(dataSource))) {
      return "custom";
    }
    return "default";
  }

  /**
   * Gets utilization as a fraction of the declared design limit.
   *
   * @return utilization fraction
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
    double applicableLimit = getApplicableLimit();
    if (isMinimumConstraint()) {
      // This is a minimum constraint (e.g., residence time)
      if (currentValue <= 0) {
        return MAX_UTILIZATION;
      }
      return Math.min(applicableLimit / currentValue, MAX_UTILIZATION);
    }
    if (applicableLimit <= 0 || applicableLimit == Double.MAX_VALUE) {
      return 0.0;
    }
    return Math.min(currentValue / applicableLimit, MAX_UTILIZATION);
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
    validateConfidence(confidence);
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
    validateValidityRange(minimum, maximum);
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
   * Checks whether the current value lies within the applicability range of the active limit.
   *
   * @return true when the active design or operating limit declares a range and the current value is within it
   */
  public boolean isCurrentValueWithinApplicableLimitValidityRange() {
    if (!hasApplicableLimitValidityRange()) {
      return false;
    }
    double value = getCurrentValue();
    return value >= getApplicableLimitValidityMinimum() && value <= getApplicableLimitValidityMaximum();
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

  /** Requires a configured operating limit before assigning its evidence metadata. */
  private void requireOperatingLimit() {
    if (!operatingLimitSet) {
      throw new IllegalStateException("an operating limit must be configured first");
    }
  }

  /**
   * Validates one evidence-quality confidence value.
   *
   * @param value confidence value to validate
   */
  private static void validateConfidence(double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException("confidence must be finite and in the range [0, 1]");
    }
  }

  /**
   * Validates one inclusive scalar applicability range.
   *
   * @param minimum lower inclusive bound
   * @param maximum upper inclusive bound
   */
  private static void validateValidityRange(double minimum, double maximum) {
    if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
      throw new IllegalArgumentException("validity range bounds must be finite");
    }
    if (minimum > maximum) {
      throw new IllegalArgumentException("validity range minimum must not exceed maximum");
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(": ");
    sb.append(String.format("%.2f", getCurrentValue())).append(" ").append(unit);
    sb.append(String.format(" (%.1f%% of %s %.2f)", getUtilizationPercent(),
        operatingLimitSet ? "operating limit" : isMinimumConstraint() ? "minimum" : "design", getApplicableLimit()));
    if (isViolated()) {
      sb.append(" [EXCEEDED]");
    } else if (isNearLimit()) {
      sb.append(" [NEAR LIMIT]");
    }
    return sb.toString();
  }
}
