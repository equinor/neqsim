package neqsim.ml;

/**
 * Interface for process equipment that can export standardized state vectors.
 *
 * <p>
 * Implement this interface to enable:
 * <ul>
 * <li>Reinforcement Learning integration</li>
 * <li>Neural network surrogate model training</li>
 * <li>Multi-agent coordination</li>
 * <li>Real-time monitoring and control</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public interface StateVectorProvider {

  /**
   * Get the current state as a standardized vector.
   *
   * <p>
   * The state vector should include all observable variables relevant for control and monitoring.
   * Values should be in physical units with appropriate bounds.
   *
   * @return current state vector
   */
  StateVector getStateVector();

  /**
   * Get the dimension of the state vector.
   *
   * @return number of state variables
   */
  default int getStateDimension() {
    return getStateVector().size();
  }

  /**
   * Get human-readable names for state variables.
   *
   * @return array of feature names
   */
  default String[] getStateNames() {
    return getStateVector().getFeatureNames();
  }
}
