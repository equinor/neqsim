package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for SourServiceAssessment, CO2CorrosionMaterialSelection, ChlorideSCCAssessment,
 * OxygenCorrosionAssessment, DensePhaseCO2Corrosion, and AmmoniaCompatibility.
 */
class CorrosionAssessmentTest {

  // ═══════════════════════════════════════════════════════════
  // SourServiceAssessment Tests
  // ═══════════════════════════════════════════════════════════

  @Test
  void testSourServiceRegion0_sweetService() {
    SourServiceAssessment a = new SourServiceAssessment();
    a.setH2SPartialPressureBar(0.001); // 0.1 kPa = 0.001 bar → Region 0
    a.setTotalPressureBar(50.0);
    a.setTemperatureC(80.0);
    a.setInSituPH(5.0);
    a.setMaterialGrade("Carbon steel");
    a.evaluate();

    assertEquals(0, a.getSourRegion());
    assertNotNull(a.getOverallRiskLevel());
    assertTrue(a.isSSCAcceptable());
    assertNotNull(a.toJson());
  }

  @Test
  void testSourServiceRegion1() {
    SourServiceAssessment a = new SourServiceAssessment();
    a.setH2SPartialPressureBar(0.005); // 0.5 kPa = 0.005 bar → Region 1
    a.setTotalPressureBar(100.0);
    a.setTemperatureC(60.0);
    a.setInSituPH(4.5);
    a.setMaterialGrade("Carbon steel");
    a.setHardnessHRC(20.0);
    a.evaluate();

    assertEquals(1, a.getSourRegion());
    assertTrue(a.isSSCAcceptable());
    assertNotNull(a.toJson());
  }

  @Test
  void testSourServiceRegion3_severe() {
    SourServiceAssessment a = new SourServiceAssessment();
    a.setH2SPartialPressureBar(0.5); // 50 kPa = 0.5 bar → Region 3
    a.setTotalPressureBar(200.0);
    a.setTemperatureC(80.0);
    a.setInSituPH(3.0);
    a.setMaterialGrade("Carbon steel");
    a.setHardnessHRC(25.0);
    a.evaluate();

    assertEquals(3, a.getSourRegion());
    assertNotNull(a.getOverallRiskLevel());
    assertFalse(a.isSSCAcceptable());
  }

  @Test
  void testSourServiceCRA22Cr() {
    SourServiceAssessment a = new SourServiceAssessment();
    a.setH2SPartialPressureBar(0.05); // 5 kPa = 0.05 bar → Region 2
    a.setTotalPressureBar(100.0);
    a.setTemperatureC(80.0);
    a.setInSituPH(4.0);
    a.setMaterialGrade("22Cr duplex");
    a.evaluate();

    assertTrue(a.isSSCAcceptable());
    assertNotNull(a.getRecommendedMaterial());
  }

  @Test
  void testSourServiceHighHardnessReject() {
    SourServiceAssessment a = new SourServiceAssessment();
    a.setH2SPartialPressureBar(0.015); // 1.5 kPa = 0.015 bar → Region 2
    a.setTotalPressureBar(50.0);
    a.setTemperatureC(60.0);
    a.setInSituPH(4.5);
    a.setMaterialGrade("Carbon steel");
    a.setHardnessHRC(25.0);
    a.evaluate();

    assertTrue(a.getSourRegion() >= 2);
    assertFalse(a.isSSCAcceptable());
  }

  @Test
  void testSourServiceWithElementalSulfur() {
    SourServiceAssessment a = new SourServiceAssessment();
    a.setH2SPartialPressureBar(0.02); // 2.0 kPa = 0.02 bar → Region 2
    a.setTotalPressureBar(100.0);
    a.setTemperatureC(60.0);
    a.setInSituPH(4.0);
    a.setMaterialGrade("Carbon steel");
    a.setElementalSulfurPresent(true);
    a.evaluate();

    assertTrue(a.getNotes().size() > 0);
  }

  @Test
  void testSourServiceToMap() {
    SourServiceAssessment a = new SourServiceAssessment();
    a.setH2SPartialPressureBar(0.005); // 0.5 kPa
    a.setTotalPressureBar(50.0);
    a.evaluate();

    java.util.Map<String, Object> map = a.toMap();
    assertNotNull(map.get("standard"));
    assertNotNull(map.get("sourRegion"));
    assertNotNull(map.get("overallRiskLevel"));
  }

