package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ojalgo.matrix.decomposition.LU;
import org.ojalgo.matrix.store.Primitive64Store;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Analysis tests for Newton solver improvements.
 *
 * <p>
 * Benchmarks JAMA vs ojAlgo linear solve, init level costs, and measures the overhead of T/P
 * derivatives vs composition-only derivatives.
 * </p>
 *
 * @author benchmarking
 * @version 1.0
 */
@Tag("LinearAlgebra")
class NewtonSolverAnalysisTest {
  private static final Logger logger = LogManager.getLogger(NewtonSolverAnalysisTest.class);

  /**
   * Benchmark JAMA vs ojAlgo dense linear solve (Ax = b) for typical flash sizes.
   *
   * <p>
   * JAMA uses full LU decomposition via double[][] arrays. ojAlgo uses optimized dense storage with
   * native-tuned operations.
   * </p>
   */
  @Test
  void benchmarkJAMAvsEJML() {
    int[] sizes = {3, 5, 10, 15, 20, 30};
    int warmup = 2000;
    int N = 20000;
    Random rng = new Random(42);

    logger.info("=== JAMA vs ojAlgo Dense Linear Solve Benchmark ===");
    logger
        .info(String.format("%-6s %-12s %-12s %-10s", "Size", "JAMA(ns)", "ojAlgo(ns)", "Speedup"));

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

      // ojAlgo warmup
      Primitive64Store ojAlgoMat = Primitive64Store.FACTORY.rows(aData);
      Primitive64Store ojAlgoB = Primitive64Store.FACTORY.make(n, 1);
      for (int i = 0; i < n; i++) {
        ojAlgoB.set(i, 0, bData[i]);
      }
      Primitive64Store ojAlgoWork = Primitive64Store.FACTORY.make(n, n);
      LU<Double> solver = LU.PRIMITIVE.make(n, n);
      for (int w = 0; w < warmup; w++) {
        copyDenseStore(ojAlgoMat, ojAlgoWork, n);
        solver.decompose(ojAlgoWork);
        solver.getSolution(ojAlgoB);
      }

      // JAMA benchmark
      long start = System.nanoTime();
      for (int iter = 0; iter < N; iter++) {
        jamaMat.solve(jamab);
      }
      long jamaTime = System.nanoTime() - start;

      // ojAlgo benchmark
      start = System.nanoTime();
      for (int iter = 0; iter < N; iter++) {
        copyDenseStore(ojAlgoMat, ojAlgoWork, n);
        solver.decompose(ojAlgoWork);
        solver.getSolution(ojAlgoB);
      }
      long ojAlgoTime = System.nanoTime() - start;

      double jamaPerCall = (double) jamaTime / N;
      double ojAlgoPerCall = (double) ojAlgoTime / N;
      double speedup = jamaPerCall / ojAlgoPerCall;

      logger.info(
          String.format("%-6d %-12.0f %-12.0f %-10.2fx", n, jamaPerCall, ojAlgoPerCall, speedup));

      // Just verify both return reasonable values
      assertTrue(jamaPerCall > 0);
      assertTrue(ojAlgoPerCall > 0);
    }


    // Also benchmark ojAlgo with explicit work copy.
    logger.info("=== ojAlgo Work-Copy Solve ===");
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

      Primitive64Store ojAlgoMat = Primitive64Store.FACTORY.rows(aData);
      Primitive64Store ojAlgoB = Primitive64Store.FACTORY.make(n, 1);
      for (int i = 0; i < n; i++) {
        ojAlgoB.set(i, 0, bData[i]);
      }
      Primitive64Store ojAlgoWork = Primitive64Store.FACTORY.make(n, n);
      LU<Double> solver2 = LU.PRIMITIVE.make(n, n);

      // Warmup
      for (int w = 0; w < warmup; w++) {
        copyDenseStore(ojAlgoMat, ojAlgoWork, n);
        solver2.decompose(ojAlgoWork);
        solver2.getSolution(ojAlgoB);
      }

