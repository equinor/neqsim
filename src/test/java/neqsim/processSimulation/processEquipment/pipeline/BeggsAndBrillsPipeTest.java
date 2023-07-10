package neqsim.processSimulation.processEquipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class BeggsAndBrillsPipeTest {
  @Test
  public void testFlowNoVolumeCorrection() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15), 1.01325);

    testSystem.addComponent("nC10", 50, "MSm^3/day");
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(false);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Assertions.assertEquals(testSystem.getPhase("oil").getFlowRate("m3/hr"),
        testSystem.getFlowRate("m3/hr"), 1e-4);
  }

  @Test
  public void testFlowVolumeCorrection() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15), 1.01325);

    testSystem.addComponent("nC10", 50, "MSm^3/day");
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Assertions.assertEquals(testSystem.getPhase("oil").getFlowRate("m3/hr"),
        testSystem.getFlowRate("m3/hr"), 1e-4);
  }

}
