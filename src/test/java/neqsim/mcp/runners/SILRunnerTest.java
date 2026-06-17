package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link SILRunner}.
 */
class SILRunnerTest {

  @Test
  void testSif1oo1WithComponentPfd() {
    String json = "{"
        + "\"name\":\"SIF-100\","
        + "\"description\":\"HP shutdown\","
        + "\"claimedSIL\":2,"
        + "\"architecture\":\"1oo1\","
        + "\"proofTestInterval_hours\":8760,"
        + "\"components\":["
        + "  {\"name\":\"PT\",\"type\":\"sensor\",\"pfd\":0.001},"
        + "  {\"name\":\"Logic\",\"type\":\"logic\",\"pfd\":0.0005},"
        + "  {\"name\":\"Valve\",\"type\":\"actuator\",\"pfd\":0.005}"
        + "]"
        + "}";
    String result = SILRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    JsonObject sum = obj.getAsJsonObject("summary");
    assertTrue(sum != null || obj.has("verification") || obj.has("sif"));
    // Find achievedSIL whatever the wrapping is
    JsonObject root = obj;
    boolean found = false;
    for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
      if (e.getValue().isJsonObject()
          && e.getValue().getAsJsonObject().has("achievedSIL")) {
        assertTrue(e.getValue().getAsJsonObject().get("achievedSIL").getAsInt() >= 1);
        found = true;
        break;
      }
    }
    assertTrue(found, "achievedSIL field expected somewhere in result");
  }

  @Test
  void testSifWithLambdaDU() {
    String json = "{"
        + "\"name\":\"SIF-200\","
        + "\"claimedSIL\":2,"
        + "\"architecture\":\"1oo1\","
        + "\"proofTestInterval_hours\":8760,"
        + "\"components\":["
        + "  {\"name\":\"PT\",\"type\":\"sensor\",\"lambdaDU_per_hr\":1.0e-7},"
        + "  {\"name\":\"Valve\",\"type\":\"actuator\",\"lambdaDU_per_hr\":5.0e-7}"
        + "]"
        + "}";
    String result = SILRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
  }

  @Test
  void testSifWithDirectPfdAvg() {
    String json = "{"
        + "\"name\":\"SIF-300\","
        + "\"claimedSIL\":1,"
        + "\"pfdAvg\":0.05"
        + "}";
    String result = SILRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
  }

  @Test
  void testMissingPfdSource() {
    String result = SILRunner.run("{\"name\":\"X\",\"claimedSIL\":1}");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
