package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 *
 */
class PSFlashGERG2008Test {

  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() throws Exception {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
  }

  /**
   * Test method for {@link neqsim.thermodynamicOperations.flashOps.PSFlashGERG2008#run()}.
   */
  @Test
  void testRun() {
    double[] gergProps = testSystem.getPhase(0).getProperties_GERG2008();
    double gergEntropy = gergProps[8] * testSystem.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
    testSystem.setPressure(20.0);
    testOps.PSflashGERG2008(gergEntropy);
    gergProps = testSystem.getPhase(0).getProperties_GERG2008();
    double gergEntropy2 = gergProps[8] * testSystem.getPhase(0).getNumberOfMolesInPhase();
    assertEquals(gergEntropy, gergEntropy2, Math.abs(gergEntropy2) / 1000.0);
  }

}
