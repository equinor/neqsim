package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

public class NeqSimDataBaseTest extends NeqSimTest {
  @Test
  void testHasComponent() {
    assertTrue(neqsim.util.database.NeqSimDataBase.hasComponent("methane"),
        "Could not load component methane");
  }

  @Test
  void testUpdateTable() {
    neqsim.util.database.NeqSimDataBase.updateTable("COMP",
        "classpath:/data/COMP.csv");
  }

  @Test
    void testMain() {
      boolean failed = true;
      NeqSimDataBase.initH2DatabaseFromCSVfiles();
      boolean testHasMethane = NeqSimDataBase.hasComponent("methane");
      NeqSimDataBase.updateTable("COMP", "/workspaces/neqsim/src/main/resources/data/COMP.csv");
      double molmass = 0.0;
      try (NeqSimDataBase database = new NeqSimDataBase();
          ResultSet dataSet = database.getResultSet("SELECT * FROM comp WHERE NAME='methane'")) {
        dataSet.next();
        molmass = Double.valueOf(dataSet.getString("molarmass"));
        dataSet.close();
        failed = false;
      } catch (Exception ex) {
        System.out.println("DB test failed ");
      }
      Assertions.assertTrue(testHasMethane, "Methane component found in database");
      assertEquals(16.04, molmass, 0.1);
      Assertions.assertFalse(failed, "Failed getting data from NeqsimDataBase");
    }
}
