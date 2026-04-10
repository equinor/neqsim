package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.automation.SimulationVariable;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Cross-validation runner that compares the same process model under different thermodynamic models
 * (equations of state) to quantify model-selection risk.
 *
 * <p>
 * This is a key enabler for UniSim–NeqSim cooperation: an engineer can export a UniSim model
 * (typically Peng-Robinson), convert it to NeqSim JSON, then run cross-validation against CPA,
 * GERG-2008, or other EoS that UniSim does not offer — all in a single MCP tool call.
 * </p>
 *
 * <h2>Input JSON Format:</h2>
 *
 * <pre>{@code { "baseProcess": { ... standard process JSON ... }, "models": ["SRK", "PR", "CPA",
 * "GERG2008"], "compareVariables": [ {"address": "HP Sep.gasOutStream.temperature", "unit": "C"},
 * {"address": "Compressor.power", "unit": "kW"}, {"address": "HP Sep.gasOutStream.density", "unit":
 * "kg/m3"} ], "tolerances": { "temperature": 2.0, "pressure": 0.5, "density": 5.0, "default": 10.0
 * } } }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class CrossValidationRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Maximum number of EoS models per comparison to prevent resource exhaustion. */
  private static final int MAX_MODELS = 10;

  private CrossValidationRunner() {}

  /**
   * Runs the same process definition under multiple thermodynamic models and compares key output
   * variables across all models.
   *
   * @param json JSON specification with baseProcess, models, and compareVariables
   * @return JSON string with per-model results, deviations, and risk flags
   */
  public static String crossValidate(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a JSON with 'baseProcess', 'models' array, and 'compareVariables' array");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure well-formed JSON");
    }

    // --- Validate required fields ---
    if (!input.has("baseProcess")) {
      return errorJson("MISSING_FIELD", "'baseProcess' is required",
          "Include the standard NeqSim process JSON under 'baseProcess'");
    }
    if (!input.has("models") || !input.get("models").isJsonArray()) {
      return errorJson("MISSING_FIELD", "'models' array is required",
          "Provide an array of EoS names, e.g. [\"SRK\", \"PR\", \"CPA\"]");
    }

    JsonObject baseProcess = input.getAsJsonObject("baseProcess");
    JsonArray modelsArray = input.getAsJsonArray("models");

    if (modelsArray.size() < 2) {
      return errorJson("INSUFFICIENT_MODELS", "Need at least 2 models for cross-validation",
          "Provide 2+ model names in the 'models' array");
    }
    if (modelsArray.size() > MAX_MODELS) {
      return errorJson("TOO_MANY_MODELS", "Maximum " + MAX_MODELS + " models per cross-validation",
          "Reduce the number of models");
    }

    // Parse compare variables
    List<CompareSpec> compareSpecs = new ArrayList<>();
    if (input.has("compareVariables") && input.get("compareVariables").isJsonArray()) {
      for (JsonElement elem : input.getAsJsonArray("compareVariables")) {
        JsonObject spec = elem.getAsJsonObject();
        compareSpecs.add(new CompareSpec(spec.get("address").getAsString(),
            spec.has("unit") ? spec.get("unit").getAsString() : ""));
      }
    }

    // Parse tolerances
    Map<String, Double> tolerances = new LinkedHashMap<>();
    if (input.has("tolerances") && input.get("tolerances").isJsonObject()) {
      for (Map.Entry<String, JsonElement> entry : input.getAsJsonObject("tolerances").entrySet()) {
        tolerances.put(entry.getKey().toLowerCase(), entry.getValue().getAsDouble());
      }
    }
    double defaultTolerance = tolerances.getOrDefault("default", 10.0);

    // --- Run each model ---
    long totalStart = System.currentTimeMillis();
    List<ModelRun> modelRuns = new ArrayList<>();

    for (JsonElement modelElem : modelsArray) {
      String modelName = modelElem.getAsString();
      ModelRun run = runWithModel(baseProcess, modelName, compareSpecs);
      modelRuns.add(run);
    }

    // --- Compute cross-model statistics ---
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.addProperty("modelsCompared", modelsArray.size());
    result.addProperty("computationTimeMs", System.currentTimeMillis() - totalStart);

    // Per-model results
    JsonArray modelResults = new JsonArray();
    for (ModelRun run : modelRuns) {
      modelResults.add(run.toJson());
    }
    result.add("modelResults", modelResults);

    // Cross-model comparison for each variable
    if (!compareSpecs.isEmpty()) {
      JsonArray comparisons =
          buildComparisons(compareSpecs, modelRuns, tolerances, defaultTolerance);
      result.add("crossComparison", comparisons);
    }

    // Risk assessment summary
    result.add("riskAssessment",
        buildRiskAssessment(compareSpecs, modelRuns, tolerances, defaultTolerance));

    return GSON.toJson(result);
  }

  /**
   * Runs the process with a specific EoS model, extracting the requested variables.
   *
   * @param baseProcess the base process JSON definition
   * @param modelName the EoS model name to use
   * @param compareSpecs list of variables to extract after simulation
   * @return a ModelRun containing extracted values and convergence status
   */
  private static ModelRun runWithModel(JsonObject baseProcess, String modelName,
      List<CompareSpec> compareSpecs) {
    ModelRun run = new ModelRun(modelName);
    long start = System.currentTimeMillis();

    try {
      // Clone the process JSON and override the model
      JsonObject processJson = baseProcess.deepCopy();
      if (processJson.has("fluid") && processJson.get("fluid").isJsonObject()) {
        processJson.getAsJsonObject("fluid").addProperty("model", modelName);
      }

      String jsonStr = GSON.toJson(processJson);
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(jsonStr);

      if (simResult.isError()) {
        run.error = "Simulation failed: " + simResult.getErrors().toString();
        run.converged = false;
      } else {
        run.converged = true;
        ProcessSystem process = simResult.getProcessSystem();

        // Extract requested variables
        if (!compareSpecs.isEmpty()) {
          ProcessAutomation auto = process.getAutomation();
          for (CompareSpec spec : compareSpecs) {
            try {
              double value = auto.getVariableValue(spec.address, spec.unit);
              run.values.put(spec.address, value);
            } catch (Exception e) {
              run.extractionErrors.put(spec.address, e.getMessage());
            }
          }
        }

        // Collect warnings
        for (String warning : simResult.getWarnings()) {
          run.warnings.add(warning);
        }
      }
    } catch (Exception e) {
      run.error = "Exception: " + e.getMessage();
      run.converged = false;
    }

    run.computationTimeMs = System.currentTimeMillis() - start;
    return run;
  }

  /**
   * Builds per-variable cross-model comparisons with deviation analysis.
   *
   * @param specs list of variables to compare
   * @param runs list of model run results
   * @param tolerances per-variable tolerance overrides
   * @param defaultTol default tolerance percentage
   * @return a JSON array of comparison objects
   */
  private static JsonArray buildComparisons(List<CompareSpec> specs, List<ModelRun> runs,
      Map<String, Double> tolerances, double defaultTol) {
    JsonArray comparisons = new JsonArray();

    for (CompareSpec spec : specs) {
      JsonObject comp = new JsonObject();
      comp.addProperty("address", spec.address);
      comp.addProperty("unit", spec.unit);

      // Collect values from all converged models
      List<Double> values = new ArrayList<>();
      JsonObject perModel = new JsonObject();
      for (ModelRun run : runs) {
        if (run.converged && run.values.containsKey(spec.address)) {
          double val = run.values.get(spec.address);
          values.add(val);
          perModel.addProperty(run.modelName, val);
        }
      }
      comp.add("values", perModel);

      if (values.size() >= 2) {
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double spread = max - min;
        double spreadPct = (mean != 0) ? (spread / Math.abs(mean)) * 100.0 : 0;

        comp.addProperty("min", min);
        comp.addProperty("max", max);
        comp.addProperty("mean", mean);
        comp.addProperty("spread", spread);
        comp.addProperty("spreadPercent", Math.round(spreadPct * 100.0) / 100.0);

        // Determine tolerance for this variable
        double tol = defaultTol;
        String addrLower = spec.address.toLowerCase();
        for (Map.Entry<String, Double> entry : tolerances.entrySet()) {
          if (addrLower.contains(entry.getKey())) {
            tol = entry.getValue();
            break;
          }
        }
        comp.addProperty("tolerancePercent", tol);
        boolean withinTolerance = spreadPct <= tol;
        comp.addProperty("withinTolerance", withinTolerance);
        comp.addProperty("riskLevel",
            withinTolerance ? "LOW" : (spreadPct <= tol * 2) ? "MEDIUM" : "HIGH");
      }

      comparisons.add(comp);
    }

    return comparisons;
  }

  /**
   * Builds an overall risk assessment summary.
   *
   * @param specs list of variables to assess
   * @param runs list of model run results
   * @param tolerances per-variable tolerance overrides
   * @param defaultTol default tolerance percentage
   * @return a JSON object with the risk assessment summary
   */
  private static JsonObject buildRiskAssessment(List<CompareSpec> specs, List<ModelRun> runs,
      Map<String, Double> tolerances, double defaultTol) {
    JsonObject assessment = new JsonObject();

    int totalVars = specs.size();
    int lowRisk = 0;
    int mediumRisk = 0;
    int highRisk = 0;
    int failedModels = 0;

    for (ModelRun run : runs) {
      if (!run.converged) {
        failedModels++;
      }
    }

    for (CompareSpec spec : specs) {
      List<Double> values = new ArrayList<>();
      for (ModelRun run : runs) {
        if (run.converged && run.values.containsKey(spec.address)) {
          values.add(run.values.get(spec.address));
        }
      }
      if (values.size() >= 2) {
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double spreadPct = (mean != 0) ? ((max - min) / Math.abs(mean)) * 100.0 : 0;

        double tol = defaultTol;
        String addrLower = spec.address.toLowerCase();
        for (Map.Entry<String, Double> entry : tolerances.entrySet()) {
          if (addrLower.contains(entry.getKey())) {
            tol = entry.getValue();
            break;
          }
        }

        if (spreadPct <= tol) {
          lowRisk++;
        } else if (spreadPct <= tol * 2) {
          mediumRisk++;
        } else {
          highRisk++;
        }
      }
    }

    assessment.addProperty("totalVariables", totalVars);
    assessment.addProperty("lowRisk", lowRisk);
    assessment.addProperty("mediumRisk", mediumRisk);
    assessment.addProperty("highRisk", highRisk);
    assessment.addProperty("failedModels", failedModels);

    String overall;
    if (highRisk > 0 || failedModels > 0) {
      overall = "HIGH — significant model-selection sensitivity detected";
    } else if (mediumRisk > 0) {
      overall = "MEDIUM — moderate sensitivity, review flagged variables";
    } else {
      overall = "LOW — results consistent across thermodynamic models";
    }
    assessment.addProperty("overallRisk", overall);

    // Recommendations
    JsonArray recommendations = new JsonArray();
    if (highRisk > 0) {
      recommendations.add("High-risk variables show >2x tolerance spread across EoS models. "
          + "Validate against experimental data before finalizing design.");
    }
    if (failedModels > 0) {
      recommendations.add(failedModels + " model(s) failed to converge. "
          + "Check if the fluid composition is compatible with those EoS.");
    }
    if (lowRisk == totalVars && failedModels == 0) {
      recommendations.add("All variables consistent across models. "
          + "Model selection has low impact on these results.");
    }
    assessment.add("recommendations", recommendations);

    return assessment;
  }

  // --- Helper classes ---

  private static class CompareSpec {
    final String address;
    final String unit;

    CompareSpec(String address, String unit) {
      this.address = address;
      this.unit = unit;
    }
  }

  private static class ModelRun {
    final String modelName;
    boolean converged;
    String error;
    long computationTimeMs;
    final Map<String, Double> values = new LinkedHashMap<>();
    final Map<String, String> extractionErrors = new LinkedHashMap<>();
    final List<String> warnings = new ArrayList<>();

    ModelRun(String modelName) {
      this.modelName = modelName;
    }

    JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("model", modelName);
      obj.addProperty("converged", converged);
      obj.addProperty("computationTimeMs", computationTimeMs);
      if (error != null) {
        obj.addProperty("error", error);
      }
      if (!values.isEmpty()) {
        JsonObject vals = new JsonObject();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
          vals.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("values", vals);
      }
      if (!extractionErrors.isEmpty()) {
        JsonObject errs = new JsonObject();
        for (Map.Entry<String, String> entry : extractionErrors.entrySet()) {
          errs.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("extractionErrors", errs);
      }
      if (!warnings.isEmpty()) {
        JsonArray warnsArr = new JsonArray();
        for (String w : warnings) {
          warnsArr.add(w);
        }
        obj.add("warnings", warnsArr);
      }
      return obj;
    }
  }

  private static String errorJson(String code, String message, String remediation) {
    JsonObject obj = new JsonObject();
    obj.addProperty("status", "error");
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    obj.add("error", err);
    return GSON.toJson(obj);
  }
}
