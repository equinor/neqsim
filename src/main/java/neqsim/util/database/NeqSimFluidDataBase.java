package neqsim.util.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NeqSimFluidDataBase class.
 *
 * @author esol
 * @version The database is used for storing fluid info and recreating a fluid it uses the database FluidDatabase for
 * storing fluid information
 */
public class NeqSimFluidDataBase extends NeqSimDatabaseBase {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqSimFluidDataBase.class);

  static boolean started = false;
  /** Constant <code>useOnlineBase=false</code>. */
  public static boolean useOnlineBase = false;
  static int numb = 0;

  /**
   * Constructor for NeqSimFluidDataBase.
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
      initializeDatabaseConnection();
    } catch (Exception ex) {
      logger.error("error in FluidDatabase ", ex);
      logger.error("The database must be rgistered on the local DBMS to work.");
    }
  }

  /** {@inheritDoc} */
  @Override
  protected Logger getLogger() {
    return logger;
  }

  /** {@inheritDoc} */
  @Override
  public Connection openConnection() throws SQLException, ClassNotFoundException {
    return openConnection("FluidDatabase");
  }

  /**
   * openConnection.
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
      return DriverManager
          .getConnection("jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ=" + dir + "\\data\\" + database);
      // return DriverManager.getConnection("jdbc:odbc:FluidDatabase");
    }
  }
}
