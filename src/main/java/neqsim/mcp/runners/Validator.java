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
import neqsim.mcp.catalog.SchemaCatalog;
import neqsim.mcp.catalog.SchemaChecker;
import neqsim.process.equipment.EquipmentFactory;

/**
 * Pre-flight validator for NeqSim JSON inputs (flash or process definitions).
 *
 * <p>
 * Checks the JSON structure and values <em>before</em> running any simulation. Returns a structured JSON response
 * listing all issues found — each with severity, code, message, and remediation hint. This is designed as a "dry-run"
 * MCP tool that catches configuration errors before they cause cryptic simulation failures.
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

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

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

  private static final Set<String> KNOWN_MODELS = Collections
      .unmodifiableSet(new HashSet<String>(Arrays.asList("SRK", "PR", "CPA", "GERG2008", "PCSAFT", "UMRPRU")));

  private static final Set<String> KNOWN_FLASH_TYPES = Collections.unmodifiableSet(new HashSet<String>(
      Arrays.asList("TP", "PH", "PS", "TV", "dewPointT", "dewPointP", "bubblePointT", "bubblePointP", "hydrateTP")));

  /**
   * Private constructor — all methods are static.
   */
  private Validator() {
  }

  /**
   * Validates a JSON input (flash or process definition) without running any simulation.
   *
   * <p>
   * Accepts the same input forms as {@code runProcess}: inline JSON or a path to a readable {@code .json} file, so a
   * caller can validate exactly what it intends to run.
   * </p>
   *
   * @param json the JSON string to validate, or a path to a {@code .json} file containing it
   * @return a JSON string with validation results: {@code {"valid": true/false, "issues": [...]}}
   */
  public static String validate(String json) {
    String resolved = ProcessRunner.resolveJsonInput(json);
    List<Issue> issues = new ArrayList<Issue>();

    if (resolved == null || resolved.trim().isEmpty()) {
      issues.add(Issue.error("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON flash or process definition"));
      return buildResponse(issues);
    }

    JsonObject root;
    try {
      root = JsonParser.parseString(resolved).getAsJsonObject();
    } catch (Exception e) {
      issues.add(
          Issue.error("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(), "Ensure the JSON is well-formed"));
      return buildResponse(issues);
    }

    // Detect input type: "areas" object -> ProcessModel, "process" array -> ProcessSystem,
    // otherwise flash.
    String toolScope = resolveToolScope(root);
    root = unwrapEnvelope(root, issues);
    if (toolScope != null) {
      validateAgainstToolSchema(toolScope, root, issues);
      if (!"run_process".equals(toolScope) && !"run_flash".equals(toolScope) && !"validate_input".equals(toolScope)) {
        return buildResponse(issues);
      }
    }
    boolean isProcessModel = root.has("areas");
    boolean isProcess = root.has("process");

    if (isProcessModel) {
      validateProcessModelDefinition(root, issues);
    } else if (isProcess) {
      validateProcessDefinition(root, issues);
    } else if (!root.has("components") && looksLikeProcessAttempt(root)) {
      issues.add(Issue.error("UNRECOGNIZED_INPUT_SHAPE",
          "Input has " + describeKeys(root) + " but neither a 'process' array (ProcessSystem), an 'areas' object "
              + "(ProcessModel) nor a top-level 'components' map (flash)",
          "For a process use {\"fluid\": {...}, \"process\": [{\"type\": \"Stream\", \"name\": \"feed\", "
              + "\"properties\": {\"flowRate\": [v, \"kg/hr\"]}}, {\"type\": \"Compressor\", \"inlet\": \"feed\", "
              + "\"properties\": {\"outletPressure\": 50.0}}]}; see getExample(process, simple-separation). "
              + "For a flash pass components, model, temperature and pressure at the top level."));
    } else {
      validateFlashDefinition(root, issues);
    }

    return buildResponse(issues);
  }

  /**
   * Unit-level keys that JsonProcessBuilder reads (plus the legacy flowRate/temperature/pressure shorthand that
   * ProcessRunner normalizes into properties); anything else on a unit is silently ignored by the builder.
   */
  private static final Set<String> UNIT_LEVEL_KEYS = Collections
      .unmodifiableSet(new HashSet<String>(Arrays.asList("type", "name", "tagName", "inlet", "inlets", "properties",
          "fluidRef", "composition", "description", "comment", "flowRate", "temperature", "pressure")));

  /** Wrapper keys under which callers sometimes nest the actual definition. */
  private static final List<String> WRAPPER_KEYS = Collections
      .unmodifiableList(Arrays.asList("input", "inputJson", "processJson", "flashJson", "definition", "payload"));

  /**
   * Unwraps a definition nested under a wrapper key such as {@code {"tool": "run_process", "input": {...}}}.
   *
   * @param root parsed root object
   * @param issues issue list; receives a warning when an envelope was unwrapped
   * @return the definition object to validate
   */
  private static JsonObject unwrapEnvelope(JsonObject root, List<Issue> issues) {
    if (root.has("process") || root.has("areas") || root.has("components")) {
      return root;
    }
    boolean toolScoped = root.has("tool");
    for (String key : WRAPPER_KEYS) {
      if (root.has(key) && root.get(key).isJsonObject()) {
        JsonObject inner = root.getAsJsonObject(key);
        if (toolScoped || inner.has("process") || inner.has("areas") || inner.has("components")) {
          if (!toolScoped) {
            issues.add(
                Issue.warning("WRAPPED_INPUT", "Definition was nested under '" + key + "'; validated the inner object",
                    "Pass the flash or process JSON directly as the tool argument, without a wrapper object"));
          }
          return inner;
        }
      }
    }
    return root;
  }

  /**
   * Heuristic for a caller that intended a process definition but used the wrong top-level grammar.
   *
   * @param root parsed root object
   * @return true when the object carries process-like keys
   */
  private static boolean looksLikeProcessAttempt(JsonObject root) {
    for (String key : Arrays.asList("fluid", "fluids", "units", "equipment", "flowsheet", "streams", "unitOperations",
        "operations", "tool", "input")) {
      if (root.has(key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Lists the top-level keys of an object for diagnostics.
   *
   * @param root object to describe
   * @return quoted, comma-separated key list
   */
  private static String describeKeys(JsonObject root) {
    List<String> keys = new ArrayList<String>();
    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
      keys.add("'" + entry.getKey() + "'");
    }
    return "keys " + keys;
  }

  /**
   * Resolves the tool a caller wants to validate against from a {@code {"tool": "...", "input": {...}}} envelope.
   *
   * @param root parsed root object
   * @return snake_case tool name known to {@link SchemaCatalog}, or null when the input is not tool-scoped
   */
  private static String resolveToolScope(JsonObject root) {
    if (!root.has("tool") || !root.get("tool").isJsonPrimitive()) {
      return null;
    }
    String snake = SchemaCatalog.normalizeToolName(root.get("tool").getAsString());
    return SchemaCatalog.getToolNames().contains(snake) ? snake : null;
  }

  /**
   * Validates a tool input against its catalog schema and reports each violation as an error issue.
   *
   * @param toolName snake_case tool name
   * @param input the (unwrapped) tool input object
   * @param issues issue list to populate
   */
  private static void validateAgainstToolSchema(String toolName, JsonObject input, List<Issue> issues) {
    if (!SchemaCatalog.hasDetailedInputSchema(toolName)) {
      issues.add(Issue.warning("NO_DETAILED_SCHEMA",
          "Tool '" + toolName + "' has only a generic schema; field names could not be checked",
          "Call getExample(category='tool', name='" + toolName + "') for a working template"));
      return;
    }
    List<String> violations = SchemaChecker.checkToolInput(toolName, input);
    for (String violation : violations) {
      issues.add(Issue.error("SCHEMA_VIOLATION", violation,
          "Compare with getSchema('" + toolName + "', 'input'); field names and units are listed per property"));
    }
    if (violations.isEmpty()) {
      issues.add(Issue.info("SCHEMA_OK", "Input satisfies the " + toolName + " input schema",
          "Run the tool; still inspect convergence and warnings in the result"));
    }
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
        issues.add(Issue.error("UNKNOWN_MODEL", "Unknown thermodynamic model: " + root.get("model").getAsString(),
            "Use one of: " + KNOWN_MODELS));
      }
    }

    // Flash type
    if (root.has("flashType")) {
      String flashType = root.get("flashType").getAsString();
      if (!KNOWN_FLASH_TYPES.contains(flashType)) {
        issues.add(
            Issue.error("UNKNOWN_FLASH_TYPE", "Unknown flash type: " + flashType, "Use one of: " + KNOWN_FLASH_TYPES));
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
    validateProcessDefinition(root, issues, Collections.<String>emptySet());
  }

  /**
   * Validates a process definition JSON, exempting units whose inlet is supplied from outside the area.
   *
   * @param root the parsed JSON
   * @param issues the issue list to populate
   * @param externallyFedUnits unit names whose inlets are wired by interAreaLinks and may be unresolved locally
   */
  private static void validateProcessDefinition(JsonObject root, List<Issue> issues, Set<String> externallyFedUnits) {
    // Fluid definition
    if (root.has("fluid")) {
      validateFluidBlock(root.getAsJsonObject("fluid"), issues);
    } else if (!root.has("fluids")) {
      issues.add(Issue.warning("NO_FLUID", "No 'fluid' or 'fluids' block defined",
          "Most process definitions need a fluid. Add a 'fluid' block with model, " + "components, etc."));
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
      issues.add(
          Issue.warning("EMPTY_PROCESS", "Process array is empty", "Add equipment definitions to the process array"));
      return;
    }

    Set<String> definedNames = new HashSet<String>();
    Set<String> allNames = collectUnitNames(processArray);
    for (int i = 0; i < processArray.size(); i++) {
      JsonObject unit = processArray.get(i).getAsJsonObject();
      validateProcessUnit(unit, i, definedNames, issues);
      validateInletReferences(unit, i, allNames, externallyFedUnits, issues);
    }
  }

  /**
   * Collects every unit name in a process array (explicit or generated), so forward references resolve.
   *
   * @param processArray process unit array
   * @return set of unit names
   */
  private static Set<String> collectUnitNames(JsonArray processArray) {
    Set<String> names = new HashSet<String>();
    for (int i = 0; i < processArray.size(); i++) {
      JsonElement el = processArray.get(i);
      if (!el.isJsonObject()) {
        continue;
      }
      JsonObject unit = el.getAsJsonObject();
      String type = unit.has("type") ? unit.get("type").getAsString() : "";
      names.add(unit.has("name") ? unit.get("name").getAsString() : type + "_" + (i + 1));
    }
    return names;
  }

  /**
   * Checks that every {@code inlet}/{@code inlets} reference names a unit in the same process array. The builder only
   * warns and then runs the disconnected flowsheet, so this is promoted to a pre-flight error.
   *
   * @param unit unit definition
   * @param index unit index
   * @param allNames every unit name in the array
   * @param externallyFedUnits units whose inlets are supplied by interAreaLinks
   * @param issues issue list to populate
   */
  private static void validateInletReferences(JsonObject unit, int index, Set<String> allNames,
      Set<String> externallyFedUnits, List<Issue> issues) {
    String type = unit.has("type") ? unit.get("type").getAsString() : "";
    String name = unit.has("name") ? unit.get("name").getAsString() : type + "_" + (index + 1);
    if (externallyFedUnits.contains(name)) {
      return;
    }
    List<String> refs = new ArrayList<String>();
    if (unit.has("inlets") && unit.get("inlets").isJsonArray()) {
      for (JsonElement el : unit.getAsJsonArray("inlets")) {
        if (el.isJsonPrimitive()) {
          refs.add(el.getAsString());
        }
      }
    } else if (unit.has("inlet") && unit.get("inlet").isJsonPrimitive()) {
      refs.add(unit.get("inlet").getAsString());
    }
    for (String ref : refs) {
      String trimmed = ref.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String unitPart = trimmed.contains(".") ? trimmed.substring(0, trimmed.indexOf('.')) : trimmed;
      if (!allNames.contains(unitPart) && !allNames.contains(trimmed)) {
        issues.add(Issue.error("UNRESOLVED_INLET",
            "Equipment '" + name + "' references inlet '" + ref + "' but no unit with that name is defined",
            "Define the source as a unit in the 'process' array (feeds are units of type 'Stream', e.g. "
                + "{\"type\": \"Stream\", \"name\": \"" + unitPart
                + "\", \"properties\": {\"flowRate\": [v, \"kg/hr\"]}}), "
                + "or reference an existing unit's port as 'unitName.port' (gasOut, liquidOut, out, splitStream_0). "
                + "Defined units: " + allNames));
      }
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

    Map<String, Set<String>> linkTargets = collectInterAreaLinkTargets(root);

    for (Map.Entry<String, JsonElement> entry : areas.entrySet()) {
      String areaName = entry.getKey();
      if (!entry.getValue().isJsonObject()) {
        issues.add(Issue.error("INVALID_AREA", "Area '" + areaName + "' must be a JSON object",
            "Provide each area as a standard process JSON object"));
        continue;
      }
      List<Issue> areaIssues = new ArrayList<Issue>();
      Set<String> fed = linkTargets.containsKey(areaName) ? linkTargets.get(areaName) : Collections.<String>emptySet();
      validateProcessDefinition(entry.getValue().getAsJsonObject(), areaIssues, fed);
      for (Issue issue : areaIssues) {
        issues.add(issue.withPrefix("Area '" + areaName + "': "));
      }
    }
  }

  /**
   * Collects, per area, the unit names that receive an inlet through {@code interAreaLinks}.
   *
   * @param root ProcessModel root object
   * @return map of area name to externally fed unit names
   */
  private static Map<String, Set<String>> collectInterAreaLinkTargets(JsonObject root) {
    Map<String, Set<String>> targets = new java.util.HashMap<String, Set<String>>();
    if (!root.has("interAreaLinks") || !root.get("interAreaLinks").isJsonArray()) {
      return targets;
    }
    for (JsonElement el : root.getAsJsonArray("interAreaLinks")) {
      if (!el.isJsonObject()) {
        continue;
      }
      JsonObject link = el.getAsJsonObject();
      if (link.has("targetArea") && link.has("targetUnit")) {
        String area = link.get("targetArea").getAsString();
        if (!targets.containsKey(area)) {
          targets.put(area, new HashSet<String>());
        }
        targets.get(area).add(link.get("targetUnit").getAsString());
      }
    }
    return targets;
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
        issues.add(
            Issue.error("UNKNOWN_MODEL", "Unknown thermodynamic model in fluid: " + fluidDef.get("model").getAsString(),
                "Use one of: " + KNOWN_MODELS));
      }
    }

    // A fluid may be defined by an inline components map, characterized
    // (TBP/plus) pseudo-components, or a reference to an Eclipse E300 fluid
    // file. JsonProcessBuilder resolves any of these, so accept them all here.
    if (fluidDef.has("components")) {
      validateComponents(fluidDef.getAsJsonObject("components"), issues);
    } else if (!fluidDef.has("characterizedComponents") && !fluidDef.has("e300FilePath")) {
      issues.add(Issue.error("MISSING_COMPONENTS",
          "Fluid block has no 'components', 'characterizedComponents' or 'e300FilePath'",
          "Add a components map, characterizedComponents array, or an e300FilePath " + "to the fluid definition"));
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
  private static void validateProcessUnit(JsonObject unit, int index, Set<String> definedNames, List<Issue> issues) {
    if (!unit.has("type")) {
      issues.add(Issue.error("MISSING_TYPE", "Unit at index " + index + " has no 'type' field",
          "Add a 'type' field (e.g., 'Stream', 'Separator', 'Compressor')"));
      return;
    }

    String type = unit.get("type").getAsString();

    if (!EquipmentFactory.supportsEquipmentType(type)) {
      issues.add(Issue.warning("UNKNOWN_EQUIPMENT_TYPE", "Unknown equipment type '" + type + "' at index " + index,
          "Known types include: Stream, Separator, Compressor, Heater, Cooler, Mixer, "
              + "Splitter, Valve, Pump, Recycle, HeatExchanger, DistillationColumn, Pipe, "
              + "Expander, Tank, Flare, SimpleReservoir, and power equipment"));
    }

    // Name uniqueness
    String name = unit.has("name") ? unit.get("name").getAsString() : type + "_" + (index + 1);
    if (definedNames.contains(name)) {
      issues.add(Issue.warning("DUPLICATE_NAME", "Duplicate equipment name '" + name + "' at index " + index,
          "Use unique names for each equipment to avoid confusion"));
    }
    definedNames.add(name);

    // Inlet reference format check (not resolving, just checking format)
    if (unit.has("inlet")) {
      String inlet = unit.get("inlet").getAsString();
      if (inlet.trim().isEmpty()) {
        issues.add(Issue.warning("EMPTY_INLET", "Equipment '" + name + "' has empty 'inlet' reference",
            "Provide a valid inlet reference (equipment name or name.portName)"));
      }
    }

    // Parameters placed beside 'properties' are ignored by the builder, so the unit silently runs on defaults.
    List<String> misplaced = new ArrayList<String>();
    for (Map.Entry<String, JsonElement> entry : unit.entrySet()) {
      String key = entry.getKey();
      if (!UNIT_LEVEL_KEYS.contains(key) && !key.startsWith("_")) {
        misplaced.add(key);
      }
    }
    if (!misplaced.isEmpty()) {
      issues.add(Issue.error("MISPLACED_UNIT_PARAMETERS",
          "Equipment '" + name + "' has parameters " + misplaced
              + " at unit level; the builder ignores them and the unit would run with defaults",
          "Move equipment parameters under \"properties\", e.g. {\"type\": \"" + type + "\", \"name\": \"" + name
              + "\", \"inlet\": \"...\", \"properties\": {\"outletPressure\": 50.0, \"outTemperature\": [35.0, \"C\"]}}. "
              + "Property names are unit-free (outletPressure in bara, or a [value, unit] pair); see "
              + "getSchema(run_process, input) for the per-equipment property list."));
    }
  }

  /**
   * Validates a components map — checks names and composition sum.
   *
   * @param components the components JSON object
   * @param issues the issue list to populate
   */
  private static void validateComponents(JsonObject components, List<Issue> issues) {
    if (components.entrySet().isEmpty()) {
      issues.add(Issue.error("EMPTY_COMPONENTS", "Components map is empty", "Provide at least one component"));
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
        issues.add(Issue.error("UNKNOWN_COMPONENT", msg, "Use ComponentQuery.search() to find valid component names"));
      }

      // Validate value
      try {
        double value = entry.getValue().getAsDouble();
        if (value < 0.0) {
          issues.add(Issue.error("NEGATIVE_FRACTION", "Component '" + name + "' has negative mole fraction: " + value,
              "Mole fractions must be non-negative"));
        }
        sum += value;
      } catch (Exception e) {
        issues.add(Issue.error("INVALID_FRACTION", "Component '" + name + "' has invalid mole fraction value",
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
      issues.add(Issue.warning(
          "TEMPERATURE_RANGE", "Temperature " + String.format("%.1f", tempK) + " K is outside typical range ("
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
      issues.add(Issue.warning(
          "PRESSURE_RANGE", "Pressure " + String.format("%.4f", pressBara) + " bara is outside typical range ("
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
     * Creates an informational issue that never affects validity.
     *
     * @param code the issue code
     * @param message the description
     * @param remediation next step
     * @return the issue
     */
    static Issue info(String code, String message, String remediation) {
      return new Issue("info", code, message, remediation);
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
