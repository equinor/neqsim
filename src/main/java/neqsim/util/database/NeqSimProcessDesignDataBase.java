package neqsim.util.database;

import java.sql.Connection;
import java.sql.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * NeqSimProcessDesignDataBase class.
 * </p>
 *
 * @author Even Solbraa
 * @version June 2023
 */
public class NeqSimProcessDesignDataBase extends NeqSimDataBase {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqSimProcessDesignDataBase.class);

  /** Constant <code>dataBasePath=""</code>. */
  public static String dataBasePath = "";

  private static boolean createTemporaryTables = false;

  private static String username = "remote";
  private static String password = "remote";

  // Default databasetype
  private static String dataBaseType = "H2fromCSV";
  private static String connectionString = "jdbc:h2:mem:neqsimprocessdesigndatabase";
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
  public NeqSimProcessDesignDataBase() {
    // Fill tables from csv-files if not initialized and not currently being initialized.
    if (dataBaseType == "H2fromCSV" && !h2IsInitialized && !h2IsInitalizing) {
      initH2DatabaseFromCSVfiles();
    }
    setDataBaseType(dataBaseType);

    try {
      databaseConnection = this.openConnection();
      statement = databaseConnection.createStatement();
    } catch (Exception ex) {
      logger.error("SQLException ", ex);
      throw new RuntimeException(ex);
    }
  }

  /** {@inheritDoc} */
  public static void updateTable(String tableName) {
    updateTable(tableName, "designdata/" + tableName + ".csv");
  }

  /**
   * <p>
   * initH2DatabaseFromCSVfiles.
   * </p>
   */
  public static void initH2DatabaseFromCSVfiles() {
    h2IsInitalizing = true;
    neqsim.util.database.NeqSimProcessDesignDataBase.connectionString =
        "jdbc:h2:mem:neqsimprocessdesigndatabase;DB_CLOSE_DELAY=-1";
    neqsim.util.database.NeqSimProcessDesignDataBase.dataBaseType = "H2";

    try {
      updateTable("TORG");
      updateTable("TechnicalRequirements_Process");
      updateTable("TechnicalRequirements_Piping");
      updateTable("TechnicalRequirements_Material");
      updateTable("TechnicalRequirements_Mechanical");
      updateTable("Packing");
      updateTable("MaterialPipeProperties");
      updateTable("MaterialPlateProperties");
      updateTable("Fittings");

      h2IsInitialized = true;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
