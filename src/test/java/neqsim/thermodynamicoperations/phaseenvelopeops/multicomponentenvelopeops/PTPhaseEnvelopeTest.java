package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PTPhaseEnvelopeTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
  }

  /**
   * Test method for
   * {@link neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.PTphaseEnvelope}.
   */
  @Test
  void testDewP() {
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule(EosMixingRuleType.byName("classic"));

    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    testOps.calcPTphaseEnvelope();
    double[] dewPointPressures = testOps.get("dewP");
    double[] expectedDewPointPressures =
        new double[] {1.1051709180756477, 1.2214027581601699, 1.3498588075760032,
            1.4918246976412703, 1.6652911949458864, 1.8794891289619104, 2.1418131227502055,
            2.4690864123141987, 2.881197018974799, 3.404779997613969, 4.075230307874481,
            4.938583914869986, 6.051801019586486, 7.477304695462727, 9.260793952051571,
            11.364101185282063, 13.480106047577934, 14.53423776629387, 13.607498029406681,
            11.181207439509638, 9.189487040488075, 9.612827246459474, 10.706126846063928,
            12.501491987760147, 15.075672692089958, 18.51283799420178, 23.330378296334104,
            29.71319711031059, 37.25532259549197, 43.660805656603934, 45.75836660678656,
            46.42490219574348, 46.83203503669948, 46.869568345957006, 46.903557772489435};
    // System.out.println(Arrays.toString(dewPointPressures));
    assertArrayEquals(expectedDewPointPressures, dewPointPressures, 10E-10);
  }

  @Test
  void testFailingCaseWithWater() {
    testSystem.addComponent("nitrogen", 0.04);
    testSystem.addComponent("CO2", 0.06);
    testSystem.addComponent("methane", 0.80);
    testSystem.addComponent("water", 0.00000000001);

    testSystem.setMixingRule("classic");

    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    Exception exception =
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> testOps.calcPTphaseEnvelope());
  }

  @Test
  void testSimpleCase() {
    testSystem.addComponent("nitrogen", 0.88);
    testSystem.addComponent("CO2", 5.7);
    testSystem.addComponent("methane", 86.89);
    testSystem.addComponent("ethane", 3.59);
    testSystem.addComponent("propane", 1.25);
    testSystem.addComponent("i-butane", 0.19);
    testSystem.addComponent("n-butane", 0.35);
    testSystem.addComponent("i-pentane", 0.12);
    testSystem.addComponent("n-pentane", 0.12);
    testSystem.setMixingRule("classic");
    testSystem.setMixingRule("classic");

    testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelope2();
    // double[] dewPointPressures = testOps.get("dewP");
    double[] dewPointTemperatures = testOps.get("dewT");
    // double[] bubblePointPressures = testOps.get("bubP");
    double[] bubblePointTemperatures = testOps.get("bubT");
    // double[] cricondenbar = testOps.get("cricondenbar");
    // double[] criticalPoint1 = testOps.get("criticalPoint1");

    assertTrue(dewPointTemperatures.length > 20);
    assertTrue(bubblePointTemperatures.length > 10);
  }

  @Test
  void testFailingCase1() {
    // testSystem.setTemperature(40, "C");
    // testSystem.setPressure(50, "bara");
    testSystem.addComponent("nitrogen", 0.88);
    testSystem.addComponent("CO2", 5.7);
    testSystem.addComponent("methane", 86.89);
    testSystem.addComponent("ethane", 3.59);
    testSystem.addComponent("propane", 1.25);
    testSystem.addComponent("i-butane", 0.19);
    testSystem.addComponent("n-butane", 0.35);
    testSystem.addComponent("i-pentane", 0.12);
    testSystem.addComponent("n-pentane", 0.12);
    testSystem.addTBPfraction("C6", 0.15, 86 / 1000.0, 0.672);
    testSystem.addTBPfraction("C7", 0.2, 96 / 1000.0, 0.737);
    testSystem.addTBPfraction("C8", 0.22, 106 / 1000.0, 0.767);
    testSystem.addTBPfraction("C9", 0.13, 121 / 1000.0, 0.783);
    testSystem.addPlusFraction("C10+", 0.21, 172 / 1000.0, 0.818);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    testSystem.useVolumeCorrection(true);
    testSystem.initPhysicalProperties();
    testSystem.setMixingRule("classic");

    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    testOps.calcPTphaseEnvelope2();
    // double[] dewPointPressures = testOps.get("dewP");
    double[] dewPointTemperatures = testOps.get("dewT");
    // double[] bubblePointPressures = testOps.get("bubP");
    double[] bubblePointTemperatures = testOps.get("bubT");
    // double[] cricondenbar = testOps.get("cricondenbar");
    // double[] criticalPoint1 = testOps.get("criticalPoint1");

    assertTrue(dewPointTemperatures.length > 20);
    assertTrue(bubblePointTemperatures.length > 10);
  }

  @Test
  void testFailingCase2() {
    // testSystem.setTemperature(40, "C");
    // testSystem.setPressure(50, "bara");
    neqsim.thermo.system.SystemInterface fluid0_HC =
        new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 50.0);
    fluid0_HC.addComponent("nitrogen", 2.5);
    fluid0_HC.addComponent("CO2", 4.5);
    fluid0_HC.addComponent("methane", 79.45);
    fluid0_HC.addComponent("ethane", 10);
    fluid0_HC.addComponent("propane", 2.5);
    fluid0_HC.addComponent("i-butane", 0.3);
    fluid0_HC.addComponent("n-butane", 0.5);
    fluid0_HC.addComponent("22-dim-C3", 0.01);
    fluid0_HC.addComponent("i-pentane", 0.05);
    fluid0_HC.addComponent("n-pentane", 0.05);
    fluid0_HC.addComponent("n-hexane", 0.05);
    fluid0_HC.addComponent("benzene", 0.02);
    fluid0_HC.addComponent("c-hexane", 0.02);
    fluid0_HC.addComponent("n-heptane", 0.02);
    fluid0_HC.addComponent("toluene", 0.01);
    fluid0_HC.addComponent("n-octane", 0.01);
    fluid0_HC.setMixingRule("HV", "UNIFAC_UMRPRU");
    testOps = new ThermodynamicOperations(fluid0_HC);
    testOps.calcPTphaseEnvelope2();
    double[] dewPointPressures = testOps.get("dewP");
    double[] dewPointTemperatures = testOps.get("dewT");
    double[] bubblePointPressures = testOps.get("bubP");
    double[] bubblePointTemperatures = testOps.get("bubT");
    double[] bubblePointEnthalpies = testOps.get("bubH");
    double[] bubblePointVolumes = testOps.get("bubDens");
    // double[] cricondenbar = testOps.get("cricondenbar");
    // double[] criticalPoint1 = testOps.get("criticalPoint1");

    assertTrue(dewPointTemperatures.length > 20);
    assertTrue(bubblePointTemperatures.length > 20);
    assertTrue(bubblePointEnthalpies.length > 20);
    assertTrue(bubblePointVolumes.length > 20);
  }
}
