package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for the NORSOK S-001 Clause 10 MCP runner.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class NorsokS001Clause10ReviewRunnerTest {
  private static final Gson GSON = new Gson();

  /**
   * Verifies that the example returns a structured passing report.
   */
  @Test
  void exampleReturnsClause10Report() {
    String example = ExampleCatalog.getExample("process-safety-review", "norsok-s001-clause10");

    JsonObject output = JsonParser.parseString(NorsokS001Clause10ReviewRunner.run(example))
        .getAsJsonObject();

    assertEquals("success", output.get("status").getAsString());
    assertEquals("norsok_s001_clause10_review", output.get("reviewType").getAsString());
    assertEquals("PASS", output.get("overallVerdict").getAsString());
    assertTrue(output.has("provenance"));
  }

  /**
   * Verifies that missing review data returns a structured error.
   */
  @Test
  void missingDataReturnsStructuredError() {
    JsonObject output = JsonParser.parseString(NorsokS001Clause10ReviewRunner.run("{}"))
        .getAsJsonObject();

    assertEquals("error", output.get("status").getAsString());
    assertEquals("MISSING_PROCESS_SAFETY_DATA",
        output.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  /**
   * Verifies that transient NeqSim simulation evidence can be embedded with the Clause 10 review.
   */
  @Test
  void dynamicSimulationEvidenceIsEmbeddedWhenSupplied() {
    JsonObject input = new JsonObject();
    input.addProperty("projectName", "dynamic clause 10 evidence");
    input.add("processSafetyFunctions", JsonParser.parseString("[{"
        + "\"functionId\":\"PSD-DYN-001\",\"functionType\":\"PSD\","
        + "\"sourceReferences\":[\"C&E\",\"SRS\"],"
        + "\"hazidHazopLopaCompleted\":true,"
        + "\"srsDefinesRequiredFunctions\":true,"
        + "\"sisEsdFgsDesignImplemented\":true,"
        + "\"verificationTestingOperationConfirmed\":true,"
        + "\"processSafetyRoleDefined\":true,\"interfacesDefined\":true,"
        + "\"protectionLayersDocumented\":true,\"designBasisDocumented\":true,"
        + "\"processSafetyPrinciplesDocumented\":true,\"bypassManagementDocumented\":true,"
        + "\"shutdownActionDefined\":true,\"psdValveFailsSafe\":true,"
        + "\"psdValveIsolationAdequate\":true,\"requiredResponseTimeSeconds\":30.0,"
        + "\"actualResponseTimeSeconds\":18.0,\"logicSolverCertified\":true,"
        + "\"logicSolverIndependent\":true,\"causeAndEffectTested\":true,"
        + "\"requiredUtilitiesIdentified\":true,\"utilityDependent\":true,"
        + "\"failSafeOnUtilityLoss\":true,\"psdIndependentFromControl\":true,"
        + "\"manualShutdownAvailable\":true,\"survivabilityRequirementDocumented\":true,"
        + "\"requiredSurvivabilityTimeMin\":30.0,\"survivabilityTimeMin\":60.0"
        + "}]").getAsJsonArray());

    JsonObject dynamicInput = new JsonObject();
    dynamicInput.addProperty("processJson", minimalDynamicProcessJson());
    dynamicInput.addProperty("duration_seconds", 1.0);
    dynamicInput.addProperty("timeStep_seconds", 1.0);
    input.add("dynamicSimulationInput", dynamicInput);

    JsonObject output = JsonParser.parseString(NorsokS001Clause10ReviewRunner.run(GSON.toJson(input)))
        .getAsJsonObject();

    assertEquals("success", output.get("status").getAsString());
    JsonObject dynamic = output.getAsJsonObject("embeddedAnalyses")
        .getAsJsonObject("dynamicSimulation");
    assertEquals("success", dynamic.get("status").getAsString());
  }

  /**
   * Builds a minimal process JSON accepted by DynamicRunner.
   *
   * @return process JSON string
   */
  private static String minimalDynamicProcessJson() {
    return "{"
        + "\"fluid\": {\"model\": \"SRK\", \"temperature\": 298.15, \"pressure\": 50.0,"
        + "  \"mixingRule\": \"classic\","
        + "  \"components\": {\"methane\": 0.9, \"nC10\": 0.1}},"
        + "\"process\": [" + "  {\"type\": \"Stream\", \"name\": \"feed\","
        + "   \"properties\": {\"flowRate\": [1000.0, \"kg/hr\"]}},"
        + "  {\"type\": \"Separator\", \"name\": \"HP Sep\", \"inlet\": \"feed\"}" + "]}";
  }
}