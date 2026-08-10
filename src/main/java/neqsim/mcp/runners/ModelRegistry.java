package neqsim.mcp.runners;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Versioned registry of process-model definitions addressed by a stable handle.
 *
 * <p>
 * Every tool that accepts a process definition previously required the full flowsheet JSON on every call, so a chat
 * session re-sent and re-parsed the same multi-kilobyte model for each question. Registering a model once returns a
 * {@code modelId} that can be supplied wherever {@code processJson} is expected, which keeps a conversation anchored to
 * one model, removes the re-transmission cost, and gives results a revision to cite.
 * </p>
 *
 * <p>
 * Models are scoped to the owner and the tenant of the authenticated principal: a handle is resolvable only by the
 * principal that registered it. A directory tenant is shared by every user of one organisation, so tenant alone is not
 * an isolation boundary — ownership is enforced on every read, revision, deletion, listing and internal resolution. A
 * handle owned by another principal is reported as unknown rather than as forbidden, so the registry cannot be used to
 * enumerate other users' models. Storage is in-memory: this is a working set for a running server, not a document
 * archive.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ModelRegistry {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Prefix identifying a model handle. */
  public static final String MODEL_ID_PREFIX = "model_";

  /** Max registered models per tenant, guarding against unbounded memory growth. */
  private static final int MAX_MODELS_PER_TENANT = 100;

  /** Separator joining tenant, owner and handle into a storage key; cannot occur in any of the three. */
  private static final char SCOPE_SEPARATOR = '\0';

  /** Registered models keyed by the scope-qualified model id. */
  private static final ConcurrentHashMap<String, ModelRecord> MODELS = new ConcurrentHashMap<String, ModelRecord>();

  /** Solved process systems keyed by the scope-qualified model id, so repeated reads do not re-solve. */
  private static final ConcurrentHashMap<String, SolvedModel> SOLVED = new ConcurrentHashMap<String, SolvedModel>();

  /** Max solved models held in memory; a solved flowsheet is far heavier than its definition. */
  private static final int MAX_SOLVED_MODELS = 8;

  /**
   * Private constructor — all methods are static.
   */
  private ModelRegistry() {
  }

  /**
   * Routes a model-management command based on the {@code action} field.
   *
   * @param json JSON command with an action and action-specific fields
   * @return JSON response
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("MODEL_ERROR", "JSON input is null or empty",
          "Provide a JSON object with an 'action' field: register, revise, get, list, inspect, delete");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "";

      if ("register".equals(action)) {
        return register(input);
      } else if ("revise".equals(action)) {
        return revise(input);
      } else if ("get".equals(action)) {
        return get(input);
      } else if ("list".equals(action)) {
        return list();
      } else if ("inspect".equals(action)) {
        return inspect(input);
      } else if ("delete".equals(action)) {
        return delete(input);
      }
      return errorJson("UNKNOWN_ACTION", "Unknown model action: " + action,
          "Use: register, revise, get, list, inspect, delete");
    } catch (Exception e) {
      return errorJson("MODEL_ERROR", e.getMessage(), "Check the JSON format and the 'action' field");
    }
  }

  /**
   * Returns whether a string is a model handle rather than an inline process definition.
   *
   * @param value the candidate value
   * @return true when the value looks like a model handle
   */
  public static boolean isModelHandle(String value) {
    if (value == null) {
      return false;
    }
    String trimmed = value.trim();
    return trimmed.startsWith(MODEL_ID_PREFIX) && trimmed.indexOf('{') < 0;
  }

  /**
   * Resolves a process-definition argument that may be either inline JSON or a model handle.
   *
   * <p>
   * This lets every tool taking a process definition accept a registered model without changing its signature or
   * breaking callers that still send inline JSON.
   * </p>
   *
   * @param processJsonOrModelId inline process JSON, or a model handle returned by {@code register}
   * @return the process definition JSON
   * @throws IllegalArgumentException if the handle is unknown or not visible to the caller
   */
  public static String resolve(String processJsonOrModelId) {
    if (!isModelHandle(processJsonOrModelId)) {
      return processJsonOrModelId;
    }
    String modelId = processJsonOrModelId.trim();
    ModelRecord record = MODELS.get(scopeKey(modelId));
    if (!isVisibleTo(record)) {
      throw new IllegalArgumentException("Unknown model handle '" + modelId
          + "'. Register the model first with manageModel(action='register'), or pass the process JSON inline.");
    }
    record.lastAccess = System.currentTimeMillis();
    record.useCount++;
    return record.definition;
  }

  /**
   * Returns the solved process system for a model handle, solving once and reusing it afterwards.
   *
   * <p>
   * Every read-only automation tool previously rebuilt and re-solved the flowsheet from JSON, which costs seconds to
   * minutes on a plant-sized model and makes an interactive session unusable. The definition behind a handle is
   * immutable until {@code revise}, so a solve can be reused for reads; a revision or deletion drops it.
   * </p>
   *
   * @param modelId the model handle
   * @return the solved process system
   * @throws IllegalArgumentException if the handle is unknown or not visible to the caller
   * @throws IllegalStateException if the model fails to build or solve
   */
  public static ProcessSystem solvedProcess(String modelId) {
    String definition = resolve(modelId);
    String handle = modelId.trim();
    String key = scopeKey(handle);
    ModelRecord record = MODELS.get(key);
    int revision = record != null ? record.revision : 0;

    SolvedModel cached = SOLVED.get(key);
    if (cached != null && cached.revision == revision) {
      cached.lastAccess = System.currentTimeMillis();
      cached.hits++;
      return cached.process;
    }

    SimulationResult result = ProcessSystem.fromJsonAndRun(definition);
    if (result.isError() || result.getProcessSystem() == null) {
      throw new IllegalStateException("Model '" + handle + "' failed to solve: " + result.getErrors());
    }

    SolvedModel solved = new SolvedModel();
    solved.process = result.getProcessSystem();
    solved.revision = revision;
    solved.lastAccess = System.currentTimeMillis();
    SOLVED.put(key, solved);
    evictSolvedModels();
    return solved.process;
  }

  /**
   * Returns how many reads were served from a cached solve.
   *
   * @param modelId the model handle
   * @return reuse count, or 0 when nothing is cached
   */
  public static int solvedHits(String modelId) {
    SolvedModel cached = modelId != null ? SOLVED.get(scopeKey(modelId.trim())) : null;
    return cached != null ? cached.hits : 0;
  }

  /**
   * Drops the cached solve for a model.
   *
   * @param modelId the model handle
   */
  public static void invalidateSolved(String modelId) {
    if (modelId != null) {
      SOLVED.remove(scopeKey(modelId.trim()));
    }
  }

  /**
   * Removes least recently used solved models beyond the retention limit.
   */
  private static void evictSolvedModels() {
    while (SOLVED.size() > MAX_SOLVED_MODELS) {
      String oldestKey = null;
      long oldestAccess = Long.MAX_VALUE;
      for (java.util.Map.Entry<String, SolvedModel> entry : SOLVED.entrySet()) {
        if (entry.getValue().lastAccess < oldestAccess) {
          oldestAccess = entry.getValue().lastAccess;
          oldestKey = entry.getKey();
        }
      }
      if (oldestKey == null) {
        return;
      }
      SOLVED.remove(oldestKey);
    }
  }

  /**
   * Registers a new model definition and returns its handle.
   *
   * @param input JSON with processJson, and optional name and version
   * @return JSON with the assigned model id and revision
   */
  private static String register(JsonObject input) {
    String definition = extractDefinition(input);
    if (definition == null) {
      return errorJson("MISSING_DEFINITION", "No process definition supplied",
          "Provide 'processJson' as a JSON string or as a nested JSON object");
    }
    String validationError = validateDefinition(definition);
    if (validationError != null) {
      return errorJson("INVALID_DEFINITION", validationError,
          "Run validateInput on the definition, or use getExample(category='process') for a template");
    }

    String tenant = currentTenant();
    if (countForTenant(tenant) >= MAX_MODELS_PER_TENANT) {
      return errorJson("MODEL_LIMIT", "Maximum registered model count reached (" + MAX_MODELS_PER_TENANT + ")",
          "Delete unused models with manageModel(action='delete')");
    }

    ModelRecord record = new ModelRecord();
    record.modelId = MODEL_ID_PREFIX + contentHash(definition);
    record.name = optionalString(input, "name", "unnamed-model");
    record.version = optionalString(input, "version", "1.0.0");
    record.definition = definition;
    record.tenant = tenant;
    record.owner = currentOwner();
    record.revision = 1;
    record.createdAt = Instant.now().toString();
    record.lastAccess = System.currentTimeMillis();

    ModelRecord existing = MODELS.get(scopeKey(record.modelId));
    if (isVisibleTo(existing)) {
      // Identical content from the same principal is the same model; return the existing handle.
      return modelResponse("register", existing, "Model already registered with identical content");
    }
    MODELS.put(scopeKey(record.modelId), record);
    return modelResponse("register", record, "Model registered");
  }

  /**
   * Stores a new revision of an existing model under the same handle.
   *
   * @param input JSON with modelId and the updated processJson
   * @return JSON with the new revision number
   */
  private static String revise(JsonObject input) {
    ModelRecord record = lookup(input);
    if (record == null) {
      return unknownModelError(input);
    }
    String definition = extractDefinition(input);
    if (definition == null) {
      return errorJson("MISSING_DEFINITION", "No updated process definition supplied",
          "Provide 'processJson' with the revised definition");
    }
    String validationError = validateDefinition(definition);
    if (validationError != null) {
      return errorJson("INVALID_DEFINITION", validationError, "Run validateInput on the revised definition");
    }
    record.definition = definition;
    record.revision++;
    record.version = optionalString(input, "version", record.version);
    record.lastAccess = System.currentTimeMillis();
    invalidateSolved(record.modelId);
    return modelResponse("revise", record, "Model revised to revision " + record.revision);
  }

  /**
   * Returns the stored definition for a model handle.
   *
   * @param input JSON with modelId
   * @return JSON with the model definition
   */
  private static String get(JsonObject input) {
    ModelRecord record = lookup(input);
    if (record == null) {
      return unknownModelError(input);
    }
    record.lastAccess = System.currentTimeMillis();
    JsonObject response = modelJson(record);
    response.add("definition", JsonParser.parseString(record.definition));
    return envelope("get", response, "Model definition returned");
  }

  /**
   * Lists the models visible to the caller.
   *
   * @return JSON array of model metadata
   */
  private static String list() {
    String tenant = currentTenant();
    List<ModelRecord> visible = new ArrayList<ModelRecord>();
    for (ModelRecord record : MODELS.values()) {
      if (isVisibleTo(record)) {
        visible.add(record);
      }
    }
    Collections.sort(visible, new Comparator<ModelRecord>() {
      @Override
      public int compare(ModelRecord left, ModelRecord right) {
        return Long.compare(right.lastAccess, left.lastAccess);
      }
    });

    JsonArray models = new JsonArray();
    for (ModelRecord record : visible) {
      models.add(modelJson(record));
    }
    JsonObject response = new JsonObject();
    response.add("models", models);
    response.addProperty("count", models.size());
    response.addProperty("tenant", tenant);
    response.addProperty("owner", currentOwner());
    return envelope("list", response, "Registered models listed");
  }

  /**
   * Returns structural metadata about a registered model without running it.
   *
   * @param input JSON with modelId
   * @return JSON with equipment names and types
   */
  private static String inspect(JsonObject input) {
    ModelRecord record = lookup(input);
    if (record == null) {
      return unknownModelError(input);
    }
    record.lastAccess = System.currentTimeMillis();

    JsonObject response = modelJson(record);
    JsonObject definition = JsonParser.parseString(record.definition).getAsJsonObject();
    JsonArray equipment = new JsonArray();
    JsonArray areas = new JsonArray();

    if (definition.has("areas") && definition.get("areas").isJsonObject()) {
      for (String areaName : definition.getAsJsonObject("areas").keySet()) {
        areas.add(areaName);
      }
    }
    for (JsonObject unit : equipmentEntries(definition)) {
      JsonObject entry = new JsonObject();
      entry.addProperty("name", unit.has("name") ? unit.get("name").getAsString() : "unnamed");
      entry.addProperty("type", unit.has("type") ? unit.get("type").getAsString() : "unknown");
      equipment.add(entry);
    }

    response.add("areas", areas);
    response.add("equipment", equipment);
    response.addProperty("equipmentCount", equipment.size());
    response.addProperty("multiArea", areas.size() > 0);
    return envelope("inspect", response, "Model structure inspected");
  }

  /**
   * Removes a model from the registry.
   *
   * @param input JSON with modelId
   * @return JSON confirmation
   */
  private static String delete(JsonObject input) {
    ModelRecord record = lookup(input);
    if (record == null) {
      return unknownModelError(input);
    }
    MODELS.remove(scopeKey(record.modelId));
    invalidateSolved(record.modelId);
    JsonObject response = new JsonObject();
    response.addProperty("modelId", record.modelId);
    response.addProperty("deleted", true);
    return envelope("delete", response, "Model deleted");
  }

  /**
   * Collects equipment entries from either a single-area or multi-area definition.
   *
   * @param definition the parsed process definition
   * @return list of equipment objects
   */
  private static List<JsonObject> equipmentEntries(JsonObject definition) {
    List<JsonObject> units = new ArrayList<JsonObject>();
    collectEquipment(definition, units);
    if (definition.has("areas") && definition.get("areas").isJsonObject()) {
      JsonObject areas = definition.getAsJsonObject("areas");
      for (String areaName : areas.keySet()) {
        if (areas.get(areaName).isJsonObject()) {
          collectEquipment(areas.getAsJsonObject(areaName), units);
        }
      }
    }
    return units;
  }

  /**
   * Adds equipment entries found directly on a process object.
   *
   * @param source object that may carry a process array or a process.equipment array
   * @param units accumulator
   */
  private static void collectEquipment(JsonObject source, List<JsonObject> units) {
    if (!source.has("process")) {
      return;
    }
    JsonArray array = null;
    if (source.get("process").isJsonArray()) {
      array = source.getAsJsonArray("process");
    } else if (source.get("process").isJsonObject() && source.getAsJsonObject("process").has("equipment")) {
      array = source.getAsJsonObject("process").getAsJsonArray("equipment");
    }
    if (array == null) {
      return;
    }
    for (int i = 0; i < array.size(); i++) {
      if (array.get(i).isJsonObject()) {
        units.add(array.get(i).getAsJsonObject());
      }
    }
  }

  /**
   * Reads the process definition from either a JSON string field or a nested JSON object.
   *
   * <p>
   * Accepting a nested object lets a client send a structured model instead of a JSON string escaped inside a string,
   * which is the form an LLM produces most reliably.
   * </p>
   *
   * @param input the request object
   * @return the definition as a JSON string, or null when absent
   */
  private static String extractDefinition(JsonObject input) {
    String[] fields = { "processJson", "definition", "model" };
    for (String field : fields) {
      if (!input.has(field) || input.get(field).isJsonNull()) {
        continue;
      }
      if (input.get(field).isJsonObject()) {
        return GSON.toJson(input.get(field));
      }
      String value = input.get(field).getAsString();
      if (value != null && !value.trim().isEmpty()) {
        return value;
      }
    }
    return null;
  }

  /**
   * Checks that a definition parses and carries the minimum structure of a process model.
   *
   * @param definition the candidate definition
   * @return null when acceptable, otherwise a description of the problem
   */
  private static String validateDefinition(String definition) {
    JsonObject parsed;
    try {
      parsed = JsonParser.parseString(definition).getAsJsonObject();
    } catch (Exception e) {
      return "Process definition is not a JSON object: " + e.getMessage();
    }
    if (parsed.has("areas") || parsed.has("process")) {
      return null;
    }
    return "Process definition must contain a 'process' array or an 'areas' object";
  }

  /**
   * Looks up a model referenced by the request, honouring owner and tenant isolation.
   *
   * @param input the request object
   * @return the model record, or null when absent or not visible
   */
  private static ModelRecord lookup(JsonObject input) {
    if (!input.has("modelId")) {
      return null;
    }
    ModelRecord record = MODELS.get(scopeKey(input.get("modelId").getAsString().trim()));
    return isVisibleTo(record) ? record : null;
  }

  /**
   * Builds the standard error for an unresolvable model reference.
   *
   * @param input the request object
   * @return JSON error response
   */
  private static String unknownModelError(JsonObject input) {
    String requested = input.has("modelId") ? input.get("modelId").getAsString() : "(none)";
    return errorJson("UNKNOWN_MODEL", "No registered model with id '" + requested + "' is visible to this caller",
        "Call manageModel(action='list') to see available models, or register the model first");
  }

  /**
   * Returns the tenant scope of the current caller.
   *
   * @return the tenant identifier
   */
  private static String currentTenant() {
    return McpRequestContext.current().getTenant();
  }

  /**
   * Returns the owner scope of the current caller.
   *
   * <p>
   * The subject is taken from the identity the transport already validated, never from a tool argument, so a client
   * cannot claim another principal. Callers the transport did not authenticate share the anonymous subject, which keeps
   * single-user desktop use working while still denying them every model registered by a named principal.
   * </p>
   *
   * @return the owner identifier
   */
  private static String currentOwner() {
    return McpRequestContext.currentSubject();
  }

  /**
   * Returns whether a record belongs to the current caller.
   *
   * <p>
   * One directory tenant covers every user of an organisation, so a tenant check alone leaves all of them in one
   * namespace; ownership is the check that keeps one engineer's flowsheet out of another engineer's session.
   * </p>
   *
   * @param record the model record, may be null
   * @return true when the record exists and is owned by the caller within the caller's tenant
   */
  private static boolean isVisibleTo(ModelRecord record) {
    return record != null && currentTenant().equals(record.tenant) && currentOwner().equals(record.owner);
  }

  /**
   * Builds the storage key that scopes a handle to the caller that registered it.
   *
   * <p>
   * Handles are content-derived, so without a scoped key two principals registering the same definition would share one
   * entry: a revision by one would silently change what the other resolves, and the second registration would hand back
   * the first caller's record. Qualifying the key with tenant and owner keeps the entries independent while the handle
   * itself stays stable for its owner.
   * </p>
   *
   * @param modelId the model handle
   * @return the scope-qualified storage key
   */
  private static String scopeKey(String modelId) {
    return currentTenant() + SCOPE_SEPARATOR + currentOwner() + SCOPE_SEPARATOR + modelId;
  }

  /**
   * Counts models registered in a tenant.
   *
   * @param tenant the tenant identifier
   * @return number of registered models
   */
  private static int countForTenant(String tenant) {
    int count = 0;
    for (ModelRecord record : MODELS.values()) {
      if (record.tenant.equals(tenant)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Computes a short content hash so identical definitions map to the same handle.
   *
   * @param definition the process definition
   * @return a 16-character hexadecimal digest
   */
  private static String contentHash(String definition) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(definition.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < 8; i++) {
        hex.append(String.format("%02x", Byte.valueOf(bytes[i])));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for model handles", e);
    }
  }

  /**
   * Reads an optional string field.
   *
   * @param input the request object
   * @param field the field name
   * @param fallback value used when the field is absent or empty
   * @return the field value or the fallback
   */
  private static String optionalString(JsonObject input, String field, String fallback) {
    if (!input.has(field) || input.get(field).isJsonNull()) {
      return fallback;
    }
    String value = input.get(field).getAsString();
    return value != null && !value.trim().isEmpty() ? value : fallback;
  }

  /**
   * Builds the metadata block for a model.
   *
   * @param record the model record
   * @return JSON metadata object
   */
  private static JsonObject modelJson(ModelRecord record) {
    JsonObject model = new JsonObject();
    model.addProperty("modelId", record.modelId);
    model.addProperty("name", record.name);
    model.addProperty("version", record.version);
    model.addProperty("revision", record.revision);
    model.addProperty("tenant", record.tenant);
    model.addProperty("owner", record.owner);
    model.addProperty("createdAt", record.createdAt);
    model.addProperty("useCount", record.useCount);
    return model;
  }

  /**
   * Builds a success response carrying model metadata.
   *
   * @param action the action performed
   * @param record the model record
   * @param message human-readable summary
   * @return JSON response
   */
  private static String modelResponse(String action, ModelRecord record, String message) {
    JsonObject response = modelJson(record);
    response.addProperty("usage",
        "Pass '" + record.modelId + "' wherever a tool expects processJson to reuse this model without resending it");
    return envelope(action, response, message);
  }

  /**
   * Wraps a payload in the standard MCP response envelope.
   *
   * @param action the action performed
   * @param payload the response payload
   * @param message human-readable summary
   * @return JSON response
   */
  private static String envelope(String action, JsonObject payload, String message) {
    JsonObject response = payload.deepCopy();
    response.addProperty("status", "success");
    response.addProperty("action", action);
    response.addProperty("message", message);
    response.add("data", payload);
    ApiEnvelope.applyStandardFields(response, "manageModel", null,
        ApiEnvelope.validationStatus(true, "registry", message), ApiEnvelope.qualityGate("passed", message, false));
    return GSON.toJson(response);
  }

  /**
   * Builds a standard error response.
   *
   * @param code machine-readable error code
   * @param message human-readable message
   * @param remediation suggested fix
   * @return JSON error response
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("code", code);
    error.addProperty("message", message);
    error.addProperty("remediation", remediation);
    error.add("data", new JsonObject());
    ApiEnvelope.applyStandardFields(error, "manageModel", null,
        ApiEnvelope.validationStatus(false, "registry", message), ApiEnvelope.qualityGate("failed", message, true));
    return GSON.toJson(error);
  }

  /**
   * Clears the registry. Test-only hook: storage is process-wide static state.
   */
  static void resetForTests() {
    MODELS.clear();
    SOLVED.clear();
  }

  /**
   * A solved flowsheet retained for reuse by read-only tools.
   */
  private static final class SolvedModel {
    /** The solved process system. */
    private ProcessSystem process;

    /** Definition revision this solve corresponds to. */
    private int revision;

    /** Last access time in milliseconds. */
    private long lastAccess;

    /** Number of reads served from this cached solve. */
    private int hits;
  }

  /**
   * One registered model definition and its metadata.
   */
  private static final class ModelRecord {
    /** Stable content-derived handle. */
    private String modelId;

    /** Human-readable model name. */
    private String name;

    /** Caller-supplied version label. */
    private String version;

    /** Monotonic revision counter incremented on each revise. */
    private int revision;

    /** Process definition JSON. */
    private String definition;

    /** Tenant scope the model belongs to. */
    private String tenant;

    /** Subject that registered the model. */
    private String owner;

    /** Registration timestamp. */
    private String createdAt;

    /** Last access time in milliseconds. */
    private long lastAccess;

    /** Number of times the handle has been resolved by a tool. */
    private int useCount;
  }
}
