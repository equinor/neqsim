package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for CO2CorrosionAnalyzer — coupled electrolyte CPA flash and de Waard-Milliams corrosion
 * model.
 */
public class CO2CorrosionAnalyzerTest {

  @Test
  void testPureCO2WaterSystem() {
    // Pure CO2 + water at 60°C, 50 bar → pH should be ~3.0-4.5
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(60.0, 50.0);
    analyzer.setCO2MoleFractionInGas(0.50);
    analyzer.setWaterMoleFractionInGas(0.50);
    analyzer.run();

    assertTrue(analyzer.isFreeWaterPresent(), "Free water should be present");
    double pH = analyzer.getAqueousPH();
    assertTrue(pH > 2.5 && pH < 5.5,
        "pH should be in range 2.5-5.5 for CO2-saturated water, got " + pH);

    double corrosionRate = analyzer.getCorrosionRate();
    assertTrue(corrosionRate > 0, "Corrosion rate should be positive: " + corrosionRate);

    String severity = analyzer.getCorrosionSeverity();
    assertNotNull(severity);
    assertFalse(severity.isEmpty());
  }

  @Test
  void testHighPressureCO2() {
    // Dense CO2 at 100 bar, 40°C — typical CCS conditions
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(40.0, 100.0);
    analyzer.setCO2MoleFractionInGas(0.95);
    analyzer.setWaterMoleFractionInGas(0.05);
    analyzer.run();

    double pCO2 = analyzer.getCO2PartialPressure();
    assertTrue(pCO2 > 0, "CO2 partial pressure should be positive: " + pCO2);

    double corrosionRate = analyzer.getCorrosionRate();
    assertTrue(corrosionRate >= 0, "Corrosion rate should be non-negative: " + corrosionRate);
  }

  @Test
  void testBrineSystem() {
    // CO2 + water + NaCl — small ion fraction to avoid numerical issues
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(60.0, 50.0);
    analyzer.setCO2MoleFractionInGas(0.10);
    analyzer.setWaterMoleFractionInGas(0.88);
    analyzer.setSodiumMoleFraction(0.01);
    analyzer.setChlorideMoleFraction(0.01);
    analyzer.run();

    assertTrue(analyzer.isFreeWaterPresent(), "Free water should be present with brine");
    double pH = analyzer.getAqueousPH();
    assertTrue(pH > 2.0 && pH < 7.0, "pH should be in valid range for CO2-brine system, got " + pH);
  }

  @Test
  void testCorrosionRateIncreasesWithTemperature() {
    // de Waard-Milliams: corrosion rate increases with temperature up to ~80°C
    CO2CorrosionAnalyzer analyzerLow = new CO2CorrosionAnalyzer(30.0, 50.0);
    analyzerLow.setCO2MoleFractionInGas(0.50);
    analyzerLow.setWaterMoleFractionInGas(0.50);
    analyzerLow.run();

    CO2CorrosionAnalyzer analyzerHigh = new CO2CorrosionAnalyzer(70.0, 50.0);
    analyzerHigh.setCO2MoleFractionInGas(0.50);
    analyzerHigh.setWaterMoleFractionInGas(0.50);
    analyzerHigh.run();

    assertTrue(analyzerHigh.getBaselineCorrosionRate() > analyzerLow.getBaselineCorrosionRate(),
        "Baseline rate at 70°C should exceed 30°C");
  }

  @Test
  void testTemperatureSweep() {
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(40.0, 50.0);
    analyzer.setCO2MoleFractionInGas(0.50);
    analyzer.setWaterMoleFractionInGas(0.50);

    List<Map<String, Object>> results = analyzer.runTemperatureSweep(20.0, 80.0, 6);
    assertEquals(7, results.size(), "Should have 7 data points for 6 steps");

    // Temperature should increase monotonically
    double prevT = -999;
    for (Map<String, Object> point : results) {
      double T = (Double) point.get("temperature_C");
      assertTrue(T > prevT, "Temperature should increase");
      prevT = T;

      assertTrue(((Double) point.get("corrosionRate_mmyr")) >= 0,
          "Corrosion rate should be non-negative");
    }
  }

