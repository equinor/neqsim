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

/** Tests ordered continuity chains of S8 reconciliation-checkpoint manifest transitions. */
class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 2.5e-5;
  private static final double WATER_INVENTORY_KG = 1000.0;
  private static final String ALLOCATION_BASIS = "caller-owned elemental sulfur scenario";
  private static final String PRODUCT_BASIS = "caller-owned S8 product identity";

  @Test
  void testMixedChainClosesExactCountsAndEndpoints() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = entry(
        "reconciliation-A", fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry second = entry(
        "reconciliation-B", fixture("target-B", "application-B", 5.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry third = entry(
        "reconciliation-C", fixture("target-C", "application-C", 7.0, true),
        fixture("target-D", "application-D", 9.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state0 = manifest(
        "manifest-A", first);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state1 = manifest(
        "manifest-A", first, second);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state2 = manifest(
        "manifest-A", first, second, third);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result firstAppend = transition(
        state0, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result unchanged = transition(
        state1, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result secondAppend = transition(
        state1, state2);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create("chain-A", Arrays.asList(firstAppend, unchanged, secondAppend));

    assertEquals("SHA-256", chain.getDigestAlgorithm());
    assertEquals("neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-v1",
        chain.getSchemaIdentifier());
    assertEquals("chain-A", chain.getChainIdentifier());
    assertEquals("manifest-A", chain.getManifestIdentifier());
    assertEquals(state0.getDigestHex(), chain.getFirstManifestDigestHex());
    assertEquals(state2.getDigestHex(), chain.getFinalManifestDigestHex());
    assertEquals(3, chain.getTransitionCount());
    assertEquals(2, chain.getStrictAppendTransitionCount());
    assertEquals(1, chain.getUnchangedTransitionCount());
    assertEquals(2, chain.getAddedReconciliationCount());
    assertEquals(3, chain.getAddedEntryCount());
    assertEquals(1, chain.getAddedStrictAppendCount());
    assertEquals(2, chain.getAddedUnchangedCount());
    assertTrue(chain.getChainDigestHex().matches("[0-9a-f]{64}"));
    assertTrue(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .verify("chain-A", Arrays.asList(firstAppend, unchanged, secondAppend), chain));
  }

  @Test
  void testGapForkReorderIdentityAndDuplicateEvidenceFailClosed() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = entry(
        "reconciliation-A", fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry second = entry(
        "reconciliation-B", fixture("target-B", "application-B", 5.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry third = entry(
        "reconciliation-C", fixture("target-C", "application-C", 7.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry fourth = entry(
        "reconciliation-D", fixture("target-D", "application-D", 9.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state0 = manifest(
        "manifest-A", first);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state1 = manifest(
        "manifest-A", first, second);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state2 = manifest(
        "manifest-A", first, second, third);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state3 = manifest(
        "manifest-A", first, second, third, fourth);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result other0 = manifest(
        "manifest-B", first);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result other1 = manifest(
        "manifest-B", first, second);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result firstAppend = transition(
        state0, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result secondAppend = transition(
        state1, state2);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result fork = transition(
        state0, state2);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result gap = transition(
        state2, state3);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result unchanged = transition(
        state1, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result otherIdentity = transition(
        other0, other1);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-A", Arrays.asList(firstAppend, fork)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-A", Arrays.asList(firstAppend, gap)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-A", Arrays.asList(secondAppend, firstAppend)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-A", Arrays.asList(firstAppend, otherIdentity)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-A", Arrays.asList(unchanged, unchanged)));
  }

  @Test
  void testSerializationCollectionsDigestAndVerificationAreDefensive() throws Exception {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = entry(
        "reconciliation-A", fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry second = entry(
        "reconciliation-B", fixture("target-B", "application-B", 5.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state0 = manifest(
        "manifest-A", first);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state1 = manifest(
        "manifest-A", first, second);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result append = transition(
        state0, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create("chain-A", Collections.singletonList(append));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result restored = serializeChain(
        chain);
    byte[] firstDigest = chain.getChainDigestBytes();
    byte[] secondDigest = chain.getChainDigestBytes();
    firstDigest[0] ^= 0xff;

    assertNotSame(firstDigest, secondDigest);
    assertNotSame(chain.getTransitions(), chain.getTransitions());
    assertThrows(UnsupportedOperationException.class, () -> chain.getTransitions().add(append));
    assertEquals(chain.getChainDigestHex(), restored.getChainDigestHex());
    assertTrue(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .verify("chain-A", Collections.singletonList(append), restored));
    assertFalse(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .verify("chain-B", Collections.singletonList(append), restored));
    assertNotEquals(chain.getChainDigestHex(),
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-B", Collections.singletonList(append)).getChainDigestHex());
  }

  @Test
  void testMissingInputsFailClosed() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = entry(
        "reconciliation-A", fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state = manifest(
        "manifest-A", first);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result unchanged = transition(
        state, state);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create("chain-A", Collections.singletonList(unchanged));

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create(" ", Collections.singletonList(unchanged)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-A", null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-A", Collections.emptyList()));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .create("chain-A", Collections.singletonList(null)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
            .verify("chain-A", Collections.singletonList(unchanged), null));
    assertTrue(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .verify("chain-A", Collections.singletonList(unchanged), chain));
  }

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result transition(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result prior,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result candidate) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(prior, candidate);
  }

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry entry(
      String reconciliationIdentifier, Fixture... fixtures) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry(reconciliationIdentifier, reconciliation(fixtures));
  }

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest(
      String manifestIdentifier,
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry... entries) {
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .create(manifestIdentifier, Arrays.asList(entries));
  }

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation(
      Fixture... fixtures) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request[] previewRequests = new AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request[fixtures.length];
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request[] receiptRequests = new AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request[fixtures.length];
    for (int index = 0; index < fixtures.length; index++) {
      Fixture fixture = fixtures[index];
      previewRequests[index] = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request
          .create(fixture.plan, fixture.targetIdentifier, fixture.applicationKey, fixture.priorStream);
      AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result candidate = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
          .applyToClone(fixture.plan, fixture.targetIdentifier, fixture.applicationKey, fixture.priorStream);
      receiptRequests[index] = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request.create(
          fixture.plan, fixture.targetIdentifier, fixture.applicationKey, fixture.priorStream,
          candidate.getCandidateStream());
    }
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result preview = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Arrays.asList(previewRequests));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result receipt = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
        .verify(Arrays.asList(receiptRequests));
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.reconcile(preview, receipt);
  }

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

  private static StreamInterface stream(String name, double s8AmountMol) {
    SystemInterface system = new SystemSrkEos(298.15, 80.0);
    system.addComponent("methane", 10.0);
    system.addComponent("CO2", 3.0);
    system.addComponent("S8", s8AmountMol);
    system.init(0);
    return new Stream(name, system);
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

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result serializeChain(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result result)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result) input
          .readObject();
    }
  }

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
