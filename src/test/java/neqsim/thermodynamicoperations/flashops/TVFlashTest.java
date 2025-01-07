package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test TVFlash.
 */
class TVFlashTest {
  /** Logger object for class. */
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
  void setUp() {
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
        logger.info("error iterations " + i);
        logger.error(ex.getMessage(), ex);
      }
    }
    assertEquals(235310.3670621656, testSystem.getEnthalpy(), 1.0);
  }

  @Test
  void testLiquidThermalExpansion() {
    testSystem = new neqsim.thermo.system.SystemPrEos(273.15 - 60.0, 40.0);
    testSystem.addComponent("CO2", 1.0);
    testSystem.addComponent("methane", 10.0);
    testSystem.addComponent("ethane", 20.0);
    testSystem.addComponent("propane", 20.0);
    testSystem.addComponent("n-butane", 20.0);
    testSystem.setMixingRule("classic");

    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double isothermalCompressibility = testSystem.getPhase(0).getIsothermalCompressibility();
    double isobaricThermalExpansivity = testSystem.getPhase(0).getIsobaricThermalExpansivity();

    assertEquals(2.1655529052373845E-4, isothermalCompressibility, 1e-6);
    assertEquals(0.0019761208438481767, isobaricThermalExpansivity, 1e-6);

    double volume = testSystem.getVolume("m3");
    testSystem.setTemperature(20.0, "C");
    testOps.TVflash(volume, "m3");
    assertEquals(747.12062, testSystem.getPressure("bara"), 1.0);
  }
}
