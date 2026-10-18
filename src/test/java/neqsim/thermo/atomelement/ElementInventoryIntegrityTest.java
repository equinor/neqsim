package neqsim.thermo.atomelement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/**
 * Chemical identity must give one coefficient for each element in both equilibrium consumers.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ElementInventoryIntegrityTest {
  /** DEA carbamate is formed from DEA plus CO2 minus one proton. */
  @Test
  void deaCarbamateHasConsistentInventory() {
    Element carbamate = new Element("DEACOO-");
    assertEquals(5.0, carbamate.getNumberOfElements("C"));
    assertEquals(10.0, carbamate.getNumberOfElements("H"));
    assertEquals(1.0, carbamate.getNumberOfElements("N"));
    assertEquals(4.0, carbamate.getNumberOfElements("O"));
  }

  /** DEA carbamate formation must conserve C, H, N and O, including hydronium. */
  @Test
  void carbamateReactionConservesElements() {
    String[] species = {"DEA", "CO2", "water", "DEACOO-", "H3O+"};
    double[] stoichiometry = {-1.0, -1.0, -1.0, 1.0, 1.0};
    for (String atom : new String[] {"C", "H", "N", "O"}) {
      double balance = 0.0;
      for (int i = 0; i < species.length; i++) {
        balance += stoichiometry[i] * new Element(species[i]).getNumberOfElements(atom);
      }
      assertEquals(0.0, balance, 1.0e-12, atom);
    }
  }

  /** No species may supply conflicting first-match and last-match coefficients. */
  @Test
  void bundledElementKeysAreUnique() {
    for (String name : new HashSet<String>(Element.getAllElementComponentNames())) {
      String[] elements = new Element(name).getElementNames();
      assertEquals(elements.length, new HashSet<String>(Arrays.asList(elements)).size(), name);
    }
  }
}
