package neqsim.process.ml.examples;

import java.util.Map;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.ml.ConstraintManager;
import neqsim.process.ml.StateVector;
import neqsim.process.ml.multiagent.CompressorAgent;
import neqsim.process.ml.multiagent.MultiAgentEnvironment;
import neqsim.process.ml.multiagent.SeparatorAgent;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Multi-agent example: Separator + Compressor train.
 *
 * <p>
 * Two coordinated agents:
 * <ul>
 * <li><b>SeparatorAgent:</b> Controls liquid level via outlet valve</li>
 * <li><b>CompressorAgent:</b> Controls discharge pressure via speed</li>
 * </ul>
 *
 * <p>
 * The agents must coordinate because:
 * <ul>
 * <li>Separator gas output feeds the compressor</li>
 * <li>Compressor suction pressure affects separator operation</li>
 * <li>Level control affects available gas flow to compressor</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * {@code
 * SeparatorCompressorMultiAgentEnv env = new SeparatorCompressorMultiAgentEnv();
 * env.setCoordinationMode(CoordinationMode.COOPERATIVE);
 *
 * Map<String, double[]> obs = env.reset();
 * while (!env.isDone()) {
 *   Map<String, double[]> actions = new HashMap<>();
 *   actions.put("separator", sepPolicy.predict(obs.get("separator")));
 *   actions.put("compressor", compPolicy.predict(obs.get("compressor")));
 *   var result = env.step(actions);
 *   obs = result.observations;
 * }
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class SeparatorCompressorMultiAgentEnv extends MultiAgentEnvironment {
  private static final long serialVersionUID = 1000L;

  private Stream feed;
  private Separator separator;
  private ThrottlingValve liquidValve;
  private Compressor compressor;
  private Stream compressorOut;

  // Simulated state (simplified dynamics)
  private double liquidLevel = 0.5;
  private double separatorPressure = 30.0;
  private double compressorSpeed = 6000.0;

  /**
   * Create the multi-agent environment.
   */
  public SeparatorCompressorMultiAgentEnv() {
    super(createProcess());
    setupAgents();
    setupConstraints();
  }

  private static ProcessSystem createProcess() {
    // Create process fluid
    SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // Build process
    ProcessSystem process = new ProcessSystem();

    Stream feedStream = new Stream("Feed", fluid);
    feedStream.setFlowRate(500.0, "kg/hr");
    process.add(feedStream);

    Separator sep = new Separator("Separator", feedStream);
    process.add(sep);

    Stream liquidOut = new Stream("Liquid Out", sep.getLiquidOutStream());
    process.add(liquidOut);

    ThrottlingValve lcv = new ThrottlingValve("LCV", liquidOut);
    lcv.setOutletPressure(10.0);
    process.add(lcv);

    Stream gasToCompressor = new Stream("Gas to Compressor", sep.getGasOutStream());
    process.add(gasToCompressor);

    Compressor comp = new Compressor("Compressor", gasToCompressor);
    comp.setOutletPressure(60.0);
    comp.setIsentropicEfficiency(0.75);
    process.add(comp);

    Stream compOut = new Stream("Compressed Gas", comp.getOutletStream());
    process.add(compOut);

    return process;
  }

  private void setupAgents() {
    // Get equipment references
    feed = (Stream) getProcess().getUnit("Feed");
    separator = (Separator) getProcess().getUnit("Separator");
    liquidValve = (ThrottlingValve) getProcess().getUnit("LCV");
    compressor = (Compressor) getProcess().getUnit("Compressor");
    compressorOut = (Stream) getProcess().getUnit("Compressed Gas");

    // Create separator agent (controls level via liquid valve)
    SeparatorAgent sepAgent = new SeparatorAgent("separator", separator, liquidValve);
    sepAgent.setLevelSetpoint(0.5);
    addAgent(sepAgent);

    // Create compressor agent (controls discharge pressure via speed)
    CompressorAgent compAgent = new CompressorAgent("compressor", compressor);
    compAgent.setDischargePressureSetpoint(60.0);
    compAgent.setSpeedRange(3000, 10000);
    addAgent(compAgent);
  }

  private void setupConstraints() {
    ConstraintManager globalConstraints = new ConstraintManager();

    // Level constraints
    globalConstraints.addHardRange("level_bounds", "liquid_level", 0.1, 0.9, "fraction");

    // Pressure constraints
    globalConstraints.addHardRange("sep_pressure", "separator_pressure", 10.0, 50.0, "bar");
    globalConstraints.addHardRange("comp_discharge", "compressor_discharge_pressure", 40.0, 80.0,
        "bar");

    // Compressor surge protection
    globalConstraints.addHardRange("surge_margin", "surge_fraction", 1.1, 3.0, "fraction");

    setSharedConstraints(globalConstraints);
  }

  @Override
  protected StateVector getGlobalState() {
    StateVector state = new StateVector();

    // Separator state
    state.add("liquid_level", liquidLevel, 0.0, 1.0, "fraction");
    state.add("separator_pressure", separatorPressure, 0.0, 100.0, "bar");

    if (separator.getFluid() != null) {
      state.add("separator_temperature", separator.getFluid().getTemperature("K"), 200.0, 400.0,
          "K");
      if (separator.getFluid().getNumberOfPhases() > 0) {
        state.add("gas_density", separator.getFluid().getPhase(0).getDensity("kg/m3"), 0.0, 100.0,
            "kg/m3");
      }
      if (separator.getFluid().getNumberOfPhases() > 1) {
        state.add("liquid_density", separator.getFluid().getPhase(1).getDensity("kg/m3"), 0.0,
            900.0, "kg/m3");
      }
    }

    // Compressor state
    state.add("inlet_pressure", compressor.getInletStream().getPressure("bar"), 0.0, 100.0, "bar");
    state.add("outlet_pressure", compressor.getOutletPressure(), 0.0, 150.0, "bar");
    state.add("compression_ratio",
        compressor.getOutletPressure() / Math.max(1.0, compressor.getInletStream().getPressure()),
        1.0, 10.0, "");
    state.add("speed", compressorSpeed, 0.0, 15000.0, "rpm");
    state.add("power", compressor.getPower("kW"), 0.0, 10000.0, "kW");

    // Surge fraction (simplified - should use actual compressor map)
    double surgeFrac = 1.5 - 0.3 * (compressor.getOutletPressure() / separatorPressure - 1.5);
    state.add("surge_fraction", Math.max(0.5, surgeFrac), 0.0, 3.0, "fraction");

    // Setpoint errors for reward calculation
    state.add("level_error", liquidLevel - 0.5, -1.0, 1.0, "fraction");
    state.add("pressure_error", compressor.getOutletPressure() - 60.0, -50.0, 50.0, "bar");

    // Feed conditions
    state.add("feed_flow", feed.getFluid().getFlowRate("kg/hr"), 0.0, 1000.0, "kg/hr");

    return state;
  }

  @Override
  protected double computeTeamReward(StateVector state, Map<String, double[]> actions) {
    double reward = 2.0; // Base survival reward for both agents

    // Level tracking
    double levelError = state.getValue("level_error");
    reward -= 5.0 * levelError * levelError;

    // Pressure tracking
    double pressureError = state.getValue("pressure_error");
    reward -= 2.0 * (pressureError / 20.0) * (pressureError / 20.0);

    // Surge margin bonus
    double surgeFrac = state.getValue("surge_fraction");
    if (surgeFrac > 1.2) {
      reward += 0.5;
    }

    // Energy efficiency
    double power = state.getValue("power");
    reward -= 0.0001 * power;

    // Action smoothness
    for (double[] action : actions.values()) {
      for (double a : action) {
        reward -= 0.05 * a * a;
      }
    }

    return reward;
  }

  /**
   * Apply feed disturbance for testing robustness.
   *
   * @param flowMultiplier flow rate multiplier
   */
  public void applyFeedDisturbance(double flowMultiplier) {
    double currentFlow = feed.getFluid().getFlowRate("kg/hr");
    feed.setFlowRate(currentFlow * flowMultiplier, "kg/hr");
  }

  /**
   * Get the separator.
   *
   * @return separator
   */
  public Separator getSeparator() {
    return separator;
  }

  /**
   * Get the compressor.
   *
   * @return compressor
   */
  public Compressor getCompressor() {
    return compressor;
  }
}
