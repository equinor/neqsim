package neqsim.process.ml.controllers;

import java.io.Serializable;

/**
 * Interface for simple control policies that can be used to test RL environments from Java.
 *
 * <p>
 * These controllers provide baselines for comparison with trained RL agents.
 *
 * @author ESOL
 * @version 1.0
 */
public interface Controller extends Serializable {

  /**
   * Compute control action based on observation.
   *
   * @param observation normalized observation array
   * @return action array
   */
  double[] computeAction(double[] observation);

  /**
   * Reset controller state (for stateful controllers like PID).
   */
  default void reset() {}

  /**
   * Get controller name.
   *
   * @return name
   */
  String getName();
}
