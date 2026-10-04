package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * Tests initialization and refresh of the contract specification database.
 *
 * @author asmf
 * @version 1.0
 */
public class NeqSimContractDataBaseTest {
  /** Verifies that initialization loads the bundled Norway GCV specification. */
  @Test
  void testInitH2DatabaseFromCSVfiles() throws SQLException {
    NeqSimContractDataBase.initH2DatabaseFromCSVfiles();
    try (NeqSimContractDataBase database = new NeqSimContractDataBase();
        ResultSet result = database.getResultSet("SELECT SPECIFICATION, MINVALUE, MAXVALUE, UNIT "
            + "FROM GASCONTRACTSPECIFICATIONS WHERE NAME='Contract1' AND SPECIFICATION='GCV'")) {
      assertTrue(result.next(), "The contract GCV specification should be loaded from the CSV resource");
      assertEquals("GCV", result.getString("SPECIFICATION"));
      assertEquals(30000.0, result.getDouble("MINVALUE"), 1.0e-9);
      assertEquals(40000.0, result.getDouble("MAXVALUE"), 1.0e-9);
      assertEquals("kJ/Sm^3", result.getString("UNIT"));
      assertFalse(result.next(), "The query should match exactly one contract specification");
    }
  }

  /** Verifies that updating the table reloads its bundled ANP CO2 specification. */
  @Test
  void testUpdateTable() throws SQLException {
    NeqSimContractDataBase.updateTable("GASCONTRACTSPECIFICATIONS");
    try (NeqSimContractDataBase database = new NeqSimContractDataBase();
        ResultSet result = database.getResultSet("SELECT SPECIFICATION, MAXVALUE, UNIT "
            + "FROM GASCONTRACTSPECIFICATIONS WHERE NAME='ANP' AND TERMINAL='central' AND SPECIFICATION='CO2'")) {
      assertTrue(result.next(), "The ANP CO2 specification should be present after the table refresh");
      assertEquals("CO2", result.getString("SPECIFICATION"));
      assertEquals(3.0, result.getDouble("MAXVALUE"), 1.0e-9);
      assertEquals("mol%", result.getString("UNIT"));
      assertFalse(result.next(), "The query should match exactly one ANP CO2 specification");
    }
  }
}
