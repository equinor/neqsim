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
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelAllocationOptimizer.AllocationSearchResult;
import neqsim.process.util.optimizer.ProcessModelAllocationOptimizer.CandidateRecord;
import neqsim.process.util.optimizer.ProcessModelAllocationOptimizer.SearchOutcome;
import neqsim.process.util.optimizer.ProcessModelAllocationBottleneckAnalyzer.AnalysisOutcome;
import neqsim.process.util.optimizer.ProcessModelAllocationBottleneckAnalyzer.BottleneckAnalysisResult;
import neqsim.process.util.optimizer.ProcessModelAllocationBottleneckAnalyzer.BottleneckReliefOpportunity;
import neqsim.process.util.optimizer.ProcessModelAllocationBottleneckAnalyzer.EvidenceClass;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateObjectiveEvidence;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ConstraintDefinition;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for {@link ProcessModelAllocationOptimizer}. */
class ProcessModelAllocationOptimizerTest {
  /** Controlled two-producer allocation fixture. */
  private static final class AllocationFixture {
    /** First producer. */
    private final Stream producerA;

    /** Second producer. */
    private final Stream producerB;

    /** Atomic evaluator. */
    private final ProcessModelOperatingActionSetEvaluator evaluator;

    /** Creates a fixture. */
    private AllocationFixture(Stream producerA, Stream producerB, ProcessModelOperatingActionSetEvaluator evaluator) {
      this.producerA = producerA;
      this.producerB = producerB;
      this.evaluator = evaluator;
    }
  }

