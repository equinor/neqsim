package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.Outcome;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.SliceResult;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.Transition;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessModelOperatingEnvelopeStudy}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModelOperatingEnvelopeStudyTest {
  /**
   * Controlled two-producer fixture with constraints whose utilization order changes.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class Fixture {
    private final Stream producerA;
    private final Stream producerB;
    private final ProcessModel model;

    /**
     * Creates the immutable fixture reference holder.
     *
     * @param producerA first producer
     * @param producerB second producer
     * @param model complete process model
     */
    private Fixture(Stream producerA, Stream producerB, ProcessModel model) {
      this.producerA = producerA;
      this.producerB = producerB;
      this.model = model;
    }
  }

  /**
   * Creates a synthetic two-producer model with one producer-local and one shared gathering limit.
   *
   * @return configured model fixture
   */
  private Fixture createFixture() {
    SystemInterface fluidA = new SystemSrkEos(298.15, 50.0);
    fluidA.addComponent("methane", 0.90);
    fluidA.addComponent("ethane", 0.10);
    fluidA.setMixingRule("classic");
    fluidA.setTotalFlowRate(600.0, "kg/hr");
    SystemInterface fluidB = fluidA.clone();
    fluidB.setTotalFlowRate(400.0, "kg/hr");

    Stream producerA = new Stream("producer A", fluidA);
    Stream producerB = new Stream("producer B", fluidB);
    ThrottlingValve chokeA = new ThrottlingValve("choke A", producerA);
    ThrottlingValve chokeB = new ThrottlingValve("choke B", producerB);
    chokeA.setOutletPressure(30.0, "bara");
    chokeB.setOutletPressure(30.0, "bara");
    Separator gatheringSink = new Separator("gathering sink", chokeA.getOutletStream());

    CapacityConstraint producerLimit = new CapacityConstraint("producer A rate", "kg/hr", ConstraintType.HARD)
        .setDesignValue(800.0).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic producer choke envelope").setConfidence(0.95)
        .setValidityRange(200.0, 1000.0).setValueSupplier(() -> producerA.getFlowRate("kg/hr"));
    CapacityConstraint sharedLimit = new CapacityConstraint("shared gathering rate", "kg/hr", ConstraintType.HARD)
        .setDesignValue(1200.0).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic shared manifold basis").setConfidence(0.95)
        .setValidityRange(400.0, 1600.0)
        .setValueSupplier(() -> producerA.getFlowRate("kg/hr") + producerB.getFlowRate("kg/hr"));
    gatheringSink.clearCapacityConstraints();
    gatheringSink.addCapacityConstraint(producerLimit);
    gatheringSink.addCapacityConstraint(sharedLimit);

    ProcessSystem wells = new ProcessSystem("wells");
    wells.add(producerA);
    wells.add(producerB);
    wells.add(chokeA);
    wells.add(chokeB);
    ProcessSystem gathering = new ProcessSystem("gathering");
    gathering.add(gatheringSink);
    ProcessModel model = new ProcessModel();
    model.add("wells", wells);
    model.add("gathering", gathering);
    model.run();
    return new Fixture(producerA, producerB, model);
  }

  /**
   * Creates the shared fail-closed action-set evaluator.
   *
   * @param fixture process fixture
   * @return configured evaluator
   */
  private ProcessModelOperatingActionSetEvaluator createEvaluator(Fixture fixture) {
    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("total production",
        model -> fixture.producerA.getFlowRate("kg/hr") + fixture.producerB.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);

    ProcessModelOperatingAction actionA = ProcessModelOperatingAction.continuous("producer-a-rate",
        "Producer A rate", "wells::producer A.flowRate", 200.0, 1000.0, "kg/hr",
        "synthetic producer A operating envelope");
    ProcessModelOperatingAction actionB = ProcessModelOperatingAction.continuous("producer-b-rate",
        "Producer B rate", "wells::producer B.flowRate", 200.0, 1000.0, "kg/hr",
        "synthetic producer B operating envelope");

    return new ProcessModelOperatingActionSetEvaluator("field-actions", "Field actions",
        "synthetic field-to-facility envelope basis", simulation, Arrays.asList(actionA, actionB))
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "gathering sink",
            "producer A rate", "synthetic producer choke envelope")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "gathering sink",
            "shared gathering rate", "synthetic shared manifold basis");
  }

  /**
   * Verifies a one-dimensional slice keeps infeasible points, detects bottleneck migration, and
   * restores the original process baseline after every candidate.
   *
   * @throws Exception if Java serialization unexpectedly fails
   */
  @Test
  void oneDimensionalSliceTracksBottleneckMigrationAndRestoresBaseline() throws Exception {
    Fixture fixture = createFixture();
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("well-a-slice",
        "Well A operating envelope", "synthetic field-to-facility acceptance", createEvaluator(fixture));

    SliceResult result = study.evaluateOneDimensional("producer-a-rate",
        new double[] {300.0, 500.0, 700.0, 900.0}, new double[] {600.0, 300.0});

    assertEquals(Outcome.COMPLETE, result.getOutcome());
    assertTrue(result.isComplete());
    assertEquals(4, result.getRequestedPointCount());
    assertEquals(4, result.getCompletedPointCount());
    assertEquals(3, result.getFeasiblePoints().size());
    assertTrue(result.getPoints().get(0).isFeasible());
    assertFalse(result.getPoints().get(3).isFeasible());
    assertEquals("gathering::gathering sink/shared gathering rate",
        result.getPoints().get(0).getLeadingInstalledConstraint().getQualifiedConstraintName());
    assertEquals("gathering::gathering sink/producer A rate",
        result.getPoints().get(2).getLeadingInstalledConstraint().getQualifiedConstraintName());
    assertEquals(1, result.getBottleneckTransitions().size());

    Transition transition = result.getBottleneckTransitions().get(0);
    assertEquals("producer-a-rate", transition.getAxisActionId());
    assertEquals(1, transition.getFromSequenceIndex());
    assertEquals(2, transition.getToSequenceIndex());
    assertEquals(500.0, transition.getFromAxisValue(), 0.0);
    assertEquals(700.0, transition.getToAxisValue(), 0.0);
    assertEquals("gathering::gathering sink/shared gathering rate", transition.getFromConstraint());
    assertEquals("gathering::gathering sink/producer A rate", transition.getToConstraint());

    assertArrayEquals(new double[] {900.0, 300.0},
        result.getPoints().get(3).getCandidateValues(), 0.0);
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
    for (ProcessModelOperatingEnvelopeStudy.Point point : result.getPoints()) {
      assertTrue(point.getEvaluation().isBaselineRestored());
      assertTrue(point.getEvaluation().isBaselineSimulationConverged());
      assertArrayEquals(new double[] {600.0, 400.0}, point.getEvaluation().getBaselineValues(), 1.0e-8);
    }

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(result);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    SliceResult restored = (SliceResult) input.readObject();
    input.close();
    assertEquals(result.getCompletedPointCount(), restored.getCompletedPointCount());
    assertEquals(result.getBottleneckTransitions().get(0).getToConstraint(),
        restored.getBottleneckTransitions().get(0).getToConstraint());
    assertNotSame(restored.getPoints(), restored.getPoints());
    assertThrows(UnsupportedOperationException.class, () -> restored.getPoints().clear());
  }

  /**
   * Verifies deterministic two-dimensional adjacency and that transitions do not connect the end
   * of one row to the start of the next row.
   */
  @Test
  void twoDimensionalSliceUsesGridAdjacencyForBottleneckTransitions() {
    Fixture fixture = createFixture();
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("allocation-grid",
        "Two-well operating envelope", "synthetic two-well grid", createEvaluator(fixture));

    SliceResult result = study.evaluateTwoDimensional("producer-a-rate", new double[] {300.0, 900.0},
        "producer-b-rate", new double[] {200.0, 600.0}, new double[] {600.0, 400.0});

    assertTrue(result.isComplete());
    assertEquals(4, result.getCompletedPointCount());
    assertArrayEquals(new double[] {300.0, 200.0}, result.getPoints().get(0).getCandidateValues(), 0.0);
    assertArrayEquals(new double[] {300.0, 600.0}, result.getPoints().get(1).getCandidateValues(), 0.0);
    assertArrayEquals(new double[] {900.0, 200.0}, result.getPoints().get(2).getCandidateValues(), 0.0);
    assertArrayEquals(new double[] {900.0, 600.0}, result.getPoints().get(3).getCandidateValues(), 0.0);
    assertEquals(2, result.getBottleneckTransitions().size());

    Transition first = result.getBottleneckTransitions().get(0);
    Transition second = result.getBottleneckTransitions().get(1);
    assertEquals("producer-a-rate", first.getAxisActionId());
    assertEquals(0, first.getFromSequenceIndex());
    assertEquals(2, first.getToSequenceIndex());
    assertEquals("producer-b-rate", second.getAxisActionId());
    assertEquals(2, second.getFromSequenceIndex());
    assertEquals(3, second.getToSequenceIndex());
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
  }

  /**
   * Verifies invalid axes and anchors fail before mutating the process model.
   */
  @Test
  void rejectsInvalidAxesAndAnchorsBeforeEvaluation() {
    Fixture fixture = createFixture();
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("validation",
        "Validation", "synthetic validation", createEvaluator(fixture));
    int before = study.getCandidateEvaluator().getSimulationEvaluator().getEvaluationCount();

    assertThrows(IllegalArgumentException.class,
        () -> study.evaluateOneDimensional("missing", new double[] {500.0}, new double[] {600.0, 400.0}));
    assertThrows(IllegalArgumentException.class,
        () -> study.evaluateOneDimensional("producer-a-rate", new double[] {1200.0},
            new double[] {600.0, 400.0}));
    assertThrows(IllegalArgumentException.class,
        () -> study.evaluateTwoDimensional("producer-a-rate", new double[] {500.0}, "producer-a-rate",
            new double[] {600.0}, new double[] {600.0, 400.0}));
    assertThrows(IllegalArgumentException.class,
        () -> study.evaluateOneDimensional("producer-a-rate", new double[] {500.0}, new double[] {600.0}));

    assertEquals(before, study.getCandidateEvaluator().getSimulationEvaluator().getEvaluationCount());
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
  }
}
