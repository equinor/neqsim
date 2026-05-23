package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgenticEngineeringKernel}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AgenticEngineeringKernelTest {

  /**
   * Verifies workflow planning creates an intent graph and domain-specific steps.
   */
  @Test
  void testPlanWorkflowBuildsIntentGraphAndWorkflow() {
    String json =
        "{\"action\":\"plan\",\"task\":\"Design gas compression and pipeline hydrate screening\","
            + "\"objectives\":[{\"name\":\"minimize power\"}],"
            + "\"constraints\":[{\"name\":\"hydrate margin positive\"}]}";
    JsonObject result =
        JsonParser.parseString(AgenticEngineeringKernel.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertTrue(result.has("engineeringIntent"));
    assertTrue(result.has("intentGraph"));
    assertTrue(result.has("workflowPlan"));
    JsonArray steps = result.getAsJsonObject("workflowPlan").getAsJsonArray("steps");
    assertTrue(containsStep(steps, "run_pipeline"));
    assertTrue(containsStep(steps, "flow_assurance"));
    assertTrue(containsStep(steps, "evidence_trust"));
  }

  /**
   * Verifies evidence and validation metadata produce high trust.
   */
  @Test
  void testEvaluateTrustScoresEvidencePackage() {
    String json = "{\"action\":\"trust\"," + "\"provenance\":{\"model\":\"SRK\"},"
        + "\"validation\":{\"valid\":true}," + "\"qualityGate\":{\"verdict\":\"passed\"},"
        + "\"benchmarkTrust\":{\"maturityLevel\":\"VALIDATED\"},"
        + "\"evidence\":[{\"id\":\"NIST\",\"summary\":\"reference data\"}],"
        + "\"assumptions\":[\"SRK screening model\"]," + "\"standards\":[\"ISO 6976\"]}";
    JsonObject result =
        JsonParser.parseString(AgenticEngineeringKernel.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    JsonObject trust = result.getAsJsonObject("trust");
    assertTrue(trust.get("score").getAsDouble() >= 80.0);
    assertEquals("HIGH_TRUST", trust.get("verdict").getAsString());
  }

  /**
   * Verifies autonomous study ranking honors objectives and constraints.
   */
  @Test
  void testRunStudyRanksFeasibleCandidates() {
    String json = "{\"action\":\"study\",\"studyName\":\"compressor alternatives\","
        + "\"objectives\":[{\"metric\":\"power_kW\",\"goal\":\"minimize\",\"weight\":0.7},"
        + "{\"metric\":\"throughput_kg_hr\",\"goal\":\"maximize\",\"weight\":0.3}],"
        + "\"constraints\":[{\"metric\":\"dischargeTemperature_C\",\"operator\":\"<=\",\"value\":180.0}],"
        + "\"candidates\":["
        + "{\"name\":\"A\",\"metrics\":{\"power_kW\":1200,\"throughput_kg_hr\":5000,\"dischargeTemperature_C\":160}},"
        + "{\"name\":\"B\",\"metrics\":{\"power_kW\":1000,\"throughput_kg_hr\":4800,\"dischargeTemperature_C\":170}},"
        + "{\"name\":\"C\",\"metrics\":{\"power_kW\":900,\"throughput_kg_hr\":5200,\"dischargeTemperature_C\":210}}]}";
    JsonObject result =
        JsonParser.parseString(AgenticEngineeringKernel.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    JsonArray ranking = result.getAsJsonArray("ranking");
    assertEquals("B", ranking.get(0).getAsJsonObject().get("name").getAsString());
    assertTrue(ranking.get(0).getAsJsonObject().get("feasible").getAsBoolean());
    assertEquals("candidate_selected",
        result.getAsJsonObject("recommendation").get("status").getAsString());
  }

  /**
   * Checks whether a workflow step id exists.
   *
   * @param steps workflow step array
   * @param id step id
   * @return true when found
   */
  private static boolean containsStep(JsonArray steps, String id) {
    for (int i = 0; i < steps.size(); i++) {
      if (id.equals(steps.get(i).getAsJsonObject().get("id").getAsString())) {
        return true;
      }
    }
    return false;
  }
}
