package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NelsonCurveAssessment} — API 941 Nelson curve high-temperature hydrogen attack
 * assessment.
 *
 * @author ESOL
 * @version 1.0
 */
public class NelsonCurveAssessmentTest {

  @Test
  void testCarbonSteel_SafeAtLowTemp() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(150.0);
    nelson.setH2PartialPressureBar(10.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    assertTrue(nelson.isBelowNelsonCurve(), "150°C should be safe for any material");
    assertEquals("Low", nelson.getRiskLevel());
    assertTrue(nelson.getTemperatureMarginC() < 0, "Margin should be negative (safe)");
  }

  @Test
  void testCarbonSteel_UnsafeAtHighTemp() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(300.0);
    nelson.setH2PartialPressureBar(10.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    // 300°C should be above the carbon steel Nelson curve at 10 bar H2
    assertFalse(nelson.isBelowNelsonCurve(),
        "300°C at 10 bar H2 should be above carbon steel Nelson curve");
    assertEquals("Very High", nelson.getRiskLevel());
    assertTrue(nelson.getTemperatureMarginC() > 0, "Margin should be positive (unsafe)");
  }

  @Test
  void testCarbonSteel_MaxAllowableTemp() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(200.0);
    nelson.setH2PartialPressureBar(0.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    double maxTemp = nelson.getMaxAllowableTemperatureC();
    // At 0 psia H2, carbon steel curve is 500°F = 260°C
    assertEquals(260.0, maxTemp, 5.0,
        "Max temp at 0 psia should be ~260°C for CS");
    assertTrue(nelson.isBelowNelsonCurve());
  }

  @Test
  void testCarbonSteel_Borderline() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    // Carbon steel curve at 100 psia: 450°F ≈ 232°C
    // Test just below the curve
    nelson.setTemperatureC(220.0);
    nelson.setH2PartialPressurePsia(100.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    assertTrue(nelson.isBelowNelsonCurve(),
        "220°C at 100 psia should be just below curve (~232°C limit)");
    // Should be High risk (close to curve, within 20°C)
    assertTrue(
        "Medium".equals(nelson.getRiskLevel()) || "High".equals(nelson.getRiskLevel()),
        "Borderline should be Medium or High risk: " + nelson.getRiskLevel());
  }

  @Test
  void test225Cr1Mo_HigherLimit() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(400.0);
    nelson.setH2PartialPressureBar(10.0);
    nelson.setMaterialType("2_25cr_1mo");
    nelson.evaluate();

