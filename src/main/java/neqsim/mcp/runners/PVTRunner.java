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
import neqsim.mcp.model.ResultProvenance;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.simulation.SaturationPressure;
import neqsim.pvtsimulation.simulation.SaturationTemperature;
import neqsim.pvtsimulation.simulation.SeparatorTest;
import neqsim.pvtsimulation.simulation.SwellingTest;
import neqsim.pvtsimulation.simulation.GOR;
import neqsim.pvtsimulation.simulation.ViscositySim;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

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
          "Provide a valid JSON PVT specification with 'experiment', 'components', etc.");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    long startTime = System.currentTimeMillis();

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

    // --- Parse model ---
    String model = input.has("model") ? input.get("model").getAsString().toUpperCase() : "SRK";

    // --- Parse temperature ---
    double temperatureK = 373.15;
    if (input.has("temperature")) {
      temperatureK = parseTemperature(input.get("temperature"));
    }

    // --- Parse pressure ---
    double pressureBara = 100.0;
    if (input.has("pressure")) {
      pressureBara = parsePressure(input.get("pressure"));
    }

    // --- Parse components ---
    if (!input.has("components")) {
      return errorJson("MISSING_COMPONENTS", "No 'components' specified",
          "Provide a components map, e.g. {\"methane\": 0.85, \"ethane\": 0.15}");
    }
    JsonObject componentsJson = input.getAsJsonObject("components");
    Map<String, Double> components = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : componentsJson.entrySet()) {
      components.put(entry.getKey(), entry.getValue().getAsDouble());
    }

    // --- Create fluid ---
    SystemInterface fluid;
    try {
      fluid = createFluid(model, temperatureK, pressureBara);
      for (Map.Entry<String, Double> comp : components.entrySet()) {
        fluid.addComponent(comp.getKey(), comp.getValue());
      }
      String mixingRule =
          input.has("mixingRule") ? input.get("mixingRule").getAsString() : "classic";
      fluid.setMixingRule(mixingRule);
      fluid.setMultiPhaseCheck(true);
    } catch (Exception e) {
      return errorJson("FLUID_ERROR", "Failed to create fluid: " + e.getMessage(),
          "Check component names and compositions");
    }

    // --- Parse pressures array ---
    double[] pressures = null;
    if (input.has("pressures")) {
      JsonArray pArr = input.getAsJsonArray("pressures");
      pressures = new double[pArr.size()];
      for (int i = 0; i < pArr.size(); i++) {
        pressures[i] = pArr.get(i).getAsDouble();
      }
    }

    // --- Run experiment ---
    try {
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("experiment", experiment);

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
      provenance.setCalculationType("PVT " + experiment);
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      result.add("provenance", GSON.toJsonTree(provenance));

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
