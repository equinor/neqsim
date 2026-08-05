package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessModelSimulationEvaluator}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModelSimulationEvaluatorTest {

  /**
   * Test fixture holding a small two-area process model.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class ModelFixture {
    /** Feed stream used as an optimization decision variable. */
    private final Stream feed;

    /** Separator used for installed capacity constraints. */
    private final Separator separator;

    /** Full process model. */
    private final ProcessModel model;

    /**
     * Creates a test fixture.
     *
     * @param feed feed stream
     * @param separator separator
     * @param model process model
     */
    private ModelFixture(Stream feed, Separator separator, ProcessModel model) {
      this.feed = feed;
      this.separator = separator;
      this.model = model;
    }
  }

  /**
   * Creates a simple gas fluid.
   *
   * @param flowRate feed flow rate
   * @return configured fluid
   */
  private SystemInterface createFluid(double flowRate) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowRate, "kg/hr");
    return fluid;
  }

  /**
   * Creates a two-area process model with a feed and downstream separator.
   *
   * @return model fixture
   */
  private ModelFixture createModelFixture() {
    Stream feed = new Stream("feed", createFluid(10000.0));
    ThrottlingValve choke = new ThrottlingValve("choke", feed);
    choke.setOutletPressure(30.0, "bara");
    Separator separator = new Separator("separator", choke.getOutletStream());

    ProcessSystem wellArea = new ProcessSystem("wells");
    wellArea.add(feed);
    wellArea.add(choke);

    ProcessSystem separationArea = new ProcessSystem("separation");
    separationArea.add(separator);

    ProcessModel model = new ProcessModel();
    model.add("wells", wellArea);
    model.add("separation", separationArea);
    return new ModelFixture(feed, separator, model);
  }

  /**
   * Verifies area-qualified automation variables and model-level objectives.
   */
  @Test
  void evaluateUsesAreaQualifiedAutomationAddresses() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr");
    evaluator.addObjective("exportGas", new ToDoubleFunction<ProcessModel>() {
      /** {@inheritDoc} */
      @Override
      public double applyAsDouble(ProcessModel model) {
        return model.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
      }
    }, ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    evaluator.addConstraintUpperBound("feedLimit", new ToDoubleFunction<ProcessModel>() {
      /** {@inheritDoc} */
      @Override
      public double applyAsDouble(ProcessModel model) {
        return model.getVariableValue("wells::feed.flowRate", "kg/hr");
      }
    }, 15000.0);

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] { 12000.0 });

    assertTrue(result.isSimulationConverged(), "model should converge");
    assertTrue(result.isFeasible(), "feed should be below the upper bound");
    assertEquals(12000.0, fixture.model.getVariableValue("wells::feed.flowRate", "kg/hr"), 1.0e-6);
    assertEquals(-result.getObjectivesRaw()[0], result.getObjectives()[0], 1.0e-6,
        "maximization objective should be sign-adjusted for minimizers");
    assertEquals(3000.0, result.getConstraintMargins()[0], 1.0e-6);
  }

  /**
   * Verifies that one completed model point samples each user result callback once.
   */
  @Test
  void evaluateSamplesEachResultCallbackOnce() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    AtomicInteger objectiveCalls = new AtomicInteger();
    AtomicInteger constraintCalls = new AtomicInteger();
    evaluator.addObjective("statefulObjective", model -> 42.0 + objectiveCalls.getAndIncrement(),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    evaluator.addConstraintUpperBound("statefulConstraint", model -> 11.0 + constraintCalls.getAndIncrement(), 10.0);

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[0]);

    assertEquals(1, objectiveCalls.get(), "one simulated point must sample each objective once");
    assertEquals(1, constraintCalls.get(), "one simulated point must sample each constraint once");
    assertEquals(42.0, result.getObjectivesRaw()[0], 0.0);
    assertEquals(-42.0, result.getObjectives()[0], 0.0);
    assertEquals(11.0, result.getConstraintValues()[0], 0.0);
    assertEquals(-1.0, result.getConstraintMargins()[0], 0.0);
    assertEquals(1000.0, result.getPenaltySum(), 0.0);
  }

  /**
   * Verifies installed capacity discovery and active bottleneck reporting across model areas.
   */
  @Test
  void capacityConstraintsIdentifyActiveModelBottleneck() {
    final ModelFixture fixture = createModelFixture();
    CapacityConstraint installedCapacity = new CapacityConstraint("installedGasCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(12000.0).setMaxValue(13200.0).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("mechanicalDesign").setConfidence(0.95).setValidityRange(8000.0, 12000.0)
        .setValueSupplier(new DoubleSupplier() {
          /** {@inheritDoc} */
          @Override
          public double getAsDouble() {
            return fixture.feed.getFlowRate("kg/hr");
          }
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(installedCapacity);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr")
        .addObjective("gas", new ToDoubleFunction<ProcessModel>() {
          /** {@inheritDoc} */
          @Override
          public double applyAsDouble(ProcessModel model) {
            return model.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
          }
        }, ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE).addEquipmentCapacityConstraints();

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] { 13000.0 });

    assertFalse(result.isFeasible(), "installed capacity should be exceeded");
    assertTrue(evaluator.getConstraintCount() > 0, "capacity constraints should be registered");
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = result.getActiveBottleneck();
    assertNotNull(bottleneck);
    assertEquals("separation", bottleneck.getAreaName());
    assertEquals("separator", bottleneck.getEquipmentName());
    assertEquals("installedGasCapacity", bottleneck.getConstraintName());
    assertEquals(13.0 / 12.0, bottleneck.getUtilization(), 1.0e-12, "metadata must not alter utilization");
    assertEquals("mechanicalDesign", bottleneck.getDataSource());
    assertTrue(bottleneck.hasConfidence());
    assertEquals(0.95, bottleneck.getConfidence(), 0.0);
    assertTrue(bottleneck.hasValidityRange());
    assertEquals(8000.0, bottleneck.getValidityMinimum(), 0.0);
    assertEquals(12000.0, bottleneck.getValidityMaximum(), 0.0);
    assertFalse(bottleneck.isCurrentValueWithinValidityRange());
    assertEquals("separation::separator", bottleneck.getQualifiedEquipmentName());
  }

  /** Verifies bottleneck utilization and evidence share one supplier snapshot. */
  @Test
  void bottleneckSnapshotInvokesValueSupplierOnce() {
    final ModelFixture fixture = createModelFixture();
    final AtomicInteger supplierCalls = new AtomicInteger();
    CapacityConstraint dynamicCapacity = new CapacityConstraint("dynamicGasCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(12000.0).setValidityRange(8000.0, 12000.0).setValueSupplier(new DoubleSupplier() {
          /** {@inheritDoc} */
          @Override
          public double getAsDouble() {
            return supplierCalls.incrementAndGet() == 1 ? 13000.0 : 9000.0;
          }
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(dynamicCapacity);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = evaluator.findActiveBottleneck(fixture.model);

    assertEquals(1, supplierCalls.get());
    assertEquals(13000.0, bottleneck.getCurrentValue(), 0.0);
    assertEquals(13.0 / 12.0, bottleneck.getUtilization(), 1.0e-12);
    assertFalse(bottleneck.isCurrentValueWithinValidityRange());
  }

  /**
   * Verifies that minimum-directed capacity limits retain their engineering-unit limit in model-level bottleneck
   * reporting.
   */
  @Test
  void minimumCapacityConstraintReportsItsFiniteLimit() {
    final ModelFixture fixture = createModelFixture();
    CapacityConstraint minimumHeadroom = new CapacityConstraint("availableHeadroom", "m", ConstraintType.HARD)
        .setMinValue(45.0).setSeverity(ConstraintSeverity.HARD).setValueSupplier(new DoubleSupplier() {
          /** {@inheritDoc} */
          @Override
          public double getAsDouble() {
            return 50.0;
          }
        });
    fixture.separator.clearCapacityConstraints();
    fixture.separator.addCapacityConstraint(minimumHeadroom);

    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.setIncludeStrategyCapacityConstraints(false);
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr").addEquipmentCapacityConstraints();

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] { 10000.0 });
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = result.getActiveBottleneck();

    assertTrue(result.isFeasible());
    assertEquals(0.9, bottleneck.getUtilization(), 1.0e-12);
    assertEquals(50.0, bottleneck.getCurrentValue(), 1.0e-12);
    assertEquals(45.0, bottleneck.getDesignValue(), 1.0e-12, "minimum limit must not be reported as Double.MAX_VALUE");
    assertTrue(bottleneck.isMinimumConstraint());
  }

  /**
   * Verifies malformed manually constructed bottleneck evidence cannot leak non-finite output.
   */
  @Test
  void bottleneckStatusNormalizesMalformedEvidenceToUnset() {
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = new ProcessModelSimulationEvaluator.BottleneckStatus(
        "separation", "separator", "installedGasCapacity", 1.0, 12000.0, 12000.0, false, "manual", true,
        Double.POSITIVE_INFINITY, true, 12000.0, 8000.0, "kg/hr", true);

    assertFalse(bottleneck.hasConfidence());
    assertTrue(Double.isNaN(bottleneck.getConfidence()));
    assertFalse(bottleneck.hasValidityRange());
    assertTrue(Double.isNaN(bottleneck.getValidityMinimum()));
    assertTrue(Double.isNaN(bottleneck.getValidityMaximum()));
    assertFalse(bottleneck.isCurrentValueWithinValidityRange());
  }

  /** Verifies applicability is derived rather than accepted as contradictory external input. */
  @Test
  void bottleneckStatusDerivesValidityApplicabilityFromSnapshot() {
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = new ProcessModelSimulationEvaluator.BottleneckStatus(
        "separation", "separator", "installedGasCapacity", 13.0 / 12.0, 13000.0, 12000.0, false, "manual", true, 0.95,
        true, 8000.0, 12000.0, "kg/hr", false);

    assertTrue(bottleneck.hasValidityRange());
    assertFalse(bottleneck.isCurrentValueWithinValidityRange());
  }

  /**
   * Verifies exported problem metadata for external optimizer bridges.
   */
  @Test
  void problemDefinitionIncludesAreasAndBounds() {
    ModelFixture fixture = createModelFixture();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addParameter("feed multiplier", "wells::feed.flowRate", 5000.0, 20000.0, "kg/hr");

    assertEquals(1, evaluator.getParameterCount());
    assertEquals(5000.0, evaluator.getLowerBounds()[0], 1.0e-12);
    assertEquals(20000.0, evaluator.getUpperBounds()[0], 1.0e-12);
    assertEquals(12500.0, evaluator.getInitialValues()[0], 1.0e-12);
    assertTrue(evaluator.toJson().contains("ProcessModelSimulationEvaluator"));
    assertTrue(evaluator.toJson().contains("wells"));
    assertTrue(evaluator.toJson().contains("separation"));
  }
}
