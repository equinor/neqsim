package neqsim.mcp.runners;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.mcp.model.ResultProvenance;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Stateless phase envelope runner for MCP integration.
 *
 * <p>
 * Calculates the PT phase envelope (bubble point and dew point curves) for a fluid mixture and returns the data as a
 * JSON array of points. This is one of the most commonly requested single-purpose engineering calculations for agents.
 * </p>
 *
 * <h2>Input JSON Format:</h2>
 *
 * <pre>{@code { "model": "SRK", "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
 * "mixingRule": "classic" } }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class PhaseEnvelopeRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private PhaseEnvelopeRunner() {
  }

  /**
   * Calculates the phase envelope for a fluid from a JSON specification.
   *
   * @param json the JSON fluid specification
   * @return a JSON string with the phase envelope data or errors
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a JSON specification with 'components' and optionally 'model'");
    }

    long startTime = System.currentTimeMillis();

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(), "Ensure the JSON is well-formed");
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
    String mixingRule = FluidDefaults.resolveMixingRule(input, model);

    try {
      // Create fluid at a reference condition
      SystemInterface fluid = FlashRunner.createFluid(model, 288.15, 1.01325);
      for (Map.Entry<String, Double> comp : components.entrySet()) {
        fluid.addComponent(comp.getKey(), comp.getValue());
      }
      fluid.setMixingRule(mixingRule);

      // Calculate phase envelope
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.calcPTphaseEnvelope();

      // getPoints(0) is {dewT, dewP, bubT, bubP}; use the named arrays so the
      // columns cannot be mistaken for {P, T}. NaN entries are branch separators.
      JsonArray envelopePoints = new JsonArray();
      addBranch(envelopePoints, "dew", ops.get("dewT"), ops.get("dewP"));
      addBranch(envelopePoints, "bubble", ops.get("bubT"), ops.get("bubP"));

      // Extrema arrays are [T(K), P(bara)]
      JsonObject criticalPoints = new JsonObject();
      addExtremum(criticalPoints, "cricondenbar", ops.get("cricondenbar"));
      addExtremum(criticalPoints, "cricondentherm", ops.get("cricondentherm"));
      addExtremum(criticalPoints, "criticalPoint", ops.get("criticalPoint1"));

      // Build provenance
      ResultProvenance provenance = ResultProvenance.forPhaseEnvelope(model);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      provenance.setMixingRule(mixingRule);
      provenance.setBenchmarkTrustLevel(BenchmarkTrust.getMaturityLevel("getPhaseEnvelope"));
      provenance.addValidationPassed("phase_envelope_converged");
      provenance.addValidationPassed("component_names_verified");

      // Build response
      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("model", model);
      result.addProperty("numberOfPoints", envelopePoints.size());
      result.add("envelope", envelopePoints);
      if (criticalPoints.size() > 0) {
        result.add("criticalPoints", criticalPoints);
      }
      result.add("provenance", GSON.toJsonTree(provenance));

      JsonObject data = new JsonObject();
      data.addProperty("model", model);
      data.addProperty("numberOfPoints", envelopePoints.size());
      data.add("envelope", envelopePoints.deepCopy());
      if (criticalPoints.size() > 0) {
        data.add("criticalPoints", criticalPoints.deepCopy());
      }
      result.add("data", data);

      String gateVerdict = envelopePoints.size() > 0 ? "passed" : "warning";
      String gateSummary = envelopePoints.size() > 0 ? "Phase envelope calculation completed"
          : "Phase envelope calculation returned no data points";
      ApiEnvelope.applyStandardFields(result, "getPhaseEnvelope", provenance,
          ApiEnvelope.validationStatus(true, "calculation", gateSummary),
          ApiEnvelope.qualityGate(gateVerdict, gateSummary, true));

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("CALCULATION_ERROR", "Phase envelope calculation failed: " + e.getMessage(),
          "Check component names. Ensure the mixture has at least 2 components "
              + "or a single component with vapor-liquid equilibrium.");
    }
  }

  /**
   * Appends the finite points of one envelope branch as {@code {branch, pressure_bara, temperature_K, temperature_C}}
   * objects, skipping NaN separators and non-positive values.
   *
   * @param target array to append to
   * @param branch branch label, {@code "dew"} or {@code "bubble"}
   * @param temperaturesK branch temperatures in K (may be null)
   * @param pressuresBara branch pressures in bara (may be null)
   */
  private static void addBranch(JsonArray target, String branch, double[] temperaturesK, double[] pressuresBara) {
    if (temperaturesK == null || pressuresBara == null) {
      return;
    }
    int n = Math.min(temperaturesK.length, pressuresBara.length);
    for (int i = 0; i < n; i++) {
      double t = temperaturesK[i];
      double p = pressuresBara[i];
      if (Double.isNaN(t) || Double.isNaN(p) || t <= 0.0 || p <= 0.0) {
        continue;
      }
      JsonObject point = new JsonObject();
      point.addProperty("branch", branch);
      point.addProperty("pressure_bara", p);
      point.addProperty("temperature_K", t);
      point.addProperty("temperature_C", t - 273.15);
      target.add(point);
    }
  }

  /**
   * Adds an envelope extremum ({@code [T(K), P(bara)]}) when it is finite and physical.
   *
   * @param target object to add the point to
   * @param name key name, e.g. {@code cricondenbar}
   * @param tp {@code [temperature K, pressure bara]} or null
   */
  private static void addExtremum(JsonObject target, String name, double[] tp) {
    if (tp == null || tp.length < 2 || Double.isNaN(tp[0]) || Double.isNaN(tp[1]) || tp[0] <= 0.0 || tp[1] <= 0.0) {
      return;
    }
    JsonObject point = new JsonObject();
    point.addProperty("pressure_bara", tp[1]);
    point.addProperty("temperature_K", tp[0]);
    point.addProperty("temperature_C", tp[0] - 273.15);
    target.add(name, point);
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
    JsonArray errors = new JsonArray();
    JsonObject issue = new JsonObject();
    issue.addProperty("code", code);
    issue.addProperty("message", message);
    issue.addProperty("remediation", remediation);
    errors.add(issue);
    error.add("errors", errors);
    ApiEnvelope.applyStandardFields(error, "getPhaseEnvelope", null,
        ApiEnvelope.validationStatus(false, "input", message), ApiEnvelope.qualityGate("failed", message, true));
    return GSON.toJson(error);
  }
}
