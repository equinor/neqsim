package neqsim.process.safety.esd;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceptance criterion for an emergency shutdown dynamic test.
 *
 * <p>
 * Criteria evaluate monitored logical tags, process logic states, simulation errors, or field-data
 * deviations. Logical tags normally come from {@link neqsim.process.operations.OperationalTagMap}
 * bindings, while direct NeqSim automation addresses can also be used as monitor names.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class EmergencyShutdownTestCriterion implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Types of acceptance checks supported by the ESD test runner. */
  public enum CriterionType {
    /** Final monitored value must be less than or equal to the target value. */
    FINAL_LESS_OR_EQUAL,
    /** Final monitored value must be greater than or equal to the target value. */
    FINAL_GREATER_OR_EQUAL,
    /** Maximum monitored value must be less than or equal to the target value. */
    MAX_LESS_OR_EQUAL,
    /** Maximum monitored value must be greater than or equal to the target value. */
    MAX_GREATER_OR_EQUAL,
    /** Minimum monitored value must be less than or equal to the target value. */
    MIN_LESS_OR_EQUAL,
    /** Minimum monitored value must be greater than or equal to the target value. */
    MIN_GREATER_OR_EQUAL,
    /** Initial value minus final value must be greater than or equal to the target value. */
    DECREASE_GREATER_OR_EQUAL,
    /** Final value minus initial value must be greater than or equal to the target value. */
    INCREASE_GREATER_OR_EQUAL,
    /** Absolute model-to-field deviation must be less than or equal to the target value. */
    FIELD_ABSOLUTE_DEVIATION_LESS_OR_EQUAL,
    /**
     * Relative model-to-field deviation fraction must be less than or equal to the target value.
     */
    FIELD_RELATIVE_DEVIATION_LESS_OR_EQUAL,
    /** Named process logic must complete during the scenario. */
    LOGIC_COMPLETED,
    /** Scenario must finish without simulation errors. */
    NO_SIMULATION_ERRORS
  }

  private final String id;
  private final CriterionType type;
  private final String logicalTag;
  private final String logicName;
  private final double targetValue;
  private final String unit;
  private final String clause;
  private final String severity;
  private final String description;
  private final String recommendation;

  /**
   * Creates an acceptance criterion.
   *
   * @param id stable criterion identifier
   * @param type criterion type
   * @param logicalTag logical tag or automation address used by signal criteria
   * @param logicName process logic name used by logic criteria
   * @param targetValue target value used by numeric criteria
   * @param unit engineering unit for target and monitored values
   * @param clause standards clause or company requirement reference
   * @param severity finding severity when the criterion fails
   * @param description human-readable criterion description
   * @param recommendation recommended action when the criterion fails
   */
  private EmergencyShutdownTestCriterion(String id, CriterionType type, String logicalTag,
      String logicName, double targetValue, String unit, String clause, String severity,
      String description, String recommendation) {
    this.id = requireText(id, "id");
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    this.type = type;
    this.logicalTag = clean(logicalTag);
    this.logicName = clean(logicName);
    this.targetValue = targetValue;
    this.unit = clean(unit);
    this.clause = clean(clause);
    this.severity = clean(severity).isEmpty() ? "HIGH" : clean(severity);
    this.description = clean(description);
    this.recommendation = clean(recommendation);
  }

  /**
   * Creates a criterion requiring the final value to be at most a limit.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag or automation address
   * @param limit maximum allowed final value
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion finalAtMost(String id, String logicalTag,
      double limit, String unit) {
    return signal(id, CriterionType.FINAL_LESS_OR_EQUAL, logicalTag, limit, unit,
        "Final value must be at or below the specified limit.");
  }

  /**
   * Creates a criterion requiring the final value to be at least a limit.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag or automation address
   * @param limit minimum allowed final value
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion finalAtLeast(String id, String logicalTag,
      double limit, String unit) {
    return signal(id, CriterionType.FINAL_GREATER_OR_EQUAL, logicalTag, limit, unit,
        "Final value must be at or above the specified limit.");
  }

  /**
   * Creates a criterion requiring the maximum value to stay below a limit.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag or automation address
   * @param limit maximum allowed value
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion maxAtMost(String id, String logicalTag, double limit,
      String unit) {
    return signal(id, CriterionType.MAX_LESS_OR_EQUAL, logicalTag, limit, unit,
        "Maximum value must be at or below the specified limit.");
  }

  /**
   * Creates a criterion requiring the maximum value to exceed a threshold.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag or automation address
   * @param threshold required maximum value
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion maxAtLeast(String id, String logicalTag,
      double threshold, String unit) {
    return signal(id, CriterionType.MAX_GREATER_OR_EQUAL, logicalTag, threshold, unit,
        "Maximum value must be at or above the specified threshold.");
  }

  /**
   * Creates a criterion requiring the minimum value to be at most a limit.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag or automation address
   * @param limit maximum allowed minimum value
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion minAtMost(String id, String logicalTag, double limit,
      String unit) {
    return signal(id, CriterionType.MIN_LESS_OR_EQUAL, logicalTag, limit, unit,
        "Minimum value must be at or below the specified limit.");
  }

  /**
   * Creates a criterion requiring the minimum value to be at least a limit.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag or automation address
   * @param limit minimum allowed value
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion minAtLeast(String id, String logicalTag,
      double limit, String unit) {
    return signal(id, CriterionType.MIN_GREATER_OR_EQUAL, logicalTag, limit, unit,
        "Minimum value must be at or above the specified limit.");
  }

  /**
   * Creates a criterion requiring a monitored value to decrease by a minimum amount.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag or automation address
   * @param minimumDecrease required decrease from initial to final value
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion decreaseAtLeast(String id, String logicalTag,
      double minimumDecrease, String unit) {
    return signal(id, CriterionType.DECREASE_GREATER_OR_EQUAL, logicalTag, minimumDecrease, unit,
        "Value must decrease by at least the specified amount.");
  }

  /**
   * Creates a criterion requiring a monitored value to increase by a minimum amount.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag or automation address
   * @param minimumIncrease required increase from initial to final value
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion increaseAtLeast(String id, String logicalTag,
      double minimumIncrease, String unit) {
    return signal(id, CriterionType.INCREASE_GREATER_OR_EQUAL, logicalTag, minimumIncrease, unit,
        "Value must increase by at least the specified amount.");
  }

  /**
   * Creates a criterion requiring absolute model-to-field deviation below a limit.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag with field data
   * @param maximumDeviation maximum absolute deviation
   * @param unit engineering unit
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion fieldAbsoluteDeviationAtMost(String id,
      String logicalTag, double maximumDeviation, String unit) {
    return signal(id, CriterionType.FIELD_ABSOLUTE_DEVIATION_LESS_OR_EQUAL, logicalTag,
        maximumDeviation, unit, "Model-to-field absolute deviation must be within tolerance.");
  }

  /**
   * Creates a criterion requiring relative model-to-field deviation below a limit.
   *
   * @param id criterion identifier
   * @param logicalTag monitored logical tag with field data
   * @param maximumDeviationFraction maximum relative deviation fraction
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion fieldRelativeDeviationAtMost(String id,
      String logicalTag, double maximumDeviationFraction) {
    return signal(id, CriterionType.FIELD_RELATIVE_DEVIATION_LESS_OR_EQUAL, logicalTag,
        maximumDeviationFraction, "",
        "Model-to-field relative deviation must be within tolerance.");
  }

  /**
   * Creates a criterion requiring a named logic sequence to complete.
   *
   * @param id criterion identifier
   * @param logicName process logic name
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion logicCompleted(String id, String logicName) {
    return new EmergencyShutdownTestCriterion(id, CriterionType.LOGIC_COMPLETED, "", logicName,
        Double.NaN, "", "", "HIGH", "Logic sequence must complete during the test.",
        "Check C&E delays, final-element action completion, and permissive status.");
  }

  /**
   * Creates a criterion requiring no simulation errors.
   *
   * @param id criterion identifier
   * @return acceptance criterion
   */
  public static EmergencyShutdownTestCriterion noSimulationErrors(String id) {
    return new EmergencyShutdownTestCriterion(id, CriterionType.NO_SIMULATION_ERRORS, "", "",
        Double.NaN, "", "", "HIGH", "Transient simulation must finish without errors.",
        "Resolve model convergence, missing equipment, or invalid action sequencing issues.");
  }

  /**
   * Returns a copy with standards clause metadata.
   *
   * @param clause standards clause or requirement reference
   * @return copied criterion with clause metadata
   */
  public EmergencyShutdownTestCriterion withClause(String clause) {
    return new EmergencyShutdownTestCriterion(id, type, logicalTag, logicName, targetValue, unit,
        clause, severity, description, recommendation);
  }

  /**
   * Returns a copy with custom severity metadata.
   *
   * @param severity severity string such as LOW, MEDIUM, HIGH, or CRITICAL
   * @return copied criterion with severity metadata
   */
  public EmergencyShutdownTestCriterion withSeverity(String severity) {
    return new EmergencyShutdownTestCriterion(id, type, logicalTag, logicName, targetValue, unit,
        clause, severity, description, recommendation);
  }

  /**
   * Returns a copy with custom explanatory text.
   *
   * @param description criterion description
   * @param recommendation recommended action if the criterion fails
   * @return copied criterion with custom text
   */
  public EmergencyShutdownTestCriterion withText(String description, String recommendation) {
    return new EmergencyShutdownTestCriterion(id, type, logicalTag, logicName, targetValue, unit,
        clause, severity, description, recommendation);
  }

  /**
   * Gets the criterion identifier.
   *
   * @return criterion identifier
   */
  public String getId() {
    return id;
  }

  /**
   * Gets the criterion type.
   *
   * @return criterion type
   */
  public CriterionType getType() {
    return type;
  }

  /**
   * Gets the monitored logical tag.
   *
   * @return logical tag or empty string
   */
  public String getLogicalTag() {
    return logicalTag;
  }

  /**
   * Gets the process logic name.
   *
   * @return logic name or empty string
   */
  public String getLogicName() {
    return logicName;
  }

  /**
   * Gets the target value.
   *
   * @return target value or NaN when not used
   */
  public double getTargetValue() {
    return targetValue;
  }

  /**
   * Gets the engineering unit.
   *
   * @return unit string or empty string
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets the standards clause reference.
   *
   * @return clause reference or empty string
   */
  public String getClause() {
    return clause;
  }

  /**
   * Gets the failure severity.
   *
   * @return severity string
   */
  public String getSeverity() {
    return severity;
  }

  /**
   * Evaluates this criterion against a completed test result.
   *
   * @param signalStats monitored signal statistics keyed by logical tag
   * @param logicStates final logic states keyed by logic name
   * @param errors simulation errors recorded by the runner
   * @param fieldComparisons model-to-field comparisons keyed by logical tag
   * @return criterion result
   */
  Result evaluate(Map<String, EmergencyShutdownTestResult.SignalStats> signalStats,
      Map<String, String> logicStates, List<String> errors,
      Map<String, EmergencyShutdownTestResult.FieldComparison> fieldComparisons) {
    switch (type) {
      case LOGIC_COMPLETED:
        return evaluateLogic(logicStates);
      case NO_SIMULATION_ERRORS:
        return new Result(this, errors == null || errors.isEmpty(),
            errors == null ? 0.0 : errors.size(), 0.0,
            errors == null || errors.isEmpty() ? "No simulation errors were recorded."
                : "Simulation errors were recorded during the ESD test.");
      case FIELD_ABSOLUTE_DEVIATION_LESS_OR_EQUAL:
      case FIELD_RELATIVE_DEVIATION_LESS_OR_EQUAL:
        return evaluateFieldComparison(fieldComparisons);
      default:
        return evaluateSignal(signalStats);
    }
  }

  /**
   * Converts this criterion to a JSON-ready map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("id", id);
    map.put("type", type.name());
    map.put("logicalTag", logicalTag);
    map.put("logicName", logicName);
    map.put("targetValue", targetValue);
    map.put("unit", unit);
    map.put("clause", clause);
    map.put("severity", severity);
    map.put("description", description);
    map.put("recommendation", recommendation);
    return map;
  }

  /**
   * Creates a signal criterion.
   *
   * @param id criterion identifier
   * @param type criterion type
   * @param logicalTag monitored tag
   * @param targetValue target value
   * @param unit engineering unit
   * @param description criterion description
   * @return acceptance criterion
   */
  private static EmergencyShutdownTestCriterion signal(String id, CriterionType type,
      String logicalTag, double targetValue, String unit, String description) {
    return new EmergencyShutdownTestCriterion(id, type, logicalTag, "", targetValue, unit, "",
        "HIGH", description,
        "Review ESD action sequence, final element response, and model input data.");
  }

  /**
   * Evaluates a monitored signal criterion.
   *
   * @param signalStats monitored signal statistics
   * @return criterion result
   */
  private Result evaluateSignal(Map<String, EmergencyShutdownTestResult.SignalStats> signalStats) {
    EmergencyShutdownTestResult.SignalStats stats = signalStats.get(logicalTag);
    if (stats == null || !stats.hasSamples()) {
      return new Result(this, false, Double.NaN, targetValue,
          "No monitored samples were found for " + logicalTag + ".");
    }
    double value = valueFor(stats);
    boolean passed = compare(value);
    return new Result(this, passed, value, targetValue,
        passed ? "Criterion satisfied." : "Criterion failed for " + logicalTag + ".");
  }

  /**
   * Evaluates a logic completion criterion.
   *
   * @param logicStates logic states keyed by name
   * @return criterion result
   */
  private Result evaluateLogic(Map<String, String> logicStates) {
    String state = logicStates.get(logicName);
    boolean passed = "COMPLETED".equals(state);
    return new Result(this, passed, passed ? 1.0 : 0.0, 1.0,
        state == null ? "Logic sequence was not present in the test."
            : "Final logic state: " + state);
  }

  /**
   * Evaluates a field comparison criterion.
   *
   * @param fieldComparisons field comparisons keyed by logical tag
   * @return criterion result
   */
  private Result evaluateFieldComparison(
      Map<String, EmergencyShutdownTestResult.FieldComparison> fieldComparisons) {
    EmergencyShutdownTestResult.FieldComparison comparison = fieldComparisons.get(logicalTag);
    if (comparison == null || !comparison.hasBothValues()) {
      return new Result(this, false, Double.NaN, targetValue,
          "No complete model-to-field comparison was available for " + logicalTag + ".");
    }
    double value = type == CriterionType.FIELD_ABSOLUTE_DEVIATION_LESS_OR_EQUAL
        ? comparison.getAbsoluteDeviation()
        : comparison.getRelativeDeviationFraction();
    boolean passed = value <= targetValue;
    return new Result(this, passed, value, targetValue,
        passed ? "Model-to-field deviation is within tolerance."
            : "Model-to-field deviation exceeds tolerance.");
  }

  /**
   * Selects the signal statistic used by this criterion.
   *
   * @param stats signal statistics
   * @return statistic value
   */
  private double valueFor(EmergencyShutdownTestResult.SignalStats stats) {
    switch (type) {
      case FINAL_LESS_OR_EQUAL:
      case FINAL_GREATER_OR_EQUAL:
        return stats.getFinalValue();
      case MAX_LESS_OR_EQUAL:
      case MAX_GREATER_OR_EQUAL:
        return stats.getMaxValue();
      case MIN_LESS_OR_EQUAL:
      case MIN_GREATER_OR_EQUAL:
        return stats.getMinValue();
      case DECREASE_GREATER_OR_EQUAL:
        return stats.getInitialValue() - stats.getFinalValue();
      case INCREASE_GREATER_OR_EQUAL:
        return stats.getFinalValue() - stats.getInitialValue();
      default:
        return Double.NaN;
    }
  }

  /**
   * Compares a statistic with the target value.
   *
   * @param value statistic value
   * @return true if the criterion passes
   */
  private boolean compare(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return false;
    }
    switch (type) {
      case FINAL_LESS_OR_EQUAL:
      case MAX_LESS_OR_EQUAL:
      case MIN_LESS_OR_EQUAL:
        return value <= targetValue;
      case FINAL_GREATER_OR_EQUAL:
      case MAX_GREATER_OR_EQUAL:
      case MIN_GREATER_OR_EQUAL:
      case DECREASE_GREATER_OR_EQUAL:
      case INCREASE_GREATER_OR_EQUAL:
        return value >= targetValue;
      default:
        return false;
    }
  }

  /**
   * Cleans nullable text.
   *
   * @param text text value
   * @return trimmed text or empty string
   */
  private static String clean(String text) {
    return text == null ? "" : text.trim();
  }

  /**
   * Requires a non-empty text value.
   *
   * @param text text value
   * @param fieldName field name for error messages
   * @return trimmed text
   */
  private static String requireText(String text, String fieldName) {
    String value = clean(text);
    if (value.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be empty");
    }
    return value;
  }

  /** Result from evaluating one ESD acceptance criterion. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String criterionId;
    private final CriterionType type;
    private final boolean passed;
    private final double observedValue;
    private final double targetValue;
    private final String unit;
    private final String clause;
    private final String severity;
    private final String message;
    private final String recommendation;

    /**
     * Creates a criterion result.
     *
     * @param criterion source criterion
     * @param passed true if the criterion passed
     * @param observedValue observed value used by the criterion
     * @param targetValue target value used by the criterion
     * @param message result message
     */
    private Result(EmergencyShutdownTestCriterion criterion, boolean passed, double observedValue,
        double targetValue, String message) {
      this.criterionId = criterion.id;
      this.type = criterion.type;
      this.passed = passed;
      this.observedValue = observedValue;
      this.targetValue = targetValue;
      this.unit = criterion.unit;
      this.clause = criterion.clause;
      this.severity = criterion.severity;
      this.message = clean(message);
      this.recommendation = criterion.recommendation;
    }

    /**
     * Checks whether the criterion passed.
     *
     * @return true if passed
     */
    public boolean isPassed() {
      return passed;
    }

    /**
     * Gets the criterion identifier.
     *
     * @return criterion identifier
     */
    public String getCriterionId() {
      return criterionId;
    }

    /**
     * Converts the result to a JSON-ready map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("criterionId", criterionId);
      map.put("type", type.name());
      map.put("passed", passed);
      map.put("observedValue", jsonNumber(observedValue));
      map.put("targetValue", jsonNumber(targetValue));
      map.put("unit", unit);
      map.put("clause", clause);
      map.put("severity", severity);
      map.put("message", message);
      map.put("recommendation", recommendation);
      return map;
    }

    /**
     * Converts finite doubles to JSON numbers and non-finite doubles to null.
     *
     * @param value numeric value
     * @return boxed finite value or null
     */
    private static Double jsonNumber(double value) {
      return Double.isNaN(value) || Double.isInfinite(value) ? null : Double.valueOf(value);
    }
  }
}
