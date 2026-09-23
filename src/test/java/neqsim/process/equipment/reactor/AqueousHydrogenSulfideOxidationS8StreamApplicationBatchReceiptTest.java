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

/** Tests fail-closed batch verification of externally applied S8 stream additions. */
public class AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceiptTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";

  @Test
  void testOrderedBatchClosesAppendAndUnchangedEntries() {
    RequestFixture append = fixture("target-A", "application-A", 2.0, true);
    RequestFixture unchanged = fixture("target-B", "application-B", 5.0, false);

    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result result = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
        .verify(Arrays.asList(append.request, unchanged.request));

    assertEquals(2, result.getReceipts().size());
    assertEquals("target-A", result.getReceipts().get(0).getTargetStateIdentifier());
    assertEquals("target-B", result.getReceipts().get(1).getTargetStateIdentifier());
    assertEquals(1, result.getStrictAppendCount());
    assertEquals(1, result.getUnchangedCount());
    assertEquals(4, result.getPreservedNonS8ComponentCount());
    assertEquals(result.getPlannedS8IncrementMol(), result.getObservedS8IncrementMol(),
        result.getS8ClosureToleranceMol());
    assertEquals(result.getObservedS8IncrementMol(), result.getObservedTotalIncrementMol(),
        result.getTotalAmountClosureToleranceMol());
    assertTrue(Math.abs(result.getS8ClosureResidualMol()) <= result.getS8ClosureToleranceMol());
    assertTrue(Math.abs(result.getTotalAmountClosureResidualMol()) <= result.getTotalAmountClosureToleranceMol());
  }

  @Test
  void testCallerStreamsAndCapturedSnapshotsRemainIndependent() {
    RequestFixture append = fixture("target-A", "application-A", 2.0, true);
    double priorTotal = append.priorStream.getFluid().getTotalNumberOfMoles();
    double candidateTotal = append.candidateStream.getFluid().getTotalNumberOfMoles();

    StreamInterface storedPrior = append.request.getPriorStream();
    storedPrior.getFluid().addComponent("methane", 1.0);
    storedPrior.getFluid().init(0);
    StreamInterface storedCandidate = append.request.getCandidateStream();
    storedCandidate.getFluid().addComponent("CO2", 1.0);
    storedCandidate.getFluid().init(0);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result result = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
        .verify(Collections.singletonList(append.request));

    assertEquals(priorTotal, append.priorStream.getFluid().getTotalNumberOfMoles(), 0.0);
    assertEquals(candidateTotal, append.candidateStream.getFluid().getTotalNumberOfMoles(), 0.0);
    assertNotSame(storedPrior, append.request.getPriorStream());
    assertNotSame(storedCandidate, append.request.getCandidateStream());
    assertThrows(UnsupportedOperationException.class, () -> result.getReceipts().clear());
  }

  @Test
  void testDuplicateTargetAndApplicationIdentitiesFailClosed() {
    RequestFixture first = fixture("target-A", "application-A", 2.0, true);
    RequestFixture duplicateTarget = fixture("target-A", "application-B", 3.0, false);
    RequestFixture duplicateKey = fixture("target-B", "application-A", 4.0, false);
    double firstCandidateTotal = first.candidateStream.getFluid().getTotalNumberOfMoles();

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
            .verify(Arrays.asList(first.request, duplicateTarget.request)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
            .verify(Arrays.asList(first.request, duplicateKey.request)));
    assertEquals(firstCandidateTotal, first.candidateStream.getFluid().getTotalNumberOfMoles(), 0.0);
  }

  @Test
  void testWrongIdentityAndInventoryChangesFailCompleteBatch() {
    RequestFixture valid = fixture("target-A", "application-A", 2.0, true);
    RequestFixture wrongIdentity = fixture("target-B", "application-B", 3.0, true);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request wrong = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request
        .create(wrongIdentity.request.getPlan(), "target-C", "application-B", wrongIdentity.priorStream,
            wrongIdentity.candidateStream);

    StreamInterface contaminatedCandidate = valid.candidateStream.clone();
    contaminatedCandidate.getFluid().addComponent("methane", 0.25);
    contaminatedCandidate.getFluid().init(0);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request contaminated = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request
        .create(valid.request.getPlan(), "target-A", "application-A", valid.priorStream, contaminatedCandidate);

    StreamInterface wrongS8Candidate = valid.candidateStream.clone();
    wrongS8Candidate.getFluid().addComponent("S8", 0.25);
    wrongS8Candidate.getFluid().init(0);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request wrongS8 = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request
        .create(valid.request.getPlan(), "target-A", "application-A", valid.priorStream, wrongS8Candidate);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
            .verify(Arrays.asList(valid.request, wrong)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
            .verify(Collections.singletonList(contaminated)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
            .verify(Collections.singletonList(wrongS8)));
  }

  @Test
  void testNullAliasAndCloneFailuresAreRejected() {
    RequestFixture valid = fixture("target-A", "application-A", 2.0, true);
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.verify(null));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.verify(
            Collections.<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request>emptyList()));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
            .verify(Arrays.asList(valid.request, null)));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request.create(
            valid.request.getPlan(), "target-A", "application-A", valid.priorStream, valid.priorStream));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request.create(
            valid.request.getPlan(), "target-A", "application-A", null, valid.candidateStream));

    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result unchangedPlan = plan("target-U",
        "application-U", 2.0, false);
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request.create(unchangedPlan,
            "target-U", "application-U", new AliasingStream("alias", system(2.0)), stream("candidate", 2.0)));
  }

  @Test
  void testDeterministicEvidenceAndSerializationRoundTrip() throws Exception {
    RequestFixture firstRequest = fixture("target-A", "application-A", 2.0, true);
    RequestFixture secondRequest = fixture("target-B", "application-B", 5.0, false);
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result first = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
        .verify(Arrays.asList(firstRequest.request, secondRequest.request));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result second = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt
        .verify(Arrays.asList(firstRequest.request, secondRequest.request));
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result restored = serializeRoundTrip(first);

    assertEquals(Double.doubleToLongBits(first.getPlannedS8IncrementMol()),
        Double.doubleToLongBits(second.getPlannedS8IncrementMol()));
    assertEquals(Double.doubleToLongBits(first.getObservedTotalIncrementMol()),
        Double.doubleToLongBits(restored.getObservedTotalIncrementMol()));
    assertEquals(first.getReceipts().get(0).getTransitionDigestHex(),
        restored.getReceipts().get(0).getTransitionDigestHex());
  }

  private static RequestFixture fixture(String targetIdentifier, String applicationKey, double priorS8AmountMol,
      boolean append) {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = plan(targetIdentifier, applicationKey,
        priorS8AmountMol, append);
    StreamInterface priorStream = stream("prior-" + targetIdentifier, priorS8AmountMol);
    StreamInterface candidateStream = priorStream.clone();
    if (plan.requiresMutation()) {
      candidateStream.getFluid().addComponent("S8", plan.getTransferredS8AmountMol());
      candidateStream.getFluid().init(0);
    }
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request request = AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request
        .create(plan, targetIdentifier, applicationKey, priorStream, candidateStream);
    return new RequestFixture(request, priorStream, candidateStream);
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan(String targetIdentifier,
      String applicationKey, double priorS8AmountMol, boolean append) {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = append
        ? ledger(batch(4.0, 0.25, "batch-0", "segment-0"), batch(6.0, 0.50, "batch-1", "segment-1"))
        : prior;
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    return AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate, transition,
        targetIdentifier, applicationKey, priorS8AmountMol);
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

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result result) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result) input.readObject();
    }
  }

  private static final class RequestFixture {
    private final AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request request;
    private final StreamInterface priorStream;
    private final StreamInterface candidateStream;

    private RequestFixture(AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request request,
        StreamInterface priorStream, StreamInterface candidateStream) {
      this.request = request;
      this.priorStream = priorStream;
      this.candidateStream = candidateStream;
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
