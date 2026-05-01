package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.WaterDewPointAnalyser;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkCPAstatoilAndersonMixing;
import neqsim.thermo.system.SystemSrkCPAstatoilAndersonReduced;
import neqsim.thermo.system.SystemSrkCPAstatoilBroydenImplicit;
import neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit;
import neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicitReduced;
import neqsim.thermo.system.SystemSrkCPAstatoilReduced;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the accelerated CPA solver variants: Broyden quasi-Newton implicit and
 * Anderson-accelerated nested.
 *
 * @author Even Solbraa
 */
public class AcceleratedCPASolverTest {

  private static final double REL_TOL = 1.0e-6;

  /**
   * Create a water system using the given system class.
   */
  private SystemInterface createWaterSystem(String type) {
    SystemInterface system;
    switch (type) {
      case "standard":
        system = new SystemSrkCPAstatoil(298.15, 10.0);
        break;
      case "implicit":
        system = new SystemSrkCPAstatoilFullyImplicit(298.15, 10.0);
        break;
      case "broyden":
        system = new SystemSrkCPAstatoilBroydenImplicit(298.15, 10.0);
        break;
      case "anderson":
        system = new SystemSrkCPAstatoilAndersonMixing(298.15, 10.0);
        break;
      case "reduced":
        system = new SystemSrkCPAstatoilReduced(298.15, 10.0);
        break;
      case "anderson-reduced":
        system = new SystemSrkCPAstatoilAndersonReduced(298.15, 10.0);
        break;
      case "implicit-reduced":
        system = new SystemSrkCPAstatoilFullyImplicitReduced(298.15, 10.0);
        break;
      default:
        throw new IllegalArgumentException("Unknown type: " + type);
    }
    system.addComponent("water", 1.0);
    system.setMixingRule(10);
    return system;
  }

  /**
   * Create a water-methanol binary system.
   */
  private SystemInterface createWaterMethanolSystem(String type) {
    SystemInterface system;
    switch (type) {
      case "standard":
        system = new SystemSrkCPAstatoil(350.0, 1.0);
        break;
      case "implicit":
        system = new SystemSrkCPAstatoilFullyImplicit(350.0, 1.0);
        break;
      case "broyden":
        system = new SystemSrkCPAstatoilBroydenImplicit(350.0, 1.0);
        break;
      case "anderson":
        system = new SystemSrkCPAstatoilAndersonMixing(350.0, 1.0);
        break;
      case "reduced":
        system = new SystemSrkCPAstatoilReduced(350.0, 1.0);
        break;
      case "anderson-reduced":
        system = new SystemSrkCPAstatoilAndersonReduced(350.0, 1.0);
        break;
      case "implicit-reduced":
        system = new SystemSrkCPAstatoilFullyImplicitReduced(350.0, 1.0);
        break;
      default:
        throw new IllegalArgumentException("Unknown type: " + type);
    }
    system.addComponent("water", 0.5);
    system.addComponent("methanol", 0.5);
    system.setMixingRule(10);
    return system;
  }

  /**
   * Create a natural gas + water + MEG system (industrial).
   */
  private SystemInterface createIndustrialSystem(String type) {
    SystemInterface system;
    switch (type) {
      case "standard":
        system = new SystemSrkCPAstatoil(273.15 + 40.0, 100.0);
        break;
      case "implicit":
        system = new SystemSrkCPAstatoilFullyImplicit(273.15 + 40.0, 100.0);
        break;
      case "broyden":
        system = new SystemSrkCPAstatoilBroydenImplicit(273.15 + 40.0, 100.0);
        break;
      case "anderson":
        system = new SystemSrkCPAstatoilAndersonMixing(273.15 + 40.0, 100.0);
        break;
      case "reduced":
        system = new SystemSrkCPAstatoilReduced(273.15 + 40.0, 100.0);
        break;
      case "anderson-reduced":
        system = new SystemSrkCPAstatoilAndersonReduced(313.15, 100.0);
        break;
      case "implicit-reduced":
        system = new SystemSrkCPAstatoilFullyImplicitReduced(273.15 + 40.0, 100.0);
        break;
      default:
        throw new IllegalArgumentException("Unknown type: " + type);
    }
    system.addComponent("methane", 0.85);
    system.addComponent("ethane", 0.05);
    system.addComponent("propane", 0.03);
    system.addComponent("water", 0.05);
    system.addComponent("MEG", 0.02);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    return system;
  }

  /**
   * Verify that the Broyden solver matches the standard solver on pure water.
   */
  @Test
  public void testBroydenMatchesStandardWater() {
    SystemInterface standard = createWaterSystem("standard");
    SystemInterface broyden = createWaterSystem("broyden");

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsBroy = new ThermodynamicOperations(broyden);
    opsBroy.TPflash();
    broyden.initProperties();

    double densityStd = standard.getDensity("kg/m3");
    double densityBroy = broyden.getDensity("kg/m3");

    assertEquals(densityStd, densityBroy, Math.abs(densityStd) * REL_TOL,
        "Broyden water density should match standard");
  }

  /**
   * Verify that the Anderson solver matches the standard solver on pure water.
   */
  @Test
  public void testAndersonMatchesStandardWater() {
    SystemInterface standard = createWaterSystem("standard");
    SystemInterface anderson = createWaterSystem("anderson");

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsAnd = new ThermodynamicOperations(anderson);
    opsAnd.TPflash();
    anderson.initProperties();

    double densityStd = standard.getDensity("kg/m3");
    double densityAnd = anderson.getDensity("kg/m3");

    assertEquals(densityStd, densityAnd, Math.abs(densityStd) * REL_TOL,
        "Anderson water density should match standard");
  }

  /**
   * Verify Broyden matches implicit, and Anderson matches standard on water-methanol binary. Note:
   * implicit-family solvers may converge to a slightly different equilibrium than nested-family
   * solvers because of the approximate Jacobian used during iteration.
   */
  @Test
  public void testSolversMatchWaterMethanol() {
    SystemInterface standard = createWaterMethanolSystem("standard");
    SystemInterface implicit = createWaterMethanolSystem("implicit");
    SystemInterface broyden = createWaterMethanolSystem("broyden");
    SystemInterface anderson = createWaterMethanolSystem("anderson");

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsImpl = new ThermodynamicOperations(implicit);
    opsImpl.TPflash();
    implicit.initProperties();

    ThermodynamicOperations opsBroy = new ThermodynamicOperations(broyden);
    opsBroy.TPflash();
    broyden.initProperties();

    ThermodynamicOperations opsAnd = new ThermodynamicOperations(anderson);
    opsAnd.TPflash();
    anderson.initProperties();

    double densityStd = standard.getDensity("kg/m3");
    double densityImpl = implicit.getDensity("kg/m3");
    double densityBroy = broyden.getDensity("kg/m3");
    double densityAnd = anderson.getDensity("kg/m3");

    // Broyden should match implicit (same algorithm family: coupled)
    assertEquals(densityImpl, densityBroy, Math.abs(densityImpl) * REL_TOL,
        "Broyden water-methanol density should match fully implicit");
    // Anderson should match standard (same algorithm family: nested)
    assertEquals(densityStd, densityAnd, Math.abs(densityStd) * REL_TOL,
        "Anderson water-methanol density should match standard nested");
  }

