package neqsim.process.materials;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete materials-review result for one item.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class MaterialReviewResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reviewed item. */
  private final MaterialReviewItem item;

  /** Damage-mechanism assessments. */
  private final List<DamageMechanismAssessment> assessments =
      new ArrayList<DamageMechanismAssessment>();

  /** Consolidated material recommendation. */
  private MaterialRecommendation recommendation = new MaterialRecommendation();

  /** Integrity life assessment. */
  private IntegrityLifeAssessment integrityLifeAssessment = new IntegrityLifeAssessment();

  /** Overall item verdict. */
  private String verdict = "PASS";

  /** Data confidence from 0 to 1. */
  private double confidence = 1.0;

  /**
   * Creates a result for an item.
   *
   * @param item reviewed item
   */
  public MaterialReviewResult(MaterialReviewItem item) {
    this.item = item;
  }

  /**
   * Adds a mechanism assessment.
   *
   * @param assessment assessment to add
   * @return this result for fluent construction
   */
  public MaterialReviewResult addAssessment(DamageMechanismAssessment assessment) {
    if (assessment != null) {
      assessments.add(assessment);
    }
    return this;
  }

  /**
   * Sets the material recommendation.
   *
   * @param recommendation recommendation to assign
   * @return this result for fluent construction
   */
  public MaterialReviewResult setRecommendation(MaterialRecommendation recommendation) {
    this.recommendation = recommendation == null ? new MaterialRecommendation() : recommendation;
    return this;
  }

  /**
   * Gets the recommendation.
   *
   * @return material recommendation
   */
  public MaterialRecommendation getRecommendation() {
    return recommendation;
  }

  /**
   * Sets the integrity life assessment.
   *
   * @param integrityLifeAssessment integrity life assessment
   * @return this result for fluent construction
   */
  public MaterialReviewResult setIntegrityLifeAssessment(
      IntegrityLifeAssessment integrityLifeAssessment) {
    this.integrityLifeAssessment =
        integrityLifeAssessment == null ? new IntegrityLifeAssessment() : integrityLifeAssessment;
    return this;
  }

  /**
   * Sets the confidence score.
   *
   * @param confidence confidence between 0 and 1
   * @return this result for fluent construction
   */
  public MaterialReviewResult setConfidence(double confidence) {
    this.confidence = Math.max(0.0, Math.min(1.0, confidence));
    return this;
  }

  /**
   * Finalizes the item verdict from all assessments and integrity life.
   */
  public void finalizeVerdict() {
    boolean hasFail = false;
    boolean hasWarning = false;
    for (DamageMechanismAssessment assessment : assessments) {
      hasFail = hasFail || assessment.isFailing();
      hasWarning = hasWarning || assessment.isWarning();
    }
    if ("FAIL".equals(integrityLifeAssessment.getVerdict())) {
      hasFail = true;
    } else if ("PASS_WITH_WARNINGS".equals(integrityLifeAssessment.getVerdict())) {
      hasWarning = true;
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
   * Gets the verdict.
   *
   * @return item verdict
   */
  public String getVerdict() {
    return verdict;
  }

  /**
   * Gets standards referenced by the assessments and recommendation.
   *
   * @return ordered standards set
   */
  public Set<String> getStandardsApplied() {
    Set<String> standards = new LinkedHashSet<String>();
    for (DamageMechanismAssessment assessment : assessments) {
      if (!assessment.getStandard().isEmpty()) {
        standards.add(assessment.getStandard());
      }
    }
    return standards;
  }

  /**
   * Converts the result to a JSON-ready map.
   *
   * @return map representation of the result
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("item", item.toMap());
    map.put("verdict", verdict);
    map.put("confidence", confidence);
    List<Map<String, Object>> mechanismMaps = new ArrayList<Map<String, Object>>();
    for (DamageMechanismAssessment assessment : assessments) {
      mechanismMaps.add(assessment.toMap());
    }
    map.put("damageMechanisms", mechanismMaps);
    map.put("recommendation", recommendation.toMap());
    map.put("integrityLife", integrityLifeAssessment.toMap());
    map.put("standardsApplied", new ArrayList<String>(getStandardsApplied()));
    return map;
  }
}
