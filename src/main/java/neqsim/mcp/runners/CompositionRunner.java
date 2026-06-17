package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Multi-server composition layer that enables NeqSim MCP to coordinate with external MCP servers
 * for cross-domain engineering workflows.
 *
 * <p>
 * Manages a registry of external MCP server endpoints and provides orchestration for multi-server
 * workflows. Each external server can be a cost estimation service, plant historian connector,
 * CAD/3D system, document extraction service, or any MCP-compliant tool provider.
 * </p>
 *
 * <p>
 * Since NeqSim MCP runs over STDIO, external server calls are modeled as registered endpoints that
 * agents can discover and invoke. The actual cross-server communication is handled by the host
 * application (Claude, Copilot, etc.) which has access to all connected MCP servers. This runner
 * provides the composition metadata and workflow templates.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CompositionRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Registry of known external MCP servers and their capabilities. */
  private static final ConcurrentHashMap<String, ExternalServer> SERVERS =
      new ConcurrentHashMap<String, ExternalServer>();

  /** Pre-built workflow templates for common multi-server patterns. */
  private static final Map<String, WorkflowTemplate> TEMPLATES =
      new LinkedHashMap<String, WorkflowTemplate>();

  static {
    initializeDefaultServers();
    initializeWorkflowTemplates();
  }

  /**
   * Private constructor — all methods are static.
   */
  private CompositionRunner() {}

  /**
   * Main entry point for composition operations.
   *
   * @param json JSON with action and parameters
   * @return JSON with results
   */
  public static String run(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "";

      switch (action) {
        case "listServers":
          return listServers();
        case "registerServer":
          return registerServer(input);
        case "removeServer":
          return removeServer(input);
        case "listWorkflows":
          return listWorkflows();
        case "getWorkflow":
          return getWorkflow(input);
        case "planComposition":
          return planComposition(input);
        case "describeCapabilities":
          return describeCapabilities();
        default:
          return errorJson("UNKNOWN_ACTION", "Unknown composition action: " + action,
              "Use: listServers, registerServer, removeServer, listWorkflows, "
                  + "getWorkflow, planComposition, describeCapabilities");
      }
    } catch (Exception e) {
      return errorJson("COMPOSITION_ERROR", e.getMessage(), "Check JSON format");
    }
  }

  /**
   * Lists all known external MCP servers.
   *
   * @return JSON with server registry
   */
  private static String listServers() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("count", SERVERS.size());

    JsonArray servers = new JsonArray();
    for (ExternalServer server : SERVERS.values()) {
      servers.add(server.toJson());
    }
    response.add("servers", servers);
    response.addProperty("note",
        "These are known MCP server types that can compose with NeqSim. "
            + "The host application (Claude, Copilot) handles actual connections. "
            + "Use 'planComposition' to get a multi-server workflow plan.");
    return GSON.toJson(response);
  }

  /**
   * Registers a new external MCP server.
   *
   * @param input JSON with server details
   * @return JSON confirmation
   */
  private static String registerServer(JsonObject input) {
    String name = input.has("name") ? input.get("name").getAsString() : "";
    if (name.isEmpty()) {
      return errorJson("MISSING_NAME", "Server name is required", "Provide 'name' field");
    }

    ExternalServer server = new ExternalServer();
    server.name = name;
    server.description = input.has("description") ? input.get("description").getAsString() : "";
    server.domain = input.has("domain") ? input.get("domain").getAsString() : "general";
    server.transport = input.has("transport") ? input.get("transport").getAsString() : "stdio";
    server.version = input.has("version") ? input.get("version").getAsString() : "1.0";

    if (input.has("tools") && input.get("tools").isJsonArray()) {
      for (JsonElement t : input.getAsJsonArray("tools")) {
        server.tools.add(t.getAsString());
      }
    }

    if (input.has("dataFormats") && input.get("dataFormats").isJsonArray()) {
      for (JsonElement f : input.getAsJsonArray("dataFormats")) {
        server.dataFormats.add(f.getAsString());
      }
    }

    SERVERS.put(name, server);

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("message", "Server '" + name + "' registered");
    response.addProperty("totalServers", SERVERS.size());
    return GSON.toJson(response);
  }

  /**
   * Removes an external server from the registry.
   *
   * @param input JSON with server name
   * @return JSON confirmation
   */
  private static String removeServer(JsonObject input) {
    String name = input.has("name") ? input.get("name").getAsString() : "";
    ExternalServer removed = SERVERS.remove(name);

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("removed", removed != null);
    response.addProperty("totalServers", SERVERS.size());
    return GSON.toJson(response);
  }

  /**
   * Lists available multi-server workflow templates.
   *
   * @return JSON with workflow catalog
   */
  private static String listWorkflows() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("count", TEMPLATES.size());

    JsonArray workflows = new JsonArray();
    for (WorkflowTemplate tmpl : TEMPLATES.values()) {
      JsonObject wf = new JsonObject();
      wf.addProperty("id", tmpl.id);
      wf.addProperty("name", tmpl.name);
      wf.addProperty("description", tmpl.description);
      wf.addProperty("stepCount", tmpl.steps.size());

      JsonArray servers = new JsonArray();
      for (String s : tmpl.requiredServers) {
        servers.add(s);
      }
      wf.add("requiredServers", servers);
      workflows.add(wf);
    }
    response.add("workflows", workflows);
    return GSON.toJson(response);
  }

  /**
   * Gets detailed workflow template by ID.
   *
   * @param input JSON with workflow ID
   * @return JSON with full workflow definition
   */
  private static String getWorkflow(JsonObject input) {
    String id = input.has("workflowId") ? input.get("workflowId").getAsString() : "";
    WorkflowTemplate tmpl = TEMPLATES.get(id);

    if (tmpl == null) {
      return errorJson("NOT_FOUND", "Workflow '" + id + "' not found",
          "Use listWorkflows to see available templates");
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("id", tmpl.id);
    response.addProperty("name", tmpl.name);
    response.addProperty("description", tmpl.description);

    JsonArray steps = new JsonArray();
    for (WorkflowStep step : tmpl.steps) {
      steps.add(step.toJson());
    }
    response.add("steps", steps);

    JsonArray servers = new JsonArray();
    for (String s : tmpl.requiredServers) {
      servers.add(s);
    }
    response.add("requiredServers", servers);

    return GSON.toJson(response);
  }

  /**
   * Plans a multi-server composition based on a task description.
   *
   * @param input JSON with task description
   * @return JSON with recommended servers and workflow plan
   */
  private static String planComposition(JsonObject input) {
    String task = input.has("task") ? input.get("task").getAsString() : "";

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("task", task);

    // Analyze task keywords to recommend servers and workflows
    String lower = task.toLowerCase();

    JsonArray recommended = new JsonArray();
    JsonArray steps = new JsonArray();

    // Always include NeqSim
    addRecommendation(recommended, "neqsim", "Core thermodynamic and process simulation",
        "required");

    // Check for cost-related keywords
    if (lower.contains("cost") || lower.contains("capex") || lower.contains("opex")
        || lower.contains("economic") || lower.contains("npv") || lower.contains("budget")) {
      addRecommendation(recommended, "cost-estimation",
          "CAPEX/OPEX cost estimation and economic analysis", "recommended");
      addStep(steps, 1, "neqsim", "runProcess", "Run process simulation to size equipment");
      addStep(steps, 2, "cost-estimation", "estimateCosts",
          "Estimate costs based on sized equipment");
    }

    // Check for plant data keywords
    if (lower.contains("plant") || lower.contains("historian") || lower.contains("pi ")
        || lower.contains("ip.21") || lower.contains("operational") || lower.contains("measured")
        || lower.contains("digital twin")) {
      addRecommendation(recommended, "plant-historian",
          "Real-time and historical plant data access", "recommended");
      addStep(steps, 1, "plant-historian", "readTags",
          "Read current operating data from historian");
      addStep(steps, 2, "neqsim", "runProcess", "Run simulation with real operating conditions");
      addStep(steps, 3, "neqsim", "validateResults", "Compare simulation vs measured values");
    }

    // Check for document extraction
    if (lower.contains("datasheet") || lower.contains("document") || lower.contains("pdf")
        || lower.contains("extract") || lower.contains("vendor")
        || lower.contains("specification")) {
      addRecommendation(recommended, "document-extraction",
          "Extract data from PDFs, datasheets, and engineering documents", "recommended");
    }

    // Check for 3D/layout
    if (lower.contains("layout") || lower.contains("3d") || lower.contains("cad")
        || lower.contains("piping") || lower.contains("arrangement")) {
      addRecommendation(recommended, "cad-3d", "3D equipment layout and piping design", "optional");
    }

    // Check for safety/risk
    if (lower.contains("safety") || lower.contains("hazop") || lower.contains("sil")
        || lower.contains("relief") || lower.contains("flare")) {
      addRecommendation(recommended, "safety-analysis", "HAZOP, SIL, relief/flare analysis",
          "recommended");
    }

    // Default: if no specific steps planned, give generic plan
    if (steps.size() == 0) {
      addStep(steps, 1, "neqsim", "runFlash or runProcess",
          "Run thermodynamic or process simulation");
      addStep(steps, 2, "neqsim", "validateResults", "Validate results against design rules");
      addStep(steps, 3, "neqsim", "generateReport", "Generate engineering report");
    }

    response.add("recommendedServers", recommended);
    response.add("suggestedSteps", steps);
    response.addProperty("compositionNote",
        "The host application orchestrates cross-server calls. "
            + "Each step's output feeds into the next step's input. "
            + "NeqSim provides the thermodynamic and process simulation core.");
    return GSON.toJson(response);
  }

  /**
   * Describes NeqSim MCP's composition capabilities and interfaces.
   *
   * @return JSON with capability manifest for composition
   */
  private static String describeCapabilities() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("server", "neqsim-mcp");

    // What NeqSim provides to other servers
    JsonObject provides = new JsonObject();
    JsonArray providesList = new JsonArray();
    providesList.add("Thermodynamic flash calculations (TP, PH, PS, dew/bubble point)");
    providesList.add("Process simulation (separators, compressors, heat exchangers, etc.)");
    providesList.add("PVT analysis (CME, CVD, saturation pressure)");
    providesList.add("Flow assurance (hydrate, wax, corrosion prediction)");
    providesList.add("Pipeline hydraulics (pressure drop, temperature profile)");
    providesList.add("Standards calculations (ISO 6976, AGA, EN 16723)");
    providesList.add("Phase envelopes and property tables");
    providesList.add("Equipment sizing and mechanical design");
    providesList.add("Dynamic simulation with control loops");
    providesList.add("Reservoir material balance");
    providesList.add("Field development economics");
    provides.add("capabilities", providesList);

    // What NeqSim can consume from other servers
    JsonObject consumes = new JsonObject();
    JsonArray consumesList = new JsonArray();
    consumesList.add("Operating conditions from plant historians (T, P, flow, composition)");
    consumesList.add("Equipment specifications from vendor datasheets");
    consumesList.add("Cost data for economic analysis");
    consumesList.add("Material properties from materials databases");
    consumesList.add("Wellbore surveys and completion data");
    consumesList.add("Reservoir properties and PVT lab data");
    consumes.add("dataInputs", consumesList);

    // Data format interfaces
    JsonObject formats = new JsonObject();
    JsonArray formatList = new JsonArray();
    formatList.add("JSON (primary — all tools accept/return JSON)");
    formatList.add("CSV (component databases, standards tables)");
    formatList.add("SVG (visualizations — phase envelopes, charts)");
    formatList.add("Mermaid (flowsheet diagrams)");
    formatList.add("Markdown (engineering reports)");
    formats.add("supportedFormats", formatList);

    response.add("provides", provides);
    response.add("consumes", consumes);
    response.add("dataFormats", formats);

    return GSON.toJson(response);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Initialization
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Initializes the default external server registry.
   */
  private static void initializeDefaultServers() {
    ExternalServer costServer = new ExternalServer();
    costServer.name = "cost-estimation";
    costServer.description = "Equipment and project cost estimation (CAPEX, OPEX, lifecycle)";
    costServer.domain = "economics";
    costServer.tools.add("estimateEquipmentCost");
    costServer.tools.add("estimateProjectCost");
    costServer.tools.add("calculateNPV");
    costServer.tools.add("generateBOM");
    costServer.dataFormats.add("JSON");
    costServer.dataFormats.add("CSV");
    SERVERS.put("cost-estimation", costServer);

    ExternalServer historianServer = new ExternalServer();
    historianServer.name = "plant-historian";
    historianServer.description = "Plant data historian access (OSIsoft PI, Aspen IP.21, OPC UA)";
    historianServer.domain = "operations";
    historianServer.tools.add("readTags");
    historianServer.tools.add("readHistory");
    historianServer.tools.add("listTags");
    historianServer.tools.add("writeTags");
    historianServer.dataFormats.add("JSON");
    historianServer.dataFormats.add("timeseries");
    SERVERS.put("plant-historian", historianServer);

    ExternalServer cadServer = new ExternalServer();
    cadServer.name = "cad-3d";
    cadServer.description = "3D CAD and equipment layout";
    cadServer.domain = "design";
    cadServer.tools.add("createLayout");
    cadServer.tools.add("generateIsometric");
    cadServer.tools.add("checkClashes");
    cadServer.dataFormats.add("JSON");
    cadServer.dataFormats.add("IFC");
    cadServer.dataFormats.add("STEP");
    SERVERS.put("cad-3d", cadServer);

    ExternalServer docServer = new ExternalServer();
    docServer.name = "document-extraction";
    docServer.description = "Extract data from engineering documents (PDF, datasheets, P&IDs)";
    docServer.domain = "document";
    docServer.tools.add("extractFromPDF");
    docServer.tools.add("parseDatasheet");
    docServer.tools.add("readPID");
    docServer.tools.add("extractStreamTable");
    docServer.dataFormats.add("JSON");
    docServer.dataFormats.add("PDF");
    docServer.dataFormats.add("XLSX");
    SERVERS.put("document-extraction", docServer);

    ExternalServer safetyServer = new ExternalServer();
    safetyServer.name = "safety-analysis";
    safetyServer.description = "Process safety analysis (HAZOP, SIL, relief, consequence)";
    safetyServer.domain = "safety";
    safetyServer.tools.add("runHAZOP");
    safetyServer.tools.add("calculateSIL");
    safetyServer.tools.add("sizeReliefValve");
    safetyServer.tools.add("runConsequenceAnalysis");
    safetyServer.dataFormats.add("JSON");
    SERVERS.put("safety-analysis", safetyServer);
  }

  /**
   * Initializes pre-built workflow templates.
   */
  private static void initializeWorkflowTemplates() {
    // 1. Digital Twin workflow
    WorkflowTemplate digitalTwin = new WorkflowTemplate();
    digitalTwin.id = "digital-twin";
    digitalTwin.name = "Digital Twin Loop";
    digitalTwin.description = "Continuous model-vs-plant comparison with auto-tuning";
    digitalTwin.requiredServers.add("neqsim");
    digitalTwin.requiredServers.add("plant-historian");
    digitalTwin.steps.add(new WorkflowStep(1, "plant-historian", "readTags",
        "Read current operating data (temperatures, pressures, flows)"));
    digitalTwin.steps.add(new WorkflowStep(2, "neqsim", "runProcess",
        "Run simulation with real operating conditions"));
    digitalTwin.steps.add(new WorkflowStep(3, "neqsim", "validateResults",
        "Compare simulation vs measured values, identify deviations"));
    digitalTwin.steps.add(new WorkflowStep(4, "neqsim", "generateReport",
        "Generate deviation report with recommendations"));
    TEMPLATES.put("digital-twin", digitalTwin);

    // 2. FEED Study workflow
    WorkflowTemplate feed = new WorkflowTemplate();
    feed.id = "feed-study";
    feed.name = "FEED Study Package";
    feed.description = "Front-End Engineering Design: process sim + sizing + cost + deliverables";
    feed.requiredServers.add("neqsim");
    feed.requiredServers.add("cost-estimation");
    feed.steps.add(new WorkflowStep(1, "neqsim", "runProcess",
        "Run process simulation to establish operating conditions"));
    feed.steps.add(new WorkflowStep(2, "neqsim", "validateResults",
        "Validate against design standards (ASME, API, NORSOK)"));
    feed.steps.add(new WorkflowStep(3, "cost-estimation", "estimateProjectCost",
        "Estimate CAPEX from sized equipment"));
    feed.steps.add(new WorkflowStep(4, "neqsim", "generateReport",
        "Generate engineering report with all results"));
    TEMPLATES.put("feed-study", feed);

    // 3. Vendor datasheet workflow
    WorkflowTemplate vendor = new WorkflowTemplate();
    vendor.id = "vendor-evaluation";
    vendor.name = "Vendor Equipment Evaluation";
    vendor.description = "Extract vendor data, simulate, compare with specifications";
    vendor.requiredServers.add("neqsim");
    vendor.requiredServers.add("document-extraction");
    vendor.steps.add(new WorkflowStep(1, "document-extraction", "parseDatasheet",
        "Extract equipment specs from vendor datasheet"));
    vendor.steps.add(
        new WorkflowStep(2, "neqsim", "runProcess", "Simulate equipment at specified conditions"));
    vendor.steps.add(new WorkflowStep(3, "neqsim", "validateResults",
        "Compare simulation results vs vendor guarantees"));
    TEMPLATES.put("vendor-evaluation", vendor);

    // 4. Safety study workflow
    WorkflowTemplate safety = new WorkflowTemplate();
    safety.id = "safety-study";
    safety.name = "Process Safety Study";
    safety.description = "Depressurization + relief sizing + consequence analysis";
    safety.requiredServers.add("neqsim");
    safety.requiredServers.add("safety-analysis");
    safety.steps.add(new WorkflowStep(1, "neqsim", "runProcess",
        "Define process conditions and fluid compositions"));
    safety.steps.add(
        new WorkflowStep(2, "neqsim", "runDynamic", "Simulate depressurization/blowdown scenario"));
    safety.steps.add(new WorkflowStep(3, "safety-analysis", "sizeReliefValve",
        "Size relief valve based on worst-case flow"));
    safety.steps.add(new WorkflowStep(4, "safety-analysis", "runConsequenceAnalysis",
        "Model release dispersion and thermal radiation"));
    TEMPLATES.put("safety-study", safety);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Adds a recommendation entry to an array.
   *
   * @param arr the array
   * @param server the server name
   * @param reason the reason
   * @param priority the priority level
   */
  private static void addRecommendation(JsonArray arr, String server, String reason,
      String priority) {
    JsonObject rec = new JsonObject();
    rec.addProperty("server", server);
    rec.addProperty("reason", reason);
    rec.addProperty("priority", priority);
    arr.add(rec);
  }

  /**
   * Adds a workflow step to an array.
   *
   * @param arr the array
   * @param order the step order
   * @param server the server
   * @param tool the tool
   * @param description the description
   */
  private static void addStep(JsonArray arr, int order, String server, String tool,
      String description) {
    JsonObject step = new JsonObject();
    step.addProperty("order", order);
    step.addProperty("server", server);
    step.addProperty("tool", tool);
    step.addProperty("description", description);
    arr.add(step);
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix
   * @return the JSON string
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

  // ═══════════════════════════════════════════════════════════════════════════
  // Inner types
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Represents an external MCP server.
   */
  static class ExternalServer {
    /** Server name. */
    String name = "";

    /** Description. */
    String description = "";

    /** Domain (economics, operations, design, etc.). */
    String domain = "";

    /** Transport type (stdio, sse, http). */
    String transport = "stdio";

    /** Version. */
    String version = "1.0";

    /** Available tool names. */
    List<String> tools = new ArrayList<String>();

    /** Supported data formats. */
    List<String> dataFormats = new ArrayList<String>();

    /**
     * Converts to JSON object.
     *
     * @return the JSON representation
     */
    JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("name", name);
      obj.addProperty("description", description);
      obj.addProperty("domain", domain);
      obj.addProperty("transport", transport);
      obj.addProperty("version", version);
      JsonArray toolArr = new JsonArray();
      for (String t : tools) {
        toolArr.add(t);
      }
      obj.add("tools", toolArr);
      JsonArray fmtArr = new JsonArray();
      for (String f : dataFormats) {
        fmtArr.add(f);
      }
      obj.add("dataFormats", fmtArr);
      return obj;
    }
  }

  /**
   * A workflow template.
   */
  static class WorkflowTemplate {
    /** Template ID. */
    String id = "";

    /** Template name. */
    String name = "";

    /** Description. */
    String description = "";

    /** Required server names. */
    List<String> requiredServers = new ArrayList<String>();

    /** Ordered workflow steps. */
    List<WorkflowStep> steps = new ArrayList<WorkflowStep>();
  }

  /**
   * A single step in a workflow.
   */
  static class WorkflowStep {
    /** Step order. */
    int order;

    /** Server name. */
    String server;

    /** Tool to invoke. */
    String tool;

    /** Description. */
    String description;

    /**
     * Creates a workflow step.
     *
     * @param order the step order
     * @param server the server name
     * @param tool the tool name
     * @param description the step description
     */
    WorkflowStep(int order, String server, String tool, String description) {
      this.order = order;
      this.server = server;
      this.tool = tool;
      this.description = description;
    }

    /**
     * Converts to JSON.
     *
     * @return the JSON representation
     */
    JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("order", order);
      obj.addProperty("server", server);
      obj.addProperty("tool", tool);
      obj.addProperty("description", description);
      return obj;
    }
  }
}
