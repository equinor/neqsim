package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for separated molecular streams retaining registered but absent ionic species.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class TPflashZeroIonicInventoryTest extends neqsim.NeqSimTest {
  /**
   * Extracting a gas stream must not force an empty aqueous ionic host on subsequent flashes.
   *
   * @param reactive whether the parent brine includes aqueous chemical reactions
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void extractedGasCanBeReflashedAndHeatedWithoutCreatingIons(boolean reactive) {
    SystemInterface feed = fluid(298.15, 1.0, 0.1, 0.001);
    if (reactive) {
      feed.chemicalReactionInit();
      feed.setMixingRule(10);
      assertTrue(AqueousReactionEquilibrium.isApplicable(feed));
    }
    new ThermodynamicOperations(feed).TPflash();
    assertTrue(feed.hasPhaseType(PhaseType.AQUEOUS));
    SystemInterface gas = feed.phaseToSystem("gas");
    assertTrue(gas.hasIons(), "component registration is retained during phase extraction");
    assertFalse(FlashPhaseConstraints.hasIonicInventory(gas));
    assertEquals(reactive, AqueousReactionEquilibrium.isApplicable(gas));
    int registeredComponents = gas.getNumberOfComponents();
    double water = gas.getComponent("water").getNumberOfmoles();
    double methane = gas.getComponent("methane").getNumberOfmoles();
    for (double temperature : new double[] {298.15, 298.15, 320.0}) {
      gas.setTemperature(temperature);
      new ThermodynamicOperations(gas).TPflash();
      assertEquals(1, gas.getNumberOfPhases());
      assertEquals(PhaseType.GAS, gas.getPhase(0).getType());
      assertEquals(registeredComponents, gas.getNumberOfComponents());
      assertEquals(water, gas.getComponent("water").getNumberOfmoles(), water * 1.0e-10);
      assertEquals(methane, gas.getComponent("methane").getNumberOfmoles(), methane * 1.0e-10);
      assertEquals(0.0, gas.getComponent("Na+").getNumberOfmoles(), 0.0);
      assertEquals(0.0, gas.getComponent("Cl-").getNumberOfmoles(), 0.0);
      for (int component = 0; component < gas.getNumberOfComponents(); component++) {
        if (FlashPhaseConstraints.isIon(gas.getComponent(component))) {
          assertEquals(0.0, gas.getComponent(component).getNumberOfmoles(), 0.0);
        }
      }
      assertFalse(FlashPhaseConstraints.hasIonicInventory(gas));
    }
    if (reactive) {
      gas.setTemperature(280.0);
      new ThermodynamicOperations(gas).TPflash();
      assertTrue(gas.hasPhaseType(PhaseType.AQUEOUS), "cooling creates a material aqueous phase");
      assertTrue(gas.isChemicalSystem(), "the separated stream retains its reaction configuration");
      assertTrue(FlashPhaseConstraints.hasIonicInventory(gas), "water dissociation resumes after condensation");
      assertTrue(gas.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() <= 2.0e-6);
      double remainingWater = gas.getComponent("water").getNumberOfmoles();
      double hydroxide = gas.getComponent("OH-").getNumberOfmoles();
      double hydronium = gas.getComponent("H3O+").getNumberOfmoles();
      assertEquals(water, remainingWater + hydroxide + hydronium, water * 1.0e-8,
          "original oxygen inventory after reaction");
      assertEquals(2.0 * water, 2.0 * remainingWater + hydroxide + 3.0 * hydronium, water * 1.0e-8,
          "original water-derived hydrogen inventory after reaction");
      assertEquals(methane, gas.getComponent("methane").getNumberOfmoles(), methane * 1.0e-10);
      assertEquals(0.0, gas.getComponent("Na+").getNumberOfmoles(), 0.0);
      assertEquals(0.0, gas.getComponent("Cl-").getNumberOfmoles(), 0.0);
      assertEquals(registeredComponents, gas.getNumberOfComponents());
    }
  }

  /** A rejected last-aqueous-phase transition must preserve the complete caller material state. */
  @Test
  void unsupportedReactiveAqueousDisappearanceIsTransactional() {
    SystemInterface feed = fluid(298.15, 1.0, 0.1, 0.001);
    feed.chemicalReactionInit();
    feed.setMixingRule(10);
    new ThermodynamicOperations(feed).TPflash();
    SystemInterface gas = feed.phaseToSystem("gas");
    new ThermodynamicOperations(gas).TPflash();
    gas.setTemperature(320.0);
    new ThermodynamicOperations(gas).TPflash();
    gas.setTemperature(280.0);
    new ThermodynamicOperations(gas).TPflash();
    assertTrue(gas.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(FlashPhaseConstraints.hasIonicInventory(gas));
    assertTrue(gas.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);

    gas.setTemperature(320.0);
    SystemInterface before = gas.clone();
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> new ThermodynamicOperations(gas).TPflash());
    assertTrue(failure.getMessage().contains("requires exactly one aqueous material phase"));
    assertEquals(before.getTotalNumberOfMoles(), gas.getTotalNumberOfMoles(), 0.0);
    assertEquals(before.getNumberOfPhases(), gas.getNumberOfPhases());
    assertEquals(before.getNumberOfComponents(), gas.getNumberOfComponents());
    assertTrue(gas.isChemicalSystem());
    for (int phase = 0; phase < before.getNumberOfPhases(); phase++) {
      assertEquals(before.getPhaseType(phase), gas.getPhaseType(phase));
      assertEquals(before.getPhase(phase).getType(), gas.getPhase(phase).getType());
      assertEquals(before.getBeta(phase), gas.getBeta(phase), 0.0);
      assertEquals(before.getPhase(phase).getNumberOfMolesInPhase(), gas.getPhase(phase).getNumberOfMolesInPhase(),
          0.0);
      for (int component = 0; component < before.getNumberOfComponents(); component++) {
        assertEquals(before.getPhase(phase).getComponent(component).getNumberOfmoles(),
            gas.getPhase(phase).getComponent(component).getNumberOfmoles(), 0.0);
        assertEquals(before.getPhase(phase).getComponent(component).getNumberOfMolesInPhase(),
            gas.getPhase(phase).getComponent(component).getNumberOfMolesInPhase(), 0.0);
        assertEquals(before.getPhase(phase).getComponent(component).getx(),
            gas.getPhase(phase).getComponent(component).getx(), 0.0);
      }
    }
    // Every species and phase amount is unchanged, so element inventory and charge are also unchanged.
    gas.setTemperature(280.0);
    new ThermodynamicOperations(gas).TPflash();
    assertTrue(gas.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(gas.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
  }

  /** Zero-amount ionic registrations must leave a neutral gas equilibrium unchanged. */
  @Test
  void zeroIonRegistrationsMatchTheNeutralFluid() {
    SystemInterface reference = fluid(320.0, 0.0001, 1.0, -1.0);
    SystemInterface registered = fluid(320.0, 0.0001, 1.0, 0.0);
    new ThermodynamicOperations(reference).TPflash();
    new ThermodynamicOperations(registered).TPflash();
    assertTrue(registered.hasIons());
    assertFalse(FlashPhaseConstraints.hasIonicInventory(registered));
    assertEquals(reference.getNumberOfPhases(), registered.getNumberOfPhases());
    assertEquals(reference.getPhase(0).getType(), registered.getPhase(0).getType());
    assertEquals(reference.getBeta(0), registered.getBeta(0), 1.0e-12);
    for (String name : new String[] {"methane", "water"}) {
      assertEquals(reference.getPhase(0).getComponent(name).getx(), registered.getPhase(0).getComponent(name).getx(),
          1.0e-12);
      assertEquals(reference.getPhase(0).getComponent(name).getLogFugacityCoefficient(),
          registered.getPhase(0).getComponent(name).getLogFugacityCoefficient(), 1.0e-9);
    }
    assertEquals(0.0, registered.getComponent("Na+").getNumberOfmoles(), 0.0);
    assertEquals(0.0, registered.getComponent("Cl-").getNumberOfmoles(), 0.0);
  }

  /** A positive trace ionic inventory still requires exact conservation in the aqueous phase. */
  @Test
  void positiveTraceIonsRemainConservedAndConfined() {
    double ionMoles = 1.0e-16;
    SystemInterface feed = fluid(298.15, 1.0, 0.1, ionMoles);
    assertTrue(FlashPhaseConstraints.hasIonicInventory(feed));
    new ThermodynamicOperations(feed).TPflash();
    assertTrue(feed.hasPhaseType(PhaseType.AQUEOUS));
    for (String name : new String[] {"Na+", "Cl-"}) {
      assertEquals(ionMoles, feed.getComponent(name).getNumberOfmoles(), ionMoles * 1.0e-10);
      assertEquals(ionMoles, feed.getPhase(PhaseType.AQUEOUS).getComponent(name).getNumberOfMolesInPhase(),
          ionMoles * 1.0e-10);
      assertEquals(0.0, feed.getPhase(PhaseType.GAS).getComponent(name).getNumberOfMolesInPhase(), 0.0);
    }
  }

  /**
   * Creates a reproducible molecular or electrolyte feed with optional ionic component registration.
   *
   * @param temperature temperature in kelvin
   * @param water water inventory in mol
   * @param methane methane inventory in mol
   * @param salt each ionic inventory in mol, or a negative value to omit registration
   * @return unflashed feed at 10 bara
   */
  private static SystemInterface fluid(double temperature, double water, double methane, double salt) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, 10.0);
    fluid.addComponent("water", water);
    fluid.addComponent("methane", methane);
    if (salt >= 0.0) {
      fluid.addComponent("Na+", salt);
      fluid.addComponent("Cl-", salt);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }
}
