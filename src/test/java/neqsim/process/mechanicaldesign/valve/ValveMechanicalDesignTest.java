package neqsim.process.mechanicaldesign.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Tests for valve mechanical design calculations. */
public class ValveMechanicalDesignTest {
  @Test
  void testCalcDesign() {
    ThrottlingValve valve = createDesignedGasValve();
    assertTrue(valve.getMechanicalDesign().getWeightTotal() > 0.0);
    assertEquals(valve.getMechanicalDesign().getRequiredCv(), valve.getMechanicalDesign().getValveCvMax(), 1.0e-12);
    double requiredCv = valve.getMechanicalDesign().getRequiredCv();
    valve.getMechanicalDesign().setMaxDesignCv(requiredCv * 2.0);
    assertEquals(0.5, valve.getMechanicalDesign().getDesignUtilization().get("design Cv"), 1.0e-12);
  }

  @Test
  void testSelectsSmallestFeasibleVendorTrimAndReportsUtilization() {
    ThrottlingValve valve = createDesignedGasValve();
    ValveMechanicalDesign design = valve.getMechanicalDesign();
    double requiredCv = design.getRequiredCv();

    design.addAvailableTrimOption("TC-40", 40.0, requiredCv * 0.80, "tungsten carbide", "metallic brickstopper");
    design.addAvailableTrimOption("TC-60", 60.0, requiredCv * 1.25, "tungsten carbide", "metallic brickstopper");
    design.addAvailableTrimOption("TC-100", 100.0, requiredCv * 2.00, "tungsten carbide", "metallic brickstopper");

    ValveTrimSizingResult result = design.getTrimSizingResult();
    assertTrue(result.isFeasible());
    assertEquals("TC-60", result.getSelectedTrimOption().getIdentifier());
    assertEquals(60.0, result.getSelectedTrimOption().getRelativeTrimSizePercent(), 1.0e-12);
    assertEquals(0.80, result.getUtilization(), 1.0e-12);
    assertEquals(requiredCv * 0.25, result.getCapacityMarginCv(), 1.0e-12);
    assertEquals(result.getUtilization(), design.getDesignUtilization().get("design Cv"), 1.0e-12);
    assertTrue(valve.applyMechanicalDesignCapacityConstraints() >= 1);
    assertEquals(result.getUtilization(), valve.getMaxUtilization(), 1.0e-12);
  }

  @Test
  void testTrimSelectionUsesDeterministicTieBreak() {
    ThrottlingValve valve = createDesignedGasValve();
    ValveMechanicalDesign design = valve.getMechanicalDesign();
    double feasibleCv = design.getRequiredCv() * 1.25;

    design.addAvailableTrimOption("TC-75", 75.0, feasibleCv);
    design.addAvailableTrimOption("TC-50-B", 50.0, feasibleCv);
    design.addAvailableTrimOption("TC-50-A", 50.0, feasibleCv);

    assertEquals("TC-50-A", design.getSelectedTrimOption().getIdentifier());
  }

  @Test
  void testTrimSelectionPrioritizesRelativeSizeForNonMonotoneVendorCatalog() {
    ThrottlingValve valve = createDesignedGasValve();
    ValveMechanicalDesign design = valve.getMechanicalDesign();
    double requiredCv = design.getRequiredCv();

    design.addAvailableTrimOption("REL-70", 70.0, requiredCv * 1.25);
    design.addAvailableTrimOption("REL-50", 50.0, requiredCv * 2.00);

    assertEquals("REL-50", design.getSelectedTrimOption().getIdentifier());
    assertEquals(requiredCv * 2.00, design.getMaximumAvailableTrimCv(), 1.0e-12);
  }

