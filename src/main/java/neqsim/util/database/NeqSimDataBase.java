package neqsim.util.database;

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

/**
 * <p>
 * NeqSimDataBase class.
 * </p>
 *
 * @author Even Solbraa
 * @version Dec 2018
 */
public class NeqSimDataBase implements neqsim.util.util.FileSystemSettings, java.io.Serializable {
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

  private static final long serialVersionUID = 1000;
  /** Constant <code>dataBasePath=""</code>. */
  public static String dataBasePath = "";
  static Logger logger = LogManager.getLogger(NeqSimDataBase.class);

  private static boolean createTemporaryTables = false;

  // private static String dataBaseType = "Derby";
  // private static String connectionString = "jdbc:derby:classpath:data/neqsimthermodatabase";
  private static String username = "remote";
  private static String password = "remote";

  private static String dataBaseType = "H2fromCSV";
  private static String connectionString = "jdbc:h2:mem:neqsimthermodatabase";
  private static boolean h2IsInit = false;
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
    if (dataBaseType == "H2fromCSV" && !h2IsInit) {
      h2IsInit = true;
      initH2DatabaseFromCSVfiles();
    }
    setDataBaseType(dataBaseType);

    try {
      databaseConnection = this.openConnection();
      statement = databaseConnection.createStatement();
    } catch (Exception ex) {
      logger.error("SQLException " + ex.getMessage());
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
      logger.error("error loading NeqSimDataBase... " + ex.toString());
      throw new RuntimeException(ex);
    } finally {
      try {
        if (ctx != null) {
          ctx.close();
        }
      } catch (Exception ex) {
        logger.error("error", ex);
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
   * getResultSet.
   * </p>
   *
   * @param sqlString a {@link java.lang.String} object
   * @return a ResultSet object
   */
  public ResultSet getResultSet(String sqlString) {
    try {
      ResultSet result = getStatement().executeQuery(sqlString);
      return result;
    } catch (Exception ex) {
      logger.error("error loading NeqSimbataBase " + ex.toString());
      throw new RuntimeException(ex);
    }
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
        databaseConnection = this.openConnection();
        setStatement(databaseConnection.createStatement());
      }
      getStatement().execute(sqlString);
    } catch (Exception ex) {
      logger.error("error in NeqSimDataBase " + ex.toString(), ex);
      logger.error("The database must be rgistered on the local DBMS to work.");
      throw new RuntimeException(ex);
    }
  }

