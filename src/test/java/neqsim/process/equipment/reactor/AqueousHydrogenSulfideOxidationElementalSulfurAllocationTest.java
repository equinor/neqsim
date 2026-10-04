package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests explicit elemental-sulfur allocation from qualified H2S/O2 sulfur-equivalent evidence. */
public class AqueousHydrogenSulfideOxidationElementalSulfurAllocationTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final double TOLERANCE = 1.0e-17;
  private static final String BASIS_IDENTIFIER = "caller-supplied-product-allocation-case-A";

  @Test
  void testQuarterAllocationClosesEveryFitPathAndPreservesOrdering() {
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result source = projection(referenceSegment(10.0),
        WATER_INVENTORY_KG);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(source, 0.25, BASIS_IDENTIFIER);

    assertEquals(source.getSegmentIndex(), allocation.getSourceSegmentIndex());
    assertEquals(source.getDurationHours(), allocation.getDurationHours(), 0.0);
    assertEquals(source.getWaterInventoryKg(), allocation.getWaterInventoryKg(), 0.0);
    assertEquals(0.25, allocation.getElementalSulfurAllocationFraction(), 0.0);
    assertEquals(BASIS_IDENTIFIER, allocation.getAllocationBasisIdentifier());

    assertPath(source.getLowerRateMeanSulfurEquivalentMassRateKgPerHour(),
        source.getLowerRateReactedSulfurEquivalentMassKg(), allocation.getLowerRate(), 0.25);
    assertPath(source.getNominalMeanSulfurEquivalentMassRateKgPerHour(),
        source.getNominalReactedSulfurEquivalentMassKg(), allocation.getNominal(), 0.25);
    assertPath(source.getUpperRateMeanSulfurEquivalentMassRateKgPerHour(),
        source.getUpperRateReactedSulfurEquivalentMassKg(), allocation.getUpperRate(), 0.25);

    assertTrue(allocation.getLowerRate().getAllocatedElementalSulfurMassKg() < allocation.getNominal()
        .getAllocatedElementalSulfurMassKg());
    assertTrue(allocation.getNominal().getAllocatedElementalSulfurMassKg() < allocation.getUpperRate()
        .getAllocatedElementalSulfurMassKg());
  }

  @Test
  void testZeroAndFullAllocationAreExactIdentities() {
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result source = projection(referenceSegment(10.0),
        WATER_INVENTORY_KG);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result zero = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(source, 0.0, "zero-allocation");
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result full = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(source, 1.0, "full-allocation");

    assertEquals(0.0, zero.getNominal().getAllocatedElementalSulfurMassRateKgPerHour(), 0.0);
    assertEquals(0.0, zero.getNominal().getAllocatedElementalSulfurMassKg(), 0.0);
    assertEquals(zero.getNominal().getSourceSulfurEquivalentMassRateKgPerHour(),
        zero.getNominal().getUnallocatedSulfurEquivalentMassRateKgPerHour(), 0.0);
    assertEquals(zero.getNominal().getSourceSulfurEquivalentMassKg(),
        zero.getNominal().getUnallocatedSulfurEquivalentMassKg(), 0.0);

    assertEquals(full.getNominal().getSourceSulfurEquivalentMassRateKgPerHour(),
        full.getNominal().getAllocatedElementalSulfurMassRateKgPerHour(), 0.0);
    assertEquals(full.getNominal().getSourceSulfurEquivalentMassKg(),
        full.getNominal().getAllocatedElementalSulfurMassKg(), 0.0);
    assertEquals(0.0, full.getNominal().getUnallocatedSulfurEquivalentMassRateKgPerHour(), 0.0);
    assertEquals(0.0, full.getNominal().getUnallocatedSulfurEquivalentMassKg(), 0.0);
  }

  @Test
  void testAllocationScalesWithWaterInventoryAndIsSegmentSplitInvariant() {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult = segmentResult(referenceSegment(10.0));
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result small = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult, 100.0), 0.4,
            BASIS_IDENTIFIER);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result large = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult, 250.0), 0.4,
            BASIS_IDENTIFIER);

    assertEquals(2.5 * small.getNominal().getAllocatedElementalSulfurMassRateKgPerHour(),
        large.getNominal().getAllocatedElementalSulfurMassRateKgPerHour(), TOLERANCE);
    assertEquals(2.5 * small.getNominal().getAllocatedElementalSulfurMassKg(),
        large.getNominal().getAllocatedElementalSulfurMassKg(), TOLERANCE);
    assertEquals(2.5 * small.getNominal().getUnallocatedSulfurEquivalentMassKg(),
        large.getNominal().getUnallocatedSulfurEquivalentMassKg(), TOLERANCE);

    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(10.0)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(4.0), referenceSegment(6.0)));
    double unsplitAllocated = allocatedNominalMass(unsplit, 0.4);
    double splitAllocated = allocatedNominalMass(split, 0.4);

    assertEquals(unsplitAllocated, splitAllocated, TOLERANCE);
  }

  @Test
  void testAllocationReceiptIsSerializableAndDeterministic() {
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result source = projection(referenceSegment(10.0),
        WATER_INVENTORY_KG);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result first = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(source, 0.5, BASIS_IDENTIFIER);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result second = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(source, 0.5, BASIS_IDENTIFIER);

    assertTrue(first instanceof Serializable);
    assertTrue(first.getNominal() instanceof Serializable);
    assertEquals(first.getNominal().getAllocatedElementalSulfurMassRateKgPerHour(),
        second.getNominal().getAllocatedElementalSulfurMassRateKgPerHour(), 0.0);
    assertEquals(first.getNominal().getAllocatedElementalSulfurMassKg(),
        second.getNominal().getAllocatedElementalSulfurMassKg(), 0.0);
    assertEquals(first.getNominal().getUnallocatedSulfurEquivalentMassKg(),
        second.getNominal().getUnallocatedSulfurEquivalentMassKg(), 0.0);
  }

  @Test
  void testMissingInvalidOrUnrepresentableAllocationFailsClosed() {
    AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result source = projection(referenceSegment(10.0),
        WATER_INVENTORY_KG);
    String oversizedIdentifier = new String(new char[257]).replace('\0', 'x');

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(null, 0.5, BASIS_IDENTIFIER));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(source, -0.1, BASIS_IDENTIFIER));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(source, 1.1, BASIS_IDENTIFIER));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(source, Double.NaN, BASIS_IDENTIFIER));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(source, Double.MIN_VALUE, BASIS_IDENTIFIER));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(source, 0.5, null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(source, 0.5, ""));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(source, 0.5, " untrimmed"));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(source, 0.5, oversizedIdentifier));
  }

  private static double allocatedNominalMass(AqueousHydrogenSulfideOxidationTrajectory.Result trajectory,
      double allocationFraction) {
    double allocated = 0.0;
    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : trajectory.getSegmentResults()) {
      AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result source = AqueousHydrogenSulfideOxidationWaterInventoryProjection
          .project(segment, WATER_INVENTORY_KG);
      allocated += AqueousHydrogenSulfideOxidationElementalSulfurAllocation
          .allocate(source, allocationFraction, BASIS_IDENTIFIER).getNominal().getAllocatedElementalSulfurMassKg();
    }
    return allocated;
  }

  private static void assertPath(double sourceRate, double sourceMass,
      AqueousHydrogenSulfideOxidationElementalSulfurAllocation.PathResult allocation, double allocationFraction) {
    assertEquals(sourceRate, allocation.getSourceSulfurEquivalentMassRateKgPerHour(), 0.0);
    assertEquals(sourceMass, allocation.getSourceSulfurEquivalentMassKg(), 0.0);
    assertEquals(sourceRate * allocationFraction, allocation.getAllocatedElementalSulfurMassRateKgPerHour(), 0.0);
    assertEquals(sourceMass * allocationFraction, allocation.getAllocatedElementalSulfurMassKg(), 0.0);
    assertEquals(sourceRate - sourceRate * allocationFraction,
        allocation.getUnallocatedSulfurEquivalentMassRateKgPerHour(), 0.0);
    assertEquals(sourceMass - sourceMass * allocationFraction, allocation.getUnallocatedSulfurEquivalentMassKg(), 0.0);
    assertEquals(0.0, allocation.getRateClosureResidualKgPerHour(), TOLERANCE);
    assertEquals(0.0, allocation.getMassClosureResidualKg(), TOLERANCE);
    assertTrue(allocation.getAllocatedElementalSulfurMassRateKgPerHour() <= allocation
        .getSourceSulfurEquivalentMassRateKgPerHour());
    assertTrue(allocation.getAllocatedElementalSulfurMassKg() <= allocation.getSourceSulfurEquivalentMassKg());
  }

  private static AqueousHydrogenSulfideOxidationWaterInventoryProjection.Result projection(
      AqueousHydrogenSulfideOxidationTrajectory.Segment segment, double waterInventoryKg) {
    return AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult(segment), waterInventoryKg);
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
