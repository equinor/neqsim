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
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
    assertEquals(21, limitations.getAsJsonArray("contractTestedTools").size());
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getCapabilities"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getSchema"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getExample"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getBenchmarkTrust"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("checkToolAccess"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("manageIndustrialProfile"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("searchComponents"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("queryDataCatalog"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getProgress"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("inspectApi"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("manageValidationProfile"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("manageModel"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("validateInput"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("manageSession"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("manageState"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getAdjustableParameters"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("listSimulationUnits"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("listUnitVariables"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getSimulationVariable"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("diagnoseAutomation"));
    assertTrue(limitations.getAsJsonArray("contractTestedTools").toString().contains("getAutomationLearningReport"));
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

    JsonObject progress = coverageRecords.getAsJsonObject("getProgress");
    assertEquals("CONTRACT_TESTED", progress.get("coverageStatus").getAsString());
    assertFalse(progress.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(progress.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_PROGRESS_RETRIEVAL",
        progress.get("benchmarkApplicability").getAsString());
    assertEquals(4, progress.get("contractEvidenceCount").getAsInt());
    assertEquals(4, progress.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(progress.get("evidenceBoundary").getAsString().contains("real-protocol listActive"));
    assertTrue(progress.get("evidenceBoundary").getAsString().contains("does not validate"));

    JsonObject apiInspection = coverageRecords.getAsJsonObject("inspectApi");
    assertEquals("CONTRACT_TESTED", apiInspection.get("coverageStatus").getAsString());
    assertFalse(apiInspection.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(apiInspection.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_RUNTIME_API_INSPECTION",
        apiInspection.get("benchmarkApplicability").getAsString());
    assertEquals(5, apiInspection.get("contractEvidenceCount").getAsInt());
    assertEquals(5, apiInspection.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(apiInspection.getAsJsonArray("contractEvidenceSources").toString().contains("ApiKnowledgeRunner.java"));
    assertTrue(
        apiInspection.getAsJsonArray("contractEvidenceSources").toString().contains("test_inspect_api_protocol.py"));
    assertTrue(apiInspection.get("evidenceBoundary").getAsString().contains("without executing"));

    JsonObject validationProfile = coverageRecords.getAsJsonObject("manageValidationProfile");
    assertEquals("CONTRACT_TESTED", validationProfile.get("coverageStatus").getAsString());
    assertFalse(validationProfile.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(validationProfile.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_VALIDATION_PROFILE_GOVERNANCE",
        validationProfile.get("benchmarkApplicability").getAsString());
    assertEquals(6, validationProfile.get("contractEvidenceCount").getAsInt());
    assertEquals(6, validationProfile.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(validationProfile.getAsJsonArray("contractEvidenceSources").toString()
        .contains("ValidationProfileRunnerTest.java"));
    assertTrue(validationProfile.getAsJsonArray("contractEvidenceSources").toString()
        .contains("test_validation_profile_protocol.py"));
    assertTrue(validationProfile.get("evidenceBoundary").getAsString().contains("legal applicability"));

    JsonObject modelRegistry = coverageRecords.getAsJsonObject("manageModel");
    assertEquals("CONTRACT_TESTED", modelRegistry.get("coverageStatus").getAsString());
    assertFalse(modelRegistry.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(modelRegistry.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_MODEL_REGISTRY_LIFECYCLE",
        modelRegistry.get("benchmarkApplicability").getAsString());
    assertEquals(5, modelRegistry.get("contractEvidenceCount").getAsInt());
    assertEquals(5, modelRegistry.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(modelRegistry.getAsJsonArray("contractEvidenceSources").toString().contains("ModelRegistryTest.java"));
    assertTrue(
        modelRegistry.getAsJsonArray("contractEvidenceSources").toString().contains("test_model_registry_protocol.py"));
    assertTrue(modelRegistry.get("evidenceBoundary").getAsString().contains("server restarts"));
    assertTrue(modelRegistry.get("evidenceBoundary").getAsString().contains("numerical model accuracy"));

    JsonObject sessionLifecycle = coverageRecords.getAsJsonObject("manageSession");
    assertEquals("CONTRACT_TESTED", sessionLifecycle.get("coverageStatus").getAsString());
    assertFalse(sessionLifecycle.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(sessionLifecycle.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_SESSION_LIFECYCLE",
        sessionLifecycle.get("benchmarkApplicability").getAsString());
    assertEquals(6, sessionLifecycle.get("contractEvidenceCount").getAsInt());
    assertEquals(6, sessionLifecycle.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(sessionLifecycle.getAsJsonArray("contractEvidenceSources").toString()
        .contains("SessionRunnerContractTest.java"));
    assertTrue(
        sessionLifecycle.getAsJsonArray("contractEvidenceSources").toString().contains("test_session_protocol.py"));
    assertTrue(sessionLifecycle.get("evidenceBoundary").getAsString().contains("restart durability"));
    assertTrue(sessionLifecycle.get("evidenceBoundary").getAsString().contains("component or energy closure"));

    JsonObject statePersistence = coverageRecords.getAsJsonObject("manageState");
    assertEquals("CONTRACT_TESTED", statePersistence.get("coverageStatus").getAsString());
    assertFalse(statePersistence.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(statePersistence.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_LOCAL_STATE_PERSISTENCE_LIFECYCLE",
        statePersistence.get("benchmarkApplicability").getAsString());
    assertEquals(5, statePersistence.get("contractEvidenceCount").getAsInt());
    assertEquals(5, statePersistence.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(statePersistence.getAsJsonArray("contractEvidenceSources").toString()
        .contains("StatePersistenceRunnerTest.java"));
    assertTrue(statePersistence.getAsJsonArray("contractEvidenceSources").toString()
        .contains("test_state_persistence_protocol.py"));
    assertTrue(statePersistence.get("evidenceBoundary").getAsString().contains("persistence lifecycle"));
    assertTrue(statePersistence.get("evidenceBoundary").getAsString().contains("numerical replay"));

    JsonObject adjustableParameters = coverageRecords.getAsJsonObject("getAdjustableParameters");
    assertEquals("CONTRACT_TESTED", adjustableParameters.get("coverageStatus").getAsString());
    assertFalse(adjustableParameters.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(adjustableParameters.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_AUTOMATION_PARAMETER_DISCOVERY",
        adjustableParameters.get("benchmarkApplicability").getAsString());
    assertEquals(5, adjustableParameters.get("contractEvidenceCount").getAsInt());
    assertEquals(5, adjustableParameters.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(adjustableParameters.getAsJsonArray("contractEvidenceSources").toString()
        .contains("AutomationLoopRunnerTest.java"));
    assertTrue(adjustableParameters.getAsJsonArray("contractEvidenceSources").toString()
        .contains("test_adjustable_parameters_protocol.py"));
    assertTrue(adjustableParameters.get("evidenceBoundary").getAsString().contains("optional bounds"));
    assertTrue(adjustableParameters.get("evidenceBoundary").getAsString().contains("optimization quality"));

    JsonObject validation = coverageRecords.getAsJsonObject("validateInput");
    assertEquals("CONTRACT_TESTED", validation.get("coverageStatus").getAsString());
    assertFalse(validation.get("toolSpecificTrustAvailable").getAsBoolean());
    assertTrue(validation.get("contractTrustAvailable").getAsBoolean());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_PREFLIGHT_INPUT_VALIDATION",
        validation.get("benchmarkApplicability").getAsString());
    assertEquals(4, validation.get("contractEvidenceCount").getAsInt());
    assertTrue(validation.getAsJsonArray("contractEvidenceSources").toString().contains("ValidatorTest.java"));
    assertTrue(validation.getAsJsonArray("contractEvidenceSources").toString()
        .contains("test_validate_input_protocol.py"));
    assertTrue(validation.get("evidenceBoundary").getAsString().contains("does not execute a model"));
    assertTrue(validation.get("evidenceBoundary").getAsString().contains("conservation"));

    String[] automationTools = new String[] { "listSimulationUnits", "listUnitVariables", "getSimulationVariable",
        "diagnoseAutomation", "getAutomationLearningReport" };
    for (String toolName : automationTools) {
      JsonObject automation = coverageRecords.getAsJsonObject(toolName);
      assertEquals("CONTRACT_TESTED", automation.get("coverageStatus").getAsString(), toolName);
      assertFalse(automation.get("toolSpecificTrustAvailable").getAsBoolean(), toolName);
      assertTrue(automation.get("contractTrustAvailable").getAsBoolean(), toolName);
      assertEquals(5, automation.get("contractEvidenceCount").getAsInt(), toolName);
      assertEquals(5, automation.getAsJsonArray("contractEvidenceSources").size(), toolName);
      assertTrue(
          automation.getAsJsonArray("contractEvidenceSources").toString().contains("AutomationReadContractTest.java"),
          toolName);
      assertTrue(
          automation.getAsJsonArray("contractEvidenceSources").toString().contains("test_automation_read_protocol.py"),
          toolName);
    }
    JsonObject variableRead = coverageRecords.getAsJsonObject("getSimulationVariable");
    assertEquals("NOT_APPLICABLE_SOFTWARE_CONTRACT_AUTOMATION_VARIABLE_READ",
        variableRead.get("benchmarkApplicability").getAsString());
    assertTrue(variableRead.get("evidenceBoundary").getAsString().contains("not benchmark-validated"));
    JsonObject diagnostics = coverageRecords.getAsJsonObject("diagnoseAutomation");
    assertTrue(diagnostics.get("evidenceBoundary").getAsString().contains("not causal diagnosis"));
    assertFalse(inventory.get("complete").getAsBoolean());
  }

  @Test
  void testProgressPromotionIsAppliedAtomically() {
    JsonObject limitations = McpEvidenceInventory.build().getAsJsonObject("knownLimitations");
    JsonObject progress = limitations.getAsJsonObject("coverageRecords").getAsJsonObject("getProgress");

    assertEquals("CONTRACT_TESTED", progress.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_PROGRESS_RETRIEVAL",
        progress.get("benchmarkApplicability").getAsString());
    assertEquals(4, progress.get("contractEvidenceCount").getAsInt());
    assertEquals(4, progress.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(progress.getAsJsonArray("contractEvidenceSources").toString().contains("ProgressTracker.java"));
    assertTrue(progress.getAsJsonArray("contractEvidenceSources").toString().contains("test_mcp_server.py"));
    assertTrue(progress.get("evidenceBoundary").getAsString().contains("real-protocol listActive"));
    assertTrue(progress.get("evidenceBoundary").getAsString().contains("does not validate"));
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
  }

  @Test
  void testValidateInputPromotionIsAppliedAtomically() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    JsonObject coverage = limitations.getAsJsonObject("coverageRecords").getAsJsonObject("validateInput");

    assertEquals("1.23", inventory.get("inventoryVersion").getAsString());
    assertEquals(0, limitations.get("contractPromotionCandidateCount").getAsInt());
    assertEquals("CONTRACT_TESTED", coverage.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_PREFLIGHT_INPUT_VALIDATION",
        coverage.get("benchmarkApplicability").getAsString());
    assertEquals(4, coverage.get("contractEvidenceCount").getAsInt());
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("Validator.java"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("ValidatorTest.java"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString()
        .contains("test_validate_input_protocol.py"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("issue severity/remediation"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("physical fidelity"));
    assertTrue(limitations.get("promotionBoundary").getAsString().contains("validateInput"));
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
  }

  @Test
  void testAdjustableParameterDiscoveryPromotionIsAppliedAtomically() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    JsonObject candidates = limitations.getAsJsonObject("contractPromotionCandidates");
    JsonObject coverage = limitations.getAsJsonObject("coverageRecords").getAsJsonObject("getAdjustableParameters");

    assertEquals("1.23", inventory.get("inventoryVersion").getAsString());
    assertEquals(0, limitations.get("contractPromotionCandidateCount").getAsInt());
    assertEquals(0, candidates.size());
    assertEquals("CONTRACT_TESTED", coverage.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_AUTOMATION_PARAMETER_DISCOVERY",
        coverage.get("benchmarkApplicability").getAsString());
    assertTrue(coverage.get("contractTrustAvailable").getAsBoolean());
    assertEquals(5, coverage.get("contractEvidenceCount").getAsInt());
    assertEquals(5, coverage.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("AutomationRunner.java"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("AutomationLoopRunnerTest.java"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString()
        .contains("test_adjustable_parameters_protocol.py"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_mcp_server.py"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("direct-definition/model-handle equivalence"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("mass or energy conservation"));
    assertTrue(limitations.get("promotionBoundary").getAsString().contains("validateInput"));
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
  }

  @Test
  void testApiInspectionPromotionIsAppliedAtomically() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    JsonObject candidates = limitations.getAsJsonObject("contractPromotionCandidates");
    JsonObject coverage = limitations.getAsJsonObject("coverageRecords").getAsJsonObject("inspectApi");
    JsonObject tests = inventory.getAsJsonObject("tests");

    assertEquals(0, limitations.get("contractPromotionCandidateCount").getAsInt());
    assertEquals(0, candidates.size());
    assertEquals("CONTRACT_TESTED", coverage.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_RUNTIME_API_INSPECTION",
        coverage.get("benchmarkApplicability").getAsString());
    assertTrue(coverage.get("contractTrustAvailable").getAsBoolean());
    assertEquals(5, coverage.get("contractEvidenceCount").getAsInt());
    assertEquals(5, coverage.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("ApiKnowledgeRunner.java"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("ApiKnowledgeRunnerTest.java"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_inspect_api_protocol.py"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("fail-closed non-NeqSim rejection"));
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
    assertEquals(3, tests.get("focusedApiProtocolScenarioCount").getAsInt());
    assertEquals("neqsim-mcp-server/test_inspect_api_protocol.py",
        tests.get("focusedApiProtocolHarness").getAsString());
  }

  @Test
  void testValidationProfilePromotionIsAppliedAtomically() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    JsonObject candidates = limitations.getAsJsonObject("contractPromotionCandidates");
    JsonObject coverage = limitations.getAsJsonObject("coverageRecords").getAsJsonObject("manageValidationProfile");

    assertEquals("1.23", inventory.get("inventoryVersion").getAsString());
    assertEquals(0, limitations.get("contractPromotionCandidateCount").getAsInt());
    assertEquals(0, candidates.size());
    assertEquals("CONTRACT_TESTED", coverage.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_VALIDATION_PROFILE_GOVERNANCE",
        coverage.get("benchmarkApplicability").getAsString());
    assertTrue(coverage.get("contractTrustAvailable").getAsBoolean());
    assertEquals(6, coverage.get("contractEvidenceCount").getAsInt());
    assertEquals(6, coverage.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(
        coverage.getAsJsonArray("contractEvidenceSources").toString().contains("ValidationProfileRunnerTest.java"));
    assertTrue(
        coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_validation_profile_protocol.py"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("legal applicability"));
    assertTrue(limitations.get("promotionBoundary").getAsString().contains("CONTRACT_TESTED"));
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
  }

  @Test
  void testModelRegistryPromotionIsAppliedAtomically() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    JsonObject candidates = limitations.getAsJsonObject("contractPromotionCandidates");
    JsonObject coverage = limitations.getAsJsonObject("coverageRecords").getAsJsonObject("manageModel");

    assertEquals("1.23", inventory.get("inventoryVersion").getAsString());
    assertEquals(0, limitations.get("contractPromotionCandidateCount").getAsInt());
    assertEquals(0, candidates.size());
    assertEquals("CONTRACT_TESTED", coverage.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_MODEL_REGISTRY_LIFECYCLE",
        coverage.get("benchmarkApplicability").getAsString());
    assertTrue(coverage.get("contractTrustAvailable").getAsBoolean());
    assertEquals(5, coverage.get("contractEvidenceCount").getAsInt());
    assertEquals(5, coverage.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("ModelRegistry.java"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("ModelRegistryTest.java"));
    assertTrue(
        coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_model_registry_protocol.py"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_mcp_server.py"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("distributed cache coherence"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("mass or energy closure"));
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
  }

  @Test
  void testSessionLifecyclePromotionIsAppliedAtomically() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    JsonObject candidates = limitations.getAsJsonObject("contractPromotionCandidates");
    JsonObject coverage = limitations.getAsJsonObject("coverageRecords").getAsJsonObject("manageSession");

    assertEquals("1.23", inventory.get("inventoryVersion").getAsString());
    assertEquals(0, limitations.get("contractPromotionCandidateCount").getAsInt());
    assertEquals(0, candidates.size());
    assertEquals("CONTRACT_TESTED", coverage.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_SESSION_LIFECYCLE",
        coverage.get("benchmarkApplicability").getAsString());
    assertTrue(coverage.get("contractTrustAvailable").getAsBoolean());
    assertEquals(6, coverage.get("contractEvidenceCount").getAsInt());
    assertEquals(6, coverage.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("SessionRunner.java"));
    assertTrue(
        coverage.getAsJsonArray("contractEvidenceSources").toString().contains("SessionRunnerContractTest.java"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_session_protocol.py"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_mcp_server.py"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("distributed coherence"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("causal troubleshooting"));
    assertTrue(limitations.get("promotionBoundary").getAsString().contains("validateInput"));
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
  }

  @Test
  void testStatePersistencePromotionIsAppliedAtomically() {
    JsonObject inventory = McpEvidenceInventory.build();
    JsonObject limitations = inventory.getAsJsonObject("knownLimitations");
    JsonObject candidates = limitations.getAsJsonObject("contractPromotionCandidates");
    JsonObject coverage = limitations.getAsJsonObject("coverageRecords").getAsJsonObject("manageState");

    assertEquals("1.23", inventory.get("inventoryVersion").getAsString());
    assertEquals(0, limitations.get("contractPromotionCandidateCount").getAsInt());
    assertEquals(0, candidates.size());
    assertEquals("CONTRACT_TESTED", coverage.get("coverageStatus").getAsString());
    assertEquals("NOT_APPLICABLE_NON_NUMERICAL_LOCAL_STATE_PERSISTENCE_LIFECYCLE",
        coverage.get("benchmarkApplicability").getAsString());
    assertTrue(coverage.get("contractTrustAvailable").getAsBoolean());
    assertEquals(5, coverage.get("contractEvidenceCount").getAsInt());
    assertEquals(5, coverage.getAsJsonArray("contractEvidenceSources").size());
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("StatePersistenceRunner.java"));
    assertTrue(
        coverage.getAsJsonArray("contractEvidenceSources").toString().contains("StatePersistenceRunnerTest.java"));
    assertTrue(
        coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_state_persistence_protocol.py"));
    assertTrue(coverage.getAsJsonArray("contractEvidenceSources").toString().contains("test_mcp_server.py"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("sandbox/path fail-closed"));
    assertTrue(coverage.get("evidenceBoundary").getAsString().contains("distributed durability"));
    assertTrue(limitations.get("promotionBoundary").getAsString().contains("validateInput"));
    assertEquals(21, limitations.get("contractTestedToolCount").getAsInt());
    assertEquals(30, limitations.get("confirmedGapToolCount").getAsInt());
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

    JsonObject missing = JsonParser.parseString(ProgressTracker.getProgress("missing-phase0-operation"))
        .getAsJsonObject();
    assertEquals("error", missing.get("status").getAsString());
  }

  @Test
  void testPhase0EvidenceIsDiscoverableThroughCapabilities() {
    JsonObject capabilities = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    JsonObject inventory = capabilities.getAsJsonObject("phase0EvidenceInventory");
    JsonObject fixtures = inventory.getAsJsonObject("acceptanceFixtures");

    assertEquals("1.23", inventory.get("inventoryVersion").getAsString());
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
