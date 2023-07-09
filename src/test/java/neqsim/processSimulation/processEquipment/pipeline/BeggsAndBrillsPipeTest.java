package neqsim.processSimulation.processEquipment.pipeline;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class BeggsAndBrillsPipeTest {
  @Test
  public void testMain() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15), 1.01325);

    testSystem.addComponent("nC10", 50, "MSm^3/day");
    testSystem.setMixingRule(2);
    testSystem.init(0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    //System.out.println("Oil flow rate m3/hr: " + testSystem.getPhase("oil").getFlowRate("m3/hr"));
    System.out.println("Total flow rate m3/hr: " + testSystem.getFlowRate("m3/hr"));

  }
}