package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link FlowAssuranceRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FlowAssuranceRunnerTest {

  @Test
  void testHydrateRisk() {
    String result = FlowAssuranceRunner.run(ExampleCatalog.flowAssuranceHydrate());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), result);
    JsonObject data = obj.getAsJsonObject("data");
    // The shipped example (CPA wet gas, 20 C, 100 bara) must give a real hydrate temperature, not NaN -> LOW.
    assertEquals(0, data.get("unknownPointCount").getAsInt(), result);
    JsonObject point = data.getAsJsonArray("profile").get(0).getAsJsonObject();
    double hydrateT = point.get("hydrateTemperature_C").getAsDouble();
    assertTrue(hydrateT > 5.0 && hydrateT < 30.0, "hydrate T at 100 bara should be ~15-25 C, got " + hydrateT);
    assertTrue(!"UNKNOWN".equals(data.get("overallRisk").getAsString()));
  }

  @Test
  void testCpaDefaultsToCpaMixingRule() {
    // With the SRK kij database ("classic") CPA gives ~0 C here; with the CPA rule ~16 C (SRK classic: 16.3 C).
    String input = "{\"model\":\"CPA\",\"temperature_C\":10.0,\"pressure_bara\":100.0,"
        + "\"components\":{\"methane\":0.9,\"ethane\":0.05,\"CO2\":0.02,\"water\":0.03},\"analysis\":\"hydrateRiskMap\"}";
    JsonObject obj = JsonParser.parseString(FlowAssuranceRunner.run(input)).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), obj.toString());
    double hydrateT = obj.getAsJsonObject("data").getAsJsonArray("profile").get(0).getAsJsonObject()
        .get("hydrateTemperature_C").getAsDouble();
    assertTrue(hydrateT > 10.0 && hydrateT < 22.0, "CPA hydrate T at 100 bara should be ~16 C, got " + hydrateT);
  }

  @Test
  void testHydrateRiskWithoutWaterIsErrorNotLowRisk() {
    String input = "{\"model\":\"SRK\",\"temperature_C\":10.0,\"pressure_bara\":100.0,"
        + "\"components\":{\"methane\":0.9,\"ethane\":0.1},\"analysis\":\"hydrateRiskMap\"}";
    JsonObject obj = JsonParser.parseString(FlowAssuranceRunner.run(input)).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString(), obj.toString());
    String text = obj.toString();
    assertTrue(text.contains("RESULT_NOT_AVAILABLE"), text);
    assertTrue(text.contains("water"), text);
    assertTrue(obj.has("partialData"));
  }

  @Test
  void testNullInput() {
    String result = FlowAssuranceRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testDemulsifierDoseOptimization() {
    String input = "{" + "\"analysis\":\"demulsifierDoseOptimization\"," + "\"untreatedOIW_mgL\":120.0,"
        + "\"waterRate_m3_h\":100.0," + "\"halfEffectDosePpm\":10.0," + "\"maxRemovalFraction\":0.85,"
        + "\"optimumDosePpm\":60.0," + "\"holdUpVolume_m3\":10.0," + "\"projectedDailyWater_m3\":2400.0,"
        + "\"monthlySamples\":[{\"oiw_mgL\":20.0,\"waterVolume_m3\":2400.0}]," + "\"dayOfMonth\":5" + "}";

    String result = FlowAssuranceRunner.run(input);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.getAsJsonObject("data").has("lastRecommendation"));
    assertTrue(
        obj.getAsJsonObject("data").getAsJsonObject("lastRecommendation").get("setpointDosePpm").getAsDouble() > 0.0);
  }
}
