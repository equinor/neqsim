package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentUMRCPAvolcor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRCPAvolcor;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks translated CPA state initialization before volume and fugacity evaluation.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
class PhaseUMRCPAvolcorStateLifecycleTest {
  /** Verifies EOS closure and extensive translation derivatives after phase amounts and composition change. */
  @Test
  void translationUsesCurrentPhaseStateOnFirstInitialization() {
    SystemInterface system = new SystemUMRCPAvolcor(300.0, 50.0);
    system.addComponent("methane", 0.7);
    system.addComponent("ethane", 0.3);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    system.init(0);
    PhaseUMRCPAvolcor phase = (PhaseUMRCPAvolcor) system.getPhase(0);
    for (int level : new int[] {1, 2, 3}) {
      for (double[] state : new double[][] {{1.0, 0.8, 0.7}, {1.0, 0.02, 0.4}, {100.0, 0.8, 0.9}}) {
        phase.getComponent(0).setx(state[2]);
        phase.getComponent(1).setx(1.0 - state[2]);
        phase.init(state[0], 2, level, PhaseType.GAS, state[1]);
        assertEquals(50.0, phase.calcPressure(), 1.0e-7, "first initialization must close the EOS pressure");
        double expectedC = 0.0;
        double partialVolume = 0.0;
        double[] logPhi = new double[2];
        for (int component = 0; component < 2; component++) {
          ComponentUMRCPAvolcor translated = (ComponentUMRCPAvolcor) phase.getComponent(component);
          expectedC += translated.getNumberOfMolesInPhase() * translated.getc();
          partialVolume += translated.getx() * translated.getVoli();
          assertEquals(translated.getc(), translated.getCi(), 1.0e-12,
              "linear translation derivative must use the current phase amount");
          logPhi[component] = Math.log(translated.fugcoef(phase));
          if (level >= 3) {
            for (int other = 0; other < 2; other++) {
              assertEquals(0.0, translated.getCij(other), 1.0e-12,
                  "linear translation has no second mole-number derivative");
            }
          }
        }
        assertEquals(expectedC, phase.getC(), Math.max(1.0e-12, Math.abs(expectedC) * 1.0e-12));
        double volume = phase.getMolarVolume();
        assertEquals(volume, partialVolume, Math.abs(volume) * 1.0e-9,
            "partial molar volumes must consume current translation derivatives");
        phase.init(state[0], 2, level, PhaseType.GAS, state[1]);
        assertEquals(volume, phase.getMolarVolume(), Math.abs(volume) * 1.0e-10,
            "repeating initialization must not repair a stale translation");
        for (int component = 0; component < 2; component++) {
          assertEquals(logPhi[component], Math.log(phase.getComponent(component).fugcoef(phase)), 1.0e-10);
        }
      }
    }
  }

  /** Checks analytic fugacity identities and their finite-difference derivatives on a translated dry mixture. */
  @Test
  void translatedMixtureRetainsThermodynamicConsistency() {
    SystemInterface system = new SystemUMRCPAvolcor(300.0, 50.0);
    system.addComponent("methane", 0.7);
    system.addComponent("ethane", 0.3);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    ThermodynamicModelTest modelTest = new ThermodynamicModelTest(system);
    modelTest.setMaxError(1.0e-8);
    assertTrue(modelTest.checkFugacityCoefficients());
    assertTrue(modelTest.checkFugacityCoefficientsDn());
    assertTrue(modelTest.checkFugacityCoefficientsDn2());
    assertTrue(modelTest.checkFugacityCoefficientsDP());
    assertTrue(modelTest.checkFugacityCoefficientsDT());
    modelTest.setMaxError(1.0e-5);
    assertTrue(modelTest.checkNumerically());
  }

  /** Verifies the split on fresh and cloned states over nearby temperatures and pressures. */
  @Test
  void methaneWaterEquilibriumConservesFeedOnReusedAndClonedStates() {
    SystemInterface reused = methaneWater();
    for (double[] state : new double[][] {{298.15, 50.0}, {280.0, 10.0}, {320.0, 80.0}, {298.15, 50.0}}) {
      SystemInterface fresh = methaneWater();
      for (SystemInterface system : new SystemInterface[] {fresh, reused}) {
        system.setTemperature(state[0]);
        system.setPressure(state[1]);
        new ThermodynamicOperations(system).TPflash();
        assertMethaneWaterEquilibrium(system);
      }
      assertEquals(fresh.getPhase(PhaseType.AQUEOUS).getBeta(), reused.getPhase(PhaseType.AQUEOUS).getBeta(), 1.0e-8);
      reused = reused.clone();
    }
  }

  /**
   * Creates the CI regression feed using the documented UMR mixing rule.
   *
   * @return methane/water fluid at 298.15 K and 50 bara
   */
  private static SystemInterface methaneWater() {
    SystemInterface system = new SystemUMRCPAvolcor(298.15, 50.0);
    system.addComponent("methane", 0.9);
    system.addComponent("water", 0.1);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    system.setMultiPhaseCheck(true);
    return system;
  }

  /**
   * Requires physical gas/aqueous phases, component conservation and fugacity equality.
   *
   * @param system flashed methane/water system
   */
  private static void assertMethaneWaterEquilibrium(SystemInterface system) {
    system.init(1);
    assertEquals(2, system.getNumberOfPhases());
    assertTrue(system.hasPhaseType(PhaseType.GAS));
    assertTrue(system.hasPhaseType(PhaseType.AQUEOUS));
    double betaSum = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseInterface current = system.getPhase(phase);
      betaSum += system.getBeta(phase);
      assertTrue(system.getBeta(phase) > 1.0e-4);
      assertEquals(system.getPressure(), ((PhaseEos) current).calcPressure(), 1.0e-6);
      assertTrue(Double.isFinite(current.getDensity()) && current.getDensity() > 0.0);
      assertEquals(1.0, current.getComponent(0).getx() + current.getComponent(1).getx(), 1.0e-12);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    assertTrue(system.getPhase(PhaseType.AQUEOUS).getComponent("water").getx() > 0.95);
    for (int component = 0; component < 2; component++) {
      double feed = 0.0;
      double referenceLogFugacity = Math.log(system.getPhase(0).getComponent(component).getx())
          + system.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        feed += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
        assertEquals(referenceLogFugacity, Math.log(system.getPhase(phase).getComponent(component).getx())
            + system.getPhase(phase).getComponent(component).getLogFugacityCoefficient(), 1.0e-7);
      }
      assertEquals(system.getComponent(component).getz(), feed, 1.0e-9);
    }
  }
}