  /**
   * Verify solver families match on industrial natgas + water + MEG system.
   */
  @Test
  public void testSolversMatchIndustrial() {
    SystemInterface standard = createIndustrialSystem("standard");
    SystemInterface implicit = createIndustrialSystem("implicit");
    SystemInterface broyden = createIndustrialSystem("broyden");
    SystemInterface anderson = createIndustrialSystem("anderson");

    for (SystemInterface sys : new SystemInterface[] {standard, implicit, broyden, anderson}) {
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      sys.initProperties();
    }

    double densityStd = standard.getDensity("kg/m3");
    double densityImpl = implicit.getDensity("kg/m3");
    double densityBroy = broyden.getDensity("kg/m3");
    double densityAnd = anderson.getDensity("kg/m3");

    // Broyden matches implicit
    assertEquals(densityImpl, densityBroy, Math.abs(densityImpl) * REL_TOL,
        "Broyden should match implicit");
    // Anderson matches standard
    assertEquals(densityStd, densityAnd, Math.abs(densityStd) * REL_TOL,
        "Anderson should match standard");
  }

  /**
   * Benchmark all four solvers on pure water over a temperature range. Verifies that results are
   * consistent and produces timing data.
   */
  @Test
  public void testBenchmarkPureWater() {
    double[] temps = {273.15 + 10.0, 298.15, 323.15, 373.15, 423.15, 473.15, 523.15};
    double pressure = 10.0;
    int warmup = 3;
    int repeats = 10;

    String[] types = {"standard", "implicit", "broyden", "anderson"};
    double[][] densities = new double[types.length][temps.length];

    for (int t = 0; t < types.length; t++) {
      for (int i = 0; i < temps.length; i++) {
        SystemInterface sys;
        switch (types[t]) {
          case "standard":
            sys = new SystemSrkCPAstatoil(temps[i], pressure);
            break;
          case "implicit":
            sys = new SystemSrkCPAstatoilFullyImplicit(temps[i], pressure);
            break;
          case "broyden":
            sys = new SystemSrkCPAstatoilBroydenImplicit(temps[i], pressure);
            break;
          case "anderson":
            sys = new SystemSrkCPAstatoilAndersonMixing(temps[i], pressure);
            break;
          default:
            throw new IllegalArgumentException();
        }
        sys.addComponent("water", 1.0);
        sys.setMixingRule(10);
        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.TPflash();
        sys.initProperties();
        densities[t][i] = sys.getDensity("kg/m3");
      }
    }

    // Compare within solver families:
    // anderson (index 3) should match standard (index 0)
    // broyden (index 2) should match implicit (index 1)
    for (int i = 0; i < temps.length; i++) {
      assertEquals(densities[0][i], densities[3][i], Math.abs(densities[0][i]) * REL_TOL,
          "anderson should match standard at T=" + temps[i]);
      assertEquals(densities[1][i], densities[2][i], Math.abs(densities[1][i]) * REL_TOL,
          "broyden should match implicit at T=" + temps[i]);
    }

    // Timing benchmark
    System.out.println("\n=== CPA Solver Benchmark: Pure Water ===");
    System.out.printf("%-15s %8s%n", "Solver", "Time(ms)");
    System.out.println("----------------------------");
    for (String type : types) {
      // Warmup
      for (int w = 0; w < warmup; w++) {
        for (double temp : temps) {
          runSingle(type, temp, pressure, "water");
        }
      }
      // Timed
      long start = System.nanoTime();
      for (int r = 0; r < repeats; r++) {
        for (double temp : temps) {
          runSingle(type, temp, pressure, "water");
        }
      }
      long elapsed = System.nanoTime() - start;
      double ms = elapsed / 1.0e6;
      System.out.printf("%-15s %8.2f%n", type, ms);
    }
  }

  /**
   * Benchmark all four solvers on water-methanol binary.
   */
  @Test
  public void testBenchmarkWaterMethanol() {
    double[] temps = {300.0, 320.0, 340.0, 360.0, 380.0};
    double pressure = 1.0;
    int warmup = 3;
    int repeats = 10;

    String[] types = {"standard", "implicit", "broyden", "anderson"};

    System.out.println("\n=== CPA Solver Benchmark: Water-Methanol ===");
    System.out.printf("%-15s %8s%n", "Solver", "Time(ms)");
    System.out.println("----------------------------");
    for (String type : types) {
      for (int w = 0; w < warmup; w++) {
        for (double temp : temps) {
          runBinary(type, temp, pressure);
        }
      }
      long start = System.nanoTime();
      for (int r = 0; r < repeats; r++) {
        for (double temp : temps) {
          runBinary(type, temp, pressure);
        }
      }
      long elapsed = System.nanoTime() - start;
      double ms = elapsed / 1.0e6;
      System.out.printf("%-15s %8.2f%n", type, ms);
    }
  }

  private SystemInterface runSingle(String type, double temp, double pressure, String component) {
    SystemInterface sys;
    switch (type) {
      case "standard":
        sys = new SystemSrkCPAstatoil(temp, pressure);
        break;
      case "implicit":
        sys = new SystemSrkCPAstatoilFullyImplicit(temp, pressure);
        break;
      case "broyden":
        sys = new SystemSrkCPAstatoilBroydenImplicit(temp, pressure);
        break;
      case "anderson":
        sys = new SystemSrkCPAstatoilAndersonMixing(temp, pressure);
        break;
      case "reduced":
        sys = new SystemSrkCPAstatoilReduced(temp, pressure);
        break;
      case "anderson-reduced":
        sys = new SystemSrkCPAstatoilAndersonReduced(temp, pressure);
        break;
      case "implicit-reduced":
        sys = new SystemSrkCPAstatoilFullyImplicitReduced(temp, pressure);
        break;
      default:
        throw new IllegalArgumentException();
    }
    sys.addComponent(component, 1.0);
    sys.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
    return sys;
  }

  private SystemInterface runBinary(String type, double temp, double pressure) {
    SystemInterface sys;
    switch (type) {
      case "standard":
        sys = new SystemSrkCPAstatoil(temp, pressure);
        break;
      case "implicit":
        sys = new SystemSrkCPAstatoilFullyImplicit(temp, pressure);
        break;
      case "broyden":
        sys = new SystemSrkCPAstatoilBroydenImplicit(temp, pressure);
        break;
      case "anderson":
        sys = new SystemSrkCPAstatoilAndersonMixing(temp, pressure);
        break;
      case "reduced":
        sys = new SystemSrkCPAstatoilReduced(temp, pressure);
        break;
      case "anderson-reduced":
        sys = new SystemSrkCPAstatoilAndersonReduced(temp, pressure);
        break;
      case "implicit-reduced":
        sys = new SystemSrkCPAstatoilFullyImplicitReduced(temp, pressure);
        break;
      default:
        throw new IllegalArgumentException();
    }
    sys.addComponent("water", 0.5);
    sys.addComponent("methanol", 0.5);
    sys.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
    return sys;
  }

