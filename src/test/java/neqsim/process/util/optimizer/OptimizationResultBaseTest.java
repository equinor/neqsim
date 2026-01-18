package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for OptimizationResultBase.
 * 
 * <p>
 * Tests the unified result base class including status, constraint violations, timing, and
 * sensitivity tracking.
 * </p>
 * 
 * @author NeqSim Development Team
 * @version 1.0
 */
class OptimizationResultBaseTest {

  @Test
  void testDefaultConstructor() {
    OptimizationResultBase result = new OptimizationResultBase();

    assertEquals(OptimizationResultBase.Status.NOT_STARTED, result.getStatus());
    assertFalse(result.isConverged());
    assertEquals(0, result.getIterations());
    assertEquals(0.0, result.getOptimalValue(), 1e-10);
    assertNotNull(result.getConstraintViolations());
    assertTrue(result.getConstraintViolations().isEmpty());
  }

  @Test
  void testStatusEnum() {
    assertEquals(7, OptimizationResultBase.Status.values().length);

    OptimizationResultBase result = new OptimizationResultBase();

    result.setStatus(OptimizationResultBase.Status.CONVERGED);
    assertEquals(OptimizationResultBase.Status.CONVERGED, result.getStatus());

    result.setStatus(OptimizationResultBase.Status.MAX_ITERATIONS_REACHED);
    assertEquals(OptimizationResultBase.Status.MAX_ITERATIONS_REACHED, result.getStatus());

    result.setStatus(OptimizationResultBase.Status.INFEASIBLE);
    assertEquals(OptimizationResultBase.Status.INFEASIBLE, result.getStatus());

    result.setStatus(OptimizationResultBase.Status.FAILED);
    assertEquals(OptimizationResultBase.Status.FAILED, result.getStatus());

    result.setStatus(OptimizationResultBase.Status.IN_PROGRESS);
    assertEquals(OptimizationResultBase.Status.IN_PROGRESS, result.getStatus());

    result.setStatus(OptimizationResultBase.Status.CANCELLED);
    assertEquals(OptimizationResultBase.Status.CANCELLED, result.getStatus());
  }

  @Test
  void testConvergedSetsStatus() {
    OptimizationResultBase result = new OptimizationResultBase();

    assertFalse(result.isConverged());
    assertEquals(OptimizationResultBase.Status.NOT_STARTED, result.getStatus());

    result.setConverged(true);
    assertTrue(result.isConverged());
    assertEquals(OptimizationResultBase.Status.CONVERGED, result.getStatus());
  }

  @Test
  void testOptimalValueAndObjective() {
    OptimizationResultBase result = new OptimizationResultBase();

    result.setOptimalValue(12345.67);
    assertEquals(12345.67, result.getOptimalValue(), 0.01);

    result.setObjective("MaxThroughput");
    assertEquals("MaxThroughput", result.getObjective());

    result.setObjectiveValue(98765.43);
    assertEquals(98765.43, result.getObjectiveValue(), 0.01);
  }

  @Test
  void testMultiVariableValues() {
    OptimizationResultBase result = new OptimizationResultBase();

    // Test optimal values map
    Map<String, Double> optimalVals = new HashMap<String, Double>();
    optimalVals.put("FlowRate", 5000.0);
    optimalVals.put("Pressure", 50.0);
    result.setOptimalValues(optimalVals);

    assertEquals(2, result.getOptimalValues().size());
    assertEquals(5000.0, result.getOptimalValues().get("FlowRate"), 0.01);
    assertEquals(50.0, result.getOptimalValues().get("Pressure"), 0.01);

    // Test individual add
    result.addOptimalValue("Temperature", 75.0);
    assertEquals(3, result.getOptimalValues().size());
    assertEquals(75.0, result.getOptimalValues().get("Temperature"), 0.01);

    // Test initial values
    Map<String, Double> initialVals = new HashMap<String, Double>();
    initialVals.put("FlowRate", 1000.0);
    result.setInitialValues(initialVals);
    assertEquals(1000.0, result.getInitialValues().get("FlowRate"), 0.01);
  }

