package neqsim.util.agentic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Agentic engineering kernel for planning, trust assessment, and autonomous study ranking.
 *
 * <p>
 * The kernel provides three reusable, JSON-contract actions for agents and MCP runners:
 * </p>
 * <ul>
 * <li><b>plan</b> builds an engineering intent graph and compiles it to a reviewable workflow.</li>
 * <li><b>trust</b> builds an evidence graph and scores result credibility.</li>
 * <li><b>study</b> ranks candidate designs against objectives and constraints.</li>
 * </ul>
 *
 * <p>
 * It is intentionally deterministic and side-effect free. Simulation execution remains in the
 * existing NeqSim runners; this class provides the planning and review substrate that agents use to
 * call those runners consistently.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AgenticEngineeringKernel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** JSON schema version emitted by this kernel. */
  public static final String SCHEMA_VERSION = "1.0";

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for utility class.
   */
  private AgenticEngineeringKernel() {}

  /**
   * Dispatches an agentic engineering action.
   *
   * @param json input JSON with an {@code action} field
   * @return JSON response for the selected action
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "Input JSON is null or empty",
          "Provide an object with action: plan, trust, or study.");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = stringValue(input, "action", "plan").toLowerCase(Locale.ROOT);
      if ("plan".equals(action) || "compile".equals(action) || "workflow".equals(action)) {
        return planWorkflow(json);
      }
      if ("trust".equals(action) || "evidence".equals(action) || "evaluateTrust".equals(action)) {
        return evaluateTrust(json);
      }
      if ("study".equals(action) || "optimize".equals(action) || "rank".equals(action)) {
        return runStudy(json);
      }
      return errorJson("UNKNOWN_ACTION", "Unknown agentic engineering action: " + action,
          "Use action 'plan', 'trust', or 'study'.");
    } catch (Exception e) {
      return errorJson("AGENTIC_KERNEL_ERROR", e.getMessage(), "Check that the input is JSON.");
    }
  }

  /**
   * Builds an engineering intent graph and compiles it to an executable workflow plan.
   *
   * @param json task input with task, optional objectives, constraints, standards, deliverables,
   *        and fluids
   * @return JSON containing engineeringIntent, intentGraph, workflowPlan, and compiledWorkflow
   */
  public static String planWorkflow(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String task =
          stringValue(input, "task", stringValue(input, "description", "engineering task"));
      List<String> domains = detectDomains(task, input);

      JsonObject intent = buildEngineeringIntent(task, domains, input);
      JsonObject graph = buildIntentGraph(intent);
      JsonObject plan = compileWorkflowPlan(intent, input);
      JsonObject compiled = buildCompiledWorkflow(intent, plan, input);

      JsonObject response = successBase("agentic engineering workflow planned");
      response.add("engineeringIntent", intent);
      response.add("intentGraph", graph);
      response.add("workflowPlan", plan);
      response.add("compiledWorkflow", compiled);
      response.add("qualityGates", workflowQualityGates(domains));
      response.add("nextActions",
          stringArray(Arrays.asList("review intentGraph for missing scope",
              "fetch schemas for each workflowPlan step", "validate generated runner inputs",
              "execute compiledWorkflow only after evidence and assumptions are accepted")));
      response.add("data", response.deepCopy());
      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("PLAN_ERROR", "Failed to plan workflow: " + e.getMessage(),
          "Check task text and optional arrays: objectives, constraints, standards, deliverables.");
    }
  }

  /**
   * Builds an evidence graph and computes a transparent trust score for a result package.
   *
   * @param json result package with optional result, provenance, validation, qualityGate,
   *        benchmarkTrust, assumptions, limitations, standards, and evidence arrays
   * @return JSON trust report with evidence graph, claims, score, verdict, and recommendations
   */
  public static String evaluateTrust(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      JsonObject result = objectValue(input, "result", input);
      JsonObject response = successBase("evidence trust evaluated");
      JsonObject graph = buildEvidenceGraph(input, result);
      JsonArray claims = buildValidationClaims(input, result);
      TrustScore score = calculateTrustScore(input, result, graph, claims);

      JsonObject trust = new JsonObject();
      trust.addProperty("score", round(score.score, 1));
      trust.addProperty("verdict", score.verdict);
      trust.addProperty("confidence", score.confidence);
      trust.add("positiveFactors", stringArray(score.positiveFactors));
      trust.add("negativeFactors", stringArray(score.negativeFactors));
      trust.add("recommendations", stringArray(score.recommendations));

      response.add("evidenceGraph", graph);
      response.add("validationClaims", claims);
      response.add("trust", trust);
      response.add("reviewPolicy", reviewPolicy(score));
      response.add("data", response.deepCopy());
      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("TRUST_ERROR", "Failed to evaluate trust: " + e.getMessage(),
          "Provide result/provenance/validation/evidence as JSON objects or arrays.");
    }
  }

  /**
   * Runs a deterministic autonomous study ranking over candidate designs.
   *
   * @param json study input with candidates, objectives, and constraints
   * @return JSON study report with candidate results, ranking, Pareto front, and recommendation
   */
  public static String runStudy(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      JsonArray objectives = arrayValue(input, "objectives");
      JsonArray constraints = arrayValue(input, "constraints");
      JsonArray candidates = arrayValue(input, "candidates");
      if (candidates.size() == 0) {
        candidates = generateDesignVariableCandidates(input);
      }
      if (objectives.size() == 0) {
        objectives = inferDefaultObjectives(candidates);
      }

      Map<String, MetricRange> ranges = metricRanges(candidates, objectives);
      List<JsonObject> evaluated = evaluateCandidates(candidates, objectives, constraints, ranges);
      Collections.sort(evaluated, candidateComparator());

      JsonObject response = successBase("autonomous study evaluated");
      response.add("studyPlan", buildStudyPlan(input, objectives, constraints, candidates));
      response.add("candidateResults", toJsonArray(evaluated));
      response.add("ranking", buildRanking(evaluated));
      response.add("paretoFront", buildParetoFront(evaluated, objectives));
      response.add("uncertaintySummary", buildUncertaintySummary(evaluated, objectives));
      response.add("recommendation", buildStudyRecommendation(evaluated));
      response.add("nextActions",
          stringArray(Arrays.asList("run NeqSim simulations for pending candidates",
              "promote the best feasible case to benchmark validation",
              "run evidence trust evaluation",
              "capture design snapshots before changing the process model")));
      response.add("data", response.deepCopy());
      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("STUDY_ERROR", "Failed to run autonomous study: " + e.getMessage(),
          "Provide candidates with metrics plus objectives and optional constraints.");
    }
  }

  /**
   * Creates a shared success response skeleton.
   *
   * @param message response message
   * @return mutable response object
   */
  private static JsonObject successBase(String message) {
    JsonObject response = new JsonObject();
    response.addProperty("schemaVersion", SCHEMA_VERSION);
    response.addProperty("status", "success");
    response.addProperty("message", message);
    return response;
  }

  /**
   * Builds an error response.
   *
   * @param code machine-readable error code
   * @param message human-readable error message
   * @param remediation suggested remediation
   * @return JSON error response
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject response = new JsonObject();
    response.addProperty("schemaVersion", SCHEMA_VERSION);
    response.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject error = new JsonObject();
    error.addProperty("code", code);
    error.addProperty("message", message);
    error.addProperty("remediation", remediation);
    errors.add(error);
    response.add("errors", errors);
    return GSON.toJson(response);
  }

  /**
   * Detects engineering domains from task text and optional explicit domain input.
   *
   * @param task task text
   * @param input full input object
   * @return ordered list of domains
   */
  private static List<String> detectDomains(String task, JsonObject input) {
    Set<String> domains = new LinkedHashSet<String>();
    if (input.has("domains") && input.get("domains").isJsonArray()) {
      for (JsonElement element : input.getAsJsonArray("domains")) {
        domains.add(element.getAsString());
      }
    }
    String lower = task == null ? "" : task.toLowerCase(Locale.ROOT);
    domains.add("thermodynamics");
    if (containsAny(lower, "compress", "separator", "heat exchanger", "distill", "teg",
        "process")) {
      domains.add("process");
    }
    if (containsAny(lower, "pipeline", "flowline", "pipe", "riser")) {
      domains.add("pipeline");
    }
    if (containsAny(lower, "hydrate", "wax", "asphaltene", "corrosion", "flow assurance")) {
      domains.add("flow-assurance");
    }
    if (containsAny(lower, "pvt", "cme", "cvd", "swelling", "phase envelope")) {
      domains.add("pvt");
    }
    if (containsAny(lower, "npv", "econom", "capex", "opex", "cost")) {
      domains.add("economics");
    }
    if (containsAny(lower, "safety", "relief", "psv", "hazop", "sil", "lopa", "flare")) {
      domains.add("safety");
    }
    if (containsAny(lower, "dynamic", "transient", "startup", "shutdown", "control")) {
      domains.add("dynamic");
    }
    if (containsAny(lower, "co2", "ccs", "carbon capture")) {
      domains.add("ccs");
    }
    if (containsAny(lower, "hydrogen", " h2", "h2 ")) {
      domains.add("hydrogen");
    }
    if (domains.size() == 1) {
      domains.add("process");
    }
    return new ArrayList<String>(domains);
  }

  /**
   * Returns true when a text contains any token.
   *
   * @param text text to search
   * @param tokens tokens to match
   * @return true when at least one token is present
   */
  private static boolean containsAny(String text, String... tokens) {
    for (int i = 0; i < tokens.length; i++) {
      if (text.contains(tokens[i])) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds the engineering intent object.
   *
   * @param task task text
   * @param domains detected domains
   * @param input full input object
   * @return engineering intent JSON
   */
  private static JsonObject buildEngineeringIntent(String task, List<String> domains,
      JsonObject input) {
    JsonObject intent = new JsonObject();
    intent.addProperty("id", stableId(task));
    intent.addProperty("task", task);
    intent.addProperty("scale", stringValue(input, "scale", "standard"));
    intent.add("domains", stringArray(domains));
    intent.add("objectives", arrayOrDefault(input, "objectives",
        Arrays.asList("produce validated engineering calculation", "quantify model risk")));
    intent.add("constraints", arrayOrDefault(input, "constraints",
        Arrays.asList("all inputs require explicit units", "engineering review required")));
    intent.add("standards", arrayOrDefault(input, "standards", Arrays.asList(
        "use task-specific API, ISO, NORSOK, DNV, ASME, or IEC standards where applicable")));
    intent.add("deliverables", arrayOrDefault(input, "deliverables", Arrays.asList("workflow plan",
        "simulation results", "validation summary", "evidence trust report")));
    intent.add("assumptions",
        arrayOrDefault(input, "assumptions",
            Arrays.asList("missing plant data must be listed as a gap",
                "screening outputs require discipline review")));
    if (input.has("fluid")) {
      intent.add("fluid", input.get("fluid"));
    }
    return intent;
  }

  /**
   * Builds a deterministic stable identifier from task text.
   *
   * @param task task text
   * @return stable identifier
   */
  private static String stableId(String task) {
    String clean = task == null ? "engineering-task"
        : task.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "")
            .replaceAll("-+$", "");
    if (clean.length() > 48) {
      return clean.substring(0, 48);
    }
    return clean.length() == 0 ? "engineering-task" : clean;
  }

  /**
   * Builds the intent graph.
   *
   * @param intent engineering intent object
   * @return graph with nodes and edges
   */
  private static JsonObject buildIntentGraph(JsonObject intent) {
    JsonObject graph = new JsonObject();
    JsonArray nodes = new JsonArray();
    JsonArray edges = new JsonArray();
    String taskId = "task:" + intent.get("id").getAsString();
    addGraphNode(nodes, taskId, "task", intent.get("task").getAsString());
    addIntentArray(nodes, edges, taskId, intent, "domains", "domain", "requiresDomain");
    addIntentArray(nodes, edges, taskId, intent, "objectives", "objective", "hasObjective");
    addIntentArray(nodes, edges, taskId, intent, "constraints", "constraint", "isConstrainedBy");
    addIntentArray(nodes, edges, taskId, intent, "standards", "standard", "usesStandardBasis");
    addIntentArray(nodes, edges, taskId, intent, "deliverables", "deliverable", "mustDeliver");
    graph.add("nodes", nodes);
    graph.add("edges", edges);
    graph.addProperty("nodeCount", nodes.size());
    graph.addProperty("edgeCount", edges.size());
    return graph;
  }

  /**
   * Adds graph nodes and edges for an intent array.
   *
   * @param nodes graph node array
   * @param edges graph edge array
   * @param source source node id
   * @param intent intent object
   * @param field intent field name
   * @param nodeType graph node type
   * @param edgeType graph edge type
   */
  private static void addIntentArray(JsonArray nodes, JsonArray edges, String source,
      JsonObject intent, String field, String nodeType, String edgeType) {
    JsonArray values = intent.getAsJsonArray(field);
    for (int i = 0; i < values.size(); i++) {
      String label = valueLabel(values.get(i));
      String id = nodeType + ":" + stableId(label);
      addGraphNode(nodes, id, nodeType, label);
      addGraphEdge(edges, source, id, edgeType);
    }
  }

  /**
   * Adds a graph node.
   *
   * @param nodes graph node array
   * @param id node id
   * @param type node type
   * @param label node label
   */
  private static void addGraphNode(JsonArray nodes, String id, String type, String label) {
    JsonObject node = new JsonObject();
    node.addProperty("id", id);
    node.addProperty("type", type);
    node.addProperty("label", label);
    nodes.add(node);
  }

  /**
   * Adds a graph edge.
   *
   * @param edges graph edge array
   * @param source source id
   * @param target target id
   * @param type edge type
   */
  private static void addGraphEdge(JsonArray edges, String source, String target, String type) {
    JsonObject edge = new JsonObject();
    edge.addProperty("source", source);
    edge.addProperty("target", target);
    edge.addProperty("type", type);
    edges.add(edge);
  }

  /**
   * Compiles a workflow plan from intent domains.
   *
   * @param intent engineering intent
   * @param input source input
   * @return workflow plan JSON
   */
  private static JsonObject compileWorkflowPlan(JsonObject intent, JsonObject input) {
    JsonObject plan = new JsonObject();
    JsonArray steps = new JsonArray();
    List<String> domains = stringsFromArray(intent.getAsJsonArray("domains"));
    addStep(steps, "discover_capabilities", "getCapabilities", "scope",
        "Discover available tool contracts and setup templates", new String[] {}, "schema_ready");
    addStep(steps, "validate_inputs", "validateInput", "scope",
        "Validate units, components, and process JSON before execution",
        new String[] {"discover_capabilities"}, "no_blocking_input_errors");
    addStep(steps, "flash_feed", "runFlash", "analysis",
        "Establish fluid phase state and base properties", new String[] {"validate_inputs"},
        "converged_flash");
    if (domains.contains("process") || input.has("process") || input.has("processJson")) {
      addStep(steps, "run_process", "runProcess", "analysis",
          "Execute steady-state process simulation", new String[] {"flash_feed"},
          "process_converged");
    }
    if (domains.contains("pipeline")) {
      addStep(steps, "run_pipeline", "runPipeline", "analysis",
          "Calculate pipeline hydraulics and pressure drop", new String[] {"flash_feed"},
          "hydraulics_completed");
    }
    if (domains.contains("flow-assurance") || domains.contains("ccs")
        || domains.contains("hydrogen")) {
      addStep(steps, "flow_assurance", "runFlowAssurance", "analysis",
          "Screen hydrate, wax, corrosion, erosion, or special-fluid risks",
          new String[] {"flash_feed"}, "flow_assurance_screened");
    }
    if (domains.contains("pvt")) {
      addStep(steps, "pvt_study", "runPVT", "analysis", "Run PVT experiment workflow",
          new String[] {"flash_feed"}, "pvt_completed");
    }
    String processDependency =
        domains.contains("process") || input.has("process") || input.has("processJson")
            ? "run_process"
            : "flash_feed";
    if (domains.contains("dynamic")) {
      addStep(steps, "dynamic_study", "runDynamic", "analysis",
          "Run transient or controller response simulation", new String[] {processDependency},
          "dynamic_completed");
    }
    if (domains.contains("economics")) {
      addStep(steps, "economics", "runFieldEconomics", "analysis",
          "Evaluate economic metrics and uncertainty drivers", new String[] {processDependency},
          "economics_completed");
    }
    if (domains.contains("safety")) {
      addStep(steps, "safety_screening", "runHAZOP", "validation",
          "Generate simulation-backed hazard and safeguard evidence",
          new String[] {processDependency}, "safety_review_required");
    }
    String primaryAnalysisStep = primaryAnalysisStep(domains, input);
    addStep(steps, "model_risk", "crossValidateModels", "validation",
        "Quantify EOS and model-selection sensitivity", new String[] {primaryAnalysisStep},
        "model_risk_documented");
    addStep(steps, "autonomous_study", "runAgenticEngineering", "optimization",
        "Rank candidate design alternatives against objectives and constraints",
        new String[] {primaryAnalysisStep, "model_risk"}, "best_feasible_case_identified");
    addStep(steps, "evidence_trust", "runAgenticEngineering", "validation",
        "Build evidence graph and trust score for the result package",
        new String[] {"autonomous_study"}, "trust_score_reported");
    addStep(steps, "report", "generateReport", "reporting",
        "Generate reviewable engineering deliverable", new String[] {"evidence_trust"},
        "report_has_traceability");
    plan.addProperty("planId", intent.get("id").getAsString() + "-workflow");
    plan.addProperty("executionMode", "reviewable_dag");
    plan.add("steps", steps);
    plan.add("requiredReview", stringArray(Arrays.asList("intent scope", "input evidence",
        "model applicability", "validation gates", "final recommendations")));
    return plan;
  }

  /**
   * Selects the primary analysis step that downstream validation should depend on.
   *
   * @param domains detected engineering domains
   * @param input source input object
   * @return workflow step id for the primary calculation
   */
  private static String primaryAnalysisStep(List<String> domains, JsonObject input) {
    if (domains.contains("process") || input.has("process") || input.has("processJson")) {
      return "run_process";
    }
    if (domains.contains("pipeline")) {
      return "run_pipeline";
    }
    if (domains.contains("flow-assurance") || domains.contains("ccs")
        || domains.contains("hydrogen")) {
      return "flow_assurance";
    }
    if (domains.contains("pvt")) {
      return "pvt_study";
    }
    return "flash_feed";
  }

  /**
   * Adds a workflow step.
   *
   * @param steps step array
   * @param id step id
   * @param tool MCP tool name
   * @param phase workflow phase
   * @param purpose step purpose
   * @param dependsOn dependency ids
   * @param gate success gate
   */
  private static void addStep(JsonArray steps, String id, String tool, String phase, String purpose,
      String[] dependsOn, String gate) {
    JsonObject step = new JsonObject();
    step.addProperty("id", id);
    step.addProperty("tool", tool);
    step.addProperty("phase", phase);
    step.addProperty("purpose", purpose);
    step.add("dependsOn", stringArray(Arrays.asList(dependsOn)));
    step.addProperty("successGate", gate);
    step.addProperty("engineeringReviewRequired", true);
    steps.add(step);
  }

  /**
   * Builds a composeWorkflow-style payload for compatible linear execution.
   *
   * @param intent engineering intent
   * @param plan workflow plan
   * @param input source input
   * @return compiled workflow object
   */
  private static JsonObject buildCompiledWorkflow(JsonObject intent, JsonObject plan,
      JsonObject input) {
    JsonObject compiled = new JsonObject();
    compiled.addProperty("workflow", intent.get("id").getAsString());
    if (input.has("fluid")) {
      compiled.add("fluid", input.get("fluid"));
    }
    JsonArray compiledSteps = new JsonArray();
    JsonArray steps = plan.getAsJsonArray("steps");
    for (int i = 0; i < steps.size(); i++) {
      JsonObject source = steps.get(i).getAsJsonObject();
      String runner = runnerName(source.get("tool").getAsString());
      if (runner == null) {
        continue;
      }
      JsonObject step = new JsonObject();
      step.addProperty("name", source.get("id").getAsString());
      step.addProperty("runner", runner);
      JsonObject stepInput = new JsonObject();
      if ("runAgenticEngineering".equals(source.get("tool").getAsString())) {
        stepInput.addProperty("action",
            source.get("id").getAsString().contains("trust") ? "trust" : "study");
      }
      step.add("input", stepInput);
      compiledSteps.add(step);
    }
    compiled.add("steps", compiledSteps);
    compiled.addProperty("note",
        "This is a starter payload; each step input must be completed from schemas before execution.");
    return compiled;
  }

  /**
   * Maps MCP tool names to TaskSolverRunner runner names.
   *
   * @param tool MCP tool name
   * @return runner name or null when not composeWorkflow-compatible
   */
  private static String runnerName(String tool) {
    if ("runFlash".equals(tool)) {
      return "flash";
    }
    if ("runProcess".equals(tool)) {
      return "process";
    }
    if ("runPipeline".equals(tool)) {
      return "pipeline";
    }
    if ("runFlowAssurance".equals(tool)) {
      return "flow_assurance";
    }
    if ("runPVT".equals(tool)) {
      return "pvt";
    }
    if ("runDynamic".equals(tool)) {
      return "dynamic";
    }
    if ("runFieldEconomics".equals(tool)) {
      return "economics";
    }
    return null;
  }

  /**
   * Builds workflow quality gates.
   *
   * @param domains detected domains
   * @return quality gate array
   */
  private static JsonArray workflowQualityGates(List<String> domains) {
    JsonArray gates = new JsonArray();
    addGate(gates, "input_evidence", "No blocking input validation findings");
    addGate(gates, "simulation", "All required runner steps converge or explain non-convergence");
    addGate(gates, "benchmark", "Benchmark trust metadata checked for design-sensitive outputs");
    addGate(gates, "uncertainty", "Model and parameter uncertainty are reported");
    if (domains.contains("safety")) {
      addGate(gates, "safety_review", "Safety outputs require independent discipline review");
    }
    addGate(gates, "traceability",
        "Every recommendation links to evidence, assumptions, and results");
    return gates;
  }

  /**
   * Adds a quality gate entry.
   *
   * @param gates mutable gate array
   * @param id gate id
   * @param description gate description
   */
  private static void addGate(JsonArray gates, String id, String description) {
    JsonObject gate = new JsonObject();
    gate.addProperty("id", id);
    gate.addProperty("description", description);
    gate.addProperty("blocking", true);
    gates.add(gate);
  }

  /**
   * Builds an evidence graph from input and result metadata.
   *
   * @param input trust input object
   * @param result result object
   * @return evidence graph JSON
   */
  private static JsonObject buildEvidenceGraph(JsonObject input, JsonObject result) {
    JsonObject graph = new JsonObject();
    JsonArray nodes = new JsonArray();
    JsonArray edges = new JsonArray();
    addGraphNode(nodes, "result:primary", "result", "calculation result package");
    addEvidenceNodes(input, nodes, edges, "evidence", "evidence");
    addEvidenceNodes(input, nodes, edges, "assumptions", "assumption");
    addEvidenceNodes(input, nodes, edges, "limitations", "limitation");
    addEvidenceNodes(input, nodes, edges, "standards", "standard");
    if (input.has("standards_applied")) {
      addEvidenceNodes(input, nodes, edges, "standards_applied", "standard");
    }
    if (input.has("provenance") || result.has("provenance")) {
      addGraphNode(nodes, "provenance:result", "provenance", "result provenance block");
      addGraphEdge(edges, "provenance:result", "result:primary", "supports");
    }
    if (input.has("validation") || result.has("validation")) {
      addGraphNode(nodes, "validation:result", "validation", "validation block");
      addGraphEdge(edges, "validation:result", "result:primary", "validates");
    }
    if (input.has("qualityGate") || result.has("qualityGate")) {
      addGraphNode(nodes, "qualityGate:result", "qualityGate", "quality gate block");
      addGraphEdge(edges, "qualityGate:result", "result:primary", "gates");
    }
    graph.add("nodes", nodes);
    graph.add("edges", edges);
    graph.addProperty("nodeCount", nodes.size());
    graph.addProperty("edgeCount", edges.size());
    return graph;
  }

  /**
   * Adds evidence nodes from an input field.
   *
   * @param input input object
   * @param nodes graph node array
   * @param edges graph edge array
   * @param field source field name
   * @param type node type
   */
  private static void addEvidenceNodes(JsonObject input, JsonArray nodes, JsonArray edges,
      String field, String type) {
    if (!input.has(field)) {
      return;
    }
    JsonArray values = elementAsArray(input.get(field));
    for (int i = 0; i < values.size(); i++) {
      String label = valueLabel(values.get(i));
      String id = type + ":" + stableId(label) + ":" + i;
      addGraphNode(nodes, id, type, label);
      addGraphEdge(edges, id, "result:primary", "supports");
    }
  }

  /**
   * Builds validation claims from result metadata.
   *
   * @param input input object
   * @param result result object
   * @return validation claim array
   */
  private static JsonArray buildValidationClaims(JsonObject input, JsonObject result) {
    JsonArray claims = new JsonArray();
    addClaim(claims, "provenance_present", hasObject(input, result, "provenance"),
        "Calculation provenance is available");
    addClaim(claims, "validation_present", hasObject(input, result, "validation"),
        "Validation block is available");
    addClaim(claims, "quality_gate_present", hasObject(input, result, "qualityGate"),
        "Quality gate block is available");
    addClaim(claims, "evidence_present", input.has("evidence"),
        "Independent evidence references are available");
    addClaim(claims, "assumptions_present", input.has("assumptions"),
        "Assumptions are explicitly recorded");
    addClaim(claims, "benchmark_trust_present",
        input.has("benchmarkTrust") || result.has("benchmarkTrust"),
        "Benchmark trust metadata is available");
    return claims;
  }

  /**
   * Adds a validation claim.
   *
   * @param claims claim array
   * @param id claim id
   * @param passed whether the claim passed
   * @param statement claim statement
   */
  private static void addClaim(JsonArray claims, String id, boolean passed, String statement) {
    JsonObject claim = new JsonObject();
    claim.addProperty("id", id);
    claim.addProperty("statement", statement);
    claim.addProperty("passed", passed);
    claims.add(claim);
  }

  /**
   * Checks whether a metadata object exists in input or result.
   *
   * @param input input object
   * @param result result object
   * @param field field name
   * @return true when present as an object
   */
  private static boolean hasObject(JsonObject input, JsonObject result, String field) {
    return (input.has(field) && input.get(field).isJsonObject())
        || (result.has(field) && result.get(field).isJsonObject());
  }

  /**
   * Calculates trust score.
   *
   * @param input input object
   * @param result result object
   * @param graph evidence graph
   * @param claims validation claims
   * @return trust score object
   */
  private static TrustScore calculateTrustScore(JsonObject input, JsonObject result,
      JsonObject graph, JsonArray claims) {
    TrustScore trust = new TrustScore();
    trust.score = 50.0;
    if (passedClaim(claims, "provenance_present")) {
      trust.score += 10.0;
      trust.positiveFactors.add("provenance present");
    } else {
      trust.score -= 10.0;
      trust.negativeFactors.add("missing provenance");
      trust.recommendations
          .add("Attach ResultProvenance before using outputs for design decisions");
    }
    if (validationPassed(
        objectValue(input, "validation", objectValue(result, "validation", null)))) {
      trust.score += 15.0;
      trust.positiveFactors.add("validation passed");
    } else {
      trust.score -= 10.0;
      trust.negativeFactors.add("validation missing or not passed");
      trust.recommendations
          .add("Run validateResults or SimulationQualityGate on the result package");
    }
    if (qualityGatePassed(
        objectValue(input, "qualityGate", objectValue(result, "qualityGate", null)))) {
      trust.score += 15.0;
      trust.positiveFactors.add("quality gate passed");
    } else {
      trust.score -= 10.0;
      trust.negativeFactors.add("quality gate missing or not passed");
    }
    trust.score += evidenceBonus(graph, trust);
    applyBenchmarkTrust(input, result, trust);
    int warnings = countArrayField(input, result, "warnings");
    if (warnings > 0) {
      trust.score -= Math.min(12.0, warnings * 2.0);
      trust.negativeFactors.add("warnings present: " + warnings);
    }
    int limitations = countArrayField(input, result, "limitations");
    if (limitations > 0) {
      trust.score -= Math.min(10.0, limitations * 1.5);
      trust.negativeFactors.add("limitations present: " + limitations);
    }
    trust.score = Math.max(0.0, Math.min(100.0, trust.score));
    trust.verdict =
        trust.score >= 80.0 ? "HIGH_TRUST" : trust.score >= 60.0 ? "REVIEW_REQUIRED" : "LOW_TRUST";
    trust.confidence = trust.score >= 80.0 ? "high" : trust.score >= 60.0 ? "medium" : "low";
    if (trust.recommendations.isEmpty()) {
      trust.recommendations
          .add("Proceed with qualified engineering review and preserve evidence graph");
    }
    return trust;
  }

  /**
   * Checks a named claim.
   *
   * @param claims claim array
   * @param id claim id
   * @return true when passed
   */
  private static boolean passedClaim(JsonArray claims, String id) {
    for (int i = 0; i < claims.size(); i++) {
      JsonObject claim = claims.get(i).getAsJsonObject();
      if (id.equals(claim.get("id").getAsString())) {
        return claim.get("passed").getAsBoolean();
      }
    }
    return false;
  }

  /**
   * Checks validation status.
   *
   * @param validation validation object or null
   * @return true when validation is explicitly valid or passed
   */
  private static boolean validationPassed(JsonObject validation) {
    if (validation == null) {
      return false;
    }
    if (validation.has("valid") && validation.get("valid").getAsBoolean()) {
      return true;
    }
    if (validation.has("acceptance_criteria_met")
        && validation.get("acceptance_criteria_met").getAsBoolean()) {
      return true;
    }
    if (validation.has("verdict")) {
      String verdict = validation.get("verdict").getAsString().toLowerCase(Locale.ROOT);
      return verdict.contains("pass");
    }
    return false;
  }

  /**
   * Checks quality gate status.
   *
   * @param qualityGate quality gate object or null
   * @return true when the gate is passed
   */
  private static boolean qualityGatePassed(JsonObject qualityGate) {
    if (qualityGate == null || !qualityGate.has("verdict")) {
      return false;
    }
    String verdict = qualityGate.get("verdict").getAsString().toLowerCase(Locale.ROOT);
    return "passed".equals(verdict) || "pass".equals(verdict);
  }

  /**
   * Computes evidence bonus.
   *
   * @param graph evidence graph
   * @param trust trust score to annotate
   * @return bonus points
   */
  private static double evidenceBonus(JsonObject graph, TrustScore trust) {
    int nodes = graph.get("nodeCount").getAsInt();
    if (nodes >= 5) {
      trust.positiveFactors.add("evidence graph populated");
      return Math.min(10.0, (nodes - 1) * 1.5);
    }
    trust.negativeFactors.add("sparse evidence graph");
    trust.recommendations
        .add("Add evidence references, assumptions, standards, and benchmark links");
    return 0.0;
  }

  /**
   * Applies benchmark trust score adjustments.
   *
   * @param input input object
   * @param result result object
   * @param trust trust score to modify
   */
  private static void applyBenchmarkTrust(JsonObject input, JsonObject result, TrustScore trust) {
    JsonObject benchmark =
        objectValue(input, "benchmarkTrust", objectValue(result, "benchmarkTrust", null));
    if (benchmark == null) {
      trust.negativeFactors.add("missing benchmark trust metadata");
      trust.recommendations.add("Call getBenchmarkTrust for the producing MCP tool");
      return;
    }
    String maturity = stringValue(benchmark, "maturityLevel", "TESTED").toUpperCase(Locale.ROOT);
    if ("VALIDATED".equals(maturity)) {
      trust.score += 10.0;
      trust.positiveFactors.add("validated benchmark maturity");
    } else if ("TESTED".equals(maturity)) {
      trust.score += 5.0;
      trust.positiveFactors.add("tested benchmark maturity");
    } else {
      trust.score -= 10.0;
      trust.negativeFactors.add("experimental benchmark maturity");
      trust.recommendations
          .add("Do not use experimental-maturity outputs without independent validation");
    }
  }

  /**
   * Builds review policy for a trust score.
   *
   * @param score trust score
   * @return review policy object
   */
  private static JsonObject reviewPolicy(TrustScore score) {
    JsonObject policy = new JsonObject();
    policy.addProperty("engineeringReviewRequired", true);
    policy.addProperty("minimumAction", "independent discipline review");
    policy.addProperty("canUseForScreening", score.score >= 50.0);
    policy.addProperty("canUseForDesignDecision", score.score >= 80.0);
    return policy;
  }

  /**
   * Generates candidates from design variables when no explicit candidates are present.
   *
   * @param input study input
   * @return candidate array
   */
  private static JsonArray generateDesignVariableCandidates(JsonObject input) {
    JsonArray generated = new JsonArray();
    JsonArray variables = arrayValue(input, "designVariables");
    for (int i = 0; i < variables.size(); i++) {
      JsonObject variable = variables.get(i).getAsJsonObject();
      String name = stringValue(variable, "name", "variable_" + i);
      addGeneratedCandidate(generated, name + "_low", name, numericValue(variable, "low", 0.0));
      addGeneratedCandidate(generated, name + "_base", name, numericValue(variable, "base", 0.0));
      addGeneratedCandidate(generated, name + "_high", name, numericValue(variable, "high", 0.0));
    }
    return generated;
  }

  /**
   * Adds a generated study candidate.
   *
   * @param candidates candidate array
   * @param candidateName candidate name
   * @param variableName variable name
   * @param value variable value
   */
  private static void addGeneratedCandidate(JsonArray candidates, String candidateName,
      String variableName, double value) {
    JsonObject candidate = new JsonObject();
    candidate.addProperty("name", candidateName);
    JsonObject variables = new JsonObject();
    variables.addProperty(variableName, value);
    candidate.add("variables", variables);
    candidate.add("metrics", new JsonObject());
    candidate.addProperty("status", "PENDING_SIMULATION");
    candidates.add(candidate);
  }

  /**
   * Infers a default objective from candidate metrics.
   *
   * @param candidates candidate array
   * @return objective array
   */
  private static JsonArray inferDefaultObjectives(JsonArray candidates) {
    JsonArray objectives = new JsonArray();
    for (int i = 0; i < candidates.size(); i++) {
      JsonObject metrics = objectValue(candidates.get(i).getAsJsonObject(), "metrics", null);
      if (metrics != null) {
        for (Map.Entry<String, JsonElement> entry : metrics.entrySet()) {
          if (entry.getValue().isJsonPrimitive()
              && entry.getValue().getAsJsonPrimitive().isNumber()) {
            JsonObject objective = new JsonObject();
            objective.addProperty("name", "maximize " + entry.getKey());
            objective.addProperty("metric", entry.getKey());
            objective.addProperty("goal", "maximize");
            objective.addProperty("weight", 1.0);
            objectives.add(objective);
            return objectives;
          }
        }
      }
    }
    return objectives;
  }

  /**
   * Computes metric ranges for objectives.
   *
   * @param candidates candidates
   * @param objectives objectives
   * @return metric range map
   */
  private static Map<String, MetricRange> metricRanges(JsonArray candidates, JsonArray objectives) {
    Map<String, MetricRange> ranges = new LinkedHashMap<String, MetricRange>();
    for (int i = 0; i < objectives.size(); i++) {
      JsonObject objective = objectives.get(i).getAsJsonObject();
      String metric = stringValue(objective, "metric", stringValue(objective, "name", ""));
      ranges.put(metric, new MetricRange());
    }
    for (int i = 0; i < candidates.size(); i++) {
      JsonObject metrics = objectValue(candidates.get(i).getAsJsonObject(), "metrics", null);
      if (metrics == null) {
        continue;
      }
      for (Map.Entry<String, MetricRange> entry : ranges.entrySet()) {
        if (metrics.has(entry.getKey()) && metrics.get(entry.getKey()).isJsonPrimitive()) {
          JsonElement element = metrics.get(entry.getKey());
          if (element.getAsJsonPrimitive().isNumber()) {
            entry.getValue().include(element.getAsDouble());
          }
        }
      }
    }
    return ranges;
  }

  /**
   * Evaluates all candidates.
   *
   * @param candidates candidate input
   * @param objectives objective definitions
   * @param constraints constraint definitions
   * @param ranges metric ranges
   * @return evaluated candidate list
   */
  private static List<JsonObject> evaluateCandidates(JsonArray candidates, JsonArray objectives,
      JsonArray constraints, Map<String, MetricRange> ranges) {
    List<JsonObject> evaluated = new ArrayList<JsonObject>();
    for (int i = 0; i < candidates.size(); i++) {
      JsonObject candidate = candidates.get(i).getAsJsonObject();
      JsonObject out = candidate.deepCopy();
      JsonObject metrics = objectValue(candidate, "metrics", new JsonObject());
      ConstraintResult constraintResult = evaluateConstraints(metrics, constraints);
      double objectiveScore = evaluateObjectives(metrics, objectives, ranges, out);
      out.addProperty("feasible", constraintResult.feasible);
      out.addProperty("score",
          round(constraintResult.feasible ? objectiveScore : objectiveScore * 0.25, 2));
      out.add("constraintFindings", stringArray(constraintResult.findings));
      evaluated.add(out);
    }
    return evaluated;
  }

  /**
   * Evaluates objectives for one candidate.
   *
   * @param metrics candidate metrics
   * @param objectives objective array
   * @param ranges metric ranges
   * @param candidateOut candidate output to annotate
   * @return weighted score from 0 to 100
   */
  private static double evaluateObjectives(JsonObject metrics, JsonArray objectives,
      Map<String, MetricRange> ranges, JsonObject candidateOut) {
    double weighted = 0.0;
    double totalWeight = 0.0;
    JsonArray contributions = new JsonArray();
    for (int i = 0; i < objectives.size(); i++) {
      JsonObject objective = objectives.get(i).getAsJsonObject();
      String metric = stringValue(objective, "metric", stringValue(objective, "name", ""));
      String goal = stringValue(objective, "goal", "maximize").toLowerCase(Locale.ROOT);
      double weight = numericValue(objective, "weight", 1.0);
      double normalized = 0.0;
      if (metrics.has(metric) && metrics.get(metric).isJsonPrimitive()
          && metrics.get(metric).getAsJsonPrimitive().isNumber()) {
        double value = metrics.get(metric).getAsDouble();
        normalized = normalizedObjective(value, goal, objective, ranges.get(metric));
      }
      weighted += normalized * weight;
      totalWeight += weight;
      JsonObject contribution = new JsonObject();
      contribution.addProperty("metric", metric);
      contribution.addProperty("goal", goal);
      contribution.addProperty("normalized", round(normalized, 4));
      contribution.addProperty("weight", weight);
      contributions.add(contribution);
    }
    candidateOut.add("objectiveContributions", contributions);
    return totalWeight > 0.0 ? weighted / totalWeight * 100.0 : 0.0;
  }

  /**
   * Computes normalized objective score.
   *
   * @param value metric value
   * @param goal objective goal
   * @param objective objective object
   * @param range metric range
   * @return normalized score from 0 to 1
   */
  private static double normalizedObjective(double value, String goal, JsonObject objective,
      MetricRange range) {
    if (range == null || !range.hasData() || range.max == range.min) {
      return 1.0;
    }
    if ("minimize".equals(goal)) {
      return (range.max - value) / (range.max - range.min);
    }
    if ("target".equals(goal)) {
      double target = numericValue(objective, "target", (range.max + range.min) / 2.0);
      double span = Math.max(Math.abs(range.max - target), Math.abs(target - range.min));
      return span > 0.0 ? Math.max(0.0, 1.0 - Math.abs(value - target) / span) : 1.0;
    }
    return (value - range.min) / (range.max - range.min);
  }

  /**
   * Evaluates constraints.
   *
   * @param metrics candidate metrics
   * @param constraints constraints
   * @return constraint result
   */
  private static ConstraintResult evaluateConstraints(JsonObject metrics, JsonArray constraints) {
    ConstraintResult result = new ConstraintResult();
    for (int i = 0; i < constraints.size(); i++) {
      JsonObject constraint = constraints.get(i).getAsJsonObject();
      String metric = stringValue(constraint, "metric", stringValue(constraint, "name", ""));
      String operator = stringValue(constraint, "operator", stringValue(constraint, "op", "<="));
      double limit = numericValue(constraint, "value", numericValue(constraint, "limit", 0.0));
      if (!metrics.has(metric) || !metrics.get(metric).isJsonPrimitive()
          || !metrics.get(metric).getAsJsonPrimitive().isNumber()) {
        result.feasible = false;
        result.findings.add("missing metric for constraint: " + metric);
        continue;
      }
      double value = metrics.get(metric).getAsDouble();
      if (!constraintSatisfied(value, operator, limit)) {
        result.feasible = false;
        result.findings.add(metric + " " + value + " violates " + operator + " " + limit);
      }
    }
    if (result.findings.isEmpty()) {
      result.findings.add("all constraints satisfied");
    }
    return result;
  }

  /**
   * Checks one constraint expression.
   *
   * @param value metric value
   * @param operator operator string
   * @param limit limit value
   * @return true if satisfied
   */
  private static boolean constraintSatisfied(double value, String operator, double limit) {
    if ("<".equals(operator)) {
      return value < limit;
    }
    if ("<=".equals(operator)) {
      return value <= limit;
    }
    if (">".equals(operator)) {
      return value > limit;
    }
    if (">=".equals(operator)) {
      return value >= limit;
    }
    if ("==".equals(operator)) {
      return Math.abs(value - limit) < 1.0e-9;
    }
    return value <= limit;
  }

  /**
   * Provides candidate ranking comparator.
   *
   * @return comparator for evaluated candidate objects
   */
  private static Comparator<JsonObject> candidateComparator() {
    return new Comparator<JsonObject>() {
      @Override
      public int compare(JsonObject left, JsonObject right) {
        boolean leftFeasible = left.has("feasible") && left.get("feasible").getAsBoolean();
        boolean rightFeasible = right.has("feasible") && right.get("feasible").getAsBoolean();
        if (leftFeasible != rightFeasible) {
          return leftFeasible ? -1 : 1;
        }
        double leftScore = left.has("score") ? left.get("score").getAsDouble() : 0.0;
        double rightScore = right.has("score") ? right.get("score").getAsDouble() : 0.0;
        return Double.compare(rightScore, leftScore);
      }
    };
  }

  /**
   * Builds ranking array.
   *
   * @param evaluated evaluated candidates
   * @return ranking array
   */
  private static JsonArray buildRanking(List<JsonObject> evaluated) {
    JsonArray ranking = new JsonArray();
    for (int i = 0; i < evaluated.size(); i++) {
      JsonObject candidate = evaluated.get(i);
      JsonObject rank = new JsonObject();
      rank.addProperty("rank", i + 1);
      rank.addProperty("name", stringValue(candidate, "name", "candidate_" + (i + 1)));
      rank.addProperty("score",
          candidate.has("score") ? candidate.get("score").getAsDouble() : 0.0);
      rank.addProperty("feasible",
          candidate.has("feasible") && candidate.get("feasible").getAsBoolean());
      ranking.add(rank);
    }
    return ranking;
  }

  /**
   * Builds Pareto front from evaluated candidates.
   *
   * @param evaluated evaluated candidates
   * @param objectives objective definitions
   * @return Pareto front array
   */
  private static JsonArray buildParetoFront(List<JsonObject> evaluated, JsonArray objectives) {
    JsonArray front = new JsonArray();
    for (int i = 0; i < evaluated.size(); i++) {
      JsonObject candidate = evaluated.get(i);
      if (!candidate.has("feasible") || !candidate.get("feasible").getAsBoolean()) {
        continue;
      }
      if (!isDominated(candidate, evaluated, objectives)) {
        JsonObject point = new JsonObject();
        point.addProperty("name", stringValue(candidate, "name", "candidate_" + i));
        point.add("metrics", objectValue(candidate, "metrics", new JsonObject()));
        front.add(point);
      }
    }
    return front;
  }

  /**
   * Checks whether a candidate is dominated.
   *
   * @param candidate candidate under test
   * @param evaluated all candidates
   * @param objectives objective array
   * @return true if dominated by another feasible candidate
   */
  private static boolean isDominated(JsonObject candidate, List<JsonObject> evaluated,
      JsonArray objectives) {
    for (int i = 0; i < evaluated.size(); i++) {
      JsonObject other = evaluated.get(i);
      if (other == candidate || !other.has("feasible") || !other.get("feasible").getAsBoolean()) {
        continue;
      }
      if (dominates(other, candidate, objectives)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks Pareto dominance.
   *
   * @param left possible dominating candidate
   * @param right possible dominated candidate
   * @param objectives objective definitions
   * @return true when left dominates right
   */
  private static boolean dominates(JsonObject left, JsonObject right, JsonArray objectives) {
    JsonObject leftMetrics = objectValue(left, "metrics", new JsonObject());
    JsonObject rightMetrics = objectValue(right, "metrics", new JsonObject());
    boolean strictlyBetter = false;
    for (int i = 0; i < objectives.size(); i++) {
      JsonObject objective = objectives.get(i).getAsJsonObject();
      String metric = stringValue(objective, "metric", stringValue(objective, "name", ""));
      String goal = stringValue(objective, "goal", "maximize").toLowerCase(Locale.ROOT);
      if (!leftMetrics.has(metric) || !rightMetrics.has(metric)) {
        return false;
      }
      double leftValue = leftMetrics.get(metric).getAsDouble();
      double rightValue = rightMetrics.get(metric).getAsDouble();
      int comparison =
          objectiveComparison(leftValue, rightValue, goal, numericValue(objective, "target", 0.0));
      if (comparison < 0) {
        return false;
      }
      if (comparison > 0) {
        strictlyBetter = true;
      }
    }
    return strictlyBetter;
  }

  /**
   * Compares two objective values.
   *
   * @param left left value
   * @param right right value
   * @param goal objective goal
   * @param target target for target goals
   * @return positive when left is better, negative when worse, zero when equal
   */
  private static int objectiveComparison(double left, double right, String goal, double target) {
    if ("minimize".equals(goal)) {
      return Double.compare(right, left);
    }
    if ("target".equals(goal)) {
      return Double.compare(Math.abs(right - target), Math.abs(left - target));
    }
    return Double.compare(left, right);
  }

  /**
   * Builds uncertainty summary from the primary objective values.
   *
   * @param evaluated evaluated candidates
   * @param objectives objectives
   * @return uncertainty summary object
   */
  private static JsonObject buildUncertaintySummary(List<JsonObject> evaluated,
      JsonArray objectives) {
    JsonObject summary = new JsonObject();
    if (objectives.size() == 0) {
      summary.addProperty("status", "no_objective_metrics");
      return summary;
    }
    String metric = stringValue(objectives.get(0).getAsJsonObject(), "metric",
        stringValue(objectives.get(0).getAsJsonObject(), "name", ""));
    List<Double> values = new ArrayList<Double>();
    for (int i = 0; i < evaluated.size(); i++) {
      JsonObject metrics = objectValue(evaluated.get(i), "metrics", null);
      if (metrics != null && metrics.has(metric) && metrics.get(metric).isJsonPrimitive()
          && metrics.get(metric).getAsJsonPrimitive().isNumber()) {
        values.add(metrics.get(metric).getAsDouble());
      }
    }
    Collections.sort(values);
    summary.addProperty("metric", metric);
    summary.addProperty("sampleCount", values.size());
    if (!values.isEmpty()) {
      summary.addProperty("p10", percentile(values, 0.10));
      summary.addProperty("p50", percentile(values, 0.50));
      summary.addProperty("p90", percentile(values, 0.90));
    }
    return summary;
  }

  /**
   * Computes percentile from sorted values.
   *
   * @param sorted sorted values
   * @param fraction percentile fraction from 0 to 1
   * @return percentile value
   */
  private static double percentile(List<Double> sorted, double fraction) {
    if (sorted.isEmpty()) {
      return Double.NaN;
    }
    double index = fraction * (sorted.size() - 1);
    int low = (int) Math.floor(index);
    int high = (int) Math.ceil(index);
    if (low == high) {
      return sorted.get(low);
    }
    double weight = index - low;
    return sorted.get(low) * (1.0 - weight) + sorted.get(high) * weight;
  }

  /**
   * Builds study plan summary.
   *
   * @param input input object
   * @param objectives objectives
   * @param constraints constraints
   * @param candidates candidates
   * @return study plan object
   */
  private static JsonObject buildStudyPlan(JsonObject input, JsonArray objectives,
      JsonArray constraints, JsonArray candidates) {
    JsonObject plan = new JsonObject();
    plan.addProperty("studyName",
        stringValue(input, "studyName", stringValue(input, "task", "agentic study")));
    plan.addProperty("candidateCount", candidates.size());
    plan.addProperty("objectiveCount", objectives.size());
    plan.addProperty("constraintCount", constraints.size());
    plan.add("objectives", objectives.deepCopy());
    plan.add("constraints", constraints.deepCopy());
    plan.addProperty("method",
        "deterministic feasibility filter plus weighted normalized objective ranking");
    return plan;
  }

  /**
   * Builds a final study recommendation.
   *
   * @param evaluated evaluated candidates sorted by rank
   * @return recommendation object
   */
  private static JsonObject buildStudyRecommendation(List<JsonObject> evaluated) {
    JsonObject recommendation = new JsonObject();
    if (evaluated.isEmpty()) {
      recommendation.addProperty("status", "no_candidates");
      recommendation.addProperty("message", "No candidate designs were available to rank");
      return recommendation;
    }
    JsonObject best = evaluated.get(0);
    recommendation.addProperty("status", "candidate_selected");
    recommendation.addProperty("candidate", stringValue(best, "name", "candidate_1"));
    recommendation.addProperty("score", best.has("score") ? best.get("score").getAsDouble() : 0.0);
    recommendation.addProperty("feasible",
        best.has("feasible") && best.get("feasible").getAsBoolean());
    recommendation.addProperty("message",
        "Promote the highest-ranked feasible candidate to detailed NeqSim simulation, benchmark validation, and evidence trust review.");
    return recommendation;
  }

  /**
   * Converts evaluated candidate list to a JSON array.
   *
   * @param objects objects to convert
   * @return JSON array
   */
  private static JsonArray toJsonArray(List<JsonObject> objects) {
    JsonArray array = new JsonArray();
    for (int i = 0; i < objects.size(); i++) {
      array.add(objects.get(i));
    }
    return array;
  }

  /**
   * Gets string value from an object.
   *
   * @param object JSON object
   * @param field field name
   * @param defaultValue fallback value
   * @return string value
   */
  private static String stringValue(JsonObject object, String field, String defaultValue) {
    if (object != null && object.has(field) && !object.get(field).isJsonNull()) {
      JsonElement element = object.get(field);
      if (element.isJsonPrimitive()) {
        return element.getAsString();
      }
      return valueLabel(element);
    }
    return defaultValue;
  }

  /**
   * Gets numeric value from an object.
   *
   * @param object JSON object
   * @param field field name
   * @param defaultValue fallback value
   * @return numeric value
   */
  private static double numericValue(JsonObject object, String field, double defaultValue) {
    if (object != null && object.has(field) && object.get(field).isJsonPrimitive()
        && object.get(field).getAsJsonPrimitive().isNumber()) {
      return object.get(field).getAsDouble();
    }
    return defaultValue;
  }

  /**
   * Gets object value from an object.
   *
   * @param object JSON object
   * @param field field name
   * @param defaultValue fallback object
   * @return object value or fallback
   */
  private static JsonObject objectValue(JsonObject object, String field, JsonObject defaultValue) {
    if (object != null && object.has(field) && object.get(field).isJsonObject()) {
      return object.getAsJsonObject(field);
    }
    return defaultValue;
  }

  /**
   * Gets array value from an object.
   *
   * @param object JSON object
   * @param field field name
   * @return array value or empty array
   */
  private static JsonArray arrayValue(JsonObject object, String field) {
    if (object != null && object.has(field)) {
      return elementAsArray(object.get(field));
    }
    return new JsonArray();
  }

  /**
   * Returns a JSON array from any JSON element.
   *
   * @param element source element
   * @return element as array, or wrapped element
   */
  private static JsonArray elementAsArray(JsonElement element) {
    JsonArray array = new JsonArray();
    if (element == null || element.isJsonNull()) {
      return array;
    }
    if (element.isJsonArray()) {
      return element.getAsJsonArray();
    }
    array.add(element);
    return array;
  }

  /**
   * Copies array field or creates a default string array.
   *
   * @param input input object
   * @param field field name
   * @param defaults default values
   * @return array value
   */
  private static JsonArray arrayOrDefault(JsonObject input, String field, List<String> defaults) {
    if (input.has(field)) {
      return elementAsArray(input.get(field)).deepCopy();
    }
    return stringArray(defaults);
  }

  /**
   * Creates a JSON string array.
   *
   * @param values values to add
   * @return JSON array
   */
  private static JsonArray stringArray(List<String> values) {
    JsonArray array = new JsonArray();
    for (int i = 0; i < values.size(); i++) {
      array.add(values.get(i));
    }
    return array;
  }

  /**
   * Converts a JSON array to strings.
   *
   * @param array input array
   * @return string list
   */
  private static List<String> stringsFromArray(JsonArray array) {
    List<String> values = new ArrayList<String>();
    for (int i = 0; i < array.size(); i++) {
      values.add(valueLabel(array.get(i)));
    }
    return values;
  }

  /**
   * Creates a display label for a JSON value.
   *
   * @param element element to label
   * @return display label
   */
  private static String valueLabel(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return "null";
    }
    if (element.isJsonPrimitive()) {
      return element.getAsString();
    }
    if (element.isJsonObject()) {
      JsonObject object = element.getAsJsonObject();
      if (object.has("id")) {
        return object.get("id").getAsString();
      }
      if (object.has("name")) {
        return object.get("name").getAsString();
      }
      if (object.has("code")) {
        return object.get("code").getAsString();
      }
      if (object.has("description")) {
        return object.get("description").getAsString();
      }
    }
    String json = element.toString();
    return json.length() > 80 ? json.substring(0, 80) : json;
  }

  /**
   * Counts array fields in input or result.
   *
   * @param input input object
   * @param result result object
   * @param field field to count
   * @return item count
   */
  private static int countArrayField(JsonObject input, JsonObject result, String field) {
    int count = 0;
    if (input.has(field)) {
      count += elementAsArray(input.get(field)).size();
    }
    if (result.has(field)) {
      count += elementAsArray(result.get(field)).size();
    }
    return count;
  }

  /**
   * Rounds a value to a number of decimals.
   *
   * @param value value to round
   * @param decimals number of decimals
   * @return rounded value
   */
  private static double round(double value, int decimals) {
    double factor = Math.pow(10.0, decimals);
    return Math.round(value * factor) / factor;
  }

  /**
   * Mutable trust score accumulator.
   */
  private static class TrustScore {
    double score;
    String verdict;
    String confidence;
    final List<String> positiveFactors = new ArrayList<String>();
    final List<String> negativeFactors = new ArrayList<String>();
    final List<String> recommendations = new ArrayList<String>();
  }

  /**
   * Mutable metric range accumulator.
   */
  private static class MetricRange {
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;

    /**
     * Adds a value to the range.
     *
     * @param value metric value
     */
    void include(double value) {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }

    /**
     * Returns whether the range has data.
     *
     * @return true when min and max were set
     */
    boolean hasData() {
      return min != Double.POSITIVE_INFINITY && max != Double.NEGATIVE_INFINITY;
    }
  }

  /**
   * Mutable constraint result accumulator.
   */
  private static class ConstraintResult {
    boolean feasible = true;
    final List<String> findings = new ArrayList<String>();
  }
}
