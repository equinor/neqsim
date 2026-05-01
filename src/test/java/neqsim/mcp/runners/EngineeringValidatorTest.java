package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  }

  @Test
  void testValidateCompressor() {
    String compressorJson = "{" + "\"efficiency\": 0.82," + "\"compressionRatio\": 3.5,"
        + "\"inletTemperature_C\": 25.0," + "\"outletTemperature_C\": 150.0,"
        + "\"power_kW\": 1500.0" + "}";

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
    assertTrue(obj.has("verdict"));
  }
}
