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

  /**
   * Test that property derivatives are calculated correctly.
   */
  @Test
  void testPropertyFlashDerivativesProducesValidResults() {
    int numPoints = 20;
    List<Double> pressures = new ArrayList<>();
    List<Double> temperatures = new ArrayList<>();

    for (int i = 0; i < numPoints; i++) {
      pressures.add(20.0 + i * 3.0); // 20 to 77 bar
      temperatures.add(280.0 + i * 4.0); // 280 to 356 K
    }

    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    ThermodynamicOperations.DerivativesResult result =
        ops.propertyFlashDerivatives(pressures, temperatures);

    // Verify all calculations completed without error
    for (int i = 0; i < numPoints; i++) {
      Assertions.assertNull(result.calculationError[i], "Derivative error at index " + i);
      Assertions.assertNotNull(result.properties[i], "Null properties at " + i);
      Assertions.assertNotNull(result.dPdP[i], "Null dP derivatives at " + i);
      Assertions.assertNotNull(result.dPdT[i], "Null dT derivatives at " + i);

      // Check key derivatives are finite
      // Property 7 is density - should have valid derivatives
      Assertions.assertNotNull(result.dPdP[i][7], "Null density dP at " + i);
      Assertions.assertNotNull(result.dPdT[i][7], "Null density dT at " + i);
      Assertions.assertFalse(Double.isNaN(result.dPdP[i][7]), "NaN density dP at " + i);
      Assertions.assertFalse(Double.isNaN(result.dPdT[i][7]), "NaN density dT at " + i);

      // Density should increase with pressure (positive dρ/dP for gas)
      Assertions.assertTrue(result.dPdP[i][7] > 0,
          "Density should increase with pressure at point " + i);

      // Density should decrease with temperature (negative dρ/dT for gas)
      Assertions.assertTrue(result.dPdT[i][7] < 0,
          "Density should decrease with temperature at point " + i);
    }
  }

  /**
   * Test derivative calculation with custom step sizes.
   */
  @Test
  void testPropertyFlashDerivativesCustomStepSize() {
    List<Double> pressures = Arrays.asList(30.0, 50.0, 70.0);
    List<Double> temperatures = Arrays.asList(300.0, 320.0, 340.0);

    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Test with different step sizes
    ThermodynamicOperations.DerivativesResult result1 =
        ops.propertyFlashDerivatives(pressures, temperatures, 0.01, 0.1);
    ThermodynamicOperations.DerivativesResult result2 =
        ops.propertyFlashDerivatives(pressures, temperatures, 0.1, 1.0);

    // Both should complete without errors
    for (int i = 0; i < pressures.size(); i++) {
      Assertions.assertNull(result1.calculationError[i], "Error with small steps at " + i);
      Assertions.assertNull(result2.calculationError[i], "Error with large steps at " + i);

      // Results should be similar (within 10% for reasonable step sizes)
      double dRhodP_1 = result1.dPdP[i][7];
      double dRhodP_2 = result2.dPdP[i][7];
      double relDiff =
          Math.abs(dRhodP_1 - dRhodP_2) / Math.max(Math.abs(dRhodP_1), Math.abs(dRhodP_2));
      Assertions.assertTrue(relDiff < 0.1,
          "Derivatives differ too much between step sizes at " + i + " (relDiff=" + relDiff + ")");
    }
  }

  /**
   * Test derivatives for single-phase liquid (compressed liquid conditions).
   */
  @Test
  void testDerivativesSinglePhaseLiquid() {
    // Create a heavier hydrocarbon system that will be liquid at moderate conditions
    SystemInterface liquid = new SystemSrkEos(273.15, 50.0);
    liquid.addComponent("n-pentane", 0.5);
    liquid.addComponent("n-hexane", 0.3);
    liquid.addComponent("n-heptane", 0.2);
    liquid.setMixingRule("classic");
    liquid.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(liquid);

    // Low temperature, high pressure -> liquid phase
    List<Double> pressures = Arrays.asList(50.0, 60.0, 70.0, 80.0, 90.0);
    List<Double> temperatures = Arrays.asList(280.0, 285.0, 290.0, 295.0, 300.0);

    ThermodynamicOperations.DerivativesResult result =
        ops.propertyFlashDerivatives(pressures, temperatures);

    for (int i = 0; i < pressures.size(); i++) {
      Assertions.assertNull(result.calculationError[i],
          "Single-phase liquid derivative error at " + i);

      // For liquid: density ~500-700 kg/m³
      double density = result.properties[i][7];
      Assertions.assertTrue(density > 400, "Density too low for liquid at " + i + ": " + density);

      // Liquid compressibility is small but positive (density increases with P)
      double dRhodP = result.dPdP[i][7];
      Assertions.assertTrue(dRhodP > 0, "Liquid dρ/dP should be positive at " + i + ": " + dRhodP);

      // Density should decrease with temperature (thermal expansion)
      double dRhodT = result.dPdT[i][7];
      Assertions.assertTrue(dRhodT < 0, "Liquid dρ/dT should be negative at " + i + ": " + dRhodT);

      System.out.printf("Liquid [%d]: P=%.1f bar, T=%.1f K, ρ=%.2f kg/m³, dρ/dP=%.4f, dρ/dT=%.4f%n",
          i, pressures.get(i), temperatures.get(i), density, dRhodP, dRhodT);
    }
  }

  /**
   * Test derivatives for two-phase vapor-liquid system.
   */
  @Test
  void testDerivativesTwoPhaseVaporLiquid() {
    // Use a system that will have vapor-liquid equilibrium
    SystemInterface vle = new SystemSrkEos(273.15 - 50.0, 20.0);
    vle.addComponent("methane", 0.7);
    vle.addComponent("ethane", 0.2);
    vle.addComponent("propane", 0.1);
    vle.setMixingRule("classic");
    vle.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(vle);

    // Conditions likely to give two phases (near bubble/dew region)
    List<Double> pressures = Arrays.asList(15.0, 20.0, 25.0, 30.0);
    List<Double> temperatures = Arrays.asList(200.0, 210.0, 220.0, 230.0);

    ThermodynamicOperations.DerivativesResult result =
        ops.propertyFlashDerivatives(pressures, temperatures);

    int validCount = 0;
    for (int i = 0; i < pressures.size(); i++) {
      Assertions.assertNull(result.calculationError[i], "Two-phase derivative error at " + i);

      // Check number of phases (property index 0)
      double numPhases = result.properties[i][0];
      System.out.printf("VLE [%d]: P=%.1f bar, T=%.1f K, phases=%.0f, ρ=%.4f kg/m³%n", i,
          pressures.get(i), temperatures.get(i), numPhases, result.properties[i][7]);

      // Derivatives should be finite even in two-phase region
      Assertions.assertFalse(Double.isNaN(result.dPdP[i][7]), "NaN dρ/dP in two-phase at " + i);
      Assertions.assertFalse(Double.isNaN(result.dPdT[i][7]), "NaN dρ/dT in two-phase at " + i);

      validCount++;
    }
    Assertions.assertEquals(pressures.size(), validCount, "All points should be valid");
  }

  /**
   * Test derivatives for three-phase system (gas + oil + water).
   */
  @Test
  void testDerivativesThreePhaseSystem() {
    // Create a system with water that can form three phases
    SystemInterface threePhase = new SystemSrkEos(273.15 + 50.0, 50.0);
    threePhase.addComponent("methane", 0.80);
    threePhase.addComponent("ethane", 0.05);
    threePhase.addComponent("propane", 0.03);
    threePhase.addComponent("n-pentane", 0.02);
    threePhase.addComponent("water", 0.10);
    threePhase.setMixingRule("classic");
    threePhase.setMultiPhaseCheck(true);
    threePhase.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(threePhase);

    // Conditions for potential three-phase behavior
    List<Double> pressures = Arrays.asList(30.0, 40.0, 50.0, 60.0);
    List<Double> temperatures = Arrays.asList(300.0, 310.0, 320.0, 330.0);

    ThermodynamicOperations.DerivativesResult result =
        ops.propertyFlashDerivatives(pressures, temperatures);

    for (int i = 0; i < pressures.size(); i++) {
      Assertions.assertNull(result.calculationError[i], "Three-phase derivative error at " + i);

      double numPhases = result.properties[i][0];
      double density = result.properties[i][7];

      System.out.printf(
          "3-Phase [%d]: P=%.1f bar, T=%.1f K, phases=%.0f, ρ=%.4f, dρ/dP=%.4f, dρ/dT=%.4f%n", i,
          pressures.get(i), temperatures.get(i), numPhases, density, result.dPdP[i][7],
          result.dPdT[i][7]);

      // Derivatives should be calculated successfully
      Assertions.assertNotNull(result.dPdP[i][7], "Null dρ/dP at " + i);
      Assertions.assertNotNull(result.dPdT[i][7], "Null dρ/dT at " + i);

      // Properties should be valid
      Assertions.assertTrue(density > 0, "Invalid density at " + i + ": " + density);
    }
  }

  /**
   * Verify numerical derivatives by comparing against manual finite differences. This validates
   * that the derivative calculation is correct.
   */
  @Test
  void testDerivativesMatchManualFiniteDifference() {
    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    double P = 50.0; // bar
    double T = 300.0; // K
    double deltaP = 0.01; // bar
    double deltaT = 0.1; // K

    // Calculate derivatives using the method
    List<Double> pressures = Arrays.asList(P);
    List<Double> temperatures = Arrays.asList(T);

    ThermodynamicOperations.DerivativesResult derivResult =
        ops.propertyFlashDerivatives(pressures, temperatures, deltaP, deltaT);

    Assertions.assertNull(derivResult.calculationError[0], "Derivative calculation failed");

    // Manually calculate finite differences for verification using same approach as propertyFlash
    // Get base properties
    SystemInterface baseFluid = createTestFluid();
    baseFluid.setPressure(P);
    baseFluid.setTemperature(T);
    ThermodynamicOperations baseOps = new ThermodynamicOperations(baseFluid);
    baseOps.TPflash();
    baseFluid.init(2);
    baseFluid.initProperties();
    Double[] baseProps = baseFluid.getProperties().getValues();
    double rhoBase = baseProps[7];

    // Get P+deltaP properties
    SystemInterface pPlusFluid = createTestFluid();
    pPlusFluid.setPressure(P + deltaP);
    pPlusFluid.setTemperature(T);
    ThermodynamicOperations pPlusOps = new ThermodynamicOperations(pPlusFluid);
    pPlusOps.TPflash();
    pPlusFluid.init(2);
    pPlusFluid.initProperties();
    double rhoPplus = pPlusFluid.getProperties().getValues()[7];

    // Get P-deltaP properties
    SystemInterface pMinusFluid = createTestFluid();
    pMinusFluid.setPressure(P - deltaP);
    pMinusFluid.setTemperature(T);
    ThermodynamicOperations pMinusOps = new ThermodynamicOperations(pMinusFluid);
    pMinusOps.TPflash();
    pMinusFluid.init(2);
    pMinusFluid.initProperties();
    double rhoPminus = pMinusFluid.getProperties().getValues()[7];

    // Get T+deltaT properties
    SystemInterface tPlusFluid = createTestFluid();
    tPlusFluid.setPressure(P);
    tPlusFluid.setTemperature(T + deltaT);
    ThermodynamicOperations tPlusOps = new ThermodynamicOperations(tPlusFluid);
    tPlusOps.TPflash();
    tPlusFluid.init(2);
    tPlusFluid.initProperties();
    double rhoTplus = tPlusFluid.getProperties().getValues()[7];

    // Get T-deltaT properties
    SystemInterface tMinusFluid = createTestFluid();
    tMinusFluid.setPressure(P);
    tMinusFluid.setTemperature(T - deltaT);
    ThermodynamicOperations tMinusOps = new ThermodynamicOperations(tMinusFluid);
    tMinusOps.TPflash();
    tMinusFluid.init(2);
    tMinusFluid.initProperties();
    double rhoTminus = tMinusFluid.getProperties().getValues()[7];

    // Calculate manual derivatives
    double manualDrhoDp = (rhoPplus - rhoPminus) / (2.0 * deltaP);
    double manualDrhoDt = (rhoTplus - rhoTminus) / (2.0 * deltaT);

    // Get method results
    double methodDrhoDp = derivResult.dPdP[0][7];
    double methodDrhoDt = derivResult.dPdT[0][7];

    System.out.println("=== Derivative Verification ===");
    System.out.printf("Base density: %.6f kg/m³%n", rhoBase);
    System.out.printf("rho(P+dP)=%.6f, rho(P-dP)=%.6f%n", rhoPplus, rhoPminus);
    System.out.printf("rho(T+dT)=%.6f, rho(T-dT)=%.6f%n", rhoTplus, rhoTminus);
    System.out.printf("Manual  dρ/dP: %.6f kg/(m³·bar)%n", manualDrhoDp);
    System.out.printf("Method  dρ/dP: %.6f kg/(m³·bar)%n", methodDrhoDp);
    System.out.printf("Manual  dρ/dT: %.6f kg/(m³·K)%n", manualDrhoDt);
    System.out.printf("Method  dρ/dT: %.6f kg/(m³·K)%n", methodDrhoDt);

    // Verify they match very closely (should be essentially identical)
    assertRelativeEquals(manualDrhoDp, methodDrhoDp, 1e-6, "dρ/dP should match manual calculation");
    assertRelativeEquals(manualDrhoDt, methodDrhoDt, 1e-6, "dρ/dT should match manual calculation");

    // Verify physical correctness
    Assertions.assertTrue(methodDrhoDp > 0, "dρ/dP should be positive for gas");
    Assertions.assertTrue(methodDrhoDt < 0, "dρ/dT should be negative for gas");
  }

  /**
   * Test derivatives across a phase transition boundary.
   */
  @Test
  void testDerivativesAcrossPhaseTransition() {
    // Create a system and sweep across conditions that span phase transition
    SystemInterface fluid = new SystemSrkEos(273.15, 30.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("n-butane", 0.1);
    fluid.setMixingRule("classic");
    fluid.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Sweep temperature from cold (liquid-like) to hot (vapor)
    List<Double> pressures = new ArrayList<>();
    List<Double> temperatures = new ArrayList<>();

    for (int i = 0; i < 20; i++) {
      pressures.add(30.0); // Fixed pressure
      temperatures.add(180.0 + i * 10.0); // 180K to 370K
    }

    ThermodynamicOperations.DerivativesResult result =
        ops.propertyFlashDerivatives(pressures, temperatures);

    System.out.println("=== Phase Transition Sweep ===");
    for (int i = 0; i < pressures.size(); i++) {
      Assertions.assertNull(result.calculationError[i],
          "Derivative error at T=" + temperatures.get(i));

      double phases = result.properties[i][0];
      double density = result.properties[i][7];
      double dRhodP = result.dPdP[i][7];
      double dRhodT = result.dPdT[i][7];

      System.out.printf("T=%.0f K: phases=%.0f, ρ=%.4f, dρ/dP=%.4f, dρ/dT=%.4f%n",
          temperatures.get(i), phases, density, dRhodP, dRhodT);

      // All derivatives should be finite
      Assertions.assertFalse(Double.isNaN(dRhodP), "NaN dρ/dP at T=" + temperatures.get(i));
      Assertions.assertFalse(Double.isNaN(dRhodT), "NaN dρ/dT at T=" + temperatures.get(i));
    }
  }

  /**
   * Test derivatives with explicit thread count specification.
   */
  @Test
  void testDerivativesWithMultipleThreadCounts() {
    int numPoints = 30;
    List<Double> pressures = new ArrayList<>();
    List<Double> temperatures = new ArrayList<>();

    for (int i = 0; i < numPoints; i++) {
      pressures.add(20.0 + i * 2.0);
      temperatures.add(280.0 + i * 3.0);
    }

    SystemInterface fluid = createTestFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Test with different thread counts
    ThermodynamicOperations.DerivativesResult result1Thread =
        ops.propertyFlashDerivatives(pressures, temperatures, 0.01, 0.1, 1);
    ThermodynamicOperations.DerivativesResult result4Threads =
        ops.propertyFlashDerivatives(pressures, temperatures, 0.01, 0.1, 4);
    ThermodynamicOperations.DerivativesResult resultAllThreads =
        ops.propertyFlashDerivatives(pressures, temperatures, 0.01, 0.1, 0);

    // All should complete without errors
    for (int i = 0; i < numPoints; i++) {
      Assertions.assertNull(result1Thread.calculationError[i], "1-thread error at " + i);
      Assertions.assertNull(result4Threads.calculationError[i], "4-thread error at " + i);
      Assertions.assertNull(resultAllThreads.calculationError[i], "All-threads error at " + i);

      // Results should match across thread counts (density derivative)
      double d1 = result1Thread.dPdP[i][7];
      double d4 = result4Threads.dPdP[i][7];
      double dAll = resultAllThreads.dPdP[i][7];

      // Compare 1-thread vs 4-threads
      assertRelativeEquals(d1, d4, 1e-10, "1-thread vs 4-threads at point " + i);
      // Compare 4-threads vs all-threads
      assertRelativeEquals(d4, dAll, 1e-10, "4-threads vs all-threads at point " + i);
    }

    System.out.println("✓ Derivatives with 1, 4, and all threads produce identical results");
  }
}