  // ═══════════════════════════════════════════════════════════
  // CO2CorrosionMaterialSelection Tests
  // ═══════════════════════════════════════════════════════════

  @Test
  void testCO2CRA_lowConditions_carbonSteel() {
    CO2CorrosionMaterialSelection s = new CO2CorrosionMaterialSelection();
    s.setTemperatureC(40.0);
    s.setCO2PartialPressureBar(2.0);
    s.setH2SPartialPressureBar(0.0);
    s.setChlorideConcentrationMgL(5000.0);
    s.setInhibitionFeasible(true);
    s.setInhibitorAvailability(0.95);
    s.evaluate();

    assertEquals("Carbon steel + corrosion inhibition", s.getSelectedMaterial());
    assertTrue(s.isCarbonSteelViable());
    assertNotNull(s.toJson());
  }

  @Test
  void testCO2CRA_13Cr() {
    CO2CorrosionMaterialSelection s = new CO2CorrosionMaterialSelection();
    s.setTemperatureC(120.0);
    s.setCO2PartialPressureBar(5.0);
    s.setH2SPartialPressureBar(0.001); // Below sour threshold (0.003)
    s.setChlorideConcentrationMgL(50000.0);
    s.setInhibitionFeasible(false);
    s.setCO2CorrosionRateMmyr(2.0); // High rate makes CS not viable (CA > 6mm)
    s.evaluate();

    assertEquals("13Cr martensitic stainless steel", s.getSelectedMaterial());
  }

  @Test
  void testCO2CRA_highH2S_nickelAlloy() {
    CO2CorrosionMaterialSelection s = new CO2CorrosionMaterialSelection();
    s.setTemperatureC(200.0);
    s.setCO2PartialPressureBar(20.0);
    s.setH2SPartialPressureBar(5.0);
    s.setChlorideConcentrationMgL(150000.0);
    s.evaluate();

    assertEquals("Nickel alloy (Alloy 625 or C-276)", s.getSelectedMaterial());
    assertTrue(s.getRelativeCostFactor() > 5.0);
  }

  @Test
  void testCO2CRA_22CrDuplex() {
    CO2CorrosionMaterialSelection s = new CO2CorrosionMaterialSelection();
    s.setTemperatureC(180.0);
    s.setCO2PartialPressureBar(10.0);
    s.setH2SPartialPressureBar(0.5);
    s.setChlorideConcentrationMgL(90000.0);
    s.evaluate();

    assertEquals("22Cr duplex stainless steel", s.getSelectedMaterial());
  }

  @Test
  void testCO2CRA_costFactorOrdering() {
    CO2CorrosionMaterialSelection s = new CO2CorrosionMaterialSelection();
    s.setTemperatureC(40.0);
    s.setCO2PartialPressureBar(2.0);
    s.setInhibitionFeasible(true);
    s.setInhibitorAvailability(0.95);
    s.evaluate();
    double csCost = s.getRelativeCostFactor();

    CO2CorrosionMaterialSelection s2 = new CO2CorrosionMaterialSelection();
    s2.setTemperatureC(200.0);
    s2.setCO2PartialPressureBar(20.0);
    s2.setH2SPartialPressureBar(5.0);
    s2.setChlorideConcentrationMgL(150000.0);
    s2.evaluate();
    double niCost = s2.getRelativeCostFactor();

    assertTrue(niCost > csCost);
  }

  // ═══════════════════════════════════════════════════════════
  // ChlorideSCCAssessment Tests
  // ═══════════════════════════════════════════════════════════

  @Test
  void testChlorideSCC_316L_safe() {
    ChlorideSCCAssessment a = new ChlorideSCCAssessment();
    a.setMaterialType("316L");
    a.setTemperatureC(40.0);
    a.setChlorideConcentrationMgL(500);
    a.evaluate();

    assertTrue(a.isSCCAcceptable());
    assertEquals("Medium", a.getRiskLevel());
    assertTrue(a.getTemperatureMarginC() < 0);
  }

