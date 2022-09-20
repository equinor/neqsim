package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * Test TVFlash.
 */
class TVFlashTest {
  static Logger logger = LogManager.getLogger(TVFlashTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static neqsim.thermo.system.SystemInterface testSystem2 = null;
  static ThermodynamicOperations testOps = null;

  /**
   * Sets up test system.
   *
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() throws Exception {
    testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(293.15, 0.1);
    testSystem.addComponent("methane", 0.0);
    testSystem.addComponent("ethane", 0.0);
    testSystem.addComponent("n-pentane", 9E-1);
    testSystem.addComponent("nC16", 1E-1);
    testSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    testSystem.setTotalFlowRate(1.0, "kg/sec");
    testOps.TPflash();
    testSystem.initProperties();

    testSystem2 = new neqsim.thermo.system.SystemUMRPRUMCEos(293.15, 0.1);
    testSystem2.addComponent("methane", 8.5E-1);
    testSystem2.addComponent("ethane", 1.5E-1);
    testSystem2.addComponent("n-pentane", 0);
    testSystem2.addComponent("nC16", 0);
    testSystem2.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem2);
    testOps.TPflash();
    testSystem2.initProperties();
    testSystem2.setTotalFlowRate(0.3, "kg/sec");
    testOps.TPflash();
    testSystem2.initProperties();
  }

  @Test
  void testTVflash() {
    double total_rig_volume = 0.998;

    for (int i = 0; i < 50; i++) {
      testSystem.addFluid(testSystem2);
      ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
      try {
        testOps.TVflash(total_rig_volume, "m3");
      } catch (Exception ex) {
        System.out.println("error iterations " + i);
        logger.error(ex.getMessage());
      }
    }
    assertEquals(235263.80103781424, testSystem.getEnthalpy(), 1e-2);
  }

}

