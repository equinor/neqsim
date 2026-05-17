package neqsim.process.safety.esd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.operations.OperationalTagBinding;
import neqsim.process.operations.OperationalTagMap;
import neqsim.process.safety.ProcessSafetyScenario;

/**
 * Configuration for one emergency shutdown dynamic test.
 *
 * <p>
 * A plan combines the process disturbance, enabled and triggered logic sequences, monitored
 * tagreader or automation signals, document references, and acceptance criteria. It is
 * intentionally plant-agnostic so public NeqSim models can use logical tags while private historian
 * tag names stay in the {@link OperationalTagMap} supplied by the caller.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class EmergencyShutdownTestPlan implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final ProcessSafetyScenario scenario;
  private final double durationSeconds;
  private final double timeStepSeconds;
  private final double triggerTimeSeconds;
  private final boolean initializeSteadyState;
  private final OperationalTagMap tagMap;
  private final Map<String, Double> fieldData;
  private final List<EmergencyShutdownTestCriterion> criteria;
  private final List<String> enabledLogicNames;
  private final List<String> triggerLogicNames;
  private final Set<String> monitoredLogicalTags;
  private final Map<String, String> monitoredUnits;
  private final List<String> evidenceReferences;
  private final List<String> standardReferences;
  private final double defaultFieldComparisonToleranceFraction;

  /**
   * Creates a test plan from a builder.
   *
   * @param builder populated builder
   */
  private EmergencyShutdownTestPlan(Builder builder) {
    name = requireText(builder.name, "name");
    scenario = builder.scenario;
    durationSeconds = requirePositive(builder.durationSeconds, "durationSeconds");
    timeStepSeconds = requirePositive(builder.timeStepSeconds, "timeStepSeconds");
    triggerTimeSeconds = Math.max(0.0, builder.triggerTimeSeconds);
    initializeSteadyState = builder.initializeSteadyState;
    tagMap = builder.tagMap == null ? new OperationalTagMap() : builder.tagMap;
    fieldData = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(builder.fieldData));
    criteria = Collections
        .unmodifiableList(new ArrayList<EmergencyShutdownTestCriterion>(builder.criteria));
    enabledLogicNames =
        Collections.unmodifiableList(new ArrayList<String>(builder.enabledLogicNames));
    triggerLogicNames =
        Collections.unmodifiableList(new ArrayList<String>(builder.triggerLogicNames));
    monitoredLogicalTags = Collections.unmodifiableSet(collectMonitoredTags(builder));
    monitoredUnits = Collections.unmodifiableMap(collectMonitorUnits(builder));
    evidenceReferences =
        Collections.unmodifiableList(new ArrayList<String>(builder.evidenceReferences));
    standardReferences =
        Collections.unmodifiableList(new ArrayList<String>(builder.standardReferences));
    defaultFieldComparisonToleranceFraction =
        Math.max(0.0, builder.defaultFieldComparisonToleranceFraction);
  }

  /**
   * Starts a plan builder.
   *
   * @param name plan name
   * @return builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Gets the test name.
   *
   * @return test name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the process safety scenario applied before the transient test.
   *
   * @return process safety scenario, or null
   */
  public ProcessSafetyScenario getScenario() {
    return scenario;
  }

  /**
   * Gets the simulation duration.
   *
   * @return duration in seconds
   */
  public double getDurationSeconds() {
    return durationSeconds;
  }

  /**
   * Gets the simulation time step.
   *
   * @return time step in seconds
   */
  public double getTimeStepSeconds() {
    return timeStepSeconds;
  }

  /**
   * Gets the time at which trigger logic is activated.
   *
   * @return trigger time in seconds
   */
  public double getTriggerTimeSeconds() {
    return triggerTimeSeconds;
  }

  /**
   * Checks whether the process should be run to steady state before testing.
   *
   * @return true if steady-state initialization is enabled
   */
  public boolean isInitializeSteadyState() {
    return initializeSteadyState;
  }

  /**
   * Gets the operational tag map.
   *
   * @return operational tag map
   */
  public OperationalTagMap getTagMap() {
    return tagMap;
  }

  /**
   * Gets tagreader or field-data values keyed by logical or historian tag.
   *
   * @return immutable field-data map
   */
  public Map<String, Double> getFieldData() {
    return fieldData;
  }

  /**
   * Gets acceptance criteria.
   *
   * @return immutable criterion list
   */
  public List<EmergencyShutdownTestCriterion> getCriteria() {
    return criteria;
  }

  /**
   * Gets logic names that should be executed during the transient.
   *
   * @return immutable logic-name list, or empty to execute all supplied logic
   */
  public List<String> getEnabledLogicNames() {
    return enabledLogicNames;
  }

  /**
   * Gets logic names that should be activated at trigger time.
   *
   * @return immutable trigger logic list, or empty to trigger enabled logic
   */
  public List<String> getTriggerLogicNames() {
    return triggerLogicNames;
  }

  /**
   * Gets monitored logical tags and direct automation addresses.
   *
   * @return immutable monitor set
   */
  public Set<String> getMonitoredLogicalTags() {
    return monitoredLogicalTags;
  }

  /**
   * Gets monitor units keyed by logical tag.
   *
   * @return immutable unit map
   */
  public Map<String, String> getMonitoredUnits() {
    return monitoredUnits;
  }

  /**
   * Gets document and evidence references.
   *
   * @return immutable evidence reference list
   */
  public List<String> getEvidenceReferences() {
    return evidenceReferences;
  }

  /**
   * Gets standards and clause references.
   *
   * @return immutable standards reference list
   */
  public List<String> getStandardReferences() {
    return standardReferences;
  }

  /**
   * Gets the default relative tolerance for field comparisons.
   *
   * @return tolerance fraction
   */
  public double getDefaultFieldComparisonToleranceFraction() {
    return defaultFieldComparisonToleranceFraction;
  }

  /**
   * Collects all monitored tags from explicit monitors, criteria, and tag bindings.
   *
   * @param builder source builder
   * @return monitored tag set
   */
  private static Set<String> collectMonitoredTags(Builder builder) {
    Set<String> tags = new LinkedHashSet<String>(builder.monitoredLogicalTags);
    if (builder.tagMap != null) {
      for (OperationalTagBinding binding : builder.tagMap.getBindings()) {
        tags.add(binding.getLogicalTag());
      }
    }
    for (EmergencyShutdownTestCriterion criterion : builder.criteria) {
      if (!criterion.getLogicalTag().isEmpty()) {
        tags.add(criterion.getLogicalTag());
      }
    }
    return tags;
  }

  /**
   * Collects monitor units from tag bindings and criteria.
   *
   * @param builder source builder
   * @return unit map
   */
  private static Map<String, String> collectMonitorUnits(Builder builder) {
    Map<String, String> units = new LinkedHashMap<String, String>(builder.monitoredUnits);
    if (builder.tagMap != null) {
      for (OperationalTagBinding binding : builder.tagMap.getBindings()) {
        if (!binding.getUnit().isEmpty()) {
          units.put(binding.getLogicalTag(), binding.getUnit());
        }
      }
    }
    for (EmergencyShutdownTestCriterion criterion : builder.criteria) {
      if (!criterion.getLogicalTag().isEmpty() && !criterion.getUnit().isEmpty()) {
        units.put(criterion.getLogicalTag(), criterion.getUnit());
      }
    }
    return units;
  }

  /**
   * Requires a positive numeric value.
   *
   * @param value value to validate
   * @param fieldName field name for error messages
   * @return validated value
   */
  private static double requirePositive(double value, String fieldName) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return value;
  }

  /**
   * Requires a non-empty text value.
   *
   * @param text text value
   * @param fieldName field name for error messages
   * @return trimmed text
   */
  private static String requireText(String text, String fieldName) {
    String value = text == null ? "" : text.trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be empty");
    }
    return value;
  }

  /** Builder for {@link EmergencyShutdownTestPlan}. */
  public static final class Builder {
    private final String name;
    private ProcessSafetyScenario scenario;
    private double durationSeconds = 60.0;
    private double timeStepSeconds = 1.0;
    private double triggerTimeSeconds = 0.0;
    private boolean initializeSteadyState = true;
    private OperationalTagMap tagMap = new OperationalTagMap();
    private final Map<String, Double> fieldData = new LinkedHashMap<String, Double>();
    private final List<EmergencyShutdownTestCriterion> criteria =
        new ArrayList<EmergencyShutdownTestCriterion>();
    private final List<String> enabledLogicNames = new ArrayList<String>();
    private final List<String> triggerLogicNames = new ArrayList<String>();
    private final Set<String> monitoredLogicalTags = new LinkedHashSet<String>();
    private final Map<String, String> monitoredUnits = new LinkedHashMap<String, String>();
    private final List<String> evidenceReferences = new ArrayList<String>();
    private final List<String> standardReferences = new ArrayList<String>();
    private double defaultFieldComparisonToleranceFraction = 0.05;

    /**
     * Creates a builder.
     *
     * @param name plan name
     */
    private Builder(String name) {
      this.name = name;
    }

    /**
     * Sets the process safety scenario applied before the transient run.
     *
     * @param scenario process safety scenario
     * @return this builder
     */
    public Builder scenario(ProcessSafetyScenario scenario) {
      this.scenario = scenario;
      return this;
    }

    /**
     * Sets the transient duration.
     *
     * @param durationSeconds duration in seconds
     * @return this builder
     */
    public Builder duration(double durationSeconds) {
      this.durationSeconds = durationSeconds;
      return this;
    }

    /**
     * Sets the transient time step.
     *
     * @param timeStepSeconds time step in seconds
     * @return this builder
     */
    public Builder timeStep(double timeStepSeconds) {
      this.timeStepSeconds = timeStepSeconds;
      return this;
    }

    /**
     * Sets when trigger logic is activated.
     *
     * @param triggerTimeSeconds trigger time in seconds
     * @return this builder
     */
    public Builder triggerTime(double triggerTimeSeconds) {
      this.triggerTimeSeconds = triggerTimeSeconds;
      return this;
    }

    /**
     * Sets whether to initialize the process to steady state before testing.
     *
     * @param initializeSteadyState true to run steady state first
     * @return this builder
     */
    public Builder initializeSteadyState(boolean initializeSteadyState) {
      this.initializeSteadyState = initializeSteadyState;
      return this;
    }

    /**
     * Sets the operational tag map.
     *
     * @param tagMap operational tag map
     * @return this builder
     */
    public Builder tagMap(OperationalTagMap tagMap) {
      this.tagMap = tagMap;
      return this;
    }

    /**
     * Adds field data from tagreader or a saved historian snapshot.
     *
     * @param tag logical or historian tag
     * @param value field value
     * @return this builder
     */
    public Builder fieldData(String tag, double value) {
      if (tag != null && !tag.trim().isEmpty()) {
        fieldData.put(tag.trim(), value);
      }
      return this;
    }

    /**
     * Adds field data from a map.
     *
     * @param values field values keyed by logical or historian tag
     * @return this builder
     */
    public Builder fieldData(Map<String, Double> values) {
      if (values != null) {
        for (Map.Entry<String, Double> entry : values.entrySet()) {
          if (entry.getValue() != null) {
            fieldData(entry.getKey(), entry.getValue().doubleValue());
          }
        }
      }
      return this;
    }

    /**
     * Adds an acceptance criterion.
     *
     * @param criterion criterion to add
     * @return this builder
     */
    public Builder criterion(EmergencyShutdownTestCriterion criterion) {
      if (criterion != null) {
        criteria.add(criterion);
      }
      return this;
    }

    /**
     * Enables a named logic sequence during the test.
     *
     * @param logicName process logic name
     * @return this builder
     */
    public Builder enableLogic(String logicName) {
      addText(enabledLogicNames, logicName);
      return this;
    }

    /**
     * Activates a named logic sequence at trigger time.
     *
     * @param logicName process logic name
     * @return this builder
     */
    public Builder triggerLogic(String logicName) {
      addText(triggerLogicNames, logicName);
      return this;
    }

    /**
     * Adds a monitored logical tag or direct automation address.
     *
     * @param logicalTag logical tag or automation address
     * @param unit engineering unit
     * @return this builder
     */
    public Builder monitor(String logicalTag, String unit) {
      if (logicalTag != null && !logicalTag.trim().isEmpty()) {
        String key = logicalTag.trim();
        monitoredLogicalTags.add(key);
        if (unit != null && !unit.trim().isEmpty()) {
          monitoredUnits.put(key, unit.trim());
        }
      }
      return this;
    }

    /**
     * Adds a document or evidence reference.
     *
     * @param evidenceReference reference text
     * @return this builder
     */
    public Builder evidenceReference(String evidenceReference) {
      addText(evidenceReferences, evidenceReference);
      return this;
    }

    /**
     * Adds a standards or clause reference.
     *
     * @param standardReference reference text
     * @return this builder
     */
    public Builder standardReference(String standardReference) {
      addText(standardReferences, standardReference);
      return this;
    }

    /**
     * Sets the default relative tolerance used for field comparisons.
     *
     * @param toleranceFraction tolerance fraction
     * @return this builder
     */
    public Builder defaultFieldComparisonTolerance(double toleranceFraction) {
      this.defaultFieldComparisonToleranceFraction = toleranceFraction;
      return this;
    }

    /**
     * Builds the immutable test plan.
     *
     * @return emergency shutdown test plan
     */
    public EmergencyShutdownTestPlan build() {
      return new EmergencyShutdownTestPlan(this);
    }

    /**
     * Adds non-empty text to a list.
     *
     * @param target target list
     * @param value text value
     */
    private static void addText(List<String> target, String value) {
      if (value != null && !value.trim().isEmpty()) {
        target.add(value.trim());
      }
    }
  }
}
