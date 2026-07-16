package neqsim.process.engineering.deliverables;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.designcase.DesignCaseEngine;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.designcase.EngineeringDesignCaseMatrix;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.model.EngineeringCalculation;
import neqsim.process.engineering.model.EngineeringCalculationDag;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphBuilder;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.model.EngineeringProvenance;
import neqsim.process.engineering.validation.EngineeringPackageValidationException;
import neqsim.process.engineering.validation.EngineeringPackageValidationReport;
import neqsim.process.engineering.validation.EngineeringPackageValidator;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/** Compiles one governed project into a coordinated engineering package and canonical graph snapshot. */
public final class EngineeringDeliverableCompiler {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private EngineeringDeliverableCompiler() {
  }

  /** Paths and in-memory snapshots produced by one compiler execution. */
  public static final class CompilationResult {
    private final Path outputDirectory;
    private final Path engineeringGraphFile;
    private final Path engineeringConnectivityFile;
    private final Path engineeringCalculationDagFile;
    private final Path engineeringDesignCaseMatrixFile;
    private final Path designEnvelopeFile;
    private final Path equipmentRegisterFile;
    private final Path lineRegisterFile;
    private final Path instrumentRegisterFile;
    private final Path compilerManifestFile;
    private final Path validationReportFile;
    private final Path revisionDiffFile;
    private final EngineeringGraph engineeringGraph;
    private final EngineeringDesignEnvelope designEnvelope;
    private final DexpiEngineeringExporter.ExportResult dexpiResult;
    private final EngineeringPackageValidationReport validationReport;

    CompilationResult(Path outputDirectory, Path engineeringGraphFile, Path engineeringConnectivityFile,
        Path engineeringCalculationDagFile, Path engineeringDesignCaseMatrixFile, Path designEnvelopeFile,
        Path equipmentRegisterFile, Path lineRegisterFile, Path instrumentRegisterFile, Path compilerManifestFile,
        Path validationReportFile, Path revisionDiffFile, EngineeringGraph engineeringGraph,
        EngineeringDesignEnvelope designEnvelope, DexpiEngineeringExporter.ExportResult dexpiResult,
        EngineeringPackageValidationReport validationReport) {
      this.outputDirectory = outputDirectory;
      this.engineeringGraphFile = engineeringGraphFile;
      this.engineeringConnectivityFile = engineeringConnectivityFile;
      this.engineeringCalculationDagFile = engineeringCalculationDagFile;
      this.engineeringDesignCaseMatrixFile = engineeringDesignCaseMatrixFile;
      this.designEnvelopeFile = designEnvelopeFile;
      this.equipmentRegisterFile = equipmentRegisterFile;
      this.lineRegisterFile = lineRegisterFile;
      this.instrumentRegisterFile = instrumentRegisterFile;
      this.compilerManifestFile = compilerManifestFile;
      this.validationReportFile = validationReportFile;
      this.revisionDiffFile = revisionDiffFile;
      this.engineeringGraph = engineeringGraph;
      this.designEnvelope = designEnvelope;
      this.dexpiResult = dexpiResult;
      this.validationReport = validationReport;
    }

    public Path getOutputDirectory() {
      return outputDirectory;
    }

    public Path getEngineeringGraphFile() {
      return engineeringGraphFile;
    }

    public Path getEngineeringConnectivityFile() {
      return engineeringConnectivityFile;
    }

    public Path getEngineeringCalculationDagFile() {
      return engineeringCalculationDagFile;
    }

    public Path getEngineeringDesignCaseMatrixFile() {
      return engineeringDesignCaseMatrixFile;
    }

    public Path getDesignEnvelopeFile() {
      return designEnvelopeFile;
    }

    public Path getEquipmentRegisterFile() {
      return equipmentRegisterFile;
    }

    public Path getLineRegisterFile() {
      return lineRegisterFile;
    }

    public Path getInstrumentRegisterFile() {
      return instrumentRegisterFile;
    }

    public Path getCompilerManifestFile() {
      return compilerManifestFile;
    }

    public Path getValidationReportFile() {
      return validationReportFile;
    }

