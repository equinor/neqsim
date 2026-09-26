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
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests ordered manifests of named S8 reconciliation checkpoints. */
class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 2.5e-5;
  private static final double WATER_INVENTORY_KG = 1000.0;
  private static final String ALLOCATION_BASIS = "caller-owned elemental sulfur scenario";
  private static final String PRODUCT_BASIS = "caller-owned S8 product identity";

  @Test
  void testDeterministicSerializedManifest() throws Exception {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry("reconciliation-A", reconciliation(fixture("target-A", "application-A", 2.0, true),
            fixture("target-B", "application-B", 5.0, false)));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry second = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry("reconciliation-B", reconciliation(fixture("target-C", "application-C", 7.0, true)));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .create("manifest-A", Arrays.asList(first, second));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result restored = serializeManifest(
        manifest);

    assertEquals("SHA-256", manifest.getDigestAlgorithm());
    assertEquals("neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-v1",
        manifest.getSchemaIdentifier());
    assertEquals("manifest-A", manifest.getManifestIdentifier());
    assertEquals(64, manifest.getDigestHex().length());
    assertTrue(manifest.getDigestHex().matches("[0-9a-f]{64}"));
    assertEquals(2, manifest.getReconciliationCount());
    assertEquals(3, manifest.getTotalEntryCount());
    assertEquals(2, manifest.getTotalStrictAppendCount());
    assertEquals(1, manifest.getTotalUnchangedCount());
    assertEquals(manifest.getDigestHex(), restored.getDigestHex());
    assertTrue(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .verify("manifest-A", Arrays.asList(first, second), restored));
  }

  @Test
  void testOrderIdentityAndDuplicateEvidenceFailClosed() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result firstReconciliation = reconciliation(
        fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result secondReconciliation = reconciliation(
        fixture("target-B", "application-B", 5.0, false));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry first = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry("reconciliation-A", firstReconciliation);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry second = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry("reconciliation-B", secondReconciliation);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .create("manifest-A", Arrays.asList(first, second));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry renamed = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry("reconciliation-C", firstReconciliation);

    assertFalse(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .verify("manifest-A", Arrays.asList(second, first), manifest));
    assertFalse(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .verify("manifest-A", Arrays.asList(renamed, second), manifest));
    assertFalse(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .verify("manifest-B", Arrays.asList(first, second), manifest));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
            .create("manifest-A", Arrays.asList(first, first)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
            .create("manifest-A", Arrays.asList(first, renamed)));
  }

  @Test
  void testManifestCollectionsAndDigestAreDefensive() throws Exception {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry entry = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry("reconciliation-A", reconciliation(fixture("target-A", "application-A", 2.0, true)));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .create("manifest-A", Collections.singletonList(entry));
    byte[] firstDigest = manifest.getDigestBytes();
    byte[] secondDigest = manifest.getDigestBytes();
    firstDigest[0] ^= 0xff;

    assertNotSame(firstDigest, secondDigest);
    assertNotSame(manifest.getEntries(), manifest.getEntries());
    assertThrows(UnsupportedOperationException.class, () -> manifest.getEntries().add(entry));
    assertEquals(manifest.getDigestHex(), serializeManifest(manifest).getDigestHex());
  }

  @Test
  void testMissingInputsFailClosed() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation = reconciliation(
        fixture("target-A", "application-A", 2.0, true));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry entry = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry("reconciliation-A", reconciliation);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .create("manifest-A", Collections.singletonList(entry));

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.entry(" ",
            reconciliation));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
            .entry("reconciliation-A", null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.create(" ",
            Collections.singletonList(entry)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
            .create("manifest-A", null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
            .create("manifest-A", Collections.emptyList()));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
            .create("manifest-A", Collections.singletonList(null)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
            .verify("manifest-A", Collections.singletonList(entry), null));
    assertTrue(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .verify("manifest-A", Collections.singletonList(entry), manifest));
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
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan
        .create(prior, candidate, transition, targetIdentifier, applicationKey, priorS8AmountMol);
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

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result serializeManifest(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result result)
      throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result) input
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
