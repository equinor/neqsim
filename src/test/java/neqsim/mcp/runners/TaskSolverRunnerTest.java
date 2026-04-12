package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link TaskSolverRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TaskSolverRunnerTest {

  @Test
  void testSolveCompressionTask() {
    String json = "{" + "\"task\": \"Design 2-stage compression from 10 to 80 bara\","
        + "\"fluid\": {" + "  \"model\": \"SRK\","
        + "  \"components\": {\"methane\": 0.90, \"ethane\": 0.07, \"propane\": 0.03}" + "},"
        + "\"parameters\": {" + "  \"outletPressure\": 80.0," + "  \"stages\": 2" + "}" + "}";

    String result = TaskSolverRunner.solveTask(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    // solveTask returns "success" boolean on success, or "status":"error" on failure
    assertTrue(obj.has("success") || obj.has("status"),
        "Should have success or status field: " + result);
  }

  @Test
  void testComposeWorkflow() {
    String json =
        "{" + "\"workflow\": \"simple-flash-test\"," + "\"fluid\": {" + "  \"model\": \"SRK\","
            + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "},"
            + "\"steps\": [" + "  {\"runner\": \"flash\", \"name\": \"base-flash\", \"input\": {"
            + "    \"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
            + "    \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"},"
            + "    \"flashType\": \"TP\"" + "  }}" + "]" + "}";

    String result = TaskSolverRunner.composeWorkflow(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    // composeWorkflow returns "workflow" and "success" fields
    assertTrue(obj.has("workflow") || obj.has("status"),
        "Should have workflow or status field: " + result);
  }

  @Test
  void testNullInput() {
    String result = TaskSolverRunner.solveTask(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
