package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualifies numerical consistency of synthetic selected-pair performance fixtures, not inhibitor partition accuracy.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalSelectedPairPerformanceTest {
  /** Checks that deactivating the sole selected pair restores full CLASSICAL equilibrium for either mixture. */
  @Test
  void deactivatingOnlyCo2WaterRestoresClassicalFullFlash() {
    for (int profile = 0; profile < 3; profile++) {
      for (int size : new int[] {5, 15}) {
        SystemInterface allClassic = fluid(profile, size, 4, false);
        for (int phase = 0; phase < allClassic.getMaxNumberOfPhases(); phase++) {
          PhaseInterface state = allClassic.getPhase(phase);
          int water = state.getComponent("water").getComponentNumber();
          int gas = state.getComponent("CO2").getComponentNumber();
          HVMixingRulesInterface rule = (HVMixingRulesInterface) state.getMixingRule();
          rule.setClassicOrHV(water, gas, "Classic");
          rule.setClassicOrHV(gas, water, "Classic");
        }
        SystemInterface classical = fluid(profile, size, 2, true);
        qualify(allClassic);
        qualify(classical);
        assertEquals(classical.getNumberOfPhases(), allClassic.getNumberOfPhases());
        for (int phase = 0; phase < classical.getNumberOfPhases(); phase++) {
          PhaseInterface expected = classical.getPhase(phase);
          PhaseInterface actual = allClassic.getPhase(expected.getType());
          assertEquals(expected.getBeta(), actual.getBeta(), 1e-10);
          for (int i = 0; i < size; i++) {
            String name = expected.getComponent(i).getComponentName();
            assertEquals(expected.getComponent(name).getx(), actual.getComponent(name).getx(), 1e-10);
          }
        }
      }
    }
  }

  /** Checks every native acceptance identity on fresh, repeated, changed and returned multicomponent states. */
  @Test
  void selectedPairMixturesRetainNativeConsistencyAndHistory() {
    for (int profile = 0; profile < 3; profile++) {
      for (int size : new int[] {5, 15}) {
        for (int rule : new int[] {2, 4}) {
          SystemInterface repeated = fluid(profile, size, rule, false);
          for (double t : new double[] {353.15, 353.15, 353.35, 353.15}) {
            repeated.setTemperature(t);
            qualify(repeated);
            SystemInterface fresh = fluid(profile, size, rule, true);
            fresh.setTemperature(t);
            qualify(fresh);
            assertEquals(fresh.getNumberOfPhases(), repeated.getNumberOfPhases());
            for (int phase = 0; phase < fresh.getNumberOfPhases(); phase++) {
              PhaseInterface expected = fresh.getPhase(phase);
              PhaseInterface actual = repeated.getPhase(expected.getType());
              assertEquals(expected.getBeta(), actual.getBeta(), 1e-10);
              for (int i = 0; i < size; i++) {
                String name = expected.getComponent(i).getComponentName();
                assertEquals(expected.getComponent(name).getx(), actual.getComponent(name).getx(), 1e-10);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Creates a matched control without selecting any further polar pair.
   *
   * @param profile source configuration
   * @param size component count
   * @param rule CLASSICAL2 or selected-pair4
   * @param reverse component order
   * @return independently configured fluid
   */
  private static SystemInterface fluid(int profile, int size, int rule, boolean reverse) {
    SystemInterface result = HuronVidalSelectedPairPerformanceBenchmark.fluid(profile, size, reverse);
    if (rule == 2) {
      result.setMixingRule(2);
    }
    return result;
  }

  /**
   * Asserts identities at the declared absolute gates before a benchmark can be accepted.
   *
   * @param fluid configured synthetic mixture
   */
  private static void qualify(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    HuronVidalSelectedPairPerformanceBenchmark.residuals(fluid);
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    checker.setMaxError(1e-8);
    assertTrue(checker.checkFugacityCoefficients(), "Gibbs energy identity");
    assertTrue(checker.checkFugacityCoefficientsDn(), "Gibbs-Duhem");
    assertTrue(checker.checkFugacityCoefficientsDn2(), "Symmetric Hessian");
    assertTrue(checker.checkFugacityCoefficientsDP(), "Pressure identity");
    assertTrue(checker.checkFugacityCoefficientsDT(), "Temperature identity");
    checker.setMaxError(1e-6);
    byte[] before = SerializationUtils.serialize(fluid);
    assertTrue(checker.checkNumerically(), "Detached numerical finite differences");
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
  }
}
