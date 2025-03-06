package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author victorigi99
 */
public class PSFlashLeachmanTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("hydrogen", 1.0);
    testSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
  }

  /**
   * Test method for {@link neqsim.thermodynamicoperations.flashops.PSFlashLeachman#run()}.
   */
  @Test
  void testRun() {
    double[] LeachmanProps = testSystem.getPhase(0).getProperties_Leachman();
    double LeachmanEntropy = LeachmanProps[8] * testSystem.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
    testSystem.setPressure(20.0);
    testOps.PSflashLeachman(LeachmanEntropy);
    LeachmanProps = testSystem.getPhase(0).getProperties_Leachman();
    double LeachmanEntropy2 = LeachmanProps[8] * testSystem.getPhase(0).getNumberOfMolesInPhase();
    assertEquals(LeachmanEntropy, LeachmanEntropy2, Math.abs(LeachmanEntropy2) / 1000.0);
  }
}

