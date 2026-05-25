package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;

/**
 * Capabilities discovery runner for MCP integration.
 *
 * <p>
 * Returns a structured manifest of everything NeqSim can calculate, organized by domain. This is
 * the entry point for external agents that need to understand what NeqSim offers before making tool
 * calls. The manifest is static and cached — it does not require any simulation to run.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CapabilitiesRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /** Cached capabilities JSON (built once on first access). */
  private static volatile String cachedCapabilities;

  /**
   * Private constructor — all methods are static.
   */
  private CapabilitiesRunner() {}

  /**
   * Returns the full capabilities manifest as a JSON string.
   *
   * @return JSON capabilities manifest
   */
  public static String getCapabilities() {
    if (cachedCapabilities == null) {
      cachedCapabilities = buildCapabilities();
    }
    return cachedCapabilities;
  }

  /**
   * Returns discoverable setup templates for major MCP workflows.
   *
   * @return JSON object keyed by setup template id
   */
  public static String getSetupTemplates() {
    return GSON.toJson(buildSetupTemplates());
  }

  /**
   * Returns one setup template by id.
   *
   * @param templateId the setup template id
   * @return JSON setup template, or an error object if the id is unknown
   */
  public static String getSetupTemplate(String templateId) {
    JsonObject templates = buildSetupTemplates();
    if (templates.has(templateId)) {
      return GSON.toJson(templates.getAsJsonObject(templateId));
    }
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", "Unknown setup template: " + templateId);
    error.add("availableTemplates", toJsonArray(getSetupTemplateIds()));
    return GSON.toJson(error);
  }

  /**
   * Builds the capabilities manifest.
   *
   * @return JSON string with all capabilities
   */
  private static String buildCapabilities() {
    JsonObject root = new JsonObject();
    root.addProperty("status", "success");
    root.addProperty("engine", "NeqSim");
    root.addProperty("description",
        "Java toolkit for thermodynamic calculations, process simulation, "
            + "mechanical design, and engineering standards compliance");

    // --- Thermodynamic Models ---
    JsonObject thermo = new JsonObject();
    thermo.add("equationsOfState",
        toJsonArray(
            Arrays.asList("SRK — Soave-Redlich-Kwong (general purpose, hydrocarbons + inorganics)",
                "PR — Peng-Robinson (general purpose, slightly better liquid density)",
                "CPA — CPA-SRK for associating fluids (water, methanol, glycols, MEG)",
                "GERG2008 — High-accuracy reference EOS for natural gas (ISO 20765)",
                "PCSAFT — PC-SAFT for polymers and complex molecules",
                "UMRPRU — UMR-PRU with Mathias-Copeman alpha function")));
    thermo.add("flashTypes",
        toJsonArray(Arrays.asList("TP — Temperature-Pressure (most common)",
            "PH — Pressure-Enthalpy", "PS — Pressure-Entropy", "TV — Temperature-Volume",
            "dewPointT — Dew point temperature at given pressure",
            "dewPointP — Dew point pressure at given temperature",
            "bubblePointT — Bubble point temperature at given pressure",
            "bubblePointP — Bubble point pressure at given temperature",
            "hydrateTP — Hydrate equilibrium temperature at given pressure")));
    thermo.addProperty("componentCount", "150+ pure components in database");
    thermo.add("specialSystems",
        toJsonArray(Arrays.asList("Hydrate prediction (sI, sII, sH structures)",
            "Wax appearance temperature (WAT)", "Asphaltene onset pressure",
            "Electrolyte systems (produced water, brine, scale prediction)",
            "CO2-rich systems (CCS transport and injection)", "Hydrogen blending and H2 systems")));
    root.add("thermodynamics", thermo);
    root.add("thermodynamicModels", thermo.deepCopy());

    // --- Process Equipment ---
    JsonObject process = new JsonObject();
    process.add("equipmentTypes",
        toJsonArray(Arrays.asList("Stream — Feed and intermediate streams",
            "Separator — Two-phase and three-phase separation",
            "Compressor — Centrifugal and reciprocating with polytropic/isentropic efficiency",
            "Expander — Turboexpander with efficiency", "Heater — Generic heat addition",
            "Cooler — Generic heat removal", "HeatExchanger — Shell-and-tube with UA-based rating",
            "ThrottlingValve — Isenthalpic expansion (JT valve)", "Mixer — Multi-stream mixing",
            "Splitter — Stream splitting by fraction",
            "ComponentSplitter — Component-level separation (e.g., TEG dehydration)",
            "DistillationColumn — Multi-stage distillation (standard, damped, inside-out solvers)",
            "Pump, Expander, Tank, Manifold, Flare, FlareStack, and SimpleReservoir",
            "FuelCell, Electrolyzer, CO2Electrolyzer, wind/solar/battery power equipment",
            "AdiabaticPipe — Adiabatic pipeline flow",
            "PipeBeggsAndBrills — Beggs & Brill multiphase flow correlation",
            "Recycle — Convergence of recycle loops",
            "Adjuster — Set-point adjustment (design spec)",
            "GasTurbine — Gas turbine with fuel consumption",
            "SteamTurbine — Steam power generation", "HRSG — Heat recovery steam generator",
            "SubseaWell — Subsea well with casing design")));
    process.add("processFeatures",
        toJsonArray(Arrays.asList("Steady-state and dynamic (transient) simulation",
            "Automatic stream connection and naming",
            "ProcessModel for multi-area plant composition",
            "String-addressable automation API (ProcessAutomation)",
            "Operational tag maps and P&ID-derived scenario runners (neqsim.process.operations)",
            "JSON-based process definition (ProcessSystem.fromJsonAndRun)",
            "State save/restore/compare for version tracking",
            "Simulation quality gate (mass/energy balance validation)")));
    root.add("processSimulation", process);
    root.add("processEquipment", process.deepCopy());

    // --- Calculation Modes ---
    JsonObject modes = new JsonObject();
    modes.add("available", toJsonArray(Arrays.asList(
        "runFlash — Single flash calculation (9 flash types x 6 EOS)",
        "runBatch — Multiple flash calculations in one call (sensitivity studies)",
        "runProcess — Full process simulation from JSON",
        "getPropertyTable — Sweep T or P and get property table",
        "getPhaseEnvelope — Calculate PT phase envelope",
        "runPVT — PVT laboratory experiments (CME, CVD, DL, saturation, separator, swelling, GOR, viscosity)",
        "runFlowAssurance — Flow assurance analysis (hydrate, wax, asphaltene, corrosion, erosion, cooldown)",
        "runChemistry — Open chemistry and integrity calculations for scale, corrosion, inhibitor, and scavenger screening",
        "runMaterialsReview — Process-wide material selection, degradation, CUI, and remaining-life review",
        "runOpenDrainReview — NORSOK S-001 Clause 9 open-drain review from normalized STID/P&ID and optional tagreader evidence",
        "runNorsokS001Clause10Review — NORSOK S-001 Clause 10 process safety system review from normalized C&E, SRS, PSV, instrument, tagreader, and optional dynamic simulation evidence",
        "calculateStandard — Gas/oil quality per 22 industry standards (ISO, AGA, GPA, EN, ASTM)",
        "runPipeline — Multiphase pipeline flow simulation (Beggs & Brill)",
        "runWaterHammer — Liquid-hammer screening for valve closure, pump trip, and check-valve scenarios",
        "runReservoir — Material balance reservoir simulation (tank model, depletion)",
        "runFieldEconomics — NPV/IRR with fiscal regimes (Norwegian NCS, UK, Brazil, US-GOM) + decline curves",
        "runDynamic — Dynamic transient simulation with auto-instrumented PID controllers",
        "runBioprocess — Bioprocessing reactors (anaerobic digester, fermentation, gasification, pyrolysis)",
        "runHAZOP — Simulation-backed IEC 61882 HAZOP worksheets from ProcessSystem scenarios",
        "runBarrierRegister — Evidence-linked SCE/barrier register validation and safety handoff",
        "runSafetySystemPerformance — STID-derived active/passive safety-system performance analysis with quantitative SIL/PFD bridge",
        "searchComponents — Component database search",
        "validateInput — Pre-flight input validation",
        "crossValidateModels — Run process under multiple EOS to quantify model risk",
        "runParametricStudy — Sweep inputs and record outputs (sensitivity/optimization)",
        "runOperationalStudy — P&ID/tag-driven valve scenarios, controller metrics, "
            + "and operating-envelope margin/trip screening",
        "getCapabilities — This tool (discovery)")));
    modes.addProperty("quickCalculation",
        "Use runFlash or getPropertyTable for single-query answers. "
            + "No process flowsheet required.");
    modes.addProperty("fullSimulation", "Use runProcess for multi-equipment flowsheet simulation. "
        + "Returns complete stream and equipment results.");
    root.add("calculationModes", modes);

    root.add("toolCapabilities", buildToolCapabilities());
    root.add("setupTemplates", buildSetupTemplates());
    root.add("processJsonContract", buildProcessJsonContract());
    root.add("capabilityGraph", buildCapabilityGraph());
    root.add("equipmentPropertyOntology", buildEquipmentPropertyOntology());
    root.add("benchmarkRegistry", buildBenchmarkRegistry());
    root.add("unitSystem", buildUnitSystem());
    root.add("automaticFlowsheetBuilder", buildAutomaticFlowsheetBuilder());
    root.add("optimizationUncertaintyWorkflows", buildOptimizationUncertaintyWorkflows());
    root.add("modelLifecycle", buildModelLifecycle());
    root.add("safetyGatePolicy", buildSafetyGatePolicy());
    root.addProperty("excludedFutureItem",
        "External simulator interoperability such as CAPE-OPEN, FMI, and commercial simulator bridges");

    // --- Engineering Domains ---
    JsonObject domains = new JsonObject();
    domains.add("supported", toJsonArray(Arrays.asList(
        "Natural gas processing (dehydration, NGL recovery, compression)",
        "Oil processing (separation, stabilization)", "CO2 capture, transport, and storage (CCS)",
        "Hydrogen systems (blending, electrolysis, transport)",
        "Flow assurance (hydrate, wax, asphaltene, corrosion, erosion, pipeline hydraulics)",
        "PVT analysis (CME, CVD, differential liberation, separator test, swelling, GOR, viscosity)",
        "Gas/oil quality standards (ISO 6976, AGA 3, GPA 2145, EN 16726, ASTM tests)",
        "Pipeline design (multiphase flow, pressure drop, flow regime, liquid holdup)",
        "Reservoir simulation (material balance, depletion, production forecasting)",
        "Field economics (NPV, IRR, cash flow, Norwegian/UK/Brazilian fiscal regimes, decline curves)",
        "Dynamic/transient simulation (PID control, startup/shutdown, controller tuning)",
        "P&ID operations (tag binding, valve-action scenarios, active-state evidence, control response)",
        "Bioprocessing (anaerobic digestion, fermentation, gasification, pyrolysis)",
        "Mechanical design (pipelines, vessels, wells per ASME/API/DNV/NORSOK)",
        "Safety (HAZOP, barrier management, SCE traceability, LOPA, SIL, bow-tie, QRA, PSV sizing)",
        "Power generation (gas turbines, steam cycles, HRSG, combined cycle)",
        "Heat integration (pinch analysis)")));
    root.add("engineeringDomains", domains);

    // --- Guided Workflows (MCP Prompts) ---
    JsonObject prompts = new JsonObject();
    prompts.add("available",
        toJsonArray(
            Arrays.asList("design_gas_processing — Step-by-step gas processing facility design",
                "pvt_study — Complete PVT study on a reservoir fluid",
                "flow_assurance_screening — Pipeline flow assurance screening",
                "field_development_screening — Field development concept screening",
                "co2_ccs_chain — CO2 transport and storage chain analysis",
                "teg_dehydration_design — TEG dehydration unit design",
                "biorefinery_analysis — Biorefinery process analysis",
                "dynamic_simulation — Dynamic simulation with controller setup",
                "pipeline_sizing — Multiphase pipeline sizing and design")));
    root.add("guidedWorkflows", prompts);

    // --- Trust Model ---
    JsonObject trust = new JsonObject();
    trust.addProperty("provenanceIncluded", true);
    trust.addProperty("provenanceDescription",
        "Every calculation response includes a 'provenance' block with: "
            + "EOS model used, mixing rule, assumptions, limitations, "
            + "convergence status, computation time, and validation checks passed");
    trust.add("validationFeatures",
        toJsonArray(Arrays.asList(
            "Pre-flight input validation (component names, T/P ranges, EOS compatibility)",
            "Post-calculation quality gate (mass/energy balance, physical bounds)",
            "Self-healing address resolution (fuzzy matching for variable names)",
            "Physical bounds checking before setting variable values")));
    trust.addProperty("operationalStudyGovernance",
        "P&ID-derived operational studies run on local simulation copies only. MCP does not write "
            + "to plant historians or control systems; governed profiles block state-modifying "
            + "scenario tools unless explicitly allowed.");
    trust.addProperty("disclaimer",
        "All outputs are computed by the NeqSim thermodynamic engine. "
            + "Results are engineering estimates requiring professional review "
            + "before use in design decisions or safety-critical applications.");
    root.add("trustModel", trust);

    neqsim.mcp.model.ResultProvenance provenance = new neqsim.mcp.model.ResultProvenance();
    provenance.setCalculationType("capability discovery");
    provenance.setConverged(true);
    provenance.setBenchmarkTrustLevel("TESTED");

    root.add("data", root.deepCopy());
    neqsim.mcp.model.ApiEnvelope.applyStandardFields(root, "getCapabilities", provenance,
        neqsim.mcp.model.ApiEnvelope.validationStatus(true, "discovery",
            "Capabilities manifest generated"),
        neqsim.mcp.model.ApiEnvelope.qualityGate("passed", "Capabilities manifest generated",
            true));

    return GSON.toJson(root);
  }

  /**
   * Builds machine-readable capability descriptors for every schema-backed MCP runner.
   *
   * @return object keyed by MCP tool method name
   */
  private static JsonObject buildToolCapabilities() {
    JsonObject tools = new JsonObject();
    addToolCapability(tools, "runFlash", "run_flash", "FlashRunner", "thermodynamics",
        "Thermodynamic flash calculation", Arrays.asList("components"),
        Arrays.asList("model", "temperature", "pressure", "flashType", "mixingRule", "enthalpy",
            "entropy", "volume"),
        eosModels(), thermoUnits(), "thermodynamic-flash",
        Arrays.asList("SRK/PR are weaker for polar and associating fluids",
            "Cubic EOS accuracy drops in near-critical and very high-pressure regions"));
    addToolCapability(tools, "runProcess", "run_process", "ProcessRunner", "process",
        "Steady-state ProcessSystem or ProcessModel simulation from JSON",
        Arrays.asList("fluid and process", "or areas"),
        Arrays.asList("fluids", "connections", "autoRun", "name"), eosModels(), processUnits(),
        "process-system",
        Arrays.asList("Recycle loops and distillation columns may require convergence tuning",
            "Equipment models use steady-state assumptions unless dynamic mode is selected"));
    addToolCapability(tools, "validateInput", "validate_input", "Validator", "validation",
        "Pre-flight validation of flash or process JSON", Arrays.asList("inputJson"),
        Collections.<String>emptyList(), eosModels(), validationUnits(), "validation",
        Arrays.asList("Validation checks input structure and common physical bounds",
            "Successful validation does not replace engineering review"));
    addToolCapability(tools, "searchComponents", "list_components", "ComponentQuery", "data",
        "Search thermodynamic component names", Arrays.asList("query"),
        Collections.<String>emptyList(), Collections.<String>emptyList(),
        Collections.<String>emptyList(), "data-catalog",
        Arrays.asList("Use exact database component names in calculations"));
    addToolCapability(tools, "runBatch", "run_batch", "BatchRunner", "thermodynamics",
        "Batch flash sensitivity calculations", Arrays.asList("cases"),
        Arrays.asList("model", "components", "flashType", "mixingRule"), eosModels(), thermoUnits(),
        "thermodynamic-flash",
        Arrays.asList("Inspect per-case failures before using aggregate results",
            "Large batches can be computationally expensive"));
    addToolCapability(tools, "getPropertyTable", "get_property_table", "PropertyTableRunner",
        "thermodynamics", "Property sweep over temperature or pressure",
        Arrays.asList("components", "sweep"),
        Arrays.asList("model", "mixingRule", "points", "properties"), eosModels(), thermoUnits(),
        "property-sweep",
        Arrays.asList("Phase transitions can create discontinuities in property profiles",
            "Transport properties have wider uncertainty than equilibrium properties"));
    addToolCapability(tools, "getPhaseEnvelope", "get_phase_envelope", "PhaseEnvelopeRunner",
        "thermodynamics", "PT phase envelope calculation", Arrays.asList("components"),
        Arrays.asList("model", "mixingRule"), eosModels(), thermoUnits(), "phase-envelope",
        Arrays.asList("Near-critical regions may have reduced curve quality",
            "Three-phase and hydrate boundaries require specialist workflows"));
    addToolCapability(tools, "getCapabilities", "get_capabilities", "CapabilitiesRunner",
        "discovery", "Discover schema-backed tools, setup templates, examples, and contracts",
        Collections.<String>emptyList(), Arrays.asList("includeExamples", "includeSetupTemplates"),
        Collections.<String>emptyList(), Collections.<String>emptyList(), "capability-discovery",
        Arrays.asList(
            "Capability metadata describes the MCP integration surface, not every Java API"));
    addToolCapability(tools, "runPVT", "run_pvt", "PVTRunner", "pvt",
        "PVT laboratory experiment simulation", Arrays.asList("experiment", "components"),
        Arrays.asList("model", "temperature_C", "pressure_bara", "experimentConfig"),
        Arrays.asList("SRK", "PR", "CPA", "PCSAFT"), thermoUnits(), "pvt-experiment",
        Arrays.asList("Accuracy depends strongly on C7+ characterization",
            "EOS tuning is often required to match laboratory saturation pressure"));
    addToolCapability(tools, "runFlowAssurance", "run_flow_assurance", "FlowAssuranceRunner",
        "flow-assurance", "Hydrate, wax, corrosion, scale, erosion, and cooldown screening",
        Arrays.asList("components", "analysis"),
        Arrays.asList("model", "temperature_C", "pressure_bara", "analysisConfig"), eosModels(),
        processUnits(), "flow-assurance", Arrays.asList(
            "Screening calculations need specialist review before chemical dosing decisions"));
    addToolCapability(tools, "calculateStandard", "calculate_standard", "StandardsRunner",
        "standards", "Gas and oil quality calculations per industry standards",
        Arrays.asList("standard", "components"), Arrays.asList("standardConfig", "model"),
        Arrays.asList("SRK", "PR", "GERG2008"), thermoUnits(), "standards-calculation",
        Arrays.asList("Custody-transfer use requires standard-specific input verification"));
    addToolCapability(tools, "runPipeline", "run_pipeline", "PipelineRunner", "pipeline",
        "Beggs and Brill multiphase pipeline simulation", Arrays.asList("components", "pipe"),
        Arrays.asList("flowRate", "model", "temperature_C", "pressure_bara"), eosModels(),
        processUnits(), "pipeline-flow",
        Arrays.asList("Correlation validity depends on flow regime and inclination"));
    addToolCapability(tools, "runReservoir", "run_reservoir", "ReservoirRunner", "reservoir",
        "Material-balance reservoir simulation", Arrays.asList("components"),
        Arrays.asList("gasVolume_Sm3", "oilVolume_Sm3", "producers", "simulationYears"),
        Arrays.asList("SRK", "PR"), processUnits(), "reservoir-forecast",
        Arrays.asList("Tank models are screening-level and do not replace reservoir simulation"));
    addToolCapability(tools, "runFieldEconomics", "run_field_economics", "FieldDevelopmentRunner",
        "economics", "Field-development economics and decline curves", Arrays.asList("mode"),
        Arrays.asList("country", "capex", "opex", "production", "prices"),
        Collections.<String>emptyList(), Arrays.asList("USD", "MUSD", "Sm3", "bbl", "year"),
        "field-economics", Arrays.asList("Cost and price assumptions dominate uncertainty"));
    addToolCapability(tools, "runDynamic", "run_dynamic", "DynamicRunner", "dynamic",
        "Transient process simulation with generated instrumentation", Arrays.asList("processJson"),
        Arrays.asList("duration_seconds", "timeStep_seconds", "tuning"), eosModels(),
        processUnits(), "dynamic-simulation",
        Arrays.asList("Default controller tuning is screening-level",
            "Timestep and holdup assumptions must be reviewed for operational studies"));
    addToolCapability(tools, "runBioprocess", "run_bioprocess", "BioprocessRunner", "bioprocess",
        "Bioprocess reactor simulation", Arrays.asList("reactorType"),
        Arrays.asList("feedRate_kgPerHr", "temperature_C", "volume_m3", "biomass"),
        Collections.<String>emptyList(), Arrays.asList("kg/hr", "C", "m3", "%"), "bioprocess",
        Arrays.asList("Bioprocess yields are screening correlations unless calibrated"));
    addToolCapability(tools, "sizeEquipment", "size_equipment", "EquipmentSizingRunner",
        "equipment-design", "Quick separator and compressor sizing", Arrays.asList("equipmentType"),
        Arrays.asList("flowRate", "orientation", "outletPressure_bara", "polytropicEfficiency"),
        eosModels(), processUnits(), "equipment-sizing",
        Arrays.asList("Sizing is preliminary and should be followed by mechanical design"));
    addToolCapability(tools, "compareProcesses", "compare_processes", "ProcessComparisonRunner",
        "process", "Compare process cases side by side", Arrays.asList("cases"),
        Arrays.asList("outputs", "tolerances"), eosModels(), processUnits(), "process-system",
        Arrays.asList("Case comparability depends on consistent scopes and units"));
    addToolCapability(tools, "manageSession", "manage_session", "SessionRunner", "session",
        "Stateful incremental process construction", Arrays.asList("action"),
        Arrays.asList("sessionId", "fluid", "processJson", "equipment", "address", "value", "unit"),
        eosModels(), processUnits(), "session-management",
        Arrays.asList("Session state is transient unless explicitly persisted"));
    addToolCapability(tools, "generateVisualization", "visualize", "VisualizationRunner",
        "visualization", "Generate SVG, Mermaid, or table visualizations", Arrays.asList("type"),
        Arrays.asList("data", "processJson", "components", "labels", "values"),
        Collections.<String>emptyList(), Collections.<String>emptyList(), "visualization-reporting",
        Arrays
            .asList("Visualizations summarize data and should not be used as calculation sources"));
    addToolCapability(tools, "runHAZOP", "run_hazop", "HAZOPStudyRunner", "safety",
        "Generate simulation-backed HAZOP worksheets", Arrays.asList("processDefinition"),
        Arrays.asList("nodes", "failureModes", "barrierRegister", "runSimulations"), eosModels(),
        processUnits(), "safety-analysis",
        Arrays.asList("HAZOP output requires facilitator and discipline review"));
    addToolCapability(tools, "runBarrierRegister", "run_barrier_register", "BarrierRegisterRunner",
        "safety", "Validate and transform safety barrier registers", Arrays.asList("register"),
        Arrays.asList("mode", "evidence", "performanceStandards"), Collections.<String>emptyList(),
        Collections.<String>emptyList(), "safety-analysis",
        Arrays.asList("Barrier evidence quality controls finding confidence"));
    addToolCapability(tools, "runSafetySystemPerformance", "run_safety_system_performance",
        "SafetySystemPerformanceRunner", "safety",
        "Analyze active and passive safety-system performance", Arrays.asList("register"),
        Arrays.asList("demands", "measurementDevices", "logicSifs", "quantitativeSifs"),
        Collections.<String>emptyList(), Arrays.asList("seconds", "PFDavg", "SIL"),
        "safety-analysis", Arrays.asList("Safety performance findings require independent review"));
    addAdditionalMcpToolCapabilities(tools);
    return tools;
  }

  /**
   * Adds capability descriptors for MCP tools that use server-level or generic schema contracts.
   *
   * @param tools mutable tool capability map
   */
  private static void addAdditionalMcpToolCapabilities(JsonObject tools) {
    addGenericToolCapability(tools, "getExample", "get_example", "discovery",
        "Fetch a canonical example input by category and name", "capability-discovery");
    addGenericToolCapability(tools, "getSchema", "get_schema", "discovery",
        "Fetch an input or output JSON Schema by tool name", "unit-safe-schema");
    addGenericToolCapability(tools, "listSimulationUnits", "list_simulation_units", "automation",
        "Run a process and list addressable equipment units", "model-lifecycle");
    addGenericToolCapability(tools, "listUnitVariables", "list_unit_variables", "automation",
        "List addressable variables for one process unit", "model-lifecycle");
    addGenericToolCapability(tools, "getSimulationVariable", "get_simulation_variable",
        "automation", "Read a process variable by automation address", "model-lifecycle");
    addGenericToolCapability(tools, "setSimulationVariable", "set_simulation_variable",
        "automation", "Set an input variable and rerun a process", "model-lifecycle");
    addGenericToolCapability(tools, "saveSimulationState", "save_simulation_state", "lifecycle",
        "Save a process state snapshot", "model-lifecycle");
    addGenericToolCapability(tools, "compareSimulationStates", "compare_simulation_states",
        "lifecycle", "Compare two process state snapshots", "model-lifecycle");
    addGenericToolCapability(tools, "diagnoseAutomation", "diagnose_automation", "automation",
        "Diagnose a failed automation address or operation", "model-lifecycle");
    addGenericToolCapability(tools, "getAutomationLearningReport", "get_automation_learning_report",
        "automation", "Summarize automation corrections and operation history", "model-lifecycle");
    addGenericToolCapability(tools, "runOperationalStudy", "run_operational_study", "process",
        "Run P&ID/tag-driven operating scenarios and trip-margin screening", "process-system");
    addGenericToolCapability(tools, "crossValidateModels", "cross_validate_models", "uncertainty",
        "Run one process under multiple EOS models", "optimization-uncertainty");
    addGenericToolCapability(tools, "runParametricStudy", "run_parametric_study", "uncertainty",
        "Sweep model inputs and collect output responses", "optimization-uncertainty");
    addGenericToolCapability(tools, "runChemistry", "run_chemistry", "flow-assurance",
        "Run chemistry and integrity screening calculations", "flow-assurance");
    addGenericToolCapability(tools, "runMaterialsReview", "run_materials_review", "materials",
        "Review process materials, degradation mechanisms, and remaining life",
        "safety-governance");
    addGenericToolCapability(tools, "runOpenDrainReview", "run_open_drain_review", "safety",
        "Review open-drain evidence against NORSOK S-001 Clause 9", "safety-analysis");
    addGenericToolCapability(tools, "runNorsokS001Clause10Review",
        "run_norsok_s001_clause10_review", "safety",
        "Review process safety systems against NORSOK S-001 Clause 10", "safety-analysis");
    addGenericToolCapability(tools, "runWaterHammer", "run_water_hammer", "pipeline",
        "Screen water-hammer and liquid-hammer transient pressure envelopes", "pipeline-flow");
    addGenericToolCapability(tools, "runRootCauseAnalysis", "run_root_cause_analysis",
        "diagnostics", "Rank equipment root-cause hypotheses from evidence and simulation",
        "benchmark-registry");
    addGenericToolCapability(tools, "solveTask", "solve_task", "workflow",
        "Run an autonomous engineering task workflow", "flowsheet-builder");
    addGenericToolCapability(tools, "composeWorkflow", "compose_workflow", "workflow",
        "Chain multiple NeqSim MCP tools into a workflow", "flowsheet-builder");
    addGenericToolCapability(tools, "validateResults", "validate_results", "validation",
        "Validate simulation results against engineering rules", "benchmark-registry");
    addGenericToolCapability(tools, "generateReport", "generate_report", "reporting",
        "Generate a structured engineering report from result JSON", "visualization-reporting");
    addGenericToolCapability(tools, "bridgeTaskWorkflow", "bridge_task_workflow", "workflow",
        "Convert MCP output into task_solve results.json format", "flowsheet-builder");
    addGenericToolCapability(tools, "runPlugin", "run_plugin", "platform",
        "Run or list registered MCP runner plugins", "capability-discovery");
    addGenericToolCapability(tools, "getProgress", "get_progress", "platform",
        "Check progress of long-running simulations", "model-lifecycle");
    addGenericToolCapability(tools, "streamSimulation", "stream_simulation", "platform",
        "Run simulations with incremental progress polling", "model-lifecycle");
    addGenericToolCapability(tools, "composeMultiServerWorkflow", "compose_multi_server_workflow",
        "workflow", "Compose workflows across multiple MCP servers", "flowsheet-builder");
    addGenericToolCapability(tools, "manageSecurity", "manage_security", "governance",
        "Manage MCP API keys, rate limits, and audit logging", "safety-governance");
    addGenericToolCapability(tools, "manageState", "manage_state", "lifecycle",
        "Persist and restore simulation states across server restarts", "model-lifecycle");
    addGenericToolCapability(tools, "manageValidationProfile", "manage_validation_profile",
        "governance", "Configure jurisdiction-specific validation profiles", "safety-governance");
    addGenericToolCapability(tools, "queryDataCatalog", "query_data_catalog", "data",
        "Browse components, standards, materials, and EOS model data", "data-catalog");
    addGenericToolCapability(tools, "runRelief", "run_relief", "safety",
        "Size relief devices and API 521 fire input cases", "safety-governance");
    addGenericToolCapability(tools, "runLOPA", "run_lopa", "safety",
        "Run layer-of-protection analysis and required SIL gap checks", "safety-governance");
    addGenericToolCapability(tools, "runSIL", "run_sil", "safety",
        "Verify SIF PFDavg and SIL claims", "safety-governance");
    addGenericToolCapability(tools, "runRiskMatrix", "run_risk_matrix", "safety",
        "Score events on an ISO 31000 style risk matrix", "safety-governance");
    addGenericToolCapability(tools, "runFlareNetwork", "run_flare_network", "safety",
        "Calculate flare radiation and safe-distance screening", "safety-governance");
    addGenericToolCapability(tools, "manageIndustrialProfile", "manage_industrial_profile",
        "governance", "Configure tool access profiles for industrial deployment",
        "safety-governance");
    addGenericToolCapability(tools, "getBenchmarkTrust", "get_benchmark_trust", "benchmark",
        "Fetch per-tool benchmark trust and validation maturity metadata", "benchmark-registry");
    addGenericToolCapability(tools, "checkToolAccess", "check_tool_access", "governance",
        "Check whether a tool is allowed in the active deployment profile", "safety-governance");
  }

  /**
   * Adds a generic capability descriptor for a server-level MCP tool.
   *
   * @param tools mutable tool capability map
   * @param mcpToolName public MCP tool method name
   * @param schemaToolName schema catalog tool name
   * @param workflowCategory workflow category
   * @param purpose short purpose description
   * @param setupTemplateId setup template id
   */
  private static void addGenericToolCapability(JsonObject tools, String mcpToolName,
      String schemaToolName, String workflowCategory, String purpose, String setupTemplateId) {
    addToolCapability(tools, mcpToolName, schemaToolName, "neqsim.mcp.server.NeqSimTools",
        workflowCategory, purpose, Collections.<String>emptyList(),
        Arrays.asList("action", "inputJson", "processJson", "arguments", "options"), eosModels(),
        processUnits(), setupTemplateId,
        Arrays.asList("Uses a generic MCP schema contract unless a dedicated schema is available",
            "Review the capability descriptor and example before execution"));
  }

  /**
   * Adds one capability descriptor to the map.
   *
   * @param tools mutable tool capability map
   * @param mcpToolName public MCP tool method name
   * @param schemaToolName schema catalog tool name
   * @param runnerClass runner implementation class
   * @param workflowCategory workflow category
   * @param purpose short tool purpose
   * @param requiredFields required input fields
   * @param optionalFields optional input fields
   * @param supportedModels supported thermodynamic models
   * @param supportedUnits supported or commonly used units
   * @param setupTemplateId setup template id
   * @param limitations known limitations
   */
  private static void addToolCapability(JsonObject tools, String mcpToolName, String schemaToolName,
      String runnerClass, String workflowCategory, String purpose, List<String> requiredFields,
      List<String> optionalFields, List<String> supportedModels, List<String> supportedUnits,
      String setupTemplateId, List<String> limitations) {
    JsonObject descriptor = new JsonObject();
    descriptor.addProperty("purpose", purpose);
    descriptor.addProperty("apiVersion", neqsim.mcp.model.ApiEnvelope.API_VERSION);
    descriptor.addProperty("mcpToolName", mcpToolName);
    descriptor.addProperty("schemaToolName", schemaToolName);
    descriptor.addProperty("runnerClass",
        runnerClass.indexOf('.') >= 0 ? runnerClass : "neqsim.mcp.runners." + runnerClass);
    descriptor.addProperty("workflowCategory", workflowCategory);
    descriptor.addProperty("maturityLevel", BenchmarkTrust.getMaturityLevel(mcpToolName));
    descriptor.add("requiredFields", toJsonArray(requiredFields));
    descriptor.add("optionalFields", toJsonArray(optionalFields));
    descriptor.add("supportedModels", toJsonArray(supportedModels));
    descriptor.add("supportedUnits", toJsonArray(supportedUnits));
    descriptor.add("knownLimitations", toJsonArray(limitations));
    descriptor.add("schemas", schemaRefs(schemaToolName));
    descriptor.add("examples", exampleRefs(schemaToolName));
    descriptor.add("setupTemplates", setupTemplateRefs(setupTemplateId));
    descriptor.add("validationCoverage", validationCoverage(schemaToolName));
    descriptor.add("responseContractCoverage", responseContractCoverage(schemaToolName));
    descriptor.add("standardResponseFields", toJsonArray(Arrays.asList("apiVersion", "status",
        "tool", "data", "provenance", "validation", "qualityGate", "warnings")));
    if ("run_process".equals(schemaToolName) || "run_dynamic".equals(schemaToolName)
        || "compare_processes".equals(schemaToolName) || "run_hazop".equals(schemaToolName)) {
      descriptor.add("processJsonContractRef", stringArray("processJsonContract"));
    }
    tools.add(mcpToolName, descriptor);
  }

  /**
   * Builds references to available schemas for a tool.
   *
   * @param schemaToolName schema catalog tool name
   * @return schema reference object
   */
  private static JsonObject schemaRefs(String schemaToolName) {
    JsonObject schemas = new JsonObject();
    schemas.addProperty("inputSchemaUri", "neqsim://schemas/" + schemaToolName + "/input");
    schemas.addProperty("outputSchemaUri", "neqsim://schemas/" + schemaToolName + "/output");
    schemas.addProperty("inputSchemaAvailable",
        SchemaCatalog.getSchema(schemaToolName, "input") != null);
    schemas.addProperty("outputSchemaAvailable",
        SchemaCatalog.getSchema(schemaToolName, "output") != null);
    return schemas;
  }

  /**
   * Builds example references for a tool.
   *
   * @param schemaToolName schema catalog tool name
   * @return array of example reference objects
   */
  private static JsonArray exampleRefs(String schemaToolName) {
    JsonArray examples = new JsonArray();
    JsonObject example = new JsonObject();
    example.addProperty("category", "tool");
    example.addProperty("name", schemaToolName);
    example.addProperty("uri", "neqsim://examples/tool/" + schemaToolName);
    example.addProperty("available", ExampleCatalog.getExample("tool", schemaToolName) != null);
    examples.add(example);
    return examples;
  }

  /**
   * Builds setup-template references for a tool.
   *
   * @param setupTemplateId setup template id
   * @return array of setup template reference objects
   */
  private static JsonArray setupTemplateRefs(String setupTemplateId) {
    JsonArray refs = new JsonArray();
    JsonObject ref = new JsonObject();
    ref.addProperty("id", setupTemplateId);
    ref.addProperty("uri", "neqsim://setup-templates/" + setupTemplateId);
    refs.add(ref);
    return refs;
  }

  /**
   * Builds validation coverage metadata for a tool.
   *
   * @param schemaToolName schema catalog tool name
   * @return validation coverage object
   */
  private static JsonObject validationCoverage(String schemaToolName) {
    JsonObject coverage = new JsonObject();
    coverage.addProperty("inputSchema", SchemaCatalog.getSchema(schemaToolName, "input") != null);
    coverage.addProperty("exampleAvailable",
        ExampleCatalog.getExample("tool", schemaToolName) != null);
    coverage.addProperty("preflightTool", "validate_input");
    coverage.addProperty("qualityGateField", "qualityGate");
    coverage.addProperty("engineeringReviewRequired", true);
    return coverage;
  }

  /**
   * Builds response contract coverage metadata for a tool.
   *
   * @param schemaToolName schema catalog tool name
   * @return response contract coverage object
   */
  private static JsonObject responseContractCoverage(String schemaToolName) {
    JsonObject coverage = new JsonObject();
    coverage.addProperty("contractVersion", neqsim.mcp.model.ApiEnvelope.API_VERSION);
    coverage.addProperty("outputSchema", SchemaCatalog.getSchema(schemaToolName, "output") != null);
    coverage.add("expectedFields",
        toJsonArray(Arrays.asList("status", "data", "validation", "qualityGate", "warnings")));
    coverage.addProperty("legacyFieldsPreserved", true);
    return coverage;
  }

  /**
   * Builds setup templates for the main MCP workflow families.
   *
   * @return setup templates keyed by id
   */
  private static JsonObject buildSetupTemplates() {
    JsonObject templates = new JsonObject();
    addSetupTemplate(templates, "capability-discovery", "Discover MCP surfaces before running",
        Arrays.asList("getCapabilities"),
        Arrays.asList("Call getCapabilities first",
            "Use toolCapabilities[*].schemas to fetch schemas",
            "Use toolCapabilities[*].examples to fetch a starter JSON"),
        "{\"includeExamples\":true,\"includeSetupTemplates\":true}");
    addSetupTemplate(templates, "thermodynamic-flash", "Single or batched thermodynamic flash",
        Arrays.asList("runFlash", "runBatch"),
        Arrays.asList("Choose EOS and mixing rule", "Provide components as mole fractions",
            "Specify temperature/pressure with units",
            "Call validateInput for reusable JSON payloads"),
        ExampleCatalog.flashTPSimpleGas());
    addSetupTemplate(templates, "property-sweep", "Temperature or pressure property sweeps",
        Arrays.asList("getPropertyTable"),
        Arrays.asList("Choose sweep variable", "Set sweepFrom/sweepTo with units",
            "Set fixedPressure or fixedTemperature",
            "Request only needed properties for compact output"),
        ExampleCatalog.propertyTableTemperatureSweep());
    addSetupTemplate(templates, "phase-envelope", "PT phase-envelope setup",
        Arrays.asList("getPhaseEnvelope"),
        Arrays.asList("Use a hydrocarbon-rich composition", "Start with SRK or PR",
            "Inspect provenance limitations near critical points"),
        ExampleCatalog.phaseEnvelopeNaturalGas());
    addSetupTemplate(templates, "process-system", "Steady-state process JSON setup",
        Arrays.asList("runProcess", "compareProcesses"),
        Arrays.asList("Define a default fluid", "Add ordered process units",
            "Reference upstream outlets by name or name.port",
            "Use [value, unit] for unit-aware equipment properties"),
        ExampleCatalog.processSimpleSeparation());
    addSetupTemplate(templates, "process-model", "Multi-area ProcessModel setup",
        Arrays.asList("runProcess"),
        Arrays.asList("Create top-level areas object",
            "Each area contains a standard fluid/process JSON", "Use shared stream names carefully",
            "Inspect returned area reports"),
        "{\"areas\":{\"Separation\":" + ExampleCatalog.processSimpleSeparation() + "}}");
    addSetupTemplate(templates, "validation", "Validate inputs or outputs before chaining",
        Arrays.asList("validateInput"), Arrays.asList("Validate flash/process JSON before run",
            "Treat error severity as blocking", "Use remediation hints to repair payloads"),
        ExampleCatalog.validationErrorFlash());
    addSetupTemplate(templates, "data-catalog", "Discover components, EOS models, and standards",
        Arrays.asList("searchComponents"),
        Arrays.asList("Search partial component names",
            "Use returned names verbatim in components maps",
            "Fetch component resources for properties"),
        ExampleCatalog.componentSearchMethane());
    addSetupTemplate(templates, "pvt-experiment", "PVT laboratory experiment setup",
        Arrays.asList("runPVT"), Arrays.asList("Define reservoir fluid composition",
            "Choose experiment type", "Add experimentConfig arrays/stages as needed"),
        ExampleCatalog.pvtCME());
    addSetupTemplate(templates, "flow-assurance", "Flow assurance screening setup",
        Arrays.asList("runFlowAssurance"), Arrays.asList("Select analysis type",
            "Use CPA when water/hydrates dominate", "Include operating envelope in analysisConfig"),
        ExampleCatalog.flowAssuranceHydrate());
    addSetupTemplate(templates, "standards-calculation", "Gas and oil quality standard setup",
        Arrays.asList("calculateStandard"),
        Arrays.asList("Select standard code", "Set reference temperature and pressure",
            "Add standardConfig for method-specific values"),
        ExampleCatalog.standardISO6976());
    addSetupTemplate(templates, "pipeline-flow", "Pipeline hydraulic screening setup",
        Arrays.asList("runPipeline"),
        Arrays.asList("Define fluid and inlet conditions",
            "Specify pipe diameter, length, elevation, roughness",
            "Review flow-regime limitations"),
        ExampleCatalog.pipelineMultiphase());
    addSetupTemplate(templates, "reservoir-forecast", "Reservoir material-balance setup",
        Arrays.asList("runReservoir"), Arrays.asList("Define in-place volumes",
            "Add producers/injectors", "Set simulation years and timestep"),
        ExampleCatalog.reservoirDepletion());
    addSetupTemplate(templates, "field-economics", "Field economics setup",
        Arrays.asList("runFieldEconomics"),
        Arrays.asList("Choose cashflow or productionProfile mode",
            "Declare price, CAPEX, OPEX, production, and discount assumptions",
            "Report currency and fiscal regime"),
        ExampleCatalog.economicsNorwegianNCS());
    addSetupTemplate(templates, "dynamic-simulation", "Dynamic simulation setup",
        Arrays.asList("runDynamic"),
        Arrays.asList("Start from a validated processJson", "Set duration and timestep",
            "Keep controller tuning explicit for operational studies"),
        ExampleCatalog.dynamicSeparatorTransient());
    addSetupTemplate(templates, "bioprocess", "Bioprocess reactor setup",
        Arrays.asList("runBioprocess"), Arrays.asList("Choose reactorType",
            "Provide feed or biomass properties", "Check energy and yield outputs"),
        ExampleCatalog.bioprocessAnaerobicDigestion());
    addSetupTemplate(templates, "equipment-sizing", "Quick equipment sizing setup",
        Arrays.asList("sizeEquipment"), Arrays.asList("Pick separator or compressor",
            "Provide fluid and flow rate", "Treat results as preliminary sizing"),
        ExampleCatalog.sizingSeparator());
    addSetupTemplate(templates, "session-management", "Stateful session setup",
        Arrays.asList("manageSession"), Arrays.asList("Create a session from fluid or processJson",
            "Add equipment incrementally", "Close or persist sessions when finished"),
        ExampleCatalog.sessionCreate());
    addSetupTemplate(templates, "visualization-reporting", "Visualization setup",
        Arrays.asList("generateVisualization"),
        Arrays.asList("Choose visualization type", "Pass result data or processJson",
            "Use generated media as reporting artifacts"),
        ExampleCatalog.visualizationPhaseEnvelope());
    addSetupTemplate(templates, "safety-analysis", "Safety workflow setup",
        Arrays.asList("runHAZOP", "runBarrierRegister", "runSafetySystemPerformance"),
        Arrays.asList("Start from processDefinition or barrier register evidence",
            "Preserve evidenceRefs for traceability", "Escalate findings for discipline review"),
        ExampleCatalog.safetyHazopStudy());
    addSetupTemplate(templates, "flowsheet-builder",
        "Convert process descriptions into validated process JSON",
        Arrays.asList("getCapabilities", "getSchema", "getExample", "validateInput", "runProcess",
            "composeWorkflow", "bridgeTaskWorkflow"),
        Arrays.asList("Classify the source description into fluid, equipment, streams, and specs",
            "Select an EOS and units from the capability manifest",
            "Assemble process JSON using processJsonContract and equipmentPropertyOntology",
            "Validate before running and preserve assumptions in provenance"),
        "{\"source\":\"Feed gas enters a separator, then gas is compressed\",\"target\":\"run_process JSON\"}");
    addSetupTemplate(templates, "benchmark-registry",
        "Attach benchmark trust and validation evidence to tool results",
        Arrays.asList("getBenchmarkTrust", "validateResults", "getCapabilities"),
        Arrays.asList("Fetch per-tool benchmarkTrust before relying on a result",
            "Compare validationCases and maturityLevel with the task criticality",
            "Run validateResults for design-rule checks", "Report accuracy bounds and limitations"),
        "{\"action\":\"getTool\",\"tool\":\"runFlash\"}");
    addSetupTemplate(templates, "unit-safe-schema", "Build inputs with explicit units",
        Arrays.asList("getSchema", "getCapabilities", "validateInput"),
        Arrays.asList("Use value/unit objects or [value, unit] arrays instead of bare numbers",
            "Pick units from unitSystem.dimensions",
            "Validate dimensional intent before execution"),
        "{\"temperature\":{\"value\":25.0,\"unit\":\"C\"},\"pressure\":{\"value\":80.0,\"unit\":\"bara\"}}");
    addSetupTemplate(templates, "optimization-uncertainty",
        "Run sensitivity, uncertainty, and model-risk workflows",
        Arrays.asList("runParametricStudy", "runBatch", "crossValidateModels", "compareProcesses",
            "runFieldEconomics"),
        Arrays.asList("Define the base case and uncertain parameters with units",
            "Run parametric sweeps or batch cases", "Cross-validate EOS choices where relevant",
            "Summarize P10/P50/P90 or tornado drivers in the report"),
        "{\"objective\":\"minimize compressor power\",\"parameters\":[{\"name\":\"outletPressure\",\"low\":60,\"high\":120,\"unit\":\"bara\"}]}");
    addSetupTemplate(templates, "model-lifecycle",
        "Create, inspect, save, restore, and compare process model states",
        Arrays.asList("manageSession", "saveSimulationState", "compareSimulationStates",
            "manageState", "listSimulationUnits", "listUnitVariables"),
        Arrays.asList("Create or load a session", "Inspect addressable units and variables",
            "Save snapshots before changes", "Compare snapshots after changes",
            "Persist state only when the deployment profile allows it"),
        "{\"action\":\"create\",\"processJson\":{\"fluid\":{},\"process\":[]}}");
    addSetupTemplate(templates, "safety-governance",
        "Apply review gates for safety, standards, and industrial deployment tools",
        Arrays.asList("runRelief", "runLOPA", "runSIL", "runRiskMatrix", "runFlareNetwork",
            "manageIndustrialProfile", "checkToolAccess", "manageValidationProfile"),
        Arrays.asList("Check tool access before execution",
            "Select jurisdiction and standard basis",
            "Require evidence references for safety findings",
            "Keep engineeringReviewRequired true",
            "Separate calculation output from design approval"),
        "{\"profile\":\"ENTERPRISE\",\"tool\":\"runRelief\",\"approvalRequired\":true}");
    return templates;
  }

  /**
   * Adds a setup template to the template map.
   *
   * @param templates mutable template map
   * @param id template id
   * @param purpose template purpose
   * @param tools applicable MCP tools
   * @param steps setup steps
   * @param skeleton JSON skeleton or example payload
   */
  private static void addSetupTemplate(JsonObject templates, String id, String purpose,
      List<String> tools, List<String> steps, String skeleton) {
    JsonObject template = new JsonObject();
    template.addProperty("id", id);
    template.addProperty("uri", "neqsim://setup-templates/" + id);
    template.addProperty("purpose", purpose);
    template.add("applicableTools", toJsonArray(tools));
    template.add("setupSteps", toJsonArray(steps));
    template.addProperty("jsonSkeleton", skeleton);
    templates.add(id, template);
  }

  /**
   * Builds the process JSON contract advertised to agents.
   *
   * @return process JSON contract object
   */
  private static JsonObject buildProcessJsonContract() {
    JsonObject contract = new JsonObject();
    contract.add("rootFields", toJsonArray(
        Arrays.asList("name", "fluid", "fluids", "process", "connections", "areas", "autoRun")));
    contract.add("fluidFields",
        toJsonArray(Arrays.asList("model", "temperature", "pressure", "mixingRule", "components",
            "characterizedComponents", "e300FilePath", "binaryInteractionParameters",
            "multiPhaseCheck")));
    contract.add("unitFields",
        toJsonArray(Arrays.asList("type", "name", "inlet", "inlets", "fluidRef", "properties")));
    contract.add("connectionFields",
        toJsonArray(Arrays.asList("from", "sourcePort", "to", "targetPort", "type")));
    contract.add("supportedEquipmentTypes",
        toJsonArray(
            Arrays.asList("Stream", "Separator", "ThreePhaseSeparator", "GasScrubber", "Compressor",
                "Pump", "Expander", "Heater", "Cooler", "HeatExchanger", "ThrottlingValve", "Mixer",
                "Splitter", "ComponentSplitter", "DistillationColumn", "Recycle", "Adjuster",
                "SetPoint", "Calculator", "Tank", "AdiabaticPipe", "PipeBeggsAndBrills",
                "SimpleReservoir", "Manifold", "Flare", "FlareStack", "GibbsReactor",
                "PlugFlowReactor", "StirredTankReactor", "SimpleTEGAbsorber", "Electrolyzer",
                "CO2Electrolyzer", "FuelCell", "WindTurbine", "BatteryStorage", "SolarPanel",
                "WindFarm", "OffshoreEnergySystem", "SubseaPowerCable", "StreamSaturatorUtil")));
    contract.add("streamReferencePorts",
        toJsonArray(Arrays.asList("outlet", "gasOut", "gas", "liquidOut", "liquid", "oilOut", "oil",
            "waterOut", "water", "split0", "split1", "hx0", "hx1")));

    JsonObject commonProperties = new JsonObject();
    commonProperties.add("Stream",
        toJsonArray(Arrays.asList("flowRate", "temperature", "pressure")));
    commonProperties.add("Compressor", toJsonArray(
        Arrays.asList("outletPressure", "isentropicEfficiency", "polytropicEfficiency")));
    commonProperties.add("Pump",
        toJsonArray(Arrays.asList("outletPressure", "isentropicEfficiency")));
    commonProperties.add("Heater", toJsonArray(Arrays.asList("outTemperature", "energyInput")));
    commonProperties.add("Cooler", toJsonArray(Arrays.asList("outTemperature", "energyInput")));
    commonProperties.add("ThrottlingValve",
        toJsonArray(Arrays.asList("outletPressure", "percentValveOpening")));
    commonProperties.add("Separator",
        toJsonArray(Arrays.asList("entrainment", "mechanicalDesign")));
    commonProperties.add("Splitter", toJsonArray(Arrays.asList("splitFactors")));
    commonProperties.add("ComponentSplitter", toJsonArray(Arrays.asList("splitFactors")));
    commonProperties.add("HeatExchanger",
        toJsonArray(Arrays.asList("UAvalue", "guessOutTemperature")));
    commonProperties.add("DistillationColumn",
        toJsonArray(Arrays.asList("numberOfTrays", "hasReboiler", "hasCondenser")));
    commonProperties.add("Adjuster",
        toJsonArray(Arrays.asList("adjustedEquipment", "adjustedVariable", "targetEquipment",
            "targetVariable", "targetValue", "targetUnit", "stepSize")));
    commonProperties.add("PipeBeggsAndBrills", toJsonArray(
        Arrays.asList("diameter", "length", "elevation", "roughness", "numberOfIncrements")));
    contract.add("commonPropertiesByEquipment", commonProperties);

    JsonObject propertyEncoding = new JsonObject();
    propertyEncoding.addProperty("bareNumber",
        "{\"outletPressure\": 80.0} uses the Java setter default unit");
    propertyEncoding.addProperty("unitArray",
        "{\"outletPressure\": [80.0, \"bara\"]} calls setters with value and unit");
    propertyEncoding.addProperty("boolean", "{\"hasReboiler\": true}");
    propertyEncoding.addProperty("string", "{\"orientation\": \"horizontal\"}");
    contract.add("propertyEncoding", propertyEncoding);

    JsonObject units = new JsonObject();
    units.add("temperature", toJsonArray(Arrays.asList("K", "C", "F", "R")));
    units.add("pressure",
        toJsonArray(Arrays.asList("bara", "barg", "Pa", "kPa", "MPa", "psi", "psia", "atm")));
    units.add("flowRate",
        toJsonArray(Arrays.asList("kg/hr", "kg/s", "mol/sec", "Sm3/day", "MSm3/day", "m3/hr")));
    units.add("energy", toJsonArray(Arrays.asList("J", "kJ", "W", "kW", "MW", "kJ/hr")));
    units.add("length", toJsonArray(Arrays.asList("m", "cm", "mm", "inch", "ft")));
    units.add("time", toJsonArray(Arrays.asList("s", "seconds", "min", "hr", "day", "year")));
    units.add("efficiency", toJsonArray(Arrays.asList("fraction", "percent")));
    contract.add("units", units);
    return contract;
  }

  /**
   * Builds a graph-style capability map for agents that reason over domains, tools, contracts, and
   * workflows.
   *
   * @return capability graph object
   */
  private static JsonObject buildCapabilityGraph() {
    JsonObject graph = new JsonObject();
    JsonArray nodes = new JsonArray();
    JsonArray edges = new JsonArray();
    List<String> domains = Arrays.asList("thermodynamics", "process", "automation", "pvt",
        "flow-assurance", "pipeline", "reservoir", "economics", "dynamic", "bioprocess", "safety",
        "governance", "validation", "benchmark", "lifecycle", "workflow", "data", "reporting",
        "visualization", "uncertainty", "platform", "discovery");
    for (String domain : domains) {
      addGraphNode(nodes, "domain:" + domain, "domain", domain, domain);
    }
    for (String toolName : SchemaCatalog.getToolNames()) {
      String category = categoryForSchemaTool(toolName);
      addGraphNode(nodes, "tool:" + toolName, "tool", toolName, category);
      addGraphEdge(edges, "domain:" + category, "tool:" + toolName, "exposes");
      addGraphEdge(edges, "tool:" + toolName, "schema:" + toolName, "hasSchema");
      addGraphEdge(edges, "tool:" + toolName, "example:" + toolName, "hasExample");
    }
    addGraphNode(nodes, "contract:processJsonContract", "contract", "processJsonContract",
        "process");
    addGraphNode(nodes, "contract:unitSystem", "contract", "unitSystem", "validation");
    addGraphNode(nodes, "contract:safetyGatePolicy", "contract", "safetyGatePolicy", "safety");
    addGraphEdge(edges, "contract:processJsonContract", "domain:process", "describes");
    addGraphEdge(edges, "contract:unitSystem", "domain:validation", "constrains");
    addGraphEdge(edges, "contract:safetyGatePolicy", "domain:safety", "governs");
    graph.add("nodes", nodes);
    graph.add("edges", edges);
    graph.addProperty("nodeCount", nodes.size());
    graph.addProperty("edgeCount", edges.size());
    graph.addProperty("scope", "MCP capability graph for agent discovery");
    return graph;
  }

  /**
   * Builds equipment and property ontology metadata for process JSON composition.
   *
   * @return ontology object
   */
  private static JsonObject buildEquipmentPropertyOntology() {
    JsonObject ontology = new JsonObject();
    JsonObject equipment = new JsonObject();
    addEquipmentOntology(equipment, "Stream", Arrays.asList("outlet"),
        Arrays.asList("flowRate", "temperature", "pressure", "fluidRef"), true,
        Arrays.asList("flowRate", "temperature", "pressure"));
    addEquipmentOntology(equipment, "Separator", Arrays.asList("gasOut", "liquidOut"),
        Arrays.asList("entrainment", "mechanicalDesign"), true,
        Arrays.asList("pressure", "gasOutStream.flowRate", "liquidOutStream.flowRate"));
    addEquipmentOntology(equipment, "ThreePhaseSeparator",
        Arrays.asList("gasOut", "oilOut", "waterOut"), Arrays.asList("retentionTime"), true,
        Arrays.asList("gasOutStream.flowRate", "oilOutStream.flowRate", "waterOutStream.flowRate"));
    addEquipmentOntology(equipment, "Compressor", Arrays.asList("outlet"),
        Arrays.asList("outletPressure", "isentropicEfficiency", "polytropicEfficiency"), true,
        Arrays.asList("power", "outletPressure", "isentropicEfficiency"));
    addEquipmentOntology(equipment, "HeatExchanger", Arrays.asList("hx0", "hx1"),
        Arrays.asList("UAvalue", "guessOutTemperature", "hotSide", "coldSide"), false,
        Arrays.asList("duty", "UAvalue", "hotOut.temperature", "coldOut.temperature"));
    addEquipmentOntology(equipment, "PipeBeggsAndBrills", Arrays.asList("outlet"),
        Arrays.asList("diameter", "length", "elevation", "roughness", "numberOfIncrements"), false,
        Arrays.asList("pressureDrop", "outletPressure", "flowRegime"));
    addEquipmentOntology(equipment, "DistillationColumn",
        Arrays.asList("gasOut", "liquidOut", "trayStreams"),
        Arrays.asList("numberOfTrays", "hasReboiler", "hasCondenser", "feedTray"), false,
        Arrays.asList("topTemperature", "bottomTemperature", "reboilerDuty"));
    ontology.add("equipment", equipment);
    ontology.add("propertyDimensions",
        toJsonArray(Arrays.asList("temperature", "pressure", "flowRate", "energy", "power",
            "length", "area", "volume", "time", "efficiency", "composition", "cost")));
    ontology.add("portRules",
        toJsonArray(Arrays.asList("Use name.port for equipment with multiple outlets",
            "Default outlet is 'outlet'",
            "Three-phase separators expose gasOut, oilOut, and waterOut",
            "Heat exchangers expose side-specific outlets such as hx0 and hx1")));
    return ontology;
  }

  /**
   * Builds benchmark registry metadata for agent trust decisions.
   *
   * @return benchmark registry object
   */
  private static JsonObject buildBenchmarkRegistry() {
    JsonObject registry = new JsonObject();
    registry.addProperty("trustTool", "getBenchmarkTrust");
    registry.addProperty("sourceClass", "neqsim.mcp.runners.BenchmarkTrust");
    registry.add("benchmarkedTools",
        toJsonArray(Arrays.asList("runFlash", "runProcess", "runPVT", "runFlowAssurance",
            "calculateStandard", "runPipeline", "runReservoir", "runFieldEconomics", "runBatch",
            "runDynamic", "runBioprocess", "crossValidateModels", "runParametricStudy",
            "getPhaseEnvelope", "getPropertyTable", "sizeEquipment")));
    registry.add("referenceTypes",
        toJsonArray(Arrays.asList("NIST property data", "ISO/GPA/AGA standard examples",
            "published VLE and hydrate references", "mass and energy balance closure",
            "process convergence fixtures")));
    registry.add("requiredTrustFields", toJsonArray(Arrays.asList("maturityLevel", "description",
        "validationCases", "accuracyBounds", "knownLimitations", "referenceData")));
    registry.add("agentPolicy",
        toJsonArray(Arrays.asList(
            "Fetch trust metadata before using results in engineering reports",
            "Downgrade confidence when maturityLevel is TESTED or EXPERIMENTAL",
            "Report knownLimitations together with numeric outputs",
            "Use validateResults or independent benchmark checks for design-sensitive decisions")));
    return registry;
  }

  /**
   * Builds the unit-safe schema system advertised to agents.
   *
   * @return unit system object
   */
  private static JsonObject buildUnitSystem() {
    JsonObject units = new JsonObject();
    JsonObject dimensions = new JsonObject();
    dimensions.add("temperature", toJsonArray(Arrays.asList("K", "C", "F", "R")));
    dimensions.add("pressure",
        toJsonArray(Arrays.asList("bara", "barg", "Pa", "kPa", "MPa", "psi", "psia", "atm")));
    dimensions.add("flowRate",
        toJsonArray(Arrays.asList("kg/hr", "kg/s", "mol/sec", "Sm3/day", "MSm3/day", "m3/hr")));
    dimensions.add("energy", toJsonArray(Arrays.asList("J", "kJ", "J/mol", "J/kg", "kJ/hr")));
    dimensions.add("power", toJsonArray(Arrays.asList("W", "kW", "MW", "hp")));
    dimensions.add("length", toJsonArray(Arrays.asList("m", "cm", "mm", "inch", "ft", "km")));
    dimensions.add("time", toJsonArray(Arrays.asList("s", "seconds", "min", "hr", "day", "year")));
    dimensions.add("cost", toJsonArray(Arrays.asList("USD", "MUSD", "NOK", "MNOK", "EUR")));
    units.add("dimensions", dimensions);
    units.add("encodings",
        toJsonArray(Arrays.asList("{\"value\": 25.0, \"unit\": \"C\"}",
            "[25.0, \"C\"] for process equipment property setters",
            "bare number only when schema default is clear")));
    units.add("validationRules",
        toJsonArray(Arrays.asList("Prefer explicit units in all agent-generated payloads",
            "Validate physical bounds before run",
            "Do not mix absolute and gauge pressure without declaring the unit",
            "Report output units exactly as returned by the tool")));
    return units;
  }

  /**
   * Builds the automatic flowsheet builder workflow descriptor.
   *
   * @return flowsheet builder descriptor
   */
  private static JsonObject buildAutomaticFlowsheetBuilder() {
    JsonObject builder = new JsonObject();
    builder.addProperty("status", "workflowDescriptor");
    builder.add("tools", toJsonArray(Arrays.asList("getCapabilities", "getSchema", "getExample",
        "validateInput", "runProcess", "compareProcesses", "generateReport")));
    builder.add("stages",
        toJsonArray(Arrays.asList("extract fluid composition and conditions",
            "map equipment names and stream topology", "choose EOS and unit system",
            "compose processJsonContract payload", "validateInput", "runProcess",
            "summarize assumptions and unresolved data gaps")));
    builder.add("requiredHumanChecks", toJsonArray(Arrays.asList("component mapping confidence",
        "missing operating envelope", "equipment design assumptions", "standards applicability")));
    return builder;
  }

  /**
   * Builds optimization, sensitivity, and uncertainty workflow descriptors.
   *
   * @return optimization and uncertainty descriptor
   */
  private static JsonObject buildOptimizationUncertaintyWorkflows() {
    JsonObject workflows = new JsonObject();
    workflows.add("standardTools", toJsonArray(Arrays.asList("runParametricStudy", "runBatch",
        "crossValidateModels", "compareProcesses", "runFieldEconomics")));
    workflows.add("patterns",
        toJsonArray(Arrays.asList("one-factor-at-a-time sensitivity", "multi-case batch sweep",
            "EOS model risk comparison", "scenario comparison",
            "Monte Carlo or P10/P50/P90 post-processing", "tornado ranking for report outputs")));
    workflows.add("requiredOutputs", toJsonArray(Arrays.asList("baseCase", "caseResults",
        "uncertainParameters", "outputMetrics", "ranking", "warnings")));
    workflows.add("agentPolicy",
        toJsonArray(
            Arrays.asList("Use full NeqSim simulations inside uncertainty loops when practical",
                "Cache expensive base simulations only when physics assumptions remain fixed",
                "Separate technical uncertainty from economic scenario uncertainty")));
    return workflows;
  }

  /**
   * Builds model lifecycle metadata for stateful and auditable agent workflows.
   *
   * @return model lifecycle object
   */
  private static JsonObject buildModelLifecycle() {
    JsonObject lifecycle = new JsonObject();
    lifecycle.add("tools",
        toJsonArray(Arrays.asList("manageSession", "saveSimulationState", "compareSimulationStates",
            "manageState", "listSimulationUnits", "listUnitVariables", "getSimulationVariable",
            "setSimulationVariable")));
    lifecycle.add("stateOperations", toJsonArray(Arrays.asList("create", "load", "snapshot",
        "restore", "diff", "modify", "rerun", "export")));
    lifecycle.add("auditFields", toJsonArray(Arrays.asList("sessionId", "modelVersion", "timestamp",
        "changedVariables", "validation", "qualityGate", "provenance")));
    lifecycle.add("guardrails",
        toJsonArray(Arrays.asList("Snapshot before mutating a process",
            "Compare snapshots after agent changes",
            "Preserve unit annotations for every modified input",
            "Do not persist plant states when governance blocks write operations")));
    return lifecycle;
  }

  /**
   * Builds certification-style safety gate metadata.
   *
   * @return safety gate policy object
   */
  private static JsonObject buildSafetyGatePolicy() {
    JsonObject policy = new JsonObject();
    policy.addProperty("engineeringReviewRequired", true);
    policy.add("safetyTools",
        toJsonArray(Arrays.asList("runRelief", "runLOPA", "runSIL", "runRiskMatrix",
            "runFlareNetwork", "runHAZOP", "runBarrierRegister", "runSafetySystemPerformance")));
    policy.add("approvalStages",
        toJsonArray(Arrays.asList("input evidence completeness", "standards and jurisdiction check",
            "calculation convergence and bounds check", "independent discipline review",
            "management of change approval where required")));
    policy.add("blockingConditions",
        toJsonArray(Arrays.asList("missing source documents", "unsupported standard basis",
            "failed validation", "qualityGate verdict failed",
            "safety-critical result without evidenceRefs")));
    policy.add("outputPolicy",
        toJsonArray(Arrays.asList("Clearly separate screening calculation from approved design",
            "Carry evidenceRefs forward",
            "Include assumptions, limitations, and benchmark maturity in reports")));
    return policy;
  }

  /**
   * Adds a graph node.
   *
   * @param nodes graph node array
   * @param id node id
   * @param type node type
   * @param label node label
   * @param group node group
   */
  private static void addGraphNode(JsonArray nodes, String id, String type, String label,
      String group) {
    JsonObject node = new JsonObject();
    node.addProperty("id", id);
    node.addProperty("type", type);
    node.addProperty("label", label);
    node.addProperty("group", group);
    nodes.add(node);
  }

  /**
   * Adds a graph edge.
   *
   * @param edges graph edge array
   * @param from source node id
   * @param to target node id
   * @param relation edge relation
   */
  private static void addGraphEdge(JsonArray edges, String from, String to, String relation) {
    JsonObject edge = new JsonObject();
    edge.addProperty("from", from);
    edge.addProperty("to", to);
    edge.addProperty("relation", relation);
    edges.add(edge);
  }

  /**
   * Adds equipment ontology metadata.
   *
   * @param equipment equipment object
   * @param type equipment type
   * @param ports outlet or stream ports
   * @param properties common settable properties
   * @param dynamicSupport whether dynamic simulation is commonly supported
   * @param variables common automation variables
   */
  private static void addEquipmentOntology(JsonObject equipment, String type, List<String> ports,
      List<String> properties, boolean dynamicSupport, List<String> variables) {
    JsonObject item = new JsonObject();
    item.add("ports", toJsonArray(ports));
    item.add("properties", toJsonArray(properties));
    item.addProperty("dynamicSupport", dynamicSupport);
    item.add("automationVariables", toJsonArray(variables));
    equipment.add(type, item);
  }

  /**
   * Categorizes a schema tool name for the capability graph.
   *
   * @param toolName schema tool name
   * @return graph domain category
   */
  private static String categoryForSchemaTool(String toolName) {
    if (toolName.indexOf("flash") >= 0 || toolName.indexOf("property") >= 0
        || toolName.indexOf("phase") >= 0 || toolName.indexOf("batch") >= 0) {
      return "thermodynamics";
    }
    if (toolName.indexOf("process") >= 0 || toolName.indexOf("equipment") >= 0) {
      return "process";
    }
    if (toolName.indexOf("simulation") >= 0 || toolName.indexOf("automation") >= 0) {
      return "automation";
    }
    if (toolName.indexOf("safety") >= 0 || toolName.indexOf("hazop") >= 0
        || toolName.indexOf("barrier") >= 0 || toolName.indexOf("relief") >= 0
        || toolName.indexOf("lopa") >= 0 || toolName.indexOf("sil") >= 0
        || toolName.indexOf("risk") >= 0 || toolName.indexOf("flare") >= 0) {
      return "safety";
    }
    if (toolName.indexOf("benchmark") >= 0 || toolName.indexOf("validate") >= 0) {
      return "benchmark";
    }
    if (toolName.indexOf("session") >= 0 || toolName.indexOf("state") >= 0) {
      return "lifecycle";
    }
    if (toolName.indexOf("workflow") >= 0 || toolName.indexOf("task") >= 0) {
      return "workflow";
    }
    if (toolName.indexOf("dynamic") >= 0) {
      return "dynamic";
    }
    if (toolName.indexOf("pvt") >= 0) {
      return "pvt";
    }
    if (toolName.indexOf("flow_assurance") >= 0) {
      return "flow-assurance";
    }
    if (toolName.indexOf("pipeline") >= 0) {
      return "pipeline";
    }
    if (toolName.indexOf("economics") >= 0) {
      return "economics";
    }
    if (toolName.indexOf("schema") >= 0 || toolName.indexOf("capabilities") >= 0
        || toolName.indexOf("example") >= 0) {
      return "discovery";
    }
    if (toolName.indexOf("data") >= 0 || toolName.indexOf("components") >= 0) {
      return "data";
    }
    return "platform";
  }

  /**
   * Returns setup template ids.
   *
   * @return list of setup template ids
   */
  private static List<String> getSetupTemplateIds() {
    return Arrays.asList("capability-discovery", "thermodynamic-flash", "property-sweep",
        "phase-envelope", "process-system", "process-model", "validation", "data-catalog",
        "pvt-experiment", "flow-assurance", "standards-calculation", "pipeline-flow",
        "reservoir-forecast", "field-economics", "dynamic-simulation", "bioprocess",
        "equipment-sizing", "session-management", "visualization-reporting", "safety-analysis",
        "flowsheet-builder", "benchmark-registry", "unit-safe-schema", "optimization-uncertainty",
        "model-lifecycle", "safety-governance");
  }

  /**
   * Returns supported EOS model names used by common thermodynamic tools.
   *
   * @return model names
   */
  private static List<String> eosModels() {
    return Arrays.asList("SRK", "PR", "CPA", "GERG2008", "PCSAFT", "UMRPRU");
  }

  /**
   * Returns common thermodynamic units.
   *
   * @return unit names
   */
  private static List<String> thermoUnits() {
    return Arrays.asList("K", "C", "F", "bara", "barg", "Pa", "kPa", "MPa", "psi", "J/mol", "J/kg",
        "J/molK", "m3/mol");
  }

  /**
   * Returns common process units.
   *
   * @return unit names
   */
  private static List<String> processUnits() {
    return Arrays.asList("K", "C", "bara", "barg", "kg/hr", "kg/s", "mol/sec", "Sm3/day", "m", "m2",
        "m3", "kJ/hr", "kW", "seconds");
  }

  /**
   * Returns validation-specific units.
   *
   * @return unit names
   */
  private static List<String> validationUnits() {
    return Arrays.asList("schema units", "tool-specific units", "SI where unspecified");
  }

  /**
   * Creates an array containing one string.
   *
   * @param value the value to include
   * @return JSON array
   */
  private static JsonArray stringArray(String value) {
    JsonArray array = new JsonArray();
    array.add(value);
    return array;
  }

  /**
   * Converts a list of strings to a JsonArray.
   *
   * @param items the string items
   * @return the JsonArray
   */
  private static JsonArray toJsonArray(List<String> items) {
    JsonArray array = new JsonArray();
    for (String item : items) {
      array.add(item);
    }
    return array;
  }
}
