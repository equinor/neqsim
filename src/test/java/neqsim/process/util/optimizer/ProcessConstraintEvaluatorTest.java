package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for ProcessConstraintEvaluator.
 * 
 * <p>
 * Tests constraint evaluation, caching, and sensitivity analysis.
 * </p>
 * 
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessConstraintEvaluatorTest {
  private ProcessSystem processSystem;
  private ProcessConstraintEvaluator evaluator;

  @BeforeEach
  void setUp() {
    // Create a simple test process
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(8000.0, "kg/hr");

    Stream feed = new Stream("feed", fluid);
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(25.0);
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

    evaluator = new ProcessConstraintEvaluator(processSystem);
  }

  @Test
  void testDefaultConstructor() {
    ProcessConstraintEvaluator emptyEvaluator = new ProcessConstraintEvaluator();
    assertNotNull(emptyEvaluator);
    assertNull(emptyEvaluator.getProcessSystem());
  }

  @Test
  void testConstructorWithProcessSystem() {
    assertNotNull(evaluator);
    assertEquals(processSystem, evaluator.getProcessSystem());
  }

  @Test
  void testSetProcessSystem() {
    ProcessConstraintEvaluator testEvaluator = new ProcessConstraintEvaluator();
    testEvaluator.setProcessSystem(processSystem);
    assertEquals(processSystem, testEvaluator.getProcessSystem());
  }

  @Test
  void testEvaluate() {
    ProcessConstraintEvaluator.ConstraintEvaluationResult result = evaluator.evaluate();

    assertNotNull(result);
    assertNotNull(result.getEquipmentSummaries());
    // Overall utilization should be between 0 and 1 (or higher if over capacity)
    assertTrue(result.getOverallUtilization() >= 0.0);
    assertNotNull(result.getBottleneckEquipment());
  }

  @Test
  void testEvaluationResultProperties() {
    ProcessConstraintEvaluator.ConstraintEvaluationResult result = evaluator.evaluate();

    // Test bottleneck
    String bottleneck = result.getBottleneckEquipment();
    assertNotNull(bottleneck);

    // Test feasibility
    boolean feasible = result.isFeasible();
    // Just verify it returns a value

    // Test violation count
    int violations = result.getTotalViolationCount();
    assertTrue(violations >= 0);
  }

  @Test
  void testEquipmentConstraintSummary() {
    ProcessConstraintEvaluator.ConstraintEvaluationResult result = evaluator.evaluate();
    Map<String, ProcessConstraintEvaluator.EquipmentConstraintSummary> summaries =
        result.getEquipmentSummaries();

    assertNotNull(summaries);
    // May or may not have entries depending on registered strategies
  }

  @Test
  void testConstraintEvaluationResultClass() {
    ProcessConstraintEvaluator.ConstraintEvaluationResult result =
        new ProcessConstraintEvaluator.ConstraintEvaluationResult();

    result.setOverallUtilization(0.85);
    result.setBottleneckEquipment("Compressor1");
    result.setBottleneckUtilization(0.95);
    result.setTotalViolationCount(0);
    result.setFeasible(true);

    assertEquals(0.85, result.getOverallUtilization(), 0.001);
    assertEquals("Compressor1", result.getBottleneckEquipment());
    assertEquals(0.95, result.getBottleneckUtilization(), 0.001);
    assertEquals(0, result.getTotalViolationCount());
    assertTrue(result.isFeasible());
  }

  @Test
  void testEquipmentConstraintSummaryClass() {
    ProcessConstraintEvaluator.EquipmentConstraintSummary summary =
        new ProcessConstraintEvaluator.EquipmentConstraintSummary();

    summary.setEquipmentName("TestSeparator");
    summary.setEquipmentType("Separator");
    summary.setUtilization(0.75);
    summary.setWithinLimits(true);
    summary.setConstraintCount(3);
    summary.setViolationCount(0);
    summary.setMarginToLimit(0.25);
    summary.setBottleneckConstraint("MaxLiquidLevel");

    assertEquals("TestSeparator", summary.getEquipmentName());
    assertEquals("Separator", summary.getEquipmentType());
    assertEquals(0.75, summary.getUtilization(), 0.001);
    assertTrue(summary.isWithinLimits());
    assertEquals(3, summary.getConstraintCount());
    assertEquals(0, summary.getViolationCount());
    assertEquals(0.25, summary.getMarginToLimit(), 0.001);
    assertEquals("MaxLiquidLevel", summary.getBottleneckConstraint());
  }

  @Test
  void testCachedConstraintsClass() {
    ProcessConstraintEvaluator.CachedConstraints cache =
        new ProcessConstraintEvaluator.CachedConstraints();

    assertFalse(cache.isValid());
    assertEquals(0, cache.getFlowRate(), 0.001);
    assertNotNull(cache.getCachedResults());

    // Mark as valid
    cache.setFlowRate(5000.0);
    cache.setTimestamp(System.currentTimeMillis());
    cache.setValid(true);

    assertTrue(cache.isValid());
    assertEquals(5000.0, cache.getFlowRate(), 0.001);
  }

  @Test
  void testCacheInvalidation() {
    ProcessConstraintEvaluator.CachedConstraints cache =
        new ProcessConstraintEvaluator.CachedConstraints();

    cache.setValid(true);
    assertTrue(cache.isValid());

    cache.invalidate();
    assertFalse(cache.isValid());
  }

  @Test
  void testCacheExpiration() throws InterruptedException {
    ProcessConstraintEvaluator.CachedConstraints cache =
        new ProcessConstraintEvaluator.CachedConstraints();

    cache.setTimestamp(System.currentTimeMillis() - 10000); // 10 seconds ago
    cache.setTtlMillis(5000); // 5 second TTL
    cache.setValid(true);

    assertTrue(cache.isExpired());
  }

  @Test
  void testCacheNotExpired() {
    ProcessConstraintEvaluator.CachedConstraints cache =
        new ProcessConstraintEvaluator.CachedConstraints();

    cache.setTimestamp(System.currentTimeMillis());
    cache.setTtlMillis(60000); // 60 second TTL
    cache.setValid(true);

    assertFalse(cache.isExpired());
  }

  @Test
  void testCalculateFlowSensitivities() {
    Map<String, Double> sensitivities = evaluator.calculateFlowSensitivities(8000.0, "kg/hr");

    assertNotNull(sensitivities);
    // May be empty if no equipment strategies registered
  }

  @Test
  void testEstimateMaxFlow() {
    double maxFlow = evaluator.estimateMaxFlow(8000.0, "kg/hr");

    // Max flow should be positive
    assertTrue(maxFlow > 0);
  }

  @Test
  void testClearCache() {
    evaluator.clearCache();
    // Should not throw
  }

  @Test
  void testSetCacheTTL() {
    evaluator.setCacheTTLMillis(30000);
    assertEquals(30000, evaluator.getCacheTTLMillis());
  }

  @Test
  void testDefaultCacheTTL() {
    // Default should be reasonable (e.g., 10 seconds = 10000 ms)
    assertTrue(evaluator.getCacheTTLMillis() > 0);
  }

  @Test
  void testEvaluateWithNullProcessSystem() {
    ProcessConstraintEvaluator emptyEvaluator = new ProcessConstraintEvaluator();
    ProcessConstraintEvaluator.ConstraintEvaluationResult result = emptyEvaluator.evaluate();

    assertNotNull(result);
    assertTrue(result.isFeasible());
    assertEquals(0, result.getTotalViolationCount());
  }

  @Test
  void testGetStrategiesForEquipment() {
    // This tests the internal strategy lookup
    ProcessConstraintEvaluator.ConstraintEvaluationResult result = evaluator.evaluate();

    // Should have evaluated equipment
    assertNotNull(result);
  }

  @Test
  void testMultipleEvaluations() {
    // First evaluation
    ProcessConstraintEvaluator.ConstraintEvaluationResult result1 = evaluator.evaluate();

    // Second evaluation (may use cache)
    ProcessConstraintEvaluator.ConstraintEvaluationResult result2 = evaluator.evaluate();

    assertNotNull(result1);
    assertNotNull(result2);

    // Results should be consistent
    assertEquals(result1.getOverallUtilization(), result2.getOverallUtilization(), 0.01);
  }

  @Test
  void testSensitivityWithZeroFlow() {
    Map<String, Double> sensitivities = evaluator.calculateFlowSensitivities(0.0, "kg/hr");

    assertNotNull(sensitivities);
    // Should handle zero flow gracefully
  }

  @Test
  void testEstimateMaxFlowWithHighCurrentFlow() {
    // Test with very high flow that might exceed capacity
    double maxFlow = evaluator.estimateMaxFlow(100000.0, "kg/hr");

    assertNotNull(maxFlow);
    assertTrue(maxFlow > 0);
  }

  @Test
  void testConstraintEvaluationResultIsFeasible() {
    ProcessConstraintEvaluator.ConstraintEvaluationResult result =
        new ProcessConstraintEvaluator.ConstraintEvaluationResult();

    result.setFeasible(true);
    assertTrue(result.isFeasible());

    result.setFeasible(false);
    assertFalse(result.isFeasible());
  }

  @Test
  void testEquipmentSummaryConstraintDetails() {
    ProcessConstraintEvaluator.EquipmentConstraintSummary summary =
        new ProcessConstraintEvaluator.EquipmentConstraintSummary();

    // Test constraint details map
    Map<String, Double> details = summary.getConstraintDetails();
    assertNotNull(details);

    summary.addConstraintDetail("Pressure", 0.85);
    summary.addConstraintDetail("Temperature", 0.70);

    assertEquals(2, summary.getConstraintDetails().size());
    assertEquals(0.85, summary.getConstraintDetails().get("Pressure"), 0.001);
  }
}