    // 2.25Cr-1Mo should easily handle 400°C at 10 bar H2
    assertTrue(nelson.isBelowNelsonCurve(),
        "400°C at 10 bar H2 should be safe for 2.25Cr-1Mo");
    assertEquals("Low", nelson.getRiskLevel());
  }

  @Test
  void test225Cr1Mo_HighTempUnsafe() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(550.0);
    nelson.setH2PartialPressureBar(100.0);
    nelson.setMaterialType("2_25cr_1mo");
    nelson.evaluate();

    // Very high temperature and very high pressure — should exceed even 2.25Cr-1Mo
    assertFalse(nelson.isBelowNelsonCurve(),
        "550°C at 100 bar H2 should exceed 2.25Cr-1Mo Nelson curve");
  }

  @Test
  void testAusteniticSS_AcceptsMost() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(500.0);
    nelson.setH2PartialPressureBar(50.0);
    nelson.setMaterialType("austenitic_ss");
    nelson.evaluate();

    // Austenitic SS should easily handle 500°C
    assertTrue(nelson.isBelowNelsonCurve(),
        "500°C should be safe for austenitic SS");
  }

  @Test
  void testMaterialUpgrade_CarbonSteelFails() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(280.0);
    nelson.setH2PartialPressureBar(10.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    assertFalse(nelson.isBelowNelsonCurve());
    String upgrade = nelson.getRecommendedUpgrade();
    assertNotNull(upgrade);
    assertFalse(upgrade.isEmpty(), "Should recommend an upgrade");
    // Should recommend at least C-0.5Mo or higher
  }

  @Test
  void testMaxAllowablePressure() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    // At 220°C (428°F) for carbon steel — should get a finite max pressure
    nelson.setTemperatureC(220.0);
    nelson.setH2PartialPressureBar(5.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    double maxPressure = nelson.getMaxAllowableH2PressureBar();
    assertTrue(maxPressure > 0.0, "Max pressure should be positive: " + maxPressure);
    assertTrue(maxPressure < 700.0, "Max pressure should be bounded: " + maxPressure);
  }

  @Test
  void testBelowHTHAThreshold() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(150.0);
    nelson.setH2PartialPressureBar(100.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    // Below 200°C → Low risk (HTHA not applicable)
    assertTrue(nelson.isBelowNelsonCurve());
    assertEquals("Low", nelson.getRiskLevel(),
        "Below 200°C should always be Low risk");
  }

  @Test
  void testSetH2PressureBarConverts() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setH2PartialPressureBar(10.0);
    nelson.setTemperatureC(250.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    // Verify the conversion worked by checking results are sensible
    Map<String, Object> map = nelson.toMap();
    double psia = (Double) map.get("h2PartialPressure_psia");
    assertEquals(10.0 * 14.5038, psia, 0.1, "Should convert bar to psia");
  }

  @Test
  void testSupportedMaterialTypes() {
    List<String> types = NelsonCurveAssessment.getSupportedMaterialTypes();
    assertNotNull(types);
    assertEquals(6, types.size(), "Should have 6 supported material types");
    assertTrue(types.contains("carbon_steel"));
    assertTrue(types.contains("2_25cr_1mo"));
    assertTrue(types.contains("austenitic_ss"));
  }

  @Test
  void testInvalidMaterialDefaultsToCarbonSteel() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setMaterialType("invalid_material");
    nelson.setTemperatureC(250.0);
    nelson.setH2PartialPressureBar(5.0);
    nelson.evaluate();

    // Should default to carbon_steel — still gives a result
    Map<String, Object> map = nelson.toMap();
    assertEquals("carbon_steel", map.get("materialType"));
  }

  @Test
  void test1Cr05Mo_IntermediateResistance() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(350.0);
    nelson.setH2PartialPressureBar(10.0);
    nelson.setMaterialType("1cr_0_5mo");
    nelson.evaluate();

    // 1Cr-0.5Mo should handle 350°C at 10 bar
    assertTrue(nelson.isBelowNelsonCurve(),
        "350°C at 10 bar should be safe for 1Cr-0.5Mo");
  }

  @Test
  void testC05Mo_FieldFailureWarning() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(280.0);
    nelson.setH2PartialPressureBar(5.0);
    nelson.setMaterialType("c_0_5mo");
    nelson.evaluate();

    // C-0.5Mo has higher curve than CS but API 941 warns of field failures
    assertTrue(nelson.isBelowNelsonCurve(),
        "280°C at 5 bar should be below C-0.5Mo curve");
  }

  @Test
  void testToMap() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(250.0);
    nelson.setH2PartialPressureBar(10.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    Map<String, Object> map = nelson.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("temperatureC"));
    assertTrue(map.containsKey("h2PartialPressure_bar"));
    assertTrue(map.containsKey("belowNelsonCurve"));
    assertTrue(map.containsKey("maxAllowableTemperature_C"));
    assertTrue(map.containsKey("riskLevel"));
    assertTrue(map.containsKey("standard"));
    assertEquals("API 941 8th Edition (2016)", map.get("standard"));
  }

  @Test
  void testToJson() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(300.0);
    nelson.setH2PartialPressureBar(20.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    String json = nelson.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("riskLevel"));
    assertTrue(json.contains("API 941"));
  }

  @Test
  void testMaterialHierarchy() {
    // Verify that higher alloy materials have higher temperature limits
    double[] maxTemps = new double[6];
    String[] materials = {"carbon_steel", "c_0_5mo", "1cr_0_5mo",
        "1_25cr_0_5mo", "2_25cr_1mo", "austenitic_ss"};

    for (int i = 0; i < materials.length; i++) {
      NelsonCurveAssessment nelson = new NelsonCurveAssessment();
      nelson.setH2PartialPressureBar(10.0);
      nelson.setTemperatureC(200.0);
      nelson.setMaterialType(materials[i]);
      nelson.evaluate();
      maxTemps[i] = nelson.getMaxAllowableTemperatureC();
    }

    // Each material should have a higher limit than the previous
    for (int i = 1; i < maxTemps.length; i++) {
      assertTrue(maxTemps[i] > maxTemps[i - 1],
          materials[i] + " (" + maxTemps[i] + "°C) should have higher limit than "
              + materials[i - 1] + " (" + maxTemps[i - 1] + "°C)");
    }
  }

  @Test
  void testHighPressureEdge() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    // Very high H2 pressure
    nelson.setH2PartialPressurePsia(15000.0);
    nelson.setTemperatureC(200.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    // Should extrapolate to the last data point
    double maxTemp = nelson.getMaxAllowableTemperatureC();
    // For carbon steel at very high pressure, max temp should be ~166°C (330°F)
    assertTrue(maxTemp > 100 && maxTemp < 200,
        "At very high pressure, carbon steel max temp should be ~166°C: " + maxTemp);
  }

  @Test
  void testZeroPressure() {
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setH2PartialPressureBar(0.0);
    nelson.setTemperatureC(250.0);
    nelson.setMaterialType("carbon_steel");
    nelson.evaluate();

    // At 0 pressure, max temp is the first point: 500°F = 260°C
    double maxTemp = nelson.getMaxAllowableTemperatureC();
    assertEquals(260.0, maxTemp, 2.0,
        "Max temp at 0 psia should be ~260°C: " + maxTemp);
    assertTrue(nelson.isBelowNelsonCurve(),
        "250°C should be safe at 0 pressure");
  }
}
