package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Focused contract tests for the read-only automation introspection and diagnostic surface.
 *
 * <p>
 * These tests qualify software-contract behavior only. They do not validate the numerical accuracy of the solved
 * process, any returned variable value, or the engineering correctness of a diagnostic recommendation.
 * </p>
 */
class AutomationReadContractTest {

  private static final String PROCESS_JSON = ExampleCatalog.processSimpleSeparation();

  @Test
  void listSimulationUnitsReturnsCanonicalSolvedProcessInventory() {
    JsonObject response = parseSuccess(AutomationRunner.listUnits(PROCESS_JSON), "listSimulationUnits");
    JsonObject data = response.getAsJsonObject("data");
    assertTrue(data.get("count").getAsInt() > 0);
    JsonArray units = data.getAsJsonArray("units");
    assertEquals(data.get("count").getAsInt(), units.size());
    for (int i = 0; i < units.size(); i++) {
      JsonObject unit = units.get(i).getAsJsonObject();
      assertFalse(unit.get("name").getAsString().trim().isEmpty());
      assertFalse(unit.get("type").getAsString().trim().isEmpty());
    }
  }

  @Test
  void listUnitVariablesPreservesAddressabilityMetadata() {
    JsonObject response = parseSuccess(AutomationRunner.listVariables(PROCESS_JSON, "HP Sep"), "listUnitVariables");
    JsonObject data = response.getAsJsonObject("data");
    assertEquals("HP Sep", data.get("unitName").getAsString());
    assertTrue(data.get("count").getAsInt() > 0);
    JsonArray variables = data.getAsJsonArray("variables");
    assertEquals(data.get("count").getAsInt(), variables.size());
    for (int i = 0; i < variables.size(); i++) {
      JsonObject variable = variables.get(i).getAsJsonObject();
      assertFalse(variable.get("address").getAsString().trim().isEmpty());
      assertFalse(variable.get("type").getAsString().trim().isEmpty());
      assertTrue(variable.has("writable"));
      assertTrue(variable.has("invalidatesProcess"));
      assertTrue(variable.has("applicability"));
    }
  }

  @Test
  void getSimulationVariableReturnsStandardEnvelopeWithoutClaimingNumericalQualification() {
    JsonObject response = parseSuccess(
        AutomationRunner.getVariable(PROCESS_JSON, "HP Sep.gasOutStream.temperature", "C"), "getSimulationVariable");
    JsonObject data = response.getAsJsonObject("data");
    assertTrue(data.size() > 0);
    assertTrue(response.has("provenance"));
    assertTrue(response.has("validation"));
    assertTrue(response.has("qualityGate"));
  }

  @Test
  void diagnoseAutomationReturnsStructuredAdvisoryEvidence() {
    String failedAddress = "Missing Unit.temperature";
    JsonObject response = JsonParser.parseString(AutomationRunner.diagnose(PROCESS_JSON, failedAddress, "get"))
        .getAsJsonObject();
    assertEquals("diagnostic", response.get("status").getAsString(), response.toString());
    assertEquals("diagnoseAutomation", response.get("tool").getAsString(), response.toString());
    assertTrue(response.has("provenance"), response.toString());
    assertTrue(response.has("validation"), response.toString());
    assertTrue(response.has("qualityGate"), response.toString());

    JsonObject data = response.getAsJsonObject("data");
    assertEquals(failedAddress, data.get("failedAddress").getAsString(), response.toString());
    assertEquals("get", data.get("operation").getAsString(), response.toString());
    JsonObject diagnosis = data.getAsJsonObject("diagnosis");
    assertEquals("UNIT_NOT_FOUND", diagnosis.get("category").getAsString(), response.toString());
    assertTrue(diagnosis.has("suggestions"), response.toString());
    assertFalse(diagnosis.get("remediation").getAsString().trim().isEmpty(), response.toString());
    assertTrue(data.has("learningReport"), response.toString());
  }

  @Test
  void getAutomationLearningReportReturnsDeterministicFreshProcessBaseline() {
    JsonObject response = parseSuccess(AutomationRunner.getLearningReport(PROCESS_JSON), "getAutomationLearningReport");
    JsonObject data = response.getAsJsonObject("data");
    assertEquals(0, data.get("totalOperations").getAsInt(), response.toString());
    assertEquals(1.0, data.get("successRate").getAsDouble(), 1.0e-12, response.toString());
    assertTrue(data.get("errorCategories").isJsonObject(), response.toString());
    assertTrue(data.get("learnedCorrections").isJsonObject(), response.toString());
    assertTrue(data.get("recentFailures").isJsonArray(), response.toString());
    assertTrue(data.get("recommendations").isJsonArray(), response.toString());
  }

  @Test
  void invalidReadRequestsFailClosedBeforeSimulationUse() {
    assertInputError(AutomationRunner.listUnits(""), "listSimulationUnits");
    assertInputError(AutomationRunner.listVariables(PROCESS_JSON, ""), "listUnitVariables");
    assertInputError(AutomationRunner.getVariable(PROCESS_JSON, "", "C"), "getSimulationVariable");
    assertInputError(AutomationRunner.diagnose(PROCESS_JSON, "", "get"), "diagnoseAutomation");
    assertInputError(AutomationRunner.getLearningReport(""), "getAutomationLearningReport");
  }

  private static JsonObject parseSuccess(String json, String toolName) {
    JsonObject response = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("success", response.get("status").getAsString(), json);
    assertEquals(toolName, response.get("tool").getAsString(), json);
    assertTrue(response.has("data"), json);
    return response;
  }

  private static void assertInputError(String json, String toolName) {
    JsonObject response = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("error", response.get("status").getAsString(), json);
    assertEquals(toolName, response.get("tool").getAsString(), json);
    assertTrue(response.has("data"), json);
    JsonObject data = response.getAsJsonObject("data");
    assertEquals("INPUT_ERROR", data.get("code").getAsString(), json);
    assertFalse(data.get("message").getAsString().trim().isEmpty(), json);
  }
}
