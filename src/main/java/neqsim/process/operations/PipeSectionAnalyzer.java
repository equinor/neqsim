package neqsim.process.operations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Analyzes pipe sections that may not be part of the main process model.
 *
 * <p>
 * Given a fluid definition and a list of pipe sections (diameter, length, roughness, operating
 * conditions), this analyzer creates a temporary {@link PipeBeggsAndBrills} for each section, runs
 * the hydraulic calculation, and returns velocity, pressure drop, flow regime, and utilization
 * against a maximum design velocity.
 * </p>
 *
 * <p>
 * This enables velocity and utilization analysis for pipe sections defined on P&amp;IDs that are
 * not modeled in the main {@link ProcessSystem}. Operating conditions can be supplied directly or
 * overridden from tagreader field data.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PipeSectionAnalyzer {

  /** JSON serializer. */
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Default maximum design velocity in m/s (API RP 14E erosional). */
  private static final double DEFAULT_MAX_DESIGN_VELOCITY = 20.0;

  /** Default pipe roughness in meters. */
  private static final double DEFAULT_ROUGHNESS_M = 0.00005;

  /** Default number of pipe increments. */
  private static final int DEFAULT_INCREMENTS = 5;

  /**
   * Private constructor for utility class.
   */
  private PipeSectionAnalyzer() {}

  /**
   * Analyzes pipe sections from a JSON specification.
   *
   * <p>
   * The input JSON must contain:
   * </p>
   * <ul>
   * <li>{@code fluid} or {@code components} — fluid composition for the pipe sections</li>
   * <li>{@code pipeSections} — array of pipe section objects</li>
   * </ul>
   *
   * <p>
   * Each pipe section object supports:
   * </p>
   * <ul>
   * <li>{@code name} — section identifier (e.g. line tag from P&amp;ID)</li>
   * <li>{@code innerDiameter_m} — pipe inner diameter in meters</li>
   * <li>{@code length_m} — pipe length in meters</li>
   * <li>{@code roughness_m} — wall roughness (default 0.00005 m)</li>
   * <li>{@code elevation_m} — elevation change (default 0)</li>
   * <li>{@code flowRate} — object with {@code value} and {@code unit}</li>
   * <li>{@code inletPressure_bara} — inlet pressure in bara</li>
   * <li>{@code inletTemperature_C} — inlet temperature in Celsius</li>
   * <li>{@code maxDesignVelocity_m_s} — design velocity limit (default 20 m/s)</li>
   * <li>{@code sourceReference} — P&amp;ID or STID reference for traceability</li>
   * </ul>
   *
   * <p>
   * Operating conditions in individual sections override the top-level fluid conditions. Field data
   * from {@code fieldData} can further override section values when tag bindings with matching
   * {@code pipeSectionName} are provided.
   * </p>
   *
   * @param json JSON specification with fluid and pipe sections
   * @return JSON result with velocity, pressure drop, and utilization for each section
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a JSON object with 'pipeSections' array and fluid definition.");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException ex) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + ex.getMessage(),
          "Ensure the pipe-section analysis input is valid JSON.");
    }

    try {
      return GSON.toJson(analyze(input));
    } catch (RuntimeException ex) {
      return errorJson("ANALYSIS_ERROR", "Pipe section analysis failed: " + ex.getMessage(),
          "Check fluid definition, pipe section geometry, and operating conditions.");
    }
  }

  /**
   * Analyzes pipe sections from a parsed JSON input.
   *
   * @param input parsed JSON input object
   * @return JSON result object with section analysis
   */
  static JsonObject analyze(JsonObject input) {
    JsonArray pipeSections = getSections(input);
    if (pipeSections == null || pipeSections.size() == 0) {
      throw new IllegalArgumentException("No 'pipeSections' array found or it is empty. "
          + "Provide at least one pipe section with geometry and flow conditions.");
    }

    FluidSpec fluidSpec = readFluidSpec(input);
    Map<String, Double> fieldData = readFieldData(input);
    List<JsonObject> sectionBindings = readSectionBindings(input);

    JsonArray results = new JsonArray();
    int overDesignCount = 0;
    int nearLimitCount = 0;
    double maxUtilization = 0.0;
    String maxUtilizationSection = "";

    for (int i = 0; i < pipeSections.size(); i++) {
      JsonObject section = pipeSections.get(i).getAsJsonObject();
      String sectionName =
          getString(section, "name", getString(section, "lineTag", "Section-" + (i + 1)));

      applyFieldDataOverrides(section, sectionName, fieldData, sectionBindings);

      JsonObject sectionResult = analyzeOneSection(sectionName, section, fluidSpec);
      results.add(sectionResult);

      double utilization = sectionResult.get("velocityUtilization").getAsDouble();
      if (utilization > maxUtilization) {
        maxUtilization = utilization;
        maxUtilizationSection = sectionName;
      }
      if (utilization > 1.0) {
        overDesignCount++;
      } else if (utilization > 0.9) {
        nearLimitCount++;
      }
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sectionCount", pipeSections.size());
    response.add("sections", results);

    JsonObject summary = new JsonObject();
    summary.addProperty("maxUtilization", maxUtilization);
    summary.addProperty("maxUtilizationPercent", maxUtilization * 100.0);
    summary.addProperty("maxUtilizationSection", maxUtilizationSection);
    summary.addProperty("sectionsOverDesign", overDesignCount);
    summary.addProperty("sectionsNearLimit", nearLimitCount);
    summary.addProperty("allWithinDesign", overDesignCount == 0);
    response.add("summary", summary);

    return response;
  }

  /**
   * Analyzes a single pipe section.
   *
   * @param sectionName identifier for the section
   * @param section JSON section definition
   * @param fluidSpec top-level fluid specification
   * @return JSON result for the section
   */
  private static JsonObject analyzeOneSection(String sectionName, JsonObject section,
      FluidSpec fluidSpec) {
    double temperatureC = getDouble(section, "inletTemperature_C", fluidSpec.temperatureC);
    double pressureBara = getDouble(section, "inletPressure_bara", fluidSpec.pressureBara);
    double diameter =
        getDouble(section, "innerDiameter_m", getDouble(section, "diameter_m", 0.254));
    double length = getDouble(section, "length_m", 100.0);
    double roughness = getDouble(section, "roughness_m", DEFAULT_ROUGHNESS_M);
    double elevation = getDouble(section, "elevation_m", 0.0);
    double maxDesignVelocity =
        getDouble(section, "maxDesignVelocity_m_s", DEFAULT_MAX_DESIGN_VELOCITY);
    int increments = getInt(section, "numberOfIncrements", DEFAULT_INCREMENTS);

    SystemInterface fluid = createFluid(fluidSpec, section, temperatureC, pressureBara);
    Stream feed = new Stream("feed-" + sectionName, fluid);
    applyFlowRate(feed, section, fluidSpec);
    feed.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe-" + sectionName, feed);
    pipe.setLength(length);
    pipe.setDiameter(diameter);
    pipe.setPipeWallRoughness(roughness);
    pipe.setOutletElevation(elevation);
    pipe.setNumberOfIncrements(increments);

    ProcessSystem tempProcess = new ProcessSystem();
    tempProcess.add(feed);
    tempProcess.add(pipe);
    tempProcess.run();

    double inletVelocity = pipe.getInletSuperficialVelocity();
    double outletVelocity = inletVelocity;
    int nInc = pipe.getNumberOfIncrements();
    try {
      outletVelocity = pipe.getSegmentMixtureSuperficialVelocity(nInc - 1);
    } catch (IndexOutOfBoundsException ignored) {
      // Profile may not be populated for very short pipes; fall back to inlet velocity
    }
    double maxVelocity = Math.max(inletVelocity, outletVelocity);
    double utilization = maxDesignVelocity > 0 ? maxVelocity / maxDesignVelocity : 0.0;
    double pressureDrop = pipe.getPressureDrop();
    String flowRegime = pipe.getFlowRegime();

    double outletPressure = pipe.getOutletStream().getPressure();
    double outletTemperature = pipe.getOutletStream().getTemperature("C");

    JsonObject result = new JsonObject();
    result.addProperty("name", sectionName);
    result.addProperty("innerDiameter_m", diameter);
    result.addProperty("length_m", length);
    result.addProperty("inletPressure_bara", pressureBara);
    result.addProperty("outletPressure_bara", outletPressure);
    result.addProperty("pressureDrop_bar", pressureDrop);
    result.addProperty("inletTemperature_C", temperatureC);
    result.addProperty("outletTemperature_C", outletTemperature);
    result.addProperty("inletVelocity_m_s", inletVelocity);
    result.addProperty("outletVelocity_m_s", outletVelocity);
    result.addProperty("maxVelocity_m_s", maxVelocity);
    result.addProperty("maxDesignVelocity_m_s", maxDesignVelocity);
    result.addProperty("velocityUtilization", utilization);
    result.addProperty("velocityUtilizationPercent", utilization * 100.0);
    result.addProperty("overDesignLimit", utilization > 1.0);
    result.addProperty("nearDesignLimit", utilization > 0.9);
    result.addProperty("flowRegime", flowRegime != null ? flowRegime : "unknown");

    if (section.has("sourceReference")) {
      result.addProperty("sourceReference", section.get("sourceReference").getAsString());
    }
    return result;
  }

  /**
   * Creates a fluid for one pipe section, potentially with section-level component overrides.
   *
   * @param fluidSpec top-level fluid specification
   * @param section section JSON with optional component overrides
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @return configured fluid
   */
  private static SystemInterface createFluid(FluidSpec fluidSpec, JsonObject section,
      double temperatureC, double pressureBara) {
    String model = getString(section, "model", fluidSpec.model);
    SystemInterface fluid;
    if ("PR".equalsIgnoreCase(model)) {
      fluid = new SystemPrEos(273.15 + temperatureC, pressureBara);
    } else {
      fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    }

    JsonObject components =
        section.has("components") ? section.getAsJsonObject("components") : fluidSpec.components;
    if (components != null) {
      for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
        fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
      }
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Applies flow rate to a stream from section or top-level spec.
   *
   * @param feed stream to configure
   * @param section section JSON
   * @param fluidSpec top-level fluid spec with default flow rate
   */
  private static void applyFlowRate(Stream feed, JsonObject section, FluidSpec fluidSpec) {
    double flowValue = fluidSpec.flowRateValue;
    String flowUnit = fluidSpec.flowRateUnit;
    if (section.has("flowRate") && section.get("flowRate").isJsonObject()) {
      JsonObject fr = section.getAsJsonObject("flowRate");
      flowValue = getDouble(fr, "value", flowValue);
      flowUnit = getString(fr, "unit", flowUnit);
    } else if (section.has("flowRate_kg_hr")) {
      flowValue = section.get("flowRate_kg_hr").getAsDouble();
      flowUnit = "kg/hr";
    }
    feed.setFlowRate(flowValue, flowUnit);
  }

  /**
   * Applies field data overrides to a section based on tag bindings.
   *
   * <p>
   * Bindings with a matching {@code pipeSectionName} map logical tags to section properties like
   * {@code flowRate_kg_hr}, {@code inletPressure_bara}, or {@code inletTemperature_C}.
   * </p>
   *
   * @param section section JSON to update
   * @param sectionName name of the current section
   * @param fieldData field data map from tagreader
   * @param sectionBindings list of section binding objects
   */
  private static void applyFieldDataOverrides(JsonObject section, String sectionName,
      Map<String, Double> fieldData, List<JsonObject> sectionBindings) {
    if (fieldData == null || fieldData.isEmpty() || sectionBindings == null) {
      return;
    }
    for (JsonObject binding : sectionBindings) {
      String boundSection = getString(binding, "pipeSectionName", "");
      if (!sectionName.equals(boundSection)) {
        continue;
      }
      String logicalTag = getString(binding, "logicalTag", "");
      String property = getString(binding, "property", "");
      if (logicalTag.isEmpty() || property.isEmpty()) {
        continue;
      }
      Double value = fieldData.get(logicalTag);
      if (value == null) {
        continue;
      }
      section.addProperty(property, value);
    }
  }

  // ========================================================================
  // Input parsing helpers
  // ========================================================================

  /**
   * Reads the pipe sections array from the input.
   *
   * @param input parsed JSON input
   * @return pipe sections array, or null
   */
  private static JsonArray getSections(JsonObject input) {
    if (input.has("pipeSections") && input.get("pipeSections").isJsonArray()) {
      return input.getAsJsonArray("pipeSections");
    }
    return null;
  }

  /**
   * Reads the top-level fluid specification from the input.
   *
   * @param input parsed JSON input
   * @return fluid specification
   */
  private static FluidSpec readFluidSpec(JsonObject input) {
    FluidSpec spec = new FluidSpec();
    spec.model = getString(input, "model", "SRK").toUpperCase(Locale.ROOT);
    spec.temperatureC =
        getDouble(input, "temperature_C", getDouble(input, "inletTemperature_C", 25.0));
    spec.pressureBara =
        getDouble(input, "pressure_bara", getDouble(input, "inletPressure_bara", 50.0));

    if (input.has("fluid") && input.get("fluid").isJsonObject()) {
      JsonObject fluid = input.getAsJsonObject("fluid");
      spec.model = getString(fluid, "model", spec.model).toUpperCase(Locale.ROOT);
      spec.temperatureC = getDouble(fluid, "temperature_C", spec.temperatureC);
      spec.pressureBara = getDouble(fluid, "pressure_bara", spec.pressureBara);
      if (fluid.has("components") && fluid.get("components").isJsonObject()) {
        spec.components = fluid.getAsJsonObject("components");
      }
    }
    if (spec.components == null && input.has("components")
        && input.get("components").isJsonObject()) {
      spec.components = input.getAsJsonObject("components");
    }

    if (input.has("flowRate") && input.get("flowRate").isJsonObject()) {
      JsonObject fr = input.getAsJsonObject("flowRate");
      spec.flowRateValue = getDouble(fr, "value", 10000.0);
      spec.flowRateUnit = getString(fr, "unit", "kg/hr");
    }
    return spec;
  }

  /**
   * Reads field data from the input.
   *
   * @param input parsed JSON input
   * @return field data map, or empty map
   */
  private static Map<String, Double> readFieldData(JsonObject input) {
    Map<String, Double> data = new java.util.LinkedHashMap<String, Double>();
    if (input.has("fieldData") && input.get("fieldData").isJsonObject()) {
      JsonObject fd = input.getAsJsonObject("fieldData");
      for (Map.Entry<String, JsonElement> entry : fd.entrySet()) {
        if (entry.getValue().isJsonPrimitive()
            && entry.getValue().getAsJsonPrimitive().isNumber()) {
          data.put(entry.getKey(), entry.getValue().getAsDouble());
        }
      }
    }
    return data;
  }

  /**
   * Reads pipe-section-level tag bindings from the input.
   *
   * @param input parsed JSON input
   * @return list of binding objects
   */
  private static List<JsonObject> readSectionBindings(JsonObject input) {
    List<JsonObject> bindings = new ArrayList<JsonObject>();
    if (input.has("sectionTagBindings") && input.get("sectionTagBindings").isJsonArray()) {
      for (JsonElement el : input.getAsJsonArray("sectionTagBindings")) {
        if (el.isJsonObject()) {
          bindings.add(el.getAsJsonObject());
        }
      }
    }
    return bindings;
  }

  // ========================================================================
  // Fluid specification holder
  // ========================================================================

  /**
   * Holds top-level fluid specification defaults.
   */
  private static class FluidSpec {
    /** EOS model name. */
    String model = "SRK";

    /** Default temperature in Celsius. */
    double temperatureC = 25.0;

    /** Default pressure in bara. */
    double pressureBara = 50.0;

    /** Default flow rate value. */
    double flowRateValue = 10000.0;

    /** Default flow rate unit. */
    String flowRateUnit = "kg/hr";

    /** Component mole fractions. */
    JsonObject components;
  }

  // ========================================================================
  // JSON utility helpers
  // ========================================================================

  /**
   * Gets a string from a JSON object with a default.
   *
   * @param obj JSON object
   * @param key property name
   * @param defaultValue fallback value
   * @return string value or default
   */
  private static String getString(JsonObject obj, String key, String defaultValue) {
    if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
      return obj.get(key).getAsString();
    }
    return defaultValue;
  }

  /**
   * Gets a double from a JSON object with a default.
   *
   * @param obj JSON object
   * @param key property name
   * @param defaultValue fallback value
   * @return double value or default
   */
  private static double getDouble(JsonObject obj, String key, double defaultValue) {
    if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
      try {
        return obj.get(key).getAsDouble();
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  /**
   * Gets an int from a JSON object with a default.
   *
   * @param obj JSON object
   * @param key property name
   * @param defaultValue fallback value
   * @return int value or default
   */
  private static int getInt(JsonObject obj, String key, int defaultValue) {
    if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
      try {
        return obj.get(key).getAsInt();
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  /**
   * Creates an error JSON response.
   *
   * @param code error code
   * @param message error message
   * @param remediation fix suggestion
   * @return JSON string with error details
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("code", code);
    error.addProperty("message", message);
    error.addProperty("remediation", remediation);
    return GSON.toJson(error);
  }
}
