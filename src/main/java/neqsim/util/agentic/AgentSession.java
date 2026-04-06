package neqsim.util.agentic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Tracks an AI agent workflow session from start to completion.
 *
 * <p>
 * Records the sequence of phases an agent traverses while solving an engineering task (scope,
 * research, analysis, validation, reporting). Each phase captures timing, tool invocations,
 * validation outcomes, and the final result status. Sessions can be serialized to JSON for
 * persistent logging and cross-session learning.
 * </p>
 *
 * <h2>Workflow Phases:</h2>
 * <ul>
 * <li><b>SCOPE</b> — Task classification, standards identification, context gathering</li>
 * <li><b>RESEARCH</b> — Literature review, API discovery, pattern search</li>
 * <li><b>ANALYSIS</b> — Simulation building, flash calculations, equipment sizing</li>
 * <li><b>VALIDATION</b> — Result verification, benchmark comparison, mass/energy balance</li>
 * <li><b>REPORTING</b> — Report generation, figure creation, results.json assembly</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * {@code
 * AgentSession session = AgentSession.start("solve.task", "TEG dehydration sizing");
 * session.beginPhase(AgentSession.Phase.SCOPE);
 * session.recordToolUse("thermo.fluid", "Create SRK fluid with water");
 * session.endPhase(AgentSession.Phase.SCOPE);
 *
 * session.beginPhase(AgentSession.Phase.ANALYSIS);
 * session.recordSimulationRun("process.run()", true, 2.3);
 * session.endPhase(AgentSession.Phase.ANALYSIS);
 *
 * session.complete(AgentSession.Outcome.SUCCESS);
 * String json = session.toJson();
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AgentSession implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Workflow phase in the task-solving lifecycle.
   */
  public enum Phase {
    /** Task classification, standards identification, context gathering. */
    SCOPE,
    /** Literature review, API discovery, pattern search. */
    RESEARCH,
    /** Simulation building, flash calculations, equipment sizing. */
    ANALYSIS,
    /** Result verification, benchmark comparison, balance checks. */
    VALIDATION,
    /** Report generation, figure creation, results assembly. */
    REPORTING
  }

  /**
   * Final outcome of the agent session.
   */
  public enum Outcome {
    /** Task completed successfully with validated results. */
    SUCCESS,
    /** Task completed with warnings (e.g., convergence issues, assumptions). */
    PARTIAL,
    /** Task failed — missing capability, non-convergence, invalid input. */
    FAILED,
    /** Task was abandoned before completion. */
    ABANDONED
  }

  private final String sessionId;
  private final String agentName;
  private final String taskDescription;
  private final long startTimeMillis;
  private long endTimeMillis;
  private Outcome outcome;
  private final List<PhaseRecord> phases;
  private final List<ToolInvocation> toolInvocations;
  private final List<SimulationRun> simulationRuns;
  private final Map<String, String> metadata;
  private String failureReason;

  /**
   * Start a new agent session.
   *
   * @param agentName name of the agent (e.g., "solve.task", "process.model")
   * @param taskDescription brief description of the engineering task
   * @return new session instance
   */
  public static AgentSession start(String agentName, String taskDescription) {
    return new AgentSession(agentName, taskDescription);
  }

  /**
   * Constructor.
   *
   * @param agentName name of the agent
   * @param taskDescription task description
   */
  private AgentSession(String agentName, String taskDescription) {
    this.sessionId = UUID.randomUUID().toString();
    this.agentName = agentName;
    this.taskDescription = taskDescription;
    this.startTimeMillis = System.currentTimeMillis();
    this.endTimeMillis = 0;
    this.outcome = null;
    this.phases = new ArrayList<PhaseRecord>();
    this.toolInvocations = new ArrayList<ToolInvocation>();
    this.simulationRuns = new ArrayList<SimulationRun>();
    this.metadata = new LinkedHashMap<String, String>();
    this.failureReason = null;
  }

  /**
   * Begin a workflow phase.
   *
   * @param phase the phase to start
   */
  public void beginPhase(Phase phase) {
    phases.add(new PhaseRecord(phase));
  }

  /**
   * End a workflow phase.
   *
   * @param phase the phase to end
   */
  public void endPhase(Phase phase) {
    for (int i = phases.size() - 1; i >= 0; i--) {
      PhaseRecord record = phases.get(i);
      if (record.phase == phase && record.endTimeMillis == 0) {
        record.endTimeMillis = System.currentTimeMillis();
        return;
      }
    }
  }

  /**
   * Record a tool invocation during the session.
   *
   * @param toolName name of the tool or agent invoked
   * @param description what the tool was used for
   */
  public void recordToolUse(String toolName, String description) {
    toolInvocations.add(new ToolInvocation(toolName, description));
  }

  /**
   * Record a simulation run and its outcome.
   *
   * @param description what simulation was run
   * @param success whether it succeeded
   * @param durationSeconds execution time in seconds
   */
  public void recordSimulationRun(String description, boolean success, double durationSeconds) {
    simulationRuns.add(new SimulationRun(description, success, durationSeconds));
  }

  /**
   * Add metadata to the session.
   *
   * @param key metadata key
   * @param value metadata value
   */
  public void addMetadata(String key, String value) {
    metadata.put(key, value);
  }

  /**
   * Mark the session as complete.
   *
   * @param outcome the final outcome
   */
  public void complete(Outcome outcome) {
    this.outcome = outcome;
    this.endTimeMillis = System.currentTimeMillis();
  }

  /**
   * Mark the session as failed with a reason.
   *
   * @param reason failure reason
   */
  public void fail(String reason) {
    this.outcome = Outcome.FAILED;
    this.failureReason = reason;
    this.endTimeMillis = System.currentTimeMillis();
  }

  /**
   * Get the session ID.
   *
   * @return unique session identifier
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * Get the agent name.
   *
   * @return agent name
   */
  public String getAgentName() {
    return agentName;
  }

  /**
   * Get the task description.
   *
   * @return task description
   */
  public String getTaskDescription() {
    return taskDescription;
  }

  /**
   * Get the session outcome.
   *
   * @return outcome or null if not yet completed
   */
  public Outcome getOutcome() {
    return outcome;
  }

  /**
   * Get the failure reason.
   *
   * @return failure reason or null if not failed
   */
  public String getFailureReason() {
    return failureReason;
  }

  /**
   * Get duration of the session in seconds.
   *
   * @return duration in seconds, or time since start if not yet completed
   */
  public double getDurationSeconds() {
    long end = endTimeMillis > 0 ? endTimeMillis : System.currentTimeMillis();
    return (end - startTimeMillis) / 1000.0;
  }

  /**
   * Get the number of simulation runs.
   *
   * @return total simulation run count
   */
  public int getSimulationRunCount() {
    return simulationRuns.size();
  }

  /**
   * Get the number of successful simulation runs.
   *
   * @return successful run count
   */
  public int getSuccessfulSimulationCount() {
    int count = 0;
    for (SimulationRun run : simulationRuns) {
      if (run.success) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get the number of tool invocations.
   *
   * @return tool invocation count
   */
  public int getToolInvocationCount() {
    return toolInvocations.size();
  }

  /**
   * Get the phases traversed.
   *
   * @return unmodifiable list of phase records
   */
  public List<PhaseRecord> getPhases() {
    return Collections.unmodifiableList(phases);
  }

  /**
   * Get the tool invocations.
   *
   * @return unmodifiable list of tool invocations
   */
  public List<ToolInvocation> getToolInvocations() {
    return Collections.unmodifiableList(toolInvocations);
  }

  /**
   * Get the simulation runs.
   *
   * @return unmodifiable list of simulation runs
   */
  public List<SimulationRun> getSimulationRuns() {
    return Collections.unmodifiableList(simulationRuns);
  }

  /**
   * Serialize the session to JSON.
   *
   * @return JSON representation of the session
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("sessionId", sessionId);
    map.put("agentName", agentName);
    map.put("taskDescription", taskDescription);
    map.put("outcome", outcome != null ? outcome.name() : "IN_PROGRESS");
    map.put("durationSeconds", getDurationSeconds());
    map.put("simulationRuns", simulationRuns.size());
    map.put("successfulSimulations", getSuccessfulSimulationCount());
    map.put("toolInvocations", toolInvocations.size());
    if (failureReason != null) {
      map.put("failureReason", failureReason);
    }

    List<Map<String, Object>> phaseList = new ArrayList<Map<String, Object>>();
    for (PhaseRecord pr : phases) {
      Map<String, Object> pm = new LinkedHashMap<String, Object>();
      pm.put("phase", pr.phase.name());
      pm.put("durationSeconds", pr.getDurationSeconds());
      phaseList.add(pm);
    }
    map.put("phases", phaseList);

    if (!metadata.isEmpty()) {
      map.put("metadata", metadata);
    }

    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(map);
  }

  /**
   * Record of a single workflow phase with timing.
   */
  public static class PhaseRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** The workflow phase. */
    public final Phase phase;
    /** When the phase started (epoch millis). */
    public final long startTimeMillis;
    /** When the phase ended (epoch millis), 0 if still running. */
    public long endTimeMillis;

    /**
     * Constructor.
     *
     * @param phase the workflow phase
     */
    PhaseRecord(Phase phase) {
      this.phase = phase;
      this.startTimeMillis = System.currentTimeMillis();
      this.endTimeMillis = 0;
    }

    /**
     * Get duration of this phase in seconds.
     *
     * @return duration in seconds
     */
    public double getDurationSeconds() {
      long end = endTimeMillis > 0 ? endTimeMillis : System.currentTimeMillis();
      return (end - startTimeMillis) / 1000.0;
    }
  }

  /**
   * Record of a tool invocation.
   */
  public static class ToolInvocation implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Name of the tool invoked. */
    public final String toolName;
    /** Description of what the tool was used for. */
    public final String description;
    /** When the tool was invoked (epoch millis). */
    public final long timestamp;

    /**
     * Constructor.
     *
     * @param toolName tool name
     * @param description invocation description
     */
    ToolInvocation(String toolName, String description) {
      this.toolName = toolName;
      this.description = description;
      this.timestamp = System.currentTimeMillis();
    }
  }

  /**
   * Record of a simulation run.
   */
  public static class SimulationRun implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Description of the simulation. */
    public final String description;
    /** Whether the run succeeded. */
    public final boolean success;
    /** Execution time in seconds. */
    public final double durationSeconds;
    /** When the run was recorded (epoch millis). */
    public final long timestamp;

    /**
     * Constructor.
     *
     * @param description simulation description
     * @param success whether it succeeded
     * @param durationSeconds execution duration
     */
    SimulationRun(String description, boolean success, double durationSeconds) {
      this.description = description;
      this.success = success;
      this.durationSeconds = durationSeconds;
      this.timestamp = System.currentTimeMillis();
    }
  }
}
