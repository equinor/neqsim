package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

public class NeqSimDataBaseTest extends NeqSimTest {
  Logger logger = LogManager.getFormatterLogger(NeqSimFluidDataBaseTest.class);

  @Test
  void testHasComponent() {
    assertTrue(neqsim.util.database.NeqSimDataBase.hasComponent("methane"),
        "Could not load component methane");
  }

  @Test
  void testReplaceTable() {
    neqsim.util.database.NeqSimDataBase.replaceTable("COMP", "src/main/resources/data/COMP.csv");
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
        () -> neqsim.util.database.NeqSimDataBase.replaceTable("COMP", "file_does_not_exist.csv"));
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: NeqSimDataBase:replaceTable - Input path - Resource file_does_not_exist.csv not found",
        thrown.getMessage());
  }

  @Test
  void testUpdateTable() {
    neqsim.util.database.NeqSimDataBase.updateTable("COMP");
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
        () -> neqsim.util.database.NeqSimDataBase.updateTable("COMP", "file_does_not_exist.csv"));
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: NeqSimDataBase:updateTable - Input path - Resource file_does_not_exist.csv not found",
        thrown.getMessage());
  }

  @Test
  void testMain() {
    boolean failed = true;
    // NeqSimDataBase.initH2DatabaseFromCSVfiles();
    double molmass = 0.0;
    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM comp WHERE NAME='methane'")) {
      dataSet.next();
      molmass = Double.valueOf(dataSet.getString("molarmass"));
      dataSet.close();
      failed = false;
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
    // Assertions.assertTrue(testHasMethane, "Methane component found in database");
    Assertions.assertEquals(16.04, molmass, 0.1);
    Assertions.assertFalse(failed, "Failed getting data from NeqsimDataBase");
  }

  @Test
  void testGetResultSetAfterClose() throws Exception {
    NeqSimDataBase database = new NeqSimDataBase();
    database.close();
    try (ResultSet rs = database.getResultSet("SELECT 1")) {
      assertTrue(rs.next(), "Expected result from reopened connection");
    }
  }
}
