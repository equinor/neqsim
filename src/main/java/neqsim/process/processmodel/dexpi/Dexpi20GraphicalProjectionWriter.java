package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import neqsim.process.engineering.model.EngineeringGraphicalProjection;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Point;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Primitive;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.PrimitiveType;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Opt-in adapter from the exchange-neutral graphical projection to DEXPI 2.0 Core primitives.
 *
 * <p>
 * The adapter emits only generic Core {@code Diagram}, {@code RepresentationGroup}, {@code Static}, {@code Polygon},
 * {@code PolyLine}, and {@code Text} objects. It never invents a DEXPI profile symbol. Every identity or graphical
 * property that cannot be mapped is recorded in the returned structured report.
 * </p>
 */
public final class Dexpi20GraphicalProjectionWriter {
  private Dexpi20GraphicalProjectionWriter() {
  }

  /**
   * Writes a schema- and NeqSim-profile-valid DEXPI Plant exchange with opt-in generic graphics.
   *
   * <p>
   * The base Plant document is written first through {@link Dexpi20XmlWriter}. The graphical extension is validated in
   * a temporary file and atomically replaces the destination only after both the bundled DEXPI XML schema and semantic
   * profile pass.
   * </p>
   *
   * @param processSystem source simulation topology
   * @param projection controlled exchange-neutral graphical projection
   * @param file destination DEXPI XML file
   * @param options controlled Plant metadata and boundary handling
   * @return deterministic structured mapping and loss report
   * @throws IOException if base export, parsing, graphical serialization, or validation fails
   */
  public static Dexpi20GraphicalProjectionReport write(ProcessSystem processSystem,
      EngineeringGraphicalProjection projection, File file, Dexpi20PlantExportOptions options) throws IOException {
    if (processSystem == null || projection == null || file == null || options == null) {
      throw new IllegalArgumentException("processSystem, projection, file and options must not be null");
    }
    Dexpi20XmlWriter.write(processSystem, file, options);

    List<Dexpi20GraphicalProjectionReport.Diagnostic> diagnostics = new ArrayList<Dexpi20GraphicalProjectionReport.Diagnostic>();
    copyProjectionDiagnostics(projection, diagnostics);
    try {
      Document document = parse(file);
      Element engineeringModel = firstObject(document, "Core/EngineeringModel");
      Element plantModel = firstObject(document, "Plant/PlantModel");
      Map<String, List<String>> representedObjects = representedObjects(document);
      Map<String, List<Primitive>> grouped = groupByExternalKey(projection.getPrimitives());
      Set<String> primitiveIds = new LinkedHashSet<String>();
      List<Primitive> emitted = new ArrayList<Primitive>();
      int emittedGroups = 0;
      int skipped = 0;

      Element diagramGroups = null;
      Element diagram = null;
      for (Map.Entry<String, List<Primitive>> item : grouped.entrySet()) {
        List<String> objectIds = representedObjects.get(item.getKey());
        if (objectIds == null || objectIds.isEmpty()) {
          skipped += item.getValue().size();
          diagnostics.add(
              diagnostic(Dexpi20GraphicalProjectionReport.Severity.ERROR, "DEXPI_GRAPHICS_UNMAPPED_REPRESENTED_OBJECT",
                  "No exported DEXPI conceptual object has this represented external key", item.getKey()));
          continue;
        }
        if (objectIds.size() != 1) {
          skipped += item.getValue().size();
          diagnostics.add(
              diagnostic(Dexpi20GraphicalProjectionReport.Severity.ERROR, "DEXPI_GRAPHICS_AMBIGUOUS_REPRESENTED_OBJECT",
                  "More than one exported DEXPI conceptual object has this represented external key", item.getKey()));
          continue;
        }
        if (diagram == null) {
          diagram = appendDiagram(document, engineeringModel, plantModel, projection);
          diagramGroups = components(document, diagram, "Groups");
        }
        Element group = object(document, "RepresentationGroup_" + digest(item.getKey()),
            "Core/Diagram.RepresentationGroup");
        Element groups = components(document, group, "Groups");
        Element staticGroup = object(document, null, "Core/Diagram.Static");
        Element elements = components(document, staticGroup, "Elements");
        groups.appendChild(staticGroup);
        for (Primitive primitive : item.getValue()) {
          String objectId = "GraphicalPrimitive_" + digest(primitive.getId());
          if (!primitiveIds.add(objectId)) {
            skipped++;
            diagnostics.add(
                diagnostic(Dexpi20GraphicalProjectionReport.Severity.ERROR, "DEXPI_GRAPHICS_PRIMITIVE_ID_COLLISION",
                    "A stable DEXPI primitive identifier collided after normalization", primitive.getId()));
            continue;
          }
          elements.appendChild(primitive(document, primitive, objectId, diagnostics));
          emitted.add(primitive);
        }
        if (!elements.hasChildNodes()) {
          continue;
        }
        references(document, group, "Represents", objectIds.get(0));
        diagramGroups.appendChild(group);
        emittedGroups++;
      }

      if (diagram != null) {
        appendBounds(document, diagram, emitted);
      } else {
        diagnostics.add(diagnostic(Dexpi20GraphicalProjectionReport.Severity.ERROR, "DEXPI_GRAPHICS_EMPTY_DIAGRAM",
            "No projection primitive could be attached to an exported DEXPI conceptual object",
            projection.getProjectId()));
      }
      skipped = projection.getPrimitives().size() - emitted.size();
      Dexpi20GraphicalProjectionReport report = new Dexpi20GraphicalProjectionReport(
          projection.getSourceGraphFingerprint(), projection.getSourceReference(), projection.getRevision(),
          emittedGroups, emitted.size(), skipped, diagnostics);
      validateAndReplace(document, file);
      return report;
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not configure DEXPI graphical-projection parsing", ex);
    } catch (SAXException ex) {
      throw new IOException("Could not parse generated DEXPI Plant exchange", ex);
    } catch (TransformerException ex) {
      throw new IOException("Could not serialize DEXPI graphical projection", ex);
    }
  }

