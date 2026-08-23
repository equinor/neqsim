package neqsim.mcp.runners;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Builds the Phase 0 MCP test, guide, and limitation-evidence inventory.
 *
 * <p>
 * Source evidence counts are frozen by the repository and protocol tests. Runtime limitation coverage is derived
 * directly from {@link BenchmarkTrust}, so tools that still use the generic trust fallback remain explicit gaps rather
 * than being presented as validated.
 * </p>
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
   * @return test, guide, and limitation evidence
   */
  public static JsonObject build() {
    JsonObject inventory = new JsonObject();
    inventory.addProperty("inventoryVersion", "1.0");
    inventory.add("tests", buildTests());
    inventory.add("guides", buildGuides());
    inventory.add("knownLimitations", buildKnownLimitations());
    inventory.addProperty("advisoryBoundary",
        "Evidence discovery does not certify a calculation or replace qualified engineering review");
    inventory.addProperty("complete", false);
    inventory.addProperty("completionReason",
        "Test and guide sources are inventoried, but not every published tool has explicit benchmark and limitation metadata");
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
    guides.addProperty("guideCount", entries.size());
    guides.add("entries", entries);
    return guides;
  }

  /** Builds limitation and maturity coverage directly from BenchmarkTrust. */
  private static JsonObject buildKnownLimitations() {
    JsonObject trustReport = JsonParser.parseString(BenchmarkTrust.getTrustReport()).getAsJsonObject();
    JsonObject explicitTools = trustReport.getAsJsonObject("tools");

    Set<String> publishedTools = IndustrialProfile.getAllKnownTools();
    Set<String> genericTools = new LinkedHashSet<String>(publishedTools);
    genericTools.removeAll(explicitTools.keySet());

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
      if (trust.has("validationCases")) {
        for (JsonElement validationCase : trust.getAsJsonArray("validationCases")) {
          if (validationCase.getAsJsonObject().has("verifiedBy")) {
            verifiedValidationCaseCount++;
          }
        }
      }
      String maturity = trust.has("maturityLevel") ? trust.get("maturityLevel").getAsString() : "UNDECLARED";
      int current = maturityCounts.has(maturity) ? maturityCounts.get(maturity).getAsInt() : 0;
      maturityCounts.addProperty(maturity, current + 1);
    }

    JsonObject limitations = new JsonObject();
    limitations.addProperty("sourceTool", "getBenchmarkTrust");
    limitations.addProperty("publishedToolCount", publishedTools.size());
    limitations.addProperty("explicitTrustToolCount", explicitTools.size());
    limitations.addProperty("genericTrustToolCount", genericTools.size());
    limitations.addProperty("knownLimitationCount", limitationCount);
    limitations.addProperty("unsupportedConditionCount", unsupportedConditionCount);
    limitations.addProperty("validationCaseCount", validationCaseCount);
    limitations.addProperty("verifiedValidationCaseCount", verifiedValidationCaseCount);
    limitations.add("maturityCounts", maturityCounts);
    limitations.add("explicitTrustTools", toJsonArray(explicitTools.keySet()));
    limitations.add("genericTrustTools", toJsonArray(genericTools));
    limitations.addProperty("complete", genericTools.isEmpty());
    limitations.addProperty("gapBoundary",
        "Generic fallback means no tool-specific benchmark, accuracy, unsupported-condition, or limitation claim is available");
    limitations.addProperty("resultBoundary",
        "Per-result provenance, convergence, warnings, assumptions, and limitations remain authoritative for an executed case");
    return limitations;
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
