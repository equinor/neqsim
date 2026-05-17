package neqsim.process.safety.processsafetysystem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Review result for one process safety function or review coverage item.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ProcessSafetySystemReviewResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSafetySystemReviewItem item;
  private final List<ProcessSafetySystemAssessment> assessments =
      new ArrayList<ProcessSafetySystemAssessment>();
  private String verdict = "NOT_EVALUATED";
  private double confidence;

  /**
   * Creates a result for one review item.
   *
   * @param item reviewed item
   */
  public ProcessSafetySystemReviewResult(ProcessSafetySystemReviewItem item) {
    this.item = item;
  }

  /**
   * Adds an assessment.
   *
   * @param assessment assessment to add
   */
  public void addAssessment(ProcessSafetySystemAssessment assessment) {
    if (assessment != null) {
      assessments.add(assessment);
    }
  }

  /**
   * Finalizes the result verdict from assessments.
   */
  public void finalizeVerdict() {
    boolean hasFail = false;
    boolean hasWarning = false;
    for (ProcessSafetySystemAssessment assessment : assessments) {
      hasFail = hasFail || assessment.isFailing();
      hasWarning = hasWarning || assessment.isWarning();
    }
    if (hasFail) {
      verdict = "FAIL";
    } else if (hasWarning) {
      verdict = "PASS_WITH_WARNINGS";
    } else {
      verdict = "PASS";
    }
  }

  /**
   * Gets the reviewed item.
   *
   * @return reviewed item
   */
  public ProcessSafetySystemReviewItem getItem() {
    return item;
  }

  /**
   * Gets the result verdict.
   *
   * @return verdict string
   */
  public String getVerdict() {
    return verdict;
  }

  /**
   * Gets requirement assessments.
   *
   * @return assessment list
   */
  public List<ProcessSafetySystemAssessment> getAssessments() {
    return assessments;
  }

  /**
   * Sets evidence confidence.
   *
   * @param confidence confidence from 0 to 1
   */
  public void setConfidence(double confidence) {
    this.confidence = Math.max(0.0, Math.min(1.0, confidence));
  }

  /**
   * Converts this result to a JSON-ready map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("functionId", item == null ? "" : item.getFunctionId());
    map.put("functionType", item == null ? "" : item.getFunctionType());
    map.put("equipmentTag", item == null ? "" : item.getEquipmentTag());
    map.put("verdict", verdict);
    map.put("confidence", confidence);
    map.put("sourceReferences",
        item == null ? new ArrayList<String>() : item.getSourceReferences());
    map.put("evidenceValues",
        item == null ? new LinkedHashMap<String, Object>() : item.getValues());
    List<Map<String, Object>> assessmentMaps = new ArrayList<Map<String, Object>>();
    for (ProcessSafetySystemAssessment assessment : assessments) {
      assessmentMaps.add(assessment.toMap());
    }
    map.put("assessments", assessmentMaps);
    return map;
  }
}