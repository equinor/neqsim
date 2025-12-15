package neqsim.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.ml.examples.SeparatorCompressorMultiAgentEnv;
import neqsim.ml.examples.SeparatorGymEnv;
import neqsim.ml.examples.SeparatorLevelControlEnv;
import neqsim.ml.multiagent.MultiAgentEnvironment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for RL integration classes.
 */
class RLIntegrationTest {

  @Test
  void testStateVectorNormalization() {
    StateVector state = new StateVector();
    state.add("temperature", 350.0, 200.0, 500.0, "K");
    state.add("pressure", 50.0, 0.0, 100.0, "bar");

    // Check raw values
    assertEquals(350.0, state.getValue("temperature"), 1e-6);
    assertEquals(50.0, state.getValue("pressure"), 1e-6);

    // Check normalized values (should be 0.5 for both)
    assertEquals(0.5, state.getNormalized("temperature"), 1e-6);
    assertEquals(0.5, state.getNormalized("pressure"), 1e-6);

    // Check arrays
    double[] arr = state.toArray();
    assertEquals(2, arr.length);
    assertEquals(350.0, arr[0], 1e-6);
    assertEquals(50.0, arr[1], 1e-6);

    double[] normArr = state.toNormalizedArray();
    assertEquals(0.5, normArr[0], 1e-6);
    assertEquals(0.5, normArr[1], 1e-6);
  }

  @Test
  void testActionVectorBounds() {
    ActionVector action = new ActionVector();
    action.define("valve", 0.0, 1.0, "fraction");
    action.define("speed", 0.0, 100.0, "rpm");

    // Set normal value
    action.set("valve", 0.5);
    assertEquals(0.5, action.get("valve"), 1e-6);

    // Set value exceeding bounds - should clamp
    action.set("valve", 1.5);
    assertEquals(1.0, action.get("valve"), 1e-6);

    action.set("valve", -0.5);
    assertEquals(0.0, action.get("valve"), 1e-6);

    // Set from normalized
    action.setNormalized("speed", 0.5);
    assertEquals(50.0, action.get("speed"), 1e-6);
  }

  @Test
  void testConstraintViolation() {
    Constraint c =
        Constraint.range("max_pressure", "pressure", 0.0, 100.0, "bar", Constraint.Type.HARD);

    // Within bounds
    c.evaluate(50.0);
    assertFalse(c.isViolated());
    assertEquals(0.0, c.getViolation(), 1e-6);
    assertEquals(50.0, c.getMargin(), 1e-6);

    // Exceeds upper bound
    c.evaluate(120.0);
    assertTrue(c.isViolated());
    assertEquals(20.0, c.getViolation(), 1e-6);

    // Below lower bound
    c.evaluate(-10.0);
    assertTrue(c.isViolated());
    assertEquals(10.0, c.getViolation(), 1e-6);
  }

  @Test
  void testConstraintManagerEvaluation() {
    ConstraintManager cm = new ConstraintManager();
    cm.addHardRange("pressure_limit", "pressure", 0.0, 100.0, "bar");
    cm.addSoftRange("optimal_temp", "temperature", 280.0, 320.0, "K");

    StateVector state = new StateVector();
    state.add("pressure", 50.0, 0.0, 100.0, "bar");
    state.add("temperature", 350.0, 200.0, 400.0, "K"); // Outside optimal

    cm.evaluate(state);

    assertFalse(cm.hasHardViolation());
    assertEquals(1, cm.getViolations().size()); // Only temp violation

    // Now with hard violation
    StateVector badState = new StateVector();
    badState.add("pressure", 120.0, 0.0, 100.0, "bar");
    badState.add("temperature", 300.0, 200.0, 400.0, "K");

    cm.evaluate(badState);

    assertTrue(cm.hasHardViolation());
  }

  @Test
  void testStateVectorFeatureNames() {
    StateVector state = new StateVector();
    state.add("level", 0.5, 0.0, 1.0, "fraction").add("pressure", 50.0, 0.0, 100.0, "bar")
        .add("temperature", 300.0, 200.0, 400.0, "K");

    String[] names = state.getFeatureNames();
    assertEquals(3, names.length);
    assertEquals("level", names[0]);
    assertEquals("pressure", names[1]);
    assertEquals("temperature", names[2]);
  }

  @Test
  void testConstraintProjection() {
    Constraint c =
        Constraint.range("level_limit", "level", 0.1, 0.9, "fraction", Constraint.Type.HARD);

    assertEquals(0.5, c.project(0.5), 1e-6);
    assertEquals(0.9, c.project(1.2), 1e-6);
    assertEquals(0.1, c.project(-0.1), 1e-6);
  }

