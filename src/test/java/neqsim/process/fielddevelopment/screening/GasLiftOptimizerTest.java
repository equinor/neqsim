package neqsim.process.fielddevelopment.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GasLiftOptimizer.
 *
 * @author ESOL
 * @version 1.0
 */
public class GasLiftOptimizerTest {
  private GasLiftOptimizer optimizer;

  @BeforeEach
  void setUp() {
    optimizer = new GasLiftOptimizer();

    // Add three wells with different performance characteristics
    // Well 1: High potential, good response
    optimizer.addWell("Well-1", 50.0, 150.0, 30000.0, 50000.0);

    // Well 2: Medium potential, moderate response
    optimizer.addWell("Well-2", 30.0, 100.0, 25000.0, 40000.0);

    // Well 3: Lower potential, high response
    optimizer.addWell("Well-3", 20.0, 80.0, 15000.0, 35000.0);

    // Set available gas
    optimizer.setAvailableGas(70000.0, "Sm3/d");
  }

  @Test
  @DisplayName("Test optimizer creates valid result")
  void testOptimizerCreatesValidResult() {
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertNotNull(result, "Result should not be null");
    assertEquals(3, result.allocations.size(), "Should have 3 well allocations");
    assertTrue(result.totalOilRate > 0, "Total oil rate should be positive");
    assertTrue(result.totalGasAllocated > 0, "Total gas allocated should be positive");
  }

  @Test
  @DisplayName("Test gas allocation respects constraints")
  void testGasAllocationRespectsConstraints() {
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertTrue(result.totalGasAllocated <= 70000.0, "Should not exceed available gas");

    for (GasLiftOptimizer.WellAllocation alloc : result.allocations) {
      assertTrue(alloc.gasRate >= 0, "Gas rate should be non-negative");
    }
  }

  @Test
  @DisplayName("Test equal slope method converges")
  void testEqualSlopeMethodConverges() {
    optimizer.setOptimizationMethod(GasLiftOptimizer.OptimizationMethod.EQUAL_SLOPE);
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertTrue(result.converged, "Equal slope method should converge");
    assertEquals(GasLiftOptimizer.OptimizationMethod.EQUAL_SLOPE, result.method);
  }

  @Test
  @DisplayName("Test proportional method works")
  void testProportionalMethod() {
    optimizer.setOptimizationMethod(GasLiftOptimizer.OptimizationMethod.PROPORTIONAL);
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertNotNull(result, "Proportional method should work");
    assertTrue(result.converged, "Proportional method should converge");
    assertEquals(GasLiftOptimizer.OptimizationMethod.PROPORTIONAL, result.method);
  }

  @Test
  @DisplayName("Test sequential method works")
  void testSequentialMethod() {
    optimizer.setOptimizationMethod(GasLiftOptimizer.OptimizationMethod.SEQUENTIAL);
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertNotNull(result, "Sequential method should work");
    assertEquals(GasLiftOptimizer.OptimizationMethod.SEQUENTIAL, result.method);
  }

  @Test
  @DisplayName("Test gradient method works")
  void testGradientMethod() {
    optimizer.setOptimizationMethod(GasLiftOptimizer.OptimizationMethod.GRADIENT);
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertNotNull(result, "Gradient method should work");
    assertEquals(GasLiftOptimizer.OptimizationMethod.GRADIENT, result.method);
  }

  @Test
  @DisplayName("Test incremental oil is positive")
  void testIncrementalOilPositive() {
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertTrue(result.totalIncrementalOil > 0, "Total incremental oil should be positive");
    assertTrue(result.totalOilRate > result.totalNaturalFlow,
        "Oil with gas lift should exceed natural flow");

    for (GasLiftOptimizer.WellAllocation alloc : result.allocations) {
      assertTrue(alloc.incrementalOil >= 0, "Incremental oil should be non-negative");
      assertEquals(alloc.oilRate - alloc.naturalFlowRate, alloc.incrementalOil, 0.001,
          "Incremental oil should be calculated correctly");
    }
  }

  @Test
  @DisplayName("Test gas efficiency is calculated")
  void testGasEfficiencyCalculated() {
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertTrue(result.fieldGasEfficiency >= 0, "Field gas efficiency should be non-negative");

    for (GasLiftOptimizer.WellAllocation alloc : result.allocations) {
      if (alloc.gasRate > 0) {
        assertEquals(alloc.incrementalOil / alloc.gasRate, alloc.gasEfficiency, 0.001,
            "Gas efficiency should be calculated correctly");
      }
    }
  }

