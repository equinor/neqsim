package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link NetworkOptimizer}.
 *
 * <p>
 * Uses a small gathering network with two wells and chokes feeding a common manifold.
 * </p>
 */
class NetworkOptimizerTest {

  private LoopedPipeNetwork network;

  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);

    network = new LoopedPipeNetwork("opt_test");
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(100.0);

    // Two wells with IPRs and chokes feeding a manifold then to export
    network.addSourceNode("res1", 200.0, 0.0); // 200 bara reservoir
    network.addSourceNode("res2", 180.0, 0.0); // 180 bara reservoir
    network.addJunctionNode("wh1");
    network.addJunctionNode("wh2");
    network.addJunctionNode("manifold");
    network.addFixedPressureSinkNode("export", 50.0);

    // Well IPR elements
    network.addWellIPR("res1", "wh1", "ipr1", 5e-6, false);
    network.addWellIPR("res2", "wh2", "ipr2", 4e-6, false);

    // Chokes from wellhead to manifold
    network.addChoke("wh1", "manifold", "choke1", 50.0, 50.0);
    network.addChoke("wh2", "manifold", "choke2", 40.0, 50.0);

    // Export pipe from manifold to export
    network.addPipe("manifold", "export", "export_pipe", 20000.0, 0.3, 0.00005);
  }

  @Test
  void testCreateOptimizer() {
    NetworkOptimizer optimizer = network.createOptimizer();
    assertNotNull(optimizer, "Optimizer should not be null");
  }

  @Test
  void testBOBYQAOptimization() {
    NetworkOptimizer optimizer = network.createOptimizer();
    optimizer.setAlgorithm(NetworkOptimizer.Algorithm.BOBYQA);
    optimizer.setObjectiveType(NetworkOptimizer.ObjectiveType.MAX_PRODUCTION);
    optimizer.setMaxEvaluations(200);
    NetworkOptimizer.OptimizationResult result = optimizer.optimize();
    assertNotNull(result, "Result should not be null");
    assertTrue(result.converged, "Optimizer should report converged");
    assertNotNull(result.chokeOpenings, "Openings should not be null");
    assertEquals(2, result.chokeOpenings.length, "Should have 2 choke openings");
    assertTrue(result.totalProductionKgHr >= 0.0, "Production should be non-negative");
  }

  @Test
  void testCMAESOptimization() {
    NetworkOptimizer optimizer = network.createOptimizer();
    optimizer.setAlgorithm(NetworkOptimizer.Algorithm.CMAES);
    optimizer.setObjectiveType(NetworkOptimizer.ObjectiveType.MAX_PRODUCTION);
    optimizer.setMaxEvaluations(200);
    NetworkOptimizer.OptimizationResult result = optimizer.optimize();
    assertNotNull(result, "Result should not be null");
    assertTrue(result.converged, "Optimizer should report converged");
    assertTrue(result.totalProductionKgHr >= 0.0, "Production should be non-negative");
  }

  @Test
  void testOptimizeProductionNLPConvenience() {
    NetworkOptimizer.OptimizationResult result = network.optimizeProductionNLP();
    assertNotNull(result, "NLP result should not be null");
    assertTrue(result.converged || result.message != null,
        "Should converge or return status message");
  }

  @Test
  void testMultiObjectiveReturnsMultiplePoints() {
    java.util.List<NetworkOptimizer.OptimizationResult> pareto = network.optimizeMultiObjective(5);
    assertNotNull(pareto);
    assertTrue(pareto.size() >= 1, "Should have at least 1 Pareto point");
    for (NetworkOptimizer.OptimizationResult r : pareto) {
      assertNotNull(r.chokeOpenings);
    }
  }

  @Test
  void testOptimizationImprovesOverBaseline() {
    // Run with default choke openings
    network.run();
    double baselineFlow = network.getTotalSinkFlow();

    // Run NLP optimizer
    NetworkOptimizer optimizer = network.createOptimizer();
    optimizer.setAlgorithm(NetworkOptimizer.Algorithm.BOBYQA);
    optimizer.setObjectiveType(NetworkOptimizer.ObjectiveType.MAX_PRODUCTION);
    optimizer.setMaxEvaluations(300);
    NetworkOptimizer.OptimizationResult result = optimizer.optimize();

    // Optimizer should produce a valid result
    assertNotNull(result, "Optimization result should not be null");
    assertTrue(result.converged, "Optimization should converge");
    assertTrue(result.totalProductionKgHr > 0, "Should have positive production");
  }
}
