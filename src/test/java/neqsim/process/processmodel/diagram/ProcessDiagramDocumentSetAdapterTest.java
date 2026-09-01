package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Designation;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Kind;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.ReviewState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Diagnostic;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Drawing;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.OffPageConnector;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Sheet;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringDiagramRevisionImpact;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.CoordinateUnit;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.PinnedPosition;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.ProtectedRoute;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetAssignment;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetDefinition;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.Waypoint;
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
    assertThrows(UnsupportedOperationException.class, () -> pressure.getProperties().put("resultUnit", "psia"));

    SemanticObject equipment = findSemanticObject(set, EngineeringNode.Kind.EQUIPMENT, "equipmentName", "10-VA-001");
    assertEquals("10-VA-001", equipment.getLabel());
    SemanticObject connection = findSemanticObject(set, EngineeringNode.Kind.PIPE_SEGMENT, "carriedObjectName",
        "10-FEED-001");
    assertEquals("MATERIAL", connection.getProperties().get("connectionType"));
    assertTrue(set.isValid());
  }

  @Test
  void appliesReviewedDesignationsWithoutMutatingSourceLabelsOrClassicDot() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    ProcessSystem process = reference.getProcessSystem();
    String classicDot = process.toDOT();
    EngineeringDiagramDocumentSet source = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        reference.getCaseId(), "A", "PFD-10-003", "Reviewed designations", ContentProfile.PFD);
    SemanticObject equipment = findSemanticObject(source, EngineeringNode.Kind.EQUIPMENT, "equipmentName", "10-VA-001");
    SemanticObject stream = findSemanticObject(source, EngineeringNode.Kind.PIPE_SEGMENT, "carriedObjectName",
        "10-FEED-001");
    EngineeringDiagramDesignationRegister register = new EngineeringDiagramDesignationRegister()
        .withDesignation(reviewedDesignation(equipment.getId(), Kind.EQUIPMENT_TAG, "V-101"))
        .withDesignation(reviewedDesignation(stream.getId(), Kind.STREAM_NUMBER, "10-P-1001-A"));

    EngineeringDiagramDocumentSet reviewed = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        reference.getCaseId(), "A", "PFD-10-003", "Reviewed designations", ContentProfile.PFD, register);

    SemanticObject reviewedEquipment = findSemanticObjectById(reviewed, equipment.getId());
    SemanticObject reviewedStream = findSemanticObjectById(reviewed, stream.getId());
    assertEquals("10-VA-001", reviewedEquipment.getLabel());
    assertEquals("V-101", reviewedEquipment.getDesignations().get(0).getValue());
    assertEquals(ReviewState.REVIEWED, reviewedEquipment.getDesignations().get(0).getReviewState());
    assertEquals("10-P-1001-A", reviewedStream.getDesignations().get(0).getValue());
    assertNotSame(reviewedEquipment.getDesignations(), reviewedEquipment.getDesignations());
    assertThrows(UnsupportedOperationException.class, () -> reviewedEquipment.getDesignations().clear());
    assertFalse(source.toJson().contains("\"designations\""));
    assertEquals(classicDot, process.toDOT());
    assertTrue(reviewed.isValid());
  }

  @Test
  void rejectsUnknownAndKindMismatchedReviewedDesignations() {
    EngineeringGraph graph = new EngineeringGraph("DESIGNATION-PLANT", "A");
    graph.addNode(new EngineeringNode("equipment:V-001", EngineeringNode.Kind.EQUIPMENT, "V-001", "Source V-001"));
    EngineeringDiagramDesignationRegister register = new EngineeringDiagramDesignationRegister()
        .withDesignation(reviewedDesignation("equipment:V-001", Kind.STREAM_NUMBER, "10-P-1001-A"))
        .withDesignation(reviewedDesignation("equipment:UNKNOWN", Kind.EQUIPMENT_TAG, "V-999"));

    EngineeringDiagramDocumentSet set = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-DESIGNATION-001",
        "Designation diagnostics", ContentProfile.PFD, register);

    assertFalse(set.isValid());
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_DESIGNATION_KIND_MISMATCH"));
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_DESIGNATION_UNKNOWN_OBJECT"));
    assertTrue(set.getSemanticObjects().get(0).getDesignations().isEmpty());
  }

  @Test
  void reportsDeterministicCrossSheetImpactForReviewedStreamNumberChange() {
    EngineeringGraph baselineGraph = twoAreaGraph("A");
    addCrossAreaConnection(baselineGraph, "pipe-segment:feed", "feed");
    EngineeringGraph revisedGraph = twoAreaGraph("B");
    addCrossAreaConnection(revisedGraph, "pipe-segment:feed", "feed");
    EngineeringDiagramDocumentSet baseline = EngineeringDiagramDocumentSet.fromGraph(baselineGraph, "PFD-IMPACT-001",
        "Revision impact", ContentProfile.PFD);
    EngineeringDiagramDesignationRegister register = new EngineeringDiagramDesignationRegister()
        .withDesignation(reviewedDesignation("pipe-segment:feed", Kind.STREAM_NUMBER, "10-P-1001-B"));
    EngineeringDiagramDocumentSet revised = EngineeringDiagramDocumentSet.fromGraph(revisedGraph, "PFD-IMPACT-001",
        "Revision impact", ContentProfile.PFD, register);

    EngineeringDiagramRevisionImpact first = baseline.compareTo(revised);
    EngineeringDiagramRevisionImpact second = EngineeringDiagramRevisionImpact.compare(baseline, revised);

    assertEquals(EngineeringDiagramRevisionImpact.Status.CHANGED, first.getStatus());
    assertEquals(1, first.getModifiedSemanticObjectIds().size());
    assertEquals("pipe-segment:feed", first.getModifiedSemanticObjectIds().get(0));
    assertTrue(first.getAddedSemanticObjectIds().isEmpty());
    assertTrue(first.getRemovedSemanticObjectIds().isEmpty());
    assertEquals(2, first.getAffectedSheetIds().size());
    assertEquals(1, first.getAffectedDrawingIds().size());
    assertNotSame(first.getAffectedSheetIds(), first.getAffectedSheetIds());
    assertEquals(first.toJson(), second.toJson());
    assertThrows(UnsupportedOperationException.class, () -> first.getAffectedSheetIds().clear());
  }

  @Test
  void reportsUnchangedSemanticImpactAcrossRevisionOnlyChange() {
    EngineeringDiagramDocumentSet baseline = EngineeringDiagramDocumentSet.fromGraph(twoAreaGraph("A"),
        "PFD-IMPACT-002", "Revision-only impact", ContentProfile.PFD);
    EngineeringDiagramDocumentSet revised = EngineeringDiagramDocumentSet.fromGraph(twoAreaGraph("B"), "PFD-IMPACT-002",
        "Revision-only impact", ContentProfile.PFD);

    EngineeringDiagramRevisionImpact impact = baseline.compareTo(revised);

    assertEquals(EngineeringDiagramRevisionImpact.Status.UNCHANGED, impact.getStatus());
    assertTrue(impact.getAffectedSheetIds().isEmpty());
    assertNotEquals(impact.getBaselineFingerprint(), impact.getRevisedFingerprint());
  }

  @Test
  void carriesOneGovernedOperatingCaseAcrossMultiAreaProcessModel() {
    EngineeringDiagramReferenceFixtures.ModelCase reference = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    reference.getProcessModel().run();

    EngineeringDiagramDocumentSet set = ProcessDiagramDocumentSetAdapter.fromProcessModel(reference.getProcessModel(),
        reference.getCaseId(), "B", "PFD-30-002", "Governed multi-area case", ContentProfile.PFD, "NORMAL-01");

    SemanticObject pressure = findSemanticObject(set, EngineeringNode.Kind.CALCULATION, "quantity", "pressure");
    assertEquals("NORMAL-01", pressure.getProperties().get("designCaseId"));
    assertEquals(4, set.getDrawings().get(0).getSheets().size());
    assertTrue(set.isValid());
  }

  @Test
  void rejectsCalculatedValuesWithoutGovernanceMetadata() {
    EngineeringGraph graph = new EngineeringGraph("INCOMPLETE-VALUE-PLANT", "A");
    graph.addNode(new EngineeringNode("calculation:pressure", EngineeringNode.Kind.CALCULATION, "pressure", "Pressure")
        .putProperty("resultValue", Double.valueOf(42.0))
        .addProvenance(new EngineeringProvenance("SIMULATION_RESULT", "equipment:V-001")));

    EngineeringDiagramDocumentSet set = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-INCOMPLETE-001",
        "Incomplete governed value", ContentProfile.PFD);

    assertFalse(set.isValid());
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_INCOMPLETE_GOVERNED_VALUE"));
  }

  @Test
  void reportsLegacyCalculationGovernanceGapsWithoutBreakingExistingDocuments() {
    EngineeringGraph graph = new EngineeringGraph("LEGACY-CALCULATION-PLANT", "A");
    graph.addNode(new EngineeringNode("calculation:pressure", EngineeringNode.Kind.CALCULATION, "pressure", "Pressure")
        .putProperty("resultValue", Double.valueOf(42.0)).putProperty("resultUnit", "bara")
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
  void persistsManualSheetAssignmentPinnedPositionAndProtectedRoute() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    ProcessSystem process = reference.getProcessSystem();
    String classicDot = process.toDOT();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        reference.getCaseId(), "A", "PFD-LAYOUT-001", "Controlled layout", ContentProfile.PFD);
    SemanticObject equipment = findSemanticObject(baseline, EngineeringNode.Kind.EQUIPMENT, "equipmentName",
        "10-VA-001");
    SemanticObject connection = findSemanticObject(baseline, EngineeringNode.Kind.PIPE_SEGMENT, "targetEquipment",
        "10-VA-001");
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withSheet(reviewedSheet("separator-detail", "2", "Separator detail"))
        .withAssignment(reviewedAssignment(equipment.getId(), "separator-detail"))
        .withPinnedPosition(reviewedPosition(equipment.getId(), "separator-detail", 80.0, 60.0))
        .withProtectedRoute(reviewedRoute(connection.getId(), "separator-detail"));

    EngineeringDiagramDocumentSet first = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        reference.getCaseId(), "A", "PFD-LAYOUT-001", "Controlled layout", ContentProfile.PFD,
        new EngineeringDiagramDesignationRegister(), layout);
    EngineeringDiagramDocumentSet second = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), reference.getCaseId(), "A",
        "PFD-LAYOUT-001", "Controlled layout", ContentProfile.PFD, new EngineeringDiagramDesignationRegister(), layout);

    Sheet detail = findSheet(first, "separator-detail");
    assertEquals(first.toJson(), second.toJson());
    assertEquals(classicDot, process.toDOT());
    assertEquals(2, first.getDrawings().get(0).getSheets().size());
    assertEquals("2", detail.getNumber());
    assertEquals("project-layout:PFD-LAYOUT-001", detail.getManualDefinition().getSourceReference());
    assertTrue(detail.getObjectNodeIds().contains(equipment.getId()));
    assertTrue(detail.getObjectNodeIds().contains(connection.getId()));
    assertEquals(1, detail.getManualAssignments().size());
    assertEquals(equipment.getId(), detail.getManualAssignments().get(0).getSemanticObjectId());
    assertEquals(EvidenceState.REVIEWED, detail.getManualAssignments().get(0).getEvidenceState());
    assertEquals(1, detail.getPinnedPositions().size());
    assertEquals(80.0, detail.getPinnedPositions().get(0).getX());
    assertEquals(CoordinateUnit.MILLIMETRE, detail.getPinnedPositions().get(0).getUnit());
    assertEquals(1, detail.getProtectedRoutes().size());
    assertEquals(3, detail.getProtectedRoutes().get(0).getWaypoints().size());
    assertNotSame(detail.getPinnedPositions(), detail.getPinnedPositions());
    assertNotSame(detail.getProtectedRoutes(), detail.getProtectedRoutes());
    assertThrows(UnsupportedOperationException.class, () -> detail.getManualAssignments().clear());
    assertThrows(UnsupportedOperationException.class, () -> detail.getPinnedPositions().clear());
    assertEquals(2, connectorCountFor(first.getDrawings().get(0), connection.getId()));
    assertTrue(first.isValid());
  }

  @Test
  void appliesTheSameManualLayoutContractToMultiAreaProcessModels() {
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessModel(
        EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(), "DEXPI-REF-MULTI-AREA", "A",
        "PFD-LAYOUT-004", "Multi-area layout", ContentProfile.PFD);
    SemanticObject equipment = firstSemanticObject(baseline, EngineeringNode.Kind.EQUIPMENT);
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withSheet(reviewedSheet("equipment-detail", "5", "Equipment detail"))
        .withAssignment(reviewedAssignment(equipment.getId(), "equipment-detail"))
        .withPinnedPosition(reviewedPosition(equipment.getId(), "equipment-detail", 75.0, 50.0));

    EngineeringDiagramDocumentSet set = ProcessDiagramDocumentSetAdapter.fromProcessModel(
        EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(), "DEXPI-REF-MULTI-AREA", "A",
        "PFD-LAYOUT-004", "Multi-area layout", ContentProfile.PFD, new EngineeringDiagramDesignationRegister(), layout);

    assertEquals(5, set.getDrawings().get(0).getSheets().size());
    assertTrue(findSheet(set, "equipment-detail").getObjectNodeIds().contains(equipment.getId()));
    assertEquals(1, findSheet(set, "equipment-detail").getPinnedPositions().size());
    assertTrue(set.isValid());
  }

  @Test
  void emptyLayoutRegisterPreservesTheLegacyDocumentShape() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet legacy = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-LAYOUT-005", "Empty layout", ContentProfile.PFD,
        new EngineeringDiagramDesignationRegister());
    EngineeringDiagramDocumentSet additive = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), reference.getCaseId(), "A",
        "PFD-LAYOUT-005", "Empty layout", ContentProfile.PFD, new EngineeringDiagramDesignationRegister(),
        new EngineeringDiagramLayoutRegister());

    assertEquals(legacy.toJson(), additive.toJson());
    assertFalse(additive.toJson().contains("manualDefinition"));
    assertFalse(additive.toJson().contains("manualAssignments"));
    assertFalse(additive.toJson().contains("pinnedPositions"));
    assertFalse(additive.toJson().contains("protectedRoutes"));
  }

  @Test
  void carriesManualLayoutEvidenceAcrossRegenerationWithoutChangingSemanticIdentity() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-LAYOUT-002", "Persistent layout",
        ContentProfile.PFD);
    SemanticObject equipment = findSemanticObject(baseline, EngineeringNode.Kind.EQUIPMENT, "equipmentName",
        "10-VA-001");
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withSheet(reviewedSheet("separator-detail", "2", "Separator detail"))
        .withAssignment(reviewedAssignment(equipment.getId(), "separator-detail"))
        .withPinnedPosition(reviewedPosition(equipment.getId(), "separator-detail", 90.0, 65.0));

    EngineeringDiagramDocumentSet revisionA = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), reference.getCaseId(), "A",
        "PFD-LAYOUT-002", "Persistent layout", ContentProfile.PFD, new EngineeringDiagramDesignationRegister(), layout);
    EngineeringDiagramDocumentSet revisionB = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        EngineeringDiagramReferenceFixtures.simpleTrain().getProcessSystem(), reference.getCaseId(), "B",
        "PFD-LAYOUT-002", "Persistent layout", ContentProfile.PFD, new EngineeringDiagramDesignationRegister(), layout);

    assertEquals(equipment.getId(),
        findSheet(revisionA, "separator-detail").getPinnedPositions().get(0).getSemanticObjectId());
    assertEquals(equipment.getId(),
        findSheet(revisionB, "separator-detail").getPinnedPositions().get(0).getSemanticObjectId());
    assertEquals("LAYOUT-B", findSheet(revisionB, "separator-detail").getPinnedPositions().get(0).getRevision());
    assertEquals(EngineeringDiagramRevisionImpact.Status.UNCHANGED, revisionA.compareTo(revisionB).getStatus());
  }

  @Test
  void reportsInvalidManualLayoutReferencesWithoutSilentlyApplyingThem() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-LAYOUT-003", "Invalid layout",
        ContentProfile.PFD);
    SemanticObject connection = findSemanticObject(baseline, EngineeringNode.Kind.PIPE_SEGMENT, "carriedObjectName",
        "10-FEED-001");
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withAssignment(reviewedAssignment("equipment:unknown", "plant"))
        .withAssignment(
            reviewedAssignment(connection.getId(), baseline.getDrawings().get(0).getSheets().get(0).getKey()))
        .withPinnedPosition(reviewedPosition(connection.getId(), "unknown-sheet", 10.0, 20.0))
        .withProtectedRoute(reviewedRoute("pipe-segment:unknown", "unknown-sheet"));

    EngineeringDiagramDocumentSet set = ProcessDiagramDocumentSetAdapter.fromProcessSystem(reference.getProcessSystem(),
        reference.getCaseId(), "A", "PFD-LAYOUT-003", "Invalid layout", ContentProfile.PFD,
        new EngineeringDiagramDesignationRegister(), layout);

    assertFalse(set.isValid());
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_LAYOUT_UNKNOWN_OBJECT"));
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_LAYOUT_CONNECTION_ASSIGNMENT_DERIVED"));
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_LAYOUT_UNKNOWN_SHEET"));
    assertTrue(hasDiagnostic(set, "DIAGRAM_DOCUMENT_LAYOUT_UNKNOWN_CONNECTION"));
  }

  @Test
  void rejectsAmbiguousOrNonFiniteLayoutRegisterInputs() {
    SheetAssignment assignment = reviewedAssignment("equipment:V-001", "detail");
    assertThrows(IllegalArgumentException.class,
        () -> new EngineeringDiagramLayoutRegister(new ArrayList<SheetDefinition>(),
            Arrays.asList(assignment, assignment), new ArrayList<PinnedPosition>(), new ArrayList<ProtectedRoute>()));
    assertThrows(IllegalArgumentException.class, () -> reviewedPosition("equipment:V-001", "detail", Double.NaN, 10.0));
    assertThrows(IllegalArgumentException.class,
        () -> new ProtectedRoute("pipe-segment:feed", "detail", Arrays.asList(new Waypoint(1.0, 2.0)),
            CoordinateUnit.MILLIMETRE, "project-layout:PFD-LAYOUT-001", EvidenceState.REVIEWED, "Process discipline",
            "2026-08-13T14:00:00Z", "LAYOUT-B"));
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
    return twoAreaGraph("A");
  }

  private static EngineeringGraph twoAreaGraph(String revision) {
    EngineeringGraph graph = new EngineeringGraph("PARALLEL-PLANT", revision);
    graph.addNode(new EngineeringNode(EngineeringIds.nodeId(EngineeringNode.Kind.AREA, "PARALLEL-PLANT/Area A"),
        EngineeringNode.Kind.AREA, "PARALLEL-PLANT/Area A", "Area A").putProperty("areaName", "Area A"));
    graph.addNode(new EngineeringNode(EngineeringIds.nodeId(EngineeringNode.Kind.AREA, "PARALLEL-PLANT/Area B"),
        EngineeringNode.Kind.AREA, "PARALLEL-PLANT/Area B", "Area B").putProperty("areaName", "Area B"));
    return graph;
  }

  private static Designation reviewedDesignation(String semanticObjectId, Kind kind, String value) {
    return new Designation(semanticObjectId, kind, value, "project-register:diagram-designations", ReviewState.REVIEWED,
        "Process discipline", "review:DIAGRAM-42", "2026-08-13T07:00:00Z", "B");
  }

  private static SheetDefinition reviewedSheet(String key, String number, String title) {
    return new SheetDefinition(key, number, title, "project-layout:PFD-LAYOUT-001", EvidenceState.REVIEWED,
        "Process discipline", "2026-08-13T14:00:00Z", "LAYOUT-B");
  }

  private static SheetAssignment reviewedAssignment(String semanticObjectId, String sheetKey) {
    return new SheetAssignment(semanticObjectId, sheetKey, "project-layout:PFD-LAYOUT-001", EvidenceState.REVIEWED,
        "Process discipline", "2026-08-13T14:00:00Z", "LAYOUT-B");
  }

  private static PinnedPosition reviewedPosition(String semanticObjectId, String sheetKey, double x, double y) {
    return new PinnedPosition(semanticObjectId, sheetKey, x, y, CoordinateUnit.MILLIMETRE,
        "project-layout:PFD-LAYOUT-001", EvidenceState.REVIEWED, "Process discipline", "2026-08-13T14:00:00Z",
        "LAYOUT-B");
  }

  private static ProtectedRoute reviewedRoute(String connectionId, String sheetKey) {
    return new ProtectedRoute(connectionId, sheetKey,
        Arrays.asList(new Waypoint(10.0, 20.0), new Waypoint(40.0, 20.0), new Waypoint(40.0, 50.0)),
        CoordinateUnit.MILLIMETRE, "project-layout:PFD-LAYOUT-001", EvidenceState.REVIEWED, "Process discipline",
        "2026-08-13T14:00:00Z", "LAYOUT-B");
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

  private static int connectorCountFor(Drawing drawing, String semanticConnectionId) {
    int result = 0;
    for (OffPageConnector connector : connectorsById(drawing).values()) {
      if (semanticConnectionId.equals(connector.getSemanticConnectionId())) {
        result++;
      }
    }
    return result;
  }

  private static Sheet findSheet(EngineeringDiagramDocumentSet set, String key) {
    for (Sheet sheet : set.getDrawings().get(0).getSheets()) {
      if (key.equals(sheet.getKey())) {
        return sheet;
      }
    }
    throw new AssertionError("Missing sheet " + key);
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

  private static SemanticObject firstSemanticObject(EngineeringDiagramDocumentSet set, EngineeringNode.Kind kind) {
    for (SemanticObject object : set.getSemanticObjects()) {
      if (object.getKind() == kind) {
        return object;
      }
    }
    throw new AssertionError("Missing semantic object " + kind);
  }

  private static SemanticObject findSemanticObjectById(EngineeringDiagramDocumentSet set, String id) {
    for (SemanticObject object : set.getSemanticObjects()) {
      if (id.equals(object.getId())) {
        return object;
      }
    }
    throw new AssertionError("Missing semantic object " + id);
  }
}
