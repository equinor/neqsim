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
import org.junit.jupiter.api.Test;

/**
 * Tests the component database JDBC lifecycle using real in-memory H2 connections.
 *
 * @author asmf
 * @version 1.0
 */
class NeqSimDatabaseBaseTest {

  /** Tests lazy reconnect after close, SQL execution, and repeated resource release. */
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
  private static class TestDatabase extends NeqSimDataBase {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a database wrapper, optionally closing its eager connection before testing reconnect.
     *
     * @param initialize true to retain the eager connection
     * @throws SQLException if the initial connection cannot be closed
     */
    TestDatabase(boolean initialize) throws SQLException {
      if (!initialize) {
        close();
      }
    }

    /** {@inheritDoc} */
    @Override
    public Connection openConnection() throws SQLException {
      return DriverManager
          .getConnection("jdbc:h2:mem:database_lifecycle_" + UUID.randomUUID().toString().replace("-", ""), "sa", "");
    }

  }
}
