package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

/**
 * Tests the shared JDBC lifecycle using real in-memory H2 connections.
 *
 * @author asmf
 * @version 1.0
 */
class NeqSimDatabaseBaseTest {
  private static final Logger logger = LogManager.getLogger(NeqSimDatabaseBaseTest.class);

  /** Tests lazy connection, SQL execution, close, and reconnect behavior. */
  @Test
  void lazilyConnectsExecutesAndReopensAfterClose() throws Exception {
    TestDatabase database = new TestDatabase(false);
    assertNull(database.getConnection());
    assertNull(database.getStatement());

    assertFalse(database.execute("CREATE TABLE sample (result_value INTEGER)"));
    Connection firstConnection = database.getConnection();
    Statement firstStatement = database.getStatement();
    assertNotNull(firstConnection);
    assertNotNull(firstStatement);
    database.execute("INSERT INTO sample VALUES (42)");

    try (ResultSet result = database.getResultSet("SELECT result_value FROM sample")) {
      assertTrue(result.next());
      assertEquals(42, result.getInt("result_value"));
      assertFalse(result.next());
    }

    database.close();
    assertTrue(firstStatement.isClosed());
    assertTrue(firstConnection.isClosed());
    assertNull(database.getConnection());
    assertNull(database.getStatement());

    try (ResultSet result = database.getResultSet("SELECT 7")) {
      assertTrue(result.next());
      assertEquals(7, result.getInt(1));
    }
    assertNotSame(firstConnection, database.getConnection());
    database.close();
  }

  /** Tests eager initialization and idempotent close. */
  @Test
  void eagerlyInitializesAndClosesEmptyWrapper() throws Exception {
    TestDatabase database = new TestDatabase(true);
    assertNotNull(database.getConnection());
    assertNotNull(database.getStatement());

    database.close();
    database.close();
    assertNull(database.getConnection());
    assertNull(database.getStatement());
  }

  /** Tests SQL failures are wrapped and retain the JDBC cause. */
  @Test
  void wrapsExecutionFailureWithCause() throws Exception {
    TestDatabase database = new TestDatabase(false);
    RuntimeException exception = assertThrows(RuntimeException.class, () -> database.execute("INVALID SQL"));
    assertTrue(exception.getCause() instanceof SQLException);
    database.close();
  }

  /** Concrete test wrapper that supplies an embedded H2 connection. */
  private static class TestDatabase extends NeqSimDatabaseBase {
    private static final long serialVersionUID = 1L;
    private final String connectionUrl;

    /** Creates a uniquely named H2 database. */
    TestDatabase(boolean initialize) {
      connectionUrl = "jdbc:h2:mem:database_base_" + UUID.randomUUID().toString().replace("-", "")
          + ";DB_CLOSE_DELAY=-1";
      if (initialize) {
        initializeDatabaseConnection();
      }
    }

    /** {@inheritDoc} */
    @Override
    public Connection openConnection() throws SQLException {
      return DriverManager.getConnection(connectionUrl, "sa", "");
    }

    /** {@inheritDoc} */
    @Override
    protected Logger getLogger() {
      return logger;
    }
  }
}