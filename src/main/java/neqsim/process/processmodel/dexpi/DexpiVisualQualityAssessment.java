package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Performs deterministic structural visual-quality checks on a Proteus-compatible DEXPI Plant/P&amp;ID document and its
 * NeqSim-native SVG projection.
 *
 * <p>
 * The assessment deliberately separates machine-checkable drawing defects from engineering completeness and standards
 * approval. It verifies that the exchange contains drawable geometry, that catalogue references and identities survive
 * into SVG, and that positioned content stays inside the declared drawing extent. It does not establish ISO
 * conformance, DEXPI certification, engineering approval, or fitness for construction.
 * </p>
 */
public final class DexpiVisualQualityAssessment {
  private static final double MINIMUM_READABLE_TEXT_HEIGHT_MM = 1.8;

  private DexpiVisualQualityAssessment() {
  }

  /** Severity assigned to one deterministic quality finding. */
  public enum Severity {
    /** The document cannot be rendered faithfully without repair. */
    ERROR,
    /** The document renders, but review is required for a visible quality risk. */
    WARNING
  }

  /** One stable, machine-readable visual-quality finding. */
  public static final class Finding {
    private final Severity severity;
    private final String code;
    private final String elementId;
    private final String message;

    Finding(Severity severity, String code, String elementId, String message) {
      this.severity = severity;
      this.code = code;
      this.elementId = elementId;
      this.message = message;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getCode() {
      return code;
    }

    public String getElementId() {
      return elementId;
    }

    public String getMessage() {
      return message;
    }
  }

  /** Immutable result for one DEXPI document and its generated SVG. */
  public static final class Report {
    private final String profile;
    private final String drawingName;
    private final double widthMillimetres;
    private final double heightMillimetres;
    private final String svgSha256;
    private final Map<String, Integer> metrics;
    private final List<Finding> findings;

    Report(String profile, String drawingName, double widthMillimetres, double heightMillimetres, String svgSha256,
        Map<String, Integer> metrics, List<Finding> findings) {
      this.profile = profile;
      this.drawingName = drawingName;
      this.widthMillimetres = widthMillimetres;
      this.heightMillimetres = heightMillimetres;
      this.svgSha256 = svgSha256;
      this.metrics = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(metrics));
      this.findings = Collections.unmodifiableList(new ArrayList<Finding>(findings));
    }

    public String getProfile() {
      return profile;
    }

    public String getDrawingName() {
      return drawingName;
    }

    public double getWidthMillimetres() {
      return widthMillimetres;
    }

    public double getHeightMillimetres() {
      return heightMillimetres;
    }

    public String getSvgSha256() {
      return svgSha256;
    }

    public Map<String, Integer> getMetrics() {
      return metrics;
    }

    public List<Finding> getFindings() {
      return findings;
    }

    /** @return {@code true} when at least one rendering-blocking defect was found */
    public boolean hasErrors() {
      for (Finding finding : findings) {
        if (finding.getSeverity() == Severity.ERROR) {
          return true;
        }
      }
      return false;
    }

    /**
     * Serializes the report with stable field and finding order.
     *
     * @return deterministic JSON suitable for CI artifacts and revision comparison
     */
    public String toJson() {
      StringBuilder json = new StringBuilder();
      json.append('{');
      appendJsonField(json, "profile", profile).append(',');
      appendJsonField(json, "drawingName", drawingName).append(',');
      json.append("\"widthMillimetres\":").append(number(widthMillimetres)).append(',');
      json.append("\"heightMillimetres\":").append(number(heightMillimetres)).append(',');
      appendJsonField(json, "svgSha256", svgSha256).append(',');
      json.append("\"metrics\":{");
      int metricIndex = 0;
      for (Map.Entry<String, Integer> metric : metrics.entrySet()) {
        if (metricIndex++ > 0) {
          json.append(',');
        }
        json.append('"').append(json(metric.getKey())).append("\":").append(metric.getValue());
      }
      json.append("},\"findings\":[");
      for (int index = 0; index < findings.size(); index++) {
        Finding finding = findings.get(index);
        if (index > 0) {
          json.append(',');
        }
        json.append('{');
        appendJsonField(json, "severity", finding.getSeverity().name()).append(',');
        appendJsonField(json, "code", finding.getCode()).append(',');
        appendJsonField(json, "elementId", finding.getElementId()).append(',');
        appendJsonField(json, "message", finding.getMessage());
        json.append('}');
      }
      json.append("]}");
      return json.toString();
    }
  }

