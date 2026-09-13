package neqsim.mcp.runners;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Runs bounded process cases through the canonical {@link ProcessRunner} and compares their reports.
 *
 * @author Even Solbraa
 * @version 1.1
 */
public final class ProcessComparisonRunner {
  static final int MAX_REQUEST_BYTES = 1024 * 1024;
  static final int MAX_CASES = 32;
  static final int MAX_NAME_LENGTH = 256;
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Private constructor for utility class. */
  private ProcessComparisonRunner() {
  }

  /**
   * Executes admitted cases sequentially in request order.
   *
   * @param json object containing two to 32 process cases
   * @return comparison with canonical per-case results and completion accounting
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
      return errorJson("Process comparison request exceeds " + MAX_REQUEST_BYTES + " UTF-8 bytes");
    }
    try {
      JsonElement parsed = JsonParser.parseString(json);
      if (!parsed.isJsonObject()) {
        return errorJson("Process comparison input must be a JSON object");
      }
      JsonObject input = parsed.getAsJsonObject();
      if (!input.has("cases") || !input.get("cases").isJsonArray()) {
        return errorJson("Missing 'cases' array. Provide at least 2 process cases to compare.");
      }
      JsonArray cases = input.getAsJsonArray("cases");
      if (cases.size() < 2) {
        return errorJson("Need at least 2 cases to compare. Got " + cases.size());
      }
      if (cases.size() > MAX_CASES) {
        return errorJson("Process comparison supports at most " + MAX_CASES + " cases");
      }

      List<JsonObject> admittedCases = new ArrayList<JsonObject>();
      List<String> caseNames = new ArrayList<String>();
      Set<String> uniqueNames = new LinkedHashSet<String>();
      for (int index = 0; index < cases.size(); index++) {
        JsonElement caseElement = cases.get(index);
        if (!caseElement.isJsonObject()) {
          return errorJson("Case " + (index + 1) + " must be a JSON object");
        }
        JsonObject caseObject = caseElement.getAsJsonObject();
        if (!caseObject.has("fluid") || !caseObject.get("fluid").isJsonObject()) {
          return errorJson("Case " + (index + 1) + " must contain a 'fluid' object");
        }
        if (!caseObject.has("process") || !caseObject.get("process").isJsonArray()) {
          return errorJson("Case " + (index + 1) + " must contain a 'process' array");
        }
        String caseName = caseName(caseObject, index);
        if (caseName == null) {
          return errorJson("Case " + (index + 1)
              + " name must be a non-blank string of at most " + MAX_NAME_LENGTH + " characters");
        }
        if (!uniqueNames.add(caseName)) {
          return errorJson("Case names must be unique. Duplicate: " + caseName);
        }
        admittedCases.add(caseObject);
        caseNames.add(caseName);
      }

      List<JsonObject> caseResults = new ArrayList<JsonObject>();
      List<String> errors = new ArrayList<String>();
      int successfulCaseCount = 0;
      for (int index = 0; index < admittedCases.size(); index++) {
        JsonObject caseObject = admittedCases.get(index);
        JsonObject processJson = new JsonObject();
        processJson.add("fluid", caseObject.get("fluid"));
        processJson.add("process", caseObject.get("process"));
        JsonObject caseResult;
        try {
          caseResult =
              JsonParser.parseString(ProcessRunner.run(GSON.toJson(processJson))).getAsJsonObject();
        } catch (Exception exception) {
          caseResult = new JsonObject();
          caseResult.addProperty("status", "error");
          caseResult.addProperty("message", "Canonical process execution failed");
        }
        if (hasError(caseResult)) {
          errors.add(caseNames.get(index) + ": " + errorMessage(caseResult));
        } else {
          successfulCaseCount++;
        }
        caseResults.add(caseResult);
      }

      int failedCaseCount = cases.size() - successfulCaseCount;
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("caseCount", cases.size());
      result.addProperty("successfulCaseCount", successfulCaseCount);
      result.addProperty("failedCaseCount", failedCaseCount);
      result.addProperty("complete", failedCaseCount == 0);

      JsonArray names = new JsonArray();
      for (String name : caseNames) {
        names.add(name);
      }
      result.add("caseNames", names);

      JsonArray individualResults = new JsonArray();
      for (int index = 0; index < caseResults.size(); index++) {
        JsonObject entry = new JsonObject();
        JsonObject caseResult = caseResults.get(index);
        boolean converged = !hasError(caseResult);
        entry.addProperty("name", caseNames.get(index));
        entry.addProperty("converged", converged);
        entry.add("result", caseResult);
        if (!converged) {
          entry.addProperty("error", errorMessage(caseResult));
        }
        individualResults.add(entry);
      }
      result.add("cases", individualResults);
      result.add("comparison", comparison(caseNames, caseResults));

      if (!errors.isEmpty()) {
        JsonArray errorArray = new JsonArray();
        for (String error : errors) {
          errorArray.add(error);
        }
        result.add("errors", errorArray);
      }
      result.addProperty("executionBoundary",
          "Each case uses canonical ProcessRunner; completeness does not establish case comparability or engineering validity");
      return GSON.toJson(result);
    } catch (Exception exception) {
      return errorJson("Process comparison input is invalid");
    }
  }

  private static String caseName(JsonObject caseObject, int index) {
    if (!caseObject.has("name")) {
      return "Case " + (index + 1);
    }
    JsonElement element = caseObject.get("name");
    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
      return null;
    }
    String name = element.getAsString().trim();
    return name.isEmpty() || name.length() > MAX_NAME_LENGTH ? null : name;
  }

  private static JsonObject comparison(List<String> names, List<JsonObject> results) {
    JsonObject comparison = new JsonObject();
    JsonArray metrics = new JsonArray();
    for (int index = 0; index < results.size(); index++) {
      JsonObject metric = new JsonObject();
      metric.addProperty("case", names.get(index));
      JsonObject caseResult = results.get(index);
      if (!hasError(caseResult) && caseResult.has("report")
          && caseResult.get("report").isJsonObject()) {
        JsonObject report = caseResult.getAsJsonObject("report");
        if (report.has("equipment") && report.get("equipment").isJsonArray()) {
          JsonArray equipment = report.getAsJsonArray("equipment");
          metric.addProperty("equipmentCount", equipment.size());
          JsonArray equipmentSummary = new JsonArray();
          for (JsonElement element : equipment) {
            if (!element.isJsonObject()) {
              continue;
            }
            JsonObject equipmentObject = element.getAsJsonObject();
            JsonObject summary = new JsonObject();
            if (equipmentObject.has("name")) {
              summary.add("name", equipmentObject.get("name"));
            }
            if (equipmentObject.has("type")) {
              summary.add("type", equipmentObject.get("type"));
            }
            equipmentSummary.add(summary);
          }
          metric.add("equipment", equipmentSummary);
        }
        if (report.has("streams")) {
          metric.add("streams", report.get("streams"));
        }
      } else {
        metric.addProperty("equipmentCount", 0);
        metric.addProperty("note",
            hasError(caseResult) ? "Canonical process execution failed"
                : "Simulation did not produce a report");
      }
      metrics.add(metric);
    }
    comparison.add("caseMetrics", metrics);
    comparison.addProperty("comparisonNote",
        "Compare cases[i].result.report only after confirming complete=true and compatible case bases");
    return comparison;
  }

  private static boolean hasError(JsonObject result) {
    if (result == null) {
      return true;
    }
    if (result.has("status") && result.get("status").isJsonPrimitive()) {
      return "error".equals(result.get("status").getAsString());
    }
    return result.has("errors");
  }

  private static String errorMessage(JsonObject result) {
    if (result != null && result.has("message") && result.get("message").isJsonPrimitive()) {
      return result.get("message").getAsString();
    }
    return "Canonical process execution returned an error";
  }

  private static String errorJson(String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", message);
    return error.toString();
  }
}
