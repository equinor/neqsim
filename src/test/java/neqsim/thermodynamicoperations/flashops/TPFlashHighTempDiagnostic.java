package neqsim.thermodynamicoperations.flashops;

import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Diagnostic for TPFlashTestHighTemp — NOT a JUnit test, just a main() to run. Investigates whether
 * gas fraction ~0.00683 or ~0.00349 is the correct answer at 268 C, 88 bara for the heavy oil
 * system.
 */
public class TPFlashHighTempDiagnostic {

  static neqsim.thermo.system.SystemInterface createSystem() {
    neqsim.thermo.system.SystemInterface sys = new neqsim.thermo.system.SystemSrkEos(243.15, 300.0);
    sys.addComponent("nitrogen", 1.64e-3);
    sys.addComponent("CO2", 1.64e-3);
    sys.addComponent("H2S", 1.64e-3);
    sys.addComponent("methane", 90.0);
    sys.addComponent("ethane", 2.0);
    sys.addComponent("propane", 1.0);
    sys.addComponent("i-butane", 1.0);
    sys.addComponent("n-butane", 1.0);
    sys.addComponent("i-pentane", 1.0);
    sys.addComponent("n-pentane", 1.0);
    sys.addComponent("n-hexane", 1.0);
    sys.addComponent("n-heptane", 1.0);
    sys.addComponent("n-octane", 1.0);
    sys.addComponent("n-nonane", 1.0);
    sys.addComponent("nC10", 1.0);
    sys.addComponent("nC11", 1.0);
    sys.addComponent("nC12", 1.0);
    sys.addComponent("nC13", 1.0);
    sys.addComponent("nC14", 1.0);
    sys.addComponent("nC15", 1.0);
    sys.addComponent("nC16", 1.0);
    sys.addComponent("nC17", 1.0);
    sys.addComponent("nC18", 1.0);
    sys.addComponent("nC19", 1.0);
    sys.setMixingRule("classic");
    sys.setMolarComposition(new double[] {1.63e-3, 3.23e-3, 0, 3e-1, 4.6e-2, 1.4e-2, 2.2e-2, 3.9e-3,
        8.8e-3, 2.6e-3, 3.2e-2, 1.2e-1, 1.5e-1, 9.8e-2, 7.6e-2, 4.1e-2, 2.5e-2, 1.6e-2, 1e-2,
        5.6e-3, 2.7e-3, 1.3e-3, 8.7e-4, 3.8e-4});
    return sys;
  }

