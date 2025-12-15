package neqsim.process.util.monitor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for SplitterResponse JSON response handling.
 *
 * @author esol
 */
public class SplitterResponseTest {

  private Splitter splitter;
  private Stream inletStream;

  @BeforeEach
  public void setUp() {
    neqsim.thermo.system.SystemInterface testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);

    inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(85.0, "bara");
    inletStream.setTemperature(35.0, "C");
    inletStream.setFlowRate(5.0, "MSm3/day");
    inletStream.run();

    splitter = new Splitter("splitter", inletStream, 3);
    splitter.setSplitFactors(new double[] {0.8, 0.15, 0.05});
    splitter.run();
  }

  @Test
  void testSplitterToJson() {
    String json = splitter.toJson();
    assertNotNull(json, "JSON response should not be null");
    assertTrue(json.length() > 0, "JSON response should not be empty");
    System.out.println("JSON output: " + json);

    // Parse JSON to verify it's valid
    JsonElement element = JsonParser.parseString(json);
    assertNotNull(element, "JSON should be parseable");
    assertTrue(element.isJsonObject(), "JSON should be an object");
  }

  @Test
  void testSplitterJsonContainsInletData() {
    String json = splitter.toJson();
    JsonElement element = JsonParser.parseString(json);
    assertTrue(element.getAsJsonObject().has("data"), "JSON should contain data object");
    com.google.gson.JsonObject dataObj = element.getAsJsonObject().getAsJsonObject("data");
    assertTrue(dataObj.has("inlet mass flow"), "data should contain inlet mass flow");
    assertTrue(dataObj.has("inlet temperature"), "data should contain inlet temperature");
    assertTrue(dataObj.has("inlet pressure"), "data should contain inlet pressure");
  }

  @Test
  void testSplitterJsonContainsOutletData() {
    String json = splitter.toJson();
    JsonElement element = JsonParser.parseString(json);
    assertTrue(element.getAsJsonObject().has("data"), "JSON should contain data object");
    com.google.gson.JsonObject dataObj = element.getAsJsonObject().getAsJsonObject("data");
    assertTrue(dataObj.has("outlet 1 mass flow"), "data should contain outlet 1 mass flow");
    assertTrue(dataObj.has("outlet 1 temperature"), "data should contain outlet 1 temperature");
    assertTrue(dataObj.has("outlet 1 pressure"), "data should contain outlet 1 pressure");
    assertTrue(dataObj.has("outlet 1 split factor"), "data should contain outlet 1 split factor");
  }

  @Test
  void testSplitterJsonMultipleOutlets() {
    String json = splitter.toJson();
    JsonElement element = JsonParser.parseString(json);
    assertTrue(element.getAsJsonObject().has("data"), "JSON should contain data object");
    com.google.gson.JsonObject dataObj = element.getAsJsonObject().getAsJsonObject("data");
    assertTrue(dataObj.has("outlet 2 mass flow"), "data should contain outlet 2 mass flow");
    assertTrue(dataObj.has("outlet 3 mass flow"), "data should contain outlet 3 mass flow");
  }

  @Test
  void testSplitterJsonWithReportConfig() {
    neqsim.process.util.report.ReportConfig config = new neqsim.process.util.report.ReportConfig();
    String json = splitter.toJson(config);
    assertNotNull(json, "JSON response should not be null");
    assertTrue(json.length() > 0, "JSON response should not be empty");

    JsonElement element = JsonParser.parseString(json);
    assertNotNull(element, "JSON should be parseable");
    assertTrue(element.isJsonObject(), "JSON should be an object");
  }

  @Test
  void testSplitterResponseCreation() {
    SplitterResponse response = new SplitterResponse(splitter);
    assertNotNull(response, "Response should be created");
    assertTrue(response.data.size() > 0, "Response should contain data");
    assertTrue(response.data.containsKey("inlet mass flow"),
        "Response should contain inlet mass flow");
  }
}
