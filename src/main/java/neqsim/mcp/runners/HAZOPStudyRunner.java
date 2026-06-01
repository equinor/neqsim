package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.process.safety.scenario.AutomaticScenarioGenerator;
import neqsim.process.safety.scenario.AutomaticScenarioGenerator.EquipmentFailure;
import neqsim.process.safety.scenario.AutomaticScenarioGenerator.FailureMode;
import neqsim.process.safety.scenario.AutomaticScenarioGenerator.HazopDeviation;
import neqsim.process.safety.hazid.HAZOPTemplate;

/**
 * MCP runner for simulation-backed HAZOP study generation.
 *
 * <p>
 * The runner bridges document-derived HAZOP nodes, a NeqSim process definition, automatic
 * equipment-failure scenario generation, and optional barrier-register handoff. It is intended for
 * agent workflows that extract nodes and safeguards from STID/P&amp;ID documents, build or retrieve
 * a {@link ProcessSystem}, and need a first-pass IEC 61882 worksheet with simulation evidence.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HAZOPStudyRunner {
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for utility class.
   */
  private HAZOPStudyRunner() {}

  /**
   * Runs a simulation-backed HAZOP study from JSON input.
   *
   * @param json JSON containing a process definition, optional nodes, failure modes, and barrier
   *        register
   * @return JSON string with worksheet rows, simulation results, and report markdown
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String studyId = optString(input, "studyId", "HAZOP-STUDY");
      StudyContext context = buildStudyContext(input, studyId);
      return GSON.toJson(buildOutput(input, context));
    } catch (Exception e) {
      return errorJson("HAZOP study generation failed: " + e.getMessage());
    }
  }

  /**
   * Builds the full HAZOP study context from input JSON.
   *
   * @param input input JSON object
   * @param studyId study identifier
   * @return populated study context
   */
  private static StudyContext buildStudyContext(JsonObject input, String studyId) {
    String processJson = extractProcessJson(input);
    SimulationResult simulation = ProcessSystem.fromJsonAndRun(processJson);
    if (simulation.isError() || simulation.getProcessSystem() == null) {
      throw new IllegalArgumentException("Process simulation failed before HAZOP generation: "
          + errorsToText(simulation.getErrors()));
    }

    StudyContext context = new StudyContext();
    context.studyId = studyId;
    context.processJson = processJson;
    context.process = simulation.getProcessSystem();
    context.baselineReportJson = simulation.getReportJson();
    context.warnings.addAll(simulation.getWarnings());
    context.nodes = parseNodes(input, context.process);
    context.enabledFailureModes = parseFailureModes(input, context.warnings);
    if (context.enabledFailureModes.isEmpty()) {
      context.enabledFailureModes.addAll(Arrays.asList(FailureMode.values()));
    }
    context.runSimulations = optBoolean(input, "runSimulations", true);
    return context;
  }

  /**
   * Builds the output JSON for a populated study context.
   *
   * @param input original input JSON
   * @param context populated study context
   * @return output JSON object
   */
  private static JsonObject buildOutput(JsonObject input, StudyContext context) {
    AutomaticScenarioGenerator generator = new AutomaticScenarioGenerator(context.process);
    for (FailureMode mode : context.enabledFailureModes) {
      generator.addFailureModes(mode);
    }

    List<ProcessSafetyScenario> scenarios = generator.generateSingleFailures();
    Map<String, AutomaticScenarioGenerator.ScenarioRunResult> scenarioResults =
        runScenarios(context, generator, scenarios);

    JsonArray rows = new JsonArray();
    JsonArray nodesOut = new JsonArray();
    Map<String, HAZOPTemplate> worksheets = new LinkedHashMap<String, HAZOPTemplate>();
    int rowNumber = 1;

    List<EquipmentFailure> failures = generator.getIdentifiedFailures();
    for (HazopNode node : context.nodes) {
      HAZOPTemplate worksheet = new HAZOPTemplate(node.nodeId, node.designIntent);
      worksheets.put(node.nodeId, worksheet);
      for (EquipmentFailure failure : failures) {
        if (!context.enabledFailureModes.contains(failure.getMode()) || !node.matches(failure)) {
          continue;
        }
        HAZOPTemplate.GuideWord guideWord = mapGuideWord(failure.getMode().getHazopDeviation());
        HAZOPTemplate.Parameter parameter = mapParameter(failure.getMode().getHazopDeviation());
        String scenarioName = scenarioName(failure);
        AutomaticScenarioGenerator.ScenarioRunResult scenarioResult =
            scenarioResults.get(scenarioName);
        String consequence = buildConsequence(failure, scenarioResult);
        String safeguards = node.safeguards.isEmpty() ? "Review extracted barrier register"
            : String.join("; ", node.safeguards);
        String recommendation = buildRecommendation(failure, scenarioResult);
        worksheet.addDeviation(guideWord, parameter, buildCause(failure), consequence, safeguards,
            recommendation);
        rows.add(buildRow(context.studyId, rowNumber, node, failure, guideWord, parameter,
            consequence, safeguards, recommendation, scenarioResult));
        rowNumber++;
      }
      nodesOut.add(buildNodeOutput(node, worksheet));
    }

    JsonObject out = new JsonObject();
    out.addProperty("status", "success");
    out.addProperty("standard", "IEC 61882 / IEC 61511 / NORSOK Z-013");
    out.addProperty("studyId", context.studyId);
    out.add("summary", buildSummary(context, failures, rows, scenarioResults));
    out.add("process", buildProcessSummary(context));
    out.add("nodes", nodesOut);
    out.add("hazopRows", rows);
    out.add("scenarioResults", scenarioResultsToJson(scenarioResults));
    out.add("qualityGates", buildQualityGates(context, rows));
    addBarrierRegisterHandoff(input, out);
    out.addProperty("reportMarkdown", buildReportMarkdown(context, worksheets, rows));
    out.add("documentExtractionTemplate", buildDocumentExtractionTemplate());
    return out;
  }

  /**
   * Runs generated scenarios when requested by the input.
   *
   * @param context study context with process and options
   * @param generator automatic scenario generator
   * @param scenarios generated scenarios
   * @return map from scenario name to scenario result
   */
  private static Map<String, AutomaticScenarioGenerator.ScenarioRunResult> runScenarios(
      StudyContext context, AutomaticScenarioGenerator generator,
      List<ProcessSafetyScenario> scenarios) {
    Map<String, AutomaticScenarioGenerator.ScenarioRunResult> resultByName =
        new LinkedHashMap<String, AutomaticScenarioGenerator.ScenarioRunResult>();
    if (!context.runSimulations) {
      return resultByName;
    }
    List<AutomaticScenarioGenerator.ScenarioRunResult> results = generator.runScenarios(scenarios);
    for (AutomaticScenarioGenerator.ScenarioRunResult result : results) {
      resultByName.put(result.getScenario().getName(), result);
    }
    return resultByName;
  }

  /**
   * Extracts a standard process JSON object from supported input shapes.
   *
   * @param input HAZOP study input JSON
   * @return process JSON string suitable for {@link ProcessSystem#fromJsonAndRun(String)}
   */
  private static String extractProcessJson(JsonObject input) {
    if (input.has("processJson")) {
      JsonElement processJson = input.get("processJson");
      return processJson.isJsonPrimitive() ? processJson.getAsString() : processJson.toString();
    }
    if (input.has("processDefinition") && input.get("processDefinition").isJsonObject()) {
      return input.getAsJsonObject("processDefinition").toString();
    }
    if (input.has("process") && input.get("process").isJsonObject()) {
      JsonObject process = input.getAsJsonObject("process");
      if (process.has("fluid") && process.has("process")) {
        return process.toString();
      }
    }
    if (input.has("fluid") && input.has("process") && input.get("process").isJsonArray()) {
      JsonObject process = new JsonObject();
      process.add("fluid", JsonParser.parseString(input.get("fluid").toString()));
      process.add("process", JsonParser.parseString(input.get("process").toString()));
      return process.toString();
    }
    throw new IllegalArgumentException(
        "Missing process definition. Provide processDefinition, processJson, or top-level fluid/process.");
  }

  /**
   * Parses HAZOP nodes, or creates one node per non-stream process unit.
   *
   * @param input HAZOP study input JSON
   * @param process built process system
   * @return list of HAZOP nodes
   */
  private static List<HazopNode> parseNodes(JsonObject input, ProcessSystem process) {
    List<HazopNode> nodes = new ArrayList<HazopNode>();
    if (input.has("nodes") && input.get("nodes").isJsonArray()) {
      JsonArray nodeArray = input.getAsJsonArray("nodes");
      for (JsonElement element : nodeArray) {
        if (element.isJsonObject()) {
          nodes.add(parseNode(element.getAsJsonObject()));
        }
      }
    }
    if (!nodes.isEmpty()) {
      return nodes;
    }
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      if (equipment instanceof StreamInterface) {
        continue;
      }
      HazopNode node = new HazopNode();
      node.nodeId = equipment.getName();
      node.designIntent =
          equipment.getClass().getSimpleName() + " operation for " + equipment.getName();
      node.equipmentNames.add(equipment.getName());
      nodes.add(node);
    }
    if (nodes.isEmpty()) {
      HazopNode node = new HazopNode();
      node.nodeId = "Process";
      node.designIntent = "Overall process operation";
      nodes.add(node);
    }
    return nodes;
  }

  /**
   * Parses one HAZOP node from JSON.
   *
   * @param input node JSON object
   * @return parsed HAZOP node
   */
  private static HazopNode parseNode(JsonObject input) {
    HazopNode node = new HazopNode();
    node.nodeId = optString(input, "nodeId", optString(input, "id", "Node"));
    node.designIntent = optString(input, "designIntent", optString(input, "intent", ""));
    addStringValues(input, node.equipmentNames, "equipment", "equipmentTags", "equipmentNames",
        "unitNames");
    addStringValues(input, node.safeguards, "safeguards", "existingSafeguards");
    addStringValues(input, node.evidenceRefs, "evidenceRefs", "sourceDocuments", "documentRefs");
    if (node.equipmentNames.isEmpty()) {
      node.equipmentNames.add(node.nodeId);
    }
    return node;
  }

  /**
   * Parses enabled failure modes from input.
   *
   * @param input HAZOP study input JSON
   * @param warnings mutable warning list
   * @return selected failure modes
   */
  private static Set<FailureMode> parseFailureModes(JsonObject input, List<String> warnings) {
    Set<FailureMode> modes = new LinkedHashSet<FailureMode>();
    if (optBoolean(input, "enableAllFailureModes", false)) {
      modes.addAll(Arrays.asList(FailureMode.values()));
      return modes;
    }
    if (!input.has("failureModes") || !input.get("failureModes").isJsonArray()) {
      return modes;
    }
    for (JsonElement element : input.getAsJsonArray("failureModes")) {
      String modeName = element.getAsString();
      try {
        modes.add(FailureMode.valueOf(modeName.trim().toUpperCase()));
      } catch (IllegalArgumentException e) {
        warnings.add("Unknown failure mode ignored: " + modeName);
      }
    }
    return modes;
  }

  /**
   * Adds string values from multiple possible JSON fields.
   *
   * @param input source JSON object
   * @param values target set of strings
   * @param fieldNames field names to inspect
   */
  private static void addStringValues(JsonObject input, Set<String> values, String... fieldNames) {
    for (String fieldName : fieldNames) {
      if (!input.has(fieldName)) {
        continue;
      }
      JsonElement element = input.get(fieldName);
      if (element.isJsonArray()) {
        for (JsonElement item : element.getAsJsonArray()) {
          values.add(item.getAsString());
        }
      } else if (element.isJsonPrimitive()) {
        values.add(element.getAsString());
      }
    }
  }

  /**
   * Maps an automatic scenario HAZOP deviation to an IEC guideword.
   *
   * @param deviation automatic scenario deviation
   * @return HAZOP guideword
   */
  private static HAZOPTemplate.GuideWord mapGuideWord(HazopDeviation deviation) {
    switch (deviation) {
      case NO_FLOW:
        return HAZOPTemplate.GuideWord.NO;
      case LESS_FLOW:
      case LOW_PRESSURE:
      case LESS_PRESSURE:
      case LOW_TEMPERATURE:
      case LOW_LEVEL:
        return HAZOPTemplate.GuideWord.LESS;
      case MORE_FLOW:
      case HIGH_PRESSURE:
      case HIGH_TEMPERATURE:
      case HIGH_LEVEL:
        return HAZOPTemplate.GuideWord.MORE;
      case REVERSE_FLOW:
        return HAZOPTemplate.GuideWord.REVERSE;
      case CONTAMINATION:
        return HAZOPTemplate.GuideWord.AS_WELL_AS;
      case CORROSION:
        return HAZOPTemplate.GuideWord.PART_OF;
      case OTHER:
      default:
        return HAZOPTemplate.GuideWord.OTHER_THAN;
    }
  }

  /**
   * Maps an automatic scenario HAZOP deviation to a process parameter.
   *
   * @param deviation automatic scenario deviation
   * @return HAZOP parameter
   */
  private static HAZOPTemplate.Parameter mapParameter(HazopDeviation deviation) {
    switch (deviation) {
      case HIGH_PRESSURE:
      case LOW_PRESSURE:
      case LESS_PRESSURE:
        return HAZOPTemplate.Parameter.PRESSURE;
      case HIGH_TEMPERATURE:
      case LOW_TEMPERATURE:
        return HAZOPTemplate.Parameter.TEMPERATURE;
      case HIGH_LEVEL:
      case LOW_LEVEL:
        return HAZOPTemplate.Parameter.LEVEL;
      case CONTAMINATION:
      case CORROSION:
        return HAZOPTemplate.Parameter.COMPOSITION;
      case NO_FLOW:
      case LESS_FLOW:
      case MORE_FLOW:
      case REVERSE_FLOW:
      case OTHER:
      default:
        return HAZOPTemplate.Parameter.FLOW;
    }
  }

  /**
   * Builds a cause text for a HAZOP row.
   *
   * @param failure equipment failure
   * @return cause text
   */
  private static String buildCause(EquipmentFailure failure) {
    return failure.getMode().getDescription() + " on " + failure.getEquipmentName();
  }

  /**
   * Builds a consequence text that references simulation status.
   *
   * @param failure equipment failure
   * @param result scenario run result, or null when simulations are disabled
   * @return consequence text
   */
  private static String buildConsequence(EquipmentFailure failure,
      AutomaticScenarioGenerator.ScenarioRunResult result) {
    String base = "Potential " + failure.getMode().getHazopDeviation().name().toLowerCase()
        + " deviation at " + failure.getEquipmentName();
    if (result == null) {
      return base + "; simulation not run.";
    }
    if (!result.isSuccessful()) {
      return base + "; scenario simulation failed: " + result.getErrorMessage();
    }
    return base + "; scenario simulation completed with " + result.getResultValues().size()
        + " captured KPI values.";
  }

  /**
   * Builds a recommendation text for a HAZOP row.
   *
   * @param failure equipment failure
   * @param result scenario run result, or null when simulations are disabled
   * @return recommendation text
   */
  private static String buildRecommendation(EquipmentFailure failure,
      AutomaticScenarioGenerator.ScenarioRunResult result) {
    if (result != null && !result.isSuccessful()) {
      return "Review scenario model setup and define a manual HAZOP action for "
          + failure.getEquipmentName();
    }
    return "Confirm safeguards, barrier credit, and action owner for " + failure.getEquipmentName();
  }

  /**
   * Builds one worksheet row JSON object.
   *
   * @param studyId study identifier
   * @param rowNumber one-based row number
   * @param node HAZOP node
   * @param failure equipment failure
   * @param guideWord HAZOP guideword
   * @param parameter HAZOP parameter
   * @param consequence consequence text
   * @param safeguards safeguard text
   * @param recommendation recommendation text
   * @param result scenario run result
   * @return row JSON object
   */
  private static JsonObject buildRow(String studyId, int rowNumber, HazopNode node,
      EquipmentFailure failure, HAZOPTemplate.GuideWord guideWord,
      HAZOPTemplate.Parameter parameter, String consequence, String safeguards,
      String recommendation, AutomaticScenarioGenerator.ScenarioRunResult result) {
    JsonObject row = new JsonObject();
    row.addProperty("rowId", studyId + "-ROW-" + String.format("%03d", rowNumber));
    row.addProperty("nodeId", node.nodeId);
    row.addProperty("designIntent", node.designIntent);
    row.addProperty("equipment", failure.getEquipmentName());
    row.addProperty("equipmentType", failure.getEquipmentType());
    row.addProperty("failureMode", failure.getMode().name());
    row.addProperty("deviation", failure.getMode().getHazopDeviation().name());
    row.addProperty("guideWord", guideWord.name());
    row.addProperty("parameter", parameter.name());
    row.addProperty("cause", buildCause(failure));
    row.addProperty("consequence", consequence);
    row.addProperty("safeguards", safeguards);
    row.addProperty("recommendation", recommendation);
    row.add("evidenceRefs", toJsonArray(node.evidenceRefs));
    row.add("simulation", scenarioResultToJson(result));
    return row;
  }

  /**
   * Builds output JSON for a HAZOP node.
   *
   * @param node HAZOP node
   * @param worksheet populated worksheet
   * @return node JSON output
   */
  private static JsonObject buildNodeOutput(HazopNode node, HAZOPTemplate worksheet) {
    JsonObject out = new JsonObject();
    out.addProperty("nodeId", node.nodeId);
    out.addProperty("designIntent", node.designIntent);
    out.add("equipment", toJsonArray(node.equipmentNames));
    out.add("safeguards", toJsonArray(node.safeguards));
    out.add("evidenceRefs", toJsonArray(node.evidenceRefs));
    out.addProperty("deviationCount", worksheet.getDeviations().size());
    out.addProperty("textReport", worksheet.report());
    return out;
  }

  /**
   * Builds a study summary.
   *
   * @param context study context
   * @param failures identified equipment failures
   * @param rows HAZOP rows
   * @param scenarioResults scenario results by name
   * @return summary JSON object
   */
  private static JsonObject buildSummary(StudyContext context, List<EquipmentFailure> failures,
      JsonArray rows, Map<String, AutomaticScenarioGenerator.ScenarioRunResult> scenarioResults) {
    JsonObject summary = new JsonObject();
    summary.addProperty("nodeCount", context.nodes.size());
    summary.addProperty("identifiedFailureCount", failures.size());
    summary.addProperty("enabledFailureModeCount", context.enabledFailureModes.size());
    summary.addProperty("hazopRowCount", rows.size());
    summary.addProperty("simulationRunCount", scenarioResults.size());
    int failed = 0;
    for (AutomaticScenarioGenerator.ScenarioRunResult result : scenarioResults.values()) {
      if (!result.isSuccessful()) {
        failed++;
      }
    }
    summary.addProperty("failedSimulationCount", failed);
    return summary;
  }

  /**
   * Builds process metadata for the output.
   *
   * @param context study context
   * @return process summary JSON object
   */
  private static JsonObject buildProcessSummary(StudyContext context) {
    JsonObject process = new JsonObject();
    process.addProperty("name", context.process.getName());
    process.addProperty("equipmentCount", context.process.getUnitOperations().size());
    process.add("warnings", toJsonArray(context.warnings));
    if (context.baselineReportJson != null && !context.baselineReportJson.trim().isEmpty()) {
      try {
        process.add("baselineReport", JsonParser.parseString(context.baselineReportJson));
      } catch (Exception e) {
        process.addProperty("baselineReport", context.baselineReportJson);
      }
    }
    return process;
  }

  /**
   * Builds scenario results as JSON.
   *
   * @param results scenario results by scenario name
   * @return scenario-results JSON array
   */
  private static JsonArray scenarioResultsToJson(
      Map<String, AutomaticScenarioGenerator.ScenarioRunResult> results) {
    JsonArray array = new JsonArray();
    for (AutomaticScenarioGenerator.ScenarioRunResult result : results.values()) {
      array.add(scenarioResultToJson(result));
    }
    return array;
  }

  /**
   * Converts one scenario result to JSON.
   *
   * @param result scenario result, or null
   * @return scenario result JSON object
   */
  private static JsonObject scenarioResultToJson(
      AutomaticScenarioGenerator.ScenarioRunResult result) {
    JsonObject obj = new JsonObject();
    if (result == null) {
      obj.addProperty("status", "not_run");
      return obj;
    }
    obj.addProperty("scenario", result.getScenario().getName());
    obj.addProperty("status", result.isSuccessful() ? "success" : "error");
    obj.addProperty("executionTimeMs", result.getExecutionTimeMs());
    if (result.getErrorMessage() != null) {
      obj.addProperty("errorMessage", result.getErrorMessage());
    }
    JsonObject values = new JsonObject();
    for (Map.Entry<String, Double> entry : result.getResultValues().entrySet()) {
      values.addProperty(entry.getKey(), entry.getValue());
    }
    obj.add("resultValues", values);
    return obj;
  }

  /**
   * Builds simple quality gates for human review.
   *
   * @param context study context
   * @param rows generated HAZOP rows
   * @return quality-gate JSON object
   */
  private static JsonObject buildQualityGates(StudyContext context, JsonArray rows) {
    JsonObject gates = new JsonObject();
    gates.addProperty("humanReviewRequired", true);
    gates.addProperty("hasProcessSimulation", true);
    gates.addProperty("hasDocumentEvidence", hasDocumentEvidence(context.nodes));
    gates.addProperty("hasGeneratedRows", rows.size() > 0);
    gates.addProperty("reviewNote",
        "Automatic HAZOP rows are screening output. A competent HAZOP team must verify nodes, causes, safeguards, and action ownership.");
    return gates;
  }

  /**
   * Adds optional barrier-register output when the input includes a register.
   *
   * @param input original input JSON
   * @param output output JSON object to enrich
   */
  private static void addBarrierRegisterHandoff(JsonObject input, JsonObject output) {
    if (!input.has("barrierRegister")) {
      return;
    }
    String registerJson = input.get("barrierRegister").toString();
    JsonObject wrapper = new JsonObject();
    wrapper.add("register", JsonParser.parseString(registerJson));
    try {
      output.add("barrierRegisterHandoff",
          JsonParser.parseString(BarrierRegisterRunner.run(wrapper.toString())));
    } catch (Exception e) {
      JsonObject error = new JsonObject();
      error.addProperty("status", "error");
      error.addProperty("message", "Barrier register handoff failed: " + e.getMessage());
      output.add("barrierRegisterHandoff", error);
    }
  }

  /**
   * Builds a compact report markdown string.
   *
   * @param context study context
   * @param worksheets worksheets by node id
   * @param rows generated HAZOP rows
   * @return markdown report text
   */
  private static String buildReportMarkdown(StudyContext context,
      Map<String, HAZOPTemplate> worksheets, JsonArray rows) {
    StringBuilder report = new StringBuilder();
    report.append("# Simulation-backed HAZOP Study: ").append(context.studyId).append("\n\n");
    report.append("Standard basis: IEC 61882 with NeqSim process-simulation scenarios.\n\n");
    report.append("## Summary\n\n");
    report.append("- Nodes: ").append(context.nodes.size()).append("\n");
    report.append("- Generated HAZOP rows: ").append(rows.size()).append("\n");
    report.append("- Simulation runs enabled: ").append(context.runSimulations).append("\n\n");
    report.append("## Nodes\n\n");
    for (Map.Entry<String, HAZOPTemplate> entry : worksheets.entrySet()) {
      report.append("### ").append(entry.getKey()).append("\n\n");
      report.append("```text\n").append(entry.getValue().report()).append("```\n\n");
    }
    report.append("## Review Gate\n\n");
    report.append("Automatic rows are screening output and require a chaired HAZOP review. ");
    report.append("Validate STID extraction, process model fidelity, safeguard independence, ");
    report.append("barrier evidence, and action ownership before using results in LOPA/SIL/QRA.\n");
    return report.toString();
  }

  /**
   * Builds a document extraction template for upstream agents.
   *
   * @return JSON template object
   */
  private static JsonObject buildDocumentExtractionTemplate() {
    JsonObject template = new JsonObject();
    template.addProperty("purpose",
        "Extract these fields from STID/P&ID, C&E, SRS, line lists, and historian tag maps before running HAZOP.");
    JsonArray fields = new JsonArray();
    fields.add("nodes[].nodeId");
    fields.add("nodes[].designIntent");
    fields.add("nodes[].equipment[]");
    fields.add("nodes[].safeguards[]");
    fields.add("nodes[].evidenceRefs[]");
    fields.add("barrierRegister.evidence[]");
    fields.add("barrierRegister.barriers[]");
    fields.add("processDefinition.fluid and processDefinition.process");
    template.add("fields", fields);
    return template;
  }

  /**
   * Determines whether any node carries document evidence references.
   *
   * @param nodes HAZOP nodes
   * @return true if any evidence reference is present
   */
  private static boolean hasDocumentEvidence(List<HazopNode> nodes) {
    for (HazopNode node : nodes) {
      if (!node.evidenceRefs.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds a scenario name matching {@link AutomaticScenarioGenerator}.
   *
   * @param failure equipment failure
   * @return scenario name
   */
  private static String scenarioName(EquipmentFailure failure) {
    return failure.getMode().getDescription() + " - " + failure.getEquipmentName();
  }

  /**
   * Converts strings to a JSON array.
   *
   * @param values string values
   * @return JSON array
   */
  private static JsonArray toJsonArray(Iterable<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Formats simulation errors as text.
   *
   * @param errors simulation errors
   * @return combined error text
   */
  private static String errorsToText(List<SimulationResult.ErrorDetail> errors) {
    if (errors == null || errors.isEmpty()) {
      return "unknown error";
    }
    List<String> messages = new ArrayList<String>();
    for (SimulationResult.ErrorDetail error : errors) {
      messages.add(error.toString());
    }
    return String.join("; ", messages);
  }

  /**
   * Reads an optional string field.
   *
   * @param input source JSON object
   * @param field field name
   * @param defaultValue default value
   * @return field string or default value
   */
  private static String optString(JsonObject input, String field, String defaultValue) {
    return input.has(field) && !input.get(field).isJsonNull() ? input.get(field).getAsString()
        : defaultValue;
  }

  /**
   * Reads an optional boolean field.
   *
   * @param input source JSON object
   * @param field field name
   * @param defaultValue default value
   * @return field boolean or default value
   */
  private static boolean optBoolean(JsonObject input, String field, boolean defaultValue) {
    return input.has(field) && !input.get(field).isJsonNull() ? input.get(field).getAsBoolean()
        : defaultValue;
  }

  /**
   * Builds an error JSON response.
   *
   * @param message error message
   * @return JSON response string
   */
  private static String errorJson(String message) {
    JsonObject err = new JsonObject();
    err.addProperty("status", "error");
    err.addProperty("message", message);
    return GSON.toJson(err);
  }

  /**
   * Working state for one HAZOP study run.
   */
  private static final class StudyContext {
    private String studyId;
    private String processJson;
    private ProcessSystem process;
    private String baselineReportJson;
    private List<String> warnings = new ArrayList<String>();
    private List<HazopNode> nodes = new ArrayList<HazopNode>();
    private Set<FailureMode> enabledFailureModes = new LinkedHashSet<FailureMode>();
    private boolean runSimulations;
  }

  /**
   * HAZOP node extracted from documents or generated from a process unit.
   */
  private static final class HazopNode {
    private String nodeId;
    private String designIntent;
    private Set<String> equipmentNames = new LinkedHashSet<String>();
    private Set<String> safeguards = new LinkedHashSet<String>();
    private Set<String> evidenceRefs = new LinkedHashSet<String>();

    /**
     * Checks whether an equipment failure belongs to this node.
     *
     * @param failure equipment failure
     * @return true when the failure should produce a row for this node
     */
    private boolean matches(EquipmentFailure failure) {
      return equipmentNames.isEmpty() || equipmentNames.contains(failure.getEquipmentName());
    }
  }
}
