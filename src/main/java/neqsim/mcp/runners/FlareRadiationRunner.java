package neqsim.mcp.runners;

import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.flare.Flare;

/**
 * Bounded MCP runner for flare-tip thermal-radiation screening.
 *
 * <p>
 * The calculation delegates to the canonical {@link Flare} model. Results are screening evidence only and do not
 * establish standards compliance, safe siting, or accountable engineering approval.
 * </p>
 */
public final class FlareRadiationRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
  private static final int MAX_REQUEST_BYTES = 16384;
  private static final int MAX_DISTANCES = 200;
  private static final double MAX_HEAT_DUTY_W = 1.0e12;
  private static final double MAX_FLAME_HEIGHT_M = 1000.0;
  private static final double MAX_DISTANCE_M = 100000.0;

  /** API 521 reference thresholds (W/m2). */
  private static final double[] API521_THRESHOLDS_W_M2 = {1580.0, 4730.0, 6310.0, 9460.0};

  /** Threshold descriptions used for the screening contour. */
  private static final String[] API521_LABELS = {"1.58 kW/m2 (continuous personnel exposure)",
      "4.73 kW/m2 (3 s emergency egress)", "6.31 kW/m2 (limited personnel access)", "9.46 kW/m2 (equipment limit)"};

  private FlareRadiationRunner() {
  }

  /**
   * Runs bounded flare-radiation screening from a caller-supplied JSON definition.
   *
   * @param json JSON with exactly one heat-duty field and optional model parameters
   * @return JSON response with screening results or a stable error code
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INVALID_INPUT", "JSON input is null or empty");
    }
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
      return errorJson("REQUEST_TOO_LARGE", "Request exceeds the 16384 UTF-8 byte admission limit");
    }

    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      boolean hasMegawatts = input.has("heatDuty_MW");
      boolean hasWatts = input.has("heatDuty_W");
      if (hasMegawatts == hasWatts) {
        return errorJson("INVALID_HEAT_DUTY", "Provide exactly one of heatDuty_MW or heatDuty_W");
      }

      double heatDutyW = hasMegawatts ? input.get("heatDuty_MW").getAsDouble() * 1.0e6
          : input.get("heatDuty_W").getAsDouble();
      if (!finitePositive(heatDutyW) || heatDutyW > MAX_HEAT_DUTY_W) {
        return errorJson("INVALID_HEAT_DUTY", "Heat duty must be finite, positive, and no greater than 1.0e12 W");
      }

      Flare flare = new Flare("flare-radiation");
      if (input.has("flameHeight_m")) {
        double flameHeight = input.get("flameHeight_m").getAsDouble();
        if (!finitePositive(flameHeight) || flameHeight > MAX_FLAME_HEIGHT_M) {
          return errorJson("INVALID_FLAME_HEIGHT",
              "flameHeight_m must be finite, positive, and no greater than 1000 m");
        }
        flare.setFlameHeight(flameHeight);
      }
      if (input.has("radiantFraction")) {
        double radiantFraction = input.get("radiantFraction").getAsDouble();
        if (!finitePositive(radiantFraction) || radiantFraction > 1.0) {
          return errorJson("INVALID_RADIANT_FRACTION", "radiantFraction must be finite and in the interval (0, 1]");
        }
        flare.setRadiantFraction(radiantFraction);
      }

      double[] distances = readDistances(input);
      if (distances == null) {
        return errorJson("INVALID_DISTANCES", "distances_m must contain between 1 and 200 numeric distances");
      }
      for (double distance : distances) {
        if (!finitePositive(distance) || distance > MAX_DISTANCE_M) {
          return errorJson("INVALID_DISTANCE", "Each distance must be finite, positive, and no greater than 100000 m");
        }
      }

      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("heatDuty_MW", round(heatDutyW / 1.0e6, 3));
      addAdvisoryMetadata(out);

      JsonArray distancesOut = new JsonArray();
      for (double distance : distances) {
        double flux = flare.estimateRadiationHeatFlux(heatDutyW, distance);
        JsonObject row = new JsonObject();
        row.addProperty("distance_m", distance);
        row.addProperty("flux_W_m2", round(flux, 1));
        row.addProperty("flux_kW_m2", round(flux / 1000.0, 3));
        distancesOut.add(row);
      }
      out.add("radiationProfile", distancesOut);

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
    } catch (Exception exception) {
      return errorJson("INVALID_INPUT", "Flare-radiation input could not be processed: " + exception.getMessage());
    }
  }

  private static double[] readDistances(JsonObject input) {
    if (!input.has("distances_m")) {
      return new double[] {15.0, 30.0, 50.0, 75.0, 100.0, 150.0, 200.0};
    }
    if (!input.get("distances_m").isJsonArray()) {
      return null;
    }
    JsonArray values = input.getAsJsonArray("distances_m");
    if (values.size() < 1 || values.size() > MAX_DISTANCES) {
      return null;
    }
    double[] distances = new double[values.size()];
    for (int i = 0; i < values.size(); i++) {
      distances[i] = values.get(i).getAsDouble();
    }
    return distances;
  }

  private static boolean finitePositive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static double round(double value, int decimals) {
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }

  private static void addAdvisoryMetadata(JsonObject response) {
    response.addProperty("inputBasis", "CALLER_SUPPLIED");
    response.addProperty("screeningOnly", true);
    response.addProperty("standardConformanceClaimed", false);
    response.addProperty("engineeringReviewRequired", true);
    response.addProperty("modelBoundary",
        "Canonical NeqSim Flare point-source screening model; no dispersion, wind, terrain, shielding, multi-flare interaction, mechanical design, or project-specific siting qualification");
  }

  private static String errorJson(String code, String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("errorCode", code);
    error.addProperty("message", message);
    addAdvisoryMetadata(error);
    return error.toString();
  }
}
