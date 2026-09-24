package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests the bounded flare-radiation screening contract. */
class FlareRadiationRunnerTest {

  private JsonObject run(String json) {
    return JsonParser.parseString(FlareRadiationRunner.run(json)).getAsJsonObject();
  }

  @Test
  void testCanonicalProfileAndAdvisoryBoundary() {
    JsonObject result =
        run("{\"heatDuty_MW\":50.0,\"flameHeight_m\":40.0,\"radiantFraction\":0.20}");
    assertEquals("success", result.get("status").getAsString());
    assertEquals(7, result.getAsJsonArray("radiationProfile").size());
    assertEquals(4, result.getAsJsonArray("safeDistanceContour").size());
    assertTrue(result.get("screeningOnly").getAsBoolean());
    assertFalse(result.get("standardConformanceClaimed").getAsBoolean());
    assertTrue(result.get("engineeringReviewRequired").getAsBoolean());
    assertTrue(result.get("modelBoundary").getAsString().contains("Canonical NeqSim Flare"));
  }

  @Test
  void testCustomDistancesAreDeterministicAndMonotonic() {
    JsonObject first = run("{\"heatDuty_W\":2.0e7,\"distances_m\":[20.0,50.0,100.0]}");
    JsonObject second = run("{\"heatDuty_W\":2.0e7,\"distances_m\":[20.0,50.0,100.0]}");
    assertEquals(first, second);
    JsonArray profile = first.getAsJsonArray("radiationProfile");
    assertEquals(3, profile.size());
    assertTrue(profile.get(0).getAsJsonObject().get("flux_W_m2").getAsDouble()
        > profile.get(2).getAsJsonObject().get("flux_W_m2").getAsDouble());
  }

  @Test
  void testMissingOrDualHeatDutyFailsClosed() {
    assertEquals("INVALID_HEAT_DUTY", run("{}").get("errorCode").getAsString());
    assertEquals("INVALID_HEAT_DUTY",
        run("{\"heatDuty_MW\":1,\"heatDuty_W\":1000000}").get("errorCode").getAsString());
  }

  @Test
  void testPhysicalBoundsFailClosed() {
    assertEquals("INVALID_HEAT_DUTY",
        run("{\"heatDuty_W\":0}").get("errorCode").getAsString());
    assertEquals("INVALID_FLAME_HEIGHT",
        run("{\"heatDuty_W\":1,\"flameHeight_m\":0}").get("errorCode").getAsString());
    assertEquals("INVALID_RADIANT_FRACTION",
        run("{\"heatDuty_W\":1,\"radiantFraction\":1.1}").get("errorCode").getAsString());
  }

  @Test
  void testDistanceBoundsFailClosed() {
    assertEquals("INVALID_DISTANCES",
        run("{\"heatDuty_W\":1,\"distances_m\":[]}").get("errorCode").getAsString());
    assertEquals("INVALID_DISTANCE",
        run("{\"heatDuty_W\":1,\"distances_m\":[-1]}").get("errorCode").getAsString());
  }

  @Test
  void testDistanceCountAdmissionLimit() {
    StringBuilder json = new StringBuilder("{\"heatDuty_W\":1,\"distances_m\":[");
    for (int i = 0; i < 201; i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append(i + 1);
    }
    json.append("]}");
    assertEquals("INVALID_DISTANCES", run(json.toString()).get("errorCode").getAsString());
  }

  @Test
  void testRequestSizeAdmissionLimit() {
    String padding = String.join("", Collections.nCopies(17000, "x"));
    JsonObject result = run("{\"heatDuty_W\":1,\"padding\":\"" + padding + "\"}");
    assertEquals("REQUEST_TOO_LARGE", result.get("errorCode").getAsString());
    assertTrue(result.get("engineeringReviewRequired").getAsBoolean());
  }
}
