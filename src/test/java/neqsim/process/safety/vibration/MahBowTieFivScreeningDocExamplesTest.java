package neqsim.process.safety.vibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.FlowInducedVibrationAnalyser;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executes every code example in {@code docs/safety/mah_bowtie_fiv_screening.md} so the documentation cannot drift away
 * from the API.
 */
public class MahBowTieFivScreeningDocExamplesTest {

  /**
   * Builds the wet inlet gas used by the rigorous main-line example.
   *
   * @return a two-phase inlet gas at 5 degC and 48 bara
   */
  private SystemInterface inletGas() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 5.0, 48.0);
    fluid.addComponent("nitrogen", 0.60);
    fluid.addComponent("CO2", 1.60);
    fluid.addComponent("methane", 84.0);
    fluid.addComponent("ethane", 6.30);
    fluid.addComponent("propane", 2.70);
    fluid.addComponent("n-pentane", 1.31);
    fluid.addComponent("n-heptane", 1.27);
    fluid.addComponent("nC10", 1.09);
    fluid.addComponent("water", 2.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  @Test
  @DisplayName("Doc example: factored PipingFivScreening calls")
  public void testFactoredScreeningExample() {
    FivLikelihoodResult gas = PipingFivScreening.screenGas("Compressor discharge", 80.0, 30.0, 0.3, 0.006, 2, 4.0, 2.0);
    assertTrue(gas.getLofScore() > 0.0);
    assertNotNull(gas.getLikelihood());
    assertTrue(gas.toJson().contains("lofScore"));

    FivLikelihoodResult liquid = PipingFivScreening.screenLiquid("Pump discharge", 3.5, 0.15, 0.005, 1, 1.5);
    assertTrue(liquid.getLofScore() > 0.0);

    assertEquals(PipingFivLikelihood.HIGH, PipingFivScreening.bandFor(0.7));
  }

  @Test
  @DisplayName("Doc example: rigorous LOF from a solved PipeBeggsAndBrills segment")
  public void testRigorousAnalyserExample() {
    Stream feed = new Stream("feed", inletGas());
    feed.setTemperature(5.0, "C");
    feed.setPressure(48.0, "bara");
    feed.setFlowRate(10.5, "MSm3/day");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("inlet pipe", feed);
    pipe.setDiameter(0.3652);
    pipe.setThickness(0.0206);
    pipe.setLength(12.0);
    pipe.setNumberOfIncrements(4);

    FlowInducedVibrationAnalyser fiv = new FlowInducedVibrationAnalyser("LOF", pipe);
    fiv.setMethod("LOF");
    fiv.setSupportArrangement("Medium stiff");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pipe);
    process.run();

    double lof = fiv.getMeasuredValue("");
    assertTrue(lof > 0.0, "documented LOF read must return a positive score");
    assertEquals(1.0, FlowInducedVibrationAnalyser.REFERENCE_VISCOSITY_CP, 1.0e-12);
  }

  @Test
  @DisplayName("Doc example: missing wall thickness is rejected, not silently NaN")
  public void testMissingThicknessExample() {
    Stream feed = new Stream("feed", inletGas());
    feed.setFlowRate(10.5, "MSm3/day");
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("inlet pipe", feed);
    pipe.setDiameter(0.3652);
    pipe.setLength(12.0);
    pipe.setNumberOfIncrements(2);

    FlowInducedVibrationAnalyser fiv = new FlowInducedVibrationAnalyser("LOF", pipe);
    fiv.setMethod("LOF");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pipe);
    process.run();

    assertThrows(IllegalStateException.class, () -> fiv.getMeasuredValue(""));
  }

  @Test
  @DisplayName("Doc example: dead-leg pulsation screening and its helper methods")
  public void testPulsationScreeningExample() {
    FlowInducedPulsationResult res = FlowInducedPulsationScreening.screen("Closed cross-over stub", 3.0, 0.2477, 0.3652,
        20.1, 46.2, 374.0);

    boolean resonance = res.isAnyModeLockedIn();
    double fs = res.getSheddingFrequencyHz();
    assertTrue(fs > 0.0);
    assertNotNull(res.getLikelihood());
    assertTrue(resonance || !resonance, "the documented flag must be readable");

    for (FlowInducedPulsationResult.BranchMode m : res.getModes()) {
      assertTrue(m.getModeIndex() >= 0);
      assertTrue(m.getFrequencyHz() > 0.0);
      assertTrue(m.getEnvelopeLowHz() < m.getFrequencyHz());
      assertTrue(m.getEnvelopeHighHz() > m.getFrequencyHz());
      assertTrue(m.getResonanceVelocityMPerS() > 0.0);
    }

    double weff = FlowInducedPulsationScreening.effectiveWidth(0.2477, 0.0);
    assertEquals(Math.PI * 0.2477 / 4.0, weff, 1.0e-12);

    double f0 = FlowInducedPulsationScreening.eigenFrequency(0, 374.0, 3.0,
        FlowInducedPulsationScreening.AcousticTermination.CLOSED);
    assertEquals(374.0 / 12.0, f0, 1.0e-9);
  }

  @Test
  @DisplayName("Doc claim: the resonant length window inverts the lock-in criterion")
  public void testDocumentedLengthWindowFormula() {
    double c = 374.0;
    double d = 0.2477;
    double u0 = 20.1;
    double sr = FlowInducedPulsationScreening.DEFAULT_STROUHAL_MODE_A;
    double env = FlowInducedPulsationScreening.LOCK_IN_ENVELOPE_FRACTION;
    double fs = sr * u0 / FlowInducedPulsationScreening.effectiveWidth(d, 0.0);

    // Documented window for mode n: L in [(1-env)(2n+1)c/(4 fs), (1+env)(2n+1)c/(4 fs)].
    double centre = c / (4.0 * fs);
    double low = (1.0 - env) * centre;
    double high = (1.0 + env) * centre;

    // A length inside the window must screen as resonant, and one outside must not.
    double inside = 0.5 * (low + high);
    double outside = 1.15 * high;
    assertTrue(
        FlowInducedPulsationScreening.screen("in", inside, d, 0.3652, u0, 46.2, c).getModes().get(0).isLockedIn());
    assertTrue(
        !FlowInducedPulsationScreening.screen("out", outside, d, 0.3652, u0, 46.2, c).getModes().get(0).isLockedIn());
  }
}
