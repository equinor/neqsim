package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    assertTrue(keyResults.get("maxPressure_bara").getAsDouble()
        >= keyResults.get("initialOutletPressure_bara").getAsDouble());
  }

  @Test
  void testPipelineRunnerDispatchesWaterHammerMode() {
    JsonObject input = JsonParser.parseString(ExampleCatalog.waterHammerValveClosure())
        .getAsJsonObject();
    input.addProperty("mode", "waterHammer");

    String result = PipelineRunner.run(input.toString());
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Water hammer failed: " + result);
    assertEquals("water_hammer_screening", obj.get("studyType").getAsString());
  }
}
