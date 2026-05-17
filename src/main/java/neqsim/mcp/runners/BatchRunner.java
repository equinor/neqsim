package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;

/**
 * Stateless batch calculation runner for MCP integration.
 *
 * <p> Runs multiple flash calculations in a single call, sharing a common fluid definition. This is
 * designed for sensitivity studies and property sweeps where an agent needs to explore a parameter
 * space efficiently. Each case in the batch can vary temperature, pressure, or composition. </p>
 *
 * <h2>Input JSON Format:</h2>
 *
 * <pre>{@code { "model": "SRK", "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
 * "mixingRule": "classic", "flashType": "TP", "cases": [ {"temperature": {"value": 0.0, "unit":
 * "C"}, "pressure": {"value": 50.0, "unit": "bara"}}, {"temperature": {"value": 25.0, "unit": "C"},
 * "pressure": {"value": 50.0, "unit": "bara"}}, {"temperature": {"value": 50.0, "unit": "C"},
 * "pressure": {"value": 50.0, "unit": "bara"}} ] } }</pre>
 *
 * <p> Each case can also override components or flash type to explore composition sensitivity or
 * different calculation modes within a single call. </p>
 *
 * @author Even Solbraa @version 1.0
 */
public class BatchRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Maximum number of cases per batch to prevent resource exhaustion. */
  private static final int MAX_CASES = 500;

  /**
   * Private constructor — all methods are static.
   */
  private BatchRunner() {}

  /**
   * Runs a batch of flash calculations from a JSON specification.
   *
   * <p>
   * Each case inherits the base fluid definition (model, components, mixingRule, flashType) and can
   * override temperature, pressure, components, or flashType per case. Results are returned as an
   * array with per-case status so partial failures don't block the entire batch.
   * </p>
   *
   * @param json the JSON batch specification
   * @return a JSON string with batch results and provenance
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a JSON batch specification with 'components' and 'cases' array");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    long startTime = System.currentTimeMillis();

    // --- Parse cases array ---
    if (!input.has("cases") || !input.get("cases").isJsonArray()) {
      return errorJson("MISSING_CASES", "'cases' array is required",
          "Provide an array of case objects, each with temperature and/or pressure overrides");
    }

    JsonArray casesArray = input.getAsJsonArray("cases");
    if (casesArray.size() == 0) {
      return errorJson("EMPTY_CASES", "'cases' array is empty", "Provide at least one case");
    }
    if (casesArray.size() > MAX_CASES) {
      return errorJson("TOO_MANY_CASES",
          "Batch has " + casesArray.size() + " cases, maximum is " + MAX_CASES,
          "Split into multiple batch calls with <= " + MAX_CASES + " cases each");
    }

    // --- Build base flash JSON template ---
    JsonObject baseFlash = new JsonObject();
    if (input.has("model")) {
      baseFlash.addProperty("model", input.get("model").getAsString());
    }
    if (input.has("components")) {
      baseFlash.add("components", input.get("components"));
    }
    if (input.has("mixingRule")) {
      baseFlash.addProperty("mixingRule", input.get("mixingRule").getAsString());
    }
    if (input.has("flashType")) {
      baseFlash.addProperty("flashType", input.get("flashType").getAsString());
    }

    // --- Run each case ---
    JsonArray results = new JsonArray();
    int successCount = 0;
    int errorCount = 0;
    List<String> failedIndices = new ArrayList<String>();

    for (int i = 0; i < casesArray.size(); i++) {
      JsonElement caseEl = casesArray.get(i);
      if (!caseEl.isJsonObject()) {
        JsonObject caseResult = new JsonObject();
        caseResult.addProperty("caseIndex", i);
        caseResult.addProperty("status", "error");
        caseResult.addProperty("message", "Case " + i + " is not a JSON object");
        results.add(caseResult);
        errorCount++;
        failedIndices.add(String.valueOf(i));
        continue;
      }

      // Merge base flash spec with case overrides
      JsonObject caseObj = caseEl.getAsJsonObject();
      JsonObject flashInput = mergeJsonObjects(baseFlash, caseObj);

      try {
        String flashResult = FlashRunner.run(flashInput.toString());
        JsonObject flashObj = JsonParser.parseString(flashResult).getAsJsonObject();
        flashObj.addProperty("caseIndex", i);
        results.add(flashObj);

        String status = flashObj.has("status") ? flashObj.get("status").getAsString() : "ok";
        if ("error".equals(status)) {
          errorCount++;
          failedIndices.add(String.valueOf(i));
        } else {
          successCount++;
        }
      } catch (Exception e) {
        JsonObject caseResult = new JsonObject();
        caseResult.addProperty("caseIndex", i);
        caseResult.addProperty("status", "error");
        caseResult.addProperty("message", "Case " + i + " failed: " + e.getMessage());
        results.add(caseResult);
        errorCount++;
        failedIndices.add(String.valueOf(i));
      }
    }

    // --- Build response ---
    JsonObject response = new JsonObject();
    response.addProperty("status",
        errorCount == 0 ? "ok" : (successCount == 0 ? "error" : "partial"));

    JsonObject summary = new JsonObject();
    summary.addProperty("totalCases", casesArray.size());
    summary.addProperty("succeeded", successCount);
    summary.addProperty("failed", errorCount);
    if (!failedIndices.isEmpty()) {
      JsonArray failedArr = new JsonArray();
      for (String idx : failedIndices) {
        failedArr.add(Integer.parseInt(idx));
      }
      summary.add("failedCaseIndices", failedArr);
    }
    response.add("summary", summary);
    response.add("results", results);

    // --- Provenance ---
    String model = input.has("model") ? input.get("model").getAsString() : "SRK";
    ResultProvenance provenance = ResultProvenance.forBatch(model, casesArray.size(), successCount);
    provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
    provenance.setConverged(errorCount == 0);
    response.add("provenance", GSON.toJsonTree(provenance));

    return GSON.toJson(response);
  }

  /**
   * Merges two JSON objects, with the override taking precedence.
   *
   * @param base the base object
   * @param overrides the overriding values
   * @return a new merged JSON object
   */
  private static JsonObject mergeJsonObjects(JsonObject base, JsonObject overrides) {
    JsonObject merged = new JsonObject();
    // Copy base properties
    for (java.util.Map.Entry<String, JsonElement> entry : base.entrySet()) {
      merged.add(entry.getKey(), entry.getValue());
    }
    // Apply overrides
    for (java.util.Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
      merged.add(entry.getKey(), entry.getValue());
    }
    return merged;
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return JSON error string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject result = new JsonObject();
    result.addProperty("status", "error");

    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    if (remediation != null) {
      err.addProperty("remediation", remediation);
    }
    errors.add(err);
    result.add("errors", errors);

    return GSON.toJson(result);
  }
}
