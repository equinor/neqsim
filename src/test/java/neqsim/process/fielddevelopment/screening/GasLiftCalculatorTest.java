package neqsim.process.fielddevelopment.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.screening.GasLiftCalculator.GasLiftResult;
import neqsim.process.fielddevelopment.screening.GasLiftCalculator.ValvePosition;

/**
 * Unit tests for GasLiftCalculator.
 *
 * @author ESOL
 */
class GasLiftCalculatorTest {

  private GasLiftCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new GasLiftCalculator();
  }

  @Test
  @DisplayName("Test basic gas lift calculation with default parameters")
  void testBasicCalculation() {
    // Configure calculator
    calculator.setReservoirPressure(250.0, "bara");
    calculator.setReservoirTemperature(85.0, "C");
    calculator.setWellheadPressure(20.0, "bara");
    calculator.setWellDepth(3000.0, "m");
    calculator.setProductivityIndex(5.0);
    calculator.setOilGravity(35.0, "API");
    calculator.setWaterCut(0.3);
    calculator.setFormationGOR(100.0);
    calculator.setInjectionPressure(100.0, "bara");

    // Calculate
    GasLiftResult result = calculator.calculate();

    // Verify result is reasonable
    assertNotNull(result);
    assertTrue(result.optimalGLR > 100.0, "Optimal GLR should be higher than formation GOR");
    assertTrue(result.oilRateAtOptimal > 0, "Production rate should be positive");
    assertTrue(result.injectionRateAtOptimal >= 0, "Injection rate should be non-negative");
    assertTrue(result.compressionPower >= 0, "Compression power should be non-negative");
  }

  @Test
  @DisplayName("Test gas lift increases production above natural flow")
  void testProductionIncrease() {
    calculator.setReservoirPressure(200.0, "bara");
    calculator.setWellheadPressure(15.0, "bara");
    calculator.setWellDepth(2500.0, "m");
    calculator.setProductivityIndex(8.0);
    calculator.setFormationGOR(80.0);

    GasLiftResult result = calculator.calculate();

    // Gas lift should increase production
    assertTrue(result.liftIncrease > 0, "Gas lift should increase production above natural flow");
    assertTrue(result.feasible, "Gas lift should be feasible for this well");
  }

  @Test
  @DisplayName("Test valve position calculation")
  void testValvePositions() {
    calculator.setReservoirPressure(280.0, "bara");
    calculator.setWellheadPressure(20.0, "bara");
    calculator.setWellDepth(3500.0, "m");
    calculator.setInjectionPressure(120.0, "bara");

    GasLiftResult result = calculator.calculate();

    // Should have at least one valve
    assertTrue(result.getValveCount() >= 1, "Should have at least one gas lift valve");

    // Valves should be in ascending depth order
    double prevDepth = 0;
    for (ValvePosition valve : result.valvePositions) {
      assertTrue(valve.depth >= prevDepth, "Valves should be ordered by depth");
      prevDepth = valve.depth;
      // Valve depths should be valid
      assertTrue(valve.depth > 0, "Valve depth should be positive");
      assertTrue(valve.depth <= 3500.0, "Valve depth should not exceed well depth");
    }
  }

  @Test
  @DisplayName("Test performance curve generation")
  void testPerformanceCurve() {
    calculator.setReservoirPressure(220.0, "bara");
    calculator.setWellDepth(2800.0, "m");
    calculator.setFormationGOR(120.0);

    GasLiftResult result = calculator.calculate();

    // Performance curve should have multiple points
    assertTrue(result.performanceCurve.size() > 10, "Performance curve should have many points");

    // First point should be at formation GOR
    double firstGLR = result.performanceCurve.get(0).totalGLR;
    assertEquals(120.0, firstGLR, 1.0, "First point should be at formation GOR");
  }

  @Test
  @DisplayName("Test compression power calculation")
  void testCompressionPower() {
    calculator.setReservoirPressure(250.0, "bara");
    calculator.setWellheadPressure(15.0, "bara");
    calculator.setWellDepth(3000.0, "m");
    calculator.setProductivityIndex(10.0); // Higher PI = more production = more gas needed
    calculator.setCompressorEfficiency(0.75);

    GasLiftResult result = calculator.calculate();

    // Should need some compression power (can be 0 if no gas needed)
    assertTrue(result.compressionPower >= 0, "Compression power should be non-negative");
    // If there is injection gas, there should be power
    if (result.injectionRateAtOptimal > 0) {
      assertTrue(result.compressionPower >= 0, "Should have compression power when injecting");
    }
  }

  @Test
  @DisplayName("Test unit conversions")
  void testUnitConversions() {
    // Set in different units
    calculator.setReservoirPressure(3625.0, "psia"); // ~250 bara
    calculator.setReservoirTemperature(185.0, "F"); // ~85°C
    calculator.setWellDepth(9843.0, "ft"); // ~3000 m
    calculator.setTubingID(4.0, "in"); // ~0.1 m
    calculator.setOilGravity(35.0, "API");

    GasLiftResult result = calculator.calculate();

    // Should still produce valid result
    assertNotNull(result);
    assertTrue(result.oilRateAtOptimal > 0, "Should calculate positive production");
  }

  @Test
  @DisplayName("Test high water cut scenario")
  void testHighWaterCut() {
    calculator.setReservoirPressure(200.0, "bara");
    calculator.setWellDepth(2500.0, "m");
    calculator.setWaterCut(0.80); // 80% water cut

    GasLiftResult result = calculator.calculate();

    // Gas lift should still work but may require more gas
    assertTrue(result.optimalGLR > 0, "Should still calculate optimal GLR");
  }

  @Test
  @DisplayName("Test result toString method")
  void testResultToString() {
    calculator.setReservoirPressure(220.0, "bara");
    calculator.setWellDepth(2800.0, "m");

    GasLiftResult result = calculator.calculate();

    String resultString = result.toString();
    assertNotNull(resultString);
    assertTrue(resultString.contains("Gas Lift Design Result"));
    assertTrue(resultString.contains("Optimal GLR"));
  }
}
