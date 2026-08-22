package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Immutable exchange-neutral graphical projection of the canonical engineering graph.
 *
 * <p>
 * Coordinates and dimensions use millimetres. Primitives retain stable semantic identities, but
 * deliberately carry no DEXPI profile symbol, ISO symbol, or accountable drawing-approval claim.
 * Format adapters may render these generic primitives natively or translate the supported subset
 * into an exchange format while reporting every loss.
 * </p>
 */
public final class EngineeringGraphicalProjection implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_engineering_graphical_projection.v1";

  /** Coordinate and dimension unit used by every primitive. */
  public enum Unit {
    /** Millimetres in drawing space. */
    MILLIMETRE
  }

  /** Verification state of the graphical projection. */
  public enum VerificationStatus {
    /** Simulation-derived engineering proposal requiring accountable review. */
    PROPOSAL,
    /** Projection inputs have review evidence; drawing approval is still separate. */
    REVIEWED
  }

  /** Generic graphical primitive types. */
  public enum PrimitiveType {
    RECTANGLE, POLYLINE, POLYGON, TEXT
  }

  /** Projection diagnostic severity. */
  public enum Severity {
    INFO, WARNING, ERROR
  }

  /** Immutable two-dimensional drawing point in millimetres. */
  public static final class Point implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double x;
    private final double y;

    public Point(double x, double y) {
      this.x = finite(x, "x");
      this.y = finite(y, "y");
    }

    public double getX() {
      return x;
    }

    public double getY() {
      return y;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("x", Double.valueOf(x));
      result.put("y", Double.valueOf(y));
      return result;
    }
  }

  /** Immutable generic primitive with stable projection and semantic identities. */
  public static final class Primitive implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final PrimitiveType type;
    private final String representedObjectId;
    private final String representedExternalKey;
    private final List<Point> points;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final double size;
    private final String text;
    private final String strokeColor;
    private final String fillColor;
    private final double strokeWidth;
    private final String dashPattern;
    private final String textAnchor;
    private final boolean protectedGeometry;

    private Primitive(String id, PrimitiveType type, String representedObjectId,
        String representedExternalKey, List<Point> points, double x, double y, double width,
        double height, double size, String text, String strokeColor, String fillColor,
        double strokeWidth, String dashPattern, String textAnchor, boolean protectedGeometry) {
      this.id = requireText(id, "id");
      if (type == null) {
        throw new IllegalArgumentException("type must not be null");
      }
      this.type = type;
      this.representedObjectId = requireText(representedObjectId, "representedObjectId");
      this.representedExternalKey =
          requireText(representedExternalKey, "representedExternalKey");
      this.points = immutablePoints(points);
      this.x = finite(x, "x");
      this.y = finite(y, "y");
      this.width = finite(width, "width");
      this.height = finite(height, "height");
      this.size = finite(size, "size");
      this.text = optionalText(text);
      this.strokeColor = color(strokeColor, "strokeColor");
      this.fillColor = color(fillColor, "fillColor");
      this.strokeWidth = finite(strokeWidth, "strokeWidth");
      this.dashPattern = optionalText(dashPattern);
      this.textAnchor = optionalText(textAnchor);
      this.protectedGeometry = protectedGeometry;
      validateGeometry();
    }

    public static Primitive rectangle(String id, String representedObjectId,
        String representedExternalKey, double x, double y, double width, double height,
        String strokeColor, String fillColor, double strokeWidth) {
      return new Primitive(id, PrimitiveType.RECTANGLE, representedObjectId,
          representedExternalKey, Collections.<Point>emptyList(), x, y, width, height, 0.0, "",
          strokeColor, fillColor, strokeWidth, "", "", false);
    }

    public static Primitive polyline(String id, String representedObjectId,
        String representedExternalKey, List<Point> points, String strokeColor, double strokeWidth,
        String dashPattern, boolean protectedGeometry) {
      return new Primitive(id, PrimitiveType.POLYLINE, representedObjectId,
          representedExternalKey, points, 0.0, 0.0, 0.0, 0.0, 0.0, "", strokeColor, "none",
          strokeWidth, dashPattern, "", protectedGeometry);
    }

    public static Primitive polygon(String id, String representedObjectId,
        String representedExternalKey, List<Point> points, String strokeColor, String fillColor,
        double strokeWidth) {
      return new Primitive(id, PrimitiveType.POLYGON, representedObjectId,
          representedExternalKey, points, 0.0, 0.0, 0.0, 0.0, 0.0, "", strokeColor, fillColor,
          strokeWidth, "", "", false);
    }

    public static Primitive text(String id, String representedObjectId,
        String representedExternalKey, double x, double y, double size, String text,
        String fillColor, String textAnchor) {
      return new Primitive(id, PrimitiveType.TEXT, representedObjectId, representedExternalKey,
          Collections.<Point>emptyList(), x, y, 0.0, 0.0, size, requireText(text, "text"), "none",
          fillColor, 0.0, "", textAnchor, false);
    }

    public String getId() {
      return id;
    }

    public PrimitiveType getType() {
      return type;
    }

    public String getRepresentedObjectId() {
      return representedObjectId;
    }

    public String getRepresentedExternalKey() {
      return representedExternalKey;
    }

    public List<Point> getPoints() {
      return Collections.unmodifiableList(new ArrayList<Point>(points));
    }

    public double getX() {
      return x;
    }

    public double getY() {
      return y;
    }

    public double getWidth() {
      return width;
    }

    public double getHeight() {
      return height;
    }

    public double getSize() {
      return size;
    }

    public String getText() {
      return text;
    }

    public String getStrokeColor() {
      return strokeColor;
    }

    public String getFillColor() {
      return fillColor;
    }

    public double getStrokeWidth() {
      return strokeWidth;
    }

    public String getDashPattern() {
      return dashPattern;
    }

    public String getTextAnchor() {
      return textAnchor;
    }

    public boolean isProtectedGeometry() {
      return protectedGeometry;
    }

    private void validateGeometry() {
      if (strokeWidth < 0.0) {
        throw new IllegalArgumentException("strokeWidth must not be negative");
      }
      if (type == PrimitiveType.RECTANGLE && (width <= 0.0 || height <= 0.0)) {
        throw new IllegalArgumentException("rectangle width and height must be positive");
      }
      if (type == PrimitiveType.POLYLINE && points.size() < 2) {
        throw new IllegalArgumentException("polyline requires at least two points");
      }
      if (type == PrimitiveType.POLYGON && points.size() < 3) {
        throw new IllegalArgumentException("polygon requires at least three points");
      }
      if (type == PrimitiveType.TEXT && (size <= 0.0 || text.isEmpty())) {
        throw new IllegalArgumentException("text size and content must be present");
      }
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("type", type.name());
      result.put("representedObjectId", representedObjectId);
      result.put("representedExternalKey", representedExternalKey);
      List<Map<String, Object>> pointMaps = new ArrayList<Map<String, Object>>();
      for (Point point : points) {
        pointMaps.add(point.toMap());
      }
      result.put("points", pointMaps);
      result.put("x", Double.valueOf(x));
      result.put("y", Double.valueOf(y));
      result.put("width", Double.valueOf(width));
      result.put("height", Double.valueOf(height));
      result.put("size", Double.valueOf(size));
      result.put("text", text);
      result.put("strokeColor", strokeColor);
      result.put("fillColor", fillColor);
      result.put("strokeWidth", Double.valueOf(strokeWidth));
      result.put("dashPattern", dashPattern);
      result.put("textAnchor", textAnchor);
      result.put("protectedGeometry", Boolean.valueOf(protectedGeometry));
      return result;
    }
  }

  /** Immutable diagnostic describing fallback, loss, or invalid projection input. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subjectId;

    public Diagnostic(Severity severity, String code, String message, String subjectId) {
      if (severity == null) {
        throw new IllegalArgumentException("severity must not be null");
      }
      this.severity = severity;
      this.code = requireText(code, "code");
      this.message = requireText(message, "message");
      this.subjectId = requireText(subjectId, "subjectId");
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }

    public String getSubjectId() {
      return subjectId;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      result.put("subjectId", subjectId);
      return result;
    }
  }

  private final String projectId;
  private final String revision;
  private final String sourceGraphFingerprint;
  private final String sourceReference;
  private final Unit unit = Unit.MILLIMETRE;
  private final VerificationStatus verificationStatus;
  private final List<Primitive> primitives;
  private final List<Diagnostic> diagnostics;

  public EngineeringGraphicalProjection(String projectId, String revision,
      String sourceGraphFingerprint, String sourceReference, VerificationStatus verificationStatus,
      List<Primitive> primitives, List<Diagnostic> diagnostics) {
    this.projectId = requireText(projectId, "projectId");
    this.revision = requireText(revision, "revision");
    this.sourceGraphFingerprint = requireText(sourceGraphFingerprint, "sourceGraphFingerprint");
    this.sourceReference = requireText(sourceReference, "sourceReference");
    if (verificationStatus == null) {
      throw new IllegalArgumentException("verificationStatus must not be null");
    }
    this.verificationStatus = verificationStatus;
    this.primitives = immutablePrimitives(primitives);
    this.diagnostics = immutableDiagnostics(diagnostics);
  }

  public String getProjectId() {
    return projectId;
  }

  public String getRevision() {
    return revision;
  }

  public String getSourceGraphFingerprint() {
    return sourceGraphFingerprint;
  }

  public String getSourceReference() {
    return sourceReference;
  }

  public Unit getUnit() {
    return unit;
  }

  public VerificationStatus getVerificationStatus() {
    return verificationStatus;
  }

  public List<Primitive> getPrimitives() {
    return Collections.unmodifiableList(new ArrayList<Primitive>(primitives));
  }

  public List<Diagnostic> getDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  public boolean isComplete() {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return false;
      }
    }
    return true;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("projectId", projectId);
    result.put("revision", revision);
    result.put("sourceGraphFingerprint", sourceGraphFingerprint);
    result.put("sourceReference", sourceReference);
    result.put("unit", unit.name());
    result.put("verificationStatus", verificationStatus.name());
    List<Map<String, Object>> primitiveMaps = new ArrayList<Map<String, Object>>();
    for (Primitive primitive : primitives) {
      primitiveMaps.add(primitive.toMap());
    }
    result.put("primitives", primitiveMaps);
    List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
    for (Diagnostic diagnostic : diagnostics) {
      diagnosticMaps.add(diagnostic.toMap());
    }
    result.put("diagnostics", diagnosticMaps);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  public static EngineeringGraphicalProjection fromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("json must not be blank");
    }
    JsonElement parsed = JsonParser.parseString(json);
    if (!parsed.isJsonObject()) {
      throw new IllegalArgumentException("Graphical projection JSON root must be an object");
    }
    JsonObject root = parsed.getAsJsonObject();
    if (!root.has("schemaVersion") || !SCHEMA_VERSION.equals(root.get("schemaVersion").getAsString())) {
      throw new IllegalArgumentException("Unsupported graphical projection schema version");
    }
    if (!Unit.MILLIMETRE.name().equals(root.get("unit").getAsString())) {
      throw new IllegalArgumentException("Unsupported graphical projection unit");
    }
    List<Primitive> primitives = new ArrayList<Primitive>();
    for (JsonElement element : root.getAsJsonArray("primitives")) {
      primitives.add(primitiveFromJson(element.getAsJsonObject()));
    }
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    for (JsonElement element : root.getAsJsonArray("diagnostics")) {
      JsonObject item = element.getAsJsonObject();
      diagnostics.add(new Diagnostic(Severity.valueOf(item.get("severity").getAsString()),
          item.get("code").getAsString(), item.get("message").getAsString(),
          item.get("subjectId").getAsString()));
    }
    return new EngineeringGraphicalProjection(root.get("projectId").getAsString(),
        root.get("revision").getAsString(), root.get("sourceGraphFingerprint").getAsString(),
        root.get("sourceReference").getAsString(),
        VerificationStatus.valueOf(root.get("verificationStatus").getAsString()), primitives,
        diagnostics);
  }

  private static Primitive primitiveFromJson(JsonObject item) {
    PrimitiveType type = PrimitiveType.valueOf(item.get("type").getAsString());
    String id = item.get("id").getAsString();
    String representedObjectId = item.get("representedObjectId").getAsString();
    String representedExternalKey = item.get("representedExternalKey").getAsString();
    List<Point> points = new ArrayList<Point>();
    JsonArray pointArray = item.getAsJsonArray("points");
    for (JsonElement element : pointArray) {
      JsonObject point = element.getAsJsonObject();
      points.add(new Point(point.get("x").getAsDouble(), point.get("y").getAsDouble()));
    }
    return new Primitive(id, type, representedObjectId, representedExternalKey, points,
        item.get("x").getAsDouble(), item.get("y").getAsDouble(),
        item.get("width").getAsDouble(), item.get("height").getAsDouble(),
        item.get("size").getAsDouble(), item.get("text").getAsString(),
        item.get("strokeColor").getAsString(), item.get("fillColor").getAsString(),
        item.get("strokeWidth").getAsDouble(), item.get("dashPattern").getAsString(),
        item.get("textAnchor").getAsString(), item.get("protectedGeometry").getAsBoolean());
  }

  private static List<Primitive> immutablePrimitives(List<Primitive> values) {
    if (values == null) {
      throw new IllegalArgumentException("primitives must not be null");
    }
    List<Primitive> result = new ArrayList<Primitive>(values);
    for (Primitive primitive : result) {
      if (primitive == null) {
        throw new IllegalArgumentException("primitive must not be null");
      }
    }
    Collections.sort(result, new Comparator<Primitive>() {
      @Override
      public int compare(Primitive left, Primitive right) {
        return left.getId().compareTo(right.getId());
      }
    });
    TreeSet<String> ids = new TreeSet<String>();
    for (Primitive primitive : result) {
      if (!ids.add(primitive.getId())) {
        throw new IllegalArgumentException("Duplicate graphical primitive " + primitive.getId());
      }
    }
    return Collections.unmodifiableList(result);
  }

  private static List<Diagnostic> immutableDiagnostics(List<Diagnostic> values) {
    if (values == null) {
      throw new IllegalArgumentException("diagnostics must not be null");
    }
    List<Diagnostic> result = new ArrayList<Diagnostic>(values);
    for (Diagnostic diagnostic : result) {
      if (diagnostic == null) {
        throw new IllegalArgumentException("diagnostic must not be null");
      }
    }
    Collections.sort(result, new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic left, Diagnostic right) {
        int code = left.getCode().compareTo(right.getCode());
        return code == 0 ? left.getSubjectId().compareTo(right.getSubjectId()) : code;
      }
    });
    return Collections.unmodifiableList(result);
  }

  private static List<Point> immutablePoints(List<Point> values) {
    if (values == null) {
      throw new IllegalArgumentException("points must not be null");
    }
    List<Point> result = new ArrayList<Point>(values);
    for (Point point : result) {
      if (point == null) {
        throw new IllegalArgumentException("point must not be null");
      }
    }
    return Collections.unmodifiableList(result);
  }

  private static String color(String value, String name) {
    String result = requireText(value, name).toLowerCase(java.util.Locale.ROOT);
    if (!"none".equals(result) && !result.matches("#[0-9a-f]{6}")) {
      throw new IllegalArgumentException(name + " must use #RRGGBB or none");
    }
    return result;
  }

  private static String optionalText(String value) {
    return value == null ? "" : value.trim();
  }

  private static String requireText(String value, String name) {
    String result = optionalText(value);
    if (result.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return result;
  }

  private static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
    return value;
  }
}
