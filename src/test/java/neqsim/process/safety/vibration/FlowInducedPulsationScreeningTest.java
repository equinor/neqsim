package neqsim.process.safety.vibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.vibration.FlowInducedPulsationScreening.AcousticTermination;

/**
 * Unit tests for {@link FlowInducedPulsationScreening}.
 */
public class FlowInducedPulsationScreeningTest {

  private static final double RUN_ID = 0.3652;

  @Test
  @DisplayName("Closed-branch eigenfrequencies reproduce the published worked example")
  public void testClosedBranchWorkedExample() {
    // Published screening example: a normally closed valve 3 m from the tee, speed of sound 400 m/s,
    // gives f0 = 33.3 Hz, f1 = 100 Hz, f2 = 166.7 Hz.
    double c = 400.0;
    double length = 3.0;
    assertEquals(33.333, FlowInducedPulsationScreening.eigenFrequency(0, c, length, AcousticTermination.CLOSED), 0.01);
    assertEquals(100.0, FlowInducedPulsationScreening.eigenFrequency(1, c, length, AcousticTermination.CLOSED), 0.01);
    assertEquals(166.667, FlowInducedPulsationScreening.eigenFrequency(2, c, length, AcousticTermination.CLOSED), 0.01);

    // No end correction: the acoustic length runs to the first acoustic boundary and is used as given.
    FlowInducedPulsationResult result = FlowInducedPulsationScreening.screen("worked example", length, 0.15, RUN_ID,
        20.0, 46.0, c);
    assertEquals(33.333, result.getModes().get(0).getFrequencyHz(), 0.01);
  }

  @Test
  @DisplayName("Open-end branch gives half-wave modes")
  public void testOpenEndBranchModes() {
    double c = 400.0;
    double length = 3.0;
    assertEquals(66.667, FlowInducedPulsationScreening.eigenFrequency(0, c, length, AcousticTermination.OPEN), 0.01);
    assertEquals(133.333, FlowInducedPulsationScreening.eigenFrequency(1, c, length, AcousticTermination.OPEN), 0.01);
  }

  @Test
  @DisplayName("Strouhal length scale is the effective mouth width, not the branch diameter")
  public void testEffectiveWidth() {
    double d = 0.15;
    assertEquals(Math.PI * d / 4.0, FlowInducedPulsationScreening.effectiveWidth(d, 0.0), 1.0e-12);
    assertEquals(Math.PI * d / 4.0 + 0.01, FlowInducedPulsationScreening.effectiveWidth(d, 0.01), 1.0e-12);

    FlowInducedPulsationResult result = FlowInducedPulsationScreening.screen("branch", 3.0, d, RUN_ID, 20.0, 46.0,
        400.0);
    double expectedShedding = FlowInducedPulsationScreening.DEFAULT_STROUHAL_MODE_A * 20.0 / (Math.PI * d / 4.0);
    assertEquals(expectedShedding, result.getSheddingFrequencyHz(), 1.0e-9);
    assertEquals(expectedShedding, result.getContributingFactors().get("sheddingFrequency_Hz"), 1.0e-9);
  }

  @Test
  @DisplayName("Resonance is flagged when the shedding frequency lands inside the plus/minus 20 % envelope")
  public void testLockInEnvelope() {
    double d = 0.15;
    double c = 400.0;
    double length = 3.0;
    double weff = Math.PI * d / 4.0;
    double f0 = c / (4.0 * length);
    double sr = FlowInducedPulsationScreening.DEFAULT_STROUHAL_MODE_A;

    // Velocity chosen so the shedding frequency sits exactly on the fundamental.
    double onResonance = f0 * weff / sr;
    FlowInducedPulsationResult hit = FlowInducedPulsationScreening.screen("tuned", length, d, RUN_ID, onResonance, 46.0,
        c);
    assertTrue(hit.isAnyModeLockedIn());
    assertTrue(hit.getModes().get(0).isLockedIn());
    assertEquals(onResonance, hit.getModes().get(0).getResonanceVelocityMPerS(), 1.0e-9);
    assertEquals(0.8 * f0, hit.getModes().get(0).getEnvelopeLowHz(), 1.0e-9);
    assertEquals(1.2 * f0, hit.getModes().get(0).getEnvelopeHighHz(), 1.0e-9);

    // Just outside the envelope on the low side of the fundamental and below the next mode.
    FlowInducedPulsationResult miss = FlowInducedPulsationScreening.screen("detuned", length, d, RUN_ID,
        0.6 * onResonance, 46.0, c);
    assertFalse(miss.getModes().get(0).isLockedIn());
    assertEquals(PipingFivLikelihood.LOW, miss.getLikelihood());
    assertTrue(miss.getRecommendation().contains("outside"));
  }

