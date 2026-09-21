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
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests non-mutating verification of externally applied S8 component additions. */
public class AqueousHydrogenSulfideOxidationS8ComponentApplicationReceiptTest
    extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final double PRIOR_S8_AMOUNT_MOL = 2.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";
  private static final String TARGET_IDENTIFIER = "target-state-A";
  private static final String APPLICATION_KEY = "application-A";

  @Test
  void testStrictAppendVerifiesExternalApplicationWithoutMutation() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    SystemInterface prior = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL, false);
    SystemInterface candidate = prior.clone();
    candidate.addComponent("S8", plan.getTransferredS8AmountMol());
    candidate.init(0);
    double priorTotalBefore = prior.getTotalNumberOfMoles();
    double candidateTotalBefore = candidate.getTotalNumberOfMoles();

    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt = AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior, candidate);

    assertTrue(receipt.isStrictAppend());
    assertEquals("S8", receipt.getComponentName());
    assertEquals(2, receipt.getPreservedNonS8ComponentCount());
    assertEquals(PRIOR_S8_AMOUNT_MOL, receipt.getPriorS8AmountMol(), 0.0);
    assertEquals(plan.getTransferredS8AmountMol(), receipt.getObservedS8IncrementMol(),
        8.0 * Math.ulp(plan.getCandidateS8AmountMol()));
    assertTrue(Math.abs(receipt.getPlanApplicationResidualMol())
        <= 8.0 * Math.ulp(plan.getCandidateS8AmountMol()));
    assertEquals(0.0, receipt.getMaximumNonS8InventoryResidualMol(), 0.0);
    assertEquals(priorTotalBefore, prior.getTotalNumberOfMoles(), 0.0);
    assertEquals(candidateTotalBefore, candidate.getTotalNumberOfMoles(), 0.0);
  }

  @Test
  void testUnchangedPlanAcceptsExactSameInventory() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = unchangedPlan();
    SystemInterface target = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL, false);

    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt = AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, target, target);

    assertTrue(receipt.isUnchanged());
    assertEquals(0.0, receipt.getPlannedS8IncrementMol(), 0.0);
    assertEquals(0.0, receipt.getObservedS8IncrementMol(), 0.0);
    assertEquals(0.0, receipt.getTotalAmountClosureResidualMol(), 0.0);
  }

  @Test
  void testReorderedComponentInventoryIsAccepted() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    SystemInterface prior = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL, false);
    SystemInterface reorderedCandidate = system(10.0, 3.0, plan.getCandidateS8AmountMol(), true);

    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt = AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior, reorderedCandidate);

    assertEquals(2, receipt.getPreservedNonS8ComponentCount());
    assertEquals(0.0, receipt.getMaximumNonS8InventoryResidualMol(), 0.0);
  }

  @Test
  void testWrongIdentityAndInventoryChangesFailClosed() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    SystemInterface prior = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL, false);
    SystemInterface candidate = system(10.0, 3.0, plan.getCandidateS8AmountMol(), false);
    SystemInterface doubleApplied = system(10.0, 3.0,
        PRIOR_S8_AMOUNT_MOL + 2.0 * plan.getTransferredS8AmountMol(), false);
    SystemInterface contaminated = system(10.5, 3.0, plan.getCandidateS8AmountMol(), false);
    SystemInterface removedComponent = system(10.0, 0.0, plan.getCandidateS8AmountMol(), false);

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, "wrong-target", APPLICATION_KEY, prior, candidate));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, "wrong-key", prior, candidate));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior, doubleApplied));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior, contaminated));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior, removedComponent));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY,
            system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL - 0.5, false), candidate));
  }

  @Test
  void testPositiveApplicationRejectsAliasedBeforeAndAfterSystem() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    SystemInterface candidate = system(10.0, 3.0, plan.getCandidateS8AmountMol(), false);

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, candidate, candidate));
  }

  @Test
  void testReceiptDeterminismAndSerializationPreserveBitwiseEvidence() throws Exception {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    SystemInterface prior = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL, false);
    SystemInterface candidate = system(10.0, 3.0, plan.getCandidateS8AmountMol(), true);
    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result first = AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior, candidate);
    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result second = AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt
        .verify(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior, candidate);
    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result restored = serializeRoundTrip(first);

    assertEquals(first.getTransitionDigestHex(), second.getTransitionDigestHex());
    assertEquals(Double.doubleToLongBits(first.getObservedS8IncrementMol()),
        Double.doubleToLongBits(second.getObservedS8IncrementMol()));
    assertEquals(first.getLedgerIdentifier(), restored.getLedgerIdentifier());
    assertEquals(first.getProductIdentityBasisIdentifier(), restored.getProductIdentityBasisIdentifier());
    assertEquals(first.getMolecularWeightBasisIdentifier(), restored.getMolecularWeightBasisIdentifier());
    assertEquals(first.getTargetStateIdentifier(), restored.getTargetStateIdentifier());
    assertEquals(first.getApplicationIdempotencyKey(), restored.getApplicationIdempotencyKey());
    assertEquals(first.getPriorCheckpointHex(), restored.getPriorCheckpointHex());
    assertEquals(first.getCandidateCheckpointHex(), restored.getCandidateCheckpointHex());
    assertEquals(first.getTransitionDigestHex(), restored.getTransitionDigestHex());
    assertEquals(Double.doubleToLongBits(first.getPlanApplicationResidualMol()),
        Double.doubleToLongBits(restored.getPlanApplicationResidualMol()));
    assertEquals(Double.doubleToLongBits(first.getTotalAmountClosureResidualMol()),
        Double.doubleToLongBits(restored.getTotalAmountClosureResidualMol()));
  }

  private static SystemInterface system(double methaneAmountMol, double carbonDioxideAmountMol,
      double s8AmountMol, boolean reverseOrder) {
    SystemInterface system = new SystemSrkEos(298.15, 80.0);
    if (reverseOrder) {
      system.addComponent("CO2", carbonDioxideAmountMol);
      system.addComponent("S8", s8AmountMol);
      system.addComponent("methane", methaneAmountMol);
    } else {
      system.addComponent("methane", methaneAmountMol);
      if (carbonDioxideAmountMol > 0.0) {
        system.addComponent("CO2", carbonDioxideAmountMol);
      }
      system.addComponent("S8", s8AmountMol);
    }
    system.init(0);
    return system;
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result strictAppendPlan() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first,
        batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    return AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate,
        transition, TARGET_IDENTIFIER, APPLICATION_KEY, PRIOR_S8_AMOUNT_MOL);
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result unchangedPlan() {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(batch(4.0, 0.25,
        "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, prior);
    return AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, prior, transition,
        TARGET_IDENTIFIER, APPLICATION_KEY, PRIOR_S8_AMOUNT_MOL);
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
        .advance(INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment))
        .getSegmentResults().get(0);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation = AqueousHydrogenSulfideOxidationElementalSulfurAllocation
        .allocate(AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(segmentResult,
            WATER_INVENTORY_KG), allocationFraction, ALLOCATION_BASIS);
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer = AqueousHydrogenSulfideOxidationS8Transfer
        .create(allocation, AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL,
            PRODUCT_BASIS, idempotencyKey);
    return AqueousHydrogenSulfideOxidationS8TransferBatch.create(
        Collections.singletonList(transfer), batchIdentifier);
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(receipt);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result) input.readObject();
    }
  }
}
