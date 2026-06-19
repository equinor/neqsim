package neqsim.util.database;

import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NeqSimFluidDataBaseTest {
  private static final Logger logger = LogManager.getLogger(NeqSimFluidDataBaseTest.class);

  @Disabled
  @Test
  void testMain() {
    boolean failed = true;
    NeqSimFluidDataBase database = new NeqSimFluidDataBase();
    try (ResultSet dataSet =
        database.getResultSet("FluidDatabase", "SELECT * FROM comp where name='water'")) {
      dataSet.next();
      // logger.info("dataset " + dataSet.getString("molarmass"));
      failed = false;
    } catch (Exception ex) {
      logger.info("failed");
    }
    Assertions.assertFalse(failed, "Failed getting properties from FluidDatabase");
  }
}