  public static void main(String[] args) {
    System.out.println("=== TPFlashTestHighTemp Diagnostic ===");
    System.out.println();

    // Test 1: Basic flash at the test condition
    System.out.println("--- Test 1: Flash at 268 C, 88 bara (multiPhaseCheck=true) ---");
    {
      neqsim.thermo.system.SystemInterface sys = createSystem();
      sys.setMultiPhaseCheck(true);
      sys.setPressure(88, "bara");
      sys.setTemperature(268.0, "C");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      System.out.println("  Number of phases: " + sys.getNumberOfPhases());
      System.out.println("  Gas mole fraction: " + sys.getPhaseFraction("gas", "mole"));
      System.out.println("  Gibbs energy: " + sys.getGibbsEnergy());
      double betaSum = 0;
      for (int p = 0; p < sys.getNumberOfPhases(); p++) {
        double b = sys.getBeta(p);
        betaSum += b;
        System.out.println("  Phase " + p + ": type=" + sys.getPhase(p).getPhaseTypeName()
            + " beta=" + b + " Z=" + sys.getPhase(p).getZ());
      }
      System.out.println("  Beta sum: " + betaSum
          + (Math.abs(betaSum - 1.0) > 1e-6 ? " *** NOT NORMALIZED ***" : " (OK)"));
    }
    System.out.println();

    // Test 2: Flash WITHOUT multiPhaseCheck
    System.out.println("--- Test 2: Flash at 268 C, 88 bara (multiPhaseCheck=false) ---");
    {
      neqsim.thermo.system.SystemInterface sys = createSystem();
      sys.setMultiPhaseCheck(false);
      sys.setPressure(88, "bara");
      sys.setTemperature(268.0, "C");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      System.out.println("  Number of phases: " + sys.getNumberOfPhases());
      System.out.println("  Gas mole fraction: " + sys.getPhaseFraction("gas", "mole"));
      System.out.println("  Gibbs energy: " + sys.getGibbsEnergy());
      double betaSum = 0;
      for (int p = 0; p < sys.getNumberOfPhases(); p++) {
        double b = sys.getBeta(p);
        betaSum += b;
        System.out.println("  Phase " + p + ": type=" + sys.getPhase(p).getPhaseTypeName()
            + " beta=" + b + " Z=" + sys.getPhase(p).getZ());
      }
      System.out.println("  Beta sum: " + betaSum
          + (Math.abs(betaSum - 1.0) > 1e-6 ? " *** NOT NORMALIZED ***" : " (OK)"));
    }
    System.out.println();

    // Test 3: Temperature sweep around 268 C to see trend
    System.out.println("--- Test 3: Temperature sweep 260-275 C at 88 bara ---");
    {
      neqsim.thermo.system.SystemInterface sys = createSystem();
      sys.setMultiPhaseCheck(true);
      sys.setPressure(88, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      for (double t = 260.0; t <= 276.0; t += 1.0) {
        sys.setTemperature(t, "C");
        ops.TPflash();
        double gasFrac = sys.getPhaseFraction("gas", "mole");
        int nph = sys.getNumberOfPhases();
        System.out.printf("  T=%.0f C  nPhases=%d  gasFrac=%.8f  Gibbs=%.2f%n", t, nph, gasFrac,
            sys.getGibbsEnergy());
      }
    }
    System.out.println();

    // Test 4: Multiple independent flash runs at 268 C to check reproducibility
    System.out.println("--- Test 4: Reproducibility - 5 independent flashes at 268 C ---");
    for (int run = 0; run < 5; run++) {
      neqsim.thermo.system.SystemInterface sys = createSystem();
      sys.setMultiPhaseCheck(true);
      sys.setPressure(88, "bara");
      sys.setTemperature(268.0, "C");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      System.out.printf("  Run %d: gasFrac=%.15f  nPhases=%d  Gibbs=%.6f%n", run,
          sys.getPhaseFraction("gas", "mole"), sys.getNumberOfPhases(), sys.getGibbsEnergy());
    }
    System.out.println();

    // Test 5: Force single-phase flash and compare Gibbs energy
    System.out.println("--- Test 5: Single-phase vs 2-phase Gibbs energy ---");
    {
      // 2-phase flash (standard)
      neqsim.thermo.system.SystemInterface sys2 = createSystem();
      sys2.setMultiPhaseCheck(true);
      sys2.setPressure(88, "bara");
      sys2.setTemperature(268.0, "C");
      ThermodynamicOperations ops2 = new ThermodynamicOperations(sys2);
      ops2.TPflash();
      double gibbs2 = sys2.getGibbsEnergy();
      int nph2 = sys2.getNumberOfPhases();
      double gasFrac2 = sys2.getPhaseFraction("gas", "mole");

      // Single-phase: set number of phases to 1, no stability check
      neqsim.thermo.system.SystemInterface sys1 = createSystem();
      sys1.setMultiPhaseCheck(false);
      sys1.setPressure(88, "bara");
      sys1.setTemperature(268.0, "C");
      sys1.setNumberOfPhases(1);
      sys1.init(0);
      sys1.init(1);
      double gibbs1single = sys1.getGibbsEnergy();

      System.out
          .println("  2-phase: nPhases=" + nph2 + " gasFrac=" + gasFrac2 + " Gibbs=" + gibbs2);
      System.out.println("  1-phase (forced): Gibbs=" + gibbs1single);
      System.out.println("  Gibbs difference (2ph - 1ph): " + (gibbs2 - gibbs1single));
      System.out.println("  2-phase is "
          + (gibbs2 < gibbs1single ? "LOWER (correct)" : "HIGHER (wrong or mixed up)"));
      System.out.println();

      // Also try: flash without multiPhaseCheck to get "natural" 2-phase
      neqsim.thermo.system.SystemInterface sysNoMPC = createSystem();
      sysNoMPC.setMultiPhaseCheck(false);
      sysNoMPC.setPressure(88, "bara");
      sysNoMPC.setTemperature(268.0, "C");
      ThermodynamicOperations opsNoMPC = new ThermodynamicOperations(sysNoMPC);
      opsNoMPC.TPflash();
      double gibbsNoMPC = sysNoMPC.getGibbsEnergy();
      System.out.println("  2-phase (no MPC): nPhases=" + sysNoMPC.getNumberOfPhases() + " gasFrac="
          + sysNoMPC.getPhaseFraction("gas", "mole") + " Gibbs=" + gibbsNoMPC);
      System.out.println("  MPC vs no-MPC Gibbs diff: " + (gibbs2 - gibbsNoMPC));
      System.out
          .println("  MPC result " + (gibbs2 <= gibbsNoMPC ? "is better or equal" : "is WORSE"));
    }
    System.out.println();

    // Test 6: Check what happens with PR EOS
    System.out.println("--- Test 6: Same system with PR EOS ---");
    {
      neqsim.thermo.system.SystemInterface sysPR =
          new neqsim.thermo.system.SystemPrEos(243.15, 300.0);
      sysPR.addComponent("nitrogen", 1.64e-3);
      sysPR.addComponent("CO2", 1.64e-3);
      sysPR.addComponent("H2S", 1.64e-3);
      sysPR.addComponent("methane", 90.0);
      sysPR.addComponent("ethane", 2.0);
      sysPR.addComponent("propane", 1.0);
      sysPR.addComponent("i-butane", 1.0);
      sysPR.addComponent("n-butane", 1.0);
      sysPR.addComponent("i-pentane", 1.0);
      sysPR.addComponent("n-pentane", 1.0);
      sysPR.addComponent("n-hexane", 1.0);
      sysPR.addComponent("n-heptane", 1.0);
      sysPR.addComponent("n-octane", 1.0);
      sysPR.addComponent("n-nonane", 1.0);
      sysPR.addComponent("nC10", 1.0);
      sysPR.addComponent("nC11", 1.0);
      sysPR.addComponent("nC12", 1.0);
      sysPR.addComponent("nC13", 1.0);
      sysPR.addComponent("nC14", 1.0);
      sysPR.addComponent("nC15", 1.0);
      sysPR.addComponent("nC16", 1.0);
      sysPR.addComponent("nC17", 1.0);
      sysPR.addComponent("nC18", 1.0);
      sysPR.addComponent("nC19", 1.0);
      sysPR.setMixingRule("classic");
      sysPR.setMolarComposition(new double[] {1.63e-3, 3.23e-3, 0, 3e-1, 4.6e-2, 1.4e-2, 2.2e-2,
          3.9e-3, 8.8e-3, 2.6e-3, 3.2e-2, 1.2e-1, 1.5e-1, 9.8e-2, 7.6e-2, 4.1e-2, 2.5e-2, 1.6e-2,
          1e-2, 5.6e-3, 2.7e-3, 1.3e-3, 8.7e-4, 3.8e-4});
      sysPR.setMultiPhaseCheck(true);
      sysPR.setPressure(88, "bara");
      sysPR.setTemperature(268.0, "C");
      ThermodynamicOperations opsPR = new ThermodynamicOperations(sysPR);
      opsPR.TPflash();
      System.out.println("  PR EOS: nPhases=" + sysPR.getNumberOfPhases() + " gasFrac="
          + sysPR.getPhaseFraction("gas", "mole") + " Gibbs=" + sysPR.getGibbsEnergy());
    }
    System.out.println();

    // Test 7: System-level vs Phase-level beta comparison
    System.out.println("--- Test 7: System vs Phase beta comparison ---");
    {
      neqsim.thermo.system.SystemInterface sys = createSystem();
      sys.setMultiPhaseCheck(true);
      sys.setPressure(88, "bara");
      sys.setTemperature(268.0, "C");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();

      System.out.println("  Number of phases: " + sys.getNumberOfPhases());
      for (int p = 0; p < sys.getNumberOfPhases(); p++) {
        double sysBeta = sys.getBeta(p);
        double phaseBeta = sys.getPhase(p).getBeta();
        System.out.println("  Phase " + p + " (" + sys.getPhase(p).getPhaseTypeName() + "):"
            + " sys.getBeta(" + p + ")=" + sysBeta + " phase.getBeta()=" + phaseBeta
            + (Math.abs(sysBeta - phaseBeta) > 1e-10 ? " *** MISMATCH ***" : " (match)"));
      }

      // Print phaseIndex mapping
      System.out.println("  Phase index mapping:");
      for (int i = 0; i < sys.getNumberOfPhases(); i++) {
        System.out.println("    phaseIndex[" + i + "] = " + sys.getPhaseIndex(i));
      }
    }
    System.out.println();

    // Test 8: Fugacity equality check
    System.out.println("--- Test 8: Fugacity equality check ---");
    {
      neqsim.thermo.system.SystemInterface sys = createSystem();
      sys.setMultiPhaseCheck(true);
      sys.setPressure(88, "bara");
      sys.setTemperature(268.0, "C");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();

      if (sys.getNumberOfPhases() >= 2) {
        System.out.println("  MPC flash - checking fi*xi across phases:");
        double maxRelError = 0;
        int worstComp = -1;
        for (int j = 0; j < sys.getPhase(0).getNumberOfComponents(); j++) {
          double fug0 = sys.getPhase(0).getComponent(j).getFugacityCoefficient()
              * sys.getPhase(0).getComponent(j).getx() * sys.getPressure();
          double fug1 = sys.getPhase(1).getComponent(j).getFugacityCoefficient()
              * sys.getPhase(1).getComponent(j).getx() * sys.getPressure();
          double relErr = (fug1 > 1e-30) ? Math.abs(fug0 - fug1) / fug1 : 0;
          if (relErr > maxRelError) {
            maxRelError = relErr;
            worstComp = j;
          }
          if (relErr > 1e-6) {
            System.out.printf("    %s: fug_ph0=%.6e fug_ph1=%.6e relErr=%.2e%n",
                sys.getPhase(0).getComponent(j).getComponentName(), fug0, fug1, relErr);
          }
        }
        System.out.printf("  Max fugacity relative error: %.2e (comp %d)%n", maxRelError,
            worstComp);
        System.out.println("  " + (maxRelError < 1e-4 ? "OK (converged)" : "POOR CONVERGENCE"));
      }

      // Same check for no-MPC flash
      neqsim.thermo.system.SystemInterface sysNoMPC = createSystem();
      sysNoMPC.setMultiPhaseCheck(false);
      sysNoMPC.setPressure(88, "bara");
      sysNoMPC.setTemperature(268.0, "C");
      ThermodynamicOperations opsNoMPC = new ThermodynamicOperations(sysNoMPC);
      opsNoMPC.TPflash();

      if (sysNoMPC.getNumberOfPhases() >= 2) {
        System.out.println("  No-MPC flash - checking fi*xi across phases:");
        double maxRelError = 0;
        for (int j = 0; j < sysNoMPC.getPhase(0).getNumberOfComponents(); j++) {
          double fug0 = sysNoMPC.getPhase(0).getComponent(j).getFugacityCoefficient()
              * sysNoMPC.getPhase(0).getComponent(j).getx() * sysNoMPC.getPressure();
          double fug1 = sysNoMPC.getPhase(1).getComponent(j).getFugacityCoefficient()
              * sysNoMPC.getPhase(1).getComponent(j).getx() * sysNoMPC.getPressure();
          double relErr = (fug1 > 1e-30) ? Math.abs(fug0 - fug1) / fug1 : 0;
          if (relErr > maxRelError)
            maxRelError = relErr;
        }
        System.out.printf("  Max fugacity relative error (no-MPC): %.2e%n", maxRelError);
      }
    }
    System.out.println();

    // Test 9: Re-normalize after MPC flash and observe effect on Gibbs
    System.out.println("--- Test 9: Re-normalize after MPC flash ---");
    {
      neqsim.thermo.system.SystemInterface sys = createSystem();
      sys.setMultiPhaseCheck(true);
      sys.setPressure(88, "bara");
      sys.setTemperature(268.0, "C");
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();

      double gibbsBefore = sys.getGibbsEnergy();
      double gasFracBefore = sys.getPhaseFraction("gas", "mole");
      double betaSumBefore = 0;
      for (int p = 0; p < sys.getNumberOfPhases(); p++) {
        betaSumBefore += sys.getBeta(p);
      }

      System.out.println("  Before: gasFrac=" + gasFracBefore + " betaSum=" + betaSumBefore
          + " Gibbs=" + gibbsBefore);

      // Re-normalize and re-init
      sys.normalizeBeta();
      sys.init(1);

      double gibbsAfter = sys.getGibbsEnergy();
      double gasFracAfter = sys.getPhaseFraction("gas", "mole");
      double betaSumAfter = 0;
      for (int p = 0; p < sys.getNumberOfPhases(); p++) {
        betaSumAfter += sys.getBeta(p);
      }

      System.out.println("  After:  gasFrac=" + gasFracAfter + " betaSum=" + betaSumAfter
          + " Gibbs=" + gibbsAfter);
      System.out.println("  Gibbs change: " + (gibbsAfter - gibbsBefore));
      System.out.println("  GasFrac change: " + (gasFracAfter - gasFracBefore));
    }
  }
}
