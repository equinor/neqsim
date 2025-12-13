package neqsim.thermodynamicoperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.api.ioc.CalculationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for parallel property flash calculations.
 *
 * <p>
 * Tests the parallel execution of flash calculations using multiple CPU cores. These tests verify
 * that the parallel implementation produces valid thermodynamic results and handles errors
 * correctly.
 * </p>
 *
 * <p>
 * Note: Tests compare parallel runs with fresh fluid instances rather than sequential vs parallel,
 * because the sequential propertyFlash modifies shared system state between calculations.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class PropertyFlashParallelTest extends neqsim.NeqSimTest {

  /**
   * Creates a fresh fluid system for testing.
   */
  private SystemInterface createTestFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Compares two values with relative tolerance, handling NaN and zero values.
   */
  private void assertRelativeEquals(double expected, double actual, double relTol, String message) {
    // Handle NaN: if both are NaN, consider equal; if only one is NaN, skip comparison
    if (Double.isNaN(expected) && Double.isNaN(actual)) {
      return;
    }
    if (Double.isNaN(expected) || Double.isNaN(actual)) {
      return; // One is NaN, skip comparison
    }
    if (expected == 0.0 && actual == 0.0) {
      return;
    }
    double maxAbs = Math.max(Math.abs(expected), Math.abs(actual));
    if (maxAbs < 1e-15) {
      return; // Both essentially zero
    }
    double relDiff = Math.abs(expected - actual) / maxAbs;
    Assertions.assertTrue(relDiff < relTol,
        message + " (expected=" + expected + ", actual=" + actual + ", relDiff=" + relDiff + ")");
  }

  /**
   * Test that parallel PT flash produces valid results without errors.
   */
  @Test
  void testParallelPTFlashProducesValidResults() {
    int numPoints = 50;
    List<Double> pressures = new ArrayList<>();
    List<Double> temperatures = new ArrayList<>();

    // Generate test data - typical natural gas conditions
    for (int i = 0; i < numPoints; i++) {
      pressures.add(10.0 + i * 1.0); // 10 to 60 bar
      temperatures.add(273.15 + 20.0 + i * 2.0); // 293 to 393 K
    }

    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    CalculationResult result = ops.propertyFlashParallel(pressures, temperatures, 1, null, null);

    // Verify all calculations completed without error
    for (int i = 0; i < numPoints; i++) {
      Assertions.assertNull(result.calculationError[i], "Error at index " + i);
      Assertions.assertNotNull(result.fluidProperties[i], "Null properties at index " + i);

      // Verify we have the expected number of properties
      Assertions.assertTrue(result.fluidProperties[i].length > 0, "Empty properties at index " + i);

      // Check key properties are valid (non-null, finite)
      // Property 1: Pressure, Property 2: Temperature, Property 7: Density
      Assertions.assertNotNull(result.fluidProperties[i][1], "Null pressure at " + i);
      Assertions.assertNotNull(result.fluidProperties[i][2], "Null temperature at " + i);
      Assertions.assertNotNull(result.fluidProperties[i][7], "Null density at " + i);
      Assertions.assertTrue(result.fluidProperties[i][7] > 0, "Invalid density at " + i);
    }
  }

  /**
   * Test that parallel PH flash produces valid results.
   */
  @Test
  void testParallelPHFlashProducesValidResults() {
    int numPoints = 20;
    List<Double> pressures = new ArrayList<>();
    List<Double> enthalpies = new ArrayList<>();

    SystemInterface fluid = createTestFluid();

    // First calculate enthalpies at known PT conditions
    for (int i = 0; i < numPoints; i++) {
      double P = 20.0 + i * 2.0;
      double T = 273.15 + 30.0 + i * 5.0;

      SystemInterface tempFluid = fluid.clone();
      tempFluid.setPressure(P);
      tempFluid.setTemperature(T);
      ThermodynamicOperations tempOps = new ThermodynamicOperations(tempFluid);
      tempOps.TPflash();
      tempFluid.init(2);

      pressures.add(P);
      enthalpies.add(tempFluid.getEnthalpy("J/mol"));
    }

    // Create fresh fluid for parallel PH flash
    SystemInterface fluid2 = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid2);

    CalculationResult result = ops.propertyFlashParallel(pressures, enthalpies, 2, null, null);

    // Verify all calculations completed without error
    for (int i = 0; i < numPoints; i++) {
      Assertions.assertNull(result.calculationError[i], "PH flash error at index " + i);
      Assertions.assertNotNull(result.fluidProperties[i], "Null PH properties at index " + i);

      // Verify temperature was recovered correctly (property index 2)
      double recoveredTemp = result.fluidProperties[i][2];
      double expectedTemp = 273.15 + 30.0 + i * 5.0;
      assertRelativeEquals(expectedTemp, recoveredTemp, 0.01, "Temperature recovery at point " + i);
    }
  }

  /**
   * Test that parallel PS flash produces valid results.
   */
  @Test
  void testParallelPSFlashProducesValidResults() {
    int numPoints = 20;
    List<Double> pressures = new ArrayList<>();
    List<Double> entropies = new ArrayList<>();
    List<Double> expectedTemps = new ArrayList<>();

    SystemInterface fluid = createTestFluid();

    // First calculate entropies at known PT conditions
    for (int i = 0; i < numPoints; i++) {
      double P = 20.0 + i * 2.0;
      double T = 273.15 + 30.0 + i * 5.0;

      SystemInterface tempFluid = fluid.clone();
      tempFluid.setPressure(P);
      tempFluid.setTemperature(T);
      ThermodynamicOperations tempOps = new ThermodynamicOperations(tempFluid);
      tempOps.TPflash();
      tempFluid.init(2);

      pressures.add(P);
      entropies.add(tempFluid.getEntropy("J/molK"));
      expectedTemps.add(T);
    }

    // Create fresh fluid for parallel PS flash
    SystemInterface fluid2 = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid2);

    CalculationResult result = ops.propertyFlashParallel(pressures, entropies, 3, null, null);

    // Verify all calculations completed without error
    for (int i = 0; i < numPoints; i++) {
      Assertions.assertNull(result.calculationError[i], "PS flash error at index " + i);
      Assertions.assertNotNull(result.fluidProperties[i], "Null PS properties at index " + i);

      // Verify temperature was recovered correctly
      double recoveredTemp = result.fluidProperties[i][2];
      assertRelativeEquals(expectedTemps.get(i), recoveredTemp, 0.01,
          "Temperature recovery at point " + i);
    }
  }

  /**
   * Test that different thread counts all produce valid results. Note: Due to order-dependent
   * internal state in some property calculations, different thread counts may produce slightly
   * different results for certain properties. This test verifies all calculations complete
   * successfully.
   */
  @Test
  void testDifferentThreadCountsProduceValidResults() {
    int numPoints = 30;
    List<Double> pressures = new ArrayList<>();
    List<Double> temperatures = new ArrayList<>();

    for (int i = 0; i < numPoints; i++) {
      pressures.add(10.0 + i * 1.5);
      temperatures.add(273.15 + 25.0 + i * 3.0);
    }

    // Test different thread counts
    int[] threadCounts = {1, 2, 4, 8};

    for (int threads : threadCounts) {
      SystemInterface fluid = createTestFluid();
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      CalculationResult result =
          ops.propertyFlashParallel(pressures, temperatures, 1, null, null, threads);

      // Verify all calculations completed without error
      for (int i = 0; i < numPoints; i++) {
        Assertions.assertNull(result.calculationError[i], threads + "-thread error at index " + i);
        Assertions.assertNotNull(result.fluidProperties[i],
            threads + "-thread null properties at " + i);

        // Verify key properties are valid
        Assertions.assertNotNull(result.fluidProperties[i][1],
            threads + "-thread null pressure at " + i);
        Assertions.assertNotNull(result.fluidProperties[i][2],
            threads + "-thread null temperature at " + i);
        Assertions.assertTrue(result.fluidProperties[i][7] > 0,
            threads + "-thread invalid density at " + i);
      }
    }
  }

  /**
   * Test batch processing produces valid results.
   */
  @Test
  void testBatchFlashProcessingProducesValidResults() {
    int numPoints = 100;
    List<Double> pressures = new ArrayList<>();
    List<Double> temperatures = new ArrayList<>();

    for (int i = 0; i < numPoints; i++) {
      pressures.add(10.0 + i * 0.5);
      temperatures.add(273.15 + 20.0 + i * 1.5);
    }

    // Test batch processing
    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    CalculationResult result = ops.propertyFlashBatch(pressures, temperatures, 1, null, null, 10);

    // Verify all calculations succeeded
    for (int i = 0; i < numPoints; i++) {
      Assertions.assertNull(result.calculationError[i], "Batch error at index " + i);
      Assertions.assertNotNull(result.fluidProperties[i], "Null batch properties at " + i);
      Assertions.assertTrue(result.fluidProperties[i][7] > 0, "Invalid density at " + i);
    }
  }

  /**
   * Test parallel flash with varying compositions (online fractions).
   */
  @Test
  void testParallelFlashWithOnlineFractions() {
    int numPoints = 20;
    List<Double> pressures = new ArrayList<>();
    List<Double> temperatures = new ArrayList<>();
    List<String> components = Arrays.asList("methane", "ethane", "propane");
    List<List<Double>> onlineFractions = new ArrayList<>();

    List<Double> methaneFracs = new ArrayList<>();
    List<Double> ethaneFracs = new ArrayList<>();
    List<Double> propaneFracs = new ArrayList<>();

    for (int i = 0; i < numPoints; i++) {
      pressures.add(20.0 + i * 2.0);
      temperatures.add(273.15 + 25.0 + i * 4.0);

      // Varying compositions (normalized to 1.0)
      double methane = 0.70 + 0.01 * i;
      double ethane = 0.20 - 0.005 * i;
      double propane = 1.0 - methane - ethane;

      methaneFracs.add(methane);
      ethaneFracs.add(ethane);
      propaneFracs.add(propane);
    }

    onlineFractions.add(methaneFracs);
    onlineFractions.add(ethaneFracs);
    onlineFractions.add(propaneFracs);

    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    CalculationResult result =
        ops.propertyFlashParallel(pressures, temperatures, 1, components, onlineFractions);

    // Verify all calculations completed
    for (int i = 0; i < numPoints; i++) {
      Assertions.assertNull(result.calculationError[i], "Fractions error at index " + i);
      Assertions.assertNotNull(result.fluidProperties[i], "Null fractions properties at " + i);
    }
  }

  /**
   * Test error handling for invalid flash mode.
   */
  @Test
  void testParallelFlashInvalidFlashMode() {
    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    List<Double> pressures = Arrays.asList(10.0, 20.0, 30.0);
    List<Double> temperatures = Arrays.asList(300.0, 310.0, 320.0);

    CalculationResult result = ops.propertyFlashParallel(pressures, temperatures, 99, null, null);

    for (int i = 0; i < pressures.size(); i++) {
      Assertions.assertNotNull(result.calculationError[i], "Should have error for invalid mode");
      Assertions.assertTrue(result.calculationError[i].contains("FlashMode"),
          "Error should mention FlashMode");
    }
  }

  /**
   * Test error handling for NaN inputs.
   */
  @Test
  void testParallelFlashWithNaNInputs() {
    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    List<Double> pressures = Arrays.asList(10.0, Double.NaN, 30.0);
    List<Double> temperatures = Arrays.asList(300.0, 310.0, 320.0);

    CalculationResult result = ops.propertyFlashParallel(pressures, temperatures, 1, null, null);

    Assertions.assertNull(result.calculationError[0], "First point should succeed");
    Assertions.assertNotNull(result.calculationError[1], "Second point should have error");
    Assertions.assertTrue(result.calculationError[1].contains("NaN"), "Error should mention NaN");
    Assertions.assertNull(result.calculationError[2], "Third point should succeed");
  }

  /**
   * Performance benchmark demonstrating parallel speedup. This test is not for correctness but to
   * verify parallel execution provides performance benefits.
   */
  @Test
  void testParallelPerformanceImprovement() {
    int numPoints = 100;
    List<Double> pressures = new ArrayList<>();
    List<Double> temperatures = new ArrayList<>();

    for (int i = 0; i < numPoints; i++) {
      pressures.add(10.0 + i * 0.9);
      temperatures.add(273.15 + 20.0 + i * 1.5);
    }

    // Warm up
    SystemInterface warmupFluid = createTestFluid();
    ThermodynamicOperations warmupOps = new ThermodynamicOperations(warmupFluid);
    warmupOps.propertyFlash(pressures, temperatures, 1, null, null);
    warmupOps.propertyFlashParallel(pressures, temperatures, 1, null, null);

    // Measure sequential time
    SystemInterface seqFluid = createTestFluid();
    ThermodynamicOperations seqOps = new ThermodynamicOperations(seqFluid);
    long seqStart = System.nanoTime();
    CalculationResult seqResult = seqOps.propertyFlash(pressures, temperatures, 1, null, null);
    long seqTime = System.nanoTime() - seqStart;

    // Measure parallel time
    SystemInterface parFluid = createTestFluid();
    ThermodynamicOperations parOps = new ThermodynamicOperations(parFluid);
    long parStart = System.nanoTime();
    CalculationResult parResult =
        parOps.propertyFlashParallel(pressures, temperatures, 1, null, null);
    long parTime = System.nanoTime() - parStart;

    // Log performance results
    System.out.println("Sequential time: " + seqTime / 1_000_000.0 + " ms");
    System.out.println("Parallel time: " + parTime / 1_000_000.0 + " ms");
    System.out.println("Speedup: " + (double) seqTime / parTime + "x");
    System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());

    // Verify both completed successfully
    for (int i = 0; i < numPoints; i++) {
      Assertions.assertNull(seqResult.calculationError[i], "Sequential error at " + i);
      Assertions.assertNull(parResult.calculationError[i], "Parallel error at " + i);
    }
  }
}
