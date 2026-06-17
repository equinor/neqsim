package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgenticEngineeringRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AgenticEngineeringRunnerTest {

  /**
   * Verifies the MCP runner wraps kernel output with standard envelope fields.
   */
  @Test
  void testRunnerAddsStandardEnvelope() {
    String result = AgenticEngineeringRunner
        .run("{\"action\":\"plan\",\"task\":\"TEG dehydration with hydrate check\"}");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", root.get("status").getAsString());
    assertEquals("runAgenticEngineering", root.get("tool").getAsString());
    assertTrue(root.has("apiVersion"));
    assertTrue(root.has("data"));
    assertTrue(root.has("provenance"));
    assertTrue(root.has("validation"));
    assertTrue(root.has("qualityGate"));
  }

  /**
   * Verifies invalid actions return standardized errors.
   */
  @Test
  void testRunnerReportsUnknownAction() {
    String result = AgenticEngineeringRunner.run("{\"action\":\"unknown\"}");
    JsonObject root = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", root.get("status").getAsString());
    assertEquals("runAgenticEngineering", root.get("tool").getAsString());
    assertTrue(root.has("errors"));
  }
}
