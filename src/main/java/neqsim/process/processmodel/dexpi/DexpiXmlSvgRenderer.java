package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
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
 * Renders the graphical content of a Proteus-compatible DEXPI Plant/P&amp;ID document as SVG.
 *
 * <p>
 * The renderer consumes the geometry already present in the DEXPI exchange. Component instances are resolved against
 * the document's {@code ShapeCatalogue}; process, signal and utility lines, labels, drawing furniture and ISO 10628
 * symbol geometry are therefore rendered from one source document rather than reconstructed from the simulation
 * topology.
 * </p>
 */
public final class DexpiXmlSvgRenderer {
  private DexpiXmlSvgRenderer() {
  }

  /**
   * Renders a DEXPI Plant/P&amp;ID XML file to an SVG string.
   *
   * @param dexpiFile Proteus-compatible DEXPI XML document
   * @return complete UTF-8 SVG document
   * @throws IOException if the document cannot be parsed or rendered
   */
  public static String render(File dexpiFile) throws IOException {
    if (dexpiFile == null) {
      throw new IllegalArgumentException("dexpiFile must not be null");
    }
    Document document = parse(dexpiFile);
    double[] extent = drawingExtent(document);
    double width = extent[0];
    double height = extent[1];
    Map<String, Element> shapes = shapeCatalogue(document);

    StringBuilder svg = new StringBuilder(256 * 1024);
    svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(number(width)).append("mm\" height=\"")
        .append(number(height)).append("mm\" viewBox=\"0 0 ").append(number(width)).append(' ').append(number(height))
        .append("\">\n");
    svg.append("  <title>").append(xml(drawingName(document))).append("</title>\n");
    svg.append("  <desc>Rendered by NeqSim from DEXPI Plant/P&amp;ID graphical content.</desc>\n");
    svg.append("  <rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
    svg.append("  <g id=\"dexpi-lines\" fill=\"none\" stroke-linejoin=\"round\" ")
        .append("stroke-linecap=\"round\">\n");
    renderGlobalCurves(document, height, svg);
    svg.append("  </g>\n");
    svg.append("  <g id=\"dexpi-symbols\" fill=\"none\" stroke-linejoin=\"round\" ")
        .append("stroke-linecap=\"round\">\n");
    renderComponentInstances(document, shapes, height, svg);
    svg.append("  </g>\n");
    svg.append("  <g id=\"dexpi-text\" font-family=\"Arial,Helvetica,sans-serif\" ").append("font-weight=\"400\">\n");
    renderText(document, height, svg);
    svg.append("  </g>\n");
    svg.append("</svg>\n");
    return svg.toString();
  }

  /**
   * Renders a DEXPI Plant/P&amp;ID XML file to an SVG file.
   *
   * @param dexpiFile Proteus-compatible DEXPI XML document
   * @param svgFile destination SVG file
   * @throws IOException if the document cannot be parsed or the SVG cannot be written
   */
  public static void render(File dexpiFile, File svgFile) throws IOException {
    if (svgFile == null) {
      throw new IllegalArgumentException("svgFile must not be null");
    }
    File parent = svgFile.getAbsoluteFile().getParentFile();
    if (parent != null) {
      Files.createDirectories(parent.toPath());
    }
    Files.write(svgFile.toPath(), render(dexpiFile).getBytes(StandardCharsets.UTF_8));
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
      throw new IOException("Could not parse DEXPI XML", ex);
    }
  }

  private static double[] drawingExtent(Document document) {
    NodeList drawings = document.getElementsByTagName("Drawing");
    if (drawings.getLength() > 0) {
      Element extent = firstDescendant((Element) drawings.item(0), "Extent");
      Element max = extent == null ? null : firstDirectChild(extent, "Max");
      if (max != null) {
        double width = attribute(max, "X", 0.0);
        double height = attribute(max, "Y", 0.0);
        if (width > 0.0 && height > 0.0) {
          return new double[] { width, height };
        }
      }
    }
    double maxX = 0.0;
    double maxY = 0.0;
    NodeList coordinates = document.getElementsByTagName("Coordinate");
    for (int index = 0; index < coordinates.getLength(); index++) {
      Element coordinate = (Element) coordinates.item(index);
      if (!inside(coordinate, "ShapeCatalogue")) {
        maxX = Math.max(maxX, attribute(coordinate, "X", 0.0));
        maxY = Math.max(maxY, attribute(coordinate, "Y", 0.0));
      }
    }
    return new double[] { Math.max(297.0, maxX + 10.0), Math.max(210.0, maxY + 10.0) };
  }

