package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
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
}
