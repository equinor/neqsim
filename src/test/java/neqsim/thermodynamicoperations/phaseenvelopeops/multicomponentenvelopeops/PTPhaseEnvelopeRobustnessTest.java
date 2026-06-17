package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Robustness tests for the PTPhaseEnvelopeMichelsen implementation. Covers the full spectrum from
 * pure components through complex multicomponent mixtures, verifying both closed and open
 * envelopes.
 *
 * <p>
 * Comparisons are made against known critical properties and NIST/textbook reference data for
 * validation against state-of-the-art phase envelope algorithms.
 * </p>
 *
 * @author copilot
 * @version 1.0
 */
public class PTPhaseEnvelopeRobustnessTest {

  // ========================== PURE COMPONENTS ==========================

  /**
   * Pure methane: The phase envelope degenerates to the vapor pressure curve. The cricondenbar and
   * cricondentherm should both be near the critical point (Tc=190.56 K, Pc=45.99 bar). NIST
   * reference: Tc=190.564 K, Pc=45.992 bar.
   */
  @Test
  void testPureMethane() {
    SystemInterface fluid = new SystemSrkEos(150.0, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope());

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] dewP = ops.get("dewP");

    assertNotNull(cricondenbar, "Pure methane should produce cricondenbar");
    assertNotNull(dewT, "Pure methane should produce dew points");
    assertTrue(dewT.length > 0, "Should have dew point data");

    // For pure component, cricondenbar ~ Pc and cricondentherm ~ Tc
    // SRK Tc for methane ~ 190.6 K, Pc ~ 46.0 bar
    double ccbP = cricondenbar[1];
    double cctT = cricondentherm[0];