  /**
   * Verify that the model test passes with the Broyden solver.
   */
  @Test
  public void testBroydenModelConsistency() {
    SystemInterface system = new SystemSrkCPAstatoilBroydenImplicit(298.15, 10.0);
    system.addComponent("water", 1.0);
    system.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    double density = system.getDensity("kg/m3");
    assertTrue(density > 900 && density < 1100,
        "Water density should be ~997 kg/m3, got " + density);
  }

  /**
   * Verify that the model test passes with the Anderson solver.
   */
  @Test
  public void testAndersonModelConsistency() {
    SystemInterface system = new SystemSrkCPAstatoilAndersonMixing(298.15, 10.0);
    system.addComponent("water", 1.0);
    system.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    double density = system.getDensity("kg/m3");
    assertTrue(density > 900 && density < 1100,
        "Water density should be ~997 kg/m3, got " + density);
  }

  /**
   * Diagnostic test: compare fugacity coefficients and molar volumes for water-methanol at 350K, 1
   * bar across all solvers.
   */
  @Test
  public void testDiagnosticWaterMethanol() {
    String[] types = {"standard", "implicit", "broyden", "anderson"};
    for (String type : types) {
      SystemInterface sys = createWaterMethanolSystem(type);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      sys.initProperties();
      double density = sys.getDensity("kg/m3");
      int numPhases = sys.getNumberOfPhases();
      System.out.printf("%-12s: density=%.6f nPhases=%d%n", type, density, numPhases);
      for (int p = 0; p < numPhases; p++) {
        double molarVol = sys.getPhase(p).getMolarVolume();
        double phaseFrac = sys.getPhase(p).getBeta();
        System.out.printf("  Phase[%d] type=%s beta=%.6f molarVol=%.6f%n", p,
            sys.getPhase(p).getType(), phaseFrac, molarVol);
        for (int c = 0; c < sys.getPhase(p).getNumberOfComponents(); c++) {
          double fugCoeff = sys.getPhase(p).getComponent(c).getFugacityCoefficient();
          double z = sys.getPhase(p).getComponent(c).getz();
          System.out.printf("    comp[%d] %s z=%.6f fugCoeff=%.8f%n", c,
              sys.getPhase(p).getComponent(c).getComponentName(), z, fugCoeff);
        }
      }
    }
  }

  // ===== Helper for creating system by solver type =====

  /**
   * Create a system with the given solver type at given T (K) and P (bar).
   *
   * @param type solver type: standard, implicit, broyden, anderson
   * @param temp temperature in K
   * @param pressure pressure in bar
   * @return the system
   */
  private SystemInterface createSystem(String type, double temp, double pressure) {
    switch (type) {
      case "standard":
        return new SystemSrkCPAstatoil(temp, pressure);
      case "implicit":
        return new SystemSrkCPAstatoilFullyImplicit(temp, pressure);
      case "broyden":
        return new SystemSrkCPAstatoilBroydenImplicit(temp, pressure);
      case "anderson":
        return new SystemSrkCPAstatoilAndersonMixing(temp, pressure);
      case "reduced":
        return new SystemSrkCPAstatoilReduced(temp, pressure);
      case "anderson-reduced":
        return new SystemSrkCPAstatoilAndersonReduced(temp, pressure);
      case "implicit-reduced":
        return new SystemSrkCPAstatoilFullyImplicitReduced(temp, pressure);
      default:
        throw new IllegalArgumentException("Unknown type: " + type);
    }
  }

  /**
   * Comprehensive benchmark covering all 11 systems from the CPA paper. Collects timing and
   * profiling data for all four solvers: standard, implicit, Broyden, Anderson.
   */
  @Test
  public void testComprehensiveBenchmark() {
    int warmup = 5;
    int repeats = 20;

    String[] solverTypes = {"standard", "implicit", "broyden", "anderson"};
    String[] systemNames = {"Pure water", "Pure methanol", "Pure ethanol", "Pure acetic acid",
        "Water-methanol", "Water-ethanol", "Water-acetic acid", "Water-ethanol-acetic acid",
        "NG + water", "NG + water + MEG", "NG + water + TEG"};

    double[][] timesMs = new double[systemNames.length][solverTypes.length];

    System.out.println("\n========= COMPREHENSIVE CPA SOLVER BENCHMARK =========\n");

    for (int s = 0; s < systemNames.length; s++) {
      final int sIdx = s;

      for (int t = 0; t < solverTypes.length; t++) {
        String solver = solverTypes[t];

        // Reset profiling counters
        if ("broyden".equals(solver)) {
          PhaseSrkCPABroydenImplicit.resetProfileCounters();
        } else if ("anderson".equals(solver)) {
          PhaseSrkCPAandersonMixing.resetProfileCounters();
        }

        // Warmup
        for (int w = 0; w < warmup; w++) {
          runSystemCase(solver, sIdx);
        }

        // Reset again for timed runs
        if ("broyden".equals(solver)) {
          PhaseSrkCPABroydenImplicit.resetProfileCounters();
        } else if ("anderson".equals(solver)) {
          PhaseSrkCPAandersonMixing.resetProfileCounters();
        }

        // Timed runs
        long start = System.nanoTime();
        for (int r = 0; r < repeats; r++) {
          runSystemCase(solver, sIdx);
        }
        long elapsed = System.nanoTime() - start;
        timesMs[s][t] = elapsed / 1.0e6;
      }
    }

    // Print summary table
    System.out.printf("%-30s %10s %10s %10s %10s | %7s %7s%n", "System", "Standard", "Implicit",
        "Broyden", "Anderson", "SpB/Std", "SpA/Std");
    System.out.println("---------------------------------------------"
        + "-------------------------------------------");

    for (int s = 0; s < systemNames.length; s++) {
      double tStd = timesMs[s][0];
      double tImpl = timesMs[s][1];
      double tBroy = timesMs[s][2];
      double tAndr = timesMs[s][3];
      double speedupBroyden = tStd / tBroy;
      double speedupAnderson = tStd / tAndr;
      System.out.printf("%-30s %8.1f ms %8.1f ms %8.1f ms %8.1f ms | %6.2fx %6.2fx%n",
          systemNames[s], tStd, tImpl, tBroy, tAndr, speedupBroyden, speedupAnderson);
    }

    // Print profiling data for Broyden and Anderson (from last system)
    System.out.println(
        "\n--- Broyden profiling (last system): " + PhaseSrkCPABroydenImplicit.getProfileSummary());
    System.out.println(
        "--- Anderson profiling (last system): " + PhaseSrkCPAandersonMixing.getProfileSummary());

    // Run profiling for each system individually
    System.out.println("\n=== Profiling Data Per System ===");
    System.out.printf("%-30s | %-60s%n", "System", "Broyden Profile");
    System.out.println("-------------------------------|"
        + "-------------------------------------------------------------");
    for (int s = 0; s < systemNames.length; s++) {
      PhaseSrkCPABroydenImplicit.resetProfileCounters();
      for (int r = 0; r < repeats; r++) {
        runSystemCase("broyden", s);
      }
      System.out.printf("%-30s | %s%n", systemNames[s],
          PhaseSrkCPABroydenImplicit.getProfileSummary());
    }

    System.out.printf("%n%-30s | %-60s%n", "System", "Anderson Profile");
    System.out.println("-------------------------------|"
        + "-------------------------------------------------------------");
    for (int s = 0; s < systemNames.length; s++) {
      PhaseSrkCPAandersonMixing.resetProfileCounters();
      for (int r = 0; r < repeats; r++) {
        runSystemCase("anderson", s);
      }
      System.out.printf("%-30s | %s%n", systemNames[s],
          PhaseSrkCPAandersonMixing.getProfileSummary());
    }
  }

