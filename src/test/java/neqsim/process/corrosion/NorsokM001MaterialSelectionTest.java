package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for NorsokM001MaterialSelection — material selection per NORSOK M-001.
 */
public class NorsokM001MaterialSelectionTest {

  @Test
  void testSweetServiceLowCorrosion() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.05);
    selector.setH2SPartialPressureBar(0.0);
    selector.setDesignTemperatureC(60.0);
    selector.setMaxDesignTemperatureC(80.0);
    selector.setDesignLifeYears(25);
    selector.evaluate();

    String material = selector.getRecommendedMaterial();
    assertTrue(material.contains("Carbon steel"),
        "Low CO2 corrosion should recommend CS: " + material);

    assertEquals("Sweet service (CO2 only)", selector.getServiceCategory());
    assertEquals("Non-sour", selector.getSourClassification());
  }

  @Test
  void testSweetServiceHighCorrosion() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.5);
    selector.setH2SPartialPressureBar(0.0);
    selector.setDesignTemperatureC(60.0);
    selector.setMaxDesignTemperatureC(80.0);
    selector.evaluate();

    String material = selector.getRecommendedMaterial();
    assertTrue(material.contains("13Cr"),
        "High CO2 corrosion without chlorides should recommend 13Cr: " + material);
  }

  @Test
  void testSweetServiceVeryHighCorrosion() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(2.0);
    selector.setH2SPartialPressureBar(0.0);
    selector.evaluate();

    String material = selector.getRecommendedMaterial();
    assertFalse(material.contains("Carbon steel"),
        "Very high corrosion should not recommend plain CS: " + material);
  }

  @Test
  void testSweetServiceHighCorrosionWithChlorides() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.5);
    selector.setChlorideConcentrationMgL(5000);
    selector.evaluate();

    String material = selector.getRecommendedMaterial();
    assertTrue(material.contains("Duplex"),
        "High corrosion + chlorides should recommend duplex: " + material);
  }

  @Test
  void testSourServiceMild() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.1);
    selector.setH2SPartialPressureBar(0.005);
    selector.evaluate();

    String sour = selector.getSourClassification();
    assertTrue(sour.contains("Mild sour"), "Should classify as mild sour: " + sour);

    String category = selector.getServiceCategory();
    assertTrue(category.contains("Sour"), "Service should be sour: " + category);

    // Notes should mention NACE compliance
    List<String> notes = selector.getNotes();
    boolean hasNACE = false;
    for (String note : notes) {
      if (note.contains("NACE") || note.contains("ISO 15156")) {
        hasNACE = true;
        break;
      }
    }
    assertTrue(hasNACE, "Notes should reference NACE/ISO 15156 for sour service");
  }

  @Test
  void testSourServiceSevere() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.5);
    selector.setH2SPartialPressureBar(0.5);
    selector.evaluate();

    String sour = selector.getSourClassification();
    assertTrue(sour.contains("Severe"), "Should classify as severe sour: " + sour);

    String material = selector.getRecommendedMaterial();
    assertFalse(material.contains("Carbon steel"),
        "Severe sour should not recommend plain CS: " + material);
  }

  @Test
  void testSourServiceSevereWithHighChlorides() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(1.0);
    selector.setH2SPartialPressureBar(0.5);
    selector.setChlorideConcentrationMgL(100000);
    selector.evaluate();

    String material = selector.getRecommendedMaterial();
    assertTrue(material.contains("C-276") || material.contains("625"),
        "Severe sour + very high chlorides should recommend nickel alloy: " + material);
  }

  @Test
  void testDryServiceNoCorrosion() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.0);
    selector.setH2SPartialPressureBar(0.1);
    selector.setFreeWaterPresent(false);
    selector.evaluate();

    String category = selector.getServiceCategory();
    assertTrue(category.contains("Dry"), "No free water should be dry service: " + category);

    String material = selector.getRecommendedMaterial();
    assertTrue(material.contains("Carbon steel"),
        "Dry service should allow CS: " + material);
  }

  @Test
  void testCorrosionAllowanceMinimum() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.01);
    selector.setDesignLifeYears(25);
    selector.evaluate();

    double ca = selector.getRecommendedCorrosionAllowanceMm();
    assertTrue(ca >= 1.0, "Minimum CA per NORSOK M-001 should be 1.0 mm: " + ca);
  }

  @Test
  void testCorrosionAllowanceMaximum() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.5);
    selector.setDesignLifeYears(30);
    // CA = 0.5 * 30 = 15 mm → should be capped at 6 mm with note
    // But this would trigger CRA recommendation, so use medium rate
    selector.setCO2CorrosionRateMmyr(0.25);
    selector.evaluate();
    // 0.25 * 30 = 7.5 mm → capped at 6 mm only if CS recommended
    // At 0.25, it recommends CS with increased CA
    double ca = selector.getRecommendedCorrosionAllowanceMm();
    assertTrue(ca <= 6.0, "CA should be capped at 6.0 mm: " + ca);
  }

  @Test
  void testSourServiceAddsExtraCA() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.05);
    selector.setDesignLifeYears(25);

    // Sweet service
    selector.setH2SPartialPressureBar(0.0);
    selector.evaluate();
    double caSweet = selector.getRecommendedCorrosionAllowanceMm();

    // Mild sour service (low CO2 rate → still CS)
    selector.setH2SPartialPressureBar(0.005);
    selector.evaluate();
    double caSour = selector.getRecommendedCorrosionAllowanceMm();

    assertTrue(caSour > caSweet,
        "Sour service should add extra CA: sour=" + caSour + " sweet=" + caSweet);
  }

  @Test
  void testCRAMaterialZeroCA() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(2.0);
    selector.setH2SPartialPressureBar(0.0);
    selector.evaluate();

    // High corrosion should recommend CRA
    String material = selector.getRecommendedMaterial();
    if (!material.contains("Carbon steel")) {
      double ca = selector.getRecommendedCorrosionAllowanceMm();
      assertEquals(0.0, ca, 0.01, "CRA material should have zero CA: " + ca);
    }
  }

  @Test
  void testChlorideSCCRiskClassification() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();

    // Low risk: low chlorides
    selector.setChlorideConcentrationMgL(10);
    selector.setMaxDesignTemperatureC(80.0);
    selector.evaluate();
    assertEquals("Low", selector.getChlorideSCCRisk());

    // Medium risk
    selector.setChlorideConcentrationMgL(500);
    selector.setMaxDesignTemperatureC(80.0);
    selector.evaluate();
    assertEquals("Medium", selector.getChlorideSCCRisk());

    // High risk
    selector.setChlorideConcentrationMgL(5000);
    selector.setMaxDesignTemperatureC(100.0);
    selector.evaluate();
    assertEquals("High", selector.getChlorideSCCRisk());
  }

  @Test
  void testAlternativeMaterials() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.5);
    selector.evaluate();

    List<String> alternatives = selector.getAlternativeMaterials();
    assertNotNull(alternatives);
    assertTrue(alternatives.size() >= 1, "Should have at least one alternative material");
  }

  @Test
  void testJsonOutput() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.5);
    selector.setH2SPartialPressureBar(0.01);
    selector.setChlorideConcentrationMgL(500);
    selector.evaluate();

    String json = selector.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("NORSOK M-001"), "JSON should reference standard");
    assertTrue(json.contains("recommendedMaterial"), "JSON should include recommendation");
    assertTrue(json.contains("serviceCategory"), "JSON should include service category");
  }

  @Test
  void testToMap() {
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.3);
    selector.evaluate();

    Map<String, Object> map = selector.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("inputConditions"));
    assertTrue(map.containsKey("serviceClassification"));
    assertTrue(map.containsKey("materialRecommendation"));
    assertTrue(map.containsKey("applicableStandards"));
  }

  @Test
  void testAutoEvaluateOnGetters() {
    // Getters should trigger evaluate() if not already run
    NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
    selector.setCO2CorrosionRateMmyr(0.5);
    // Don't call evaluate() explicitly

    String material = selector.getRecommendedMaterial();
    assertNotNull(material);
    assertFalse(material.isEmpty(), "Auto-evaluate should produce a recommendation");
  }

  @Test
  void testIntegrationWithM506Model() {
    // Test workflow: M-506 corrosion rate → M-001 material selection
    NorsokM506CorrosionRate m506 = new NorsokM506CorrosionRate(60.0, 100.0, 0.03);
    m506.setH2SMoleFraction(0.001);
    m506.calculate();

    NorsokM001MaterialSelection m001 = new NorsokM001MaterialSelection();
    m001.setCO2CorrosionRateMmyr(m506.getCorrectedCorrosionRate());
    m001.setH2SPartialPressureBar(m506.getH2SPartialPressureBar());
    m001.setCO2PartialPressureBar(m506.getCO2PartialPressureBar());
    m001.setDesignTemperatureC(60.0);
    m001.setDesignLifeYears(25);
    m001.evaluate();

    assertNotNull(m001.getRecommendedMaterial());
    assertNotNull(m001.getServiceCategory());
    assertNotNull(m001.getSourClassification());
    assertTrue(m001.getRecommendedCorrosionAllowanceMm() >= 0);
  }
}
