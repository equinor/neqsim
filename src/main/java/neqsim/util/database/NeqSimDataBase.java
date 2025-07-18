package neqsim.util.database;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.jdbc.JdbcSQLSyntaxErrorException;

/**
 * <p>
 * NeqSimDataBase class.
 * </p>
 *
 * @author Even Solbraa
 * @version Dec 2018
 */
public class NeqSimDataBase
    implements neqsim.util.util.FileSystemSettings, java.io.Serializable, AutoCloseable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqSimDataBase.class);

  /** Constant <code>dataBasePath=""</code>. */
  public static String dataBasePath = "";

  private static boolean createTemporaryTables = false;

  private static String username = "remote";
  private static String password = "remote";

  // Default databasetype
  private static String dataBaseType = "H2fromCSV";
  private static String connectionString = "jdbc:h2:mem:neqsimthermodatabase";
  /** True if h2 database has been initialized, i.e., populated with tables */
  private static boolean h2IsInitialized = false;
  /** True while h2 database is being initialized. */
  private static boolean h2IsInitalizing = false;
  // static String dataBaseType = "MSAccessUCanAccess";
  // public static String connectionString =
  // "jdbc:ucanaccess://C:/Users/esol/OneDrive -
  // Equinor/programming/neqsimdatabase/MSAccess/NeqSimDataBase.mdb;memory=true";

  private Statement statement = null;
  protected Connection databaseConnection = null;

  /**
   * <p>
   * Constructor for NeqSimDataBase.
   * </p>
   */
  public NeqSimDataBase() {
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
   * <p>
   * openConnection.
   * </p>
   *
   * @return a Connection object
   * @throws java.sql.SQLException if any.
   * @throws java.lang.ClassNotFoundException if any.
   */
  public Connection openConnection() throws SQLException, ClassNotFoundException {
    javax.naming.InitialContext ctx = null;
    javax.sql.DataSource ds = null;
    try {
      if (System.getenv("NEQSIMTHERMODB_CS") != null) {
        Properties properties = new Properties();
        properties.setProperty("user", System.getenv("MYSQL_USER"));
        properties.setProperty("password", System.getenv("MYSQL_PASSWORD"));
        properties.setProperty("useSSL", "false");
        return DriverManager.getConnection(System.getenv("NEQSIMTHERMODB_CS"), properties);
      } else if (dataBaseType.equals("MSAccess")) {
        String dir = "";
        if (System.getProperty("NeqSim.home") == null) {
          dir = neqsim.util.util.FileSystemSettings.root
              + "\\programming\\NeqSimSourceCode\\java\\neqsim";
        } else {
          dir = System.getProperty("NeqSim.home");
        }
        return DriverManager.getConnection("jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ="
            + dir + "\\data\\NeqSimDatabase");
      } else if (dataBaseType.equals("H2fromCSV") || dataBaseType.equals("H2")
          || dataBaseType.equals("H2RT")) {
        return DriverManager.getConnection(connectionString, "sa", "");
      } else if (dataBaseType.equals("MSAccessUCanAccess")) {
        return DriverManager.getConnection(getConnectionString());
      } else if (dataBaseType.equals("mySQL") || dataBaseType.equals("mySQLNTNU")
          || dataBaseType.equals("Derby")) {
        return DriverManager.getConnection(getConnectionString(), username, password);
      } else if (dataBaseType.equals("mySQLNeqSimWeb")) {
        ctx = new javax.naming.InitialContext();
        ds = (javax.sql.DataSource) ctx.lookup("java:comp/env/jdbc/NeqsimThermoDatabase");
        return ds.getConnection();
      } else {
        return DriverManager.getConnection(getConnectionString());
      }
    } catch (Exception ex) {
      logger.error("error loading NeqSimDataBase... ", ex);
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
   * Getter for the field <code>statement</code>.
   * </p>
   *
   * @return a Statement object
   */
  public Statement getStatement() {
    return statement;
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
   * Execute query using execute.
   * </p>
   *
   * @param sqlString Query to execute.
   * @return True if the first result is a ResultSet object; false if it is an update count or there
   *         are no results
   */
  public boolean execute(String sqlString) {
    try {
      if (databaseConnection == null) {
        databaseConnection = this.openConnection();
        setStatement(databaseConnection.createStatement());
      }
      return getStatement().execute(sqlString);
    } catch (Exception ex) {
      logger.error("error in NeqSimDataBase ", ex);
      // TODO: should be checked against database type.
      logger.error("The database must be registered on the local DBMS to work.");
      throw new RuntimeException(ex);
    }
  }

  /**
   * <p>
   * Execute query using executeQuery but do not return anything.
   * </p>
   *
   * @param sqlString Query to execute.
   */
  public void executeQuery(String sqlString) {
    try {
      if (databaseConnection == null) {
        databaseConnection = this.openConnection();
        setStatement(databaseConnection.createStatement());
      }
      getStatement().executeQuery(sqlString);
    } catch (Exception ex) {
      logger.error("error in NeqSimDataBase ", ex);
      // TODO: should be checked against database type.
      logger.error("The database must be registered on the local DBMS to work.");
      throw new RuntimeException(ex);
    }
  }

  /**
   * <p>
   * Execute query using executeQuery and return ResultSet.
   * </p>
   *
   * @param sqlString Query to execute.
   * @return a ResultSet object
   */
  public ResultSet getResultSet(String sqlString) {
    try {
      return getStatement().executeQuery(sqlString);
    } catch (JdbcSQLSyntaxErrorException ex) {
      if (ex.getMessage().startsWith("Table ") && ex.getMessage().contains(" not found;")) {
        throw new RuntimeException(new neqsim.util.exception.NotInitializedException(this,
            "getResultSet", ex.getMessage()));
      }
      throw new RuntimeException(ex);
    } catch (Exception ex) {
      logger.error("error loading NeqSimbataBase ", ex);
      throw new RuntimeException(ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws SQLException {
    if (databaseConnection != null) {
      databaseConnection.close();
    }
    if (statement != null) {
      statement.close();
    }
  }

  /**
   * <p>
   * createTemporaryTables.
   * </p>
   *
   * @return the createTemporaryTables
   */
  public static boolean createTemporaryTables() {
    return createTemporaryTables;
  }

  /**
   * <p>
   * Setter for the field <code>createTemporaryTables</code>.
   * </p>
   *
   * @param createTemporaryTables the createTemporaryTables to set
   */
  public static void setCreateTemporaryTables(boolean createTemporaryTables) {
    NeqSimDataBase.createTemporaryTables = createTemporaryTables;
  }

  /**
   * <p>
   * Getter for the field <code>dataBaseType</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public static String getDataBaseType() {
    return dataBaseType;
  }

  /**
   * <p>
   * Setter for the field <code>dataBaseType</code>.
   * </p>
   *
   * @param aDataBaseType a {@link java.lang.String} object
   */
  public static void setDataBaseType(String aDataBaseType) {
    setDataBaseType(aDataBaseType, null);
  }

  /**
   * <p>
   * Setter for the field <code>dataBaseType</code>.
   * </p>
   *
   * @param aDataBaseType a {@link java.lang.String} object
   * @param connectionString a {@link java.lang.String} object
   */
  public static void setDataBaseType(String aDataBaseType, String connectionString) {
    dataBaseType = aDataBaseType;

    // Fill tables from csv-files if not initialized and not currently being
    // initialized.
    if ("H2fromCSV".equals(dataBaseType) && !h2IsInitialized && !h2IsInitalizing) {
      initH2DatabaseFromCSVfiles();
    }

    if (connectionString != null) {
      NeqSimDataBase.connectionString = connectionString;
    }

    try {
      if (dataBaseType.equals("MSAccess")) {
        Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("H2fromCSV") || dataBaseType.equals("H2")
          || dataBaseType.equals("H2RT")) {
        Class.forName("org.h2.Driver");
      } else if (dataBaseType.equals("MSAccessUCanAccess")) {
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
      } else if (dataBaseType.equals("mySQL")) {
        Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("mySQLNTNU")) {
        Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("Derby")) {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor()
            .newInstance();
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
   * <p>
   * Setter for the field <code>username</code>.
   * </p>
   *
   * @param aUsername the username to set
   */
  public static void setUsername(String aUsername) {
    username = aUsername;
  }

  /**
   * <p>
   * Setter for the field <code>password</code>.
   * </p>
   *
   * @param aPassword the password to set
   */
  public static void setPassword(String aPassword) {
    password = aPassword;
  }

  /**
   * <p>
   * Getter for the field <code>connectionString</code>.
   * </p>
   *
   * @return the connectionString
   */
  public static String getConnectionString() {
    return connectionString;
  }

  /**
   * <p>
   * Setter for the field <code>connectionString</code>.
   * </p>
   *
   * @param aConnectionString the connectionString to set
   */
  public static void setConnectionString(String aConnectionString) {
    connectionString = aConnectionString;
  }

  /**
   * <p>
   * getComponentNames.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public static String[] getComponentNames() {
    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT name FROM comp ORDER BY ID")) {
      List<String> names = new ArrayList<>();
      while (dataSet.next()) {
        names.add(dataSet.getString("name"));
      }
      return names.toArray(new String[0]);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Verify if database has a component.
   *
   * @param name Name of component to look for.
   * @return True if component is found.
   */
  public static boolean hasComponent(String name) {
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet =
            database.getResultSet("select count(*) from comp WHERE NAME='" + name + "'")) {
      dataSet.next();
      int size = dataSet.getInt(1);
      if (size == 0) {
        return false;
      } else {
        return true;
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Verify if database has a component.
   *
   * @param name Name of component to look for.
   * @return True if component is found.
   */
  public static boolean hasTempComponent(String name) {
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet =
            database.getResultSet("select count(*) from comptemp WHERE NAME='" + name + "'")) {
      dataSet.next();
      int size = dataSet.getInt(1);
      if (size == 0) {
        return false;
      } else {
        return true;
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Drops and re-creates table from contents in default csv file.
   *
   * @param tableName Name of table to replace
   */
  public static void updateTable(String tableName) {
    updateTable(tableName, "data/" + tableName + ".csv");
  }

  /**
   * Drops and re-creates table from contents in csv file.
   *
   * @param tableName Name of table to replace
   * @param path Path to csv file to get table data from
   */
  public static void updateTable(String tableName, String path) {
    URL url = NeqSimDataBase.class.getClassLoader().getResource(path);
    if (url == null) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("NeqSimDataBase",
          "updateTable", "path", "- Resource " + path + " not found"));
    }
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      database.execute("DROP TABLE IF EXISTS " + tableName);
      String sqlString =
          "CREATE TABLE " + tableName + " AS SELECT * FROM CSVREAD('file:" + url + "')";
      database.execute(sqlString);
    } catch (Exception ex) {
      logger.error("Failed updating table " + tableName, ex);
    }
  }

  /**
   * Drops and re-creates table from contents in csv file.
   *
   * @param tableName Name of table to replace
   * @param path Path to csv file to
   */
  public static void replaceTable(String tableName, String path) {
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      database.execute("DROP TABLE IF EXISTS " + tableName);
      String sqlString = "CREATE TABLE " + tableName + " AS SELECT * FROM CSVREAD('" + path + "')";
      database.execute(sqlString);
    } catch (Exception ex) {
      updateTable(tableName);
      logger.error("Failed updating table " + tableName, ex);
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("NeqSimDataBase",
          "replaceTable", "path", "- Resource " + path + " not found"));
    }
  }

  /**
   * <p>
   * initH2DatabaseFromCSVfiles.
   * </p>
   */
  public static void initH2DatabaseFromCSVfiles() {
    h2IsInitalizing = true;
    neqsim.util.database.NeqSimDataBase.connectionString =
        "jdbc:h2:mem:neqsimthermodatabase;DB_CLOSE_DELAY=-1";
    neqsim.util.database.NeqSimDataBase.dataBaseType = "H2";

    try {
      updateTable("COMP");
      updateTable("INTER");
      updateTable("element");
      updateTable("ISO6976constants");
      updateTable("ISO6976constants2016");
      updateTable("STOCCOEFDATA");
      updateTable("REACTIONDATA");
      // Table ReactionKSPdata is not in use anywhere
      updateTable("ReactionKSPdata");
      updateTable("AdsorptionParameters");

      updateTable("UNIFACcomp");
      updateTable("UNIFACcompUMRPRU");
      updateTable("UNIFACGroupParam");
      updateTable("UNIFACInterParam");

      updateTable("UNIFACInterParamA_UMR");
      updateTable("UNIFACInterParamA_UMRMC");

      updateTable("UNIFACInterParamB");
      updateTable("UNIFACInterParamB_UMR");
      updateTable("UNIFACInterParamB_UMRMC");

      updateTable("UNIFACInterParamC");
      updateTable("UNIFACInterParamC_UMR");
      updateTable("UNIFACInterParamC_UMRMC");
      updateTable("MBWR32param");
      updateTable("COMPSALT");
      updateTable("PIPEDATA");

      // TODO: missing tables: ionicData, reactiondatakenteisenberg,
      // purecomponentvapourpressures,
      // binarysystemviscosity, binaryliquiddiffusioncoefficientdata,
      // purecomponentconductivitydata, purecomponentdensity,
      // purecomponentsurfacetension2,
      // BinaryComponentSurfaceTension, purecomponentsurfacetension,
      // purecomponentviscosity,PureComponentVapourPressures
      // technicalrequirements, technicalrequirements_process, materialpipeproperties,
      // materialplateproperties, fittings, LuciaData, Luciadata8

      try (neqsim.util.database.NeqSimDataBase database =
          new neqsim.util.database.NeqSimDataBase()) {
        database.execute("CREATE TABLE comptemp AS SELECT * FROM comp");
        database.execute("CREATE TABLE intertemp AS SELECT * FROM inter");
      }

      h2IsInitialized = true;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
