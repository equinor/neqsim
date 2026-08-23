package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Tests the complete #3153 criterion and discipline maturity matrix. */
class McpCampaignMatrixTests {

  @Test
  void testAllCampaignCriteriaAreEnumeratedExactlyOnce() {
    JsonObject matrix = McpCampaignMatrix.build();
    JsonArray criteria = matrix.getAsJsonArray("criteria");

    assertEquals(66, matrix.get("criterionCount").getAsInt());
    assertEquals(66, criteria.size());
    assertFalse(matrix.get("roadmapCompletionClaim").getAsBoolean());

    Set<String> ids = new HashSet<String>();
    int merged = 0;
    int partial = 0;
    int gaps = 0;
    for (JsonElement element : criteria) {
      JsonObject row = element.getAsJsonObject();
      assertTrue(ids.add(row.get("id").getAsString()), "Duplicate criterion id " + row.get("id").getAsString());
      assertFalse(row.get("criterion").getAsString().isEmpty());
      assertFalse(row.get("evidence").getAsString().isEmpty());
      String status = row.get("evidenceStatus").getAsString();
      assertTrue(
          status.equals("MERGED_EVIDENCE") || status.equals("PARTIAL_EVIDENCE") || status.equals("CONFIRMED_GAP"));
      if (status.equals("MERGED_EVIDENCE")) {
        merged++;
      } else if (status.equals("PARTIAL_EVIDENCE")) {
        partial++;
      } else {
        gaps++;
      }
    }

    assertTrue(merged > 0);
    assertTrue(partial > 0);
    assertTrue(gaps > 0);
    assertTrue(ids.contains("P0-C1"));
    assertTrue(ids.contains("P10-C7"));
  }

  @Test
  void testPhase0MatrixDoesNotSelfCertifyCurrentTreeWork() {
    JsonArray criteria = McpCampaignMatrix.build().getAsJsonArray("criteria");
    JsonObject traceability = criterion(criteria, "P0-C4");
    JsonObject baselines = criterion(criteria, "P0-C5");
    JsonObject maturity = criterion(criteria, "P0-C6");

    assertEquals("PARTIAL_EVIDENCE", traceability.get("evidenceStatus").getAsString());
    assertEquals("PARTIAL_EVIDENCE", baselines.get("evidenceStatus").getAsString());
    assertEquals("PARTIAL_EVIDENCE", maturity.get("evidenceStatus").getAsString());
    assertTrue(baselines.get("evidence").getAsString().contains("numeric closure gaps"));
  }

  @Test
  void testDisciplineMaturityIsBoundedByPublishedTrustEvidence() {
    JsonObject disciplines = McpCampaignMatrix.build().getAsJsonObject("disciplines");
    assertEquals(10, disciplines.size());

    for (java.util.Map.Entry<String, JsonElement> entry : disciplines.entrySet()) {
      JsonObject discipline = entry.getValue().getAsJsonObject();
      assertFalse(discipline.get("qualifiedForAccountableEngineeringApproval").getAsBoolean());
      assertTrue(discipline.get("publishedRepresentativeToolCount").getAsInt() > 0);
      assertTrue(discipline.get("toolSpecificTrustCount").getAsInt() <= discipline
          .get("publishedRepresentativeToolCount").getAsInt());
      String maturity = discipline.get("maturityStatus").getAsString();
      assertTrue(maturity.equals("TOOL_SPECIFIC_TRUST") || maturity.equals("PARTIAL_TOOL_SPECIFIC_TRUST")
          || maturity.equals("CONFIRMED_TRUST_GAP"));
    }
  }

  private static JsonObject criterion(JsonArray criteria, String id) {
    for (JsonElement element : criteria) {
      JsonObject row = element.getAsJsonObject();
      if (id.equals(row.get("id").getAsString())) {
        return row;
      }
    }
    throw new AssertionError("Missing criterion " + id);
  }
}
