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

/** Tests immutable transitions between qualified S8 checkpoint-transition-chain checkpoints. */
class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 2.5e-5;
  private static final double WATER_INVENTORY_KG = 1000.0;
  private static final String ALLOCATION_BASIS = "caller-owned elemental sulfur scenario";
  private static final String PRODUCT_BASIS = "caller-owned S8 product identity";

  /** Verify a strict append preserves the exact prefix and closes every aggregate delta. */
  @Test
  void testStrictAppendTransitionPreservesEvidenceAndClosesCounts() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result prior = checkpointTransitionChain(false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result candidate = checkpointTransitionChain(true);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, prior);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 18L, candidate);

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result receipt = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, candidateCheckpoint);

    assertEquals("SHA-256", receipt.getDigestAlgorithm());
    assertEquals("checkpoint-series-A", receipt.getCheckpointTransitionChainCheckpointIdentifier());
    assertEquals(17L, receipt.getPriorCheckpointSequence());
    assertEquals(18L, receipt.getCandidateCheckpointSequence());
    assertEquals(1L, receipt.getSequenceDelta());
    assertTrue(receipt.isStrictAppend());
    assertFalse(receipt.isUnchanged());
    assertEquals(candidate.getTransitionCount() - prior.getTransitionCount(), receipt.getAddedReceiptCount());
    assertEquals(candidate.getAddedLedgerReceiptCount() - prior.getAddedLedgerReceiptCount(),
        receipt.getAddedLedgerReceiptCount());
    assertEquals(candidate.getAddedTransitionCount() - prior.getAddedTransitionCount(),
        receipt.getAddedTransitionCount());
    assertEquals(candidate.getAddedStrictAppendTransitionCount() - prior.getAddedStrictAppendTransitionCount(),
        receipt.getAddedStrictAppendTransitionCount());
    assertEquals(candidate.getAddedUnchangedTransitionCount() - prior.getAddedUnchangedTransitionCount(),
        receipt.getAddedUnchangedTransitionCount());
    assertEquals(candidate.getAddedReconciliationCount() - prior.getAddedReconciliationCount(),
        receipt.getAddedReconciliationCount());
    assertEquals(candidate.getAddedEntryCount() - prior.getAddedEntryCount(), receipt.getAddedEntryCount());
    assertEquals(candidate.getAddedStrictAppendCount() - prior.getAddedStrictAppendCount(),
        receipt.getAddedStrictAppendCount());
    assertEquals(candidate.getAddedUnchangedCount() - prior.getAddedUnchangedCount(), receipt.getAddedUnchangedCount());
    assertEquals(prior.getFinalCandidateLedgerDigestHex(), receipt.getPriorFinalLedgerDigestHex());
    assertEquals(candidate.getFinalCandidateLedgerDigestHex(), receipt.getCandidateFinalLedgerDigestHex());
    assertTrue(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
            .verify(prior, priorCheckpoint, candidate, candidateCheckpoint, receipt));
  }

  /** Verify an unchanged chain is allowed only at a later checkpoint sequence. */
  @Test
  void testUnchangedChainAtLaterSequenceIsQualified() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result chain = checkpointTransitionChain(false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, chain);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 19L, chain);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result repeatedCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, chain);

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result receipt = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(chain, priorCheckpoint, chain, candidateCheckpoint);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result repeatedReceipt = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(chain, priorCheckpoint, chain, repeatedCheckpoint);

    assertTrue(receipt.isUnchanged());
    assertFalse(receipt.isStrictAppend());
    assertEquals(2L, receipt.getSequenceDelta());
    assertEquals(0, receipt.getAddedReceiptCount());
    assertEquals(0, receipt.getAddedLedgerReceiptCount());
    assertEquals(0, receipt.getAddedTransitionCount());
    assertEquals(0, receipt.getAddedReconciliationCount());
    assertEquals(0, receipt.getAddedEntryCount());
    assertEquals(receipt.getPriorChainDigestHex(), receipt.getCandidateChainDigestHex());
    assertTrue(repeatedReceipt.isUnchanged());
    assertEquals(0L, repeatedReceipt.getSequenceDelta());
  }

  /** Verify determinism, serialization and defensive raw-digest copies. */
  @Test
  void testDigestIsDeterministicSerializableAndDefensive() throws Exception {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result prior = checkpointTransitionChain(false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result candidate = checkpointTransitionChain(true);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, prior);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 18L, candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result laterCheckpoint = checkpoint(
        "checkpoint-series-A", 19L, candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result first = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, candidateCheckpoint);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result repeated = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, candidateCheckpoint);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result changedSequence = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, laterCheckpoint);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result restored = serializeTransition(
        first);
    byte[] firstDigest = first.getTransitionDigestBytes();
    byte[] secondDigest = first.getTransitionDigestBytes();
    byte[] firstCheckpointDigest = first.getPriorCheckpointDigestBytes();
    byte[] secondCheckpointDigest = first.getPriorCheckpointDigestBytes();
    firstDigest[0] ^= 0xff;
    firstCheckpointDigest[0] ^= 0xff;

    assertEquals(first.getTransitionDigestHex(), repeated.getTransitionDigestHex());
    assertNotEquals(first.getTransitionDigestHex(), changedSequence.getTransitionDigestHex());
    assertEquals(first.getTransitionDigestHex(), restored.getTransitionDigestHex());
    assertNotSame(firstDigest, secondDigest);
    assertNotSame(firstCheckpointDigest, secondCheckpointDigest);
  }

  /** Verify nulls, sequence drift, truncation and identity drift fail closed. */
  @Test
  void testInvalidCheckpointTransitionsFailClosed() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result prior = checkpointTransitionChain(false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result candidate = checkpointTransitionChain(true);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, prior);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 18L, candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result sameSequence = checkpoint(
        "checkpoint-series-A", 17L, candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result differentSeries = checkpoint(
        "checkpoint-series-B", 18L, candidate);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
            .create(null, priorCheckpoint, candidate, candidateCheckpoint));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
            .create(prior, priorCheckpoint, candidate, null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
            .create(prior, priorCheckpoint, candidate, sameSequence));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
            .create(prior, priorCheckpoint, candidate, differentSeries));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
            .create(candidate, candidateCheckpoint, prior, checkpoint("checkpoint-series-A", 19L, prior)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition
            .verify(prior, priorCheckpoint, candidate, candidateCheckpoint, null));
  }


  /** Build a qualified checkpoint-transition chain prefix or strict extension. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result checkpointTransitionChain(
      boolean includeThirdTransition) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result prior = priorTransitionChain();
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result candidate = transitionChain();
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result priorCheckpoint = checkpoint(
        "checkpoint-series-A", 17L, prior);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result candidateCheckpoint = checkpoint(
        "checkpoint-series-A", 18L, candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result laterCheckpoint = checkpoint(
        "checkpoint-series-A", 20L, candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result latestCheckpoint = checkpoint(
        "checkpoint-series-A", 21L, candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition.Result first = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition
        .create(prior, priorCheckpoint, candidate, candidateCheckpoint);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition.Result second = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition
        .create(candidate, candidateCheckpoint, candidate, laterCheckpoint);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition.Result third = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition
        .create(candidate, laterCheckpoint, candidate, latestCheckpoint);
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.create("checkpoint-transition-chain-A",
        includeThirdTransition ? Arrays.asList(first, second, third) : Arrays.asList(first, second));
  }

  /** Create one checkpoint for a qualified checkpoint-transition chain. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result checkpoint(
      String identifier, long sequence, AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChain.Result chain) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.create(identifier, sequence, chain);
  }

  /** Create one checkpoint for a qualified transition chain. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint.Result checkpoint(
      String identifier, long sequence,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result chain) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpoint
        .create(identifier, sequence, chain);
  }

  /** Build the qualified prefix of the transition-ledger transition chain. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain.Result priorTransitionChain() {
    TransitionFixtures fixtures = transitionFixtures("ledger-A", "chain-A", "manifest-A");
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChain
        .create("ledger-transition-chain-A", Arrays.asList(fixtures.firstTransition, fixtures.secondTransition));
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


  /** Serialize and restore one checkpoint-transition-chain checkpoint-transition receipt. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result serializeTransition(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result result) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransition.Result) input.readObject();
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
