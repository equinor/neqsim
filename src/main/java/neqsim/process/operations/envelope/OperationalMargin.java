package neqsim.process.operations.envelope;

import java.io.Serializable;
import com.google.gson.JsonObject;
import neqsim.process.equipment.capacity.CapacityConstraint;

/**
 * Snapshot of one operating margin derived from an equipment capacity constraint.
 *
 * <p>
 * The margin is advisory: it does not replace the equipment-specific constraint source, but gives operational workflows
 * a consistent ranking and JSON representation for dashboards, MCP tools, and plant-data evidence packages.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalMargin implements Serializable, Comparable<OperationalMargin> {
  private static final long serialVersionUID = 1L;

  /** Margin status thresholds used for operational screening. */
  public enum Status {
    /** Margin is greater than the advisory threshold. */
    NORMAL(0),
    /** Margin is below the advisory threshold but above warning. */
    NARROWING(1),
    /** Margin is below warning threshold. */
    WARNING(2),
    /** Margin is below critical threshold. */
    CRITICAL(3),
    /** Constraint is already violated. */
    VIOLATED(4);

    private final int rank;

    /**
     * Creates a status with a severity rank.
     *
     * @param rank severity rank where higher values are more severe
     */
    Status(int rank) {
      this.rank = rank;
    }

    /**
     * Returns the severity rank.
     *
     * @return severity rank where higher values are more severe
     */
    public int getRank() {
      return rank;
    }
  }

  /** Advisory threshold in percent. */
  public static final double NARROWING_MARGIN_PERCENT = 20.0;

  /** Warning threshold in percent. */
  public static final double WARNING_MARGIN_PERCENT = 10.0;

  /** Critical threshold in percent. */
  public static final double CRITICAL_MARGIN_PERCENT = 5.0;

  private final String equipmentName;
  private final String constraintName;
  private final String key;
  private final double currentValue;
  private final double limitValue;
  private final double utilizationPercent;
  private final double marginPercent;
  private final String unit;
  private final String constraintType;
  private final String severity;
  private final String dataSource;
  private final double designValue;
  private final String applicableLimitRole;
  private final String applicableLimitSource;
  private final String applicableLimitSourceReference;
  private final String description;
  private final boolean minimumConstraint;
  private final boolean hardLimitExceeded;
  private final Status status;

  /**
   * Creates an operational margin snapshot.
   *
   * @param equipmentName equipment name
   * @param constraintName constraint name
   * @param currentValue current value in the constraint unit
   * @param limitValue design or minimum limit value in the constraint unit
   * @param utilizationPercent current utilization in percent
   * @param marginPercent remaining margin in percent
   * @param unit engineering unit
   * @param constraintType constraint type name
   * @param severity constraint severity name
   * @param dataSource design data source label
   * @param description human-readable constraint description
   * @param minimumConstraint true when lower values are worse
   * @param hardLimitExceeded true when an absolute hard limit is exceeded
   */
  public OperationalMargin(String equipmentName, String constraintName, double currentValue, double limitValue,
      double utilizationPercent, double marginPercent, String unit, String constraintType, String severity,
      String dataSource, String description, boolean minimumConstraint, boolean hardLimitExceeded) {
    this(equipmentName, constraintName, currentValue, limitValue, utilizationPercent, marginPercent, unit,
        constraintType, severity, dataSource, limitValue, "DESIGN_RATED", "DEFAULT", "", description, minimumConstraint,
        hardLimitExceeded);
  }

  /**
   * Creates an operational margin with explicit applicable-limit identity.
   *
   * @param equipmentName equipment name
   * @param constraintName constraint name
   * @param currentValue current value in the constraint unit
   * @param limitValue applicable limit in the constraint unit
   * @param utilizationPercent current utilization in percent
   * @param marginPercent remaining margin in percent
   * @param unit engineering unit
   * @param constraintType constraint type name
   * @param severity constraint severity name
   * @param dataSource design/default data source
   * @param designValue preserved design/default limit
   * @param applicableLimitRole applicable-limit role
   * @param applicableLimitSource applicable-limit authority
   * @param applicableLimitSourceReference applicable-limit reference
   * @param description human-readable constraint description
   * @param minimumConstraint true when lower values are worse
   * @param hardLimitExceeded true when an absolute hard limit is exceeded
   */
  private OperationalMargin(String equipmentName, String constraintName, double currentValue, double limitValue,
      double utilizationPercent, double marginPercent, String unit, String constraintType, String severity,
      String dataSource, double designValue, String applicableLimitRole, String applicableLimitSource,
      String applicableLimitSourceReference, String description, boolean minimumConstraint, boolean hardLimitExceeded) {
    this.equipmentName = clean(equipmentName);
    this.constraintName = clean(constraintName);
    this.key = this.equipmentName + "." + this.constraintName;
    this.currentValue = currentValue;
    this.limitValue = limitValue;
    this.utilizationPercent = utilizationPercent;
    this.marginPercent = marginPercent;
    this.unit = clean(unit);
    this.constraintType = clean(constraintType);
    this.severity = clean(severity);
    this.dataSource = clean(dataSource);
    this.designValue = designValue;
    this.applicableLimitRole = clean(applicableLimitRole);
    this.applicableLimitSource = clean(applicableLimitSource);
    this.applicableLimitSourceReference = clean(applicableLimitSourceReference);
    this.description = clean(description);
    this.minimumConstraint = minimumConstraint;
    this.hardLimitExceeded = hardLimitExceeded;
    this.status = classify(marginPercent, hardLimitExceeded);
  }

  /**
   * Builds an operational margin from a capacity constraint.
   *
   * @param equipmentName equipment name
   * @param constraint capacity constraint to read
   * @return operational margin snapshot
   * @throws IllegalArgumentException if the constraint is null
   */
  public static OperationalMargin fromConstraint(String equipmentName, CapacityConstraint constraint) {
    if (constraint == null) {
      throw new IllegalArgumentException("constraint must not be null");
    }
    double current = constraint.getCurrentValue();
    return new OperationalMargin(equipmentName, constraint.getName(), current, constraint.getApplicableLimit(),
        constraint.getUtilizationPercent(), constraint.getMarginPercent(), constraint.getUnit(),
        constraint.getType().name(), constraint.getSeverity().name(), constraint.getDataSource(),
        constraint.getDisplayDesignValue(), constraint.getApplicableLimitRole().name(),
        constraint.getApplicableLimitSource().name(), constraint.getApplicableLimitSourceReference(),
        constraint.getDescription(), constraint.isMinimumConstraint(), constraint.isHardLimitExceeded());
  }

  /**
   * Classifies a margin value into an operational status.
   *
   * @param marginPercent margin in percent
   * @param hardLimitExceeded true when an absolute hard limit is exceeded
   * @return operational margin status
   */
  public static Status classify(double marginPercent, boolean hardLimitExceeded) {
    if (hardLimitExceeded || marginPercent <= 0.0) {
      return Status.VIOLATED;
    }
    if (marginPercent <= CRITICAL_MARGIN_PERCENT) {
      return Status.CRITICAL;
    }
    if (marginPercent <= WARNING_MARGIN_PERCENT) {
      return Status.WARNING;
    }
    if (marginPercent <= NARROWING_MARGIN_PERCENT) {
      return Status.NARROWING;
    }
    return Status.NORMAL;
  }

  /**
   * Returns the equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Returns the constraint name.
   *
   * @return constraint name
   */
  public String getConstraintName() {
    return constraintName;
  }

  /**
   * Returns the stable margin key.
   *
   * @return key in the form {@code equipment.constraint}
   */
  public String getKey() {
    return key;
  }

  /**
   * Returns the current value.
   *
   * @return current value in the margin unit
   */
  public double getCurrentValue() {
    return currentValue;
  }

  /**
   * Returns the limit value used for display.
   *
   * @return design or minimum limit value
   */
  public double getLimitValue() {
    return limitValue;
  }

  /**
   * Returns utilization as a percent.
   *
   * @return utilization in percent
   */
  public double getUtilizationPercent() {
    return utilizationPercent;
  }

  /**
   * Returns remaining margin as a percent.
   *
   * @return margin in percent
   */
  public double getMarginPercent() {
    return marginPercent;
  }

  /**
   * Returns the engineering unit.
   *
   * @return engineering unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the capacity constraint type.
   *
   * @return constraint type name
   */
  public String getConstraintType() {
    return constraintType;
  }

  /**
   * Returns the capacity constraint severity.
   *
   * @return severity name
   */
  public String getSeverity() {
    return severity;
  }

  /**
   * Returns the design data source.
   *
   * @return data source label
   */
  public String getDataSource() {
    return dataSource;
  }

  /** @return preserved design/default limit */
  public double getDesignValue() {
    return designValue;
  }

  /** @return role of the limit used to calculate the margin */
  public String getApplicableLimitRole() {
    return applicableLimitRole;
  }

  /** @return authority backing the applicable limit */
  public String getApplicableLimitSource() {
    return applicableLimitSource;
  }

  /** @return reference supporting the applicable limit */
  public String getApplicableLimitSourceReference() {
    return applicableLimitSourceReference;
  }

  /**
   * Returns the description.
   *
   * @return human-readable description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns whether this is a minimum constraint.
   *
   * @return true when lower values are worse
   */
  public boolean isMinimumConstraint() {
    return minimumConstraint;
  }

  /**
   * Returns whether a hard limit is exceeded.
   *
   * @return true when a hard limit is exceeded
   */
  public boolean isHardLimitExceeded() {
    return hardLimitExceeded;
  }

  /**
   * Returns the operational status.
   *
   * @return operational status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Converts the margin to JSON.
   *
   * @return JSON object representation
   */
  public JsonObject toJsonObject() {
    JsonObject json = new JsonObject();
    json.addProperty("key", key);
    json.addProperty("equipmentName", equipmentName);
    json.addProperty("constraintName", constraintName);
    json.addProperty("status", status.name());
    json.addProperty("currentValue", currentValue);
    json.addProperty("limitValue", limitValue);
    json.addProperty("designValue", designValue);
    json.addProperty("applicableLimitRole", applicableLimitRole);
    json.addProperty("applicableLimitSource", applicableLimitSource);
    json.addProperty("applicableLimitSourceReference", applicableLimitSourceReference);
    json.addProperty("utilizationPercent", utilizationPercent);
    json.addProperty("marginPercent", marginPercent);
    json.addProperty("unit", unit);
    json.addProperty("constraintType", constraintType);
    json.addProperty("severity", severity);
    json.addProperty("dataSource", dataSource);
    json.addProperty("description", description);
    json.addProperty("minimumConstraint", minimumConstraint);
    json.addProperty("hardLimitExceeded", hardLimitExceeded);
    return json;
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(OperationalMargin other) {
    int severityCompare = other.status.getRank() - status.getRank();
    if (severityCompare != 0) {
      return severityCompare;
    }
    return Double.compare(marginPercent, other.marginPercent);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return key + " " + status + " margin=" + marginPercent + "%";
  }

  /**
   * Cleans nullable text to a non-null trimmed value.
   *
   * @param text text to clean
   * @return trimmed text or empty string
   */
  private static String clean(String text) {
    return text == null ? "" : text.trim();
  }
}
