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
 * NeqSimFluidDataBase class.
 * </p>
 *
 * @author esol
 * @version The database is used for storing fluid info and recreating a fluid it uses the database
 *          neqsimfluiddatabase for storing fluid information
 */
public class NeqSimFluidDataBase
    implements neqsim.util.util.FileSystemSettings, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqSimFluidDataBase.class);

  static boolean started = false;
  protected Connection databaseConnection;
  /** Constant <code>useOnlineBase=false</code>. */
  public static boolean useOnlineBase = false;
  static int numb = 0;
  Statement statement = null;

  /**
   * <p>
   * Constructor for NeqSimFluidDataBase.
   * </p>
   */
  public NeqSimFluidDataBase() {
    try {
      if (useOnlineBase) {
        // Class.forName("org.gjt.mm.mysql.Driver");
      } else {
        numb++;
        if (numb == 1) {
          Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
        }
      }
      databaseConnection = this.openConnection("FluidDatabase");
      statement = databaseConnection.createStatement();
    } catch (Exception ex) {
      logger.error("error in FluidDatabase ", ex);
      logger.error("The database must be rgistered on the local DBMS to work.");
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
    if (useOnlineBase) {
      Class.forName("org.gjt.mm.mysql.Driver");
      return DriverManager.getConnection("jdbc:mysql:" + database);
    } else {
      String dir = "";
      if (System.getProperty("NeqSim.home") == null) {
        dir = neqsim.util.util.FileSystemSettings.root + "\\java\\neqsim";
      } else {
        dir = System.getProperty("NeqSim.home");
      }
      return DriverManager.getConnection(
          "jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ=" + dir + "\\data\\" + database);
      // return DriverManager.getConnection("jdbc:odbc:FluidDatabase");
    }
  }

  /**
   * <p>
   * getConnection.
   * </p>
   *
   * @return a Connection object
   */
  public Connection getConnection() {
    return databaseConnection;
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
      ResultSet result = statement.executeQuery(sqlString);
      return result;
    } catch (Exception ex) {
      logger.error("error in FluidDatabase ", ex);
      logger.error("The database must be rgistered on the local DBMS to work.");
    }
    return null;
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
    return this.getResultSet("FluidDatabase", sqlString);
  }

  /**
   * <p>
   * execute.
   * </p>
   *
   * @param sqlString a {@link java.lang.String} object
   */
  public void execute(String sqlString) {
    try {
      if (databaseConnection == null) {
        databaseConnection = this.openConnection("FluidDatabase");
        statement = databaseConnection.createStatement();
      }
      statement.execute(sqlString);
    } catch (Exception ex) {
      logger.error("error in FluidDatabase ", ex);
      logger.error("The database must be rgistered on the local DBMS to work.");
    }
  }
}
