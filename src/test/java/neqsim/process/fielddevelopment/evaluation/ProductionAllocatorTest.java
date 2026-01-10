package neqsim.process.fielddevelopment.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.evaluation.ProductionAllocator.MeteringType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for ProductionAllocator.
 *
 * <p>
 * Tests production allocation calculations, metering uncertainty, and mass balance.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProductionAllocatorTest {
  private ProductionAllocator allocator;
  private Stream wellA;
  private Stream wellB;
  private Stream wellC;

  @BeforeEach
  void setUp() {
    allocator = new ProductionAllocator();

    // Create test wells with different compositions
    SystemInterface fluidA = new SystemSrkEos(288.15, 50.0);
    fluidA.addComponent("methane", 0.70);
    fluidA.addComponent("nC10", 0.30);
    fluidA.setMixingRule("classic");

    wellA = new Stream("Well-A", fluidA);
    wellA.setFlowRate(10000, "kg/hr");
    wellA.run();

    SystemInterface fluidB = new SystemSrkEos(288.15, 50.0);
    fluidB.addComponent("methane", 0.60);
    fluidB.addComponent("nC10", 0.40);
    fluidB.setMixingRule("classic");

    wellB = new Stream("Well-B", fluidB);
    wellB.setFlowRate(15000, "kg/hr");
    wellB.run();

    SystemInterface fluidC = new SystemSrkEos(288.15, 50.0);
    fluidC.addComponent("methane", 0.80);
    fluidC.addComponent("nC10", 0.20);
    fluidC.setMixingRule("classic");

    wellC = new Stream("Well-C", fluidC);
    wellC.setFlowRate(5000, "kg/hr");
    wellC.run();
  }

  @Test
  void testMeteringTypeUncertainties() {
    // Verify metering uncertainties
    assertEquals(0.005, MeteringType.ULTRASONIC.getUncertainty(), 0.001,
        "Ultrasonic uncertainty should be 0.5%");
    assertEquals(0.001, MeteringType.CORIOLIS.getUncertainty(), 0.0001,
        "Coriolis uncertainty should be 0.1%");
    assertEquals(0.03, MeteringType.MULTIPHASE.getUncertainty(), 0.001,
        "Multiphase uncertainty should be 3%");
  }

  @Test
  void testAddSources() {
    allocator.addSource("Well-A", wellA, MeteringType.MULTIPHASE);
    allocator.addSource("Well-B", wellB, MeteringType.MULTIPHASE);

    Map<String, Double> allocation = allocator.allocateByMass();

    assertEquals(2, allocation.size(), "Should have 2 sources");
    assertTrue(allocation.containsKey("Well-A"), "Should contain Well-A");
    assertTrue(allocation.containsKey("Well-B"), "Should contain Well-B");
  }

  @Test
  void testMassAllocation() {
    allocator.addSource("Well-A", wellA); // 10,000 kg/hr
    allocator.addSource("Well-B", wellB); // 15,000 kg/hr
    allocator.addSource("Well-C", wellC); // 5,000 kg/hr
    // Total: 30,000 kg/hr

    Map<String, Double> allocation = allocator.allocateByMass();

    assertEquals(10000.0 / 30000.0, allocation.get("Well-A"), 0.01,
        "Well-A should have 33.3% allocation");
    assertEquals(15000.0 / 30000.0, allocation.get("Well-B"), 0.01,
        "Well-B should have 50% allocation");
    assertEquals(5000.0 / 30000.0, allocation.get("Well-C"), 0.01,
        "Well-C should have 16.7% allocation");
  }

  @Test
  void testAllocationSumToOne() {
    allocator.addSource("Well-A", wellA);
    allocator.addSource("Well-B", wellB);
    allocator.addSource("Well-C", wellC);

    Map<String, Double> massAlloc = allocator.allocateByMass();
    Map<String, Double> gasAlloc = allocator.allocateByGas();
    Map<String, Double> oilAlloc = allocator.allocateByOil();
    Map<String, Double> energyAlloc = allocator.allocateByEnergy();

    double massSum = 0;
    for (double v : massAlloc.values()) {
      massSum += v;
    }
    assertEquals(1.0, massSum, 0.001, "Mass allocation should sum to 1.0");

    double gasSum = 0;
    for (double v : gasAlloc.values()) {
      gasSum += v;
    }
    assertEquals(1.0, gasSum, 0.001, "Gas allocation should sum to 1.0");
  }

  @Test
  void testOverallUncertainty() {
    allocator.addSource("Well-A", wellA, MeteringType.MULTIPHASE); // 3%
    allocator.addSource("Well-B", wellB, MeteringType.MULTIPHASE); // 3%

    double uncertainty = allocator.getOverallUncertainty();

    // RSS: sqrt(0.03^2 + 0.03^2) = sqrt(0.0018) = 0.0424
    assertTrue(uncertainty > 0.04 && uncertainty < 0.05, "RSS uncertainty should be ~4.2%");
  }

  @Test
  void testSourceUncertainty() {
    allocator.addSource("Well-A", wellA, MeteringType.CORIOLIS);
    allocator.addSource("Well-B", wellB, MeteringType.MULTIPHASE);

    assertEquals(0.001, allocator.getSourceUncertainty("Well-A"), 0.0001,
        "Well-A uncertainty should be Coriolis 0.1%");
    assertEquals(0.03, allocator.getSourceUncertainty("Well-B"), 0.001,
        "Well-B uncertainty should be Multiphase 3%");
  }

  @Test
  void testAllocatedWithUncertainty() {
    allocator.addSource("Well-A", wellA, MeteringType.MULTIPHASE);
    allocator.addSource("Well-B", wellB, MeteringType.MULTIPHASE);

    double exportVolume = 100000.0; // 100,000 Sm3

    double[] result = allocator.getAllocatedWithUncertainty("Well-A", exportVolume);

    assertEquals(3, result.length, "Should return [value, low, high]");
    assertTrue(result[1] < result[0], "Low should be less than value");
    assertTrue(result[2] > result[0], "High should be greater than value");
  }

  @Test
  void testMassImbalance() {
    allocator.addSource("Well-A", wellA);
    allocator.addSource("Well-B", wellB);

    // Create export meter (same fluid for simplicity)
    SystemInterface exportFluid = new SystemSrkEos(288.15, 50.0);
    exportFluid.addComponent("methane", 0.65);
    exportFluid.addComponent("nC10", 0.35);
    exportFluid.setMixingRule("classic");

    Stream exportStream = new Stream("Export", exportFluid);
    exportStream.setFlowRate(25000, "kg/hr"); // 10k + 15k = 25k
    exportStream.run();

    allocator.setExportMeter("Export", exportStream, MeteringType.ULTRASONIC);

    double imbalance = allocator.getMassImbalance();

    // (25000 - 25000) / 25000 = 0
    assertEquals(0.0, imbalance, 0.01, "Imbalance should be close to 0");
    assertTrue(allocator.isBalanceAcceptable(0.02), "Balance should be acceptable");
  }

  @Test
  void testImbalanceDetection() {
    allocator.addSource("Well-A", wellA); // 10k

    // Export meter shows less (leakage or metering error)
    SystemInterface exportFluid = new SystemSrkEos(288.15, 50.0);
    exportFluid.addComponent("methane", 0.70);
    exportFluid.addComponent("nC10", 0.30);
    exportFluid.setMixingRule("classic");

    Stream exportStream = new Stream("Export", exportFluid);
    exportStream.setFlowRate(9000, "kg/hr"); // 10% less
    exportStream.run();

    allocator.setExportMeter("Export", exportStream, MeteringType.ULTRASONIC);

    double imbalance = allocator.getMassImbalance();

    // (10000 - 9000) / 9000 = 0.111 = 11.1%
    assertTrue(Math.abs(imbalance) > 0.05, "Should detect significant imbalance");
    assertFalse(allocator.isBalanceAcceptable(0.02), "Imbalance should exceed 2%");
  }

  @Test
  void testReportGeneration() {
    allocator.addSource("Well-A", wellA, MeteringType.MULTIPHASE);
    allocator.addSource("Well-B", wellB, MeteringType.MULTIPHASE);

    String report = allocator.generateReport();

    assertNotNull(report, "Report should not be null");
    assertTrue(report.contains("Allocation"), "Report should contain 'Allocation'");
    assertTrue(report.contains("Well-A"), "Report should list sources");
    assertTrue(report.contains("Multiphase"), "Report should show meter types");
  }

  @Test
  void testEmptyAllocator() {
    Map<String, Double> allocation = allocator.allocateByMass();

    assertTrue(allocation.isEmpty(), "Empty allocator should return empty map");
    assertEquals(0.0, allocator.getOverallUncertainty(), 0.001,
        "Empty allocator should have 0 uncertainty");
  }

  @Test
  void testMeteringTypeDisplayNames() {
    assertEquals("Ultrasonic", MeteringType.ULTRASONIC.getDisplayName());
    assertEquals("Coriolis", MeteringType.CORIOLIS.getDisplayName());
    assertEquals("Multiphase", MeteringType.MULTIPHASE.getDisplayName());
    assertEquals("DP", MeteringType.DIFFERENTIAL_PRESSURE.getDisplayName());
  }

  @Test
  void testAllocatedVolumes() {
    allocator.addSource("Well-A", wellA); // 10k = 1/3
    allocator.addSource("Well-B", wellB); // 15k = 1/2
    allocator.addSource("Well-C", wellC); // 5k = 1/6

    double exportOil = 30000.0; // Sm3/d

    Map<String, Double> volumes = allocator.getAllocatedOilVolumes(exportOil);

    // Allocations based on oil fractions, allow wider tolerance for thermodynamic variations
    assertTrue(volumes.get("Well-A") > 8000 && volumes.get("Well-A") < 12000,
        "Well-A allocated volume");
    assertTrue(volumes.get("Well-B") > 12000 && volumes.get("Well-B") < 18000,
        "Well-B allocated volume");
    assertTrue(volumes.get("Well-C") > 3000 && volumes.get("Well-C") < 7000,
        "Well-C allocated volume");
  }
}
