package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonElement;
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
    assertTrue("success".equals(obj.get("status").getAsString()), "Expected success but got: " + result);
    assertTrue(obj.has("envelope"));
    assertTrue(obj.has("apiVersion"));
    assertTrue(obj.has("tool"));
    assertTrue(obj.has("data"));
    assertTrue(obj.has("validation"));
    assertTrue(obj.has("qualityGate"));
    assertTrue(obj.has("warnings"));
    assertTrue(obj.getAsJsonArray("envelope").size() > 0, "Envelope should have data points");
    assertTrue(obj.has("provenance"), "Response should include provenance metadata");
    assertTrue(obj.getAsJsonObject("provenance").has("benchmarkTrustLevel"));
    assertTrue(obj.has("model"));

    // Regression: getPoints(0) is {dewT, dewP, bubT, bubP}; reading it as {P, T} gave T ~ 1 K.
    boolean sawDew = false;
    boolean sawBubble = false;
    for (JsonElement el : obj.getAsJsonArray("envelope")) {
      JsonObject pt = el.getAsJsonObject();
      double tC = pt.get("temperature_C").getAsDouble();
      double p = pt.get("pressure_bara").getAsDouble();
      assertTrue(tC > -180.0 && tC < 100.0, "Unphysical envelope temperature " + tC + " C");
      assertTrue(p > 0.0 && p < 500.0, "Unphysical envelope pressure " + p + " bara");
      String branch = pt.get("branch").getAsString();
      sawDew |= "dew".equals(branch);
      sawBubble |= "bubble".equals(branch);
    }
    assertTrue(sawDew && sawBubble, "Envelope should contain both dew and bubble branches");

    JsonObject crit = obj.getAsJsonObject("criticalPoints");
    assertTrue(crit.has("cricondenbar") && crit.has("cricondentherm"));
    double ccbT = crit.getAsJsonObject("cricondenbar").get("temperature_C").getAsDouble();
    double cctT = crit.getAsJsonObject("cricondentherm").get("temperature_C").getAsDouble();
    assertTrue(ccbT > -120.0 && ccbT < 60.0, "Cricondenbar T unphysical: " + ccbT);
    assertTrue(cctT >= ccbT - 1.0, "Cricondentherm T should not be below cricondenbar T");
  }

  @Test
  void testMissingComponents() {
    String json = "{\"model\": \"SRK\"}";
    String result = PhaseEnvelopeRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("error".equals(obj.get("status").getAsString()));
    assertTrue(obj.has("apiVersion"));
    assertTrue(obj.has("validation"));
    assertTrue(obj.has("qualityGate"));
  }

  @Test
  void testNullInput() {
    String result = PhaseEnvelopeRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("error".equals(obj.get("status").getAsString()));
  }
}
