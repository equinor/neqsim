package neqsim.mcp.runners;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Security and multi-tenancy layer for the NeqSim MCP server.
 *
 * <p>
 * Provides:
 * <ul>
 * <li>API key-based authentication for production deployments</li>
 * <li>Per-user session isolation to prevent cross-contamination</li>
 * <li>Comprehensive audit logging of all simulation and data access operations</li>
 * <li>Rate limiting to protect compute resources from abuse</li>
 * <li>User/project context for multi-tenant usage</li>
 * </ul>
 *
 * <p>
 * This is an application-level security layer. In production, combine with transport-level security (TLS, OAuth2)
 * provided by the deployment platform.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SecurityRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Registered API keys (in production, these would come from a database or vault). */
  private static final ConcurrentHashMap<String, UserContext> API_KEYS = new ConcurrentHashMap<String, UserContext>();

  /** Audit log (in production, this would write to a persistent store). */
  private static final List<AuditEntry> AUDIT_LOG = Collections.synchronizedList(new ArrayList<AuditEntry>());

  /** Rate limiting: requests per key in the current time window. */
  private static final ConcurrentHashMap<String, RateState> RATE_LIMITS = new ConcurrentHashMap<String, RateState>();

  /** Default rate limit: requests per minute. */
  private static final int DEFAULT_RATE_LIMIT = 60;

  /** Rate window in milliseconds. */
  private static final long RATE_WINDOW_MS = 60000L;

  /** Max audit log entries kept in memory. */
  private static final int MAX_AUDIT_LOG_SIZE = 10000;

  /** Whether security enforcement is enabled. */
  private static volatile boolean enabled = false;

  /** Global request counter. */
  private static final AtomicLong REQUEST_COUNTER = new AtomicLong(0);

  /**
   * Tools that stay reachable without a credential so an operator can authenticate after security has been enabled.
   * Without this exemption, enabling enforcement locks every caller out of the server permanently — including the
   * management tool needed to disable it again.
   */
  private static final Set<String> BOOTSTRAP_TOOLS = Collections
      .unmodifiableSet(new HashSet<String>(Arrays.asList("manageSecurity")));

  /**
   * Security actions that expose or grant privilege. When enforcement is enabled these require the configured admin
   * token, otherwise any caller reaching the bootstrap-exempt manageSecurity tool could mint an admin key for itself.
   */
  private static final Set<String> PRIVILEGED_ACTIONS = Collections.unmodifiableSet(
      new HashSet<String>(Arrays.asList("createApiKey", "revokeApiKey", "setConfig", "getAuditLog", "getRateLimits")));

  /**
   * Private constructor — all methods are static.
   */
  private SecurityRunner() {
  }

  /**
   * Main entry point for security operations.
   *
   * @param json JSON with action and parameters
   * @return JSON with results
   */
  public static String run(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "";

      String privilegeDenied = checkPrivilegedAction(action, input);
      if (privilegeDenied != null) {
        return privilegeDenied;
      }

      switch (action) {
      case "createApiKey":
        return createApiKey(input);
      case "revokeApiKey":
        return revokeApiKey(input);
      case "authenticate":
        return authenticate(input);
      case "getAuditLog":
        return getAuditLog(input);
      case "getRateLimits":
        return getRateLimits();
      case "setConfig":
        return setConfig(input);
      case "getStatus":
        return getStatus();
      default:
        return errorJson("UNKNOWN_ACTION", "Unknown security action: " + action,
            "Use: createApiKey, revokeApiKey, authenticate, getAuditLog, " + "getRateLimits, setConfig, getStatus");
      }
    } catch (Exception e) {
      return errorJson("SECURITY_ERROR", e.getMessage(), "Check JSON format");
    }
  }

  /**
   * Checks authentication and rate limiting for an incoming request. Call this at the beginning of any protected tool
   * invocation.
   *
   * <p>
   * When no explicit key is supplied the credential bound by the transport layer through {@link McpRequestContext} is
   * used. Credentials are never accepted as MCP tool arguments.
   * </p>
   *
   * @param apiKey the API key, or null to resolve the credential from {@link McpRequestContext}
   * @param tool the tool being invoked
   * @return null if allowed, or an error JSON string if denied
   */
  public static String checkAccess(String apiKey, String tool) {
    REQUEST_COUNTER.incrementAndGet();

    McpRequestContext.Principal principal = McpRequestContext.current();
    String credential = apiKey != null && !apiKey.isEmpty() ? apiKey : principal.getCredential();
    String subject = principal.getSubject();

    if (!enabled) {
      // Log even when not enforcing
      logAudit(subject, tool, "allowed", "Security disabled");
      return null;
    }

    if (BOOTSTRAP_TOOLS.contains(tool)) {
      // Reachable without a credential so an operator can still authenticate or read status.
      // Privileged actions inside the tool are separately gated by checkPrivilegedAction.
      logAudit(subject, tool, "allowed", "Bootstrap tool");
      return null;
    }

    if (credential == null || credential.isEmpty()) {
      logAudit(subject, tool, "denied", "No authenticated principal");
      return errorJson("AUTH_REQUIRED", "Authenticated caller required",
          "Authenticate at the transport layer (API key header or OAuth bearer token). "
              + "Credentials are not accepted as tool arguments.");
    }

    UserContext user = API_KEYS.get(credential);
    if (user == null) {
      logAudit("unknown:" + credential.substring(0, Math.min(8, credential.length())), tool, "denied",
          "Invalid credential");
      return errorJson("AUTH_FAILED", "Invalid credential", "Check the API key or bearer token used by the transport");
    }

    // Check rate limit
    if (!checkRateLimit(credential, user.rateLimit)) {
      logAudit(user.userId, tool, "rate_limited", "Exceeded " + user.rateLimit + " requests/minute");
      return errorJson("RATE_LIMITED", "Rate limit exceeded: " + user.rateLimit + " requests/minute",
          "Wait and retry, or request a higher rate limit");
    }

    logAudit(user.userId, tool, "allowed", null);
    return null; // Access granted
  }

  /**
   * Resets all global security state. Test-only hook: the key store, audit log, rate-limit counters and the enforcement
   * flag are process-wide, so tests that enable enforcement must be able to restore a clean state deterministically.
   */
  static void resetForTests() {
    enabled = false;
    API_KEYS.clear();
    RATE_LIMITS.clear();
    AUDIT_LOG.clear();
  }

  /**
   * Gates privileged security actions behind the configured admin token while enforcement is on.
   *
   * <p>
   * Enforcement is only applied when {@link #enabled} is true. With security disabled the server is a single-user
   * desktop tool and the management actions stay open, which preserves local workflows and existing behaviour.
   * </p>
   *
   * @param action the requested security action
   * @param input the raw request object, which may carry an adminToken field
   * @return null when the action may proceed, otherwise an error JSON string
   */
  private static String checkPrivilegedAction(String action, JsonObject input) {
    if (!enabled || !PRIVILEGED_ACTIONS.contains(action)) {
      return null;
    }
    if (!IndustrialProfile.isAdminConfigured()) {
      logAudit(McpRequestContext.currentSubject(), "manageSecurity:" + action, "denied", "No admin token configured");
      return errorJson("ADMIN_NOT_CONFIGURED", "Security enforcement is enabled but no admin token is configured",
          "Set NEQSIM_MCP_ADMIN_TOKEN (or -Dneqsim.mcp.adminToken) on the server before enabling security");
    }
    String adminToken = input.has("adminToken") ? input.get("adminToken").getAsString() : null;
    if (!IndustrialProfile.isAdminAuthorized(adminToken)) {
      logAudit(McpRequestContext.currentSubject(), "manageSecurity:" + action, "denied", "Admin authorization failed");
      return errorJson("ADMIN_REQUIRED", "Action '" + action + "' requires administrator authorization",
          "Supply the configured admin token as 'adminToken'");
    }
    return null;
  }

  /**
   * Creates a new API key for a user/project.
   *
   * @param input JSON with user details
   * @return JSON with the new API key
   */
  private static String createApiKey(JsonObject input) {
    String userId = input.has("userId") ? input.get("userId").getAsString() : "";
    String project = input.has("project") ? input.get("project").getAsString() : "default";
    String role = input.has("role") ? input.get("role").getAsString() : "user";
    int rateLimit = input.has("rateLimit") ? input.get("rateLimit").getAsInt() : DEFAULT_RATE_LIMIT;

    if (userId.isEmpty()) {
      return errorJson("MISSING_USER", "userId is required", "Provide a userId field");
    }

    String apiKey = "neqsim_" + UUID.randomUUID().toString().replace("-", "");

    UserContext user = new UserContext();
    user.userId = userId;
    user.project = project;
    user.role = role;
    user.rateLimit = rateLimit;
    user.createdAt = Instant.now().toString();

    API_KEYS.put(apiKey, user);
    logAudit(userId, "createApiKey", "success", "Role: " + role + ", Project: " + project);

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("apiKey", apiKey);
    response.addProperty("userId", userId);
    response.addProperty("project", project);
    response.addProperty("role", role);
    response.addProperty("rateLimit", rateLimit);
    response.addProperty("note",
        "Store this API key securely. Include it as 'apiKey' in requests when security is enabled.");
    return GSON.toJson(response);
  }

  /**
   * Revokes an API key.
   *
   * @param input JSON with apiKey
   * @return JSON confirmation
   */
  private static String revokeApiKey(JsonObject input) {
    String apiKey = input.has("apiKey") ? input.get("apiKey").getAsString() : "";
    UserContext removed = API_KEYS.remove(apiKey);

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("revoked", removed != null);
    if (removed != null) {
      logAudit(removed.userId, "revokeApiKey", "success", null);
    }
    return GSON.toJson(response);
  }

  /**
   * Authenticates with an API key and returns user context.
   *
   * @param input JSON with apiKey
   * @return JSON with authentication result
   */
  private static String authenticate(JsonObject input) {
    String apiKey = input.has("apiKey") ? input.get("apiKey").getAsString() : "";
    if (apiKey.isEmpty()) {
      String bound = McpRequestContext.currentCredential();
      apiKey = bound != null ? bound : "";
    }

    if (!enabled) {
      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.addProperty("authenticated", true);
      response.addProperty("securityEnabled", false);
      response.addProperty("message", "Security is disabled — all requests are allowed");
      return GSON.toJson(response);
    }

    UserContext user = API_KEYS.get(apiKey);
    if (user == null) {
      return errorJson("AUTH_FAILED", "Invalid API key", "Check your API key or create one");
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("authenticated", true);
    response.addProperty("userId", user.userId);
    response.addProperty("project", user.project);
    response.addProperty("role", user.role);
    response.addProperty("rateLimit", user.rateLimit);
    return GSON.toJson(response);
  }

  /**
   * Returns recent audit log entries.
   *
   * @param input JSON with optional filters (userId, tool, limit)
   * @return JSON with audit entries
   */
  private static String getAuditLog(JsonObject input) {
    String filterUser = input.has("userId") ? input.get("userId").getAsString() : null;
    String filterTool = input.has("tool") ? input.get("tool").getAsString() : null;
    int limit = input.has("limit") ? input.get("limit").getAsInt() : 100;

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("totalEntries", AUDIT_LOG.size());

    JsonArray entries = new JsonArray();
    int count = 0;

    synchronized (AUDIT_LOG) {
      // Iterate in reverse to get newest first
      for (int i = AUDIT_LOG.size() - 1; i >= 0 && count < limit; i--) {
        AuditEntry entry = AUDIT_LOG.get(i);

        // Apply filters
        if (filterUser != null && !entry.userId.equals(filterUser)) {
          continue;
        }
        if (filterTool != null && !entry.tool.equals(filterTool)) {
          continue;
        }

        entries.add(entry.toJson());
        count++;
      }
    }

    response.add("entries", entries);
    response.addProperty("returnedCount", count);
    return GSON.toJson(response);
  }

  /**
   * Returns current rate limit status for all authenticated users.
   *
   * @return JSON with rate limit details
   */
  private static String getRateLimits() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("securityEnabled", enabled);
    response.addProperty("defaultRateLimit", DEFAULT_RATE_LIMIT);

    JsonArray users = new JsonArray();
    for (Map.Entry<String, UserContext> entry : API_KEYS.entrySet()) {
      JsonObject userInfo = new JsonObject();
      UserContext user = entry.getValue();
      userInfo.addProperty("userId", user.userId);
      userInfo.addProperty("project", user.project);
      userInfo.addProperty("rateLimit", user.rateLimit);

      RateState rate = RATE_LIMITS.get(entry.getKey());
      if (rate != null) {
        long remaining = Math.max(0, user.rateLimit - rate.getRequestCount(RATE_WINDOW_MS));
        userInfo.addProperty("remainingRequests", remaining);
      }
      users.add(userInfo);
    }
    response.add("users", users);
    return GSON.toJson(response);
  }

  /**
   * Configures security settings.
   *
   * @param input JSON with configuration
   * @return JSON confirmation
   */
  private static String setConfig(JsonObject input) {
    if (input.has("enabled")) {
      enabled = input.get("enabled").getAsBoolean();
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("securityEnabled", enabled);
    response.addProperty("apiKeyCount", API_KEYS.size());
    response.addProperty("auditLogSize", AUDIT_LOG.size());
    return GSON.toJson(response);
  }

  /**
   * Returns current security status.
   *
   * @return JSON with security status
   */
  private static String getStatus() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("securityEnabled", enabled);
    response.addProperty("apiKeyCount", API_KEYS.size());
    response.addProperty("auditLogSize", AUDIT_LOG.size());
    response.addProperty("totalRequests", REQUEST_COUNTER.get());
    response.addProperty("activeRateLimits", RATE_LIMITS.size());
    return GSON.toJson(response);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Checks rate limiting for a key.
   *
   * @param key the API key
   * @param limit the max requests per window
   * @return true if within limits
   */
  private static boolean checkRateLimit(String key, int limit) {
    RateState state = RATE_LIMITS.computeIfAbsent(key, k -> new RateState());
    return state.tryRequest(limit, RATE_WINDOW_MS);
  }

  /**
   * Logs an audit entry.
   *
   * @param userId the user ID
   * @param tool the tool invoked
   * @param result the result (allowed, denied, rate_limited)
   * @param details additional details
   */
  private static void logAudit(String userId, String tool, String result, String details) {
    AuditEntry entry = new AuditEntry();
    entry.timestamp = Instant.now().toString();
    entry.userId = userId;
    entry.tenant = McpRequestContext.current().getTenant();
    entry.tool = tool;
    entry.result = result;
    entry.details = details;
    entry.requestId = REQUEST_COUNTER.get();

    AUDIT_LOG.add(entry);

    // Trim log if too large
    while (AUDIT_LOG.size() > MAX_AUDIT_LOG_SIZE) {
      AUDIT_LOG.remove(0);
    }
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
   * User context associated with an API key.
   */
  static class UserContext {
    /** User identifier. */
    String userId = "";

    /** Project name. */
    String project = "default";

    /** Role: admin, engineer, viewer. */
    String role = "user";

    /** Rate limit in requests per minute. */
    int rateLimit = DEFAULT_RATE_LIMIT;

    /** Creation timestamp. */
    String createdAt = "";
  }

  /**
   * Rate limiting state for a user.
   */
  static class RateState {
    /** Timestamps of recent requests. */
    private final List<Long> requests = Collections.synchronizedList(new ArrayList<Long>());

    /**
     * Attempts to make a request within the rate limit.
     *
     * @param maxRequests max requests in the window
     * @param windowMs window duration in milliseconds
     * @return true if allowed
     */
    boolean tryRequest(int maxRequests, long windowMs) {
      long now = System.currentTimeMillis();
      long cutoff = now - windowMs;

      // Remove expired entries
      synchronized (requests) {
        while (!requests.isEmpty() && requests.get(0) < cutoff) {
          requests.remove(0);
        }

        if (requests.size() >= maxRequests) {
          return false;
        }

        requests.add(now);
        return true;
      }
    }

    /**
     * Gets the current request count in the window.
     *
     * @param windowMs window in ms
     * @return request count
     */
    long getRequestCount(long windowMs) {
      long cutoff = System.currentTimeMillis() - windowMs;
      synchronized (requests) {
        int count = 0;
        for (Long ts : requests) {
          if (ts >= cutoff) {
            count++;
          }
        }
        return count;
      }
    }
  }

  /**
   * An audit log entry.
   */
  static class AuditEntry {
    /** ISO timestamp. */
    String timestamp = "";

    /** User identifier. */
    String userId = "";

    /** Tenant or project scope the request was made in. */
    String tenant = "default";

    /** Tool invoked. */
    String tool = "";

    /** Result: allowed, denied, rate_limited. */
    String result = "";

    /** Additional details. */
    String details;

    /** Request ID. */
    long requestId;

    /**
     * Converts to JSON.
     *
     * @return JSON representation
     */
    JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("timestamp", timestamp);
      obj.addProperty("userId", userId);
      obj.addProperty("tenant", tenant);
      obj.addProperty("tool", tool);
      obj.addProperty("result", result);
      if (details != null) {
        obj.addProperty("details", details);
      }
      obj.addProperty("requestId", requestId);
      return obj;
    }
  }
}
