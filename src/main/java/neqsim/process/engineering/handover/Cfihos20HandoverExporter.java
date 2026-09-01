package neqsim.process.engineering.handover;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.engineering.handover.Cfihos20HandoverReport.Finding;
import neqsim.process.engineering.handover.Cfihos20ReferenceDataMapping.NodeClassification;
import neqsim.process.engineering.handover.Cfihos20ReferenceDataMapping.PropertyDefinition;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;

/**
 * Exports a deterministic, auditable staging package for project-controlled CFIHOS 2.0 transformation.
 *
 * <p>
 * The emitted CSVs are a NeqSim staging profile, not an official Principal bulk-loader template. Exact target-system
 * transformation and CFIHOS acceptance remain outside the exporter.
 */
public final class Cfihos20HandoverExporter {
  private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
  private static final String TAGS = "cfihos-tags.csv";
  private static final String EQUIPMENT = "cfihos-equipment.csv";
  private static final String PROPERTIES = "cfihos-properties.csv";
  private static final String DOCUMENTS = "cfihos-documents.csv";
  private static final String RELATIONSHIPS = "cfihos-relationships.csv";
  private static final String UNMAPPED = "cfihos-unmapped.csv";
  private static final String ASSESSMENT = "cfihos-20-assessment.json";
  private static final String MANIFEST = "cfihos-20-manifest.json";

  private Cfihos20HandoverExporter() {
  }

  /** Files and in-memory readiness evidence produced by one export. */
  public static final class Result {
    private final Path outputDirectory;
    private final Path manifestFile;
    private final Path assessmentFile;
    private final Path unmappedFile;
    private final Cfihos20HandoverReport report;

    Result(Path outputDirectory, Path manifestFile, Path assessmentFile, Path unmappedFile,
        Cfihos20HandoverReport report) {
      this.outputDirectory = outputDirectory;
      this.manifestFile = manifestFile;
      this.assessmentFile = assessmentFile;
      this.unmappedFile = unmappedFile;
      this.report = report;
    }

    public Path getOutputDirectory() {
      return outputDirectory;
    }

    public Path getManifestFile() {
      return manifestFile;
    }

    public Path getAssessmentFile() {
      return assessmentFile;
    }

    public Path getUnmappedFile() {
      return unmappedFile;
    }

    public Cfihos20HandoverReport getReport() {
      return report;
    }
  }

