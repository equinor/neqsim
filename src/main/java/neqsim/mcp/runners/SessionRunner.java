package neqsim.mcp.runners;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Stateful session runner that maintains live ProcessSystem instances across multiple MCP calls.
 *
 * <p>
 * Enables incremental flowsheet construction: create a session with a fluid, add equipment one-by-one, modify
 * parameters, and re-run without re-sending the entire JSON each time. Sessions are stored in-memory with configurable
 * TTL and max session count.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SessionRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Max sessions to prevent unbounded memory growth. */
  private static final int MAX_SESSIONS = 50;

  /** Cryptographically strong random source for session identifiers. */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** Default owner used for backwards-compatible anonymous sessions. */
  private static final String ANONYMOUS_OWNER = "anonymous";

  /** Session TTL in milliseconds (30 minutes). */
  private static final long SESSION_TTL_MS = 30L * 60L * 1000L;

  /** Active sessions keyed by session ID. */
  private static final ConcurrentHashMap<String, SessionState> SESSIONS = new ConcurrentHashMap<String, SessionState>();

  /**
   * Private constructor — all methods are static.
   */
  private SessionRunner() {
  }

  /**
   * Routes a session command based on the "action" field in the JSON.
   *
   * @param json the JSON command
   * @return JSON result string
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("SESSION_ERROR", "JSON input is null or empty", "Provide a JSON object with 'action' field");
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
      case "evaluate":
	return evaluateSession(input);
      case "getValues":
	return getValuesSession(input);
      case "setValues":
	return setValuesSession(input);
      case "adjustables":
	return adjustablesSession(input);
      case "getState":
	return getSessionState(input);
      case "list":
	return listSessions(input);
      case "close":
	return closeSession(input);
      default:
	return errorJson("UNKNOWN_ACTION", "Unknown session action: " + action,
	    "Use: create, addEquipment, run, modify, evaluate, getValues, setValues, "
		+ "adjustables, getState, list, close");
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

    String sessionId = generateSessionId();
    String name = input.has("name") ? input.get("name").getAsString() : "Session-" + sessionId;
    String ownerId = getOwnerId(input);

    // Build process from fluid definition or from full process JSON
    SessionState state = new SessionState(sessionId, name, ownerId);

    if (input.has("processJson")) {
      // Create from full process JSON (like run_process)
      String processJson = GSON.toJson(input.get("processJson"));
      SimulationResult result = ProcessSystem.fromJsonAndRun(processJson);
      if (result.isError()) {
	String errMsg = result.getErrors().isEmpty() ? "unknown error" : result.getErrors().get(0).getMessage();
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
    response.addProperty("ownerId", ownerId);
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
      String errMsg = result.getErrors().isEmpty() ? "unknown error" : result.getErrors().get(0).getMessage();
      return errorJson("EQUIPMENT_ADD_ERROR", "Failed to add equipment: " + errMsg,
	  "Check equipment type, name, inlet reference, and properties");
    }

    state.process = result.getProcessSystem();
    state.hasRun = true;
    state.lastAccess = System.currentTimeMillis();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("ownerId", state.ownerId);
    response.addProperty("equipmentCount", state.process.size());
    response.addProperty("added", equipment.has("name") ? equipment.get("name").getAsString() : "unnamed");
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
      String errMsg = result.getErrors().isEmpty() ? "unknown error" : result.getErrors().get(0).getMessage();
      return errorJson("RUN_ERROR", "Simulation failed: " + errMsg, "Check equipment connections and parameter values");
    }

    state.process = result.getProcessSystem();
    state.hasRun = true;
    state.runCount++;
    state.lastAccess = System.currentTimeMillis();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("ownerId", state.ownerId);
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
      String errMsg = buildResult.getErrors().isEmpty() ? "unknown error" : buildResult.getErrors().get(0).getMessage();
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
    response.addProperty("ownerId", state.ownerId);
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
   * Runs one closed-loop evaluation step against the cached, already-converged process in a session. Unlike
   * {@link #modifyParameter(JsonObject)}, this does NOT rebuild the flowsheet from JSON; it applies a batch of
   * setpoints in place and re-converges via
   * {@link neqsim.process.automation.ProcessAutomation#evaluate(Map, String, List, String, int, double)}, making it the
   * cheap atomic step for agent optimization loops.
   *
   * @param input JSON with sessionId, setpoints (object of address-&gt;value), optional readbacks (array of addresses),
   *              optional setpointUnit, readbackUnit, maxIterations, tolerance
   * @return JSON envelope containing the schema-versioned evaluation result (including the feasible flag, rejected
   *         setpoints, and read-back errors)
   */
  private static String evaluateSession(JsonObject input) {
    SessionState state = getValidSession(input);
    if (state == null) {
      return errorJson("SESSION_NOT_FOUND", "Session not found or expired",
	  "Create a new session with action: 'create'");
    }
    if (state.process == null) {
      return errorJson("NO_PROCESS", "No process in session", "Add equipment first");
    }

    Map<String, Double> setpoints = parseDoubleMap(input, "setpoints");
    List<String> readbacks = parseStringList(input, "readbacks");
    String spUnit = optString(input, "setpointUnit");
    String rbUnit = optString(input, "readbackUnit");
    int maxIter = input.has("maxIterations") ? input.get("maxIterations").getAsInt() : 30;
    double tol = input.has("tolerance") ? input.get("tolerance").getAsDouble() : 5.0e-3;

    String evalJson;
    try {
      neqsim.process.automation.ProcessAutomation auto = state.process.getAutomation();
      evalJson = auto.evaluate(setpoints, spUnit, readbacks, rbUnit, maxIter, tol);
    } catch (RuntimeException ex) {
      return errorJson("EVALUATE_ERROR", "evaluate failed: " + ex.getMessage(),
	  "Ensure maxIterations >= 1 and tolerance is a finite positive number.");
    }

    state.runCount++;
    state.hasRun = true;
    state.lastAccess = System.currentTimeMillis();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("ownerId", state.ownerId);
    response.addProperty("runCount", state.runCount);
    response.add("evaluation", JsonParser.parseString(evalJson));
    return GSON.toJson(response);
  }

  /**
   * Reads a batch of variable values from the cached session process without rebuilding or running.
   *
   * @param input JSON with sessionId, addresses (array of dot-notation addresses), optional unit
   * @return JSON envelope mapping each successfully resolved address to its value
   */
  private static String getValuesSession(JsonObject input) {
    SessionState state = getValidSession(input);
    if (state == null) {
      return errorJson("SESSION_NOT_FOUND", "Session not found or expired",
	  "Create a new session with action: 'create'");
    }
    if (state.process == null) {
      return errorJson("NO_PROCESS", "No process in session", "Add equipment first");
    }

    List<String> addresses = parseStringList(input, "addresses");
    String unit = optString(input, "unit");
    neqsim.process.automation.ProcessAutomation auto = state.process.getAutomation();
    Map<String, Double> values = auto.getValues(addresses, unit);
    state.lastAccess = System.currentTimeMillis();

    JsonObject valuesObj = new JsonObject();
    for (Map.Entry<String, Double> e : values.entrySet()) {
      valuesObj.addProperty(e.getKey(), e.getValue());
    }
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("requested", addresses.size());
    response.addProperty("resolved", values.size());
    response.add("values", valuesObj);
    return GSON.toJson(response);
  }

  /**
   * Writes a batch of input variables to the cached session process, optionally re-running once.
   *
   * @param input JSON with sessionId, updates (object of address-&gt;value), optional unit, optional runAfter (default
   *              true)
   * @return JSON envelope with the count of variables set and whether the process was re-run
   */
  private static String setValuesSession(JsonObject input) {
    SessionState state = getValidSession(input);
    if (state == null) {
      return errorJson("SESSION_NOT_FOUND", "Session not found or expired",
	  "Create a new session with action: 'create'");
    }
    if (state.process == null) {
      return errorJson("NO_PROCESS", "No process in session", "Add equipment first");
    }

    Map<String, Double> updates = parseDoubleMap(input, "updates");
    String unit = optString(input, "unit");
    boolean runAfter = !input.has("runAfter") || input.get("runAfter").getAsBoolean();

    neqsim.process.automation.ProcessAutomation auto = state.process.getAutomation();
    int set = auto.setValues(updates, unit, runAfter);
    if (runAfter) {
      state.hasRun = true;
      state.runCount++;
    }
    state.lastAccess = System.currentTimeMillis();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.addProperty("requested", updates.size());
    response.addProperty("set", set);
    response.addProperty("reRan", runAfter);
    if (runAfter) {
      String report = state.process.getReport_json();
      if (report != null && !report.isEmpty()) {
	response.add("results", JsonParser.parseString(report));
      }
    }
    return GSON.toJson(response);
  }

  /**
   * Enumerates the bounded decision space (adjustable parameters and adjusters) for the cached session process so an
   * agent can discover what it may perturb.
   *
   * @param input JSON with sessionId
   * @return JSON envelope with the adjustable-parameter registry
   */
  private static String adjustablesSession(JsonObject input) {
    SessionState state = getValidSession(input);
    if (state == null) {
      return errorJson("SESSION_NOT_FOUND", "Session not found or expired",
	  "Create a new session with action: 'create'");
    }
    if (state.process == null) {
      return errorJson("NO_PROCESS", "No process in session", "Add equipment first");
    }

    neqsim.process.automation.ProcessAutomation auto = state.process.getAutomation();
    String json = auto.getAdjustableParametersJson();
    state.lastAccess = System.currentTimeMillis();

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("sessionId", state.sessionId);
    response.add("adjustableParameters", JsonParser.parseString(json));
    return GSON.toJson(response);
  }

  /**
   * Parses a nested JSON object of address-&gt;number into an ordered map.
   *
   * @param input the parent JSON object
   * @param key   the field name holding the address-&gt;value object
   * @return an ordered map (never null; empty if the field is absent)
   */
  private static Map<String, Double> parseDoubleMap(JsonObject input, String key) {
    Map<String, Double> out = new LinkedHashMap<String, Double>();
    if (input.has(key) && input.get(key).isJsonObject()) {
      JsonObject obj = input.getAsJsonObject(key);
      for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
	out.put(e.getKey(), e.getValue().getAsDouble());
      }
    }
    return out;
  }

  /**
   * Parses a JSON array field into a list of strings.
   *
   * @param input the parent JSON object
   * @param key   the field name holding the array
   * @return a list of strings (never null; empty if the field is absent)
   */
  private static List<String> parseStringList(JsonObject input, String key) {
    List<String> out = new ArrayList<String>();
    if (input.has(key) && input.get(key).isJsonArray()) {
      com.google.gson.JsonArray arr = input.getAsJsonArray(key);
      for (int i = 0; i < arr.size(); i++) {
	out.add(arr.get(i).getAsString());
      }
    }
    return out;
  }

  /**
   * Returns a trimmed string field, or {@code null} when the field is absent or blank.
   *
   * @param input the parent JSON object
   * @param key   the field name
   * @return the string value, or {@code null} for absent/blank
   */
  private static String optString(JsonObject input, String key) {
    if (!input.has(key) || input.get(key).isJsonNull()) {
      return null;
    }
    String v = input.get(key).getAsString();
    return v.trim().isEmpty() ? null : v;
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
    response.addProperty("ownerId", state.ownerId);
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
   * @param input JSON request object; optional {@code ownerId} filters by owner
   * @return JSON with session summaries
   */
  private static String listSessions(JsonObject input) {
    evictExpiredSessions();
    String ownerId = input.has("ownerId") ? input.get("ownerId").getAsString() : "";

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("count", SESSIONS.size());
    response.addProperty("maxSessions", MAX_SESSIONS);

    JsonArray sessions = new JsonArray();
    for (Map.Entry<String, SessionState> entry : SESSIONS.entrySet()) {
      SessionState s = entry.getValue();
      if (!ownerId.isEmpty() && !ownerId.equals(s.ownerId)) {
	continue;
      }
      JsonObject info = new JsonObject();
      info.addProperty("sessionId", s.sessionId);
      info.addProperty("name", s.name);
      info.addProperty("ownerId", s.ownerId);
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
    SessionState state = getValidSession(input);
    SessionState removed = state != null ? SESSIONS.remove(sessionId) : null;

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
    String requestedOwner = input.has("ownerId") ? input.get("ownerId").getAsString() : "";
    if (!ANONYMOUS_OWNER.equals(state.ownerId) && !state.ownerId.equals(requestedOwner)) {
      return null;
    }
    return state;
  }

  /**
   * Generates a cryptographically strong URL-safe session ID.
   *
   * @return random session identifier
   */
  private static String generateSessionId() {
    byte[] bytes = new byte[24];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Reads the optional owner ID from an input request.
   *
   * @param input JSON request
   * @return normalized owner ID, or anonymous for backwards compatibility
   */
  private static String getOwnerId(JsonObject input) {
    if (!input.has("ownerId")) {
      return ANONYMOUS_OWNER;
    }
    String ownerId = input.get("ownerId").getAsString().trim();
    return ownerId.isEmpty() ? ANONYMOUS_OWNER : ownerId;
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
   * @param code        the error code
   * @param message     the error message
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

    /** Optional owner identifier for client-side session isolation. */
    final String ownerId;

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
     * @param name      the session name
     * @param ownerId   the owner identifier
     */
    SessionState(String sessionId, String name, String ownerId) {
      this.sessionId = sessionId;
      this.name = name;
      this.ownerId = ownerId;
    }
  }
}
