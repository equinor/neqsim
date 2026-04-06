package neqsim.mcp.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;
import neqsim.mcp.runners.ComponentQuery;
import neqsim.mcp.runners.FlashRunner;
import neqsim.mcp.runners.ProcessRunner;
import neqsim.mcp.runners.Validator;
import neqsim.mcp.runners.AutomationRunner;

/**
 * MCP tools for NeqSim thermodynamic calculations and process simulation.
 *
 * <p>
 * Each method annotated with {@code @Tool} is exposed as an MCP tool that LLM clients can discover
 * and invoke via the Model Context Protocol. The tools delegate to the stateless runner layer in
 * {@code neqsim.mcp.runners}.
 * </p>
 */
@ApplicationScoped
public class NeqSimTools {

  /**
   * Run a thermodynamic flash calculation on a fluid mixture.
   *
   * @param components fluid composition as JSON object
   * @param temperature temperature value
   * @param temperatureUnit temperature unit
   * @param pressure pressure value
   * @param pressureUnit pressure unit
   * @param eos equation of state model
   * @param flashType type of flash calculation
   * @return JSON string with phase equilibrium results
   */
  @Tool(description = "Run a thermodynamic flash calculation on a fluid mixture. "
      + "Computes phase equilibrium, densities, viscosities, heat capacities, "
      + "and component compositions for each phase present. "
      + "Supports multiple equations of state and flash types.")
  public String runFlash(
      @ToolArg(description = "Fluid composition as JSON object mapping component names "
          + "to mole fractions, e.g. {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}. "
          + "Use searchComponents tool to find valid names.") String components,
      @ToolArg(description = "Temperature value (number)") double temperature,
      @ToolArg(description = "Temperature unit: C, K, or F") String temperatureUnit,
      @ToolArg(description = "Pressure value (number)") double pressure,
      @ToolArg(
          description = "Pressure unit: bara, barg, Pa, kPa, MPa, psi, or atm") String pressureUnit,
      @ToolArg(description = "Equation of state: SRK (Soave-Redlich-Kwong, general purpose), "
          + "PR (Peng-Robinson), CPA (CPA-SRK for associating fluids like water/methanol/glycol), "
          + "GERG2008 (high-accuracy natural gas), PCSAFT (PC-SAFT), "
          + "UMRPRU (UMR-PRU with Mathias-Copeman)") String eos,
      @ToolArg(description = "Flash type: TP (temperature-pressure, most common), "
          + "PH (pressure-enthalpy), PS (pressure-entropy), " + "TV (temperature-volume), "
          + "dewPointT (dew point T at given P), dewPointP (dew point P at given T), "
          + "bubblePointT (bubble point T at given P), bubblePointP (bubble point P at given T), "
          + "hydrateTP (hydrate equilibrium T at given P)") String flashType) {
    try {
      JsonObject json = new JsonObject();
      json.add("components", JsonParser.parseString(components));

      JsonObject temp = new JsonObject();
      temp.addProperty("value", temperature);
      temp.addProperty("unit", temperatureUnit);
      json.add("temperature", temp);

      JsonObject press = new JsonObject();
      press.addProperty("value", pressure);
      press.addProperty("unit", pressureUnit);
      json.add("pressure", press);

      json.addProperty("model", eos);
      json.addProperty("flashType", flashType);

      return FlashRunner.run(json.toString());
    } catch (Exception e) {
      return errorJson("Flash calculation failed: " + e.getMessage());
    }
  }

  /**
   * Run a process simulation from a JSON definition.
   *
   * @param processJson complete process definition as JSON
   * @return JSON string with simulation results
   */
  @Tool(description = "Run a process simulation from a JSON definition. "
      + "Build flowsheets with streams, separators, compressors, heat exchangers, "
      + "valves, mixers, splitters, distillation columns, and pipelines. "
      + "Use getExample with category 'process' for templates.")
  public String runProcess(
      @ToolArg(description = "Complete process definition as JSON string. Must include "
          + "'fluid' with components and model, and 'equipment' array with process units. "
          + "Use getExample(category='process', name='simple-separation') for a template.") String processJson) {
    try {
      return ProcessRunner.run(processJson);
    } catch (Exception e) {
      return errorJson("Process simulation failed: " + e.getMessage());
    }
  }

  /**
   * Validate flash or process JSON input before running.
   *
   * @param inputJson the JSON string to validate
   * @return JSON string with validation results
   */
  @Tool(description = "Validate a flash or process JSON input before running it. "
      + "Checks component names, temperature/pressure ranges, EOS compatibility, "
      + "and process wiring. Returns issues with severity and fix suggestions.")
  public String validateInput(
      @ToolArg(description = "JSON string to validate. Can be a flash input or "
          + "process definition - the validator auto-detects the type.") String inputJson) {
    try {
      return Validator.validate(inputJson);
    } catch (Exception e) {
      return errorJson("Validation failed: " + e.getMessage());
    }
  }

