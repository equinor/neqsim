package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.pvtsimulation.flowassurance.HydrateRiskMapper;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;

/**
 * Stateless flow assurance runner for MCP integration.
 *
 * <p>
 * Supports hydrate risk mapping, wax appearance temperature, asphaltene stability screening,
 * CO2/H2S corrosion prediction, scale prediction, erosion analysis, pipeline cooldown, and emulsion
 * viscosity calculations.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FlowAssuranceRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private static final List<String> SUPPORTED_ANALYSES = Collections
      .unmodifiableList(Arrays.asList("hydrateRiskMap", "waxAppearance", "asphalteneStability",
          "CO2Corrosion", "scalePrediction", "erosion", "pipelineCooldown", "emulsionViscosity"));

  /**
   * Private constructor — all methods are static.
   */
  private FlowAssuranceRunner() {}

  /**
   * Returns the list of supported flow assurance analysis types.
   *
   * @return unmodifiable list of analysis type names
   */
  public static List<String> getSupportedAnalyses() {
    return SUPPORTED_ANALYSES;
  }

  /**
   * Runs a flow assurance analysis from a JSON input string.
   *
   * @param json the JSON analysis specification
   * @return a JSON string with status and analysis results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON flow assurance specification");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    long startTime = System.currentTimeMillis();

    if (!input.has("analysis")) {
      return errorJson("MISSING_ANALYSIS", "No 'analysis' field specified",
          "Provide 'analysis': one of " + SUPPORTED_ANALYSES);
    }
    String analysis = input.get("analysis").getAsString();
    if (!SUPPORTED_ANALYSES.contains(analysis)) {
      return errorJson("UNKNOWN_ANALYSIS", "Unknown analysis type: " + analysis,
          "Use one of: " + SUPPORTED_ANALYSES);
    }

    try {
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("analysis", analysis);

      JsonObject data;
      switch (analysis) {
        case "hydrateRiskMap":
          data = runHydrateRiskMap(input);
          break;
        case "waxAppearance":
          data = runWaxAppearance(input);
          break;
        case "asphalteneStability":
          data = runAsphalteneStability(input);
          break;
        case "CO2Corrosion":
          data = runCO2Corrosion(input);
          break;
        case "scalePrediction":
          data = runScalePrediction(input);
          break;
        case "erosion":
          data = runErosion(input);
          break;
        case "pipelineCooldown":
          data = runPipelineCooldown(input);
          break;
        case "emulsionViscosity":
          data = runEmulsionViscosity(input);
          break;
        default:
          return errorJson("UNKNOWN_ANALYSIS", "Not implemented: " + analysis, "");
      }

      result.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("flow assurance: " + analysis);
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      result.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("FLOW_ASSURANCE_ERROR", "Analysis failed: " + e.getMessage(),
          "Check input parameters for " + analysis);
    }
  }

  /**
   * Runs hydrate risk mapping along a pipeline profile.
   *
   * @param input the JSON input with fluid and profile data
   * @return JSON object with hydrate risk results
   */
  private static JsonObject runHydrateRiskMap(JsonObject input) {
    SystemInterface fluid = createFluidFromInput(input);
    neqsim.pvtsimulation.flowassurance.HydrateRiskMapper mapper =
        new neqsim.pvtsimulation.flowassurance.HydrateRiskMapper(fluid);
    if (input.has("profilePoints")) {
      JsonArray points = input.getAsJsonArray("profilePoints");
      for (int i = 0; i < points.size(); i++) {
        JsonObject pt = points.get(i).getAsJsonObject();
        mapper.addProfilePoint(pt.get("km").getAsDouble(), pt.get("pressure_bara").getAsDouble(),
            pt.get("temperature_C").getAsDouble());
      }
    }
    HydrateRiskMapper.RiskProfile riskProfile = mapper.calculate();
    return JsonParser.parseString(riskProfile.toJson()).getAsJsonObject();
  }

  /**
   * Runs wax appearance temperature calculation.
   *
   * @param input the JSON input with fluid definition
   * @return JSON object with WAT results
   */
  private static JsonObject runWaxAppearance(JsonObject input) {
    SystemInterface fluid = createFluidFromInput(input);
    neqsim.pvtsimulation.flowassurance.WaxCurveCalculator wax =
        new neqsim.pvtsimulation.flowassurance.WaxCurveCalculator(fluid);
    if (input.has("pressure_bara")) {
      wax.setPressure(input.get("pressure_bara").getAsDouble());
    }
    if (input.has("temperatureRange")) {
      JsonObject range = input.getAsJsonObject("temperatureRange");
      wax.setTemperatureRange(range.get("startC").getAsDouble(), range.get("endC").getAsDouble(),
          range.has("stepC") ? range.get("stepC").getAsDouble() : 5.0);
    }
    wax.calculate();
    JsonObject data = new JsonObject();
    data.addProperty("waxAppearanceTemperature_C", wax.getWaxAppearanceTemperatureC());
    data.addProperty("pressure_bara", wax.getPressureBara());
    data.add("temperatures_C", toJsonArray(wax.getTemperaturesC()));
    data.add("waxWeightFractions", toJsonArray(wax.getWaxWeightFractions()));
    return data;
  }

  /**
   * Runs asphaltene stability assessment.
   *
   * @param input the JSON input with fluid and reservoir conditions
   * @return JSON object with asphaltene stability results
   */
  private static JsonObject runAsphalteneStability(JsonObject input) {
    neqsim.pvtsimulation.flowassurance.AsphalteneStabilityAnalyzer analyzer =
        new neqsim.pvtsimulation.flowassurance.AsphalteneStabilityAnalyzer();
    if (input.has("components")) {
      SystemInterface fluid = createFluidFromInput(input);
      analyzer.setSystem(fluid);
    }
    if (input.has("SARA")) {
      JsonObject sara = input.getAsJsonObject("SARA");
      analyzer.setSARAFractions(sara.get("saturates").getAsDouble(),
          sara.get("aromatics").getAsDouble(), sara.get("resins").getAsDouble(),
          sara.get("asphaltenes").getAsDouble());
    }
    double resPressure =
        input.has("reservoirPressure_bara") ? input.get("reservoirPressure_bara").getAsDouble()
            : 300.0;
    double satPressure =
        input.has("saturationPressure_bara") ? input.get("saturationPressure_bara").getAsDouble()
            : 200.0;
    double density = input.has("density_kg_m3") ? input.get("density_kg_m3").getAsDouble() : 800.0;

    JsonObject data = new JsonObject();
    data.addProperty("deBoerRisk",
        analyzer.deBoerScreening(resPressure, satPressure, density).name());
    data.addProperty("colloidalInstabilityIndex", analyzer.getColloidalInstabilityIndex());
    data.addProperty("resinToAsphalteneRatio", analyzer.getResinToAsphalteneRatio());
    data.addProperty("saraStability", analyzer.evaluateSARAStability().name());
    return data;
  }

  /**
   * Runs CO2 corrosion rate prediction.
   *
   * @param input the JSON input with operating conditions
   * @return JSON object with corrosion rate results
   */
  private static JsonObject runCO2Corrosion(JsonObject input) {
    neqsim.pvtsimulation.flowassurance.CO2CorrosionAnalyzer analyzer =
        new neqsim.pvtsimulation.flowassurance.CO2CorrosionAnalyzer();
    if (input.has("temperature_C")) {
      analyzer.setTemperatureCelsius(input.get("temperature_C").getAsDouble());
    }
    if (input.has("pressure_bara")) {
      analyzer.setPressureBara(input.get("pressure_bara").getAsDouble());
    }
    if (input.has("CO2MoleFraction")) {
      analyzer.setCO2MoleFractionInGas(input.get("CO2MoleFraction").getAsDouble());
    }
    if (input.has("flowVelocity_m_s")) {
      analyzer.setFlowVelocity(input.get("flowVelocity_m_s").getAsDouble());
    }
    if (input.has("pipeDiameter_m")) {
      analyzer.setPipeDiameter(input.get("pipeDiameter_m").getAsDouble());
    }
    if (input.has("inhibitorEfficiency")) {
      analyzer.setInhibitorEfficiency(input.get("inhibitorEfficiency").getAsDouble());
    }
    if (input.has("H2SMoleFraction")) {
      analyzer.setH2SMoleFractionInGas(input.get("H2SMoleFraction").getAsDouble());
    }
    analyzer.run();
    return JsonParser.parseString(analyzer.toJson()).getAsJsonObject();
  }

  /**
   * Runs scale prediction calculation.
   *
   * @param input the JSON input with water chemistry
   * @return JSON object with scale saturation indices
   */
  private static JsonObject runScalePrediction(JsonObject input) {
    neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator calc =
        new neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator();
    if (input.has("temperature_C")) {
      calc.setTemperatureCelsius(input.get("temperature_C").getAsDouble());
    }
    if (input.has("pressure_bara")) {
      calc.setPressureBara(input.get("pressure_bara").getAsDouble());
    }
    if (input.has("calcium_mgL")) {
      calc.setCalciumConcentration(input.get("calcium_mgL").getAsDouble());
    }
    if (input.has("barium_mgL")) {
      calc.setBariumConcentration(input.get("barium_mgL").getAsDouble());
    }
    if (input.has("strontium_mgL")) {
      calc.setStrontiumConcentration(input.get("strontium_mgL").getAsDouble());
    }
    if (input.has("iron_mgL")) {
      calc.setIronConcentration(input.get("iron_mgL").getAsDouble());
    }
    if (input.has("bicarbonate_mgL")) {
      calc.setBicarbonateConcentration(input.get("bicarbonate_mgL").getAsDouble());
    }
    if (input.has("sulphate_mgL")) {
      calc.setSulphateConcentration(input.get("sulphate_mgL").getAsDouble());
    }
    if (input.has("TDS_mgL")) {
      calc.setTotalDissolvedSolids(input.get("TDS_mgL").getAsDouble());
    }
    if (input.has("CO2PartialPressure_bara")) {
      calc.setCO2PartialPressure(input.get("CO2PartialPressure_bara").getAsDouble());
    }
    if (input.has("pH")) {
      calc.setPH(input.get("pH").getAsDouble());
    }
    calc.calculate();
    return JsonParser.parseString(calc.toJson()).getAsJsonObject();
  }

  /**
   * Runs erosion prediction calculation.
   *
   * @param input the JSON input with flow conditions
   * @return JSON object with erosion results
   */
  private static JsonObject runErosion(JsonObject input) {
    neqsim.pvtsimulation.flowassurance.ErosionPredictionCalculator calc =
        new neqsim.pvtsimulation.flowassurance.ErosionPredictionCalculator();
    if (input.has("mixtureDensity_kg_m3")) {
      calc.setMixtureDensity(input.get("mixtureDensity_kg_m3").getAsDouble());
    }
    if (input.has("gasDensity_kg_m3")) {
      calc.setGasDensity(input.get("gasDensity_kg_m3").getAsDouble());
    }
    if (input.has("liquidDensity_kg_m3")) {
      calc.setLiquidDensity(input.get("liquidDensity_kg_m3").getAsDouble());
    }
    if (input.has("mixtureVelocity_m_s")) {
      calc.setMixtureVelocity(input.get("mixtureVelocity_m_s").getAsDouble());
    }
    if (input.has("pipeDiameter_m")) {
      calc.setPipeDiameter(input.get("pipeDiameter_m").getAsDouble());
    }
    if (input.has("wallThickness_mm")) {
      calc.setWallThickness(input.get("wallThickness_mm").getAsDouble());
    }
    if (input.has("sandRate_kg_day")) {
      calc.setSandRate(input.get("sandRate_kg_day").getAsDouble());
    }
    if (input.has("geometry")) {
      calc.setGeometry(input.get("geometry").getAsString());
    }
    calc.calculate();
    return JsonParser.parseString(calc.toJson()).getAsJsonObject();
  }

  /**
   * Runs pipeline cooldown calculation.
   *
   * @param input the JSON input with pipeline thermal parameters
   * @return JSON object with cooldown results
   */
  private static JsonObject runPipelineCooldown(JsonObject input) {
    neqsim.pvtsimulation.flowassurance.PipelineCooldownCalculator calc =
        new neqsim.pvtsimulation.flowassurance.PipelineCooldownCalculator();
    if (input.has("internalDiameter_m")) {
      calc.setInternalDiameter(input.get("internalDiameter_m").getAsDouble());
    }
    if (input.has("wallThickness_m")) {
      calc.setWallThickness(input.get("wallThickness_m").getAsDouble());
    }
    if (input.has("insulationThickness_m")) {
      calc.setInsulationThickness(input.get("insulationThickness_m").getAsDouble());
    }
    if (input.has("insulationConductivity_W_mK")) {
      calc.setInsulationConductivity(input.get("insulationConductivity_W_mK").getAsDouble());
    }
    if (input.has("overallUValue_W_m2K")) {
      calc.setOverallUValue(input.get("overallUValue_W_m2K").getAsDouble());
    }
    if (input.has("initialFluidTemperature_C")) {
      calc.setInitialFluidTemperature(
          input.get("initialFluidTemperature_C").getAsDouble() + 273.15);
    }
    if (input.has("ambientTemperature_C")) {
      calc.setAmbientTemperature(input.get("ambientTemperature_C").getAsDouble() + 273.15);
    }
    if (input.has("fluidDensity_kg_m3")) {
      calc.setFluidDensity(input.get("fluidDensity_kg_m3").getAsDouble());
    }
    if (input.has("fluidSpecificHeat_J_kgK")) {
      calc.setFluidSpecificHeat(input.get("fluidSpecificHeat_J_kgK").getAsDouble());
    }
    if (input.has("totalTimeHours")) {
      calc.setTotalTimeHours(input.get("totalTimeHours").getAsDouble());
    }
    if (input.has("targetTemperature_C")) {
      calc.calculate();
      double targetK = input.get("targetTemperature_C").getAsDouble() + 273.15;
      JsonObject data = JsonParser.parseString(calc.toJson()).getAsJsonObject();
      data.addProperty("timeToTargetTemperature_hours", calc.getTimeToReachTemperature(targetK));
      return data;
    }
    calc.calculate();
    return JsonParser.parseString(calc.toJson()).getAsJsonObject();
  }

  /**
   * Runs emulsion viscosity calculation.
   *
   * @param input the JSON input with emulsion parameters
   * @return JSON object with emulsion viscosity results
   */
  private static JsonObject runEmulsionViscosity(JsonObject input) {
    neqsim.pvtsimulation.flowassurance.EmulsionViscosityCalculator calc =
        new neqsim.pvtsimulation.flowassurance.EmulsionViscosityCalculator();
    if (input.has("oilViscosity_cP")) {
      calc.setOilViscosity(input.get("oilViscosity_cP").getAsDouble());
    }
    if (input.has("waterViscosity_cP")) {
      calc.setWaterViscosity(input.get("waterViscosity_cP").getAsDouble());
    }
    if (input.has("waterCut")) {
      calc.setWaterCut(input.get("waterCut").getAsDouble());
    }
    if (input.has("model")) {
      calc.setModel(input.get("model").getAsString());
    }
    if (input.has("temperature_C")) {
      calc.setTemperatureC(input.get("temperature_C").getAsDouble());
    }
    calc.calculate();
    return JsonParser.parseString(calc.toJson()).getAsJsonObject();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Utility methods
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Creates a fluid system from the JSON input.
   *
   * @param input the JSON object containing components, model, temperature, pressure
   * @return the configured fluid system
   */
  private static SystemInterface createFluidFromInput(JsonObject input) {
    String model = input.has("model") ? input.get("model").getAsString().toUpperCase() : "SRK";
    double tempK = 288.15;
    if (input.has("temperature")) {
      tempK = parseTemperature(input.get("temperature"));
    }
    double pBara = 50.0;
    if (input.has("pressure")) {
      pBara = parsePressure(input.get("pressure"));
    }
    SystemInterface fluid = createFluid(model, tempK, pBara);
    if (input.has("components")) {
      JsonObject comps = input.getAsJsonObject("components");
      for (Map.Entry<String, JsonElement> entry : comps.entrySet()) {
        fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
      }
    }
    String mixingRule = input.has("mixingRule") ? input.get("mixingRule").getAsString() : "classic";
    fluid.setMixingRule(mixingRule);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Creates a fluid system for the given EOS model.
   *
   * @param model EOS model name
   * @param tempK temperature in Kelvin
   * @param pBara pressure in bara
   * @return the fluid system
   */
  private static SystemInterface createFluid(String model, double tempK, double pBara) {
    switch (model.toUpperCase()) {
      case "PR":
        return new SystemPrEos(tempK, pBara);
      case "CPA":
        return new SystemSrkCPAstatoil(tempK, pBara);
      case "GERG2008":
        return new SystemGERG2008Eos(tempK, pBara);
      case "PCSAFT":
        return new SystemPCSAFT(tempK, pBara);
      case "UMRPRU":
        return new SystemUMRPRUMCEos(tempK, pBara);
      default:
        return new SystemSrkEos(tempK, pBara);
    }
  }

  /**
   * Parses a temperature specification from JSON.
   *
   * @param element the JSON element
   * @return temperature in Kelvin
   */
  private static double parseTemperature(JsonElement element) {
    if (element.isJsonPrimitive()) {
      return element.getAsDouble();
    }
    JsonObject obj = element.getAsJsonObject();
    double value = obj.get("value").getAsDouble();
    String unit = obj.has("unit") ? obj.get("unit").getAsString() : "K";
    switch (unit) {
      case "C":
        return value + 273.15;
      case "F":
        return (value - 32.0) * 5.0 / 9.0 + 273.15;
      default:
        return value;
    }
  }

  /**
   * Parses a pressure specification from JSON.
   *
   * @param element the JSON element
   * @return pressure in bara
   */
  private static double parsePressure(JsonElement element) {
    if (element.isJsonPrimitive()) {
      return element.getAsDouble();
    }
    JsonObject obj = element.getAsJsonObject();
    double value = obj.get("value").getAsDouble();
    String unit = obj.has("unit") ? obj.get("unit").getAsString() : "bara";
    switch (unit) {
      case "barg":
        return value + 1.01325;
      case "Pa":
        return value / 100000.0;
      case "kPa":
        return value / 100.0;
      case "MPa":
        return value * 10.0;
      case "psi":
        return value / 14.696;
      case "atm":
        return value * 1.01325;
      default:
        return value;
    }
  }

  /**
   * Converts a double array to a JSON array.
   *
   * @param arr the double array
   * @return the JSON array
   */
  private static JsonArray toJsonArray(double[] arr) {
    JsonArray ja = new JsonArray();
    if (arr != null) {
      for (double v : arr) {
        ja.add(v);
      }
    }
    return ja;
  }

  /**
   * Creates a standard error JSON string.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return the error JSON string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }
}