      // With pre-allocated copy
      long start = System.nanoTime();
      for (int iter = 0; iter < N; iter++) {
        copyDenseStore(ojAlgoMat, ojAlgoWork, n);
        solver2.decompose(ojAlgoWork);
        solver2.getSolution(ojAlgoB);
      }
      long time = System.nanoTime() - start;
      System.out
          .println(String.format("n=%d ojAlgo work-copy: %.0f ns/call", n, (double) time / N));
    }

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

    logger.info("=== Init Level Cost Breakdown (10-comp SRK) ===");
    logger.info(String.format("init(1) fugacities:       %6.1f us", init1us));
    logger.info(
        String.format("init(2) + T,P derivs:    %6.1f us (delta: +%.1f us)", init2us, tpDerivCost));
    logger.info(String.format("init(3) + comp derivs:   %6.1f us (delta: +%.1f us)", init3us,
        compDerivCost));
    logger.info(String.format("T,P derivative cost:     %6.1f us (%.1f%% of init(3))", tpDerivCost,
        wastedPercent));
    logger
        .info(String.format("Wasted per Newton iter:  %6.1f us (logfugcoefdT + dP)", tpDerivCost));

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

    logger.info("=== init(3) All-at-once vs Per-phase ===");
    logger.info(String.format("init(3) all:     %6.1f us", allTime / 1000.0 / N));
    logger.info(String.format("init(3,0)+init(3,1): %6.1f us", perPhaseTime / 1000.0 / N));
    logger.info(String.format("Ratio: %.2f", (double) perPhaseTime / allTime));

  }

  /**
   * Benchmark the full Newton solver step vs SS step to understand relative costs.
   */
  @Test
  void benchmarkNewtonVsSSIteration() {
    int N = 200;
    int warmup = 20;

    // Measure a single SS step vs Newton step on a 10-comp two-phase system
    logger.info("=== Newton vs SS Iteration Cost (10-comp, two-phase) ===");

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
    logger.info(String.format("SS step (init(1) x2):    %8.1f us", ssUs));
    logger.info(String.format("Newton step (init(3) + solve): %8.1f us", newtonUs));
    logger.info(String.format("Newton/SS ratio:         %8.1fx", newtonUs / ssUs));

  }

  /**
   * Measure allocation overhead: JAMA creates new Matrix objects each solve. ojAlgo can reuse
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
      jamaMat.solve(jamab);
    }
    long jamaTime = System.nanoTime() - start;

    // ojAlgo: pre-allocated solver and work buffer
    Primitive64Store ojAlgoMat = Primitive64Store.FACTORY.rows(aData);
    Primitive64Store ojAlgoB = Primitive64Store.FACTORY.make(n, 1);
    for (int i = 0; i < n; i++) {
      ojAlgoB.set(i, 0, bData[i]);
    }
    Primitive64Store ojAlgoWork = Primitive64Store.FACTORY.make(n, n);
    LU<Double> solver = LU.PRIMITIVE.make(n, n);

    // Warmup
    for (int w = 0; w < 5000; w++) {
      copyDenseStore(ojAlgoMat, ojAlgoWork, n);
      solver.decompose(ojAlgoWork);
      solver.getSolution(ojAlgoB);
    }

    start = System.nanoTime();
    for (int iter = 0; iter < N; iter++) {
      copyDenseStore(ojAlgoMat, ojAlgoWork, n);
      solver.decompose(ojAlgoWork);
      solver.getSolution(ojAlgoB);
    }
    long ojAlgoTime = System.nanoTime() - start;

    logger.info("=== Allocation Overhead (n=10, " + N + " solves) ===");
    logger.info(String.format("JAMA (new alloc each): %.0f ns/call", (double) jamaTime / N));
    logger.info(String.format("ojAlgo (work-copy):     %.0f ns/call", (double) ojAlgoTime / N));
    logger.info(String.format("Speedup:               %.2fx", (double) jamaTime / ojAlgoTime));
    System.out.println();
  }

  /**
   * Copies a dense square matrix into a reusable ojAlgo work store.
   *
   * @param source source dense matrix
   * @param target target dense matrix
   * @param size matrix dimension
   */
  private void copyDenseStore(Primitive64Store source, Primitive64Store target, int size) {
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        target.set(i, j, source.get(i, j));
      }
    }
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
