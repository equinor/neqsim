package neqsim.mcp.runners;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Executes the four public synthetic MCP acceptance fixtures and records bounded Phase 0 baseline evidence.
 *
 * <p>
 * This runner is an on-demand test and evidence harness, not a published MCP tool and not a second process simulator.
 * It invokes the production {@link FlashRunner} and {@link ProcessRunner} contracts, repeats every fixture once for a
 * deterministic outcome check, and records environment-qualified runtime, heap-snapshot, payload, response-guard,
 * convergence, report, and balance-evidence coverage.
 * </p>
 *
 * <p>
 * Runtime and heap figures are observations for the executing environment. They are not portable performance
 * thresholds, allocation profiles, scientific validation, design qualification, or plant authority. Missing numerical
 * balance evidence is recorded as a gap instead of being inferred from a successful solve.
 * </p>
 */
public final class McpAcceptanceBaselineRunner {

  private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();
  private static final int REPEAT_RUN_COUNT = 2;

  /** Private constructor for utility class. */
  private McpAcceptanceBaselineRunner() {
  }

  /**
   * Describes the bounded measurement contract without executing a fixture.
   *
   * @return machine-readable baseline contract
   */
  public static JsonObject describe() {
    JsonObject contract = new JsonObject();
    contract.addProperty("schemaVersion", "1.0");
    contract.addProperty("fixtureCount", 4);
    contract.addProperty("repeatRunCount", REPEAT_RUN_COUNT);
    contract.addProperty("executionMode", "ON_DEMAND_TEST_HARNESS");
    contract.addProperty("executionEvidenceStatus", "CONTRACT_DEFINED_EXACT_HEAD_EXECUTION_REQUIRED");
    contract.addProperty("evidenceDocument", "neqsim-mcp-server/docs/ACCEPTANCE_BASELINES.md");
    contract.add("measurementGroups", toJsonArray(new String[] { "environment", "runtime", "heapSnapshot",
        "payloadAndGuard", "convergence", "determinism", "reportEvidence", "balanceEvidence" }));
    contract.addProperty("canonicalExecution",
        "Production FlashRunner and ProcessRunner execute the McpAcceptanceFixtureCatalog inputs");
    contract.addProperty("performanceQualification", false);
    contract.addProperty("scientificValidationComplete", false);
    contract.addProperty("boundary",
        "Environment-qualified observations only; no portable performance threshold, allocation profile, scientific validation, design certification, optimization claim, or plant authority");
    return contract;
  }

