package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Neutral DEA identity must agree across supported database modes.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DeaIdentityDataTest {
  /**
   * Both database modes must supply the sourced neutral identity and consistent mass conversion.
   *
   * @throws Exception if the bundled database cannot be queried
   */
  @Test
  void identityAndMassConversionAreConsistent() throws Exception {
    try {
      for (boolean extended : new boolean[] {false, true}) {
        NeqSimDataBase.useExtendedComponentDatabase(extended);
        try (NeqSimDataBase database = new NeqSimDataBase();
            ResultSet row = database.getResultSet("SELECT * FROM comp WHERE name='DEA'")) {
          row.next();
          assertEquals("111-42-2", row.getString("CASnumber"));
          assertEquals("C4H11NO2", row.getString("FORMULA"));
          assertEquals(105.1356, row.getDouble("MOLARMASS"), 1.0e-8);
        }
        SystemSrkEos fluid = new SystemSrkEos(298.15, 1.01325);
        fluid.addComponent("DEA", 2.0);
        fluid.init(0);
        assertEquals(0.1051356, fluid.getComponent("DEA").getMolarMass(), 1.0e-10);
        assertEquals(0.2102712, fluid.getMass("kg"), 1.0e-10);
      }
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }
}