  @Test
  void testIterationTracking() {
    OptimizationResultBase result = new OptimizationResultBase();

    result.setIterations(42);
    assertEquals(42, result.getIterations());

    result.incrementIterations();
    assertEquals(43, result.getIterations());
  }

  @Test
  void testFunctionEvaluationTracking() {
    OptimizationResultBase result = new OptimizationResultBase();

    result.setFunctionEvaluations(100);
    assertEquals(100, result.getFunctionEvaluations());

    result.incrementFunctionEvaluations();
    assertEquals(101, result.getFunctionEvaluations());

    result.setConstraintEvaluations(50);
    assertEquals(50, result.getConstraintEvaluations());

    result.incrementConstraintEvaluations();
    assertEquals(51, result.getConstraintEvaluations());
  }

  @Test
  void testTiming() throws InterruptedException {
    OptimizationResultBase result = new OptimizationResultBase();

    result.markStart();
    assertEquals(OptimizationResultBase.Status.IN_PROGRESS, result.getStatus());
    assertTrue(result.getStartTimeMillis() > 0);

    Thread.sleep(50); // Sleep 50ms

    result.markEnd();
    assertTrue(result.getEndTimeMillis() > result.getStartTimeMillis());
    assertTrue(result.getElapsedTimeMillis() >= 50);
    assertTrue(result.getElapsedTimeSeconds() >= 0.05);
  }

  @Test
  void testConstraintViolationClass() {
    OptimizationResultBase.ConstraintViolation violation =
        new OptimizationResultBase.ConstraintViolation("Compressor1", "MaxPower", 15.0, 12.0, "MW",
            true);

    assertEquals("Compressor1", violation.getEquipmentName());
    assertEquals("MaxPower", violation.getConstraintName());
    assertEquals(15.0, violation.getCurrentValue(), 0.01);
    assertEquals(12.0, violation.getLimitValue(), 0.01);
    assertEquals("MW", violation.getUnit());
    assertTrue(violation.isHardConstraint());
    assertEquals(3.0, violation.getViolationAmount(), 0.01);
    assertEquals(25.0, violation.getViolationPercent(), 0.1); // 3/12 * 100 = 25%

    String str = violation.toString();
    assertTrue(str.contains("Compressor1"));
    assertTrue(str.contains("MaxPower"));
  }

  @Test
  void testAddConstraintViolation() {
    OptimizationResultBase result = new OptimizationResultBase();

    result.addConstraintViolation("Separator1", "MaxLiquidLevel", 95.0, 85.0, "%", true);
    result.addConstraintViolation("Compressor1", "MaxPower", 15.0, 12.0, "MW", false);

    assertEquals(2, result.getConstraintViolations().size());
    assertTrue(result.hasViolations());
    assertTrue(result.hasHardViolations());

    // Check first violation
    OptimizationResultBase.ConstraintViolation v1 = result.getConstraintViolations().get(0);
    assertEquals("Separator1", v1.getEquipmentName());
    assertEquals("MaxLiquidLevel", v1.getConstraintName());
    assertEquals(10.0, v1.getViolationAmount(), 0.01);
  }

  @Test
  void testConstraintMargins() {
    OptimizationResultBase result = new OptimizationResultBase();

    Map<String, Double> margins = new HashMap<String, Double>();
    margins.put("Compressor1/Power", 0.85);
    margins.put("Separator1/Level", 0.92);
    result.setConstraintMargins(margins);

    assertEquals(2, result.getConstraintMargins().size());
    assertEquals(0.85, result.getConstraintMargins().get("Compressor1/Power"), 0.01);

    result.addConstraintMargin("Valve1/Cv", 0.75);
    assertEquals(3, result.getConstraintMargins().size());
  }

  @Test
  void testBottleneck() {
    OptimizationResultBase result = new OptimizationResultBase();

    result.setBottleneckEquipment("Compressor1");
    result.setBottleneckConstraint("MaxPower");

    assertEquals("Compressor1", result.getBottleneckEquipment());
    assertEquals("MaxPower", result.getBottleneckConstraint());
  }

