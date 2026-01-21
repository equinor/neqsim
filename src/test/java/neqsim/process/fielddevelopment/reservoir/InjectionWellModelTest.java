package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel.InjectionType;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel.InjectionWellResult;

/**
 * Unit tests for InjectionWellModel.
 *
 * @author ESOL
 */
class InjectionWellModelTest {
  private InjectionWellModel model;

  @BeforeEach
  void setUp() {
    model = new InjectionWellModel();
  }

  @Test
  @DisplayName("Test basic water injection calculation")
  void testBasicWaterInjection() {
    // Configure model
    model.setWellType(InjectionType.WATER_INJECTOR);
    model.setReservoirPressure(250.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");
    model.setSkinFactor(2.0);
    model.setWellDepth(3000.0, "m");
    model.setMaxBHP(350.0, "bara");

    // Calculate for target rate
    InjectionWellResult result = model.calculate(10000.0);

    // Verify result
    assertNotNull(result);
    assertTrue(result.injectivityIndex > 0, "II should be positive");
    assertTrue(result.achievableRate > 0, "Achievable rate should be positive");
    assertTrue(result.bottomholePressure > 250.0, "BHP should exceed reservoir pressure");
  }

  @Test
  @DisplayName("Test injectivity index calculation")
  void testInjectivityIndex() {
    model.setReservoirPressure(200.0, "bara");
    model.setFormationPermeability(200.0, "mD"); // Higher perm = higher II
    model.setFormationThickness(50.0, "m"); // Thicker = higher II
    model.setSkinFactor(0.0); // No skin damage

    InjectionWellResult result = model.calculate(5000.0);

    // Higher permeability and thickness should give higher II
    assertTrue(result.injectivityIndex > 10, "II should be significant");
  }

  @Test
  @DisplayName("Test rate limited by pressure constraint")
  void testPressureLimitedRate() {
    model.setReservoirPressure(300.0, "bara");
    model.setMaxBHP(320.0, "bara"); // Low margin above reservoir pressure
    model.setFormationPermeability(50.0, "mD");
    model.setFormationThickness(20.0, "m");

    // Request high rate
    InjectionWellResult result = model.calculate(50000.0);

    // Should be limited by pressure
    assertTrue(result.limitedByPressure, "Should be pressure limited");
    assertTrue(result.achievableRate < 50000.0, "Rate should be limited");
    // BHP should be near maximum (320 bara)
    assertTrue(result.bottomholePressure >= 318.0, "BHP should be near maximum");
  }

  @Test
  @DisplayName("Test pump requirement calculation")
  void testPumpRequirement() {
    model.setReservoirPressure(200.0, "bara");
    model.setMaxBHP(300.0, "bara");
    model.setSurfaceInjectionPressure(50.0, "bara"); // Low surface pressure

    InjectionWellResult result = model.calculate(10000.0);

    if (result.wellheadPressure > 50.0) {
      assertTrue(result.needsPump, "Should need pump when WHP > surface pressure");
      assertTrue(result.pumpPower > 0, "Pump power should be calculated");
      assertTrue(result.requiredPumpDeltaP > 0, "Pump ΔP should be positive");
    }
  }

  @Test
  @DisplayName("Test maximum rate calculation")
  void testMaximumRate() {
    model.setReservoirPressure(200.0, "bara");
    model.setMaxBHP(350.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");

    InjectionWellResult result = model.calculateMaximumRate();

    // Maximum rate should use full pressure margin
    assertTrue(result.limitedByPressure, "Max rate should be pressure limited");
    assertTrue(result.bottomholePressure >= 348.0, "Should use near-maximum BHP");
  }

  @Test
  @DisplayName("Test Hall plot parameters")
  void testHallPlotParameters() {
    model.setReservoirPressure(250.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");
    model.setSkinFactor(5.0); // Some skin damage

    InjectionWellResult result = model.calculate(8000.0);

    // Hall slope should be calculated
    assertTrue(result.hallSlope > 0, "Hall slope should be positive");
    assertTrue(result.skinContribution >= 0, "Skin contribution should be calculated");
  }

  @Test
  @DisplayName("Test pressure interference effects")
  void testPressureInterference() {
    model.setReservoirPressure(250.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");
    model.setMaxBHP(300.0, "bara");

    // Calculate with nearby producers
    double[] producerDistances = {300.0, 400.0}; // m
    double[] producerRates = {5000.0, 6000.0}; // Sm3/day

    InjectionWellResult result =
        model.calculateWithInterference(10000.0, producerDistances, producerRates);

    // Interference should reduce effective reservoir pressure
    assertTrue(result.interferencePressure >= 0, "Should have interference pressure");
    assertTrue(result.effectiveReservoirPressure <= 250.0,
        "Effective pressure should be lower or equal");
  }

  @Test
  @DisplayName("Test gas injection")
  void testGasInjection() {
    model.setWellType(InjectionType.GAS_INJECTOR);
    model.setReservoirPressure(200.0, "bara");
    model.setReservoirTemperature(90.0, "C");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");

    InjectionWellResult result = model.calculate(50000.0); // Higher rate for gas

    assertNotNull(result);
    assertEquals(InjectionType.GAS_INJECTOR, result.injectionType);
    assertTrue(result.injectivityIndex > 0, "Gas II should be positive");
  }

  @Test
  @DisplayName("Test skin factor effect on injectivity")
  void testSkinFactorEffect() {
    model.setReservoirPressure(200.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");

    // No skin
    model.setSkinFactor(0.0);
    InjectionWellResult noSkinResult = model.calculate(5000.0);

    // High skin
    model.setSkinFactor(10.0);
    InjectionWellResult highSkinResult = model.calculate(5000.0);

    // Higher skin should give lower II
    assertTrue(noSkinResult.injectivityIndex > highSkinResult.injectivityIndex,
        "Higher skin should reduce injectivity");
  }

  @Test
  @DisplayName("Test unit conversions")
  void testUnitConversions() {
    // Set in field units
    model.setReservoirPressure(3625.0, "psia"); // ~250 bara
    model.setReservoirTemperature(194.0, "F"); // ~90°C
    model.setFormationThickness(98.4, "ft"); // ~30 m
    model.setWellDepth(9843.0, "ft"); // ~3000 m
    model.setTubingID(4.0, "in"); // ~0.1 m
    model.setMaxBHP(5075.0, "psia"); // ~350 bara

    InjectionWellResult result = model.calculate(10000.0);

    assertNotNull(result);
    assertTrue(result.injectivityIndex > 0, "Should calculate with field units");
  }

  @Test
  @DisplayName("Test result toString format")
  void testResultToString() {
    model.setReservoirPressure(250.0, "bara");
    model.setFormationPermeability(100.0, "mD");

    InjectionWellResult result = model.calculate(10000.0);

    String output = result.toString();
    assertNotNull(output);
    assertTrue(output.contains("Injection Well Result"));
    assertTrue(output.contains("Injectivity index"));
    assertTrue(output.contains("BHP"));
    assertTrue(output.contains("WHP"));
  }

  @Test
  @DisplayName("Test injection pattern sweep efficiency")
  void testInjectionPatternSweep() {
    InjectionWellModel.InjectionPattern fiveSpot = new InjectionWellModel.InjectionPattern(
        InjectionWellModel.InjectionPattern.PatternType.FIVE_SPOT);

    // Favorable mobility ratio
    double sweepFavorable = fiveSpot.getArealSweepEfficiency(0.5);

    // Unfavorable mobility ratio
    double sweepUnfavorable = fiveSpot.getArealSweepEfficiency(5.0);

    assertTrue(sweepFavorable > sweepUnfavorable, "Favorable mobility should give better sweep");
    assertTrue(sweepFavorable > 0.5, "Should have reasonable sweep");
    assertTrue(sweepFavorable < 1.0, "Sweep cannot exceed 100%");
  }

  @Test
  @DisplayName("Test injection pattern configuration")
  void testInjectionPatternConfiguration() {
    InjectionWellModel.InjectionPattern pattern = new InjectionWellModel.InjectionPattern(
        InjectionWellModel.InjectionPattern.PatternType.LINE_DRIVE);

    pattern.setWellSpacing(400.0);

    assertEquals(400.0, pattern.getWellSpacing(), 0.1);
  }
}
