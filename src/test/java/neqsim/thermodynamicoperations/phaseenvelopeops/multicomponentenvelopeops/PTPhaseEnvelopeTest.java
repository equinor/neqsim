
package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PTPhaseEnvelopeTest {
  @Test
  void testWithTBPfraction() {
    // Test addTBPfraction functionality
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemUMRPRUMCEos(280.0, 10.0);
    java.util.Map<String, Double> compoundData = new java.util.LinkedHashMap<>();
    compoundData.put("ethane", 2.45516);
    compoundData.put("methane", 89.26002);
    compoundData.put("propane", 0.38468);
    compoundData.put("i-butane", 0.14674);
    compoundData.put("n-butane", 0.09195);
    compoundData.put("22-dim-C3", 0.00254);
    compoundData.put("i-pentane", 0.07672);
    compoundData.put("n-pentane", 0.03854);

    for (java.util.Map.Entry<String, Double> entry : compoundData.entrySet()) {
      feedGas.addComponent(entry.getKey(), entry.getValue());
    }
    // feedGas.addTBPfraction("C10 (pseudo)", 0.005, 0.15, 0.75);
    feedGas.setMixingRule("HV", "UNIFAC_UMRPRU");
    feedGas.setPressure(140.0, "bara");
    feedGas.setTemperature(0.0, "C");

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(feedGas);
    ops.TPflash();

    // Print summary of the system (replace with assertions as needed)
    // feedGas.display();
    // Optionally, add assertions to check phase, composition, etc.
    // assertTrue(feedGas.getPressure() > 100.0);
    // assertTrue(feedGas.getTemperature() < 10.0);
    // assertTrue(feedGas.getNumberOfComponents() >= compoundData.size() + 1); // TBP fraction added
  }

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

    assertDoesNotThrow(() -> testOps.calcPTphaseEnvelope());
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
    testSystem.addComponent("22-DM-C5", 0.01);
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
    // fluid0_HC.addComponent("2.2-DM-C7", 0.01);


    fluid0_HC.setMixingRule("HV", "UNIFAC_UMRPRU");
    testOps = new ThermodynamicOperations(fluid0_HC);
    testOps.TPflash();

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

  @Test
  void testFailingCase3() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 50.0);
    fluid.addComponent("ethane", 2.45516);
    fluid.addComponent("methane", 89.26002);
    fluid.addComponent("propane", 0.38468);
    fluid.addComponent("i-butane", 0.14674);
    fluid.addComponent("n-butane", 0.09195);
    fluid.addComponent("22-dim-C3", 0.00254);
    fluid.addComponent("i-pentane", 0.07672);
    fluid.addComponent("n-pentane", 0.03854);
    fluid.addComponent("nC10", 0.3854);
    fluid.addComponent("22-dim-C4", 0.00334);
    fluid.addComponent("c-C5", 0.00961);
    fluid.addComponent("23-dim-C4", 0.00516);
    fluid.addComponent("2-m-C5", 0.02051);
    fluid.addComponent("3-m-C5", 0.0126);
    fluid.addComponent("n-hexane", 0.02039);
    fluid.addComponent("22-DM-C5", 0.001);
    fluid.addComponent("M-cy-C5", 0.02885);
    fluid.addComponent("24-DM-C5", 0.00165);
    fluid.addComponent("223-TM-C4", 0.00041);
    fluid.addComponent("benzene", 0.0017);
    fluid.addComponent("33-DM-C5", 0.00058);
    fluid.addComponent("c-hexane", 0.03273);
    fluid.addComponent("2-M-C6", 0.00395);
    fluid.addComponent("11-DM-cy-C5", 0.00171);
    fluid.addComponent("3-M-C6", 0.0059);
    fluid.addComponent("cis-13-DM-cy-C5", 0.00392);
    fluid.addComponent("trans-13-DM-cy-C5", 0.00341);
    fluid.addComponent("trans-12-DM-cy-C5", 0.00663);
    fluid.addComponent("n-heptane", 0.00688);
    fluid.addComponent("M-cy-C6", 0.03824);
    fluid.addComponent("113-TM-cy-C5", 0.00119);
    fluid.addComponent("E-cy-C5", 0.00179);
    fluid.addComponent("25-DM-C6", 0.00047);
    fluid.addComponent("24-DM-C6", 0.00082);
    fluid.addComponent("33-DM-C6", 0.00026);
    fluid.addComponent("234-TM-C5", 0.00013);
    fluid.addComponent("toluene", 0.00427);
    fluid.addComponent("2-M-C7", 0.00105);
    fluid.addComponent("cis-13-DM-cy-C6", 0.00681);
    fluid.addComponent("3-M-C7", 0.00216);
    fluid.addComponent("trans-12-DM-cy-C6", 0.00234);
    fluid.addComponent("n-octane", 0.00459);
    fluid.addComponent("cis-12-DM-cy-C6", 0.0001);
    fluid.addComponent("ethylcyclohexane", 0.00356);
    fluid.addComponent("ethylbenzene", 0.00045);
    fluid.addComponent("m-Xylene", 0.00119);
    fluid.addComponent("p-Xylene", 0.0003);
    fluid.addComponent("4-M-C8", 0.00018);
    fluid.addComponent("2-M-C8", 0.00019);
    fluid.addComponent("3-M-C8", 0.00023);
    fluid.addComponent("o-Xylene", 0.00052);
    fluid.addComponent("n-nonane", 0.0024);
    fluid.addComponent("nC10", 0.00018);
    fluid.addComponent("nC11", 0.00003);
    fluid.addComponent("CO2", 0.58374);
    fluid.addComponent("oxygen", 0.07139);
    fluid.addComponent("nitrogen", 4.67105);
    fluid.addComponent("c-C7", 0.00518);
    fluid.addComponent("c-C8", 0.00321);

    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

    testOps = new ThermodynamicOperations(fluid);
    testOps.TPflash();

    testOps.calcPTphaseEnvelopeNew3(1.0, 250, -50, 150, 15, 5);
    Object op = testOps.getOperation();
    List<Double> pressurePhaseEnvelope = ((PTphaseEnvelopeNew3) op).getPressurePhaseEnvelope();
    List<Double> temperaturePhaseEnvelope =
        ((PTphaseEnvelopeNew3) op).getTemperaturePhaseEnvelope();
    assertTrue(pressurePhaseEnvelope.size() > 5,
        "pressurePhaseEnvelope should have more than 5 points");
    assertTrue(temperaturePhaseEnvelope.size() > 5,
        "temperaturePhaseEnvelope should have more than 5 points");

    double cricondenbar = ((PTphaseEnvelopeNew3) op).getCricondenbar();
    double cricondentherm = ((PTphaseEnvelopeNew3) op).getCricondentherm();
    assertTrue(Math.abs(cricondenbar - 211) <= 5,
        "cricondenbar should be within 211±5, got: " + cricondenbar);
    assertTrue(Math.abs(cricondentherm - 93) <= 3,
        "cricondentherm should be within 93±3, got: " + cricondentherm);

    // Optionally, add assertions or print statements to check bettaMatrix

    // double[] dewPointPressures = testOps.get("dewP");
    // double[] dewPointTemperatures = testOps.get("dewT");
    // double[] bubblePointPressures = testOps.get("bubP");
    // double[] bubblePointTemperatures = testOps.get("bubT");
    // double[] bubblePointEnthalpies = testOps.get("bubH");
    // double[] bubblePointVolumes = testOps.get("bubDens");

    // assertTrue(dewPointTemperatures.length > 20);
    // assertTrue(bubblePointTemperatures.length > 20);
    // assertTrue(bubblePointEnthalpies.length > 20);
    // assertTrue(bubblePointVolumes.length > 20);
  }

  @Test
  void testFailingCase4() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 50.0);
    fluid.addComponent("ethane", 2.45516);
    fluid.addComponent("methane", 89.26002);
    fluid.addComponent("propane", 0.38468);
    fluid.addComponent("i-butane", 0.14674);
    fluid.addComponent("n-butane", 0.09195);
    fluid.addComponent("22-dim-C3", 0.00254);
    fluid.addComponent("i-pentane", 0.07672);
    fluid.addComponent("n-pentane", 0.03854);
    fluid.addComponent("22-dim-C4", 0.00334);
    fluid.addComponent("c-C5", 0.00961);
    fluid.addComponent("23-dim-C4", 0.00516);
    fluid.addComponent("2-m-C5", 0.02051);
    fluid.addComponent("3-m-C5", 0.0126);
    fluid.addComponent("n-hexane", 0.02039);
    fluid.addComponent("22-DM-C5", 0.001);
    fluid.addComponent("M-cy-C5", 0.02885);
    fluid.addComponent("24-DM-C5", 0.00165);
    fluid.addComponent("223-TM-C4", 0.00041);
    fluid.addComponent("benzene", 0.0017);
    fluid.addComponent("33-DM-C5", 0.00058);
    fluid.addComponent("c-hexane", 0.03273);
    fluid.addComponent("2-M-C6", 0.00395);
    fluid.addComponent("11-DM-cy-C5", 0.00171);
    fluid.addComponent("3-M-C6", 0.0059);
    fluid.addComponent("cis-13-DM-cy-C5", 0.00392);
    fluid.addComponent("trans-13-DM-cy-C5", 0.00341);
    fluid.addComponent("trans-12-DM-cy-C5", 0.00663);
    fluid.addComponent("n-heptane", 0.00688);
    fluid.addComponent("M-cy-C6", 0.03824);
    fluid.addComponent("113-TM-cy-C5", 0.00119);
    fluid.addComponent("E-cy-C5", 0.00179);
    fluid.addComponent("25-DM-C6", 0.00047);
    fluid.addComponent("24-DM-C6", 0.00082);
    fluid.addComponent("33-DM-C6", 0.00026);
    fluid.addComponent("234-TM-C5", 0.00013);
    fluid.addComponent("toluene", 0.00427);
    fluid.addComponent("2-M-C7", 0.00105);
    fluid.addComponent("cis-13-DM-cy-C6", 0.00681);
    fluid.addComponent("3-M-C7", 0.00216);
    fluid.addComponent("trans-12-DM-cy-C6", 0.00234);
    fluid.addComponent("n-octane", 0.00459);
    fluid.addComponent("cis-12-DM-cy-C6", 0.0001);
    fluid.addComponent("ethylcyclohexane", 0.00356);
    fluid.addComponent("ethylbenzene", 0.00045);
    fluid.addComponent("m-Xylene", 0.00119);
    fluid.addComponent("p-Xylene", 0.0003);
    fluid.addComponent("4-M-C8", 0.00018);
    fluid.addComponent("2-M-C8", 0.00019);
    fluid.addComponent("3-M-C8", 0.00023);
    fluid.addComponent("o-Xylene", 0.00052);
    fluid.addComponent("n-nonane", 0.0024);
    fluid.addComponent("nC10", 0.00018);
    fluid.addComponent("nC11", 0.00003);
    fluid.addComponent("CO2", 0.58374);
    fluid.addComponent("oxygen", 0.07139);
    fluid.addComponent("nitrogen", 4.67105);
    fluid.addComponent("c-C7", 0.00518);
    fluid.addComponent("c-C8", 0.00321);
    // Add TBP fractions for C10 (pseudo) and C11 (pseudo)
    // fluid.addTBPfraction("C10 (pseudo)", 0.00373, 0.136, 0.787);
    // fluid.addTBPfraction("C11 (pseudo)", 0.00013, 0.15, 0.793);

    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    // testOps = new ThermodynamicOperations(fluid);
    // testOps.TPflash();
    // testOps.calcPTphaseEnvelope2();
    // double[] dewPointPressures = testOps.get("dewP");
    // double[] dewPointTemperatures = testOps.get("dewT");
    // double[] bubblePointPressures = testOps.get("bubP");
    // double[] bubblePointTemperatures = testOps.get("bubT");
    // double[] bubblePointEnthalpies = testOps.get("bubH");
    // double[] bubblePointVolumes = testOps.get("bubDens");

    // assertTrue(dewPointTemperatures.length > 20);
    // assertTrue(bubblePointTemperatures.length > 20);
    // assertTrue(bubblePointEnthalpies.length > 20);
    // assertTrue(bubblePointVolumes.length > 20);
  }



}
