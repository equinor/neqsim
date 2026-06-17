package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression test for the warm-start K-value guard in TPflash for systems that previously converged
 * to 3+ phases.
 *
 * <p>
 * Background: {@code Component.K} is a single scalar per (phase, component), so when a flash
 * converges to 3 phases (gas / HC-liquid / aqueous) the stored K only describes the gas ↔ HC-liquid
 * split. Using those K as a warm-start in the 2-phase loop of the next TPflash call is blind to
 * components that mostly distributed to the 3rd phase (water, glycols, methanol under CPA /
 * electrolyte EOS). The guard in {@link TPflash#run()} disables warm-start for a single flash call
 * when {@code system.getNumberOfPhases() > 2}.
 */
class TPflashWarmStartThreePhaseTest {

  private boolean originalWarmStart;

  @BeforeEach
  void enableWarmStart() {
    originalWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    ThermodynamicModelSettings.setUseWarmStartKValues(true);
  }

  @AfterEach
  void restoreWarmStart() {
    ThermodynamicModelSettings.setUseWarmStartKValues(originalWarmStart);
  }

  /**
   * Build a natural-gas + water + MEG fluid under SRK-CPA that naturally forms a 3-phase split (gas
   * / HC-liquid / aqueous) at the test conditions.
   */
  private SystemInterface buildCpaFluid() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 60.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-hexane", 1.0);
    fluid.addComponent("nC10", 1.0);
    fluid.addComponent("MEG", 2.0);
    fluid.addComponent("water", 5.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Flashing the same 3-phase CPA fluid twice with warm-start enabled must give the same result as
   * the first flash. Without the guard, the second flash reuses stale gas ↔ HC-liquid K for
   * water/MEG (which actually live in the aqueous phase) and can diverge or converge to a different
   * split.
   */
  @Test
  void repeatedThreePhaseFlashIsStableWithWarmStart() {
    SystemInterface fluid = buildCpaFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // First flash: cold start, authoritative result.
    ops.TPflash();
    int phasesFirst = fluid.getNumberOfPhases();
    double betaGasFirst = fluid.getBeta(0);
    double waterInGasFirst = fluid.getPhase(0).getComponent("water").getx();
    assertTrue(phasesFirst >= 2, "expected at least 2 phases on first flash");

    // Second flash at identical conditions. Guard must force Wilson because
    // the system is carrying 3 phases from the previous call.
    ops.TPflash();
    int phasesSecond = fluid.getNumberOfPhases();
    double betaGasSecond = fluid.getBeta(0);
    double waterInGasSecond = fluid.getPhase(0).getComponent("water").getx();

    assertEquals(phasesFirst, phasesSecond, "phase count must be stable across repeated flashes");
    assertEquals(betaGasFirst, betaGasSecond, 1e-6, "gas phase beta must be reproducible");
    assertEquals(waterInGasFirst, waterInGasSecond, 1e-8,
        "water mole fraction in gas must be reproducible");
  }

  /**
   * Perturb the operating conditions slightly (simulating a recycle-loop step) and re-flash. The
   * guard ensures the result matches a fresh cold flash started from Wilson K at the same perturbed
   * conditions.
   */
  @Test
  void perturbedRecycleStepMatchesColdFlash() {
    SystemInterface fluid = buildCpaFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Perturb T/P (typical recycle-loop change): +2 K, -0.5 bar.
    fluid.setTemperature(300.15);
    fluid.setPressure(59.5);
    ops.TPflash();
    double warmBetaGas = fluid.getBeta(0);
    double warmWaterInAq =
        fluid.getPhase(fluid.getNumberOfPhases() - 1).getComponent("water").getx();
    int warmPhases = fluid.getNumberOfPhases();

    // Cold reference: flash a fresh fluid at the perturbed conditions from
    // Wilson K (warm-start disabled for this reference to avoid any reuse).
    SystemInterface cold = buildCpaFluid();
    cold.setTemperature(300.15);
    cold.setPressure(59.5);
    boolean prev = ThermodynamicModelSettings.isUseWarmStartKValues();
    ThermodynamicModelSettings.setUseWarmStartKValues(false);
    try {
      new ThermodynamicOperations(cold).TPflash();
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(prev);
    }
    double coldBetaGas = cold.getBeta(0);
    double coldWaterInAq = cold.getPhase(cold.getNumberOfPhases() - 1).getComponent("water").getx();
    int coldPhases = cold.getNumberOfPhases();

    assertEquals(coldPhases, warmPhases,
        "warm-restart must find the same number of phases as a cold flash");
    assertEquals(coldBetaGas, warmBetaGas, 1e-4, "gas beta must match cold flash within 1e-4");
    assertEquals(coldWaterInAq, warmWaterInAq, 1e-4,
        "water mole fraction in aqueous phase must match cold flash");
  }

  /**
   * Sanity check: after the 3-phase flash returns, the thread-local warm-start flag must be
   * restored to its entry value (try/finally contract).
   */
  @Test
  void warmStartFlagIsRestoredAfterThreePhaseFlash() {
    SystemInterface fluid = buildCpaFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    assertTrue(ThermodynamicModelSettings.isUseWarmStartKValues(),
        "test precondition: warm-start enabled by @BeforeEach");
    ops.TPflash();
    assertTrue(ThermodynamicModelSettings.isUseWarmStartKValues(),
        "warm-start flag must be restored after TPflash on a 3-phase system");
  }
}
