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
            "JSON-based process definition (ProcessSystem.fromJsonAndRun)",
            "State save/restore/compare for version tracking",
            "Simulation quality gate (mass/energy balance validation)")));
    root.add("processSimulation", process);

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
        "calculateStandard — Gas/oil quality per 22 industry standards (ISO, AGA, GPA, EN, ASTM)",
        "runPipeline — Multiphase pipeline flow simulation (Beggs & Brill)",
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
    return tools;
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
    descriptor.addProperty("runnerClass", "neqsim.mcp.runners." + runnerClass);
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
   * Returns setup template ids.
   *
   * @return list of setup template ids
   */
  private static List<String> getSetupTemplateIds() {
    return Arrays.asList("capability-discovery", "thermodynamic-flash", "property-sweep",
        "phase-envelope", "process-system", "process-model", "validation", "data-catalog",
        "pvt-experiment", "flow-assurance", "standards-calculation", "pipeline-flow",
        "reservoir-forecast", "field-economics", "dynamic-simulation", "bioprocess",
        "equipment-sizing", "session-management", "visualization-reporting", "safety-analysis");
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
