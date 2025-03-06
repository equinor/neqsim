package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;


/**
 * @author victorigi99
 */
public class PHFlashLeachmanTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 50.0);
    testSystem.addComponent("hydrogen", 1.0);
    testSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
  }

  @Test
  void testRun() {
    double[] leachmanProps = testSystem.getPhase(0).getProperties_Leachman();
    double leachmanEnthalpy = leachmanProps[7] * testSystem.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
    testSystem.setPressure(10.0);
    testOps.PHflashLeachman(leachmanEnthalpy);
    leachmanProps = testSystem.getPhase(0).getProperties_Leachman();
    double leachmanEnthalpy2 = leachmanProps[7] * testSystem.getPhase(0).getNumberOfMolesInPhase();
    assertEquals(leachmanEnthalpy, leachmanEnthalpy2, Math.abs(leachmanEnthalpy2) / 1000.0);
  
    testOps.PHflashLeachman(leachmanEnthalpy + 100.0);
    leachmanProps = testSystem.getPhase(0).getProperties_Leachman();
    double leachmanEnthalpy3 = leachmanProps[7] * testSystem.getPhase(0).getNumberOfMolesInPhase();
    assertEquals(leachmanEnthalpy3, leachmanEnthalpy2 + 100.0, Math.abs(leachmanEnthalpy2) / 1000.0);
  }
}
