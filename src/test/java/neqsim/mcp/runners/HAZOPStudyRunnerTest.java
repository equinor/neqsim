package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;

/**
 * Tests for {@link HAZOPStudyRunner}.
 */
class HAZOPStudyRunnerTest {

  /**
   * Verifies that the catalog example produces simulation-backed HAZOP rows.
   */
  @Test
  void testCatalogExampleCreatesHazopRowsAndSimulationEvidence() {
    String result = HAZOPStudyRunner.run(ExampleCatalog.getExample("safety", "hazop-study"));
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    JsonObject summary = obj.getAsJsonObject("summary");
    assertTrue(summary.get("hazopRowCount").getAsInt() >= 2);
    assertTrue(summary.get("simulationRunCount").getAsInt() >= 2);
    assertTrue(obj.getAsJsonArray("hazopRows").size() >= 2);
    assertTrue(obj.getAsJsonArray("scenarioResults").size() >= 2);
    assertTrue(obj.getAsJsonObject("qualityGates").get("humanReviewRequired").getAsBoolean());
    assertTrue(obj.get("reportMarkdown").getAsString().contains("Simulation-backed HAZOP"));
    assertNotNull(obj.get("barrierRegisterHandoff"));
  }

  /**
   * Verifies the compact top-level process input form.
   */
  @Test
  void testTopLevelProcessInputRuns() {
    String json = "{\"studyId\":\"HAZOP-SIMPLE\"," + "\"runSimulations\":false,"
        + "\"failureModes\":[\"BLOCKED_OUTLET\"],"
        + "\"fluid\":{\"model\":\"SRK\",\"temperature\":298.15,\"pressure\":50.0,"
        + "\"mixingRule\":\"classic\",\"components\":{\"methane\":0.85,"
        + "\"ethane\":0.10,\"propane\":0.05}},"
        + "\"process\":[{\"type\":\"Stream\",\"name\":\"feed\","
        + "\"properties\":{\"flowRate\":[10000.0,\"kg/hr\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"HP Sep\",\"inlet\":\"feed\"}]}";
    JsonObject obj = JsonParser.parseString(HAZOPStudyRunner.run(json)).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.getAsJsonObject("summary").get("hazopRowCount").getAsInt() >= 1);
    assertEquals(0, obj.getAsJsonObject("summary").get("simulationRunCount").getAsInt());
  }

  /**
   * Verifies that invalid input returns an error envelope.
   */
  @Test
  void testInvalidInputReturnsError() {
    JsonObject obj = JsonParser.parseString(HAZOPStudyRunner.run("not-json")).getAsJsonObject();

    assertEquals("error", obj.get("status").getAsString());
  }

  /**
   * Verifies that schemas and examples are discoverable through catalogs.
   */
  @Test
  void testCatalogDiscovery() {
    assertTrue(ExampleCatalog.getExampleNames("safety").contains("hazop-study"));
    assertNotNull(ExampleCatalog.getExample("safety", "hazop-study"));
    assertTrue(SchemaCatalog.getToolNames().contains("run_hazop"));
    assertNotNull(SchemaCatalog.getSchema("run_hazop", "input"));
    assertNotNull(SchemaCatalog.getSchema("run_hazop", "output"));
  }
}
