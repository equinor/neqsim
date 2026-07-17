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
import neqsim.process.engineering.EngineeringSimulationRunner;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.designcase.DesignCaseEngine;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.designcase.EngineeringDesignCaseMatrix;
import neqsim.process.engineering.dexpi.DexpiEngineeringExporter;
import neqsim.process.engineering.dexpi.EngineeringDexpiRoundTripQualifier;
import neqsim.process.engineering.model.EngineeringCalculation;
import neqsim.process.engineering.model.EngineeringCalculationDag;
import neqsim.process.engineering.model.EngineeringDiagramLayout;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphBuilder;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.model.EngineeringProvenance;
import neqsim.process.engineering.production.EngineeringProductionReadinessAssessment;
import neqsim.process.engineering.production.EngineeringQualificationPlan;
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
  private static final String[] COORDINATED_ARTIFACTS = new String[] { "process-design-basis.json",
      "equipment-datasheets.json", "valve-list.json", "io-list.json", "alarm-trip-schedule.json",
      "shutdown-narratives.json", "psv-datasheets.json", "flare-blowdown-report.json", "utility-summary.json",
      "materials-selection-report.json", "unresolved-engineering-actions.json", "revision-impact-report.json",
      "engineering-production-readiness.json", "engineering-qualification-plan.json",
      "engineering-vertical-slice-execution-manifest.json" };

  private EngineeringDeliverableCompiler() {
  }

  /** Paths and in-memory snapshots produced by one compiler execution. */
  public static final class CompilationResult {
    private final Path outputDirectory;
    private final Path engineeringGraphFile;
    private final Path engineeringConnectivityFile;
    private final Path engineeringCalculationDagFile;
    private final Path engineeringDesignCaseMatrixFile;
    private final Path engineeringDisciplinePackageFile;
    private final Path engineeringApprovalLedgerFile;
    private final Path engineeringDexpiRoundTripReportFile;
    private final Path engineeringAutomationPlanFile;
    private final Path designEnvelopeFile;
    private final Path equipmentRegisterFile;
    private final Path lineRegisterFile;
    private final Path instrumentRegisterFile;
    private final Path productionReadinessFile;
    private final Path verticalSliceQualificationFile;
    private final Path verticalSliceExecutionManifestFile;
    private final Path qualificationPlanFile;
    private final Path compilerManifestFile;
    private final Path validationReportFile;
    private final Path revisionDiffFile;
    private final EngineeringGraph engineeringGraph;
    private final EngineeringDesignEnvelope designEnvelope;
    private final DexpiEngineeringExporter.ExportResult dexpiResult;
    private final EngineeringPackageValidationReport validationReport;

    CompilationResult(Path outputDirectory, Path engineeringGraphFile, Path engineeringConnectivityFile,
        Path engineeringCalculationDagFile, Path engineeringDesignCaseMatrixFile, Path engineeringDisciplinePackageFile,
        Path engineeringApprovalLedgerFile, Path engineeringDexpiRoundTripReportFile,
        Path engineeringAutomationPlanFile, Path designEnvelopeFile, Path equipmentRegisterFile, Path lineRegisterFile,
        Path instrumentRegisterFile, Path productionReadinessFile, Path verticalSliceQualificationFile,
        Path verticalSliceExecutionManifestFile, Path qualificationPlanFile, Path compilerManifestFile,
        Path validationReportFile, Path revisionDiffFile, EngineeringGraph engineeringGraph,
        EngineeringDesignEnvelope designEnvelope, DexpiEngineeringExporter.ExportResult dexpiResult,
        EngineeringPackageValidationReport validationReport) {
      this.outputDirectory = outputDirectory;
      this.engineeringGraphFile = engineeringGraphFile;
      this.engineeringConnectivityFile = engineeringConnectivityFile;
      this.engineeringCalculationDagFile = engineeringCalculationDagFile;
      this.engineeringDesignCaseMatrixFile = engineeringDesignCaseMatrixFile;
      this.engineeringDisciplinePackageFile = engineeringDisciplinePackageFile;
      this.engineeringApprovalLedgerFile = engineeringApprovalLedgerFile;
      this.engineeringDexpiRoundTripReportFile = engineeringDexpiRoundTripReportFile;
      this.engineeringAutomationPlanFile = engineeringAutomationPlanFile;
      this.designEnvelopeFile = designEnvelopeFile;
      this.equipmentRegisterFile = equipmentRegisterFile;
      this.lineRegisterFile = lineRegisterFile;
      this.instrumentRegisterFile = instrumentRegisterFile;
      this.productionReadinessFile = productionReadinessFile;
      this.verticalSliceQualificationFile = verticalSliceQualificationFile;
      this.verticalSliceExecutionManifestFile = verticalSliceExecutionManifestFile;
      this.qualificationPlanFile = qualificationPlanFile;
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

    public Path getEngineeringDisciplinePackageFile() {
      return engineeringDisciplinePackageFile;
    }

    public Path getEngineeringApprovalLedgerFile() {
      return engineeringApprovalLedgerFile;
    }

    public Path getEngineeringDexpiRoundTripReportFile() {
      return engineeringDexpiRoundTripReportFile;
    }

    public Path getEngineeringAutomationPlanFile() {
      return engineeringAutomationPlanFile;
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

    public Path getProductionReadinessFile() {
      return productionReadinessFile;
    }

    public Path getVerticalSliceQualificationFile() {
      return verticalSliceQualificationFile;
    }

    public Path getVerticalSliceExecutionManifestFile() {
      return verticalSliceExecutionManifestFile;
    }

    public Path getQualificationPlanFile() {
      return qualificationPlanFile;
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

    if (!project.getEngineeringDesignModules().isEmpty()) {
      EngineeringSimulationRunner.run(project,
          EngineeringCaseRunOptions.builder().parallelism(1).requireConvergence(true).build());
    }

    EngineeringDesignEnvelope envelope = null;
    List<EngineeringCalculation> envelopeCalculations = new ArrayList<EngineeringCalculation>();
    if (!project.getExecutableDesignCases().isEmpty() && !project.getEngineeringMetrics().isEmpty()) {
      envelope = DesignCaseEngine.run(project.getEngineeringProcessSystem(), project.getExecutableDesignCases(),
          project.getEngineeringMetrics());
      envelopeCalculations.addAll(envelope.toCalculations());
    }
    List<EngineeringCalculation> dagCalculations = new ArrayList<EngineeringCalculation>(project.getCalculations());
    dagCalculations.addAll(envelopeCalculations);
    EngineeringCalculationDag calculationDag = EngineeringCalculationDag.from(dagCalculations);
    EngineeringGraph graph = EngineeringGraphBuilder.fromProject(project, envelopeCalculations);
    addDocumentNodes(graph, project);

    Path diagramLayoutFile = outputDirectory.resolve("engineering-diagram-layout.json");
    write(diagramLayoutFile, GSON.toJson(EngineeringDiagramLayout.build(graph)));

    DexpiEngineeringExporter.ExportResult dexpiResult = DexpiEngineeringExporter.export(project, outputDirectory);
    Path dexpiRoundTripReportFile = outputDirectory.resolve("engineering-dexpi-roundtrip-report.json");
    write(dexpiRoundTripReportFile, GSON.toJson(EngineeringDexpiRoundTripQualifier.qualify(graph,
        dexpiResult.getDexpi20File(), dexpiResult.getDexpiFile(), dexpiResult.getPyDexpiFile())));
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
    Path disciplinePackageFile = outputDirectory.resolve("engineering-discipline-package.json");
    write(disciplinePackageFile, GSON.toJson(EngineeringDisciplinePackage.build(project, graph, envelope)));
    Path automationPlanFile = outputDirectory.resolve("engineering-automation-plan.json");
    write(automationPlanFile, GSON.toJson(EngineeringAutomationPlan.build(project, graph, envelope)));
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
    Map<String, Object> coordinated = EngineeringCoordinatedPackage.build(project, graph, envelope, diff);
    for (Map.Entry<String, Object> artifact : coordinated.entrySet()) {
      write(outputDirectory.resolve(artifact.getKey()), GSON.toJson(artifact.getValue()));
    }
    Path productionReadinessFile = outputDirectory.resolve("engineering-production-readiness.json");
    write(productionReadinessFile, GSON.toJson(
        EngineeringProductionReadinessAssessment.assess(project, project.getProductionReadinessBasis()).toMap()));
    Path orchestrationFile = outputDirectory.resolve("engineering-discipline-orchestration.json");
    Map<String, Object> orchestration = new LinkedHashMap<String, Object>();
    orchestration.put("schemaVersion", EngineeringSchemaCatalog.DISCIPLINE_ORCHESTRATION);
    orchestration.put("schemaUri",
        EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.DISCIPLINE_ORCHESTRATION));
    orchestration.put("projectId", project.getProjectId());
    orchestration.put("revision", project.getRevision());
    orchestration.put("configuration",
        project.getProductionReadinessBasis() == null
            || project.getProductionReadinessBasis().getAutoConfigurationResult() == null ? null
                : project.getProductionReadinessBasis().getAutoConfigurationResult().toMap());
    orchestration.put("status",
        project.getProductionReadinessBasis() != null
            && project.getProductionReadinessBasis().getAutoConfigurationResult() != null
            && project.getProductionReadinessBasis().getAutoConfigurationResult().isExecutionReady()
                ? "EXECUTED_REVIEW_REQUIRED"
                : "NOT_EXECUTION_READY");
    orchestration.put("governance",
        "Dependency completion does not replace HAZOP/LOPA, vendor validation or accountable approval");
    write(orchestrationFile, GSON.toJson(orchestration));
    Path verticalSliceQualificationFile = outputDirectory.resolve("engineering-vertical-slice-qualification.json");
    write(verticalSliceQualificationFile, GSON.toJson(verticalSliceQualification(project)));
    Path verticalSliceExecutionManifestFile = outputDirectory
        .resolve("engineering-vertical-slice-execution-manifest.json");
    write(verticalSliceExecutionManifestFile, GSON.toJson(verticalSliceExecutionManifest(project)));
    Path qualificationPlanFile = outputDirectory.resolve("engineering-qualification-plan.json");
    write(qualificationPlanFile,
        GSON.toJson(EngineeringQualificationPlan.build(project, project.getProductionReadinessBasis())));
    Path approvalLedgerFile = outputDirectory.resolve("engineering-approval-ledger.json");
    write(approvalLedgerFile, GSON.toJson(EngineeringApprovalLedger.build(project, graph, diff)));
    Path compilerManifest = outputDirectory.resolve("engineering-compiler-manifest.json");
    write(compilerManifest, compilerManifest(project, graph, envelope, diff, schemaArtifacts));
    Path validationFile = outputDirectory.resolve("engineering-validation-report.json");
    write(validationFile, new EngineeringPackageValidationReport().toJson());
    EngineeringPackageValidationReport validation = EngineeringPackageValidator.validatePackage(outputDirectory);
    write(validationFile, validation.toJson());
    if (!validation.isValid()) {
      throw new EngineeringPackageValidationException(validationFile, validation);
    }
    DexpiEngineeringExporter.refreshPackageManifest(outputDirectory);
    return new CompilationResult(outputDirectory, graphFile, connectivityFile, calculationDagFile, designCaseMatrixFile,
        disciplinePackageFile, approvalLedgerFile, dexpiRoundTripReportFile, automationPlanFile, envelopeFile,
        equipmentFile, lineFile, instrumentFile, productionReadinessFile, verticalSliceQualificationFile,
        verticalSliceExecutionManifestFile, qualificationPlanFile, compilerManifest, validationFile, diffFile, graph,
        envelope, dexpiResult, validation);
  }

  private static void addDocumentNodes(EngineeringGraph graph, EngineeringProject project) {
    String projectNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.PROJECT, project.getProjectId());
    String[] documents = new String[] { "plant.dexpi.xml", "plant-proteus.xml", "plant-pydexpi.xml",
        "engineering-manifest.json", "engineering-calculations.json", "cause-and-effect.json",
        "interoperability-report.json", "engineering-model.json", "engineering-connectivity.json",
        "engineering-calculation-dag.json", "engineering-design-case-matrix.json",
        "engineering-discipline-package.json", "engineering-approval-ledger.json",
        "engineering-dexpi-roundtrip-report.json", "engineering-automation-plan.json", "design-case-envelope.json",
        "equipment-register.json", "line-register.json", "instrument-register.json", "engineering-diagram-layout.json",
        "engineering-compiler-manifest.json", "engineering-schema-catalog.json", "engineering-validation-report.json",
        "process-design-basis.json", "equipment-datasheets.json", "valve-list.json", "io-list.json",
        "alarm-trip-schedule.json", "shutdown-narratives.json", "psv-datasheets.json", "flare-blowdown-report.json",
        "utility-summary.json", "materials-selection-report.json", "unresolved-engineering-actions.json",
        "revision-impact-report.json", "engineering-production-readiness.json", "engineering-qualification-plan.json",
        "engineering-discipline-orchestration.json", "engineering-vertical-slice-qualification.json",
        "engineering-vertical-slice-execution-manifest.json" };
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
    for (ProcessEquipmentInterface unit : project.getEngineeringProcessSystem().getUnitOperations()) {
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
    for (MeasurementDeviceInterface instrument : project.getEngineeringProcessSystem().getMeasurementDevices()) {
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
    artifacts.add("engineering-discipline-package.json");
    artifacts.add("engineering-approval-ledger.json");
    artifacts.add("engineering-dexpi-roundtrip-report.json");
    artifacts.add("engineering-automation-plan.json");
    artifacts.add("design-case-envelope.json");
    artifacts.add("equipment-register.json");
    artifacts.add("line-register.json");
    artifacts.add("instrument-register.json");
    artifacts.add("engineering-diagram-layout.json");
    artifacts.add("engineering-schema-catalog.json");
    artifacts.add("engineering-validation-report.json");
    artifacts.add("engineering-discipline-orchestration.json");
    artifacts.add("engineering-vertical-slice-qualification.json");
    artifacts.add("engineering-vertical-slice-execution-manifest.json");
    artifacts.add("plant.dexpi.xml");
    artifacts.add("plant-proteus.xml");
    artifacts.add("plant-pydexpi.xml");
    artifacts.add("engineering-manifest.json");
    artifacts.add("engineering-calculations.json");
    artifacts.add("cause-and-effect.json");
    artifacts.add("interoperability-report.json");
    for (String artifact : COORDINATED_ARTIFACTS) {
      artifacts.add(artifact);
    }
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

  private static Map<String, Object> verticalSliceQualification(EngineeringProject project) {
    if (project.getLatestVerticalSliceQualification() != null) {
      return project.getLatestVerticalSliceQualification().toMap();
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", EngineeringSchemaCatalog.VERTICAL_SLICE_QUALIFICATION);
    result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.VERTICAL_SLICE_QUALIFICATION));
    result.put("projectId", project.getProjectId());
    result.put("projectRevision", project.getRevision());
    result.put("policyId", "NOT_CONFIGURED");
    result.put("policyRevision", "NOT_CONFIGURED");
    Map<String, Object> gate = new LinkedHashMap<String, Object>();
    gate.put("passed", Boolean.FALSE);
    gate.put("findings", Collections.singletonList("Production vertical-slice qualification was not configured"));
    gate.put("requiredAction", "Run ProductionVerticalSliceSimulator with a controlled acceptance policy");
    result.put("gates", Collections.singletonMap("CONFIGURATION", gate));
    result.put("failedGates", Collections.singletonList("CONFIGURATION"));
    result.put("qualifiedForControlledPilot", Boolean.FALSE);
    result.put("qualifiedForFeedSupport", Boolean.FALSE);
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("finalEngineeringApprovalGranted", Boolean.FALSE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    result.put("governance", "No vertical-slice qualification has been executed");
    return result;
  }

  private static Map<String, Object> verticalSliceExecutionManifest(EngineeringProject project) {
    if (project.getLatestVerticalSliceExecutionManifest() != null) {
      return project.getLatestVerticalSliceExecutionManifest().toMap();
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", EngineeringSchemaCatalog.VERTICAL_SLICE_EXECUTION_MANIFEST);
    result.put("schemaUri",
        EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.VERTICAL_SLICE_EXECUTION_MANIFEST));
    result.put("status", "NOT_CONFIGURED");
    result.put("projectId", project.getProjectId());
    result.put("projectRevision", project.getRevision());
    result.put("designPolicyId", "NOT_CONFIGURED");
    result.put("designPolicyRevision", "NOT_CONFIGURED");
    result.put("designPolicyFingerprint", String.format("%064d", Integer.valueOf(0)));
    result.put("qualificationPolicyId", "NOT_CONFIGURED");
    result.put("qualificationPolicyRevision", "NOT_CONFIGURED");
    result.put("qualificationPolicyFingerprint", String.format("%064d", Integer.valueOf(0)));
    result.put("inputFingerprint", String.format("%064d", Integer.valueOf(0)));
    result.put("caseDefinitions", Collections.emptyList());
    result.put("dynamicScenarios", Collections.emptyList());
    result.put("coupledStudies", Collections.emptyList());
    result.put("standards", Collections.emptyList());
    result.put("evidence", Collections.emptyList());
    result.put("preflightReady", Boolean.FALSE);
    result.put("preflightBlockers", Collections.singletonList("Production vertical slice was not configured"));
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
