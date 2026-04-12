package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link SessionRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SessionRunnerTest {

  @Test
  void testCreateSession() {
    String json = "{\"action\": \"create\", \"fluid\": {"
        + "\"model\": \"SRK\", \"temperature\": 298.15, \"pressure\": 50.0,"
        + "\"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05},"
        + "\"mixingRule\": \"classic\"}}";

    String result = SessionRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Session create failed: " + result);
    assertTrue(obj.has("sessionId"), "Should return sessionId");
  }

  @Test
  void testListSessions() {
    String json = "{\"action\": \"list\"}";
    String result = SessionRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Session list failed: " + result);
  }

  @Test
  void testInvalidAction() {
    String json = "{\"action\": \"INVALID_ACTION\"}";
    String result = SessionRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testNullInput() {
    String result = SessionRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
