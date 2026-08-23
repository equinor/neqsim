package neqsim.mcp.runners;

import java.util.Arrays;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Builds the machine-readable #3153 campaign criterion and discipline maturity matrix. */
public final class McpCampaignMatrix {

  private McpCampaignMatrix() {
  }

  /**
   * Builds the complete campaign evidence matrix without claiming campaign completion.
   *
   * @return criterion traceability and engineering-discipline maturity evidence
   */
  public static JsonObject build() {
    JsonObject result = new JsonObject();
    result.addProperty("matrixVersion", "1.0");
    result.addProperty("campaignIssue", 3153);
    result.addProperty("criterionCount", 66);
    result.add("statusDefinitions", statusDefinitions());
    result.add("criteria", buildCriteria());
    result.add("disciplines", buildDisciplines());
    result.addProperty("roadmapCompletionClaim", false);
    result.addProperty("interpretationBoundary",
        "Evidence status records what current source demonstrates; PARTIAL_EVIDENCE and CONFIRMED_GAP are not validation or completion claims");
    return result;
  }

  private static JsonObject statusDefinitions() {
    JsonObject definitions = new JsonObject();
    definitions.addProperty("MERGED_EVIDENCE",
        "Merged current-tree source, tests, documentation, or campaign evidence directly supports the criterion");
    definitions.addProperty("PARTIAL_EVIDENCE",
        "Relevant capability or current-tree evidence exists, but one or more criterion requirements remain unproven, unmerged, or incomplete");
    definitions.addProperty("CONFIRMED_GAP",
        "Current evidence does not demonstrate the criterion; the gap remains explicit");
    return definitions;
  }

