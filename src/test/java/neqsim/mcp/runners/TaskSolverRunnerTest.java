package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
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
    String json = "{" + "\"task\": \"Design 2-stage compression from 10 to 80 bara\"," + "\"fluid\": {"
        + "  \"model\": \"SRK\"," + "  \"components\": {\"methane\": 0.90, \"ethane\": 0.07, \"propane\": 0.03}" + "},"
        + "\"parameters\": {" + "  \"outletPressure\": 80.0," + "  \"stages\": 2" + "}" + "}";

    String result = TaskSolverRunner.solveTask(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue(obj.has("success") || obj.has("status"), "Should have success or status field: " + result);
  }

  @Test
  void testComposeWorkflow() {
    String json = "{" + "\"workflow\": \"simple-flash-test\"," + "\"fluid\": {" + "  \"model\": \"SRK\","
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "}," + "\"steps\": ["
        + "  {\"runner\": \"flash\", \"name\": \"base-flash\", \"input\": {"
        + "    \"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
        + "    \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}," + "    \"flashType\": \"TP\"" + "  }}" + "]" + "}";

    String result = TaskSolverRunner.composeWorkflow(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue(obj.has("workflow") || obj.has("status"), "Should have workflow or status field: " + result);
  }

  @Test
  void testComposeWorkflowRunsSharedFluidFlash() {
    JsonObject fluid = new JsonObject();
    fluid.addProperty("model", "SRK");
    JsonObject components = new JsonObject();
    components.addProperty("methane", 1.0);
    fluid.add("components", components);

    JsonObject stepInput = new JsonObject();
    JsonObject temperature = new JsonObject();
    temperature.addProperty("value", 25.0);
    temperature.addProperty("unit", "C");
    stepInput.add("temperature", temperature);
    JsonObject pressure = new JsonObject();
    pressure.addProperty("value", 50.0);
    pressure.addProperty("unit", "bara");
    stepInput.add("pressure", pressure);

    JsonObject step = new JsonObject();
    step.addProperty("runner", "flash");
    step.addProperty("name", "feed_flash");
    step.add("input", stepInput);
    JsonArray steps = new JsonArray();
    steps.add(step);

    JsonObject request = new JsonObject();
    request.addProperty("workflow", "bounded-flash");
    request.add("fluid", fluid);
    request.add("steps", steps);

    JsonObject result = JsonParser.parseString(TaskSolverRunner.composeWorkflow(request.toString())).getAsJsonObject();
    assertEquals("bounded-flash", result.get("workflow").getAsString());
    assertEquals(1, result.get("totalSteps").getAsInt());
    assertEquals(1, result.get("completedSteps").getAsInt());
    assertTrue(result.get("success").getAsBoolean());

    JsonObject completed = result.getAsJsonArray("steps").get(0).getAsJsonObject();
    assertEquals("feed_flash", completed.get("step").getAsString());
    assertEquals("flash", completed.get("runner").getAsString());
    assertTrue(completed.get("success").getAsBoolean());
    assertEquals("success", completed.getAsJsonObject("output").get("status").getAsString());
    assertTrue(result.getAsJsonObject("combinedData").has("fluid"));
    assertTrue(result.getAsJsonObject("combinedData").has("feed_flash_result"));
  }

  @Test
  void testComposeWorkflowStopsOnUnknownRunner() {
    JsonArray steps = new JsonArray();
    JsonObject blocked = new JsonObject();
    blocked.addProperty("runner", "arbitrary");
    blocked.addProperty("name", "blocked");
    blocked.add("input", new JsonObject());
    steps.add(blocked);

    JsonObject skipped = new JsonObject();
    skipped.addProperty("runner", "flash");
    skipped.addProperty("name", "skipped");
    skipped.add("input", new JsonObject());
    steps.add(skipped);

    JsonObject request = new JsonObject();
    request.addProperty("workflow", "fail-closed");
    request.add("steps", steps);

    JsonObject result = JsonParser.parseString(TaskSolverRunner.composeWorkflow(request.toString())).getAsJsonObject();
    assertEquals(2, result.get("totalSteps").getAsInt());
    assertEquals(1, result.get("completedSteps").getAsInt());
    assertFalse(result.get("success").getAsBoolean());

    JsonObject completed = result.getAsJsonArray("steps").get(0).getAsJsonObject();
    assertFalse(completed.get("success").getAsBoolean());
    JsonObject output = completed.getAsJsonObject("output");
    assertEquals("error", output.get("status").getAsString());
    assertEquals("UNKNOWN_RUNNER", output.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
    assertFalse(result.getAsJsonObject("combinedData").has("skipped_result"));
  }

  @Test
  void testComposeWorkflowRejectsMissingSteps() {
    JsonObject request = new JsonObject();
    request.addProperty("workflow", "missing-steps");

    JsonObject result = JsonParser.parseString(TaskSolverRunner.composeWorkflow(request.toString())).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertEquals("MISSING_STEPS", result.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testComposeWorkflowRejectsMalformedStepInput() {
    JsonObject step = new JsonObject();
    step.addProperty("runner", "flash");
    step.addProperty("name", "malformed");
    step.addProperty("input", "not-an-object");
    JsonArray steps = new JsonArray();
    steps.add(step);

    JsonObject request = new JsonObject();
    request.addProperty("workflow", "malformed-step");
    request.add("steps", steps);

    JsonObject result = JsonParser.parseString(TaskSolverRunner.composeWorkflow(request.toString())).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertEquals("WORKFLOW_ERROR", result.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testNullInput() {
    String result = TaskSolverRunner.solveTask(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
