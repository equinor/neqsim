package neqsim.processsimulation.util.report;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FluidReportTest {
  @Test
  void testWrite() {
    SystemSrkEos testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("n-heptane", 100.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    String report = testSystem.toJson();
    // System.out.println(report);
    neqsim.util.unit.Units.activateFieldUnits();
    report = testSystem.toJson();
    // System.out.println(report);
  }
}
