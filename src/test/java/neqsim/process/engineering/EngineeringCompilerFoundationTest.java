package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.process.engineering.deliverables.EngineeringApprovalLedger;
import neqsim.process.engineering.deliverables.EngineeringDeliverableCompiler;
import neqsim.process.engineering.designcase.DesignCaseEngine;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.engineering.model.EngineeringCalculation;
import neqsim.process.engineering.model.EngineeringCalculationDag;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphBuilder;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.validation.EngineeringPackageValidationReport;
import neqsim.process.engineering.validation.EngineeringPackageValidator;
import neqsim.process.engineering.validation.EngineeringTopologyValidator;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies the canonical graph, design envelope and coordinated deliverable compiler. */
class EngineeringCompilerFoundationTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void executesCasesSelectsEnvelopeAndCompilesCoordinatedPackage() throws Exception {
    EngineeringProject project = createProject();
    EngineeringDesignEnvelope envelope = DesignCaseEngine.run(project.getProcessSystem(),
        project.getExecutableDesignCases(), project.getEngineeringMetrics());

    assertEquals(3, envelope.getCaseResults().size());
    assertEquals("FAILED", envelope.getCaseResults().get(2).getStatus());
    EngineeringDesignEnvelope.GoverningValue pressure = envelope.getGoverningValues().get("20-VG-001.pressure");
    assertNotNull(pressure);
    assertEquals("CASE-MAX", pressure.getDesignCaseId());
    assertEquals(75.0, pressure.getValue(), 0.1);
    assertEquals(55.0, project.getProcessSystem().getUnit("20-FEED-001").getPressure("bara"), 0.1);

