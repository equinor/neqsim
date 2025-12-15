package neqsim.util.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * AspenIP21Database class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AspenIP21Database
    implements neqsim.util.util.FileSystemSettings, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AspenIP21Database.class);

  protected transient Connection databaseConnection = null;
  private static String dataBaseType = "Karsto";
  private transient Statement statement = null;

  /**
   * <p>
   * Constructor for AspenIP21Database.
   * </p>
   */
  public AspenIP21Database() {
    try {
      if (dataBaseType.equals("Karsto")) {
        Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").getDeclaredConstructor().newInstance();
      }
    } catch (Exception ex) {
      logger.error("error in Online Karsto ", ex);
      logger.error("The database must be registered on the local DBMS to work.");
    }

    try {
      databaseConnection = this.openConnection("Karsto");
      setStatement(databaseConnection.createStatement());
    } catch (Exception ex) {
      logger.error("SQLException ", ex);
    }
  }

  /**
   * <p>
   * openConnection.
   * </p>
   *
   * @param database a {@link java.lang.String} object
   * @return a Connection object
   * @throws java.sql.SQLException if any.
   * @throws java.lang.ClassNotFoundException if any.
   */
  public Connection openConnection(String database) throws SQLException, ClassNotFoundException {
    javax.naming.InitialContext ctx = null;
    try {
      return DriverManager.getConnection(".....");
    } catch (Exception ex) {
      logger.error("SQLException ", ex);
      logger.error("error in Kaarsto DB ", ex);
      logger.error("The Kaarsto database must be registered on the local DBMS to work.");
    } finally {
      try {
        if (ctx != null) {
          ctx.close();
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    return null;
  }

  /**
   * <p>
   * Setter for the field <code>statement</code>.
   * </p>
   *
   * @param statement a Statement object
   */
  public void setStatement(Statement statement) {
    this.statement = statement;
  }

  /**
   * <p>
   * getResultSet.
   * </p>
   *
   * @param sqlString a {@link java.lang.String} object
   * @return a ResultSet object
   */
  public ResultSet getResultSet(String sqlString) {
    return this.getResultSet("Karsto", sqlString);
  }

  /**
   * <p>
   * getResultSet.
   * </p>
   *
   * @param database a {@link java.lang.String} object
   * @param sqlString a {@link java.lang.String} object
   * @return a ResultSet object
   */
  public ResultSet getResultSet(String database, String sqlString) {
    try {
      ResultSet result = getStatement().executeQuery(sqlString);
      return result;
    } catch (Exception ex) {
      logger.error("error in DB ", ex);
      logger.error("The database must be rgistered on the local DBMS to work.");
    }
    return null;
  }

  /**
   * <p>
   * Getter for the field <code>statement</code>.
   * </p>
   *
   * @return a Statement object
   */
  public Statement getStatement() {
    return statement;
  }
}
