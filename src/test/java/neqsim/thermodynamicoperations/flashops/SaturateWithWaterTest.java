package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SaturateWithWaterTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SaturateWithWaterTest.class);

  @Test
  void testRun() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 70.0, 150.0);

    testSystem.addComponent("methane", 75.0);
    testSystem.addComponent("ethane", 7.5);
    testSystem.addComponent("propane", 4.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("i-butane", 0.6);
    testSystem.addComponent("n-hexane", 0.3);
    testSystem.addPlusFraction("C6", 1.3, 100.3 / 1000.0, 0.8232);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.saturateWithWater();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    assertEquals(0.0029033655101811814, testSystem.getComponent("water").getz(), 1e-5);
  }

  @Test
  void testRun2() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 70.0, 150.0);

    testSystem.addComponent("methane", 75.0);
    testSystem.addComponent("ethane", 7.5);
    testSystem.addComponent("propane", 4.0);
    testSystem.setMixingRule(10);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.saturateWithWater();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    assertEquals(0.002891748277007660, testSystem.getComponent("water").getz(), 1e-5);
  }

  @Test
  void testRun3() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 20.0, 150.0);

    testSystem.addComponent("methane", 2.0);
    testSystem.addComponent("n-heptane", 75.0);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.saturateWithWater();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    assertEquals(2.8465024974919816E-4, testSystem.getComponent("water").getz(), 1e-5);
  }

  @Test
  void testRun4() {
    SystemInterface testSystem = new SystemPrEos(273.15 + 20.0, 150.0);

    testSystem.addComponent("methane", 2.0);
    testSystem.addComponent("n-heptane", 75.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.saturateWithWater();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    assertEquals(2.4301370485671443E-4, testSystem.getComponent("water").getz(), 1e-5);
  }
}
