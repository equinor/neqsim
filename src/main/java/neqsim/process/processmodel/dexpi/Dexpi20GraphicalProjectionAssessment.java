package neqsim.process.processmodel.dexpi;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import neqsim.process.engineering.model.EngineeringGraphicalProjection;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Point;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Primitive;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.PrimitiveType;

/**
 * Assesses whether supported generic graphical content survives native DEXPI 2.0 serialization.
 *
 * <p>
 * This is an internal export-inspection check, not a DEXPI profile, external-tool, standards-conformance, or drawing
 * approval claim. It compares stable primitive identities, represented-object references, mapped geometry and mapped
 * styles. Deliberately lossy mappings remain explicit diagnostics.
 * </p>
 */
public final class Dexpi20GraphicalProjectionAssessment {
  private static final String SCHEMA_VERSION = "neqsim_dexpi_2_0_graphical_projection_assessment.v1";

  private Dexpi20GraphicalProjectionAssessment() {
  }

  /** Severity of one graphical-equivalence diagnostic. */
  public enum Severity {
    /** Informational evidence that does not affect supported equivalence. */
    INFO,
    /** Confirmed deterministic approximation or retained graphical loss. */
    WARNING,
    /** Missing, unexpected, ambiguous, or changed supported content. */
    ERROR
  }

  /** Immutable graphical-equivalence diagnostic. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subjectId;

    Diagnostic(Severity severity, String code, String message, String subjectId) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subjectId = subjectId == null ? "" : subjectId;
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

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      result.put("subjectId", subjectId);
      return result;
    }
  }

  /** Deterministic evidence for supported graphical export equivalence. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String sourceGraphFingerprint;
    private final String sourceReference;
    private final String revision;
    private final String inspectedFileSha256;
    private final Map<String, String> expectedPrimitiveSignatures;
    private final Map<String, String> exportedPrimitiveSignatures;
    private final int matchedPrimitiveCount;
    private final List<Diagnostic> diagnostics;

    Report(EngineeringGraphicalProjection projection, String inspectedFileSha256, Map<String, String> expected,
        Map<String, String> exported, int matchedPrimitiveCount, List<Diagnostic> diagnostics) {
      this.sourceGraphFingerprint = projection.getSourceGraphFingerprint();
      this.sourceReference = projection.getSourceReference();
      this.revision = projection.getRevision();
      this.inspectedFileSha256 = inspectedFileSha256;
      this.expectedPrimitiveSignatures = immutableMap(expected);
      this.exportedPrimitiveSignatures = immutableMap(exported);
      this.matchedPrimitiveCount = matchedPrimitiveCount;
      this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
    }

    public String getSourceGraphFingerprint() {
      return sourceGraphFingerprint;
    }

    public String getSourceReference() {
      return sourceReference;
    }

    public String getRevision() {
      return revision;
    }

    /**
     * Returns the full SHA-256 of the exact inspected DEXPI XML bytes.
     *
     * @return lower-case hexadecimal SHA-256
     */
    public String getInspectedFileSha256() {
      return inspectedFileSha256;
    }

    public Map<String, String> getExpectedPrimitiveSignatures() {
      return expectedPrimitiveSignatures;
    }

    public Map<String, String> getExportedPrimitiveSignatures() {
      return exportedPrimitiveSignatures;
    }

    public int getMatchedPrimitiveCount() {
      return matchedPrimitiveCount;
    }

    public List<Diagnostic> getDiagnostics() {
      return diagnostics;
    }

    /**
     * Returns whether every supported projected primitive survived with equivalent mapped semantics.
     *
     * @return true when there are no equivalence errors
     */
    public boolean isSupportedProjectionEquivalent() {
      for (Diagnostic diagnostic : diagnostics) {
        if (diagnostic.getSeverity() == Severity.ERROR) {
          return false;
        }
      }
      return true;
    }

