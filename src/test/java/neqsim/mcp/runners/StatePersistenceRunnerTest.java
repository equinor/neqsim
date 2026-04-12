package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link StatePersistenceRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class StatePersistenceRunnerTest {

  @Test
  void testGetInfo() {
    String json = "{\"action\": \"getInfo\"}";
    String result = StatePersistenceRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Get info failed: " + result);
  }

  @Test
  void testListStates() {
    String json = "{\"action\": \"list\"}";
    String result = StatePersistenceRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "List states failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = StatePersistenceRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
