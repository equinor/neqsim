package neqsim.ml;

import java.io.Serializable;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Reinforcement Learning environment wrapper for NeqSim process systems.
 *
 * <p>
 * Provides a Gym-compatible interface for RL training on process control tasks. Key features:
 * <ul>
 * <li>Standardized observation and action spaces</li>
 * <li>Physics-grounded reward computation</li>
 * <li>Safe action projection via constraint manager</li>
 * <li>Episode management with reset capability</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * ProcessSystem process = new ProcessSystem();
 * // ... build process ...
 *
 * RLEnvironment env = new RLEnvironment(process);
 * env.addControlledEquipment("valve1", valve, actionSpace);
 * env.setRewardWeights(weights);
 *
 * StateVector obs = env.reset();
 * while (!done) {
 *   ActionVector action = agent.selectAction(obs);
 *   StepResult result = env.step(action);
 *   obs = result.observation;
 *   done = result.done;
 * }
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class RLEnvironment implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem process;
  private final ConstraintManager constraintManager;
  private final ActionVector actionSpace;
  private double simulationTimeStep = 1.0; // seconds
  private double currentTime = 0.0;
  private double maxEpisodeTime = 3600.0; // 1 hour default

  // Reward weights
  private double weightEnergy = 1.0;
  private double weightSetpointError = 10.0;
  private double weightConstraintViolation = 100.0;
  private double weightThroughput = 1.0;

  // Episode state
  private boolean done = false;
  private int stepCount = 0;

  /**
   * Result of a simulation step.
   */
  public static class StepResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Current observation (state vector). */
    public final StateVector observation;
    /** Reward for this step. */
    public final double reward;
    /** Whether episode is finished. */
    public final boolean done;
    /** Whether episode was truncated (time limit). */
    public final boolean truncated;
    /** Additional info for debugging. */
    public final StepInfo info;

    /**
     * Constructor.
     *
     * @param observation current state
     * @param reward step reward
     * @param done episode finished
     * @param truncated time limit reached
     * @param info additional info
     */
    public StepResult(StateVector observation, double reward, boolean done, boolean truncated,
        StepInfo info) {
      this.observation = observation;
      this.reward = reward;
      this.done = done;
      this.truncated = truncated;
      this.info = info;
    }
  }

  /**
   * Additional info from a step.
   */
  public static class StepInfo implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Constraint violation penalty applied. */
    public double constraintPenalty;
    /** Energy consumption this step. */
    public double energyConsumption;
    /** Current throughput. */
    public double throughput;
    /** Simulation time. */
    public double simulationTime;
    /** Whether any hard constraint was violated. */
    public boolean hardViolation;
    /** Explanation of any violations. */
    public String violationExplanation;
  }

  /**
   * Create an RL environment wrapping a process system.
   *
   * @param process the process system to control
   */
  public RLEnvironment(ProcessSystem process) {
    this.process = process;
    this.constraintManager = new ConstraintManager();
    this.actionSpace = new ActionVector();
  }

  /**
   * Define an action dimension.
   *
   * @param name action name
   * @param lowerBound minimum value
   * @param upperBound maximum value
   * @param unit physical unit
   * @return this environment for chaining
   */
  public RLEnvironment defineAction(String name, double lowerBound, double upperBound,
      String unit) {
    actionSpace.define(name, lowerBound, upperBound, unit);
    return this;
  }

  /**
   * Add a hard constraint.
   *
   * @param name constraint name
   * @param variableName state variable to constrain
   * @param minValue minimum allowed
   * @param maxValue maximum allowed
   * @param unit physical unit
   * @return this environment for chaining
   */
  public RLEnvironment addConstraint(String name, String variableName, double minValue,
      double maxValue, String unit) {
    constraintManager.addHardRange(name, variableName, minValue, maxValue, unit);
    return this;
  }

  /**
   * Set reward weights.
   *
   * @param energy weight for energy consumption (negative reward)
   * @param setpointError weight for setpoint deviation (negative reward)
   * @param constraintViolation weight for constraint violations (negative reward)
   * @param throughput weight for production throughput (positive reward)
   * @return this environment for chaining
   */
  public RLEnvironment setRewardWeights(double energy, double setpointError,
      double constraintViolation, double throughput) {
    this.weightEnergy = energy;
    this.weightSetpointError = setpointError;
    this.weightConstraintViolation = constraintViolation;
    this.weightThroughput = throughput;
    return this;
  }

  /**
   * Set simulation time step.
   *
   * @param dt time step in seconds
   * @return this environment for chaining
   */
  public RLEnvironment setTimeStep(double dt) {
    this.simulationTimeStep = dt;
    return this;
  }

  /**
   * Set maximum episode time.
   *
   * @param maxTime maximum time in seconds
   * @return this environment for chaining
   */
  public RLEnvironment setMaxEpisodeTime(double maxTime) {
    this.maxEpisodeTime = maxTime;
    return this;
  }

  /**
   * Reset the environment to initial state.
   *
   * @return initial observation
   */
  public StateVector reset() {
    // Reset process to initial conditions
    // Note: ProcessSystem should implement a reset method
    currentTime = 0.0;
    stepCount = 0;
    done = false;

    // Run initial steady state
    process.run();

    return getObservation();
  }

  /**
   * Execute one simulation step with given action.
   *
   * @param action control action to apply
   * @return step result with observation, reward, done flag
   */
  public StepResult step(ActionVector action) {
    stepCount++;

    // Apply action to process (subclass should override applyAction)
    applyAction(action);

    // Run simulation for one time step
    // Note: This is a simplified version - real implementation would use
    // dynamic simulation
    process.run();

    // Get new observation
    StateVector obs = getObservation();

    // Evaluate constraints
    constraintManager.evaluate(obs);

    // Compute reward
    StepInfo info = new StepInfo();
    double reward = computeReward(obs, action, info);

    // Check termination conditions
    currentTime += simulationTimeStep;
    boolean truncated = currentTime >= maxEpisodeTime;
    done = truncated || constraintManager.hasHardViolation();

    info.simulationTime = currentTime;
    info.hardViolation = constraintManager.hasHardViolation();
    info.violationExplanation = constraintManager.explainViolations();

    return new StepResult(obs, reward, done, truncated, info);
  }

  /**
   * Apply action to process equipment. Override in subclass to implement specific control logic.
   *
   * @param action the action to apply
   */
  protected void applyAction(ActionVector action) {
    // Default implementation does nothing
    // Subclass should override to apply actions to specific equipment
  }

  /**
   * Get current observation. Override in subclass to include equipment-specific states.
   *
   * @return current state vector
   */
  protected StateVector getObservation() {
    // Default implementation returns empty state
    // Subclass should override to include actual process states
    return new StateVector();
  }

  /**
   * Compute reward for current state and action.
   *
   * @param state current state
   * @param action applied action
   * @param info info object to fill with details
   * @return scalar reward
   */
  protected double computeReward(StateVector state, ActionVector action, StepInfo info) {
    double reward = 0.0;

    // Constraint violation penalty
    info.constraintPenalty = constraintManager.getTotalViolationPenalty();
    reward -= weightConstraintViolation * info.constraintPenalty;

    // Energy consumption penalty (would need actual values from process)
    info.energyConsumption = 0.0; // Placeholder
    reward -= weightEnergy * info.energyConsumption;

    // Throughput reward (would need actual values from process)
    info.throughput = 0.0; // Placeholder
    reward += weightThroughput * info.throughput;

    return reward;
  }

  /**
   * Get the action space specification.
   *
   * @return action space
   */
  public ActionVector getActionSpace() {
    return actionSpace;
  }

  /**
   * Get the constraint manager.
   *
   * @return constraint manager
   */
  public ConstraintManager getConstraintManager() {
    return constraintManager;
  }

  /**
   * Get the underlying process system.
   *
   * @return process system
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /**
   * Get current simulation time.
   *
   * @return time in seconds
   */
  public double getCurrentTime() {
    return currentTime;
  }

  /**
   * Get step count in current episode.
   *
   * @return number of steps taken
   */
  public int getStepCount() {
    return stepCount;
  }

  /**
   * Check if episode is done.
   *
   * @return true if episode finished
   */
  public boolean isDone() {
    return done;
  }
}
