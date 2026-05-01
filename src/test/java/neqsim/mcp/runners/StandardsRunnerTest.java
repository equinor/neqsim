package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link StandardsRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class StandardsRunnerTest {

  @Test
  void testISO6976() {
    String result = StandardsRunner.run(ExampleCatalog.standardISO6976());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "ISO6976 failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = StandardsRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
