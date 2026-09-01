package neqsim.mcp.runners;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Builds the Phase 0 MCP test, guide, limitation, merged-foundation, acceptance, and campaign-matrix evidence
 * inventory.
 */
public final class McpEvidenceInventory {

  private static final int JAVA_TEST_CLASS_COUNT = 69;
  private static final int PROTOCOL_SCENARIO_COUNT = 94;
  private static final int FOCUSED_API_PROTOCOL_SCENARIO_COUNT = 3;

  /** Private constructor for utility class. */
  private McpEvidenceInventory() {
  }

  /**
   * Builds the evidence inventory.
   *
   * @return current Phase 0 evidence
   */
  public static JsonObject build() {
    JsonObject inventory = new JsonObject();
    inventory.addProperty("inventoryVersion", "1.22");
    inventory.add("tests", buildTests());
    inventory.add("guides", buildGuides());
    inventory.add("mergedFoundations", buildMergedFoundations());
    inventory.add("knownLimitations", buildKnownLimitations());
    inventory.add("acceptanceFixtures", McpAcceptanceFixtureCatalog.build());
    inventory.add("acceptanceBaselineContract", McpAcceptanceBaselineRunner.describe());
    inventory.add("campaignMatrix", McpCampaignMatrix.build());
    inventory.addProperty("advisoryBoundary",
        "Evidence discovery does not certify a calculation or replace qualified engineering review");
    inventory.addProperty("complete", false);
    inventory.addProperty("completionReason",
        "Published surfaces, merged foundations, per-tool trust coverage, four-scale fixtures, bounded baseline harness, and current-tree campaign matrices are inventoried, but confirmed trust gaps, run-specific numeric closure gaps, later-phase acceptance evidence, and merged-master completion audit remain incomplete");
    return inventory;
  }

  /** Builds source-level test evidence. */
  private static JsonObject buildTests() {
    JsonObject tests = new JsonObject();
    tests.addProperty("javaTestClassCount", JAVA_TEST_CLASS_COUNT);
    tests.addProperty("javaTestRoot", "src/test/java/neqsim/mcp");
    tests.addProperty("protocolScenarioCount", PROTOCOL_SCENARIO_COUNT);
    tests.addProperty("protocolHarness", "neqsim-mcp-server/test_mcp_server.py");
    tests.addProperty("focusedApiProtocolScenarioCount", FOCUSED_API_PROTOCOL_SCENARIO_COUNT);
    tests.addProperty("focusedApiProtocolHarness", "neqsim-mcp-server/test_inspect_api_protocol.py");
    tests.addProperty("sourceCountContract",
        "CapabilitiesRunnerTest, the packaged MCP protocol suite, and focused API protocol qualification freeze these source inventories");
    tests.addProperty("executionBoundary",
        "Inventory presence is not an execution result; use exact-head CI and recorded test output as pass evidence");
    return tests;
  }

  /** Builds the exact MCP guide inventory. */
  private static JsonObject buildGuides() {
    JsonObject guides = new JsonObject();
    JsonArray entries = new JsonArray();
    entries.add(guide("server-readme", "neqsim-mcp-server/README.md",
        "Installation, profiles, tools, workflows, testing, and troubleshooting"));
    entries.add(guide("protocol-contract", "neqsim-mcp-server/MCP_CONTRACT.md",
        "Versioning, stability, response envelopes, governance, security, and trust metadata"));
    entries.add(guide("api-reference", "neqsim-mcp-server/docs/API_REFERENCE.md",
        "Tool parameters, schemas, examples, resources, and selected result contracts"));
    entries.add(guide("surface-inventory", "neqsim-mcp-server/docs/SURFACE_INVENTORY.md",
        "Exact protocol, implementation, equipment, reporting, and Phase 0 evidence inventory"));
    entries.add(guide("foundation-traceability", "neqsim-mcp-server/docs/FOUNDATION_TRACEABILITY.md",
        "Merged #2874, #2875, and #3152 capability evidence and remaining boundaries"));
    entries.add(guide("acceptance-fixtures", "neqsim-mcp-server/docs/ACCEPTANCE_FIXTURES.md",
        "Four public synthetic acceptance scales and canonical execution routes"));
    entries.add(guide("acceptance-baselines", "neqsim-mcp-server/docs/ACCEPTANCE_BASELINES.md",
        "Bounded exact-run measurements, interpretation limits, and explicit evidence gaps"));
    entries.add(guide("campaign-matrix", "neqsim-mcp-server/docs/CAMPAIGN_MATRIX.md",
        "All 66 campaign criteria and discipline-level trust maturity with explicit gaps"));
    guides.addProperty("guideCount", entries.size());
    guides.add("entries", entries);
    return guides;
  }

