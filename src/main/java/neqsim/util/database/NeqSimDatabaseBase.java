package neqsim.util.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.logging.log4j.Logger;

/**
 * Shared JDBC lifecycle support for NeqSim database wrappers.
 *
 * <p>
 * Subclasses provide the concrete connection strategy by implementing {@link #openConnection()} and the class logger
 * via {@link #getLogger()}.
 * </p>
 *
 * @author asmf
 * @version June 2026
 */
public abstract class NeqSimDatabaseBase
    implements neqsim.util.util.FileSystemSettings, java.io.Serializable, AutoCloseable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Active SQL statement for this database wrapper instance. */
  private transient Statement statement = null;

  /** Active JDBC connection for this database wrapper instance. */
  protected transient Connection databaseConnection = null;

  /**
   * Opens a connection using the concrete subclass configuration.
   *
   * @return active database connection
   * @throws SQLException if connection creation fails
   * @throws ClassNotFoundException if required JDBC driver is not available
   */
  public abstract Connection openConnection() throws SQLException, ClassNotFoundException;

  /**
   * Returns the logger associated with the concrete database class.
   *
   * @return subclass logger
   */
  protected abstract Logger getLogger();

  /**
   * Initializes connection and statement eagerly.
   *
   * @throws RuntimeException if initialization fails
   */
  protected final void initializeDatabaseConnection() {
    try {
      ensureConnection();
    } catch (Exception ex) {
      getLogger().error("SQLException ", ex);
      throw new RuntimeException(ex);
    }
  }

  /**
   * Ensures this wrapper has an open connection and statement.
   *
   * @throws SQLException if statement creation fails
   * @throws ClassNotFoundException if the JDBC driver cannot be loaded
   */
  protected final void ensureConnection() throws SQLException, ClassNotFoundException {
    if (databaseConnection == null) {
      Connection connection = openConnection();
      try {
        Statement newStatement = connection.createStatement();
        databaseConnection = connection;
        statement = newStatement;
      } catch (SQLException | RuntimeException ex) {
        try {
          connection.close();
        } catch (SQLException closeFailure) {
          ex.addSuppressed(closeFailure);
        }
        throw ex;
      }
    } else if (statement == null || statement.isClosed()) {
      statement = databaseConnection.createStatement();
    }
  }

  /**
   * Gets the active JDBC connection.
   *
   * @return current connection instance
   */
  public Connection getConnection() {
    return databaseConnection;
  }

  /**
   * Gets the active SQL statement.
   *
   * @return current statement
   */
  public Statement getStatement() {
    return statement;
  }

  /**
   * Sets the active SQL statement.
   *
   * @param statement statement instance
   */
  public void setStatement(Statement statement) {
    this.statement = statement;
  }

  /**
   * Executes an SQL statement.
   *
   * @param sqlString SQL statement
   * @return true if execution produces a result set
   * @throws RuntimeException if SQL execution fails
   */
  protected final boolean executeSql(String sqlString) {
    try {
      ensureConnection();
      return getStatement().execute(sqlString);
    } catch (Exception ex) {
      getLogger().error("error in database execution", ex);
      getLogger().error("The database must be registered on the local DBMS to work.");
      throw new RuntimeException(ex);
    }
  }

  /**
   * Executes an SQL query and returns a result set.
   *
   * @param sqlString SQL query
   * @return query result set
   * @throws RuntimeException if query execution fails
   */
  public ResultSet getResultSet(String sqlString) {
    try {
      ensureConnection();
      return getStatement().executeQuery(sqlString);
    } catch (Exception ex) {
      getLogger().error("error loading database", ex);
      throw new RuntimeException(ex);
    }
  }

  /**
   * Closes statement and connection resources.
   *
   * @throws SQLException if close operation fails
   */
  @Override
  public void close() throws SQLException {
    Statement statementToClose = statement;
    Connection connectionToClose = databaseConnection;
    statement = null;
    databaseConnection = null;
    SQLException failure = null;
    try {
      if (statementToClose != null) {
        statementToClose.close();
      }
    } catch (SQLException ex) {
      failure = ex;
    }
    try {
      if (connectionToClose != null) {
        connectionToClose.close();
      }
    } catch (SQLException ex) {
      if (failure == null) {
        failure = ex;
      } else if (failure != ex) {
        failure.addSuppressed(ex);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
