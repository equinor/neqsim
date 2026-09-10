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
 * Provides synthetic equation, conservation, and cross-solver benchmark cases for verifying the Hardy Cross and
 * Newton-Raphson solvers against known solutions. Each benchmark returns a result object with computed values, expected
 * values, and pass/fail status.
 * </p>
 *
 * <h2>Benchmark Cases</h2>
 * <ul>
 * <li><b>Single Pipe (Darcy-Weisbach)</b>: Analytical ΔP from Swamee-Jain / Darcy-Weisbach equation</li>
 * <li><b>Two Parallel Pipes</b>: Known flow split from equal/unequal diameter pipes</li>
 * <li><b>Triangle Loop</b>: Classic 3-pipe loop with Hardy Cross analytical solution</li>
 * <li><b>Two-Loop Network</b>: Synthetic example with 5 pipes and 2 loops</li>
 * <li><b>Mass Balance</b>: Verifies conservation of mass at all junction nodes</li>
 * <li><b>Pressure Monotonicity</b>: Verifies pressure drops along flow direction</li>
 * </ul>
 *
 * <p>
 * Reference: Cross, H. (1936). "Analysis of Flow in Networks of Conduits or Conductors". Bulletin 286, University of
 * Illinois Engineering Experiment Station.
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
   * Verifies: For a single straight pipe of known L, D, roughness, with a given mass flow, the network solver produces
   * the same pressure drop as the Darcy-Weisbach equation with the Swamee-Jain friction factor.
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
    neqsim.thermodynamicoperations.ThermodynamicOperations ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(
        gas);
    ops.TPflash();
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
    double swameeJainF = 0.25 / Math.pow(Math.log10(relRough / 3.7 + 5.74 / Math.pow(reynolds, 0.9)), 2);
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

    double networkFlow = Math.abs(network.getPipeFlowRate("pipe1")); // kg/hr

    result.addMetric("Darcy-Weisbach dP (bar)", network.getPipe("pipe1").getHeadLoss() / 1e5, analyticalDp / 1e5, 1e-6);
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
   * For two parallel pipes with the same L and roughness but different diameters D1 and D2, the flow split at steady
   * state satisfies equal pressure drop across both paths:
   * </p>
   *
   * <pre>
   * dP1(Q1) = dP2(Q2) and Q1 + Q2 = Q_total
   * </pre>
   *
   * <p>
   * The reference solves the scalar Darcy-Weisbach equations with each pipe's own Reynolds number and relative
   * roughness. The approximate equal-friction D^(5/2) ratio is not used as an exact acceptance criterion.
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
    double totalFlowKgHr = 100000.0;

    LoopedPipeNetwork network = new LoopedPipeNetwork("bench2_parallel");
    network.setFluidTemplate(gas);

    network.addSourceNode("source", sourcePressure, totalFlowKgHr);
    network.addSinkNode("sink", totalFlowKgHr);
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

    double q1 = Math.abs(network.getPipeFlowRate("upper")); // kg/hr
    double q2 = Math.abs(network.getPipeFlowRate("lower")); // kg/hr
    double qTotal = q1 + q2;

    // The equal-friction D^(5/2) approximation is not an exact reference for
    // unequal diameters: Reynolds number and relative roughness differ.
    // Solve the two scalar Darcy-Weisbach equations independently, subject to
    // the specified total mass flow, to obtain an absolute flow reference.
    neqsim.thermodynamicoperations.ThermodynamicOperations operations = new neqsim.thermodynamicoperations.ThermodynamicOperations(
        gas);
    operations.TPflash();
    gas.initProperties();
    double density = gas.getDensity("kg/m3");
    double viscosity = gas.getViscosity("kg/msec");
    double totalFlowKgS = totalFlowKgHr / 3600.0;
    double lower = 0.0;
    double upper = totalFlowKgS;
    for (int i = 0; i < 100; i++) {
      double firstFlow = (lower + upper) / 2.0;
      double firstDp = darcyPressureDrop(firstFlow, length, d1, roughness, density, viscosity);
      double secondDp = darcyPressureDrop(totalFlowKgS - firstFlow, length, d2, roughness, density, viscosity);
      if (firstDp > secondDp) {
        upper = firstFlow;
      } else {
        lower = firstFlow;
      }
    }
    double referenceQ1 = (lower + upper) * 1800.0;
    double referenceQ2 = totalFlowKgHr - referenceQ1;
    result.addMetric("Flow ratio Q_big/Q_small", q1 / q2, referenceQ1 / referenceQ2, 1e-6);
    result.addMetric("Total flow (kg/hr)", qTotal, totalFlowKgHr, 0.01);
    result.addMetric("Upper pipe flow (kg/hr)", q1, referenceQ1, 0.01);
    result.addMetric("Lower pipe flow (kg/hr)", q2, referenceQ2, 0.01);
    result.addMetric("Split mass balance (kg/hr)", network.getNodeFlowRate("jA"), 0.0, 0.01);
    result.addMetric("Merge mass balance (kg/hr)", network.getNodeFlowRate("jB"), 0.0, 0.01);
    double dpUpper = network.getPipe("upper").getHeadLoss() / 1e5;
    double dpLower = network.getPipe("lower").getHeadLoss() / 1e5;
    result.addMetric("dP balance (bar)", dpUpper - dpLower, 0.0, 1e-6);

    result.converged = network.isConverged();
    result.solverIterations = network.getIterationCount();
    result.evaluate();
    return result;
  }

  /**
   * Evaluate the scalar Darcy-Weisbach reference in SI units.
   *
   * @param massFlow flow in kg/s
   * @param length length in m
   * @param diameter diameter in m
   * @param roughness absolute roughness in m
   * @param density fluid density in kg/m3
   * @param viscosity dynamic viscosity in Pa s
   * @return pressure drop in Pa
   */
  private static double darcyPressureDrop(double massFlow, double length, double diameter, double roughness,
      double density, double viscosity) {
    if (massFlow == 0.0) {
      return 0.0;
    }
    double area = Math.PI * diameter * diameter / 4.0;
    double velocity = massFlow / (density * area);
    double reynolds = density * velocity * diameter / viscosity;
    double friction = reynolds < 2300.0 ? 64.0 / reynolds
        : 0.25 / Math.pow(Math.log10(roughness / (3.7 * diameter) + 5.74 / Math.pow(reynolds, 0.9)), 2);
    return friction * length / diameter * density * velocity * velocity / 2.0;
  }

  /**
   * Benchmark 3: Triangle loop mass balance.
   *
   * <p>
   * A triangle network (A-B-C-A) with one source at A and two sinks at B and C. Verifies that mass is conserved at all
   * junction nodes and that the Hardy Cross solver correctly distributes flow.
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
    double qAB = network.getPipeFlowRate("AB");
    double qBC = network.getPipeFlowRate("BC");
    double qCA = network.getPipeFlowRate("CA");

    // At A: supply = outflow_AB - inflow_CA
    double balanceA = supplyRate - qAB + qCA;
    // At B: inflow_AB = demand_B + outflow_BC
    double balanceB = qAB - 40.0 - qBC;
    // At C: inflow_BC + inflow_CA = demand_C (CA flows from C to A, so CA entering A is -CA)
    double balanceC = qBC - qCA - 60.0;

    result.addMetric("Node A mass balance (kg/hr)", Math.abs(balanceA), 0.0, 0.01);
    result.addMetric("Node B mass balance (kg/hr)", Math.abs(balanceB), 0.0, 0.01);
    result.addMetric("Node C mass balance (kg/hr)", Math.abs(balanceC), 0.0, 0.01);
    result.addMetric("Overall mass balance error (kg/s)", network.getMassBalanceError(), 0.0, 1e-6);

    result.converged = network.isConverged();
    result.solverIterations = network.getIterationCount();
    result.evaluate();
    return result;
  }

  /**
   * Benchmark 4: Hardy Cross vs Newton-Raphson agreement.
   *
   * <p>
   * Solves the same two-loop network with both methods and verifies that results agree within tolerance. This is a
   * cross-verification benchmark — both solvers should converge to the same physical solution.
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

    double hcQAB = hcNetwork.getPipeFlowRate("AB");
    double hcQBC = hcNetwork.getPipeFlowRate("BC");
    double hcPA = hcNetwork.getNodePressure("A");

    // Solve with Newton-Raphson
    LoopedPipeNetwork nrNetwork = buildTwoLoopNetwork(gas, "nr");
    nrNetwork.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    nrNetwork.run();
    boolean nrConverged = nrNetwork.isConverged();

    double nrQAB = nrNetwork.getPipeFlowRate("AB");
    double nrQBC = nrNetwork.getPipeFlowRate("BC");
    double nrPA = nrNetwork.getNodePressure("A");

    result.addMetric("HC converged", hcConverged ? 1.0 : 0.0, 1.0, 0.0);
    result.addMetric("NR converged", nrConverged ? 1.0 : 0.0, 1.0, 0.0);
    result.addMetric("Q_AB agreement (kg/hr)", hcQAB, nrQAB, 0.01);
    result.addMetric("Q_BC agreement (kg/hr)", hcQBC, nrQBC, 0.01);
    result.addMetric("P_A agreement (bara)", hcPA, nrPA, 0.5);
    for (String edge : new String[] { "CA", "CD", "DB" }) {
      result.addMetric("Q_" + edge + " agreement (kg/hr)", hcNetwork.getPipeFlowRate(edge),
          nrNetwork.getPipeFlowRate(edge), 0.01);
    }
    for (LoopedPipeNetwork net : new LoopedPipeNetwork[] { hcNetwork, nrNetwork }) {
      String prefix = net == hcNetwork ? "HC " : "NR ";
      result.addMetric(prefix + "C demand (kg/hr)", net.getNodeFlowRate("C"), 80.0, 0.01);
      result.addMetric(prefix + "D demand (kg/hr)", net.getNodeFlowRate("D"), 120.0, 0.01);
      result.addMetric(prefix + "B mass balance (kg/hr)", net.getNodeFlowRate("B"), 0.0, 0.01);
    }

    result.converged = hcConverged && nrConverged;
    result.solverIterations = hcNetwork.getIterationCount() + nrNetwork.getIterationCount();
    result.evaluate();
    return result;
  }

  /**
   * Benchmark 5: Pressure monotonicity along flow direction.
   *
   * <p>
   * In a purely pipe-based network with no compressors, pressure must decrease along the flow direction. This benchmark
   * verifies that the solver produces physically consistent pressure profiles.
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

    result.addMetric("Source pressure (bara)", pSource, 70.0, 1e-8);
    result.addMetric("Sink demand (kg/hr)", network.getNodeFlowRate("sink"), 500.0, 0.01);
    result.addMetric("Pressure monotonicity", monotone ? 1.0 : 0.0, 1.0, 0.0);
    result.addMetric("Positive outlet pressure", pSink > 0.0 ? 1.0 : 0.0, 1.0, 0.0);

    result.converged = network.isConverged();
    result.solverIterations = network.getIterationCount();
    result.evaluate();
    return result;
  }

  /**
   * Benchmark 6: Sparse vs Dense solver agreement for large networks.
   *
   * <p>
   * Constructs a 36-row banded matrix with grid-like off-diagonals and verifies that the sparse CSC solver and dense
   * Gaussian elimination agree and satisfy the original linear equations. Reports timing for all three.
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

    result.addMetric("Dense vs Gauss max diff", maxDiffDenseGauss, 0.0, 1e-8);
    result.addMetric("Sparse vs Gauss max diff", maxDiffSparseGauss, 0.0, 1e-8);
    double maxResidual = 0.0;
    for (double[] solution : new double[][] { xGauss, xDense, xSparse }) {
      for (int i = 0; i < n; i++) {
        double residual = -vecB[i];
        for (int j = 0; j < n; j++) {
          residual += matA[i][j] * solution[j];
        }
        maxResidual = Math.max(maxResidual, Math.abs(residual));
      }
    }
    result.addMetric("Maximum Ax-b residual", maxResidual, 0.0, 1e-10);
    logger.debug("Linear solve times (us): Gaussian {}, Dense {}, Sparse {}", gaussTime / 1000.0, denseTime / 1000.0,
        sparseTime / 1000.0);

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
    logger.info("Benchmarks: " + passed + " passed, " + failed + " failed out of " + results.size());
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
        sb.append(String.format("  %-35s computed=%.6f  expected=%.6f  tol=%.6f  %s%n", m.name, m.computed, m.expected,
            m.tolerance, m.passed ? "PASS" : "FAIL"));
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