  /** Creates the complete staging package and a fail-closed readiness assessment. */
  public static Result export(EngineeringGraph graph, Cfihos20ReferenceDataMapping mapping, Path outputDirectory)
      throws IOException {
    if (graph == null) {
      throw new IllegalArgumentException("graph must not be null");
    }
    if (mapping == null) {
      throw new IllegalArgumentException("mapping must not be null");
    }
    if (outputDirectory == null) {
      throw new IllegalArgumentException("outputDirectory must not be null");
    }
    Files.createDirectories(outputDirectory);

    Rows rows = buildRows(graph, mapping);
    LinkedHashMap<String, Path> csvFiles = new LinkedHashMap<String, Path>();
    csvFiles.put(TAGS, writeCsv(outputDirectory.resolve(TAGS), Arrays.asList("tag_id", "tag_name", "tag_description",
        "cfihos_tag_class_id", "source_node_id", "project_id", "revision"), rows.tags));
    csvFiles.put(EQUIPMENT, writeCsv(outputDirectory.resolve(EQUIPMENT), Arrays.asList("equipment_id", "equipment_name",
        "cfihos_equipment_class_id", "realizes_tag_id", "source_node_id"), rows.equipment));
    csvFiles.put(PROPERTIES, writeCsv(outputDirectory.resolve(PROPERTIES),
        Arrays.asList("owner_id", "cfihos_property_id", "value", "cfihos_uom_id", "source_property"), rows.properties));
    csvFiles.put(DOCUMENTS, writeCsv(outputDirectory.resolve(DOCUMENTS),
        Arrays.asList("document_id", "document_title", "cfihos_document_type_id", "file_reference", "source_node_id"),
        rows.documents));
    csvFiles.put(RELATIONSHIPS, writeCsv(outputDirectory.resolve(RELATIONSHIPS),
        Arrays.asList("relationship_id", "source_id", "target_id", "relationship_type", "role"), rows.relationships));
    csvFiles.put(UNMAPPED, writeCsv(outputDirectory.resolve(UNMAPPED),
        Arrays.asList("source_node_id", "source_kind", "source_item", "gap_type", "required_action"), rows.unmapped));

    Map<String, String> csvDigests = digests(csvFiles);
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    counts.put("tags", Integer.valueOf(rows.tags.size()));
    counts.put("equipment", Integer.valueOf(rows.equipment.size()));
    counts.put("properties", Integer.valueOf(rows.properties.size()));
    counts.put("documents", Integer.valueOf(rows.documents.size()));
    counts.put("relationships", Integer.valueOf(rows.relationships.size()));
    counts.put("unmapped", Integer.valueOf(rows.unmapped.size()));
    addMappingControlFindings(mapping, rows.findings);
    if (rows.tags.isEmpty()) {
      rows.findings.add(
          blocker("NO_HANDOVER_TAGS", graph.getProjectId(), "No tag-like engineering nodes were available for handover",
              "Build the canonical engineering graph and classify its tag-bearing nodes"));
    }

    Cfihos20HandoverReport report = new Cfihos20HandoverReport(graph.getProjectId(), graph.getRevision(), mapping,
        counts, rows.findings, csvDigests);
    Path assessmentFile = outputDirectory.resolve(ASSESSMENT);
    write(assessmentFile, report.toJson());

    LinkedHashMap<String, Path> manifestedFiles = new LinkedHashMap<String, Path>(csvFiles);
    manifestedFiles.put(ASSESSMENT, assessmentFile);
    Path manifestFile = outputDirectory.resolve(MANIFEST);
    write(manifestFile, manifest(graph, mapping, report, digests(manifestedFiles)));
    return new Result(outputDirectory, manifestFile, assessmentFile, outputDirectory.resolve(UNMAPPED), report);
  }

  private static Rows buildRows(EngineeringGraph graph, Cfihos20ReferenceDataMapping mapping) {
    Rows rows = new Rows();
    Map<String, String> transferredIds = new LinkedHashMap<String, String>();
    List<EngineeringNode> nodes = new ArrayList<EngineeringNode>(graph.getNodes().values());
    Collections.sort(nodes, Comparator.comparing(EngineeringNode::getId));
    for (EngineeringNode node : nodes) {
      if (isTagKind(node.getKind())) {
        addTag(graph, mapping, node, rows, transferredIds);
      } else if (node.getKind() == EngineeringNode.Kind.DOCUMENT) {
        addDocument(mapping, node, rows, transferredIds);
      }
    }
    addGraphRelationships(graph, transferredIds, rows);
    Collections.sort(rows.relationships, Comparator.comparing(values -> values.get(0)));
    return rows;
  }

  private static void addTag(EngineeringGraph graph, Cfihos20ReferenceDataMapping mapping, EngineeringNode node,
      Rows rows, Map<String, String> transferredIds) {
    NodeClassification classification = mapping.getNodeClassification(node.getId());
    if (classification == null) {
      rows.unmapped.add(gap(node, node.getExternalKey(), "MISSING_NODE_CLASSIFICATION",
          "Map the canonical node to an exact CFIHOS 2.0 tag class id"));
      rows.findings.add(blocker("MISSING_NODE_CLASSIFICATION", node.getId(),
          "No CFIHOS tag class is mapped for " + node.getExternalKey(),
          "Approve an exact RDL tag-class mapping for this node"));
      return;
    }
    String transferId = node.getId();
    transferredIds.put(node.getId(), transferId);
    rows.tags.add(Arrays.asList(transferId, node.getExternalKey(), node.getLabel(), classification.getTagClassId(),
        node.getId(), graph.getProjectId(), graph.getRevision()));

    if (node.getKind() == EngineeringNode.Kind.EQUIPMENT) {
      if (classification.getEquipmentClassId().isEmpty()) {
        rows.unmapped.add(gap(node, node.getExternalKey(), "MISSING_EQUIPMENT_CLASSIFICATION",
            "Map the physical equipment to an exact CFIHOS 2.0 equipment class id"));
        rows.findings.add(
            blocker("MISSING_EQUIPMENT_CLASSIFICATION", node.getId(), "Equipment node has no CFIHOS equipment class",
                "Approve an exact RDL equipment-class mapping for this node"));
      } else {
        String equipmentId = "physical:" + node.getId();
        rows.equipment.add(Arrays.asList(equipmentId, node.getLabel(), classification.getEquipmentClassId(), transferId,
            node.getId()));
        rows.relationships.add(
            Arrays.asList("realizes:" + node.getId(), equipmentId, transferId, "EQUIPMENT_REALIZES_TAG", "realizes"));
      }
    }
    addProperties(mapping, node, transferId, rows);
  }

