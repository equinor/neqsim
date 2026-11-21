package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Guards the electrolyte-CPA volume-solver speedups: cached volume-independent terms and the gas root-scan
 * short-circuit must not change results.
 *
 * @author NeqSim
 * @version 1.0
 */
class ElectrolyteCPAVolumeSolverPerformanceTest {
  /**
   * Adds a gas, oil, water and MEG feed, optionally with NaCl.
   *
   * @param fluid empty fluid system
   * @param salt whether to add Na+ and Cl-
   * @return the configured fluid
   */
  private static SystemInterface gasOilMeg(SystemInterface fluid, boolean salt) {
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 4.0);
    fluid.addComponent("nC10", 5.0);
    fluid.addComponent("water", 55.5);
    fluid.addComponent("MEG", 6.9);
    if (salt) {
      fluid.addComponent("Na+", 0.9);
      fluid.addComponent("Cl-", 0.9);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /** Checks cached volume-independent terms against direct evaluation and cache refresh on composition change. */
  @Test
  void cachedVolumeIndependentTermsMatchDirectEvaluation() {
    SystemInterface fluid = gasOilMeg(new SystemElectrolyteCPAstatoil(280.0, 100.0), true);
    new ThermodynamicOperations(fluid).TPflash();
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      PhaseModifiedFurstElectrolyteEos phase = (PhaseModifiedFurstElectrolyteEos) fluid.getPhase(i);
      phase.volInit();
      double cachedW = phase.W;
      double cachedEps = phase.eps;
      double cachedSolventDielectric = phase.getSolventDiElectricConstant();
      assertEquals(phase.calcW(phase, phase.getTemperature(), phase.getPressure(), phase.getNumberOfComponents()),
          cachedW, 1e-12 * Math.max(1.0, Math.abs(cachedW)));
      assertEquals(phase.calcEps(), cachedEps, 1e-14);
      assertEquals(phase.calcSolventDiElectricConstant(phase.getTemperature()), cachedSolventDielectric, 1e-12);
      // A composition change without init must refresh the cache.
      phase.getComponent("water").setNumberOfMolesInPhase(phase.getComponent("water").getNumberOfMolesInPhase() * 1.1);
      phase.volInit();
      assertEquals(phase.calcSolventDiElectricConstant(phase.getTemperature()), phase.getSolventDiElectricConstant(),
          1e-12);
    }
  }

  /** Checks that the gas root finder returns the value the full scan returned. */
  @Test
  void gasRootFinderReturnsTheDenseEndAsBefore() {
    SystemInterface fluid = gasOilMeg(new SystemElectrolyteCPAstatoil(280.0, 100.0), false);
    new ThermodynamicOperations(fluid).TPflash();
    PhaseElectrolyteCPA phase = (PhaseElectrolyteCPA) fluid.getPhase(0);
    assertEquals(1.0, phase.calcRootVolFinder(PhaseType.GAS), 0.0);
  }

  /**
   * Checks that ion-free e-CPA matches SRK-CPA and stays fast.
   *
   * @throws Exception if a hydrate calculation fails
   */
  @Test
  void ionFreeElectrolyteCpaMatchesCpaHydrateTemperature() throws Exception {
    SystemInterface cpa = gasOilMeg(new SystemSrkCPAstatoil(285.0, 100.0), false);
    SystemInterface ecpa = gasOilMeg(new SystemElectrolyteCPAstatoil(285.0, 100.0), false);
    new ThermodynamicOperations(cpa).hydrateFormationTemperature();
    long start = System.nanoTime();
    new ThermodynamicOperations(ecpa).hydrateFormationTemperature();
    double seconds = (System.nanoTime() - start) * 1e-9;
    assertEquals(cpa.getTemperature(), ecpa.getTemperature(), 0.01);
    // Was 8-9 s for this case before the volume-solver changes; generous bound for slow CI machines.
    assertTrue(seconds < 8.0, "e-CPA hydrate temperature took " + seconds + " s");
  }
}
