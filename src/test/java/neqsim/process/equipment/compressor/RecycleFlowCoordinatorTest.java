package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemSrkEos;

class RecycleFlowCoordinatorTest {
  @Test
  void testRequestedRecycleIsBoundedAndMassConserving() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream discharge = new Stream("compressor discharge", fluid);
    discharge.setFlowRate(1000.0, "kg/hr");
    discharge.run();

    Splitter splitter = new Splitter("discharge splitter", discharge, 2);
    splitter.setSplitFactors(new double[] { 0.9, 0.1 });
    splitter.run();
    ThrottlingValve recycleValve = new ThrottlingValve("recycle valve", splitter.getSplitStream(1));
    recycleValve.getOutletStream().setFlowRate(800.0, "kg/hr");

    RecycleFlowCoordinator coordinator = new RecycleFlowCoordinator("recycle coordinator", discharge, splitter,
        recycleValve);
    coordinator.setMaximumRecycleFraction(0.5);
    coordinator.runTransient(1.0, UUID.randomUUID());

    assertEquals(500.0, coordinator.getLastRecycleFlow(), 1.0e-10);
    assertEquals(500.0, coordinator.getLastMainFlow(), 1.0e-10);
    assertEquals(1000.0,
        splitter.getSplitStream(0).getFlowRate("kg/hr") + splitter.getSplitStream(1).getFlowRate("kg/hr"), 1.0e-10);
    assertEquals(500.0, recycleValve.getOutletStream().getFlowRate("kg/hr"), 1.0e-10);
    assertEquals(1.0, coordinator.getTime(), 0.0);
  }
}