  private static void addProperties(Cfihos20ReferenceDataMapping mapping, EngineeringNode node, String transferId,
      Rows rows) {
    for (Map.Entry<String, Object> property : new TreeMap<String, Object>(node.getProperties()).entrySet()) {
      if (!isScalar(property.getValue())) {
        continue;
      }
      PropertyDefinition definition = mapping.getPropertyDefinition(property.getKey());
      if (definition == null) {
        rows.unmapped.add(gap(node, property.getKey(), "UNMAPPED_OPTIONAL_PROPERTY",
            "Map this property if it is required by the project CFIHOS data requirements"));
        rows.findings.add(warning("UNMAPPED_OPTIONAL_PROPERTY", node.getId(),
            "Canonical property " + property.getKey() + " was not transferred",
            "Confirm it is out of scope or map its CFIHOS property and UOM ids"));
        continue;
      }
      if (property.getValue() instanceof Number && definition.getUnitOfMeasureId().isEmpty()) {
        rows.unmapped.add(gap(node, property.getKey(), "MISSING_NUMERIC_PROPERTY_UOM",
            "Map the numeric property to an exact CFIHOS 2.0 unit-of-measure id"));
        rows.findings.add(blocker("MISSING_NUMERIC_PROPERTY_UOM", node.getId(),
            "Numeric property " + property.getKey() + " has no CFIHOS UOM mapping",
            "Approve an exact RDL unit-of-measure mapping for this property"));
        continue;
      }
      rows.properties.add(Arrays.asList(transferId, definition.getPropertyId(), String.valueOf(property.getValue()),
          definition.getUnitOfMeasureId(), property.getKey()));
    }
  }

  private static void addDocument(Cfihos20ReferenceDataMapping mapping, EngineeringNode node, Rows rows,
      Map<String, String> transferredIds) {
    String documentTypeId = mapping.getDocumentTypeId(node.getExternalKey());
    if (documentTypeId == null) {
      rows.unmapped.add(gap(node, node.getExternalKey(), "MISSING_DOCUMENT_TYPE",
          "Map the document to an exact CFIHOS 2.0 document-type id"));
      rows.findings.add(blocker("MISSING_DOCUMENT_TYPE", node.getId(),
          "No CFIHOS document type is mapped for " + node.getExternalKey(),
          "Approve an exact RDL document-type mapping or remove the document from handover scope"));
      return;
    }
    String fileReference = node.getProperties().containsKey("file") ? String.valueOf(node.getProperties().get("file"))
        : node.getExternalKey();
    rows.documents.add(Arrays.asList(node.getId(), node.getLabel(), documentTypeId, fileReference, node.getId()));
    transferredIds.put(node.getId(), node.getId());
  }

  private static void addGraphRelationships(EngineeringGraph graph, Map<String, String> transferredIds, Rows rows) {
    List<EngineeringEdge> edges = new ArrayList<EngineeringEdge>(graph.getEdges().values());
    Collections.sort(edges, Comparator.comparing(EngineeringEdge::getId));
    for (EngineeringEdge edge : edges) {
      String sourceId = transferredIds.get(edge.getSourceId());
      String targetId = transferredIds.get(edge.getTargetId());
      if (sourceId != null && targetId != null) {
        rows.relationships.add(Arrays.asList(edge.getId(), sourceId, targetId, edge.getKind().name(), edge.getRole()));
      }
    }
  }

