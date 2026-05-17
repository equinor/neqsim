package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for DegradedOperationOptimizer.
 */
class DegradedOperationOptimizerTest {

  private ProcessSystem processSystem;
  private Stream feed;
  private Separator separator;
  private Compressor compressor;
  private Cooler cooler;
  private Stream exportStream;

  @BeforeEach
  void setUp() {
    // Create test process
    SystemInterface fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    processSystem = new ProcessSystem();

    feed = new Stream("Feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    processSystem.add(feed);

    separator = new Separator("Inlet Separator", feed);
    processSystem.add(separator);

    compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    processSystem.add(compressor);

    cooler = new Cooler("Export Cooler", compressor.getOutletStream());
    cooler.setOutTemperature(35.0, "C");
    processSystem.add(cooler);

    exportStream = new Stream("Export Gas", cooler.getOutletStream());
    processSystem.add(exportStream);

    processSystem.run();
  }

  @Test
  void testBasicOptimization() {
    DegradedOperationOptimizer optimizer = new DegradedOperationOptimizer(processSystem);
    optimizer.setFeedStreamName("Feed");
    optimizer.setProductStreamName("Export Gas");

    DegradedOperationResult result = optimizer.optimizeWithEquipmentDown("Export Compressor");

    assertNotNull(result);
    assertTrue(result.getCapacityFactor() <= 1.0, "Capacity factor should be <= 100%");
    assertTrue(result.getCapacityFactor() >= 0.0, "Capacity factor should be >= 0%");
  }

  @Test
  void testOptimizationWithMultipleFailures() {
    DegradedOperationOptimizer optimizer = new DegradedOperationOptimizer(processSystem);
    optimizer.setFeedStreamName("Feed");
    optimizer.setProductStreamName("Export Gas");

    java.util.List<String> failures = java.util.Arrays.asList("Export Compressor", "Export Cooler");
    DegradedOperationResult result = optimizer.optimizeWithMultipleFailures(failures);

    assertNotNull(result);
    assertNotNull(result.toJson());
  }

  @Test
  void testOperatingModeEvaluation() {
    DegradedOperationOptimizer optimizer = new DegradedOperationOptimizer(processSystem);
    optimizer.setFeedStreamName("Feed");
    optimizer.setProductStreamName("Export Gas");

    java.util.Map<DegradedOperationOptimizer.OperatingMode, Double> results =
        optimizer.evaluateOperatingModes("Export Compressor");

    assertNotNull(results);
    // Should have at least one operating mode
    assertTrue(results.size() >= 1);
  }

  @Test
  void testRecoveryPlan() {
    DegradedOperationOptimizer optimizer = new DegradedOperationOptimizer(processSystem);

    DegradedOperationOptimizer.RecoveryPlan plan =
        optimizer.createRecoveryPlan("Export Compressor");

    assertNotNull(plan);
    assertNotNull(plan.getFailedEquipment());
    assertTrue(plan.getEstimatedRecoveryTime() >= 0);
    assertNotNull(plan.getActions());
  }

  @Test
  void testResultToJson() {
    DegradedOperationOptimizer optimizer = new DegradedOperationOptimizer(processSystem);
    optimizer.setFeedStreamName("Feed");
    optimizer.setProductStreamName("Export Gas");

    DegradedOperationResult result = optimizer.optimizeWithEquipmentDown("Export Compressor");

    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("capacityFactor"));
    assertTrue(json.contains("productionLoss"));
  }
}
