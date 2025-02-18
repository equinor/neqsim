package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class TPFlashTestHighTemp {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(243.15, 300.0);
    testSystem.addComponent("nitrogen", 1.64e-3);
    testSystem.addComponent("CO2", 1.64e-3);
    testSystem.addComponent("H2S", 1.64e-3);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 2.0);
    testSystem.addComponent("propane", 1.0);
    testSystem.addComponent("i-butane", 1.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("i-pentane", 1.0);
    testSystem.addComponent("n-pentane", 1.0);
    testSystem.addComponent("n-hexane", 1.0);
    testSystem.addComponent("n-heptane", 1.0);
    testSystem.addComponent("n-octane", 1.0);
    testSystem.addComponent("n-nonane", 1.0);
    testSystem.addComponent("nC10", 1.0);
    testSystem.addComponent("nC11", 1.0);
    testSystem.addComponent("nC12", 1.0);
    testSystem.addComponent("nC13", 1.0);
    testSystem.addComponent("nC14", 1.0);
    testSystem.addComponent("nC15", 1.0);
    testSystem.addComponent("nC16", 1.0);
    testSystem.addComponent("nC17", 1.0);
    testSystem.addComponent("nC18", 1.0);
    testSystem.addComponent("nC19", 1.0);

    testSystem.setMixingRule("classic");
    testSystem.setMolarComposition(new double[] {1.63e-3, 3.23e-3, 0, 3e-1, 4.6e-2, 1.4e-2, 2.2e-2,
        3.9e-3, 8.8e-3, 2.6e-3, 3.2e-2, 1.2e-1, 1.5e-1, 9.8e-2, 7.6e-2, 4.1e-2, 2.5e-2, 1.6e-2,
        1e-2, 5.6e-3, 2.7e-3, 1.3e-3, 8.7e-4, 3.8e-4});
    testSystem.setMultiPhaseCheck(true);
  }

  @Test
  void testRun() {
    testSystem.setPressure(88, "bara");

    /*
     * for (int i = 0; i < 400; i++) { testSystem.setTemperature(0.0 + i * 1, "C"); testOps = new
     * ThermodynamicOperations(testSystem); testOps.TPflash(); testSystem.initProperties();
     * System.out.print(testSystem.getPhaseFraction("gas", "mole") + " numerofphases " +
     * testSystem.getNumberOfPhases() + " temp " + testSystem.getTemperature("C") + " hasoil " +
     * testSystem.hasPhaseType("oil") + " gibbs energy " + testSystem.getGibbsEnergy() + " gibbs
     * energy " + " density " + testSystem.getDensity("kg/m3") + " \n");
     * 
     * }
     */

    testSystem.setTemperature(268.0, "C");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    assertEquals(0.006832557441121211, testSystem.getPhaseFraction("gas", "mole"), 0.001);
  }
}

