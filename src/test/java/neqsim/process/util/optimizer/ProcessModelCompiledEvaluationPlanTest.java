package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelCompiledEvaluationPlan.EvaluationResult;
import neqsim.process.util.optimizer.ProcessModelCompiledEvaluationPlan.Outcome;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for {@link ProcessModelCompiledEvaluationPlan}. */
class ProcessModelCompiledEvaluationPlanTest {
  /** Controlled two-action process fixture. */
  private static final class Fixture {
    private final Stream producerA;
    private final Stream producerB;
    private final CapacityConstraint sharedCapacity;
    private final ProcessSystem wells;
    private final ProcessModel model;

    private Fixture(Stream producerA, Stream producerB, CapacityConstraint sharedCapacity, ProcessSystem wells,
        ProcessModel model) {
      this.producerA = producerA;
      this.producerB = producerB;
      this.sharedCapacity = sharedCapacity;
      this.wells = wells;
      this.model = model;
    }
  }

  /** Creates independently addressable producers with one exact installed and boundary capacity. */
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
    CapacityConstraint sharedCapacity = new CapacityConstraint("installed gathering rate", "kg/hr", ConstraintType.HARD)
        .setDesignValue(1200.0).setSeverity(ConstraintSeverity.HARD).setDataSource("synthetic shared manifold basis")
        .setConfidence(0.95).setValidityRange(500.0, 1400.0)
        .setValueSupplier(() -> producerA.getFlowRate("kg/hr") + producerB.getFlowRate("kg/hr"));
    gatheringSink.clearCapacityConstraints();
    gatheringSink.addCapacityConstraint(sharedCapacity);

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
    return new Fixture(producerA, producerB, sharedCapacity, wells, model);
  }

  /** Creates the transactional authority used by the compiled plan. */
  private ProcessModelOperatingActionSetEvaluator createEvaluator(Fixture fixture) {
    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("total production",
        model -> fixture.producerA.getFlowRate("kg/hr") + fixture.producerB.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    ProcessBoundaryConstraintEvidence.Metadata boundary = new ProcessBoundaryConstraintEvidence.Metadata(
        "host-receiving", "gathering", "host inlet", ProcessBoundaryConstraintEvidence.Kind.RECEIVING_CAPACITY,
        ProcessBoundaryConstraintEvidence.FlowDirection.INTO_PROCESS, NetworkDecisionVariable.RateBasis.MASS,
        "synthetic host receiving basis", 1.0, null, null,
        ProcessBoundaryConstraintEvidence.ApplicabilityStatus.NOT_ASSESSED, "total rate", null, null, -1);
    simulation.addBoundaryConstraint("host receiving rate", boundary,
        model -> ProcessBoundaryConstraintEvidence.Sample
            .available(fixture.producerA.getFlowRate("kg/hr") + fixture.producerB.getFlowRate("kg/hr")),
        ProcessModelSimulationEvaluator.ConstraintDefinition.Type.UPPER_BOUND, Double.NEGATIVE_INFINITY, 2000.0, 0.0,
        "kg/hr", true, 10.0, 1000.0);
    ProcessModelOperatingAction actionA = ProcessModelOperatingAction.continuous("producer-a-rate", "Producer A rate",
        "wells::producer A.flowRate", 200.0, 1000.0, "kg/hr", "synthetic producer A envelope");
    ProcessModelOperatingAction actionB = ProcessModelOperatingAction.continuous("producer-b-rate", "Producer B rate",
        "wells::producer B.flowRate", 200.0, 1000.0, "kg/hr", "synthetic producer B envelope");
    return new ProcessModelOperatingActionSetEvaluator("well-allocation", "Coupled well allocation",
        "synthetic two-well allocation basis", simulation, Arrays.asList(actionA, actionB))
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "gathering sink",
            "installed gathering rate", "shared installed gathering capacity");
  }

  /** Compiles at a converged baseline and accepts only complete exact-coverage replay evidence. */
  @Test
  void compilesAndAcceptsCompleteCandidateEvidence() {
    Fixture fixture = createFixture();
    ProcessModelCompiledEvaluationPlan plan = ProcessModelCompiledEvaluationPlan.compile("allocation-plan",
        "Compiled allocation", "controlled two-producer qualification", "master-test-baseline",
        createEvaluator(fixture));

    assertTrue(plan.isCurrent(), plan.getStalenessDiagnostics().toString());
    assertEquals(Arrays.asList("wells", "gathering"), plan.getAreaNames());
    assertEquals(1, plan.getExpectedInstalledCapacityIdentities().size());
    assertEquals(1, plan.getExpectedBoundaryIdentities().size());
    assertTrue(plan.getCompilationEvidence().isFeasible());

    EvaluationResult result = plan.evaluate(new double[] { 700.0, 300.0 });
    assertEquals(Outcome.ACCEPTED, result.getOutcome(), result.getDiagnostics().toString());
    assertTrue(result.isAccepted());
    assertTrue(result.getCandidateEvidence().isFeasible());
    assertTrue(result.getCandidateEvidence().isBaselineRestored());
    assertTrue(result.getCandidateEvidence().isBaselineSimulationConverged());
    assertArrayEquals(new double[] { 600.0, 400.0 }, result.getCandidateEvidence().getBaselineValues(), 1.0e-8);
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);

    JsonObject planJson = JsonParser.parseString(plan.toJson()).getAsJsonObject();
    JsonObject resultJson = JsonParser.parseString(result.toJson()).getAsJsonObject();
    assertEquals("ProcessModelCompiledEvaluationPlan", planJson.get("type").getAsString());
    assertTrue(planJson.get("current").getAsBoolean());
    assertEquals("ACCEPTED", resultJson.get("outcome").getAsString());
    assertEquals(1, resultJson.getAsJsonArray("expectedInstalledCapacityIdentities").size());
    assertEquals(1, resultJson.getAsJsonArray("expectedBoundaryIdentities").size());
    assertTrue(resultJson.getAsJsonObject("candidateEvidence").get("candidateSimulationConverged").getAsBoolean());
  }

  /** Rejects invalid values before any baseline run or candidate mutation. */
  @Test
  void rejectsNonFiniteAndWrongLengthCandidatesBeforeDelegation() {
    Fixture fixture = createFixture();
    ProcessModelOperatingActionSetEvaluator evaluator = createEvaluator(fixture);
    ProcessModelCompiledEvaluationPlan plan = ProcessModelCompiledEvaluationPlan.compile("allocation-plan",
        "Compiled allocation", "controlled two-producer qualification", "master-test-baseline", evaluator);
    int evaluationsBefore = evaluator.getSimulationEvaluator().getEvaluationCount();

    EvaluationResult nonFinite = plan.evaluate(new double[] { Double.NaN, 300.0 });
    EvaluationResult wrongLength = plan.evaluate(new double[] { 700.0 });

    assertEquals(Outcome.CANDIDATE_VECTOR_INVALID, nonFinite.getOutcome());
    assertEquals(Outcome.CANDIDATE_VECTOR_INVALID, wrongLength.getOutcome());
    assertNull(nonFinite.getCandidateEvidence());
    assertEquals(evaluationsBefore, evaluator.getSimulationEvaluator().getEvaluationCount());
    assertFalse(nonFinite.toJson().contains("NaN"));
    assertFalse(nonFinite.toJson().contains("Infinity"));
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Rejects structural model changes before candidate mutation. */
  @Test
  void rejectsStaleProcessSystemStructureBeforeDelegation() {
    Fixture fixture = createFixture();
    ProcessModelOperatingActionSetEvaluator evaluator = createEvaluator(fixture);
    ProcessModelCompiledEvaluationPlan plan = ProcessModelCompiledEvaluationPlan.compile("allocation-plan",
        "Compiled allocation", "controlled two-producer qualification", "master-test-baseline", evaluator);
    int evaluationsBefore = evaluator.getSimulationEvaluator().getEvaluationCount();
    Stream addedAfterCompilation = new Stream("late stream", fixture.producerA.getThermoSystem().clone());
    fixture.wells.add(addedAfterCompilation);

    EvaluationResult result = plan.evaluate(new double[] { 700.0, 300.0 });

    assertEquals(Outcome.PLAN_STALE, result.getOutcome(), result.getDiagnostics().toString());
    assertFalse(plan.isCurrent());
    assertTrue(result.getDiagnostics().toString().contains("structure version"));
    assertEquals(evaluationsBefore, evaluator.getSimulationEvaluator().getEvaluationCount());
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Equal area names and structure counters do not authorize a replacement model. */
  @Test
  void rejectsReplacementModelBeforeDelegation() {
    Fixture fixture = createFixture();
    ProcessModelOperatingActionSetEvaluator evaluator = createEvaluator(fixture);
    ProcessModelCompiledEvaluationPlan plan = ProcessModelCompiledEvaluationPlan.compile("allocation-plan",
        "Compiled allocation", "controlled two-producer qualification", "master-test-baseline", evaluator);
    Fixture replacement = createFixture();
    int evaluationsBefore = evaluator.getSimulationEvaluator().getEvaluationCount();
    evaluator.getSimulationEvaluator().setProcessModel(replacement.model);

    assertFalse(plan.isCurrent());
    EvaluationResult result = plan.evaluate(new double[] { 700.0, 300.0 });
    assertEquals(Outcome.PLAN_STALE, result.getOutcome(), result.getDiagnostics().toString());
    assertNull(result.getCandidateEvidence());
    assertEquals(evaluationsBefore, evaluator.getSimulationEvaluator().getEvaluationCount());
    assertEquals(600.0, replacement.producerA.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Replacing an area with equal counters must invalidate its frozen object identity. */
  @Test
  void rejectsReplacementAreaBeforeDelegation() {
    Fixture fixture = createFixture();
    ProcessModelOperatingActionSetEvaluator evaluator = createEvaluator(fixture);
    ProcessModelCompiledEvaluationPlan plan = ProcessModelCompiledEvaluationPlan.compile("allocation-plan",
        "Compiled allocation", "controlled two-producer qualification", "master-test-baseline", evaluator);
    Fixture replacement = createFixture();
    assertEquals(fixture.model.get("gathering").getStructureVersion(),
        replacement.model.get("gathering").getStructureVersion());
    fixture.model.remove("gathering");
    fixture.model.add("gathering", replacement.model.get("gathering"));
    int evaluationsBefore = evaluator.getSimulationEvaluator().getEvaluationCount();

    assertFalse(plan.isCurrent());
    EvaluationResult result = plan.evaluate(new double[] { 700.0, 300.0 });
    assertEquals(Outcome.PLAN_STALE, result.getOutcome(), result.getDiagnostics().toString());
    assertNull(result.getCandidateEvidence());
    assertEquals(evaluationsBefore, evaluator.getSimulationEvaluator().getEvaluationCount());
    assertEquals(600.0, replacement.producerA.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Rejects changed installed ratings and evaluator definitions as stale compiled authority. */
  @Test
  void rejectsChangedRatingAndEvaluatorDefinition() {
    Fixture ratingFixture = createFixture();
    ProcessModelOperatingActionSetEvaluator ratingEvaluator = createEvaluator(ratingFixture);
    ProcessModelCompiledEvaluationPlan ratingPlan = ProcessModelCompiledEvaluationPlan.compile("rating-plan",
        "Compiled rating", "controlled rating qualification", "master-test-baseline", ratingEvaluator);
    ratingFixture.sharedCapacity.setDesignValue(1300.0);

    EvaluationResult changedRating = ratingPlan.evaluate(new double[] { 700.0, 300.0 });
    assertEquals(Outcome.PLAN_STALE, changedRating.getOutcome(), changedRating.getDiagnostics().toString());
    assertTrue(changedRating.getDiagnostics().toString().contains("rating"));

    Fixture definitionFixture = createFixture();
    ProcessModelOperatingActionSetEvaluator definitionEvaluator = createEvaluator(definitionFixture);
    ProcessModelCompiledEvaluationPlan definitionPlan = ProcessModelCompiledEvaluationPlan.compile("definition-plan",
        "Compiled definition", "controlled evaluator qualification", "master-test-baseline", definitionEvaluator);
    definitionEvaluator.getSimulationEvaluator().addConstraintUpperBound("late constraint", model -> 0.0, 1.0);

    EvaluationResult changedDefinition = definitionPlan.evaluate(new double[] { 700.0, 300.0 });
    assertEquals(Outcome.PLAN_STALE, changedDefinition.getOutcome(), changedDefinition.getDiagnostics().toString());
    assertTrue(changedDefinition.getDiagnostics().toString().contains("evaluator definition"));
  }

  /** Preserves complete rejected evidence and restores the baseline after a physical overload. */
  @Test
  void preservesRejectedEvidenceAndRestoresOverloadedCandidate() {
    Fixture fixture = createFixture();
    ProcessModelCompiledEvaluationPlan plan = ProcessModelCompiledEvaluationPlan.compile("allocation-plan",
        "Compiled allocation", "controlled two-producer qualification", "master-test-baseline",
        createEvaluator(fixture));

    EvaluationResult result = plan.evaluate(new double[] { 800.0, 500.0 });

    assertEquals(Outcome.CANDIDATE_REJECTED, result.getOutcome(), result.getDiagnostics().toString());
    assertFalse(result.isAccepted());
    assertNotSame(result.getDiagnostics(), result.getDiagnostics());
    assertNotSame(result.getExpectedInstalledCapacityIdentities(), result.getExpectedInstalledCapacityIdentities());
    assertTrue(result.getCandidateEvidence().isBaselineRestored());
    assertTrue(result.getCandidateEvidence().isBaselineSimulationConverged());
    assertFalse(result.getCandidateEvidence().isFeasible());
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
    assertThrows(UnsupportedOperationException.class, () -> result.getDiagnostics().clear());
  }

  /** Keeps the complete accepted result serializable for Java and JPype consumers. */
  @Test
  void serializesImmutableAcceptedResult() throws Exception {
    Fixture fixture = createFixture();
    ProcessModelCompiledEvaluationPlan plan = ProcessModelCompiledEvaluationPlan.compile("allocation-plan",
        "Compiled allocation", "controlled two-producer qualification", "master-test-baseline",
        createEvaluator(fixture));
    EvaluationResult original = plan.evaluate(new double[] { 700.0, 300.0 });

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(original);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    EvaluationResult restored = (EvaluationResult) input.readObject();
    input.close();

    assertTrue(restored.isAccepted());
    assertEquals("allocation-plan", restored.getPlanId());
    assertArrayEquals(new double[] { 700.0, 300.0 }, restored.getCandidateValues(), 0.0);
    assertTrue(restored.getCandidateEvidence().isFeasible());
    assertTrue(plan.toJson().contains("compilationEvidence"));
    assertFalse(plan.toJson().contains("NaN"));
    assertFalse(plan.toJson().contains("Infinity"), plan.toJson());
    double[] values = restored.getCandidateValues();
    values[0] = -1.0;
    assertArrayEquals(new double[] { 700.0, 300.0 }, restored.getCandidateValues(), 0.0);
    assertFalse(restored.toJson().contains("NaN"));
    assertFalse(restored.toJson().contains("Infinity"));
  }
}
