package neqsim.process.safety.opendrain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Aggregated report for an open-drain review.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class OpenDrainReviewReport implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reviewed project name. */
  private final String projectName;
  /** Per-item review results. */
  private final List<OpenDrainReviewResult> results = new ArrayList<OpenDrainReviewResult>();
  /** Report assumptions. */
  private final List<String> assumptions = new ArrayList<String>();
  /** Report limitations. */
  private final List<String> limitations = new ArrayList<String>();
  /** Standards applied. */
  private final List<String> standardsApplied = new ArrayList<String>();
  /** Overall report verdict. */
  private String overallVerdict = "NOT_EVALUATED";

  /**
   * Creates an open-drain report.
   *
   * @param projectName reviewed project name
   */
  public OpenDrainReviewReport(String projectName) {
    this.projectName = projectName == null || projectName.trim().isEmpty() ? "open-drain-review"
        : projectName.trim();
    standardsApplied.add(OpenDrainReviewEngine.NORSOK_S001 + " Clause 9");
    assumptions.add("Evidence may be normalized STID/P&ID input or calculated from NeqSim process streams.");
    assumptions.add("If no area-specific process fire leak rate is supplied, 5 kg/s is used.");
    limitations.add(
        "The review checks evidence and rule consistency; it is not a CFD or hydraulic transient model.");
    limitations.add(
        "Tagreader data is optional operational evidence and is not required for deterministic standards review.");
  }

  /**
   * Adds a per-item review result.
   *
   * @param result result to add
   */
  public void addResult(OpenDrainReviewResult result) {
    if (result != null) {
      results.add(result);
    }
  }

  /**
   * Finalizes the overall verdict from all result verdicts.
   */
  public void finalizeVerdict() {
    boolean hasFail = false;
    boolean hasWarning = false;
    for (OpenDrainReviewResult result : results) {
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
   * Gets the per-item results.
   *
   * @return review results
   */
  public List<OpenDrainReviewResult> getResults() {
    return results;
  }

  /**
   * Converts the report to a JSON-ready map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    int failedItems = 0;
    int warningItems = 0;
    for (OpenDrainReviewResult result : results) {
      if ("FAIL".equals(result.getVerdict())) {
        failedItems++;
      } else if ("PASS_WITH_WARNINGS".equals(result.getVerdict())) {
        warningItems++;
      }
    }
    map.put("status", "success");
    map.put("reviewType", "open_drain_review");
    map.put("projectName", projectName);
    map.put("overallVerdict", overallVerdict);
    map.put("itemCount", results.size());
    map.put("failedItems", failedItems);
    map.put("warningItems", warningItems);
    map.put("standardsApplied", new ArrayList<String>(standardsApplied));
    map.put("assumptions", new ArrayList<String>(assumptions));
    map.put("limitations", new ArrayList<String>(limitations));
    List<Map<String, Object>> resultMaps = new ArrayList<Map<String, Object>>();
    for (OpenDrainReviewResult result : results) {
      resultMaps.add(result.toMap());
    }
    map.put("results", resultMaps);
    return map;
  }

  /**
   * Serializes the report to pretty JSON.
   *
   * @return JSON report
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(toMap());
  }
}
