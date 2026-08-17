package neqsim.process.processmodel.diagram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolConvention;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolShape;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Drawing;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.OffPageConnector;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.SemanticObject;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Sheet;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.PinnedPosition;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.ProtectedRoute;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.Waypoint;
import neqsim.process.engineering.model.EngineeringNode;

/**
 * Deterministic native SVG and PDF renderer for controlled engineering-diagram document sets.
 *
 * <p>
 * The renderer consumes the immutable semantic document model directly. It does not invoke Graphviz and therefore
 * preserves stable sheet identities, pinned millimetre positions, protected routes, reciprocal off-page references, and
 * controlled title/revision metadata. Automatically placed objects use a deterministic grid. Native output is an
 * engineering proposal unless the source document set carries accountable approval evidence; rendering does not qualify
 * symbols, layout, or content against ISO 10628 or any other drawing standard.
 * </p>
 */
public final class NativeEngineeringDiagramRenderer {
  private static final double MM_TO_POINT = 72.0 / 25.4;
  private static final double CONTENT_LEFT = 16.0;
  private static final double CONTENT_TOP = 24.0;
  private static final double TITLE_BLOCK_HEIGHT = 30.0;
  private static final double OBJECT_WIDTH = 34.0;
  private static final double OBJECT_HEIGHT = 16.0;
  private static final double PORT_MARKER_SIZE = 1.8;
  private static final double PORT_SLOT_MARGIN = 2.0;
  private static final double PARALLEL_LANE_SPACING = 4.0;

  /** Controlled paper sizes supported by the native renderer. */
  public enum SheetFormat {
    /** ISO A3 landscape paper geometry (420 x 297 mm); no conformance claim is implied. */
    A3_LANDSCAPE(420.0, 297.0),
    /** ISO A1 landscape paper geometry (841 x 594 mm); no conformance claim is implied. */
    A1_LANDSCAPE(841.0, 594.0);

    private final double widthMillimetres;
    private final double heightMillimetres;

    SheetFormat(double widthMillimetres, double heightMillimetres) {
      this.widthMillimetres = widthMillimetres;
      this.heightMillimetres = heightMillimetres;
    }

    /** @return sheet width in drawing-paper millimetres */
    public double getWidthMillimetres() {
      return widthMillimetres;
    }

    /** @return sheet height in drawing-paper millimetres */
    public double getHeightMillimetres() {
      return heightMillimetres;
    }
  }

  /** Connection-routing behavior. */
  public enum RoutingMode {
    /** Byte-compatible center-to-center routing used by the original native renderer. */
    LEGACY_CENTER,
    /**
     * Orthogonal routing from stable canonical port/nozzle identities at their owner-symbol bounds.
     *
     * <p>
     * Parallel owner pairs receive deterministic lanes and recycle/backward connections receive a deterministic return
     * path. Protected project routes remain authoritative.
     * </p>
     */
    FIXED_PORT_ORTHOGONAL
  }

  /** Renderer diagnostic severity. */
  public enum Severity {
    /** Informational source-document evidence retained in the rendering report. */
    INFO,
    /** Recoverable rendering limitation requiring review. */
    WARNING,
    /** Broken source or rendering state that prevents a complete view. */
    ERROR
  }

  /** Immutable structured rendering diagnostic. */
  public static final class Diagnostic {
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subjectId;

    private Diagnostic(Severity severity, String code, String message, String subjectId) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subjectId = subjectId;
    }

    /** @return diagnostic severity */
    public Severity getSeverity() {
      return severity;
    }

    /** @return stable machine-readable diagnostic code */
    public String getCode() {
      return code;
    }

    /** @return human-readable diagnostic message */
    public String getMessage() {
      return message;
    }

