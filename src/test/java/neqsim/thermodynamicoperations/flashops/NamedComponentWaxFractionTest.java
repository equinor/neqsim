package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Mass-balance and numerical wax-onset regressions for named wax formers in issue #4309.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class NamedComponentWaxFractionTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(NamedComponentWaxFractionTest.class);

  /**
   * Builds the public issue feeds without pseudo-component characterization.
   *
   * @param pressure absolute pressure in bar
   * @param heavy whether to include the heavier nc24 feed
   * @return initialized feed at 320 K
   */
  private static SystemInterface feed(double pressure, boolean heavy) {
    SystemInterface fluid = new SystemSrkEos(320.0, pressure);
    fluid.addComponent("methane", heavy ? 40.0 : 70.0);
    fluid.addComponent("n-heptane", 10.0);
    fluid.addComponent("nc14", 10.0);
    fluid.addComponent("nc20", heavy ? 20.0 : 10.0);
    if (heavy) {
      fluid.addComponent("nc24", 20.0);
    }
    fluid.setMixingRule(2);
    fluid.addSolidComplexPhase("wax");
    fluid.setMultiphaseWaxCheck(true);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    fluid.init(1);
    return fluid;
  }

  /**
   * Checks mass fractions directly against conserved component inventories, without density or volume.
   *
   * @param fluid flashed fluid
   */
  private static void assertMassFractions(SystemInterface fluid) {
    double feedMass = 0.0;
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      feedMass += fluid.getPhase(0).getComponent(component).getNumberOfmoles()
          * fluid.getPhase(0).getComponent(component).getMolarMass();
    }
    double massFractionSum = 0.0;
    double[] componentMoles = new double[fluid.getNumberOfComponents()];
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface state = fluid.getPhase(phase);
      double phaseMass = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double moles = state.getComponent(component).getNumberOfMolesInPhase();
        componentMoles[component] += moles;
        phaseMass += moles * state.getComponent(component).getMolarMass();
      }
      double fraction = fluid.getPhaseFraction(state.getPhaseTypeName(), "mass");
      assertTrue(Double.isFinite(fraction) && fraction >= 0.0 && fraction <= 1.0,
          state.getPhaseTypeName() + " mass fraction: " + fraction);
      assertEquals(phaseMass / feedMass, fraction, 1e-10, state.getPhaseTypeName());
      assertEquals(fluid.getWtFraction(phase), fraction, 1e-12);
      assertEquals(fluid.getBeta(phase), fluid.getPhaseFraction(state.getPhaseTypeName(), "mole"), 0.0);
      massFractionSum += fraction;
    }
    assertEquals(1.0, massFractionSum, 1e-7);
    for (int component = 0; component < componentMoles.length; component++) {
      assertEquals(fluid.getPhase(0).getComponent(component).getNumberOfmoles(), componentMoles[component], 1e-6);
    }
  }

  /**
   * Verifies the cold flash and phase ordering independently of the wax-density model.
   *
   * @param pressure absolute pressure in bar
   * @param heavy whether to include nc24
   * @param temperature wax-positive temperature in K
   */
  @ParameterizedTest
  @CsvSource({"1,false,265", "5,false,265", "5,true,270"})
  void coldWaxMassFractionsConserveFeed(double pressure, boolean heavy, double temperature) {
    SystemInterface fluid = feed(pressure, heavy);
    fluid.setTemperature(temperature);
    new ThermodynamicOperations(fluid).TPflash();
    assertTrue(fluid.hasPhaseType("wax"));
    assertMassFractions(fluid);
    // Swap logical phase positions while keeping the physical phase inventory unchanged.
    int first = fluid.getPhaseIndex(0);
    int last = fluid.getNumberOfPhases() - 1;
    fluid.setPhaseIndex(0, fluid.getPhaseIndex(last));
    fluid.setPhaseIndex(last, first);
    assertMassFractions(fluid);
  }

  /**
   * Verifies direct WAT against independent TP flashes on either side of the onset.
   *
   * @param pressure absolute pressure in bar
   * @param heavy whether to include nc24
   * @throws Exception if the wax appearance operation fails
   */
  @ParameterizedTest
  @CsvSource({"1,false", "5,false", "5,true"})
  void namedWaxFormersHaveReproducibleOnset(double pressure, boolean heavy) throws Exception {
    SystemInterface original = feed(pressure, heavy);
    SystemInterface fluid = original.clone();
    new ThermodynamicOperations(fluid).calcWAT();
    double wat = fluid.getTemperature();
    assertTrue(Double.isFinite(wat) && wat > 265.0 && wat < 350.0);
    assertEquals(pressure, fluid.getPressure(), 0.0);
    assertMassFractions(fluid);
    SystemInterface cold = original.clone();
    cold.setTemperature(wat - 0.01);
    new ThermodynamicOperations(cold).TPflash();
    assertTrue(cold.hasPhaseType("wax"));
    assertTrue(cold.getPhaseFraction("wax", "mass") > 1e-8);
    assertMassFractions(cold);
    SystemInterface warm = original.clone();
    warm.setTemperature(wat + 0.01);
    new ThermodynamicOperations(warm).TPflash();
    assertFalse(warm.hasPhaseType("wax") && warm.getPhaseFraction("wax", "mass") > 1e-8);
    assertMassFractions(warm);
    logger.info("Named-component WAT: P={} bara, heavy={}, T={} K", pressure, heavy, wat);
  }

  /** Verifies that the same inventory definition applies to gas, oil and aqueous phases. */
  @Test
  void fluidPhaseMassFractionsUseTheSameInventoryDefinition() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 50.0);
    fluid.addComponent("n-heptane", 30.0);
    fluid.addComponent("water", 20.0);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(3, fluid.getNumberOfPhases());
    assertMassFractions(fluid);
  }
}