    EngineeringDeliverableCompiler.CompilationResult result = EngineeringDeliverableCompiler.compile(project,
        temporaryDirectory);
    assertTrue(Files.isRegularFile(result.getEngineeringGraphFile()));
    assertTrue(Files.isRegularFile(result.getEngineeringConnectivityFile()));
    assertTrue(Files.isRegularFile(result.getEngineeringCalculationDagFile()));
    assertTrue(Files.isRegularFile(result.getEngineeringDesignCaseMatrixFile()));
    assertTrue(Files.isRegularFile(result.getEngineeringDisciplinePackageFile()));
    assertTrue(Files.isRegularFile(result.getEngineeringApprovalLedgerFile()));
    assertTrue(Files.isRegularFile(result.getDesignEnvelopeFile()));
    assertTrue(Files.isRegularFile(result.getEquipmentRegisterFile()));
    assertTrue(Files.isRegularFile(result.getLineRegisterFile()));
    assertTrue(Files.isRegularFile(result.getInstrumentRegisterFile()));
    assertTrue(Files.isRegularFile(result.getValidationReportFile()));
    assertTrue(Files.isRegularFile(temporaryDirectory.resolve("engineering-schema-catalog.json")));
    assertTrue(Files.isRegularFile(temporaryDirectory.resolve("schemas/engineering-model.schema.json")));
    assertTrue(Files.isRegularFile(result.getDexpiResult().getDexpi20File()));
    assertTrue(read(result.getEquipmentRegisterFile()).contains("20-VG-001"));
    assertTrue(read(result.getInstrumentRegisterFile()).contains("ENGINEERING_REQUIREMENT"));
    assertTrue(read(result.getDesignEnvelopeFile()).contains("CASE-MAX"));
    assertTrue(read(result.getEngineeringConnectivityFile()).contains("PROCESS_FLOW"));
    assertTrue(read(result.getEngineeringConnectivityFile()).contains("SIGNAL_FLOW"));
    assertTrue(read(result.getEngineeringCalculationDagFile()).contains("topologicalOrder"));
    assertTrue(read(result.getEngineeringCalculationDagFile()).contains("API 521"));
    assertTrue(read(result.getEngineeringDesignCaseMatrixFile()).contains("limitViolationCount"));
    assertTrue(read(result.getEngineeringDesignCaseMatrixFile()).contains("ABOVE_UPPER_LIMIT"));
    assertTrue(read(result.getEngineeringDisciplinePackageFile()).contains("PROCESS_SAFETY"));
    assertTrue(read(result.getEngineeringDisciplinePackageFile()).contains("MECHANICAL-EQUIPMENT-DATASHEETS"));
    assertTrue(read(result.getEngineeringApprovalLedgerFile()).contains("APPROVED"));
    assertTrue(result.getValidationReport().isValid());
    assertTrue(EngineeringPackageValidator.validatePackage(temporaryDirectory).isValid());
    assertNotNull(result.getEngineeringGraph().getNode("calculation:envelope-20-vg-001-pressure"));
    assertNotNull(
        result.getEngineeringGraph().getNode(EngineeringIds.nodeId(EngineeringNode.Kind.PORT, "20-FEED-001.outlet")));
    assertNotNull(
        result.getEngineeringGraph().getNode(EngineeringIds.nodeId(EngineeringNode.Kind.NOZZLE, "20-VG-001.inlet")));
    assertNotNull(result.getEngineeringGraph().getNode(
        EngineeringIds.nodeId(EngineeringNode.Kind.PIPE_SEGMENT, "MATERIAL:20-FEED-001.outlet->20-VG-001.inlet")));
    assertTrue(hasEdge(result.getEngineeringGraph(), EngineeringEdge.Kind.HAS_PORT));
    assertTrue(hasEdge(result.getEngineeringGraph(), EngineeringEdge.Kind.PROCESS_FLOW));
    assertTrue(hasEdge(result.getEngineeringGraph(), EngineeringEdge.Kind.SIGNAL_FLOW));
    assertTrue(hasEdge(result.getEngineeringGraph(), EngineeringEdge.Kind.MEASURES));
    assertNotNull(result.getEngineeringGraph()
        .getNode(EngineeringIds.nodeId(EngineeringNode.Kind.PROCESS_TAP, "20-PT-001.processTap")));
    assertNotNull(result.getEngineeringGraph()
        .getNode(EngineeringIds.nodeId(EngineeringNode.Kind.PIPE_SEGMENT, "BOUNDARY:20-FLARE-001")));
    assertTrue(hasEdge(result.getEngineeringGraph(), EngineeringEdge.Kind.DEPENDS_ON));
    assertTrue(hasEdge(result.getEngineeringGraph(), EngineeringEdge.Kind.APPROVES));
    assertNotNull(result.getEngineeringGraph()
        .getNode(EngineeringIds.nodeId(EngineeringNode.Kind.APPROVAL, "APPROVAL-20-VG-001-PROCESS")));
  }

  @Test
  void buildsStandardsAwareCalculationDagAndRejectsCycles() {
    EngineeringCalculation prerequisite = new EngineeringCalculation("CALC-A", "equipment:test", "Prerequisite")
        .setStatus(EngineeringCalculation.Status.CALCULATED).setResult(10.0, "bara").setStandardsRequired(true)
        .addStandardReference(
            new EngineeringCalculation.StandardReference("API 521", "2020", "4.4", "Relief design basis"));
    EngineeringCalculation dependent = new EngineeringCalculation("CALC-B", "equipment:test", "Dependent")
        .dependsOnCalculation("CALC-A");

    EngineeringCalculationDag dag = EngineeringCalculationDag.from(java.util.Arrays.asList(dependent, prerequisite));

    assertEquals("CALC-A", dag.getTopologicalOrder().get(0));
    assertEquals("CALC-B", dag.getTopologicalOrder().get(1));
    assertEquals(EngineeringCalculationDag.Readiness.READY, dag.getReadiness("CALC-B"));
    prerequisite.dependsOnCalculation("CALC-B");
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringCalculationDag.from(java.util.Arrays.asList(prerequisite, dependent)));
  }

  @Test
  void managesOptionalCasesAndIsolatesMetricFailures() {
    EngineeringProject project = createProject();
    project.addDesignCase(new EngineeringDesignCase("CASE-OPTIONAL-DISABLED", "Disabled optional study",
        EngineeringDesignCase.Type.CUSTOM, new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
          }
        }).setRequired(false).setEnabled(false).setCaseGroup("OPTIONAL-STUDIES").setPriority(1000));
    project.addEngineeringMetric(EngineeringMetric.equipmentPressure("UNKNOWN-EQUIPMENT"));

    EngineeringDesignEnvelope envelope = DesignCaseEngine.run(project.getProcessSystem(),
        project.getExecutableDesignCases(), project.getEngineeringMetrics());

    assertEquals(2, envelope.getPartialCaseCount());
    assertEquals(1, envelope.getFailedCaseCount());
    assertEquals(1, envelope.getSkippedCaseCount());
    assertTrue(envelope.getLimitViolationCount() > 0);
  }

  @Test
  void reportsIncompleteCanonicalPhysicalTopology() {
    EngineeringGraph graph = new EngineeringGraph("TOPOLOGY-TEST", "A");
    String projectId = EngineeringIds.nodeId(EngineeringNode.Kind.PROJECT, "TOPOLOGY-TEST");
    String segmentId = EngineeringIds.nodeId(EngineeringNode.Kind.PIPE_SEGMENT, "orphan-segment");
    graph.addNode(new EngineeringNode(projectId, EngineeringNode.Kind.PROJECT, "TOPOLOGY-TEST", "Topology test"));
    graph
        .addNode(new EngineeringNode(segmentId, EngineeringNode.Kind.PIPE_SEGMENT, "orphan-segment", "Orphan segment"));
    graph.addEdge(new EngineeringEdge(
        EngineeringIds.edgeId(EngineeringEdge.Kind.CONTAINS, projectId, segmentId, "physicalConnection"), projectId,
        segmentId, EngineeringEdge.Kind.CONTAINS, "physicalConnection"));

    EngineeringPackageValidationReport validation = EngineeringTopologyValidator.validate(graph);

    assertFalse(validation.isValid());
    assertTrue(hasFinding(validation, "ENG-TOPOLOGY-004"));
  }

  @Test
  void rejectsUnknownGraphVersionsAndReportsCrossArtifactErrors() throws Exception {
    EngineeringProject project = createProject();
    EngineeringDeliverableCompiler.CompilationResult result = EngineeringDeliverableCompiler.compile(project,
        temporaryDirectory);
    String graphJson = read(result.getEngineeringGraphFile());
    assertThrows(IllegalArgumentException.class, () -> EngineeringGraph
        .fromJson(graphJson.replace("neqsim_engineering_graph.v1", "neqsim_engineering_graph.v2")));
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringGraph.fromJson(graphJson.replace("\"revision\": \"A\"", "\"revision\": \"B\"")));

    JsonObject equipment = JsonParser.parseString(read(result.getEquipmentRegisterFile())).getAsJsonObject();
    equipment.addProperty("rowCount", equipment.get("rowCount").getAsInt() + 1);
    Files.write(result.getEquipmentRegisterFile(),
        new GsonBuilder().setPrettyPrinting().create().toJson(equipment).getBytes(StandardCharsets.UTF_8));
    EngineeringPackageValidationReport validation = EngineeringPackageValidator.validatePackage(temporaryDirectory);
    assertFalse(validation.isValid());
    assertTrue(hasFinding(validation, "ENG-SCHEMA-006"));
  }

  @Test
  void persistsGraphAndReportsRevisionImpact() throws Exception {
    EngineeringProject project = createProject();
    EngineeringGraph revisionA = EngineeringGraphBuilder.fromProject(project);
    Path snapshot = temporaryDirectory.resolve("revision-a.json");
    Files.write(snapshot, revisionA.toJson().getBytes(StandardCharsets.UTF_8));
    EngineeringGraph reloaded = EngineeringGraph.read(snapshot);
    assertTrue(revisionA.compareTo(reloaded).isEmpty());

    project.setRevision("B");
    String equipmentNode = EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, "20-VG-001");
    project
        .addCalculation(new EngineeringCalculation("20-VG-001-DESIGN-P", equipmentNode,
            "Maximum design-case pressure plus project margin")
            .setStatus(EngineeringCalculation.Status.REVIEW_REQUIRED).setResult(82.5, "bara")
            .setDesignCaseId("CASE-MAX").addInput(new EngineeringCalculation.Input("maximum case pressure",
                equipmentNode, 75.0, "bara", "DESIGN-CASE-REGISTER-REV-B"))
            .addEvidenceReference("PROCESS-DESIGN-BASIS-REV-B"));
    EngineeringGraph revisionB = EngineeringGraphBuilder.fromProject(project);
    EngineeringGraphDiff diff = reloaded.compareTo(revisionB);

    assertFalse(diff.isEmpty());
    assertTrue(diff.getAddedNodeIds().contains("calculation:20-vg-001-design-p"));
    assertTrue(diff.getImpactedNodeIds().contains(equipmentNode));
    String approvalLedger = new GsonBuilder().create()
        .toJson(EngineeringApprovalLedger.build(project, revisionB, diff));
    assertTrue(approvalLedger.contains("REVALIDATION_REQUIRED"));
  }

  private static EngineeringProject createProject() {
    SystemInterface fluid = new SystemSrkEos(298.15, 55.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(30000.0, "kg/hr");
    Separator separator = new Separator("20-VG-001", feed);
    ProcessSystem process = new ProcessSystem("Engineering compiler test process");
    process.add(feed);
    process.add(separator);
    PressureTransmitter pressureTransmitter = new PressureTransmitter("20-PT-001", feed);
    pressureTransmitter.setTag("20-PT-001");
    process.add(pressureTransmitter);
    process.connect("20-FEED-001", "outlet", "20-VG-001", "inlet", ProcessConnection.ConnectionType.MATERIAL);
    process.connect("20-VG-001", "pressureSignal", "20-FEED-001", "flowSetpoint",
        ProcessConnection.ConnectionType.SIGNAL);
    process.run();

    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Engineering compiler test", process)
        .projectId("TEST-ENGINEERING-PROJECT").registerProposedInstruments(false).build().setRevision("A");
    project.addBoundary(new EngineeringBoundary("20-FLARE-001", "20-VG-001", EngineeringBoundary.Type.FLARE_HEADER));
    project.addDesignCase(caseAtPressure("CASE-NORMAL", "Normal operation", 55.0));
    project.addDesignCase(caseAtPressure("CASE-MAX", "Maximum production", 75.0));
    project.addDesignCase(new EngineeringDesignCase("CASE-INVALID", "Invalid controlled case",
        EngineeringDesignCase.Type.CUSTOM, new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            throw new IllegalStateException("Missing controlled feed composition");
          }
        }));
    project.addEngineeringMetric(
        EngineeringMetric.equipmentPressure("20-VG-001").setAcceptanceRange(null, Double.valueOf(70.0)));
    project.addEngineeringMetric(EngineeringMetric.equipmentTemperature("20-VG-001"));
    project.addEngineeringMetric(EngineeringMetric.equipmentInletMassFlow("20-VG-001"));
    String separatorNode = EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, "20-VG-001");
    project
        .addCalculation(new EngineeringCalculation("20-VG-001-RELIEF-BASIS", separatorNode,
            "Establish maximum credible relief pressure basis").setStatus(EngineeringCalculation.Status.CALCULATED)
            .setResult(75.0, "bara").setStandardsRequired(true)
            .addStandardReference(new EngineeringCalculation.StandardReference("API 521", "2020", "4.4",
                "Credible overpressure scenario and design pressure basis"))
            .addEvidenceReference("PROCESS-DESIGN-BASIS"));
    project.addCalculation(new EngineeringCalculation("20-VG-001-RELIEF-REVIEW", separatorNode,
        "Review relief protection against the controlled pressure basis").dependsOnCalculation("20-VG-001-RELIEF-BASIS")
        .setStandardsRequired(true).addStandardReference(new EngineeringCalculation.StandardReference("API 520 Part I",
            "2020", "5", "Pressure-relieving device sizing and selection")));
    project.addApprovalRecord(new EngineeringApprovalRecord("APPROVAL-20-VG-001-PROCESS", separatorNode, "PROCESS",
        EngineeringApprovalRecord.Status.APPROVED, "Accountable Process Engineer", "PROCESS-DESIGN-REVIEW-001",
        "2026-07-16"));
    return project;
  }

  private static EngineeringDesignCase caseAtPressure(final String id, String name, final double pressureBara) {
    return new EngineeringDesignCase(id, name, EngineeringDesignCase.Type.CUSTOM,
        new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            Stream feed = (Stream) process.getUnit("20-FEED-001");
            feed.setPressure(pressureBara, "bara");
          }
        }).addInput(new EngineeringDesignCase.Input("feed pressure", pressureBara, "bara", "PROCESS-DESIGN-BASIS"));
  }

  private static String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private static boolean hasFinding(EngineeringPackageValidationReport report, String code) {
    for (EngineeringPackageValidationReport.Finding finding : report.getFindings()) {
      if (code.equals(finding.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasEdge(EngineeringGraph graph, EngineeringEdge.Kind kind) {
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (edge.getKind() == kind) {
        return true;
      }
    }
    return false;
  }
}
