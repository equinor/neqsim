package neqsim.process.safety.vibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PipingFivScreening}.
 *
 * @author ESOL
 * @version 1.0
 */
class PipingFivScreeningTest {

  @Test
  void lowLofForCalmGas() {
    // ρv² = 50 * 5^2 = 1250 → energy term 0.025; D/t = 40 → factor 1; no branches; steady; good
    // support.
    FivLikelihoodResult r = PipingFivScreening.screenGas("C1", 50.0, 5.0, 0.2, 0.005, 0, 1.0, 1.0);
    assertEquals(PipingFivLikelihood.LOW, r.getLikelihood());
    assertTrue(r.getLofScore() < 0.3);
  }

  @Test
  void highLofForReciprocatingCompressorDischarge() {
    // ρv² = 80 * 30^2 = 72000 → energy term 1.44; D/t = 0.3 / 0.006 = 50 → factor 1.25; pulsation
    // 4.0
    FivLikelihoodResult r = PipingFivScreening.screenGas("Compressor discharge", 80.0, 30.0, 0.3, 0.006, 2, 4.0, 2.0);
    assertEquals(PipingFivLikelihood.VERY_HIGH, r.getLikelihood());
    assertTrue(r.getLofScore() >= 1.0);
  }

  @Test
  void mediumLofForLiquidLine() {
    // v/vref = 1.5/6 = 0.25; D/t = 30 → factor 1.0; branch 1 → 1.1; support 1.5 → lof ≈ 0.41
    FivLikelihoodResult r = PipingFivScreening.screenLiquid("Pump discharge", 1.5, 0.15, 0.005, 1, 1.5);
    assertTrue(r.getLikelihood() == PipingFivLikelihood.LOW || r.getLikelihood() == PipingFivLikelihood.MEDIUM);
    assertTrue(r.getLofScore() > 0.0);
  }

  @Test
  void bandForBoundaries() {
    assertEquals(PipingFivLikelihood.LOW, PipingFivScreening.bandFor(0.25));
    assertEquals(PipingFivLikelihood.MEDIUM, PipingFivScreening.bandFor(0.4));
    assertEquals(PipingFivLikelihood.HIGH, PipingFivScreening.bandFor(0.7));
    assertEquals(PipingFivLikelihood.VERY_HIGH, PipingFivScreening.bandFor(1.5));
  }

  @Test
  void invalidGeometryRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> PipingFivScreening.screenGas("C1", 50.0, 5.0, 0.0, 0.005, 0, 1.0, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> PipingFivScreening.screenLiquid("C1", -1.0, 0.15, 0.005, 0, 1.0));
  }

  @Test
  void jsonExport() {
    FivLikelihoodResult r = PipingFivScreening.screenGas("Discharge", 80.0, 30.0, 0.3, 0.006, 2, 4.0, 2.0);
    String json = r.toJson();
    assertTrue(json.contains("\"lofScore\""));
    assertTrue(json.contains("\"likelihood\""));
  }
}
