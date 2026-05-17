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
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Stateless standards calculation runner for MCP integration.
 *
 * <p>
 * Supports gas quality standards (ISO 6976, AGA, GPA, EN) and oil quality standards (ASTM, BSW).
 * Each standard returns its results (heating value, Wobbe index, density, distillation curve, etc.)
 * in the standard JSON envelope format.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class StandardsRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private static final List<String> SUPPORTED_STANDARDS =
      Collections.unmodifiableList(Arrays.asList("ISO6976", "ISO6976_2016", "ISO12213", "ISO13443",
          "ISO18453", "ISO14687", "ISO15112", "ISO6578", "AGA3", "AGA7", "GPA2145", "GPA2172",
          "EN16723", "EN16726", "ASTM_D86", "ASTM_D445", "ASTM_D2500", "ASTM_D4052", "ASTM_D4294",
          "ASTM_D6377", "ASTM_D97", "BSW"));

  /**
   * Private constructor — all methods are static.
   */
  private StandardsRunner() {}

  /**
   * Returns the list of supported standard calculation types.
   *
   * @return unmodifiable list of standard names
   */
  public static List<String> getSupportedStandards() {
    return SUPPORTED_STANDARDS;
  }

  /**
   * Runs a standards calculation from a JSON input string.
   *
   * @param json the JSON specification with fluid and standard selection
   * @return a JSON string with status and calculations per the selected standard
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON with 'standard', 'components', etc.");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    long startTime = System.currentTimeMillis();

    if (!input.has("standard")) {
      return errorJson("MISSING_STANDARD", "No 'standard' field specified",
          "Provide 'standard': one of " + SUPPORTED_STANDARDS);
    }
    String standard = input.get("standard").getAsString();
    if (!SUPPORTED_STANDARDS.contains(standard)) {
      return errorJson("UNKNOWN_STANDARD", "Unknown standard: " + standard,
          "Use one of: " + SUPPORTED_STANDARDS);
    }

    // --- Create fluid ---
    if (!input.has("components")) {
      return errorJson("MISSING_COMPONENTS", "No 'components' specified",
          "Provide a components map for the fluid");
    }

    SystemInterface fluid;
    try {
      String model = input.has("model") ? input.get("model").getAsString().toUpperCase() : "SRK";
      double tempK = 288.15;
      double pBara = 1.01325;
      if (input.has("temperature")) {
        tempK = parseTemperature(input.get("temperature"));
      } else if (input.has("temperature_C")) {
        tempK = input.get("temperature_C").getAsDouble() + 273.15;
      }
      if (input.has("pressure")) {
        pBara = parsePressure(input.get("pressure"));
      } else if (input.has("pressure_bara")) {
        pBara = input.get("pressure_bara").getAsDouble();
      }
      fluid = createFluid(model, tempK, pBara);
      JsonObject comps = input.getAsJsonObject("components");
      for (Map.Entry<String, JsonElement> entry : comps.entrySet()) {
        fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
      }
      String mixingRule =
          input.has("mixingRule") ? input.get("mixingRule").getAsString() : "classic";
      fluid.setMixingRule(mixingRule);
    } catch (Exception e) {
      return errorJson("FLUID_ERROR", "Failed to create fluid: " + e.getMessage(),
          "Check component names and compositions");
    }

    try {
      // Run flash to initialize the fluid
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("standard", standard);

      JsonObject data = runStandard(fluid, standard, input);
      result.add("data", data);
      result.add("results", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("standards: " + standard);
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      result.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("STANDARDS_ERROR", "Standards calculation failed: " + e.getMessage(),
          "Check fluid definition and standard-specific parameters");
    }
  }

  /**
   * Routes to the appropriate standard calculation.
   *
   * @param fluid the initialized fluid system
   * @param standard the standard name
   * @param input the full JSON input for standard-specific parameters
   * @return JSON object with standard results
   */
  private static JsonObject runStandard(SystemInterface fluid, String standard, JsonObject input) {
    switch (standard) {
      case "ISO6976":
        return runISO6976(fluid, input, false);
      case "ISO6976_2016":
        return runISO6976(fluid, input, true);
      case "ISO12213":
        return runGenericGasStandard(fluid, "ISO12213");
      case "ISO13443":
        return runGenericGasStandard(fluid, "ISO13443");
      case "ISO18453":
        return runGenericGasStandard(fluid, "ISO18453");
      case "ISO14687":
        return runGenericGasStandard(fluid, "ISO14687");
      case "ISO15112":
        return runGenericGasStandard(fluid, "ISO15112");
      case "ISO6578":
        return runGenericGasStandard(fluid, "ISO6578");
      case "AGA3":
        return runAGA3(fluid, input);
      case "AGA7":
        return runGenericGasStandard(fluid, "AGA7");
      case "GPA2145":
        return runGenericGasStandard(fluid, "GPA2145");
      case "GPA2172":
        return runGenericGasStandard(fluid, "GPA2172");
      case "EN16723":
        return runGenericGasStandard(fluid, "EN16723");
      case "EN16726":
        return runGenericGasStandard(fluid, "EN16726");
      case "ASTM_D86":
        return runASTM_D86(fluid, input);
      case "ASTM_D445":
        return runGenericOilStandard(fluid, "D445");
      case "ASTM_D2500":
        return runGenericOilStandard(fluid, "D2500");
      case "ASTM_D4052":
        return runGenericOilStandard(fluid, "D4052");
      case "ASTM_D4294":
        return runGenericOilStandard(fluid, "D4294");
      case "ASTM_D6377":
        return runGenericOilStandard(fluid, "D6377");
      case "ASTM_D97":
        return runGenericOilStandard(fluid, "D97");
      case "BSW":
        return runGenericOilStandard(fluid, "BSW");
      default:
        JsonObject err = new JsonObject();
        err.addProperty("error", "Standard not yet implemented: " + standard);
        return err;
    }
  }

  /**
   * Runs ISO 6976 gas quality calculation.
   *
   * @param fluid the initialized fluid
   * @param input the JSON input with optional reference conditions
   * @param use2016 whether to use the 2016 edition
   * @return JSON with calorific values, Wobbe index, density
   */
  private static JsonObject runISO6976(SystemInterface fluid, JsonObject input, boolean use2016) {
    double volRefT = input.has("volumeReferenceTemperature_C")
        ? input.get("volumeReferenceTemperature_C").getAsDouble()
        : 15.0;
    double energyRefT = input.has("energyReferenceTemperature_C")
        ? input.get("energyReferenceTemperature_C").getAsDouble()
        : 15.0;
    String calcType =
        input.has("calculationType") ? input.get("calculationType").getAsString() : "volume";

    neqsim.standards.gasquality.Standard_ISO6976 iso;
    if (use2016) {
      iso = new neqsim.standards.gasquality.Standard_ISO6976_2016(fluid);
    } else {
      iso = new neqsim.standards.gasquality.Standard_ISO6976(fluid, volRefT, energyRefT, calcType);
    }
    iso.calculate();

    JsonObject data = new JsonObject();
    data.addProperty("superiorCalorificValue_MJ_Sm3", iso.getValue("SuperiorCalorificValue"));
    data.addProperty("inferiorCalorificValue_MJ_Sm3", iso.getValue("InferiorCalorificValue"));
    data.addProperty("superiorWobbeIndex", iso.getValue("SuperiorWobbeIndex"));
    data.addProperty("inferiorWobbeIndex", iso.getValue("InferiorWobbeIndex"));
    data.addProperty("relativeDensity", iso.getValue("RelativeDensity"));
    data.addProperty("molarMass_kg_kmol", iso.getValue("MolarMass"));
    data.addProperty("compressionFactor", iso.getValue("CompressionFactor"));
    data.addProperty("isOnSpec", iso.isOnSpec());
    data.addProperty("volumeReferenceTemperature_C", volRefT);
    data.addProperty("energyReferenceTemperature_C", energyRefT);
    return data;
  }

  /**
   * Runs AGA 3 orifice metering calculation.
   *
   * @param fluid the initialized fluid
   * @param input the JSON input with metering parameters
   * @return JSON with flow rate and metering results
   */
  private static JsonObject runAGA3(SystemInterface fluid, JsonObject input) {
    neqsim.standards.gasquality.Standard_AGA3 aga =
        new neqsim.standards.gasquality.Standard_AGA3(fluid);
    // No custom methods exposed beyond calculate in some versions
    aga.calculate();
    JsonObject data = new JsonObject();
    data.addProperty("isOnSpec", aga.isOnSpec());
    // Return the standard table
    String[][] table = aga.createTable("AGA3 Results");
    JsonArray tableArr = new JsonArray();
    if (table != null) {
      for (String[] row : table) {
        JsonArray rowArr = new JsonArray();
        for (String cell : row) {
          rowArr.add(cell);
        }
        tableArr.add(rowArr);
      }
    }
    data.add("resultsTable", tableArr);
    return data;
  }

  /**
   * Runs ASTM D86 distillation curve calculation.
   *
   * @param fluid the initialized fluid
   * @param input the JSON input
   * @return JSON with distillation curve and temperatures
   */
  private static JsonObject runASTM_D86(SystemInterface fluid, JsonObject input) {
    neqsim.standards.oilquality.Standard_ASTM_D86 d86 =
        new neqsim.standards.oilquality.Standard_ASTM_D86(fluid);
    d86.calculate();
    JsonObject data = new JsonObject();
    data.addProperty("IBP_C", d86.getValue("IBP", "C"));
    data.addProperty("T10_C", d86.getValue("T10", "C"));
    data.addProperty("T50_C", d86.getValue("T50", "C"));
    data.addProperty("T90_C", d86.getValue("T90", "C"));
    data.addProperty("FBP_C", d86.getValue("FBP", "C"));
    data.addProperty("isOnSpec", d86.isOnSpec());
    // Distillation curve
    double[][] curve = d86.getDistillationCurve();
    if (curve != null) {
      JsonArray curveArr = new JsonArray();
      for (int i = 0; i < curve.length; i++) {
        JsonObject pt = new JsonObject();
        pt.addProperty("volumePercent", curve[i][0]);
        pt.addProperty("temperature_K", curve[i][1]);
        pt.addProperty("temperature_C", curve[i][1] - 273.15);
        curveArr.add(pt);
      }
      data.add("distillationCurve", curveArr);
    }
    return data;
  }

  /**
   * Runs a generic gas quality standard calculation.
   *
   * @param fluid the initialized fluid
   * @param standardName the standard name for factory lookup
   * @return JSON with standard results
   */
  private static JsonObject runGenericGasStandard(SystemInterface fluid, String standardName) {
    neqsim.standards.StandardInterface std = createGasStandard(standardName, fluid);
    if (std == null) {
      JsonObject err = new JsonObject();
      err.addProperty("error", "Standard not found: " + standardName);
      return err;
    }
    std.calculate();
    JsonObject data = new JsonObject();
    data.addProperty("isOnSpec", std.isOnSpec());
    String[][] table = std.createTable(standardName + " Results");
    if (table != null) {
      JsonArray tableArr = new JsonArray();
      for (String[] row : table) {
        JsonArray rowArr = new JsonArray();
        for (String cell : row) {
          rowArr.add(cell);
        }
        tableArr.add(rowArr);
      }
      data.add("resultsTable", tableArr);
    }
    return data;
  }

  /**
   * Runs a generic oil quality standard calculation.
   *
   * @param fluid the initialized fluid
   * @param standardName the standard name suffix (e.g. D445, D2500, BSW)
   * @return JSON with standard results
   */
  private static JsonObject runGenericOilStandard(SystemInterface fluid, String standardName) {
    neqsim.standards.StandardInterface std = createOilStandard(standardName, fluid);
    if (std == null) {
      JsonObject err = new JsonObject();
      err.addProperty("error", "Standard not found: " + standardName);
      return err;
    }
    std.calculate();
    JsonObject data = new JsonObject();
    data.addProperty("isOnSpec", std.isOnSpec());
    String[][] table = std.createTable(standardName + " Results");
    if (table != null) {
      JsonArray tableArr = new JsonArray();
      for (String[] row : table) {
        JsonArray rowArr = new JsonArray();
        for (String cell : row) {
          rowArr.add(cell);
        }
        tableArr.add(rowArr);
      }
      data.add("resultsTable", tableArr);
    }
    return data;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Utility methods (same as other runners)
  // ═══════════════════════════════════════════════════════════════════════════

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
   * Parses temperature from JSON element.
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
   * Parses pressure from JSON element.
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

  /**
   * Creates a gas quality standard instance by name.
   *
   * @param name the standard name (e.g. "ISO6976", "AGA3")
   * @param fluid the fluid system
   * @return the standard instance, or null if not found
   */
  private static neqsim.standards.StandardInterface createGasStandard(String name,
      SystemInterface fluid) {
    switch (name) {
      case "ISO6976":
        return new neqsim.standards.gasquality.Standard_ISO6976(fluid);
      case "ISO6976_2016":
        return new neqsim.standards.gasquality.Standard_ISO6976_2016(fluid);
      case "ISO12213":
        return new neqsim.standards.gasquality.Standard_ISO12213(fluid);
      case "ISO13443":
        return new neqsim.standards.gasquality.Standard_ISO13443(fluid);
      case "ISO18453":
        return new neqsim.standards.gasquality.Standard_ISO18453(fluid);
      case "ISO14687":
        return new neqsim.standards.gasquality.Standard_ISO14687(fluid);
      case "ISO15112":
        return new neqsim.standards.gasquality.Standard_ISO15112(fluid);
      case "ISO6578":
        return new neqsim.standards.gasquality.Standard_ISO6578(fluid);
      case "AGA3":
        return new neqsim.standards.gasquality.Standard_AGA3(fluid);
      case "AGA7":
        return new neqsim.standards.gasquality.Standard_AGA7(fluid);
      case "GPA2145":
        return new neqsim.standards.gasquality.Standard_GPA2145(fluid);
      case "GPA2172":
        return new neqsim.standards.gasquality.Standard_GPA2172(fluid);
      case "EN16723":
        return new neqsim.standards.gasquality.Standard_EN16723(fluid);
      case "EN16726":
        return new neqsim.standards.gasquality.Standard_EN16726(fluid);
      default:
        return null;
    }
  }

  /**
   * Creates an oil quality standard instance by name.
   *
   * @param name the standard name (e.g. "ASTM_D86", "BSW")
   * @param fluid the fluid system
   * @return the standard instance, or null if not found
   */
  private static neqsim.standards.StandardInterface createOilStandard(String name,
      SystemInterface fluid) {
    switch (name) {
      case "ASTM_D86":
        return new neqsim.standards.oilquality.Standard_ASTM_D86(fluid);
      case "ASTM_D445":
        return new neqsim.standards.oilquality.Standard_ASTM_D445(fluid);
      case "ASTM_D2500":
        return new neqsim.standards.oilquality.Standard_ASTM_D2500(fluid);
      case "ASTM_D4052":
        return new neqsim.standards.oilquality.Standard_ASTM_D4052(fluid);
      case "ASTM_D4294":
        return new neqsim.standards.oilquality.Standard_ASTM_D4294(fluid);
      case "ASTM_D6377":
        return new neqsim.standards.oilquality.Standard_ASTM_D6377(fluid);
      case "ASTM_D97":
        return new neqsim.standards.oilquality.Standard_ASTM_D97(fluid);
      case "BSW":
        return new neqsim.standards.oilquality.Standard_BSW(fluid);
      default:
        return null;
    }
  }
}
