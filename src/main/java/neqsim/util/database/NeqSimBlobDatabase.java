package neqsim.util.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NeqSimBlobDatabase class.
 *
 * @author Even Solbraa
 * @version Dec 2018
 */
public class NeqSimBlobDatabase implements neqsim.util.util.FileSystemSettings, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqSimBlobDatabase.class);

  /** Constant <code>dataBasePath=""</code>. */
  public static String dataBasePath = "";
  private static boolean createTemporaryTables = true;

  private static String dataBaseType = "";
  private static String connectionString = "";
  private static String username = "";
  private static String password = "";

  private transient Statement statement = null;
  protected transient Connection databaseConnection = null;

  /**
   * createTemporaryTables.
   *
   * @return the createTemporaryTables
   */
  public boolean createTemporaryTables() {
    return createTemporaryTables;
  }

  /**
   * Setter for the field <code>createTemporaryTables</code>.
   *
   * @param createTemporaryTables the createTemporaryTables to set
   */
  public void setCreateTemporaryTables(boolean createTemporaryTables) {
    NeqSimBlobDatabase.createTemporaryTables = createTemporaryTables;
  }

  /**
   * Constructor for NeqSimBlobDatabase.
   */
  public NeqSimBlobDatabase() {
    setDataBaseType(dataBaseType);

    try {
      databaseConnection = this.openConnection();
      statement = databaseConnection.createStatement();
    } catch (Exception ex) {
      logger.error("SQLException ", ex);
      throw new RuntimeException(ex);
    }
  }

  /**
   * openConnection.
   *
   * @return a Connection object
   * @throws java.sql.SQLException if any.
   * @throws java.lang.ClassNotFoundException if any.
   */
  public Connection openConnection() throws SQLException, ClassNotFoundException {
    javax.naming.InitialContext ctx = null;
    javax.sql.DataSource ds = null;

    try {
      if (System.getenv("NEQSIMBLOBDB_CS") != null) {
        return DriverManager.getConnection(System.getenv("NEQSIMBLOBDB_CS"), System.getenv("MYSQL_USER"),
            System.getenv("MYSQL_PASSWORD"));
      } else if (dataBaseType.equals("MSAccess")) {
        String dir = "";
        if (System.getProperty("NeqSim.home") == null) {
          dir = neqsim.util.util.FileSystemSettings.root + "\\programming\\NeqSimSourceCode\\java\\neqsim";
        } else {
          dir = System.getProperty("NeqSim.home");
        }
        return DriverManager
            .getConnection("jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ=" + dir + "\\data\\NeqSimDatabase");
      } else if (dataBaseType.equals("H2") || dataBaseType.equals("H2RT")) {
        return DriverManager.getConnection(connectionString, "sa", "");
      } else if (dataBaseType.equals("MSAccessUCanAccess")) {
        return DriverManager.getConnection(getConnectionString());
      } else if (dataBaseType.equals("mySQL") || dataBaseType.equals("mySQLNTNU") || dataBaseType.equals("Derby")) {
        return DriverManager.getConnection(getConnectionString(), username, password);
      } else if (dataBaseType.equals("mySQLNeqSimWeb")) {
        ctx = new javax.naming.InitialContext();
        ds = (javax.sql.DataSource) ctx.lookup("java:comp/env/jdbc/NeqsimThermoDatabase");
        return ds.getConnection();
      } else {
        return DriverManager.getConnection(getConnectionString(), username, password);
      }
    } catch (Exception ex) {
      logger.error("error loading NeqSimBlobDatabase... ", ex);
      throw new RuntimeException(ex);
    } finally {
      try {
        if (ctx != null) {
          ctx.close();
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
  }

  /**
   * getConnection.
   *
   * @return a Connection object
   */
  public Connection getConnection() {
    return databaseConnection;
  }

  /**
   * getResultSet.
   *
   * @param sqlString a {@link java.lang.String} object
   * @return a ResultSet object
   */
  public ResultSet getResultSet(String sqlString) {
    try {
      ResultSet result = getStatement().executeQuery(sqlString);
      return result;
    } catch (Exception ex) {
      logger.error("error loading NeqSimBlobDatabase ", ex);
      throw new RuntimeException(ex);
    }
  }

  /**
   * execute.
   *
   * @param sqlString a {@link java.lang.String} object
   */
  public void execute(String sqlString) {
    try {
      if (databaseConnection == null) {
        databaseConnection = this.openConnection();
        setStatement(databaseConnection.createStatement());
      }
      getStatement().execute(sqlString);
    } catch (Exception ex) {
      logger.error("error in NeqSimDataBase ", ex);
      logger.error("The database must be rgistered on the local DBMS to work.");
      throw new RuntimeException(ex);
    }
  }

  /**
   * Getter for the field <code>dataBaseType</code>.
   *
   * @return a {@link java.lang.String} object
   */
  public static String getDataBaseType() {
    return dataBaseType;
  }

  /**
   * Setter for the field <code>dataBaseType</code>.
   *
   * @param aDataBaseType a {@link java.lang.String} object
   */
  public static void setDataBaseType(String aDataBaseType) {
    setDataBaseType(aDataBaseType, null);
  }

  /**
   * Setter for the field <code>dataBaseType</code>.
   *
   * @param aDataBaseType a {@link java.lang.String} object
   * @param connectionString a {@link java.lang.String} object
   */
  public static void setDataBaseType(String aDataBaseType, String connectionString) {
    dataBaseType = aDataBaseType;

    if (connectionString != null) {
      NeqSimBlobDatabase.connectionString = connectionString;
    }

    try {
      if (dataBaseType.equals("mySQL")) {
        Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
      } else {
        Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
      }
    } catch (Exception ex) {
      logger.error("error loading database driver.. ", ex);
      throw new RuntimeException(ex);
    }
  }

  /**
   * Getter for the field <code>statement</code>.
   *
   * @return a Statement object
   */
  public Statement getStatement() {
    return statement;
  }

  /**
   * Setter for the field <code>statement</code>.
   *
   * @param statement a Statement object
   */
  public void setStatement(Statement statement) {
    this.statement = statement;
  }

  /**
   * Setter for the field <code>username</code>.
   *
   * @param aUsername the username to set
   */
  public static void setUsername(String aUsername) {
    username = aUsername;
  }

  /**
   * Setter for the field <code>password</code>.
   *
   * @param aPassword the password to set
   */
  public static void setPassword(String aPassword) {
    password = aPassword;
  }

  /**
   * Getter for the field <code>connectionString</code>.
   *
   * @return the connectionString
   */
  public static String getConnectionString() {
    return connectionString;
  }

  /**
   * Setter for the field <code>connectionString</code>.
   *
   * @param aConnectionString the connectionString to set
   */
  public static void setConnectionString(String aConnectionString) {
    connectionString = aConnectionString;
  }
}
