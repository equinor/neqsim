package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionImpactResult.RecommendedAction;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ProductionImpactAnalyzer.
 *
 * <p>
 * These tests verify the production impact analysis capabilities for equipment failure scenarios.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class ProductionImpactAnalyzerTest {

  private ProcessSystem process;
  private Stream feed;
  private Separator hpSeparator;
  private Compressor compressor1;
  private Compressor compressor2;
  private Cooler cooler;

  @BeforeEach
  void setUp() {
    // Create a simple gas processing system
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    // Feed stream
    feed = new Stream("Feed", gas);
    feed.setFlowRate(50000, "kg/hr");
    feed.setTemperature(25, "C");
    feed.setPressure(50, "bara");

    // HP Separator
    hpSeparator = new Separator("HP Separator", feed);
    hpSeparator.setInternalDiameter(2.0);

    // First stage compression
    compressor1 = new Compressor("Stage 1 Compressor", hpSeparator.getGasOutStream());
    compressor1.setOutletPressure(100.0, "bara");
    compressor1.setPolytropicEfficiency(0.75);

    // Intercooler
    cooler = new Cooler("Intercooler", compressor1.getOutletStream());
    cooler.setOutTemperature(35, "C");

    // Second stage compression
    compressor2 = new Compressor("Stage 2 Compressor", cooler.getOutletStream());
    compressor2.setOutletPressure(150.0, "bara");
    compressor2.setPolytropicEfficiency(0.75);

    // Build process
    process = new ProcessSystem();
    process.add(feed);
    process.add(hpSeparator);
    process.add(compressor1);
    process.add(cooler);
    process.add(compressor2);
  }

  @Test
  @DisplayName("Analyzer initialization auto-detects streams")
  void testAutoDetectStreams() {
    process.run();

    ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(process);
    assertNotNull(analyzer);
    assertNotNull(analyzer.getProcessSystem());
  }

  @Test
  @DisplayName("Single compressor failure analysis")
  void testSingleCompressorFailure() {
    process.run();

    ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(process);
    analyzer.setFeedStreamName("Feed");
    analyzer.setProductStreamName("Stage 2 Compressor");

    ProductionImpactResult result = analyzer.analyzeFailureImpact("Stage 1 Compressor");

    assertNotNull(result);
    assertTrue(result.isConverged());
    assertEquals("Stage 1 Compressor", result.getEquipmentName());
    assertEquals("Compressor", result.getEquipmentType());

    // With compressor failed, production should be significantly reduced
    assertTrue(result.getBaselineProductionRate() > 0, "Baseline should have production");

    System.out.println(result);
  }

  @Test
  @DisplayName("Compare degraded operation to full shutdown")
  void testCompareToPlantStop() {
    process.run();

    ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(process);
    analyzer.setFeedStreamName("Feed");
    analyzer.setProductStreamName("Stage 2 Compressor");

    ProductionImpactResult result = analyzer.compareToPlantStop("Intercooler");

    assertNotNull(result);
    assertEquals(0.0, result.getFullShutdownProduction(), 0.001);

    // Degraded operation should produce more than shutdown
    assertTrue(result.getLossVsFullShutdown() >= 0,
        "Degraded operation should produce >= shutdown");

    System.out.println("=== Compare to Plant Stop ===");
    System.out.println(result);
  }

  @Test
  @DisplayName("Equipment failure mode trip")
  void testEquipmentFailureModeTrip() {
    EquipmentFailureMode tripMode = EquipmentFailureMode.trip("Compressor");

    assertNotNull(tripMode);
    assertEquals("Trip", tripMode.getName());
    assertEquals(EquipmentFailureMode.FailureType.TRIP, tripMode.getType());
    assertEquals(0.0, tripMode.getCapacityFactor(), 0.001);
    assertTrue(tripMode.isCompleteFailure());
    assertTrue(tripMode.getMttr() > 0);
  }

  @Test
  @DisplayName("Equipment failure mode degraded")
  void testEquipmentFailureModeDegraded() {
    EquipmentFailureMode degradedMode = EquipmentFailureMode.degraded(50);

    assertNotNull(degradedMode);
    assertEquals("Degraded", degradedMode.getName());
    assertEquals(EquipmentFailureMode.FailureType.DEGRADED, degradedMode.getType());
    assertEquals(0.5, degradedMode.getCapacityFactor(), 0.001);
    assertFalse(degradedMode.isCompleteFailure());
    assertEquals(0.5, degradedMode.getProductionLossFactor(), 0.001);
  }

  @Test
  @DisplayName("Equipment failure mode builder")
  void testEquipmentFailureModeBuilder() {
    EquipmentFailureMode customMode = EquipmentFailureMode.builder().name("Partial Failure")
        .description("Bearing damage causing reduced speed")
        .type(EquipmentFailureMode.FailureType.PARTIAL_FAILURE).capacityFactor(0.7)
        .efficiencyFactor(0.85).mttr(48.0).failureFrequency(0.5).requiresImmediateAction(false)
        .build();

    assertEquals("Partial Failure", customMode.getName());
    assertEquals(0.7, customMode.getCapacityFactor(), 0.001);
    assertEquals(0.85, customMode.getEfficiencyFactor(), 0.001);
    assertEquals(48.0, customMode.getMttr(), 0.001);
    assertEquals(0.5, customMode.getFailureFrequency(), 0.001);
    assertFalse(customMode.isRequiresImmediateAction());
  }

  @Test
  @DisplayName("Equipment base class failure mode support")
  void testEquipmentBaseClassFailureMode() {
    process.run();

    // Test trip simulation
    compressor1.simulateTrip();
    assertTrue(compressor1.isFailed());
    assertFalse(compressor1.isActive());
    assertFalse(compressor1.isCapacityAnalysisEnabled());
    assertEquals(0.0, compressor1.getEffectiveCapacityFactor(), 0.001);

    // Test restore
    compressor1.restoreFromFailure();
    assertFalse(compressor1.isFailed());
    assertTrue(compressor1.isActive());
    assertTrue(compressor1.isCapacityAnalysisEnabled());
    assertEquals(1.0, compressor1.getEffectiveCapacityFactor(), 0.001);

    // Test degraded operation
    compressor1.simulateDegradedOperation(60);
    assertTrue(compressor1.isFailed());
    assertEquals(0.6, compressor1.getEffectiveCapacityFactor(), 0.001);
  }

  @Test
  @DisplayName("Rank equipment by criticality")
  void testRankEquipmentByCriticality() {
    process.run();

    ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(process);
    analyzer.setFeedStreamName("Feed");
    analyzer.setProductStreamName("Stage 2 Compressor");

    List<ProductionImpactResult> ranking = analyzer.rankEquipmentByCriticality();

    assertNotNull(ranking);
    assertFalse(ranking.isEmpty());

    System.out.println("=== Equipment Criticality Ranking ===");
    for (int i = 0; i < ranking.size(); i++) {
      ProductionImpactResult result = ranking.get(i);
      System.out.printf("%d. %s: %.1f%% loss%n", i + 1, result.getEquipmentName(),
          result.getPercentLoss());
    }

    // First item should have highest loss
    if (ranking.size() > 1) {
      assertTrue(ranking.get(0).getPercentLoss() >= ranking.get(1).getPercentLoss());
    }
  }

  @Test
  @DisplayName("Production impact result calculations")
  void testProductionImpactResultCalculations() {
    ProductionImpactResult result =
        new ProductionImpactResult("Test Equipment", EquipmentFailureMode.trip("test"));

    result.setBaselineProductionRate(10000.0);
    result.setProductionWithFailure(7000.0);
    result.setFullShutdownProduction(0.0);
    result.setProductPricePerKg(0.50);
    result.setBaselinePower(1000.0);
    result.setPowerWithFailure(700.0);

    result.calculateDerivedMetrics();

    assertEquals(3000.0, result.getAbsoluteLoss(), 0.001);
    assertEquals(30.0, result.getPercentLoss(), 0.001);
    assertEquals(7000.0, result.getLossVsFullShutdown(), 0.001);
    assertEquals(1500.0, result.getEconomicLossPerHour(), 0.001);
    assertEquals(36000.0, result.getEconomicLossPerDay(), 0.001);
    assertEquals(300.0, result.getPowerSavings(), 0.001);

    // Should recommend reduced throughput since we still produce 70%
    assertEquals(RecommendedAction.REDUCE_THROUGHPUT, result.getRecommendedAction());
  }

  @Test
  @DisplayName("Production impact result to JSON")
  void testProductionImpactResultToJson() {
    ProductionImpactResult result =
        new ProductionImpactResult("Compressor 1", EquipmentFailureMode.trip("Compressor"));

    result.setBaselineProductionRate(50000.0);
    result.setProductionWithFailure(30000.0);
    result.setFullShutdownProduction(0.0);
    result.calculateDerivedMetrics();

    String json = result.toJson();

    assertNotNull(json);
    assertTrue(json.contains("Compressor 1"));
    assertTrue(json.contains("baselineProductionRate"));
    assertTrue(json.contains("percentLoss"));
    assertTrue(json.contains("recommendedAction"));

    System.out.println("=== JSON Output ===");
    System.out.println(json);
  }

  @Test
  @DisplayName("Multiple equipment failures")
  void testMultipleFailures() {
    process.run();

    ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(process);
    analyzer.setFeedStreamName("Feed");
    analyzer.setProductStreamName("Stage 2 Compressor");

    List<String> failedEquipment = java.util.Arrays.asList("Stage 1 Compressor", "Intercooler");

    ProductionImpactResult result = analyzer.analyzeMultipleFailures(failedEquipment);

    assertNotNull(result);
    assertTrue(result.getEquipmentName().contains("Stage 1 Compressor"));
    assertTrue(result.getEquipmentName().contains("Intercooler"));

    System.out.println("=== Multiple Failures ===");
    System.out.println(result);
  }

  @Test
  @DisplayName("Optimized setpoints recommendation")
  void testOptimizedSetpoints() {
    process.run();

    ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(process);
    analyzer.setFeedStreamName("Feed");
    analyzer.setProductStreamName("Stage 2 Compressor");
    analyzer.setOptimizeDegradedOperation(true);

    ProductionImpactResult result = analyzer.analyzeFailureImpact("Intercooler");

    // Check that optimized setpoints were calculated
    assertNotNull(result.getOptimizedSetpoints());

    System.out.println("=== Optimized Setpoints ===");
    for (java.util.Map.Entry<String, Double> entry : result.getOptimizedSetpoints().entrySet()) {
      System.out.printf("  %s: %.2f%n", entry.getKey(), entry.getValue());
    }
  }
}
