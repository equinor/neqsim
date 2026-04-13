package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SeparatorPerformanceCalculator}.
 */
class SeparatorPerformanceCalculatorTest {

  @Test
  void testTwoPhaseGasLiquidSeparation() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();

    // Set up a Rosin-Rammler DSD with d_63.2 = 100 um
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(100e-6, 2.6));

    // Wire mesh mist eliminator
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    // Run calculation: horizontal separator, 2 m ID, 8 m long
    double gasDensity = 50.0;
    double oilDensity = 800.0;
    double waterDensity = 0.0; // Two-phase (no water)
    double gasViscosity = 1.5e-5;
    double oilViscosity = 2.0e-3;
    double waterViscosity = 0.0;
    double gasVelocity = 1.0; // m/s
    double diameter = 2.0;
    double length = 8.0;
    String orientation = "horizontal";
    double liquidLevelFrac = 0.5;

    calc.calculate(gasDensity, oilDensity, waterDensity, gasViscosity, oilViscosity, waterViscosity,
        gasVelocity, diameter, length, orientation, liquidLevelFrac);

    // Check results
    double overallEff = calc.getOverallGasLiquidEfficiency();
    assertTrue(overallEff > 0.99, "With 100 um DSD and wire mesh, overall efficiency should be "
        + ">99%. Got: " + overallEff);

    double oilInGas = calc.getOilInGasFraction();
    assertTrue(oilInGas < 0.01, "Oil-in-gas should be very low. Got: " + oilInGas);

    // No water, so water-in-gas should be zero
    assertEquals(0.0, calc.getWaterInGasFraction(), 1e-10);
  }

  @Test
  void testVerticalScrubber() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();

    // Fine mist DSD
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(30e-6, 2.6));

    // Vane pack
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.vanePackDefault());

    // Vertical scrubber: smaller diameter, no liquid level
    calc.calculate(60.0, 850.0, 0.0, 1.5e-5, 3.0e-3, 0.0, 2.0, 1.0, 4.0, "vertical", 0.1);

    double efficiency = calc.getOverallGasLiquidEfficiency();
    assertTrue(efficiency > 0.50,
        "Vertical scrubber should have reasonable efficiency with vane pack");

    // Gravity cut diameter should be reasonable
    double dCut = calc.getGravityCutDiameter();
    assertTrue(dCut > 0 && dCut < 1e-2, "Gravity cut diameter should be positive");
  }

  @Test
  void testThreePhaseCalculation() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();

    // Gas-liquid DSD
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(80e-6, 2.6));
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    // Liquid-liquid DSDs
    calc.setWaterInOilDSD(DropletSizeDistribution.rosinRammler(300e-6, 2.0));
    calc.setOilInWaterDSD(DropletSizeDistribution.rosinRammler(200e-6, 2.0));

    // Three-phase separator
    calc.calculate(40.0, 800.0, 1020.0, 1.5e-5, 5.0e-3, 8.0e-4, 1.5, 2.5, 10.0, "horizontal", 0.6);

    // Gas-liquid separation should be good
    assertTrue(calc.getOverallGasLiquidEfficiency() > 0.98, "Gas-liquid efficiency should be high");

    // Liquid-liquid results should be between 0 and 1
    assertTrue(calc.getOilInWaterFraction() >= 0 && calc.getOilInWaterFraction() <= 1,
        "Oil-in-water should be between 0 and 1");
    assertTrue(calc.getWaterInOilFraction() >= 0 && calc.getWaterInOilFraction() <= 1,
        "Water-in-oil should be between 0 and 1");
  }

  @Test
  void testNoMistEliminator() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();

    // Fine mist DSD
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(20e-6, 2.6));
    // No mist eliminator

    // Large horizontal separator with gravity only
    calc.calculate(30.0, 750.0, 0.0, 1.2e-5, 2.0e-3, 0.0, 0.5, 3.0, 12.0, "horizontal", 0.5);

    // Should still have some gravity separation
    assertTrue(calc.getGravitySectionEfficiency() > 0.0,
        "Gravity section should provide some separation");

    // But without mist eliminator, fine droplets pass through
    assertEquals(0.0, calc.getMistEliminatorEfficiency(), 1e-10,
        "Mist eliminator efficiency should be 0 when not installed");

    // Overall = gravity only
    assertEquals(calc.getGravitySectionEfficiency(), calc.getOverallGasLiquidEfficiency(), 1e-10);
  }

  @Test
  void testJsonOutput() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(100e-6, 2.6));
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    calc.calculate(50.0, 800.0, 0.0, 1.5e-5, 2.0e-3, 0.0, 1.0, 2.0, 8.0, "horizontal", 0.5);

    String json = calc.toJson();
    assertTrue(json.contains("overallGasLiquidEfficiency"), "JSON should contain efficiency");
    assertTrue(json.contains("inletDSD"), "JSON should contain DSD info");
    assertTrue(json.contains("mistEliminator"), "JSON should contain mist eliminator info");
  }

  @Test
  void testGasBubbleCarryUnder() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();

    // Gas-liquid DSD for droplets
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(100e-6, 2.6));
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    // Gas bubble DSD (fine bubbles in liquid)
    calc.setGasBubbleDSD(DropletSizeDistribution.rosinRammler(500e-6, 2.0));

    calc.calculate(50.0, 800.0, 0.0, 1.5e-5, 3.0e-3, 0.0, 1.0, 2.0, 8.0, "horizontal", 0.5);

    // Gas carry-under should be between 0 and 1
    double gasInOil = calc.getGasInOilFraction();
    assertTrue(gasInOil >= 0 && gasInOil <= 1,
        "Gas-in-oil should be between 0 and 1. Got: " + gasInOil);
  }

  @Test
  void testWithPlatePackCoalescer() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();

    // Liquid-liquid DSDs
    calc.setWaterInOilDSD(DropletSizeDistribution.rosinRammler(100e-6, 2.0));
    calc.setOilInWaterDSD(DropletSizeDistribution.rosinRammler(80e-6, 2.0));

    // Add plate pack coalescer
    calc.setOilWaterCoalescerCurve(GradeEfficiencyCurve.platePack(30e-6, 0.98));

    calc.calculate(40.0, 800.0, 1020.0, 1.5e-5, 5.0e-3, 8.0e-4, 1.5, 2.5, 10.0, "horizontal", 0.6);

    // With plate pack, liquid-liquid separation should be better
    double woFrac = calc.getWaterInOilFraction();
    assertTrue(woFrac < 0.5, "With plate pack, water-in-oil should be low. Got: " + woFrac);
  }
}
