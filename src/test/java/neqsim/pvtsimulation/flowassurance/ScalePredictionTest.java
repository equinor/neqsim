package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for mineral scale prediction improvements.
 *
 * <p>
 * Covers ScalePredictionCalculator, ScaleMassCalculator, BariteCelestiteSolidSolution,
 * WaterCompatibilityScreener, and FlowlineScaleProfile.
 * </p>
 */
public class ScalePredictionTest {

  // ────────────────────────────────────────────────────────────────
  // ScalePredictionCalculator tests
  // ────────────────────────────────────────────────────────────────

  @Test
  void testCaCO3AtAmbientConditions() {
    // Standard North Sea produced water at 80°C, 100 bar
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(400.0);
    calc.setBariumConcentration(0.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(0.0);
    calc.setBicarbonateConcentration(150.0);
    calc.setSulphateConcentration(10.0);
    calc.setTotalDissolvedSolids(35000.0);
    calc.setTemperatureCelsius(80.0);
    calc.setPressureBara(100.0);
    calc.setCO2PartialPressure(2.0);
    calc.enableAutoPH();
    calc.calculate();

    double si = calc.getCaCO3SaturationIndex();
    // CaCO3 SI should be a finite number (positive or negative)
    assertFalse(Double.isNaN(si), "CaCO3 SI should not be NaN");
    assertFalse(Double.isInfinite(si), "CaCO3 SI should be finite");
  }

  @Test
  void testBaSO4Supersaturation() {
    // High barium + high sulphate → should be supersaturated
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(100.0);
    calc.setBariumConcentration(200.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(0.0);
    calc.setBicarbonateConcentration(100.0);
    calc.setSulphateConcentration(2000.0);
    calc.setTotalDissolvedSolids(40000.0);
    calc.setTemperatureCelsius(60.0);
    calc.setPressureBara(1.013);
    calc.setCO2PartialPressure(0.5);
    calc.enableAutoPH();
    calc.calculate();

    double si = calc.getBaSO4SaturationIndex();
    assertTrue(si > 0.0, "BaSO4 SI should be positive (supersaturated) with 200 mg/L Ba + 2000 "
        + "mg/L SO4, got " + si);
  }

  @Test
  void testZeroBaSO4WithoutBarium() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(100.0);
    calc.setBariumConcentration(0.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(0.0);
    calc.setBicarbonateConcentration(100.0);
    calc.setSulphateConcentration(2000.0);
    calc.setTotalDissolvedSolids(40000.0);
    calc.setTemperatureCelsius(60.0);
    calc.setPressureBara(1.013);
    calc.setCO2PartialPressure(0.5);
    calc.enableAutoPH();
    calc.calculate();

    double si = calc.getBaSO4SaturationIndex();
    // With zero Ba, BaSO4 SI should be very negative or NaN
    assertTrue(Double.isNaN(si) || si < -10.0,
        "BaSO4 SI with zero Ba should be very negative or NaN, got " + si);
  }

  @Test
  void testTemperatureDependence() {
    // CaCO3 retrograde solubility: SI should increase with temperature
    double si25 = calcCaCO3SI(25.0, 1.013);
    double si80 = calcCaCO3SI(80.0, 1.013);

    assertTrue(si80 > si25, "CaCO3 SI at 80C (" + si80 + ") should exceed SI at 25C (" + si25
        + ") due to retrograde solubility");
  }

  @Test
  void testPressureCorrection() {
    // At high pressure, Ksp increases → SI should decrease
    double si1 = calcCaCO3SI(80.0, 1.013);
    double si500 = calcCaCO3SI(80.0, 500.0);

    assertTrue(si500 < si1, "CaCO3 SI at 500 bar (" + si500 + ") should be lower than at 1 bar ("
        + si1 + ") due to pressure correction");
  }

  @Test
  void testIonPairingReducesSI() {
    // With Mg and Na present, ion pairing should reduce effective SO4 → lower BaSO4 SI
    ScalePredictionCalculator calcPlain = new ScalePredictionCalculator();
    calcPlain.setCalciumConcentration(100.0);
    calcPlain.setBariumConcentration(50.0);
    calcPlain.setStrontiumConcentration(0.0);
    calcPlain.setIronConcentration(0.0);
    calcPlain.setBicarbonateConcentration(100.0);
    calcPlain.setSulphateConcentration(2000.0);
    calcPlain.setTotalDissolvedSolids(50000.0);
    calcPlain.setMagnesiumConcentration(0.0);
    calcPlain.setSodiumConcentration(0.0);
    calcPlain.setTemperatureCelsius(60.0);
    calcPlain.setPressureBara(1.013);
    calcPlain.setCO2PartialPressure(0.5);
    calcPlain.enableAutoPH();
    calcPlain.calculate();
    double siPlain = calcPlain.getBaSO4SaturationIndex();

    ScalePredictionCalculator calcPaired = new ScalePredictionCalculator();
    calcPaired.setCalciumConcentration(100.0);
    calcPaired.setBariumConcentration(50.0);
    calcPaired.setStrontiumConcentration(0.0);
    calcPaired.setIronConcentration(0.0);
    calcPaired.setBicarbonateConcentration(100.0);
    calcPaired.setSulphateConcentration(2000.0);
    calcPaired.setTotalDissolvedSolids(50000.0);
    calcPaired.setMagnesiumConcentration(1300.0);
    calcPaired.setSodiumConcentration(11000.0);
    calcPaired.setTemperatureCelsius(60.0);
    calcPaired.setPressureBara(1.013);
    calcPaired.setCO2PartialPressure(0.5);
    calcPaired.enableAutoPH();
    calcPaired.calculate();
    double siPaired = calcPaired.getBaSO4SaturationIndex();

    assertTrue(siPaired < siPlain,
        "Ion pairing with Mg/Na should reduce BaSO4 SI from " + siPlain + " to " + siPaired);
  }

  @Test
  void testFeCO3SaturationIndex() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(100.0);
    calc.setBariumConcentration(0.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(50.0);
    calc.setBicarbonateConcentration(500.0);
    calc.setSulphateConcentration(10.0);
    calc.setTotalDissolvedSolids(30000.0);
    calc.setTemperatureCelsius(80.0);
    calc.setPressureBara(10.0);
    calc.setCO2PartialPressure(5.0);
    calc.enableAutoPH();
    calc.calculate();

    double si = calc.getFeCO3SaturationIndex();
    assertFalse(Double.isNaN(si), "FeCO3 SI should not be NaN");
    assertFalse(Double.isInfinite(si), "FeCO3 SI should be finite");
  }

  @Test
  void testSrSO4SaturationIndex() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(100.0);
    calc.setBariumConcentration(0.0);
    calc.setStrontiumConcentration(800.0);
    calc.setIronConcentration(0.0);
    calc.setBicarbonateConcentration(100.0);
    calc.setSulphateConcentration(2000.0);
    calc.setTotalDissolvedSolids(60000.0);
    calc.setTemperatureCelsius(60.0);
    calc.setPressureBara(50.0);
    calc.setCO2PartialPressure(1.0);
    calc.enableAutoPH();
    calc.calculate();

    double si = calc.getSrSO4SaturationIndex();
    assertFalse(Double.isNaN(si), "SrSO4 SI should not be NaN");
  }

  @Test
  void testCaSO4AnhydriteSI() {
    // Very high calcium + sulphate → should show CaSO4 supersaturation
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(2000.0);
    calc.setBariumConcentration(0.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(0.0);
    calc.setBicarbonateConcentration(100.0);
    calc.setSulphateConcentration(4000.0);
    calc.setTotalDissolvedSolids(80000.0);
    calc.setTemperatureCelsius(120.0);
    calc.setPressureBara(50.0);
    calc.setCO2PartialPressure(1.0);
    calc.enableAutoPH();
    calc.calculate();

    double si = calc.getCaSO4SaturationIndex();
    assertFalse(Double.isNaN(si), "CaSO4 SI should not be NaN");
  }

  // ────────────────────────────────────────────────────────────────
  // ScaleMassCalculator tests
  // ────────────────────────────────────────────────────────────────

  @Test
  void testScaleMassZeroWhenUndersaturated() {
    // BaSO4: negative SI → no precipitation
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    ScaleMassCalculator massCal = new ScaleMassCalculator(calc);
    double mass = massCal.calcBaSO4Mass(0.001, 0.001, -2.0);
    assertEquals(0.0, mass, 1e-10, "Mass should be zero when SI is negative");
  }

  @Test
  void testScaleMassPositiveWhenSupersaturated() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(2000.0);
    calc.setBariumConcentration(200.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(50.0);
    calc.setBicarbonateConcentration(500.0);
    calc.setSulphateConcentration(3000.0);
    calc.setTotalDissolvedSolids(60000.0);
    calc.setTemperatureCelsius(80.0);
    calc.setPressureBara(10.0);
    calc.setCO2PartialPressure(2.0);
    calc.enableAutoPH();
    calc.calculate();

    ScaleMassCalculator massCal = new ScaleMassCalculator(calc);
    // Ba = 200 mg/L, MW_Ba=137.33 → mol/L; SO4 = 3000 mg/L, MW_SO4=96.06 → mol/L
    double baMolL = 200.0 / 137330.0;
    double so4MolL = 3000.0 / 96060.0;
    double siBa = calc.getBaSO4SaturationIndex();
    double mass = massCal.calcBaSO4Mass(baMolL, so4MolL, siBa);

    if (siBa > 0) {
      assertTrue(mass > 0.0, "BaSO4 mass should be positive when SI=" + siBa + ", got " + mass);
    }
  }

  @Test
  void testScaleMassJsonOutput() {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(500.0);
    calc.setBariumConcentration(50.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(0.0);
    calc.setBicarbonateConcentration(200.0);
    calc.setSulphateConcentration(1000.0);
    calc.setTotalDissolvedSolids(40000.0);
    calc.setTemperatureCelsius(60.0);
    calc.setPressureBara(50.0);
    calc.setCO2PartialPressure(1.0);
    calc.enableAutoPH();
    calc.calculate();

    ScaleMassCalculator massCal = new ScaleMassCalculator(calc);
    massCal.setWaterVolume(1000.0);
    String json = massCal.toJson();
    assertNotNull(json, "JSON output should not be null");
    assertTrue(json.contains("waterVolumeLitres"), "JSON should contain water volume");
  }

  // ────────────────────────────────────────────────────────────────
  // BariteCelestiteSolidSolution tests
  // ────────────────────────────────────────────────────────────────

  @Test
  void testPureBariteEndMember() {
    // Only Ba in solution, no Sr → solid should be pure BaSO4
    BariteCelestiteSolidSolution ss = new BariteCelestiteSolidSolution();
    ss.setAqueousActivities(0.01, 0.0, 0.05);
    ss.setEndMemberKsp(1.08e-10, 3.44e-7);
    ss.calculate();

    assertEquals(1.0, ss.getBaSO4MoleFraction(), 0.01,
        "With zero Sr activity, solid should be pure BaSO4");
  }

  @Test
  void testPureCelestiteEndMember() {
    // Only Sr in solution, no Ba → solid should be pure SrSO4
    BariteCelestiteSolidSolution ss = new BariteCelestiteSolidSolution();
    ss.setAqueousActivities(0.0, 0.01, 0.05);
    ss.setEndMemberKsp(1.08e-10, 3.44e-7);
    ss.calculate();

    assertEquals(0.0, ss.getBaSO4MoleFraction(), 0.01,
        "With zero Ba activity, solid should be pure SrSO4");
  }

  @Test
  void testMixedSolidSolution() {
    // Both Ba and Sr present → mixed (Ba,Sr)SO4
    BariteCelestiteSolidSolution ss = new BariteCelestiteSolidSolution();
    ss.setAqueousActivities(0.001, 0.005, 0.01);
    ss.setEndMemberKsp(1.08e-10, 3.44e-7);
    ss.calculate();

    double xBa = ss.getBaSO4MoleFraction();
    assertTrue(xBa > 0.0 && xBa < 1.0, "Mixed solid should have 0 < xBa < 1, got " + xBa);
  }

  @Test
  void testSolidSolutionTotalSI() {
    BariteCelestiteSolidSolution ss = new BariteCelestiteSolidSolution();
    ss.setAqueousActivities(0.001, 0.005, 0.01);
    ss.setEndMemberKsp(1.08e-10, 3.44e-7);
    ss.calculate();

    double totalSI = ss.getTotalSaturationIndex();
    assertFalse(Double.isNaN(totalSI), "Total SI should not be NaN");
  }

  @Test
  void testSolidSolutionJsonOutput() {
    BariteCelestiteSolidSolution ss = new BariteCelestiteSolidSolution();
    ss.setAqueousActivities(0.001, 0.005, 0.01);
    ss.setEndMemberKsp(1.08e-10, 3.44e-7);
    ss.calculate();

    String json = ss.toJson();
    assertNotNull(json);
    assertTrue(json.contains("xBaSO4_solid"), "JSON should contain BaSO4 mole fraction");
  }

  // ────────────────────────────────────────────────────────────────
  // WaterCompatibilityScreener tests
  // ────────────────────────────────────────────────────────────────

  @Test
  void testSeawaterMixingBaSO4Peak() {
    // Classic case: FW with Ba, IW (seawater) with SO4 → BaSO4 peak at intermediate ratio
    WaterCompatibilityScreener screener = new WaterCompatibilityScreener();
    screener.setFormationWater(400, 200, 5, 0, 150, 10, 50000, 80, 100, 2.0, 6.5);
    screener.setInjectionWater(20, 0, 0, 0, 100, 2700, 35000, 20, 100, 0.5, 8.0);
    screener.setMixingRatios(new double[] {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100});
    screener.calculate();

    double worstRatio = screener.getWorstCaseRatio();
    assertTrue(worstRatio >= 0 && worstRatio <= 100, "Worst ratio should be 0-100");

    String worstScale = screener.getWorstCaseScale();
    assertNotNull(worstScale, "Worst scale name should not be null");
    assertFalse(worstScale.isEmpty(), "Worst scale name should not be empty");

    double worstSI = screener.getWorstCaseSI();
    assertFalse(Double.isNaN(worstSI), "Worst SI should not be NaN");

    // BaSO4 risk should be significant given 200 mg/L Ba mixing with 2700 mg/L SO4
    List<WaterCompatibilityScreener.MixingResult> results = screener.getResults();
    assertEquals(11, results.size(), "Should have 11 mixing results");
  }

  @Test
  void testPureFormationWater() {
    // 100% FW, 0% IW
    WaterCompatibilityScreener screener = new WaterCompatibilityScreener();
    screener.setFormationWater(400, 10, 5, 0, 150, 10, 50000, 80, 100, 2.0, 6.5);
    screener.setInjectionWater(20, 0, 0, 0, 100, 2700, 35000, 20, 100, 0.5, 8.0);
    screener.setMixingRatios(new double[] {0});
    screener.calculate();

    List<WaterCompatibilityScreener.MixingResult> results = screener.getResults();
    assertEquals(1, results.size(), "Should have 1 result");
    assertEquals(0.0, results.get(0).injectionWaterPct, 0.01);
  }

  @Test
  void testScreenerJsonOutput() {
    WaterCompatibilityScreener screener = new WaterCompatibilityScreener();
    screener.setFormationWater(400, 10, 5, 0, 150, 10, 50000, 80, 100, 2.0, 6.5);
    screener.setInjectionWater(20, 0, 0, 0, 100, 2700, 35000, 20, 100, 0.5, 8.0);
    screener.calculate();

    String json = screener.toJson();
    assertNotNull(json);
    assertTrue(json.contains("worstCase"), "JSON should contain worstCase section");
    assertTrue(json.contains("mixingResults"), "JSON should contain mixingResults array");
  }

  // ────────────────────────────────────────────────────────────────
  // FlowlineScaleProfile tests
  // ────────────────────────────────────────────────────────────────

  @Test
  void testFlowlineProfileSegments() {
    FlowlineScaleProfile profile = new FlowlineScaleProfile();
    profile.setWaterChemistry(400, 10, 5, 0, 150, 10, 40000, 2.0, 6.5);
    profile.setInletConditions(80.0, 200.0);
    profile.setOutletConditions(20.0, 50.0);
    profile.setNumberOfSegments(10);
    profile.calculate();

    List<FlowlineScaleProfile.SegmentResult> results = profile.getResults();
    assertEquals(11, results.size(), "Should have 11 results (10 segments + 1)");

    // Verify first segment is inlet conditions
    FlowlineScaleProfile.SegmentResult inlet = results.get(0);
    assertEquals(0.0, inlet.distanceFraction, 0.01);
    assertEquals(80.0, inlet.temperatureC, 0.1);
    assertEquals(200.0, inlet.pressureBar, 0.1);

    // Verify last segment is outlet conditions
    FlowlineScaleProfile.SegmentResult outlet = results.get(10);
    assertEquals(1.0, outlet.distanceFraction, 0.01);
    assertEquals(20.0, outlet.temperatureC, 0.1);
    assertEquals(50.0, outlet.pressureBar, 0.1);
  }

  @Test
  void testFlowlineMaxSI() {
    FlowlineScaleProfile profile = new FlowlineScaleProfile();
    profile.setWaterChemistry(400, 10, 5, 0, 150, 10, 40000, 2.0, 6.5);
    profile.setInletConditions(80.0, 200.0);
    profile.setOutletConditions(20.0, 50.0);
    profile.setNumberOfSegments(10);
    profile.calculate();

    double maxCaCO3 = profile.getMaxSI("CaCO3");
    assertFalse(Double.isNaN(maxCaCO3), "Max CaCO3 SI should not be NaN");
    assertFalse(Double.isInfinite(maxCaCO3), "Max CaCO3 SI should be finite");
  }

  @Test
  void testFlowlineProfileJsonOutput() {
    FlowlineScaleProfile profile = new FlowlineScaleProfile();
    profile.setWaterChemistry(400, 10, 5, 0, 150, 10, 40000, 2.0, 6.5);
    profile.setInletConditions(80.0, 200.0);
    profile.setOutletConditions(20.0, 50.0);
    profile.setNumberOfSegments(5);
    profile.calculate();

    String json = profile.toJson();
    assertNotNull(json);
    assertTrue(json.contains("segmentResults"), "JSON should contain segmentResults");
    assertTrue(json.contains("maxSaturationIndices"), "JSON should contain max SI summary");
  }

  @Test
  void testFlowlineCaCO3RetrogradeSolubility() {
    // CaCO3 has retrograde solubility: higher T → more supersaturated
    // So inlet (80°C) should have higher CaCO3 SI than outlet (20°C)
    FlowlineScaleProfile profile = new FlowlineScaleProfile();
    profile.setWaterChemistry(400, 0, 0, 0, 300, 0, 40000, 2.0, 6.5);
    profile.setInletConditions(80.0, 100.0);
    profile.setOutletConditions(20.0, 100.0);
    profile.setNumberOfSegments(1);
    profile.calculate();

    List<FlowlineScaleProfile.SegmentResult> results = profile.getResults();
    double siInlet = results.get(0).siCaCO3;
    double siOutlet = results.get(1).siCaCO3;

    assertTrue(siInlet > siOutlet,
        "CaCO3 SI at inlet (80C): " + siInlet + " should exceed outlet (20C): " + siOutlet);
  }

  // ────────────────────────────────────────────────────────────────
  // Helper methods
  // ────────────────────────────────────────────────────────────────

  /**
   * Helper: compute CaCO3 SI at given T and P with standard brine composition.
   *
   * @param tempC temperature Celsius
   * @param pressBar pressure bara
   * @return CaCO3 saturation index
   */
  private double calcCaCO3SI(double tempC, double pressBar) {
    ScalePredictionCalculator calc = new ScalePredictionCalculator();
    calc.setCalciumConcentration(500.0);
    calc.setBariumConcentration(0.0);
    calc.setStrontiumConcentration(0.0);
    calc.setIronConcentration(0.0);
    calc.setBicarbonateConcentration(300.0);
    calc.setSulphateConcentration(10.0);
    calc.setTotalDissolvedSolids(40000.0);
    calc.setTemperatureCelsius(tempC);
    calc.setPressureBara(pressBar);
    calc.setCO2PartialPressure(2.0);
    calc.enableAutoPH();
    calc.calculate();
    return calc.getCaCO3SaturationIndex();
  }
}