    public Path getRevisionDiffFile() {
      return revisionDiffFile;
    }

    public EngineeringGraph getEngineeringGraph() {
      return engineeringGraph;
    }

    public EngineeringDesignEnvelope getDesignEnvelope() {
      return designEnvelope;
    }

    public DexpiEngineeringExporter.ExportResult getDexpiResult() {
      return dexpiResult;
    }

    public EngineeringPackageValidationReport getValidationReport() {
      return validationReport;
    }
  }

  public static CompilationResult compile(EngineeringProject project, Path outputDirectory) throws IOException {
    return compile(project, outputDirectory, null);
  }

  /**
   * Compiles all current engineering deliverables and optionally compares them with a previous graph revision.
   *
   * @param project governed engineering project
   * @param outputDirectory target package directory
   * @param baselineGraph optional prior revision for change-impact reporting
   * @return compilation result
   * @throws IOException if any artifact cannot be written
   */
  public static CompilationResult compile(EngineeringProject project, Path outputDirectory,
      EngineeringGraph baselineGraph) throws IOException {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    if (outputDirectory == null) {
      throw new IllegalArgumentException("outputDirectory must not be null");
    }
    Files.createDirectories(outputDirectory);
    List<String> schemaArtifacts = EngineeringSchemaCatalog.writeSchemas(outputDirectory);

    EngineeringDesignEnvelope envelope = null;
    List<EngineeringCalculation> envelopeCalculations = new ArrayList<EngineeringCalculation>();
    if (!project.getExecutableDesignCases().isEmpty() && !project.getEngineeringMetrics().isEmpty()) {
      envelope = DesignCaseEngine.run(project.getProcessSystem(), project.getExecutableDesignCases(),
          project.getEngineeringMetrics());
      envelopeCalculations.addAll(envelope.toCalculations());
    }
    List<EngineeringCalculation> dagCalculations = new ArrayList<EngineeringCalculation>(project.getCalculations());
    dagCalculations.addAll(envelopeCalculations);
    EngineeringCalculationDag calculationDag = EngineeringCalculationDag.from(dagCalculations);
    EngineeringGraph graph = EngineeringGraphBuilder.fromProject(project, envelopeCalculations);
    addDocumentNodes(graph, project);

    DexpiEngineeringExporter.ExportResult dexpiResult = DexpiEngineeringExporter.export(project, outputDirectory);
    Path graphFile = outputDirectory.resolve("engineering-model.json");
    write(graphFile, graph.toJson());
    Path connectivityFile = outputDirectory.resolve("engineering-connectivity.json");
    write(connectivityFile, connectivityJson(graph));
    Path calculationDagFile = outputDirectory.resolve("engineering-calculation-dag.json");
    write(calculationDagFile, GSON.toJson(calculationDag.toMap(project.getProjectId(), project.getRevision())));
    Path designCaseMatrixFile = outputDirectory.resolve("engineering-design-case-matrix.json");
    EngineeringDesignCaseMatrix designCaseMatrix = new EngineeringDesignCaseMatrix(project.getExecutableDesignCases(),
        project.getEngineeringMetrics(), envelope);
    write(designCaseMatrixFile, GSON.toJson(designCaseMatrix.toMap(project.getProjectId(), project.getRevision())));
    Path envelopeFile = outputDirectory.resolve("design-case-envelope.json");
    write(envelopeFile, envelopeJson(project, envelope));
    Path equipmentFile = outputDirectory.resolve("equipment-register.json");
    write(equipmentFile, registerJson("neqsim_equipment_register.v1", equipmentRegister(project, envelope)));
    Path lineFile = outputDirectory.resolve("line-register.json");
    write(lineFile, registerJson("neqsim_line_register.v1", lineRegister(project)));
    Path instrumentFile = outputDirectory.resolve("instrument-register.json");
    write(instrumentFile, registerJson("neqsim_instrument_register.v1", instrumentRegister(project)));

    Path diffFile = null;
    EngineeringGraphDiff diff = null;
    if (baselineGraph != null) {
      diff = baselineGraph.compareTo(graph);
      diffFile = outputDirectory.resolve("engineering-revision-diff.json");
      write(diffFile, diff.toJson());
    }
    Path compilerManifest = outputDirectory.resolve("engineering-compiler-manifest.json");
    write(compilerManifest, compilerManifest(project, graph, envelope, diff, schemaArtifacts));
    Path validationFile = outputDirectory.resolve("engineering-validation-report.json");
    write(validationFile, new EngineeringPackageValidationReport().toJson());
    EngineeringPackageValidationReport validation = EngineeringPackageValidator.validatePackage(outputDirectory);
    write(validationFile, validation.toJson());
    if (!validation.isValid()) {
      throw new EngineeringPackageValidationException(validationFile, validation);
    }
    return new CompilationResult(outputDirectory, graphFile, connectivityFile, calculationDagFile, designCaseMatrixFile,
        envelopeFile, equipmentFile, lineFile, instrumentFile, compilerManifest, validationFile, diffFile, graph,
        envelope, dexpiResult, validation);
  }

