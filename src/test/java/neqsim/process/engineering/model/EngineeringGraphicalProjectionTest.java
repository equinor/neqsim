package neqsim.process.engineering.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolConvention;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolShape;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Primitive;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.PrimitiveType;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Unit;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.VerificationStatus;

class EngineeringGraphicalProjectionTest {
  @Test
  void deterministicProjectionRetainsParallelSemanticConnections() {
    EngineeringGraph graph = graph();

    EngineeringGraphicalProjection first = EngineeringGraphicalProjectionBuilder.build(graph,
        new EngineeringDiagramConventionRegister(), "DOC-PFD-001", VerificationStatus.PROPOSAL);
    EngineeringGraphicalProjection second = EngineeringGraphicalProjectionBuilder.build(graph,
        new EngineeringDiagramConventionRegister(), "DOC-PFD-001", VerificationStatus.PROPOSAL);

    assertEquals(first.toJson(), second.toJson());
    assertEquals(Unit.MILLIMETRE, first.getUnit());
    assertEquals(2L, first.getPrimitives().stream().filter(item -> item.getType() == PrimitiveType.POLYLINE).count());
    assertNotNull(primitive(first, "flow-main:route"));
    assertNotNull(primitive(first, "flow-bypass:route"));
    assertTrue(first.isComplete());
    assertTrue(first.getDiagnostics().stream()
        .anyMatch(item -> "GRAPHICAL_PROJECTION_GENERIC_SYMBOL_FALLBACK".equals(item.getCode())));
  }

  @Test
  void projectConventionControlsGenericShapeWithoutApprovalClaim() {
    SymbolConvention equipment = new SymbolConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.DIAMOND, "#123456",
        "#abcdef", "PROJECT-SYMBOLS-7", EvidenceState.REVIEWED, "diagram-reviewer", "RVW-42", "2026-08-22T08:00:00Z",
        "C");
    EngineeringDiagramConventionRegister register = new EngineeringDiagramConventionRegister(Arrays.asList(equipment));

    EngineeringGraphicalProjection projection = EngineeringGraphicalProjectionBuilder.build(graph(), register,
        "DOC-PFD-001", VerificationStatus.REVIEWED);
    Primitive shape = primitive(projection, "pump-a:shape");

    assertEquals(PrimitiveType.POLYGON, shape.getType());
    assertEquals("#123456", shape.getStrokeColor());
    assertEquals("#abcdef", shape.getFillColor());
    assertEquals(VerificationStatus.REVIEWED, projection.getVerificationStatus());
    assertTrue((Boolean) projection.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void jsonRoundTripPreservesProjectionAndDiagnostics() {
    EngineeringGraphicalProjection original = EngineeringGraphicalProjectionBuilder.build(graph(),
        new EngineeringDiagramConventionRegister(), "DOC-PFD-001", VerificationStatus.PROPOSAL);

    EngineeringGraphicalProjection restored = EngineeringGraphicalProjection.fromJson(original.toJson());

    assertEquals(original.toJson(), restored.toJson());
    assertEquals(original.getSourceGraphFingerprint(), restored.getSourceGraphFingerprint());
    assertFalse(restored.getDiagnostics().isEmpty());
  }

  @Test
  void rejectsDuplicatePrimitiveIdentity() {
    Primitive first = Primitive.rectangle("duplicate", "pump-a", "P-100", 0.0, 0.0, 10.0, 10.0, "#000000", "none", 0.5);
    Primitive second = Primitive.text("duplicate", "pump-a", "P-100", 5.0, 5.0, 3.0, "P-100", "#000000", "middle");

    assertThrows(IllegalArgumentException.class,
        () -> new EngineeringGraphicalProjection("plant", "A", "fingerprint", "DOC", VerificationStatus.PROPOSAL,
            Arrays.asList(first, second),
            java.util.Collections.<EngineeringGraphicalProjection.Diagnostic>emptyList()));
  }

  @Test
  void reportsEmptyAndUnplacedGraphicalContent() {
    EngineeringGraph empty = new EngineeringGraph("empty-plant", "A");
    empty.addNode(new EngineeringNode("area-only", EngineeringNode.Kind.AREA, "AREA-ONLY", "Area only"));
    EngineeringGraphicalProjection emptyProjection = EngineeringGraphicalProjectionBuilder.build(empty,
        new EngineeringDiagramConventionRegister(), "DOC-EMPTY", VerificationStatus.PROPOSAL);

    assertFalse(emptyProjection.isComplete());
    assertTrue(emptyProjection.getDiagnostics().stream()
        .anyMatch(item -> "GRAPHICAL_PROJECTION_EMPTY".equals(item.getCode())));

    EngineeringGraph unplaced = new EngineeringGraph("unplaced-plant", "A");
    unplaced.addNode(new EngineeringNode("area", EngineeringNode.Kind.AREA, "AREA-A", "Area"));
    unplaced.addNode(new EngineeringNode("pump", EngineeringNode.Kind.EQUIPMENT, "P-1", "Pump"));
    unplaced.addEdge(new EngineeringEdge("unplaced-flow", "pump", "area", EngineeringEdge.Kind.PROCESS_FLOW,
        "invalid drawing endpoint"));
    EngineeringGraphicalProjection unplacedProjection = EngineeringGraphicalProjectionBuilder.build(unplaced,
        new EngineeringDiagramConventionRegister(), "DOC-UNPLACED", VerificationStatus.PROPOSAL);

    assertTrue(unplacedProjection.getDiagnostics().stream()
        .anyMatch(item -> "GRAPHICAL_PROJECTION_ROUTE_ENDPOINT_NOT_PLACED".equals(item.getCode())));
  }

  private static EngineeringGraph graph() {
    EngineeringGraph graph = new EngineeringGraph("plant-alpha", "A");
    graph.addNode(new EngineeringNode("area-a", EngineeringNode.Kind.AREA, "AREA-A", "Area A"));
    graph.addNode(new EngineeringNode("area-b", EngineeringNode.Kind.AREA, "AREA-B", "Area B"));
    graph.addNode(new EngineeringNode("pump-a", EngineeringNode.Kind.EQUIPMENT, "P-100", "Feed pump"));
    graph.addNode(new EngineeringNode("separator-b", EngineeringNode.Kind.EQUIPMENT, "V-200", "Product separator"));
    graph.addEdge(new EngineeringEdge("area-a-pump", "area-a", "pump-a", EngineeringEdge.Kind.CONTAINS, "equipment"));
    graph.addEdge(
        new EngineeringEdge("area-b-separator", "area-b", "separator-b", EngineeringEdge.Kind.CONTAINS, "equipment"));
    graph.addEdge(new EngineeringEdge("flow-main", "pump-a", "separator-b", EngineeringEdge.Kind.PROCESS_FLOW, "main"));
    graph.addEdge(
        new EngineeringEdge("flow-bypass", "pump-a", "separator-b", EngineeringEdge.Kind.PROCESS_FLOW, "bypass"));
    return graph;
  }

  private static Primitive primitive(EngineeringGraphicalProjection projection, String id) {
    for (Primitive primitive : projection.getPrimitives()) {
      if (id.equals(primitive.getId())) {
        return primitive;
      }
    }
    return null;
  }
}
