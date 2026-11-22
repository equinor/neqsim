package neqsim.mcp.runners;

import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import neqsim.process.safety.risk.RiskMatrix;

/**
 * MCP runner for bounded 5x5 risk-matrix screening.
 *
 * <p>
 * Accepts a list of risk events with either:
 * <ul>
 * <li>{@code failuresPerYear} + {@code productionLossPercent} — categorised via
 * {@link RiskMatrix.ProbabilityCategory#fromFrequency(double)} and
 * {@link RiskMatrix.ConsequenceCategory#fromProductionLoss(double)}, or</li>
 * <li>explicit {@code probabilityLevel} (1-5) and {@code consequenceLevel} (1-5).</li>
 * </ul>
 * Returns risk score (P × C), risk level (LOW/MEDIUM/HIGH/CRITICAL) and display colour. The categories are generic
 * screening defaults and are not evidence of ISO 31000 or NORSOK Z-013 compliance. A qualified safety engineer must
 * select project-specific criteria and review the result.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RiskMatrixRunner {

  /** Maximum accepted serialized request size. */
  private static final int MAX_REQUEST_BYTES = 16384;
  /** Maximum number of events in one request. */
  private static final int MAX_EVENTS = 100;
  /** Maximum trimmed event-name length. */
  private static final int MAX_NAME_LENGTH = 256;
  /** Maximum trimmed mitigation-text length. */
  private static final int MAX_MITIGATION_LENGTH = 2048;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private RiskMatrixRunner() {
  }

  /**
   * Runs a risk-matrix scoring from a JSON definition.
   *
   * @param json JSON with events array
   * @return JSON string with scored matrix
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INVALID_INPUT", "JSON input is null or empty");
    }
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
      return errorJson("REQUEST_TOO_LARGE", "Risk-matrix input exceeds 16384 UTF-8 bytes");
    }
    try {
      JsonElement parsed = JsonParser.parseString(json);
      if (!parsed.isJsonObject()) {
        return errorJson("INVALID_INPUT", "Risk-matrix input must be a JSON object");
      }
      JsonObject input = parsed.getAsJsonObject();
      if (!input.has("events") || !input.get("events").isJsonArray()) {
        return errorJson("INVALID_INPUT", "Missing required field: events (array)");
      }
      JsonArray events = input.getAsJsonArray("events");
      if (events.size() == 0) {
        return errorJson("INVALID_INPUT", "At least one risk event is required");
      }
      if (events.size() > MAX_EVENTS) {
        return errorJson("TOO_MANY_EVENTS", "At most 100 risk events are allowed");
      }

      JsonArray scored = new JsonArray();
      int maxScore = 0;
      String overallLevel = "LOW";
      String overallColor = "green";

      for (int i = 0; i < events.size(); i++) {
        JsonElement el = events.get(i);
        if (!el.isJsonObject()) {
          return errorJson("INVALID_EVENT", "events[" + i + "] must be a JSON object");
        }
        JsonObject ev = el.getAsJsonObject();
        String name = readBoundedString(ev, "name", "Event " + (i + 1), MAX_NAME_LENGTH);
        if (name == null) {
          return errorJson("INVALID_EVENT",
              "events[" + i + "].name must be a non-blank string of at most 256 characters");
        }

        boolean hasExplicitProbability = ev.has("probabilityLevel");
        boolean hasExplicitConsequence = ev.has("consequenceLevel");
        boolean hasFrequency = ev.has("failuresPerYear");
        boolean hasProductionLoss = ev.has("productionLossPercent");
        boolean explicitMode = hasExplicitProbability && hasExplicitConsequence;
        boolean measuredMode = hasFrequency && hasProductionLoss;
        if (explicitMode == measuredMode || hasExplicitProbability != hasExplicitConsequence
            || hasFrequency != hasProductionLoss) {
          return errorJson("INVALID_EVENT", "events[" + i
              + "] must provide exactly one complete input mode: probabilityLevel plus consequenceLevel, or failuresPerYear plus productionLossPercent");
        }

        RiskMatrix.ProbabilityCategory pCat;
        RiskMatrix.ConsequenceCategory cCat;
        String inputBasis;
        if (explicitMode) {
          pCat = probabilityFromLevel(readLevel(ev, "probabilityLevel", i));
          cCat = consequenceFromLevel(readLevel(ev, "consequenceLevel", i));
          inputBasis = "CALLER_SUPPLIED_LEVELS";
        } else {
          double failuresPerYear = readFiniteNumber(ev, "failuresPerYear", i);
          double productionLossPercent = readFiniteNumber(ev, "productionLossPercent", i);
          if (failuresPerYear < 0.0) {
            return errorJson("INVALID_EVENT", "events[" + i + "].failuresPerYear must be non-negative");
          }
          if (productionLossPercent < 0.0 || productionLossPercent > 100.0) {
            return errorJson("INVALID_EVENT", "events[" + i + "].productionLossPercent must be between 0 and 100");
          }
          pCat = RiskMatrix.ProbabilityCategory.fromFrequency(failuresPerYear);
          cCat = RiskMatrix.ConsequenceCategory.fromProductionLoss(productionLossPercent);
          inputBasis = "CALLER_SUPPLIED_FREQUENCY_AND_PRODUCTION_LOSS";
        }

        int score = pCat.getLevel() * cCat.getLevel();
        RiskMatrix.RiskLevel level = RiskMatrix.RiskLevel.fromScore(score);

        JsonObject row = new JsonObject();
        row.addProperty("name", name);
        row.addProperty("probabilityLevel", pCat.getLevel());
        row.addProperty("probabilityCategory", pCat.getName());
        row.addProperty("consequenceLevel", cCat.getLevel());
        row.addProperty("consequenceCategory", cCat.getName());
        row.addProperty("riskScore", score);
        row.addProperty("riskLevel", level.getName());
        row.addProperty("color", level.getColor());
        row.addProperty("inputBasis", inputBasis);
        if (ev.has("mitigation")) {
          String mitigation = readBoundedString(ev, "mitigation", null, MAX_MITIGATION_LENGTH);
          if (mitigation == null) {
            return errorJson("INVALID_EVENT",
                "events[" + i + "].mitigation must be a string of at most 2048 characters");
          }
          row.addProperty("mitigation", mitigation);
        }
        scored.add(row);

        if (score > maxScore) {
          maxScore = score;
          overallLevel = level.getName();
          overallColor = level.getColor();
        }
      }

      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("screeningOnly", true);
      out.addProperty("standardConformanceClaimed", false);
      out.addProperty("standard", "Generic 5x5 screening; project-specific verification required");
      out.addProperty("standardContext",
          "Generic 5x5 screening only; ISO 31000 and NORSOK Z-013 require project-specific criteria and qualified review");
      out.addProperty("advisoryBoundary",
          "The caller supplies the probability and consequence basis; this score does not validate hazards, safeguards, risk acceptance, compliance, or plant actions");
      out.addProperty("eventCount", events.size());
      JsonObject overall = new JsonObject();
      overall.addProperty("maxScore", maxScore);
      overall.addProperty("riskLevel", overallLevel);
      overall.addProperty("color", overallColor);
      out.add("overall", overall);
      out.add("events", scored);
      return GSON.toJson(out);
    } catch (IllegalArgumentException e) {
      return errorJson("INVALID_EVENT", e.getMessage());
    } catch (Exception e) {
      return errorJson("INVALID_INPUT", "Risk-matrix input could not be processed");
    }
  }

  /**
   * Reads and trims a bounded string field.
   *
   * @param object source object
   * @param field field name
   * @param defaultValue value when the field is absent
   * @param maxLength maximum accepted character count
   * @return bounded string, default value, or {@code null} when invalid
   */
  private static String readBoundedString(JsonObject object, String field, String defaultValue, int maxLength) {
    if (!object.has(field)) {
      return defaultValue;
    }
    JsonElement value = object.get(field);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      return null;
    }
    String text = value.getAsString().trim();
    if (("name".equals(field) && text.isEmpty()) || text.length() > maxLength) {
      return null;
    }
    return text;
  }

  /**
   * Reads one integral risk-matrix level.
   *
   * @param event event object
   * @param field field name
   * @param eventIndex zero-based event index
   * @return level from 1 through 5
   * @throws IllegalArgumentException when the field is not an integer from 1 through 5
   */
  private static int readLevel(JsonObject event, String field, int eventIndex) {
    JsonElement value = event.get(field);
    if (!value.isJsonPrimitive()) {
      throw new IllegalArgumentException("events[" + eventIndex + "]." + field + " must be an integer from 1 to 5");
    }
    JsonPrimitive primitive = value.getAsJsonPrimitive();
    if (!primitive.isNumber()) {
      throw new IllegalArgumentException("events[" + eventIndex + "]." + field + " must be an integer from 1 to 5");
    }
    double number = primitive.getAsDouble();
    if (!Double.isFinite(number) || number != Math.rint(number) || number < 1.0 || number > 5.0) {
      throw new IllegalArgumentException("events[" + eventIndex + "]." + field + " must be an integer from 1 to 5");
    }
    return (int) number;
  }

  /**
   * Reads one finite numeric event field.
   *
   * @param event event object
   * @param field field name
   * @param eventIndex zero-based event index
   * @return finite numeric value
   * @throws IllegalArgumentException when the field is not a finite number
   */
  private static double readFiniteNumber(JsonObject event, String field, int eventIndex) {
    JsonElement value = event.get(field);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException("events[" + eventIndex + "]." + field + " must be a finite number");
    }
    double number = value.getAsDouble();
    if (!Double.isFinite(number)) {
      throw new IllegalArgumentException("events[" + eventIndex + "]." + field + " must be a finite number");
    }
    return number;
  }

  /**
   * Maps integer level (1-5) to {@link RiskMatrix.ProbabilityCategory}.
   *
   * @param level 1-5
   * @return matching probability category
   */
  private static RiskMatrix.ProbabilityCategory probabilityFromLevel(int level) {
    switch (level) {
    case 1:
      return RiskMatrix.ProbabilityCategory.VERY_LOW;
    case 2:
      return RiskMatrix.ProbabilityCategory.LOW;
    case 3:
      return RiskMatrix.ProbabilityCategory.MEDIUM;
    case 4:
      return RiskMatrix.ProbabilityCategory.HIGH;
    case 5:
      return RiskMatrix.ProbabilityCategory.VERY_HIGH;
    default:
      throw new IllegalArgumentException("probabilityLevel must be an integer from 1 to 5");
    }
  }

  /**
   * Maps integer level (1-5) to {@link RiskMatrix.ConsequenceCategory}.
   *
   * @param level 1-5
   * @return matching consequence category
   */
  private static RiskMatrix.ConsequenceCategory consequenceFromLevel(int level) {
    switch (level) {
    case 1:
      return RiskMatrix.ConsequenceCategory.NEGLIGIBLE;
    case 2:
      return RiskMatrix.ConsequenceCategory.MINOR;
    case 3:
      return RiskMatrix.ConsequenceCategory.MODERATE;
    case 4:
      return RiskMatrix.ConsequenceCategory.MAJOR;
    case 5:
      return RiskMatrix.ConsequenceCategory.CATASTROPHIC;
    default:
      throw new IllegalArgumentException("consequenceLevel must be an integer from 1 to 5");
    }
  }

  /**
   * Error JSON.
   *
   * @param code stable error code
   * @param message message
   * @return JSON string
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
