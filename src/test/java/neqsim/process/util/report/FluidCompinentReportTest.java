package neqsim.process.util.report;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

public class FluidCompinentReportTest {
  private static final Logger logger = LogManager.getLogger(FluidCompinentReportTest.class);

  @Test
  void testWrite() {
    SystemSrkEos testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("n-heptane", 100.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);

    String report = testSystem.toCompJson();
    // logger.info(report);
    neqsim.util.unit.Units.activateFieldUnits();
    report = testSystem.toCompJson();
    // logger.info(report);
  }
}
