package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link StreamingRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class StreamingRunnerTest {

  @Test
  void testListOperations() {
    String json = "{\"action\": \"list\"}";
    String result = StreamingRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    // list action returns count + operations, no top-level status
    assertTrue(obj.has("operations"), "Should list operations: " + result);
  }

  @Test
  void testPollNonexistent() {
    String json =
        "{\"action\": \"poll\", \"operationId\": \"nonexistent-id\"," + "\"lastIndex\": 0}";
    String result = StreamingRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testCancelNonexistent() {
    String json = "{\"action\": \"cancel\", \"operationId\": \"nonexistent-id\"}";
    String result = StreamingRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("not_found", obj.get("status").getAsString());
  }

  @Test
  void testNullInput() {
    String result = StreamingRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
