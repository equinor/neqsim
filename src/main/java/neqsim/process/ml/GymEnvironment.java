package neqsim.process.ml;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Gymnasium (OpenAI Gym) compatible environment interface for NeqSim.
 *
 * <p>
 * This class provides a standardized interface compatible with Python's Gymnasium library, enabling
 * seamless integration with popular RL frameworks like stable-baselines3, RLlib, and CleanRL.
 *
 * <h2>Python Usage via JPype:</h2>
 *
 * <pre>
 * {@code
 * import jpype
 * from jpype import JClass
 *
 * GymEnvironment = JClass('neqsim.process.ml.GymEnvironment')
 * env = MySeparatorEnv()  # extends GymEnvironment
 *
 * obs = env.reset()
 * for _ in range(1000):
 *     action = agent.predict(obs)
 *     obs, reward, terminated, truncated, info = env.step(action)
 *     if terminated or truncated:
 *         obs = env.reset()
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public abstract class GymEnvironment implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Observation space bounds. */
  protected double[] observationLow;
  protected double[] observationHigh;
  protected int observationDim;

  /** Action space bounds. */
  protected double[] actionLow;
  protected double[] actionHigh;
  protected int actionDim;

  /** Environment metadata. */
  protected String envId;
  protected int maxEpisodeSteps = 1000;
  protected double rewardThreshold = Double.NaN;

  /** Episode state. */
  protected int currentStep = 0;
  protected double episodeReward = 0.0;
  protected boolean terminated = false;
  protected boolean truncated = false;

  /**
   * Step result matching Gymnasium API.
   */
  public static class StepResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    public final double[] observation;
    public final double reward;
    public final boolean terminated;
    public final boolean truncated;
    public final Map<String, Object> info;

    /**
     * Constructor.
     *
     * @param observation next observation
     * @param reward step reward
     * @param terminated episode ended due to task completion/failure
     * @param truncated episode ended due to time limit
     * @param info additional info dictionary
     */
    public StepResult(double[] observation, double reward, boolean terminated, boolean truncated,
        Map<String, Object> info) {
      this.observation = observation;
      this.reward = reward;
      this.terminated = terminated;
      this.truncated = truncated;
      this.info = info != null ? info : new HashMap<>();
    }
  }

  /**
   * Reset result matching Gymnasium API.
   */
  public static class ResetResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    public final double[] observation;
    public final Map<String, Object> info;

    /**
     * Constructor.
     *
     * @param observation initial observation
     * @param info additional info dictionary
     */
    public ResetResult(double[] observation, Map<String, Object> info) {
      this.observation = observation;
      this.info = info != null ? info : new HashMap<>();
    }
  }

  /**
   * Reset the environment to initial state.
   *
   * <p>
   * Gymnasium API: {@code obs, info = env.reset()}
   *
   * @return ResetResult with initial observation and info
   */
  public ResetResult reset() {
    return reset(null, null);
  }

  /**
   * Reset the environment with optional seed and options.
   *
   * @param seed random seed for reproducibility (nullable)
   * @param options additional reset options (nullable)
   * @return ResetResult with initial observation and info
   */
  public ResetResult reset(Long seed, Map<String, Object> options) {
    currentStep = 0;
    episodeReward = 0.0;
    terminated = false;
    truncated = false;

    if (seed != null) {
      setSeed(seed);
    }

    double[] obs = resetInternal(options);
    Map<String, Object> info = new HashMap<>();
    info.put("episode_step", 0);

    return new ResetResult(obs, info);
  }

  /**
   * Take a step in the environment.
   *
   * <p>
   * Gymnasium API: {@code obs, reward, terminated, truncated, info = env.step(action)}
   *
   * @param action action array (continuous values)
   * @return StepResult with next observation, reward, termination flags, and info
   */
  public StepResult step(double[] action) {
    currentStep++;

    // Clip action to valid range
    double[] clippedAction = clipAction(action);

    // Execute step
    StepResult result = stepInternal(clippedAction);

    // Update episode state
    episodeReward += result.reward;
    terminated = result.terminated;

    // Check truncation (time limit)
    truncated = currentStep >= maxEpisodeSteps && !terminated;

    // Add episode info
    Map<String, Object> info = new HashMap<>(result.info);
    info.put("episode_step", currentStep);
    info.put("episode_reward", episodeReward);

    if (terminated || truncated) {
      Map<String, Object> episodeInfo = new HashMap<>();
      episodeInfo.put("r", episodeReward);
      episodeInfo.put("l", currentStep);
      episodeInfo.put("t", truncated);
      info.put("episode", episodeInfo);
    }

    return new StepResult(result.observation, result.reward, terminated, truncated, info);
  }

  /**
   * Clip action to valid bounds.
   *
   * @param action raw action
   * @return clipped action
   */
  protected double[] clipAction(double[] action) {
    double[] clipped = new double[actionDim];
    for (int i = 0; i < actionDim; i++) {
      double val = i < action.length ? action[i] : 0.0;
      clipped[i] = Math.max(actionLow[i], Math.min(actionHigh[i], val));
    }
    return clipped;
  }

  /**
   * Internal reset implementation. Override in subclass.
   *
   * @param options reset options
   * @return initial observation
   */
  protected abstract double[] resetInternal(Map<String, Object> options);

  /**
   * Internal step implementation. Override in subclass.
   *
   * @param action clipped action
   * @return step result
   */
  protected abstract StepResult stepInternal(double[] action);

  /**
   * Set random seed for reproducibility.
   *
   * @param seed random seed
   */
  protected void setSeed(long seed) {
    // Override in subclass if needed
  }

  // ==================== Space Definitions ====================

  /**
   * Get observation space dimension.
   *
   * @return observation dimension
   */
  public int getObservationDim() {
    return observationDim;
  }

  /**
   * Get action space dimension.
   *
   * @return action dimension
   */
  public int getActionDim() {
    return actionDim;
  }

  /**
   * Get observation space lower bounds.
   *
   * @return lower bounds array
   */
  public double[] getObservationLow() {
    return observationLow;
  }

  /**
   * Get observation space upper bounds.
   *
   * @return upper bounds array
   */
  public double[] getObservationHigh() {
    return observationHigh;
  }

  /**
   * Get action space lower bounds.
   *
   * @return lower bounds array
   */
  public double[] getActionLow() {
    return actionLow;
  }

  /**
   * Get action space upper bounds.
   *
   * @return upper bounds array
   */
  public double[] getActionHigh() {
    return actionHigh;
  }

  /**
   * Get environment ID.
   *
   * @return environment identifier
   */
  public String getEnvId() {
    return envId;
  }

  /**
   * Get maximum episode steps.
   *
   * @return max steps
   */
  public int getMaxEpisodeSteps() {
    return maxEpisodeSteps;
  }

  /**
   * Set maximum episode steps.
   *
   * @param maxSteps max steps
   */
  public void setMaxEpisodeSteps(int maxSteps) {
    this.maxEpisodeSteps = maxSteps;
  }

  /**
   * Check if environment is done (terminated or truncated).
   *
   * @return true if episode ended
   */
  public boolean isDone() {
    return terminated || truncated;
  }

  /**
   * Get current episode step.
   *
   * @return step count
   */
  public int getCurrentStep() {
    return currentStep;
  }

  /**
   * Get cumulative episode reward.
   *
   * @return total reward
   */
  public double getEpisodeReward() {
    return episodeReward;
  }
}
