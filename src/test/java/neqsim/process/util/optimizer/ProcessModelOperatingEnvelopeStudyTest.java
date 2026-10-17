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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSource;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.LeadingConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.Outcome;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.SliceResult;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.Transition;
import neqsim.process.util.optimizer.ProcessModelOperatingPointOptimizer.OperatingPointSearchResult;
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

  /** Validated accepted-point inputs retained for one lifecycle period. */
  private static final class AcceptedPeriod {
    private final Fixture fixture;
    private final ProcessModelOperatingEnvelopeStudy study;
    private final OperatingPointSearchResult search;
    private final SliceResult slice;

    private AcceptedPeriod(Fixture fixture, ProcessModelOperatingEnvelopeStudy study, OperatingPointSearchResult search,
        SliceResult slice) {
      this.fixture = fixture;
      this.study = study;
      this.search = search;
      this.slice = slice;
    }
  }

  /**
   * Creates a synthetic two-producer model with one producer-local and one shared gathering limit.
   *
   * @return configured model fixture
   */
  private Fixture createFixture() {
    return createFixture(800.0, 1200.0);
  }

  /**
   * Creates a synthetic two-producer model for one independently configured lifecycle period.
   *
   * @param producerOperatingLimit producer-A operating limit in kg/hr
   * @param sharedOperatingLimit shared gathering limit in kg/hr
   * @return configured model fixture
   */
  private Fixture createFixture(double producerOperatingLimit, double sharedOperatingLimit) {
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
        .setDesignValue(Math.max(1600.0, producerOperatingLimit)).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic producer choke envelope").setConfidence(0.95).setValidityRange(200.0, 2000.0)
        .setOperatingLimit(producerOperatingLimit, ConstraintSource.USER_RULE, "synthetic facility choke rule")
        .setOperatingLimitConfidence(0.90).setOperatingLimitValidityRange(200.0, 2000.0)
        .setValueSupplier(() -> producerA.getFlowRate("kg/hr"));
    CapacityConstraint sharedLimit = new CapacityConstraint("shared gathering rate", "kg/hr", ConstraintType.HARD)
        .setDesignValue(sharedOperatingLimit).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic shared manifold basis").setConfidence(0.95).setValidityRange(400.0, 2500.0)
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
   * Runs one independent accepted-point search and nearby slice for lifecycle aggregation.
   *
   * @param periodId period identifier used by the optimizer and study
   * @param producerOperatingLimit producer-A operating limit in kg/hr
   * @param sharedOperatingLimit shared gathering limit in kg/hr
   * @return complete accepted period inputs
   */
  private AcceptedPeriod createAcceptedPeriod(String periodId, double producerOperatingLimit,
      double sharedOperatingLimit) {
    Fixture fixture = createFixture(producerOperatingLimit, sharedOperatingLimit);
    ProcessModelOperatingActionSetEvaluator evaluator = createEvaluator(fixture);
    ProcessModelOperatingPointOptimizer optimizer = new ProcessModelOperatingPointOptimizer(periodId + "-search",
        periodId + " search", "synthetic lifecycle accepted-point search", evaluator)
        .setInitialCandidate(new double[] {600.0, 400.0}).setMaximumEvaluations(30).setInitialStepFraction(0.25)
        .setRelativeStepTolerance(0.05)
        .setObjectiveImprovementTolerance(1.0e-6, "synthetic lifecycle objective tolerance");
    OperatingPointSearchResult search = optimizer.optimize();
    assertTrue(search.isAcceptedPointReplayed(), search.getOutcome() + ": " + search.getDiagnostics());
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy(periodId + "-envelope",
        periodId + " envelope", "synthetic lifecycle period", evaluator);
    double[] accepted = search.getAcceptedCandidateValues();
    SliceResult slice = study.evaluateOneDimensional("producer-a-rate",
        new double[] {Math.max(200.0, accepted[0] - 50.0), accepted[0]}, search);
    assertTrue(slice.isComplete(), slice.getDiagnostics().toString());
    return new AcceptedPeriod(fixture, study, search, slice);
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

    ProcessModelOperatingAction actionA = ProcessModelOperatingAction.continuous("producer-a-rate", "Producer A rate",
        "wells::producer A.flowRate", 200.0, 1000.0, "kg/hr", "synthetic producer A operating envelope");
    ProcessModelOperatingAction actionB = ProcessModelOperatingAction.continuous("producer-b-rate", "Producer B rate",
        "wells::producer B.flowRate", 200.0, 1000.0, "kg/hr", "synthetic producer B operating envelope");

    return new ProcessModelOperatingActionSetEvaluator("field-actions", "Field actions",
        "synthetic field-to-facility envelope basis", simulation, Arrays.asList(actionA, actionB))
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "gathering sink",
            "producer A rate", "synthetic producer choke envelope")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "gathering sink",
            "shared gathering rate", "synthetic shared manifold basis");
  }

  /**
   * Verifies a one-dimensional slice keeps infeasible points, detects bottleneck migration, and restores the original
   * process baseline after every candidate.
   *
   * @throws Exception if Java serialization unexpectedly fails
   */
  @Test
  void oneDimensionalSliceTracksBottleneckMigrationAndRestoresBaseline() throws Exception {
    Fixture fixture = createFixture();
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("well-a-slice",
        "Well A operating envelope", "synthetic field-to-facility acceptance", createEvaluator(fixture));

    SliceResult result = study.evaluateOneDimensional("producer-a-rate", new double[] {300.0, 500.0, 700.0, 900.0},
        new double[] {600.0, 300.0});

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
    assertEquals("CONFIGURED_OPERATING", result.getPoints().get(2).getLeadingConstraint().getApplicableLimitRole());
    assertEquals("synthetic facility choke rule",
        result.getPoints().get(2).getLeadingConstraint().getApplicableLimitProvenance());
    assertEquals(1, result.getBottleneckTransitions().size());

    Transition transition = result.getBottleneckTransitions().get(0);
    assertEquals("producer-a-rate", transition.getAxisActionId());
    assertEquals(1, transition.getFromSequenceIndex());
    assertEquals(2, transition.getToSequenceIndex());
    assertEquals(500.0, transition.getFromAxisValue(), 0.0);
    assertEquals(700.0, transition.getToAxisValue(), 0.0);
    assertEquals("gathering::gathering sink/shared gathering rate", transition.getFromConstraint());
    assertEquals("gathering::gathering sink/producer A rate", transition.getToConstraint());

    assertArrayEquals(new double[] {900.0, 300.0}, result.getPoints().get(3).getCandidateValues(), 0.0);
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
   * Verifies deterministic two-dimensional adjacency and that transitions do not connect the end of one row to the
   * start of the next row.
   */
  @Test
  void twoDimensionalSliceUsesGridAdjacencyForBottleneckTransitions() {
    Fixture fixture = createFixture();
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("allocation-grid",
        "Two-well operating envelope", "synthetic two-well grid", createEvaluator(fixture));

    SliceResult result = study.evaluateTwoDimensional("producer-a-rate", new double[] {300.0, 900.0}, "producer-b-rate",
        new double[] {200.0, 600.0}, new double[] {600.0, 400.0});

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
    assertEquals("producer-a-rate", second.getAxisActionId());
    assertEquals(1, second.getFromSequenceIndex());
    assertEquals(3, second.getToSequenceIndex());
    assertEquals("gathering::gathering sink/producer A rate", second.getToConstraint());
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
  }

  /**
   * Verifies an accepted mixed-search replay supplies the bottleneck report and exact envelope anchor, while changed
   * baseline state or evaluator definitions fail before another simulation is run.
   */
  @Test
  void acceptedPointReplayAnchorsBottleneckAndEnvelopeFailClosed() throws Exception {
    Fixture fixture = createFixture();
    ProcessModelOperatingActionSetEvaluator evaluator = createEvaluator(fixture);
    ProcessModelOperatingPointOptimizer optimizer = new ProcessModelOperatingPointOptimizer("field-search",
        "Field search", "synthetic accepted-point handoff", evaluator).setInitialCandidate(new double[] {600.0, 400.0})
        .setMaximumEvaluations(24).setInitialStepFraction(0.25).setRelativeStepTolerance(0.1)
        .setObjectiveImprovementTolerance(500.0, "synthetic interior-anchor stability threshold");
    OperatingPointSearchResult search = optimizer.optimize();
    assertTrue(search.isAcceptedPointReplayed(),
        search.getOutcome() + ": " + search.getDiagnostics() + "; trace="
            + search
                .getCandidates().stream().map(candidate -> candidate.getSequenceIndex() + ":"
                    + candidate.getRawObjective() + ":" + Arrays.toString(candidate.getCandidateValues()))
                .collect(java.util.stream.Collectors.toList()));

    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("accepted-envelope",
        "Accepted operating-point envelope", "synthetic accepted-point handoff", evaluator);
    int evaluationsBeforeReport = evaluator.getSimulationEvaluator().getEvaluationCount();
    LeadingConstraintEvidence bottleneck = study.getAcceptedPointBottleneck(search);
    assertEquals(evaluationsBeforeReport, evaluator.getSimulationEvaluator().getEvaluationCount());
    assertEquals(LeadingConstraintEvidence.Source.INSTALLED_EQUIPMENT, bottleneck.getSource());
    assertTrue(bottleneck.isFeasible());

    double[] accepted = search.getAcceptedCandidateValues();
    SliceResult slice = study.evaluateOneDimensional("producer-a-rate",
        new double[] {accepted[0], Math.min(1000.0, accepted[0] + 100.0)}, search);
    assertTrue(slice.isComplete());
    assertEquals(2, slice.getEvaluationCount());
    assertTrue(Double.isFinite(slice.getRuntimeSeconds()) && slice.getRuntimeSeconds() >= 0.0);
    assertArrayEquals(accepted, slice.getAnchorValues(), 0.0);
    assertEquals(accepted[1], slice.getPoints().get(0).getCandidateValues()[1], 0.0);
    SliceResult secondSlice = study.evaluateOneDimensional("producer-b-rate",
        new double[] {Math.max(200.0, accepted[1] - 100.0), accepted[1]}, search);
    assertEquals(2, secondSlice.getEvaluationCount());
    int evaluationsBeforeEvidence = evaluator.getSimulationEvaluator().getEvaluationCount();
    Map<String, Object> envelopeEvidence = study.getAcceptedPointEnvelopeEvidence("cycle-43", search, slice,
        secondSlice);
    assertEquals(evaluationsBeforeEvidence, evaluator.getSimulationEvaluator().getEvaluationCount());
    assertEquals(Integer.valueOf(1), envelopeEvidence.get("schema_version"));
    assertEquals("cycle-43", envelopeEvidence.get("cycle_id"));
    assertEquals(Arrays.asList("producer-a-rate", "producer-b-rate"), envelopeEvidence.get("accepted_action_ids"));
    assertEquals(Arrays.asList(Double.valueOf(accepted[0]), Double.valueOf(accepted[1])),
        envelopeEvidence.get("accepted_candidate_values"));
    assertEquals(Integer.valueOf(2), envelopeEvidence.get("slice_count"));
    assertEquals(Long.valueOf(4L), envelopeEvidence.get("envelope_evaluation_count"));
    assertEquals(Long.valueOf(search.getEvaluationCount() + 4L), envelopeEvidence.get("total_evaluation_count"));
    assertTrue(((Double) envelopeEvidence.get("envelope_runtime_seconds")).doubleValue() >= 0.0);
    assertEquals(2, ((List<?>) envelopeEvidence.get("slices")).size());
    assertEquals(bottleneck.getQualifiedConstraintName(),
        ((Map<?, ?>) envelopeEvidence.get("accepted_point_bottleneck")).get("qualified_constraint_name"));
    assertThrows(UnsupportedOperationException.class, () -> envelopeEvidence.put("study_id", "changed"));
    assertThrows(IllegalArgumentException.class,
        () -> study.getAcceptedPointEnvelopeEvidence("cycle-43", search, slice, slice));
    ProcessModelOperatingEnvelopeStudy foreignStudy = new ProcessModelOperatingEnvelopeStudy("accepted-envelope",
        "Accepted operating-point envelope", "synthetic accepted-point handoff", evaluator);
    assertThrows(IllegalStateException.class,
        () -> foreignStudy.getAcceptedPointEnvelopeEvidence("cycle-43", search, slice, secondSlice));
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);

    fixture.producerA.setFlowRate(650.0, "kg/hr");
    fixture.model.run();
    int evaluationsBeforeStaleBaseline = evaluator.getSimulationEvaluator().getEvaluationCount();
    assertThrows(IllegalStateException.class, () -> study.getAcceptedPointBottleneck(search));
    assertEquals(evaluationsBeforeStaleBaseline, evaluator.getSimulationEvaluator().getEvaluationCount());
    fixture.producerA.setFlowRate(600.0, "kg/hr");
    fixture.model.run();
    assertTrue(study.getAcceptedPointBottleneck(search).isFeasible());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(search);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    OperatingPointSearchResult deserialized = (OperatingPointSearchResult) input.readObject();
    input.close();
    assertThrows(IllegalStateException.class, () -> study.getAcceptedPointBottleneck(deserialized));
    bytes = new ByteArrayOutputStream();
    output = new ObjectOutputStream(bytes);
    output.writeObject(slice);
    output.close();
    input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    SliceResult deserializedSlice = (SliceResult) input.readObject();
    input.close();
    assertThrows(IllegalStateException.class,
        () -> study.getAcceptedPointEnvelopeEvidence("cycle-43", search, deserializedSlice, secondSlice));

    CapacityConstraint changedLimit = evaluator.getSimulationEvaluator().getConstraints().stream()
        .filter(constraint -> constraint.isCapacityConstraint()
            && "producer A rate".equals(constraint.getEquipmentConstraintName()))
        .findFirst().get().getCapturedCapacityConstraint();
    changedLimit.setOperatingLimit(790.0, ConstraintSource.USER_RULE, "post-search facility choke rule");
    int evaluationsBeforeDefinitionChange = evaluator.getSimulationEvaluator().getEvaluationCount();
    assertThrows(IllegalStateException.class,
        () -> study.evaluateOneDimensional("producer-a-rate", new double[] {accepted[0]}, search));
    assertEquals(evaluationsBeforeDefinitionChange, evaluator.getSimulationEvaluator().getEvaluationCount());
  }

  /**
   * Verifies independently configured periods retain complete envelope evidence and report an accepted bottleneck
   * migration without reusing mutable process state between periods.
   */
  @Test
  void lifecycleEvidenceTracksIndependentPeriodBottleneckMigration() {
    AcceptedPeriod early = createAcceptedPeriod("early-life", 650.0, 2000.0);
    AcceptedPeriod late = createAcceptedPeriod("late-life", 1500.0, 1200.0);
    assertEquals("gathering::gathering sink/producer A rate",
        early.study.getAcceptedPointBottleneck(early.search).getQualifiedConstraintName());
    assertEquals("gathering::gathering sink/shared gathering rate",
        late.study.getAcceptedPointBottleneck(late.search).getQualifiedConstraintName());
    SliceResult earlyComparable = early.study.evaluateOneDimensional("producer-a-rate", new double[] {500.0, 700.0},
        early.search);
    SliceResult lateComparable = late.study.evaluateOneDimensional("producer-a-rate", new double[] {500.0, 700.0},
        late.search);
    assertTrue(earlyComparable.isComplete());
    assertTrue(lateComparable.isComplete());

    ProcessModelOperatingEnvelopeLifecycleStudy lifecycle = new ProcessModelOperatingEnvelopeLifecycleStudy(
        "two-period-field", "Two-period field envelope", "synthetic changing facility limits", "year")
        .addPeriod("early-life", "Early life", 0.0, "producer-A operating limit governs", early.study, early.search,
            earlyComparable)
        .addPeriod("late-life", "Late life", 5.0, "shared gathering limit governs", late.study, late.search,
            lateComparable);

    assertEquals(2, lifecycle.getPeriods().size());
    assertEquals(1, lifecycle.getBottleneckTransitions().size());
    ProcessModelOperatingEnvelopeLifecycleStudy.LifecycleBottleneckTransition transition = lifecycle
        .getBottleneckTransitions().get(0);
    assertEquals("early-life", transition.getFromPeriodId());
    assertEquals("late-life", transition.getToPeriodId());
    assertEquals("gathering::gathering sink/producer A rate", transition.getFromConstraint());
    assertEquals("gathering::gathering sink/shared gathering rate", transition.getToConstraint());

    Map<String, Object> evidence = lifecycle.toEvidenceMap("cycle-lifecycle-1");
    assertEquals(Integer.valueOf(2), evidence.get("schema_version"));
    assertEquals("cycle-lifecycle-1", evidence.get("cycle_id"));
    assertEquals(Integer.valueOf(2), evidence.get("period_count"));
    assertEquals(Arrays.asList("producer-a-rate", "producer-b-rate"), evidence.get("accepted_action_ids"));
    assertEquals(Integer.valueOf(4), evidence.get("requested_point_count"));
    assertEquals(Integer.valueOf(4), evidence.get("completed_point_count"));
    assertTrue(((Long) evidence.get("total_evaluation_count")).longValue() >= 4L);
    assertTrue(Double.isFinite(((Double) evidence.get("envelope_runtime_seconds")).doubleValue()));
    assertEquals(2, ((List<?>) evidence.get("periods")).size());
    assertEquals(1, ((List<?>) evidence.get("bottleneck_transitions")).size());
    List<?> periodChanges = (List<?>) evidence.get("period_change_diagnostics");
    assertEquals(1, periodChanges.size());
    Map<?, ?> periodChange = (Map<?, ?>) periodChanges.get(0);
    assertEquals("early-life", periodChange.get("from_period_id"));
    assertEquals("late-life", periodChange.get("to_period_id"));
    assertEquals(Integer.valueOf(2), Integer.valueOf(((List<?>) periodChange.get("accepted_action_changes")).size()));
    assertEquals(Integer.valueOf(1), periodChange.get("comparable_slice_count"));
    assertTrue(((List<?>) periodChange.get("removed_slice_axes")).isEmpty());
    assertTrue(((List<?>) periodChange.get("added_slice_axes")).isEmpty());
    Map<?, ?> sliceChange = (Map<?, ?>) ((List<?>) periodChange.get("slice_changes")).get(0);
    assertEquals(Arrays.asList("producer-a-rate"), sliceChange.get("axis_action_ids"));
    assertEquals(Integer.valueOf(2), sliceChange.get("from_requested_point_count"));
    assertEquals(Integer.valueOf(2), sliceChange.get("to_requested_point_count"));
    assertEquals(Boolean.TRUE, sliceChange.get("grid_identical"));
    assertEquals(Integer.valueOf(2), sliceChange.get("comparable_point_count"));
    assertEquals(Integer.valueOf(1), sliceChange.get("became_feasible_count"));
    assertEquals(Integer.valueOf(0), sliceChange.get("became_infeasible_count"));
    assertEquals(Boolean.TRUE, periodChange.get("advisory"));
    assertThrows(UnsupportedOperationException.class, () -> evidence.put("period_count", Integer.valueOf(0)));

    assertEquals(600.0, early.fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, early.fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(600.0, late.fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, late.fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
    assertTrue(early.fixture.model.isModelConverged());
    assertTrue(late.fixture.model.isModelConverged());
  }

  /** Verifies malformed lifecycle ordering is rejected before accepting another period. */
  @Test
  void lifecycleEvidenceRejectsDuplicateOrNonIncreasingPeriods() {
    AcceptedPeriod early = createAcceptedPeriod("early-life", 650.0, 2000.0);
    ProcessModelOperatingEnvelopeLifecycleStudy empty = new ProcessModelOperatingEnvelopeLifecycleStudy("empty-field",
        "Empty field envelope", "synthetic validation", "year");
    assertThrows(IllegalStateException.class, () -> empty.toEvidenceMap("cycle-empty"));

    ProcessModelOperatingEnvelopeLifecycleStudy lifecycle = new ProcessModelOperatingEnvelopeLifecycleStudy(
        "ordered-field", "Ordered field envelope", "synthetic validation", "year")
        .addPeriod("early-life", "Early life", 0.0, "initial constraints", early.study, early.search, early.slice);
    assertThrows(IllegalArgumentException.class, () -> lifecycle.addPeriod("early-life", "Duplicate", 1.0,
        "duplicate identifier", early.study, early.search, early.slice));
    assertThrows(IllegalArgumentException.class, () -> lifecycle.addPeriod("same-time", "Same time", 0.0,
        "non-increasing time", early.study, early.search, early.slice));
    assertEquals(1, lifecycle.getPeriods().size());
  }

  /** Verifies period diagnostics expose added and removed slice axes without inventing interpolation. */
  @Test
  void lifecycleEvidenceReportsIncomparableSliceAxes() {
    AcceptedPeriod early = createAcceptedPeriod("early-life", 650.0, 2000.0);
    AcceptedPeriod late = createAcceptedPeriod("late-life", 1500.0, 1200.0);
    SliceResult lateProducerB = late.study.evaluateOneDimensional("producer-b-rate", new double[] {300.0, 400.0},
        late.search);

    ProcessModelOperatingEnvelopeLifecycleStudy lifecycle = new ProcessModelOperatingEnvelopeLifecycleStudy(
        "changed-slices", "Changed lifecycle slices", "synthetic slice-comparability check", "year")
        .addPeriod("early-life", "Early life", 0.0, "producer-A slice", early.study, early.search, early.slice)
        .addPeriod("late-life", "Late life", 5.0, "producer-B slice", late.study, late.search, lateProducerB);

    Map<String, Object> diagnostic = lifecycle.getPeriodChangeDiagnostics().get(0);
    assertEquals(Integer.valueOf(0), diagnostic.get("comparable_slice_count"));
    assertEquals(Arrays.asList(Arrays.asList("producer-a-rate")), diagnostic.get("removed_slice_axes"));
    assertEquals(Arrays.asList(Arrays.asList("producer-b-rate")), diagnostic.get("added_slice_axes"));
    assertTrue(((List<?>) diagnostic.get("slice_changes")).isEmpty());
  }

  /**
   * Verifies invalid axes and anchors fail before mutating the process model.
   */
  @Test
  void rejectsInvalidAxesAndAnchorsBeforeEvaluation() {
    Fixture fixture = createFixture();
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("validation", "Validation",
        "synthetic validation", createEvaluator(fixture));
    int before = study.getCandidateEvaluator().getSimulationEvaluator().getEvaluationCount();

    assertThrows(IllegalArgumentException.class,
        () -> study.evaluateOneDimensional("missing", new double[] {500.0}, new double[] {600.0, 400.0}));
    assertThrows(IllegalArgumentException.class,
        () -> study.evaluateOneDimensional("producer-a-rate", new double[] {1200.0}, new double[] {600.0, 400.0}));
    assertThrows(IllegalArgumentException.class, () -> study.evaluateTwoDimensional("producer-a-rate",
        new double[] {500.0}, "producer-a-rate", new double[] {600.0}, new double[] {600.0, 400.0}));
    assertThrows(IllegalArgumentException.class,
        () -> study.evaluateOneDimensional("producer-a-rate", new double[] {500.0}, new double[] {600.0}));

    assertEquals(before, study.getCandidateEvaluator().getSimulationEvaluator().getEvaluationCount());
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
  }
}
