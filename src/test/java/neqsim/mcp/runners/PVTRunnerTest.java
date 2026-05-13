package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link PVTRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PVTRunnerTest {

  @Test
  void testCME() {
    String result = PVTRunner.run(ExampleCatalog.pvtCME());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "CME failed: " + result);
    assertTrue(obj.has("data"), "CME should return data");
  }

  @Test
  void testSaturationPressure() {
    String result = PVTRunner.run(ExampleCatalog.pvtSaturationPressure());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(),
        "Saturation pressure failed: " + result);
  }

  @Test
  void testSaturationPressureE300FilePath() {
    String result = PVTRunner.run(ExampleCatalog.pvtE300SaturationPressure());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(),
        "E300 saturation pressure failed: " + result);
    assertEquals("e300File", obj.get("fluidSource").getAsString());
    assertTrue(obj.has("e300FilePath"));
    assertTrue(obj.getAsJsonObject("data").has("saturationPressure_bara"));
  }

  @Test
  void testE300WithComponentsWarnsAndIgnoresComponents() {
    String json = "{" + "\"model\": \"AUTO\"," + "\"experiment\": \"saturationPressure\","
        + "\"temperature_C\": 100.0," + "\"pressure_bara\": 200.0,"
        + "\"e300FilePath\": \"src/test/java/neqsim/thermo/util/readwrite/fluid1.e300\","
        + "\"components\": {\"metane\": 1.0}" + "}";

    String result = PVTRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), result);
    assertTrue(obj.has("warnings"));
  }

  @Test
  void testAutoModelWithoutE300FilePath() {
    String json = "{" + "\"model\": \"AUTO\"," + "\"experiment\": \"saturationPressure\","
        + "\"components\": {\"methane\": 1.0}" + "}";

    String result = PVTRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testMissingExperiment() {
    String json = "{\"components\": {\"methane\": 0.9, \"ethane\": 0.1}}";
    String result = PVTRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testUnknownExperiment() {
    String json = "{\"experiment\": \"UNKNOWN\", \"components\": {\"methane\": 0.9}}";
    String result = PVTRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testNullInput() {
    String result = PVTRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
