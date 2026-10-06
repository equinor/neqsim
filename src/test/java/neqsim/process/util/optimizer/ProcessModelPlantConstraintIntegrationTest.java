package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.Outcome;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.LeadingConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.SliceResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for typed plant/shared constraints in process-model candidates and operating envelopes.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModelPlantConstraintIntegrationTest {
  /** Mutable two-area compression fixture. */
  private static final class Fixture {
    private final ProcessModel model;
    private final Stream feedA;
    private final Stream feedB;
    private final double sharedPowerLimitKw;

    /** Creates one fixture holder. */
    private Fixture(ProcessModel model, Stream feedA, Stream feedB, double sharedPowerLimitKw) {
      this.model = model;
      this.feedA = feedA;
      this.feedB = feedB;
      this.sharedPowerLimitKw = sharedPowerLimitKw;
    }
  }

  /**
   * Verifies one shared-power sample is evaluated once and becomes the exact rejection identity in an envelope slice.
   */
  @Test
  void sharedPowerViolationBecomesEnvelopeRejectionIdentity() {
    Fixture fixture = createFixture();
    PlantConstraintDefinition sharedPower = sharedPowerDefinition();
    AtomicInteger sampleCalls = new AtomicInteger();

    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("total production",
        model -> fixture.feedA.getFlowRate("kg/hr") + fixture.feedB.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    simulation.addPlantConstraint(sharedPower, (model, calculationId) -> {
      sampleCalls.incrementAndGet();
      return PlantSharedResourceEvidence.fromProcessModelShaftPower(sharedPower, calculationId,
          fixture.sharedPowerLimitKw, model, model.isModelConverged(), "synthetic completed candidate")
          .toPlantConstraintSample();
    });

    ProcessModelOperatingAction rateA = ProcessModelOperatingAction.continuous("rate-a", "Producer A rate",
        "A::feed A.flowRate", 300.0, 1000.0, "kg/hr", "synthetic well A operating range");
    ProcessModelOperatingAction rateB = ProcessModelOperatingAction.continuous("rate-b", "Producer B rate",
        "B::feed B.flowRate", 300.0, 1000.0, "kg/hr", "synthetic well B operating range");
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator("field-actions",
        "Field actions", "synthetic field-to-host power acceptance", simulation, Arrays.asList(rateA, rateB))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "A", "feed A", "well deliverability",
            "synthetic well A limit")
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "B", "feed B", "well deliverability",
            "synthetic well B limit");
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("shared-power-slice",
        "Shared power slice", "synthetic field-to-host acceptance", candidates);

    SliceResult result = study.evaluateOneDimensional("rate-a", new double[] {400.0, 900.0},
        new double[] {400.0, 400.0});

    assertTrue(result.isComplete());
    assertEquals(2, sampleCalls.get(), "plant sampler must run once for each candidate, not once per constraint");
    assertTrue(result.getPoints().get(0).isFeasible());
    assertFalse(result.getPoints().get(1).isFeasible());
    assertEquals(Outcome.OTHER_MODEL_CONSTRAINT_VIOLATED, result.getPoints().get(1).getEvaluation().getOutcome());
    LeadingConstraintEvidence rejection = result.getPoints().get(1).getLeadingConstraint();
    assertEquals(LeadingConstraintEvidence.Source.PLANT_CONSTRAINT, rejection.getSource());
    assertEquals(sharedPower.getQualifiedId(), rejection.getQualifiedConstraintName());
    assertEquals("kW", rejection.getPhysicalUnit());
    assertTrue(rejection.getPhysicalMargin() < 0.0);
    assertEquals(1, result.getBottleneckTransitions().size());
    assertEquals(sharedPower.getQualifiedId(), result.getBottleneckTransitions().get(0).getToConstraint());
    assertEquals(400.0, fixture.feedA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.feedB.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Verifies a coupled group produces multiple typed rows from one exact callback. */
  @Test
  void groupedPlantConstraintsAreSampledOnceAndFailClosed() {
    Fixture fixture = createFixture();
    PlantConstraintDefinition power = sharedPowerDefinition();
    PlantConstraintDefinition hostRate = PlantConstraintDefinition
        .builder("host-rate", PlantConstraintScope.sharedResource("Plant", "host inlet"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SUM)
        .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
        .category(PlantConstraintDefinition.Category.OPERATING).severity(ConstraintSeverity.HARD).unit("kg/hr")
        .basis("mass rate").provenance("synthetic host operating limit")
        .participant(PlantConstraintParticipant.direct("A", "kg/hr", "mass rate"))
        .participant(PlantConstraintParticipant.direct("B", "kg/hr", "mass rate")).build();
    AtomicInteger sampleCalls = new AtomicInteger();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addPlantConstraintGroup("host-coupling", Arrays.asList(power, hostRate), (model, calculationId) -> {
      sampleCalls.incrementAndGet();
      double totalRate = fixture.feedA.getFlowRate("kg/hr") + fixture.feedB.getFlowRate("kg/hr");
      PlantConstraintSample powerSample = PlantSharedResourceEvidence.fromProcessModelShaftPower(power, calculationId,
          fixture.sharedPowerLimitKw * 2.0, model, model.isModelConverged(), "synthetic completed candidate")
          .toPlantConstraintSample();
      PlantConstraintSample rateSample = PlantConstraintSample.builder(hostRate.getQualifiedId(), calculationId)
          .values(totalRate, 700.0).normalized(totalRate / 700.0, totalRate / 700.0 - 1.0)
          .physical(700.0 - totalRate, Math.max(0.0, totalRate - 700.0)).unit("kg/hr").basis("mass rate")
          .provenance("synthetic completed candidate").build();
      return Arrays.asList(powerSample, rateSample);
    });

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[0]);

    assertEquals(1, sampleCalls.get());
    assertFalse(result.isFeasible());
    assertEquals(2, result.getPlantConstraintEvidence().size());
    assertEquals(2, result.getConstraintValues().length);
    assertTrue(result.getPlantConstraintEvidence().get(0).hasAvailableEvidence());
    assertEquals(PlantConstraintEvidence.OperatingStatus.VIOLATED,
        result.getPlantConstraintEvidence().get(1).getOperatingStatus());

    AtomicInteger missingCalls = new AtomicInteger();
    ProcessModelSimulationEvaluator missingEvaluator = new ProcessModelSimulationEvaluator(fixture.model);
    missingEvaluator.addPlantConstraint(hostRate, (model, calculationId) -> {
      missingCalls.incrementAndGet();
      return null;
    });
    ProcessModelSimulationEvaluator.EvaluationResult missing = missingEvaluator.evaluate(new double[0]);
    assertEquals(1, missingCalls.get());
    assertFalse(missing.isFeasible());
    assertTrue(Double.isNaN(missing.getConstraintValues()[0]));
    assertEquals(PlantConstraintEvidence.CoverageStatus.MISSING_SAMPLE,
        missing.getPlantConstraintEvidence().get(0).getCoverageStatus());
  }

  /** Builds the deterministic two-area compression model and calibrates an intermediate shared-power limit. */
  private Fixture createFixture() {
    Stream feedA = new Stream("feed A", gas());
    Stream feedB = new Stream("feed B", gas());
    feedA.setFlowRate(400.0, "kg/hr");
    feedB.setFlowRate(400.0, "kg/hr");
    Compressor compressorA = new Compressor("compressor A", feedA);
    Compressor compressorB = new Compressor("compressor B", feedB);
    compressorA.setOutletPressure(80.0, "bara");
    compressorB.setOutletPressure(80.0, "bara");
    addWellConstraint(feedA);
    addWellConstraint(feedB);

    ProcessSystem areaA = new ProcessSystem("area A");
    areaA.add(feedA);
    areaA.add(compressorA);
    ProcessSystem areaB = new ProcessSystem("area B");
    areaB.add(feedB);
    areaB.add(compressorB);
    ProcessModel model = new ProcessModel();
    model.add("A", areaA);
    model.add("B", areaB);
    model.run();
    double baselinePower = model.getPower("kW");
    feedA.setFlowRate(900.0, "kg/hr");
    model.run();
    double highPower = model.getPower("kW");
    feedA.setFlowRate(400.0, "kg/hr");
    model.run();
    assertTrue(highPower > baselinePower);
    return new Fixture(model, feedA, feedB, 0.5 * (baselinePower + highPower));
  }

  /** Adds a conservative synthetic well deliverability limit to one producer stream. */
  private void addWellConstraint(Stream feed) {
    CapacityConstraint constraint = new CapacityConstraint("well deliverability", "kg/hr", ConstraintType.HARD)
        .setDesignValue(1200.0).setSeverity(ConstraintSeverity.HARD).setDataSource("synthetic well operating envelope")
        .setConfidence(1.0).setValidityRange(300.0, 1000.0).setValueSupplier(() -> feed.getFlowRate("kg/hr"));
    feed.addCapacityConstraint(constraint);
  }

  /** Creates the exact shared shaft-power definition for both process areas. */
  private PlantConstraintDefinition sharedPowerDefinition() {
    return PlantConstraintDefinition
        .builder("total-shaft-power", PlantConstraintScope.sharedResource("Plant", "compression shaft power"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET)
        .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
        .category(PlantConstraintDefinition.Category.OPERATING).severity(ConstraintSeverity.HARD).unit("kW")
        .basis("compressor and pump shaft power").provenance("synthetic shared power budget")
        .participant(PlantConstraintParticipant.direct("A", "kW", "compressor and pump shaft power"))
        .participant(PlantConstraintParticipant.direct("B", "kW", "compressor and pump shaft power")).build();
  }

  /** Creates the shared dry-gas fluid. */
  private SystemInterface gas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
