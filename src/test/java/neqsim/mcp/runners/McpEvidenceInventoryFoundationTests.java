package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Tests Phase 0 foundation and trust-coverage evidence exposed by MCP capability discovery. */
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
    assertTrue(foundations.get("remainingPhase0Boundary").getAsString().contains("acceptance scales"));

    // Foundation reconciliation is complete, but the overall Phase 0 evidence inventory is not.
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
    assertEquals(51, limitations.get("confirmedGapToolCount").getAsInt());
    assertEquals(71, coverageRecords.size());
    assertTrue(limitations.get("coverageComplete").getAsBoolean());
    assertFalse(limitations.get("scientificValidationComplete").getAsBoolean());

    JsonObject flash = coverageRecords.getAsJsonObject("runFlash");
    assertEquals("EXPLICIT_TRUST", flash.get("coverageStatus").getAsString());
    assertTrue(flash.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(flash.get("validationCaseCount").getAsInt() > 0);
    assertTrue(flash.get("knownLimitationCount").getAsInt() > 0);

    JsonObject capabilities = coverageRecords.getAsJsonObject("getCapabilities");
    assertEquals("CONFIRMED_GAP", capabilities.get("coverageStatus").getAsString());
    assertFalse(capabilities.get("toolSpecificTrustAvailable").getAsBoolean());
    assertEquals("TESTED", capabilities.get("maturityLevel").getAsString());
    assertTrue(capabilities.get("implementationClass").getAsString().contains("CapabilitiesRunner"));
    assertTrue(capabilities.get("gapReason").getAsString().contains("not benchmark"));

    // Coverage classification is complete; tool-specific trust evidence is intentionally not.
    assertFalse(limitations.get("complete").getAsBoolean());
    assertFalse(inventory.get("complete").getAsBoolean());
  }
}
