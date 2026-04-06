package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.Collections;
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

  /** Cached capabilities JSON (built once). */
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
    modes.add("available",
        toJsonArray(Arrays.asList("runFlash — Single flash calculation (9 flash types x 6 EOS)",
            "runBatch — Multiple flash calculations in one call (sensitivity studies)",
            "runProcess — Full process simulation from JSON",
            "getPropertyTable — Sweep T or P and get property table",
            "getPhaseEnvelope — Calculate PT phase envelope",
            "searchComponents — Component database search",
            "validateInput — Pre-flight input validation",
            "getCapabilities — This tool (discovery)")));
    modes.addProperty("quickCalculation",
        "Use runFlash or getPropertyTable for single-query answers. "
            + "No process flowsheet required.");
    modes.addProperty("fullSimulation", "Use runProcess for multi-equipment flowsheet simulation. "
        + "Returns complete stream and equipment results.");
    root.add("calculationModes", modes);

    // --- Engineering Domains ---
    JsonObject domains = new JsonObject();
    domains.add("supported",
        toJsonArray(Arrays.asList("Natural gas processing (dehydration, NGL recovery, compression)",
            "Oil processing (separation, stabilization)",
            "CO2 capture, transport, and storage (CCS)",
            "Hydrogen systems (blending, electrolysis, transport)",
            "Flow assurance (hydrate, wax, corrosion, pipeline hydraulics)",
            "PVT analysis (CME, CVD, differential liberation, swelling)",
            "Mechanical design (pipelines, vessels, wells per ASME/API/DNV/NORSOK)",
            "Safety (depressurization, PSV sizing, source terms)",
            "Power generation (gas turbines, steam cycles, HRSG)",
            "Heat integration (pinch analysis)")));
    root.add("engineeringDomains", domains);

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
