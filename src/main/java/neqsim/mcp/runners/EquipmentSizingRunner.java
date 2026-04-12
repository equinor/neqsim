package neqsim.mcp.runners;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quick equipment sizing runner for MCP integration.
 *
 * <p>
 * Performs approximate sizing for common process equipment based on thermodynamic flash results.
 * Supports separators (API 12J approach) and compressors (polytropic head / power). Results include
 * dimensions, duty/power, and design basis summary.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class EquipmentSizingRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private EquipmentSizingRunner() {}

  /**
   * Runs an equipment sizing calculation from a JSON definition.
   *
   * @param json JSON with equipmentType, components, conditions, and sizing parameters
   * @return JSON string with sizing results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }

    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String equipmentType =
          input.has("equipmentType") ? input.get("equipmentType").getAsString() : "";

      switch (equipmentType) {
        case "separator":
          return sizeSeparator(input);
        case "compressor":
          return sizeCompressor(input);
        default:
          return errorJson(
              "Unknown equipmentType: " + equipmentType + ". Supported: separator, compressor");
      }
    } catch (Exception e) {
      return errorJson("Equipment sizing failed: " + e.getMessage());
    }
  }

  /**
   * Sizes a separator vessel using flash results and retention time approach.
   *
   * @param input JSON input with fluid definition and sizing parameters
   * @return JSON string with separator dimensions
   */
  private static String sizeSeparator(JsonObject input) {
    SystemInterface fluid = buildFluid(input);
    double flowRateKgHr = extractFlowRate(input);
    double retentionTimeMin =
        input.has("liquidRetentionTime_min") ? input.get("liquidRetentionTime_min").getAsDouble()
            : 5.0;
    String orientation =
        input.has("orientation") ? input.get("orientation").getAsString() : "horizontal";

    // Build and run a simple stream + separator process
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(flowRateKgHr, "kg/hr");
    Separator sep = new Separator("Sizing Sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    // Extract phase properties
    double gasFlowM3s = 0.0;
    double liquidFlowM3s = 0.0;
    double gasDensity = 1.0;
    double liquidDensity = 800.0;

    try {
      SystemInterface gasFluid = sep.getGasOutStream().getFluid();
      SystemInterface liqFluid = sep.getLiquidOutStream().getFluid();
      gasDensity = gasFluid.getDensity("kg/m3");
      liquidDensity = liqFluid.getDensity("kg/m3");
      gasFlowM3s = sep.getGasOutStream().getFlowRate("m3/hr") / 3600.0;
      liquidFlowM3s = sep.getLiquidOutStream().getFlowRate("m3/hr") / 3600.0;
    } catch (Exception e) {
      // If only single phase, use bulk properties
      gasDensity = fluid.getDensity("kg/m3");
      gasFlowM3s = (flowRateKgHr / gasDensity) / 3600.0;
    }

    // Souders-Brown approach for gas velocity
    double kFactor = "horizontal".equals(orientation) ? 0.12 : 0.07;
    double maxGasVelocity = kFactor * Math.sqrt((liquidDensity - gasDensity) / gasDensity);
    if (maxGasVelocity < 0.01) {
      maxGasVelocity = 0.5; // fallback
    }
    double minGasArea = gasFlowM3s / maxGasVelocity;
    double vesselDiameter = Math.sqrt(4.0 * minGasArea / Math.PI) * 1.5; // 50% margin
    vesselDiameter = Math.max(vesselDiameter, 0.5); // minimum 0.5 m

    // Liquid volume for retention time
    double liquidVolumeM3 = liquidFlowM3s * retentionTimeMin * 60.0;
    double liquidHeight = 0.0;
    if ("horizontal".equals(orientation)) {
      double halfFillArea = Math.PI * vesselDiameter * vesselDiameter / 8.0;
      double vesselLength = liquidVolumeM3 / halfFillArea;
      vesselLength = Math.max(vesselLength, vesselDiameter * 3.0); // min L/D = 3

      liquidHeight = vesselDiameter * 0.5;

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("equipmentType", "separator");
      result.addProperty("orientation", orientation);

      JsonObject sizing = new JsonObject();
      sizing.addProperty("vesselDiameter_m", round(vesselDiameter, 3));
      sizing.addProperty("vesselLength_m", round(vesselLength, 3));
      sizing.addProperty("tangentToTangentLength_m", round(vesselLength + vesselDiameter, 3));
      sizing.addProperty("liquidLevel_m", round(liquidHeight, 3));
      sizing.addProperty("maxGasVelocity_m_s", round(maxGasVelocity, 3));
      sizing.addProperty("liquidRetentionTime_min", retentionTimeMin);
      result.add("sizing", sizing);

      JsonObject basis = new JsonObject();
      basis.addProperty("gasDensity_kg_m3", round(gasDensity, 2));
      basis.addProperty("liquidDensity_kg_m3", round(liquidDensity, 2));
      basis.addProperty("gasVolumetricFlow_m3_s", round(gasFlowM3s, 4));
      basis.addProperty("liquidVolumetricFlow_m3_s", round(liquidFlowM3s, 4));
      basis.addProperty("kFactor", kFactor);
      result.add("designBasis", basis);

      return GSON.toJson(result);
    } else {
      // Vertical
      double vesselLength = liquidVolumeM3 / (Math.PI * vesselDiameter * vesselDiameter / 4.0);
      vesselLength = Math.max(vesselLength, vesselDiameter * 3.0);

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("equipmentType", "separator");
      result.addProperty("orientation", orientation);

      JsonObject sizing = new JsonObject();
      sizing.addProperty("vesselDiameter_m", round(vesselDiameter, 3));
      sizing.addProperty("vesselHeight_m", round(vesselLength, 3));
      sizing.addProperty("maxGasVelocity_m_s", round(maxGasVelocity, 3));
      sizing.addProperty("liquidRetentionTime_min", retentionTimeMin);
      result.add("sizing", sizing);

      JsonObject basis = new JsonObject();
      basis.addProperty("gasDensity_kg_m3", round(gasDensity, 2));
      basis.addProperty("liquidDensity_kg_m3", round(liquidDensity, 2));
      result.add("designBasis", basis);

      return GSON.toJson(result);
    }
  }

  /**
   * Sizes a compressor using polytropic calculations.
   *
   * @param input JSON input with fluid definition and compressor parameters
   * @return JSON string with compressor sizing results
   */
  private static String sizeCompressor(JsonObject input) {
    SystemInterface fluid = buildFluid(input);
    double flowRateKgHr = extractFlowRate(input);
    double outletPressure =
        input.has("outletPressure_bara") ? input.get("outletPressure_bara").getAsDouble() : 80.0;
    double efficiency =
        input.has("polytropicEfficiency") ? input.get("polytropicEfficiency").getAsDouble() : 0.75;

    // Build and run compressor
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(flowRateKgHr, "kg/hr");
    Compressor comp = new Compressor("Sizing Comp", feed);
    comp.setOutletPressure(outletPressure);
    comp.setPolytropicEfficiency(efficiency);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    double powerKW = comp.getPower("kW");
    double outletTemp = comp.getOutletStream().getTemperature("C");
    double inletPressure = feed.getPressure("bara");
    double pressureRatio = outletPressure / inletPressure;

    // Calculate number of stages needed (max ratio ~4 per stage)
    int stages = 1;
    if (pressureRatio > 4.0) {
      stages = (int) Math.ceil(Math.log(pressureRatio) / Math.log(3.5));
    }

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.addProperty("equipmentType", "compressor");

    JsonObject sizing = new JsonObject();
    sizing.addProperty("power_kW", round(powerKW, 1));
    sizing.addProperty("power_HP", round(powerKW * 1.341, 1));
    sizing.addProperty("outletTemperature_C", round(outletTemp, 1));
    sizing.addProperty("pressureRatio", round(pressureRatio, 2));
    sizing.addProperty("recommendedStages", stages);
    sizing.addProperty("polytropicEfficiency", efficiency);
    result.add("sizing", sizing);

    JsonObject basis = new JsonObject();
    basis.addProperty("inletPressure_bara", round(inletPressure, 2));
    basis.addProperty("outletPressure_bara", outletPressure);
    basis.addProperty("inletTemperature_C", round(feed.getTemperature("C"), 1));
    basis.addProperty("massFlowRate_kg_hr", flowRateKgHr);
    result.add("designBasis", basis);

    return GSON.toJson(result);
  }

  /**
   * Builds a fluid from JSON input.
   *
   * @param input JSON object with model, temperature, pressure, components
   * @return configured SystemInterface
   */
  private static SystemInterface buildFluid(JsonObject input) {
    String model = input.has("model") ? input.get("model").getAsString() : "SRK";
    double tempC = input.has("temperature_C") ? input.get("temperature_C").getAsDouble() : 25.0;
    double pressBara = input.has("pressure_bara") ? input.get("pressure_bara").getAsDouble() : 50.0;

    SystemInterface fluid;
    if ("PR".equalsIgnoreCase(model)) {
      fluid = new SystemPrEos(273.15 + tempC, pressBara);
    } else {
      fluid = new SystemSrkEos(273.15 + tempC, pressBara);
    }

    JsonObject components = input.getAsJsonObject("components");
    for (String compName : components.keySet()) {
      fluid.addComponent(compName, components.get(compName).getAsDouble());
    }

    String mixingRule = input.has("mixingRule") ? input.get("mixingRule").getAsString() : "classic";
    fluid.setMixingRule(mixingRule);

    return fluid;
  }

  /**
   * Extracts flow rate from input JSON.
   *
   * @param input JSON with flowRate field
   * @return flow rate in kg/hr
   */
  private static double extractFlowRate(JsonObject input) {
    if (input.has("flowRate")) {
      JsonObject fr = input.getAsJsonObject("flowRate");
      double value = fr.has("value") ? fr.get("value").getAsDouble() : 10000.0;
      // Assume kg/hr if not specified
      return value;
    }
    return 10000.0;
  }

  /**
   * Rounds a value to the specified number of decimal places.
   *
   * @param value the value to round
   * @param decimals number of decimal places
   * @return rounded value
   */
  private static double round(double value, int decimals) {
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }

  /**
   * Creates an error JSON response.
   *
   * @param message the error message
   * @return JSON string with status error
   */
  private static String errorJson(String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", message);
    return error.toString();
  }
}
