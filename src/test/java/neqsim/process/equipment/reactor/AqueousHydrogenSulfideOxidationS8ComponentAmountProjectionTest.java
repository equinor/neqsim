package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Tests verified component-amount projections for S8 ledger transitions. */
public class AqueousHydrogenSulfideOxidationS8ComponentAmountProjectionTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";

  @Test
  void testUnchangedTransitionProjectsExactZero() {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, prior);

    AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result projection = AqueousHydrogenSulfideOxidationS8ComponentAmountProjection
        .project(prior, prior, transition);

    assertTrue(projection.isUnchanged());
    assertFalse(projection.isStrictAppend());
    assertEquals(0.0, projection.getTransferredS8MassKg(), 0.0);
    assertEquals(0.0, projection.getTransferredS8AmountMol(), 0.0);
    assertEquals(0.0, projection.getTransferredS8AmountKmol(), 0.0);
    assertEquals(0.0, projection.getReconstructedS8MassKg(), 0.0);
    assertEquals(0.0, projection.getMassClosureResidualKg(), 0.0);
    assertEquals(projection.getPriorCheckpointHex(), projection.getCandidateCheckpointHex());
  }

  @Test
  void testStrictAppendProjectsDatabaseBasisAmounts() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result second = batch(6.0, 0.50, "batch-1", "segment-1");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first, second);
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);

    AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result projection = AqueousHydrogenSulfideOxidationS8ComponentAmountProjection
        .project(prior, candidate, transition);

    assertTrue(projection.isStrictAppend());
    assertFalse(projection.isUnchanged());
    assertEquals("S8", projection.getComponentName());
    assertEquals(0.25648, projection.getS8MolarMassKgPerMol(), 0.0);
    assertEquals("NeqSim-COMP.csv-S8-256.48-g-per-mol", projection.getMolecularWeightBasisIdentifier());
    assertEquals(transition.getTransferredS8MassDeltaKg(), projection.getTransferredS8MassKg(), 0.0);
    assertEquals(projection.getTransferredS8MassKg() / projection.getS8MolarMassKgPerMol(),
        projection.getTransferredS8AmountMol(), 0.0);
    assertEquals(projection.getTransferredS8AmountMol() / 1000.0, projection.getTransferredS8AmountKmol(), 0.0);
    assertEquals(projection.getTransferredS8AmountMol() * projection.getS8MolarMassKgPerMol(),
        projection.getReconstructedS8MassKg(), 0.0);
    assertTrue(Math.abs(projection.getMassClosureResidualKg()) <= 8.0 * Math.ulp(projection.getTransferredS8MassKg()));
    assertEquals(transition.getTransitionDigestHex(), projection.getTransitionDigestHex());
  }

  @Test
  void testMismatchedTransitionReceiptFailsClosed() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first,
        batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result replacement = ledger(first,
        batch(6.0, 0.60, "batch-1", "replacement-key"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result replacementTransition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, replacement);

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentAmountProjection
        .project(prior, candidate, replacementTransition));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.project(prior, candidate, null));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentAmountProjection
        .project(null, candidate, replacementTransition));
  }

  @Test
  void testProjectionSerializationPreservesBitwiseEvidence() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first,
        batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result projection = AqueousHydrogenSulfideOxidationS8ComponentAmountProjection
        .project(prior, candidate, transition);
    AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result restored = serializeRoundTrip(projection);

    assertEquals(projection.getLedgerIdentifier(), restored.getLedgerIdentifier());
    assertEquals(projection.getProductIdentityBasisIdentifier(), restored.getProductIdentityBasisIdentifier());
    assertEquals(projection.getPriorCheckpointHex(), restored.getPriorCheckpointHex());
    assertEquals(projection.getCandidateCheckpointHex(), restored.getCandidateCheckpointHex());
    assertEquals(projection.getTransitionDigestHex(), restored.getTransitionDigestHex());
    assertEquals(Double.doubleToLongBits(projection.getTransferredS8MassKg()),
        Double.doubleToLongBits(restored.getTransferredS8MassKg()));
    assertEquals(Double.doubleToLongBits(projection.getTransferredS8AmountMol()),
        Double.doubleToLongBits(restored.getTransferredS8AmountMol()));
    assertEquals(Double.doubleToLongBits(projection.getMassClosureResidualKg()),
        Double.doubleToLongBits(restored.getMassClosureResidualKg()));
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

  private static AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result projection) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(projection);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result) input.readObject();
    }
  }
}