  @Test
  void testInfeasibleTrimCatalogUsesLargestOptionAsLimitingCapacity() {
    ThrottlingValve valve = createDesignedGasValve();
    ValveMechanicalDesign design = valve.getMechanicalDesign();
    double requiredCv = design.getRequiredCv();

    design.addAvailableTrimOption("TC-40", 40.0, requiredCv * 0.40);
    design.addAvailableTrimOption("TC-80", 80.0, requiredCv * 0.80);

    ValveTrimSizingResult result = design.getTrimSizingResult();
    assertFalse(result.isFeasible());
    assertEquals(ValveTrimSizingResult.Status.NO_FEASIBLE_TRIM, result.getStatus());
    assertNull(result.getSelectedTrimOption());
    assertEquals("TC-80", result.getLimitingTrimOption().getIdentifier());
    assertEquals(1.25, result.getUtilization(), 1.0e-12);
    assertTrue(result.getCapacityMarginCv() < 0.0);
    assertEquals(1.25, design.getDesignUtilization().get("design Cv"), 1.0e-12);
  }

  @Test
  void testResponseSeparatesRequiredAndAvailableTrimCv() {
    ThrottlingValve valve = createDesignedGasValve();
    ValveMechanicalDesign design = valve.getMechanicalDesign();
    double requiredCv = design.getRequiredCv();
    design.setMaximumAllowedTrimUtilization(0.80);
    design.addAvailableTrimOption("TC-BS-75", 75.0, requiredCv / 0.75, "tungsten carbide",
        "non-collapsible metallic brickstopper");

    ValveMechanicalDesignResponse response = design.getResponse();
    assertEquals(requiredCv, response.getCvRequired(), 1.0e-12);
    assertEquals("FEASIBLE", response.getTrimAssessmentStatus());
    assertEquals("TC-BS-75", response.getSelectedTrimIdentifier());
    assertEquals(requiredCv / 0.75, response.getSelectedTrimMaximumCv(), 1.0e-12);
    assertEquals(0.75, response.getTrimCvUtilization(), 1.0e-12);
    assertEquals("tungsten carbide", response.getTrimMaterial());
    assertEquals("non-collapsible metallic brickstopper", response.getTrimConstruction());
    assertTrue(response.isTrimFeasible());
    assertTrue(design.toJson().contains("selectedTrimMaximumCv"));
  }

  @Test
  void testExplicitRequiredCvAndUtilizationReserve() {
    ThrottlingValve valve = createDesignedGasValve();
    ValveMechanicalDesign design = valve.getMechanicalDesign();
    design.setMaximumAllowedTrimUtilization(0.80);
    design.addAvailableTrimOption("REL-50", 50.0, 50.0);
    design.addAvailableTrimOption("REL-75", 75.0, 75.0);

    ValveTrimSizingResult result = design.assessTrimOptionsForRequiredCv(45.0);

    assertTrue(result.isFeasible());
    assertEquals("REL-75", result.getSelectedTrimOption().getIdentifier());
    assertEquals(0.60, result.getUtilization(), 1.0e-12);
  }

  @Test
  void testAutoSizePreservesCatalogCapacityAsDesignLimit() {
    ThrottlingValve valve = createDesignedGasValve();
    ValveMechanicalDesign design = valve.getMechanicalDesign();
    double trimMaximumCv = design.getRequiredCv() * 20.0;
    design.addAvailableTrimOption("AUTO-TC", 100.0, trimMaximumCv, "tungsten carbide", "metallic brickstopper");

    valve.autoSize(1.0, 50.0);

    assertTrue(design.getTrimSizingResult().isFeasible());
    assertEquals("AUTO-TC", design.getSelectedTrimOption().getIdentifier());
    assertEquals(trimMaximumCv, design.getMaxDesignCv(), 1.0e-12);
  }

  @Test
  void testUnrunValveResponseDoesNotSerializeNonFiniteRequiredCv() {
    ThrottlingValve valve = new ThrottlingValve("unrun valve");

    ValveMechanicalDesignResponse response = valve.getMechanicalDesign().getResponse();

    assertEquals(0.0, response.getCvRequired(), 1.0e-12);
    assertTrue(response.toJson().contains("NOT_EVALUATED"));
  }

  private ThrottlingValve createDesignedGasValve() {
    SystemInterface gas = new SystemSrkEos(300.0, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(10.0, "kg/hr");

    ThrottlingValve valve = new ThrottlingValve("valve", inlet);
    valve.setOutletPressure(5.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(valve);
    ps.run();

    valve.initMechanicalDesign();
    valve.getMechanicalDesign().calcDesign();
    return valve;
  }
}
