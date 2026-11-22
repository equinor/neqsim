package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Zero throughput must retain an intensive state without fabricating active phases or changing composition.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SystemThermoZeroFlowStateTest {
  /** Reproduces issue #4883 without using a valve or PSV. */
  @Test
  void repeatedMolarZeroPreservesSingleGasPhase() {
    SystemInterface gas = new SystemSrkEos(300.0, 5.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    Stream feed = new Stream("FEED", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    gas = feed.getFluid();
    double expectedZ = gas.getZ();
    PhaseInterface expectedPhase = gas.getPhase(0);
    gas.setTotalNumberOfMoles(0.0);
    feed.setFlowRate(0.0, "mole/sec");
    assertEquals(1, gas.getNumberOfPhases());
    assertSame(expectedPhase, gas.getPhase(0));
    assertEquals(1.0, gas.getBeta(0), 1.0e-12);
    assertEquals(expectedZ, gas.getZ(), 1.0e-12);
    assertZeroInventory(gas);
  }

  /**
   * Gas, liquid, condensate and aqueous states retain the established split through repeated zero requests.
   *
   * @param model EOS family
   * @param state feed composition case
   * @param phases expected equilibrium phase count before zeroing
   */
  @ParameterizedTest
  @CsvSource({"SRK, gas, 1", "SRK, liquid, 1", "SRK, mixed, 2", "SRK, aqueous, 3", "PR, gas, 1", "PR, liquid, 1",
      "PR, mixed, 2", "PR, aqueous, 3", "CPA, aqueous, 3"})
  void repeatedZeroRetainsIntensiveState(String model, String state, int phases) {
    SystemInterface fluid = createFluid(model, state);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(phases, fluid.getNumberOfPhases(), "fixture must exercise the intended equilibrium state");
    SystemInterface reference = fluid.clone();
    Stream feed = new Stream("zero boundary", fluid);
    fluid.setTotalNumberOfMoles(0.0);
    for (String unit : new String[] {"mole/sec", "kg/hr", "kg/sec", "Sm3/day", "Am3/hr", "mole/sec"}) {
      PhaseInterface[] activePhases = new PhaseInterface[fluid.getNumberOfPhases()];
      for (int p = 0; p < activePhases.length; p++) {
        activePhases[p] = fluid.getPhase(p);
      }
      feed.setFlowRate(0.0, unit);
      fluid.setTotalFlowRate(0.0, unit);
      for (int p = 0; p < activePhases.length; p++) {
        assertSame(activePhases[p], fluid.getPhase(p));
      }
      assertIntensiveState(reference, fluid);
      assertZeroInventory(fluid);
      SystemInterface initializedZero = fluid.clone();
      initializedZero.initProperties();
      assertFiniteZeroState(initializedZero);
      feed.run();
      fluid = feed.getFluid();
      assertIntensiveState(reference, fluid);
      assertZeroInventory(fluid);
    }
  }

  /**
   * Phase changes belong to the subsequent equilibrium solve at the new P/T, rather than the zero setter.
   *
   * @param model EOS family
   */
  @ParameterizedTest
  @CsvSource({"SRK", "PR", "CPA"})
  void restartAtChangedConditionsReflashesTheSavedComposition(String model) {
    SystemInterface fluid = createFluid(model, "aqueous");
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(3, fluid.getNumberOfPhases());
    SystemInterface reference = fluid.clone();
    reference.setTemperature(600.0);
    reference.setPressure(1.0);
    reference.setTotalNumberOfMoles(7.0);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    assertEquals(1, reference.getNumberOfPhases());
    Stream feed = new Stream("heated restarting feed", fluid);
    feed.setFlowRate(0.0, "kg/hr");
    assertEquals(3, fluid.getNumberOfPhases());
    feed.setTemperature(600.0, "K");
    feed.setPressure(1.0, "bara");
    feed.setFlowRate(reference.getFlowRate("kg/hr"), "kg/hr");
    feed.run();
    assertEquals(7.0, feed.getFluid().getTotalNumberOfMoles(), 1.0e-9);
    assertIntensiveState(reference, feed.getFluid());
  }

  /**
   * Recovery must reproduce a direct positive-flow flash regardless of the original inventory normalization.
   *
   * @param model EOS family
   * @param state feed composition case
   */
  @ParameterizedTest
  @CsvSource({"SRK, gas", "SRK, liquid", "SRK, mixed", "SRK, aqueous", "PR, mixed", "PR, aqueous", "CPA, aqueous"})
  void positiveRecoveryPreservesFeedAndEquilibrium(String model, String state) {
    SystemInterface reference = createFluid(model, state);
    reference.setTotalNumberOfMoles(10.0);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    for (double inventory : new double[] {1.0e-6, 1.0, 1.0e12}) {
      SystemInterface fluid = createFluid(model, state);
      fluid.setTotalNumberOfMoles(inventory);
      new ThermodynamicOperations(fluid).TPflash();
      Stream feed = new Stream("restarting feed", fluid);
      feed.setFlowRate(0.0, "kg/hr");
      feed.setFlowRate(0.0, "mole/sec");
      feed.setFlowRate(10.0, "mole/sec");
      assertEquals(10.0, fluid.getTotalNumberOfMoles(), 1.0e-10);
      double[] fractions = reference.getMolarComposition();
      for (int i = 0; i < fractions.length; i++) {
        assertEquals(10.0 * fractions[i], fluid.getPhase(0).getComponent(i).getNumberOfmoles(), 1.0e-9);
      }
      feed.run();
      fluid = feed.getFluid();
      fluid.initProperties();
      assertIntensiveState(reference, fluid);
    }
  }

  /**
   * Builds synthetic feeds at a common P/T with intentionally unequal component fractions.
   *
   * @param model EOS family
   * @param state composition case
   * @return configured fluid with positive inventory
   */
  private static SystemInterface createFluid(String model, String state) {
    SystemInterface fluid = "PR".equals(model) ? new SystemPrEos(293.15, 20.0)
        : "CPA".equals(model) ? new SystemSrkCPAstatoil(293.15, 20.0) : new SystemSrkEos(293.15, 20.0);
    if ("liquid".equals(state)) {
      fluid.addComponent("n-heptane", 0.8);
      fluid.addComponent("n-decane", 0.2);
    } else {
      fluid.addComponent("methane", 0.7);
      fluid.addComponent("gas".equals(state) ? "nitrogen" : "n-heptane", 0.3);
      if ("aqueous".equals(state)) {
        fluid.addComponent("water", 0.2);
      }
    }
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Checks intensive phase properties and composition independently of the extensive inventory.
   *
   * @param expected reference equilibrium state
   * @param actual state under test
   */
  private static void assertIntensiveState(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(expected.getTemperature(), actual.getTemperature(), 1.0e-12);
    assertEquals(expected.getPressure(), actual.getPressure(), 1.0e-12);
    assertArrayEquals(expected.getMolarComposition(), actual.getMolarComposition(), 1.0e-10);
    double betaSum = 0.0;
    for (int p = 0; p < actual.getNumberOfPhases(); p++) {
      PhaseInterface phase = actual.getPhase(p);
      assertEquals(expected.getPhase(p).getType(), phase.getType());
      assertEquals(expected.getBeta(p), actual.getBeta(p), 1.0e-7);
      assertTrue(Double.isFinite(actual.getBeta(p)) && actual.getBeta(p) >= 0.0);
      assertEquals(expected.getPhase(p).getZ(), phase.getZ(), 1.0e-7);
      assertEquals(expected.getPhase(p).getDensity("kg/m3"), phase.getDensity("kg/m3"), 1.0e-4);
      for (int i = 0; i < actual.getNumberOfComponents(); i++) {
        assertEquals(expected.getPhase(p).getComponent(i).getx(), phase.getComponent(i).getx(), 1.0e-7);
      }
      betaSum += actual.getBeta(p);
    }
    assertEquals(1.0, betaSum, 1.0e-10);
  }

  /**
   * No active or inactive physical slot may retain material at zero throughput.
   *
   * @param fluid zero-throughput system
   */
  private static void assertZeroInventory(SystemInterface fluid) {
    assertEquals(0.0, fluid.getTotalNumberOfMoles(), 0.0);
    assertEquals(0.0, fluid.getFlowRate("kg/hr"), 0.0);
    for (PhaseInterface phase : fluid.getPhases()) {
      if (phase != null) {
        assertEquals(0.0, phase.getNumberOfMolesInPhase(), 0.0);
        for (int i = 0; i < phase.getNumberOfComponents(); i++) {
          assertEquals(0.0, phase.getComponent(i).getNumberOfmoles(), 0.0);
          assertEquals(0.0, phase.getComponent(i).getNumberOfMolesInPhase(), 0.0);
        }
      }
    }
  }

  /**
   * Explicit zero-inventory property initialization must still leave a finite normalized state.
   *
   * @param fluid initialized zero-throughput system
   */
  private static void assertFiniteZeroState(SystemInterface fluid) {
    double betaSum = 0.0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      assertTrue(Double.isFinite(fluid.getBeta(p)) && fluid.getBeta(p) >= 0.0);
      assertTrue(Double.isFinite(fluid.getPhase(p).getZ()));
      assertTrue(Double.isFinite(fluid.getPhase(p).getDensity("kg/m3")));
      betaSum += fluid.getBeta(p);
    }
    assertEquals(1.0, betaSum, 1.0e-10);
    for (double fraction : fluid.getMolarComposition()) {
      assertTrue(Double.isFinite(fraction) && fraction >= 0.0);
    }
    assertZeroInventory(fluid);
  }
}