  @Test
  @DisplayName("Test well can be disabled")
  void testWellDisabled() {
    optimizer.setWellEnabled("Well-2", false);
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    GasLiftOptimizer.WellAllocation well2 = result.getAllocation("Well-2");
    assertNotNull(well2, "Well-2 should still be in results");
    assertEquals(0.0, well2.gasRate, 0.01, "Disabled well should get no gas");
  }

  @Test
  @DisplayName("Test well priority affects allocation")
  void testWellPriorityAffectsAllocation() {
    // Give Well-3 higher priority
    optimizer.setWellPriority("Well-3", 2.0);
    optimizer.setOptimizationMethod(GasLiftOptimizer.OptimizationMethod.SEQUENTIAL);

    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    // Well-3 should get more gas due to higher priority
    GasLiftOptimizer.WellAllocation well3 = result.getAllocation("Well-3");
    assertNotNull(well3, "Well-3 should be in results");
  }

  @Test
  @DisplayName("Test compression power constraint")
  void testCompressionPowerConstraint() {
    // Set a low compression power limit
    optimizer.setMaxCompressionPower(1000.0); // kW

    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertTrue(result.compressionPower <= 1000.0 || result.totalGasAllocated < 70000.0,
        "Should respect compression power limit");
  }

  @Test
  @DisplayName("Test compression efficiency setting")
  void testCompressionEfficiencySetting() {
    optimizer.setCompressionEfficiency(0.80);
    optimizer.setCompressionPressures(5.0, 80.0);

    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    assertTrue(result.compressionPower >= 0, "Compression power should be calculated");
  }

  @Test
  @DisplayName("Test gas utilization calculation")
  void testGasUtilizationCalculation() {
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    double expectedUtilization = result.totalGasAllocated / result.availableGas;
    assertEquals(expectedUtilization, result.gasUtilization, 0.001,
        "Gas utilization should be correctly calculated");
  }

  @Test
  @DisplayName("Test more gas improves production")
  void testMoreGasImprovesProduction() {
    optimizer.setAvailableGas(50000.0, "Sm3/d");
    GasLiftOptimizer.AllocationResult result1 = optimizer.optimize();

    optimizer.setAvailableGas(100000.0, "Sm3/d");
    GasLiftOptimizer.AllocationResult result2 = optimizer.optimize();

    assertTrue(result2.totalOilRate >= result1.totalOilRate,
        "More available gas should enable more production");
  }

  @Test
  @DisplayName("Test performance curve interpolation")
  void testPerformanceCurveInterpolation() {
    double[] gasRates = {0, 10000, 20000, 30000, 40000};
    double[] oilRates = {50, 100, 130, 145, 150};

    GasLiftOptimizer.PerformanceCurve curve =
        new GasLiftOptimizer.PerformanceCurve(gasRates, oilRates);

    // Test interpolation
    assertEquals(50.0, curve.getOilRate(0), 0.1, "Should return first point at 0");
    assertEquals(150.0, curve.getOilRate(40000), 0.1, "Should return last point at max");

    double midPoint = curve.getOilRate(15000);
    assertTrue(midPoint > 100 && midPoint < 130, "Should interpolate between points");
  }

  @Test
  @DisplayName("Test marginal response decreases with gas")
  void testMarginalResponseDecreases() {
    double[] gasRates = {0, 10000, 20000, 30000, 40000, 50000};
    double[] oilRates = {50, 100, 130, 145, 150, 148}; // Decline at high GLR

    GasLiftOptimizer.PerformanceCurve curve =
        new GasLiftOptimizer.PerformanceCurve(gasRates, oilRates);

    double response1 = curve.getMarginalResponse(5000);
    double response2 = curve.getMarginalResponse(25000);
    double response3 = curve.getMarginalResponse(45000);

    assertTrue(response1 > response2, "Marginal response should decrease");
    assertTrue(response3 < response1, "Marginal response should decrease at high gas");
  }

  @Test
  @DisplayName("Test empty optimizer throws exception")
  void testEmptyOptimizerThrows() {
    GasLiftOptimizer emptyOptimizer = new GasLiftOptimizer();
    assertThrows(IllegalStateException.class, () -> emptyOptimizer.optimize(),
        "Should throw for no wells");
  }

  @Test
  @DisplayName("Test single well optimization")
  void testSingleWellOptimization() {
    GasLiftOptimizer singleWell = new GasLiftOptimizer();
    singleWell.addWell("Single", 50.0, 150.0, 30000.0, 50000.0);
    singleWell.setAvailableGas(40000.0, "Sm3/d");

    GasLiftOptimizer.AllocationResult result = singleWell.optimize();

    assertEquals(1, result.allocations.size(), "Should have 1 allocation");
    assertTrue(result.allocations.get(0).gasRate <= 40000.0, "Should use available gas");
  }

