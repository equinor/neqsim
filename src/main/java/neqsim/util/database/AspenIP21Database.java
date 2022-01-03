package neqsim.util.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * <p>
 * AspenIP21Database class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AspenIP21Database
        implements neqsim.util.util.FileSystemSettings, java.io.Serializable {
    private static final long serialVersionUID = 1000;

    protected Connection databaseConnection = null;
    private static String dataBaseType = "Karsto";
    private Statement statement = null;

    /**
     * <p>
     * Constructor for AspenIP21Database.
     * </p>
     */
    public AspenIP21Database() {
        try {
            if (dataBaseType.equals("Karsto")) {
                Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").getDeclaredConstructor()
                        .newInstance();
            }
        } catch (Exception ex) {
            System.out.println("error in Online Karsto " + ex.toString());
            System.out.println("The database must be registered on the local DBMS to work.");
        }

        try {
            databaseConnection = this.openConnection("Karsto");
            setStatement(databaseConnection.createStatement());
        } catch (Exception ex) {
            System.out.println("SQLException " + ex.getMessage());

        }
    }

    /**
     * <p>
     * openConnection.
     * </p>
     *
     * @param database a {@link java.lang.String} object
     * @return a Connection object
     * @throws java.sql.SQLException if any.
     * @throws java.lang.ClassNotFoundException if any.
     */
    public Connection openConnection(String database) throws SQLException, ClassNotFoundException {
        javax.naming.InitialContext ctx = null;
        javax.sql.DataSource ds = null;

        try {
            return DriverManager.getConnection(".....");
        } catch (Exception ex) {
            System.out.println("SQLException " + ex.getMessage());
            System.out.println("error in Kaarsto DB " + ex.toString());
            System.out
                    .println("The Kaarsto database must be registered on the local DBMS to work.");
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
     * getResultSet.
     * </p>
     *
     * @param sqlString a {@link java.lang.String} object
     * @return a ResultSet object
     */
    public ResultSet getResultSet(String sqlString) {
        return this.getResultSet("Karsto", sqlString);
    }

    /**
     * <p>
     * getResultSet.
     * </p>
     *
     * @param database a {@link java.lang.String} object
     * @param sqlString a {@link java.lang.String} object
     * @return a ResultSet object
     */
    public ResultSet getResultSet(String database, String sqlString) {
        try {
            ResultSet result = getStatement().executeQuery(sqlString);
            return result;
        } catch (Exception e) {
            System.out.println("error in DB " + e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
        return null;
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
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        AspenIP21Database database = new AspenIP21Database();
        ResultSet dataSet = database.getResultSet("Karsto", "....'");
        try {
            while (dataSet.next()) {
                System.out.println("dataset " + dataSet.getString(4));
                System.out.println("dataset value " + dataSet.getDouble("..."));
            }
        } catch (Exception e) {
            System.out.println("failed " + e.toString());
        }
        System.out.println("ok");
    }
}
