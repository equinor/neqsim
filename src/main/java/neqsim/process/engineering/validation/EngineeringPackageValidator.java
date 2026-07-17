package neqsim.process.engineering.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;

/** Structural, referential and cross-artifact validation for compiled engineering packages. */
public final class EngineeringPackageValidator {
  private static final Set<String> CONTROLLED_UNITS = new HashSet<String>(Arrays.asList("1", "%", "bara", "barg", "bar",
      "pa", "kpa", "mpa", "c", "k", "f", "kg/hr", "kg/s", "kg/day", "mol/s", "kmol/hr", "sm3/day", "msm3/day", "nm3/hr",
      "m3/hr", "m3/s", "kg/m3", "m/s", "bar/km", "w", "kw", "mw", "j/kg", "kj/kg", "m", "m2", "m3", "mm", "cm", "in",
      "inch", "in2", "s", "min", "hr", "year", "fraction", "cv", "rpm", "flag"));

  private EngineeringPackageValidator() {
  }

  /** Validates stable identities, referential integrity and graph-level engineering invariants. */
  public static EngineeringPackageValidationReport validateGraph(EngineeringGraph graph) {
    EngineeringPackageValidationReport report = new EngineeringPackageValidationReport();
    String artifact = "engineering-model.json";
    if (graph == null) {
      report.addError("ENG-GRAPH-001", artifact, "", "Engineering graph is missing");
      return report;
    }
    int projectNodes = 0;
    Set<String> identities = new HashSet<String>();
    for (Map.Entry<String, EngineeringNode> entry : graph.getNodes().entrySet()) {
      EngineeringNode node = entry.getValue();
      String path = "/nodes/" + entry.getKey();
      if (!entry.getKey().equals(node.getId())) {
        report.addError("ENG-GRAPH-002", artifact, path, "Node map key does not match the node id");
      }
      String expectedId = EngineeringIds.nodeId(node.getKind(), node.getExternalKey());
      if (!expectedId.equals(node.getId())) {
        report.addError("ENG-GRAPH-003", artifact, path + "/id", "Node id is not canonical; expected " + expectedId);
      }
      String identity = node.getKind().name() + ":" + EngineeringIds.canonical(node.getExternalKey());
      if (!identities.add(identity)) {
        report.addError("ENG-GRAPH-004", artifact, path + "/externalKey",
            "Duplicate canonical external identity " + identity);
      }
      if (node.getKind() == EngineeringNode.Kind.PROJECT) {
        projectNodes++;
        if (!graph.getProjectId().equals(node.getExternalKey())) {
          report.addError("ENG-GRAPH-005", artifact, path, "Project node external key does not match graph projectId");
        }
      }
      validateCalculationUnit(node, report, artifact, path);
    }
    if (projectNodes != 1) {
      report.addError("ENG-GRAPH-006", artifact, "/nodes",
          "Engineering graph must contain exactly one PROJECT node; found " + projectNodes);
    }
    for (Map.Entry<String, EngineeringEdge> entry : graph.getEdges().entrySet()) {
      EngineeringEdge edge = entry.getValue();
      String path = "/edges/" + entry.getKey();
      if (!entry.getKey().equals(edge.getId())) {
        report.addError("ENG-GRAPH-007", artifact, path, "Edge map key does not match the edge id");
      }
      if (graph.getNode(edge.getSourceId()) == null) {
        report.addError("ENG-GRAPH-008", artifact, path + "/sourceId",
            "Edge source does not exist: " + edge.getSourceId());
      }
      if (graph.getNode(edge.getTargetId()) == null) {
        report.addError("ENG-GRAPH-009", artifact, path + "/targetId",
            "Edge target does not exist: " + edge.getTargetId());
      }
      String expectedId = EngineeringIds.edgeId(edge.getKind(), edge.getSourceId(), edge.getTargetId(), edge.getRole());
      if (!expectedId.equals(edge.getId())) {
        report.addError("ENG-GRAPH-010", artifact, path + "/id", "Edge id is not canonical; expected " + expectedId);
      }
    }
    report.addAll(EngineeringTopologyValidator.validate(graph));
    return report;
  }

