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
public class NeqSimContractDataBase extends NeqSimDataBase {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqSimContractDataBase.class);

  /** Constant <code>dataBasePath=""</code>. */
  public static String dataBasePath = "";

  private static boolean createTemporaryTables = false;

  private static String username = "remote";
  private static String password = "remote";

  // Default databasetype
  private static String dataBaseType = "H2fromCSV";
  private static String connectionString = "jdbc:h2:mem:neqsimcontractdatabase";
  private static boolean h2IsInitialized = false;
  private static boolean h2IsInitalizing = false;

  private Statement statement = null;
  protected Connection databaseConnection = null;

  /**
   * <p>
   * Constructor for NeqSimDataBase.
   * </p>
   */
  public NeqSimContractDataBase() {
    // Fill tables from csv-files if not initialized and not currently being initialized.
    if ("H2fromCSV".equals(dataBaseType) && !h2IsInitialized && !h2IsInitalizing) {
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
    updateTable(tableName, "commercial/" + tableName + ".csv");
  }

  /**
   * <p>
   * initH2DatabaseFromCSVfiles.
   * </p>
   */
  public static void initH2DatabaseFromCSVfiles() {
    h2IsInitalizing = true;
    neqsim.util.database.NeqSimContractDataBase.connectionString =
        "jdbc:h2:mem:neqsimcontractdatabase;DB_CLOSE_DELAY=-1";
    neqsim.util.database.NeqSimContractDataBase.dataBaseType = "H2";

    try {
      updateTable("GASCONTRACTSPECIFICATIONS");

      h2IsInitialized = true;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