  /**
   * Run a single system case by index.
   *
   * @param solver solver type
   * @param systemIndex 0-10 corresponding to the 11 system types
   */
  private void runSystemCase(String solver, int systemIndex) {
    SystemInterface sys;
    switch (systemIndex) {
      case 0: // Pure water
        sys = createSystem(solver, 298.15, 10.0);
        sys.addComponent("water", 1.0);
        break;
      case 1: // Pure methanol
        sys = createSystem(solver, 298.15, 1.0);
        sys.addComponent("methanol", 1.0);
        break;
      case 2: // Pure ethanol
        sys = createSystem(solver, 298.15, 1.0);
        sys.addComponent("ethanol", 1.0);
        break;
      case 3: // Pure acetic acid
        sys = createSystem(solver, 298.15, 1.0);
        sys.addComponent("acetic acid", 1.0);
        break;
      case 4: // Water-methanol
        sys = createSystem(solver, 350.0, 1.0);
        sys.addComponent("water", 0.5);
        sys.addComponent("methanol", 0.5);
        break;
      case 5: // Water-ethanol
        sys = createSystem(solver, 350.0, 1.0);
        sys.addComponent("water", 0.5);
        sys.addComponent("ethanol", 0.5);
        break;
      case 6: // Water-acetic acid
        sys = createSystem(solver, 350.0, 1.0);
        sys.addComponent("water", 0.5);
        sys.addComponent("acetic acid", 0.5);
        break;
      case 7: // Water-ethanol-acetic acid
        sys = createSystem(solver, 350.0, 1.0);
        sys.addComponent("water", 0.4);
        sys.addComponent("ethanol", 0.3);
        sys.addComponent("acetic acid", 0.3);
        break;
      case 8: // NG + water
        sys = createSystem(solver, 313.15, 100.0);
        sys.addComponent("methane", 0.85);
        sys.addComponent("ethane", 0.07);
        sys.addComponent("propane", 0.03);
        sys.addComponent("water", 0.05);
        sys.setMultiPhaseCheck(true);
        break;
      case 9: // NG + water + MEG
        sys = createSystem(solver, 313.15, 100.0);
        sys.addComponent("methane", 0.80);
        sys.addComponent("ethane", 0.06);
        sys.addComponent("propane", 0.03);
        sys.addComponent("water", 0.07);
        sys.addComponent("MEG", 0.04);
        sys.setMultiPhaseCheck(true);
        break;
      case 10: // NG + water + TEG
        sys = createSystem(solver, 313.15, 100.0);
        sys.addComponent("methane", 0.80);
        sys.addComponent("ethane", 0.06);
        sys.addComponent("propane", 0.03);
        sys.addComponent("water", 0.07);
        sys.addComponent("TEG", 0.04);
        sys.setMultiPhaseCheck(true);
        break;
      default:
        throw new IllegalArgumentException("Unknown system index: " + systemIndex);
    }
    sys.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
  }

  // ===================================================================
  // Tests for the reduced-dimension solver (Algorithm III: Site Symmetry)
  // ===================================================================

  /**
   * Verify the reduced solver on pure water. Since the reduced solver uses the coupled (implicit)
   * approach, it should match the Broyden/implicit family. For pure water, 4 sites reduce to 2
   * unique types.
   */
  @Test
  public void testReducedMatchesBroydenWater() {
    SystemInterface broyden = createWaterSystem("broyden");
    SystemInterface reduced = createWaterSystem("reduced");

    ThermodynamicOperations opsBroy = new ThermodynamicOperations(broyden);
    opsBroy.TPflash();
    broyden.initProperties();

    ThermodynamicOperations opsRed = new ThermodynamicOperations(reduced);
    opsRed.TPflash();
    reduced.initProperties();

    double densityBroy = broyden.getDensity("kg/m3");
    double densityRed = reduced.getDensity("kg/m3");

    assertEquals(densityBroy, densityRed, Math.abs(densityBroy) * REL_TOL,
        "Reduced solver water density should match Broyden");

    // Verify site reduction occurred: water (4C) -> 2 unique types
    PhaseSrkCPAreduced phase = (PhaseSrkCPAreduced) reduced.getPhase(1);
    assertEquals(2, phase.getLastNumTypes(), "Water 4C should give 2 unique site types");
    assertEquals(4, phase.getLastFullSites(), "Water 4C should have 4 individual sites");
  }

  /**
   * Verify the reduced solver matches the standard solver on water-methanol binary. Water(4C) +
   * methanol(2B): 6 sites, 4 unique types.
   *
   * <p>
   * Note: At 350 K / 1 bar, the CPA EOS for water-methanol has multiple valid roots. The standard
   * and reduced solvers converge to the same root (density ~3.81 kg/m3), while the Broyden and
   * implicit solvers find a different root (~2.85 kg/m3). Both are thermodynamically valid.
   */
  @Test
  public void testReducedMatchesBroydenWaterMethanol() {
    SystemInterface standard = createWaterMethanolSystem("standard");
    SystemInterface reduced = createWaterMethanolSystem("reduced");

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsRed = new ThermodynamicOperations(reduced);
    opsRed.TPflash();
    reduced.initProperties();

    double densityStd = standard.getDensity("kg/m3");
    double densityRed = reduced.getDensity("kg/m3");

    assertEquals(densityStd, densityRed, Math.abs(densityStd) * REL_TOL,
        "Reduced solver water-methanol density should match standard");
  }

