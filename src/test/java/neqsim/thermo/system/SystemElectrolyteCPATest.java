package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * ElectrolyteCPAEosTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SystemElectrolyteCPATest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem;
  static ThermodynamicOperations testOps;
  static neqsim.thermo.ThermodynamicModelTest testModel = null;

  /**
   * <p>
   * setUp.
   * </p>
   */
  @BeforeAll
  public static void setUp() {
    thermoSystem = new SystemElectrolyteCPAstatoil(298.15, 10.01325);
    thermoSystem.addComponent("methane", 0.1);
    thermoSystem.addComponent("water", 1.0);
    thermoSystem.addComponent("Na+", 0.001);
    thermoSystem.addComponent("Cl-", 0.001);
    thermoSystem.setMixingRule(10);
    testModel = new neqsim.thermo.ThermodynamicModelTest(thermoSystem);
    testModel.setMaxError(1e-10);
    testOps = new ThermodynamicOperations(thermoSystem);
    testOps.TPflash();
    thermoSystem.initProperties();
  }

  /**
   * <p>
   * tearDown.
   * </p>
   */
  @AfterAll
  public static void tearDown() {}

  /**
   * <p>
   * testTPflash.
   * </p>
   */
  @Test
  public void testTPflash() {
    assertEquals(2, thermoSystem.getNumberOfPhases());
  }

  /**
   * <p>
   * testinitPhysicalProperties.
   * </p>
   */
  @Test
  public void testDensity() {
    assertEquals(6.594232943612613, thermoSystem.getPhase(PhaseType.GAS).getDensity("kg/m3"), 0.01);
    assertEquals(995.69, thermoSystem.getPhase(PhaseType.AQUEOUS).getDensity("kg/m3"), 0.01);
  }

  /**
   * <p>
   * testFugacityCoefficients.
   * </p>
   */
  @Test
  @DisplayName("test the fugacity coefficients calculated")
  public void testFugacityCoefficients() {
    assertTrue(testModel.checkFugacityCoefficients());
  }

  /**
   * <p>
   * checkFugacityCoefficientsDP.
   * </p>
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to pressure")
  public void checkFugacityCoefficientsDP() {
    assertTrue(testModel.checkFugacityCoefficientsDP());
  }

  /**
   * <p>
   * checkFugacityCoefficientsDT.
   * </p>
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to temperature")
  public void checkFugacityCoefficientsDT() {
    assertTrue(testModel.checkFugacityCoefficientsDT());
  }

  /**
   * <p>
   * checkFugacityCoefficientsDn.
   * </p>
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to composition")
  public void checkFugacityCoefficientsDn() {
    assertTrue(testModel.checkFugacityCoefficientsDn());
  }

  /**
   * <p>
   * checkFugacityCoefficientsDn2.
   * </p>
   */
  @Test
  @DisplayName("test derivative of fugacity coefficients with respect to composition (2nd method)")
  public void checkFugacityCoefficientsDn2() {
    assertTrue(testModel.checkFugacityCoefficientsDn2());
  }
}
