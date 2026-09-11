package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests dimensional water-inventory projection of qualified aqueous H2S/O2 evidence. */
public class AqueousHydrogenSulfideOxidationWaterInventoryProjectionTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final double NUMERICAL_TOLERANCE = 1.0e-17;

  @Test
  void testProjectionClosesPositiveDurationInventoryInHoursAndSeconds() {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment = segmentResult(referenceSegment(10.0));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result projection = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(segment, WATER_INVENTORY_KG);

    assertEquals(segment.getIndex(), projection.getSegmentIndex());
    assertEquals(10.0, projection.getDurationHours(), 0.0);
    assertEquals(WATER_INVENTORY_KG, projection.getWaterInventoryKg(), 0.0);

    assertProjectionPath(segment.getLowerRateMeanLossRateMolalityPerHour(),
        segment.getLowerRateReactedTotalSulfideMolality(), projection.getLowerRateMeanLossMolesPerHour(),
        projection.getLowerRateMeanLossMolesPerSecond(), projection.getLowerRateReactedMoles(), 10.0);
    assertProjectionPath(segment.getNominalMeanLossRateMolalityPerHour(),
        segment.getNominalReactedTotalSulfideMolality(), projection.getNominalMeanLossMolesPerHour(),
        projection.getNominalMeanLossMolesPerSecond(), projection.getNominalReactedMoles(), 10.0);
    assertProjectionPath(segment.getUpperRateMeanLossRateMolalityPerHour(),
        segment.getUpperRateReactedTotalSulfideMolality(), projection.getUpperRateMeanLossMolesPerHour(),
        projection.getUpperRateMeanLossMolesPerSecond(), projection.getUpperRateReactedMoles(), 10.0);
  }

  @Test
  void testZeroDurationPreservesDifferentialLimitAndZeroReaction() {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment = segmentResult(referenceSegment(0.0));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result projection = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(segment, WATER_INVENTORY_KG);

    assertEquals(0.0, projection.getDurationHours(), 0.0);
    assertEquals(segment.getLowerRateInletLossRateMolalityPerHour() * WATER_INVENTORY_KG,
        projection.getLowerRateMeanLossMolesPerHour(), 0.0);
    assertEquals(segment.getNominalInletLossRateMolalityPerHour() * WATER_INVENTORY_KG,
        projection.getNominalMeanLossMolesPerHour(), 0.0);
    assertEquals(segment.getUpperRateInletLossRateMolalityPerHour() * WATER_INVENTORY_KG,
        projection.getUpperRateMeanLossMolesPerHour(), 0.0);
    assertTrue(Double.isFinite(projection.getNominalMeanLossMolesPerSecond()));
    assertEquals(0.0, projection.getLowerRateReactedMoles(), 0.0);
    assertEquals(0.0, projection.getNominalReactedMoles(), 0.0);
    assertEquals(0.0, projection.getUpperRateReactedMoles(), 0.0);
  }

  @Test
  void testReferenceSegmentPreservesScalingAndFitScatterOrdering() {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment = segmentResult(referenceSegment(10.0));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result small = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(segment, 100.0);
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result large = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(segment, 250.0);

    assertEquals(2.5 * small.getLowerRateMeanLossMolesPerHour(), large.getLowerRateMeanLossMolesPerHour(),
        NUMERICAL_TOLERANCE);
    assertEquals(2.5 * small.getNominalMeanLossMolesPerHour(), large.getNominalMeanLossMolesPerHour(),
        NUMERICAL_TOLERANCE);
    assertEquals(2.5 * small.getUpperRateMeanLossMolesPerHour(), large.getUpperRateMeanLossMolesPerHour(),
        NUMERICAL_TOLERANCE);
    assertEquals(2.5 * small.getNominalReactedMoles(), large.getNominalReactedMoles(), NUMERICAL_TOLERANCE);

    assertTrue(large.getLowerRateMeanLossMolesPerHour() < large.getNominalMeanLossMolesPerHour());
    assertTrue(large.getNominalMeanLossMolesPerHour() < large.getUpperRateMeanLossMolesPerHour());
  }

  @Test
  void testConstantWaterInventorySegmentSplitClosesTotalReaction() {
    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(10.0)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(4.0), referenceSegment(6.0)));

    double unsplitReacted = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(unsplit.getSegmentResults().get(0), WATER_INVENTORY_KG).getNominalReactedMoles();
    double splitReacted = 0.0;
    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : split.getSegmentResults()) {
      splitReacted += AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, WATER_INVENTORY_KG)
          .getNominalReactedMoles();
    }

    assertEquals(unsplitReacted, splitReacted, NUMERICAL_TOLERANCE);
  }

  @Test
  void testProjectionFailsClosedForMissingOrInvalidWaterInventory() {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment = segmentResult(referenceSegment(1.0));

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project((AqueousHydrogenSulfideOxidationTrajectory.SegmentResult) null, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, -1.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, Double.MIN_VALUE));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, Double.POSITIVE_INFINITY));
  }

  @Test
  void testTrajectoryProjectionClosesAllPathsAndPreservesSourceOrder() {
    AqueousHydrogenSulfideOxidationTrajectory.Result trajectory = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(4.0), referenceSegment(6.0)));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.TrajectoryResult projection = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(trajectory, WATER_INVENTORY_KG);

    assertEquals(WATER_INVENTORY_KG, projection.getWaterInventoryKg(), 0.0);
    assertEquals(trajectory.getTotalTimeHours(), projection.getTotalTimeHours(), 0.0);
    assertEquals(2, projection.getSegmentProjections().size());
    assertEquals(0, projection.getSegmentProjections().get(0).getSegmentIndex());
    assertEquals(1, projection.getSegmentProjections().get(1).getSegmentIndex());

    assertEquals((trajectory.getInitialTotalSulfideMolality() - trajectory.getFinalTotalSulfideMolalityAtLowerRate())
        * WATER_INVENTORY_KG, projection.getLowerRateReactedMoles(), NUMERICAL_TOLERANCE);
    assertEquals(trajectory.getReactedTotalSulfideMolality() * WATER_INVENTORY_KG, projection.getNominalReactedMoles(),
        NUMERICAL_TOLERANCE);
    assertEquals((trajectory.getInitialTotalSulfideMolality() - trajectory.getFinalTotalSulfideMolalityAtUpperRate())
        * WATER_INVENTORY_KG, projection.getUpperRateReactedMoles(), NUMERICAL_TOLERANCE);
    assertEquals(0.0, projection.getLowerRateClosureResidualMoles(), NUMERICAL_TOLERANCE);
    assertEquals(0.0, projection.getNominalClosureResidualMoles(), NUMERICAL_TOLERANCE);
    assertEquals(0.0, projection.getUpperRateClosureResidualMoles(), NUMERICAL_TOLERANCE);
    assertTrue(projection.getLowerRateReactedMoles() < projection.getNominalReactedMoles());
    assertTrue(projection.getNominalReactedMoles() < projection.getUpperRateReactedMoles());
  }

  @Test
  void testTrajectoryProjectionMatchesSingleSegmentAndIsSplitInvariant() {
    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(10.0)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(4.0), referenceSegment(6.0)));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result segmentProjection = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(unsplit.getSegmentResults().get(0), WATER_INVENTORY_KG);
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.TrajectoryResult whole = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(unsplit, WATER_INVENTORY_KG);
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.TrajectoryResult parts = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(split, WATER_INVENTORY_KG);

    assertEquals(segmentProjection.getLowerRateReactedMoles(), whole.getLowerRateReactedMoles(), 0.0);
    assertEquals(segmentProjection.getNominalReactedMoles(), whole.getNominalReactedMoles(), 0.0);
    assertEquals(segmentProjection.getUpperRateReactedMoles(), whole.getUpperRateReactedMoles(), 0.0);
    assertEquals(whole.getLowerRateReactedMoles(), parts.getLowerRateReactedMoles(), NUMERICAL_TOLERANCE);
    assertEquals(whole.getNominalReactedMoles(), parts.getNominalReactedMoles(), NUMERICAL_TOLERANCE);
    assertEquals(whole.getUpperRateReactedMoles(), parts.getUpperRateReactedMoles(), NUMERICAL_TOLERANCE);
  }

  @Test
  void testTrajectoryProjectionZeroDurationIsExactAndResultIsDefensive() {
    AqueousHydrogenSulfideOxidationTrajectory.Result trajectory = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(0.0)));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.TrajectoryResult projection = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project(trajectory, WATER_INVENTORY_KG);

    assertEquals(0.0, projection.getLowerRateReactedMoles(), 0.0);
    assertEquals(0.0, projection.getNominalReactedMoles(), 0.0);
    assertEquals(0.0, projection.getUpperRateReactedMoles(), 0.0);
    assertThrows(UnsupportedOperationException.class,
        () -> projection.getSegmentProjections().add(projection.getSegmentProjections().get(0)));
  }

  @Test
  void testTrajectoryProjectionFailsClosedForMissingOrInvalidWaterInventory() {
    AqueousHydrogenSulfideOxidationTrajectory.Result trajectory = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(1.0)));

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .project((AqueousHydrogenSulfideOxidationTrajectory.Result) null, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(trajectory, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(trajectory, -1.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(trajectory, Double.MIN_VALUE));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(trajectory, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(trajectory, Double.POSITIVE_INFINITY));
  }

  @Test
  void testReactedMolesTargetReproducesHalfInventoryAndForwardExposure() {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment = referenceSegment(100.0);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentEvidence = segmentResult(segment);
    double initialMoles = INITIAL_TOTAL_SULFIDE_MOLALITY * WATER_INVENTORY_KG;
    double targetReactedMoles = 0.5 * initialMoles;

    AqueousHydrogenSulfideOxidationWaterInventoryProjection.ReactedMolesTargetResult target = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, targetReactedMoles, WATER_INVENTORY_KG,
            Collections.singletonList(segment));

    assertEquals(initialMoles, target.getInitialTotalSulfideMoles(), 0.0);
    assertEquals(targetReactedMoles, target.getTargetReactedMoles(), 0.0);
    assertEquals(0.5 * initialMoles, target.getTargetRemainingMoles(), 0.0);
    assertEquals(0.5, target.getTargetRemainingFraction(), 0.0);
    assertEquals(22.4288, target.getCrossingRange().getNominalTimeHours(), 1.0e-4);

    assertTargetReaction(targetReactedMoles, initialMoles, segmentEvidence.getUpperPseudoFirstOrderRate(),
        target.getCrossingRange().getShortestTimeHours());
    assertTargetReaction(targetReactedMoles, initialMoles, segmentEvidence.getNominalPseudoFirstOrderRate(),
        target.getCrossingRange().getNominalTimeHours());
    assertTargetReaction(targetReactedMoles, initialMoles, segmentEvidence.getLowerPseudoFirstOrderRate(),
        target.getCrossingRange().getLongestTimeHours());
  }

  @Test
  void testReactedMolesTargetIsMonotonicAndPreservesLinearWaterScaling() {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment = referenceSegment(100.0);
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.ReactedMolesTargetResult smaller = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.006, WATER_INVENTORY_KG,
            Collections.singletonList(segment));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.ReactedMolesTargetResult larger = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.015, WATER_INVENTORY_KG,
            Collections.singletonList(segment));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.ReactedMolesTargetResult scaled = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.030, 2.0 * WATER_INVENTORY_KG,
            Collections.singletonList(segment));

    assertTrue(smaller.getCrossingRange().getShortestTimeHours() < larger.getCrossingRange().getShortestTimeHours());
    assertTrue(smaller.getCrossingRange().getNominalTimeHours() < larger.getCrossingRange().getNominalTimeHours());
    assertTrue(smaller.getCrossingRange().getLongestTimeHours() < larger.getCrossingRange().getLongestTimeHours());
    assertEquals(larger.getCrossingRange().getShortestTimeHours(), scaled.getCrossingRange().getShortestTimeHours(),
        NUMERICAL_TOLERANCE);
    assertEquals(larger.getCrossingRange().getNominalTimeHours(), scaled.getCrossingRange().getNominalTimeHours(),
        NUMERICAL_TOLERANCE);
    assertEquals(larger.getCrossingRange().getLongestTimeHours(), scaled.getCrossingRange().getLongestTimeHours(),
        NUMERICAL_TOLERANCE);
  }

  @Test
  void testReactedMolesTargetIsSplitInvariantAndPreservesCrossingSegment() {
    double targetReactedMoles = 0.015;
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.ReactedMolesTargetResult unsplit = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, targetReactedMoles, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(100.0)));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.ReactedMolesTargetResult split = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, targetReactedMoles, WATER_INVENTORY_KG,
            Arrays.asList(referenceSegment(10.0), referenceSegment(90.0)));

    assertEquals(unsplit.getCrossingRange().getShortestTimeHours(), split.getCrossingRange().getShortestTimeHours(),
        NUMERICAL_TOLERANCE);
    assertEquals(unsplit.getCrossingRange().getNominalTimeHours(), split.getCrossingRange().getNominalTimeHours(),
        NUMERICAL_TOLERANCE);
    assertEquals(unsplit.getCrossingRange().getLongestTimeHours(), split.getCrossingRange().getLongestTimeHours(),
        NUMERICAL_TOLERANCE);
    assertEquals(0, unsplit.getCrossingRange().getNominalCrossingSegmentIndex());
    assertEquals(1, split.getCrossingRange().getNominalCrossingSegmentIndex());
  }

  @Test
  void testReactedMolesTargetIdentityAndInvalidInputsFailClosed() {
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.ReactedMolesTargetResult identity = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.0, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(0.0)));
    assertEquals(0.0, identity.getCrossingRange().getShortestTimeHours(), 0.0);
    assertEquals(0.0, identity.getCrossingRange().getNominalTimeHours(), 0.0);
    assertEquals(0.0, identity.getCrossingRange().getLongestTimeHours(), 0.0);

    double initialMoles = INITIAL_TOTAL_SULFIDE_MOLALITY * WATER_INVENTORY_KG;
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToReactedMolesRange(19.0e-6, 0.001,
            WATER_INVENTORY_KG, Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToReactedMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, -1.0, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToReactedMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, initialMoles, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToReactedMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, Double.MIN_VALUE, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToReactedMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, 0.001, Double.NaN, Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToReactedMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, 0.015, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(1.0))));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.0, WATER_INVENTORY_KG, null));
  }

  @Test
  void testRemainingMolesTargetReproducesHalfInventoryAndAgreesWithComplement() {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment = referenceSegment(100.0);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentEvidence = segmentResult(segment);
    double initialMoles = INITIAL_TOTAL_SULFIDE_MOLALITY * WATER_INVENTORY_KG;
    double targetRemainingMoles = 0.5 * initialMoles;

    AqueousHydrogenSulfideOxidationWaterInventoryProjection.RemainingMolesTargetResult remainingTarget = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToRemainingMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, targetRemainingMoles, WATER_INVENTORY_KG,
            Collections.singletonList(segment));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.ReactedMolesTargetResult reactedTarget = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToReactedMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, initialMoles - targetRemainingMoles,
            WATER_INVENTORY_KG, Collections.singletonList(segment));

    assertEquals(initialMoles, remainingTarget.getInitialTotalSulfideMoles(), 0.0);
    assertEquals(targetRemainingMoles, remainingTarget.getTargetRemainingMoles(), 0.0);
    assertEquals(0.5 * initialMoles, remainingTarget.getTargetReactedMoles(), 0.0);
    assertEquals(0.5, remainingTarget.getTargetRemainingFraction(), 0.0);
    assertEquals(22.4288, remainingTarget.getCrossingRange().getNominalTimeHours(), 1.0e-4);
    assertEquals(reactedTarget.getCrossingRange().getShortestTimeHours(),
        remainingTarget.getCrossingRange().getShortestTimeHours(), 0.0);
    assertEquals(reactedTarget.getCrossingRange().getNominalTimeHours(),
        remainingTarget.getCrossingRange().getNominalTimeHours(), 0.0);
    assertEquals(reactedTarget.getCrossingRange().getLongestTimeHours(),
        remainingTarget.getCrossingRange().getLongestTimeHours(), 0.0);

    assertTargetRemaining(targetRemainingMoles, initialMoles, segmentEvidence.getUpperPseudoFirstOrderRate(),
        remainingTarget.getCrossingRange().getShortestTimeHours());
    assertTargetRemaining(targetRemainingMoles, initialMoles, segmentEvidence.getNominalPseudoFirstOrderRate(),
        remainingTarget.getCrossingRange().getNominalTimeHours());
    assertTargetRemaining(targetRemainingMoles, initialMoles, segmentEvidence.getLowerPseudoFirstOrderRate(),
        remainingTarget.getCrossingRange().getLongestTimeHours());
  }

  @Test
  void testRemainingMolesTargetIsMonotonicAndPreservesLinearWaterScaling() {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment = referenceSegment(100.0);
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.RemainingMolesTargetResult largerRemaining = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToRemainingMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.018, WATER_INVENTORY_KG,
            Collections.singletonList(segment));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.RemainingMolesTargetResult smallerRemaining = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToRemainingMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.009, WATER_INVENTORY_KG,
            Collections.singletonList(segment));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.RemainingMolesTargetResult scaled = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToRemainingMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.036, 2.0 * WATER_INVENTORY_KG,
            Collections.singletonList(segment));

    assertTrue(largerRemaining.getCrossingRange().getShortestTimeHours() < smallerRemaining.getCrossingRange()
        .getShortestTimeHours());
    assertTrue(largerRemaining.getCrossingRange().getNominalTimeHours() < smallerRemaining.getCrossingRange()
        .getNominalTimeHours());
    assertTrue(largerRemaining.getCrossingRange().getLongestTimeHours() < smallerRemaining.getCrossingRange()
        .getLongestTimeHours());
    assertEquals(largerRemaining.getCrossingRange().getShortestTimeHours(),
        scaled.getCrossingRange().getShortestTimeHours(), NUMERICAL_TOLERANCE);
    assertEquals(largerRemaining.getCrossingRange().getNominalTimeHours(),
        scaled.getCrossingRange().getNominalTimeHours(), NUMERICAL_TOLERANCE);
    assertEquals(largerRemaining.getCrossingRange().getLongestTimeHours(),
        scaled.getCrossingRange().getLongestTimeHours(), NUMERICAL_TOLERANCE);
  }

  @Test
  void testRemainingMolesTargetIsSplitInvariantAndIdentityIsExact() {
    double initialMoles = INITIAL_TOTAL_SULFIDE_MOLALITY * WATER_INVENTORY_KG;
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.RemainingMolesTargetResult unsplit = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToRemainingMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.015, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(100.0)));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.RemainingMolesTargetResult split = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToRemainingMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, 0.015, WATER_INVENTORY_KG,
            Arrays.asList(referenceSegment(10.0), referenceSegment(90.0)));
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.RemainingMolesTargetResult identity = AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToRemainingMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, initialMoles, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(0.0)));

    assertEquals(unsplit.getCrossingRange().getShortestTimeHours(), split.getCrossingRange().getShortestTimeHours(),
        NUMERICAL_TOLERANCE);
    assertEquals(unsplit.getCrossingRange().getNominalTimeHours(), split.getCrossingRange().getNominalTimeHours(),
        NUMERICAL_TOLERANCE);
    assertEquals(unsplit.getCrossingRange().getLongestTimeHours(), split.getCrossingRange().getLongestTimeHours(),
        NUMERICAL_TOLERANCE);
    assertEquals(0, unsplit.getCrossingRange().getNominalCrossingSegmentIndex());
    assertEquals(1, split.getCrossingRange().getNominalCrossingSegmentIndex());
    assertEquals(0.0, identity.getTargetReactedMoles(), 0.0);
    assertEquals(1.0, identity.getTargetRemainingFraction(), 0.0);
    assertEquals(0.0, identity.getCrossingRange().getShortestTimeHours(), 0.0);
    assertEquals(0.0, identity.getCrossingRange().getNominalTimeHours(), 0.0);
    assertEquals(0.0, identity.getCrossingRange().getLongestTimeHours(), 0.0);
  }

  @Test
  void testRemainingMolesTargetInvalidInputsAndUnreachableTrajectoryFailClosed() {
    double initialMoles = INITIAL_TOTAL_SULFIDE_MOLALITY * WATER_INVENTORY_KG;
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToRemainingMolesRange(19.0e-6, 0.01,
            WATER_INVENTORY_KG, Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToRemainingMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, 0.0, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToRemainingMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, initialMoles + 0.001, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToRemainingMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, Double.NaN, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToRemainingMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, Double.MIN_VALUE, 1.0e300,
            Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToRemainingMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, 0.015, Double.POSITIVE_INFINITY,
            Collections.singletonList(referenceSegment(100.0))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.timeToRemainingMolesRange(
            INITIAL_TOTAL_SULFIDE_MOLALITY, 0.015, WATER_INVENTORY_KG,
            Collections.singletonList(referenceSegment(1.0))));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection
        .timeToRemainingMolesRange(INITIAL_TOTAL_SULFIDE_MOLALITY, initialMoles, WATER_INVENTORY_KG, null));
  }

  private static void assertTargetRemaining(double targetRemainingMoles, double initialMoles,
      double pseudoFirstOrderRate, double crossingTimeHours) {
    double remainingMoles = initialMoles * Math.exp(-pseudoFirstOrderRate * crossingTimeHours);
    assertEquals(targetRemainingMoles, remainingMoles, NUMERICAL_TOLERANCE);
  }

  private static void assertTargetReaction(double targetReactedMoles, double initialMoles, double pseudoFirstOrderRate,
      double crossingTimeHours) {
    double reactedMoles = initialMoles * -Math.expm1(-pseudoFirstOrderRate * crossingTimeHours);
    assertEquals(targetReactedMoles, reactedMoles, NUMERICAL_TOLERANCE);
  }

  private static void assertProjectionPath(double meanLossMolalityPerHour, double reactedMolality,
      double meanLossMolesPerHour, double meanLossMolesPerSecond, double reactedMoles, double durationHours) {
    assertEquals(meanLossMolalityPerHour * WATER_INVENTORY_KG, meanLossMolesPerHour, 0.0);
    assertEquals(meanLossMolesPerHour / 3600.0, meanLossMolesPerSecond, 0.0);
    assertEquals(reactedMolality * WATER_INVENTORY_KG, reactedMoles, 0.0);
    assertEquals(reactedMoles, meanLossMolesPerHour * durationHours, NUMERICAL_TOLERANCE);
  }

  private static AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult(
      AqueousHydrogenSulfideOxidationTrajectory.Segment segment) {
    return AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment)).getSegmentResults().get(0);
  }

  private static AqueousHydrogenSulfideOxidationTrajectory.Segment referenceSegment(double durationHours) {
    return new AqueousHydrogenSulfideOxidationTrajectory.Segment(durationHours, 298.15, 8.0, 0.723, 250.0e-6);
  }
}
