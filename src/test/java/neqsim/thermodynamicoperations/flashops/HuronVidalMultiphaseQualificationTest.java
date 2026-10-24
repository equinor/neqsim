package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Numerical qualification of SRK/PR Huron-Vidal gas/oil/water equilibria with common hydrate inhibitors.
 *
 * <p>
 * These synthetic fixtures check balance, equilibrium and repeatability, not experimental parameter accuracy. Hydrate
 * tests establish the inhibitor-depression trend for the aqueous/hydrate calculation; ice equilibrium is not enabled
 * and no measured hydrate-temperature accuracy is claimed.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HuronVidalMultiphaseQualificationTest extends neqsim.NeqSimTest {
  /**
   * Exercises three phases, a changed state and return to the initial state for both cubic equations of state.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param inhibitor inhibitor component or {@code none}
   */
  @ParameterizedTest
  @CsvSource({"false,none", "false,methanol", "false,MEG", "true,none", "true,methanol", "true,MEG"})
  void gasOilWaterAndInhibitorsRemainBalancedAndEquilibrated(boolean pengRobinson, String inhibitor) {
    SystemInterface fluid = fluid(pengRobinson, inhibitor, true);
    flashAndCheck(fluid);
    SystemInterface reference = fluid.clone();

    flashAndCheck(fluid);
    assertSameEquilibrium(reference, fluid);

    fluid.setTemperature(300.15);
    fluid.setPressure(59.5);
    flashAndCheck(fluid);
    SystemInterface fresh = fluid(pengRobinson, inhibitor, true);
    fresh.setTemperature(300.15);
    fresh.setPressure(59.5);
    flashAndCheck(fresh);
    assertSameEquilibrium(fresh, fluid);

    fluid.setTemperature(298.15);
    fluid.setPressure(60.0);
    flashAndCheck(fluid);
    assertSameEquilibrium(reference, fluid);
  }

  /**
   * Checks the sign of the inhibitor effect without treating it as an experimental hydrate benchmark.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @throws Exception if the hydrate-temperature operation fails
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void methanolAndMegDepressHydrateFormationTemperature(boolean pengRobinson) throws Exception {
    double uninhibited = hydrateTemperature(pengRobinson, "none");
    assertTrue(uninhibited > 275.0 && uninhibited < 290.0);
    for (String inhibitor : new String[] {"methanol", "MEG"}) {
      double inhibited = hydrateTemperature(pengRobinson, inhibitor);
      assertTrue(Double.isFinite(inhibited) && inhibited > 250.0 && inhibited < uninhibited - 1.0,
          inhibitor + " must lower the calculated hydrate temperature: " + inhibited + " versus " + uninhibited);
    }
  }

  /**
   * Builds a public synthetic fluid with the temperature-dependent HV mixing rule.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param inhibitor inhibitor component or {@code none}
   * @param oil whether to include a separate hydrocarbon liquid
   * @return configured fluid at 298.15 K and 60 bara
   */
  private static SystemInterface fluid(boolean pengRobinson, String inhibitor, boolean oil) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(298.15, 60.0) : new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", oil ? 0.65 : 0.85);
    if (oil) {
      fluid.addComponent("n-decane", 0.20);
    }
    fluid.addComponent("water", 0.15);
    if (!"none".equals(inhibitor)) {
      fluid.addComponent(inhibitor, 0.03);
    }
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Computes an aqueous/hydrate formation temperature with the public operation.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param inhibitor inhibitor component or {@code none}
   * @return calculated temperature in K
   * @throws Exception if the hydrate-temperature operation fails
   */
  private static double hydrateTemperature(boolean pengRobinson, String inhibitor) throws Exception {
    SystemInterface fluid = fluid(pengRobinson, inhibitor, false);
    fluid.setTemperature(285.0);
    fluid.setHydrateCheck(true);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    return fluid.getTemperature();
  }

  /**
   * Verifies component balance and chemical-potential equality across all three phases.
   *
   * @param fluid fluid to flash and inspect
   */
  private static void flashAndCheck(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertEquals(3, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.OIL));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(Double.isFinite(fluid.getEnthalpy()));
    assertTrue(Double.isFinite(fluid.getCp()) && fluid.getCp() > 0.0);

    double betaTotal = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double beta = fluid.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0);
      betaTotal += beta;
      double compositionTotal = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(fraction) && fraction >= 0.0 && fraction <= 1.0);
        compositionTotal += fraction;
      }
      assertEquals(1.0, compositionTotal, 1.0e-12);
    }
    assertEquals(1.0, betaTotal, 1.0e-12);

    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      double reference = logFugacity(fluid, 0, component);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
        assertEquals(reference, logFugacity(fluid, phase, component), 1.0e-8,
            "interphase log fugacity for component " + component);
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), recovered, 1.0e-10,
          "component material balance for " + component);
    }
  }

  /**
   * Returns the component log fugacity without the common pressure factor.
   *
   * @param fluid equilibrated fluid
   * @param phase phase index
   * @param component component index
   * @return log mole fraction plus log fugacity coefficient
   */
  private static double logFugacity(SystemInterface fluid, int phase, int component) {
    double fraction = fluid.getPhase(phase).getComponent(component).getx();
    double coefficient = fluid.getPhase(phase).getComponent(component).getFugacityCoefficient();
    assertTrue(Double.isFinite(coefficient) && coefficient > 0.0);
    assertTrue(fraction > 0.0);
    return Math.log(fraction) + Math.log(coefficient);
  }

  /**
   * Compares full phase splits after repeated and changed-state calculations.
   *
   * @param expected independently retained equilibrium
   * @param actual equilibrium to compare
   */
  private static void assertSameEquilibrium(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    for (int phase = 0; phase < expected.getNumberOfPhases(); phase++) {
      PhaseType type = expected.getPhase(phase).getType();
      assertEquals(expected.getPhase(type).getBeta(), actual.getPhase(type).getBeta(), 1.0e-10,
          "phase fraction for " + type);
      for (int component = 0; component < expected.getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(type).getComponent(component).getx(),
            actual.getPhase(type).getComponent(component).getx(), 1.0e-10,
            "phase composition for " + type + " component " + component);
      }
    }
  }
}
