package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.FileWriter;
import java.io.PrintWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Benchmark tests for TPflash performance analysis.
 *
 * <p>
 * Measures wall-clock time for TPflash under different scenarios to identify bottlenecks. Tests
 * cover: init level costs, successive substitution vs Newton, stability analysis overhead, and
 * component count scaling.
 * </p>
 *
 * @author benchmarking
 * @version 1.0
 */
@Tag("slow")
class TPFlashBenchmarkTest {

  /**
   * Creates a simple 3-component natural gas system for benchmarking.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bara
   * @return configured system
   */
  private SystemInterface createSimpleGas(double T, double P) {
    SystemInterface sys = new SystemSrkEos(T, P);
    sys.addComponent("methane", 0.90);
    sys.addComponent("ethane", 0.07);
    sys.addComponent("propane", 0.03);
    sys.setMixingRule("classic");
    return sys;
  }

  /**
   * Creates a 10-component natural gas system for benchmarking.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bara
   * @return configured system
   */
  private SystemInterface createMediumGas(double T, double P) {
    SystemInterface sys = new SystemSrkEos(T, P);
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

  /**
   * Creates a 20+ component well fluid for benchmarking.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bara
   * @return configured system
   */
  private SystemInterface createWellFluid(double T, double P) {
    SystemInterface sys = new SystemSrkEos(T, P);
    sys.addComponent("nitrogen", 1.0);
    sys.addComponent("CO2", 2.0);
    sys.addComponent("H2S", 0.5);
    sys.addComponent("methane", 60.0);
    sys.addComponent("ethane", 8.0);
    sys.addComponent("propane", 5.0);
    sys.addComponent("i-butane", 2.0);
    sys.addComponent("n-butane", 3.0);
    sys.addComponent("i-pentane", 1.5);
    sys.addComponent("n-pentane", 1.2);
    sys.addComponent("n-hexane", 1.0);
    sys.addComponent("n-heptane", 0.8);
    sys.addComponent("n-octane", 0.6);
    sys.addComponent("n-nonane", 0.4);
    sys.addComponent("nC10", 0.3);
    sys.addComponent("nC11", 0.2);
    sys.addComponent("nC12", 0.15);
    sys.addComponent("nC13", 0.1);
    sys.addComponent("nC14", 0.08);
    sys.addComponent("nC15", 0.05);
    sys.setMixingRule("classic");
    return sys;
  }

  /**
   * Benchmark: Measure cost of init(0), init(1), init(2), init(3) individually.
   */
  @Test
  void benchmarkInitLevels() {
    SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
    sys.init(0);
    sys.init(1); // warm up

    int N = 5000;

    // Benchmark init(0)
    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(0);
    }
    long init0Time = System.nanoTime() - start;

    // Benchmark init(1) - fugacity coefficients
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(1);
    }
    long init1Time = System.nanoTime() - start;

