package neqsim.processSimulation.util.report;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

public class FluidCompinentReportTest {

  @Test
  void testWrite() {

    SystemSrkEos testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("n-heptane", 100.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);

    String report = testSystem.toCompJson();
    // System.out.println(report);
    neqsim.util.unit.Units.activateFieldUnits();
    report = testSystem.toCompJson();
    // System.out.println(report);

  }
}
