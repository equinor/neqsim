package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link HydrogenMaterialAssessment} — comprehensive hydrogen material compatibility
 * assessment integrating ASME B31.12, API 941, NACE MR0175/ISO 15156.
 *
 * @author ESOL
 * @version 1.0
 */
public class HydrogenMaterialAssessmentTest {

  @Test
  void testVeryLowH2_NegligibleRisk() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(0.05);
    assessment.setTotalPressureBar(90.0);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(25.0);
    assessment.evaluate();

    assertEquals("Negligible", assessment.getHydrogenEmbrittlementRisk());
    assertTrue(assessment.isHydrogenEmbrittlementAcceptable());
    assertEquals("Low", assessment.getOverallRiskLevel());
  }

  @Test
  void testLowH2_LowRisk() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(0.5);
    assessment.setTotalPressureBar(90.0);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(25.0);
    assessment.evaluate();

    assertEquals("Low", assessment.getHydrogenEmbrittlementRisk());
    assertTrue(assessment.isHydrogenEmbrittlementAcceptable());
  }

  @Test
  void testMediumH2_X52Acceptable() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setTotalPressureBar(90.0);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(25.0);
    assessment.setPwhtApplied(true);
    assessment.evaluate();

    assertEquals("Medium", assessment.getHydrogenEmbrittlementRisk());
    // X52 has SMYS 358 MPa <= 360 and PWHT applied, should be acceptable
    assertTrue(assessment.isHydrogenEmbrittlementAcceptable());
  }

  @Test
  void testMediumH2_HighGradeNotAcceptable() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setTotalPressureBar(90.0);
    assessment.setMaterialGrade("X70");
    assessment.setDesignTemperatureC(25.0);
    assessment.setPwhtApplied(false);
    assessment.evaluate();

    assertEquals("Medium", assessment.getHydrogenEmbrittlementRisk());
    // X70 has SMYS 483 MPa > 360, and no PWHT => not acceptable
    assertFalse(assessment.isHydrogenEmbrittlementAcceptable());
  }

  @Test
  void testHighH2_RequiresPWHTAndLowGrade() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(30.0);
    assessment.setTotalPressureBar(90.0);
    assessment.setMaterialGrade("X42");
    assessment.setDesignTemperatureC(25.0);
    assessment.setPwhtApplied(true);
    assessment.evaluate();

    assertEquals("High", assessment.getHydrogenEmbrittlementRisk());
    // X42 SMYS 290 <= 360, with PWHT → acceptable
    assertTrue(assessment.isHydrogenEmbrittlementAcceptable());
  }

  @Test
  void testHighH2_X65NotAcceptable() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(30.0);
    assessment.setTotalPressureBar(60.0);
    assessment.setMaterialGrade("X65");
    assessment.setDesignTemperatureC(25.0);
    assessment.setPwhtApplied(true);
    assessment.evaluate();

    assertEquals("High", assessment.getHydrogenEmbrittlementRisk());
    // X65 SMYS 448 > 360 → not acceptable even with PWHT
    assertFalse(assessment.isHydrogenEmbrittlementAcceptable());
  }

  @Test
  void testVeryHighH2() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(70.0);
    assessment.setTotalPressureBar(100.0);
    assessment.setMaterialGrade("X42");
    assessment.setDesignTemperatureC(25.0);
    assessment.evaluate();

    assertEquals("Very High", assessment.getHydrogenEmbrittlementRisk());
    // X42 SMYS 290 <= 290 → should be acceptable in very high range
    assertTrue(assessment.isHydrogenEmbrittlementAcceptable());
  }

  @Test
  void testVeryHighH2_X52NotAcceptable() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(70.0);
    assessment.setTotalPressureBar(100.0);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(25.0);
    assessment.evaluate();

    // X52 SMYS 358 > 290 → not acceptable in very high range
    assertFalse(assessment.isHydrogenEmbrittlementAcceptable());
  }

  @Test
  void testHTHANotApplicableLowTemp() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(10.0);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(100.0);
    assessment.setMaxOperatingTemperatureC(120.0);
    assessment.evaluate();

    assertTrue(assessment.getHTHARisk().contains("Not applicable"),
        "HTHA should not apply below 200°C: " + assessment.getHTHARisk());
    assertTrue(assessment.isHTHAAcceptable());
  }

  @Test
  void testHTHAHighTemp_CarbonSteel() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(20.0);
    assessment.setMaterialGrade("SA-516-70");
    assessment.setDesignTemperatureC(280.0);
    assessment.setMaxOperatingTemperatureC(280.0);
    assessment.evaluate();

    // 280°C + 20 bar H2 on carbon steel — should be above Nelson curve
    assertFalse(assessment.isHTHAAcceptable(),
        "280°C at 20 bar H2 should be above Nelson curve for carbon steel");
    assertTrue(assessment.getHTHARisk().contains("High") || assessment.getHTHARisk().contains("Very"),
        "Risk should be High or Very High: " + assessment.getHTHARisk());
  }

  @Test
  void testHTHAHighTemp_SafeCrMo() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(10.0);
    assessment.setMaterialGrade("2.25Cr-1Mo");
    assessment.setDesignTemperatureC(350.0);
    assessment.setMaxOperatingTemperatureC(350.0);
    assessment.evaluate();

    // 350°C + 10 bar H2 on 2.25Cr-1Mo — should be well below Nelson curve
    assertTrue(assessment.isHTHAAcceptable(),
        "350°C at 10 bar H2 should be safe for 2.25Cr-1Mo");
  }

  @Test
  void testSourServiceNotApplicable() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setH2SPartialPressureBar(0.0);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(25.0);
    assessment.evaluate();

    assertTrue(assessment.getHICRisk().contains("Not applicable"),
        "Should be N/A with no H2S: " + assessment.getHICRisk());
    assertTrue(assessment.isSourServiceOk());
  }

  @Test
  void testSourServiceMildSeverity() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(1.0);
    assessment.setH2SPartialPressureBar(0.1);
    assessment.setFreeWaterPresent(true);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(40.0);
    assessment.evaluate();

    assertFalse(assessment.getHICRisk().contains("Not applicable"),
        "Should flag sour service: " + assessment.getHICRisk());
    assertTrue(assessment.getHICRisk().contains("Region 1"),
        "pH2S 0.1 bar should be Region 1: " + assessment.getHICRisk());
  }

  @Test
  void testSourServiceHighSeverity() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setH2SPartialPressureBar(2.0);
    assessment.setFreeWaterPresent(true);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(40.0);
    assessment.evaluate();

    assertTrue(assessment.getHICRisk().contains("Region 3"),
        "pH2S 2 bar should be Region 3: " + assessment.getHICRisk());
  }

  @Test
  void testTraceH2S() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(1.0);
    assessment.setH2SPartialPressureBar(0.01);
    assessment.setFreeWaterPresent(true);
    assessment.setMaterialGrade("X52");
    assessment.evaluate();

    assertTrue(assessment.getHICRisk().contains("Trace"),
        "0.01 bar H2S should be 'Trace': " + assessment.getHICRisk());
    assertTrue(assessment.isSourServiceOk());
  }

  @Test
  void testCombinedRisk_MultiMechanism() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setH2SPartialPressureBar(0.1);
    assessment.setFreeWaterPresent(true);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(25.0);
    assessment.setPwhtApplied(true);
    assessment.evaluate();

    // Both HE (Medium) and HIC (Region 1) are active → combined risk should elevate
    String overall = assessment.getOverallRiskLevel();
    assertNotNull(overall);
    assertFalse(overall.isEmpty());
    // With multiple mechanisms, risk should be at least Medium
    assertTrue("Medium".equals(overall) || "High".equals(overall) || "Very High".equals(overall),
        "Combined risk should be elevated: " + overall);
  }

  @Test
  void testHardnessExceedsLimit() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setMaterialGrade("X52");
    assessment.setHardnessHRC(25.0);
    assessment.setDesignTemperatureC(25.0);
    assessment.evaluate();

    // 25 HRC > 22 HRC limit
    assertFalse(assessment.isHydrogenEmbrittlementAcceptable(),
        "Hardness 25 HRC should fail against 22 HRC limit");
    assertTrue(assessment.getWarnings().size() > 0, "Should have warnings about hardness");
  }

  @Test
  void testSmysExceedsLimit() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setMaterialGrade("X80");
    assessment.setDesignTemperatureC(25.0);
    assessment.evaluate();

    // X80 SMYS 552 MPa > 480 MPa limit
    assertFalse(assessment.isHydrogenEmbrittlementAcceptable(),
        "X80 (SMYS 552) should fail against 480 MPa limit");
  }

  @Test
  void testCyclicServiceIncreasesRisk() {
    // Without cyclic service
    HydrogenMaterialAssessment baseAssessment = new HydrogenMaterialAssessment();
    baseAssessment.setH2PartialPressureBar(3.0);
    baseAssessment.setMaterialGrade("X52");
    baseAssessment.setDesignTemperatureC(25.0);
    baseAssessment.setCyclicService(false);
    baseAssessment.setPwhtApplied(true);
    baseAssessment.evaluate();
    String baseRisk = baseAssessment.getHydrogenEmbrittlementRisk();

    // With cyclic service
    HydrogenMaterialAssessment cyclicAssessment = new HydrogenMaterialAssessment();
    cyclicAssessment.setH2PartialPressureBar(3.0);
    cyclicAssessment.setMaterialGrade("X52");
    cyclicAssessment.setDesignTemperatureC(25.0);
    cyclicAssessment.setCyclicService(true);
    cyclicAssessment.setPwhtApplied(true);
    cyclicAssessment.evaluate();
    String cyclicRisk = cyclicAssessment.getHydrogenEmbrittlementRisk();

    // Cyclic should elevate the risk or produce warnings
    boolean cyclicHasWarnings = false;
    for (String w : cyclicAssessment.getWarnings()) {
      if (w.contains("Cyclic") || w.contains("fatigue")) {
        cyclicHasWarnings = true;
        break;
      }
    }
    assertTrue(cyclicHasWarnings, "Cyclic service should produce fatigue warning");
  }

  @Test
  void testDeratingFactor_X42() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setTotalPressureBar(100.0);
    assessment.setH2MoleFractionGas(0.5);
    assessment.setMaterialGrade("X42");
    assessment.evaluate();

    double df = assessment.getHydrogenDeratingFactor();
    assertEquals(1.0, df, 0.01, "X42 (SMYS 290) derating should be 1.0");
  }

  @Test
  void testDeratingFactor_X70() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setTotalPressureBar(100.0);
    assessment.setH2MoleFractionGas(0.5);
    assessment.setMaterialGrade("X70");
    assessment.evaluate();

    double df = assessment.getHydrogenDeratingFactor();
    // X70 base factor 0.80, interpolated with 50% H2: 1 - 0.5*(1-0.80) = 0.90
    assertEquals(0.90, df, 0.02, "X70 at 50% H2 derating should be ~0.90");
  }

  @Test
  void testDeratingFactor_LowH2() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setTotalPressureBar(100.0);
    assessment.setH2MoleFractionGas(0.05);
    assessment.setMaterialGrade("X65");
    assessment.evaluate();

    double df = assessment.getHydrogenDeratingFactor();
    assertEquals(1.0, df, 0.01, "Below 10% H2, derating factor should be 1.0");
  }

  @Test
  void testPWHTWarningAbove5bar() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(10.0);
    assessment.setMaterialGrade("X52");
    assessment.setPwhtApplied(false);
    assessment.evaluate();

    boolean hasPwhtWarning = false;
    for (String w : assessment.getWarnings()) {
      if (w.contains("PWHT")) {
        hasPwhtWarning = true;
        break;
      }
    }
    assertTrue(hasPwhtWarning, "Should warn about missing PWHT at pH2 > 5 bar");
  }

  @Test
  void testMaterialRecommendation_Low() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(0.05);
    assessment.setMaterialGrade("X65");
    assessment.evaluate();

    String rec = assessment.getRecommendedMaterial();
    assertTrue(rec.contains("acceptable"),
        "Very low H2 should state material acceptable: " + rec);
  }

  @Test
  void testMaterialRecommendation_MediumH2_HighGrade() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setMaterialGrade("X65");
    assessment.evaluate();

    String rec = assessment.getRecommendedMaterial();
    assertTrue(rec.contains("X52") || rec.contains("lower"),
        "5 bar H2 with X65 should recommend downgrade: " + rec);
  }

  @Test
  void testSetFluidExtractsH2() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 90.0);
    fluid.addComponent("CO2", 0.95);
    fluid.addComponent("hydrogen", 0.01);
    fluid.addComponent("nitrogen", 0.04);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setFluid(fluid);
    assessment.setMaterialGrade("X52");
    assessment.evaluate();

    // Should extract H2 partial pressure from the fluid
    double ph2 = assessment.getH2PartialPressureBar();
    assertTrue(ph2 > 0.0, "Should extract pH2 from fluid: " + ph2);
    // ~1% H2 at 90 bar → ~0.9 bar (but phase split changes this)
    assertTrue(ph2 < 5.0, "pH2 should be reasonable: " + ph2);
  }

  @Test
  void testSetFluidExtractsH2S() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 60.0);
    fluid.addComponent("CO2", 0.90);
    fluid.addComponent("hydrogen", 0.02);
    fluid.addComponent("H2S", 0.005);
    fluid.addComponent("methane", 0.075);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setFluid(fluid);
    assessment.setFreeWaterPresent(true);
    assessment.setMaterialGrade("X52");
    assessment.evaluate();

    // Should detect both H2 and H2S
    assertTrue(assessment.getH2PartialPressureBar() > 0.0, "Should detect H2");
    assertFalse(assessment.getHICRisk().contains("Not applicable (no H2S)"),
        "Should detect H2S for sour assessment: " + assessment.getHICRisk());
  }

  @Test
  void testJsonOutput() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setH2SPartialPressureBar(0.1);
    assessment.setFreeWaterPresent(true);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(60.0);
    assessment.setPwhtApplied(true);
    assessment.evaluate();

    String json = assessment.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("overallRiskLevel"), "JSON should contain risk: " + json);
    assertTrue(json.contains("hydrogenEmbrittlement"), "JSON should contain HE assessment");
    assertTrue(json.contains("standardsApplied"), "JSON should list standards");
    assertTrue(json.contains("ASME B31.12"), "Should reference ASME B31.12");
  }

  @Test
  void testToMap() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(1.0);
    assessment.setMaterialGrade("X52");
    assessment.evaluate();

    Map<String, Object> map = assessment.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("inputConditions"));
    assertTrue(map.containsKey("assessment"));
    assertTrue(map.containsKey("recommendedMaterial"));
    assertTrue(map.containsKey("recommendations"));
    assertTrue(map.containsKey("standardsApplied"));
  }

  @Test
  void testStandardsApplied() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(5.0);
    assessment.setH2SPartialPressureBar(0.1);
    assessment.setFreeWaterPresent(true);
    assessment.setMaterialGrade("X52");
    assessment.setDesignTemperatureC(250.0);
    assessment.setMaxOperatingTemperatureC(250.0);
    assessment.evaluate();

    List<String> standards = assessment.getStandardsApplied();
    assertNotNull(standards);
    assertTrue(standards.size() >= 2, "Should apply at least 2 standards");

    boolean hasB3112 = false;
    boolean hasApi941 = false;
    boolean hasNace = false;
    for (String s : standards) {
      if (s.contains("B31.12")) {
        hasB3112 = true;
      }
      if (s.contains("941")) {
        hasApi941 = true;
      }
      if (s.contains("NACE") || s.contains("15156")) {
        hasNace = true;
      }
    }
    assertTrue(hasB3112, "Should apply ASME B31.12");
    assertTrue(hasApi941, "Should apply API 941 (T > 200°C)");
    assertTrue(hasNace, "Should apply NACE MR0175 (H2S present)");
  }

  @Test
  void testGradeAutoSetsSmys() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setMaterialGrade("X65");
    assessment.setH2PartialPressureBar(1.0);
    assessment.evaluate();

    // Internally SMYS should be 448 MPa for X65
    // We can verify indirectly through the derating factor
    // At 100% H2 fraction, X65 (448 MPa) should have derating < 1.0
    assessment.setTotalPressureBar(10.0);
    assessment.setH2MoleFractionGas(1.0);
    assessment.evaluate();
    double df = assessment.getHydrogenDeratingFactor();
    assertTrue(df < 1.0, "X65 at 100% H2 should have derating < 1.0: " + df);
  }

  @Test
  void test316L_ImmuneToHE() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(50.0);
    assessment.setTotalPressureBar(60.0);
    assessment.setMaterialGrade("316L");
    assessment.setDesignTemperatureC(25.0);
    assessment.evaluate();

    // 316L has SMYS 170 MPa, well below all limits
    assertTrue(assessment.isHydrogenEmbrittlementAcceptable(),
        "316L should be acceptable for hydrogen service");
  }

  @Test
  void testNelsonCurveAccessFromAssessment() {
    HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
    assessment.setH2PartialPressureBar(10.0);
    assessment.setMaterialGrade("SA-516-70");
    assessment.setDesignTemperatureC(300.0);
    assessment.setMaxOperatingTemperatureC(300.0);
    assessment.evaluate();

    NelsonCurveAssessment nelson = assessment.getNelsonCurveAssessment();
    assertNotNull(nelson, "Should expose Nelson curve assessment");
    // At 300°C + 10 bar H2 for carbon steel, should be above Nelson curve
    assertFalse(nelson.isBelowNelsonCurve(), "Should be above Nelson curve");
  }
}
