package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.capacity.EquipmentDesignData;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.operations.ControllerTuningResult;
import neqsim.process.operations.ControllerTuningStudy;
import neqsim.process.operations.OperationalAction;
import neqsim.process.operations.OperationalEvidencePackage;
import neqsim.process.operations.OperationalScenario;
import neqsim.process.operations.OperationalScenarioResult;
import neqsim.process.operations.OperationalScenarioRunner;
import neqsim.process.operations.OperationalTagBinding;
import neqsim.process.operations.OperationalTagMap;
import neqsim.process.operations.PipeSectionAnalyzer;
import neqsim.process.operations.envelope.MarginTrendTracker;
import neqsim.process.operations.envelope.OperationalEnvelopeEvaluator;
import neqsim.process.operations.envelope.OperationalEnvelopeReport;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.util.validation.ValidationResult;
import neqsim.util.validation.ValidationResult.ValidationIssue;

/**
 * MCP runner for P&amp;ID-derived operational studies.
 *
 * <p>
 * The runner exposes the {@code neqsim.process.operations} orchestration helpers to MCP clients. It
 * intentionally delegates model interaction to existing NeqSim features: JSON process building,
 * {@link neqsim.process.automation.ProcessAutomation}, measurement-device field inputs, existing
 * valve logic actions, steady-state runs, and transient process execution.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalStudyRunner {
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for utility class.
   */
  private OperationalStudyRunner() {}

  /**
   * Runs an operational study action.
   *
   * @param json study input JSON
   * @return JSON string with the action result
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide an operational study JSON object with an 'action' field.");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException ex) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + ex.getMessage(),
          "Ensure the operational study input is valid JSON.");
    }

    String action = getString(input, "action", "getSchema");
    try {
      if ("getSchema".equalsIgnoreCase(action)) {
        return getSchema();
      } else if ("validateTagMap".equalsIgnoreCase(action)) {
        return validateTagMap(input);
      } else if ("applyFieldData".equalsIgnoreCase(action)) {
        return applyFieldData(input);
      } else if ("runScenario".equalsIgnoreCase(action)) {
        return runScenario(input);
      } else if ("runEvidencePackage".equalsIgnoreCase(action)) {
        return runEvidencePackage(input);
      } else if ("evaluateControllerResponse".equalsIgnoreCase(action)) {
        return evaluateControllerResponse(input);
      } else if ("analyzePipeSections".equalsIgnoreCase(action)) {
        return analyzePipeSections(input);
      } else if ("evaluateOperatingEnvelope".equalsIgnoreCase(action)) {
        return evaluateOperatingEnvelope(input);
      }
      return errorJson("UNKNOWN_ACTION", "Unknown operational study action: " + action,
          "Use getSchema, validateTagMap, applyFieldData, runScenario, runEvidencePackage, "
              + "evaluateControllerResponse, analyzePipeSections, or evaluateOperatingEnvelope.");
    } catch (RuntimeException ex) {
      return errorJson("OPERATIONAL_STUDY_ERROR", "Operational study failed: " + ex.getMessage(),
          "Check the processJson, tagBindings, fieldData, action list, and units.");
    }
  }

  /**
   * Returns a compact schema and usage manifest for this runner.
   *
   * @return JSON schema description
   */
  private static String getSchema() {
    JsonObject root = new JsonObject();
    root.addProperty("status", "success");
    root.addProperty("tool", "runOperationalStudy");
    root.addProperty("description",
        "Run plant-agnostic operational studies from P&ID semantics, tag maps, valve actions, "
            + "automation variables, and controller response time series.");

    JsonArray actions = new JsonArray();
    actions.add("getSchema");
    actions.add("validateTagMap");
    actions.add("applyFieldData");
    actions.add("runScenario");
    actions.add("runEvidencePackage");
    actions.add("evaluateControllerResponse");
    actions.add("analyzePipeSections");
    actions.add("evaluateOperatingEnvelope");
    root.add("actions", actions);

    JsonObject binding = new JsonObject();
    binding.addProperty("logicalTag", "stable public tag name, e.g. separator_pressure");
    binding.addProperty("historianTag", "optional private historian tag name");
    binding.addProperty("automationAddress",
        "optional NeqSim address such as Valve.percentValveOpening");
    binding.addProperty("unit", "engineering unit such as bara, C, kg/hr, or %");
    binding.addProperty("role", "INPUT, BENCHMARK, or VIRTUAL");
    root.add("tagBindingFields", binding);

    JsonObject scenarioAction = new JsonObject();
    scenarioAction.addProperty("SET_VARIABLE", "target, value, unit");
    scenarioAction.addProperty("SET_VALVE_OPENING", "target valve name and value percent open");
    scenarioAction.addProperty("APPLY_FIELD_INPUTS", "no target required");
    scenarioAction.addProperty("RUN_STEADY_STATE", "no target required");
    scenarioAction.addProperty("RUN_TRANSIENT", "durationSeconds and timeStepSeconds");
    root.add("scenarioActionTypes", scenarioAction);

    JsonObject example = new JsonObject();
    example.addProperty("action", "runEvidencePackage");
    example.addProperty("studyName", "private operating case screening");
    example.addProperty("processJson", "{... standard runProcess JSON ...}");
    example.addProperty("benchmarkToleranceFraction", 0.05);
    JsonObject exDesignCapacities = new JsonObject();
    JsonObject sepDesign = new JsonObject();
    sepDesign.addProperty("internalDiameter", 2.0);
    sepDesign.addProperty("separatorLength", 6.0);
    exDesignCapacities.add("HP Separator", sepDesign);
    JsonObject compDesign = new JsonObject();
    compDesign.addProperty("ratedPower", 5000);
    exDesignCapacities.add("Compressor", compDesign);
    example.add("designCapacities", exDesignCapacities);
    JsonArray exampleActions = new JsonArray();
    JsonObject valveAction = new JsonObject();
    valveAction.addProperty("type", "SET_VALVE_OPENING");
    valveAction.addProperty("target", "Outlet Valve");
    valveAction.addProperty("value", 15.0);
    exampleActions.add(valveAction);
    JsonObject runAction = new JsonObject();
    runAction.addProperty("type", "RUN_STEADY_STATE");
    exampleActions.add(runAction);
    JsonArray exampleScenarios = new JsonArray();
    JsonObject scenario = new JsonObject();
    scenario.addProperty("scenarioName", "partly close outlet");
    scenario.add("actions", exampleActions);
    exampleScenarios.add(scenario);
    example.add("scenarios", exampleScenarios);
    example.addProperty("envelopeAction", "evaluateOperatingEnvelope");
    example.addProperty("predictionHorizonSeconds", 3600.0);
    root.add("example", example);

    return GSON.toJson(root);
  }

  /**
   * Validates operational tag bindings against a process.
   *
   * @param input operational study input
   * @return JSON validation result
   */
  private static String validateTagMap(JsonObject input) {
    ProcessSystem process = buildProcess(input);
    OperationalTagMap tagMap = buildTagMap(input);
    ValidationResult validation = tagMap.validate(process);

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.add("validation", validationToJson(validation));
    return GSON.toJson(result);
  }

  /**
   * Builds a combined evidence package with field-data reconciliation, bottlenecks, and scenarios.
   *
   * @param input operational study input
   * @return JSON evidence package result
   */
  private static String runEvidencePackage(JsonObject input) {
    ProcessSystem process = buildProcess(input);

    JsonObject designCapacitiesReport = applyDesignCapacitiesIfProvided(input, process);

    OperationalTagMap tagMap = hasTagBindings(input) ? buildTagMap(input) : new OperationalTagMap();
    ValidationResult validation = tagMap.validate(process);
    if (!validation.isValid()) {
      return validationErrorJson(validation);
    }

    Map<String, Double> fieldData =
        input.has("fieldData") ? parseFieldData(input) : new LinkedHashMap<String, Double>();
    List<OperationalScenario> scenarios = buildScenarioList(input);
    double tolerance = parseBenchmarkTolerance(input);

    JsonObject packageReport = OperationalEvidencePackage.buildReport(
        getString(input, "studyName", "operational evidence package"), process, tagMap, fieldData,
        scenarios, tolerance);

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.add("validation", validationToJson(validation));
    if (designCapacitiesReport != null) {
      result.add("designCapacitiesApplied", designCapacitiesReport);
    }
    result.add("evidencePackage", packageReport);

    if (input.has("pipeSections") && input.get("pipeSections").isJsonArray()
        && input.getAsJsonArray("pipeSections").size() > 0) {
      String pipeResult = PipeSectionAnalyzer.run(GSON.toJson(input));
      result.add("pipeSectionAnalysis", JsonParser.parseString(pipeResult));
    }

    addOptionalPassThrough(input, result, "evidenceReferences", "evidenceReferences");
    addOptionalPassThrough(input, result, "evidenceRefs", "evidenceReferences");
    addOptionalPassThrough(input, result, "assumptions", "assumptions");
    addOptionalPassThrough(input, result, "pidOperationalModel", "pidOperationalModel");
    addProcessReport(process, result);
    return GSON.toJson(result);
  }

  /**
   * Applies field data to a process through operational tag bindings.
   *
   * @param input operational study input
   * @return JSON result with applied values and model readback
   */
  private static String applyFieldData(JsonObject input) {
    ProcessSystem process = buildProcess(input);
    OperationalTagMap tagMap = buildTagMap(input);
    ValidationResult validation = tagMap.validate(process);
    if (!validation.isValid()) {
      return validationErrorJson(validation);
    }

    Map<String, Double> fieldData = parseFieldData(input);
    Map<String, Double> applied = tagMap.applyFieldData(process, fieldData);
    if (getBoolean(input, "runAfterApply", true)) {
      process.run();
    }

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.add("validation", validationToJson(validation));
    result.add("applied", mapToJson(applied));
    result.add("modelValues", mapToJson(tagMap.readValues(process)));
    addProcessReport(process, result);
    return GSON.toJson(result);
  }

  /**
   * Runs an operational scenario against a process.
   *
   * @param input operational study input
   * @return JSON scenario result
   */
  private static String runScenario(JsonObject input) {
    ProcessSystem process = buildProcess(input);
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");

    if (hasTagBindings(input) && input.has("fieldData")) {
      OperationalTagMap tagMap = buildTagMap(input);
      ValidationResult validation = tagMap.validate(process);
      if (!validation.isValid()) {
        return validationErrorJson(validation);
      }
      Map<String, Double> applied = tagMap.applyFieldData(process, parseFieldData(input));
      result.add("validation", validationToJson(validation));
      result.add("applied", mapToJson(applied));
    }

    OperationalScenario scenario = buildScenario(input);
    OperationalScenarioResult scenarioResult = OperationalScenarioRunner.run(process, scenario);
    result.add("scenarioResult", JsonParser.parseString(scenarioResult.toJson()));
    result.addProperty("successful", scenarioResult.isSuccessful());
    addProcessReport(process, result);
    return GSON.toJson(result);
  }

  /**
   * Evaluates controller response metrics from supplied time series.
   *
   * @param input operational study input
   * @return JSON controller tuning result
   */
  private static String evaluateControllerResponse(JsonObject input) {
    double setPoint = getRequiredDouble(input, "setPoint");
    double outputMin = getRequiredDouble(input, "outputMin");
    double outputMax = getRequiredDouble(input, "outputMax");
    double settlingTolerance = getRequiredDouble(input, "settlingTolerance");
    double[] timeSeconds = parseDoubleArray(input, "timeSeconds");
    double[] processValue = parseDoubleArray(input, "processValue");
    double[] controllerOutput = parseDoubleArray(input, "controllerOutput");

    ControllerTuningResult tuning =
        ControllerTuningStudy.evaluateStepResponse(getString(input, "controllerName", ""), setPoint,
            timeSeconds, processValue, controllerOutput, outputMin, outputMax, settlingTolerance);

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.add("controllerTuning", JsonParser.parseString(tuning.toJson()));
    return GSON.toJson(result);
  }

  /**
   * Analyzes pipe sections that are not part of the main process model.
   *
   * <p>
   * This action does not require {@code processJson}. It takes a fluid definition and a
   * {@code pipeSections} array describing pipe geometry and operating conditions (which may come
   * from P&amp;ID data and tagreader field data), runs a Beggs &amp; Brill hydraulic calculation
   * for each section, and returns velocity, pressure drop, flow regime, and utilization against a
   * design limit.
   * </p>
   *
   * @param input study input containing fluid, pipeSections, optional fieldData and
   *        sectionTagBindings
   * @return JSON result with per-section analysis and summary
   */
  private static String analyzePipeSections(JsonObject input) {
    return PipeSectionAnalyzer.run(GSON.toJson(input));
  }

  /**
   * Evaluates process operating margins, simple margin trends, and advisory mitigations.
   *
   * <p>
   * This action reuses {@link OperationalTagMap} for optional plant-data application and
   * {@link EquipmentDesignData} for optional STID or datasheet capacity values before delegating to
   * {@link OperationalEnvelopeEvaluator}.
   * </p>
   *
   * @param input operational study input
   * @return JSON result with operating envelope report
   */
  private static String evaluateOperatingEnvelope(JsonObject input) {
    ProcessSystem process = buildProcess(input);
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");

    JsonObject designCapacitiesReport = applyDesignCapacitiesIfProvided(input, process);
    if (designCapacitiesReport != null) {
      result.add("designCapacitiesApplied", designCapacitiesReport);
    }

    if (hasTagBindings(input)) {
      OperationalTagMap tagMap = buildTagMap(input);
      ValidationResult validation = tagMap.validate(process);
      if (!validation.isValid()) {
        return validationErrorJson(validation);
      }
      result.add("validation", validationToJson(validation));
      if (input.has("fieldData")) {
        Map<String, Double> fieldData = parseFieldData(input);
        Map<String, Double> applied = tagMap.applyFieldData(process, fieldData);
        process.run();
        result.add("applied", mapToJson(applied));
        result.add("modelValues", mapToJson(tagMap.readValues(process)));
      }
    }

    Map<String, MarginTrendTracker> history = parseMarginHistory(input);
    double horizon = getDouble(input, "predictionHorizonSeconds",
        OperationalEnvelopeEvaluator.DEFAULT_PREDICTION_HORIZON_SECONDS);
    boolean includeMitigations = getBoolean(input, "includeMitigations", true);
    OperationalEnvelopeReport report = OperationalEnvelopeEvaluator.evaluate(process, history,
        horizon, includeMitigations);
    result.add("operatingEnvelope", report.toJsonObject());
    addOptionalPassThrough(input, result, "evidenceReferences", "evidenceReferences");
    addOptionalPassThrough(input, result, "evidenceRefs", "evidenceReferences");
    addOptionalPassThrough(input, result, "assumptions", "assumptions");
    addOptionalPassThrough(input, result, "pidOperationalModel", "pidOperationalModel");
    addProcessReport(process, result);
    return GSON.toJson(result);
  }

  /**
   * Applies design capacities when supplied and tags default data sources otherwise.
   *
   * @param input operational study input
   * @param process process system to update
   * @return report of applied design capacities, or null when no design capacities were supplied
   */
  private static JsonObject applyDesignCapacitiesIfProvided(JsonObject input,
      ProcessSystem process) {
    if (input.has("designCapacities") && input.get("designCapacities").isJsonObject()) {
      JsonObject designCapacities = input.getAsJsonObject("designCapacities");
      Map<String, EquipmentDesignData.ApplyResult> designResults =
          EquipmentDesignData.apply(process, designCapacities);
      process.run();
      JsonObject designCapacitiesReport = new JsonObject();
      for (Map.Entry<String, EquipmentDesignData.ApplyResult> entry : designResults.entrySet()) {
        designCapacitiesReport.add(entry.getKey(), entry.getValue().toJson());
      }
      return designCapacitiesReport;
    }
    EquipmentDesignData.tagConstraintDataSources(process, null);
    return null;
  }

  /**
   * Parses optional margin history from {@code marginHistory} or {@code history}.
   *
   * @param input operational study input
   * @return trackers keyed by margin key
   */
  private static Map<String, MarginTrendTracker> parseMarginHistory(JsonObject input) {
    Map<String, MarginTrendTracker> trackers = new LinkedHashMap<String, MarginTrendTracker>();
    JsonArray history = null;
    if (input.has("marginHistory") && input.get("marginHistory").isJsonArray()) {
      history = input.getAsJsonArray("marginHistory");
    } else if (input.has("history") && input.get("history").isJsonArray()) {
      history = input.getAsJsonArray("history");
    }
    if (history == null) {
      return trackers;
    }
    for (JsonElement element : history) {
      JsonObject sample = element.getAsJsonObject();
      String key = getString(sample, "key", getString(sample, "marginKey", ""));
      if (key.trim().isEmpty()) {
        continue;
      }
      MarginTrendTracker tracker = trackers.get(key);
      if (tracker == null) {
        tracker = new MarginTrendTracker(key);
        trackers.put(key, tracker);
      }
      double timestamp = getDouble(sample, "timestampSeconds",
          getDouble(sample, "timeSeconds", getDouble(sample, "time", 0.0)));
      double marginPercent = getDouble(sample, "marginPercent", 0.0);
      tracker.addSample(timestamp, marginPercent);
    }
    return trackers;
  }

  /**
   * Builds and runs a process from a study input.
   *
   * @param input study input containing a {@code processJson} field
   * @return process system
   * @throws IllegalArgumentException if the process JSON is missing or the process cannot run
   */
  private static ProcessSystem buildProcess(JsonObject input) {
    if (!input.has("processJson")) {
      throw new IllegalArgumentException("Missing required field 'processJson'");
    }
    JsonElement processElement = input.get("processJson");
    String processJson =
        processElement.isJsonObject() ? GSON.toJson(processElement) : processElement.getAsString();
    SimulationResult simulationResult = ProcessSystem.fromJsonAndRun(processJson);
    if (simulationResult.isError()) {
      throw new IllegalArgumentException("Process failed: " + simulationResult.getErrors());
    }
    return simulationResult.getProcessSystem();
  }

  /**
   * Builds an operational tag map from JSON bindings.
   *
   * @param input study input containing {@code tagBindings} or {@code bindings}
   * @return operational tag map
   * @throws IllegalArgumentException if no binding array is present or a binding is invalid
   */
  private static OperationalTagMap buildTagMap(JsonObject input) {
    JsonArray bindings = getArray(input, "tagBindings", "bindings");
    OperationalTagMap tagMap = new OperationalTagMap();
    for (JsonElement element : bindings) {
      JsonObject binding = element.getAsJsonObject();
      String logicalTag = getString(binding, "logicalTag", "");
      OperationalTagBinding.Builder builder = OperationalTagBinding.builder(logicalTag)
          .historianTag(getString(binding, "historianTag", ""))
          .pidReference(getString(binding, "pidReference", ""))
          .automationAddress(getString(binding, "automationAddress", ""))
          .unit(getString(binding, "unit", "")).description(getString(binding, "description", ""));
      builder.role(parseRole(getString(binding, "role", "VIRTUAL")));
      tagMap.addBinding(builder.build());
    }
    return tagMap;
  }

  /**
   * Parses field data from JSON.
   *
   * @param input study input containing a {@code fieldData} object
   * @return map of field data values
   * @throws IllegalArgumentException if the field data object is missing
   */
  private static Map<String, Double> parseFieldData(JsonObject input) {
    if (!input.has("fieldData") || !input.get("fieldData").isJsonObject()) {
      throw new IllegalArgumentException("Missing required object 'fieldData'");
    }
    Map<String, Double> fieldData = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, JsonElement> entry : input.getAsJsonObject("fieldData").entrySet()) {
      fieldData.put(entry.getKey(), entry.getValue().getAsDouble());
    }
    return fieldData;
  }

  /**
   * Builds an operational scenario from JSON actions.
   *
   * @param input study input containing an {@code actions} array
   * @return operational scenario
   * @throws IllegalArgumentException if no actions are configured
   */
  private static OperationalScenario buildScenario(JsonObject input) {
    return buildScenarioFromObject(input, getString(input, "scenarioName", "operational scenario"));
  }

  /**
   * Builds all scenarios configured for an evidence package.
   *
   * @param input operational study input
   * @return list of configured scenarios
   */
  private static List<OperationalScenario> buildScenarioList(JsonObject input) {
    List<OperationalScenario> scenarios = new ArrayList<OperationalScenario>();
    if (input.has("scenarios") && input.get("scenarios").isJsonArray()) {
      JsonArray scenarioArray = input.getAsJsonArray("scenarios");
      for (int i = 0; i < scenarioArray.size(); i++) {
        JsonObject scenarioObject = scenarioArray.get(i).getAsJsonObject();
        scenarios.add(buildScenarioFromObject(scenarioObject, "scenario " + (i + 1)));
      }
      return scenarios;
    }
    if (input.has("actions") || input.has("scenarioActions")) {
      scenarios.add(buildScenario(input));
    }
    return scenarios;
  }

  /**
   * Builds one operational scenario from a JSON object.
   *
   * @param input scenario JSON object containing actions
   * @param defaultName default scenario name
   * @return operational scenario
   */
  private static OperationalScenario buildScenarioFromObject(JsonObject input, String defaultName) {
    JsonArray actions = getArray(input, "actions", "scenarioActions");
    OperationalScenario.Builder builder =
        OperationalScenario.builder(getString(input, "scenarioName", defaultName));
    for (JsonElement element : actions) {
      builder.addAction(parseAction(element.getAsJsonObject()));
    }
    return builder.build();
  }

  /**
   * Parses benchmark tolerance from fraction or percentage fields.
   *
   * @param input operational study input
   * @return benchmark tolerance as fraction
   */
  private static double parseBenchmarkTolerance(JsonObject input) {
    if (input.has("benchmarkToleranceFraction")) {
      return input.get("benchmarkToleranceFraction").getAsDouble();
    }
    if (input.has("benchmarkTolerancePercent")) {
      return input.get("benchmarkTolerancePercent").getAsDouble() / 100.0;
    }
    return OperationalEvidencePackage.DEFAULT_BENCHMARK_TOLERANCE_FRACTION;
  }

  /**
   * Passes an optional JSON field through to the output.
   *
   * @param input source JSON object
   * @param result target JSON object
   * @param inputName source field name
   * @param outputName target field name
   */
  private static void addOptionalPassThrough(JsonObject input, JsonObject result, String inputName,
      String outputName) {
    if (input.has(inputName) && !result.has(outputName)) {
      result.add(outputName, input.get(inputName));
    }
  }

  /**
   * Parses one scenario action.
   *
   * @param action action JSON object
   * @return operational action
   * @throws IllegalArgumentException if the action type is unknown
   */
  private static OperationalAction parseAction(JsonObject action) {
    String type = getString(action, "type", "");
    if ("SET_VARIABLE".equalsIgnoreCase(type) || "setVariable".equalsIgnoreCase(type)) {
      return OperationalAction.setVariable(getString(action, "target", ""),
          getRequiredDouble(action, "value"), getString(action, "unit", ""));
    } else if ("SET_VALVE_OPENING".equalsIgnoreCase(type)
        || "setValveOpening".equalsIgnoreCase(type)) {
      return OperationalAction.setValveOpening(getString(action, "target", ""),
          getRequiredDouble(action, "value"));
    } else if ("APPLY_FIELD_INPUTS".equalsIgnoreCase(type)
        || "applyFieldInputs".equalsIgnoreCase(type)) {
      return OperationalAction.applyFieldInputs();
    } else if ("RUN_STEADY_STATE".equalsIgnoreCase(type)
        || "runSteadyState".equalsIgnoreCase(type)) {
      return OperationalAction.runSteadyState();
    } else if ("RUN_TRANSIENT".equalsIgnoreCase(type) || "runTransient".equalsIgnoreCase(type)) {
      double duration =
          getDouble(action, "durationSeconds", getDouble(action, "duration_seconds", 0.0));
      double step =
          getDouble(action, "timeStepSeconds", getDouble(action, "timeStep_seconds", 0.0));
      return OperationalAction.runTransient(duration, step);
    }
    throw new IllegalArgumentException("Unknown scenario action type: " + type);
  }

  /**
   * Parses an instrument tag role.
   *
   * @param roleText role text
   * @return instrument tag role
   */
  private static InstrumentTagRole parseRole(String roleText) {
    try {
      return InstrumentTagRole.valueOf(roleText.trim().toUpperCase());
    } catch (RuntimeException ex) {
      return InstrumentTagRole.VIRTUAL;
    }
  }

  /**
   * Returns whether tag bindings are present in the input.
   *
   * @param input study input
   * @return true when bindings are present
   */
  private static boolean hasTagBindings(JsonObject input) {
    return input.has("tagBindings") || input.has("bindings");
  }

  /**
   * Reads an array under either of two accepted names.
   *
   * @param input JSON object
   * @param primary primary field name
   * @param secondary secondary field name
   * @return JSON array
   * @throws IllegalArgumentException if neither field contains an array
   */
  private static JsonArray getArray(JsonObject input, String primary, String secondary) {
    if (input.has(primary) && input.get(primary).isJsonArray()) {
      return input.getAsJsonArray(primary);
    }
    if (input.has(secondary) && input.get(secondary).isJsonArray()) {
      return input.getAsJsonArray(secondary);
    }
    throw new IllegalArgumentException("Missing required array '" + primary + "'");
  }

  /**
   * Parses an array of doubles.
   *
   * @param input source JSON object
   * @param field field name
   * @return double array
   * @throws IllegalArgumentException if the field is missing or not an array
   */
  private static double[] parseDoubleArray(JsonObject input, String field) {
    if (!input.has(field) || !input.get(field).isJsonArray()) {
      throw new IllegalArgumentException("Missing required numeric array '" + field + "'");
    }
    JsonArray array = input.getAsJsonArray(field);
    double[] values = new double[array.size()];
    for (int i = 0; i < array.size(); i++) {
      values[i] = array.get(i).getAsDouble();
    }
    return values;
  }

  /**
   * Returns a required double field.
   *
   * @param input source JSON object
   * @param field field name
   * @return field value
   * @throws IllegalArgumentException if the field is missing
   */
  private static double getRequiredDouble(JsonObject input, String field) {
    if (!input.has(field)) {
      throw new IllegalArgumentException("Missing required numeric field '" + field + "'");
    }
    return input.get(field).getAsDouble();
  }

  /**
   * Returns an optional double field.
   *
   * @param input source JSON object
   * @param field field name
   * @param defaultValue value returned when the field is missing
   * @return field value or default value
   */
  private static double getDouble(JsonObject input, String field, double defaultValue) {
    return input.has(field) ? input.get(field).getAsDouble() : defaultValue;
  }

  /**
   * Returns an optional boolean field.
   *
   * @param input source JSON object
   * @param field field name
   * @param defaultValue value returned when the field is missing
   * @return field value or default value
   */
  private static boolean getBoolean(JsonObject input, String field, boolean defaultValue) {
    return input.has(field) ? input.get(field).getAsBoolean() : defaultValue;
  }

  /**
   * Returns an optional string field.
   *
   * @param input source JSON object
   * @param field field name
   * @param defaultValue value returned when the field is missing
   * @return field value or default value
   */
  private static String getString(JsonObject input, String field, String defaultValue) {
    return input.has(field) && !input.get(field).isJsonNull() ? input.get(field).getAsString()
        : defaultValue;
  }

  /**
   * Converts a validation result to JSON.
   *
   * @param validation validation result
   * @return JSON object
   */
  private static JsonObject validationToJson(ValidationResult validation) {
    JsonObject root = new JsonObject();
    root.addProperty("valid", validation.isValid());
    root.addProperty("hasWarnings", validation.hasWarnings());
    root.addProperty("report", validation.getReport());
    JsonArray issues = new JsonArray();
    for (ValidationIssue issue : validation.getIssues()) {
      JsonObject issueJson = new JsonObject();
      issueJson.addProperty("severity", issue.getSeverity().name());
      issueJson.addProperty("category", issue.getCategory());
      issueJson.addProperty("message", issue.getMessage());
      issueJson.addProperty("remediation", issue.getRemediation());
      issues.add(issueJson);
    }
    root.add("issues", issues);
    return root;
  }

  /**
   * Converts a map to a JSON object.
   *
   * @param values values to convert
   * @return JSON object
   */
  private static JsonObject mapToJson(Map<String, Double> values) {
    JsonObject object = new JsonObject();
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      object.addProperty(entry.getKey(), entry.getValue());
    }
    return object;
  }

  /**
   * Adds a process report to a result when available.
   *
   * @param process process system
   * @param result result object to update
   */
  private static void addProcessReport(ProcessSystem process, JsonObject result) {
    String report = process.getReport_json();
    if (report != null && !report.trim().isEmpty()) {
      try {
        result.add("processReport", JsonParser.parseString(report));
      } catch (RuntimeException ex) {
        result.addProperty("processReport", report);
      }
    }
  }

  /**
   * Builds a validation error response.
   *
   * @param validation failed validation result
   * @return JSON string
   */
  private static String validationErrorJson(ValidationResult validation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("code", "VALIDATION_ERROR");
    error.addProperty("message", "Operational tag map validation failed");
    error.add("validation", validationToJson(validation));
    return GSON.toJson(error);
  }

  /**
   * Builds a standard error JSON response.
   *
   * @param code error code
   * @param message human-readable message
   * @param remediation remediation guidance
   * @return JSON string
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