  /**
   * Executes every frozen fixture twice and records bounded baseline evidence.
   *
   * @return exact-run baseline report
   */
  public static JsonObject run() {
    JsonObject baseline = describe();
    baseline.addProperty("executionEvidenceStatus", "EXECUTED_CURRENT_RUNTIME");
    baseline.add("environment", buildEnvironment());
    baseline.addProperty("responseGuardLimitBytes", ResponseSizeGuard.getMaxBytes());

    JsonArray measurements = new JsonArray();
    JsonArray fixtures = McpAcceptanceFixtureCatalog.build().getAsJsonArray("fixtures");
    int successCount = 0;
    int convergedCount = 0;
    int deterministicCount = 0;
    int guardTriggeredCount = 0;
    int explicitBalanceGapCount = 0;
    int balanceEvidencePresentCount = 0;
    for (JsonElement fixtureElement : fixtures) {
      JsonObject measurement = measureFixture(fixtureElement.getAsJsonObject());
      measurements.add(measurement);
      if (measurement.get("successful").getAsBoolean()) {
        successCount++;
      }
      if (measurement.getAsJsonObject("convergence").get("converged").getAsBoolean()) {
        convergedCount++;
      }
      if (measurement.getAsJsonObject("determinism").get("stableOutcomeMatch").getAsBoolean()) {
        deterministicCount++;
      }
      if (measurement.getAsJsonObject("payloadAndGuard").get("guardTriggered").getAsBoolean()) {
        guardTriggeredCount++;
      }
      String balanceStatus = measurement.getAsJsonObject("balanceEvidence").get("status").getAsString();
      if ("RESPONSE_EVIDENCE_PRESENT".equals(balanceStatus)) {
        balanceEvidencePresentCount++;
      } else if ("GAP_NO_NUMERIC_CLOSURE_IN_MCP_RESPONSE".equals(balanceStatus)) {
        explicitBalanceGapCount++;
      }
    }
    baseline.add("measurements", measurements);

    JsonObject summary = new JsonObject();
    summary.addProperty("fixtureCount", fixtures.size());
    summary.addProperty("toolExecutionCount", fixtures.size() * REPEAT_RUN_COUNT);
    summary.addProperty("successCount", successCount);
    summary.addProperty("convergedCount", convergedCount);
    summary.addProperty("deterministicCount", deterministicCount);
    summary.addProperty("guardTriggeredCount", guardTriggeredCount);
    summary.addProperty("balanceEvidencePresentCount", balanceEvidencePresentCount);
    summary.addProperty("explicitBalanceGapCount", explicitBalanceGapCount);
    summary.addProperty("status",
        successCount == fixtures.size() && convergedCount == fixtures.size() && deterministicCount == fixtures.size()
            ? (explicitBalanceGapCount > 0 ? "EXECUTION_COMPLETE_WITH_EXPLICIT_GAPS" : "EXECUTION_COMPLETE")
            : "EXECUTION_FAILED_ACCEPTANCE");
    summary.addProperty("remainingBoundary",
        "Close explicit numerical balance/report gaps where the canonical model exposes defensible evidence; keep process performance with #2939 and optimization fidelity with #3154");
    baseline.add("summary", summary);
    return baseline;
  }

