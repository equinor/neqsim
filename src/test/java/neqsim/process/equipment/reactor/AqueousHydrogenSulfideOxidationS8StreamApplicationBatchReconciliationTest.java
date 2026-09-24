package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 1.0e-3;
  private static final double WATER_INVENTORY_KG = 1000.0;
  private static final String ALLOCATION_BASIS = "caller-owned elemental sulfur scenario";
  private static final String PRODUCT_BASIS = "caller-owned S8 product identity";

  @Test
  void testOrderedPreviewAndApplicationReconcile() {
    Fixture append = fixture("target-A", "application-A", 2.0, true);
    Fixture unchanged = fixture("target-B", "application-B", 5.0, false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result preview = preview(append, unchanged);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result application = application(append, unchanged);

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result result = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation
        .reconcile(preview, application);

    assertEquals(2, result.getEntries().size());
    assertEquals("target-A", result.getEntries().get(0).getTargetStateIdentifier());
    assertEquals("target-B", result.getEntries().get(1).getTargetStateIdentifier());
    assertEquals(1, result.getStrictAppendCount());
    assertEquals(1, result.getUnchangedCount());
    assertEquals(result.getPreviewObservedS8IncrementMol(), result.getApplicationObservedS8IncrementMol(),
        result.getS8ToleranceMol());
    assertEquals(result.getPreviewObservedTotalIncrementMol(), result.getApplicationObservedTotalIncrementMol(),
        result.getTotalAmountToleranceMol());
    assertTrue(Math.abs(result.getS8ResidualMol()) <= result.getS8ToleranceMol());
    assertTrue(Math.abs(result.getTotalAmountResidualMol()) <= result.getTotalAmountToleranceMol());
    assertEquals(0.0, result.getMaximumEntryS8ResidualMol(), 0.0);
    assertEquals(0.0, result.getMaximumEntryTotalAmountResidualMol(), 0.0);
  }

  @Test
  void testReorderedApplicationFailsClosed() {
    Fixture first = fixture("target-A", "application-A", 2.0, true);
    Fixture second = fixture("target-B", "application-B", 5.0, false);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.reconcile(preview(first, second),
            application(second, first)));
  }

  @Test
  void testDifferentTransitionAndInvalidEvidenceFailClosed() {
    Fixture expected = fixture("target-A", "application-A", 2.0, true);
    Fixture different = fixture("target-A", "application-A", 2.0, false);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.reconcile(preview(expected),
            application(different)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.reconcile(null,
            application(expected)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.reconcile(preview(expected), null));
  }

  @Test
  void testResultIsImmutableDeterministicAndSerializable() throws Exception {
    Fixture append = fixture("target-A", "application-A", 2.0, true);
    Fixture unchanged = fixture("target-B", "application-B", 5.0, false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result preview = preview(append, unchanged);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result application = application(append, unchanged);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result first = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation
        .reconcile(preview, application);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result second = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation
        .reconcile(preview, application);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result restored = serializeRoundTrip(first);

    assertThrows(UnsupportedOperationException.class, () -> first.getEntries().clear());
    assertEquals(Double.doubleToLongBits(first.getS8ResidualMol()), Double.doubleToLongBits(second.getS8ResidualMol()));
    assertEquals(first.getEntries().get(0).getTransitionDigestHex(),
        restored.getEntries().get(0).getTransitionDigestHex());
    assertEquals(Double.doubleToLongBits(first.getTotalAmountResidualMol()),
        Double.doubleToLongBits(restored.getTotalAmountResidualMol()));
  }

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result preview(Fixture... fixtures) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request[] requests = new AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request[fixtures.length];
    for (int index = 0; index < fixtures.length; index++) {
      requests[index] = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request.create(
          fixtures[index].plan, fixtures[index].targetIdentifier, fixtures[index].applicationKey,
          fixtures[index].priorStream);
    }
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.applyToClones(Arrays.asList(requests));
  }

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result application(
      Fixture... fixtures) {
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request[] requests = new AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request[fixtures.length];
    for (int index = 0; index < fixtures.length; index++) {
      AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result candidate = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
          .applyToClone(fixtures[index].plan, fixtures[index].targetIdentifier, fixtures[index].applicationKey,
              fixtures[index].priorStream);
      requests[index] = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request.create(
          fixtures[index].plan, fixtures[index].targetIdentifier, fixtures[index].applicationKey,
          fixtures[index].priorStream, candidate.getCandidateStream());
    }
    return AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.verify(Arrays.asList(requests));
  }

  private static Fixture fixture(String targetIdentifier, String applicationKey, double priorS8AmountMol,
      boolean append) {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = plan(targetIdentifier, applicationKey,
        priorS8AmountMol, append);
    return new Fixture(plan, targetIdentifier, applicationKey, stream("prior-" + targetIdentifier, priorS8AmountMol));
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan(String targetIdentifier,
      String applicationKey, double priorS8AmountMol, boolean append) {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = append
        ? ledger(batch(4.0, 0.25, "batch-0", "segment-0"), batch(6.0, 0.50, "batch-1", "segment-1"))
        : prior;
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    return AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate, transition, targetIdentifier,
        applicationKey, priorS8AmountMol);
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

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result result) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result) input.readObject();
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
