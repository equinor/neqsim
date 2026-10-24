package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * Checks wax onset and a wax-bearing flash for a characterized gas condensate.
 *
 * @author ESOL
 * @version 1.0
 */
class WaxFlashTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * Configures the characterized wax-forming fluid.
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
   *
   * @throws Exception if the wax appearance calculation fails
   */
  @Test
  void testRun() throws Exception {
    testOps = new ThermodynamicOperations(testSystem);
    testOps.calcWAT();
    double waxT = testSystem.getTemperature("C");
    SystemInterface warm = testSystem.clone();
    warm.setTemperature(waxT + 0.01, "C");
    new ThermodynamicOperations(warm).TPflash();
    assertFalse(warm.hasPhaseType("wax"));
    testSystem.setTemperature(waxT - 10.0, "C");
    testOps.TPflash();
    assertTrue(testSystem.hasPhaseType("wax"));
    // getWtFraction expects a logical phase number, whereas getPhaseIndex returns a storage slot.
    double waxMassFraction = testSystem.getPhaseFraction("wax", "mass");
    assertTrue(waxMassFraction > 0.0 && waxMassFraction < 1.0);
    // Updated internal reference for the corrected Pedersen distribution, with unchanged tolerance.
    assertEquals(26.198953247070335, waxT, 0.001);
    assertEquals(2.9218275199921067e-4, waxMassFraction, 1e-8);
  }
}
