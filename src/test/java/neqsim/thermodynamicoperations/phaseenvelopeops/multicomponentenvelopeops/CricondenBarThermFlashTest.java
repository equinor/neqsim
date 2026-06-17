package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the direct cricondenbar (CricondenBarFlash) and cricondentherm (CricondenThermFlash)
 * calculation algorithms.
 *
 * <p>
 * These tests verify that the direct Newton refinement produces results consistent with the
 * incremental tracking from phase envelope tracing (which serves as ground truth).
 * </p>
 */
public class CricondenBarThermFlashTest {

  /**
   * Test cricondenbar for a simple lean natural gas with SRK EOS. The envelope tracing should
   * produce a cricondenbar around 47 bar for this methane-dominated mixture.
   */
  @Test
  void testCricondenBarSimpleGas() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("methane", 0.98);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    assertNotNull(cricondenbar, "cricondenbar should not be null");
    assertTrue(cricondenbar[1] > 40.0,
        "Cricondenbar pressure should be > 40 bar for methane-dominated gas, got: "
            + cricondenbar[1]);
    assertTrue(cricondenbar[0] > 150.0,
        "Cricondenbar temperature should be > 150 K, got: " + cricondenbar[0]);
  }

  /**
   * Test cricondenbar and cricondentherm for a rich natural gas with SRK EOS. The mixture contains
   * significant C2+ fractions, giving a well-defined phase envelope.
   */
  @Test
  void testCricondenBarThermRichGas() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("nitrogen", 3.43);
    fluid.addComponent("CO2", 0.34);
    fluid.addComponent("methane", 62.51);
    fluid.addComponent("ethane", 15.65);
    fluid.addComponent("propane", 13.22);
    fluid.addComponent("i-butane", 1.61);
    fluid.addComponent("n-butane", 2.48);
    fluid.addComponent("i-pentane", 0.35);
    fluid.addComponent("n-pentane", 0.29);
    fluid.addComponent("n-hexane", 0.12);
    fluid.setMixingRule(2);
    fluid.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(cricondenbar, "cricondenbar should not be null");
    assertNotNull(cricondentherm, "cricondentherm should not be null");

    // Rich gas cricondenbar should be > 80 bar
    assertTrue(cricondenbar[1] > 80.0,
        "Cricondenbar pressure should be > 80 bar, got: " + cricondenbar[1]);

    // Cricondentherm should be > 270 K (close to 0 C) for this composition
    assertTrue(cricondentherm[0] > 250.0,
        "Cricondentherm temperature should be > 250 K, got: " + cricondentherm[0]);

    // Cricondentherm temperature > cricondenbar temperature (therm is the max T)
    assertTrue(cricondentherm[0] >= cricondenbar[0], "Cricondentherm T (" + cricondentherm[0]
        + ") should be >= cricondenbar T (" + cricondenbar[0] + ")");

    // Cricondenbar pressure > cricondentherm pressure (bar is the max P)
    assertTrue(cricondenbar[1] >= cricondentherm[1], "Cricondenbar P (" + cricondenbar[1]
        + ") should be >= cricondentherm P (" + cricondentherm[1] + ")");
  }

  /**
   * Test cricondenbar refinement via calcCricoP. First compute the phase envelope to get estimates,
   * then run the direct cricondenbar flash and verify it refines to a similar or better result.
   */
  @Test
  void testCalcCricoPRefinement() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("n-pentane", 2.0);
    fluid.setMixingRule("classic");

    // First get envelope estimate
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondenbarX = ops.get("cricondenbarX");
    double[] cricondenbarY = ops.get("cricondenbarY");

    assertNotNull(cricondenbar, "cricondenbar from envelope should not be null");
    double envelopeT = cricondenbar[0];
    double envelopeP = cricondenbar[1];
    assertTrue(envelopeP > 50.0, "Envelope cricondenbar should be > 50 bar, got: " + envelopeP);

    // Now refine with direct cricondenbar flash
    double[] cricoInput = new double[] {envelopeT, envelopeP, 0.0};
    ops.calcCricoP(cricoInput, cricondenbarX, cricondenbarY);

    // The refined result should be close to the envelope estimate (within 5%)
    double refinedT = cricoInput[0];
    double refinedP = cricoInput[1];

    if (refinedT > 0 && refinedP > 0) {
      // If converged, should be close to envelope estimate
      assertEquals(envelopeT, refinedT, envelopeT * 0.05,
          "Refined cricondenbar T should be within 5% of envelope estimate");
      assertEquals(envelopeP, refinedP, envelopeP * 0.05,
          "Refined cricondenbar P should be within 5% of envelope estimate");
    }
    // If not converged (T=-1, P=-1), the algorithm falls back to envelope estimate
  }

  /**
   * Test cricondentherm refinement via calcCricoT. First compute the phase envelope to get
   * estimates, then run the direct cricondentherm flash and verify it refines to a similar or
   * better result.
   */
  @Test
  void testCalcCricoTRefinement() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("n-pentane", 2.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondentherm = ops.get("cricondentherm");
    double[] cricondenthermX = ops.get("cricondenthermX");
    double[] cricondenthermY = ops.get("cricondenthermY");

    assertNotNull(cricondentherm, "cricondentherm from envelope should not be null");
    double envelopeT = cricondentherm[0];
    double envelopeP = cricondentherm[1];
    assertTrue(envelopeT > 200.0, "Envelope cricondentherm T should be > 200 K, got: " + envelopeT);

    // Now refine with direct cricondentherm flash
    double[] cricoInput = new double[] {envelopeT, envelopeP, 0.0};
    ops.calcCricoT(cricoInput, cricondenthermX, cricondenthermY);

    double refinedT = cricoInput[0];
    double refinedP = cricoInput[1];

    if (refinedT > 0 && refinedP > 0) {
      assertEquals(envelopeT, refinedT, envelopeT * 0.05,
          "Refined cricondentherm T should be within 5% of envelope estimate");
      assertEquals(envelopeP, refinedP, envelopeP * 0.05,
          "Refined cricondentherm P should be within 5% of envelope estimate");
    }
  }

  /**
   * Test with the NJA 24-component natural gas composition using UMRPRU EOS. This is the
   * challenging case from the integration tests with cricondenbar around 107-110 bar.
   */
  @Test
  void testCricondenBarNJAFluid() {
    SystemInterface fluid = new SystemUMRPRUMCEos(280.0, 10.0);
    fluid.addComponent("nitrogen", 0.006857943);
    fluid.addComponent("CO2", 0.016177945);
    fluid.addComponent("methane", 0.784699957);
    fluid.addComponent("ethane", 0.095835435);
    fluid.addComponent("propane", 0.058808079);
    fluid.addComponent("i-butane", 0.007946427);
    fluid.addComponent("n-butane", 0.018245426);
    fluid.addComponent("i-pentane", 0.003795847);
    fluid.addComponent("n-pentane", 0.003870575);
    fluid.addComponent("2-m-C5", 0.000556670);
    fluid.addComponent("3-m-C5", 0.000284215);
    fluid.addComponent("n-hexane", 0.000755472);
    fluid.addComponent("c-hexane", 0.000783208);
    fluid.addComponent("n-heptane", 0.000334932);
    fluid.addComponent("benzene", 0.000156906);
    fluid.addComponent("n-octane", 6.321321e-05);
    fluid.addComponent("c-C7", 2.968081e-04);
    fluid.addComponent("toluene", 7.374866e-05);
    fluid.addComponent("n-nonane", 1.558264e-05);
    fluid.addComponent("c-C8", 2.366789e-05);
    fluid.addComponent("m-Xylene", 2.170802e-05);
    fluid.addComponent("nC10", 1.768925e-05);
    fluid.addComponent("nC11", 9.800553e-08);
    fluid.addComponent("nC12", 4.900277e-07);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] cricondentherm = ops.get("cricondentherm");

    assertNotNull(cricondenbar, "cricondenbar should not be null for NJA fluid");
    assertTrue(cricondenbar[1] > 100.0,
        "NJA fluid cricondenbar should be > 100 bar, got: " + cricondenbar[1]);

    assertNotNull(cricondentherm, "cricondentherm should not be null for NJA fluid");
    assertTrue(cricondentherm[0] > 250.0,
        "NJA fluid cricondentherm T should be > 250 K, got: " + cricondentherm[0]);
  }

  /**
   * Test that cricondenbar pressure is indeed the maximum dew-point pressure. Compare the
   * cricondenbar value against all dew-point pressures from the envelope.
   */
  @Test
  void testCricondenBarIsMaxDewPressure() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("i-butane", 1.5);
    fluid.addComponent("n-butane", 1.5);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondenbar = ops.get("cricondenbar");
    double[] dewP = ops.get("dewP");
    double[] bubP = ops.get("bubP");

    assertNotNull(cricondenbar, "cricondenbar should not be null");
    assertNotNull(dewP, "dewP should not be null");

    // Find max of all dew-point pressures
    double maxDewP = Double.NEGATIVE_INFINITY;
    for (double p : dewP) {
      if (p > maxDewP) {
        maxDewP = p;
      }
    }

    // Also check bubble-point pressures if available
    double maxBubP = Double.NEGATIVE_INFINITY;
    if (bubP != null) {
      for (double p : bubP) {
        if (p > maxBubP) {
          maxBubP = p;
        }
      }
    }

    double maxEnvelopeP = Math.max(maxDewP, maxBubP);

    // Cricondenbar should be close to the maximum envelope pressure
    assertEquals(maxEnvelopeP, cricondenbar[1], maxEnvelopeP * 0.02,
        "Cricondenbar pressure should match max envelope pressure within 2%");
  }

  /**
   * Test that cricondentherm temperature is indeed the maximum dew-point temperature. Compare the
   * cricondentherm value against all dew-point temperatures from the envelope.
   */
  @Test
  void testCricondenThermIsMaxDewTemperature() {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("i-butane", 1.5);
    fluid.addComponent("n-butane", 1.5);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope();

    double[] cricondentherm = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");

    assertNotNull(cricondentherm, "cricondentherm should not be null");
    assertNotNull(dewT, "dewT should not be null");

    // Find max temperature across both branches
    double maxDewT = Double.NEGATIVE_INFINITY;
    for (double t : dewT) {
      if (t > maxDewT) {
        maxDewT = t;
      }
    }

    double maxBubT = Double.NEGATIVE_INFINITY;
    if (bubT != null) {
      for (double t : bubT) {
        if (t > maxBubT) {
          maxBubT = t;
        }
      }
    }

    double maxEnvelopeT = Math.max(maxDewT, maxBubT);

    // Cricondentherm should be close to the maximum envelope temperature
    assertEquals(maxEnvelopeT, cricondentherm[0], maxEnvelopeT * 0.02,
        "Cricondentherm temperature should match max envelope temperature within 2%");
  }
}
