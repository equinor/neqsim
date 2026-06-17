package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link RiskMatrixRunner}.
 */
class RiskMatrixRunnerTest {

  @Test
  void testFromLevels() {
    String json = "{\"events\":["
        + "  {\"name\":\"Compressor seal failure\","
        + "   \"probabilityLevel\":3,\"consequenceLevel\":4},"
        + "  {\"name\":\"PSV failure\","
        + "   \"probabilityLevel\":2,\"consequenceLevel\":2}"
        + "]}";
    String result = RiskMatrixRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertEquals(2, obj.get("eventCount").getAsInt());
    JsonArray events = obj.getAsJsonArray("events");
    assertEquals(2, events.size());
    for (int i = 0; i < events.size(); i++) {
      JsonObject ev = events.get(i).getAsJsonObject();
      assertTrue(ev.has("riskScore"));
      assertTrue(ev.has("riskLevel"));
      assertTrue(ev.has("color"));
    }
    assertTrue(obj.getAsJsonObject("overall").get("maxScore").getAsInt() >= 4);
  }

  @Test
  void testFromFrequency() {
    String json = "{\"events\":["
        + "  {\"name\":\"Pump failure\","
        + "   \"failuresPerYear\":0.5,\"productionLossPercent\":15.0}"
        + "]}";
    String result = RiskMatrixRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    JsonObject ev = obj.getAsJsonArray("events").get(0).getAsJsonObject();
    assertTrue(ev.get("probabilityLevel").getAsInt() >= 1);
    assertTrue(ev.get("consequenceLevel").getAsInt() >= 1);
  }

  @Test
  void testMissingEvents() {
    String result = RiskMatrixRunner.run("{}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testIncompleteEvent() {
    String result = RiskMatrixRunner.run("{\"events\":[{\"name\":\"x\"}]}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
