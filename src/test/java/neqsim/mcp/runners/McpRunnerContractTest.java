package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

      assertTrue(!"error".equals(response.get("status").getAsString()),
          toolName + " returned an error response: " + responseJson);
      assertEquals(contract.get("apiVersion").getAsString(),
          response.get("apiVersion").getAsString(), toolName + " apiVersion mismatch");
      assertEquals(toolName, response.get("tool").getAsString(), toolName + " tool mismatch");
      assertRequiredFields(toolName, response, contract.getAsJsonArray("requiredFields"));
      assertRequiredFields(toolName, response.getAsJsonObject("validation"),
          contract.getAsJsonArray("requiredValidationFields"));
      assertRequiredFields(toolName, response.getAsJsonObject("qualityGate"),
          contract.getAsJsonArray("requiredQualityGateFields"));
    }
  }

  /**
   * Reads the standard MCP response contract fixture.
   *
   * @return parsed contract JSON
   * @throws Exception if the resource is missing or unreadable
   */
  private JsonObject readContractFixture() throws Exception {
    InputStream stream =
        getClass().getResourceAsStream("/neqsim/mcp/contracts/standard_response_contract.json");
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
    throw new IllegalArgumentException("Unsupported contract tool: " + toolName);
  }

  /**
   * Verifies that a JSON object contains all required fields.
   *
   * @param label assertion label
   * @param response JSON object to inspect
   * @param requiredFields required field names
   */
  private static void assertRequiredFields(String label, JsonObject response,
      JsonArray requiredFields) {
    for (JsonElement fieldElement : requiredFields) {
      String fieldName = fieldElement.getAsString();
      assertTrue(response.has(fieldName), label + " missing required field " + fieldName);
    }
  }
}
