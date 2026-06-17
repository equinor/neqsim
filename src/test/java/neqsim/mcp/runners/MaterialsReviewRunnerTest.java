package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for the MCP materials review runner.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class MaterialsReviewRunnerTest {

  /**
   * Verifies the catalog example runs and returns materials review content.
   */
  @Test
  void testExampleMaterialsReviewRuns() {
    String result = MaterialsReviewRunner.run(ExampleCatalog.materialsReviewStidRegister());
    JsonObject json = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", json.get("status").getAsString());
    assertEquals("materials_integrity_review", json.get("reviewType").getAsString());
    assertTrue(json.get("itemCount").getAsInt() >= 2);
    assertTrue(result.contains("CO2 corrosion"));
    assertTrue(result.contains("provenance"));
  }

  /**
   * Verifies invalid input returns a structured MCP error.
   */
  @Test
  void testMissingMaterialsDataReturnsError() {
    String result = MaterialsReviewRunner.run("{\"projectName\":\"empty\"}");
    JsonObject json = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", json.get("status").getAsString());
    assertTrue(result.contains("MISSING_MATERIALS_DATA"));
  }
}
