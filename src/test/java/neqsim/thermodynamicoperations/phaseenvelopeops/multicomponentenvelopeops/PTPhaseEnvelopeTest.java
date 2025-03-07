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
    double[] expectedDewPointPressures = new double[] {1.1051709180756477, 1.2214027581601699,
        1.3498588075760032, 1.4918246976412703, 1.6652911949458864, 1.8794891289619104,
        2.14181312275025, 2.46908641231418, 2.8811970189747984, 3.4047799976139075,
        4.075230307874492, 4.938583914870001, 6.051801019586493, 7.47730469546273,
        9.260793952051543, 11.36410118528208, 13.480106047577777, 14.534237766293936,
        13.607498029406711, 11.181207439509738, 9.18948704048801, 9.612827246459416,
        10.706126846063874, 12.501491987759975, 15.075672692089858, 18.51283799420163,
        23.33037829633385, 29.713197110310297, 37.25532259549187, 43.66080565660384,
        45.03450029736208, 45.79022878540134, 46.405707358783545, 46.804819646870286,
        46.89430062856313, 46.90382666699785};
    // System.out.println(java.util.Arrays.toString(dewPointPressures));
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
