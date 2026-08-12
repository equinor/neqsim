package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.pid.NorsokPidRuleCatalog;
import neqsim.process.engineering.pid.PidCompletenessReport;
import neqsim.process.engineering.pid.PidCompletenessValidator;
import neqsim.process.engineering.pid.PidDesignBasis;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidDesignSynthesizer;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidProposalStatus;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModelGraphvizExporter;
import neqsim.process.processmodel.diagram.EngineeringDiagramReferenceFixtures;
import neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end golden evidence for the public DEXPI/P&amp;ID reference cases. */
class EngineeringDiagramReferenceCasesTest {
  private static final double MASS_BALANCE_RELATIVE_TOLERANCE = 1.0e-7;

  @TempDir
  Path temporaryDirectory;

  @Test
  void simpleReferenceCasePreservesDeterministicExchangeAndProposalBoundaries() throws Exception {
    assertSystemCase(EngineeringDiagramReferenceFixtures.simpleTrain(),
        EngineeringDiagramReferenceFixtures.simpleTrain(), "simple");
  }

  @Test
  void branchedReferenceCasePreservesDeterministicExchangeAndProposalBoundaries() throws Exception {
    assertSystemCase(EngineeringDiagramReferenceFixtures.branchedSeparatorCompressionTrain(),
        EngineeringDiagramReferenceFixtures.branchedSeparatorCompressionTrain(), "branched");
  }

  @Test
  void multiAreaReferenceCasePreservesPlantTopologyAndProteusSheets() throws Exception {
    EngineeringDiagramReferenceFixtures.ModelCase first = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    EngineeringDiagramReferenceFixtures.ModelCase second = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    first.getProcessModel().run();
    second.getProcessModel().run();
    assertMassBalance(first.getFeed(), first.getProducts());
    assertMassBalance(second.getFeed(), second.getProducts());

    ProcessDiagramGraphAdapter.Result firstCanonical = ProcessDiagramGraphAdapter
        .fromProcessModel(first.getProcessModel(), first.getCaseId(), "A", "NORMAL-001");
    ProcessDiagramGraphAdapter.Result secondCanonical = ProcessDiagramGraphAdapter
        .fromProcessModel(second.getProcessModel(), second.getCaseId(), "A", "NORMAL-001");
    assertEquals(firstCanonical.getFingerprint(), secondCanonical.getFingerprint());
    assertEquals(firstCanonical.getGraphJson(), secondCanonical.getGraphJson());
    assertTrue(firstCanonical.isComplete(), diagnosticCodes(firstCanonical).toString());
    assertEquals(first.getMaterialConnections(), canonicalMaterialConnections(firstCanonical.getGraph()));
    assertEquals(first.getAreaNames(), new ArrayList<String>(first.getProcessModel().getProcessSystemNames()));

    ByteArrayOutputStream firstCombined = new ByteArrayOutputStream();
    ByteArrayOutputStream secondCombined = new ByteArrayOutputStream();
    DexpiXmlWriter.write(first.getProcessModel(), firstCombined);
    DexpiXmlWriter.write(second.getProcessModel(), secondCombined);
    assertDeterministicProteusXml(firstCombined.toString(StandardCharsets.UTF_8.name()),
        secondCombined.toString(StandardCharsets.UTF_8.name()));

    Path firstSheets = temporaryDirectory.resolve("multi-area-first");
    Path secondSheets = temporaryDirectory.resolve("multi-area-second");
    List<File> firstFiles = DexpiXmlWriter.writeSheets(first.getProcessModel(), firstSheets.toFile());
    List<File> secondFiles = DexpiXmlWriter.writeSheets(second.getProcessModel(), secondSheets.toFile());
    assertEquals(first.getAreaNames().size(), firstFiles.size());
    assertEquals(fileNames(firstFiles), fileNames(secondFiles));
    for (int index = 0; index < firstFiles.size(); index++) {
      assertDeterministicProteusXml(read(firstFiles.get(index).toPath()), read(secondFiles.get(index).toPath()));
      assertTrue(read(firstFiles.get(index).toPath()).contains("<PlantModel"));
    }

    ProcessModelGraphvizExporter firstGraphviz = new ProcessModelGraphvizExporter(first.getProcessModel());
    ProcessModelGraphvizExporter secondGraphviz = new ProcessModelGraphvizExporter(second.getProcessModel());
    String firstDot = firstGraphviz.toDot();
    assertEquals(firstDot, secondGraphviz.toDot());
    assertDotEdgeBefore(firstDot, "Inlet::30-FEED-001\" -> \"Inlet::30-XV-001",
        "Inlet::30-XV-001\" -> \"Inlet::30-VA-001");
    assertDotEdgeBefore(firstDot, "Inlet::30-XV-001\" -> \"Inlet::30-VA-001",
        "Inlet::30-VA-001\" -> \"Inlet::30-SP-001");
    assertDotEdgeBefore(firstDot, "Inlet::30-VA-001\" -> \"Inlet::30-SP-001",
        "Inlet::30-SP-001\" -> \"Compression::31-KA-001");
    assertDotEdgeBefore(firstDot, "Inlet::30-SP-001\" -> \"Compression::31-KA-001",
        "Inlet::30-SP-001\" -> \"Flare::40-PL-001");
    assertDotEdgeBefore(firstDot, "Inlet::30-SP-001\" -> \"Flare::40-PL-001",
        "Compression::31-KA-001\" -> \"Compression::31-HA-001");
    assertDotEdgeBefore(firstDot, "Compression::31-KA-001\" -> \"Compression::31-HA-001",
        "Compression::31-HA-001\" -> \"Export::32-PL-001");
    assertEquals(first.getAreaNames(), new ArrayList<String>(firstGraphviz.toAreaDots().keySet()));

    List<EngineeringProject> projects = NorsokOffshoreEngineeringBuilder.fromProcessModel("Multi-area DEXPI reference",
        first.getProcessModel(), false);
    assertEquals(first.getAreaNames().size(), projects.size());
    int proposedElementCount = 0;
    for (EngineeringProject project : projects) {
      PidDesignModel proposal = PidDesignSynthesizer.synthesize(project,
          new PidDesignBasis("REFERENCE-PID-PROPOSAL", areaCode(project.getProcessSystem().getName())),
          NorsokPidRuleCatalog.completeProposals());
      proposedElementCount += proposal.getElements().size();
      assertProposalBoundary(proposal, false);
    }
    assertTrue(proposedElementCount > 0);
  }

