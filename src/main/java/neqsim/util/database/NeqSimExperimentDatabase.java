/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package neqsim.util.database;

/*
 * testPointbase.java
 *
 * Created on 1. november 2001, 08:56
 */
import java.sql.*;
import org.apache.logging.log4j.*;

/**
 * @author  Even Solbraa
 * @version Dec 2018
 */
public class NeqSimExperimentDatabase implements neqsim.util.util.FileSystemSettings, java.io.Serializable {

    /**
     * @return the createTemporaryTables
     */
    public boolean createTemporaryTables() {
        return createTemporaryTables;
    }

    /**
     * @param createTemporaryTables the createTemporaryTables to set
     */
    public void setCreateTemporaryTables(boolean createTemporaryTables) {
        NeqSimExperimentDatabase.createTemporaryTables = createTemporaryTables;
    }

    private static final long serialVersionUID = 1000;
    public static String dataBasePath = "";
    public static String username = "", password = "";
    static Logger logger = LogManager.getLogger(NeqSimExperimentDatabase.class);
    private static boolean createTemporaryTables = false;

    private static String dataBaseType = "MSAccessUCanAccess";
    public static String connectionString = "jdbc:ucanaccess://C:/Users/esol/OneDrive - Equinor/programming/neqsimdatabase/MSAccess/NeqSimExperimentalData.mdb;memory=true";

    private Statement statement = null;
    protected Connection databaseConnection = null;

    /**
     * Creates new testPointbase
     */
    public NeqSimExperimentDatabase() {

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
            if (dataBaseType.equals("MSAccessUCanAccess")) {
                return DriverManager.getConnection(getConnectionString());
            } else if (dataBaseType.equals("mySQL") || dataBaseType.equals("mySQLNTNU")
                    || dataBaseType.equals("Derby")) {
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

    }

}
