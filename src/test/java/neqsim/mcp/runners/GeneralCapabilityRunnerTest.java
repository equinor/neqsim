package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link GeneralCapabilityRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class GeneralCapabilityRunnerTest {

  @Test
  void testSearchFindsStaticAndStatefulSulfurCapabilities() {
    JsonObject result = JsonParser.parseString(GeneralCapabilityRunner.search("sulfur vapour pressure", 25))
        .getAsJsonObject();

    assertEquals("success", result.get("status").getAsString());
    JsonArray matches = result.getAsJsonArray("matches");
    assertTrue(matches.toString().contains("SulfurThermodynamics"));
    assertTrue(matches.toString().contains("calculateVapourPressureBar"));
    assertTrue(matches.toString().contains("static-json"));

    JsonObject solubility = JsonParser.parseString(GeneralCapabilityRunner.search("sulfur solubility", 50))
        .getAsJsonObject();
    assertTrue(solubility.getAsJsonArray("matches").toString().contains("SulfurDepositionAnalyser"),
        solubility.toString());
    assertTrue(solubility.getAsJsonArray("matches").toString().contains("process-json"));
  }

  @Test
  void testInvokeRunsDiscoveredSulfurCalculation() {
    String request = "{\"action\":\"invoke\"," + "\"className\":\"neqsim.thermo.util.sulfur.SulfurThermodynamics\","
        + "\"methodName\":\"calculateVapourPressureBar\"," + "\"parameterTypes\":[\"double\"],\"arguments\":[717.76]}";

    JsonObject result = JsonParser.parseString(GeneralCapabilityRunner.run(request)).getAsJsonObject();

    assertEquals("success", result.get("status").getAsString());
    assertEquals("static-json", result.get("executionMode").getAsString());
    assertEquals(1.01325, result.get("result").getAsDouble(), 1.0e-10);
  }

  @Test
  void testInvokeRejectsInstanceMethodAndExternalClass() {
    String instanceRequest = "{\"action\":\"invoke\","
        + "\"className\":\"neqsim.process.equipment.reactor.SulfurDepositionAnalyser\","
        + "\"methodName\":\"getSulfurSolubilityMgSm3\",\"arguments\":[]}";
    JsonObject instanceResult = JsonParser.parseString(GeneralCapabilityRunner.run(instanceRequest)).getAsJsonObject();
    assertEquals("METHOD_NOT_EXECUTABLE", instanceResult.get("code").getAsString());
    assertTrue(instanceResult.get("remediation").getAsString().contains("runProcess"));

    String externalRequest = "{\"action\":\"invoke\",\"className\":\"java.lang.Runtime\","
        + "\"methodName\":\"getRuntime\",\"arguments\":[]}";
    JsonObject externalResult = JsonParser.parseString(GeneralCapabilityRunner.run(externalRequest)).getAsJsonObject();
    assertEquals("CLASS_NOT_ALLOWED", externalResult.get("code").getAsString());
  }

  @Test
  void testInvokeRejectsMcpRunnerAndRawGenericContainerSignatures() {
    String runnerRequest = "{\"action\":\"invoke\"," + "\"className\":\"neqsim.mcp.runners.ProcessRunner\","
        + "\"methodName\":\"validateAndRun\",\"arguments\":[\"{}\"]}";
    JsonObject runnerResult = JsonParser.parseString(GeneralCapabilityRunner.run(runnerRequest)).getAsJsonObject();
    assertEquals("METHOD_NOT_EXECUTABLE", runnerResult.get("code").getAsString());

    String genericRequest = "{\"action\":\"invoke\","
        + "\"className\":\"neqsim.process.fielddevelopment.economics.ProductionProfileGenerator\","
        + "\"methodName\":\"getProfileSummary\",\"arguments\":[{}]}";
    JsonObject genericResult = JsonParser.parseString(GeneralCapabilityRunner.run(genericRequest)).getAsJsonObject();
    assertEquals("METHOD_NOT_EXECUTABLE", genericResult.get("code").getAsString());
  }

  @Test
  void testRunRejectsOversizedRequestBeforeReflection() {
    StringBuilder query = new StringBuilder(70000);
    for (int i = 0; i < 70000; i++) {
      query.append('x');
    }
    String request = "{\"action\":\"search\",\"query\":\"" + query + "\"}";

    JsonObject result = JsonParser.parseString(GeneralCapabilityRunner.run(request)).getAsJsonObject();

    assertEquals("INPUT_TOO_LARGE", result.get("code").getAsString());
  }
}