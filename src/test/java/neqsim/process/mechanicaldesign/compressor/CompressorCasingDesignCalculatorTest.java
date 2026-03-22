package neqsim.process.mechanicaldesign.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for CompressorCasingDesignCalculator.
 *
 * <p>
 * Verifies all 10 casing design features:
 * </p>
 * <ol>
 * <li>Casing wall thickness per ASME Section VIII Div.1</li>
 * <li>Material selection with SMYS/SMTS</li>
 * <li>Nozzle load analysis per API 617</li>
 * <li>Flange rating verification per ASME B16.5/B16.47</li>
 * <li>Hydrostatic test pressure</li>
 * <li>Corrosion allowance integration</li>
 * <li>NACE MR0175 material compliance</li>
 * <li>Thermal growth / differential expansion</li>
 * <li>Split-line bolt calculation for horizontally-split casings</li>
 * <li>Barrel casing inner/outer sizing</li>
 * </ol>
 */
public class CompressorCasingDesignCalculatorTest {

  /**
   * Test basic wall thickness calculation per ASME VIII UG-27.
   */
  @Test
  void testWallThicknessASMEVIII() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(50.0); // 5.0 MPa
    calc.setDesignTemperatureC(150.0);
    calc.setCasingInnerDiameterMm(600.0);
    calc.setCasingLengthMm(2000.0);
    calc.setMaterialGrade("SA-516-70");
    calc.setCorrosionAllowanceMm(1.5);
    calc.setJointEfficiency(0.85);
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);

    calc.calculate();

    // Wall thickness must be positive and above API 617 minimum of 12.7 mm
    assertTrue(calc.getSelectedWallThicknessMm() >= 12.7,
        "Wall thickness should be >= API 617 min 12.7 mm, got: "
            + calc.getSelectedWallThicknessMm());
    assertTrue(calc.getRequiredWallThicknessMm() > 0,
        "Required wall thickness should be positive");
    assertTrue(calc.getMinimumWallThicknessMm() > calc.getRequiredWallThicknessMm(),
        "Min thickness should include corrosion allowance");

    // Verify MAWP is >= design pressure
    assertTrue(calc.getMawpMPa() >= 5.0,
        "MAWP should be >= design pressure, got: " + calc.getMawpMPa());

    // Stress ratio must be <= 1.0
    assertTrue(calc.getStressRatio() <= 1.0,
        "Stress ratio should be <= 1.0, got: " + calc.getStressRatio());
  }

  /**
   * Test material property lookup for SA-516-70.
   */
  @Test
  void testMaterialSelection_SA516_70() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(30.0);
    calc.setDesignTemperatureC(100.0);
    calc.setCasingInnerDiameterMm(500.0);
    calc.setCasingLengthMm(1500.0);
    calc.setMaterialGrade("SA-516-70");
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);

    calc.calculate();

    assertEquals(260.0, calc.getSmysMPa(), 0.1, "SA-516-70 SMYS should be 260 MPa");
    assertEquals(485.0, calc.getSmtsMPa(), 0.1, "SA-516-70 SMTS should be 485 MPa");
    assertTrue(calc.getAllowableStressMPa() > 100.0, "Allowable stress should be > 100 MPa");
    assertFalse(calc.isMaterialNaceCompliant(), "SA-516-70 is not NACE compliant by default");
  }

  /**
   * Test material property lookup for SA-182-F316L (austenitic SS).
   */
  @Test
  void testMaterialSelection_F316L() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(80.0);
    calc.setDesignTemperatureC(200.0);
    calc.setCasingInnerDiameterMm(500.0);
    calc.setCasingLengthMm(1500.0);
    calc.setMaterialGrade("SA-182-F316L");
    calc.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);

    calc.calculate();

    assertEquals(170.0, calc.getSmysMPa(), 0.1, "F316L SMYS should be 170 MPa");
    assertTrue(calc.isMaterialNaceCompliant(), "F316L should be NACE compliant");
    assertEquals("AusteniticSS", calc.getMaterialType());
  }

  /**
   * Test Inconel-718 for high pressure sour service.
   */
  @Test
  void testMaterialSelection_Inconel718() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(200.0);
    calc.setDesignTemperatureC(250.0);
    calc.setCasingInnerDiameterMm(400.0);
    calc.setCasingLengthMm(1200.0);
    calc.setMaterialGrade("Inconel-718");
    calc.setSourService(true);
    calc.setH2sPartialPressureKPa(5.0);
    calc.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);

    calc.calculate();

    assertEquals(1035.0, calc.getSmysMPa(), 0.1, "Inconel-718 SMYS should be 1035 MPa");
    assertTrue(calc.isMaterialNaceCompliant(), "Inconel-718 should be NACE compliant");
    assertEquals("COMPLIANT", calc.getNaceComplianceStatus());
  }

  /**
   * Test nozzle load analysis per API 617 Table 3.
   */
  @Test
  void testNozzleLoadAnalysis() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(50.0);
    calc.setDesignTemperatureC(150.0);
    calc.setCasingInnerDiameterMm(600.0);
    calc.setCasingLengthMm(2000.0);
    calc.setSuctionNozzleSizeMm(300.0); // 12 inch
    calc.setDischargeNozzleSizeMm(200.0); // 8 inch
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);

    calc.calculate();

    // Allowable forces must be positive
    assertTrue(calc.getSuctionNozzleAllowableForceN() > 0,
        "Suction nozzle allowable force should be positive");
    assertTrue(calc.getDischargeNozzleAllowableForceN() > 0,
        "Discharge nozzle allowable force should be positive");

    // Larger nozzle should have larger allowable loads
    assertTrue(calc.getSuctionNozzleAllowableForceN() > calc.getDischargeNozzleAllowableForceN(),
        "Larger suction nozzle should have larger allowable force");
    assertTrue(
        calc.getSuctionNozzleAllowableMomentNm() > calc.getDischargeNozzleAllowableMomentNm(),
        "Larger suction nozzle should have larger allowable moment");
  }

  /**
   * Test flange rating verification per ASME B16.5.
   */
  @Test
  void testFlangeRatingSelection() {
    // Low pressure - should select Class 150 or 300
    CompressorCasingDesignCalculator calcLow = new CompressorCasingDesignCalculator();
    calcLow.setDesignPressureBara(15.0);
    calcLow.setDesignTemperatureC(100.0);
    calcLow.setCasingInnerDiameterMm(500.0);
    calcLow.setCasingLengthMm(1500.0);
    calcLow.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);
    calcLow.calculate();

    assertTrue(calcLow.getFlangeClass() <= 300,
        "Low pressure should use Class 150 or 300, got: " + calcLow.getFlangeClass());
    assertTrue(calcLow.isFlangeRatingAdequate(), "Flange should be adequate for low pressure");

    // High pressure - should select higher class
    CompressorCasingDesignCalculator calcHigh = new CompressorCasingDesignCalculator();
    calcHigh.setDesignPressureBara(80.0);
    calcHigh.setDesignTemperatureC(200.0);
    calcHigh.setCasingInnerDiameterMm(500.0);
    calcHigh.setCasingLengthMm(1500.0);
    calcHigh.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);
    calcHigh.calculate();

    assertTrue(calcHigh.getFlangeClass() >= 600,
        "High pressure should use Class 600+, got: " + calcHigh.getFlangeClass());
  }

  /**
   * Test hydrostatic test pressure per ASME VIII UG-99.
   */
  @Test
  void testHydrostaticTestPressure() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(50.0); // 5.0 MPa
    calc.setDesignTemperatureC(150.0);
    calc.setCasingInnerDiameterMm(600.0);
    calc.setCasingLengthMm(2000.0);
    calc.setMaterialGrade("SA-516-70");
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);

    calc.calculate();

    // Hydro test should be >= 1.3 * MAWP or >= 1.5 * design pressure
    assertTrue(calc.getHydroTestPressureMPa() >= 1.3 * calc.getMawpMPa(),
        "Hydro test should be >= 1.3 * MAWP");
    assertTrue(calc.getHydroTestPressureMPa() >= 1.5 * 5.0,
        "Hydro test should be >= 1.5 * design pressure");
    assertTrue(calc.isHydroTestAcceptable(), "Hydro test should be acceptable");
  }

  /**
   * Test corrosion allowance integration.
   */
  @Test
  void testCorrosionAllowance() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(40.0);
    calc.setDesignTemperatureC(100.0);
    calc.setCasingInnerDiameterMm(500.0);
    calc.setCasingLengthMm(1500.0);
    calc.setCorrosionAllowanceMm(3.0);
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);

    calc.calculate();

    double requiredThk = calc.getRequiredWallThicknessMm();
    double minThk = calc.getMinimumWallThicknessMm();

    // Minimum thickness includes corrosion allowance
    assertTrue(minThk >= requiredThk + 3.0,
        "Min thickness should include 3mm corrosion allowance");
  }

  /**
   * Test NACE MR0175 compliance check for sour service.
   */
  @Test
  void testNACECompliance_SourService() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(50.0);
    calc.setDesignTemperatureC(120.0);
    calc.setCasingInnerDiameterMm(500.0);
    calc.setCasingLengthMm(1500.0);
    calc.setSourService(true);
    calc.setH2sPartialPressureKPa(5.0);

    // Non-NACE material
    calc.setMaterialGrade("SA-516-70");
    calc.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);
    calc.calculate();

    assertFalse("COMPLIANT".equals(calc.getNaceComplianceStatus()),
        "SA-516-70 should not be fully NACE compliant");
    assertTrue(calc.getNaceIssues().size() > 0,
        "Should have NACE issues for carbon steel in sour service");

    // NACE compliant material
    CompressorCasingDesignCalculator calcNACE = new CompressorCasingDesignCalculator();
    calcNACE.setDesignPressureBara(50.0);
    calcNACE.setDesignTemperatureC(120.0);
    calcNACE.setCasingInnerDiameterMm(500.0);
    calcNACE.setCasingLengthMm(1500.0);
    calcNACE.setSourService(true);
    calcNACE.setH2sPartialPressureKPa(5.0);
    calcNACE.setMaterialGrade("SA-182-F316L");
    calcNACE.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);
    calcNACE.calculate();

    assertEquals("COMPLIANT", calcNACE.getNaceComplianceStatus(),
        "F316L should be NACE compliant");
  }

  /**
   * Test NACE not applicable when no sour service.
   */
  @Test
  void testNACECompliance_SweetService() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(50.0);
    calc.setDesignTemperatureC(120.0);
    calc.setCasingInnerDiameterMm(500.0);
    calc.setCasingLengthMm(1500.0);
    calc.setSourService(false);
    calc.setH2sPartialPressureKPa(0.0);
    calc.setMaterialGrade("SA-516-70");
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);
    calc.calculate();

    assertEquals("NOT_APPLICABLE", calc.getNaceComplianceStatus());
  }

  /**
   * Test thermal growth and differential expansion.
   */
  @Test
  void testThermalGrowth() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(40.0);
    calc.setDesignTemperatureC(200.0);
    calc.setMaxOperatingTemperatureC(180.0);
    calc.setAmbientTemperatureC(20.0);
    calc.setCasingInnerDiameterMm(600.0);
    calc.setCasingLengthMm(2500.0);
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);

    calc.calculate();

    // Thermal growth must be positive for hot operation
    assertTrue(calc.getCasingAxialGrowthMm() > 0,
        "Casing axial growth should be positive for elevated temp");

    // Differential expansion should be reasonable (< 3 mm for most)
    assertTrue(Math.abs(calc.getDifferentialExpansionMm()) < 5.0,
        "Differential expansion should be reasonable");
  }

  /**
   * Test split-line bolt calculation for horizontally-split casing.
   */
  @Test
  void testSplitLineBolts() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(50.0);
    calc.setDesignTemperatureC(150.0);
    calc.setCasingInnerDiameterMm(600.0);
    calc.setCasingLengthMm(2000.0);
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);

    calc.calculate();

    // Split-line bolts should be calculated
    assertTrue(calc.getSplitLineBoltCount() >= 8,
        "Should have at least 8 split-line bolts, got: " + calc.getSplitLineBoltCount());
    assertTrue(calc.getSplitLineBoltDiameterMm() >= 16.0,
        "Bolt diameter should be >= M16, got: " + calc.getSplitLineBoltDiameterMm());
    assertTrue(calc.isSplitLineBoltsAdequate(), "Bolt design should be adequate");
  }

  /**
   * Test barrel casing design (inner/outer sizing).
   */
  @Test
  void testBarrelCasingDesign() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(150.0); // High pressure -> barrel
    calc.setDesignTemperatureC(180.0);
    calc.setCasingInnerDiameterMm(500.0);
    calc.setCasingLengthMm(1800.0);
    calc.setMaterialGrade("SA-266-Gr4");
    calc.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);

    calc.calculate();

    // Barrel OD should be > ID
    assertTrue(calc.getBarrelOuterODMm() > 500.0,
        "Barrel OD should be > casing ID, got: " + calc.getBarrelOuterODMm());

    // End cover should have reasonable thickness
    assertTrue(calc.getBarrelEndCoverThicknessMm() >= 25.0,
        "End cover should be >= 25mm, got: " + calc.getBarrelEndCoverThicknessMm());

    // End cover bolts
    assertTrue(calc.getBarrelEndCoverBoltCount() >= 12,
        "Should have >= 12 end cover bolts, got: " + calc.getBarrelEndCoverBoltCount());
  }

  /**
   * Test automatic material recommendation.
   */
  @Test
  void testMaterialRecommendation() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();

    // Standard service
    calc.setSourService(false);
    calc.setMinOperatingTemperatureC(10.0);
    calc.setDesignTemperatureC(150.0);
    calc.setDesignPressureMPa(3.0);
    assertEquals("SA-516-70", calc.recommendMaterial());

    // Sour service
    calc.setSourService(true);
    calc.setH2sPartialPressureKPa(5.0);
    calc.setDesignTemperatureC(150.0);
    assertEquals("SA-182-F316L", calc.recommendMaterial());

    // Sour service at high temp
    calc.setDesignTemperatureC(250.0);
    assertEquals("Inconel-718", calc.recommendMaterial());

    // Low temperature (non-sour)
    calc.setSourService(false);
    calc.setH2sPartialPressureKPa(0.0);
    calc.setMinOperatingTemperatureC(-40.0);
    calc.setDesignTemperatureC(100.0);
    assertEquals("SA-350-LF2", calc.recommendMaterial());

    // High pressure barrel
    calc.setMinOperatingTemperatureC(10.0);
    calc.setDesignPressureMPa(12.0);
    calc.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);
    assertEquals("SA-266-Gr4", calc.recommendMaterial());
  }

  /**
   * Test JSON output includes all design sections.
   */
  @Test
  void testJsonOutput() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(50.0);
    calc.setDesignTemperatureC(150.0);
    calc.setCasingInnerDiameterMm(600.0);
    calc.setCasingLengthMm(2000.0);
    calc.setSourService(true);
    calc.setH2sPartialPressureKPa(2.0);
    calc.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);

    calc.calculate();

    String json = calc.toJson();
    assertNotNull(json);
    assertTrue(json.contains("wallThickness"), "JSON should contain wall thickness section");
    assertTrue(json.contains("hydrostaticTest"), "JSON should contain hydro test section");
    assertTrue(json.contains("flangeRating"), "JSON should contain flange rating section");
    assertTrue(json.contains("nozzleLoads"), "JSON should contain nozzle loads section");
    assertTrue(json.contains("thermalGrowth"), "JSON should contain thermal growth section");
    assertTrue(json.contains("splitLineBolts"), "JSON should contain split-line bolts section");
    assertTrue(json.contains("naceAssessment"), "JSON should contain NACE assessment section");
    assertTrue(json.contains("appliedStandards"), "JSON should list applied standards");
  }

  /**
   * Test toMap output structure.
   */
  @Test
  void testToMapOutput() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setDesignPressureBara(50.0);
    calc.setDesignTemperatureC(150.0);
    calc.setCasingInnerDiameterMm(600.0);
    calc.setCasingLengthMm(2000.0);
    calc.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);
    calc.calculate();

    Map<String, Object> map = calc.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("inputs"));
    assertTrue(map.containsKey("materialProperties"));
    assertTrue(map.containsKey("wallThickness"));
    assertTrue(map.containsKey("hydrostaticTest"));
    assertTrue(map.containsKey("flangeRating"));
    assertTrue(map.containsKey("nozzleLoads"));
    assertTrue(map.containsKey("thermalGrowth"));
    assertTrue(map.containsKey("barrelCasing"),
        "Barrel casing section should be present for barrel type");
    assertFalse(map.containsKey("splitLineBolts"),
        "Split-line bolts should NOT be present for barrel type");
  }

  /**
   * Test integration via CompressorMechanicalDesign.calcDesign().
   */
  @Test
  void testIntegrationWithCompressorMechanicalDesign() {
    SystemInterface gas = new SystemSrkEos(300.0, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(10000.0, "kg/hr");

    Compressor comp = new Compressor("comp", inlet);
    comp.setOutletPressure(40.0);
    comp.setPolytropicEfficiency(0.75);
    comp.setSpeed(8000);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(comp);
    ps.run();

    CompressorMechanicalDesign design = comp.getMechanicalDesign();
    design.setCasingMaterialGrade("SA-516-70");
    design.setCasingCorrosionAllowanceMm(1.5);
    design.calcDesign();

    // Casing design calculator should be populated
    CompressorCasingDesignCalculator casingCalc = design.getCasingDesignCalculator();
    assertNotNull(casingCalc, "Casing design calculator should be created after calcDesign()");
    assertTrue(casingCalc.getSelectedWallThicknessMm() > 0,
        "Wall thickness should be calculated");
    assertTrue(casingCalc.getMawpMPa() > 0, "MAWP should be calculated");
    assertTrue(casingCalc.getFlangeClass() >= 150, "Flange class should be selected");
    assertTrue(casingCalc.isHydroTestAcceptable(), "Hydro test should pass");
  }

  /**
   * Test JSON output from CompressorMechanicalDesign includes casing design.
   */
  @Test
  void testIntegrationJsonOutput() {
    SystemInterface gas = new SystemSrkEos(300.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(5000.0, "kg/hr");

    Compressor comp = new Compressor("HP comp", inlet);
    comp.setOutletPressure(150.0);
    comp.setPolytropicEfficiency(0.78);
    comp.setSpeed(10000);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(comp);
    ps.run();

    comp.getMechanicalDesign().setCasingMaterialGrade("SA-266-Gr4");
    comp.getMechanicalDesign().calcDesign();

    String json = comp.getMechanicalDesign().toJson();
    assertNotNull(json);
    assertTrue(json.contains("casingDesign"), "JSON should include casing design section");
    assertTrue(json.contains("wallThickness"), "JSON should include wall thickness within casing");
  }

  /**
   * Test sour service integration with NACE via CompressorMechanicalDesign.
   */
  @Test
  void testIntegrationSourService() {
    SystemInterface gas = new SystemSrkEos(300.0, 30.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("H2S", 0.01);
    gas.addComponent("CO2", 0.09);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(8000.0, "kg/hr");

    Compressor comp = new Compressor("sour comp", inlet);
    comp.setOutletPressure(80.0);
    comp.setPolytropicEfficiency(0.76);
    comp.setSpeed(9000);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(comp);
    ps.run();

    CompressorMechanicalDesign design = comp.getMechanicalDesign();
    design.setNaceCompliance(true);
    design.setH2sPartialPressureKPa(3.0);
    design.setCasingMaterialGrade("SA-182-F316L");
    design.calcDesign();

    CompressorCasingDesignCalculator casingCalc = design.getCasingDesignCalculator();
    assertNotNull(casingCalc);
    assertEquals("COMPLIANT", casingCalc.getNaceComplianceStatus(),
        "F316L should be NACE compliant");
  }

  /**
   * Test high temperature derating of allowable stress.
   */
  @Test
  void testHighTemperatureDerating() {
    CompressorCasingDesignCalculator calcLow = new CompressorCasingDesignCalculator();
    calcLow.setDesignPressureBara(50.0);
    calcLow.setDesignTemperatureC(100.0); // Below derating threshold
    calcLow.setCasingInnerDiameterMm(500.0);
    calcLow.setCasingLengthMm(1500.0);
    calcLow.setMaterialGrade("SA-516-70");
    calcLow.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);
    calcLow.calculate();
    double allowableLow = calcLow.getAllowableStressMPa();

    CompressorCasingDesignCalculator calcHigh = new CompressorCasingDesignCalculator();
    calcHigh.setDesignPressureBara(50.0);
    calcHigh.setDesignTemperatureC(350.0); // Above derating threshold
    calcHigh.setCasingInnerDiameterMm(500.0);
    calcHigh.setCasingLengthMm(1500.0);
    calcHigh.setMaterialGrade("SA-516-70");
    calcHigh.setCasingType(CompressorMechanicalDesign.CasingType.HORIZONTALLY_SPLIT);
    calcHigh.calculate();
    double allowableHigh = calcHigh.getAllowableStressMPa();

    assertTrue(allowableHigh < allowableLow,
        "Allowable stress should be reduced at high temperature");
  }
}
