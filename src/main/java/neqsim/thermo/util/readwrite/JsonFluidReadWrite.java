package neqsim.thermo.util.readwrite;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Read and write NeqSim fluids in a JSON format that provides full EOS-level fidelity equivalent to
 * Eclipse E300 fluid files.
 *
 * <p>
 * The JSON format supports all parameters needed to exactly reproduce a PVT-tuned fluid: component
 * critical properties, acentric factors, molecular weights, volume shifts, parachors, binary
 * interaction coefficients, viscosity model coefficients (LBC/Pedersen), and pseudo-component (TBP
 * fraction) definitions.
 * </p>
 *
 * <p>
 * Example JSON:
 * </p>
 *
 * <pre>
 * {
 *   "format": "neqsim-fluid",
 *   "version": "1.0",
 *   "eos": "SRK",
 *   "reservoirTemperature": { "value": 90.0, "unit": "C" },
 *   "components": [
 *     {
 *       "name": "methane",
 *       "moleFraction": 0.85,
 *       "criticalTemperature": 190.6,
 *       "criticalPressure": 46.0,
 *       "acentricFactor": 0.008,
 *       "molarMass": 16.043,
 *       "normalBoilingPoint": 111.6,
 *       "criticalVolume": 0.0986,
 *       "volumeShift": -0.194,
 *       "parachor": 77.3,
 *       "isPseudo": false
 *     }
 *   ],
 *   "binaryInteractionCoefficients": [
 *     { "i": "methane", "j": "ethane", "kij": 0.0 }
 *   ]
 * }
 * </pre>
 *
 * @author copilot
 * @version 1.0
 */
public class JsonFluidReadWrite {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(JsonFluidReadWrite.class);

  /** Format identifier expected in the JSON. */
  private static final String FORMAT_ID = "neqsim-fluid";

  /** Current format version. */
  private static final String FORMAT_VERSION = "1.0";

  /**
   * Private constructor to prevent instantiation — all methods are static.
   */
  private JsonFluidReadWrite() {}

  /**
   * Read a fluid from a JSON file on disk.
   *
   * @param inputFile path to the JSON file
   * @return a {@link SystemInterface} with all EOS parameters set
   * @throws IllegalArgumentException if the file does not exist, cannot be read, or has invalid
   *         content
   */
  public static SystemInterface read(String inputFile) {
    File file = new File(inputFile);
    if (!file.exists()) {
      throw new IllegalArgumentException(
          "JSON fluid file does not exist: " + inputFile + ". Provide a valid file path.");
    }
    if (!file.canRead()) {
      throw new IllegalArgumentException(
          "JSON fluid file cannot be read: " + inputFile + ". Check file permissions.");
    }
    try {
      String content = new String(Files.readAllBytes(Paths.get(inputFile)), StandardCharsets.UTF_8);
      return readString(content);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Failed to read JSON fluid file: " + inputFile + ". " + e.getMessage(), e);
    }
  }

  /**
   * Read a fluid from a JSON file and optionally add a water component.
   *
   * @param inputFile path to the JSON file
   * @param addWater if true, add a water component with default kij = 0.5
   * @return a {@link SystemInterface} with all EOS parameters set
   * @throws IllegalArgumentException if the file does not exist, cannot be read, or has invalid
   *         content
   */
  public static SystemInterface read(String inputFile, boolean addWater) {
    return read(inputFile, addWater, 0.5);
  }

  /**
   * Read a fluid from a JSON file and optionally add a water component with a custom kij value.
   *
   * @param inputFile path to the JSON file
   * @param addWater if true, add a water component
   * @param waterKij binary interaction parameter between water and all other components
   * @return a {@link SystemInterface} with all EOS parameters set
   * @throws IllegalArgumentException if the file does not exist, cannot be read, or has invalid
   *         content
   */
  public static SystemInterface read(String inputFile, boolean addWater, double waterKij) {
    SystemInterface fluid = read(inputFile);
    if (addWater) {
      EclipseFluidReadWrite.addWaterToFluid(fluid, waterKij);
    }
    return fluid;
  }