  /** Executes and measures one catalog fixture. */
  private static JsonObject measureFixture(JsonObject fixture) {
    String fixtureId = fixture.get("id").getAsString();
    String toolName = fixture.get("executionTool").getAsString();
    String input = GSON.toJson(fixture.get("input"));
    long heapBefore = usedHeapBytes();
    Observation first = execute(toolName, input);
    long heapAfterFirst = usedHeapBytes();
    Observation repeat = execute(toolName, input);
    long heapAfterRepeat = usedHeapBytes();

    JsonObject result = new JsonObject();
    result.addProperty("fixtureId", fixtureId);
    result.addProperty("scale", fixture.get("scale").getAsString());
    result.addProperty("executionTool", toolName);
    result.addProperty("modelKind", fixture.get("modelKind").getAsString());
    result.addProperty("declaredAreaCount", fixture.get("areaCount").getAsInt());
    result.addProperty("declaredUnitCount", fixture.get("unitCount").getAsInt());
    result.addProperty("toolExecutionCount", REPEAT_RUN_COUNT);
    result.addProperty("successful", isSuccessful(first.response) && isSuccessful(repeat.response));

    JsonObject runtime = new JsonObject();
    runtime.addProperty("firstRunMillis", first.elapsedMillis);
    runtime.addProperty("repeatRunMillis", repeat.elapsedMillis);
    runtime.addProperty("firstProvenanceComputationTimeMillis", provenanceComputationTime(first.response));
    runtime.addProperty("repeatProvenanceComputationTimeMillis", provenanceComputationTime(repeat.response));
    runtime.addProperty("qualification", "OBSERVED_CURRENT_RUNTIME_NOT_A_PORTABLE_THRESHOLD");
    result.add("runtime", runtime);

    JsonObject heap = new JsonObject();
    heap.addProperty("beforeBytes", heapBefore);
    heap.addProperty("afterFirstRunBytes", heapAfterFirst);
    heap.addProperty("afterRepeatRunBytes", heapAfterRepeat);
    heap.addProperty("firstRunDeltaBytes", heapAfterFirst - heapBefore);
    heap.addProperty("repeatRunDeltaBytes", heapAfterRepeat - heapAfterFirst);
    heap.addProperty("measurementMaturity", "JVM_USED_HEAP_SNAPSHOT_PROXY");
    heap.addProperty("boundary",
        "Snapshot deltas include JVM allocation, retention and garbage-collection effects; they are not allocation or peak-memory profiles");
    result.add("heapSnapshot", heap);

    JsonObject guarded = first.response.deepCopy();
    int rawBytes = serializedSize(first.response);
    boolean guardTriggered = ResponseSizeGuard.enforce(guarded, toolName);
    int guardedBytes = serializedSize(guarded);
    int guardLimit = ResponseSizeGuard.getMaxBytes();
    JsonObject payload = new JsonObject();
    payload.addProperty("rawResponseBytes", rawBytes);
    payload.addProperty("guardedResponseBytes", guardedBytes);
    payload.addProperty("guardLimitBytes", guardLimit);
    payload.addProperty("guardTriggered", guardTriggered);
    payload.addProperty("guardedWithinLimit", guardLimit <= 0 || guardedBytes <= guardLimit);
    payload.addProperty("selectiveRetrievalGuidancePresent", hasRetrievalGuidance(guarded));
    payload.addProperty("transportBoundary",
        "Raw runner payload is measured before the shared guard; guarded bytes represent the bounded agent-facing form");
    result.add("payloadAndGuard", payload);

    JsonObject convergence = new JsonObject();
    convergence.addProperty("declared", hasDeclaredConvergence(first.response));
    convergence.addProperty("converged", isConverged(first.response));
    convergence.addProperty("warningCount", arraySize(first.response, "warnings"));
    convergence.addProperty("validationPresent", first.response.has("validation"));
    convergence.addProperty("qualityGateStatus", qualityGateStatus(first.response));
    result.add("convergence", convergence);

    String firstFingerprint = stableOutcomeFingerprint(first.response);
    String repeatFingerprint = stableOutcomeFingerprint(repeat.response);
    JsonObject determinism = new JsonObject();
    determinism.addProperty("stableOutcomeMatch", firstFingerprint.equals(repeatFingerprint));
    determinism.addProperty("firstFingerprintSha256", firstFingerprint);
    determinism.addProperty("repeatFingerprintSha256", repeatFingerprint);
    determinism.addProperty("excludedMetadata",
        "timestamps, computation times, generated-at fields, calculation identifiers and correlation identifiers");
    result.add("determinism", determinism);

    JsonObject report = buildReportEvidence(first.response, toolName);
    result.add("reportEvidence", report);
    result.add("balanceEvidence", buildBalanceEvidence(first.response, toolName));
    result.addProperty("scientificValidationStatus", "NOT_ESTABLISHED_BY_PHASE0_EXECUTION_HARNESS");
    return result;
  }

  /** Executes one production runner call and records elapsed wall time. */
  private static Observation execute(String toolName, String input) {
    long started = System.nanoTime();
    String responseText = "runFlash".equals(toolName) ? FlashRunner.run(input) : ProcessRunner.run(input);
    double elapsedMillis = (System.nanoTime() - started) / 1.0e6;
    JsonObject response = JsonParser.parseString(responseText).getAsJsonObject();
    return new Observation(response, elapsedMillis);
  }

  /** Builds environment identity for interpreting non-portable measurements. */
  private static JsonObject buildEnvironment() {
    Runtime runtime = Runtime.getRuntime();
    JsonObject environment = new JsonObject();
    environment.addProperty("javaVersion", System.getProperty("java.version", "unknown"));
    environment.addProperty("javaVendor", System.getProperty("java.vendor", "unknown"));
    environment.addProperty("vmName", System.getProperty("java.vm.name", "unknown"));
    environment.addProperty("osName", System.getProperty("os.name", "unknown"));
    environment.addProperty("osArch", System.getProperty("os.arch", "unknown"));
    environment.addProperty("availableProcessors", runtime.availableProcessors());
    environment.addProperty("maxHeapBytes", runtime.maxMemory());
    environment.addProperty("identityBoundary",
        "Runtime and heap observations are comparable only when environment and harness inputs are equivalent");
    return environment;
  }

