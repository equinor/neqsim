package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    assertTrue(obj.get("totalCount").getAsInt() >= 5, "Should include all built-in profiles");
    String profiles = obj.getAsJsonArray("profiles").toString();
    assertTrue(profiles.contains("ncs"));
    assertTrue(profiles.contains("generic"));
  }

  @Test
  void testGetProfile() {
    String json = "{\"action\": \"getProfile\", \"profileName\": \"ncs\"}";
    String result = ValidationProfileRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Get profile failed: " + result);
    assertEquals("ncs", obj.get("name").getAsString());
    assertEquals("built-in", obj.get("type").getAsString());
    assertTrue(obj.getAsJsonObject("profile").has("standards"));
  }

  @Test
  void testGetActiveProfile() {
    String json = "{\"action\": \"getActiveProfile\"}";
    String result = ValidationProfileRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Get active profile failed: " + result);
    assertTrue(obj.has("activeProfile"));
    assertTrue(obj.has("profile"));
  }

  @Test
  void testGetStandardsForEquipment() {
    String json = "{\"action\": \"getStandardsForEquipment\", \"equipmentType\": \"separator\"}";
    String result = ValidationProfileRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Equipment standards failed: " + result);
    assertEquals("separator", obj.get("equipmentType").getAsString());
    assertTrue(obj.has("standards"));
  }

  @Test
  void testCustomProfileLifecycleIsExplicitAndRecoverable() {
    String originalProfile = ValidationProfileRunner.getActiveProfileName();
    String profileName = "phase0-contract-profile";
    try {
      JsonObject cleanup = JsonParser
          .parseString(ValidationProfileRunner.run("{\"action\":\"deleteProfile\",\"profileName\":\"" + profileName
              + "\"}"))
          .getAsJsonObject();
      assertEquals("success", cleanup.get("status").getAsString());

      JsonObject created = JsonParser.parseString(ValidationProfileRunner.run(
          "{\"action\":\"createProfile\",\"profileName\":\"" + profileName
              + "\",\"basedOn\":\"generic\",\"description\":\"Phase 0 contract fixture\"}"))
          .getAsJsonObject();
      assertEquals("success", created.get("status").getAsString());
      assertEquals(profileName, created.get("profileName").getAsString());

      JsonObject activated = JsonParser.parseString(ValidationProfileRunner.run(
          "{\"action\":\"setActiveProfile\",\"profileName\":\"" + profileName + "\"}"))
          .getAsJsonObject();
      assertEquals("success", activated.get("status").getAsString());
      assertEquals(profileName, activated.get("activeProfile").getAsString());

      JsonObject active = JsonParser.parseString(ValidationProfileRunner.run("{\"action\":\"getActiveProfile\"}"))
          .getAsJsonObject();
      assertEquals("success", active.get("status").getAsString());
      assertEquals(profileName, active.get("activeProfile").getAsString());
      assertEquals("custom", active.get("type").getAsString());
      assertEquals("Phase 0 contract fixture", active.getAsJsonObject("profile").get("description").getAsString());

      JsonObject deleted = JsonParser.parseString(ValidationProfileRunner.run(
          "{\"action\":\"deleteProfile\",\"profileName\":\"" + profileName + "\"}"))
          .getAsJsonObject();
      assertEquals("success", deleted.get("status").getAsString());
      assertTrue(deleted.get("deleted").getAsBoolean());
      assertEquals("generic", deleted.get("activeProfile").getAsString());
    } finally {
      ValidationProfileRunner.run(
          "{\"action\":\"deleteProfile\",\"profileName\":\"" + profileName + "\"}");
      ValidationProfileRunner.run(
          "{\"action\":\"setActiveProfile\",\"profileName\":\"" + originalProfile + "\"}");
    }
  }

  @Test
  void testMutationErrorsFailClosed() {
    JsonObject reservedDelete = JsonParser.parseString(
        ValidationProfileRunner.run("{\"action\":\"deleteProfile\",\"profileName\":\"generic\"}"))
        .getAsJsonObject();
    assertEquals("error", reservedDelete.get("status").getAsString());
    assertEquals("CANNOT_DELETE", reservedDelete.get("errorCode").getAsString());

    JsonObject missingProfile = JsonParser.parseString(
        ValidationProfileRunner.run("{\"action\":\"setActiveProfile\",\"profileName\":\"does-not-exist\"}"))
        .getAsJsonObject();
    assertEquals("error", missingProfile.get("status").getAsString());
    assertEquals("PROFILE_NOT_FOUND", missingProfile.get("errorCode").getAsString());

    JsonObject unknownAction = JsonParser
        .parseString(ValidationProfileRunner.run("{\"action\":\"unsupported-phase0-action\"}"))
        .getAsJsonObject();
    assertEquals("error", unknownAction.get("status").getAsString());
    assertEquals("UNKNOWN_ACTION", unknownAction.get("errorCode").getAsString());
    assertFalse(unknownAction.get("message").getAsString().isEmpty());
  }

  @Test
  void testNullInput() {
    String result = ValidationProfileRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
