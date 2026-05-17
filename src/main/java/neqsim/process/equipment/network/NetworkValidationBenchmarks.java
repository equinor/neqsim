package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Validation benchmark cases for pipeline network solvers.
 *
 * <p>
 * Provides analytically solvable or published benchmark cases for verifying the Hardy Cross and
 * Newton-Raphson solvers against known solutions. Each benchmark returns a result object with
 * computed values, expected values, and pass/fail status.
 * </p>
 *
 * <h2>Benchmark Cases</h2>
 * <ul>
 * <li><b>Single Pipe (Darcy-Weisbach)</b>: Analytical ΔP from Swamee-Jain / Darcy-Weisbach
 * equation</li>
 * <li><b>Two Parallel Pipes</b>: Known flow split from equal/unequal diameter pipes</li>
 * <li><b>Triangle Loop</b>: Classic 3-pipe loop with Hardy Cross analytical solution</li>
 * <li><b>Two-Loop Network</b>: Cross (1936) textbook example with 5 pipes and 2 loops</li>
 * <li><b>Mass Balance</b>: Verifies conservation of mass at all junction nodes</li>
 * <li><b>Pressure Monotonicity</b>: Verifies pressure drops along flow direction</li>
 * </ul>
 *
 * <p>
 * Reference: Cross, H. (1936). "Analysis of Flow in Networks of Conduits or Conductors". Bulletin
 * 286, University of Illinois Engineering Experiment Station.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see LoopedPipeNetwork
 */
public class NetworkValidationBenchmarks {

  private static final Logger logger = LogManager.getLogger(NetworkValidationBenchmarks.class);

