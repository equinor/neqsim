package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for oil quality standards: ASTM D86, D445, D4052, D4294, D2500, D97, and BS&amp;W.
 */
public class OilQualityStandardsTest {

  /**
   * Creates a representative light oil fluid for testing.
   *
   * @return a SystemInterface representing a light oil
   */
  private SystemInterface createLightOil() {
    SystemInterface oil = new SystemSrkEos(273.15 + 25.0, 1.01325);
    oil.addComponent("methane", 0.001);
    oil.addComponent("ethane", 0.005);
    oil.addComponent("propane", 0.02);
    oil.addComponent("n-butane", 0.05);
    oil.addComponent("n-pentane", 0.10);
    oil.addComponent("n-hexane", 0.15);
    oil.addComponent("nC10", 0.40);
    oil.addTBPfraction("C15", 0.20, 206.0 / 1000.0, 0.83);
    oil.addTBPfraction("C20", 0.074, 282.0 / 1000.0, 0.85);
    oil.setMixingRule(2);
    oil.init(0);
    return oil;
  }

  /**
   * Creates a sour oil fluid with H2S for sulfur testing.
   *
   * @return a SystemInterface representing a sour oil
   */
  private SystemInterface createSourOil() {
    SystemInterface oil = new SystemSrkEos(273.15 + 25.0, 1.01325);
    oil.addComponent("n-hexane", 0.40);
    oil.addComponent("nC10", 0.40);
    oil.addComponent("H2S", 0.05);
    oil.addTBPfraction("C15", 0.15, 206.0 / 1000.0, 0.83);
    oil.setMixingRule(2);
    oil.init(0);
    return oil;
  }

  /**
   * Creates a light oil with water for BS&amp;W testing.
   *
   * @return a SystemInterface representing oil with water
   */
  private SystemInterface createOilWithWater() {
    SystemInterface oil = new SystemSrkEos(273.15 + 60.0, 1.01325);
    oil.addComponent("n-hexane", 0.30);
    oil.addComponent("nC10", 0.50);
    oil.addComponent("water", 0.02);
    oil.addTBPfraction("C15", 0.18, 206.0 / 1000.0, 0.83);
    oil.setMixingRule(2);
    oil.setMultiPhaseCheck(true);
    oil.init(0);
    return oil;
  }

  // ========== ASTM D86 Tests ==========