  @Test
  void testPressureSweep() {
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(60.0, 50.0);
    analyzer.setCO2MoleFractionInGas(0.50);
    analyzer.setWaterMoleFractionInGas(0.50);

    List<Map<String, Object>> results = analyzer.runPressureSweep(10.0, 100.0, 9);
    assertEquals(10, results.size(), "Should have 10 data points for 9 steps");

    double prevP = -999;
    for (Map<String, Object> point : results) {
      double P = (Double) point.get("pressure_bara");
      assertTrue(P > prevP, "Pressure should increase");
      prevP = P;
    }
  }

  @Test
  void testJsonOutput() {
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(60.0, 50.0);
    analyzer.setCO2MoleFractionInGas(0.50);
    analyzer.setWaterMoleFractionInGas(0.50);
    analyzer.run();

    String json = analyzer.toJson();
    assertNotNull(json);
    assertTrue(json.contains("flashResults"));
    assertTrue(json.contains("corrosionResults"));
    assertTrue(json.contains("modelInfo"));
    assertTrue(json.contains("Electrolyte CPA"));
    assertTrue(json.contains("de Waard-Milliams"));
  }

  @Test
  void testCorrosionAllowance() {
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(60.0, 50.0);
    analyzer.setCO2MoleFractionInGas(0.50);
    analyzer.setWaterMoleFractionInGas(0.50);
    analyzer.run();

    double ca25 = analyzer.estimateCorrosionAllowance(25.0);
    assertEquals(analyzer.getCorrosionRate() * 25.0, ca25, 0.001);
  }

  @Test
  void testNoWaterSystem() {
    // Pure CO2, no water — no free water phase expected
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(60.0, 50.0);
    analyzer.setCO2MoleFractionInGas(1.0);
    analyzer.setWaterMoleFractionInGas(0.0);
    analyzer.run();

    assertFalse(analyzer.isFreeWaterPresent(), "No free water expected without water in feed");
    assertTrue(Double.isNaN(analyzer.getAqueousPH()), "pH should be NaN without aqueous phase");
  }

  @Test
  void testWithMethane() {
    // Natural gas with CO2: methane + CO2 + water
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer(40.0, 80.0);
    analyzer.setCO2MoleFractionInGas(0.05);
    analyzer.setWaterMoleFractionInGas(0.05);
    analyzer.setMethaneMoleFractionInGas(0.90);
    analyzer.run();

    double pCO2 = analyzer.getCO2PartialPressure();
    assertTrue(pCO2 > 0 && pCO2 < 80.0, "CO2 partial pressure should be less than total: " + pCO2);

    double rate = analyzer.getCorrosionRate();
    assertTrue(rate >= 0, "Corrosion rate should be non-negative: " + rate);
  }

  @Test
  void testGettersBeforeRun() {
    CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer();
    assertTrue(Double.isNaN(analyzer.getAqueousPH()), "pH should be NaN before run");
    assertTrue(Double.isNaN(analyzer.getCorrosionRate()), "Rate should be NaN before run");
  }

  @Test
  void testInhibitorReducesCorrosion() {
    CO2CorrosionAnalyzer noInhibitor = new CO2CorrosionAnalyzer(60.0, 50.0);
    noInhibitor.setCO2MoleFractionInGas(0.50);
    noInhibitor.setWaterMoleFractionInGas(0.50);
    noInhibitor.run();

    CO2CorrosionAnalyzer withInhibitor = new CO2CorrosionAnalyzer(60.0, 50.0);
    withInhibitor.setCO2MoleFractionInGas(0.50);
    withInhibitor.setWaterMoleFractionInGas(0.50);
    withInhibitor.setInhibitorEfficiency(0.80);
    withInhibitor.run();

    assertTrue(withInhibitor.getCorrosionRate() < noInhibitor.getCorrosionRate(),
        "Inhibitor should reduce corrosion rate");
  }
}
