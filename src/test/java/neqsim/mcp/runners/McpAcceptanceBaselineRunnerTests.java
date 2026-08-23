package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Tests the bounded Phase 0 acceptance-baseline harness. */
class McpAcceptanceBaselineRunnerTests {

  private static final Logger logger = LogManager.getLogger(McpAcceptanceBaselineRunnerTests.class);

  @Test
  void testContractIsDiscoverableWithoutExecutingFixtures() {
    JsonObject contract = McpAcceptanceBaselineRunner.describe();
    assertEquals(4, contract.get("fixtureCount").getAsInt());
    assertEquals(2, contract.get("repeatRunCount").getAsInt());
    assertEquals("ON_DEMAND_TEST_HARNESS", contract.get("executionMode").getAsString());
    assertFalse(contract.get("performanceQualification").getAsBoolean());
    assertFalse(contract.get("scientificValidationComplete").getAsBoolean());

    JsonObject inventory = McpEvidenceInventory.build();
    assertEquals("1.4", inventory.get("inventoryVersion").getAsString());
    assertTrue(inventory.has("acceptanceBaselineContract"));
    assertFalse(inventory.get("complete").getAsBoolean());
  }

  @Test
  void testFourScaleExecutionRecordsBoundedEvidenceAndExplicitGaps() {
    JsonObject baseline = McpAcceptanceBaselineRunner.run();
    logger.info("MCP Phase 0 acceptance baseline: {}", baseline);

    JsonArray measurements = baseline.getAsJsonArray("measurements");
    JsonObject summary = baseline.getAsJsonObject("summary");
    assertEquals(4, measurements.size());
    assertEquals(8, summary.get("toolExecutionCount").getAsInt());
    assertEquals(4, summary.get("successCount").getAsInt(), baseline.toString());
    assertEquals(4, summary.get("convergedCount").getAsInt(), baseline.toString());
    assertEquals(4, summary.get("deterministicCount").getAsInt(), baseline.toString());
    assertTrue(summary.get("status").getAsString().startsWith("EXECUTION_COMPLETE"), baseline.toString());

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
      assertEquals("NOT_ESTABLISHED_BY_PHASE0_EXECUTION_HARNESS",
          measurement.get("scientificValidationStatus").getAsString());
    }
  }
}
