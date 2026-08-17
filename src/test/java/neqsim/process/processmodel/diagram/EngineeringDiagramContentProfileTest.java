package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Diagnostic;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Drawing;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Sheet;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.CoordinateUnit;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.PinnedPosition;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.ProtectedRoute;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetAssignment;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetDefinition;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.Waypoint;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;

class EngineeringDiagramContentProfileTest {
  @Test
  void separatesDrawingViewsWithoutDiscardingCanonicalSemantics() {
    EngineeringDiagramDocumentSet bfd = documents(profileGraph(), ContentProfile.BFD);
    EngineeringDiagramDocumentSet pfd = documents(profileGraph(), ContentProfile.PFD);
    EngineeringDiagramDocumentSet pid = documents(profileGraph(), ContentProfile.PID);

    assertEquals(semanticIds(pid), semanticIds(pfd));
    assertEquals(semanticIds(pid), semanticIds(bfd));

    Set<String> bfdIds = visibleIds(bfd);
    Set<String> pfdIds = visibleIds(pfd);
    Set<String> pidIds = visibleIds(pid);
    assertTrue(pfdIds.containsAll(bfdIds));
    assertTrue(pidIds.containsAll(pfdIds));

    assertTrue(bfdIds.contains("equipment:feed"));
    assertTrue(bfdIds.contains("pipe-segment:feed-a"));
    assertTrue(bfdIds.contains("pipe-segment:feed-b"));
    assertFalse(bfdIds.contains("line:feed"));
    assertFalse(bfdIds.contains("energy-connection:heater-duty"));
    assertFalse(bfdIds.contains("instrument:temperature"));
    assertFalse(bfdIds.contains("signal-connection:temperature"));

    assertTrue(pfdIds.contains("line:feed"));
    assertTrue(pfdIds.contains("energy-connection:heater-duty"));
    assertFalse(pfdIds.contains("instrument:temperature"));
    assertFalse(pfdIds.contains("port:signal-source"));
    assertFalse(pfdIds.contains("signal-connection:temperature"));

    assertTrue(pidIds.contains("instrument:temperature"));
    assertTrue(pidIds.contains("port:signal-source"));
    assertTrue(pidIds.contains("signal-connection:temperature"));
    assertTrue(hasDiagnostic(bfd, "DIAGRAM_CONTENT_PROFILE_OBJECT_OMITTED", "energy-connection:heater-duty"));
    assertTrue(hasDiagnostic(pfd, "DIAGRAM_CONTENT_PROFILE_OBJECT_OMITTED", "instrument:temperature"));
    assertFalse(hasDiagnostic(pid, "DIAGRAM_CONTENT_PROFILE_OBJECT_OMITTED", "instrument:temperature"));
  }

  @Test
  void filtersCrossSheetReferencesAndPreservesParallelMaterialConnections() {
    EngineeringDiagramDocumentSet bfd = documents(multiAreaGraph(), ContentProfile.BFD);
    EngineeringDiagramDocumentSet pfd = documents(multiAreaGraph(), ContentProfile.PFD);
    EngineeringDiagramDocumentSet pid = documents(multiAreaGraph(), ContentProfile.PID);

    assertEquals(4, connectorCount(bfd));
    assertEquals(6, connectorCount(pfd));
    assertEquals(8, connectorCount(pid));
    assertEquals(2, connectorCount(bfd, "pipe-segment:parallel-a"));
    assertEquals(2, connectorCount(bfd, "pipe-segment:parallel-b"));
    assertEquals(0, connectorCount(bfd, "energy-connection:duty"));
    assertEquals(2, connectorCount(pfd, "energy-connection:duty"));
    assertEquals(0, connectorCount(pfd, "signal-connection:control"));
    assertEquals(2, connectorCount(pid, "signal-connection:control"));
    assertTrue(bfd.isValid());
    assertTrue(pfd.isValid());
    assertTrue(pid.isValid());
  }

  @Test
  void remainsDeterministicAndReturnsImmutableProfileViews() {
    EngineeringDiagramDocumentSet first = documents(profileGraph(), ContentProfile.PFD);
    EngineeringDiagramDocumentSet second = documents(profileGraph(), ContentProfile.PFD);

    assertEquals(first.toJson(), second.toJson());
    assertThrows(UnsupportedOperationException.class,
        () -> first.getDrawings().get(0).getSheets().get(0).getObjectNodeIds().add("equipment:extra"));
    assertTrue(hasDiagnostic(first, "DIAGRAM_CONTENT_PROFILE_PROPOSAL_ONLY", "PFD-PROFILE-001"));
  }

