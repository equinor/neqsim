package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemPitzer;

/**
 * Bisulfate identity and aqueous formation references must survive both database loading routes.
 *
 * @author NeqSim
 * @version 1.0
 */
class BisulfateDatabaseTest {
  /**
   * Aqueous reference data must remain available without certifying an ideal-gas formation enthalpy.
   *
   * @throws Exception if the bundled component database cannot be queried
   */
  @Test
  void aqueousReferenceAndCanonicalIdentityArePreservedInBothDatabases() throws Exception {
    try {
      for (boolean extended : new boolean[] {false, true}) {
        NeqSimDataBase.useExtendedComponentDatabase(extended);
        try (NeqSimDataBase database = new NeqSimDataBase();
            ResultSet row = database.getResultSet("SELECT * FROM COMP WHERE NAME='HSO4-'")) {
          assertTrue(row.next(), "Bisulfate must be available in both database modes");
          assertEquals("ion", row.getString("COMPTYPE"));
          assertEquals("HSO4", row.getString("FORMULA"));
          String formationSource = row.getString("FORMATIONENTHALPYSOURCE");
          assertTrue(formationSource == null || formationSource.trim().isEmpty());
          assertEquals(-755876.240287, row.getDouble("GIBBSENERGYOFFORMATION"), 1.0e-6);
          assertEquals(-893142.112599, row.getDouble("ENTHALPYOFFORMATION"), 1.0e-6);
          assertFalse(row.next(), "Bisulfate must have exactly one component record");
        }
        SystemPitzer fluid = new SystemPitzer(298.15, 1.01325);
        fluid.addComponent("water", 55.508);
        fluid.addComponent("Na+", 0.1);
        fluid.addComponent("hso4-", 0.1);
        ComponentInterface bisulfate = fluid.getComponent("HSO4-");
        assertEquals("HSO4-", bisulfate.getComponentName());
        assertEquals(-1.0, bisulfate.getIonicCharge(), 0.0);
        assertEquals(0.097078, bisulfate.getMolarMass(), 1.0e-12);
        assertEquals(1.0, bisulfate.getElements().getNumberOfElements("H"), 0.0);
        assertEquals(1.0, bisulfate.getElements().getNumberOfElements("S"), 0.0);
        assertEquals(4.0, bisulfate.getElements().getNumberOfElements("O"), 0.0);
        assertFalse(bisulfate.hasIdealGasEnthalpyOfFormation());
        assertEquals("", bisulfate.getFormationEnthalpySource());
        assertThrows(IllegalStateException.class, () -> bisulfate.getHID(298.15, true));
      }
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }
}
