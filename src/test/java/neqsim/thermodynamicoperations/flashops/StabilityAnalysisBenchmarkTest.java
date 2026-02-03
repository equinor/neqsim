package neqsim.thermodynamicoperations.flashops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Benchmark test to analyze stability analysis performance for electrolyte CPA systems.
 *
 * <p>
 * This test helps identify performance bottlenecks in stability analysis by measuring time spent in
 * different parts of the algorithm.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class StabilityAnalysisBenchmarkTest {

  private SystemInterface fluid;

  /**
   * Set up the test fluid - electrolyte CPA with MEG and brine.
   */
  @BeforeEach
  public void setUp() {
    fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);

    fluid.addComponent("water", 0.494505);
    fluid.addComponent("MEG", 0.164835);
    fluid.addComponent("methane", 0.247253);
    fluid.addComponent("ethane", 0.0164835);
    fluid.addComponent("propane", 0.010989);
    fluid.addComponent("i-butane", 0.00549451);
    fluid.addComponent("n-butane", 0.00549451);
    fluid.addComponent("Na+", 0.0274725);
    fluid.addComponent("Cl-", 0.0274725);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
  }

  /**
   * Benchmark a single TPflash with stability analysis.
   */
  @Test
  @DisplayName("Benchmark single TPflash with stability analysis")
  public void benchmarkSingleTPflash() {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Warm up
    ops.TPflash();

    // Reset and measure
    fluid.setTemperature(273.15 - 10.0);
    fluid.init(0);

    long startTime = System.nanoTime();
    ops.TPflash();
    long endTime = System.nanoTime();

    double elapsedMs = (endTime - startTime) / 1_000_000.0;
    System.out.println("=== Single TPflash Benchmark ===");
    System.out.println("TPflash time: " + String.format("%.2f", elapsedMs) + " ms");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    System.out.println("Number of components: " + fluid.getNumberOfComponents());
  }

  /**
   * Benchmark multiple TPflash calls to see consistency.
   */
  @Test
  @DisplayName("Benchmark multiple TPflash calls")
  public void benchmarkMultipleTPflash() {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Warm up
    ops.TPflash();

    System.out.println("=== Multiple TPflash Benchmark ===");

    double[] temperatures =
        {273.15 + 10.0, 273.15 - 5.0, 273.15 - 10.0, 273.15 - 15.0, 273.15 - 20.0};
    double totalTime = 0;

    for (int i = 0; i < temperatures.length; i++) {
      fluid.setTemperature(temperatures[i]);
      fluid.init(0);

      long startTime = System.nanoTime();
      ops.TPflash();
      long endTime = System.nanoTime();

      double elapsedMs = (endTime - startTime) / 1_000_000.0;
      totalTime += elapsedMs;
      System.out.println("T=" + String.format("%.1f", temperatures[i] - 273.15) + "°C: "
          + String.format("%.2f", elapsedMs) + " ms, phases=" + fluid.getNumberOfPhases());
    }

    System.out.println("Total time for " + temperatures.length + " flashes: "
        + String.format("%.2f", totalTime) + " ms");
    System.out.println("Average time per flash: "
        + String.format("%.2f", totalTime / temperatures.length) + " ms");
  }

  /**
   * Benchmark TPflash with and without multiPhaseCheck to quantify stability analysis cost.
   */
  @Test
  @DisplayName("Compare TPflash with and without stability analysis")
  public void compareWithAndWithoutStabilityAnalysis() {
    System.out.println("=== Stability Analysis Cost Comparison ===");

    // With stability analysis
    fluid.setMultiPhaseCheck(true);
    fluid.setTemperature(273.15 - 10.0);
    fluid.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    long startTime = System.nanoTime();
    ops.TPflash();
    long endTime = System.nanoTime();
    double withStabilityMs = (endTime - startTime) / 1_000_000.0;
    int phasesWithStability = fluid.getNumberOfPhases();

    // Without stability analysis (just 2-phase flash)
    SystemInterface fluid2 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 50.0);
    fluid2.addComponent("water", 0.494505);
    fluid2.addComponent("MEG", 0.164835);
    fluid2.addComponent("methane", 0.247253);
    fluid2.addComponent("ethane", 0.0164835);
    fluid2.addComponent("propane", 0.010989);
    fluid2.addComponent("i-butane", 0.00549451);
    fluid2.addComponent("n-butane", 0.00549451);
    fluid2.addComponent("Na+", 0.0274725);
    fluid2.addComponent("Cl-", 0.0274725);
    fluid2.setMixingRule(10);
    fluid2.setMultiPhaseCheck(false); // No stability analysis

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);

    startTime = System.nanoTime();
    ops2.TPflash();
    endTime = System.nanoTime();
    double withoutStabilityMs = (endTime - startTime) / 1_000_000.0;
    int phasesWithoutStability = fluid2.getNumberOfPhases();

    System.out.println("WITH stability analysis: " + String.format("%.2f", withStabilityMs)
        + " ms, phases=" + phasesWithStability);
    System.out.println("WITHOUT stability analysis: " + String.format("%.2f", withoutStabilityMs)
        + " ms, phases=" + phasesWithoutStability);
    System.out.println("Stability analysis overhead: "
        + String.format("%.2f", withStabilityMs - withoutStabilityMs) + " ms ("
        + String.format("%.1f", (withStabilityMs / withoutStabilityMs - 1) * 100) + "% slower)");
  }

  /**
   * Benchmark phase init() calls which are expensive for electrolyte systems.
   */
  @Test
  @DisplayName("Benchmark phase initialization cost")
  public void benchmarkPhaseInitCost() {
    System.out.println("=== Phase Initialization Cost ===");

    fluid.setTemperature(273.15 - 10.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Debug: show phase info
    System.out.println("After flash:");
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      System.out.println("  Phase " + p + ": " + fluid.getPhase(p).getPhaseTypeName() + " ("
          + fluid.getPhase(p).getClass().getSimpleName() + ")" + " moles="
          + fluid.getPhase(p).getNumberOfMolesInPhase());
    }

    // Measure init(1) cost for different phases
    int numIterations = 10;

    for (int phaseNum = 0; phaseNum < Math.min(fluid.getNumberOfPhases(), 2); phaseNum++) {
      long startTime = System.nanoTime();
      for (int i = 0; i < numIterations; i++) {
        fluid.init(1, phaseNum);
      }
      long endTime = System.nanoTime();
      double avgMs = (endTime - startTime) / 1_000_000.0 / numIterations;
      System.out.println("Phase " + phaseNum + " (" + fluid.getPhase(phaseNum).getPhaseTypeName()
          + ") init(1) avg: " + String.format("%.3f", avgMs) + " ms");
    }

    // Measure init(3) cost (with derivatives)
    for (int phaseNum = 0; phaseNum < Math.min(fluid.getNumberOfPhases(), 2); phaseNum++) {
      long startTime = System.nanoTime();
      for (int i = 0; i < numIterations; i++) {
        fluid.init(3, phaseNum);
      }
      long endTime = System.nanoTime();
      double avgMs = (endTime - startTime) / 1_000_000.0 / numIterations;
      System.out.println("Phase " + phaseNum + " (" + fluid.getPhase(phaseNum).getPhaseTypeName()
          + ") init(3) avg: " + String.format("%.3f", avgMs) + " ms");
    }
  }

  /**
   * Benchmark fugacity coefficient calculations.
   */
  @Test
  @DisplayName("Benchmark fugacity coefficient calculation cost")
  public void benchmarkFugacityCoefficientCost() {
    System.out.println("=== Fugacity Coefficient Calculation Cost ===");

    fluid.setTemperature(273.15 - 10.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    int numIterations = 100;

    for (int phaseNum = 0; phaseNum < fluid.getNumberOfPhases(); phaseNum++) {
      long totalTime = 0;
      for (int i = 0; i < numIterations; i++) {
        long startTime = System.nanoTime();
        for (int comp = 0; comp < fluid.getNumberOfComponents(); comp++) {
          fluid.getPhase(phaseNum).getComponent(comp).fugcoef(fluid.getPhase(phaseNum));
        }
        long endTime = System.nanoTime();
        totalTime += (endTime - startTime);
      }
      double avgMs = totalTime / 1_000_000.0 / numIterations;
      System.out.println("Phase " + phaseNum + " (" + fluid.getPhase(phaseNum).getPhaseTypeName()
          + ") " + "all fugcoef avg: " + String.format("%.3f", avgMs) + " ms");
    }
  }

  /**
   * Test without ions to see how much they contribute to slowdown.
   */
  @Test
  @DisplayName("Compare performance with and without ions")
  public void compareWithAndWithoutIons() {
    System.out.println("=== Ion Impact on Performance ===");

    // With ions (original fluid)
    fluid.setTemperature(273.15 - 10.0);
    fluid.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    long startTime = System.nanoTime();
    ops.TPflash();
    long endTime = System.nanoTime();
    double withIonsMs = (endTime - startTime) / 1_000_000.0;

    // Without ions
    SystemInterface fluidNoIons = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 50.0);
    fluidNoIons.addComponent("water", 0.494505 + 0.0274725 + 0.0274725); // Add ion moles to water
    fluidNoIons.addComponent("MEG", 0.164835);
    fluidNoIons.addComponent("methane", 0.247253);
    fluidNoIons.addComponent("ethane", 0.0164835);
    fluidNoIons.addComponent("propane", 0.010989);
    fluidNoIons.addComponent("i-butane", 0.00549451);
    fluidNoIons.addComponent("n-butane", 0.00549451);
    // No Na+ or Cl-
    fluidNoIons.setMixingRule(10);
    fluidNoIons.setMultiPhaseCheck(true);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluidNoIons);

    startTime = System.nanoTime();
    ops2.TPflash();
    endTime = System.nanoTime();
    double withoutIonsMs = (endTime - startTime) / 1_000_000.0;

    System.out.println("WITH ions: " + String.format("%.2f", withIonsMs) + " ms, "
        + fluid.getNumberOfComponents() + " components");
    System.out.println("WITHOUT ions: " + String.format("%.2f", withoutIonsMs) + " ms, "
        + fluidNoIons.getNumberOfComponents() + " components");
    System.out.println("Ion overhead: " + String.format("%.2f", withIonsMs - withoutIonsMs)
        + " ms (" + String.format("%.1f", (withIonsMs / withoutIonsMs - 1) * 100) + "% slower)");
  }

  /**
   * Profile where time is spent in TPmultiflash by counting iterations.
   */
  @Test
  @DisplayName("Analyze TPmultiflash iteration behavior")
  @Disabled("This test requires code instrumentation - run manually")
  public void analyzeTPmultiflashIterations() {
    // This test would need instrumentation in TPmultiflash to count iterations
    // and measure time per component trial
    System.out.println("=== TPmultiflash Iteration Analysis ===");
    System.out.println("This test requires code instrumentation.");
    System.out.println("Key areas to profile:");
    System.out.println("1. Number of stability analysis iterations per component");
    System.out.println("2. Time spent in clonedSystem.init(1,1) calls");
    System.out.println("3. Time spent in clonedSystem.init(3,1) calls (with derivatives)");
    System.out.println("4. Number of components used as trial phases");
  }

  /**
   * Benchmark system cloning which is used in stability analysis.
   */
  @Test
  @DisplayName("Benchmark system cloning cost")
  public void benchmarkSystemCloningCost() {
    System.out.println("=== System Cloning Cost ===");

    fluid.setTemperature(273.15 - 10.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    int numIterations = 50;
    long startTime = System.nanoTime();
    for (int i = 0; i < numIterations; i++) {
      SystemInterface clone = fluid.clone();
      clone.init(0);
    }
    long endTime = System.nanoTime();
    double avgMs = (endTime - startTime) / 1_000_000.0 / numIterations;
    System.out.println("System clone + init(0) avg: " + String.format("%.3f", avgMs) + " ms");
  }

  /**
   * Test that simulates stability analysis: clone, set ions to 0, then init. This tests whether the
   * optimization to skip electrolyte calculations for ion-free trial phases is working.
   */
  @Test
  @DisplayName("Verify ion-free trial phase optimization")
  public void verifyIonFreeTrialPhaseOptimization() {
    System.out.println("=== Ion-Free Trial Phase Optimization Test ===");

    fluid.setTemperature(273.15 - 10.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    System.out.println("Number of phases after flash: " + fluid.getNumberOfPhases());
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      System.out.println("Phase " + p + ": " + fluid.getPhase(p).getPhaseTypeName() + " ("
          + fluid.getPhase(p).getClass().getSimpleName() + ")");
    }

    // Clone the system (simulating what stability analysis does)
    SystemInterface clone = fluid.clone();

    int numIterations = 100;

    // First: measure init(1,1) with ions present (original mole fractions)
    System.out.println("\n--- Phase 1 tests ---");
    System.out.println("Phase 1 type: " + clone.getPhase(1).getPhaseTypeName());
    System.out.println("Phase 1 class: " + clone.getPhase(1).getClass().getSimpleName());

    // Check ion mole fractions in phase 1
    System.out.println("Ion mole fractions in phase 1 before setx(0):");
    for (int i = 0; i < clone.getNumberOfComponents(); i++) {
      if (clone.getPhase(1).getComponent(i).getIonicCharge() != 0) {
        System.out.println("  " + clone.getPhase(1).getComponent(i).getComponentName() + ": x="
            + clone.getPhase(1).getComponent(i).getx());
      }
    }

    long startWithIons = System.nanoTime();
    for (int i = 0; i < numIterations; i++) {
      clone.init(1, 1);
    }
    long endWithIons = System.nanoTime();
    double avgWithIonsMs = (endWithIons - startWithIons) / 1_000_000.0 / numIterations;

    // Now set ions to x=0 (simulating what stability analysis does for trial phases)
    for (int i = 0; i < clone.getNumberOfComponents(); i++) {
      if (clone.getPhase(1).getComponent(i).getIonicCharge() != 0) {
        clone.getPhase(1).getComponent(i).setx(0.0);
      }
    }

    // Check ion mole fractions after
    System.out.println("Ion mole fractions in phase 1 after setx(0):");
    for (int i = 0; i < clone.getNumberOfComponents(); i++) {
      if (clone.getPhase(1).getComponent(i).getIonicCharge() != 0) {
        System.out.println("  " + clone.getPhase(1).getComponent(i).getComponentName() + ": x="
            + clone.getPhase(1).getComponent(i).getx());
      }
    }

    // Measure init(1,1) with ions set to 0
    long startNoIons = System.nanoTime();
    for (int i = 0; i < numIterations; i++) {
      clone.init(1, 1);
    }
    long endNoIons = System.nanoTime();
    double avgNoIonsMs = (endNoIons - startNoIons) / 1_000_000.0 / numIterations;

    System.out.println("init(1,1) with ions present: " + String.format("%.3f", avgWithIonsMs)
        + " ms avg over " + numIterations + " iterations");
    System.out.println("init(1,1) with ions x=0: " + String.format("%.3f", avgNoIonsMs)
        + " ms avg over " + numIterations + " iterations");
    double speedup = avgWithIonsMs / avgNoIonsMs;
    System.out.println("Speedup from ion optimization: " + String.format("%.1fx", speedup));

    // Also check phase 0 (aqueous phase with ions) - this is NOT optimized
    System.out.println("\n--- Phase 0 tests ---");
    System.out.println("Phase 0 type: " + clone.getPhase(0).getPhaseTypeName());
    System.out.println("Phase 0 class: " + clone.getPhase(0).getClass().getSimpleName());

    long startPhase0 = System.nanoTime();
    for (int i = 0; i < numIterations; i++) {
      clone.init(1, 0);
    }
    long endPhase0 = System.nanoTime();
    double avgPhase0Ms = (endPhase0 - startPhase0) / 1_000_000.0 / numIterations;
    System.out.println("init(1,0) phase 0: " + String.format("%.3f", avgPhase0Ms) + " ms avg");
  }
}
