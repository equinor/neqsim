package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link FlareRadiationRunner}.
 */
class FlareRadiationRunnerTest {

  @Test
  void testApi521Thresholds() {
    String json = "{\"heatDuty_MW\":50.0,\"flameHeight_m\":40.0,\"radiantFraction\":0.20}";
    String result = FlareRadiationRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.has("safeDistanceContour"));
    JsonArray contour = obj.getAsJsonArray("safeDistanceContour");
    assertEquals(4, contour.size()); // 1580/4730/6310/9460 W/m^2
    for (int i = 0; i < contour.size(); i++) {
      JsonObject row = contour.get(i).getAsJsonObject();
      assertTrue(row.has("safeGroundDistance_m"));
      assertTrue(row.has("threshold_W_m2"));
    }
  }

  @Test
  void testCustomDistances() {
    String json = "{\"heatDuty_W\":2.0e7,\"distances_m\":[20.0, 50.0, 100.0]}";
    String result = FlareRadiationRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    JsonArray prof = obj.getAsJsonArray("radiationProfile");
    assertEquals(3, prof.size());
    // Flux must decrease with distance
    double f0 = prof.get(0).getAsJsonObject().get("flux_W_m2").getAsDouble();
    double f2 = prof.get(2).getAsJsonObject().get("flux_W_m2").getAsDouble();
    assertTrue(f0 > f2);
  }

  @Test
  void testMissingHeatDuty() {
    String result = FlareRadiationRunner.run("{}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
