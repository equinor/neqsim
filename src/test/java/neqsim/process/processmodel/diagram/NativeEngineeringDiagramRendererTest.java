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
import java.util.TreeMap;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolConvention;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolShape;
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
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetAssignment;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetDefinition;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetOverviewRegion;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.Waypoint;
import neqsim.process.engineering.model.EngineeringGraph;
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
        NativeEngineeringDiagramRenderer.SheetFormat.A1_LANDSCAPE,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL).render();
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
    assertFalse(hasDiagnostic(result, "DIAGRAM_RENDER_FIXED_PORT_UNRESOLVED"));
    assertTrue(result.isComplete());
  }

  @Test
  void rendersControlledOverviewRegionsAsSheetIndexesWithoutClaimingProcessConnectivity() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDocumentSet baseline = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-OVERVIEW", "Overview index reference",
        ContentProfile.PFD);
    Sheet overview = baseline.getDrawings().get(0).getSheets().get(0);
    SemanticObject separator = findObject(baseline, EngineeringNode.Kind.EQUIPMENT, "equipmentName", "10-VA-001");
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withSheet(new SheetDefinition("separator-detail", "2", "Separator detail", "project-layout:overview",
            EvidenceState.REVIEWED, "Process discipline", "2026-09-09T00:00:00Z", "B"))
        .withAssignment(new SheetAssignment(separator.getId(), "separator-detail", "project-layout:overview",
            EvidenceState.REVIEWED, "Process discipline", "2026-09-09T00:00:00Z", "B"))
        .withOverviewRegion(new SheetOverviewRegion(overview.getKey(), "separator-detail", 40.0, 90.0, 330.0, 120.0,
            CoordinateUnit.MILLIMETRE, "project-layout:overview", EvidenceState.REVIEWED, "Process discipline",
            "2026-09-09T00:00:00Z", "B"));
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(
        reference.getProcessSystem(), reference.getCaseId(), "A", "PFD-NATIVE-OVERVIEW", "Overview index reference",
        ContentProfile.PFD, new EngineeringDiagramDesignationRegister(), layout);

    NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents).render();
    String svg = result.getSvgBySheetId().get(documents.getDrawings().get(0).getSheets().get(0).getId());

    assertEquals(1, documents.getDrawings().get(0).getSheets().get(0).getOverviewRegions().size());
    assertTrue(svg.contains("CONTROLLED SHEET INDEX - NOT PROCESS CONNECTIVITY"));
    assertTrue(svg.contains("SHEET 2 - Separator detail"));
    assertTrue(svg.contains("10-VA-001"));
    assertTrue(svg.contains("data-semantic-id=\"overview-region:"));
    assertTrue(result.isComplete());
  }

  @Test
  void rendersControlledDirectionalOffPageLabelsWithoutExposingInternalSheetIds() {
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessModel(
        EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(), "DEXPI-REF-MULTI-AREA", "B",
        "PFD-NATIVE-002-CONTROLLED", "Controlled off-page reference", ContentProfile.PFD);
    EngineeringDiagramConventionRegister conventions = new EngineeringDiagramConventionRegister()
        .withConvention(new SymbolConvention(EngineeringNode.Kind.LINE, SymbolShape.LINE_TERMINAL, "#1f2937", "#eff6ff",
            "teaching-symbol-profile:v1", EngineeringDiagramConventionRegister.EvidenceState.PROPOSED, "", "",
            "2026-09-08T00:00:00Z", "A"));

    NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.SheetFormat.A1_LANDSCAPE, conventions,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL).render();
    Map<String, String> sheetNumberById = new TreeMap<String, String>();
    Map<String, SemanticObject> objectsById = new TreeMap<String, SemanticObject>();
    for (SemanticObject object : documents.getSemanticObjects()) {
      objectsById.put(object.getId(), object);
    }
    for (Sheet sheet : documents.getDrawings().get(0).getSheets()) {
      sheetNumberById.put(sheet.getId(), sheet.getNumber());
    }

    for (Sheet sheet : documents.getDrawings().get(0).getSheets()) {
      String svg = result.getSvgBySheetId().get(sheet.getId());
      assertFalse(svg.contains("TO/FROM"));
      for (EngineeringDiagramDocumentSet.OffPageConnector connector : sheet.getOffPageConnectors()) {
        SemanticObject connection = objectsById.get(connector.getSemanticConnectionId());
        String direction = connector.getRole() == EngineeringDiagramDocumentSet.ConnectorRole.SOURCE ? "TO" : "FROM";
        String expected = connection.getLabel() + " " + direction + " SHEET "
            + sheetNumberById.get(connector.getPeerSheetId());
        assertTrue(svg.contains(">" + expected + "</text>"), expected);
        assertFalse(svg.contains("[" + connector.getPeerSheetId() + "]"));
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

  @Test
  void separatesParallelOwnerPairsAndReturnsRecycleRoutesDeterministically() {
    EngineeringDiagramDocumentSet documents = EngineeringDiagramDocumentSet.fromGraph(fixedPortRoutingGraph(),
        "PFD-NATIVE-009", "Parallel and recycle routing reference", ContentProfile.PFD);
    NativeEngineeringDiagramRenderer.Result first = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL).render();
    NativeEngineeringDiagramRenderer.Result second = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL).render();
    String svg = first.getSvgBySheetId().values().iterator().next();

    String firstParallel = pointsForSemanticId(svg, "connection:parallel-1");
    String secondParallel = pointsForSemanticId(svg, "connection:parallel-2");
    String recycle = pointsForSemanticId(svg, "connection:recycle");

    assertNotEquals(firstParallel, secondParallel);
    assertNotEquals(pointX(firstParallel, 1), pointX(secondParallel, 1));
    assertTrue(recycle.split(" ").length >= 6);
    assertEquals(first.getSvgBySheetId(), second.getSvgBySheetId());
    assertArrayEquals(first.getPdf(), second.getPdf());
    assertEquals(first.getVisualFingerprintsBySheetId(), second.getVisualFingerprintsBySheetId());
    assertTrue(first.isComplete());
  }

  @Test
  void placesFixedPortRouteLabelsOnHorizontalSegmentsInsteadOfCongestedVerticalTrunks() {
    EngineeringGraph graph = fixedPortRoutingGraph();
    EngineeringDiagramDocumentSet baseline = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-NATIVE-010",
        "Collision-aware route-label reference", ContentProfile.PFD);
    String sheetKey = baseline.getDrawings().get(0).getSheets().get(0).getKey();
    EngineeringDiagramLayoutRegister layout = new EngineeringDiagramLayoutRegister()
        .withPinnedPosition(reviewedPosition("equipment:a", sheetKey, 80.0, 60.0))
        .withPinnedPosition(reviewedPosition("equipment:b", sheetKey, 250.0, 180.0));
    EngineeringDiagramDocumentSet documents = EngineeringDiagramDocumentSet.fromGraph(graph, "PFD-NATIVE-010",
        "Collision-aware route-label reference", ContentProfile.PFD, new EngineeringDiagramDesignationRegister(),
        layout);

    NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL).render();
    String svg = result.getSvgBySheetId().values().iterator().next();
    String route = pointsForSemanticId(svg, "connection:parallel-1");
    double labelX = textCoordinateForSemanticId(svg, "connection:parallel-1", "x");
    double labelY = textCoordinateForSemanticId(svg, "connection:parallel-1", "y") + 3.0;

    assertTrue(pointLiesOnHorizontalSegment(route, labelX, labelY),
        "route label should follow a horizontal process-line segment: " + route);
    assertTrue(result.isComplete());
  }

  @Test
  void alignsSingleOffPageConnectorsWithTheirLocalEndpoints() {
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessModel(
        EngineeringDiagramReferenceFixtures.multiAreaFacility().getProcessModel(), "DEXPI-REF-MULTI-AREA", "A",
        "PFD-NATIVE-011", "Endpoint-aligned off-page connector reference", ContentProfile.PFD);
    NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.SheetFormat.A1_LANDSCAPE,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL).render();
    int alignedConnectors = 0;

    for (Sheet sheet : documents.getDrawings().get(0).getSheets()) {
      Map<EngineeringDiagramDocumentSet.ConnectorRole, Integer> counts = new TreeMap<EngineeringDiagramDocumentSet.ConnectorRole, Integer>();
      for (EngineeringDiagramDocumentSet.OffPageConnector connector : sheet.getOffPageConnectors()) {
        Integer count = counts.get(connector.getRole());
        counts.put(connector.getRole(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
      }
      String svg = result.getSvgBySheetId().get(sheet.getId());
      Map<EngineeringDiagramDocumentSet.ConnectorRole, List<Double>> connectorY = new TreeMap<EngineeringDiagramDocumentSet.ConnectorRole, List<Double>>();
      for (EngineeringDiagramDocumentSet.OffPageConnector connector : sheet.getOffPageConnectors()) {
        List<Double> sameSide = connectorY.get(connector.getRole());
        if (sameSide == null) {
          sameSide = new ArrayList<Double>();
          connectorY.put(connector.getRole(), sameSide);
        }
        sameSide.add(Double.valueOf(pointY(pointsForSemanticId(svg, connector.getId()).split(" ")[0])));
        if (counts.get(connector.getRole()).intValue() != 1) {
          continue;
        }
        String route = pointsForSemanticId(svg, connector.getSemanticConnectionId());
        String[] routePoints = route.split(" ");
        int localIndex = connector.getRole() == EngineeringDiagramDocumentSet.ConnectorRole.SOURCE ? 0
            : routePoints.length - 1;
        int connectorIndex = connector.getRole() == EngineeringDiagramDocumentSet.ConnectorRole.SOURCE
            ? routePoints.length - 1
            : 0;
        assertEquals(pointY(routePoints[localIndex]), pointY(routePoints[connectorIndex]), 0.0000001,
            connector.getId());
        alignedConnectors++;
      }
      for (List<Double> sameSide : connectorY.values()) {
        for (int left = 0; left < sameSide.size(); left++) {
          for (int right = left + 1; right < sameSide.size(); right++) {
            assertTrue(Math.abs(sameSide.get(left).doubleValue() - sameSide.get(right).doubleValue()) >= 14.0);
          }
        }
      }
    }

    assertTrue(alignedConnectors > 0);
    assertTrue(result.isComplete());
  }

  private static EngineeringGraph fixedPortRoutingGraph() {
    EngineeringGraph graph = new EngineeringGraph("FIXED-PORT-ROUTING", "A");
    graph.addNode(new EngineeringNode("equipment:a", EngineeringNode.Kind.EQUIPMENT, "a", "Equipment A")
        .putProperty("equipmentName", "A"));
    graph.addNode(new EngineeringNode("equipment:b", EngineeringNode.Kind.EQUIPMENT, "b", "Equipment B")
        .putProperty("equipmentName", "B"));
    graph.addNode(endpointNode("nozzle:a-out-1", "equipment:a", "OUTLET"));
    graph.addNode(endpointNode("nozzle:a-out-2", "equipment:a", "OUTLET"));
    graph.addNode(endpointNode("nozzle:a-in", "equipment:a", "INLET"));
    graph.addNode(endpointNode("nozzle:b-in-1", "equipment:b", "INLET"));
    graph.addNode(endpointNode("nozzle:b-in-2", "equipment:b", "INLET"));
    graph.addNode(endpointNode("nozzle:b-out", "equipment:b", "OUTLET"));
    graph.addNode(connectionNode("connection:parallel-1", "nozzle:a-out-1", "nozzle:b-in-1", "A", "B", false));
    graph.addNode(connectionNode("connection:parallel-2", "nozzle:a-out-2", "nozzle:b-in-2", "A", "B", false));
    graph.addNode(connectionNode("connection:recycle", "nozzle:b-out", "nozzle:a-in", "B", "A", true));
    return graph;
  }

  private static EngineeringNode endpointNode(String id, String ownerId, String direction) {
    return new EngineeringNode(id, EngineeringNode.Kind.NOZZLE, id, id).putProperty("ownerNodeId", ownerId)
        .putProperty("direction", direction).putProperty("connectionType", "MATERIAL");
  }

  private static EngineeringNode connectionNode(String id, String sourceEndpointId, String targetEndpointId,
      String sourceEquipment, String targetEquipment, boolean recycle) {
    return new EngineeringNode(id, EngineeringNode.Kind.PIPE_SEGMENT, id, id).putProperty("connectionType", "MATERIAL")
        .putProperty("sourceEndpointId", sourceEndpointId).putProperty("targetEndpointId", targetEndpointId)
        .putProperty("sourceEquipment", sourceEquipment).putProperty("targetEquipment", targetEquipment)
        .putProperty("recycle", Boolean.valueOf(recycle));
  }

  private static String pointsForSemanticId(String svg, String semanticId) {
    String identity = "data-semantic-id=\"" + semanticId + "\"";
    int identityIndex = svg.indexOf(identity);
    int elementStart = svg.lastIndexOf("<polyline", identityIndex);
    int pointsStart = svg.indexOf("points=\"", elementStart) + "points=\"".length();
    int pointsEnd = svg.indexOf('"', pointsStart);
    assertTrue(identityIndex >= 0);
    assertTrue(elementStart >= 0);
    assertTrue(pointsStart >= "points=\"".length());
    assertTrue(pointsEnd > pointsStart);
    return svg.substring(pointsStart, pointsEnd);
  }

  private static String pointX(String points, int index) {
    return points.split(" ")[index].split(",", 2)[0];
  }

  private static double pointY(String point) {
    String[] coordinates = point.split(",", 2);
    assertTrue(coordinates.length == 2, "Malformed SVG point: " + point);
    return parseCoordinate(coordinates[1], "point y in '" + point + "'");
  }

  private static double textCoordinateForSemanticId(String svg, String semanticId, String coordinate) {
    String identity = "data-semantic-id=\"" + semanticId + "\"";
    int elementStart = svg.indexOf("<text");
    while (elementStart >= 0) {
      int elementEnd = svg.indexOf('>', elementStart);
      if (elementEnd > elementStart && svg.substring(elementStart, elementEnd).contains(identity)) {
        break;
      }
      elementStart = svg.indexOf("<text", elementEnd);
    }
    assertTrue(elementStart >= 0);
    String attribute = coordinate + "=\"";
    int valueStart = svg.indexOf(attribute, elementStart) + attribute.length();
    int valueEnd = svg.indexOf('"', valueStart);
    assertTrue(valueStart >= attribute.length());
    assertTrue(valueEnd > valueStart);
    String value = svg.substring(valueStart, valueEnd);
    return parseCoordinate(value, coordinate + " for " + semanticId);
  }

  private static boolean pointLiesOnHorizontalSegment(String points, double x, double y) {
    String[] vertices = points.split(" ");
    for (int index = 1; index < vertices.length; index++) {
      String[] start = vertices[index - 1].split(",", 2);
      String[] end = vertices[index].split(",", 2);
      String context = "polyline segment '" + vertices[index - 1] + " " + vertices[index] + "'";
      assertTrue(start.length == 2 && end.length == 2, "Malformed SVG " + context);
      double startX = parseCoordinate(start[0], context);
      double startY = parseCoordinate(start[1], context);
      double endX = parseCoordinate(end[0], context);
      double endY = parseCoordinate(end[1], context);
      if (Math.abs(startY - endY) < 0.0000001 && Math.abs(y - startY) < 0.0000001 && x >= Math.min(startX, endX)
          && x <= Math.max(startX, endX)) {
        return true;
      }
    }
    return false;
  }

  private static double parseCoordinate(String value, String context) {
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException error) {
      throw new AssertionError("Non-numeric SVG coordinate '" + value + "' for " + context, error);
    }
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