  private static void copyProjectionDiagnostics(EngineeringGraphicalProjection projection,
      List<Dexpi20GraphicalProjectionReport.Diagnostic> diagnostics) {
    for (EngineeringGraphicalProjection.Diagnostic source : projection.getDiagnostics()) {
      Dexpi20GraphicalProjectionReport.Severity severity = Dexpi20GraphicalProjectionReport.Severity
          .valueOf(source.getSeverity().name());
      diagnostics.add(diagnostic(severity, "SOURCE_" + source.getCode(), source.getMessage(), source.getSubjectId()));
    }
  }

  private static Document parse(File file) throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(file);
  }

  private static Map<String, List<String>> representedObjects(Document document) {
    Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      String id = object.getAttribute("id");
      if (id.isEmpty()) {
        continue;
      }
      register(result, directStringData(object, "TagName"), id);
      register(result, directStringData(object, "PipeConnectorNumber"), id);
      register(result, directStringData(object, "LineNumber"), id);
    }
    return result;
  }

  private static void register(Map<String, List<String>> values, String key, String id) {
    if (key == null || key.trim().isEmpty()) {
      return;
    }
    List<String> ids = values.get(key.trim());
    if (ids == null) {
      ids = new ArrayList<String>();
      values.put(key.trim(), ids);
    }
    if (!ids.contains(id)) {
      ids.add(id);
      Collections.sort(ids);
    }
  }

  private static Map<String, List<Primitive>> groupByExternalKey(List<Primitive> primitives) {
    Map<String, List<Primitive>> result = new TreeMap<String, List<Primitive>>();
    for (Primitive primitive : primitives) {
      List<Primitive> values = result.get(primitive.getRepresentedExternalKey());
      if (values == null) {
        values = new ArrayList<Primitive>();
        result.put(primitive.getRepresentedExternalKey(), values);
      }
      values.add(primitive);
    }
    return result;
  }

  private static Element appendDiagram(Document document, Element engineeringModel, Element plantModel,
      EngineeringGraphicalProjection projection) {
    Element diagrams = components(document, engineeringModel, "Diagram");
    Element diagram = object(document, "Diagram_" + digest(projection.getProjectId() + ":" + projection.getRevision()),
        "Core/Diagram.Diagram");
    colorData(document, diagram, "BackgroundColor", "#ffffff");
    data(document, diagram, "Name",
        projection.getProjectId() + " " + projection.getRevision() + " " + projection.getSourceReference());
    references(document, diagram, "Represents", plantModel.getAttribute("id"));
    diagrams.appendChild(diagram);
    return diagram;
  }

  private static Element primitive(Document document, Primitive source, String id,
      List<Dexpi20GraphicalProjectionReport.Diagnostic> diagnostics) {
    if (source.getType() == PrimitiveType.TEXT) {
      return text(document, source, id, diagnostics);
    }
    if (source.getType() == PrimitiveType.POLYLINE) {
      return polyline(document, source, id, diagnostics);
    }
    return polygon(document, source, id, diagnostics);
  }

  private static Element polyline(Document document, Primitive source, String id,
      List<Dexpi20GraphicalProjectionReport.Diagnostic> diagnostics) {
    Element result = object(document, id, "Core/Diagram.PolyLine");
    pointsData(document, result, source.getPoints());
    strokeData(document, result, source, diagnostics);
    return result;
  }

  private static Element polygon(Document document, Primitive source, String id,
      List<Dexpi20GraphicalProjectionReport.Diagnostic> diagnostics) {
    Element result = object(document, id, "Core/Diagram.Polygon");
    String fillStyle = "none".equals(source.getFillColor()) ? "Transparent" : "Solid";
    dataReference(document, result, "FillStyle", "Core/Diagram.FillStyle." + fillStyle);
    if (!"none".equals(source.getFillColor())) {
      diagnostics
          .add(diagnostic(Dexpi20GraphicalProjectionReport.Severity.WARNING, "DEXPI_GRAPHICS_FILL_COLOR_NOT_PRESERVED",
              "DEXPI Core Polygon preserves solid/transparent fill state but not this projection fill color",
              source.getId()));
    }
    List<Point> points = source.getPoints();
    if (source.getType() == PrimitiveType.RECTANGLE) {
      points = Arrays.asList(new Point(source.getX(), source.getY()),
          new Point(source.getX() + source.getWidth(), source.getY()),
          new Point(source.getX() + source.getWidth(), source.getY() + source.getHeight()),
          new Point(source.getX(), source.getY() + source.getHeight()));
    }
    pointsData(document, result, points);
    strokeData(document, result, source, diagnostics);
    return result;
  }

  private static Element text(Document document, Primitive source, String id,
      List<Dexpi20GraphicalProjectionReport.Diagnostic> diagnostics) {
    Element result = object(document, id, "Core/Diagram.Text");
    String alignment = "middle".equals(source.getTextAnchor()) ? "CenterCenter"
        : "end".equals(source.getTextAnchor()) ? "RightCenter" : "LeftCenter";
    dataReference(document, result, "Alignment", "Core/Diagram.TextAlignment." + alignment);
    colorData(document, result, "Color", usableColor(source.getFillColor(), source.getId(), diagnostics));
    data(document, result, "Font", "Arial");
    pointData(document, result, "Position", new Point(source.getX(), source.getY()));
    doubleData(document, result, "Rotation", 0.0);
    doubleData(document, result, "Size", source.getSize());
    data(document, result, "Text", source.getText());
    return result;
  }

  private static void strokeData(Document document, Element parent, Primitive source,
      List<Dexpi20GraphicalProjectionReport.Diagnostic> diagnostics) {
    Element property = document.createElement("Data");
    property.setAttribute("property", "Stroke");
    Element stroke = document.createElement("AggregatedDataValue");
    stroke.setAttribute("type", "Core/Diagram.Stroke");
    colorData(document, stroke, "Color", usableColor(source.getStrokeColor(), source.getId(), diagnostics));
    String dash = source.getDashPattern().isEmpty() ? "Solid" : "Dash";
    dataReference(document, stroke, "DashStyle", "Core/Diagram.DashStyle." + dash);
    if (!source.getDashPattern().isEmpty()) {
      diagnostics
          .add(diagnostic(Dexpi20GraphicalProjectionReport.Severity.WARNING, "DEXPI_GRAPHICS_DASH_PATTERN_APPROXIMATED",
              "Numeric projection dash pattern was mapped to the generic DEXPI Dash enumeration", source.getId()));
    }
    doubleData(document, stroke, "Width", source.getStrokeWidth());
    property.appendChild(stroke);
    parent.appendChild(property);
  }

  private static String usableColor(String color, String subjectId,
      List<Dexpi20GraphicalProjectionReport.Diagnostic> diagnostics) {
    if (!"none".equals(color)) {
      return color;
    }
    diagnostics.add(diagnostic(Dexpi20GraphicalProjectionReport.Severity.WARNING, "DEXPI_GRAPHICS_COLOR_FALLBACK",
        "DEXPI Core requires a color for this primitive; black was used for projection color none", subjectId));
    return "#000000";
  }

  private static void appendBounds(Document document, Element diagram, List<Primitive> primitives) {
    Bounds bounds = new Bounds();
    for (Primitive primitive : primitives) {
      if (primitive.getType() == PrimitiveType.RECTANGLE) {
        bounds.include(primitive.getX(), primitive.getY());
        bounds.include(primitive.getX() + primitive.getWidth(), primitive.getY() + primitive.getHeight());
      } else if (primitive.getType() == PrimitiveType.TEXT) {
        bounds.include(primitive.getX(), primitive.getY());
      } else {
        for (Point point : primitive.getPoints()) {
          bounds.include(point.getX(), point.getY());
        }
      }
    }
    doubleData(document, diagram, "MaxX", bounds.maxX + 10.0);
    doubleData(document, diagram, "MaxY", bounds.maxY + 10.0);
    doubleData(document, diagram, "MinX", bounds.minX - 10.0);
    doubleData(document, diagram, "MinY", bounds.minY - 10.0);
  }

  private static void pointsData(Document document, Element parent, List<Point> points) {
    Element property = document.createElement("Data");
    property.setAttribute("property", "Points");
    for (Point point : points) {
      property.appendChild(pointValue(document, point));
    }
    parent.appendChild(property);
  }

  private static void pointData(Document document, Element parent, String propertyName, Point point) {
    Element property = document.createElement("Data");
    property.setAttribute("property", propertyName);
    property.appendChild(pointValue(document, point));
    parent.appendChild(property);
  }

  private static Element pointValue(Document document, Point point) {
    Element value = document.createElement("AggregatedDataValue");
    value.setAttribute("type", "Core/Diagram.Point");
    doubleData(document, value, "X", point.getX());
    doubleData(document, value, "Y", point.getY());
    return value;
  }

  private static void colorData(Document document, Element parent, String propertyName, String color) {
    Element property = document.createElement("Data");
    property.setAttribute("property", propertyName);
    Element value = document.createElement("AggregatedDataValue");
    value.setAttribute("type", "Core/Diagram.Color");
    int rgb = parseColorOrBlack(color);
    integerData(document, value, "B", rgb & 255);
    integerData(document, value, "G", (rgb >> 8) & 255);
    integerData(document, value, "R", (rgb >> 16) & 255);
    property.appendChild(value);
    parent.appendChild(property);
  }

  static int parseColorOrBlack(String color) {
    if (color == null || color.length() != 7 || color.charAt(0) != '#') {
      return 0;
    }
    try {
      return Integer.parseInt(color.substring(1), 16);
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private static void data(Document document, Element parent, String propertyName, String value) {
    Element property = document.createElement("Data");
    property.setAttribute("property", propertyName);
    Element string = document.createElement("String");
    string.setTextContent(value);
    property.appendChild(string);
    parent.appendChild(property);
  }

  private static void doubleData(Document document, Element parent, String propertyName, double value) {
    Element property = document.createElement("Data");
    property.setAttribute("property", propertyName);
    Element number = document.createElement("Double");
    number.setTextContent(Double.toString(value == 0.0 ? 0.0 : value));
    property.appendChild(number);
    parent.appendChild(property);
  }

  private static void integerData(Document document, Element parent, String propertyName, int value) {
    Element property = document.createElement("Data");
    property.setAttribute("property", propertyName);
    Element number = document.createElement("Integer");
    number.setTextContent(Integer.toString(value));
    property.appendChild(number);
    parent.appendChild(property);
  }

  private static void dataReference(Document document, Element parent, String propertyName, String reference) {
    Element property = document.createElement("Data");
    property.setAttribute("property", propertyName);
    Element value = document.createElement("DataReference");
    value.setAttribute("data", reference);
    property.appendChild(value);
    parent.appendChild(property);
  }

  private static Element object(Document document, String id, String type) {
    Element result = document.createElement("Object");
    if (id != null) {
      result.setAttribute("id", id);
    }
    result.setAttribute("type", type);
    return result;
  }

  private static Element components(Document document, Element parent, String propertyName) {
    Element result = document.createElement("Components");
    result.setAttribute("property", propertyName);
    parent.appendChild(result);
    return result;
  }

  private static void references(Document document, Element parent, String propertyName, String id) {
    Element result = document.createElement("References");
    result.setAttribute("property", propertyName);
    result.setAttribute("objects", "#" + id);
    parent.appendChild(result);
  }

  private static Element firstObject(Document document, String type) {
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (type.equals(object.getAttribute("type"))) {
        return object;
      }
    }
    throw new IllegalArgumentException("Generated DEXPI exchange has no " + type);
  }

  private static String directStringData(Element object, String propertyName) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!(child instanceof Element) || !"Data".equals(((Element) child).getTagName())
          || !propertyName.equals(((Element) child).getAttribute("property"))) {
        continue;
      }
      NodeList strings = ((Element) child).getElementsByTagName("String");
      return strings.getLength() == 0 ? null : strings.item(0).getTextContent().trim();
    }
    return null;
  }

  private static String digest(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (int index = 0; index < 8; index++) {
        result.append(String.format(java.util.Locale.ROOT, "%02x", Integer.valueOf(bytes[index] & 255)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by every supported Java runtime", ex);
    }
  }

  private static Dexpi20GraphicalProjectionReport.Diagnostic diagnostic(
      Dexpi20GraphicalProjectionReport.Severity severity, String code, String message, String subjectId) {
    return new Dexpi20GraphicalProjectionReport.Diagnostic(severity, code, message, subjectId);
  }

  private static void validateAndReplace(Document document, File destination) throws IOException, TransformerException {
    Path parent = destination.toPath().toAbsolutePath().getParent();
    Path temporary = Files.createTempFile(parent, "neqsim-dexpi-graphics-", ".xml");
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.transform(new DOMSource(document), new StreamResult(temporary.toFile()));
      try {
        Dexpi20XmlValidator.validate(temporary);
        Dexpi20SemanticValidator.validateOrThrow(temporary);
      } catch (SAXException ex) {
        throw new IOException("Generated DEXPI graphical projection failed schema validation", ex);
      }
      Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static final class Bounds {
    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    private void include(double x, double y) {
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x);
      maxY = Math.max(maxY, y);
    }
  }
}
