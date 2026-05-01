package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Random;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.junit.jupiter.api.Test;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Analysis tests for Newton solver improvements.
 *
 * <p>
 * Benchmarks JAMA vs EJML linear solve, init level costs, and measures the overhead of T/P
 * derivatives vs composition-only derivatives.
 * </p>
 *
 * @author benchmarking
 * @version 1.0
 */
class NewtonSolverAnalysisTest {

  /**
   * Benchmark JAMA vs EJML dense linear solve (Ax = b) for typical flash sizes.
   *
   * <p>
   * JAMA uses full LU decomposition via double[][] arrays. EJML uses optimized row-major storage
   * with native-tuned operations.
   * </p>
   */
  @Test
  void benchmarkJAMAvsEJML() {
    int[] sizes = {3, 5, 10, 15, 20, 30};
    int warmup = 2000;
    int N = 20000;
    Random rng = new Random(42);

    System.out.println("=== JAMA vs EJML Dense Linear Solve Benchmark ===");
    System.out.println(
        String.format("%-6s %-12s %-12s %-10s", "Size", "JAMA(ns)", "EJML(ns)", "Speedup"));

    for (int n : sizes) {
      // Create random SPD matrix (typical Jacobian is SPD)
      double[][] aData = new double[n][n];
      double[] bData = new double[n];
      for (int i = 0; i < n; i++) {
        bData[i] = rng.nextDouble();
        for (int j = 0; j < n; j++) {
          aData[i][j] = rng.nextDouble();
        }
        aData[i][i] += n; // Make diagonally dominant
      }

      // JAMA warmup
      Matrix jamaMat = new Matrix(aData);
      Matrix jamab = new Matrix(n, 1);
      for (int i = 0; i < n; i++) {
        jamab.set(i, 0, bData[i]);
      }
      for (int w = 0; w < warmup; w++) {
        jamaMat.solve(jamab);
      }

      // EJML warmup
      DMatrixRMaj ejmlMat = new DMatrixRMaj(aData);
      DMatrixRMaj ejmlb = new DMatrixRMaj(n, 1);
      for (int i = 0; i < n; i++) {
        ejmlb.set(i, 0, bData[i]);
      }
      DMatrixRMaj ejmlx = new DMatrixRMaj(n, 1);
      LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.lu(n);
      for (int w = 0; w < warmup; w++) {
        solver.setA(ejmlMat.copy());
        solver.solve(ejmlb, ejmlx);
      }

      // JAMA benchmark
      long start = System.nanoTime();
      for (int iter = 0; iter < N; iter++) {
        jamaMat.solve(jamab);
      }
      long jamaTime = System.nanoTime() - start;

      // EJML benchmark
      start = System.nanoTime();
      for (int iter = 0; iter < N; iter++) {
        solver.setA(ejmlMat.copy());
        solver.solve(ejmlb, ejmlx);
      }
      long ejmlTime = System.nanoTime() - start;

      double jamaPerCall = (double) jamaTime / N;
      double ejmlPerCall = (double) ejmlTime / N;
      double speedup = jamaPerCall / ejmlPerCall;

      System.out.println(
          String.format("%-6d %-12.0f %-12.0f %-10.2fx", n, jamaPerCall, ejmlPerCall, speedup));

      // Just verify both return reasonable values
      assertTrue(jamaPerCall > 0);
      assertTrue(ejmlPerCall > 0);
    }
    System.out.println();

    // Also benchmark EJML with in-place solve (no copy)
    System.out.println("=== EJML In-Place vs Copy Solve ===");
    for (int n : new int[] {10, 20}) {
      double[][] aData = new double[n][n];
      double[] bData = new double[n];
      for (int i = 0; i < n; i++) {
        bData[i] = rng.nextDouble();
        for (int j = 0; j < n; j++) {
          aData[i][j] = rng.nextDouble();
        }
        aData[i][i] += n;
      }

      DMatrixRMaj ejmlMat = new DMatrixRMaj(aData);
      DMatrixRMaj ejmlb = new DMatrixRMaj(n, 1);
      for (int i = 0; i < n; i++) {
        ejmlb.set(i, 0, bData[i]);
      }
      DMatrixRMaj ejmlx = new DMatrixRMaj(n, 1);
      DMatrixRMaj ejmlMatCopy = new DMatrixRMaj(n, n);
      LinearSolverDense<DMatrixRMaj> solver2 = LinearSolverFactory_DDRM.lu(n);

      // Warmup
      for (int w = 0; w < warmup; w++) {
        ejmlMatCopy.setTo(ejmlMat);
        solver2.setA(ejmlMatCopy);
        solver2.solve(ejmlb, ejmlx);
      }

      // With pre-allocated copy
      long start = System.nanoTime();
      for (int iter = 0; iter < N; iter++) {
        ejmlMatCopy.setTo(ejmlMat);
        solver2.setA(ejmlMatCopy);
        solver2.solve(ejmlb, ejmlx);
      }
      long time = System.nanoTime() - start;
      System.out
          .println(String.format("n=%d EJML pre-alloc copy: %.0f ns/call", n, (double) time / N));
    }
    System.out.println();
  }

