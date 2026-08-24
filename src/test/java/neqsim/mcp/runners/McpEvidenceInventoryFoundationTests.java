package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests Phase 0 foundation, trust, acceptance, and campaign-matrix evidence exposed by MCP discovery. */
class McpEvidenceInventoryFoundationTests {

  @Test
  void testMergedFoundationsAreExplicitAndBounded() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject foundations = inventory.getAsJsonObject("mergedFoundations");

    assertTrue(foundations.get("complete").getAsBoolean());
    assertEquals(3, foundations.get("foundationCount").getAsInt());
    assertEquals("neqsim-mcp-server/docs/FOUNDATION_TRACEABILITY.md",
        foundations.get("evidenceDocument").getAsString());

    JsonArray entries = foundations.getAsJsonArray("entries");
    assertEquals(3, entries.size());

    Set<Integer> pullRequests = new HashSet<Integer>();
    for (int i = 0; i < entries.size(); i++) {
      JsonObject entry = entries.get(i).getAsJsonObject();
      pullRequests.add(Integer.valueOf(entry.get("pullRequest").getAsInt()));
      assertEquals("MERGED_CURRENT_MASTER_FOUNDATION", entry.get("status").getAsString());
      assertFalse(entry.get("mergeCommit").getAsString().isEmpty());
      assertFalse(entry.get("capability").getAsString().isEmpty());
      assertTrue(entry.getAsJsonArray("sourcePaths").size() >= 4);
      assertTrue(entry.getAsJsonArray("testPaths").size() >= 1);
      assertFalse(entry.get("boundary").getAsString().isEmpty());
    }

    assertTrue(pullRequests.contains(Integer.valueOf(2874)));
    assertTrue(pullRequests.contains(Integer.valueOf(2875)));
    assertTrue(pullRequests.contains(Integer.valueOf(3152)));
    assertFalse(inventory.get("complete").getAsBoolean());
  }

  @Test
  void testEveryPublishedToolHasExplicitTrustCoverageStatus() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    JsonObject coverageRecords = limitations.getAsJsonObject("coverageRecords");

    assertEquals(71, limitations.get("publishedToolCount").getAsInt());
    assertEquals(71, limitations.get("coverageRecordCount").getAsInt());
    assertEquals(20, limitations.get("explicitCoverageRecordCount").getAsInt());
    assertEquals(1, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(50, limitations.get("confirmedGapToolCount").getAsInt());
    assertEquals(71, coverageRecords.size());
    assertTrue(limitations.get("coverageComplete").getAsBoolean());
    assertFalse(limitations.get("scientificValidationComplete").getAsBoolean());

    JsonObject flash = coverageRecords.getAsJsonObject("runFlash");
    assertEquals("EXPLICIT_TRUST", flash.get("coverageStatus").getAsString());
    assertTrue(flash.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(flash.get("validationCaseCount").getAsInt() > 0);

    JsonObject capabilities = coverageRecords.getAsJsonObject("getCapabilities");
    assertEquals("CONTRACT_TESTED", capabilities.get("coverageStatus").getAsString());
    assertFalse(capabilities.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(capabilities.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_DISCOVERY",
        capabilities.get("benchmarkApplicability").getAsString());
    assertEquals("TESTED", capabilities.get("maturityLevel").getAsString());
    assertEquals(4, capabilities.get("contractEvidenceCount").getAsInt());
    assertTrue(capabilities.get("evidenceBoundary").getAsString().contains("not scientific validation"));
    assertFalse(inventory.get("complete").getAsBoolean());
  }

  @Test
  void testPhase0EvidenceIsDiscoverableThroughCapabilities() {
    JsonObject capabilities = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject inventory = capabilities.getAsJsonObject("phase0EvidenceInventory");
    JsonObject fixtures = inventory.getAsJsonObject("acceptanceFixtures");

    assertEquals("1.6", inventory.get("inventoryVersion").getAsString());
    assertEquals(8, inventory.getAsJsonObject("guides").get("guideCount").getAsInt());
    assertEquals(4, fixtures.get("fixtureCount").getAsInt());
    assertTrue(fixtures.get("complete").getAsBoolean());
    assertEquals("BASELINE_HARNESS_AVAILABLE_RESULTS_RUN_SPECIFIC",
        fixtures.get("executionEvidenceStatus").getAsString());

    JsonObject baselineContract = inventory.getAsJsonObject("acceptanceBaselineContract");
    assertEquals(4, baselineContract.get("fixtureCount").getAsInt());
    assertEquals(2, baselineContract.get("repeatRunCount").getAsInt());
    assertFalse(baselineContract.get("performanceQualification").getAsBoolean());

    JsonObject matrix = inventory.getAsJsonObject("campaignMatrix");
    assertEquals(66, matrix.get("criterionCount").getAsInt());
    assertEquals(10, matrix.getAsJsonObject("disciplines").size());
    assertFalse(matrix.get("roadmapCompletionClaim").getAsBoolean());
    assertFalse(inventory.get("complete").getAsBoolean());
  }
}
