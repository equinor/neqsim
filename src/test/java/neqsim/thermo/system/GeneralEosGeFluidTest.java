package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * General pure-fluid and phase-role coverage for the EOS/GE hybrid architecture.
 *
 * @author NeqSim
 * @version 1.0
 */
class GeneralEosGeFluidTest extends neqsim.NeqSimTest {
  /**
   * Pure fluids must retain a normalized, finite state in the appropriate model-owned role.
   *
   * @param model GE model
   * @param name component name
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param expectedRole expected phase role
   */
  @ParameterizedTest
  @MethodSource("pureCases")
  void pureHybridFluidUsesSupportedRole(String model, String name, double temperature, double pressure,
      PhaseType expectedRole) {
    SystemEosGE system = createSystem(model, temperature, pressure);
    system.addComponent(name, 1.0);
    system.setMixingRule("classic");
    system.enableHybridEosGeFlash();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    assertPureState(system, expectedRole);
    operations.TPflash();
    assertPureState(system, expectedRole);
    SystemEosGE copy = (SystemEosGE) system.clone();
    new ThermodynamicOperations(copy).TPflash();
    assertPureState(copy, expectedRole);
  }

  /**
   * Covers the supported pure-fluid domains of the six liquid model families.
   *
   * @return pure-fluid cases
   */
  private static Stream<Arguments> pureCases() {
    List<Arguments> cases = new ArrayList<Arguments>();
    Object[][] states = {{"methane", 298.15, 10.0, PhaseType.GAS}, {"n-heptane", 298.15, 10.0, PhaseType.OIL},
        {"water", 298.15, 10.0, PhaseType.AQUEOUS}, {"water", 700.0, 10.0, PhaseType.GAS},
        {"methanol", 298.15, 10.0, PhaseType.AQUEOUS}, {"CO2", 280.0, 80.0, PhaseType.OIL},
        {"CO2", 350.0, 10.0, PhaseType.GAS}};
    for (String model : new String[] {"nrtl", "wilson", "unifac", "pitzer", "desmukh", "kent"}) {
      for (Object[] state : states) {
        // Pitzer implements a water solvent, not a pure-methanol liquid standard state.
        if (!"pitzer".equals(model) || !"methanol".equals(state[0])) {
          cases.add(Arguments.of(model, state[0], state[1], state[2], state[3]));
        }
      }
    }
    return cases.stream();
  }

  /**
   * The general hybrid path uses each phase's own fugacity model for a three-phase fluid.
   *
   * @param model GE model
   * @param gas gas-forming component
   * @param pressure pressure in bara
   */
  @ParameterizedTest
  @CsvSource({"nrtl, methane, 50.0", "unifac, methane, 50.0", "pitzer, methane, 50.0", "desmukh, methane, 50.0",
      "kent, methane, 50.0", "wilson, n-butane, 1.0"})
  void multicomponentGasOilWaterUsesSeparateEosAndGeModels(String model, String gas, double pressure) {
    SystemEosGE system = createSystem(model, 298.15, pressure);
    system.addComponent(gas, 5.0);
    system.addComponent("n-heptane", 2.0);
    system.addComponent("water", 55.508);
    system.setMixingRule("classic");
    system.enableHybridEosGeFlash();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    assertMulticomponentRoles(system);
    operations.TPflash();
    assertMulticomponentRoles(system);
    SystemEosGE copy = (SystemEosGE) system.clone();
    new ThermodynamicOperations(copy).TPflash();
    assertMulticomponentRoles(copy);
  }

  /**
   * Checks normalized, converged three-phase results and the ownership of each phase model.
   *
   * @param system flashed hybrid system
   */
  private static void assertMulticomponentRoles(SystemEosGE system) {
    assertEquals(3, system.getNumberOfPhases(), system.getHybridEosGeFlashDiagnostics(1.0e-10));
    assertTrue(system.finishHybridEosGeFlash(1.0e-10), system.getHybridEosGeFlashDiagnostics(1.0e-10));
    for (int index = 0; index < system.getNumberOfPhases(); index++) {
      PhaseInterface phase = system.getPhase(index);
      assertTrue(phase == system.getGeLiquidPhase() || phase == system.getEquationOfStatePhase()
          || phase == system.getEosOilPhase());
      assertEquals(phase == system.getGeLiquidPhase() ? PhaseType.AQUEOUS
          : phase == system.getEosOilPhase() ? PhaseType.OIL : PhaseType.GAS, phase.getType());
    }
  }

  /**
   * Selects a public GE system with its normal EOS carrier.
   *
   * @param model model name
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return unflashed system
   */
  private static SystemEosGE createSystem(String model, double temperature, double pressure) {
    switch (model) {
    case "wilson":
      return new SystemGEWilson(temperature, pressure);
    case "unifac":
      return new SystemUNIFAC(temperature, pressure);
    case "pitzer":
      return new SystemPitzer(temperature, pressure);
    case "desmukh":
      return new SystemDesmukhMather(temperature, pressure);
    case "kent":
      return new SystemKentEisenberg(temperature, pressure);
    default:
      return new SystemNRTL(temperature, pressure);
    }
  }

  /**
   * Audits the pure component and phase fractions and fugacity.
   *
   * @param system flashed system
   * @param expectedRole expected sole phase role
   */
  private static void assertPureState(SystemEosGE system, PhaseType expectedRole) {
    assertEquals(1, system.getNumberOfPhases(), system.getHybridEosGeFlashDiagnostics(1.0e-10));
    PhaseInterface phase = system.getPhase(0);
    assertEquals(expectedRole, phase.getType());
    assertEquals(1.0, phase.getBeta(), 1.0e-12);
    assertEquals(1.0, phase.getComponent(0).getx(), 1.0e-12);
    assertEquals(1.0, phase.getComponent(0).getNumberOfMolesInPhase(), 1.0e-10);
    assertTrue(Double.isFinite(phase.getComponent(0).getFugacityCoefficient()));
    assertTrue(phase.getComponent(0).getFugacityCoefficient() > 0.0);
  }
}