  /** Builds a criterion-level reconciliation of the three merged MCP campaign foundations. */
  private static JsonObject buildMergedFoundations() {
    JsonObject foundations = new JsonObject();
    foundations.addProperty("complete", true);
    foundations.addProperty("foundationCount", 3);
    foundations.addProperty("evidenceDocument", "neqsim-mcp-server/docs/FOUNDATION_TRACEABILITY.md");

    JsonArray entries = new JsonArray();
    entries.add(foundation(2874, "0894b7820b6317c64ccaaaee5a3326f5bbdf5d77",
        "Caller identity, recoverable security enforcement, principal-scoped approvals, and fail-closed admin actions",
        new String[] { "src/main/java/neqsim/mcp/runners/McpRequestContext.java",
            "src/main/java/neqsim/mcp/runners/SecurityRunner.java",
            "src/main/java/neqsim/mcp/runners/IndustrialProfile.java",
            "neqsim-mcp-server/src/main/java/neqsim/mcp/server/McpIdentityResolver.java" },
        new String[] { "src/test/java/neqsim/mcp/runners/McpSecurityEnforcementTest.java" },
        "Security remains disabled by default for local desktop use; governed deployments must supply transport identity and configured admin policy"));
    entries.add(foundation(2875, "7dac75744ebf25cfbe2b4ccd763bb30c3d14cbdf",
        "Tenant-scoped model handles, solved-model reuse, response-size protection, execution bounds, and complete tool-catalog coverage",
        new String[] { "src/main/java/neqsim/mcp/runners/ModelRegistry.java",
            "src/main/java/neqsim/mcp/runners/ResponseSizeGuard.java",
            "src/main/java/neqsim/mcp/runners/McpExecutionPolicy.java",
            "src/main/java/neqsim/mcp/runners/CapabilitiesRunner.java" },
        new String[] { "src/test/java/neqsim/mcp/runners/ModelRegistryTest.java",
            "src/test/java/neqsim/mcp/runners/ResponseSizeGuardTest.java",
            "src/test/java/neqsim/mcp/runners/McpToolSurfaceContractTest.java" },
        "Bounded execution and selective retrieval do not by themselves establish scientific accuracy for every published tool"));
    entries.add(foundation(3152, "bd07729f105efb48b14c641697e0f99fe9af6898",
        "Runtime capability discovery/execution, canonical replayable ProcessSystem/ProcessModel definitions, design/capacity evidence, and typed two-fluid pipeline results",
        new String[] { "src/main/java/neqsim/mcp/runners/GeneralCapabilityRunner.java",
            "src/main/java/neqsim/mcp/runners/ProcessRunner.java",
            "src/main/java/neqsim/process/processmodel/JsonProcessBuilder.java",
            "src/main/java/neqsim/process/util/monitor/TwoFluidPipeResponse.java" },
        new String[] { "src/test/java/neqsim/mcp/runners/CapabilitiesRunnerTest.java",
            "src/test/java/neqsim/mcp/runners/ProcessRunnerTest.java" },
        "Generic execution remains narrower than discovery; stateful calculations stay behind curated runners and domain validation remains authoritative"));

    foundations.add("entries", entries);
    foundations.addProperty("remainingPhase0Boundary",
        "Close defensible trust and numeric balance/report gaps, merge and re-audit the campaign matrices, and preserve explicit later-phase evidence gaps");
    return foundations;
  }

