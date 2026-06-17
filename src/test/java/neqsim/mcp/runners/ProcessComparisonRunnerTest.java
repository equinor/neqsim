package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link ProcessComparisonRunner}.
 */
class ProcessComparisonRunnerTest {

  @Test
  void testCompareTwoCases() {
    String json = ExampleCatalog.getExample("comparison", "two-cases");
    String result = ProcessComparisonRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertEquals(2, obj.get("caseCount").getAsInt());
    assertTrue(obj.has("cases"));
    assertTrue(obj.has("comparison"));
  }

  @Test
  void testSingleCaseError() {
    String json = "{\"cases\": [{\"name\": \"Only One\", \"fluid\": {}, \"process\": []}]}";
    String result = ProcessComparisonRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
    assertTrue(obj.get("message").getAsString().contains("at least 2"));
  }

  @Test
  void testNullInput() {
    String result = ProcessComparisonRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