  @Test
  void testChlorideSCC_316L_unsafe() {
    ChlorideSCCAssessment a = new ChlorideSCCAssessment();
    a.setMaterialType("316L");
    a.setTemperatureC(80.0);
    a.setChlorideConcentrationMgL(50000);
    a.evaluate();

    assertFalse(a.isSCCAcceptable());
    assertEquals("Very High", a.getRiskLevel());
    assertTrue(a.getTemperatureMarginC() > 0);
  }

  @Test
  void testChlorideSCC_22CrDuplex() {
    ChlorideSCCAssessment a = new ChlorideSCCAssessment();
    a.setMaterialType("22Cr duplex");
    a.setTemperatureC(150.0);
    a.setChlorideConcentrationMgL(30000);
    a.evaluate();

    assertTrue(a.isSCCAcceptable());
    assertTrue(a.getMaxAllowableTemperatureC() > 200);
  }

  @Test
  void testChlorideSCC_nickelAlloy_immune() {
    ChlorideSCCAssessment a = new ChlorideSCCAssessment();
    a.setMaterialType("Alloy 625");
    a.setTemperatureC(250.0);
    a.setChlorideConcentrationMgL(200000);
    a.evaluate();

    assertTrue(a.isSCCAcceptable());
    assertEquals("Low", a.getRiskLevel());
  }

  @Test
  void testChlorideSCC_oxygenEffect() {
    ChlorideSCCAssessment a = new ChlorideSCCAssessment();
    a.setMaterialType("316L");
    a.setTemperatureC(55.0);
    a.setChlorideConcentrationMgL(800);
    a.setOxygenPresent(true);
    a.evaluate();

    // Oxygen reduces the safe envelope
    assertNotNull(a.getNotes());
    assertTrue(a.getNotes().size() > 0);
  }

  @Test
  void testChlorideSCC_toJson() {
    ChlorideSCCAssessment a = new ChlorideSCCAssessment();
    a.setMaterialType("316L");
    a.setTemperatureC(50.0);
    a.setChlorideConcentrationMgL(1000);
    a.evaluate();

    String json = a.toJson();
    assertNotNull(json);
    assertTrue(json.contains("sccAcceptable"));
    assertTrue(json.contains("riskLevel"));
  }

  // ═══════════════════════════════════════════════════════════
  // OxygenCorrosionAssessment Tests
  // ═══════════════════════════════════════════════════════════

  @Test
  void testOxygenCorrosion_lowO2_injection() {
    OxygenCorrosionAssessment a = new OxygenCorrosionAssessment();
    a.setDissolvedO2Ppb(5.0);
    a.setTemperatureC(30.0);
    a.setSystemType("injection_water");
    a.setMaterialType("Carbon steel");
    a.evaluate();

    assertTrue(a.isMeetsO2Target());
    assertEquals(10.0, a.getTargetO2Ppb());
    assertEquals("Low", a.getRiskLevel());
  }

  @Test
  void testOxygenCorrosion_highO2() {
    OxygenCorrosionAssessment a = new OxygenCorrosionAssessment();
    a.setDissolvedO2Ppb(500.0);
    a.setTemperatureC(50.0);
    a.setSystemType("injection_water");
    a.setMaterialType("Carbon steel");
    a.evaluate();

    assertFalse(a.isMeetsO2Target());
    assertTrue(a.getCorrosionRateMmYr() > 0.1);
    assertTrue(a.getPittingRateMmYr() > a.getCorrosionRateMmYr());
  }

  @Test
  void testOxygenCorrosion_CRA_lowRate() {
    OxygenCorrosionAssessment a = new OxygenCorrosionAssessment();
    a.setDissolvedO2Ppb(100.0);
    a.setTemperatureC(40.0);
    a.setMaterialType("22Cr duplex");
    a.evaluate();

    assertTrue(a.getCorrosionRateMmYr() < 0.1);
  }

  @Test
  void testOxygenCorrosion_closedLoop() {
    OxygenCorrosionAssessment a = new OxygenCorrosionAssessment();
    a.setDissolvedO2Ppb(3.0);
    a.setSystemType("closed_loop");
    a.evaluate();

    assertTrue(a.isMeetsO2Target());
    assertEquals(5.0, a.getTargetO2Ppb());
  }