  /** Validates the declared version and required top-level structure of one JSON artifact. */
  public static EngineeringPackageValidationReport validateArtifact(String artifactName, String json) {
    EngineeringPackageValidationReport report = new EngineeringPackageValidationReport();
    EngineeringSchemaCatalog.Definition definition = EngineeringSchemaCatalog.forArtifact(artifactName);
    if (definition == null) {
      report.addError("ENG-SCHEMA-001", artifactName, "", "Artifact has no registered engineering schema");
      return report;
    }
    JsonObject root = parseObject(artifactName, json, report);
    if (root == null) {
      return report;
    }
    requireString(root, "schemaVersion", artifactName, report);
    if (root.has("schemaVersion") && root.get("schemaVersion").isJsonPrimitive()
        && !definition.getSchemaVersion().equals(root.get("schemaVersion").getAsString())) {
      report.addError("ENG-SCHEMA-002", artifactName, "/schemaVersion",
          "Expected " + definition.getSchemaVersion() + " but found " + root.get("schemaVersion").getAsString());
    }
    if (!root.has("schemaUri")) {
      report.addWarning("ENG-SCHEMA-003", artifactName, "/schemaUri",
          "Schema URI is missing; readers must resolve the version through the package catalog");
    } else if (!root.get("schemaUri").isJsonPrimitive()
        || !definition.getSchemaUri().equals(root.get("schemaUri").getAsString())) {
      report.addError("ENG-SCHEMA-004", artifactName, "/schemaUri", "Schema URI does not match the registered schema");
    }
    if ("engineering-model.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireArray(root, "nodes", artifactName, report);
      requireArray(root, "edges", artifactName, report);
      requireString(root, "fingerprint", artifactName, report);
    } else if ("engineering-connectivity.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "graphFingerprint", artifactName, report);
      requireObject(root, "summary", artifactName, report);
      requireArray(root, "nodes", artifactName, report);
      requireArray(root, "edges", artifactName, report);
    } else if ("engineering-calculation-dag.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireArray(root, "topologicalOrder", artifactName, report);
      requireArray(root, "nodes", artifactName, report);
      requireArray(root, "edges", artifactName, report);
      requireObject(root, "summary", artifactName, report);
    } else if ("engineering-design-case-matrix.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireArray(root, "cases", artifactName, report);
      requireArray(root, "metrics", artifactName, report);
      requireArray(root, "executions", artifactName, report);
      requireArray(root, "governingValues", artifactName, report);
      requireObject(root, "summary", artifactName, report);
    } else if ("engineering-discipline-package.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "graphFingerprint", artifactName, report);
      requireArray(root, "deliverables", artifactName, report);
      requireArray(root, "equipmentDatasheets", artifactName, report);
      requireArray(root, "lineDatasheets", artifactName, report);
      requireArray(root, "instrumentAndSafeguardingRequirements", artifactName, report);
      requireObject(root, "disciplineSummary", artifactName, report);
      requireString(root, "governance", artifactName, report);
    } else if ("engineering-approval-ledger.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "graphFingerprint", artifactName, report);
      requireString(root, "baselineRevision", artifactName, report);
      requireArray(root, "records", artifactName, report);
      requireArray(root, "effectiveStates", artifactName, report);
      requireArray(root, "revisionImpactedNodeIds", artifactName, report);
      requireObject(root, "summary", artifactName, report);
    } else if ("engineering-dexpi-roundtrip-report.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "graphFingerprint", artifactName, report);
      requireString(root, "qualificationScope", artifactName, report);
      requireString(root, "status", artifactName, report);
      requireArray(root, "formats", artifactName, report);
      requireString(root, "commercialCaeStatus", artifactName, report);
      requireString(root, "commercialCaeEvidenceRequired", artifactName, report);
    } else if ("engineering-automation-plan.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "graphFingerprint", artifactName, report);
      requireArray(root, "studies", artifactName, report);
      requireArray(root, "availableEngines", artifactName, report);
      requireObject(root, "executionBoundary", artifactName, report);
      requireObject(root, "summary", artifactName, report);
    } else if (artifactName.endsWith("-register.json")) {
      validateRegisterStructure(root, artifactName, report);
    } else if ("design-case-envelope.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "status", artifactName, report);
      requireArray(root, "caseResults", artifactName, report);
      requireArray(root, "governingValues", artifactName, report);
    } else if ("engineering-compiler-manifest.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "graphFingerprint", artifactName, report);
      requireArray(root, "artifacts", artifactName, report);
      requireArray(root, "schemas", artifactName, report);
    } else if ("engineering-revision-diff.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "fromRevision", artifactName, report);
      requireString(root, "toRevision", artifactName, report);
      requireArray(root, "addedNodeIds", artifactName, report);
      requireArray(root, "removedNodeIds", artifactName, report);
      requireArray(root, "modifiedNodeIds", artifactName, report);
      requireArray(root, "impactedNodeIds", artifactName, report);
    } else if ("engineering-validation-report.json".equals(artifactName)) {
      requireArray(root, "findings", artifactName, report);
      if (!root.has("valid") || !root.get("valid").isJsonPrimitive()) {
        report.addError("ENG-SCHEMA-011", artifactName, "/valid", "Required boolean property is missing");
      }
    } else if ("engineering-production-readiness.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "maturityLevel", artifactName, report);
      requireObject(root, "gates", artifactName, report);
      requireArray(root, "failedGates", artifactName, report);
      requireObject(root, "safetyLifecycle", artifactName, report);
      requireObject(root, "evidenceBasis", artifactName, report);
      requireFalse(root, "fitnessForConstruction", artifactName, report);
      requireFalse(root, "finalEngineeringApprovalGranted", artifactName, report);
    } else if ("engineering-qualification-plan.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireArray(root, "executedMethods", artifactName, report);
      requireArray(root, "methodQualificationMatrix", artifactName, report);
      requireArray(root, "actions", artifactName, report);
      requireString(root, "readinessLevel", artifactName, report);
      requireFalse(root, "fitnessForConstruction", artifactName, report);
    } else if ("engineering-discipline-orchestration.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "revision", artifactName, report);
      requireString(root, "status", artifactName, report);
      requireString(root, "governance", artifactName, report);
    } else if ("engineering-vertical-slice-qualification.json".equals(artifactName)) {
      requireString(root, "projectId", artifactName, report);
      requireString(root, "projectRevision", artifactName, report);
      requireString(root, "policyId", artifactName, report);
      requireString(root, "policyRevision", artifactName, report);
      requireObject(root, "gates", artifactName, report);
      requireArray(root, "failedGates", artifactName, report);
      requireFalse(root, "fitnessForConstruction", artifactName, report);
      requireFalse(root, "finalEngineeringApprovalGranted", artifactName, report);
    }
    return report;
  }

  private static void requireFalse(JsonObject root, String property, String artifact,
      EngineeringPackageValidationReport report) {
    if (!root.has(property) || !root.get(property).isJsonPrimitive()
        || !root.get(property).getAsJsonPrimitive().isBoolean() || root.get(property).getAsBoolean()) {
      report.addError("ENG-SCHEMA-012", artifact, "/" + property,
          "Required governance property must be the boolean value false");
    }
  }

