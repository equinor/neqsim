package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Tests deterministic integrity checkpoints for immutable S8 transfer ledgers. */
public class AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpointTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";

  @Test
  void testSerializedLedgerProducesIdenticalCheckpoint() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger = ledger(batch(4.0, 0.25, "batch-0", "segment-0"),
        batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result restored = serializeRoundTrip(ledger);

    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result first = AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint
        .create(ledger);
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result second = AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint
        .create(restored);

    assertEquals("SHA-256", first.getDigestAlgorithm());
    assertEquals("neqsim-s8-transfer-ledger-checkpoint-v1", first.getSchemaIdentifier());
    assertEquals(64, first.getDigestHex().length());
    assertTrue(first.getDigestHex().matches("[0-9a-f]{64}"));
    assertEquals(first.getDigestHex(), second.getDigestHex());
    assertTrue(AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.verify(restored, first));
    assertEquals(ledger.getBatchCount(), first.getBatchCount());
    assertEquals(ledger.getTransferCount(), first.getTransferCount());
    assertEquals(ledger.getTotalTransferredS8MassKg(), first.getTotalTransferredS8MassKg(), 0.0);
  }

  @Test
  void testAppendReorderAndReplacementChangeCheckpoint() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result second = batch(6.0, 0.50, "batch-1", "segment-1");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result appended = ledger(first, second);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result reordered = ledger(second, first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result replaced = ledger(
        batch(4.0, 0.30, "batch-0", "replacement-key"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result checkpoint = AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint
        .create(prior);

    assertFalse(AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.verify(appended, checkpoint));
    assertFalse(AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.verify(reordered, checkpoint));
    assertFalse(AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.verify(replaced, checkpoint));
  }

  @Test
  void testCheckpointReceiptIsImmutableAndSerializable() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger = ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result checkpoint = AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint
        .create(ledger);
    byte[] first = checkpoint.getDigestBytes();
    byte[] second = checkpoint.getDigestBytes();
    first[0] ^= 0xff;
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result restored = serializeRoundTrip(checkpoint);

    assertNotSame(first, second);
    assertEquals(checkpoint.getDigestHex(), restored.getDigestHex());
    assertTrue(AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.verify(ledger, restored));
  }

  @Test
  void testMissingInputsFailClosed() {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger = ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result checkpoint = AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint
        .create(ledger);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.create(null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.verify(null, checkpoint));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.verify(ledger, null));
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

  private static AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result checkpoint) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(checkpoint);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result) input.readObject();
    }
  }
}
