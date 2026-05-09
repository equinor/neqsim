package neqsim.mcp.runners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.mcp.model.ProcessResult;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.processmodel.JsonProcessBuilder;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Stateless process simulation runner for MCP integration.
 *
 * <p>
 * Accepts a JSON process definition, optionally pre-validates it using {@link Validator}, then
 * builds and runs either a {@link ProcessSystem} using {@link ProcessSystem#fromJsonAndRun(String)}
 * or a multi-area {@link ProcessModel} when the JSON contains a top-level {@code areas} object.
 * Returns the simulation result as a JSON string in the standard envelope format.
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
      String normalizedJson = normalizeProcessJson(json);
      if (isProcessModelJson(normalizedJson)) {
        return runProcessModel(normalizedJson, startTime, false, null);
      }
      return runProcessSystem(normalizedJson, startTime, false, null);
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
    String normalizedJson = normalizeProcessJson(json);

    // Pre-validate
    String validationJson = Validator.validate(normalizedJson);
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
      JsonArray valIssues = validation.getAsJsonArray("issues");
      if (isProcessModelJson(normalizedJson)) {
        return runProcessModel(normalizedJson, startTime, true, valIssues);
      }
      return runProcessSystem(normalizedJson, startTime, true, valIssues);
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
      String normalizedJson = normalizeProcessJson(json);
      if (isProcessModelJson(normalizedJson)) {
        return runTypedProcessModel(normalizedJson);
      }

      SimulationResult simResult = ProcessSystem.fromJsonAndRun(normalizedJson);

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
   * Runs a normalized single-area process-system JSON definition.
   *
   * @param normalizedJson the normalized JSON process definition
   * @param startTime the wall-clock start time in milliseconds
   * @param preValidationPassed true if {@link Validator} was already run successfully
   * @param validationIssues optional validation issues to include in the response
   * @return JSON response containing simulation status, report, warnings, and provenance
   */
  private static String runProcessSystem(String normalizedJson, long startTime,
      boolean preValidationPassed, JsonArray validationIssues) {
    SimulationResult result = ProcessSystem.fromJsonAndRun(normalizedJson);
    String simJson = result.toJson();

    String model = extractModel(normalizedJson);
    String mixingRule = extractMixingRule(normalizedJson);
    int equipCount = extractEquipmentCount(normalizedJson);
    ResultProvenance provenance = ResultProvenance.forProcess(model, mixingRule, equipCount);
    provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
    provenance.setConverged(!result.isError());

    if (preValidationPassed) {
      provenance.addValidationPassed("Pre-flight validation passed");
    }
    if (!result.isError()) {
      provenance.addValidationPassed("Process simulation completed");
    }
    for (String warning : result.getWarnings()) {
      provenance.addLimitation("Warning: " + warning);
    }
    addValidationIssueLimitations(provenance, validationIssues);

    JsonObject simObj = JsonParser.parseString(simJson).getAsJsonObject();
    addValidationIssues(simObj, validationIssues);
    simObj.add("provenance", GSON.toJsonTree(provenance));
    return GSON.toJson(simObj);
  }

  /**
   * Runs a normalized multi-area process-model JSON definition.
   *
   * @param normalizedJson the normalized JSON containing a top-level {@code areas} object
   * @param startTime the wall-clock start time in milliseconds
   * @param preValidationPassed true if {@link Validator} was already run successfully
   * @param validationIssues optional validation issues to include in the response
   * @return JSON response containing model status, area metadata, report, warnings, and provenance
   */
  private static String runProcessModel(String normalizedJson, long startTime,
      boolean preValidationPassed, JsonArray validationIssues) {
    ProcessModelBuildResult buildResult = buildProcessModel(normalizedJson);
    if (!buildResult.errors.isEmpty()) {
      return errorJson(buildResult.errors, buildResult.warnings);
    }

    try {
      buildResult.model.run();
    } catch (Exception e) {
      return errorJson("SIMULATION_ERROR", "Process model simulation failed: " + e.getMessage(),
          "Check area wiring, recycle settings, and equipment parameters in the 'areas' object.");
    }

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.addProperty("processModelName", "json-process-model");
    result.addProperty("areaCount", buildResult.model.size());
    result.add("areas", toJsonArray(buildResult.model.getProcessSystemNames()));

    if (!buildResult.warnings.isEmpty()) {
      JsonArray warnings = new JsonArray();
      for (String warning : buildResult.warnings) {
        warnings.add(warning);
      }
      result.add("warnings", warnings);
    }

    String reportJson = null;
    try {
      reportJson = buildResult.model.getReport_json();
    } catch (Exception e) {
      buildResult.warnings.add("ProcessModel report generation failed: " + e.getMessage());
    }
    if (reportJson != null) {
      try {
        result.add("report", JsonParser.parseString(reportJson));
      } catch (Exception e) {
        result.addProperty("report", reportJson);
      }
    }
    result.addProperty("convergenceSummary", buildResult.model.getConvergenceSummary());

    ResultProvenance provenance = ResultProvenance.forProcess(extractModel(normalizedJson),
        extractMixingRule(normalizedJson), extractEquipmentCount(normalizedJson));
    provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
    provenance.setConverged(buildResult.model.isModelConverged() || buildResult.model.size() <= 1);
    provenance.addAssumption("Multi-area ProcessModel executed from top-level JSON areas");
    provenance.addLimitation("ProcessModel contains " + buildResult.model.size()
        + " areas - verify inter-area stream references and convergence summary");
    if (preValidationPassed) {
      provenance.addValidationPassed("Pre-flight validation passed");
    }
    provenance.addValidationPassed("ProcessModel simulation completed");
    for (String warning : buildResult.warnings) {
      provenance.addLimitation("Warning: " + warning);
    }
    addValidationIssueLimitations(provenance, validationIssues);
    addValidationIssues(result, validationIssues);
    result.add("provenance", GSON.toJsonTree(provenance));

    return GSON.toJson(result);
  }

  /**
   * Runs a normalized process-model JSON definition and returns a typed MCP envelope.
   *
   * @param normalizedJson the normalized JSON containing a top-level {@code areas} object
   * @return typed process result containing the built {@link ProcessModel}, or errors on failure
   */
  private static ApiEnvelope<ProcessResult> runTypedProcessModel(String normalizedJson) {
    ProcessModelBuildResult buildResult = buildProcessModel(normalizedJson);
    if (!buildResult.errors.isEmpty()) {
      java.util.List<neqsim.mcp.model.DiagnosticIssue> issues =
          new java.util.ArrayList<neqsim.mcp.model.DiagnosticIssue>();
      for (SimulationResult.ErrorDetail err : buildResult.errors) {
        issues.add(neqsim.mcp.model.DiagnosticIssue.error(err.getCode(), err.getMessage(),
            err.getRemediation()));
      }
      return ApiEnvelope.errors(issues);
    }

    try {
      buildResult.model.run();
      ProcessResult result = new ProcessResult("json-process-model", buildResult.model,
          buildResult.model.getReport_json(), buildResult.model.getProcessSystemNames());
      ApiEnvelope<ProcessResult> envelope = ApiEnvelope.success(result);
      for (String warning : buildResult.warnings) {
        envelope.addWarning(warning);
      }
      return envelope;
    } catch (Exception e) {
      return ApiEnvelope.error("SIMULATION_ERROR",
          "Process model simulation failed: " + e.getMessage(),
          "Check area wiring, recycle settings, and equipment parameters in the 'areas' object.");
    }
  }

  /**
   * Builds a {@link ProcessModel} from top-level {@code areas} JSON while preserving area-level
   * build errors for MCP responses.
   *
   * @param normalizedJson normalized JSON containing named process areas
   * @return build result containing the model, errors, and warnings
   */
  private static ProcessModelBuildResult buildProcessModel(String normalizedJson) {
    ProcessModelBuildResult result = new ProcessModelBuildResult();
    try {
      JsonObject root = JsonParser.parseString(normalizedJson).getAsJsonObject();
      if (!root.has("areas") || !root.get("areas").isJsonObject()) {
        result.errors.add(new SimulationResult.ErrorDetail("MISSING_AREAS",
            "ProcessModel JSON must contain an 'areas' object", null,
            "Use {\"areas\": {\"areaName\": {\"fluid\": {...}, \"process\": [...]}}}"));
        return result;
      }

      JsonObject areas = root.getAsJsonObject("areas");
      if (areas.entrySet().isEmpty()) {
        result.errors.add(new SimulationResult.ErrorDetail("EMPTY_AREAS",
            "ProcessModel JSON contains no process areas", null,
            "Add at least one named area under the 'areas' object"));
        return result;
      }

      applyProcessModelExecutionSettings(root, result.model);

      for (Map.Entry<String, com.google.gson.JsonElement> entry : areas.entrySet()) {
        String areaName = entry.getKey();
        if (!entry.getValue().isJsonObject()) {
          result.errors.add(new SimulationResult.ErrorDetail("INVALID_AREA",
              "Area '" + areaName + "' must be a JSON object", areaName,
              "Provide each area as a standard ProcessSystem JSON object"));
          continue;
        }
        SimulationResult areaResult = new JsonProcessBuilder().build(entry.getValue().toString());
        if (areaResult.isSuccess() && areaResult.getProcessSystem() != null) {
          result.model.add(areaName, areaResult.getProcessSystem());
          for (String warning : areaResult.getWarnings()) {
            result.warnings.add("Area '" + areaName + "': " + warning);
          }
        } else {
          for (SimulationResult.ErrorDetail error : areaResult.getErrors()) {
            result.errors.add(new SimulationResult.ErrorDetail(error.getCode(),
                "Area '" + areaName + "': " + error.getMessage(), areaName,
                error.getRemediation()));
          }
          for (String warning : areaResult.getWarnings()) {
            result.warnings.add("Area '" + areaName + "': " + warning);
          }
        }
      }
      if (result.errors.isEmpty() && root.has("interAreaLinks")
          && root.get("interAreaLinks").isJsonArray()) {
        result.warnings
            .addAll(result.model.applyInterAreaLinks(root.getAsJsonArray("interAreaLinks")));
      }
    } catch (Exception e) {
      result.errors.add(new SimulationResult.ErrorDetail("PROCESS_MODEL_PARSE_ERROR",
          "Failed to parse ProcessModel JSON: " + e.getMessage(), null,
          "Ensure the JSON has a top-level 'areas' object with valid area definitions"));
    }
    return result;
  }

  /**
   * Applies top-level execution controls from ProcessModel JSON.
   *
   * @param root root JSON object containing optional execution settings
   * @param model process model to configure before running
   */
  private static void applyProcessModelExecutionSettings(JsonObject root, ProcessModel model) {
    if (root.has("runStep")) {
      model.setRunStep(root.get("runStep").getAsBoolean());
    }
    if (root.has("maxIterations")) {
      model.setMaxIterations(root.get("maxIterations").getAsInt());
    }
    if (root.has("flowTolerance")) {
      model.setFlowTolerance(root.get("flowTolerance").getAsDouble());
    }
    if (root.has("temperatureTolerance")) {
      model.setTemperatureTolerance(root.get("temperatureTolerance").getAsDouble());
    }
    if (root.has("pressureTolerance")) {
      model.setPressureTolerance(root.get("pressureTolerance").getAsDouble());
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
      if (root.has("areas") && root.get("areas").isJsonObject()) {
        return extractAreaSummary(root.getAsJsonObject("areas"), "model", "SRK");
      }
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
      if (root.has("areas") && root.get("areas").isJsonObject()) {
        return extractAreaSummary(root.getAsJsonObject("areas"), "mixingRule", "classic");
      }
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
      if (root.has("areas") && root.get("areas").isJsonObject()) {
        int count = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("areas")
            .entrySet()) {
          count += extractEquipmentCount(entry.getValue().toString());
        }
        return count;
      }
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
   * Normalizes accepted process JSON variants to the canonical schema.
   *
   * <p>
   * Canonical schema expects {@code process} to be an array. Some legacy clients send
   * {@code {"process": {"equipment": [...]}}}. This method converts the legacy shape to the
   * canonical one while preserving all other fields.
   * </p>
   *
   * @param json raw process JSON
   * @return canonical JSON string (or original input if not parseable)
   */
  private static String normalizeProcessJson(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();

      if (root.has("areas") && root.get("areas").isJsonObject()) {
        JsonObject areas = root.getAsJsonObject("areas");
        JsonObject normalizedAreas = new JsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : areas.entrySet()) {
          if (entry.getValue().isJsonObject()) {
            normalizedAreas.add(entry.getKey(), JsonParser
                .parseString(normalizeProcessJson(entry.getValue().toString())).getAsJsonObject());
          } else {
            normalizedAreas.add(entry.getKey(), entry.getValue());
          }
        }
        root.add("areas", normalizedAreas);
        return GSON.toJson(root);
      }

      if (root.has("fluid") && root.get("fluid").isJsonObject()) {
        JsonObject fluid = root.getAsJsonObject("fluid");
        if (!fluid.has("temperature") && fluid.has("temperature_C")) {
          fluid.addProperty("temperature", fluid.get("temperature_C").getAsDouble() + 273.15);
        }
        if (!fluid.has("pressure") && fluid.has("pressure_bara")) {
          fluid.addProperty("pressure", fluid.get("pressure_bara").getAsDouble());
        }
      }

      if (root.has("process") && root.get("process").isJsonObject()) {
        JsonObject processObj = root.getAsJsonObject("process");
        if (processObj.has("equipment") && processObj.get("equipment").isJsonArray()) {
          root.add("process", processObj.getAsJsonArray("equipment"));
        }
      }

      if (root.has("process") && root.get("process").isJsonArray()) {
        JsonArray processArr = root.getAsJsonArray("process");
        for (int i = 0; i < processArr.size(); i++) {
          JsonObject unit = processArr.get(i).getAsJsonObject();
          JsonObject properties = unit.has("properties") && unit.get("properties").isJsonObject()
              ? unit.getAsJsonObject("properties")
              : new JsonObject();

          if (!properties.has("flowRate") && unit.has("flowRate")) {
            properties.add("flowRate", unit.get("flowRate"));
          }
          if (!properties.has("temperature") && unit.has("temperature")) {
            properties.add("temperature", unit.get("temperature"));
          }
          if (!properties.has("pressure") && unit.has("pressure")) {
            properties.add("pressure", unit.get("pressure"));
          }

          if (properties.size() > 0) {
            normalizeLegacyPropertyObjects(properties);
            unit.add("properties", properties);
          }
        }
      }

      return GSON.toJson(root);
    } catch (Exception ignored) {
    }
    return json;
  }

  /**
   * Checks whether a JSON string represents a multi-area ProcessModel.
   *
   * @param json the JSON string to inspect
   * @return true if the root object has a JSON object named {@code areas}
   */
  private static boolean isProcessModelJson(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      return root.has("areas") && root.get("areas").isJsonObject();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Extracts a comma-separated summary of a fluid field across all process-model areas.
   *
   * @param areas the areas object from a ProcessModel JSON document
   * @param fluidField the field to extract from each area's fluid block
   * @param defaultValue fallback value when an area omits the field
   * @return one value, or comma-separated values when areas differ
   */
  private static String extractAreaSummary(JsonObject areas, String fluidField,
      String defaultValue) {
    List<String> values = new ArrayList<String>();
    for (Map.Entry<String, com.google.gson.JsonElement> entry : areas.entrySet()) {
      if (!entry.getValue().isJsonObject()) {
        continue;
      }
      JsonObject area = entry.getValue().getAsJsonObject();
      String value = defaultValue;
      if (area.has("fluid") && area.get("fluid").isJsonObject()
          && area.getAsJsonObject("fluid").has(fluidField)) {
        value = area.getAsJsonObject("fluid").get(fluidField).getAsString();
      }
      if (!values.contains(value)) {
        values.add(value);
      }
    }
    if (values.isEmpty()) {
      return defaultValue;
    }
    return join(values, ", ");
  }

  /**
   * Adds validation warnings to a response object.
   *
   * @param response the response object to mutate
   * @param validationIssues validation issues returned by {@link Validator}
   */
  private static void addValidationIssues(JsonObject response, JsonArray validationIssues) {
    if (validationIssues != null && validationIssues.size() > 0) {
      response.add("validationIssues", validationIssues);
    }
  }

  /**
   * Adds validation warnings to provenance limitations.
   *
   * @param provenance the provenance object to mutate
   * @param validationIssues validation issues returned by {@link Validator}
   */
  private static void addValidationIssueLimitations(ResultProvenance provenance,
      JsonArray validationIssues) {
    if (validationIssues == null) {
      return;
    }
    for (int i = 0; i < validationIssues.size(); i++) {
      JsonObject issue = validationIssues.get(i).getAsJsonObject();
      if (issue.has("message")) {
        provenance.addLimitation("Validation warning: " + issue.get("message").getAsString());
      }
    }
  }

  /**
   * Converts a string list to a JSON array.
   *
   * @param values values to convert
   * @return JSON array containing the string values
   */
  private static JsonArray toJsonArray(List<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Joins string values with a delimiter.
   *
   * @param values values to join
   * @param delimiter delimiter between values
   * @return joined string
   */
  private static String join(List<String> values, String delimiter) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        builder.append(delimiter);
      }
      builder.append(values.get(i));
    }
    return builder.toString();
  }

  /**
   * Converts legacy {value, unit} property objects to [value, unit] arrays expected by the
   * JsonProcessBuilder reflection setter logic.
   *
   * @param properties mutable properties object
   */
  private static void normalizeLegacyPropertyObjects(JsonObject properties) {
    String[] unitAwareKeys = {"flowRate", "temperature", "pressure"};
    for (String key : unitAwareKeys) {
      if (properties.has(key) && properties.get(key).isJsonObject()) {
        JsonObject obj = properties.getAsJsonObject(key);
        if (obj.has("value") && obj.has("unit")) {
          JsonArray arr = new JsonArray();
          arr.add(obj.get("value"));
          arr.add(obj.get("unit"));
          properties.add(key, arr);
        }
      }
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

  /**
   * Creates a standard error JSON response from detailed simulation errors.
   *
   * @param errors simulation errors to expose
   * @param warnings non-fatal warnings to expose
   * @return JSON error string
   */
  private static String errorJson(List<SimulationResult.ErrorDetail> errors,
      List<String> warnings) {
    JsonObject result = new JsonObject();
    result.addProperty("status", "error");

    JsonArray errorArray = new JsonArray();
    for (SimulationResult.ErrorDetail error : errors) {
      errorArray.add(error.toJsonObject());
    }
    result.add("errors", errorArray);

    if (warnings != null && !warnings.isEmpty()) {
      JsonArray warningArray = new JsonArray();
      for (String warning : warnings) {
        warningArray.add(warning);
      }
      result.add("warnings", warningArray);
    }

    return GSON.toJson(result);
  }

  /**
   * Mutable container for ProcessModel build results.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ProcessModelBuildResult {
    private final ProcessModel model = new ProcessModel();
    private final List<SimulationResult.ErrorDetail> errors =
        new ArrayList<SimulationResult.ErrorDetail>();
    private final List<String> warnings = new ArrayList<String>();
  }
}