  /** Validates a complete package, including manifest inventory and cross-document references. */
  public static EngineeringPackageValidationReport validatePackage(Path packageDirectory) throws IOException {
    EngineeringPackageValidationReport report = new EngineeringPackageValidationReport();
    if (packageDirectory == null || !Files.isDirectory(packageDirectory)) {
      report.addError("ENG-PACKAGE-001", "engineering-package", "", "Engineering package directory does not exist");
      return report;
    }
    for (EngineeringSchemaCatalog.Definition definition : EngineeringSchemaCatalog.getDefinitions()) {
      Path artifact = packageDirectory.resolve(definition.getArtifactName());
      if ("engineering-revision-diff.json".equals(definition.getArtifactName()) && !Files.exists(artifact)) {
        continue;
      }
      if (!Files.isRegularFile(artifact)) {
        report.addError("ENG-PACKAGE-002", definition.getArtifactName(), "", "Required artifact is missing");
        continue;
      }
      report.addAll(validateArtifact(definition.getArtifactName(), read(artifact)));
    }
    Path graphPath = packageDirectory.resolve("engineering-model.json");
    if (!Files.isRegularFile(graphPath)) {
      return report;
    }
    EngineeringGraph graph;
    try {
      graph = EngineeringGraph.fromJson(read(graphPath));
      report.addAll(validateGraph(graph));
    } catch (RuntimeException ex) {
      report.addError("ENG-PACKAGE-003", "engineering-model.json", "",
          "Canonical graph cannot be reconstructed: " + ex.getMessage());
      return report;
    }
    validateRegister(packageDirectory, "equipment-register.json", "equipmentTag", EngineeringNode.Kind.EQUIPMENT, graph,
        report);
    validateRegister(packageDirectory, "line-register.json", "lineTag", EngineeringNode.Kind.LINE, graph, report);
    validateInstrumentRegister(packageDirectory, graph, report);
    validateEnvelope(packageDirectory, graph, report);
    validateConnectivity(packageDirectory, graph, report);
    validateCalculationDag(packageDirectory, graph, report);
    validateDesignCaseMatrix(packageDirectory, graph, report);
    validateDisciplinePackage(packageDirectory, graph, report);
    validateApprovalLedger(packageDirectory, graph, report);
    validateDexpiRoundTrip(packageDirectory, graph, report);
    validateAutomationPlan(packageDirectory, graph, report);
    validateManifest(packageDirectory, graph, report);
    validateSchemaFiles(packageDirectory, report);
    return report;
  }

  private static void validateRegisterStructure(JsonObject root, String artifact,
      EngineeringPackageValidationReport report) {
    requireArray(root, "rows", artifact, report);
    if (!root.has("rowCount") || !root.get("rowCount").isJsonPrimitive()
        || !root.get("rowCount").getAsJsonPrimitive().isNumber()) {
      report.addError("ENG-SCHEMA-005", artifact, "/rowCount", "Required integer property is missing");
      return;
    }
    if (root.has("rows") && root.get("rows").isJsonArray()
        && root.get("rowCount").getAsInt() != root.getAsJsonArray("rows").size()) {
      report.addError("ENG-SCHEMA-006", artifact, "/rowCount", "rowCount does not match rows array length");
    }
  }

