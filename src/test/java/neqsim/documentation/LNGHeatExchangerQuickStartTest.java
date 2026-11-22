package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.LNGHeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executes the complete Java quickstart in docs/process/equipment/LNGHeatExchanger.md. */
class LNGHeatExchangerQuickStartTest {

  @Test
  void testDocumentedQuickStartRunsAndReturnsBoundedDiagnostics() {
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    hotFluid.addComponent("methane", 0.90);
    hotFluid.addComponent("ethane", 0.05);
    hotFluid.addComponent("propane", 0.03);
    hotFluid.addComponent("nitrogen", 0.02);
    hotFluid.setMixingRule("classic");

    SystemInterface coldFluid = new SystemSrkEos(273.15 - 33.0, 3.0);
    coldFluid.addComponent("methane", 0.40);
    coldFluid.addComponent("ethane", 0.30);
    coldFluid.addComponent("propane", 0.30);
    coldFluid.setMixingRule("classic");

    Stream hotStream = new Stream("NG Feed", hotFluid);
    hotStream.setFlowRate(100000.0, "kg/hr");

    Stream coldStream = new Stream("MR Return", coldFluid);
    coldStream.setFlowRate(150000.0, "kg/hr");

    LNGHeatExchanger mche = new LNGHeatExchanger("MCHE");
    mche.addInStream(hotStream);
    mche.addInStream(coldStream);
    mche.setNumberOfZones(15);
    mche.setStreamPressureDrop(0, 1.5);
    mche.setStreamPressureDrop(1, 0.3);

    ProcessSystem process = new ProcessSystem();
    process.add(hotStream);
    process.add(coldStream);
    process.add(mche);
    process.run();

    double mitaC = mche.getMITA();
    double secondLawEfficiency = mche.getSecondLawEfficiency();

    assertEquals(1.5, mche.getStreamPressureDrop(0), 1.0e-12);
    assertEquals(0.3, mche.getStreamPressureDrop(1), 1.0e-12);
    assertTrue(Double.isFinite(mitaC));
    assertTrue(mitaC >= 0.0);
    assertTrue(Double.isFinite(secondLawEfficiency));
    assertTrue(secondLawEfficiency > 0.0);
    assertTrue(secondLawEfficiency <= 1.0);
  }
}
