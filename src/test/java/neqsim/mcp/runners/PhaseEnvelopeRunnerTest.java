package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for the PhaseEnvelopeRunner.
 */
class PhaseEnvelopeRunnerTest {

  @Test
  void testNaturalGasEnvelope() {
    String json = "{" + "\"model\": \"SRK\","
        + "\"components\": {\"methane\": 0.80, \"ethane\": 0.10, \"propane\": 0.05, \"n-butane\": 0.03, \"n-pentane\": 0.02}"
        + "}";

    String result = PhaseEnvelopeRunner.run(json);
    assertNotNull(result);

    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("success".equals(obj.get("status").getAsString()),
        "Expected success but got: " + result);
    assertTrue(obj.has("envelope"));
    assertTrue(obj.getAsJsonArray("envelope").size() > 0, "Envelope should have data points");
    assertTrue(obj.has("provenance"), "Response should include provenance metadata");
    assertTrue(obj.has("model"));
  }

  @Test
  void testMissingComponents() {
    String json = "{\"model\": \"SRK\"}";
    String result = PhaseEnvelopeRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("error".equals(obj.get("status").getAsString()));
  }

  @Test
  void testNullInput() {
    String result = PhaseEnvelopeRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("error".equals(obj.get("status").getAsString()));
  }
}
