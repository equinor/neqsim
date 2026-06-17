package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.materials.MaterialsReviewEngine;
import neqsim.process.materials.MaterialsReviewInput;
import neqsim.process.materials.MaterialsReviewReport;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Stateless MCP runner for process-wide materials, corrosion, degradation, and integrity review.
 *
 * <p>
 * The runner accepts normalized materials-register JSON, optional STID extract JSON, and optional
 * NeqSim process JSON. Process JSON is executed first so temperatures, pressures, and compositions
 * can be merged with STID/material-register material data before the materials review is evaluated.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class MaterialsReviewRunner {
  /** JSON serializer. */
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for a static runner.
   */
  private MaterialsReviewRunner() {}

  /**
   * Runs a materials review from JSON.
   *
   * @param json input JSON containing optional processJson, materialsRegister/items, or stidData
   * @return JSON result with materials review report and provenance
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide processJson, materialsRegister/items, or stidData.");
    }
    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception ex) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse materials-review JSON input.",
          "Ensure the input JSON is well formed and does not contain comments or trailing commas.");
    }

    long startTime = System.currentTimeMillis();
    try {
      MaterialsReviewInput registerInput = MaterialsReviewInput.fromJsonObject(input);
      MaterialsReviewEngine engine = new MaterialsReviewEngine();
      MaterialsReviewReport report;
      JsonArray warnings = new JsonArray();
      if (input.has("processJson")) {
        SimulationResult processResult = ProcessSystem.fromJsonAndRun(processJsonAsString(input));
        if (processResult.isError()) {
          return errorJson("PROCESS_SIMULATION_ERROR", "Process JSON failed to run",
              "Run runProcess first and fix process JSON errors before materials review.");
        }
        for (String warning : processResult.getWarnings()) {
          warnings.add(warning);
        }
        report = engine.evaluate(processResult.getProcessSystem(), registerInput);
      } else {
        if (registerInput.getItems().isEmpty()) {
          return errorJson("MISSING_MATERIALS_DATA",
              "No materialsRegister/items/stidData or processJson was supplied",
              "Provide at least one review item or a runnable processJson object.");
        }
        report = engine.evaluate(registerInput);
      }
      JsonObject root = JsonParser.parseString(report.toJson()).getAsJsonObject();
      if (warnings.size() > 0) {
        root.add("processWarnings", warnings);
      }
      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("materials review");
      provenance.setConverged(!"FAIL".equals(report.getOverallVerdict()));
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      root.add("provenance", GSON.toJsonTree(provenance));
      return GSON.toJson(root);
    } catch (Exception ex) {
      return errorJson("MATERIALS_REVIEW_ERROR", "Materials review failed during evaluation.",
          "Check material register keys, numeric units, and process JSON compatibility.");
    }
  }

  /**
   * Converts the processJson field to a string accepted by ProcessSystem.fromJsonAndRun.
   *
   * @param input runner input object
   * @return process JSON string
   */
  private static String processJsonAsString(JsonObject input) {
    JsonElement process = input.get("processJson");
    if (process.isJsonPrimitive()) {
      return process.getAsString();
    }
    return GSON.toJson(process);
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
