package neqsim.util.database;

import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NeqSimFluidDataBaseTest {
  Logger logger = LogManager.getFormatterLogger(NeqSimFluidDataBaseTest.class);

  @Disabled
  @Test
  void testMain() {
    boolean failed = true;
    NeqSimFluidDataBase database = new NeqSimFluidDataBase();
    try (ResultSet dataSet =
        database.getResultSet("FluidDatabase", "SELECT * FROM comp where name='water'")) {
      dataSet.next();
      System.out.println("dataset " + dataSet.getString("molarmass"));
      failed = false;
    } catch (Exception ex) {
      System.out.println("failed");
    }
    Assertions.assertFalse(failed, "Failed getting properties from FluidDatabase");
  }
}
