package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 *
 */
class PHFlashTest {

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() throws Exception {
    testSystem = new neqsim.thermo.system.SystemPrEos(243.15, 300.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 0.0);
    testSystem.addComponent("propane", 0.0);
    testSystem.addComponent("i-butane", 0.0);
    testSystem.addComponent("n-butane", 0.0);
    testSystem.addComponent("i-pentane", 0.0);
    testSystem.addComponent("n-pentane", 0.0);
    testSystem.addComponent("n-hexane", 0.0);
    testSystem.addComponent("nitrogen", 10.0);
    testSystem.setMixingRule("classic");
  }

  /**
   * Test method for {@link neqsim.thermodynamicOperations.flashOps.PHFlash#run()}.
   */
  @Test
  void testRun() {
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    double enthalpy = testSystem.getEnthalpy();
    testSystem.setPressure(4.0);
    testOps.PHflash(enthalpy);
    assertEquals(enthalpy, testSystem.getEnthalpy(), 1e-2);
  }

}