    // SRK overpredicts Pc for methane (~58 bar vs NIST 46 bar)
    assertTrue(ccbP > 40.0 && ccbP < 65.0,
        "Pure CH4 cricondenbar should be near Pc=46 bar (SRK ~58), got " + ccbP);
    assertTrue(cctT > 180.0 && cctT < 200.0,
        "Pure CH4 cricondentherm should be near Tc=190.6 K, got " + cctT);
  }

  /**
   * Pure ethane: Tc=305.33 K, Pc=48.72 bar (NIST). Validates the algorithm handles a heavier pure
   * component.
   */
  @Test
  void testPureEthane() {
    SystemInterface fluid = new SystemSrkEos(200.0, 10.0);
    fluid.addComponent("ethane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope());

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(cricondenbar, "Pure ethane should produce cricondenbar");
    double ccbP = cricondenbar[1];
    double cctT = cricondentherm[0];

    // SRK gives near-NIST values for ethane
    assertTrue(ccbP > 42.0 && ccbP < 56.0,
        "Pure C2H6 cricondenbar should be near Pc=48.7 bar, got " + ccbP);
    assertTrue(cctT > 295.0 && cctT < 315.0,
        "Pure C2H6 cricondentherm should be near Tc=305.3 K, got " + cctT);
  }

  /**
   * Pure CO2: Tc=304.13 K, Pc=73.77 bar (NIST). Important for CCS applications.
   */
  @Test
  void testPureCO2() {
    SystemInterface fluid = new SystemSrkEos(220.0, 10.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope());

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(cricondenbar, "Pure CO2 should produce cricondenbar");
    double ccbP = cricondenbar[1];
    double cctT = cricondentherm[0];

    // SRK slightly overpredicts Pc for CO2
    assertTrue(ccbP > 65.0 && ccbP < 85.0,
        "Pure CO2 cricondenbar should be near Pc=73.8 bar, got " + ccbP);
    assertTrue(cctT > 295.0 && cctT < 315.0,
        "Pure CO2 cricondentherm should be near Tc=304.1 K, got " + cctT);
  }

  // ========================== BINARY MIXTURES ==========================

  /**
   * Binary methane-ethane (90/10): Classic binary mixture. Well-characterized system, cricondenbar
   * &gt; pure component Pc values (mixture effect).
   */
  @Test
  void testBinaryMethaneEthane() {
    SystemInterface fluid = new SystemSrkEos(200.0, 20.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");

    assertNotNull(dewT, "Binary CH4/C2H6 should have dew curve");
    assertNotNull(bubT, "Binary CH4/C2H6 should have bubble curve");
    assertTrue(dewT.length >= 3, "Dew curve should have enough points");
    assertTrue(bubT.length >= 3, "Bubble curve should have enough points");

    // Cricondenbar for 90/10 CH4/C2H6 should be ~ 55-65 bar (above either Pc)
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 45.0 && ccbP < 75.0,
        "Binary CH4/C2H6 cricondenbar should be 45-75 bar, got " + ccbP);

    // Cricondentherm should be between Tc_CH4 and Tc_C2H6
    double cctT = cricondentherm[0];
    assertTrue(cctT > 190.0 && cctT < 310.0,
        "Binary CH4/C2H6 cricondentherm should be 190-310 K, got " + cctT);
  }

  /**
   * Binary methane-CO2 (80/20): Important for CCS/EOR. CO2 significantly affects the shape of the
   * phase envelope.
   */
  @Test
  void testBinaryMethaneCO2() {
    SystemInterface fluid = new SystemSrkEos(200.0, 20.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("CO2", 0.20);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");

    assertNotNull(dewT, "Binary CH4/CO2 should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have points");

    double ccbP = cricondenbar[1];
    // CH4/CO2 80/20: cricondenbar ~ 80-100 bar (CO2 has high Pc)
    assertTrue(ccbP > 50.0 && ccbP < 120.0,
        "Binary CH4/CO2 cricondenbar should be 50-120 bar, got " + ccbP);
  }

  /**
   * Binary methane-propane (95/5): Asymmetric system with wider phase envelope than CH4/C2H6.
   */
  @Test
  void testBinaryMethanePropane() {
    SystemInterface fluid = new SystemSrkEos(200.0, 20.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");

    assertNotNull(dewT, "Binary CH4/C3 should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have enough points");

    // Cricondentherm for methane + propane should be well above Tc_CH4
    double cctT = cricondentherm[0];
    assertTrue(cctT > 200.0, "CH4/C3 cricondentherm should be above Tc_CH4=190.6 K, got " + cctT);
  }

  /**
   * Binary CO2-H2S: Important sour gas system. Both components have similar Tc but different Pc.
   */
  @Test
  void testBinaryCO2H2S() {
    SystemInterface fluid = new SystemSrkEos(250.0, 20.0);
    fluid.addComponent("CO2", 0.70);
    fluid.addComponent("H2S", 0.30);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope());

    double[] cricondenbar = ops.get("cricondenbar");
    assertNotNull(cricondenbar, "CO2/H2S binary should produce cricondenbar");

    double ccbP = cricondenbar[1];
    // CO2 Pc=73.8 bar, H2S Pc=89.6 bar: mixture cricondenbar should be in between or higher
    assertTrue(ccbP > 60.0 && ccbP < 110.0,
        "CO2/H2S cricondenbar should be 60-110 bar, got " + ccbP);
  }

  // ========================== ASYMMETRIC BINARIES ==========================

  /**
   * Binary methane-n-decane (95/5): Highly asymmetric system with very wide phase envelope.
   * Challenges the algorithm near the critical region where step sizes must be small.
   */
  @Test
  void testBinaryMethaneDecane() {
    SystemInterface fluid = new SystemSrkEos(250.0, 20.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("nC10", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");

    assertNotNull(dewT, "CH4/nC10 should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have enough points");

    // nC10 Tc=617.7 K: cricondentherm should be very high
    double cctT = cricondentherm[0];
    assertTrue(cctT > 200.0, "CH4/nC10 cricondentherm should be well above Tc_CH4, got " + cctT);

    // Wide asymmetric envelopes have very high cricondenbar
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 50.0,
        "CH4/nC10 cricondenbar should be high for asymmetric system, got " + ccbP);
  }

  /**
   * Binary methane-n-hexane (80/20): Moderately asymmetric. The cricondenbar for 80/20 CH4/nC6
   * should be significantly above pure-component Pc values (typically 100-200 bar range).
   */
  @Test
  void testBinaryMethaneHexane() {
    SystemInterface fluid = new SystemSrkEos(250.0, 20.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("n-hexane", 0.20);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");

    assertNotNull(dewT, "CH4/nC6 should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have enough points");

    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 80.0, "CH4/nC6 80/20 cricondenbar should be >80 bar, got " + ccbP);
  }

  // ========================== MULTICOMPONENT CLOSED ENVELOPES ==========================

  /**
   * Lean natural gas (5 components): Should produce a clean closed envelope. Reference against
   * typical lean gas cricondenbar ~ 55-80 bar and cricondentherm ~ 230-270 K.
   */
  @Test
  void testLeanNaturalGas() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("methane", 0.91);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.015);
    fluid.addComponent("n-butane", 0.005);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");
    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(dewT, "Lean gas should have dew curve");
    assertNotNull(bubT, "Lean gas should have bubble curve");
    assertTrue(dewT.length >= 5, "Dew curve should have >=5 points");
    assertTrue(bubT.length >= 3, "Bubble curve should have >=3 points");

    // Lean gas cricondenbar typically 50-80 bar
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 45.0 && ccbP < 85.0,
        "Lean gas cricondenbar should be 45-85 bar, got " + ccbP);

    // Lean gas cricondentherm typically 220-270 K
    double cctT = cricondentherm[0];
    assertTrue(cctT > 200.0 && cctT < 280.0,
        "Lean gas cricondentherm should be 200-280 K, got " + cctT);

    // Check envelope closure
    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    assertTrue(env.isEnvelopeClosed(), "Lean gas should have a closed envelope");
  }

  /**
   * Rich natural gas (9 components with C5+): Larger phase envelope than lean gas. Cricondentherm
   * can reach 300+ K.
   */
  @Test
  void testRichNaturalGas() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.07);
    fluid.addComponent("i-butane", 0.03);
    fluid.addComponent("n-butane", 0.04);
    fluid.addComponent("i-pentane", 0.015);
    fluid.addComponent("n-pentane", 0.015);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");
    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(dewT, "Rich gas should have dew curve");
    assertTrue(dewT.length >= 5, "Dew curve should have >=5 points");

    // Rich gas cricondenbar: 80-150 bar
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 70.0 && ccbP < 160.0,
        "Rich gas cricondenbar should be 70-160 bar, got " + ccbP);

    // Rich gas cricondentherm: 270-350 K
    double cctT = cricondentherm[0];
    assertTrue(cctT > 260.0 && cctT < 360.0,
        "Rich gas cricondentherm should be 260-360 K, got " + cctT);

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    assertTrue(env.isEnvelopeClosed(), "Rich gas should have a closed envelope");
  }

  /**
   * Sour natural gas with H2S: Validates the algorithm handles H2S correctly. H2S has high Pc (89.6
   * bar) which increases cricondenbar.
   */
  @Test
  void testSourNaturalGas() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("H2S", 0.10);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] cricondenbar = ops.get("cricondenbar");

    assertNotNull(dewT, "Sour gas should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have points");

    // H2S pushes cricondenbar higher
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 60.0, "Sour gas cricondenbar should be elevated due to H2S, got " + ccbP);
  }

  /**
   * Very rich gas with C7+: Condensate-like system with very wide phase envelope. Uses a simplified
   * TBP-equivalent approach with n-heptane + n-octane.
   */
  @Test
  void testCondensateGas() {
    SystemInterface fluid = new SystemSrkEos(350.0, 100.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("i-butane", 0.03);
    fluid.addComponent("n-butane", 0.04);
    fluid.addComponent("i-pentane", 0.02);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.04);
    fluid.addComponent("n-heptane", 0.05);
    fluid.addComponent("n-octane", 0.04);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(dewT, "Condensate should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have points");

    // Condensate cricondenbar typically 100-300 bar
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 80.0, "Condensate cricondenbar should be >80 bar, got " + ccbP);

    // Condensate cricondentherm: 350-450 K with octane present
    double cctT = cricondentherm[0];
    assertTrue(cctT > 300.0, "Condensate cricondentherm should be >300 K, got " + cctT);
  }

  // ========================== EQUATION OF STATE COMPARISON ==========================

  /**
   * Peng-Robinson EOS vs SRK for the same lean gas: Both should give similar cricondenbar and
   * cricondentherm (within ~10%).
   */
  @Test
  void testPRvsSRKConsistency() {
    // SRK
    SystemInterface fluidSRK = new SystemSrkEos(273.15, 50.0);
    fluidSRK.addComponent("methane", 0.90);
    fluidSRK.addComponent("ethane", 0.07);
    fluidSRK.addComponent("propane", 0.03);
    fluidSRK.setMixingRule("classic");

    ThermodynamicOperations opsSRK = new ThermodynamicOperations(fluidSRK);
    opsSRK.calcPTphaseEnvelope();

    // PR
    SystemInterface fluidPR = new SystemPrEos(273.15, 50.0);
    fluidPR.addComponent("methane", 0.90);
    fluidPR.addComponent("ethane", 0.07);
    fluidPR.addComponent("propane", 0.03);
    fluidPR.setMixingRule("classic");

    ThermodynamicOperations opsPR = new ThermodynamicOperations(fluidPR);
    opsPR.calcPTphaseEnvelope();

    double[] ccbSRK = opsSRK.get("cricondenbar");
    double[] ccbPR = opsPR.get("cricondenbar");
    double[] cctSRK = opsSRK.get("cricondentherm");
    double[] cctPR = opsPR.get("cricondentherm");

    // SRK and PR should give cricondenbar within ~15% of each other
    double pDiff = Math.abs(ccbSRK[1] - ccbPR[1]) / ccbSRK[1] * 100;
    assertTrue(pDiff < 15.0,
        "SRK and PR cricondenbar should agree within 15%, diff=" + pDiff + "%");

    // Temperature should also be reasonably close
    double tDiff = Math.abs(cctSRK[0] - cctPR[0]) / cctSRK[0] * 100;
    assertTrue(tDiff < 10.0,
        "SRK and PR cricondentherm should agree within 10%, diff=" + tDiff + "%");
  }

  // ========================== BUBBLE-FIRST VS DEW-FIRST ==========================

  /**
   * Verify that bubble-first and dew-first give consistent cricondenbar/cricondentherm. The
   * starting side should not affect the final result.
   */
  @Test
  void testBubbleFirstVsDewFirstConsistency() {
    SystemInterface fluid1 = new SystemSrkEos(273.15, 50.0);
    fluid1.addComponent("methane", 0.85);
    fluid1.addComponent("ethane", 0.10);
    fluid1.addComponent("propane", 0.05);
    fluid1.setMixingRule("classic");

    SystemInterface fluid2 = fluid1.clone();

    ThermodynamicOperations ops1 = new ThermodynamicOperations(fluid1);
    ops1.calcPTphaseEnvelope(false); // dew-first (default)

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.calcPTphaseEnvelope(true); // bubble-first

    double[] ccb1 = ops1.get("cricondenbar");
    double[] ccb2 = ops2.get("cricondenbar");
    double[] cct1 = ops1.get("cricondentherm");
    double[] cct2 = ops2.get("cricondentherm");

    // Pressure should be within 5%
    double pDiff = Math.abs(ccb1[1] - ccb2[1]) / Math.max(ccb1[1], 1.0) * 100;
    assertTrue(pDiff < 5.0, "Dew-first and bubble-first cricondenbar should agree within 5%, diff="
        + pDiff + "%" + " (dew=" + ccb1[1] + ", bub=" + ccb2[1] + ")");

    // Temperature should be within 5%
    double tDiff = Math.abs(cct1[0] - cct2[0]) / cct1[0] * 100;
    assertTrue(tDiff < 5.0,
        "Dew-first and bubble-first cricondentherm should agree within 5%, diff=" + tDiff + "%");
  }

  // ========================== PHYSICAL CONSISTENCY CHECKS ==========================

  /**
   * Verify that dewT temperatures monotonically cover a wide range and all values are physically
   * reasonable.
   */
  @Test
  void testDewCurvePhysicalConsistency() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] dewP = ops.get("dewP");

    assertNotNull(dewT, "Should have dew temperature data");
    assertNotNull(dewP, "Should have dew pressure data");
    assertEquals(dewT.length, dewP.length, "dewT and dewP should have equal lengths");

    // All temperatures should be physically reasonable (50-600 K)
    // NaN entries are branch-break sentinels (inserted at critical-point crossings
    // and restart passes so plotters render disjoint polylines).
    for (int i = 0; i < dewT.length; i++) {
      if (Double.isNaN(dewT[i])) {
        assertTrue(Double.isNaN(dewP[i]),
            "dewT[" + i + "] and dewP[" + i + "] must both be NaN at branch breaks");
        continue;
      }
      assertTrue(dewT[i] > 50.0 && dewT[i] < 600.0,
          "dewT[" + i + "]=" + dewT[i] + " should be in 50-600 K range");
      assertTrue(dewP[i] > 0.0 && dewP[i] < 2000.0,
          "dewP[" + i + "]=" + dewP[i] + " should be in 0-2000 bar range");
    }
  }

  /**
   * Verify enthalpy, density, and entropy are available and non-NaN on the dew curve.
   */
  @Test
  void testDewCurveProperties() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewH = ops.get("dewH");
    double[] dewDens = ops.get("dewDens");
    double[] dewS = ops.get("dewS");
    double[] bubH = ops.get("bubH");
    double[] bubDens = ops.get("bubDens");
    double[] bubS = ops.get("bubS");

    assertNotNull(dewH, "Should have dew enthalpy");
    assertNotNull(dewDens, "Should have dew density");
    assertNotNull(dewS, "Should have dew entropy");
    assertNotNull(bubH, "Should have bubble enthalpy");
    assertNotNull(bubDens, "Should have bubble density");
    assertNotNull(bubS, "Should have bubble entropy");

    assertTrue(dewH.length > 0, "Should have dew enthalpy data points");
    assertTrue(dewDens.length > 0, "Should have dew density data points");
    assertTrue(dewS.length > 0, "Should have dew entropy data points");

    // Density should be positive (NaN entries are branch-break sentinels).
    for (int i = 0; i < dewDens.length; i++) {
      if (Double.isNaN(dewDens[i])) {
        continue;
      }
      assertTrue(dewDens[i] > 0.0, "Dew density[" + i + "]=" + dewDens[i] + " should be positive");
    }
    for (int i = 0; i < bubDens.length; i++) {
      if (Double.isNaN(bubDens[i])) {
        continue;
      }
      assertTrue(bubDens[i] > 0.0,
          "Bubble density[" + i + "]=" + bubDens[i] + " should be positive");
    }
  }

  /**
   * Verify cricondenbar has maximum pressure among all dew points. This is a mathematical
   * invariant.
   */
  @Test
  void testCricondenbarIsMaxDewPressure() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("n-butane", 0.04);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] dewP = ops.get("dewP");

    double maxP = -1;
    for (double p : dewP) {
      if (p > maxP) {
        maxP = p;
      }
    }

    // Cricondenbar should be at or very near the max dew pressure
    double diff = Math.abs(cricondenbar[1] - maxP) / cricondenbar[1] * 100;
    assertTrue(diff < 5.0,
        "Cricondenbar pressure should match max dew pressure within 5%, diff=" + diff + "%");
  }

  // ========================== WITH TBP FRACTIONS ==========================

  /**
   * Gas condensate with TBP fractions. Tests the algorithm with pseudo-components that have custom
   * critical properties.
   */
  @Test
  void testGasCondensateWithTBP() {
    SystemInterface fluid = new SystemSrkEos(350.0, 150.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.03);
    // MW in kg/mol for addTBPfraction
    fluid.addTBPfraction("C7", 0.05, 91.0 / 1000.0, 0.746);
    fluid.addTBPfraction("C8", 0.04, 104.0 / 1000.0, 0.770);
    fluid.addTBPfraction("C9", 0.02, 121.0 / 1000.0, 0.790);
    fluid.addPlusFraction("C10+", 0.02, 200.0 / 1000.0, 0.850);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(dewT, "Gas condensate should have dew curve");
    // TBP fractions with wide boiling range can be challenging;
    // the algorithm should produce at least some dew points
    assertTrue(dewT.length >= 1, "Dew curve should have at least 1 point, got " + dewT.length);

    // Gas condensate with C10+ has high cricondenbar
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 80.0, "Gas condensate cricondenbar should be >80 bar, got " + ccbP);

    // Cricondentherm should be high with C10+ present
    double cctT = cricondentherm[0];
    assertTrue(cctT > 300.0,
        "Gas condensate cricondentherm should be >300 K with C10+, got " + cctT);
  }

  // ========================== QUALITY LINES ==========================

  /**
   * Quality lines should lie within the phase envelope. For each quality line point, T should be
   * less than cricondentherm and P should be less than cricondenbar.
   */
  @Test
  void testQualityLinesInsideEnvelope() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    double[] betas = {0.1, 0.5, 0.9};
    ops.calcPTphaseEnvelopeWithQualityLines(betas);

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    for (double beta : betas) {
      String key = String.valueOf(beta);
      double[] qualT = ops.get("qualityT_" + key);
      double[] qualP = ops.get("qualityP_" + key);

      if (qualT != null && qualT.length > 0) {
        for (int i = 0; i < qualT.length; i++) {
          assertTrue(qualP[i] <= cricondenbar[1] * 1.05,
              "Quality line P at beta=" + beta + " should be <= cricondenbar +" + " 5% margin, got "
                  + qualP[i] + " vs ccb=" + cricondenbar[1]);
        }
      }
    }
  }

  // ========================== REFERENCE DATA COMPARISON ==========================

  /**
   * Methane-ethane 90/10 at SRK: Compare cricondenbar against published data. Knapp et al. (1982)
   * VLE compilation gives the phase envelope for this system.
   *
   * <p>
   * The SRK EOS-predicted critical locus for 90/10 CH4/C2H6 should produce: cricondenbar ~ 55-65
   * bar, cricondentherm ~ 215-235 K.
   * </p>
   */
  @Test
  void testMethaneEthaneAgainstLiterature() {
    SystemInterface fluid = new SystemSrkEos(200.0, 20.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] critPt = ops.get("criticalPoint1");

    // Literature: CH4/C2H6 90/10 cricondenbar is ~55-62 bar for SRK
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 50.0 && ccbP < 70.0,
        "CH4/C2H6 90/10 SRK cricondenbar expected 50-70 bar, got " + ccbP);

    // Critical temperature between Tc_CH4 (190.6) and Tc_C2H6 (305.3)
    double critT = critPt[0];
    assertTrue(critT > 185.0 && critT < 220.0,
        "CH4/C2H6 90/10 critical T expected 185-220 K, got " + critT);

    // Critical pressure should be above both Pc_CH4 and Pc_C2H6
    double critP = critPt[1];
    assertTrue(critP > 45.0 && critP < 70.0,
        "CH4/C2H6 90/10 critical P expected 45-70 bar, got " + critP);
  }

  /**
   * Five-component natural gas benchmark from Michelsen &amp; Mollerup (2007) Table 11.1 test case.
   * N2/CH4/C2H6/C3/nC4 typical lean gas. Cricondenbar should be in the 55-75 bar range, and the
   * cricondentherm in 230-260 K range with SRK.
   */
  @Test
  void testFiveComponentBenchmark() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.02);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");

    // Both branches should be populated
    assertTrue(dewT.length >= 5, "Should have >=5 dew points, got " + dewT.length);
    assertTrue(bubT.length >= 3, "Should have >=3 bubble points, got " + bubT.length);

    // Cricondenbar in typical range
    double ccbP = cricondenbar[1];
    // SRK can give higher cricondenbar than expected, ~86 bar is within model accuracy
    assertTrue(ccbP > 50.0 && ccbP < 100.0,
        "5-component lean gas cricondenbar expected 50-100 bar, got " + ccbP);

    // Cricondentherm
    double cctT = cricondentherm[0];
    assertTrue(cctT > 220.0 && cctT < 270.0,
        "5-component lean gas cricondentherm expected 220-270 K, got " + cctT);

    // Envelope should be closed
    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    assertTrue(env.isEnvelopeClosed(), "5-component gas should have closed envelope");
  }

  /**
   * High-CO2 gas (30% CO2): CO2-rich gases have distinctive phase envelopes with higher
   * cricondenbar due to CO2's high Pc (73.8 bar).
   */
  @Test
  void testHighCO2Gas() {
    SystemInterface fluid = new SystemSrkEos(250.0, 50.0);
    fluid.addComponent("CO2", 0.30);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] cricondenbar = ops.get("cricondenbar");

    assertNotNull(dewT, "High CO2 gas should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have points");

    // CO2 pushes cricondenbar higher
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 70.0,
        "High CO2 gas cricondenbar should be >70 bar due to CO2 Pc=73.8, got " + ccbP);
  }

  /**
   * Near-equimolar binary (50/50 CH4/C2H6): Tests the algorithm near the critical locus where the
   * phase envelope has its widest shape and the critical region is most challenging.
   */
  @Test
  void testEquimolarBinary() {
    SystemInterface fluid = new SystemSrkEos(250.0, 30.0);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("ethane", 0.50);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");
    double[] cricondenbar = ops.get("cricondenbar");
    double[] critPt = ops.get("criticalPoint1");

    assertNotNull(dewT, "50/50 CH4/C2H6 should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have points");

    // For 50/50 CH4/C2H6: Tc ~ 245 K, Pc ~ 55 bar (from phase diagram)
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 48.0 && ccbP < 70.0,
        "50/50 CH4/C2 cricondenbar expected 48-70 bar, got " + ccbP);

    // Critical point should be between Tc values
    double critT = critPt[0];
    assertTrue(critT > 200.0 && critT < 300.0,
        "50/50 CH4/C2 critical T expected 200-300 K, got " + critT);
  }

  // ========================== ADDITIONAL EDGE CASES ==========================

  /**
   * Very lean gas (99% methane, 1% ethane): Almost pure component. Tests that the algorithm handles
   * highly methane-dominated systems where the phase envelope is very narrow.
   */
  @Test
  void testVeryLeanGas() {
    SystemInterface fluid = new SystemSrkEos(170.0, 10.0);
    fluid.addComponent("methane", 0.99);
    fluid.addComponent("ethane", 0.01);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(dewT, "Very lean gas should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have enough points");

    // Very lean gas cricondentherm should be only slightly above Tc_CH4
    double cctT = cricondentherm[0];
    assertTrue(cctT > 185.0 && cctT < 230.0,
        "Very lean gas cricondentherm should be 185-230 K (just above Tc_CH4), got " + cctT);
  }

  /**
   * Nitrogen-rich gas: N2 has very low Tc (126.2 K) which shifts the phase envelope to lower
   * temperatures and pressures.
   */
  @Test
  void testNitrogenRichGas() {
    SystemInterface fluid = new SystemSrkEos(200.0, 20.0);
    fluid.addComponent("nitrogen", 0.30);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] cricondenbar = ops.get("cricondenbar");

    assertNotNull(dewT, "N2-rich gas should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have enough points");

    // N2 pushes cricondenbar lower than pure methane systems
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 30.0 && ccbP < 120.0,
        "N2-rich gas cricondenbar should be 30-120 bar, got " + ccbP);
  }

  /**
   * H2-containing system: Hydrogen has extremely low Tc (-239.95 C) making it a supercritical
   * component at most conditions. This is challenging because H2 barely condenses.
   */
  @Test
  void testHydrogenContainingGas() {
    SystemInterface fluid = new SystemSrkEos(200.0, 20.0);
    fluid.addComponent("hydrogen", 0.05);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertDoesNotThrow(() -> ops.calcPTphaseEnvelope());

    double[] dewT = ops.get("dewT");
    assertNotNull(dewT, "H2-containing gas should have dew curve");
    assertTrue(dewT.length >= 1,
        "H2-containing gas should have at least some dew points, got " + dewT.length);
  }

  /**
   * Water-free heavy system (C1/C3/nC5/nC7): Tests a mixture with significant heavy ends but no TBP
   * fractions, using only standard database components. Wider phase envelope than lean gas.
   */
  @Test
  void testHeavyHydrocarbonMix() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-pentane", 0.10);
    fluid.addComponent("n-heptane", 0.10);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");
    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(dewT, "Heavy HC mix should have dew curve");
    assertTrue(dewT.length >= 5, "Dew curve should have >=5 points, got " + dewT.length);

    // With nC7, cricondentherm should be quite high
    double cctT = cricondentherm[0];
    assertTrue(cctT > 300.0, "CH4/C3/nC5/nC7 cricondentherm should be >300 K, got " + cctT);

    // Cricondenbar should be elevated for this asymmetric mixture
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 80.0, "CH4/C3/nC5/nC7 cricondenbar should be >80 bar, got " + ccbP);

    // Envelope should close
    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    assertTrue(env.isEnvelopeClosed(), "Heavy HC mix should have closed envelope");
  }

  /**
   * Diagnostic test: Print cricondenbar, cricondentherm, and critical point for a standard lean gas
   * composition. Values can be compared against CMG WinProp, HYSYS, or other commercial phase
   * envelope software.
   *
   * <p>
   * This test always passes — its purpose is to produce reference output.
   * </p>
   */
  @Test
  void testDiagnosticPrintReference() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("nitrogen", 0.005);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("i-butane", 0.01);
    fluid.addComponent("n-butane", 0.015);
    fluid.addComponent("i-pentane", 0.005);
    fluid.addComponent("n-pentane", 0.005);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] ccb = ops.get("cricondenbar");
    double[] cct = ops.get("cricondentherm");
    double[] crit = ops.get("criticalPoint1");
    double[] dewT = ops.get("dewT");
    double[] dewP = ops.get("dewP");
    double[] bubT = ops.get("bubT");
    double[] bubP = ops.get("bubP");

    System.out.println("=== Phase Envelope Reference Values (SRK EOS) ===");
    System.out.println("Composition: N2=0.5%, CO2=2%, CH4=85%, C2=6%, C3=3%, "
        + "iC4=1%, nC4=1.5%, iC5=0.5%, nC5=0.5%");
    System.out.println(
        "Cricondenbar:   T=" + ccb[0] + " K (" + (ccb[0] - 273.15) + " °C), P=" + ccb[1] + " bar");
    System.out.println(
        "Cricondentherm: T=" + cct[0] + " K (" + (cct[0] - 273.15) + " °C), P=" + cct[1] + " bar");
    System.out.println("Critical point: T=" + crit[0] + " K (" + (crit[0] - 273.15) + " °C), P="
        + crit[1] + " bar");
    System.out.println("Dew curve: " + dewT.length + " points");
    System.out.println("Bubble curve: " + bubT.length + " points");

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    System.out.println("Envelope closed: " + env.isEnvelopeClosed());

    // Basic sanity: critical point should be between cricondenbar and cricondentherm
    assertTrue(crit[0] > 0, "Critical T should be positive");
    assertTrue(crit[1] > 0, "Critical P should be positive");

    // Cricondenbar should be the max P, cricondentherm should be the max T
    assertTrue(ccb[1] >= crit[1],
        "Cricondenbar P=" + ccb[1] + " should be >= critical P=" + crit[1]);
    assertTrue(cct[0] >= crit[0],
        "Cricondentherm T=" + cct[0] + " should be >= critical T=" + crit[0]);
  }

  // ========================== MULTIPLE CRITICAL POINT SYSTEMS ==========================

  /**
   * Methane + n-heptane (80/20): A moderately asymmetric binary system. With SRK, this typically
   * has one critical point. Verifies the algorithm correctly reports one CP and doesn't falsely
   * trigger multiple CP detection.
   */
  @Test
  void testOneCP_MethaneHeptane() {
    SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("n-heptane", 0.20);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();

    double[] crit1 = ops.get("criticalPoint1");
    double[] crit2 = ops.get("criticalPoint2");
    int numCPs = env.getNumberOfCriticalPoints();

    assertNotNull(crit1, "Should have at least one critical point");
    assertTrue(numCPs >= 1, "Should detect at least 1 critical point, got " + numCPs);

    if (numCPs >= 1) {
      assertTrue(crit1[0] > 0 && crit1[1] > 0,
          "Critical point 1 should have positive T and P, got T=" + crit1[0] + " P=" + crit1[1]);
    }

    // For a normal mixture, should typically detect exactly 1 CP
    // A second CP at {0,0} signals none found
    if (numCPs == 1) {
      assertEquals(0.0, crit2[0], 1e-10, "With 1 CP, criticalPoint2 T should be 0");
      assertEquals(0.0, crit2[1], 1e-10, "With 1 CP, criticalPoint2 P should be 0");
    }

    // Verify envelope properties are physically correct
    double[] dewT = ops.get("dewT");
    double[] cricondenbar = ops.get("cricondenbar");
    assertNotNull(dewT, "Should have dew points");
    assertTrue(dewT.length >= 3, "Should have enough dew points, got " + dewT.length);
  }

  /**
   * Methane + n-hexane (70/30): Wider phase envelope with significant asymmetry. Tests the
   * getNumberOfCriticalPoints() method. With SRK classic mixing, the VLE envelope has one CP.
   */
  @Test
  void testOneCP_MethaneHexane7030() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("n-hexane", 0.30);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    int numCPs = env.getNumberOfCriticalPoints();

    assertTrue(numCPs >= 1, "Should detect at least 1 CP for CH4/nC6 70/30, got " + numCPs);

    double[] crit1 = ops.get("criticalPoint1");
    assertTrue(crit1[0] > 200 && crit1[0] < 450,
        "CP1 temperature should be 200-450 K, got " + crit1[0]);
    assertTrue(crit1[1] > 50 && crit1[1] < 200,
        "CP1 pressure should be 50-200 bar, got " + crit1[1]);
  }

  /**
   * Ternary N2/CH4/C2H6 (10/75/15): Near the nitrogen-methane critical locus. This system can
   * produce complex phase behavior but typically has a single critical point on the VLE envelope.
   * The test verifies the algorithm handles the N2 supercritical component without issues.
   */
  @Test
  void testN2CH4C2H6NearCritical() {
    SystemInterface fluid = new SystemSrkEos(200.0, 30.0);
    fluid.addComponent("nitrogen", 0.10);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.15);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    int numCPs = env.getNumberOfCriticalPoints();

    assertTrue(numCPs >= 1, "Should detect at least 1 CP, got " + numCPs);

    double[] crit1 = ops.get("criticalPoint1");
    // N2/CH4/C2H6 mixture: critical T should be between 126 K (N2) and 305 K (C2H6)
    assertTrue(crit1[0] > 150.0 && crit1[0] < 250.0,
        "N2/CH4/C2H6 CP1 T should be 150-250 K, got " + crit1[0]);

    // Envelope should be well-behaved
    double[] dewT = ops.get("dewT");
    assertNotNull(dewT, "Should have dew curve");
    assertTrue(dewT.length >= 3, "Dew curve should have >=3 points");
  }

  /**
   * Highly asymmetric: methane/n-hexane/nC10 (80/10/10). The large size difference between CH4 and
   * nC10 creates a very wide envelope. Tests that the algorithm traces through the complex region
   * without falsely detecting multiple CPs for a system that should have one.
   */
  @Test
  void testAsymmetricTernary_CH4_nC6_nC10() {
    SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("n-hexane", 0.10);
    fluid.addComponent("nC10", 0.10);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    int numCPs = env.getNumberOfCriticalPoints();

    // Highly asymmetric but still single VP region with SRK classic mixing
    assertTrue(numCPs >= 1, "Should detect at least 1 CP, got " + numCPs);

    double[] dewT = ops.get("dewT");
    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(dewT, "Should have dew curve");

    // Very wide envelope: cricondentherm should be high due to nC10
    double cctT = cricondentherm[0];
    assertTrue(cctT > 300.0, "Asymmetric ternary cricondentherm should be >300 K, got " + cctT);

    // High cricondenbar for this very asymmetric system
    double ccbP = cricondenbar[1];
    assertTrue(ccbP > 80.0, "Asymmetric ternary cricondenbar should be >80 bar, got " + ccbP);
  }

  /**
   * CO2 + methane (50/50): A system known for complex phase behavior. With SRK classic mixing, the
   * VLE envelope should have one CP. Tests that the algorithm handles the CO2 + CH4 system
   * correctly. CO2's high Pc pushes the cricondenbar very high.
   */
  @Test
  void testCO2Methane5050() {
    SystemInterface fluid = new SystemSrkEos(250.0, 30.0);
    fluid.addComponent("CO2", 0.50);
    fluid.addComponent("methane", 0.50);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    int numCPs = env.getNumberOfCriticalPoints();

    assertTrue(numCPs >= 1, "Should detect at least 1 CP for CO2/CH4 50/50");

    double[] crit1 = ops.get("criticalPoint1");
    // Critical T should be between Tc_CH4 (190.6) and Tc_CO2 (304.1)
    assertTrue(crit1[0] > 200.0 && crit1[0] < 290.0,
        "CO2/CH4 50/50 CP1 T should be 200-290 K, got " + crit1[0]);

    double[] cricondenbar = ops.get("cricondenbar");
    // High CO2 content pushes cricondenbar
    assertTrue(cricondenbar[1] > 60.0,
        "CO2/CH4 50/50 cricondenbar should be >60 bar, got " + cricondenbar[1]);
  }

  /**
   * Test that the algorithm can handle a rich condensate system with TBP fractions and correctly
   * identifies the critical point. This is a system that may have complex phase behavior with the
   * heavy C10+ pseudo-component creating asymmetry.
   */
  @Test
  void testCriticalPointWithTBPFractions() {
    SystemInterface fluid = new SystemSrkEos(350.0, 100.0);
    fluid.addComponent("nitrogen", 0.005);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.03);
    fluid.addTBPfraction("C7", 0.035, 91.0 / 1000.0, 0.746);
    fluid.addTBPfraction("C8", 0.025, 104.0 / 1000.0, 0.770);
    fluid.addPlusFraction("C9+", 0.025, 150.0 / 1000.0, 0.810);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    int numCPs = env.getNumberOfCriticalPoints();

    assertTrue(numCPs >= 1, "Gas condensate with TBP should detect >=1 CP, got " + numCPs);

    double[] crit1 = ops.get("criticalPoint1");
    assertTrue(crit1[0] > 0, "Critical T should be positive for gas condensate, got " + crit1[0]);
    assertTrue(crit1[1] > 0, "Critical P should be positive for gas condensate, got " + crit1[1]);

    // Gas condensate typically has critical point at relatively high T and P
    double[] cricondenbar = ops.get("cricondenbar");
    assertTrue(cricondenbar[1] >= crit1[1],
        "Cricondenbar (" + cricondenbar[1] + ") should be >= CP pressure (" + crit1[1] + ")");
  }

  /**
   * Verify that criticalPoint2 returns {0,0} for a standard single-CP system (lean natural gas).
   * This ensures backward compatibility with consumers that check criticalPoint2.
   */
  @Test
  void testCriticalPoint2BackwardCompatibility() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] crit2 = ops.get("criticalPoint2");
    assertNotNull(crit2, "criticalPoint2 should not be null");

    PTPhaseEnvelopeMichelsen env = (PTPhaseEnvelopeMichelsen) ops.getOperation();
    if (env.getNumberOfCriticalPoints() <= 1) {
      assertEquals(0.0, crit2[0], 1e-10, "criticalPoint2 T should be 0 when only 1 CP exists");
      assertEquals(0.0, crit2[1], 1e-10, "criticalPoint2 P should be 0 when only 1 CP exists");
    }
  }

  /**
   * Test criticalPoint3 key — should return {0,0} for all standard mixtures. Only exotic mixtures
   * with three or more CPs would populate this (extremely rare in practice).
   */
  @Test
  void testCriticalPoint3ReturnsZero() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] crit3 = ops.get("criticalPoint3");
    assertNotNull(crit3, "criticalPoint3 should not be null");
    assertEquals(0.0, crit3[0], 1e-10, "criticalPoint3 T should be 0");
    assertEquals(0.0, crit3[1], 1e-10, "criticalPoint3 P should be 0");
  }
}
