package neqsim.process.engineering.designcase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.model.EngineeringCalculation;

/** Governing values and cases selected from an executed design-case set. */
public final class EngineeringDesignEnvelope implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One governing metric value with its source design case. */
  public static final class GoverningValue implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final EngineeringMetric metric;
    private final String designCaseId;
    private final String designCaseName;
    private final double value;

    GoverningValue(EngineeringMetric metric, String designCaseId, String designCaseName, double value) {
      this.metric = metric;
      this.designCaseId = designCaseId;
      this.designCaseName = designCaseName;
      this.value = value;
    }

    public EngineeringMetric getMetric() {
      return metric;
    }

    public String getDesignCaseId() {
      return designCaseId;
    }

    public double getValue() {
      return value;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("metric", metric.toMap());
      result.put("designCaseId", designCaseId);
      result.put("designCaseName", designCaseName);
      result.put("value", value);
      result.put("unit", metric.getUnit());
      result.put("limitStatus", metric.assess(value));
      if (metric.getLowerAcceptanceLimit() != null || metric.getUpperAcceptanceLimit() != null) {
        double margin = Double.POSITIVE_INFINITY;
        if (metric.getLowerAcceptanceLimit() != null) {
          margin = Math.min(margin, value - metric.getLowerAcceptanceLimit().doubleValue());
        }
        if (metric.getUpperAcceptanceLimit() != null) {
          margin = Math.min(margin, metric.getUpperAcceptanceLimit().doubleValue() - value);
        }
        result.put("acceptanceMargin", Double.valueOf(margin));
      }
      return result;
    }
  }

  private final List<DesignCaseResult> caseResults;
  private final Map<String, GoverningValue> governingValues;

  EngineeringDesignEnvelope(List<DesignCaseResult> caseResults, Map<String, GoverningValue> governingValues) {
    this.caseResults = new ArrayList<DesignCaseResult>(caseResults);
    this.governingValues = new LinkedHashMap<String, GoverningValue>(governingValues);
  }

  public List<DesignCaseResult> getCaseResults() {
    return Collections.unmodifiableList(caseResults);
  }

  public Map<String, GoverningValue> getGoverningValues() {
    return Collections.unmodifiableMap(governingValues);
  }

  public int getSuccessfulCaseCount() {
    int count = 0;
    for (DesignCaseResult result : caseResults) {
      if ("CALCULATED".equals(result.getStatus())) {
        count++;
      }
    }
    return count;
  }

  public int getPartialCaseCount() {
    return countCases("CALCULATED_WITH_METRIC_FAILURES") + countCases("CALCULATED_NOT_CONVERGED");
  }

  public int getFailedCaseCount() {
    return countCases("FAILED");
  }

  public int getSkippedCaseCount() {
    return countCases("SKIPPED");
  }

  public int getLimitViolationCount() {
    int count = 0;
    for (DesignCaseResult result : caseResults) {
      for (DesignCaseResult.MetricResult metric : result.getMetricResults().values()) {
        if ("BELOW_LOWER_LIMIT".equals(metric.getLimitStatus())
            || "ABOVE_UPPER_LIMIT".equals(metric.getLimitStatus())) {
          count++;
        }
      }
    }
    return count;
  }

  public boolean hasCaseFailures() {
    return getPartialCaseCount() > 0 || getFailedCaseCount() > 0;
  }

  public List<EngineeringCalculation> toCalculations() {
    List<EngineeringCalculation> result = new ArrayList<EngineeringCalculation>();
    for (GoverningValue governing : governingValues.values()) {
      String subjectNodeId = "equipment:" + canonical(governing.getMetric().getSubjectTag());
      EngineeringCalculation calculation = new EngineeringCalculation(
          "envelope:" + canonical(governing.getMetric().getId()), subjectNodeId, "DesignCaseEngine governing envelope")
          .setStatus(EngineeringCalculation.Status.CALCULATED)
          .setResult(governing.getValue(), governing.getMetric().getUnit()).setDesignCaseId(governing.getDesignCaseId())
          .setMessage("Selected by " + governing.getMetric().getGoverningDirection().name());
      result.add(calculation);
    }
    return result;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> cases = new ArrayList<Map<String, Object>>();
    for (DesignCaseResult caseResult : caseResults) {
      cases.add(caseResult.toMap());
    }
    result.put("caseResults", cases);
    List<Map<String, Object>> governing = new ArrayList<Map<String, Object>>();
    for (GoverningValue value : governingValues.values()) {
      governing.add(value.toMap());
    }
    result.put("governingValues", governing);
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("configuredCaseCount", Integer.valueOf(caseResults.size()));
    summary.put("successfulCaseCount", Integer.valueOf(getSuccessfulCaseCount()));
    summary.put("partialCaseCount", Integer.valueOf(getPartialCaseCount()));
    summary.put("failedCaseCount", Integer.valueOf(getFailedCaseCount()));
    summary.put("skippedCaseCount", Integer.valueOf(getSkippedCaseCount()));
    summary.put("limitViolationCount", Integer.valueOf(getLimitViolationCount()));
    result.put("summary", summary);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static String canonical(String value) {
    return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }

  private int countCases(String status) {
    int count = 0;
    for (DesignCaseResult result : caseResults) {
      if (status.equals(result.getStatus())) {
        count++;
      }
    }
    return count;
  }
}