  private static void addMappingControlFindings(Cfihos20ReferenceDataMapping mapping, List<Finding> findings) {
    if (!mapping.isSourceDigestVerified()) {
      findings.add(blocker("RDL_DIGEST_NOT_VERIFIED", mapping.getSourceUri(),
          "The configured RDL digest was declared but not calculated from controlled bytes",
          "Use verifiedSource with the exact project RDL delivery"));
    }
    if (!mapping.isApprovedForProject()) {
      findings.add(blocker("MAPPING_NOT_PROJECT_APPROVED", mapping.getMappingRevision(),
          "The RDL mapping revision has not been approved for this project",
          "Record approval by the accountable project information-management authority"));
    }
  }

  private static String manifest(EngineeringGraph graph, Cfihos20ReferenceDataMapping mapping,
      Cfihos20HandoverReport report, Map<String, String> digests) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_cfihos_20_staging_manifest.v1");
    result.put("packageProfile", "NEQSIM_CONTROLLED_CFIHOS_2_0_STAGING_V1");
    result.put("projectId", graph.getProjectId());
    result.put("revision", graph.getRevision());
    result.put("cfihosVersion", "2.0");
    result.put("status", report.getStatus().name());
    result.put("referenceDataMapping", mapping.toMap());
    List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
    for (Map.Entry<String, String> item : digests.entrySet()) {
      Map<String, Object> file = new LinkedHashMap<String, Object>();
      file.put("file", item.getKey());
      file.put("sha256", item.getValue());
      file.put("mediaType", item.getKey().endsWith(".csv") ? "text/csv" : "application/json");
      files.add(file);
    }
    result.put("files", files);
    result.put("cfihosConformanceClaim", Boolean.FALSE);
    result.put("principalAcceptanceRequired", Boolean.TRUE);
    result.put("targetSystemTransformationRequired", Boolean.TRUE);
    return GSON.toJson(result);
  }

  private static Path writeCsv(Path file, List<String> headers, List<List<String>> rows) throws IOException {
    StringBuilder content = new StringBuilder();
    appendCsvRow(content, headers);
    for (List<String> row : rows) {
      appendCsvRow(content, row);
    }
    write(file, content.toString());
    return file;
  }

  private static void appendCsvRow(StringBuilder content, List<String> values) {
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        content.append(',');
      }
      content.append(csv(values.get(index)));
    }
    content.append('\n');
  }

  private static String csv(String value) {
    String text = value == null ? "" : value;
    if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
      return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    return text;
  }

  private static void write(Path file, String content) throws IOException {
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, String> digests(Map<String, Path> files) throws IOException {
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (Map.Entry<String, Path> file : files.entrySet()) {
      result.put(file.getKey(), digest(Files.readAllBytes(file.getValue())));
    }
    return result;
  }

  private static String digest(byte[] content) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
      StringBuilder result = new StringBuilder();
      for (byte value : hash) {
        result.append(String.format("%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static boolean isTagKind(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.EQUIPMENT || kind == EngineeringNode.Kind.LINE
        || kind == EngineeringNode.Kind.INSTRUMENT || kind == EngineeringNode.Kind.BOUNDARY;
  }

  private static boolean isScalar(Object value) {
    return value instanceof String || value instanceof Number || value instanceof Boolean;
  }

  private static List<String> gap(EngineeringNode node, String item, String type, String action) {
    return Arrays.asList(node.getId(), node.getKind().name(), item, type, action);
  }

  private static Finding blocker(String code, String sourceId, String message, String action) {
    return new Finding("BLOCKER", code, safe(sourceId), message, action);
  }

  private static Finding warning(String code, String sourceId, String message, String action) {
    return new Finding("WARNING", code, safe(sourceId), message, action);
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static final class Rows {
    private final List<List<String>> tags = new ArrayList<List<String>>();
    private final List<List<String>> equipment = new ArrayList<List<String>>();
    private final List<List<String>> properties = new ArrayList<List<String>>();
    private final List<List<String>> documents = new ArrayList<List<String>>();
    private final List<List<String>> relationships = new ArrayList<List<String>>();
    private final List<List<String>> unmapped = new ArrayList<List<String>>();
    private final List<Finding> findings = new ArrayList<Finding>();
  }
}
