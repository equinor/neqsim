package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
    //neqsim.util.database.NeqSimDataBase.updateTable("COMP",
    //    "/workspaces/neqsim/src/main/resources/data/COMP.csv");
  }
}
