package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the unified PTPhaseEnvelopeMichelsen implementation.
 *
 * <p>
 * These tests verify that the Michelsen continuation-based phase envelope calculation produces
 * correct results for a variety of fluid systems: simple gases, natural gas mixtures, systems with
 * TBP fractions, UMRPRU mixing rules, and trace water.
 * </p>
 */
public class PTPhaseEnvelopeMichelsenTest {

  /**
   * Test with a simple 3-component gas (N2/CO2/CH4). Verifies that the dew and bubble arrays have
   * reasonable length and that cricondenbar and cricondentherm are physically meaningful.
   */
  @Test
  void testSimpleGas() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    testOps.calcPTphaseEnvelopeMichelsen();

    double[] dewP = testOps.get("dewP");
    double[] dewT = testOps.get("dewT");
    double[] bubP = testOps.get("bubP");
    double[] bubT = testOps.get("bubT");

    assertNotNull(dewP, "dewP should not be null");
    assertNotNull(dewT, "dewT should not be null");
    assertTrue(dewP.length > 10, "Should have more than 10 dew points, got: " + dewP.length);
    assertTrue(dewT.length == dewP.length, "dewT and dewP should have same length");

    // Cricondenbar should be reasonable for this system (~47 bar)
    double[] cricondenbar = testOps.get("cricondenbar");
    assertNotNull(cricondenbar, "cricondenbar should not be null");
    assertTrue(cricondenbar[1] > 30.0,
        "Cricondenbar pressure should be > 30 bar, got: " + cricondenbar[1]);
    assertTrue(cricondenbar[1] < 100.0,
        "Cricondenbar pressure should be < 100 bar, got: " + cricondenbar[1]);

