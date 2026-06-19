package neqsim.process.util.report;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FluidReportTest {
  private static final Logger logger = LogManager.getLogger(FluidReportTest.class);

  @Test
  void testWrite() {
    SystemSrkEos testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("n-heptane", 100.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    testSystem.toJson();
    // logger.info(report);
    neqsim.util.unit.Units.activateFieldUnits();
    testSystem.toJson();
    // logger.info(report);
  }
}
