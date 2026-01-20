package neqsim.process.ml.multiagent;

import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.ml.ConstraintManager;
import neqsim.process.ml.StateVector;

/**
 * Base class for process equipment control agents.
 *
 * <p>
 * Provides common functionality for agents that control process equipment:
 * <ul>
 * <li>Equipment reference and state extraction</li>
 * <li>Action bounds and clipping</li>
 * <li>Local constraint management</li>
 * <li>Setpoint tracking rewards</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public abstract class ProcessAgent implements Agent {
  private static final long serialVersionUID = 1000L;

  protected final String agentId;
  protected final ProcessEquipmentInterface equipment;
  protected final ConstraintManager localConstraints;

  // Observation/action configuration
  protected String[] observationNames;
  protected String[] actionNames;
  protected double[] actionLow;
  protected double[] actionHigh;

  // Setpoints
  protected Map<String, Double> setpoints;
  protected Map<String, Double> setpointWeights;

  /**
   * Create a process agent.
   *
   * @param agentId unique identifier
   * @param equipment controlled equipment
   */
  public ProcessAgent(String agentId, ProcessEquipmentInterface equipment) {
    this.agentId = agentId;
    this.equipment = equipment;
    this.localConstraints = new ConstraintManager();
    this.setpoints = new java.util.HashMap<>();
    this.setpointWeights = new java.util.HashMap<>();
  }

  @Override
  public String getAgentId() {
    return agentId;
  }

  @Override
  public int getObservationDim() {
    return observationNames != null ? observationNames.length : 0;
  }

  @Override
  public int getActionDim() {
    return actionNames != null ? actionNames.length : 0;
  }

  /**
   * Set a control setpoint.
   *
   * @param variableName variable name
   * @param value setpoint value
   * @param weight reward weight for tracking error
   * @return this agent for chaining
   */
  public ProcessAgent setSetpoint(String variableName, double value, double weight) {
    setpoints.put(variableName, value);
    setpointWeights.put(variableName, weight);
    return this;
  }

  /**
   * Get setpoint value.
   *
   * @param variableName variable name
   * @return setpoint value or NaN if not set
   */
  public double getSetpoint(String variableName) {
    return setpoints.getOrDefault(variableName, Double.NaN);
  }

  /**
   * Compute setpoint tracking reward.
   *
   * @param state current state
   * @return negative reward based on setpoint errors
   */
  protected double computeSetpointReward(StateVector state) {
    double reward = 0.0;
    for (Map.Entry<String, Double> entry : setpoints.entrySet()) {
      String varName = entry.getKey();
      double setpoint = entry.getValue();
      double weight = setpointWeights.getOrDefault(varName, 1.0);

      double value = state.getValue(varName);
      if (!Double.isNaN(value) && !Double.isNaN(setpoint)) {
        double error = value - setpoint;
        reward -= weight * error * error;
      }
    }
    return reward;
  }

  /**
   * Compute constraint penalty.
   *
   * @param state current state
   * @return negative reward for constraint violations
   */
  protected double computeConstraintPenalty(StateVector state) {
    localConstraints.evaluate(state);
    return -localConstraints.getTotalViolationPenalty();
  }

  @Override
  public double computeReward(StateVector globalState, double[] action) {
    double reward = 1.0; // Survival bonus
    reward += computeSetpointReward(globalState);
    reward += computeConstraintPenalty(globalState);
    return reward;
  }

  @Override
  public boolean isTerminated(StateVector globalState) {
    localConstraints.evaluate(globalState);
    return localConstraints.hasHardViolation();
  }

  /**
   * Get the controlled equipment.
   *
   * @return equipment
   */
  public ProcessEquipmentInterface getEquipment() {
    return equipment;
  }

  /**
   * Get local constraint manager.
   *
   * @return constraints
   */
  public ConstraintManager getLocalConstraints() {
    return localConstraints;
  }

  /**
   * Get observation names.
   *
   * @return observation variable names
   */
  public String[] getObservationNames() {
    return observationNames;
  }

  /**
   * Get action names.
   *
   * @return action variable names
   */
  public String[] getActionNames() {
    return actionNames;
  }

  /**
   * Get action lower bounds.
   *
   * @return lower bounds
   */
  public double[] getActionLow() {
    return actionLow;
  }

  /**
   * Get action upper bounds.
   *
   * @return upper bounds
   */
  public double[] getActionHigh() {
    return actionHigh;
  }
}
