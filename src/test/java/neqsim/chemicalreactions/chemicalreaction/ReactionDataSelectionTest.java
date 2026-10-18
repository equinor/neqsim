package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import neqsim.util.database.NeqSimDataBase;

/**
 * Regressions for the enabled reaction-data selection contract.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ReactionDataSelectionTest {
  /**
   * Every public name must select the same enabled coefficients as the reaction list.
   *
   * @throws Exception if the bundled database cannot be queried
   */
  @Test
  void factoryUsesEnabledCoefficients() throws Exception {
    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet rows = database.getResultSet("SELECT * FROM reactiondata WHERE usereaction=1")) {
      while (rows.next()) {
        double[] expected = {rows.getDouble("K1"), rows.getDouble("K2"), rows.getDouble("K3"), rows.getDouble("K4")};
        assertArrayEquals(expected, ChemicalReactionFactory.getChemicalReaction(rows.getString("name")).getK(), 1.0e-12,
            rows.getString("name"));
      }
    }
  }

  /** Name enumeration must not include superseded fits or duplicate entries. */
  @Test
  void namesAreUniqueAndEnabled() {
    String[] names = ChemicalReactionFactory.getChemicalReactionNames();
    assertEquals(names.length, new HashSet<String>(Arrays.asList(names)).size());
  }
}