  /**
   * Search the NeqSim component database.
   *
   * @param query search term for component lookup
   * @return JSON string with matching components
   */
  @Tool(description = "Search the NeqSim thermodynamic component database by name. "
      + "Returns matching component names for use in flash calculations and process simulations. "
      + "Supports partial matching (e.g. 'meth' finds 'methane', 'methanol').")
  public String searchComponents(
      @ToolArg(description = "Component name or partial name to search for. "
          + "Examples: 'methane', 'C3', 'water', 'hydro'. "
          + "Empty string returns all components.") String query) {
    try {
      return ComponentQuery.search(query);
    } catch (Exception e) {
      return errorJson("Component search failed: " + e.getMessage());
    }
  }

  /**
   * Get an example JSON template.
   *
   * @param category the example category
   * @param name the example name
   * @return JSON example string
   */
  @Tool(description = "Get an example JSON template for NeqSim tools. "
      + "Categories: flash (tp-simple-gas, tp-two-phase, dew-point-t, "
      + "bubble-point-p, cpa-with-water), process (simple-separation, "
      + "compression-with-cooling), validation (error-flash).")
  public String getExample(
      @ToolArg(description = "Example category: flash, process, or validation") String category,
      @ToolArg(
          description = "Example name, e.g. 'tp-simple-gas' or 'simple-separation'") String name) {
    String example = ExampleCatalog.getExample(category, name);
    if (example != null) {
      return example;
    }
    return errorJson("Example not found: " + category + "/" + name
        + ". Use categories: flash, process, validation");
  }

  /**
   * Get a JSON schema for a tool's input or output.
   *
   * @param toolName the tool name
   * @param schemaType input or output
   * @return JSON schema string
   */
  @Tool(description = "Get the JSON schema for a NeqSim tool's input or output format. "
      + "Tools: run_flash, run_process, validate_input, search_components. "
      + "Types: input, output.")
  public String getSchema(@ToolArg(
      description = "Tool name: run_flash, run_process, validate_input, or search_components") String toolName,
      @ToolArg(description = "Schema type: input or output") String schemaType) {
    String schema = SchemaCatalog.getSchema(toolName, schemaType);
    if (schema != null) {
      return schema;
    }
    return errorJson("Schema not found: " + toolName + "/" + schemaType);
  }

  /**
   * List all equipment units in a process simulation with their types.
   *
   * @param processJson complete process definition as JSON
   * @return JSON string with list of unit names and equipment types
   */
  @Tool(description = "Run a process simulation and list all addressable equipment units. "
      + "Returns unit names and types for use with listUnitVariables, "
      + "getSimulationVariable, and setSimulationVariable tools.")
  public String listSimulationUnits(
      @ToolArg(description = "Complete process definition as JSON string. "
          + "Same format as runProcess.") String processJson) {
    try {
      return AutomationRunner.listUnits(processJson);
    } catch (Exception e) {
      return errorJson("Failed to list units: " + e.getMessage());
    }
  }

  /**
   * List all readable/writable variables for a specific equipment unit.
   *
   * @param processJson complete process definition as JSON
   * @param unitName the equipment unit name to query
   * @return JSON string with list of variables and their metadata
   */
  @Tool(description = "Run a process simulation and list all variables for a specific "
      + "equipment unit. Each variable has an address (for reading/writing), type "
      + "(INPUT = writable, OUTPUT = read-only), default unit, and description. "
      + "Use listSimulationUnits first to discover available unit names.")
  public String listUnitVariables(
      @ToolArg(description = "Complete process definition as JSON string.") String processJson,
      @ToolArg(description = "Equipment unit name to list variables for, "
          + "e.g. 'HP Separator', 'Compressor Stage 1'. "
          + "Use listSimulationUnits to find valid names.") String unitName) {
    try {
      return AutomationRunner.listVariables(processJson, unitName);
    } catch (Exception e) {
      return errorJson("Failed to list variables: " + e.getMessage());
    }
  }

  /**
   * Read a specific simulation variable value by dot-notation address.
   *
   * @param processJson complete process definition as JSON
   * @param address dot-notation variable address
   * @param unit desired unit of measurement
   * @return JSON string with the variable value
   */
  @Tool(description = "Run a process simulation and read a specific variable value "
      + "using dot-notation addressing. Example addresses: "
      + "'HP Sep.gasOutStream.temperature', 'Compressor.power', "
      + "'Feed.flowRate'. Use listUnitVariables to discover valid addresses.")
  public String getSimulationVariable(
      @ToolArg(description = "Complete process definition as JSON string.") String processJson,
      @ToolArg(description = "Dot-notation variable address, e.g. "
          + "'HP Sep.gasOutStream.temperature' or 'Compressor.power'. "
          + "Use listUnitVariables to find valid addresses.") String address,
      @ToolArg(description = "Desired unit of measurement: "
          + "Temperature: C, K, F | Pressure: bara, barg, Pa, kPa, MPa, psi | "
          + "Flow: kg/hr, m3/hr, MSm3/day | Power: kW, MW, hp") String unit) {
    try {
      return AutomationRunner.getVariable(processJson, address, unit);
    } catch (Exception e) {
      return errorJson("Failed to get variable: " + e.getMessage());
    }
  }

