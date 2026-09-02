package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link EngineeringValidator}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class EngineeringValidatorTest {

  @Test
  void testValidateProcessResults() {
    // Simulate a process result with some values
    String resultsJson = "{" + "\"status\": \"success\"," + "\"report\": {"
        + "  \"feed\": {\"temperature_C\": 25.0, \"pressure_bara\": 50.0},"
        + "  \"HP Sep\": {\"temperature_C\": 25.0, \"pressure_bara\": 50.0}" + "}" + "}";

    String result = EngineeringValidator.validate(resultsJson, "process");
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue(obj.has("verdict"), "Should return a verdict");
    assertTrue(obj.has("findings"), "Should return findings array");
    assertTrue(obj.get("passed").getAsBoolean());
    assertEquals("process", obj.get("validationContext").getAsString());
    assertEquals(0, obj.get("errors").getAsInt());
  }

  @Test
  void testValidateCompressor() {
    String compressorJson = "{" + "\"efficiency\": 0.82," + "\"compressionRatio\": 3.5,"
        + "\"inletTemperature_C\": 25.0," + "\"outletTemperature_C\": 150.0," + "\"power_kW\": 1500.0" + "}";

    String result = EngineeringValidator.validateEquipment(compressorJson, "compressor");
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue(obj.has("verdict"));
  }

  @Test
  void testInvalidJson() {
    String result = EngineeringValidator.validate("not json", "general");
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertFalse(obj.get("passed").getAsBoolean());
    assertEquals(1, obj.get("errors").getAsInt());
    assertEquals("PARSE_ERROR", obj.getAsJsonArray("findings").get(0).getAsJsonObject().get("code").getAsString());
    assertEquals("ERROR", obj.getAsJsonArray("findings").get(0).getAsJsonObject().get("severity").getAsString());
  }

  @Test
  void testWarningsRemainNonBlocking() {
    String result = EngineeringValidator.validate("{\"converged\":true,\"polytropicEfficiency\":0.40}", "compressor");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertTrue(obj.get("passed").getAsBoolean());
    assertEquals(0, obj.get("errors").getAsInt());
    assertEquals(1, obj.get("warnings").getAsInt());
    assertTrue(obj.get("verdict").getAsString().startsWith("PASS_WITH_WARNINGS"));
    assertEquals("LOW_EFFICIENCY", obj.getAsJsonArray("findings").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testBlockingPhysicalAndConvergenceFindingsFail() {
    String result = EngineeringValidator.validate("{\"converged\":false,\"pressure\":-1.0,\"massBalanceError\":0.02}",
        "process");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertFalse(obj.get("passed").getAsBoolean());
    assertEquals(3, obj.get("errors").getAsInt());
    String findings = obj.getAsJsonArray("findings").toString();
    assertTrue(findings.contains("NEGATIVE_PRESSURE"));
    assertTrue(findings.contains("MASS_BALANCE"));
    assertTrue(findings.contains("NOT_CONVERGED"));
  }

  @Test
  void testNestedEquipmentFieldsAreValidatedDeterministically() {
    String input = "{\"equipment\":{\"compressor\":{\"outletTemperature\":220.0," + "\"compressionRatio\":5.0}}}";
    JsonObject first = JsonParser.parseString(EngineeringValidator.validate(input, "process")).getAsJsonObject();
    JsonObject second = JsonParser.parseString(EngineeringValidator.validate(input, "process")).getAsJsonObject();

    assertEquals(first, second);
    assertTrue(first.get("passed").getAsBoolean());
    assertEquals(2, first.get("warnings").getAsInt());
    String findings = first.getAsJsonArray("findings").toString();
    assertTrue(findings.contains("HIGH_DISCHARGE_TEMP"));
    assertTrue(findings.contains("HIGH_COMPRESSION_RATIO"));
  }
}