  /** Builds one merged-foundation descriptor. */
  private static JsonObject foundation(int pullRequest, String mergeCommit, String capability, String[] sourcePaths,
      String[] testPaths, String boundary) {
    JsonObject entry = new JsonObject();
    entry.addProperty("pullRequest", pullRequest);
    entry.addProperty("mergeCommit", mergeCommit);
    entry.addProperty("status", "MERGED_CURRENT_MASTER_FOUNDATION");
    entry.addProperty("capability", capability);
    entry.add("sourcePaths", toJsonArray(java.util.Arrays.asList(sourcePaths)));
    entry.add("testPaths", toJsonArray(java.util.Arrays.asList(testPaths)));
    entry.addProperty("boundary", boundary);
    return entry;
  }

  /** Builds limitation, maturity, and explicit per-tool trust coverage from BenchmarkTrust. */
  private static JsonObject buildKnownLimitations() {
    JsonObject trustReport = JsonParser.parseString(BenchmarkTrust.getTrustReport()).getAsJsonObject();
    JsonObject explicitTools = trustReport.getAsJsonObject("tools");

    Set<String> publishedTools = IndustrialProfile.getAllKnownTools();
    Set<String> genericTools = new LinkedHashSet<String>(publishedTools);
    genericTools.removeAll(explicitTools.keySet());
    Set<String> contractTestedTools = new LinkedHashSet<String>();

    int limitationCount = 0;
    int unsupportedConditionCount = 0;
    int validationCaseCount = 0;
    int verifiedValidationCaseCount = 0;
    JsonObject maturityCounts = new JsonObject();
    for (Map.Entry<String, JsonElement> entry : explicitTools.entrySet()) {
      JsonObject trust = entry.getValue().getAsJsonObject();
      limitationCount += arraySize(trust, "knownLimitations");
      unsupportedConditionCount += arraySize(trust, "unsupported");
      validationCaseCount += arraySize(trust, "validationCases");
      verifiedValidationCaseCount += verifiedValidationCaseCount(trust);
      String maturity = trust.has("maturityLevel") ? trust.get("maturityLevel").getAsString() : "UNDECLARED";
      int current = maturityCounts.has(maturity) ? maturityCounts.get(maturity).getAsInt() : 0;
      maturityCounts.addProperty(maturity, current + 1);
    }

    JsonObject coverageRecords = new JsonObject();
    int explicitCoverageRecordCount = 0;
    int contractTestedRecordCount = 0;
    int confirmedGapRecordCount = 0;
    for (String toolName : new java.util.TreeSet<String>(publishedTools)) {
      JsonObject record = new JsonObject();
      String implementationClass = McpImplementationInventory.getImplementationClass(toolName);
      if (implementationClass != null) {
        record.addProperty("implementationClass", implementationClass);
      }

      if (explicitTools.has(toolName)) {
        JsonObject trust = explicitTools.getAsJsonObject(toolName);
        record.addProperty("coverageStatus", "EXPLICIT_TRUST");
        record.addProperty("toolSpecificTrustAvailable", true);
        record.addProperty("maturityLevel",
            trust.has("maturityLevel") ? trust.get("maturityLevel").getAsString() : "UNDECLARED");
        record.addProperty("knownLimitationCount", arraySize(trust, "knownLimitations"));
        record.addProperty("unsupportedConditionCount", arraySize(trust, "unsupported"));
        record.addProperty("validationCaseCount", arraySize(trust, "validationCases"));
        record.addProperty("verifiedValidationCaseCount", verifiedValidationCaseCount(trust));
        explicitCoverageRecordCount++;
      } else if (addContractTestedEvidence(toolName, record)) {
        contractTestedTools.add(toolName);
        contractTestedRecordCount++;
      } else {
        record.addProperty("coverageStatus", "CONFIRMED_GAP");
        record.addProperty("toolSpecificTrustAvailable", false);
        record.addProperty("maturityLevel", BenchmarkTrust.getMaturityLevel(toolName));
        record.addProperty("knownLimitationCount", 0);
        record.addProperty("unsupportedConditionCount", 0);
        record.addProperty("validationCaseCount", 0);
        record.addProperty("verifiedValidationCaseCount", 0);
        record.addProperty("gapReason",
            "No specific trust/contract evidence; generic TESTED means compatibility, not validation, accuracy, or applicability.");
        confirmedGapRecordCount++;
      }
      coverageRecords.add(toolName, record);
    }

    JsonObject coverageDefinitions = new JsonObject();
    coverageDefinitions.addProperty("EXPLICIT_TRUST",
        "Tool-specific BenchmarkTrust metadata exists; use its declared maturity, validation cases, accuracy bounds, and limitations");
    coverageDefinitions.addProperty("CONTRACT_TESTED",
        "Bounded MCP software-contract behavior has direct source, contract-test, and real-protocol evidence; no engineering accuracy benchmark is implied");
    coverageDefinitions.addProperty("CONFIRMED_GAP",
        "No tool-specific BenchmarkTrust entry or bounded software-contract evidence closes the gap; generic fallback maturity must not be interpreted as benchmark validation");

    JsonObject promotionCandidates = buildContractPromotionCandidates();
    JsonObject limitations = new JsonObject();
    limitations.addProperty("sourceTool", "getBenchmarkTrust");
    limitations.addProperty("publishedToolCount", publishedTools.size());
    limitations.addProperty("explicitTrustToolCount", explicitTools.size());
    limitations.addProperty("genericTrustToolCount", genericTools.size());
    limitations.addProperty("contractTestedToolCount", contractTestedRecordCount);
    limitations.addProperty("confirmedGapToolCount", confirmedGapRecordCount);
    limitations.addProperty("coverageRecordCount", coverageRecords.size());
    limitations.addProperty("explicitCoverageRecordCount", explicitCoverageRecordCount);
    limitations.addProperty("coverageComplete", coverageRecords.size() == publishedTools.size());
    limitations.addProperty("scientificValidationComplete", false);
    limitations.addProperty("knownLimitationCount", limitationCount);
    limitations.addProperty("unsupportedConditionCount", unsupportedConditionCount);
    limitations.addProperty("validationCaseCount", validationCaseCount);
    limitations.addProperty("verifiedValidationCaseCount", verifiedValidationCaseCount);
    limitations.add("maturityCounts", maturityCounts);
    limitations.add("explicitTrustTools", toJsonArray(explicitTools.keySet()));
    limitations.add("genericTrustTools", toJsonArray(genericTools));
    limitations.add("contractTestedTools", toJsonArray(contractTestedTools));
    limitations.add("coverageStatusDefinitions", coverageDefinitions);
    limitations.add("coverageRecords", coverageRecords);
    limitations.addProperty("contractPromotionCandidateCount", promotionCandidates.size());
    limitations.add("contractPromotionCandidates", promotionCandidates);
    limitations.addProperty("promotionBoundary",
        "getAdjustableParameters is CONTRACT_TESTED from merged metadata discovery, definition/handle equivalence, fail-closed inputs, and packaged transport; inventory 1.22 queues no further candidate.");
    limitations.addProperty("complete", genericTools.isEmpty());
    limitations.addProperty("gapBoundary",
        "All 71 tools have coverage records: twenty bounded software contracts are contract-tested without numerical benchmark claims; CONFIRMED_GAP marks the remaining missing tool-specific trust evidence.");
    limitations.addProperty("resultBoundary",
        "Per-result provenance, convergence, warnings, assumptions, units, and limitations remain authoritative for an executed case");
    return limitations;
  }

