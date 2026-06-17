package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.safety.opendrain.OpenDrainReviewEngine;
import neqsim.process.safety.opendrain.OpenDrainReviewInput;
import neqsim.process.safety.opendrain.OpenDrainReviewReport;

/**
 * Stateless MCP runner for NORSOK S-001 open-drain review.
 *
 * <p>
 * The runner accepts normalized STID/P&amp;ID extracts and optional tagreader/historian evidence.
 * It does not connect to STID or tagreader directly; those systems should feed normalized JSON into
 * the runner.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class OpenDrainReviewRunner {
  /** JSON serializer. */
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for a static runner.
   */
  private OpenDrainReviewRunner() {}

  /**
   * Runs an open-drain review from JSON.
   *
   * @param json input JSON containing items, openDrainAreas, drainAreas, or stidData
   * @return JSON result with open-drain review report and provenance
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide normalized open-drain items, openDrainAreas, drainAreas, or stidData.");
    }
    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception ex) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse open-drain-review JSON input.",
          "Ensure the input JSON is well formed and does not contain comments or trailing commas.");
    }
    long startTime = System.currentTimeMillis();
    try {
      OpenDrainReviewInput reviewInput = OpenDrainReviewInput.fromJsonObject(input);
      if (reviewInput.getItems().isEmpty()) {
        return errorJson("MISSING_OPEN_DRAIN_DATA",
            "No open-drain items, drainAreas, openDrainAreas, or stidData were supplied.",
            "Provide at least one normalized area or drain-system record from STID/P&ID evidence.");
      }
      OpenDrainReviewReport report = new OpenDrainReviewEngine().evaluate(reviewInput);
      JsonObject root = JsonParser.parseString(report.toJson()).getAsJsonObject();
      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("open drain review");
      provenance.setConverged(!"FAIL".equals(report.getOverallVerdict()));
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      provenance.addAssumption("Normalized STID/P&ID evidence supplied by caller.");
      provenance
          .addLimitation("No direct STID or tagreader connection is opened by the Java runner.");
      root.add("provenance", GSON.toJsonTree(provenance));
      return GSON.toJson(root);
    } catch (Exception ex) {
      return errorJson("OPEN_DRAIN_REVIEW_ERROR", "Open-drain review failed during evaluation.",
          "Check normalized evidence keys, numeric units, and JSON shape.");
    }
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
