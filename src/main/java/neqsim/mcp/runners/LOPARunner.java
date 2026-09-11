package neqsim.mcp.runners;

import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/**
 * MCP runner for bounded Layer of Protection Analysis (LOPA) screening.
 *
 * <p>
 * Computes the mitigated event frequency from caller-supplied initiating-event frequency and probability-of-failure-on-
 * demand values. The calculation uses NeqSim's canonical {@link LOPAResult}; it does not establish that a protection
 * layer is independent or suitable, verify SIL, determine risk acceptance, or claim standards compliance.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.1
 */
public final class LOPARunner {

  /** Maximum accepted serialized request size. */
  private static final int MAX_REQUEST_BYTES = 16384;
  /** Maximum number of protection layers in one request. */
  private static final int MAX_LAYERS = 100;
  /** Maximum trimmed scenario or layer-name length. */
  private static final int MAX_NAME_LENGTH = 256;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private LOPARunner() {
  }

  /**
   * Runs a bounded LOPA screening calculation from a JSON definition.
   *
   * @param json JSON with scenario, initiatingEventFrequency, targetFrequency and layers array
   * @return JSON string with canonical LOPA result and screening boundary
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INVALID_INPUT", "JSON input is null or empty");
    }
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
      return errorJson("REQUEST_TOO_LARGE", "LOPA input exceeds 16384 UTF-8 bytes");
    }
    try {
      JsonElement parsed = JsonParser.parseString(json);
      if (!parsed.isJsonObject()) {
        return errorJson("INVALID_INPUT", "LOPA input must be a JSON object");
      }
      JsonObject input = parsed.getAsJsonObject();
      String scenarioName = readBoundedString(input, "scenario", "unnamed scenario");
      if (scenarioName == null) {
        return errorJson("INVALID_INPUT", "scenario must be a non-blank string of at most 256 characters");
      }
      double initiating = readPositiveFiniteNumber(input, "initiatingEventFrequency_per_year");
      double target = readPositiveFiniteNumber(input, "targetFrequency_per_year");

      if (!input.has("layers") || !input.get("layers").isJsonArray()) {
        return errorJson("INVALID_INPUT", "Missing required field: layers (array)");
      }
      JsonArray layers = input.getAsJsonArray("layers");
      if (layers.size() == 0) {
        return errorJson("INVALID_INPUT", "At least one protection layer is required");
      }
      if (layers.size() > MAX_LAYERS) {
        return errorJson("TOO_MANY_LAYERS", "At most 100 protection layers are allowed");
      }

      LOPAResult lopa = new LOPAResult(scenarioName);
      lopa.setInitiatingEventFrequency(initiating);
      lopa.setTargetFrequency(target);

      double current = initiating;
      for (int i = 0; i < layers.size(); i++) {
        JsonElement element = layers.get(i);
        if (!element.isJsonObject()) {
          return errorJson("INVALID_LAYER", "layers[" + i + "] must be a JSON object");
        }
        JsonObject layer = element.getAsJsonObject();
        String name = readBoundedString(layer, "name", null);
        if (name == null) {
          return errorJson("INVALID_LAYER",
              "layers[" + i + "].name must be a non-blank string of at most 256 characters");
        }
        double pfd = readFiniteNumber(layer, "pfd", "layers[" + i + "].pfd");
        if (pfd <= 0.0 || pfd > 1.0) {
          return errorJson("INVALID_LAYER", "layers[" + i + "].pfd must be greater than 0 and at most 1");
        }
        double before = current;
        double after = before * pfd;
        if (!Double.isFinite(after) || after == 0.0) {
          return errorJson("CALCULATION_OUT_OF_RANGE",
              "LOPA layer calculation is outside the finite representable range");
        }
        lopa.addLayer(name, pfd, before, after);
        current = after;
      }
      lopa.setMitigatedFrequency(current);

      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("screeningOnly", true);
      out.addProperty("standardConformanceClaimed", false);
      out.addProperty("standard", "Caller-supplied LOPA screening; project-specific verification required");
      out.addProperty("standardContext",
          "IEC 61511 and CCPS LOPA are context only; this result does not demonstrate conformance");
      out.addProperty("inputBasis", "CALLER_SUPPLIED_FREQUENCIES_AND_LAYER_PFDS");
      out.addProperty("advisoryBoundary",
          "The caller supplies frequencies, layer PFDs, and claimed IPL independence; the result does not identify hazards, validate safeguards or independence, verify SIL, determine risk acceptance, certify compliance, or authorize plant action");
      JsonArray assumptions = new JsonArray();
      assumptions.add("Initiating-event frequency and target frequency are caller supplied and unverified");
      assumptions.add(
          "Layer PFDs, independence, common-cause assumptions, and human reliability are caller supplied and unverified");
      assumptions
          .add("Proof-test intervals, consequences, safeguards, and project risk criteria require qualified review");
      out.add("assumptions", assumptions);
      out.add("lopa", JsonParser.parseString(lopa.toJson()));

      JsonObject gap = new JsonObject();
      gap.addProperty("targetMet", lopa.isTargetMet());
      gap.addProperty("gapToTarget_per_year", round(lopa.getGapToTarget(), 12));
      gap.addProperty("totalRRF", round(lopa.getTotalRRF(), 2));
      if (!lopa.isTargetMet()) {
        gap.addProperty("requiredAdditionalRRF", round(lopa.getRequiredAdditionalRRF(), 2));
        gap.addProperty("requiredAdditionalSIL", lopa.getRequiredAdditionalSIL());
        gap.addProperty("requiredAdditionalSILIsIndicative", true);
        double requiredPfd = SafetyInstrumentedFunction.calculateRequiredPfd(current, target);
        gap.addProperty("requiredAdditionalPFD", round(requiredPfd, 6));
      }
      out.add("gapAnalysis", gap);
      return GSON.toJson(out);
    } catch (IllegalArgumentException e) {
      return errorJson("INVALID_INPUT", e.getMessage());
    } catch (Exception e) {
      return errorJson("INVALID_INPUT", "LOPA input could not be processed");
    }
  }

  /**
   * Reads and trims a bounded string field.
   *
   * @param object source object
   * @param field field name
   * @param defaultValue value when the field is absent
   * @return bounded string, default value, or {@code null} when invalid
   */
  private static String readBoundedString(JsonObject object, String field, String defaultValue) {
    if (!object.has(field)) {
      return defaultValue;
    }
    JsonElement value = object.get(field);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      return null;
    }
    String text = value.getAsString().trim();
    if (text.isEmpty() || text.length() > MAX_NAME_LENGTH) {
      return null;
    }
    return text;
  }

  /**
   * Reads a required positive finite numeric field.
   *
   * @param input source object
   * @param field field name
   * @return positive finite value
   */
  private static double readPositiveFiniteNumber(JsonObject input, String field) {
    double value = readFiniteNumber(input, field, field);
    if (value <= 0.0) {
      throw new IllegalArgumentException(field + " must be greater than 0");
    }
    return value;
  }

  /**
   * Reads a required finite numeric field.
   *
   * @param input source object
   * @param field field name
   * @param displayName stable field path for errors
   * @return finite value
   */
  private static double readFiniteNumber(JsonObject input, String field, String displayName) {
    if (!input.has(field)) {
      throw new IllegalArgumentException("Missing required field: " + displayName);
    }
    JsonElement element = input.get(field);
    if (!element.isJsonPrimitive()) {
      throw new IllegalArgumentException(displayName + " must be a finite number");
    }
    JsonPrimitive primitive = element.getAsJsonPrimitive();
    if (!primitive.isNumber()) {
      throw new IllegalArgumentException(displayName + " must be a finite number");
    }
    double value = primitive.getAsDouble();
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(displayName + " must be a finite number");
    }
    return value;
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
   * Returns a stable fail-closed error response.
   *
   * @param code stable error code
   * @param message sanitized message
   * @return JSON error response
   */
  private static String errorJson(String code, String message) {
    JsonObject err = new JsonObject();
    err.addProperty("status", "error");
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("screeningOnly", true);
    err.addProperty("standardConformanceClaimed", false);
    return err.toString();
  }
}
