package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

/**
 * Stateless chemistry-and-integrity runner for MCP integration.
 *
 * <p>
 * Exposes the open standards-traceable chemistry stack — electrolyte scale prediction, mechanistic corrosion (NORSOK
 * M-506 + Nesic mass transfer + Langmuir inhibitor), Langmuir inhibitor isotherm dosing, and packed-bed H2S scavenger
 * breakthrough — as JSON-driven analyses usable by AI agents over the Model Context Protocol.
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

  private static final List<String> SUPPORTED_ANALYSES = Collections.unmodifiableList(Arrays.asList("electrolyteScale",
      "multiMineralScale", "mechanisticCorrosion", "langmuirInhibitor", "packedBedScavenger"));

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
