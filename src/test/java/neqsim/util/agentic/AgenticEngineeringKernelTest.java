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
    String json = "{\"action\":\"plan\",\"task\":\"Design gas compression and pipeline hydrate screening\","
        + "\"objectives\":[{\"name\":\"minimize power\"}],"
        + "\"constraints\":[{\"name\":\"hydrate margin positive\"}]}";
    JsonObject result = JsonParser.parseString(AgenticEngineeringKernel.run(json)).getAsJsonObject();
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
    String json = "{\"action\":\"trust\"," + "\"provenance\":{\"model\":\"SRK\"}," + "\"validation\":{\"valid\":true},"
        + "\"qualityGate\":{\"verdict\":\"passed\"}," + "\"benchmarkTrust\":{\"maturityLevel\":\"VALIDATED\"},"
        + "\"evidence\":[{\"id\":\"NIST\",\"summary\":\"reference data\"}],"
        + "\"assumptions\":[\"SRK screening model\"]," + "\"standards\":[\"ISO 6976\"]}";
    JsonObject result = JsonParser.parseString(AgenticEngineeringKernel.run(json)).getAsJsonObject();
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
    JsonObject result = JsonParser.parseString(AgenticEngineeringKernel.run(json)).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    JsonArray ranking = result.getAsJsonArray("ranking");
    assertEquals("B", ranking.get(0).getAsJsonObject().get("name").getAsString());
    assertTrue(ranking.get(0).getAsJsonObject().get("feasible").getAsBoolean());
    assertEquals("candidate_selected", result.getAsJsonObject("recommendation").get("status").getAsString());
  }

  /**
   * Verifies readiness assessment flags missing critical task artifacts and accepts complete evidence packages.
   */
  @Test
  void testAssessReadinessReportsCriticalGapsAndDesignReadiness() {
    String incompleteJson = "{\"action\":\"readiness\",\"scale\":\"standard\","
        + "\"artifacts\":[{\"path\":\"step1_scope_and_research/task_spec.md\"}],"
        + "\"result\":{\"key_results\":{\"pressure_drop_bar\":3.2}}}";
    JsonObject incomplete = JsonParser.parseString(AgenticEngineeringKernel.run(incompleteJson)).getAsJsonObject();
    assertEquals("success", incomplete.get("status").getAsString());
    JsonObject incompleteReadiness = incomplete.getAsJsonObject("readiness");
    assertEquals("NOT_READY", incompleteReadiness.get("level").getAsString());
    assertTrue(incomplete.getAsJsonArray("missingCritical").size() > 0);

    String completeJson = "{\"action\":\"readiness\",\"scale\":\"standard\"," + "\"workflowPlan\":{\"steps\":[]},"
        + "\"artifacts\":[" + "{\"path\":\"step1_scope_and_research/task_spec.md\"},"
        + "{\"path\":\"step1_scope_and_research/capability_assessment.md\"},"
        + "{\"path\":\"step1_scope_and_research/analysis.md\"},"
        + "{\"path\":\"step1_scope_and_research/neqsim_improvements.md\"}," + "{\"path\":\"results.json\"},"
        + "{\"path\":\"step2_analysis/02_benchmark_validation.ipynb\"}," + "{\"path\":\"consistency_report.json\"}],"
        + "\"result\":{\"key_results\":{\"pressure_drop_bar\":3.2},"
        + "\"figure_discussion\":[{\"figure\":\"profile.png\"}]," + "\"validation\":{\"acceptance_criteria_met\":true},"
        + "\"benchmark_validation\":{\"status\":\"PASS\"}," + "\"uncertainty\":{\"p50\":1.0},"
        + "\"risk_evaluation\":{\"overall_risk_level\":\"Medium\"}}}";
    JsonObject complete = JsonParser.parseString(AgenticEngineeringKernel.run(completeJson)).getAsJsonObject();
    JsonObject completeReadiness = complete.getAsJsonObject("readiness");
    assertEquals("READY_FOR_DESIGN_REVIEW", completeReadiness.get("level").getAsString());
    assertTrue(completeReadiness.get("designDecisionAllowed").getAsBoolean());
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
