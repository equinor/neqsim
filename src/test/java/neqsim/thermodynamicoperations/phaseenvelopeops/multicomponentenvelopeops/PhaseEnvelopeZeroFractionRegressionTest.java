package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression test for phase envelope tracing with zero-fraction components (NIP-05).
 *
 * <p>
 * Uses a synthetic multi-component gas condensate fluid (not from any real field or well test)
 * with several components set to z=0. Before NIP-03, the zero-fraction components caused
 * singular Jacobians in the Newton-Raphson solver, producing silent degenerate envelopes.
 * </p>
 *
 * @author NeqSim Agent
 * @version 1.0
 */
class PhaseEnvelopeZeroFractionRegressionTest {

  /**
   * Tests that a PR fluid with zero-fraction aromatics produces a valid envelope.
   *
   * <p>
   * Composition is a synthetic gas condensate proxy. Benzene, toluene, and c-hexane are
   * deliberately set to z=0.0 to exercise the NIP-03 filtering logic. Uses Peng-Robinson EOS
   * with classic mixing rule for robust envelope tracing.
   * </p>
   */
  @Test
  void testEnvelopeWithZeroFractionComponents_PR() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemPrEos(298.0, 50.0);

    // Synthetic gas condensate — NOT from any real field
    fluid.addComponent("nitrogen", 1.8);
    fluid.addComponent("CO2", 3.2);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("ethane", 8.5);
    fluid.addComponent("propane", 3.5);
    fluid.addComponent("i-butane", 0.6);
    fluid.addComponent("n-butane", 1.0);
    fluid.addComponent("i-pentane", 0.4);
    fluid.addComponent("n-pentane", 0.3);
    fluid.addComponent("n-hexane", 0.2);

    // Zero-fraction components — the NIP-03 regression trigger
    fluid.addComponent("benzene", 0.0);
    fluid.addComponent("toluene", 0.0);
    fluid.addComponent("c-hexane", 0.0);

    fluid.addComponent("n-heptane", 0.3);
    fluid.addComponent("n-octane", 0.2);

    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");

    assertNotNull(dewT, "Dew temperatures should not be null");
    assertNotNull(bubT, "Bubble temperatures should not be null");

    // Before NIP-03, this produced 0-1 points; with the fix it should produce a full envelope
    assertTrue(dewT.length > 10,
        "Should have > 10 dew points with z=0 filtering, got: " + dewT.length);
    assertTrue(bubT.length > 5,
        "Should have > 5 bubble points with z=0 filtering, got: " + bubT.length);

    // Cricondenbar should be at a reasonable pressure for this gas condensate
    double[] ccb = ops.get("cricondenbar");
    assertNotNull(ccb, "Cricondenbar values should not be null");
    assertTrue(ccb[1] > 30.0,
        "Cricondenbar pressure should be > 30 bara for this composition, got: " + ccb[1]);
    assertTrue(ccb[1] < 300.0,
        "Cricondenbar pressure should be < 300 bara for this composition, got: " + ccb[1]);
  }

  /**
   * Tests that a SRK fluid with zero-fraction components produces a valid envelope.
   */
  @Test
  void testEnvelopeWithZeroFractionComponents_SRK() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);

    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("n-butane", 2.0);

    // Zero-fraction: should be silently removed
    fluid.addComponent("i-pentane", 0.0);
    fluid.addComponent("n-pentane", 0.0);

    fluid.addComponent("n-hexane", 1.0);

    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    ops.calcPTphaseEnvelope();

    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");

    assertTrue(dewT.length > 5,
        "SRK envelope with z=0 components should have > 5 dew points, got: " + dewT.length);
    assertTrue(bubT.length > 5,
        "SRK envelope with z=0 components should have > 5 bubble points, got: " + bubT.length);
  }

  /**
   * Tests the PhaseEnvelopeResult status reporting on a normal (non-degenerate) fluid.
   */
  @Test
  void testPhaseEnvelopeResult() {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 90.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("propane", 3.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    ops.calcPTphaseEnvelope();

    // Access the operation and cast to get the result
    PTPhaseEnvelopeMichelsen envelope =
        (PTPhaseEnvelopeMichelsen) ops.getOperation();
    PhaseEnvelopeResult result = envelope.getEnvelopeResult();

    assertNotNull(result, "Envelope result should not be null");
    assertEquals(PhaseEnvelopeResult.Status.CONVERGED, result.getStatus(),
        "Simple fluid should converge");
    assertTrue(result.isConverged());
    assertTrue(result.getTotalPointCount() > 10,
        "Total points should be > 10, got: " + result.getTotalPointCount());
    assertTrue(result.getCricondenbarPressure() > 40.0,
        "Cricondenbar should be > 40 bara for methane-rich gas");
    assertTrue(result.getDiagnosticMessage().contains("successfully"));
  }
}
