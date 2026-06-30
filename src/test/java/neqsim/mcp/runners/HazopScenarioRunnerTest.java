package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link HazopScenarioRunner}.
 */
class HazopScenarioRunnerTest {

  /**
   * Verifies that the catalog example quantifies a single MORE TEMPERATURE deviation on a compressor and returns a
   * finding that carries the limit basis.
   */
  @Test
  void testCatalogExampleQuantifiesDeviationWithLimitBasis() {
    String result = HazopScenarioRunner.run(ExampleCatalog.getExample("safety", "hazop-scenario"));
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("ok", obj.get("status").getAsString());
    assertEquals(HazopScenarioRunner.SCHEMA_VERSION, obj.get("schemaVersion").getAsString());
    assertTrue(obj.get("matchCount").getAsInt() >= 1);

    JsonArray findings = obj.getAsJsonArray("findings");
    assertTrue(findings.size() >= 1);
    JsonObject finding = findings.get(0).getAsJsonObject();
    assertEquals("MORE", finding.get("guideWord").getAsString());
    assertEquals("TEMPERATURE", finding.get("parameter").getAsString());
    assertNotNull(finding.get("limitBasis"));
    assertFalse(finding.get("limitBasis").getAsString().trim().isEmpty());
    assertNotNull(finding.get("standardReference"));
    assertNotNull(obj.get("limitsPolicy"));
  }

  /**
   * Verifies that a non-matching node filter yields zero findings and an explanatory note.
   */
  @Test
  void testNodeFilterExcludesNonMatchingUnits() {
    String json = "{\"nodeTag\":\"does-not-exist\",\"guideWord\":\"MORE\",\"parameter\":\"TEMPERATURE\","
        + "\"process\":{\"fluid\":{\"model\":\"SRK\",\"temperature\":298.15,\"pressure\":10.0,"
        + "\"mixingRule\":\"classic\",\"components\":{\"methane\":0.90,\"ethane\":0.07,\"propane\":0.03}},"
        + "\"process\":[{\"type\":\"Stream\",\"name\":\"feed\",\"properties\":{\"flowRate\":[5000.0,\"kg/hr\"]}},"
        + "{\"type\":\"Compressor\",\"name\":\"2nd Stage\",\"inlet\":\"feed\","
        + "\"properties\":{\"outletPressure\":[80.0,\"bara\"]}}]}}";
    JsonObject obj = JsonParser.parseString(HazopScenarioRunner.run(json)).getAsJsonObject();

    assertEquals("ok", obj.get("status").getAsString());
    assertEquals(0, obj.get("matchCount").getAsInt());
    assertNotNull(obj.get("note"));
  }

  /**
   * Verifies that empty input returns an error envelope.
   */
  @Test
  void testEmptyInputReturnsError() {
    JsonObject obj = JsonParser.parseString(HazopScenarioRunner.run("")).getAsJsonObject();

    assertEquals("error", obj.get("status").getAsString());
  }

  /**
   * Verifies that the scenario example is discoverable through the catalog.
   */
  @Test
  void testCatalogDiscovery() {
    assertTrue(ExampleCatalog.getExampleNames("safety").contains("hazop-scenario"));
    assertNotNull(ExampleCatalog.getExample("safety", "hazop-scenario"));
  }
}
