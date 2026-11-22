package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

  /**
   * Tests lazy reconnect after close, SQL execution, and repeated resource release.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
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

  /**
   * Tests eager initialization and idempotent close.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
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

  /**
   * Tests SQL failures are wrapped and retain the JDBC cause.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
  @Test
  void wrapsExecutionFailureWithCause() throws Exception {
    TestDatabase database = new TestDatabase(false);
    RuntimeException exception = assertThrows(RuntimeException.class, () -> database.execute("INVALID SQL"));
    assertTrue(exception.getCause() instanceof SQLException);
    database.close();
  }

  /**
   * Repeated eager initialization must not leak the first connection.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
  @Test
  void repeatedInitializationKeepsOwnedResources() throws Exception {
    try (TestDatabase database = new TestDatabase(true)) {
      Connection connection = database.getConnection();
      Statement statement = database.getStatement();
      database.initializeDatabaseConnection();
      assertSame(connection, database.getConnection());
      assertSame(statement, database.getStatement());
    }
  }

  /**
   * A caller-closed statement can be recreated on the existing connection.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
  @Test
  void recreatesClosedStatement() throws Exception {
    try (TestDatabase database = new TestDatabase(true)) {
      Connection connection = database.getConnection();
      database.getStatement().close();
      try (ResultSet result = database.getResultSet("SELECT 11")) {
        assertTrue(result.next());
        assertEquals(11, result.getInt(1));
      }
      assertSame(connection, database.getConnection());
    }
  }

  /**
   * Statement cleanup failure must not prevent connection cleanup.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
  @Test
  void closesConnectionEvenWhenStatementCloseFails() throws Exception {
    TestDatabase database = new TestDatabase(true);
    Connection connection = database.getConnection();
    SQLException failure = new SQLException("statement close");
    database.setStatement((Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
        new Class<?>[] {Statement.class}, (proxy, method, args) -> {
          throw failure;
        }));
    assertSame(failure, assertThrows(SQLException.class, database::close));
    assertTrue(connection.isClosed());
    assertNull(database.getConnection());
    assertNull(database.getStatement());
    database.close();
  }

  /**
   * Failed initialization closes the new connection and allows a clean retry.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
  @Test
  void initializationFailureReleasesConnectionAndPreservesCleanupCause() throws Exception {
    SQLException createFailure = new SQLException("create statement");
    SQLException closeFailure = new SQLException("close connection");
    Connection failingConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
        new Class<?>[] {Connection.class}, (proxy, method, args) -> {
          if ("createStatement".equals(method.getName())) {
            throw createFailure;
          }
          if ("close".equals(method.getName())) {
            throw closeFailure;
          }
          throw new UnsupportedOperationException(method.getName());
        });
    try (SuppliedDatabase database = new SuppliedDatabase(failingConnection)) {
      RuntimeException failure = assertThrows(RuntimeException.class, database::initializeDatabaseConnection);
      assertSame(createFailure, failure.getCause());
      assertEquals(1, createFailure.getSuppressed().length);
      assertSame(closeFailure, createFailure.getSuppressed()[0]);
      assertNull(database.getConnection());
      assertNull(database.getStatement());
      database.connectionToOpen = DriverManager.getConnection("jdbc:h2:mem:retry_4172", "sa", "");
      try (ResultSet result = database.getResultSet("SELECT 13")) {
        assertTrue(result.next());
        assertEquals(13, result.getInt(1));
      }
    }
  }

  /**
   * Both close errors remain observable and repeated close does not repeat failures.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
  @Test
  void preservesBothCloseFailures() throws Exception {
    SQLException statementFailure = new SQLException("statement close");
    SQLException connectionFailure = new SQLException("connection close");
    Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
        new Class<?>[] {Statement.class}, (proxy, method, args) -> {
          throw statementFailure;
        });
    Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
        new Class<?>[] {Connection.class}, (proxy, method, args) -> {
          if ("createStatement".equals(method.getName())) {
            return statement;
          }
          throw connectionFailure;
        });
    SuppliedDatabase database = new SuppliedDatabase(connection);
    database.initializeDatabaseConnection();
    assertSame(statementFailure, assertThrows(SQLException.class, database::close));
    assertEquals(1, statementFailure.getSuppressed().length);
    assertSame(connectionFailure, statementFailure.getSuppressed()[0]);
    assertNull(database.getConnection());
    assertNull(database.getStatement());
    database.close();
  }

  /**
   * Legacy return descriptors must remain binary compatible.
   *
   * @throws Exception if JDBC setup, cleanup, or reflection fails
   */
  @Test
  void preservesConcreteExecuteSignatures() throws Exception {
    assertEquals(boolean.class, NeqSimDataBase.class.getMethod("execute", String.class).getReturnType());
    assertEquals(void.class, NeqSimBlobDatabase.class.getMethod("execute", String.class).getReturnType());
    assertEquals(void.class, NeqSimExperimentDatabase.class.getMethod("execute", String.class).getReturnType());
    assertEquals(void.class, NeqSimFluidDataBase.class.getMethod("execute", String.class).getReturnType());
  }

  /**
   * JDBC fixture supporting deterministic driver failures.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class SuppliedDatabase extends NeqSimDatabaseBase {
    private static final long serialVersionUID = 1L;
    private transient Connection connectionToOpen;

    /**
     * Creates a fixture.
     *
     * @param connection connection returned by the next open
     */
    SuppliedDatabase(Connection connection) {
      connectionToOpen = connection;
    }

    /** {@inheritDoc} */
    @Override
    public Connection openConnection() {
      return connectionToOpen;
    }

    /** {@inheritDoc} */
    @Override
    protected Logger getLogger() {
      return LogManager.getLogger(SuppliedDatabase.class);
    }
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
