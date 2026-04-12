package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Compares two or more process configurations side by side.
 *
 * <p>
 * Accepts an array of process cases (each with its own fluid and process definition), runs them
 * all, and returns a comparison table highlighting differences in key outputs (temperatures,
 * pressures, duties, compositions).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ProcessComparisonRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private ProcessComparisonRunner() {}

  /**
   * Runs multiple process cases and compares results.
   *
   * @param json JSON with "cases" array, each containing a full process definition
   * @return JSON with comparison table and individual results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }

    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();

      if (!input.has("cases") || !input.get("cases").isJsonArray()) {
        return errorJson("Missing 'cases' array. Provide at least 2 process cases to compare.");
      }

      JsonArray cases = input.getAsJsonArray("cases");
      if (cases.size() < 2) {
        return errorJson("Need at least 2 cases to compare. Got " + cases.size());
      }

      List<JsonObject> caseResults = new ArrayList<JsonObject>();
      List<String> caseNames = new ArrayList<String>();
      List<String> errors = new ArrayList<String>();

      for (int i = 0; i < cases.size(); i++) {
        JsonObject caseObj = cases.get(i).getAsJsonObject();
        String caseName =
            caseObj.has("name") ? caseObj.get("name").getAsString() : "Case " + (i + 1);
        caseNames.add(caseName);

        try {
          // Build the process JSON (remove the "name" field, pass rest to ProcessRunner)
          JsonObject processJson = new JsonObject();
          if (caseObj.has("fluid")) {
            processJson.add("fluid", caseObj.get("fluid"));
          }
          if (caseObj.has("process")) {
            processJson.add("process", caseObj.get("process"));
          }

          String resultStr = ProcessRunner.run(GSON.toJson(processJson));
          JsonObject resultObj = JsonParser.parseString(resultStr).getAsJsonObject();
          caseResults.add(resultObj);
        } catch (Exception e) {
          errors.add(caseName + ": " + e.getMessage());
          caseResults.add(null);
        }
      }

      // Build comparison
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("caseCount", cases.size());

      // Case names
      JsonArray namesArray = new JsonArray();
      for (String name : caseNames) {
        namesArray.add(name);
      }
      result.add("caseNames", namesArray);

      // Individual results
      JsonArray individualResults = new JsonArray();
      for (int i = 0; i < caseResults.size(); i++) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", caseNames.get(i));
        if (caseResults.get(i) != null) {
          entry.add("result", caseResults.get(i));
          entry.addProperty("converged", !hasError(caseResults.get(i)));
        } else {
          entry.addProperty("converged", false);
          entry.addProperty("error", errors.isEmpty() ? "Unknown error" : "Simulation failed");
        }
        individualResults.add(entry);
      }
      result.add("cases", individualResults);

      // Build comparison summary from report sections
      JsonObject comparison = buildComparisonSummary(caseNames, caseResults);
      result.add("comparison", comparison);

      if (!errors.isEmpty()) {
        JsonArray errArray = new JsonArray();
        for (String err : errors) {
          errArray.add(err);
        }
        result.add("errors", errArray);
      }

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("Process comparison failed: " + e.getMessage());
    }
  }

  /**
   * Builds a comparison summary from individual case results.
   *
   * @param caseNames list of case names
   * @param caseResults list of result JSON objects
   * @return comparison JSON object
   */
  private static JsonObject buildComparisonSummary(List<String> caseNames,
      List<JsonObject> caseResults) {
    JsonObject comparison = new JsonObject();

    // Extract key metrics from each case's report
    JsonArray metrics = new JsonArray();

    for (int i = 0; i < caseResults.size(); i++) {
      JsonObject caseMetrics = new JsonObject();
      caseMetrics.addProperty("case", caseNames.get(i));

      JsonObject caseResult = caseResults.get(i);
      if (caseResult != null && caseResult.has("report")) {
        JsonObject report = caseResult.getAsJsonObject("report");

        // Extract equipment summaries
        if (report.has("equipment")) {
          JsonArray equipment = report.getAsJsonArray("equipment");
          caseMetrics.addProperty("equipmentCount", equipment.size());

          // Summarize equipment outputs
          JsonArray equipSummary = new JsonArray();
          for (JsonElement eq : equipment) {
            JsonObject eqObj = eq.getAsJsonObject();
            JsonObject summary = new JsonObject();
            if (eqObj.has("name")) {
              summary.addProperty("name", eqObj.get("name").getAsString());
            }
            if (eqObj.has("type")) {
              summary.addProperty("type", eqObj.get("type").getAsString());
            }
            equipSummary.add(summary);
          }
          caseMetrics.add("equipment", equipSummary);
        }

        // Extract stream conditions
        if (report.has("streams")) {
          caseMetrics.add("streams", report.get("streams"));
        }
      } else {
        caseMetrics.addProperty("equipmentCount", 0);
        caseMetrics.addProperty("note", "Simulation did not produce a report");
      }

      metrics.add(caseMetrics);
    }

    comparison.add("caseMetrics", metrics);
    comparison.addProperty("comparisonNote",
        "Compare 'cases[i].result.report' for detailed equipment and stream data");

    return comparison;
  }

  /**
   * Checks if a result JSON indicates an error.
   *
   * @param result the result JSON object
   * @return true if the result has error status
   */
  private static boolean hasError(JsonObject result) {
    if (result.has("status")) {
      return "error".equals(result.get("status").getAsString());
    }
    return result.has("errors");
  }

  /**
   * Creates an error JSON response.
   *
   * @param message the error message
   * @return JSON string with status error
   */
  private static String errorJson(String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", message);
    return error.toString();
  }
}
