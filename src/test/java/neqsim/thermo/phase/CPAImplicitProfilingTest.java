package neqsim.thermo.phase;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Profiling test to understand where implicit CPA spends time for multi-component systems.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CPAImplicitProfilingTest extends neqsim.NeqSimTest {

  /**
   * Profile Oil+Gas+Water+MEG case to count molarVolume calls and iterations.
   */
  @Test
  void profileOilGasWaterMEG() {
    double T = 273.15 + 50.0;
    double P = 80.0;

    // --- Standard solver ---
    SystemInterface standard = new SystemSrkCPAstatoil(T, P);
    addOilGasWaterMEG(standard);
    ThermodynamicOperations opsStd = new ThermodynamicOperations(standard);
    long t0 = System.nanoTime();
    opsStd.TPflash();
    long stdFlashTime = System.nanoTime() - t0;
    standard.initProperties();

    System.out.println("=== Standard CPA ===");
    System.out.printf("TPflash time: %.1f ms%n", stdFlashTime / 1e6);
    System.out.printf("Phases: %d%n", standard.getNumberOfPhases());

    // --- Implicit solver ---
    PhaseSrkCPAfullyImplicit.resetProfileCounters();

    SystemInterface implicit = new SystemSrkCPAstatoilFullyImplicit(T, P);
    addOilGasWaterMEG(implicit);
    ThermodynamicOperations opsImpl = new ThermodynamicOperations(implicit);
    t0 = System.nanoTime();
    opsImpl.TPflash();
    long implFlashTime = System.nanoTime() - t0;
    implicit.initProperties();

    System.out.println("\n=== Implicit CPA ===");
    System.out.printf("TPflash time: %.1f ms%n", implFlashTime / 1e6);
    System.out.printf("Phases: %d%n", implicit.getNumberOfPhases());
    System.out.printf("Profile: %s%n", PhaseSrkCPAfullyImplicit.getProfileSummary());
    System.out.printf("Ratio: %.2f%n", (double) implFlashTime / stdFlashTime);

    // --- Now test single molarVolume call timing (no TPflash overhead) ---
    System.out.println("\n=== Single init(1) comparison (after TPflash) ===");

    // Standard: time a single init(1) call
    t0 = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      standard.init(1);
    }
    long stdInitTime = System.nanoTime() - t0;

    PhaseSrkCPAfullyImplicit.resetProfileCounters();
    t0 = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      implicit.init(1);
    }
    long implInitTime = System.nanoTime() - t0;

    System.out.printf("Standard 100x init(1): %.1f ms%n", stdInitTime / 1e6);
    System.out.printf("Implicit 100x init(1): %.1f ms%n", implInitTime / 1e6);
    System.out.printf("Ratio: %.2f%n", (double) implInitTime / stdInitTime);
    System.out.printf("Implicit profile: %s%n", PhaseSrkCPAfullyImplicit.getProfileSummary());

    // --- Single init(3) comparison ---
    System.out.println("\n=== Single init(3) comparison (computes fugacity coefficients) ===");

    t0 = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      standard.init(3);
    }
    long stdInit3Time = System.nanoTime() - t0;

    PhaseSrkCPAfullyImplicit.resetProfileCounters();
    t0 = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      implicit.init(3);
    }
    long implInit3Time = System.nanoTime() - t0;

    System.out.printf("Standard 100x init(3): %.1f ms%n", stdInit3Time / 1e6);
    System.out.printf("Implicit 100x init(3): %.1f ms%n", implInit3Time / 1e6);
    System.out.printf("Ratio: %.2f%n", (double) implInit3Time / stdInit3Time);
    System.out.printf("Implicit profile: %s%n", PhaseSrkCPAfullyImplicit.getProfileSummary());
  }

  /**
   * Profile pure water case for baseline.
   */
  @Test
  void profilePureWater() {
    double T = 273.15 + 25.0;
    double P = 1.0;

    PhaseSrkCPAfullyImplicit.resetProfileCounters();

    SystemInterface implicit = new SystemSrkCPAstatoilFullyImplicit(T, P);
    implicit.addComponent("water", 1.0);
    implicit.setMixingRule(10);

    ThermodynamicOperations ops = new ThermodynamicOperations(implicit);
    ops.TPflash();

    System.out.println("=== Pure water implicit profile ===");
    System.out.printf("Profile: %s%n", PhaseSrkCPAfullyImplicit.getProfileSummary());
  }

  /**
   * Compare molarVolume call counts between simple and complex systems.
   */
  @Test
  void profileMolarVolumeCounts() {
    System.out.println("=== molarVolume call count comparison ===");
    System.out.printf("%-30s %8s %8s %8s%n", "Case", "Calls", "AvgIter", "Fallback");

    // Pure water
    profileCase("Pure water", 273.15 + 25, 1.0, new String[] {"water"}, new double[] {1.0}, false);

    // Methane + water
    profileCase("Methane + water", 273.15 + 5, 50.0, new String[] {"methane", "water"},
        new double[] {0.95, 0.05}, true);

    // NatGas + water + MEG
    profileCase("NatGas+water+MEG", 273.15 + 5, 50.0,
        new String[] {"methane", "ethane", "propane", "n-butane", "water", "MEG"},
        new double[] {0.80, 0.06, 0.03, 0.01, 0.08, 0.02}, true);

    // Oil+Gas+Water+MEG
    profileCase("Oil+Gas+Water+MEG", 273.15 + 50, 80.0,
        new String[] {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-hexane",
            "n-heptane", "n-octane", "water", "MEG"},
        new double[] {0.40, 0.05, 0.04, 0.03, 0.03, 0.04, 0.08, 0.05, 0.25, 0.03}, true);
  }

  private void profileCase(String label, double T, double P, String[] comps, double[] moles,
      boolean multiPhase) {
    PhaseSrkCPAfullyImplicit.resetProfileCounters();

    SystemInterface sys = new SystemSrkCPAstatoilFullyImplicit(T, P);
    for (int i = 0; i < comps.length; i++) {
      sys.addComponent(comps[i], moles[i]);
    }
    sys.setMixingRule(10);
    if (multiPhase) {
      sys.setMultiPhaseCheck(true);
    }

    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();

    System.out.printf("%-30s %s%n", label, PhaseSrkCPAfullyImplicit.getProfileSummary());
  }

  private void addOilGasWaterMEG(SystemInterface sys) {
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
}
