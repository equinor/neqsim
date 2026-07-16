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

  public boolean hasCaseFailures() {
    return getSuccessfulCaseCount() < caseResults.size();
  }

  public List<EngineeringCalculation> toCalculations() {
    List<EngineeringCalculation> result = new ArrayList<EngineeringCalculation>();
    for (GoverningValue governing : governingValues.values()) {
      String subjectNodeId = "equipment:" + canonical(governing.getMetric().getSubjectTag());
      EngineeringCalculation calculation = new EngineeringCalculation(
          "envelope:" + canonical(governing.getMetric().getId()), subjectNodeId, "DesignCaseEngine governing envelope")
              .setStatus(EngineeringCalculation.Status.CALCULATED)
              .setResult(governing.getValue(), governing.getMetric().getUnit())
              .setDesignCaseId(governing.getDesignCaseId())
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
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static String canonical(String value) {
    return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }
}
