package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class PTPhaseEnvelopeTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
  }

  /**
   * Test method for
   * {@link neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps.pTphaseEnvelope}.
   */
  @Test
  void testDewP() {
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");

    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    testOps.calcPTphaseEnvelope();
    double[] dewPointPressures = testOps.get("dewP");

    assertTrue(dewPointPressures.length > 10);
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

    testOps.calcPTphaseEnvelope();
    double[] dewPointPressures = testOps.get("dewP");
    double[] dewPointTemperautres = testOps.get("dewT");
    double[] bubblePointPressures = testOps.get("bubP");
    double[] bubblePointTemperautres = testOps.get("bubT");
    assertTrue(bubblePointPressures.length > 20);
    assertTrue(bubblePointTemperautres.length > 10);
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
    double[] dewPointPressures = testOps.get("dewP");
    double[] dewPointTemperautres = testOps.get("dewT");
    double[] bubblePointPressures = testOps.get("bubP");
    double[] bubblePointTemperautres = testOps.get("bubT");
    double[] cricondenbar = testOps.get("cricondenbar");
    double[] criticalPoint1 = testOps.get("criticalPoint1");


    assertTrue(dewPointTemperautres.length > 20);
    assertTrue(bubblePointTemperautres.length > 10);

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
    double[] dewPointPressures = testOps.get("dewP");
    double[] dewPointTemperautres = testOps.get("dewT");
    double[] bubblePointPressures = testOps.get("bubP");
    double[] bubblePointTemperautres = testOps.get("bubT");
    double[] cricondenbar = testOps.get("cricondenbar");
    double[] criticalPoint1 = testOps.get("criticalPoint1");


    assertTrue(dewPointTemperautres.length > 20);
    assertTrue(bubblePointTemperautres.length > 10);

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
    double[] dewPointTemperautres = testOps.get("dewT");
    double[] bubblePointPressures = testOps.get("bubP");
    double[] bubblePointTemperautres = testOps.get("bubT");
    double[] bubblePointEnthalpies = testOps.get("bubH");
    double[] bubblePointVolumes = testOps.get("bubDens");
    double[] cricondenbar = testOps.get("cricondenbar");
    double[] criticalPoint1 = testOps.get("criticalPoint1");

    assertTrue(dewPointTemperautres.length > 20);
    assertTrue(bubblePointTemperautres.length > 20);
    assertTrue(bubblePointEnthalpies.length > 20);
    assertTrue(bubblePointVolumes.length > 20);

  }
}
