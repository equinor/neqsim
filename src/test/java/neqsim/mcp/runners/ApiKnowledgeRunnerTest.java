package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link ApiKnowledgeRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ApiKnowledgeRunnerTest {

  @Test
  void testEquipmentAliasResolvesRuntimeApi() {
    JsonObject result = JsonParser.parseString(ApiKnowledgeRunner.inspect("Mixer", "addStream")).getAsJsonObject();

    assertEquals("success", result.get("status").getAsString());
    assertEquals("neqsim.process.equipment.mixer.Mixer", result.get("resolvedClass").getAsString());
    JsonArray methods = result.getAsJsonArray("methods");
    assertTrue(methods.size() > 0);
    assertTrue(methods.toString().contains("addStream"));
    assertTrue(result.get("sourcePath").getAsString().endsWith("/Mixer.java"));
  }

  @Test
  void testCommonProcessClassResolves() {
    JsonObject result = JsonParser.parseString(ApiKnowledgeRunner.inspect("ProcessModel", "runUntilConverged"))
        .getAsJsonObject();

    assertEquals("success", result.get("status").getAsString());
    assertEquals("neqsim.process.processmodel.ProcessModel", result.get("resolvedClass").getAsString());
    assertTrue(result.getAsJsonArray("methods").toString().contains("runUntilConverged"));
  }

  @Test
  void testRejectsNonNeqSimClass() {
    JsonObject result = JsonParser.parseString(ApiKnowledgeRunner.inspect("java.lang.Runtime", null)).getAsJsonObject();

    assertEquals("error", result.get("status").getAsString());
  }
}