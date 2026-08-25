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
    assertEquals(8, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(43, limitations.get("confirmedGapToolCount").getAsInt());
    assertEquals(8, limitations.getAsJsonArray("contractTestedTools").size());
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getCapabilities"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getSchema"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getExample"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getBenchmarkTrust"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("checkToolAccess"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("manageIndustrialProfile"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("searchComponents"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("queryDataCatalog"));
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
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_DISCOVERY", capabilities.get("benchmarkApplicability").getAsString());
    assertEquals("TESTED", capabilities.get("maturityLevel").getAsString());
    assertEquals(4, capabilities.get("contractEvidenceCount").getAsInt());
    assertEquals(4, capabilities.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(capabilities.get("evidenceBoundary").getAsString().contains("not scientific validation"));

    JsonObject schema = coverageRecords.getAsJsonObject("getSchema");
    assertEquals("CONTRACT_TESTED", schema.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_SCHEMA_CATALOG", schema.get("benchmarkApplicability").getAsString());
    assertEquals(3, schema.get("contractEvidenceCount").getAsInt());
    assertEquals(3, schema.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(schema.get("evidenceBoundary").getAsString().contains("142 canonical"));

    JsonObject example = coverageRecords.getAsJsonObject("getExample");
    assertEquals("CONTRACT_TESTED", example.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_EXAMPLE_CATALOG", example.get("benchmarkApplicability").getAsString());
    assertEquals(3, example.get("contractEvidenceCount").getAsInt());
    assertEquals(3, example.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(example.get("evidenceBoundary").getAsString().contains("114 catalog examples"));

    JsonObject benchmarkTrust = coverageRecords.getAsJsonObject("getBenchmarkTrust");
    assertEquals("CONTRACT_TESTED", benchmarkTrust.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_TRUST_CATALOG",
        benchmarkTrust.get("benchmarkApplicability").getAsString());
    assertEquals(3, benchmarkTrust.get("contractEvidenceCount").getAsInt());
    assertEquals(3, benchmarkTrust.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(benchmarkTrust.get("evidenceBoundary").getAsString().contains("does not validate"));

    JsonObject access = coverageRecords.getAsJsonObject("checkToolAccess");
    assertEquals("CONTRACT_TESTED", access.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_ACCESS_POLICY", access.get("benchmarkApplicability").getAsString());
    assertEquals(4, access.get("contractEvidenceCount").getAsInt());
    assertEquals(4, access.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(access.get("evidenceBoundary").getAsString().contains("does not grant external authorization"));

    JsonObject profile = coverageRecords.getAsJsonObject("manageIndustrialProfile");
    assertEquals("CONTRACT_TESTED", profile.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_GOVERNANCE_POLICY", profile.get("benchmarkApplicability").getAsString());
    assertEquals(5, profile.get("contractEvidenceCount").getAsInt());
    assertEquals(5, profile.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(profile.get("evidenceBoundary").getAsString().contains("external identity"));

    JsonObject componentSearch = coverageRecords.getAsJsonObject("searchComponents");
    assertEquals("CONTRACT_TESTED", componentSearch.get("coverageStatus").getAsString());
    assertFalse(componentSearch.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(componentSearch.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_COMPONENT_CATALOG_LOOKUP",
        componentSearch.get("benchmarkApplicability").getAsString());
    assertEquals(3, componentSearch.get("contractEvidenceCount").getAsInt());
    assertEquals(3, componentSearch.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(componentSearch.get("evidenceBoundary").getAsString().contains("real-protocol retrieval"));
    assertTrue(componentSearch.get("evidenceBoundary").getAsString().contains("does not validate thermodynamic"));

    JsonObject dataCatalog = coverageRecords.getAsJsonObject("queryDataCatalog");
    assertEquals("CONTRACT_TESTED", dataCatalog.get("coverageStatus").getAsString());
    assertFalse(dataCatalog.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(dataCatalog.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_DATA_CATALOG_DISCOVERY",
        dataCatalog.get("benchmarkApplicability").getAsString());
    assertEquals(3, dataCatalog.get("contractEvidenceCount").getAsInt());
    assertEquals(3, dataCatalog.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(dataCatalog.get("evidenceBoundary").getAsString().contains("Read-only catalog"));
    assertTrue(dataCatalog.get("evidenceBoundary").getAsString().contains("standards applicability"));
    assertFalse(inventory.get("complete").getAsBoolean());
  }

  @Test
  void testPromotedReadOnlyCatalogContractsRetirePendingCandidates() {
    JsonObject limitations = McpEvidenceInventory.build().getAsJsonObject("knownLimitations");
    JsonObject candidates = limitations.getAsJsonObject("contractPromotionCandidates");

    assertEquals(0, limitations.get("contractPromotionCandidateCount").getAsInt());
    assertEquals(0, candidates.size());
    assertTrue(limitations.get("promotionBoundary").getAsString().contains("No read-only catalog promotion candidate"));
    assertEquals(8, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(43, limitations.get("confirmedGapToolCount").getAsInt());
  }

  @Test
  void testProgressRetrievalContractIsBoundedAndReadOnly() {
    String operationId = ProgressTracker.start("phase0-progress-contract", 2);

    JsonObject active = JsonParser.parseString(ProgressTracker.listActive()).getAsJsonObject();
    assertEquals("success", active.get("status").getAsString());
    assertTrue(active.getAsJsonArray("operations").toString().contains(operationId));

    ProgressTracker.update(operationId, 1, "half complete");
    JsonObject progress = JsonParser.parseString(ProgressTracker.getProgress(operationId)).getAsJsonObject();
    assertEquals(operationId, progress.get("operationId").getAsString());
    assertEquals("phase0-progress-contract", progress.get("operationType").getAsString());
    assertEquals(2, progress.get("totalSteps").getAsInt());
    assertEquals(1, progress.get("currentStep").getAsInt());
    assertEquals(50, progress.get("percentComplete").getAsInt());
    assertFalse(progress.get("completed").getAsBoolean());
    assertTrue(progress.getAsJsonArray("recentMilestones").toString().contains("half complete"));

    ProgressTracker.complete(operationId, "complete");
    JsonObject completed = JsonParser.parseString(ProgressTracker.getProgress(operationId)).getAsJsonObject();
    assertEquals(100, completed.get("percentComplete").getAsInt());
    assertTrue(completed.get("completed").getAsBoolean());
    assertFalse(completed.get("failed").getAsBoolean());

    JsonObject after = JsonParser.parseString(ProgressTracker.listActive()).getAsJsonObject();
    assertFalse(after.getAsJsonArray("operations").toString().contains(operationId));

    JsonObject missing = JsonParser.parseString(ProgressTracker.getProgress("missing-phase0-operation")).getAsJsonObject();
    assertEquals("error", missing.get("status").getAsString());
  }

  @Test
  void testPhase0EvidenceIsDiscoverableThroughCapabilities() {
    JsonObject capabilities = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject inventory = capabilities.getAsJsonObject("phase0EvidenceInventory");
    JsonObject fixtures = inventory.getAsJsonObject("acceptanceFixtures");

    assertEquals("1.10", inventory.get("inventoryVersion").getAsString());
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
