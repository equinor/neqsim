package neqsim.util.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Tests the legacy fluid database wrapper and its inherited connection lifecycle. */
public class NeqSimFluidDataBaseTest {
  private static final String H2_URL = "jdbc:h2:mem:fluid_" + UUID.randomUUID().toString().replace("-", "");

  /** Verifies the fluid wrapper initializes inherited JDBC resources from a concrete connection implementation. */
  @Test
  void initializesAndUsesInheritedDatabaseLifecycle() throws Exception {
    int previousNumber = NeqSimFluidDataBase.numb;
    boolean previousOnlineSetting = NeqSimFluidDataBase.useOnlineBase;
    NeqSimFluidDataBase.numb = 1;
    NeqSimFluidDataBase.useOnlineBase = false;
    try {
      NeqSimFluidDataBase database = new EmbeddedFluidDatabase();
      try (Connection connection = database.getConnection()) {
        Assertions.assertTrue(connection.isValid(1));
        database.execute("CREATE TABLE fluid_test (property_value INTEGER)");
        database.execute("INSERT INTO fluid_test VALUES (99)");
        try (ResultSet result = database.getResultSet("SELECT property_value FROM fluid_test")) {
          Assertions.assertTrue(result.next());
          Assertions.assertEquals(99, result.getInt(1));
        }
      }
    } finally {
      NeqSimFluidDataBase.numb = previousNumber;
      NeqSimFluidDataBase.useOnlineBase = previousOnlineSetting;
    }
  }

  /** Embedded connection fixture for the legacy fluid database wrapper. */
  private static class EmbeddedFluidDatabase extends NeqSimFluidDataBase {
    /** {@inheritDoc} */
    @Override
    public Connection openConnection(String database) throws SQLException {
      return DriverManager.getConnection(H2_URL, "sa", "");
    }
  }

  @Disabled("Requires a locally registered FluidDatabase source")
  @Test
  void testMain() throws Exception {
    NeqSimFluidDataBase database = new NeqSimFluidDataBase();
    try (Connection connection = database.getConnection();
        ResultSet dataSet = database.getResultSet("SELECT * FROM comp where name='water'")) {
      Assertions.assertTrue(connection.isValid(1));
      Assertions.assertTrue(dataSet.next());
    }
  }
}