  @Test
  @DisplayName("Severity grades on momentum flux, diameter ratio and mode order")
  public void testSeverityGrading() {
    double c = 400.0;
    double sr = FlowInducedPulsationScreening.DEFAULT_STROUHAL_MODE_A;

    double bigD = 0.2477;
    double bigL = 3.0;
    double bigV = (c / (4.0 * bigL)) * (Math.PI * bigD / 4.0) / sr;
    FlowInducedPulsationResult big = FlowInducedPulsationScreening.screen("large stub", bigL, bigD, RUN_ID, bigV, 46.0,
        c);

    double smallD = 0.0243;
    double smallL = 0.5;
    double smallV = (c / (4.0 * smallL)) * (Math.PI * smallD / 4.0) / sr;
    FlowInducedPulsationResult small = FlowInducedPulsationScreening.screen("small drain", smallL, smallD, RUN_ID,
        smallV, 46.0, c);

    assertTrue(big.isAnyModeLockedIn());
    assertTrue(small.isAnyModeLockedIn());
    assertEquals(1.0, big.getContributingFactors().get("modeWeight"), 1.0e-9);
    assertTrue(
        big.getContributingFactors().get("couplingFactor") > small.getContributingFactors().get("couplingFactor"),
        "a wider branch mouth must couple more strongly to the shear layer");
    assertTrue(big.getContributingFactors().get("severity") > small.getContributingFactors().get("severity"));
    assertTrue(big.toJson().contains("branchName"));
  }

  @Test
  @DisplayName("A higher speed of sound lifts every mode and raises the resonance velocity")
  public void testDryGasShiftsResonanceVelocityUpwards() {
    double length = 1.5;
    double d = 0.15;
    FlowInducedPulsationResult liquidFilled = FlowInducedPulsationScreening.screen("drain", length, d, RUN_ID, 20.0,
        56.0, 843.0);
    FlowInducedPulsationResult gasFilled = FlowInducedPulsationScreening.screen("drain", length, d, RUN_ID, 20.0, 46.0,
        374.0);

    List<FlowInducedPulsationResult.BranchMode> wet = liquidFilled.getModes();
    List<FlowInducedPulsationResult.BranchMode> dry = gasFilled.getModes();
    assertTrue(wet.get(0).getFrequencyHz() > dry.get(0).getFrequencyHz(),
        "a liquid-filled leg resonates well above the same leg once it drains to gas");
    assertEquals(843.0 / 374.0, wet.get(0).getFrequencyHz() / dry.get(0).getFrequencyHz(), 1.0e-9);
    assertTrue(liquidFilled.getLowestResonanceVelocityMPerS() > gasFilled.getLowestResonanceVelocityMPerS());
  }

  @Test
  @DisplayName("Non-physical geometry, acoustics and numerics are rejected")
  public void testInputValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> FlowInducedPulsationScreening.screen("b", 0.0, 0.1, RUN_ID, 20.0, 46.0, 400.0));
    assertThrows(IllegalArgumentException.class,
        () -> FlowInducedPulsationScreening.screen("b", 1.0, 0.0, RUN_ID, 20.0, 46.0, 400.0));
    assertThrows(IllegalArgumentException.class,
        () -> FlowInducedPulsationScreening.screen("b", 1.0, 0.1, 0.0, 20.0, 46.0, 400.0));
    assertThrows(IllegalArgumentException.class,
        () -> FlowInducedPulsationScreening.screen("b", 1.0, 0.1, RUN_ID, 20.0, 46.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> FlowInducedPulsationScreening.screen("b", 1.0, 0.1, RUN_ID, -1.0, 46.0, 400.0));
    assertThrows(IllegalArgumentException.class, () -> FlowInducedPulsationScreening.screen("b", 1.0, 0.1, -0.01,
        RUN_ID, 20.0, 46.0, 400.0, AcousticTermination.CLOSED, 0.37, 5));
    assertThrows(IllegalArgumentException.class, () -> FlowInducedPulsationScreening.screen("b", 1.0, 0.1, 0.0, RUN_ID,
        20.0, 46.0, 400.0, AcousticTermination.CLOSED, 0.0, 5));
    assertThrows(IllegalArgumentException.class, () -> FlowInducedPulsationScreening.screen("b", 1.0, 0.1, 0.0, RUN_ID,
        20.0, 46.0, 400.0, AcousticTermination.CLOSED, 0.37, 0));
  }
}
