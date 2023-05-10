package neqsim.util.database;

import java.sql.ResultSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class AspenIP21DatabaseTest {
  @Disabled
  @Test
  void testMain() {
    boolean failed = true;
    AspenIP21Database database = new AspenIP21Database();
    try (ResultSet dataSet = database.getResultSet("Karsto", "....'")) {
      while (dataSet.next()) {
        System.out.println("dataset " + dataSet.getString(4));
        System.out.println("dataset value " + dataSet.getDouble("..."));
      }
      failed = false;
    } catch (Exception ex) {
      System.out.println("failed ");
    }
    Assertions.assertFalse(failed, "Failed getting properties from FluidDatabase");
  }
}
