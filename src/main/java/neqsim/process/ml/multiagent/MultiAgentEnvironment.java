package neqsim.process.ml.multiagent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.ml.ConstraintManager;
import neqsim.process.ml.StateVector;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Multi-agent environment for coordinated process control.
 *
 * <p>
 * Manages multiple agents that each control a subset of process equipment while respecting global
 * constraints. Supports various multi-agent paradigms:
 * <ul>
 * <li><b>Independent</b> - Agents act independently with local observations</li>
 * <li><b>Centralized Training Decentralized Execution (CTDE)</b> - Shared critic during
 * training</li>
 * <li><b>Communicating</b> - Agents exchange messages before acting</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * MultiAgentEnvironment env = new MultiAgentEnvironment(processSystem);
 * env.addAgent(separatorAgent);
 * env.addAgent(compressorAgent);
 * env.setSharedConstraints(globalConstraints);
 *
 * Map<String, double[]> obs = env.reset();
 * while (!env.isDone()) {
 *   Map<String, double[]> actions = getActionsFromPolicies(obs);
 *   MultiAgentStepResult result = env.step(actions);
 *   obs = result.observations;
 * }
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class MultiAgentEnvironment implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Coordination mode for multi-agent systems.
   */
  public enum CoordinationMode {
    /** Agents act independently with local rewards. */
    INDEPENDENT,
    /** Agents share a team reward. */
    COOPERATIVE,
    /** Centralized training with decentralized execution. */
    CTDE,
    /** Agents communicate before acting. */
    COMMUNICATING
  }

  private final ProcessSystem process;
  private final Map<String, Agent> agents;
  private final List<String> agentOrder;
  private ConstraintManager sharedConstraints;
  private CoordinationMode coordinationMode = CoordinationMode.INDEPENDENT;

  // Episode state
  private int currentStep = 0;
  private int maxEpisodeSteps = 1000;
  private boolean terminated = false;
  private boolean truncated = false;
  private StateVector currentGlobalState;

  // Communication
  private Map<String, double[]> messages;

  /**
   * Step result for multi-agent environment.
   */
  public static class MultiAgentStepResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Observations for each agent. */
    public final Map<String, double[]> observations;
    /** Rewards for each agent (or shared if cooperative). */
    public final Map<String, Double> rewards;
    /** Whether episode terminated. */
    public final boolean terminated;
    /** Whether episode truncated. */
    public final boolean truncated;
    /** Info for each agent. */
    public final Map<String, Map<String, Object>> infos;
    /** Global state (for centralized critic). */
    public final StateVector globalState;

    /**
     * Constructor.
     *
     * @param observations agent observations
     * @param rewards agent rewards
     * @param terminated episode terminated
     * @param truncated episode truncated
     * @param infos agent infos
     * @param globalState global state
     */
    public MultiAgentStepResult(Map<String, double[]> observations, Map<String, Double> rewards,
        boolean terminated, boolean truncated, Map<String, Map<String, Object>> infos,
        StateVector globalState) {
      this.observations = observations;
      this.rewards = rewards;
      this.terminated = terminated;
      this.truncated = truncated;
      this.infos = infos;
      this.globalState = globalState;
    }
  }

  /**
   * Create a multi-agent environment.
   *
   * @param process the process system
   */
  public MultiAgentEnvironment(ProcessSystem process) {
    this.process = process;
    this.agents = new LinkedHashMap<>();
    this.agentOrder = new ArrayList<>();
    this.sharedConstraints = new ConstraintManager();
    this.messages = new HashMap<>();
  }

  /**
   * Add an agent to the environment.
   *
   * @param agent the agent to add
   * @return this environment for chaining
   */
  public MultiAgentEnvironment addAgent(Agent agent) {
    agents.put(agent.getAgentId(), agent);
    agentOrder.add(agent.getAgentId());
    return this;
  }

  /**
   * Set shared constraints for all agents.
   *
   * @param constraints shared constraint manager
   * @return this environment for chaining
   */
  public MultiAgentEnvironment setSharedConstraints(ConstraintManager constraints) {
    this.sharedConstraints = constraints;
    return this;
  }

  /**
   * Set coordination mode.
   *
   * @param mode coordination mode
   * @return this environment for chaining
   */
  public MultiAgentEnvironment setCoordinationMode(CoordinationMode mode) {
    this.coordinationMode = mode;
    return this;
  }

  /**
   * Set maximum episode steps.
   *
   * @param maxSteps maximum steps
   * @return this environment for chaining
   */
  public MultiAgentEnvironment setMaxEpisodeSteps(int maxSteps) {
    this.maxEpisodeSteps = maxSteps;
    return this;
  }

  /**
   * Reset the environment.
   *
   * @return initial observations for each agent
   */
  public Map<String, double[]> reset() {
    currentStep = 0;
    terminated = false;
    truncated = false;
    messages.clear();

    // Reset process to initial state
    process.run();

    // Get global state
    currentGlobalState = getGlobalState();

    // Get initial observations
    Map<String, double[]> observations = new LinkedHashMap<>();
    for (String agentId : agentOrder) {
      Agent agent = agents.get(agentId);
      observations.put(agentId, agent.getLocalObservation(currentGlobalState));
    }

    return observations;
  }

  /**
   * Take a step with all agents' actions.
   *
   * @param actions map of agent ID to action array
   * @return step result
   */
  public MultiAgentStepResult step(Map<String, double[]> actions) {
    currentStep++;

    // Phase 1: Communication (if enabled)
    if (coordinationMode == CoordinationMode.COMMUNICATING) {
      messages.clear();
      for (String agentId : agentOrder) {
        Agent agent = agents.get(agentId);
        double[] msg = agent.getMessage(currentGlobalState);
        if (msg.length > 0) {
          messages.put(agentId, msg);
        }
      }
      // Distribute messages
      for (Agent agent : agents.values()) {
        agent.receiveMessages(messages);
      }
    }

    // Phase 2: Apply all actions
    for (String agentId : agentOrder) {
      Agent agent = agents.get(agentId);
      double[] action = actions.getOrDefault(agentId, new double[agent.getActionDim()]);
      agent.applyAction(action);
    }

    // Phase 3: Run process simulation
    process.run();

    // Phase 4: Get new global state
    currentGlobalState = getGlobalState();

    // Phase 5: Evaluate constraints
    sharedConstraints.evaluate(currentGlobalState);
    boolean constraintViolation = sharedConstraints.hasHardViolation();

    // Phase 6: Compute rewards
    Map<String, Double> rewards = new LinkedHashMap<>();
    if (coordinationMode == CoordinationMode.COOPERATIVE) {
      // Shared team reward
      double teamReward = computeTeamReward(currentGlobalState, actions);
      for (String agentId : agentOrder) {
        rewards.put(agentId, teamReward);
      }
    } else {
      // Individual rewards
      for (String agentId : agentOrder) {
        Agent agent = agents.get(agentId);
        double[] action = actions.getOrDefault(agentId, new double[agent.getActionDim()]);
        double reward = agent.computeReward(currentGlobalState, action);
        // Add constraint penalty
        if (constraintViolation) {
          reward -= 100.0;
        }
        rewards.put(agentId, reward);
      }
    }

    // Phase 7: Check termination
    terminated = constraintViolation;
    for (Agent agent : agents.values()) {
      if (agent.isTerminated(currentGlobalState)) {
        terminated = true;
        break;
      }
    }

    // Check truncation
    truncated = currentStep >= maxEpisodeSteps && !terminated;

    // Phase 8: Get new observations
    Map<String, double[]> observations = new LinkedHashMap<>();
    Map<String, Map<String, Object>> infos = new LinkedHashMap<>();

    for (String agentId : agentOrder) {
      Agent agent = agents.get(agentId);
      observations.put(agentId, agent.getLocalObservation(currentGlobalState));

      Map<String, Object> info = new HashMap<>();
      info.put("step", currentStep);
      info.put("constraint_violations", sharedConstraints.getViolations().size());
      infos.put(agentId, info);
    }

    return new MultiAgentStepResult(observations, rewards, terminated, truncated, infos,
        currentGlobalState);
  }

  /**
   * Get global state from process.
   *
   * @return global state vector
   */
  protected StateVector getGlobalState() {
    // Override in subclass to collect from all equipment
    StateVector state = new StateVector();
    // Basic state from first unit
    if (process.getUnitOperations().size() > 0) {
      var unit = process.getUnitOperations().get(0);
      if (unit.getFluid() != null) {
        state.add("pressure", unit.getFluid().getPressure("bar"), 0.0, 200.0, "bar");
        state.add("temperature", unit.getFluid().getTemperature("K"), 200.0, 500.0, "K");
      }
    }
    return state;
  }

  /**
   * Compute team reward for cooperative mode.
   *
   * @param state global state
   * @param actions all agent actions
   * @return team reward
   */
  protected double computeTeamReward(StateVector state, Map<String, double[]> actions) {
    double reward = 1.0; // Survival reward
    reward -= sharedConstraints.getTotalViolationPenalty();
    return reward;
  }

  /**
   * Check if episode is done.
   *
   * @return true if terminated or truncated
   */
  public boolean isDone() {
    return terminated || truncated;
  }

  /**
   * Get number of agents.
   *
   * @return agent count
   */
  public int getNumAgents() {
    return agents.size();
  }

  /**
   * Get agent IDs in order.
   *
   * @return list of agent IDs
   */
  public List<String> getAgentIds() {
    return new ArrayList<>(agentOrder);
  }

  /**
   * Get a specific agent.
   *
   * @param agentId agent ID
   * @return the agent
   */
  public Agent getAgent(String agentId) {
    return agents.get(agentId);
  }

  /**
   * Get current global state.
   *
   * @return current state
   */
  public StateVector getCurrentGlobalState() {
    return currentGlobalState;
  }

  /**
   * Get the process system.
   *
   * @return process system
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /**
   * Get current step.
   *
   * @return step count
   */
  public int getCurrentStep() {
    return currentStep;
  }
}