  /**
   * Assesses one Proteus-compatible DEXPI Plant/P&amp;ID document.
   *
   * @param dexpiFile DEXPI XML produced by a NeqSim Proteus-compatible export method
   * @return immutable deterministic assessment
   * @throws IOException if the XML or generated SVG cannot be parsed
   */
  public static Report assess(File dexpiFile) throws IOException {
    if (dexpiFile == null) {
      throw new IllegalArgumentException("dexpiFile must not be null");
    }
    Document document = parse(dexpiFile);
    String svg = DexpiXmlSvgRenderer.render(dexpiFile);
    List<Finding> findings = new ArrayList<Finding>();
    Map<String, Integer> metrics = new LinkedHashMap<String, Integer>();

    Element root = document.getDocumentElement();
    if (root == null || !"PlantModel".equals(root.getTagName())) {
      add(findings, Severity.ERROR, "UNSUPPORTED_ROOT", "", "Expected a Proteus-compatible PlantModel document");
    }

    Element drawing = first(document, "Drawing");
    String drawingName = drawing == null ? "" : drawing.getAttribute("Name");
    double[] extent = drawingExtent(drawing);
    if (extent[0] <= 0.0 || extent[1] <= 0.0) {
      add(findings, Severity.ERROR, "DRAWING_EXTENT_MISSING", "", "Drawing must declare a positive Extent/Max");
    }

    String profile = profile(document);
    Map<String, Element> shapes = shapeCatalogue(document);
    metrics.put("catalogueShapes", shapes.size());
    assessIdentities(document, findings, metrics);
    assessInstances(document, shapes, svg, findings, metrics);
    assessCoordinates(document, extent, findings, metrics);
    assessText(document, findings, metrics);
    assessFlowDirectionArrows(document, svg, findings, metrics);

    metrics.put("sourcePolylines", countOutsideCatalogue(document, "PolyLine"));
    metrics.put("sourceCenterLines", countOutsideCatalogue(document, "CenterLine"));
    metrics.put("sourceCircles", countOutsideCatalogue(document, "Circle"));
    metrics.put("sourceTrimmedCurves", countOutsideCatalogue(document, "TrimmedCurve"));
    metrics.put("svgPolylines", occurrences(svg, "<polyline "));
    metrics.put("svgCircles", occurrences(svg, "<circle "));
    metrics.put("svgPaths", occurrences(svg, "<path "));
    metrics.put("svgTexts", occurrences(svg, "<text "));

    int sourceDrawable = metrics.get("sourcePolylines") + metrics.get("sourceCenterLines")
        + metrics.get("sourceCircles") + metrics.get("sourceTrimmedCurves") + metrics.get("componentInstances");
    int svgDrawable = metrics.get("svgPolylines") + metrics.get("svgCircles") + metrics.get("svgPaths")
        + metrics.get("renderedInstances");
    if (sourceDrawable > 0 && svgDrawable == 0) {
      add(findings, Severity.ERROR, "SVG_EMPTY", "",
          "Source geometry exists but the SVG contains no drawable primitives");
    }
    if (drawingName.trim().isEmpty()) {
      add(findings, Severity.WARNING, "DRAWING_NAME_MISSING", "", "Drawing has no controlled name");
    }

    Collections.sort(findings, Comparator.comparing(Finding::getSeverity).thenComparing(Finding::getCode)
        .thenComparing(Finding::getElementId));
    return new Report(profile, drawingName, extent[0], extent[1], sha256(svg), metrics, findings);
  }

