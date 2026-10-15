package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Model-consistency regressions for the named-component cold-start collapse in issue #4324.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class NamedWaxColdStartTest extends neqsim.NeqSimTest {
  /**
   * Builds the reported synthetic feed without changing component or wax-model parameters.
   *
   * @param pressure absolute pressure in bar
   * @return initialized SRK/classic feed at 320 K
   */
  private static SystemInterface feed(double pressure) {
    SystemInterface fluid = new SystemSrkEos(320.0, pressure);
    fluid.addComponent("methane", 40.0);
    fluid.addComponent("n-heptane", 10.0);
    fluid.addComponent("nc14", 10.0);
    fluid.addComponent("nc20", 20.0);
    fluid.addComponent("nc24", 20.0);
    fluid.setMixingRule(2);
    fluid.addSolidComplexPhase("wax");
    fluid.setMultiphaseWaxCheck(true);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    fluid.init(1);
    return fluid;
  }

  /**
   * Flashes an independent clone, optionally resetting phase storage first.
   *
   * @param feed initial state
   * @param temperature temperature in K
   * @param initialize whether to explicitly initialize at the trial temperature
   * @return flashed state
   */
  private static SystemInterface flash(SystemInterface feed, double temperature, boolean initialize) {
    SystemInterface result = feed.clone();
    result.setTemperature(temperature);
    if (initialize) {
      result.init(0);
    }
    new ThermodynamicOperations(result).TPflash();
    return result;
  }

  /**
   * The gas and wax must survive cooling, including fresh, warm and previously wax-bearing starts.
   *
   * @param pressure absolute pressure in bar
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0, 5.0})
  void coolingPreservesEquilibratedGasOilWax(double pressure) {
    SystemInterface original = feed(pressure);
    for (boolean initialize : new boolean[] {false, true}) {
      for (double start : new double[] {320.0, 270.0, 265.0}) {
        SystemInterface initial = start == 320.0 ? original : flash(original, start, initialize);
        double previousWax = Double.POSITIVE_INFINITY;
        for (double temperature : new double[] {260.0, 265.0, 270.0, 275.0}) {
          SystemInterface result = flash(initial, temperature, initialize);
          assertTrue(result.hasPhaseType("gas"), "Missing gas at " + temperature + " K");
          assertTrue(result.hasPhaseType("oil"), "Missing oil at " + temperature + " K");
          assertEquals(temperature <= 270.0, result.hasPhaseType("wax"), "Wax topology at " + temperature + " K");
          double wax = waxFraction(result);
          assertTrue(wax <= previousWax, "Wax must decrease on warming this feed");
          previousWax = wax;
          assertEquilibriumAndInventory(original, result);
          if (start == 320.0 && !initialize) {
            assertTangentPlaneStability(result);
          }
        }
      }
    }
  }

  /**
   * Initial temperatures must locate the same physical model transition, not a spurious cold onset.
   *
   * @param pressure absolute pressure in bar
   * @throws Exception if the WAT operation fails
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0, 5.0})
  void appearanceIsIndependentOfInitialTemperature(double pressure) throws Exception {
    double reference = Double.NaN;
    for (double guess : new double[] {320.0, 270.0, 265.0}) {
      SystemInterface original = feed(pressure);
      original.setTemperature(guess);
      SystemInterface result = original.clone();
      new ThermodynamicOperations(result).calcWAT();
      double wat = result.getTemperature();
      assertTrue(wat > 270.0 && wat < 275.0, "Unexpected model onset: " + wat);
      if (Double.isFinite(reference)) {
        assertEquals(reference, wat, 1e-4);
      }
      reference = wat;
      assertEquilibriumAndInventory(original, result);
      assertTrue(waxFraction(flash(original, wat - 0.01, true)) > 1e-8);
      assertFalse(waxFraction(flash(original, wat + 0.01, true)) > 1e-8);
    }
  }

  /**
   * Reads wax by conserved inventory, independent of the separate #4309 mass-accessor bug.
   *
   * @param fluid flashed fluid
   * @return wax mass fraction
   */
  private static double waxFraction(SystemInterface fluid) {
    return fluid.hasPhaseType("wax") ? fluid.getWtFraction(fluid.getPhaseNumberOfPhase("wax")) : 0.0;
  }

  /**
   * Verifies phase normalization, total mass, component inventory and equal component fugacities.
   *
   * @param original conserved feed
   * @param result flashed state
   */
  private static void assertEquilibriumAndInventory(SystemInterface original, SystemInterface result) {
    double betaSum = 0.0;
    double mass = 0.0;
    double[] moles = new double[original.getNumberOfComponents()];
    for (int phase = 0; phase < result.getNumberOfPhases(); phase++) {
      PhaseInterface state = result.getPhase(phase);
      assertTrue(result.getBeta(phase) > 0.0 && result.getBeta(phase) <= 1.0);
      betaSum += result.getBeta(phase);
      mass += state.getNumberOfMolesInPhase() * state.getMolarMass();
      double sum = 0.0;
      for (int component = 0; component < moles.length; component++) {
        double x = state.getComponent(component).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        sum += x;
        moles[component] += state.getComponent(component).getNumberOfMolesInPhase();
        double logFugacity = Math.log(x) + state.getComponent(component).getLogFugacityCoefficient();
        double reference = Math.log(result.getPhase(0).getComponent(component).getx())
            + result.getPhase(0).getComponent(component).getLogFugacityCoefficient();
        assertEquals(reference, logFugacity, 1e-5, "Component fugacity mismatch: " + component);
      }
      assertEquals(1.0, sum, 1e-10);
    }
    assertEquals(1.0, betaSum, 1e-8);
    assertEquals(original.getTotalNumberOfMoles() * original.getMolarMass(), mass, 1e-6);
    for (int component = 0; component < moles.length; component++) {
      assertEquals(original.getPhase(0).getComponent(component).getNumberOfmoles(), moles[component], 1e-6);
    }
  }

  /**
   * Independently minimizes tangent-plane trials using the configured EOS and wax fugacity coefficients. This tests
   * model consistency, not experimental accuracy or a proof of global stability for arbitrary feeds.
   *
   * @param result equilibrium under test
   */
  private static void assertTangentPlaneStability(SystemInterface result) {
    int components = result.getNumberOfComponents();
    double[] chemicalPotential = new double[components];
    for (int component = 0; component < components; component++) {
      chemicalPotential[component] = Math.log(result.getPhase(0).getComponent(component).getx())
          + result.getPhase(0).getComponent(component).getLogFugacityCoefficient();
    }
    PhaseInterface[] prototypes = {result.getPhase("gas"), result.getPhase("oil"), result.getPhases()[5]};
    PhaseType[] roots = {PhaseType.GAS, PhaseType.LIQUID, PhaseType.WAX};
    for (int phase = 0; phase < prototypes.length; phase++) {
      for (int seed = -1; seed < components; seed++) {
        PhaseInterface trial = prototypes[phase].clone();
        for (int component = 0; component < components; component++) {
          trial.getComponent(component)
              .setx(seed < 0 ? result.getPhase(0).getComponent(component).getz() : component == seed ? 1.0 : 1e-12);
        }
        trial.normalize();
        double sum = Double.NaN;
        boolean converged = false;
        for (int iteration = 0; iteration < 100; iteration++) {
          trial.init(1.0, components, 1, roots[phase], 1.0);
          double[] weights = new double[components];
          sum = 0.0;
          for (int component = 0; component < components; component++) {
            double phi = trial.getComponent(component).fugcoef(trial);
            weights[component] = Math.exp(chemicalPotential[component] - Math.log(phi));
            sum += weights[component];
          }
          assertTrue(Double.isFinite(sum) && sum > 0.0);
          double change = 0.0;
          for (int component = 0; component < components; component++) {
            double fraction = weights[component] / sum;
            change = Math.max(change, Math.abs(fraction - trial.getComponent(component).getx()));
            trial.getComponent(component).setx(fraction);
          }
          if (change < 1e-10) {
            converged = true;
            break;
          }
        }
        assertTrue(converged, "Unconverged independent stability trial: " + roots[phase] + ", seed=" + seed);
        assertTrue(1.0 - sum >= -1e-6,
            "Unstable " + roots[phase] + " trial at " + result.getTemperature() + " K: TPD=" + (1.0 - sum));
      }
    }
  }
}
