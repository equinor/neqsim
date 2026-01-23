package neqsim.process.ml.examples;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.ml.GymEnvironment;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Gymnasium-compatible separator control environment.
 *
 * <p>
 * This environment wraps a NeqSim separator model with a Gym-compatible API that works directly
 * with Python RL frameworks via JPype/Py4J.
 *
 * <h2>State Space (8-dimensional, continuous):</h2>
 * <ul>
 * <li>[0] liquid_level - Liquid level fraction [0, 1]</li>
 * <li>[1] pressure - Normalized pressure [0, 1]</li>
 * <li>[2] temperature - Normalized temperature [0, 1]</li>
 * <li>[3] feed_flow - Normalized feed flow [0, 1]</li>
 * <li>[4] gas_density - Normalized gas density</li>
 * <li>[5] liquid_density - Normalized liquid density</li>
 * <li>[6] level_error - Setpoint tracking error [-1, 1]</li>
 * <li>[7] valve_position - Current valve position [0, 1]</li>
 * </ul>
 *
 * <h2>Action Space (1-dimensional, continuous):</h2>
 * <ul>
 * <li>[0] valve_delta - Valve position change [-0.1, 0.1]</li>
 * </ul>
 *
 * <h2>Reward:</h2>
 * <ul>
 * <li>Negative squared level error: -10 * (level - setpoint)²</li>
 * <li>Action penalty: -0.1 * action²</li>
 * <li>Survival bonus: +1.0 per step</li>
 * <li>Constraint penalty: -100 for hard constraint violation</li>
 * </ul>
 *
 * <h2>Python Usage:</h2>
 *
 * <pre>
 * {@code
 * from jpype import JClass
 * import numpy as np
 *
 * SeparatorGymEnv = JClass('neqsim.process.ml.examples.SeparatorGymEnv')
 * env = SeparatorGymEnv()
 * env.setMaxEpisodeSteps(500)
 *
 * obs, info = env.reset().observation, env.reset().info
 * done = False
 * while not done:
 *     action = policy.predict(np.array(obs))
 *     result = env.step([float(action[0])])
 *     obs, reward = result.observation, result.reward
 *     done = result.terminated or result.truncated
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class SeparatorGymEnv extends GymEnvironment {
  private static final long serialVersionUID = 1000L;

  private ProcessSystem process;
  private Stream feed;
  private Separator separator;
  private ThrottlingValve liquidValve;

  // State variables
  private double liquidLevel = 0.5;
  private double valvePosition = 0.5;
  private double levelSetpoint = 0.5;

  // Bounds for normalization
  private double maxPressure = 100.0; // bar
  private double minTemp = 250.0;
  private double maxTemp = 350.0;
  private double maxFeedFlow = 200.0; // kg/hr

  /**
   * Create separator gym environment with default parameters.
   */
  public SeparatorGymEnv() {
    initializeSpaces();
    initializeProcess();
  }

  private void initializeSpaces() {
    envId = "NeqSim-Separator-v1";

    // Observation space: 8 dimensions
    observationDim = 8;
    observationLow = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0};
    observationHigh = new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};

    // Action space: 1 dimension (valve delta)
    actionDim = 1;
    actionLow = new double[] {-0.1};
    actionHigh = new double[] {0.1};

    maxEpisodeSteps = 500;
  }

  private void initializeProcess() {
    // Create a simple separation process
    SystemInterface fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    feed = new Stream("Feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");

    separator = new Separator("Separator", feed);

    Stream liquidOut = new Stream("Liquid Out", separator.getLiquidOutStream());
    liquidValve = new ThrottlingValve("LCV", liquidOut);
    liquidValve.setOutletPressure(20.0);

    Stream gasOut = new Stream("Gas Out", separator.getGasOutStream());

    process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(liquidValve);
    process.add(liquidOut);
    process.add(gasOut);
  }

  @Override
  protected double[] resetInternal(Map<String, Object> options) {
    // Reset to random initial state
    liquidLevel = 0.3 + Math.random() * 0.4; // [0.3, 0.7]
    valvePosition = 0.4 + Math.random() * 0.2; // [0.4, 0.6]

    // Randomize setpoint if options provided
    if (options != null && options.containsKey("setpoint")) {
      levelSetpoint = (Double) options.get("setpoint");
    } else {
      levelSetpoint = 0.5;
    }

    // Randomize feed conditions
    double feedFlow = 80.0 + Math.random() * 40.0;
    feed.setFlowRate(feedFlow, "kg/hr");
    double temp = minTemp + Math.random() * (maxTemp - minTemp);
    feed.setTemperature(temp, "K");

    // Run initial simulation
    process.run();

    return getObservation();
  }

  @Override
  protected StepResult stepInternal(double[] action) {
    // Apply action (valve position change)
    double valveDelta = action[0];
    valvePosition = Math.max(0.05, Math.min(0.95, valvePosition + valveDelta));

    // Map valve position to outlet pressure (affects liquid outflow)
    double outletPressure = 10.0 + valvePosition * 30.0; // 10-40 bar
    liquidValve.setOutletPressure(outletPressure);

    // Simulate level dynamics (simplified)
    double feedRate = feed.getFluid().getFlowRate("kg/hr");
    double liquidOutRate = getLiquidOutflowRate();
    double levelChange = (feedRate * 0.3 - liquidOutRate) / 1000.0;
    liquidLevel = Math.max(0.0, Math.min(1.0, liquidLevel + levelChange));

    // Add some feed variability
    if (Math.random() < 0.1) {
      double newFeed = feed.getFluid().getFlowRate("kg/hr") * (0.9 + Math.random() * 0.2);
      feed.setFlowRate(Math.max(50.0, Math.min(150.0, newFeed)), "kg/hr");
    }

    // Run simulation
    process.run();

    // Compute reward
    double levelError = liquidLevel - levelSetpoint;
    double reward = 1.0; // Survival bonus
    reward -= 10.0 * levelError * levelError; // Setpoint tracking
    reward -= 0.1 * valveDelta * valveDelta; // Action smoothness

    // Check termination
    boolean terminated = false;
    Map<String, Object> info = new HashMap<>();
    info.put("liquid_level", liquidLevel);
    info.put("valve_position", valvePosition);
    info.put("level_error", levelError);

    // Hard constraint: level out of bounds
    if (liquidLevel < 0.05 || liquidLevel > 0.95) {
      reward -= 100.0;
      terminated = true;
      info.put("termination_reason", "level_out_of_bounds");
    }

    return new StepResult(getObservation(), reward, terminated, false, info);
  }

  private double[] getObservation() {
    SystemInterface fluid = separator.getFluid();

    double pressure = fluid != null ? fluid.getPressure("bar") / maxPressure : 0.5;
    double temperature =
        fluid != null ? (fluid.getTemperature("K") - minTemp) / (maxTemp - minTemp) : 0.5;
    double feedFlow = feed.getFluid().getFlowRate("kg/hr") / maxFeedFlow;

    double gasDensity = 0.5;
    double liquidDensity = 0.5;
    if (fluid != null && fluid.getNumberOfPhases() > 0) {
      gasDensity = Math.min(1.0, fluid.getPhase(0).getDensity("kg/m3") / 100.0);
      if (fluid.getNumberOfPhases() > 1) {
        liquidDensity = Math.min(1.0, fluid.getPhase(1).getDensity("kg/m3") / 800.0);
      }
    }

    double levelError = (liquidLevel - levelSetpoint) / 0.5; // Normalized error

    return new double[] {liquidLevel, pressure, temperature, feedFlow, gasDensity, liquidDensity,
        levelError, valvePosition};
  }

  private double getLiquidOutflowRate() {
    // Simplified: outflow depends on valve position and level
    return valvePosition * liquidLevel * 50.0; // kg/hr
  }

  /**
   * Set level setpoint.
   *
   * @param setpoint target level [0-1]
   */
  public void setLevelSetpoint(double setpoint) {
    this.levelSetpoint = Math.max(0.1, Math.min(0.9, setpoint));
  }

  /**
   * Get level setpoint.
   *
   * @return current setpoint
   */
  public double getLevelSetpoint() {
    return levelSetpoint;
  }

  /**
   * Get current liquid level.
   *
   * @return level [0-1]
   */
  public double getLiquidLevel() {
    return liquidLevel;
  }

  /**
   * Get current valve position.
   *
   * @return position [0-1]
   */
  public double getValvePosition() {
    return valvePosition;
  }

  /**
   * Get the underlying process system.
   *
   * @return process
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /**
   * Get observation feature names.
   *
   * @return feature names
   */
  public String[] getObservationNames() {
    return new String[] {"liquid_level", "pressure", "temperature", "feed_flow", "gas_density",
        "liquid_density", "level_error", "valve_position"};
  }

  /**
   * Get action names.
   *
   * @return action names
   */
  public String[] getActionNames() {
    return new String[] {"valve_delta"};
  }
}