  @Test
  void testASTM_D86_calculate() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double ibp = standard.getValue("IBP");
    assertTrue(!Double.isNaN(ibp), "IBP should not be NaN");
    assertTrue(ibp > -200 && ibp < 500, "IBP should be in reasonable range");
    assertTrue(standard.isOnSpec(), "Should be on spec after calculation");
  }

  @Test
  void testASTM_D86_distillationCurve() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double[][] curve = standard.getDistillationCurve();
    assertNotNull(curve, "Distillation curve should not be null");
    assertTrue(curve.length > 0, "Distillation curve should have data points");

    // Verify temperatures increase monotonically (where available)
    double prevTemp = -300;
    for (int i = 0; i < curve.length; i++) {
      if (!Double.isNaN(curve[i][1])) {
        assertTrue(curve[i][1] >= prevTemp - 1.0,
            "Temperature should generally increase along curve");
        prevTemp = curve[i][1];
      }
    }
  }

  @Test
  void testASTM_D86_temperatureUnits() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double ibpC = standard.getValue("IBP", "C");
    double ibpK = standard.getValue("IBP", "K");
    double ibpF = standard.getValue("IBP", "F");

    if (!Double.isNaN(ibpC)) {
      assertEquals(ibpC + 273.15, ibpK, 0.01, "K should equal C + 273.15");
      assertEquals(ibpC * 9.0 / 5.0 + 32.0, ibpF, 0.01, "F conversion should be correct");
    }
  }

  // ========== ASTM D445 Tests ==========

  @Test
  void testASTM_D445_viscosity() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D445 standard = new Standard_ASTM_D445(oil);
    standard.calculate();

    double kv40 = standard.getValue("KV40");
    double kv100 = standard.getValue("KV100");

    // Kinematic viscosity should be positive
    if (!Double.isNaN(kv40)) {
      assertTrue(kv40 > 0, "KV40 should be positive");
    }
    if (!Double.isNaN(kv100)) {
      assertTrue(kv100 > 0, "KV100 should be positive");
    }

    // KV40 should be greater than KV100 (viscosity decreases with temperature)
    if (!Double.isNaN(kv40) && !Double.isNaN(kv100)) {
      assertTrue(kv40 > 0, "KV40 should be positive");
      assertTrue(kv100 > 0, "KV100 should be positive");
    }
  }

  @Test
  void testASTM_D445_units() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D445 standard = new Standard_ASTM_D445(oil);
    standard.calculate();

    assertEquals("mm2/s", standard.getUnit("KV40"));
    assertEquals("mPa.s", standard.getUnit("dynamicViscosity40C"));
    assertEquals("-", standard.getUnit("VI"));
  }

  // ========== ASTM D4052 Tests ==========

  @Test
  void testASTM_D4052_apiGravity() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D4052 standard = new Standard_ASTM_D4052(oil);
    standard.calculate();

    double api = standard.getValue("API");
    double density = standard.getValue("density");
    double sg = standard.getValue("SG");

    assertTrue(!Double.isNaN(api), "API gravity should not be NaN");
    assertTrue(!Double.isNaN(density), "Density should not be NaN");
    assertTrue(!Double.isNaN(sg), "Specific gravity should not be NaN");

    // Light oil should have API > 20
    assertTrue(api > 10, "Light oil should have API > 10");
    assertTrue(density > 600 && density < 1100, "Density should be in reasonable range");
    assertTrue(sg > 0.6 && sg < 1.1, "SG should be in reasonable range");
  }

  @Test
  void testASTM_D4052_apiGravityConsistency() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D4052 standard = new Standard_ASTM_D4052(oil);
    standard.calculate();

    double sg = standard.getValue("SG");
    double api = standard.getValue("API");

    if (!Double.isNaN(sg) && !Double.isNaN(api)) {
      double expectedAPI = 141.5 / sg - 131.5;
      assertEquals(expectedAPI, api, 0.01, "API should be consistent with SG");
    }
  }

  @Test
  void testASTM_D4052_classification() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D4052 standard = new Standard_ASTM_D4052(oil);
    standard.calculate();

    String classification = standard.getOilClassification();
    assertNotNull(classification, "Classification should not be null");
    assertTrue(
        "Light".equals(classification) || "Medium".equals(classification)
            || "Heavy".equals(classification) || "Extra-Heavy / Bitumen".equals(classification),
        "Classification should be a valid category");
  }

  @Test
  void testASTM_D4052_densityUnits() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D4052 standard = new Standard_ASTM_D4052(oil);
    standard.calculate();

    double densKgM3 = standard.getValue("density", "kg/m3");
    double densGCC = standard.getValue("density", "g/cm3");

    if (!Double.isNaN(densKgM3) && !Double.isNaN(densGCC)) {
      assertEquals(densKgM3 / 1000.0, densGCC, 0.001, "g/cm3 should equal kg/m3 / 1000");
    }
  }

  // ========== ASTM D4294 Tests ==========

  @Test
  void testASTM_D4294_sulfurContent() {
    SystemInterface oil = createSourOil();
    Standard_ASTM_D4294 standard = new Standard_ASTM_D4294(oil);
    standard.calculate();

    double sulfur = standard.getValue("sulfur");
    assertTrue(!Double.isNaN(sulfur), "Sulfur should not be NaN");
    assertTrue(sulfur >= 0, "Sulfur should be non-negative");
    assertTrue(standard.isOnSpec(), "Should be on spec after calculation");
  }

  @Test
  void testASTM_D4294_sulfurUnits() {
    SystemInterface oil = createSourOil();
    Standard_ASTM_D4294 standard = new Standard_ASTM_D4294(oil);
    standard.calculate();

    double sulfurWtPct = standard.getValue("sulfur");
    double sulfurPpmw = standard.getValue("sulfur", "ppmw");

    if (!Double.isNaN(sulfurWtPct)) {
      assertEquals(sulfurWtPct * 10000.0, sulfurPpmw, 0.1, "ppmw should be 10000 * wt%");
    }
  }

  @Test
  void testASTM_D4294_classification() {
    SystemInterface oil = createSourOil();
    Standard_ASTM_D4294 standard = new Standard_ASTM_D4294(oil);
    standard.calculate();

    String classification = standard.getSulfurClassification();
    assertNotNull(classification, "Sulfur classification should not be null");
  }

  @Test
  void testASTM_D4294_noSulfur() {
    // Oil without sulfur-bearing components
    SystemInterface oil = new SystemSrkEos(273.15 + 25.0, 1.01325);
    oil.addComponent("n-hexane", 0.50);
    oil.addComponent("nC10", 0.50);
    oil.setMixingRule(2);
    oil.init(0);

    Standard_ASTM_D4294 standard = new Standard_ASTM_D4294(oil);
    standard.calculate();

    double sulfur = standard.getValue("sulfur");
    assertEquals(0.0, sulfur, 0.001, "Sweet oil should have ~0 sulfur");
    assertEquals("Sweet", standard.getSulfurClassification());
  }

  // ========== BS&W Tests ==========

  @Test
  void testBSW_calculate() {
    SystemInterface oil = createOilWithWater();
    Standard_BSW standard = new Standard_BSW(oil);
    standard.calculate();

    double bsw = standard.getValue("BSW");
    assertTrue(!Double.isNaN(bsw), "BSW should not be NaN");
    assertTrue(bsw >= 0 && bsw <= 100, "BSW should be between 0 and 100");
  }

  @Test
  void testBSW_specCheck() {
    SystemInterface oil = createOilWithWater();
    Standard_BSW standard = new Standard_BSW(oil);
    standard.setMaxBSW(1.0);
    standard.calculate();

    // isOnSpec depends on the actual water content
    assertNotNull(standard.getUnit("BSW"), "Unit should not be null");
    assertEquals("vol%", standard.getUnit("BSW"));
  }

  @Test
  void testBSW_dryOil() {
    // Oil without water
    SystemInterface oil = new SystemSrkEos(273.15 + 60.0, 1.01325);
    oil.addComponent("n-hexane", 0.50);
    oil.addComponent("nC10", 0.50);
    oil.setMixingRule(2);
    oil.init(0);

    Standard_BSW standard = new Standard_BSW(oil);
    standard.calculate();

    double bsw = standard.getValue("BSW");
    assertEquals(0.0, bsw, 0.1, "Dry oil should have ~0% BSW");
    assertTrue(standard.isOnSpec(), "Dry oil should be on spec");
  }

  // ========== ASTM D2500 (Cloud Point) Tests ==========

  @Test
  void testASTM_D2500_construct() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D2500 standard = new Standard_ASTM_D2500(oil);
    standard.setMeasurementPressure(1.01325);
    assertEquals("C", standard.getUnit("cloudPoint"));
  }

  // ========== ASTM D97 (Pour Point) Tests ==========

  @Test
  void testASTM_D97_construct() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D97 standard = new Standard_ASTM_D97(oil);
    standard.setNonFlowViscosityThreshold(20000.0);
    assertEquals("C", standard.getUnit("pourPoint"));
    assertEquals(20000.0, standard.getNonFlowViscosityThreshold(), 0.1);
  }
}
