package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

/** Tests the immutable cross-batch S8 transfer accounting ledger. */
public class AqueousHydrogenSulfideOxidationS8TransferLedgerTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final double TOLERANCE = 1.0e-17;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";

  @Test
  void testLedgerPreservesOrderKeysAndCumulativeClosure() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(referenceSegment(4.0), 0.25,
        PRODUCT_BASIS, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result second = batch(referenceSegment(6.0), 0.50,
        PRODUCT_BASIS, "batch-1", "segment-1");

    AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger = AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(Arrays.asList(first, second), "ledger-A");

    assertEquals("ledger-A", ledger.getLedgerIdentifier());
    assertEquals("S8", ledger.getComponentName());
    assertEquals(PRODUCT_BASIS, ledger.getProductIdentityBasisIdentifier());
    assertEquals(2, ledger.getBatchCount());
    assertEquals(2, ledger.getTransferCount());
    assertSame(first, ledger.getBatches().get(0));
    assertSame(second, ledger.getBatches().get(1));
    assertTrue(ledger.containsDownstreamIdempotencyKey("segment-0"));
    assertTrue(ledger.containsDownstreamIdempotencyKey("segment-1"));
    assertFalse(ledger.containsDownstreamIdempotencyKey("unused"));
    assertEquals(first.getTotalSourceSulfurEquivalentMassKg() + second.getTotalSourceSulfurEquivalentMassKg(),
        ledger.getTotalSourceSulfurEquivalentMassKg(), 0.0);
    assertEquals(first.getTotalTransferredS8MassKg() + second.getTotalTransferredS8MassKg(),
        ledger.getTotalTransferredS8MassKg(), 0.0);
    assertEquals(first.getTotalUnallocatedSulfurEquivalentMassKg()
        + second.getTotalUnallocatedSulfurEquivalentMassKg(),
        ledger.getTotalUnallocatedSulfurEquivalentMassKg(), 0.0);
    assertEquals(ledger.getTotalSourceSulfurEquivalentMassKg()
        - (ledger.getTotalTransferredS8MassKg() + ledger.getTotalUnallocatedSulfurEquivalentMassKg()),
        ledger.getMassClosureResidualKg(), 0.0);
    assertEquals(0.0, ledger.getMassClosureResidualKg(), TOLERANCE);
  }

  @Test
  void testDuplicateBatchKeysAndMixedProductBasesFailClosed() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(referenceSegment(4.0), 0.25,
        PRODUCT_BASIS, "batch-0", "duplicate-key");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result duplicateKey = batch(referenceSegment(6.0), 0.50,
        PRODUCT_BASIS, "batch-1", "duplicate-key");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result duplicateBatch = batch(referenceSegment(6.0), 0.50,
        PRODUCT_BASIS, "batch-0", "segment-1");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result mixedBasis = batch(referenceSegment(6.0), 0.50,
        "different-product-basis", "batch-1", "segment-1");

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(Arrays.asList(first, duplicateKey), "ledger"));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(Arrays.asList(first, duplicateBatch), "ledger"));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(Arrays.asList(first, mixedBasis), "ledger"));
  }

  @Test
  void testSerializedLedgerAppendRejectsPreviouslyConsumedKey() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(referenceSegment(4.0), 0.25,
        PRODUCT_BASIS, "batch-0", "persisted-key");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result original = AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(Collections.singletonList(first), "persistent-ledger");

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (AqueousHydrogenSulfideOxidationS8TransferLedger.Result) input.readObject();
    }

    AqueousHydrogenSulfideOxidationS8TransferBatch.Result next = batch(referenceSegment(6.0), 0.50,
        PRODUCT_BASIS, "batch-1", "new-key");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result appended = AqueousHydrogenSulfideOxidationS8TransferLedger
        .append(restored, next);
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result duplicate = batch(referenceSegment(6.0), 0.50,
        PRODUCT_BASIS, "batch-2", "persisted-key");

    assertNotSame(original, restored);
    assertNotSame(restored, appended);
    assertEquals(1, restored.getBatchCount());
    assertEquals(2, appended.getBatchCount());
    assertTrue(appended.containsDownstreamIdempotencyKey("new-key"));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedger.append(restored, duplicate));
    assertThrows(UnsupportedOperationException.class, () -> restored.getBatches().add(next));
    assertThrows(UnsupportedOperationException.class,
        () -> restored.getDownstreamIdempotencyKeys().add("external-key"));
  }

  @Test
  void testSegmentSubdivisionPreservesLedgerMassTotals() {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result unsplit = ledgerFromTrajectory(
        Collections.singletonList(referenceSegment(10.0)), "unsplit-ledger");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result split = ledgerFromTrajectory(
        Arrays.asList(referenceSegment(4.0), referenceSegment(6.0)), "split-ledger");

    assertEquals(unsplit.getTotalTransferredS8MassKg(), split.getTotalTransferredS8MassKg(), TOLERANCE);
    assertEquals(unsplit.getTotalSourceSulfurEquivalentMassKg(), split.getTotalSourceSulfurEquivalentMassKg(),
        TOLERANCE);
    assertEquals(unsplit.getTotalUnallocatedSulfurEquivalentMassKg(),
        split.getTotalUnallocatedSulfurEquivalentMassKg(), TOLERANCE);
  }

  @Test
  void testMissingOrInvalidLedgerEvidenceFailsClosed() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result valid = batch(referenceSegment(10.0), 0.40,
        PRODUCT_BASIS, "batch", "segment-0");
    List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> nullBatch = new ArrayList<AqueousHydrogenSulfideOxidationS8TransferBatch.Result>();
    nullBatch.add(null);
    String oversizedIdentifier = new String(new char[257]).replace('\0', 'x');

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedger.create(null, "ledger"));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(Collections.<AqueousHydrogenSulfideOxidationS8TransferBatch.Result>emptyList(), "ledger"));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedger.create(nullBatch, "ledger"));
    assertInvalidIdentifier(valid, null);
    assertInvalidIdentifier(valid, "");
    assertInvalidIdentifier(valid, " untrimmed");
    assertInvalidIdentifier(valid, oversizedIdentifier);
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedger.append(null, valid));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedger.append(
            AqueousHydrogenSulfideOxidationS8TransferLedger.create(Collections.singletonList(valid), "ledger"),
            null));
  }

  private static void assertInvalidIdentifier(AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch,
      String ledgerIdentifier) {
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8TransferLedger
        .create(Collections.singletonList(batch), ledgerIdentifier));
  }

  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledgerFromTrajectory(
      List<AqueousHydrogenSulfideOxidationTrajectory.Segment> segments, String ledgerIdentifier) {
    AqueousHydrogenSulfideOxidationTrajectory.Result trajectory = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, segments);
    List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> batches = new ArrayList<AqueousHydrogenSulfideOxidationS8TransferBatch.Result>();
    int index = 0;
    for (AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segment : trajectory.getSegmentResults()) {
      AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
          .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segment, WATER_INVENTORY_KG), 0.40,
              ALLOCATION_BASIS);
      AqueousHydrogenSulfideOxidationS8Transfer.Result transfer = AqueousHydrogenSulfideOxidationS8Transfer.create(
          allocation, AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, PRODUCT_BASIS,
          ledgerIdentifier + "-segment-" + index);
      batches.add(AqueousHydrogenSulfideOxidationS8TransferBatch.create(Collections.singletonList(transfer),
          ledgerIdentifier + "-batch-" + index));
      index++;
    }
    return AqueousHydrogenSulfideOxidationS8TransferLedger.create(batches, ledgerIdentifier);
  }

  private static AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch(
      AqueousHydrogenSulfideOxidationTrajectory.Segment segment, double allocationFraction, String productBasis,
      String batchIdentifier, String idempotencyKey) {
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment)).getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult, WATER_INVENTORY_KG),
            allocationFraction, ALLOCATION_BASIS);
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer = AqueousHydrogenSulfideOxidationS8Transfer.create(
        allocation, AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, productBasis, idempotencyKey);
    return AqueousHydrogenSulfideOxidationS8TransferBatch.create(Collections.singletonList(transfer), batchIdentifier);
  }

  private static AqueousHydrogenSulfideOxidationTrajectory.Segment referenceSegment(double durationHours) {
    return new AqueousHydrogenSulfideOxidationTrajectory.Segment(durationHours, 298.15, 8.0, 0.723, 250.0e-6);
  }
}
