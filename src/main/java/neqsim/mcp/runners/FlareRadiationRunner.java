package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.flare.Flare;

/**
 * MCP runner for flare-tip radiation analysis per API 521 §6 / API 537.
 *
 * <p>
 * Computes the radiant heat flux at user-specified ground distances and the safe distance to a
 * given flux threshold (default API 521 thresholds: 1.58 / 4.73 / 6.31 / 9.46 kW/m²).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FlareRadiationRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** API 521 thresholds (W/m²). */
  private static final double[] API521_THRESHOLDS_W_M2 = {1580.0, 4730.0, 6310.0, 9460.0};

  /** API 521 threshold descriptions. */
  private static final String[] API521_LABELS =
      {"1.58 kW/m² (continuous personnel exposure)", "4.73 kW/m² (3 s emergency egress)",
          "6.31 kW/m² (limited personnel access)", "9.46 kW/m² (equipment limit)"};

  private FlareRadiationRunner() {}

  /**
   * Runs flare radiation analysis from a JSON definition.
   *
   * @param json JSON with heatDuty, optional flameHeight / radiantFraction / distances
   * @return JSON string with radiation results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      double heatDutyW;
      if (input.has("heatDuty_MW")) {
        heatDutyW = input.get("heatDuty_MW").getAsDouble() * 1.0e6;
      } else if (input.has("heatDuty_W")) {
        heatDutyW = input.get("heatDuty_W").getAsDouble();
      } else {
        return errorJson("Missing required field: heatDuty_MW or heatDuty_W");
      }

      Flare flare = new Flare("flare-radiation");
      if (input.has("flameHeight_m")) {
        flare.setFlameHeight(input.get("flameHeight_m").getAsDouble());
      }
      if (input.has("radiantFraction")) {
        flare.setRadiantFraction(input.get("radiantFraction").getAsDouble());
      }

      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("standard", "API 521 §6 / API 537");
      out.addProperty("heatDuty_MW", round(heatDutyW / 1.0e6, 3));

      // Per-distance flux table
      JsonArray distancesOut = new JsonArray();
      double[] distances;
      if (input.has("distances_m")) {
        JsonArray d = input.getAsJsonArray("distances_m");
        distances = new double[d.size()];
        for (int i = 0; i < d.size(); i++) {
          distances[i] = d.get(i).getAsDouble();
        }
      } else {
        distances = new double[] {15.0, 30.0, 50.0, 75.0, 100.0, 150.0, 200.0};
      }
      for (double d : distances) {
        double flux = flare.estimateRadiationHeatFlux(heatDutyW, d);
        JsonObject row = new JsonObject();
        row.addProperty("distance_m", d);
        row.addProperty("flux_W_m2", round(flux, 1));
        row.addProperty("flux_kW_m2", round(flux / 1000.0, 3));
        distancesOut.add(row);
      }
      out.add("radiationProfile", distancesOut);

      // Safe-distance table for API 521 thresholds
      JsonArray contour = new JsonArray();
      for (int i = 0; i < API521_THRESHOLDS_W_M2.length; i++) {
        double safeDistance = flare.radiationDistanceForFlux(heatDutyW, API521_THRESHOLDS_W_M2[i]);
        JsonObject row = new JsonObject();
        row.addProperty("threshold_W_m2", API521_THRESHOLDS_W_M2[i]);
        row.addProperty("threshold_kW_m2", API521_THRESHOLDS_W_M2[i] / 1000.0);
        row.addProperty("description", API521_LABELS[i]);
        row.addProperty("safeGroundDistance_m", round(safeDistance, 2));
        contour.add(row);
      }
      out.add("safeDistanceContour", contour);

      return GSON.toJson(out);
    } catch (Exception e) {
      return errorJson("Flare radiation calculation failed: " + e.getMessage());
    }
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