  /** Builds structural report coverage without claiming subjective usefulness. */
  private static JsonObject buildReportEvidence(JsonObject response, String toolName) {
    JsonElement reportElement = findReport(response);
    JsonObject evidence = new JsonObject();
    evidence.addProperty("reportPresent", reportElement != null);
    evidence.addProperty("reportBytes", reportElement == null ? 0 : serializedSize(reportElement));
    evidence.addProperty("canonicalReplayPresent", response.has("processDefinition"));
    evidence.addProperty("operatorSummaryPresent", response.has("message"));
    evidence.addProperty("status", reportElement != null ? "STRUCTURED_REPORT_PRESENT"
        : ("runFlash".equals(toolName) ? "NOT_APPLICABLE_FLASH_RESULT_IS_STRUCTURED_DATA" : "GAP_NO_REPORT"));
    evidence.addProperty("usefulnessBoundary",
        "Presence and size are structural evidence only; engineering usefulness requires content review and case-specific validation");
    return evidence;
  }

  /** Builds explicit numerical balance coverage and never infers closure from success. */
  private static JsonObject buildBalanceEvidence(JsonObject response, String toolName) {
    JsonObject evidence = new JsonObject();
    if ("runFlash".equals(toolName)) {
      evidence.addProperty("status", "NOT_APPLICABLE_SINGLE_EQUILIBRIUM_CALCULATION");
      evidence.add("responsePaths", new JsonArray());
      evidence.addProperty("boundary",
          "The flash fixture is checked through convergence and thermodynamic result provenance, not a process balance");
      return evidence;
    }

    List<String> paths = new ArrayList<String>();
    collectBalanceEvidencePaths(response, "$", paths);
    evidence.addProperty("status",
        paths.isEmpty() ? "GAP_NO_NUMERIC_CLOSURE_IN_MCP_RESPONSE" : "RESPONSE_EVIDENCE_PRESENT");
    evidence.add("responsePaths", toJsonArray(paths));
    evidence.addProperty("boundary", paths.isEmpty()
        ? "A successful process solve does not prove mass, component or energy closure; the MCP response exposes no explicit numerical closure field for this fixture"
        : "Listed paths show response-level balance evidence; their values and tolerances still require engineering review");
    return evidence;
  }