  @Test
  void testActionVectorFromArray() {
    ActionVector action = new ActionVector();
    action.define("valve1", 0.0, 1.0, "fraction");
    action.define("valve2", 0.0, 1.0, "fraction");
    action.define("setpoint", 0.0, 100.0, "%");

    action.setFromNormalizedArray(new double[] {0.25, 0.75, 0.5});

    assertEquals(0.25, action.get("valve1"), 1e-6);
    assertEquals(0.75, action.get("valve2"), 1e-6);
    assertEquals(50.0, action.get("setpoint"), 1e-6);
  }

  @Test
  void testConstraintManagerPenalty() {
    ConstraintManager cm = new ConstraintManager();
    cm.addHardRange("pressure", "pressure", 0.0, 100.0, "bar");
    cm.addSoftRange("temp", "temperature", 280.0, 320.0, "K");

    // All satisfied
    StateVector good = new StateVector();
    good.add("pressure", 50.0, 0.0, 100.0, "bar");
    good.add("temperature", 300.0, 200.0, 400.0, "K");
    cm.evaluate(good);
    assertEquals(0.0, cm.getTotalViolationPenalty(), 1e-6);

    // Soft violation
    StateVector soft = new StateVector();
    soft.add("pressure", 50.0, 0.0, 100.0, "bar");
    soft.add("temperature", 340.0, 200.0, 400.0, "K"); // 20K above optimal
    cm.evaluate(soft);
    assertTrue(cm.getTotalViolationPenalty() > 0);
    assertFalse(cm.hasHardViolation());
  }

  @Test
  void testStateVectorToMap() {
    StateVector state = new StateVector();
    state.add("pressure", 50.0, 0.0, 100.0, "bar");

    var map = state.toMap();
    assertNotNull(map.get("timestamp_ms"));
    assertNotNull(map.get("features"));
  }

  @Test
  void testConstraintCategories() {
    Constraint safety = new Constraint("hipps", "HIPPS trigger", Constraint.Type.HARD,
        Constraint.Category.SAFETY, "pressure", 0.0, 150.0, "bar");

    Constraint operational = new Constraint("optimal", "Optimal range", Constraint.Type.SOFT,
        Constraint.Category.OPERATIONAL, "temperature", 280.0, 320.0, "K");

    assertEquals(Constraint.Category.SAFETY, safety.getCategory());
    assertEquals(Constraint.Category.OPERATIONAL, operational.getCategory());
    assertTrue(safety.isHard());
    assertFalse(operational.isHard());
  }

  @Test
  void testTrainingDataCollector() {
    TrainingDataCollector collector = new TrainingDataCollector("test_model");
    collector.defineInput("temp", "K", 200.0, 400.0);
    collector.defineInput("pres", "bar", 1.0, 100.0);
    collector.defineOutput("result", "fraction", 0.0, 1.0);

    // Add samples
    for (int i = 0; i < 10; i++) {
      collector.startSample();
      collector.recordInput("temp", 250.0 + i * 10);
      collector.recordInput("pres", 20.0 + i * 5);
      collector.recordOutput("result", 0.1 * i);
      collector.endSample();
    }

    assertEquals(10, collector.getSampleCount());

    // Check CSV generation
    String csv = collector.toCSV();
    assertTrue(csv.contains("input_temp"));
    assertTrue(csv.contains("output_result"));
    String[] lines = csv.split("\n");
    assertEquals(11, lines.length); // header + 10 samples

    // Check statistics
    var inputStats = collector.getInputStatistics();
    assertTrue(inputStats.get("temp").get("mean") > 280);
    assertTrue(inputStats.get("temp").get("std") > 0);
  }

  @Test
  void testSeparatorLevelControlEnv() {
    SeparatorLevelControlEnv env = new SeparatorLevelControlEnv();
    env.setLevelSetpoint(0.5);

    // Reset should return observation
    StateVector obs = env.reset();
    assertNotNull(obs);
    assertTrue(obs.size() > 0);
    assertTrue(obs.getFeatureNames().length > 0);

    // Action space should be defined
    ActionVector actionSpace = env.getActionSpace();
    assertEquals(1, actionSpace.size());
    assertEquals("valve_opening", actionSpace.getActionNames()[0]);

    // Step should work
    actionSpace.setNormalized("valve_opening", 0.5);
    RLEnvironment.StepResult result = env.step(actionSpace);

    assertNotNull(result.observation);
    assertFalse(Double.isNaN(result.reward));
    assertNotNull(result.info);

    // Constraints should be set up
    assertTrue(env.getConstraintManager().size() >= 2);
  }

  @Test
  void testSeparatorStateVectorProvider() {
    // Create fluid
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-hexane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Separator separator = new Separator("sep", feed);
    separator.run();

    // Get state vector
    StateVector state = separator.getStateVector();

    // Verify state vector has expected fields
    assertNotNull(state);
    assertTrue(state.size() >= 3);

    // Check pressure and temperature are present
    assertFalse(Double.isNaN(state.getValue("pressure")));
    assertFalse(Double.isNaN(state.getValue("temperature")));
    assertTrue(state.getValue("pressure") > 0);
    assertTrue(state.getValue("temperature") > 0);

    // Verify normalization works
    double normPressure = state.getNormalized("pressure");
    assertTrue(normPressure >= 0 && normPressure <= 1);
  }

