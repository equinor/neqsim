package neqsim.mcp.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;
import neqsim.mcp.runners.BioprocessRunner;
import neqsim.mcp.runners.ComponentQuery;
import neqsim.mcp.runners.DynamicRunner;
import neqsim.mcp.runners.FieldDevelopmentRunner;
import neqsim.mcp.runners.FlashRunner;
import neqsim.mcp.runners.FlowAssuranceRunner;
import neqsim.mcp.runners.PVTRunner;
import neqsim.mcp.runners.PipelineRunner;
import neqsim.mcp.runners.ProcessRunner;
import neqsim.mcp.runners.ReservoirRunner;
import neqsim.mcp.runners.StandardsRunner;
import neqsim.mcp.runners.Validator;
import neqsim.mcp.runners.AutomationRunner;
import neqsim.mcp.runners.BarrierRegisterRunner;
import neqsim.mcp.runners.BatchRunner;
import neqsim.mcp.runners.CapabilitiesRunner;
import neqsim.mcp.runners.CrossValidationRunner;
import neqsim.mcp.runners.EngineeringValidator;
import neqsim.mcp.runners.ParametricStudyRunner;
import neqsim.mcp.runners.PhaseEnvelopeRunner;
import neqsim.mcp.runners.PluginRegistry;
import neqsim.mcp.runners.ProgressTracker;
import neqsim.mcp.runners.PropertyTableRunner;
import neqsim.mcp.runners.ReportRunner;
import neqsim.mcp.runners.SecurityRunner;
import neqsim.mcp.runners.SessionRunner;
import neqsim.mcp.runners.StatePersistenceRunner;
import neqsim.mcp.runners.StreamingRunner;
import neqsim.mcp.runners.TaskSolverRunner;
import neqsim.mcp.runners.TaskWorkflowBridge;
import neqsim.mcp.runners.ValidationProfileRunner;
import neqsim.mcp.runners.VisualizationRunner;
import neqsim.mcp.runners.BenchmarkTrust;
import neqsim.mcp.runners.CompositionRunner;
import neqsim.mcp.runners.DataCatalogRunner;
import neqsim.mcp.runners.EquipmentSizingRunner;
import neqsim.mcp.runners.FlareRadiationRunner;
import neqsim.mcp.runners.HAZOPStudyRunner;
import neqsim.mcp.runners.IndustrialProfile;
import neqsim.mcp.runners.LOPARunner;
import neqsim.mcp.runners.MaterialsReviewRunner;
import neqsim.mcp.runners.ProcessComparisonRunner;
import neqsim.mcp.runners.ReliefRunner;
import neqsim.mcp.runners.RiskMatrixRunner;
import neqsim.mcp.runners.SafetySystemPerformanceRunner;
import neqsim.mcp.runners.SILRunner;

