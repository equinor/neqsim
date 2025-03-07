package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
/**
 * @author victorigi99
 */
public class PHFlashVegaTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 50.0);
    testSystem.addComponent("helium", 1.0);
    testSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
  }

  @Test
  void testRun() {
    double[] VegaProps = testSystem.getPhase(0).getProperties_Vega();
    double VegaEnthalpy = VegaProps[7] * testSystem.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
    testSystem.setPressure(10.0);
    testOps.PHflashVega(VegaEnthalpy);
    VegaProps = testSystem.getPhase(0).getProperties_Vega();
    double VegaEnthalpy2 = VegaProps[7] * testSystem.getPhase(0).getNumberOfMolesInPhase();
    assertEquals(VegaEnthalpy, VegaEnthalpy2, Math.abs(VegaEnthalpy2) / 1000.0);
  
    testOps.PHflashVega(VegaEnthalpy + 100.0);
    VegaProps = testSystem.getPhase(0).getProperties_Vega();
    double VegaEnthalpy3 = VegaProps[7] * testSystem.getPhase(0).getNumberOfMolesInPhase();
    assertEquals(VegaEnthalpy3, VegaEnthalpy2 + 100.0, Math.abs(VegaEnthalpy2) / 1000.0);
  }
}