  @Test
  void testSensitivities() {
    OptimizationResultBase result = new OptimizationResultBase();

    Map<String, Double> sensitivities = new HashMap<String, Double>();
    sensitivities.put("InletPressure", 125.5);
    sensitivities.put("Temperature", -50.2);
    result.setSensitivities(sensitivities);

    assertEquals(2, result.getSensitivities().size());
    assertEquals(125.5, result.getSensitivities().get("InletPressure"), 0.1);

    result.addSensitivity("Composition", 75.0);
    assertEquals(3, result.getSensitivities().size());
  }

  @Test
  void testShadowPrices() {
    OptimizationResultBase result = new OptimizationResultBase();

    Map<String, Double> shadowPrices = new HashMap<String, Double>();
    shadowPrices.put("Compressor1/MaxPower", 1500.0);
    result.setShadowPrices(shadowPrices);

    assertEquals(1, result.getShadowPrices().size());
    assertEquals(1500.0, result.getShadowPrices().get("Compressor1/MaxPower"), 0.1);

    result.addShadowPrice("Separator1/MaxFlow", 800.0);
    assertEquals(2, result.getShadowPrices().size());
  }

  @Test
  void testErrorMessage() {
    OptimizationResultBase result = new OptimizationResultBase();

    assertNull(result.getErrorMessage());

    result.setErrorMessage("Convergence failed due to ill-conditioned Jacobian");
    assertEquals("Convergence failed due to ill-conditioned Jacobian", result.getErrorMessage());
  }

  @Test
  void testSummary() {
    OptimizationResultBase result = new OptimizationResultBase();
    result.setStatus(OptimizationResultBase.Status.CONVERGED);
    result.setConverged(true);
    result.setOptimalValue(5000.0);
    result.setIterations(25);
    result.setFunctionEvaluations(50);
    result.setBottleneckEquipment("Compressor1");
    result.setBottleneckConstraint("MaxPower");

    String summary = result.getSummary();
    assertTrue(summary.contains("CONVERGED"));
    assertTrue(summary.contains("5000"));
    assertTrue(summary.contains("25"));
    assertTrue(summary.contains("Compressor1"));
    assertTrue(summary.contains("MaxPower"));
  }

  @Test
  void testToString() {
    OptimizationResultBase result = new OptimizationResultBase();
    result.setStatus(OptimizationResultBase.Status.CONVERGED);
    result.setOptimalValue(1234.5);

    String str = result.toString();
    assertTrue(str.contains("CONVERGED"));
    assertTrue(str.contains("1234.5"));
  }

  @Test
  void testCompleteWorkflow() {
    // Simulate a complete optimization run
    OptimizationResultBase result = new OptimizationResultBase();

    // Start
    result.markStart();
    assertEquals(OptimizationResultBase.Status.IN_PROGRESS, result.getStatus());

    // Set objective
    result.setObjective("MaxThroughput");

    // Simulate iterations
    for (int i = 0; i < 20; i++) {
      result.incrementIterations();
      result.incrementFunctionEvaluations();
      result.incrementConstraintEvaluations();
    }

    // Set optimal values
    result.setOptimalValue(5500.0);
    result.addOptimalValue("FlowRate", 5500.0);
    result.setObjectiveValue(5500.0);

    // Record bottleneck
    result.setBottleneckEquipment("Compressor1");
    result.setBottleneckConstraint("MaxPower");
    result.addConstraintMargin("Compressor1/MaxPower", 0.98);

    // Add sensitivities
    result.addSensitivity("InletPressure", 100.0);
    result.addShadowPrice("Compressor1/MaxPower", 1200.0);

    // Complete
    result.setConverged(true);
    result.markEnd();

    // Verify
    assertEquals(OptimizationResultBase.Status.CONVERGED, result.getStatus());
    assertTrue(result.isConverged());
    assertEquals(20, result.getIterations());
    assertEquals(20, result.getFunctionEvaluations());
    assertEquals(20, result.getConstraintEvaluations());
    assertEquals(5500.0, result.getOptimalValue(), 0.01);
    assertEquals("Compressor1", result.getBottleneckEquipment());
    assertTrue(result.getElapsedTimeMillis() >= 0);
    assertFalse(result.hasViolations());
  }
}
