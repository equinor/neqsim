package neqsim.mcp.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Catalog of JSON schemas for MCP tool input and output declarations.
 *
 * <p>
 * Provides JSON Schema strings describing the input/output format of each MCP tool. These schemas
 * are designed to be served as MCP Resources via URIs like {@code neqsim://schema/run_flash/input},
 * enabling language models to understand the expected data format without trial and error.
 * </p>
 *
 * <p>
 * Schemas follow JSON Schema Draft 2020-12 format and can also be used as
 * {@code inputSchema}/{@code outputSchema} declarations in MCP tool definitions.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SchemaCatalog {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /**
   * Private constructor — all methods are static.
   */
  private SchemaCatalog() {}

  // ========== Flash Schemas ==========

  /**
   * Returns the JSON Schema for flash calculation input.
   *
   * @return JSON Schema string
   */
  public static String flashInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "FlashInput");
    schema.put("description", "Input for a flash calculation (run_flash tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    // model
    Map<String, Object> model = new LinkedHashMap<String, Object>();
    model.put("type", "string");
    model.put("description", "Thermodynamic model to use");
    model.put("enum", Arrays.asList("SRK", "PR", "CPA", "GERG2008", "PCSAFT", "UMRPRU"));
    model.put("default", "SRK");
    properties.put("model", model);

    // temperature
    properties.put("temperature",
        valueWithUnitSchema("Temperature specification",
            "Temperature unit (K=Kelvin, C=Celsius, F=Fahrenheit, R=Rankine)",
            Arrays.asList("K", "C", "F", "R"), "K"));

    // pressure
    properties.put("pressure", valueWithUnitSchema("Pressure specification", "Pressure unit",
        Arrays.asList("bara", "barg", "Pa", "kPa", "MPa", "psi", "psia", "atm"), "bara"));

    // flashType
    Map<String, Object> flashType = new LinkedHashMap<String, Object>();
    flashType.put("type", "string");
    flashType.put("description", "Type of flash calculation to perform");
    flashType.put("enum", Arrays.asList("TP", "PH", "PS", "TV", "dewPointT", "dewPointP",
        "bubblePointT", "bubblePointP", "hydrateTP"));
    flashType.put("default", "TP");
    properties.put("flashType", flashType);

    // components
    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description",
        "Component name to mole fraction map. Use ComponentQuery to find valid names.");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    addProps.put("minimum", 0);
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    // mixingRule
    Map<String, Object> mixingRule = new LinkedHashMap<String, Object>();
    mixingRule.put("type", "string");
    mixingRule.put("description", "Mixing rule. Use 'classic' for SRK/PR, '10' for CPA.");
    mixingRule.put("default", "classic");
    properties.put("mixingRule", mixingRule);

    // enthalpy, entropy, volume (optional for PH/PS/TV flashes)
    properties.put("enthalpy", valueWithUnitSchema("Enthalpy spec for PH flash", "Enthalpy unit",
        Arrays.asList("J/mol", "kJ/mol", "J/kg", "kJ/kg"), "J/mol"));
    properties.put("entropy", valueWithUnitSchema("Entropy spec for PS flash", "Entropy unit",
        Arrays.asList("J/molK", "kJ/molK", "J/kgK"), "J/molK"));
    properties.put("volume", valueWithUnitSchema("Volume spec for TV flash", "Volume unit",
        Arrays.asList("m3/mol", "m3", "L"), "m3/mol"));

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("components"));

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for flash calculation output.
   *
   * @return JSON Schema string
   */
  public static String flashOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "FlashOutput");
    schema.put("description", "Output of a flash calculation (run_flash tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    // status
    Map<String, Object> status = new LinkedHashMap<String, Object>();
    status.put("type", "string");
    status.put("enum", Arrays.asList("success", "error"));
    properties.put("status", status);

    // flash metadata
    Map<String, Object> flash = new LinkedHashMap<String, Object>();
    flash.put("type", "object");
    flash.put("description", "Flash calculation metadata");
    Map<String, Object> flashProps = new LinkedHashMap<String, Object>();
    flashProps.put("model", stringProp("Thermodynamic model used"));
    flashProps.put("flashType", stringProp("Flash type performed"));
    flashProps.put("numberOfPhases", intProp("Number of phases found"));
    Map<String, Object> phases = new LinkedHashMap<String, Object>();
    phases.put("type", "array");
    Map<String, Object> phaseItems = new LinkedHashMap<String, Object>();
    phaseItems.put("type", "string");
    phases.put("items", phaseItems);
    phases.put("description", "Phase type names");
    flashProps.put("phases", phases);
    flash.put("properties", flashProps);
    properties.put("flash", flash);

    // fluid - simplified since FluidResponse is complex
    Map<String, Object> fluid = new LinkedHashMap<String, Object>();
    fluid.put("type", "object");
    fluid.put("description", "Fluid properties, conditions, and compositions per phase. "
        + "Contains 'properties', 'conditions', and 'composition' sub-objects.");
    properties.put("fluid", fluid);

    // errors
    Map<String, Object> errors = new LinkedHashMap<String, Object>();
    errors.put("type", "array");
    Map<String, Object> errItems = new LinkedHashMap<String, Object>();
    errItems.put("type", "object");
    errors.put("items", errItems);
    errors.put("description", "Error details if status is 'error'");
    properties.put("errors", errors);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));

    return GSON.toJson(schema);
  }

  // ========== Process Schemas ==========

  /**
   * Returns the JSON Schema for process simulation input.
   *
   * @return JSON Schema string
   */
  public static String processInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "ProcessInput");
    schema.put("description",
        "Input for a process simulation (run_process tool). Accepts either a single "
            + "ProcessSystem with 'fluid' and 'process', or a multi-area ProcessModel with "
            + "top-level 'areas'.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    // fluid block
    Map<String, Object> fluid = new LinkedHashMap<String, Object>();
    fluid.put("type", "object");
    fluid.put("description",
        "Thermodynamic fluid definition with model, T, P, components, and mixing rule");
    properties.put("fluid", fluid);

    // process array
    Map<String, Object> process = new LinkedHashMap<String, Object>();
    process.put("type", "array");
    process.put("description",
        "Ordered list of equipment units. Each unit has 'type', 'name', 'inlet', "
            + "and 'properties'.");
    Map<String, Object> processItems = new LinkedHashMap<String, Object>();
    processItems.put("type", "object");
    Map<String, Object> unitProps = new LinkedHashMap<String, Object>();
    unitProps.put("type", stringProp("Equipment type (Stream, Separator, Compressor, etc.)"));
    unitProps.put("name", stringProp("Unique equipment name"));
    unitProps.put("inlet",
        stringProp("Inlet reference: equipment name or name.portName (e.g., 'HP Sep.gasOut')"));
    Map<String, Object> propsObj = new LinkedHashMap<String, Object>();
    propsObj.put("type", "object");
    propsObj.put("description", "Equipment-specific properties as key: [value, unit] pairs");
    unitProps.put("properties", propsObj);
    processItems.put("properties", unitProps);
    processItems.put("required", Arrays.asList("type", "name"));
    process.put("items", processItems);
    properties.put("process", process);

    Map<String, Object> areas = new LinkedHashMap<String, Object>();
    areas.put("type", "object");
    areas.put("description",
        "Named ProcessModel areas. Each property value is a standard process JSON object "
            + "with 'fluid' and 'process'.");
    properties.put("areas", areas);

    schema.put("properties", properties);
    List<Map<String, Object>> alternatives = new ArrayList<Map<String, Object>>();
    Map<String, Object> processSystemRequired = new LinkedHashMap<String, Object>();
    processSystemRequired.put("required", Arrays.asList("fluid", "process"));
    alternatives.add(processSystemRequired);
    Map<String, Object> processModelRequired = new LinkedHashMap<String, Object>();
    processModelRequired.put("required", Collections.singletonList("areas"));
    alternatives.add(processModelRequired);
    schema.put("anyOf", alternatives);

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for process simulation output.
   *
   * @return JSON Schema string
   */
  public static String processOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "ProcessOutput");
    schema.put("description", "Output of a process simulation (run_process tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("processSystemName", stringProp("Name of the built process system"));
    properties.put("processModelName", stringProp("Name of the built process model"));
    Map<String, Object> areaCount = new LinkedHashMap<String, Object>();
    areaCount.put("type", "integer");
    areaCount.put("description", "Number of areas in a process model response");
    properties.put("areaCount", areaCount);

    Map<String, Object> areas = new LinkedHashMap<String, Object>();
    areas.put("type", "array");
    areas.put("items", stringProp("Process area name"));
    areas.put("description", "ProcessModel area names when the input used top-level 'areas'");
    properties.put("areas", areas);

    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("type", "object");
    report.put("description", "Per-equipment simulation results with stream properties");
    properties.put("report", report);

    Map<String, Object> errors = new LinkedHashMap<String, Object>();
    errors.put("type", "array");
    Map<String, Object> errItems = new LinkedHashMap<String, Object>();
    errItems.put("type", "object");
    errors.put("items", errItems);
    properties.put("errors", errors);

    Map<String, Object> warnings = new LinkedHashMap<String, Object>();
    warnings.put("type", "array");
    Map<String, Object> warnItems = new LinkedHashMap<String, Object>();
    warnItems.put("type", "string");
    warnings.put("items", warnItems);
    properties.put("warnings", warnings);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));

    return GSON.toJson(schema);
  }

  // ========== Validate Schema ==========

  /**
   * Returns the JSON Schema for validation input (same as flash or process inputs).
   *
   * @return JSON Schema string
   */
  public static String validateInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "ValidateInput");
    schema.put("description",
        "Input for validation (validate_input tool). Accepts either a flash or process JSON.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("components", stringProp("Present if validating flash input"));
    properties.put("process", stringProp("Present if validating process input"));
    schema.put("properties", properties);

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for validation output.
   *
   * @return JSON Schema string
   */
  public static String validateOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "ValidateOutput");
    schema.put("description", "Output of validation (validate_input tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    Map<String, Object> valid = new LinkedHashMap<String, Object>();
    valid.put("type", "boolean");
    valid.put("description", "true if no errors found");
    properties.put("valid", valid);

    Map<String, Object> issues = new LinkedHashMap<String, Object>();
    issues.put("type", "array");
    Map<String, Object> issueItem = new LinkedHashMap<String, Object>();
    issueItem.put("type", "object");
    Map<String, Object> issuePropMap = new LinkedHashMap<String, Object>();
    issuePropMap.put("severity", enumProp("Issue severity", Arrays.asList("error", "warning")));
    issuePropMap.put("code", stringProp("Machine-readable issue code"));
    issuePropMap.put("message", stringProp("Human-readable description"));
    issuePropMap.put("remediation", stringProp("How to fix the issue"));
    issueItem.put("properties", issuePropMap);
    issues.put("items", issueItem);
    properties.put("issues", issues);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("valid", "issues"));

    return GSON.toJson(schema);
  }

  // ========== Component Query Schema ==========

  /**
   * Returns the JSON Schema for component search output.
   *
   * @return JSON Schema string
   */
  public static String componentSearchOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "ComponentSearchOutput");
    schema.put("description", "Output of component search (list_components tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", stringProp("Result status"));
    properties.put("query", stringProp("The search query used"));
    Map<String, Object> matchCount = new LinkedHashMap<String, Object>();
    matchCount.put("type", "integer");
    matchCount.put("description", "Number of matching components");
    properties.put("matchCount", matchCount);
    Map<String, Object> comps = new LinkedHashMap<String, Object>();
    comps.put("type", "array");
    Map<String, Object> compItems = new LinkedHashMap<String, Object>();
    compItems.put("type", "string");
    comps.put("items", compItems);
    comps.put("description", "Matching component names");
    properties.put("components", comps);

    schema.put("properties", properties);

    return GSON.toJson(schema);
  }

  // ========== Batch Schemas ==========

  /**
   * Returns the JSON Schema for batch calculation input.
   *
   * @return JSON Schema string
   */
  public static String batchInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "BatchInput");
    schema.put("description", "Input for batch flash calculations (run_batch tool). "
        + "Shares a base fluid; each case can override T, P, components, or flashType.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("model", enumProp("Thermodynamic model",
        Arrays.asList("SRK", "PR", "CPA", "GERG2008", "PCSAFT", "UMRPRU")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Base component-to-mole-fraction map");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    addProps.put("minimum", 0);
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    properties.put("mixingRule", stringProp("Mixing rule (default: classic)"));
    properties.put("flashType", enumProp("Default flash type for all cases", Arrays.asList("TP",
        "PH", "PS", "TV", "dewPointT", "dewPointP", "bubblePointT", "bubblePointP", "hydrateTP")));

    Map<String, Object> cases = new LinkedHashMap<String, Object>();
    cases.put("type", "array");
    cases.put("description",
        "Array of case objects (max 500). Each can have temperature, pressure, components, flashType.");
    Map<String, Object> caseItem = new LinkedHashMap<String, Object>();
    caseItem.put("type", "object");
    Map<String, Object> caseProps = new LinkedHashMap<String, Object>();
    caseProps.put("temperature", valueWithUnitSchema("Temperature override", "Temperature unit",
        Arrays.asList("K", "C", "F", "R"), "K"));
    caseProps.put("pressure", valueWithUnitSchema("Pressure override", "Pressure unit",
        Arrays.asList("bara", "barg", "Pa", "kPa", "MPa", "psi", "atm"), "bara"));
    caseItem.put("properties", caseProps);
    cases.put("items", caseItem);
    Map<String, Object> casesConstraint = new LinkedHashMap<String, Object>();
    casesConstraint.put("maxItems", 500);
    cases.putAll(casesConstraint);
    properties.put("cases", cases);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("components", "cases"));

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for batch calculation output.
   *
   * @return JSON Schema string
   */
  public static String batchOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "BatchOutput");
    schema.put("description", "Output of batch flash calculations (run_batch tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status",
        enumProp("Overall batch status", Arrays.asList("success", "partial", "error")));

    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("type", "object");
    summary.put("description", "Batch summary with counts and timing");
    Map<String, Object> summaryProps = new LinkedHashMap<String, Object>();
    summaryProps.put("totalCases", intProp("Total cases submitted"));
    summaryProps.put("succeeded", intProp("Cases that succeeded"));
    summaryProps.put("failed", intProp("Cases that failed"));
    summaryProps.put("elapsedMs", intProp("Total elapsed time in milliseconds"));
    summary.put("properties", summaryProps);
    properties.put("summary", summary);

    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("type", "array");
    results.put("description", "Per-case results in same order as input cases");
    Map<String, Object> resultItem = new LinkedHashMap<String, Object>();
    resultItem.put("type", "object");
    results.put("items", resultItem);
    properties.put("results", results);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("status", "summary", "results"));

    return GSON.toJson(schema);
  }

  // ========== Property Table Schemas ==========

  /**
   * Returns the JSON Schema for property table input.
   *
   * @return JSON Schema string
   */
  public static String propertyTableInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "PropertyTableInput");
    schema.put("description", "Input for property table sweep (get_property_table tool). "
        + "Sweeps temperature or pressure and returns properties at each point.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("model", enumProp("Thermodynamic model",
        Arrays.asList("SRK", "PR", "CPA", "GERG2008", "PCSAFT", "UMRPRU")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component-to-mole-fraction map");
    Map<String, Object> addPropsT = new LinkedHashMap<String, Object>();
    addPropsT.put("type", "number");
    addPropsT.put("minimum", 0);
    components.put("additionalProperties", addPropsT);
    properties.put("components", components);

    properties.put("mixingRule", stringProp("Mixing rule (default: classic)"));
    properties.put("sweep",
        enumProp("Variable to sweep", Arrays.asList("temperature", "pressure")));
    properties.put("sweepFrom", valueWithUnitSchema("Start of sweep range", "Unit",
        Arrays.asList("K", "C", "F", "bara", "barg", "Pa", "kPa", "MPa", "psi"), "C"));
    properties.put("sweepTo", valueWithUnitSchema("End of sweep range", "Unit",
        Arrays.asList("K", "C", "F", "bara", "barg", "Pa", "kPa", "MPa", "psi"), "C"));

    Map<String, Object> pts = new LinkedHashMap<String, Object>();
    pts.put("type", "integer");
    pts.put("description", "Number of data points (2-200, default 20)");
    pts.put("minimum", 2);
    pts.put("maximum", 200);
    pts.put("default", 20);
    properties.put("points", pts);

    properties.put("fixedPressure",
        valueWithUnitSchema("Fixed pressure (when sweeping temperature)", "Pressure unit",
            Arrays.asList("bara", "barg", "Pa", "kPa", "MPa", "psi"), "bara"));
    properties.put("fixedTemperature",
        valueWithUnitSchema("Fixed temperature (when sweeping pressure)", "Temperature unit",
            Arrays.asList("K", "C", "F"), "C"));

    Map<String, Object> propsArray = new LinkedHashMap<String, Object>();
    propsArray.put("type", "array");
    Map<String, Object> propsItems = new LinkedHashMap<String, Object>();
    propsItems.put("type", "string");
    propsItems.put("enum",
        Arrays.asList("density", "viscosity", "Cp", "Cv", "Z", "enthalpy", "entropy",
            "thermalConductivity", "molarMass", "soundSpeed", "jouleThomsonCoefficient",
            "numberOfPhases"));
    propsArray.put("items", propsItems);
    propsArray.put("description", "Properties to include in table (default: all available)");
    properties.put("properties", propsArray);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("components", "sweep"));

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for property table output.
   *
   * @return JSON Schema string
   */
  public static String propertyTableOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "PropertyTableOutput");
    schema.put("description", "Output of property table sweep (get_property_table tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("model", stringProp("Thermodynamic model used"));
    properties.put("sweep", stringProp("Variable swept (temperature or pressure)"));

    Map<String, Object> table = new LinkedHashMap<String, Object>();
    table.put("type", "array");
    table.put("description",
        "Array of data rows, one per sweep point, each containing requested properties");
    Map<String, Object> rowItem = new LinkedHashMap<String, Object>();
    rowItem.put("type", "object");
    table.put("items", rowItem);
    properties.put("table", table);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("status", "table"));

    return GSON.toJson(schema);
  }

  // ========== Phase Envelope Schemas ==========

  /**
   * Returns the JSON Schema for phase envelope input.
   *
   * @return JSON Schema string
   */
  public static String phaseEnvelopeInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "PhaseEnvelopeInput");
    schema.put("description", "Input for phase envelope calculation (get_phase_envelope tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("model", enumProp("Thermodynamic model",
        Arrays.asList("SRK", "PR", "CPA", "GERG2008", "PCSAFT", "UMRPRU")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component-to-mole-fraction map");
    Map<String, Object> addPropsE = new LinkedHashMap<String, Object>();
    addPropsE.put("type", "number");
    addPropsE.put("minimum", 0);
    components.put("additionalProperties", addPropsE);
    properties.put("components", components);

    properties.put("mixingRule", stringProp("Mixing rule (default: classic)"));

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("components"));

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for phase envelope output.
   *
   * @return JSON Schema string
   */
  public static String phaseEnvelopeOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "PhaseEnvelopeOutput");
    schema.put("description", "Output of phase envelope calculation (get_phase_envelope tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));

    Map<String, Object> envelope = new LinkedHashMap<String, Object>();
    envelope.put("type", "array");
    envelope.put("description",
        "Array of {temperature_K, pressure_bara, type} points along the phase boundary");
    Map<String, Object> envItem = new LinkedHashMap<String, Object>();
    envItem.put("type", "object");
    Map<String, Object> envProps = new LinkedHashMap<String, Object>();
    envProps.put("temperature_K", new LinkedHashMap<String, Object>() {
      {
        put("type", "number");
      }
    });
    envProps.put("pressure_bara", new LinkedHashMap<String, Object>() {
      {
        put("type", "number");
      }
    });
    envProps.put("type", stringProp("Point type: bubblePoint or dewPoint"));
    envItem.put("properties", envProps);
    envelope.put("items", envItem);
    properties.put("envelope", envelope);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("status", "envelope"));

    return GSON.toJson(schema);
  }

  // ========== Capabilities Schema ==========

  /**
   * Returns the JSON Schema for capabilities output.
   *
   * @return JSON Schema string
   */
  public static String capabilitiesOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "CapabilitiesOutput");
    schema.put("description", "Output of capabilities discovery (get_capabilities tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", stringProp("Result status"));
    properties.put("engine", stringProp("Engine name (NeqSim)"));
    properties.put("description", stringProp("Engine description"));

    Map<String, Object> thermo = new LinkedHashMap<String, Object>();
    thermo.put("type", "object");
    thermo.put("description", "Thermodynamic models and flash types supported");
    properties.put("thermodynamicModels", thermo);

    Map<String, Object> equipment = new LinkedHashMap<String, Object>();
    equipment.put("type", "object");
    equipment.put("description", "Process equipment types supported");
    properties.put("processEquipment", equipment);

    Map<String, Object> domains = new LinkedHashMap<String, Object>();
    domains.put("type", "array");
    domains.put("description", "Engineering domains covered");
    Map<String, Object> domainItem = new LinkedHashMap<String, Object>();
    domainItem.put("type", "string");
    domains.put("items", domainItem);
    properties.put("engineeringDomains", domains);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));

    return GSON.toJson(schema);
  }

  // ========== PVT Schemas ==========

  /**
   * Returns the JSON Schema for PVT experiment input.
   *
   * @return JSON Schema string
   */
  public static String pvtInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "PVTInput");
    schema.put("description", "Input for PVT experiment simulation (run_pvt tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("model",
        enumProp("Thermodynamic model", Arrays.asList("SRK", "PR", "CPA", "PCSAFT")));
    properties.put("experiment",
        enumProp("PVT experiment",
            Arrays.asList("CME", "CVD", "differentialLiberation", "saturationPressure",
                "saturationTemperature", "separatorTest", "swellingTest", "GOR", "viscosity")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component-to-mole-fraction map");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    properties.put("temperature_C", numberProp("Temperature in Celsius"));
    properties.put("pressure_bara", numberProp("Pressure in bara"));

    Map<String, Object> config = new LinkedHashMap<String, Object>();
    config.put("type", "object");
    config.put("description", "Experiment-specific configuration (pressures array, etc.)");
    properties.put("experimentConfig", config);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("components", "experiment"));

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for PVT experiment output.
   *
   * @return JSON Schema string
   */
  public static String pvtOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "PVTOutput");
    schema.put("description", "Output of PVT experiment (run_pvt tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("experiment", stringProp("Experiment type performed"));

    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("type", "object");
    data.put("description", "Experiment results (saturation pressure, relative volume, etc.)");
    properties.put("data", data);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));

    return GSON.toJson(schema);
  }

  // ========== Flow Assurance Schemas ==========

  /**
   * Returns the JSON Schema for flow assurance analysis input.
   *
   * @return JSON Schema string
   */
  public static String flowAssuranceInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "FlowAssuranceInput");
    schema.put("description", "Input for flow assurance analysis (run_flow_assurance tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("model",
        enumProp("Thermodynamic model", Arrays.asList("SRK", "PR", "CPA", "PCSAFT")));
    properties.put("analysis",
        enumProp("Analysis type",
            Arrays.asList("hydrateRiskMap", "waxAppearance", "asphalteneStability", "CO2Corrosion",
                "scalePrediction", "erosion", "pipelineCooldown", "emulsionViscosity")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component-to-mole-fraction map");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    properties.put("temperature_C", numberProp("Temperature in Celsius"));
    properties.put("pressure_bara", numberProp("Pressure in bara"));

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("components", "analysis"));

    return GSON.toJson(schema);
  }

  // ========== Standards Schemas ==========

  /**
   * Returns the JSON Schema for standards calculation input.
   *
   * @return JSON Schema string
   */
  public static String standardsInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "StandardsInput");
    schema.put("description", "Input for standards calculation (calculate_standard tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("standard",
        enumProp("Standard to calculate",
            Arrays.asList("ISO6976", "ISO6976_2016", "ISO12213", "ISO13443", "ISO18453", "ISO14687",
                "ISO15112", "ISO6578", "AGA3", "AGA7", "GPA2145", "GPA2172", "EN16723", "EN16726",
                "ASTM_D86", "ASTM_D445", "ASTM_D2500", "ASTM_D4052", "ASTM_D4294", "ASTM_D6377",
                "ASTM_D97", "BSW")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component-to-mole-fraction map");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    properties.put("temperature_C", numberProp("Metering temperature (default: 15 C)"));
    properties.put("pressure_bara", numberProp("Metering pressure (default: 1.01325 bara)"));

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("components", "standard"));

    return GSON.toJson(schema);
  }

  // ========== Pipeline Schemas ==========

  /**
   * Returns the JSON Schema for pipeline simulation input.
   *
   * @return JSON Schema string
   */
  public static String pipelineInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "PipelineInput");
    schema.put("description", "Input for pipeline simulation (run_pipeline tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("model", enumProp("Thermodynamic model", Arrays.asList("SRK", "PR", "CPA")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component-to-mole-fraction map");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    properties.put("temperature_C", numberProp("Inlet temperature in Celsius"));
    properties.put("pressure_bara", numberProp("Inlet pressure in bara"));

    Map<String, Object> flowRate = new LinkedHashMap<String, Object>();
    flowRate.put("type", "object");
    flowRate.put("description", "Flow rate with unit");
    Map<String, Object> frProps = new LinkedHashMap<String, Object>();
    frProps.put("value", numberProp("Numeric value"));
    frProps.put("unit", stringProp("Unit (kg/hr, MSm3/day, etc.)"));
    flowRate.put("properties", frProps);
    properties.put("flowRate", flowRate);

    Map<String, Object> pipe = new LinkedHashMap<String, Object>();
    pipe.put("type", "object");
    pipe.put("description", "Pipe geometry and configuration");
    Map<String, Object> pipeProps = new LinkedHashMap<String, Object>();
    pipeProps.put("diameter_m", numberProp("Inner diameter in metres"));
    pipeProps.put("length_m", numberProp("Length in metres"));
    pipeProps.put("elevation_m", numberProp("Elevation change in metres"));
    pipeProps.put("roughness_m", numberProp("Surface roughness in metres"));
    pipeProps.put("numberOfIncrements", intProp("Number of calculation segments"));
    pipe.put("properties", pipeProps);
    properties.put("pipe", pipe);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("components", "pipe"));

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for water-hammer screening input.
   *
   * @return JSON Schema string
   */
  public static String waterHammerInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "WaterHammerInput");
    schema.put("description", "Input for water/liquid hammer screening (run_water_hammer tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("studyName", stringProp("Study name or scenario identifier"));
    properties.put("model", enumProp("Thermodynamic model", Arrays.asList("SRK", "PR")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component-to-mole-fraction map, defaults to water if omitted");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    properties.put("temperature_C", numberProp("Inlet temperature in Celsius"));
    properties.put("pressure_bara", numberProp("Inlet pressure in bara"));
    properties.put("designPressure_bara", numberProp("Pipe design pressure or MAOP in bara"));
    properties.put("simulationTime_s", numberProp("Simulation duration in seconds"));
    properties.put("timeStep_s", numberProp("Requested transient time step in seconds"));
    properties.put("closureTime_s", numberProp("Default valve closure duration in seconds"));

    Map<String, Object> flowRate = new LinkedHashMap<String, Object>();
    flowRate.put("type", "object");
    Map<String, Object> frProps = new LinkedHashMap<String, Object>();
    frProps.put("value", numberProp("Numeric flow-rate value"));
    frProps.put("unit", stringProp("Flow-rate unit, for example kg/hr"));
    flowRate.put("properties", frProps);
    properties.put("flowRate", flowRate);

    Map<String, Object> pipe = new LinkedHashMap<String, Object>();
    pipe.put("type", "object");
    pipe.put("description", "Equivalent pipe geometry for single-line screening");
    Map<String, Object> pipeProps = new LinkedHashMap<String, Object>();
    pipeProps.put("length_m", numberProp("Pipe length in metres"));
    pipeProps.put("diameter_m", numberProp("Pipe internal diameter in metres"));
    pipeProps.put("wallThickness_m", numberProp("Pipe wall thickness in metres"));
    pipeProps.put("roughness_m", numberProp("Pipe roughness in metres"));
    pipeProps.put("elevation_m", numberProp("Elevation change from inlet to outlet in metres"));
    pipeProps.put("numberOfNodes", intProp("Transient computational node count"));
    pipeProps.put("waveSpeed_m_s", numberProp("Optional acoustic wave-speed override"));
    pipe.put("properties", pipeProps);
    properties.put("pipe", pipe);

    Map<String, Object> route = new LinkedHashMap<String, Object>();
    route.put("type", "object");
    route.put("description",
        "Optional STID/E3D route with segments containing length, diameter, elevation, "
            + "wall thickness, roughness, and minorLosses");
    properties.put("stidRoute", route);

    Map<String, Object> fieldData = new LinkedHashMap<String, Object>();
    fieldData.put("type", "object");
    fieldData.put("description",
        "Optional tagreader overrides such as inletPressure_bara, inletTemperature_C, "
            + "flowRate_kg_hr, and valveOpening");
    properties.put("fieldData", fieldData);

    Map<String, Object> events = new LinkedHashMap<String, Object>();
    events.put("type", "array");
    events.put("description",
        "Valve event schedule with type, startTime_s, duration_s, startOpening, endOpening");
    events.put("items", objectProp("Valve closure or opening event"));
    properties.put("eventSchedule", events);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("flowRate", "pipe"));
    return GSON.toJson(schema);
  }

  // ========== Root-Cause Analysis Schemas ==========

  /**
   * Returns the JSON Schema for root-cause analysis input.
   *
   * @return JSON Schema string
   */
  public static String rootCauseInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "RootCauseAnalysisInput");
    schema.put("description",
        "Input for equipment root-cause analysis (run_root_cause_analysis tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("equipmentName", stringProp("Name of the equipment to diagnose"));
    properties.put("symptom",
        enumProp("Observed symptom",
            Arrays.asList("TRIP", "HIGH_VIBRATION", "SEAL_FAILURE", "HIGH_TEMPERATURE",
                "LOW_EFFICIENCY", "PRESSURE_DEVIATION", "FLOW_DEVIATION", "HIGH_POWER",
                "SURGE_EVENT", "FOULING", "ABNORMAL_NOISE", "LIQUID_CARRYOVER")));
    properties.put("processJson",
        stringProp("ProcessSystem JSON definition accepted by ProcessSystem.fromJsonAndRun"));
    properties.put("simulationEnabled",
        boolProp("Whether to run conservative NeqSim perturbation verification"));
    properties.put("historianCsv", stringProp(
        "CSV text with header row time,<tag1>,<tag2>... from tagreader or historian export"));

    Map<String, Object> designLimits = new LinkedHashMap<String, Object>();
    designLimits.put("type", "object");
    designLimits.put("description",
        "Map of tag/parameter to [low, high] limits, for example {\"vibration\": [0, 8]}.");
    properties.put("designLimits", designLimits);

    Map<String, Object> stidData = new LinkedHashMap<String, Object>();
    stidData.put("type", "object");
    stidData.put("description",
        "STID/design data map such as design point, rated speed, normal operating value, "
            + "or datasheet references");
    properties.put("stidData", stidData);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("equipmentName", "symptom", "processJson"));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for root-cause analysis output.
   *
   * @return JSON Schema string
   */
  public static String rootCauseOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "RootCauseAnalysisOutput");
    schema.put("description", "Output from root-cause analysis with ranked hypotheses");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("equipment", stringProp("Diagnosed equipment name"));
    properties.put("equipmentType", stringProp("Classified equipment type"));
    properties.put("symptom", stringProp("Analyzed symptom"));
    properties.put("dataPointsAnalyzed", intProp("Number of historian data points analyzed"));
    properties.put("parametersAnalyzed", intProp("Number of historian parameters analyzed"));
    properties.put("hypotheses",
        typedArraySchema(
            "Ranked hypotheses with confidenceScore, priorProbability, likelihoodScore, "
                + "verificationScore, evidence, recommendedActions, and simulationSummary"));
    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));
    return GSON.toJson(schema);
  }

  // ========== Reservoir Schemas ==========

  /**
   * Returns the JSON Schema for reservoir simulation input.
   *
   * @return JSON Schema string
   */
  public static String reservoirInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "ReservoirInput");
    schema.put("description", "Input for reservoir simulation (run_reservoir tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("model", enumProp("Thermodynamic model", Arrays.asList("SRK", "PR", "CPA")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component-to-mole-fraction map");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    properties.put("reservoirTemperature_C", numberProp("Reservoir temperature (C)"));
    properties.put("reservoirPressure_bara", numberProp("Initial reservoir pressure (bara)"));
    properties.put("gasVolume_Sm3", numberProp("Gas in place (Sm3)"));
    properties.put("oilVolume_Sm3", numberProp("Oil in place (Sm3, 0 for gas only)"));
    properties.put("waterVolume_Sm3", numberProp("Water volume (Sm3)"));
    properties.put("simulationYears", intProp("Years to simulate"));
    properties.put("timeStepDays", intProp("Time step in days"));

    Map<String, Object> producers = new LinkedHashMap<String, Object>();
    producers.put("type", "array");
    producers.put("description", "List of producer wells");
    Map<String, Object> prodItem = new LinkedHashMap<String, Object>();
    prodItem.put("type", "object");
    Map<String, Object> prodProps = new LinkedHashMap<String, Object>();
    prodProps.put("name", stringProp("Well name"));
    Map<String, Object> fr = new LinkedHashMap<String, Object>();
    fr.put("type", "object");
    prodProps.put("flowRate", fr);
    prodItem.put("properties", prodProps);
    producers.put("items", prodItem);
    properties.put("producers", producers);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("components", "producers"));

    return GSON.toJson(schema);
  }

  // ========== Field Economics Schemas ==========

  /**
   * Returns the JSON Schema for field economics input.
   *
   * @return JSON Schema string
   */
  public static String fieldEconomicsInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "FieldEconomicsInput");
    schema.put("description", "Input for field economics calculation (run_field_economics tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("mode",
        enumProp("Calculation mode", Arrays.asList("cashflow", "productionProfile")));
    properties.put("country",
        enumProp("Fiscal regime", Arrays.asList("NO", "UK", "BR", "US-GOM", "GENERIC")));
    properties.put("discountRate", numberProp("Discount rate (e.g. 0.08 = 8%)"));
    properties.put("oilPrice_usdPerBbl", numberProp("Oil price USD/bbl"));
    properties.put("gasPrice_usdPerSm3", numberProp("Gas price USD/Sm3"));

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("mode"));

    return GSON.toJson(schema);
  }

  // ========== Dynamic Simulation Schemas ==========

  /**
   * Returns the JSON Schema for dynamic simulation input.
   *
   * @return JSON Schema string
   */
  public static String dynamicInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "DynamicInput");
    schema.put("description", "Input for dynamic simulation (run_dynamic tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    Map<String, Object> processJson = new LinkedHashMap<String, Object>();
    processJson.put("type", "object");
    processJson.put("description",
        "Standard process JSON (same as run_process) used as steady-state starting point");
    properties.put("processJson", processJson);

    properties.put("duration_seconds", numberProp("Total dynamic simulation duration (seconds)"));
    properties.put("timeStep_seconds", numberProp("Time step (seconds)"));

    Map<String, Object> tuning = new LinkedHashMap<String, Object>();
    tuning.put("type", "object");
    tuning.put("description",
        "Optional PID tuning: pressureKp, pressureTi, levelKp, levelTi, flowKp, flowTi, etc.");
    properties.put("tuning", tuning);

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("processJson", "duration_seconds", "timeStep_seconds"));

    return GSON.toJson(schema);
  }

  // ========== Bioprocess Schemas ==========

  /**
   * Returns the JSON Schema for bioprocess simulation input.
   *
   * @return JSON Schema string
   */
  public static String bioprocessInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "BioprocessInput");
    schema.put("description", "Input for bioprocess simulation (run_bioprocess tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("reactorType", enumProp("Reactor type",
        Arrays.asList("anaerobicDigester", "fermenter", "gasifier", "pyrolysis")));
    properties.put("feedRate_kgPerHr", numberProp("Feed rate in kg/hr"));
    properties.put("temperature_C", numberProp("Operating temperature (C)"));
    properties.put("volume_m3", numberProp("Reactor volume (m3)"));

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("reactorType"));

    return GSON.toJson(schema);
  }

  // ========== Equipment Sizing Schemas ==========

  /**
   * Returns the JSON Schema for equipment sizing input.
   *
   * @return JSON Schema string
   */
  public static String equipmentSizingInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "EquipmentSizingInput");
    schema.put("description", "Input for equipment sizing (size_equipment tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("equipmentType", enumProp("Equipment type to size",
        Arrays.asList("separator", "compressor", "heatExchanger", "pipe")));
    properties.put("model",
        enumProp("Thermodynamic model", Arrays.asList("SRK", "PR", "CPA", "GERG2008")));
    properties.put("temperature_C", numberProp("Operating temperature (C)"));
    properties.put("pressure_bara", numberProp("Operating pressure (bara)"));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Component name to mole fraction map");
    Map<String, Object> addProps = new LinkedHashMap<String, Object>();
    addProps.put("type", "number");
    components.put("additionalProperties", addProps);
    properties.put("components", components);

    Map<String, Object> flowRate = new LinkedHashMap<String, Object>();
    flowRate.put("type", "object");
    flowRate.put("description", "Flow rate with value and unit");
    properties.put("flowRate", flowRate);

    properties.put("orientation",
        enumProp("Separator orientation", Arrays.asList("horizontal", "vertical")));
    properties.put("liquidRetentionTime_min", numberProp("Liquid retention time (minutes)"));
    properties.put("outletPressure_bara", numberProp("Compressor outlet pressure (bara)"));
    properties.put("polytropicEfficiency", numberProp("Compressor polytropic efficiency (0-1)"));

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("equipmentType", "components"));

    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for equipment sizing output.
   *
   * @return JSON Schema string
   */
  public static String equipmentSizingOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "EquipmentSizingOutput");
    schema.put("description", "Output of equipment sizing");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("equipmentType", stringProp("Equipment type sized"));

    Map<String, Object> sizing = new LinkedHashMap<String, Object>();
    sizing.put("type", "object");
    sizing.put("description", "Sizing results with dimensions and capacities");
    properties.put("sizing", sizing);

    Map<String, Object> designBasis = new LinkedHashMap<String, Object>();
    designBasis.put("type", "object");
    designBasis.put("description", "Design basis used for sizing");
    properties.put("designBasis", designBasis);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));

    return GSON.toJson(schema);
  }

  // ========== Process Comparison Schemas ==========

  /**
   * Returns the JSON Schema for process comparison input.
   *
   * @return JSON Schema string
   */
  public static String comparisonInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "ProcessComparisonInput");
    schema.put("description",
        "Input for process comparison (compare_processes tool). Each case is a "
            + "full process definition that will be run and compared.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    Map<String, Object> cases = new LinkedHashMap<String, Object>();
    cases.put("type", "array");
    cases.put("description",
        "Array of process cases to compare. Each case has a name, fluid definition, "
            + "and process equipment array.");
    cases.put("minItems", 2);
    Map<String, Object> caseItem = new LinkedHashMap<String, Object>();
    caseItem.put("type", "object");
    Map<String, Object> caseProps = new LinkedHashMap<String, Object>();
    caseProps.put("name", stringProp("Case name for labelling"));
    Map<String, Object> fluid = new LinkedHashMap<String, Object>();
    fluid.put("type", "object");
    fluid.put("description", "Fluid definition (same as run_process fluid block)");
    caseProps.put("fluid", fluid);
    Map<String, Object> process = new LinkedHashMap<String, Object>();
    process.put("type", "array");
    process.put("description", "Equipment array (same as run_process process block)");
    caseProps.put("process", process);
    caseItem.put("properties", caseProps);
    cases.put("items", caseItem);
    properties.put("cases", cases);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("cases"));

    return GSON.toJson(schema);
  }

  // ========== Session Schemas ==========

  /**
   * Returns the JSON Schema for session management input.
   *
   * @return JSON Schema string
   */
  public static String sessionInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "SessionInput");
    schema.put("description", "Input for simulation session management (manage_session tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("action", enumProp("Session action",
        Arrays.asList("create", "addEquipment", "run", "modify", "getState", "list", "close")));
    properties.put("sessionId", stringProp("Session ID (required for all actions except create)"));

    Map<String, Object> fluid = new LinkedHashMap<String, Object>();
    fluid.put("type", "object");
    fluid.put("description", "Fluid definition (required for create action)");
    properties.put("fluid", fluid);

    Map<String, Object> equipment = new LinkedHashMap<String, Object>();
    equipment.put("type", "object");
    equipment.put("description", "Equipment definition (required for addEquipment action)");
    properties.put("equipment", equipment);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("action"));

    return GSON.toJson(schema);
  }

  // ========== Visualization Schemas ==========

  /**
   * Returns the JSON Schema for visualization input.
   *
   * @return JSON Schema string
   */
  public static String visualizationInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "VisualizationInput");
    schema.put("description", "Input for visualization generation (visualize tool)");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("type", enumProp("Visualization type", Arrays.asList("phaseEnvelope",
        "flowsheet", "compressorMap", "propertyTable", "barChart", "pieChart", "lineChart")));
    properties.put("title", stringProp("Chart title"));
    properties.put("model",
        enumProp("Thermodynamic model (for phaseEnvelope)", Arrays.asList("SRK", "PR", "CPA")));

    Map<String, Object> components = new LinkedHashMap<String, Object>();
    components.put("type", "object");
    components.put("description", "Components (for phaseEnvelope type)");
    properties.put("components", components);

    properties.put("xLabel", stringProp("X-axis label"));
    properties.put("yLabel", stringProp("Y-axis label"));

    Map<String, Object> categories = new LinkedHashMap<String, Object>();
    categories.put("type", "array");
    Map<String, Object> catItems = new LinkedHashMap<String, Object>();
    catItems.put("type", "string");
    categories.put("items", catItems);
    categories.put("description", "Category labels (for barChart/pieChart)");
    properties.put("categories", categories);

    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("type", "array");
    Map<String, Object> valItems = new LinkedHashMap<String, Object>();
    valItems.put("type", "number");
    values.put("items", valItems);
    values.put("description", "Data values (for barChart/pieChart)");
    properties.put("values", values);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("type"));

    return GSON.toJson(schema);
  }

  // ========== HAZOP Study Schemas ==========

  /**
   * Returns the JSON Schema for simulation-backed HAZOP study input.
   *
   * @return JSON Schema string
   */
  public static String hazopInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "HAZOPStudyInput");
    schema.put("description",
        "Input for run_hazop: process JSON plus optional STID/P&ID-extracted nodes, "
            + "failure modes, and barrier register");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("studyId", stringProp("Stable HAZOP study identifier"));
    properties.put("processDefinition", objectProp("Standard run_process JSON definition"));
    properties.put("processJson", stringProp("Alternative string form of the process JSON"));
    properties.put("nodes", typedArraySchema(
        "HAZOP nodes with nodeId, designIntent, equipment, safeguards, and evidenceRefs"));
    properties.put("failureModes", typedArraySchema(
        "AutomaticScenarioGenerator failure modes such as COOLING_LOSS or VALVE_STUCK_CLOSED"));
    properties.put("enableAllFailureModes", boolProp("Enable every supported failure mode"));
    properties.put("runSimulations", boolProp("Run generated scenarios against copied processes"));
    properties.put("barrierRegister",
        objectProp("Optional register block accepted by run_barrier_register"));
    schema.put("properties", properties);
    schema.put("anyOf", Arrays.asList(requiredSchema("processDefinition"),
        requiredSchema("processJson"), requiredSchema("fluid", "process")));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for simulation-backed HAZOP study output.
   *
   * @return JSON Schema string
   */
  public static String hazopOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "HAZOPStudyOutput");
    schema.put("description",
        "Output from run_hazop with worksheet rows, scenario results, and report markdown");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("studyId", stringProp("HAZOP study identifier"));
    properties.put("summary", objectProp("Node, row, failure-mode, and simulation counts"));
    properties.put("process", objectProp("Baseline process simulation summary"));
    properties.put("nodes", typedArraySchema("Node-level text reports"));
    properties.put("hazopRows", typedArraySchema("Generated IEC 61882 worksheet rows"));
    properties.put("scenarioResults", typedArraySchema("Per-scenario simulation result values"));
    properties.put("qualityGates", objectProp("Human-review and evidence quality gates"));
    properties.put("barrierRegisterHandoff", objectProp("Optional barrier-register analysis"));
    properties.put("reportMarkdown", stringProp("Markdown report suitable for task reports"));
    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));
    return GSON.toJson(schema);
  }

  // ========== Safety Barrier Register Schemas ==========

  /**
   * Returns the JSON Schema for barrier-register input.
   *
   * @return JSON Schema string
   */
  public static String barrierRegisterInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "BarrierRegisterInput");
    schema.put("description",
        "Evidence-linked safety barrier register input for run_barrier_register");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("action", enumProp("Operation to perform",
        Arrays.asList("validate", "summary", "audit", "lopaInput", "bowTieInput")));

    Map<String, Object> register = new LinkedHashMap<String, Object>();
    register.put("type", "object");
    register.put("description",
        "Barrier register with evidence, performanceStandards, barriers, and SCEs");
    Map<String, Object> registerProps = new LinkedHashMap<String, Object>();
    registerProps.put("registerId", stringProp("Stable barrier register identifier"));
    registerProps.put("name", stringProp("Register display name"));

    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("type", "array");
    evidence.put("description", "Document evidence extracted from technical documents");
    Map<String, Object> evidenceItem = new LinkedHashMap<String, Object>();
    evidenceItem.put("type", "object");
    Map<String, Object> evidenceProps = new LinkedHashMap<String, Object>();
    evidenceProps.put("evidenceId", stringProp("Evidence identifier"));
    evidenceProps.put("documentId", stringProp("Document number or tag"));
    evidenceProps.put("sourceReference", stringProp("Page, table, drawing coordinate, or tag"));
    evidenceProps.put("excerpt", stringProp("Quoted source text supporting the claim"));
    evidenceProps.put("confidence", numberProp("Extraction confidence from 0 to 1"));
    evidenceItem.put("properties", evidenceProps);
    evidence.put("items", evidenceItem);
    registerProps.put("evidence", evidence);

    registerProps.put("performanceStandards",
        typedArraySchema("Performance standards for PSFs, SIFs, or SCE functions"));
    registerProps.put("barriers", typedArraySchema("Barrier or protection-layer records"));
    registerProps.put("safetyCriticalElements", typedArraySchema("SCE records with barrierRefs"));
    register.put("properties", registerProps);
    register.put("required", Arrays.asList("registerId", "barriers"));
    properties.put("register", register);

    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("register"));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for barrier-register output.
   *
   * @return JSON Schema string
   */
  public static String barrierRegisterOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "BarrierRegisterOutput");
    schema.put("description",
        "Output from run_barrier_register with validation and safety-analysis handoffs");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("summary", objectProp("Counts of SCEs, barriers, evidence, and impairments"));
    properties.put("validation", objectProp("Validation findings and remediation hints"));
    properties.put("impairedBarriers",
        typedArraySchema("Impaired, bypassed, or unavailable barriers"));
    properties.put("equipmentBarrierMap", objectProp("Equipment tag to barrier mapping"));
    properties.put("lopaHandoff", objectProp("LOPA-compatible protection layer handoff"));
    properties.put("silHandoff", objectProp("SIL verification handoff candidates"));
    properties.put("bowTieHandoff", objectProp("Bow-tie barrier handoff"));
    properties.put("qraHandoff", objectProp("QRA screening multipliers by hazard"));
    properties.put("registerExport", objectProp("Full normalized BarrierRegister export"));
    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for safety-system performance input.
   *
   * @return JSON Schema string
   */
  public static String safetySystemPerformanceInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "SafetySystemPerformanceInput");
    schema.put("description",
        "Input for run_safety_system_performance: barrier register plus STID-derived demands, detectors, and optional SIF data");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("register", objectProp("Barrier register accepted by run_barrier_register"));
    properties.put("barrierRegister",
        objectProp("Alias for register for agent handoff compatibility"));
    properties.put("demands", typedArraySchema(
        "Demand/capacity cases with demandId, barrierId, category, demandValue, capacityValue, units, response time, availability, and evidenceRefs"));
    properties.put("demandCases", typedArraySchema("Alias for demands"));
    properties.put("measurementDevices", typedArraySchema(
        "FireDetector or GasDetector definitions with name/tag/type/location/responseTimeSeconds"));
    properties.put("logicSifs", typedArraySchema(
        "Cause-and-effect voting SIFs with name, votingLogic, and detector states"));
    properties.put("quantitativeSifs", typedArraySchema(
        "SIL/PFD SIFs with claimedSIL, pfdAvg or components, architecture, proof-test interval, and protectedEquipment"));
    properties.put("safetyInstrumentedFunctions",
        typedArraySchema("Mixed logic or quantitative SIF definitions"));
    schema.put("properties", properties);
    schema.put("anyOf", Arrays.asList(requiredSchema("register"), requiredSchema("barrierRegister"),
        requiredSchema("registerId", "barriers")));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for safety-system performance output.
   *
   * @return JSON Schema string
   */
  public static String safetySystemPerformanceOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "SafetySystemPerformanceOutput");
    schema.put("description",
        "Output from run_safety_system_performance with report, standards templates, and STID extraction templates");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("summary", objectProp("Overall verdict and parsed input counts"));
    properties.put("performanceReport",
        objectProp("SafetySystemPerformanceReport with assessments and findings"));
    properties.put("standardsTemplates",
        objectProp("NORSOK S-001, ISO 13702, and TR1055-style screening templates"));
    properties.put("stidExtractionTemplates", objectProp(
        "Extraction templates for C&E, SRS, firewater datasheets, detector layouts, and PFP schedules"));
    properties.put("registerExport", objectProp("Normalized BarrierRegister export"));
    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for materials review input.
   *
   * @return JSON Schema string
   */
  public static String materialsReviewInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "MaterialsReviewInput");
    schema.put("description",
        "Input for run_materials_review. Accepts processJson plus normalized STID/materials-register items.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("projectName", stringProp("Project, asset, or review name"));
    properties.put("designLifeYears", numberProp("Default design life in years"));
    properties.put("processJson", objectProp("Optional runProcess-style process definition"));
    properties.put("materialsRegister", typedArraySchema(
        "Review items with tag, equipmentType, existingMaterial, sourceReferences, and service envelope"));
    properties.put("items", typedArraySchema("Alias for materialsRegister"));
    properties.put("stidData", objectProp(
        "Normalized STID/technical database extract with lineList, equipment, materialsRegister, inspectionData, or materialCertificates arrays"));
    properties.put("includeMechanisms", typedArraySchema(
        "Optional mechanism names to document intended scope; engine auto-detects mechanisms from data"));
    schema.put("properties", properties);
    schema.put("anyOf", Arrays.asList(requiredSchema("processJson"),
        requiredSchema("materialsRegister"), requiredSchema("items"), requiredSchema("stidData")));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for materials review output.
   *
   * @return JSON Schema string
   */
  public static String materialsReviewOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "MaterialsReviewOutput");
    schema.put("description",
        "Output from run_materials_review with item verdicts, mechanisms, material recommendations, and integrity life screening.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("reviewType", stringProp("materials_integrity_review"));
    properties.put("overallVerdict",
        enumProp("Overall verdict", Arrays.asList("PASS", "PASS_WITH_WARNINGS", "FAIL")));
    properties.put("itemCount", intProp("Number of reviewed items"));
    properties.put("failedItems", intProp("Number of failed items"));
    properties.put("warningItems", intProp("Number of items with warnings"));
    properties.put("items", typedArraySchema(
        "Item results with damageMechanisms, recommendation, integrityLife, confidence, and standardsApplied"));
    properties.put("standardsApplied",
        typedArraySchema("Standards and recommended practices used"));
    properties.put("limitations", typedArraySchema("Limitations and assumptions"));
    properties.put("provenance", objectProp("MCP result provenance"));
    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for open-drain review input.
   *
   * @return JSON Schema string
   */
  public static String openDrainReviewInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "OpenDrainReviewInput");
    schema.put("description",
        "Input for run_open_drain_review. Accepts normalized STID/P&ID evidence and optional tagreader summaries for NORSOK S-001 Clause 9 review.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("projectName", stringProp("Project, asset, or review name"));
    properties.put("defaultLiquidLeakRateKgPerS",
        numberProp("Default worst credible process fire leak rate in kg/s, commonly 5 kg/s"));
    properties.put("items", typedArraySchema(
        "Review items with areaId, areaType, drainSystemType, sourceReferences, and evidence values"));
    properties.put("openDrainAreas", typedArraySchema("Alias for open-drain area review items"));
    properties.put("drainAreas", typedArraySchema("Alias for drain area review items"));
    properties.put("stidData", objectProp(
        "Normalized STID/P&ID extract with openDrainAreas, drainAreas, areaDrains, drainSystems, helideckDrains, temporaryStorageAreas, lineList, or equipment arrays"));
    properties.put("tagreaderData", objectProp(
        "Optional tagreader summary with sump level, backpressure, pump status, valve status, and backflow evidence"));
    schema.put("properties", properties);
    schema.put("anyOf", Arrays.asList(requiredSchema("items"), requiredSchema("openDrainAreas"),
        requiredSchema("drainAreas"), requiredSchema("stidData")));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for open-drain review output.
   *
   * @return JSON Schema string
   */
  public static String openDrainReviewOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "OpenDrainReviewOutput");
    schema.put("description",
        "Output from run_open_drain_review with item verdicts, NORSOK S-001 Clause 9 assessments, evidence values, and provenance.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("reviewType", stringProp("open_drain_review"));
    properties.put("overallVerdict",
        enumProp("Overall verdict", Arrays.asList("PASS", "PASS_WITH_WARNINGS", "FAIL")));
    properties.put("itemCount", intProp("Number of reviewed drain areas or systems"));
    properties.put("failedItems", intProp("Number of failed items"));
    properties.put("warningItems", intProp("Number of items with warnings"));
    properties.put("results", typedArraySchema(
        "Item results with areaId, verdict, confidence, evidenceValues, sourceReferences, and assessments"));
    properties.put("standardsApplied", typedArraySchema("Standards and clauses used"));
    properties.put("limitations", typedArraySchema("Limitations and assumptions"));
    properties.put("provenance", objectProp("MCP result provenance"));
    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for NORSOK S-001 Clause 10 process safety review input.
   *
   * @return JSON Schema string
   */
  public static String norsokS001Clause10ReviewInputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "NorsokS001Clause10ReviewInput");
    schema.put("description",
        "Input for run_norsok_s001_clause10_review. Accepts normalized C&E, SRS, PSV, STID/P&ID, instrument-data, and tagreader evidence for NORSOK S-001 Clause 10 process safety system review.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("projectName", stringProp("Project, asset, or review name"));
    properties.put("items", typedArraySchema(
        "Review items with functionId, functionType, equipmentTag, sourceReferences, lifecycle evidence, and Clause 10 evidence values"));
    properties.put("processSafetyFunctions", typedArraySchema("Process safety function records"));
    properties.put("psdValves", typedArraySchema("PSD/shutdown valve and final-element evidence"));
    properties.put("psvs", typedArraySchema("PSV and relief protection evidence"));
    properties.put("alarms", typedArraySchema("Alarm and operator action evidence"));
    properties.put("sifs", typedArraySchema("SIF, logic solver, and SRS evidence"));
    properties.put("secondaryPressureProtection",
        typedArraySchema("Instrumented secondary pressure protection evidence"));
    properties.put("stidData", objectProp(
        "Normalized STID/P&ID/C&E/SRS/PSV extract with processSafetyFunctions, psdValves, psvs, alarms, sifs, secondaryPressureProtection, utilityDependencies, survivabilityItems, instrumentData, or tagreaderEvidence arrays"));
    properties.put("tagreaderData", objectProp(
        "Optional tagreader or instrument-data summary with bypass, override, proof-test, demand-failure, and response-time evidence"));
    properties.put("lifecycleEvidence", objectProp(
        "Optional HAZID/HAZOP/LOPA to SRS to SIS/ESD/FGS implementation to verification/testing/operation traceability summary"));
    properties.put("safetySystemPerformanceInput", objectProp(
        "Optional input passed to run_safety_system_performance and embedded in the result"));
    properties.put("operationalStudyInput",
        objectProp("Optional input passed to runOperationalStudy and embedded in the result"));
    properties.put("dynamicSimulationInput", objectProp(
        "Optional input passed to run_dynamic and embedded as transient process evidence. Use for dynamic SIS/ESD/FGS/PSD scenario evidence when the ProcessSystem model implements the safety logic or valve actions."));
    schema.put("properties", properties);
    schema.put("anyOf",
        Arrays.asList(requiredSchema("items"), requiredSchema("processSafetyFunctions"),
            requiredSchema("stidData"), requiredSchema("tagreaderData"),
            requiredSchema("lifecycleEvidence")));
    return GSON.toJson(schema);
  }

  /**
   * Returns the JSON Schema for NORSOK S-001 Clause 10 process safety review output.
   *
   * @return JSON Schema string
   */
  public static String norsokS001Clause10ReviewOutputSchema() {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("title", "NorsokS001Clause10ReviewOutput");
    schema.put("description",
        "Output from run_norsok_s001_clause10_review with Clause 10 item verdicts, requirement assessments, evidence values, optional embedded analysis outputs, and provenance.");
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("status", enumProp("Result status", Arrays.asList("success", "error")));
    properties.put("reviewType", stringProp("norsok_s001_clause10_review"));
    properties.put("overallVerdict",
        enumProp("Overall verdict", Arrays.asList("PASS", "PASS_WITH_WARNINGS", "FAIL")));
    properties.put("itemCount", intProp("Number of reviewed functions and coverage records"));
    properties.put("failedItems", intProp("Number of failed review items"));
    properties.put("warningItems", intProp("Number of review items with warnings"));
    properties.put("results", typedArraySchema(
        "Item results with functionId, functionType, verdict, confidence, sourceReferences, evidenceValues, and assessments"));
    properties.put("embeddedAnalyses", objectProp(
        "Optional safetySystemPerformance, operationalStudy, and dynamicSimulation outputs embedded by the runner"));
    properties.put("extractionTemplates",
        typedArraySchema("Recommended document and instrument extraction templates"));
    properties.put("provenance", objectProp("MCP result provenance"));
    schema.put("properties", properties);
    schema.put("required", Collections.singletonList("status"));
    return GSON.toJson(schema);
  }

  // ========== Catalog Metadata ==========

  /**
   * Returns the list of tools with available schemas.
   *
   * @return list of tool names
   */
  public static List<String> getToolNames() {
    return Collections.unmodifiableList(Arrays.asList("run_flash", "run_process", "validate_input",
        "list_components", "run_batch", "get_property_table", "get_phase_envelope",
        "get_capabilities", "run_pvt", "run_flow_assurance", "calculate_standard", "run_pipeline",
        "run_water_hammer", "run_root_cause_analysis", "run_materials_review",
        "run_open_drain_review", "run_norsok_s001_clause10_review", "run_reservoir",
        "run_field_economics", "run_dynamic", "run_bioprocess", "size_equipment",
        "compare_processes", "manage_session", "visualize", "run_hazop", "run_barrier_register",
        "run_safety_system_performance"));
  }

  /**
   * Returns a schema by tool name and schema type (input/output).
   *
   * @param toolName the tool name
   * @param schemaType "input" or "output"
   * @return the JSON Schema string, or null if not found
   */
  public static String getSchema(String toolName, String schemaType) {
    if ("run_flash".equals(toolName)) {
      return "input".equals(schemaType) ? flashInputSchema() : flashOutputSchema();
    } else if ("run_process".equals(toolName)) {
      return "input".equals(schemaType) ? processInputSchema() : processOutputSchema();
    } else if ("validate_input".equals(toolName)) {
      return "input".equals(schemaType) ? validateInputSchema() : validateOutputSchema();
    } else if ("list_components".equals(toolName)) {
      return "output".equals(schemaType) ? componentSearchOutputSchema() : null;
    } else if ("run_batch".equals(toolName)) {
      return "input".equals(schemaType) ? batchInputSchema() : batchOutputSchema();
    } else if ("get_property_table".equals(toolName)) {
      return "input".equals(schemaType) ? propertyTableInputSchema() : propertyTableOutputSchema();
    } else if ("get_phase_envelope".equals(toolName)) {
      return "input".equals(schemaType) ? phaseEnvelopeInputSchema() : phaseEnvelopeOutputSchema();
    } else if ("get_capabilities".equals(toolName)) {
      return "output".equals(schemaType) ? capabilitiesOutputSchema() : null;
    } else if ("run_pvt".equals(toolName)) {
      return "input".equals(schemaType) ? pvtInputSchema() : pvtOutputSchema();
    } else if ("run_flow_assurance".equals(toolName)) {
      return "input".equals(schemaType) ? flowAssuranceInputSchema() : null;
    } else if ("calculate_standard".equals(toolName)) {
      return "input".equals(schemaType) ? standardsInputSchema() : null;
    } else if ("run_pipeline".equals(toolName)) {
      return "input".equals(schemaType) ? pipelineInputSchema() : null;
    } else if ("run_water_hammer".equals(toolName)) {
      return "input".equals(schemaType) ? waterHammerInputSchema() : null;
    } else if ("run_root_cause_analysis".equals(toolName)) {
      return "input".equals(schemaType) ? rootCauseInputSchema() : rootCauseOutputSchema();
    } else if ("run_materials_review".equals(toolName)) {
      return "input".equals(schemaType) ? materialsReviewInputSchema()
          : materialsReviewOutputSchema();
    } else if ("run_open_drain_review".equals(toolName)) {
      return "input".equals(schemaType) ? openDrainReviewInputSchema()
          : openDrainReviewOutputSchema();
    } else if ("run_norsok_s001_clause10_review".equals(toolName)) {
      return "input".equals(schemaType) ? norsokS001Clause10ReviewInputSchema()
          : norsokS001Clause10ReviewOutputSchema();
    } else if ("run_reservoir".equals(toolName)) {
      return "input".equals(schemaType) ? reservoirInputSchema() : null;
    } else if ("run_field_economics".equals(toolName)) {
      return "input".equals(schemaType) ? fieldEconomicsInputSchema() : null;
    } else if ("run_dynamic".equals(toolName)) {
      return "input".equals(schemaType) ? dynamicInputSchema() : null;
    } else if ("run_bioprocess".equals(toolName)) {
      return "input".equals(schemaType) ? bioprocessInputSchema() : null;
    } else if ("size_equipment".equals(toolName)) {
      return "input".equals(schemaType) ? equipmentSizingInputSchema()
          : equipmentSizingOutputSchema();
    } else if ("compare_processes".equals(toolName)) {
      return "input".equals(schemaType) ? comparisonInputSchema() : null;
    } else if ("manage_session".equals(toolName)) {
      return "input".equals(schemaType) ? sessionInputSchema() : null;
    } else if ("visualize".equals(toolName)) {
      return "input".equals(schemaType) ? visualizationInputSchema() : null;
    } else if ("run_hazop".equals(toolName)) {
      return "input".equals(schemaType) ? hazopInputSchema() : hazopOutputSchema();
    } else if ("run_barrier_register".equals(toolName)) {
      return "input".equals(schemaType) ? barrierRegisterInputSchema()
          : barrierRegisterOutputSchema();
    } else if ("run_safety_system_performance".equals(toolName)) {
      return "input".equals(schemaType) ? safetySystemPerformanceInputSchema()
          : safetySystemPerformanceOutputSchema();
    }
    return null;
  }

  /**
   * Returns a full catalog listing of all available schemas as JSON.
   *
   * @return JSON string listing all schemas
   */
  public static String getCatalogJson() {
    Map<String, Object> catalog = new LinkedHashMap<String, Object>();
    for (String tool : getToolNames()) {
      Map<String, String> schemas = new LinkedHashMap<String, String>();
      schemas.put("inputSchemaUri", "neqsim://schema/" + tool + "/input");
      schemas.put("outputSchemaUri", "neqsim://schema/" + tool + "/output");
      catalog.put(tool, schemas);
    }
    return GSON.toJson(catalog);
  }

  // ========== Helpers ==========

  /**
   * Creates a ValueWithUnit JSON Schema fragment (oneOf: number or object).
   *
   * @param description the field description
   * @param unitDescription the unit field description
   * @param allowedUnits the allowed unit values
   * @param defaultUnit the default unit
   * @return the schema map
   */
  private static Map<String, Object> valueWithUnitSchema(String description, String unitDescription,
      List<String> allowedUnits, String defaultUnit) {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("description", description + " (bare number uses " + defaultUnit + ")");

    List<Object> oneOf = new java.util.ArrayList<Object>();

    // Option 1: bare number
    Map<String, Object> bareNumber = new LinkedHashMap<String, Object>();
    bareNumber.put("type", "number");
    oneOf.add(bareNumber);

    // Option 2: object with value and unit
    Map<String, Object> withUnit = new LinkedHashMap<String, Object>();
    withUnit.put("type", "object");
    Map<String, Object> unitProps = new LinkedHashMap<String, Object>();
    Map<String, Object> valueProp = new LinkedHashMap<String, Object>();
    valueProp.put("type", "number");
    unitProps.put("value", valueProp);
    Map<String, Object> unitProp = new LinkedHashMap<String, Object>();
    unitProp.put("type", "string");
    unitProp.put("description", unitDescription);
    unitProp.put("enum", allowedUnits);
    unitProp.put("default", defaultUnit);
    unitProps.put("unit", unitProp);
    withUnit.put("properties", unitProps);
    withUnit.put("required", Collections.singletonList("value"));
    oneOf.add(withUnit);

    schema.put("oneOf", oneOf);
    return schema;
  }

  /**
   * Creates a simple string property schema.
   *
   * @param description the property description
   * @return the schema map
   */
  private static Map<String, Object> stringProp(String description) {
    Map<String, Object> prop = new LinkedHashMap<String, Object>();
    prop.put("type", "string");
    prop.put("description", description);
    return prop;
  }

  /**
   * Creates a simple integer property schema.
   *
   * @param description the property description
   * @return the schema map
   */
  private static Map<String, Object> intProp(String description) {
    Map<String, Object> prop = new LinkedHashMap<String, Object>();
    prop.put("type", "integer");
    prop.put("description", description);
    return prop;
  }

  /**
   * Creates a simple number property schema.
   *
   * @param description the property description
   * @return the schema map
   */
  private static Map<String, Object> numberProp(String description) {
    Map<String, Object> prop = new LinkedHashMap<String, Object>();
    prop.put("type", "number");
    prop.put("description", description);
    return prop;
  }

  /**
   * Creates a simple boolean property schema.
   *
   * @param description the property description
   * @return the schema map
   */
  private static Map<String, Object> boolProp(String description) {
    Map<String, Object> prop = new LinkedHashMap<String, Object>();
    prop.put("type", "boolean");
    prop.put("description", description);
    return prop;
  }

  /**
   * Creates an enum string property schema.
   *
   * @param description the property description
   * @param values the enum values
   * @return the schema map
   */
  private static Map<String, Object> enumProp(String description, List<String> values) {
    Map<String, Object> prop = new LinkedHashMap<String, Object>();
    prop.put("type", "string");
    prop.put("description", description);
    prop.put("enum", values);
    return prop;
  }

  /**
   * Creates a generic object property schema.
   *
   * @param description the property description
   * @return the schema map
   */
  private static Map<String, Object> objectProp(String description) {
    Map<String, Object> prop = new LinkedHashMap<String, Object>();
    prop.put("type", "object");
    prop.put("description", description);
    return prop;
  }

  /**
   * Creates a generic array of object schema.
   *
   * @param description the array description
   * @return the schema map
   */
  private static Map<String, Object> typedArraySchema(String description) {
    Map<String, Object> array = new LinkedHashMap<String, Object>();
    array.put("type", "array");
    array.put("description", description);
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("type", "object");
    array.put("items", item);
    return array;
  }

  /**
   * Creates a minimal JSON Schema required-field alternative.
   *
   * @param fields field names that must be present
   * @return the schema map
   */
  private static Map<String, Object> requiredSchema(String... fields) {
    Map<String, Object> required = new LinkedHashMap<String, Object>();
    required.put("required", Arrays.asList(fields));
    return required;
  }
}
