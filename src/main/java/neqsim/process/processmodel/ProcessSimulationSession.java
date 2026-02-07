package neqsim.process.processmodel;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Session-scoped ProcessSystem manager for multi-user online simulation.
 *
 * <p>
 * Manages isolated simulation sessions where each user/request gets their own copy of a
 * ProcessSystem. Supports template-based creation (copy-on-create), automatic session expiry, and
 * concurrent access control.
 * </p>
 *
 * <h3>Usage from Python (via JPype):</h3>
 *
 * <pre>{@code
 * # Create a session manager with a template process
 * manager = ProcessSimulationSession()
 *
 * # Register a template
 * template = ProcessSystem("gas processing")
 * # ... build template ...
 * template.run()
 * manager.registerTemplate("gas_processing", template)
 *
 * # Create a session from the template
 * sessionId = manager.createSession("gas_processing")
 *
 * # Get the session's process system and modify it
 * process = manager.getSession(sessionId)
 * process.getUnit("feed").setFlowRate(60000.0, "kg/hr")
 * process.run()
 * report = process.getReport_json()
 *
 * # Clean up
 * manager.destroySession(sessionId)
 * }</pre>
 *
 * <h3>Usage with JSON builder:</h3>
 *
 * <pre>{@code
 * manager = ProcessSimulationSession()
 * sessionId = manager.createSessionFromJson(jsonDefinition)
 * process = manager.getSession(sessionId)
 * result = process.runAndReport()
 * }</pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessSimulationSession {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ProcessSimulationSession.class);

  /** Default session timeout in minutes. */
  private static final long DEFAULT_TIMEOUT_MINUTES = 30;

  /** Maximum number of concurrent sessions. */
  private static final int DEFAULT_MAX_SESSIONS = 100;

  /** Active sessions indexed by session ID. */
  private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();

  /** Named templates for copy-on-create. */
  private final ConcurrentHashMap<String, ProcessSystem> templates = new ConcurrentHashMap<>();

  /** Session timeout in minutes. */
  private long timeoutMinutes;

  /** Maximum concurrent sessions. */
  private int maxSessions;

  /** Scheduled executor for session cleanup. */
  private ScheduledExecutorService cleanupExecutor;

  /**
   * Internal session entry tracking creation time and last access.
   */
  private static class SessionEntry {
    private final ProcessSystem processSystem;
    private final long createdAt;
    private volatile long lastAccessedAt;
    private final String templateName;

    /**
     * Creates a session entry.
     *
     * @param processSystem the process system
     * @param templateName name of the template used (nullable)
     */
    SessionEntry(ProcessSystem processSystem, String templateName) {
      this.processSystem = processSystem;
      this.createdAt = System.currentTimeMillis();
      this.lastAccessedAt = System.currentTimeMillis();
      this.templateName = templateName;
    }

    /**
     * Touches the session to update last access time.
     */
    void touch() {
      this.lastAccessedAt = System.currentTimeMillis();
    }

    /**
     * Checks if the session has expired.
     *
     * @param timeoutMs timeout in milliseconds
     * @return true if expired
     */
    boolean isExpired(long timeoutMs) {
      return (System.currentTimeMillis() - lastAccessedAt) > timeoutMs;
    }
  }

  /**
   * Creates a session manager with default settings (30 min timeout, 100 max sessions).
   */
  public ProcessSimulationSession() {
    this(DEFAULT_TIMEOUT_MINUTES, DEFAULT_MAX_SESSIONS);
  }

  /**
   * Creates a session manager with custom settings.
   *
   * @param timeoutMinutes session timeout in minutes (0 = no timeout)
   * @param maxSessions maximum number of concurrent sessions
   */
  public ProcessSimulationSession(long timeoutMinutes, int maxSessions) {
    this.timeoutMinutes = timeoutMinutes;
    this.maxSessions = maxSessions;

    if (timeoutMinutes > 0) {
      cleanupExecutor =
          Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "neqsim-session-cleanup");
              t.setDaemon(true);
              return t;
            }
          });
      cleanupExecutor.scheduleAtFixedRate(new Runnable() {
        @Override
        public void run() {
          cleanupExpiredSessions();
        }
      }, timeoutMinutes, Math.max(1, timeoutMinutes / 2), TimeUnit.MINUTES);
    }
  }

  /**
   * Registers a named template ProcessSystem for copy-on-create session instantiation.
   *
   * <p>
   * The template is deeply copied when creating new sessions, so modifications to the template
   * after registration will not affect existing sessions.
   * </p>
   *
   * @param name the template name
   * @param template the process system to use as a template
   */
  public void registerTemplate(String name, ProcessSystem template) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Template name cannot be null or empty");
    }
    if (template == null) {
      throw new IllegalArgumentException("Template ProcessSystem cannot be null");
    }
    templates.put(name, template);
    logger.info("Registered template '{}'", name);
  }

  /**
   * Removes a named template.
   *
   * @param name the template name
   * @return true if the template was found and removed
   */
  public boolean removeTemplate(String name) {
    return templates.remove(name) != null;
  }

  /**
   * Gets the names of all registered templates.
   *
   * @return unmodifiable set of template names
   */
  public java.util.Set<String> getTemplateNames() {
    return Collections.unmodifiableSet(templates.keySet());
  }

  /**
   * Creates a new session from a named template (copy-on-create).
   *
   * <p>
   * The template is deep-copied so the session gets an independent ProcessSystem.
   * </p>
   *
   * @param templateName the name of the registered template
   * @return the session ID
   * @throws IllegalArgumentException if template not found or max sessions reached
   */
  public String createSession(String templateName) {
    if (sessions.size() >= maxSessions) {
      cleanupExpiredSessions();
      if (sessions.size() >= maxSessions) {
        throw new IllegalStateException("Maximum number of sessions (" + maxSessions + ") reached. "
            + "Destroy existing sessions or increase the limit.");
      }
    }

    ProcessSystem template = templates.get(templateName);
    if (template == null) {
      throw new IllegalArgumentException(
          "Template '" + templateName + "' not found. Available: " + templates.keySet());
    }

    ProcessSystem sessionProcess = template.copy();
    String sessionId = UUID.randomUUID().toString();
    sessions.put(sessionId, new SessionEntry(sessionProcess, templateName));
    logger.info("Created session '{}' from template '{}'", sessionId, templateName);
    return sessionId;
  }

  /**
   * Creates a new session with a blank ProcessSystem.
   *
   * @return the session ID
   * @throws IllegalStateException if max sessions reached
   */
  public String createEmptySession() {
    if (sessions.size() >= maxSessions) {
      cleanupExpiredSessions();
      if (sessions.size() >= maxSessions) {
        throw new IllegalStateException(
            "Maximum number of sessions (" + maxSessions + ") reached.");
      }
    }

    ProcessSystem process = new ProcessSystem("session-process");
    String sessionId = UUID.randomUUID().toString();
    sessions.put(sessionId, new SessionEntry(process, null));
    logger.info("Created empty session '{}'", sessionId);
    return sessionId;
  }

  /**
   * Creates a session from a JSON process definition.
   *
   * @param json the JSON process definition
   * @return a SimulationResult containing the session ID on success
   */
  public SimulationResult createSessionFromJson(String json) {
    if (sessions.size() >= maxSessions) {
      cleanupExpiredSessions();
      if (sessions.size() >= maxSessions) {
        return SimulationResult.error("MAX_SESSIONS",
            "Maximum number of sessions (" + maxSessions + ") reached",
            "Destroy existing sessions or increase the limit");
      }
    }

    SimulationResult buildResult = new JsonProcessBuilder().build(json);
    if (buildResult.isError()) {
      return buildResult;
    }

    ProcessSystem process = buildResult.getProcessSystem();
    String sessionId = UUID.randomUUID().toString();
    sessions.put(sessionId, new SessionEntry(process, "json"));
    logger.info("Created session '{}' from JSON definition", sessionId);

    // Return success with process and the sessionId stored in warnings (accessible)
    java.util.List<String> info = new java.util.ArrayList<>(buildResult.getWarnings());
    info.add(0, "sessionId:" + sessionId);
    return SimulationResult.success(process, null, info);
  }

  /**
   * Gets the ProcessSystem for an active session.
   *
   * @param sessionId the session ID
   * @return the session's ProcessSystem
   * @throws IllegalArgumentException if session not found or expired
   */
  public ProcessSystem getSession(String sessionId) {
    SessionEntry entry = sessions.get(sessionId);
    if (entry == null) {
      throw new IllegalArgumentException("Session '" + sessionId + "' not found or expired");
    }
    if (timeoutMinutes > 0 && entry.isExpired(timeoutMinutes * 60 * 1000)) {
      sessions.remove(sessionId);
      throw new IllegalArgumentException("Session '" + sessionId + "' has expired");
    }
    entry.touch();
    return entry.processSystem;
  }

  /**
   * Runs the simulation for a session and returns the result.
   *
   * @param sessionId the session ID
   * @return a SimulationResult with the run report or errors
   */
  public SimulationResult runSession(String sessionId) {
    try {
      ProcessSystem process = getSession(sessionId);
      return process.runAndReport();
    } catch (IllegalArgumentException e) {
      return SimulationResult.error("SESSION_NOT_FOUND", e.getMessage(),
          "Create a new session first");
    }
  }

  /**
   * Destroys a session and releases its resources.
   *
   * @param sessionId the session ID
   * @return true if the session was found and destroyed
   */
  public boolean destroySession(String sessionId) {
    SessionEntry removed = sessions.remove(sessionId);
    if (removed != null) {
      logger.info("Destroyed session '{}'", sessionId);
      return true;
    }
    return false;
  }

  /**
   * Gets the number of active sessions.
   *
   * @return active session count
   */
  public int getActiveSessionCount() {
    return sessions.size();
  }

  /**
   * Gets the maximum number of allowed sessions.
   *
   * @return max sessions limit
   */
  public int getMaxSessions() {
    return maxSessions;
  }

  /**
   * Sets the maximum number of allowed sessions.
   *
   * @param maxSessions the new limit
   */
  public void setMaxSessions(int maxSessions) {
    this.maxSessions = maxSessions;
  }

  /**
   * Gets session timeout in minutes.
   *
   * @return timeout in minutes
   */
  public long getTimeoutMinutes() {
    return timeoutMinutes;
  }

  /**
   * Gets information about all active sessions.
   *
   * @return map of session IDs to their template names (null if blank session)
   */
  public Map<String, String> getSessionInfo() {
    Map<String, String> info = new ConcurrentHashMap<>();
    for (Map.Entry<String, SessionEntry> entry : sessions.entrySet()) {
      info.put(entry.getKey(),
          entry.getValue().templateName != null ? entry.getValue().templateName : "(blank)");
    }
    return info;
  }

  /**
   * Removes all expired sessions.
   *
   * @return number of sessions removed
   */
  public int cleanupExpiredSessions() {
    if (timeoutMinutes <= 0) {
      return 0;
    }
    long timeoutMs = timeoutMinutes * 60 * 1000;
    int removed = 0;
    for (Map.Entry<String, SessionEntry> entry : sessions.entrySet()) {
      if (entry.getValue().isExpired(timeoutMs)) {
        sessions.remove(entry.getKey());
        removed++;
        logger.info("Expired session '{}'", entry.getKey());
      }
    }
    return removed;
  }

  /**
   * Destroys all sessions and releases resources.
   */
  public void destroyAllSessions() {
    int count = sessions.size();
    sessions.clear();
    logger.info("Destroyed all {} sessions", count);
  }

  /**
   * Shuts down the session manager including the cleanup executor.
   */
  public void shutdown() {
    destroyAllSessions();
    if (cleanupExecutor != null) {
      cleanupExecutor.shutdownNow();
    }
    logger.info("Session manager shut down");
  }
}
