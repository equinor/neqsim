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
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests immutable checkpoints over qualified S8 transition-ledger transition chains. */
class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 2.5e-5;
  private static final double WATER_INVENTORY_KG = 1000.0;
  private static final String ALLOCATION_BASIS = "caller-owned elemental sulfur scenario";
  private static final String PRODUCT_BASIS = "caller-owned S8 product identity";

  /** Verify a checkpoint preserves upstream identities, endpoints and exact aggregate counts. */
  @Test
  void testCheckpointPreservesQualifiedChainEvidence() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result chain = transitionChain();

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result checkpoint = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
        .create("checkpoint-A", 17L, chain);

    assertEquals("SHA-256", checkpoint.getDigestAlgorithm());
    assertEquals("checkpoint-A", checkpoint.getCheckpointIdentifier());
    assertEquals(17L, checkpoint.getCheckpointSequence());
    assertEquals(chain.getTransitionChainIdentifier(), checkpoint.getTransitionChainIdentifier());
    assertEquals(chain.getLedgerIdentifier(), checkpoint.getLedgerIdentifier());
    assertEquals(chain.getChainIdentifier(), checkpoint.getChainIdentifier());
    assertEquals(chain.getManifestIdentifier(), checkpoint.getManifestIdentifier());
    assertEquals(chain.getFirstPriorLedgerDigestHex(), checkpoint.getFirstPriorLedgerDigestHex());
    assertEquals(chain.getFinalCandidateLedgerDigestHex(), checkpoint.getFinalCandidateLedgerDigestHex());
    assertEquals(chain.getFirstPriorChainDigestHex(), checkpoint.getFirstPriorChainDigestHex());
    assertEquals(chain.getFinalCandidateChainDigestHex(), checkpoint.getFinalCandidateChainDigestHex());
    assertEquals(chain.getFirstPriorManifestDigestHex(), checkpoint.getFirstPriorManifestDigestHex());
    assertEquals(chain.getFinalCandidateManifestDigestHex(), checkpoint.getFinalCandidateManifestDigestHex());
    assertEquals(chain.getTransitionCount(), checkpoint.getTransitionCount());
    assertEquals(chain.getStrictAppendReceiptCount(), checkpoint.getStrictAppendReceiptCount());
    assertEquals(chain.getUnchangedReceiptCount(), checkpoint.getUnchangedReceiptCount());
    assertEquals(chain.getAddedLedgerReceiptCount(), checkpoint.getAddedLedgerReceiptCount());
    assertEquals(chain.getAddedTransitionCount(), checkpoint.getAddedTransitionCount());
    assertEquals(chain.getAddedStrictAppendTransitionCount(), checkpoint.getAddedStrictAppendTransitionCount());
    assertEquals(chain.getAddedUnchangedTransitionCount(), checkpoint.getAddedUnchangedTransitionCount());
    assertEquals(chain.getAddedReconciliationCount(), checkpoint.getAddedReconciliationCount());
    assertEquals(chain.getAddedEntryCount(), checkpoint.getAddedEntryCount());
    assertEquals(chain.getAddedStrictAppendCount(), checkpoint.getAddedStrictAppendCount());
    assertEquals(chain.getAddedUnchangedCount(), checkpoint.getAddedUnchangedCount());
    assertEquals(chain.getChainDigestHex(), checkpoint.getChainDigestHex());
  }

  /** Verify checkpoint identity and sequence are digest-bound and verification is deterministic. */
  @Test
  void testCheckpointDigestAndVerificationAreDeterministic() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result chain = transitionChain();
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result first = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
        .create("checkpoint-A", 17L, chain);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result repeated = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
        .create("checkpoint-A", 17L, chain);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result changedIdentifier = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
        .create("checkpoint-B", 17L, chain);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result changedSequence = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
        .create("checkpoint-A", 18L, chain);

    assertEquals(first.getCheckpointDigestHex(), repeated.getCheckpointDigestHex());
    assertNotEquals(first.getCheckpointDigestHex(), changedIdentifier.getCheckpointDigestHex());
    assertNotEquals(first.getCheckpointDigestHex(), changedSequence.getCheckpointDigestHex());
    assertTrue(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
            .verify("checkpoint-A", 17L, chain, first));
    assertFalse(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
            .verify("checkpoint-B", 17L, chain, first));
    assertFalse(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
            .verify("checkpoint-A", 18L, chain, first));
  }

  /** Verify missing, blank and negative checkpoint inputs fail closed. */
  @Test
  void testInvalidCheckpointInputsFailClosed() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result chain = transitionChain();

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
            .create(" ", 17L, chain));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
            .create("checkpoint-A", -1L, chain));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
            .create("checkpoint-A", 17L, null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
            .verify("checkpoint-A", 17L, chain, null));
  }

  /** Verify checkpoint serialization and both raw digests are defensively copied. */
  @Test
  void testSerializationAndDigestBytesAreDefensive() throws Exception {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result chain = transitionChain();
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result checkpoint = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
        .create("checkpoint-A", 17L, chain);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result restored = serializeCheckpoint(
        checkpoint);
    byte[] firstCheckpointDigest = checkpoint.getCheckpointDigestBytes();
    byte[] secondCheckpointDigest = checkpoint.getCheckpointDigestBytes();
    byte[] firstChainDigest = checkpoint.getChainDigestBytes();
    byte[] secondChainDigest = checkpoint.getChainDigestBytes();
    firstCheckpointDigest[0] ^= 0xff;
    firstChainDigest[0] ^= 0xff;

    assertNotSame(firstCheckpointDigest, secondCheckpointDigest);
    assertNotSame(firstChainDigest, secondChainDigest);
    assertEquals(checkpoint.getCheckpointDigestHex(), restored.getCheckpointDigestHex());
    assertEquals(checkpoint.getChainDigestHex(), restored.getChainDigestHex());
    assertTrue(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
            .verify("checkpoint-A", 17L, chain, restored));
  }

  /** Build one qualified transition-ledger transition chain. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result transitionChain() {
    TransitionFixtures fixtures = transitionFixtures("ledger-A", "chain-A", "manifest-A");
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain
        .create("ledger-transition-chain-A",
            Arrays.asList(fixtures.firstTransition, fixtures.secondTransition, fixtures.unchangedTransition));
  }

  /** Build three adjacent ledger-transition receipts. */
  private static TransitionFixtures transitionFixtures(String ledgerIdentifier, String chainIdentifier,
      String manifestIdentifier) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = entry(
        "reconciliation-A", fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry second = entry(
        "reconciliation-B", fixture("target-B", "application-B", 5.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry third = entry(
        "reconciliation-C", fixture("target-C", "application-C", 7.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry fourth = entry(
        "reconciliation-D", fixture("target-D", "application-D", 9.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state0 = manifest(
        manifestIdentifier, first);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state1 = manifest(
        manifestIdentifier, first, second);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state2 = manifest(
        manifestIdentifier, first, second, third);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state3 = manifest(
        manifestIdentifier, first, second, third, fourth);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result append1 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state0, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result unchanged = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state1, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result append2 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state1, state2);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result append3 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state2, state3);

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain0 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Collections.singletonList(append1));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain1 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Arrays.asList(append1, unchanged));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain2 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Arrays.asList(append1, unchanged, append2));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain3 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Arrays.asList(append1, unchanged, append2, append3));

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result chainReceipt1 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .create(chain0, chain1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result chainReceipt2 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .create(chain1, chain2);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result chainReceipt3 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .create(chain2, chain3);

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result ledger0 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
        .create(ledgerIdentifier, Collections.singletonList(chainReceipt1));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result ledger1 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
        .create(ledgerIdentifier, Arrays.asList(chainReceipt1, chainReceipt2));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result ledger2 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
        .create(ledgerIdentifier, Arrays.asList(chainReceipt1, chainReceipt2, chainReceipt3));

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result firstTransition = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition
        .create(ledger0, ledger1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result secondTransition = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition
        .create(ledger1, ledger2);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result unchangedTransition = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition
        .create(ledger2, ledger2);
    return new TransitionFixtures(firstTransition, secondTransition, unchangedTransition);
  }

  /** Create one manifest entry from a reconciliation fixture. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry entry(
      String reconciliationIdentifier, Fixture fixture) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry(reconciliationIdentifier, reconciliation(fixture));
  }

  /** Create one manifest state. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest(
      String manifestIdentifier,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry... entries) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .create(manifestIdentifier, Arrays.asList(entries));
  }

  /** Create one reconciled stream-application result. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation(
      Fixture fixture) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request previewRequest = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request
        .create(fixture.plan, fixture.targetIdentifier, fixture.applicationKey, fixture.priorStream);
    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result candidate = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(fixture.plan, fixture.targetIdentifier, fixture.applicationKey, fixture.priorStream);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request receiptRequest = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request
        .create(fixture.plan, fixture.targetIdentifier, fixture.applicationKey, fixture.priorStream,
            candidate.getCandidateStream());
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result preview = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Collections.singletonList(previewRequest));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result receipt = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
        .verify(Collections.singletonList(receiptRequest));
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.reconcile(preview, receipt);
  }

  /** Build one stream-application fixture. */
  private static Fixture fixture(String targetIdentifier, String applicationKey, double priorS8AmountMol,
      boolean append) {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = append
        ? ledger(batch(4.0, 0.25, "batch-0", "segment-0"), batch(6.0, 0.50, "batch-1", "segment-1"))
        : prior;
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result ledgerTransition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan
        .create(prior, candidate, ledgerTransition, targetIdentifier, applicationKey, priorS8AmountMol);
    return new Fixture(plan, targetIdentifier, applicationKey, stream("prior-" + targetIdentifier, priorS8AmountMol));
  }

  /** Create a stream containing S8. */
  private static StreamInterface stream(String name, double s8AmountMol) {
    SystemInterface system = new SystemSrkEos(298.15, 80.0);
    system.addComponent("methane", 10.0);
    system.addComponent("CO2", 3.0);
    system.addComponent("S8", s8AmountMol);
    system.init(0);
    return new Stream(name, system);
  }

  /** Create an S8 transfer ledger. */
  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger(
      AqueousHydrogenSulfideOxidationS8TransferBatch.Result... batches) {
    return AqueousHydrogenSulfideOxidationS8TransferLedger.create(Arrays.asList(batches), "ledger-A");
  }

  /** Create one qualified S8 transfer batch. */
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

  /** Serialize and restore one transition-chain checkpoint. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result serializeCheckpoint(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result result)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result) input
          .readObject();
    }
  }

  /** Adjacent ledger-transition receipts. */
  private static final class TransitionFixtures {
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result firstTransition;
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result secondTransition;
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result unchangedTransition;

    private TransitionFixtures(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result firstTransition,
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result secondTransition,
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransition.Result unchangedTransition) {
      this.firstTransition = firstTransition;
      this.secondTransition = secondTransition;
      this.unchangedTransition = unchangedTransition;
    }
  }

  /** Stream-application fixture. */
  private static final class Fixture {
    private final AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan;
    private final String targetIdentifier;
    private final String applicationKey;
    private final StreamInterface priorStream;

    private Fixture(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan, String targetIdentifier,
        String applicationKey, StreamInterface priorStream) {
      this.plan = plan;
      this.targetIdentifier = targetIdentifier;
      this.applicationKey = applicationKey;
      this.priorStream = priorStream;
    }
  }
}
