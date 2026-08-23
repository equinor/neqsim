package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Tests the Phase 0 merged-foundation reconciliation exposed by MCP capability discovery. */
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
}
