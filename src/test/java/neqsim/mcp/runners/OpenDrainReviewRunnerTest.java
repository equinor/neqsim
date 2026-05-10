package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Tests for the open-drain MCP runner.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class OpenDrainReviewRunnerTest {

  /**
   * Verifies that the runner returns a structured report and provenance.
   */
  @Test
  void testRunnerReturnsOpenDrainReport() {
    String json = "{\n" + "  \"projectName\": \"MCP open drain\",\n"
        + "  \"openDrainAreas\": [{\n" + "    \"areaId\": \"OD-R01\",\n"
        + "    \"areaType\": \"process area\",\n"
        + "    \"drainSystemType\": \"hazardous open drain\",\n"
        + "    \"standards\": \"NORSOK P-002; ISO 13702\",\n"
        + "    \"sourceHasFlammableOrHazardousLiquid\": true,\n"
        + "    \"hasOpenDrainMeasures\": true,\n"
        + "    \"drainageCapacityKgPerS\": 12.0,\n"
        + "    \"fireWaterCapacityKgPerS\": 6.0,\n"
        + "    \"liquidLeakRateKgPerS\": 5.0,\n"
        + "    \"backflowPrevented\": true,\n"
        + "    \"closedOpenDrainInteractionPrevented\": true,\n"
        + "    \"hazardousNonHazardousPhysicallySeparated\": true,\n"
        + "    \"sealDesignedForMaxBackpressure\": true,\n"
        + "    \"ventTerminatedSafe\": true,\n"
        + "    \"openDrainDependsOnUtility\": false\n" + "  }]\n" + "}";

    JsonObject output = JsonParser.parseString(OpenDrainReviewRunner.run(json)).getAsJsonObject();

    assertEquals("success", output.get("status").getAsString());
    assertEquals("open_drain_review", output.get("reviewType").getAsString());
    assertEquals("PASS", output.get("overallVerdict").getAsString());
    assertTrue(output.has("provenance"));
  }

  /**
   * Verifies that empty input returns a structured error.
   */
  @Test
  void testRunnerReportsMissingOpenDrainData() {
    JsonObject output = JsonParser.parseString(OpenDrainReviewRunner.run("{}"))
        .getAsJsonObject();

    assertEquals("error", output.get("status").getAsString());
    assertEquals("MISSING_OPEN_DRAIN_DATA",
        output.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }
}