package neqsim.process.safety.esd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured report from an emergency shutdown dynamic test.
 *
 * <p>
 * The report is JSON-friendly and intended for notebooks, MCP runners, and standards evidence
 * packages. It records signal statistics, time-series samples, tagreader comparisons, logic states,
 * acceptance criteria, document references, and quality gates.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class EmergencyShutdownTestResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().create();

  /** Overall verdict for the ESD dynamic test. */
  public enum Verdict {
    /** All criteria passed and no warnings remain. */
    PASS,
    /** No criterion failed, but warnings or evidence gaps remain. */
    PASS_WITH_WARNINGS,
    /** One or more criteria failed or simulation errors occurred. */
    FAIL
  }

  private final String testName;
  private final double durationSeconds;
  private final double timeStepSeconds;
  private final double triggerTimeSeconds;
  private final List<String> evidenceReferences = new ArrayList<String>();
  private final List<String> standardReferences = new ArrayList<String>();
  private final List<String> warnings = new ArrayList<String>();
  private final List<String> errors = new ArrayList<String>();
  private final List<SignalSample> timeSeries = new ArrayList<SignalSample>();
  private final Map<String, SignalStats> signalStats = new LinkedHashMap<String, SignalStats>();
  private final Map<String, String> logicStates = new LinkedHashMap<String, String>();
  private final Map<String, FieldComparison> fieldComparisons =
      new LinkedHashMap<String, FieldComparison>();
  private final List<EmergencyShutdownTestCriterion.Result> criterionResults =
      new ArrayList<EmergencyShutdownTestCriterion.Result>();
  private Verdict verdict = Verdict.PASS_WITH_WARNINGS;

  /**
   * Creates a report shell.
   *
   * @param plan source test plan
   */
  EmergencyShutdownTestResult(EmergencyShutdownTestPlan plan) {
    testName = plan.getName();
    durationSeconds = plan.getDurationSeconds();
    timeStepSeconds = plan.getTimeStepSeconds();
    triggerTimeSeconds = plan.getTriggerTimeSeconds();
    evidenceReferences.addAll(plan.getEvidenceReferences());
    standardReferences.addAll(plan.getStandardReferences());
  }

  /**
   * Gets the test name.
   *
   * @return test name
   */
  public String getTestName() {
    return testName;
  }

  /**
   * Gets the overall verdict.
   *
   * @return test verdict
   */
  public Verdict getVerdict() {
    return verdict;
  }

  /**
   * Gets recorded warnings.
   *
   * @return immutable warning list
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Gets recorded errors.
   *
   * @return immutable error list
   */
  public List<String> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  /**
   * Gets monitored signal statistics.
   *
   * @return immutable signal-statistics map
   */
  public Map<String, SignalStats> getSignalStats() {
    return Collections.unmodifiableMap(signalStats);
  }

  /**
   * Gets final process logic states.
   *
   * @return immutable logic-state map
   */
  public Map<String, String> getLogicStates() {
    return Collections.unmodifiableMap(logicStates);
  }

  /**
   * Gets model-to-field comparisons.
   *
   * @return immutable comparison map
   */
  public Map<String, FieldComparison> getFieldComparisons() {
    return Collections.unmodifiableMap(fieldComparisons);
  }

  /**
   * Gets acceptance criterion results.
   *
   * @return immutable criterion-result list
   */
  public List<EmergencyShutdownTestCriterion.Result> getCriterionResults() {
    return Collections.unmodifiableList(criterionResults);
  }

  /**
   * Gets time-series signal samples.
   *
   * @return immutable sample list
   */
  public List<SignalSample> getTimeSeries() {
    return Collections.unmodifiableList(timeSeries);
  }

  /**
   * Adds a warning message.
   *
   * @param warning warning text
   */
  void addWarning(String warning) {
    if (warning != null && !warning.trim().isEmpty()) {
      warnings.add(warning.trim());
    }
  }

  /**
   * Adds an error message.
   *
   * @param error error text
   */
  void addError(String error) {
    if (error != null && !error.trim().isEmpty()) {
      errors.add(error.trim());
    }
  }

  /**
   * Adds a sampled point and updates signal statistics.
   *
   * @param sample sampled signal values
   * @param units units keyed by logical tag
   */
  void addSample(SignalSample sample, Map<String, String> units) {
    if (sample == null) {
      return;
    }
    timeSeries.add(sample);
    for (Map.Entry<String, Double> entry : sample.getValues().entrySet()) {
      String tag = entry.getKey();
      Double value = entry.getValue();
      if (value == null) {
        continue;
      }
      SignalStats stats = signalStats.get(tag);
      if (stats == null) {
        stats = new SignalStats(tag, units == null ? "" : units.get(tag));
        signalStats.put(tag, stats);
      }
      stats.record(value.doubleValue());
    }
  }

  /**
   * Records the final state of one process logic sequence.
   *
   * @param logicName logic name
   * @param state state name
   */
  void putLogicState(String logicName, String state) {
    if (logicName != null && !logicName.trim().isEmpty()) {
      logicStates.put(logicName.trim(), state == null ? "UNKNOWN" : state.trim());
    }
  }

  /**
   * Adds a model-to-field comparison.
   *
   * @param comparison comparison result
   */
  void addFieldComparison(FieldComparison comparison) {
    if (comparison != null) {
      fieldComparisons.put(comparison.getLogicalTag(), comparison);
    }
  }

  /**
   * Adds an acceptance criterion result.
   *
   * @param result criterion result
   */
  void addCriterionResult(EmergencyShutdownTestCriterion.Result result) {
    if (result != null) {
      criterionResults.add(result);
    }
  }

  /**
   * Finalizes the verdict after all results have been added.
   */
  void finalizeVerdict() {
    boolean hasFailedCriterion = false;
    for (EmergencyShutdownTestCriterion.Result result : criterionResults) {
      hasFailedCriterion = hasFailedCriterion || !result.isPassed();
    }
    if (!errors.isEmpty() || hasFailedCriterion) {
      verdict = Verdict.FAIL;
    } else if (!warnings.isEmpty() || criterionResults.isEmpty()) {
      verdict = Verdict.PASS_WITH_WARNINGS;
    } else {
      verdict = Verdict.PASS;
    }
  }

  /**
   * Converts this report to a JSON-ready map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("testName", testName);
    map.put("verdict", verdict.name());
    map.put("durationSeconds", durationSeconds);
    map.put("timeStepSeconds", timeStepSeconds);
    map.put("triggerTimeSeconds", triggerTimeSeconds);
    map.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    map.put("standardReferences", new ArrayList<String>(standardReferences));
    map.put("warnings", new ArrayList<String>(warnings));
    map.put("errors", new ArrayList<String>(errors));

    Map<String, Object> statsMap = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, SignalStats> entry : signalStats.entrySet()) {
      statsMap.put(entry.getKey(), entry.getValue().toMap());
    }
    map.put("signalStats", statsMap);

    List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
    for (SignalSample sample : timeSeries) {
      samples.add(sample.toMap());
    }
    map.put("timeSeries", samples);

    map.put("logicStates", new LinkedHashMap<String, String>(logicStates));

    Map<String, Object> comparisons = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, FieldComparison> entry : fieldComparisons.entrySet()) {
      comparisons.put(entry.getKey(), entry.getValue().toMap());
    }
    map.put("fieldComparisons", comparisons);

    List<Map<String, Object>> criteria = new ArrayList<Map<String, Object>>();
    for (EmergencyShutdownTestCriterion.Result result : criterionResults) {
      criteria.add(result.toMap());
    }
    map.put("criterionResults", criteria);
    return map;
  }

  /**
   * Converts this report to pretty JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return GSON.toJson(toMap());
  }

  /** Monitored signal statistics over the ESD transient. */
  public static final class SignalStats implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String logicalTag;
    private final String unit;
    private int sampleCount;
    private double initialValue = Double.NaN;
    private double finalValue = Double.NaN;
    private double minValue = Double.NaN;
    private double maxValue = Double.NaN;

    /**
     * Creates empty signal statistics.
     *
     * @param logicalTag logical tag
     * @param unit engineering unit
     */
    private SignalStats(String logicalTag, String unit) {
      this.logicalTag = logicalTag;
      this.unit = unit == null ? "" : unit;
    }

    /**
     * Records one monitored value.
     *
     * @param value monitored value
     */
    private void record(double value) {
      if (Double.isNaN(value) || Double.isInfinite(value)) {
        return;
      }
      if (sampleCount == 0) {
        initialValue = value;
        minValue = value;
        maxValue = value;
      }
      finalValue = value;
      minValue = Math.min(minValue, value);
      maxValue = Math.max(maxValue, value);
      sampleCount++;
    }

    /**
     * Checks whether at least one sample was recorded.
     *
     * @return true when samples exist
     */
    public boolean hasSamples() {
      return sampleCount > 0;
    }

    /**
     * Gets the initial value.
     *
     * @return initial value
     */
    public double getInitialValue() {
      return initialValue;
    }

    /**
     * Gets the final value.
     *
     * @return final value
     */
    public double getFinalValue() {
      return finalValue;
    }

    /**
     * Gets the minimum value.
     *
     * @return minimum value
     */
    public double getMinValue() {
      return minValue;
    }

    /**
     * Gets the maximum value.
     *
     * @return maximum value
     */
    public double getMaxValue() {
      return maxValue;
    }

    /**
     * Converts the statistics to a JSON-ready map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("logicalTag", logicalTag);
      map.put("unit", unit);
      map.put("sampleCount", sampleCount);
      map.put("initialValue", jsonNumber(initialValue));
      map.put("finalValue", jsonNumber(finalValue));
      map.put("minValue", jsonNumber(minValue));
      map.put("maxValue", jsonNumber(maxValue));
      return map;
    }
  }

  /** One time-series sample of monitored ESD signals. */
  public static final class SignalSample implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double timeSeconds;
    private final Map<String, Double> values;

    /**
     * Creates a signal sample.
     *
     * @param timeSeconds elapsed time in seconds
     * @param values signal values keyed by logical tag
     */
    SignalSample(double timeSeconds, Map<String, Double> values) {
      this.timeSeconds = timeSeconds;
      this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(values));
    }

    /**
     * Gets the elapsed time.
     *
     * @return time in seconds
     */
    public double getTimeSeconds() {
      return timeSeconds;
    }

    /**
     * Gets sampled values.
     *
     * @return immutable value map
     */
    public Map<String, Double> getValues() {
      return values;
    }

    /**
     * Converts the sample to a JSON-ready map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("timeSeconds", timeSeconds);
      map.put("values", new LinkedHashMap<String, Double>(values));
      return map;
    }
  }

  /** Comparison between a field-data value and the final model value. */
  public static final class FieldComparison implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String logicalTag;
    private final String historianTag;
    private final String unit;
    private final Double fieldValue;
    private final Double modelValue;
    private final double signedDeviation;
    private final double absoluteDeviation;
    private final double relativeDeviationFraction;
    private final boolean withinTolerance;

    /**
     * Creates a field comparison.
     *
     * @param logicalTag logical tag
     * @param historianTag historian tag or empty string
     * @param unit engineering unit
     * @param fieldValue field value, or null
     * @param modelValue model value, or null
     * @param toleranceFraction relative tolerance fraction
     */
    FieldComparison(String logicalTag, String historianTag, String unit, Double fieldValue,
        Double modelValue, double toleranceFraction) {
      this.logicalTag = logicalTag == null ? "" : logicalTag;
      this.historianTag = historianTag == null ? "" : historianTag;
      this.unit = unit == null ? "" : unit;
      this.fieldValue = fieldValue;
      this.modelValue = modelValue;
      boolean hasBoth = fieldValue != null && modelValue != null;
      signedDeviation = hasBoth ? modelValue.doubleValue() - fieldValue.doubleValue() : Double.NaN;
      absoluteDeviation = hasBoth ? Math.abs(signedDeviation) : Double.NaN;
      double denominator = hasBoth ? Math.max(Math.abs(fieldValue.doubleValue()), 1.0) : 1.0;
      relativeDeviationFraction = hasBoth ? Math.abs(absoluteDeviation) / denominator : Double.NaN;
      withinTolerance = hasBoth && relativeDeviationFraction <= Math.max(0.0, toleranceFraction);
    }

    /**
     * Gets the logical tag.
     *
     * @return logical tag
     */
    public String getLogicalTag() {
      return logicalTag;
    }

    /**
     * Checks whether both model and field values are available.
     *
     * @return true when both values are present
     */
    public boolean hasBothValues() {
      return fieldValue != null && modelValue != null;
    }

    /**
     * Gets the absolute deviation.
     *
     * @return model value minus field value
     */
    public double getAbsoluteDeviation() {
      return absoluteDeviation;
    }

    /**
     * Gets the signed model-to-field deviation.
     *
     * @return model value minus field value
     */
    public double getSignedDeviation() {
      return signedDeviation;
    }

    /**
     * Gets the relative deviation fraction.
     *
     * @return absolute deviation divided by field magnitude
     */
    public double getRelativeDeviationFraction() {
      return relativeDeviationFraction;
    }

    /**
     * Converts the comparison to a JSON-ready map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("logicalTag", logicalTag);
      map.put("historianTag", historianTag);
      map.put("unit", unit);
      map.put("fieldValue", fieldValue);
      map.put("modelValue", modelValue);
      map.put("signedDeviation", jsonNumber(signedDeviation));
      map.put("absoluteDeviation", jsonNumber(absoluteDeviation));
      map.put("relativeDeviationFraction", jsonNumber(relativeDeviationFraction));
      map.put("withinTolerance", withinTolerance);
      return map;
    }
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