  @Test
  void reportsManualLayoutEvidenceThatTheProfileCannotDisplay() {
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withSheet(new SheetDefinition("instrument-detail", "2", "Instrument detail", "layout:profile",
            EvidenceState.REVIEWED, "Process discipline", "2026-08-17T05:00:00Z", "A"))
        .withAssignment(new SheetAssignment("instrument:temperature", "instrument-detail", "layout:profile",
            EvidenceState.REVIEWED, "Process discipline", "2026-08-17T05:00:00Z", "A"))
        .withPinnedPosition(
            new PinnedPosition("instrument:temperature", "instrument-detail", 50.0, 60.0, CoordinateUnit.MILLIMETRE,
                "layout:profile", EvidenceState.REVIEWED, "Process discipline", "2026-08-17T05:00:00Z", "A"))
        .withProtectedRoute(new ProtectedRoute("energy-connection:heater-duty", "plant",
            Arrays.asList(new Waypoint(10.0, 20.0), new Waypoint(30.0, 20.0)), CoordinateUnit.MILLIMETRE,
            "layout:profile", EvidenceState.REVIEWED, "Process discipline", "2026-08-17T05:00:00Z", "A"));

    EngineeringDiagramDocumentSet documents = EngineeringDiagramDocumentSet.fromGraph(profileGraph(), "PFD-PROFILE-001",
        "Content profile reference", ContentProfile.BFD, new EngineeringDiagramDesignationRegister(), layout);

    assertTrue(documents.isValid());
    assertEquals(3, diagnosticCount(documents, "DIAGRAM_CONTENT_PROFILE_LAYOUT_OMITTED"));
    assertTrue(findSheet(documents, "instrument-detail").getManualAssignments().isEmpty());
    assertTrue(findSheet(documents, "instrument-detail").getPinnedPositions().isEmpty());
    assertTrue(findSheet(documents, "plant").getProtectedRoutes().isEmpty());
  }

  private static EngineeringDiagramDocumentSet documents(EngineeringGraph graph, ContentProfile profile) {
    return EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-PROFILE-001", "Content profile reference", profile);
  }

  private static EngineeringGraph profileGraph() {
    EngineeringGraph graph = new EngineeringGraph("PROFILE-PLANT", "A");
    graph.addNode(node("equipment:feed", EngineeringNode.Kind.EQUIPMENT, "Feed block"));
    graph.addNode(node("equipment:product", EngineeringNode.Kind.EQUIPMENT, "Product block"));
    graph.addNode(node("boundary:feed", EngineeringNode.Kind.BOUNDARY, "Feed boundary"));
    graph.addNode(node("line:feed", EngineeringNode.Kind.LINE, "L-001"));
    graph.addNode(endpoint("nozzle:feed-out", EngineeringNode.Kind.NOZZLE, "equipment:feed", "MATERIAL"));
    graph.addNode(endpoint("nozzle:product-in", EngineeringNode.Kind.NOZZLE, "equipment:product", "MATERIAL"));
    graph.addNode(connection("pipe-segment:feed-a", EngineeringNode.Kind.PIPE_SEGMENT, "MATERIAL", "nozzle:feed-out",
        "nozzle:product-in"));
    graph.addNode(connection("pipe-segment:feed-b", EngineeringNode.Kind.PIPE_SEGMENT, "MATERIAL", "nozzle:feed-out",
        "nozzle:product-in"));
    graph.addNode(endpoint("port:energy-source", EngineeringNode.Kind.PORT, "equipment:feed", "ENERGY"));
    graph.addNode(endpoint("port:energy-target", EngineeringNode.Kind.PORT, "equipment:product", "ENERGY"));
    graph.addNode(connection("energy-connection:heater-duty", EngineeringNode.Kind.ENERGY_CONNECTION, "ENERGY",
        "port:energy-source", "port:energy-target"));
    graph.addNode(node("instrument:temperature", EngineeringNode.Kind.INSTRUMENT, "TIT-001"));
    graph.addNode(endpoint("port:signal-source", EngineeringNode.Kind.PORT, "instrument:temperature", "SIGNAL"));
    graph.addNode(endpoint("port:signal-target", EngineeringNode.Kind.PORT, "equipment:product", "SIGNAL"));
    graph.addNode(connection("signal-connection:temperature", EngineeringNode.Kind.SIGNAL_CONNECTION, "SIGNAL",
        "port:signal-source", "port:signal-target"));
    return graph;
  }

