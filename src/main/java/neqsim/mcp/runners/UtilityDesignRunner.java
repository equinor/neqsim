package neqsim.mcp.runners;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.util.utilitydesign.Boiler;
import neqsim.process.util.utilitydesign.Deaerator;
import neqsim.process.util.utilitydesign.NitrogenSystem;
import neqsim.process.util.utilitydesign.RefrigerationCycle;
import neqsim.process.util.utilitydesign.SteamNetwork;

/**
 * Screening-level utility-system design runner for MCP integration.
 *
 * <p>
 * Dispatches a JSON request to one of the deterministic utility-sizing models in
 * {@link neqsim.process.util.utilitydesign}: a fired steam {@link Boiler}, a {@link Deaerator}, a vapour-compression
 * {@link RefrigerationCycle}, an on-site {@link NitrogenSystem} generator, or a multi-pressure {@link SteamNetwork}
 * header cascade. Each model returns a schema-versioned JSON result with the design basis, sized duties/flows/power,
 * and associated CO<sub>2</sub> emissions and operating cost. The models are intended for early-stage utility
 * screening, not detailed mechanical design.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class UtilityDesignRunner {

  /**
   * Private constructor — all methods are static.
   */
  private UtilityDesignRunner() {
  }

  /**
   * Runs a utility-system design calculation from a JSON definition.
   *
   * @param json JSON with a {@code utilityType} ({@code boiler|deaerator|refrigeration|nitrogen|steamNetwork}) and the
   * corresponding design-basis parameters
   * @return JSON string with the schema-versioned design result, or a JSON error object
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String utilityType = input.has("utilityType") ? input.get("utilityType").getAsString() : "";
      if (utilityType == null) {
        utilityType = "";
      }
      String type = utilityType.trim().toLowerCase();
      if ("boiler".equals(type)) {
        return designBoiler(input);
      } else if ("deaerator".equals(type)) {
        return designDeaerator(input);
      } else if ("refrigeration".equals(type)) {
        return designRefrigeration(input);
      } else if ("nitrogen".equals(type)) {
        return designNitrogen(input);
      } else if ("steamnetwork".equals(type)) {
        return designSteamNetwork(input);
      } else {
        return errorJson("Unknown utilityType: '" + utilityType
            + "'. Supported: boiler, deaerator, refrigeration, nitrogen, steamNetwork");
      }
    } catch (Exception e) {
      return errorJson("Utility design failed: " + e.getMessage());
    }
  }

  /**
   * Designs a fired steam boiler package.
   *
   * @param input JSON design basis with optional {@code duties} array of {name, dutyKW}
   * @return JSON string with boiler sizing results
   */
  private static String designBoiler(JsonObject input) {
    Boiler boiler = new Boiler(getString(input, "name", "Boiler"));
    if (input.has("boilerEfficiency")) {
      boiler.setBoilerEfficiency(input.get("boilerEfficiency").getAsDouble());
    }
    if (input.has("fuelLowHeatingValueMJperKg")) {
      boiler.setFuelLowHeatingValueMJperKg(input.get("fuelLowHeatingValueMJperKg").getAsDouble());
    }
    if (input.has("steamEnthalpyRiseKJperKg")) {
      boiler.setSteamEnthalpyRiseKJperKg(input.get("steamEnthalpyRiseKJperKg").getAsDouble());
    }
    if (input.has("annualOperatingHours")) {
      boiler.setAnnualOperatingHours(input.get("annualOperatingHours").getAsDouble());
    }
    boolean added = false;
    if (input.has("duties") && input.get("duties").isJsonArray()) {
      JsonArray duties = input.getAsJsonArray("duties");
      for (JsonElement el : duties) {
        JsonObject d = el.getAsJsonObject();
        boiler.addSteamDuty(getString(d, "name", "Duty"), d.get("dutyKW").getAsDouble());
        added = true;
      }
    }
    if (!added && input.has("dutyKW")) {
      boiler.addSteamDuty(getString(input, "name", "Duty"), input.get("dutyKW").getAsDouble());
    }
    boiler.calculate();
    return boiler.toJson();
  }

  /**
   * Designs a deaerator.
   *
   * @param input JSON design basis
   * @return JSON string with deaerator sizing results
   */
  private static String designDeaerator(JsonObject input) {
    Deaerator deaerator = new Deaerator(getString(input, "name", "Deaerator"));
    if (input.has("feedwaterFlowKgh")) {
      deaerator.setFeedwaterFlowKgh(input.get("feedwaterFlowKgh").getAsDouble());
    }
    if (input.has("feedwaterInletTempC")) {
      deaerator.setFeedwaterInletTempC(input.get("feedwaterInletTempC").getAsDouble());
    }
    if (input.has("operatingPressureBara")) {
      deaerator.setOperatingPressureBara(input.get("operatingPressureBara").getAsDouble());
    }
    deaerator.calculate();
    return deaerator.toJson();
  }

  /**
   * Designs a vapour-compression refrigeration cycle.
   *
   * @param input JSON design basis with optional {@code duties} array of {name, dutyKW}
   * @return JSON string with refrigeration sizing results
   */
  private static String designRefrigeration(JsonObject input) {
    RefrigerationCycle cycle = new RefrigerationCycle(getString(input, "name", "Refrigeration"));
    if (input.has("refrigerant")) {
      cycle.setRefrigerant(input.get("refrigerant").getAsString());
    }
    if (input.has("evaporatorTempC")) {
      cycle.setEvaporatorTempC(input.get("evaporatorTempC").getAsDouble());
    }
    if (input.has("condenserTempC")) {
      cycle.setCondenserTempC(input.get("condenserTempC").getAsDouble());
    }
    if (input.has("cycleEfficiency")) {
      cycle.setCycleEfficiency(input.get("cycleEfficiency").getAsDouble());
    }
    if (input.has("electricityCostPerKWh")) {
      cycle.setElectricityCostPerKWh(input.get("electricityCostPerKWh").getAsDouble());
    }
    if (input.has("annualOperatingHours")) {
      cycle.setAnnualOperatingHours(input.get("annualOperatingHours").getAsDouble());
    }
    boolean added = false;
    if (input.has("duties") && input.get("duties").isJsonArray()) {
      JsonArray duties = input.getAsJsonArray("duties");
      for (JsonElement el : duties) {
        JsonObject d = el.getAsJsonObject();
        cycle.addRefrigerationDuty(getString(d, "name", "Duty"), d.get("dutyKW").getAsDouble());
        added = true;
      }
    }
    if (!added && input.has("dutyKW")) {
      cycle.addRefrigerationDuty(getString(input, "name", "Duty"), input.get("dutyKW").getAsDouble());
    }
    cycle.calculate();
    return cycle.toJson();
  }

  /**
   * Designs an on-site nitrogen generation system.
   *
   * @param input JSON design basis
   * @return JSON string with nitrogen system sizing results
   */
  private static String designNitrogen(JsonObject input) {
    NitrogenSystem nitrogen = new NitrogenSystem(getString(input, "name", "Nitrogen System"));
    if (input.has("nitrogenDemandNm3h")) {
      nitrogen.setNitrogenDemandNm3h(input.get("nitrogenDemandNm3h").getAsDouble());
    }
    if (input.has("purityPercent")) {
      nitrogen.setPurityPercent(input.get("purityPercent").getAsDouble());
    }
    if (input.has("deliveryPressureBarg")) {
      nitrogen.setDeliveryPressureBarg(input.get("deliveryPressureBarg").getAsDouble());
    }
    if (input.has("generationMethod")) {
      String method = input.get("generationMethod").getAsString().trim().toUpperCase();
      nitrogen.setGenerationMethod(NitrogenSystem.GenerationMethod.valueOf(method));
    }
    if (input.has("specificEnergyOverride")) {
      nitrogen.setSpecificEnergyOverride(input.get("specificEnergyOverride").getAsDouble());
    }
    if (input.has("electricityCostPerKWh")) {
      nitrogen.setElectricityCostPerKWh(input.get("electricityCostPerKWh").getAsDouble());
    }
    nitrogen.calculate();
    return nitrogen.toJson();
  }

  /**
   * Designs a multi-pressure steam header network with an integrated boiler and deaerator.
   *
   * @param input JSON design basis with {@code levels}, {@code demands}, and {@code localGeneration} arrays
   * @return JSON string with steam network sizing results
   */
  private static String designSteamNetwork(JsonObject input) {
    SteamNetwork network = new SteamNetwork(getString(input, "name", "Steam Network"));
    if (input.has("steamEnthalpyRiseKJperKg")) {
      network.setSteamEnthalpyRiseKJperKg(input.get("steamEnthalpyRiseKJperKg").getAsDouble());
    }
    if (input.has("condensateReturnFraction")) {
      network.setCondensateReturnFraction(input.get("condensateReturnFraction").getAsDouble());
    }
    if (input.has("levels") && input.get("levels").isJsonArray()) {
      for (JsonElement el : input.getAsJsonArray("levels")) {
        JsonObject lvl = el.getAsJsonObject();
        network.addLevel(getString(lvl, "name", "Level"), lvl.get("pressureBara").getAsDouble(),
            lvl.get("saturationTempC").getAsDouble());
      }
    }
    if (input.has("demands") && input.get("demands").isJsonArray()) {
      for (JsonElement el : input.getAsJsonArray("demands")) {
        JsonObject d = el.getAsJsonObject();
        network.addDemand(d.get("level").getAsString(), d.get("demandKgh").getAsDouble());
      }
    }
    if (input.has("localGeneration") && input.get("localGeneration").isJsonArray()) {
      for (JsonElement el : input.getAsJsonArray("localGeneration")) {
        JsonObject g = el.getAsJsonObject();
        network.setLocalGeneration(g.get("level").getAsString(), g.get("generationKgh").getAsDouble());
      }
    }
    network.calculate();
    return network.toJson();
  }

  /**
   * Reads an optional string property from a JSON object.
   *
   * @param obj the JSON object
   * @param key the property name
   * @param fallback the value returned when the property is absent
   * @return the string value, or {@code fallback} when absent
   */
  private static String getString(JsonObject obj, String key, String fallback) {
    return obj.has(key) ? obj.get(key).getAsString() : fallback;
  }

  /**
   * Creates an error JSON response.
   *
   * @param message the error message
   * @return JSON string with status error
   */
  private static String errorJson(String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("message", message);
    return error.toString();
  }
}
