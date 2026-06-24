package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemPrEoSTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static neqsim.thermo.ThermodynamicModelTest testModel = null;

  /**
   * setUp.
   */
  @BeforeAll
  public static void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.68);
    testSystem.addComponent("ethane", 0.1);
    testSystem.addComponent("n-heptane", 0.2);
    testSystem.setMixingRule("classic");
    testModel = new neqsim.thermo.ThermodynamicModelTest(testSystem);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
  }

  /**
   * testMolarVolume.
   */
  @Test
  @DisplayName("test molarVolume calculation as well as unit conerters")
  public void testMolarVolume() {
    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 1.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.68);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");
    testModel = new neqsim.thermo.ThermodynamicModelTest(testSystem);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(testSystem.getMolarVolume("m3/mol"),
	testSystem.getMolarMass("kg/mol") / testSystem.getDensity("kg/m3"));
  }

  /**
   * testTPflash2.
   */
  @Test
  @DisplayName("test a TPflash2")
  public void testTPflash2() {
    assertEquals(2, testSystem.getNumberOfPhases());
  }

  /**
   * testTPflash.
   */
  @Test
  @DisplayName("test a TPflash of the fluid (should return two phases)")
  public void testTPflash() {
    assertEquals(2, testSystem.getNumberOfPhases());
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

  /**
   * checkCompressibility.
   */
  @Test
  @DisplayName("calculate compressibility of gas phase")
  public void checkCompressibility() {
    assertEquals(0.9708455641951108, testSystem.getPhase("gas").getZ(), 1e-5);
  }

  /**
   * calcProperties.
   */
  @Test
  @DisplayName("calculate properties when flow rate is 0")
  public void calcProperties() {
    testSystem.setTotalFlowRate(1.0, "mol/sec");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assertEquals(-165.60627184389855, testSystem.getEnthalpy("kJ/kg"), Math.abs(-165.60627184389855 / 1000.0));
  }

  /**
   * checkKappa.
   */
  @Test
  @DisplayName("check kappa of fluid and phase is the same")
  public void checkKappa() {
    neqsim.thermo.system.SystemPrEos testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 75.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.68);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");
    testSystem.useVolumeCorrection(true);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    testSystem.getKappa();
    testSystem.getPhase("gas").getKappa();
    assertEquals(testSystem.getKappa(), testSystem.getPhase("gas").getKappa(), 1e-5);
    assertEquals(0.817374332, testSystem.getZ(), 1e-6);
    assertEquals(0.833031816, testSystem.getZvolcorr(), 1e-6);
    assertEquals(0.833031816, testSystem.getPhase("gas").getZvolcorr(), 1e-6);
  }

  /**
   * checCompressibilityFunctions.
   */
  @Test
  @DisplayName("check compressibility functions")
  public void checkCompressibilityFunctions() {
    neqsim.thermo.system.SystemPrEos testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 75.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.68);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    double isoThermComp = testSystem.getPhase("gas").getIsothermalCompressibility();
    assertEquals(0.01566013036971859, isoThermComp, 1e-5);

    double isobaricThermalExpansivity = testSystem.getPhase("gas").getIsobaricThermalExpansivity();
    assertEquals(0.006177715706274527, isobaricThermalExpansivity, 1e-5);

    double compressibilityX = testSystem.getPhase("gas").getCompressibilityX();
    assertEquals(-1.8409592804698092, compressibilityX, 1e-5);

    double compressibilityY = testSystem.getPhase("gas").getCompressibilityY();
    assertEquals(-1.1745097777288942, compressibilityY, 1e-5);
  }
}
