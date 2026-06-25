package neqsim.process.safety.vibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AcousticInducedVibrationScreening}.
 *
 * @author ESOL
 * @version 1.0
 */
class AcousticInducedVibrationScreeningTest {

  @Test
  void lowLikelihoodForBenignLetdown() {
    // Small flow, small pressure drop, thick small-bore pipe -> low PWL, low risk.
    AcousticInducedVibrationResult r = AcousticInducedVibrationScreening.screen("Benign", 0.5, 10.0, 8.0, 300.0, 20.0,
	0.114, 0.0057);
    assertEquals(PipingFivLikelihood.LOW, r.getLikelihood());
    assertTrue(r.getMarginDb() < 0.0);
  }

  @Test
  void veryHighLikelihoodForSevereLetdown() {
    // Large flow, deep pressure drop, thin large-bore pipe -> high PWL, very high risk.
    AcousticInducedVibrationResult r = AcousticInducedVibrationScreening.screen("Severe", 50.0, 100.0, 5.0, 350.0, 16.0,
	0.6, 0.006);
    assertEquals(PipingFivLikelihood.VERY_HIGH, r.getLikelihood());
    assertTrue(r.getMarginDb() >= 10.0);
  }

  @Test
  void pwlIncreasesWithPressureDrop() {
    AcousticInducedVibrationResult low = AcousticInducedVibrationScreening.screen("A", 10.0, 50.0, 45.0, 320.0, 18.0,
	0.3, 0.008);
    AcousticInducedVibrationResult high = AcousticInducedVibrationScreening.screen("B", 10.0, 50.0, 5.0, 320.0, 18.0,
	0.3, 0.008);
    assertTrue(high.getPwlDb() > low.getPwlDb());
  }

  @Test
  void thinnerWallReducesAllowableAndRaisesMargin() {
    AcousticInducedVibrationResult thick = AcousticInducedVibrationScreening.screen("Thick", 20.0, 60.0, 10.0, 330.0,
	17.0, 0.3, 0.012);
    AcousticInducedVibrationResult thin = AcousticInducedVibrationScreening.screen("Thin", 20.0, 60.0, 10.0, 330.0,
	17.0, 0.3, 0.004);
    assertTrue(thin.getAllowablePwlDb() < thick.getAllowablePwlDb());
    assertTrue(thin.getMarginDb() > thick.getMarginDb());
  }

  @Test
  void bandForBoundaries() {
    assertEquals(PipingFivLikelihood.LOW, AcousticInducedVibrationScreening.bandFor(-15.0));
    assertEquals(PipingFivLikelihood.MEDIUM, AcousticInducedVibrationScreening.bandFor(-5.0));
    assertEquals(PipingFivLikelihood.HIGH, AcousticInducedVibrationScreening.bandFor(5.0));
    assertEquals(PipingFivLikelihood.VERY_HIGH, AcousticInducedVibrationScreening.bandFor(15.0));
  }

  @Test
  void invalidInputsRejected() {
    assertThrows(IllegalArgumentException.class,
	() -> AcousticInducedVibrationScreening.screen("X", 0.0, 50.0, 10.0, 300.0, 18.0, 0.3, 0.008));
    assertThrows(IllegalArgumentException.class,
	() -> AcousticInducedVibrationScreening.screen("X", 10.0, 50.0, 60.0, 300.0, 18.0, 0.3, 0.008));
    assertThrows(IllegalArgumentException.class,
	() -> AcousticInducedVibrationScreening.screen("X", 10.0, 50.0, 10.0, 300.0, 18.0, 0.0, 0.008));
  }

  @Test
  void jsonExport() {
    AcousticInducedVibrationResult r = AcousticInducedVibrationScreening.screen("Severe", 50.0, 100.0, 5.0, 350.0, 16.0,
	0.6, 0.006);
    String json = r.toJson();
    assertTrue(json.contains("\"pwlDb\""));
    assertTrue(json.contains("\"likelihood\""));
  }
}