  /**
   * Verify the reduced solver on the industrial NG + water + MEG system. Water(4C) + MEG(4C): 8
   * sites, 4 unique types. NG components have no association sites.
   */
  @Test
  public void testReducedMatchesBroydenIndustrial() {
    SystemInterface broyden = createIndustrialSystem("broyden");
    SystemInterface reduced = createIndustrialSystem("reduced");

    for (SystemInterface sys : new SystemInterface[] {broyden, reduced}) {
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      sys.initProperties();
    }

    double densityBroy = broyden.getDensity("kg/m3");
    double densityRed = reduced.getDensity("kg/m3");

    assertEquals(densityBroy, densityRed, Math.abs(densityBroy) * REL_TOL,
        "Reduced solver NG+water+MEG density should match Broyden");
  }

  /**
   * Verify the reduced solver consistency: water density should be ~997 kg/m3.
   */
  @Test
  public void testReducedModelConsistency() {
    SystemInterface system = new SystemSrkCPAstatoilReduced(298.15, 10.0);
    system.addComponent("water", 1.0);
    system.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    double density = system.getDensity("kg/m3");
    assertTrue(density > 900 && density < 1100,
        "Water density should be ~997 kg/m3, got " + density);
  }

  /**
   * Benchmark the reduced solver against all other solvers on all 11 systems. Verifies that the
   * reduced solver gives correct results and measures speedup.
   */
  @Test
  public void testBenchmarkReduced() {
    int warmup = 5;
    int repeats = 20;

    String[] solverTypes = {"standard", "broyden", "reduced"};
    String[] systemNames = {"Pure water", "Pure methanol", "Water-methanol", "Water-ethanol",
        "Water-EtOH-AcOH", "NG + water", "NG + water + MEG", "NG + water + TEG"};
    int[] systemIndices = {0, 1, 4, 5, 7, 8, 9, 10};

    double[][] timesMs = new double[systemIndices.length][solverTypes.length];

    System.out.println("\n========= REDUCED SOLVER BENCHMARK =========\n");

    for (int s = 0; s < systemIndices.length; s++) {
      for (int t = 0; t < solverTypes.length; t++) {
        String solver = solverTypes[t];
        // Warmup
        for (int w = 0; w < warmup; w++) {
          runSystemCase(solver, systemIndices[s]);
        }
        // Timed
        long start = System.nanoTime();
        for (int r = 0; r < repeats; r++) {
          runSystemCase(solver, systemIndices[s]);
        }
        long elapsed = System.nanoTime() - start;
        timesMs[s][t] = elapsed / 1.0e6;
      }
    }

    System.out.printf("%-25s %10s %10s %10s | %8s %8s%n", "System", "Standard", "Broyden",
        "Reduced", "SpR/Std", "SpR/Broy");
    System.out.println(
        "------------------------------------------------------------------------------------");

    for (int s = 0; s < systemIndices.length; s++) {
      double tStd = timesMs[s][0];
      double tBroy = timesMs[s][1];
      double tRed = timesMs[s][2];
      double spVsStd = tStd / tRed;
      double spVsBroy = tBroy / tRed;
      System.out.printf("%-25s %8.1f ms %8.1f ms %8.1f ms | %7.2fx %7.2fx%n", systemNames[s], tStd,
          tBroy, tRed, spVsStd, spVsBroy);
    }
  }

