package neqsim.mcp.runners;

import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.monitor.TwoFluidPipeResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;

/**
 * Stateless pipeline simulation runner for MCP integration.
 *
 * <p>
 * Supports multiphase pipeline flow calculations using either the Beggs and Brill correlation or the finite-volume
 * two-fluid solver, including pressure drop, liquid holdup, flow regime, and temperature profiles.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PipelineRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private PipelineRunner() {
  }

  /**
   * Runs a pipeline simulation from a JSON input string.
   *
   * @param json the JSON pipeline specification
   * @return a JSON string with status and pipeline results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty", "Provide a valid JSON pipeline specification");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(), "Ensure the JSON is well-formed");
    }

    String analysis = getString(input, "analysis", getString(input, "mode", "beggsAndBrill"));
    if ("waterHammer".equalsIgnoreCase(analysis) || "liquidHammer".equalsIgnoreCase(analysis)
        || "hydraulicTransient".equalsIgnoreCase(analysis)) {
      return WaterHammerRunner.run(json);
    }
    String solver = getString(input, "solver", analysis);
    if (!isTwoFluidSolver(solver) && !isBeggsAndBrillSolver(solver)) {
      return errorJson("UNKNOWN_PIPELINE_SOLVER", "Unsupported pipeline solver: " + solver,
          "Use beggsBrill (default) or twoFluid");
    }

    long startTime = System.currentTimeMillis();

    // --- Create fluid ---
    if (!input.has("components")) {
      return errorJson("MISSING_COMPONENTS", "No 'components' specified", "Provide a components map for the fluid");
    }

    try {
      SystemInterface fluid = createFluidFromInput(input);

      // --- Create feed stream ---
      Stream feed = new Stream("Pipeline Feed", fluid);
      if (input.has("flowRate")) {
        JsonObject fr = input.getAsJsonObject("flowRate");
        feed.setFlowRate(fr.get("value").getAsDouble(), fr.has("unit") ? fr.get("unit").getAsString() : "kg/hr");
      }

      if (isTwoFluidSolver(solver)) {
        return runTwoFluid(input, feed, startTime);
      }

      // --- Create pipeline ---
      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Pipeline", feed);
      applyPipeConfiguration(pipe, input);

      // --- Run simulation ---
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      process.add(pipe);
      process.run();

      // --- Collect results ---
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");

      JsonObject data = new JsonObject();
      data.addProperty("inletPressure_bara", feed.getPressure());
      data.addProperty("outletPressure_bara", pipe.getOutletStream().getPressure());
      data.addProperty("pressureDrop_bara", feed.getPressure() - pipe.getOutletStream().getPressure());
      data.addProperty("inletTemperature_C", feed.getTemperature() - 273.15);
      data.addProperty("outletTemperature_C", pipe.getOutletStream().getTemperature() - 273.15);
      JsonObject pipeInput = getPipeInput(input);
      Double length = getOptionalDouble(input, pipeInput, "length_m", "length");
      Double diameter = getOptionalDouble(input, pipeInput, "diameter_m", "diameter");
      data.addProperty("length_m", length == null ? 0.0 : length.doubleValue());
      data.addProperty("diameter_m", diameter == null ? 0.0 : diameter.doubleValue());

      result.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("pipeline simulation (Beggs & Brill)");
      String model = input.has("model") ? input.get("model").getAsString() : "SRK";
      provenance.setThermodynamicModel(model);
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      result.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("PIPELINE_ERROR", "Pipeline simulation failed: " + e.getMessage(),
          "Check fluid definition and pipeline parameters");
    }
  }

  /**
   * Runs the finite-volume two-fluid pipeline solver and serializes its typed response.
   *
   * @param input parsed pipeline input
   * @param feed configured inlet stream
   * @param startTime calculation start time in epoch milliseconds
   * @return response envelope containing a {@link TwoFluidPipeResponse}
   */
  private static String runTwoFluid(JsonObject input, Stream feed, long startTime) {
    TwoFluidPipe pipe = new TwoFluidPipe("Pipeline", feed);
    applyTwoFluidConfiguration(pipe, input);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pipe);
    process.run();

    TwoFluidPipeResponse response = new TwoFluidPipeResponse(pipe);
    response.applyConfig(parseReportConfig(input));

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.add("data", GSON.toJsonTree(response));

    ResultProvenance provenance = new ResultProvenance();
    provenance.setCalculationType("pipeline simulation (finite-volume two-fluid model)");
    provenance.setThermodynamicModel(getString(input, "model", "SRK"));
    provenance.setConverged(pipe.isSteadyStateConverged());
    provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
    result.add("provenance", GSON.toJsonTree(provenance));
    return GSON.toJson(result);
  }

  /**
   * Applies geometry, mesh, and thermal profiles to a two-fluid pipe.
   *
   * @param pipe two-fluid pipe to configure
   * @param input runner input containing top-level or nested pipe settings
   */
  private static void applyTwoFluidConfiguration(TwoFluidPipe pipe, JsonObject input) {
    JsonObject pipeInput = getPipeInput(input);

    Double length = getOptionalDouble(input, pipeInput, "length_m", "length");
    double[] sectionLengths = getOptionalDoubleArray(input, pipeInput, "sectionLengths_m", "lengthProfile_m",
        "sectionLengths");
    if (length == null && sectionLengths != null) {
      length = Double.valueOf(sum(sectionLengths));
    }
    if (length != null) {
      pipe.setLength(length.doubleValue());
    }
    if (sectionLengths != null) {
      double expectedLength = length == null ? sum(sectionLengths) : length.doubleValue();
      double tolerance = Math.max(1.0e-8, Math.abs(expectedLength) * 1.0e-8);
      if (Math.abs(sum(sectionLengths) - expectedLength) > tolerance) {
        throw new IllegalArgumentException("sectionLengths_m must sum to length_m");
      }
      pipe.setSectionLengths(sectionLengths);
    }

    Double diameter = getOptionalDouble(input, pipeInput, "diameter_m", "diameter");
    if (diameter != null) {
      pipe.setDiameter(diameter.doubleValue());
    }
    Double roughness = getOptionalDouble(input, pipeInput, "pipeWallRoughness_m", "roughness_m", "roughness");
    if (roughness != null) {
      pipe.setRoughness(roughness.doubleValue());
    }

    Integer sections = getOptionalInt(input, pipeInput, "numberOfSections", "numberOfSegments", "numberOfIncrements",
        "numberOfNodes");
    if (sectionLengths == null && sections != null) {
      pipe.setNumberOfSections(sections.intValue());
    }
    int numberOfSections = sectionLengths == null ? (sections == null ? 50 : sections.intValue())
        : sectionLengths.length;

    double[] elevations = getOptionalDoubleArray(input, pipeInput, "elevationProfile_m", "heightProfile_m",
        "elevationProfile");
    if (elevations == null) {
      elevations = linearElevationProfile(input, pipeInput, numberOfSections);
    }
    if (elevations != null) {
      validateProfileLength("elevationProfile_m", elevations, numberOfSections);
      pipe.setElevationProfile(elevations);
    }

    Double heatTransferCoefficient = getOptionalDouble(input, pipeInput, "heatTransferCoefficient_W_m2K",
        "uValue_W_m2K", "heatTransferCoefficient", "U_value");
    if (heatTransferCoefficient != null) {
      pipe.setHeatTransferCoefficient(heatTransferCoefficient.doubleValue());
    }
    double[] heatTransferProfile = getOptionalDoubleArray(input, pipeInput, "heatTransferProfile_W_m2K",
        "uValueProfile_W_m2K", "heatTransferProfile");
    if (heatTransferProfile != null) {
      validateProfileLength("heatTransferProfile_W_m2K", heatTransferProfile, numberOfSections);
      pipe.setHeatTransferProfile(heatTransferProfile);
    }

    Double surfaceTemperatureC = getOptionalDouble(input, pipeInput, "surfaceTemperature_C", "ambientTemperature_C");
    if (surfaceTemperatureC != null) {
      pipe.setSurfaceTemperature(surfaceTemperatureC.doubleValue(), "C");
    } else {
      Double surfaceTemperatureK = getOptionalDouble(input, pipeInput, "surfaceTemperature_K", "ambientTemperature_K");
      if (surfaceTemperatureK != null) {
        pipe.setSurfaceTemperature(surfaceTemperatureK.doubleValue(), "K");
      }
    }
    double[] surfaceTemperatureProfileC = getOptionalDoubleArray(input, pipeInput, "surfaceTemperatureProfile_C",
        "ambientTemperatureProfile_C");
    if (surfaceTemperatureProfileC != null) {
      validateProfileLength("surfaceTemperatureProfile_C", surfaceTemperatureProfileC, numberOfSections);
      pipe.setSurfaceTemperatureProfile(addOffset(surfaceTemperatureProfileC, 273.15));
    } else {
      double[] surfaceTemperatureProfileK = getOptionalDoubleArray(input, pipeInput, "surfaceTemperatureProfile_K",
          "ambientTemperatureProfile_K");
      if (surfaceTemperatureProfileK != null) {
        validateProfileLength("surfaceTemperatureProfile_K", surfaceTemperatureProfileK, numberOfSections);
        pipe.setSurfaceTemperatureProfile(surfaceTemperatureProfileK);
      }
    }

    Double wallClockLimit = getOptionalDouble(input, pipeInput, "steadyStateMaxWallClockTime_s");
    if (wallClockLimit != null) {
      pipe.setSteadyStateMaxWallClockTime(wallClockLimit.doubleValue());
    }
  }

  /**
   * Returns whether the requested solver denotes the finite-volume two-fluid model.
   *
   * @param solver requested solver name
   * @return true for accepted two-fluid solver aliases
   */
  private static boolean isTwoFluidSolver(String solver) {
    return "twoFluid".equalsIgnoreCase(solver) || "two-fluid".equalsIgnoreCase(solver)
        || "finiteVolumeTwoFluid".equalsIgnoreCase(solver);
  }

  /**
   * Returns whether the requested solver denotes the Beggs and Brill correlation.
   *
   * @param solver requested solver name
   * @return true for accepted Beggs and Brill solver aliases
   */
  private static boolean isBeggsAndBrillSolver(String solver) {
    return "beggsAndBrill".equalsIgnoreCase(solver) || "beggsBrill".equalsIgnoreCase(solver)
        || "beggs-and-brill".equalsIgnoreCase(solver);
  }

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
    } else if (input.has("temperature_C")) {
      tempK = input.get("temperature_C").getAsDouble() + 273.15;
    } else if (input.has("temperature_K")) {
      tempK = input.get("temperature_K").getAsDouble();
    }
    double pBara = 50.0;
    if (input.has("pressure")) {
      pBara = parsePressure(input.get("pressure"));
    } else if (input.has("pressure_bara")) {
      pBara = input.get("pressure_bara").getAsDouble();
    } else if (input.has("pressure_bar")) {
      pBara = input.get("pressure_bar").getAsDouble();
    } else if (input.has("pressure_barg")) {
      pBara = input.get("pressure_barg").getAsDouble() + 1.01325;
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
   * Applies pipe geometry and solver settings from either top-level JSON fields or a nested {@code pipe} object.
   *
   * @param pipe the pipeline object to configure
   * @param input the runner input JSON containing optional pipe settings
   */
  private static void applyPipeConfiguration(PipeBeggsAndBrills pipe, JsonObject input) {
    JsonObject pipeInput = getPipeInput(input);

    Double length = getOptionalDouble(input, pipeInput, "length_m", "length");
    if (length != null) {
      pipe.setLength(length.doubleValue());
    }

    Double diameter = getOptionalDouble(input, pipeInput, "diameter_m", "diameter");
    if (diameter != null) {
      pipe.setDiameter(diameter.doubleValue());
    }

    Double roughness = getOptionalDouble(input, pipeInput, "pipeWallRoughness_m", "roughness_m", "roughness");
    if (roughness != null) {
      pipe.setPipeWallRoughness(roughness.doubleValue());
    }

    Double elevation = getOptionalDouble(input, pipeInput, "elevation_m", "elevationChange_m");
    Double inletElevation = getOptionalDouble(input, pipeInput, "inletElevation_m");
    Double outletElevation = getOptionalDouble(input, pipeInput, "outletElevation_m");
    if (elevation != null) {
      pipe.setElevation(elevation.doubleValue());
    } else if (inletElevation != null && outletElevation != null) {
      pipe.setInletElevation(inletElevation.doubleValue());
      pipe.setOutletElevation(outletElevation.doubleValue());
      pipe.setElevation(outletElevation.doubleValue() - inletElevation.doubleValue());
    }

    Double angle = getOptionalDouble(input, pipeInput, "angle_degrees", "angle_deg");
    if (angle != null) {
      pipe.setAngle(angle.doubleValue());
    }

    Integer numberOfSegments = getOptionalInt(input, pipeInput, "numberOfSegments", "numberOfIncrements",
        "numberOfNodes");
    if (numberOfSegments != null) {
      pipe.setNumberOfIncrements(numberOfSegments.intValue());
    }
  }

  /**
   * Returns the nested pipe object when present, otherwise the original input object.
   *
   * @param input the runner input JSON
   * @return nested pipe settings or the input object when no nested object exists
   */
  private static JsonObject getPipeInput(JsonObject input) {
    if (input.has("pipe") && input.get("pipe").isJsonObject()) {
      return input.getAsJsonObject("pipe");
    }
    return input;
  }

  /**
   * Reads the first matching double value from a primary or secondary JSON object.
   *
   * @param primary the JSON object checked first
   * @param secondary the JSON object checked when the primary has no matching field
   * @param names accepted field names in priority order
   * @return the parsed double value, or {@code null} when no field is present
   */
  private static Double getOptionalDouble(JsonObject primary, JsonObject secondary, String... names) {
    JsonElement element = getOptionalElement(primary, names);
    if (element == null && secondary != primary) {
      element = getOptionalElement(secondary, names);
    }
    return element == null ? null : Double.valueOf(element.getAsDouble());
  }

  /**
   * Reads the first matching integer value from a primary or secondary JSON object.
   *
   * @param primary the JSON object checked first
   * @param secondary the JSON object checked when the primary has no matching field
   * @param names accepted field names in priority order
   * @return the parsed integer value, or {@code null} when no field is present
   */
  private static Integer getOptionalInt(JsonObject primary, JsonObject secondary, String... names) {
    JsonElement element = getOptionalElement(primary, names);
    if (element == null && secondary != primary) {
      element = getOptionalElement(secondary, names);
    }
    return element == null ? null : Integer.valueOf(element.getAsInt());
  }

  /**
   * Reads the first matching numeric array from a primary or secondary JSON object.
   *
   * @param primary the JSON object checked first
   * @param secondary the JSON object checked when the primary has no matching field
   * @param names accepted field names in priority order
   * @return parsed array, or {@code null} when no field is present
   */
  private static double[] getOptionalDoubleArray(JsonObject primary, JsonObject secondary, String... names) {
    JsonElement element = getOptionalElement(primary, names);
    if (element == null && secondary != primary) {
      element = getOptionalElement(secondary, names);
    }
    if (element == null) {
      return null;
    }
    JsonArray values = element.getAsJsonArray();
    double[] result = new double[values.size()];
    for (int i = 0; i < values.size(); i++) {
      result[i] = values.get(i).getAsDouble();
    }
    return result;
  }

  /**
   * Creates a linear elevation profile from scalar elevation inputs.
   *
   * @param input top-level runner input
   * @param pipeInput nested pipe input
   * @param numberOfSections number of profile values to create
   * @return elevation profile, or {@code null} when no elevation is specified
   */
  private static double[] linearElevationProfile(JsonObject input, JsonObject pipeInput, int numberOfSections) {
    Double elevationChange = getOptionalDouble(input, pipeInput, "elevation_m", "elevationChange_m");
    Double inletElevation = getOptionalDouble(input, pipeInput, "inletElevation_m");
    Double outletElevation = getOptionalDouble(input, pipeInput, "outletElevation_m");
    if (elevationChange == null && (inletElevation == null || outletElevation == null)) {
      return null;
    }
    double start = inletElevation == null ? 0.0 : inletElevation.doubleValue();
    double end = outletElevation == null ? start + elevationChange.doubleValue() : outletElevation.doubleValue();
    double[] profile = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      profile[i] = numberOfSections == 1 ? end : start + (end - start) * i / (numberOfSections - 1.0);
    }
    return profile;
  }

  /**
   * Validates that a spatial profile matches the configured mesh.
   *
   * @param name profile field name
   * @param profile profile values
   * @param expectedLength required number of values
   */
  private static void validateProfileLength(String name, double[] profile, int expectedLength) {
    if (profile.length != expectedLength) {
      throw new IllegalArgumentException(name + " must contain " + expectedLength + " values");
    }
  }

  /**
   * Sums an array of section lengths.
   *
   * @param values values to sum
   * @return sum of all values
   */
  private static double sum(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException("Section lengths must be positive and finite");
      }
      sum += value;
    }
    return sum;
  }

  /**
   * Returns a copy of an array with a constant offset applied.
   *
   * @param values source values
   * @param offset offset added to every value
   * @return converted values
   */
  private static double[] addOffset(double[] values, double offset) {
    double[] result = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = values[i] + offset;
    }
    return result;
  }

  /**
   * Parses the requested typed-response detail level.
   *
   * @param input runner input
   * @return report configuration, defaulting to full profiles
   */
  private static ReportConfig parseReportConfig(JsonObject input) {
    String requested = getString(input, "detailLevel", getString(input, "reportDetail", "FULL")).toUpperCase();
    try {
      return new ReportConfig(DetailLevel.valueOf(requested));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("detailLevel must be FULL, SUMMARY, MINIMUM, or HIDE");
    }
  }

  /**
   * Finds the first non-null JSON field matching one of the supplied names.
   *
   * @param object the JSON object to inspect
   * @param names accepted field names in priority order
   * @return the matching JSON element, or {@code null} when no field is present
   */
  private static JsonElement getOptionalElement(JsonObject object, String... names) {
    if (object == null) {
      return null;
    }
    for (String name : names) {
      if (object.has(name) && !object.get(name).isJsonNull()) {
        return object.get(name);
      }
    }
    return null;
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
   * Gets a string field from a JSON object with a fallback value.
   *
   * @param input source JSON object
   * @param name field name
   * @param defaultValue fallback value when the field is missing
   * @return string field value or fallback value
   */
  private static String getString(JsonObject input, String name, String defaultValue) {
    if (input != null && input.has(name) && !input.get(name).isJsonNull()) {
      return input.get(name).getAsString();
    }
    return defaultValue;
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
