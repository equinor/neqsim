package neqsim.thermodynamicoperations.flashops;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Decomposes TPflash+stability cost into isolated sub-phases to find speedup opportunities. Each
 * scenario is run as a fresh clone so allocation costs are included.
 */
public class StabilityProfileTest {

  private SystemInterface makeHeavyFluid() {
    SystemInterface f = new SystemSrkEos(298.0, 80.0);
    f.addComponent("nitrogen", 0.01);
    f.addComponent("CO2", 0.02);
    f.addComponent("methane", 0.65);
    f.addComponent("ethane", 0.10);
    f.addComponent("propane", 0.06);
    f.addComponent("i-butane", 0.02);
    f.addComponent("n-butane", 0.03);
    f.addComponent("i-pentane", 0.015);
    f.addComponent("n-pentane", 0.015);
    f.addComponent("n-hexane", 0.01);
    f.addComponent("n-heptane", 0.01);
    f.addComponent("n-octane", 0.01);
    f.setMixingRule("classic");
    return f;
  }

  /** Lighter single-phase gas at 80 bara, 25C — triggers stability path. */
  private SystemInterface makeLightFluid() {
    SystemInterface f = new SystemSrkEos(298.0, 80.0);
    f.addComponent("nitrogen", 0.02);
    f.addComponent("CO2", 0.03);
    f.addComponent("methane", 0.90);
    f.addComponent("ethane", 0.035);
    f.addComponent("propane", 0.015);
    f.setMixingRule("classic");
    return f;
  }

  /** Wet gas at 60 bara, 20C — classic 2-phase at flash temperature. */
  private SystemInterface makeWetGas() {
    SystemInterface f = new SystemSrkEos(293.0, 60.0);
    f.addComponent("nitrogen", 0.01);
    f.addComponent("CO2", 0.02);
    f.addComponent("methane", 0.70);
    f.addComponent("ethane", 0.08);
    f.addComponent("propane", 0.05);
    f.addComponent("n-butane", 0.04);
    f.addComponent("n-pentane", 0.02);
    f.addComponent("n-hexane", 0.02);
    f.addComponent("n-heptane", 0.02);
    f.addComponent("water", 0.04);
    f.setMixingRule("classic");
    return f;
  }

  @Test
  void profileStabilityBreakdown() throws Exception {
    int ITERS = 200;

    SystemInterface[] cases =
        new SystemInterface[] {makeLightFluid(), makeHeavyFluid(), makeWetGas()};
    String[] caseNames = new String[] {"Light 5c gas (1ph)", "Heavy 13c gas", "Wet gas 2ph"};

    System.out.printf("%n%-22s %12s %12s %12s %12s %12s%n", "case", "flash_noStab", "flash_stab",
        "stab_delta", "stab_only", "multiPh+stab");
    System.out.println(
        "--------------------------------------------------------------------------------------");

    for (int c = 0; c < cases.length; c++) {
      SystemInterface base = cases[c];
      // Warm up
      for (int w = 0; w < 5; w++) {
        SystemInterface cln = base.clone();
        cln.checkStability(false);
        new ThermodynamicOperations(cln).TPflash();
      }

      // A) TPflash without stability
      double a;
      {
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
          SystemInterface cln = base.clone();
          cln.checkStability(false);
          cln.setMultiPhaseCheck(false);
          new ThermodynamicOperations(cln).TPflash();
        }
        a = (System.nanoTime() - t0) / (double) ITERS / 1e6;
      }

      // B) TPflash with stability
      double b;
      {
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
          SystemInterface cln = base.clone();
          cln.checkStability(true);
          cln.setMultiPhaseCheck(false);
          new ThermodynamicOperations(cln).TPflash();
        }
        b = (System.nanoTime() - t0) / (double) ITERS / 1e6;
      }

      // C) Bare stability analysis only (no full flash)
      double d;
      {
        // Pre-flash to prime state
        SystemInterface primed = base.clone();
        primed.checkStability(false);
        primed.setMultiPhaseCheck(false);
        new ThermodynamicOperations(primed).TPflash();

        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
          SystemInterface cln = primed.clone();
          Flash flash = new TPflash(cln);
          try {
            flash.stabilityAnalysis();
          } catch (Exception ex) {
            // ignore
          }
        }
        d = (System.nanoTime() - t0) / (double) ITERS / 1e6;
      }

      // D) TPflash with multiPhase + stability
      double e;
      {
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
          SystemInterface cln = base.clone();
          cln.checkStability(true);
          cln.setMultiPhaseCheck(true);
          new ThermodynamicOperations(cln).TPflash();
        }
        e = (System.nanoTime() - t0) / (double) ITERS / 1e6;
      }

      System.out.printf("%-22s %12.3f %12.3f %12.3f %12.3f %12.3f%n", caseNames[c], a, b, (b - a),
          d, e);
    }
    System.out.println();
  }
}
