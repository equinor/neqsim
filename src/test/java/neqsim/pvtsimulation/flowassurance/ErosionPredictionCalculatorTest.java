package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for ErosionPredictionCalculator.
 */
public class ErosionPredictionCalculatorTest {

  @Test
  void testApiRP14E_erosionalVelocity() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(100.0); // kg/m3
    calc.setMixtureVelocity(5.0);
    calc.setPipeDiameter(0.1524);
    calc.setSandRate(0.0);
    calc.setApiCFactor(100.0);
    calc.calculate();

    double ve = calc.getErosionalVelocity();
    assertTrue(ve > 0, "Erosional velocity should be positive");
    // For rho=100 kg/m3, C=100: rho_imperial = 6.243 lb/ft3
    // Ve_imperial = 100 / sqrt(6.243) = 40.0 ft/s
    // Ve_SI = 40.0 * 0.3048 = 12.19 m/s
    assertEquals(12.19, ve, 0.5, "API RP 14E erosional velocity");
  }

  @Test
  void testApiRP14E_withinLimits() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(100.0);
    calc.setMixtureVelocity(5.0); // Well below erosional velocity
    calc.setSandRate(0.0);
    calc.calculate();

    assertTrue(calc.isWithinApiLimits(), "Low velocity should be within limits");
    assertTrue(calc.getVelocityRatio() < 1.0, "Velocity ratio should be < 1");
  }

  @Test
  void testApiRP14E_exceedsLimits() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(100.0);
    calc.setMixtureVelocity(50.0); // Very high velocity
    calc.setSandRate(0.0);
    calc.calculate();

    assertFalse(calc.isWithinApiLimits(), "High velocity should exceed limits");
    assertTrue(calc.getVelocityRatio() > 1.0, "Velocity ratio should be > 1");
  }

  @Test
  void testDNV_sandErosion_elbow() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(150.0);
    calc.setMixtureVelocity(10.0);
    calc.setPipeDiameter(0.1524); // 6 inch
    calc.setWallThickness(10.0);
    calc.setSandRate(50.0); // 50 kg/day
    calc.setSandParticleDiameter(0.25);
    calc.setPipeMaterial("carbon_steel");
    calc.setGeometry("elbow");
    calc.setDesignLife(25.0);
    calc.setCorrosionAllowance(3.0);
    calc.calculate();

    double erosionRate = calc.getErosionRate();
    assertTrue(erosionRate > 0, "Erosion rate should be positive with sand");
    assertTrue(erosionRate < 100, "Erosion rate should be in reasonable range");

    double cumulative = calc.getCumulativeErosion();
    assertEquals(erosionRate * 25.0, cumulative, 0.001,
        "Cumulative erosion should be rate * design life");
  }

  @Test
  void testNoSand_noErosion() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(100.0);
    calc.setMixtureVelocity(5.0);
    calc.setSandRate(0.0); // No sand
    calc.setGeometry("elbow");
    calc.calculate();

    assertEquals(0.0, calc.getErosionRate(), 0.001, "No sand should mean no erosion");
  }

  @Test
  void testGeometryFactors() {
    // Test that different geometries give different erosion rates
    double[] rates = new double[3];
    String[] geometries = {"blind_tee", "elbow", "choke"};

    for (int i = 0; i < geometries.length; i++) {
      ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
      calc.setMixtureDensity(150.0);
      calc.setMixtureVelocity(15.0);
      calc.setPipeDiameter(0.1524);
      calc.setSandRate(100.0);
      calc.setGeometry(geometries[i]);
      calc.calculate();
      rates[i] = calc.getErosionRate();
    }

    // Choke should have higher erosion than blind tee
    assertTrue(rates[2] > rates[0], "Choke erosion should exceed blind tee erosion");
  }

  @Test
  void testRiskAssessment() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(100.0);
    calc.setMixtureVelocity(5.0);
    calc.setSandRate(0.0);
    calc.calculate();

    String risk = calc.getRiskLevel();
    assertNotNull(risk);
    assertEquals("low", risk, "Low velocity + no sand should be low risk");
  }

  @Test
  void testMaterialDifference() {
    // Different materials should give different erosion results
    ErosionPredictionCalculator calcSteel = new ErosionPredictionCalculator();
    calcSteel.setMixtureDensity(150.0);
    calcSteel.setMixtureVelocity(15.0);
    calcSteel.setSandRate(100.0);
    calcSteel.setPipeMaterial("carbon_steel");
    calcSteel.setGeometry("elbow");
    calcSteel.calculate();

    ErosionPredictionCalculator calcInconel = new ErosionPredictionCalculator();
    calcInconel.setMixtureDensity(150.0);
    calcInconel.setMixtureVelocity(15.0);
    calcInconel.setSandRate(100.0);
    calcInconel.setPipeMaterial("inconel");
    calcInconel.setGeometry("elbow");
    calcInconel.calculate();

    assertTrue(calcInconel.getErosionRate() < calcSteel.getErosionRate(),
        "Inconel should have lower erosion rate than carbon steel");
  }

  @Test
  void testToJsonNotNull() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(100.0);
    calc.setMixtureVelocity(10.0);
    calc.setSandRate(50.0);
    calc.setGeometry("elbow");
    calc.calculate();

    String json = calc.toJson();
    assertNotNull(json);
    assertTrue(json.contains("erosionalVelocity_ms"));
    assertTrue(json.contains("erosionRate_mmyr"));
    assertTrue(json.contains("riskLevel"));
  }

  @Test
  void testRemainingWallThickness() {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(100.0);
    calc.setMixtureVelocity(10.0);
    calc.setPipeDiameter(0.1524);
    calc.setWallThickness(15.0);
    calc.setSandRate(10.0);
    calc.setCorrosionAllowance(3.0);
    calc.setDesignLife(25.0);
    calc.setGeometry("elbow");
    calc.calculate();

    double remaining = calc.getRemainingWallThickness();
    double expected = 15.0 - calc.getCumulativeErosion() - 3.0;
    assertEquals(expected, remaining, 0.001, "Remaining wall = initial - erosion - corrosion");
  }
}
