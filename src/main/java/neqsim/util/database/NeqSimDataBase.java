package neqsim.util.database;

/*
 * testPointbase.java
 *
 * Created on 1. november 2001, 08:56
 */
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
 * @author Even Solbraa
 * @version Dec 2018
 */
public class NeqSimDataBase implements neqsim.util.util.FileSystemSettings, java.io.Serializable {
    /**
     * @return the createTemporaryTables
     */
    public static boolean createTemporaryTables() {
        return createTemporaryTables;
    }

    /**
     * @param createTemporaryTables the createTemporaryTables to set
     */
    public static void setCreateTemporaryTables(boolean createTemporaryTables) {
        NeqSimDataBase.createTemporaryTables = createTemporaryTables;
    }

    private static final long serialVersionUID = 1000;
    public static String dataBasePath = "";
    static Logger logger = LogManager.getLogger(NeqSimDataBase.class);
    private static boolean createTemporaryTables = false;

    private static String dataBaseType = "Derby";
    private static String connectionString = "jdbc:derby:classpath:data/neqsimthermodatabase";
    private static String username = "remote";
    private static String password = "remote";

    // static String dataBaseType = "MSAccessUCanAccess";
    // public static String connectionString =
    // "jdbc:ucanaccess://C:/Users/esol/OneDrive -
    // Equinor/programming/neqsimdatabase/MSAccess/NeqSimDataBase.mdb;memory=true";

    private Statement statement = null;
    protected Connection databaseConnection = null;

    /**
     * Creates new testPointbase
     */
    public NeqSimDataBase() {
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
     * Creates new NeqSimDataBase
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
                return DriverManager
                        .getConnection("jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ="
                                + dir + "\\data\\NeqSimDatabase");
            } else if (dataBaseType.equals("H2") || dataBaseType.equals("H2RT")) {
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
            } catch (Exception e) {
                logger.error("error", e);
            }
        }
    }

    public Connection getConnection() {
        return databaseConnection;
    }

    public ResultSet getResultSet(String sqlString) {
        try {
            ResultSet result = getStatement().executeQuery(sqlString);
            return result;
        } catch (Exception e) {
            logger.error("error loading NeqSimbataBase " + e.toString());
            throw new RuntimeException(e);
        }
    }

    public void execute(String sqlString) {
        try {
            if (databaseConnection == null) {
                databaseConnection = this.openConnection();
                setStatement(databaseConnection.createStatement());
            }
            getStatement().execute(sqlString);
        } catch (Exception e) {
            logger.error("error in NeqSimDataBase " + e.toString(), e);
            logger.error("The database must be rgistered on the local DBMS to work.");
            throw new RuntimeException(e);
        }
    }

    public static String getDataBaseType() {
        return dataBaseType;
    }

    public static void setDataBaseType(String aDataBaseType) {
        setDataBaseType(aDataBaseType, null);
    }

    public static void setDataBaseType(String aDataBaseType, String connectionString) {
        dataBaseType = aDataBaseType;

        if (connectionString != null) {
            NeqSimDataBase.connectionString = connectionString;
        }

        try {
            if (dataBaseType.equals("MSAccess")) {
                Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").getDeclaredConstructor()
                        .newInstance();
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
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor()
                        .newInstance();
            } else if (dataBaseType.equals("oracle")) {
                Class.forName("oracle.jdbc.driver.OracleDriver").getDeclaredConstructor()
                        .newInstance();
            } else if (dataBaseType.equals("oracleST")) {
                Class.forName("oracle.jdbc.driver.OracleDriver").getDeclaredConstructor()
                        .newInstance();
            } else {
                Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
            }
        } catch (Exception ex) {
            logger.error("error loading database driver.. " + ex.toString());
            throw new RuntimeException(ex);
        }
    }

    public Statement getStatement() {
        return statement;
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    /**
     * @param aUsername the username to set
     */
    public static void setUsername(String aUsername) {
        username = aUsername;
    }

    /**
     * @param aPassword the password to set
     */
    public static void setPassword(String aPassword) {
        password = aPassword;
    }

    /**
     * @return the connectionString
     */
    public static String getConnectionString() {
        return connectionString;
    }

    /**
     * @param aConnectionString the connectionString to set
     */
    public static void setConnectionString(String aConnectionString) {
        connectionString = aConnectionString;
    }

    public static void main(String[] args) {
        NeqSimDataBase database = new NeqSimDataBase();

        ResultSet dataSet = database.getResultSet("SELECT * FROM comp WHERE NAME='methane'");

        try {
            dataSet.next();
            logger.info("dataset " + dataSet.getString("molarmass"));
        } catch (Exception e) {
            logger.error("failed " + e.toString());
            throw new RuntimeException(e);
        }
    }

    public static String[] getComponentNames() {
        NeqSimDataBase database = new NeqSimDataBase();
        try {
            List<String> names = new ArrayList<>();
            ResultSet dataSet = database.getResultSet("SELECT name FROM comp ORDER BY ID");
            while (dataSet.next()) {
                names.add(dataSet.getString("name"));
            }
            return names.toArray(new String[0]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean hasComponent(String compName) {
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        String[] names = null;
        try {
            dataSet = database
                    .getResultSet("select count(*) from comp WHERE NAME='" + compName + "'");
            dataSet.next();
            int size = dataSet.getInt(1);
            if (size == 0) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
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
            } catch (Exception e) {
                logger.error("error closing database.....", e);
            }
        }
    }
}
