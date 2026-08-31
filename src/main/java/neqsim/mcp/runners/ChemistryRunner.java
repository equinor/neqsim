package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.chemistry.corrosion.LangmuirInhibitorIsotherm;
import neqsim.process.chemistry.corrosion.MechanisticCorrosionModel;
import neqsim.process.chemistry.scale.ElectrolyteScaleCalculator;
import neqsim.process.chemistry.scavenger.PackedBedScavengerReactor;
import neqsim.pvtsimulation.flowassurance.MultiMineralScaleEquilibrium;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PitzerNeutralParameterCoverage;
import neqsim.thermo.phase.PitzerParameterCoverage;
import neqsim.thermo.phase.PitzerParameterDatasets;
import neqsim.thermo.phase.PitzerParameterQualification;
import neqsim.thermo.phase.PitzerParameterQualification.ValidationTarget;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.saturationops.MultiSaltPrecipitationResult;
import neqsim.thermodynamicoperations.flashops.saturationops.SaltPrecipitationResult;

/**
 * Stateless chemistry-and-integrity runner for MCP integration.
 *
 * <p>
 * Exposes the open standards-traceable chemistry stack — electrolyte scale prediction, mechanistic corrosion (NORSOK
 * M-506 + Nesic mass transfer + Langmuir inhibitor), Langmuir inhibitor isotherm dosing, packed-bed H2S scavenger
 * breakthrough, activity-consistent electrolyte scale equilibrium, and fail-closed Pitzer dataset qualification — as
 * JSON-driven analyses usable by AI agents over the Model Context Protocol.
 *
 * <p>
 * All analyses follow the same pattern: agents pass an {@code analysis} field naming the routine and a flat object with
 * the required parameters; the runner returns {@code {status, analysis, data, provenance}} on success or a structured
 * error object with a remediation hint on failure.
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemistryRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls()
      .serializeSpecialFloatingPointValues().create();

  private static final List<String> SUPPORTED_ANALYSES = Collections
      .unmodifiableList(Arrays.asList("electrolyteScale", "multiMineralScale", "mechanisticCorrosion",
          "langmuirInhibitor", "packedBedScavenger", "electrolyteScaleEquilibrium",
          "electrolyteMultiScaleEquilibrium", "pitzerQualification"));

  /**
   * Private constructor — static utility class.
   */
  private ChemistryRunner() {
  }

  /**
   * Returns the list of supported chemistry analyses.
   *
   * @return unmodifiable list of analysis names
   */
  public static List<String> getSupportedAnalyses() {
    return SUPPORTED_ANALYSES;
  }

  /**
   * Runs a chemistry analysis from a JSON input string.
   *
   * @param json the JSON analysis specification
   * @return JSON string with status, analysis name, data and provenance
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty", "Provide a valid JSON chemistry specification");
    }
    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(), "Ensure the JSON is well-formed");
    }
    if (!input.has("analysis")) {
      return errorJson("MISSING_ANALYSIS", "No 'analysis' field specified",
          "Provide 'analysis': one of " + SUPPORTED_ANALYSES);
    }
    String analysis = input.get("analysis").getAsString();
    if (!SUPPORTED_ANALYSES.contains(analysis)) {
      return errorJson("UNKNOWN_ANALYSIS", "Unknown analysis type: " + analysis, "Use one of: " + SUPPORTED_ANALYSES);
    }
    long start = System.currentTimeMillis();
    try {
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("analysis", analysis);
      JsonObject data;
      switch (analysis) {
      case "electrolyteScale":
        data = runElectrolyteScale(input);
        break;
      case "multiMineralScale":
        data = runMultiMineralScale(input);
        break;
      case "mechanisticCorrosion":
        data = runMechanisticCorrosion(input);
        break;
      case "langmuirInhibitor":
        data = runLangmuirInhibitor(input);
        break;
      case "packedBedScavenger":
        data = runPackedBedScavenger(input);
        break;
      case "electrolyteScaleEquilibrium":
        data = runElectrolyteScaleEquilibrium(input);
        break;
      case "electrolyteMultiScaleEquilibrium":
        data = runElectrolyteMultiScaleEquilibrium(input);
        break;
      case "pitzerQualification":
        data = runPitzerQualification(input);
        break;
      default:
        return errorJson("UNKNOWN_ANALYSIS", "Not implemented: " + analysis, "");
      }
      result.add("data", data);
      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("chemistry: " + analysis);
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - start);
      result.add("provenance", GSON.toJsonTree(provenance));
      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("CHEMISTRY_ERROR", "Analysis failed: " + e.getMessage(),
          "Check input parameters for " + analysis);
    }
  }

  // ─── Analyses ──────────────────────────────────────────

  private static JsonObject runElectrolyteScale(JsonObject input) {
    ElectrolyteScaleCalculator calc = new ElectrolyteScaleCalculator()
        .setTemperatureCelsius(d(input, "temperature_C", 60.0)).setPressureBara(d(input, "pressure_bara", 50.0))
        .setPH(d(input, "pH", 6.5)).setCO2PartialPressureBar(d(input, "pCO2_bar", 1.0))
        .setCations(d(input, "ca_mgL", 0.0), d(input, "ba_mgL", 0.0), d(input, "sr_mgL", 0.0), d(input, "mg_mgL", 0.0),
            d(input, "na_mgL", 0.0), d(input, "k_mgL", 0.0), d(input, "fe_mgL", 0.0))
        .setAnions(d(input, "cl_mgL", 0.0), d(input, "so4_mgL", 0.0), d(input, "hco3_mgL", 0.0),
            d(input, "co3_mgL", 0.0))
        .calculate();
    return JsonParser.parseString(calc.toJson()).getAsJsonObject();
  }

  /**
   * Runs the coupled multi-mineral scale equilibrium: precipitates barite, celestite, anhydrite, calcite and siderite
   * simultaneously with shared-ion competition, returning per-mineral precipitated amounts and (optionally) a kg/day
   * scaling rate when a produced-water flow is supplied.
   *
   * @param input JSON input object
   * @return JSON result object
   */
  private static JsonObject runMultiMineralScale(JsonObject input) {
    ScalePredictionCalculator p = new ScalePredictionCalculator();
    p.setTemperatureCelsius(d(input, "temperature_C", 60.0));
    p.setPressureBara(d(input, "pressure_bara", 50.0));
    p.setCalciumConcentration(d(input, "ca_mgL", 0.0));
    p.setBariumConcentration(d(input, "ba_mgL", 0.0));
    p.setStrontiumConcentration(d(input, "sr_mgL", 0.0));
    p.setIronConcentration(d(input, "fe_mgL", 0.0));
    p.setMagnesiumConcentration(d(input, "mg_mgL", 0.0));
    p.setSodiumConcentration(d(input, "na_mgL", 0.0));
    p.setBicarbonateConcentration(d(input, "hco3_mgL", 0.0));
    p.setSulphateConcentration(d(input, "so4_mgL", 0.0));
    double tds = d(input, "tds_mgL", 0.0);
    if (tds > 0.0) {
      p.setTotalDissolvedSolids(tds);
    }
    p.setCO2PartialPressure(d(input, "pCO2_bar", 0.0));
    if (input.has("pH")) {
      p.setPH(d(input, "pH", 6.5));
    } else {
      p.enableAutoPH();
    }
    if (bool(input, "secondOrderPressure", false)) {
      p.setSecondOrderPressureCorrection(true);
    }

    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(p);
    String model = input.has("activityModel") ? input.get("activityModel").getAsString() : "DAVIES";
    if ("BDOT".equalsIgnoreCase(model)) {
      eq.setActivityModel(MultiMineralScaleEquilibrium.ActivityModel.BDOT);
    }
    eq.solve();

    JsonObject data = JsonParser.parseString(eq.toJson()).getAsJsonObject();

    double waterFlow = d(input, "waterFlow_LPerDay", 0.0);
    if (waterFlow > 0.0) {
      JsonObject rates = new JsonObject();
      for (Map.Entry<String, MultiMineralScaleEquilibrium.MineralResult> e : eq.getResults().entrySet()) {
        rates.addProperty(e.getKey(), e.getValue().getPrecipitatedMassMgPerL() * waterFlow / 1.0e6);
      }
      rates.addProperty("total", eq.getTotalScaleMassMgPerL() * waterFlow / 1.0e6);
      data.addProperty("waterFlow_LPerDay", waterFlow);
      data.add("scaleRates_kgPerDay", rates);
    }
    return data;
  }

  private static JsonObject runMechanisticCorrosion(JsonObject input) {
    LangmuirInhibitorIsotherm iso = new LangmuirInhibitorIsotherm();
    if (input.has("kAdsRef")) {
      iso = new LangmuirInhibitorIsotherm(d(input, "kAdsRef", 5000.0), d(input, "dHads_kJmol", -35.0),
          d(input, "thetaMax", 0.95), d(input, "molarMass_gmol", 350.0));
    }
    MechanisticCorrosionModel model = new MechanisticCorrosionModel()
        .setTemperatureCelsius(d(input, "temperature_C", 60.0)).setTotalPressureBara(d(input, "pressure_bara", 80.0))
        .setGasComposition(d(input, "co2_mol", 0.05), d(input, "h2s_mol", 0.0))
        .setWaterChemistry(d(input, "pH", 5.5), d(input, "bicarb_mgL", 100.0), d(input, "ionicStrength_molL", 0.5))
        .setFlow(d(input, "velocity_ms", 2.0), d(input, "diameter_m", 0.15), d(input, "density_kgm3", 1000.0),
            d(input, "viscosity_pas", 1.0e-3))
        .setInhibitor(iso, d(input, "dose_mgL", 0.0)).evaluate();
    return JsonParser.parseString(model.toJson()).getAsJsonObject();
  }

  private static JsonObject runLangmuirInhibitor(JsonObject input) {
    LangmuirInhibitorIsotherm iso;
    if (input.has("kAdsRef")) {
      iso = new LangmuirInhibitorIsotherm(d(input, "kAdsRef", 5000.0), d(input, "dHads_kJmol", -35.0),
          d(input, "thetaMax", 0.95), d(input, "molarMass_gmol", 350.0));
    } else {
      iso = new LangmuirInhibitorIsotherm();
    }
    double tC = d(input, "temperature_C", 60.0);
    double dose = d(input, "dose_mgL", 50.0);
    JsonObject data = new JsonObject();
    data.add("isotherm", JsonParser.parseString(iso.toJson()).getAsJsonObject());
    data.addProperty("kAds_Lmol_atTemp", iso.getKAds(tC));
    data.addProperty("coverage", iso.getCoverage(dose, tC));
    data.addProperty("efficiency", iso.getEfficiency(dose, tC));
    if (input.has("targetEfficiency")) {
      double target = d(input, "targetEfficiency", 0.8);
      data.addProperty("doseForTargetEfficiency_mgL", iso.getDoseForEfficiency(target, tC));
      data.addProperty("targetEfficiency", target);
    }
    return data;
  }

  private static JsonObject runPackedBedScavenger(JsonObject input) {
    PackedBedScavengerReactor bed = new PackedBedScavengerReactor()
        .setGeometry(d(input, "diameter_m", 0.5), d(input, "height_m", 2.0), d(input, "voidage", 0.4))
        .setMedia(d(input, "loading_mol_kg", 5.0), d(input, "bulkDensity_kgm3", 1100.0),
            d(input, "stoichiometricRatio", 1.0))
        .setRateConstant(d(input, "k_per_s", 5.0)).setFeed(d(input, "cInlet_molm3", 1.0), d(input, "flow_m3s", 0.005))
        .setDiscretisation(i(input, "nCells", 30), i(input, "nTimeSteps", 100))
        .setSimulationTime(d(input, "simTime_s", 3600.0 * 24.0 * 30.0), d(input, "breakthroughFraction", 0.05))
        .evaluate();
    return JsonParser.parseString(bed.toJson()).getAsJsonObject();
  }

  /**
   * Runs the authoritative single-pure-mineral electrolyte equilibrium operation and reports its scientific gates.
   *
   * <p>
   * The adapter adds no thermodynamic equation or parameter. Pitzer GE and electrolyte CPA retain distinct parameter
   * semantics while sharing the public {@link ThermodynamicOperations#precipitateScale(String)} operation.
   * </p>
   *
   * @param input chemistry analysis input
   * @return precipitation ledger, aqueous-state diagnostics, and qualification boundary
   */
  private static JsonObject runElectrolyteScaleEquilibrium(JsonObject input) {
    double temperature = requiredFinitePositive(input, "temperature_K");
    double pressure = requiredFinitePositive(input, "pressure_bara");
    Map<String, Double> components = requiredComponentAmounts(input);
    if (!hasPositiveWater(components)) {
      throw new IllegalArgumentException("Electrolyte scale equilibrium requires a positive water amount in mol");
    }
    if (!input.has("mineral") || input.get("mineral").isJsonNull()
        || input.get("mineral").getAsString().trim().isEmpty()) {
      throw new IllegalArgumentException("A non-empty COMPSALT 'mineral' name is required");
    }

    String mineral = input.get("mineral").getAsString().trim();
    String model = input.has("model") ? input.get("model").getAsString().trim().toLowerCase() : "pitzer";
    String datasetSelector = input.has("dataset") ? input.get("dataset").getAsString().trim().toLowerCase()
        : "pitzer".equals(model) ? "phreeqc-ca-mg-cl-so4" : "not-applicable";
    SystemInterface system;
    PitzerParameterQualification pitzerQualification = null;

    if ("pitzer".equals(model)) {
      SystemPitzer pitzer = new SystemPitzer(temperature, pressure);
      addComponents(pitzer, components);
      requireElectroneutralInput(pitzer.getPhase(1), components);
      pitzer.init(0);
      applyScalePitzerDataset(pitzer, datasetSelector);
      pitzer.setMixingRule("classic");
      pitzer.setMultiPhaseCheck(true);
      pitzerQualification = pitzer.getPitzerParameterQualification();
      system = pitzer;
    } else if ("electrolyte-cpa".equals(model)) {
      if (input.has("dataset") && !"not-applicable".equals(datasetSelector)) {
        throw new IllegalArgumentException("Pitzer dataset selectors do not apply to electrolyte-CPA parameters");
      }
      SystemElectrolyteCPAstatoil electrolyteCpa = new SystemElectrolyteCPAstatoil(temperature, pressure);
      addComponents(electrolyteCpa, components);
      requireElectroneutralInput(electrolyteCpa.getPhase(1), components);
      electrolyteCpa.chemicalReactionInit();
      electrolyteCpa.createDatabase(true);
      electrolyteCpa.setMixingRule(10);
      electrolyteCpa.setMultiPhaseCheck(true);
      system = electrolyteCpa;
    } else {
      throw new IllegalArgumentException("Unknown electrolyte model '" + model + "'; use pitzer or electrolyte-cpa");
    }

    SaltPrecipitationResult solid = new ThermodynamicOperations(system).precipitateScale(mineral);
    JsonObject phaseState = validateAqueousPhaseState(system);
    boolean numericalGatesPass = Double.isFinite(solid.getInitialSaturationRatio())
        && Double.isFinite(solid.getFinalSaturationRatio()) && solid.getFinalSaturationRatio() > 0.0
        && Double.isFinite(solid.getPrecipitatedMoles()) && solid.getPrecipitatedMoles() >= 0.0
        && Double.isFinite(solid.getPrecipitatedMassGrams()) && solid.getPrecipitatedMassGrams() >= 0.0
        && solid.getComplementarityViolation() <= 1.0e-6 && solid.getMaximumIonBalanceResidualMoles() <= 1.0e-10
        && phaseState.get("normalizedNonNegative").getAsBoolean()
        && Math.abs(phaseState.get("chargeResidual_molPerKgWater").getAsDouble()) <= 1.0e-10;
    if (!numericalGatesPass) {
      throw new IllegalStateException(
          "Electrolyte scale equilibrium failed complementarity, balance, or phase-state gates");
    }

    JsonObject data = new JsonObject();
    data.addProperty("model", model);
    data.addProperty("authoritativeJavaOperation", "ThermodynamicOperations.precipitateScale");
    data.addProperty("temperature_K", temperature);
    data.addProperty("pressure_bara", pressure);
    data.addProperty("compositionBasis", "component amount in mol; aqueous molality derived from water mass");
    data.addProperty("mineral", solid.getSaltName());
    data.addProperty("datasetSelector", datasetSelector);
    data.addProperty("datasetId",
        pitzerQualification == null ? "not-applicable: electrolyte-EOS parameters are not Pitzer parameters"
            : pitzerQualification.getDatasetId());
    data.addProperty("precipitatedSolid", solid.hasPrecipitatedSolid());
    data.addProperty("precipitatedMoles_mol", solid.getPrecipitatedMoles());
    data.addProperty("precipitatedMass_g", solid.getPrecipitatedMassGrams());
    data.addProperty("initialSaturationRatio", solid.getInitialSaturationRatio());
    data.addProperty("finalSaturationRatio", solid.getFinalSaturationRatio());
    data.addProperty("complementarityViolation_log10SR", solid.getComplementarityViolation());
    data.addProperty("maximumIonBalanceResidual_mol", solid.getMaximumIonBalanceResidualMoles());
    data.add("aqueousPhaseState", phaseState);
    data.addProperty("engineeringGatesPass", true);
    if (pitzerQualification == null) {
      data.add("pitzerQualificationLevel", null);
      data.add("mineralTargetQualified", null);
    } else {
      data.addProperty("pitzerQualificationLevel", pitzerQualification.getLevel().name());
      data.addProperty("mineralTargetQualified",
          pitzerQualification.isValidatedFor(ValidationTarget.MINERAL_SATURATION_AND_PRECIPITATION));
      data.add("qualificationLimitations", GSON.toJsonTree(pitzerQualification.getLimitations()));
    }
    data.addProperty("publicationReady", false);
    data.addProperty("publicationLimitation",
        "Numerical engineering gates pass, but no exact mixed-brine mineral evidence envelope is registered for this "
            + "state");
    return data;
  }

  /**
   * Runs simultaneous competing pure-mineral equilibrium against the selected electrolyte model.
   *
   * <p>
   * This is a thin JSON/MCP view over {@link ThermodynamicOperations#precipitateScales(String...)}. It is distinct from
   * the concentration-based {@code multiMineralScale} screening analysis and adds no thermodynamic equation, Ksp, or
   * interaction parameter.
   * </p>
   *
   * @param input chemistry analysis input
   * @return deterministic solid ledger, complementarity, aqueous-state, and qualification evidence
   */
  private static JsonObject runElectrolyteMultiScaleEquilibrium(JsonObject input) {
    double temperature = requiredFinitePositive(input, "temperature_K");
    double pressure = requiredFinitePositive(input, "pressure_bara");
    Map<String, Double> components = requiredComponentAmounts(input);
    if (!hasPositiveWater(components)) {
      throw new IllegalArgumentException("Electrolyte multi-scale equilibrium requires a positive water amount in mol");
    }
    String[] mineralNames = requiredMineralNames(input);

    String model = input.has("model") ? input.get("model").getAsString().trim().toLowerCase() : "pitzer";
    String datasetSelector = input.has("dataset") ? input.get("dataset").getAsString().trim().toLowerCase()
        : "pitzer".equals(model) ? "phreeqc-catalog" : "not-applicable";
    SystemInterface system;
    PitzerParameterQualification pitzerQualification = null;

    if ("pitzer".equals(model)) {
      SystemPitzer pitzer = new SystemPitzer(temperature, pressure);
      addComponents(pitzer, components);
      requireElectroneutralInput(pitzer.getPhase(1), components);
      pitzer.init(0);
      applyScalePitzerDataset(pitzer, datasetSelector);
      pitzer.setMixingRule("classic");
      pitzer.setMultiPhaseCheck(true);
      pitzerQualification = pitzer.getPitzerParameterQualification();
      system = pitzer;
    } else if ("electrolyte-cpa".equals(model)) {
      if (input.has("dataset") && !"not-applicable".equals(datasetSelector)) {
        throw new IllegalArgumentException("Pitzer dataset selectors do not apply to electrolyte-CPA parameters");
      }
      SystemElectrolyteCPAstatoil electrolyteCpa = new SystemElectrolyteCPAstatoil(temperature, pressure);
      addComponents(electrolyteCpa, components);
      requireElectroneutralInput(electrolyteCpa.getPhase(1), components);
      electrolyteCpa.chemicalReactionInit();
      electrolyteCpa.createDatabase(true);
      electrolyteCpa.setMixingRule(10);
      electrolyteCpa.setMultiPhaseCheck(true);
      system = electrolyteCpa;
    } else {
      throw new IllegalArgumentException("Unknown electrolyte model '" + model + "'; use pitzer or electrolyte-cpa");
    }

    MultiSaltPrecipitationResult equilibrium = new ThermodynamicOperations(system).precipitateScales(mineralNames);
    JsonObject phaseState = validateAqueousPhaseState(system);
    JsonObject mineralResults = new JsonObject();
    boolean mineralValuesFinite = true;
    int presentSolidCount = 0;
    for (Map.Entry<String, SaltPrecipitationResult> entry : equilibrium.getMineralResults().entrySet()) {
      SaltPrecipitationResult solid = entry.getValue();
      mineralValuesFinite &= Double.isFinite(solid.getInitialSaturationRatio())
          && Double.isFinite(solid.getFinalSaturationRatio()) && solid.getFinalSaturationRatio() > 0.0
          && Double.isFinite(solid.getPrecipitatedMoles()) && solid.getPrecipitatedMoles() >= 0.0
          && Double.isFinite(solid.getPrecipitatedMassGrams()) && solid.getPrecipitatedMassGrams() >= 0.0;
      if (solid.hasPrecipitatedSolid()) {
        presentSolidCount++;
      }
      JsonObject mineral = new JsonObject();
      mineral.addProperty("precipitatedSolid", solid.hasPrecipitatedSolid());
      mineral.addProperty("precipitatedMoles_mol", solid.getPrecipitatedMoles());
      mineral.addProperty("precipitatedMass_g", solid.getPrecipitatedMassGrams());
      mineral.addProperty("initialSaturationRatio", solid.getInitialSaturationRatio());
      mineral.addProperty("finalSaturationRatio", solid.getFinalSaturationRatio());
      mineral.addProperty("complementarityViolation_log10SR", solid.getComplementarityViolation());
      mineralResults.add(entry.getKey(), mineral);
    }

    boolean numericalGatesPass = mineralValuesFinite
        && Double.isFinite(equilibrium.getMaximumComplementarityViolation())
        && equilibrium.getMaximumComplementarityViolation() <= 1.0e-6
        && Double.isFinite(equilibrium.getMaximumComponentBalanceResidualMoles())
        && equilibrium.getMaximumComponentBalanceResidualMoles() <= 1.0e-10
        && Double.isFinite(equilibrium.getTotalPrecipitatedMassGrams())
        && equilibrium.getTotalPrecipitatedMassGrams() >= 0.0
        && phaseState.get("normalizedNonNegative").getAsBoolean()
        && Math.abs(phaseState.get("chargeResidual_molPerKgWater").getAsDouble()) <= 1.0e-10;
    if (!numericalGatesPass) {
      throw new IllegalStateException(
          "Electrolyte multi-scale equilibrium failed complementarity, balance, or phase-state gates");
    }

    JsonObject data = new JsonObject();
    data.addProperty("model", model);
    data.addProperty("authoritativeJavaOperation", "ThermodynamicOperations.precipitateScales");
    data.addProperty("temperature_K", temperature);
    data.addProperty("pressure_bara", pressure);
    data.addProperty("compositionBasis", "component amount in mol; aqueous molality derived from water mass");
    data.addProperty("solidLedgerBasis",
        "pure COMPSALT ion-formula amounts; crystallization water and solid solutions are not represented");
    data.addProperty("datasetSelector", datasetSelector);
    data.addProperty("datasetId",
        pitzerQualification == null ? "not-applicable: electrolyte-EOS parameters are not Pitzer parameters"
            : pitzerQualification.getDatasetId());
    data.addProperty("requestedMineralCount", equilibrium.getMineralResults().size());
    data.addProperty("presentSolidCount", presentSolidCount);
    data.addProperty("equilibriumUpdates", equilibrium.getEquilibriumUpdates());
    data.addProperty("maximumComplementarityViolation_log10SR",
        equilibrium.getMaximumComplementarityViolation());
    data.addProperty("maximumComponentBalanceResidual_mol",
        equilibrium.getMaximumComponentBalanceResidualMoles());
    data.addProperty("totalPrecipitatedMass_g", equilibrium.getTotalPrecipitatedMassGrams());
    data.add("mineralResults", mineralResults);
    data.add("aqueousPhaseState", phaseState);
    data.addProperty("engineeringGatesPass", true);
    if (pitzerQualification == null) {
      data.add("pitzerQualificationLevel", null);
      data.add("mineralTargetQualified", null);
    } else {
      data.addProperty("pitzerQualificationLevel", pitzerQualification.getLevel().name());
      data.addProperty("mineralTargetQualified",
          pitzerQualification.isValidatedFor(ValidationTarget.MINERAL_SATURATION_AND_PRECIPITATION));
      data.add("qualificationLimitations", GSON.toJsonTree(pitzerQualification.getLimitations()));
    }
    data.addProperty("publicationReady", false);
    data.addProperty("publicationLimitation",
        "Numerical engineering gates pass, but no exact competitive mixed-brine mineral evidence envelope is "
            + "registered for this state");
    return data;
  }

  private static String[] requiredMineralNames(JsonObject input) {
    if (!input.has("minerals") || !input.get("minerals").isJsonArray()
        || input.getAsJsonArray("minerals").size() == 0) {
      throw new IllegalArgumentException("'minerals' must be a non-empty JSON array of COMPSALT names");
    }
    JsonArray requested = input.getAsJsonArray("minerals");
    String[] mineralNames = new String[requested.size()];
    for (int index = 0; index < requested.size(); index++) {
      if (requested.get(index).isJsonNull()) {
        throw new IllegalArgumentException("COMPSALT mineral names cannot be null");
      }
      mineralNames[index] = requested.get(index).getAsString().trim();
      if (mineralNames[index].isEmpty()) {
        throw new IllegalArgumentException("COMPSALT mineral names cannot be blank");
      }
    }
    return mineralNames;
  }

  private static Map<String, Double> requiredComponentAmounts(JsonObject input) {
    if (!input.has("components") || !input.get("components").isJsonObject()) {
      throw new IllegalArgumentException("'components' must be a JSON object of component moles");
    }
    Map<String, Double> components = new TreeMap<String, Double>();
    for (Map.Entry<String, com.google.gson.JsonElement> entry : input.getAsJsonObject("components").entrySet()) {
      String name = entry.getKey() == null ? "" : entry.getKey().trim();
      if (name.isEmpty() || entry.getValue() == null || entry.getValue().isJsonNull()) {
        throw new IllegalArgumentException("Electrolyte component names and amounts must not be empty");
      }
      double amount = entry.getValue().getAsDouble();
      if (!Double.isFinite(amount) || amount < 0.0) {
        throw new IllegalArgumentException("Component '" + name + "' amount must be finite and non-negative mol");
      }
      components.put(name, amount);
    }
    return components;
  }

  private static void addComponents(SystemInterface system, Map<String, Double> components) {
    for (Map.Entry<String, Double> component : components.entrySet()) {
      system.addComponent(component.getKey(), component.getValue());
    }
  }

  private static void requireElectroneutralInput(PhaseInterface phase, Map<String, Double> components) {
    JsonObject validation = validateElectroneutrality(phase, components);
    if (!validation.get("valid").getAsBoolean()) {
      throw new IllegalArgumentException(
          "Electrolyte input is not electroneutral: residual " + validation.get("chargeResidual_mol").getAsDouble()
              + " mol exceeds tolerance " + validation.get("chargeTolerance_mol").getAsDouble() + " mol");
    }
  }

  private static void applyScalePitzerDataset(SystemPitzer system, String selector) {
    if ("phreeqc-ca-mg-cl-so4".equals(selector)) {
      system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    } else if ("phreeqc-catalog".equals(selector)) {
      system.applyCompletePhreeqcPitzerCatalogParameters();
    } else {
      throw new IllegalArgumentException(
          "Unknown scale Pitzer dataset selector '" + selector + "'; use phreeqc-ca-mg-cl-so4 or phreeqc-catalog");
    }
  }

  private static JsonObject validateAqueousPhaseState(SystemInterface system) {
    int aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
    PhaseInterface aqueous = system.getPhase(aqueousPhaseNumber >= 0 ? aqueousPhaseNumber : 1);
    double moleFractionSum = 0.0;
    double chargeResidual = 0.0;
    boolean finiteNonNegative = true;
    for (int componentIndex = 0; componentIndex < aqueous.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = aqueous.getComponent(componentIndex);
      double moleFraction = component.getx();
      finiteNonNegative &= Double.isFinite(moleFraction) && moleFraction >= 0.0;
      moleFractionSum += moleFraction;
      chargeResidual += component.getMolality(aqueous) * component.getIonicCharge();
    }
    JsonObject state = new JsonObject();
    state.addProperty("normalizedNonNegative", finiteNonNegative && Math.abs(moleFractionSum - 1.0) <= 1.0e-12);
    state.addProperty("moleFractionSum", moleFractionSum);
    state.addProperty("normalizationTolerance", 1.0e-12);
    state.addProperty("chargeResidual_molPerKgWater", chargeResidual);
    state.addProperty("chargeTolerance_molPerKgWater", 1.0e-10);
    return state;
  }

  /**
   * Reports active-topology coverage and scientific qualification from the authoritative {@link SystemPitzer} APIs.
   *
   * <p>
   * This setup/publication view performs no flash and adopts no parameter. Component amounts are system moles;
   * temperature is K and pressure is bara. The explicit dataset selector prevents a caller from confusing source
   * availability with observable-specific qualification.
   * </p>
   *
   * @param input chemistry analysis input
   * @return deterministic qualification decision and evidence, apart from envelope timing/provenance
   */
  private static JsonObject runPitzerQualification(JsonObject input) {
    double temperature = requiredFinitePositive(input, "temperature_K");
    double pressure = requiredFinitePositive(input, "pressure_bara");
    if (!input.has("components") || !input.get("components").isJsonObject()) {
      throw new IllegalArgumentException("'components' must be a JSON object of component moles");
    }

    Map<String, Double> components = new TreeMap<String, Double>();
    for (Map.Entry<String, com.google.gson.JsonElement> entry : input.getAsJsonObject("components").entrySet()) {
      String name = entry.getKey() == null ? "" : entry.getKey().trim();
      if (name.isEmpty() || entry.getValue() == null || entry.getValue().isJsonNull()) {
        throw new IllegalArgumentException("Pitzer component names and amounts must not be empty");
      }
      double amount = entry.getValue().getAsDouble();
      if (!Double.isFinite(amount) || amount < 0.0) {
        throw new IllegalArgumentException("Component '" + name + "' amount must be finite and non-negative mol");
      }
      if (amount > 0.0) {
        components.put(name, amount);
      }
    }
    if (!hasPositiveWater(components)) {
      throw new IllegalArgumentException("Pitzer qualification requires a positive water amount in mol");
    }

    String selector = input.has("dataset") ? input.get("dataset").getAsString().trim().toLowerCase() : "auto";
    SystemPitzer system = new SystemPitzer(temperature, pressure);
    for (Map.Entry<String, Double> component : components.entrySet()) {
      system.addComponent(component.getKey(), component.getValue());
    }
    if ("legacy".equals(selector)) {
      system.useLegacyPitzerParameters();
    }

    PhasePitzer aqueous = (PhasePitzer) system.getPhase(1);
    JsonObject inputValidation = validateElectroneutrality(aqueous, components);
    if (!inputValidation.get("valid").getAsBoolean()) {
      JsonObject rejected = basePitzerQualificationResult(temperature, pressure, selector, inputValidation);
      rejected.addProperty("decision", "REJECTED");
      rejected.addProperty("publicationReady", false);
      rejected.addProperty("diagnostic", "Input ionic composition is not electroneutral");
      return rejected;
    }

    system.init(0);
    applyPitzerDatasetSelection(system, selector);
    PitzerParameterQualification qualification = system.getPitzerParameterQualification();
    PitzerParameterCoverage ionicCoverage = aqueous.getPitzerParameterCoverage();
    PitzerNeutralParameterCoverage neutralCoverage = aqueous.auditNeutralPitzerParameterCoverage();
    JsonObject phaseState = validateNormalizedNonNegativePhase(aqueous);

    ValidationTarget target = parseValidationTarget(input);
    boolean targetQualified = target != null && qualification.isValidatedFor(target);
    JsonObject stateRange = stateRangeEvidence(temperature, aqueous, qualification.getDatasetId());
    boolean insideRange = stateRange.get("checked").getAsBoolean() && stateRange.get("withinRange").getAsBoolean();
    boolean completeTopology = ionicCoverage.isComplete() && neutralCoverage.isComplete();
    boolean publicationReady = completeTopology && targetQualified && insideRange
        && phaseState.get("normalizedNonNegative").getAsBoolean();

    JsonObject data = basePitzerQualificationResult(temperature, pressure, selector, inputValidation);
    data.addProperty("datasetId", qualification.getDatasetId());
    data.addProperty("qualificationLevel", qualification.getLevel().name());
    data.add("validatedSystems", GSON.toJsonTree(qualification.getValidatedSystems()));
    data.add("validatedTargets", validationTargetArray(qualification.getValidatedTargets()));
    data.add("limitations", GSON.toJsonTree(qualification.getLimitations()));
    data.addProperty("qualificationDiagnostic", qualification.formatDiagnostic());
    data.add("ionicCoverage", GSON.toJsonTree(ionicCoverage));
    data.add("neutralCoverage", GSON.toJsonTree(neutralCoverage));
    data.addProperty("completeTopology", completeTopology);
    data.add("aqueousPhaseState", phaseState);
    if (target == null) {
      data.add("requestedValidationTarget", null);
      data.add("targetQualified", null);
    } else {
      data.addProperty("requestedValidationTarget", target.name());
      data.addProperty("targetQualified", targetQualified);
    }
    data.add("stateRange", stateRange);
    data.addProperty("decision", publicationReady ? "ACCEPTED" : "REJECTED");
    data.addProperty("publicationReady", publicationReady);
    data.addProperty("diagnostic", publicationDiagnostic(target, completeTopology, targetQualified, stateRange,
        phaseState.get("normalizedNonNegative").getAsBoolean()));
    return data;
  }

  private static JsonObject basePitzerQualificationResult(double temperature, double pressure, String selector,
      JsonObject inputValidation) {
    JsonObject data = new JsonObject();
    data.addProperty("model", "SystemPitzer / PhasePitzer aqueous GE with SRK gas-oil roles");
    data.addProperty("temperature_K", temperature);
    data.addProperty("pressure_bara", pressure);
    data.addProperty("compositionBasis", "component amount in mol; aqueous molality derived from water mass");
    data.addProperty("datasetSelector", selector);
    data.add("inputValidation", inputValidation);
    return data;
  }

  private static double requiredFinitePositive(JsonObject input, String key) {
    if (!input.has(key) || input.get(key).isJsonNull()) {
      throw new IllegalArgumentException("Missing required '" + key + "'");
    }
    double value = input.get(key).getAsDouble();
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException("'" + key + "' must be finite and positive");
    }
    return value;
  }

  private static boolean hasPositiveWater(Map<String, Double> components) {
    for (Map.Entry<String, Double> component : components.entrySet()) {
      if ("water".equalsIgnoreCase(component.getKey()) && component.getValue() > 0.0) {
        return true;
      }
    }
    return false;
  }

  private static JsonObject validateElectroneutrality(PhaseInterface phase, Map<String, Double> components) {
    double chargeMoles = 0.0;
    double absoluteChargeMoles = 0.0;
    for (Map.Entry<String, Double> component : components.entrySet()) {
      if (!phase.hasComponent(component.getKey())) {
        throw new IllegalArgumentException("Unknown electrolyte component '" + component.getKey() + "'");
      }
      double chargedMoles = phase.getComponent(component.getKey()).getIonicCharge() * component.getValue();
      chargeMoles += chargedMoles;
      absoluteChargeMoles += Math.abs(chargedMoles);
    }
    double tolerance = 1.0e-12 * Math.max(1.0, absoluteChargeMoles);
    JsonObject validation = new JsonObject();
    validation.addProperty("valid", Math.abs(chargeMoles) <= tolerance);
    validation.addProperty("chargeResidual_mol", chargeMoles);
    validation.addProperty("chargeTolerance_mol", tolerance);
    return validation;
  }

  private static JsonObject validateNormalizedNonNegativePhase(PhasePitzer phase) {
    double moleFractionSum = 0.0;
    boolean finiteNonNegative = true;
    for (int index = 0; index < phase.getNumberOfComponents(); index++) {
      double moleFraction = phase.getComponent(index).getx();
      finiteNonNegative &= Double.isFinite(moleFraction) && moleFraction >= 0.0;
      moleFractionSum += moleFraction;
    }
    JsonObject validation = new JsonObject();
    validation.addProperty("normalizedNonNegative", finiteNonNegative && Math.abs(moleFractionSum - 1.0) <= 1.0e-12);
    validation.addProperty("moleFractionSum", moleFractionSum);
    validation.addProperty("normalizationTolerance", 1.0e-12);
    return validation;
  }

  private static void applyPitzerDatasetSelection(SystemPitzer system, String selector) {
    switch (selector) {
    case "auto":
    case "legacy":
      return;
    case "phreeqc-na-k-cl":
      system.applyPhreeqcSodiumPotassiumChlorideParameters();
      return;
    case "phreeqc-co2-na2so4":
      system.applyPhreeqcCo2SodiumSulfateParameters();
      return;
    case "phreeqc-catalog":
      system.applyCompletePhreeqcPitzerCatalogParameters();
      return;
    default:
      throw new IllegalArgumentException("Unknown Pitzer dataset selector '" + selector
          + "'; use auto, legacy, phreeqc-na-k-cl, phreeqc-co2-na2so4, or phreeqc-catalog");
    }
  }

  private static ValidationTarget parseValidationTarget(JsonObject input) {
    if (!input.has("validationTarget") || input.get("validationTarget").isJsonNull()
        || input.get("validationTarget").getAsString().trim().isEmpty()) {
      return null;
    }
    String name = input.get("validationTarget").getAsString().trim().toUpperCase();
    try {
      return ValidationTarget.valueOf(name);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Unknown Pitzer validationTarget '" + name + "'; use " + Arrays.toString(ValidationTarget.values()));
    }
  }

  private static JsonArray validationTargetArray(Set<ValidationTarget> targets) {
    JsonArray array = new JsonArray();
    for (ValidationTarget target : ValidationTarget.values()) {
      if (targets.contains(target)) {
        array.add(target.name());
      }
    }
    return array;
  }

  private static JsonObject stateRangeEvidence(double temperature, PhasePitzer phase, String datasetId) {
    JsonObject evidence = new JsonObject();
    evidence.addProperty("checked", false);
    evidence.addProperty("withinRange", false);
    evidence.addProperty("pressureChecked", false);
    evidence.addProperty("reason", "No exact declared-envelope helper is registered for this dataset identity");
    if (PitzerParameterDatasets.PHREEQC_NA_K_CL_ID.equals(datasetId)) {
      double sodium = molality(phase, "Na+");
      double potassium = molality(phase, "K+");
      double chloride = molality(phase, "Cl-");
      evidence.addProperty("checked", true);
      evidence.addProperty("withinRange", PitzerParameterDatasets
          .isWithinSodiumPotassiumChlorideValidationRange(temperature, sodium, potassium, chloride));
      evidence.addProperty("reason", "298.15-423.15 K and 0.1-3 mol/kg total chloride Na-K-Cl envelope");
      evidence.addProperty("sodiumMolality_molPerKgWater", sodium);
      evidence.addProperty("potassiumMolality_molPerKgWater", potassium);
      evidence.addProperty("chlorideMolality_molPerKgWater", chloride);
    } else if (PitzerParameterDatasets.PHREEQC_CO2_NA2SO4_ID.equals(datasetId)) {
      double sulfate = molality(phase, "SO4--");
      evidence.addProperty("checked", true);
      evidence.addProperty("withinRange",
          PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(temperature, sulfate));
      evidence.addProperty("reason", "303.15-423.15 K and 1-2 mol/kg Na2SO4 activity/water-property envelope");
      evidence.addProperty("sodiumSulfateMolality_molPerKgWater", sulfate);
    }
    return evidence;
  }

  private static double molality(PhasePitzer phase, String componentName) {
    if (!phase.hasComponent(componentName)) {
      return 0.0;
    }
    return phase.getComponent(componentName).getMolality(phase);
  }

  private static String publicationDiagnostic(ValidationTarget target, boolean completeTopology,
      boolean targetQualified, JsonObject stateRange, boolean normalizedNonNegative) {
    if (!completeTopology) {
      return "Rejected: active ionic or neutral Pitzer topology is incomplete";
    }
    if (target == null) {
      return "Rejected: validationTarget is required for an observable-specific publication decision";
    }
    if (!targetQualified) {
      return "Rejected: selected dataset is not independently qualified for " + target;
    }
    if (!stateRange.get("checked").getAsBoolean()) {
      return "Rejected: no exact state-range helper is registered for the selected dataset";
    }
    if (!stateRange.get("withinRange").getAsBoolean()) {
      return "Rejected: current state is outside the declared independent-evidence envelope";
    }
    if (!normalizedNonNegative) {
      return "Rejected: aqueous phase is not finite, non-negative, and normalized";
    }
    return "Accepted: complete topology, observable qualification, and declared state envelope all pass";
  }

  // ─── Helpers ───────────────────────────────────────────

  private static double d(JsonObject o, String key, double def) {
    return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsDouble() : def;
  }

  private static int i(JsonObject o, String key, int def) {
    return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsInt() : def;
  }

  private static boolean bool(JsonObject o, String key, boolean def) {
    return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsBoolean() : def;
  }

  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }
}
