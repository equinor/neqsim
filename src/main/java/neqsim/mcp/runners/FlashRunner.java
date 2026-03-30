package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.mcp.model.FlashRequest;
import neqsim.mcp.model.FlashResult;
import neqsim.mcp.model.ValueWithUnit;
import neqsim.process.util.monitor.FluidResponse;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Stateless flash calculation runner for MCP integration.
 *
 * <p>
 * Accepts a JSON string describing a fluid and flash specification, performs the flash calculation,
 * and returns a JSON string with the results including per-phase properties, compositions, and
 * conditions. Uses the same standard response envelope as the process simulation pipeline.
 * </p>
 *
 * <h2>Supported Flash Types:</h2>
 * <ul>
 * <li>{@code TP} — Temperature-Pressure flash (default)</li>
 * <li>{@code PH} — Pressure-Enthalpy flash</li>
 * <li>{@code PS} — Pressure-Entropy flash</li>
 * <li>{@code TV} — Temperature-Volume flash</li>
 * <li>{@code dewPointT} — Dew point temperature at given pressure</li>
 * <li>{@code dewPointP} — Dew point pressure at given temperature</li>
 * <li>{@code bubblePointT} — Bubble point temperature at given pressure</li>
 * <li>{@code
 * bubblePointP} — Bubble point pressure at given temperature</li>
 * <li>{@code hydrateTP} — Hydrate equilibrium temperature at given pressure</li>
 * </ul>
 *
 * <h2>Input JSON Format:</h2>
 *
 * <pre>{@code { "model": "SRK", "temperature": {"value": 25.0, "unit": "C"}, "pressure": {"value":
 * 50.0, "unit": "bara"}, "flashType": "TP", "components": {"methane": 0.85, "ethane": 0.10,
 * "propane": 0.05}, "mixingRule": "classic", "enthalpy": {"value": -5000.0, "unit": "J/mol"},
 * "entropy": {"value": 100.0, "unit": "J/molK"}, "volume": {"value": 0.001, "unit": "m3/mol"} }
 * }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class FlashRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private static final List<String> SUPPORTED_FLASH_TYPES =
      Collections.unmodifiableList(Arrays.asList("TP", "PH", "PS", "TV", "dewPointT", "dewPointP",
          "bubblePointT", "bubblePointP", "hydrateTP"));

  private static final List<String> SUPPORTED_MODELS = Collections
      .unmodifiableList(Arrays.asList("SRK", "PR", "CPA", "GERG2008", "PCSAFT", "UMRPRU"));

  /**
   * Private constructor — all methods are static.
   */
  private FlashRunner() {}

  /**
   * Runs a flash calculation from a JSON input string.
   *
   * @param json the JSON flash specification
   * @return a JSON string with status, fluid properties, or errors
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON flash specification");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    List<String> warnings = new ArrayList<>();

    // --- Parse model ---
    String model = "SRK";
    if (input.has("model")) {
      model = input.get("model").getAsString().toUpperCase();
    }
    if (!SUPPORTED_MODELS.contains(model)) {
      return errorJson("UNKNOWN_MODEL", "Unknown thermodynamic model: " + model,
          "Use one of: " + SUPPORTED_MODELS);
    }

    // --- Parse temperature ---
    double temperatureK = 288.15; // default 15C
    if (input.has("temperature")) {
      temperatureK = parseTemperature(input.get("temperature"));
      if (Double.isNaN(temperatureK)) {
        return errorJson("TEMPERATURE_ERROR", "Invalid temperature specification",
            "Provide {\"value\": 25.0, \"unit\": \"C\"} or a number (Kelvin)");
      }
    }

    // --- Parse pressure ---
    double pressureBara = 1.01325; // default 1 atm
    if (input.has("pressure")) {
      pressureBara = parsePressure(input.get("pressure"));
      if (Double.isNaN(pressureBara)) {
        return errorJson("PRESSURE_ERROR", "Invalid pressure specification",
            "Provide {\"value\": 50.0, \"unit\": \"bara\"} or a number (bara)");
      }
    }

    // --- Parse flash type ---
    String flashType = "TP";
    if (input.has("flashType")) {
      flashType = input.get("flashType").getAsString();
    }
    if (!SUPPORTED_FLASH_TYPES.contains(flashType)) {
      return errorJson("UNKNOWN_FLASH_TYPE", "Unknown flash type: " + flashType,
          "Use one of: " + SUPPORTED_FLASH_TYPES);
    }

    // --- Parse components ---
    if (!input.has("components")) {
      return errorJson("MISSING_COMPONENTS", "No 'components' specified",
          "Provide a components map, e.g. {\"methane\": 0.85, \"ethane\": 0.15}");
    }
    JsonObject componentsJson = input.getAsJsonObject("components");
    Map<String, Double> components = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : componentsJson.entrySet()) {
      String name = entry.getKey();
      if (!ComponentQuery.isValid(name)) {
        String suggestion = ComponentQuery.closestMatch(name);
        return errorJson("UNKNOWN_COMPONENT",
            "Unknown component: '" + name + "'"
                + (suggestion != null ? ". Did you mean '" + suggestion + "'?" : ""),
            "Use ComponentQuery.search() to find valid component names");
      }
      components.put(name, entry.getValue().getAsDouble());
    }
    if (components.isEmpty()) {
      return errorJson("MISSING_COMPONENTS", "Components map is empty",
          "Provide at least one component");
    }

    // --- Parse mixing rule ---
    String mixingRule = "classic";
    if (input.has("mixingRule")) {
      mixingRule = input.get("mixingRule").getAsString();
    }

    // --- Parse optional flash specs ---
    double enthalpySpec = Double.NaN;
    String enthalpyUnit = "J/mol";
    if (input.has("enthalpy")) {
      JsonElement enthEl = input.get("enthalpy");
      if (enthEl.isJsonObject()) {
        JsonObject enthObj = enthEl.getAsJsonObject();
        enthalpySpec = enthObj.get("value").getAsDouble();
        if (enthObj.has("unit")) {
          enthalpyUnit = enthObj.get("unit").getAsString();
        }
      } else {
        enthalpySpec = enthEl.getAsDouble();
      }
    }

    double entropySpec = Double.NaN;
    String entropyUnit = "J/molK";
    if (input.has("entropy")) {
      JsonElement entEl = input.get("entropy");
      if (entEl.isJsonObject()) {
        JsonObject entObj = entEl.getAsJsonObject();
        entropySpec = entObj.get("value").getAsDouble();
        if (entObj.has("unit")) {
          entropyUnit = entObj.get("unit").getAsString();
        }
      } else {
        entropySpec = entEl.getAsDouble();
      }
    }

    double volumeSpec = Double.NaN;
    String volumeUnit = "m3/mol";
    if (input.has("volume")) {
      JsonElement volEl = input.get("volume");
      if (volEl.isJsonObject()) {
        JsonObject volObj = volEl.getAsJsonObject();
        volumeSpec = volObj.get("value").getAsDouble();
        if (volObj.has("unit")) {
          volumeUnit = volObj.get("unit").getAsString();
        }
      } else {
        volumeSpec = volEl.getAsDouble();
      }
    }

    // --- Create fluid ---
    SystemInterface fluid;
    try {
      fluid = createFluid(model, temperatureK, pressureBara);
      for (Map.Entry<String, Double> comp : components.entrySet()) {
        fluid.addComponent(comp.getKey(), comp.getValue());
      }
      fluid.setMixingRule(mixingRule);
    } catch (Exception e) {
      return errorJson("FLUID_ERROR", "Failed to create fluid: " + e.getMessage(),
          "Check component names and fluid parameters");
    }

    // --- Run flash ---
    try {
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

      switch (flashType) {
        case "TP":
          ops.TPflash();
          break;
        case "PH":
          if (Double.isNaN(enthalpySpec)) {
            return errorJson("MISSING_SPEC", "PH flash requires 'enthalpy' specification",
                "Provide enthalpy as {\"value\": -5000.0, \"unit\": \"J/mol\"} or a number");
          }
          ops.PHflash(enthalpySpec, enthalpyUnit);
          break;
        case "PS":
          if (Double.isNaN(entropySpec)) {
            return errorJson("MISSING_SPEC", "PS flash requires 'entropy' specification",
                "Provide entropy as {\"value\": 100.0, \"unit\": \"J/molK\"} or a number");
          }
          ops.PSflash(entropySpec, entropyUnit);
          break;
        case "TV":
          if (Double.isNaN(volumeSpec)) {
            return errorJson("MISSING_SPEC", "TV flash requires 'volume' specification",
                "Provide volume as {\"value\": 0.001, \"unit\": \"m3/mol\"} or a number");
          }
          ops.TVflash(volumeSpec, volumeUnit);
          break;
        case "dewPointT":
          ops.dewPointTemperatureFlash();
          break;
        case "dewPointP":
          ops.dewPointPressureFlash();
          break;
        case "bubblePointT":
          ops.bubblePointTemperatureFlash();
          break;
        case "bubblePointP":
          ops.bubblePointPressureFlash();
          break;
        case "hydrateTP":
          fluid.setHydrateCheck(true);
          ops.hydrateTPflash();
          break;
        default:
          return errorJson("UNKNOWN_FLASH_TYPE", "Unknown flash type: " + flashType,
              "Use one of: " + SUPPORTED_FLASH_TYPES);
      }

      // Initialize all properties (thermo + transport)
      fluid.initProperties();
    } catch (Exception e) {
      return errorJson("FLASH_ERROR",
          "Flash calculation failed (" + flashType + "): " + e.getMessage(),
          "Check that inputs are physically reasonable. "
              + "For PH/PS flashes, ensure spec values are reachable for this fluid.");
    }

    // --- Build response ---
    try {
      FluidResponse fluidResponse = new FluidResponse("flash-result", fluid);
      String fluidJson = GSON.toJson(fluidResponse);

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");

      // Flash metadata
      JsonObject meta = new JsonObject();
      meta.addProperty("model", model);
      meta.addProperty("flashType", flashType);
      meta.addProperty("numberOfPhases", fluid.getNumberOfPhases());
      JsonArray phaseNames = new JsonArray();
      for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        phaseNames.add(fluid.getPhase(i).getPhaseTypeName());
      }
      meta.add("phases", phaseNames);
      result.add("flash", meta);

      // Fluid data
      result.add("fluid", JsonParser.parseString(fluidJson));

      // Warnings
      if (!warnings.isEmpty()) {
        JsonArray warnArray = new JsonArray();
        for (String w : warnings) {
          warnArray.add(w);
        }
        result.add("warnings", warnArray);
      }

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("RESPONSE_ERROR", "Failed to build response: " + e.getMessage(),
          "This is an internal error — please report it");
    }
  }

  /**
   * Returns the list of supported flash types.
   *
   * @return unmodifiable list of flash type names
   */
  public static List<String> getSupportedFlashTypes() {
    return SUPPORTED_FLASH_TYPES;
  }

  /**
   * Returns the list of supported thermodynamic models.
   *
   * @return unmodifiable list of model names
   */
  public static List<String> getSupportedModels() {
    return SUPPORTED_MODELS;
  }

  /**
   * Parses a temperature value from JSON. Accepts a number (Kelvin) or an object with value and
   * unit.
   *
   * @param element the JSON element
   * @return temperature in Kelvin, or NaN on error
   */
  static double parseTemperature(JsonElement element) {
    try {
      if (element.isJsonPrimitive()) {
        return element.getAsDouble(); // assume Kelvin
      }
      JsonObject obj = element.getAsJsonObject();
      double value = obj.get("value").getAsDouble();
      String unit = obj.has("unit") ? obj.get("unit").getAsString() : "K";
      switch (unit) {
        case "K":
          return value;
        case "C":
          return value + 273.15;
        case "F":
          return (value - 32.0) * 5.0 / 9.0 + 273.15;
        case "R":
          return value * 5.0 / 9.0;
        default:
          return Double.NaN;
      }
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Parses a pressure value from JSON. Accepts a number (bara) or an object with value and unit.
   *
   * @param element the JSON element
   * @return pressure in bara, or NaN on error
   */
  static double parsePressure(JsonElement element) {
    try {
      if (element.isJsonPrimitive()) {
        return element.getAsDouble(); // assume bara
      }
      JsonObject obj = element.getAsJsonObject();
      double value = obj.get("value").getAsDouble();
      String unit = obj.has("unit") ? obj.get("unit").getAsString() : "bara";
      switch (unit) {
        case "bara":
          return value;
        case "barg":
          return value + 1.01325;
        case "Pa":
          return value / 1.0e5;
        case "kPa":
          return value / 100.0;
        case "MPa":
          return value * 10.0;
        case "psi":
        case "psia":
          return value * 0.0689476;
        case "atm":
          return value * 1.01325;
        default:
          return Double.NaN;
      }
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Creates a SystemInterface based on the model type string.
   *
   * @param model the model name (e.g., "SRK", "PR", "CPA")
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return the created fluid system
   * @throws IllegalArgumentException if model is not recognized
   */
  private static SystemInterface createFluid(String model, double temperatureK,
      double pressureBara) {
    switch (model) {
      case "SRK":
        return new SystemSrkEos(temperatureK, pressureBara);
      case "PR":
        return new SystemPrEos(temperatureK, pressureBara);
      case "CPA":
        return new SystemSrkCPAstatoil(temperatureK, pressureBara);
      case "GERG2008":
        return new SystemGERG2008Eos(temperatureK, pressureBara);
      case "PCSAFT":
        return new SystemPCSAFT(temperatureK, pressureBara);
      case "UMRPRU":
        return new SystemUMRPRUMCEos(temperatureK, pressureBara);
      default:
        throw new IllegalArgumentException("Unknown model: " + model);
    }
  }

  /**
   * Runs a flash calculation from a typed request object.
   *
   * <p>
   * This is the typed counterpart to {@link #run(String)}. It accepts a {@link FlashRequest} POJO
   * and returns a typed {@link ApiEnvelope} with a {@link FlashResult} payload. For direct Java
   * consumers this avoids JSON parsing overhead and provides compile-time type safety.
   * </p>
   *
   * @param request the flash request
   * @return an ApiEnvelope containing the FlashResult on success, or errors on failure
   */
  public static ApiEnvelope<FlashResult> runTyped(FlashRequest request) {
    if (request == null) {
      return ApiEnvelope.error("INPUT_ERROR", "FlashRequest is null",
          "Provide a valid FlashRequest object");
    }

    List<String> warnings = new ArrayList<>();

    // --- Validate model ---
    String model = request.getModel() != null ? request.getModel().toUpperCase() : "SRK";
    if (!SUPPORTED_MODELS.contains(model)) {
      return ApiEnvelope.error("UNKNOWN_MODEL", "Unknown thermodynamic model: " + model,
          "Use one of: " + SUPPORTED_MODELS);
    }

    // --- Convert temperature to Kelvin ---
    double temperatureK = convertTemperatureToKelvin(request.getTemperature());
    if (Double.isNaN(temperatureK)) {
      return ApiEnvelope.error("TEMPERATURE_ERROR", "Invalid temperature specification",
          "Provide a ValueWithUnit with a supported unit (K, C, F, R)");
    }

    // --- Convert pressure to bara ---
    double pressureBara = convertPressureToBara(request.getPressure());
    if (Double.isNaN(pressureBara)) {
      return ApiEnvelope.error("PRESSURE_ERROR", "Invalid pressure specification",
          "Provide a ValueWithUnit with a supported unit (bara, barg, Pa, kPa, MPa, psi, atm)");
    }

    // --- Validate flash type ---
    String flashType = request.getFlashType() != null ? request.getFlashType() : "TP";
    if (!SUPPORTED_FLASH_TYPES.contains(flashType)) {
      return ApiEnvelope.error("UNKNOWN_FLASH_TYPE", "Unknown flash type: " + flashType,
          "Use one of: " + SUPPORTED_FLASH_TYPES);
    }

    // --- Validate components ---
    Map<String, Double> components = request.getComponents();
    if (components == null || components.isEmpty()) {
      return ApiEnvelope.error("MISSING_COMPONENTS", "No components specified",
          "Add at least one component via addComponent()");
    }
    for (Map.Entry<String, Double> entry : components.entrySet()) {
      if (!ComponentQuery.isValid(entry.getKey())) {
        String suggestion = ComponentQuery.closestMatch(entry.getKey());
        return ApiEnvelope.error("UNKNOWN_COMPONENT",
            "Unknown component: '" + entry.getKey() + "'"
                + (suggestion != null ? ". Did you mean '" + suggestion + "'?" : ""),
            "Use ComponentQuery.search() to find valid component names");
      }
    }

    // --- Parse mixing rule ---
    String mixingRule = request.getMixingRule() != null ? request.getMixingRule() : "classic";

    // --- Validate flash specs ---
    ValueWithUnit enthalpySpec = request.getEnthalpy();
    ValueWithUnit entropySpec = request.getEntropy();
    ValueWithUnit volumeSpec = request.getVolume();

    // --- Create fluid ---
    SystemInterface fluid;
    try {
      fluid = createFluid(model, temperatureK, pressureBara);
      for (Map.Entry<String, Double> comp : components.entrySet()) {
        fluid.addComponent(comp.getKey(), comp.getValue());
      }
      fluid.setMixingRule(mixingRule);
    } catch (Exception e) {
      return ApiEnvelope.error("FLUID_ERROR", "Failed to create fluid: " + e.getMessage(),
          "Check component names and fluid parameters");
    }

    // --- Run flash ---
    try {
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

      switch (flashType) {
        case "TP":
          ops.TPflash();
          break;
        case "PH":
          if (enthalpySpec == null) {
            return ApiEnvelope.error("MISSING_SPEC", "PH flash requires enthalpy specification",
                "Set enthalpy via setEnthalpy()");
          }
          ops.PHflash(enthalpySpec.getValue(), enthalpySpec.getUnit());
          break;
        case "PS":
          if (entropySpec == null) {
            return ApiEnvelope.error("MISSING_SPEC", "PS flash requires entropy specification",
                "Set entropy via setEntropy()");
          }
          ops.PSflash(entropySpec.getValue(), entropySpec.getUnit());
          break;
        case "TV":
          if (volumeSpec == null) {
            return ApiEnvelope.error("MISSING_SPEC", "TV flash requires volume specification",
                "Set volume via setVolume()");
          }
          ops.TVflash(volumeSpec.getValue(), volumeSpec.getUnit());
          break;
        case "dewPointT":
          ops.dewPointTemperatureFlash();
          break;
        case "dewPointP":
          ops.dewPointPressureFlash();
          break;
        case "bubblePointT":
          ops.bubblePointTemperatureFlash();
          break;
        case "bubblePointP":
          ops.bubblePointPressureFlash();
          break;
        case "hydrateTP":
          fluid.setHydrateCheck(true);
          ops.hydrateTPflash();
          break;
        default:
          return ApiEnvelope.error("UNKNOWN_FLASH_TYPE", "Unknown flash type: " + flashType,
              "Use one of: " + SUPPORTED_FLASH_TYPES);
      }

      fluid.initProperties();
    } catch (Exception e) {
      return ApiEnvelope.error("FLASH_ERROR",
          "Flash calculation failed (" + flashType + "): " + e.getMessage(),
          "Check that inputs are physically reasonable.");
    }

    // --- Build typed result ---
    try {
      FluidResponse fluidResponse = new FluidResponse("flash-result", fluid);

      List<String> phaseNames = new ArrayList<>();
      for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        phaseNames.add(fluid.getPhase(i).getPhaseTypeName());
      }

      FlashResult result =
          new FlashResult(model, flashType, fluid.getNumberOfPhases(), phaseNames, fluidResponse);

      return ApiEnvelope.success(result, warnings);
    } catch (Exception e) {
      return ApiEnvelope.error("RESPONSE_ERROR", "Failed to build response: " + e.getMessage(),
          "This is an internal error — please report it");
    }
  }

  /**
   * Converts a temperature ValueWithUnit to Kelvin.
   *
   * @param temp the temperature with unit
   * @return temperature in Kelvin, or NaN on error
   */
  static double convertTemperatureToKelvin(ValueWithUnit temp) {
    if (temp == null) {
      return 288.15; // default 15C
    }
    double value = temp.getValue();
    String unit = temp.getUnit() != null ? temp.getUnit() : "K";
    switch (unit) {
      case "K":
        return value;
      case "C":
        return value + 273.15;
      case "F":
        return (value - 32.0) * 5.0 / 9.0 + 273.15;
      case "R":
        return value * 5.0 / 9.0;
      default:
        return Double.NaN;
    }
  }

  /**
   * Converts a pressure ValueWithUnit to bara.
   *
   * @param pres the pressure with unit
   * @return pressure in bara, or NaN on error
   */
  static double convertPressureToBara(ValueWithUnit pres) {
    if (pres == null) {
      return 1.01325; // default 1 atm
    }
    double value = pres.getValue();
    String unit = pres.getUnit() != null ? pres.getUnit() : "bara";
    switch (unit) {
      case "bara":
        return value;
      case "barg":
        return value + 1.01325;
      case "Pa":
        return value / 1.0e5;
      case "kPa":
        return value / 100.0;
      case "MPa":
        return value * 10.0;
      case "psi":
      case "psia":
        return value * 0.0689476;
      case "atm":
        return value * 1.01325;
      default:
        return Double.NaN;
    }
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return JSON error string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject result = new JsonObject();
    result.addProperty("status", "error");

    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    if (remediation != null) {
      err.addProperty("remediation", remediation);
    }
    errors.add(err);
    result.add("errors", errors);

    return GSON.toJson(result);
  }
}