  /** Recursively finds explicitly named mass, component, or energy balance members. */
  private static void collectBalanceEvidencePaths(JsonElement element, String path, List<String> paths) {
    if (element == null || element.isJsonNull()) {
      return;
    }
    if (element.isJsonObject()) {
      for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
        String normalized = entry.getKey().replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
        String childPath = path + "." + entry.getKey();
        if (normalized.contains("massbalance") || normalized.contains("massclosure")
            || normalized.contains("componentbalance") || normalized.contains("energybalance")) {
          paths.add(childPath);
        }
        collectBalanceEvidencePaths(entry.getValue(), childPath, paths);
      }
    } else if (element.isJsonArray()) {
      JsonArray array = element.getAsJsonArray();
      for (int index = 0; index < array.size(); index++) {
        collectBalanceEvidencePaths(array.get(index), path + "[" + index + "]", paths);
      }
    }
  }

  /** Returns a stable fingerprint after removing run-identity metadata and sorting object keys. */
  private static String stableOutcomeFingerprint(JsonObject response) {
    JsonElement normalized = normalizeForFingerprint(response);
    return sha256(GSON.toJson(normalized));
  }

  /** Recursively removes volatile metadata and sorts JSON object keys. */
  private static JsonElement normalizeForFingerprint(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return JsonNull.INSTANCE;
    }
    if (element.isJsonArray()) {
      JsonArray normalized = new JsonArray();
      for (JsonElement item : element.getAsJsonArray()) {
        normalized.add(normalizeForFingerprint(item));
      }
      return normalized;
    }
    if (element.isJsonObject()) {
      JsonObject normalized = new JsonObject();
      Map<String, JsonElement> sorted = new TreeMap<String, JsonElement>();
      for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
        if (!isVolatileMetadata(entry.getKey())) {
          sorted.put(entry.getKey(), entry.getValue());
        }
      }
      for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
        normalized.add(entry.getKey(), normalizeForFingerprint(entry.getValue()));
      }
      return normalized;
    }
    return JsonParser.parseString(element.toString());
  }

  /** Identifies temporal and correlation metadata excluded from deterministic engineering output comparison. */
  private static boolean isVolatileMetadata(String fieldName) {
    String normalized = fieldName.replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
    return "timestamp".equals(normalized) || "generatedat".equals(normalized) || "computationtimems".equals(normalized)
        || "calculationidentifier".equals(normalized) || "calculationid".equals(normalized)
        || "correlationid".equals(normalized);
  }

  /** Returns whether a response reports success. */
  private static boolean isSuccessful(JsonObject response) {
    return response.has("status") && "success".equals(response.get("status").getAsString());
  }

  /** Returns whether response provenance explicitly declares convergence. */
  private static boolean hasDeclaredConvergence(JsonObject response) {
    return response.has("provenance") && response.get("provenance").isJsonObject()
        && response.getAsJsonObject("provenance").has("converged");
  }

  /** Returns convergence only from explicit provenance, never from status alone. */
  private static boolean isConverged(JsonObject response) {
    return hasDeclaredConvergence(response) && response.getAsJsonObject("provenance").get("converged").getAsBoolean();
  }

  /** Returns the runner-reported computation time, or -1 when absent. */
  private static long provenanceComputationTime(JsonObject response) {
    if (response.has("provenance") && response.get("provenance").isJsonObject()
        && response.getAsJsonObject("provenance").has("computationTimeMs")) {
      return response.getAsJsonObject("provenance").get("computationTimeMs").getAsLong();
    }
    return -1L;
  }

  /** Returns quality-gate status or an explicit absence marker. */
  private static String qualityGateStatus(JsonObject response) {
    if (response.has("qualityGate") && response.get("qualityGate").isJsonObject()
        && response.getAsJsonObject("qualityGate").has("status")) {
      return response.getAsJsonObject("qualityGate").get("status").getAsString();
    }
    return "NOT_DECLARED";
  }

  /** Finds a top-level or canonical data-block report. */
  private static JsonElement findReport(JsonObject response) {
    if (response.has("report")) {
      return response.get("report");
    }
    if (response.has("data") && response.get("data").isJsonObject() && response.getAsJsonObject("data").has("report")) {
      return response.getAsJsonObject("data").get("report");
    }
    return null;
  }

  /** Returns whether a guarded response explains selective retrieval. */
  private static boolean hasRetrievalGuidance(JsonObject guarded) {
    return guarded.has("truncation") && guarded.get("truncation").isJsonObject()
        && guarded.getAsJsonObject("truncation").has("howToRetrieve");
  }

  /** Returns an array member size or zero. */
  private static int arraySize(JsonObject object, String field) {
    return object.has(field) && object.get(field).isJsonArray() ? object.getAsJsonArray(field).size() : 0;
  }

  /** Returns current used-heap proxy without forcing garbage collection. */
  private static long usedHeapBytes() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  /** Returns serialized UTF-8 byte size. */
  private static int serializedSize(JsonElement element) {
    return GSON.toJson(element).getBytes(StandardCharsets.UTF_8).length;
  }

  /** Calculates a SHA-256 hex digest. */
  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      char[] hex = new char[bytes.length * 2];
      char[] digits = "0123456789abcdef".toCharArray();
      for (int index = 0; index < bytes.length; index++) {
        int unsigned = bytes[index] & 0xff;
        hex[index * 2] = digits[unsigned >>> 4];
        hex[index * 2 + 1] = digits[unsigned & 0x0f];
      }
      return new String(hex);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  /** Converts strings to JSON. */
  private static JsonArray toJsonArray(String[] values) {
    List<String> ordered = new ArrayList<String>();
    Collections.addAll(ordered, values);
    return toJsonArray(ordered);
  }

  /** Converts ordered strings to JSON. */
  private static JsonArray toJsonArray(Iterable<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /** One production-runner observation. */
  private static final class Observation {
    private final JsonObject response;
    private final double elapsedMillis;

    private Observation(JsonObject response, double elapsedMillis) {
      this.response = response;
      this.elapsedMillis = elapsedMillis;
    }
  }
}
