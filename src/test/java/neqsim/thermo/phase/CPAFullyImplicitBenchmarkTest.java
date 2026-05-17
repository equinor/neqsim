package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Detailed benchmark comparing fully implicit CPA vs standard nested for multi-component gas-oil-
 * aqueous systems with MEG and oil components.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CPAFullyImplicitBenchmarkTest extends neqsim.NeqSimTest {

  /** Number of TP flash repetitions for timing. */
  private static final int N_REPS = 10;

  // ------- Accuracy tests -------

  /**
   * Gas condensate with water and MEG at HP separator conditions.
   */
  @Test
  void testGasCondensateWaterMEG() {
    double T = 273.15 + 40.0;
    double P = 120.0;
    CompareResult r = compareFlash(T, P, true, true, true);
    printResult("GasCondensate+Water+MEG (40C, 120bar)", r);
    assertMatch(r, 0.02);
  }

  /**
   * Rich gas with water at pipeline conditions.
   */
  @Test
  void testRichGasWater() {
    double T = 273.15 + 5.0;
    double P = 80.0;
    CompareResult r = compareFlash(T, P, true, false, false);
    printResult("RichGas+Water (5C, 80bar)", r);
    assertMatch(r, 0.02);
  }

  /**
   * Oil with dissolved gas and water at separator conditions.
   */
  @Test
  void testOilGasWater() {
    double T = 273.15 + 70.0;
    double P = 30.0;
    CompareResult r = compareFlash(T, P, true, true, false);
    printResult("Oil+Gas+Water (70C, 30bar)", r);
    assertMatch(r, 0.02);
  }

  /**
   * Full wellstream with oil, gas, water and MEG at wellhead conditions.
   */
  @Test
  void testWellstreamWithMEG() {
    double T = 273.15 + 60.0;
    double P = 200.0;
    CompareResult r = compareFlash(T, P, true, true, true);
    printResult("Wellstream+MEG (60C, 200bar)", r);
    assertMatch(r, 0.02);
  }

  /**
   * Heavy oil with water at low pressure.
   */
  @Test
  void testHeavyOilWater() {
    double T = 273.15 + 90.0;
    double P = 5.0;

    SystemInterface standard = createHeavyOilWater(new SystemSrkCPAstatoil(T, P));
    SystemInterface implicit = createHeavyOilWater(new SystemSrkCPAstatoilFullyImplicit(T, P));

    runFlash(standard);
    runFlash(implicit);

    CompareResult r = buildResult(standard, implicit);
    printResult("HeavyOil+Water (90C, 5bar)", r);
    assertMatch(r, 0.02);
  }

  /**
   * Gas dehydration conditions - lean gas with traces of water and MEG.
   */
  @Test
  void testGasDehydration() {
    double T = 273.15 + 30.0;
    double P = 70.0;

    SystemInterface standard = createDehydration(new SystemSrkCPAstatoil(T, P));
    SystemInterface implicit = createDehydration(new SystemSrkCPAstatoilFullyImplicit(T, P));

    runFlash(standard);
    runFlash(implicit);

    CompareResult r = buildResult(standard, implicit);
    printResult("GasDehydration (30C, 70bar)", r);
    assertMatch(r, 0.02);
  }

  /**
   * Supercritical gas + aqueous phase at high pressure.
   */
  @Test
  void testHighPressureGasWater() {
    double T = 273.15 + 150.0;
    double P = 500.0;
    CompareResult r = compareFlash(T, P, false, false, false);
    printResult("Gas+Water HP (150C, 500bar)", r);
    assertMatch(r, 0.02);
  }

  // ------- Comprehensive timing benchmark -------

  /**
   * Detailed timing benchmark across multiple fluid types and conditions.
   */
  @Test
  void testDetailedTimingBenchmark() {
    System.out.println("\n====================================================================");
    System.out.println("  CPA Fully Implicit vs Standard Nested — Detailed Benchmark");
    System.out.println("====================================================================");
    System.out.println(
        String.format("%-45s %8s %8s %6s %6s", "Case", "Std(ms)", "Impl(ms)", "Ratio", "Phases"));
    System.out.println("--------------------------------------------------------------------");

    // Case 1: Pure water sweep
    benchmarkCase("Pure water (T sweep)", new CaseBuilder() {
      @Override
      public void configure(SystemInterface sys, int idx) {
        sys.addComponent("water", 1.0);
        sys.setMixingRule(10);
      }
    }, generateTP(273.15 + 10, 10, 1.0, 0));

    // Case 2: Water + methane
    benchmarkCase("Methane + water", new CaseBuilder() {
      @Override
      public void configure(SystemInterface sys, int idx) {
        sys.addComponent("methane", 0.95);
        sys.addComponent("water", 0.05);
        sys.setMixingRule(10);
        sys.setMultiPhaseCheck(true);
      }
    }, generateTP(273.15 + 5, 15, 50.0, 30));

    // Case 3: Natural gas + water + MEG
    benchmarkCase("NatGas + water + MEG", new CaseBuilder() {
      @Override
      public void configure(SystemInterface sys, int idx) {
        sys.addComponent("methane", 0.80);
        sys.addComponent("ethane", 0.06);
        sys.addComponent("propane", 0.03);
        sys.addComponent("n-butane", 0.01);
        sys.addComponent("water", 0.08);
        sys.addComponent("MEG", 0.02);
        sys.setMixingRule(10);
        sys.setMultiPhaseCheck(true);
      }
    }, generateTP(273.15 + 5, 20, 50.0, 30));

    // Case 4: Gas condensate + water
    benchmarkCase("GasCondensate + water", new CaseBuilder() {
      @Override
      public void configure(SystemInterface sys, int idx) {
        sys.addComponent("methane", 0.70);
        sys.addComponent("ethane", 0.07);
        sys.addComponent("propane", 0.05);
        sys.addComponent("n-butane", 0.03);
        sys.addComponent("n-pentane", 0.02);
        sys.addComponent("n-hexane", 0.01);
        sys.addComponent("n-heptane", 0.005);
        sys.addComponent("water", 0.065);
        sys.setMixingRule(10);
        sys.setMultiPhaseCheck(true);
      }
    }, generateTP(273.15 + 20, 20, 80.0, 40));

    // Case 5: Oil + gas + water + MEG
    benchmarkCase("Oil+Gas+Water+MEG", new CaseBuilder() {
      @Override
      public void configure(SystemInterface sys, int idx) {
        sys.addComponent("methane", 0.40);
        sys.addComponent("ethane", 0.05);
        sys.addComponent("propane", 0.04);
        sys.addComponent("n-butane", 0.03);
        sys.addComponent("n-pentane", 0.03);
        sys.addComponent("n-hexane", 0.04);
        sys.addComponent("n-heptane", 0.08);
        sys.addComponent("n-octane", 0.05);
        sys.addComponent("water", 0.25);
        sys.addComponent("MEG", 0.03);
        sys.setMixingRule(10);
        sys.setMultiPhaseCheck(true);
      }
    }, generateTP(273.15 + 40, 20, 100.0, 50));

    // Case 6: MEG-water binary sweep
    benchmarkCase("MEG + water (P sweep)", new CaseBuilder() {
      @Override
      public void configure(SystemInterface sys, int idx) {
        sys.addComponent("MEG", 0.4);
        sys.addComponent("water", 0.6);
        sys.setMixingRule(10);
      }
    }, generateTP(273.15 + 25, 0, 10.0, 40));

    // Case 7: Methanol + water + methane
    benchmarkCase("Methanol+Water+Methane", new CaseBuilder() {
      @Override
      public void configure(SystemInterface sys, int idx) {
        sys.addComponent("methane", 0.85);
        sys.addComponent("methanol", 0.05);
        sys.addComponent("water", 0.10);
        sys.setMixingRule(10);
        sys.setMultiPhaseCheck(true);
      }
    }, generateTP(273.15 + 0, 15, 60.0, 30));

    System.out.println("====================================================================\n");

    assertTrue(true, "Benchmark completed");
  }

  // ------- Phase-by-phase comparison -------

  /**
   * Detailed phase property comparison for oil+gas+water+MEG system.
   */
  @Test
  void testPhasePropertyComparison() {
    double T = 273.15 + 50.0;
    double P = 80.0;

    SystemInterface standard = createMultiComp(new SystemSrkCPAstatoil(T, P), true, true, true);
    SystemInterface implicit =
        createMultiComp(new SystemSrkCPAstatoilFullyImplicit(T, P), true, true, true);

    runFlash(standard);
    runFlash(implicit);

    System.out.println("\n=== Phase Property Comparison (Oil+Gas+Water+MEG, 50C, 80bar) ===");
    System.out.println(String.format("%-12s %-10s %-15s %-15s %-8s", "Phase", "Property",
        "Standard", "Implicit", "Err(%)"));
    System.out.println("----------------------------------------------------------------");

    int nPhStd = standard.getNumberOfPhases();
    int nPhImpl = implicit.getNumberOfPhases();
    assertEquals(nPhStd, nPhImpl, "Number of phases must match");

    for (int p = 0; p < nPhStd; p++) {
      String phName = standard.getPhase(p).getType().toString();
      double densStd = standard.getPhase(p).getDensity("kg/m3");
      double densImpl = implicit.getPhase(p).getDensity("kg/m3");
      double zStd = standard.getPhase(p).getZ();
      double zImpl = implicit.getPhase(p).getZ();
      double betaStd = standard.getPhase(p).getBeta();
      double betaImpl = implicit.getPhase(p).getBeta();

      printProp(phName, "Density", densStd, densImpl);
      printProp(phName, "Z", zStd, zImpl);
      printProp(phName, "Beta", betaStd, betaImpl);

      assertEquals(densStd, densImpl, Math.max(Math.abs(densStd) * 0.02, 0.01),
          phName + " density mismatch");
      assertEquals(zStd, zImpl, Math.max(Math.abs(zStd) * 0.02, 1e-6), phName + " Z mismatch");
    }
    System.out.println("----------------------------------------------------------------\n");
  }

  // ------- Helper methods -------

  private interface CaseBuilder {
    void configure(SystemInterface sys, int idx);
  }

  private static class CompareResult {
    int nPhasesStd;
    int nPhasesImpl;
    double densStdPh0;
    double densImplPh0;
    double zStdPh0;
    double zImplPh0;
  }

  private double[][] generateTP(double tBase, double tStep, double pBase, double pStep) {
    double[][] tp = new double[N_REPS][2];
    for (int i = 0; i < N_REPS; i++) {
      tp[i][0] = tBase + i * tStep;
      tp[i][1] = pBase + i * pStep;
    }
    return tp;
  }

  private void benchmarkCase(String label, CaseBuilder builder, double[][] tp) {
    // Warm up both solvers for JIT compilation
    for (int i = 0; i < 2; i++) {
      SystemInterface warmupStd = new SystemSrkCPAstatoil(tp[0][0], tp[0][1]);
      builder.configure(warmupStd, 0);
      ThermodynamicOperations opsStd = new ThermodynamicOperations(warmupStd);
      opsStd.TPflash();

      SystemInterface warmupImpl = new SystemSrkCPAstatoilFullyImplicit(tp[0][0], tp[0][1]);
      builder.configure(warmupImpl, 0);
      ThermodynamicOperations opsImpl = new ThermodynamicOperations(warmupImpl);
      opsImpl.TPflash();
    }

    int nPhases = -1;

    long startStd = System.nanoTime();
    for (int i = 0; i < tp.length; i++) {
      SystemInterface sys = new SystemSrkCPAstatoil(tp[i][0], tp[i][1]);
      builder.configure(sys, i);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      nPhases = sys.getNumberOfPhases();
    }
    long timeStd = System.nanoTime() - startStd;

    long startImpl = System.nanoTime();
    for (int i = 0; i < tp.length; i++) {
      SystemInterface sys = new SystemSrkCPAstatoilFullyImplicit(tp[i][0], tp[i][1]);
      builder.configure(sys, i);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
    }
    long timeImpl = System.nanoTime() - startImpl;

    double ratio = (double) timeImpl / timeStd;
    System.out.println(String.format("%-45s %8d %8d %6.2f %6d", label, timeStd / 1_000_000,
        timeImpl / 1_000_000, ratio, nPhases));
  }

  private CompareResult compareFlash(double T, double P, boolean gas, boolean oil, boolean meg) {
    SystemInterface standard = createMultiComp(new SystemSrkCPAstatoil(T, P), gas, oil, meg);
    SystemInterface implicit =
        createMultiComp(new SystemSrkCPAstatoilFullyImplicit(T, P), gas, oil, meg);

    runFlash(standard);
    runFlash(implicit);

    return buildResult(standard, implicit);
  }

  private SystemInterface createMultiComp(SystemInterface sys, boolean gas, boolean oil,
      boolean meg) {
    if (gas) {
      sys.addComponent("methane", 0.50);
      sys.addComponent("ethane", 0.05);
      sys.addComponent("propane", 0.03);
    }
    if (oil) {
      sys.addComponent("n-pentane", 0.03);
      sys.addComponent("n-hexane", 0.03);
      sys.addComponent("n-heptane", 0.05);
      sys.addComponent("n-octane", 0.03);
    }
    sys.addComponent("water", 0.20);
    if (meg) {
      sys.addComponent("MEG", 0.05);
    }
    // Normalize: remaining to methane if gas, otherwise to water
    sys.setMixingRule(10);
    sys.setMultiPhaseCheck(true);
    return sys;
  }

  private SystemInterface createHeavyOilWater(SystemInterface sys) {
    sys.addComponent("methane", 0.10);
    sys.addComponent("n-pentane", 0.05);
    sys.addComponent("n-hexane", 0.08);
    sys.addComponent("n-heptane", 0.15);
    sys.addComponent("n-octane", 0.15);
    sys.addComponent("n-nonane", 0.10);
    sys.addComponent("nC10", 0.07);
    sys.addComponent("water", 0.30);
    sys.setMixingRule(10);
    sys.setMultiPhaseCheck(true);
    return sys;
  }

  private SystemInterface createDehydration(SystemInterface sys) {
    sys.addComponent("methane", 0.90);
    sys.addComponent("ethane", 0.04);
    sys.addComponent("propane", 0.01);
    sys.addComponent("CO2", 0.02);
    sys.addComponent("water", 0.02);
    sys.addComponent("MEG", 0.01);
    sys.setMixingRule(10);
    sys.setMultiPhaseCheck(true);
    return sys;
  }

  private void runFlash(SystemInterface sys) {
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
  }

  private CompareResult buildResult(SystemInterface standard, SystemInterface implicit) {
    CompareResult r = new CompareResult();
    r.nPhasesStd = standard.getNumberOfPhases();
    r.nPhasesImpl = implicit.getNumberOfPhases();
    r.densStdPh0 = standard.getPhase(0).getDensity("kg/m3");
    r.densImplPh0 = implicit.getPhase(0).getDensity("kg/m3");
    r.zStdPh0 = standard.getPhase(0).getZ();
    r.zImplPh0 = implicit.getPhase(0).getZ();
    return r;
  }

  private void printResult(String label, CompareResult r) {
    double errDens =
        Math.abs(r.densStdPh0) > 1e-6 ? Math.abs(r.densStdPh0 - r.densImplPh0) / r.densStdPh0 * 100
            : 0;
    double errZ =
        Math.abs(r.zStdPh0) > 1e-6 ? Math.abs(r.zStdPh0 - r.zImplPh0) / r.zStdPh0 * 100 : 0;
    System.out.println(String.format("  %-40s phases=%d/%d  dens_err=%.4f%%  Z_err=%.4f%%", label,
        r.nPhasesStd, r.nPhasesImpl, errDens, errZ));
  }

  private void printProp(String phase, String prop, double std, double impl) {
    double err = Math.abs(std) > 1e-10 ? Math.abs(std - impl) / std * 100.0 : 0.0;
    System.out
        .println(String.format("%-12s %-10s %15.6f %15.6f %7.4f%%", phase, prop, std, impl, err));
  }

  private void assertMatch(CompareResult r, double relTol) {
    assertEquals(r.nPhasesStd, r.nPhasesImpl, "Number of phases must match");
    assertEquals(r.densStdPh0, r.densImplPh0, Math.max(Math.abs(r.densStdPh0) * relTol, 0.01),
        "Phase 0 density mismatch");
    assertEquals(r.zStdPh0, r.zImplPh0, Math.max(Math.abs(r.zStdPh0) * relTol, 1e-6),
        "Phase 0 Z mismatch");
  }
}