  private static void assessIdentities(Document document, List<Finding> findings, Map<String, Integer> metrics) {
    Map<String, Integer> identities = new HashMap<String, Integer>();
    NodeList all = document.getElementsByTagName("*");
    int identified = 0;
    for (int index = 0; index < all.getLength(); index++) {
      Element element = (Element) all.item(index);
      if (inside(element, "ShapeCatalogue")) {
        continue;
      }
      String identity = element.getAttribute("ID").trim();
      if (identity.isEmpty()) {
        continue;
      }
      identified++;
      Integer count = identities.get(identity);
      identities.put(identity, count == null ? 1 : count + 1);
    }
    int duplicates = 0;
    for (Map.Entry<String, Integer> entry : identities.entrySet()) {
      if (entry.getValue() > 1) {
        duplicates++;
        add(findings, Severity.ERROR, "DUPLICATE_ID", entry.getKey(), "Identity occurs " + entry.getValue() + " times");
      }
    }
    metrics.put("identifiedElements", identified);
    metrics.put("duplicateIds", duplicates);
  }

  private static void assessInstances(Document document, Map<String, Element> shapes, String svg,
      List<Finding> findings, Map<String, Integer> metrics) {
    NodeList all = document.getElementsByTagName("*");
    int instances = 0;
    int rendered = 0;
    int missingReferences = 0;
    int missingPositions = 0;
    for (int index = 0; index < all.getLength(); index++) {
      Element element = (Element) all.item(index);
      if (inside(element, "ShapeCatalogue")) {
        continue;
      }
      String componentName = element.getAttribute("ComponentName").trim();
      if (componentName.isEmpty()) {
        continue;
      }
      instances++;
      String identity = element.getAttribute("ID").trim();
      if (!shapes.containsKey(componentName)) {
        missingReferences++;
        add(findings, Severity.ERROR, "SHAPE_REFERENCE_MISSING", identity,
            "ShapeCatalogue has no component named " + componentName);
      }
      if (directLocation(element) == null) {
        missingPositions++;
        add(findings, Severity.ERROR, "INSTANCE_POSITION_MISSING", identity,
            "Graphical component instance has no direct Position/Location");
      }
      if (identity.isEmpty()) {
        add(findings, Severity.WARNING, "INSTANCE_ID_MISSING", componentName,
            "Graphical component instance has no stable ID");
      } else if (svg.contains("data-dexpi-id=\"" + xml(identity) + "\"")) {
        rendered++;
      } else if (shapes.containsKey(componentName) && directLocation(element) != null) {
        add(findings, Severity.ERROR, "INSTANCE_NOT_RENDERED", identity,
            "Drawable component identity is absent from generated SVG");
      }
    }
    metrics.put("componentInstances", instances);
    metrics.put("renderedInstances", rendered);
    metrics.put("missingShapeReferences", missingReferences);
    metrics.put("missingInstancePositions", missingPositions);
  }