  private static void assertDotEdgeBefore(String dot, String firstEdge, String secondEdge) {
    int firstIndex = dot.indexOf(firstEdge);
    int secondIndex = dot.indexOf(secondEdge);
    assertTrue(firstIndex >= 0, firstEdge);
    assertTrue(secondIndex >= 0, secondEdge);
    assertTrue(firstIndex < secondIndex, firstEdge + " should precede " + secondEdge);
  }

  @Test
  void committedManifestPinsSyntheticProvenanceAndQualificationBoundaries() throws Exception {
    Path manifestPath = new File(getClass().getResource("/dexpi/reference-cases/reference-case-manifest.json").toURI())
        .toPath();
    JsonObject manifest = new JsonParser().parse(read(manifestPath)).getAsJsonObject();
    JsonArray cases = manifest.getAsJsonArray("cases");

    assertEquals("neqsim_engineering_diagram_reference_cases.v1", manifest.get("schemaVersion").getAsString());
    assertEquals("SYNTHETIC_PUBLIC_DATA", manifest.get("provenance").getAsString());
    assertEquals("REVIEW_REQUIRED", manifest.get("engineeringState").getAsString());
    assertFalse(manifest.get("fitnessForConstruction").getAsBoolean());
    assertEquals(3, cases.size());
    assertEquals("DEXPI-REF-SIMPLE", cases.get(0).getAsJsonObject().get("caseId").getAsString());
    assertEquals("DEXPI-REF-BRANCHED", cases.get(1).getAsJsonObject().get("caseId").getAsString());
    assertEquals("DEXPI-REF-MULTI-AREA", cases.get(2).getAsJsonObject().get("caseId").getAsString());
    assertTrue(manifest.toString().contains("DEXPI_PROCESS_MULTI_AREA_UNSUPPORTED"));
    assertTrue(manifest.toString().contains("NAMED_CAE_QUALIFICATION_REQUIRED"));
  }

