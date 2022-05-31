package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class SystemUMRPRUMCEosNewTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static neqsim.thermo.ThermodynamicModelTest testModel = null;
  neqsim.thermo.ThermodynamicModelTest fugTest;

  /**
   * <p>
   * setUp.
   * </p>
   */
  @BeforeAll
  public static void setUp() {
    // testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 10.0);
    testSystem = new neqsim.thermo.system.SystemUMRPRUMCEosNew(298.0, 10.0);
    // testSystem = new neqsim.thermo.system.SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("nitrogen", 1);
    //testSystem.addComponent("CO2", 0.01);
    //testSystem.addComponent("methane", 0.68);
    //testSystem.addComponent("ethane", 0.1);
    // testSystem.addComponent("n-heptane", 0.2);
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    // testSystem.setMixingRule(1);
    testSystem.setMixingRule("classic");
    testModel = new neqsim.thermo.ThermodynamicModelTest(testSystem);
    // testModel = new neqsim.thermo.ThermodynamicModelTest(testSystem);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.init(0);
    // testSystem.init(3);
    testSystem.initProperties();
    // testSystem.i
  }



  /**
   * <p>
   * testFugasities.
   * </p>
   */
  // @Test
  public void testFugasities() {
    testSystem.init(0);
    testSystem.init(1);
    fugTest = new neqsim.thermo.ThermodynamicModelTest(testSystem);
    assertTrue(fugTest.checkFugacityCoefficients());

    double fucoef = testSystem.getComponent(0).getLogFugacityCoefficient();

    assertEquals(-0.002884922, fucoef, 1e-6);


    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    double molvol = testSystem.getMolarVolume();

    assertEquals(247.09909107115, molvol, 1e-2);
  }

  /**
   * <p>
   * testCompressibility.
   * </p>
   */
  @Test
  @DisplayName("test compressibility of gas phase")
  public void testCompressibility() {
    // testSystem = new neqsim.thermo.system.SystemPr(298.0, 10.0);
    // testSystem = new SystemSrkEos(298.0, 10.0);
    // testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 10.0);
    testSystem = new neqsim.thermo.system.SystemUMRPRUMCEosNew(298, 10);
    testSystem.addComponent("nitrogen", 1);
    // testSystem.addComponent("CO2", 0.01);
     testSystem.addComponent("methane", 0.68);
     testSystem.addComponent("ethane", 0.1);
    // testSystem.addComponent("n-heptane", 0.2);
    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.init(0);
    // testSystem.init(1);
    testSystem.init(3);
    System.out.println("molar volume gas+oil is " + testSystem.getMolarVolume());
    System.out.println("molar volume gas is " + testSystem.getPhase(0).getMolarVolume());
    System.out.println("molar volume liquid is " + testSystem.getPhase(1).getMolarVolume());
    // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testOps.TPflash();

    // testSystem.initProperties();
    // assertEquals(0.9711401538454589, testSystem.getPhase(0).getZ(), 0.001);
  }

  /**
   * <p>
   * testTPflash2.
   * </p>
   */
  @Disabled
  @Test
  @DisplayName("test a TPflash2")
  public void testTPflash2() {
    assertEquals(2, testSystem.getNumberOfPhases());
  }

  /**
   * <p>
   * testTPflash.
   * </p>
   */
  @Disabled
  @Test
  @DisplayName("test a TPflash of the fluid (should return two phases)")
  public void testTPflash() {
    assertEquals(2, testSystem.getNumberOfPhases());
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

    // System.out.println("molar volume liquid is " + testSystem.((PhasePrEosvolcor)
    // phase).getFC());
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


  /**
   * <p>
   * checkPhaseEnvelope.
   * </p>
   * 
   * @throws Exception
   */
   @Test
  @DisplayName("calculate phase envelope using UMR")
  public void checkPhaseEnvelope() throws Exception {
    testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 10.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.addComponent("propane", 0.1);
    testSystem.addComponent("i-butane", 0.1);
    testSystem.addComponent("n-butane", 0.1);
    testSystem.addComponent("n-pentane", 0.1);
    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.init(0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.calcPTphaseEnvelope();
      System.out.println("Cricondenbar " + (testOps.get("cricondenbar")[0] - 273.15) + " "
          + testOps.get("cricondenbar")[1]);
    } catch (Exception e) {
      assertTrue(false);
      throw new Exception(e);
    }
    assertEquals(testOps.get("cricondenbar")[1], 130.686140727503, 0.02);
  }
}
