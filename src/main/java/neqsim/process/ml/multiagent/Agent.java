package neqsim.process.ml.multiagent;

import java.io.Serializable;
import neqsim.process.ml.StateVector;

/**
 * Interface for an individual agent in a multi-agent system.
 *
 * <p>
 * Each agent controls a subset of process equipment and can communicate with other agents through
 * shared observations or explicit messages.
 *
 * @author ESOL
 * @version 1.0
 */
public interface Agent extends Serializable {

  /**
   * Get the agent's unique identifier.
   *
   * @return agent ID
   */
  String getAgentId();

  /**
   * Get the dimension of this agent's observation space.
   *
   * @return observation dimension
   */
  int getObservationDim();

  /**
   * Get the dimension of this agent's action space.
   *
   * @return action dimension
   */
  int getActionDim();

  /**
   * Get the agent's local observation from global state.
   *
   * @param globalState full system state
   * @return local observation array
   */
  double[] getLocalObservation(StateVector globalState);

  /**
   * Apply agent's action to the process.
   *
   * @param action action array
   */
  void applyAction(double[] action);

  /**
   * Compute agent's local reward.
   *
   * @param globalState current global state
   * @param action action taken
   * @return local reward
   */
  double computeReward(StateVector globalState, double[] action);

  /**
   * Check if agent's local termination condition is met.
   *
   * @param globalState current global state
   * @return true if agent triggers termination
   */
  default boolean isTerminated(StateVector globalState) {
    return false;
  }

  /**
   * Get message to broadcast to other agents.
   *
   * @param globalState current state
   * @return message array (can be empty)
   */
  default double[] getMessage(StateVector globalState) {
    return new double[0];
  }

  /**
   * Receive messages from other agents.
   *
   * @param messages map of agent ID to message
   */
  default void receiveMessages(java.util.Map<String, double[]> messages) {
    // Default: ignore messages
  }
}
