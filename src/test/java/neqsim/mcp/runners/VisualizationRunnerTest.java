package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link VisualizationRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class VisualizationRunnerTest {

  @Test
  void testPhaseEnvelopeSVG() {
    String json = "{" + "\"type\": \"phaseEnvelope\","
        + "\"components\": {\"methane\": 0.80, \"ethane\": 0.10, \"propane\": 0.05,"
        + "  \"n-butane\": 0.03, \"n-pentane\": 0.02}," + "\"model\": \"SRK\"" + "}";

    String result = VisualizationRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(),
        "Phase envelope viz failed: " + result);
    assertTrue(obj.has("svg") || obj.has("mermaid"), "Should contain rendered content");
  }

  @Test
  void testBarChart() {
    String json = "{" + "\"type\": \"barChart\"," + "\"title\": \"Pressure Comparison\","
        + "\"labels\": [\"Case 1\", \"Case 2\", \"Case 3\"]," + "\"values\": [50.0, 75.0, 100.0],"
        + "\"unit\": \"bara\"" + "}";

    String result = VisualizationRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Bar chart viz failed: " + result);
  }

  @Test
  void testStyledTable() {
    String json = "{" + "\"type\": \"propertyTable\"," + "\"caption\": \"Stream Summary\","
        + "\"headers\": [\"Property\", \"Feed\", \"Gas Out\", \"Liquid Out\"]," + "\"rows\": ["
        + "  [\"Temperature (C)\", \"25.0\", \"25.0\", \"25.0\"],"
        + "  [\"Pressure (bara)\", \"50.0\", \"50.0\", \"50.0\"]" + "]" + "}";

    String result = VisualizationRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Table viz failed: " + result);
  }

  @Test
  void testFlowsheetDiagram() {
    String json = "{" + "\"type\": \"flowsheet\"," + "\"equipment\": ["
        + "  {\"name\": \"Feed\", \"type\": \"Stream\"},"
        + "  {\"name\": \"HP Sep\", \"type\": \"Separator\"},"
        + "  {\"name\": \"Compressor\", \"type\": \"Compressor\"}" + "]" + "}";

    String result = VisualizationRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Flowsheet viz failed: " + result);
  }

  @Test
  void testCompressorMapDerivesPressureRatioFromPressures() {
    String json =
        "{" + "\"type\": \"compressorMap\"," + "\"inletFlow\": 5000.0," + "\"inletPressure\": 25.0,"
            + "\"outletPressure\": 100.0," + "\"efficiency\": 0.80," + "\"power_kW\": 2500.0" + "}";

    String result = VisualizationRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(),
        "Compressor map viz failed: " + result);
    assertTrue(obj.get("svg").getAsString().contains("PR: 4.00"),
        "Pressure ratio should be derived from inlet/outlet pressure when pressureRatio is not provided");
  }

  @Test
  void testNullInput() {
    String result = VisualizationRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testUnknownType() {
    String json = "{\"type\": \"UNKNOWN_VIZ_TYPE\"}";
    String result = VisualizationRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
