package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.CandidateEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicConstraintSnapshot;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.Outcome;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for {@link ProcessModelOperatingActionEvaluator}. */
class ProcessModelOperatingActionEvaluatorTest {
  /** Small deterministic gathering/process fixture. */
  private static final class GatheringFixture {
    /** Writable model feed. */
    private final Stream feed;

    /** Installed gathering constraint. */
    private final CapacityConstraint gatheringCapacity;

    /** Full process model. */
    private final ProcessModel model;

    /** Creates the fixture. */
    private GatheringFixture(Stream feed, CapacityConstraint gatheringCapacity, ProcessModel model) {
      this.feed = feed;
      this.gatheringCapacity = gatheringCapacity;
      this.model = model;
    }
  }

  /** Creates a two-area model with one synthetic installed gathering limit. */
  private GatheringFixture createGatheringFixture(double designRate, double validityMaximum) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1000.0, "kg/hr");

    Stream feed = new Stream("feed", fluid);
    ThrottlingValve choke = new ThrottlingValve("gathering choke", feed);
    choke.setOutletPressure(30.0, "bara");
    Separator separator = new Separator("inlet separator", choke.getOutletStream());
    CapacityConstraint gatheringCapacity = new CapacityConstraint("installed gathering rate", "kg/hr",
        ConstraintType.HARD).setDesignValue(designRate).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic installed flowline basis").setConfidence(0.90)
        .setValidityRange(500.0, validityMaximum).setValueSupplier(() -> feed.getFlowRate("kg/hr"));
    separator.clearCapacityConstraints();
    separator.addCapacityConstraint(gatheringCapacity);

    ProcessSystem wells = new ProcessSystem("wells");
    wells.add(feed);
    wells.add(choke);
    ProcessSystem gathering = new ProcessSystem("gathering");
    gathering.add(separator);
    ProcessModel model = new ProcessModel();
    model.add("wells", wells);
    model.add("gathering", gathering);
    model.run();
    return new GatheringFixture(feed, gatheringCapacity, model);
  }

  /** Creates an evaluator for the fixture's one action and exact installed gathering constraint. */
  private ProcessModelOperatingActionEvaluator createGatheringEvaluator(GatheringFixture fixture) {
    ProcessModelSimulationEvaluator simulationEvaluator = new ProcessModelSimulationEvaluator(fixture.model);
    simulationEvaluator.setIncludeStrategyCapacityConstraints(false);
    simulationEvaluator.addObjective("field feed", model -> fixture.feed.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    ProcessModelOperatingAction action = ProcessModelOperatingAction.continuous("field-feed", "Field feed target",
        "wells::feed.flowRate", 500.0, 1500.0, "kg/hr", "synthetic operating envelope");
    return new ProcessModelOperatingActionEvaluator(simulationEvaluator, action).requireHydraulicConstraint(
        HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "inlet separator", "installed gathering rate",
        "synthetic installed gathering constraint selected for candidate screening");
  }

  /** Verifies feasible and violated nearby points are distinct and every call restores the baseline. */
  @Test
  void evaluatesNearbyGatheringPointsAndRestoresBaseline() {
    GatheringFixture fixture = createGatheringFixture(1200.0, 1400.0);
    ProcessModelOperatingActionEvaluator evaluator = createGatheringEvaluator(fixture);

    CandidateEvaluationResult feasible = evaluator.evaluate(1100.0);
    assertEquals(Outcome.FEASIBLE, feasible.getOutcome());
    assertTrue(feasible.isFeasible());
    assertTrue(feasible.isCandidateSimulationConverged());
    assertTrue(feasible.isCandidateEvaluatorFeasible());
    assertTrue(feasible.isBaselineRestored());
    assertTrue(feasible.isBaselineSimulationConverged());
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
    HydraulicConstraintSnapshot feasibleConstraint = feasible.getHydraulicConstraints().get(0);
    assertEquals(1100.0 / 1200.0, feasibleConstraint.getUtilization(), 1.0e-12);
    assertEquals(1.0 - 1100.0 / 1200.0, feasibleConstraint.getMargin(), 1.0e-12);
    assertEquals("kg/hr", feasibleConstraint.getUnit());
    assertEquals("synthetic installed flowline basis", feasibleConstraint.getDataSource());
    assertTrue(feasibleConstraint.hasConfidence());
    assertEquals(0.90, feasibleConstraint.getConfidence(), 0.0);

    CandidateEvaluationResult violated = evaluator.evaluate(1300.0);
    assertEquals(Outcome.HYDRAULIC_CONSTRAINT_VIOLATED, violated.getOutcome());
    assertFalse(violated.isFeasible());
    assertTrue(violated.isCandidateSimulationConverged());
    assertFalse(violated.isCandidateEvaluatorFeasible());
    assertEquals(1300.0 / 1200.0, violated.getHydraulicConstraints().get(0).getUtilization(), 1.0e-12);
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);

    CandidateEvaluationResult repeated = evaluator.evaluate(1100.0);
    assertEquals(feasible.getOutcome(), repeated.getOutcome());
    assertEquals(feasibleConstraint.getUtilization(), repeated.getHydraulicConstraints().get(0).getUtilization(), 0.0,
        "repeated candidate evaluation must be deterministic");
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Verifies invalid candidates, missing exact names, and out-of-range evidence fail closed. */
  @Test
  void rejectsInvalidMissingAndOutOfValidityCandidates() {
    GatheringFixture invalidFixture = createGatheringFixture(1200.0, 1400.0);
    CandidateEvaluationResult invalid = createGatheringEvaluator(invalidFixture).evaluate(1600.0);
    assertEquals(Outcome.ACTION_REJECTED, invalid.getOutcome());
    assertFalse(invalid.isCandidateSimulationConverged());
    assertTrue(invalid.isBaselineRestored());
    assertEquals(1000.0, invalidFixture.feed.getFlowRate("kg/hr"), 1.0e-8);

    GatheringFixture missingFixture = createGatheringFixture(1200.0, 1400.0);
    ProcessModelSimulationEvaluator missingSimulation = new ProcessModelSimulationEvaluator(missingFixture.model);
    missingSimulation.setIncludeStrategyCapacityConstraints(false);
    ProcessModelOperatingAction missingAction = ProcessModelOperatingAction.continuous("field-feed", "Field feed",
        "wells::feed.flowRate", 500.0, 1500.0, "kg/hr", "synthetic operating envelope");
    CandidateEvaluationResult missing = new ProcessModelOperatingActionEvaluator(missingSimulation, missingAction)
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "inlet separator",
            "wrong exact name", "negative exact-identity regression")
        .evaluate(1100.0);
    assertEquals(Outcome.REQUIRED_CONSTRAINT_MISSING, missing.getOutcome());
    assertFalse(missing.getHydraulicConstraints().get(0).isPresent());
    assertEquals(1000.0, missingFixture.feed.getFlowRate("kg/hr"), 1.0e-8);

    GatheringFixture validityFixture = createGatheringFixture(1500.0, 1200.0);
    CandidateEvaluationResult outside = createGatheringEvaluator(validityFixture).evaluate(1300.0);
    assertEquals(Outcome.EVIDENCE_OUTSIDE_VALIDITY_RANGE, outside.getOutcome());
    assertTrue(outside.isCandidateEvaluatorFeasible());
    assertFalse(outside.isFeasible());
    assertEquals(ProcessModelSimulationEvaluator.BottleneckStatus.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE,
        outside.getHydraulicConstraints().get(0).getEvidenceApplicability());
    assertEquals(1000.0, validityFixture.feed.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Verifies result serialization, defensive arrays, and JPype-friendly immutable lists. */
  @Test
  void resultIsSerializableAndDefensivelyImmutable() throws Exception {
    GatheringFixture fixture = createGatheringFixture(1200.0, 1400.0);
    CandidateEvaluationResult original = createGatheringEvaluator(fixture).evaluate(1100.0);

    double[] objectives = original.getRawObjectives();
    objectives[0] = -1.0;
    assertEquals(1100.0, original.getRawObjectives()[0], 1.0e-8);
    assertThrows(UnsupportedOperationException.class, () -> original.getDiagnostics().clear());
    assertThrows(UnsupportedOperationException.class, () -> original.getHydraulicConstraints().clear());
    assertNotSame(original.getDiagnostics(), original.getDiagnostics());
    assertNotSame(original.getHydraulicConstraints(), original.getHydraulicConstraints());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(original);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    CandidateEvaluationResult restored = (CandidateEvaluationResult) input.readObject();
    input.close();

    assertEquals(Outcome.FEASIBLE, restored.getOutcome());
    assertEquals("field-feed", restored.getAction().getId());
    assertEquals("synthetic operating envelope", restored.getAction().getProvenance());
    assertEquals(HydraulicLimitRole.GATHERING_HYDRAULICS,
        restored.getHydraulicConstraints().get(0).getBinding().getRole());
    assertEquals("synthetic installed gathering constraint selected for candidate screening",
        restored.getHydraulicConstraints().get(0).getBinding().getProvenance());
  }

  /** Verifies the wrapper owns candidate application and rejects an evaluator with independent parameters. */
  @Test
  void rejectsEvaluatorWithCompetingParameters() {
    GatheringFixture fixture = createGatheringFixture(1200.0, 1400.0);
    ProcessModelSimulationEvaluator simulationEvaluator = new ProcessModelSimulationEvaluator(fixture.model);
    simulationEvaluator.addParameter("wells::feed.flowRate", 500.0, 1500.0, "kg/hr");
    ProcessModelOperatingAction action = ProcessModelOperatingAction.continuous("field-feed", "Field feed",
        "wells::feed.flowRate", 500.0, 1500.0, "kg/hr", "synthetic operating envelope");

    assertThrows(IllegalStateException.class,
        () -> new ProcessModelOperatingActionEvaluator(simulationEvaluator, action));
  }

  /**
   * Verifies the action evaluator consumes a real WellFlow drawdown constraint without replacing the well inflow
   * calculation.
   */
  @Test
  void evaluatesRealWellDrawdownAndRestoresReservoirModel() {
    SystemInterface fluid = new SystemPrEos(373.15, 100.0);
    fluid.addComponent("water", 3.599);
    fluid.addComponent("nitrogen", 0.599);
    fluid.addComponent("CO2", 0.51);
    fluid.addComponent("methane", 62.8);
    fluid.addComponent("n-heptane", 12.8);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);

    SimpleReservoir reservoir = new SimpleReservoir("reservoir");
    reservoir.setReservoirFluid(fluid, 1.0e9, 10.0, 1.0e8);
    StreamInterface producer = reservoir.addGasProducer("producer");
    producer.setName("producer");
    producer.setFlowRate(1.0, "MSm3/day");
    WellFlow well = new WellFlow("well");
    well.setInletStream(producer);
    well.setWellProductionIndex(5.0e-4);
    ProcessSystem subsurface = new ProcessSystem("subsurface");
    subsurface.add(reservoir);
    subsurface.add(producer);
    subsurface.add(well);
    subsurface.run();
    double baselineDrawdown = well.getDrawdown();
    well.setMaxDrawdown(baselineDrawdown * 1.10, "bara");
    well.useWellConstraints();

    ProcessModel model = new ProcessModel();
    model.add("Subsurface", subsurface);
    model.run();
    double baselineRate = producer.getFlowRate("kg/hr");
    ProcessModelSimulationEvaluator simulationEvaluator = new ProcessModelSimulationEvaluator(model);
    simulationEvaluator.setIncludeStrategyCapacityConstraints(false);
    double readBackTolerance = baselineRate * 1.0e-6;
    String readBackToleranceProvenance = "One part per million of the baseline rate for Stream mass-flow conversion read-back";
    ProcessModelOperatingAction action = ProcessModelOperatingAction
        .continuous("well-rate", "Producer gas rate", "Subsurface::producer.flowRate", 0.5 * baselineRate,
            1.5 * baselineRate, "kg/hr", "synthetic well operating envelope")
        .withReadBackTolerance(readBackTolerance, 0.0, readBackToleranceProvenance);
    ProcessModelOperatingActionEvaluator evaluator = new ProcessModelOperatingActionEvaluator(simulationEvaluator,
        action).requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Subsurface", "well",
            "well drawdown", "WellFlow installed maximum drawdown");

    CandidateEvaluationResult lowerRate = evaluator.evaluate(0.8 * baselineRate);
    CandidateEvaluationResult higherRate = evaluator.evaluate(1.2 * baselineRate);

    assertEquals(Outcome.FEASIBLE, lowerRate.getOutcome(), lowerRate.getDiagnostics().toString());
    assertEquals(readBackTolerance, lowerRate.getAction().getReadBackAbsoluteTolerance(), 0.0);
    assertEquals(0.0, lowerRate.getAction().getReadBackRelativeTolerance(), 0.0);
    assertEquals(readBackToleranceProvenance, lowerRate.getAction().getReadBackToleranceProvenance());
    assertTrue(lowerRate.getDiagnostics().get(0).contains("absolute residual="), lowerRate.getDiagnostics().toString());
    assertTrue(lowerRate.getDiagnostics().get(0).contains("tolerance provenance=" + readBackToleranceProvenance),
        lowerRate.getDiagnostics().toString());
    assertEquals(Outcome.HYDRAULIC_CONSTRAINT_VIOLATED, higherRate.getOutcome(),
        higherRate.getDiagnostics().toString());
    assertTrue(lowerRate.getHydraulicConstraints().get(0).getUtilization() < higherRate.getHydraulicConstraints().get(0)
        .getUtilization());
    assertEquals(baselineRate, producer.getFlowRate("kg/hr"), readBackTolerance);
    assertEquals(baselineDrawdown, well.getDrawdown(), 1.0e-6);
    assertTrue(higherRate.isBaselineRestored());
    assertTrue(higherRate.isBaselineSimulationConverged());
  }
}
