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
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests ordered fail-closed batch previews of verified S8 stream additions. */
public class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreviewTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";

  @Test
  void testOrderedBatchClosesOneAppendAndOneUnchangedEntry() {
    RequestFixture append = fixture("target-A", "application-A", 2.0, true);
    RequestFixture unchanged = fixture("target-B", "application-B", 5.0, false);

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result result = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Arrays.asList(append.request, unchanged.request));

    assertEquals(2, result.getPreviews().size());
    assertEquals(1, result.getStrictAppendCount());
    assertEquals(1, result.getUnchangedCount());
    assertEquals("target-A", result.getPreviews().get(0).getApplicationReceipt().getTargetStateIdentifier());
    assertEquals("target-B", result.getPreviews().get(1).getApplicationReceipt().getTargetStateIdentifier());
    assertEquals(result.getPlannedS8IncrementMol(), result.getObservedS8IncrementMol(),
        result.getClosureToleranceMol());
    assertTrue(Math.abs(result.getClosureResidualMol()) <= result.getClosureToleranceMol());
  }

  @Test
  void testCallerStreamsAndResultCandidatesRemainIndependent() {
    RequestFixture append = fixture("target-A", "application-A", 2.0, true);
    double callerTotalBefore = append.callerStream.getFluid().getTotalNumberOfMoles();

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result result = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Collections.singletonList(append.request));
    StreamInterface first = result.getPreviews().get(0).getCandidateStream();
    first.getFluid().addComponent("methane", 1.0);
    first.getFluid().init(0);
    StreamInterface second = result.getPreviews().get(0).getCandidateStream();

    assertEquals(callerTotalBefore, append.callerStream.getFluid().getTotalNumberOfMoles(), 0.0);
    assertNotSame(append.callerStream, second);
    assertNotSame(append.callerStream.getFluid(), second.getFluid());
    assertNotSame(first, second);
    assertNotSame(first.getFluid(), second.getFluid());
    assertThrows(UnsupportedOperationException.class, () -> result.getPreviews().clear());
  }

  @Test
  void testDuplicateTargetAndApplicationIdentitiesFailClosed() {
    RequestFixture first = fixture("target-A", "application-A", 2.0, true);
    RequestFixture duplicateTarget = fixture("target-A", "application-B", 3.0, false);
    RequestFixture duplicateKey = fixture("target-B", "application-A", 4.0, false);
    double firstTotalBefore = first.callerStream.getFluid().getTotalNumberOfMoles();

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Arrays.asList(first.request, duplicateTarget.request)));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Arrays.asList(first.request, duplicateKey.request)));
    assertEquals(firstTotalBefore, first.callerStream.getFluid().getTotalNumberOfMoles(), 0.0);
  }

  @Test
  void testInvalidBatchEntryAndCloneFailWithoutMutation() {
    RequestFixture valid = fixture("target-A", "application-A", 2.0, true);
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.applyToClones(null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.applyToClones(
            Collections.<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request>emptyList()));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Arrays.asList(valid.request, null)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request.create(valid.request.getPlan(),
            "target-C", "application-C", null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request.create(valid.request.getPlan(),
            "target-C", "application-C", new AliasingStream("alias", system(2.0))));
  }

  @Test
  void testMismatchedObservedIdentityFailsCompleteBatch() {
    RequestFixture valid = fixture("target-A", "application-A", 2.0, true);
    RequestFixture mismatched = fixture("target-B", "application-B", 3.0, false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request wrong = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request
        .create(mismatched.request.getPlan(), "target-C", "application-B", mismatched.callerStream);

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Arrays.asList(valid.request, wrong)));
  }

  @Test
  void testDeterministicEvidenceAndSerializationRoundTrip() throws Exception {
    RequestFixture firstRequest = fixture("target-A", "application-A", 2.0, true);
    RequestFixture secondRequest = fixture("target-B", "application-B", 5.0, false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result first = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Arrays.asList(firstRequest.request, secondRequest.request));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result second = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview
        .applyToClones(Arrays.asList(firstRequest.request, secondRequest.request));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result restored = serializeRoundTrip(first);

    assertEquals(Double.doubleToLongBits(first.getPlannedS8IncrementMol()),
        Double.doubleToLongBits(second.getPlannedS8IncrementMol()));
    assertEquals(Double.doubleToLongBits(first.getObservedS8IncrementMol()),
        Double.doubleToLongBits(restored.getObservedS8IncrementMol()));
    assertEquals(first.getPreviews().get(0).getApplicationReceipt().getTransitionDigestHex(),
        restored.getPreviews().get(0).getApplicationReceipt().getTransitionDigestHex());
  }

  private static RequestFixture fixture(String targetIdentifier, String applicationKey, double priorS8AmountMol,
      boolean append) {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = plan(targetIdentifier, applicationKey,
        priorS8AmountMol, append);
    StreamInterface callerStream = stream("prior-" + targetIdentifier, priorS8AmountMol);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request request = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request
        .create(plan, targetIdentifier, applicationKey, callerStream);
    return new RequestFixture(request, callerStream);
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
    return new Stream(name, system(s8AmountMol));
  }

  private static SystemInterface system(double s8AmountMol) {
    SystemInterface system = new SystemSrkEos(298.15, 80.0);
    system.addComponent("methane", 10.0);
    system.addComponent("CO2", 3.0);
    system.addComponent("S8", s8AmountMol);
    system.init(0);
    return system;
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

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result result) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result) input.readObject();
    }
  }

  private static final class RequestFixture {
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request request;
    private final StreamInterface callerStream;

    private RequestFixture(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request request,
        StreamInterface callerStream) {
      this.request = request;
      this.callerStream = callerStream;
    }
  }

  private static final class AliasingStream extends Stream {
    private static final long serialVersionUID = 1000L;

    private AliasingStream(String name, SystemInterface fluid) {
      super(name, fluid);
    }

    @Override
    public AliasingStream clone() {
      return this;
    }
  }
}
