package neqsim.util.agentic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Collects and aggregates feedback metrics from agent sessions for continuous improvement.
 *
 * <p>
 * Maintains a rolling log of agent session outcomes, categorized by task type, agent name, and
 * failure mode. Provides summary statistics that can be used to identify recurring failure patterns
 * and guide improvements to both the AI agent prompts and the underlying NeqSim Java codebase.
 * </p>
 *
 * <h2>Key Metrics Tracked:</h2>
 * <ul>
 * <li>Success rate per agent and task type</li>
 * <li>Common failure modes (convergence, missing API, invalid input)</li>
 * <li>Simulation run success rate across sessions</li>
 * <li>Average phase durations (scope, research, analysis, validation, reporting)</li>
 * <li>NeqSim API gaps discovered during sessions</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * {@code
 * AgentFeedbackCollector collector = new AgentFeedbackCollector();
 *
 * // After each session completes
 * collector.recordSession(session);
 *
 * // Get aggregate statistics
 * String report = collector.getSummaryReport();
 * double successRate = collector.getSuccessRate("solve.task");
 *
 * // Get improvement recommendations
 * List<String> gaps = collector.getDiscoveredAPIGaps();
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AgentFeedbackCollector implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Failure category for classification of session failures.
   */
  public enum FailureCategory {
    /** Flash or solver did not converge. */
    CONVERGENCE,
    /** Required NeqSim API method or class missing. */
    MISSING_API,
    /** User input was invalid or physically impossible. */
    INVALID_INPUT,
    /** Java compilation or runtime error. */
    CODE_ERROR,
    /** Timeout or resource exhaustion. */
    TIMEOUT,
    /** Unknown or unclassified failure. */
    OTHER
  }

  private final List<SessionSummary> sessions;
  private final List<APIGap> discoveredGaps;

  /**
   * Create a new feedback collector.
   */
  public AgentFeedbackCollector() {
    this.sessions = new ArrayList<SessionSummary>();
    this.discoveredGaps = new ArrayList<APIGap>();
  }

  /**
   * Record a completed agent session.
   *
   * @param session the completed session
   */
  public void recordSession(AgentSession session) {
    sessions.add(new SessionSummary(session));
  }

  /**
   * Record a discovered API gap (missing capability in NeqSim).
   *
   * @param description what is missing
   * @param suggestedPackage where the implementation should go
   * @param priority priority level ("critical", "important", "nice-to-have")
   */
  public void recordAPIGap(String description, String suggestedPackage, String priority) {
    discoveredGaps.add(new APIGap(description, suggestedPackage, priority));
  }

  /**
   * Get the overall success rate across all recorded sessions.
   *
   * @return success rate as a fraction (0.0 to 1.0), or 0.0 if no sessions
   */
  public double getOverallSuccessRate() {
    if (sessions.isEmpty()) {
      return 0.0;
    }
    int successes = 0;
    for (SessionSummary s : sessions) {
      if (s.outcome == AgentSession.Outcome.SUCCESS || s.outcome == AgentSession.Outcome.PARTIAL) {
        successes++;
      }
    }
    return (double) successes / sessions.size();
  }

  /**
   * Get the success rate for a specific agent.
   *
   * @param agentName agent to filter by
   * @return success rate as a fraction (0.0 to 1.0), or 0.0 if no sessions for that agent
   */
  public double getSuccessRate(String agentName) {
    int total = 0;
    int successes = 0;
    for (SessionSummary s : sessions) {
      if (s.agentName.equals(agentName)) {
        total++;
        if (s.outcome == AgentSession.Outcome.SUCCESS
            || s.outcome == AgentSession.Outcome.PARTIAL) {
          successes++;
        }
      }
    }
    return total > 0 ? (double) successes / total : 0.0;
  }

  /**
   * Get the total number of recorded sessions.
   *
   * @return session count
   */
  public int getSessionCount() {
    return sessions.size();
  }

  /**
   * Get all discovered API gaps.
   *
   * @return unmodifiable list of API gaps
   */
  public List<APIGap> getDiscoveredAPIGaps() {
    return Collections.unmodifiableList(discoveredGaps);
  }

  /**
   * Get the most common failure categories across all sessions.
   *
   * @return map of failure category to count, sorted by frequency
   */
  public Map<String, Integer> getFailureCategoryCounts() {
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    for (SessionSummary s : sessions) {
      if (s.outcome == AgentSession.Outcome.FAILED && s.failureCategory != null) {
        String cat = s.failureCategory;
        Integer prev = counts.get(cat);
        counts.put(cat, prev != null ? prev + 1 : 1);
      }
    }
    return counts;
  }

  /**
   * Get average simulation success rate across all sessions.
   *
   * @return average simulation success rate (0.0 to 1.0)
   */
  public double getAverageSimulationSuccessRate() {
    if (sessions.isEmpty()) {
      return 0.0;
    }
    double totalRate = 0.0;
    int count = 0;
    for (SessionSummary s : sessions) {
      if (s.totalSimulations > 0) {
        totalRate += (double) s.successfulSimulations / s.totalSimulations;
        count++;
      }
    }
    return count > 0 ? totalRate / count : 0.0;
  }

  /**
   * Generate a summary report as JSON.
   *
   * @return JSON summary report
   */
  public String getSummaryReport() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("totalSessions", sessions.size());
    report.put("overallSuccessRate", getOverallSuccessRate());
    report.put("averageSimulationSuccessRate", getAverageSimulationSuccessRate());
    report.put("failureCategories", getFailureCategoryCounts());
    report.put("discoveredAPIGaps", discoveredGaps.size());

    // Per-agent breakdown
    Map<String, Map<String, Object>> agentBreakdown =
        new LinkedHashMap<String, Map<String, Object>>();
    for (SessionSummary s : sessions) {
      if (!agentBreakdown.containsKey(s.agentName)) {
        Map<String, Object> agentInfo = new LinkedHashMap<String, Object>();
        agentInfo.put("sessions", 0);
        agentInfo.put("successes", 0);
        agentBreakdown.put(s.agentName, agentInfo);
      }
      Map<String, Object> agentInfo = agentBreakdown.get(s.agentName);
      agentInfo.put("sessions", ((Integer) agentInfo.get("sessions")) + 1);
      if (s.outcome == AgentSession.Outcome.SUCCESS || s.outcome == AgentSession.Outcome.PARTIAL) {
        agentInfo.put("successes", ((Integer) agentInfo.get("successes")) + 1);
      }
    }
    report.put("agentBreakdown", agentBreakdown);

    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(report);
  }

  /**
   * Classify a failure reason into a category.
   *
   * @param failureReason the failure reason string from AgentSession
   * @return classified failure category
   */
  public static FailureCategory classifyFailure(String failureReason) {
    if (failureReason == null || failureReason.trim().isEmpty()) {
      return FailureCategory.OTHER;
    }
    String lower = failureReason.toLowerCase();
    if (lower.contains("convergence") || lower.contains("converge") || lower.contains("iteration")
        || lower.contains("diverge")) {
      return FailureCategory.CONVERGENCE;
    }
    if (lower.contains("missing") || lower.contains("not found") || lower.contains("no such method")
        || lower.contains("not implemented")) {
      return FailureCategory.MISSING_API;
    }
    if (lower.contains("invalid") || lower.contains("negative pressure")
        || lower.contains("negative temperature") || lower.contains("out of range")) {
      return FailureCategory.INVALID_INPUT;
    }
    if (lower.contains("compile") || lower.contains("classnotfound")
        || lower.contains("nullpointer") || lower.contains("exception")) {
      return FailureCategory.CODE_ERROR;
    }
    if (lower.contains("timeout") || lower.contains("memory") || lower.contains("oom")) {
      return FailureCategory.TIMEOUT;
    }
    return FailureCategory.OTHER;
  }

  /**
   * Summary of a recorded session (lightweight copy for storage).
   */
  public static class SessionSummary implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Session ID. */
    public final String sessionId;
    /** Agent name. */
    public final String agentName;
    /** Task description. */
    public final String taskDescription;
    /** Final outcome. */
    public final AgentSession.Outcome outcome;
    /** Duration in seconds. */
    public final double durationSeconds;
    /** Total simulation runs. */
    public final int totalSimulations;
    /** Successful simulation runs. */
    public final int successfulSimulations;
    /** Classified failure category (null if not failed). */
    public final String failureCategory;

    /**
     * Create summary from a completed session.
     *
     * @param session the completed session
     */
    SessionSummary(AgentSession session) {
      this.sessionId = session.getSessionId();
      this.agentName = session.getAgentName();
      this.taskDescription = session.getTaskDescription();
      this.outcome = session.getOutcome();
      this.durationSeconds = session.getDurationSeconds();
      this.totalSimulations = session.getSimulationRunCount();
      this.successfulSimulations = session.getSuccessfulSimulationCount();
      if (session.getFailureReason() != null) {
        this.failureCategory = classifyFailure(session.getFailureReason()).name();
      } else {
        this.failureCategory = null;
      }
    }
  }

  /**
   * Record of a discovered API gap.
   */
  public static class APIGap implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Description of what is missing. */
    public final String description;
    /** Suggested package for implementation. */
    public final String suggestedPackage;
    /** Priority level. */
    public final String priority;
    /** When it was discovered (epoch millis). */
    public final long discoveredAt;

    /**
     * Constructor.
     *
     * @param description gap description
     * @param suggestedPackage suggested implementation location
     * @param priority priority level
     */
    APIGap(String description, String suggestedPackage, String priority) {
      this.description = description;
      this.suggestedPackage = suggestedPackage;
      this.priority = priority;
      this.discoveredAt = System.currentTimeMillis();
    }
  }
}
