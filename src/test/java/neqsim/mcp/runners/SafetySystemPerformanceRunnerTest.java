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
 * Tests for {@link SafetySystemPerformanceRunner}.
 */
class SafetySystemPerformanceRunnerTest {

  /**
   * Verifies that the catalog example runs and includes report templates.
   */
  @Test
  void testCatalogExampleRuns() {
    String result = SafetySystemPerformanceRunner
        .run(ExampleCatalog.getExample("safety", "safety-system-performance"));
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    assertEquals("PASS_WITH_WARNINGS",
        obj.getAsJsonObject("summary").get("overallVerdict").getAsString());
    assertTrue(obj.getAsJsonObject("performanceReport").has("assessments"));
    assertTrue(obj.getAsJsonObject("standardsTemplates").has("NORSOK-S-001"));
    assertTrue(obj.getAsJsonObject("stidExtractionTemplates").has("causeAndEffect"));
  }

  /**
   * Verifies that invalid input reports an error envelope.
   */
  @Test
  void testInvalidJsonReturnsError() {
    JsonObject obj =
        JsonParser.parseString(SafetySystemPerformanceRunner.run("not-json")).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  /**
   * Verifies that schemas and examples are discoverable through catalogs.
   */
  @Test
  void testCatalogDiscovery() {
    assertTrue(ExampleCatalog.getCatalogJson().contains("safety-system-performance"));
    assertNotNull(ExampleCatalog.getExample("safety", "safety-system-performance"));
    assertTrue(SchemaCatalog.getToolNames().contains("run_safety_system_performance"));
    assertNotNull(SchemaCatalog.getSchema("run_safety_system_performance", "input"));
    assertNotNull(SchemaCatalog.getSchema("run_safety_system_performance", "output"));
  }
}
