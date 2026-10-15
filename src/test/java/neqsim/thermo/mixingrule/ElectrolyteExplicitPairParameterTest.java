package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Public pair setters remain effective when flash initialization refreshes calculated defaults.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteExplicitPairParameterTest extends neqsim.NeqSimTest {
  /**
   * Verifies each explicit coefficient across initialization, cloning and equilibrium.
   *
   * @param coefficient temperature-polynomial coefficient index
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void individualSetterSurvivesInitializationAndFlash(int coefficient) {
    SystemInterface fluid = brine();
    double expected = coefficient == 0 ? 0.0 : coefficient == 1 ? 2.34e-3 : 3.45e-5;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      ElectrolyteMixingRulesInterface rule = rule(phase);
      int gas = phase.getComponent("methane").getComponentNumber();
      int ion = phase.getComponent("Na+").getComponentNumber();
      if (coefficient == 0) {
        rule.setWijParameter(gas, ion, expected);
      } else if (coefficient == 1) {
        rule.setWijT1Parameter(gas, ion, expected);
      } else {
        rule.setWijT2Parameter(gas, ion, expected);
      }
    }
    for (int repeat = 0; repeat < 2; repeat++) {
      fluid.init(0);
      assertCoefficient(fluid, coefficient, expected);
    }
    SystemInterface copy = fluid.clone();
    new ThermodynamicOperations(copy).TPflash();
    assertCoefficient(copy, coefficient, expected);
    assertCoefficient(fluid, coefficient, expected);
    PhaseInterface gas = copy.getPhase(PhaseType.GAS);
    PhaseInterface aqueous = copy.getPhase(PhaseType.AQUEOUS);
    double gasFugacity = gas.getComponent("methane").getx() * gas.getComponent("methane").getFugacityCoefficient();
    double aqueousFugacity = aqueous.getComponent("methane").getx()
        * aqueous.getComponent("methane").getFugacityCoefficient();
    assertEquals(0.0, Math.log(aqueousFugacity / gasFugacity), 1e-7);
  }

  /**
   * Checks both orders of the fitted pair in every material phase.
   *
   * @param fluid fluid to inspect
   * @param coefficient temperature-polynomial coefficient index
   * @param expected explicitly supplied coefficient
   */
  private static void assertCoefficient(SystemInterface fluid, int coefficient, double expected) {
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      ElectrolyteMixingRulesInterface rule = rule(phase);
      int gas = phase.getComponent("methane").getComponentNumber();
      int ion = phase.getComponent("Na+").getComponentNumber();
      for (int[] pair : new int[][] {{gas, ion}, {ion, gas}}) {
        double actual = coefficient == 0 ? rule.getWijParameter(pair[0], pair[1])
            : coefficient == 1 ? rule.gettWijT1Parameter(pair[0], pair[1]) : rule.gettWijT2Parameter(pair[0], pair[1]);
        assertEquals(expected, actual, 0.0);
      }
    }
  }

  /**
   * Reads the phase-owned electrolyte mixing rule.
   *
   * @param phase electrolyte phase
   * @return electrolyte mixing rule
   */
  private static ElectrolyteMixingRulesInterface rule(PhaseInterface phase) {
    return ((PhaseModifiedFurstElectrolyteEos) phase).getElectrolyteMixingRule();
  }

  /**
   * Creates an initialized methane/brine pair-parameter fixture.
   *
   * @return fluid with calculated default parameters
   */
  private static SystemInterface brine() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 50.0);
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("Na+", 0.018);
    fluid.addComponent("Cl-", 0.018);
    fluid.setMixingRule(10);
    fluid.init(0);
    return fluid;
  }
}