    // Critical point should be reasonable
    double[] cp = testOps.get("criticalPoint1");
    assertNotNull(cp, "criticalPoint1 should not be null");
    assertTrue(cp[0] > 150.0, "Critical temperature should be > 150 K, got: " + cp[0]);
    assertTrue(cp[0] < 250.0, "Critical temperature should be < 250 K, got: " + cp[0]);
  }

  /**
   * Test with a 9-component natural gas mixture. Verifies that both dew and bubble branches produce
   * sufficient points and that enthalpies and densities are available.
   */
  @Test
  void testNaturalGas() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
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

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeMichelsen();

    double[] dewT = testOps.get("dewT");
    double[] bubT = testOps.get("bubT");
    double[] dewH = testOps.get("dewH");
    double[] dewDens = testOps.get("dewDens");
    double[] bubH = testOps.get("bubH");
    double[] bubDens = testOps.get("bubDens");

    assertTrue(dewT.length > 15, "Should have > 15 dew points, got: " + dewT.length);
    assertTrue(bubT.length > 5, "Should have > 5 bubble points, got: " + bubT.length);

    // Enthalpy and density arrays should match temperature array lengths
    assertTrue(dewH.length == dewT.length, "dewH length should match dewT length");
    assertTrue(dewDens.length == dewT.length, "dewDens length should match dewT length");
    assertTrue(bubH.length == bubT.length, "bubH length should match bubT length");
    assertTrue(bubDens.length == bubT.length, "bubDens length should match bubT length");

    // Cricondenbar for this mixture should be ~60-90 bar
    double[] cricondenbar = testOps.get("cricondenbar");
    assertTrue(cricondenbar[1] > 40.0, "Cricondenbar should be > 40 bar, got: " + cricondenbar[1]);
    assertTrue(cricondenbar[1] < 200.0,
        "Cricondenbar should be < 200 bar, got: " + cricondenbar[1]);

    // Cricondentherm for this mixture
    double[] cricondentherm = testOps.get("cricondentherm");
    assertTrue(cricondentherm[0] > 200.0,
        "Cricondentherm should be > 200 K, got: " + cricondentherm[0]);
    assertTrue(cricondentherm[0] < 400.0,
        "Cricondentherm should be < 400 K, got: " + cricondentherm[0]);
  }

  /**
   * Test starting from bubble side (bubfirst=true). Verifies that the algorithm correctly traces
   * from the bubble point side.
   */
  @Test
  void testBubblePointFirst() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeMichelsen(true);

    double[] dewT = testOps.get("dewT");
    double[] bubT = testOps.get("bubT");

    // With bubfirst=true, dew/bubble naming follows convention
    assertNotNull(dewT, "dewT should not be null");
    assertNotNull(bubT, "bubT should not be null");
    assertTrue(dewT.length + bubT.length > 10,
        "Total points should be > 10, got: " + (dewT.length + bubT.length));
  }

  /**
   * Test with trace water component. This is a known problematic case for legacy implementations.
   */
  @Test
  void testWithWaterTrace() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.04);
    testSystem.addComponent("CO2", 0.06);
    testSystem.addComponent("methane", 0.80);
    testSystem.addComponent("water", 0.00000000001);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    assertDoesNotThrow(() -> testOps.calcPTphaseEnvelopeMichelsen());
  }

  /**
   * Test with TBP fractions. This tests the algorithm with heavy pseudo-components.
   */
  @Test
  void testWithTBPfractions() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.88);
    testSystem.addComponent("CO2", 5.7);
    testSystem.addComponent("methane", 86.89);
    testSystem.addComponent("ethane", 3.59);
    testSystem.addComponent("propane", 1.25);
    testSystem.addComponent("i-butane", 0.19);
    testSystem.addComponent("n-butane", 0.35);
    testSystem.addComponent("i-pentane", 0.12);
    testSystem.addComponent("n-pentane", 0.12);
    testSystem.addTBPfraction("C6", 0.15, 86.0 / 1000.0, 0.672);
    testSystem.addTBPfraction("C7", 0.2, 96.0 / 1000.0, 0.737);
    testSystem.addTBPfraction("C8", 0.22, 106.0 / 1000.0, 0.767);
    testSystem.addTBPfraction("C9", 0.13, 121.0 / 1000.0, 0.783);
    testSystem.addPlusFraction("C10+", 0.21, 172.0 / 1000.0, 0.818);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    testSystem.useVolumeCorrection(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    testOps.calcPTphaseEnvelopeMichelsen();

    double[] dewT = testOps.get("dewT");
    double[] bubT = testOps.get("bubT");

    assertTrue(dewT.length > 10, "Should have > 10 dew points with TBP, got: " + dewT.length);
    assertTrue(bubT.length > 5, "Should have > 5 bubble points with TBP, got: " + bubT.length);
  }

  /**
   * Test with UMRPRU mixing rule and many components.
   */
  @Test
  void testUMRPRU() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 50.0);
    fluid.addComponent("nitrogen", 2.5);
    fluid.addComponent("CO2", 4.5);
    fluid.addComponent("methane", 79.45);
    fluid.addComponent("ethane", 10);
    fluid.addComponent("propane", 2.5);
    fluid.addComponent("i-butane", 0.3);
    fluid.addComponent("n-butane", 0.5);
    fluid.addComponent("22-dim-C3", 0.01);
    fluid.addComponent("i-pentane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.addComponent("n-hexane", 0.05);
    fluid.addComponent("benzene", 0.02);
    fluid.addComponent("c-hexane", 0.02);
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("toluene", 0.01);
    fluid.addComponent("n-octane", 0.01);

    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

    ThermodynamicOperations testOps = new ThermodynamicOperations(fluid);
    testOps.TPflash();

    testOps.calcPTphaseEnvelopeMichelsen();

    double[] dewT = testOps.get("dewT");
    double[] bubT = testOps.get("bubT");
    double[] bubH = testOps.get("bubH");
    double[] bubDens = testOps.get("bubDens");

    assertTrue(dewT.length > 15, "Should have > 15 dew points with UMRPRU, got: " + dewT.length);
    assertTrue(bubT.length > 10, "Should have > 10 bubble points with UMRPRU, got: " + bubT.length);
    assertTrue(bubH.length > 10,
        "Should have > 10 bubble enthalpies with UMRPRU, got: " + bubH.length);
    assertTrue(bubDens.length > 10,
        "Should have > 10 bubble densities with UMRPRU, got: " + bubDens.length);
  }

  /**
   * Test with custom pressure bounds and starting pressure.
   */
  @Test
  void testWithCustomBounds() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeMichelsen(false, 0.5);

    double[] dewP = testOps.get("dewP");
    assertNotNull(dewP, "dewP should not be null");
    assertTrue(dewP.length > 5, "Should have > 5 dew points, got: " + dewP.length);
  }

  /**
   * Test that all get() keys return non-null arrays for a successful envelope calculation.
   */
  @Test
  void testAllGetKeys() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeMichelsen();

    // All standard keys should return non-null
    String[] keys = {"dewT", "dewP", "bubT", "bubP", "dewH", "dewDens", "dewS", "bubH", "bubDens",
        "bubS", "cricondentherm", "cricondenthermX", "cricondenthermY", "cricondenbar",
        "cricondenbarX", "cricondenbarY", "criticalPoint1", "criticalPoint2"};
    for (String key : keys) {
      assertNotNull(testOps.get(key), "get(\"" + key + "\") should not be null");
    }
  }

  /**
   * Test that the dew point temperatures are in Kelvin (>100 K for hydrocarbon systems).
   */
  @Test
  void testTemperatureUnits() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 0.90);
    testSystem.addComponent("ethane", 0.10);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeMichelsen();

    double[] dewT = testOps.get("dewT");
    assertTrue(dewT.length > 0, "Should have at least one dew point");
    for (double t : dewT) {
      assertTrue(t > 100.0, "Temperature should be in Kelvin (>100 K), got: " + t);
      assertTrue(t < 500.0, "Temperature should be reasonable (<500 K), got: " + t);
    }
  }

  /**
   * Test that pressures are positive and in bara.
   */
  @Test
  void testPressureValues() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 0.90);
    testSystem.addComponent("ethane", 0.10);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeMichelsen();

    double[] dewP = testOps.get("dewP");
    assertTrue(dewP.length > 0, "Should have at least one dew point");
    for (double p : dewP) {
      assertTrue(p > 0.0, "Pressure should be positive, got: " + p);
      assertTrue(p < 1000.0, "Pressure should be reasonable (<1000 bar), got: " + p);
    }
  }

  /**
   * Test that cricondentherm X and Y compositions have physical values.
   */
  @Test
  void testCricondenthermCompositions() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeMichelsen();

    double[] cricondenthermX = testOps.get("cricondenthermX");
    double[] cricondenthermY = testOps.get("cricondenthermY");

    // Check that compositions sum approximately to 1
    double sumX = 0.0;
    double sumY = 0.0;
    for (int i = 0; i < testSystem.getPhase(0).getNumberOfComponents(); i++) {
      sumX += cricondenthermX[i];
      sumY += cricondenthermY[i];
    }
    assertTrue(Math.abs(sumX - 1.0) < 0.01,
        "Cricondentherm X compositions should sum to ~1, got: " + sumX);
    assertTrue(Math.abs(sumY - 1.0) < 0.01,
        "Cricondentherm Y compositions should sum to ~1, got: " + sumY);
  }

  /**
   * Compare Michelsen and legacy (New2) implementations on the same system. Both should produce
   * similar cricondenbar and cricondentherm values.
   */
  @Test
  void testConsistencyWithLegacy() {
    // Run with legacy implementation
    neqsim.thermo.system.SystemInterface system1 =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    system1.addComponent("nitrogen", 0.88);
    system1.addComponent("CO2", 5.7);
    system1.addComponent("methane", 86.89);
    system1.addComponent("ethane", 3.59);
    system1.addComponent("propane", 1.25);
    system1.addComponent("i-butane", 0.19);
    system1.addComponent("n-butane", 0.35);
    system1.addComponent("i-pentane", 0.12);
    system1.addComponent("n-pentane", 0.12);
    system1.setMixingRule("classic");
    ThermodynamicOperations ops1 = new ThermodynamicOperations(system1);
    ops1.calcPTphaseEnvelope2();
    double[] cricoBar1 = ops1.get("cricondenbar");
    double[] cricoTherm1 = ops1.get("cricondentherm");

    // Run with Michelsen implementation
    neqsim.thermo.system.SystemInterface system2 =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    system2.addComponent("nitrogen", 0.88);
    system2.addComponent("CO2", 5.7);
    system2.addComponent("methane", 86.89);
    system2.addComponent("ethane", 3.59);
    system2.addComponent("propane", 1.25);
    system2.addComponent("i-butane", 0.19);
    system2.addComponent("n-butane", 0.35);
    system2.addComponent("i-pentane", 0.12);
    system2.addComponent("n-pentane", 0.12);
    system2.setMixingRule("classic");
    ThermodynamicOperations ops2 = new ThermodynamicOperations(system2);
    ops2.calcPTphaseEnvelopeMichelsen();
    double[] cricoBar2 = ops2.get("cricondenbar");
    double[] cricoTherm2 = ops2.get("cricondentherm");

    // Cricondenbar should match within 5%
    double relDiffBar = Math.abs(cricoBar1[1] - cricoBar2[1]) / cricoBar1[1];
    assertTrue(relDiffBar < 0.05, "Cricondenbar P should match legacy within 5%, legacy="
        + cricoBar1[1] + " new=" + cricoBar2[1] + " relDiff=" + relDiffBar);

    // Cricondentherm should match within 5%
    double relDiffTherm = Math.abs(cricoTherm1[0] - cricoTherm2[0]) / cricoTherm1[0];
    assertTrue(relDiffTherm < 0.05, "Cricondentherm T should match legacy within 5%, legacy="
        + cricoTherm1[0] + " new=" + cricoTherm2[0] + " relDiff=" + relDiffTherm);
  }
}
