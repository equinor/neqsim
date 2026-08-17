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
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.ActionCandidateEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.Outcome;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for {@link ProcessModelOperatingActionSetEvaluator}. */
class ProcessModelOperatingActionSetEvaluatorTest {
  /** Controlled dual-producer fixture. */
  private static final class DualProducerFixture {
    /** First producer. */
    private final Stream producerA;

    /** Second producer. */
    private final Stream producerB;

    /** Full process model. */
    private final ProcessModel model;

    /** Creates a fixture. */
    private DualProducerFixture(Stream producerA, Stream producerB, ProcessModel model) {
      this.producerA = producerA;
      this.producerB = producerB;
      this.model = model;
    }
  }

  /** Creates two independently addressable producers and one shared installed gathering limit. */
  private DualProducerFixture createDualProducerFixture() {
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
    return new DualProducerFixture(producerA, producerB, model);
  }

  /** Creates the coupled evaluator for the controlled fixture. */
  private ProcessModelOperatingActionSetEvaluator createCoupledEvaluator(DualProducerFixture fixture) {
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
        model -> ProcessBoundaryConstraintEvidence.Sample.available(
            fixture.producerA.getFlowRate("kg/hr") + fixture.producerB.getFlowRate("kg/hr")),
        ProcessModelSimulationEvaluator.ConstraintDefinition.Type.UPPER_BOUND, Double.NEGATIVE_INFINITY, 2000.0,
        0.0, "kg/hr", true, 10.0, 1000.0);
    ProcessModelOperatingAction actionA = ProcessModelOperatingAction.continuous("producer-a-rate", "Producer A rate",
        "wells::producer A.flowRate", 200.0, 1000.0, "kg/hr", "synthetic producer A envelope");
    ProcessModelOperatingAction actionB = ProcessModelOperatingAction.continuous("producer-b-rate", "Producer B rate",
        "wells::producer B.flowRate", 200.0, 1000.0, "kg/hr", "synthetic producer B envelope");
    return new ProcessModelOperatingActionSetEvaluator("well-allocation", "Coupled well allocation",
        "synthetic two-well allocation basis", simulation, Arrays.asList(actionA, actionB))
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "gathering", "gathering sink",
            "installed gathering rate", "shared installed gathering capacity");
  }

  /** Verifies redistribution, overload evidence, deterministic repetition, and complete baseline recovery. */
  @Test
  void evaluatesCoupledCandidatesAndRestoresEveryAction() {
    DualProducerFixture fixture = createDualProducerFixture();
    ProcessModelOperatingActionSetEvaluator evaluator = createCoupledEvaluator(fixture);

    CandidateSetEvaluationResult redistributed = evaluator.evaluate(new double[] { 700.0, 300.0 });
    assertEquals(Outcome.FEASIBLE, redistributed.getOutcome(), redistributed.getDiagnostics().toString());
    assertTrue(redistributed.isFeasible());
    assertTrue(redistributed.isCandidateSimulationConverged());
    assertTrue(redistributed.isCandidateEvaluatorFeasible());
    assertTrue(redistributed.isBaselineRestored());
    assertTrue(redistributed.isBaselineSimulationConverged());
    assertArrayEquals(new double[] { 600.0, 400.0 }, redistributed.getBaselineValues(), 1.0e-8);
    assertArrayEquals(new double[] { 700.0, 300.0 }, redistributed.getCandidateValues(), 0.0);
    assertEquals(1000.0, redistributed.getRawObjectives()[0], 1.0e-8);
    assertEquals(1, redistributed.getProcessBoundaryConstraintEvidence().size());
    assertEquals(1000.0, redistributed.getProcessBoundaryConstraintEvidence().get(0).getSampledValue(), 1.0e-8);
    assertEquals(1000.0 / 1200.0, redistributed.getHydraulicConstraints().get(0).getUtilization(), 1.0e-12);
    assertEquals(1.0 - 1000.0 / 1200.0, redistributed.getHydraulicConstraints().get(0).getMargin(), 1.0e-12);
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
    for (ActionCandidateEvidence evidence : redistributed.getActionEvidence()) {
      assertTrue(evidence.isApplicationAttempted());
      assertTrue(evidence.isApplied());
      assertTrue(evidence.isRestorationAttempted());
      assertTrue(evidence.isRestored());
      assertEquals(0.0, evidence.getReadBackResidual(), 1.0e-8);
      assertEquals(0.0, evidence.getRestorationReadBackResidual(), 1.0e-8);
    }

    CandidateSetEvaluationResult overloaded = evaluator.evaluate(new double[] { 800.0, 500.0 });
    assertEquals(Outcome.HYDRAULIC_CONSTRAINT_VIOLATED, overloaded.getOutcome(),
        overloaded.getDiagnostics().toString());
    assertEquals(1300.0 / 1200.0, overloaded.getHydraulicConstraints().get(0).getUtilization(), 1.0e-12);
    assertEquals(1.0 - 1300.0 / 1200.0, overloaded.getHydraulicConstraints().get(0).getMargin(), 1.0e-12);
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);

    CandidateSetEvaluationResult repeated = evaluator.evaluate(new double[] { 700.0, 300.0 });
    assertEquals(redistributed.getOutcome(), repeated.getOutcome());
    assertArrayEquals(redistributed.getRawObjectives(), repeated.getRawObjectives(), 1.0e-9);
    assertEquals(redistributed.getHydraulicConstraints().get(0).getUtilization(),
        repeated.getHydraulicConstraints().get(0).getUtilization(), 1.0e-12);
  }

  /** Verifies a later rejected action never reaches simulation and all prior actions are recovered. */
  @Test
  void rejectsPartialCandidateWithoutSimulationAndRollsBackPriorAction() {
    DualProducerFixture fixture = createDualProducerFixture();
    ProcessModelOperatingActionSetEvaluator evaluator = createCoupledEvaluator(fixture);
    int evaluationsBefore = evaluator.getSimulationEvaluator().getEvaluationCount();

    CandidateSetEvaluationResult rejected = evaluator.evaluate(new double[] { 700.0, 1200.0 });

    assertEquals(Outcome.ACTION_REJECTED, rejected.getOutcome(), rejected.getDiagnostics().toString());
    assertFalse(rejected.isCandidateSimulationConverged());
    assertEquals(evaluationsBefore, evaluator.getSimulationEvaluator().getEvaluationCount());
    assertTrue(rejected.isBaselineRestored());
    assertTrue(rejected.isBaselineSimulationConverged());
    assertTrue(rejected.getActionEvidence().get(0).isApplied());
    assertFalse(rejected.getActionEvidence().get(1).isApplied());
    assertTrue(rejected.getActionEvidence().get(0).isRestored());
    assertTrue(rejected.getActionEvidence().get(1).isRestored());
    assertEquals(600.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Verifies identity/address validation and Java/JPype-oriented immutable result behavior. */
  @Test
  void validatesIdentityAndSerializesDefensiveEvidence() throws Exception {
    DualProducerFixture fixture = createDualProducerFixture();
    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    ProcessModelOperatingAction actionA = ProcessModelOperatingAction.continuous("rate", "Rate A",
        "wells::producer A.flowRate", 200.0, 1000.0, "kg/hr", "basis A");
    ProcessModelOperatingAction duplicateId = ProcessModelOperatingAction.continuous("rate", "Rate B",
        "wells::producer B.flowRate", 200.0, 1000.0, "kg/hr", "basis B");
    ProcessModelOperatingAction duplicateAddress = ProcessModelOperatingAction.continuous("other", "Rate A duplicate",
        "wells::producer A.flowRate", 200.0, 1000.0, "kg/hr", "basis C");

    assertThrows(IllegalArgumentException.class, () -> new ProcessModelOperatingActionSetEvaluator("set", "Set",
        "basis", simulation, Arrays.asList(actionA, duplicateId)));
    assertThrows(IllegalArgumentException.class, () -> new ProcessModelOperatingActionSetEvaluator("set", "Set",
        "basis", simulation, Arrays.asList(actionA, duplicateAddress)));

    ProcessModelOperatingActionSetEvaluator evaluator = createCoupledEvaluator(fixture);
    assertEquals(Outcome.CANDIDATE_VECTOR_INVALID, evaluator.evaluate(new double[] { 700.0 }).getOutcome());
    CandidateSetEvaluationResult original = evaluator.evaluate(new double[] { 700.0, 300.0 });
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(original);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    CandidateSetEvaluationResult restored = (CandidateSetEvaluationResult) input.readObject();
    input.close();

    assertEquals("well-allocation", restored.getId());
    assertEquals("synthetic two-well allocation basis", restored.getProvenance());
    assertArrayEquals(new double[] { 700.0, 300.0 }, restored.getCandidateValues(), 0.0);
    double[] candidates = restored.getCandidateValues();
    candidates[0] = -1.0;
    assertArrayEquals(new double[] { 700.0, 300.0 }, restored.getCandidateValues(), 0.0);
    assertNotSame(restored.getActions(), restored.getActions());
    assertNotSame(restored.getActionEvidence(), restored.getActionEvidence());
    assertNotSame(restored.getHydraulicConstraints(), restored.getHydraulicConstraints());
    assertNotSame(restored.getProcessBoundaryConstraintEvidence(), restored.getProcessBoundaryConstraintEvidence());
    assertNotSame(restored.getDiagnostics(), restored.getDiagnostics());
    assertThrows(UnsupportedOperationException.class, () -> restored.getActions().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getActionEvidence().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getHydraulicConstraints().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> restored.getProcessBoundaryConstraintEvidence().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getDiagnostics().clear());
  }

  /** Verifies two real WellFlow constraints and one shared gathering constraint respond monotonically. */
  @Test
  void evaluatesTwoRealWellsAndSharedGatheringCapacity() {
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
    StreamInterface producerA = reservoir.addGasProducer("producer A");
    StreamInterface producerB = reservoir.addGasProducer("producer B");
    producerA.setName("producer A");
    producerB.setName("producer B");
    producerA.setFlowRate(0.6, "MSm3/day");
    producerB.setFlowRate(0.4, "MSm3/day");
    WellFlow wellA = new WellFlow("well A");
    WellFlow wellB = new WellFlow("well B");
    wellA.setInletStream(producerA);
    wellB.setInletStream(producerB);
    wellA.setWellProductionIndex(5.0e-4);
    wellB.setWellProductionIndex(5.0e-4);

    ProcessSystem subsurface = new ProcessSystem("subsurface");
    subsurface.add(reservoir);
    subsurface.add(producerA);
    subsurface.add(producerB);
    subsurface.add(wellA);
    subsurface.add(wellB);
    subsurface.run();
    double baselineDrawdownA = wellA.getDrawdown();
    double baselineDrawdownB = wellB.getDrawdown();
    wellA.setMaxDrawdown(baselineDrawdownA * 1.50, "bara");
    wellB.setMaxDrawdown(baselineDrawdownB * 1.50, "bara");
    wellA.useWellConstraints();
    wellB.useWellConstraints();

    Separator gatheringSink = new Separator("gathering sink", wellA.getOutletStream());
    double baselineRateA = producerA.getFlowRate("kg/hr");
    double baselineRateB = producerB.getFlowRate("kg/hr");
    double baselineTotal = baselineRateA + baselineRateB;
    CapacityConstraint sharedCapacity = new CapacityConstraint("installed gathering rate", "kg/hr", ConstraintType.HARD)
        .setDesignValue(1.10 * baselineTotal).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic installed capacity over two real WellFlow producers").setConfidence(0.90)
        .setValidityRange(0.5 * baselineTotal, 1.5 * baselineTotal)
        .setValueSupplier(() -> producerA.getFlowRate("kg/hr") + producerB.getFlowRate("kg/hr"));
    gatheringSink.clearCapacityConstraints();
    gatheringSink.addCapacityConstraint(sharedCapacity);
    ProcessSystem gathering = new ProcessSystem("gathering");
    gathering.add(gatheringSink);
    ProcessModel model = new ProcessModel();
    model.add("Subsurface", subsurface);
    model.add("Gathering", gathering);
    model.run();

    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("combined well outlet mass rate",
        processModel -> wellA.getOutletStream().getFlowRate("kg/hr") + wellB.getOutletStream().getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    double toleranceA = baselineRateA * 1.0e-6;
    double toleranceB = baselineRateB * 1.0e-6;
    ProcessModelOperatingAction actionA = ProcessModelOperatingAction
        .continuous("well-a-rate", "Well A rate", "Subsurface::producer A.flowRate", 0.5 * baselineRateA,
            1.5 * baselineRateA, "kg/hr", "synthetic well A operating envelope")
        .withReadBackTolerance(toleranceA, 0.0, "one ppm Stream mass-flow conversion resolution");
    ProcessModelOperatingAction actionB = ProcessModelOperatingAction
        .continuous("well-b-rate", "Well B rate", "Subsurface::producer B.flowRate", 0.5 * baselineRateB,
            1.5 * baselineRateB, "kg/hr", "synthetic well B operating envelope")
        .withReadBackTolerance(toleranceB, 0.0, "one ppm Stream mass-flow conversion resolution");
    ProcessModelOperatingActionSetEvaluator evaluator = new ProcessModelOperatingActionSetEvaluator(
        "two-well-allocation", "Two-well allocation", "synthetic coupled allocation validation", simulation,
        Arrays.asList(actionA, actionB))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Subsurface", "well A", "well drawdown",
            "WellFlow A installed maximum drawdown")
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Subsurface", "well B", "well drawdown",
            "WellFlow B installed maximum drawdown")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Gathering", "gathering sink",
            "installed gathering rate", "shared installed gathering capacity");

    CandidateSetEvaluationResult lower = evaluator.evaluate(new double[] { 0.8 * baselineRateA, 0.8 * baselineRateB });
    CandidateSetEvaluationResult higher = evaluator.evaluate(new double[] { 1.2 * baselineRateA, 1.2 * baselineRateB });

    assertEquals(Outcome.FEASIBLE, lower.getOutcome(), lower.getDiagnostics().toString());
    assertEquals(Outcome.HYDRAULIC_CONSTRAINT_VIOLATED, higher.getOutcome(), higher.getDiagnostics().toString());
    assertTrue(lower.getHydraulicConstraints().get(0).getUtilization() < higher.getHydraulicConstraints().get(0)
        .getUtilization());
    assertTrue(lower.getHydraulicConstraints().get(1).getUtilization() < higher.getHydraulicConstraints().get(1)
        .getUtilization());
    assertEquals(1.2 * baselineTotal, higher.getHydraulicConstraints().get(2).getCurrentValue(),
        toleranceA + toleranceB);
    assertEquals(1.2 * baselineTotal, higher.getRawObjectives()[0], toleranceA + toleranceB,
        "combined WellFlow outlet mass must equal the requested producer mass rate");
    assertEquals(baselineRateA, producerA.getFlowRate("kg/hr"), toleranceA);
    assertEquals(baselineRateB, producerB.getFlowRate("kg/hr"), toleranceB);
    assertEquals(baselineDrawdownA, wellA.getDrawdown(), 1.0e-6);
    assertEquals(baselineDrawdownB, wellB.getDrawdown(), 1.0e-6);
    assertTrue(higher.isBaselineRestored());
    assertTrue(higher.isBaselineSimulationConverged());
  }
}
