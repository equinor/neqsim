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

  @Test
  void testRun_processModelAreas() {
    String result = ProcessRunner.run(processModelJson());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertEquals("json-process-model", root.get("processModelName").getAsString());
    assertEquals(2, root.get("areaCount").getAsInt());
    assertTrue(root.has("areas"));
    assertTrue(root.has("report"));
    assertTrue(root.has("convergenceSummary"));
    assertTrue(root.get("convergenceSummary").getAsString().contains("ProcessModel"));
  }

  @Test
  void testRun_processModelHonorsExecutionSettings() {
    String json = processModelJson().replace("{\"areas\":",
        "{\"runStep\": true,"
            + "\"maxIterations\": 7,\"flowTolerance\": 0.02,\"temperatureTolerance\": 0.03,"
            + "\"pressureTolerance\": 0.04,\"areas\":");

    String result = ProcessRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.getAsJsonObject("provenance").get("converged").getAsBoolean());
    assertTrue(root.get("convergenceSummary").getAsString().contains("Iterations: 1 / 7"));
    assertTrue(root.get("convergenceSummary").getAsString().contains("Flow rate:    0.00e+00"));
  }

  @Test
  void testValidateAndRun_processModelAreas() {
    String result = ProcessRunner.validateAndRun(processModelJson());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertEquals(2, root.get("areaCount").getAsInt());
  }

  @Test
  void testValidateAndRun_processWithExpander() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15,"
        + "  \"pressure\": 60.0," + "  \"components\": {\"methane\": 1.0}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [5000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Expander\", \"name\": \"Expander-1\", \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [20.0, \"bara\"]}}" + "]" + "}";

    String result = ProcessRunner.validateAndRun(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
  }

  private static String processModelJson() {
    String fluid = "\"fluid\": {" + "\"model\": \"SRK\"," + "\"temperature\": 298.15,"
        + "\"pressure\": 50.0," + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}";
    String separation =
        "{" + fluid + "," + "\"process\": [" + "{\"type\": \"Stream\", \"name\": \"feed\","
            + "\"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
            + "{\"type\": \"Separator\", \"name\": \"Sep\", \"inlet\": \"feed\"}" + "]}";
    String compression =
        "{" + fluid + "," + "\"process\": [" + "{\"type\": \"Stream\", \"name\": \"compFeed\","
            + "\"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
            + "{\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"compFeed\","
            + "\"properties\": {\"outletPressure\": [80.0, \"bara\"]}}" + "]}";
    String interAreaLinks = "\"interAreaLinks\": [{\"sourceArea\": \"separation\","
        + "\"source\": \"Sep.gasOut\", \"targetArea\": \"compression\","
        + "\"targetUnit\": \"Comp\", \"targetInletIndex\": 0}]";
    return "{\"areas\": {\"separation\": " + separation + ", \"compression\": " + compression + "},"
        + interAreaLinks + "}";
  }
}
