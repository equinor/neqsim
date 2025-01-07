package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * @author ESOL
 */
class WaxFlashTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WaxFlashTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    NeqSimDataBase.setCreateTemporaryTables(true);
    testSystem = new SystemSrkEos(273.0 + 30, 50.0);
    testSystem.addComponent("CO2", 0.018);
    testSystem.addComponent("nitrogen", 0.333);
    testSystem.addComponent("methane", 96.702);
    testSystem.addComponent("ethane", 1.773);
    testSystem.addComponent("propane", 0.496);
    testSystem.addComponent("i-butane", 0.099);
    testSystem.addComponent("n-butane", 0.115);
    testSystem.addComponent("i-pentane", 0.004);
    testSystem.addComponent("n-pentane", 0.024);
    testSystem.addComponent("n-heptane", 0.324);
    testSystem.addPlusFraction("C9", 0.095, 207.0 / 1000.0, 0.8331);
    testSystem.getCharacterization().characterisePlusFraction();
    testSystem.getWaxModel().addTBPWax();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.addSolidComplexPhase("wax");
    testSystem.setMultiphaseWaxCheck(true);
    NeqSimDataBase.setCreateTemporaryTables(false);
  }

  /**
   * Test method for {@link neqsim.thermodynamicoperations.flashops.saturationops.WATcalc#run()}.
   */
  @Test
  void testRun() {
    testOps = new ThermodynamicOperations(testSystem);
    double waxT = 0.0;
    try {
      testOps.calcWAT();
      waxT = testSystem.getTemperature("C");
      testSystem.setTemperature(waxT - 10.0, "C");
      testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    double waxVolumeFrac = 0;
    if (testSystem.hasPhaseType("wax")) {
      waxVolumeFrac = testSystem.getWtFraction(testSystem.getPhaseIndex("wax"));
    }
    assertEquals(30.323689017118397, waxT, 0.001);
    assertEquals(3.236072552269342E-4, waxVolumeFrac, 0.0001);
  }
}
