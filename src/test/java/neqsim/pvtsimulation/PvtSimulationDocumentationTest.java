package neqsim.pvtsimulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest.SeparatorStageResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies the executable PVT landing-page example. */
class PvtSimulationDocumentationTest extends NeqSimTest {
  @Test
  void testMultiStageSeparatorQuickStart() {
    SystemInterface reservoirFluid = new SystemSrkEos(373.15, 300.0);
    reservoirFluid.addComponent("nitrogen", 0.5);
    reservoirFluid.addComponent("CO2", 2.0);
    reservoirFluid.addComponent("methane", 45.0);
    reservoirFluid.addComponent("ethane", 8.0);
    reservoirFluid.addComponent("propane", 5.0);
    reservoirFluid.addComponent("i-butane", 1.5);
    reservoirFluid.addComponent("n-butane", 2.5);
    reservoirFluid.addComponent("i-pentane", 1.0);
    reservoirFluid.addComponent("n-pentane", 1.5);
    reservoirFluid.addComponent("n-hexane", 3.0);
    reservoirFluid.addComponent("n-heptane", 30.0);
    reservoirFluid.setMixingRule("classic");
    reservoirFluid.setMultiPhaseCheck(true);

    MultiStageSeparatorTest separatorTest = new MultiStageSeparatorTest(reservoirFluid);
    separatorTest.setReservoirConditions(300.0, 100.0);
    separatorTest.addSeparatorStage(50.0, 40.0, "HP separator");
    separatorTest.addSeparatorStage(10.0, 30.0, "LP separator");
    separatorTest.addStockTankStage();
    separatorTest.run();

    List<SeparatorStageResult> stages = separatorTest.getStageResults();
    assertEquals(3, stages.size());
    assertEquals("HP separator", stages.get(0).getStageName());
    assertEquals(50.0, stages.get(0).getPressure(), 1.0e-12);
    assertEquals(40.0, stages.get(0).getTemperature(), 1.0e-12);
    assertEquals(separatorTest.getTotalGOR(), stages.get(2).getCumulativeGOR(), 0.1);
    assertTrue(Double.isFinite(separatorTest.getTotalGOR()));
    assertTrue(separatorTest.getTotalGOR() > 0.0);
    assertTrue(Double.isFinite(separatorTest.getBo()));
    assertTrue(separatorTest.getBo() > 1.0);
    assertTrue(separatorTest.getStockTankOilDensity() > 500.0);
    assertTrue(separatorTest.getStockTankOilDensity() < 1200.0);
  }
}
