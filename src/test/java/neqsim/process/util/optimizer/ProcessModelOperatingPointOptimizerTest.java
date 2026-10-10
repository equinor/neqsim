package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingPointOptimizer.OperatingPointSearchResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for {@link ProcessModelOperatingPointOptimizer}. */
class ProcessModelOperatingPointOptimizerTest {
  /** Controlled mixed-action fixture. */
  private static final class Fixture {
    /** First producer. */
    private final Stream producerA;
    /** Second producer. */
    private final Stream producerB;
    /** Atomic mixed-action evaluator. */
    private final ProcessModelOperatingActionSetEvaluator evaluator;

    /** Creates one fixture holder. */
    private Fixture(Stream producerA, Stream producerB, ProcessModelOperatingActionSetEvaluator evaluator) {
      this.producerA = producerA;
      this.producerB = producerB;
      this.evaluator = evaluator;
    }
  }

  /** Creates two producers with one continuous action, one discrete action and installed limits. */
  private Fixture createFixture() {
    SystemInterface fluidA = new SystemSrkEos(298.15, 50.0);
    fluidA.addComponent("methane", 0.90);
    fluidA.addComponent("ethane", 0.10);
    fluidA.setMixingRule("classic");
    fluidA.setTotalFlowRate(500.0, "kg/hr");
    Stream producerA = new Stream("producer A", fluidA);
    Stream producerB = new Stream("producer B", fluidA.clone());
    Separator sink = new Separator("mixed action sink", producerA);
    sink.clearCapacityConstraints();
    sink.addCapacityConstraint(rateConstraint("producer A rate", 700.0, () -> producerA.getFlowRate("kg/hr"),
        "synthetic producer A envelope"));
    sink.addCapacityConstraint(rateConstraint("shared gathering rate", 1200.0,
        () -> producerA.getFlowRate("kg/hr") + producerB.getFlowRate("kg/hr"), "synthetic gathering envelope"));

    ProcessSystem wells = new ProcessSystem("wells");
    wells.add(producerA);
    wells.add(producerB);
    ProcessSystem gathering = new ProcessSystem("gathering");
    gathering.add(sink);
    ProcessModel model = new ProcessModel();
    model.add("wells", wells);
    model.add("gathering", gathering);
    model.run();

    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("mixed production value",
        processModel -> 2.0 * producerA.getFlowRate("kg/hr") + producerB.getFlowRate("kg/hr"),
        ObjectiveDefinition.Direction.MAXIMIZE);
    simulation.getObjectives().get(0).setUnit("value-unit/hr");
    ProcessModelOperatingAction continuous = ProcessModelOperatingAction.continuous("producer-a-rate",
        "Producer A rate", "wells::producer A.flowRate", 200.0, 800.0, "kg/hr",
        "synthetic producer A operating envelope");
    ProcessModelOperatingAction discrete = ProcessModelOperatingAction.discrete("producer-b-mode",
        "Producer B rate mode", "wells::producer B.flowRate", new double[] {300.0, 500.0}, "kg/hr",
        "synthetic producer B operating modes");
    ProcessModelOperatingActionSetEvaluator evaluator = new ProcessModelOperatingActionSetEvaluator("mixed-actions",
        "Mixed producer actions", "synthetic mixed-action search", simulation, Arrays.asList(continuous, discrete))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "gathering", "mixed action sink",
            "producer A rate", "synthetic producer A installed capacity")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "mixed action sink",
            "shared gathering rate", "synthetic shared gathering capacity");
    return new Fixture(producerA, producerB, evaluator);
  }

  /** Creates one deterministic hard rate constraint. */
  private CapacityConstraint rateConstraint(String name, double designValue,
      java.util.function.DoubleSupplier valueSupplier, String dataSource) {
    return new CapacityConstraint(name, "kg/hr", ConstraintType.HARD).setDesignValue(designValue)
        .setSeverity(ConstraintSeverity.HARD).setDataSource(dataSource).setConfidence(1.0).setValidityRange(0.0, 1500.0)
        .setValueSupplier(valueSupplier);
  }

  /** Creates the documented bounded mixed-action optimizer. */
  private ProcessModelOperatingPointOptimizer createOptimizer(Fixture fixture) {
    return new ProcessModelOperatingPointOptimizer("mixed-search", "Mixed search",
        "synthetic continuous and discrete operating basis", fixture.evaluator)
        .setInitialCandidate(new double[] {500.0, 500.0}).setInitialStepFraction(0.25).setRelativeStepTolerance(0.01)
        .setMaximumEvaluations(40)
        .setObjectiveImprovementTolerance(1.0e-8, "synthetic deterministic objective resolution");
  }

  /** Verifies mixed search, selected-point replay, guard evidence and exact baseline recovery. */
  @Test
  void searchesMixedActionsAndReplaysAcceptedPoint() {
    Fixture fixture = createFixture();

    OperatingPointSearchResult result = createOptimizer(fixture).optimize();

    assertTrue(result.isAcceptedPointReplayed(), result.getOutcome() + ": " + result.getDiagnostics());
    assertTrue(result.getAcceptedPointReplay().getEvaluation().isFeasible());
    assertTrue(result.getAcceptedPointReplay().getRawObjective() > result.getCandidates().get(0).getRawObjective());
    assertTrue(result.isCandidateFinite());
    assertTrue(result.isConstraintEvidenceComplete());
    assertTrue(result.isActionsComplete());
    assertTrue(result.getCandidates().stream().anyMatch(record -> record.getCandidateValues()[1] == 300.0));
    assertTrue(result.getEvaluationCount() <= result.getMaximumEvaluations());
    assertEquals(500.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(500.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);

    Map<String, Object> evidence = result.getOptimizerEvidence("cycle-42");
    assertEquals("cycle-42", evidence.get("cycle_id"));
    assertEquals(Boolean.TRUE, evidence.get("simulation_converged"));
    assertEquals(Boolean.TRUE, evidence.get("candidate_feasible"));
    assertEquals(Boolean.TRUE, evidence.get("candidate_finite"));
    assertEquals(Boolean.TRUE, evidence.get("constraint_evidence_complete"));
    assertEquals(Boolean.TRUE, evidence.get("state_restore_complete"));
    assertEquals(Boolean.TRUE, evidence.get("accepted_point_replayed"));
    assertEquals(Boolean.TRUE, evidence.get("actions_complete"));
    assertEquals(result.getEvaluationCount(), evidence.get("evaluation_count"));
    assertEquals(result.getRuntimeSeconds(), evidence.get("runtime_seconds"));
  }

  /** Verifies the hard budget reserves replay and exposes a defensive selected vector. */
  @Test
  void reservesFinalBudgetEvaluationForReplay() {
    Fixture fixture = createFixture();
    OperatingPointSearchResult result = createOptimizer(fixture).setMaximumEvaluations(2).optimize();

    assertTrue(result.isAcceptedPointReplayed());
    assertEquals(2, result.getEvaluationCount());
    assertFalse(result.isConverged());
    assertArrayEquals(new double[] {500.0, 500.0}, result.getAcceptedCandidateValues(), 0.0);
    double[] mutated = result.getAcceptedCandidateValues();
    mutated[0] = -1.0;
    assertArrayEquals(new double[] {500.0, 500.0}, result.getAcceptedCandidateValues(), 0.0);
  }

  /** Verifies fail-fast validation of discrete values, budgets and step configuration. */
  @Test
  void rejectsInvalidSearchDefinitions() {
    Fixture fixture = createFixture();
    ProcessModelOperatingPointOptimizer optimizer = createOptimizer(fixture);

    assertThrows(IllegalArgumentException.class, () -> optimizer.setInitialCandidate(new double[] {500.0, 400.0}));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setMaximumEvaluations(1));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setInitialStepFraction(0.0));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setRelativeStepTolerance(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setObjectiveImprovementTolerance(-1.0, "invalid"));
    assertThrows(IllegalStateException.class, () -> createOptimizer(fixture).setRelativeStepTolerance(0.25).optimize());
  }
}
