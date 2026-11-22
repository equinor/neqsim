package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests immutable ledgers of S8 manifest transition-chain transition receipts. */
class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 2.5e-5;
  private static final double WATER_INVENTORY_KG = 1000.0;
  private static final String ALLOCATION_BASIS = "caller-owned elemental sulfur scenario";
  private static final String PRODUCT_BASIS = "caller-owned S8 product identity";

  /** Verify ordered receipts close exact aggregates and endpoints. */
  @Test
  void testOrderedLedgerClosesAggregatesAndEndpoints() {
    LedgerFixtures fixtures = ledgerFixtures("chain-A", "manifest-A");
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result ledger = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
        .create("ledger-A", Arrays.asList(fixtures.firstReceipt, fixtures.secondReceipt));

    assertEquals("SHA-256", ledger.getDigestAlgorithm());
    assertEquals(
        "neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-transition-chain-transition-ledger-v1",
        ledger.getSchemaIdentifier());
    assertEquals("ledger-A", ledger.getLedgerIdentifier());
    assertEquals("chain-A", ledger.getChainIdentifier());
    assertEquals("manifest-A", ledger.getManifestIdentifier());
    assertEquals(2, ledger.getTransitionCount());
    assertEquals(fixtures.firstReceipt.getPriorChainDigestHex(), ledger.getFirstPriorChainDigestHex());
    assertEquals(fixtures.secondReceipt.getCandidateChainDigestHex(), ledger.getFinalCandidateChainDigestHex());
    assertEquals(fixtures.firstReceipt.getPriorFinalManifestDigestHex(), ledger.getFirstPriorFinalManifestDigestHex());
    assertEquals(fixtures.secondReceipt.getCandidateFinalManifestDigestHex(),
        ledger.getFinalCandidateFinalManifestDigestHex());
    assertEquals(fixtures.firstReceipt.getAddedTransitionCount() + fixtures.secondReceipt.getAddedTransitionCount(),
        ledger.getAddedTransitionCount());
    assertEquals(
        fixtures.firstReceipt.getAddedStrictAppendTransitionCount()
            + fixtures.secondReceipt.getAddedStrictAppendTransitionCount(),
        ledger.getAddedStrictAppendTransitionCount());
    assertEquals(fixtures.firstReceipt.getAddedUnchangedTransitionCount()
        + fixtures.secondReceipt.getAddedUnchangedTransitionCount(), ledger.getAddedUnchangedTransitionCount());
    assertEquals(
        fixtures.firstReceipt.getAddedReconciliationCount() + fixtures.secondReceipt.getAddedReconciliationCount(),
        ledger.getAddedReconciliationCount());
    assertEquals(fixtures.firstReceipt.getAddedEntryCount() + fixtures.secondReceipt.getAddedEntryCount(),
        ledger.getAddedEntryCount());
    assertTrue(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
            .verify("ledger-A", Arrays.asList(fixtures.firstReceipt, fixtures.secondReceipt), ledger));
  }

  /** Verify duplicate, reordered, disconnected, and identity-drift evidence fails closed. */
  @Test
  void testInvalidReceiptSequencesFailClosed() {
    LedgerFixtures fixtures = ledgerFixtures("chain-A", "manifest-A");
    LedgerFixtures otherChain = ledgerFixtures("chain-B", "manifest-A");

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
            .create("ledger-A", Arrays.asList(fixtures.firstReceipt, fixtures.firstReceipt)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
            .create("ledger-A", Arrays.asList(fixtures.secondReceipt, fixtures.firstReceipt)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
            .create("ledger-A", Arrays.asList(fixtures.firstReceipt, otherChain.secondReceipt)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
            .create("ledger-A", Collections.singletonList(null)));
  }

  /** Verify result collections, digest bytes, serialization, and ledger identity are deterministic. */
  @Test
  void testImmutabilitySerializationAndIdentityBinding() throws Exception {
    LedgerFixtures fixtures = ledgerFixtures("chain-A", "manifest-A");
    List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result> receipts = Arrays
        .asList(fixtures.firstReceipt, fixtures.secondReceipt);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result ledger = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
        .create("ledger-A", receipts);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result restored = serializeLedger(
        ledger);

    assertNotSame(ledger.getTransitions(), ledger.getTransitions());
    assertThrows(UnsupportedOperationException.class, () -> ledger.getTransitions().add(fixtures.firstReceipt));
    byte[] digest = ledger.getLedgerDigestBytes();
    assertNotSame(digest, ledger.getLedgerDigestBytes());
    digest[0] ^= 0x01;
    assertEquals(ledger.getLedgerDigestHex(), restored.getLedgerDigestHex());
    assertTrue(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
            .verify("ledger-A", receipts, restored));
    assertNotEquals(ledger.getLedgerDigestHex(),
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger
            .create("ledger-B", receipts).getLedgerDigestHex());
  }

  /** Verify internal count accumulation fails closed on integer overflow. */
  @Test
  void testCountOverflowFailsClosed() throws Exception {
    Method addExact = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.class
        .getDeclaredMethod("addExact", int.class, int.class, String.class);
    addExact.setAccessible(true);
    InvocationTargetException exception = assertThrows(InvocationTargetException.class,
        () -> addExact.invoke(null, Integer.MAX_VALUE, 1, "Count"));
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
  }

  /** Build two adjacent qualified chain-transition receipts. */
  private static LedgerFixtures ledgerFixtures(String chainIdentifier, String manifestIdentifier) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = entry(
        "reconciliation-A", fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry second = entry(
        "reconciliation-B", fixture("target-B", "application-B", 5.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry third = entry(
        "reconciliation-C", fixture("target-C", "application-C", 7.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state0 = manifest(
        manifestIdentifier, first);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state1 = manifest(
        manifestIdentifier, first, second);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result state2 = manifest(
        manifestIdentifier, first, second, third);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result firstAppend = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state0, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result unchanged = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state1, state1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition.Result secondAppend = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransition
        .create(state1, state2);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain0 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Collections.singletonList(firstAppend));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain1 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Arrays.asList(firstAppend, unchanged));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain.Result chain2 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChain
        .create(chainIdentifier, Arrays.asList(firstAppend, unchanged, secondAppend));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result firstReceipt = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .create(chain0, chain1);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result secondReceipt = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition
        .create(chain1, chain2);
    return new LedgerFixtures(firstReceipt, secondReceipt);
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

  /** Serialize and restore one ledger result. */
  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result serializeLedger(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result result)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedger.Result) input
          .readObject();
    }
  }

  /** Adjacent chain-transition receipts. */
  private static final class LedgerFixtures {
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result firstReceipt;
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result secondReceipt;

    /** Create receipt fixtures. */
    private LedgerFixtures(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result firstReceipt,
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransition.Result secondReceipt) {
      this.firstReceipt = firstReceipt;
      this.secondReceipt = secondReceipt;
    }
  }

  /** Stream-application fixture. */
  private static final class Fixture {
    private final AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan;
    private final String targetIdentifier;
    private final String applicationKey;
    private final StreamInterface priorStream;

    /** Create one stream-application fixture. */
    private Fixture(AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan, String targetIdentifier,
        String applicationKey, StreamInterface priorStream) {
      this.plan = plan;
      this.targetIdentifier = targetIdentifier;
      this.applicationKey = applicationKey;
      this.priorStream = priorStream;
    }
  }
}