  @Test
  void testCompressorStateVectorProvider() {
    // Create gas fluid
    SystemInterface gas = new SystemSrkEos(298.15, 10.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("compFeed", gas);
    feed.setFlowRate(500.0, "kg/hr");
    feed.run();

    Compressor compressor = new Compressor("comp", feed);
    compressor.setOutletPressure(50.0);
    compressor.setIsentropicEfficiency(0.75);
    compressor.run();

    // Get state vector
    StateVector state = compressor.getStateVector();

    // Verify state vector
    assertNotNull(state);
    assertTrue(state.size() >= 5);

    // Check key fields
    assertFalse(Double.isNaN(state.getValue("inlet_pressure")));
    assertFalse(Double.isNaN(state.getValue("outlet_pressure")));
    assertFalse(Double.isNaN(state.getValue("power")));
    assertTrue(state.getValue("outlet_pressure") > state.getValue("inlet_pressure"));
  }

  @Test
  void testGymEnvironmentAPI() {
    SeparatorGymEnv env = new SeparatorGymEnv();
    env.setMaxEpisodeSteps(100);

    // Verify space dimensions
    assertEquals(8, env.getObservationDim());
    assertEquals(1, env.getActionDim());
    assertEquals(8, env.getObservationLow().length);
    assertEquals(8, env.getObservationHigh().length);

    // Reset returns proper structure
    GymEnvironment.ResetResult resetResult = env.reset();
    assertNotNull(resetResult.observation);
    assertEquals(8, resetResult.observation.length);
    assertNotNull(resetResult.info);

    // Step returns proper structure
    GymEnvironment.StepResult stepResult = env.step(new double[] {0.05});
    assertNotNull(stepResult.observation);
    assertEquals(8, stepResult.observation.length);
    assertFalse(Double.isNaN(stepResult.reward));
    assertFalse(stepResult.terminated);
    assertFalse(stepResult.truncated);
    assertNotNull(stepResult.info);

    // Episode step counter works
    assertEquals(1, env.getCurrentStep());
  }

  @Test
  void testGymEnvironmentEpisodeDynamics() {
    SeparatorGymEnv env = new SeparatorGymEnv();
    env.setMaxEpisodeSteps(50);

    env.reset();
    int steps = 0;
    while (!env.isDone() && steps < 100) {
      env.step(new double[] {0.0}); // No change
      steps++;
    }

    // Episode should end due to truncation at 50 steps
    assertTrue(steps <= 51);
    assertTrue(env.isDone());
  }

  @Test
  void testMultiAgentEnvironmentBasics() {
    SeparatorCompressorMultiAgentEnv env = new SeparatorCompressorMultiAgentEnv();

    // Check agents registered
    assertEquals(2, env.getNumAgents());
    assertTrue(env.getAgentIds().contains("separator"));
    assertTrue(env.getAgentIds().contains("compressor"));

    // Reset returns observations for all agents
    Map<String, double[]> obs = env.reset();
    assertEquals(2, obs.size());
    assertNotNull(obs.get("separator"));
    assertNotNull(obs.get("compressor"));
  }

  @Test
  void testMultiAgentEnvironmentStep() {
    SeparatorCompressorMultiAgentEnv env = new SeparatorCompressorMultiAgentEnv();
    env.setMaxEpisodeSteps(100);

    Map<String, double[]> obs = env.reset();

    // Create actions for both agents
    Map<String, double[]> actions = new HashMap<>();
    actions.put("separator", new double[] {0.01}); // Small level control action
    actions.put("compressor", new double[] {0.0}); // No speed change

    // Step
    MultiAgentEnvironment.MultiAgentStepResult result = env.step(actions);

    // Verify result structure
    assertEquals(2, result.observations.size());
    assertEquals(2, result.rewards.size());
    assertEquals(2, result.infos.size());
    assertNotNull(result.globalState);
    assertFalse(result.terminated);
    assertFalse(result.truncated);
  }

  @Test
  void testCooperativeRewards() {
    SeparatorCompressorMultiAgentEnv env = new SeparatorCompressorMultiAgentEnv();
    env.setCoordinationMode(MultiAgentEnvironment.CoordinationMode.COOPERATIVE);
    env.setMaxEpisodeSteps(10);

    env.reset();

    Map<String, double[]> actions = new HashMap<>();
    actions.put("separator", new double[] {0.0});
    actions.put("compressor", new double[] {0.0});

    MultiAgentEnvironment.MultiAgentStepResult result = env.step(actions);

    // In cooperative mode, all agents get same reward
    double sepReward = result.rewards.get("separator");
    double compReward = result.rewards.get("compressor");
    assertEquals(sepReward, compReward, 1e-6);
  }
}
