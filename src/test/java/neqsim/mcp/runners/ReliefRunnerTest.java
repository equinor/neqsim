package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for {@link ReliefRunner}.
 */
class ReliefRunnerTest {

  private static JsonObject run(String json) {
    return JsonParser.parseString(ReliefRunner.run(json)).getAsJsonObject();
  }

  private static void assertScreeningBoundary(JsonObject obj) {
    assertTrue(obj.get("screeningOnly").getAsBoolean());
    assertEquals(false, obj.get("standardConformanceClaimed").getAsBoolean());
    assertTrue(obj.get("advisoryBoundary").getAsString().contains("qualified pressure-relief"));
    assertTrue(obj.get("advisoryBoundary").getAsString().contains("not certification"));
  }

  @Test
  void testGasPSVContract() {
    JsonObject obj = run("{" + "\"case\":\"gas\"," + "\"massFlowRate_kg_s\":10.0," + "\"setPressure_bara\":20.0,"
        + "\"temperature_K\":350.0," + "\"molecularWeight_kg_mol\":0.020," + "\"compressibility\":0.95,"
        + "\"specificHeatRatio\":1.3" + "}");
    assertEquals("success", obj.get("status").getAsString());
    assertEquals("gas", obj.get("case").getAsString());
    JsonObject sizing = obj.getAsJsonObject("sizing");
    assertTrue(Double.isFinite(sizing.get("requiredArea_mm2").getAsDouble()));
    assertTrue(sizing.get("requiredArea_mm2").getAsDouble() > 0.0);
    assertTrue(sizing.get("selectedArea_mm2").getAsDouble() >= sizing.get("requiredArea_mm2").getAsDouble());
    assertTrue(sizing.has("recommendedOrifice"));
    assertScreeningBoundary(obj);
  }

  @Test
  void testLiquidPSVContract() {
    JsonObject obj = run("{" + "\"case\":\"liquid\"," + "\"volumeFlowRate_m3_s\":0.01,"
        + "\"liquidDensity_kg_m3\":850.0," + "\"setPressure_bara\":15.0" + "}");
    assertEquals("success", obj.get("status").getAsString());
    assertEquals("liquid", obj.get("case").getAsString());
    assertTrue(obj.getAsJsonObject("sizing").get("requiredArea_mm2").getAsDouble() > 0.0);
    assertScreeningBoundary(obj);
  }

  @Test
  void testTwoPhasePSVContract() {
    JsonObject obj = run("{" + "\"case\":\"twoPhase\"," + "\"massFlowRate_kg_s\":4.0," + "\"setPressure_bara\":12.0,"
        + "\"temperature_K\":330.0," + "\"gasMassFraction\":0.25," + "\"gasDensity_kg_m3\":12.0,"
        + "\"liquidDensity_kg_m3\":780.0," + "\"latentHeat_J_kg\":300000.0," + "\"liquidCp_J_kgK\":2200.0" + "}");
    assertEquals("success", obj.get("status").getAsString());
    assertEquals("twoPhase", obj.get("case").getAsString());
    assertTrue(obj.getAsJsonObject("sizing").get("requiredArea_mm2").getAsDouble() > 0.0);
    assertScreeningBoundary(obj);
  }

  @Test
  void testFireHeatInputContract() {
    JsonObject obj = run("{" + "\"case\":\"fireHeatInput\"," + "\"wettedArea_m2\":50.0," + "\"hasDrainage\":true,"
        + "\"hasFireFighting\":false" + "}");
    assertEquals("success", obj.get("status").getAsString());
    JsonObject q = obj.getAsJsonObject("fireHeatInput");
    assertTrue(q.get("heatInput_W").getAsDouble() > 0.0);
    assertTrue(q.get("heatInput_kW").getAsDouble() > 0.0);
    assertScreeningBoundary(obj);
  }

  @Test
  void testInvalidEngineeringInputsFailClosed() {
    assertEquals("error",
        run("{\"case\":\"gas\",\"massFlowRate_kg_s\":-1," + "\"setPressure_bara\":20,\"temperature_K\":350,"
            + "\"molecularWeight_kg_mol\":0.02}").get("status").getAsString());
    assertEquals("error",
        run("{\"case\":\"twoPhase\",\"massFlowRate_kg_s\":4,"
            + "\"setPressure_bara\":12,\"temperature_K\":330,\"gasMassFraction\":1.1,"
            + "\"gasDensity_kg_m3\":12,\"liquidDensity_kg_m3\":780,"
            + "\"latentHeat_J_kg\":300000,\"liquidCp_J_kgK\":2200}").get("status").getAsString());
    assertEquals("error",
        run("{\"case\":\"liquid\",\"volumeFlowRate_m3_s\":0.01,"
            + "\"liquidDensity_kg_m3\":850,\"setPressure_bara\":10,"
            + "\"backPressure_bara\":11,\"overpressureFraction\":0.1}").get("status").getAsString());
  }

  @Test
  void testMalformedUnknownAndOversizedInputsFailClosed() {
    assertEquals("error", run("{not-json").get("status").getAsString());
    assertEquals("error", run("{\"case\":\"plasma\"}").get("status").getAsString());
    assertEquals("error", run(null).get("status").getAsString());
    String oversized = "{\"case\":\"gas\",\"padding\":\""
        + new String(new char[ReliefRunner.MAX_INPUT_BYTES]).replace('\0', 'x') + "\"}";
    JsonObject result = run(oversized);
    assertEquals("error", result.get("status").getAsString());
    assertTrue(result.get("message").getAsString().contains("maximum size"));
    assertScreeningBoundary(result);
  }
}