  private static String drawingName(Document document) {
    NodeList drawings = document.getElementsByTagName("Drawing");
    if (drawings.getLength() == 0) {
      return "NeqSim DEXPI Plant P&ID";
    }
    String name = ((Element) drawings.item(0)).getAttribute("Name");
    return name == null || name.trim().isEmpty() ? "NeqSim DEXPI Plant P&ID" : name;
  }

  private static Map<String, Element> shapeCatalogue(Document document) {
    Map<String, Element> result = new LinkedHashMap<String, Element>();
    NodeList catalogues = document.getElementsByTagName("ShapeCatalogue");
    if (catalogues.getLength() == 0) {
      return result;
    }
    Element catalogue = (Element) catalogues.item(0);
    NodeList children = catalogue.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child instanceof Element) {
        Element shape = (Element) child;
        String name = shape.getAttribute("ComponentName");
        if (name != null && !name.trim().isEmpty()) {
          result.put(name, shape);
        }
      }
    }
    return result;
  }

  private static void renderGlobalCurves(Document document, double sheetHeight, StringBuilder svg) {
    renderGlobalTag(document, "CenterLine", sheetHeight, svg);
    renderGlobalTag(document, "PolyLine", sheetHeight, svg);
    NodeList circles = document.getElementsByTagName("Circle");
    for (int index = 0; index < circles.getLength(); index++) {
      Element circle = (Element) circles.item(index);
      if (inside(circle, "ShapeCatalogue") || circle.getParentNode() instanceof Element
          && "TrimmedCurve".equals(((Element) circle.getParentNode()).getTagName())) {
        continue;
      }
      double[] location = location(circle);
      if (location == null) {
        continue;
      }
      svg.append("    <circle cx=\"").append(number(location[0])).append("\" cy=\"")
          .append(number(sheetHeight - location[1])).append("\" r=\"").append(number(attribute(circle, "Radius", 0.0)))
          .append("\" ").append(style(circle)).append("/>").append('\n');
    }
    NodeList arcs = document.getElementsByTagName("TrimmedCurve");
    for (int index = 0; index < arcs.getLength(); index++) {
      Element curve = (Element) arcs.item(index);
      if (inside(curve, "ShapeCatalogue")) {
        continue;
      }
      Element circle = firstDirectChild(curve, "Circle");
      if (circle != null) {
        appendGlobalArc(curve, circle, sheetHeight, svg);
      }
    }
  }

  private static void renderGlobalTag(Document document, String tagName, double sheetHeight, StringBuilder svg) {
    NodeList curves = document.getElementsByTagName(tagName);
    for (int index = 0; index < curves.getLength(); index++) {
      Element curve = (Element) curves.item(index);
      if (inside(curve, "ShapeCatalogue")) {
        continue;
      }
      NodeList coordinates = curve.getElementsByTagName("Coordinate");
      if (coordinates.getLength() < 2) {
        continue;
      }
      svg.append("    <polyline points=\"");
      for (int point = 0; point < coordinates.getLength(); point++) {
        Element coordinate = (Element) coordinates.item(point);
        if (point > 0) {
          svg.append(' ');
        }
        svg.append(number(attribute(coordinate, "X", 0.0))).append(',')
            .append(number(sheetHeight - attribute(coordinate, "Y", 0.0)));
      }
      svg.append("\" ").append(styleWithFill(curve)).append("/>").append('\n');
    }
  }

  private static void renderComponentInstances(Document document, Map<String, Element> shapes, double sheetHeight,
      StringBuilder svg) {
    NodeList all = document.getElementsByTagName("*");
    for (int index = 0; index < all.getLength(); index++) {
      Element instance = (Element) all.item(index);
      if (inside(instance, "ShapeCatalogue")) {
        continue;
      }
      String shapeName = instance.getAttribute("ComponentName");
      Element shape = shapes.get(shapeName);
      double[] position = directLocation(instance);
      if (shape == null || position == null) {
        continue;
      }
      double[] scale = directScale(instance);
      String identity = instance.getAttribute("ID");
      svg.append("    <g transform=\"translate(").append(number(position[0])).append(' ')
          .append(number(sheetHeight - position[1])).append(") scale(").append(number(scale[0])).append(' ')
          .append(number(-scale[1])).append(")\"");
      if (identity != null && !identity.isEmpty()) {
        svg.append(" data-dexpi-id=\"").append(xml(identity)).append("\"");
      }
      svg.append(">\n");
      renderShape(shape, svg);
      svg.append("    </g>\n");
    }
  }

  private static void renderShape(Element shape, StringBuilder svg) {
    NodeList children = shape.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (!(child instanceof Element)) {
        continue;
      }
      Element primitive = (Element) child;
      if ("PolyLine".equals(primitive.getTagName())) {
        NodeList coordinates = primitive.getElementsByTagName("Coordinate");
        if (coordinates.getLength() < 2) {
          continue;
        }
        svg.append("      <polyline points=\"");
        for (int point = 0; point < coordinates.getLength(); point++) {
          Element coordinate = (Element) coordinates.item(point);
          if (point > 0) {
            svg.append(' ');
          }
          svg.append(number(attribute(coordinate, "X", 0.0))).append(',')
              .append(number(attribute(coordinate, "Y", 0.0)));
        }
        svg.append("\" ").append(styleWithFill(primitive)).append("/>").append('\n');
      } else if ("Circle".equals(primitive.getTagName())) {
        appendLocalCircle(primitive, primitive, svg);
      } else if ("TrimmedCurve".equals(primitive.getTagName())) {
        Element circle = firstDirectChild(primitive, "Circle");
        if (circle != null) {
          appendLocalArc(primitive, circle, svg);
        }
      }
    }
  }

  private static void appendLocalCircle(Element circle, Element styleSource, StringBuilder svg) {
    double[] location = location(circle);
    if (location == null) {
      location = new double[] { 0.0, 0.0 };
    }
    boolean filled = "Solid".equalsIgnoreCase(circle.getAttribute("Filled"));
    String circleStyle = style(styleSource);
    if (filled) {
      circleStyle = circleStyle.replace(" fill=\"none\"", "");
    }
    svg.append("      <circle cx=\"").append(number(location[0])).append("\" cy=\"").append(number(location[1]))
        .append("\" r=\"").append(number(attribute(circle, "Radius", 0.0))).append("\" ").append(circleStyle);
    if (filled) {
      svg.append(" fill=\"").append(color(circle)).append("\"");
    }
    svg.append("/>\n");
  }

  private static void appendLocalArc(Element curve, Element circle, StringBuilder svg) {
    double[] center = location(circle);
    if (center == null) {
      center = new double[] { 0.0, 0.0 };
    }
    double radius = attribute(circle, "Radius", 0.0);
    double start = Math.toRadians(attribute(curve, "StartAngle", 0.0));
    double end = Math.toRadians(attribute(curve, "EndAngle", 0.0));
    double startX = center[0] + radius * Math.cos(start);
    double startY = center[1] + radius * Math.sin(start);
    double endX = center[0] + radius * Math.cos(end);
    double endY = center[1] + radius * Math.sin(end);
    double delta = (Math.toDegrees(end - start) + 360.0) % 360.0;
    int largeArc = delta > 180.0 ? 1 : 0;
    svg.append("      <path d=\"M ").append(number(startX)).append(' ').append(number(startY)).append(" A ")
        .append(number(radius)).append(' ').append(number(radius)).append(" 0 ").append(largeArc).append(" 1 ")
        .append(number(endX)).append(' ').append(number(endY)).append("\" ").append(styleWithFill(curve)).append("/>\n");
  }

  private static void appendGlobalArc(Element curve, Element circle, double sheetHeight, StringBuilder svg) {
    double[] center = location(circle);
    if (center == null) {
      center = new double[] { 0.0, 0.0 };
    }
    double radius = attribute(circle, "Radius", 0.0);
    double start = Math.toRadians(attribute(curve, "StartAngle", 0.0));
    double end = Math.toRadians(attribute(curve, "EndAngle", 0.0));
    double startX = center[0] + radius * Math.cos(start);
    double startY = sheetHeight - center[1] - radius * Math.sin(start);
    double endX = center[0] + radius * Math.cos(end);
    double endY = sheetHeight - center[1] - radius * Math.sin(end);
    double delta = (Math.toDegrees(end - start) + 360.0) % 360.0;
    int largeArc = delta > 180.0 ? 1 : 0;
    svg.append("    <path d=\"M ").append(number(startX)).append(' ').append(number(startY)).append(" A ")
        .append(number(radius)).append(' ').append(number(radius)).append(" 0 ").append(largeArc).append(" 0 ")
        .append(number(endX)).append(' ').append(number(endY)).append("\" ").append(styleWithFill(curve)).append("/>\n");
  }

  private static void renderText(Document document, double sheetHeight, StringBuilder svg) {
    NodeList texts = document.getElementsByTagName("Text");
    for (int index = 0; index < texts.getLength(); index++) {
      Element text = (Element) texts.item(index);
      if (inside(text, "ShapeCatalogue")) {
        continue;
      }
      double[] position = location(text);
      if (position == null) {
        continue;
      }
      String value = text.getAttribute("String");
      double size = Math.max(1.8, attribute(text, "Height", 2.5));
      String justification = text.getAttribute("Justification");
      String anchor = justification.startsWith("Left") ? "start" : justification.startsWith("Right") ? "end" : "middle";
      String baseline = justification.endsWith("Bottom") ? "auto"
          : justification.endsWith("Top") ? "hanging" : "middle";
      svg.append("    <text x=\"").append(number(position[0])).append("\" y=\"")
          .append(number(sheetHeight - position[1])).append("\" font-size=\"").append(number(size))
          .append("\" text-anchor=\"").append(anchor).append("\" dominant-baseline=\"").append(baseline)
          .append("\" fill=\"").append(color(text)).append("\">").append(xml(value)).append("</text>\n");
    }
  }

  private static String styleWithFill(Element element) {
    String renderedStyle = style(element);
    if (!"Solid".equalsIgnoreCase(element.getAttribute("Filled"))) {
      return renderedStyle;
    }
    return renderedStyle.replace(" fill=\"none\"", "") + " fill=\"" + color(element)
        + "\" data-dexpi-filled=\"solid\"";
  }

  private static String style(Element element) {
    Element presentation = firstDirectChild(element, "Presentation");
    String stroke = color(presentation == null ? element : presentation);
    double weight = presentation == null ? 0.3 : attribute(presentation, "LineWeight", 0.3);
    StringBuilder result = new StringBuilder();
    result.append("stroke=\"").append(stroke).append("\" stroke-width=\"").append(number(Math.max(0.18, weight)))
        .append("\" fill=\"none\"");
    int lineType = presentation == null ? 0 : (int) attribute(presentation, "LineType", 0.0);
    if (lineType == 1) {
      result.append(" stroke-dasharray=\"4 2\"");
    } else if (lineType == 2) {
      result.append(" stroke-dasharray=\"1 1.8\"");
    } else if (lineType == 3) {
      result.append(" stroke-dasharray=\"6 2 1 2\"");
    }
    return result.toString();
  }

  private static String color(Element element) {
    Element presentation = element == null ? null : firstDirectChild(element, "Presentation");
    Element source = presentation == null ? element : presentation;
    if (source == null) {
      return "#000000";
    }
    int red = channel(attribute(source, "R", 0.0));
    int green = channel(attribute(source, "G", 0.0));
    int blue = channel(attribute(source, "B", 0.0));
    return String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue);
  }

  private static int channel(double value) {
    return (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, value)));
  }

  private static double[] directLocation(Element parent) {
    Element position = firstDirectChild(parent, "Position");
    Element location = position == null ? null : firstDirectChild(position, "Location");
    return location == null ? null : new double[] { attribute(location, "X", 0.0), attribute(location, "Y", 0.0) };
  }

  private static double[] location(Element parent) {
    Element position = firstDescendant(parent, "Position");
    Element location = position == null ? null : firstDescendant(position, "Location");
    return location == null ? null : new double[] { attribute(location, "X", 0.0), attribute(location, "Y", 0.0) };
  }

  private static double[] directScale(Element parent) {
    Element scale = firstDirectChild(parent, "Scale");
    return scale == null ? new double[] { 1.0, 1.0 }
        : new double[] { attribute(scale, "X", 1.0), attribute(scale, "Y", 1.0) };
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
    NodeList items = parent.getElementsByTagName(name);
    return items.getLength() == 0 ? null : (Element) items.item(0);
  }

  private static boolean inside(Node node, String ancestorName) {
    Node parent = node.getParentNode();
    while (parent != null) {
      if (parent instanceof Element && ancestorName.equals(((Element) parent).getTagName())) {
        return true;
      }
      parent = parent.getParentNode();
    }
    return false;
  }

  private static double attribute(Element element, String name, double fallback) {
    if (element == null) {
      return fallback;
    }
    try {
      String value = element.getAttribute(name);
      return value == null || value.trim().isEmpty() ? fallback : Double.parseDouble(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static String number(double value) {
    if (Math.abs(value - Math.rint(value)) < 1.0e-9) {
      return Long.toString(Math.round(value));
    }
    String formatted = String.format(Locale.ROOT, "%.6f", value);
    int end = formatted.length();
    while (end > 0 && formatted.charAt(end - 1) == '0') {
      end--;
    }
    return formatted.substring(0, end);
  }

  private static String xml(String value) {
    return value == null ? ""
        : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
            "&apos;");
  }
}
