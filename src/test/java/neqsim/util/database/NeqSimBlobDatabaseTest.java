package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests the blob database wrapper against an embedded H2 database.
 *
 * @author asmf
 * @version 1.0
 */
class NeqSimBlobDatabaseTest {
  /** Verifies H2 configuration, inherited execution, query, and temporary-table settings. */
  @Test
  void connectsAndExecutesAgainstConfiguredH2Database() throws Exception {
    String previousType = NeqSimBlobDatabase.getDataBaseType();
    String previousConnectionString = NeqSimBlobDatabase.getConnectionString();
    String connectionString = "jdbc:h2:mem:blob_" + UUID.randomUUID().toString().replace("-", "");
    NeqSimBlobDatabase.setDataBaseType("H2", connectionString);

    try (NeqSimBlobDatabase database = new NeqSimBlobDatabase()) {
      assertTrue(database.getConnection().isValid(1));
      assertFalse(database.execute("CREATE TABLE blob_test (payload VARCHAR(20))"));
      database.execute("INSERT INTO blob_test VALUES ('stored')");
      try (ResultSet result = database.getResultSet("SELECT payload FROM blob_test")) {
        assertTrue(result.next());
        assertEquals("stored", result.getString("payload"));
      }

      boolean previousTemporaryTableSetting = database.createTemporaryTables();
      try {
        database.setCreateTemporaryTables(!previousTemporaryTableSetting);
        assertEquals(!previousTemporaryTableSetting, database.createTemporaryTables());
      } finally {
        database.setCreateTemporaryTables(previousTemporaryTableSetting);
      }
    } finally {
      NeqSimBlobDatabase.setDataBaseType(previousType, previousConnectionString);
    }
  }
}