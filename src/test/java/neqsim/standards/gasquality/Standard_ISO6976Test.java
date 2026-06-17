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
    // Reference: typical NCS sales gas at 0 degC vol-ref / 60 degF (15.55 degC) energy-ref.
    // GCV 39.61 MJ/Sm3 and WI 51.70 MJ/Sm3 are within the Gassco/NORSOK I-104 export
    // gas specification (Wobbe 48.6 - 53.5 MJ/Sm3, ISO 6976 reference state).
    assertEquals(39614.56783352743, GCV, 0.01);
    // WI alias should resolve to SuperiorWobbeIndex (was previously a constant
    // ~44.6 mol/m3 because "WI" was not aliased in Standard_ISO6976#getValue()).
    assertEquals(standard.getValue("SuperiorWobbeIndex"), WI, 1e-9);
    assertEquals(51701.01275822569, WI, 0.01);
    assertEquals(WI, standard.getValue("WobbeIndex"), 1e-9);
  }

  /**
   * ISO 6976 anchor test for pure methane. Verifies that the calculator reproduces published
   * reference values within 0.5 % at the canonical reference conditions (vol-ref 0 degC, energy-ref
   * 25 degC, real gas, volume basis).
   *
   * <p>
   * Literature references:
   * <ul>
   * <li>ISO 6976:2016 Table 5 - molar superior calorific value of methane = 891.51 kJ/mol.</li>
   * <li>ISO 6976:2016 Annex E - pure methane superior Wobbe Index ~ 53.45 MJ/Sm3 at 0 degC metering
   * / 25 degC combustion.</li>
   * <li>GPSA Engineering Data Book, 14th ed., Section 23 - methane HHV = 1010 BTU/scf at 60 degF (~
   * 39.65 MJ/Sm3 at 0 degC).</li>
   * </ul>
   */
  @Test
  void testPureMethaneAgainstISO6976Reference() {
    SystemInterface pureCH4 = new SystemSrkEos(273.15 + 20.0, 1.0);
    pureCH4.addComponent("methane", 1.0);
    pureCH4.setMixingRule("classic");
    new ThermodynamicOperations(pureCH4).TPflash();
    Standard_ISO6976 std = new Standard_ISO6976(pureCH4, 0, 25.0, "volume");
    std.setReferenceState("real");
    std.setReferenceType("volume");
    std.calculate();
    double gcv = std.getValue("GCV");
    double wi = std.getValue("WI");
    // ISO 6976:2016 reference values for pure methane:
    // GCV ~ 39 840 kJ/Sm3, Superior Wobbe Index ~ 53 450 kJ/Sm3
    Assertions.assertEquals(39840.0, gcv, 250.0,
        "Pure methane GCV should match ISO 6976 reference within 0.6 %, got " + gcv);
    Assertions.assertEquals(53450.0, wi, 250.0,
        "Pure methane WI should match ISO 6976 reference within 0.5 %, got " + wi);
  }

  /**
   * Regression test: getValue("WI") must vary with composition. Previously returned a near-constant
   * compression-factor / molar-density value because "WI" was not aliased to "SuperiorWobbeIndex".
   *
   * <p>
   * Literature reference: EASEE-gas CBP 2005-001 / EN 16726 H-gas Wobbe range is 47.20 - 56.50
   * MJ/Sm3. Rich gas mixtures with significant propane content can exceed the upper limit, which is
   * the expected physical behaviour validated here.
   */
  @Test
  void testWIAliasVariesWithComposition() {
    // Lean gas: 98 % CH4 / 2 % C2H6 - WI should be close to pure methane (~ 53 MJ/Sm3)
    SystemInterface leanGas = new SystemSrkEos(273.15 + 20.0, 1.0);
    leanGas.addComponent("methane", 0.98);
    leanGas.addComponent("ethane", 0.02);
    leanGas.setMixingRule("classic");
    new ThermodynamicOperations(leanGas).TPflash();
    Standard_ISO6976 leanStd = new Standard_ISO6976(leanGas, 0, 15.55, "volume");
    leanStd.setReferenceState("real");
    leanStd.setReferenceType("volume");
    leanStd.calculate();
    double leanWI = leanStd.getValue("WI");

    // Rich gas: 80 % CH4 / 10 % C2H6 / 10 % C3H8 - WI ~ 58 MJ/Sm3 (above EU H-gas spec)
    SystemInterface richGas = new SystemSrkEos(273.15 + 20.0, 1.0);
    richGas.addComponent("methane", 0.80);
    richGas.addComponent("ethane", 0.10);
    richGas.addComponent("propane", 0.10);
    richGas.setMixingRule("classic");
    new ThermodynamicOperations(richGas).TPflash();
    Standard_ISO6976 richStd = new Standard_ISO6976(richGas, 0, 15.55, "volume");
    richStd.setReferenceState("real");
    richStd.setReferenceType("volume");
    richStd.calculate();
    double richWI = richStd.getValue("WI");

    // Sanity: the values must differ - the original bug returned a constant ~44.6.
    Assertions.assertTrue(Math.abs(leanWI - richWI) > 1000.0,
        "WI should differ between lean and rich gas, got lean=" + leanWI + " rich=" + richWI);
    Assertions.assertTrue(richWI > leanWI,
        "Rich gas should have higher WI, got lean=" + leanWI + " rich=" + richWI);

    // Lean gas matches pure-methane reference (53.45 MJ/Sm3 +/- ethane offset).
    Assertions.assertEquals(53860.0, leanWI, 200.0,
        "Lean (98/2) WI should match literature ~ 53.86 MJ/Sm3, got " + leanWI);
    // Rich gas exceeds EU H-gas upper limit (56.5 MJ/Sm3) - physically expected for 10 % C3.
    Assertions.assertEquals(58380.0, richWI, 200.0,
        "Rich (80/10/10) WI should match literature ~ 58.38 MJ/Sm3, got " + richWI);
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
