package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Qualifies Huron-Vidal parameter orientation, temperature offsets and EOS-specific classical limits.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HuronVidalParameterWiringTest extends neqsim.NeqSimTest {
  /**
   * Linear covolume curvature cannot depend on the initialization order of component derivatives.
   *
   * @param eos cubic EOS identifier
   */
  @ParameterizedTest
  @ValueSource(strings = {"SRK", "PR"})
  void linearCovolumeHasZeroCurvatureBeforeComponentInitialization(String eos) {
    SystemInterface fluid = createSystem(eos, 320.0);
    fluid.addComponent("methane", 1.4);
    fluid.addComponent("ethane", 0.6);
    fluid.setMixingRule(4);
    PhaseInterface phase = fluid.getPhase(0);
    assertEquals(0.0, ((ComponentEosInterface) phase.getComponent(1)).getBi(), 0.0,
        "The later component has not published its covolume derivative");
    EosMixingRulesInterface rule = (EosMixingRulesInterface) phase.getMixingRule();
    assertEquals(0.0, rule.calcBij(0, 1, phase, 320.0, 50.0, 2), 0.0);
    assertEquals(0.0, rule.calcBij(1, 0, phase, 320.0, 50.0, 2), 0.0);
  }

  /**
   * The linear-rule shortcut must preserve nonzero curvature for a nonlinear covolume rule.
   *
   * @param eos cubic EOS identifier
   */
  @ParameterizedTest
  @ValueSource(strings = {"SRK", "PR"})
  void nonlinearCovolumeRetainsItsCompositionCurvature(String eos) {
    SystemInterface fluid = hydrocarbons(eos, 2);
    PhaseInterface phase = fluid.getPhase(0);
    EosMixingRulesInterface rule = (EosMixingRulesInterface) phase.getMixingRule();
    rule.setBmixType(1);
    fluid.init(3);
    ComponentEosInterface first = (ComponentEosInterface) phase.getComponent(0);
    ComponentEosInterface second = (ComponentEosInterface) phase.getComponent(1);
    double meanRootB = 0.5 * (Math.sqrt(first.getb()) + Math.sqrt(second.getb()));
    double expected = (2.0 * meanRootB * meanRootB - first.getBi() - second.getBi()) / phase.getNumberOfMolesInPhase();
    assertTrue(Math.abs(expected) > 1.0e-6, "The nonlinear covolume has nonzero curvature");
    assertClose(expected, rule.calcBij(0, 1, phase, 320.0, 50.0, 3));
  }

  /**
   * An entirely classical pair matrix must reproduce the selected cubic EOS and its derivatives.
   *
   * @param eos cubic EOS identifier
   */
  @ParameterizedTest
  @ValueSource(strings = {"SRK", "PR"})
  void allClassicPairsRecoverCubicMixingRule(String eos) {
    SystemInterface reference = hydrocarbons(eos, 2);
    for (int rule : new int[] {3, 4}) {
      SystemInterface actual = hydrocarbons(eos, rule);
      assertObservablesEqual(reference.getPhase(0), actual.getPhase(0), new int[] {0, 1, 2});
    }
  }

  /**
   * Database orientation follows the component identities when the fluid component order changes.
   *
   * @param eos cubic EOS identifier
   */
  @ParameterizedTest
  @ValueSource(strings = {"SRK", "PR"})
  void databaseParametersAndResultsFollowComponentPermutation(String eos) {
    SystemInterface reference = polarMixture(eos, false, 323.15);
    SystemInterface permuted = polarMixture(eos, true, 323.15);
    HVMixingRulesInterface rule = (HVMixingRulesInterface) reference.getPhase(0).getMixingRule();
    HVMixingRulesInterface reordered = (HVMixingRulesInterface) permuted.getPhase(0).getMixingRule();
    assertEquals(410.67, rule.getHVDijParameter(0, 1), 0.0);
    assertEquals(-169.63, rule.getHVDijParameter(1, 0), 0.0);
    assertEquals(1.89, rule.getHVDijTParameter(0, 1), 0.0);
    assertEquals(-1.11, rule.getHVDijTParameter(1, 0), 0.0);
    int[] permutation = {2, 0, 1};
    for (int i = 0; i < permutation.length; i++) {
      for (int j = 0; j < permutation.length; j++) {
        assertEquals(rule.getHVDijParameter(i, j), reordered.getHVDijParameter(permutation[i], permutation[j]), 0.0);
        assertEquals(rule.getHVDijTParameter(i, j), reordered.getHVDijTParameter(permutation[i], permutation[j]), 0.0);
      }
    }
    assertObservablesEqual(reference.getPhase(0), permuted.getPhase(0), permutation);
  }

  /**
   * Parameter changes and temperature continuation must refresh the result without stale GE state.
   *
   * @param eos cubic EOS identifier
   */
  @ParameterizedTest
  @ValueSource(strings = {"SRK", "PR"})
  void temperatureOffsetsAreUsedAndRuntimeUpdatesAreReversible(String eos) {
    SystemInterface fluid = polarMixture(eos, false, 323.15);
    SystemInterface reference = polarMixture(eos, false, 323.15);
    HVMixingRulesInterface rule = (HVMixingRulesInterface) fluid.getPhase(0).getMixingRule();
    double originalA = fluid.getPhase(0).getA();
    rule.setHVDijTParameter(0, 1, 0.0);
    rule.setHVDijTParameter(1, 0, 0.0);
    fluid.init(3);
    assertTrue(Math.abs(originalA - fluid.getPhase(0).getA()) > 1.0,
        "A nonzero dimensionless temperature offset must affect the attraction term");
    rule.setHVDijTParameter(0, 1, 1.89);
    rule.setHVDijTParameter(1, 0, -1.11);
    fluid.init(3);
    assertObservablesEqual(reference.getPhase(0), fluid.getPhase(0), new int[] {0, 1, 2});
    fluid.setTemperature(343.15);
    fluid.init(3);
    SystemInterface fresh = polarMixture(eos, false, 343.15);
    assertObservablesEqual(fresh.getPhase(0), fluid.getPhase(0), new int[] {0, 1, 2});
    fluid.setTemperature(323.15);
    fluid.init(3);
    assertObservablesEqual(reference.getPhase(0), fluid.getPhase(0), new int[] {0, 1, 2});
  }

  /**
   * Builds an entirely nonpolar fluid with a non-unit mole inventory.
   *
   * @param eos cubic EOS identifier
   * @param rule numeric mixing rule
   * @return initialized single-phase fluid
   */
  private static SystemInterface hydrocarbons(String eos, int rule) {
    SystemInterface fluid = createSystem(eos, 320.0);
    fluid.addComponent("methane", 1.4);
    fluid.addComponent("ethane", 0.4);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule(rule);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(3);
    return fluid;
  }

  /**
   * Builds a polar fluid with directed database interactions and a non-unit mole inventory.
   *
   * @param eos cubic EOS identifier
   * @param permuted whether to reorder the component identities
   * @param temperature temperature in kelvin
   * @return initialized single-phase fluid
   */
  private static SystemInterface polarMixture(String eos, boolean permuted, double temperature) {
    SystemInterface fluid = createSystem(eos, temperature);
    if (permuted) {
      fluid.addComponent("water", 0.4);
      fluid.addComponent("methane", 1.4);
      fluid.addComponent("methanol", 0.2);
    } else {
      fluid.addComponent("methanol", 0.2);
      fluid.addComponent("water", 0.4);
      fluid.addComponent("methane", 1.4);
    }
    fluid.setMixingRule(4);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(3);
    return fluid;
  }

  /**
   * Creates either cubic EOS at a fixed absolute pressure.
   *
   * @param eos cubic EOS identifier
   * @param temperature temperature in kelvin
   * @return fluid at 50 bara
   */
  private static SystemInterface createSystem(String eos, double temperature) {
    return "PR".equals(eos) ? new SystemPrEos(temperature, 50.0) : new SystemSrkEos(temperature, 50.0);
  }

  /**
   * Checks attraction, its thermal and component derivatives, and fugacity under a component permutation.
   *
   * @param expected reference phase
   * @param actual tested phase
   * @param permutation tested index for each reference component
   */
  private static void assertObservablesEqual(PhaseInterface expected, PhaseInterface actual, int[] permutation) {
    assertClose(expected.getA(), actual.getA());
    assertClose(expected.getAT(), actual.getAT());
    assertClose(expected.getATT(), actual.getATT());
    for (int i = 0; i < permutation.length; i++) {
      int mapped = permutation[i];
      ComponentEosInterface expectedComponent = (ComponentEosInterface) expected.getComponent(i);
      ComponentEosInterface actualComponent = (ComponentEosInterface) actual.getComponent(mapped);
      assertClose(expectedComponent.getAi(), actualComponent.getAi());
      assertClose(expectedComponent.getAiT(), actualComponent.getAiT());
      assertClose(expected.getComponent(i).getLogFugacityCoefficient(),
          actual.getComponent(mapped).getLogFugacityCoefficient());
      for (int j = 0; j < permutation.length; j++) {
        assertClose(expectedComponent.getAij(j), actualComponent.getAij(permutation[j]));
      }
    }
  }

  /**
   * Compares finite observables using a scale-aware roundoff tolerance.
   *
   * @param expected reference value
   * @param actual tested value
   */
  private static void assertClose(double expected, double actual) {
    assertTrue(Double.isFinite(expected) && Double.isFinite(actual), "Expected finite observables");
    assertEquals(expected, actual, 2.0e-10 * Math.max(1.0, Math.abs(expected)));
  }
}
