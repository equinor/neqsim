package neqsim.process.fielddevelopment.olga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SyntheticWellTrajectory} -- the ported, equivalent Java class for
 * {@code agentic_flow_assurance}'s {@code well-inflow-pi} skill's own {@code synthetic_trajectory.py}.
 *
 * <p>
 * {@link SyntheticWellTrajectory} ships with no built-in {@code Statistics} basis (see its own "Data governance"
 * Javadoc section), so every test here constructs its own small, self-contained {@link
 * SyntheticWellTrajectory.Statistics} fixture with round, easy-to-hand-verify, INVENTED numbers -- not derived from
 * any real well or field -- mirroring the ported Python module's own test file's fixture values exactly.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SyntheticWellTrajectoryTest {
  private static final SyntheticWellTrajectory.Statistics FIXTURE_STATISTICS = new SyntheticWellTrajectory.Statistics(
      new SyntheticWellTrajectory.DepthTier[] {
          new SyntheticWellTrajectory.DepthTier("shallow", 10, 2.0, 200.0, 1000.0),
          new SyntheticWellTrajectory.DepthTier("medium", 10, 1.0, 400.0, 2000.0),
          new SyntheticWellTrajectory.DepthTier("deep", 10, 0.5, 600.0, 3000.0),
      },
      80.0,
      "Fixture statistics for unit testing only -- invented round numbers, not derived from any real well or field.");

  private static SyntheticWellTrajectory.Result generate(double targetTvdMetre) {
    return SyntheticWellTrajectory.generateSyntheticTrajectory(targetTvdMetre, 0.0, null, 8, FIXTURE_STATISTICS);
  }

  private static SyntheticWellTrajectory.Result generate(double targetTvdMetre, double reservoirContactLengthMetre) {
    return SyntheticWellTrajectory.generateSyntheticTrajectory(targetTvdMetre, reservoirContactLengthMetre, null, 8,
        FIXTURE_STATISTICS);
  }

  @Test
  void testFinalTvdMatchesTargetWithinTolerance() {
    for (double target : new double[] {500.0, 1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 4000.0}) {
      SyntheticWellTrajectory.Result result = generate(target);
      assertEquals(target, result.tvd[result.tvd.length - 1], 0.5);
    }
  }

  @Test
  void testMdAndTvdAreStrictlyAscending() {
    SyntheticWellTrajectory.Result result = generate(2500.0);
    for (int i = 1; i < result.md.length; i++) {
      assertTrue(result.md[i] > result.md[i - 1], "md must be strictly ascending (no degenerate zero-length segment)");
      assertTrue(result.tvd[i] >= result.tvd[i - 1], "tvd must be ascending");
    }
  }

  @Test
  void testProvenanceIsAlwaysSyntheticTemplate() {
    assertEquals("synthetic_template", generate(2000.0).provenance);
  }

  @Test
  void testTierInterpolationExactTierPointReturnsTierValue() {
    SyntheticWellTrajectory.Result result = generate(2000.0);
    assertEquals(1.0, result.tierInterpolation.buildRateDegPer30m, 1e-9);
    assertEquals(400.0, result.tierInterpolation.kopMdMetre, 1e-9);
  }

  @Test
  void testTierInterpolationMidpointInterpolatesLinearly() {
    // Halfway between shallow (1000, 2.0) and medium (2000, 1.0) -> 1.5.
    SyntheticWellTrajectory.Result result = generate(1500.0);
    assertEquals(1.5, result.tierInterpolation.buildRateDegPer30m, 1e-9);
  }

  @Test
  void testTierInterpolationBelowRangeFlatExtrapolatesToShallowestTier() {
    // A target of 300 is shallower than the shallow tier's own TVD (1000) but still deeper than its KOP (200), so
    // this exercises genuine flat extrapolation of the build-rate metric itself, not the vertical-fallback path.
    SyntheticWellTrajectory.Result result = generate(300.0);
    assertEquals(2.0, result.tierInterpolation.buildRateDegPer30m, 1e-9);
  }

  @Test
  void testTierInterpolationAboveRangeFlatExtrapolatesToDeepestTier() {
    SyntheticWellTrajectory.Result result = generate(50000.0);
    assertEquals(0.5, result.tierInterpolation.buildRateDegPer30m, 1e-9);
  }

  @Test
  void testVerticalSectionHasMdEqualTvd() {
    SyntheticWellTrajectory.Result result = generate(3000.0);
    // The first two stations are always the vertical section: (0,0) and (kopMd, kopMd).
    assertEquals(0.0, result.md[0], 1e-9);
    assertEquals(0.0, result.tvd[0], 1e-9);
    assertEquals(result.tvd[1], result.md[1], 1e-9);
  }

  @Test
  void testTargetShallowerThanInterpolatedKopFallsBackToVertical() {
    // Shallow tier KOP is 200m; a target of 100m is shallower than that.
    SyntheticWellTrajectory.Result result = generate(100.0);
    assertEquals(2, result.md.length);
    assertEquals(0.0, result.md[0], 1e-9);
    assertEquals(100.0, result.md[1], 1e-9);
    assertEquals(0.0, result.tvd[0], 1e-9);
    assertEquals(100.0, result.tvd[1], 1e-9);
    assertTrue(result.warnings.stream().anyMatch(w -> w.contains("falling back to a purely vertical")));
  }

  @Test
  void testVerticalFallbackStillAppendsReservoirContact() {
    SyntheticWellTrajectory.Result result = generate(100.0, 50.0);
    assertEquals(150.0, result.md[result.md.length - 1], 1e-9);
    assertEquals(150.0, result.tvd[result.tvd.length - 1], 1e-9);
  }

  @Test
  void testNonPositiveTargetTvdRaises() {
    assertThrows(SyntheticWellTrajectory.SyntheticTrajectoryException.class, () -> generate(0.0));
    assertThrows(SyntheticWellTrajectory.SyntheticTrajectoryException.class, () -> generate(-500.0));
  }

  @Test
  void testNegativeReservoirContactLengthRaises() {
    assertThrows(SyntheticWellTrajectory.SyntheticTrajectoryException.class, () -> generate(2000.0, -10.0));
  }

  @Test
  void testMaxInclinationOverrideIsCappedWithWarning() {
    SyntheticWellTrajectory.Result result = SyntheticWellTrajectory.generateSyntheticTrajectory(2500.0, 0.0, 95.0, 8,
        FIXTURE_STATISTICS);
    assertEquals(89.0, result.tierInterpolation.maxInclinationDeg, 1e-9);
    assertTrue(result.warnings.stream().anyMatch(w -> w.contains("was capped")));
  }

  @Test
  void testTargetFarBeyondSampledRangeWarnsAboutLongTangent() {
    // Deep tier tops out at 3000m TVD in the fixture; a target far beyond that, extrapolated flat, should need an
    // implausibly long tangent section and must say so, not silently return it.
    SyntheticWellTrajectory.Result result = generate(20000.0);
    assertTrue(result.warnings.stream().anyMatch(w -> w.contains("tangent/hold section")));
    // It must still genuinely reach the requested target, though.
    assertEquals(20000.0, result.tvd[result.tvd.length - 1], 0.5);
  }

  @Test
  void testReservoirContactLengthExtendsMdWithoutRequiringFurtherTvdTarget() {
    SyntheticWellTrajectory.Result base = generate(2500.0);
    SyntheticWellTrajectory.Result extended = generate(2500.0, 1000.0);
    assertEquals(base.md[base.md.length - 1] + 1000.0, extended.md[extended.md.length - 1], 1e-9);
    // TVD still increases somewhat (terminal inclination is not exactly 90 degrees), but is not required to reach
    // a new target.
    assertTrue(extended.tvd[extended.tvd.length - 1] >= base.tvd[base.tvd.length - 1]);
  }

  @Test
  void testWarningIsAlwaysNonEmptyEvenWithNoInternalWarnings() {
    SyntheticWellTrajectory.Result result = generate(2000.0);
    assertTrue(result.warnings.isEmpty()); // a "normal" case with no edge-case warnings
    String warningText = SyntheticWellTrajectory.syntheticTrajectoryWarning(result);
    assertFalse(warningText.isEmpty());
    assertTrue(warningText.contains(SyntheticWellTrajectory.SYNTHETIC_TRAJECTORY_WARNING_PREFIX));
    assertTrue(warningText.contains("SYNTHETIC TEMPLATE"));
  }

  @Test
  void testBasisDescriptionFlowsThroughFromStatisticsToResultAndWarning() {
    // Every caller-supplied Statistics basis must be able to disclose its own real provenance, since this class
    // itself ships with no embedded data (see its own "Data governance" Javadoc section).
    SyntheticWellTrajectory.Result result = generate(2000.0);
    assertEquals(FIXTURE_STATISTICS.basisDescription, result.basis);
    String warningText = SyntheticWellTrajectory.syntheticTrajectoryWarning(result);
    assertTrue(warningText.contains(FIXTURE_STATISTICS.basisDescription));
  }

  @Test
  void testConvenienceOverloadRequiresCallerSuppliedStatistics() {
    // The simple 2-arg overload still requires a Statistics argument -- there is no implicit default to fall back
    // to, by design.
    SyntheticWellTrajectory.Result result =
        SyntheticWellTrajectory.generateSyntheticTrajectory(2500.0, FIXTURE_STATISTICS);
    assertEquals("synthetic_template", result.provenance);
    assertEquals(2500.0, result.tvd[result.tvd.length - 1], 0.5);
  }
}
