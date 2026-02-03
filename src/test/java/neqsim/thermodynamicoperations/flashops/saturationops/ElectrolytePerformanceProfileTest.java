package neqsim.thermodynamicoperations.flashops.saturationops;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Performance profiling test for electrolyte CPA systems.
 *
 * This test helps identify performance bottlenecks in multiPhase stability analysis.
 *
 * @author ESOL
 */
public class ElectrolytePerformanceProfileTest {

  /**
   * Profile init() call times for electrolyte CPA.
   */
  @Test
  @DisplayName("Profile init() call times")
  public void profileInitCallTimes() {
    System.out.println("=== Profile init() Call Times ===\n");

    // Electrolyte CPA system
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);
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

    // Initialize once
    fluid.init(0);
    fluid.init(1);

    // Profile init(1) calls
    int iterations = 100;
    long start = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
      fluid.init(1);
    }
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Electrolyte CPA init(1) x " + iterations + ": " + elapsed + " ms");
    System.out.println("  Average per call: " + (elapsed / (double) iterations) + " ms");

    // Profile init(1, 0) for phase 0 only
    start = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
      fluid.init(1, 0);
    }
    elapsed = System.currentTimeMillis() - start;
    System.out.println("Electrolyte CPA init(1, 0) x " + iterations + ": " + elapsed + " ms");
    System.out.println("  Average per call: " + (elapsed / (double) iterations) + " ms");

    // Profile init(1, 1) for phase 1 only
    start = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
      fluid.init(1, 1);
    }
    elapsed = System.currentTimeMillis() - start;
    System.out.println("Electrolyte CPA init(1, 1) x " + iterations + ": " + elapsed + " ms");
    System.out.println("  Average per call: " + (elapsed / (double) iterations) + " ms");

    // Compare with non-electrolyte CPA
    System.out.println("\n--- Non-Electrolyte CPA Comparison ---");
    SystemInterface fluidNoElec = new SystemSrkCPAstatoil(273.15 + 10.0, 50.0);
    fluidNoElec.addComponent("water", 0.494505);
    fluidNoElec.addComponent("MEG", 0.164835);
    fluidNoElec.addComponent("methane", 0.247253);
    fluidNoElec.addComponent("ethane", 0.0164835);
    fluidNoElec.addComponent("propane", 0.010989);
    fluidNoElec.addComponent("i-butane", 0.00549451);
    fluidNoElec.addComponent("n-butane", 0.00549451);
    fluidNoElec.setMixingRule(10);
    fluidNoElec.init(0);
    fluidNoElec.init(1);

    start = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
      fluidNoElec.init(1);
    }
    elapsed = System.currentTimeMillis() - start;
    System.out.println("Non-Electrolyte CPA init(1) x " + iterations + ": " + elapsed + " ms");
    System.out.println("  Average per call: " + (elapsed / (double) iterations) + " ms");
  }

  /**
   * Profile TPflash times.
   */
  @Test
  @DisplayName("Profile TPflash times")
  public void profileTPflashTimes() {
    System.out.println("=== Profile TPflash Times ===\n");

    // Electrolyte CPA system
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);
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

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Warm up
    fluid.setMultiPhaseCheck(false);
    ops.TPflash();

    int iterations = 10;

    // TPflash WITHOUT multiPhaseCheck
    fluid.setMultiPhaseCheck(false);
    long start = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
      ops.TPflash();
    }
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("TPflash (multiPhaseCheck=false) x " + iterations + ": " + elapsed + " ms");
    System.out.println("  Average per call: " + (elapsed / (double) iterations) + " ms");

    // TPflash WITH multiPhaseCheck
    fluid.setMultiPhaseCheck(true);
    start = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
      ops.TPflash();
    }
    elapsed = System.currentTimeMillis() - start;
    System.out.println("TPflash (multiPhaseCheck=true) x " + iterations + ": " + elapsed + " ms");
    System.out.println("  Average per call: " + (elapsed / (double) iterations) + " ms");

    // Compare with non-electrolyte
    System.out.println("\n--- Non-Electrolyte CPA Comparison ---");
    SystemInterface fluidNoElec = new SystemSrkCPAstatoil(273.15 + 10.0, 50.0);
    fluidNoElec.addComponent("water", 0.494505);
    fluidNoElec.addComponent("MEG", 0.164835);
    fluidNoElec.addComponent("methane", 0.247253);
    fluidNoElec.addComponent("ethane", 0.0164835);
    fluidNoElec.addComponent("propane", 0.010989);
    fluidNoElec.addComponent("i-butane", 0.00549451);
    fluidNoElec.addComponent("n-butane", 0.00549451);
    fluidNoElec.setMixingRule(10);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluidNoElec);
    ops2.TPflash();

    fluidNoElec.setMultiPhaseCheck(true);
    start = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
      ops2.TPflash();
    }
    elapsed = System.currentTimeMillis() - start;
    System.out.println(
        "Non-Elec TPflash (multiPhaseCheck=true) x " + iterations + ": " + elapsed + " ms");
    System.out.println("  Average per call: " + (elapsed / (double) iterations) + " ms");
  }

  /**
   * Profile stability analysis times.
   */
  @Test
  @DisplayName("Profile stability analysis times")
  public void profileStabilityAnalysisTimes() {
    System.out.println("=== Profile Stability Analysis Times ===\n");

    // Electrolyte CPA system - first flash to establish phases
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);
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

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // First flash - this triggers stability analysis
    System.out.println("First TPflash (includes full stability analysis):");
    long start = System.currentTimeMillis();
    ops.TPflash();
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("  Time: " + elapsed + " ms");
    System.out.println("  Phases found: " + fluid.getNumberOfPhases());

    // Second flash - may still do stability analysis
    System.out.println("\nSecond TPflash (same conditions):");
    start = System.currentTimeMillis();
    ops.TPflash();
    elapsed = System.currentTimeMillis() - start;
    System.out.println("  Time: " + elapsed + " ms");

    // Third flash - with slight temperature change
    fluid.setTemperature(273.15 + 5.0);
    System.out.println("\nThird TPflash (T changed to 5°C):");
    start = System.currentTimeMillis();
    ops.TPflash();
    elapsed = System.currentTimeMillis() - start;
    System.out.println("  Time: " + elapsed + " ms");
    System.out.println("  Phases: " + fluid.getNumberOfPhases());

    // Fourth flash - with multiPhaseCheck disabled
    fluid.setMultiPhaseCheck(false);
    System.out.println("\nFourth TPflash (multiPhaseCheck=false):");
    start = System.currentTimeMillis();
    ops.TPflash();
    elapsed = System.currentTimeMillis() - start;
    System.out.println("  Time: " + elapsed + " ms");
  }
}
