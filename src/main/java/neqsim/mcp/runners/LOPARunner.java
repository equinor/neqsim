package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/**
 * MCP runner for Layer of Protection Analysis (LOPA).
 *
 * <p>
 * Computes the mitigated event frequency from an initiating event and a list of independent
 * protection layers (IPLs), checks against a target frequency, and returns the gap and required
 * additional risk reduction (RRF / SIL).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class LOPARunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private LOPARunner() {}

  /**
   * Runs a LOPA calculation from a JSON definition.
   *
   * @param json JSON with scenario, initiatingEventFrequency, targetFrequency and layers array
   * @return JSON string with LOPA result
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String scenarioName =
          input.has("scenario") ? input.get("scenario").getAsString() : "unnamed scenario";
      double initiating = req(input, "initiatingEventFrequency_per_year");
      double target = req(input, "targetFrequency_per_year");

      LOPAResult lopa = new LOPAResult(scenarioName);
      lopa.setInitiatingEventFrequency(initiating);
      lopa.setTargetFrequency(target);

      double current = initiating;
      if (input.has("layers")) {
        JsonArray layers = input.getAsJsonArray("layers");
        for (JsonElement el : layers) {
          JsonObject layer = el.getAsJsonObject();
          String name = layer.has("name") ? layer.get("name").getAsString() : "Layer " + (current);
          double pfd = layer.get("pfd").getAsDouble();
          double before = current;
          double after = before * pfd;
          lopa.addLayer(name, pfd, before, after);
          current = after;
        }
      }
      lopa.setMitigatedFrequency(current);

      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("standard", "IEC 61511 / CCPS LOPA");
      out.add("lopa", JsonParser.parseString(lopa.toJson()));

      // Add explicit gap analysis with required SIL
      JsonObject gap = new JsonObject();
      gap.addProperty("targetMet", lopa.isTargetMet());
      gap.addProperty("gapToTarget_per_year", round(lopa.getGapToTarget(), 12));
      gap.addProperty("totalRRF", round(lopa.getTotalRRF(), 2));
      if (!lopa.isTargetMet()) {
        gap.addProperty("requiredAdditionalRRF", round(lopa.getRequiredAdditionalRRF(), 2));
        gap.addProperty("requiredAdditionalSIL", lopa.getRequiredAdditionalSIL());
        double requiredPfd = SafetyInstrumentedFunction.calculateRequiredPfd(current, target);
        gap.addProperty("requiredAdditionalPFD", round(requiredPfd, 6));
      }
      out.add("gapAnalysis", gap);
      return GSON.toJson(out);
    } catch (Exception e) {
      return errorJson("LOPA calculation failed: " + e.getMessage());
    }
  }

  /**
   * Reads a required double field from JSON.
   *
   * @param input JSON object
   * @param field field name
   * @return field value
   */
  private static double req(JsonObject input, String field) {
    if (!input.has(field)) {
      throw new IllegalArgumentException("Missing required field: " + field);
    }
    return input.get(field).getAsDouble();
  }

  /**
   * Rounds a value.
   *
   * @param value value
   * @param decimals decimals
   * @return rounded value
   */
  private static double round(double value, int decimals) {
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }

  /**
   * Error JSON.
   *
   * @param message message
   * @return JSON string
   */
  private static String errorJson(String message) {
    JsonObject err = new JsonObject();
    err.addProperty("status", "error");
    err.addProperty("message", message);
    return err.toString();
  }
}
