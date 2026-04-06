package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for the BatchRunner.
 */
class BatchRunnerTest {

  @Test
  void testTemperatureSweepBatch() {
    String json = "{" + "\"model\": \"SRK\","
        + "\"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05},"
        + "\"flashType\": \"TP\"," + "\"cases\": ["
        + "  {\"temperature\": {\"value\": 0.0, \"unit\": \"C\"}, "
        + "   \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}},"
        + "  {\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}, "
        + "   \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}},"
        + "  {\"temperature\": {\"value\": 50.0, \"unit\": \"C\"}, "
        + "   \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}}" + "]" + "}";

    String result = BatchRunner.run(json);
    assertNotNull(result);

    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("ok", obj.get("status").getAsString(), "Expected ok but got: " + result);

    // Check summary
    JsonObject summary = obj.getAsJsonObject("summary");
    assertEquals(3, summary.get("totalCases").getAsInt());
    assertEquals(3, summary.get("succeeded").getAsInt());
    assertEquals(0, summary.get("failed").getAsInt());

    // Check results array
    JsonArray results = obj.getAsJsonArray("results");
    assertEquals(3, results.size());
    for (int i = 0; i < 3; i++) {
      JsonObject caseResult = results.get(i).getAsJsonObject();
      assertTrue(caseResult.has("caseIndex"));
      assertEquals(i, caseResult.get("caseIndex").getAsInt());
    }

    // Check provenance
    assertTrue(obj.has("provenance"), "Response should include provenance metadata");
    JsonObject provenance = obj.getAsJsonObject("provenance");
    assertTrue(provenance.has("thermodynamicModel"));
    assertTrue(provenance.has("calculationType"));
    String calcType = provenance.get("calculationType").getAsString();
    assertTrue(calcType.contains("batch"), "Provenance should mention batch calculation");
  }

  @Test
  void testPressureSweepBatch() {
    String json = "{" + "\"model\": \"PR\"," + "\"components\": {\"methane\": 0.90, \"CO2\": 0.10},"
        + "\"flashType\": \"TP\"," + "\"cases\": ["
        + "  {\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}, "
        + "   \"pressure\": {\"value\": 10.0, \"unit\": \"bara\"}},"
        + "  {\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}, "
        + "   \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}}" + "]" + "}";

    String result = BatchRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("ok", obj.get("status").getAsString(), "Expected ok but got: " + result);
    assertEquals(2, obj.getAsJsonObject("summary").get("succeeded").getAsInt());
  }

  @Test
  void testEmptyCases() {
    String json = "{" + "\"model\": \"SRK\"," + "\"components\": {\"methane\": 0.85},"
        + "\"cases\": []" + "}";

    String result = BatchRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testMissingCases() {
    String json = "{" + "\"model\": \"SRK\"," + "\"components\": {\"methane\": 0.85}" + "}";

    String result = BatchRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testNullInput() {
    String result = BatchRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testPartialFailure() {
    // One case with valid components, one with invalid component
    String json = "{" + "\"model\": \"SRK\"," + "\"flashType\": \"TP\"," + "\"cases\": ["
        + "  {\"components\": {\"methane\": 0.85, \"ethane\": 0.15},"
        + "   \"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
        + "   \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}},"
        + "  {\"components\": {\"unobtanium\": 1.0},"
        + "   \"temperature\": {\"value\": 25.0, \"unit\": \"C\"},"
        + "   \"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}}" + "]" + "}";

    String result = BatchRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("partial", obj.get("status").getAsString(),
        "Expected partial success but got: " + result);

    JsonObject summary = obj.getAsJsonObject("summary");
    assertEquals(2, summary.get("totalCases").getAsInt());
    assertEquals(1, summary.get("succeeded").getAsInt());
    assertEquals(1, summary.get("failed").getAsInt());
  }

  @Test
  void testSingleCaseBatch() {
    String json = "{" + "\"model\": \"SRK\","
        + "\"components\": {\"nitrogen\": 0.02, \"methane\": 0.98}," + "\"flashType\": \"TP\","
        + "\"cases\": [" + "  {\"temperature\": {\"value\": 15.0, \"unit\": \"C\"}, "
        + "   \"pressure\": {\"value\": 100.0, \"unit\": \"bara\"}}" + "]" + "}";

    String result = BatchRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("ok", obj.get("status").getAsString());
    assertEquals(1, obj.getAsJsonObject("summary").get("succeeded").getAsInt());
  }
}
