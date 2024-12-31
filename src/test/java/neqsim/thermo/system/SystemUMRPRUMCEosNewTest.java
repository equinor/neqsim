package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.SaturationPressure;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

class SystemUMRPRUMCEosNewTest extends neqsim.NeqSimTest {
  static Logger logger = LogManager.getLogger(SystemUMRPRUMCEosNewTest.class);

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
    testSystem.addComponent("nitrogen", 0.7);
    // testSystem.addComponent("CO2", 0.01);
    // testSystem.addComponent("methane", 0.68);
    testSystem.addComponent("ethane", 0.3);
    // testSystem.addComponent("n-heptane", 0.2);
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    // testSystem.setMixingRule(1);
    testSystem.setMixingRule(1);
    testModel = new neqsim.thermo.ThermodynamicModelTest(testSystem);
    // testModel = new neqsim.thermo.ThermodynamicModelTest(testSystem);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.init(0);
    testSystem.init(3);
    // testSystem.initProperties();
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
    testSystem.addComponent("nitrogen", 0.7);
    // testSystem.addComponent("CO2", 0.01);
    // testSystem.addComponent("methane", 0.68);
    testSystem.addComponent("ethane", 0.3);
    // testSystem.addComponent("n-heptane", 0.2);
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.setMixingRule(0);
    testSystem.init(0);
    // testSystem.init(1);
    testSystem.init(3);
    logger.info("molar volume gas+oil is " + testSystem.getMolarVolume());
    logger.info("molar volume gas is " + testSystem.getPhase(0).getMolarVolume());
    logger.info("molar volume liquid is " + testSystem.getPhase(1).getMolarVolume());
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

    // logger.info("molar volume liquid is " + testSystem.((PhasePrEosvolcor)
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
      logger.info("Cricondenbar " + (testOps.get("cricondenbar")[0] - 273.15) + " "
          + testOps.get("cricondenbar")[1]);
    } catch (Exception ex) {
      assertTrue(false);
      throw new Exception(ex);
    }
    assertEquals(testOps.get("cricondenbar")[1], 130.686140727503, 0.02);
  }

  /**
   * <p>
   * checkPhaseEnvelope2.
   * </p>
   *
   * @throws Exception
   */
  @Test
  @DisplayName("calculate phase envelope using UMR")
  public void checkPhaseEnvelope2() throws Exception {
    testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 10.0);
    testSystem.addComponent("N2", 0.00675317857);
    testSystem.addComponent("CO2", .02833662296);
    testSystem.addComponent("methane", 0.8363194562);
    testSystem.addComponent("ethane", 0.06934307324);
    testSystem.addComponent("propane", 0.03645246567);
    testSystem.addComponent("i-butane", 0.0052133558);
    testSystem.addComponent("n-butane", 0.01013260919);
    testSystem.addComponent("i-pentane", 0.00227310164);
    testSystem.addComponent("n-pentane", 0.00224658464);
    testSystem.addComponent("2-m-C5", 0.00049491);
    testSystem.addComponent("3-m-C5", 0.00025783);
    testSystem.addComponent("n-hexane", 0.00065099);
    testSystem.addComponent("c-hexane", .00061676);
    testSystem.addComponent("n-heptane", 0.00038552);
    testSystem.addComponent("benzene", 0.00016852);
    testSystem.addComponent("n-octane", 0.00007629);
    testSystem.addComponent("c-C7", 0.0002401);
    testSystem.addComponent("toluene", 0.0000993);
    testSystem.addComponent("n-nonane", 0.00001943);
    testSystem.addComponent("c-C8", 0.00001848);
    testSystem.addComponent("m-Xylene", 0.00002216);
    testSystem.addComponent("nC10", 0.00000905);
    testSystem.addComponent("nC11", 0.000000001);
    testSystem.addComponent("nC12", 0.000000001);

    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.calcPTphaseEnvelope();
      logger.info("Cricondenbar " + (testOps.get("cricondenbar")[0] - 273.15) + " "
          + testOps.get("cricondenbar")[1]);
    } catch (Exception ex) {
      assertTrue(false);
      throw new Exception(ex);
    }
    assertEquals((testOps.get("cricondenbar")[0] - 273.15), -11.09948347, 0.02);
    assertEquals(testOps.get("cricondenbar")[1], 104.75329137038476, 0.02);

    testSystem.setTemperature(-11.0994834, "C");
    testSystem.setPressure(10);
    SaturationPressure satPresSim = new SaturationPressure(testSystem);
    satPresSim.run();
    assertEquals(104.7532901763, satPresSim.getThermoSystem().getPressure(), 0.001);
  }

  /**
   * <p>
   * checkPhaseEnvelope2.
   * </p>
   *
   * @throws Exception
   */
  @Test
  @DisplayName("test UMR with pseudo comp")
  public void testPseudoComptest() {
    NeqSimDataBase.setCreateTemporaryTables(true);
    SystemInterface testSystem = new SystemUMRPRUMCEos(273.15 + 15, 10.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 80);
    testSystem.addTBPfraction("C7", .0010, 85.5 / 1000.0, 0.66533);
    testSystem.createDatabase(true);
    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    NeqSimDataBase.setCreateTemporaryTables(false);
    try {
      testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.initPhysicalProperties("density");
    assertEquals(6.84959007, testSystem.getDensity("kg/m3"), 0.00001);
  }
}
