package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests experiment database connection and JDBC operations with H2.
 *
 * @author asmf
 * @version 1.0
 */
class NeqSimExperimentDatabaseTest {
  /** Verifies configured H2 connections, SQL operations, and temporary-table configuration. */
  @Test
  void connectsAndExecutesAgainstConfiguredH2Database() throws Exception {
    String previousType = NeqSimExperimentDatabase.getDataBaseType();
    String previousConnectionString = NeqSimExperimentDatabase.getConnectionString();
    String previousUsername = NeqSimExperimentDatabase.username;
    String previousPassword = NeqSimExperimentDatabase.password;
    String connectionString = "jdbc:h2:mem:experiment_" + UUID.randomUUID().toString().replace("-", "");
    NeqSimExperimentDatabase.setDataBaseType("H2", connectionString);
    NeqSimExperimentDatabase.setUsername("sa");
    NeqSimExperimentDatabase.setPassword("");

    NeqSimExperimentDatabase database = new NeqSimExperimentDatabase();
    try (Connection connection = database.getConnection(); Statement statement = database.getStatement()) {
      assertTrue(database.getConnection().isValid(1));
      database.execute("CREATE TABLE experiment_test (measurement DOUBLE)");
      database.execute("INSERT INTO experiment_test VALUES (12.5)");
      try (ResultSet result = database.getResultSet("SELECT measurement FROM experiment_test")) {
        assertTrue(result.next());
        assertEquals(12.5, result.getDouble("measurement"), 0.0);
      }

      boolean previousTemporaryTableSetting = database.createTemporaryTables();
      try {
        database.setCreateTemporaryTables(!previousTemporaryTableSetting);
        assertEquals(!previousTemporaryTableSetting, database.createTemporaryTables());
      } finally {
        database.setCreateTemporaryTables(previousTemporaryTableSetting);
      }
    } finally {
      try {
        NeqSimExperimentDatabase.setDataBaseType(previousType, previousConnectionString);
      } catch (RuntimeException unavailablePreviousDriver) {
        assertEquals(previousType, NeqSimExperimentDatabase.getDataBaseType());
        assertEquals(previousConnectionString, NeqSimExperimentDatabase.getConnectionString());
      }
      NeqSimExperimentDatabase.setUsername(previousUsername);
      NeqSimExperimentDatabase.setPassword(previousPassword);
    }
  }
}