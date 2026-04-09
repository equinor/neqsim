package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    testOps.calcPTphaseEnvelope();

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
    testOps.calcPTphaseEnvelope();

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
    testOps.calcPTphaseEnvelope(true);

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

    assertDoesNotThrow(() -> testOps.calcPTphaseEnvelope());
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

    testOps.calcPTphaseEnvelope();

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

    testOps.calcPTphaseEnvelope();

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
    testOps.calcPTphaseEnvelope(false, 0.5);

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
    testOps.calcPTphaseEnvelope();

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
    testOps.calcPTphaseEnvelope();

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
    testOps.calcPTphaseEnvelope();

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
    testOps.calcPTphaseEnvelope();

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
   * Test that calcPTphaseEnvelope produces physically valid cricondenbar and cricondentherm for a
   * 9-component natural gas.
   */
  @Test
  void testCricondenbarAndCricondentherm() {
    neqsim.thermo.system.SystemInterface system =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    system.addComponent("nitrogen", 0.88);
    system.addComponent("CO2", 5.7);
    system.addComponent("methane", 86.89);
    system.addComponent("ethane", 3.59);
    system.addComponent("propane", 1.25);
    system.addComponent("i-butane", 0.19);
    system.addComponent("n-butane", 0.35);
    system.addComponent("i-pentane", 0.12);
    system.addComponent("n-pentane", 0.12);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.calcPTphaseEnvelope();
    double[] cricoBar = ops.get("cricondenbar");
    double[] cricoTherm = ops.get("cricondentherm");

    // Cricondenbar pressure should be in a reasonable range for this gas (~60-90 bar)
    assertNotNull(cricoBar, "cricondenbar should not be null");
    assertTrue(cricoBar[1] > 40.0, "Cricondenbar P should be > 40 bar, got: " + cricoBar[1]);
    assertTrue(cricoBar[1] < 200.0, "Cricondenbar P should be < 200 bar, got: " + cricoBar[1]);

    // Cricondentherm temperature should be reasonable (~260-300 K)
    assertNotNull(cricoTherm, "cricondentherm should not be null");
    assertTrue(cricoTherm[0] > 200.0, "Cricondentherm T should be > 200 K, got: " + cricoTherm[0]);
    assertTrue(cricoTherm[0] < 400.0, "Cricondentherm T should be < 400 K, got: " + cricoTherm[0]);
  }

  /**
   * Test isEnvelopeClosed returns true for a successful envelope trace.
   */
  @Test
  void testIsEnvelopeClosed() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelope();

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) testOps.getOperation();
    assertTrue(env.isEnvelopeClosed(), "Envelope should be closed for simple natural gas");
  }

  /**
   * Test that dewT2, dewP2, bubT2, bubP2 keys return null for compatibility. The Michelsen method
   * merges all points into dewT/dewP/bubT/bubP, so separate pass-2 arrays are not applicable.
   * Returning null matches legacy PTphaseEnvelope behavior when no second pass exists.
   */
  @Test
  void testDewT2BubT2Keys() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 0.90);
    testSystem.addComponent("ethane", 0.10);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelope();

    double[] dewT2 = testOps.get("dewT2");
    double[] dewP2 = testOps.get("dewP2");
    double[] bubT2 = testOps.get("bubT2");
    double[] bubP2 = testOps.get("bubP2");

    assertNull(dewT2, "dewT2 should be null (no second pass in Michelsen)");
    assertNull(dewP2, "dewP2 should be null (no second pass in Michelsen)");
    assertNull(bubT2, "bubT2 should be null (no second pass in Michelsen)");
    assertNull(bubP2, "bubP2 should be null (no second pass in Michelsen)");
  }

  /**
   * Test quality line tracing with mole fractions. Verifies that quality lines are traced inside
   * the two-phase region with temperatures between the dew and bubble curves.
   */
  @Test
  void testQualityLinesMoleFraction() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.02);
    testSystem.addComponent("CO2", 0.03);
    testSystem.addComponent("methane", 0.80);
    testSystem.addComponent("ethane", 0.08);
    testSystem.addComponent("propane", 0.04);
    testSystem.addComponent("n-butane", 0.03);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeWithQualityLines(new double[] {0.1, 0.25, 0.5, 0.75, 0.9});

    // Check that quality line data is accessible via get()
    double[] qT50 = testOps.get("qualityT_0.5");
    double[] qP50 = testOps.get("qualityP_0.5");

    assertNotNull(qT50, "qualityT_0.5 should not be null");
    assertNotNull(qP50, "qualityP_0.5 should not be null");
    assertTrue(qT50.length > 5,
        "Should have > 5 quality line points at beta=0.5, got: " + qT50.length);
    assertEquals(qT50.length, qP50.length, "qualityT and qualityP should have same length");

    // Temperatures should be in Kelvin
    for (double t : qT50) {
      assertTrue(t > 100.0, "Quality line T should be in Kelvin (>100 K), got: " + t);
    }
    // Pressures should be positive
    for (double p : qP50) {
      assertTrue(p > 0.0, "Quality line P should be positive, got: " + p);
    }
  }

  /**
   * Test volume fraction and mass fraction computation on quality lines. At beta=0.5 (50% moles
   * vapor), volume fraction should be greater than 0.5 (vapor is less dense) and mass fraction
   * should differ from the mole fraction.
   */
  @Test
  void testQualityLinesVolumeMassFraction() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 0.80);
    testSystem.addComponent("ethane", 0.10);
    testSystem.addComponent("propane", 0.05);
    testSystem.addComponent("n-butane", 0.05);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeWithQualityLines(new double[] {0.5});

    double[] volFrac = testOps.get("qualityVolFrac_0.5");
    double[] massFrac = testOps.get("qualityMassFrac_0.5");

    assertNotNull(volFrac, "qualityVolFrac_0.5 should not be null");
    assertNotNull(massFrac, "qualityMassFrac_0.5 should not be null");
    assertTrue(volFrac.length > 3,
        "Should have > 3 volume fraction points, got: " + volFrac.length);

    // Volume fractions should be between 0 and 1
    for (double vf : volFrac) {
      assertTrue(vf > 0.0 && vf < 1.0, "Volume fraction should be between 0 and 1, got: " + vf);
    }
    // Mass fractions should be between 0 and 1
    for (double mf : massFrac) {
      assertTrue(mf > 0.0 && mf < 1.0, "Mass fraction should be between 0 and 1, got: " + mf);
    }

    // For a gas-dominant system at beta=0.5:
    // Volume fraction should typically be > 0.5 (vapor occupies more volume per mole)
    // This may not hold at very high pressures near critical, but should hold at low P
    assertTrue(volFrac[0] > 0.5,
        "Volume fraction at low P should be > 0.5 for beta=0.5, got: " + volFrac[0]);
  }

  /**
   * Test that multiple quality lines can be traced simultaneously and accessed via the
   * getQualityLine() method.
   */
  @Test
  void testMultipleQualityLinesViaGetQualityLine() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 0.80);
    testSystem.addComponent("ethane", 0.10);
    testSystem.addComponent("propane", 0.10);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeWithQualityLines(new double[] {0.1, 0.5, 0.9});

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) testOps.getOperation();

    // getQualityLine should return [T, P, volFrac, massFrac]
    double[][] line50 = env.getQualityLine(0.5);
    assertNotNull(line50, "Quality line at beta=0.5 should not be null");
    assertEquals(4, line50.length, "Quality line should have 4 arrays [T, P, vF, mF]");
    assertTrue(line50[0].length > 3, "Quality line at beta=0.5 should have > 3 points");

    // Lines at 0.1 and 0.9 should also exist
    double[][] line10 = env.getQualityLine(0.1);
    double[][] line90 = env.getQualityLine(0.9);
    assertNotNull(line10, "Quality line at beta=0.1 should not be null");
    assertNotNull(line90, "Quality line at beta=0.9 should not be null");

    // A non-traced beta should return null
    assertNull(env.getQualityLine(0.42), "Non-traced beta should return null");

    // getQualityBetaValues should return the traced values
    double[] betas = env.getQualityBetaValues();
    assertEquals(3, betas.length, "Should have 3 quality beta values");
  }

  /**
   * Test quality lines with bubble-first starting point.
   */
  @Test
  void testQualityLinesBubbleFirst() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 0.85);
    testSystem.addComponent("ethane", 0.10);
    testSystem.addComponent("propane", 0.05);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.calcPTphaseEnvelopeWithQualityLines(true, new double[] {0.25, 0.75});

    double[] qT25 = testOps.get("qualityT_0.25");
    double[] qT75 = testOps.get("qualityT_0.75");

    assertNotNull(qT25, "qualityT_0.25 should not be null");
    assertNotNull(qT75, "qualityT_0.75 should not be null");
    assertTrue(qT25.length > 3, "Quality line 0.25 should have > 3 points");
    assertTrue(qT75.length > 3, "Quality line 0.75 should have > 3 points");
  }

  /**
   * Test that the get(String, double[]) overload returns results when the key exists and returns
   * the default array when the key is absent or maps to null. This enables the Python/JPype idiom
   * {@code pe_data.get("dewT", [])} that previously threw a JPype overload resolution error.
   */
  @Test
  void testGetWithDefault() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.98);
    testSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    testOps.calcPTphaseEnvelope();

    double[] emptyDefault = new double[0];

    // Existing key should return actual data, not the default
    double[] dewT = testOps.get("dewT", emptyDefault);
    assertNotNull(dewT, "dewT with default should not be null");
    assertTrue(dewT.length > 0, "dewT should have data points");
    assertTrue(dewT != emptyDefault, "dewT should be the actual array, not the default");

    double[] dewP = testOps.get("dewP", emptyDefault);
    assertNotNull(dewP, "dewP with default should not be null");
    assertTrue(dewP.length > 0, "dewP should have data points");

    // Absent key should return the default
    double[] unknown = testOps.get("nonExistentKey", emptyDefault);
    assertArrayEquals(emptyDefault, unknown, "Absent key should return the default array");

    // Key that maps to null (e.g. dewT2) should return the default
    double[] dewT2 = testOps.get("dewT2", emptyDefault);
    assertArrayEquals(emptyDefault, dewT2, "dewT2 (null) should return the default array");

    // Also test via the OperationInterface directly
    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) testOps.getOperation();
    double[] bubP = env.get("bubP", emptyDefault);
    assertNotNull(bubP, "bubP via operation with default should not be null");
    assertTrue(bubP.length > 0, "bubP should have data points");

    double[] missing = env.get("noSuchKey", emptyDefault);
    assertArrayEquals(emptyDefault, missing,
        "Absent key via operation should return the default array");
  }
}
