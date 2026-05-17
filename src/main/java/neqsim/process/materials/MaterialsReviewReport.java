package neqsim.process.materials;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Process-wide materials review report.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class MaterialsReviewReport implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** JSON serializer for report output. */
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Project or asset name. */
  private String projectName = "materials-review";

  /** Overall review verdict. */
  private String overallVerdict = "PASS";

  /** Item results. */
  private final List<MaterialReviewResult> results = new ArrayList<MaterialReviewResult>();

  /** Report-level limitations and assumptions. */
  private final List<String> limitations = new ArrayList<String>();

  /**
   * Sets the project name.
   *
   * @param projectName project or asset name
   * @return this report for fluent construction
   */
  public MaterialsReviewReport setProjectName(String projectName) {
    this.projectName =
        projectName == null || projectName.trim().isEmpty() ? "materials-review" : projectName;
    return this;
  }

  /**
   * Adds an item result.
   *
   * @param result item result to add
   * @return this report for fluent construction
   */
  public MaterialsReviewReport addResult(MaterialReviewResult result) {
    if (result != null) {
      results.add(result);
    }
    return this;
  }

  /**
   * Adds a limitation or assumption.
   *
   * @param limitation limitation text
   * @return this report for fluent construction
   */
  public MaterialsReviewReport addLimitation(String limitation) {
    if (limitation != null && !limitation.trim().isEmpty()) {
      limitations.add(limitation);
    }
    return this;
  }

  /**
   * Finalizes the report verdict from all item results.
   */
  public void finalizeVerdict() {
    boolean hasFail = false;
    boolean hasWarning = false;
    for (MaterialReviewResult result : results) {
      hasFail = hasFail || "FAIL".equals(result.getVerdict());
      hasWarning = hasWarning || "PASS_WITH_WARNINGS".equals(result.getVerdict());
    }
    if (hasFail) {
      overallVerdict = "FAIL";
    } else if (hasWarning) {
      overallVerdict = "PASS_WITH_WARNINGS";
    } else {
      overallVerdict = "PASS";
    }
  }

  /**
   * Gets the overall verdict.
   *
   * @return overall verdict
   */
  public String getOverallVerdict() {
    return overallVerdict;
  }

  /**
   * Converts this report to a JSON-ready map.
   *
   * @return map representation of the report
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("status", "success");
    map.put("apiVersion", "1.0");
    map.put("reviewType", "materials_integrity_review");
    map.put("projectName", projectName);
    map.put("overallVerdict", overallVerdict);
    map.put("itemCount", results.size());
    int failed = 0;
    int warnings = 0;
    List<Map<String, Object>> resultMaps = new ArrayList<Map<String, Object>>();
    Set<String> standards = new LinkedHashSet<String>();
    for (MaterialReviewResult result : results) {
      if ("FAIL".equals(result.getVerdict())) {
        failed++;
      } else if ("PASS_WITH_WARNINGS".equals(result.getVerdict())) {
        warnings++;
      }
      standards.addAll(result.getStandardsApplied());
      resultMaps.add(result.toMap());
    }
    map.put("failedItems", failed);
    map.put("warningItems", warnings);
    map.put("items", resultMaps);
    map.put("standardsApplied", new ArrayList<String>(standards));
    map.put("limitations", new ArrayList<String>(limitations));
    return map;
  }

  /**
   * Converts this report to pretty JSON.
   *
   * @return JSON representation of the report
   */
  public String toJson() {
    return GSON.toJson(toMap());
  }
}
