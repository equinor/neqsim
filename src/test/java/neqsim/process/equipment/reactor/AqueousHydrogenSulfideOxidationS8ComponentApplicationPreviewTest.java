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
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests defensive preview application of verified S8 component-addition plans. */
public class AqueousHydrogenSulfideOxidationS8ComponentApplicationPreviewTest
    extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final double PRIOR_S8_AMOUNT_MOL = 2.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";
  private static final String TARGET_IDENTIFIER = "target-state-A";
  private static final String APPLICATION_KEY = "application-A";

  @Test
  void testStrictAppendPreviewMutatesOnlyIndependentCandidate() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    SystemInterface prior = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL);
    double priorTotalBefore = prior.getTotalNumberOfMoles();
    long priorS8Bits = Double.doubleToLongBits(componentAmount(prior, "S8"));

    AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result preview =
        AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
            plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior);
    SystemInterface candidate = preview.getCandidateTarget();

    assertNotSame(prior, candidate);
    assertEquals(priorTotalBefore, prior.getTotalNumberOfMoles(), 0.0);
    assertEquals(priorS8Bits, Double.doubleToLongBits(componentAmount(prior, "S8")));
    assertEquals(
        plan.getCandidateS8AmountMol(),
        componentAmount(candidate, "S8"),
        8.0 * Math.ulp(plan.getCandidateS8AmountMol()));
    assertTrue(preview.getApplicationReceipt().isStrictAppend());
    assertEquals(0.0, preview.getApplicationReceipt().getMaximumNonS8InventoryResidualMol(), 0.0);
  }

  @Test
  void testUnchangedPreviewPreservesExactInventory() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = unchangedPlan();
    SystemInterface prior = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL);

    AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result preview =
        AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
            plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior);
    SystemInterface candidate = preview.getCandidateTarget();

    assertTrue(preview.getApplicationReceipt().isUnchanged());
    assertEquals(
        Double.doubleToLongBits(prior.getTotalNumberOfMoles()),
        Double.doubleToLongBits(candidate.getTotalNumberOfMoles()));
    assertEquals(
        Double.doubleToLongBits(componentAmount(prior, "methane")),
        Double.doubleToLongBits(componentAmount(candidate, "methane")));
    assertEquals(
        Double.doubleToLongBits(componentAmount(prior, "CO2")),
        Double.doubleToLongBits(componentAmount(candidate, "CO2")));
    assertEquals(
        Double.doubleToLongBits(componentAmount(prior, "S8")),
        Double.doubleToLongBits(componentAmount(candidate, "S8")));
  }

  @Test
  void testCandidateGetterReturnsDefensiveClones() {
    AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result preview =
        AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
            strictAppendPlan(),
            TARGET_IDENTIFIER,
            APPLICATION_KEY,
            system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL));
    SystemInterface first = preview.getCandidateTarget();
    double methaneBefore = componentAmount(first, "methane");

    first.addComponent("methane", 1.0);
    first.init(0);
    SystemInterface second = preview.getCandidateTarget();

    assertNotSame(first, second);
    assertEquals(methaneBefore, componentAmount(second, "methane"), 0.0);
  }

  @Test
  void testInvalidIdentityPriorAndCloneFailWithoutMutatingInput() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    SystemInterface prior = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL);
    double priorTotalBefore = prior.getTotalNumberOfMoles();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
                plan, "wrong-target", APPLICATION_KEY, prior));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
                plan, TARGET_IDENTIFIER, "wrong-key", prior));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
                plan,
                TARGET_IDENTIFIER,
                APPLICATION_KEY,
                system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL - 0.5)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
                plan, TARGET_IDENTIFIER, APPLICATION_KEY, null));

    AliasingSystem aliasing = new AliasingSystem();
    aliasing.addComponent("methane", 10.0);
    aliasing.addComponent("CO2", 3.0);
    aliasing.addComponent("S8", PRIOR_S8_AMOUNT_MOL);
    aliasing.init(0);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
                plan, TARGET_IDENTIFIER, APPLICATION_KEY, aliasing));

    assertEquals(priorTotalBefore, prior.getTotalNumberOfMoles(), 0.0);
  }

  @Test
  void testPreviewDeterminismAndSerializationPreserveEvidenceAndCandidate() throws Exception {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    SystemInterface prior = system(10.0, 3.0, PRIOR_S8_AMOUNT_MOL);
    AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result first =
        AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
            plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior);
    AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result second =
        AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
            plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior);
    AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result restored =
        serializeRoundTrip(first);

    assertEquals(
        Double.doubleToLongBits(first.getApplicationReceipt().getObservedS8IncrementMol()),
        Double.doubleToLongBits(second.getApplicationReceipt().getObservedS8IncrementMol()));
    assertEquals(
        first.getApplicationReceipt().getTransitionDigestHex(),
        restored.getApplicationReceipt().getTransitionDigestHex());
    assertEquals(
        Double.doubleToLongBits(componentAmount(first.getCandidateTarget(), "S8")),
        Double.doubleToLongBits(componentAmount(restored.getCandidateTarget(), "S8")));
  }

  private static SystemInterface system(
      double methaneAmountMol, double carbonDioxideAmountMol, double s8AmountMol) {
    SystemInterface system = new SystemSrkEos(298.15, 80.0);
    system.addComponent("methane", methaneAmountMol);
    system.addComponent("CO2", carbonDioxideAmountMol);
    system.addComponent("S8", s8AmountMol);
    system.init(0);
    return system;
  }

  private static double componentAmount(SystemInterface system, String componentName) {
    for (int componentIndex = 0;
        componentIndex < system.getNumberOfComponents();
        componentIndex++) {
      if (componentName.equals(system.getComponent(componentIndex).getName())) {
        return system.getComponent(componentIndex).getNumberOfmoles();
      }
    }
    return 0.0;
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result
      strictAppendPlan() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first =
        batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate =
        ledger(first, batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition =
        AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, candidate);
    return AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(
        prior, candidate, transition, TARGET_IDENTIFIER, APPLICATION_KEY, PRIOR_S8_AMOUNT_MOL);
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result unchangedPlan() {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior =
        ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition =
        AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, prior);
    return AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(
        prior, prior, transition, TARGET_IDENTIFIER, APPLICATION_KEY, PRIOR_S8_AMOUNT_MOL);
  }

  private static AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger(
      AqueousHydrogenSulfideOxidationS8TransferBatch.Result... batches) {
    return AqueousHydrogenSulfideOxidationS8TransferLedger.create(
        Arrays.asList(batches), "ledger-A");
  }

  private static AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch(
      double durationHours,
      double allocationFraction,
      String batchIdentifier,
      String idempotencyKey) {
    AqueousHydrogenSulfideOxidationTrajectory.Segment segment =
        new AqueousHydrogenSulfideOxidationTrajectory.Segment(
            durationHours, 298.15, 8.0, 0.723, 250.0e-6);
    AqueousHydrogenSulfideOxidationTrajectory.SegmentResult segmentResult =
        AqueousHydrogenSulfideOxidationTrajectory.advance(
                INITIAL_TOTAL_SULFIDE_MOLALITY, Collections.singletonList(segment))
            .getSegmentResults()
            .get(0);
    AqueousHydrogenSulfideOxidationElementalSulfurAllocation.Result allocation =
        AqueousHydrogenSulfideOxidationElementalSulfurAllocation.allocate(
            AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(
                segmentResult, WATER_INVENTORY_KG),
            allocationFraction,
            ALLOCATION_BASIS);
    AqueousHydrogenSulfideOxidationS8Transfer.Result transfer =
        AqueousHydrogenSulfideOxidationS8Transfer.create(
            allocation,
            AqueousHydrogenSulfideOxidationS8Transfer.FitPath.NOMINAL,
            PRODUCT_BASIS,
            idempotencyKey);
    return AqueousHydrogenSulfideOxidationS8TransferBatch.create(
        Collections.singletonList(transfer), batchIdentifier);
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result
      serializeRoundTrip(
          AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result preview)
          throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(preview);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result)
          input.readObject();
    }
  }

  private static final class AliasingSystem extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;

    private AliasingSystem() {
      super(298.15, 80.0);
    }

    @Override
    public AliasingSystem clone() {
      return this;
    }
  }
}
