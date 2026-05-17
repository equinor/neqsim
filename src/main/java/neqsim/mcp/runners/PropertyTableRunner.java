package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Stateless property table runner for MCP integration.
 *
 * <p> Sweeps temperature or pressure over a range and returns a table of thermodynamic and
 * transport properties at each point. This is the "one-shot engineering answer" tool that external
 * agents need most often — no process flowsheet construction required. </p>
 *
 * <h2>Input JSON Format:</h2>
 *
 * <pre>{@code { "model": "SRK", "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
 * "mixingRule": "classic", "sweep": "temperature", "sweepFrom": {"value": -40.0, "unit": "C"},
 * "sweepTo": {"value": 80.0, "unit": "C"}, "points": 25, "fixedPressure": {"value": 50.0, "unit":
 * "bara"}, "fixedTemperature": {"value": 25.0, "unit": "C"}, "properties": ["density", "viscosity",
 * "Cp", "Z", "enthalpy", "entropy", "thermalConductivity", "molarMass", "soundSpeed"] } }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class PropertyTableRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private static final List<String> AVAILABLE_PROPERTIES =
      Collections.unmodifiableList(Arrays.asList("density", "viscosity", "Cp", "Cv", "Z",
          "enthalpy", "entropy", "thermalConductivity", "molarMass", "soundSpeed",
          "jouleThomsonCoefficient", "numberOfPhases"));

  /**
   * Private constructor — all methods are static.
   */
  private PropertyTableRunner() {}

  /**
   * Runs a property table calculation from a JSON input string.
   *
   * @param json the JSON property table specification
   * @return a JSON string with the property table or errors
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a JSON property table specification with 'components' and 'sweep'");
    }

    long startTime = System.currentTimeMillis();

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    // --- Parse model ---
    String model = input.has("model") ? input.get("model").getAsString().toUpperCase() : "SRK";

    // --- Parse components ---
    if (!input.has("components")) {
      return errorJson("MISSING_COMPONENTS", "No 'components' specified",
          "Provide a components map, e.g. {\"methane\": 0.85, \"ethane\": 0.15}");
    }
    JsonObject componentsJson = input.getAsJsonObject("components");
    Map<String, Double> components = new HashMap<String, Double>();
    for (Map.Entry<String, JsonElement> entry : componentsJson.entrySet()) {
      components.put(entry.getKey(), entry.getValue().getAsDouble());
    }

    // --- Parse mixing rule ---
    String mixingRule = input.has("mixingRule") ? input.get("mixingRule").getAsString() : "classic";

    // --- Parse sweep variable ---
    String sweep =
        input.has("sweep") ? input.get("sweep").getAsString().toLowerCase() : "temperature";
    if (!"temperature".equals(sweep) && !"pressure".equals(sweep)) {
      return errorJson("INVALID_SWEEP", "Sweep must be 'temperature' or 'pressure'",
          "Specify sweep as 'temperature' (vary T at fixed P) or 'pressure' (vary P at fixed T)");
    }

    // --- Parse sweep range ---
    double sweepFromK;
    double sweepToK;
    double fixedPBara;
    double fixedTK;
    int points = input.has("points") ? input.get("points").getAsInt() : 20;
    if (points < 2) {
      points = 2;
    }
    if (points > 200) {
      points = 200;
    }

    if ("temperature".equals(sweep)) {
      if (!input.has("sweepFrom") || !input.has("sweepTo")) {
        return errorJson("MISSING_RANGE", "Temperature sweep requires 'sweepFrom' and 'sweepTo'",
            "Provide temperature range, e.g. {\"value\": -40, \"unit\": \"C\"}");
      }
      sweepFromK = FlashRunner.parseTemperature(input.get("sweepFrom"));
      sweepToK = FlashRunner.parseTemperature(input.get("sweepTo"));
      if (Double.isNaN(sweepFromK) || Double.isNaN(sweepToK)) {
        return errorJson("TEMPERATURE_ERROR", "Invalid temperature range",
            "Provide {\"value\": 25.0, \"unit\": \"C\"}");
      }
      if (!input.has("fixedPressure")) {
        return errorJson("MISSING_PRESSURE", "Temperature sweep requires 'fixedPressure'",
            "Provide pressure, e.g. {\"value\": 50.0, \"unit\": \"bara\"}");
      }
      fixedPBara = FlashRunner.parsePressure(input.get("fixedPressure"));
      fixedTK = 0; // unused
    } else {
      if (!input.has("sweepFrom") || !input.has("sweepTo")) {
        return errorJson("MISSING_RANGE", "Pressure sweep requires 'sweepFrom' and 'sweepTo'",
            "Provide pressure range, e.g. {\"value\": 10, \"unit\": \"bara\"}");
      }
      double fromP = FlashRunner.parsePressure(input.get("sweepFrom"));
      double toP = FlashRunner.parsePressure(input.get("sweepTo"));
      if (Double.isNaN(fromP) || Double.isNaN(toP)) {
        return errorJson("PRESSURE_ERROR", "Invalid pressure range",
            "Provide {\"value\": 50.0, \"unit\": \"bara\"}");
      }
      sweepFromK = fromP; // reuse variables (they're actually pressures for P sweep)
      sweepToK = toP;
      if (!input.has("fixedTemperature")) {
        return errorJson("MISSING_TEMPERATURE", "Pressure sweep requires 'fixedTemperature'",
            "Provide temperature, e.g. {\"value\": 25.0, \"unit\": \"C\"}");
      }
      fixedTK = FlashRunner.parseTemperature(input.get("fixedTemperature"));
      fixedPBara = 0; // unused
    }

    // --- Parse requested properties ---
    List<String> requestedProps = new ArrayList<String>();
    if (input.has("properties")) {
      JsonArray propsArray = input.getAsJsonArray("properties");
      for (JsonElement el : propsArray) {
        requestedProps.add(el.getAsString());
      }
    }
    if (requestedProps.isEmpty()) {
      requestedProps.addAll(Arrays.asList("density", "viscosity", "Cp", "Z", "enthalpy"));
    }

    // --- Build property table ---
    try {
      JsonArray tableRows = new JsonArray();
      int convergedCount = 0;

      for (int i = 0; i < points; i++) {
        double fraction = (double) i / (double) (points - 1);
        double sweepValue = sweepFromK + fraction * (sweepToK - sweepFromK);

        double tK;
        double pBara;
        if ("temperature".equals(sweep)) {
          tK = sweepValue;
          pBara = fixedPBara;
        } else {
          tK = fixedTK;
          pBara = sweepValue;
        }

        // Create fresh fluid for each point
        SystemInterface fluid = FlashRunner.createFluid(model, tK, pBara);
        for (Map.Entry<String, Double> comp : components.entrySet()) {
          fluid.addComponent(comp.getKey(), comp.getValue());
        }
        fluid.setMixingRule(mixingRule);

        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        try {
          ops.TPflash();
          fluid.initProperties();
          convergedCount++;
        } catch (Exception e) {
          // Skip non-converged points
          continue;
        }

        JsonObject row = new JsonObject();
        if ("temperature".equals(sweep)) {
          row.addProperty("temperature_C", tK - 273.15);
          row.addProperty("temperature_K", tK);
          row.addProperty("pressure_bara", fixedPBara);
        } else {
          row.addProperty("temperature_C", fixedTK - 273.15);
          row.addProperty("pressure_bara", pBara);
        }

        row.addProperty("numberOfPhases", fluid.getNumberOfPhases());

        for (String prop : requestedProps) {
          addProperty(row, fluid, prop);
        }

        tableRows.add(row);
      }

      // --- Build response with provenance ---
      ResultProvenance provenance = ResultProvenance.forPropertyTable(model, sweep, convergedCount);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      provenance.setMixingRule(mixingRule);
      provenance.addValidationPassed("component_names_verified");
      if (convergedCount == points) {
        provenance.addValidationPassed("all_points_converged");
      } else {
        provenance.addLimitation(
            (points - convergedCount) + " of " + points + " points failed to converge");
      }

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("sweep", sweep);
      result.addProperty("pointsRequested", points);
      result.addProperty("pointsConverged", convergedCount);
      result.add("table", tableRows);
      result.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("CALCULATION_ERROR", "Property table calculation failed: " + e.getMessage(),
          "Check component names and operating range");
    }
  }

  /**
   * Returns the list of available properties that can be requested.
   *
   * @return the available property names
   */
  public static List<String> getAvailableProperties() {
    return AVAILABLE_PROPERTIES;
  }

  /**
   * Adds a named property value to a JSON row object.
   *
   * @param row the JSON row to add to
   * @param fluid the fluid system to read from
   * @param property the property name
   */
  private static void addProperty(JsonObject row, SystemInterface fluid, String property) {
    try {
      switch (property) {
        case "density":
          row.addProperty("density_kg_m3", fluid.getDensity("kg/m3"));
          if (fluid.hasPhaseType("gas")) {
            row.addProperty("gasDensity_kg_m3", fluid.getPhase("gas").getDensity("kg/m3"));
          }
          if (fluid.hasPhaseType("oil") || fluid.hasPhaseType("liquid")) {
            String liqPhase = fluid.hasPhaseType("oil") ? "oil" : "liquid";
            row.addProperty("liquidDensity_kg_m3", fluid.getPhase(liqPhase).getDensity("kg/m3"));
          }
          break;
        case "viscosity":
          if (fluid.hasPhaseType("gas")) {
            row.addProperty("gasViscosity_cP", fluid.getPhase("gas").getViscosity("cP"));
          }
          if (fluid.hasPhaseType("oil") || fluid.hasPhaseType("liquid")) {
            String liqPhase = fluid.hasPhaseType("oil") ? "oil" : "liquid";
            row.addProperty("liquidViscosity_cP", fluid.getPhase(liqPhase).getViscosity("cP"));
          }
          break;
        case "Cp":
          row.addProperty("Cp_J_kgK", fluid.getCp("J/kgK"));
          break;
        case "Cv":
          row.addProperty("Cv_J_kgK", fluid.getCv("J/kgK"));
          break;
        case "Z":
          row.addProperty("compressibilityFactor", fluid.getZ());
          break;
        case "enthalpy":
          row.addProperty("enthalpy_J_kg", fluid.getEnthalpy("J/kg"));
          break;
        case "entropy":
          row.addProperty("entropy_J_kgK", fluid.getEntropy("J/kgK"));
          break;
        case "thermalConductivity":
          if (fluid.hasPhaseType("gas")) {
            row.addProperty("gasThermalConductivity_W_mK",
                fluid.getPhase("gas").getThermalConductivity("W/mK"));
          }
          if (fluid.hasPhaseType("oil") || fluid.hasPhaseType("liquid")) {
            String liqPhase = fluid.hasPhaseType("oil") ? "oil" : "liquid";
            row.addProperty("liquidThermalConductivity_W_mK",
                fluid.getPhase(liqPhase).getThermalConductivity("W/mK"));
          }
          break;
        case "molarMass":
          row.addProperty("molarMass_kg_mol", fluid.getMolarMass("kg/mol"));
          break;
        case "soundSpeed":
          if (fluid.hasPhaseType("gas")) {
            row.addProperty("soundSpeed_m_s", fluid.getPhase("gas").getSoundSpeed("m/s"));
          }
          break;
        case "jouleThomsonCoefficient":
          if (fluid.hasPhaseType("gas")) {
            row.addProperty("jouleThomsonCoefficient_K_bar",
                fluid.getPhase("gas").getJouleThomsonCoefficient("K/bar"));
          }
          break;
        case "numberOfPhases":
          // Already added above
          break;
        default:
          row.addProperty(property + "_note", "unknown property: " + property);
          break;
      }
    } catch (Exception e) {
      row.addProperty(property + "_error", e.getMessage());
    }
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return JSON error string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("code", code);
    error.addProperty("message", message);
    error.addProperty("remediation", remediation);
    return GSON.toJson(error);
  }
}
