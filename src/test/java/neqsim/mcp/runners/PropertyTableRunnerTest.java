package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for the PropertyTableRunner.
 */
class PropertyTableRunnerTest {

  @Test
  void testTemperatureSweep() {
    String json = "{" + "\"model\": \"SRK\","
        + "\"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05},"
        + "\"sweep\": \"temperature\"," + "\"sweepFrom\": {\"value\": -20.0, \"unit\": \"C\"},"
        + "\"sweepTo\": {\"value\": 60.0, \"unit\": \"C\"}," + "\"points\": 10,"
        + "\"fixedPressure\": {\"value\": 50.0, \"unit\": \"bara\"},"
        + "\"properties\": [\"density\", \"viscosity\", \"Cp\", \"Z\"]" + "}";

    String result = PropertyTableRunner.run(json);
    assertNotNull(result);

    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue(obj.has("status"));
    assertTrue("success".equals(obj.get("status").getAsString()),
        "Expected success but got: " + result);
    assertTrue(obj.has("table"));
    assertTrue(obj.getAsJsonArray("table").size() > 0, "Table should have data points");
    assertTrue(obj.has("provenance"), "Response should include provenance metadata");

    // Check provenance has required fields
    JsonObject provenance = obj.getAsJsonObject("provenance");
    assertTrue(provenance.has("thermodynamicModel"));
    assertTrue(provenance.has("calculationType"));
    assertTrue(provenance.has("assumptions"));
    assertTrue(provenance.has("limitations"));
  }

  @Test
  void testPressureSweep() {
    String json = "{" + "\"model\": \"PR\"," + "\"components\": {\"methane\": 0.90, \"CO2\": 0.10},"
        + "\"sweep\": \"pressure\"," + "\"sweepFrom\": {\"value\": 10.0, \"unit\": \"bara\"},"
        + "\"sweepTo\": {\"value\": 100.0, \"unit\": \"bara\"}," + "\"points\": 5,"
        + "\"fixedTemperature\": {\"value\": 25.0, \"unit\": \"C\"}" + "}";

    String result = PropertyTableRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("success".equals(obj.get("status").getAsString()),
        "Expected success but got: " + result);
    assertTrue(obj.getAsJsonArray("table").size() == 5);
  }

  @Test
  void testMissingComponents() {
    String json = "{\"sweep\": \"temperature\"}";
    String result = PropertyTableRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("error".equals(obj.get("status").getAsString()));
    assertTrue(obj.get("code").getAsString().contains("MISSING"));
  }

  @Test
  void testNullInput() {
    String result = PropertyTableRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("error".equals(obj.get("status").getAsString()));
  }
}
