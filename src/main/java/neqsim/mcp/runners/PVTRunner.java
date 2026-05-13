package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.simulation.GOR;
import neqsim.pvtsimulation.simulation.SaturationPressure;
import neqsim.pvtsimulation.simulation.SaturationTemperature;
import neqsim.pvtsimulation.simulation.SeparatorTest;
import neqsim.pvtsimulation.simulation.SwellingTest;
import neqsim.pvtsimulation.simulation.ViscositySim;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemPrLeeKeslerEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;

/**
 * Stateless PVT simulation runner for MCP integration.
 *
 * <p>
 * Supports all standard PVT laboratory experiments: CME, CVD, differential liberation, saturation
 * point, separator test, swelling test, GOR, and viscosity. Returns results in the standard JSON
 * envelope format with provenance metadata.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PVTRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private static final List<String> SUPPORTED_EXPERIMENTS = Collections
      .unmodifiableList(Arrays.asList("CME", "CVD", "differentialLiberation", "saturationPressure",
          "saturationTemperature", "separatorTest", "swellingTest", "GOR", "viscosity"));

  private static final List<String> SUPPORTED_MODELS = Collections
      .unmodifiableList(Arrays.asList("SRK", "PR", "PR_LK", "CPA", "GERG2008", "PCSAFT", "UMRPRU"));

  /**
   * Private constructor — all methods are static.
   */
  private PVTRunner() {}

  /**
   * Returns the list of supported PVT experiment types.
   *
   * @return unmodifiable list of experiment type names
   */
  public static List<String> getSupportedExperiments() {
    return SUPPORTED_EXPERIMENTS;
  }

  /**
   * Runs a PVT simulation from a JSON input string.
   *
   * @param json the JSON PVT specification
   * @return a JSON string with status and PVT results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON PVT specification with 'experiment' and either 'components' "
              + "or 'e300FilePath'.");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    long startTime = System.currentTimeMillis();
    List<String> warnings = new ArrayList<String>();

    // --- Parse experiment type ---
    if (!input.has("experiment")) {
      return errorJson("MISSING_EXPERIMENT", "No 'experiment' field specified",
          "Provide 'experiment': one of " + SUPPORTED_EXPERIMENTS);
    }
    String experiment = input.get("experiment").getAsString();
    if (!SUPPORTED_EXPERIMENTS.contains(experiment)) {
      return errorJson("UNKNOWN_EXPERIMENT", "Unknown experiment type: " + experiment,
          "Use one of: " + SUPPORTED_EXPERIMENTS);
    }

    // --- Parse fluid source and model ---
    String e300FilePath = FlashRunner.getE300FilePath(input);
    boolean e300Source = e300FilePath != null;

    String model = e300Source ? "AUTO" : "SRK";
    if (input.has("model")) {
      model = FlashRunner.normalizeModel(input.get("model").getAsString());
    }
    if (!"AUTO".equals(model) && !SUPPORTED_MODELS.contains(model)) {
      return errorJson("UNKNOWN_MODEL", "Unknown thermodynamic model: " + model,
          "Use one of: " + SUPPORTED_MODELS + ", or AUTO with e300FilePath");
    }
    if ("AUTO".equals(model) && !e300Source) {
      return errorJson("UNKNOWN_MODEL", "AUTO model can only be used with e300FilePath",
          "Use a concrete model such as SRK or PR, or provide an E300 file path");
    }

    // --- Parse temperature ---
    double temperatureK = parseTemperatureFromInput(input, 373.15);

    // --- Parse pressure ---
    double pressureBara = parsePressureFromInput(input, 100.0);

    // --- Parse components unless an E300 file supplies the fluid ---
    Map<String, Double> components = new HashMap<String, Double>();
    if (!e300Source && !input.has("components")) {
      return errorJson("MISSING_COMPONENTS", "No 'components' specified",
          "Provide a components map, e.g. {\"methane\": 0.85, \"ethane\": 0.15}, "
              + "or provide e300FilePath");
    }
    if (!e300Source) {
      JsonObject componentsJson = input.getAsJsonObject("components");
      for (Map.Entry<String, JsonElement> entry : componentsJson.entrySet()) {
        components.put(entry.getKey(), entry.getValue().getAsDouble());
      }
    } else if (input.has("components")) {
      warnings.add("Ignoring components because e300FilePath supplies the fluid composition");
    }
    if (!e300Source && components.isEmpty()) {
      return errorJson("MISSING_COMPONENTS", "Components map is empty",
          "Provide at least one component");
    }

    // --- Parse mixing rule ---
    String mixingRule = e300Source ? "E300" : "classic";
    if (input.has("mixingRule")) {
      if (e300Source) {
        warnings.add("Ignoring mixingRule because E300 files include mixing rules and BIPs");
      } else {
        mixingRule = input.get("mixingRule").getAsString();
      }
    }

    // --- Create fluid ---
    SystemInterface fluid;
    try {
      if (e300Source) {
        fluid = FlashRunner.readE300Fluid(e300FilePath, input);
        fluid.setTemperature(temperatureK, "K");
        fluid.setPressure(pressureBara, "bara");
        model = FlashRunner.inferModelName(fluid);
      } else {
        fluid = createFluid(model, temperatureK, pressureBara);
        for (Map.Entry<String, Double> comp : components.entrySet()) {
          fluid.addComponent(comp.getKey(), comp.getValue());
        }
        fluid.setMixingRule(mixingRule);
      }
      fluid.setMultiPhaseCheck(true);
    } catch (Exception e) {
      return errorJson("FLUID_ERROR", "Failed to create fluid: " + e.getMessage(),
          e300Source ? "Check that the E300 file path exists and can be read"
              : "Check component names and compositions");
    }

    // --- Parse pressures array ---
    double[] pressures = parsePressures(input);

    // --- Run experiment ---
    try {
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("experiment", experiment);
      result.addProperty("model", model);
      result.addProperty("fluidSource", e300Source ? "e300File" : "components");
      if (e300Source) {
        result.addProperty("e300FilePath", e300FilePath);
      }

      JsonObject data;
      switch (experiment) {
        case "CME":
          data = runCME(fluid, temperatureK, pressures, input);
          break;
        case "CVD":
          data = runCVD(fluid, temperatureK, pressures, input);
          break;
        case "differentialLiberation":
          data = runDL(fluid, temperatureK, pressures, input);
          break;
        case "saturationPressure":
          data = runSaturationPressure(fluid);
          break;
        case "saturationTemperature":
          data = runSaturationTemperature(fluid);
          break;
        case "separatorTest":
          data = runSeparatorTest(fluid, input);
          break;
        case "swellingTest":
          data = runSwellingTest(fluid, input);
          break;
        case "GOR":
          data = runGOR(fluid, input);
          break;
        case "viscosity":
          data = runViscosity(fluid, temperatureK, pressures, input);
          break;
        default:
          return errorJson("UNKNOWN_EXPERIMENT", "Not implemented: " + experiment, "");
      }

      result.add("data", data);

      // Provenance
      ResultProvenance provenance = new ResultProvenance();
      provenance.setThermodynamicModel(model);
      provenance.setMixingRule(mixingRule);
      provenance.setCalculationType("PVT " + experiment);
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      provenance.addValidationPassed(e300Source ? "e300_file_loaded" : "component_map_loaded");
      result.add("provenance", GSON.toJsonTree(provenance));

      if (!warnings.isEmpty()) {
        JsonArray warnArray = new JsonArray();
        for (String warning : warnings) {
          warnArray.add(warning);
        }
        result.add("warnings", warnArray);
      }

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("PVT_ERROR", "PVT calculation failed: " + e.getMessage(),
          "Check fluid definition and experiment parameters");
    }
  }

  /**
   * Runs a Constant Mass Expansion experiment.
   *
   * @param fluid the thermodynamic system
   * @param temperatureK temperature in Kelvin
   * @param pressures pressure points in bara
   * @param input the full JSON input
   * @return JSON object with CME results
   */
  private static JsonObject runCME(SystemInterface fluid, double temperatureK, double[] pressures,
      JsonObject input) {
    ConstantMassExpansion cme = new ConstantMassExpansion(fluid);
    if (pressures != null) {
      double[] temps = new double[pressures.length];
      Arrays.fill(temps, temperatureK);
      cme.setTemperaturesAndPressures(temps, pressures);
    }
    cme.runCalc();

    JsonObject data = new JsonObject();
    data.addProperty("saturationPressure_bara", cme.getSaturationPressure());
    data.add("relativeVolume", toJsonArray(cme.getRelativeVolume()));
    data.add("liquidRelativeVolume", toJsonArray(cme.getLiquidRelativeVolume()));
    data.add("Yf actor", toJsonArray(cme.getYfactor()));
    data.add("Zgas", toJsonArray(cme.getZgas()));
    data.add("density", toJsonArray(cme.getDensity()));
    data.add("viscosity", toJsonArray(cme.getViscosity()));
    data.add("isoThermalCompressibility", toJsonArray(cme.getIsoThermalCompressibility()));
    data.add("Bg", toJsonArray(cme.getBg()));
    if (pressures != null) {
      data.add("pressures_bara", toJsonArray(pressures));
    }
    return data;
  }

  /**
   * Runs a Constant Volume Depletion experiment.
   *
   * @param fluid the thermodynamic system
   * @param temperatureK temperature in Kelvin
   * @param pressures pressure points in bara
   * @param input the full JSON input
   * @return JSON object with CVD results
   */
  private static JsonObject runCVD(SystemInterface fluid, double temperatureK, double[] pressures,
      JsonObject input) {
    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
    if (pressures != null) {
      double[] temps = new double[pressures.length];
      Arrays.fill(temps, temperatureK);
      cvd.setTemperaturesAndPressures(temps, pressures);
    }
    cvd.runCalc();

    JsonObject data = new JsonObject();
    data.addProperty("saturationPressure_bara", cvd.getSaturationPressure());
    data.add("relativeVolume", toJsonArray(cvd.getRelativeVolume()));
    data.add("liquidRelativeVolume", toJsonArray(cvd.getLiquidRelativeVolume()));
    data.add("Zmix", toJsonArray(cvd.getZmix()));
    data.add("Zgas", toJsonArray(cvd.getZgas()));
    data.add("cummulativeMolePercDepleted", toJsonArray(cvd.getCummulativeMolePercDepleted()));
    data.add("liquidDropoutCurve", toJsonArray(cvd.getLiquidDropoutCurve()));
    if (pressures != null) {
      data.add("pressures_bara", toJsonArray(pressures));
    }
    return data;
  }

  /**
   * Runs a Differential Liberation experiment.
   *
   * @param fluid the thermodynamic system
   * @param temperatureK temperature in Kelvin
   * @param pressures pressure points in bara
   * @param input the full JSON input
   * @return JSON object with DL results
   */
  private static JsonObject runDL(SystemInterface fluid, double temperatureK, double[] pressures,
      JsonObject input) {
    DifferentialLiberation dl = new DifferentialLiberation(fluid);
    if (pressures != null) {
      dl.setPressures(pressures);
    }
    dl.setTemperature(temperatureK);
    dl.runCalc();

    JsonObject data = new JsonObject();
    data.addProperty("saturationPressure_bara", dl.getSaturationPressure());
    data.add("Bo", toJsonArray(dl.getBo()));
    data.add("Bg", toJsonArray(dl.getBg()));
    data.add("Rs", toJsonArray(dl.getRs()));
    data.add("Zgas", toJsonArray(dl.getZgas()));
    data.add("relGasGravity", toJsonArray(dl.getRelGasGravity()));
    data.add("oilDensity", toJsonArray(dl.getOilDensity()));
    data.add("shrinkage", toJsonArray(dl.getShrinkage()));
    if (pressures != null) {
      data.add("pressures_bara", toJsonArray(pressures));
    }
    return data;
  }

  /**
   * Runs a saturation pressure calculation.
   *
   * @param fluid the thermodynamic system
   * @return JSON object with saturation pressure
   */
  private static JsonObject runSaturationPressure(SystemInterface fluid) {
    SaturationPressure satP = new SaturationPressure(fluid);
    satP.run();
    JsonObject data = new JsonObject();
    data.addProperty("saturationPressure_bara", satP.getSaturationPressure());
    return data;
  }

  /**
   * Runs a saturation temperature calculation.
   *
   * @param fluid the thermodynamic system
   * @return JSON object with saturation temperature
   */
  private static JsonObject runSaturationTemperature(SystemInterface fluid) {
    SaturationTemperature satT = new SaturationTemperature(fluid);
    satT.run();
    JsonObject data = new JsonObject();
    data.addProperty("saturationTemperature_K", satT.getTemperature());
    data.addProperty("saturationTemperature_C", satT.getTemperature() - 273.15);
    return data;
  }

  /**
   * Runs a separator test.
   *
   * @param fluid the thermodynamic system
   * @param input the JSON input with separator conditions
   * @return JSON object with separator test results
   */
  private static JsonObject runSeparatorTest(SystemInterface fluid, JsonObject input) {
    SeparatorTest sepTest = new SeparatorTest(fluid);
    if (input.has("separatorConditions")) {
      JsonArray conds = input.getAsJsonArray("separatorConditions");
      double[] temps = new double[conds.size()];
      double[] presses = new double[conds.size()];
      for (int i = 0; i < conds.size(); i++) {
        JsonObject cond = conds.get(i).getAsJsonObject();
        temps[i] = cond.has("temperature_C") ? cond.get("temperature_C").getAsDouble() + 273.15
            : cond.get("temperature_K").getAsDouble();
        presses[i] = cond.get("pressure_bara").getAsDouble();
      }
      sepTest.setSeparatorConditions(temps, presses);
    }
    sepTest.runCalc();
    JsonObject data = new JsonObject();
    data.add("GOR", toJsonArray(sepTest.getGOR()));
    data.add("Bo", toJsonArray(sepTest.getBofactor()));
    return data;
  }

  /**
   * Runs a swelling test.
   *
   * @param fluid the thermodynamic system
   * @param input the JSON input with injection gas definition
   * @return JSON object with swelling test results
   */
  private static JsonObject runSwellingTest(SystemInterface fluid, JsonObject input) {
    SwellingTest swelling = new SwellingTest(fluid);
    if (input.has("injectionGas")) {
      JsonObject gasJson = input.getAsJsonObject("injectionGas");
      SystemInterface injGas = fluid.clone();
      injGas.reset();
      for (Map.Entry<String, JsonElement> entry : gasJson.entrySet()) {
        injGas.addComponent(entry.getKey(), entry.getValue().getAsDouble());
      }
      swelling.setInjectionGas(injGas);
    }
    if (input.has("cumulativeMolePercInjected")) {
      JsonArray molArr = input.getAsJsonArray("cumulativeMolePercInjected");
      double[] mols = new double[molArr.size()];
      for (int i = 0; i < molArr.size(); i++) {
        mols[i] = molArr.get(i).getAsDouble();
      }
      swelling.setCummulativeMolePercentGasInjected(mols);
    }
    swelling.runCalc();
    JsonObject data = new JsonObject();
    data.add("saturationPressures_bara", toJsonArray(swelling.getPressures()));
    data.add("relativeOilVolume", toJsonArray(swelling.getRelativeOilVolume()));
    return data;
  }

  /**
   * Runs a GOR calculation.
   *
   * @param fluid the thermodynamic system
   * @param input the JSON input
   * @return JSON object with GOR results
   */
  private static JsonObject runGOR(SystemInterface fluid, JsonObject input) {
    GOR gor = new GOR(fluid);
    if (input.has("separatorConditions")) {
      JsonArray conds = input.getAsJsonArray("separatorConditions");
      double[] temps = new double[conds.size()];
      double[] presses = new double[conds.size()];
      for (int i = 0; i < conds.size(); i++) {
        JsonObject cond = conds.get(i).getAsJsonObject();
        temps[i] = cond.has("temperature_C") ? cond.get("temperature_C").getAsDouble() + 273.15
            : cond.get("temperature_K").getAsDouble();
        presses[i] = cond.get("pressure_bara").getAsDouble();
      }
      gor.setTemperaturesAndPressures(temps, presses);
    }
    gor.runCalc();
    JsonObject data = new JsonObject();
    data.add("GOR", toJsonArray(gor.getGOR()));
    data.add("Bo", toJsonArray(gor.getBofactor()));
    return data;
  }

  /**
   * Runs a viscosity simulation.
   *
   * @param fluid the thermodynamic system
   * @param temperatureK temperature in Kelvin
   * @param pressures pressure points in bara
   * @param input the JSON input
   * @return JSON object with viscosity results
   */
  private static JsonObject runViscosity(SystemInterface fluid, double temperatureK,
      double[] pressures, JsonObject input) {
    ViscositySim visc = new ViscositySim(fluid);
    if (pressures != null) {
      double[] temps = new double[pressures.length];
      Arrays.fill(temps, temperatureK);
      visc.setTemperaturesAndPressures(temps, pressures);
    }
    visc.runCalc();
    JsonObject data = new JsonObject();
    data.add("gasViscosity_cP", toJsonArray(visc.getGasViscosity()));
    data.add("oilViscosity_cP", toJsonArray(visc.getOilViscosity()));
    data.add("aqueousViscosity_cP", toJsonArray(visc.getAqueousViscosity()));
    if (pressures != null) {
      data.add("pressures_bara", toJsonArray(pressures));
    }
    return data;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Utility methods
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
    switch (model.toUpperCase(Locale.ROOT)) {
      case "PR":
        return new SystemPrEos(tempK, pBara);
      case "PR_LK":
        return new SystemPrLeeKeslerEos(tempK, pBara);
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
   * @param element the JSON element (number in K or object with value/unit)
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
   * Parses the reservoir temperature from supported PVT input fields.
   *
   * @param input the parsed PVT input object
   * @param defaultTemperatureK default temperature in Kelvin
   * @return temperature in Kelvin
   */
  private static double parseTemperatureFromInput(JsonObject input, double defaultTemperatureK) {
    if (input.has("temperature")) {
      return parseTemperature(input.get("temperature"));
    }
    if (input.has("temperature_C")) {
      return input.get("temperature_C").getAsDouble() + 273.15;
    }
    if (input.has("temperature_K")) {
      return input.get("temperature_K").getAsDouble();
    }
    return defaultTemperatureK;
  }

  /**
   * Parses a pressure specification from JSON.
   *
   * @param element the JSON element (number in bara or object with value/unit)
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
   * Parses the reservoir pressure from supported PVT input fields.
   *
   * @param input the parsed PVT input object
   * @param defaultPressureBara default pressure in bara
   * @return pressure in bara
   */
  private static double parsePressureFromInput(JsonObject input, double defaultPressureBara) {
    if (input.has("pressure")) {
      return parsePressure(input.get("pressure"));
    }
    if (input.has("pressure_bara")) {
      return input.get("pressure_bara").getAsDouble();
    }
    return defaultPressureBara;
  }

  /**
   * Parses pressure steps from top-level fields or experimentConfig.
   *
   * @param input the parsed PVT input object
   * @return pressure steps in bara, or null when not supplied
   */
  private static double[] parsePressures(JsonObject input) {
    JsonArray pressureArray = null;
    if (input.has("pressures")) {
      pressureArray = input.getAsJsonArray("pressures");
    } else if (input.has("pressures_bara")) {
      pressureArray = input.getAsJsonArray("pressures_bara");
    } else if (input.has("experimentConfig") && input.get("experimentConfig").isJsonObject()) {
      JsonObject config = input.getAsJsonObject("experimentConfig");
      if (config.has("pressures_bara")) {
        pressureArray = config.getAsJsonArray("pressures_bara");
      } else if (config.has("pressures")) {
        pressureArray = config.getAsJsonArray("pressures");
      }
    }
    return pressureArray == null ? null : toDoubleArray(pressureArray);
  }

  /**
   * Converts a JSON array to a primitive double array.
   *
   * @param values JSON array with numeric values
   * @return primitive double array with the same values
   */
  private static double[] toDoubleArray(JsonArray values) {
    double[] array = new double[values.size()];
    for (int i = 0; i < values.size(); i++) {
      array[i] = values.get(i).getAsDouble();
    }
    return array;
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