  private static JsonArray buildCriteria() {
    JsonArray rows = new JsonArray();
    add(rows, 0, new String[] {
        "Inventory the complete public MCP surface and evidence estate",
        "Reconcile merged foundations #2874, #2875 and #3152",
        "Establish four public synthetic acceptance scales",
        "Trace every campaign criterion to evidence or a confirmed gap",
        "Record runtime, memory, response size, tool-call, convergence, balance and report baselines",
        "Define a machine-readable capability/maturity matrix by engineering discipline"},
        new String[] {"MERGED_EVIDENCE", "MERGED_EVIDENCE", "MERGED_EVIDENCE", "PARTIAL_EVIDENCE",
            "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE"},
        new String[] {"McpEvidenceInventory; SURFACE_INVENTORY.md", "mergedFoundations; FOUNDATION_TRACEABILITY.md",
            "McpAcceptanceFixtureCatalog; ACCEPTANCE_FIXTURES.md",
            "McpCampaignMatrix; CAMPAIGN_MATRIX.md; merged-master audit still required",
            "McpAcceptanceBaselineRunner; ACCEPTANCE_BASELINES.md; numeric closure gaps remain explicit",
            "McpCampaignMatrix.disciplines; merged-master audit still required"});
    add(rows, 1, new String[] {
        "Versioned facility-description contract", "Deterministic normalized facility specification",
        "Component/equipment/tag synonym resolution", "Pre-solve topology/unit/component/model validation",
        "Canonical ProcessSystem or ProcessModel generation", "Incremental facility refinement",
        "DEXPI/P&ID coordination without parallel model", "Explicit review checkpoint for inferred facility content"},
        partial(8), evidence("SchemaCatalog/Validator/ProcessRunner", 8));
    add(rows, 2, new String[] {
        "Reusable oil-and-gas process building blocks", "Preserve topology, recycles, controls and design identity",
        "Composition, cloning, revision, comparison and replay", "Explicit unsupported equipment/connections",
        "Deterministic small, medium and large execution"}, partial(5),
        evidence("JsonProcessBuilder/ProcessModel/ModelRegistry", 5));
    add(rows, 3, new String[] {
        "Fluids and PVT calculation coverage", "Flow-assurance calculation coverage", "Core-equipment coverage",
        "Facility studies, balances, capacity and case comparison", "Governed generic static capability execution"},
        partial(5), evidence("toolCapabilities/BenchmarkTrust/McpImplementationInventory", 5));
    add(rows, 4, new String[] {
        "Structured troubleshooting case", "Description-to-testable-hypothesis conversion", "Reusable diagnostic patterns",
        "Reference/current/what-if evidence comparison", "Separate model, data, equipment, control and numerical causes",
        "Recommend safe next measurement/check", "Public known-cause synthetic fault library"},
        new String[] {"PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE",
            "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "CONFIRMED_GAP"},
        evidence("RootCauseAnalysisRunner/OperationalStudyRunner; synthetic fault library remains open", 7));
    add(rows, 5, new String[] {
        "Bounded steady/quasi-steady/dynamic orchestration", "Compose validated transient scenarios",
        "Coordinate dynamic-model implementation with #2911", "Preserve initial state, chronology and replay",
        "Separate screening from qualified dynamic studies"}, partial(5),
        evidence("DynamicRunner/#2911 ownership boundary", 5));
    add(rows, 6, new String[] {
        "Benchmark small, medium, large and stress-scale facilities", "Lifecycle, revision, caching, isolation and cancellation",
        "Avoid unnecessary full solves for retrieval", "Paged/selective large-result retrieval",
        "Compact response and deterministic retrieval after truncation", "Runtime/memory/payload regression baselines",
        "Multi-client and tenant-isolation tests"},
        new String[] {"PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE",
            "MERGED_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE"},
        evidence("ModelRegistry/ResponseSizeGuard/McpExecutionPolicy/acceptance baselines", 7));
    add(rows, 7, new String[] {
        "Typed versioned result contracts", "Summary/detail with units, provenance and validation",
        "Operator/support-engineer summaries", "Process-engineering detailed results",
        "Deterministic Markdown/JSON/CSV and visual artifacts", "Report completeness and large-result validation"},
        partial(6), evidence("ApiEnvelope/ProcessResult/ReportRunner", 6));
    add(rows, 8, new String[] {
        "Task-oriented discovery", "Complete bounded example-backed schemas", "Fix-oriented validation errors",
        "Resumable multi-step study handoffs", "STDIO and Streamable HTTP end-to-end protocol tests",
        "Synchronize NeqSim agents/skills with MCP contracts"},
        new String[] {"PARTIAL_EVIDENCE", "MERGED_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE",
            "PARTIAL_EVIDENCE", "MERGED_EVIDENCE"},
        evidence("CapabilitiesRunner/SchemaCatalog/test_mcp_server.py/tool-reference lint", 6));
    add(rows, 9, new String[] {
        "Profile, identity, tenant, audit, approval and execution security", "Correlation IDs and structured observability",
        "Malformed/adversarial/resource-exhaustion tests", "Deployment and secret/privacy evidence"},
        new String[] {"MERGED_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE"},
        evidence("SecurityRunner/McpRequestContext/McpExecutionPolicy", 4));
    add(rows, 10, new String[] {
        "Retained executed PVT/equipment/small/large/troubleshooting outputs",
        "Narrative-to-model-to-validation-to-report workflow", "Progressive reuse from calculation to facility",
        "Independent engineering validation of key results", "Full protocol/security/static/documentation release gates",
        "Final capability/maturity/benchmark/limitation matrix", "Independent current-master completion audit"},
        new String[] {"PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE",
            "PARTIAL_EVIDENCE", "PARTIAL_EVIDENCE", "CONFIRMED_GAP"},
        evidence("Phase 10 remains completion-gate work", 7));
    return rows;
  }

  private static JsonObject buildDisciplines() {
    JsonObject disciplines = new JsonObject();
    addDiscipline(disciplines, "thermodynamics-pvt", "Thermodynamics and PVT",
        new String[] {"runFlash", "runPVT", "getPhaseEnvelope", "getPropertyTable", "crossValidateModels"},
        "#2937 for generic flash internals");
    addDiscipline(disciplines, "process-simulation", "Process simulation and facility studies",
        new String[] {"runProcess", "manageModel", "getCapabilities", "runParametricStudy"},
        "#2939/#3154 for execution performance and optimization");
    addDiscipline(disciplines, "flow-assurance", "Flow assurance",
        new String[] {"runFlowAssurance", "runPipeline", "runWaterHammer", "runChemistry"},
        "#2907/#2935 for pipeline solver physics");
    addDiscipline(disciplines, "rotating-equipment", "Compressors, pumps and rotating equipment",
        new String[] {"runProcess", "runOperationalStudy", "runDynamic"},
        "#2911 for dynamic machinery behavior");
    addDiscipline(disciplines, "separation-treating", "Separation, treating and columns",
        new String[] {"runProcess", "runChemistry", "runOperationalStudy"},
        "#2936/#205 for column and reactive-absorber internals");
    addDiscipline(disciplines, "reservoir-wells", "Reservoir, wells and production technology",
        new String[] {"runReservoir", "runPipeline", "runProcess"},
        "Core reservoir/well models remain authoritative");
    addDiscipline(disciplines, "dynamics-controls", "Dynamics and controls",
        new String[] {"runDynamic", "runOperationalStudy", "runSafetySystemPerformance"},
        "#2911 owns the dynamic engine");
    addDiscipline(disciplines, "safety-integrity", "Safety, integrity and materials",
        new String[] {"runHAZOP", "runBarrierRegister", "runSafetySystemPerformance", "runMaterialsReview"},
        "Advisory only; accountable engineering remains external");
    addDiscipline(disciplines, "engineering-data", "Engineering data, diagrams and interoperability",
        new String[] {"runProcess", "validateInput", "getCapabilities"}, "#2899 owns DEXPI/P&ID semantics");
    addDiscipline(disciplines, "reporting-governance", "Reporting, lifecycle and governance",
        new String[] {"generateReport", "manageModel", "checkToolAccess", "getBenchmarkTrust"},
        "MCP-owned transport, evidence and governance layer");
    return disciplines;
  }

  private static void addDiscipline(JsonObject root, String id, String name, String[] tools, String boundary) {
    JsonObject trust = JsonParser.parseString(BenchmarkTrust.getTrustReport()).getAsJsonObject().getAsJsonObject("tools");
    int published = 0;
    int explicit = 0;
    JsonArray toolArray = new JsonArray();
    for (String tool : tools) {
      JsonObject item = new JsonObject();
      item.addProperty("tool", tool);
      boolean isPublished = IndustrialProfile.getAllKnownTools().contains(tool);
      boolean hasTrust = isPublished && trust.has(tool);
      item.addProperty("published", isPublished);
      item.addProperty("toolSpecificTrust", hasTrust);
      if (isPublished) {
        published++;
      }
      if (hasTrust) {
        explicit++;
      }
      toolArray.add(item);
    }
    JsonObject row = new JsonObject();
    row.addProperty("name", name);
    row.add("representativeTools", toolArray);
    row.addProperty("publishedRepresentativeToolCount", published);
    row.addProperty("toolSpecificTrustCount", explicit);
    row.addProperty("maturityStatus", published > 0 && explicit == published ? "TOOL_SPECIFIC_TRUST"
        : explicit == 0 ? "CONFIRMED_TRUST_GAP" : "PARTIAL_TOOL_SPECIFIC_TRUST");
    row.addProperty("ownerBoundary", boundary);
    row.addProperty("qualifiedForAccountableEngineeringApproval", false);
    root.add(id, row);
  }

  private static void add(JsonArray rows, int phase, String[] criteria, String[] statuses, String[] evidence) {
    for (int i = 0; i < criteria.length; i++) {
      JsonObject row = new JsonObject();
      row.addProperty("id", "P" + phase + "-C" + (i + 1));
      row.addProperty("phase", phase);
      row.addProperty("criterion", criteria[i]);
      row.addProperty("evidenceStatus", statuses[i]);
      row.addProperty("evidence", evidence[i]);
      rows.add(row);
    }
  }

  private static String[] partial(int count) {
    String[] values = new String[count];
    Arrays.fill(values, "PARTIAL_EVIDENCE");
    return values;
  }

  private static String[] evidence(String text, int count) {
    String[] values = new String[count];
    Arrays.fill(values, text);
    return values;
  }
}
