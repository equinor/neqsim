package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class dewPointPressureFlashTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(dewPointPressureFlashTest.class);

  @Test
  void testRun() {
    SystemSrkEos fluid0_HC = new SystemSrkEos();
    fluid0_HC.addComponent("methane", 0.7);
    fluid0_HC.addComponent("ethane", 0.1);
    fluid0_HC.addComponent("propane", 0.1);
    fluid0_HC.addComponent("n-butane", 0.1);
    fluid0_HC.setMixingRule("classic");

    fluid0_HC.setPressure(10.0, "bara");
    fluid0_HC.setTemperature(0.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid0_HC);
    try {
      ops.dewPointPressureFlash();
    } catch (Exception e) {
      logger.error(e.getMessage());;
    }
    assertEquals(9.332383561, fluid0_HC.getPressure(), 1e-2);
  }
}
