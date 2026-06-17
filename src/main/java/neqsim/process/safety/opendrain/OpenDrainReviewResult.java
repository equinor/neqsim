package neqsim.process.safety.opendrain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Review result for one open-drain area or drain system.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class OpenDrainReviewResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reviewed item. */
  private final OpenDrainReviewItem item;
  /** Requirement assessments. */
  private final List<OpenDrainAssessment> assessments = new ArrayList<OpenDrainAssessment>();
  /** Final verdict. */
  private String verdict = "NOT_EVALUATED";
  /** Evidence confidence from 0 to 1. */
  private double confidence;

  /**
   * Creates a result for one item.
   *
   * @param item reviewed item
   */
  public OpenDrainReviewResult(OpenDrainReviewItem item) {
    this.item = item;
  }

  /**
   * Adds an assessment.
   *
   * @param assessment assessment to add
   */
  public void addAssessment(OpenDrainAssessment assessment) {
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
    for (OpenDrainAssessment assessment : assessments) {
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
  public OpenDrainReviewItem getItem() {
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
  public List<OpenDrainAssessment> getAssessments() {
    return assessments;
  }

  /**
   * Sets the evidence confidence.
   *
   * @param confidence confidence from 0 to 1
   */
  public void setConfidence(double confidence) {
    this.confidence = Math.max(0.0, Math.min(1.0, confidence));
  }

  /**
   * Gets the evidence confidence.
   *
   * @return evidence confidence from 0 to 1
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Converts this result to a JSON-ready map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("areaId", item == null ? "" : item.getAreaId());
    map.put("areaType", item == null ? "" : item.getAreaType());
    map.put("drainSystemType", item == null ? "" : item.getDrainSystemType());
    map.put("verdict", verdict);
    map.put("confidence", confidence);
    map.put("sourceReferences",
        item == null ? new ArrayList<String>() : item.getSourceReferences());
    map.put("evidenceValues",
        item == null ? new LinkedHashMap<String, Object>() : item.getValues());
    List<Map<String, Object>> assessmentMaps = new ArrayList<Map<String, Object>>();
    for (OpenDrainAssessment assessment : assessments) {
      assessmentMaps.add(assessment.toMap());
    }
    map.put("assessments", assessmentMaps);
    return map;
  }
}
