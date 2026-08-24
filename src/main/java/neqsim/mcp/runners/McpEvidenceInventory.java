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

  private static final int JAVA_TEST_CLASS_COUNT = 67;
  private static final int PROTOCOL_SCENARIO_COUNT = 94;

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
    inventory.addProperty("inventoryVersion", "1.7");
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
    tests.addProperty("sourceCountContract",
        "CapabilitiesRunnerTest and the packaged MCP protocol suite freeze these source inventories");
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
      } else if ("getCapabilities".equals(toolName) || "getSchema".equals(toolName)
          || "getExample".equals(toolName)) {
        record.addProperty("coverageStatus", "CONTRACT_TESTED");
        record.addProperty("toolSpecificTrustAvailable", false);
        record.addProperty("contractTrustAvailable", true);
        record.addProperty("maturityLevel", "TESTED");
        record.addProperty("knownLimitationCount", 0);
        record.addProperty("unsupportedConditionCount", 0);
        record.addProperty("validationCaseCount", 0);
        record.addProperty("verifiedValidationCaseCount", 0);
        if ("getCapabilities".equals(toolName)) {
          record.addProperty("benchmarkApplicability", "NOT_APPLICABLE_NON_NUMERICAL_DISCOVERY");
          record.addProperty("contractEvidenceCount", 4);
          record.addProperty("evidenceBoundary",
              "CapabilitiesRunnerTest, McpToolSurfaceContractTest, ResponseSizeGuardTest, and the packaged MCP protocol verify discovery-contract fidelity; this is not scientific validation of advertised calculations");
        } else if ("getSchema".equals(toolName)) {
          record.addProperty("benchmarkApplicability", "NOT_APPLICABLE_NON_NUMERICAL_SCHEMA_CATALOG");
          record.addProperty("contractEvidenceCount", 3);
          record.addProperty("evidenceBoundary",
              "SchemaCatalog source, CapabilitiesRunnerTest catalog resolution, and the packaged MCP protocol verify all 142 canonical input/output schema resources; schema availability does not validate the calculations described by those schemas");
        } else {
          record.addProperty("benchmarkApplicability", "NOT_APPLICABLE_NON_NUMERICAL_EXAMPLE_CATALOG");
          record.addProperty("contractEvidenceCount", 3);
          record.addProperty("evidenceBoundary",
              "ExampleCatalog source, CapabilitiesRunnerTest example resolution, and the packaged MCP protocol verify all 114 catalog examples; example availability does not establish scientific accuracy or fitness for a facility decision");
        }
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
            "No tool-specific BenchmarkTrust entry exists; the generic TESTED fallback is compatibility metadata, not benchmark, accuracy, applicability, or no-limitations evidence");
        confirmedGapRecordCount++;
      }
      coverageRecords.add(toolName, record);
    }

    JsonObject coverageDefinitions = new JsonObject();
    coverageDefinitions.addProperty("EXPLICIT_TRUST",
        "Tool-specific BenchmarkTrust metadata exists; use its declared maturity, validation cases, accuracy bounds, and limitations");
    coverageDefinitions.addProperty("CONTRACT_TESTED",
        "Non-numerical MCP contract behavior has direct source, contract-test, and real-protocol evidence; no engineering accuracy benchmark is applicable or implied");
    coverageDefinitions.addProperty("CONFIRMED_GAP",
        "No tool-specific BenchmarkTrust entry or bounded non-numerical contract evidence closes the gap; generic fallback maturity must not be interpreted as benchmark validation");

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
    limitations.addProperty("complete", genericTools.isEmpty());
    limitations.addProperty("gapBoundary",
        "Every published tool has an explicit coverage record; getCapabilities, getSchema, and getExample are contract-tested without numerical benchmark claims, while CONFIRMED_GAP identifies the remaining missing tool-specific trust evidence");
    limitations.addProperty("resultBoundary",
        "Per-result provenance, convergence, warnings, assumptions, and limitations remain authoritative for an executed case");
    return limitations;
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
