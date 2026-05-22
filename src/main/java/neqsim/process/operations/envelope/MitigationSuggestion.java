package neqsim.process.operations.envelope;

import com.google.gson.JsonObject;
import java.io.Serializable;

/**
 * Advisory operating action suggested from a narrow or violated margin.
 *
 * @author ESOL
 * @version 1.0
 */
public final class MitigationSuggestion implements Serializable, Comparable<MitigationSuggestion> {
  private static final long serialVersionUID = 1L;

  /** Suggested action urgency. */
  public enum Priority {
    /** Immediate operator or engineering review is recommended. */
    IMMEDIATE(4),
    /** Prompt action is recommended. */
    HIGH(3),
    /** Planned adjustment or closer monitoring is recommended. */
    MEDIUM(2),
    /** Advisory monitoring only. */
    LOW(1);

    private final int rank;

    /**
     * Creates a priority.
     *
     * @param rank priority rank where higher values are more urgent
     */
    Priority(int rank) {
      this.rank = rank;
    }

    /**
     * Returns the priority rank.
     *
     * @return priority rank
     */
    public int getRank() {
      return rank;
    }
  }

  /** Suggested action category. */
  public enum Category {
    /** Change a set point or manipulated variable. */
    SETPOINT_CHANGE,
    /** Reduce throughput or equipment loading. */
    LOAD_REDUCTION,
    /** Route, valve, or control-mode review. */
    OPERABILITY_REVIEW,
    /** More plant data or design evidence is needed. */
    DATA_QUALITY
  }

  private final String marginKey;
  private final String description;
  private final String targetEquipment;
  private final String targetVariable;
  private final double suggestedValue;
  private final String unit;
  private final Priority priority;
  private final Category category;
  private final String expectedImprovement;
  private final double confidence;

  /**
   * Creates a mitigation suggestion.
   *
   * @param marginKey margin key that triggered the suggestion
   * @param description suggested action description
   * @param targetEquipment target equipment name
   * @param targetVariable target variable or constraint name
   * @param suggestedValue suggested value, or {@link Double#NaN} when qualitative
   * @param unit engineering unit
   * @param priority priority level
   * @param category action category
   * @param expectedImprovement expected improvement text
   * @param confidence confidence from 0.0 to 1.0
   */
  public MitigationSuggestion(String marginKey, String description, String targetEquipment,
      String targetVariable, double suggestedValue, String unit, Priority priority,
      Category category, String expectedImprovement, double confidence) {
    this.marginKey = clean(marginKey);
    this.description = clean(description);
    this.targetEquipment = clean(targetEquipment);
    this.targetVariable = clean(targetVariable);
    this.suggestedValue = suggestedValue;
    this.unit = clean(unit);
    this.priority = priority == null ? Priority.LOW : priority;
    this.category = category == null ? Category.OPERABILITY_REVIEW : category;
    this.expectedImprovement = clean(expectedImprovement);
    this.confidence = Math.max(0.0, Math.min(1.0, confidence));
  }

  /**
   * Builds a generic suggestion for a narrow margin.
   *
   * @param margin margin that triggered the suggestion
   * @return mitigation suggestion
   */
  public static MitigationSuggestion fromMargin(OperationalMargin margin) {
    Priority priority = priorityFromStatus(margin.getStatus());
    Category category = chooseCategory(margin);
    double suggestedValue = suggestedValue(margin);
    String direction = margin.isMinimumConstraint() ? "increase" : "reduce";
    String description = "Review " + margin.getEquipmentName() + " "
        + margin.getConstraintName() + " and " + direction + " loading away from the limit.";
    String expected = "Restore margin above " + OperationalMargin.WARNING_MARGIN_PERCENT
        + "% and confirm against plant data.";
    return new MitigationSuggestion(margin.getKey(), description, margin.getEquipmentName(),
        margin.getConstraintName(), suggestedValue, margin.getUnit(), priority, category, expected,
        0.6);
  }

  /**
   * Converts a margin status to action priority.
   *
   * @param status margin status
   * @return action priority
   */
  private static Priority priorityFromStatus(OperationalMargin.Status status) {
    if (status == OperationalMargin.Status.VIOLATED || status == OperationalMargin.Status.CRITICAL) {
      return Priority.IMMEDIATE;
    }
    if (status == OperationalMargin.Status.WARNING) {
      return Priority.HIGH;
    }
    if (status == OperationalMargin.Status.NARROWING) {
      return Priority.MEDIUM;
    }
    return Priority.LOW;
  }

  /**
   * Chooses a suggestion category from the margin metadata.
   *
   * @param margin operational margin
   * @return suggestion category
   */
  private static Category chooseCategory(OperationalMargin margin) {
    String name = margin.getConstraintName().toLowerCase();
    if (name.contains("opening") || name.contains("pressure") || name.contains("temperature")) {
      return Category.SETPOINT_CHANGE;
    }
    if (name.contains("flow") || name.contains("power") || name.contains("load")) {
      return Category.LOAD_REDUCTION;
    }
    if ("not_set".equalsIgnoreCase(margin.getDataSource())) {
      return Category.DATA_QUALITY;
    }
    return Category.OPERABILITY_REVIEW;
  }

  /**
   * Calculates a conservative suggested value.
   *
   * @param margin operational margin
   * @return suggested value, or {@link Double#NaN} when no finite limit exists
   */
  private static double suggestedValue(OperationalMargin margin) {
    if (Double.isNaN(margin.getLimitValue()) || Double.isInfinite(margin.getLimitValue())) {
      return Double.NaN;
    }
    if (margin.isMinimumConstraint()) {
      return margin.getLimitValue() * 1.10;
    }
    return margin.getLimitValue() * 0.90;
  }

  /**
   * Returns the triggering margin key.
   *
   * @return margin key
   */
  public String getMarginKey() {
    return marginKey;
  }

  /**
   * Returns the description.
   *
   * @return suggested action description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the target equipment.
   *
   * @return target equipment name
   */
  public String getTargetEquipment() {
    return targetEquipment;
  }

  /**
   * Returns the target variable.
   *
   * @return target variable or constraint name
   */
  public String getTargetVariable() {
    return targetVariable;
  }

  /**
   * Returns the suggested value.
   *
   * @return suggested value or {@link Double#NaN}
   */
  public double getSuggestedValue() {
    return suggestedValue;
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
   * Returns the priority.
   *
   * @return priority level
   */
  public Priority getPriority() {
    return priority;
  }

  /**
   * Returns the category.
   *
   * @return action category
   */
  public Category getCategory() {
    return category;
  }

  /**
   * Returns the expected improvement text.
   *
   * @return expected improvement text
   */
  public String getExpectedImprovement() {
    return expectedImprovement;
  }

  /**
   * Returns suggestion confidence.
   *
   * @return confidence from 0.0 to 1.0
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Converts the suggestion to JSON.
   *
   * @return JSON object representation
   */
  public JsonObject toJsonObject() {
    JsonObject json = new JsonObject();
    json.addProperty("marginKey", marginKey);
    json.addProperty("description", description);
    json.addProperty("targetEquipment", targetEquipment);
    json.addProperty("targetVariable", targetVariable);
    json.addProperty("suggestedValue", suggestedValue);
    json.addProperty("unit", unit);
    json.addProperty("priority", priority.name());
    json.addProperty("category", category.name());
    json.addProperty("expectedImprovement", expectedImprovement);
    json.addProperty("confidence", confidence);
    return json;
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(MitigationSuggestion other) {
    return other.priority.getRank() - priority.getRank();
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