package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link LOPARunner}.
 */
class LOPARunnerTest {

  @Test
  void testTargetMet() {
    String json = "{"
        + "\"scenario\":\"Overpressure of HP separator\","
        + "\"initiatingEventFrequency_per_year\":0.1,"
        + "\"targetFrequency_per_year\":1.0e-5,"
        + "\"layers\":["
        + "  {\"name\":\"BPCS\",\"pfd\":0.1},"
        + "  {\"name\":\"Operator response\",\"pfd\":0.1},"
        + "  {\"name\":\"Relief valve\",\"pfd\":0.01},"
        + "  {\"name\":\"SIF\",\"pfd\":0.01}"
        + "]"
        + "}";
    String result = LOPARunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.has("lopa"));
    JsonObject gap = obj.getAsJsonObject("gapAnalysis");
    assertTrue(gap.get("targetMet").getAsBoolean());
    assertTrue(gap.get("totalRRF").getAsDouble() > 1.0);
  }

  @Test
  void testTargetNotMet() {
    String json = "{"
        + "\"scenario\":\"Insufficient mitigation\","
        + "\"initiatingEventFrequency_per_year\":0.5,"
        + "\"targetFrequency_per_year\":1.0e-5,"
        + "\"layers\":["
        + "  {\"name\":\"BPCS\",\"pfd\":0.1}"
        + "]"
        + "}";
    String result = LOPARunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    JsonObject gap = obj.getAsJsonObject("gapAnalysis");
    assertEquals(false, gap.get("targetMet").getAsBoolean());
    assertTrue(gap.has("requiredAdditionalSIL"));
    assertTrue(gap.get("requiredAdditionalRRF").getAsDouble() > 1.0);
  }

  @Test
  void testMissingFields() {
    String result = LOPARunner.run("{\"scenario\":\"x\"}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
