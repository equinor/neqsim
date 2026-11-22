package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests explicit mass-based S8 transfer receipts from qualified sulfur allocations. */
public class AqueousHydrogenSulfideOxidationS8TransferTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final double TOLERANCE = 1.0e-17;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";
  private static final String IDEMPOTENCY_KEY = "segment-0-S8-transfer-case-A";

  @Test
  void testEveryFitPathIsSelectedExplicitlyWithoutMassConversion() {
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = allocation(referenceSegment(10.0),
        WATER_INVENTORY_KG, 0.25);

    assertTransfer(allocation, AqueousHydrogenSulfideOxidationS8Transfer.FitPath.LOWER_RATE, allocation.getLowerRate());
    assertTransfer(allocation, AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, allocation.getNominal());
    assertTransfer(allocation, AqueousHydrogenSulfideOxidationS8Transfer.FitPath.UPPER_RATE, allocation.getUpperRate());
  }

  @Test
  void testZeroAndFullAllocationPreserveExactIdentities() {
    AqueousHydrogenSulfideOxidationS8Transfer.Result zero = transfer(
        allocation(referenceSegment(10.0), WATER_INVENTORY_KG, 0.0),
        AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, "zero-transfer");
    AqueousHydrogenSulfideOxidationS8Transfer.Result full = transfer(
        allocation(referenceSegment(10.0), WATER_INVENTORY_KG, 1.0),
        AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, "full-transfer");

    assertEquals(0.0, zero.getTransferredS8MassRateKgPerHour(), 0.0);
    assertEquals(0.0, zero.getTransferredS8MassKg(), 0.0);
    assertEquals(zero.getSourceSulfurEquivalentMassRateKgPerHour(),
        zero.getUnallocatedSulfurEquivalentMassRateKgPerHour(), 0.0);
    assertEquals(zero.getSourceSulfurEquivalentMassKg(), zero.getUnallocatedSulfurEquivalentMassKg(), 0.0);

    assertEquals(full.getSourceSulfurEquivalentMassRateKgPerHour(), full.getTransferredS8MassRateKgPerHour(), 0.0);
    assertEquals(full.getSourceSulfurEquivalentMassKg(), full.getTransferredS8MassKg(), 0.0);
    assertEquals(0.0, full.getUnallocatedSulfurEquivalentMassRateKgPerHour(), 0.0);
    assertEquals(0.0, full.getUnallocatedSulfurEquivalentMassKg(), 0.0);
  }

  @Test
  void testWaterScalingAndSegmentSplitMassSumsArePreserved() {
    AqueousHydrogenSulfideOxidationS8Transfer.Result small = transfer(allocation(referenceSegment(10.0), 100.0, 0.4),
        AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, "small-inventory");
    AqueousHydrogenSulfideOxidationS8Transfer.Result large = transfer(allocation(referenceSegment(10.0), 250.0, 0.4),
        AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, "large-inventory");

    assertEquals(2.5 * small.getTransferredS8MassRateKgPerHour(), large.getTransferredS8MassRateKgPerHour(), TOLERANCE);
    assertEquals(2.5 * small.getTransferredS8MassKg(), large.getTransferredS8MassKg(), TOLERANCE);

    AqueousHydrogenSulfideOxidationTrajectory.Result unsplit = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(referenceSegment(10.0)));
    AqueousHydrogenSulfideOxidationTrajectory.Result split = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Arrays.asList(referenceSegment(4.0), referenceSegment(6.0)));

    assertEquals(transferredNominalMass(unsplit, 0.4), transferredNominalMass(split, 0.4), TOLERANCE);
  }

  @Test
  void testReceiptRoundTripsThroughSerializationAndPreservesProvenance() throws Exception {
    AqueousHydrogenSulfideOxidationS8Transfer.Result original = transfer(
        allocation(referenceSegment(10.0), WATER_INVENTORY_KG, 0.5),
        AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, IDEMPOTENCY_KEY);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    AqueousHydrogenSulfideOxidationS8Transfer.Result restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (AqueousHydrogenSulfideOxidationS8Transfer.Result) input.readObject();
    }

    assertNotSame(original, restored);
    assertEquals("S8", restored.getComponentName());
    assertEquals(ALLOCATION_BASIS, restored.getAllocationBasisIdentifier());
    assertEquals(PRODUCT_BASIS, restored.getProductIdentityBasisIdentifier());
    assertEquals(IDEMPOTENCY_KEY, restored.getDownstreamIdempotencyKey());
    assertEquals(original.getFitPath(), restored.getFitPath());
    assertEquals(original.getTransferredS8MassRateKgPerHour(), restored.getTransferredS8MassRateKgPerHour(), 0.0);
    assertEquals(original.getTransferredS8MassKg(), restored.getTransferredS8MassKg(), 0.0);
  }

  @Test
  void testMissingOrInvalidTransferEvidenceFailsClosed() {
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = allocation(referenceSegment(10.0),
        WATER_INVENTORY_KG, 0.5);
    String oversizedIdentifier = new String(new char[257]).replace('\0', 'x');

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8Transfer.create(null,
        AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, PRODUCT_BASIS, IDEMPOTENCY_KEY));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8Transfer.create(allocation, null, PRODUCT_BASIS, IDEMPOTENCY_KEY));
    assertInvalidIdentifier(allocation, null, IDEMPOTENCY_KEY);
    assertInvalidIdentifier(allocation, "", IDEMPOTENCY_KEY);
    assertInvalidIdentifier(allocation, " untrimmed", IDEMPOTENCY_KEY);
    assertInvalidIdentifier(allocation, oversizedIdentifier, IDEMPOTENCY_KEY);
    assertInvalidIdentifier(allocation, PRODUCT_BASIS, null);
    assertInvalidIdentifier(allocation, PRODUCT_BASIS, "");
    assertInvalidIdentifier(allocation, PRODUCT_BASIS, "untrimmed ");
    assertInvalidIdentifier(allocation, PRODUCT_BASIS, oversizedIdentifier);
  }

  private static void assertTransfer(AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation,
      AqueousHydrogenSulfideOxidationS8Transfer.FitPath fitPath,
      AqueousHydrogenSulfideOxidationElementalSulfurAllocation.PathResult expected) {
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer = transfer(allocation, fitPath,
        IDEMPOTENCY_KEY + "-" + fitPath.name());

    assertEquals("S8", transfer.getComponentName());
    assertEquals(allocation.getSourceSegmentIndex(), transfer.getSourceSegmentIndex());
    assertEquals(allocation.getDurationHours(), transfer.getDurationHours(), 0.0);
    assertEquals(allocation.getWaterInventoryKg(), transfer.getWaterInventoryKg(), 0.0);
    assertEquals(fitPath, transfer.getFitPath());
    assertEquals(ALLOCATION_BASIS, transfer.getAllocationBasisIdentifier());
    assertEquals(PRODUCT_BASIS, transfer.getProductIdentityBasisIdentifier());
    assertEquals(expected.getSourceSulfurEquivalentMassRateKgPerHour(),
        transfer.getSourceSulfurEquivalentMassRateKgPerHour(), 0.0);
    assertEquals(expected.getAllocatedElementalSulfurMassRateKgPerHour(), transfer.getTransferredS8MassRateKgPerHour(),
        0.0);
    assertEquals(expected.getUnallocatedSulfurEquivalentMassRateKgPerHour(),
        transfer.getUnallocatedSulfurEquivalentMassRateKgPerHour(), 0.0);
    assertEquals(expected.getRateClosureResidualKgPerHour(), transfer.getRateClosureResidualKgPerHour(), 0.0);
    assertEquals(expected.getSourceSulfurEquivalentMassKg(), transfer.getSourceSulfurEquivalentMassKg(), 0.0);
    assertEquals(expected.getAllocatedElementalSulfurMassKg(), transfer.getTransferredS8MassKg(), 0.0);
    assertEquals(expected.getUnallocatedSulfurEquivalentMassKg(), transfer.getUnallocatedSulfurEquivalentMassKg(), 0.0);
    assertEquals(expected.getMassClosureResidualKg(), transfer.getMassClosureResidualKg(), 0.0);
  }

  private static void assertInvalidIdentifier(
      AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation, String productBasis,
      String idempotencyKey) {
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8Transfer.create(allocation,
        AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, productBasis, idempotencyKey));
  }

  private static double transferredNominalMass(AqueousHydrogenSulfideOxidationTrajectory.Result trajectory,
      double allocationFraction) {
    double transferred = 0.0;
    int index = 0;
    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : trajectory.getSegmentResults()) {
      transferred += transfer(
          AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(
              AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, WATER_INVENTORY_KG),
              allocationFraction, ALLOCATION_BASIS),
          AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, "split-transfer-" + index)
          .getTransferredS8MassKg();
      index++;
    }
    return transferred;
  }

  private static AqueousHydrogenSulfideOxidationS8Transfer.Result transfer(
      AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation,
      AqueousHydrogenSulfideOxidationS8Transfer.FitPath fitPath, String idempotencyKey) {
    return AqueousHydrogenSulfideOxidationS8Transfer.create(allocation, fitPath, PRODUCT_BASIS, idempotencyKey);
  }

  private static AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation(
      AqueousHydrogenSulfideOxidationTrajectory.Segment segment, double waterInventoryKg, double allocationFraction) {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment)).getSegmentResults().get(0);
    return AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(
        AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult, waterInventoryKg),
        allocationFraction, ALLOCATION_BASIS);
  }

  private static AqueousHydrogenSulfideOxidationTrajectory.Segment referenceSegment(double durationHours) {
    return new AqueousHydrogenSulfideOxidationTrajectory.Segment(durationHours, 298.15, 8.0, 0.723, 250.0e-6);
  }
}