  /** Returns evidence-qualified candidates for future atomic contract-status promotion. */
  private static JsonObject buildContractPromotionCandidates() {
    return new JsonObject();
  }

  /**
   * Adds bounded contract evidence for an MCP tool whose qualified scope is software-contract behavior.
   *
   * @param toolName published MCP tool name
   * @param record mutable coverage record
   * @return true when the tool has a bounded contract-evidence definition
   */
  private static boolean addContractTestedEvidence(String toolName, JsonObject record) {
    String benchmarkApplicability;
    String evidenceBoundary;
    String[] evidenceSources;

    switch (toolName) {
    case "getCapabilities":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_DISCOVERY";
      evidenceSources = new String[] { "src/test/java/neqsim/mcp/runners/CapabilitiesRunnerTest.java",
          "src/test/java/neqsim/mcp/runners/McpToolSurfaceContractTest.java",
          "src/test/java/neqsim/mcp/runners/ResponseSizeGuardTest.java", "neqsim-mcp-server/test_mcp_server.py" };
      evidenceBoundary = "Capability discovery, published-surface reconciliation, response guarding, and real-protocol retrieval are contract-tested; this is not scientific validation of advertised calculations";
      break;
    case "getSchema":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_SCHEMA_CATALOG";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/catalog/SchemaCatalog.java",
          "src/test/java/neqsim/mcp/runners/CapabilitiesRunnerTest.java", "neqsim-mcp-server/test_mcp_server.py" };
      evidenceBoundary = "All 142 canonical input/output schema resources resolve through source, catalog reconciliation, and the packaged MCP protocol; schema availability does not validate the calculations described by those schemas";
      break;
    case "getExample":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_EXAMPLE_CATALOG";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/catalog/ExampleCatalog.java",
          "src/test/java/neqsim/mcp/runners/CapabilitiesRunnerTest.java", "neqsim-mcp-server/test_mcp_server.py" };
      evidenceBoundary = "All 114 catalog examples resolve through source, catalog reconciliation, and the packaged MCP protocol; example availability does not establish scientific accuracy or fitness for a facility decision";
      break;
    case "getBenchmarkTrust":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_TRUST_CATALOG";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/BenchmarkTrust.java",
          "src/test/java/neqsim/mcp/runners/McpEvidenceInventoryFoundationTests.java",
          "neqsim-mcp-server/test_mcp_server.py" };
      evidenceBoundary = "Trust-catalog all-tool and single-tool retrieval plus runtime inventory reconciliation are contract-tested; retrieval fidelity does not validate the scientific claims inside any trust page";
      break;
    case "checkToolAccess":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_ACCESS_POLICY";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/IndustrialProfile.java",
          "src/test/java/neqsim/mcp/runners/IndustrialProfileTest.java",
          "src/test/java/neqsim/mcp/runners/McpSecurityEnforcementTest.java", "neqsim-mcp-server/test_mcp_server.py" };
      evidenceBoundary = "The profile-tier access matrix, fail-closed security enforcement, and real-protocol access response are contract-tested; this does not grant external authorization or plant authority";
      break;
    case "manageIndustrialProfile":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_GOVERNANCE_POLICY";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/IndustrialProfile.java",
          "src/test/java/neqsim/mcp/runners/IndustrialProfileTest.java",
          "src/test/java/neqsim/mcp/runners/McpPrincipalScopingTest.java",
          "src/test/java/neqsim/mcp/runners/McpSecurityEnforcementTest.java", "neqsim-mcp-server/test_mcp_server.py" };
      evidenceBoundary = "Profile discovery, classification, admin-gated mode changes, and principal-scoped one-shot approvals are contract-tested; deployments still require external identity, policy configuration, and accountable review";
      break;
    case "searchComponents":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_COMPONENT_CATALOG_LOOKUP";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/ComponentQuery.java",
          "src/test/java/neqsim/mcp/runners/ComponentQueryTest.java", "neqsim-mcp-server/test_mcp_server.py" };
      evidenceBoundary = "Component-name lookup, substring search, empty-query enumeration, typo handling, and no-match behavior are directly tested, including real-protocol retrieval; catalog lookup does not validate thermodynamic calculations or component-property models";
      break;
    case "queryDataCatalog":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_DATA_CATALOG_DISCOVERY";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/DataCatalogRunner.java",
          "src/test/java/neqsim/mcp/runners/DataCatalogRunnerTest.java", "neqsim-mcp-server/test_mcp_server.py" };
      evidenceBoundary = "Read-only catalog dispatch and representative component-family, EOS-model, component-property, and real-protocol catalog retrieval are tested; database contents, standards applicability, EOS accuracy, and material or design decisions are not validated by this evidence";
      break;
    case "getProgress":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_PROGRESS_RETRIEVAL";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/ProgressTracker.java",
          "src/test/java/neqsim/mcp/runners/McpEvidenceInventoryFoundationTests.java",
          "neqsim-mcp-server/test_mcp_server.py", "neqsim-mcp-server/docs/evidence/PROGRESS_RETRIEVAL_CONTRACT.md" };
      evidenceBoundary = "Active-operation discovery, point retrieval, milestone visibility, completion state, missing-operation errors, and real-protocol listActive retrieval are directly tested; this evidence does not validate the underlying calculation, cancellation, durability, deployment isolation, or plant authority";
      break;
    case "inspectApi":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_RUNTIME_API_INSPECTION";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/ApiKnowledgeRunner.java",
          "src/test/java/neqsim/mcp/runners/ApiKnowledgeRunnerTest.java",
          "neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java",
          "neqsim-mcp-server/test_inspect_api_protocol.py",
          "neqsim-mcp-server/docs/evidence/API_INSPECTION_CONTRACT.md" };
      evidenceBoundary = "Version-matched reflection is restricted to neqsim.* classes, common NeqSim process aliases, and EquipmentFactory aliases; source-level and packaged-MCP tests prove representative resolution, member filtering, source pointers, and fail-closed non-NeqSim rejection without executing the inspected method or validating engineering calculations";
      break;
    case "manageValidationProfile":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_VALIDATION_PROFILE_GOVERNANCE";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/ValidationProfileRunner.java",
          "src/main/java/neqsim/mcp/runners/IndustrialProfile.java",
          "src/test/java/neqsim/mcp/runners/ValidationProfileRunnerTest.java",
          "neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java",
          "neqsim-mcp-server/test_validation_profile_protocol.py",
          "neqsim-mcp-server/docs/evidence/VALIDATION_PROFILE_CONTRACT.md" };
      evidenceBoundary = "Built-in discovery, structural validation metadata preservation, isolated custom-profile lifecycle and recovery, equipment-standard retrieval, fail-closed mutation errors, and packaged-MCP transport are contract-tested; this does not validate standards currency, legal applicability or licensing, validator scientific correctness, deployment isolation or durability, external authorization, or plant authority";
      break;
    case "manageModel":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_MODEL_REGISTRY_LIFECYCLE";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/ModelRegistry.java",
          "src/test/java/neqsim/mcp/runners/ModelRegistryTest.java",
          "neqsim-mcp-server/test_model_registry_protocol.py", "neqsim-mcp-server/test_mcp_server.py",
          "neqsim-mcp-server/docs/evidence/MODEL_REGISTRY_CONTRACT.md" };
      evidenceBoundary = "Content-addressed registration, caller-scoped get/list/inspect, canonical process and automation handle routing, stable revisioning, fail-closed invalid or unknown requests, deletion/invalidation, source-level tenant/principal isolation, and packaged-MCP transport are contract-tested; this does not establish persistence across server restarts, distributed cache coherence, external identity or authorization correctness, numerical model accuracy, convergence, mass or energy closure, facility fidelity, plant authority, control permission, design certification, or accountable engineering approval";
      break;
    case "manageSession":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_SESSION_LIFECYCLE";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/SessionRunner.java",
          "src/test/java/neqsim/mcp/runners/SessionRunnerTest.java",
          "src/test/java/neqsim/mcp/runners/SessionRunnerContractTest.java",
          "neqsim-mcp-server/test_session_protocol.py", "neqsim-mcp-server/test_mcp_server.py",
          "neqsim-mcp-server/docs/evidence/SESSION_LIFECYCLE_CONTRACT.md" };
      evidenceBoundary = "Lifecycle contract; restart durability, distributed coherence, component or energy closure, and causal troubleshooting remain unqualified";
      break;
    case "manageState":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_LOCAL_STATE_PERSISTENCE_LIFECYCLE";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/StatePersistenceRunner.java",
          "src/test/java/neqsim/mcp/runners/StatePersistenceRunnerTest.java",
          "neqsim-mcp-server/test_state_persistence_protocol.py", "neqsim-mcp-server/test_mcp_server.py",
          "neqsim-mcp-server/docs/evidence/STATE_PERSISTENCE_CONTRACT.md" };
      evidenceBoundary = "Local canonical-definition persistence lifecycle and sandbox/path fail-closed contract; numerical replay, accuracy, conservation, distributed durability/security, facility fidelity, plant authority, and engineering approval remain unqualified";
      break;
    case "getAdjustableParameters":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_AUTOMATION_PARAMETER_DISCOVERY";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/AutomationRunner.java",
          "src/test/java/neqsim/mcp/runners/AutomationLoopRunnerTest.java",
          "neqsim-mcp-server/test_adjustable_parameters_protocol.py", "neqsim-mcp-server/test_mcp_server.py",
          "neqsim-mcp-server/docs/evidence/ADJUSTABLE_PARAMETERS_CONTRACT.md" };
      evidenceBoundary = "Canonical adjustable-parameter metadata, units/bounds, definition/handle equivalence, deterministic discovery, fail-closed inputs, and packaged transport are contract-tested; feasibility, model fidelity, convergence, conservation, optimization quality, plant authority, certification, and engineering approval remain unqualified";
      break;
    case "listSimulationUnits":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_AUTOMATION_UNIT_DISCOVERY";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/AutomationRunner.java",
          "src/main/java/neqsim/process/automation/ProcessAutomation.java",
          "src/test/java/neqsim/mcp/runners/AutomationReadContractTest.java",
          "neqsim-mcp-server/test_automation_read_protocol.py",
          "neqsim-mcp-server/docs/evidence/AUTOMATION_READ_CONTRACT.md" };
      evidenceBoundary = "Canonical solved-ProcessSystem unit discovery, unit identity/type metadata, fail-closed input handling, standard response envelopes, and packaged-MCP transport are contract-tested; this does not establish facility topology completeness, numerical process accuracy, or plant authority";
      break;
    case "listUnitVariables":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_AUTOMATION_VARIABLE_DISCOVERY";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/AutomationRunner.java",
          "src/main/java/neqsim/process/automation/ProcessAutomation.java",
          "src/test/java/neqsim/mcp/runners/AutomationReadContractTest.java",
          "neqsim-mcp-server/test_automation_read_protocol.py",
          "neqsim-mcp-server/docs/evidence/AUTOMATION_READ_CONTRACT.md" };
      evidenceBoundary = "Canonical unit-variable registry discovery, addresses, types, units, writeability/applicability metadata, fail-closed input handling, and packaged-MCP transport are contract-tested; this does not validate the engineering correctness of exposed variables or facility completeness";
      break;
    case "getSimulationVariable":
      benchmarkApplicability = "NOT_APPLICABLE_SOFTWARE_CONTRACT_AUTOMATION_VARIABLE_READ";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/AutomationRunner.java",
          "src/main/java/neqsim/process/automation/ProcessAutomation.java",
          "src/test/java/neqsim/mcp/runners/AutomationReadContractTest.java",
          "neqsim-mcp-server/test_automation_read_protocol.py",
          "neqsim-mcp-server/docs/evidence/AUTOMATION_READ_CONTRACT.md" };
      evidenceBoundary = "Addressed read routing, requested-unit handling, provenance/validation/quality-gate envelope preservation, fail-closed invalid inputs, and packaged-MCP transport are contract-tested; the returned numerical value, model fidelity, convergence adequacy, and engineering applicability are not benchmark-validated by this classification";
      break;
    case "diagnoseAutomation":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_AUTOMATION_DIAGNOSTIC_ADVISORY";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/AutomationRunner.java",
          "src/main/java/neqsim/process/automation/AutomationDiagnostics.java",
          "src/test/java/neqsim/mcp/runners/AutomationReadContractTest.java",
          "neqsim-mcp-server/test_automation_read_protocol.py",
          "neqsim-mcp-server/docs/evidence/AUTOMATION_READ_CONTRACT.md" };
      evidenceBoundary = "Structured UNIT_NOT_FOUND advisory classification, suggestions/remediation shape, process-local learning-report inclusion, fail-closed invalid inputs, and packaged-MCP transport are contract-tested; suggestions are not causal diagnosis, plant measurements, control instructions, or accountable engineering approval";
      break;
    case "getAutomationLearningReport":
      benchmarkApplicability = "NOT_APPLICABLE_NON_NUMERICAL_AUTOMATION_LEARNING_REPORT";
      evidenceSources = new String[] { "src/main/java/neqsim/mcp/runners/AutomationRunner.java",
          "src/main/java/neqsim/process/automation/AutomationDiagnostics.java",
          "src/test/java/neqsim/mcp/runners/AutomationReadContractTest.java",
          "neqsim-mcp-server/test_automation_read_protocol.py",
          "neqsim-mcp-server/docs/evidence/AUTOMATION_READ_CONTRACT.md" };
      evidenceBoundary = "Fresh-process zero-history learning-report structure, error-category/correction/recommendation containers, fail-closed input handling, and packaged-MCP transport are contract-tested; persistence across processes/restarts/tenants and learning quality are not established";
      break;
    default:
      return false;
    }

    record.addProperty("coverageStatus", "CONTRACT_TESTED");
    record.addProperty("toolSpecificTrustAvailable", false);
    record.addProperty("contractTrustAvailable", true);
    record.addProperty("benchmarkApplicability", benchmarkApplicability);
    record.addProperty("maturityLevel", "TESTED");
    record.addProperty("contractEvidenceCount", evidenceSources.length);
    record.add("contractEvidenceSources", toJsonArray(java.util.Arrays.asList(evidenceSources)));
    record.addProperty("knownLimitationCount", 0);
    record.addProperty("unsupportedConditionCount", 0);
    record.addProperty("validationCaseCount", 0);
    record.addProperty("verifiedValidationCaseCount", 0);
    record.addProperty("evidenceBoundary", evidenceBoundary);
    return true;
  }

  /** Counts validation cases that identify a concrete verification source. */
  private static int verifiedValidationCaseCount(JsonObject trust) {
    if (!trust.has("validationCases")) {
      return 0;
    }
    int count = 0;
    for (JsonElement validationCase : trust.getAsJsonArray("validationCases")) {
      if (validationCase.getAsJsonObject().has("verifiedBy")) {
        count++;
      }
    }
    return count;
  }

  /** Builds one guide descriptor. */
  private static JsonObject guide(String id, String path, String scope) {
    JsonObject guide = new JsonObject();
    guide.addProperty("id", id);
    guide.addProperty("path", path);
    guide.addProperty("scope", scope);
    return guide;
  }

  /** Returns an array size or zero when the member is absent. */
  private static int arraySize(JsonObject object, String member) {
    return object.has(member) ? object.getAsJsonArray(member).size() : 0;
  }

  /** Converts ordered values to JSON. */
  private static JsonArray toJsonArray(Iterable<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }
}
