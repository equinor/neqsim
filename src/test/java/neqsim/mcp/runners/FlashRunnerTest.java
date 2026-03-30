package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link FlashRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FlashRunnerTest {

  // --- TP flash tests ---

  @Test
  void testTPFlash_simpleGas() {
    String json =
        "{" + "\"model\": \"SRK\"," + "\"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
            + "\"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}," + "\"flashType\": \"TP\","
            + "\"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05},"
            + "\"mixingRule\": \"classic\"" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.has("flash"));
    assertTrue(root.has("fluid"));

    // Check flash metadata
    JsonObject flash = root.getAsJsonObject("flash");
    assertEquals("SRK", flash.get("model").getAsString());
    assertEquals("TP", flash.get("flashType").getAsString());
    assertTrue(flash.get("numberOfPhases").getAsInt() >= 1);

    // Check fluid has properties
    JsonObject fluid = root.getAsJsonObject("fluid");
    assertTrue(fluid.has("properties"));
    assertTrue(fluid.has("conditions"));
    assertTrue(fluid.has("composition"));
  }

  @Test
  void testTPFlash_defaultValues() {
    // Minimal input — only components
    String json = "{\"components\": {\"methane\": 0.9, \"ethane\": 0.1}}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    // Should use defaults: SRK, TP flash, 15C, 1 atm, classic mixing rule
  }

  @Test
  void testTPFlash_prModel() {
    String json = "{" + "\"model\": \"PR\"," + "\"temperature\": 300.0," + "\"pressure\": 10.0,"
        + "\"components\": {\"methane\": 0.7, \"propane\": 0.3}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    JsonObject flash = root.getAsJsonObject("flash");
    assertEquals("PR", flash.get("model").getAsString());
  }

  @Test
  void testTPFlash_twoPhase() {
    // Low pressure, near bubble - CH4/C3 should split into two phases at 10 bara, -20C
    String json =
        "{" + "\"model\": \"SRK\"," + "\"temperature\": {\"value\": -20.0, \"unit\": \"C\"},"
            + "\"pressure\": {\"value\": 10.0, \"unit\": \"bara\"},"
            + "\"components\": {\"methane\": 0.5, \"propane\": 0.5},"
            + "\"mixingRule\": \"classic\"" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    JsonObject flash = root.getAsJsonObject("flash");
    assertTrue(flash.get("numberOfPhases").getAsInt() >= 2, "Expected two-phase result");
  }

  @Test
  void testTPFlash_hasTransportProperties() {
    String json =
        "{" + "\"model\": \"SRK\"," + "\"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
            + "\"pressure\": {\"value\": 50.0, \"unit\": \"bara\"},"
            + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    JsonObject fluid = root.getAsJsonObject("fluid");
    JsonObject properties = fluid.getAsJsonObject("properties");

    // Should have phase-level properties with transport properties
    assertTrue(properties.size() >= 2, "Expected overall + at least one phase");
    // Check that at least one phase has viscosity
    boolean hasViscosity = false;
    for (String key : properties.keySet()) {
      if (!"overall".equals(key)) {
        JsonObject phaseProps = properties.getAsJsonObject(key);
        if (phaseProps.has("viscosity")) {
          hasViscosity = true;
          break;
        }
      }
    }
    assertTrue(hasViscosity, "Expected transport properties in phase data");
  }

  // --- PH flash tests ---

  @Test
  void testPHFlash() {
    // First do a TP flash to get a valid enthalpy, then use it
    String json = "{" + "\"model\": \"SRK\"," + "\"flashType\": \"PH\","
        + "\"pressure\": {\"value\": 50.0, \"unit\": \"bara\"},"
        + "\"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
        + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1},"
        + "\"enthalpy\": {\"value\": -5000.0, \"unit\": \"J/mol\"}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    JsonObject flash = root.getAsJsonObject("flash");
    assertEquals("PH", flash.get("flashType").getAsString());
  }

  @Test
  void testPHFlash_missingEnthalpy() {
    String json = "{" + "\"flashType\": \"PH\"," + "\"pressure\": 50.0,"
        + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    JsonArray errors = root.getAsJsonArray("errors");
    assertEquals("MISSING_SPEC", errors.get(0).getAsJsonObject().get("code").getAsString());
  }

  // --- PS flash tests ---

  @Test
  void testPSFlash() {
    String json = "{" + "\"model\": \"SRK\"," + "\"flashType\": \"PS\","
        + "\"pressure\": {\"value\": 50.0, \"unit\": \"bara\"},"
        + "\"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
        + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1},"
        + "\"entropy\": {\"value\": -30.0, \"unit\": \"J/molK\"}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertEquals("PS", root.getAsJsonObject("flash").get("flashType").getAsString());
  }

  @Test
  void testPSFlash_missingEntropy() {
    String json = "{" + "\"flashType\": \"PS\"," + "\"pressure\": 50.0,"
        + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("MISSING_SPEC",
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  // --- Dew/Bubble point tests ---

  @Test
  void testDewPointTemperature() {
    String json = "{" + "\"flashType\": \"dewPointT\","
        + "\"pressure\": {\"value\": 50.0, \"unit\": \"bara\"},"
        + "\"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
        + "\"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertEquals("dewPointT", root.getAsJsonObject("flash").get("flashType").getAsString());
  }

  @Test
  void testBubblePointPressure() {
    String json = "{" + "\"flashType\": \"bubblePointP\","
        + "\"temperature\": {\"value\": -50.0, \"unit\": \"C\"},"
        + "\"pressure\": {\"value\": 30.0, \"unit\": \"bara\"},"
        + "\"components\": {\"methane\": 0.5, \"ethane\": 0.3, \"propane\": 0.2}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertEquals("bubblePointP", root.getAsJsonObject("flash").get("flashType").getAsString());
  }

  // --- Error handling tests ---

  @Test
  void testNullInput() {
    String result = FlashRunner.run(null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("INPUT_ERROR",
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testEmptyInput() {
    String result = FlashRunner.run("");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
  }

  @Test
  void testMalformedJson() {
    String result = FlashRunner.run("{not valid json}");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("JSON_PARSE_ERROR",
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testMissingComponents() {
    String json = "{\"model\": \"SRK\", \"temperature\": 300.0, \"pressure\": 50.0}";
    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("MISSING_COMPONENTS",
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testUnknownModel() {
    String json = "{" + "\"model\": \"NONEXISTENT\"," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("UNKNOWN_MODEL",
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testUnknownFlashType() {
    String json = "{" + "\"flashType\": \"XYZ\"," + "\"components\": {\"methane\": 1.0}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("UNKNOWN_FLASH_TYPE",
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testUnknownComponent_withSuggestion() {
    String json = "{\"components\": {\"metane\": 1.0}}"; // typo

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    String message =
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("message").getAsString();
    assertTrue(message.contains("metane"), "Error should mention the bad name");
    assertTrue(message.contains("methane"), "Error should suggest the correct name");
  }

  // --- Unit conversion tests ---

  @Test
  void testTemperatureParsing_kelvin() {
    assertEquals(300.0, FlashRunner.parseTemperature(JsonParser.parseString("300.0")), 0.01);
  }

  @Test
  void testTemperatureParsing_celsius() {
    assertEquals(298.15,
        FlashRunner.parseTemperature(JsonParser.parseString("{\"value\": 25.0, \"unit\": \"C\"}")),
        0.01);
  }

  @Test
  void testTemperatureParsing_fahrenheit() {
    assertEquals(373.15,
        FlashRunner.parseTemperature(JsonParser.parseString("{\"value\": 212.0, \"unit\": \"F\"}")),
        0.1);
  }

  @Test
  void testPressureParsing_bara() {
    assertEquals(50.0, FlashRunner.parsePressure(JsonParser.parseString("50.0")), 0.01);
  }

  @Test
  void testPressureParsing_psi() {
    assertEquals(6.89476,
        FlashRunner.parsePressure(JsonParser.parseString("{\"value\": 100.0, \"unit\": \"psi\"}")),
        0.01);
  }

  @Test
  void testPressureParsing_MPa() {
    assertEquals(10.0,
        FlashRunner.parsePressure(JsonParser.parseString("{\"value\": 1.0, \"unit\": \"MPa\"}")),
        0.01);
  }

  // --- Utility method tests ---

  @Test
  void testGetSupportedFlashTypes() {
    assertNotNull(FlashRunner.getSupportedFlashTypes());
    assertTrue(FlashRunner.getSupportedFlashTypes().contains("TP"));
    assertTrue(FlashRunner.getSupportedFlashTypes().contains("PH"));
    assertTrue(FlashRunner.getSupportedFlashTypes().contains("dewPointT"));
    assertFalse(FlashRunner.getSupportedFlashTypes().contains("XYZ"));
  }

  @Test
  void testGetSupportedModels() {
    assertNotNull(FlashRunner.getSupportedModels());
    assertTrue(FlashRunner.getSupportedModels().contains("SRK"));
    assertTrue(FlashRunner.getSupportedModels().contains("PR"));
    assertTrue(FlashRunner.getSupportedModels().contains("CPA"));
  }

  // --- TV flash test ---

  @Test
  void testTVFlash_missingVolume() {
    String json = "{" + "\"flashType\": \"TV\"," + "\"temperature\": 300.0,"
        + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}";

    String result = FlashRunner.run(json);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertEquals("MISSING_SPEC",
        root.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }
}
