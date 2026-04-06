package neqsim.mcp.catalog;

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
    schema.put("description", "Input for a process simulation (run_process tool)");
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
        "Ordered list of equipment units. Each unit has 'type', 'name', 'inlet', and 'properties'.");
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

    schema.put("properties", properties);
    schema.put("required", Arrays.asList("fluid", "process"));

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

  // ========== Catalog Metadata ==========

  /**
   * Returns the list of tools with available schemas.
   *
   * @return list of tool names
   */
  public static List<String> getToolNames() {
    return Collections.unmodifiableList(
        Arrays.asList("run_flash", "run_process", "validate_input", "list_components", "run_batch",
            "get_property_table", "get_phase_envelope", "get_capabilities"));
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
}
