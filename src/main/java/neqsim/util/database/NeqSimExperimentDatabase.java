package neqsim.util.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NeqSimExperimentDatabase class.
 *
 * @author Even Solbraa
 * @version Dec 2018
 */
public class NeqSimExperimentDatabase implements neqsim.util.util.FileSystemSettings, java.io.Serializable {
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
    NeqSimExperimentDatabase.createTemporaryTables = createTemporaryTables;
  }

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqSimExperimentDatabase.class);

  /** Constant <code>dataBasePath=""</code>. */
  public static String dataBasePath = "";

  /** Constant <code>username=""</code> */
  public static String username = "";
  /** Constant <code>password=""</code> */
  public static String password = "";
  private static boolean createTemporaryTables = false;

  private static String dataBaseType = "MSAccessUCanAccess";
  /** Constant <code>connectionString="jdbc:ucanaccess://C:/Users/esol/OneDriv"{trunked}</code>. */
  public static String connectionString = "jdbc:ucanaccess://C:/Users/esol/OneDrive - Equinor/programming/neqsimdatabase/MSAccess/NeqSimExperimentalData.mdb;memory=true";

  private transient Statement statement = null;
  protected transient Connection databaseConnection = null;

  /**
   * Constructor for NeqSimExperimentDatabase.
   */
  public NeqSimExperimentDatabase() {
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
      if (dataBaseType.equals("MSAccessUCanAccess")) {
	return DriverManager.getConnection(getConnectionString());
      } else if (dataBaseType.equals("mySQL") || dataBaseType.equals("mySQLNTNU") || dataBaseType.equals("Derby")) {
	return DriverManager.getConnection(getConnectionString(), username, password);
      } else if (dataBaseType.equals("mySQLNeqSimWeb")) {
	ctx = new javax.naming.InitialContext();
	ds = (javax.sql.DataSource) ctx.lookup("java:comp/env/jdbc/NeqsimDataSource");
	if (ctx != null) {
	  ctx.close();
	}
	return ds.getConnection();
      } else {
	return DriverManager.getConnection(getConnectionString(), username, password);
      }
    } catch (Exception ex) {
      logger.error("error loading NeqSimExperimentDatabase... ", ex);
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
      logger.error("error loading NeqSimExperimentDatabase ", ex);
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
      NeqSimExperimentDatabase.connectionString = connectionString;
    }

    try {
      if (dataBaseType.equals("MSAccess")) {
	Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("H2")) {
	Class.forName("org.h2.Driver");
      } else if (dataBaseType.equals("H2RT")) {
	Class.forName("org.h2.Driver");
      } else if (dataBaseType.equals("MSAccessUCanAccess")) {
	Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
      } else if (dataBaseType.equals("mySQL")) {
	Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("mySQLNTNU")) {
	Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("Derby")) {
	Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("oracle")) {
	Class.forName("oracle.jdbc.driver.OracleDriver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("oracleST")) {
	Class.forName("oracle.jdbc.driver.OracleDriver").getDeclaredConstructor().newInstance();
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
