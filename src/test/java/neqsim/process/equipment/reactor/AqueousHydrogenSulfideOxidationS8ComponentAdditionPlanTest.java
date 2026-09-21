package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Tests target-scoped plans for verified S8 component additions. */
public class AqueousHydrogenSulfideOxidationS8ComponentAdditionPlanTest
    extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";
  private static final String TARGET_IDENTIFIER = "target-state-A";
  private static final String APPLICATION_KEY = "application-A";

  @Test
  void testUnchangedTransitionPreservesExactPriorAmount() {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior =
        ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition =
        AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, prior);

    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan =
        AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, prior, transition,
            TARGET_IDENTIFIER, APPLICATION_KEY, 12.5);

    assertTrue(plan.isUnchanged());
    assertFalse(plan.isStrictAppend());
    assertFalse(plan.requiresMutation());
    assertEquals(12.5, plan.getPriorS8AmountMol(), 0.0);
    assertEquals(0.0, plan.getTransferredS8AmountMol(), 0.0);
    assertEquals(12.5, plan.getCandidateS8AmountMol(), 0.0);
    assertEquals(0.0, plan.getReconstructedIncrementMol(), 0.0);
    assertEquals(0.0, plan.getAmountClosureResidualMol(), 0.0);
  }

  @Test
  void testStrictAppendProducesClosedTargetAmountPlan() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first =
        batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result second =
        batch(6.0, 0.50, "batch-1", "segment-1");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first, second);
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition =
        AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, candidate);

    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan =
        AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, TARGET_IDENTIFIER, APPLICATION_KEY, 2.0);

    assertTrue(plan.isStrictAppend());
    assertTrue(plan.requiresMutation());
    assertEquals("S8", plan.getComponentName());
    assertEquals("NeqSim-COMP.csv-S8-256.48-g-per-mol",
        plan.getMolecularWeightBasisIdentifier());
    assertEquals(TARGET_IDENTIFIER, plan.getTargetStateIdentifier());
    assertEquals(APPLICATION_KEY, plan.getApplicationIdempotencyKey());
    assertEquals(2.0 + plan.getTransferredS8AmountMol(), plan.getCandidateS8AmountMol(), 0.0);
    assertEquals(plan.getCandidateS8AmountMol() - plan.getPriorS8AmountMol(),
        plan.getReconstructedIncrementMol(), 0.0);
    assertTrue(Math.abs(plan.getAmountClosureResidualMol())
        <= 8.0 * Math.ulp(plan.getCandidateS8AmountMol()));
    assertEquals(transition.getTransitionDigestHex(), plan.getTransitionDigestHex());
  }

  @Test
  void testMismatchedTransitionAndInvalidInputsFailClosed() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first =
        batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate =
        ledger(first, batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result replacement =
        ledger(first, batch(6.0, 0.60, "batch-1", "replacement-key"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition =
        AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, candidate);
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result replacementTransition =
        AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, replacement);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            replacementTransition, TARGET_IDENTIFIER, APPLICATION_KEY, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, " target-state-A", APPLICATION_KEY, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, TARGET_IDENTIFIER, "", 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, TARGET_IDENTIFIER, APPLICATION_KEY, -1.0));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, TARGET_IDENTIFIER, APPLICATION_KEY, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, TARGET_IDENTIFIER, APPLICATION_KEY, Double.POSITIVE_INFINITY));
  }

  @Test
  void testPositiveIncrementLostAtTargetScaleFailsClosed() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first =
        batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate =
        ledger(first, batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition =
        AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, candidate);

    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, TARGET_IDENTIFIER, APPLICATION_KEY, 1.0e300));
  }

  @Test
  void testDeterminismAndSerializationPreserveBitwiseEvidence() throws Exception {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first =
        batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate =
        ledger(first, batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition =
        AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, candidate);
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result firstPlan =
        AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, TARGET_IDENTIFIER, APPLICATION_KEY, 2.0);
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result secondPlan =
        AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
            transition, TARGET_IDENTIFIER, APPLICATION_KEY, 2.0);
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result restored =
        serializeRoundTrip(firstPlan);

    assertEquals(firstPlan.getTransitionDigestHex(), secondPlan.getTransitionDigestHex());
    assertEquals(Double.doubleToLongBits(firstPlan.getCandidateS8AmountMol()),
        Double.doubleToLongBits(secondPlan.getCandidateS8AmountMol()));
    assertEquals(firstPlan.getLedgerIdentifier(), restored.getLedgerIdentifier());
    assertEquals(firstPlan.getProductIdentityBasisIdentifier(),
        restored.getProductIdentityBasisIdentifier());
    assertEquals(firstPlan.getTargetStateIdentifier(), restored.getTargetStateIdentifier());
    assertEquals(firstPlan.getApplicationIdempotencyKey(),
        restored.getApplicationIdempotencyKey());
    assertEquals(firstPlan.getPriorCheckpointHex(), restored.getPriorCheckpointHex());
    assertEquals(firstPlan.getCandidateCheckpointHex(), restored.getCandidateCheckpointHex());
    assertEquals(firstPlan.getTransitionDigestHex(), restored.getTransitionDigestHex());
    assertEquals(Double.doubleToLongBits(firstPlan.getTransferredS8AmountMol()),
        Double.doubleToLongBits(restored.getTransferredS8AmountMol()));
    assertEquals(Double.doubleToLongBits(firstPlan.getAmountClosureResidualMol()),
        Double.doubleToLongBits(restored.getAmountClosureResidualMol()));
  }

  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger(
      AqueousHydrogenSulfideOxidationS8TransferBatch.Result... batches) {
    return AqueousHydrogenSulfideOxidationS8TransferLedger.create(Arrays.asList(batches),
        "ledger-A");
  }

  private static AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch(
      double durationHours, double allocationFraction, String batchIdentifier,
      String idempotencyKey) {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment =
        new AqueousHydrogenSulfideOxidationTrajectory.Segment(durationHours, 298.15, 8.0, 0.723,
            250.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult =
        AqueousHydrogenSulfideOxidationTrajectory
            .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment))
            .getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation =
        AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(
            AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult,
                WATER_INVENTORY_KG),
            allocationFraction, ALLOCATION_BASIS);
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer =
        AqueousHydrogenSulfideOxidationS8Transfer.create(allocation,
            AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL, PRODUCT_BASIS,
            idempotencyKey);
    return AqueousHydrogenSulfideOxidationS8TransferBatch
        .create(Collections.singletonList(transfer), batchIdentifier);
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(plan);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result) input.readObject();
    }
  }
}
