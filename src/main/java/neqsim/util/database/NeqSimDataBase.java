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

/**
 *
 * @author Even Solbraa
 * @version Dec 2018
 */
public final class NeqSimDataBase implements neqsim.util.util.FileSystemSettings, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    private static String dataBaseType = "MSAccessUCanAccess"; //"MSAccess";// //"MSAccessUCanAccess";//"mySQL";//"oracle", "oracleST" , "mySQLNTNU";
    private static String connectionString = "jdbc:mysql://tr-w33:3306/neqsimthermodatabase";
    private static String username = "remote";
    private static String password = "remote";
    // connection string to Equinor mysql database "jdbc:mysql://tr-w33:3306/neqsimthermodatabase";
    // connection string to local derby database "jdbc:derby://localhost:1527/neqsimthermodatabase";
    // connection string to NTNU database  public static String connectionString = "jdbc:mysql://iept1122.ivt.ntnu.no:3306/neqsimthermodatabase";
    // connection string to MSAccess neqsim database jdbc:odbc:Driver={Microsoft Access Driver (*.mdb)};DBQ=C:/programming/NeqSimSourceCode/java/neqsim/data/NeqSimDatabase";
    // connection string to MSAccess neqsim database using UCanAccess "jdbc:ucanaccess://" + dataBasePathMSAccess + ";memory=true")
    // connection string to 
    public static String dataBasePathMSAccess = "C:/programming/NeqSimSourceCode/java/neqsim/data/NeqSimDatabase.mdb";

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
            System.out.println("SQLException " + ex.getMessage());

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
            }
            if (dataBaseType.equals("MSAccessUCanAccess")) {
                return DriverManager.getConnection("jdbc:ucanaccess://" + dataBasePathMSAccess + ";memory=true");
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
            System.out.println("SQLException.. " + ex.getMessage());
            System.out.println("error loading NeqSimDataBase... " + ex.toString());
            System.out.println("Could not find database..");
        } finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public Connection getConnection() {
        return databaseConnection;
    }

    public ResultSet getResultSet(String sqlString) {
        try {
            ResultSet result = getStatement().executeQuery(sqlString);
            return result;
        } catch (Exception e) {
            System.out.println("error loading NeqSimbataBase " + e.toString());
        }
        return null;
    }

    public void execute(String sqlString) {
        try {
            if (databaseConnection == null) {
                databaseConnection = this.openConnection();
                setStatement(databaseConnection.createStatement());
            }
            getStatement().execute(sqlString);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error in NeqSimDataBase " + e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
    }

    public static String getDataBaseType() {
        return dataBaseType;
    }

    public static void setDataBaseType(String aDataBaseType) {
        dataBaseType = aDataBaseType;
        try {
            if (dataBaseType.equals("MSAccess")) {
                Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").newInstance();
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
            System.out.println("error loading database driver.. " + ex.toString());
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
        NeqSimDataBase.dataBasePathMSAccess
                = "C:/programming/NeqSimSourceCode/java/neqsim/data/NeqSimDatabase.mdb";
        NeqSimDataBase.dataBaseType
                = "MSAccessUCanAccess";

        neqsim.util.database.NeqSimDataBase.setDataBaseType("mySQL");
        neqsim.util.database.NeqSimDataBase.setConnectionString("jdbc:mysql://tr-w33:3306/neqsimthermodatabase");
        neqsim.util.database.NeqSimDataBase.setUsername("remote");
        neqsim.util.database.NeqSimDataBase.setPassword("remote");

        NeqSimDataBase database
                = new NeqSimDataBase();

        ResultSet dataSet
                = database
                        .getResultSet("SELECT * FROM COMP WHERE NAME='methane'");

        try {
            dataSet
                    .next();
            System.out
                    .println("dataset " + dataSet
                            .getString("molarmass"));

        } catch (Exception e) {
            System.out
                    .println("failed " + e
                            .toString());
        }

    }
}
