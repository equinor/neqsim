package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
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

  @Test
  void testEnhancedModePopulatesApi12JResultAndFloodingDegradesNotZero() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setUseEnhancedCalculation(true);
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(100e-6, 2.6));
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

    calc.calculate(40.0, 800.0, 0.0, 1.4e-5, 3.0e-3, 0.0,
        4.0, 1.0, 4.0, "horizontal", 0.2);

    assertNotNull(calc.getApiComplianceResult(),
        "API 12J compliance result should be populated after calculate()");
    assertEquals(calc.getKFactorUtilization() > 1.0, calc.isMistEliminatorFlooded(),
        "Flooding flag should be consistent with K-factor utilization");
  }

  @Test
  void testCalibrationFactorsScaleEntrainmentFractions() {
    SeparatorPerformanceCalculator base = new SeparatorPerformanceCalculator();
    base.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.2));
    base.setMistEliminatorCurve(GradeEfficiencyCurve.vanePackDefault());
    base.setGasBubbleDSD(DropletSizeDistribution.rosinRammler(400e-6, 2.0));
    base.setWaterInOilDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.0));
    base.setOilInWaterDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.0));

    base.calculate(50.0, 800.0, 1020.0, 1.5e-5, 3.0e-3, 8.0e-4, 1.2, 2.0, 8.0, "horizontal", 0.5);

    double baseOilInGas = base.getOilInGasFraction();
    double baseGasInOil = base.getGasInOilFraction();
    double baseWaterInOil = base.getWaterInOilFraction();

    SeparatorPerformanceCalculator tuned = new SeparatorPerformanceCalculator();
    tuned.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.2));
    tuned.setMistEliminatorCurve(GradeEfficiencyCurve.vanePackDefault());
    tuned.setGasBubbleDSD(DropletSizeDistribution.rosinRammler(400e-6, 2.0));
    tuned.setWaterInOilDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.0));
    tuned.setOilInWaterDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.0));

    tuned.setLiquidInGasCalibrationFactor(1.5);
    tuned.setGasCarryUnderCalibrationFactor(1.25);
    tuned.setLiquidLiquidCalibrationFactor(0.8);

    tuned.calculate(50.0, 800.0, 1020.0, 1.5e-5, 3.0e-3, 8.0e-4, 1.2, 2.0, 8.0, "horizontal", 0.5);

    assertEquals(Math.min(1.0, baseOilInGas * 1.5), tuned.getOilInGasFraction(), 1e-12);
    assertEquals(Math.min(1.0, baseGasInOil * 1.25), tuned.getGasInOilFraction(), 1e-12);
    assertEquals(Math.min(1.0, baseWaterInOil * 0.8), tuned.getWaterInOilFraction(), 1e-12);
  }

  @Test
  void testAutoCalibrateFromMeasuredFractionsOnePoint() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.2));
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.vanePackDefault());
    calc.setGasBubbleDSD(DropletSizeDistribution.rosinRammler(400e-6, 2.0));
    calc.setWaterInOilDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.0));
    calc.setOilInWaterDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.0));

    calc.calculate(50.0, 800.0, 1020.0, 1.5e-5, 3.0e-3, 8.0e-4, 1.2, 2.0, 8.0, "horizontal", 0.5);

    double modelOilInGas = calc.getOilInGasFraction();
    double modelGasInOil = calc.getGasInOilFraction();
    double modelWaterInOil = calc.getWaterInOilFraction();

    double measuredOilInGas = modelOilInGas * 1.40;
    double measuredGasInOil = modelGasInOil * 0.75;
    double measuredWaterInOil = modelWaterInOil * 1.20;

    SeparatorPerformanceCalculator.CalibrationSummary summary = calc
        .calibrateFromMeasuredFractions(measuredOilInGas, calc.getWaterInGasFraction() * 1.40,
            measuredGasInOil, calc.getGasInWaterFraction() * 0.75, calc.getOilInWaterFraction() * 1.20,
            measuredWaterInOil, 1e-12);

    assertTrue(summary.liquidInGasPointsUsed >= 1);
    assertTrue(summary.gasCarryUnderPointsUsed >= 1);
    assertTrue(summary.liquidLiquidPointsUsed >= 1);

    assertEquals(1.40, calc.getLiquidInGasCalibrationFactor(), 1e-10);
    assertEquals(0.75, calc.getGasCarryUnderCalibrationFactor(), 1e-10);
    assertEquals(1.20, calc.getLiquidLiquidCalibrationFactor(), 1e-10);

    // Recalculate with same process point: calibrated prediction should hit measured values.
    calc.calculate(50.0, 800.0, 1020.0, 1.5e-5, 3.0e-3, 8.0e-4, 1.2, 2.0, 8.0, "horizontal", 0.5);
    assertEquals(measuredOilInGas, calc.getOilInGasFraction(), 1e-12);
    assertEquals(measuredGasInOil, calc.getGasInOilFraction(), 1e-12);
    assertEquals(measuredWaterInOil, calc.getWaterInOilFraction(), 1e-12);
  }

  @Test
  void testAutoCalibrateKeepsFactorsWhenModelBelowFloor() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setLiquidInGasCalibrationFactor(1.1);
    calc.setGasCarryUnderCalibrationFactor(0.9);
    calc.setLiquidLiquidCalibrationFactor(1.3);

    SeparatorPerformanceCalculator.CalibrationSummary summary = calc
        .calibrateFromMeasuredFractions(0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 1e-6);

    assertEquals(1.1, calc.getLiquidInGasCalibrationFactor(), 1e-12);
    assertEquals(0.9, calc.getGasCarryUnderCalibrationFactor(), 1e-12);
    assertEquals(1.3, calc.getLiquidLiquidCalibrationFactor(), 1e-12);
    assertEquals(0, summary.liquidInGasPointsUsed);
    assertEquals(0, summary.gasCarryUnderPointsUsed);
    assertEquals(0, summary.liquidLiquidPointsUsed);
  }

  @Test
  void testGroupedCalibrationConvenienceMethod() {
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.2));
    calc.setMistEliminatorCurve(GradeEfficiencyCurve.vanePackDefault());
    calc.setGasBubbleDSD(DropletSizeDistribution.rosinRammler(400e-6, 2.0));
    calc.setWaterInOilDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.0));
    calc.setOilInWaterDSD(DropletSizeDistribution.rosinRammler(120e-6, 2.0));
    calc.calculate(50.0, 800.0, 1020.0, 1.5e-5, 3.0e-3, 8.0e-4, 1.2, 2.0, 8.0, "horizontal", 0.5);

    double ligGroup = 0.5 * (calc.getOilInGasFraction() + calc.getWaterInGasFraction());
    double gcuGroup = 0.5 * (calc.getGasInOilFraction() + calc.getGasInWaterFraction());
    double llGroup = 0.5 * (calc.getOilInWaterFraction() + calc.getWaterInOilFraction());

    calc.calibrateFromGroupedMeasurements(ligGroup * 1.1, gcuGroup * 0.9, llGroup * 1.2, 1e-12);

    assertEquals(1.1, calc.getLiquidInGasCalibrationFactor(), 1e-10);
    assertEquals(0.9, calc.getGasCarryUnderCalibrationFactor(), 1e-10);
    assertEquals(1.2, calc.getLiquidLiquidCalibrationFactor(), 1e-10);
  }

  @Test
  void testLoadCalibrationCasesFromCsvAndBatchFit() throws IOException {
    File tmp = File.createTempFile("sep-cal", ".csv");
    FileWriter writer = new FileWriter(tmp);
    try {
      writer.write("case_id,modeled_oil_in_gas,modeled_water_in_gas,modeled_gas_in_oil,");
      writer.write("modeled_gas_in_water,modeled_oil_in_water,modeled_water_in_oil,");
      writer.write("measured_oil_in_gas,measured_water_in_gas,measured_gas_in_oil,");
      writer.write("measured_gas_in_water,measured_oil_in_water,measured_water_in_oil\n");
      writer.write("c1,0.010,0.010,0.020,0.020,0.030,0.030,0.015,0.015,0.010,0.010,0.060,0.060\n");
      writer.write("c2,0.020,0.020,0.010,0.010,0.040,0.040,0.030,0.030,0.005,0.005,0.080,0.080\n");
    } finally {
      writer.close();
    }

    List<SeparatorPerformanceCalculator.CalibrationCase> cases =
        SeparatorPerformanceCalculator.loadCalibrationCasesFromCsv(tmp.getAbsolutePath());
    assertEquals(2, cases.size());

    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    SeparatorPerformanceCalculator.BatchCalibrationSummary summary =
        calc.calibrateFromCaseLibrary(cases, 1e-12);

    assertTrue(summary.liquidInGasPointsUsed > 0);
    assertTrue(summary.gasCarryUnderPointsUsed > 0);
    assertTrue(summary.liquidLiquidPointsUsed > 0);
    assertTrue(summary.mapeAfter <= summary.mapeBefore + 1e-15,
        "Batch calibration should not increase average percentage error");

    // Expected means from two rows:
    // lig ratios: 1.5 and 1.5, 1.5 and 1.5 => mean 1.5
    // gcu ratios: 0.5 and 0.5, 0.5 and 0.5 => mean 0.5
    // ll ratios: 2.0 and 2.0, 2.0 and 2.0 => mean 2.0
    assertEquals(1.5, calc.getLiquidInGasCalibrationFactor(), 1e-12);
    assertEquals(0.5, calc.getGasCarryUnderCalibrationFactor(), 1e-12);
    assertEquals(2.0, calc.getLiquidLiquidCalibrationFactor(), 1e-12);

    tmp.delete();
  }

  @Test
  void testBatchCalibrationReportJsonAndSave() throws IOException {
    File tmpCsv = File.createTempFile("sep-cal-report", ".csv");
    FileWriter writer = new FileWriter(tmpCsv);
    try {
      writer.write("case_id,modeled_oil_in_gas,modeled_water_in_gas,modeled_gas_in_oil,");
      writer.write("modeled_gas_in_water,modeled_oil_in_water,modeled_water_in_oil,");
      writer.write("measured_oil_in_gas,measured_water_in_gas,measured_gas_in_oil,");
      writer.write("measured_gas_in_water,measured_oil_in_water,measured_water_in_oil\n");
      writer.write("r1,0.01,0.01,0.02,0.02,0.03,0.03,0.015,0.015,0.01,0.01,0.06,0.06\n");
    } finally {
      writer.close();
    }

    List<SeparatorPerformanceCalculator.CalibrationCase> cases =
        SeparatorPerformanceCalculator.loadCalibrationCasesFromCsv(tmpCsv.getAbsolutePath());
    SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
    SeparatorPerformanceCalculator.BatchCalibrationSummary summary =
        calc.calibrateFromCaseLibrary(cases, 1e-12);

    String json = calc.buildBatchCalibrationReportJson(cases, summary, 1e-12);
    assertTrue(json.contains("\"casesProcessed\""));
    assertTrue(json.contains("\"mapeBefore\""));
    assertTrue(json.contains("\"mapeAfter\""));
    assertTrue(json.contains("\"caseResiduals\""));
    assertTrue(json.contains("\"r1\""));

    File tmpReport = File.createTempFile("sep-cal-report-out", ".json");
    calc.saveBatchCalibrationReportJson(tmpReport.getAbsolutePath(), cases, summary, 1e-12);

    String saved = new String(Files.readAllBytes(Paths.get(tmpReport.getAbsolutePath())));
    assertTrue(saved.contains("\"caseResiduals\""));
    assertTrue(saved.contains("\"newLiquidInGasFactor\""));

    tmpCsv.delete();
    tmpReport.delete();
  }
}
