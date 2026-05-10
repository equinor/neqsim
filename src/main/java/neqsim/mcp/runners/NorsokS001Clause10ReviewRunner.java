package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.safety.processsafetysystem.ProcessSafetySystemReviewEngine;
import neqsim.process.safety.processsafetysystem.ProcessSafetySystemReviewInput;
import neqsim.process.safety.processsafetysystem.ProcessSafetySystemReviewReport;

/**
 * Stateless MCP runner for NORSOK S-001 Clause 10 process safety system review.
 *
 * <p>
 * The runner consumes normalized technical-document and instrument-data evidence. If supplied, it
 * also embeds outputs from the existing safety-system performance runner, operational-study runner,
 * and dynamic simulation runner, so a single result can show clause findings alongside calculated
 * barrier, tagreader, and transient process evidence.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class NorsokS001Clause10ReviewRunner {
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for a static runner.
   */
  private NorsokS001Clause10ReviewRunner() {}

  /**
   * Runs a Clause 10 process safety system review from JSON.
   *
   * @param json input JSON containing items, processSafetyFunctions, stidData, or tagreaderData
   * @return JSON result with Clause 10 review report, optional embedded analyses, and provenance
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide normalized process safety functions, stidData, or tagreaderData.");
    }
    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException ex) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse Clause 10 review JSON input.",
          "Ensure the input JSON is well formed and does not contain comments or trailing commas.");
    }
    long startTime = System.currentTimeMillis();
    try {
      ProcessSafetySystemReviewInput reviewInput =
          ProcessSafetySystemReviewInput.fromJsonObject(input);
      if (reviewInput.getItems().isEmpty()) {
        return errorJson("MISSING_PROCESS_SAFETY_DATA",
            "No process safety system review items were supplied.",
            "Provide at least one item, processSafetyFunctions array, stidData, or tagreaderData record.");
      }
      ProcessSafetySystemReviewReport report =
          new ProcessSafetySystemReviewEngine().evaluate(reviewInput);
      JsonObject root = JsonParser.parseString(report.toJson()).getAsJsonObject();
      addEmbeddedAnalysis(input, root, "safetySystemPerformanceInput", "safetySystemPerformance",
          EmbeddedRunner.SAFETY_SYSTEM_PERFORMANCE);
      addEmbeddedAnalysis(input, root, "operationalStudyInput", "operationalStudy",
          EmbeddedRunner.OPERATIONAL_STUDY);
      addEmbeddedAnalysis(input, root, "dynamicSimulationInput", "dynamicSimulation",
          EmbeddedRunner.DYNAMIC_SIMULATION);
      addProvenance(root, report, startTime);
      return GSON.toJson(root);
    } catch (RuntimeException ex) {
      return errorJson("CLAUSE10_REVIEW_ERROR",
          "NORSOK S-001 Clause 10 review failed during evaluation: " + ex.getMessage(),
          "Check normalized evidence keys, numeric units, and JSON shape.");
    }
  }

  /** Embedded runner selectors. */
  private enum EmbeddedRunner {
    /** Existing safety-system performance runner. */
    SAFETY_SYSTEM_PERFORMANCE,
    /** Existing operational-study runner. */
    OPERATIONAL_STUDY,
    /** Existing dynamic/transient process runner. */
    DYNAMIC_SIMULATION
  }

  /**
   * Adds an embedded analysis result when an input object is present.
   *
   * @param input top-level runner input
   * @param root output root receiving embedded analyses
   * @param inputKey key containing the embedded runner input
   * @param outputKey key used in the embeddedAnalyses object
   * @param runner runner selector
   */
  private static void addEmbeddedAnalysis(JsonObject input, JsonObject root, String inputKey,
      String outputKey, EmbeddedRunner runner) {
    if (!input.has(inputKey) || !input.get(inputKey).isJsonObject()) {
      return;
    }
    JsonObject analyses = root.has("embeddedAnalyses") && root.get("embeddedAnalyses").isJsonObject()
        ? root.getAsJsonObject("embeddedAnalyses") : new JsonObject();
    String runnerInput = GSON.toJson(input.getAsJsonObject(inputKey));
    String runnerOutput;
    if (runner == EmbeddedRunner.SAFETY_SYSTEM_PERFORMANCE) {
      runnerOutput = SafetySystemPerformanceRunner.run(runnerInput);
    } else if (runner == EmbeddedRunner.OPERATIONAL_STUDY) {
      runnerOutput = OperationalStudyRunner.run(runnerInput);
    } else {
      runnerOutput = DynamicRunner.run(runnerInput);
    }
    try {
      JsonElement parsed = JsonParser.parseString(runnerOutput);
      analyses.add(outputKey, parsed);
    } catch (RuntimeException ex) {
      JsonObject fallback = new JsonObject();
      fallback.addProperty("status", "error");
      fallback.addProperty("message", "Embedded runner returned non-JSON output.");
      analyses.add(outputKey, fallback);
    }
    root.add("embeddedAnalyses", analyses);
  }

  /**
   * Adds result provenance to the output root.
   *
   * @param root output root
   * @param report generated report
   * @param startTime start time in milliseconds
   */
  private static void addProvenance(JsonObject root, ProcessSafetySystemReviewReport report,
      long startTime) {
    ResultProvenance provenance = new ResultProvenance();
    provenance.setCalculationType("NORSOK S-001 Clause 10 process safety system review");
    provenance.setConverged(!"FAIL".equals(report.getOverallVerdict()));
    provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
    provenance.addAssumption("Normalized technical-document and instrument-data evidence supplied by caller.");
    provenance.addLimitation("No direct STID or tagreader connection is opened by the Java runner.");
    provenance.addLimitation(
        "Dynamic simulations are embedded when supplied as dynamicSimulationInput; the review engine itself remains deterministic.");
    root.add("provenance", GSON.toJsonTree(provenance));
  }

  /**
   * Creates a standard error JSON string.
   *
   * @param code error code
   * @param message error message
   * @param remediation recommended remediation
   * @return JSON error string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject item = new JsonObject();
    item.addProperty("code", code);
    item.addProperty("message", message);
    item.addProperty("remediation", remediation);
    errors.add(item);
    error.add("errors", errors);
    return GSON.toJson(error);
  }
}