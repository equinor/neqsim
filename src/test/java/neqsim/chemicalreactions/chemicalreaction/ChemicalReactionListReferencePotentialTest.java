package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Regression tests for {@link ChemicalReactionList#calcReferencePotentials()}.
 *
 * @author asmf
 * @version 1.0
 */
class ChemicalReactionListReferencePotentialTest {
  /**
   * Checks every reaction against its thermodynamic equilibrium identity.
   *
   * @param list initialized reaction list
   */
  private static void assertReactionIdentities(ChemicalReactionList list) {
    double[] potentials = list.calcReferencePotentials();
    double[][] matrix = list.getReactionGMatrix();
    for (int reaction = 0; reaction < matrix.length; reaction++) {
      double reactionPotential = 0.0;
      for (int species = 0; species < potentials.length; species++) {
        reactionPotential += matrix[reaction][species] * potentials[species];
      }
      assertEquals(matrix[reaction][potentials.length], reactionPotential, 1.0e-8,
          "Reaction " + reaction + " must satisfy sum(nu * mu_ref) = -RT ln(K)");
    }
  }

  /**
   * Builds an initialised electrolyte CPA system with chemical reactions enabled.
   *
   * @param names component names
   * @param moles number of moles of each component
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  private static SystemInterface createSystem(String[] names, double[] moles) {
    SystemInterface system = new SystemElectrolyteCPAstatoil(313.15, 10.0);
    for (int i = 0; i < names.length; i++) {
      system.addComponent(names[i], moles[i]);
    }
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.init(0);
    return system;
  }

  /**
   * Verifies the reference potentials of the CO2-water reaction set.
   */
  @Test
  void co2WaterReferencePotentialsSatisfyEquilibrium() {
    ChemicalReactionList list = createSystem(new String[] {"CO2", "water"}, new double[] {0.1, 10.0})
        .getChemicalReactionOperations().getReactionList();

    assertEquals(3, list.getChemicalReactionList().size());
    assertReactionIdentities(list);
  }

  /**
   * Verifies the reference potentials of the MDEA-CO2-water reaction set.
   */
  @Test
  void mdeaCo2WaterReferencePotentialsSatisfyEquilibrium() {
    ChemicalReactionList list = createSystem(new String[] {"CO2", "water", "MDEA"}, new double[] {0.2, 10.0, 1.0})
        .getChemicalReactionOperations().getReactionList();

    assertEquals(4, list.getChemicalReactionList().size());
    assertReactionIdentities(list);
  }

  /**
   * Verifies the reaction-rate column combines every reaction and follows the supplied component ordering.
   */
  @Test
  void reactionRatesFollowComponentOrderingAndStoichiometry() {
    SystemInterface system = createSystem(new String[] {"CO2", "water"}, new double[] {0.1, 10.0});
    system.initProperties();
    PhaseInterface phase = system.getPhase(0);
    ChemicalReactionList list = system.getChemicalReactionOperations().getReactionList();
    String[] reactiveNames = list.getAllComponents();
    ComponentInterface[] components = new ComponentInterface[reactiveNames.length];
    for (int index = 0; index < reactiveNames.length; index++) {
      components[index] = phase.getComponent(reactiveNames[index]);
    }
    list.createReactionMatrix(phase, components);

    double[] rates = list.calcReationRates(phase, components);
    org.ejml.simple.SimpleMatrix rateColumn = list.calcReacRates(phase, components);
    assertEquals(components.length, rateColumn.numRows());
    assertEquals(1, rateColumn.numCols());
    for (int component = 0; component < components.length; component++) {
      assertEquals(rates[component], rateColumn.get(component, 0), 0.0);
    }

    assertEquals(components.length, rates.length);
    double[][] stoichiometry = list.getReactionMatrix();
    double concentrationFactor = phase.getDensity() / phase.getMolarMass();
    for (int componentIndex = 0; componentIndex < components.length; componentIndex++) {
      double expectedRate = 0.0;
      for (int reactionIndex = 0; reactionIndex < list.getChemicalReactionList().size(); reactionIndex++) {
        ChemicalReaction reaction = list.getReaction(reactionIndex);
        double forward = reaction.getRateFactor();
        double backward = reaction.getK(phase) / reaction.getRateFactor();
        for (int speciesIndex = 0; speciesIndex < components.length; speciesIndex++) {
          double activity = Math.pow(components[speciesIndex].getx() * concentrationFactor,
              Math.abs(stoichiometry[reactionIndex][speciesIndex]));
          if (stoichiometry[reactionIndex][speciesIndex] > 0.0) {
            forward *= activity;
          }
          if (stoichiometry[reactionIndex][speciesIndex] < 0.0) {
            backward *= activity;
          }
        }
        expectedRate += stoichiometry[reactionIndex][componentIndex] * (forward - backward);
      }
      assertEquals(expectedRate, rates[componentIndex], Math.max(1.0e-12, Math.abs(expectedRate) * 1.0e-12));
    }
  }
}
