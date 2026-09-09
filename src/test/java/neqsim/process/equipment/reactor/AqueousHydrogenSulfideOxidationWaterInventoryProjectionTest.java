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

    assertEquals(2.5 * small.getLowerRateMeanLossMolesPerHour(), large.getLowerRateMeanLossMolesPerHour(), 1.0e-20);
    assertEquals(2.5 * small.getNominalMeanLossMolesPerHour(), large.getNominalMeanLossMolesPerHour(), 1.0e-20);
    assertEquals(2.5 * small.getUpperRateMeanLossMolesPerHour(), large.getUpperRateMeanLossMolesPerHour(), 1.0e-20);
    assertEquals(2.5 * small.getNominalReactedMoles(), large.getNominalReactedMoles(), 1.0e-20);

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

    assertEquals(unsplitReacted, splitReacted, 1.0e-20);
  }

  @Test
  void testProjectionFailsClosedForMissingOrInvalidWaterInventory() {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment = segmentResult(referenceSegment(1.0));

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(null, 1.0));
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

  private static void assertProjectionPath(double meanLossMolalityPerHour, double reactedMolality,
      double meanLossMolesPerHour, double meanLossMolesPerSecond, double reactedMoles, double durationHours) {
    assertEquals(meanLossMolalityPerHour * WATER_INVENTORY_KG, meanLossMolesPerHour, 0.0);
    assertEquals(meanLossMolesPerHour / 3600.0, meanLossMolesPerSecond, 0.0);
    assertEquals(reactedMolality * WATER_INVENTORY_KG, reactedMoles, 0.0);
    assertEquals(reactedMoles, meanLossMolesPerHour * durationHours, 1.0e-20);
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
