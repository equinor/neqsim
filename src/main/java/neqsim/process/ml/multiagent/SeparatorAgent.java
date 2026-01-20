package neqsim.process.ml.multiagent;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.ml.StateVector;

/**
 * RL agent for separator level and pressure control.
 *
 * <p>
 * Controls:
 * <ul>
 * <li>Liquid outlet valve for level control</li>
 * <li>Gas outlet valve for pressure control (optional)</li>
 * </ul>
 *
 * <p>
 * Observations:
 * <ul>
 * <li>liquid_level - Current level [0-1]</li>
 * <li>pressure - Separator pressure [bar]</li>
 * <li>level_error - Deviation from level setpoint</li>
 * <li>pressure_error - Deviation from pressure setpoint</li>
 * <li>liquid_valve_pos - Liquid valve position [0-1]</li>
 * <li>gas_valve_pos - Gas valve position [0-1] (if controlled)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class SeparatorAgent extends ProcessAgent {
  private static final long serialVersionUID = 1000L;

  private final Separator separator;
  private ThrottlingValve liquidValve;
  private ThrottlingValve gasValve;
  private boolean controlGasValve = false;

  private double currentLiquidValvePos = 0.5;
  private double currentGasValvePos = 0.5;

  /**
   * Create separator agent with level control only.
   *
   * @param agentId agent ID
   * @param separator the separator
   * @param liquidValve liquid outlet valve
   */
  public SeparatorAgent(String agentId, Separator separator, ThrottlingValve liquidValve) {
    super(agentId, separator);
    this.separator = separator;
    this.liquidValve = liquidValve;
    this.controlGasValve = false;

    initializeSpaces();
  }

  /**
   * Create separator agent with level and pressure control.
   *
   * @param agentId agent ID
   * @param separator the separator
   * @param liquidValve liquid outlet valve
   * @param gasValve gas outlet valve
   */
  public SeparatorAgent(String agentId, Separator separator, ThrottlingValve liquidValve,
      ThrottlingValve gasValve) {
    super(agentId, separator);
    this.separator = separator;
    this.liquidValve = liquidValve;
    this.gasValve = gasValve;
    this.controlGasValve = true;

    initializeSpaces();
  }

  private void initializeSpaces() {
    // Observation space
    if (controlGasValve) {
      observationNames = new String[] {"liquid_level", "pressure", "level_error", "pressure_error",
          "liquid_valve_pos", "gas_valve_pos"};
    } else {
      observationNames =
          new String[] {"liquid_level", "pressure", "level_error", "liquid_valve_pos"};
    }

    // Action space: valve position changes [-0.1, 0.1]
    if (controlGasValve) {
      actionNames = new String[] {"liquid_valve_delta", "gas_valve_delta"};
      actionLow = new double[] {-0.1, -0.1};
      actionHigh = new double[] {0.1, 0.1};
    } else {
      actionNames = new String[] {"liquid_valve_delta"};
      actionLow = new double[] {-0.1};
      actionHigh = new double[] {0.1};
    }

    // Default setpoints
    setSetpoint("liquid_level", 0.5, 10.0);
    if (controlGasValve) {
      setSetpoint("pressure", 50.0, 5.0);
    }

    // Default constraints
    localConstraints.addHardRange("level_critical", "liquid_level", 0.05, 0.95, "fraction");
    localConstraints.addSoftRange("level_optimal", "liquid_level", 0.2, 0.8, "fraction");
    localConstraints.addHardRange("pressure_max", "pressure", 0.0, 100.0, "bar");
  }

  @Override
  public double[] getLocalObservation(StateVector globalState) {
    double level = globalState.getValue("liquid_level");
    double pressure = globalState.getValue("pressure");
    double levelSP = getSetpoint("liquid_level");
    double pressureSP = getSetpoint("pressure");

    double levelError = Double.isNaN(levelSP) ? 0.0 : (level - levelSP) / 0.5;
    double pressureError = Double.isNaN(pressureSP) ? 0.0 : (pressure - pressureSP) / 20.0;

    if (controlGasValve) {
      return new double[] {level, pressure / 100.0, levelError, pressureError,
          currentLiquidValvePos, currentGasValvePos};
    } else {
      return new double[] {level, pressure / 100.0, levelError, currentLiquidValvePos};
    }
  }

  @Override
  public void applyAction(double[] action) {
    // Apply liquid valve change
    if (action.length > 0) {
      currentLiquidValvePos = Math.max(0.0, Math.min(1.0, currentLiquidValvePos + action[0]));
      applyValvePosition(liquidValve, currentLiquidValvePos);
    }

    // Apply gas valve change
    if (controlGasValve && action.length > 1) {
      currentGasValvePos = Math.max(0.0, Math.min(1.0, currentGasValvePos + action[1]));
      applyValvePosition(gasValve, currentGasValvePos);
    }
  }

  private void applyValvePosition(ThrottlingValve valve, double position) {
    if (valve != null) {
      // Map position [0,1] to pressure drop
      // Position 0 = high pressure drop (mostly closed)
      // Position 1 = low pressure drop (fully open)
      double basePressure = 2.0; // Minimum outlet pressure
      double maxDrop = 10.0; // Maximum additional pressure drop
      double outletPressure = basePressure + position * maxDrop;
      valve.setOutletPressure(outletPressure);
    }
  }

  @Override
  public double computeReward(StateVector globalState, double[] action) {
    double reward = super.computeReward(globalState, action);

    // Action smoothness penalty
    if (action.length > 0) {
      reward -= 0.1 * action[0] * action[0];
    }
    if (action.length > 1) {
      reward -= 0.1 * action[1] * action[1];
    }

    return reward;
  }

  /**
   * Set level setpoint.
   *
   * @param level target level [0-1]
   */
  public void setLevelSetpoint(double level) {
    setSetpoint("liquid_level", level, 10.0);
  }

  /**
   * Set pressure setpoint.
   *
   * @param pressure target pressure [bar]
   */
  public void setPressureSetpoint(double pressure) {
    setSetpoint("pressure", pressure, 5.0);
  }

  /**
   * Get the separator.
   *
   * @return separator
   */
  public Separator getSeparator() {
    return separator;
  }
}
