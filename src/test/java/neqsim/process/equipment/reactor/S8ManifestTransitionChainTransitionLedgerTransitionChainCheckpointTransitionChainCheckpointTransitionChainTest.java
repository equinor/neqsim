package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Tests immutable ordered chains of qualified S8 checkpoint-transition-chain checkpoint transitions. */
class S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChainTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 2.5e-5;
  private static final double WATER_INVENTORY_KG = 1000.0;
  private static final String ALLOCATION_BASIS = "caller-owned elemental sulfur scenario";
  private static final String PRODUCT_BASIS = "caller-owned S8 product identity";

  /** Verify an adjacent chain preserves checkpoint continuity and closes every aggregate delta. */
  @Test
  void testAdjacentCheckpointTransitionsPreserveEvidenceAndCloseCounts() {
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result prior = checkpointTransitionChain(
        false);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result candidate = checkpointTransitionChain(
        true);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, prior);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 18L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result laterCheckpoint = checkpoint(
        "checkpoint-series-A", 20L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result first = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, candidateCheckpoint);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result second = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(candidate, candidateCheckpoint, candidate, laterCheckpoint);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain.Result chain = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
        .create("checkpoint-transition-chain-checkpoint-transition-chain-A", Arrays.asList(first, second));

    assertEquals("SHA-256", chain.getDigestAlgorithm());
    assertEquals("checkpoint-transition-chain-checkpoint-transition-chain-A",
        chain.getCheckpointTransitionChainCheckpointTransitionChainIdentifier());
    assertEquals("checkpoint-series-A", chain.getCheckpointTransitionChainCheckpointIdentifier());
    assertEquals("checkpoint-transition-chain-A", chain.getCheckpointTransitionChainIdentifier());
    assertEquals(17L, chain.getFirstPriorCheckpointSequence());
    assertEquals(20L, chain.getFinalCandidateCheckpointSequence());
    assertEquals(3L, chain.getTotalSequenceDelta());
    assertEquals(2, chain.getTransitionCount());
    assertEquals(1, chain.getStrictAppendReceiptCount());
    assertEquals(1, chain.getUnchangedReceiptCount());
    assertEquals(first.getAddedReceiptCount(), chain.getAddedReceiptCount());
    assertEquals(first.getAddedLedgerReceiptCount(), chain.getAddedLedgerReceiptCount());
    assertEquals(first.getAddedTransitionCount(), chain.getAddedTransitionCount());
    assertEquals(first.getAddedReconciliationCount(), chain.getAddedReconciliationCount());
    assertEquals(first.getAddedEntryCount(), chain.getAddedEntryCount());
    assertEquals(first.getPriorFinalLedgerDigestHex(), chain.getFirstPriorLedgerDigestHex());
    assertEquals(second.getCandidateFinalLedgerDigestHex(), chain.getFinalCandidateLedgerDigestHex());
    assertTrue(
        S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
            .verify("checkpoint-transition-chain-checkpoint-transition-chain-A", Arrays.asList(first, second), chain));
  }

  /** Verify determinism, serialization, immutable receipts and defensive raw-digest copies. */
  @Test
  void testChainIsDeterministicSerializableAndDefensive() throws Exception {
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result prior = checkpointTransitionChain(
        false);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result candidate = checkpointTransitionChain(
        true);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, prior);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 18L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result laterCheckpoint = checkpoint(
        "checkpoint-series-A", 20L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result first = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, candidateCheckpoint);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result second = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(candidate, candidateCheckpoint, candidate, laterCheckpoint);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain.Result chain = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
        .create("checkpoint-transition-chain-checkpoint-transition-chain-A", Arrays.asList(first, second));
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain.Result repeated = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
        .create("checkpoint-transition-chain-checkpoint-transition-chain-A", Arrays.asList(first, second));
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain.Result restored = serializeChain(
        chain);
    byte[] firstDigest = chain.getChainDigestBytes();
    byte[] secondDigest = chain.getChainDigestBytes();
    byte[] firstCheckpointDigest = chain.getFirstPriorCheckpointDigestBytes();
    byte[] secondCheckpointDigest = chain.getFirstPriorCheckpointDigestBytes();
    firstDigest[0] ^= 0xff;
    firstCheckpointDigest[0] ^= 0xff;

    assertEquals(chain.getChainDigestHex(), repeated.getChainDigestHex());
    assertEquals(chain.getChainDigestHex(), restored.getChainDigestHex());
    assertNotSame(firstDigest, secondDigest);
    assertNotSame(firstCheckpointDigest, secondCheckpointDigest);
    assertThrows(UnsupportedOperationException.class, () -> chain.getTransitions().clear());
  }

  /** Verify gaps, duplicates, ordering drift and identity drift fail closed. */
  @Test
  void testGapsDuplicatesOrderingAndIdentityDriftFailClosed() {
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result prior = checkpointTransitionChain(
        false);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result candidate = checkpointTransitionChain(
        true);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, prior);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 18L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result gapPrior = checkpoint(
        "checkpoint-series-A", 19L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result laterCheckpoint = checkpoint(
        "checkpoint-series-A", 20L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result differentPrior = checkpoint(
        "checkpoint-series-B", 18L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result differentCandidate = checkpoint(
        "checkpoint-series-B", 20L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result first = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, candidateCheckpoint);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result gap = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(candidate, gapPrior, candidate, laterCheckpoint);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result differentSeries = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(candidate, differentPrior, candidate, differentCandidate);

    assertThrows(IllegalArgumentException.class,
        () -> S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
            .create("checkpoint-transition-chain-checkpoint-transition-chain-A", null));
    assertThrows(IllegalArgumentException.class,
        () -> S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
            .create("checkpoint-transition-chain-checkpoint-transition-chain-A", Collections.emptyList()));
    assertThrows(IllegalArgumentException.class,
        () -> S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
            .create("checkpoint-transition-chain-checkpoint-transition-chain-A", Arrays.asList(first, first)));
    assertThrows(IllegalArgumentException.class,
        () -> S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
            .create("checkpoint-transition-chain-checkpoint-transition-chain-A", Arrays.asList(first, gap)));
    assertThrows(IllegalArgumentException.class,
        () -> S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
            .create("checkpoint-transition-chain-checkpoint-transition-chain-A",
                Arrays.asList(first, differentSeries)));
    assertThrows(IllegalArgumentException.class,
        () -> S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain
            .verify("checkpoint-transition-chain-checkpoint-transition-chain-A", Collections.singletonList(first),
                null));
  }

  /** Serialize and restore one checkpoint-transition-chain checkpoint-transition chain. */
  private static S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain.Result serializeChain(
      S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain.Result result)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChain.Result) input
          .readObject();
    }
  }

  /** Build a qualified checkpoint-transition chain prefix or strict extension. */
  private static S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result checkpointTransitionChain(
      boolean includeThirdTransition) {
    S8ManifestTransitionChainTransitionLedgerTransitionChain.Result prior = priorTransitionChain();
    S8ManifestTransitionChainTransitionLedgerTransitionChain.Result candidate = transitionChain();
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, prior);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 18L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result laterCheckpoint = checkpoint(
        "checkpoint-series-A", 20L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result latestCheckpoint = checkpoint(
        "checkpoint-series-A", 21L, candidate);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition.Result first = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, candidateCheckpoint);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition.Result second = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition
        .create(candidate, candidateCheckpoint, candidate, laterCheckpoint);
    S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition.Result third = S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition
        .create(candidate, laterCheckpoint, candidate, latestCheckpoint);
    return S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.create(
        "checkpoint-transition-chain-A",
        includeThirdTransition ? Arrays.asList(first, second, third) : Arrays.asList(first, second));
  }

  /** Create one checkpoint for a qualified checkpoint-transition chain. */
  private static S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result checkpoint(
      String identifier, long sequence,
      S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result chain) {
    return S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint
        .create(identifier, sequence, chain);
  }

  /** Create one checkpoint for a qualified transition chain. */
  private static S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result checkpoint(String identifier,
      long sequence, S8ManifestTransitionChainTransitionLedgerTransitionChain.Result chain) {
    return S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.create(identifier, sequence, chain);
  }

  /** Build the qualified prefix of the transition-ledger transition chain. */
  private static S8ManifestTransitionChainTransitionLedgerTransitionChain.Result priorTransitionChain() {
    TransitionFixtures fixtures = transitionFixtures("ledger-A", "chain-A", "manifest-A");
    return S8ManifestTransitionChainTransitionLedgerTransitionChain.create("ledger-transition-chain-A",
        Arrays.asList(fixtures.firstTransition, fixtures.secondTransition));
  }

  /** Build one qualified transition-ledger transition chain. */
  private static S8ManifestTransitionChainTransitionLedgerTransitionChain.Result transitionChain() {
    TransitionFixtures fixtures = transitionFixtures("ledger-A", "chain-A", "manifest-A");
    return S8ManifestTransitionChainTransitionLedgerTransitionChain.create("ledger-transition-chain-A",
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
    S8ManifestTransition.Result append1 = S8ManifestTransition.create(state0, state1);
    S8ManifestTransition.Result unchanged = S8ManifestTransition.create(state1, state1);
    S8ManifestTransition.Result append2 = S8ManifestTransition.create(state1, state2);
    S8ManifestTransition.Result append3 = S8ManifestTransition.create(state2, state3);

    S8ManifestTransitionChain.Result chain0 = S8ManifestTransitionChain.create(chainIdentifier,
        Collections.singletonList(append1));
    S8ManifestTransitionChain.Result chain1 = S8ManifestTransitionChain.create(chainIdentifier,
        Arrays.asList(append1, unchanged));
    S8ManifestTransitionChain.Result chain2 = S8ManifestTransitionChain.create(chainIdentifier,
        Arrays.asList(append1, unchanged, append2));
    S8ManifestTransitionChain.Result chain3 = S8ManifestTransitionChain.create(chainIdentifier,
        Arrays.asList(append1, unchanged, append2, append3));

    S8ManifestTransitionChainTransition.Result chainReceipt1 = S8ManifestTransitionChainTransition.create(chain0,
        chain1);
    S8ManifestTransitionChainTransition.Result chainReceipt2 = S8ManifestTransitionChainTransition.create(chain1,
        chain2);
    S8ManifestTransitionChainTransition.Result chainReceipt3 = S8ManifestTransitionChainTransition.create(chain2,
        chain3);

    S8ManifestTransitionChainTransitionLedger.Result ledger0 = S8ManifestTransitionChainTransitionLedger
        .create(ledgerIdentifier, Collections.singletonList(chainReceipt1));
    S8ManifestTransitionChainTransitionLedger.Result ledger1 = S8ManifestTransitionChainTransitionLedger
        .create(ledgerIdentifier, Arrays.asList(chainReceipt1, chainReceipt2));
    S8ManifestTransitionChainTransitionLedger.Result ledger2 = S8ManifestTransitionChainTransitionLedger
        .create(ledgerIdentifier, Arrays.asList(chainReceipt1, chainReceipt2, chainReceipt3));

    S8ManifestTransitionChainTransitionLedgerTransition.Result firstTransition = S8ManifestTransitionChainTransitionLedgerTransition
        .create(ledger0, ledger1);
    S8ManifestTransitionChainTransitionLedgerTransition.Result secondTransition = S8ManifestTransitionChainTransitionLedgerTransition
        .create(ledger1, ledger2);
    S8ManifestTransitionChainTransitionLedgerTransition.Result unchangedTransition = S8ManifestTransitionChainTransitionLedgerTransition
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

  /** Serialize and restore one checkpoint-transition-chain checkpoint-transition receipt. */
  private static S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result serializeTransition(
      S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result result)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (S8ManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result) input
          .readObject();
    }
  }

  /** Adjacent ledger-transition receipts. */
  private static final class TransitionFixtures {
    private final S8ManifestTransitionChainTransitionLedgerTransition.Result firstTransition;
    private final S8ManifestTransitionChainTransitionLedgerTransition.Result secondTransition;
    private final S8ManifestTransitionChainTransitionLedgerTransition.Result unchangedTransition;

    private TransitionFixtures(S8ManifestTransitionChainTransitionLedgerTransition.Result firstTransition,
        S8ManifestTransitionChainTransitionLedgerTransition.Result secondTransition,
        S8ManifestTransitionChainTransitionLedgerTransition.Result unchangedTransition) {
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
