package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    String json = "{\"events\":[" + "  {\"name\":\"Compressor seal failure\","
        + "   \"probabilityLevel\":3,\"consequenceLevel\":4}," + "  {\"name\":\"PSV failure\","
        + "   \"probabilityLevel\":2,\"consequenceLevel\":2}" + "]}";
    String result = RiskMatrixRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.get("screeningOnly").getAsBoolean());
    assertFalse(obj.get("standardConformanceClaimed").getAsBoolean());
    assertEquals("Generic 5x5 screening; project-specific verification required", obj.get("standard").getAsString());
    assertEquals(2, obj.get("eventCount").getAsInt());
    JsonArray events = obj.getAsJsonArray("events");
    assertEquals(2, events.size());
    for (int i = 0; i < events.size(); i++) {
      JsonObject ev = events.get(i).getAsJsonObject();
      assertTrue(ev.has("riskScore"));
      assertTrue(ev.has("riskLevel"));
      assertTrue(ev.has("color"));
      assertEquals("CALLER_SUPPLIED_LEVELS", ev.get("inputBasis").getAsString());
    }
    assertTrue(obj.getAsJsonObject("overall").get("maxScore").getAsInt() >= 4);
  }

  @Test
  void testFromFrequency() {
    String json = "{\"events\":[" + "  {\"name\":\"Pump failure\","
        + "   \"failuresPerYear\":0.5,\"productionLossPercent\":15.0}" + "]}";
    String result = RiskMatrixRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    JsonObject ev = obj.getAsJsonArray("events").get(0).getAsJsonObject();
    assertTrue(ev.get("probabilityLevel").getAsInt() >= 1);
    assertTrue(ev.get("consequenceLevel").getAsInt() >= 1);
    assertEquals("CALLER_SUPPLIED_FREQUENCY_AND_PRODUCTION_LOSS", ev.get("inputBasis").getAsString());
  }

  @Test
  void testMissingEvents() {
    String result = RiskMatrixRunner.run("{}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
    assertEquals("INVALID_INPUT", obj.get("code").getAsString());
  }

  @Test
  void testIncompleteEvent() {
    String result = RiskMatrixRunner.run("{\"events\":[{\"name\":\"x\"}]}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
    assertEquals("INVALID_EVENT", obj.get("code").getAsString());
  }

  @Test
  void rejectsAmbiguousAndPartialInputModes() {
    assertErrorCode(
        "{\"events\":[{\"probabilityLevel\":2,\"consequenceLevel\":3,\"failuresPerYear\":0.2,\"productionLossPercent\":4}]}",
        "INVALID_EVENT");
    assertErrorCode("{\"events\":[{\"probabilityLevel\":2}]}", "INVALID_EVENT");
  }

  @Test
  void rejectsFractionalLevelsAndOutOfRangeMeasuredInputs() {
    assertErrorCode("{\"events\":[{\"probabilityLevel\":1.5,\"consequenceLevel\":3}]}", "INVALID_EVENT");
    assertErrorCode("{\"events\":[{\"failuresPerYear\":-0.1,\"productionLossPercent\":4}]}", "INVALID_EVENT");
    assertErrorCode("{\"events\":[{\"failuresPerYear\":0.1,\"productionLossPercent\":101}]}", "INVALID_EVENT");
  }

  @Test
  void rejectsWrongShapesWithoutLeakingParserDetails() {
    assertErrorCode("[]", "INVALID_INPUT");
    assertErrorCode("{\"events\":[\"not-an-object\"]}", "INVALID_EVENT");
    JsonObject malformed = JsonParser.parseString(RiskMatrixRunner.run("{not-json")).getAsJsonObject();
    assertEquals("INVALID_INPUT", malformed.get("code").getAsString());
    assertFalse(malformed.get("message").getAsString().contains("line"));
  }

  @Test
  void boundsRequestEventsAndText() {
    assertErrorCode("{\"events\":[]}", "INVALID_INPUT");
    StringBuilder events = new StringBuilder("{\"events\":[");
    for (int i = 0; i < 101; i++) {
      if (i > 0) {
        events.append(',');
      }
      events.append("{\"probabilityLevel\":1,\"consequenceLevel\":1}");
    }
    events.append("]}");
    assertErrorCode(events.toString(), "TOO_MANY_EVENTS");
    assertErrorCode(
        "{\"events\":[{\"name\":\"" + repeat('n', 257) + "\",\"probabilityLevel\":1,\"consequenceLevel\":1}]}",
        "INVALID_EVENT");
    assertErrorCode(
        "{\"events\":[{\"mitigation\":\"" + repeat('m', 2049) + "\",\"probabilityLevel\":1,\"consequenceLevel\":1}]}",
        "INVALID_EVENT");
    assertErrorCode(
        "{\"events\":[{\"name\":\"" + repeat('x', 16400) + "\",\"probabilityLevel\":1,\"consequenceLevel\":1}]}",
        "REQUEST_TOO_LARGE");
  }

  @Test
  void preservesBoundariesAndDeterministicDefaults() {
    JsonObject obj = JsonParser
        .parseString(RiskMatrixRunner.run("{\"events\":[{\"failuresPerYear\":0,\"productionLossPercent\":100}]}"))
        .getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    JsonObject event = obj.getAsJsonArray("events").get(0).getAsJsonObject();
    assertEquals("Event 1", event.get("name").getAsString());
    assertEquals(1, event.get("probabilityLevel").getAsInt());
    assertEquals(5, event.get("consequenceLevel").getAsInt());
    assertTrue(obj.get("advisoryBoundary").getAsString().contains("caller supplies"));
  }

  private static void assertErrorCode(String input, String code) {
    JsonObject result = JsonParser.parseString(RiskMatrixRunner.run(input)).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertEquals(code, result.get("code").getAsString());
    assertTrue(result.get("screeningOnly").getAsBoolean());
    assertFalse(result.get("standardConformanceClaimed").getAsBoolean());
  }

  private static String repeat(char value, int count) {
    StringBuilder result = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      result.append(value);
    }
    return result.toString();
  }
}
