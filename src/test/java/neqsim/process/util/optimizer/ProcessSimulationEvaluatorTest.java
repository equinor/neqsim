package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for ProcessSimulationEvaluator.
 *
 * <p>
 * Tests the black-box evaluator for external optimization algorithms.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessSimulationEvaluatorTest {

  private ProcessSystem processSystem;
  private ProcessSimulationEvaluator evaluator;

  @BeforeEach
  void setUp() {
    // Create a simple test process
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(10000.0, "kg/hr");

    Stream feed = new Stream("feed", fluid);
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(30.0);
    valve.run();

    Separator separator = new Separator("separator", valve.getOutletStream());
    separator.run();

    Stream gasOut = new Stream("gasOut", separator.getGasOutStream());
    gasOut.run();

    processSystem = new ProcessSystem();
    processSystem.add(feed);
    processSystem.add(valve);
    processSystem.add(separator);
    processSystem.add(gasOut);

    evaluator = new ProcessSimulationEvaluator(processSystem);
  }

  @Test
  void testDefaultConstructor() {
    ProcessSimulationEvaluator emptyEvaluator = new ProcessSimulationEvaluator();
    assertNotNull(emptyEvaluator);
    assertNull(emptyEvaluator.getProcessSystem());
    assertEquals(0, emptyEvaluator.getParameterCount());
    assertEquals(0, emptyEvaluator.getObjectiveCount());
    assertEquals(0, emptyEvaluator.getConstraintCount());
  }

  @Test
  void testConstructorWithProcessSystem() {
    assertNotNull(evaluator);
    assertEquals(processSystem, evaluator.getProcessSystem());
  }

  @Test
  void testAddParameter() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");

    assertEquals(1, evaluator.getParameterCount());
    assertEquals(1, evaluator.getParameters().size());

    ProcessSimulationEvaluator.ParameterDefinition param = evaluator.getParameters().get(0);
    assertEquals("feed.flowRate", param.getName());
    assertEquals("feed", param.getEquipmentName());
    assertEquals("flowRate", param.getPropertyName());
    assertEquals(1000.0, param.getLowerBound(), 0.01);
    assertEquals(50000.0, param.getUpperBound(), 0.01);
    assertEquals("kg/hr", param.getUnit());
  }

  @Test
  void testAddMultipleParameters() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr")
        .addParameter("feed", "pressure", 30.0, 80.0, "bara")
        .addParameter("feed", "temperature", 273.15, 373.15, "K");

    assertEquals(3, evaluator.getParameterCount());
  }

  @Test
  void testGetBounds() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr").addParameter("feed",
        "pressure", 30.0, 80.0, "bara");

    double[][] bounds = evaluator.getBounds();
    assertEquals(2, bounds.length);
    assertEquals(1000.0, bounds[0][0], 0.01);
    assertEquals(50000.0, bounds[0][1], 0.01);
    assertEquals(30.0, bounds[1][0], 0.01);
    assertEquals(80.0, bounds[1][1], 0.01);
  }

  @Test
  void testGetLowerBounds() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr").addParameter("feed",
        "pressure", 30.0, 80.0, "bara");

    double[] lb = evaluator.getLowerBounds();
    assertEquals(2, lb.length);
    assertEquals(1000.0, lb[0], 0.01);
    assertEquals(30.0, lb[1], 0.01);
  }

  @Test
  void testGetUpperBounds() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr").addParameter("feed",
        "pressure", 30.0, 80.0, "bara");

    double[] ub = evaluator.getUpperBounds();
    assertEquals(2, ub.length);
    assertEquals(50000.0, ub[0], 0.01);
    assertEquals(80.0, ub[1], 0.01);
  }

  @Test
  void testGetInitialValues() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");

    double[] x0 = evaluator.getInitialValues();
    assertEquals(1, x0.length);
    assertEquals(25500.0, x0[0], 0.01); // (1000 + 50000) / 2
  }

  @Test
  void testAddObjective() {
    evaluator.addObjective("power", process -> 100.0);

    assertEquals(1, evaluator.getObjectiveCount());
    assertEquals("power", evaluator.getObjectives().get(0).getName());
    assertEquals(ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MINIMIZE,
        evaluator.getObjectives().get(0).getDirection());
  }

  @Test
  void testAddObjectiveWithDirection() {
    evaluator.addObjective("throughput", process -> 10000.0,
        ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);

    assertEquals(ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE,
        evaluator.getObjectives().get(0).getDirection());
  }

  @Test
  void testAddConstraintLowerBound() {
    evaluator.addConstraintLowerBound("minPressure", process -> 25.0, 20.0);

    assertEquals(1, evaluator.getConstraintCount());
    ProcessSimulationEvaluator.ConstraintDefinition constraint = evaluator.getConstraints().get(0);
    assertEquals("minPressure", constraint.getName());
    assertEquals(ProcessSimulationEvaluator.ConstraintDefinition.Type.LOWER_BOUND,
        constraint.getType());
    assertEquals(20.0, constraint.getLowerBound(), 0.01);
  }

  @Test
  void testAddConstraintUpperBound() {
    evaluator.addConstraintUpperBound("maxPressure", process -> 50.0, 60.0);

    ProcessSimulationEvaluator.ConstraintDefinition constraint = evaluator.getConstraints().get(0);
    assertEquals(ProcessSimulationEvaluator.ConstraintDefinition.Type.UPPER_BOUND,
        constraint.getType());
    assertEquals(60.0, constraint.getUpperBound(), 0.01);
  }

  @Test
  void testAddConstraintRange() {
    evaluator.addConstraintRange("pressureRange", process -> 45.0, 30.0, 60.0);

    ProcessSimulationEvaluator.ConstraintDefinition constraint = evaluator.getConstraints().get(0);
    assertEquals(ProcessSimulationEvaluator.ConstraintDefinition.Type.RANGE, constraint.getType());
    assertEquals(30.0, constraint.getLowerBound(), 0.01);
    assertEquals(60.0, constraint.getUpperBound(), 0.01);
  }

  @Test
  void testBasicEvaluation() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addObjective("outletPressure",
        process -> process.getUnit("gasOut").getFluid().getPressure("bara"));

    double[] x = {15000.0};
    ProcessSimulationEvaluator.EvaluationResult result = evaluator.evaluate(x);

    assertNotNull(result);
    assertTrue(result.isSimulationConverged());
    assertEquals(1, result.getEvaluationNumber());
    assertNotNull(result.getObjectives());
    assertEquals(1, result.getObjectives().length);
    assertFalse(Double.isNaN(result.getObjective()));
  }

  @Test
  void testEvaluationWithConstraints() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addObjective("flow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"));
    evaluator.addConstraintLowerBound("minFlow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"), 5000.0);

    double[] x = {15000.0};
    ProcessSimulationEvaluator.EvaluationResult result = evaluator.evaluate(x);

    assertTrue(result.isFeasible());
    assertTrue(result.getConstraintMargins()[0] > 0); // 15000 > 5000

    // Test constraint violation
    double[] xLow = {3000.0};
    ProcessSimulationEvaluator.EvaluationResult resultLow = evaluator.evaluate(xLow);
    assertFalse(resultLow.isFeasible());
    assertTrue(resultLow.getConstraintMargins()[0] < 0); // 3000 < 5000
  }

  @Test
  void testEvaluationResult() {
    ProcessSimulationEvaluator.EvaluationResult result =
        new ProcessSimulationEvaluator.EvaluationResult();

    result.setParameters(new double[] {100.0, 200.0});
    result.setObjectives(new double[] {50.0});
    result.setObjectivesRaw(new double[] {50.0});
    result.setConstraintValues(new double[] {25.0});
    result.setConstraintMargins(new double[] {5.0});
    result.setFeasible(true);
    result.setSimulationConverged(true);
    result.setPenaltySum(0.0);
    result.setEvaluationNumber(1);
    result.setEvaluationTimeMs(100);

    assertEquals(50.0, result.getObjective(), 0.01);
    assertEquals(50.0, result.getPenalizedObjective(), 0.01);
    assertTrue(result.isFeasible());
    assertTrue(result.isSimulationConverged());
    assertEquals(1, result.getEvaluationNumber());
    assertEquals(100, result.getEvaluationTimeMs());
  }

  @Test
  void testPenalizedObjective() {
    ProcessSimulationEvaluator.EvaluationResult result =
        new ProcessSimulationEvaluator.EvaluationResult();
    result.setObjectives(new double[] {100.0});
    result.setPenaltySum(50.0);

    assertEquals(150.0, result.getPenalizedObjective(), 0.01);
  }

  @Test
  void testWeightedObjective() {
    ProcessSimulationEvaluator.EvaluationResult result =
        new ProcessSimulationEvaluator.EvaluationResult();
    result.setObjectives(new double[] {10.0, 20.0, 30.0});

    double[] weights = {1.0, 2.0, 0.5};
    double weighted = result.getWeightedObjective(weights);
    assertEquals(10.0 * 1.0 + 20.0 * 2.0 + 30.0 * 0.5, weighted, 0.01);
  }

  @Test
  void testConvenienceMethods() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addObjective("flow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"));
    evaluator.addConstraintLowerBound("minFlow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"), 5000.0);

    double[] x = {20000.0};

    // Test evaluateObjective
    double obj = evaluator.evaluateObjective(x);
    assertTrue(obj > 0);

    // Test isFeasible
    assertTrue(evaluator.isFeasible(x));

    // Test getConstraintMargins
    double[] margins = evaluator.getConstraintMargins(x);
    assertEquals(1, margins.length);
    assertTrue(margins[0] > 0);
  }

  @Test
  void testParameterDefinitionBounds() {
    ProcessSimulationEvaluator.ParameterDefinition param =
        new ProcessSimulationEvaluator.ParameterDefinition("test", "eq", "prop", 10.0, 100.0,
            "units");

    assertTrue(param.isWithinBounds(50.0));
    assertTrue(param.isWithinBounds(10.0));
    assertTrue(param.isWithinBounds(100.0));
    assertFalse(param.isWithinBounds(5.0));
    assertFalse(param.isWithinBounds(150.0));

    assertEquals(10.0, param.clamp(5.0), 0.01);
    assertEquals(100.0, param.clamp(150.0), 0.01);
    assertEquals(50.0, param.clamp(50.0), 0.01);
  }

  @Test
  void testConstraintDefinitionMargin() {
    // Lower bound: g(x) >= 20
    ProcessSimulationEvaluator.ConstraintDefinition lowerBound =
        new ProcessSimulationEvaluator.ConstraintDefinition("test", p -> 25.0, 20.0);
    lowerBound.setType(ProcessSimulationEvaluator.ConstraintDefinition.Type.LOWER_BOUND);
    assertEquals(5.0, lowerBound.margin(processSystem), 0.01); // 25 - 20 = 5
    assertTrue(lowerBound.isSatisfied(processSystem));

    // Upper bound: g(x) <= 30
    ProcessSimulationEvaluator.ConstraintDefinition upperBound =
        new ProcessSimulationEvaluator.ConstraintDefinition();
    upperBound.setEvaluator(p -> 25.0);
    upperBound.setUpperBound(30.0);
    upperBound.setType(ProcessSimulationEvaluator.ConstraintDefinition.Type.UPPER_BOUND);
    assertEquals(5.0, upperBound.margin(processSystem), 0.01); // 30 - 25 = 5
    assertTrue(upperBound.isSatisfied(processSystem));

    // Range: 20 <= g(x) <= 30
    ProcessSimulationEvaluator.ConstraintDefinition range =
        new ProcessSimulationEvaluator.ConstraintDefinition("range", p -> 25.0, 20.0, 30.0);
    assertEquals(5.0, range.margin(processSystem), 0.01); // min(25-20, 30-25) = 5
    assertTrue(range.isSatisfied(processSystem));
  }

  @Test
  void testConstraintPenalty() {
    ProcessSimulationEvaluator.ConstraintDefinition constraint =
        new ProcessSimulationEvaluator.ConstraintDefinition("test", p -> 15.0, 20.0); // violates
    constraint.setPenaltyWeight(100.0);

    double margin = constraint.margin(processSystem); // 15 - 20 = -5
    assertEquals(-5.0, margin, 0.01);
    assertFalse(constraint.isSatisfied(processSystem));

    double penalty = constraint.penalty(processSystem);
    assertEquals(100.0 * 25.0, penalty, 0.01); // 100 * (-5)^2 = 2500
  }

  @Test
  void testEvaluationCount() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addObjective("flow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"));

    assertEquals(0, evaluator.getEvaluationCount());

    evaluator.evaluate(new double[] {10000.0});
    assertEquals(1, evaluator.getEvaluationCount());

    evaluator.evaluate(new double[] {20000.0});
    assertEquals(2, evaluator.getEvaluationCount());

    evaluator.resetEvaluationCount();
    assertEquals(0, evaluator.getEvaluationCount());
  }

  @Test
  void testConfiguration() {
    evaluator.setFiniteDifferenceStep(1e-5);
    assertEquals(1e-5, evaluator.getFiniteDifferenceStep(), 1e-10);

    evaluator.setUseRelativeStep(false);
    assertFalse(evaluator.isUseRelativeStep());

    evaluator.setCloneForEvaluation(true);
    assertTrue(evaluator.isCloneForEvaluation());
  }

  @Test
  void testGradientEstimation() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addObjective("flow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"));

    double[] x = {20000.0};
    double[] gradient = evaluator.estimateGradient(x);

    assertEquals(1, gradient.length);
    // Gradient of flow w.r.t. flow should be approximately 1
    assertTrue(Math.abs(gradient[0] - 1.0) < 0.1);
  }

  @Test
  void testConstraintJacobian() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addConstraintLowerBound("minFlow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"), 5000.0);
    evaluator.addConstraintUpperBound("maxFlow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"), 40000.0);

    double[] x = {20000.0};
    double[][] jacobian = evaluator.estimateConstraintJacobian(x);

    assertEquals(2, jacobian.length);
    assertEquals(1, jacobian[0].length);
    // Jacobian of flow constraints w.r.t. flow should be approximately ±1
    assertTrue(Math.abs(jacobian[0][0] - 1.0) < 0.1); // d(flow - min)/d(flow) ≈ 1
    assertTrue(Math.abs(jacobian[1][0] + 1.0) < 0.1); // d(max - flow)/d(flow) ≈ -1
  }

  @Test
  void testProblemDefinition() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addObjective("power", process -> 100.0);
    evaluator.addConstraintLowerBound("minFlow", process -> 10000.0, 5000.0);

    Map<String, Object> definition = evaluator.getProblemDefinition();

    assertNotNull(definition);
    assertTrue(definition.containsKey("parameters"));
    assertTrue(definition.containsKey("objectives"));
    assertTrue(definition.containsKey("constraints"));
  }

  @Test
  void testToJson() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addObjective("power", process -> 100.0);
    evaluator.addConstraintLowerBound("minFlow", process -> 10000.0, 5000.0);

    String json = evaluator.toJson();

    assertNotNull(json);
    assertTrue(json.contains("parameters"));
    assertTrue(json.contains("objectives"));
    assertTrue(json.contains("constraints"));
    assertTrue(json.contains("feed.flowRate"));
    assertTrue(json.contains("power"));
    assertTrue(json.contains("minFlow"));
  }

  @Test
  void testCustomSetter() {
    evaluator.addParameterWithSetter("customParam", (process, value) -> {
      process.getUnit("feed").getFluid().setTotalFlowRate(value, "kg/hr");
    }, 1000.0, 50000.0, "kg/hr");

    evaluator.addObjective("flow",
        process -> process.getUnit("feed").getFluid().getTotalNumberOfMoles());

    double[] x = {25000.0};
    ProcessSimulationEvaluator.EvaluationResult result = evaluator.evaluate(x);

    assertTrue(result.isSimulationConverged());
    assertNotNull(result.getObjectives());
  }

  @Test
  void testInvalidParameterArrayLength() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addParameter("feed", "pressure", 30.0, 80.0, "bara");

    assertThrows(IllegalArgumentException.class, () -> {
      evaluator.evaluate(new double[] {10000.0}); // Should be length 2
    });
  }

  @Test
  void testNullParameterArray() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");

    assertThrows(IllegalArgumentException.class, () -> {
      evaluator.evaluate(null);
    });
  }

  @Test
  void testAdditionalOutputs() {
    ProcessSimulationEvaluator.EvaluationResult result =
        new ProcessSimulationEvaluator.EvaluationResult();

    result.addOutput("power", 150.0);
    result.addOutput("efficiency", 0.85);

    assertEquals(2, result.getAdditionalOutputs().size());
    assertEquals(150.0, result.getAdditionalOutputs().get("power"), 0.01);
    assertEquals(0.85, result.getAdditionalOutputs().get("efficiency"), 0.01);
  }

  @Test
  void testMaximizeObjective() {
    evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr");
    evaluator.addObjective("flow",
        process -> ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"),
        ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);

    double[] x = {20000.0};
    ProcessSimulationEvaluator.EvaluationResult result = evaluator.evaluate(x);

    // For MAXIMIZE, the returned objective should be negated
    assertTrue(result.getObjective() < 0); // Negated for minimization form
    assertTrue(result.getObjectivesRaw()[0] > 0); // Raw value is positive
  }
}

