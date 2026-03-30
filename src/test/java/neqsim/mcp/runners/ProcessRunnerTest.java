package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link ProcessRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ProcessRunnerTest {

  @Test
  void testRun_simpleProcess() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0," + "  \"mixingRule\": \"classic\","
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}" + "]" + "}";

    String result = ProcessRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.has("report"));
  }

  @Test
  void testRun_nullInput() {
    String result = ProcessRunner.run(null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("INPUT_ERROR",
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testRun_emptyInput() {
    String result = ProcessRunner.run("");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
  }

  @Test
  void testValidateAndRun_validProcess() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 50.0,"
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}" + "]" + "}";

    String result = ProcessRunner.validateAndRun(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  @Test
  void testValidateAndRun_invalidComponents() {
    String json = "{" + "\"fluid\": {" + "  \"components\": {\"fakey\": 1.0}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\"}" + "]" + "}";

    String result = ProcessRunner.validateAndRun(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("validation", root.get("phase").getAsString());
    assertTrue(root.has("validation"));
    assertFalse(root.getAsJsonObject("validation").get("valid").getAsBoolean());
  }

  @Test
  void testValidateAndRun_nullInput() {
    String result = ProcessRunner.validateAndRun(null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
  }

  @Test
  void testRun_processWithCompressor() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 20.0," + "  \"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [60.0, \"bara\"]}}" + "]" + "}";

    String result = ProcessRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }
}
