package neqsim.process.ml.examples;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.ml.ActionVector;
import neqsim.process.ml.Constraint;
import neqsim.process.ml.RLEnvironment;
import neqsim.process.ml.StateVector;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example RL environment for separator level control.
 *
 * <p>
 * This demonstrates how to wrap a NeqSim process in an RL-compatible interface for training control
 * policies. The agent learns to maintain liquid level at setpoint by controlling the liquid outlet
 * valve.
 *
 * <h2>State Space:</h2>
 * <ul>
 * <li>liquid_level: Current level fraction [0, 1]</li>
 * <li>pressure: Separator pressure [bar]</li>
 * <li>temperature: Separator temperature [K]</li>
 * <li>feed_flow: Inlet flow rate [kg/s]</li>
 * <li>gas_density: Gas phase density [kg/m³]</li>
 * <li>liquid_density: Liquid phase density [kg/m³]</li>
 * <li>level_error: Deviation from setpoint [-1, 1]</li>
 * <li>pressure_error: Deviation from pressure setpoint [-1, 1]</li>
 * </ul>
 *
 * <h2>Action Space:</h2>
 * <ul>
 * <li>valve_opening: Liquid outlet valve position [0, 1]</li>
 * </ul>
 *
 * <h2>Reward:</h2>
 * <ul>
 * <li>Negative quadratic penalty for level error</li>
 * <li>Negative penalty for constraint violations</li>
 * <li>Positive reward for stability (low action changes)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class SeparatorLevelControlEnv extends RLEnvironment {
  private static final long serialVersionUID = 1000L;

  private Separator separator;
  private ThrottlingValve liquidValve;
  private Stream feedStream;

  private double levelSetpoint = 0.5; // Target level fraction
  private double pressureSetpoint = 50.0; // Target pressure [bar]
  private double previousAction = 0.5;

  /**
   * Create a separator level control environment with default process.
   */
  public SeparatorLevelControlEnv() {
    super(createDefaultProcess());
    initializeComponents();
  }

  /**
   * Create a separator level control environment with custom process.
   *
   * @param process the process system containing a separator
   * @param separatorName name of the separator in the process
   * @param valveName name of the liquid outlet valve
   * @param feedName name of the feed stream
   */
  public SeparatorLevelControlEnv(ProcessSystem process, String separatorName, String valveName,
      String feedName) {
    super(process);
    this.separator = (Separator) process.getUnit(separatorName);
    this.liquidValve = (ThrottlingValve) process.getUnit(valveName);
    this.feedStream = (Stream) process.getUnit(feedName);
    initializeComponents();
  }

  private static ProcessSystem createDefaultProcess() {
    // Create a simple gas-liquid separation process
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("n-pentane", 2.0);
    fluid.addComponent("n-hexane", 10.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(298.15, "K");
    feed.setPressure(50.0, "bar");

    Separator separator = new Separator("separator", feed);

    ThrottlingValve liquidValve =
        new ThrottlingValve("liquidValve", separator.getLiquidOutStream());
    liquidValve.setOutletPressure(10.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(liquidValve);

    return process;
  }

  private void initializeComponents() {
    // Find components if not already set
    ProcessSystem process = getProcess();
    if (separator == null) {
      separator = (Separator) process.getUnit("separator");
    }
    if (liquidValve == null) {
      liquidValve = (ThrottlingValve) process.getUnit("liquidValve");
    }
    if (feedStream == null) {
      feedStream = (Stream) process.getUnit("feed");
    }

    // Define action space
    defineAction("valve_opening", 0.0, 1.0, "fraction");

    // Add constraints
    getConstraintManager().add(new Constraint("max_pressure", "Separator over-pressure protection",
        Constraint.Type.HARD, Constraint.Category.SAFETY, "pressure", 0.0, 70.0, "bar"));

    getConstraintManager()
        .add(new Constraint("min_pressure", "Separator minimum pressure", Constraint.Type.SOFT,
            Constraint.Category.OPERATIONAL, "pressure", 30.0, Double.POSITIVE_INFINITY, "bar"));

    getConstraintManager()
        .add(new Constraint("level_range", "Separator level operating range", Constraint.Type.SOFT,
            Constraint.Category.OPERATIONAL, "liquid_level", 0.2, 0.8, "fraction"));

    getConstraintManager().add(new Constraint("level_critical", "Critical level limits",
        Constraint.Type.HARD, Constraint.Category.SAFETY, "liquid_level", 0.05, 0.95, "fraction"));

    // Set reward weights
    setRewardWeights(0.01, 10.0, 100.0, 0.1);
    setTimeStep(1.0);
    setMaxEpisodeTime(300.0);
  }

  /**
   * Set the level setpoint.
   *
   * @param setpoint target level fraction [0, 1]
   */
  public void setLevelSetpoint(double setpoint) {
    this.levelSetpoint = Math.max(0.1, Math.min(0.9, setpoint));
  }

  /**
   * Set the pressure setpoint.
   *
   * @param setpoint target pressure [bar]
   */
  public void setPressureSetpoint(double setpoint) {
    this.pressureSetpoint = setpoint;
  }

  @Override
  protected void applyAction(ActionVector action) {
    double valveOpening = action.get("valve_opening");

    // Apply valve opening (affects outlet flow)
    // In a real dynamic simulation, this would affect the liquid outlet rate
    // For now, we simulate the effect by adjusting outlet pressure
    double outletPressure = 5.0 + valveOpening * 10.0; // 5-15 bar range
    liquidValve.setOutletPressure(outletPressure);

    previousAction = valveOpening;
  }

  @Override
  protected StateVector getObservation() {
    StateVector state = new StateVector();

    // Get process states
    double pressure = 50.0; // Default
    double temperature = 298.15; // Default
    double gasDensity = 50.0;
    double liquidDensity = 600.0;
    double feedFlow = 1000.0 / 3600.0; // kg/s

    if (separator != null && separator.getFluid() != null) {
      pressure = separator.getPressure();
      temperature = separator.getTemperature();

      if (separator.getFluid().hasPhaseType("gas")) {
        gasDensity = separator.getFluid().getPhase("gas").getDensity("kg/m3");
      }
      if (separator.getFluid().hasPhaseType("oil")) {
        liquidDensity = separator.getFluid().getPhase("oil").getDensity("kg/m3");
      }
    }

    if (feedStream != null) {
      feedFlow = feedStream.getFlowRate("kg/sec");
    }

    // Simulate liquid level (simplified dynamics)
    // In reality, this would come from mass balance integration
    double liquidLevel = 0.5 + 0.3 * (0.5 - previousAction);

    // Build state vector
    state.add("liquid_level", liquidLevel, 0.0, 1.0, "fraction");
    state.add("pressure", pressure, 0.0, 100.0, "bar");
    state.add("temperature", temperature, 200.0, 400.0, "K");
    state.add("feed_flow", feedFlow, 0.0, 1.0, "kg/s");
    state.add("gas_density", gasDensity, 0.0, 200.0, "kg/m3");
    state.add("liquid_density", liquidDensity, 400.0, 1000.0, "kg/m3");

    // Computed features (useful for RL)
    double levelError = (liquidLevel - levelSetpoint) / 0.5; // Normalized error
    double pressureError = (pressure - pressureSetpoint) / 20.0; // Normalized error

    state.add("level_error", levelError, -1.0, 1.0, "normalized");
    state.add("pressure_error", pressureError, -1.0, 1.0, "normalized");

    return state;
  }

  @Override
  protected double computeReward(StateVector state, ActionVector action, StepInfo info) {
    // Base reward from parent class (constraint penalties)
    double reward = super.computeReward(state, action, info);

    // Level tracking reward
    double levelError = state.getValue("level_error");
    reward -= 10.0 * levelError * levelError;

    // Pressure tracking reward (less important)
    double pressureError = state.getValue("pressure_error");
    reward -= 1.0 * pressureError * pressureError;

    // Action smoothness reward
    double actionChange = action.get("valve_opening") - previousAction;
    reward -= 0.1 * actionChange * actionChange;

    // Survival reward (small positive for staying alive)
    reward += 1.0;

    return reward;
  }

  /**
   * Get the separator being controlled.
   *
   * @return the separator
   */
  public Separator getSeparator() {
    return separator;
  }

  /**
   * Get the liquid outlet valve.
   *
   * @return the valve
   */
  public ThrottlingValve getLiquidValve() {
    return liquidValve;
  }

  /**
   * Demo main method.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    System.out.println("=== Separator Level Control RL Environment Demo ===\n");

    // Create environment
    SeparatorLevelControlEnv env = new SeparatorLevelControlEnv();
    env.setLevelSetpoint(0.6);

    // Reset and get initial observation
    StateVector obs = env.reset();
    System.out.println("Initial observation:");
    for (String name : obs.getFeatureNames()) {
      System.out.printf("  %s: %.4f (normalized: %.4f)%n", name, obs.getValue(name),
          obs.getNormalized(name));
    }

    System.out.println("\nAction space: " + env.getActionSpace());
    System.out.println("Constraints: " + env.getConstraintManager());

    // Simulate a few steps with random actions
    System.out.println("\n--- Simulation Steps ---");
    java.util.Random rand = new java.util.Random(42);

    for (int i = 0; i < 5; i++) {
      // Random action
      double valveAction = rand.nextDouble();
      ActionVector action = env.getActionSpace();
      action.setNormalized("valve_opening", valveAction);

      // Step environment
      RLEnvironment.StepResult result = env.step(action);

      System.out.printf("%nStep %d: action=%.2f, reward=%.2f, done=%s%n", i + 1, valveAction,
          result.reward, result.done);
      System.out.printf("  Level: %.4f, Error: %.4f%n", result.observation.getValue("liquid_level"),
          result.observation.getValue("level_error"));
      System.out.printf("  Constraint violations: %d%n",
          env.getConstraintManager().getViolations().size());

      if (result.done) {
        System.out.println("  Episode ended: " + result.info.violationExplanation);
        break;
      }
    }

    System.out.println("\n=== Demo Complete ===");
  }
}