    /**
     * Returns deterministic machine-readable assessment evidence.
     *
     * @return key-ordered evidence map
     */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", SCHEMA_VERSION);
      result.put("sourceGraphFingerprint", sourceGraphFingerprint);
      result.put("sourceReference", sourceReference);
      result.put("revision", revision);
      result.put("inspectedFileSha256", inspectedFileSha256);
      result.put("engineeringState", "CALCULATED_GRAPHICS");
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("expectedPrimitiveSignatures", expectedPrimitiveSignatures);
      result.put("exportedPrimitiveSignatures", exportedPrimitiveSignatures);
      result.put("expectedPrimitiveCount", Integer.valueOf(expectedPrimitiveSignatures.size()));
      result.put("exportedPrimitiveCount", Integer.valueOf(exportedPrimitiveSignatures.size()));
      result.put("matchedPrimitiveCount", Integer.valueOf(matchedPrimitiveCount));
      List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
      for (Diagnostic diagnostic : diagnostics) {
        diagnosticMaps.add(diagnostic.toMap());
      }
      result.put("diagnostics", diagnosticMaps);
      result.put("supportedProjectionEquivalent", Boolean.valueOf(isSupportedProjectionEquivalent()));
      return result;
    }

    /**
     * Returns deterministic pretty-printed JSON evidence.
     *
     * @return JSON assessment
     */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }

    private static Map<String, String> immutableMap(Map<String, String> source) {
      return Collections.unmodifiableMap(new LinkedHashMap<String, String>(new TreeMap<String, String>(source)));
    }
  }

  /**
   * Inspects one native DEXPI 2.0 file against the projection that drove its graphical export.
   *
   * @param projection controlled source projection
   * @param file native DEXPI 2.0 file to inspect
   * @return deterministic supported-content equivalence evidence
   * @throws IOException when the file cannot be parsed
   */
  public static Report assess(EngineeringGraphicalProjection projection, Path file) throws IOException {
    if (projection == null || file == null) {
      throw new IllegalArgumentException("projection and file must not be null");
    }
    Document document = parse(file);
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    Map<String, String> expected = expectedSignatures(projection, diagnostics);
    Map<String, String> exported = exportedSignatures(document, diagnostics);
    assessDiagram(document, projection, diagnostics);
    int matched = compare(expected, exported, diagnostics);
    Collections.sort(diagnostics, new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic left, Diagnostic right) {
        int bySeverity = left.getSeverity().compareTo(right.getSeverity());
        if (bySeverity != 0) {
          return bySeverity;
        }
        int byCode = left.getCode().compareTo(right.getCode());
        return byCode == 0 ? left.getSubjectId().compareTo(right.getSubjectId()) : byCode;
      }
    });
    return new Report(projection, sha256(file), expected, exported, matched, diagnostics);
  }

  private static Map<String, String> expectedSignatures(EngineeringGraphicalProjection projection,
      List<Diagnostic> diagnostics) {
    Map<String, String> result = new TreeMap<String, String>();
    for (Primitive primitive : projection.getPrimitives()) {
      String id = "GraphicalPrimitive_" + Dexpi20GraphicalProjectionWriter.digest(primitive.getId());
      if (result.put(id, expectedSignature(primitive, diagnostics)) != null) {
        diagnostics.add(error("DEXPI_GRAPHICS_EXPECTED_ID_COLLISION",
            "Two projection primitives resolve to the same stable DEXPI identity", primitive.getId()));
      }
    }
    return result;
  }

  private static String expectedSignature(Primitive primitive, List<Diagnostic> diagnostics) {
    StringBuilder result = new StringBuilder();
    result.append(primitive.getRepresentedExternalKey()).append('|');
    if (primitive.getType() == PrimitiveType.TEXT) {
      result.append("Core/Diagram.Text|");
      result.append(point(primitive.getX(), primitive.getY())).append('|');
      result.append(primitive.getText()).append('|');
      result.append(number(primitive.getSize())).append('|');
      result.append(textAlignment(primitive.getTextAnchor())).append('|');
      result.append(color("none".equals(primitive.getFillColor()) ? "#000000" : primitive.getFillColor()));
      if ("none".equals(primitive.getFillColor())) {
        diagnostics.add(warning("DEXPI_GRAPHICS_COLOR_FALLBACK_CONFIRMED",
            "DEXPI Core requires text color, so projection color none maps to black", primitive.getId()));
      }
      return result.toString();
    }
    if (primitive.getType() == PrimitiveType.POLYLINE) {
      result.append("Core/Diagram.PolyLine|");
      result.append(points(primitive.getPoints())).append('|');
    } else {
      result.append("Core/Diagram.Polygon|");
      result.append(points(polygonPoints(primitive))).append('|');
      result.append("none".equals(primitive.getFillColor()) ? "Transparent" : "Solid").append('|');
      if (!"none".equals(primitive.getFillColor())) {
        diagnostics.add(warning("DEXPI_GRAPHICS_FILL_COLOR_LOSS_CONFIRMED",
            "DEXPI Core Polygon retains solid fill state but not the projection fill color", primitive.getId()));
      }
    }
    result.append(color("none".equals(primitive.getStrokeColor()) ? "#000000" : primitive.getStrokeColor()))
        .append('|');
    result.append(primitive.getDashPattern().isEmpty() ? "Solid" : "Dash").append('|');
    result.append(number(primitive.getStrokeWidth()));
    if (!primitive.getDashPattern().isEmpty()) {
      diagnostics.add(warning("DEXPI_GRAPHICS_DASH_APPROXIMATION_CONFIRMED",
          "Numeric projection dash pattern maps to the generic DEXPI Dash enumeration", primitive.getId()));
    }
    return result.toString();
  }

  private static Map<String, String> exportedSignatures(Document document, List<Diagnostic> diagnostics) {
    Map<String, String> objectKeys = representedExternalKeys(document);
    Map<Element, String> groupKeys = representationGroupKeys(document, objectKeys, diagnostics);
    Map<String, String> result = new TreeMap<String, String>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      String id = object.getAttribute("id");
      if (!id.startsWith("GraphicalPrimitive_")) {
        continue;
      }
      Element group = ancestor(object, "Core/Diagram.RepresentationGroup");
      String externalKey = group == null ? "" : groupKeys.get(group);
      if (group == null || externalKey == null || externalKey.isEmpty()) {
        diagnostics.add(error("DEXPI_GRAPHICS_PRIMITIVE_GROUP_UNRESOLVED",
            "Graphical primitive does not resolve through a representation group to one exported object", id));
        externalKey = "";
      }
      String previous = result.put(id, exportedSignature(object, externalKey));
      if (previous != null) {
        diagnostics.add(error("DEXPI_GRAPHICS_EXPORTED_ID_DUPLICATE",
            "DEXPI document contains duplicate graphical primitive identities", id));
      }
    }
    return result;
  }

  private static String exportedSignature(Element primitive, String externalKey) {
    String type = primitive.getAttribute("type");
    StringBuilder result = new StringBuilder();
    result.append(externalKey).append('|').append(type).append('|');
    if ("Core/Diagram.Text".equals(type)) {
      Element position = directAggregate(primitive, "Position");
      result.append(point(directDouble(position, "X"), directDouble(position, "Y"))).append('|');
      result.append(directString(primitive, "Text")).append('|');
      result.append(number(directDouble(primitive, "Size"))).append('|');
      result.append(referenceName(directDataReference(primitive, "Alignment"))).append('|');
      result.append(readColor(directAggregate(primitive, "Color")));
      return result.toString();
    }
    result.append(readPoints(primitive)).append('|');
    if ("Core/Diagram.Polygon".equals(type)) {
      result.append(referenceName(directDataReference(primitive, "FillStyle"))).append('|');
    }
    Element stroke = directAggregate(primitive, "Stroke");
    result.append(readColor(directAggregate(stroke, "Color"))).append('|');
    result.append(referenceName(directDataReference(stroke, "DashStyle"))).append('|');
    result.append(number(directDouble(stroke, "Width")));
    return result.toString();
  }

  private static int compare(Map<String, String> expected, Map<String, String> exported, List<Diagnostic> diagnostics) {
    Set<String> ids = new LinkedHashSet<String>();
    ids.addAll(expected.keySet());
    ids.addAll(exported.keySet());
    int matched = 0;
    for (String id : ids) {
      if (!expected.containsKey(id)) {
        diagnostics.add(error("DEXPI_GRAPHICS_PRIMITIVE_UNEXPECTED",
            "DEXPI document contains a graphical primitive absent from the source projection", id));
      } else if (!exported.containsKey(id)) {
        diagnostics.add(error("DEXPI_GRAPHICS_PRIMITIVE_MISSING",
            "Source projection primitive is absent from the DEXPI document", id));
      } else if (!expected.get(id).equals(exported.get(id))) {
        diagnostics.add(error("DEXPI_GRAPHICS_PRIMITIVE_MISMATCH",
            "Represented identity, mapped geometry, or mapped style differs from the source projection", id));
      } else {
        matched++;
      }
    }
    return matched;
  }

  private static void assessDiagram(Document document, EngineeringGraphicalProjection projection,
      List<Diagnostic> diagnostics) {
    String id = "Diagram_"
        + Dexpi20GraphicalProjectionWriter.digest(projection.getProjectId() + ":" + projection.getRevision());
    Element diagram = objectById(document, id);
    if (diagram == null || !"Core/Diagram.Diagram".equals(diagram.getAttribute("type"))) {
      diagnostics.add(error("DEXPI_GRAPHICS_DIAGRAM_MISSING",
          "Expected stable Core Diagram identity is absent from the DEXPI document", id));
      return;
    }
    String expectedName = projection.getProjectId() + " " + projection.getRevision() + " "
        + projection.getSourceReference();
    if (!expectedName.equals(directString(diagram, "Name"))) {
      diagnostics.add(error("DEXPI_GRAPHICS_DIAGRAM_METADATA_MISMATCH",
          "Diagram name does not retain project, revision, and controlled source reference", id));
    }
    double[] expected = bounds(projection.getPrimitives());
    double[] actual = new double[] { directDouble(diagram, "MinX"), directDouble(diagram, "MinY"),
        directDouble(diagram, "MaxX"), directDouble(diagram, "MaxY") };
    for (int index = 0; index < expected.length; index++) {
      if (Double.compare(expected[index], actual[index]) != 0) {
        diagnostics.add(error("DEXPI_GRAPHICS_DIAGRAM_BOUNDS_MISMATCH",
            "Diagram bounds differ from the supported source projection", id));
        break;
      }
    }
  }

  private static Map<Element, String> representationGroupKeys(Document document, Map<String, String> objectKeys,
      List<Diagnostic> diagnostics) {
    Map<Element, String> result = new LinkedHashMap<Element, String>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (!"Core/Diagram.RepresentationGroup".equals(object.getAttribute("type"))) {
        continue;
      }
      String representedId = directReference(object, "Represents");
      String externalKey = objectKeys.get(representedId);
      if (externalKey == null || externalKey.isEmpty()) {
        diagnostics.add(error("DEXPI_GRAPHICS_GROUP_REPRESENTS_UNRESOLVED",
            "Representation group does not resolve to one tagged exported conceptual object",
            object.getAttribute("id")));
        externalKey = "";
      }
      result.put(object, externalKey);
    }
    return result;
  }

  private static Map<String, String> representedExternalKeys(Document document) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      String id = object.getAttribute("id");
      String key = firstText(object, "TagName", "PipeConnectorNumber", "LineNumber");
      if (!id.isEmpty() && !key.isEmpty()) {
        result.put(id, key);
      }
    }
    return result;
  }

  private static String firstText(Element object, String... properties) {
    for (String property : properties) {
      String value = directString(object, property);
      if (!value.isEmpty()) {
        return value;
      }
    }
    return "";
  }

  private static Element ancestor(Element source, String type) {
    for (Node node = source.getParentNode(); node != null; node = node.getParentNode()) {
      if (node instanceof Element && "Object".equals(((Element) node).getTagName())
          && type.equals(((Element) node).getAttribute("type"))) {
        return (Element) node;
      }
    }
    return null;
  }

  private static Element objectById(Document document, String id) {
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (id.equals(object.getAttribute("id"))) {
        return object;
      }
    }
    return null;
  }

  private static String directReference(Element parent, String property) {
    if (parent == null) {
      return "";
    }
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "References".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return ((Element) child).getAttribute("objects").replaceFirst("^#", "");
      }
    }
    return "";
  }

  private static String directString(Element parent, String property) {
    Element data = directData(parent, property);
    if (data == null) {
      return "";
    }
    NodeList values = data.getElementsByTagName("String");
    return values.getLength() == 0 ? "" : values.item(0).getTextContent();
  }

  private static double directDouble(Element parent, String property) {
    Element data = directData(parent, property);
    if (data == null) {
      return Double.NaN;
    }
    NodeList values = data.getElementsByTagName("Double");
    if (values.getLength() == 0) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(values.item(0).getTextContent());
    } catch (NumberFormatException exception) {
      return Double.NaN;
    }
  }

  private static Element directAggregate(Element parent, String property) {
    Element data = directData(parent, property);
    if (data == null) {
      return null;
    }
    for (Node child = data.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "AggregatedDataValue".equals(((Element) child).getTagName())) {
        return (Element) child;
      }
    }
    return null;
  }

  private static String directDataReference(Element parent, String property) {
    Element data = directData(parent, property);
    if (data == null) {
      return "";
    }
    NodeList values = data.getElementsByTagName("DataReference");
    return values.getLength() == 0 ? "" : ((Element) values.item(0)).getAttribute("data");
  }

  private static Element directData(Element parent, String property) {
    if (parent == null) {
      return null;
    }
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Data".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return (Element) child;
      }
    }
    return null;
  }

  private static String readPoints(Element primitive) {
    Element data = directData(primitive, "Points");
    if (data == null) {
      return "";
    }
    List<Point> points = new ArrayList<Point>();
    for (Node child = data.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "AggregatedDataValue".equals(((Element) child).getTagName())) {
        Element value = (Element) child;
        points.add(new Point(directDouble(value, "X"), directDouble(value, "Y")));
      }
    }
    return points(points);
  }

  private static String readColor(Element value) {
    int red = directInteger(value, "R");
    int green = directInteger(value, "G");
    int blue = directInteger(value, "B");
    if (red < 0 || green < 0 || blue < 0 || red > 255 || green > 255 || blue > 255) {
      return "invalid";
    }
    return String.format(java.util.Locale.ROOT, "#%02x%02x%02x", Integer.valueOf(red), Integer.valueOf(green),
        Integer.valueOf(blue));
  }

  private static int directInteger(Element parent, String property) {
    Element data = directData(parent, property);
    if (data == null) {
      return -1;
    }
    NodeList values = data.getElementsByTagName("Integer");
    if (values.getLength() == 0) {
      return -1;
    }
    try {
      return Integer.parseInt(values.item(0).getTextContent());
    } catch (NumberFormatException exception) {
      return -1;
    }
  }

  private static List<Point> polygonPoints(Primitive primitive) {
    if (primitive.getType() != PrimitiveType.RECTANGLE) {
      return primitive.getPoints();
    }
    List<Point> points = new ArrayList<Point>();
    points.add(new Point(primitive.getX(), primitive.getY()));
    points.add(new Point(primitive.getX() + primitive.getWidth(), primitive.getY()));
    points.add(new Point(primitive.getX() + primitive.getWidth(), primitive.getY() + primitive.getHeight()));
    points.add(new Point(primitive.getX(), primitive.getY() + primitive.getHeight()));
    return points;
  }

  private static String points(List<Point> values) {
    StringBuilder result = new StringBuilder();
    for (Point value : values) {
      if (result.length() > 0) {
        result.append(';');
      }
      result.append(point(value.getX(), value.getY()));
    }
    return result.toString();
  }

  private static String point(double x, double y) {
    return number(x) + ',' + number(y);
  }

  private static String number(double value) {
    return Double.toString(value == 0.0 ? 0.0 : value);
  }

  private static String color(String value) {
    return value.toLowerCase(java.util.Locale.ROOT);
  }

  private static String textAlignment(String anchor) {
    return "middle".equals(anchor) ? "CenterCenter" : "end".equals(anchor) ? "RightCenter" : "LeftCenter";
  }

  private static String referenceName(String value) {
    int separator = value.lastIndexOf('.');
    return separator < 0 ? value : value.substring(separator + 1);
  }

  private static double[] bounds(List<Primitive> primitives) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (Primitive primitive : primitives) {
      List<Point> values = primitive.getType() == PrimitiveType.RECTANGLE ? polygonPoints(primitive)
          : primitive.getType() == PrimitiveType.TEXT
              ? Collections.singletonList(new Point(primitive.getX(), primitive.getY()))
              : primitive.getPoints();
      for (Point value : values) {
        minX = Math.min(minX, value.getX());
        minY = Math.min(minY, value.getY());
        maxX = Math.max(maxX, value.getX());
        maxY = Math.max(maxY, value.getY());
      }
    }
    return new double[] { minX - 10.0, minY - 10.0, maxX + 10.0, maxY + 10.0 };
  }

  private static Diagnostic warning(String code, String message, String subjectId) {
    return new Diagnostic(Severity.WARNING, code, message, subjectId);
  }

  private static Diagnostic error(String code, String message, String subjectId) {
    return new Diagnostic(Severity.ERROR, code, message, subjectId);
  }

  private static String sha256(Path file) throws IOException {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
      StringBuilder result = new StringBuilder();
      for (byte value : bytes) {
        result.append(String.format(java.util.Locale.ROOT, "%02x", Integer.valueOf(value & 255)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by every supported Java runtime", exception);
    }
  }

  private static Document parse(Path file) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(file.toFile());
    } catch (ParserConfigurationException exception) {
      throw new IOException("Could not configure DEXPI graphical-projection assessment", exception);
    } catch (SAXException exception) {
      throw new IOException("Could not parse DEXPI graphical-projection assessment input", exception);
    }
  }
}
