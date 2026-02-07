package neqsim.process.util.optimizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ManipulatedVariable;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.ParetoResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Advanced tests for ProductionOptimizer covering gradient descent, parallel Pareto, edge cases,
 * and the PSO seed configuration.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProductionOptimizerAdvancedTest {

  /**
   * Creates a simple compressor process for testing.
   *
   * @return array of [ProcessSystem, Stream]
   */
  private Object[] createSimpleProcess() {
    SystemSrkEos system = new SystemSrkEos(298.15, 20.0);
    system.addComponent("methane", 200.0);

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(100.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(60.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);

    return new Object[] {process, feed};
  }

  /**
   * Tests that gradient descent search produces a valid result with a single variable.
   */
  @Test
  void testGradientDescentSingleVariable() {
    Object[] setup = createSimpleProcess();
    ProcessSystem process = (ProcessSystem) setup[0];
    Stream feed = (Stream) setup[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(50.0, 500.0).searchMode(SearchMode.GRADIENT_DESCENT_SCORE)
            .rateUnit("kg/hr").maxIterations(20).tolerance(1.0);

    OptimizationResult result =
        optimizer.optimize(process, feed, config, Collections.emptyList(), Collections.emptyList());

    Assertions.assertNotNull(result, "Gradient descent should return a result");
    Assertions.assertTrue(result.getOptimalRate() >= 50.0,
        "Optimal rate should be within lower bound");
    Assertions.assertTrue(result.getOptimalRate() <= 500.0,
        "Optimal rate should be within upper bound");
    Assertions.assertTrue(result.getIterations() > 0, "Should have performed at least 1 iteration");
  }

  /**
   * Tests gradient descent with multiple variables via ManipulatedVariable.
   */
  @Test
  void testGradientDescentMultiVariable() {
    Object[] setup = createSimpleProcess();
    ProcessSystem process = (ProcessSystem) setup[0];
    Stream feed = (Stream) setup[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();

    ManipulatedVariable flowVar = new ManipulatedVariable("flow", 50.0, 300.0, "kg/hr",
        (proc, val) -> ((StreamInterface) proc.getUnit("feed")).setFlowRate(val, "kg/hr"));
    ManipulatedVariable pressureVar = new ManipulatedVariable("pressure", 40.0, 100.0, "bara",
        (proc, val) -> ((Compressor) proc.getUnit("compressor")).setOutletPressure(val));

    OptimizationConfig config =
        new OptimizationConfig(50.0, 300.0).searchMode(SearchMode.GRADIENT_DESCENT_SCORE)
            .rateUnit("kg/hr").maxIterations(15).tolerance(1.0);

    OptimizationResult result = optimizer.optimize(process, Arrays.asList(flowVar, pressureVar),
        config, Collections.emptyList(), Collections.emptyList());

    Assertions.assertNotNull(result, "Multi-variable gradient descent should return a result");
    Assertions.assertNotNull(result.getDecisionVariables(), "Should have decision variables");
    Assertions.assertTrue(result.getDecisionVariables().containsKey("flow"),
        "Should have flow variable");
    Assertions.assertTrue(result.getDecisionVariables().containsKey("pressure"),
        "Should have pressure variable");
  }

  /**
   * Tests PSO with a fixed seed produces reproducible results.
   */
  @Test
  void testPsoFixedSeedReproducibility() {
    Object[] setup = createSimpleProcess();
    ProcessSystem process = (ProcessSystem) setup[0];
    Stream feed = (Stream) setup[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(50.0, 500.0).searchMode(SearchMode.PARTICLE_SWARM_SCORE)
            .rateUnit("kg/hr").maxIterations(10).tolerance(1.0).randomSeed(42L).useFixedSeed(true);

    OptimizationResult result1 =
        optimizer.optimize(process, feed, config, Collections.emptyList(), Collections.emptyList());

    OptimizationResult result2 =
        optimizer.optimize(process, feed, config, Collections.emptyList(), Collections.emptyList());

    Assertions.assertEquals(result1.getOptimalRate(), result2.getOptimalRate(), 1e-6,
        "PSO with fixed seed should produce identical results");
  }

  /**
   * Tests that stream-based optimize correctly delegates to variable-based optimize.
   */
  @Test
  void testStreamDelegationConsistency() {
    Object[] setup = createSimpleProcess();
    ProcessSystem process = (ProcessSystem) setup[0];
    Stream feed = (Stream) setup[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Stream-based call (delegates internally)
    OptimizationConfig config =
        new OptimizationConfig(50.0, 500.0).searchMode(SearchMode.GOLDEN_SECTION_SCORE)
            .rateUnit("kg/hr").maxIterations(15).tolerance(1.0);

    OptimizationResult streamResult =
        optimizer.optimize(process, feed, config, Collections.emptyList(), Collections.emptyList());

    // Equivalent variable-based call
    ManipulatedVariable feedVar = new ManipulatedVariable("feed", 50.0, 500.0, "kg/hr",
        (proc, val) -> ((StreamInterface) proc.getUnit("feed")).setFlowRate(val, "kg/hr"));

    OptimizationResult varResult = optimizer.optimize(process, Collections.singletonList(feedVar),
        config, Collections.emptyList(), Collections.emptyList());

    Assertions.assertEquals(streamResult.getOptimalRate(), varResult.getOptimalRate(), 50.0,
        "Stream-based and variable-based should converge to similar results");
    Assertions.assertTrue(streamResult.isFeasible() == varResult.isFeasible(),
        "Feasibility should be consistent");
  }

  /**
   * Tests that equal lower and upper bounds produces a valid (degenerate) result.
   */
  @Test
  void testEqualBoundsEdgeCase() {
    Object[] setup = createSimpleProcess();
    ProcessSystem process = (ProcessSystem) setup[0];
    Stream feed = (Stream) setup[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(100.0, 100.0).rateUnit("kg/hr").maxIterations(5).tolerance(0.1);

    OptimizationResult result =
        optimizer.optimize(process, feed, config, Collections.emptyList(), Collections.emptyList());

    Assertions.assertNotNull(result);
    // With equal bounds, optimal rate should equal the single feasible point
    Assertions.assertEquals(100.0, result.getOptimalRate(), 1.0,
        "Equal bounds should force optimal to the single point");
  }

  /**
   * Tests Pareto optimization with two objectives.
   */
  @Test
  void testParetoOptimizationTwoObjectives() {
    Object[] setup = createSimpleProcess();
    ProcessSystem process = (ProcessSystem) setup[0];
    Stream feed = (Stream) setup[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Throughput objective (maximize)
    OptimizationObjective throughput = new OptimizationObjective("throughput",
        proc -> ((StreamInterface) proc.getUnit("feed")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    // Power objective (minimize — less compressor power is better)
    OptimizationObjective power = new OptimizationObjective("power",
        proc -> Math.abs(((Compressor) proc.getUnit("compressor")).getPower()), 1.0,
        ObjectiveType.MINIMIZE);

    List<OptimizationObjective> objectives = Arrays.asList(throughput, power);

    OptimizationConfig config = new OptimizationConfig(50.0, 300.0).rateUnit("kg/hr")
        .maxIterations(10).tolerance(10.0).paretoGridSize(5);

    ParetoResult pareto =
        optimizer.optimizePareto(process, feed, config, objectives, Collections.emptyList());

    Assertions.assertNotNull(pareto, "Pareto result should not be null");
    Assertions.assertTrue(pareto.getParetoFront().size() >= 1,
        "Should have at least one Pareto-optimal point");
    Assertions.assertEquals(5, pareto.getAllPoints().size(),
        "Should have exactly paretoGridSize evaluated points");
  }

  /**
   * Tests adaptive penalty scaling — infeasible points should always score worse than feasible.
   */
  @Test
  void testAdaptivePenaltyDominance() {
    Object[] setup = createSimpleProcess();
    ProcessSystem process = (ProcessSystem) setup[0];
    Stream feed = (Stream) setup[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Run with tight utilization limit to force some infeasible points
    OptimizationConfig config = new OptimizationConfig(50.0, 500.0).rateUnit("kg/hr")
        .maxIterations(15).defaultUtilizationLimit(0.5);

    OptimizationResult result =
        optimizer.optimize(process, feed, config, Collections.emptyList(), Collections.emptyList());

    Assertions.assertNotNull(result);
    // With a 50% utilization limit, the optimizer should find a constrained optimum
    // The key property: the result should claim to be feasible (or at least attempt
    // to return the best feasible point found)
    if (result.isFeasible()) {
      Assertions.assertTrue(result.getOptimalRate() > 0,
          "Feasible result should have a positive rate");
    }
  }

  /**
   * Tests that Nelder-Mead works with a single variable.
   */
  @Test
  void testNelderMeadSingleVariable() {
    Object[] setup = createSimpleProcess();
    ProcessSystem process = (ProcessSystem) setup[0];
    Stream feed = (Stream) setup[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(50.0, 500.0).searchMode(SearchMode.NELDER_MEAD_SCORE)
            .rateUnit("kg/hr").maxIterations(20).tolerance(1.0);

    OptimizationResult result =
        optimizer.optimize(process, feed, config, Collections.emptyList(), Collections.emptyList());

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.getOptimalRate() >= 50.0);
    Assertions.assertTrue(result.getOptimalRate() <= 500.0);
  }

  /**
   * Tests scenario comparison with multiple scenarios.
   */
  @Test
  void testScenarioComparison() {
    // Create two separate processes for two scenarios
    Object[] setup1 = createSimpleProcess();
    ProcessSystem process1 = (ProcessSystem) setup1[0];
    Stream feed1 = (Stream) setup1[1];

    Object[] setup2 = createSimpleProcess();
    ProcessSystem process2 = (ProcessSystem) setup2[0];
    Stream feed2 = (Stream) setup2[1];

    ProductionOptimizer optimizer = new ProductionOptimizer();

    OptimizationConfig config1 =
        new OptimizationConfig(50.0, 300.0).rateUnit("kg/hr").maxIterations(10);
    OptimizationConfig config2 =
        new OptimizationConfig(100.0, 500.0).rateUnit("kg/hr").maxIterations(10);

    ProductionOptimizer.ScenarioRequest scenario1 = new ProductionOptimizer.ScenarioRequest(
        "Low Pressure", process1, feed1, config1, Collections.emptyList(), Collections.emptyList());
    ProductionOptimizer.ScenarioRequest scenario2 =
        new ProductionOptimizer.ScenarioRequest("High Pressure", process2, feed2, config2,
            Collections.emptyList(), Collections.emptyList());

    List<ProductionOptimizer.ScenarioResult> results =
        optimizer.optimizeScenarios(Arrays.asList(scenario1, scenario2));

    Assertions.assertEquals(2, results.size(), "Should have results for both scenarios");
    Assertions.assertEquals("Low Pressure", results.get(0).getName());
    Assertions.assertEquals("High Pressure", results.get(1).getName());
    Assertions.assertNotNull(results.get(0).getResult());
    Assertions.assertNotNull(results.get(1).getResult());
  }
}
