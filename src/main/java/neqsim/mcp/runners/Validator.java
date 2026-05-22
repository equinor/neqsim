package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Pre-flight validator for NeqSim JSON inputs (flash or process definitions).
 *
 * <p>
 * Checks the JSON structure and values <em>before</em> running any simulation. Returns a structured
 * JSON response listing all issues found — each with severity, code, message, and remediation hint.
 * This is designed as a "dry-run" MCP tool that catches configuration errors before they cause
 * cryptic simulation failures.
 * </p>
 *
 * <h2>Checks performed:</h2>
 * <ul>
 * <li>Well-formed JSON</li>
 * <li>Required fields present (components for flash; fluid + process for process)</li>
 * <li>Component names valid (via {@link ComponentQuery})</li>
 * <li>Thermodynamic model recognized</li>
 * <li>Flash type recognized (for flash inputs)</li>
 * <li>Temperature and pressure in physically reasonable ranges</li>
 * <li>Composition sums are reasonable (warn if far from 1.0)</li>
 * <li>Equipment types recognized (for process inputs)</li>
 * <li>Required flash specs present (enthalpy for PH, entropy for PS, volume for TV)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class Validator {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Minimum physically reasonable temperature in Kelvin. */
  private static final double MIN_TEMP_K = 1.0;

  /** Maximum physically reasonable temperature in Kelvin. */
  private static final double MAX_TEMP_K = 2000.0;

  /** Minimum physically reasonable pressure in bara. */
  private static final double MIN_PRESS_BARA = 1e-6;

  /** Maximum physically reasonable pressure in bara. */
  private static final double MAX_PRESS_BARA = 10000.0;

  /** Composition sum tolerance for warnings. */
  private static final double COMP_SUM_TOLERANCE = 0.01;

  private static final Set<String> KNOWN_MODELS = Collections.unmodifiableSet(
      new HashSet<String>(Arrays.asList("SRK", "PR", "CPA", "GERG2008", "PCSAFT", "UMRPRU")));

  private static final Set<String> KNOWN_FLASH_TYPES =
      Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("TP", "PH", "PS", "TV",
          "dewPointT", "dewPointP", "bubblePointT", "bubblePointP", "hydrateTP")));

  /**
   * Known process equipment types recognized by JsonProcessBuilder and EquipmentFactory.
   */
  private static final Set<String> KNOWN_EQUIPMENT_TYPES;

  static {
    Set<String> types = new HashSet<String>();
    types.add("Stream");
    types.add("VirtualStream");
    types.add("Separator");
    types.add("ThreePhaseSeparator");
    types.add("GasScrubber");
    types.add("Compressor");
    types.add("Pump");
    types.add("Expander");
    types.add("Heater");
    types.add("Cooler");
    types.add("HeatExchanger");
    types.add("Mixer");
    types.add("Splitter");
    types.add("ComponentSplitter");
    types.add("Valve");
    types.add("ThrottlingValve");
    types.add("Recycle");
    types.add("SetPoint");
    types.add("Adjuster");
    types.add("FlowRateAdjuster");
    types.add("Calculator");
    types.add("SpreadsheetBlock");
    types.add("UnisimCalculator");
    types.add("DistillationColumn");
    types.add("Column");
    types.add("SimpleTEGAbsorber");
    types.add("Tank");
    types.add("SimpleReservoir");
    types.add("Manifold");
    types.add("Flare");
    types.add("FlareStack");
    types.add("FuelCell");
    types.add("Electrolyzer");
    types.add("AdiabaticPipe");
    types.add("Pipe");
    types.add("Pipeline");
    types.add("PipeBeggsAndBrills");
    types.add("WaterHammerPipe");
    types.add("WaterHammer");
    types.add("LiquidHammer");
    types.add("HydraulicTransientPipe");
    types.add("StreamSaturatorUtil");
    types.add("Saturator");
    types.add("Separator_3phase");
    types.add("Separator3phase");
    types.add("Gas_Scrubber");
    types.add("CO2Electrolyzer");
    types.add("CO2Electrolyser");
    types.add("WindTurbine");
    types.add("BatteryStorage");
    types.add("SolarPanel");
    types.add("WindFarm");
    types.add("OffshoreEnergySystem");
    types.add("AmmoniaSynthesisReactor");
    types.add("SubseaPowerCable");
    KNOWN_EQUIPMENT_TYPES = Collections.unmodifiableSet(types);
  }

  /**
   * Private constructor — all methods are static.
   */
  private Validator() {}

  /**
   * Validates a JSON input (flash or process definition) without running any simulation.
   *
   * @param json the JSON string to validate
   * @return a JSON string with validation results: {@code {"valid": true/false, "issues": [...]}}
   */
  public static String validate(String json) {
    List<Issue> issues = new ArrayList<Issue>();

    if (json == null || json.trim().isEmpty()) {
      issues.add(Issue.error("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON flash or process definition"));
      return buildResponse(issues);
    }

    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      issues.add(Issue.error("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed"));
      return buildResponse(issues);
    }

    // Detect input type: "areas" object -> ProcessModel, "process" array -> ProcessSystem,
    // otherwise flash.
    boolean isProcessModel = root.has("areas");
    boolean isProcess = root.has("process");

    if (isProcessModel) {
      validateProcessModelDefinition(root, issues);
    } else if (isProcess) {
      validateProcessDefinition(root, issues);
    } else {
      validateFlashDefinition(root, issues);
    }

    return buildResponse(issues);
  }

  /**
   * Validates a flash definition JSON.
   *
   * @param root the parsed JSON
   * @param issues the issue list to populate
   */
  private static void validateFlashDefinition(JsonObject root, List<Issue> issues) {
    // Model
    if (root.has("model")) {
      String model = root.get("model").getAsString().toUpperCase();
      if (!KNOWN_MODELS.contains(model)) {
        issues.add(Issue.error("UNKNOWN_MODEL",
            "Unknown thermodynamic model: " + root.get("model").getAsString(),
            "Use one of: " + KNOWN_MODELS));
      }
    }

    // Flash type
    if (root.has("flashType")) {
      String flashType = root.get("flashType").getAsString();
      if (!KNOWN_FLASH_TYPES.contains(flashType)) {
        issues.add(Issue.error("UNKNOWN_FLASH_TYPE", "Unknown flash type: " + flashType,
            "Use one of: " + KNOWN_FLASH_TYPES));
      } else {
        // Check required specs for specific flash types
        if ("PH".equals(flashType) && !root.has("enthalpy")) {
          issues.add(Issue.error("MISSING_SPEC", "PH flash requires 'enthalpy' specification",
              "Add: \"enthalpy\": {\"value\": -5000.0, \"unit\": \"J/mol\"}"));
        }
        if ("PS".equals(flashType) && !root.has("entropy")) {
          issues.add(Issue.error("MISSING_SPEC", "PS flash requires 'entropy' specification",
              "Add: \"entropy\": {\"value\": -30.0, \"unit\": \"J/molK\"}"));
        }
        if ("TV".equals(flashType) && !root.has("volume")) {
          issues.add(Issue.error("MISSING_SPEC", "TV flash requires 'volume' specification",
              "Add: \"volume\": {\"value\": 0.001, \"unit\": \"m3/mol\"}"));
        }
      }
    }

    // Temperature
    validateTemperature(root, issues);

    // Pressure
    validatePressure(root, issues);

    // Components (required for flash)
    if (!root.has("components")) {
      issues.add(Issue.error("MISSING_COMPONENTS", "No 'components' specified",
          "Provide a components map, e.g. {\"methane\": 0.85, \"ethane\": 0.15}"));
    } else {
      validateComponents(root.getAsJsonObject("components"), issues);
    }
  }

  /**
   * Validates a process definition JSON.
   *
   * @param root the parsed JSON
   * @param issues the issue list to populate
   */
  private static void validateProcessDefinition(JsonObject root, List<Issue> issues) {
    // Fluid definition
    if (root.has("fluid")) {
      validateFluidBlock(root.getAsJsonObject("fluid"), issues);
    } else if (!root.has("fluids")) {
      issues.add(Issue.warning("NO_FLUID", "No 'fluid' or 'fluids' block defined",
          "Most process definitions need a fluid. Add a 'fluid' block with model, "
              + "components, etc."));
    }

    // Process array
    if (!root.has("process")) {
      issues.add(Issue.error("MISSING_PROCESS", "No 'process' array defined",
          "Add a 'process' array with equipment definitions"));
      return;
    }

    JsonElement processEl = root.get("process");
    if (!processEl.isJsonArray()) {
      issues.add(Issue.error("INVALID_PROCESS", "'process' must be a JSON array",
          "Change 'process' to an array of equipment objects"));
      return;
    }

    JsonArray processArray = processEl.getAsJsonArray();
    if (processArray.size() == 0) {
      issues.add(Issue.warning("EMPTY_PROCESS", "Process array is empty",
          "Add equipment definitions to the process array"));
      return;
    }

    Set<String> definedNames = new HashSet<String>();
    for (int i = 0; i < processArray.size(); i++) {
      JsonObject unit = processArray.get(i).getAsJsonObject();
      validateProcessUnit(unit, i, definedNames, issues);
    }
  }

  /**
   * Validates a multi-area ProcessModel JSON definition.
   *
   * @param root the parsed root JSON object
   * @param issues the issue list to populate
   */
  private static void validateProcessModelDefinition(JsonObject root, List<Issue> issues) {
    if (!root.get("areas").isJsonObject()) {
      issues.add(Issue.error("INVALID_AREAS", "'areas' must be a JSON object",
          "Use {\"areas\": {\"areaName\": {\"fluid\": {...}, \"process\": [...]}}}"));
      return;
    }

    JsonObject areas = root.getAsJsonObject("areas");
    if (areas.entrySet().isEmpty()) {
      issues.add(Issue.error("EMPTY_AREAS", "ProcessModel contains no process areas",
          "Add at least one named process area under the 'areas' object"));
      return;
    }

    for (Map.Entry<String, JsonElement> entry : areas.entrySet()) {
      String areaName = entry.getKey();
      if (!entry.getValue().isJsonObject()) {
        issues.add(Issue.error("INVALID_AREA", "Area '" + areaName + "' must be a JSON object",
            "Provide each area as a standard process JSON object"));
        continue;
      }
      List<Issue> areaIssues = new ArrayList<Issue>();
      validateProcessDefinition(entry.getValue().getAsJsonObject(), areaIssues);
      for (Issue issue : areaIssues) {
        issues.add(issue.withPrefix("Area '" + areaName + "': "));
      }
    }
  }

  /**
   * Validates a fluid block (used in process definitions).
   *
   * @param fluidDef the fluid JSON object
   * @param issues the issue list to populate
   */
  private static void validateFluidBlock(JsonObject fluidDef, List<Issue> issues) {
    if (fluidDef.has("model")) {
      String model = fluidDef.get("model").getAsString().toUpperCase();
      if (!KNOWN_MODELS.contains(model)) {
        issues.add(Issue.error("UNKNOWN_MODEL",
            "Unknown thermodynamic model in fluid: " + fluidDef.get("model").getAsString(),
            "Use one of: " + KNOWN_MODELS));
      }
    }

    if (fluidDef.has("components")) {
      validateComponents(fluidDef.getAsJsonObject("components"), issues);
    } else {
      issues.add(Issue.error("MISSING_COMPONENTS", "Fluid block has no 'components'",
          "Add a components map to the fluid definition"));
    }
  }

  /**
   * Validates a single process unit definition.
   *
   * @param unit the unit JSON object
   * @param index the unit index in the process array
   * @param definedNames set of equipment names defined so far
   * @param issues the issue list to populate
   */
  private static void validateProcessUnit(JsonObject unit, int index, Set<String> definedNames,
      List<Issue> issues) {
    if (!unit.has("type")) {
      issues.add(Issue.error("MISSING_TYPE", "Unit at index " + index + " has no 'type' field",
          "Add a 'type' field (e.g., 'Stream', 'Separator', 'Compressor')"));
      return;
    }

    String type = unit.get("type").getAsString();

    // Check if type is known (case-insensitive with separator-insensitive aliases)
    boolean typeKnown = false;
    for (String known : KNOWN_EQUIPMENT_TYPES) {
      if (known.equalsIgnoreCase(type)
          || normalizeEquipmentType(known).equalsIgnoreCase(normalizeEquipmentType(type))) {
        typeKnown = true;
        break;
      }
    }
    if (!typeKnown) {
      issues.add(Issue.warning("UNKNOWN_EQUIPMENT_TYPE",
          "Unknown equipment type '" + type + "' at index " + index,
          "Known types include: Stream, Separator, Compressor, Heater, Cooler, Mixer, "
              + "Splitter, Valve, Pump, Recycle, HeatExchanger, DistillationColumn, Pipe, "
              + "Expander, Tank, Flare, SimpleReservoir, and power equipment"));
    }

    // Name uniqueness
    String name = unit.has("name") ? unit.get("name").getAsString() : type + "_" + (index + 1);
    if (definedNames.contains(name)) {
      issues.add(Issue.warning("DUPLICATE_NAME",
          "Duplicate equipment name '" + name + "' at index " + index,
          "Use unique names for each equipment to avoid confusion"));
    }
    definedNames.add(name);

    // Inlet reference format check (not resolving, just checking format)
    if (unit.has("inlet")) {
      String inlet = unit.get("inlet").getAsString();
      if (inlet.trim().isEmpty()) {
        issues.add(
            Issue.warning("EMPTY_INLET", "Equipment '" + name + "' has empty 'inlet' reference",
                "Provide a valid inlet reference (equipment name or name.portName)"));
      }
    }
  }

  /**
   * Normalizes an equipment type for alias matching.
   *
   * @param equipmentType the raw equipment type
   * @return the normalized equipment type with whitespace, underscore, and hyphen removed
   */
  private static String normalizeEquipmentType(String equipmentType) {
    return equipmentType == null ? "" : equipmentType.replaceAll("[\\s_-]", "");
  }

  /**
   * Validates a components map — checks names and composition sum.
   *
   * @param components the components JSON object
   * @param issues the issue list to populate
   */
  private static void validateComponents(JsonObject components, List<Issue> issues) {
    if (components.entrySet().isEmpty()) {
      issues.add(Issue.error("EMPTY_COMPONENTS", "Components map is empty",
          "Provide at least one component"));
      return;
    }

    double sum = 0.0;
    for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
      String name = entry.getKey();

      // Validate component name
      if (!ComponentQuery.isValid(name)) {
        String suggestion = ComponentQuery.closestMatch(name);
        String msg = "Unknown component: '" + name + "'";
        if (suggestion != null) {
          msg += ". Did you mean '" + suggestion + "'?";
        }
        issues.add(Issue.error("UNKNOWN_COMPONENT", msg,
            "Use ComponentQuery.search() to find valid component names"));
      }

      // Validate value
      try {
        double value = entry.getValue().getAsDouble();
        if (value < 0.0) {
          issues.add(Issue.error("NEGATIVE_FRACTION",
              "Component '" + name + "' has negative mole fraction: " + value,
              "Mole fractions must be non-negative"));
        }
        sum += value;
      } catch (Exception e) {
        issues.add(Issue.error("INVALID_FRACTION",
            "Component '" + name + "' has invalid mole fraction value",
            "Mole fractions must be numbers"));
      }
    }

    // Check composition sum
    if (sum > 0 && Math.abs(sum - 1.0) > COMP_SUM_TOLERANCE) {
      issues.add(Issue.warning("COMPOSITION_SUM",
          "Component mole fractions sum to " + String.format("%.4f", sum) + " (expected ~1.0)",
          "NeqSim will normalize but this may indicate an error"));
    }
  }

  /**
   * Validates temperature if present.
   *
   * @param root the root JSON
   * @param issues the issue list to populate
   */
  private static void validateTemperature(JsonObject root, List<Issue> issues) {
    if (!root.has("temperature")) {
      return;
    }

    double tempK = FlashRunner.parseTemperature(root.get("temperature"));
    if (Double.isNaN(tempK)) {
      issues.add(Issue.error("INVALID_TEMPERATURE", "Cannot parse temperature specification",
          "Provide {\"value\": 25.0, \"unit\": \"C\"} or a number (Kelvin)"));
    } else if (tempK < MIN_TEMP_K || tempK > MAX_TEMP_K) {
      issues.add(Issue.warning("TEMPERATURE_RANGE",
          "Temperature " + String.format("%.1f", tempK) + " K is outside typical range ("
              + MIN_TEMP_K + " - " + MAX_TEMP_K + " K)",
          "Check units — NeqSim expects Kelvin if no unit is specified"));
    }
  }

  /**
   * Validates pressure if present.
   *
   * @param root the root JSON
   * @param issues the issue list to populate
   */
  private static void validatePressure(JsonObject root, List<Issue> issues) {
    if (!root.has("pressure")) {
      return;
    }

    double pressBara = FlashRunner.parsePressure(root.get("pressure"));
    if (Double.isNaN(pressBara)) {
      issues.add(Issue.error("INVALID_PRESSURE", "Cannot parse pressure specification",
          "Provide {\"value\": 50.0, \"unit\": \"bara\"} or a number (bara)"));
    } else if (pressBara < MIN_PRESS_BARA || pressBara > MAX_PRESS_BARA) {
      issues.add(Issue.warning("PRESSURE_RANGE",
          "Pressure " + String.format("%.4f", pressBara) + " bara is outside typical range ("
              + MIN_PRESS_BARA + " - " + MAX_PRESS_BARA + " bara)",
          "Check units — NeqSim expects bara if no unit is specified"));
    }
  }

  /**
   * Builds the JSON response from the list of issues.
   *
   * @param issues the collected issues
   * @return JSON string with validation results
   */
  private static String buildResponse(List<Issue> issues) {
    JsonObject result = new JsonObject();

    boolean hasErrors = false;
    for (Issue issue : issues) {
      if ("error".equals(issue.severity)) {
        hasErrors = true;
        break;
      }
    }

    result.addProperty("valid", !hasErrors);

    JsonArray issueArray = new JsonArray();
    for (Issue issue : issues) {
      issueArray.add(issue.toJson());
    }
    result.add("issues", issueArray);

    return GSON.toJson(result);
  }

  /**
   * Represents a single validation issue.
   */
  static class Issue {
    final String severity;
    final String code;
    final String message;
    final String remediation;

    /**
     * Creates an issue.
     *
     * @param severity "error" or "warning"
     * @param code machine-readable issue code
     * @param message human-readable description
     * @param remediation how to fix the issue
     */
    Issue(String severity, String code, String message, String remediation) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.remediation = remediation;
    }

    /**
     * Creates an error-level issue.
     *
     * @param code the issue code
     * @param message the description
     * @param remediation how to fix
     * @return the issue
     */
    static Issue error(String code, String message, String remediation) {
      return new Issue("error", code, message, remediation);
    }

    /**
     * Creates a warning-level issue.
     *
     * @param code the issue code
     * @param message the description
     * @param remediation how to fix
     * @return the issue
     */
    static Issue warning(String code, String message, String remediation) {
      return new Issue("warning", code, message, remediation);
    }

    /**
     * Creates a copy of this issue with a prefixed message.
     *
     * @param prefix text to prepend to the message
     * @return the prefixed issue
     */
    Issue withPrefix(String prefix) {
      return new Issue(severity, code, prefix + message, remediation);
    }

    /**
     * Converts this issue to a JsonObject.
     *
     * @return JSON representation
     */
    JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("severity", severity);
      obj.addProperty("code", code);
      obj.addProperty("message", message);
      if (remediation != null) {
        obj.addProperty("remediation", remediation);
      }
      return obj;
    }
  }
}