  /** Creates two controlled producers with per-leg and shared installed limits. */
  private AllocationFixture createFixture() {
    SystemInterface fluidA = new SystemSrkEos(298.15, 50.0);
    fluidA.addComponent("methane", 0.90);
    fluidA.addComponent("ethane", 0.10);
    fluidA.setMixingRule("classic");
    fluidA.setTotalFlowRate(500.0, "kg/hr");
    SystemInterface fluidB = fluidA.clone();

    Stream producerA = new Stream("producer A", fluidA);
    Stream producerB = new Stream("producer B", fluidB);
    Separator allocationSink = new Separator("allocation sink", producerA);
    allocationSink.clearCapacityConstraints();
    allocationSink.addCapacityConstraint(rateConstraint("producer A installed rate", 700.0,
        () -> producerA.getFlowRate("kg/hr"), "synthetic producer A installed envelope"));
    allocationSink.addCapacityConstraint(rateConstraint("producer B installed rate", 800.0,
        () -> producerB.getFlowRate("kg/hr"), "synthetic producer B installed envelope"));
    allocationSink.addCapacityConstraint(rateConstraint("shared gathering rate", 1000.0,
        () -> producerA.getFlowRate("kg/hr") + producerB.getFlowRate("kg/hr"), "synthetic shared gathering capacity"));

    ProcessSystem wells = new ProcessSystem("wells");
    wells.add(producerA);
    wells.add(producerB);
    ProcessSystem gathering = new ProcessSystem("gathering");
    gathering.add(allocationSink);
    ProcessModel model = new ProcessModel();
    model.add("wells", wells);
    model.add("gathering", gathering);
    model.run();

    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("allocation value proxy",
        processModel -> 2.0 * producerA.getFlowRate("kg/hr") + producerB.getFlowRate("kg/hr"),
        ObjectiveDefinition.Direction.MAXIMIZE);
    simulation.getObjectives().get(0).setUnit("value-unit/hr");
    ProcessModelOperatingAction actionA = ProcessModelOperatingAction.continuous("producer-a-rate", "Producer A rate",
        "wells::producer A.flowRate", 200.0, 1000.0, "kg/hr", "synthetic producer A operating envelope");
    ProcessModelOperatingAction actionB = ProcessModelOperatingAction.continuous("producer-b-rate", "Producer B rate",
        "wells::producer B.flowRate", 200.0, 1000.0, "kg/hr", "synthetic producer B operating envelope");
    ProcessModelOperatingActionSetEvaluator evaluator = new ProcessModelOperatingActionSetEvaluator(
        "allocation-actions", "Producer allocation actions", "synthetic fixed-total allocation", simulation,
        Arrays.asList(actionA, actionB))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "gathering", "allocation sink",
            "producer A installed rate", "producer A installed capacity")
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "gathering", "allocation sink",
            "producer B installed rate", "producer B installed capacity")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "allocation sink",
            "shared gathering rate", "shared gathering capacity");
    return new AllocationFixture(producerA, producerB, evaluator);
  }

  /**
   * Creates a hard rate constraint with nano-kg/hr-normalized analytical observations.
   *
   * <p>
   * This removes platform noise from stream mass/molar unit round trips without changing nominal capacity limits.
   * </p>
   */
  private CapacityConstraint rateConstraint(String name, double designValue,
      java.util.function.DoubleSupplier valueSupplier, String dataSource) {
    java.util.function.DoubleSupplier stableValueSupplier = () -> Math.rint(valueSupplier.getAsDouble() * 1.0e9)
        / 1.0e9;
    return new CapacityConstraint(name, "kg/hr", ConstraintType.HARD).setDesignValue(designValue)
        .setSeverity(ConstraintSeverity.HARD).setDataSource(dataSource).setConfidence(0.95)
        .setValidityRange(200.0, 1200.0).setValueSupplier(stableValueSupplier);
  }

  /** Creates the documented bounded transfer search. */
  private ProcessModelAllocationOptimizer createOptimizer(AllocationFixture fixture) {
    return new ProcessModelAllocationOptimizer("producer-allocation", "Producer allocation",
        "synthetic installed-capacity allocation basis", fixture.evaluator, 1000.0, "kg/hr")
        .setInitialAllocation(new double[] { 500.0, 500.0 }).setObjectiveIndex(0).setInitialStepFraction(0.10)
        .setRelativeStepTolerance(1.0e-3)
        .setObjectiveImprovementTolerance(1.0e-9,
            "synthetic objective is deterministic to substantially better than one nano-unit")
        .setMaximumEvaluations(100);
  }

  /** Verifies deterministic constrained allocation, conservation, opportunity, and recovery. */
  @Test
  void optimizesFixedTotalAndRanksLimitingEvidence() {
    AllocationFixture fixture = createFixture();
    ProcessModelAllocationOptimizer optimizer = createOptimizer(fixture);

    AllocationSearchResult result = optimizer.optimize();

    assertEquals(SearchOutcome.CONVERGED_WITH_FEASIBLE_CANDIDATE, result.getOutcome(),
        result.getDiagnostics().toString());
    assertTrue(result.isConverged());
    assertTrue(result.isModelRecovered());
    assertEquals("producer-allocation", result.getId());
    assertEquals("allocation-actions", result.getActionSetId());
    assertEquals("allocation value proxy", result.getObjective().getName());
    assertEquals(ObjectiveDefinition.Direction.MAXIMIZE, result.getObjective().getDirection());
    assertEquals("value-unit/hr", result.getObjective().getUnit());
    assertArrayEquals(new double[] { 700.0, 300.0 }, result.getBestFeasibleCandidate().getCandidateValues(), 1.0e-10);
    assertEquals(1700.0, result.getBestFeasibleCandidate().getRawObjective(), 1.0e-8);
    assertArrayEquals(new double[] { 800.0, 200.0 }, result.getBestSampledObjectiveCandidate().getCandidateValues(),
        1.0e-10);
    assertFalse(result.getBestSampledObjectiveCandidate().getEvaluation().isFeasible());
    assertEquals(100.0, result.getSampledObjectiveOpportunityGap(), 1.0e-8);
    assertEquals("producer A installed rate",
        result.getRankedHydraulicConstraintsAtBestFeasible().get(0).getBinding().getConstraintName());
    assertEquals(1.0, result.getRankedHydraulicConstraintsAtBestFeasible().get(0).getUtilization(), 1.0e-12);
    assertEquals("producer A installed rate",
        result.getRankedHydraulicConstraintsAtBestSampledObjective().get(0).getBinding().getConstraintName());
    assertEquals(800.0 / 700.0, result.getRankedHydraulicConstraintsAtBestSampledObjective().get(0).getUtilization(),
        1.0e-12);
    InstalledEquipmentCapacityEvidence bestCapacity = result.getInstalledCapacityEvidenceAtBestFeasible().get(0);
    assertEquals("gathering::allocation sink/producer A installed rate", bestCapacity.getQualifiedConstraintName());
    assertEquals(1.0, bestCapacity.getNormalizedUtilization(), 1.0e-12);
    assertEquals(0.0, bestCapacity.getPhysicalMargin(), 1.0e-8);
    assertEquals("kg/hr", bestCapacity.getPhysicalUnit());
    assertEquals(InstalledEquipmentCapacityEvidence.ConstraintOrigin.DIRECT, bestCapacity.getConstraintOrigin());
    assertEquals(800.0 / 700.0,
        result.getInstalledCapacityEvidenceAtBestSampledObjective().get(0).getNormalizedUtilization(), 1.0e-12);
    for (CandidateRecord candidate : result.getCandidates()) {
      double[] values = candidate.getCandidateValues();
      assertEquals(1000.0, values[0] + values[1], 1.0e-10);
      assertTrue(candidate.getEvaluation().isBaselineRestored());
      assertTrue(candidate.getEvaluation().isBaselineSimulationConverged());
    }
    assertEquals(500.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(500.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);

    AllocationSearchResult repeated = optimizer.optimize();
    assertEquals(result.getOutcome(), repeated.getOutcome());
    assertEquals(result.getEvaluationCount(), repeated.getEvaluationCount());
    assertArrayEquals(result.getBestFeasibleCandidate().getCandidateValues(),
        repeated.getBestFeasibleCandidate().getCandidateValues(), 1.0e-10);
    assertEquals(result.getBestFeasibleCandidate().getRawObjective(),
        repeated.getBestFeasibleCandidate().getRawObjective(), 1.0e-9);
  }

  /** Verifies evaluation-budget evidence without weakening the best feasible result. */
  @Test
  void reportsBudgetExhaustionWithBestFeasibleCandidate() {
    AllocationFixture fixture = createFixture();
    ProcessModelAllocationOptimizer optimizer = createOptimizer(fixture).setMaximumEvaluations(2);

    AllocationSearchResult result = optimizer.optimize();

    assertEquals(SearchOutcome.BUDGET_EXHAUSTED_WITH_FEASIBLE_CANDIDATE, result.getOutcome());
    assertFalse(result.isConverged());
    assertTrue(result.isModelRecovered());
    assertEquals(2, result.getEvaluationCount());
    assertArrayEquals(new double[] { 600.0, 400.0 }, result.getBestFeasibleCandidate().getCandidateValues(), 1.0e-10);
  }

  /** Verifies immutable Java serialization and frozen objective identity for JPype consumers. */
  @Test
  void serializesImmutableTraceAndFreezesMetadata() throws Exception {
    AllocationFixture fixture = createFixture();
    AllocationSearchResult original = createOptimizer(fixture).optimize();
    fixture.evaluator.getSimulationEvaluator().getObjectives().get(0).setName("mutated later");
    fixture.evaluator.getSimulationEvaluator().getConstraints().get(0).setName("mutated constraint later");
    fixture.evaluator.getSimulationEvaluator().getConstraints().get(0).getCapturedCapacityConstraint().setUnit("t/day")
        .setDataSource("mutated later");

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(original);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    AllocationSearchResult restored = (AllocationSearchResult) input.readObject();
    input.close();

    assertEquals("allocation value proxy", restored.getObjective().getName());
    CandidateObjectiveEvidence objectiveEvidence = restored.getBestFeasibleCandidate().getEvaluation()
        .getObjectiveEvidence().get(0);
    assertEquals("allocation value proxy", objectiveEvidence.getName());
    assertEquals(1700.0, objectiveEvidence.getRawValue(), 1.0e-8);
    CandidateConstraintEvidence constraintEvidence = restored.getBestSampledObjectiveCandidate().getEvaluation()
        .getConstraintEvidence().get(0);
    assertFalse("mutated constraint later".equals(constraintEvidence.getName()));
    assertEquals("synthetic installed-capacity allocation basis", restored.getProvenance());
    double[] allocation = restored.getBestFeasibleCandidate().getCandidateValues();
    allocation[0] = -1.0;
    assertArrayEquals(new double[] { 700.0, 300.0 }, restored.getBestFeasibleCandidate().getCandidateValues(), 1.0e-10);
    double[] lowerBounds = restored.getLowerBounds();
    lowerBounds[0] = -1.0;
    assertArrayEquals(new double[] { 200.0, 200.0 }, restored.getLowerBounds(), 0.0);
    assertNotSame(restored.getCandidates(), restored.getCandidates());
    assertNotSame(restored.getDiagnostics(), restored.getDiagnostics());
    assertNotSame(restored.getRankedHydraulicConstraintsAtBestFeasible(),
        restored.getRankedHydraulicConstraintsAtBestFeasible());
    assertNotSame(restored.getInstalledCapacityEvidenceAtBestFeasible(),
        restored.getInstalledCapacityEvidenceAtBestFeasible());
    InstalledEquipmentCapacityEvidence restoredCapacity = restored.getInstalledCapacityEvidenceAtBestFeasible().get(0);
    assertEquals("kg/hr", restoredCapacity.getPhysicalUnit());
    assertFalse("mutated later".equals(restoredCapacity.getDataSource()));
    assertThrows(UnsupportedOperationException.class, () -> restored.getCandidates().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getDiagnostics().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> restored.getRankedHydraulicConstraintsAtBestFeasible().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> restored.getInstalledCapacityEvidenceAtBestFeasible().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> restored.getBestFeasibleCandidate().getEvaluation().getInstalledEquipmentCapacityEvidence().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> restored.getBestFeasibleCandidate().getEvaluation().getObjectiveEvidence().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> restored.getBestFeasibleCandidate().getEvaluation().getConstraintEvidence().clear());
  }

  /** Verifies exact, direction-aware, in-unit bottleneck relief without rerunning the simulator. */
  @Test
  void identifiesTraceQualifiedBottleneckRelief() throws Exception {
    AllocationFixture fixture = createFixture();
    ConstraintDefinition softConstraint = new ConstraintDefinition("soft diagnostic", model -> 0.0, 1.0);
    softConstraint.setHard(false);
    softConstraint.setUnit("diagnostic-unit");
    fixture.evaluator.getSimulationEvaluator().getConstraints().add(softConstraint);
    AllocationSearchResult search = createOptimizer(fixture).optimize();
    ProcessModelAllocationBottleneckAnalyzer analyzer = new ProcessModelAllocationBottleneckAnalyzer(
        "allocation-relief", "Allocation bottleneck relief", "synthetic sampled-trace validation");

    BottleneckAnalysisResult result = analyzer.analyze(search);

    assertEquals(AnalysisOutcome.OPPORTUNITIES_IDENTIFIED, result.getOutcome());
    assertEquals("producer-allocation", result.getSourceSearchId());
    assertEquals("allocation-actions", result.getActionSetId());
    BottleneckReliefOpportunity leading = result.getOpportunities().get(0);
    assertEquals(100.0, leading.getObjectiveGain(), 1.0e-8);
    assertEquals("value-unit/hr", leading.getObjective().getUnit());
    assertArrayEquals(new double[] { 800.0, 200.0 }, leading.getCandidateValues(), 1.0e-10);
    assertArrayEquals(new double[] { 100.0, -100.0 }, leading.getActionDeltasFromBestFeasible(), 1.0e-10);
    assertEquals(EvidenceClass.ISOLATED, leading.getEvidenceClass());
    assertEquals(1, leading.getConstraintRelief().size(), "soft constraint must not be reported");
    assertEquals("producer A installed rate",
        leading.getConstraintRelief().get(0).getConstraint().getEquipmentConstraintName());
    assertEquals(100.0, leading.getConstraintRelief().get(0).getRequiredMarginRelief(), 1.0e-8);
    assertEquals("kg/hr", leading.getConstraintRelief().get(0).getUnit());
    assertTrue(leading.getConstraintRelief().get(0).isDerivedFromInstalledCapacityEvidence());
    assertTrue(leading.getConstraintRelief().get(0).isDerivedFromHydraulicEvidence(),
        "compatibility alias must retain the exact-evidence meaning");
    assertEquals("gathering::allocation sink/producer A installed rate",
        leading.getConstraintRelief().get(0).getInstalledCapacityEvidence().getQualifiedConstraintName());
    assertEquals(100.0, leading.getConstraintRelief().get(0).getInstalledCapacityEvidence().getRequiredRelief(),
        1.0e-8);
    assertEquals(3, leading.getInstalledCapacityEvidence().size());
    assertEquals("allocation value proxy", leading.getObjectiveEvidence().getName());
    assertFalse(leading.isAcceptedAsIncumbent());
    assertEquals(500.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(500.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);

    BottleneckAnalysisResult repeated = analyzer.analyze(search);
    assertEquals(result.getOutcome(), repeated.getOutcome());
    assertEquals(result.getOpportunities().size(), repeated.getOpportunities().size());
    assertEquals(result.getOpportunities().get(0).getObjectiveGain(),
        repeated.getOpportunities().get(0).getObjectiveGain(), 0.0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(result);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    BottleneckAnalysisResult restored = (BottleneckAnalysisResult) input.readObject();
    input.close();
    double[] candidateValues = restored.getOpportunities().get(0).getCandidateValues();
    candidateValues[0] = -1.0;
    assertArrayEquals(new double[] { 800.0, 200.0 }, restored.getOpportunities().get(0).getCandidateValues(), 0.0);
    assertNotSame(restored.getOpportunities(), restored.getOpportunities());
    assertThrows(UnsupportedOperationException.class, () -> restored.getOpportunities().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> restored.getOpportunities().get(0).getConstraintRelief().clear());
  }

  /** Verifies coupled, evidence-limited, minimize-direction, and no-feasible classifications. */
  @Test
  void classifiesTraceEvidenceWithoutCausalClaims() {
    ProcessModelAllocationBottleneckAnalyzer analyzer = new ProcessModelAllocationBottleneckAnalyzer(
        "allocation-relief", "Allocation bottleneck relief", "synthetic sampled-trace validation");

    AllocationFixture coupledFixture = createFixture();
    coupledFixture.evaluator.getSimulationEvaluator().addConstraintUpperBound("producer A operating ceiling",
        model -> coupledFixture.producerA.getFlowRate("kg/hr"), 750.0);
    coupledFixture.evaluator.getSimulationEvaluator().getConstraints()
        .get(coupledFixture.evaluator.getSimulationEvaluator().getConstraintCount() - 1).setUnit("kg/hr");
    BottleneckAnalysisResult coupled = analyzer.analyze(createOptimizer(coupledFixture).optimize());
    assertEquals(EvidenceClass.COUPLED, coupled.getOpportunities().get(0).getEvidenceClass());
    assertEquals(2, coupled.getOpportunities().get(0).getConstraintRelief().size());

    AllocationFixture limitedFixture = createFixture();
    for (ConstraintDefinition constraint : limitedFixture.evaluator.getSimulationEvaluator().getConstraints()) {
      if ("producer A installed rate".equals(constraint.getEquipmentConstraintName())) {
        constraint.getCapturedCapacityConstraint().setValidityRange(200.0, 700.0);
      }
    }
    BottleneckAnalysisResult limited = analyzer.analyze(createOptimizer(limitedFixture).optimize());
    assertEquals(EvidenceClass.EVIDENCE_LIMITED, limited.getOpportunities().get(0).getEvidenceClass());

    AllocationFixture minimizeFixture = createFixture();
    ObjectiveDefinition minimizeObjective = minimizeFixture.evaluator.getSimulationEvaluator().getObjectives().get(0);
    minimizeObjective.setDirection(ObjectiveDefinition.Direction.MINIMIZE);
    minimizeObjective.setEvaluator(model -> -2.0 * minimizeFixture.producerA.getFlowRate("kg/hr")
        - minimizeFixture.producerB.getFlowRate("kg/hr"));
    BottleneckAnalysisResult minimize = analyzer.analyze(createOptimizer(minimizeFixture).optimize());
    assertEquals(ObjectiveDefinition.Direction.MINIMIZE, minimize.getObjective().getDirection());
    assertEquals(100.0, minimize.getOpportunities().get(0).getObjectiveGain(), 1.0e-8);

    AllocationFixture infeasibleFixture = createFixture();
    infeasibleFixture.evaluator.getSimulationEvaluator().addConstraintUpperBound("impossible hard limit", model -> 1.0,
        0.0);
    BottleneckAnalysisResult infeasible = analyzer.analyze(createOptimizer(infeasibleFixture).optimize());
    assertEquals(AnalysisOutcome.NO_FEASIBLE_BASELINE, infeasible.getOutcome());
    assertTrue(infeasible.getOpportunities().isEmpty());
    assertTrue(infeasible.getDiagnostics().get(0).contains("not causal attribution"));
  }

  /** Verifies fail-fast unit, domain, fixed-total, and configuration contracts. */
  @Test
  void validatesAllocationDomainBeforeSimulation() {
    AllocationFixture fixture = createFixture();
    assertThrows(IllegalArgumentException.class, () -> new ProcessModelAllocationOptimizer("allocation", "Allocation",
        "basis", fixture.evaluator, 2500.0, "kg/hr"));
    assertThrows(IllegalArgumentException.class, () -> new ProcessModelAllocationOptimizer("allocation", "Allocation",
        "basis", fixture.evaluator, 1000.0, "Sm3/day"));
    ProcessModelAllocationOptimizer optimizer = createOptimizer(fixture);
    assertThrows(IllegalArgumentException.class, () -> optimizer.setInitialAllocation(new double[] { 700.0, 400.0 }));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setObjectiveIndex(1));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setMaximumEvaluations(0));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setInitialStepFraction(0.0));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setRelativeStepTolerance(0.0));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setObjectiveImprovementTolerance(-1.0, "invalid"));
  }
}
