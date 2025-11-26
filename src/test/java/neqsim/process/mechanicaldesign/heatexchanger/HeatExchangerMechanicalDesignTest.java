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
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

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
    double minArea = design.getSizingResults().stream().mapToDouble(HeatExchangerSizingResult::getRequiredArea)
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
}
