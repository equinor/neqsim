package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Ownership and configuration preservation for reactive system copies.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ChemicalReactionCopyTest extends neqsim.NeqSimTest {
  /** Customized equilibrium and kinetic parameters must survive cloning and remain independently mutable. */
  @Test
  void cloneRetainsConfiguredReactionParametersWithoutSharingArrays() {
    SystemInterface original = configuredWater();
    ChemicalReaction configured = waterReaction(original);
    SystemInterface copy = original.clone();
    ChemicalReaction copied = waterReaction(copy);
    assertNotSame(configured, copied);
    assertArrayEquals(configured.getEquilibriumConstantCoefficients(), copied.getEquilibriumConstantCoefficients());
    assertEquals(configured.getKineticRateLaw(), copied.getKineticRateLaw());
    assertEquals(configured.getRateFactor(), copied.getRateFactor(), 0.0);
    assertEquals(configured.getActivationEnergy(), copied.getActivationEnergy(), 0.0);
    assertEquals(configured.getReferenceTemperature(), copied.getReferenceTemperature(), 0.0);
    assertEquals(configured.getK(original.getPhase(0)), copied.getK(copy.getPhase(0)), 0.0);
    assertEquals(configured.getRateFactor(original.getPhase(0)), copied.getRateFactor(copy.getPhase(0)), 0.0);
    double firstCoefficient = configured.getK()[0];
    copied.getK()[0] += 1.0;
    assertEquals(firstCoefficient, configured.getK()[0], 0.0);
    String firstName = configured.getNames()[0];
    copied.getNames()[0] = "changed only in the copy";
    assertEquals(firstName, configured.getNames()[0]);
    assertNotSame(original.getChemicalReactionOperations(), copy.getChemicalReactionOperations());
  }

  /** A copy of a fixed-species trial must retain the disabled reaction flag and exact registered inventory. */
  @Test
  void frozenSpeciesCloneDoesNotReactivateChemistryOrSeedSpecies() {
    SystemInterface original = configuredWater();
    original.isChemicalSystem(false);
    SystemInterface copy = original.clone();
    assertFalse(copy.isChemicalSystem());
    assertEquals(original.getNumberOfComponents(), copy.getNumberOfComponents());
    assertArrayEquals(original.getComponentNames(), copy.getComponentNames());
    for (int component = 0; component < original.getNumberOfComponents(); component++) {
      assertEquals(original.getComponent(component).getNumberOfmoles(), copy.getComponent(component).getNumberOfmoles(),
          0.0);
    }
  }

  /** Restricting an initialized reaction network must survive a copy even when the database offers other reactions. */
  @Test
  void cloneDoesNotRediscoverRemovedReactions() {
    SystemInterface original = configuredWater();
    original.addComponent("CO2", 0.01);
    original.chemicalReactionInit();
    original.getChemicalReactionOperations().getReactionList().getChemicalReactionList()
        .removeIf(reaction -> !"waterreac".equals(reaction.getName()));
    original.getChemicalReactionOperations().reinitialize();
    SystemInterface copy = original.clone();
    assertEquals(1, copy.getChemicalReactionOperations().getReactionList().getChemicalReactionList().size());
    assertEquals("waterreac",
        copy.getChemicalReactionOperations().getReactionList().getChemicalReactionList().get(0).getName());
    assertArrayEquals(original.getComponentNames(), copy.getComponentNames());
    for (int component = 0; component < original.getNumberOfComponents(); component++) {
      assertEquals(original.getComponent(component).getNumberOfmoles(), copy.getComponent(component).getNumberOfmoles(),
          0.0);
    }
  }

  /** Coupling and phase extraction must retain customized equilibrium constants and their kinetic metadata. */
  @Test
  void equilibriumAndExtractedAqueousPhaseRetainConfiguredReactions() {
    SystemInterface original = configuredWater();
    double[] coefficients = waterReaction(original).getEquilibriumConstantCoefficients();
    new ThermodynamicOperations(original).TPflash();
    assertTrue(original.hasPhaseType("aqueous"));
    assertArrayEquals(coefficients, waterReaction(original).getEquilibriumConstantCoefficients());
    assertTrue(original.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
    SystemInterface aqueous = original.phaseToSystem("aqueous");
    assertArrayEquals(coefficients, waterReaction(aqueous).getEquilibriumConstantCoefficients());
    assertEquals(0.123, waterReaction(aqueous).getRateFactor(), 0.0);
    new ThermodynamicOperations(aqueous).TPflash();
    assertArrayEquals(coefficients, waterReaction(aqueous).getEquilibriumConstantCoefficients());
    assertTrue(aqueous.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 2.0e-6);
    assertArrayEquals(coefficients, waterReaction(original).getEquilibriumConstantCoefficients());
  }

  /**
   * Creates a molecular feed with explicitly customized water dissociation parameters.
   *
   * @return configured reactive feed
   */
  private static SystemInterface configuredWater() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 10.0);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("methane", 0.1);
    fluid.chemicalReactionInit();
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    ChemicalReaction water = waterReaction(fluid);
    water.setK(0, water.getK()[0] + 0.4);
    water.setReferenceKinetics(0.123, 50200.0, 310.0);
    return fluid;
  }

  /**
   * Returns the registered water autoionization reaction.
   *
   * @param fluid configured fluid
   * @return water dissociation reaction
   */
  private static ChemicalReaction waterReaction(SystemInterface fluid) {
    for (ChemicalReaction reaction : fluid.getChemicalReactionOperations().getReactionList()
        .getChemicalReactionList()) {
      if ("waterreac".equals(reaction.getName())) {
        return reaction;
      }
    }
    throw new AssertionError("Water reaction missing");
  }
}
