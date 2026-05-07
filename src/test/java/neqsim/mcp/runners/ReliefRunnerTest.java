package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link ReliefRunner}.
 */
class ReliefRunnerTest {

  @Test
  void testGasPSV() {
    String json = "{"
        + "\"case\":\"gas\","
        + "\"massFlowRate_kg_s\":10.0,"
        + "\"setPressure_bara\":20.0,"
        + "\"temperature_K\":350.0,"
        + "\"molecularWeight_kg_mol\":0.020,"
        + "\"compressibility\":0.95,"
        + "\"specificHeatRatio\":1.3"
        + "}";
    String result = ReliefRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertEquals("gas", obj.get("case").getAsString());
    JsonObject sizing = obj.getAsJsonObject("sizing");
    assertTrue(sizing.has("requiredArea_mm2"));
    assertTrue(sizing.get("requiredArea_mm2").getAsDouble() > 0.0);
    assertTrue(sizing.has("recommendedOrifice"));
  }

  @Test
  void testLiquidPSV() {
    String json = "{"
        + "\"case\":\"liquid\","
        + "\"volumeFlowRate_m3_s\":0.01,"
        + "\"liquidDensity_kg_m3\":850.0,"
        + "\"setPressure_bara\":15.0"
        + "}";
    String result = ReliefRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertEquals("liquid", obj.get("case").getAsString());
    assertTrue(obj.getAsJsonObject("sizing").get("requiredArea_mm2").getAsDouble() > 0.0);
  }

  @Test
  void testFireHeatInput() {
    String json = "{"
        + "\"case\":\"fireHeatInput\","
        + "\"wettedArea_m2\":50.0,"
        + "\"hasDrainage\":true,"
        + "\"hasFireFighting\":false"
        + "}";
    String result = ReliefRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    JsonObject q = obj.getAsJsonObject("fireHeatInput");
    assertTrue(q.get("heatInput_W").getAsDouble() > 0.0);
    assertTrue(q.get("heatInput_kW").getAsDouble() > 0.0);
  }

  @Test
  void testUnknownCase() {
    String result = ReliefRunner.run("{\"case\":\"plasma\"}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testNullInput() {
    JsonObject obj = JsonParser.parseString(ReliefRunner.run(null)).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
