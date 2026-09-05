package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;
import org.junit.jupiter.api.Test;

/**
 * Focused software-contract tests for canonical simulation-variable mutation.
 */
class AutomationVariableWriteContractTest {

  @Test
  void testCanonicalTemperatureWriteRerunsAndReturnsReport() {
    JsonObject root = parse(AutomationRunner.setVariableAndRun(ExampleCatalog.processSimpleSeparation(),
        "feed.temperature", 35.0, "C"));
    JsonObject data = root.getAsJsonObject("data");

    assertEquals("success", root.get("status").getAsString());
    assertEquals("setSimulationVariable", root.get("tool").getAsString());
    assertEquals("success", data.get("status").getAsString());
    assertEquals("feed.temperature", data.get("address").getAsString());
    assertEquals(35.0, data.get("value").getAsDouble(), 1.0e-12);
    assertEquals("C", data.get("unit").getAsString());
    assertTrue(data.has("simulationReport"));
    assertTrue(data.get("simulationReport").isJsonObject());
    assertTrue(root.getAsJsonObject("validation").get("valid").getAsBoolean());
    assertEquals("passed", root.getAsJsonObject("qualityGate").get("verdict").getAsString());
  }

  @Test
  void testPhysicalBoundViolationFailsWithoutRerunClaim() {
    JsonObject root = parse(AutomationRunner.setVariableAndRun(ExampleCatalog.processSimpleSeparation(),
        "feed.temperature", -300.0, "C"));
    JsonObject data = root.getAsJsonObject("data");

    assertEquals("error", root.get("status").getAsString());
    assertFalse(data.has("simulationReport"));
    assertEquals("failed", root.getAsJsonObject("qualityGate").get("verdict").getAsString());
  }

  @Test
  void testOutputAddressIsNotReportedAsSuccessfulWrite() {
    JsonObject root = parse(AutomationRunner.setVariableAndRun(ExampleCatalog.processSimpleSeparation(),
        "HP Sep.gasOutStream.temperature", 35.0, "C"));
    JsonObject data = root.getAsJsonObject("data");

    assertFalse("success".equals(root.get("status").getAsString()));
    assertFalse(data.has("simulationReport"));
    assertFalse("passed".equals(root.getAsJsonObject("qualityGate").get("verdict").getAsString()));
  }

  @Test
  void testMissingInputsFailClosed() {
    JsonObject missingProcess =
        parse(AutomationRunner.setVariableAndRun(null, "feed.temperature", 35.0, "C"));
    JsonObject missingAddress =
        parse(AutomationRunner.setVariableAndRun(ExampleCatalog.processSimpleSeparation(), null, 35.0, "C"));

    assertEquals("error", missingProcess.get("status").getAsString());
    assertEquals("INPUT_ERROR", missingProcess.get("code").getAsString());
    assertEquals("error", missingAddress.get("status").getAsString());
    assertEquals("INPUT_ERROR", missingAddress.get("code").getAsString());
  }

  private static JsonObject parse(String json) {
    return JsonParser.parseString(json).getAsJsonObject();
  }
}
