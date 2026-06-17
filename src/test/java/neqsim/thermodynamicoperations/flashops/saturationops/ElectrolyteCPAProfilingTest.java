package neqsim.thermodynamicoperations.flashops.saturationops;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Profiling test to identify bottlenecks in electrolyte CPA TPflash.
 *
 * @author ESOL
 */
@Tag("slow")
public class ElectrolyteCPAProfilingTest {

  /**
   * Compare TPflash cost: electrolyte CPA vs regular CPA.
   */
  @Test
  public void compareFlashCost() {
    // ===== Regular CPA (no electrolytes) =====
    SystemInterface cpa = new SystemSrkCPAstatoil(273.15 + 10.0, 50.0);
    cpa.addComponent("water", 0.5);
    cpa.addComponent("MEG", 0.17);
    cpa.addComponent("methane", 0.25);
    cpa.addComponent("ethane", 0.016);
    cpa.addComponent("propane", 0.011);
    cpa.addComponent("i-butane", 0.005);
    cpa.addComponent("n-butane", 0.005);
    cpa.setMixingRule(10);
    cpa.setMultiPhaseCheck(true);

    ThermodynamicOperations opsCpa = new ThermodynamicOperations(cpa);

    // Warmup
    opsCpa.TPflash();

    // Measure CPA (no multiPhaseCheck)
    cpa.setMultiPhaseCheck(false);
    int reps = 5;
    long t0 = System.nanoTime();
    for (int i = 0; i < reps; i++) {
      opsCpa.TPflash();
    }
    long cpaNoMPC = (System.nanoTime() - t0) / reps;

    // Measure CPA (with multiPhaseCheck)
    cpa.setMultiPhaseCheck(true);
    t0 = System.nanoTime();
    for (int i = 0; i < reps; i++) {
      opsCpa.TPflash();
    }
    long cpaWithMPC = (System.nanoTime() - t0) / reps;

    System.out.println("=== Regular CPA (7 components, no ions) ===");
    System.out.printf("  TPflash (no multiPhaseCheck):   %8.1f ms%n", cpaNoMPC / 1e6);
    System.out.printf("  TPflash (with multiPhaseCheck): %8.1f ms%n", cpaWithMPC / 1e6);
    System.out.printf("  Ratio multiPhaseCheck/noCheck:  %8.1fx%n", (double) cpaWithMPC / cpaNoMPC);

    // ===== Electrolyte CPA (with ions) =====
    SystemInterface ecpa = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);
    ecpa.addComponent("water", 0.494505);
    ecpa.addComponent("MEG", 0.164835);
    ecpa.addComponent("methane", 0.247253);
    ecpa.addComponent("ethane", 0.0164835);
    ecpa.addComponent("propane", 0.010989);
    ecpa.addComponent("i-butane", 0.00549451);
    ecpa.addComponent("n-butane", 0.00549451);
    ecpa.addComponent("Na+", 0.0274725);
    ecpa.addComponent("Cl-", 0.0274725);
    ecpa.setMixingRule(10);
    ecpa.setMultiPhaseCheck(true);

    ThermodynamicOperations opsEcpa = new ThermodynamicOperations(ecpa);

    // Warmup
    opsEcpa.TPflash();

    // Measure electrolyte CPA (no multiPhaseCheck)
    ecpa.setMultiPhaseCheck(false);
    t0 = System.nanoTime();
    for (int i = 0; i < reps; i++) {
      opsEcpa.TPflash();
    }
    long ecpaNoMPC = (System.nanoTime() - t0) / reps;

    // Measure electrolyte CPA (with multiPhaseCheck)
    ecpa.setMultiPhaseCheck(true);
    t0 = System.nanoTime();
    for (int i = 0; i < reps; i++) {
      opsEcpa.TPflash();
    }
    long ecpaWithMPC = (System.nanoTime() - t0) / reps;

    System.out.println("\n=== Electrolyte CPA (9 components, with Na+/Cl-) ===");
    System.out.printf("  TPflash (no multiPhaseCheck):   %8.1f ms%n", ecpaNoMPC / 1e6);
    System.out.printf("  TPflash (with multiPhaseCheck): %8.1f ms%n", ecpaWithMPC / 1e6);
    System.out.printf("  Ratio multiPhaseCheck/noCheck:  %8.1fx%n",
        (double) ecpaWithMPC / ecpaNoMPC);

    System.out.println("\n=== Cross Comparison ===");
    System.out.printf("  Electrolyte/CPA ratio (no MPC):   %8.1fx%n",
        (double) ecpaNoMPC / cpaNoMPC);
    System.out.printf("  Electrolyte/CPA ratio (with MPC): %8.1fx%n",
        (double) ecpaWithMPC / cpaWithMPC);

    // ===== Breakdown: measure init(1) cost =====
    System.out.println("\n=== init(1) Breakdown ===");
    t0 = System.nanoTime();
    for (int i = 0; i < reps; i++) {
      cpa.init(1);
    }
    long cpaInit1 = (System.nanoTime() - t0) / reps;

    t0 = System.nanoTime();
    for (int i = 0; i < reps; i++) {
      ecpa.init(1);
    }
    long ecpaInit1 = (System.nanoTime() - t0) / reps;

    System.out.printf("  CPA init(1):          %8.1f ms%n", cpaInit1 / 1e6);
    System.out.printf("  Electrolyte CPA init(1): %8.1f ms%n", ecpaInit1 / 1e6);
    System.out.printf("  Ratio:                %8.1fx%n", (double) ecpaInit1 / cpaInit1);

    // init(2)
    t0 = System.nanoTime();
    for (int i = 0; i < reps; i++) {
      cpa.init(2);
    }
    long cpaInit2 = (System.nanoTime() - t0) / reps;

    t0 = System.nanoTime();
    for (int i = 0; i < reps; i++) {
      ecpa.init(2);
    }
    long ecpaInit2 = (System.nanoTime() - t0) / reps;

    System.out.printf("  CPA init(2):          %8.1f ms%n", cpaInit2 / 1e6);
    System.out.printf("  Electrolyte CPA init(2): %8.1f ms%n", ecpaInit2 / 1e6);
    System.out.printf("  Ratio:                %8.1fx%n", (double) ecpaInit2 / cpaInit2);

    // ===== Phase count comparison =====
    System.out.println("\n=== Phase Information ===");
    System.out.printf("  CPA phases: %d%n", cpa.getNumberOfPhases());
    System.out.printf("  Electrolyte CPA phases: %d%n", ecpa.getNumberOfPhases());
    for (int p = 0; p < ecpa.getNumberOfPhases(); p++) {
      System.out.printf("    Phase %d: %s (beta=%.4f)%n", p, ecpa.getPhase(p).getPhaseTypeName(),
          ecpa.getBeta(p));
    }
  }
}