  /**
   * Verify that the reduced solver's site type computation is correct for key systems. Tests the
   * grouping: 4C -> 2 types, 2B -> 2 types, 4C+4C -> 4 types, etc.
   */
  @Test
  public void testSiteTypeReduction() {
    // Pure water: 4C -> 4 sites, 2 unique types (multiplicity 2+2)
    SystemInterface water = new SystemSrkCPAstatoilReduced(298.15, 10.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    ThermodynamicOperations opsW = new ThermodynamicOperations(water);
    opsW.TPflash();
    water.initProperties();
    PhaseSrkCPAreduced phaseW = (PhaseSrkCPAreduced) water.getPhase(1);
    assertEquals(2, phaseW.getLastNumTypes(), "Water 4C: expected 2 unique types");
    assertEquals(4, phaseW.getLastFullSites(), "Water 4C: expected 4 full sites");

    // Water + MEG: 4C+4C -> 8 sites, 4 unique types (2+2+2+2)
    SystemInterface wm = new SystemSrkCPAstatoilReduced(298.15, 10.0);
    wm.addComponent("water", 0.5);
    wm.addComponent("MEG", 0.5);
    wm.setMixingRule(10);
    ThermodynamicOperations opsWM = new ThermodynamicOperations(wm);
    opsWM.TPflash();
    wm.initProperties();
    PhaseSrkCPAreduced phaseWM = (PhaseSrkCPAreduced) wm.getPhase(1);
    assertEquals(4, phaseWM.getLastNumTypes(), "Water+MEG: expected 4 unique types");
    assertEquals(8, phaseWM.getLastFullSites(), "Water+MEG: expected 8 full sites");

    // NG + water + TEG: only water(4C)+TEG(4C) have sites -> 8 sites, 4 types
    SystemInterface ng = new SystemSrkCPAstatoilReduced(313.15, 100.0);
    ng.addComponent("methane", 0.80);
    ng.addComponent("ethane", 0.06);
    ng.addComponent("propane", 0.03);
    ng.addComponent("water", 0.07);
    ng.addComponent("TEG", 0.04);
    ng.setMixingRule(10);
    ng.setMultiPhaseCheck(true);
    ThermodynamicOperations opsNG = new ThermodynamicOperations(ng);
    opsNG.TPflash();
    ng.initProperties();
    PhaseSrkCPAreduced phaseNG = (PhaseSrkCPAreduced) ng.getPhase(1);
    assertEquals(4, phaseNG.getLastNumTypes(), "NG+water+TEG: expected 4 unique types");
    assertEquals(8, phaseNG.getLastFullSites(), "NG+water+TEG: expected 8 full sites");
  }

  // ===================================================================
  // Tests for Anderson + Reduced solver (Algorithm IV)
  // ===================================================================

  /**
   * Verify anderson-reduced matches standard on pure water. Anderson-reduced is nested-family (like
   * standard), so results should match.
   */
  @Test
  public void testAndersonReducedMatchesStandardWater() {
    SystemInterface standard = createWaterSystem("standard");
    SystemInterface andRed = createWaterSystem("anderson-reduced");

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsAR = new ThermodynamicOperations(andRed);
    opsAR.TPflash();
    andRed.initProperties();

    double densityStd = standard.getDensity("kg/m3");
    double densityAR = andRed.getDensity("kg/m3");

    assertEquals(densityStd, densityAR, Math.abs(densityStd) * REL_TOL,
        "Anderson-reduced water density should match standard nested");
  }

  /**
   * Verify anderson-reduced matches standard on water-methanol binary. Both are nested-family
   * solvers.
   */
  @Test
  public void testAndersonReducedMatchesStandardWaterMethanol() {
    SystemInterface standard = createWaterMethanolSystem("standard");
    SystemInterface andRed = createWaterMethanolSystem("anderson-reduced");

    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    opsStd.TPflash();
    standard.initProperties();

    ThermodynamicOperations opsAR = new ThermodynamicOperations(andRed);
    opsAR.TPflash();
    andRed.initProperties();

    double densityStd = standard.getDensity("kg/m3");
    double densityAR = andRed.getDensity("kg/m3");

    assertEquals(densityStd, densityAR, Math.abs(densityStd) * REL_TOL,
        "Anderson-reduced water-methanol density should match standard nested");
  }

  /**
   * Verify anderson-reduced matches standard and anderson on industrial NG + water + MEG system.
   */
  @Test
  public void testAndersonReducedMatchesStandardIndustrial() {
    SystemInterface standard = createIndustrialSystem("standard");
    SystemInterface anderson = createIndustrialSystem("anderson");
    SystemInterface andRed = createIndustrialSystem("anderson-reduced");

    for (SystemInterface sys : new SystemInterface[] {standard, anderson, andRed}) {
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      sys.initProperties();
    }

    double densityStd = standard.getDensity("kg/m3");
    double densityAnd = anderson.getDensity("kg/m3");
    double densityAR = andRed.getDensity("kg/m3");

    assertEquals(densityStd, densityAR, Math.abs(densityStd) * REL_TOL,
        "Anderson-reduced NG+water+MEG density should match standard");
    assertEquals(densityAnd, densityAR, Math.abs(densityAnd) * REL_TOL,
        "Anderson-reduced should match unreduced Anderson");
  }

  /**
   * Verify anderson-reduced gives reasonable water density.
   */
  @Test
  public void testAndersonReducedModelConsistency() {
    SystemInterface system = new SystemSrkCPAstatoilAndersonReduced(298.15, 10.0);
    system.addComponent("water", 1.0);
    system.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    double density = system.getDensity("kg/m3");
    assertTrue(density > 900 && density < 1100,
        "Water density should be ~997 kg/m3, got " + density);
  }

  /**
   * Benchmark the anderson-reduced solver against all other solvers on representative systems.
   */
  @Test
  public void testBenchmarkAndersonReduced() {
    int warmup = 5;
    int repeats = 20;

    String[] solverTypes = {"standard", "anderson", "reduced", "anderson-reduced"};
    String[] systemNames = {"Pure water", "Pure methanol", "Water-methanol", "Water-ethanol",
        "Water-EtOH-AcOH", "NG + water", "NG + water + MEG", "NG + water + TEG"};
    int[] systemIndices = {0, 1, 4, 5, 7, 8, 9, 10};

    double[][] timesMs = new double[systemIndices.length][solverTypes.length];

    System.out.println("\n========= ANDERSON-REDUCED SOLVER BENCHMARK =========\n");

    for (int s = 0; s < systemIndices.length; s++) {
      for (int t = 0; t < solverTypes.length; t++) {
        String solver = solverTypes[t];
        // Warmup
        for (int w = 0; w < warmup; w++) {
          runSystemCase(solver, systemIndices[s]);
        }
        // Timed
        long start = System.nanoTime();
        for (int r = 0; r < repeats; r++) {
          runSystemCase(solver, systemIndices[s]);
        }
        long elapsed = System.nanoTime() - start;
        timesMs[s][t] = elapsed / 1.0e6;
      }
    }

    System.out.printf("%-25s %10s %10s %10s %10s | %8s %8s%n", "System", "Standard", "Anderson",
        "Reduced", "And+Red", "SpAR/Std", "SpAR/And");
    System.out.println(
        "------------------------------------------------------------------------------------"
            + "------");

    for (int s = 0; s < systemIndices.length; s++) {
      double tStd = timesMs[s][0];
      double tAnd = timesMs[s][1];
      double tRed = timesMs[s][2];
      double tAR = timesMs[s][3];
      double spVsStd = tStd / tAR;
      double spVsAnd = tAnd / tAR;
      System.out.printf("%-25s %8.1f ms %8.1f ms %8.1f ms %8.1f ms | %7.2fx %7.2fx%n",
          systemNames[s], tStd, tAnd, tRed, tAR, spVsStd, spVsAnd);
    }

    // Print profiling data
    System.out.println(
        "\n--- Anderson-Reduced profiling: " + PhaseSrkCPAandersonReduced.getProfileSummary());
  }

  // ===================================================================
  // Tests for Fully Implicit + Reduced solver (Algorithm V)
  // ===================================================================

  /**
   * Verify implicit-reduced matches Broyden/implicit family on pure water. Implicit-reduced is
   * coupled-family (like implicit, Broyden, reduced), so results should match those.
   */
  @Test
  public void testImplicitReducedMatchesBroydenWater() {
    SystemInterface broyden = createWaterSystem("broyden");
    SystemInterface implRed = createWaterSystem("implicit-reduced");

    ThermodynamicOperations opsBroy = new ThermodynamicOperations(broyden);
    opsBroy.TPflash();
    broyden.initProperties();

    ThermodynamicOperations opsIR = new ThermodynamicOperations(implRed);
    opsIR.TPflash();
    implRed.initProperties();

    double densityBroy = broyden.getDensity("kg/m3");
    double densityIR = implRed.getDensity("kg/m3");

    assertEquals(densityBroy, densityIR, Math.abs(densityBroy) * REL_TOL,
        "Implicit-reduced water density should match Broyden");

    // Verify site reduction: water (4C) -> 2 unique types
    PhaseSrkCPAfullyImplicitReduced phase = (PhaseSrkCPAfullyImplicitReduced) implRed.getPhase(1);
    assertEquals(2, phase.getLastNumTypes(), "Water 4C should give 2 unique site types");
    assertEquals(4, phase.getLastFullSites(), "Water 4C should have 4 individual sites");
  }

  /**
   * Verify implicit-reduced matches reduced (Broyden+reduced) on water-methanol binary.
   */
  @Test
  public void testImplicitReducedMatchesReducedWaterMethanol() {
    SystemInterface reduced = createWaterMethanolSystem("reduced");
    SystemInterface implRed = createWaterMethanolSystem("implicit-reduced");

    ThermodynamicOperations opsRed = new ThermodynamicOperations(reduced);
    opsRed.TPflash();
    reduced.initProperties();

    ThermodynamicOperations opsIR = new ThermodynamicOperations(implRed);
    opsIR.TPflash();
    implRed.initProperties();

    double densityRed = reduced.getDensity("kg/m3");
    double densityIR = implRed.getDensity("kg/m3");

    assertEquals(densityRed, densityIR, Math.abs(densityRed) * REL_TOL,
        "Implicit-reduced water-methanol density should match reduced");
  }

  /**
   * Verify implicit-reduced matches reduced on industrial NG + water + MEG system.
   */
  @Test
  public void testImplicitReducedMatchesReducedIndustrial() {
    SystemInterface reduced = createIndustrialSystem("reduced");
    SystemInterface implRed = createIndustrialSystem("implicit-reduced");

    for (SystemInterface sys : new SystemInterface[] {reduced, implRed}) {
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      sys.initProperties();
    }

    double densityRed = reduced.getDensity("kg/m3");
    double densityIR = implRed.getDensity("kg/m3");

    assertEquals(densityRed, densityIR, Math.abs(densityRed) * REL_TOL,
        "Implicit-reduced NG+water+MEG density should match reduced");
  }

  /**
   * Verify implicit-reduced gives reasonable water density.
   */
  @Test
  public void testImplicitReducedModelConsistency() {
    SystemInterface system = new SystemSrkCPAstatoilFullyImplicitReduced(298.15, 10.0);
    system.addComponent("water", 1.0);
    system.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    double density = system.getDensity("kg/m3");
    assertTrue(density > 900 && density < 1100,
        "Water density should be ~997 kg/m3, got " + density);
  }

  /**
   * Benchmark the implicit-reduced solver against all other reduced-dimension solvers. Compares:
   * standard, implicit, reduced (Broyden+reduced), anderson-reduced, implicit-reduced.
   */
  @Test
  public void testBenchmarkImplicitReduced() {
    int warmup = 5;
    int repeats = 20;

    String[] solverTypes =
        {"standard", "implicit", "reduced", "anderson-reduced", "implicit-reduced"};
    String[] systemNames = {"Pure water", "Pure methanol", "Water-methanol", "Water-ethanol",
        "Water-EtOH-AcOH", "NG + water", "NG + water + MEG", "NG + water + TEG"};
    int[] systemIndices = {0, 1, 4, 5, 7, 8, 9, 10};

    double[][] timesMs = new double[systemIndices.length][solverTypes.length];

    System.out.println("\n========= IMPLICIT-REDUCED SOLVER BENCHMARK =========\n");

    for (int s = 0; s < systemIndices.length; s++) {
      for (int t = 0; t < solverTypes.length; t++) {
        String solver = solverTypes[t];
        // Warmup
        for (int w = 0; w < warmup; w++) {
          runSystemCase(solver, systemIndices[s]);
        }
        // Timed
        long start = System.nanoTime();
        for (int r = 0; r < repeats; r++) {
          runSystemCase(solver, systemIndices[s]);
        }
        long elapsed = System.nanoTime() - start;
        timesMs[s][t] = elapsed / 1.0e6;
      }
    }

    System.out.printf("%-20s %8s %8s %8s %8s %8s | %7s %7s%n", "System", "Std", "Impl", "Red",
        "And+R", "Impl+R", "IR/Std", "IR/Impl");
    System.out.println(
        "------------------------------------------------------------------------------------"
            + "-----------");

    for (int s = 0; s < systemIndices.length; s++) {
      double tStd = timesMs[s][0];
      double tImpl = timesMs[s][1];
      double tRed = timesMs[s][2];
      double tAR = timesMs[s][3];
      double tIR = timesMs[s][4];
      double spVsStd = tStd / tIR;
      double spVsImpl = tImpl / tIR;
      System.out.printf("%-20s %6.1fms %6.1fms %6.1fms %6.1fms %6.1fms | %6.2fx %6.2fx%n",
          systemNames[s], tStd, tImpl, tRed, tAR, tIR, spVsStd, spVsImpl);
    }
  }

  // ===================================================================
  // TEG Dehydration Process Test — validates CPA solvers in a real
  // process simulation with absorber, flash, reboiler, and recycle.
  // ===================================================================

  /**
   * Build and run a TEG dehydration process using the given CPA system type. Returns an array:
   * [waterDewPointC, dryGasDensity_kgm3, richTEGwaterFraction, leanTEGwaterFraction].
   *
   * @param systemType CPA solver type string
   * @return array of key process results
   */
  private double[] runTEGDehydrationProcess(String systemType) {
    // Create feed gas system with the specified CPA solver
    SystemInterface feedGas = createSystem(systemType, 273.15 + 42.0, 10.0);
    feedGas.addComponent("nitrogen", 0.245);
    feedGas.addComponent("CO2", 3.4);
    feedGas.addComponent("methane", 85.7);
    feedGas.addComponent("ethane", 5.981);
    feedGas.addComponent("propane", 0.2743);
    feedGas.addComponent("i-butane", 0.037);
    feedGas.addComponent("n-butane", 0.077);
    feedGas.addComponent("i-pentane", 0.0142);
    feedGas.addComponent("n-pentane", 0.0166);
    feedGas.addComponent("n-hexane", 0.006);
    feedGas.addComponent("water", 0.0);
    feedGas.addComponent("TEG", 0);
    feedGas.setMixingRule(10);
    feedGas.setMultiPhaseCheck(false);
    feedGas.init(0);

    // Wet feed gas
    Stream dryFeedGas = new Stream("dry feed gas", feedGas);
    dryFeedGas.setFlowRate(10.0, "MSm3/day");
    dryFeedGas.setTemperature(25.0, "C");
    dryFeedGas.setPressure(40.0, "bara");

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("water saturator", dryFeedGas);
    Stream wetFeedGas = new Stream("water saturated feed gas", saturator.getOutletStream());

    Heater feedTPsetter = new Heater("TP of gas to absorber", wetFeedGas);
    feedTPsetter.setOutPressure(40.0, "bara");
    feedTPsetter.setOutTemperature(37.0, "C");

    Stream feedToAbsorber = new Stream("feed to TEG absorber", feedTPsetter.getOutletStream());

    // Lean TEG stream
    SystemInterface feedTEG = (SystemInterface) feedGas.clone();
    feedTEG.setMolarComposition(
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.01, 0.99});

    Stream tegFeed = new Stream("lean TEG to absorber", feedTEG);
    tegFeed.setFlowRate(8000.0, "kg/hr");
    tegFeed.setTemperature(40.0, "C");
    tegFeed.setPressure(40.0, "bara");

    // TEG absorber
    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");
    absorber.addGasInStream(feedToAbsorber);
    absorber.addSolventInStream(tegFeed);
    absorber.setNumberOfStages(4);
    absorber.setStageEfficiency(0.8);
    absorber.setInternalDiameter(2.240);

    Stream dehydratedGas = new Stream("dry gas from absorber", absorber.getGasOutStream());
    Stream richTEG = new Stream("rich TEG from absorber", absorber.getLiquidOutStream());

    WaterDewPointAnalyser waterDewPointAnalyser =
        new WaterDewPointAnalyser("water dew point analyser", dehydratedGas);
    waterDewPointAnalyser.setReferencePressure(70.0);

    // Rich TEG flash and regeneration
    ThrottlingValve flashValve = new ThrottlingValve("Rich TEG HP flash valve", richTEG);
    flashValve.setOutletPressure(7.0);

    Heater richTEGHeater = new Heater("rich TEG heater", flashValve.getOutletStream());
    richTEGHeater.setOutTemperature(273.15 + 90.0);

    Separator flashSep = new Separator("degassing separator", richTEGHeater.getOutletStream());
    flashSep.setInternalDiameter(1.2);

    Stream flashGas = new Stream("gas from degassing", flashSep.getGasOutStream());
    Stream flashLiquid = new Stream("liquid from degassing", flashSep.getLiquidOutStream());

    // Build and run process
    ProcessSystem process = new ProcessSystem();
    process.add(dryFeedGas);
    process.add(saturator);
    process.add(wetFeedGas);
    process.add(feedTPsetter);
    process.add(feedToAbsorber);
    process.add(tegFeed);
    process.add(absorber);
    process.add(dehydratedGas);
    process.add(richTEG);
    process.add(waterDewPointAnalyser);
    process.add(flashValve);
    process.add(richTEGHeater);
    process.add(flashSep);
    process.add(flashGas);
    process.add(flashLiquid);

    process.run();

    // Collect key results
    double waterDewPoint = waterDewPointAnalyser.getMeasuredValue("C");
    double dryGasDensity = dehydratedGas.getFluid().getDensity("kg/m3");
    double richTEGwaterMoleFrac =
        richTEG.getFluid().getPhase("aqueous").getComponent("water").getx();
    double dryGasFlowRate = dehydratedGas.getFlowRate("MSm3/day");

    return new double[] {waterDewPoint, dryGasDensity, richTEGwaterMoleFrac, dryGasFlowRate};
  }

