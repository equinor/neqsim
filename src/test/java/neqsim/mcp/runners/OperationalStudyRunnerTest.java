package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link OperationalStudyRunner}.
 *
 * @author ESOL
 * @version 1.0
 */
class OperationalStudyRunnerTest {

  /**
   * Verifies the schema discovery action.
   */
  @Test
  void getSchemaReturnsOperationalActions() {
    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(
      "{\"action\":\"getSchema\"}")).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertEquals("runOperationalStudy", result.get("tool").getAsString());
    assertTrue(result.getAsJsonArray("actions").size() >= 5);
  }

  /**
   * Verifies that field data can be applied through an automation address binding.
   */
  @Test
  void applyFieldDataWritesAutomationBinding() {
    String json = "{" + "\"action\":\"applyFieldData\"," + processJsonField() + ","
      + "\"tagBindings\":[{" + "\"logicalTag\":\"outlet_valve_position\","
      + "\"automationAddress\":\"Outlet Valve.percentValveOpening\","
      + "\"unit\":\"%\",\"role\":\"INPUT\"}],"
        + "\"fieldData\":{\"outlet_valve_position\":35.0}}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals(35.0, result.getAsJsonObject("applied").get("outlet_valve_position")
        .getAsDouble(), 1.0e-10);
    assertEquals(35.0, result.getAsJsonObject("modelValues").get("outlet_valve_position")
        .getAsDouble(), 1.0e-10);
  }

  /**
   * Verifies that operational scenarios execute valve and automation actions.
   */
  @Test
  void runScenarioExecutesValveAndAutomationActions() {
    String json = "{" + "\"action\":\"runScenario\","
        + "\"scenarioName\":\"partly close outlet\"," + processJsonField() + ","
        + "\"actions\":["
        + "{\"type\":\"SET_VALVE_OPENING\",\"target\":\"Outlet Valve\",\"value\":15.0},"
        + "{\"type\":\"SET_VARIABLE\",\"target\":\"Outlet Valve.outletPressure\","
        + "\"value\":45.0,\"unit\":\"bara\"},"
        + "{\"type\":\"RUN_STEADY_STATE\"}]}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertTrue(result.get("successful").getAsBoolean(), result.toString());
    JsonObject scenarioResult = result.getAsJsonObject("scenarioResult");
    assertEquals(15.0, scenarioResult.getAsJsonObject("afterValues")
        .get("Outlet Valve.percentValveOpening").getAsDouble(), 1.0e-10);
  }

  /**
   * Verifies controller-response screening output.
   */
  @Test
  void evaluateControllerResponseReturnsRecommendation() {
    String json = "{" + "\"action\":\"evaluateControllerResponse\","
        + "\"controllerName\":\"LC-001\",\"setPoint\":1.0,"
        + "\"timeSeconds\":[0.0,10.0,20.0,30.0,40.0,50.0],"
        + "\"processValue\":[0.0,0.55,0.82,0.95,0.99,1.0],"
        + "\"controllerOutput\":[40.0,55.0,58.0,53.0,50.0,50.0],"
        + "\"outputMin\":0.0,\"outputMax\":100.0,\"settlingTolerance\":0.05}";

    JsonObject result = JsonParser.parseString(OperationalStudyRunner.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    JsonObject tuning = result.getAsJsonObject("controllerTuning");
    assertEquals("ACCEPTABLE_SCREENING_RESULT", tuning.get("recommendation").getAsString());
  }

  /**
   * Creates the process JSON field used by the tests.
   *
   * @return a JSON fragment with the processJson field
   */
  private String processJsonField() {
    return "\"processJson\":{" + "\"fluid\":{" + "\"model\":\"SRK\","
        + "\"temperature\":298.15," + "\"pressure\":70.0,"
        + "\"mixingRule\":\"classic\"," + "\"components\":{\"methane\":0.90,"
        + "\"ethane\":0.10}}," + "\"process\":["
        + "{\"type\":\"Stream\",\"name\":\"feed\","
        + "\"properties\":{\"flowRate\":[10000.0,\"kg/hr\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"Separator\",\"inlet\":\"feed\"},"
        + "{\"type\":\"valve\",\"name\":\"Outlet Valve\","
        + "\"inlet\":\"Separator.gasOut\","
        + "\"properties\":{\"outletPressure\":50.0}}]}";
  }
}