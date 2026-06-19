package neqsim.util.database;

import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class AspenIP21DatabaseTest {
  private static final Logger logger = LogManager.getLogger(AspenIP21DatabaseTest.class);

  @Disabled
  @Test
  void testMain() {
    boolean failed = true;
    AspenIP21Database database = new AspenIP21Database();
    try (ResultSet dataSet = database.getResultSet("Karsto", "....'")) {
      while (dataSet.next()) {
	// logger.info("dataset " + dataSet.getString(4));
	// logger.info("dataset value " + dataSet.getDouble("..."));
      }
      failed = false;
    } catch (Exception ex) {
      logger.info("failed ");
    }
    Assertions.assertFalse(failed, "Failed getting properties from FluidDatabase");
  }
}