  @Test
  void testOxygenCorrosion_treatmentRecommendation() {
    OxygenCorrosionAssessment a = new OxygenCorrosionAssessment();
    a.setDissolvedO2Ppb(200.0);
    a.setSystemType("injection_water");
    a.setScavengerApplied(false);
    a.setDeaerationApplied(false);
    a.evaluate();

    String treatment = a.getRecommendedTreatment();
    assertNotNull(treatment);
    assertTrue(treatment.length() > 0);
  }

  @Test
  void testOxygenCorrosion_highChloridePitting() {
    OxygenCorrosionAssessment a = new OxygenCorrosionAssessment();
    a.setDissolvedO2Ppb(50.0);
    a.setChlorideMgL(50000);
    a.setTemperatureC(40.0);
    a.evaluate();

    assertTrue(a.getPittingFactor() >= 3.0);
  }

  @Test
  void testOxygenCorrosion_toJson() {
    OxygenCorrosionAssessment a = new OxygenCorrosionAssessment();
    a.setDissolvedO2Ppb(100.0);
    a.evaluate();
    String json = a.toJson();
    assertNotNull(json);
    assertTrue(json.contains("riskLevel"));
    assertTrue(json.contains("corrosionRate_mmyr"));
  }

  // ═══════════════════════════════════════════════════════════
  // DensePhaseCO2Corrosion Tests
  // ═══════════════════════════════════════════════════════════

  @Test
  void testDenseCO2_drySafe() {
    DensePhaseCO2Corrosion a = new DensePhaseCO2Corrosion();
    a.setTemperatureC(25.0);
    a.setPressureBara(120.0);
    a.setCo2PurityMolPct(99.5);
    a.setWaterContentPpmv(200);
    a.evaluate();

    assertFalse(a.isFreeWaterRisk());
    assertEquals("Low", a.getRiskLevel());
    assertTrue(a.isMeetsImpuritySpecs());
    assertTrue(a.getWetCorrosionRateMmYr() < 0.01);
  }

  @Test
  void testDenseCO2_wetHighRisk() {
    DensePhaseCO2Corrosion a = new DensePhaseCO2Corrosion();
    a.setTemperatureC(10.0);
    a.setPressureBara(100.0);
    a.setCo2PurityMolPct(95.0);
    a.setWaterContentPpmv(5000);
    a.evaluate();

    assertTrue(a.isFreeWaterRisk());
    assertTrue(a.getWetCorrosionRateMmYr() > 1.0);
    assertTrue(a.getRiskLevel().equals("High") || a.getRiskLevel().equals("Very High"));
  }

  @Test
  void testDenseCO2_impurityExceedances() {
    DensePhaseCO2Corrosion a = new DensePhaseCO2Corrosion();
    a.setTemperatureC(25.0);
    a.setPressureBara(120.0);
    a.setWaterContentPpmv(100);
    a.setO2ContentPpmv(200);
    a.setSo2ContentPpmv(150);
    a.setH2sContentPpmv(300);
    a.evaluate();

    assertFalse(a.isMeetsImpuritySpecs());
    assertTrue(a.getImpurityIssues().size() >= 3);
  }

  @Test
  void testDenseCO2_supercriticalPhase() {
    DensePhaseCO2Corrosion a = new DensePhaseCO2Corrosion();
    a.setTemperatureC(40.0);
    a.setPressureBara(150.0);
    a.evaluate();

    assertEquals("Supercritical", a.getCo2PhaseState());
  }

  @Test
  void testDenseCO2_gasPhase() {
    DensePhaseCO2Corrosion a = new DensePhaseCO2Corrosion();
    a.setTemperatureC(40.0);
    a.setPressureBara(50.0);
    a.evaluate();

    assertEquals("Gas", a.getCo2PhaseState());
  }

  @Test
  void testDenseCO2_SO2O2synergy() {
    DensePhaseCO2Corrosion a = new DensePhaseCO2Corrosion();
    a.setTemperatureC(15.0);
    a.setPressureBara(100.0);
    a.setWaterContentPpmv(5000);
    a.setSo2ContentPpmv(100);
    a.setO2ContentPpmv(100);
    a.evaluate();

    // SO2+O2 synergy should increase corrosion
    assertTrue(a.isFreeWaterRisk());
    assertTrue(a.getNotes().stream().anyMatch(n -> n.contains("synergy")));
  }

