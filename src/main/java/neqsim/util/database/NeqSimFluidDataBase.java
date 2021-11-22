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

/**
 *
 * @author esol
 * @version The database is used for storing fluid info and recreating a fluid it uses the database
 *          neqsimfluiddatabase for storing fluid information
 */
public class NeqSimFluidDataBase
        implements neqsim.util.util.FileSystemSettings, java.io.Serializable {
    private static final long serialVersionUID = 1000;

    static boolean started = false;
    protected Connection databaseConnection;
    public static boolean useOnlineBase = false;
    static int numb = 0;
    Statement statement = null;

    /** Creates new testPointbase */
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
        } catch (Exception e) {
            System.out.println("error in FluidDatabase " + e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
    }

    /** Creates new FluidDatabase */

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
                    .getConnection("jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ=" + dir
                            + "\\data\\" + database);
            // return DriverManager.getConnection("jdbc:odbc:FluidDatabase");
        }
    }

    public Connection getConnection() {
        return databaseConnection;
    }

    public ResultSet getResultSet(String database, String sqlString) {
        try {
            ResultSet result = statement.executeQuery(sqlString);
            return result;
        } catch (Exception e) {
            System.out.println("error in FluidDatabase " + e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
        return null;
    }

    public ResultSet getResultSet(String sqlString) {
        return this.getResultSet("FluidDatabase", sqlString);
    }

    public void execute(String sqlString) {
        try {
            if (databaseConnection == null) {
                databaseConnection = this.openConnection("FluidDatabase");
                statement = databaseConnection.createStatement();
            }
            statement.execute(sqlString);
        } catch (Exception e) {
            System.out.println("error in FluidDatabase " + e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
    }

    public static void main(String[] args) {
        NeqSimFluidDataBase database = new NeqSimFluidDataBase();
        ResultSet dataSet =
                database.getResultSet("FluidDatabase", "SELECT * FROM comp where name='water'");
        try {
            dataSet.next();
            System.out.println("dataset " + dataSet.getString("molarmass"));
        } catch (Exception e) {
            System.out.println("failed " + e.toString());
        }
        System.out.println("ok");
    }
}