  private static void validateRegister(Path directory, String artifact, String tagField,
      EngineeringNode.Kind expectedKind, EngineeringGraph graph, EngineeringPackageValidationReport report)
      throws IOException {
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null || !root.has("rows") || !root.get("rows").isJsonArray()) {
      return;
    }
    Set<String> tags = new HashSet<String>();
    JsonArray rows = root.getAsJsonArray("rows");
    for (int i = 0; i < rows.size(); i++) {
      if (!rows.get(i).isJsonObject()) {
        report.addError("ENG-REGISTER-001", artifact, "/rows/" + i, "Register row must be an object");
        continue;
      }
      JsonObject row = rows.get(i).getAsJsonObject();
      String tag = stringValue(row, tagField);
      if (tag.isEmpty()) {
        report.addError("ENG-REGISTER-002", artifact, "/rows/" + i + "/" + tagField, "Controlled tag is missing");
      } else if (!tags.add(EngineeringIds.canonical(tag))) {
        report.addError("ENG-REGISTER-003", artifact, "/rows/" + i + "/" + tagField, "Duplicate canonical tag " + tag);
      }
      validateGraphReference(row, "graphNodeId", expectedKind, artifact, "/rows/" + i, graph, report);
    }
  }

  private static void validateInstrumentRegister(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "instrument-register.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null || !root.has("rows") || !root.get("rows").isJsonArray()) {
      return;
    }
    Set<String> tags = new HashSet<String>();
    JsonArray rows = root.getAsJsonArray("rows");
    for (int i = 0; i < rows.size(); i++) {
      if (!rows.get(i).isJsonObject()) {
        report.addError("ENG-REGISTER-001", artifact, "/rows/" + i, "Register row must be an object");
        continue;
      }
      JsonObject row = rows.get(i).getAsJsonObject();
      String tag = stringValue(row, "instrumentTag");
      if (tag.isEmpty() || !tags.add(EngineeringIds.canonical(tag))) {
        report.addError("ENG-REGISTER-004", artifact, "/rows/" + i + "/instrumentTag",
            tag.isEmpty() ? "Instrument tag is missing" : "Duplicate canonical instrument tag " + tag);
      }
      if (row.has("graphNodeId")) {
        validateGraphReference(row, "graphNodeId", EngineeringNode.Kind.INSTRUMENT, artifact, "/rows/" + i, graph,
            report);
      } else {
        validateGraphReference(row, "requirementGraphNodeId", EngineeringNode.Kind.REQUIREMENT, artifact, "/rows/" + i,
            graph, report);
      }
    }
  }

  private static void validateGraphReference(JsonObject row, String field, EngineeringNode.Kind expectedKind,
      String artifact, String path, EngineeringGraph graph, EngineeringPackageValidationReport report) {
    String nodeId = stringValue(row, field);
    EngineeringNode node = graph.getNode(nodeId);
    if (node == null) {
      report.addError("ENG-REGISTER-005", artifact, path + "/" + field,
          "Referenced graph node does not exist: " + nodeId);
    } else if (node.getKind() != expectedKind) {
      report.addError("ENG-REGISTER-006", artifact, path + "/" + field,
          "Referenced graph node has kind " + node.getKind() + "; expected " + expectedKind);
    }
  }

  private static void validateEnvelope(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "design-case-envelope.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null || !root.has("caseResults") || !root.has("governingValues")) {
      return;
    }
    Set<String> caseIds = new LinkedHashSet<String>();
    JsonArray cases = root.getAsJsonArray("caseResults");
    for (int i = 0; i < cases.size(); i++) {
      if (!cases.get(i).isJsonObject()) {
        report.addError("ENG-ENVELOPE-004", artifact, "/caseResults/" + i, "Design-case result must be an object");
        continue;
      }
      JsonObject item = cases.get(i).getAsJsonObject();
      if (item.has("case") && item.get("case").isJsonObject()) {
        String caseId = stringValue(item.getAsJsonObject("case"), "id");
        if (!caseId.isEmpty() && !caseIds.add(caseId)) {
          report.addError("ENG-ENVELOPE-001", artifact, "/caseResults/" + i + "/case/id",
              "Duplicate design-case id " + caseId);
        }
      }
    }
    JsonArray governing = root.getAsJsonArray("governingValues");
    for (int i = 0; i < governing.size(); i++) {
      if (!governing.get(i).isJsonObject()) {
        report.addError("ENG-ENVELOPE-005", artifact, "/governingValues/" + i, "Governing value must be an object");
        continue;
      }
      JsonObject value = governing.get(i).getAsJsonObject();
      String caseId = stringValue(value, "designCaseId");
      if (!caseIds.contains(caseId)) {
        report.addError("ENG-ENVELOPE-002", artifact, "/governingValues/" + i + "/designCaseId",
            "Governing value references an unknown design case " + caseId);
      }
      JsonObject metric = value.has("metric") && value.get("metric").isJsonObject() ? value.getAsJsonObject("metric")
          : null;
      String subjectTag = metric == null ? "" : stringValue(metric, "subjectTag");
      String equipmentId = subjectTag.isEmpty() ? ""
          : EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, subjectTag);
      if (graph.getNode(equipmentId) == null) {
        report.addError("ENG-ENVELOPE-003", artifact, "/governingValues/" + i + "/metric/subjectTag",
            "Envelope metric references unknown equipment " + subjectTag);
      }
      validateUnit(stringValue(value, "unit"), artifact, "/governingValues/" + i + "/unit", report);
    }
  }

  private static void validateConnectivity(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "engineering-connectivity.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null) {
      return;
    }
    if (!graph.getProjectId().equals(stringValue(root, "projectId"))) {
      report.addError("ENG-CONNECTIVITY-001", artifact, "/projectId", "Connectivity and graph project ids differ");
    }
    if (!graph.getRevision().equals(stringValue(root, "revision"))) {
      report.addError("ENG-CONNECTIVITY-002", artifact, "/revision", "Connectivity and graph revisions differ");
    }
    String fingerprint = String.valueOf(graph.toMap().get("fingerprint"));
    if (!fingerprint.equals(stringValue(root, "graphFingerprint"))) {
      report.addError("ENG-CONNECTIVITY-003", artifact, "/graphFingerprint",
          "Connectivity fingerprint does not match engineering-model.json");
    }
    validateConnectivityReferences(root, "nodes", graph.getNodes(), artifact, report);
    validateConnectivityReferences(root, "edges", graph.getEdges(), artifact, report);
  }

  private static void validateConnectivityReferences(JsonObject root, String arrayName, Map<String, ?> graphValues,
      String artifact, EngineeringPackageValidationReport report) {
    if (!root.has(arrayName) || !root.get(arrayName).isJsonArray()) {
      return;
    }
    JsonArray values = root.getAsJsonArray(arrayName);
    for (int i = 0; i < values.size(); i++) {
      if (!values.get(i).isJsonObject()) {
        report.addError("ENG-CONNECTIVITY-004", artifact, "/" + arrayName + "/" + i,
            "Connectivity entry must be an object");
        continue;
      }
      String id = stringValue(values.get(i).getAsJsonObject(), "id");
      if (!graphValues.containsKey(id)) {
        report.addError("ENG-CONNECTIVITY-005", artifact, "/" + arrayName + "/" + i + "/id",
            "Connectivity entry does not exist in the canonical graph: " + id);
      }
    }
  }

  private static void validateCalculationDag(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "engineering-calculation-dag.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null) {
      return;
    }
    if (!graph.getProjectId().equals(stringValue(root, "projectId"))) {
      report.addError("ENG-CALCULATION-001", artifact, "/projectId", "Calculation DAG and graph project ids differ");
    }
    if (!graph.getRevision().equals(stringValue(root, "revision"))) {
      report.addError("ENG-CALCULATION-002", artifact, "/revision", "Calculation DAG and graph revisions differ");
    }
    if (!root.has("nodes") || !root.get("nodes").isJsonArray() || !root.has("topologicalOrder")
        || !root.get("topologicalOrder").isJsonArray()) {
      return;
    }
    Map<String, Integer> positions = new java.util.LinkedHashMap<String, Integer>();
    JsonArray order = root.getAsJsonArray("topologicalOrder");
    for (int i = 0; i < order.size(); i++) {
      String id = order.get(i).isJsonPrimitive() ? order.get(i).getAsString() : "";
      if (id.isEmpty() || positions.put(id, Integer.valueOf(i)) != null) {
        report.addError("ENG-CALCULATION-003", artifact, "/topologicalOrder/" + i,
            id.isEmpty() ? "Calculation id is missing" : "Duplicate calculation in topological order: " + id);
      }
    }
    Set<String> nodeIds = new LinkedHashSet<String>();
    JsonArray nodes = root.getAsJsonArray("nodes");
    for (int i = 0; i < nodes.size(); i++) {
      if (!nodes.get(i).isJsonObject()) {
        report.addError("ENG-CALCULATION-004", artifact, "/nodes/" + i, "Calculation node must be an object");
        continue;
      }
      JsonObject node = nodes.get(i).getAsJsonObject();
      String id = stringValue(node, "id");
      if (!nodeIds.add(id) || !positions.containsKey(id)) {
        report.addError("ENG-CALCULATION-005", artifact, "/nodes/" + i + "/id",
            positions.containsKey(id) ? "Duplicate calculation node " + id
                : "Calculation node is missing from topological order: " + id);
      }
      String graphNodeId = id.isEmpty() ? "" : EngineeringIds.nodeId(EngineeringNode.Kind.CALCULATION, id);
      if (graph.getNode(graphNodeId) == null) {
        report.addError("ENG-CALCULATION-006", artifact, "/nodes/" + i + "/id",
            "Calculation is missing from the canonical graph: " + id);
      }
      if (node.has("standardsRequired") && node.get("standardsRequired").getAsBoolean() && node.has("standardsReady")
          && !node.get("standardsReady").getAsBoolean()) {
        String status = stringValue(node, "status");
        if ("CALCULATED".equals(status) || "APPROVED".equals(status)) {
          report.addError("ENG-CALCULATION-007", artifact, "/nodes/" + i + "/standardReferences",
              "Completed calculation is missing its required standards basis: " + id);
        } else {
          report.addWarning("ENG-CALCULATION-008", artifact, "/nodes/" + i + "/standardReferences",
              "Calculation cannot become ready until its standards basis is declared: " + id);
        }
      }
    }
    if (nodeIds.size() != positions.size()) {
      report.addError("ENG-CALCULATION-009", artifact, "/topologicalOrder",
          "Topological order and calculation node inventory differ");
    }
    if (root.has("edges") && root.get("edges").isJsonArray()) {
      JsonArray edges = root.getAsJsonArray("edges");
      for (int i = 0; i < edges.size(); i++) {
        if (!edges.get(i).isJsonObject()) {
          report.addError("ENG-CALCULATION-010", artifact, "/edges/" + i, "Dependency edge must be an object");
          continue;
        }
        JsonObject edge = edges.get(i).getAsJsonObject();
        String source = stringValue(edge, "sourceCalculationId");
        String target = stringValue(edge, "targetCalculationId");
        if (!positions.containsKey(source) || !positions.containsKey(target)) {
          report.addError("ENG-CALCULATION-011", artifact, "/edges/" + i,
              "Dependency edge references an unknown calculation");
        } else if (positions.get(target).intValue() >= positions.get(source).intValue()) {
          report.addError("ENG-CALCULATION-012", artifact, "/edges/" + i,
              "Prerequisite must precede the dependent calculation in topological order");
        }
      }
    }
  }

  private static void validateDesignCaseMatrix(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "engineering-design-case-matrix.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null) {
      return;
    }
    if (!graph.getProjectId().equals(stringValue(root, "projectId"))) {
      report.addError("ENG-DESIGN-CASE-001", artifact, "/projectId", "Design-case matrix and graph project ids differ");
    }
    if (!graph.getRevision().equals(stringValue(root, "revision"))) {
      report.addError("ENG-DESIGN-CASE-002", artifact, "/revision", "Design-case matrix and graph revisions differ");
    }
    Set<String> caseIds = new LinkedHashSet<String>();
    if (root.has("cases") && root.get("cases").isJsonArray()) {
      JsonArray cases = root.getAsJsonArray("cases");
      for (int i = 0; i < cases.size(); i++) {
        if (!cases.get(i).isJsonObject()) {
          report.addError("ENG-DESIGN-CASE-003", artifact, "/cases/" + i, "Design case must be an object");
          continue;
        }
        String id = stringValue(cases.get(i).getAsJsonObject(), "id");
        if (!caseIds.add(id)) {
          report.addError("ENG-DESIGN-CASE-004", artifact, "/cases/" + i + "/id", "Duplicate design-case id " + id);
        }
        String graphNodeId = id.isEmpty() ? "" : EngineeringIds.nodeId(EngineeringNode.Kind.DESIGN_CASE, id);
        if (graph.getNode(graphNodeId) == null) {
          report.addError("ENG-DESIGN-CASE-005", artifact, "/cases/" + i + "/id",
              "Design case is missing from the canonical graph: " + id);
        }
      }
    }
    if (root.has("executions") && root.get("executions").isJsonArray()) {
      JsonArray executions = root.getAsJsonArray("executions");
      for (int i = 0; i < executions.size(); i++) {
        if (!executions.get(i).isJsonObject()) {
          report.addError("ENG-DESIGN-CASE-006", artifact, "/executions/" + i,
              "Design-case execution must be an object");
          continue;
        }
        JsonObject execution = executions.get(i).getAsJsonObject();
        JsonObject designCase = execution.has("case") && execution.get("case").isJsonObject()
            ? execution.getAsJsonObject("case")
            : null;
        String id = stringValue(designCase, "id");
        if (!caseIds.contains(id)) {
          report.addError("ENG-DESIGN-CASE-007", artifact, "/executions/" + i + "/case/id",
              "Execution references an unknown design case " + id);
        }
      }
    }
    if (root.has("summary") && root.get("summary").isJsonObject()) {
      JsonObject summary = root.getAsJsonObject("summary");
      int requiredFailures = intValue(summary, "requiredCaseFailureCount");
      int limitViolations = intValue(summary, "limitViolationCount");
      if (requiredFailures > 0) {
        report.addWarning("ENG-DESIGN-CASE-008", artifact, "/summary/requiredCaseFailureCount",
            requiredFailures + " required design case(s) did not complete successfully");
      }
      if (limitViolations > 0) {
        report.addWarning("ENG-DESIGN-CASE-009", artifact, "/summary/limitViolationCount",
            limitViolations + " design-case metric acceptance-limit violation(s) require review");
      }
    }
  }

  private static void validateDisciplinePackage(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "engineering-discipline-package.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null) {
      return;
    }
    if (!graph.getProjectId().equals(stringValue(root, "projectId"))) {
      report.addError("ENG-DISCIPLINE-001", artifact, "/projectId", "Discipline package and graph project ids differ");
    }
    if (!graph.getRevision().equals(stringValue(root, "revision"))) {
      report.addError("ENG-DISCIPLINE-002", artifact, "/revision", "Discipline package and graph revisions differ");
    }
    String fingerprint = String.valueOf(graph.toMap().get("fingerprint"));
    if (!fingerprint.equals(stringValue(root, "graphFingerprint"))) {
      report.addError("ENG-DISCIPLINE-003", artifact, "/graphFingerprint",
          "Discipline package fingerprint does not match engineering-model.json");
    }
    validateDisciplineReferences(root, "equipmentDatasheets", EngineeringNode.Kind.EQUIPMENT, graph, artifact, report);
    validateDisciplineReferences(root, "lineDatasheets", EngineeringNode.Kind.LINE, graph, artifact, report);
    validateDisciplineReferences(root, "instrumentAndSafeguardingRequirements", EngineeringNode.Kind.REQUIREMENT, graph,
        artifact, report);
    if (root.has("disciplineSummary") && root.get("disciplineSummary").isJsonObject()) {
      JsonObject summary = root.getAsJsonObject("disciplineSummary");
      if ("INCOMPLETE".equals(stringValue(summary, "overallStatus"))) {
        report.addWarning("ENG-DISCIPLINE-005", artifact, "/disciplineSummary/overallStatus",
            "One or more discipline deliverables have controlled data gaps");
      }
    }
  }

  private static void validateDisciplineReferences(JsonObject root, String arrayName, EngineeringNode.Kind expectedKind,
      EngineeringGraph graph, String artifact, EngineeringPackageValidationReport report) {
    if (!root.has(arrayName) || !root.get(arrayName).isJsonArray()) {
      return;
    }
    JsonArray values = root.getAsJsonArray(arrayName);
    for (int i = 0; i < values.size(); i++) {
      if (!values.get(i).isJsonObject()) {
        report.addError("ENG-DISCIPLINE-004", artifact, "/" + arrayName + "/" + i, "Discipline item must be an object");
        continue;
      }
      JsonObject item = values.get(i).getAsJsonObject();
      validateGraphReference(item, "graphNodeId", expectedKind, artifact, "/" + arrayName + "/" + i, graph, report);
    }
  }

  private static void validateApprovalLedger(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "engineering-approval-ledger.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null) {
      return;
    }
    if (!graph.getProjectId().equals(stringValue(root, "projectId"))) {
      report.addError("ENG-APPROVAL-001", artifact, "/projectId", "Approval ledger and graph project ids differ");
    }
    if (!graph.getRevision().equals(stringValue(root, "revision"))) {
      report.addError("ENG-APPROVAL-002", artifact, "/revision", "Approval ledger and graph revisions differ");
    }
    String fingerprint = String.valueOf(graph.toMap().get("fingerprint"));
    if (!fingerprint.equals(stringValue(root, "graphFingerprint"))) {
      report.addError("ENG-APPROVAL-003", artifact, "/graphFingerprint",
          "Approval ledger fingerprint does not match engineering-model.json");
    }
    Set<String> recordIds = new LinkedHashSet<String>();
    if (root.has("records") && root.get("records").isJsonArray()) {
      JsonArray records = root.getAsJsonArray("records");
      for (int i = 0; i < records.size(); i++) {
        String path = "/records/" + i;
        if (!records.get(i).isJsonObject()) {
          report.addError("ENG-APPROVAL-004", artifact, path, "Approval record must be an object");
          continue;
        }
        JsonObject record = records.get(i).getAsJsonObject();
        String recordId = stringValue(record, "id");
        String subjectNodeId = stringValue(record, "subjectNodeId");
        if (recordId.isEmpty() || !recordIds.add(recordId)) {
          report.addError("ENG-APPROVAL-005", artifact, path + "/id", "Approval record id is missing or duplicated");
        }
        if (graph.getNode(subjectNodeId) == null) {
          report.addError("ENG-APPROVAL-006", artifact, path + "/subjectNodeId",
              "Approval subject does not exist in the engineering graph: " + subjectNodeId);
        }
        String supersedes = stringValue(record, "supersedesRecordId");
        if (!supersedes.isEmpty() && !recordIds.contains(supersedes)) {
          report.addError("ENG-APPROVAL-007", artifact, path + "/supersedesRecordId",
              "Superseded record must exist earlier in the ledger: " + supersedes);
        }
      }
    }
    if (root.has("effectiveStates") && root.get("effectiveStates").isJsonArray()) {
      JsonArray states = root.getAsJsonArray("effectiveStates");
      for (int i = 0; i < states.size(); i++) {
        if (!states.get(i).isJsonObject()) {
          report.addError("ENG-APPROVAL-008", artifact, "/effectiveStates/" + i,
              "Effective approval state must be an object");
          continue;
        }
        JsonObject state = states.get(i).getAsJsonObject();
        String subjectNodeId = stringValue(state, "subjectNodeId");
        if (graph.getNode(subjectNodeId) == null) {
          report.addError("ENG-APPROVAL-009", artifact, "/effectiveStates/" + i + "/subjectNodeId",
              "Effective approval subject does not exist: " + subjectNodeId);
        }
        String effectiveRecordId = stringValue(state, "effectiveRecordId");
        if (!recordIds.contains(effectiveRecordId)) {
          report.addError("ENG-APPROVAL-010", artifact, "/effectiveStates/" + i + "/effectiveRecordId",
              "Effective approval record does not exist: " + effectiveRecordId);
        }
        if ("REVALIDATION_REQUIRED".equals(stringValue(state, "status"))) {
          report.addWarning("ENG-APPROVAL-011", artifact, "/effectiveStates/" + i + "/status",
              "An approved engineering object changed since the baseline and requires revalidation");
        }
      }
    }
  }

  private static void validateDexpiRoundTrip(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "engineering-dexpi-roundtrip-report.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null) {
      return;
    }
    if (!graph.getProjectId().equals(stringValue(root, "projectId"))) {
      report.addError("ENG-DEXPI-ROUNDTRIP-001", artifact, "/projectId",
          "DEXPI round-trip report and graph project ids differ");
    }
    if (!graph.getRevision().equals(stringValue(root, "revision"))) {
      report.addError("ENG-DEXPI-ROUNDTRIP-002", artifact, "/revision",
          "DEXPI round-trip report and graph revisions differ");
    }
    String fingerprint = String.valueOf(graph.toMap().get("fingerprint"));
    if (!fingerprint.equals(stringValue(root, "graphFingerprint"))) {
      report.addError("ENG-DEXPI-ROUNDTRIP-003", artifact, "/graphFingerprint",
          "DEXPI round-trip report fingerprint does not match engineering-model.json");
    }
    if (!"INTERNAL_STRUCTURAL_ROUNDTRIP_PASSED".equals(stringValue(root, "status"))) {
      report.addError("ENG-DEXPI-ROUNDTRIP-004", artifact, "/status",
          "One or more DEXPI representations failed internal structural export/reimport qualification");
    }
    if (!"QUALIFICATION_REQUIRED".equals(stringValue(root, "commercialCaeStatus"))) {
      report.addError("ENG-DEXPI-ROUNDTRIP-005", artifact, "/commercialCaeStatus",
          "Internal round-trip evidence must not be presented as commercial CAE qualification");
    }
    if (root.has("formats") && root.get("formats").isJsonArray()) {
      JsonArray formats = root.getAsJsonArray("formats");
      Set<String> profiles = new LinkedHashSet<String>();
      for (int i = 0; i < formats.size(); i++) {
        if (!formats.get(i).isJsonObject()) {
          report.addError("ENG-DEXPI-ROUNDTRIP-006", artifact, "/formats/" + i,
              "Format qualification must be an object");
          continue;
        }
        JsonObject format = formats.get(i).getAsJsonObject();
        String profile = stringValue(format, "profile");
        if (!profiles.add(profile)) {
          report.addError("ENG-DEXPI-ROUNDTRIP-007", artifact, "/formats/" + i + "/profile",
              "DEXPI format qualification profile is duplicated: " + profile);
        }
        if (!"PASSED".equals(stringValue(format, "status"))) {
          report.addError("ENG-DEXPI-ROUNDTRIP-008", artifact, "/formats/" + i + "/status",
              "DEXPI format did not preserve required identities and references: " + profile);
        }
      }
      if (profiles.size() != 3) {
        report.addError("ENG-DEXPI-ROUNDTRIP-009", artifact, "/formats",
            "Expected native DEXPI, Proteus and pyDEXPI qualification results");
      }
    }
  }

  private static void validateAutomationPlan(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "engineering-automation-plan.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null) {
      return;
    }
    if (!graph.getProjectId().equals(stringValue(root, "projectId"))) {
      report.addError("ENG-AUTOMATION-001", artifact, "/projectId", "Automation plan and graph project ids differ");
    }
    if (!graph.getRevision().equals(stringValue(root, "revision"))) {
      report.addError("ENG-AUTOMATION-002", artifact, "/revision", "Automation plan and graph revisions differ");
    }
    String fingerprint = String.valueOf(graph.toMap().get("fingerprint"));
    if (!fingerprint.equals(stringValue(root, "graphFingerprint"))) {
      report.addError("ENG-AUTOMATION-003", artifact, "/graphFingerprint",
          "Automation plan fingerprint does not match engineering-model.json");
    }
    if (root.has("executionBoundary") && root.get("executionBoundary").isJsonObject()) {
      JsonObject boundary = root.getAsJsonObject("executionBoundary");
      if (!boundary.has("automaticPlantChange") || !boundary.get("automaticPlantChange").isJsonPrimitive()
          || boundary.get("automaticPlantChange").getAsBoolean()) {
        report.addError("ENG-AUTOMATION-004", artifact, "/executionBoundary/automaticPlantChange",
            "Engineering automation must remain advisory and must not authorize automatic plant changes");
      }
    }
    Set<String> studyIds = new LinkedHashSet<String>();
    if (root.has("studies") && root.get("studies").isJsonArray()) {
      JsonArray studies = root.getAsJsonArray("studies");
      for (int i = 0; i < studies.size(); i++) {
        if (!studies.get(i).isJsonObject()) {
          report.addError("ENG-AUTOMATION-005", artifact, "/studies/" + i, "Automation study must be an object");
          continue;
        }
        JsonObject study = studies.get(i).getAsJsonObject();
        String studyId = stringValue(study, "id");
        if (studyId.isEmpty() || !studyIds.add(studyId)) {
          report.addError("ENG-AUTOMATION-006", artifact, "/studies/" + i + "/id",
              "Automation study id is missing or duplicated");
        }
        if (!study.has("decisionVariables") || !study.get("decisionVariables").isJsonArray()) {
          continue;
        }
        JsonArray variables = study.getAsJsonArray("decisionVariables");
        Set<String> variableIds = new LinkedHashSet<String>();
        for (int j = 0; j < variables.size(); j++) {
          if (!variables.get(j).isJsonObject()) {
            report.addError("ENG-AUTOMATION-007", artifact, "/studies/" + i + "/decisionVariables/" + j,
                "Decision variable must be an object");
            continue;
          }
          JsonObject variable = variables.get(j).getAsJsonObject();
          String variableId = stringValue(variable, "id");
          if (variableId.isEmpty() || !variableIds.add(variableId)) {
            report.addError("ENG-AUTOMATION-008", artifact, "/studies/" + i + "/decisionVariables/" + j + "/id",
                "Decision-variable id is missing or duplicated within the study");
          }
          String graphNodeId = stringValue(variable, "graphNodeId");
          if (graph.getNode(graphNodeId) == null) {
            report.addError("ENG-AUTOMATION-009", artifact,
                "/studies/" + i + "/decisionVariables/" + j + "/graphNodeId",
                "Decision variable references unknown graph node " + graphNodeId);
          }
        }
      }
    }
  }

  private static void validateManifest(Path directory, EngineeringGraph graph,
      EngineeringPackageValidationReport report) throws IOException {
    String artifact = "engineering-compiler-manifest.json";
    Path file = directory.resolve(artifact);
    if (!Files.isRegularFile(file)) {
      return;
    }
    JsonObject root = parseObject(artifact, read(file), report);
    if (root == null) {
      return;
    }
    if (!graph.getProjectId().equals(stringValue(root, "projectId"))) {
      report.addError("ENG-MANIFEST-001", artifact, "/projectId", "Manifest and graph project ids differ");
    }
    if (!graph.getRevision().equals(stringValue(root, "revision"))) {
      report.addError("ENG-MANIFEST-002", artifact, "/revision", "Manifest and graph revisions differ");
    }
    String fingerprint = String.valueOf(graph.toMap().get("fingerprint"));
    if (!fingerprint.equals(stringValue(root, "graphFingerprint"))) {
      report.addError("ENG-MANIFEST-003", artifact, "/graphFingerprint",
          "Manifest fingerprint does not match engineering-model.json");
    }
    if (root.has("artifacts") && root.get("artifacts").isJsonArray()) {
      Set<String> inventory = new HashSet<String>();
      for (JsonElement item : root.getAsJsonArray("artifacts")) {
        if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
          report.addError("ENG-MANIFEST-006", artifact, "/artifacts",
              "Artifact inventory entries must be relative path strings");
          continue;
        }
        String relative = item.getAsString();
        if (!inventory.add(relative)) {
          report.addError("ENG-MANIFEST-004", artifact, "/artifacts", "Duplicate artifact entry " + relative);
        }
        Path rootDirectory = directory.toAbsolutePath().normalize();
        Path resolved = rootDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(rootDirectory) || !Files.isRegularFile(resolved)) {
          report.addError("ENG-MANIFEST-005", artifact, "/artifacts", "Inventory artifact is missing: " + relative);
        }
      }
    }
  }

  private static void validateSchemaFiles(Path directory, EngineeringPackageValidationReport report)
      throws IOException {
    Path catalog = directory.resolve("engineering-schema-catalog.json");
    if (!Files.isRegularFile(catalog)) {
      report.addError("ENG-SCHEMA-020", "engineering-schema-catalog.json", "", "Schema catalog is missing");
    }
    for (EngineeringSchemaCatalog.Definition definition : EngineeringSchemaCatalog.getDefinitions()) {
      Path schema = directory.resolve("schemas").resolve(definition.getSchemaFile());
      if (!Files.isRegularFile(schema)) {
        report.addError("ENG-SCHEMA-021", definition.getArtifactName(), "/schemaFile",
            "Bundled JSON Schema is missing: schemas/" + definition.getSchemaFile());
        continue;
      }
      JsonObject schemaRoot = parseObject("schemas/" + definition.getSchemaFile(), read(schema), report);
      if (schemaRoot != null && !definition.getSchemaUri().equals(stringValue(schemaRoot, "$id"))) {
        report.addError("ENG-SCHEMA-022", definition.getArtifactName(), "/schemaFile/$id",
            "Bundled JSON Schema id does not match the catalog URI");
      }
    }
  }

  private static void validateCalculationUnit(EngineeringNode node, EngineeringPackageValidationReport report,
      String artifact, String path) {
    if (node.getKind() != EngineeringNode.Kind.CALCULATION || !node.getProperties().containsKey("resultValue")) {
      return;
    }
    Object result = node.getProperties().get("resultValue");
    if (result instanceof Number) {
      Object unit = node.getProperties().get("resultUnit");
      validateUnit(unit == null ? "" : String.valueOf(unit), artifact, path + "/properties/resultUnit", report);
    }
  }

  private static void validateUnit(String unit, String artifact, String path,
      EngineeringPackageValidationReport report) {
    if (unit == null || unit.trim().isEmpty()) {
      report.addError("ENG-UNIT-001", artifact, path, "Numeric engineering value has no unit");
    } else if (!CONTROLLED_UNITS.contains(unit.trim().toLowerCase())) {
      report.addWarning("ENG-UNIT-002", artifact, path,
          "Unit is not in the compiler controlled-unit vocabulary: " + unit);
    }
  }

  private static JsonObject parseObject(String artifact, String json, EngineeringPackageValidationReport report) {
    if (json == null || json.trim().isEmpty()) {
      report.addError("ENG-SCHEMA-007", artifact, "", "JSON document is empty");
      return null;
    }
    try {
      JsonElement parsed = JsonParser.parseString(json);
      if (!parsed.isJsonObject()) {
        report.addError("ENG-SCHEMA-008", artifact, "", "JSON document root must be an object");
        return null;
      }
      return parsed.getAsJsonObject();
    } catch (JsonParseException ex) {
      report.addError("ENG-SCHEMA-009", artifact, "", "Invalid JSON: " + ex.getMessage());
      return null;
    }
  }

  private static void requireString(JsonObject root, String name, String artifact,
      EngineeringPackageValidationReport report) {
    if (!root.has(name) || !root.get(name).isJsonPrimitive() || !root.get(name).getAsJsonPrimitive().isString()
        || root.get(name).getAsString().trim().isEmpty()) {
      report.addError("ENG-SCHEMA-010", artifact, "/" + name, "Required non-empty string property is missing");
    }
  }

  private static void requireArray(JsonObject root, String name, String artifact,
      EngineeringPackageValidationReport report) {
    if (!root.has(name) || !root.get(name).isJsonArray()) {
      report.addError("ENG-SCHEMA-012", artifact, "/" + name, "Required array property is missing");
    }
  }

  private static void requireObject(JsonObject root, String name, String artifact,
      EngineeringPackageValidationReport report) {
    if (!root.has(name) || !root.get(name).isJsonObject()) {
      report.addError("ENG-SCHEMA-013", artifact, "/" + name, "Required object property is missing");
    }
  }

  private static String stringValue(JsonObject value, String name) {
    if (value == null || !value.has(name) || value.get(name).isJsonNull() || !value.get(name).isJsonPrimitive()) {
      return "";
    }
    return value.get(name).getAsString();
  }

  private static int intValue(JsonObject value, String name) {
    if (value == null || !value.has(name) || !value.get(name).isJsonPrimitive()
        || !value.get(name).getAsJsonPrimitive().isNumber()) {
      return 0;
    }
    return value.get(name).getAsInt();
  }

  private static String read(Path file) throws IOException {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
