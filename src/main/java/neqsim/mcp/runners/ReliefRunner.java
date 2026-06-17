package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.process.util.fire.ReliefValveSizing.PSVSizingResult;
import neqsim.process.util.fire.ReliefValveSizing.LiquidPSVSizingResult;

/**
 * MCP runner for Pressure Safety Valve (PSV) sizing per API 520 / API 521.
 *
 * <p>
 * Supports four cases:
 * <ul>
 * <li>{@code gas} — gas/vapour service (API 520 §5)</li>
 * <li>{@code liquid} — liquid relief (API 520 §5.8)</li>
 * <li>{@code twoPhase} — two-phase Leung omega method (API 520 Appendix D)</li>
 * <li>{@code fireHeatInput} — API 521 wetted-area fire heat absorption</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ReliefRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private ReliefRunner() {}

  /**
   * Runs a PSV sizing calculation from a JSON definition.
   *
   * @param json JSON with case and case-specific parameters
   * @return JSON string with sizing results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String reliefCase = input.has("case") ? input.get("case").getAsString() : "gas";
      switch (reliefCase) {
        case "gas":
          return sizeGas(input);
        case "liquid":
          return sizeLiquid(input);
        case "twoPhase":
        case "two_phase":
          return sizeTwoPhase(input);
        case "fireHeatInput":
        case "fire":
          return fireHeatInput(input);
        default:
          return errorJson(
              "Unknown case: " + reliefCase + ". Supported: gas, liquid, twoPhase, fireHeatInput");
      }
    } catch (Exception e) {
      return errorJson("Relief sizing failed: " + e.getMessage());
    }
  }

  /**
   * Sizes a gas/vapour PSV per API 520.
   *
   * @param input JSON with sizing inputs
   * @return JSON string with results
   */
  private static String sizeGas(JsonObject input) {
    double massFlow = req(input, "massFlowRate_kg_s");
    double setPressureBara = req(input, "setPressure_bara");
    double overpressureFraction =
        input.has("overpressureFraction") ? input.get("overpressureFraction").getAsDouble() : 0.21;
    double backPressureBara =
        input.has("backPressure_bara") ? input.get("backPressure_bara").getAsDouble() : 1.0;
    double tempK = req(input, "temperature_K");
    double mwKgPerMol = req(input, "molecularWeight_kg_mol");
    double z = input.has("compressibility") ? input.get("compressibility").getAsDouble() : 1.0;
    double gamma =
        input.has("specificHeatRatio") ? input.get("specificHeatRatio").getAsDouble() : 1.3;
    boolean balancedBellows =
        input.has("balancedBellows") && input.get("balancedBellows").getAsBoolean();
    boolean ruptureDisk = input.has("ruptureDisk") && input.get("ruptureDisk").getAsBoolean();

    PSVSizingResult res = ReliefValveSizing.calculateRequiredArea(massFlow, setPressureBara * 1.0e5,
        overpressureFraction, backPressureBara * 1.0e5, tempK, mwKgPerMol, z, gamma,
        balancedBellows, ruptureDisk);

    JsonObject out = new JsonObject();
    out.addProperty("status", "success");
    out.addProperty("case", "gas");
    out.addProperty("standard", "API 520 / API 521");

    JsonObject sizing = new JsonObject();
    sizing.addProperty("requiredArea_mm2", round(res.getRequiredArea() * 1.0e6, 2));
    sizing.addProperty("requiredArea_in2", round(res.getRequiredAreaIn2(), 4));
    sizing.addProperty("recommendedOrifice", res.getRecommendedOrifice());
    sizing.addProperty("selectedArea_mm2", round(res.getSelectedArea() * 1.0e6, 2));
    sizing.addProperty("selectedArea_in2", round(res.getSelectedAreaIn2(), 4));
    sizing.addProperty("massFlowCapacity_kg_s", round(res.getMassFlowCapacity(), 4));
    out.add("sizing", sizing);

    JsonObject factors = new JsonObject();
    factors.addProperty("Kd_dischargeCoefficient", round(res.getDischargeCoefficient(), 3));
    factors.addProperty("Kb_backPressureCorrection",
        round(res.getBackPressureCorrectionFactor(), 3));
    factors.addProperty("Kc_combinationCorrection", round(res.getCombinationCorrectionFactor(), 3));
    factors.addProperty("overpressureFraction", res.getOverpressureFraction());
    factors.addProperty("backPressureFraction", round(res.getBackPressureFraction(), 3));
    out.add("correctionFactors", factors);

    out.addProperty("validation", ReliefValveSizing.validateSizing(res, false));
    return GSON.toJson(out);
  }

  /**
   * Sizes a liquid PSV per API 520.
   *
   * @param input JSON with sizing inputs
   * @return JSON string with results
   */
  private static String sizeLiquid(JsonObject input) {
    double volFlow = req(input, "volumeFlowRate_m3_s");
    double rho = req(input, "liquidDensity_kg_m3");
    double setPressureBara = req(input, "setPressure_bara");
    double overpressureFraction =
        input.has("overpressureFraction") ? input.get("overpressureFraction").getAsDouble() : 0.10;
    double backPressureBara =
        input.has("backPressure_bara") ? input.get("backPressure_bara").getAsDouble() : 1.0;
    double mu = input.has("viscosity_Pa_s") ? input.get("viscosity_Pa_s").getAsDouble() : 1.0e-3;
    boolean balancedBellows =
        input.has("balancedBellows") && input.get("balancedBellows").getAsBoolean();

    LiquidPSVSizingResult res =
        ReliefValveSizing.calculateLiquidReliefArea(volFlow, rho, setPressureBara * 1.0e5,
            overpressureFraction, backPressureBara * 1.0e5, mu, balancedBellows);

    JsonObject out = new JsonObject();
    out.addProperty("status", "success");
    out.addProperty("case", "liquid");
    out.addProperty("standard", "API 520 §5.8");

    JsonObject sizing = new JsonObject();
    sizing.addProperty("requiredArea_mm2", round(res.getRequiredAreaM2() * 1.0e6, 2));
    sizing.addProperty("requiredArea_in2", round(res.getRequiredAreaIn2(), 4));
    sizing.addProperty("recommendedOrifice", res.getRecommendedOrifice());
    sizing.addProperty("selectedArea_in2", round(res.getSelectedAreaIn2(), 4));
    sizing.addProperty("massFlowRate_kg_s", round(res.getMassFlowRate(), 4));
    sizing.addProperty("volumeFlowRate_m3_s", round(res.getVolumeFlowRate(), 6));
    out.add("sizing", sizing);

    JsonObject factors = new JsonObject();
    factors.addProperty("Kd_dischargeCoefficient", round(res.getDischargeCoefficient(), 3));
    factors.addProperty("Kw_backPressureCorrection",
        round(res.getBackPressureCorrectionFactor(), 3));
    factors.addProperty("Kv_viscosityCorrection", round(res.getViscosityCorrectionFactor(), 3));
    out.add("correctionFactors", factors);

    return GSON.toJson(out);
  }

  /**
   * Sizes a two-phase PSV using the Leung omega method per API 520 Appendix D.
   *
   * @param input JSON with sizing inputs
   * @return JSON string with results
   */
  private static String sizeTwoPhase(JsonObject input) {
    double massFlow = req(input, "massFlowRate_kg_s");
    double setPressureBara = req(input, "setPressure_bara");
    double overpressureFraction =
        input.has("overpressureFraction") ? input.get("overpressureFraction").getAsDouble() : 0.10;
    double backPressureBara =
        input.has("backPressure_bara") ? input.get("backPressure_bara").getAsDouble() : 1.0;
    double tempK = req(input, "temperature_K");
    double xGas = req(input, "gasMassFraction");
    double rhoGas = req(input, "gasDensity_kg_m3");
    double rhoLiq = req(input, "liquidDensity_kg_m3");
    double latentHeat = req(input, "latentHeat_J_kg");
    double cpLiq = req(input, "liquidCp_J_kgK");

    double area = ReliefValveSizing.calculateTwoPhaseReliefArea(massFlow, setPressureBara * 1.0e5,
        overpressureFraction, backPressureBara * 1.0e5, tempK, xGas, rhoGas, rhoLiq, latentHeat,
        cpLiq);

    JsonObject out = new JsonObject();
    out.addProperty("status", "success");
    out.addProperty("case", "twoPhase");
    out.addProperty("standard", "API 520 Appendix D (Leung omega method)");
    JsonObject sizing = new JsonObject();
    sizing.addProperty("requiredArea_m2", round(area, 8));
    sizing.addProperty("requiredArea_mm2", round(area * 1.0e6, 2));
    sizing.addProperty("requiredArea_in2", round(area / 6.4516e-4, 4));
    out.add("sizing", sizing);
    return GSON.toJson(out);
  }

  /**
   * Calculates API 521 fire heat input on a wetted vessel surface.
   *
   * @param input JSON with wetted area and protection flags
   * @return JSON string with results
   */
  private static String fireHeatInput(JsonObject input) {
    double area = req(input, "wettedArea_m2");
    boolean drainage = input.has("hasDrainage") && input.get("hasDrainage").getAsBoolean();
    boolean fireFighting =
        input.has("hasFireFighting") && input.get("hasFireFighting").getAsBoolean();

    double qW = ReliefValveSizing.calculateAPI521FireHeatInput(area, drainage, fireFighting);

    JsonObject out = new JsonObject();
    out.addProperty("status", "success");
    out.addProperty("case", "fireHeatInput");
    out.addProperty("standard", "API 521 Table 4");
    JsonObject result = new JsonObject();
    result.addProperty("heatInput_W", round(qW, 0));
    result.addProperty("heatInput_kW", round(qW / 1000.0, 2));
    result.addProperty("heatInput_BTU_hr", round(qW / 0.29307107, 0));
    result.addProperty("wettedArea_m2", area);
    result.addProperty("hasDrainage", drainage);
    result.addProperty("hasFireFighting", fireFighting);
    out.add("fireHeatInput", result);
    return GSON.toJson(out);
  }

  /**
   * Reads a required positive double field from JSON.
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
   * Rounds a value to the specified number of decimal places.
   *
   * @param value the value
   * @param decimals decimal places
   * @return rounded value
   */
  private static double round(double value, int decimals) {
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }

  /**
   * Builds an error JSON response.
   *
   * @param message error message
   * @return JSON string
   */
  private static String errorJson(String message) {
    JsonObject err = new JsonObject();
    err.addProperty("status", "error");
    err.addProperty("message", message);
    return err.toString();
  }
}
