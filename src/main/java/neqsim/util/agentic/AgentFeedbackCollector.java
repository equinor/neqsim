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
    report.put("remediationRecommendations", generateRemediations());
    report.put("learningTrend", computeLearningTrend());

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
   * Generate automated remediation recommendations based on recurring failure patterns.
   *
   * <p>
   * Analyzes failure frequency by category and agent, and returns prioritized, actionable
   * recommendations. This is the core of the cross-session learning loop described in the
   * framework: failures drive recommendations, which in turn drive framework improvements.
   * </p>
   *
   * @return list of remediation recommendation maps, each with "priority", "category",
   *         "recommendation", "affectedAgent", and "frequency"
   */
  public List<Map<String, Object>> generateRemediations() {
    List<Map<String, Object>> remediations = new ArrayList<Map<String, Object>>();

    if (sessions.isEmpty()) {
      return remediations;
    }

    // Count failures per (agent, category) pair
    Map<String, Map<String, Integer>> agentFailures =
        new LinkedHashMap<String, Map<String, Integer>>();
    for (SessionSummary s : sessions) {
      if (s.outcome == AgentSession.Outcome.FAILED && s.failureCategory != null) {
        Map<String, Integer> catMap = agentFailures.get(s.agentName);
        if (catMap == null) {
          catMap = new LinkedHashMap<String, Integer>();
          agentFailures.put(s.agentName, catMap);
        }
        Integer prev = catMap.get(s.failureCategory);
        catMap.put(s.failureCategory, prev != null ? prev + 1 : 1);
      }
    }

    // Generate recommendations for each recurring failure pattern
    for (Map.Entry<String, Map<String, Integer>> agentEntry : agentFailures.entrySet()) {
      String agent = agentEntry.getKey();
      for (Map.Entry<String, Integer> catEntry : agentEntry.getValue().entrySet()) {
        String category = catEntry.getKey();
        int count = catEntry.getValue();

        // Only recommend for patterns that occur more than once
        if (count < 2) {
          continue;
        }

        Map<String, Object> rec = new LinkedHashMap<String, Object>();
        rec.put("affectedAgent", agent);
        rec.put("category", category);
        rec.put("frequency", count);
        rec.put("priority", count >= 5 ? "critical" : count >= 3 ? "high" : "medium");
        rec.put("recommendation", getRemediationText(category, agent, count));
        remediations.add(rec);
      }
    }

    // Sort by frequency descending
    Collections.sort(remediations, new java.util.Comparator<Map<String, Object>>() {
      @Override
      public int compare(Map<String, Object> a, Map<String, Object> b) {
        return ((Integer) b.get("frequency")).compareTo((Integer) a.get("frequency"));
      }
    });

    return remediations;
  }

  /**
   * Compute a learning trend showing success rate over sliding windows.
   *
   * <p>
   * Divides sessions into windows of 10 and computes the success rate in each window. An increasing
   * trend indicates that the framework is improving over time through accumulated knowledge (skill
   * updates, API gap fixes, new error-handling patterns).
   * </p>
   *
   * @return list of maps with "window", "sessions", "successRate" keys, one per window
   */
  public List<Map<String, Object>> computeLearningTrend() {
    List<Map<String, Object>> trend = new ArrayList<Map<String, Object>>();
    int windowSize = 10;

    if (sessions.size() < windowSize) {
      // Not enough data for trend analysis — return single window
      if (!sessions.isEmpty()) {
        Map<String, Object> window = new LinkedHashMap<String, Object>();
        window.put("window", 1);
        window.put("sessions", sessions.size());
        window.put("successRate", getOverallSuccessRate());
        trend.add(window);
      }
      return trend;
    }

    int windowCount = 0;
    for (int start = 0; start < sessions.size(); start += windowSize) {
      int end = Math.min(start + windowSize, sessions.size());
      int successes = 0;
      int total = end - start;

      for (int i = start; i < end; i++) {
        SessionSummary s = sessions.get(i);
        if (s.outcome == AgentSession.Outcome.SUCCESS
            || s.outcome == AgentSession.Outcome.PARTIAL) {
          successes++;
        }
      }

      windowCount++;
      Map<String, Object> window = new LinkedHashMap<String, Object>();
      window.put("window", windowCount);
      window.put("sessions", total);
      window.put("successRate", total > 0 ? (double) successes / total : 0.0);
      trend.add(window);
    }

    return trend;
  }

  /**
   * Identify the most impactful API gap to fix next.
   *
   * <p>
   * Cross-references discovered API gaps with failure frequency to determine which missing
   * capability would fix the most failures if implemented. This drives the development flywheel
   * described in the framework: Gap discovery leads to targeted implementation.
   * </p>
   *
   * @return the highest-impact API gap, or null if none recorded
   */
  public APIGap getMostImpactfulGap() {
    if (discoveredGaps.isEmpty()) {
      return null;
    }

    // Count MISSING_API failures
    int missingApiCount = 0;
    for (SessionSummary s : sessions) {
      if (s.failureCategory != null && s.failureCategory.equals("MISSING_API")) {
        missingApiCount++;
      }
    }

    // If no missing API failures, return the highest-priority gap
    if (missingApiCount == 0) {
      APIGap best = discoveredGaps.get(0);
      for (APIGap gap : discoveredGaps) {
        if ("critical".equals(gap.priority)) {
          return gap;
        }
        if ("important".equals(gap.priority) && !"critical".equals(best.priority)) {
          best = gap;
        }
      }
      return best;
    }

    // Return the most recent critical gap (most likely to be causing current failures)
    APIGap best = null;
    for (APIGap gap : discoveredGaps) {
      if ("critical".equals(gap.priority)) {
        if (best == null || gap.discoveredAt > best.discoveredAt) {
          best = gap;
        }
      }
    }
    return best != null ? best : discoveredGaps.get(discoveredGaps.size() - 1);
  }

  /**
   * Get remediation text for a failure category.
   *
   * @param category the failure category name
   * @param agent the affected agent name
   * @param count how many times this failure occurred
   * @return human-readable remediation recommendation
   */
  private String getRemediationText(String category, String agent, int count) {
    if ("CONVERGENCE".equals(category)) {
      return "Agent '" + agent + "' has " + count
          + " convergence failures. Load the troubleshooting skill and apply "
          + "ranked recovery strategies: (1) tighten recycle tolerance, (2) adjust "
          + "initial estimates, (3) switch solver type, (4) simplify fluid model.";
    }
    if ("MISSING_API".equals(category)) {
      return "Agent '" + agent + "' encountered " + count
          + " missing API errors. Review discovered API gaps and implement "
          + "the missing classes/methods. Check CHANGELOG_AGENT_NOTES.md for recent changes.";
    }
    if ("INVALID_INPUT".equals(category)) {
      return "Agent '" + agent + "' received " + count
          + " invalid inputs. Add stricter input validation in the agent's "
          + "scope phase. Load the input-validation skill for pre-simulation checks.";
    }
    if ("CODE_ERROR".equals(category)) {
      return "Agent '" + agent + "' has " + count
          + " code errors. Review Java 8 compatibility rules and ensure all "
          + "generated code compiles. Check for null pointer issues in fluid initialization.";
    }
    if ("TIMEOUT".equals(category)) {
      return "Agent '" + agent + "' has " + count
          + " timeouts. Reduce simulation complexity, use simpler EOS for initial "
          + "estimates, or increase time limits for complex multi-recycle flowsheets.";
    }
    return "Agent '" + agent + "' has " + count + " failures in category " + category
        + ". Review session logs for root cause.";
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
