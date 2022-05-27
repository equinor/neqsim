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


  @Test
  void testRun() {
    testSystem.setMultiPhaseCheck(true);
    testSystem.setPressure(10.0, "bara");
    testSystem.setTemperature(25.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    double enthalpy = testSystem.getEnthalpy();
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
    double enthalpy = testSystem.getEnthalpy();
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
    double enthalpy = testSystem.getEnthalpy();
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
    double enthalpy = testSystem.getEnthalpy();
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
  
  @Test
  void testRun6() {
    neqsim.thermo.system.SystemInterface testSystem5 =
        new neqsim.thermo.system.SystemUMRPRUMCEos(293.15, 89.03471693706786);
    testSystem5.addComponent("methane", 8.51863E-1);
    testSystem5.addComponent("ethane", 1.25248E-1);
    testSystem5.addComponent("n-pentane", 2.28881E-2);
    testSystem5.addComponent("nC16", 2.57656E-7);
    testSystem5.setMixingRule("classic");
    testSystem5.setPressure(89.03471693706786, "bara");
    testSystem5.setTemperature(293.15, "K");
    testSystem5.setTotalFlowRate(1.3025, "kg/sec");
    testOps = new ThermodynamicOperations(testSystem5);
    testOps.TPflash();
   // testSystem5.display();
    
    neqsim.thermo.system.SystemInterface testSystem6 = testSystem5.phaseToSystem("oil");
    testSystem6.setTotalFlowRate(1.2961e-09, "kg/sec");
    ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem6);
    testOps2.TPflash();
    testSystem6.initProperties();
    testSystem6.display();
    double beta = testSystem6.getBeta();
    assertEquals(1.0, beta, 1e-5);
  }
}
