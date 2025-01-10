package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class Standard_ISO6976Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 20.0, 1.0);
    testSystem.addComponent("methane", 0.931819);
    testSystem.addComponent("ethane", 0.025618);
    testSystem.addComponent("nitrogen", 0.010335);
    testSystem.addComponent("CO2", 0.015391);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test method for {@link neqsim.standards.gasquality.Standard_ISO6976#calculate()}.
   */
  @Test
  void testCalculate() {
    Standard_ISO6976 standard = new Standard_ISO6976(testSystem, 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    double GCV = standard.getValue("GCV");
    double WI = standard.getValue("WI");
    assertEquals(39614.56783352743, GCV, 0.01);
    assertEquals(44.61477915805513, WI, 0.01);
  }

  /**
   * Test method for {@link neqsim.standards.gasquality.Standard_ISO6976#calculate()} if wrong
   * reference state is gven. Valid reference states should be 0, 15 and 20 C and 15F (15.55C). If
   * wrong reference state is given, the program should use standard conditions (15C).
   */
  @Test
  void testCalculateWithWrongReferenceState() {
    double volumeReferenceState = 0;
    double energyReferenceState = 15.55;
    Standard_ISO6976 standard =
        new Standard_ISO6976(testSystem, volumeReferenceState, energyReferenceState, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    double GCV = standard.getValue("GCV");
    standard.getValue("WI");
    assertEquals(39614.56783352743, GCV, 0.01);
    energyReferenceState = 15.15; // example of wrong reference condition
    volumeReferenceState = 1.15; // example of wrong volume reference condition
    standard.setEnergyRefT(energyReferenceState);
    standard.setVolRefT(volumeReferenceState);
    standard.calculate();
    GCV = standard.getValue("GCV");
    assertEquals(37499.35392575905, GCV, 0.01);
  }

  /**
   * Test method for {@link neqsim.standards.gasquality.Standard_ISO6976#calculate()}.
   */
  @Test
  void testCalculateWithPSeudo() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 20.0, 1.0);
    testSystem.addComponent("methane", 0.931819);
    testSystem.addComponent("ethane", 0.025618);
    testSystem.addComponent("nitrogen", 0.010335);
    testSystem.addComponent("CO2", 0.015391);
    testSystem.addTBPfraction("C10", 0.015391, 90.0 / 1000.0, 0.82);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    Standard_ISO6976 standard = new Standard_ISO6976(testSystem, 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    double GCV = standard.getValue("GCV");
    standard.getValue("WI");
    assertEquals(42377.76099372482, GCV, 0.01);
  }

  @Test
  void testCalculate2() {
    SystemInterface testSystem = new SystemSrkEos(273.15 - 150.0, 1.0);
    testSystem.addComponent("methane", 0.931819);
    testSystem.addComponent("ethane", 0.025618);
    testSystem.addComponent("nitrogen", 0.010335);
    testSystem.addComponent("CO2", 0.015391);

    // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    /*
     * testSystem.addComponent("methane", 0.922393); testSystem.addComponent("ethane", 0.025358);
     * testSystem.addComponent("propane", 0.01519); testSystem.addComponent("n-butane", 0.000523);
     * testSystem.addComponent("i-butane", 0.001512); testSystem.addComponent("n-pentane",
     * 0.002846); testSystem.addComponent("i-pentane", 0.002832);
     * testSystem.addComponent("22-dim-C3", 0.001015); testSystem.addComponent("n-hexane",
     * 0.002865); testSystem.addComponent("nitrogen", 0.01023); testSystem.addComponent("CO2",
     * 0.015236);
     */

    /*
     * testSystem.addComponent("methane", 0.9247); testSystem.addComponent("ethane", 0.035);
     * testSystem.addComponent("propane", 0.0098); testSystem.addComponent("n-butane", 0.0022);
     * testSystem.addComponent("i-butane", 0.0034); testSystem.addComponent("n-pentane", 0.0006);
     * testSystem.addComponent("nitrogen", 0.0175); testSystem.addComponent("CO2", 0.0068);
     */

    // testSystem.addComponent("water", 0.016837);

    /*
     * testSystem.addComponent("n-hexane", 0.0); testSystem.addComponent("n-heptane", 0.0);
     * testSystem.addComponent("n-octane", 0.0); testSystem.addComponent("n-nonane", 0.0);
     * testSystem.addComponent("nC10", 0.0);
     *
     * testSystem.addComponent("CO2", 0.68); testSystem.addComponent("H2S", 0.0);
     * testSystem.addComponent("water", 0.0); testSystem.addComponent("oxygen", 0.0);
     * testSystem.addComponent("carbonmonoxide", 0.0); testSystem.addComponent("nitrogen", 1.75);
     */
    // testSystem.addComponent("MEG", 1.75);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    testSystem.init(0);
    Standard_ISO6976 standard = new Standard_ISO6976(testSystem, 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    Assertions.assertEquals(0.9974432506378011, standard.getValue("CompressionFactor"));
    Assertions.assertEquals(39614.56783352743, standard.getValue("SuperiorCalorificValue"));
    Assertions.assertEquals(35693.92161464964, standard.getValue("InferiorCalorificValue"));
    Assertions.assertEquals(39614.56783352743, standard.getValue("GCV"));

    Assertions.assertEquals(51701.01275822569, standard.getValue("SuperiorWobbeIndex"));
    Assertions.assertEquals(46584.17339159412, standard.getValue("InferiorWobbeIndex"));

    Assertions.assertEquals(0.5870995452263126, standard.getValue("RelativeDensity"));
    Assertions.assertEquals(0.9974432506378011, standard.getValue("CompressionFactor"));
    Assertions.assertEquals(16.972142879156355, standard.getValue("MolarMass"));

    // standard.display("test");
    /*
     * StandardInterface standardUK = new UKspecifications_ICF_SI(testSystem);
     * standardUK.calculate(); logger.info("ICF " +
     * standardUK.getValue("IncompleteCombustionFactor", ""));
     *
     * logger.info("HID " + testSystem.getPhase(0).getComponent("methane").getHID(273.15 - 150.0));
     * logger.info("Hres " + testSystem.getPhase(0).getComponent("methane").getHresTP(273.15 -
     * 150.0));
     */
  }


  @Test
  void testCalculate3() {
    SystemInterface testSystem = new SystemSrkEos(273.15, 1.0);
    testSystem.addComponent("methane", 0.92470);
    testSystem.addComponent("ethane", 0.035);
    testSystem.addComponent("propane", 0.0098);
    testSystem.addComponent("n-butane", 0.00220);
    testSystem.addComponent("i-butane", 0.0034);
    testSystem.addComponent("n-pentane", 0.0006);
    testSystem.addComponent("nitrogen", 0.0175);
    testSystem.addComponent("CO2", 0.0068);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    testSystem.init(0);
    Standard_ISO6976 standard = new Standard_ISO6976(testSystem, 15, 15, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    Assertions.assertEquals(0.99764929782, standard.getValue("CompressionFactor"), 1e-3);
    Assertions.assertEquals(35144.8789915, standard.getValue("InferiorCalorificValue"), 5);
    Assertions.assertEquals(38959.473378295, standard.getValue("GCV"), 1e-5);

    Assertions.assertEquals(50107.49824498, standard.getValue("SuperiorWobbeIndex"), 1e-5);
    Assertions.assertEquals(45201.380041, standard.getValue("InferiorWobbeIndex"), 1e-5);

    Assertions.assertEquals(0.60453397833045, standard.getValue("RelativeDensity"), 1e-5);
    Assertions.assertEquals(0.99770997554, standard.getValue("CompressionFactor"), 1e-5);
    Assertions.assertEquals(17.477845, standard.getValue("MolarMass"), 1e-5);

    Stream testStream = new Stream("testStream", testSystem);
    testStream.run();
    Assertions.assertEquals(50107.49824498, testStream.getWI("volume", 15, 15) / 1e3, 1e-5);
    Assertions.assertEquals(38959.473378, testStream.getGCV("volume", 15, 15) / 1e3, 1e-5);

  }

  @Test
  @Disabled
  void testDisplay() {
    Standard_ISO6976 s = new Standard_ISO6976(testSystem);
    s.display("test");
  }
}