  private void assertSystemCase(EngineeringDiagramReferenceFixtures.SystemCase first,
      EngineeringDiagramReferenceFixtures.SystemCase second, String filePrefix) throws Exception {
    first.getProcessSystem().run();
    second.getProcessSystem().run();
    assertMassBalance(first.getFeed(), first.getProducts());
    assertMassBalance(second.getFeed(), second.getProducts());

    ProcessDiagramGraphAdapter.Result firstCanonical = ProcessDiagramGraphAdapter
        .fromProcessSystem(first.getProcessSystem(), first.getCaseId(), "A", "NORMAL-001");
    ProcessDiagramGraphAdapter.Result secondCanonical = ProcessDiagramGraphAdapter
        .fromProcessSystem(second.getProcessSystem(), second.getCaseId(), "A", "NORMAL-001");
    assertEquals(firstCanonical.getFingerprint(), secondCanonical.getFingerprint());
    assertEquals(firstCanonical.getGraphJson(), secondCanonical.getGraphJson());
    assertTrue(firstCanonical.isComplete(), diagnosticCodes(firstCanonical).toString());
    assertEquals(first.getMaterialConnections(), canonicalMaterialConnections(firstCanonical.getGraph()));

    Path firstProcess = temporaryDirectory.resolve(filePrefix + "-first.process.dexpi.xml");
    Path secondProcess = temporaryDirectory.resolve(filePrefix + "-second.process.dexpi.xml");
    Dexpi20ProcessTopologyAssessment.Report firstProcessReport = Dexpi20ProcessModelWriter
        .writeAndAssessTopology(first.getProcessSystem(), firstProcess.toFile(), first.getCaseId(), "A", "NORMAL-001");
    Dexpi20ProcessTopologyAssessment.Report secondProcessReport = Dexpi20ProcessModelWriter.writeAndAssessTopology(
        second.getProcessSystem(), secondProcess.toFile(), second.getCaseId(), "A", "NORMAL-001");
    assertTrue(firstProcessReport.isSchemaProfileAndSupportedTopologyValid(),
        firstProcessReport.getDiagnostics().toString());
    assertEquals(first.getMaterialConnections(), firstProcessReport.getCanonicalMaterialConnections());
    assertEquals(read(firstProcess), read(secondProcess));
    assertEquals(firstProcessReport.toJson(), secondProcessReport.toJson());
    assertDistinctConnectionPorts(firstProcessReport);
    assertTrue(diagnosticCodes(firstProcessReport).contains("DEXPI_PROCESS_MULTI_AREA_UNSUPPORTED"));
    assertTrue(diagnosticCodes(firstProcessReport).contains("DEXPI_PROCESS_DOCUMENT_SEMANTICS_UNSUPPORTED"));
    assertTrue(diagnosticCodes(firstProcessReport).contains("DEXPI_PROCESS_GRAPHICS_UNSUPPORTED"));

    Path firstPlant = temporaryDirectory.resolve(filePrefix + "-first.plant.dexpi.xml");
    Path secondPlant = temporaryDirectory.resolve(filePrefix + "-second.plant.dexpi.xml");
    Dexpi20ConformanceAssessment.Report firstPlantReport = Dexpi20XmlWriter.writeAndAssess(first.getProcessSystem(),
        firstPlant.toFile());
    Dexpi20ConformanceAssessment.Report secondPlantReport = Dexpi20XmlWriter.writeAndAssess(second.getProcessSystem(),
        secondPlant.toFile());
    assertTrue(firstPlantReport.isSchemaAndProfileConformant(), firstPlantReport.getErrors().toString());
    assertTrue(secondPlantReport.isSchemaAndProfileConformant(), secondPlantReport.getErrors().toString());
    assertEquals(read(firstPlant), read(secondPlant));
    assertEquals(firstPlantReport.toJson(), secondPlantReport.toJson());

    ByteArrayOutputStream firstProteus = new ByteArrayOutputStream();
    ByteArrayOutputStream secondProteus = new ByteArrayOutputStream();
    DexpiXmlWriter.write(first.getProcessSystem(), firstProteus);
    DexpiXmlWriter.write(second.getProcessSystem(), secondProteus);
    assertDeterministicProteusXml(firstProteus.toString(StandardCharsets.UTF_8.name()),
        secondProteus.toString(StandardCharsets.UTF_8.name()));

    EngineeringProject firstProject = NorsokOffshoreEngineeringBuilder
        .from("DEXPI reference case " + first.getCaseId(), first.getProcessSystem()).projectId(first.getCaseId())
        .build();
    EngineeringProject secondProject = NorsokOffshoreEngineeringBuilder
        .from("DEXPI reference case " + second.getCaseId(), second.getProcessSystem()).projectId(second.getCaseId())
        .build();
    PidDesignModel firstProposal = PidDesignSynthesizer.synthesize(firstProject,
        new PidDesignBasis("REFERENCE-PID-PROPOSAL", filePrefix.equals("simple") ? "10" : "20"),
        NorsokPidRuleCatalog.completeProposals());
    PidDesignModel secondProposal = PidDesignSynthesizer.synthesize(secondProject,
        new PidDesignBasis("REFERENCE-PID-PROPOSAL", filePrefix.equals("simple") ? "10" : "20"),
        NorsokPidRuleCatalog.completeProposals());
    assertEquals(firstProposal.toMap(), secondProposal.toMap());
    assertProposalBoundary(firstProposal, true);
  }

