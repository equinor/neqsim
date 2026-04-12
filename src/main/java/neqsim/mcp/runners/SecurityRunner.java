package neqsim.mcp.runners;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 * </p>
 *
 * <p>
 * This is an application-level security layer. In production, combine with transport-level security
 * (TLS, OAuth2) provided by the deployment platform.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SecurityRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Registered API keys (in production, these would come from a database or vault). */
  private static final ConcurrentHashMap<String, UserContext> API_KEYS =
      new ConcurrentHashMap<String, UserContext>();

  /** Audit log (in production, this would write to a persistent store). */
  private static final List<AuditEntry> AUDIT_LOG =
      Collections.synchronizedList(new ArrayList<AuditEntry>());

  /** Rate limiting: requests per key in the current time window. */
  private static final ConcurrentHashMap<String, RateState> RATE_LIMITS =
      new ConcurrentHashMap<String, RateState>();

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
   * Private constructor — all methods are static.
   */
  private SecurityRunner() {}

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
              "Use: createApiKey, revokeApiKey, authenticate, getAuditLog, "
                  + "getRateLimits, setConfig, getStatus");
      }
    } catch (Exception e) {
      return errorJson("SECURITY_ERROR", e.getMessage(), "Check JSON format");
    }
  }

  /**
   * Checks authentication and rate limiting for an incoming request. Call this at the beginning of
   * any protected tool invocation.
   *
   * @param apiKey the API key (optional if security is disabled)
   * @param tool the tool being invoked
   * @return null if allowed, or an error JSON string if denied
   */
  public static String checkAccess(String apiKey, String tool) {
    long reqId = REQUEST_COUNTER.incrementAndGet();

    if (!enabled) {
      // Log even when not enforcing
      logAudit("anonymous", tool, "allowed", "Security disabled");
      return null;
    }

    // Check API key
    if (apiKey == null || apiKey.isEmpty()) {
      logAudit("anonymous", tool, "denied", "Missing API key");
      return errorJson("AUTH_REQUIRED", "API key required",
          "Provide 'apiKey' field in request or disable security enforcement");
    }

    UserContext user = API_KEYS.get(apiKey);
    if (user == null) {
      logAudit("unknown:" + apiKey.substring(0, Math.min(8, apiKey.length())), tool, "denied",
          "Invalid API key");
      return errorJson("AUTH_FAILED", "Invalid API key", "Check your API key");
    }

    // Check rate limit
    if (!checkRateLimit(apiKey, user.rateLimit)) {
      logAudit(user.userId, tool, "rate_limited",
          "Exceeded " + user.rateLimit + " requests/minute");
      return errorJson("RATE_LIMITED",
          "Rate limit exceeded: " + user.rateLimit + " requests/minute",
          "Wait and retry, or request a higher rate limit");
    }

    logAudit(user.userId, tool, "allowed", null);
    return null; // Access granted
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
