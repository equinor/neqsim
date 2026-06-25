package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
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
        + "\"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}," + "\"mixingRule\": \"classic\"}}";

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

  /**
   * Creates a session containing a small compressor process and returns its session ID.
   *
   * @return the new session ID
   */
  private static String createCompressorSession() {
    String createJson = "{\"action\": \"create\", \"processJson\": {" + "\"fluid\": {"
        + "  \"model\": \"SRK\", \"temperature\": 298.15, \"pressure\": 50.0," + "  \"mixingRule\": \"classic\","
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"," + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Compressor\", \"name\": \"Compressor\", \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [100.0, \"bara\"]}}]}}";
    String createResult = SessionRunner.run(createJson);
    JsonObject obj = JsonParser.parseString(createResult).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Session create failed: " + createResult);
    return obj.get("sessionId").getAsString();
  }

  @Test
  void testSessionEvaluate() {
    String sessionId = createCompressorSession();
    String json = "{\"action\": \"evaluate\", \"sessionId\": \"" + sessionId + "\","
        + "\"setpoints\": {\"Compressor.outletPressure\": 120.0}," + "\"readbacks\": [\"Compressor.power\"],"
        + "\"setpointUnit\": \"bara\", \"readbackUnit\": \"kW\"}";
    String result = SessionRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "evaluate failed: " + result);
    assertTrue(obj.has("evaluation"), "Should return evaluation block");
    JsonObject eval = obj.getAsJsonObject("evaluation");
    assertTrue(eval.has("feasible"), "evaluation should expose feasible flag");
    assertTrue(eval.has("readbacks"), "evaluation should expose readbacks");
  }

  @Test
  void testSessionGetValues() {
    String sessionId = createCompressorSession();
    String json = "{\"action\": \"getValues\", \"sessionId\": \"" + sessionId + "\","
        + "\"addresses\": [\"Compressor.outletPressure\"], \"unit\": \"bara\"}";
    String result = SessionRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "getValues failed: " + result);
    assertTrue(obj.has("values"), "Should return values block");
    assertEquals(1, obj.get("requested").getAsInt());
  }

  @Test
  void testSessionSetValues() {
    String sessionId = createCompressorSession();
    String json = "{\"action\": \"setValues\", \"sessionId\": \"" + sessionId + "\","
        + "\"updates\": {\"Compressor.outletPressure\": 115.0}, \"unit\": \"bara\"," + "\"runAfter\": true}";
    String result = SessionRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "setValues failed: " + result);
    assertEquals(1, obj.get("requested").getAsInt());
    assertTrue(obj.get("reRan").getAsBoolean());
  }

  @Test
  void testSessionAdjustables() {
    String sessionId = createCompressorSession();
    String json = "{\"action\": \"adjustables\", \"sessionId\": \"" + sessionId + "\"}";
    String result = SessionRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "adjustables failed: " + result);
    assertTrue(obj.has("adjustableParameters"), "Should return adjustableParameters block");
  }
}
