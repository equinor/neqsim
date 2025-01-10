package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class Standard_ISO6976_2016_Test extends neqsim.NeqSimTest {
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
    Standard_ISO6976_2016 standard = new Standard_ISO6976_2016(testSystem, 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    double GCV = standard.getValue("GCV");
    double WI = standard.getValue("WI");
    assertEquals(39612.08330867018, GCV, 0.01);
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
    Standard_ISO6976_2016 standard =
        new Standard_ISO6976_2016(testSystem, volumeReferenceState, energyReferenceState, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    double GCV = standard.getValue("GCV");
    standard.getValue("WI");
    assertEquals(39612.08330867018, GCV, 0.01);
    energyReferenceState = 15.15; // example of wrong reference condition
    volumeReferenceState = 1.15; // example of wrong volume reference condition
    standard.setEnergyRefT(energyReferenceState);
    standard.setVolRefT(volumeReferenceState);
    standard.calculate();
    GCV = standard.getValue("GCV");
    assertEquals(37496.955002184994, GCV, 0.01);
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
    Standard_ISO6976_2016 standard = new Standard_ISO6976_2016(testSystem, 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    double GCV = standard.getValue("GCV");
    standard.getValue("WI");
    assertEquals(42374.88507879093, GCV, 0.01);
  }

  @Test
  void testCalculate2() {
    SystemInterface testSystem = new SystemSrkEos(273.15 - 150.0, 1.0);
    testSystem.addComponent("methane", 0.931819);
    testSystem.addComponent("ethane", 0.025618);
    testSystem.addComponent("nitrogen", 0.010335);
    testSystem.addComponent("CO2", 0.015391);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    testSystem.init(0);
    Standard_ISO6976_2016 standard = new Standard_ISO6976_2016(testSystem, 0, 15.55, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    Assertions.assertEquals(0.9974581843581334, standard.getValue("CompressionFactor"), 1e-5);
    Assertions.assertEquals(35693.5928445084, standard.getValue("InferiorCalorificValue"), 1e-5);
    Assertions.assertEquals(39612.08330867018, standard.getValue("GCV"));

    Assertions.assertEquals(51698.75555489656, standard.getValue("SuperiorWobbeIndex"));
    Assertions.assertEquals(46584.63219328704, standard.getValue("InferiorWobbeIndex"));

    Assertions.assertEquals(0.5870771657884608, standard.getValue("RelativeDensity"));
    Assertions.assertEquals(0.9974581843581334, standard.getValue("CompressionFactor"));
    Assertions.assertEquals(16.97159718679405, standard.getValue("MolarMass"));

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
    Standard_ISO6976_2016 standard = new Standard_ISO6976_2016(testSystem, 15, 15, "volume");
    standard.setReferenceState("real");
    standard.setReferenceType("volume");
    standard.calculate();
    Assertions.assertEquals(0.99764929782, standard.getValue("CompressionFactor"), 1e-3);
    Assertions.assertEquals(35144.8789915, standard.getValue("InferiorCalorificValue"), 5);
    Assertions.assertEquals(38956.94693, standard.getValue("GCV"), 1e-5);

    Assertions.assertEquals(50105.17975854, standard.getValue("SuperiorWobbeIndex"), 1e-5);
    Assertions.assertEquals(45201.5624460, standard.getValue("InferiorWobbeIndex"), 1e-5);

    Assertions.assertEquals(0.6045115156, standard.getValue("RelativeDensity"), 1e-5);
    Assertions.assertEquals(0.99773023373, standard.getValue("CompressionFactor"), 1e-5);
    Assertions.assertEquals(17.477288925, standard.getValue("MolarMass"), 1e-5);
  }

  @Test
  @Disabled
  void testDisplay() {
    Standard_ISO6976_2016 s = new Standard_ISO6976_2016(testSystem);
    s.display("test");
  }
}
