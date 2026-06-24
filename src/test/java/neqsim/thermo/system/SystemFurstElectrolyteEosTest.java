package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * ElectrolyteScrkEosTest class.
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SystemFurstElectrolyteEosTest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem;
  static ThermodynamicOperations testOps;
  static neqsim.thermo.ThermodynamicModelTest testModel = null;

  /**
   * setUp.
   */
  @BeforeAll
  public static void setUp() {
    thermoSystem = new SystemFurstElectrolyteEos(298.15, 10.01325);
    thermoSystem.addComponent("methane", 0.1);
    thermoSystem.addComponent("water", 1.0);
    thermoSystem.addComponent("Na+", 0.001);
    thermoSystem.addComponent("Cl-", 0.001);
    thermoSystem.setMixingRule(4);
    testModel = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
    testOps = new ThermodynamicOperations(thermoSystem);
    testOps.TPflash();
    thermoSystem.initProperties();
  }

  /**
   * testTPflash.
   */
  @Test
  public void testTPflash() {
    assertEquals(2, thermoSystem.getNumberOfPhases());
  }

  /**
   * testinitPhysicalProperties.
   */
  @Test
  public void testinitPhysicalProperties() {
    assertEquals(thermoSystem.getPhase(0).getPhysicalProperties().getDensity(),
        thermoSystem.getPhase(0).getPhysicalProperties().getDensity());
  }

  /**
   * testFugacityCoefficients.
   */
  @Test
  @DisplayName("test the fugacity coefficients calculated")
  public void testFugacityCoefficients() {
    assertTrue(testModel.checkFugacityCoefficients());
  }

  /**
   * checkFugacityCoefficientsDP.
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to pressure")
  public void checkFugacityCoefficientsDP() {
    assertTrue(testModel.checkFugacityCoefficientsDP());
  }

  /**
   * checkFugacityCoefficientsDT.
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to temperature")
  public void checkFugacityCoefficientsDT() {
    assertTrue(testModel.checkFugacityCoefficientsDT());
  }

  /**
   * checkFugacityCoefficientsDn.
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to composition")
  public void checkFugacityCoefficientsDn() {
    assertTrue(testModel.checkFugacityCoefficientsDn());
  }

  /**
   * checkFugacityCoefficientsDn2.
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to composition (2nd method)")
  public void checkFugacityCoefficientsDn2() {
    assertTrue(testModel.checkFugacityCoefficientsDn2());
  }
}
