package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.safety.risk.RiskMatrix;

/**
 * MCP runner for 5x5 risk-matrix scoring per ISO 31000 / NORSOK Z-013.
 *
 * <p>
 * Accepts a list of risk events with either:
 * <ul>
 * <li>{@code failuresPerYear} + {@code productionLossPercent} — categorised via
 * {@link RiskMatrix.ProbabilityCategory#fromFrequency(double)} and
 * {@link RiskMatrix.ConsequenceCategory#fromProductionLoss(double)}, or</li>
 * <li>explicit {@code probabilityLevel} (1-5) and {@code consequenceLevel} (1-5).</li>
 * </ul>
 * Returns risk score (P × C), risk level (LOW/MEDIUM/HIGH/CRITICAL) and recommended colour.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RiskMatrixRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private RiskMatrixRunner() {}

  /**
   * Runs a risk-matrix scoring from a JSON definition.
   *
   * @param json JSON with events array
   * @return JSON string with scored matrix
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      if (!input.has("events")) {
        return errorJson("Missing required field: events (array)");
      }
      JsonArray events = input.getAsJsonArray("events");

      JsonArray scored = new JsonArray();
      int maxScore = 0;
      String overallLevel = "LOW";
      String overallColor = "green";

      for (JsonElement el : events) {
        JsonObject ev = el.getAsJsonObject();
        String name = ev.has("name") ? ev.get("name").getAsString() : "event";

        RiskMatrix.ProbabilityCategory pCat;
        RiskMatrix.ConsequenceCategory cCat;
        if (ev.has("probabilityLevel") && ev.has("consequenceLevel")) {
          pCat = probabilityFromLevel(ev.get("probabilityLevel").getAsInt());
          cCat = consequenceFromLevel(ev.get("consequenceLevel").getAsInt());
        } else if (ev.has("failuresPerYear") && ev.has("productionLossPercent")) {
          pCat =
              RiskMatrix.ProbabilityCategory.fromFrequency(ev.get("failuresPerYear").getAsDouble());
          cCat = RiskMatrix.ConsequenceCategory
              .fromProductionLoss(ev.get("productionLossPercent").getAsDouble());
        } else {
          return errorJson("Each event needs (probabilityLevel + consequenceLevel) "
              + "or (failuresPerYear + productionLossPercent)");
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
        if (ev.has("mitigation")) {
          row.add("mitigation", ev.get("mitigation"));
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
      out.addProperty("standard", "ISO 31000 / NORSOK Z-013 (5x5 matrix)");
      out.addProperty("eventCount", events.size());
      JsonObject overall = new JsonObject();
      overall.addProperty("maxScore", maxScore);
      overall.addProperty("riskLevel", overallLevel);
      overall.addProperty("color", overallColor);
      out.add("overall", overall);
      out.add("events", scored);
      return GSON.toJson(out);
    } catch (Exception e) {
      return errorJson("Risk matrix scoring failed: " + e.getMessage());
    }
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
        throw new IllegalArgumentException("probabilityLevel must be 1-5, got: " + level);
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
        throw new IllegalArgumentException("consequenceLevel must be 1-5, got: " + level);
    }
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
