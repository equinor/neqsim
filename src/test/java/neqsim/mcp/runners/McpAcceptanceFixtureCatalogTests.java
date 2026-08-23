package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests the public synthetic four-scale MCP acceptance-fixture catalog. */
class McpAcceptanceFixtureCatalogTests {

  @Test
  void testCatalogDefinesExactlyFourDistinctAcceptanceScales() {
    JsonObject catalog = McpAcceptanceFixtureCatalog.build();
    assertTrue(catalog.get("complete").getAsBoolean());
    assertEquals(4, catalog.get("fixtureCount").getAsInt());
    assertEquals("FIXTURES_DEFINED_BASELINES_PENDING", catalog.get("executionEvidenceStatus").getAsString());

    Set<String> scales = new HashSet<String>();
    for (com.google.gson.JsonElement element : catalog.getAsJsonArray("fixtures")) {
      JsonObject fixture = element.getAsJsonObject();
      scales.add(fixture.get("scale").getAsString());
      assertTrue(fixture.get("publicSynthetic").getAsBoolean());
      assertTrue(fixture.getAsJsonArray("acceptanceChecks").size() >= 3);
      assertFalse(fixture.get("boundary").getAsString().trim().isEmpty());
    }
    assertTrue(scales.contains("SINGLE_CALCULATION"));
    assertTrue(scales.contains("SMALL_TRAIN"));
    assertTrue(scales.contains("MULTI_AREA"));
    assertTrue(scales.contains("LARGE_FACILITY"));
  }

  @Test
  void testSingleCalculationFixtureExecutesThroughFlashRunner() {
    JsonObject result = JsonParser.parseString(FlashRunner.run(McpAcceptanceFixtureCatalog.singleCalculationInput()))
        .getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertTrue(result.has("provenance"));
    assertTrue(result.has("validation"));
  }

  @Test
  void testSmallRecycleTrainExecutesThroughProcessRunner() {
    JsonObject result = JsonParser.parseString(ProcessRunner.run(McpAcceptanceFixtureCatalog.smallTrainInput()))
        .getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertTrue(result.has("processDefinition"));
    assertTrue(result.has("provenance"));
  }

  @Test
  void testMultiAreaFixtureExecutesAsThreeAreaProcessModel() {
    JsonObject result = JsonParser.parseString(ProcessRunner.run(McpAcceptanceFixtureCatalog.multiAreaInput()))
        .getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals(3, result.get("areaCount").getAsInt());
    assertEquals(3, result.getAsJsonArray("areas").size());
    assertTrue(result.has("convergenceSummary"));
    assertTrue(result.has("processDefinition"));
  }

  @Test
  void testLargeFixtureIs154UnitsAndExecutableWithRecycle() {
    String fixture = McpAcceptanceFixtureCatalog.largeFacilityInput();
    JsonObject input = JsonParser.parseString(fixture).getAsJsonObject();
    JsonArray process = input.getAsJsonArray("process");
    assertEquals(154, process.size());
    assertEquals("Recycle", process.get(process.size() - 1).getAsJsonObject().get("type").getAsString());

    JsonObject validation = JsonParser.parseString(Validator.validate(fixture)).getAsJsonObject();
    assertTrue(validation.get("valid").getAsBoolean(), validation.toString());

    JsonObject result = JsonParser.parseString(ProcessRunner.run(fixture)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals(154, result.getAsJsonObject("processDefinition").getAsJsonArray("process").size());
    assertTrue(result.getAsJsonObject("provenance").get("converged").getAsBoolean());
  }
}
