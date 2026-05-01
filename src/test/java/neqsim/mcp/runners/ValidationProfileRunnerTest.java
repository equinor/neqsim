package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link ValidationProfileRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ValidationProfileRunnerTest {

  @Test
  void testListProfiles() {
    String json = "{\"action\": \"listProfiles\"}";
    String result = ValidationProfileRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "List profiles failed: " + result);
    assertTrue(obj.has("profiles"), "Should list profiles");
  }

  @Test
  void testGetProfile() {
    String json = "{\"action\": \"getProfile\", \"profileName\": \"ncs\"}";
    String result = ValidationProfileRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Get profile failed: " + result);
  }

  @Test
  void testGetActiveProfile() {
    String json = "{\"action\": \"getActiveProfile\"}";
    String result = ValidationProfileRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(),
        "Get active profile failed: " + result);
  }

  @Test
  void testGetStandardsForEquipment() {
    String json = "{\"action\": \"getStandardsForEquipment\", \"equipmentType\": \"separator\"}";
    String result = ValidationProfileRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(),
        "Equipment standards failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = ValidationProfileRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
