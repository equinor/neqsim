package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link BioprocessRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class BioprocessRunnerTest {

  @Test
  void testAnaerobicDigestion() {
    String result = BioprocessRunner.run(ExampleCatalog.bioprocessAnaerobicDigestion());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(),
        "Anaerobic digestion failed: " + result);
  }

  @Test
  void testGasification() {
    String result = BioprocessRunner.run(ExampleCatalog.bioprocessGasification());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Gasification failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = BioprocessRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
