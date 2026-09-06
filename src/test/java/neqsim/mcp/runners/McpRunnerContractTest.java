package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.process.processmodel.lifecycle.ProcessModelState;

/**
 * Golden contract tests for high-use MCP runner responses.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class McpRunnerContractTest {

  /**
   * Verifies that high-use runner success responses satisfy the standard envelope fixture.
   *
   * @throws Exception if the contract fixture cannot be read
   */
  @Test
  void testHighUseRunnerSuccessResponsesMatchStandardContract() throws Exception {
    JsonObject contract = readContractFixture();
    JsonArray tools = contract.getAsJsonArray("successTools");

    for (JsonElement toolElement : tools) {
      String toolName = toolElement.getAsString();
      String responseJson = runTool(toolName);
      JsonObject response = JsonParser.parseString(responseJson).getAsJsonObject();

      String status = response.get("status").getAsString();
      assertTrue(contains(contract.getAsJsonArray("successStatuses"), status),
          toolName + " returned status '" + status + "', which is outside the declared success vocabulary "
              + contract.getAsJsonArray("successStatuses") + ": " + responseJson);
      assertEquals(contract.get("apiVersion").getAsString(), response.get("apiVersion").getAsString(),
          toolName + " apiVersion mismatch");
      assertEquals(toolName, response.get("tool").getAsString(), toolName + " tool mismatch");
      assertRequiredFields(toolName, response, contract.getAsJsonArray("requiredFields"));
      assertRequiredFields(toolName, response.getAsJsonObject("validation"),
          contract.getAsJsonArray("requiredValidationFields"));
      assertRequiredFields(toolName, response.getAsJsonObject("qualityGate"),
          contract.getAsJsonArray("requiredQualityGateFields"));
    }
  }

  /**
   * Qualifies the bounded canonical ProcessSystem snapshot content.
   */
  @Test
  void testSimulationStateSnapshotPreservesCanonicalContent() {
    JsonObject response = JsonParser
        .parseString(AutomationRunner.saveState(ExampleCatalog.processSimpleSeparation(), "phase0-snapshot", "1.0"))
        .getAsJsonObject();
    JsonObject data = response.getAsJsonObject("data");
    JsonObject state = data.getAsJsonObject("state");

    assertEquals("success", response.get("status").getAsString());
    assertEquals("saveSimulationState", response.get("tool").getAsString());
    assertEquals("phase0-snapshot", data.get("stateName").getAsString());
    assertEquals("1.0", data.get("stateVersion").getAsString());
    assertEquals("1.1", state.get("schemaVersion").getAsString());
    assertEquals("phase0-snapshot", state.get("name").getAsString());
    assertEquals("1.0", state.get("version").getAsString());
    assertTrue(state.getAsJsonArray("equipmentStates").size() > 0);
    assertTrue(state.getAsJsonObject("streamStates").size() > 0);
    assertTrue(response.getAsJsonObject("validation").get("valid").getAsBoolean());
    assertEquals("passed", response.getAsJsonObject("qualityGate").get("verdict").getAsString());
  }

  /**
   * Qualifies deterministic identity and explicit metadata-version comparison for emitted snapshots.
   */
  @Test
  void testSimulationStateComparisonReportsMetadataVersionChange() {
    JsonObject saved = JsonParser
        .parseString(AutomationRunner.saveState(ExampleCatalog.processSimpleSeparation(), "phase0-snapshot", "1.0"))
        .getAsJsonObject();
    JsonObject state = saved.getAsJsonObject("data").getAsJsonObject("state");

    JsonObject identical =
        JsonParser.parseString(AutomationRunner.compareStates(state.toString(), state.toString())).getAsJsonObject();
    assertFalse(identical.getAsJsonObject("data").get("hasChanges").getAsBoolean());

    JsonObject revised = state.deepCopy();
    revised.addProperty("version", "1.1");
    JsonObject changed =
        JsonParser.parseString(AutomationRunner.compareStates(state.toString(), revised.toString())).getAsJsonObject();
    JsonObject changedData = changed.getAsJsonObject("data");

    assertEquals("success", changed.get("status").getAsString());
    assertEquals("compareSimulationStates", changed.get("tool").getAsString());
    assertTrue(changedData.get("hasChanges").getAsBoolean());
    assertEquals("1.0 -> 1.1",
        changedData.getAsJsonObject("diff").getAsJsonObject("modifiedParameters").get("version").getAsString());
    assertTrue(changed.getAsJsonObject("validation").get("valid").getAsBoolean());
    assertEquals("passed", changed.getAsJsonObject("qualityGate").get("verdict").getAsString());
  }

  /**
   * Requires both state-snapshot tools to fail closed on missing primary inputs.
   */
  @Test
  void testSimulationStateSnapshotInputsFailClosed() {
    JsonObject missingProcess = JsonParser.parseString(AutomationRunner.saveState(null, "snapshot", "1.0"))
        .getAsJsonObject();
    JsonObject missingFirst = JsonParser.parseString(AutomationRunner.compareStates(null, "{}")).getAsJsonObject();
    JsonObject missingSecond = JsonParser.parseString(AutomationRunner.compareStates("{}", "")).getAsJsonObject();

    assertEquals("error", missingProcess.get("status").getAsString());
    assertEquals("INPUT_ERROR", missingProcess.get("code").getAsString());
    assertEquals("error", missingFirst.get("status").getAsString());
    assertEquals("INPUT_ERROR", missingFirst.get("code").getAsString());
    assertEquals("error", missingSecond.get("status").getAsString());
    assertEquals("INPUT_ERROR", missingSecond.get("code").getAsString());
  }

  /**
   * Reads the standard MCP response contract fixture.
   *
   * @return parsed contract JSON
   * @throws Exception if the resource is missing or unreadable
   */
  private JsonObject readContractFixture() throws Exception {
    InputStream stream = getClass().getResourceAsStream("/neqsim/mcp/contracts/standard_response_contract.json");
    assertNotNull(stream, "standard_response_contract.json must exist");
    Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
    try {
      return JsonParser.parseReader(reader).getAsJsonObject();
    } finally {
      reader.close();
    }
  }

  /**
   * Runs a high-use MCP runner with a small deterministic input.
   *
   * @param toolName camel-case runner tool name from the contract fixture
   * @return runner response JSON
   */
  private static String runTool(String toolName) {
    if ("runFlash".equals(toolName)) {
      return FlashRunner.run(ExampleCatalog.flashTPSimpleGas());
    }
    if ("runProcess".equals(toolName)) {
      return ProcessRunner.run(ExampleCatalog.processSimpleSeparation());
    }
    if ("runBatch".equals(toolName)) {
      return BatchRunner.run(ExampleCatalog.batchTemperatureSweep());
    }
    if ("getPropertyTable".equals(toolName)) {
      return PropertyTableRunner.run(ExampleCatalog.propertyTableTemperatureSweep());
    }
    if ("getPhaseEnvelope".equals(toolName)) {
      return PhaseEnvelopeRunner.run(ExampleCatalog.phaseEnvelopeNaturalGas());
    }
    if ("runPVT".equals(toolName)) {
      return PVTRunner.run(ExampleCatalog.pvtCME());
    }
    if ("runDynamic".equals(toolName)) {
      JsonObject input = new JsonObject();
      input.addProperty("processJson", ExampleCatalog.processSimpleSeparation());
      input.addProperty("duration_seconds", 1.0);
      input.addProperty("timeStep_seconds", 1.0);
      return DynamicRunner.run(input.toString());
    }
    if ("getCapabilities".equals(toolName)) {
      return CapabilitiesRunner.getCapabilities();
    }
    if ("listSimulationUnits".equals(toolName)) {
      return AutomationRunner.listUnits(ExampleCatalog.processSimpleSeparation());
    }
    if ("listUnitVariables".equals(toolName)) {
      return AutomationRunner.listVariables(ExampleCatalog.processSimpleSeparation(), "HP Sep");
    }
    if ("getSimulationVariable".equals(toolName)) {
      return AutomationRunner.getVariable(ExampleCatalog.processSimpleSeparation(), "HP Sep.gasOutStream.temperature",
          "C");
    }
    if ("setSimulationVariable".equals(toolName)) {
      return AutomationRunner.setVariableAndRun(ExampleCatalog.processSimpleSeparation(), "feed.temperature", 35.0,
          "C");
    }
    if ("saveSimulationState".equals(toolName)) {
      return AutomationRunner.saveState(ExampleCatalog.processSimpleSeparation(), "base", "1.0");
    }
    if ("compareSimulationStates".equals(toolName)) {
      ProcessModelState baseState = new ProcessModelState();
      baseState.setName("base");
      baseState.setVersion("1.0");
      ProcessModelState updatedState = new ProcessModelState();
      updatedState.setName("base");
      updatedState.setVersion("1.1");
      return AutomationRunner.compareStates(baseState.toJson(), updatedState.toJson());
    }
    if ("diagnoseAutomation".equals(toolName)) {
      return AutomationRunner.diagnose(ExampleCatalog.processSimpleSeparation(), "HP separator.gasOut.temp", "get");
    }
    if ("getAutomationLearningReport".equals(toolName)) {
      return AutomationRunner.getLearningReport(ExampleCatalog.processSimpleSeparation());
    }
    throw new IllegalArgumentException("Unsupported contract tool: " + toolName);
  }

  /**
   * Verifies that a JSON object contains all required fields.
   *
   * @param label assertion label
   * @param response JSON object to inspect
   * @param requiredFields required field names
   */
  private static void assertRequiredFields(String label, JsonObject response, JsonArray requiredFields) {
    for (JsonElement fieldElement : requiredFields) {
      String fieldName = fieldElement.getAsString();
      assertTrue(response.has(fieldName), label + " missing required field " + fieldName);
    }
  }

  /**
   * Checks whether a JSON string array contains a value.
   *
   * @param values the array to search
   * @param value the value to look for
   * @return true when the value is present
   */
  private static boolean contains(JsonArray values, String value) {
    for (JsonElement element : values) {
      if (element.getAsString().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
