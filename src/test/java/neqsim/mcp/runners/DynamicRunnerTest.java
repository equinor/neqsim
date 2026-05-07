package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link DynamicRunner}.
 *
 * <p>
 * Covers the runDynamic JSON \u2192 transient simulation path used by the MCP server, with focus on
 * the depressurization / blowdown scenario that previously NPE'd inside
 * {@code Separator.runTransient} because {@code gasOutStream} was null.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DynamicRunnerTest {

  /**
   * Minimal methane / n-decane separator with continuous feed, dynamically time-stepped. This is
   * the canonical happy path \u2014 the MCP server's runDynamic tool must succeed end-to-end.
   */
  @Test
  void testRun_methaneDecaneSeparatorTransient() {
    String processJson = "{"
        + "\"fluid\": {\"model\": \"SRK\", \"temperature\": 298.15, \"pressure\": 50.0,"
        + "  \"mixingRule\": \"classic\"," + "  \"components\": {\"methane\": 0.9, \"nC10\": 0.1}},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [1000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}" + "]}";

    JsonObject input = new JsonObject();
    input.addProperty("processJson", processJson);
    input.addProperty("duration_seconds", 5.0);
    input.addProperty("timeStep_seconds", 1.0);

    String result = DynamicRunner.run(input.toString());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString(),
        "runDynamic must succeed for a wired separator: " + result);
    JsonObject data = root.getAsJsonObject("data");
    assertNotNull(data);
    assertTrue(data.has("transmitters"));
    JsonObject transmitters = data.getAsJsonObject("transmitters");
    assertTrue(transmitters.has("PT-HP Sep"),
        "Pressure transmitter must be auto-instrumented on the separator gas outlet");
  }

  /**
   * Regression for the gasOutStream NPE: a separator without an inlet must not produce a raw
   * NullPointerException. The runner must surface a clear, actionable diagnostic instead.
   */
  @Test
  void testRun_orphanSeparatorGivesClearError() {
    // Separator declared with no 'inlet' \u2014 previously NPE'd at the first time step.
    String processJson = "{"
        + "\"fluid\": {\"model\": \"SRK\", \"temperature\": 298.15, \"pressure\": 50.0,"
        + "  \"mixingRule\": \"classic\"," + "  \"components\": {\"methane\": 0.9, \"nC10\": 0.1}},"
        + "\"process\": [" + "  {\"type\": \"Separator\", \"name\": \"OrphanSep\"}" + "]}";

    JsonObject input = new JsonObject();
    input.addProperty("processJson", processJson);
    input.addProperty("duration_seconds", 2.0);
    input.addProperty("timeStep_seconds", 1.0);

    String result = DynamicRunner.run(input.toString());
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();

    // The build may already error out at steady state; either way the response must NOT be
    // a raw NPE \u2014 it must be a structured error JSON the MCP client can parse.
    assertEquals("error", root.get("status").getAsString(), result);
    String body = result.toLowerCase();
    assertTrue(!body.contains("nullpointerexception"),
        "Response must not leak a raw NullPointerException: " + result);
  }
}
