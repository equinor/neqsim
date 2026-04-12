package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Stateful session runner that maintains live ProcessSystem instances across multiple MCP calls.
 *
 * <p>
 * Enables incremental flowsheet construction: create a session with a fluid, add equipment
 * one-by-one, modify parameters, and re-run without re-sending the entire JSON each time. Sessions
 * are stored in-memory with configurable TTL and max session count.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SessionRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Max sessions to prevent unbounded memory growth. */
  private static final int MAX_SESSIONS = 50;

  /** Session TTL in milliseconds (30 minutes). */
  private static final long SESSION_TTL_MS = 30L * 60L * 1000L;

  /** Active sessions keyed by session ID. */
  private static final ConcurrentHashMap<String, SessionState> SESSIONS =
      new ConcurrentHashMap<String, SessionState>();

  /**
   * Private constructor — all methods are static.
   */
  private SessionRunner() {}

  /**
   * Routes a session command based on the "action" field in the JSON.
   *
   * @param json the JSON command
   * @return JSON result string
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("SESSION_ERROR", "JSON input is null or empty",
          "Provide a JSON object with 'action' field");
    }

    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "";

      switch (action) {
        case "create":
          return createSession(input);
        case "addEquipment":
          return addEquipment(input);
        case "run":
          return runSession(input);
        case "modify":
          return modifyParameter(input);
        case "getState":
          return getSessionState(input);
        case "list":
          return listSessions();
        case "close":
          return closeSession(input);
        default:
          return errorJson("UNKNOWN_ACTION", "Unknown session action: " + action,
              "Use: create, addEquipment, run, modify, getState, list, close");
      }
    } catch (Exception e) {
      return errorJson("SESSION_ERROR", "Session operation failed: " + e.getMessage(),
          "Check JSON format and session ID validity");
    }
  }

  /**
   * Creates a new session with a fluid definition.
   *
   * @param input JSON with fluid definition
   * @return JSON with session ID
   */
  private static String createSession(JsonObject input) {
    evictExpiredSessions();
    if (SESSIONS.size() >= MAX_SESSIONS) {
      return errorJson("SESSION_LIMIT", "Maximum session count reached (" + MAX_SESSIONS + ")",
          "Close existing sessions with action: 'close'");
    }

    String sessionId = UUID.randomUUID().toString().substring(0, 8);
    String name = input.has("name") ? input.get("name").getAsString() : "Session-" + sessionId;

    // Build process from fluid definition or from full process JSON
    SessionState state = new SessionState(sessionId, name);

    if (input.has("processJson")) {
      // Create from full process JSON (like run_process)
      String processJson = GSON.toJson(input.get("processJson"));
      SimulationResult result = ProcessSystem.fromJsonAndRun(processJson);
      if (result.isError()) {
        String errMsg =
            result.getErrors().isEmpty() ? "unknown error" : result.getErrors().get(0).getMessage();
        return errorJson("PROCESS_BUILD_ERROR", "Failed to build process: " + errMsg,
            "Check the processJson structure");
      }
      state.process = result.getProcessSystem();
      state.hasRun = true;
    } else if (input.has("fluid")) {
      // Create from fluid definition with optional first stream
      JsonObject fluidDef = input.getAsJsonObject("fluid");
      state.fluidJson = GSON.toJson(fluidDef);
      state.process = new ProcessSystem();
      state.process.setName(name);
    }

    SESSIONS.put(sessionId, state);

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", sessionId);
    response.addProperty("name", name);
    response.addProperty("message",
        "Session created. Use 'addEquipment' to build flowsheet, " + "then 'run' to simulate.");
    response.addProperty("equipmentCount", state.process != null ? state.process.size() : 0);
    return GSON.toJson(response);
  }

  /**
   * Adds equipment to an existing session.
   *
   * @param input JSON with sessionId and equipment definition
   * @return JSON with updated process state
   */
  private static String addEquipment(JsonObject input) {
    SessionState state = getValidSession(input);
    if (state == null) {
      return errorJson("SESSION_NOT_FOUND", "Session not found or expired",
          "Create a new session with action: 'create'");
    }

    if (!input.has("equipment")) {
      return errorJson("MISSING_EQUIPMENT", "No 'equipment' field in request",
          "Provide equipment as JSON object with 'type', 'name', 'inlet', 'properties'");
    }

    // Build a mini process JSON with the existing fluid + all existing equipment + the new one
    JsonObject equipment = input.getAsJsonObject("equipment");
    state.equipmentDefs.add(equipment);
    state.hasRun = false;

    // Rebuild and run the full process
    String fullJson = buildFullProcessJson(state);
    SimulationResult result = ProcessSystem.fromJsonAndRun(fullJson);
    if (result.isError()) {
      state.equipmentDefs.remove(state.equipmentDefs.size() - 1);
      String errMsg =
          result.getErrors().isEmpty() ? "unknown error" : result.getErrors().get(0).getMessage();
      return errorJson("EQUIPMENT_ADD_ERROR", "Failed to add equipment: " + errMsg,
          "Check equipment type, name, inlet reference, and properties");
    }

    state.process = result.getProcessSystem();
    state.hasRun = true;
    state.lastAccess = System.currentTimeMillis();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("equipmentCount", state.process.size());
    response.addProperty("added",
        equipment.has("name") ? equipment.get("name").getAsString() : "unnamed");
    response.add("equipmentList", getEquipmentList(state.process));

    // Include brief results
    String report = state.process.getReport_json();
    if (report != null && !report.isEmpty()) {
      response.add("results", JsonParser.parseString(report));
    }

    return GSON.toJson(response);
  }

  /**
   * Runs the current session process.
   *
   * @param input JSON with sessionId
   * @return JSON with simulation results
   */
  private static String runSession(JsonObject input) {
    SessionState state = getValidSession(input);
    if (state == null) {
      return errorJson("SESSION_NOT_FOUND", "Session not found or expired",
          "Create a new session with action: 'create'");
    }

    long startTime = System.currentTimeMillis();

    // Rebuild and run the full process
    String fullJson = buildFullProcessJson(state);
    SimulationResult result = ProcessSystem.fromJsonAndRun(fullJson);
    if (result.isError()) {
      String errMsg =
          result.getErrors().isEmpty() ? "unknown error" : result.getErrors().get(0).getMessage();
      return errorJson("RUN_ERROR", "Simulation failed: " + errMsg,
          "Check equipment connections and parameter values");
    }

    state.process = result.getProcessSystem();
    state.hasRun = true;
    state.runCount++;
    state.lastAccess = System.currentTimeMillis();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("runCount", state.runCount);
    response.addProperty("computationTimeMs", System.currentTimeMillis() - startTime);
    response.addProperty("equipmentCount", state.process.size());

    // Full results
    String report = state.process.getReport_json();
    if (report != null && !report.isEmpty()) {
      response.add("results", JsonParser.parseString(report));
    }

    // Warnings from simulation result
    if (result.hasWarnings()) {
      JsonArray warnings = new JsonArray();
      for (String w : result.getWarnings()) {
        warnings.add(w);
      }
      response.add("warnings", warnings);
    }

    return GSON.toJson(response);
  }

  /**
   * Modifies a parameter in the session process and optionally re-runs.
   *
   * @param input JSON with sessionId, address, value, unit, and optional autoRun flag
   * @return JSON with modification result
   */
  private static String modifyParameter(JsonObject input) {
    SessionState state = getValidSession(input);
    if (state == null) {
      return errorJson("SESSION_NOT_FOUND", "Session not found or expired",
          "Create a new session with action: 'create'");
    }

    String address = input.has("address") ? input.get("address").getAsString() : "";
    if (address.isEmpty()) {
      return errorJson("MISSING_ADDRESS", "No 'address' field",
          "Provide equipment.property address like 'Compressor.outletPressure'");
    }

    double value = input.has("value") ? input.get("value").getAsDouble() : 0.0;
    String unit = input.has("unit") ? input.get("unit").getAsString() : "";
    boolean autoRun = !input.has("autoRun") || input.get("autoRun").getAsBoolean();

    // Use automation API to set the value
    if (state.process == null) {
      return errorJson("NO_PROCESS", "No process in session", "Add equipment first");
    }

    // Rebuild process first to ensure it's current
    String fullJson = buildFullProcessJson(state);
    SimulationResult buildResult = ProcessSystem.fromJsonAndRun(fullJson);
    if (buildResult.isError()) {
      String errMsg = buildResult.getErrors().isEmpty() ? "unknown error"
          : buildResult.getErrors().get(0).getMessage();
      return errorJson("REBUILD_ERROR", "Failed to rebuild process", errMsg);
    }
    state.process = buildResult.getProcessSystem();

    try {
      neqsim.process.automation.ProcessAutomation auto = state.process.getAutomation();
      if (unit.isEmpty()) {
        auto.setVariableValue(address, value, "");
      } else {
        auto.setVariableValue(address, value, unit);
      }
    } catch (Exception e) {
      return errorJson("SET_ERROR", "Failed to set " + address + ": " + e.getMessage(),
          "Check address format. Use getState to see available equipment and properties.");
    }

    state.lastAccess = System.currentTimeMillis();

    if (autoRun) {
      state.process.run();
      state.hasRun = true;
      state.runCount++;
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("modified", address);
    response.addProperty("newValue", value);
    if (!unit.isEmpty()) {
      response.addProperty("unit", unit);
    }
    response.addProperty("reRan", autoRun);

    if (autoRun) {
      String report = state.process.getReport_json();
      if (report != null && !report.isEmpty()) {
        response.add("results", JsonParser.parseString(report));
      }
    }

    return GSON.toJson(response);
  }

  /**
   * Returns the current state of a session.
   *
   * @param input JSON with sessionId
   * @return JSON with session metadata and equipment list
   */
  private static String getSessionState(JsonObject input) {
    SessionState state = getValidSession(input);
    if (state == null) {
      return errorJson("SESSION_NOT_FOUND", "Session not found or expired",
          "Create a new session with action: 'create'");
    }

    state.lastAccess = System.currentTimeMillis();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("name", state.name);
    response.addProperty("hasRun", state.hasRun);
    response.addProperty("runCount", state.runCount);
    response.addProperty("equipmentCount", state.process != null ? state.process.size() : 0);
    response.addProperty("ageSeconds", (System.currentTimeMillis() - state.createdAt) / 1000);

    if (state.process != null) {
      response.add("equipment", getEquipmentList(state.process));

      // List available variables via automation
      try {
        neqsim.process.automation.ProcessAutomation auto = state.process.getAutomation();
        List<String> units = auto.getUnitList();
        JsonObject variables = new JsonObject();
        for (String unitName : units) {
          JsonArray vars = new JsonArray();
          for (Object sv : auto.getVariableList(unitName)) {
            vars.add(sv.toString());
          }
          variables.add(unitName, vars);
        }
        response.add("variables", variables);
      } catch (Exception e) {
        response.addProperty("variablesError", e.getMessage());
      }
    }

    return GSON.toJson(response);
  }

  /**
   * Lists all active sessions.
   *
   * @return JSON with session summaries
   */
  private static String listSessions() {
    evictExpiredSessions();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("count", SESSIONS.size());
    response.addProperty("maxSessions", MAX_SESSIONS);

    JsonArray sessions = new JsonArray();
    for (Map.Entry<String, SessionState> entry : SESSIONS.entrySet()) {
      SessionState s = entry.getValue();
      JsonObject info = new JsonObject();
      info.addProperty("sessionId", s.sessionId);
      info.addProperty("name", s.name);
      info.addProperty("equipmentCount", s.process != null ? s.process.size() : 0);
      info.addProperty("runCount", s.runCount);
      info.addProperty("ageSeconds", (System.currentTimeMillis() - s.createdAt) / 1000);
      sessions.add(info);
    }
    response.add("sessions", sessions);
    return GSON.toJson(response);
  }

  /**
   * Closes and removes a session.
   *
   * @param input JSON with sessionId
   * @return JSON confirmation
   */
  private static String closeSession(JsonObject input) {
    String sessionId = input.has("sessionId") ? input.get("sessionId").getAsString() : "";
    SessionState removed = SESSIONS.remove(sessionId);

    JsonObject response = new JsonObject();
    if (removed != null) {
      response.addProperty("status", "success");
      response.addProperty("message", "Session " + sessionId + " closed");
    } else {
      response.addProperty("status", "success");
      response.addProperty("message", "Session " + sessionId + " not found (may have expired)");
    }
    return GSON.toJson(response);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Builds the full process JSON from session state.
   *
   * @param state the session state
   * @return the full JSON string for ProcessSystem.fromJsonAndRun
   */
  private static String buildFullProcessJson(SessionState state) {
    JsonObject fullJson = new JsonObject();

    // Add fluid definition
    if (state.fluidJson != null) {
      fullJson.add("fluid", JsonParser.parseString(state.fluidJson));
    }

    // Add all equipment
    JsonArray process = new JsonArray();
    for (JsonObject eq : state.equipmentDefs) {
      process.add(eq);
    }
    fullJson.add("process", process);

    return GSON.toJson(fullJson);
  }

  /**
   * Gets the equipment list from a ProcessSystem.
   *
   * @param process the process system
   * @return JSON array of equipment names and types
   */
  private static JsonArray getEquipmentList(ProcessSystem process) {
    JsonArray list = new JsonArray();
    List<ProcessEquipmentInterface> units = process.getUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface eq = units.get(i);
      if (eq != null) {
        JsonObject info = new JsonObject();
        info.addProperty("name", eq.getName());
        info.addProperty("type", eq.getClass().getSimpleName());
        list.add(info);
      }
    }
    return list;
  }

  /**
   * Retrieves and validates a session from the input JSON.
   *
   * @param input the JSON input containing sessionId
   * @return the session state, or null if not found/expired
   */
  private static SessionState getValidSession(JsonObject input) {
    String sessionId = input.has("sessionId") ? input.get("sessionId").getAsString() : "";
    SessionState state = SESSIONS.get(sessionId);
    if (state == null) {
      return null;
    }
    if (System.currentTimeMillis() - state.lastAccess > SESSION_TTL_MS) {
      SESSIONS.remove(sessionId);
      return null;
    }
    return state;
  }

  /**
   * Removes sessions that have exceeded the TTL.
   */
  private static void evictExpiredSessions() {
    long now = System.currentTimeMillis();
    List<String> toRemove = new ArrayList<String>();
    for (Map.Entry<String, SessionState> entry : SESSIONS.entrySet()) {
      if (now - entry.getValue().lastAccess > SESSION_TTL_MS) {
        toRemove.add(entry.getKey());
      }
    }
    for (String key : toRemove) {
      SESSIONS.remove(key);
    }
  }

  /**
   * Creates a standard error JSON string.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return the error JSON string
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
  // Session state (package-private for testability)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Internal session state holder.
   */
  static class SessionState {
    /** Unique session ID. */
    final String sessionId;

    /** Human-readable session name. */
    final String name;

    /** The live process system. */
    ProcessSystem process;

    /** The fluid JSON for rebuilding. */
    String fluidJson;

    /** Equipment definitions added incrementally. */
    final List<JsonObject> equipmentDefs = new ArrayList<JsonObject>();

    /** Whether the process has been run at least once. */
    boolean hasRun = false;

    /** Number of times run() has been called. */
    int runCount = 0;

    /** Creation timestamp. */
    final long createdAt = System.currentTimeMillis();

    /** Last access timestamp. */
    long lastAccess = System.currentTimeMillis();

    /**
     * Creates a new session state.
     *
     * @param sessionId the session ID
     * @param name the session name
     */
    SessionState(String sessionId, String name) {
      this.sessionId = sessionId;
      this.name = name;
    }
  }
}
