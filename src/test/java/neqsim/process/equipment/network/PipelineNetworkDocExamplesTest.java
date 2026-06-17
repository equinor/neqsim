package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that every code example in docs/process/pipeline_network_optimization.md compiles and
 * runs correctly.
 */
class PipelineNetworkDocExamplesTest {

  private static SystemInterface gas;

  @BeforeAll
  static void setUp() {
    gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);
  }

  /**
   * Tests the Java example from section "NLP Choke Optimization".
   */
  @Test
  void testNlpChokeOptimizationExample() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("MyNetwork");
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Build network with wells, chokes, and export
    network.addSourceNode("res1", 200.0, 0.0);
    network.addJunctionNode("wh1");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("export", 50.0);

    network.addWellIPR("res1", "wh1", "ipr1", 5e-6, false);
    network.addChoke("wh1", "manifold", "choke1", 50.0, 80.0);
    network.addPipe("manifold", "export", "export_pipe", 20000.0, 0.3, 0.00005);

    // Create and configure optimizer
    NetworkOptimizer optimizer = network.createOptimizer();
    optimizer.setAlgorithm(NetworkOptimizer.Algorithm.BOBYQA);
    optimizer.setObjectiveType(NetworkOptimizer.ObjectiveType.MAX_PRODUCTION);
    optimizer.setMaxEvaluations(300);

    // Run optimization
    NetworkOptimizer.OptimizationResult result = optimizer.optimize();
    assertTrue(result.converged, "Optimizer should converge");
    assertTrue(result.totalProductionKgHr >= 0, "Production should be non-negative");

    // Verify fields referenced in the doc exist and have sane values
    assertNotNull(result.algorithm);
    assertNotNull(result.objectiveTypeName);
    assertTrue(result.elapsedMs >= 0);
  }

  /**
   * Tests the convenience methods from the doc: optimizeProductionNLP() and
   * optimizeMultiObjective().
   */
  @Test
  void testConvenienceMethodsExample() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("Convenience");
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res1", 200.0, 0.0);
    network.addSourceNode("res2", 180.0, 0.0);
    network.addJunctionNode("wh1");
    network.addJunctionNode("wh2");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("export", 50.0);

    network.addWellIPR("res1", "wh1", "ipr1", 5e-6, false);
    network.addWellIPR("res2", "wh2", "ipr2", 4e-6, false);
    network.addChoke("wh1", "manifold", "choke1", 50.0, 80.0);
    network.addChoke("wh2", "manifold", "choke2", 50.0, 70.0);
    network.addPipe("manifold", "export", "export_pipe", 20000.0, 0.3, 0.00005);

    // Quick single-objective optimization
    NetworkOptimizer.OptimizationResult result = network.optimizeProductionNLP();
    assertNotNull(result);
    assertTrue(result.totalProductionKgHr >= 0);

    // Multi-objective Pareto front (5 points)
    List<NetworkOptimizer.OptimizationResult> pareto = network.optimizeMultiObjective(5);
    assertNotNull(pareto);
    assertTrue(pareto.size() > 0, "Pareto front should have at least one point");
  }

  /**
   * Tests the objective type enum values referenced in the doc table.
   */
  @Test
  void testObjectiveTypeEnumValues() {
    assertNotNull(NetworkOptimizer.ObjectiveType.MAX_PRODUCTION);
    assertNotNull(NetworkOptimizer.ObjectiveType.MAX_REVENUE);
    assertNotNull(NetworkOptimizer.ObjectiveType.MIN_COMPRESSOR_POWER);
    assertNotNull(NetworkOptimizer.ObjectiveType.MAX_SPECIFIC_PRODUCTION);
  }

  /**
   * Tests the linear solver API from section "Sparse Matrix Solver".
   */
  @Test
  void testLinearSolverExample() {
    int n = 5;
    double[][] matA = new double[n][n];
    double[] vecB = new double[n];
    for (int i = 0; i < n; i++) {
      matA[i][i] = 4.0;
      if (i > 0) {
        matA[i][i - 1] = -1.0;
      }
      if (i < n - 1) {
        matA[i][i + 1] = -1.0;
      }
      vecB[i] = 1.0;
    }

    // Auto-select
    double[] x = NetworkLinearSolver.solve(matA, vecB, n);
    assertNotNull(x);
    assertEquals(n, x.length);

    // Force Gaussian
    double[] xg = NetworkLinearSolver.solveGaussian(matA, vecB, n);
    assertNotNull(xg);

    // Force Dense EJML
    double[] xd = NetworkLinearSolver.solveDense(matA, vecB, n);
    assertNotNull(xd);

    // Force Sparse EJML
    double[] xs = NetworkLinearSolver.solveSparse(matA, vecB, n);
    assertNotNull(xs);

    // All solvers should agree
    for (int i = 0; i < n; i++) {
      assertEquals(xg[i], xd[i], 1e-10, "Dense should match Gaussian at index " + i);
      assertEquals(xg[i], xs[i], 1e-10, "Sparse should match Gaussian at index " + i);
    }
  }

  /**
   * Tests the validation benchmark API from section "Validation Benchmarks".
   */
  @Test
  void testValidationBenchmarksExample() {
    // Direct call
    List<NetworkValidationBenchmarks.BenchmarkResult> results =
        NetworkValidationBenchmarks.runAllBenchmarks();
    assertNotNull(results);
    assertTrue(results.size() >= 6, "Should have at least 6 benchmarks");

    for (NetworkValidationBenchmarks.BenchmarkResult r : results) {
      String summary = r.getSummary();
      assertNotNull(summary);
      assertFalse(summary.isEmpty());
    }

    // Static convenience method on LoopedPipeNetwork
    List<NetworkValidationBenchmarks.BenchmarkResult> results2 =
        LoopedPipeNetwork.runValidationBenchmarks();
    assertNotNull(results2);
    assertEquals(results.size(), results2.size());
  }

  /**
   * Tests the OptimizationResult fields referenced in the doc: paretoWeight,
   * totalCompressorPowerKW.
   */
  @Test
  void testParetoResultFields() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("ParetoFields");
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    network.addSourceNode("res1", 200.0, 0.0);
    network.addJunctionNode("wh1");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("export", 50.0);
    network.addWellIPR("res1", "wh1", "ipr1", 5e-6, false);
    network.addChoke("wh1", "manifold", "choke1", 50.0, 80.0);
    network.addPipe("manifold", "export", "export_pipe", 20000.0, 0.3, 0.00005);

    NetworkOptimizer optimizer = network.createOptimizer();
    optimizer.setParetoPoints(3);
    List<NetworkOptimizer.OptimizationResult> pareto = optimizer.optimizeMultiObjective();

    for (NetworkOptimizer.OptimizationResult r : pareto) {
      // Fields referenced in doc
      assertTrue(r.paretoWeight >= 0.0 && r.paretoWeight <= 1.0,
          "Pareto weight should be in [0,1]");
      assertTrue(r.totalProductionKgHr >= 0);
      assertTrue(r.totalCompressorPowerKW >= 0);
    }
  }
}
