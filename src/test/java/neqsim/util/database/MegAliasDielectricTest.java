package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import org.h2.tools.Csv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentSrk;

/**
 * Ensures that MEG aliases use the existing canonical dielectric curve, including its derivatives.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class MegAliasDielectricTest {
  /**
   * Both supported database modes must load the same canonical property for the two aliases.
   *
   * @param extended whether the extended component database is loaded
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aliasesMatchCanonicalMegInBothDatabaseModes(boolean extended) {
    try {
      NeqSimDataBase.useExtendedComponentDatabase(extended);
      ComponentSrk canonical = new ComponentSrk("MEG", 1.0, 1.0, 0);
      for (String name : new String[] {"MEGPVTsim18", "MEGPVTsim19"}) {
        ComponentSrk alias = new ComponentSrk(name, 1.0, 1.0, 0);
        for (double temperature : new double[] {250.0, 273.15, 298.15, 323.15, 400.0}) {
          double expected = -18.46 + 16744.0 / temperature;
          assertEquals(expected, canonical.getDielectricConstant(temperature), 1e-12,
              "The canonical MEG correlation must remain unchanged");
          assertEquals(expected, alias.getDielectricConstant(temperature), 1e-12, name);
          assertEquals(-16744.0 / (temperature * temperature), alias.getDielectricConstantdT(temperature), 1e-14, name);
          assertEquals(33488.0 / Math.pow(temperature, 3.0), alias.getDielectricConstantdTdT(temperature), 1e-15, name);
        }
      }
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }

  /**
   * The legacy fixture must not reintroduce the malformed polynomial when imported directly.
   *
   * @throws Exception if the bundled fixture cannot be read
   */
  @Test
  void historicalFixtureUsesTheSameAliasCoefficients() throws Exception {
    String[] columns = new String[150];
    for (int column = 0; column < columns.length; column++) {
      columns[column] = "COLUMN" + column;
    }
    columns[1] = "NAME";
    double[] expected = {-18.46, 16744.0, 0.0, 0.0, 0.0};
    int aliases = 0;
    InputStream stream = getClass().getClassLoader().getResourceAsStream("neqsim/thermodynamicoperations/COMP.csv");
    assertNotNull(stream);
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        ResultSet rows = new Csv().read(reader, columns)) {
      while (rows.next()) {
        String name = rows.getString("NAME");
        if ("MEGPVTsim18".equals(name) || "MEGPVTsim19".equals(name)) {
          aliases++;
          for (int coefficient = 0; coefficient < expected.length; coefficient++) {
            assertEquals(expected[coefficient], rows.getDouble("COLUMN" + (40 + coefficient)), 0.0, name);
          }
        }
      }
    }
    assertEquals(2, aliases);
  }
}
