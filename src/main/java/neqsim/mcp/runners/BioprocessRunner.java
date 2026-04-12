package neqsim.mcp.runners;

import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.equipment.reactor.AnaerobicDigester;
import neqsim.process.equipment.reactor.FermentationReactor;
import neqsim.process.equipment.reactor.BiomassGasifier;
import neqsim.process.equipment.reactor.PyrolysisReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.characterization.BiomassCharacterization;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Stateless bioprocessing runner for MCP integration.
 *
 * <p>
 * Supports four reactor types: anaerobic digestion, fermentation, biomass gasification, and
 * pyrolysis. Each takes feedstock parameters and returns process outputs (biogas, syngas, bio-oil,
 * char yields, energy recovery, etc.).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BioprocessRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private BioprocessRunner() {}

  /**
   * Runs a bioprocess simulation from a JSON input.
   *
   * <p>
   * The "reactorType" field determines which reactor is used:
   * </p>
   * <ul>
   * <li>"anaerobicDigester" — biogas production from organic waste</li>
   * <li>"fermentation" — microbial fermentation (ethanol, etc.)</li>
   * <li>"gasifier" — thermochemical biomass gasification to syngas</li>
   * <li>"pyrolysis" — thermal decomposition to char, bio-oil, gas</li>
   * </ul>
   *
   * @param json the JSON bioprocess specification
   * @return a JSON string with bioprocess results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON bioprocess specification");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    String reactorType =
        input.has("reactorType") ? input.get("reactorType").getAsString().toLowerCase()
            : "anaerobicdigester";

    switch (reactorType) {
      case "fermentation":
        return runFermentation(input);
      case "gasifier":
        return runGasification(input);
      case "pyrolysis":
        return runPyrolysis(input);
      case "anaerobicdigester":
      default:
        return runAnaerobicDigestion(input);
    }
  }

  /**
   * Runs an anaerobic digestion simulation.
   *
   * @param input the JSON input
   * @return the results as JSON
   */
  private static String runAnaerobicDigestion(JsonObject input) {
    long startTime = System.currentTimeMillis();
    try {
      // Create a feed stream with biogas-relevant components
      SystemInterface fluid = new SystemSrkEos(273.15 + 37.0, 1.013);
      fluid.addComponent("methane", 0.6);
      fluid.addComponent("CO2", 0.35);
      fluid.addComponent("nitrogen", 0.03);
      fluid.addComponent("H2S", 0.02);
      fluid.setMixingRule("classic");

      Stream feed = new Stream("Feed", fluid);
      feed.setFlowRate(1000.0, "kg/hr");
      feed.run();

      AnaerobicDigester digester = new AnaerobicDigester("AD Reactor", feed);

      // Configure from input
      if (input.has("substrateType")) {
        String st = input.get("substrateType").getAsString().toUpperCase();
        digester.setSubstrateType(AnaerobicDigester.SubstrateType.valueOf(st));
      }
      if (input.has("feedRate_kgPerHr") && input.has("totalSolidsFraction")) {
        digester.setFeedRate(input.get("feedRate_kgPerHr").getAsDouble(),
            input.get("totalSolidsFraction").getAsDouble());
      }
      if (input.has("temperature_C")) {
        digester.setDigesterTemperature(input.get("temperature_C").getAsDouble(), "C");
      }
      if (input.has("specificMethaneYield_Nm3PerKgVS")) {
        digester
            .setSpecificMethaneYield(input.get("specificMethaneYield_Nm3PerKgVS").getAsDouble());
      }
      if (input.has("vsDestruction")) {
        digester.setVSDestruction(input.get("vsDestruction").getAsDouble());
      }
      if (input.has("methaneFraction")) {
        digester.setMethaneFraction(input.get("methaneFraction").getAsDouble());
      }
      if (input.has("volume_m3")) {
        digester.setVesselVolume(input.get("volume_m3").getAsDouble());
      }

      // Run
      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      process.add(digester);
      process.run();

      // Build response
      JsonObject data = new JsonObject();
      data.addProperty("biogasFlowRate_Nm3PerDay", digester.getBiogasFlowRateNm3PerDay());

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("anaerobic digestion simulation");
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      response.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("AD_ERROR", "Anaerobic digestion simulation failed: " + e.getMessage(),
          "Check substrate type and feed parameters");
    }
  }

  /**
   * Runs a fermentation reactor simulation.
   *
   * @param input the JSON input
   * @return the results as JSON
   */
  private static String runFermentation(JsonObject input) {
    long startTime = System.currentTimeMillis();
    try {
      SystemInterface fluid = new SystemSrkEos(273.15 + 32.0, 1.013);
      fluid.addComponent("ethanol", 0.05);
      fluid.addComponent("water", 0.90);
      fluid.addComponent("CO2", 0.05);
      fluid.setMixingRule("classic");

      Stream feed = new Stream("Feed", fluid);
      feed.setFlowRate(500.0, "kg/hr");
      feed.run();

      FermentationReactor reactor = new FermentationReactor("Fermenter", feed);

      // Configure from input
      if (input.has("kineticModel")) {
        String km = input.get("kineticModel").getAsString().toUpperCase();
        reactor.setKineticModel(FermentationReactor.KineticModel.valueOf(km));
      }
      if (input.has("operationMode")) {
        String om = input.get("operationMode").getAsString().toUpperCase();
        reactor.setOperationMode(FermentationReactor.OperationMode.valueOf(om));
      }
      if (input.has("maxSpecificGrowthRate")) {
        reactor.setMaxSpecificGrowthRate(input.get("maxSpecificGrowthRate").getAsDouble());
      }
      if (input.has("monodConstant")) {
        reactor.setMonodConstant(input.get("monodConstant").getAsDouble());
      }
      if (input.has("yieldBiomass")) {
        reactor.setYieldBiomass(input.get("yieldBiomass").getAsDouble());
      }
      if (input.has("yieldProduct")) {
        reactor.setYieldProduct(input.get("yieldProduct").getAsDouble());
      }
      if (input.has("substrateConcentration_gPerL")) {
        reactor.setSubstrateConcentration(input.get("substrateConcentration_gPerL").getAsDouble());
      }
      if (input.has("biomassConcentration_gPerL")) {
        reactor.setBiomassConcentration(input.get("biomassConcentration_gPerL").getAsDouble());
      }
      if (input.has("volume_m3")) {
        reactor.setVesselVolume(input.get("volume_m3").getAsDouble());
      }
      if (input.has("residenceTime_hr")) {
        reactor.setResidenceTime(input.get("residenceTime_hr").getAsDouble(), "hr");
      }
      if (input.has("temperature_C")) {
        reactor.setReactorTemperature(input.get("temperature_C").getAsDouble(), "C");
      }

      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      process.add(reactor);
      process.run();

      JsonObject data = new JsonObject();
      data.addProperty("substrateConversion", reactor.getSubstrateConversion());
      data.addProperty("productivity", reactor.getProductivity());

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("fermentation reactor simulation");
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      response.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("FERMENTATION_ERROR", "Fermentation simulation failed: " + e.getMessage(),
          "Check kinetic model parameters");
    }
  }

  /**
   * Runs a biomass gasification simulation.
   *
   * @param input the JSON input
   * @return the results as JSON
   */
  private static String runGasification(JsonObject input) {
    long startTime = System.currentTimeMillis();
    try {
      BiomassGasifier gasifier = new BiomassGasifier("Gasifier");

      // Biomass characterization
      BiomassCharacterization biomass = new BiomassCharacterization("Biomass");
      double moisturePct = 15.0;
      if (input.has("biomass")) {
        JsonObject bm = input.getAsJsonObject("biomass");
        if (bm.has("carbon")) {
          biomass.setUltimateAnalysis(bm.get("carbon").getAsDouble(),
              bm.has("hydrogen") ? bm.get("hydrogen").getAsDouble() : 6.0,
              bm.has("oxygen") ? bm.get("oxygen").getAsDouble() : 42.0,
              bm.has("nitrogen") ? bm.get("nitrogen").getAsDouble() : 0.5,
              bm.has("sulfur") ? bm.get("sulfur").getAsDouble() : 0.1,
              bm.has("ash") ? bm.get("ash").getAsDouble() : 1.0);
        }
        if (bm.has("moisture")) {
          moisturePct = bm.get("moisture").getAsDouble() * 100.0;
        }
        double ashPct = bm.has("ash") ? bm.get("ash").getAsDouble() : 1.0;
        biomass.setProximateAnalysis(moisturePct, 80.0, 20.0 - ashPct, ashPct);
      }
      double feedRate =
          input.has("feedRate_kgPerHr") ? input.get("feedRate_kgPerHr").getAsDouble() : 1000.0;
      gasifier.setBiomass(biomass, feedRate);

      // Gasifier configuration
      if (input.has("gasifierType")) {
        gasifier.setGasifierType(BiomassGasifier.GasifierType
            .valueOf(input.get("gasifierType").getAsString().toUpperCase()));
      }
      if (input.has("agentType")) {
        gasifier.setAgentType(
            BiomassGasifier.AgentType.valueOf(input.get("agentType").getAsString().toUpperCase()));
      }
      if (input.has("equivalenceRatio")) {
        gasifier.setEquivalenceRatio(input.get("equivalenceRatio").getAsDouble());
      }
      if (input.has("temperature_C")) {
        gasifier.setGasificationTemperature(input.get("temperature_C").getAsDouble(), "C");
      }
      if (input.has("pressure_bara")) {
        gasifier.setGasificationPressure(input.get("pressure_bara").getAsDouble());
      }
      if (input.has("carbonConversionEfficiency")) {
        gasifier
            .setCarbonConversionEfficiency(input.get("carbonConversionEfficiency").getAsDouble());
      }

      gasifier.run();

      JsonObject data = new JsonObject();
      data.addProperty("coldGasEfficiency", gasifier.getColdGasEfficiency());
      data.addProperty("syngasYield_Nm3PerKg", gasifier.getSyngasYieldNm3PerKg());
      data.addProperty("syngasLHV_MjPerNm3", gasifier.getSyngasLHVMjPerNm3());
      data.addProperty("charYieldFraction", gasifier.getCharYieldFraction());

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("biomass gasification simulation");
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      response.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("GASIFICATION_ERROR", "Gasification simulation failed: " + e.getMessage(),
          "Check biomass characterization and gasifier parameters");
    }
  }

  /**
   * Runs a pyrolysis reactor simulation.
   *
   * @param input the JSON input
   * @return the results as JSON
   */
  private static String runPyrolysis(JsonObject input) {
    long startTime = System.currentTimeMillis();
    try {
      PyrolysisReactor reactor = new PyrolysisReactor("Pyrolysis");

      // Biomass characterization
      BiomassCharacterization biomass = new BiomassCharacterization("Biomass");
      double moisturePct = 15.0;
      if (input.has("biomass")) {
        JsonObject bm = input.getAsJsonObject("biomass");
        if (bm.has("carbon")) {
          biomass.setUltimateAnalysis(bm.get("carbon").getAsDouble(),
              bm.has("hydrogen") ? bm.get("hydrogen").getAsDouble() : 6.0,
              bm.has("oxygen") ? bm.get("oxygen").getAsDouble() : 42.0,
              bm.has("nitrogen") ? bm.get("nitrogen").getAsDouble() : 0.5,
              bm.has("sulfur") ? bm.get("sulfur").getAsDouble() : 0.1,
              bm.has("ash") ? bm.get("ash").getAsDouble() : 1.0);
        }
        if (bm.has("moisture")) {
          moisturePct = bm.get("moisture").getAsDouble() * 100.0;
        }
        double ashPct = bm.has("ash") ? bm.get("ash").getAsDouble() : 1.0;
        biomass.setProximateAnalysis(moisturePct, 80.0, 20.0 - ashPct, ashPct);
      }
      double feedRate =
          input.has("feedRate_kgPerHr") ? input.get("feedRate_kgPerHr").getAsDouble() : 1000.0;
      reactor.setBiomass(biomass, feedRate);

      // Pyrolysis configuration
      if (input.has("mode")) {
        reactor.setPyrolysisMode(
            PyrolysisReactor.PyrolysisMode.valueOf(input.get("mode").getAsString().toUpperCase()));
      }
      if (input.has("temperature_C")) {
        reactor.setPyrolysisTemperature(input.get("temperature_C").getAsDouble(), "C");
      }
      if (input.has("pressure_bara")) {
        reactor.setReactorPressure(input.get("pressure_bara").getAsDouble());
      }
      if (input.has("heatingRate_KPerS")) {
        reactor.setHeatingRate(input.get("heatingRate_KPerS").getAsDouble());
      }
      if (input.has("vapourResidenceTime_s")) {
        reactor.setVapourResidenceTime(input.get("vapourResidenceTime_s").getAsDouble());
      }

      reactor.run();

      JsonObject data = new JsonObject();
      data.addProperty("charYield", reactor.getActualCharYield());
      data.addProperty("bioOilYield", reactor.getActualBioOilYield());
      data.addProperty("gasYield", reactor.getActualGasYield());
      data.addProperty("bioOilHHV_MjPerKg", reactor.getBioOilHHV());
      data.addProperty("biocharHHV_MjPerKg", reactor.getBiocharHHV());
      data.addProperty("gasLHV_MjPerNm3", reactor.getGasLHVMjPerNm3());
      data.addProperty("energyYield", reactor.getEnergyYield());

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("pyrolysis simulation");
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      response.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("PYROLYSIS_ERROR", "Pyrolysis simulation failed: " + e.getMessage(),
          "Check biomass characterization and pyrolysis mode");
    }
  }

  /**
   * Creates a standard error JSON string.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return the error JSON string
   */
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
