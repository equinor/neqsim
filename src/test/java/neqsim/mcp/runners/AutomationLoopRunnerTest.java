package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for the stateless closed-loop automation methods on {@link AutomationRunner} — {@code runLoop} (build-once /
 * sweep-many) and {@code getAdjustableParameters}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AutomationLoopRunnerTest {

  /** A small compressor process with an adjustable discharge pressure. */
  private static String compressorProcessJson() {
    return "{" + "\"fluid\": {" + "  \"model\": \"SRK\"," + "  \"temperature\": 298.15," + "  \"pressure\": 50.0,"
        + "  \"mixingRule\": \"classic\","
        + "  \"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}" + "}," + "\"process\": ["
        + "  {\"type\": \"Stream\", \"name\": \"feed\"," + "   \"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Compressor\", \"name\": \"Compressor\", \"inlet\": \"feed\","
        + "   \"properties\": {\"outletPressure\": [100.0, \"bara\"]}}" + "]" + "}";
  }

  @Test
  void testRunLoopSweepsAllTrials() {
    String trials = "[{\"Compressor.outletPressure\": 90.0}," + "{\"Compressor.outletPressure\": 110.0},"
        + "{\"Compressor.outletPressure\": 130.0}]";
    String readbacks = "[\"Compressor.power\"]";

    String result = AutomationRunner.runLoop(compressorProcessJson(), trials, readbacks, "bara", "kW");
    assertNotNull(result);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), "runLoop failed: " + result);

    JsonObject data = root.getAsJsonObject("data");
    assertEquals(3, data.get("trialCount").getAsInt());
    assertTrue(data.has("feasibleCount"));
    JsonArray trialResults = data.getAsJsonArray("trials");
    assertEquals(3, trialResults.size());

    // Each trial carries its index and a feasible flag from evaluate().
    for (int i = 0; i < trialResults.size(); i++) {
      JsonObject trial = trialResults.get(i).getAsJsonObject();
      assertEquals(i, trial.get("trialIndex").getAsInt());
      assertTrue(trial.has("feasible"), "Trial should expose feasible flag");
    }
  }

  @Test
  void testRunLoopBadAddressDegradesSingleTrial() {
    // One good trial, one with an unknown address — the bad one must not crash the sweep.
    String trials = "[{\"Compressor.outletPressure\": 110.0}," + "{\"NoSuchUnit.outletPressure\": 120.0}]";
    String result = AutomationRunner.runLoop(compressorProcessJson(), trials, "[]", "bara", null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString());
    JsonArray trialResults = root.getAsJsonObject("data").getAsJsonArray("trials");
    assertEquals(2, trialResults.size());
    // The second trial should have rejected the bad setpoint rather than throwing.
    JsonObject badTrial = trialResults.get(1).getAsJsonObject();
    assertTrue(badTrial.has("setpointsRejected"));
  }

  @Test
  void testRunLoopNullProcessIsError() {
    String result = AutomationRunner.runLoop(null, "[]", "[]", null, null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", root.get("status").getAsString());
  }

  @Test
  void testRunLoopNullTrialsIsError() {
    String result = AutomationRunner.runLoop(compressorProcessJson(), null, "[]", null, null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", root.get("status").getAsString());
  }

  @Test
  void testGetAdjustableParameters() {
    String result = AutomationRunner.getAdjustableParameters(compressorProcessJson());
    assertNotNull(result);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString(), "getAdjustableParameters failed: " + result);
    JsonObject data = root.getAsJsonObject("data");
    assertTrue(data.has("parameters"), "Should expose a parameters array");
    assertTrue(data.has("count"));
  }

  @Test
  void testGetAdjustableParametersNullIsError() {
    String result = AutomationRunner.getAdjustableParameters(null);
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", root.get("status").getAsString());
  }
}
