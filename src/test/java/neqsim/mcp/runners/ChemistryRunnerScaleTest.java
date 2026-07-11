package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests the {@code multiMineralScale} analysis exposed by {@link ChemistryRunner} for agentic use.
 */
public class ChemistryRunnerScaleTest {

  @Test
  void testMultiMineralScaleIsSupported() {
    assertTrue(ChemistryRunner.getSupportedAnalyses().contains("multiMineralScale"),
        "multiMineralScale must be an advertised analysis");
  }

  @Test
  void testMultiMineralScalePrecipitatesBarite() {
    String json = "{" + "\"analysis\":\"multiMineralScale\"," + "\"temperature_C\":70,\"pressure_bara\":100,"
        + "\"ca_mgL\":500,\"ba_mgL\":300,\"so4_mgL\":2000,\"hco3_mgL\":300,"
        + "\"tds_mgL\":60000,\"pCO2_bar\":1.0,\"waterFlow_LPerDay\":100000}";
    String out = ChemistryRunner.run(json);
    JsonObject result = JsonParser.parseString(out).getAsJsonObject();

    assertEquals("success", result.get("status").getAsString(), "Analysis should succeed: " + out);
    JsonObject data = result.getAsJsonObject("data");
    assertTrue(data.has("minerals"), "Result must list minerals");
    assertTrue(data.get("totalScaleMass_mgL").getAsDouble() > 0.0, "Barite brine should precipitate scale");
    assertTrue(data.has("scaleRates_kgPerDay"), "A kg/day rate must be returned when water flow is supplied");
    double totalRate = data.getAsJsonObject("scaleRates_kgPerDay").get("total").getAsDouble();
    assertTrue(totalRate > 0.0, "Total scaling rate must be positive");
    assertTrue(data.getAsJsonObject("minerals").getAsJsonObject("BaSO4").get("precipitated_mgL").getAsDouble() > 0.0,
        "Barite must be reported as precipitating");
  }

  @Test
  void testMultiMineralScaleBdotModel() {
    String json = "{" + "\"analysis\":\"multiMineralScale\",\"activityModel\":\"BDOT\","
        + "\"temperature_C\":90,\"pressure_bara\":200,"
        + "\"ca_mgL\":15000,\"ba_mgL\":400,\"sr_mgL\":800,\"so4_mgL\":600,\"hco3_mgL\":200,"
        + "\"tds_mgL\":200000,\"pCO2_bar\":2.0}";
    String out = ChemistryRunner.run(json);
    JsonObject result = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), "B-dot analysis should succeed: " + out);
    JsonObject data = result.getAsJsonObject("data");
    assertEquals("BDOT", data.getAsJsonObject("conditions").get("activityModel").getAsString());
  }
}
