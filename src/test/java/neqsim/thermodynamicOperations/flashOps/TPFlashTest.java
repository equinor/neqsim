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

  static neqsim.thermo.system.SystemInterface wellFluid = null;
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() throws Exception {
    wellFluid = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 30.0, 65.00);
    wellFluid.addComponent("oxygen", 0.0);
    wellFluid.addComponent("H2S", 0.00008);
    wellFluid.addComponent("nitrogen", 0.08);
    wellFluid.addComponent("CO2", 3.56);
    wellFluid.addComponent("methane", 87.36);
    wellFluid.addComponent("ethane", 4.02);
    wellFluid.addComponent("propane", 1.54);
    wellFluid.addComponent("i-butane", 0.2);
    wellFluid.addComponent("n-butane", 0.42);
    wellFluid.addComponent("i-pentane", 0.15);
    wellFluid.addComponent("n-pentane", 0.20);

    wellFluid.addTBPfraction("C6_Frigg", 0.24, 84.99 / 1000.0, 695.0 / 1000.0);
    wellFluid.addTBPfraction("C7_Frigg", 0.34, 97.87 / 1000.0, 718.0 / 1000.0);
    wellFluid.addTBPfraction("C8_Frigg", 0.33, 111.54 / 1000.0, 729.0 / 1000.0);
    wellFluid.addTBPfraction("C9_Frigg", 0.19, 126.1 / 1000.0, 749.0 / 1000.0);
    wellFluid.addTBPfraction("C10_Frigg", 0.15, 140.14 / 1000.0, 760.0 / 1000.0);
    wellFluid.addTBPfraction("C11_Frigg", 0.69, 175.0 / 1000.0, 830.0 / 1000.0);
    wellFluid.addTBPfraction("C12_Frigg", 0.5, 280.0 / 1000.0, 914.0 / 1000.0);
    wellFluid.addTBPfraction("C13_Frigg", 0.103, 560.0 / 1000.0, 980.0 / 1000.0);

    wellFluid.addTBPfraction("C6_ML_WestCtrl", 0.0, 84.0 / 1000.0, 684.0 / 1000.0);
    wellFluid.addTBPfraction("C7_ML_WestCtrl", 0.0, 97.9 / 1000.0, 742.0 / 1000.0);
    wellFluid.addTBPfraction("C8_ML_WestCtrl", 0.0, 111.5 / 1000.0, 770.0 / 1000.0);
    wellFluid.addTBPfraction("C9_ML_WestCtrl", 0.0, 126.1 / 1000.0, 790.0 / 1000.0);
    wellFluid.addTBPfraction("C10_ML_WestCtrl", 0.0, 140.14 / 1000.0, 805.0 / 1000.0);
    wellFluid.addTBPfraction("C11_ML_WestCtrl", 0.0, 175.0 / 1000.0, 815.0 / 1000.0);
    wellFluid.addTBPfraction("C12_ML_WestCtrl", 0.0, 280.0 / 1000.0, 835.0 / 1000.0);
    wellFluid.addTBPfraction("C13_ML_WestCtrl", 0.0, 450.0 / 1000.0, 850.0 / 1000.0);
    wellFluid.addComponent("water", 12.01);
    wellFluid.setMixingRule(10);
    wellFluid.init(0);
    wellFluid.setMultiPhaseCheck(true);

    // Actually there are two sets of tests in the same file now
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
  void testTPflashComp1() {
    testOps = new ThermodynamicOperations(wellFluid);
    testOps.TPflash();
  }

  @Test
  void testTPflashComp2() {
    wellFluid.setTemperature(339.04);
    wellFluid.setPressure(1.5);
    wellFluid.setMolarComposition(new double[] {0.0, 4.76579e-6, 1.21459e-5, 1.3409e-3, 3.30439e-2,
        5.06e-3, 7.34e-3, 1.53e-3, 4.11e-3, 1.58e-3, 2.255e-3, 2.8779e-4, 8.58e-4, 8.73e-4, 8.5e-4,
        3.88e-3, 7.36e-2, 1.47e-1, 6.176e-2, 3.69e-2, 7.735e-3, 1.023e-2, 6.19e-3, 4.3e-3, 1.2e-2,
        8.96e-3, 1.539e-3, 5.9921e-1});
    testOps = new ThermodynamicOperations(wellFluid);
    testOps.TPflash();
    assertEquals(1.4292538950216407, wellFluid.getPhase(0).getDensity(), 1e-5);
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
