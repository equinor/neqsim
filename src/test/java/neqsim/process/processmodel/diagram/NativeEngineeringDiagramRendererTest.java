package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Designation;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.Kind;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister.ReviewState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Sheet;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.CoordinateUnit;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.PinnedPosition;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.ProtectedRoute;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.Waypoint;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.ProcessSystem;
import org.junit.jupiter.api.Test;

class NativeEngineeringDiagramRendererTest {
  @Test
  void rendersDeterministicNativeSvgAndPdfWithoutChangingClassicOutputs() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    ProcessSystem process = reference.getProcessSystem();
    String classicDot = process.toDOT();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        reference.getCaseId(), "A", "PFD-NATIVE-001", "Native renderer reference", ContentProfile.PFD);
    String sheetKey = baseline.getDrawings().get(0).getSheets().get(0).getKey();
    SemanticObject separator = findObject(baseline, EngineeringNode.Kind.EQUIPMENT, "equipmentName", "10-VA-001");
    SemanticObject feedConnection = findObject(baseline, EngineeringNode.Kind.PIPE_SEGMENT, "targetEquipment",
        "10-XV-001");
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withPinnedPosition(reviewedPosition(separator.getId(), sheetKey, 80.0, 60.0))
        .withProtectedRoute(reviewedRoute(feedConnection.getId(), sheetKey));
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        reference.getCaseId(), "A", "PFD-NATIVE-001", "Native renderer reference", ContentProfile.PFD,
        new EngineeringDiagramDesignationRegister(), layout);
    String controlledJson = documents.toJson();

    NativeEngineeringDiagramRenderer renderer = new NativeEngineeringDiagramRenderer(documents);
    NativeEngineeringDiagramRenderer.Result first = renderer.render();
    NativeEngineeringDiagramRenderer.Result second = renderer.render();
    String svg = first.getSvgBySheetId().values().iterator().next();

    assertEquals(first.getSvgBySheetId(), second.getSvgBySheetId());
    assertArrayEquals(first.getPdf(), second.getPdf());
    assertEquals(first.getVisualFingerprintsBySheetId(), second.getVisualFingerprintsBySheetId());
    assertEquals(first.getSvgBySheetId().keySet(), first.getVisualFingerprintsBySheetId().keySet());
    assertTrue(first.getVisualFingerprintsBySheetId().values().iterator().next().matches("[0-9a-f]{64}"));
    assertTrue(svg.contains("width=\"420mm\" height=\"297mm\" viewBox=\"0 0 420 297\""));
    assertTrue(svg.contains("x=\"63\" y=\"52\" width=\"34\" height=\"16\""));
    assertTrue(svg.contains("points=\"10,20 40,20 40,50\""));
    assertTrue(svg.contains("data-protected-route=\"true\""));
    assertTrue(svg.contains("REV A  STATUS WORKING"));
    assertTrue(svg.contains("ENGINEERING PROPOSAL - NOT APPROVED FOR DESIGN OR CONSTRUCTION"));
    assertTrue(new String(first.getPdf(), 0, 8, StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"));
    assertTrue(first.isComplete());
    assertEquals(controlledJson, documents.toJson());
    assertEquals(classicDot, process.toDOT());
  }

  @Test
  void rendersEveryMultiAreaSheetWithReciprocalOffPageReferencesInOnePdf() {
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessModel(
        EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(), "DEXPI-REF-MULTI-AREA", "B",
        "PFD-NATIVE-002", "Multi-area native drawing set", ContentProfile.PFD);

    NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.SheetFormat.A1_LANDSCAPE).render();
    String pdf = new String(result.getPdf(), StandardCharsets.ISO_8859_1);

    assertEquals(4, result.getSvgBySheetId().size());
    assertEquals(4, result.getVisualFingerprintsBySheetId().size());
    assertTrue(pdf.contains("/Count 4"));
    for (Sheet sheet : documents.getDrawings().get(0).getSheets()) {
      String svg = result.getSvgBySheetId().get(sheet.getId());
      assertTrue(svg.contains("width=\"841mm\" height=\"594mm\" viewBox=\"0 0 841 594\""));
      for (EngineeringDiagramDocumentSet.OffPageConnector connector : sheet.getOffPageConnectors()) {
        assertTrue(svg.contains(connector.getId()));
        assertTrue(svg.contains(connector.getPeerSheetId()));
        assertTrue(svg.contains(connector.getZoneReference()));
      }
    }
    assertTrue(result.isComplete());
  }

  @Test
  void reportsOutOfBoundsManualGeometryWithoutSilentlyReplacingIt() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-003", "Layout loss diagnostics",
        ContentProfile.PFD);
    String sheetKey = baseline.getDrawings().get(0).getSheets().get(0).getKey();
    SemanticObject separator = findObject(baseline, EngineeringNode.Kind.EQUIPMENT, "equipmentName", "10-VA-001");
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withPinnedPosition(reviewedPosition(separator.getId(), sheetKey, 600.0, 60.0));
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-003", "Layout loss diagnostics",
        ContentProfile.PFD, new EngineeringDiagramDesignationRegister(), layout);

    NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents).render();
    String svg = result.getSvgBySheetId().values().iterator().next();

    assertTrue(hasDiagnostic(result, "DIAGRAM_RENDER_PIN_OUTSIDE_SHEET"));
    assertTrue(svg.contains("x=\"583\" y=\"52\" width=\"34\" height=\"16\""));
    assertTrue(result.isComplete());
  }

  @Test
  void reportsDeterministicCollisionClippingAndReadabilityDiagnostics() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-005", "Drawing quality diagnostics",
        ContentProfile.PFD);
    String sheetKey = baseline.getDrawings().get(0).getSheets().get(0).getKey();
    SemanticObject separator = findObject(baseline, EngineeringNode.Kind.EQUIPMENT, "equipmentName", "10-VA-001");
    SemanticObject valve = findObject(baseline, EngineeringNode.Kind.EQUIPMENT, "equipmentName", "10-XV-001");
    EngineeringDiagramDesignationRegister designations = new EngineeringDiagramDesignationRegister()
        .withDesignation(new Designation(separator.getId(), Kind.EQUIPMENT_TAG, "10-VERY-LONG-SEPARATOR-DESIGNATION",
            "equipment-register:PFD-NATIVE-005", ReviewState.REVIEWED, "Process discipline", "review:PFD-NATIVE-005",
            "2026-08-14T18:00:00Z", "A"));
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withPinnedPosition(reviewedPosition(separator.getId(), sheetKey, 10.0, 60.0))
        .withPinnedPosition(reviewedPosition(valve.getId(), sheetKey, 10.0, 60.0));
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-005", "Drawing quality diagnostics",
        ContentProfile.PFD, designations, layout);

    NativeEngineeringDiagramRenderer renderer = new NativeEngineeringDiagramRenderer(documents);
    NativeEngineeringDiagramRenderer.Result first = renderer.render();
    NativeEngineeringDiagramRenderer.Result second = renderer.render();

    assertTrue(hasDiagnostic(first, "DIAGRAM_RENDER_OBJECT_COLLISION"));
    assertTrue(hasDiagnostic(first, "DIAGRAM_RENDER_OBJECT_CLIPPED"));
    assertTrue(hasDiagnostic(first, "DIAGRAM_RENDER_LABEL_OVERFLOW"));
    assertEquals(diagnosticSignatures(first), diagnosticSignatures(second));
    assertTrue(first.isComplete());
  }

  @Test
  void labelsConnectionsAndReportsDeterministicRouteAndLabelObstacles() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-007", "Route quality diagnostics",
        ContentProfile.PFD);
    String sheetKey = baseline.getDrawings().get(0).getSheets().get(0).getKey();
    SemanticObject separator = findObject(baseline, EngineeringNode.Kind.EQUIPMENT, "equipmentName", "10-VA-001");
    SemanticObject feedConnection = findObject(baseline, EngineeringNode.Kind.PIPE_SEGMENT, "targetEquipment",
        "10-XV-001");
    ProtectedRoute obstructedRoute = new ProtectedRoute(feedConnection.getId(), sheetKey,
        Arrays.asList(new Waypoint(20.0, 60.0), new Waypoint(140.0, 60.0)), CoordinateUnit.MILLIMETRE,
        "project-layout:PFD-NATIVE-007", EvidenceState.REVIEWED, "Process discipline", "2026-08-15T00:00:00Z", "A");
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withPinnedPosition(reviewedPosition(separator.getId(), sheetKey, 80.0, 60.0))
        .withProtectedRoute(obstructedRoute);
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-007", "Route quality diagnostics",
        ContentProfile.PFD, new EngineeringDiagramDesignationRegister(), layout);

    NativeEngineeringDiagramRenderer renderer = new NativeEngineeringDiagramRenderer(documents);
    NativeEngineeringDiagramRenderer.Result first = renderer.render();
    NativeEngineeringDiagramRenderer.Result second = renderer.render();
    String svg = first.getSvgBySheetId().values().iterator().next();

    assertTrue(svg.contains(">" + feedConnection.getLabel() + "</text>"));
    assertTrue(hasDiagnostic(first, "DIAGRAM_RENDER_ROUTE_OBJECT_INTERSECTION"));
    assertTrue(hasDiagnostic(first, "DIAGRAM_RENDER_ROUTE_LABEL_OBJECT_COLLISION"));
    assertEquals(first.getVisualFingerprintsBySheetId(), second.getVisualFingerprintsBySheetId());
    assertEquals(diagnosticSignatures(first), diagnosticSignatures(second));
    assertTrue(first.isComplete());
  }

  @Test
  void propagatesBrokenControlledDocumentReferencesAsRendererErrors() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-006", "Broken reference diagnostics",
        ContentProfile.PFD);
    String sheetKey = baseline.getDrawings().get(0).getSheets().get(0).getKey();
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withPinnedPosition(reviewedPosition("missing-semantic-object", sheetKey, 80.0, 60.0));
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-006", "Broken reference diagnostics",
        ContentProfile.PFD, new EngineeringDiagramDesignationRegister(), layout);

    NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents).render();

    assertTrue(hasDiagnostic(result, "DIAGRAM_DOCUMENT_LAYOUT_UNKNOWN_OBJECT"));
    assertFalse(result.isComplete());
  }

  @Test
  void keepsSheetOrderAndRenderedBytesStableAcrossFreshEquivalentModels() {
    Map<String, String> expectedSvg = null;
    Map<String, String> expectedVisualFingerprints = null;
    byte[] expectedPdf = null;
    for (int attempt = 0; attempt < 4; attempt++) {
      EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessModel(
          EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(), "DEXPI-REF-MULTI-AREA", "A",
          "PFD-NATIVE-004", "Fresh deterministic rendering", ContentProfile.PFD);
      NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents).render();
      if (expectedSvg == null) {
        expectedSvg = result.getSvgBySheetId();
        expectedVisualFingerprints = result.getVisualFingerprintsBySheetId();
        expectedPdf = result.getPdf();
      } else {
        assertEquals(expectedSvg, result.getSvgBySheetId());
        assertEquals(expectedVisualFingerprints, result.getVisualFingerprintsBySheetId());
        assertArrayEquals(expectedPdf, result.getPdf());
      }
    }
  }

  @Test
  void supportsOptInFixedPortOrthogonalRoutingForBranchesWithoutChangingLegacyDefault() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures
        .branchedSeparatorCompressionTrain();
    ProcessSystem process = reference.getProcessSystem();
    String classicDot = process.toDOT();
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        reference.getCaseId(), "A", "PFD-NATIVE-008", "Fixed port routing reference", ContentProfile.PFD);

    NativeEngineeringDiagramRenderer.Result defaultResult = new NativeEngineeringDiagramRenderer(documents).render();
    NativeEngineeringDiagramRenderer.Result explicitLegacy = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.RoutingMode.LEGACY_CENTER).render();
    NativeEngineeringDiagramRenderer.Result first = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL).render();
    NativeEngineeringDiagramRenderer.Result second = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL).render();
    String svg = first.getSvgBySheetId().values().iterator().next();

    List<String> separatorOutletIds = new ArrayList<String>();
    for (SemanticObject object : documents.getSemanticObjects()) {
      if (object.getKind() == EngineeringNode.Kind.PIPE_SEGMENT
          && "20-VA-001".equals(object.getProperties().get("sourceEquipment"))) {
        separatorOutletIds.add(String.valueOf(object.getProperties().get("sourceEndpointId")));
      }
    }

    assertEquals(2, separatorOutletIds.size());
    assertNotEquals(separatorOutletIds.get(0), separatorOutletIds.get(1));
    for (String endpointId : separatorOutletIds) {
      assertTrue(svg.contains("data-semantic-id=\"" + endpointId + "\""));
    }
    assertEquals(defaultResult.getSvgBySheetId(), explicitLegacy.getSvgBySheetId());
    assertArrayEquals(defaultResult.getPdf(), explicitLegacy.getPdf());
    assertEquals(defaultResult.getVisualFingerprintsBySheetId(), explicitLegacy.getVisualFingerprintsBySheetId());
    assertEquals(first.getSvgBySheetId(), second.getSvgBySheetId());
    assertArrayEquals(first.getPdf(), second.getPdf());
    assertEquals(first.getVisualFingerprintsBySheetId(), second.getVisualFingerprintsBySheetId());
    assertNotEquals(defaultResult.getVisualFingerprintsBySheetId(), first.getVisualFingerprintsBySheetId());
    assertTrue(first.isComplete());
    assertEquals(classicDot, process.toDOT());
  }

  private static PinnedPosition reviewedPosition(String semanticObjectId, String sheetKey, double x, double y) {
    return new PinnedPosition(semanticObjectId, sheetKey, x, y, CoordinateUnit.MILLIMETRE, "project-layout:PFD-NATIVE",
        EvidenceState.REVIEWED, "Process discipline", "2026-08-14T08:00:00Z", "B");
  }

  private static ProtectedRoute reviewedRoute(String connectionId, String sheetKey) {
    return new ProtectedRoute(connectionId, sheetKey,
        Arrays.asList(new Waypoint(10.0, 20.0), new Waypoint(40.0, 20.0), new Waypoint(40.0, 50.0)),
        CoordinateUnit.MILLIMETRE, "project-layout:PFD-NATIVE", EvidenceState.REVIEWED, "Process discipline",
        "2026-08-14T08:00:00Z", "B");
  }

  private static SemanticObject findObject(EngineeringDiagramDocumentSet documents, EngineeringNode.Kind kind,
      String property, String value) {
    for (SemanticObject object : documents.getSemanticObjects()) {
      if (object.getKind() == kind && value.equals(object.getProperties().get(property))) {
        return object;
      }
    }
    throw new AssertionError("Missing semantic object " + kind + " with " + property + "=" + value);
  }

  private static boolean hasDiagnostic(NativeEngineeringDiagramRenderer.Result result, String code) {
    for (NativeEngineeringDiagramRenderer.Diagnostic diagnostic : result.getDiagnostics()) {
      if (code.equals(diagnostic.getCode())) {
        assertFalse(diagnostic.getMessage().isEmpty());
        assertFalse(diagnostic.getSubjectId().isEmpty());
        return true;
      }
    }
    return false;
  }

  private static List<String> diagnosticSignatures(NativeEngineeringDiagramRenderer.Result result) {
    List<String> signatures = new ArrayList<String>();
    for (NativeEngineeringDiagramRenderer.Diagnostic diagnostic : result.getDiagnostics()) {
      signatures.add(diagnostic.getSeverity().name() + ":" + diagnostic.getCode() + ":" + diagnostic.getSubjectId());
    }
    return signatures;
  }
}