  private static void assessFlowDirectionArrows(Document document, String svg, List<Finding> findings,
      Map<String, Integer> metrics) {
    NodeList segments = document.getElementsByTagName("PipingNetworkSegment");
    int routedSegments = 0;
    int sourceArrows = 0;
    for (int index = 0; index < segments.getLength(); index++) {
      Element segment = (Element) segments.item(index);
      if (firstDirectChild(segment, "Connection") == null || firstDirectChild(segment, "CenterLine") == null) {
        continue;
      }
      routedSegments++;
      int segmentArrows = 0;
      NodeList children = segment.getChildNodes();
      for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
        Node child = children.item(childIndex);
        if (!(child instanceof Element)) {
          continue;
        }
        Element primitive = (Element) child;
        if ("PolyLine".equals(primitive.getTagName()) && "Solid".equalsIgnoreCase(primitive.getAttribute("Filled"))
            && primitive.getElementsByTagName("Coordinate").getLength() >= 4) {
          segmentArrows++;
          sourceArrows++;
        }
      }
      String identity = segment.getAttribute("ID").trim();
      if (segmentArrows == 0) {
        add(findings, Severity.ERROR, "FLOW_DIRECTION_ARROW_MISSING", identity,
            "Routed material segment has no solid flow-direction arrow");
      } else if (segmentArrows > 1) {
        add(findings, Severity.WARNING, "FLOW_DIRECTION_ARROW_AMBIGUOUS", identity,
            "Routed material segment has more than one solid flow-direction arrow");
      }
    }
    int renderedArrows = occurrences(svg, "data-dexpi-filled=\"solid\"");
    metrics.put("routedMaterialSegments", routedSegments);
    metrics.put("sourceFlowDirectionArrows", sourceArrows);
    metrics.put("renderedFilledFlowDirectionArrows", renderedArrows);
    if (sourceArrows > renderedArrows) {
      add(findings, Severity.ERROR, "FLOW_DIRECTION_ARROW_NOT_RENDERED", "",
          "One or more solid source flow-direction arrows are absent from generated SVG");
    }
  }

  private static void assessCoordinates(Document document, double[] extent, List<Finding> findings,
      Map<String, Integer> metrics) {
    NodeList coordinates = document.getElementsByTagName("Coordinate");
    int positioned = 0;
    int outOfBounds = 0;
    for (int index = 0; index < coordinates.getLength(); index++) {
      Element coordinate = (Element) coordinates.item(index);
      if (inside(coordinate, "ShapeCatalogue")) {
        continue;
      }
      positioned++;
      double x = attribute(coordinate, "X", Double.NaN);
      double y = attribute(coordinate, "Y", Double.NaN);
      if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0.0 || y < 0.0 || extent[0] > 0.0 && x > extent[0]
          || extent[1] > 0.0 && y > extent[1]) {
        outOfBounds++;
        Element owner = nearestIdentifiedAncestor(coordinate);
        add(findings, Severity.WARNING, "COORDINATE_OUTSIDE_DRAWING", owner == null ? "" : owner.getAttribute("ID"),
            "Coordinate lies outside the declared drawing extent");
      }
    }
    metrics.put("positionedCoordinates", positioned);
    metrics.put("outOfBoundsCoordinates", outOfBounds);
  }

  private static void assessText(Document document, List<Finding> findings, Map<String, Integer> metrics) {
    NodeList texts = document.getElementsByTagName("Text");
    int visibleTexts = 0;
    int unreadableTexts = 0;
    for (int index = 0; index < texts.getLength(); index++) {
      Element text = (Element) texts.item(index);
      if (inside(text, "ShapeCatalogue")) {
        continue;
      }
      visibleTexts++;
      String identity = identifiedAncestor(text);
      if (text.getAttribute("String").trim().isEmpty()) {
        add(findings, Severity.WARNING, "TEXT_EMPTY", identity, "Positioned Text element has no visible string");
      }
      double height = attribute(text, "Height", 2.5);
      if (height < MINIMUM_READABLE_TEXT_HEIGHT_MM) {
        unreadableTexts++;
        add(findings, Severity.WARNING, "TEXT_HEIGHT_BELOW_RENDERER_MINIMUM", identity,
            "Text height is below NeqSim's 1.8 mm renderer floor");
      }
    }
    metrics.put("sourceTexts", visibleTexts);
    metrics.put("textBelowMinimum", unreadableTexts);
  }

  private static Document parse(File file) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(file);
    } catch (ParserConfigurationException | SAXException ex) {
      throw new IOException("Could not parse DEXPI XML for visual-quality assessment", ex);
    }
  }

  private static String profile(Document document) {
    Element information = first(document, "PlantInformation");
    String version = information == null ? "unspecified" : information.getAttribute("SchemaVersion");
    if (version.trim().isEmpty()) {
      version = "unspecified";
    }
    return "Proteus-compatible DEXPI Plant/P&ID (SchemaVersion " + version + ")";
  }

  private static double[] drawingExtent(Element drawing) {
    Element extent = drawing == null ? null : firstDescendant(drawing, "Extent");
    Element max = extent == null ? null : firstDirectChild(extent, "Max");
    return max == null ? new double[] { 0.0, 0.0 }
        : new double[] { attribute(max, "X", 0.0), attribute(max, "Y", 0.0) };
  }

  private static Map<String, Element> shapeCatalogue(Document document) {
    Map<String, Element> result = new LinkedHashMap<String, Element>();
    Element catalogue = first(document, "ShapeCatalogue");
    if (catalogue == null) {
      return result;
    }
    NodeList children = catalogue.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child instanceof Element) {
        Element element = (Element) child;
        String name = element.getAttribute("ComponentName").trim();
        if (!name.isEmpty()) {
          result.put(name, element);
        }
      }
    }
    return result;
  }

  private static int countOutsideCatalogue(Document document, String tagName) {
    NodeList elements = document.getElementsByTagName(tagName);
    int count = 0;
    for (int index = 0; index < elements.getLength(); index++) {
      if (!inside((Element) elements.item(index), "ShapeCatalogue")) {
        count++;
      }
    }
    return count;
  }

  private static int occurrences(String value, String token) {
    int count = 0;
    int offset = 0;
    while ((offset = value.indexOf(token, offset)) >= 0) {
      count++;
      offset += token.length();
    }
    return count;
  }

  private static double[] directLocation(Element parent) {
    Element position = firstDirectChild(parent, "Position");
    Element location = position == null ? null : firstDirectChild(position, "Location");
    return location == null ? null : new double[] { attribute(location, "X", 0.0), attribute(location, "Y", 0.0) };
  }

  private static Element nearestIdentifiedAncestor(Element element) {
    Node current = element;
    while (current instanceof Element) {
      Element candidate = (Element) current;
      if (!candidate.getAttribute("ID").trim().isEmpty()) {
        return candidate;
      }
      current = current.getParentNode();
    }
    return null;
  }

  private static String identifiedAncestor(Element element) {
    Element ancestor = nearestIdentifiedAncestor(element);
    return ancestor == null ? "" : ancestor.getAttribute("ID");
  }

  private static boolean inside(Element element, String ancestorName) {
    Node current = element.getParentNode();
    while (current instanceof Element) {
      if (ancestorName.equals(((Element) current).getTagName())) {
        return true;
      }
      current = current.getParentNode();
    }
    return false;
  }

  private static Element first(Document document, String name) {
    NodeList elements = document.getElementsByTagName(name);
    return elements.getLength() == 0 ? null : (Element) elements.item(0);
  }

  private static Element firstDirectChild(Element parent, String name) {
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child instanceof Element && name.equals(((Element) child).getTagName())) {
        return (Element) child;
      }
    }
    return null;
  }

  private static Element firstDescendant(Element parent, String name) {
    NodeList elements = parent.getElementsByTagName(name);
    return elements.getLength() == 0 ? null : (Element) elements.item(0);
  }

  private static double attribute(Element element, String name, double fallback) {
    try {
      return Double.parseDouble(element.getAttribute(name));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static void add(List<Finding> findings, Severity severity, String code, String elementId, String message) {
    findings.add(new Finding(severity, code, elementId == null ? "" : elementId, message));
  }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte item : digest) {
        result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
    }
  }

  private static StringBuilder appendJsonField(StringBuilder json, String name, String value) {
    return json.append('"').append(json(name)).append("\":\"").append(json(value)).append('"');
  }

  private static String json(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '"' || character == '\\') {
        escaped.append('\\').append(character);
      } else if (character == '\n') {
        escaped.append("\\n");
      } else if (character == '\r') {
        escaped.append("\\r");
      } else if (character == '\t') {
        escaped.append("\\t");
      } else {
        escaped.append(character);
      }
    }
    return escaped.toString();
  }

  private static String xml(String value) {
    return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String number(double value) {
    if (Math.rint(value) == value) {
      return Long.toString((long) value);
    }
    return String.format(Locale.ROOT, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
  }
}
