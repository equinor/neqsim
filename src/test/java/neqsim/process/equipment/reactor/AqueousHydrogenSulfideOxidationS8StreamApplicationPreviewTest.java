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

/** Tests detached stream preview application of verified S8 component-addition plans. */
public class AqueousHydrogenSulfideOxidationS8StreamApplicationPreviewTest extends NeqSimTest {
  private static final double INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6;
  private static final double WATER_INVENTORY_KG = 1200.0;
  private static final double PRIOR_S8_AMOUNT_MOL = 2.0;
  private static final String ALLOCATION_BASIS = "caller-allocation-case-A";
  private static final String PRODUCT_BASIS = "caller-S8-identity-case-A";
  private static final String TARGET_IDENTIFIER = "stream-state-A";
  private static final String APPLICATION_KEY = "stream-application-A";

  @Test
  void testStrictAppendChangesOnlyDetachedCandidateStream() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    StreamInterface prior = stream("prior", PRIOR_S8_AMOUNT_MOL);
    SystemInterface priorFluid = prior.getFluid();
    double priorTotalBefore = priorFluid.getTotalNumberOfMoles();
    long priorS8Bits = Double.doubleToLongBits(componentAmount(priorFluid, "S8"));

    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result preview = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior);
    StreamInterface candidate = preview.getCandidateStream();

    assertNotSame(prior, candidate);
    assertNotSame(priorFluid, candidate.getFluid());
    assertEquals(priorTotalBefore, prior.getFluid().getTotalNumberOfMoles(), 0.0);
    assertEquals(priorS8Bits, Double.doubleToLongBits(componentAmount(prior.getFluid(), "S8")));
    assertEquals(plan.getCandidateS8AmountMol(), componentAmount(candidate.getFluid(), "S8"),
        8.0 * Math.ulp(plan.getCandidateS8AmountMol()));
    assertTrue(preview.getApplicationReceipt().isStrictAppend());
    assertEquals(0.0, preview.getApplicationReceipt().getMaximumNonS8InventoryResidualMol(), 0.0);
  }

  @Test
  void testUnchangedTransitionPreservesExactDetachedInventory() {
    StreamInterface prior = stream("prior", PRIOR_S8_AMOUNT_MOL);
    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result preview = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(unchangedPlan(), TARGET_IDENTIFIER, APPLICATION_KEY, prior);
    StreamInterface candidate = preview.getCandidateStream();

    assertTrue(preview.getApplicationReceipt().isUnchanged());
    assertNotSame(prior, candidate);
    assertNotSame(prior.getFluid(), candidate.getFluid());
    assertEquals(Double.doubleToLongBits(prior.getFluid().getTotalNumberOfMoles()),
        Double.doubleToLongBits(candidate.getFluid().getTotalNumberOfMoles()));
    assertEquals(Double.doubleToLongBits(componentAmount(prior.getFluid(), "S8")),
        Double.doubleToLongBits(componentAmount(candidate.getFluid(), "S8")));
  }

  @Test
  void testCandidateGetterReturnsDefensiveStreamAndFluidClones() {
    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result preview = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(strictAppendPlan(), TARGET_IDENTIFIER, APPLICATION_KEY, stream("prior", PRIOR_S8_AMOUNT_MOL));
    StreamInterface first = preview.getCandidateStream();
    double methaneBefore = componentAmount(first.getFluid(), "methane");

    first.setName("mutated-candidate");
    first.getFluid().addComponent("methane", 1.0);
    first.getFluid().init(0);
    StreamInterface second = preview.getCandidateStream();

    assertNotSame(first, second);
    assertNotSame(first.getFluid(), second.getFluid());
    assertEquals("prior", second.getName());
    assertEquals(methaneBefore, componentAmount(second.getFluid(), "methane"), 0.0);
  }

  @Test
  void testInvalidIdentityPriorAndCloneFailWithoutMutatingInput() {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    StreamInterface prior = stream("prior", PRIOR_S8_AMOUNT_MOL);
    double priorTotalBefore = prior.getFluid().getTotalNumberOfMoles();

    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(plan, "wrong-target", APPLICATION_KEY, prior));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(plan, TARGET_IDENTIFIER, "wrong-key", prior));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(plan, TARGET_IDENTIFIER, APPLICATION_KEY, stream("wrong-prior", PRIOR_S8_AMOUNT_MOL - 0.5)));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(plan, TARGET_IDENTIFIER, APPLICATION_KEY, null));
    assertThrows(IllegalArgumentException.class, () -> AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(plan, TARGET_IDENTIFIER, APPLICATION_KEY, new Stream("empty")));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.applyToClone(plan, TARGET_IDENTIFIER,
            APPLICATION_KEY, new AliasingStream("alias", system(PRIOR_S8_AMOUNT_MOL))));
    assertThrows(IllegalArgumentException.class,
        () -> AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.applyToClone(plan, TARGET_IDENTIFIER,
            APPLICATION_KEY, new ShallowFluidStream("shallow", system(PRIOR_S8_AMOUNT_MOL))));

    assertEquals(priorTotalBefore, prior.getFluid().getTotalNumberOfMoles(), 0.0);
  }

  @Test
  void testDeterminismAndSerializationPreserveEvidenceAndCandidate() throws Exception {
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan = strictAppendPlan();
    StreamInterface prior = stream("prior", PRIOR_S8_AMOUNT_MOL);
    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result first = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior);
    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result second = AqueousHydrogenSulfideOxidationS8StreamApplicationPreview
        .applyToClone(plan, TARGET_IDENTIFIER, APPLICATION_KEY, prior);
    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result restored = serializeRoundTrip(first);

    assertEquals(first.getApplicationReceipt().getTransitionDigestHex(),
        second.getApplicationReceipt().getTransitionDigestHex());
    assertEquals(first.getApplicationReceipt().getTransitionDigestHex(),
        restored.getApplicationReceipt().getTransitionDigestHex());
    assertEquals(Double.doubleToLongBits(componentAmount(first.getCandidateStream().getFluid(), "S8")),
        Double.doubleToLongBits(componentAmount(restored.getCandidateStream().getFluid(), "S8")));
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

  private static double componentAmount(SystemInterface system, String componentName) {
    for (int componentIndex = 0; componentIndex < system.getNumberOfComponents(); componentIndex++) {
      if (componentName.equals(system.getComponent(componentIndex).getName())) {
        return system.getComponent(componentIndex).getNumberOfmoles();
      }
    }
    return 0.0;
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result strictAppendPlan() {
    AqueousHydrogenSulfideOxidationS8TransferBatch.Result first = batch(4.0, 0.25, "batch-0", "segment-0");
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(first);
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ledger(first,
        batch(6.0, 0.50, "batch-1", "segment-1"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, candidate);
    return AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, candidate, transition,
        TARGET_IDENTIFIER, APPLICATION_KEY, PRIOR_S8_AMOUNT_MOL);
  }

  private static AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result unchangedPlan() {
    AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ledger(batch(4.0, 0.25, "batch-0", "segment-0"));
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition = AqueousHydrogenSulfideOxidationS8TransferLedgerTransition
        .create(prior, prior);
    return AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(prior, prior, transition, TARGET_IDENTIFIER,
        APPLICATION_KEY, PRIOR_S8_AMOUNT_MOL);
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

  private static AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result serializeRoundTrip(
      AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result preview) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(preview);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result) input.readObject();
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

  private static final class ShallowFluidStream extends Stream {
    private static final long serialVersionUID = 1000L;

    private ShallowFluidStream(String name, SystemInterface fluid) {
      super(name, fluid);
    }

    @Override
    public ShallowFluidStream clone() {
      return new ShallowFluidStream(getName(), getFluid());
    }
  }
}
