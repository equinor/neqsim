package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.GeneralElectrolyteEquilibriumTest.Inventory;
import neqsim.thermodynamicoperations.flashops.saturationops.HydrateFormationTemperatureFlash;

/**
 * Hydrate integration checks for general gas, inhibitor and salt mixtures, using original inventories and the same
 * molecular-equilibrium acceptance as an ordinary TP flash.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
@Tag("slow")
class GeneralElectrolyteHydrateEquilibriumTest {
  /**
   * Checks the reported 44 bara, 10 wt% NaCl and 5 wt% MEG feed from independent temperature guesses.
   *
   * @throws Exception if the hydrate operation fails
   */
  @Test
  void megBrineHasTheSameConservativeEndpointFromIndependentGuesses() throws Exception {
    SystemInterface reference = inhibitedBrine(null, false);
    Inventory original = new Inventory(reference);
    hydrate(reference, original, 273.15);
    SystemInterface warmGuess = inhibitedBrine(null, false);
    hydrate(warmGuess, new Inventory(warmGuess), 283.15);
    assertEquals(reference.getTemperature(), warmGuess.getTemperature(), 2.0e-4);
    GeneralElectrolyteEquilibriumTest.assertEquivalent(reference, warmGuess, 1.0e-6);
    double referenceTemperature = reference.getTemperature();
    SystemInterface copy = reference.clone();
    hydrate(copy, original, referenceTemperature);
    assertEquals(referenceTemperature, copy.getTemperature(), 2.0e-4);
    assertEquals(referenceTemperature, reference.getTemperature(), 0.0);
    GeneralElectrolyteEquilibriumTest.assertEndpoint(reference, original, "unmodified hydrate source");
    GeneralElectrolyteEquilibriumTest.assertEquivalent(reference, copy, 1.0e-6);
  }

  /**
   * Tests continuity when a molecule crosses the former CO2/water/MEG routing boundary.
   *
   * @param trace trace molecular component
   * @throws Exception if the hydrate operation fails
   */
  @ParameterizedTest
  @ValueSource(strings = {"methane", "methanol"})
  void traceMoleculesPreserveHydrateAndFluidEquilibrium(String trace) throws Exception {
    SystemInterface reference = inhibitedBrine(null, false);
    SystemInterface perturbed = inhibitedBrine(trace, false);
    hydrate(reference, new Inventory(reference), 278.15);
    hydrate(perturbed, new Inventory(perturbed), 278.15);
    assertEquals(reference.getTemperature(), perturbed.getTemperature(), 2.0e-4, trace + " trace continuity");
    GeneralElectrolyteEquilibriumTest.assertEquivalent(reference, perturbed, 1.0e-6);
  }

  /**
   * Requires one common acceptance contract for mixed gases, two inhibitors and three cations.
   *
   * @throws Exception if the hydrate operation fails
   */
  @Test
  void mixedGasInhibitorAndSaltFeedUsesTheSameEquilibriumContract() throws Exception {
    SystemInterface fluid = inhibitedBrine(null, true);
    Inventory original = new Inventory(fluid);
    hydrate(fluid, original, 273.15);
    SystemInterface second = inhibitedBrine(null, true);
    hydrate(second, new Inventory(second), 283.15);
    assertEquals(fluid.getTemperature(), second.getTemperature(), 2.0e-4);
    GeneralElectrolyteEquilibriumTest.assertEquivalent(fluid, second, 1.0e-6);
  }

  /**
   * Constructs the mass-basis fixture. NaCl is 10% of water plus salt mass and MEG is 5% of water plus MEG mass; the
   * two percentages therefore have distinct denominators.
   *
   * @param trace optional molecule present at 1e-8 mol
   * @param mixed whether to use a mixed gas, inhibitor and salt feed
   * @return unflashed feed with hydrate checks enabled
   */
  private static SystemInterface inhibitedBrine(String trace, boolean mixed) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, 44.0);
    fluid.addComponent("CO2", mixed ? 8.0 : 10.0);
    fluid.addComponent("water", 1.0 / 0.01801528);
    fluid.addComponent("MEG", (0.05 / 0.95) / 0.06206784);
    if (mixed) {
      fluid.addComponent("methane", 2.0);
      fluid.addComponent("nitrogen", 0.2);
      fluid.addComponent("methanol", 0.5);
      fluid.addComponent("Na+", 1.0);
      fluid.addComponent("K+", 0.2);
      fluid.addComponent("Ca++", 0.1);
      fluid.addComponent("Cl-", 1.4);
    } else {
      double saltMoles = (0.10 / 0.90) / 0.05844277;
      fluid.addComponent("Na+", saltMoles);
      fluid.addComponent("Cl-", saltMoles);
    }
    if (trace != null) {
      fluid.addComponent(trace, 1.0e-8);
    }
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Checks the actual material phases and the incipient hydrate water fugacity after convergence.
   *
   * @param fluid mutable feed
   * @param original original species and element inventory
   * @param initialTemperature hydrate temperature guess in K
   * @throws Exception if the hydrate operation fails
   */
  private static void hydrate(SystemInterface fluid, Inventory original, double initialTemperature) throws Exception {
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.hydrateFormationTemperature(initialTemperature);
    HydrateFormationTemperatureFlash operation = (HydrateFormationTemperatureFlash) operations.getOperation();
    assertTrue(operation.isConverged(), "hydrate convergence status");
    assertTrue(Double.isFinite(fluid.getTemperature()) && fluid.getTemperature() > 0.0, "hydrate temperature");
    GeneralElectrolyteEquilibriumTest.assertEndpoint(fluid, original, "hydrate fluid endpoint");
    double ratio = fluid.getPhase(4).getFugacity("water") / fluid.getPhase("aqueous").getFugacity("water");
    assertEquals(0.0, 1.0 - ratio, 1.0e-6, "hydrate water fugacity residual");
  }
}
