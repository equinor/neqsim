package neqsim.process.materials;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consolidated material selection and mitigation recommendation for one reviewed item.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class MaterialRecommendation implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Recommended material grade or material family. */
  private String recommendedMaterial = "";

  /** Recommended corrosion allowance in millimetres. */
  private double recommendedCorrosionAllowanceMm = 0.0;

  /** Recommendation rationale. */
  private String rationale = "";

  /** Alternative acceptable materials. */
  private final List<String> alternativeMaterials = new ArrayList<String>();

  /** Recommended actions or mitigations. */
  private final List<String> actions = new ArrayList<String>();

  /** Standards supporting the recommendation. */
  private final List<String> standards = new ArrayList<String>();

  /**
   * Creates an empty recommendation.
   */
  public MaterialRecommendation() {}

  /**
   * Sets the recommended material.
   *
   * @param recommendedMaterial material grade or family
   * @return this recommendation for fluent construction
   */
  public MaterialRecommendation setRecommendedMaterial(String recommendedMaterial) {
    this.recommendedMaterial = recommendedMaterial == null ? "" : recommendedMaterial;
    return this;
  }

  /**
   * Gets the recommended material.
   *
   * @return recommended material string
   */
  public String getRecommendedMaterial() {
    return recommendedMaterial;
  }

  /**
   * Sets the recommended corrosion allowance.
   *
   * @param recommendedCorrosionAllowanceMm corrosion allowance in millimetres
   * @return this recommendation for fluent construction
   */
  public MaterialRecommendation setRecommendedCorrosionAllowanceMm(
      double recommendedCorrosionAllowanceMm) {
    this.recommendedCorrosionAllowanceMm = Math.max(0.0, recommendedCorrosionAllowanceMm);
    return this;
  }

  /**
   * Gets the recommended corrosion allowance.
   *
   * @return corrosion allowance in millimetres
   */
  public double getRecommendedCorrosionAllowanceMm() {
    return recommendedCorrosionAllowanceMm;
  }

  /**
   * Sets the recommendation rationale.
   *
   * @param rationale human-readable rationale
   * @return this recommendation for fluent construction
   */
  public MaterialRecommendation setRationale(String rationale) {
    this.rationale = rationale == null ? "" : rationale;
    return this;
  }

  /**
   * Adds an alternative material.
   *
   * @param material material grade or family
   * @return this recommendation for fluent construction
   */
  public MaterialRecommendation addAlternativeMaterial(String material) {
    if (material != null && !material.trim().isEmpty()
        && !alternativeMaterials.contains(material)) {
      alternativeMaterials.add(material);
    }
    return this;
  }

  /**
   * Adds a recommended action.
   *
   * @param action action text
   * @return this recommendation for fluent construction
   */
  public MaterialRecommendation addAction(String action) {
    if (action != null && !action.trim().isEmpty() && !actions.contains(action)) {
      actions.add(action);
    }
    return this;
  }

  /**
   * Adds a standard reference.
   *
   * @param standard standard reference string
   * @return this recommendation for fluent construction
   */
  public MaterialRecommendation addStandard(String standard) {
    if (standard != null && !standard.trim().isEmpty() && !standards.contains(standard)) {
      standards.add(standard);
    }
    return this;
  }

  /**
   * Converts the recommendation to a JSON-ready map.
   *
   * @return map representation of the recommendation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("recommendedMaterial", recommendedMaterial);
    map.put("recommendedCorrosionAllowance_mm", recommendedCorrosionAllowanceMm);
    map.put("rationale", rationale);
    map.put("alternativeMaterials", new ArrayList<String>(alternativeMaterials));
    map.put("actions", new ArrayList<String>(actions));
    map.put("standards", new ArrayList<String>(standards));
    return map;
  }
}