    /** @return stable source subject identity, or an empty string */
    public String getSubjectId() {
      return subjectId;
    }
  }

  /** Immutable rendering result containing every SVG sheet, one PDF drawing set, and diagnostics. */
  public static final class Result {
    private final Map<String, String> svgBySheetId;
    private final byte[] pdf;
    private final Map<String, String> visualFingerprintsBySheetId;
    private final List<Diagnostic> diagnostics;

    private Result(Map<String, String> svgBySheetId, byte[] pdf, Map<String, String> visualFingerprintsBySheetId,
        List<Diagnostic> diagnostics) {
      this.svgBySheetId = Collections.unmodifiableMap(new LinkedHashMap<String, String>(svgBySheetId));
      this.pdf = Arrays.copyOf(pdf, pdf.length);
      this.visualFingerprintsBySheetId = Collections
          .unmodifiableMap(new LinkedHashMap<String, String>(visualFingerprintsBySheetId));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
    }

    /** @return deterministic sheet-ID-ordered native SVG documents */
    public Map<String, String> getSvgBySheetId() {
      return svgBySheetId;
    }

    /** @return defensive copy of the deterministic multi-page native PDF */
    public byte[] getPdf() {
      return Arrays.copyOf(pdf, pdf.length);
    }

    /**
     * Returns normalized visual fingerprints for reviewed-baseline comparison.
     *
     * <p>
     * Each SHA-256 fingerprint covers visible page geometry, text and style shared by the SVG and PDF renderers while
     * excluding serialization syntax and invisible semantic identifiers.
     * </p>
     *
     * @return immutable stable sheet-ID-to-fingerprint map
     */
    public Map<String, String> getVisualFingerprintsBySheetId() {
      return visualFingerprintsBySheetId;
    }

    /** @return immutable structured rendering diagnostics */
    public List<Diagnostic> getDiagnostics() {
      return diagnostics;
    }

    /** @return {@code true} when no renderer error was recorded */
    public boolean isComplete() {
      for (Diagnostic diagnostic : diagnostics) {
        if (diagnostic.getSeverity() == Severity.ERROR) {
          return false;
        }
      }
      return true;
    }
  }

  private final EngineeringDiagramDocumentSet documentSet;
  private final SheetFormat format;
  private final EngineeringDiagramConventionRegister conventionRegister;
  private final RoutingMode routingMode;

  /**
   * Creates an A3-landscape native renderer using byte-compatible legacy rectangle defaults.
   *
   * @param documentSet immutable controlled engineering-diagram document set
   */
  public NativeEngineeringDiagramRenderer(EngineeringDiagramDocumentSet documentSet) {
    this(documentSet, SheetFormat.A3_LANDSCAPE, new EngineeringDiagramConventionRegister(), RoutingMode.LEGACY_CENTER);
  }

  /**
   * Creates a native renderer with explicit controlled sheet geometry and legacy rectangle defaults.
   *
   * @param documentSet immutable controlled engineering-diagram document set
   * @param format paper geometry
   */
  public NativeEngineeringDiagramRenderer(EngineeringDiagramDocumentSet documentSet, SheetFormat format) {
    this(documentSet, format, new EngineeringDiagramConventionRegister(), RoutingMode.LEGACY_CENTER);
  }

  /**
   * Creates an A3-landscape renderer with explicit routing behavior and legacy symbol defaults.
   *
   * @param documentSet immutable controlled engineering-diagram document set
   * @param routingMode connection-routing behavior
   */
  public NativeEngineeringDiagramRenderer(EngineeringDiagramDocumentSet documentSet, RoutingMode routingMode) {
    this(documentSet, SheetFormat.A3_LANDSCAPE, new EngineeringDiagramConventionRegister(), routingMode);
  }

  /**
   * Creates a renderer with explicit sheet geometry and routing behavior.
   *
   * @param documentSet immutable controlled engineering-diagram document set
   * @param format paper geometry
   * @param routingMode connection-routing behavior
   */
  public NativeEngineeringDiagramRenderer(EngineeringDiagramDocumentSet documentSet, SheetFormat format,
      RoutingMode routingMode) {
    this(documentSet, format, new EngineeringDiagramConventionRegister(), routingMode);
  }

  /**
   * Creates an A3-landscape native renderer with explicit project symbol conventions.
   *
   * @param documentSet immutable controlled engineering-diagram document set
   * @param conventionRegister evidence-bearing project symbol conventions
   */
  public NativeEngineeringDiagramRenderer(EngineeringDiagramDocumentSet documentSet,
      EngineeringDiagramConventionRegister conventionRegister) {
    this(documentSet, SheetFormat.A3_LANDSCAPE, conventionRegister, RoutingMode.LEGACY_CENTER);
  }

  /**
   * Creates an A3-landscape renderer with project symbol conventions and explicit routing behavior.
   *
   * @param documentSet immutable controlled engineering-diagram document set
   * @param conventionRegister evidence-bearing project symbol conventions
   * @param routingMode connection-routing behavior
   */
  public NativeEngineeringDiagramRenderer(EngineeringDiagramDocumentSet documentSet,
      EngineeringDiagramConventionRegister conventionRegister, RoutingMode routingMode) {
    this(documentSet, SheetFormat.A3_LANDSCAPE, conventionRegister, routingMode);
  }

  /**
   * Creates a native renderer with explicit sheet geometry and project symbol conventions.
   *
   * <p>
   * An empty convention register preserves the legacy rectangle bytes. A non-empty register applies only its exact
   * canonical-node-kind mappings and reports every visible unmapped node through a structured fallback diagnostic.
   * Generic renderer-native shapes do not claim standards conformance or drawing approval.
   * </p>
   *
   * @param documentSet immutable controlled engineering-diagram document set
   * @param format paper geometry
   * @param conventionRegister evidence-bearing project symbol conventions
   */
  public NativeEngineeringDiagramRenderer(EngineeringDiagramDocumentSet documentSet, SheetFormat format,
      EngineeringDiagramConventionRegister conventionRegister) {
    this(documentSet, format, conventionRegister, RoutingMode.LEGACY_CENTER);
  }

  /**
   * Creates a renderer with explicit sheet geometry, project symbol conventions and routing behavior.
   *
   * @param documentSet immutable controlled engineering-diagram document set
   * @param format paper geometry
   * @param conventionRegister evidence-bearing project symbol conventions
   * @param routingMode connection-routing behavior
   */
  public NativeEngineeringDiagramRenderer(EngineeringDiagramDocumentSet documentSet, SheetFormat format,
      EngineeringDiagramConventionRegister conventionRegister, RoutingMode routingMode) {
    if (documentSet == null) {
      throw new IllegalArgumentException("documentSet must not be null");
    }
    if (format == null) {
      throw new IllegalArgumentException("format must not be null");
    }
    if (conventionRegister == null) {
      throw new IllegalArgumentException("conventionRegister must not be null");
    }
    if (routingMode == null) {
      throw new IllegalArgumentException("routingMode must not be null");
    }
    this.documentSet = documentSet;
    this.format = format;
    this.conventionRegister = conventionRegister;
    this.routingMode = routingMode;
  }

  /**
   * Renders every controlled sheet to native SVG and the drawing set to one multi-page native PDF.
   *
   * @return deterministic rendering result
   */
  public Result render() {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    addDocumentDiagnostics(diagnostics);
    Map<String, SemanticObject> objects = objectsById();
    addConventionDiagnostics(objects, diagnostics);
    List<Page> pages = new ArrayList<Page>();
    for (Drawing drawing : documentSet.getDrawings()) {
      for (Sheet sheet : drawing.getSheets()) {
        pages.add(buildPage(drawing, sheet, objects, diagnostics));
      }
    }
    Collections.sort(pages, new Comparator<Page>() {
      @Override
      public int compare(Page left, Page right) {
        return left.sheetId.compareTo(right.sheetId);
      }
    });
    Map<String, String> svg = new LinkedHashMap<String, String>();
    Map<String, String> visualFingerprints = new LinkedHashMap<String, String>();
    for (Page page : pages) {
      svg.put(page.sheetId, toSvg(page));
      visualFingerprints.put(page.sheetId, visualFingerprint(page));
    }
    return new Result(svg, toPdf(pages), visualFingerprints, diagnostics);
  }

  /**
   * Writes one SVG file per stable sheet ID.
   *
   * @param directory target directory
   * @return stable sheet-ID-to-path map
   * @throws IOException when output cannot be written
   */
  public Map<String, Path> exportSvg(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("directory must not be null");
    }
    Result result = render();
    Files.createDirectories(directory);
    Map<String, Path> paths = new LinkedHashMap<String, Path>();
    for (Map.Entry<String, String> entry : result.getSvgBySheetId().entrySet()) {
      Path path = directory.resolve(fileName(entry.getKey()) + ".svg");
      Files.write(path, entry.getValue().getBytes(StandardCharsets.UTF_8));
      paths.put(entry.getKey(), path);
    }
    return Collections.unmodifiableMap(paths);
  }

  /**
   * Writes one deterministic multi-page native PDF drawing set.
   *
   * @param path target PDF path
   * @throws IOException when output cannot be written
   */
  public void exportPdf(Path path) throws IOException {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(path, render().getPdf());
  }

  private Page buildPage(Drawing drawing, Sheet sheet, Map<String, SemanticObject> objects,
      List<Diagnostic> diagnostics) {
    Page page = new Page(sheet.getId(), format.getWidthMillimetres(), format.getHeightMillimetres());
    double contentRight = page.width - CONTENT_LEFT;
    double contentBottom = page.height - TITLE_BLOCK_HEIGHT - 7.0;
    page.commands
        .add(Command.rect(8.0, 8.0, page.width - 16.0, page.height - 16.0, "#1f2937", "none", 0.5, "sheet-border", ""));
    page.commands.add(Command.text(12.0, 17.0, 4.2, documentSet.getTitle(), "#111827", "document-title", ""));
    page.commands.add(Command.text(page.width - 12.0, 17.0, 2.7,
        drawing.getContentProfile().name() + " / " + format.name(), "#374151", "sheet-format", "end"));

    Map<String, Point> positions = layoutPositions(sheet, objects, contentRight, contentBottom, diagnostics);
    addDrawingQualityDiagnostics(sheet, objects, positions, page.width, contentBottom, diagnostics);
    Map<String, OffPageConnector> connectors = new TreeMap<String, OffPageConnector>();
    for (OffPageConnector connector : sheet.getOffPageConnectors()) {
      connectors.put(connector.getSemanticConnectionId(), connector);
    }
    Map<String, ProtectedRoute> protectedRoutes = new TreeMap<String, ProtectedRoute>();
    for (ProtectedRoute route : sheet.getProtectedRoutes()) {
      protectedRoutes.put(route.getSemanticConnectionId(), route);
      if (!insideAll(route.getWaypoints(), page.width, contentBottom)) {
        diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_ROUTE_OUTSIDE_SHEET",
            "Protected route contains a waypoint outside the drawable sheet area and is retained unchanged",
            route.getSemanticConnectionId()));
      }
    }
    Map<String, Point> endpointAnchors = routingMode == RoutingMode.FIXED_PORT_ORTHOGONAL
        ? fixedPortAnchors(sheet, positions, objects, diagnostics) : Collections.<String, Point>emptyMap();
    Map<String, Double> routeLanes = routingMode == RoutingMode.FIXED_PORT_ORTHOGONAL
        ? parallelRouteLanes(sheet, objects) : Collections.<String, Double>emptyMap();

    List<String> ids = new ArrayList<String>(sheet.getObjectNodeIds());
    Collections.sort(ids);
    for (String id : ids) {
      SemanticObject object = objects.get(id);
      if (object == null) {
        diagnostics.add(diagnostic(Severity.ERROR, "DIAGRAM_RENDER_BROKEN_OBJECT_REFERENCE",
            "Controlled sheet references a semantic object that is absent from the document set", id));
      } else if (isConnection(object.getKind())) {
        addConnection(page, object, positions, endpointAnchors, connectors.get(id), protectedRoutes.get(id), objects,
            contentRight, contentBottom, routeLanes.containsKey(id) ? routeLanes.get(id).doubleValue() : 0.0,
            diagnostics);
      }
    }
    addRouteQualityDiagnostics(page, positions, diagnostics);
    for (OffPageConnector connector : sheet.getOffPageConnectors()) {
      addOffPageConnector(page, connector, contentRight, contentBottom);
    }
    for (String id : ids) {
      SemanticObject object = objects.get(id);
      Point position = positions.get(id);
      if (object != null && position != null && isDrawableNode(object.getKind())) {
        addObject(page, object, position);
      }
    }
    addPortMarkers(page, endpointAnchors);
    addTitleBlock(page, drawing, sheet);
    return page;
  }

  private void addDocumentDiagnostics(List<Diagnostic> diagnostics) {
    for (EngineeringDiagramDocumentSet.Diagnostic source : documentSet.getDiagnostics()) {
      Severity severity = Severity.INFO;
      if (source.getSeverity() == EngineeringDiagramDocumentSet.Severity.WARNING) {
        severity = Severity.WARNING;
      } else if (source.getSeverity() == EngineeringDiagramDocumentSet.Severity.ERROR) {
        severity = Severity.ERROR;
      }
      diagnostics.add(diagnostic(severity, source.getCode(), source.getMessage(), source.getSubjectId()));
    }
  }

  private void addConventionDiagnostics(Map<String, SemanticObject> objects, List<Diagnostic> diagnostics) {
    if (conventionRegister.isEmpty()) {
      return;
    }
    Map<String, SemanticObject> visibleObjects = new TreeMap<String, SemanticObject>();
    for (Drawing drawing : documentSet.getDrawings()) {
      for (Sheet sheet : drawing.getSheets()) {
        for (String objectId : sheet.getObjectNodeIds()) {
          SemanticObject object = objects.get(objectId);
          if (object != null && isDrawableNode(object.getKind())) {
            visibleObjects.put(objectId, object);
          }
        }
      }
    }
    for (SemanticObject object : visibleObjects.values()) {
      SymbolConvention convention = conventionRegister.getSymbolConvention(object.getKind());
      if (convention == null) {
        diagnostics.add(
            diagnostic(Severity.WARNING, "DIAGRAM_RENDER_SYMBOL_FALLBACK", "No project symbol convention exists for "
                + object.getKind().name() + "; the legacy rectangle fallback was retained", object.getId()));
      } else if (convention.getEvidenceState() == EvidenceState.PROPOSED) {
        diagnostics.add(diagnostic(Severity.INFO, "DIAGRAM_RENDER_SYMBOL_CONVENTION_PROPOSAL",
            "Project symbol convention is proposed and does not imply standards qualification or drawing approval",
            object.getId()));
      }
    }
  }

  private void addDrawingQualityDiagnostics(Sheet sheet, Map<String, SemanticObject> objects,
      Map<String, Point> positions, double pageWidth, double contentBottom, List<Diagnostic> diagnostics) {
    List<String> drawableIds = new ArrayList<String>();
    for (String id : sheet.getObjectNodeIds()) {
      SemanticObject object = objects.get(id);
      if (object != null && isDrawableNode(object.getKind()) && positions.containsKey(id)) {
        drawableIds.add(id);
      }
    }
    Collections.sort(drawableIds);
    for (int index = 0; index < drawableIds.size(); index++) {
      String id = drawableIds.get(index);
      SemanticObject object = objects.get(id);
      Point position = positions.get(id);
      if (!objectInside(position, pageWidth, contentBottom)) {
        diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_OBJECT_CLIPPED",
            "Rendered object boundary intersects the sheet border, document header, or title-block area", id));
      }
      String label = displayLabel(object);
      if (estimatedTextWidth(label, 2.8) > OBJECT_WIDTH - 4.0) {
        diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_LABEL_OVERFLOW",
            "Primary object label exceeds the available symbol width and requires drawing review", id));
      }
      for (int otherIndex = index + 1; otherIndex < drawableIds.size(); otherIndex++) {
        String otherId = drawableIds.get(otherIndex);
        if (overlaps(position, positions.get(otherId))) {
          diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_OBJECT_COLLISION",
              "Rendered object overlaps semantic object " + otherId, id));
        }
      }
    }
  }

  private Map<String, Point> layoutPositions(Sheet sheet, Map<String, SemanticObject> objects, double contentRight,
      double contentBottom, List<Diagnostic> diagnostics) {
    Map<String, Point> result = new TreeMap<String, Point>();
    for (PinnedPosition pinned : sheet.getPinnedPositions()) {
      result.put(pinned.getSemanticObjectId(), new Point(pinned.getX(), pinned.getY()));
      if (!inside(pinned.getX(), pinned.getY(), format.getWidthMillimetres(), contentBottom)) {
        diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_PIN_OUTSIDE_SHEET",
            "Pinned millimetre position is outside the drawable sheet area and is retained unchanged",
            pinned.getSemanticObjectId()));
      }
    }
    List<String> automatic = new ArrayList<String>();
    for (String id : sheet.getObjectNodeIds()) {
      SemanticObject object = objects.get(id);
      if (object != null && isDrawableNode(object.getKind()) && !result.containsKey(id)) {
        automatic.add(id);
      }
    }
    Collections.sort(automatic);
    int columns = Math.max(1, (int) Math.floor((contentRight - CONTENT_LEFT) / 62.0));
    for (int index = 0; index < automatic.size(); index++) {
      int column = index % columns;
      int row = index / columns;
      double x = CONTENT_LEFT + 24.0 + column * 62.0;
      double y = CONTENT_TOP + 18.0 + row * 34.0;
      if (y > contentBottom - OBJECT_HEIGHT) {
        diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_AUTO_LAYOUT_OVERFLOW",
            "Automatic grid placement exceeds the drawable area; manual sheet assignment or pinned layout is needed",
            automatic.get(index)));
      }
      result.put(automatic.get(index), new Point(x, y));
    }
    return result;
  }

  private Map<String, Point> fixedPortAnchors(Sheet sheet, Map<String, Point> positions,
      Map<String, SemanticObject> objects, List<Diagnostic> diagnostics) {
    Map<String, List<String>> anchorKeysByOwnerSide = new TreeMap<String, List<String>>();
    List<String> connectionIds = new ArrayList<String>(sheet.getObjectNodeIds());
    Collections.sort(connectionIds);
    for (String connectionId : connectionIds) {
      SemanticObject connection = objects.get(connectionId);
      if (connection == null || !isConnection(connection.getKind())) {
        continue;
      }
      collectPortAnchor(connection, "sourceEndpointId", true, positions, objects, anchorKeysByOwnerSide, diagnostics);
      collectPortAnchor(connection, "targetEndpointId", false, positions, objects, anchorKeysByOwnerSide, diagnostics);
    }
    Map<String, Point> result = new TreeMap<String, Point>();
    for (Map.Entry<String, List<String>> entry : anchorKeysByOwnerSide.entrySet()) {
      List<String> keys = entry.getValue();
      Collections.sort(keys);
      String[] ownerAndSide = entry.getKey().split("\\|", 2);
      Point owner = positions.get(ownerAndSide[0]);
      boolean outlet = "OUTLET".equals(ownerAndSide[1]);
      double spacing = keys.size() <= 1 ? 0.0
          : Math.min(3.0, (OBJECT_HEIGHT - 2.0 * PORT_SLOT_MARGIN) / (keys.size() - 1));
      for (int index = 0; index < keys.size(); index++) {
        double offset = (index - (keys.size() - 1) / 2.0) * spacing;
        result.put(keys.get(index),
            new Point(owner.x + (outlet ? OBJECT_WIDTH / 2.0 : -OBJECT_WIDTH / 2.0), owner.y + offset));
      }
    }
    return result;
  }

  private static void collectPortAnchor(SemanticObject connection, String property, boolean source,
      Map<String, Point> positions, Map<String, SemanticObject> objects,
      Map<String, List<String>> anchorKeysByOwnerSide, List<Diagnostic> diagnostics) {
    String endpointId = stringProperty(connection, property, "");
    SemanticObject endpoint = objects.get(endpointId);
    String ownerId = endpoint == null ? "" : stringProperty(endpoint, "ownerNodeId", "");
    if (endpointId.isEmpty() || ownerId.isEmpty()) {
      diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_FIXED_PORT_UNRESOLVED",
          "Canonical endpoint cannot be resolved to an owner symbol; center routing fallback is retained",
          endpointId.isEmpty() ? connection.getId() : endpointId));
      return;
    }
    if (!positions.containsKey(ownerId)) {
      // The peer owner of a valid off-page connection is intentionally absent from this sheet.
      return;
    }
    String role = source ? "source" : "target";
    String anchorKey = endpointId + "|" + role;
    String side = source ? "OUTLET" : "INLET";
    String groupKey = ownerId + "|" + side;
    List<String> keys = anchorKeysByOwnerSide.get(groupKey);
    if (keys == null) {
      keys = new ArrayList<String>();
      anchorKeysByOwnerSide.put(groupKey, keys);
    }
    if (!keys.contains(anchorKey)) {
      keys.add(anchorKey);
    }
  }

  private static Map<String, Double> parallelRouteLanes(Sheet sheet, Map<String, SemanticObject> objects) {
    Map<String, List<String>> connectionIdsByOwnerPair = new TreeMap<String, List<String>>();
    for (String objectId : sheet.getObjectNodeIds()) {
      SemanticObject connection = objects.get(objectId);
      if (connection == null || !isConnection(connection.getKind())) {
        continue;
      }
      String sourceOwner = endpointOwnerId(connection, "sourceEndpointId", objects);
      String targetOwner = endpointOwnerId(connection, "targetEndpointId", objects);
      String key = sourceOwner + "|" + targetOwner + "|"
          + stringProperty(connection, "connectionType", "MATERIAL");
      List<String> ids = connectionIdsByOwnerPair.get(key);
      if (ids == null) {
        ids = new ArrayList<String>();
        connectionIdsByOwnerPair.put(key, ids);
      }
      ids.add(connection.getId());
    }
    Map<String, Double> result = new TreeMap<String, Double>();
    for (List<String> ids : connectionIdsByOwnerPair.values()) {
      Collections.sort(ids);
      for (int index = 0; index < ids.size(); index++) {
        result.put(ids.get(index),
            Double.valueOf((index - (ids.size() - 1) / 2.0) * PARALLEL_LANE_SPACING));
      }
    }
    return result;
  }

  private static void addPortMarkers(Page page, Map<String, Point> endpointAnchors) {
    for (Map.Entry<String, Point> entry : endpointAnchors.entrySet()) {
      Point point = entry.getValue();
      String endpointId = entry.getKey().substring(0, entry.getKey().lastIndexOf('|'));
      page.commands.add(Command.rect(point.x - PORT_MARKER_SIZE / 2.0, point.y - PORT_MARKER_SIZE / 2.0,
          PORT_MARKER_SIZE, PORT_MARKER_SIZE, "#1f2937", "#ffffff", 0.5, endpointId, ""));
    }
  }

  private static Point routedEndpointPosition(SemanticObject connection, String property, boolean source,
      Map<String, Point> positions, Map<String, Point> endpointAnchors, Map<String, SemanticObject> objects) {
    String endpointId = stringProperty(connection, property, "");
    Point fixed = endpointAnchors.get(endpointId + "|" + (source ? "source" : "target"));
    return fixed == null ? endpointPosition(connection, property, positions, objects) : fixed;
  }

  private static double recycleReturnY(Point source, Point target, double contentBottom, double laneOffset) {
    double offset = 14.0 + Math.abs(laneOffset);
    double above = Math.min(source.y, target.y) - offset;
    if (above >= CONTENT_TOP + PORT_SLOT_MARGIN) {
      return above;
    }
    return Math.min(contentBottom - PORT_SLOT_MARGIN, Math.max(source.y, target.y) + offset);
  }

  private void addConnection(Page page, SemanticObject connection, Map<String, Point> positions,
      Map<String, Point> endpointAnchors, OffPageConnector connector, ProtectedRoute protectedRoute,
      Map<String, SemanticObject> objects, double contentRight, double contentBottom, double laneOffset,
      List<Diagnostic> diagnostics) {
    List<Point> points = new ArrayList<Point>();
    boolean protectedGeometry = protectedRoute != null;
    if (protectedGeometry) {
      for (Waypoint waypoint : protectedRoute.getWaypoints()) {
        points.add(new Point(waypoint.getX(), waypoint.getY()));
      }
    } else {
      Point source = routedEndpointPosition(connection, "sourceEndpointId", true, positions, endpointAnchors, objects);
      Point target = routedEndpointPosition(connection, "targetEndpointId", false, positions, endpointAnchors, objects);
      Point offPage = connector == null ? null : connectorPoint(connector, contentRight, contentBottom);
      if (connector != null && connector.getRole() == EngineeringDiagramDocumentSet.ConnectorRole.SOURCE) {
        target = offPage;
      } else if (connector != null) {
        source = offPage;
      }
      if (source != null && target != null) {
        points.add(source);
        if (routingMode == RoutingMode.FIXED_PORT_ORTHOGONAL
            && (Boolean.TRUE.equals(connection.getProperties().get("recycle")) || source.x >= target.x)) {
          double returnY = recycleReturnY(source, target, contentBottom, laneOffset);
          double sourceTurnX = source.x + 10.0 + Math.abs(laneOffset);
          double targetTurnX = target.x - 10.0 - Math.abs(laneOffset);
          points.add(new Point(sourceTurnX, source.y));
          points.add(new Point(sourceTurnX, returnY));
          points.add(new Point(targetTurnX, returnY));
          points.add(new Point(targetTurnX, target.y));
        } else {
          double middleX = (source.x + target.x) / 2.0 + laneOffset;
          points.add(new Point(middleX, source.y));
          points.add(new Point(middleX, target.y));
        }
        points.add(target);
      } else {
        diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_CONNECTION_ENDPOINT_OMITTED",
            "Connection endpoints cannot both be placed on this sheet; the semantic connection remains in the source document",
            connection.getId()));
        return;
      }
    }
    String connectionType = stringProperty(connection, "connectionType", "MATERIAL");
    String color = "#1f2937";
    String dash = "";
    if ("ENERGY".equals(connectionType)) {
      color = "#b45309";
      dash = "4 2";
    } else if ("SIGNAL".equals(connectionType)) {
      color = "#2563eb";
      dash = "2 2";
    }
    page.commands.add(Command.polyline(points, color, 0.8, dash, connection.getId(), protectedGeometry));
    String label = displayLabel(connection);
    Point labelPoint = routeLabelPoint(points);
    if (label == null || label.trim().isEmpty()) {
      diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_CONNECTION_LABEL_MISSING",
          "Rendered connection has no primary label and requires drawing review", connection.getId()));
      label = "";
    } else {
      page.commands
          .add(Command.text(labelPoint.x, labelPoint.y - 3.0, 2.3, label, color, connection.getId(), "middle"));
    }
    page.routes.add(new RouteView(connection.getId(), points, label, labelPoint,
        endpointOwnerId(connection, "sourceEndpointId", objects),
        endpointOwnerId(connection, "targetEndpointId", objects)));
  }

  private void addRouteQualityDiagnostics(Page page, Map<String, Point> positions, List<Diagnostic> diagnostics) {
    List<String> objectIds = new ArrayList<String>(positions.keySet());
    Collections.sort(objectIds);
    for (int routeIndex = 0; routeIndex < page.routes.size(); routeIndex++) {
      RouteView route = page.routes.get(routeIndex);
      for (String objectId : objectIds) {
        Point objectPosition = positions.get(objectId);
        boolean endpointOwner = objectId.equals(route.sourceOwnerId) || objectId.equals(route.targetOwnerId);
        if (!endpointOwner && polylineIntersectsObject(route.points, objectPosition)) {
          diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_ROUTE_OBJECT_INTERSECTION",
              "Rendered connection route intersects non-endpoint semantic object " + objectId, route.connectionId));
        }
        if (!route.label.isEmpty() && labelIntersectsObject(route.label, route.labelPoint, objectPosition)) {
          diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_ROUTE_LABEL_OBJECT_COLLISION",
              "Rendered connection label overlaps semantic object " + objectId, route.connectionId));
        }
      }
      for (int otherIndex = routeIndex + 1; otherIndex < page.routes.size(); otherIndex++) {
        RouteView other = page.routes.get(otherIndex);
        if (!route.label.isEmpty() && !other.label.isEmpty()
            && labelsOverlap(route.label, route.labelPoint, other.label, other.labelPoint)) {
          diagnostics.add(diagnostic(Severity.WARNING, "DIAGRAM_RENDER_CONNECTION_LABEL_COLLISION",
              "Rendered connection label overlaps connection label " + other.connectionId, route.connectionId));
        }
      }
    }
  }

  private void addOffPageConnector(Page page, OffPageConnector connector, double contentRight, double contentBottom) {
    Point point = connectorPoint(connector, contentRight, contentBottom);
    double direction = connector.getRole() == EngineeringDiagramDocumentSet.ConnectorRole.SOURCE ? 1.0 : -1.0;
    List<Point> triangle = Arrays.asList(new Point(point.x, point.y),
        new Point(point.x - direction * 5.0, point.y - 3.0), new Point(point.x - direction * 5.0, point.y + 3.0),
        new Point(point.x, point.y));
    page.commands.add(Command.polyline(triangle, "#111827", 0.7, "", connector.getId(), false));
    String label = "TO/FROM " + connector.getZoneReference() + " [" + connector.getPeerSheetId() + "]";
    double textX = point.x - direction * 7.0;
    page.commands.add(Command.text(textX, point.y - 4.0, 2.4, label, "#111827", connector.getId(),
        direction > 0.0 ? "end" : "start"));
  }

  private void addObject(Page page, SemanticObject object, Point position) {
    SymbolConvention convention = conventionRegister.getSymbolConvention(object.getKind());
    SymbolShape shape = convention == null ? SymbolShape.RECTANGLE : convention.getShape();
    String stroke = convention == null ? "#1f2937" : convention.getStrokeColor();
    String fill = convention == null ? (object.getKind() == EngineeringNode.Kind.EQUIPMENT ? "#eef6ee" : "#eff6ff")
        : convention.getFillColor();
    page.commands.add(symbolCommand(shape, position, stroke, fill, object.getId()));
    String primary = displayLabel(object);
    page.commands.add(Command.text(position.x, position.y - 0.8, 2.8, primary, "#111827", object.getId(), "middle"));
    page.commands.add(
        Command.text(position.x, position.y + 4.0, 2.0, object.getKind().name(), "#4b5563", object.getId(), "middle"));
  }

  private static Command symbolCommand(SymbolShape shape, Point position, String stroke, String fill, String objectId) {
    double left = position.x - OBJECT_WIDTH / 2.0;
    double top = position.y - OBJECT_HEIGHT / 2.0;
    if (shape == SymbolShape.DIAMOND) {
      return Command
          .polygon(
              Arrays.asList(new Point(position.x, top), new Point(position.x + OBJECT_WIDTH / 2.0, position.y),
                  new Point(position.x, top + OBJECT_HEIGHT), new Point(left, position.y)),
              stroke, fill, 0.7, objectId);
    }
    if (shape == SymbolShape.HEXAGON) {
      double shoulder = OBJECT_WIDTH / 4.0;
      return Command.polygon(
          Arrays.asList(new Point(left + shoulder, top), new Point(left + OBJECT_WIDTH - shoulder, top),
              new Point(left + OBJECT_WIDTH, position.y),
              new Point(left + OBJECT_WIDTH - shoulder, top + OBJECT_HEIGHT),
              new Point(left + shoulder, top + OBJECT_HEIGHT), new Point(left, position.y)),
          stroke, fill, 0.7, objectId);
    }
    return Command.rect(left, top, OBJECT_WIDTH, OBJECT_HEIGHT, stroke, fill, 0.7, objectId, "");
  }

  private void addTitleBlock(Page page, Drawing drawing, Sheet sheet) {
    double top = page.height - TITLE_BLOCK_HEIGHT;
    page.commands.add(Command.rect(8.0, top, page.width - 16.0, TITLE_BLOCK_HEIGHT - 8.0, "#1f2937", "#ffffff", 0.5,
        "title-block", ""));
    page.commands.add(
        Command.line(page.width * 0.58, top, page.width * 0.58, page.height - 8.0, "#1f2937", 0.4, "title-block", ""));
    page.commands.add(Command.text(12.0, top + 6.0, 3.0, drawing.getTitle(), "#111827", "drawing-title", ""));
    page.commands
        .add(Command.text(12.0, top + 12.0, 2.5, "DRAWING " + drawing.getNumber(), "#111827", "drawing-number", ""));
    page.commands.add(Command.text(12.0, top + 17.0, 2.3, "SHEET " + sheet.getNumber() + " - " + sheet.getTitle(),
        "#111827", "sheet-number", ""));
    page.commands.add(Command.text(page.width * 0.60, top + 6.0, 2.4,
        "REV " + documentSet.getRevision() + "  STATUS " + documentSet.getStatus().name(), "#111827", "revision", ""));
    page.commands.add(Command.text(page.width * 0.60, top + 11.0, 2.2,
        "PURPOSE " + documentSet.getIssuePurpose().name(), "#111827", "issue-purpose", ""));
    page.commands.add(Command.text(page.width * 0.60, top + 16.0, 1.8,
        "SOURCE " + documentSet.getSourceGraphFingerprint(), "#4b5563", "fingerprint", ""));
    if (documentSet.getStatus() != EngineeringDiagramDocumentSet.DocumentStatus.APPROVED
        || documentSet.getIssuePurpose() == EngineeringDiagramDocumentSet.IssuePurpose.ENGINEERING_PROPOSAL) {
      page.commands.add(Command.text(page.width / 2.0, top - 3.0, 3.0,
          "ENGINEERING PROPOSAL - NOT APPROVED FOR DESIGN OR CONSTRUCTION", "#b91c1c", "proposal-boundary", "middle"));
    }
  }

  private String toSvg(Page page) {
    StringBuilder svg = new StringBuilder();
    svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(number(page.width)).append("mm\" height=\"")
        .append(number(page.height)).append("mm\" viewBox=\"0 0 ").append(number(page.width)).append(' ')
        .append(number(page.height)).append("\" data-sheet-id=\"").append(xml(page.sheetId)).append("\">\n");
    svg.append("  <title>").append(xml(documentSet.getTitle())).append(" - ").append(xml(page.sheetId))
        .append("</title>\n");
    svg.append("  <g font-family=\"Arial,Helvetica,sans-serif\" fill=\"none\">\n");
    for (Command command : page.commands) {
      svg.append(command.toSvg());
    }
    svg.append("  </g>\n</svg>\n");
    return svg.toString();
  }

  private static String visualFingerprint(Page page) {
    StringBuilder normalized = new StringBuilder();
    normalized.append(number(page.width)).append('x').append(number(page.height)).append('\n');
    for (Command command : page.commands) {
      command.appendVisualSignature(normalized);
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(normalized.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(bytes.length * 2);
      for (byte value : bytes) {
        result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", ex);
    }
  }

  private byte[] toPdf(List<Page> pages) {
    List<byte[]> objects = new ArrayList<byte[]>();
    objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
    StringBuilder kids = new StringBuilder();
    for (int index = 0; index < pages.size(); index++) {
      kids.append(4 + index * 2).append(" 0 R ");
    }
    objects.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>"));
    objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
    for (int index = 0; index < pages.size(); index++) {
      Page page = pages.get(index);
      int contentObject = 5 + index * 2;
      String mediaBox = "0 0 " + number(page.width * MM_TO_POINT) + " " + number(page.height * MM_TO_POINT);
      objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [" + mediaBox
          + "] /Resources << /Font << /F1 3 0 R >> >> /Contents " + contentObject + " 0 R >>"));
      StringBuilder stream = new StringBuilder("q\n");
      for (Command command : page.commands) {
        stream.append(command.toPdf(page.height));
      }
      stream.append("Q\n");
      byte[] streamBytes = bytes(stream.toString());
      ByteArrayOutputStream content = new ByteArrayOutputStream();
      write(content, bytes("<< /Length " + streamBytes.length + " >>\nstream\n"));
      write(content, streamBytes);
      write(content, bytes("endstream"));
      objects.add(content.toByteArray());
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    write(output, bytes("%PDF-1.4\n%âãÏÓ\n"));
    List<Integer> offsets = new ArrayList<Integer>();
    offsets.add(Integer.valueOf(0));
    for (int index = 0; index < objects.size(); index++) {
      offsets.add(Integer.valueOf(output.size()));
      write(output, bytes((index + 1) + " 0 obj\n"));
      write(output, objects.get(index));
      write(output, bytes("\nendobj\n"));
    }
    int xref = output.size();
    write(output, bytes("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
    for (int index = 1; index < offsets.size(); index++) {
      write(output, bytes(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(index))));
    }
    write(output,
        bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"));
    return output.toByteArray();
  }

  private Map<String, SemanticObject> objectsById() {
    Map<String, SemanticObject> result = new TreeMap<String, SemanticObject>();
    for (SemanticObject object : documentSet.getSemanticObjects()) {
      result.put(object.getId(), object);
    }
    return result;
  }

  private static Point endpointPosition(SemanticObject connection, String property, Map<String, Point> positions,
      Map<String, SemanticObject> objects) {
    String endpointId = stringProperty(connection, property, "");
    Point direct = positions.get(endpointId);
    if (direct != null) {
      return direct;
    }
    SemanticObject endpoint = objects.get(endpointId);
    if (endpoint == null) {
      return null;
    }
    return positions.get(stringProperty(endpoint, "ownerNodeId", ""));
  }

  private static Point connectorPoint(OffPageConnector connector, double contentRight, double contentBottom) {
    int bucket = Math.abs(connector.getPairId().hashCode() % 7);
    double y = Math.min(contentBottom - 10.0, CONTENT_TOP + 20.0 + bucket * 22.0);
    double x = connector.getRole() == EngineeringDiagramDocumentSet.ConnectorRole.SOURCE ? contentRight : CONTENT_LEFT;
    return new Point(x, y);
  }

  private static String displayLabel(SemanticObject object) {
    for (EngineeringDiagramDesignationRegister.Designation designation : object.getDesignations()) {
      return designation.getValue();
    }
    return object.getLabel();
  }

  private static boolean isDrawableNode(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.EQUIPMENT || kind == EngineeringNode.Kind.INSTRUMENT
        || kind == EngineeringNode.Kind.BOUNDARY || kind == EngineeringNode.Kind.PROCESS_TAP;
  }

  private static boolean isConnection(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.PIPE_SEGMENT || kind == EngineeringNode.Kind.SIGNAL_CONNECTION
        || kind == EngineeringNode.Kind.ENERGY_CONNECTION;
  }

  private static boolean inside(double x, double y, double width, double contentBottom) {
    return x >= 8.0 && x <= width - 8.0 && y >= 8.0 && y <= contentBottom;
  }

  private static boolean objectInside(Point point, double width, double contentBottom) {
    return point.x - OBJECT_WIDTH / 2.0 >= 8.0 && point.x + OBJECT_WIDTH / 2.0 <= width - 8.0
        && point.y - OBJECT_HEIGHT / 2.0 >= CONTENT_TOP && point.y + OBJECT_HEIGHT / 2.0 <= contentBottom;
  }

  private static boolean overlaps(Point left, Point right) {
    return Math.abs(left.x - right.x) < OBJECT_WIDTH && Math.abs(left.y - right.y) < OBJECT_HEIGHT;
  }

  private static Point routeLabelPoint(List<Point> points) {
    double totalLength = 0.0;
    for (int index = 1; index < points.size(); index++) {
      totalLength += distance(points.get(index - 1), points.get(index));
    }
    double remaining = totalLength / 2.0;
    for (int index = 1; index < points.size(); index++) {
      Point start = points.get(index - 1);
      Point end = points.get(index);
      double segmentLength = distance(start, end);
      if (remaining <= segmentLength || index == points.size() - 1) {
        double fraction = segmentLength == 0.0 ? 0.0 : remaining / segmentLength;
        return new Point(start.x + (end.x - start.x) * fraction, start.y + (end.y - start.y) * fraction);
      }
      remaining -= segmentLength;
    }
    return points.get(0);
  }

  private static double distance(Point start, Point end) {
    double deltaX = end.x - start.x;
    double deltaY = end.y - start.y;
    return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
  }

  private static String endpointOwnerId(SemanticObject connection, String property,
      Map<String, SemanticObject> objects) {
    String endpointId = stringProperty(connection, property, "");
    SemanticObject endpoint = objects.get(endpointId);
    if (endpoint == null) {
      return endpointId;
    }
    return stringProperty(endpoint, "ownerNodeId", endpointId);
  }

  private static boolean polylineIntersectsObject(List<Point> points, Point objectPosition) {
    double left = objectPosition.x - OBJECT_WIDTH / 2.0;
    double right = objectPosition.x + OBJECT_WIDTH / 2.0;
    double top = objectPosition.y - OBJECT_HEIGHT / 2.0;
    double bottom = objectPosition.y + OBJECT_HEIGHT / 2.0;
    for (int index = 1; index < points.size(); index++) {
      Point start = points.get(index - 1);
      Point end = points.get(index);
      if (pointInsideRectangle(start, left, right, top, bottom) || pointInsideRectangle(end, left, right, top, bottom)
          || segmentsIntersect(start, end, new Point(left, top), new Point(right, top))
          || segmentsIntersect(start, end, new Point(right, top), new Point(right, bottom))
          || segmentsIntersect(start, end, new Point(right, bottom), new Point(left, bottom))
          || segmentsIntersect(start, end, new Point(left, bottom), new Point(left, top))) {
        return true;
      }
    }
    return false;
  }

  private static boolean pointInsideRectangle(Point point, double left, double right, double top, double bottom) {
    return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom;
  }

  private static boolean segmentsIntersect(Point firstStart, Point firstEnd, Point secondStart, Point secondEnd) {
    double first = cross(firstStart, firstEnd, secondStart);
    double second = cross(firstStart, firstEnd, secondEnd);
    double third = cross(secondStart, secondEnd, firstStart);
    double fourth = cross(secondStart, secondEnd, firstEnd);
    return first * second <= 0.0 && third * fourth <= 0.0
        && Math.max(Math.min(firstStart.x, firstEnd.x), Math.min(secondStart.x, secondEnd.x)) <= Math
            .min(Math.max(firstStart.x, firstEnd.x), Math.max(secondStart.x, secondEnd.x))
        && Math.max(Math.min(firstStart.y, firstEnd.y), Math.min(secondStart.y, secondEnd.y)) <= Math
            .min(Math.max(firstStart.y, firstEnd.y), Math.max(secondStart.y, secondEnd.y));
  }

  private static double cross(Point start, Point end, Point point) {
    return (end.x - start.x) * (point.y - start.y) - (end.y - start.y) * (point.x - start.x);
  }

  private static boolean labelIntersectsObject(String label, Point labelPoint, Point objectPosition) {
    return rectanglesOverlap(labelPoint.x, labelPoint.y - 3.0, estimatedTextWidth(label, 2.3), 3.2, objectPosition.x,
        objectPosition.y, OBJECT_WIDTH, OBJECT_HEIGHT);
  }

  private static boolean labelsOverlap(String firstLabel, Point firstPoint, String secondLabel, Point secondPoint) {
    return rectanglesOverlap(firstPoint.x, firstPoint.y - 3.0, estimatedTextWidth(firstLabel, 2.3), 3.2, secondPoint.x,
        secondPoint.y - 3.0, estimatedTextWidth(secondLabel, 2.3), 3.2);
  }

  private static boolean rectanglesOverlap(double firstX, double firstY, double firstWidth, double firstHeight,
      double secondX, double secondY, double secondWidth, double secondHeight) {
    return Math.abs(firstX - secondX) * 2.0 < firstWidth + secondWidth
        && Math.abs(firstY - secondY) * 2.0 < firstHeight + secondHeight;
  }

  private static double estimatedTextWidth(String value, double fontSize) {
    return value.length() * fontSize * 0.52;
  }

  private static boolean insideAll(List<Waypoint> waypoints, double width, double contentBottom) {
    for (Waypoint waypoint : waypoints) {
      if (!inside(waypoint.getX(), waypoint.getY(), width, contentBottom)) {
        return false;
      }
    }
    return true;
  }

  private static String stringProperty(SemanticObject object, String name, String fallback) {
    Object value = object.getProperties().get(name);
    return value == null ? fallback : String.valueOf(value);
  }

  private static Diagnostic diagnostic(Severity severity, String code, String message, String subjectId) {
    return new Diagnostic(severity, code, message, subjectId == null ? "" : subjectId);
  }

  private static String fileName(String value) {
    return value.replaceAll("[^A-Za-z0-9._-]+", "-");
  }

  private static String number(double value) {
    if (Math.abs(value - Math.rint(value)) < 0.0000001) {
      return Long.toString(Math.round(value));
    }
    return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  private static String xml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private static String pdf(String value) {
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '(' || character == ')' || character == '\\') {
        result.append('\\').append(character);
      } else if (character >= 32 && character <= 255) {
        result.append(character);
      } else {
        result.append('?');
      }
    }
    return result.toString();
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.ISO_8859_1);
  }

  private static void write(ByteArrayOutputStream output, byte[] value) {
    output.write(value, 0, value.length);
  }

  private static final class Point {
    private final double x;
    private final double y;

    private Point(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }

  private static final class Page {
    private final String sheetId;
    private final double width;
    private final double height;
    private final List<Command> commands = new ArrayList<Command>();
    private final List<RouteView> routes = new ArrayList<RouteView>();

    private Page(String sheetId, double width, double height) {
      this.sheetId = sheetId;
      this.width = width;
      this.height = height;
    }
  }

  private static final class RouteView {
    private final String connectionId;
    private final List<Point> points;
    private final String label;
    private final Point labelPoint;
    private final String sourceOwnerId;
    private final String targetOwnerId;

    private RouteView(String connectionId, List<Point> points, String label, Point labelPoint, String sourceOwnerId,
        String targetOwnerId) {
      this.connectionId = connectionId;
      this.points = Collections.unmodifiableList(new ArrayList<Point>(points));
      this.label = label;
      this.labelPoint = labelPoint;
      this.sourceOwnerId = sourceOwnerId;
      this.targetOwnerId = targetOwnerId;
    }
  }

  private static final class Command {
    private final String type;
    private final List<Point> points;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final double size;
    private final String text;
    private final String stroke;
    private final String fill;
    private final double strokeWidth;
    private final String dash;
    private final String id;
    private final String anchor;
    private final boolean protectedGeometry;

    private Command(String type, List<Point> points, double x, double y, double width, double height, double size,
        String text, String stroke, String fill, double strokeWidth, String dash, String id, String anchor,
        boolean protectedGeometry) {
      this.type = type;
      this.points = points;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.size = size;
      this.text = text;
      this.stroke = stroke;
      this.fill = fill;
      this.strokeWidth = strokeWidth;
      this.dash = dash;
      this.id = id;
      this.anchor = anchor;
      this.protectedGeometry = protectedGeometry;
    }

    private static Command rect(double x, double y, double width, double height, String stroke, String fill,
        double strokeWidth, String id, String dash) {
      return new Command("rect", Collections.<Point>emptyList(), x, y, width, height, 0.0, "", stroke, fill,
          strokeWidth, dash, id, "", false);
    }

    private static Command line(double x1, double y1, double x2, double y2, String stroke, double strokeWidth,
        String id, String dash) {
      return polyline(Arrays.asList(new Point(x1, y1), new Point(x2, y2)), stroke, strokeWidth, dash, id, false);
    }

    private static Command polyline(List<Point> points, String stroke, double strokeWidth, String dash, String id,
        boolean protectedGeometry) {
      return new Command("polyline", new ArrayList<Point>(points), 0.0, 0.0, 0.0, 0.0, 0.0, "", stroke, "none",
          strokeWidth, dash, id, "", protectedGeometry);
    }

    private static Command polygon(List<Point> points, String stroke, String fill, double strokeWidth, String id) {
      return new Command("polygon", new ArrayList<Point>(points), 0.0, 0.0, 0.0, 0.0, 0.0, "", stroke, fill,
          strokeWidth, "", id, "", false);
    }

    private static Command text(double x, double y, double size, String text, String fill, String id, String anchor) {
      return new Command("text", Collections.<Point>emptyList(), x, y, 0.0, 0.0, size, text, "none", fill, 0.0, "", id,
          anchor, false);
    }

    private String toSvg() {
      StringBuilder result = new StringBuilder("    <");
      if ("rect".equals(type)) {
        result.append("rect x=\"").append(number(x)).append("\" y=\"").append(number(y)).append("\" width=\"")
            .append(number(width)).append("\" height=\"").append(number(height)).append("\"");
      } else if ("text".equals(type)) {
        result.append("text x=\"").append(number(x)).append("\" y=\"").append(number(y)).append("\" font-size=\"")
            .append(number(size)).append("\" dominant-baseline=\"middle\"");
        if (!anchor.isEmpty()) {
          result.append(" text-anchor=\"").append(anchor).append("\"");
        }
      } else {
        result.append("polygon".equals(type) ? "polygon points=\"" : "polyline points=\"");
        for (int index = 0; index < points.size(); index++) {
          if (index > 0) {
            result.append(' ');
          }
          result.append(number(points.get(index).x)).append(',').append(number(points.get(index).y));
        }
        result.append("\"");
      }
      result.append(" stroke=\"").append(stroke).append("\" fill=\"").append(fill).append("\"");
      if (strokeWidth > 0.0) {
        result.append(" stroke-width=\"").append(number(strokeWidth)).append("\"");
      }
      if (!dash.isEmpty()) {
        result.append(" stroke-dasharray=\"").append(dash).append("\"");
      }
      if (!id.isEmpty()) {
        result.append(" data-semantic-id=\"").append(xml(id)).append("\"");
      }
      if (protectedGeometry) {
        result.append(" data-protected-route=\"true\"");
      }
      if ("text".equals(type)) {
        result.append('>').append(xml(text)).append("</text>\n");
      } else {
        result.append("/>\n");
      }
      return result.toString();
    }

    private void appendVisualSignature(StringBuilder result) {
      result.append(type).append('|').append(number(x)).append('|').append(number(y)).append('|').append(number(width))
          .append('|').append(number(height)).append('|').append(number(size)).append('|').append(stroke).append('|')
          .append(fill).append('|').append(number(strokeWidth)).append('|').append(dash).append('|').append(anchor)
          .append('|').append(text.length()).append(':').append(text);
      for (Point point : points) {
        result.append('|').append(number(point.x)).append(',').append(number(point.y));
      }
      result.append('\n');
    }

    private String toPdf(double pageHeight) {
      StringBuilder result = new StringBuilder();
      if ("text".equals(type)) {
        double adjustedX = x;
        if ("middle".equals(anchor)) {
          adjustedX -= text.length() * size * 0.24;
        } else if ("end".equals(anchor)) {
          adjustedX -= text.length() * size * 0.48;
        }
        result.append(rgb(fill, false)).append(" BT /F1 ").append(number(size * MM_TO_POINT * 0.78)).append(" Tf ")
            .append(number(adjustedX * MM_TO_POINT)).append(' ').append(number((pageHeight - y) * MM_TO_POINT))
            .append(" Td (").append(pdf(text)).append(") Tj ET\n");
        return result.toString();
      }
      result.append(rgb(stroke, true)).append(' ').append(number(strokeWidth * MM_TO_POINT)).append(" w ");
      if (dash.isEmpty()) {
        result.append("[] 0 d ");
      } else {
        result.append('[');
        for (String item : dash.split(" ")) {
          if (item.isEmpty()) {
            continue;
          }
          try {
            result.append(number(Double.parseDouble(item) * MM_TO_POINT)).append(' ');
          } catch (NumberFormatException exception) {
            // Ignore malformed dash tokens while retaining every valid dash length.
          }
        }
        result.append("] 0 d ");
      }
      if ("rect".equals(type)) {
        result.append(rgb(fill, false)).append(' ').append(number(x * MM_TO_POINT)).append(' ')
            .append(number((pageHeight - y - height) * MM_TO_POINT)).append(' ').append(number(width * MM_TO_POINT))
            .append(' ').append(number(height * MM_TO_POINT)).append(" re ");
        result.append("none".equals(fill) ? "S\n" : "B\n");
      } else if (!points.isEmpty()) {
        if ("polygon".equals(type) && !"none".equals(fill)) {
          result.append(rgb(fill, false)).append(' ');
        }
        Point first = points.get(0);
        result.append(number(first.x * MM_TO_POINT)).append(' ').append(number((pageHeight - first.y) * MM_TO_POINT))
            .append(" m ");
        for (int index = 1; index < points.size(); index++) {
          Point point = points.get(index);
          result.append(number(point.x * MM_TO_POINT)).append(' ').append(number((pageHeight - point.y) * MM_TO_POINT))
              .append(" l ");
        }
        if ("polygon".equals(type)) {
          result.append("h ").append("none".equals(fill) ? "S\n" : "B\n");
        } else {
          result.append("S\n");
        }
      }
      return result.toString();
    }

    private static String rgb(String value, boolean stroke) {
      String fallback = "0 0 0 " + (stroke ? "RG" : "rg");
      if (value == null || "none".equals(value) || value.length() != 7 || value.charAt(0) != '#') {
        return fallback;
      }
      try {
        int red = Integer.parseInt(value.substring(1, 3), 16);
        int green = Integer.parseInt(value.substring(3, 5), 16);
        int blue = Integer.parseInt(value.substring(5, 7), 16);
        return number(red / 255.0) + " " + number(green / 255.0) + " " + number(blue / 255.0) + " "
            + (stroke ? "RG" : "rg");
      } catch (NumberFormatException exception) {
        return fallback;
      }
    }
  }
}