/**
 * MCP tools for NeqSim thermodynamic calculations and process simulation.
 *
 * <p>
 * Each method annotated with {@code @Tool} is exposed as an MCP tool that LLM clients can discover
 * and invoke via the Model Context Protocol. The tools delegate to the stateless runner layer in
 * {@code neqsim.mcp.runners}.
 * </p>
 *
 * <p>
 * Tools are classified into four categories by the {@link IndustrialProfile} system:
 * </p>
 * <ul>
 * <li><b>ADVISORY</b> — read-only discovery and validation (always allowed)</li>
 * <li><b>CALCULATION</b> — stateless engineering calculations</li>
 * <li><b>EXECUTION</b> — state-modifying operations (may require approval)</li>
 * <li><b>PLATFORM</b> — security, persistence, multi-server (restricted in production)</li>
 * </ul>
 *
 * <p>
 * When auto-validation is enabled (default), every CALCULATION tool automatically runs
 * {@link EngineeringValidator#validate(String, String)} on its output and appends a
 * {@code "validation"} block to the response.
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

      return withAutoValidation(FlashRunner.run(json.toString()), "flash");
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
      + "valves, mixers, splitters, distillation columns, pipelines, and other "
      + "factory-backed process equipment. Also accepts ProcessModel JSON with "
      + "top-level 'areas' for multi-area plants. "
      + "Use getExample with category 'process' for templates.")
  public String runProcess(
      @ToolArg(description = "Complete process definition as JSON string. Must include "
          + "either 'fluid' with components and model plus a 'process' array, or "
          + "top-level 'areas' containing named process-area JSON objects. "
          + "Use getExample(category='process', name='simple-separation') for a template.") String processJson) {
    try {
      return withAutoValidation(ProcessRunner.run(processJson), "process");
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
      + "compression-with-cooling), validation (error-flash), "
      + "batch (temperature-sweep, pressure-sweep), "
      + "property-table (temperature-sweep, pressure-sweep), "
      + "phase-envelope (natural-gas), safety (barrier-register, hazop-study).")
  public String getExample(@ToolArg(
      description = "Example category: flash, process, validation, safety, etc.") String category,
      @ToolArg(
          description = "Example name, e.g. 'tp-simple-gas', 'simple-separation', or 'hazop-study'") String name) {
    String example = ExampleCatalog.getExample(category, name);
    if (example != null) {
      return example;
    }
    return errorJson("Example not found: " + category + "/" + name
        + ". Use getExample with a listed category such as flash, process, validation, or safety");
  }

  /**
   * Get a JSON schema for a tool's input or output.
   *
   * @param toolName the tool name
   * @param schemaType input or output
   * @return JSON schema string
   */
  @Tool(description = "Get the JSON schema for a NeqSim tool's input or output format. "
      + "Tools: run_flash, run_process, validate_input, list_components, "
      + "run_batch, get_property_table, get_phase_envelope, get_capabilities, "
      + "run_hazop, run_barrier_register. " + "Types: input, output.")
  public String getSchema(
      @ToolArg(description = "Tool name: run_flash, run_process, validate_input, list_components, "
          + "run_batch, get_property_table, get_phase_envelope, get_capabilities, run_hazop, "
          + "or run_barrier_register") String toolName,
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
  public String compareSimulationStates(@ToolArg(
      description = "First state JSON (from saveSimulationState 'state' field)") String stateJson1,
      @ToolArg(
          description = "Second state JSON (from saveSimulationState 'state' field)") String stateJson2) {
    try {
      return AutomationRunner.compareStates(stateJson1, stateJson2);
    } catch (Exception e) {
      return errorJson("Failed to compare states: " + e.getMessage());
    }
  }

  /**
   * Diagnose a failed automation operation and get suggestions for fixing it. Call this when
   * getSimulationVariable or setSimulationVariable returns an error to get actionable remediation
   * hints including fuzzy name matches and auto-corrections.
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
      @ToolArg(
          description = "The address that failed, e.g. 'HP separator.gasOut.temp'") String failedAddress,
      @ToolArg(
          description = "The operation that failed: 'get', 'set', or 'list'") String operation) {
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
  // ═══════════════════════════════════════════════════════════════════════════
  // Quick engineering calculation tools (no process flowsheet required)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Calculate a property table by sweeping temperature or pressure.
   *
   * @param components fluid composition as JSON
   * @param sweep sweep variable (temperature or pressure)
   * @param sweepFrom start value
   * @param sweepFromUnit unit for start value
   * @param sweepTo end value
   * @param sweepToUnit unit for end value
   * @param fixedValue the fixed condition value
   * @param fixedUnit the fixed condition unit
   * @param points number of data points
   * @param eos equation of state
   * @return JSON property table
   */
  @Tool(description = "Calculate a table of thermodynamic properties by sweeping temperature "
      + "or pressure over a range. Returns density, viscosity, Cp, Z-factor, enthalpy, "
      + "and more at each point. This is the 'quick engineering answer' tool — no process "
      + "flowsheet needed. Includes provenance metadata for trust assessment.")
  public String getPropertyTable(
      @ToolArg(description = "Fluid composition as JSON object mapping component names "
          + "to mole fractions, e.g. {\"methane\": 0.85, \"ethane\": 0.10}") String components,
      @ToolArg(description = "Variable to sweep: 'temperature' (vary T at fixed P) "
          + "or 'pressure' (vary P at fixed T)") String sweep,
      @ToolArg(description = "Start of sweep range (number)") double sweepFrom,
      @ToolArg(description = "Unit for start value: C, K, F (temperature) "
          + "or bara, barg, Pa, kPa, MPa, psi (pressure)") String sweepFromUnit,
      @ToolArg(description = "End of sweep range (number)") double sweepTo,
      @ToolArg(description = "Unit for end value (same type as sweepFromUnit)") String sweepToUnit,
      @ToolArg(description = "Fixed condition value: pressure if sweeping temperature, "
          + "or temperature if sweeping pressure") double fixedValue,
      @ToolArg(description = "Unit for fixed condition: bara/barg/Pa/kPa/MPa/psi "
          + "(if fixed pressure) or C/K/F (if fixed temperature)") String fixedUnit,
      @ToolArg(description = "Number of data points (2-200, default 20)") int points,
      @ToolArg(description = "Equation of state: SRK, PR, CPA, GERG2008, "
          + "PCSAFT, UMRPRU") String eos) {
    try {
      com.google.gson.JsonObject json = new com.google.gson.JsonObject();
      json.add("components", com.google.gson.JsonParser.parseString(components));
      json.addProperty("model", eos);
      json.addProperty("sweep", sweep);

      com.google.gson.JsonObject from = new com.google.gson.JsonObject();
      from.addProperty("value", sweepFrom);
      from.addProperty("unit", sweepFromUnit);
      json.add("sweepFrom", from);

      com.google.gson.JsonObject to = new com.google.gson.JsonObject();
      to.addProperty("value", sweepTo);
      to.addProperty("unit", sweepToUnit);
      json.add("sweepTo", to);

      json.addProperty("points", points);

      if ("temperature".equalsIgnoreCase(sweep)) {
        com.google.gson.JsonObject fixedP = new com.google.gson.JsonObject();
        fixedP.addProperty("value", fixedValue);
        fixedP.addProperty("unit", fixedUnit);
        json.add("fixedPressure", fixedP);
      } else {
        com.google.gson.JsonObject fixedT = new com.google.gson.JsonObject();
        fixedT.addProperty("value", fixedValue);
        fixedT.addProperty("unit", fixedUnit);
        json.add("fixedTemperature", fixedT);
      }

      return PropertyTableRunner.run(json.toString());
    } catch (Exception e) {
      return errorJson("Property table calculation failed: " + e.getMessage());
    }
  }

  /**
   * Calculate the PT phase envelope for a fluid mixture.
   *
   * @param components fluid composition as JSON
   * @param eos equation of state
   * @return JSON with phase envelope data
   */
  @Tool(description = "Calculate the PT phase envelope (bubble/dew point curves) for a "
      + "fluid mixture. Returns pressure-temperature points along the phase boundary, "
      + "plus cricondenbar and cricondentherm if available. Essential for flow assurance "
      + "and pipeline design. Includes provenance metadata.")
  public String getPhaseEnvelope(
      @ToolArg(description = "Fluid composition as JSON object mapping component names "
          + "to mole fractions, e.g. {\"methane\": 0.85, \"ethane\": 0.10}") String components,
      @ToolArg(description = "Equation of state: SRK, PR, CPA, GERG2008, "
          + "PCSAFT, UMRPRU") String eos) {
    try {
      com.google.gson.JsonObject json = new com.google.gson.JsonObject();
      json.add("components", com.google.gson.JsonParser.parseString(components));
      json.addProperty("model", eos);
      return PhaseEnvelopeRunner.run(json.toString());
    } catch (Exception e) {
      return errorJson("Phase envelope calculation failed: " + e.getMessage());
    }
  }

  /**
   * Discover NeqSim capabilities, supported models, equipment types, and calculation modes.
   *
   * @return JSON capabilities manifest
   */
  @Tool(description = "Discover what NeqSim can calculate. Returns a structured manifest "
      + "of supported thermodynamic models, flash types, equipment types, engineering domains, "
      + "and calculation modes. Call this first to understand available capabilities "
      + "before deciding which tool to use. Also describes the trust model and provenance "
      + "metadata included in every response.")
  public String getCapabilities() {
    try {
      return CapabilitiesRunner.getCapabilities();
    } catch (Exception e) {
      return errorJson("Failed to get capabilities: " + e.getMessage());
    }
  }

  /**
   * Run a batch of flash calculations in a single call.
   *
   * @param components base fluid composition as JSON
   * @param eos equation of state
   * @param flashType flash type for all cases (can be overridden per case)
   * @param cases JSON array of case specifications
   * @return JSON string with batch results
   */
  @Tool(description = "Run multiple flash calculations in a single call for sensitivity "
      + "studies and parameter sweeps. Define a base fluid, then provide an array of cases "
      + "each varying temperature, pressure, or composition. Much more efficient than "
      + "calling runFlash repeatedly. Returns per-case results with a summary. "
      + "Max 500 cases per batch.")
  public String runBatch(
      @ToolArg(description = "Base fluid composition as JSON object mapping component names "
          + "to mole fractions, e.g. {\"methane\": 0.85, \"ethane\": 0.10}. "
          + "Individual cases can override components.") String components,
      @ToolArg(description = "Equation of state: SRK, PR, CPA, GERG2008, "
          + "PCSAFT, UMRPRU") String eos,
      @ToolArg(description = "Flash type for all cases (unless overridden per case): "
          + "TP, PH, PS, TV, dewPointT, dewPointP, bubblePointT, bubblePointP, "
          + "hydrateTP") String flashType,
      @ToolArg(description = "JSON array of case objects. Each case can have: "
          + "'temperature' (e.g. {\"value\": 25.0, \"unit\": \"C\"}), "
          + "'pressure' (e.g. {\"value\": 50.0, \"unit\": \"bara\"}), "
          + "'components' (override), 'flashType' (override). "
          + "Example: [{\"temperature\": {\"value\": 0, \"unit\": \"C\"}, "
          + "\"pressure\": {\"value\": 50, \"unit\": \"bara\"}}, "
          + "{\"temperature\": {\"value\": 25, \"unit\": \"C\"}, "
          + "\"pressure\": {\"value\": 50, \"unit\": \"bara\"}}]") String cases) {
    String blocked = IndustrialProfile.enforceAccess("runBatch");
    if (blocked != null) {
      return blocked;
    }
    try {
      com.google.gson.JsonObject json = new com.google.gson.JsonObject();
      json.add("components", com.google.gson.JsonParser.parseString(components));
      json.addProperty("model", eos);
      json.addProperty("flashType", flashType);
      json.add("cases", com.google.gson.JsonParser.parseString(cases));
      return BatchRunner.run(json.toString());
    } catch (Exception e) {
      return errorJson("Batch calculation failed: " + e.getMessage());
    }
  }

  private static String errorJson(String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", message);
    return error.toString();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // UniSim cooperation tools — cross-validation and parametric studies
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Cross-validate a process model across multiple thermodynamic models.
   *
   * @param crossValidationJson JSON specification with baseProcess, models, and compareVariables
   * @return JSON with per-model results, deviations, and risk flags
   */
  @Tool(description = "Cross-validate a process model by running it under multiple equations "
      + "of state (e.g. SRK, PR, CPA, GERG2008) and comparing key output variables. "
      + "Quantifies model-selection risk for UniSim-to-NeqSim conversions or any design "
      + "where EoS choice matters. Returns per-model values, spread, tolerance flags, "
      + "and an overall risk assessment with recommendations.")
  public String crossValidateModels(
      @ToolArg(description = "JSON specification with: 'baseProcess' (standard process JSON), "
          + "'models' (array of EoS names, e.g. [\"SRK\",\"PR\",\"CPA\",\"GERG2008\"]), "
          + "'compareVariables' (array of {address, unit} to track across models), and "
          + "optional 'tolerances' ({\"temperature\": 2.0, \"density\": 5.0, \"default\": 10.0} "
          + "as percent thresholds).") String crossValidationJson) {
    String blocked = IndustrialProfile.enforceAccess("crossValidateModels");
    if (blocked != null) {
      return blocked;
    }
    try {
      return CrossValidationRunner.crossValidate(crossValidationJson);
    } catch (Exception e) {
      return errorJson("Cross-validation failed: " + e.getMessage());
    }
  }

  /**
   * Run a parametric study sweeping input variables and recording outputs.
   *
   * @param studyJson JSON specification with baseProcess, sweeps, and outputs
   * @return JSON with all case results and summary statistics
   */
  @Tool(description = "Run a parametric study by sweeping one or more input variables and "
      + "recording output variables for each case. Supports full-factorial (all combinations) "
      + "and one-at-a-time (vary one while others at midpoint) modes. Ideal for license-free "
      + "optimization of models converted from UniSim — run hundreds of cases without "
      + "commercial license constraints. Max 5000 cases per study.")
  public String runParametricStudy(
      @ToolArg(description = "JSON specification with: 'baseProcess' (standard process JSON), "
          + "'sweeps' (array of sweep definitions with 'address', 'unit', and either "
          + "'values' array or 'from'/'to'/'steps' range), 'outputs' (array of {address, unit} "
          + "to extract from each case), and optional 'mode' ('one_at_a_time' or "
          + "'full_factorial', default one_at_a_time).") String studyJson) {
    String blocked = IndustrialProfile.enforceAccess("runParametricStudy");
    if (blocked != null) {
      return blocked;
    }
    try {
      return ParametricStudyRunner.run(studyJson);
    } catch (Exception e) {
      return errorJson("Parametric study failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PVT laboratory simulation tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Run a PVT laboratory experiment simulation.
   *
   * @param pvtJson JSON specification with fluid, experiment type, and conditions
   * @return JSON with PVT experiment results
   */
  @Tool(description = "Run a PVT laboratory experiment simulation on a fluid. "
      + "Supports: CME (constant mass expansion), CVD (constant volume depletion), "
      + "differentialLiberation, saturationPressure, saturationTemperature, "
      + "separatorTest, swellingTest, GOR (gas-oil ratio), and viscosity measurements. "
      + "Requires fluid composition, experiment type, and conditions.")
  public String runPVT(
      @ToolArg(description = "JSON specification with: 'components' (composition map), "
          + "'model' (SRK/PR/CPA), 'temperature_C' and 'pressure_bara' for the reservoir "
          + "conditions, 'experiment' (CME, CVD, differentialLiberation, saturationPressure, "
          + "saturationTemperature, separatorTest, swellingTest, GOR, viscosity), and "
          + "'experimentConfig' with experiment-specific parameters like 'pressures_bara' "
          + "array, separator stages, or injection gas composition.") String pvtJson) {
    String blocked = IndustrialProfile.enforceAccess("runPVT");
    if (blocked != null) {
      return blocked;
    }
    try {
      return withAutoValidation(PVTRunner.run(pvtJson), "general");
    } catch (Exception e) {
      return errorJson("PVT simulation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Flow assurance tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Run a flow assurance analysis on a fluid.
   *
   * @param flowAssuranceJson JSON specification with fluid and analysis type
   * @return JSON with flow assurance analysis results
   */
  @Tool(description = "Run a flow assurance analysis on a fluid mixture. "
      + "Supports: hydrateRiskMap (hydrate formation temperatures/pressures), "
      + "waxAppearance (WAT), asphalteneStability (onset pressure), "
      + "CO2Corrosion (corrosion rate), scalePrediction, erosion, "
      + "pipelineCooldown (temperature profile during shutdown), and "
      + "emulsionViscosity calculation. Essential for pipeline design and operation.")
  public String runFlowAssurance(
      @ToolArg(description = "JSON specification with: 'components' (composition map), "
          + "'model' (SRK/PR/CPA), 'temperature_C', 'pressure_bara', "
          + "'analysis' (hydrateRiskMap, waxAppearance, asphalteneStability, CO2Corrosion, "
          + "scalePrediction, erosion, pipelineCooldown, emulsionViscosity), and "
          + "'analysisConfig' with analysis-specific parameters.") String flowAssuranceJson) {
    String blocked = IndustrialProfile.enforceAccess("runFlowAssurance");
    if (blocked != null) {
      return blocked;
    }
    try {
      return withAutoValidation(FlowAssuranceRunner.run(flowAssuranceJson), "pipeline");
    } catch (Exception e) {
      return errorJson("Flow assurance analysis failed: " + e.getMessage());
    }
  }

  /**
   * Run a process-wide materials, degradation, and integrity review.
   *
   * @param materialsReviewJson JSON specification with optional process JSON and material register
   * @return JSON with materials review recommendations and integrity findings
   */
  @Tool(description = "Run a process-wide materials selection, corrosion/degradation, "
      + "and integrity lifetime review. Consumes optional runProcess-style processJson plus "
      + "materialsRegister/items or normalized stidData from technical databases. Evaluates "
      + "CO2 corrosion, sour service, chloride SCC, oxygen corrosion, dense CO2, hydrogen "
      + "service, ammonia compatibility, CUI, erosion-corrosion, MIC, galvanic corrosion, "
      + "fatigue/vibration, remaining life, and material recommendations.")
  public String runMaterialsReview(
      @ToolArg(description = "JSON with optional 'processJson' (same format as runProcess), "
          + "'materialsRegister' or 'items' array, optional 'stidData' normalized extract, "
          + "and optional 'projectName' and 'designLifeYears'.") String materialsReviewJson) {
    String blocked = IndustrialProfile.enforceAccess("runMaterialsReview");
    if (blocked != null) {
      return blocked;
    }
    try {
      return withAutoValidation(MaterialsReviewRunner.run(materialsReviewJson), "general");
    } catch (Exception e) {
      return errorJson("Materials review failed during evaluation.");
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Gas/oil quality standards tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Calculate gas or oil quality per industry standards.
   *
   * @param standardJson JSON specification with fluid and standard to apply
   * @return JSON with standard calculation results
   */
  @Tool(description = "Calculate gas or oil properties per industry standards. "
      + "Supports 22 standards: ISO 6976 (calorific value, Wobbe index), "
      + "ISO 12213 (compressibility), ISO 13443 (energy), ISO 18453 (cricondentherm), "
      + "ISO 14687 (hydrogen fuel), ISO 15112 (energy determination), "
      + "ISO 6578 (LNG custody), AGA 3 (flow measurement), AGA 7 (ultrasonic), "
      + "GPA 2145/2172 (physical constants), EN 16723/16726 (gas quality), "
      + "ASTM D86/D445/D2500/D4052/D4294/D6377/D97/BSW (oil testing). "
      + "Essential for custody transfer and sales gas specification compliance.")
  public String calculateStandard(
      @ToolArg(description = "JSON specification with: 'components' (composition map), "
          + "'model' (SRK/PR), 'temperature_C', 'pressure_bara', and "
          + "'standard' (ISO6976, ISO12213, AGA3, ASTM_D86, etc.). "
          + "Some standards require additional parameters in 'standardConfig'.") String standardJson) {
    try {
      return withAutoValidation(StandardsRunner.run(standardJson), "general");
    } catch (Exception e) {
      return errorJson("Standard calculation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Pipeline flow simulation tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Simulate multiphase pipeline flow using Beggs and Brill correlation.
   *
   * @param pipelineJson JSON specification with fluid, pipe geometry, and flow conditions
   * @return JSON with pressure drop, temperature profile, and flow regime
   */
  @Tool(description = "Simulate multiphase pipeline flow using the Beggs & Brill "
      + "correlation. Calculates pressure drop, outlet temperature, liquid holdup, "
      + "and flow regime for gas-liquid flow in pipes. Specify pipe geometry "
      + "(diameter, length, elevation, roughness) and flow conditions.")
  public String runPipeline(
      @ToolArg(description = "JSON specification with: 'components' (composition map), "
          + "'model' (SRK/PR), 'temperature_C', 'pressure_bara', "
          + "'flowRate' ({value, unit}), 'pipe' ({diameter_m, length_m, "
          + "elevation_m, roughness_m, numberOfIncrements}).") String pipelineJson) {
    String blocked = IndustrialProfile.enforceAccess("runPipeline");
    if (blocked != null) {
      return blocked;
    }
    try {
      return withAutoValidation(PipelineRunner.run(pipelineJson), "pipeline");
    } catch (Exception e) {
      return errorJson("Pipeline simulation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Reservoir simulation tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Simulate a reservoir using material balance (tank model).
   *
   * @param reservoirJson JSON specification with fluid, reservoir volumes, and producers
   * @return JSON with reservoir pressure decline and production data
   */
  @Tool(description = "Simulate a reservoir using material balance (tank model). "
      + "Creates a SimpleReservoir with gas/oil/water volumes, adds producer and "
      + "injector wells, and optionally runs transient depletion over multiple years. "
      + "Returns reservoir pressure, volumes in place, and cumulative production. "
      + "Ideal for resource estimation and production forecasting.")
  public String runReservoir(
      @ToolArg(description = "JSON specification with: 'components' (composition map), "
          + "'model' (SRK/PR), 'reservoirTemperature_C', 'reservoirPressure_bara', "
          + "'gasVolume_Sm3', 'oilVolume_Sm3', 'waterVolume_Sm3', "
          + "'producers' (array of {name, flowRate: {value, unit}}), "
          + "'simulationYears' (optional), 'timeStepDays' (optional).") String reservoirJson) {
    String blocked = IndustrialProfile.enforceAccess("runReservoir");
    if (blocked != null) {
      return blocked;
    }
    try {
      return ReservoirRunner.run(reservoirJson);
    } catch (Exception e) {
      return errorJson("Reservoir simulation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Field development economics tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Run field development economics (NPV, IRR, cash flow analysis).
   *
   * @param economicsJson JSON specification with CAPEX, OPEX, production, prices, and fiscal regime
   * @return JSON with NPV, IRR, payback, and annual cash flows
   */
  @Tool(description = "Run field development economics analysis. Calculates NPV, IRR, "
      + "payback period, and annual cash flows with detailed tax breakdown. "
      + "Supports multiple fiscal regimes: Norwegian NCS (78% marginal rate with "
      + "uplift/depreciation), UK (40% ring-fence + 35% supplementary), Brazil, "
      + "US-GOM. Also generates production profiles with exponential/hyperbolic/"
      + "harmonic decline curves. Two modes: 'cashflow' (full NPV/IRR) or "
      + "'productionProfile' (decline curve generation).")
  public String runFieldEconomics(
      @ToolArg(description = "JSON specification with 'mode' ('cashflow' or 'productionProfile'). "
          + "For cashflow: 'country' (NO/UK/BR/US-GOM), 'capex' ({totalMusd, year} or "
          + "{schedule: {year: musd}}), 'opex' ({percentOfCapex, fixedPerYearMusd, variablePerBoe}), "
          + "'oilPrice_usdPerBbl', 'gasPrice_usdPerSm3', 'production' ({oil: {year: bbl}, "
          + "gas: {year: sm3}}), 'discountRate'. For productionProfile: 'declineType' "
          + "(EXPONENTIAL/HYPERBOLIC/HARMONIC), 'initialRate_bblPerDay', 'annualDeclineRate', "
          + "'startYear', 'totalYears', 'plateauYears' (optional).") String economicsJson) {
    String blocked = IndustrialProfile.enforceAccess("runFieldEconomics");
    if (blocked != null) {
      return blocked;
    }
    try {
      return FieldDevelopmentRunner.run(economicsJson);
    } catch (Exception e) {
      return errorJson("Field economics calculation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Dynamic simulation tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Run a dynamic (transient) process simulation with controllers.
   *
   * @param dynamicJson JSON specification with process, duration, and optional tuning
   * @return JSON with time-series results from all transmitters
   */
  @Tool(description = "Run a dynamic (transient) process simulation. Takes a standard "
      + "process JSON, automatically instruments it with PID controllers and "
      + "measurement devices (pressure, level, temperature, flow transmitters), "
      + "then runs a transient simulation for the specified duration. "
      + "Returns time-series data from all transmitters. Use for startup/shutdown "
      + "analysis, controller tuning, and dynamic response studies.")
  public String runDynamic(
      @ToolArg(description = "JSON specification with: 'processJson' (standard process "
          + "definition), 'duration_seconds' (simulation length), 'timeStep_seconds' "
          + "(step size, default 1.0), and optional 'tuning' ({pressure: {kp, ti}, "
          + "level: {kp, ti}, flow: {kp, ti}, temperature: {kp, ti}}).") String dynamicJson) {
    String blocked = IndustrialProfile.enforceAccess("runDynamic");
    if (blocked != null) {
      return blocked;
    }
    try {
      return DynamicRunner.run(dynamicJson);
    } catch (Exception e) {
      return errorJson("Dynamic simulation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Bioprocessing tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Run a bioprocess reactor simulation.
   *
   * @param bioprocessJson JSON specification with reactor type and parameters
   * @return JSON with bioprocess results
   */
  @Tool(description = "Run a bioprocessing reactor simulation. Supports: "
      + "anaerobicDigester (biogas from organic waste — food waste, manure, sewage sludge), "
      + "fermentation (ethanol, biochemicals — Monod, Contois kinetics), "
      + "gasifier (thermochemical biomass gasification — downdraft, updraft, fluidized bed), "
      + "pyrolysis (thermal decomposition — slow, fast, flash modes producing char, "
      + "bio-oil, and gas). Each reactor returns product yields, energy balances, "
      + "and conversion efficiencies.")
  public String runBioprocess(
      @ToolArg(description = "JSON specification with: 'reactorType' (anaerobicDigester, "
          + "fermentation, gasifier, pyrolysis). For anaerobicDigester: 'substrateType' "
          + "(FOOD_WASTE, MANURE, SEWAGE_SLUDGE, etc.), 'feedRate_kgPerHr', "
          + "'totalSolidsFraction', 'temperature_C'. For fermentation: 'kineticModel' "
          + "(MONOD, CONTOIS), 'maxSpecificGrowthRate', 'yieldBiomass', 'yieldProduct'. "
          + "For gasifier: 'biomass' ({carbon, hydrogen, oxygen, nitrogen, sulfur, ash}), "
          + "'gasifierType' (DOWNDRAFT, UPDRAFT, FLUIDIZED_BED), 'agentType' (AIR, OXYGEN, STEAM). "
          + "For pyrolysis: 'biomass' (same), 'mode' (SLOW, FAST, FLASH), "
          + "'temperature_C'.") String bioprocessJson) {
    String blocked = IndustrialProfile.enforceAccess("runBioprocess");
    if (blocked != null) {
      return blocked;
    }
    try {
      return BioprocessRunner.run(bioprocessJson);
    } catch (Exception e) {
      return errorJson("Bioprocess simulation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Session management tools (stateful)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Manage a persistent simulation session for incremental flowsheet construction.
   *
   * @param sessionJson JSON with action and session parameters
   * @return JSON with session state or results
   */
  @Tool(description = "Manage a persistent simulation session. Enables incremental process "
      + "construction: create a session with a fluid, add equipment one-by-one, modify "
      + "parameters, and re-run — all without resending the entire JSON each time. "
      + "Sessions persist across multiple calls with automatic 30-minute TTL. "
      + "Actions: 'create' (new session with fluid), 'addEquipment' (add equipment to "
      + "session), 'run' (execute simulation), 'modify' (change a parameter and re-run), "
      + "'getState' (inspect session), 'list' (all sessions), 'close' (delete session).")
  public String manageSession(
      @ToolArg(description = "JSON with 'action' (create|addEquipment|run|modify|getState|"
          + "list|close). For create: 'fluid' (composition) or 'processJson' (full process). "
          + "For addEquipment: 'sessionId', 'equipment' ({type, name, inlet, properties}). "
          + "For modify: 'sessionId', 'address' (e.g. 'Compressor.outletPressure'), "
          + "'value', 'unit'. For run/getState/close: 'sessionId'.") String sessionJson) {
    String blocked = IndustrialProfile.enforceAccess("manageSession");
    if (blocked != null) {
      return blocked;
    }
    try {
      return SessionRunner.run(sessionJson);
    } catch (Exception e) {
      return errorJson("Session operation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Task solver and workflow composition
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Solve a high-level engineering task by automatic planning and execution.
   *
   * @param taskJson JSON with task description and parameters
   * @return JSON with execution plan, step results, validation, and report
   */
  @Tool(description = "[EXPERIMENTAL — Tier 3] Solve a complete engineering task. "
      + "Takes a high-level description "
      + "(e.g., 'Design a 3-stage compression system from 5 to 150 bara'), automatically "
      + "classifies the task, builds a multi-step execution plan, executes each step, "
      + "chains results between steps, runs engineering validation against industry rules, "
      + "and returns a structured report. Limited validation — results require independent "
      + "review. Not available in STUDY_TEAM, DIGITAL_TWIN, or ENTERPRISE modes.")
  public String solveTask(
      @ToolArg(description = "JSON with: 'task' (natural language description), "
          + "'fluid' (composition), 'parameters' (task-specific values like outletPressure, "
          + "stages, intercoolerTemp), optional 'process' (equipment definitions), "
          + "optional 'validate' (true/false, default true).") String taskJson) {
    String blocked = IndustrialProfile.enforceAccess("solveTask");
    if (blocked != null) {
      return blocked;
    }
    try {
      return TaskSolverRunner.solveTask(taskJson);
    } catch (Exception e) {
      return errorJson("Task solving failed: " + e.getMessage());
    }
  }

  /**
   * Compose a multi-domain workflow by chaining runners in sequence.
   *
   * @param workflowJson JSON with workflow steps
   * @return JSON with all step results and combined output
   */
  @Tool(description = "Compose a multi-domain workflow by chaining simulation steps. "
      + "Define a sequence of runners (flash, process, pipeline, pvt, flow_assurance, "
      + "reservoir, economics, dynamic, standards, bioprocess) and chain them together — "
      + "results from each step flow to the next. Example: Reservoir → Process → "
      + "Pipeline → Economics for a full field development evaluation.")
  public String composeWorkflow(
      @ToolArg(description = "JSON with: 'workflow' (name), 'fluid' (shared fluid), "
          + "'steps' array of {runner, name, input} objects. Runners: flash, process, "
          + "pipeline, pvt, flow_assurance, reservoir, economics, dynamic, standards, "
          + "bioprocess. Each step's output is available to subsequent steps.") String workflowJson) {
    String blocked = IndustrialProfile.enforceAccess("composeWorkflow");
    if (blocked != null) {
      return blocked;
    }
    try {
      return TaskSolverRunner.composeWorkflow(workflowJson);
    } catch (Exception e) {
      return errorJson("Workflow composition failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Engineering validation
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Validate simulation results against engineering design rules and industry standards.
   *
   * @param resultsJson JSON with simulation results
   * @param context the validation context
   * @return JSON with validation findings
   */
  @Tool(description = "Validate simulation results against engineering design rules. "
      + "Checks: temperature/pressure physical limits, compressor efficiency (75-88%) and "
      + "compression ratio (<4.5 per stage per API 617), separator residence time "
      + "(>60s per NORSOK P-001), heat exchanger approach temperature (>3C per TEMA), "
      + "pipeline erosional velocity (<25 m/s per API RP 14E), mass/energy balance closure, "
      + "convergence status, hydrate risk, and material selection limits. "
      + "Returns PASS / PASS_WITH_WARNINGS / FAIL verdict with remediation hints.")
  public String validateResults(
      @ToolArg(description = "JSON with simulation results to validate. Can be output "
          + "from any runner (flash, process, pipeline, etc.).") String resultsJson,
      @ToolArg(description = "Validation context: 'process', 'compressor', 'separator', "
          + "'heatExchanger', 'pipeline', 'valve', or 'general'.") String context) {
    try {
      return EngineeringValidator.validate(resultsJson, context);
    } catch (Exception e) {
      return errorJson("Validation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Report generation
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Generate a structured engineering report from simulation results.
   *
   * @param reportJson JSON with report type, title, and data
   * @return JSON with Markdown report, tables, chart data, and validation
   */
  @Tool(description = "Generate a structured engineering report from simulation results. "
      + "Produces a professional Markdown report with tables, chart-ready data arrays "
      + "(for plotting by AI agents), summary statistics, and optional engineering "
      + "validation. Report types: process_summary, pvt_study, parametric_sweep, "
      + "flow_assurance, equipment_design, custom.")
  public String generateReport(
      @ToolArg(description = "JSON with: 'reportType' (process_summary|pvt_study|"
          + "parametric_sweep|flow_assurance|equipment_design|custom), 'title' (report "
          + "title), 'data' (simulation results to report on), optional 'author', "
          + "'includeValidation' (true/false), 'includeChartData' (true/false).") String reportJson) {
    String blocked = IndustrialProfile.enforceAccess("generateReport");
    if (blocked != null) {
      return blocked;
    }
    try {
      return ReportRunner.run(reportJson);
    } catch (Exception e) {
      return errorJson("Report generation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Task workflow bridge (task_solve integration)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Convert MCP tool output to the task_solve results.json format.
   *
   * @param bridgeJson JSON with tool output and metadata
   * @return JSON in task_solve results.json schema
   */
  @Tool(description = "Convert any MCP tool output into the task_solve results.json format "
      + "for professional engineering report generation. Takes raw output from any tool "
      + "(runFlash, runProcess, runPVT, runPipeline, calculateStandard, runFieldEconomics, etc.), "
      + "extracts key results, validation status, and produces the results.json schema "
      + "consumed by generate_report.py. Use action 'getSchema' for the full schema reference. "
      + "This bridges MCP simulations to the NeqSim task-solving workflow that produces "
      + "Word/HTML engineering reports.")
  public String bridgeTaskWorkflow(
      @ToolArg(description = "JSON with: 'action' ('toResultsJson' or 'getSchema'). "
          + "For toResultsJson: 'toolOutput' (raw output from any MCP tool), "
          + "'sourceRunner' (tool name, e.g. 'runFlash'), optional 'taskTitle', "
          + "'approach' (methodology description), 'conclusions'.") String bridgeJson) {
    String blocked = IndustrialProfile.enforceAccess("bridgeTaskWorkflow");
    if (blocked != null) {
      return blocked;
    }
    try {
      return TaskWorkflowBridge.run(bridgeJson);
    } catch (Exception e) {
      return errorJson("Task workflow bridge failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Plugin system
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Run a registered plugin by name, or list all available plugins.
   *
   * @param pluginJson JSON with plugin name and input
   * @return JSON with plugin output or plugin list
   */
  @Tool(description = "Run a registered plugin or list available plugins. "
      + "Plugins extend NeqSim MCP with domain-specific calculations. "
      + "Use action 'list' to discover available plugins, or 'run' to execute one.")
  public String runPlugin(
      @ToolArg(description = "JSON with: 'action' ('list' or 'run'). For 'run': "
          + "'pluginName' (registered plugin name), 'input' (plugin-specific JSON). "
          + "For 'list': no additional fields needed.") String pluginJson) {
    String blocked = IndustrialProfile.enforceAccess("runPlugin");
    if (blocked != null) {
      return blocked;
    }
    try {
      JsonObject input = JsonParser.parseString(pluginJson).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "list";

      if ("list".equals(action)) {
        return PluginRegistry.listPlugins();
      } else if ("run".equals(action)) {
        String pluginName = input.has("pluginName") ? input.get("pluginName").getAsString() : "";
        String pluginInput = input.has("input") ? input.get("input").toString() : "{}";
        return PluginRegistry.runPlugin(pluginName, pluginInput);
      } else {
        return errorJson("Unknown plugin action: " + action + ". Use 'list' or 'run'.");
      }
    } catch (Exception e) {
      return errorJson("Plugin operation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Progress tracking
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Check progress of long-running simulations.
   *
   * @param progressJson JSON with operation ID or action
   * @return JSON with progress details
   */
  @Tool(description = "Check progress of long-running simulations. "
      + "Use 'listActive' to see all running operations, or provide an 'operationId' "
      + "to get detailed progress (percentage, current step, milestones).")
  public String getProgress(
      @ToolArg(description = "JSON with: 'action' ('get' or 'listActive'). For 'get': "
          + "'operationId' (ID returned when starting a long simulation).") String progressJson) {
    try {
      JsonObject input = JsonParser.parseString(progressJson).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "listActive";

      if ("listActive".equals(action)) {
        return ProgressTracker.listActive();
      } else if ("get".equals(action) && input.has("operationId")) {
        return ProgressTracker.getProgress(input.get("operationId").getAsString());
      } else {
        return ProgressTracker.listActive();
      }
    } catch (Exception e) {
      return errorJson("Progress query failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Streaming simulations
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Run long-running simulations with streaming/polling results.
   *
   * @param streamJson JSON with streaming action and parameters
   * @return JSON with operation ID or polled results
   */
  @Tool(description = "Run simulations with incremental streaming results. "
      + "Starts async operations (parametric sweeps, dynamic sims, Monte Carlo) "
      + "and polls for new results as they become available. "
      + "Actions: startParametricSweep, startDynamicStreaming, startMonteCarlo, "
      + "pollResults, cancelOperation, listOperations.")
  public String streamSimulation(
      @ToolArg(description = "JSON with: 'action' (startParametricSweep|startDynamicStreaming|"
          + "startMonteCarlo|pollResults|cancelOperation|listOperations). "
          + "For start actions: simulation parameters. "
          + "For pollResults: 'operationId' and 'lastIndex'.") String streamJson) {
    String blocked = IndustrialProfile.enforceAccess("streamSimulation");
    if (blocked != null) {
      return blocked;
    }
    try {
      return StreamingRunner.run(streamJson);
    } catch (Exception e) {
      return errorJson("Streaming operation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Visualization
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Generate inline visualizations (SVG charts, diagrams, styled tables).
   *
   * @param vizJson JSON with visualization type and data
   * @return JSON with SVG/Mermaid/HTML content
   */
  @Tool(description = "Generate inline visualizations for simulation results. "
      + "Produces SVG charts (phase envelopes, compressor maps, bar charts), "
      + "Mermaid flowsheet diagrams, and styled HTML tables. "
      + "Types: phaseEnvelope, flowsheetDiagram, compressorMap, barChart, styledTable.")
  public String generateVisualization(
      @ToolArg(description = "JSON with: 'type' (phaseEnvelope|flowsheetDiagram|compressorMap|"
          + "barChart|styledTable). For phaseEnvelope: fluid components. "
          + "For flowsheetDiagram: processJson. For barChart: labels, values. "
          + "For styledTable: headers, rows, caption.") String vizJson) {
    String blocked = IndustrialProfile.enforceAccess("generateVisualization");
    if (blocked != null) {
      return blocked;
    }
    try {
      return VisualizationRunner.run(vizJson);
    } catch (Exception e) {
      return errorJson("Visualization failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Multi-server composition
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Compose multi-server engineering workflows.
   *
   * @param compositionJson JSON with composition action and parameters
   * @return JSON with workflow plan or server info
   */
  @Tool(description = "Compose multi-server engineering workflows across MCP servers. "
      + "Browse external servers (cost estimation, plant historian, CAD, safety), "
      + "plan cross-domain workflows (digital-twin, feed study, vendor evaluation), "
      + "and describe NeqSim capabilities. "
      + "Actions: listServers, registerServer, removeServer, listWorkflows, "
      + "getWorkflow, planComposition, describeCapabilities.")
  public String composeMultiServerWorkflow(
      @ToolArg(description = "JSON with: 'action' (listServers|registerServer|removeServer|"
          + "listWorkflows|getWorkflow|planComposition|describeCapabilities). "
          + "For planComposition: 'task' (natural language description). "
          + "For getWorkflow: 'workflowId' (digital-twin|feed-study|vendor-evaluation|"
          + "safety-study).") String compositionJson) {
    String blocked = IndustrialProfile.enforceAccess("composeMultiServerWorkflow");
    if (blocked != null) {
      return blocked;
    }
    try {
      return CompositionRunner.run(compositionJson);
    } catch (Exception e) {
      return errorJson("Composition failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Security & audit
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Manage API keys, audit logging, rate limiting, and security configuration.
   *
   * @param securityJson JSON with security action and parameters
   * @return JSON with security status or audit entries
   */
  @Tool(description = "Manage MCP server security: API key management, audit logging, "
      + "rate limiting, and access control configuration. "
      + "Actions: createApiKey, revokeApiKey, authenticate, getAuditLog, "
      + "getRateLimits, setConfig, getStatus.")
  public String manageSecurity(
      @ToolArg(description = "JSON with: 'action' (createApiKey|revokeApiKey|authenticate|"
          + "getAuditLog|getRateLimits|setConfig|getStatus). "
          + "For createApiKey: 'userId', 'project', 'role', 'rateLimit'. "
          + "For getAuditLog: optional 'userId', 'tool', 'limit' filters.") String securityJson) {
    String blocked = IndustrialProfile.enforceAccess("manageSecurity");
    if (blocked != null) {
      return blocked;
    }
    try {
      return SecurityRunner.run(securityJson);
    } catch (Exception e) {
      return errorJson("Security operation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // State persistence
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Save, load, compare, and export simulation states.
   *
   * @param persistJson JSON with persistence action and parameters
   * @return JSON with save confirmation, loaded state, or comparison
   */
  @Tool(description = "Persist simulation states across server restarts. "
      + "Save sessions to versioned JSON files, load saved states to create new sessions, "
      + "compare versions, and export for sharing. "
      + "Actions: save, load, list, delete, compare, export, setStorageDir, getInfo.")
  public String manageState(
      @ToolArg(description = "JSON with: 'action' (save|load|list|delete|compare|export|"
          + "setStorageDir|getInfo). " + "For save: 'sessionId', 'name', 'version', 'description'. "
          + "For load: 'filename' or 'filePath'. "
          + "For compare: 'file1', 'file2'.") String persistJson) {
    String blocked = IndustrialProfile.enforceAccess("manageState");
    if (blocked != null) {
      return blocked;
    }
    try {
      return StatePersistenceRunner.run(persistJson);
    } catch (Exception e) {
      return errorJson("State persistence failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Validation profiles
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Manage domain-specific validation profiles for different jurisdictions.
   *
   * @param profileJson JSON with profile action and parameters
   * @return JSON with profile details or validation results
   */
  @Tool(description = "Manage domain-specific validation profiles for different jurisdictions. "
      + "Built-in profiles: ncs (Norway), ukcs (UK), gom (Gulf of Mexico), brazil, generic. "
      + "Create custom profiles with operator-specific overrides. "
      + "Actions: listProfiles, getProfile, setActiveProfile, createProfile, "
      + "deleteProfile, validateWithProfile, getActiveProfile, getStandardsForEquipment.")
  public String manageValidationProfile(
      @ToolArg(description = "JSON with: 'action' (listProfiles|getProfile|setActiveProfile|"
          + "createProfile|deleteProfile|validateWithProfile|getActiveProfile|"
          + "getStandardsForEquipment). " + "For setActiveProfile: 'profileName'. "
          + "For createProfile: 'profileName', optional 'basedOn', 'overrides'. "
          + "For getStandardsForEquipment: 'equipmentType'.") String profileJson) {
    String blocked = IndustrialProfile.enforceAccess("manageValidationProfile");
    if (blocked != null) {
      return blocked;
    }
    try {
      return ValidationProfileRunner.run(profileJson);
    } catch (Exception e) {
      return errorJson("Validation profile operation failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Data catalog
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Browse the NeqSim data catalog — components, EOS models, materials, standards.
   *
   * @param catalogJson JSON with catalog query
   * @return JSON with catalog data
   */
  @Tool(description = "Browse the NeqSim data catalog. Query thermodynamic component properties, "
      + "equation-of-state models, pipe/plate/casing materials, design standards, and database "
      + "tables. Actions: listComponentFamilies, getComponentProperties, listEOSModels, "
      + "listMaterials, listDesignStandards, queryStandard, listDataTables.")
  public String queryDataCatalog(
      @ToolArg(description = "JSON with: 'action' (listComponentFamilies|getComponentProperties|"
          + "listEOSModels|listMaterials|listDesignStandards|queryStandard|listDataTables). "
          + "For getComponentProperties: 'componentName'. "
          + "For listMaterials: 'materialType' (pipe|plate|casing|compressor|heatExchanger). "
          + "For queryStandard: 'code', optional 'equipmentType'.") String catalogJson) {
    String blocked = IndustrialProfile.enforceAccess("queryDataCatalog");
    if (blocked != null) {
      return blocked;
    }
    try {
      return DataCatalogRunner.run(catalogJson);
    } catch (Exception e) {
      return errorJson("Data catalog query failed: " + e.getMessage());
    }
  }

  /**
   * Perform quick equipment sizing for separators and compressors.
   *
   * @param sizingJson JSON with equipmentType, fluid, and sizing parameters
   * @return JSON string with sizing results
   */
  @Tool(description = "Perform quick equipment sizing for separators and compressors. "
      + "For separators: calculates vessel diameter and length using Souders-Brown approach. "
      + "For compressors: calculates power, outlet temperature, and recommended stages. "
      + "Use getExample with category 'equipment-sizing' for templates.")
  public String sizeEquipment(
      @ToolArg(description = "JSON with: 'equipmentType' (separator|compressor), "
          + "'model', 'temperature_C', 'pressure_bara', 'components', 'flowRate'. "
          + "For separator: 'orientation', 'liquidRetentionTime_min'. "
          + "For compressor: 'outletPressure_bara', 'polytropicEfficiency'.") String sizingJson) {
    String blocked = IndustrialProfile.enforceAccess("sizeEquipment");
    if (blocked != null) {
      return blocked;
    }
    try {
      return EquipmentSizingRunner.run(sizingJson);
    } catch (Exception e) {
      return errorJson("Equipment sizing failed: " + e.getMessage());
    }
  }

  /**
   * Compare two or more process configurations side by side.
   *
   * @param comparisonJson JSON with cases array
   * @return JSON string with comparison results
   */
  @Tool(description = "Compare two or more process configurations side by side. "
      + "Run multiple process cases and get a comparison table of key outputs "
      + "(temperatures, pressures, duties, compositions). "
      + "Use getExample with category 'comparison' for templates.")
  public String compareProcesses(
      @ToolArg(description = "JSON with 'cases' array. Each case has 'name', 'fluid', "
          + "and 'process' (same format as runProcess). Minimum 2 cases.") String comparisonJson) {
    String blocked = IndustrialProfile.enforceAccess("compareProcesses");
    if (blocked != null) {
      return blocked;
    }
    try {
      return ProcessComparisonRunner.run(comparisonJson);
    } catch (Exception e) {
      return errorJson("Process comparison failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Process safety tools (API 520/521, IEC 61508/61511, ISO 31000)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Size a Pressure Safety Valve (PSV) per API 520 / API 521.
   *
   * @param reliefJson JSON spec with relief case and inputs
   * @return JSON string with sizing result
   */
  @Tool(description = "Size a Pressure Safety Valve (PSV) per API 520 / API 521. "
      + "Supports four cases: 'gas' (vapour service), 'liquid' (liquid relief), "
      + "'twoPhase' (Leung omega method, Appendix D), and 'fireHeatInput' (API 521 "
      + "wetted-area fire heat absorption). Returns required orifice area, recommended "
      + "API standard orifice letter (D-T), correction factors (Kd/Kb/Kc/Kw/Kv), and "
      + "validation warnings.")
  public String runRelief(
      @ToolArg(description = "JSON with: 'case' (gas|liquid|twoPhase|fireHeatInput). "
          + "For gas: 'massFlowRate_kg_s', 'setPressure_bara', 'temperature_K', "
          + "'molecularWeight_kg_mol', optional 'overpressureFraction' (default 0.21), "
          + "'backPressure_bara', 'compressibility', 'specificHeatRatio', "
          + "'balancedBellows', 'ruptureDisk'. "
          + "For liquid: 'volumeFlowRate_m3_s', 'liquidDensity_kg_m3', 'setPressure_bara', "
          + "optional 'viscosity_Pa_s'. "
          + "For twoPhase: 'massFlowRate_kg_s', 'gasMassFraction', 'gasDensity_kg_m3', "
          + "'liquidDensity_kg_m3', 'latentHeat_J_kg', 'liquidCp_J_kgK', 'temperature_K'. "
          + "For fireHeatInput: 'wettedArea_m2', 'hasDrainage', 'hasFireFighting'.") String reliefJson) {
    String blocked = IndustrialProfile.enforceAccess("runRelief");
    if (blocked != null) {
      return blocked;
    }
    try {
      return ReliefRunner.run(reliefJson);
    } catch (Exception e) {
      return errorJson("Relief sizing failed: " + e.getMessage());
    }
  }

  /**
   * Run a Layer of Protection Analysis (LOPA) per IEC 61511 / CCPS LOPA.
   *
   * @param lopaJson JSON spec with scenario, frequencies, and layers
   * @return JSON string with LOPA result and gap analysis
   */
  @Tool(description = "Run a Layer of Protection Analysis (LOPA) per IEC 61511 / CCPS LOPA. "
      + "Computes the mitigated event frequency by stacking PFDs of independent "
      + "protection layers (BPCS, alarms, relief valves, SIFs), compares against a target, "
      + "and reports the gap, total RRF, and required additional SIL/PFD if the target "
      + "is not met.")
  public String runLOPA(
      @ToolArg(description = "JSON with: 'scenario' (name), 'initiatingEventFrequency_per_year', "
          + "'targetFrequency_per_year', and 'layers' array. Each layer has 'name' and 'pfd' "
          + "(probability of failure on demand, 0-1). Example: "
          + "{\"scenario\":\"HP separator overpressure\",\"initiatingEventFrequency_per_year\":0.1,"
          + "\"targetFrequency_per_year\":1e-5,\"layers\":[{\"name\":\"BPCS\",\"pfd\":0.1},"
          + "{\"name\":\"PSV\",\"pfd\":0.01}]}") String lopaJson) {
    String blocked = IndustrialProfile.enforceAccess("runLOPA");
    if (blocked != null) {
      return blocked;
    }
    try {
      return LOPARunner.run(lopaJson);
    } catch (Exception e) {
      return errorJson("LOPA calculation failed: " + e.getMessage());
    }
  }

  /**
   * Verify a Safety Instrumented Function (SIF) against its claimed SIL per IEC 61508/61511.
   *
   * @param silJson JSON spec with SIF metadata and component reliability data
   * @return JSON string with SIL verification result
   */
  @Tool(description = "Verify a Safety Instrumented Function (SIF) against its claimed SIL "
      + "per IEC 61508 / IEC 61511. Computes PFDavg from component-level failure rates "
      + "(sensors, logic solver, final elements) using simplified architecture formulae "
      + "(1oo1, 1oo2, 2oo3), determines achieved SIL, hardware fault tolerance, and "
      + "verification issues.")
  public String runSIL(
      @ToolArg(description = "JSON with: 'name', 'claimedSIL' (1-4), 'architecture' "
          + "(1oo1|1oo2|2oo3), 'proofTestInterval_hours', and EITHER 'pfdAvg' (direct) OR "
          + "'components' array with each component having 'name', 'type' (sensor|logic|finalElement), "
          + "and either 'pfd' or 'lambdaDU_per_hr' (dangerous undetected failure rate).") String silJson) {
    String blocked = IndustrialProfile.enforceAccess("runSIL");
    if (blocked != null) {
      return blocked;
    }
    try {
      return SILRunner.run(silJson);
    } catch (Exception e) {
      return errorJson("SIL verification failed: " + e.getMessage());
    }
  }

  /**
   * Score risk events on a 5x5 matrix per ISO 31000 / NORSOK Z-013.
   *
   * @param riskJson JSON spec with risk events
   * @return JSON string with scored matrix
   */
  @Tool(description = "Score risk events on a 5x5 matrix per ISO 31000 / NORSOK Z-013. "
      + "Each event is categorised by probability (1-5) and consequence (1-5), either "
      + "directly or from frequency (failures/year) and production loss (%). "
      + "Returns risk score, level (LOW/MEDIUM/HIGH/CRITICAL) and colour for each event "
      + "plus the overall worst-case.")
  public String runRiskMatrix(
      @ToolArg(description = "JSON with: 'events' array. Each event has 'name' and EITHER "
          + "('probabilityLevel' 1-5 + 'consequenceLevel' 1-5) OR "
          + "('failuresPerYear' + 'productionLossPercent'), plus optional 'mitigation'.") String riskJson) {
    String blocked = IndustrialProfile.enforceAccess("runRiskMatrix");
    if (blocked != null) {
      return blocked;
    }
    try {
      return RiskMatrixRunner.run(riskJson);
    } catch (Exception e) {
      return errorJson("Risk matrix scoring failed: " + e.getMessage());
    }
  }

  /**
   * Compute flare-tip thermal radiation per API 521 §6 / API 537.
   *
   * @param flareJson JSON spec with heat duty and radiation parameters
   * @return JSON string with radiation profile and safe-distance contour
   */
  @Tool(description = "Compute flare-tip thermal radiation per API 521 §6 / API 537. "
      + "Calculates the radiant heat flux at user-specified ground distances and the "
      + "safe ground distance to API 521 thresholds (1.58, 4.73, 6.31, 9.46 kW/m²) "
      + "used for personnel exposure and equipment limits.")
  public String runFlareNetwork(
      @ToolArg(description = "JSON with: 'heatDuty_MW' (or 'heatDuty_W'), optional "
          + "'flameHeight_m' (default 30), 'radiantFraction' (default 0.18), and "
          + "'distances_m' array (default 15-200 m grid).") String flareJson) {
    String blocked = IndustrialProfile.enforceAccess("runFlareNetwork");
    if (blocked != null) {
      return blocked;
    }
    try {
      return FlareRadiationRunner.run(flareJson);
    } catch (Exception e) {
      return errorJson("Flare radiation calculation failed: " + e.getMessage());
    }
  }

  /**
   * Generate a simulation-backed HAZOP worksheet from a process definition and optional document
   * extraction context.
   *
   * @param hazopJson JSON spec with process definition, nodes, failure modes, and optional barrier
   *        register
   * @return JSON string with HAZOP rows, scenario simulations, and report markdown
   */
  @Tool(description = "Generate a simulation-backed HAZOP worksheet from a NeqSim process "
      + "definition. Accepts STID/P&ID-extracted nodes, safeguards, evidence references, "
      + "failure modes, and an optional barrier register. Runs generated safety scenarios "
      + "against copied ProcessSystem models and returns IEC 61882 rows, simulation evidence, "
      + "quality gates, barrier handoff, and report markdown.")
  public String runHAZOP(
      @ToolArg(description = "JSON with 'processDefinition' (standard runProcess JSON), optional "
          + "'nodes' array with nodeId/designIntent/equipment/safeguards/evidenceRefs, optional "
          + "'failureModes' array (e.g. COOLING_LOSS, VALVE_STUCK_CLOSED), optional "
          + "'barrierRegister', and 'runSimulations' boolean. Use getExample with category "
          + "'safety' and name 'hazop-study' for a template.") String hazopJson) {
    String blocked = IndustrialProfile.enforceAccess("runHAZOP");
    if (blocked != null) {
      return blocked;
    }
    try {
      return HAZOPStudyRunner.run(hazopJson);
    } catch (Exception e) {
      return errorJson("HAZOP study generation failed: " + e.getMessage());
    }
  }

  /**
   * Validate and transform an evidence-linked barrier register.
   *
   * @param barrierJson JSON spec with evidence, performance standards, barriers, and SCEs
   * @return JSON string with validation findings and safety-analysis handoffs
   */
  @Tool(description = "Validate and transform an evidence-linked safety barrier register. "
      + "Accepts extracted document evidence, performance standards, safety barriers, "
      + "and safety critical elements (SCEs). Returns validation findings plus handoff "
      + "blocks for LOPA, SIL verification, bow-tie analysis, and QRA screening.")
  public String runBarrierRegister(
      @ToolArg(description = "JSON with 'register' containing registerId, evidence, "
          + "performanceStandards, barriers, and safetyCriticalElements. Use getExample "
          + "with category 'safety' and name 'barrier-register' for a template.") String barrierJson) {
    String blocked = IndustrialProfile.enforceAccess("runBarrierRegister");
    if (blocked != null) {
      return blocked;
    }
    try {
      return BarrierRegisterRunner.run(barrierJson);
    } catch (Exception e) {
      return errorJson("Barrier register analysis failed: " + e.getMessage());
    }
  }

  /**
   * Analyze active and passive safety-system barrier performance from STID and NeqSim evidence.
   *
   * @param safetySystemJson JSON with register, demands, detector data, and optional SIF data
   * @return JSON string with safety-system performance report
   */
  @Tool(description = "Analyze active and passive safety-system barrier performance from STID, "
      + "C&E, SRS, firewater, detector-layout, PFP, and optional quantitative SIL/PFD data. "
      + "Returns a SafetySystemPerformanceReport plus NORSOK S-001, ISO 13702, and "
      + "TR1055-style performance-standard templates.")
  public String runSafetySystemPerformance(
      @ToolArg(description = "JSON with 'register' (or barrierRegister), optional 'demands', "
          + "'measurementDevices', 'logicSifs', and 'quantitativeSifs'. Use getExample with "
          + "category 'safety' and name 'safety-system-performance' for a template.") String safetySystemJson) {
    String blocked = IndustrialProfile.enforceAccess("runSafetySystemPerformance");
    if (blocked != null) {
      return blocked;
    }
    try {
      return SafetySystemPerformanceRunner.run(safetySystemJson);
    } catch (Exception e) {
      return errorJson("Safety-system performance analysis failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Industrial governance & trust tools
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * List deployment profiles and manage the active industrial mode.
   *
   * <p>
   * The industrial profile system controls which tools are exposed, whether human-approval gates
   * are required, and which validation level is enforced. Four profiles cover the range from
   * full-access desktop engineering to restricted enterprise deployment.
   * </p>
   *
   * @param profileJson JSON with action and optional parameters
   * @return JSON with profile information or confirmation
   */
  @Tool(description = "Manage industrial deployment profiles that control tool access, "
      + "validation enforcement, and approval gates. "
      + "Profiles: DESKTOP_ENGINEER (all tools), STUDY_TEAM (collaborative), "
      + "DIGITAL_TWIN (read-heavy advisory), ENTERPRISE (restricted industrial core). "
      + "Actions: describe (list all profiles), getActive (current mode), "
      + "setActive (change mode), classifyTool (check a tool's category).")
  public String manageIndustrialProfile(
      @ToolArg(description = "JSON with: 'action' (describe|getActive|setActive|classifyTool). "
          + "For setActive: 'mode' (DESKTOP_ENGINEER|STUDY_TEAM|DIGITAL_TWIN|ENTERPRISE). "
          + "For classifyTool: 'toolName' (name of tool to classify).") String profileJson) {
    try {
      JsonObject input = JsonParser.parseString(profileJson).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "describe";

      switch (action) {
        case "describe":
          return IndustrialProfile.describeProfiles();
        case "getActive": {
          JsonObject result = new JsonObject();
          result.addProperty("status", "success");
          result.addProperty("activeMode", IndustrialProfile.getActiveMode().name());
          result.addProperty("autoValidation", IndustrialProfile.isAutoValidationEnabled());
          return GSON_PRETTY.toJson(result);
        }
        case "setActive": {
          String modeName = input.has("mode") ? input.get("mode").getAsString() : "";
          try {
            IndustrialProfile.DeploymentMode mode =
                IndustrialProfile.DeploymentMode.valueOf(modeName);
            IndustrialProfile.setActiveMode(mode);
            JsonObject result = new JsonObject();
            result.addProperty("status", "success");
            result.addProperty("activeMode", mode.name());
            result.addProperty("message",
                "Deployment mode set to " + mode.name() + ". Tool access updated.");
            return GSON_PRETTY.toJson(result);
          } catch (IllegalArgumentException e) {
            return errorJson("Invalid mode: " + modeName
                + ". Use DESKTOP_ENGINEER, STUDY_TEAM, DIGITAL_TWIN, or ENTERPRISE.");
          }
        }
        case "classifyTool": {
          String toolName = input.has("toolName") ? input.get("toolName").getAsString() : "";
          IndustrialProfile.ToolCategory cat = IndustrialProfile.getToolCategory(toolName);
          JsonObject result = new JsonObject();
          result.addProperty("status", "success");
          result.addProperty("tool", toolName);
          result.addProperty("category", cat != null ? cat.name() : "UNKNOWN");
          result.addProperty("allowed", IndustrialProfile.isToolAllowed(toolName));
          result.addProperty("requiresApproval", IndustrialProfile.requiresApproval(toolName));
          result.addProperty("inIndustrialCore",
              IndustrialProfile.getIndustrialCore().contains(toolName));
          return GSON_PRETTY.toJson(result);
        }
        default:
          return errorJson("Unknown action: " + action
              + ". Use describe, getActive, setActive, or classifyTool.");
      }
    } catch (Exception e) {
      return errorJson("Industrial profile operation failed: " + e.getMessage());
    }
  }

  /**
   * Get benchmark trust metadata for tools — validation cases, accuracy bounds, known limitations,
   * and maturity levels.
   *
   * <p>
   * Industrial users should review this before relying on results for design decisions or
   * safety-critical applications.
   * </p>
   *
   * @param trustJson JSON with action and optional tool name
   * @return JSON with trust metadata
   */
  @Tool(description = "Get benchmark trust metadata for NeqSim MCP tools. "
      + "Shows validation status (VALIDATED/TESTED/EXPERIMENTAL), reference validation "
      + "cases with accuracy bounds, known limitations, and unsupported conditions. "
      + "Industrial users should review this before relying on results. "
      + "Actions: getAll (full trust report), getTool (single tool trust page).")
  public String getBenchmarkTrust(@ToolArg(description = "JSON with: 'action' (getAll|getTool). "
      + "For getTool: 'toolName' (e.g. 'runFlash', 'runProcess', 'runPVT').") String trustJson) {
    try {
      JsonObject input = JsonParser.parseString(trustJson).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "getAll";

      if ("getTool".equals(action)) {
        String toolName = input.has("toolName") ? input.get("toolName").getAsString() : "";
        return BenchmarkTrust.getToolTrust(toolName);
      } else {
        return BenchmarkTrust.getTrustReport();
      }
    } catch (Exception e) {
      return errorJson("Benchmark trust query failed: " + e.getMessage());
    }
  }

  /**
   * Check whether the current deployment profile allows a tool and whether it requires human
   * approval. Use this before invoking tools in governed deployments.
   *
   * @param toolName the tool name to check
   * @return JSON with access decision
   */
  @Tool(description = "Check if a tool is allowed in the current industrial deployment mode "
      + "and whether it requires human approval before execution. "
      + "Use this in governed deployments (DIGITAL_TWIN, ENTERPRISE) to verify "
      + "access before calling a tool. Returns: allowed, requiresApproval, category, "
      + "and active deployment mode.")
  public String checkToolAccess(@ToolArg(description = "Tool name to check, e.g. 'runProcess', "
      + "'setSimulationVariable', 'manageSecurity'.") String toolName) {
    try {
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("tool", toolName);
      result.addProperty("activeMode", IndustrialProfile.getActiveMode().name());

      IndustrialProfile.ToolCategory cat = IndustrialProfile.getToolCategory(toolName);
      result.addProperty("category", cat != null ? cat.name() : "UNKNOWN");
      result.addProperty("allowed", IndustrialProfile.isToolAllowed(toolName));
      result.addProperty("requiresApproval", IndustrialProfile.requiresApproval(toolName));
      result.addProperty("inIndustrialCore",
          IndustrialProfile.getIndustrialCore().contains(toolName));
      result.addProperty("autoValidation", IndustrialProfile.isAutoValidationEnabled());

      if (!IndustrialProfile.isToolAllowed(toolName)) {
        result.addProperty("message",
            "Tool '" + toolName + "' is not allowed in " + IndustrialProfile.getActiveMode().name()
                + " mode. Switch to a less restrictive profile or use an alternative tool.");
      } else if (IndustrialProfile.requiresApproval(toolName)) {
        result.addProperty("message",
            "Tool '" + toolName + "' requires human approval in "
                + IndustrialProfile.getActiveMode().name()
                + " mode. Present the planned action to the engineer for confirmation.");
      }

      return GSON_PRETTY.toJson(result);
    } catch (Exception e) {
      return errorJson("Tool access check failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  private static final com.google.gson.Gson GSON_PRETTY = new com.google.gson.GsonBuilder()
      .setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Wraps a calculation result with automatic engineering validation when enabled.
   *
   * <p>
   * If {@link IndustrialProfile#isAutoValidationEnabled()} is true, this method appends a
   * {@code "validation"} block to the result JSON. This enforces the review's requirement that
   * validation be unavoidable, not optional.
   * </p>
   *
   * @param resultJson the raw calculation result JSON
   * @param context the validation context (process, compressor, pipeline, etc.)
   * @return the original JSON with validation appended, or unchanged if validation is off
   */
  static String withAutoValidation(String resultJson, String context) {
    if (!IndustrialProfile.isAutoValidationEnabled()) {
      return resultJson;
    }
    try {
      JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
      // Only validate successful results
      if (result.has("status") && "success".equals(result.get("status").getAsString())) {
        String validationJson = EngineeringValidator.validate(resultJson, context);
        JsonObject validation = JsonParser.parseString(validationJson).getAsJsonObject();
        result.add("autoValidation", validation);
      }
      return GSON_PRETTY.toJson(result);
    } catch (Exception e) {
      // If validation itself fails, return the original result unchanged
      return resultJson;
    }
  }
}