  private static EngineeringGraph multiAreaGraph() {
    EngineeringGraph graph = new EngineeringGraph("PROFILE-MULTI-AREA", "A");
    graph.addNode(
        new EngineeringNode("area:a", EngineeringNode.Kind.AREA, "area-a", "Area A").putProperty("areaName", "Area A"));
    graph.addNode(
        new EngineeringNode("area:b", EngineeringNode.Kind.AREA, "area-b", "Area B").putProperty("areaName", "Area B"));
    graph.addNode(crossAreaConnection("pipe-segment:parallel-a", EngineeringNode.Kind.PIPE_SEGMENT, "MATERIAL"));
    graph.addNode(crossAreaConnection("pipe-segment:parallel-b", EngineeringNode.Kind.PIPE_SEGMENT, "MATERIAL"));
    graph.addNode(crossAreaConnection("energy-connection:duty", EngineeringNode.Kind.ENERGY_CONNECTION, "ENERGY"));
    graph.addNode(crossAreaConnection("signal-connection:control", EngineeringNode.Kind.SIGNAL_CONNECTION, "SIGNAL"));
    return graph;
  }

  private static EngineeringNode node(String id, EngineeringNode.Kind kind, String label) {
    return new EngineeringNode(id, kind, id, label);
  }

  private static EngineeringNode endpoint(String id, EngineeringNode.Kind kind, String ownerId, String connectionType) {
    return node(id, kind, id).putProperty("ownerNodeId", ownerId).putProperty("connectionType", connectionType);
  }

  private static EngineeringNode connection(String id, EngineeringNode.Kind kind, String connectionType,
      String sourceEndpointId, String targetEndpointId) {
    return node(id, kind, id).putProperty("connectionType", connectionType)
        .putProperty("sourceEndpointId", sourceEndpointId).putProperty("targetEndpointId", targetEndpointId);
  }

  private static EngineeringNode crossAreaConnection(String id, EngineeringNode.Kind kind, String connectionType) {
    return node(id, kind, id).putProperty("connectionType", connectionType).putProperty("crossArea", Boolean.TRUE)
        .putProperty("sourceArea", "Area A").putProperty("targetArea", "Area B");
  }

  private static Set<String> visibleIds(EngineeringDiagramDocumentSet documents) {
    Set<String> result = new LinkedHashSet<String>();
    for (Sheet sheet : documents.getDrawings().get(0).getSheets()) {
      result.addAll(sheet.getObjectNodeIds());
    }
    return result;
  }

  private static List<String> semanticIds(EngineeringDiagramDocumentSet documents) {
    List<String> result = new ArrayList<String>();
    for (SemanticObject object : documents.getSemanticObjects()) {
      result.add(object.getId());
    }
    return result;
  }

  private static int connectorCount(EngineeringDiagramDocumentSet documents) {
    int result = 0;
    for (Sheet sheet : documents.getDrawings().get(0).getSheets()) {
      result += sheet.getOffPageConnectors().size();
    }
    return result;
  }

  private static int connectorCount(EngineeringDiagramDocumentSet documents, String connectionId) {
    int result = 0;
    Drawing drawing = documents.getDrawings().get(0);
    for (Sheet sheet : drawing.getSheets()) {
      for (EngineeringDiagramDocumentSet.OffPageConnector connector : sheet.getOffPageConnectors()) {
        if (connectionId.equals(connector.getSemanticConnectionId())) {
          result++;
        }
      }
    }
    return result;
  }

  private static boolean hasDiagnostic(EngineeringDiagramDocumentSet documents, String code, String subjectId) {
    for (Diagnostic diagnostic : documents.getDiagnostics()) {
      if (code.equals(diagnostic.getCode()) && subjectId.equals(diagnostic.getSubjectId())) {
        return true;
      }
    }
    return false;
  }

  private static int diagnosticCount(EngineeringDiagramDocumentSet documents, String code) {
    int result = 0;
    for (Diagnostic diagnostic : documents.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        result++;
      }
    }
    return result;
  }

  private static Sheet findSheet(EngineeringDiagramDocumentSet documents, String key) {
    for (Sheet sheet : documents.getDrawings().get(0).getSheets()) {
      if (key.equals(sheet.getKey())) {
        return sheet;
      }
    }
    throw new AssertionError("Missing sheet " + key);
  }
}
