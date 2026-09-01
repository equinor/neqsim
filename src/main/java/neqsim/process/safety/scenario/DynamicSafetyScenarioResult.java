package neqsim.process.safety.scenario;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable time-series evidence and verdict from one dynamic protection scenario. */
public final class DynamicSafetyScenarioResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Time history and deadline verdict for one response criterion. */
  public static final class CriterionResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final DynamicScenarioCriterion criterion;
    private final List<Double> timeSeconds = new ArrayList<Double>();
    private final List<Double> values = new ArrayList<Double>();
    private Double firstSatisfiedSeconds;

    CriterionResult(DynamicScenarioCriterion criterion) {
      this.criterion = criterion;
    }

    void sample(double relativeTimeSeconds, double value) {
      timeSeconds.add(Double.valueOf(relativeTimeSeconds));
      values.add(Double.valueOf(value));
      if (firstSatisfiedSeconds == null && relativeTimeSeconds >= 0.0 && criterion.isSatisfied(value)) {
        firstSatisfiedSeconds = Double.valueOf(relativeTimeSeconds);
      }
    }

    public boolean isPassed() {
      return firstSatisfiedSeconds != null
          && firstSatisfiedSeconds.doubleValue() <= criterion.getDeadlineSeconds() + 1.0e-9;
    }

    /** @return first time the criterion was satisfied, or null when it was never satisfied */
    public Double getFirstSatisfiedSeconds() {
      return firstSatisfiedSeconds;
    }

    DynamicScenarioCriterion getCriterion() {
      return criterion;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("criterion", criterion.toMap());
      result.put("timeSeconds", new ArrayList<Double>(timeSeconds));
      result.put("values", new ArrayList<Double>(values));
      result.put("firstSatisfiedSeconds", firstSatisfiedSeconds);
      result.put("passed", Boolean.valueOf(isPassed()));
      return result;
    }
  }

  private final String scenarioId;
  private final String scenarioName;
  private final Map<String, CriterionResult> criterionResults;
  private final Map<String, String> finalLogicStates;
  private final Map<String, Map<String, Object>> logicEvidence;
  private final List<String> errors;
  private final boolean steadyStateConverged;

  DynamicSafetyScenarioResult(String scenarioId, String scenarioName, Map<String, CriterionResult> criterionResults,
      Map<String, String> finalLogicStates, Map<String, Map<String, Object>> logicEvidence, List<String> errors,
      boolean steadyStateConverged) {
    this.scenarioId = scenarioId;
    this.scenarioName = scenarioName;
    this.criterionResults = Collections.unmodifiableMap(new LinkedHashMap<String, CriterionResult>(criterionResults));
    this.finalLogicStates = Collections.unmodifiableMap(new LinkedHashMap<String, String>(finalLogicStates));
    this.logicEvidence = Collections.unmodifiableMap(new LinkedHashMap<String, Map<String, Object>>(logicEvidence));
    this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
    this.steadyStateConverged = steadyStateConverged;
  }

  public boolean isPassed() {
    if (!errors.isEmpty() || !steadyStateConverged) {
      return false;
    }
    for (CriterionResult result : criterionResults.values()) {
      if (result.criterion.isRequired() && !result.isPassed()) {
        return false;
      }
    }
    return true;
  }

  /** @return controlled scenario identifier */
  public String getScenarioId() {
    return scenarioId;
  }

  public Map<String, CriterionResult> getCriterionResults() {
    return criterionResults;
  }

  /** @return structured evidence keyed by protection-logic name */
  public Map<String, Map<String, Object>> getLogicEvidence() {
    return logicEvidence;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "dynamic_safety_scenario_result.v2");
    result.put("scenarioId", scenarioId);
    result.put("scenarioName", scenarioName);
    result.put("steadyStateConverged", Boolean.valueOf(steadyStateConverged));
    Map<String, Object> criteria = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, CriterionResult> entry : criterionResults.entrySet()) {
      criteria.put(entry.getKey(), entry.getValue().toMap());
    }
    result.put("criteria", criteria);
    result.put("finalLogicStates", new LinkedHashMap<String, String>(finalLogicStates));
    result.put("logicEvidence", new LinkedHashMap<String, Map<String, Object>>(logicEvidence));
    result.put("errors", new ArrayList<String>(errors));
    result.put("passed", Boolean.valueOf(isPassed()));
    result.put("silTargetInferred", Boolean.FALSE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }
}
