package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author victorigi99
 */
public class PSFlashVegaTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("helium", 1.0);
    testSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
  }

  /**
   * Test method for {@link neqsim.thermodynamicoperations.flashops.PSFlashVega#run()}.
   */
  @Test
  void testRun() {
    double[] VegaProps = testSystem.getPhase(0).getProperties_Vega();
    double VegaEntropy = VegaProps[8] * testSystem.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
    testSystem.setPressure(20.0);
    testOps.PSflashVega(VegaEntropy);
    VegaProps = testSystem.getPhase(0).getProperties_Vega();
    double VegaEntropy2 = VegaProps[8] * testSystem.getPhase(0).getNumberOfMolesInPhase();
    assertEquals(VegaEntropy, VegaEntropy2, Math.abs(VegaEntropy2) / 1000.0);
  }
}

