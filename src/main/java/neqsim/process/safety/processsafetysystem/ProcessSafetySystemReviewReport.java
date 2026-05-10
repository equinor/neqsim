package neqsim.process.safety.processsafetysystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated report for a NORSOK S-001 Clause 10 process safety system review.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ProcessSafetySystemReviewReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String projectName;
  private final List<ProcessSafetySystemReviewResult> results =
      new ArrayList<ProcessSafetySystemReviewResult>();
  private final List<String> assumptions = new ArrayList<String>();
  private final List<String> limitations = new ArrayList<String>();
  private final List<String> standardsApplied = new ArrayList<String>();
  private final List<String> extractionTemplates = new ArrayList<String>();
  private String overallVerdict = "NOT_EVALUATED";

  /**
   * Creates a process safety system report.
   *
   * @param projectName reviewed project name
   */
  public ProcessSafetySystemReviewReport(String projectName) {
    this.projectName = projectName == null || projectName.trim().isEmpty()
        ? "process-safety-system-review" : projectName.trim();
    standardsApplied.add(ProcessSafetySystemReviewEngine.NORSOK_S001 + " Clause 10");
    assumptions.add("Technical documentation and instrument data are normalized before review.");
    limitations.add("The Java review engine does not connect directly to STID or tagreader.");
    limitations.add("Calculated PSV, SIF, LOPA, and operational outputs are referenced as evidence when supplied.");
    extractionTemplates.add("HAZID/HAZOP/LOPA: scenario id, consequence, demand frequency, safeguards, and credited protection layers.");
    extractionTemplates.add("C&E matrix: functionId, causes, actions, response times, logic solver, final elements.");
    extractionTemplates.add("SRS/SIL records: SIF id, architecture, SIL/PFD, proof-test interval, bypass rules.");
    extractionTemplates.add("SIS/ESD/FGS implementation: logic solver, FGS/ESD/SIS architecture, cause/effect implementation, and final-element data.");
    extractionTemplates.add("Verification/testing/operation: FAT, SAT, proof-test status, bypass/override register, maintenance plan, and live instrument status.");
    extractionTemplates.add("PSV list: protected equipment, set pressure, relief load, rated capacity, scenario basis.");
    extractionTemplates.add("Instrument data: tag, role, trip setpoint, proof-test status, bypass/override status.");
    extractionTemplates.add("Utility dependency matrix: safety function, required utility, fail-safe state, survivability.");
  }

  /**
   * Adds a per-item review result.
   *
   * @param result result to add
   */
  public void addResult(ProcessSafetySystemReviewResult result) {
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
    for (ProcessSafetySystemReviewResult result : results) {
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
   * Gets per-item results.
   *
   * @return review results
   */
  public List<ProcessSafetySystemReviewResult> getResults() {
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
    for (ProcessSafetySystemReviewResult result : results) {
      if ("FAIL".equals(result.getVerdict())) {
        failedItems++;
      } else if ("PASS_WITH_WARNINGS".equals(result.getVerdict())) {
        warningItems++;
      }
    }
    map.put("status", "success");
    map.put("reviewType", "norsok_s001_clause10_review");
    map.put("projectName", projectName);
    map.put("overallVerdict", overallVerdict);
    map.put("itemCount", results.size());
    map.put("failedItems", failedItems);
    map.put("warningItems", warningItems);
    map.put("standardsApplied", new ArrayList<String>(standardsApplied));
    map.put("assumptions", new ArrayList<String>(assumptions));
    map.put("limitations", new ArrayList<String>(limitations));
    map.put("extractionTemplates", new ArrayList<String>(extractionTemplates));
    List<Map<String, Object>> resultMaps = new ArrayList<Map<String, Object>>();
    for (ProcessSafetySystemReviewResult result : results) {
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
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
        .create();
    return gson.toJson(toMap());
  }
}