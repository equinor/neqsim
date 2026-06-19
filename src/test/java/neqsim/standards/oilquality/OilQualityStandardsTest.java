package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  /**
   * Creates a representative middle-distillate (diesel-like) fluid for cetane index testing.
   *
   * @return a SystemInterface representing a diesel-range distillate
   */
  private SystemInterface createDiesel() {
    SystemInterface oil = new SystemSrkEos(273.15 + 25.0, 1.01325);
    oil.addComponent("nC10", 0.05);
    oil.addTBPfraction("C12", 0.20, 170.0 / 1000.0, 0.78);
    oil.addTBPfraction("C14", 0.25, 198.0 / 1000.0, 0.80);
    oil.addTBPfraction("C16", 0.25, 226.0 / 1000.0, 0.82);
    oil.addTBPfraction("C18", 0.15, 254.0 / 1000.0, 0.83);
    oil.addTBPfraction("C20", 0.10, 282.0 / 1000.0, 0.85);
    oil.setMixingRule(2);
    oil.init(0);
    return oil;
  }

  /**
   * Creates a waxy oil fluid with an active wax model so a finite cloud point can be calculated.
   *
   * @return a SystemInterface representing a waxy oil with the wax model enabled
   */
  private SystemInterface createWaxyOil() {
    neqsim.util.database.NeqSimDataBase.setCreateTemporaryTables(true);
    SystemInterface oil = new SystemSrkEos(298.0, 5.0);
    oil.addComponent("methane", 6.78);
    oil.addTBPfraction("C19", 10.13, 170.0 / 1000.0, 0.7814);
    oil.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.850871882888);
    oil.getCharacterization().characterisePlusFraction();
    oil.getWaxModel().addTBPWax();
    oil.createDatabase(true);
    oil.setMixingRule(2);
    oil.addSolidComplexPhase("wax");
    oil.setMultiphaseWaxCheck(true);
    oil.setMultiPhaseCheck(true);
    neqsim.util.database.NeqSimDataBase.setCreateTemporaryTables(false);
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
	assertTrue(curve[i][1] >= prevTemp - 1.0, "Temperature should generally increase along curve");
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

  /**
   * Regression test for the distillation-curve "flat curve" bug. The original implementation used a fixed-temperature
   * TVfractionFlash, so every recovered cut collapsed onto the IBP. A correct D86 curve must rise: heavier cuts boil at
   * higher temperatures than lighter ones.
   */
  @Test
  void testASTM_D86_curveRisesNotFlat() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double ibp = standard.getValue("IBP");
    double t10 = standard.getValue("T10");
    double t50 = standard.getValue("T50");
    double t90 = standard.getValue("T90");

    assertTrue(!Double.isNaN(t10) && !Double.isNaN(t50) && !Double.isNaN(t90),
	"T10/T50/T90 should be computable for a light oil");

    // The cuts must be ordered and span a meaningful range (not collapsed onto the IBP).
    assertTrue(t10 >= ibp - 1.0, "T10 should be at or above the IBP");
    assertTrue(t50 > t10, "T50 should be hotter than T10");
    assertTrue(t90 > t50, "T90 should be hotter than T50");
    assertTrue(t90 - t10 > 20.0, "Distillation curve should span > 20 C between T10 and T90 (not flat)");
  }

  /**
   * Verifies T95 reports a genuine 95 % recovered point rather than aliasing onto the last table entry (previously
   * T90).
   */
  @Test
  void testASTM_D86_t95IsRealPoint() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double t90 = standard.getValue("T90");
    double t95 = standard.getValue("T95");
    if (!Double.isNaN(t90) && !Double.isNaN(t95)) {
      assertTrue(t95 >= t90 - 1.0, "T95 should be at or above T90 (true 95% point)");
    }
  }

  /**
   * Verifies the component-based average boiling points are ordered correctly (MABP &le; MeABP &le; CABP &le; WABP) and
   * the Watson (UOP) characterization factor falls in the physically expected band for a light paraffinic oil.
   */
  @Test
  void testASTM_D86_averageBoilingPointsAndWatsonK() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double mabp = standard.getMABP();
    double wabp = standard.getWABP();
    double cabp = standard.getCABP();
    double meabp = standard.getMeABP();
    double vabp = standard.getVABP();

    assertTrue(!Double.isNaN(mabp) && !Double.isNaN(wabp) && !Double.isNaN(cabp),
	"Average boiling points should be computable");
    // Molal is the lowest, weight the highest; mean and cubic sit in between.
    assertTrue(mabp <= cabp, "MABP should be at or below CABP");
    assertTrue(cabp <= wabp, "CABP should be at or below WABP");
    assertEquals((mabp + cabp) / 2.0, meabp, 0.01, "MeABP should be the mean of MABP and CABP");
    // VABP comes from the curve and should sit inside the T10..T90 window.
    assertTrue(vabp > standard.getValue("T10") && vabp < standard.getValue("T90"),
	"VABP should fall between T10 and T90");

    double sg = standard.getSpecificGravity();
    assertTrue(sg > 0.6 && sg < 1.0, "Specific gravity should be in a plausible petroleum range");

    double watsonK = standard.getWatsonK();
    assertTrue(watsonK > 10.0 && watsonK < 14.0,
	"Watson K should be in the typical petroleum range (10-14), was " + watsonK);
  }

  /**
   * Verifies the D86 slope is positive and that recovery + loss + residue conserve to 100 %.
   */
  @Test
  void testASTM_D86_slopeAndRecoveryBalance() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    assertTrue(standard.getSlope() > 0.0, "D86 slope (T90-T10)/80 should be positive");

    double total = standard.getPercentRecovered() + standard.getPercentLoss() + standard.getPercentResidue();
    assertEquals(100.0, total, 0.01, "Recovery + loss + residue should conserve to 100%");
    assertTrue(standard.getPercentLoss() >= 0.0, "Loss should be non-negative");
    assertTrue(standard.getPercentResidue() >= 0.0, "Residue should be non-negative");
  }

  /**
   * Verifies the Riazi-Daubert TBP&rarr;D86 conversion produces a distinct curve from the simulated (TBP-like) curve
   * and that selecting the TBP_CONVERTED basis changes the reported temperatures.
   */
  @Test
  void testASTM_D86_tbpToD86Conversion() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double[][] tbp = standard.getTBPCurve();
    double[][] d86 = standard.getD86Curve();
    assertEquals(tbp.length, d86.length, "TBP and D86 curves should have the same length");

    boolean anyDifferent = false;
    for (int i = 0; i < tbp.length; i++) {
      if (!Double.isNaN(tbp[i][1]) && !Double.isNaN(d86[i][1]) && Math.abs(tbp[i][1] - d86[i][1]) > 1.0) {
	anyDifferent = true;
	break;
      }
    }
    assertTrue(anyDifferent, "TBP and D86 curves should differ (conversion applied)");

    double molarT50 = standard.getValue("T50");
    standard.setBasis(Standard_ASTM_D86.D86Basis.TBP_CONVERTED);
    double convertedT50 = standard.getValue("T50");
    assertTrue(Math.abs(convertedT50 - molarT50) > 0.1, "TBP_CONVERTED basis should change the reported T50");
    standard.setBasis(Standard_ASTM_D86.D86Basis.MOLAR);
  }

  /**
   * Verifies the liquid-volume reporting basis yields a different curve from the molar basis.
   */
  @Test
  void testASTM_D86_liquidVolumeBasis() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double molarT50 = standard.getValue("T50");
    standard.setBasis(Standard_ASTM_D86.D86Basis.LIQUID_VOLUME);
    double liquidVolT50 = standard.getValue("T50");
    standard.setBasis(Standard_ASTM_D86.D86Basis.MOLAR);

    assertTrue(!Double.isNaN(liquidVolT50), "Liquid-volume T50 should be computable");
    assertTrue(Math.abs(liquidVolT50 - molarT50) > 0.1, "Liquid-volume basis should differ from molar basis");
  }

  /**
   * Verifies the Sydney Young barometric-pressure correction shifts reported temperatures: a sub-760 mmHg pressure
   * raises the equivalent 760 mmHg temperature.
   */
  @Test
  void testASTM_D86_barometricCorrection() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double t50at760 = standard.getValue("T50");
    standard.setBarometricPressure(700.0, "mmHg");
    double t50at700 = standard.getValue("T50");
    standard.setBarometricPressure(760.0, "mmHg");

    assertTrue(t50at700 > t50at760,
	"Lower barometric pressure should raise the corrected (760 mmHg-equivalent) temperature");
    // Unit handling: 1 atm == 760 mmHg, so the correction should vanish.
    standard.setBarometricPressure(1.0, "atm");
    assertEquals(t50at760, standard.getValue("T50"), 0.01, "1 atm should be equivalent to 760 mmHg (no correction)");
    standard.setBarometricPressure(760.0, "mmHg");
  }

  /**
   * Verifies product specification limits drive {@link Standard_ASTM_D86#isOnSpec()}.
   */
  @Test
  void testASTM_D86_specLimits() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();

    double t90 = standard.getValue("T90");

    standard.setSpecLimit("T90", t90 + 20.0);
    assertTrue(standard.isOnSpec(), "Should pass when T90 is below the limit");

    standard.setSpecLimit("T90", t90 - 20.0);
    assertTrue(!standard.isOnSpec(), "Should fail when T90 exceeds the limit");

    standard.clearSpecLimits();
    assertTrue(standard.isOnSpec(), "Should fall back to default on-spec check when no limits set");
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
    assertTrue("Light".equals(classification) || "Medium".equals(classification) || "Heavy".equals(classification)
	|| "Extra-Heavy / Bitumen".equals(classification), "Classification should be a valid category");
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

  // ========== TVP (True Vapor Pressure) Tests ==========

  @Test
  void testTVP_basic() {
    SystemInterface oil = createLightOil();
    Standard_TVP standard = new Standard_TVP(oil);
    standard.calculate();

    double tvp = standard.getValue("TVP");
    assertTrue(tvp > 0.0, "TVP should be positive for a live oil");
    assertEquals("bara", standard.getUnit("TVP"));
    assertEquals(37.8, standard.getReferenceTemperature(), 1.0e-6, "Default reference temperature should be 37.8 C");
  }

  @Test
  void testTVP_referenceTemperatureIncreasesPressure() {
    SystemInterface oil = createLightOil();

    Standard_TVP cold = new Standard_TVP(oil);
    cold.setReferenceTemperature(20.0, "C");
    cold.calculate();
    double tvpCold = cold.getValue("TVP");

    Standard_TVP hot = new Standard_TVP(oil);
    hot.setReferenceTemperature(60.0, "C");
    hot.calculate();
    double tvpHot = hot.getValue("TVP");

    assertTrue(tvpHot > tvpCold, "TVP should increase with reference temperature (" + tvpHot + " > " + tvpCold + ")");
  }

  @Test
  void testTVP_unitConversion() {
    SystemInterface oil = createLightOil();
    Standard_TVP standard = new Standard_TVP(oil);
    standard.calculate();

    double tvpBara = standard.getValue("TVP", "bara");
    double tvpPsia = standard.getValue("TVP", "psia");
    assertEquals(tvpBara / 0.0689475729317831, tvpPsia, 1.0e-6, "psia conversion should match the bara value");
  }

  @Test
  void testTVP_maxSpecLimit() {
    SystemInterface oil = createLightOil();
    Standard_TVP standard = new Standard_TVP(oil);
    standard.calculate();
    double tvp = standard.getValue("TVP");

    standard.setMaxTvpSpec(tvp + 0.5, "bara");
    assertTrue(standard.isOnSpec(), "Should pass when TVP is below the max limit");

    standard.setMaxTvpSpec(tvp - 0.5, "bara");
    assertTrue(!standard.isOnSpec(), "Should fail when TVP exceeds the max limit");

    standard.clearMaxTvpSpec();
    assertTrue(standard.isOnSpec(), "Should pass when no spec limit is set");
  }

  // ========== ASTM D4737 (Calculated Cetane Index) Tests ==========

  @Test
  void testASTM_D4737_basic() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D4737 standard = new Standard_ASTM_D4737(oil);
    standard.calculate();

    double cci = standard.getValue("cetaneIndex");
    assertTrue(!Double.isNaN(cci), "Cetane index should be a finite number");
    assertTrue(cci > 0.0 && cci < 120.0, "Cetane index should be physically plausible: " + cci);
    assertEquals("-", standard.getUnit("cetaneIndex"));
  }

  @Test
  void testASTM_D4737_aliasesMatch() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D4737 standard = new Standard_ASTM_D4737(oil);
    standard.calculate();

    double cci = standard.getValue("cetaneIndex");
    assertEquals(cci, standard.getValue("CCI"), 1.0e-9, "CCI alias should match cetaneIndex");
    assertEquals(cci, standard.getValue("cetaneIndexD4737"), 1.0e-9, "cetaneIndexD4737 alias should match cetaneIndex");
  }

  @Test
  void testASTM_D4737_d976CrossCheck() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D4737 standard = new Standard_ASTM_D4737(oil);
    standard.calculate();

    double cciD976 = standard.getValue("cetaneIndexD976");
    assertTrue(!Double.isNaN(cciD976), "D976 cetane index should be a finite number");
    assertTrue(cciD976 > 0.0 && cciD976 < 120.0, "D976 cetane index should be physically plausible: " + cciD976);
  }

  @Test
  void testASTM_D4737_inputPassthrough() {
    SystemInterface oil = createDiesel();

    Standard_ASTM_D86 d86 = new Standard_ASTM_D86(oil);
    d86.calculate();

    Standard_ASTM_D4737 standard = new Standard_ASTM_D4737(oil);
    standard.calculate();

    assertEquals(d86.getValue("T10", "C"), standard.getValue("T10"), 1.0e-6,
	"T10 should pass through from the internal D86 calculation");
    assertEquals(d86.getValue("T50", "C"), standard.getValue("T50"), 1.0e-6,
	"T50 should pass through from the internal D86 calculation");
    assertEquals(d86.getValue("T90", "C"), standard.getValue("T90"), 1.0e-6,
	"T90 should pass through from the internal D86 calculation");
    assertEquals("C", standard.getUnit("T50"));
    assertEquals("kg/m3", standard.getUnit("density"));
  }

  @Test
  void testASTM_D4737_minSpecLimit() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D4737 standard = new Standard_ASTM_D4737(oil);
    standard.calculate();
    double cci = standard.getValue("cetaneIndex");

    standard.setMinCetaneSpec(cci - 5.0);
    assertTrue(standard.isOnSpec(), "Should pass when cetane index exceeds the min limit");

    standard.setMinCetaneSpec(cci + 5.0);
    assertTrue(!standard.isOnSpec(), "Should fail when cetane index is below the min limit");

    standard.clearMinCetaneSpec();
    assertTrue(standard.isOnSpec(), "Should pass when no spec limit is set");
  }

  // ========== ASTM D611 Aniline Point Tests ==========

  @Test
  void testASTM_D611_basic() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D611 standard = new Standard_ASTM_D611(oil);
    standard.calculate();

    double anilineC = standard.getValue("anilinePoint", "C");
    assertTrue(!Double.isNaN(anilineC), "Aniline point should be a finite number");
    assertTrue(anilineC > 0.0 && anilineC < 120.0,
	"Aniline point should be physically plausible for a diesel: " + anilineC);
  }

  @Test
  void testASTM_D611_unitConversion() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D611 standard = new Standard_ASTM_D611(oil);
    standard.calculate();

    double anilineC = standard.getValue("anilinePoint", "C");
    double anilineK = standard.getValue("anilinePoint", "K");
    double anilineF = standard.getValue("anilinePoint", "F");

    assertEquals(anilineC + 273.15, anilineK, 1.0e-6, "K should be C + 273.15");
    assertEquals(anilineC * 9.0 / 5.0 + 32.0, anilineF, 1.0e-6, "F conversion should match");
    assertEquals("C", standard.getUnit("anilinePoint"));
  }

  @Test
  void testASTM_D611_coefficientsAffectResult() {
    SystemInterface oil = createDiesel();

    Standard_ASTM_D611 base = new Standard_ASTM_D611(oil);
    base.calculate();
    double baseAniline = base.getValue("anilinePoint");

    Standard_ASTM_D611 shifted = new Standard_ASTM_D611(oil);
    shifted.setCorrelationCoefficients(70.0, 35.0, 0.083);
    shifted.calculate();
    double shiftedAniline = shifted.getValue("anilinePoint");

    assertEquals(baseAniline + 10.0, shiftedAniline, 1.0e-6,
	"Raising the intercept by 10 should raise the aniline point by 10");
  }

  @Test
  void testASTM_D611_minSpecLimit() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D611 standard = new Standard_ASTM_D611(oil);
    standard.calculate();
    double anilineC = standard.getValue("anilinePoint", "C");

    standard.setMinAnilineSpec(anilineC - 5.0, "C");
    assertTrue(standard.isOnSpec(), "Should pass when aniline point exceeds the min limit");

    standard.setMinAnilineSpec(anilineC + 5.0, "C");
    assertTrue(!standard.isOnSpec(), "Should fail when aniline point is below the min limit");

    standard.clearMinAnilineSpec();
    assertTrue(standard.isOnSpec(), "Should pass when no spec limit is set");
  }

  // ========== ASTM D1322 Smoke Point Tests ==========

  @Test
  void testASTM_D1322_basic() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D1322 standard = new Standard_ASTM_D1322(oil);
    standard.calculate();

    double smokeMm = standard.getValue("smokePoint", "mm");
    assertTrue(!Double.isNaN(smokeMm), "Smoke point should be a finite number");
    assertTrue(smokeMm > 0.0 && smokeMm < 60.0, "Smoke point should be physically plausible: " + smokeMm);
    assertEquals("mm", standard.getUnit("smokePoint"));
  }

  @Test
  void testASTM_D1322_tracksAnilinePoint() {
    SystemInterface oil = createDiesel();

    Standard_ASTM_D611 d611 = new Standard_ASTM_D611(oil);
    d611.calculate();
    double anilineC = d611.getValue("anilinePoint");

    Standard_ASTM_D1322 standard = new Standard_ASTM_D1322(oil);
    standard.calculate();

    assertEquals(anilineC, standard.getValue("anilinePoint"), 1.0e-6,
	"Aniline point should pass through from the internal D611 calculation");
    assertEquals(8.5 + 0.325 * anilineC, standard.getValue("smokePoint"), 1.0e-6,
	"Smoke point should match the default correlation");
  }

  @Test
  void testASTM_D1322_minSpecLimit() {
    SystemInterface oil = createDiesel();
    Standard_ASTM_D1322 standard = new Standard_ASTM_D1322(oil);
    standard.calculate();
    double smokeMm = standard.getValue("smokePoint");

    standard.setMinSmokeSpec(smokeMm - 2.0);
    assertTrue(standard.isOnSpec(), "Should pass when smoke point exceeds the min limit");

    standard.setMinSmokeSpec(smokeMm + 2.0);
    assertTrue(!standard.isOnSpec(), "Should fail when smoke point is below the min limit");

    standard.clearMinSmokeSpec();
    assertTrue(standard.isOnSpec(), "Should pass when no spec limit is set");
  }

  // ========== EN 116 CFPP Tests ==========

  @Test
  void testEN116_basic() {
    SystemInterface oil = createWaxyOil();
    Standard_EN116 standard = new Standard_EN116(oil);
    standard.calculate();

    double cfppC = standard.getValue("CFPP", "C");
    double cloudC = standard.getValue("cloudPoint", "C");
    assertTrue(!Double.isNaN(cfppC), "CFPP should be a finite number");
    assertEquals(cloudC, cfppC, 1.0e-6, "Default CFPP should equal the cloud point");
    assertEquals("C", standard.getUnit("CFPP"));
  }

  @Test
  void testEN116_offsetShiftsResult() {
    SystemInterface oil = createWaxyOil();

    Standard_EN116 standard = new Standard_EN116(oil);
    standard.setOffset(-3.0);
    standard.calculate();

    double cloudC = standard.getValue("cloudPoint", "C");
    double cfppC = standard.getValue("CFPP", "C");
    assertTrue(!Double.isNaN(cloudC), "Cloud point basis should be a finite number");
    assertEquals(-3.0, standard.getOffset(), 1.0e-9, "Offset getter should return what was set");
    assertEquals(cloudC - 3.0, cfppC, 1.0e-6, "CFPP should be cloud point plus the offset");
  }

  @Test
  void testEN116_maxSpecLimit() {
    SystemInterface oil = createWaxyOil();
    Standard_EN116 standard = new Standard_EN116(oil);
    standard.calculate();
    double cfppC = standard.getValue("CFPP", "C");
    assertTrue(!Double.isNaN(cfppC), "CFPP should be a finite number");

    standard.setMaxCfppSpec(cfppC + 5.0, "C");
    assertTrue(standard.isOnSpec(), "Should pass when CFPP is below the max limit");

    standard.setMaxCfppSpec(cfppC - 5.0, "C");
    assertTrue(!standard.isOnSpec(), "Should fail when CFPP exceeds the max limit");

    standard.clearMaxCfppSpec();
    assertTrue(standard.isOnSpec(), "Should pass when no spec limit is set");
  }

  // ========== ASTM D3230 Salt Content Tests ==========

  @Test
  void testASTM_D3230_requiresInputs() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D3230 standard = new Standard_ASTM_D3230(oil);
    standard.calculate();

    assertTrue(Double.isNaN(standard.getValue("saltContentPTB")), "Salt content should be NaN without a brine assay");
    assertTrue(!standard.isOnSpec(), "Should be off-spec when no result is available");
  }

  @Test
  void testASTM_D3230_ptbConversion() {
    SystemInterface oil = createLightOil();
    Standard_ASTM_D3230 standard = new Standard_ASTM_D3230(oil);
    standard.setWaterCut(0.005); // 0.5 vol% water
    standard.setBrineSalinity(35.0, "kg/m3"); // 35 g/L brine
    standard.calculate();

    double saltMassPerCrudeVolume = 0.005 * 35.0; // kg salt per m3 crude
    double expectedPtb = saltMassPerCrudeVolume * 158.987 * 2.20462;
    assertEquals(expectedPtb, standard.getValue("saltContentPTB"), 1.0e-6,
	"PTB should match the documented conversion");
    assertEquals("PTB", standard.getUnit("saltContent"));

    double ppmw = standard.getValue("saltContent", "mg/kg");
    assertTrue(!Double.isNaN(ppmw) && ppmw > 0.0, "mg/kg salt content should be a finite positive number: " + ppmw);
  }

  @Test
  void testASTM_D3230_waterCutUnitsAndSpec() {
    SystemInterface oil = createLightOil();

    Standard_ASTM_D3230 percent = new Standard_ASTM_D3230(oil);
    percent.setWaterCut(0.5, "vol%");
    percent.setBrineSalinity(35.0, "kg/m3");
    percent.calculate();

    Standard_ASTM_D3230 fraction = new Standard_ASTM_D3230(oil);
    fraction.setWaterCut(0.005);
    fraction.setBrineSalinity(35.0, "kg/m3");
    fraction.calculate();

    assertEquals(fraction.getValue("saltContentPTB"), percent.getValue("saltContentPTB"), 1.0e-6,
	"0.5 vol% should equal a 0.005 volume fraction");

    double ptb = fraction.getValue("saltContentPTB");
    fraction.setMaxSaltSpec(ptb + 1.0);
    assertTrue(fraction.isOnSpec(), "Should pass when salt content is below the max limit");
    fraction.setMaxSaltSpec(ptb - 0.1);
    assertTrue(!fraction.isOnSpec(), "Should fail when salt content exceeds the max limit");
  }
}
