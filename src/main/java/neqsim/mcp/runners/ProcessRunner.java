package neqsim.mcp.runners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.mcp.model.ProcessResult;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Stateless process simulation runner for MCP integration.
 *
 * <p>
 * Accepts a JSON process definition, optionally pre-validates it using {@link Validator}, then
 * builds and runs the process using {@link ProcessSystem#fromJsonAndRun(String)}. Returns the
 * simulation result as a JSON string in the standard envelope format.
 * </p>
 *
 * <h2>Input JSON Format:</h2>
 *
 * <pre>{@code { "fluid": { "model": "SRK", "temperature": 298.15, "pressure": 50.0, "mixingRule":
 * "classic", "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05} }, "process": [
 * {"type": "Stream", "name": "feed", "properties": {"flowRate": [50000.0, "kg/hr"]}}, {"type":
 * "Separator", "name": "HP Sep", "inlet": "feed"}, {"type": "Compressor", "name": "Comp", "inlet":
 * "HP Sep.gasOut", "properties": {"outletPressure": [80.0, "bara"]}} ] } }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class ProcessRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private ProcessRunner() {}

  /**
   * Runs a process simulation from a JSON definition string.
   *
   * <p>
   * Delegates to {@link ProcessSystem#fromJsonAndRun(String)} and returns the result in the
   * standard JSON envelope format with status, report, and any warnings or errors.
   * </p>
   *
   * @param json the JSON process definition
   * @return a JSON string with the simulation result
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON process definition with 'fluid' and 'process' blocks");
    }

    long startTime = System.currentTimeMillis();
    try {
      SimulationResult result = ProcessSystem.fromJsonAndRun(json);
      String simJson = result.toJson();

      // Inject provenance into the response
      String model = extractModel(json);
      String mixingRule = extractMixingRule(json);
      int equipCount = extractEquipmentCount(json);
      ResultProvenance provenance = ResultProvenance.forProcess(model, mixingRule, equipCount);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      provenance.setConverged(!result.isError());

      if (!result.isError()) {
        provenance.addValidationPassed("Process simulation completed");
      }
      for (String warning : result.getWarnings()) {
        provenance.addLimitation("Warning: " + warning);
      }

      JsonObject simObj = JsonParser.parseString(simJson).getAsJsonObject();
      simObj.add("provenance", GSON.toJsonTree(provenance));
      return GSON.toJson(simObj);
    } catch (Exception e) {
      return errorJson("SIMULATION_ERROR", "Process simulation failed: " + e.getMessage(),
          "Check the JSON definition. Use Validator.validate() first to catch common issues.");
    }
  }

  /**
   * Validates and then runs a process simulation.
   *
   * <p>
   * First performs pre-flight validation using {@link Validator}. If validation finds errors,
   * returns them without running the simulation. If only warnings are found, proceeds with the
   * simulation and includes the validation warnings in the response.
   * </p>
   *
   * @param json the JSON process definition
   * @return a JSON string with validation issues and/or simulation results
   */
  public static String validateAndRun(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON process definition with 'fluid' and 'process' blocks");
    }

    long startTime = System.currentTimeMillis();

    // Pre-validate
    String validationJson = Validator.validate(json);
    JsonObject validation = JsonParser.parseString(validationJson).getAsJsonObject();

    if (!validation.get("valid").getAsBoolean()) {
      // Return validation errors without running
      JsonObject result = new JsonObject();
      result.addProperty("status", "error");
      result.addProperty("phase", "validation");
      result.add("validation", validation);
      return GSON.toJson(result);
    }

    // Run simulation
    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(json);
      String simJson = simResult.toJson();

      // Inject provenance
      String model = extractModel(json);
      String mixingRule = extractMixingRule(json);
      int equipCount = extractEquipmentCount(json);
      ResultProvenance provenance = ResultProvenance.forProcess(model, mixingRule, equipCount);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      provenance.setConverged(!simResult.isError());
      provenance.addValidationPassed("Pre-flight validation passed");

      if (!simResult.isError()) {
        provenance.addValidationPassed("Process simulation completed");
      }

      JsonObject simObj = JsonParser.parseString(simJson).getAsJsonObject();
      simObj.add("provenance", GSON.toJsonTree(provenance));

      // If there were validation warnings, merge them
      JsonArray valIssues = validation.getAsJsonArray("issues");
      if (valIssues != null && valIssues.size() > 0) {
        simObj.add("validationIssues", valIssues);
        for (int i = 0; i < valIssues.size(); i++) {
          provenance.addLimitation(
              "Validation warning: " + valIssues.get(i).getAsJsonObject().get("message"));
        }
      }

      return GSON.toJson(simObj);
    } catch (Exception e) {
      return errorJson("SIMULATION_ERROR", "Process simulation failed: " + e.getMessage(),
          "Check the JSON definition. Validation passed but simulation threw an exception.");
    }
  }

  /**
   * Runs a process simulation and returns a typed result.
   *
   * <p>
   * This is the typed counterpart to {@link #run(String)}. It accepts a JSON string (same format)
   * but returns a typed {@link ApiEnvelope} with a {@link ProcessResult} payload for direct Java
   * consumers.
   * </p>
   *
   * @param json the JSON process definition
   * @return an ApiEnvelope containing the ProcessResult on success, or errors on failure
   */
  public static ApiEnvelope<ProcessResult> runTyped(String json) {
    if (json == null || json.trim().isEmpty()) {
      return ApiEnvelope.error("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON process definition with 'fluid' and 'process' blocks");
    }

    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(json);

      if (simResult.isError()) {
        java.util.List<neqsim.mcp.model.DiagnosticIssue> issues =
            new java.util.ArrayList<neqsim.mcp.model.DiagnosticIssue>();
        for (SimulationResult.ErrorDetail err : simResult.getErrors()) {
          issues.add(neqsim.mcp.model.DiagnosticIssue.error(err.getCode(), err.getMessage(),
              err.getRemediation()));
        }
        return ApiEnvelope.errors(issues);
      }

      ProcessSystem process = simResult.getProcessSystem();
      String name = process != null ? process.getName() : "unknown";
      String reportJson = simResult.getReportJson();

      ProcessResult result = new ProcessResult(name, process, reportJson);
      ApiEnvelope<ProcessResult> envelope = ApiEnvelope.success(result);

      for (String warning : simResult.getWarnings()) {
        envelope.addWarning(warning);
      }

      return envelope;
    } catch (Exception e) {
      return ApiEnvelope.error("SIMULATION_ERROR", "Process simulation failed: " + e.getMessage(),
          "Check the JSON definition. Use Validator.validate() first to catch common issues.");
    }
  }

  /**
   * Extracts the EOS model name from the input JSON.
   *
   * @param json the input JSON string
   * @return the model name, or "SRK" as default
   */
  private static String extractModel(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (root.has("fluid") && root.getAsJsonObject("fluid").has("model")) {
        return root.getAsJsonObject("fluid").get("model").getAsString();
      }
    } catch (Exception ignored) {
    }
    return "SRK";
  }

  /**
   * Extracts the mixing rule from the input JSON.
   *
   * @param json the input JSON string
   * @return the mixing rule, or "classic" as default
   */
  private static String extractMixingRule(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (root.has("fluid") && root.getAsJsonObject("fluid").has("mixingRule")) {
        return root.getAsJsonObject("fluid").get("mixingRule").getAsString();
      }
    } catch (Exception ignored) {
    }
    return "classic";
  }

  /**
   * Counts the number of equipment entries in the process definition.
   *
   * @param json the input JSON string
   * @return the equipment count, or 0 if not parseable
   */
  private static int extractEquipmentCount(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (root.has("process") && root.get("process").isJsonArray()) {
        return root.getAsJsonArray("process").size();
      }
      if (root.has("equipment") && root.get("equipment").isJsonArray()) {
        return root.getAsJsonArray("equipment").size();
      }
    } catch (Exception ignored) {
    }
    return 0;
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
    JsonObject result = new JsonObject();
    result.addProperty("status", "error");

    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    if (remediation != null) {
      err.addProperty("remediation", remediation);
    }
    errors.add(err);
    result.add("errors", errors);

    return GSON.toJson(result);
  }
}