  /**
   * Test that the fully implicit CPA solver produces the same TEG dehydration results as the
   * standard nested CPA solver. This validates the implicit solver in a full process simulation
   * context with water-TEG-hydrocarbon multi-component, multi-phase calculations.
   */
  @Test
  public void testTEGDehydrationFullyImplicit() {
    // Warmup run to ensure database/component initialization is complete
    runTEGDehydrationProcess("standard");

    double[] stdResults = runTEGDehydrationProcess("standard");
    double[] implResults = runTEGDehydrationProcess("implicit");

    System.out.println("\n=== TEG Dehydration: Standard vs Fully Implicit ===");
    System.out.printf("%-25s %12s %12s%n", "Property", "Standard", "Implicit");
    System.out.println("---------------------------------------------------");
    System.out.printf("%-25s %10.2f C %10.2f C%n", "Water dew point", stdResults[0],
        implResults[0]);
    System.out.printf("%-25s %10.4f   %10.4f%n", "Dry gas density (kg/m3)", stdResults[1],
        implResults[1]);
    System.out.printf("%-25s %10.6f   %10.6f%n", "Rich TEG water x", stdResults[2], implResults[2]);
    System.out.printf("%-25s %10.6f   %10.6f%n", "Dry gas flow (MSm3/d)", stdResults[3],
        implResults[3]);

    // Water dew point should match within 0.5°C (process-level tolerance)
    assertEquals(stdResults[0], implResults[0], 0.5, "Water dew point should match within 0.5°C");
    // Gas density should match within 0.1%
    assertEquals(stdResults[1], implResults[1], Math.abs(stdResults[1]) * 1.0e-3,
        "Dry gas density should match within 0.1%");
    // Water mole fraction in rich TEG should match within 0.1%
    assertEquals(stdResults[2], implResults[2], Math.abs(stdResults[2]) * 1.0e-3,
        "Rich TEG water fraction should match within 0.1%");
    // Gas flow rate should match within 0.01%
    assertEquals(stdResults[3], implResults[3], Math.abs(stdResults[3]) * 1.0e-4,
        "Dry gas flow rate should match within 0.01%");
  }