  /**
   * <p>
   * execute.
   * </p>
   *
   * @param sqlString a {@link java.lang.String} object
   */
  public void executeQuery(String sqlString) {
    try {
      if (databaseConnection == null) {
        databaseConnection = this.openConnection();
        setStatement(databaseConnection.createStatement());
      }
      getStatement().executeQuery(sqlString);
    } catch (Exception ex) {
      logger.error("error in NeqSimDataBase " + ex.toString(), ex);
      logger.error("The database must be rgistered on the local DBMS to work.");
      throw new RuntimeException(ex);
    }
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

    if (connectionString != null) {
      NeqSimDataBase.connectionString = connectionString;
    }

    try {
      if (dataBaseType.equals("MSAccess")) {
        Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").getDeclaredConstructor().newInstance();
      } else if (dataBaseType.equals("H2fromCSV") || dataBaseType.equals("H2")
          || dataBaseType.equals("H2RT")) {
        // Class.forName("org.h2.Driver");
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
      logger.error("error loading database driver.. " + ex.toString());
      throw new RuntimeException(ex);
    }
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
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    // NeqSimDataBase.initH2DatabaseFromCSVfiles();
    // NeqSimDataBase.initDatabaseFromCSVfiles();
    NeqSimDataBase database = new NeqSimDataBase();
    NeqSimDataBase.updateTable("COMP", "/workspaces/neqsim/src/main/resources/data/COMP.csv");

    try (ResultSet dataSet = database.getResultSet("SELECT * FROM comp WHERE NAME='methane'")) {
      dataSet.next();
      System.out.println("dataset " + dataSet.getString("molarmass"));
      logger.info("dataset " + dataSet.getString("molarmass"));
      dataSet.close();
      database.getConnection().close();
    } catch (Exception ex) {
      logger.error("failed " + ex.toString());
      throw new RuntimeException(ex);
    }
  }

  /**
   * <p>
   * getComponentNames.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public static String[] getComponentNames() {
    NeqSimDataBase database = new NeqSimDataBase();
    try (ResultSet dataSet = database.getResultSet("SELECT name FROM comp ORDER BY ID")) {
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
   * <p>
   * hasComponent.
   * </p>
   *
   * @param compName a {@link java.lang.String} object
   * @return a boolean
   */
  public static boolean hasComponent(String compName) {
    neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
    java.sql.ResultSet dataSet = null;
    try {
      dataSet = database.getResultSet("select count(*) from comp WHERE NAME='" + compName + "'");
      dataSet.next();
      int size = dataSet.getInt(1);
      if (size == 0) {
        return false;
      } else {
        return true;
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    } finally {
      try {
        if (dataSet != null) {
          dataSet.close();
        }
        if (database.getStatement() != null) {
          database.getStatement().close();
        }
        if (database.getConnection() != null) {
          database.getConnection().close();
        }
      } catch (Exception ex) {
        logger.error("error closing database.....", ex);
      }
    }
  }

  public static void updateTable(String tableName, String path) {
    neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
    String sqlString = "CREATE TABLE " + tableName + " AS SELECT * FROM CSVREAD('" + path + "')";
    try {
      database.execute("DROP TABLE " + tableName);
      database.execute(sqlString);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (database.getStatement() != null) {
          database.getStatement().close();
        }
      } catch (Exception ex) {
        logger.error("error closing database.....", ex);
      }
    }
  }

  public static void initH2DatabaseFromCSVfiles() {
    neqsim.util.database.NeqSimDataBase.connectionString =
        "jdbc:h2:mem:neqsimthermodatabase;DB_CLOSE_DELAY=-1";
    neqsim.util.database.NeqSimDataBase.createTemporaryTables = false;
    neqsim.util.database.NeqSimDataBase.dataBaseType = "H2";
    neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
    // Connection con = database.getConnection();
    // Statement stmn = con.createStatement();
    // stmn.execute(defaultDatabaseRootRoot)

    String createCOMP = "CREATE TABLE COMP AS SELECT * FROM CSVREAD('classpath:/data/COMP.csv')";
    String createINTER = "CREATE TABLE INTER AS SELECT * FROM CSVREAD('classpath:/data/INTER.csv')";
    String createElement =
        "CREATE TABLE element AS SELECT * FROM CSVREAD('classpath:/data/element.csv')";
    String create_iso6976 =
        "CREATE TABLE iso6976constants AS SELECT * FROM CSVREAD('classpath:/data/ISO6976constants.csv')";
    String create_iso6976_2016 =
        "CREATE TABLE iso6976constants2016 AS SELECT * FROM CSVREAD('classpath:/data/ISO6976constants2016.csv')";
    String create_STOCCOEFDATA =
        "CREATE TABLE STOCCOEFDATA AS SELECT * FROM CSVREAD('classpath:/data/STOCCOEFDATA.csv')";
    String create_REACTIONDATA =
        "CREATE TABLE REACTIONDATA AS SELECT * FROM CSVREAD('classpath:/data/REACTIONDATA.csv')";
    String ReactionKSPdata =
        "CREATE TABLE ReactionKSPdata AS SELECT * FROM CSVREAD('classpath:/data/ReactionKSPdata.csv')";
    String create_AdsorptionParameters =
        "CREATE TABLE AdsorptionParameters AS SELECT * FROM CSVREAD('classpath:/data/AdsorptionParameters.csv')";
    String create_UNIFACcomp =
        "CREATE TABLE UNIFACcomp AS SELECT * FROM CSVREAD('classpath:/data/UNIFACcomp.csv')";
    String create_UNIFACcompUMRPRU =
        "CREATE TABLE UNIFACcompUMRPRU AS SELECT * FROM CSVREAD('classpath:/data/UNIFACcompUMRPRU.csv')";
    String UNIFACGroupParam =
        "CREATE TABLE UNIFACGroupParam AS SELECT * FROM CSVREAD('classpath:/data/UNIFACGroupParam.csv')";
    String UNIFACInterParam =
        "CREATE TABLE UNIFACInterParam AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParam.csv')";
    String UNIFACInterParamA_UMR =
        "CREATE TABLE UNIFACInterParamA_UMR AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParamA_UMR.csv')";
    String UNIFACInterParamA_UMRMC =
        "CREATE TABLE UNIFACInterParamA_UMRMC AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParamA_UMRMC.csv')";
    String UNIFACInterParamB =
        "CREATE TABLE UNIFACInterParamB AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParamB.csv')";
    String UNIFACInterParamB_UMR =
        "CREATE TABLE UNIFACInterParamB_UMR AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParamB_UMR.csv')";
    String UNIFACInterParamB_UMRMC =
        "CREATE TABLE UNIFACInterParamB_UMRMC AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParamB_UMRMC.csv')";
    String UNIFACInterParamC =
        "CREATE TABLE UNIFACInterParamC AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParamC.csv')";
    String UNIFACInterParamC_UMR =
        "CREATE TABLE UNIFACInterParamC_UMR AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParamC_UMR.csv')";
    String UNIFACInterParamC_UMRMC =
        "CREATE TABLE UNIFACInterParamC_UMRMC AS SELECT * FROM CSVREAD('classpath:/data/UNIFACInterParamC_UMRMC.csv')";

    try {
      database.execute(createCOMP);
      database.execute(createINTER);
      database.execute(createElement);
      database.execute(create_iso6976);
      database.execute(create_iso6976_2016);
      database.execute(create_STOCCOEFDATA);
      database.execute(create_REACTIONDATA);
      database.execute(ReactionKSPdata);
      database.execute(create_AdsorptionParameters);
      database.execute(create_UNIFACcomp);
      database.execute(create_UNIFACcompUMRPRU);
      database.execute(UNIFACGroupParam);
      database.execute(UNIFACInterParam);
      database.execute(UNIFACInterParamA_UMR);
      database.execute(UNIFACInterParamA_UMRMC);
      database.execute(UNIFACInterParamB);
      database.execute(UNIFACInterParamB_UMR);
      database.execute(UNIFACInterParamB_UMRMC);
      database.execute(UNIFACInterParamC);
      database.execute(UNIFACInterParamC_UMR);
      database.execute(UNIFACInterParamC_UMRMC);
      database.execute("CREATE TABLE comptemp AS SELECT * FROM comp");
      database.execute("CREATE TABLE intertemp AS SELECT * FROM inter");
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (database.getStatement() != null) {
          database.getStatement().close();
        }
        if (database.getConnection() != null) {
          database.getConnection().close();
        }
      } catch (Exception ex) {
        logger.error("error closing database.....", ex);
      }
    }
  }
}
