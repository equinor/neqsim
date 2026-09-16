package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests immutable duplicate-safe batches of mass-based S8 transfer receipts. */
public class AqueousHydrogenSulfideOxidationS8TransferBatchTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final double TOLERANCE = 1.0e-17;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";

  @Test
  void testBatchPreservesOrderAndClosesAggregateMass() {
    AqueousHydrogenSulfideOxidationS8Transfer.Result first = transfer(referenceSegment(4.0), 0.25, PRODUCT_BASIS,
        "segment-0");
    AqueousHydrogenSulfideOxidationS8Transfer.Result second = transfer(referenceSegment(6.0), 0.50, PRODUCT_BASIS,
        "segment-1");

    AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch = AqueousHydrogenSulfideOxidationS8TransferBatch
        .create(Arrays.asList(first, second), "batch-A");

    assertEquals("batch-A", batch.getBatchIdentifier());
    assertEquals("S8", batch.getComponentName());
    assertEquals(PRODUCT_BASIS, batch.getProductIdentityBasisIdentifier());
    assertEquals(2, batch.getTransferCount());
    assertSame(first, batch.getTransfers().get(0));
    assertSame(second, batch.getTransfers().get(1));
    assertEquals(first.getSourceSulfurEquivalentMassKg() + second.getSourceSulfurEquivalentMassKg(),
        batch.getTotalSourceSulfurEquivalentMassKg(), 0.0);
    assertEquals(first.getTransferredS8MassKg() + second.getTransferredS8MassKg(), batch.getTotalTransferredS8MassKg(),
        0.0);
    assertEquals(first.getUnallocatedSulfurEquivalentMassKg() + second.getUnallocatedSulfurEquivalentMassKg(),
        batch.getTotalUnallocatedSulfurEquivalentMassKg(), 0.0);
    assertEquals(
        batch.getTotalSourceSulfurEquivalentMassKg()
            - (batch.getTotalTransferredS8MassKg() + batch.getTotalUnallocatedSulfurEquivalentMassKg()),
        batch.getMassClosureResidualKg(), 0.0);
    assertEquals(0.0, batch.getMassClosureResidualKg(), TOLERANCE);
  }

  @Test
  void testDuplicateKeysAndMixedProductBasesFailClosed() {
    AqueousHydrogenSulfideOxidationS8Transfer.Result first = transfer(referenceSegment(4.0), 0.25, PRODUCT_BASIS,
        "duplicate-key");
    AqueousHydrogenSulfideOxidationS8Transfer.Result duplicate = transfer(referenceSegment(6.0), 0.50, PRODUCT_BASIS,
        "duplicate-key");
    AqueousHydrogenSulfideOxidationS8Transfer.Result mixedBasis = transfer(referenceSegment(6.0), 0.50,
        "different-product-basis", "segment-1");

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferBatch
        .create(Arrays.asList(first, duplicate), "duplicate-batch"));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferBatch
        .create(Arrays.asList(first, mixedBasis), "mixed-basis-batch"));
  }

  @Test
  void testSegmentSubdivisionPreservesTotalTransferredMass() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result unsplit = batchFromTrajectory(
        Collections.singletonList(referenceSegment(10.0)), "unsplit");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result split = batchFromTrajectory(
        Arrays.asList(referenceSegment(4.0), referenceSegment(6.0)), "split");

    assertEquals(unsplit.getTotalTransferredS8MassKg(), split.getTotalTransferredS8MassKg(), TOLERANCE);
    assertEquals(unsplit.getTotalSourceSulfurEquivalentMassKg(), split.getTotalSourceSulfurEquivalentMassKg(),
        TOLERANCE);
    assertEquals(unsplit.getTotalUnallocatedSulfurEquivalentMassKg(), split.getTotalUnallocatedSulfurEquivalentMassKg(),
        TOLERANCE);
  }

  @Test
  void testBatchRoundTripsThroughSerializationAndRemainsUnmodifiable() throws Exception {
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer = transfer(referenceSegment(10.0), 0.40, PRODUCT_BASIS,
        "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result original = AqueousHydrogenSulfideOxidationS8TransferBatch
        .create(Collections.singletonList(transfer), "serial-batch");

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (AqueousHydrogenSulfideOxidationS8TransferBatch.Result) input.readObject();
    }

    assertNotSame(original, restored);
    assertEquals(original.getBatchIdentifier(), restored.getBatchIdentifier());
    assertEquals(original.getTransferCount(), restored.getTransferCount());
    assertEquals(original.getTotalTransferredS8MassKg(), restored.getTotalTransferredS8MassKg(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> restored.getTransfers().add(transfer));
  }

  @Test
  void testMissingOrInvalidBatchEvidenceFailsClosed() {
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer = transfer(referenceSegment(10.0), 0.40, PRODUCT_BASIS,
        "segment-0");
    List<AqueousHydrogenSulfideOxidationS8Transfer.Result> nullReceipt = new ArrayList<AqueousHydrogenSulfideOxidationS8Transfer.Result>();
    nullReceipt.add(null);
    String oversizedIdentifier = new String(new char[257]).replace('\0', 'x');

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferBatch.create(null, "batch"));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferBatch
        .create(Collections.<AqueousHydrogenSulfideOxidationS8Transfer.Result>emptyList(), "batch"));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferBatch.create(nullReceipt, "batch"));
    assertInvalidIdentifier(transfer, null);
    assertInvalidIdentifier(transfer, "");
    assertInvalidIdentifier(transfer, " untrimmed");
    assertInvalidIdentifier(transfer, oversizedIdentifier);
  }

  private static void assertInvalidIdentifier(AqueousHydrogenSulfideOxidationS8Transfer.Result transfer,
      String batchIdentifier) {
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferBatch
        .create(Collections.singletonList(transfer), batchIdentifier));
  }

  private static AqueousHydrogenSulfideOxidationS8TransferBatch.Result batchFromTrajectory(
      List<AqueousHydrogenSulfideOxidationTrajectory.Segment> segments, String batchIdentifier) {
    AqueousHydrogenSulfideOxidationTrajectory.Result trajectory = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, segments);
    List<AqueousHydrogenSulfideOxidationS8Transfer.Result> transfers = new ArrayList<AqueousHydrogenSulfideOxidationS8Transfer.Result>();
    int index = 0;
    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : trajectory.getSegmentResults()) {
      AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
          .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, WATER_INVENTORY_KG), 0.40,
              ALLOCATION_BASIS);
      transfers.add(AqueousHydrogenSulfideOxidationS8Transfer.create(allocation,
          AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, PRODUCT_BASIS,
          batchIdentifier + "-segment-" + index));
      index++;
    }
    return AqueousHydrogenSulfideOxidationS8TransferBatch.create(transfers, batchIdentifier);
  }

  private static AqueousHydrogenSulfideOxidationS8Transfer.Result transfer(
      AqueousHydrogenSulfideOxidationTrajectory.Segment segment, double allocationFraction, String productBasis,
      String idempotencyKey) {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment)).getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult, WATER_INVENTORY_KG),
            allocationFraction, ALLOCATION_BASIS);
    return AqueousHydrogenSulfideOxidationS8Transfer.create(allocation,
        AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, productBasis, idempotencyKey);
  }

  private static AqueousHydrogenSulfideOxidationTrajectory.Segment referenceSegment(double durationHours) {
    return new AqueousHydrogenSulfideOxidationTrajectory.Segment(durationHours, 298.15, 8.0, 0.723, 250.0e-6);
  }
}