  /**
   * Benchmark init(3) vs init(1) + logfugcoefdN only, to measure cost of unnecessary T/P derivative
   * computation.
   */
  @Test
  void benchmarkInitLevelBreakdown() {
    SystemInterface sys = createMediumGas();
    sys.init(0);
    sys.init(3);

    int N = 5000;

    // init(1) only: fugacity coefficients
    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(1);
    }
    long init1Time = System.nanoTime() - start;

    // init(2): adds logfugcoefdT + logfugcoefdP
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(2);
    }
    long init2Time = System.nanoTime() - start;

    // init(3): adds logfugcoefdN (composition derivatives for Jacobian)
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(3);
    }
    long init3Time = System.nanoTime() - start;

    double init1us = init1Time / 1000.0 / N;
    double init2us = init2Time / 1000.0 / N;
    double init3us = init3Time / 1000.0 / N;
    double tpDerivCost = init2us - init1us;
    double compDerivCost = init3us - init2us;
    double wastedPercent = tpDerivCost / init3us * 100;

    System.out.println("=== Init Level Cost Breakdown (10-comp SRK) ===");
    System.out.println(String.format("init(1) fugacities:       %6.1f us", init1us));
    System.out.println(
        String.format("init(2) + T,P derivs:    %6.1f us (delta: +%.1f us)", init2us, tpDerivCost));
    System.out.println(String.format("init(3) + comp derivs:   %6.1f us (delta: +%.1f us)", init3us,
        compDerivCost));
    System.out.println(String.format("T,P derivative cost:     %6.1f us (%.1f%% of init(3))",
        tpDerivCost, wastedPercent));
    System.out.println(
        String.format("Wasted per Newton iter:  %6.1f us (logfugcoefdT + dP)", tpDerivCost));
    System.out.println();

    // T/P derivatives should be a measurable fraction of init(3)
    assertTrue(tpDerivCost >= 0, "T,P derivative cost should be non-negative");
  }

  /**
   * Benchmark init(3,phaseNum) per-phase vs init(3) all-at-once.
   */
  @Test
  void benchmarkInit3PerPhaseVsAll() {
    SystemInterface sys = createMediumGas();
    sys.init(0);
    sys.init(3);

    int N = 5000;

    // init(3) all phases
    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(3);
    }
    long allTime = System.nanoTime() - start;

    // init(3) phase by phase
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(3, 0);
      sys.init(3, 1);
    }
    long perPhaseTime = System.nanoTime() - start;

    System.out.println("=== init(3) All-at-once vs Per-phase ===");
    System.out.println(String.format("init(3) all:     %6.1f us", allTime / 1000.0 / N));
    System.out.println(String.format("init(3,0)+init(3,1): %6.1f us", perPhaseTime / 1000.0 / N));
    System.out.println(String.format("Ratio: %.2f", (double) perPhaseTime / allTime));
    System.out.println();
  }

  /**
   * Benchmark the full Newton solver step vs SS step to understand relative costs.
   */
  @Test
  void benchmarkNewtonVsSSIteration() {
    int N = 200;
    int warmup = 20;

    // Measure a single SS step vs Newton step on a 10-comp two-phase system
    System.out.println("=== Newton vs SS Iteration Cost (10-comp, two-phase) ===");

    // First establish a converged two-phase state
    SystemInterface sys = createMediumGas();
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();

    // Perturb slightly then measure one SS step
    long totalSS = 0;
    for (int w = 0; w < warmup + N; w++) {
      SystemInterface sysCopy = sys.clone();
      // Apply small perturbation to compositions
      for (int i = 0; i < sysCopy.getPhase(0).getNumberOfComponents(); i++) {
        double x0 = sysCopy.getPhase(0).getComponent(i).getx();
        sysCopy.getPhase(0).getComponent(i).setx(x0 * (1.0 + 0.01 * (i % 3 - 1)));
      }
      sysCopy.getPhase(0).normalize();
      sysCopy.init(1);

      long start = System.nanoTime();
      // One RR + init(1, 0) + init(1, 1) ≈ one SS step
      sysCopy.init(1, 0);
      sysCopy.init(1, 1);
      long elapsed = System.nanoTime() - start;
      if (w >= warmup) {
        totalSS += elapsed;
      }
    }

    // Measure one Newton step cost
    long totalNewton = 0;
    for (int w = 0; w < warmup + N; w++) {
      SystemInterface sysCopy = sys.clone();
      sysCopy.init(1);

      SysNewtonRhapsonTPflash solver =
          new SysNewtonRhapsonTPflash(sysCopy, 2, sysCopy.getPhase(0).getNumberOfComponents());

      long start = System.nanoTime();
      solver.solve();
      long elapsed = System.nanoTime() - start;
      if (w >= warmup) {
        totalNewton += elapsed;
      }
    }

    double ssUs = totalSS / 1000.0 / N;
    double newtonUs = totalNewton / 1000.0 / N;
    System.out.println(String.format("SS step (init(1) x2):    %8.1f us", ssUs));
    System.out.println(String.format("Newton step (init(3) + solve): %8.1f us", newtonUs));
    System.out.println(String.format("Newton/SS ratio:         %8.1fx", newtonUs / ssUs));
    System.out.println();
  }

  /**
   * Measure allocation overhead: JAMA creates new Matrix objects each solve. EJML can reuse
   * pre-allocated buffers.
   */
  @Test
  void benchmarkAllocationOverhead() {
    int n = 10;
    int N = 50000;
    Random rng = new Random(42);

    double[][] aData = new double[n][n];
    double[] bData = new double[n];
    for (int i = 0; i < n; i++) {
      bData[i] = rng.nextDouble();
      for (int j = 0; j < n; j++) {
        aData[i][j] = rng.nextDouble();
      }
      aData[i][i] += n;
    }

    // JAMA: each solve allocates new LU decomposition + result matrix
    Matrix jamaMat = new Matrix(aData);
    Matrix jamab = new Matrix(n, 1);
    for (int i = 0; i < n; i++) {
      jamab.set(i, 0, bData[i]);
    }

    // Warmup
    for (int w = 0; w < 5000; w++) {
      jamaMat.solve(jamab);
    }

    long start = System.nanoTime();
    for (int iter = 0; iter < N; iter++) {
      Matrix result = jamaMat.solve(jamab);
    }
    long jamaTime = System.nanoTime() - start;

    // EJML: pre-allocated solver and output buffer
    DMatrixRMaj ejmlMat = new DMatrixRMaj(aData);
    DMatrixRMaj ejmlb = new DMatrixRMaj(n, 1);
    for (int i = 0; i < n; i++) {
      ejmlb.set(i, 0, bData[i]);
    }
    DMatrixRMaj ejmlx = new DMatrixRMaj(n, 1);
    DMatrixRMaj ejmlWork = new DMatrixRMaj(n, n);
    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.lu(n);

    // Warmup
    for (int w = 0; w < 5000; w++) {
      ejmlWork.setTo(ejmlMat);
      solver.setA(ejmlWork);
      solver.solve(ejmlb, ejmlx);
    }

    start = System.nanoTime();
    for (int iter = 0; iter < N; iter++) {
      ejmlWork.setTo(ejmlMat);
      solver.setA(ejmlWork);
      solver.solve(ejmlb, ejmlx);
    }
    long ejmlTime = System.nanoTime() - start;

    System.out.println("=== Allocation Overhead (n=10, " + N + " solves) ===");
    System.out.println(String.format("JAMA (new alloc each): %.0f ns/call", (double) jamaTime / N));
    System.out.println(String.format("EJML (pre-allocated):  %.0f ns/call", (double) ejmlTime / N));
    System.out.println(String.format("Speedup:               %.2fx", (double) jamaTime / ejmlTime));
    System.out.println();
  }

  /**
   * Creates a 10-component natural gas at two-phase conditions.
   *
   * @return configured system
   */
  private SystemInterface createMediumGas() {
    SystemInterface sys = new SystemSrkEos(273.15 + 25.0, 50.0);
    sys.addComponent("nitrogen", 1.0);
    sys.addComponent("CO2", 2.0);
    sys.addComponent("methane", 80.0);
    sys.addComponent("ethane", 6.0);
    sys.addComponent("propane", 3.0);
    sys.addComponent("i-butane", 1.5);
    sys.addComponent("n-butane", 2.0);
    sys.addComponent("i-pentane", 1.0);
    sys.addComponent("n-pentane", 0.8);
    sys.addComponent("n-hexane", 0.5);
    sys.setMixingRule("classic");
    return sys;
  }
}
