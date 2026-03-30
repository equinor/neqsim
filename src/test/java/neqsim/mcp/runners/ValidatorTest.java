package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link Validator}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ValidatorTest {

  // --- Flash validation tests ---

  @Test
  void testValidFlash_noIssues() {
    String json =
        "{" + "\"model\": \"SRK\"," + "\"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
            + "\"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}," + "\"flashType\": \"TP\","
            + "\"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05},"
            + "\"mixingRule\": \"classic\"" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertTrue(root.get("valid").getAsBoolean());
    assertEquals(0, root.getAsJsonArray("issues").size());
  }

  @Test
  void testInvalidModel() {
    String json = "{" + "\"model\": \"NONEXISTENT\"," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "UNKNOWN_MODEL"));
  }

  @Test
  void testUnknownFlashType() {
    String json = "{" + "\"flashType\": \"XYZ\"," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "UNKNOWN_FLASH_TYPE"));
  }

  @Test
  void testMissingComponents() {
    String json = "{\"model\": \"SRK\", \"temperature\": 300.0}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "MISSING_COMPONENTS"));
  }

  @Test
  void testUnknownComponent_withSuggestion() {
    String json = "{\"components\": {\"metane\": 1.0}}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "UNKNOWN_COMPONENT"));

    // Should suggest correct name
    JsonArray issues = root.getAsJsonArray("issues");
    String message = issues.get(0).getAsJsonObject().get("message").getAsString();
    assertTrue(message.contains("methane"), "Should suggest 'methane' for 'metane'");
  }

  @Test
  void testNegativeFraction() {
    String json = "{\"components\": {\"methane\": -0.5}}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "NEGATIVE_FRACTION"));
  }

  @Test
  void testCompositionSumWarning() {
    String json = "{\"components\": {\"methane\": 0.5, \"ethane\": 0.3}}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    // This is a warning, not an error — so valid should still be true
    assertTrue(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "COMPOSITION_SUM"));
  }

  @Test
  void testPHFlash_missingEnthalpy() {
    String json = "{" + "\"flashType\": \"PH\"," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "MISSING_SPEC"));
  }

  @Test
  void testPSFlash_missingEntropy() {
    String json = "{" + "\"flashType\": \"PS\"," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "MISSING_SPEC"));
  }

  @Test
  void testTVFlash_missingVolume() {
    String json = "{" + "\"flashType\": \"TV\"," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "MISSING_SPEC"));
  }

  @Test
  void testExtremeTemperature() {
    String json = "{" + "\"temperature\": 50000.0," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    // Warning only, not error
    assertTrue(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "TEMPERATURE_RANGE"));
  }

  @Test
  void testExtremePressure() {
    String json = "{" + "\"pressure\": 99999.0," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertTrue(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "PRESSURE_RANGE"));
  }

  // --- Process validation tests ---

  @Test
  void testValidProcess() {
    String json = "{" + "\"fluid\": {" + "  \"model\": \"SRK\","
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.15}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}" + "]" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertTrue(root.get("valid").getAsBoolean());
  }

  @Test
  void testProcessMissingFluid() {
    String json =
        "{" + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\"}" + "]" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    // Warning, not error
    assertTrue(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "NO_FLUID"));
  }

  @Test
  void testProcessUnitMissingType() {
    String json = "{" + "\"fluid\": {\"components\": {\"methane\": 1.0}}," + "\"process\": ["
        + "  {\"name\": \"feed\"}" + "]" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "MISSING_TYPE"));
  }

  @Test
  void testProcessUnknownEquipmentType() {
    String json = "{" + "\"fluid\": {\"components\": {\"methane\": 1.0}}," + "\"process\": ["
        + "  {\"type\": \"SuperWidget\", \"name\": \"widget1\"}" + "]" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    // Warning only — partial success allowed
    assertTrue(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "UNKNOWN_EQUIPMENT_TYPE"));
  }

  @Test
  void testProcessDuplicateNames() {
    String json = "{" + "\"fluid\": {\"components\": {\"methane\": 1.0}}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"},"
        + "  {\"type\": \"Stream\", \"name\": \"feed\"}" + "]" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertTrue(hasIssueCode(root, "DUPLICATE_NAME"));
  }

  @Test
  void testProcessEmptyInlet() {
    String json = "{" + "\"fluid\": {\"components\": {\"methane\": 1.0}}," + "\"process\": ["
        + "  {\"type\": \"Separator\", \"name\": \"sep\", \"inlet\": \"\"}" + "]" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertTrue(hasIssueCode(root, "EMPTY_INLET"));
  }

  // --- Edge cases ---

  @Test
  void testNullInput() {
    String result = Validator.validate(null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "INPUT_ERROR"));
  }

  @Test
  void testEmptyInput() {
    String result = Validator.validate("");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "INPUT_ERROR"));
  }

  @Test
  void testMalformedJson() {
    String result = Validator.validate("{bad json}");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    assertTrue(hasIssueCode(root, "JSON_PARSE_ERROR"));
  }

  @Test
  void testMultipleErrors() {
    // Unknown model + unknown component + missing spec
    String json = "{" + "\"model\": \"FAKEOS\"," + "\"flashType\": \"PH\","
        + "\"components\": {\"fakey\": 1.0}" + "}";

    String result = Validator.validate(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(root.get("valid").getAsBoolean());
    JsonArray issues = root.getAsJsonArray("issues");
    assertTrue(issues.size() >= 3, "Should have at least 3 issues");
  }

  // --- Helpers ---

  /**
   * Checks if any issue in the response has the given code.
   */
  private boolean hasIssueCode(JsonObject root, String code) {
    JsonArray issues = root.getAsJsonArray("issues");
    for (int i = 0; i < issues.size(); i++) {
      if (code.equals(issues.get(i).getAsJsonObject().get("code").getAsString())) {
        return true;
      }
    }
    return false;
  }
}