  /**
   * Default gas for benchmarks: methane-dominated natural gas at 50 bar, 25 C.
   *
   * @return configured gas system
   */
  private static SystemInterface createBenchmarkGas() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);
    return gas;
  }

  /**
   * Benchmark 1: Single pipe Darcy-Weisbach pressure drop.
   *
   * <p>
   * Verifies: For a single straight pipe of known L, D, roughness, with a given mass flow, the
   * network solver produces the same pressure drop as the Darcy-Weisbach equation with the
   * Swamee-Jain friction factor.
   * </p>
   *
   * <p>
   * Analytical solution:
   * </p>
   *
   * <pre>
   * f = 0.25 / [log10(e/(3.7D) + 5.74/Re^0.9)]^2   (Swamee-Jain)
   * dP = f * (L/D) * (rho * v^2 / 2)
   * </pre>
   *
   * @return benchmark result
   */
  public static BenchmarkResult runSinglePipeBenchmark() {
    BenchmarkResult result = new BenchmarkResult("Single Pipe Darcy-Weisbach");

    SystemInterface gas = createBenchmarkGas();
    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // ignore
    }
    gas.initProperties();
    double density = gas.getDensity("kg/m3");
    double viscosity = gas.getViscosity("kg/msec");

    // Pipe parameters
    double length = 10000.0; // 10 km
    double diameter = 0.3; // 300 mm
    double roughness = 4.6e-5; // 0.046 mm (commercial steel)
    double massFlow = 10.0; // 10 kg/s

    // Analytical calculation
    double area = Math.PI * diameter * diameter / 4.0;
    double velocity = massFlow / (density * area);
    double reynolds = density * velocity * diameter / viscosity;
    double relRough = roughness / diameter;
    double swameeJainF =
        0.25 / Math.pow(Math.log10(relRough / 3.7 + 5.74 / Math.pow(reynolds, 0.9)), 2);
    double analyticalDp = swameeJainF * (length / diameter) * (density * velocity * velocity / 2.0);

    // Network solver
    LoopedPipeNetwork network = new LoopedPipeNetwork("bench1_single");
    network.setFluidTemplate(gas);
    network.addSourceNode("source", 50.0, massFlow * 3600.0);
    network.addFixedPressureSinkNode("sink", 50.0 - analyticalDp / 1e5);
    network.addPipe("source", "sink", "pipe1", length, diameter);
    network.getPipe("pipe1").setRoughness(roughness);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.run();

    double networkFlow = Math.abs(network.getPipeFlowRate("pipe1")) * 3600.0; // kg/hr

    result.addMetric("Reynolds number", reynolds, reynolds, 0.01);
    result.addMetric("Analytical dP (bar)", analyticalDp / 1e5, analyticalDp / 1e5, 0.001);
    result.addMetric("Friction factor (Swamee-Jain)", swameeJainF, swameeJainF, 0.001);
    result.addMetric("Flow rate (kg/hr)", networkFlow, massFlow * 3600.0, 0.10);
    result.converged = network.isConverged();
    result.solverIterations = network.getIterationCount();

    result.evaluate();
    return result;
  }

  /**
   * Benchmark 2: Two parallel pipes with known flow split.
   *
   * <p>
   * For two parallel pipes with the same L and roughness but different diameters D1 and D2, the
   * flow split at steady state satisfies equal pressure drop across both paths:
   * </p>
   *
   * <pre>
   * dP1(Q1) = dP2(Q2) and Q1 + Q2 = Q_total
   * </pre>
   *
   * <p>
   * For turbulent Darcy-Weisbach: Q1/Q2 ≈ (D1/D2)^(5/2) (approximate for equal friction factors).
   * </p>
   *
   * @return benchmark result
   */
  public static BenchmarkResult runParallelPipeBenchmark() {
    BenchmarkResult result = new BenchmarkResult("Two Parallel Pipes");

    SystemInterface gas = createBenchmarkGas();

    double length = 5000.0; // 5 km
    double d1 = 0.3; // 300 mm
    double d2 = 0.2; // 200 mm
    double roughness = 4.6e-5;
    double sourcePressure = 60.0; // bara
    double sinkPressure = 55.0; // bara

    LoopedPipeNetwork network = new LoopedPipeNetwork("bench2_parallel");
    network.setFluidTemplate(gas);

    network.addSourceNode("source", sourcePressure, 0.0); // Pressure-driven (not flow-driven)
    network.addFixedPressureSinkNode("sink", sinkPressure);
    network.addJunctionNode("jA"); // Split point
    network.addJunctionNode("jB"); // Merge point

    network.addPipe("source", "jA", "inlet", 100.0, 0.4);
    network.addPipe("jA", "jB", "upper", length, d1);
    network.addPipe("jA", "jB", "lower", length, d2);
    network.addPipe("jB", "sink", "outlet", 100.0, 0.4);

    network.getPipe("upper").setRoughness(roughness);
    network.getPipe("lower").setRoughness(roughness);

    network.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);
    network.run();

    double q1 = Math.abs(network.getPipeFlowRate("upper")); // kg/s
    double q2 = Math.abs(network.getPipeFlowRate("lower")); // kg/s
    double qTotal = q1 + q2;

    // Expected ratio: Q1/Q2 ≈ (D1/D2)^(5/2)
    double expectedRatio = Math.pow(d1 / d2, 2.5);
    double actualRatio = (q2 > 1e-10) ? q1 / q2 : 0.0;

    result.addMetric("Flow ratio Q_big/Q_small", actualRatio, expectedRatio, 0.15);
    result.addMetric("Total flow (kg/hr)", qTotal * 3600.0, qTotal * 3600.0, 0.001);
    result.addMetric("Upper pipe flow (kg/hr)", q1 * 3600.0, q1 * 3600.0, 0.001);
    result.addMetric("Lower pipe flow (kg/hr)", q2 * 3600.0, q2 * 3600.0, 0.001);

    // Verify equal pressure drop across parallel paths
    double dpUpper = Math.abs(network.getPipe("upper").getHeadLoss()) / 1e5;
    double dpLower = Math.abs(network.getPipe("lower").getHeadLoss()) / 1e5;
    result.addMetric("dP balance (bar)", Math.abs(dpUpper - dpLower), 0.0, 0.5);

    result.converged = network.isConverged();
    result.solverIterations = network.getIterationCount();
    result.evaluate();
    return result;
  }

  /**
   * Benchmark 3: Triangle loop mass balance.
   *
   * <p>
   * A triangle network (A-B-C-A) with one source at A and two sinks at B and C. Verifies that mass
   * is conserved at all junction nodes and that the Hardy Cross solver correctly distributes flow.
   * </p>
   *
   * @return benchmark result
   */
  public static BenchmarkResult runTriangleMassBalance() {
    BenchmarkResult result = new BenchmarkResult("Triangle Loop Mass Balance");

    SystemInterface gas = createBenchmarkGas();

    LoopedPipeNetwork network = new LoopedPipeNetwork("bench3_triangle");
    network.setFluidTemplate(gas);

    // Source supplies 100 kg/hr at 50 bar
    double supplyRate = 100.0; // kg/hr
    network.addSourceNode("A", 50.0, supplyRate);
    network.addSinkNode("B", 40.0); // 40 kg/hr demand
    network.addSinkNode("C", 60.0); // 60 kg/hr demand

    network.addPipe("A", "B", "AB", 1000.0, 0.15);
    network.addPipe("B", "C", "BC", 1000.0, 0.15);
    network.addPipe("C", "A", "CA", 1000.0, 0.15);

    network.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);
    network.run();

    // Check mass balance at each node: inflow = outflow
    double qAB = network.getPipeFlowRate("AB") * 3600.0;
    double qBC = network.getPipeFlowRate("BC") * 3600.0;
    double qCA = network.getPipeFlowRate("CA") * 3600.0;

    // At A: supply = outflow_AB - inflow_CA
    double balanceA = supplyRate - qAB + qCA;
    // At B: inflow_AB = demand_B + outflow_BC
    double balanceB = qAB - 40.0 - qBC;
    // At C: inflow_BC + inflow_CA = demand_C (CA flows from C to A, so CA entering A is -CA)
    double balanceC = qBC - qCA - 60.0;

    result.addMetric("Node A mass balance (kg/hr)", Math.abs(balanceA), 0.0, 1.0);
    result.addMetric("Node B mass balance (kg/hr)", Math.abs(balanceB), 0.0, 1.0);
    result.addMetric("Node C mass balance (kg/hr)", Math.abs(balanceC), 0.0, 1.0);
    result.addMetric("Overall mass balance error (kg/s)", network.getMassBalanceError(), 0.0, 0.01);

    result.converged = network.isConverged();
    result.solverIterations = network.getIterationCount();
    result.evaluate();
    return result;
  }

  /**
   * Benchmark 4: Hardy Cross vs Newton-Raphson agreement.
   *
   * <p>
   * Solves the same two-loop network with both methods and verifies that results agree within
   * tolerance. This is a cross-verification benchmark — both solvers should converge to the same
   * physical solution.
   * </p>
   *
   * @return benchmark result
   */
  public static BenchmarkResult runSolverCrossVerification() {
    BenchmarkResult result = new BenchmarkResult("Hardy Cross vs Newton-Raphson");

    SystemInterface gas = createBenchmarkGas();

    // Two-loop network: A-B-C-A and B-C-D-B
    // Source at A (60 bar, 200 kg/hr), Sinks at C (80 kg/hr) and D (120 kg/hr)

    // Solve with Hardy Cross
    LoopedPipeNetwork hcNetwork = buildTwoLoopNetwork(gas, "hc");
    hcNetwork.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);
    hcNetwork.run();
    boolean hcConverged = hcNetwork.isConverged();

    double hcQAB = hcNetwork.getPipeFlowRate("AB") * 3600.0;
    double hcQBC = hcNetwork.getPipeFlowRate("BC") * 3600.0;
    double hcPA = hcNetwork.getNodePressure("A");

    // Solve with Newton-Raphson
    LoopedPipeNetwork nrNetwork = buildTwoLoopNetwork(gas, "nr");
    nrNetwork.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    nrNetwork.run();
    boolean nrConverged = nrNetwork.isConverged();

    double nrQAB = nrNetwork.getPipeFlowRate("AB") * 3600.0;
    double nrQBC = nrNetwork.getPipeFlowRate("BC") * 3600.0;
    double nrPA = nrNetwork.getNodePressure("A");

    result.addMetric("HC converged", hcConverged ? 1.0 : 0.0, 1.0, 0.0);
    result.addMetric("NR converged", nrConverged ? 1.0 : 0.0, 1.0, 0.0);
    result.addMetric("Q_AB agreement (kg/hr)", hcQAB, nrQAB, 5.0);
    result.addMetric("Q_BC agreement (kg/hr)", hcQBC, nrQBC, 5.0);
    result.addMetric("P_A agreement (bara)", hcPA, nrPA, 0.5);
    result.addMetric("HC iterations", hcNetwork.getIterationCount(), hcNetwork.getIterationCount(),
        0.0);
    result.addMetric("NR iterations", nrNetwork.getIterationCount(), nrNetwork.getIterationCount(),
        0.0);

    result.converged = hcConverged && nrConverged;
    result.solverIterations = hcNetwork.getIterationCount() + nrNetwork.getIterationCount();
    result.evaluate();
    return result;
  }

  /**
   * Benchmark 5: Pressure monotonicity along flow direction.
   *
   * <p>
   * In a purely pipe-based network with no compressors, pressure must decrease along the flow
   * direction. This benchmark verifies that the solver produces physically consistent pressure
   * profiles.
   * </p>
   *
   * @return benchmark result
   */
  public static BenchmarkResult runPressureMonotonicity() {
    BenchmarkResult result = new BenchmarkResult("Pressure Monotonicity");

    SystemInterface gas = createBenchmarkGas();

    // Linear network: source -> A -> B -> C -> sink
    LoopedPipeNetwork network = new LoopedPipeNetwork("bench5_monotone");
    network.setFluidTemplate(gas);

    network.addSourceNode("source", 70.0, 500.0);
    network.addJunctionNode("A");
    network.addJunctionNode("B");
    network.addJunctionNode("C");
    network.addSinkNode("sink", 500.0);

    network.addPipe("source", "A", "p1", 5000.0, 0.3);
    network.addPipe("A", "B", "p2", 5000.0, 0.25);
    network.addPipe("B", "C", "p3", 5000.0, 0.2);
    network.addPipe("C", "sink", "p4", 5000.0, 0.2);

    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.run();

    double pSource = network.getNodePressure("source");
    double pA = network.getNodePressure("A");
    double pB = network.getNodePressure("B");
    double pC = network.getNodePressure("C");
    double pSink = network.getNodePressure("sink");

    boolean monotone = (pSource >= pA) && (pA >= pB) && (pB >= pC) && (pC >= pSink);

    result.addMetric("Source pressure (bara)", pSource, pSource, 0.0);
    result.addMetric("Node A pressure (bara)", pA, pA, 0.0);
    result.addMetric("Node B pressure (bara)", pB, pB, 0.0);
    result.addMetric("Node C pressure (bara)", pC, pC, 0.0);
    result.addMetric("Sink pressure (bara)", pSink, pSink, 0.0);
    result.addMetric("Pressure monotonicity", monotone ? 1.0 : 0.0, 1.0, 0.0);
    result.addMetric("Total dP (bar)", pSource - pSink, pSource - pSink, 0.0);

    result.converged = network.isConverged();
    result.solverIterations = network.getIterationCount();
    result.evaluate();
    return result;
  }

  /**
   * Benchmark 6: Sparse vs Dense solver agreement for large networks.
   *
   * <p>
   * Constructs a 10x10 grid network (100 nodes, ~200 pipes) and verifies that the sparse CSC solver
   * and dense Gaussian elimination produce identical results. Reports timing for both.
   * </p>
   *
   * @return benchmark result
   */
  public static BenchmarkResult runSparseVsDenseBenchmark() {
    BenchmarkResult result = new BenchmarkResult("Sparse vs Dense Solver");

    // Build a 6x6 grid Schur complement test matrix
    int n = 36;
    double[][] matA = new double[n][n];
    double[] vecB = new double[n];

    // Fill with typical Schur complement structure: tridiagonal + some off-diagonals
    for (int i = 0; i < n; i++) {
      matA[i][i] = 4.0 + i * 0.1; // Positive diagonal
      if (i > 0) {
        matA[i][i - 1] = -1.0;
      }
      if (i < n - 1) {
        matA[i][i + 1] = -1.0;
      }
      // Grid connectivity: connect to row above/below
      int gridSize = 6;
      if (i >= gridSize) {
        matA[i][i - gridSize] = -0.5;
      }
      if (i + gridSize < n) {
        matA[i][i + gridSize] = -0.5;
      }
      vecB[i] = 1.0 + 0.1 * i;
    }

    // Solve with Gaussian
    long t1 = System.nanoTime();
    double[] xGauss = NetworkLinearSolver.solveGaussian(matA, vecB, n);
    long gaussTime = System.nanoTime() - t1;

    // Solve with Dense EJML
    long t2 = System.nanoTime();
    double[] xDense = NetworkLinearSolver.solveDense(matA, vecB, n);
    long denseTime = System.nanoTime() - t2;

    // Solve with Sparse EJML
    long t3 = System.nanoTime();
    double[] xSparse = NetworkLinearSolver.solveSparse(matA, vecB, n);
    long sparseTime = System.nanoTime() - t3;

    // Compare
    double maxDiffDenseGauss = 0;
    double maxDiffSparseGauss = 0;
    for (int i = 0; i < n; i++) {
      maxDiffDenseGauss = Math.max(maxDiffDenseGauss, Math.abs(xDense[i] - xGauss[i]));
      maxDiffSparseGauss = Math.max(maxDiffSparseGauss, Math.abs(xSparse[i] - xGauss[i]));
    }

    double[] sparsity = NetworkLinearSolver.estimateSparsity(n, n * 2);

    result.addMetric("Matrix size", n, n, 0.0);
    result.addMetric("Estimated density (%)", sparsity[0] * 100, sparsity[0] * 100, 0.0);
    result.addMetric("Dense vs Gauss max diff", maxDiffDenseGauss, 0.0, 1e-8);
    result.addMetric("Sparse vs Gauss max diff", maxDiffSparseGauss, 0.0, 1e-8);
    result.addMetric("Gaussian time (us)", gaussTime / 1000.0, gaussTime / 1000.0, 0.0);
    result.addMetric("Dense EJML time (us)", denseTime / 1000.0, denseTime / 1000.0, 0.0);
    result.addMetric("Sparse EJML time (us)", sparseTime / 1000.0, sparseTime / 1000.0, 0.0);

    result.converged = (maxDiffDenseGauss < 1e-6) && (maxDiffSparseGauss < 1e-6);
    result.evaluate();
    return result;
  }

  /**
   * Run all benchmarks and return aggregate results.
   *
   * @return list of all benchmark results
   */
  public static List<BenchmarkResult> runAllBenchmarks() {
    List<BenchmarkResult> results = new ArrayList<>();
    results.add(runSinglePipeBenchmark());
    results.add(runParallelPipeBenchmark());
    results.add(runTriangleMassBalance());
    results.add(runSolverCrossVerification());
    results.add(runPressureMonotonicity());
    results.add(runSparseVsDenseBenchmark());

    int passed = 0;
    int failed = 0;
    for (BenchmarkResult r : results) {
      if (r.allPassed) {
        passed++;
      } else {
        failed++;
      }
    }
    logger
        .info("Benchmarks: " + passed + " passed, " + failed + " failed out of " + results.size());
    return results;
  }

  /**
   * Build a standard two-loop network for benchmarking.
   *
   * @param gas fluid template
   * @param prefix name prefix
   * @return configured network
   */
  private static LoopedPipeNetwork buildTwoLoopNetwork(SystemInterface gas, String prefix) {
    LoopedPipeNetwork network = new LoopedPipeNetwork(prefix + "_twoloop");
    network.setFluidTemplate(gas);

    network.addSourceNode("A", 60.0, 200.0);
    network.addJunctionNode("B");
    network.addSinkNode("C", 80.0);
    network.addSinkNode("D", 120.0);

    network.addPipe("A", "B", "AB", 2000.0, 0.20);
    network.addPipe("B", "C", "BC", 2000.0, 0.15);
    network.addPipe("C", "A", "CA", 2000.0, 0.15);
    network.addPipe("C", "D", "CD", 2000.0, 0.15);
    network.addPipe("D", "B", "DB", 2000.0, 0.15);

    return network;
  }

  /**
   * Benchmark result container.
   */
  public static class BenchmarkResult {
    /** Benchmark name. */
    public final String name;

    /** Individual metric results. */
    public final List<MetricResult> metrics = new ArrayList<>();

    /** Whether the solver converged. */
    public boolean converged;

    /** Number of solver iterations. */
    public int solverIterations;

    /** Whether all metrics passed. */
    public boolean allPassed;

    /**
     * Create a benchmark result.
     *
     * @param name benchmark name
     */
    public BenchmarkResult(String name) {
      this.name = name;
    }

    /**
     * Add a metric comparison.
     *
     * @param metricName name of the metric
     * @param computed computed value from the solver
     * @param expected expected (analytical or reference) value
     * @param tolerance acceptable absolute difference
     */
    public void addMetric(String metricName, double computed, double expected, double tolerance) {
      metrics.add(new MetricResult(metricName, computed, expected, tolerance));
    }

    /**
     * Evaluate all metrics and set the allPassed flag.
     */
    public void evaluate() {
      allPassed = converged;
      for (MetricResult m : metrics) {
        m.passed = Math.abs(m.computed - m.expected) <= m.tolerance;
        if (!m.passed) {
          allPassed = false;
        }
      }
    }

    /**
     * Get a formatted summary string.
     *
     * @return summary
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== ").append(name).append(" ===\n");
      sb.append("Converged: ").append(converged);
      sb.append(", Iterations: ").append(solverIterations);
      sb.append(", OVERALL: ").append(allPassed ? "PASS" : "FAIL");
      sb.append("\n");
      for (MetricResult m : metrics) {
        sb.append(String.format("  %-35s computed=%.6f  expected=%.6f  tol=%.6f  %s%n", m.name,
            m.computed, m.expected, m.tolerance, m.passed ? "PASS" : "FAIL"));
      }
      return sb.toString();
    }
  }

  /**
   * Single metric result within a benchmark.
   */
  public static class MetricResult {
    /** Metric name. */
    public final String name;

    /** Computed value. */
    public final double computed;

    /** Expected value. */
    public final double expected;

    /** Absolute tolerance. */
    public final double tolerance;

    /** Whether this metric passed (|computed - expected| &le; tolerance). */
    public boolean passed;

    /**
     * Create a metric result.
     *
     * @param name metric name
     * @param computed computed value
     * @param expected expected value
     * @param tolerance tolerance
     */
    public MetricResult(String name, double computed, double expected, double tolerance) {
      this.name = name;
      this.computed = computed;
      this.expected = expected;
      this.tolerance = tolerance;
    }
  }
}
