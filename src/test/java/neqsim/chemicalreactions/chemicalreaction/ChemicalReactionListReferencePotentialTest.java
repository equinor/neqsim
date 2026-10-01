package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Regression tests for {@link ChemicalReactionList#calcReferencePotentials()}.
 *
 * <p>
 * The expected values were captured from the implementation that used {@code Jama.Matrix} directly, before it was
 * migrated to {@code LinearAlgebraOperations}. Both paths run the same JAMA SVD rank and LU solve, so the values must
 * match exactly.
 * </p>
 *
 * @author asmf
 * @version 1.0
 */
class ChemicalReactionListReferencePotentialTest {
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
  void co2WaterReferencePotentialsAreUnchanged() {
    ChemicalReactionList list = createSystem(new String[] {"CO2", "water"}, new double[] {0.1, 10.0})
        .getChemicalReactionOperations().getReactionList();

    double[] expected = {-141690.53028244557, 89725.39409866939, 77364.71634592868, 204172.1437028202,
        -128651.62787303378, -153372.98337851517};

    assertEquals(3, list.getChemicalReactionList().size());
    assertArrayEquals(expected, list.calcReferencePotentials(), 0.0);
  }

  /**
   * Verifies the reference potentials of the MDEA-CO2-water reaction set.
   */
  @Test
  void mdeaCo2WaterReferencePotentialsAreUnchanged() {
    ChemicalReactionList list = createSystem(new String[] {"CO2", "water", "MDEA"}, new double[] {0.2, 10.0, 1.0})
        .getChemicalReactionOperations().getReactionList();

    double[] expected = {-141690.53028244557, 89725.39409866939, 47604.7840179418, 77364.71634592868, 204172.1437028202,
        -128651.62787303378, -153372.98337851517, 119930.92354136503};

    assertEquals(4, list.getChemicalReactionList().size());
    assertArrayEquals(expected, list.calcReferencePotentials(), 0.0);
  }
}