    // Benchmark init(2) - adds T and P derivatives
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(2);
    }
    long init2Time = System.nanoTime() - start;

    // Benchmark init(3) - adds composition derivatives
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(3);
    }
    long init3Time = System.nanoTime() - start;

    System.out.println("=== Init Level Benchmark (10-comp SRK, " + N + " iterations) ===");
    System.out.println("init(0): " + (init0Time / 1_000_000) + " ms total, "
        + String.format("%.1f", init0Time / 1000.0 / N) + " us/call");
    System.out.println("init(1): " + (init1Time / 1_000_000) + " ms total, "
        + String.format("%.1f", init1Time / 1000.0 / N) + " us/call");
    System.out.println("init(2): " + (init2Time / 1_000_000) + " ms total, "
        + String.format("%.1f", init2Time / 1000.0 / N) + " us/call");
    System.out.println("init(3): " + (init3Time / 1_000_000) + " ms total, "
        + String.format("%.1f", init3Time / 1000.0 / N) + " us/call");
    System.out
        .println("Ratio init(1)/init(0): " + String.format("%.1f", (double) init1Time / init0Time));
    System.out
        .println("Ratio init(2)/init(1): " + String.format("%.1f", (double) init2Time / init1Time));
    System.out
        .println("Ratio init(3)/init(1): " + String.format("%.1f", (double) init3Time / init1Time));
    System.out.println();

    // init(1) should be more expensive than init(0) since it computes fugacity coefficients
    assertTrue(init1Time > init0Time, "init(1) should be more expensive than init(0)");
  }

  /**
   * Benchmark: Measure init(1) cost for a single phase vs all phases.
   */
  @Test
  void benchmarkInitSinglePhaseVsAll() {
    SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
    sys.init(0);
    sys.init(1);

    int N = 5000;

    // init(1) for all phases (default)
    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(1);
    }
    long allPhaseTime = System.nanoTime() - start;

    // init(1, 0) for phase 0 only
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(1, 0);
    }
    long phase0Time = System.nanoTime() - start;

    // init(1, 1) for phase 1 only
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(1, 1);
    }
    long phase1Time = System.nanoTime() - start;

    System.out.println("=== Init Single Phase vs All Phases (10-comp SRK) ===");
    System.out.println("init(1) all phases: " + (allPhaseTime / 1_000_000) + " ms, "
        + String.format("%.1f", allPhaseTime / 1000.0 / N) + " us/call");
    System.out.println("init(1, 0) phase 0: " + (phase0Time / 1_000_000) + " ms, "
        + String.format("%.1f", phase0Time / 1000.0 / N) + " us/call");
    System.out.println("init(1, 1) phase 1: " + (phase1Time / 1_000_000) + " ms, "
        + String.format("%.1f", phase1Time / 1000.0 / N) + " us/call");
    System.out
        .println("Ratio all/single: " + String.format("%.1f", (double) allPhaseTime / phase0Time));
    System.out.println();
  }

  /**
   * Benchmark: Full TPflash for two-phase case - Simple gas at 25C, 50bar.
   */
  @Test
  void benchmarkTPflashTwoPhaseSimple() {
    int N = 2000;
    int warmup = 100;

    // Warmup
    for (int i = 0; i < warmup; i++) {
      SystemInterface sys = createSimpleGas(273.15 + 25.0, 50.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }

    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      SystemInterface sys = createSimpleGas(273.15 - 40.0, 30.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    System.out.println("=== TPflash Two-Phase (3-comp, -40C, 30bar) ===");
    System.out.println("Total: " + (elapsed / 1_000_000) + " ms for " + N + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", elapsed / 1000.0 / N) + " us");
    System.out.println();
  }

  /**
   * Benchmark: TPflash for single-phase case (all gas, no stability check needed).
   */
  @Test
  void benchmarkTPflashSinglePhase() {
    int N = 2000;
    int warmup = 100;

    // Warmup
    for (int i = 0; i < warmup; i++) {
      SystemInterface sys = createMediumGas(273.15 + 100.0, 10.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }

    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      SystemInterface sys = createMediumGas(273.15 + 100.0, 10.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    System.out.println("=== TPflash Single-Phase (10-comp, 100C, 10bar - all gas) ===");
    System.out.println("Total: " + (elapsed / 1_000_000) + " ms for " + N + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", elapsed / 1000.0 / N) + " us");
    System.out.println();
  }

  /**
   * Benchmark: TPflash for a two-phase 10-component mixture.
   */
  @Test
  void benchmarkTPflashTwoPhaseMedium() {
    int N = 1000;
    int warmup = 50;

    // Two-phase: 25C, 50 bar should give 2-phase for this rich gas
    for (int i = 0; i < warmup; i++) {
      SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }

    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    System.out.println("=== TPflash Two-Phase (10-comp, 25C, 50bar) ===");
    System.out.println("Total: " + (elapsed / 1_000_000) + " ms for " + N + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", elapsed / 1000.0 / N) + " us");
    System.out.println();
  }

  /**
   * Benchmark: TPflash for a 20-component well fluid.
   */
  @Test
  void benchmarkTPflashWellFluid() {
    int N = 500;
    int warmup = 25;

    for (int i = 0; i < warmup; i++) {
      SystemInterface sys = createWellFluid(273.15 + 80.0, 100.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }

    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      SystemInterface sys = createWellFluid(273.15 + 80.0, 100.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    System.out.println("=== TPflash (20-comp well fluid, 80C, 100bar) ===");
    System.out.println("Total: " + (elapsed / 1_000_000) + " ms for " + N + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", elapsed / 1000.0 / N) + " us");
    System.out.println();
  }

  /**
   * Benchmark: Component count scaling - measure how TPflash time scales with number of components.
   */
  @Test
  void benchmarkComponentCountScaling() {
    int N = 500;

    String[] components3 = {"methane", "ethane", "propane"};
    String[] components6 = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-hexane"};
    String[] components10 = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
        "n-butane", "i-pentane", "n-pentane", "n-hexane"};
    String[] components15 =
        {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
            "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10", "nC11"};

    String[][] allComponents = {components3, components6, components10, components15};

    System.out.println("=== Component Count Scaling (two-phase, -20C, 30bar) ===");
    for (String[] comps : allComponents) {
      // Warmup
      for (int w = 0; w < 50; w++) {
        SystemInterface sys = new SystemSrkEos(273.15 - 20.0, 30.0);
        for (String c : comps) {
          sys.addComponent(c, 1.0);
        }
        sys.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }

      long start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        SystemInterface sys = new SystemSrkEos(273.15 - 20.0, 30.0);
        for (String c : comps) {
          sys.addComponent(c, 1.0);
        }
        sys.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      long elapsed = System.nanoTime() - start;

      System.out.println(comps.length + " components: " + (elapsed / 1_000_000) + " ms total, "
          + String.format("%.1f", elapsed / 1000.0 / N) + " us/flash");
    }
    System.out.println();
  }

  /**
   * Benchmark: Stability analysis time as separate measurement. Creates a stable single-phase
   * system at high T, low P to force stability analysis.
   */
  @Test
  void benchmarkStabilityAnalysis() {
    int N = 1000;
    int warmup = 50;

    // System at conditions where stability analysis is triggered
    // High temp, low pressure => single phase => stability check runs
    for (int i = 0; i < warmup; i++) {
      SystemInterface sys = createMediumGas(273.15 + 150.0, 5.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }

    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      SystemInterface sys = createMediumGas(273.15 + 150.0, 5.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    System.out
        .println("=== TPflash with Stability Analysis (10-comp, 150C, 5bar - single phase) ===");
    System.out.println("Total: " + (elapsed / 1_000_000) + " ms for " + N + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", elapsed / 1000.0 / N) + " us");
    System.out.println();
  }

  /**
   * Benchmark: Detailed breakdown of TPflash operations by counting init calls. Measures a single
   * flash with instrumented timing.
   */
  @Test
  void benchmarkTPflashDetailed() {
    // Two-phase case: 10-component at 25C, 50bar
    SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);

    // Manual breakdown: measure each step separately
    long t0 = System.nanoTime();
    sys.init(0);
    long tInit0 = System.nanoTime() - t0;

    t0 = System.nanoTime();
    sys.init(1);
    long tInit1First = System.nanoTime() - t0;

    // Rachford-Rice solve
    t0 = System.nanoTime();
    RachfordRice rr = new RachfordRice();
    try {
      double beta = rr.calcBeta(sys.getKvector(), sys.getzvector());
    } catch (Exception e) {
      // ignore
    }
    long tRR = System.nanoTime() - t0;

    // calc_x_y
    t0 = System.nanoTime();
    sys.calc_x_y();
    long tXY = System.nanoTime() - t0;

    // Second init(1) (after x,y update)
    t0 = System.nanoTime();
    sys.init(1);
    long tInit1Second = System.nanoTime() - t0;

    System.out.println("=== Detailed TPflash Operation Costs (10-comp SRK) ===");
    System.out.println("init(0):     " + String.format("%7.1f", tInit0 / 1000.0) + " us");
    System.out.println("init(1) 1st: " + String.format("%7.1f", tInit1First / 1000.0) + " us");
    System.out.println("Rachford-Rice: " + String.format("%5.1f", tRR / 1000.0) + " us");
    System.out.println("calc_x_y:    " + String.format("%7.1f", tXY / 1000.0) + " us");
    System.out.println("init(1) 2nd: " + String.format("%7.1f", tInit1Second / 1000.0) + " us");
    System.out.println();
  }

  /**
   * Benchmark: Measure molarVolume solver convergence (major part of init(1)).
   */
  @Test
  void benchmarkMolarVolumeSolver() {
    SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
    sys.init(0);

    int N = 5000;

    // The molarVolume solve is called as part of init(1).
    // Track how many init(1) calls we can do per second.
    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      sys.init(1);
    }
    long elapsed = System.nanoTime() - start;

    double flashesPerSec = N * 1e9 / elapsed;
    System.out.println("=== Molar Volume Solver Throughput ===");
    System.out.println("init(1) calls/sec: " + String.format("%.0f", flashesPerSec));
    System.out.println("Per call: " + String.format("%.1f", elapsed / 1000.0 / N) + " us");
    System.out.println();
  }

  /**
   * Benchmark: Compare PR vs SRK EOS performance.
   */
  @Test
  void benchmarkPRvsSRK() {
    int N = 1000;

    // SRK
    long start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      SystemInterface sys = new SystemSrkEos(273.15 + 25.0, 50.0);
      sys.addComponent("methane", 80.0);
      sys.addComponent("ethane", 6.0);
      sys.addComponent("propane", 3.0);
      sys.addComponent("n-butane", 2.0);
      sys.addComponent("n-pentane", 1.0);
      sys.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }
    long srkTime = System.nanoTime() - start;

    // PR
    start = System.nanoTime();
    for (int i = 0; i < N; i++) {
      SystemInterface sys = new SystemPrEos(273.15 + 25.0, 50.0);
      sys.addComponent("methane", 80.0);
      sys.addComponent("ethane", 6.0);
      sys.addComponent("propane", 3.0);
      sys.addComponent("n-butane", 2.0);
      sys.addComponent("n-pentane", 1.0);
      sys.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }
    long prTime = System.nanoTime() - start;

    System.out.println("=== PR vs SRK EOS (5-comp, -20C, 30bar) ===");
    System.out.println("SRK: " + (srkTime / 1_000_000) + " ms, "
        + String.format("%.1f", srkTime / 1000.0 / N) + " us/flash");
    System.out.println("PR:  " + (prTime / 1_000_000) + " ms, "
        + String.format("%.1f", prTime / 1000.0 / N) + " us/flash");
    System.out.println("PR/SRK ratio: " + String.format("%.2f", (double) prTime / srkTime));
    System.out.println();
  }

  /**
   * * Comprehensive benchmark writing results to a file for analysis.
   *
   * @throws Exception if file write errors occur
   */
  @Test
  void runAllBenchmarksToFile() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("=== NeqSim TPflash Performance Benchmark Report ===\n\n");

    // --- Init Level Costs ---
    {
      SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
      sys.init(0);
      sys.init(1);
      int N = 5000;

      long start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        sys.init(0);
      }
      long init0Time = System.nanoTime() - start;

      start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        sys.init(1);
      }
      long init1Time = System.nanoTime() - start;

      start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        sys.init(2);
      }
      long init2Time = System.nanoTime() - start;

      start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        sys.init(3);
      }
      long init3Time = System.nanoTime() - start;

      sb.append("--- Init Level Benchmark (10-comp SRK, " + N + " iterations) ---\n");
      sb.append("init(0): " + (init0Time / 1_000_000) + " ms total, "
          + String.format("%.1f", init0Time / 1000.0 / N) + " us/call\n");
      sb.append("init(1): " + (init1Time / 1_000_000) + " ms total, "
          + String.format("%.1f", init1Time / 1000.0 / N) + " us/call\n");
      sb.append("init(2): " + (init2Time / 1_000_000) + " ms total, "
          + String.format("%.1f", init2Time / 1000.0 / N) + " us/call\n");
      sb.append("init(3): " + (init3Time / 1_000_000) + " ms total, "
          + String.format("%.1f", init3Time / 1000.0 / N) + " us/call\n");
      sb.append(
          "Ratio init(1)/init(0): " + String.format("%.2f", (double) init1Time / init0Time) + "\n");
      sb.append(
          "Ratio init(2)/init(1): " + String.format("%.2f", (double) init2Time / init1Time) + "\n");
      sb.append("Ratio init(3)/init(1): " + String.format("%.2f", (double) init3Time / init1Time)
          + "\n\n");
    }

    // --- Single Phase vs All Phase init ---
    {
      SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
      sys.init(0);
      sys.init(1);
      int N = 5000;

      long start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        sys.init(1);
      }
      long allPhaseTime = System.nanoTime() - start;

      start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        sys.init(1, 0);
      }
      long phase0Time = System.nanoTime() - start;

      sb.append("--- Single Phase vs All Phases (10-comp SRK) ---\n");
      sb.append(
          "init(1) all phases: " + String.format("%.1f", allPhaseTime / 1000.0 / N) + " us/call\n");
      sb.append("init(1, 0) phase 0 only: " + String.format("%.1f", phase0Time / 1000.0 / N)
          + " us/call\n");
      sb.append("Ratio all/single: " + String.format("%.2f", (double) allPhaseTime / phase0Time)
          + "\n\n");
    }

    // --- TPflash Scenarios ---
    {
      int N = 1000;
      int warmup = 100;

      // Simple 3-comp two-phase
      for (int i = 0; i < warmup; i++) {
        SystemInterface sys = createSimpleGas(273.15 - 40.0, 30.0);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      long start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        SystemInterface sys = createSimpleGas(273.15 - 40.0, 30.0);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      long simple2Phase = System.nanoTime() - start;

      // 10-comp single phase (high T, low P)
      for (int i = 0; i < warmup; i++) {
        SystemInterface sys = createMediumGas(273.15 + 150.0, 5.0);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        SystemInterface sys = createMediumGas(273.15 + 150.0, 5.0);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      long medium1Phase = System.nanoTime() - start;

      // 10-comp two-phase
      for (int i = 0; i < warmup; i++) {
        SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      long medium2Phase = System.nanoTime() - start;

      // 20-comp well fluid
      int N2 = 500;
      for (int i = 0; i < 25; i++) {
        SystemInterface sys = createWellFluid(273.15 + 80.0, 100.0);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      start = System.nanoTime();
      for (int i = 0; i < N2; i++) {
        SystemInterface sys = createWellFluid(273.15 + 80.0, 100.0);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      long well2Phase = System.nanoTime() - start;

      sb.append("--- TPflash Scenario Performance ---\n");
      sb.append("3-comp two-phase (-40C, 30bar):   "
          + String.format("%.0f", simple2Phase / 1000.0 / N) + " us/flash\n");
      sb.append("10-comp single-phase (150C, 5bar): "
          + String.format("%.0f", medium1Phase / 1000.0 / N) + " us/flash\n");
      sb.append("10-comp two-phase (25C, 50bar):   "
          + String.format("%.0f", medium2Phase / 1000.0 / N) + " us/flash\n");
      sb.append("20-comp two-phase (80C, 100bar):  "
          + String.format("%.0f", well2Phase / 1000.0 / N2) + " us/flash\n\n");
    }

    // --- Component scaling ---
    {
      int N = 500;
      String[][] allComponents = {{"methane", "ethane", "propane"},
          {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-hexane"},
          {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
              "n-pentane", "n-hexane"},
          {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
              "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10", "nC11"}};

      sb.append("--- Component Count Scaling (two-phase, -20C, 30bar) ---\n");
      for (String[] comps : allComponents) {
        for (int w = 0; w < 50; w++) {
          SystemInterface sys = new SystemSrkEos(273.15 - 20.0, 30.0);
          for (String c : comps) {
            sys.addComponent(c, 1.0);
          }
          sys.setMixingRule("classic");
          ThermodynamicOperations ops = new ThermodynamicOperations(sys);
          ops.TPflash();
        }
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
          SystemInterface sys = new SystemSrkEos(273.15 - 20.0, 30.0);
          for (String c : comps) {
            sys.addComponent(c, 1.0);
          }
          sys.setMixingRule("classic");
          ThermodynamicOperations ops = new ThermodynamicOperations(sys);
          ops.TPflash();
        }
        long elapsed = System.nanoTime() - start;
        sb.append(comps.length + " components: " + String.format("%.0f", elapsed / 1000.0 / N)
            + " us/flash\n");
      }
      sb.append("\n");
    }

    // --- PR vs SRK ---
    {
      int N = 1000;
      long start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        SystemInterface sys = new SystemSrkEos(273.15 + 25.0, 50.0);
        sys.addComponent("methane", 80.0);
        sys.addComponent("ethane", 6.0);
        sys.addComponent("propane", 3.0);
        sys.addComponent("n-butane", 2.0);
        sys.addComponent("n-pentane", 1.0);
        sys.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      long srkTime = System.nanoTime() - start;

      start = System.nanoTime();
      for (int i = 0; i < N; i++) {
        SystemInterface sys = new SystemPrEos(273.15 + 25.0, 50.0);
        sys.addComponent("methane", 80.0);
        sys.addComponent("ethane", 6.0);
        sys.addComponent("propane", 3.0);
        sys.addComponent("n-butane", 2.0);
        sys.addComponent("n-pentane", 1.0);
        sys.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
      }
      long prTime = System.nanoTime() - start;

      sb.append("--- PR vs SRK EOS (5-comp, 25C, 50bar) ---\n");
      sb.append("SRK: " + String.format("%.0f", srkTime / 1000.0 / N) + " us/flash\n");
      sb.append("PR:  " + String.format("%.0f", prTime / 1000.0 / N) + " us/flash\n");
      sb.append("PR/SRK ratio: " + String.format("%.2f", (double) prTime / srkTime) + "\n\n");
    }

    // --- Detailed TPflash breakdown ---
    {
      SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
      long t0 = System.nanoTime();
      sys.init(0);
      long tInit0 = System.nanoTime() - t0;

      t0 = System.nanoTime();
      sys.init(1);
      long tInit1 = System.nanoTime() - t0;

      t0 = System.nanoTime();
      RachfordRice rr = new RachfordRice();
      try {
        rr.calcBeta(sys.getKvector(), sys.getzvector());
      } catch (Exception e) {
        // ignore
      }
      long tRR = System.nanoTime() - t0;

      t0 = System.nanoTime();
      sys.calc_x_y();
      long tXY = System.nanoTime() - t0;

      t0 = System.nanoTime();
      sys.init(1);
      long tInit1b = System.nanoTime() - t0;

      sb.append("--- Detailed TPflash Operation Costs (10-comp SRK, single call) ---\n");
      sb.append("init(0):         " + String.format("%7.1f", tInit0 / 1000.0) + " us\n");
      sb.append("init(1) first:   " + String.format("%7.1f", tInit1 / 1000.0) + " us\n");
      sb.append("Rachford-Rice:   " + String.format("%7.1f", tRR / 1000.0) + " us\n");
      sb.append("calc_x_y:        " + String.format("%7.1f", tXY / 1000.0) + " us\n");
      sb.append("init(1) second:  " + String.format("%7.1f", tInit1b / 1000.0) + " us\n");
      sb.append("\n");
    }

    // Write to file
    try (PrintWriter out = new PrintWriter(new FileWriter("benchmark_results.txt"))) {
      out.print(sb.toString());
    }

    // Also verify the file was written
    assertTrue(new java.io.File("benchmark_results.txt").exists());
  }

  /**
   * * Counts init calls during a TPflash by comparing system state. This test verifies correctness
   * is maintained.
   */
  @Test
  void verifyTPflashResultsConsistency() {
    SystemInterface sys = createMediumGas(273.15 + 25.0, 50.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();

    // Verify mass balance
    double totalZ = 0.0;
    for (int i = 0; i < sys.getPhase(0).getNumberOfComponents(); i++) {
      totalZ += sys.getPhase(0).getComponent(i).getz();
    }
    assertEquals(1.0, totalZ, 1e-8, "Feed mole fractions should sum to 1.0");

    // Verify component material balance
    for (int i = 0; i < sys.getPhase(0).getNumberOfComponents(); i++) {
      double zi = sys.getPhase(0).getComponent(i).getz();
      double reconstructed = 0.0;
      for (int p = 0; p < sys.getNumberOfPhases(); p++) {
        reconstructed += sys.getBeta(p) * sys.getPhase(p).getComponent(i).getx();
      }
      assertEquals(zi, reconstructed, 1e-6,
          "Material balance for " + sys.getPhase(0).getComponent(i).getName());
    }

    // Verify fugacity equality between phases
    if (sys.getNumberOfPhases() == 2) {
      for (int i = 0; i < sys.getPhase(0).getNumberOfComponents(); i++) {
        double fug0 = sys.getPhase(0).getComponent(i).getFugacityCoefficient()
            * sys.getPhase(0).getComponent(i).getx();
        double fug1 = sys.getPhase(1).getComponent(i).getFugacityCoefficient()
            * sys.getPhase(1).getComponent(i).getx();
        if (fug0 > 1e-15 && fug1 > 1e-15) {
          double relErr = Math.abs(fug0 - fug1) / Math.max(fug0, fug1);
          assertTrue(relErr < 1e-6, "Fugacity equality for "
              + sys.getPhase(0).getComponent(i).getName() + " relErr=" + relErr);
        }
      }
    }
  }
}
