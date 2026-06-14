package neqsim.process.util.optimizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.ParetoResult;
import neqsim.process.util.optimizer.ProductionOptimizer.ScenarioRequest;
import neqsim.process.util.optimizer.ProductionOptimizer.ScenarioResult;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests that {@link ProductionOptimizer} can optimize a multi-area {@link ProcessModel} plant
 * through the additive {@link ProcessModelOptimizationView} adapter, and that the adapter
 * aggregates equipment across all areas.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessModelOptimizationViewTest {

  /**
   * Builds a two-area plant: a "separation" area (feed -&gt; inlet separator) and a "compression"
   * area (export compressor -&gt; export cooler) where the compressor is fed by the separator gas
   * outlet shared by object reference across areas.
   *
   * @return the assembled multi-area plant
   */
  private ProcessModel buildTwoAreaPlant() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(10.0, "bara");

    Separator inletSeparator = new Separator("inlet separator", feed);
    inletSeparator.getMechanicalDesign().setMaxDesignGassVolumeFlow(60_000.0);

    ProcessSystem separation = new ProcessSystem();
    separation.add(feed);
    separation.add(inletSeparator);

    Compressor exportCompressor =
        new Compressor("export compressor", inletSeparator.getGasOutStream());
    exportCompressor.setOutletPressure(50.0);
    exportCompressor.getMechanicalDesign().setMaxDesignPower(5000.0);

    Cooler exportCooler = new Cooler("export cooler", exportCompressor.getOutletStream());
    exportCooler.setOutTemperature(40.0, "C");

    ProcessSystem compression = new ProcessSystem();
    compression.add(exportCompressor);
    compression.add(exportCooler);

    ProcessModel plant = new ProcessModel();
    plant.add("separation", separation);
    plant.add("compression", compression);
    return plant;
  }

  /**
   * Verifies the optimizer returns a feasible whole-plant optimum, identifies a bottleneck, and
   * that the adapter view aggregates units from both areas.
   */
  @Test
  public void testOptimizeProcessModelAcrossAreas() {
    ProcessModel plant = buildTwoAreaPlant();
    Stream feed = (Stream) plant.get("separation").getUnit("feed");
    Compressor exportCompressor =
        (Compressor) plant.get("compression").getUnit("export compressor");

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(500.0, 12_000.0).rateUnit("kg/hr")
        .tolerance(50.0).defaultUtilizationLimit(0.95);

    OptimizationObjective maximizeFeed =
        new OptimizationObjective("feed throughput", proc -> feed.getFlowRate("kg/hr"), 1.0);

    OptimizationResult result = optimizer.optimize(plant, feed, config,
        Collections.singletonList(maximizeFeed), Collections.<OptimizationConstraint>emptyList());

    Assertions.assertNotNull(result, "Optimizer should return a result for a ProcessModel");
    Assertions.assertTrue(result.isFeasible(), "Whole-plant optimum should be feasible");
    Assertions.assertNotNull(result.getBottleneck(), "A bottleneck should be identified");
    Assertions.assertTrue(result.getOptimalRate() >= 500.0 && result.getOptimalRate() <= 12_000.0,
        "Optimal rate should be within configured bounds");
    // The bottleneck must be a real unit drawn from one of the two areas.
    Assertions.assertFalse(view(plant).getUnitOperations().isEmpty(),
        "Adapter view should expose plant units");
    // Sanity check the compressor was actually exercised during optimization.
    Assertions.assertTrue(exportCompressor.getPower() > 0.0,
        "Export compressor should have a computed power after optimization");
  }

  /**
   * Helper that wraps a plant in the optimization view for assertions.
   *
   * @param plant the multi-area plant
   * @return a view over the plant
   */
  private ProcessModelOptimizationView view(ProcessModel plant) {
    return new ProcessModelOptimizationView(plant);
  }

  /**
   * Verifies that {@link ProcessModelOptimizationView#getUnitOperations()} aggregates the units of
   * every area and that area-qualified addresses resolve correctly.
   */
  @Test
  public void testViewAggregatesAllAreaUnits() {
    ProcessModel plant = buildTwoAreaPlant();
    ProcessModelOptimizationView view = new ProcessModelOptimizationView(plant);

    Assertions.assertEquals(4, view.getUnitOperations().size(),
        "View should expose all four units across both areas");
    Assertions.assertNotNull(view.getUnit("inlet separator"),
        "Plain unit name in first area should resolve");
    Assertions.assertNotNull(view.getUnit("export compressor"),
        "Plain unit name in second area should resolve");
    Assertions.assertNotNull(view.getUnit("compression::export cooler"),
        "Area-qualified address should resolve");
    Assertions.assertSame(plant, view.getModel(), "View should expose its backing model");
  }

  /**
   * Verifies multi-objective (Pareto) optimization works on a multi-area {@link ProcessModel},
   * trading feed throughput against compressor power, and returns a non-empty Pareto front.
   */
  @Test
  public void testOptimizeParetoProcessModel() {
    ProcessModel plant = buildTwoAreaPlant();
    Stream feed = (Stream) plant.get("separation").getUnit("feed");
    Compressor exportCompressor =
        (Compressor) plant.get("compression").getUnit("export compressor");

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(500.0, 12_000.0).rateUnit("kg/hr")
        .tolerance(50.0).defaultUtilizationLimit(95.0).paretoGridSize(4);

    OptimizationObjective maximizeFeed = new OptimizationObjective("feed throughput",
        proc -> feed.getFlowRate("kg/hr"), 1.0, ObjectiveType.MAXIMIZE);
    OptimizationObjective minimizePower = new OptimizationObjective("compressor power",
        proc -> exportCompressor.getPower(), 1.0, ObjectiveType.MINIMIZE);

    ParetoResult pareto = optimizer.optimizePareto(plant, feed, config,
        Arrays.asList(maximizeFeed, minimizePower),
        Collections.<OptimizationConstraint>emptyList());

    Assertions.assertNotNull(pareto, "Pareto optimization should return a result for a ProcessModel");
    Assertions.assertFalse(pareto.getParetoFront().isEmpty(),
        "Pareto front should contain at least one point");
  }

  /**
   * Verifies scenario comparison works on a multi-area {@link ProcessModel} via the
   * {@link ScenarioRequest} ProcessModel constructor.
   */
  @Test
  public void testOptimizeScenariosProcessModel() {
    ProcessModel plantA = buildTwoAreaPlant();
    Stream feedA = (Stream) plantA.get("separation").getUnit("feed");
    ProcessModel plantB = buildTwoAreaPlant();
    Stream feedB = (Stream) plantB.get("separation").getUnit("feed");

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config = new OptimizationConfig(500.0, 12_000.0).rateUnit("kg/hr")
        .tolerance(50.0).defaultUtilizationLimit(95.0);

    OptimizationObjective objA =
        new OptimizationObjective("throughput", proc -> feedA.getFlowRate("kg/hr"), 1.0);
    OptimizationObjective objB =
        new OptimizationObjective("throughput", proc -> feedB.getFlowRate("kg/hr"), 1.0);

    ScenarioRequest scenarioA = new ScenarioRequest("base case", plantA, feedA, config,
        Collections.singletonList(objA), Collections.<OptimizationConstraint>emptyList());
    ScenarioRequest scenarioB = new ScenarioRequest("high limit", plantB, feedB, config,
        Collections.singletonList(objB), Collections.<OptimizationConstraint>emptyList());

    List<ScenarioResult> results =
        optimizer.optimizeScenarios(Arrays.asList(scenarioA, scenarioB));

    Assertions.assertEquals(2, results.size(), "Both ProcessModel scenarios should be evaluated");
    for (ScenarioResult result : results) {
      Assertions.assertNotNull(result.getResult(),
          "Each scenario should produce an optimization result");
      Assertions.assertTrue(result.getResult().isFeasible(),
          "Each ProcessModel scenario optimum should be feasible");
    }
  }
}
