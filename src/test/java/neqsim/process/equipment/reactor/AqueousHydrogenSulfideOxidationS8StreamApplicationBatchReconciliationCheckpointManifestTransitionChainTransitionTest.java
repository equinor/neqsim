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

/** Tests for immutable S8 manifest transition-chain transition receipts. */
class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 2.5e-5;
  private static final double WATER_INVENTORY_KG = 1000.0;
  private static final String ALLOCATION_BASIS = "caller-owned elemental sulfur scenario";
  private static final String PRODUCT_BASIS = "caller-owned S8 product identity";

  /** Verify strict append and unchanged receipts close all exact count deltas. */
  @Test
  void testStrictAppendAndUnchangedReceiptsCloseExactDeltas() {
    Chains chains = chains("chain-A", "manifest-A");
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result appended = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .create(chains.prior, chains.candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result unchanged = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .create(chains.candidate, chains.candidate);

    assertEquals("SHA-256", appended.getDigestAlgorithm());
    assertEquals(
        "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-v1",
        appended.getSchemaIdentifier());
    assertEquals("chain-A", appended.getChainIdentifier());
    assertEquals("manifest-A", appended.getManifestIdentifier());
    assertEquals(chains.prior.getChainDigestHex(), appended.getPriorChainDigestHex());
    assertEquals(chains.candidate.getChainDigestHex(), appended.getCandidateChainDigestHex());
    assertTrue(appended.isStrictAppend());
    assertFalse(appended.isUnchanged());
    assertEquals(1, appended.getAddedTransitionCount());
    assertEquals(0, appended.getAddedStrictAppendTransitionCount());
    assertEquals(1, appended.getAddedUnchangedTransitionCount());
    assertEquals(0, appended.getAddedReconciliationCount());
    assertEquals(0, appended.getAddedEntryCount());
    assertEquals(0, appended.getAddedStrictAppendCount());
    assertEquals(0, appended.getAddedUnchangedCount());
    assertTrue(appended.getTransitionDigestHex().matches("[0-9a-f]{64}"));
    assertTrue(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .verify(chains.prior, chains.candidate, appended));

    assertTrue(unchanged.isUnchanged());
    assertFalse(unchanged.isStrictAppend());
    assertEquals(0, unchanged.getAddedTransitionCount());
    assertTrue(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .verify(chains.candidate, chains.candidate, unchanged));
    assertNotEquals(appended.getTransitionDigestHex(), unchanged.getTransitionDigestHex());
  }

  /** Verify truncation, replacement, identity drift, and missing inputs fail closed. */
  @Test
  void testTruncationReplacementIdentityDriftAndMissingInputsFailClosed() {
    Chains chains = chains("chain-A", "manifest-A");
    Chains otherChain = chains("chain-B", "manifest-A");
    Chains otherManifest = chains("chain-A", "manifest-B");
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result replacement = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create("chain-A", Collections.singletonList(chains.secondTransition));

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
            .create(chains.candidate, chains.prior));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
            .create(chains.prior, replacement));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
            .create(chains.prior, otherChain.candidate));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
            .create(chains.prior, otherManifest.candidate));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
            .create(null, chains.candidate));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
            .create(chains.prior, null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
            .verify(chains.prior, chains.candidate, null));
  }

  /** Verify Java serialization, defensive digest access, and deterministic verification. */
  @Test
  void testSerializationDigestAndVerificationAreDefensive() throws Exception {
    Chains chains = chains("chain-A", "manifest-A");
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result transition = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .create(chains.prior, chains.candidate);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result restored = serializeTransition(
        transition);
    byte[] firstDigest = transition.getTransitionDigestBytes();
    byte[] secondDigest = transition.getTransitionDigestBytes();
    firstDigest[0] ^= 0xff;

    assertNotSame(firstDigest, secondDigest);
    assertEquals(transition.getTransitionDigestHex(), restored.getTransitionDigestHex());
    assertTrue(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .verify(chains.prior, chains.candidate, restored));
    assertFalse(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .verify(chains.candidate, chains.candidate, restored));
  }

  /**
   * Build prior and candidate chains with one exact append.
   *
   * @param chainIdentifier chain identity
   * @param manifestIdentifier manifest identity
   * @return related chain fixture
   */
  private static Chains chains(String chainIdentifier, String manifestIdentifier) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = entry(
        "reconciliation-A", fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry second = entry(
        "reconciliation-B", fixture("target-B", "application-B", 5.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state0 = manifest(
        manifestIdentifier, first);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state1 = manifest(
        manifestIdentifier, first, second);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result firstTransition = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state0, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result secondTransition = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state1, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result prior = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Collections.singletonList(firstTransition));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result candidate = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Arrays.asList(firstTransition, secondTransition));
    return new Chains(prior, candidate, secondTransition);
  }

  /**
   * Create one manifest entry from a reconciliation fixture.
   *
   * @param reconciliationIdentifier reconciliation identity
   * @param fixture stream-application fixture
   * @return immutable manifest entry
   */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry entry(
      String reconciliationIdentifier, Fixture fixture) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry(reconciliationIdentifier, reconciliation(fixture));
  }

  /**
   * Create one manifest state.
   *
   * @param manifestIdentifier manifest identity
   * @param entries ordered manifest entries
   * @return immutable manifest result
   */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest(
      String manifestIdentifier,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry... entries) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .create(manifestIdentifier, Arrays.asList(entries));
  }

  /**
   * Create one reconciled stream-application result.
   *
   * @param fixture stream-application fixture
   * @return immutable reconciliation result
   */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation(
      Fixture fixture) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request previewRequest = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request
        .create(fixture.plan, fixture.targetIdentifier, fixture.applicationKey, fixture.priorStream);
    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result candidate = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(fixture.plan, fixture.targetIdentifier, fixture.applicationKey,
            fixture.priorStream);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request receiptRequest = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request
        .create(fixture.plan, fixture.targetIdentifier, fixture.applicationKey, fixture.priorStream,
            candidate.getCandidateStream());
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result preview = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Collections.singletonList(previewRequest));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result receipt = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
        .verify(Collections.singletonList(receiptRequest));
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.reconcile(preview,
        receipt);
  }

  /**
   * Build one stream-application fixture.
   *
   * @param targetIdentifier target identity
   * @param applicationKey application idempotency key
   * @param priorS8AmountMol prior stream S8 amount in mol
   * @param append true to append one transfer-ledger batch
   * @return fixture
   */
  private static Fixture fixture(String targetIdentifier, String applicationKey,
      double priorS8AmountMol, boolean append) {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(
        batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = append
        ? ledger(batch(4.0, 0.25, "batch-0", "segment-0"),
            batch(6.0, 0.50, "batch-1", "segment-1"))
        : prior;
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result ledgerTransition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan
        .create(prior, candidate, ledgerTransition, targetIdentifier, applicationKey,
            priorS8AmountMol);
    return new Fixture(plan, targetIdentifier, applicationKey,
        stream("prior-" + targetIdentifier, priorS8AmountMol));
  }

  /**
   * Create a stream containing S8.
   *
   * @param name stream name
   * @param s8AmountMol S8 amount in mol
   * @return stream
   */
  private static StreamInterface stream(String name, double s8AmountMol) {
    SystemInterface system = new SystemSrkEos(298.15, 80.0);
    system.addComponent("methane", 10.0);
    system.addComponent("CO2", 3.0);
    system.addComponent("S8", s8AmountMol);
    system.init(0);
    return new Stream(name, system);
  }

  /**
   * Create an S8 transfer ledger.
   *
   * @param batches ordered batches
   * @return ledger result
   */
  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger(
      AqueousHydrogenSulfideOxidationS8TransferBatch.Result... batches) {
    return AqueousHydrogenSulfideOxidationS8TransferLedger.create(Arrays.asList(batches),
        "ledger-A");
  }

  /**
   * Create one qualified S8 transfer batch.
   *
   * @param durationHours segment duration in hours
   * @param allocationFraction elemental-sulfur allocation fraction
   * @param batchIdentifier batch identity
   * @param idempotencyKey transfer idempotency key
   * @return transfer batch
   */
  private static AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch(
      double durationHours, double allocationFraction, String batchIdentifier,
      String idempotencyKey) {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment = new AqueousHydrogenSulfideOxidationTrajectory.Segment(
        durationHours, 298.15, 8.0, 0.723, 250.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult = AqueousHydrogenSulfideOxidationTrajectory
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment))
        .getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult,
            WATER_INVENTORY_KG), allocationFraction, ALLOCATION_BASIS);
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer = AqueousHydrogenSulfideOxidationS8Transfer
        .create(allocation, AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL,
            PRODUCT_BASIS, idempotencyKey);
    return AqueousHydrogenSulfideOxidationS8TransferBatch
        .create(Collections.singletonList(transfer), batchIdentifier);
  }

  /**
   * Serialize and restore one chain-transition receipt.
   *
   * @param result receipt to serialize
   * @return restored receipt
   * @throws Exception if serialization fails
   */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result serializeTransition(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result result)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(
        new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result) input
          .readObject();
    }
  }

  /** Related transition-chain fixtures. */
  private static final class Chains {
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result prior;
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result candidate;
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result secondTransition;

    /**
     * Create related chain fixtures.
     *
     * @param prior prior chain
     * @param candidate candidate chain
     * @param secondTransition candidate-only transition
     */
    private Chains(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result prior,
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result candidate,
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result secondTransition) {
      this.prior = prior;
      this.candidate = candidate;
      this.secondTransition = secondTransition;
    }
  }

  /** Stream-application fixture. */
  private static final class Fixture {
    private final AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan;
    private final String targetIdentifier;
    private final String applicationKey;
    private final StreamInterface priorStream;

    /**
     * Create one stream-application fixture.
     *
     * @param plan component-addition plan
     * @param targetIdentifier target identity
     * @param applicationKey application idempotency key
     * @param priorStream prior stream
     */
    private Fixture(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan,
        String targetIdentifier, String applicationKey, StreamInterface priorStream) {
      this.plan = plan;
      this.targetIdentifier = targetIdentifier;
      this.applicationKey = applicationKey;
      this.priorStream = priorStream;
    }
  }
}