  /**
   * Read a fluid from a JSON string.
   *
   * <p>
   * This is the primary parsing method. It supports all EOS parameters including pseudo-components,
   * binary interaction coefficients, volume shifts, parachors, and viscosity model configuration.
   * </p>
   *
   * @param json the JSON string defining the fluid
   * @return a {@link SystemInterface} with all EOS parameters set
   * @throws IllegalArgumentException if the JSON is invalid or missing required fields
   */
  public static SystemInterface readString(String json) {
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("JSON input is null or empty.");
    }

    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
    }

    // Determine EOS type
    String eosType = "SRK";
    boolean prcorr = false;
    if (root.has("eos")) {
      eosType = root.get("eos").getAsString().toUpperCase();
    }
    if (root.has("prcorr")) {
      prcorr = root.get("prcorr").getAsBoolean();
    }

    SystemInterface fluid = createFluidByEOS(eosType, prcorr);

    // Parse components array
    if (!root.has("components")) {
      throw new IllegalArgumentException("JSON fluid must contain a 'components' array. "
          + "Each component needs at least 'name' and 'moleFraction'.");
    }

    JsonArray components = root.getAsJsonArray("components");
    if (components.size() == 0) {
      throw new IllegalArgumentException("'components' array is empty.");
    }

    List<String> componentNames = new ArrayList<String>();
    List<ComponentData> componentDataList = new ArrayList<ComponentData>();

    for (int i = 0; i < components.size(); i++) {
      JsonObject comp = components.get(i).getAsJsonObject();
      ComponentData data = parseComponent(comp);
      componentDataList.add(data);

      // Map E300-style short names to NeqSim database names
      String neqsimName = mapToNeqSimName(data.name);

      if (data.isPseudo) {
        double density = data.density;
        if (density <= 0) {
          // Estimate density from molecular weight if not provided
          density = 0.5046 * data.molarMass / 1000.0 + 0.668468;
        }
        fluid.addTBPfraction(data.name, data.moleFraction, data.molarMass / 1000.0, density);
        neqsimName = data.name + "_PC";
      } else {
        fluid.addComponent(neqsimName, data.moleFraction);
      }

      componentNames.add(neqsimName);

      // Set critical properties on all phases
      for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
        fluid.getPhase(phase).getComponent(neqsimName).setTC(data.criticalTemperature);
        fluid.getPhase(phase).getComponent(neqsimName).setPC(data.criticalPressure);
        fluid.getPhase(phase).getComponent(neqsimName).setAcentricFactor(data.acentricFactor);
        fluid.getPhase(phase).getComponent(neqsimName).setMolarMass(data.molarMass / 1000.0);
        fluid.getPhase(phase).getComponent(neqsimName)
            .setNormalBoilingPoint(data.normalBoilingPoint);
        fluid.getPhase(phase).getComponent(neqsimName).setCriticalVolume(data.criticalVolume);
        fluid.getPhase(phase).getComponent(neqsimName).setParachorParameter(data.parachor);
        double volShift =
            data.volumeShiftSurface != 0.0 ? data.volumeShiftSurface : data.volumeShift;
        fluid.getPhase(phase).getComponent(neqsimName).setVolumeCorrectionConst(volShift);
        fluid.getPhase(phase).getComponent(neqsimName)
            .setRacketZ(0.29056 - 0.08775 * data.acentricFactor);
      }

      // Rename pseudo-components back to the original name
      if (data.isPseudo) {
        fluid.changeComponentName(neqsimName, data.name);
        componentNames.set(componentNames.size() - 1, data.name);
      }
    }

    // Initialize mixing rule and volume correction
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(true);
    fluid.init(0);

    // Apply binary interaction coefficients
    if (root.has("binaryInteractionCoefficients")) {
      applyBinaryInteractionCoefficients(fluid, componentNames,
          root.getAsJsonArray("binaryInteractionCoefficients"));
    }

    // Apply viscosity model
    if (root.has("viscosityModel")) {
      applyViscosityModel(fluid, root.getAsJsonObject("viscosityModel"));
    }

    return fluid;
  }

  /**
   * Read a fluid from a JSON string and optionally add a water component.
   *
   * @param json the JSON string
   * @param addWater if true, add a water component with kij = 0.5
   * @return a {@link SystemInterface} with all EOS parameters set
   * @throws IllegalArgumentException if the JSON is invalid
   */
  public static SystemInterface readString(String json, boolean addWater) {
    return readString(json, addWater, 0.5);
  }

  /**
   * Read a fluid from a JSON string and optionally add a water component with custom kij.
   *
   * @param json the JSON string
   * @param addWater if true, add a water component
   * @param waterKij binary interaction parameter between water and all other components
   * @return a {@link SystemInterface} with all EOS parameters set
   * @throws IllegalArgumentException if the JSON is invalid
   */
  public static SystemInterface readString(String json, boolean addWater, double waterKij) {
    SystemInterface fluid = readString(json);
    if (addWater) {
      EclipseFluidReadWrite.addWaterToFluid(fluid, waterKij);
    }
    return fluid;
  }

  /**
   * Write a fluid to a JSON file.
   *
   * @param fluid the fluid to export
   * @param outputFile path to output file (e.g., "myfluid.json")
   * @throws IOException if writing fails
   */
  public static void write(SystemInterface fluid, String outputFile) throws IOException {
    write(fluid, Paths.get(outputFile));
  }

  /**
   * Write a fluid to a JSON file with a specified reservoir temperature.
   *
   * @param fluid the fluid to export
   * @param outputFile path to output file
   * @param reservoirTempC reservoir temperature in Celsius
   * @throws IOException if writing fails
   */
  public static void write(SystemInterface fluid, String outputFile, double reservoirTempC)
      throws IOException {
    write(fluid, Paths.get(outputFile), reservoirTempC);
  }

  /**
   * Write a fluid to a JSON file at the given path.
   *
   * @param fluid the fluid to export
   * @param outputPath output file path
   * @throws IOException if writing fails
   */
  public static void write(SystemInterface fluid, Path outputPath) throws IOException {
    write(fluid, outputPath, 100.0);
  }

  /**
   * Write a fluid to a JSON file at the given path with a specified reservoir temperature.
   *
   * @param fluid the fluid to export
   * @param outputPath output file path
   * @param reservoirTempC reservoir temperature in Celsius
   * @throws IOException if writing fails
   */
  public static void write(SystemInterface fluid, Path outputPath, double reservoirTempC)
      throws IOException {
    String jsonContent = toJsonString(fluid, reservoirTempC);
    Files.write(outputPath, jsonContent.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Convert a NeqSim fluid to a JSON string with full EOS parameters.
   *
   * @param fluid the fluid to export
   * @return JSON string representing the fluid
   */
  public static String toJsonString(SystemInterface fluid) {
    return toJsonString(fluid, 100.0);
  }

  /**
   * Convert a NeqSim fluid to a JSON string with full EOS parameters and a specified reservoir
   * temperature.
   *
   * @param fluid the fluid to export
   * @param reservoirTempC reservoir temperature in Celsius
   * @return JSON string representing the fluid
   */
  public static String toJsonString(SystemInterface fluid, double reservoirTempC) {
    JsonObject root = new JsonObject();

    // Metadata
    root.addProperty("format", FORMAT_ID);
    root.addProperty("version", FORMAT_VERSION);
    String timestamp =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    root.addProperty("generatedBy", "NeqSim");
    root.addProperty("generatedAt", timestamp);

    // EOS type
    String eosType = getEOSType(fluid);
    root.addProperty("eos", eosType);
    if ("PR".equals(eosType)) {
      root.addProperty("prcorr", fluid.getClass().getSimpleName().toLowerCase().contains("1978"));
    }

    // Reservoir temperature
    JsonObject resTempObj = new JsonObject();
    resTempObj.addProperty("value", reservoirTempC);
    resTempObj.addProperty("unit", "C");
    root.add("reservoirTemperature", resTempObj);

    // Standard conditions
    JsonObject stdCondObj = new JsonObject();
    stdCondObj.addProperty("temperature", 15.0);
    stdCondObj.addProperty("pressure", 1.01325);
    root.add("standardConditions", stdCondObj);

    // Components
    int nComps = fluid.getNumberOfComponents();
    JsonArray componentsArray = new JsonArray();

    for (int i = 0; i < nComps; i++) {
      JsonObject compObj = new JsonObject();
      String name = fluid.getComponent(i).getComponentName();
      compObj.addProperty("name", name);
      compObj.addProperty("moleFraction", fluid.getComponent(i).getz());
      compObj.addProperty("criticalTemperature", fluid.getComponent(i).getTC());
      compObj.addProperty("criticalPressure", fluid.getComponent(i).getPC());
      compObj.addProperty("acentricFactor", fluid.getComponent(i).getAcentricFactor());
      compObj.addProperty("molarMass", fluid.getComponent(i).getMolarMass() * 1000.0);
      compObj.addProperty("normalBoilingPoint", fluid.getComponent(i).getNormalBoilingPoint());
      compObj.addProperty("criticalVolume", fluid.getComponent(i).getCriticalVolume());
      compObj.addProperty("volumeShift", fluid.getComponent(i).getVolumeCorrectionConst());
      compObj.addProperty("parachor", fluid.getComponent(i).getParachorParameter());
      compObj.addProperty("isPseudo", fluid.getComponent(i).isIsTBPfraction());

      if (fluid.getComponent(i).isIsTBPfraction()) {
        compObj.addProperty("density", fluid.getComponent(i).getNormalLiquidDensity());
      }

      componentsArray.add(compObj);
    }
    root.add("components", componentsArray);

    // Binary interaction coefficients
    double[][] kij = getBinaryInteractionParameters(fluid);
    JsonArray bicArray = new JsonArray();
    for (int i = 0; i < nComps; i++) {
      for (int j = i + 1; j < nComps; j++) {
        if (Math.abs(kij[i][j]) > 1e-15) {
          JsonObject bicObj = new JsonObject();
          bicObj.addProperty("i", fluid.getComponent(i).getComponentName());
          bicObj.addProperty("j", fluid.getComponent(j).getComponentName());
          bicObj.addProperty("kij", kij[i][j]);
          bicArray.add(bicObj);
        }
      }
    }
    root.add("binaryInteractionCoefficients", bicArray);

    // Viscosity model
    JsonObject viscObj = buildViscosityModelJson(fluid);
    if (viscObj != null) {
      root.add("viscosityModel", viscObj);
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }

  /**
   * Convert a fluid from E300 format to JSON format.
   *
   * <p>
   * Convenience method that reads an E300 file and writes a JSON file.
   * </p>
   *
   * @param inputE300File path to the E300 input file
   * @param outputJsonFile path to the JSON output file
   * @throws IOException if reading or writing fails
   * @throws IllegalArgumentException if the E300 file is invalid
   */
  public static void convertE300ToJson(String inputE300File, String outputJsonFile)
      throws IOException {
    SystemInterface fluid = EclipseFluidReadWrite.read(inputE300File);
    write(fluid, outputJsonFile);
  }

  /**
   * Convert a fluid from E300 format to JSON format with a specified reservoir temperature.
   *
   * @param inputE300File path to the E300 input file
   * @param outputJsonFile path to the JSON output file
   * @param reservoirTempC reservoir temperature in Celsius
   * @throws IOException if reading or writing fails
   * @throws IllegalArgumentException if the E300 file is invalid
   */
  public static void convertE300ToJson(String inputE300File, String outputJsonFile,
      double reservoirTempC) throws IOException {
    SystemInterface fluid = EclipseFluidReadWrite.read(inputE300File);
    write(fluid, outputJsonFile, reservoirTempC);
  }

  /**
   * Convert a fluid from JSON format to E300 format.
   *
   * <p>
   * Convenience method that reads a JSON file and writes an E300 file.
   * </p>
   *
   * @param inputJsonFile path to the JSON input file
   * @param outputE300File path to the E300 output file
   * @throws IOException if reading or writing fails
   * @throws IllegalArgumentException if the JSON file is invalid
   */
  public static void convertJsonToE300(String inputJsonFile, String outputE300File)
      throws IOException {
    SystemInterface fluid = read(inputJsonFile);
    EclipseFluidReadWrite.write(fluid, outputE300File);
  }

  /**
   * Convert a fluid from JSON format to E300 format with a specified reservoir temperature.
   *
   * @param inputJsonFile path to the JSON input file
   * @param outputE300File path to the E300 output file
   * @param reservoirTempC reservoir temperature in Celsius
   * @throws IOException if reading or writing fails
   * @throws IllegalArgumentException if the JSON file is invalid
   */
  public static void convertJsonToE300(String inputJsonFile, String outputE300File,
      double reservoirTempC) throws IOException {
    SystemInterface fluid = read(inputJsonFile);
    EclipseFluidReadWrite.write(fluid, outputE300File, reservoirTempC);
  }

  // ===== Internal helper methods =====

  /**
   * Create a SystemInterface for the given EOS type.
   *
   * @param eosType the EOS type string (SRK, PR, etc.)
   * @param prcorr whether to use the 1978 PR correction
   * @return a new SystemInterface instance
   */
  private static SystemInterface createFluidByEOS(String eosType, boolean prcorr) {
    double refT = 288.15;
    double refP = ThermodynamicConstantsInterface.referencePressure;

    if ("PR".equals(eosType)) {
      if (prcorr) {
        return new neqsim.thermo.system.SystemPrEos1978(refT, refP);
      } else {
        return new neqsim.thermo.system.SystemPrEos(refT, refP);
      }
    } else {
      // Default to SRK
      return new neqsim.thermo.system.SystemSrkEos(refT, refP);
    }
  }

  /**
   * Parse a single component from its JSON object representation.
   *
   * @param comp the JSON object for the component
   * @return a ComponentData record with all parsed values
   * @throws IllegalArgumentException if required fields are missing
   */
  private static ComponentData parseComponent(JsonObject comp) {
    if (!comp.has("name")) {
      throw new IllegalArgumentException("Component is missing required field 'name'.");
    }
    if (!comp.has("moleFraction")) {
      throw new IllegalArgumentException(
          "Component '" + comp.get("name").getAsString() + "' is missing 'moleFraction'.");
    }

    ComponentData data = new ComponentData();
    data.name = comp.get("name").getAsString();
    data.moleFraction = comp.get("moleFraction").getAsDouble();
    data.criticalTemperature = getDoubleOrDefault(comp, "criticalTemperature", 0.0);
    data.criticalPressure = getDoubleOrDefault(comp, "criticalPressure", 0.0);
    data.acentricFactor = getDoubleOrDefault(comp, "acentricFactor", 0.0);
    data.molarMass = getDoubleOrDefault(comp, "molarMass", 0.0);
    data.normalBoilingPoint = getDoubleOrDefault(comp, "normalBoilingPoint", 0.0);
    data.criticalVolume = getDoubleOrDefault(comp, "criticalVolume", 0.0);
    data.volumeShift = getDoubleOrDefault(comp, "volumeShift", 0.0);
    data.volumeShiftSurface = getDoubleOrDefault(comp, "volumeShiftSurface", 0.0);
    data.parachor = getDoubleOrDefault(comp, "parachor", 0.0);
    data.isPseudo = comp.has("isPseudo") && comp.get("isPseudo").getAsBoolean();
    data.density = getDoubleOrDefault(comp, "density", 0.0);
    return data;
  }

  /**
   * Get a double value from a JSON object, or return a default if the field is missing.
   *
   * @param obj the JSON object
   * @param field the field name
   * @param defaultValue the default value
   * @return the field value or the default
   */
  private static double getDoubleOrDefault(JsonObject obj, String field, double defaultValue) {
    if (obj.has(field) && !obj.get(field).isJsonNull()) {
      return obj.get(field).getAsDouble();
    }
    return defaultValue;
  }

  /**
   * Map E300-style short component names to NeqSim database component names.
   *
   * @param name the input name (e.g., "C1", "N2")
   * @return the NeqSim database name (e.g., "methane", "nitrogen")
   */
  private static String mapToNeqSimName(String name) {
    switch (name) {
      case "C1":
        return "methane";
      case "C2":
        return "ethane";
      case "C3":
        return "propane";
      case "iC4":
        return "i-butane";
      case "C4":
        return "n-butane";
      case "iC5":
        return "i-pentane";
      case "C5":
        return "n-pentane";
      case "C6":
        return "n-hexane";
      case "N2":
        return "nitrogen";
      case "CO2":
        return "CO2";
      case "H2O":
        return "water";
      case "H2S":
        return "H2S";
      default:
        return name;
    }
  }

  /**
   * Apply binary interaction coefficients from a JSON array to the fluid.
   *
   * <p>
   * The array contains objects with "i", "j", and "kij" fields. Components are referenced by name.
   * </p>
   *
   * @param fluid the fluid
   * @param componentNames list of component names in order
   * @param bicArray the JSON array of BIC entries
   */
  private static void applyBinaryInteractionCoefficients(SystemInterface fluid,
      List<String> componentNames, JsonArray bicArray) {
    for (int k = 0; k < bicArray.size(); k++) {
      JsonObject bic = bicArray.get(k).getAsJsonObject();
      String nameI = bic.get("i").getAsString();
      String nameJ = bic.get("j").getAsString();
      double kijValue = bic.get("kij").getAsDouble();

      int idxI = findComponentIndex(fluid, componentNames, nameI);
      int idxJ = findComponentIndex(fluid, componentNames, nameJ);

      if (idxI < 0 || idxJ < 0) {
        logger.warn("BIC references unknown component(s): i='{}', j='{}'", nameI, nameJ);
        continue;
      }

      for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
        ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule()
            .setBinaryInteractionParameter(idxI, idxJ, kijValue);
        ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule()
            .setBinaryInteractionParameter(idxJ, idxI, kijValue);
      }
    }
  }

  /**
   * Find the index of a component in the fluid by name. Tries exact match first, then checks the
   * mapped names in the componentNames list.
   *
   * @param fluid the fluid
   * @param componentNames ordered list of component names
   * @param name the name to find
   * @return the component index, or -1 if not found
   */
  private static int findComponentIndex(SystemInterface fluid, List<String> componentNames,
      String name) {
    // Try exact match in componentNames list first
    for (int i = 0; i < componentNames.size(); i++) {
      if (componentNames.get(i).equals(name)) {
        return i;
      }
    }
    // Try mapped name
    String mapped = mapToNeqSimName(name);
    for (int i = 0; i < componentNames.size(); i++) {
      if (componentNames.get(i).equals(mapped)) {
        return i;
      }
    }
    // Try fluid component lookup
    try {
      return fluid.getComponent(name).getComponentNumber();
    } catch (Exception e) {
      return -1;
    }
  }

  /**
   * Apply viscosity model configuration from JSON to the fluid.
   *
   * @param fluid the fluid
   * @param viscObj the viscosity model JSON object
   */
  private static void applyViscosityModel(SystemInterface fluid, JsonObject viscObj) {
    if (!viscObj.has("type")) {
      return;
    }
    String type = viscObj.get("type").getAsString().toUpperCase();

    if ("LBC".equals(type) && viscObj.has("coefficients")) {
      JsonArray coeffs = viscObj.getAsJsonArray("coefficients");
      if (coeffs.size() >= 5) {
        double[] lbcParams = new double[5];
        for (int i = 0; i < 5; i++) {
          lbcParams[i] = coeffs.get(i).getAsDouble();
        }
        for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
          try {
            fluid.getPhase(phase).getPhysicalProperties().setViscosityModel("LBC");
            fluid.getPhase(phase).getPhysicalProperties().setLbcParameters(lbcParams);
          } catch (Exception e) {
            logger.debug("Could not set LBC model for phase {}: {}", phase, e.getMessage());
          }
        }
      }
    } else if ("PEDERSEN".equals(type) || "PFCT".equals(type)) {
      for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
        try {
          fluid.getPhase(phase).getPhysicalProperties().setViscosityModel("PFCT");
        } catch (Exception e) {
          logger.debug("Could not set PFCT model for phase {}: {}", phase, e.getMessage());
        }
      }
    }
  }

  /**
   * Build a JSON object representing the viscosity model configuration of a fluid.
   *
   * @param fluid the fluid
   * @return a JsonObject, or null if no special viscosity model is configured
   */
  private static JsonObject buildViscosityModelJson(SystemInterface fluid) {
    // Check for PFCT (Pedersen)
    try {
      for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
        if (fluid.getPhase(phase).getPhysicalProperties() != null
            && fluid.getPhase(phase).getPhysicalProperties().isPFCTViscosityModel()) {
          JsonObject obj = new JsonObject();
          obj.addProperty("type", "PEDERSEN");
          return obj;
        }
      }
    } catch (Exception e) {
      // ignore
    }

    // Check for LBC
    try {
      for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
        if (fluid.getPhase(phase).getPhysicalProperties() != null
            && fluid.getPhase(phase).getPhysicalProperties().isLBCViscosityModel()) {
          double[] params = fluid.getPhase(phase).getPhysicalProperties().getLbcParameters();
          if (params != null && params.length >= 5) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "LBC");
            JsonArray coeffArr = new JsonArray();
            for (int i = 0; i < 5; i++) {
              coeffArr.add(params[i]);
            }
            obj.add("coefficients", coeffArr);
            return obj;
          }
        }
      }
    } catch (Exception e) {
      // ignore
    }

    return null;
  }

  /**
   * Determine the EOS type string from the fluid class.
   *
   * @param fluid the fluid
   * @return "SRK" or "PR"
   */
  private static String getEOSType(SystemInterface fluid) {
    String className = fluid.getClass().getSimpleName().toLowerCase();
    if (className.contains("pr")) {
      return "PR";
    }
    return "SRK";
  }

  /**
   * Get the binary interaction parameter matrix from the fluid.
   *
   * @param fluid the fluid
   * @return 2D array of kij values
   */
  private static double[][] getBinaryInteractionParameters(SystemInterface fluid) {
    int n = fluid.getNumberOfComponents();
    double[][] kij = new double[n][n];

    if (fluid.getPhase(0) instanceof PhaseEosInterface) {
      PhaseEosInterface phase = (PhaseEosInterface) fluid.getPhase(0);
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          kij[i][j] = phase.getEosMixingRule().getBinaryInteractionParameter(i, j);
        }
      }
    }
    return kij;
  }

  /**
   * Internal data holder for a parsed component.
   */
  private static class ComponentData {
    /** Component name. */
    String name;
    /** Mole fraction. */
    double moleFraction;
    /** Critical temperature in K. */
    double criticalTemperature;
    /** Critical pressure in bar. */
    double criticalPressure;
    /** Acentric factor. */
    double acentricFactor;
    /** Molar mass in g/mol. */
    double molarMass;
    /** Normal boiling point in K. */
    double normalBoilingPoint;
    /** Critical volume in m3/kmol. */
    double criticalVolume;
    /** Volume shift parameter. */
    double volumeShift;
    /** Volume shift at surface conditions. */
    double volumeShiftSurface;
    /** Parachor for IFT calculation. */
    double parachor;
    /** Whether this is a pseudo-component (TBP fraction). */
    boolean isPseudo;
    /** Liquid density in kg/m3 (for pseudo-components). */
    double density;
  }
}
