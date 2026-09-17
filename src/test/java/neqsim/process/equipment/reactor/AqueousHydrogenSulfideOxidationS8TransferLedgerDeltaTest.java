package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/** Tests deterministic reconciliation of immutable S8 transfer-ledger states. */
public class AqueousHydrogenSulfideOxidationS8TransferLedgerDeltaTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";

  @Test
  void testUnchangedSerializedLedgerHasZeroDelta() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(batch(4.0, 0.25, PRODUCT_BASIS,
        "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result restored = serializeRoundTrip(prior);

    AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.Result delta =
        AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(prior, restored);

    assertTrue(delta.isUnchanged());
    assertFalse(delta.isStrictAppend());
    assertEquals(0, delta.getAddedBatchCount());
    assertEquals(0, delta.getAddedTransferCount());
    assertEquals(0.0, delta.getSourceSulfurEquivalentMassDeltaKg(), 0.0);
    assertEquals(0.0, delta.getTransferredS8MassDeltaKg(), 0.0);
    assertEquals(0.0, delta.getUnallocatedSulfurEquivalentMassDeltaKg(), 0.0);
    assertEquals(0.0, delta.getMassClosureResidualDeltaKg(), 0.0);
  }

  @Test
  void testStrictAppendReportsExactInventoryDifferences() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first =
        batch(4.0, 0.25, PRODUCT_BASIS, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result second =
        batch(6.0, 0.50, PRODUCT_BASIS, "batch-1", "segment-1");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = serializeRoundTrip(ledger(first));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate =
        AqueousHydrogenSulfideOxidationS8TransferLedger.append(prior, second);

    AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.Result delta =
        AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(prior, candidate);

    assertFalse(delta.isUnchanged());
    assertTrue(delta.isStrictAppend());
    assertEquals("ledger-A", delta.getLedgerIdentifier());
    assertEquals(PRODUCT_BASIS, delta.getProductIdentityBasisIdentifier());
    assertEquals("S8", delta.getComponentName());
    assertEquals(1, delta.getAddedBatchCount());
    assertEquals(1, delta.getAddedTransferCount());
    assertSame(second, delta.getAddedBatches().get(0));
    assertEquals(candidate.getTotalSourceSulfurEquivalentMassKg() - prior.getTotalSourceSulfurEquivalentMassKg(),
        delta.getSourceSulfurEquivalentMassDeltaKg(), 0.0);
    assertEquals(candidate.getTotalTransferredS8MassKg() - prior.getTotalTransferredS8MassKg(),
        delta.getTransferredS8MassDeltaKg(), 0.0);
    assertEquals(
        candidate.getTotalUnallocatedSulfurEquivalentMassKg()
            - prior.getTotalUnallocatedSulfurEquivalentMassKg(),
        delta.getUnallocatedSulfurEquivalentMassDeltaKg(), 0.0);
    assertEquals(candidate.getMassClosureResidualKg() - prior.getMassClosureResidualKg(),
        delta.getMassClosureResidualDeltaKg(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> delta.getAddedBatches().add(first));
  }

  @Test
  void testTruncationReorderingAndReplacementFailClosed() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first =
        batch(4.0, 0.25, PRODUCT_BASIS, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result second =
        batch(6.0, 0.50, PRODUCT_BASIS, "batch-1", "segment-1");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prefix = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result full = ledger(first, second);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result reordered = ledger(second, first);
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result replacement =
        batch(4.0, 0.30, PRODUCT_BASIS, "batch-0", "replacement-key");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result replaced = ledger(replacement, second);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(full, prefix));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(prefix, reordered));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(prefix, replaced));
  }

  @Test
  void testIdentityMismatchAndMissingStatesFailClosed() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first =
        batch(4.0, 0.25, PRODUCT_BASIS, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result otherLedger =
        AqueousHydrogenSulfideOxidationS8TransferLedger.create(Collections.singletonList(first), "ledger-B");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result otherProduct = ledger(
        batch(4.0, 0.25, "other-product-basis", "batch-0", "segment-0"));

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(null, prior));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(prior, null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(prior, otherLedger));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(prior, otherProduct));
  }

  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger(
      AqueousHydrogenSulfideOxidationS8TransferBatch.Result... batches) {
    return AqueousHydrogenSulfideOxidationS8TransferLedger.create(Arrays.asList(batches), "ledger-A");
  }

  private static AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch(double durationHours,
      double allocationFraction, String productBasis, String batchIdentifier, String idempotencyKey) {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment =
        new AqueousHydrogenSulfideOxidationTrajectory.Segment(durationHours, 298.15, 8.0, 0.723, 250.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult =
        AqueousHydrogenSulfideOxidationTrajectory.advance(INITIAL_TOTAL_SULFIDE_MOLALITY,
            Collections.singletonList(segment)).getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation =
        AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(
            AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult, WATER_INVENTORY_KG),
            allocationFraction, ALLOCATION_BASIS);
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer =
        AqueousHydrogenSulfideOxidationS8Transfer.create(allocation,
            AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, productBasis, idempotencyKey);
    return AqueousHydrogenSulfideOxidationS8TransferBatch.create(Collections.singletonList(transfer),
        batchIdentifier);
  }

  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(ledger);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8TransferLedger.Result) input.readObject();
    }
  }
}
