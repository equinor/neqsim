package neqsim.process.safety.vibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FlowInducedPulsationScreening}.
 */
public class FlowInducedPulsationScreeningTest {

  private static final double RUN_ID = 0.3652;

  @Test
  @DisplayName("Quarter-wave modes follow (2n-1)c/(4 Leff) with the end correction applied")
  public void testQuarterWaveModeFrequencies() {
    double branchLength = 1.5;
    double branchId = 0.15;
    double c = 400.0;
    FlowInducedPulsationResult result = FlowInducedPulsationScreening.screen("drain", branchLength, branchId, RUN_ID,
        20.0, 46.0, c);

    double effectiveLength = branchLength + FlowInducedPulsationScreening.END_CORRECTION_FACTOR * branchId;
    List<FlowInducedPulsationResult.BranchMode> modes = result.getModes();
    assertEquals(FlowInducedPulsationScreening.MODE_COUNT, modes.size());
    for (int n = 1; n <= modes.size(); n++) {
      FlowInducedPulsationResult.BranchMode mode = modes.get(n - 1);
      assertEquals(n, mode.getModeNumber());
      assertEquals((2 * n - 1) * c / (4.0 * effectiveLength), mode.getFrequencyHz(), 1.0e-9);
      assertEquals(mode.getFrequencyHz() * branchId / 20.0, mode.getStrouhalNumber(), 1.0e-9);
    }
    assertEquals(effectiveLength, result.getContributingFactors().get("effectiveBranchLength_m"), 1.0e-9);
  }

  @Test
  @DisplayName("A branch tuned into the lock-in band is flagged, one outside it is not")
  public void testLockInDetection() {
    // Fundamental mode at 400/(4*1.56) = 64.1 Hz; with d = 0.15 m and v = 20 m/s, St = 0.48 -> in band.
    FlowInducedPulsationResult inBand = FlowInducedPulsationScreening.screen("tuned branch", 1.5, 0.15, RUN_ID, 20.0,
        46.0, 400.0);
    assertTrue(inBand.isAnyModeLockedIn());
    assertTrue(inBand.getModes().get(0).isLockedIn());
    assertTrue(inBand.getModes().get(0).getStrouhalNumber() >= FlowInducedPulsationScreening.LOCK_IN_STROUHAL_LOW);
    assertTrue(inBand.getModes().get(0).getStrouhalNumber() <= FlowInducedPulsationScreening.LOCK_IN_STROUHAL_HIGH);

    // A very short branch pushes every mode far above the band, so nothing is driven.
    FlowInducedPulsationResult offBand = FlowInducedPulsationScreening.screen("short branch", 0.15, 0.025, RUN_ID, 20.0,
        46.0, 400.0);
    assertFalse(offBand.isAnyModeLockedIn());
    assertEquals(PipingFivLikelihood.LOW, offBand.getLikelihood());
    assertTrue(offBand.getRecommendation().contains("No acoustic mode"));
  }

  @Test
  @DisplayName("Dry gas raises the branch frequencies and shifts the lock-in velocity band upwards")
  public void testDryGasShiftsLockInBandRelativeToWetGas() {
    // A two-phase mixture has a far lower speed of sound than the dry gas it becomes after the liquid is removed.
    double wetSpeedOfSound = 120.0;
    double drySpeedOfSound = 400.0;
    double velocity = 20.0;

    FlowInducedPulsationResult wet = FlowInducedPulsationScreening.screen("cross-over stub", 1.5, 0.15, RUN_ID,
        velocity, 56.0, wetSpeedOfSound);
    FlowInducedPulsationResult dry = FlowInducedPulsationScreening.screen("cross-over stub", 1.5, 0.15, RUN_ID,
        velocity, 46.0, drySpeedOfSound);

    double wetFundamental = wet.getModes().get(0).getFrequencyHz();
    double dryFundamental = dry.getModes().get(0).getFrequencyHz();
    assertTrue(dryFundamental > wetFundamental, "the dry-gas branch mode must sit above the wet-gas one");
    assertEquals(drySpeedOfSound / wetSpeedOfSound, dryFundamental / wetFundamental, 1.0e-9);

    // The strongest mode is the fundamental: in wet gas it sits below the lock-in band at this velocity, in dry gas
    // the higher speed of sound lifts it into the band.
    assertFalse(wet.getModes().get(0).isLockedIn());
    assertTrue(dry.getModes().get(0).isLockedIn());
    assertTrue(dry.getLowestLockInVelocityMPerS() > wet.getLowestLockInVelocityMPerS());
  }

  @Test
  @DisplayName("Severity grades on momentum flux and branch-to-run diameter ratio")
  public void testSeverityGrading() {
    FlowInducedPulsationResult small = FlowInducedPulsationScreening.screen("small tapping", 0.36, 0.036, RUN_ID, 20.0,
        46.0, 400.0);
    FlowInducedPulsationResult large = FlowInducedPulsationScreening.screen("large stub", 1.5, 0.15, RUN_ID, 20.0, 46.0,
        400.0);

    assertTrue(small.isAnyModeLockedIn());
    assertTrue(large.isAnyModeLockedIn());
    assertTrue(
        large.getContributingFactors().get("couplingFactor") > small.getContributingFactors().get("couplingFactor"),
        "a wider branch mouth must couple more strongly to the shear layer");
    assertTrue(large.getContributingFactors().get("severity") > small.getContributingFactors().get("severity"));
    assertTrue(large.toJson().contains("branchName"));
  }

  @Test
  @DisplayName("Non-physical geometry and acoustics are rejected")
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
  }
}
