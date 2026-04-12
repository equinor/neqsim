package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link DataCatalogRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DataCatalogRunnerTest {

  @Test
  void testListEOSModels() {
    String json = "{\"action\": \"listEOSModels\"}";
    String result = DataCatalogRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "List EOS failed: " + result);
  }

  @Test
  void testListComponentFamilies() {
    String json = "{\"action\": \"listComponentFamilies\"}";
    String result = DataCatalogRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "List families failed: " + result);
  }

  @Test
  void testGetComponentProperties() {
    String json = "{\"action\": \"getComponentProperties\", \"componentName\": \"methane\"}";
    String result = DataCatalogRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Component props failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = DataCatalogRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