  private static void assertMassBalance(StreamInterface feed, List<StreamInterface> products) {
    double feedMassFlow = feed.getFlowRate("kg/hr");
    double productMassFlow = 0.0;
    for (StreamInterface product : products) {
      productMassFlow += product.getFlowRate("kg/hr");
    }
    assertEquals(feedMassFlow, productMassFlow,
        Math.max(1.0e-6, Math.abs(feedMassFlow) * MASS_BALANCE_RELATIVE_TOLERANCE));
  }

  private static void assertDistinctConnectionPorts(Dexpi20ProcessTopologyAssessment.Report report) {
    Set<String> ports = new LinkedHashSet<String>();
    for (Dexpi20ProcessTopologyAssessment.ExportedConnection connection : report.getExportedConnections()) {
      assertTrue(ports.add(connection.getSourcePortId()), "Source port reused: " + connection.getSourcePortId());
      assertTrue(ports.add(connection.getTargetPortId()), "Target port reused: " + connection.getTargetPortId());
    }
  }

  private static void assertProposalBoundary(PidDesignModel proposal, boolean requireElements) {
    if (requireElements) {
      assertFalse(proposal.getElements().isEmpty());
    }
    for (PidElement element : proposal.getElements()) {
      assertEquals(PidProposalStatus.REVIEW_REQUIRED, element.getStatus());
    }
    PidCompletenessReport completeness = PidCompletenessValidator.validate(proposal);
    assertFalse(completeness.isReadyForApproval());
    assertEquals(Boolean.FALSE, completeness.toMap().get("fitnessForConstruction"));
  }

  private static List<String> canonicalMaterialConnections(EngineeringGraph graph) {
    List<String> connections = new ArrayList<String>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.PIPE_SEGMENT) {
        connections.add(String.valueOf(node.getProperties().get("sourceEquipment")) + "->"
            + String.valueOf(node.getProperties().get("targetEquipment")));
      }
    }
    Collections.sort(connections);
    return connections;
  }

  private static Set<String> diagnosticCodes(ProcessDiagramGraphAdapter.Result result) {
    Set<String> codes = new LinkedHashSet<String>();
    for (ProcessDiagramGraphAdapter.Diagnostic diagnostic : result.getDiagnostics()) {
      codes.add(diagnostic.getCode());
    }
    return codes;
  }

  private static Set<String> diagnosticCodes(Dexpi20ProcessTopologyAssessment.Report report) {
    Set<String> codes = new LinkedHashSet<String>();
    for (Dexpi20ProcessTopologyAssessment.Diagnostic diagnostic : report.getDiagnostics()) {
      codes.add(diagnostic.getCode());
    }
    return codes;
  }

  private static List<String> fileNames(List<File> files) {
    List<String> names = new ArrayList<String>();
    for (File file : files) {
      names.add(file.getName());
    }
    return names;
  }

  private static String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private static void assertDeterministicProteusXml(String first, String second) {
    assertTrue(first.contains("<PlantInformation"));
    assertTrue(first.contains(" Date=\""));
    assertTrue(first.contains(" Time=\""));
    assertTrue(second.contains("<PlantInformation"));
    assertTrue(second.contains(" Date=\""));
    assertTrue(second.contains(" Time=\""));
    assertEquals(normalizeProteusEmissionMetadata(first), normalizeProteusEmissionMetadata(second));
  }

  private static String normalizeProteusEmissionMetadata(String xml) {
    return xml.replaceFirst(" Date=\"[^\"]+\"", " Date=\"<generated-date>\"").replaceFirst(" Time=\"[^\"]+\"",
        " Time=\"<generated-time>\"");
  }

  private static String areaCode(String areaName) {
    if (areaName.startsWith("Inlet")) {
      return "30";
    }
    if (areaName.startsWith("Compression")) {
      return "31";
    }
    if (areaName.startsWith("Export")) {
      return "32";
    }
    return "40";
  }
}
