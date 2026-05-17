package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
            "Operational tag maps and P&ID-derived scenario runners (neqsim.process.operations)",
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
        "runMaterialsReview — Process-wide material selection, degradation, CUI, and remaining-life review",
        "runOpenDrainReview — NORSOK S-001 Clause 9 open-drain review from normalized STID/P&ID and optional tagreader evidence",
        "runNorsokS001Clause10Review — NORSOK S-001 Clause 10 process safety system review from normalized C&E, SRS, PSV, instrument, tagreader, and optional dynamic simulation evidence",
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
        "runOperationalStudy — P&ID/tag-driven valve scenarios, controller metrics, "
            + "and operating-envelope margin/trip screening",
        "getCapabilities — This tool (discovery)")));
    modes.addProperty("quickCalculation",
        "Use runFlash or getPropertyTable for single-query answers. "
            + "No process flowsheet required.");
    modes.addProperty("fullSimulation", "Use runProcess for multi-equipment flowsheet simulation. "
        + "Returns complete stream and equipment results.");
    root.add("calculationModes", modes);

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

    return GSON.toJson(root);
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
