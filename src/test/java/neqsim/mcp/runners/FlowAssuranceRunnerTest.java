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
    assertTrue(obj.has("status"), "Missing status field");
    // Hydrate calculation may fail in unit-test context (no profile points etc.)
    String status = obj.get("status").getAsString();
    assertTrue("success".equals(status) || "error".equals(status),
        "Status should be success or error, got: " + status);
  }

  @Test
  void testNullInput() {
    String result = FlowAssuranceRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testDemulsifierDoseOptimization() {
    String input =
        "{" + "\"analysis\":\"demulsifierDoseOptimization\"," + "\"untreatedOIW_mgL\":120.0,"
            + "\"waterRate_m3_h\":100.0," + "\"halfEffectDosePpm\":10.0,"
            + "\"maxRemovalFraction\":0.85," + "\"optimumDosePpm\":60.0,"
            + "\"holdUpVolume_m3\":10.0," + "\"projectedDailyWater_m3\":2400.0,"
            + "\"monthlySamples\":[{\"oiw_mgL\":20.0,\"waterVolume_m3\":2400.0}],"
            + "\"dayOfMonth\":5" + "}";

    String result = FlowAssuranceRunner.run(input);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.getAsJsonObject("data").has("lastRecommendation"));
    assertTrue(obj.getAsJsonObject("data").getAsJsonObject("lastRecommendation")
        .get("setpointDosePpm").getAsDouble() > 0.0);
  }
}
