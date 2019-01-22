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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.sql.*;
import org.apache.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version Dec 2018
 */
public final class NeqSimDataBase implements neqsim.util.util.FileSystemSettings, java.io.Serializable {

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
        this.createTemporaryTables = createTemporaryTables;
    }

    private static final long serialVersionUID = 1000;
    public static String dataBasePath = "", username = "remote", password = "remote";
    static Logger logger = Logger.getLogger(NeqSimDataBase.class);
    private boolean createTemporaryTables = false;

    private static String dataBaseType = "Derby";
    private static String connectionString = "jdbc:derby:classpath:data/neqsimthermodatabase";
   // private static String connectionString = "jdbc:derby:C:/programming/NeqSimSourceCode/java/neqsim/data/webdb/neqsimthermodatabase";
    //
    //private static String dataBaseType = "H2RT";
    //private static String connectionString = "jdbc:h2:C:/Users/esol/OneDrive - Statoil ASA/programming/neqsimsource/src/main/resources/data/neqsimH2DB/neqsim.db;DATABASE_TO_UPPER=false";
    // private static String username = "sa";
    // private static String password = "";    
    //private static String dataBaseType ="MSAccess";
    //public static String dataBasePath = "C:/programming/NeqSimSourceCode/java/neqsim/data/NeqSimDatabase.mdb";
   // private static String dataBaseType = "MSAccessUCanAccess";
   // public static String connectionString = "jdbc:ucanaccess://C:/programming/NeqSimSourceCode/java/neqsim/data/NeqSimDatabase.mdb;memory=true";
    //
    //private static String dataBaseType ="mySQLNTNU";
    //private static String connectionString = "jdbc:mysql://iept1122.ivt.ntnu.no:3306/neqsimthermodatabase";
    // private static String username = "remote";
    // private static String password = "remote";
    //
    //private static String dataBaseType ="mySQL";
    //private static String connectionString = "jdbc:mysql://tr-w33:3306/neqsimthermodatabase";
    // private static String username = "remote";
    // private static String password = "remote";  
    //
    //private static String dataBaseType ="mySQLNeqSimWeb";
    //
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
            if (dataBaseType.equals("MSAccess")) {
                String dir = "";
                if (System.getProperty("NeqSim.home") == null) {
                    dir = neqsim.util.util.FileSystemSettings.root + "\\programming\\NeqSimSourceCode\\java\\neqsim";
                } else {
                    dir = System.getProperty("NeqSim.home");
                }
                return DriverManager.getConnection("jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ=" + dir + "\\data\\NeqSimDatabase");

            } else if (dataBaseType.equals("H2") || dataBaseType.equals("H2RT")) {
                //  Path path = FileSystems.getDefault().getPath(dataBasePath);
                //  return DriverManager.getConnection("jdbc:h2:" + path.toAbsolutePath().toString() + ";DATABASE_TO_UPPER=false", "sa", "");
                return DriverManager.getConnection(connectionString, "sa", "");
           } else if (dataBaseType.equals("MSAccessUCanAccess")) {
                return DriverManager.getConnection("jdbc:ucanaccess://" + dataBasePath + ";memory=true");
            } else if (dataBaseType.equals("mySQL") || dataBaseType.equals("mySQLNTNU") || dataBaseType.equals("Derby")) {
                return DriverManager.getConnection(getConnectionString(), username, password);
            } else if (dataBaseType.equals("oracle")) {
                return DriverManager.getConnection("jdbc:oracle:thin:@db13.statoil.no:10002:U461", "neqsim", "neqsim");
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

    public static void setDataBaseType(String aDataBaseType, String connectionString) {
        dataBaseType = aDataBaseType;
        if (connectionString != null) {
            NeqSimDataBase.connectionString = connectionString;
        }
        setDataBaseType(aDataBaseType);
    }

    public static void setDataBaseType(String aDataBaseType) {
        setDataBaseType(aDataBaseType, null);
    }
    
    public static void setDataBaseType(String aDataBaseType, String connectionString) {
        dataBaseType = aDataBaseType;

        if (connectionString!=null) {
            NeqSimDataBase.connectionString = connectionString;
        }
      
        try {
            if (dataBaseType.equals("MSAccess")) {
                Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").newInstance();
            } else if (dataBaseType.equals("H2")) {
                Class.forName("org.h2.Driver");
            } else if (dataBaseType.equals("H2RT")) {
                Class.forName("org.h2.Driver");
            } else if (dataBaseType.equals("MSAccessUCanAccess")) {
                Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            } else if (dataBaseType.equals("mySQL")) {
                Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
            } else if (dataBaseType.equals("mySQLNTNU")) {
                Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
            } else if (dataBaseType.equals("Derby")) {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
            } else if (dataBaseType.equals("oracle")) {
                Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
            } else if (dataBaseType.equals("oracleST")) {
                Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
            } else {
                Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
            }
        } catch (Exception ex) {
            logger.error("error loading database driver.. " + ex.toString());
            throw new RuntimeException(ex);
        }
    }

    public Statement
            getStatement() {
        return statement;

    }

    public void setStatement(Statement statement
    ) {
        this.statement
                = statement;

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

    public static void main(String[] args
    ) {
        /*
        // example of how to use mySQL  database
        neqsim.util.database.NeqSimDataBase.setDataBaseType("mySQL");
        neqsim.util.database.NeqSimDataBase.setConnectionString("jdbc:mysql://tr-w33:3306/neqsimthermodatabase");
        neqsim.util.database.NeqSimDataBase.setUsername("remote");
        neqsim.util.database.NeqSimDataBase.setPassword("remote");
         */

        // example of how to use local Access database
        NeqSimDataBase.dataBasePath
                = "C:/programming/NeqSimSourceCode/java/neqsim/data/NeqSimDatabase.mdb";
        NeqSimDataBase.dataBaseType
                = "MSAccessUCanAccess";

        neqsim.util.database.NeqSimDataBase.setDataBaseType("MSAccessUCanAccess");
        //neqsim.util.database.NeqSimDataBase.setConnectionString("jdbc:derby:classpath:data/neqsimthermodatabase");//jdbc:mysql://tr-w33:3306/neqsimthermodatabase");"jdbc:derby://localhost:1527/neqsimthermodatabase"
        //  neqsim.util.database.NeqSimDataBase.setConnectionString("jdbc:derby:classpath:data/neqsimthermodatabase");
        neqsim.util.database.NeqSimDataBase.setUsername("remote");
        neqsim.util.database.NeqSimDataBase.setPassword("remote");

        NeqSimDataBase database = new NeqSimDataBase();

        ResultSet dataSet = database.getResultSet("SELECT * FROM COMP WHERE NAME='methane'");

        try {
            dataSet.next();
            logger.info("dataset " + dataSet.getString("molarmass"));

        } catch (Exception e) {
            logger.error("failed " + e.toString());
            throw new RuntimeException(e);
        }

    }

    public static String[] getComponentNames() {
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        String[] names = null;
        try {
            dataSet = database.getResultSet("select count(*) from COMP");
            dataSet.next();
            int size = dataSet.getInt(1);

            dataSet = database.getResultSet(("SELECT * FROM COMP ORDER BY ID"));

            names = new String[size];
            for (int i = 0; i < size; i++) {
                dataSet.next();
                names[i] = dataSet.getString("name");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return names;
    }

    public static boolean hasComponent(String compName) {
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        String[] names = null;
        try {
            dataSet = database.getResultSet("select count(*) from COMP WHERE NAME='" + compName + "'");
            dataSet.next();
            int size = dataSet.getInt(1);
            if (size == 0) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
