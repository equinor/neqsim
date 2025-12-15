package neqsim.process.ml;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.ml.controllers.Controller;

/**
 * Runner for testing RL environments with simple controllers from Java.
 *
 * <p>
 * This class allows testing the ML infrastructure without Python by running episodes with simple
 * control policies (P, PID, bang-bang, random).
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * SeparatorGymEnv env = new SeparatorGymEnv();
 * Controller controller = new PIDController("LevelPID", 6, 0.5, 0.1, 0.05, -0.1, 0.1, 1.0);
 *
 * EpisodeRunner runner = new EpisodeRunner(env);
 * EpisodeResult result = runner.runEpisode(controller, 500);
 *
 * System.out.println("Total reward: " + result.totalReward);
 * System.out.println("Steps: " + result.steps);
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class EpisodeRunner implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final GymEnvironment environment;
  private boolean verbose = false;
  private int printInterval = 50;

  /**
   * Result of running an episode.
   */
  public static class EpisodeResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Total reward accumulated. */
    public final double totalReward;
    /** Number of steps taken. */
    public final int steps;
    /** Whether episode terminated (vs truncated). */
    public final boolean terminated;
    /** History of observations. */
    public final List<double[]> observations;
    /** History of actions. */
    public final List<double[]> actions;
    /** History of rewards. */
    public final List<Double> rewards;
    /** Final observation. */
    public final double[] finalObservation;

    /**
     * Constructor.
     *
     * @param totalReward total reward
     * @param steps steps taken
     * @param terminated episode terminated
     * @param observations observation history
     * @param actions action history
     * @param rewards reward history
     * @param finalObservation final observation
     */
    public EpisodeResult(double totalReward, int steps, boolean terminated,
        List<double[]> observations, List<double[]> actions, List<Double> rewards,
        double[] finalObservation) {
      this.totalReward = totalReward;
      this.steps = steps;
      this.terminated = terminated;
      this.observations = observations;
      this.actions = actions;
      this.rewards = rewards;
      this.finalObservation = finalObservation;
    }

    /**
     * Get mean reward per step.
     *
     * @return mean reward
     */
    public double getMeanReward() {
      return steps > 0 ? totalReward / steps : 0.0;
    }

    /**
     * Get observation at specific index.
     *
     * @param index observation index
     * @param featureIndex feature index within observation
     * @return feature value
     */
    public double getObservation(int index, int featureIndex) {
      if (index < observations.size() && featureIndex < observations.get(index).length) {
        return observations.get(index)[featureIndex];
      }
      return Double.NaN;
    }

    /**
     * Get feature trajectory as array.
     *
     * @param featureIndex feature index
     * @return array of feature values over time
     */
    public double[] getFeatureTrajectory(int featureIndex) {
      double[] trajectory = new double[observations.size()];
      for (int i = 0; i < observations.size(); i++) {
        trajectory[i] =
            featureIndex < observations.get(i).length ? observations.get(i)[featureIndex]
                : Double.NaN;
      }
      return trajectory;
    }
  }

  /**
   * Statistics from multiple episode runs.
   */
  public static class BenchmarkResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Controller name. */
    public final String controllerName;
    /** Number of episodes. */
    public final int numEpisodes;
    /** Mean total reward. */
    public final double meanReward;
    /** Standard deviation of reward. */
    public final double stdReward;
    /** Mean episode length. */
    public final double meanLength;
    /** Success rate (completed without termination). */
    public final double successRate;
    /** Min reward. */
    public final double minReward;
    /** Max reward. */
    public final double maxReward;

    /**
     * Constructor.
     *
     * @param controllerName controller name
     * @param numEpisodes number of episodes
     * @param meanReward mean reward
     * @param stdReward std reward
     * @param meanLength mean length
     * @param successRate success rate
     * @param minReward min reward
     * @param maxReward max reward
     */
    public BenchmarkResult(String controllerName, int numEpisodes, double meanReward,
        double stdReward, double meanLength, double successRate, double minReward,
        double maxReward) {
      this.controllerName = controllerName;
      this.numEpisodes = numEpisodes;
      this.meanReward = meanReward;
      this.stdReward = stdReward;
      this.meanLength = meanLength;
      this.successRate = successRate;
      this.minReward = minReward;
      this.maxReward = maxReward;
    }

    @Override
    public String toString() {
      return String.format("%s: reward=%.2f±%.2f, length=%.1f, success=%.1f%%, range=[%.2f, %.2f]",
          controllerName, meanReward, stdReward, meanLength, successRate * 100, minReward,
          maxReward);
    }
  }

  /**
   * Create episode runner.
   *
   * @param environment the Gym environment
   */
  public EpisodeRunner(GymEnvironment environment) {
    this.environment = environment;
  }

  /**
   * Set verbose mode.
   *
   * @param verbose whether to print progress
   * @return this runner
   */
  public EpisodeRunner setVerbose(boolean verbose) {
    this.verbose = verbose;
    return this;
  }

  /**
   * Set print interval for verbose mode.
   *
   * @param interval steps between prints
   * @return this runner
   */
  public EpisodeRunner setPrintInterval(int interval) {
    this.printInterval = interval;
    return this;
  }

  /**
   * Run a single episode with given controller.
   *
   * @param controller the controller to use
   * @param maxSteps maximum steps (overrides env setting if smaller)
   * @return episode result
   */
  public EpisodeResult runEpisode(Controller controller, int maxSteps) {
    controller.reset();

    List<double[]> observations = new ArrayList<>();
    List<double[]> actions = new ArrayList<>();
    List<Double> rewards = new ArrayList<>();

    GymEnvironment.ResetResult resetResult = environment.reset();
    double[] obs = resetResult.observation;
    observations.add(obs.clone());

    double totalReward = 0.0;
    int steps = 0;
    boolean terminated = false;

    while (steps < maxSteps) {
      // Get action from controller
      double[] action = controller.computeAction(obs);
      actions.add(action.clone());

      // Step environment
      GymEnvironment.StepResult stepResult = environment.step(action);
      obs = stepResult.observation;
      observations.add(obs.clone());
      rewards.add(stepResult.reward);

      totalReward += stepResult.reward;
      steps++;

      if (verbose && steps % printInterval == 0) {
        System.out.printf("Step %d: reward=%.3f, total=%.2f%n", steps, stepResult.reward,
            totalReward);
      }

      if (stepResult.terminated) {
        terminated = true;
        break;
      }
      if (stepResult.truncated) {
        break;
      }
    }

    return new EpisodeResult(totalReward, steps, terminated, observations, actions, rewards, obs);
  }

  /**
   * Run multiple episodes and compute statistics.
   *
   * @param controller the controller to use
   * @param numEpisodes number of episodes
   * @param maxStepsPerEpisode max steps per episode
   * @return benchmark result
   */
  public BenchmarkResult benchmark(Controller controller, int numEpisodes, int maxStepsPerEpisode) {
    double[] rewards = new double[numEpisodes];
    double[] lengths = new double[numEpisodes];
    int successes = 0;

    for (int i = 0; i < numEpisodes; i++) {
      EpisodeResult result = runEpisode(controller, maxStepsPerEpisode);
      rewards[i] = result.totalReward;
      lengths[i] = result.steps;
      if (!result.terminated) {
        successes++;
      }

      if (verbose) {
        System.out.printf("Episode %d/%d: reward=%.2f, steps=%d, terminated=%b%n", i + 1,
            numEpisodes, result.totalReward, result.steps, result.terminated);
      }
    }

    // Compute statistics
    double meanReward = mean(rewards);
    double stdReward = std(rewards);
    double meanLength = mean(lengths);
    double successRate = (double) successes / numEpisodes;
    double minReward = min(rewards);
    double maxReward = max(rewards);

    return new BenchmarkResult(controller.getName(), numEpisodes, meanReward, stdReward, meanLength,
        successRate, minReward, maxReward);
  }

  /**
   * Compare multiple controllers.
   *
   * @param controllers list of controllers
   * @param numEpisodes episodes per controller
   * @param maxStepsPerEpisode max steps
   * @return list of benchmark results
   */
  public List<BenchmarkResult> compareControllers(List<Controller> controllers, int numEpisodes,
      int maxStepsPerEpisode) {
    List<BenchmarkResult> results = new ArrayList<>();

    for (Controller controller : controllers) {
      if (verbose) {
        System.out.println("\n--- Testing: " + controller.getName() + " ---");
      }
      BenchmarkResult result = benchmark(controller, numEpisodes, maxStepsPerEpisode);
      results.add(result);
    }

    return results;
  }

  /**
   * Print comparison table.
   *
   * @param results benchmark results to compare
   */
  public static void printComparison(List<BenchmarkResult> results) {
    System.out.println("\n" + repeatString("=", 80));
    System.out.println("Controller Comparison");
    System.out.println(repeatString("=", 80));
    System.out.printf("%-20s %10s %10s %10s %10s%n", "Controller", "Mean", "Std", "Success%",
        "Length");
    System.out.println(repeatString("-", 80));

    for (BenchmarkResult r : results) {
      System.out.printf("%-20s %10.2f %10.2f %10.1f %10.1f%n", r.controllerName, r.meanReward,
          r.stdReward, r.successRate * 100, r.meanLength);
    }
    System.out.println(repeatString("=", 80));
  }

  /**
   * Repeat a string n times (Java 8 compatible replacement for String.repeat()).
   *
   * @param str string to repeat
   * @param count number of times to repeat
   * @return repeated string
   */
  private static String repeatString(String str, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(str);
    }
    return sb.toString();
  }

  // Helper methods
  private static double mean(double[] arr) {
    double sum = 0;
    for (double v : arr) {
      sum += v;
    }
    return sum / arr.length;
  }

  private static double std(double[] arr) {
    double m = mean(arr);
    double sumSq = 0;
    for (double v : arr) {
      sumSq += (v - m) * (v - m);
    }
    return Math.sqrt(sumSq / arr.length);
  }

  private static double min(double[] arr) {
    double m = arr[0];
    for (double v : arr) {
      if (v < m) {
        m = v;
      }
    }
    return m;
  }

  private static double max(double[] arr) {
    double m = arr[0];
    for (double v : arr) {
      if (v > m) {
        m = v;
      }
    }
    return m;
  }
}
