package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;

/**
 * Tests for {@link FlowAssuranceRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FlowAssuranceRunnerTest {

  /** Executes the agent-facing CPA example and checks all three separately converged boundaries. */
  @Test
  void testCpaWaterPhaseBoundaries() {
    JsonObject result = JsonParser.parseString(FlowAssuranceRunner.run(waterBoundaryInput().toString()))
        .getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    JsonObject data = result.getAsJsonObject("data");
    assertTrue(data.get("complete").getAsBoolean());
    assertEquals("CPAs-SRK-EOS-statoil", data.get("model").getAsString());
    assertEquals(10, data.get("mixingRule").getAsInt());
    assertEquals(50.0, data.get("pressure_bara").getAsDouble(), 0.0);
    assertEquals(0.0001, data.get("waterMoleFraction").getAsDouble(), 1.0e-14);
    assertEquals(-6.73048, data.getAsJsonObject("liquidWater").get("temperature_C").getAsDouble(), 0.001);
    assertEquals(-5.99035, data.getAsJsonObject("ice").get("temperature_C").getAsDouble(), 0.001);
    assertEquals(-2.79076, data.getAsJsonObject("hydrate").get("temperature_C").getAsDouble(), 0.001);
    for (String name : new String[] {"liquidWater", "ice", "hydrate"}) {
      JsonObject boundary = data.getAsJsonObject(name);
      assertEquals("CONVERGED", boundary.get("status").getAsString());
      assertTrue(Double.isFinite(boundary.get("residual").getAsDouble()));
      assertTrue(boundary.get("waterActivity").getAsDouble() > 0.0);
      assertTrue(boundary.has("phaseReference") && boundary.has("diagnostic"));
    }
    assertTrue(data.get("explanation").getAsString().contains("not a sequential cooling path"));
    assertTrue(result.getAsJsonObject("provenance").get("converged").getAsBoolean());
    assertTrue(!result.toString().contains("NaN"));
  }

  /** A flat absolute-pressure input must override the legacy runner's default, with CPA selected by the analysis. */
  @Test
  void testWaterBoundariesHonorPressureAndDefaultCpaModel() {
    JsonObject input = waterBoundaryInput();
    input.remove("model");
    input.addProperty("pressure_bara", 20.0);
    JsonObject result = JsonParser.parseString(FlowAssuranceRunner.run(input.toString())).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals(20.0, result.getAsJsonObject("data").get("pressure_bara").getAsDouble(), 0.0);
    assertEquals(-14.73267, result.getAsJsonObject("data").getAsJsonObject("ice").get("temperature_C").getAsDouble(),
        0.001);
  }

  /** Oil and MEG remain in the inventory, and an inapplicable gas dew point cannot become an overall success. */
  @Test
  void testWaterBoundariesRetainOilAndMegWithPartialStatus() {
    JsonObject input = waterBoundaryInput();
    input.add("components",
        JsonParser.parseString("{\"methane\":0.70,\"ethane\":0.05,\"n-heptane\":0.10,\"water\":0.10,\"MEG\":0.05}"));
    JsonObject result = JsonParser.parseString(FlowAssuranceRunner.run(input.toString())).getAsJsonObject();
    assertEquals("partial", result.get("status").getAsString(), result.toString());
    JsonObject data = result.getAsJsonObject("data");
    assertTrue(!data.get("complete").getAsBoolean());
    JsonObject liquid = data.getAsJsonObject("liquidWater");
    assertEquals("NOT_APPLICABLE", liquid.get("status").getAsString());
    assertTrue(liquid.get("temperature_C").isJsonNull());
    assertTrue(liquid.get("residual").isJsonNull());
    assertTrue(liquid.get("waterActivity").isJsonNull());
    assertEquals(0.05, data.getAsJsonObject("moleFractions").get("MEG").getAsDouble(), 1.0e-14);
    assertEquals(0.10, data.getAsJsonObject("moleFractions").get("n-heptane").getAsDouble(), 1.0e-14);
    assertTrue(data.getAsJsonObject("hydrate").get("phaseReference").getAsString().contains("x_MEG="));
    assertTrue(data.getAsJsonObject("ice").get("phaseReference").getAsString().contains("x_MEG="));
    assertTrue(!result.getAsJsonObject("provenance").get("converged").getAsBoolean());
  }

  /** A typed pseudo-component definition must be retained instead of discarded when building the CPA fluid. */
  @Test
  void testWaterBoundariesRetainCharacterizedOil() {
    JsonObject input = waterBoundaryInput();
    input.add("components", JsonParser.parseString("{\"methane\":0.75,\"water\":0.10,\"MEG\":0.05}"));
    input.add("tbpFractions", JsonParser.parseString(
        "[{\"name\":\"oil-cut\",\"moleFraction\":0.10," + "\"molarMass_kg_mol\":0.20,\"density_g_cm3\":0.85}]"));
    JsonObject result = JsonParser.parseString(FlowAssuranceRunner.run(input.toString())).getAsJsonObject();
    assertEquals("partial", result.get("status").getAsString(), result.toString());
    JsonObject data = result.getAsJsonObject("data");
    assertTrue(data.getAsJsonObject("moleFractions").toString().contains("oil-cut"));
    assertEquals(0.05, data.getAsJsonObject("moleFractions").get("MEG").getAsDouble(), 1.0e-14);
    assertEquals("NOT_APPLICABLE", data.getAsJsonObject("liquidWater").get("status").getAsString());
  }

  /** Dry gas and explicitly incompatible model settings must fail before reporting any usable boundary. */
  @Test
  void testWaterBoundariesRejectInvalidInputs() {
    JsonObject input = waterBoundaryInput();
    input.getAsJsonObject("components").remove("water");
    String result = FlowAssuranceRunner.run(input.toString());
    assertEquals("error", JsonParser.parseString(result).getAsJsonObject().get("status").getAsString());
    assertTrue(result.contains("water"));
    input = waterBoundaryInput();
    input.addProperty("model", "SRK");
    result = FlowAssuranceRunner.run(input.toString());
    assertEquals("error", JsonParser.parseString(result).getAsJsonObject().get("status").getAsString());
    assertTrue(result.contains("CPA"));
    input = waterBoundaryInput();
    input.remove("pressure_bara");
    result = FlowAssuranceRunner.run(input.toString());
    assertEquals("error", JsonParser.parseString(result).getAsJsonObject().get("status").getAsString());
    assertTrue(result.contains("pressure_bara"));
  }

  /** The discoverable tool schema exposes the complete boundary analysis and its oil-fraction input contract. */
  @Test
  void testWaterBoundarySchema() {
    JsonObject schema = JsonParser.parseString(SchemaCatalog.flowAssuranceInputSchema()).getAsJsonObject();
    JsonObject properties = schema.getAsJsonObject("properties");
    assertTrue(
        properties.getAsJsonObject("analysis").getAsJsonArray("enum").toString().contains("waterPhaseBoundaries"));
    assertTrue(properties.has("tbpFractions") && properties.has("plusFraction"));
    assertTrue(schema.has("allOf"));
    JsonObject outputSchema = JsonParser.parseString(SchemaCatalog.getSchema("run_flow_assurance", "output"))
        .getAsJsonObject();
    assertTrue(outputSchema.getAsJsonObject("properties").getAsJsonObject("status").getAsJsonArray("enum").toString()
        .contains("partial"));
  }

  /**
   * Provides the executable JSON example used by the agent-facing CPA boundary workflow.
   *
   * @return normalized 90/8/2 dry-gas composition with 100 ppmv wet water at 50 bara
   */
  private JsonObject waterBoundaryInput() {
    return JsonParser.parseString("{\"analysis\":\"waterPhaseBoundaries\",\"model\":\"CPA\","
        + "\"temperature_C\":20.0,\"pressure_bara\":50.0,\"components\":{"
        + "\"methane\":0.89991,\"ethane\":0.079992,\"propane\":0.019998,\"water\":0.0001}}").getAsJsonObject();
  }

  @Test
  void testHydrateRisk() {
    String result = FlowAssuranceRunner.run(ExampleCatalog.flowAssuranceHydrate());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), result);
    JsonObject data = obj.getAsJsonObject("data");
    // The shipped example (CPA wet gas, 20 C, 100 bara) must give a real hydrate temperature, not NaN -> LOW.
    assertEquals(0, data.get("unknownPointCount").getAsInt(), result);
    JsonObject point = data.getAsJsonArray("profile").get(0).getAsJsonObject();
    double hydrateT = point.get("hydrateTemperature_C").getAsDouble();
    assertTrue(hydrateT > 5.0 && hydrateT < 30.0, "hydrate T at 100 bara should be ~15-25 C, got " + hydrateT);
    assertTrue(!"UNKNOWN".equals(data.get("overallRisk").getAsString()));
  }

  @Test
  void testCpaDefaultsToCpaMixingRule() {
    // With the SRK kij database ("classic") CPA gives ~0 C here; with the CPA rule ~16 C (SRK classic: 16.3 C).
    String input = "{\"model\":\"CPA\",\"temperature_C\":10.0,\"pressure_bara\":100.0,"
        + "\"components\":{\"methane\":0.9,\"ethane\":0.05,\"CO2\":0.02,\"water\":0.03},\"analysis\":\"hydrateRiskMap\"}";
    JsonObject obj = JsonParser.parseString(FlowAssuranceRunner.run(input)).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), obj.toString());
    double hydrateT = obj.getAsJsonObject("data").getAsJsonArray("profile").get(0).getAsJsonObject()
        .get("hydrateTemperature_C").getAsDouble();
    assertTrue(hydrateT > 10.0 && hydrateT < 22.0, "CPA hydrate T at 100 bara should be ~16 C, got " + hydrateT);
  }

  @Test
  void testHydrateRiskWithoutWaterIsErrorNotLowRisk() {
    String input = "{\"model\":\"SRK\",\"temperature_C\":10.0,\"pressure_bara\":100.0,"
        + "\"components\":{\"methane\":0.9,\"ethane\":0.1},\"analysis\":\"hydrateRiskMap\"}";
    JsonObject obj = JsonParser.parseString(FlowAssuranceRunner.run(input)).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString(), obj.toString());
    String text = obj.toString();
    assertTrue(text.contains("RESULT_NOT_AVAILABLE"), text);
    assertTrue(text.contains("water"), text);
    assertTrue(obj.has("partialData"));
  }

  @Test
  void testNullInput() {
    String result = FlowAssuranceRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testDemulsifierDoseOptimization() {
    String input = "{" + "\"analysis\":\"demulsifierDoseOptimization\"," + "\"untreatedOIW_mgL\":120.0,"
        + "\"waterRate_m3_h\":100.0," + "\"halfEffectDosePpm\":10.0," + "\"maxRemovalFraction\":0.85,"
        + "\"optimumDosePpm\":60.0," + "\"holdUpVolume_m3\":10.0," + "\"projectedDailyWater_m3\":2400.0,"
        + "\"monthlySamples\":[{\"oiw_mgL\":20.0,\"waterVolume_m3\":2400.0}]," + "\"dayOfMonth\":5" + "}";

    String result = FlowAssuranceRunner.run(input);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.getAsJsonObject("data").has("lastRecommendation"));
    assertTrue(
        obj.getAsJsonObject("data").getAsJsonObject("lastRecommendation").get("setpointDosePpm").getAsDouble() > 0.0);
  }
}
