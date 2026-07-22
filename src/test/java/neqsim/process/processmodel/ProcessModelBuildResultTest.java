package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the never-throwing structured multi-area builder {@link ProcessModel#buildFromJson(String)} and
 * {@link ProcessModel#buildFromJsonAndRun(String)} together with {@link ProcessModelResult}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessModelBuildResultTest {

  /**
   * Builds a two-area model (separation feeding a downstream cooler) and returns its JSON.
   *
   * @return JSON string for a valid two-area model with one inter-area link
   */
  private String twoAreaModelJson() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(40000.0, "kg/hr");
    Separator separator = new Separator("HP Sep", feed);

    ProcessSystem separation = new ProcessSystem();
    separation.add(feed);
    separation.add(separator);
    separation.run();

    Cooler downstreamCooler = new Cooler("Downstream Cooler", separator.getGasOutStream());
    downstreamCooler.setOutletTemperature(273.15 + 25.0);

    ProcessSystem downstream = new ProcessSystem();
    downstream.add(downstreamCooler);
    downstream.run();

    ProcessModel model = new ProcessModel();
    model.add("separation", separation);
    model.add("downstream", downstream);
    return model.toJson();
  }

  @Test
  void testBuildFromJsonSucceeds() {
    String json = twoAreaModelJson();
    ProcessModelResult result = ProcessModel.buildFromJson(json);

    assertTrue(result.isSuccess(), "Two-area model should build: " + result.toJson());
    assertNotNull(result.getModel(), "Built model should not be null");
    assertEquals(2, result.getModel().size(), "Both areas should be present");
    assertTrue(result.getFailedAreas().isEmpty(), "No areas should fail");
    Map<String, SimulationResult> areaResults = result.getAreaResults();
    assertTrue(areaResults.containsKey("separation"));
    assertTrue(areaResults.containsKey("downstream"));
    assertTrue(areaResults.get("separation").isSuccess());
    assertTrue(areaResults.get("downstream").isSuccess());
  }

  @Test
  void testBuildFromJsonAndRunProducesRunStatus() {
    String json = twoAreaModelJson();
    ProcessModelResult result = ProcessModel.buildFromJsonAndRun(json);

    assertTrue(result.isSuccess(), "Two-area model should build and run: " + result.toJson());
    assertNotNull(result.getModel(), "Built model should not be null");
    assertNotNull(result.getRunStatusJson(), "Run status JSON should be captured");
    JsonObject reported = JsonParser.parseString(result.toJson()).getAsJsonObject();
    assertEquals("success", reported.get("status").getAsString());
    assertEquals(ProcessModelResult.SCHEMA_VERSION, reported.get("schemaVersion").getAsString());
    assertTrue(reported.has("runStatus"), "Reported JSON should embed run status");
  }

  @Test
  void testNullInputReturnsErrorWithoutThrowing() {
    ProcessModelResult result = ProcessModel.buildFromJson(null);
    assertTrue(result.isError());
    assertFalse(result.getErrors().isEmpty());
    assertEquals("EMPTY_INPUT", result.getErrors().get(0).getCode());
  }

  @Test
  void testMissingAreasReturnsError() {
    ProcessModelResult result = ProcessModel.buildFromJson("{\"foo\": 1}");
    assertTrue(result.isError());
    assertEquals("MISSING_AREAS", result.getErrors().get(0).getCode());
  }

  @Test
  void testInvalidJsonReturnsParseError() {
    ProcessModelResult result = ProcessModel.buildFromJson("{not valid json");
    assertTrue(result.isError());
    assertEquals("JSON_PARSE_ERROR", result.getErrors().get(0).getCode());
  }

  @Test
  void testOneFailedAreaIsReportedButModelStillBuilds() {
    String json = twoAreaModelJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    // Corrupt the downstream area so it fails to build, leaving separation intact.
    JsonObject areas = root.getAsJsonObject("areas");
    JsonObject broken = new JsonObject();
    broken.addProperty("definitelyNotAValidArea", true);
    areas.add("downstream", broken);

    ProcessModelResult result = ProcessModel.buildFromJson(root.toString());

    // separation still builds → overall success, but downstream is reported as failed.
    assertTrue(result.isSuccess(), "Model should still build from the surviving area: " + result.toJson());
    assertTrue(result.getFailedAreas().contains("downstream"), "Downstream should be flagged as failed");
    assertNotNull(result.getModel().get("separation"), "Separation area should be present");
  }
}
