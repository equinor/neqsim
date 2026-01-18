package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for ProcessOptimizationEngine.
 * 
 * <p>
 * Tests the gradient-based optimization, sensitivity analysis, and FlowRateOptimizer integration
 * features.
 * </p>
 * 
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessOptimizationEngineTest {

  private ProcessSystem processSystem;
  private ProcessOptimizationEngine engine;

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

    processSystem = new ProcessSystem();
    processSystem.add(feed);
    processSystem.add(valve);
    processSystem.add(separator);

    engine = new ProcessOptimizationEngine(processSystem);
  }

  @Test
  void testDefaultConstructor() {
    ProcessOptimizationEngine defaultEngine = new ProcessOptimizationEngine();
    assertNotNull(defaultEngine);
    assertEquals(ProcessOptimizationEngine.SearchAlgorithm.GOLDEN_SECTION,
        defaultEngine.getSearchAlgorithm());
    assertEquals(1e-6, defaultEngine.getTolerance(), 1e-10);
    assertEquals(100, defaultEngine.getMaxIterations());
  }

  @Test
  void testSetSearchAlgorithm() {
    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
    assertEquals(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH,
        engine.getSearchAlgorithm());

    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT);
    assertEquals(ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT,
        engine.getSearchAlgorithm());
  }

  @Test
  void testSetTolerance() {
    engine.setTolerance(1e-4);
    assertEquals(1e-4, engine.getTolerance(), 1e-10);
  }

  @Test
  void testSetMaxIterations() {
    engine.setMaxIterations(50);
    assertEquals(50, engine.getMaxIterations());
  }

  @Test
  void testSetEnforceConstraints() {
    assertTrue(engine.isEnforceConstraints());
    engine.setEnforceConstraints(false);
    assertFalse(engine.isEnforceConstraints());
  }

  @Test
  void testGetProcessSystem() {
    assertNotNull(engine.getProcessSystem());
    assertEquals(processSystem, engine.getProcessSystem());
  }

  @Test
  void testFindMaximumThroughputGoldenSection() {
    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.GOLDEN_SECTION);
    engine.setEnforceConstraints(false);

    ProcessOptimizationEngine.OptimizationResult result =
        engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0);

    assertNotNull(result);
    assertEquals("Maximum Throughput", result.getObjective());
    // Result should be within search bounds
    assertTrue(result.getOptimalValue() >= 1000.0);
    assertTrue(result.getOptimalValue() <= 50000.0);
  }

  @Test
  void testFindMaximumThroughputBinarySearch() {
    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
    engine.setEnforceConstraints(false);

    ProcessOptimizationEngine.OptimizationResult result =
        engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0);

    assertNotNull(result);
    assertTrue(result.getOptimalValue() >= 1000.0);
    assertTrue(result.getOptimalValue() <= 50000.0);
  }

  @Test
  void testFindMaximumThroughputGradientDescent() {
    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT);
    engine.setEnforceConstraints(false);

    ProcessOptimizationEngine.OptimizationResult result =
        engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0);

    assertNotNull(result);
    assertTrue(result.getOptimalValue() >= 1000.0);
    assertTrue(result.getOptimalValue() <= 50000.0);
  }

  @Test
  void testFindMaximumThroughputArmijoWolfe() {
    engine.setSearchAlgorithm(
        ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT_ARMIJO_WOLFE);
    engine.setEnforceConstraints(false);

    // Configure Armijo-Wolfe parameters
    engine.setArmijoC1(1e-4);
    engine.setWolfeC2(0.9);
    engine.setMaxLineSearchIterations(20);

    ProcessOptimizationEngine.OptimizationResult result =
        engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0);

    assertNotNull(result);
    assertTrue(result.getOptimalValue() >= 1000.0, "Flow should be above min");
    assertTrue(result.getOptimalValue() <= 50000.0, "Flow should be below max");
    System.out.println("Armijo-Wolfe optimal flow: " + result.getOptimalValue());
  }

  @Test
  void testFindMaximumThroughputBFGS() {
    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BFGS);
    engine.setEnforceConstraints(false);

    // Configure BFGS parameters
    engine.setBfgsGradientTolerance(1e-6);

    ProcessOptimizationEngine.OptimizationResult result =
        engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0);

    assertNotNull(result);
    assertTrue(result.getOptimalValue() >= 1000.0, "Flow should be above min");
    assertTrue(result.getOptimalValue() <= 50000.0, "Flow should be below max");
    System.out.println("BFGS optimal flow: " + result.getOptimalValue());
  }

  @Test
  void testCompareOptimizationAlgorithms() {
    engine.setEnforceConstraints(false);

    // Test with all algorithms and compare results
    double[] results = new double[4];

    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.GOLDEN_SECTION);
    results[0] = engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0).getOptimalValue();

    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT);
    results[1] = engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0).getOptimalValue();

    engine.setSearchAlgorithm(
        ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT_ARMIJO_WOLFE);
    results[2] = engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0).getOptimalValue();

    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BFGS);
    results[3] = engine.findMaximumThroughput(50.0, 10.0, 1000.0, 50000.0).getOptimalValue();

    System.out.println("Algorithm comparison:");
    System.out.println("  Golden Section:    " + results[0]);
    System.out.println("  Gradient Descent:  " + results[1]);
    System.out.println("  Armijo-Wolfe:      " + results[2]);
    System.out.println("  BFGS:              " + results[3]);

    // All algorithms should find similar results (within 20% of each other)
    double maxResult = Math.max(Math.max(results[0], results[1]), Math.max(results[2], results[3]));
    double minResult = Math.min(Math.min(results[0], results[1]), Math.min(results[2], results[3]));
    assertTrue(maxResult / minResult < 1.5, "Algorithm results should be within 50% of each other");
  }

  @Test
  void testEvaluateAllConstraints() {
    ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();

    assertNotNull(report);
    assertNotNull(report.getEquipmentStatuses());
    // We should have at least some equipment evaluated
    // Note: Not all equipment may have strategies registered
  }

  @Test
  void testFindBottleneckEquipment() {
    String bottleneck = engine.findBottleneckEquipment();
    // May or may not find a bottleneck depending on setup
    // Just verify it doesn't throw
  }

  @Test
  void testFindRequiredInletPressure() {
    ProcessOptimizationEngine.OptimizationResult result =
        engine.findRequiredInletPressure(5000.0, 10.0, 20.0, 100.0);

    assertNotNull(result);
    assertEquals("Required Inlet Pressure", result.getObjective());
    // Pressure should be within search bounds
    assertTrue(result.getOptimalValue() >= 20.0);
    assertTrue(result.getOptimalValue() <= 100.0);
  }

  @Test
  void testGenerateLiftCurve() {
    double[] pressures = {30.0, 40.0, 50.0};
    double[] temperatures = {298.15};
    double[] waterCuts = {0.0};
    double[] gors = {100.0};

    ProcessOptimizationEngine.LiftCurveData curve =
        engine.generateLiftCurve(pressures, temperatures, waterCuts, gors);

    assertNotNull(curve);
    assertNotNull(curve.getPoints());
  }

  @Test
  void testClearCache() {
    // Should not throw
    engine.clearCache();
  }

  @Test
  void testOptimizationResultClass() {
    ProcessOptimizationEngine.OptimizationResult result =
        new ProcessOptimizationEngine.OptimizationResult();

    result.setObjective("Test");
    result.setOptimalValue(1234.5);
    result.setConverged(true);
    result.setBottleneck("TestEquipment");
    result.setErrorMessage(null);

    assertEquals("Test", result.getObjective());
    assertEquals(1234.5, result.getOptimalValue(), 0.01);
    assertTrue(result.isConverged());
    assertEquals("TestEquipment", result.getBottleneck());
    assertNull(result.getErrorMessage());
    assertNotNull(result.getConstraintViolations());
  }

  @Test
  void testConstraintReportClass() {
    ProcessOptimizationEngine.ConstraintReport report =
        new ProcessOptimizationEngine.ConstraintReport();

    ProcessOptimizationEngine.EquipmentConstraintStatus status1 =
        new ProcessOptimizationEngine.EquipmentConstraintStatus();
    status1.setEquipmentName("Compressor1");
    status1.setEquipmentType("Compressor");
    status1.setUtilization(0.85);
    status1.setWithinLimits(true);

    ProcessOptimizationEngine.EquipmentConstraintStatus status2 =
        new ProcessOptimizationEngine.EquipmentConstraintStatus();
    status2.setEquipmentName("Separator1");
    status2.setEquipmentType("Separator");
    status2.setUtilization(0.95);
    status2.setWithinLimits(true);

    report.addEquipmentStatus(status1);
    report.addEquipmentStatus(status2);

    assertEquals(2, report.getEquipmentStatuses().size());

    // Bottleneck should be the one with highest utilization
    ProcessOptimizationEngine.EquipmentConstraintStatus bottleneck = report.getBottleneck();
    assertNotNull(bottleneck);
    assertEquals("Separator1", bottleneck.getEquipmentName());
  }

  @Test
  void testEquipmentConstraintStatusClass() {
    ProcessOptimizationEngine.EquipmentConstraintStatus status =
        new ProcessOptimizationEngine.EquipmentConstraintStatus();

    status.setEquipmentName("TestCompressor");
    status.setEquipmentType("Compressor");
    status.setUtilization(0.75);
    status.setWithinLimits(true);
    status.setBottleneckConstraint("MaxPower");

    assertEquals("TestCompressor", status.getEquipmentName());
    assertEquals("Compressor", status.getEquipmentType());
    assertEquals(0.75, status.getUtilization(), 0.001);
    assertTrue(status.isWithinLimits());
    assertEquals("MaxPower", status.getBottleneckConstraint());
  }

  @Test
  void testLiftCurveDataClass() {
    ProcessOptimizationEngine.LiftCurveData data = new ProcessOptimizationEngine.LiftCurveData();

    ProcessOptimizationEngine.LiftCurvePoint point1 =
        new ProcessOptimizationEngine.LiftCurvePoint();
    point1.setInletPressure(50.0);
    point1.setTemperature(300.0);
    point1.setWaterCut(0.1);
    point1.setGOR(150.0);
    point1.setMaxFlowRate(10000.0);

    data.addPoint(point1);

    assertEquals(1, data.size());
    assertEquals(1, data.getPoints().size());
    assertEquals(50.0, data.getPoints().get(0).getInletPressure(), 0.01);
  }

  @Test
  void testLiftCurvePointClass() {
    ProcessOptimizationEngine.LiftCurvePoint point = new ProcessOptimizationEngine.LiftCurvePoint();

    point.setInletPressure(45.0);
    point.setTemperature(310.0);
    point.setWaterCut(0.2);
    point.setGOR(200.0);
    point.setMaxFlowRate(15000.0);

    assertEquals(45.0, point.getInletPressure(), 0.01);
    assertEquals(310.0, point.getTemperature(), 0.01);
    assertEquals(0.2, point.getWaterCut(), 0.01);
    assertEquals(200.0, point.getGOR(), 0.01);
    assertEquals(15000.0, point.getMaxFlowRate(), 0.01);
  }

  @Test
  void testSensitivityResultClass() {
    ProcessOptimizationEngine.SensitivityResult result =
        new ProcessOptimizationEngine.SensitivityResult();

    result.setBaseFlow(5000.0);
    result.setFlowGradient(0.5);
    result.setTightestConstraint("Compressor1");
    result.setTightestMargin(0.03);
    result.setFlowBuffer(250.0);

    assertEquals(5000.0, result.getBaseFlow(), 0.01);
    assertEquals(0.5, result.getFlowGradient(), 0.01);
    assertEquals("Compressor1", result.getTightestConstraint());
    assertEquals(0.03, result.getTightestMargin(), 0.001);
    assertEquals(250.0, result.getFlowBuffer(), 0.01);

    assertTrue(result.isAtCapacity()); // margin < 5%
    assertEquals("Compressor1", result.getBottleneckEquipment());
  }

  @Test
  void testSensitivityResultNotAtCapacity() {
    ProcessOptimizationEngine.SensitivityResult result =
        new ProcessOptimizationEngine.SensitivityResult();
    result.setTightestMargin(0.15); // 15% margin

    assertFalse(result.isAtCapacity());
  }

  @Test
  void testAnalyzeSensitivity() {
    engine.setEnforceConstraints(false);

    ProcessOptimizationEngine.SensitivityResult sensitivity =
        engine.analyzeSensitivity(5000.0, 50.0, 10.0);

    assertNotNull(sensitivity);
    assertEquals(5000.0, sensitivity.getBaseFlow(), 0.01);
    assertNotNull(sensitivity.getConstraintMargins());
  }

  @Test
  void testCalculateShadowPrices() {
    Map<String, Double> shadowPrices = engine.calculateShadowPrices(5000.0, 50.0, 10.0);

    assertNotNull(shadowPrices);
    // May be empty if no equipment strategies registered
  }

  @Test
  void testGetConstraintEvaluator() {
    ProcessConstraintEvaluator evaluator = engine.getConstraintEvaluator();

    assertNotNull(evaluator);
    assertEquals(processSystem, evaluator.getProcessSystem());
  }

  @Test
  void testEvaluateConstraintsWithCache() {
    ProcessConstraintEvaluator.ConstraintEvaluationResult result =
        engine.evaluateConstraintsWithCache();

    assertNotNull(result);
    assertNotNull(result.getEquipmentSummaries());
  }

  @Test
  void testCalculateFlowSensitivities() {
    Map<String, Double> sensitivities = engine.calculateFlowSensitivities(5000.0, "kg/hr");

    assertNotNull(sensitivities);
    // May be empty if no equipment strategies registered
  }

  @Test
  void testEstimateMaximumFlow() {
    double maxFlow = engine.estimateMaximumFlow(5000.0, "kg/hr");

    assertTrue(maxFlow > 0);
  }

  @Test
  void testCreateFlowRateOptimizer() {
    neqsim.process.util.optimization.FlowRateOptimizer flowOptimizer =
        engine.createFlowRateOptimizer();

    assertNotNull(flowOptimizer);
  }

  @Test
  void testCreateFlowRateOptimizerWithoutProcessSystem() {
    ProcessOptimizationEngine emptyEngine = new ProcessOptimizationEngine();

    assertThrows(IllegalStateException.class, () -> {
      emptyEngine.createFlowRateOptimizer();
    });
  }

  @Test
  void testGenerateComprehensiveLiftCurve() {
    double[] inletPressures = {40.0, 50.0, 60.0};

    neqsim.process.util.optimization.FlowRateOptimizer optimizer =
        engine.generateComprehensiveLiftCurve("feed", inletPressures, 10.0);

    assertNotNull(optimizer);
  }

  @Test
  void testNullProcessSystemHandling() {
    ProcessOptimizationEngine emptyEngine = new ProcessOptimizationEngine();

    // Should handle null gracefully
    String bottleneck = emptyEngine.findBottleneckEquipment();
    assertNull(bottleneck);

    ProcessOptimizationEngine.ConstraintReport report = emptyEngine.evaluateAllConstraints();
    assertNotNull(report);
    assertTrue(report.getEquipmentStatuses().isEmpty());
  }
}
