package neqsim.mcp.runners;

import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemPrEos;

/**
 * Stateless reservoir simulation runner for MCP integration.
 *
 * <p>
 * Supports simple reservoir material balance (tank model) with producer and injector wells.
 * Provides production forecasting by running transient steps to simulate depletion over time.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ReservoirRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private ReservoirRunner() {}

  /**
   * Runs a reservoir simulation from a JSON input string.
   *
   * @param json the JSON reservoir specification
   * @return a JSON string with status and production results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON reservoir specification");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    long startTime = System.currentTimeMillis();

    try {
      // --- Create reservoir fluid ---
      if (!input.has("components")) {
        return errorJson("MISSING_COMPONENTS", "No 'components' specified",
            "Provide a components map for the reservoir fluid");
      }
      SystemInterface fluid = createFluidFromInput(input);

      // --- Reservoir volumes ---
      double gasVolume =
          input.has("gasVolume_Sm3") ? input.get("gasVolume_Sm3").getAsDouble() : 1e9;
      double oilVolume = input.has("oilVolume_Sm3") ? input.get("oilVolume_Sm3").getAsDouble() : 0;
      double waterVolume =
          input.has("waterVolume_Sm3") ? input.get("waterVolume_Sm3").getAsDouble() : 0;

      // --- Create reservoir ---
      SimpleReservoir reservoir = new SimpleReservoir("Reservoir");
      reservoir.setReservoirFluid(fluid, gasVolume, oilVolume, waterVolume);

      // --- Add producers ---
      JsonArray producers = input.has("producers") ? input.getAsJsonArray("producers") : null;
      int numProducers = 1;
      if (producers != null) {
        numProducers = producers.size();
      }

      ProcessSystem process = new ProcessSystem();
      process.add(reservoir);

      StreamInterface[] prodStreams = new StreamInterface[numProducers];
      for (int i = 0; i < numProducers; i++) {
        String name = producers != null && producers.get(i).getAsJsonObject().has("name")
            ? producers.get(i).getAsJsonObject().get("name").getAsString()
            : "Producer-" + (i + 1);
        prodStreams[i] = reservoir.addGasProducer(name);
        if (producers != null) {
          JsonObject prod = producers.get(i).getAsJsonObject();
          if (prod.has("flowRate")) {
            JsonObject fr = prod.getAsJsonObject("flowRate");
            prodStreams[i].setFlowRate(fr.get("value").getAsDouble(),
                fr.has("unit") ? fr.get("unit").getAsString() : "MSm3/day");
          }
        }
        process.add((Stream) prodStreams[i]);
      }

      // --- Run initial steady state ---
      process.run();

      // --- Run transient if timeSteps specified ---
      JsonObject data = new JsonObject();
      if (input.has("simulationYears") || input.has("timeSteps")) {
        int years = input.has("simulationYears") ? input.get("simulationYears").getAsInt() : 20;
        double dtDays = input.has("timeStepDays") ? input.get("timeStepDays").getAsDouble() : 30.0;
        int steps = (int) (years * 365.0 / dtDays);

        JsonArray timeArr = new JsonArray();
        JsonArray pressureArr = new JsonArray();
        JsonArray gasInPlaceArr = new JsonArray();
        JsonArray oilInPlaceArr = new JsonArray();
        JsonArray cumGasArr = new JsonArray();
        JsonArray cumOilArr = new JsonArray();

        for (int step = 0; step <= steps; step++) {
          double time_days = step * dtDays;
          timeArr.add(time_days / 365.0);
          pressureArr.add(reservoir.getReservoirFluid().getPressure());
          gasInPlaceArr.add(reservoir.getGasInPlace("GSm3"));
          oilInPlaceArr.add(reservoir.getOilInPlace("MSm3"));
          cumGasArr.add(reservoir.getGasProductionTotal("GSm3"));
          cumOilArr.add(reservoir.getOilProductionTotal("MSm3"));

          if (step < steps) {
            process.runTransient(dtDays * 24.0 * 3600.0);
          }
        }

        data.add("time_years", timeArr);
        data.add("reservoirPressure_bara", pressureArr);
        data.add("gasInPlace_GSm3", gasInPlaceArr);
        data.add("oilInPlace_MSm3", oilInPlaceArr);
        data.add("cumulativeGasProduction_GSm3", cumGasArr);
        data.add("cumulativeOilProduction_MSm3", cumOilArr);
      } else {
        // Single-step results
        data.addProperty("reservoirPressure_bara", reservoir.getReservoirFluid().getPressure());
        data.addProperty("gasInPlace_GSm3", reservoir.getGasInPlace("GSm3"));
        data.addProperty("oilInPlace_MSm3", reservoir.getOilInPlace("MSm3"));
      }

      // --- Producer stream results ---
      JsonArray prodResults = new JsonArray();
      for (int i = 0; i < numProducers; i++) {
        JsonObject pr = new JsonObject();
        pr.addProperty("name", prodStreams[i].getName());
        pr.addProperty("temperature_C", prodStreams[i].getTemperature() - 273.15);
        pr.addProperty("pressure_bara", prodStreams[i].getPressure());
        prodResults.add(pr);
      }
      data.add("producers", prodResults);

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("reservoir simulation (material balance)");
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      result.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("RESERVOIR_ERROR", "Reservoir simulation failed: " + e.getMessage(),
          "Check fluid definition and reservoir parameters");
    }
  }

  /**
   * Creates a fluid system from the JSON input.
   *
   * @param input the JSON object
   * @return the configured fluid system
   */
  private static SystemInterface createFluidFromInput(JsonObject input) {
    String model = input.has("model") ? input.get("model").getAsString().toUpperCase() : "SRK";
    double tempK = input.has("reservoirTemperature_C")
        ? input.get("reservoirTemperature_C").getAsDouble() + 273.15
        : 373.15;
    double pBara =
        input.has("reservoirPressure_bara") ? input.get("reservoirPressure_bara").getAsDouble()
            : 200.0;
    SystemInterface fluid;
    switch (model.toUpperCase()) {
      case "PR":
        fluid = new SystemPrEos(tempK, pBara);
        break;
      default:
        fluid = new SystemSrkEos(tempK, pBara);
        break;
    }
    JsonObject comps = input.getAsJsonObject("components");
    for (Map.Entry<String, JsonElement> entry : comps.entrySet()) {
      fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
    }
    String mixingRule = input.has("mixingRule") ? input.get("mixingRule").getAsString() : "classic";
    fluid.setMixingRule(mixingRule);
    fluid.setMultiPhaseCheck(true);
    return fluid;
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
