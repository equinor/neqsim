package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
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
    double expected = -359377.5331957406;
    double deviation = Math.abs((testSystem.getEnthalpy() - expected) / expected * 100);
    assertEquals(0.0, deviation, 0.5);
  }

  @Test
  void testRun3() {
    testSystem.setMultiPhaseCheck(false);
    testSystem.setPressure(500.0, "bara");
    testSystem.setTemperature(15.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    double expected = -552558.80195817;
    double deviation = Math.abs((testSystem.getEnthalpy() - expected) / expected * 100);
    assertEquals(0.0, deviation, 0.5);
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

  @Test
  void testRun6() {
    neqsim.thermo.system.SystemInterface testSystem5 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(243.15, 300.0);
    testSystem5.addComponent("water", 50.0);
    testSystem5.addComponent("nitrogen", 0.58419764);
    testSystem5.addComponent("CO2", 0.070499);
    testSystem5.addComponent("methane", 64.04974);
    testSystem5.addComponent("ethane", 8.148467);
    testSystem5.addComponent("propane", 4.985780239);
    testSystem5.addComponent("i-butane", 0.966896117);
    testSystem5.addComponent("n-butane", 1.923792362);
    testSystem5.addComponent("i-pentane", 0.725197077);
    testSystem5.addComponent("n-pentane", 0.856096566);
    testSystem5.addComponent("n-hexane", 0.968696);
    testSystem5.addTBPfraction("C7", 1.357195, 98.318 / 1000, 736.994 / 1000);
    testSystem5.addTBPfraction("C8", 1.507094, 112.273 / 1000, 758.73 / 1000);
    testSystem5.addTBPfraction("C9", 1.216195, 126.266 / 1000, 775.35 / 1000);
    testSystem5.addTBPfraction("C11", 1.624694, 146.891006469727 / 1000, 0.794530808925629);
    testSystem5.addTBPfraction("C13", 1.4131942987442, 174.875 / 1000, 0.814617037773132);
    testSystem5.addTBPfraction("C15", 1.22939503192902, 202.839004516602 / 1000, 0.830620348453522);
    testSystem5.addTBPfraction("C18", 1.55159378051758, 237.324005126953 / 1000, 0.846814215183258);
    testSystem5.addTBPfraction("C20", 0.868496537208557, 272.686004638672 / 1000,
        0.860718548297882);
    testSystem5.addTBPfraction("C23", 1.0966956615448, 307.141998291016 / 1000, 0.872339725494385);
    testSystem5.addTBPfraction("C29", 1.61579358577728, 367.554992675781 / 1000, 0.889698147773743);
    testSystem5.addTBPfraction("C30", 3.24028706550598, 594.625 / 1000, 0.935410261154175);

    testSystem5.setMixingRule(10);
    testSystem5.setMultiPhaseCheck(true);
    testSystem5.setPressure(300.0, "bara");
    testSystem5.setTemperature(343.15, "K");
    testOps = new ThermodynamicOperations(testSystem5);
    testOps.TPflash();
    testSystem5.initProperties();
    assertEquals(0.27697023508525664, testSystem5.getBeta(), 1e-6);
    assertEquals(3, testSystem5.getNumberOfPhases());
  }
}
