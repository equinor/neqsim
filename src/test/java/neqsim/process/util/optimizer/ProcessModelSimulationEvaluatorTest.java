package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    ProcessModelSimulationEvaluator.EvaluationResult result =
        evaluator.evaluate(new double[] {12000.0});

    assertTrue(result.isSimulationConverged(), "model should converge");
    assertTrue(result.isFeasible(), "feed should be below the upper bound");
    assertEquals(12000.0, fixture.model.getVariableValue("wells::feed.flowRate", "kg/hr"), 1.0e-6);
    assertEquals(-result.getObjectivesRaw()[0], result.getObjectives()[0], 1.0e-6,
        "maximization objective should be sign-adjusted for minimizers");
    assertEquals(3000.0, result.getConstraintMargins()[0], 1.0e-6);
  }

  /**
   * Verifies installed capacity discovery and active bottleneck reporting across model areas.
   */
  @Test
  void capacityConstraintsIdentifyActiveModelBottleneck() {
    final ModelFixture fixture = createModelFixture();
    CapacityConstraint installedCapacity =
        new CapacityConstraint("installedGasCapacity", "kg/hr", ConstraintType.HARD)
            .setDesignValue(5000.0).setMaxValue(12000.0).setSeverity(ConstraintSeverity.HARD)
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
    evaluator.addParameter("wells::feed.flowRate", 5000.0, 20000.0, "kg/hr")
        .addObjective("gas", new ToDoubleFunction<ProcessModel>() {
          /** {@inheritDoc} */
          @Override
          public double applyAsDouble(ProcessModel model) {
            return model.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
          }
        }, ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE)
        .addEquipmentCapacityConstraints();

    ProcessModelSimulationEvaluator.EvaluationResult result =
        evaluator.evaluate(new double[] {13000.0});

    assertFalse(result.isFeasible(), "installed capacity should be exceeded");
    assertTrue(evaluator.getConstraintCount() > 0, "capacity constraints should be registered");
    ProcessModelSimulationEvaluator.BottleneckStatus bottleneck = result.getActiveBottleneck();
    assertNotNull(bottleneck);
    assertEquals("separation", bottleneck.getAreaName());
    assertEquals("separator", bottleneck.getEquipmentName());
    assertEquals("installedGasCapacity", bottleneck.getConstraintName());
    assertTrue(bottleneck.getUtilization() > 1.0, "bottleneck should be over capacity");
    assertEquals("separation::separator", bottleneck.getQualifiedEquipmentName());
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
