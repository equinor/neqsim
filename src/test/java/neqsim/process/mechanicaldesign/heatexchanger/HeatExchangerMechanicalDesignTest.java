package neqsim.process.mechanicaldesign.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Comparator;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign.SelectionCriterion;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for HeatExchanger mechanical design calculations.
 */
public class HeatExchangerMechanicalDesignTest {
  @Test
  void testCalcDesign() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();
    assertNotNull(design.getSelectedSizingResult());
    assertTrue(design.getWeightTotal() > 0.0);
    assertFalse(design.getSizingResults().isEmpty());
    assertNotNull(design.getSizingSummary());
  }

  @Test
  void testManualTypeSelection() {
    HeatExchanger hx = createHeatExchanger(800.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.setCandidateTypes(HeatExchangerType.SHELL_AND_TUBE, HeatExchangerType.PLATE_AND_FRAME);
    design.setManualSelection(HeatExchangerType.PLATE_AND_FRAME);
    design.calcDesign();

    assertEquals(HeatExchangerType.PLATE_AND_FRAME, design.getSelectedType());
    HeatExchangerSizingResult selected = design.getSelectedSizingResult();
    assertNotNull(selected);
    assertTrue(selected.getRequiredArea() > 0.0);
    assertTrue(selected.getTubeCount() > 0);

    Optional<HeatExchangerSizingResult> shellResult = design.getSizingResults().stream()
        .filter(result -> result.getType() == HeatExchangerType.SHELL_AND_TUBE).findFirst();
    assertTrue(shellResult.isPresent());
    assertTrue(shellResult.get().getTubeCount() > 0);
  }

  @Test
  void testAutomaticSelectionByCriterion() {
    HeatExchanger hx = createHeatExchanger(1200.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.setCandidateTypes(HeatExchangerType.SHELL_AND_TUBE, HeatExchangerType.PLATE_AND_FRAME,
        HeatExchangerType.AIR_COOLER);

    design.setSelectionCriterion(SelectionCriterion.MIN_AREA);
    design.calcDesign();

    HeatExchangerSizingResult selected = design.getSelectedSizingResult();
    assertNotNull(selected);
    double minArea =
        design.getSizingResults().stream().mapToDouble(HeatExchangerSizingResult::getRequiredArea)
            .min().orElseThrow(() -> new IllegalStateException("No sizing results available"));
    assertEquals(minArea, selected.getRequiredArea(), 1e-6);

    // Switch criterion to evaluate another automatic decision
    design.setSelectionCriterion(SelectionCriterion.MIN_PRESSURE_DROP);
    design.setManualSelection(null);
    design.calcDesign();
    HeatExchangerType minPressureType = design.getSizingResults().stream()
        .min(Comparator.comparingDouble(HeatExchangerSizingResult::getEstimatedPressureDrop))
        .map(HeatExchangerSizingResult::getType)
        .orElseThrow(() -> new IllegalStateException("No sizing result available"));
    assertEquals(minPressureType, design.getSelectedType());

    Optional<HeatExchangerSizingResult> airCooler = design.getSizingResults().stream()
        .filter(result -> result.getType() == HeatExchangerType.AIR_COOLER).findFirst();
    assertTrue(airCooler.isPresent());
    assertTrue(airCooler.get().getFinSurfaceArea() > 0.0);
  }

  private HeatExchanger createHeatExchanger(double uaValue) {
    SystemInterface system1 = new SystemSrkEos(273.15 + 60.0, 20.0);
    system1.addComponent("methane", 120.0);
    system1.addComponent("ethane", 120.0);
    system1.addComponent("n-heptane", 3.0);
    system1.createDatabase(true);
    system1.setMixingRule(2);
    ThermodynamicOperations ops1 = new ThermodynamicOperations(system1);
    ops1.TPflash();

    Stream hot = new Stream("hot", system1);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(1000.0, "kg/hr");

    Stream cold = new Stream("cold", system1.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(310.0, "kg/hr");

    HeatExchanger hx = new HeatExchanger("hx", hot, cold);
    hx.setUAvalue(uaValue);
    hx.setThermalEffectiveness(0.75);

    ProcessSystem ps = new ProcessSystem();
    ps.add(hot);
    ps.add(cold);
    ps.add(hx);
    ps.run();

    return hx;
  }

  // ============================================================================
  // Process Design Parameter Tests
  // ============================================================================

  @Test
  void testFoulingResistances() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    double shellFouling = design.getFoulingResistanceShellHC();
    double tubeFouling = design.getFoulingResistanceTubeHC();

    assertTrue(shellFouling > 0, "Shell fouling resistance should be positive");
    assertTrue(tubeFouling > 0, "Tube fouling resistance should be positive");
    System.out.println("Shell fouling resistance (HC): " + shellFouling + " m²K/W");
    System.out.println("Tube fouling resistance (HC): " + tubeFouling + " m²K/W");
  }

  @Test
  void testTemaClass() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    String temaClass = design.getTemaClass();
    assertNotNull(temaClass, "TEMA class should not be null");
    assertTrue(temaClass.equals("R") || temaClass.equals("C") || temaClass.equals("B"),
        "TEMA class should be R, C, or B");
    System.out.println("TEMA class: " + temaClass);
  }

  @Test
  void testVelocityLimits() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    double maxTubeVel = design.getMaxTubeVelocity();
    double maxShellVel = design.getMaxShellVelocity();

    assertTrue(maxTubeVel > 0, "Max tube velocity should be positive");
    assertTrue(maxShellVel > 0, "Max shell velocity should be positive");
    System.out.println("Max tube velocity: " + maxTubeVel + " m/s");
    System.out.println("Max shell velocity: " + maxShellVel + " m/s");
  }

  @Test
  void testApproachTemperature() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    double minApproach = design.getMinApproachTemperatureC();
    assertTrue(minApproach > 0, "Min approach temperature should be positive");
    assertTrue(minApproach <= 30, "Min approach temperature should be reasonable (<= 30 C)");
    System.out.println("Min approach temperature: " + minApproach + " C");
  }

  // ============================================================================
  // Validation Method Tests
  // ============================================================================

  @Test
  void testValidateTubeVelocity() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    double maxVel = design.getMaxTubeVelocity();

    // Test within limit
    assertTrue(design.validateTubeVelocity(maxVel * 0.8), "Velocity 80% of max should pass");

    // Test above limit
    assertFalse(design.validateTubeVelocity(maxVel * 1.2), "Velocity 120% of max should fail");
  }

  @Test
  void testValidateShellVelocity() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    double maxVel = design.getMaxShellVelocity();

    // Test within limit
    assertTrue(design.validateShellVelocity(maxVel * 0.5), "Velocity 50% of max should pass");

    // Test above limit
    assertFalse(design.validateShellVelocity(maxVel * 1.5), "Velocity 150% of max should fail");
  }

  @Test
  void testValidateApproachTemperature() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    double minApproach = design.getMinApproachTemperatureC();

    // Test with adequate approach
    assertTrue(design.validateApproachTemperature(minApproach * 2),
        "Approach 2x minimum should pass");

    // Test with inadequate approach
    assertFalse(design.validateApproachTemperature(minApproach * 0.5),
        "Approach 0.5x minimum should fail");
  }

  @Test
  void testComprehensiveValidation() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    HeatExchangerMechanicalDesign.HeatExchangerValidationResult result = design.validateDesign();

    assertNotNull(result, "Validation result should not be null");
    assertNotNull(result.getIssues(), "Issues list should not be null");

    System.out.println("Heat exchanger validation valid: " + result.isValid());
    if (!result.isValid()) {
      for (String issue : result.getIssues()) {
        System.out.println("  Issue: " + issue);
      }
    }
  }

  // ============================================================================
  // ASME VIII Design Tests
  // ============================================================================

  @Test
  void testShellAndTubeCalculatorWithASME() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.setShellMaterialGrade("SA-516-70");
    design.setTubeMaterialGrade("SA-179");
    design.calcDesign();

    ShellAndTubeDesignCalculator calc = design.getShellAndTubeCalculator();
    assertNotNull(calc, "Calculator should not be null after calcDesign");

    // MAWP should be positive
    assertTrue(calc.getMawpShellSide() > 0, "Shell MAWP should be positive");
    assertTrue(calc.getMawpTubeSide() > 0, "Tube MAWP should be positive");
    System.out.println("MAWP shell: " + calc.getMawpShellSide() + " bara");
    System.out.println("MAWP tube: " + calc.getMawpTubeSide() + " bara");

    // Hydro test pressure should exceed MAWP
    assertTrue(calc.getHydroTestPressureShell() > calc.getMawpShellSide(),
        "Hydro test pressure shell should exceed MAWP");
    assertTrue(calc.getHydroTestPressureTube() > calc.getMawpTubeSide(),
        "Hydro test pressure tube should exceed MAWP");
    System.out.println(
        "Hydro test pressure shell: " + calc.getHydroTestPressureShell() + " bara");

    // Tubesheet thickness from UHX should be positive
    assertTrue(calc.getTubesheetThicknessUHX() > 0,
        "Tubesheet thickness should be positive");
    System.out.println("Tubesheet thickness UHX: " + calc.getTubesheetThicknessUHX() + " mm");

    // Nozzle reinforcement check should have been performed
    assertTrue(calc.getNozzleReinforcementArea() >= 0,
        "Nozzle reinforcement area should be non-negative");
    System.out.println(
        "Nozzle reinforcement adequate: " + calc.isNozzleReinforcementAdequate());

    // Applied standards should contain ASME entries
    assertFalse(calc.getAppliedStandards().isEmpty(),
        "Applied standards should not be empty");
    System.out.println("Applied standards: " + calc.getAppliedStandards());
  }

  @Test
  void testMaterialGradeLookup() {
    HeatExchanger hx = createHeatExchanger(800.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.setShellMaterialGrade("SA-516-70");
    design.setTubeMaterialGrade("SA-213-TP316");
    design.calcDesign();

    ShellAndTubeDesignCalculator calc = design.getShellAndTubeCalculator();
    assertNotNull(calc);

    // Tube thermal conductivity for stainless steel should be much lower than CS
    double thermalCond = calc.getTubeThermalConductivity();
    assertTrue(thermalCond > 0, "Tube thermal conductivity should be positive");
    System.out.println("Tube thermal conductivity: " + thermalCond + " W/mK");
  }

  @Test
  void testJointEfficiency() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();

    // Full RT joint efficiency
    design.setShellJointEfficiency(1.0);
    design.calcDesign();
    double mawpFull = design.getShellAndTubeCalculator().getMawpShellSide();

    // Spot RT joint efficiency
    design.setShellJointEfficiency(0.85);
    design.calcDesign();
    double mawpSpot = design.getShellAndTubeCalculator().getMawpShellSide();

    // Higher joint efficiency should give higher MAWP
    assertTrue(mawpFull > mawpSpot,
        "Full RT MAWP should exceed spot RT MAWP");
    System.out.println(
        "MAWP full RT: " + mawpFull + ", MAWP spot RT: " + mawpSpot);
  }

  // ============================================================================
  // NACE / Sour Service Tests
  // ============================================================================

  @Test
  void testNACESourServiceBelow() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.setSourServiceAssessment(true);
    design.setH2sPartialPressure(0.001); // Below 0.003 bar threshold
    design.calcDesign();

    ShellAndTubeDesignCalculator calc = design.getShellAndTubeCalculator();
    assertFalse(calc.isSourServiceRequired(),
        "Sour service should not be required below threshold");
    assertTrue(calc.getNaceIssues().isEmpty(),
        "No NACE issues expected below threshold");
  }

  @Test
  void testNACESourServiceAbove() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.setSourServiceAssessment(true);
    design.setH2sPartialPressure(0.05); // Above threshold
    design.calcDesign();

    ShellAndTubeDesignCalculator calc = design.getShellAndTubeCalculator();
    assertTrue(calc.isSourServiceRequired(),
        "Sour service should be required above threshold");
    System.out.println("NACE issues: " + calc.getNaceIssues());
  }

  @Test
  void testCalculatorJsonOutput() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.setSourServiceAssessment(true);
    design.setH2sPartialPressure(0.01);
    design.calcDesign();

    ShellAndTubeDesignCalculator calc = design.getShellAndTubeCalculator();
    String json = calc.toJson();
    assertNotNull(json, "JSON output should not be null");
    assertTrue(json.contains("pressureDesign"), "JSON should contain pressure design section");
    assertTrue(json.contains("mawpShellSide_bara"), "JSON should contain MAWP");
    assertTrue(json.contains("appliedStandards"), "JSON should contain applied standards");
    assertTrue(json.contains("naceAssessment"), "JSON should contain NACE assessment");
    System.out.println("JSON length: " + json.length() + " chars");
  }

  @Test
  void testResponseIncludesNewFields() {
    HeatExchanger hx = createHeatExchanger(1000.0);
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.setShellMaterialGrade("SA-516-70");
    design.setTubeMaterialGrade("SA-179");
    design.calcDesign();

    HeatExchangerMechanicalDesignResponse response =
        new HeatExchangerMechanicalDesignResponse(design);
    assertEquals("SA-516-70", response.getShellMaterialGrade());
    assertEquals("SA-179", response.getTubeMaterialGrade());
    assertTrue(response.getMawpShellSide() > 0);
    assertTrue(response.getHydroTestPressureShell() > 0);
  }
}