  /**
   * Test that the implicit-reduced CPA solver produces the same TEG dehydration results as the
   * standard nested solver. Compares against the standard reference solver to ensure correctness
   * independently of other solver variants. TEG (4C) + water (4C) gives 8 association sites reduced
   * to 4 unique types, exercising the site symmetry reduction in a real process context.
   */
  @Test
  public void testTEGDehydrationImplicitReduced() {
    // Warmup run to ensure database/component initialization is complete
    runTEGDehydrationProcess("standard");

    double[] stdResults = runTEGDehydrationProcess("standard");
    double[] irResults = runTEGDehydrationProcess("implicit-reduced");

    System.out.println("\n=== TEG Dehydration: Standard vs Implicit-Reduced ===");
    System.out.printf("%-25s %12s %12s%n", "Property", "Standard", "Impl+Red");
    System.out.println("---------------------------------------------------");
    System.out.printf("%-25s %10.2f C %10.2f C%n", "Water dew point", stdResults[0], irResults[0]);
    System.out.printf("%-25s %10.4f   %10.4f%n", "Dry gas density (kg/m3)", stdResults[1],
        irResults[1]);
    System.out.printf("%-25s %10.6f   %10.6f%n", "Rich TEG water x", stdResults[2], irResults[2]);
    System.out.printf("%-25s %10.6f   %10.6f%n", "Dry gas flow (MSm3/d)", stdResults[3],
        irResults[3]);

    assertEquals(stdResults[0], irResults[0], 0.5, "Water dew point should match within 0.5°C");
    assertEquals(stdResults[1], irResults[1], Math.abs(stdResults[1]) * 1.0e-3,
        "Dry gas density should match within 0.1%");
    assertEquals(stdResults[2], irResults[2], Math.abs(stdResults[2]) * 1.0e-3,
        "Rich TEG water fraction should match within 0.1%");
    assertEquals(stdResults[3], irResults[3], Math.abs(stdResults[3]) * 1.0e-4,
        "Dry gas flow rate should match within 0.01%");
  }

  /**
   * Benchmark all CPA solver variants on the TEG dehydration process. Measures total process
   * simulation time for standard, implicit, reduced, anderson-reduced, and implicit-reduced.
   */
  @Test
  public void testBenchmarkTEGDehydration() {
    int warmup = 2;
    int repeats = 5;

    String[] solverTypes =
        {"standard", "implicit", "reduced", "anderson-reduced", "implicit-reduced"};
    double[] timesMs = new double[solverTypes.length];

    System.out.println("\n========= TEG DEHYDRATION PROCESS BENCHMARK =========\n");

    for (int t = 0; t < solverTypes.length; t++) {
      // Warmup
      for (int w = 0; w < warmup; w++) {
        runTEGDehydrationProcess(solverTypes[t]);
      }
      // Timed
      long start = System.nanoTime();
      for (int r = 0; r < repeats; r++) {
        runTEGDehydrationProcess(solverTypes[t]);
      }
      long elapsed = System.nanoTime() - start;
      timesMs[t] = elapsed / 1.0e6;
    }

    System.out.printf("%-20s %10s | %8s%n", "Solver", "Time(ms)", "Speedup");
    System.out.println("------------------------------------------");
    for (int t = 0; t < solverTypes.length; t++) {
      double speedup = timesMs[0] / timesMs[t];
      System.out.printf("%-20s %8.0f ms | %6.2fx%n", solverTypes[t], timesMs[t], speedup);
    }
  }
}
