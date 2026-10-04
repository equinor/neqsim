package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

/** Tests checkpoint-linked transition receipts for immutable S8 transfer ledgers. */
public class AqueousHydrogenSulfideOxidationS8TransferLedgerTransitionTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";

  @Test
  void testUnchangedSerializedStatesProduceZeroTransition() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result restored = serializeRoundTrip(prior);

    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, restored);

    assertTrue(transition.isUnchanged());
    assertFalse(transition.isStrictAppend());
    assertEquals(transition.getPriorCheckpointHex(), transition.getCandidateCheckpointHex());
    assertEquals(0, transition.getAddedBatchCount());
    assertEquals(0, transition.getAddedTransferCount());
    assertEquals(0.0, transition.getSourceSulfurEquivalentMassDeltaKg(), 0.0);
    assertEquals(0.0, transition.getTransferredS8MassDeltaKg(), 0.0);
    assertEquals(0.0, transition.getUnallocatedSulfurEquivalentMassDeltaKg(), 0.0);
    assertEquals(0.0, transition.getMassClosureResidualDeltaKg(), 0.0);
    assertTrue(AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.verify(prior, restored, transition));
  }

  @Test
  void testStrictAppendLinksCheckpointsAndExactDeltas() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result second = batch(6.0, 0.50, "batch-1", "segment-1");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first, second);

    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);

    assertFalse(transition.isUnchanged());
    assertTrue(transition.isStrictAppend());
    assertNotEquals(transition.getPriorCheckpointHex(), transition.getCandidateCheckpointHex());
    assertEquals(1, transition.getAddedBatchCount());
    assertEquals(1, transition.getAddedTransferCount());
    assertEquals(candidate.getTotalSourceSulfurEquivalentMassKg() - prior.getTotalSourceSulfurEquivalentMassKg(),
        transition.getSourceSulfurEquivalentMassDeltaKg(), 0.0);
    assertEquals(candidate.getTotalTransferredS8MassKg() - prior.getTotalTransferredS8MassKg(),
        transition.getTransferredS8MassDeltaKg(), 0.0);
    assertEquals(64, transition.getTransitionDigestHex().length());
    assertTrue(transition.getTransitionDigestHex().matches("[0-9a-f]{64}"));
    assertTrue(AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.verify(prior, candidate, transition));
  }

  @Test
  void testDivergentAndMismatchedTransitionsFailClosed() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result second = batch(6.0, 0.50, "batch-1", "segment-1");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first, second);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result reordered = ledger(second, first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result replacement = ledger(first,
        batch(6.0, 0.60, "batch-1", "replacement-key"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result candidateReceipt = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result replacementReceipt = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, replacement);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(candidate, prior));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, reordered));
    assertFalse(AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.verify(prior, candidate, replacementReceipt));
    assertTrue(AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.verify(prior, candidate, candidateReceipt));
  }

  @Test
  void testReceiptSerializationAndDefensiveDigestCopy() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first,
        batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    byte[] firstDigest = transition.getTransitionDigestBytes();
    byte[] secondDigest = transition.getTransitionDigestBytes();
    firstDigest[0] ^= 0xff;
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result restored = serializeRoundTrip(transition);

    assertNotSame(firstDigest, secondDigest);
    assertEquals(transition.getTransitionDigestHex(), restored.getTransitionDigestHex());
    assertTrue(AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.verify(prior, candidate, restored));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(null, candidate));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.verify(prior, candidate, null));
  }

  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger(
      AqueousHydrogenSulfideOxidationS8TransferBatch.Result... batches) {
    return AqueousHydrogenSulfideOxidationS8TransferLedger.create(Arrays.asList(batches), "ledger-A");
  }

  private static AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch(double durationHours,
      double allocationFraction, String batchIdentifier, String idempotencyKey) {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment = new AqueousHydrogenSulfideOxidationTrajectory.Segment(
        durationHours, 298.15, 8.0, 0.723, 250.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment)).getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult, WATER_INVENTORY_KG),
            allocationFraction, ALLOCATION_BASIS);
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer = AqueousHydrogenSulfideOxidationS8Transfer
        .create(allocation, AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, PRODUCT_BASIS, idempotencyKey);
    return AqueousHydrogenSulfideOxidationS8TransferBatch.create(Collections.singletonList(transfer), batchIdentifier);
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

  private static AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(transition);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result) input.readObject();
    }
  }
}
