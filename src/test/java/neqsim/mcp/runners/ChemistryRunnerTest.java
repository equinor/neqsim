package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Smoke tests for the chemistry MCP runner.
 *
 * @author ESOL
 * @version 1.0
 */
class ChemistryRunnerTest {

  @Test
  void electrolyteScaleViaJson() {
    String json = "{\"analysis\":\"electrolyteScale\",\"temperature_C\":60,\"pH\":7.5,"
        + "\"pCO2_bar\":1.0,\"ca_mgL\":600,\"hco3_mgL\":300}";
    String out = ChemistryRunner.run(json);
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.has("data"));
    assertTrue(obj.getAsJsonObject("data").has("ionicStrengthMolKg")
        || obj.getAsJsonObject("data").has("ionicStrength_molkg")
        || obj.getAsJsonObject("data").has("ionicStrength"));
  }

  @Test
  void mechanisticCorrosionViaJson() {
    String json = "{\"analysis\":\"mechanisticCorrosion\",\"temperature_C\":60,"
        + "\"pressure_bara\":80,\"co2_mol\":0.05,\"velocity_ms\":2.0,"
        + "\"diameter_m\":0.15,\"dose_mgL\":50}";
    String out = ChemistryRunner.run(json);
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
  }

  @Test
  void langmuirInhibitorViaJson() {
    String json = "{\"analysis\":\"langmuirInhibitor\",\"temperature_C\":60,"
        + "\"dose_mgL\":50,\"targetEfficiency\":0.5}";
    String out = ChemistryRunner.run(json);
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.getAsJsonObject("data").has("efficiency"));
  }

  @Test
  void packedBedScavengerViaJson() {
    String json = "{\"analysis\":\"packedBedScavenger\",\"diameter_m\":0.5,"
        + "\"height_m\":2.0,\"k_per_s\":8.0,\"cInlet_molm3\":1.0,"
        + "\"flow_m3s\":0.005,\"nCells\":20,\"nTimeSteps\":50,\"simTime_s\":864000}";
    String out = ChemistryRunner.run(json);
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
  }

  @Test
  void rejectsUnknownAnalysis() {
    String out = ChemistryRunner.run("{\"analysis\":\"unknown\"}");
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
