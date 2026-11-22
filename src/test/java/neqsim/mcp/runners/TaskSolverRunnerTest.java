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
  void testSolvePvtTaskRoutesSharedFluid() {
    JsonObject fluid = new JsonObject();
    fluid.addProperty("model", "PR");
    JsonObject components = new JsonObject();
    components.addProperty("methane", 0.70);
    components.addProperty("ethane", 0.10);
    components.addProperty("propane", 0.05);
    components.addProperty("n-heptane", 0.15);
    fluid.add("components", components);

    JsonObject parameters = new JsonObject();
    parameters.addProperty("experiment", "saturationPressure");

    JsonObject request = new JsonObject();
    request.addProperty("task", "Run a PVT saturation pressure analysis");
    request.add("fluid", fluid);
    request.add("parameters", parameters);
    request.addProperty("validate", false);

    JsonObject result = JsonParser.parseString(TaskSolverRunner.solveTask(request.toString())).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertTrue(result.get("success").getAsBoolean());
    assertEquals("pvt", result.get("taskType").getAsString());
    assertEquals(1, result.get("totalSteps").getAsInt());
    assertEquals(1, result.get("completedSteps").getAsInt());

    JsonObject step = result.getAsJsonArray("stepResults").get(0).getAsJsonObject();
    assertEquals("pvt_study", step.get("step").getAsString());
    assertEquals("pvt", step.get("runner").getAsString());
    assertTrue(step.get("success").getAsBoolean());
    assertEquals("success", step.getAsJsonObject("output").get("status").getAsString());
    assertTrue(
        result.getAsJsonObject("combinedData").getAsJsonObject("fluid").getAsJsonObject("components").has("methane"));
    assertTrue(result.getAsJsonObject("combinedData").has("pvt_study_result"));
  }

  @Test
  void testSolveCompressionTaskStopsOnRequiredFailure() {
    JsonObject request = new JsonObject();
    request.addProperty("task", "Design two-stage compression");
    request.add("parameters", new JsonObject());

    JsonObject result = JsonParser.parseString(TaskSolverRunner.solveTask(request.toString())).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertFalse(result.get("success").getAsBoolean());
    assertEquals("compression", result.get("taskType").getAsString());
    assertEquals(3, result.get("totalSteps").getAsInt());
    assertEquals(1, result.get("completedSteps").getAsInt());
    assertEquals("flash_feed", result.getAsJsonArray("stepResults").get(0).getAsJsonObject().get("step").getAsString());
  }

  @Test
  void testSolveRejectsMissingTask() {
    JsonObject result = JsonParser.parseString(TaskSolverRunner.solveTask("{}")).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertEquals("MISSING_TASK", result.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testSolveRejectsBlankOrMalformedTask() {
    JsonObject blank = JsonParser.parseString(TaskSolverRunner.solveTask("{\"task\":\"   \"}")).getAsJsonObject();
    assertEquals("MISSING_TASK", blank.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());

    JsonObject malformed = JsonParser.parseString(TaskSolverRunner.solveTask("{")).getAsJsonObject();
    assertEquals("TASK_ERROR", malformed.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testSolveRejectsUnsupportedTask() {
    JsonObject request = new JsonObject();
    request.addProperty("task", "Write a poem about offshore weather");

    JsonObject result = JsonParser.parseString(TaskSolverRunner.solveTask(request.toString())).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertEquals("UNSUPPORTED_TASK",
        result.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testSolveFailureUsesErrorEnvelope() {
    JsonObject request = new JsonObject();
    request.addProperty("task", "Run a PVT analysis");
    request.addProperty("validate", false);

    JsonObject result = JsonParser.parseString(TaskSolverRunner.solveTask(request.toString())).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertFalse(result.get("success").getAsBoolean());
    assertEquals("TASK_STEP_FAILED",
        result.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
    assertEquals("MISSING_EXPERIMENT",
        result.getAsJsonArray("errors").get(0).getAsJsonObject().get("causeCode").getAsString());
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
