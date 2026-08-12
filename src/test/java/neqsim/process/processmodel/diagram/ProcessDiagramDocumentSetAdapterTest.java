package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Diagnostic;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Drawing;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.OffPageConnector;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Sheet;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.model.EngineeringProvenance;
import neqsim.process.processmodel.ProcessSystem;
import org.junit.jupiter.api.Test;

/** Regression tests for immutable diagram document, sheet, and off-page semantics. */
class ProcessDiagramDocumentSetAdapterTest {

  @Test
  void createsDeterministicSingleSheetProposalWithoutChangingClassicDot() {
    EngineeringDiagramReferenceFixtures.SystemCase firstCase = EngineeringDiagramReferenceFixtures.simpleTrain();
    ProcessSystem process = firstCase.getProcessSystem();
    String dotBefore = process.toDOT();

    EngineeringDiagramDocumentSet first = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        firstCase.getCaseId(), "A", "PFD-10-001", "Simple reference train", ContentProfile.PFD);
    EngineeringDiagramDocumentSet second = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), firstCase.getCaseId(), "A", "PFD-10-001",
        "Simple reference train", ContentProfile.PFD);

    assertEquals(first.toJson(), second.toJson());
    assertEquals(dotBefore, process.toDOT());
    assertEquals(EngineeringDiagramDocumentSet.DocumentStatus.WORKING, first.getStatus());
    assertEquals(EngineeringDiagramDocumentSet.IssuePurpose.ENGINEERING_PROPOSAL, first.getIssuePurpose());
    assertEquals("A", first.getRevision());
    assertEquals(1, first.getRevisionHistory().size());
    assertEquals(1, first.getDrawings().size());
    assertEquals(ContentProfile.PFD, first.getDrawings().get(0).getContentProfile());
    assertEquals(1, first.getDrawings().get(0).getSheets().size());
    assertTrue(first.getDrawings().get(0).getSheets().get(0).getOffPageConnectors().isEmpty());
    assertTrue(first.isValid());
    assertFalse(first.getSourceGraphFingerprint().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> first.getDrawings().add(first.getDrawings().get(0)));
    assertThrows(UnsupportedOperationException.class, () -> first.getSemanticObjects().clear());
  }

  @Test
  void carriesGovernedOperatingValuesAndSourceDesignationsIntoControlledSnapshot() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    ProcessSystem process = reference.getProcessSystem();
    process.run();

    EngineeringDiagramDocumentSet set = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        reference.getCaseId(), "B", "PFD-10-002", "Governed operating case", ContentProfile.PFD, "NORMAL-01");

    SemanticObject pressure = findSemanticObject(set, EngineeringNode.Kind.CALCULATION, "quantity", "pressure");
    assertEquals("bara", pressure.getProperties().get("resultUnit"));
    assertEquals("ABSOLUTE", pressure.getProperties().get("quantityBasis"));
    assertEquals("NORMAL-01", pressure.getProperties().get("designCaseId"));
    assertEquals("CALCULATED", pressure.getProperties().get("engineeringState"));
    assertEquals("REVIEW_REQUIRED", pressure.getProperties().get("approvalStatus"));
    assertEquals("SIMULATION_RESULT", pressure.getProvenance().get(0).getSourceType());
    assertEquals("NORMAL-01", pressure.getProvenance().get(0).getDesignCaseId());
    assertThrows(UnsupportedOperationException.class,
        () -> pressure.getProperties().put("resultUnit", "psia"));

    SemanticObject equipment = findSemanticObject(set, EngineeringNode.Kind.EQUIPMENT, "equipmentName",
        "10-VA-001");
    assertEquals("10-VA-001", equipment.getLabel());
    SemanticObject connection = findSemanticObject(set, EngineeringNode.Kind.PIPE_SEGMENT, "carriedObjectName",
        "10-FEED-001");
    assertEquals("MATERIAL", connection.getProperties().get("connectionType"));
    assertTrue(set.isValid());
  }

  @Test
  void rejectsCalculatedValuesWithoutGovernanceMetadata() {
    EngineeringGraph graph = new EngineeringGraph("INCOMPLETE-VALUE-PLANT", "A");
    graph.addNode(new EngineeringNode("calculation:pressure", EngineeringNode.Kind.CALCULATION, "pressure",
        "Pressure").putProperty("resultValue", Double.valueOf(42.0))
            .addProvenance(new EngineeringProvenance("SIMULATION_RESULT", "equipment:V-001")));

    EngineeringDiagramDocumentSet set = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-INCOMPLETE-001",
        "Incomplete governed value", ContentProfile.PFD);

    assertFalse(set.isValid());
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_INCOMPLETE_GOVERNED_VALUE"));
  }

  @Test
  void reportsLegacyCalculationGovernanceGapsWithoutBreakingExistingDocuments() {
    EngineeringGraph graph = new EngineeringGraph("LEGACY-CALCULATION-PLANT", "A");
    graph.addNode(new EngineeringNode("calculation:pressure", EngineeringNode.Kind.CALCULATION, "pressure",
        "Pressure").putProperty("resultValue", Double.valueOf(42.0)).putProperty("resultUnit", "bara")
            .putProperty("status", "CALCULATED")
            .addProvenance(new EngineeringProvenance("CALCULATION", "legacy-pressure")));

    EngineeringDiagramDocumentSet set = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-LEGACY-001",
        "Legacy calculation governance", ContentProfile.PFD);

    assertTrue(set.isValid());
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_INCOMPLETE_GOVERNED_VALUE"));
  }

  @Test
  void snapshotsNestedSemanticPropertiesWithoutSharingMutableCollections() {
    List<String> sourceEvidence = new ArrayList<String>();
    sourceEvidence.add("calculation:C-001");
    EngineeringGraph graph = new EngineeringGraph("IMMUTABLE-PROPERTY-PLANT", "A");
    graph.addNode(new EngineeringNode("equipment:V-001", EngineeringNode.Kind.EQUIPMENT, "V-001", "V-001")
        .putProperty("evidenceReferences", sourceEvidence));

    EngineeringDiagramDocumentSet set = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-IMMUTABLE-001",
        "Immutable semantic properties", ContentProfile.PFD);
    sourceEvidence.add("calculation:C-002");

    @SuppressWarnings("unchecked")
    List<String> snapshotEvidence = (List<String>) set.getSemanticObjects().get(0).getProperties()
        .get("evidenceReferences");
    assertEquals(1, snapshotEvidence.size());
    assertThrows(UnsupportedOperationException.class, () -> snapshotEvidence.add("calculation:C-003"));
  }

  @Test
  void createsReciprocalOffPagePairsForOneMultiAreaSemanticGraph() {
    EngineeringDiagramDocumentSet set = ProcessDiagramDocumentSetAdapter.fromProcessModel(
        EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(), "DEXPI-REF-MULTI-AREA", "A",
        "PFD-30-001", "Multi-area reference facility", ContentProfile.PFD);

    Drawing drawing = set.getDrawings().get(0);
    assertEquals(4, drawing.getSheets().size());
    Map<String, OffPageConnector> connectorsById = connectorsById(drawing);
    assertEquals(6, connectorsById.size());
    assertEquals(3, connectorPairCounts(drawing).size());
    for (Map.Entry<String, Integer> pair : connectorPairCounts(drawing).entrySet()) {
      assertEquals(2, pair.getValue().intValue());
    }
    for (OffPageConnector connector : connectorsById.values()) {
      OffPageConnector peer = connectorsById.get(connector.getPeerConnectorId());
      assertEquals(connector.getId(), peer.getPeerConnectorId());
      assertEquals(connector.getPairId(), peer.getPairId());
      assertEquals(connector.getSemanticConnectionId(), peer.getSemanticConnectionId());
      assertNotEquals(connector.getRole(), peer.getRole());
      assertNotEquals(connector.getSheetId(), peer.getSheetId());
    }
    assertTrue(set.isValid());
  }

  @Test
  void preservesDistinctParallelCrossSheetConnections() {
    EngineeringGraph graph = twoAreaGraph();
    addCrossAreaConnection(graph, "pipe-segment:first", "first");
    addCrossAreaConnection(graph, "pipe-segment:second", "second");

    EngineeringDiagramDocumentSet set = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-PARALLEL-001",
        "Parallel cross-sheet connections", ContentProfile.PFD);

    Drawing drawing = set.getDrawings().get(0);
    assertEquals(4, connectorsById(drawing).size());
    assertEquals(2, connectorPairCounts(drawing).size());
    assertTrue(set.isValid());
  }

  @Test
  void reportsBrokenCrossSheetReferenceAsStructuredError() {
    EngineeringGraph graph = new EngineeringGraph("BROKEN-PLANT", "A");
    EngineeringNode connection = new EngineeringNode("pipe-segment:broken", EngineeringNode.Kind.PIPE_SEGMENT, "broken",
        "Broken connection").putProperty("crossArea", Boolean.TRUE).putProperty("sourceArea", "Unknown A")
        .putProperty("targetArea", "Unknown B");
    graph.addNode(connection);

    EngineeringDiagramDocumentSet set = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-BROKEN-001",
        "Broken reference evidence", ContentProfile.PFD);

    assertFalse(set.isValid());
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_BROKEN_CROSS_SHEET_REFERENCE"));
  }

  @Test
  void remainsByteDeterministicAcrossFreshMultiAreaModels() {
    String expected = null;
    for (int attempt = 0; attempt < 8; attempt++) {
      String json = ProcessDiagramDocumentSetAdapter
          .fromProcessModel(EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(),
              "DEXPI-REF-MULTI-AREA", "A", "PFD-30-001", "Multi-area reference facility", ContentProfile.PFD)
          .toJson();
      if (expected == null) {
        expected = json;
      } else {
        assertEquals(expected, json);
      }
    }
  }

  private static EngineeringGraph twoAreaGraph() {
    EngineeringGraph graph = new EngineeringGraph("PARALLEL-PLANT", "A");
    graph.addNode(new EngineeringNode(EngineeringIds.nodeId(EngineeringNode.Kind.AREA, "PARALLEL-PLANT/Area A"),
        EngineeringNode.Kind.AREA, "PARALLEL-PLANT/Area A", "Area A").putProperty("areaName", "Area A"));
    graph.addNode(new EngineeringNode(EngineeringIds.nodeId(EngineeringNode.Kind.AREA, "PARALLEL-PLANT/Area B"),
        EngineeringNode.Kind.AREA, "PARALLEL-PLANT/Area B", "Area B").putProperty("areaName", "Area B"));
    return graph;
  }

  private static void addCrossAreaConnection(EngineeringGraph graph, String id, String externalKey) {
    graph.addNode(new EngineeringNode(id, EngineeringNode.Kind.PIPE_SEGMENT, externalKey, externalKey)
        .putProperty("crossArea", Boolean.TRUE).putProperty("sourceArea", "Area A").putProperty("targetArea", "Area B")
        .putProperty("connectionType", "MATERIAL"));
  }

  private static Map<String, OffPageConnector> connectorsById(Drawing drawing) {
    Map<String, OffPageConnector> result = new LinkedHashMap<String, OffPageConnector>();
    for (Sheet sheet : drawing.getSheets()) {
      for (OffPageConnector connector : sheet.getOffPageConnectors()) {
        result.put(connector.getId(), connector);
      }
    }
    return result;
  }

  private static Map<String, Integer> connectorPairCounts(Drawing drawing) {
    Map<String, Integer> result = new LinkedHashMap<String, Integer>();
    for (OffPageConnector connector : connectorsById(drawing).values()) {
      Integer count = result.get(connector.getPairId());
      result.put(connector.getPairId(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }
    return result;
  }

  private static boolean hasDiagnostic(EngineeringDiagramDocumentSet set, String code) {
    List<String> codes = new ArrayList<String>();
    for (Diagnostic diagnostic : set.getDiagnostics()) {
      codes.add(diagnostic.getCode());
    }
    return codes.contains(code);
  }

  private static SemanticObject findSemanticObject(EngineeringDiagramDocumentSet set, EngineeringNode.Kind kind,
      String property, String value) {
    for (SemanticObject object : set.getSemanticObjects()) {
      if (object.getKind() == kind && value.equals(object.getProperties().get(property))) {
        return object;
      }
    }
    throw new AssertionError("Missing semantic object " + kind + " with " + property + "=" + value);
  }
}
