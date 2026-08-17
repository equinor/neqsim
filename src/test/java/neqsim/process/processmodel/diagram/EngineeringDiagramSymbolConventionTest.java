package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolConvention;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolShape;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import org.junit.jupiter.api.Test;

class EngineeringDiagramSymbolConventionTest {
  @Test
  void rendersDeterministicConfiguredShapesAndReportsExplicitFallbacks() {
    EngineeringDiagramDocumentSet documents = conventionDocuments();
    SymbolConvention equipmentConvention = new SymbolConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.DIAMOND,
        "#123456", "#fef3c7", "project-symbols:reference", EvidenceState.PROPOSED, "", "", "2026-08-17T11:00:00Z", "A");
    EngineeringDiagramConventionRegister conventions = new EngineeringDiagramConventionRegister()
        .withConvention(equipmentConvention);

    NativeEngineeringDiagramRenderer.Result baseline = new NativeEngineeringDiagramRenderer(documents).render();
    NativeEngineeringDiagramRenderer renderer = new NativeEngineeringDiagramRenderer(documents, conventions);
    NativeEngineeringDiagramRenderer.Result first = renderer.render();
    NativeEngineeringDiagramRenderer.Result second = renderer.render();
    String svg = first.getSvgBySheetId().values().iterator().next();

    assertEquals(first.getSvgBySheetId(), second.getSvgBySheetId());
    assertArrayEquals(first.getPdf(), second.getPdf());
    assertEquals(first.getVisualFingerprintsBySheetId(), second.getVisualFingerprintsBySheetId());
    assertTrue(svg.contains("<polygon points="));
    assertTrue(svg.contains("stroke=\"#123456\" fill=\"#fef3c7\""));
    assertTrue(svg.contains("data-semantic-id=\"equipment:separator\""));
    assertNotEquals(baseline.getVisualFingerprintsBySheetId(), first.getVisualFingerprintsBySheetId());
    assertTrue(hasDiagnostic(first, "DIAGRAM_RENDER_SYMBOL_CONVENTION_PROPOSAL", "equipment:separator"));
    assertTrue(hasDiagnostic(first, "DIAGRAM_RENDER_SYMBOL_FALLBACK", "boundary:feed"));
    assertTrue(first.isComplete());
  }

  @Test
  void preservesLegacyBytesWhenTheConventionRegisterIsEmpty() {
    EngineeringDiagramDocumentSet documents = conventionDocuments();

    NativeEngineeringDiagramRenderer.Result implicitDefaults = new NativeEngineeringDiagramRenderer(documents).render();
    NativeEngineeringDiagramRenderer.Result explicitDefaults = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.SheetFormat.A3_LANDSCAPE, new EngineeringDiagramConventionRegister()).render();

    assertEquals(implicitDefaults.getSvgBySheetId(), explicitDefaults.getSvgBySheetId());
    assertArrayEquals(implicitDefaults.getPdf(), explicitDefaults.getPdf());
    assertEquals(implicitDefaults.getVisualFingerprintsBySheetId(), explicitDefaults.getVisualFingerprintsBySheetId());
    assertFalse(hasDiagnostic(explicitDefaults, "DIAGRAM_RENDER_SYMBOL_FALLBACK", "boundary:feed"));
  }

  @Test
  void keepsConventionsSortedImmutableAndDefensivelyCopied() {
    SymbolConvention equipment = reviewedConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.HEXAGON);
    SymbolConvention boundary = reviewedConvention(EngineeringNode.Kind.BOUNDARY, SymbolShape.RECTANGLE);
    List<SymbolConvention> source = new ArrayList<SymbolConvention>(Arrays.asList(equipment, boundary));

    EngineeringDiagramConventionRegister register = new EngineeringDiagramConventionRegister(source);
    source.clear();

    assertEquals(2, register.getConventions().size());
    assertEquals(EngineeringNode.Kind.BOUNDARY, register.getConventions().get(0).getNodeKind());
    assertEquals(SymbolShape.HEXAGON, register.getSymbolConvention(EngineeringNode.Kind.EQUIPMENT).getShape());
    assertThrows(UnsupportedOperationException.class, () -> register.getConventions().clear());
  }

  @Test
  void rejectsAmbiguousOrInvalidProjectConventionEvidence() {
    SymbolConvention first = reviewedConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.RECTANGLE);
    SymbolConvention duplicate = reviewedConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.DIAMOND);

    assertThrows(IllegalArgumentException.class,
        () -> new EngineeringDiagramConventionRegister(Arrays.asList(first, duplicate)));
    assertThrows(IllegalArgumentException.class,
        () -> new SymbolConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.RECTANGLE, "black", "#ffffff",
            "project-symbols:reference", EvidenceState.PROPOSED, "", "", "2026-08-17T11:00:00Z", "A"));
    assertThrows(IllegalArgumentException.class,
        () -> new SymbolConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.RECTANGLE, "#000000", "#ffffff",
            "project-symbols:reference", EvidenceState.REVIEWED, "", "", "2026-08-17T11:00:00Z", "A"));
  }

  private static EngineeringDiagramDocumentSet conventionDocuments() {
    EngineeringGraph graph = new EngineeringGraph("CONVENTION-PLANT", "A");
    graph.addNode(
        new EngineeringNode("equipment:separator", EngineeringNode.Kind.EQUIPMENT, "separator", "Separator block"));
    graph.addNode(new EngineeringNode("boundary:feed", EngineeringNode.Kind.BOUNDARY, "feed", "Feed boundary"));
    return EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-CONVENTION-001", "Project convention reference",
        ContentProfile.PFD);
  }

  private static SymbolConvention reviewedConvention(EngineeringNode.Kind kind, SymbolShape shape) {
    return new SymbolConvention(kind, shape, "#1f2937", "#eff6ff", "project-symbols:reviewed", EvidenceState.REVIEWED,
        "Process discipline", "review:project-symbols", "2026-08-17T11:00:00Z", "B");
  }

  private static boolean hasDiagnostic(NativeEngineeringDiagramRenderer.Result result, String code, String subjectId) {
    for (NativeEngineeringDiagramRenderer.Diagnostic diagnostic : result.getDiagnostics()) {
      if (code.equals(diagnostic.getCode()) && subjectId.equals(diagnostic.getSubjectId())) {
        assertFalse(diagnostic.getMessage().isEmpty());
        return true;
      }
    }
    return false;
  }
}