  private static void addDocumentNodes(EngineeringGraph graph, EngineeringProject project) {
    String projectNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.PROJECT, project.getProjectId());
    String[] documents = new String[] { "plant.dexpi.xml", "plant-proteus.xml", "plant-pydexpi.xml",
        "engineering-manifest.json", "engineering-calculations.json", "cause-and-effect.json",
        "interoperability-report.json", "engineering-model.json", "engineering-connectivity.json",
        "engineering-calculation-dag.json", "engineering-design-case-matrix.json", "design-case-envelope.json",
        "equipment-register.json", "line-register.json", "instrument-register.json",
        "engineering-compiler-manifest.json", "engineering-schema-catalog.json", "engineering-validation-report.json" };
    for (String document : documents) {
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.DOCUMENT, document);
      graph.addNode(new EngineeringNode(nodeId, EngineeringNode.Kind.DOCUMENT, document, document)
          .putProperty("file", document).putProperty("revision", project.getRevision())
          .addProvenance(new EngineeringProvenance("COMPILER", "EngineeringDeliverableCompiler")
              .setMethod("Generated from canonical EngineeringProject")));
      graph.addEdge(new EngineeringEdge(
          EngineeringIds.edgeId(EngineeringEdge.Kind.GENERATED_FROM, nodeId, projectNodeId, "project"), nodeId,
          projectNodeId, EngineeringEdge.Kind.GENERATED_FROM, "project"));
    }
  }

  private static String envelopeJson(EngineeringProject project, EngineeringDesignEnvelope envelope) {
    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", EngineeringSchemaCatalog.DESIGN_CASE_ENVELOPE);
    document.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.DESIGN_CASE_ENVELOPE));
    document.put("projectId", project.getProjectId());
    document.put("revision", project.getRevision());
    if (envelope == null) {
      document.put("status", "NOT_CONFIGURED");
      document.put("message", "Add executable design cases and engineering metrics to calculate an envelope");
      document.put("caseResults", Collections.emptyList());
      document.put("governingValues", Collections.emptyList());
    } else {
      document.put("status",
          envelope.getGoverningValues().isEmpty() ? "NOT_CALCULATED_ALL_CASES_FAILED"
              : envelope.hasCaseFailures() ? "CALCULATED_WITH_CASE_FAILURES_REVIEW_REQUIRED"
                  : "CALCULATED_REVIEW_REQUIRED");
      document.putAll(envelope.toMap());
    }
    return GSON.toJson(document);
  }

  private static String connectivityJson(EngineeringGraph graph) {
    Map<String, Boolean> includedNodeIds = new LinkedHashMap<String, Boolean>();
    List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (isPhysicalEdge(edge.getKind())) {
        edges.add(edge.toMap());
        includedNodeIds.put(edge.getSourceId(), Boolean.TRUE);
        includedNodeIds.put(edge.getTargetId(), Boolean.TRUE);
      }
    }
    List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
    int connectionCount = 0;
    int unresolvedBoundaryCount = 0;
    for (EngineeringNode node : graph.getNodes().values()) {
      if (includedNodeIds.containsKey(node.getId())) {
        nodes.add(node.toMap());
        if (node.getKind() == EngineeringNode.Kind.PIPE_SEGMENT
            || node.getKind() == EngineeringNode.Kind.SIGNAL_CONNECTION
            || node.getKind() == EngineeringNode.Kind.ENERGY_CONNECTION) {
          connectionCount++;
        }
      }
      if (node.getKind() == EngineeringNode.Kind.BOUNDARY
          && Boolean.FALSE.equals(node.getProperties().get("resolved"))) {
        unresolvedBoundaryCount++;
      }
    }
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("nodeCount", Integer.valueOf(nodes.size()));
    summary.put("edgeCount", Integer.valueOf(edges.size()));
    summary.put("connectionCount", Integer.valueOf(connectionCount));
    summary.put("unresolvedBoundaryCount", Integer.valueOf(unresolvedBoundaryCount));
    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", EngineeringSchemaCatalog.CONNECTIVITY);
    document.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.CONNECTIVITY));
    document.put("projectId", graph.getProjectId());
    document.put("revision", graph.getRevision());
    document.put("graphFingerprint", graph.toMap().get("fingerprint"));
    document.put("summary", summary);
    document.put("nodes", nodes);
    document.put("edges", edges);
    return GSON.toJson(document);
  }

  private static boolean isPhysicalEdge(EngineeringEdge.Kind kind) {
    return kind == EngineeringEdge.Kind.HAS_PORT || kind == EngineeringEdge.Kind.PROCESS_FLOW
        || kind == EngineeringEdge.Kind.SIGNAL_FLOW || kind == EngineeringEdge.Kind.ENERGY_FLOW
        || kind == EngineeringEdge.Kind.PART_OF_LINE || kind == EngineeringEdge.Kind.MEASURES;
  }

  private static List<Map<String, Object>> equipmentRegister(EngineeringProject project,
      EngineeringDesignEnvelope envelope) {
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit == null || unit instanceof Stream) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("equipmentTag", unit.getName());
      row.put("equipmentType", unit.getClass().getSimpleName());
      row.put("javaClass", unit.getClass().getName());
      row.put("graphNodeId", EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, unit.getName()));
      row.put("designConditionsStatus",
          unit.getDesignConditions() == null || unit.getDesignConditions().isEmpty() ? "MISSING" : "DECLARED");
      if (unit.getDesignConditions() != null && !unit.getDesignConditions().isEmpty()) {
        row.put("designConditions", unit.getDesignConditions());
      }
      List<Map<String, Object>> governing = new ArrayList<Map<String, Object>>();
      if (envelope != null) {
        for (EngineeringDesignEnvelope.GoverningValue value : envelope.getGoverningValues().values()) {
          if (unit.getName().equals(value.getMetric().getSubjectTag())) {
            governing.add(value.toMap());
          }
        }
      }
      row.put("governingDesignValues", governing);
      row.put("approvalStatus", "REVIEW_REQUIRED");
      rows.add(row);
    }
    return rows;
  }

  private static List<Map<String, Object>> lineRegister(EngineeringProject project) {
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (LineDesignInput input : project.getLineDesignInputs()) {
      Map<String, Object> row = new LinkedHashMap<String, Object>(input.toMap());
      row.put("graphNodeId", EngineeringIds.nodeId(EngineeringNode.Kind.LINE, input.getLineTag()));
      row.put("approvalStatus", input.getMissingFields().isEmpty() ? "REVIEW_REQUIRED" : "INCOMPLETE");
      rows.add(row);
    }
    return rows;
  }

  private static List<Map<String, Object>> instrumentRegister(EngineeringProject project) {
    Map<String, Map<String, Object>> rows = new LinkedHashMap<String, Map<String, Object>>();
    for (MeasurementDeviceInterface instrument : project.getProcessSystem().getMeasurementDevices()) {
      String tag = instrument.getTag();
      if (tag == null || tag.trim().isEmpty()) {
        tag = instrument.getName();
      }
      if (tag == null || tag.trim().isEmpty()) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("instrumentTag", tag);
      row.put("name", instrument.getName());
      row.put("unit", instrument.getUnit());
      row.put("source", "PROCESS_SYSTEM");
      row.put("graphNodeId", EngineeringIds.nodeId(EngineeringNode.Kind.INSTRUMENT, tag));
      row.put("approvalStatus", "SIMULATION_MODEL");
      rows.put(tag, row);
    }
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (!isInstrumentRequirement(requirement)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("instrumentTag", requirement.getId());
      row.put("name", requirement.getTitle());
      row.put("equipmentTag", requirement.getEquipmentTag());
      row.put("functionType", requirement.getType().name());
      row.put("source", "ENGINEERING_REQUIREMENT");
      row.put("requirementGraphNodeId", EngineeringIds.nodeId(EngineeringNode.Kind.REQUIREMENT, requirement.getId()));
      row.put("silTarget", requirement.getSilTarget());
      row.put("approvalStatus", requirement.getApprovalStatus().name());
      rows.put(requirement.getId(), row);
    }
    return new ArrayList<Map<String, Object>>(rows.values());
  }

  private static boolean isInstrumentRequirement(EngineeringRequirement requirement) {
    return requirement.getType() == EngineeringRequirement.Type.CONTROL
        || requirement.getType() == EngineeringRequirement.Type.INSTRUMENT
        || requirement.getType() == EngineeringRequirement.Type.ALARM
        || requirement.getType() == EngineeringRequirement.Type.TRIP
        || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS;
  }

  private static String registerJson(String schemaVersion, List<Map<String, Object>> rows) {
    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", schemaVersion);
    document.put("schemaUri", EngineeringSchemaCatalog.schemaUri(schemaVersion));
    document.put("rowCount", rows.size());
    document.put("rows", rows);
    return GSON.toJson(document);
  }

  private static String compilerManifest(EngineeringProject project, EngineeringGraph graph,
      EngineeringDesignEnvelope envelope, EngineeringGraphDiff diff, List<String> schemaArtifacts) {
    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", EngineeringSchemaCatalog.COMPILER_MANIFEST);
    document.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.COMPILER_MANIFEST));
    document.put("projectId", project.getProjectId());
    document.put("projectName", project.getName());
    document.put("revision", project.getRevision());
    document.put("graphFingerprint", graph.toMap().get("fingerprint"));
    document.put("designEnvelopeStatus",
        envelope == null ? "NOT_CONFIGURED"
            : envelope.getGoverningValues().isEmpty() ? "NOT_CALCULATED_ALL_CASES_FAILED"
                : envelope.hasCaseFailures() ? "CALCULATED_WITH_CASE_FAILURES_REVIEW_REQUIRED"
                    : "CALCULATED_REVIEW_REQUIRED");
    document.put("revisionDiffStatus", diff == null ? "NOT_REQUESTED" : diff.isEmpty() ? "NO_CHANGES" : "CHANGES");
    List<String> artifacts = new ArrayList<String>();
    artifacts.add("engineering-model.json");
    artifacts.add("engineering-connectivity.json");
    artifacts.add("engineering-calculation-dag.json");
    artifacts.add("engineering-design-case-matrix.json");
    artifacts.add("design-case-envelope.json");
    artifacts.add("equipment-register.json");
    artifacts.add("line-register.json");
    artifacts.add("instrument-register.json");
    artifacts.add("engineering-schema-catalog.json");
    artifacts.add("engineering-validation-report.json");
    artifacts.add("plant.dexpi.xml");
    artifacts.add("plant-proteus.xml");
    artifacts.add("plant-pydexpi.xml");
    artifacts.add("engineering-manifest.json");
    artifacts.add("engineering-calculations.json");
    artifacts.add("cause-and-effect.json");
    artifacts.add("interoperability-report.json");
    if (diff != null) {
      artifacts.add("engineering-revision-diff.json");
    }
    for (String schemaArtifact : schemaArtifacts) {
      if (!artifacts.contains(schemaArtifact)) {
        artifacts.add(schemaArtifact);
      }
    }
    document.put("artifacts", artifacts);
    document.put("schemas", EngineeringSchemaCatalog.manifestEntries());
    document.put("governance", "Generated values and documents remain review-required until discipline approval");
    return GSON.toJson(document);
  }

  private static void write(Path file, String content) throws IOException {
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }
}
