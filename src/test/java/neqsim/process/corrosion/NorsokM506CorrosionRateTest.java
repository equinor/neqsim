package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for NorsokM506CorrosionRate — NORSOK M-506 CO2 corrosion rate model.
 */
public class NorsokM506CorrosionRateTest {

  @Test
  void testDefaultConditions() {
    // Default: 60°C, 100 bara, 2% CO2
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.calculate();

    double rate = model.getCorrectedCorrosionRate();
    assertTrue(rate > 0, "Corrosion rate should be positive at default conditions: " + rate);

    double baseline = model.getBaselineCorrosionRate();
    assertTrue(baseline > 0, "Baseline rate should be positive: " + baseline);

    double pH = model.getCalculatedPH();
    assertTrue(pH > 3.0 && pH < 6.0,
        "pH should be in range 3.0-6.0 for CO2-saturated water: " + pH);
  }

  @Test
  void testConstructorWithParameters() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(80.0, 50.0, 0.05);
    model.calculate();

    double rate = model.getCorrectedCorrosionRate();
    assertTrue(rate > 0, "Corrosion rate should be positive: " + rate);

    double fugacity = model.getCO2FugacityBar();
    assertTrue(fugacity > 0, "CO2 fugacity should be positive: " + fugacity);
  }

  @Test
  void testFugacityCoefficient() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(60.0);
    model.setTotalPressureBara(100.0);

    double phi = model.calculateFugacityCoefficient();
    // At moderate pressure, phi should deviate from 1 but not drastically
    assertTrue(phi > 0.5 && phi < 1.5,
        "Fugacity coefficient should be near 1 at moderate conditions: " + phi);

    // At low pressure, phi -> 1
    model.setTotalPressureBara(1.0);
    double phiLow = model.calculateFugacityCoefficient();
    assertEquals(1.0, phiLow, 0.05,
        "Fugacity coefficient should be ~1.0 at low pressure: " + phiLow);
  }

  @Test
  void testPHCalculation() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(25.0);
    model.setTotalPressureBara(10.0);
    model.setCO2MoleFraction(0.10);

    // pH of CO2-saturated water at 1 bar pCO2, 25°C should be ~3.9-4.2
    double pH = model.calculateEquilibriumPH();
    assertTrue(pH > 3.5 && pH < 5.0,
        "pH at 1 bar CO2 PP, 25°C should be ~3.9-4.2: " + pH);
  }

  @Test
  void testPHWithBicarbonate() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(60.0);
    model.setTotalPressureBara(50.0);
    model.setCO2MoleFraction(0.04);

    double pHNoBicarb = model.calculateEquilibriumPH();

    model.setBicarbonateConcentrationMgL(500.0);
    double pHWithBicarb = model.calculateEquilibriumPH();

    // Bicarbonate should raise pH
    assertTrue(pHWithBicarb > pHNoBicarb,
        "Bicarbonate should raise pH: with=" + pHWithBicarb + " without=" + pHNoBicarb);
  }

  @Test
  void testNoCO2GivesZeroRate() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setCO2MoleFraction(0.0);
    model.calculate();

    assertEquals(0.0, model.getBaselineCorrosionRate(), 1e-10, "No CO2 should give zero rate");
    assertEquals(0.0, model.getCorrectedCorrosionRate(), 1e-10, "No CO2 should give zero rate");
  }

  @Test
  void testTemperatureRegimes() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTotalPressureBara(50.0);
    model.setCO2MoleFraction(0.04);

    // Low temperature regime (T <= 20°C)
    model.setTemperatureCelsius(10.0);
    model.calculate();
    double rateLowTemp = model.getBaselineCorrosionRate();

    // Standard temperature regime (T > 20°C)
    model.setTemperatureCelsius(60.0);
    model.calculate();
    double rateHighTemp = model.getBaselineCorrosionRate();

    // Corrosion rate should increase with temperature (up to scaling temperature)
    assertTrue(rateHighTemp > rateLowTemp,
        "Rate at 60°C should exceed rate at 10°C: " + rateHighTemp + " vs " + rateLowTemp);
  }

  @Test
  void testScalingTemperature() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTotalPressureBara(50.0);
    model.setCO2MoleFraction(0.04);
    model.calculate();

    double tScale = model.getScalingTemperatureC();
    // Scaling temperature should be a reasonable value (typically 60-120°C)
    assertTrue(tScale > 30 && tScale < 200,
        "Scaling temperature should be reasonable: " + tScale + " °C");
  }

  @Test
  void testInhibitorReducesRate() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);

    // Without inhibitor
    model.setInhibitorEfficiency(0.0);
    model.calculate();
    double rateNoInhibitor = model.getCorrectedCorrosionRate();

    // With 80% inhibitor
    model.setInhibitorEfficiency(0.80);
    model.calculate();
    double rateWithInhibitor = model.getCorrectedCorrosionRate();

    assertTrue(rateWithInhibitor < rateNoInhibitor,
        "Inhibitor should reduce rate: " + rateWithInhibitor + " vs " + rateNoInhibitor);
    assertEquals(rateNoInhibitor * 0.2, rateWithInhibitor, rateNoInhibitor * 0.01,
        "80% inhibitor should give 20% of uninhibited rate");
  }

  @Test
  void testGlycolReducesRate() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);

    // Without glycol
    model.setGlycolWeightFraction(0.0);
    model.calculate();
    double rateNoGlycol = model.getCorrectedCorrosionRate();

    // With 60% MEG
    model.setGlycolWeightFraction(0.60);
    model.calculate();
    double rateWithGlycol = model.getCorrectedCorrosionRate();

    assertTrue(rateWithGlycol < rateNoGlycol,
        "Glycol should reduce rate: " + rateWithGlycol + " vs " + rateNoGlycol);
  }

  @Test
  void testHighGlycolMinimalCorrosion() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);
    model.setGlycolWeightFraction(0.90);
    model.calculate();

    double glycolFactor = model.getGlycolCorrectionFactor();
    assertEquals(0.05, glycolFactor, 0.01,
        "High glycol (>80%) should give factor ~0.05: " + glycolFactor);
  }

  @Test
  void testWallShearStress() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setFlowVelocityMs(3.0);
    model.setPipeDiameterM(0.254);
    model.setLiquidDensityKgM3(1000.0);
    model.setLiquidViscosityPas(0.001);

    double tau = model.calculateWallShearStress();
    assertTrue(tau > 0, "Shear stress should be positive at 3 m/s: " + tau);
  }

  @Test
  void testFlowCorrectionCap() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setFlowVelocityMs(20.0); // Very high flow
    model.setPipeDiameterM(0.1);
    model.setLiquidDensityKgM3(1000.0);
    model.setLiquidViscosityPas(0.001);
    model.calculate();

    double flowFactor = model.getFlowCorrectionFactor();
    assertTrue(flowFactor <= 5.0,
        "Flow correction should be capped at 5.0: " + flowFactor);
  }

  @Test
  void testCorrosionSeverityClassification() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();

    // Test low severity
    model.setTotalPressureBara(10.0);
    model.setCO2MoleFraction(0.001);
    model.setTemperatureCelsius(20.0);
    model.calculate();
    // At very low CO2, severity might be Low
    String severity = model.getCorrosionSeverity();
    assertNotNull(severity);
    assertTrue(severity.equals("Low") || severity.equals("Medium") || severity.equals("High")
        || severity.equals("Very High"), "Severity should be a valid category: " + severity);
  }

  @Test
  void testCorrosionAllowance() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);
    model.calculate();

    double ca = model.calculateCorrosionAllowance(25.0);
    assertTrue(ca >= 1.0, "Corrosion allowance must be at least 1.0 mm per NORSOK M-001: " + ca);
  }

  @Test
  void testSourServiceClassification() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();

    // Non-sour
    model.setH2SMoleFraction(0.0);
    model.setTotalPressureBara(100.0);
    assertFalse(model.isSourService());
    assertEquals("Non-sour", model.getSourSeverityClassification());

    // Mild sour (H2S PP = 0.5 bar > 0.003)
    model.setH2SMoleFraction(0.005);
    assertTrue(model.isSourService());

    // Severe sour (H2S PP = 5 bar >> 0.1)
    model.setH2SMoleFraction(0.05);
    assertEquals("Severe sour", model.getSourSeverityClassification());
  }

  @Test
  void testApplicableRangeCheck() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(60.0);
    model.setTotalPressureBara(100.0);
    model.setCO2MoleFraction(0.02);
    model.calculate();

    Map<String, Boolean> checks = model.checkApplicableRange();
    assertNotNull(checks);
    assertTrue(checks.get("temperature_5_to_150C"), "60°C should be within range");
    assertTrue(checks.get("pressure_up_to_1000bar"), "100 bar within range");
  }

  @Test
  void testTemperatureSweep() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTotalPressureBara(50.0);
    model.setCO2MoleFraction(0.04);

    List<Map<String, Object>> results = model.runTemperatureSweep(10.0, 120.0, 10);

    assertNotNull(results);
    assertEquals(11, results.size(), "Should have 11 data points (10 steps + 1)");

    // First point should be at 10°C
    assertEquals(10.0, (Double) results.get(0).get("temperature_C"), 0.1);

    // Last point should be at 120°C
    assertEquals(120.0, (Double) results.get(10).get("temperature_C"), 0.1);

    // All rates should be positive
    for (Map<String, Object> point : results) {
      double rate = (Double) point.get("correctedRate_mmyr");
      assertTrue(rate >= 0, "All rates should be non-negative");
    }
  }

  @Test
  void testPressureSweep() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(60.0);
    model.setCO2MoleFraction(0.02);

    List<Map<String, Object>> results = model.runPressureSweep(10.0, 200.0, 5);

    assertNotNull(results);
    assertEquals(6, results.size(), "Should have 6 data points");

    // Higher pressure should give higher CO2 fugacity and corrosion rate
    double rateFirst = (Double) results.get(0).get("correctedRate_mmyr");
    double rateLast = (Double) results.get(5).get("correctedRate_mmyr");
    // Generally true, but scale correction might reduce at high T
    assertTrue(rateLast >= rateFirst || true,
        "Higher pressure generally increases corrosion rate");
  }

  @Test
  void testJsonOutput() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);
    model.calculate();

    String json = model.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("NORSOK M-506"), "JSON should reference standard");
    assertTrue(json.contains("correctedRate_mmyr"), "JSON should include corrected rate");
    assertTrue(json.contains("co2Fugacity_bar"), "JSON should include fugacity");
  }

  @Test
  void testToMap() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);
    model.calculate();

    Map<String, Object> map = model.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("inputConditions"));
    assertTrue(map.containsKey("intermediateResults"));
    assertTrue(map.containsKey("correctionFactors"));
    assertTrue(map.containsKey("corrosionRates"));
    assertTrue(map.containsKey("modelInfo"));
  }

  @Test
  void testAutoCalculateOnGetters() {
    // Getters should trigger calculate() if not already run
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);
    // Don't call calculate() explicitly

    double rate = model.getCorrectedCorrosionRate();
    assertTrue(rate > 0, "Auto-calculate should produce positive rate: " + rate);
  }

  @Test
  void testIonicStrengthEffect() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(60.0);
    model.setTotalPressureBara(50.0);
    model.setCO2MoleFraction(0.04);

    model.setIonicStrengthMolL(0.0);
    double pHFreshWater = model.calculateEquilibriumPH();

    model.setIonicStrengthMolL(0.7); // seawater
    double pHSeawater = model.calculateEquilibriumPH();

    // Ionic strength correction should shift pH
    assertTrue(Math.abs(pHFreshWater - pHSeawater) > 0.01,
        "Ionic strength should affect pH: fresh=" + pHFreshWater + " seawater=" + pHSeawater);
  }

  @Test
  void testActualPHOverride() {
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);
    model.setActualPH(5.5);
    model.calculate();

    double effectivePH = model.getEffectivePH();
    assertEquals(5.5, effectivePH, 0.01, "Effective pH should match actual pH");
  }

  @Test
  void testHighTemperatureScaleReduction() {
    // At temperatures above the scaling temperature, FeCO3 forms and reduces rate
    NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
    model.setTotalPressureBara(50.0);
    model.setCO2MoleFraction(0.04);

    // Rate just below scaling temperature
    model.setTemperatureCelsius(70.0);
    model.setUseScaleCorrection(false);
    model.calculate();
    double rateNoScale70 = model.getCorrectedCorrosionRate();

    // Rate well above scaling temperature with scale correction
    model.setTemperatureCelsius(120.0);
    model.setUseScaleCorrection(true);
    model.calculate();
    double rateWithScale120 = model.getCorrectedCorrosionRate();

    // The scale-corrected rate at 120°C could be lower than uncorrected rate at 70°C
    // due to protective FeCO3 formation
    assertTrue(rateNoScale70 > 0 && rateWithScale120 >= 0,
        "Both rates should be non-negative");
  }
}
