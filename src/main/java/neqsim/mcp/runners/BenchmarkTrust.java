package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Benchmark trust metadata for each MCP tool.
 *
 * <p>
 * Provides per-tool validation status, known accuracy ranges, reference validation cases, known
 * limitations, and unsupported regions. This is the "trust page" that industrial users require
 * before relying on a tool for engineering decisions.
 * </p>
 *
 * <p>
 * Each tool entry includes:
 * </p>
 * <ul>
 * <li><b>maturityLevel</b>: VALIDATED, TESTED, EXPERIMENTAL</li>
 * <li><b>validationCases</b>: Reference cases with expected results and tolerances</li>
 * <li><b>accuracyBounds</b>: Expected accuracy for different fluid types and conditions</li>
 * <li><b>knownLimitations</b>: Conditions where the tool may give poor results</li>
 * <li><b>referenceData</b>: Published sources used for validation</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class BenchmarkTrust {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Tool maturity level.
   */
  public enum MaturityLevel {
    /** Validated against published reference data with known accuracy bounds. */
    VALIDATED,
    /** Covered by unit tests and integration tests, not yet fully benchmark-validated. */
    TESTED,
    /** Functional but interface or accuracy may change. */
    EXPERIMENTAL
  }

  /**
   * Private constructor — utility class.
   */
  private BenchmarkTrust() {}

  /**
   * Returns the full trust report for all tools.
   *
   * @return JSON string with per-tool trust metadata
   */
  public static String getTrustReport() {
    JsonObject root = new JsonObject();
    root.addProperty("status", "success");
    root.addProperty("description",
        "Per-tool validation status, accuracy bounds, reference data, and known limitations. "
            + "Industrial users should review this before relying on results for design decisions.");

    JsonObject tools = new JsonObject();
    tools.add("runFlash", buildFlashTrust());
    tools.add("runProcess", buildProcessTrust());
    tools.add("runPVT", buildPVTTrust());
    tools.add("runFlowAssurance", buildFlowAssuranceTrust());
    tools.add("calculateStandard", buildStandardsTrust());
    tools.add("runPipeline", buildPipelineTrust());
    tools.add("runWaterHammer", buildWaterHammerTrust());
    tools.add("runRootCauseAnalysis", buildRootCauseTrust());
    tools.add("runMaterialsReview", buildMaterialsReviewTrust());
    tools.add("runReservoir", buildReservoirTrust());
    tools.add("runFieldEconomics", buildEconomicsTrust());
    tools.add("runDynamic", buildDynamicTrust());
    tools.add("runBioprocess", buildBioprocessTrust());
    tools.add("crossValidateModels", buildCrossValidationTrust());
    tools.add("runParametricStudy", buildParametricTrust());
    tools.add("getPhaseEnvelope", buildPhaseEnvelopeTrust());
    tools.add("getPropertyTable", buildPropertyTableTrust());
    tools.add("sizeEquipment", buildEquipmentSizingTrust());

    root.add("tools", tools);
    return GSON.toJson(root);
  }

  /**
   * Returns the trust report for a specific tool.
   *
   * @param toolName the MCP tool name
   * @return JSON string with trust metadata for that tool
   */
  public static String getToolTrust(String toolName) {
    JsonObject root = new JsonObject();
    root.addProperty("status", "success");
    root.addProperty("tool", toolName);

    switch (toolName) {
      case "runFlash":
        root.add("trust", buildFlashTrust());
        break;
      case "runProcess":
        root.add("trust", buildProcessTrust());
        break;
      case "runPVT":
        root.add("trust", buildPVTTrust());
        break;
      case "runFlowAssurance":
        root.add("trust", buildFlowAssuranceTrust());
        break;
      case "calculateStandard":
        root.add("trust", buildStandardsTrust());
        break;
      case "runPipeline":
        root.add("trust", buildPipelineTrust());
        break;
      case "runWaterHammer":
        root.add("trust", buildWaterHammerTrust());
        break;
      case "runRootCauseAnalysis":
        root.add("trust", buildRootCauseTrust());
        break;
      case "runMaterialsReview":
        root.add("trust", buildMaterialsReviewTrust());
        break;
      case "runReservoir":
        root.add("trust", buildReservoirTrust());
        break;
      case "runFieldEconomics":
        root.add("trust", buildEconomicsTrust());
        break;
      case "runDynamic":
        root.add("trust", buildDynamicTrust());
        break;
      case "runBioprocess":
        root.add("trust", buildBioprocessTrust());
        break;
      default:
        root.addProperty("maturityLevel", "TESTED");
        root.addProperty("note", "No specific benchmark data available for this tool. "
            + "Results should be reviewed by a qualified engineer.");
        break;
    }

    return GSON.toJson(root);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Per-tool trust builders
  // ═══════════════════════════════════════════════════════════════════════════

  private static JsonObject buildFlashTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "VALIDATED");
    trust.addProperty("description",
        "Flash calculations using well-established cubic equations of state. "
            + "SRK and PR are validated against published VLE data for common hydrocarbons. "
            + "GERG-2008 is the ISO reference model for natural gas.");

    // Validation cases
    JsonArray cases = new JsonArray();
    cases.add(validationCase("Methane density at 25C, 100 bara", "SRK",
        "Density within 15% of NIST reference", "NIST Chemistry WebBook"));
    cases.add(validationCase("Methane-ethane VLE at 50 bara", "SRK",
        "Phase compositions within 1 mol% of Wichterle et al.", "Wichterle et al. (1972)"));
    cases.add(validationCase("Natural gas dew point at 50 bara", "SRK",
        "Dew point T within 1K of experimental data", "NeqSim JUnit test suite"));
    cases.add(validationCase("CO2-methane binary at 230K", "SRK",
        "Dew/bubble pressure within 3% of Davalos et al.", "Davalos et al. (1976)"));
    cases.add(validationCase("Water-methane hydrate equilibrium", "CPA",
        "Hydrate T within 1K of Sloan & Koh data", "Sloan & Koh (2008)"));
    trust.add("validationCases", cases);

    // Accuracy bounds
    JsonObject accuracy = new JsonObject();
    accuracy.addProperty("lightHydrocarbons",
        "Density ±2%, VLE compositions ±1 mol%, dew/bubble point ±1K");
    accuracy.addProperty("heavyHydrocarbons",
        "Liquid density ±3-5% (SRK without Peneloux), improve with volume translation");
    accuracy.addProperty("CO2Systems",
        "Dense phase density ±2-3% (SRK/PR), ±1% with GERG-2008 for pipeline-grade CO2");
    accuracy.addProperty("polarSystems",
        "Water content in gas ±10-20% (SRK), ±5% (CPA). Always use CPA for water/glycol.");
    accuracy.addProperty("highPressure",
        "Above 500 bara, all cubic EOS lose accuracy. Use GERG-2008 for natural gas at high P.");
    trust.add("accuracyBounds", accuracy);

    // Known limitations
    JsonArray limitations = new JsonArray();
    limitations.add("SRK/PR liquid density can be 5-15% off without volume translation");
    limitations.add("Hydrate predictions require the CPA EOS with water as a component");
    limitations.add("Near-critical region: all EOS have reduced accuracy");
    limitations.add("Electrolyte systems require SystemElectrolyteCPAstatoil");
    limitations.add("Heavy C20+ fractions need characterization before flash");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildProcessTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "VALIDATED");
    trust.addProperty("description", "Process simulation using sequential-modular approach. "
        + "Validated against commercial simulators for standard equipment.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("HP/LP separation train", "SRK",
        "Stream compositions within 1% of UniSim reference", "UniSim TUTOR1 comparison"));
    cases.add(validationCase("3-stage compression with intercooling", "SRK",
        "Power consumption within 3% of design data", "NeqSim CompressorTest suite"));
    cases.add(validationCase("JT valve cooling", "SRK",
        "Outlet temperature within 0.5K of isenthalpic flash", "Thermodynamic identity"));
    trust.add("validationCases", cases);

    JsonObject accuracy = new JsonObject();
    accuracy.addProperty("separators", "Mass balance closure <0.01%");
    accuracy.addProperty("compressors", "Power ±3% (polytropic efficiency model vs vendor curves)");
    accuracy.addProperty("heatExchangers", "Duty ±1-2% vs energy balance check");
    accuracy.addProperty("distillation",
        "Converged column: compositions ±1-2% vs rigorous INSIDE-OUT solver");
    trust.add("accuracyBounds", accuracy);

    JsonArray limitations = new JsonArray();
    limitations.add("Distillation column convergence can fail for highly non-ideal systems");
    limitations.add("Recycle loops may require manual convergence tuning");
    limitations.add("Dynamic simulation timestep must be small enough for controller stability");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildPVTTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "VALIDATED");
    trust.addProperty("description",
        "PVT laboratory simulations validated against experimental data.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("CME on North Sea gas condensate", "SRK",
        "Relative volume within 1% of lab data above saturation", "Industry PVT reports"));
    cases.add(validationCase("Saturation pressure of black oil", "PR",
        "Psat within 3% of measured value after kij tuning", "Standard PVT matching workflow"));
    trust.add("validationCases", cases);

    JsonArray limitations = new JsonArray();
    limitations.add("Accuracy depends heavily on C7+ characterization quality");
    limitations.add("Untuned EOS may give 5-15% error on saturation pressure");
    limitations.add("Viscosity correlations have 10-30% uncertainty");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildFlowAssuranceTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "TESTED");
    trust.addProperty("description",
        "Flow assurance screening tools. Hydrate prediction is validated; "
            + "wax and asphaltene are screening-level.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("Hydrate equilibrium T for methane+water", "CPA",
        "Within 1K of Sloan data", "Sloan & Koh (2008)"));
    cases.add(validationCase("CO2 corrosion rate estimate", "SRK",
        "Within factor of 2 of de Waard correlation", "de Waard et al. (1991)"));
    trust.add("validationCases", cases);

    JsonArray limitations = new JsonArray();
    limitations.add("Wax appearance temperature is a screening estimate only");
    limitations.add("Asphaltene onset depends heavily on fluid characterization");
    limitations.add("Corrosion rates are estimates — use with safety margin");
    limitations.add("Pipeline cooldown is simplified (1D heat transfer)");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildStandardsTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "VALIDATED");
    trust.addProperty("description",
        "Gas/oil quality calculations per published industry standards. "
            + "ISO 6976 implementation validated against published reference values.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("ISO 6976 heating value for pure methane", "SRK",
        "Within 0.1% of published reference value", "ISO 6976:2016"));
    cases.add(validationCase("Wobbe index for typical natural gas", "SRK",
        "Within 0.5% of custody transfer reference", "ISO 6976:2016 worked examples"));
    trust.add("validationCases", cases);

    JsonArray limitations = new JsonArray();
    limitations.add("Standards cover specific composition ranges — results outside "
        + "the standard's applicability range carry a warning");
    limitations.add("Some standards require specific input conditions (metering T, P)");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildPipelineTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "TESTED");
    trust.addProperty("description", "Multiphase pipeline flow using Beggs & Brill correlation. "
        + "Validated for vertical and horizontal flow.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("Single-phase gas pipeline", "SRK",
        "Pressure drop within 5% of analytical Darcy-Weisbach", "Standard pipe flow textbooks"));
    cases.add(validationCase("Multiphase flow in vertical well", "SRK",
        "Pressure profile within 10% of field data", "Beggs & Brill (1973) correlation basis"));
    trust.add("validationCases", cases);

    JsonArray limitations = new JsonArray();
    limitations.add("Beggs & Brill has 20-30% uncertainty for multiphase pressure drop");
    limitations.add("Slug flow prediction is approximate");
    limitations.add("Not suitable for very high GVF (>0.99) or very low GVF (<0.01)");
    limitations.add("Does not model terrain effects (slug catcher sizing)");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  /**
   * Builds trust metadata for the water-hammer screening tool.
   *
   * @return JSON object with trust metadata
   */
  private static JsonObject buildWaterHammerTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "TESTED");
    trust.addProperty("description",
        "Water-hammer / liquid-hammer screening using a "
            + "single-line Method of Characteristics transient model. Suitable for fast ranking "
            + "of valve closure and pump-trip scenarios before a detailed surge study.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("Joukowsky pressure rise for instantaneous closure", "SRK",
        "Peak pressure envelope consistent with rho*a*deltaV estimate",
        "Classic water-hammer theory"));
    cases.add(validationCase("Courant-limited transient stability", "MOC",
        "Stable time step follows dx/a condition", "NeqSim JUnit test suite"));
    trust.add("validationCases", cases);

    JsonArray limitations = new JsonArray();
    limitations.add("Equivalent single-line model; split varying-diameter routes into sections");
    limitations.add("Valve closure curves are linear unless event schedule provides alternatives");
    limitations.add("Support loads, pipe stress, vapor cavity collapse, and detailed pump curves "
        + "require specialist surge software or vendor data");
    limitations.add("Use as a screening and evidence-pack tool, not as final design approval");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  /**
   * Builds trust metadata for the materials review tool.
   *
   * @return JSON object with trust metadata
   */
  private static JsonObject buildMaterialsReviewTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "TESTED");
    trust.addProperty("description",
        "Process-wide materials review orchestrating tested NeqSim corrosion calculators "
            + "(NORSOK M-506, NORSOK M-001, ISO 15156/NACE MR0175, chloride SCC, "
            + "oxygen corrosion, dense CO2, hydrogen, ammonia, CUI, and remaining-life screening). "
            + "Suitable for screening, challenge/support work, and STID-backed review packages.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("Wet CO2/H2S line material review", "SRK/process register",
        "Identifies CO2 corrosion, sour service, material recommendation, and corrosion allowance",
        "NORSOK M-001/M-506 regression tests"));
    cases.add(validationCase("Insulated chloride service", "STID register",
        "Identifies chloride SCC, oxygen corrosion, CUI risk, and inspection interval",
        "API 581/API 583 and corrosion unit tests"));
    trust.add("validationCases", cases);

    JsonArray limitations = new JsonArray();
    limitations
        .add("Screening-level; final materials selection requires discipline engineer approval");
    limitations.add("Quality depends on normalized STID/materials-register completeness");
    limitations.add("API 579/API 581 quantitative RBI is not fully implemented in this runner");
    limitations.add("Document retrieval and OCR are handled outside the Java runner");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  /**
   * Builds trust metadata for the root-cause analysis tool.
   *
   * @return JSON object with trust metadata
   */
  private static JsonObject buildRootCauseTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "TESTED");
    trust.addProperty("description", "Equipment root-cause analysis that combines OREDA-style "
        + "priors, hypothesis-specific historian/STID evidence fingerprints, and conservative "
        + "process-simulation perturbation checks. Suitable for operations troubleshooting and "
        + "shift-to-shift investigation support, not for replacing discipline engineer review.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("Compressor high vibration", "SRK process model",
        "Ranks bearing degradation, rotor imbalance, liquid ingestion, and misalignment using "
            + "vibration/level/bearing-temperature evidence patterns",
        "RootCauseAnalyzerTest and OREDA-style hypothesis library"));
    cases.add(validationCase("Compressor efficiency degradation", "SRK process model",
        "Applies compressor efficiency perturbation and compares KPI direction with historian tags",
        "SimulationVerifier regression tests"));
    cases.add(validationCase("Separator liquid carryover", "Process historian + STID",
        "Uses level, demister differential pressure, feed-rate, and carryover fingerprints",
        "Root-cause diagnosis package tests"));
    trust.add("validationCases", cases);

    JsonArray limitations = new JsonArray();
    limitations
        .add("Confidence scores are Bayesian-inspired rankings, not calibrated " + "probabilities");
    limitations.add("Quality depends on historian tag mapping, data quality, and STID/design-limit "
        + "completeness");
    limitations.add("Simulation verification is limited to supported perturbations and reports "
        + "neutral when unsupported");
    limitations.add("Final operating decisions require qualified rotating-equipment, process, "
        + "or control-system review");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildReservoirTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "TESTED");
    trust.addProperty("description",
        "Material balance reservoir simulation (tank model). Suitable for "
            + "screening-level resource estimation, not full reservoir engineering.");

    JsonArray limitations = new JsonArray();
    limitations.add("Tank model only — no spatial heterogeneity");
    limitations.add("Suitable for screening and early-phase studies, not detailed modeling");
    limitations.add("Water influx model is simplified");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildEconomicsTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "TESTED");
    trust.addProperty("description", "Field development economics with standard DCF methodology. "
        + "Fiscal regimes implemented per published tax laws.");

    JsonArray cases = new JsonArray();
    cases.add(validationCase("Norwegian NCS tax model", "N/A",
        "Marginal tax rate matches 78% (22% + 56%) NCS regime", "Norwegian Petroleum Tax Act"));
    trust.add("validationCases", cases);

    JsonArray limitations = new JsonArray();
    limitations.add("Tax law changes annually — verify against current legislation");
    limitations.add("Cost estimates are Class 5/4 (screening/feasibility) accuracy");
    limitations.add("Decline curves are idealized — actual production may differ");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildDynamicTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "EXPERIMENTAL");
    trust.addProperty("description", "Dynamic transient simulation with PID controllers. "
        + "Suitable for controller tuning and startup analysis.");

    JsonArray limitations = new JsonArray();
    limitations.add("Auto-instrumented controllers use default tuning parameters");
    limitations.add("Timestep must be appropriately small for numerical stability");
    limitations.add("Equipment volume and holdup assumptions are simplified");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildBioprocessTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "EXPERIMENTAL");
    trust.addProperty("description",
        "Bioprocess reactor simulations using published kinetic models.");

    JsonArray limitations = new JsonArray();
    limitations.add("Kinetic parameters are literature defaults — may need calibration");
    limitations.add("Anaerobic digester yield depends heavily on feedstock characterization");
    limitations.add("Gasification and pyrolysis are simplified equilibrium models");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildCrossValidationTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "VALIDATED");
    trust.addProperty("description", "Cross-validates a process across multiple EOS models. "
        + "Quantifies model selection risk for design decisions.");

    JsonArray limitations = new JsonArray();
    limitations.add("Spread between models does not guarantee accuracy envelope");
    limitations.add("All models share common component databases — systematic bias possible");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildParametricTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "VALIDATED");
    trust.addProperty("description", "Parametric sweep using repeated process simulation. "
        + "Accuracy inherits from the underlying tool being swept.");

    JsonArray limitations = new JsonArray();
    limitations.add("Large sweeps may take significant computation time");
    limitations.add("Non-converged cases are reported but may bias statistics");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildPhaseEnvelopeTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "VALIDATED");
    trust.addProperty("description",
        "PT phase envelope calculation. Validated for hydrocarbon mixtures.");

    JsonArray limitations = new JsonArray();
    limitations.add("Near-critical region may have gaps in the envelope curve");
    limitations.add("Three-phase boundaries (water + hydrate) not always smooth");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildPropertyTableTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "VALIDATED");
    trust.addProperty("description",
        "Property sweep across T or P range. Same accuracy as runFlash at each point.");

    JsonArray limitations = new JsonArray();
    limitations.add("Phase transitions may cause discontinuities in property values");
    limitations.add("Transport properties (viscosity) have larger uncertainty than "
        + "thermodynamic properties");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  private static JsonObject buildEquipmentSizingTrust() {
    JsonObject trust = new JsonObject();
    trust.addProperty("maturityLevel", "TESTED");
    trust.addProperty("description", "Quick equipment sizing using standard correlations "
        + "(Souders-Brown for separators, polytropic model for compressors).");

    JsonArray limitations = new JsonArray();
    limitations.add("Separator sizing is screening-level (Souders-Brown K-factor)");
    limitations.add("Does not account for internals, mist eliminators, or inlet devices");
    limitations.add("Compressor staging is rule-of-thumb, not optimized");
    trust.add("knownLimitations", limitations);

    return trust;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Builds a validation case entry.
   *
   * @param description case description
   * @param model EOS model used
   * @param expectedResult accuracy expectation
   * @param referenceSource published reference
   * @return JSON object for the validation case
   */
  private static JsonObject validationCase(String description, String model, String expectedResult,
      String referenceSource) {
    JsonObject vc = new JsonObject();
    vc.addProperty("description", description);
    vc.addProperty("model", model);
    vc.addProperty("expectedResult", expectedResult);
    vc.addProperty("referenceSource", referenceSource);
    return vc;
  }
}