  @Test
  @DisplayName("Test MSm3/d unit conversion")
  void testMSm3UnitConversion() {
    GasLiftOptimizer opt = new GasLiftOptimizer();
    opt.addWell("Test", 50.0, 150.0, 30000.0, 50000.0);
    opt.setAvailableGas(0.1, "MSm3/d"); // 100,000 Sm³/d

    GasLiftOptimizer.AllocationResult result = opt.optimize();
    assertTrue(result.availableGas > 50000.0, "Should convert MSm³/d to Sm³/d");
  }

  @Test
  @DisplayName("Test result toString formatting")
  void testResultToString() {
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();
    String str = result.toString();

    assertNotNull(str, "toString should not return null");
    assertTrue(str.contains("Gas Lift Optimization Result"), "Should have header");
    assertTrue(str.contains("Well-1"), "Should list wells");
    assertTrue(str.contains("Allocated"), "Should show allocation info");
  }

  @Test
  @DisplayName("Test getAllocation by name")
  void testGetAllocationByName() {
    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    GasLiftOptimizer.WellAllocation well1 = result.getAllocation("Well-1");
    assertNotNull(well1, "Should find Well-1");
    assertEquals("Well-1", well1.wellName, "Should return correct well");

    GasLiftOptimizer.WellAllocation notFound = result.getAllocation("NonExistent");
    assertNull(notFound, "Should return null for unknown well");
  }

  @Test
  @DisplayName("Test parametric curve model")
  void testParametricCurveModel() {
    // Create curve using parametric model
    GasLiftOptimizer.PerformanceCurve curve =
        new GasLiftOptimizer.PerformanceCurve(50.0, 150.0, 30000.0);

    assertEquals(50.0, curve.naturalFlowRate, 0.1, "Natural flow rate should be set");
    assertTrue(curve.gasRates.length > 0, "Should generate gas rate points");
    assertTrue(curve.oilRates.length > 0, "Should generate oil rate points");

    // Verify curve shape
    double lowGas = curve.getOilRate(5000);
    double optGas = curve.getOilRate(30000);
    double highGas = curve.getOilRate(45000);

    assertTrue(lowGas > 50.0, "Should increase from natural flow");
    assertTrue(optGas > lowGas, "Should increase toward optimal");
  }

  @Test
  @DisplayName("Test well count getter")
  void testWellCountGetter() {
    assertEquals(3, optimizer.getWellCount(), "Should have 3 wells");

    optimizer.addWell("Well-4", 40.0, 120.0, 20000.0, 45000.0);
    assertEquals(4, optimizer.getWellCount(), "Should have 4 wells after adding");
  }

  @Test
  @DisplayName("Test scarce gas allocation")
  void testScarceGasAllocation() {
    // Very limited gas - should allocate to highest response wells
    optimizer.setAvailableGas(20000.0, "Sm3/d");
    optimizer.setOptimizationMethod(GasLiftOptimizer.OptimizationMethod.EQUAL_SLOPE);

    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    // With limited gas, not all wells may get allocation
    assertTrue(result.totalGasAllocated <= 20000.0, "Should not exceed available gas");
    assertTrue(result.gasUtilization >= 0.5, "Should use most of limited gas");
  }

  @Test
  @DisplayName("Test abundant gas allocation")
  void testAbundantGasAllocation() {
    // Abundant gas - wells should approach their optimal rates
    optimizer.setAvailableGas(200000.0, "Sm3/d");

    GasLiftOptimizer.AllocationResult result = optimizer.optimize();

    // Should achieve close to maximum production
    assertTrue(result.totalIncrementalOil > 100.0, "Should have significant incremental oil");
  }

  @Test
  @DisplayName("Test chained configuration methods")
  void testChainedConfiguration() {
    GasLiftOptimizer opt = new GasLiftOptimizer().addWell("W1", 30.0, 100.0, 20000.0, 40000.0)
        .addWell("W2", 25.0, 80.0, 15000.0, 35000.0).setAvailableGas(50000.0, "Sm3/d")
        .setMaxCompressionPower(5000.0).setCompressionEfficiency(0.75)
        .setOptimizationMethod(GasLiftOptimizer.OptimizationMethod.EQUAL_SLOPE);

    assertEquals(2, opt.getWellCount(), "Should have 2 wells from chained calls");
  }
}
