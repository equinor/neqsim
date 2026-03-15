package neqsim.thermodynamicoperations.flashops;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Benchmark tests for TPmultiflash performance analysis.
 *
 * <p>
 * Profiles the three main cost centers: 1. Stability analysis (clone + SS iterations with
 * init(1,1)) 2. solveBeta (double init(1) per iteration, SimpleMatrix allocation) 3. Overall run()
 * including seeding and phase removal
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TPMultiflashBenchmarkTest {

  /**
   * Benchmark: 10-component natural gas with water, multiPhaseCheck on. This is the most common use
   * case - gas/oil/water three-phase.
   */
  @Test
  @DisplayName("Benchmark 10-comp gas+water multiflash")
  public void benchmark10CompGasWater() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.04);
    fluid.addComponent("H2S", 0.01);
    fluid.addComponent("water", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Warm up
    ops.TPflash();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(50.0);
    fluid.init(0);

    int nRuns = 200;
    long start = System.nanoTime();
    for (int i = 0; i < nRuns; i++) {
      fluid.setTemperature(273.15 + 25.0);
      fluid.setPressure(50.0);
      fluid.init(0);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    double perFlashUs = elapsed / 1000.0 / nRuns;
    System.out.println("=== 10-comp gas+water multiflash ===");
    System.out.println("Phases found: " + fluid.getNumberOfPhases());
    System.out.println("Total: " + elapsed / 1_000_000 + " ms for " + nRuns + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", perFlashUs) + " us");
    System.out.println();
  }

  /**
   * Benchmark: 3-component sour gas (CH4/CO2/H2S) at conditions where 3 phases may exist.
   */
  @Test
  @DisplayName("Benchmark sour gas three-phase")
  public void benchmarkSourGasThreePhase() {
    SystemInterface fluid = new SystemPrEos(210.0, 55.0);
    fluid.addComponent("methane", 49.88);
    fluid.addComponent("CO2", 9.87);
    fluid.addComponent("H2S", 40.22);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Warm up
    ops.TPflash();

    int nRuns = 100;
    long start = System.nanoTime();
    for (int i = 0; i < nRuns; i++) {
      fluid.setTemperature(210.0);
      fluid.setPressure(55.0);
      fluid.init(0);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    double perFlashUs = elapsed / 1000.0 / nRuns;
    System.out.println("=== Sour gas three-phase (CH4/CO2/H2S) ===");
    System.out.println("Phases found: " + fluid.getNumberOfPhases());
    System.out.println("Total: " + elapsed / 1_000_000 + " ms for " + nRuns + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", perFlashUs) + " us");
    System.out.println();
  }

  /**
   * Benchmark: CPA system with water and MEG (common hydrate inhibitor scenario).
   */
  @Test
  @DisplayName("Benchmark CPA gas+water+MEG")
  public void benchmarkCPAMEG() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 10.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("water", 0.12);
    fluid.addComponent("MEG", 0.05);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Warm up
    ops.TPflash();

    int nRuns = 50;
    long start = System.nanoTime();
    for (int i = 0; i < nRuns; i++) {
      fluid.setTemperature(273.15 + 10.0);
      fluid.setPressure(50.0);
      fluid.init(0);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    double perFlashUs = elapsed / 1000.0 / nRuns;
    System.out.println("=== CPA gas+water+MEG multiflash ===");
    System.out.println("Phases found: " + fluid.getNumberOfPhases());
    System.out.println("Total: " + elapsed / 1_000_000 + " ms for " + nRuns + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", perFlashUs) + " us");
    System.out.println();
  }

  /**
   * Benchmark: Profile the cost breakdown of solveBeta. Measures time for init(1), calcE/setXY, and
   * SimpleMatrix ops.
   */
  @Test
  @DisplayName("Profile solveBeta cost breakdown")
  public void profileSolveBeta() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.04);
    fluid.addComponent("H2S", 0.01);
    fluid.addComponent("water", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // First do a normal flash so we know it finds multiple phases
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    System.out.println("=== solveBeta Cost Analysis ===");
    System.out.println("Phases: " + fluid.getNumberOfPhases());

    // Measure init(1) for all phases
    int nIter = 5000;
    long start = System.nanoTime();
    for (int i = 0; i < nIter; i++) {
      fluid.init(1);
    }
    long initAllTime = System.nanoTime() - start;

    // Measure per-phase init
    start = System.nanoTime();
    for (int i = 0; i < nIter; i++) {
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        fluid.init(1, p);
      }
    }
    long initPerPhaseTime = System.nanoTime() - start;

    System.out.println("init(1) all phases: " + initAllTime / 1000 / nIter + " us/call");
    System.out
        .println("init(1,p) per-phase summed: " + initPerPhaseTime / 1000 / nIter + " us/call");
    System.out.println(
        "Ratio all/per-phase: " + String.format("%.2f", (double) initAllTime / initPerPhaseTime));
    System.out.println();
  }

  /**
   * Compare multiflash with multiPhaseCheck=false (pure 2-phase TPflash) to see overhead.
   */
  @Test
  @DisplayName("Compare multiflash vs two-phase flash overhead")
  public void compareMultiflashOverhead() {
    int nRuns = 500;

    // Two-phase flash (no multiPhaseCheck)
    SystemInterface fluid2 = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid2.addComponent("methane", 0.70);
    fluid2.addComponent("ethane", 0.08);
    fluid2.addComponent("propane", 0.05);
    fluid2.addComponent("n-butane", 0.03);
    fluid2.addComponent("water", 0.04);
    fluid2.setMixingRule("classic");
    fluid2.setMultiPhaseCheck(false);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.TPflash(); // warm up

    long start2 = System.nanoTime();
    for (int i = 0; i < nRuns; i++) {
      fluid2.setTemperature(273.15 + 25.0);
      fluid2.setPressure(50.0);
      fluid2.init(0);
      ops2.TPflash();
    }
    long elapsed2 = System.nanoTime() - start2;

    // Multi-phase flash
    SystemInterface fluidM = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluidM.addComponent("methane", 0.70);
    fluidM.addComponent("ethane", 0.08);
    fluidM.addComponent("propane", 0.05);
    fluidM.addComponent("n-butane", 0.03);
    fluidM.addComponent("water", 0.04);
    fluidM.setMixingRule("classic");
    fluidM.setMultiPhaseCheck(true);

    ThermodynamicOperations opsM = new ThermodynamicOperations(fluidM);
    opsM.TPflash(); // warm up

    long startM = System.nanoTime();
    for (int i = 0; i < nRuns; i++) {
      fluidM.setTemperature(273.15 + 25.0);
      fluidM.setPressure(50.0);
      fluidM.init(0);
      opsM.TPflash();
    }
    long elapsedM = System.nanoTime() - startM;

    double twoPhaseUs = elapsed2 / 1000.0 / nRuns;
    double multiUs = elapsedM / 1000.0 / nRuns;

    System.out.println("=== Two-phase vs Multiflash Overhead ===");
    System.out.println("Two-phase flash: " + String.format("%.1f", twoPhaseUs) + " us/flash"
        + " (phases: " + fluid2.getNumberOfPhases() + ")");
    System.out.println("Multiflash:      " + String.format("%.1f", multiUs) + " us/flash"
        + " (phases: " + fluidM.getNumberOfPhases() + ")");
    System.out.println("Multiflash overhead: " + String.format("%.1fx", multiUs / twoPhaseUs));
    System.out.println();
  }

  /**
   * Profile stability analysis specifically - the most expensive part. Clone cost vs SS iteration
   * cost.
   */
  @Test
  @DisplayName("Profile stability analysis cost breakdown")
  public void profileStabilityAnalysis() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("water", 0.04);
    fluid.setMixingRule("classic");

    // First init the system properly
    fluid.init(0);
    fluid.init(1);

    // Measure clone cost
    int nClones = 2000;
    long start = System.nanoTime();
    for (int i = 0; i < nClones; i++) {
      SystemInterface clone = fluid.clone();
    }
    long cloneTime = System.nanoTime() - start;

    // Measure init(1,1) on a clone (the per-iteration cost in stability analysis)
    SystemInterface clone = fluid.clone();
    int nInits = 5000;
    start = System.nanoTime();
    for (int i = 0; i < nInits; i++) {
      clone.init(1, 1);
    }
    long initTime = System.nanoTime() - start;

    // Measure init(1) full (what solveBeta calls twice per iteration)
    start = System.nanoTime();
    for (int i = 0; i < nInits; i++) {
      fluid.init(1);
    }
    long initFullTime = System.nanoTime() - start;

    System.out.println("=== Stability Analysis Cost Breakdown ===");
    System.out.println("clone() cost: " + cloneTime / 1000 / nClones + " us/clone");
    System.out.println("init(1,1) cost: " + initTime / 1000 / nInits + " us/call");
    System.out.println("init(1) full cost: " + initFullTime / 1000 / nInits + " us/call");
    System.out.println();

    // Compute relative cost for typical stability analysis:
    // 1 clone + ~50 iters * init(1,1) for each trial component
    int numComp = fluid.getNumberOfComponents();
    int typicalIters = 50;
    double cloneCostUs = cloneTime / 1000.0 / nClones;
    double initCostUs = initTime / 1000.0 / nInits;
    double totalStabilityUs = numComp * (cloneCostUs + typicalIters * initCostUs);

    System.out.println("Estimated stability analysis cost for " + numComp + " components:");
    System.out.println("  Clone overhead: " + String.format("%.0f", numComp * cloneCostUs) + " us ("
        + String.format("%.1f%%", 100.0 * numComp * cloneCostUs / totalStabilityUs) + ")");
    System.out.println("  init(1,1) iterations: "
        + String.format("%.0f", numComp * typicalIters * initCostUs) + " us ("
        + String.format("%.1f%%", 100.0 * numComp * typicalIters * initCostUs / totalStabilityUs)
        + ")");
    System.out.println("  Total estimated: " + String.format("%.0f", totalStabilityUs) + " us");
    System.out.println();
  }

  /**
   * Benchmark: 20-comp well fluid with water and multiPhaseCheck. This is the heavy-duty case.
   */
  @Test
  @DisplayName("Benchmark 20-comp well fluid multiflash")
  public void benchmark20CompWellFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 100.0);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("i-pentane", 0.015);
    fluid.addComponent("n-pentane", 0.015);
    fluid.addComponent("n-hexane", 0.02);
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("n-octane", 0.015);
    fluid.addComponent("n-nonane", 0.01);
    fluid.addComponent("nC10", 0.01);
    fluid.addComponent("nC11", 0.005);
    fluid.addComponent("nC12", 0.005);
    fluid.addComponent("H2S", 0.005);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash(); // warm up

    int nRuns = 50;
    long start = System.nanoTime();
    for (int i = 0; i < nRuns; i++) {
      fluid.setTemperature(273.15 + 80.0);
      fluid.setPressure(100.0);
      fluid.init(0);
      ops.TPflash();
    }
    long elapsed = System.nanoTime() - start;

    double perFlashUs = elapsed / 1000.0 / nRuns;
    System.out.println("=== 20-comp well fluid multiflash ===");
    System.out.println("Phases found: " + fluid.getNumberOfPhases());
    System.out.println("Total: " + elapsed / 1_000_000 + " ms for " + nRuns + " flashes");
    System.out.println("Per flash: " + String.format("%.1f", perFlashUs) + " us");
    System.out.println();
  }

  /**
   * Benchmark solveBeta SimpleMatrix allocation overhead. Tests whether pre-allocating DMatrixRMaj
   * would help.
   */
  @Test
  @DisplayName("Profile SimpleMatrix allocation overhead in solveBeta")
  public void profileSimpleMatrixOverhead() {
    int numPhases = 3;
    int numComp = 10;
    int nIter = 50000;

    // Current approach: create new SimpleMatrix each iteration
    long start = System.nanoTime();
    for (int i = 0; i < nIter; i++) {
      double[][] dQdbeta = new double[numPhases][1];
      double[][] Qmatrix = new double[numPhases][numPhases];
      // Simulate filling
      for (int k = 0; k < numPhases; k++) {
        dQdbeta[k][0] = 1.0;
        for (int j = 0; j < numPhases; j++) {
          Qmatrix[k][j] = k == j ? 1.001 : 0.001;
        }
      }
      org.ejml.simple.SimpleMatrix dQM = new org.ejml.simple.SimpleMatrix(dQdbeta);
      org.ejml.simple.SimpleMatrix dQdBM = new org.ejml.simple.SimpleMatrix(Qmatrix);
      org.ejml.simple.SimpleMatrix ans = dQdBM.solve(dQM).transpose();
    }
    long simpleTime = System.nanoTime() - start;

    // Alternative: pre-allocate DMatrixRMaj, use EJML directly
    org.ejml.data.DMatrixRMaj jacMat = new org.ejml.data.DMatrixRMaj(numPhases, numPhases);
    org.ejml.data.DMatrixRMaj rhsMat = new org.ejml.data.DMatrixRMaj(numPhases, 1);
    org.ejml.data.DMatrixRMaj solMat = new org.ejml.data.DMatrixRMaj(numPhases, 1);
    org.ejml.dense.row.factory.LinearSolverFactory_DDRM factory =
        new org.ejml.dense.row.factory.LinearSolverFactory_DDRM();

    start = System.nanoTime();
    for (int i = 0; i < nIter; i++) {
      for (int k = 0; k < numPhases; k++) {
        rhsMat.set(k, 0, 1.0);
        for (int j = 0; j < numPhases; j++) {
          jacMat.set(k, j, k == j ? 1.001 : 0.001);
        }
      }
      org.ejml.interfaces.linsol.LinearSolverDense<org.ejml.data.DMatrixRMaj> solver =
          org.ejml.dense.row.factory.LinearSolverFactory_DDRM.lu(numPhases);
      solver.setA(jacMat);
      solver.solve(rhsMat, solMat);
    }
    long directTime = System.nanoTime() - start;

    System.out.println("=== SimpleMatrix vs Direct EJML in solveBeta ===");
    System.out.println("SimpleMatrix (current): " + simpleTime / 1000 / nIter + " us/iter");
    System.out.println("Direct EJML:            " + directTime / 1000 / nIter + " us/iter");
    System.out.println("Speedup: " + String.format("%.2fx", (double) simpleTime / directTime));
    System.out.println("Note: solveBeta runs ~50 iters, total saving: "
        + String.format("%.0f", 50.0 * (simpleTime - directTime) / 1000.0 / nIter) + " us");
    System.out.println();
  }

  /**
   * Measure the double init(1) in solveBeta: currently calls system.init(1) before AND after
   * setXY(). Tests whether the first init(1) could be replaced by per-phase init.
   */
  @Test
  @DisplayName("Profile double init(1) in solveBeta")
  public void profileDoubleInitInSolveBeta() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("water", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    int nPhases = fluid.getNumberOfPhases();
    int nIter = 5000;

    // Cost of 2x init(1) - current solveBeta approach
    long start = System.nanoTime();
    for (int i = 0; i < nIter; i++) {
      fluid.init(1);
      fluid.init(1);
    }
    long doubleInitTime = System.nanoTime() - start;

    // Cost of 1x init(1) - if we could eliminate one
    start = System.nanoTime();
    for (int i = 0; i < nIter; i++) {
      fluid.init(1);
    }
    long singleInitTime = System.nanoTime() - start;

    double doubleUs = doubleInitTime / 1000.0 / nIter;
    double singleUs = singleInitTime / 1000.0 / nIter;

    System.out.println("=== Double init(1) in solveBeta ===");
    System.out.println("Phases: " + nPhases);
    System.out.println("2x init(1): " + String.format("%.1f", doubleUs) + " us");
    System.out.println("1x init(1): " + String.format("%.1f", singleUs) + " us");
    System.out.println("Savings if eliminated: " + String.format("%.1f", doubleUs - singleUs)
        + " us/iter x 50 iters = " + String.format("%.0f", (doubleUs - singleUs) * 50) + " us");
    System.out.println();
  }
}
