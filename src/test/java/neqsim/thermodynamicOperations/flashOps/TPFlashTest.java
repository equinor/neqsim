/**
 * 
 */
package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 *
 */
class TPFlashTest {

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() throws Exception {
    testSystem = new neqsim.thermo.system.SystemPrEos(243.15, 300.0);
    testSystem.addComponent("nitrogen", 1.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 2.0);
    testSystem.addComponent("propane", 1.0);
    testSystem.addComponent("i-butane", 1.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("i-pentane", 1.0);
    testSystem.addComponent("n-pentane", 1.0);
    testSystem.addComponent("n-hexane", 1.0);
    testSystem.addComponent("nC10", 1.0);
    testSystem.addComponent("water", 10.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
  }

  void testRun() {
    testSystem.setMultiPhaseCheck(true);
    testSystem.setPressure(10.0, "bara");
    testSystem.setTemperature(25.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(-430041.49312169873, testSystem.getEnthalpy(), 1e-2);
  }

  @Test
  void testRun2() {
    testSystem.setMultiPhaseCheck(false);
    testSystem.setPressure(10.0, "bara");
    testSystem.setTemperature(25.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(-359394.2117634512, testSystem.getEnthalpy(), 1e-2);
  }

  @Test
  void testRun3() {
    testSystem.setMultiPhaseCheck(false);
    testSystem.setPressure(500.0, "bara");
    testSystem.setTemperature(15.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(-552568.2810227782, testSystem.getEnthalpy(), 1e-2);
  }

  // @Test
  void testRun4() {
    testSystem.setMultiPhaseCheck(true);
    testSystem.setPressure(500.0, "bara");
    testSystem.setTemperature(15.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(-936973.1969586421, testSystem.getEnthalpy(), 1e-2);
  }

  @Test
  void testRun5() {
    neqsim.thermo.system.SystemInterface testSystem5 =
        new neqsim.thermo.system.SystemUMRPRUMCEos(243.15, 300.0);
    testSystem5.addComponent("methane", 4.16683e-1);
    testSystem5.addComponent("ethane", 1.7522e-1);
    testSystem5.addComponent("n-pentane", 3.58009e-1);
    testSystem5.addComponent("nC16", 5.00888e-2);
    testSystem5.setMixingRule("classic");
    testSystem5.setMultiPhaseCheck(true);
    testSystem5.setPressure(90.03461693, "bara");
    testSystem5.setTemperature(293.15, "K");
    testSystem5.setTotalFlowRate(4.925e-07, "kg/sec");
    testOps = new ThermodynamicOperations(testSystem5);
    testOps.TPflash();
    testSystem5.initProperties();
    double beta = testSystem5.getBeta();
    assertEquals(6.272876522701802E-7, beta, 1e-5);
  }
}
