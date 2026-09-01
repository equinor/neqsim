package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link PipelineRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PipelineRunnerTest {

  @Test
  void testMultiphaseFlow() {
    String result = PipelineRunner.run(ExampleCatalog.pipelineMultiphase());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Pipeline failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = PipelineRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testWaterHammerRunnerExample() {
    String result = WaterHammerRunner.run(ExampleCatalog.waterHammerValveClosure());
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Water hammer failed: " + result);
    JsonObject keyResults = obj.getAsJsonObject("keyResults");
    assertTrue(
        keyResults.get("maxPressure_bara").getAsDouble() >= keyResults.get("initialOutletPressure_bara").getAsDouble());
  }

  @Test
  void testPipelineRunnerDispatchesWaterHammerMode() {
    JsonObject input = JsonParser.parseString(ExampleCatalog.waterHammerValveClosure()).getAsJsonObject();
    input.addProperty("mode", "waterHammer");

    String result = PipelineRunner.run(input.toString());
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Water hammer failed: " + result);
    assertEquals("water_hammer_screening", obj.get("studyType").getAsString());
  }

  @Test
  void testTwoFluidReturnsTypedFullProfile() {
    JsonObject input = twoFluidInput();

    String result = PipelineRunner.run(input.toString());
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Two-fluid pipeline failed: " + result);

    JsonObject data = obj.getAsJsonObject("data");
    assertTrue(data.has("outletPressureBara"));
    assertTrue(data.has("averageLiquidHoldup"));
    assertTrue(data.has("dominantFlowRegime"));
    JsonObject profile = data.getAsJsonObject("profile");
    assertEquals(5, profile.getAsJsonArray("positionM").size());
    assertEquals(5, profile.getAsJsonArray("pressureBara").size());
    assertEquals(5, profile.getAsJsonArray("temperatureC").size());
    assertEquals(5, profile.getAsJsonArray("liquidHoldup").size());
  }

  @Test
  void testTwoFluidSummaryOmitsProfile() {
    JsonObject input = twoFluidInput();
    input.addProperty("detailLevel", "SUMMARY");

    String result = PipelineRunner.run(input.toString());
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Two-fluid pipeline failed: " + result);

    JsonObject data = obj.getAsJsonObject("data");
    assertTrue(data.has("outletPressureBara"));
    assertTrue(data.has("averageLiquidHoldup"));
    assertFalse(data.has("profile"));
  }

  @Test
  void testTwoFluidAcceptsKelvinProfileAndReportDetailAlias() {
    JsonObject input = twoFluidInput();
    JsonObject pipe = input.getAsJsonObject("pipe");
    pipe.remove("surfaceTemperatureProfile_C");
    pipe.add("surfaceTemperatureProfile_K",
        JsonParser.parseString("[283.15,282.15,281.15,280.15,279.15]").getAsJsonArray());
    input.addProperty("reportDetail", "MINIMUM");

    String result = PipelineRunner.run(input.toString());
    JsonObject data = JsonParser.parseString(result).getAsJsonObject().getAsJsonObject("data");
    assertTrue(data.has("outletPressureBara"));
    assertTrue(data.has("averageLiquidHoldup"));
    assertFalse(data.has("profile"));
    assertFalse(data.has("outletTemperatureC"));
  }

  @Test
  void testUnknownPipelineSolverIsRejected() {
    JsonObject input = twoFluidInput();
    input.addProperty("solver", "unknownSolver");

    JsonObject result = JsonParser.parseString(PipelineRunner.run(input.toString())).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertEquals("UNKNOWN_PIPELINE_SOLVER",
        result.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  private JsonObject twoFluidInput() {
    return JsonParser.parseString("{\"solver\":\"twoFluid\",\"model\":\"SRK\","
        + "\"components\":{\"methane\":0.90,\"ethane\":0.04,\"propane\":0.02,\"n-heptane\":0.04},"
        + "\"temperature_C\":40.0,\"pressure_bara\":80.0,"
        + "\"flowRate\":{\"value\":30000.0,\"unit\":\"kg/hr\"},\"pipe\":{"
        + "\"length_m\":1000.0,\"diameter_m\":0.254,\"roughness_m\":4.6e-5,"
        + "\"sectionLengths_m\":[100.0,150.0,200.0,250.0,300.0]," + "\"elevationProfile_m\":[0.0,-5.0,-10.0,-4.0,2.0],"
        + "\"heatTransferProfile_W_m2K\":[5.0,5.0,4.0,3.0,3.0],"
        + "\"surfaceTemperatureProfile_C\":[10.0,9.0,8.0,7.0,6.0]}}").getAsJsonObject();
  }
}
