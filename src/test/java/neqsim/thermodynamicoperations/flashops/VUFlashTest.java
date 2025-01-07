package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test VUFlash.
 */
class VUFlashTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(VUFlashTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static neqsim.thermo.system.SystemInterface testSystem2 = null;
  static ThermodynamicOperations testOps = null;

  @Test
  void testVUflash() {
    testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(293.15, 23.5);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("ethane", 0.01);
    testSystem.addComponent("n-pentane", 0.01);
    testSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double volume = testSystem.getVolume("m3");
    double internalenergy = testSystem.getInternalEnergy("J");

    testOps.VUflash(volume * 1.1, internalenergy, "m3", "J");

    assertEquals(21.387, testSystem.getPressure(), 0.01);
  }
}
