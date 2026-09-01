package neqsim.process.engineering.handover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.engineering.handover.Cfihos20ReferenceDataMapping.Edition;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;

/** Verifies deterministic and fail-closed CFIHOS 2.0 staging handover. */
class Cfihos20HandoverExporterTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void exportsControlledDeterministicPackage() throws Exception {
    EngineeringGraph graph = graph();
    Path rdl = temporaryDirectory.resolve("project-cfihos-rdl.csv");
    Files.write(rdl, "controlled example RDL bytes\n".getBytes(StandardCharsets.UTF_8));
    Cfihos20ReferenceDataMapping mapping = Cfihos20ReferenceDataMapping.builder(Edition.CORE)
        .verifiedSource("urn:project:cfihos-rdl:2.0:rev-a", rdl).approvedBy("Project Information Manager", "MAP-A")
        .mapNode("equipment:20-vg-001", "CFIHOS-TAG-CLASS-ID", "CFIHOS-EQUIPMENT-CLASS-ID")
        .mapProperty("designPressure", "CFIHOS-PROPERTY-ID", "CFIHOS-UOM-ID")
        .mapProperty("service", "CFIHOS-SERVICE-PROPERTY-ID", null)
        .mapDocument("20-VG-001-datasheet.pdf", "CFIHOS-DOCUMENT-TYPE-ID").build();

    Cfihos20HandoverExporter.Result first = Cfihos20HandoverExporter.export(graph, mapping,
        temporaryDirectory.resolve("first"));
    Cfihos20HandoverExporter.Result second = Cfihos20HandoverExporter.export(graph, mapping,
        temporaryDirectory.resolve("second"));

    assertTrue(first.getReport().isReadyForPrincipalTransformation());
    assertEquals(first.getReport().getFileDigests(), second.getReport().getFileDigests());
    assertTrue(Files.isRegularFile(first.getManifestFile()));
    assertTrue(Files.isRegularFile(first.getAssessmentFile()));
    assertTrue(read(first.getManifestFile()).contains("\"cfihosConformanceClaim\": false"));
    assertTrue(read(first.getAssessmentFile()).contains("READY_FOR_PRINCIPAL_TRANSFORMATION"));
    assertTrue(read(temporaryDirectory.resolve("first/cfihos-tags.csv")).contains("\"Separator, inlet\""));
    assertTrue(read(temporaryDirectory.resolve("first/cfihos-properties.csv")).contains("\"gas, condensate\""));
    assertTrue(read(temporaryDirectory.resolve("first/cfihos-relationships.csv")).contains("EQUIPMENT_REALIZES_TAG"));
    assertEquals("source_node_id,source_kind,source_item,gap_type,required_action\n", read(first.getUnmappedFile()));
  }

  @Test
  void missingMappingsAndUnverifiedRdlBlockReadiness() throws Exception {
    Cfihos20ReferenceDataMapping mapping = Cfihos20ReferenceDataMapping.builder(Edition.EXTENDED)
        .source("urn:unverified:rdl", repeat("0", 64)).build();

    Cfihos20HandoverExporter.Result result = Cfihos20HandoverExporter.export(graph(), mapping,
        temporaryDirectory.resolve("incomplete"));

    assertFalse(result.getReport().isReadyForPrincipalTransformation());
    String assessment = read(result.getAssessmentFile());
    assertTrue(assessment.contains("RDL_DIGEST_NOT_VERIFIED"));
    assertTrue(assessment.contains("MAPPING_NOT_PROJECT_APPROVED"));
    assertTrue(assessment.contains("MISSING_NODE_CLASSIFICATION"));
    assertTrue(assessment.contains("MISSING_DOCUMENT_TYPE"));
  }

  @Test
  void approvedMappingsRequireControlledRdlEvidence() {
    assertThrows(IllegalStateException.class,
        () -> Cfihos20ReferenceDataMapping.builder(Edition.CORE).approvedBy("Authority", "A").build());
    assertThrows(IllegalArgumentException.class,
        () -> Cfihos20ReferenceDataMapping.builder(Edition.CORE).source("urn:rdl", "not-a-digest"));
  }

  private static EngineeringGraph graph() {
    EngineeringGraph graph = new EngineeringGraph("PROJECT-20", "A");
    EngineeringNode project = new EngineeringNode("project:project-20", EngineeringNode.Kind.PROJECT, "PROJECT-20",
        "Project 20");
    EngineeringNode equipment = new EngineeringNode("equipment:20-vg-001", EngineeringNode.Kind.EQUIPMENT, "20-VG-001",
        "Separator, inlet").putProperty("designPressure", Double.valueOf(80.0))
        .putProperty("service", "gas, condensate");
    EngineeringNode document = new EngineeringNode("document:20-vg-001-datasheet-pdf", EngineeringNode.Kind.DOCUMENT,
        "20-VG-001-datasheet.pdf", "Separator datasheet").putProperty("file", "documents/20-VG-001-datasheet.pdf");
    graph.addNode(project).addNode(equipment).addNode(document);
    graph.addEdge(new EngineeringEdge(
        EngineeringIds.edgeId(EngineeringEdge.Kind.REFERENCES, equipment.getId(), document.getId(), "datasheet"),
        equipment.getId(), document.getId(), EngineeringEdge.Kind.REFERENCES, "datasheet"));
    return graph;
  }

  private static String read(Path file) throws Exception {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  private static String repeat(String value, int count) {
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < count; index++) {
      result.append(value);
    }
    return result.toString();
  }
}