  @Test
  void testDenseCO2_toJson() {
    DensePhaseCO2Corrosion a = new DensePhaseCO2Corrosion();
    a.setTemperatureC(25.0);
    a.setPressureBara(120.0);
    a.setWaterContentPpmv(150);
    a.evaluate();
    String json = a.toJson();
    assertNotNull(json);
    assertTrue(json.contains("co2PhaseState"));
    assertTrue(json.contains("freeWaterRisk"));
    assertTrue(json.contains("impurities"));
  }

  // ═══════════════════════════════════════════════════════════
  // AmmoniaCompatibility Tests
  // ═══════════════════════════════════════════════════════════

  @Test
  void testAmmonia_carbonSteel_withInhibitor() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("Carbon steel");
    a.setAnhydrous(true);
    a.setTemperatureC(25.0);
    a.setO2InhibitorWtPct(0.15);
    a.setPwhtApplied(true);
    a.setHardnessHRC(18.0);
    a.evaluate();

    assertTrue(a.isCompatible());
    assertEquals("Low", a.getRiskLevel());
    assertTrue(a.isO2InhibitorAdequate());
  }

  @Test
  void testAmmonia_carbonSteel_noInhibitor() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("Carbon steel");
    a.setAnhydrous(true);
    a.setO2InhibitorWtPct(0.0);
    a.evaluate();

    assertFalse(a.isCompatible());
    assertEquals("Very High", a.getRiskLevel());
    assertFalse(a.isO2InhibitorAdequate());
  }

  @Test
  void testAmmonia_copperAlwaysIncompatible() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("Copper");
    a.setTemperatureC(25.0);
    a.evaluate();

    assertFalse(a.isCompatible());
    assertEquals("Very High", a.getRiskLevel());
    assertTrue(a.getPrimaryMechanism().contains("SCC"));
  }

  @Test
  void testAmmonia_brassIncompatible() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("Brass");
    a.evaluate();

    assertFalse(a.isCompatible());
    assertEquals("Very High", a.getRiskLevel());
  }

  @Test
  void testAmmonia_316L_excellent() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("316L");
    a.setAnhydrous(true);
    a.setTemperatureC(100.0);
    a.evaluate();

    assertTrue(a.isCompatible());
    assertEquals("Low", a.getRiskLevel());
    assertTrue(a.getNotes().stream().anyMatch(n -> n.contains("immune")));
  }

  @Test
  void testAmmonia_aqueousConcentratedHot() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("Carbon steel");
    a.setAnhydrous(false);
    a.setNh3ConcentrationWtPct(30.0);
    a.setTemperatureC(80.0);
    a.evaluate();

    assertFalse(a.isCompatible());
    assertEquals("High", a.getRiskLevel());
  }

  @Test
  void testAmmonia_aqueousDiluteSafe() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("Carbon steel");
    a.setAnhydrous(false);
    a.setNh3ConcentrationWtPct(10.0);
    a.setTemperatureC(30.0);
    a.evaluate();

    assertTrue(a.isCompatible());
    assertEquals("Low", a.getRiskLevel());
  }

  @Test
  void testAmmonia_nickelAlloyHighTemp() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("Alloy 625");
    a.setTemperatureC(500.0);
    a.evaluate();

    assertTrue(a.isCompatible());
    assertEquals("Low", a.getRiskLevel());
    assertTrue(a.getMaxAllowableTempC() >= 600.0);
  }

  @Test
  void testAmmonia_nitridationWarning() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("Carbon steel");
    a.setAnhydrous(true);
    a.setTemperatureC(350.0);
    a.setO2InhibitorWtPct(0.15);
    a.evaluate();

    assertTrue(a.getNotes().stream().anyMatch(n -> n.contains("Nitridation")));
  }

  @Test
  void testAmmonia_toJson() {
    AmmoniaCompatibility a = new AmmoniaCompatibility();
    a.setMaterialType("316L");
    a.evaluate();
    String json = a.toJson();
    assertNotNull(json);
    assertTrue(json.contains("compatible"));
    assertTrue(json.contains("riskLevel"));
    assertTrue(json.contains("primaryMechanism"));
  }
}
