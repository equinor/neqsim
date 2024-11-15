package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class TPgradientFlashTest {
  static Logger logger = LogManager.getLogger(TPgradientFlashTest.class);

  @Test
  void testRun() {
    SystemInterface testSystem = new SystemSrkEos(345, 80.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("hydrogen", 1.0);
    testSystem.addComponent("methane", 8.0);
    testSystem.addComponent("propane", 1.0);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    SystemInterface newSystem = null;
    try {
      try {
        newSystem = testOps.TPgradientFlash(1000, 355).phaseToSystem(0);
      } catch (Exception e) {
        e.printStackTrace();
      }
      // newSystem.prettyPrint();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    assertEquals(0.0987274603, newSystem.getComponent("hydrogen").getx(), 1e-2);
  }

}