  /**
   * Modify a simulation variable and re-run the process.
   *
   * @param processJson complete process definition as JSON
   * @param address dot-notation variable address to modify
   * @param value new value to set
   * @param unit unit of the value
   * @return JSON string with updated simulation results
   */
  @Tool(description = "Run a process, modify an INPUT variable, re-run, and return "
      + "updated results. Use this for sensitivity analysis or optimization. "
      + "Only INPUT-type variables can be modified (use listUnitVariables to check). "
      + "Example: change compressor outlet pressure and see effect on power.")
  public String setSimulationVariable(
      @ToolArg(description = "Complete process definition as JSON string.") String processJson,
      @ToolArg(description = "Dot-notation address of the INPUT variable to modify, "
          + "e.g. 'Compressor.outletPressure'.") String address,
      @ToolArg(description = "New value for the variable.") double value,
      @ToolArg(description = "Unit of measurement for the value, "
          + "e.g. 'C', 'bara', 'kg/hr'.") String unit) {
    try {
      return AutomationRunner.setVariableAndRun(processJson, address, value, unit);
    } catch (Exception e) {
      return errorJson("Failed to set variable: " + e.getMessage());
    }
  }

  /**
   * Save a process simulation state as a lifecycle snapshot.
   *
   * @param processJson complete process definition as JSON
   * @param stateName name for the snapshot
   * @param stateVersion version string
   * @return JSON string with the serialized state
   */
  @Tool(description = "Run a process simulation and save its complete state as a "
      + "JSON lifecycle snapshot. Use for reproducibility, version tracking, "
      + "and comparing design iterations. The returned state can be passed to "
      + "compareSimulationStates to find differences between versions.")
  public String saveSimulationState(
      @ToolArg(description = "Complete process definition as JSON string.") String processJson,
      @ToolArg(description = "Name for the state snapshot, "
          + "e.g. 'Gas Processing Base Case'.") String stateName,
      @ToolArg(description = "Version string, e.g. '1.0.0'.") String stateVersion) {
    try {
      return AutomationRunner.saveState(processJson, stateName, stateVersion);
    } catch (Exception e) {
      return errorJson("Failed to save state: " + e.getMessage());
    }
  }

  /**
   * Compare two simulation state snapshots and return differences.
   *
   * @param stateJson1 first state JSON from saveSimulationState
   * @param stateJson2 second state JSON from saveSimulationState
   * @return JSON string with differences between the two states
   */
  @Tool(description = "Compare two simulation state snapshots and return the differences. "
      + "Shows modified parameters, added/removed equipment, and changed stream conditions. "
      + "Use after saveSimulationState to track design changes between iterations.")
  public String compareSimulationStates(
      @ToolArg(description = "First state JSON (from saveSimulationState 'state' field)") String stateJson1,
      @ToolArg(description = "Second state JSON (from saveSimulationState 'state' field)") String stateJson2) {
    try {
      return AutomationRunner.compareStates(stateJson1, stateJson2);
    } catch (Exception e) {
      return errorJson("Failed to compare states: " + e.getMessage());
    }
  }

  /**
   * Diagnose a failed automation operation and get suggestions for fixing it. Call this when
   * getSimulationVariable or setSimulationVariable returns an error to get actionable
   * remediation hints including fuzzy name matches and auto-corrections.
   *
   * @param processJson process definition as JSON
   * @param failedAddress the address that failed
   * @param operation the operation that failed
   * @return JSON diagnostic result with suggestions
   */
  @Tool(description = "Diagnose a failed automation operation and get suggestions for fixing it. "
      + "Call this when getSimulationVariable or setSimulationVariable returns an error. "
      + "Returns fuzzy name matches, auto-corrections, and actionable remediation hints. "
      + "Use this tool to self-correct and retry with the corrected address.")
  public String diagnoseAutomation(
      @ToolArg(description = "Process definition as JSON string") String processJson,
      @ToolArg(description = "The address that failed, e.g. 'HP separator.gasOut.temp'") String failedAddress,
      @ToolArg(description = "The operation that failed: 'get', 'set', or 'list'") String operation) {
    try {
      return AutomationRunner.diagnose(processJson, failedAddress, operation);
    } catch (Exception e) {
      return errorJson("Failed to diagnose: " + e.getMessage());
    }
  }

  /**
   * Get the automation learning report showing operation history, success rates, error patterns,
   * and learned corrections.
   *
   * @param processJson process definition as JSON
   * @return JSON learning report
   */
  @Tool(description = "Get the automation learning report showing operation history statistics, "
      + "success rates, error patterns, learned auto-corrections, and recommendations. "
      + "Use this after multiple automation operations to understand what went wrong "
      + "and improve future calls.")
  public String getAutomationLearningReport(
      @ToolArg(description = "Process definition as JSON string") String processJson) {
    try {
      return AutomationRunner.getLearningReport(processJson);
    } catch (Exception e) {
      return errorJson("Failed to get learning report: " + e.getMessage());
    }
  }

  private static String errorJson(String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", message);
    return error.toString();
  }
}
