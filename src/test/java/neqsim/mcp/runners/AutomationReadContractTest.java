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
 * Focused contract tests for the read-only automation introspection surface.
 *
 * <p>
 * These tests qualify software-contract behavior only. They do not validate the numerical accuracy of the solved
 * process or any returned variable value.
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
  void invalidReadRequestsFailClosedBeforeSimulationUse() {
    assertInputError(AutomationRunner.listUnits(""), "listSimulationUnits");
    assertInputError(AutomationRunner.listVariables(PROCESS_JSON, ""), "listUnitVariables");
    assertInputError(AutomationRunner.getVariable(PROCESS_JSON, "", "C"), "getSimulationVariable");
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
