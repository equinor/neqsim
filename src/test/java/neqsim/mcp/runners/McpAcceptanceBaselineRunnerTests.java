package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Tests the bounded Phase 0 acceptance-baseline harness. */
class McpAcceptanceBaselineRunnerTests {

  @Test
  void testContractIsDiscoverableWithoutExecutingFixtures() {
    JsonObject contract = McpAcceptanceBaselineRunner.describe();
    assertEquals(4, contract.get("fixtureCount").getAsInt());
    assertEquals(2, contract.get("repeatRunCount").getAsInt());
    assertEquals("ON_DEMAND_TEST_HARNESS", contract.get("executionMode").getAsString());
    assertFalse(contract.get("performanceQualification").getAsBoolean());
    assertFalse(contract.get("scientificValidationComplete").getAsBoolean());

    JsonObject inventory = McpEvidenceInventory.build();
    assertEquals("1.25", inventory.get("inventoryVersion").getAsString());
    assertTrue(inventory.has("acceptanceBaselineContract"));
    assertFalse(inventory.get("complete").getAsBoolean());
  }

  @Test
  void testFourScaleExecutionRecordsBoundedEvidenceAndExplicitGaps() {
    JsonObject baseline = McpAcceptanceBaselineRunner.run();

    JsonArray measurements = baseline.getAsJsonArray("measurements");
    JsonObject summary = baseline.getAsJsonObject("summary");
    assertEquals(4, measurements.size());
    assertEquals(8, summary.get("toolExecutionCount").getAsInt());
    assertEquals(4, summary.get("successCount").getAsInt(), baseline.toString());
    assertEquals(4, summary.get("convergedCount").getAsInt(), baseline.toString());
    assertEquals(4, summary.get("deterministicCount").getAsInt(), baseline.toString());
    assertEquals(3, summary.get("balanceEvidencePresentCount").getAsInt(), baseline.toString());
    assertEquals(0, summary.get("explicitBalanceGapCount").getAsInt(), baseline.toString());
    assertEquals("EXECUTION_COMPLETE", summary.get("status").getAsString(), baseline.toString());

    boolean smallSystemBalanceEvidenceFound = false;
    boolean multiAreaMassClosureEvidenceFound = false;
    boolean largeSystemBalanceEvidenceFound = false;
    for (com.google.gson.JsonElement element : measurements) {
      JsonObject measurement = element.getAsJsonObject();
      assertTrue(measurement.get("successful").getAsBoolean(), measurement.toString());
      assertTrue(measurement.getAsJsonObject("runtime").get("firstRunMillis").getAsDouble() >= 0.0);
      assertTrue(measurement.getAsJsonObject("payloadAndGuard").get("rawResponseBytes").getAsInt() > 0);
      assertTrue(measurement.getAsJsonObject("payloadAndGuard").get("guardedWithinLimit").getAsBoolean(),
          measurement.toString());
      assertTrue(measurement.getAsJsonObject("convergence").get("declared").getAsBoolean(), measurement.toString());
      assertTrue(measurement.getAsJsonObject("determinism").get("stableOutcomeMatch").getAsBoolean(),
          measurement.toString());
      assertFalse(measurement.getAsJsonObject("balanceEvidence").get("status").getAsString().trim().isEmpty());
      String fixtureId = measurement.get("fixtureId").getAsString();
      if ("small-recycle-train".equals(fixtureId) || "large-recycle-facility".equals(fixtureId)) {
        JsonObject balanceEvidence = measurement.getAsJsonObject("balanceEvidence");
        assertEquals("RESPONSE_EVIDENCE_PRESENT", balanceEvidence.get("status").getAsString(), measurement.toString());
        assertTrue(balanceEvidence.getAsJsonArray("responsePaths").toString().contains("massBalanceEvidence"),
            balanceEvidence.toString());
        if ("small-recycle-train".equals(fixtureId)) {
          smallSystemBalanceEvidenceFound = true;
        } else {
          largeSystemBalanceEvidenceFound = true;
        }
      }
      if ("multi-area-facility".equals(fixtureId)) {
        JsonObject balanceEvidence = measurement.getAsJsonObject("balanceEvidence");
        assertEquals("RESPONSE_EVIDENCE_PRESENT", balanceEvidence.get("status").getAsString(), measurement.toString());
        assertTrue(balanceEvidence.getAsJsonArray("responsePaths").toString().contains("massClosure"),
            balanceEvidence.toString());
        multiAreaMassClosureEvidenceFound = true;
      }
      assertEquals("NOT_ESTABLISHED_BY_PHASE0_EXECUTION_HARNESS",
          measurement.get("scientificValidationStatus").getAsString());
    }
    assertTrue(smallSystemBalanceEvidenceFound, baseline.toString());
    assertTrue(multiAreaMassClosureEvidenceFound, baseline.toString());
    assertTrue(largeSystemBalanceEvidenceFound, baseline.toString());
  }
}
