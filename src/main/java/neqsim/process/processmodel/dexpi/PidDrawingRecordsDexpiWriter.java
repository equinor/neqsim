package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Writes a Proteus-profile DEXPI P&amp;ID from drawing records extracted from a P&amp;ID sheet.
 *
 * <p>
 * The input is a {@code neqsim_pid_drawing_records.v1} JSON document: equipment and off-page connector nodes with
 * nozzles (ports), pipe-junction (tee) nodes, traced pipe runs (edges) with their drawn centre lines, inline valves and
 * instrument bubbles. Such records are produced by a drawing extractor (for example a vector-PDF reader) or by hand,
 * and are a draft that must be reviewed against the controlled drawing.
 * </p>
 *
 * <p>
 * The written file keeps the drawing geometry (converted from PDF points with y down to millimetres with y up), so
 * {@link DexpiXmlSvgRenderer} regenerates a P&amp;ID that follows the source sheet, and it is accepted by
 * {@link DexpiXmlReader} and {@link DexpiSimulationBuilder}. Nozzles carry the {@code PortRole} attribute so
 * multi-outlet equipment is wired to the right outlet. Pipe runs that carry no flow in normal operation (drains, flare,
 * chemicals, sampling) are written with {@code NormalFlow=false} and are left out of the steady-state flowsheet.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class PidDrawingRecordsDexpiWriter {
  private static final Logger logger = LogManager.getLogger(PidDrawingRecordsDexpiWriter.class);

  /** Schema version of the accepted drawing records. */
  public static final String SCHEMA_VERSION = "neqsim_pid_drawing_records.v1";

  /** Generic attribute that marks a pipe run without flow in normal operation. */
  public static final String NORMAL_FLOW = DexpiFlowsheetGraph.NORMAL_FLOW;

  private static final String NAMESPACE = "http://sandbox.dexpi.org/xml";
  private static final double PT_TO_MM = 25.4 / 72.0;
  private static final String PROCESS_RGB = "0";
  private static final Set<String> INLINE_KINDS = new HashSet<String>(
      java.util.Arrays.asList("valve", "flange", "reducer", "blind"));

  private PidDrawingRecordsDexpiWriter() {
  }

  /**
   * Summary of one conversion.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static final class Result {
    private final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    private final List<String> warnings = new ArrayList<String>();
    private String drawingNumber = "";

    /**
     * Returns element counts by category.
     *
     * @return unmodifiable counts
     */
    public Map<String, Integer> getCounts() {
      return Collections.unmodifiableMap(counts);
    }

    /**
     * Returns conversion warnings.
     *
     * @return unmodifiable warnings
     */
    public List<String> getWarnings() {
      return Collections.unmodifiableList(warnings);
    }

    /**
     * Returns the source drawing number.
     *
     * @return drawing number or empty string
     */
    public String getDrawingNumber() {
      return drawingNumber;
    }

    /**
     * Returns the summary as JSON.
     *
     * @return JSON text
     */
    public String toJson() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("drawingNumber", drawingNumber);
      map.put("counts", counts);
      map.put("warnings", warnings);
      return new GsonBuilder().setPrettyPrinting().create().toJson(map);
    }

    /**
     * Increments a count.
     *
     * @param key count name
     */
    private void count(String key) {
      Integer value = counts.get(key);
      counts.put(key, value == null ? 1 : value + 1);
    }
  }

  /**
   * Converts a drawing-records JSON file to a DEXPI file.
   *
   * @param recordsFile {@code neqsim_pid_drawing_records.v1} JSON file
   * @param dexpiFile output DEXPI XML file
   * @return conversion summary
   * @throws IOException if reading, parsing or writing fails
   */
  public static Result write(File recordsFile, File dexpiFile) throws IOException {
    try (Reader reader = new InputStreamReader(Files.newInputStream(recordsFile.toPath()), StandardCharsets.UTF_8)) {
      return write(JsonParser.parseReader(reader).getAsJsonObject(), dexpiFile);
    }
  }

  /**
   * Converts drawing-records JSON text to a DEXPI file.
   *
   * @param recordsJson {@code neqsim_pid_drawing_records.v1} JSON text
   * @param dexpiFile output DEXPI XML file
   * @return conversion summary
   * @throws IOException if parsing or writing fails
   */
  public static Result write(String recordsJson, File dexpiFile) throws IOException {
    if (recordsJson == null) {
      throw new IllegalArgumentException("Drawing records JSON must not be null");
    }
    return write(JsonParser.parseString(recordsJson).getAsJsonObject(), dexpiFile);
  }

  /**
   * Converts parsed drawing records to a DEXPI file.
   *
   * @param records parsed records
   * @param dexpiFile output DEXPI XML file
   * @return conversion summary
   * @throws IOException if writing fails
   */
  private static Result write(JsonObject records, File dexpiFile) throws IOException {
    Result result = new Result();
    Document document = toDocument(records, result);
    try {
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      File parent = dexpiFile.getAbsoluteFile().getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        throw new IOException("Could not create " + parent);
      }
      transformer.transform(new DOMSource(document), new StreamResult(dexpiFile));
    } catch (TransformerException exception) {
      throw new IOException("Could not write DEXPI file " + dexpiFile, exception);
    }
    logger.info("Wrote DEXPI {} from drawing records: {}", dexpiFile.getName(), result.getCounts());
    return result;
  }

  /**
   * Builds the DEXPI document from drawing records.
   *
   * @param records parsed records
   * @param result summary to fill
   * @return DEXPI document
   * @throws IOException if the records are not supported
   */
  static Document toDocument(JsonObject records, Result result) throws IOException {
    if (!records.has("schemaVersion") || !SCHEMA_VERSION.equals(records.get("schemaVersion").getAsString())) {
      throw new IOException("Unsupported drawing records; expected schemaVersion " + SCHEMA_VERSION);
    }
    Document document;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      document = factory.newDocumentBuilder().newDocument();
    } catch (ParserConfigurationException exception) {
      throw new IOException("Could not create XML document", exception);
    }
    JsonObject source = records.has("source") ? records.getAsJsonObject("source") : new JsonObject();
    double pageWidth = number(source, "pageWidth", 2384.0);
    double pageHeight = number(source, "pageHeight", 1684.0);
    result.drawingNumber = text(source, "drawingNumber");
    Sheet sheet = new Sheet(pageHeight);

    Element root = document.createElementNS(NAMESPACE, "PlantModel");
    root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    document.appendChild(root);
    appendPlantInformation(document, root);
    Element drawing = child(document, root, "Drawing");
    drawing.setAttribute("Name", result.drawingNumber.isEmpty() ? "P&ID from drawing records" : result.drawingNumber);
    drawing.setAttribute("Type", "PID");
    Element extent = child(document, drawing, "Extent");
    point(document, extent, "Min", 0.0, 0.0);
    point(document, extent, "Max", pageWidth * PT_TO_MM, pageHeight * PT_TO_MM);
    Element drawingAttributes = genericSet(document, drawing, "NeqSimDrawingRecords");
    attribute(document, drawingAttributes, "SchemaVersion", SCHEMA_VERSION);
    attribute(document, drawingAttributes, "SourceFile", text(source, "file"));
    attribute(document, drawingAttributes, "SourceSha256", text(source, "sha256"));
    attribute(document, drawingAttributes, "SourcePage", text(source, "page"));
    attribute(document, drawingAttributes, "ReviewStatus",
        records.has("reviewStatus") ? records.get("reviewStatus").getAsString() : "REVIEW_REQUIRED");
    JsonObject review = records.has("review") && records.get("review").isJsonObject()
        ? records.getAsJsonObject("review")
        : new JsonObject();
    attribute(document, drawingAttributes, "ReviewedBy", text(review, "reviewer"));
    attribute(document, drawingAttributes, "ReviewDate", text(review, "date"));
    attribute(document, drawingAttributes, "ReviewCorrections", text(review, "correctionCount"));
    JsonArray sheets = array(records, "sheets");
    for (int i = 0; i < sheets.size(); i++) {
      JsonObject sheetInfo = sheets.get(i).getAsJsonObject();
      attribute(document, drawingAttributes, "Sheet" + (i + 1), text(sheetInfo, "drawingNumber"));
    }

    Map<String, JsonObject> nodes = new LinkedHashMap<String, JsonObject>();
    for (JsonElement item : array(records, "nodes")) {
      JsonObject node = item.getAsJsonObject();
      nodes.put(node.get("node_id").getAsString(), node);
    }
    Set<String> referenced = new HashSet<String>();
    for (JsonElement item : array(records, "edges")) {
      JsonObject edge = item.getAsJsonObject();
      referenced.add(text(edge, "from_node"));
      referenced.add(text(edge, "to_node"));
    }
    for (JsonObject node : nodes.values()) {
      String kind = text(node, "kind");
      if ("off_page_connector".equals(kind) || "tee".equals(kind)) {
        if ("off_page_connector".equals(kind) && !referenced.contains(text(node, "node_id"))) {
          appendConnectorGraphics(document, root, node, sheet);
          result.count("unconnectedConnectors");
        }
        continue;
      }
      appendEquipment(document, root, node, sheet);
      result.count("equipment");
    }

    Map<String, Element> placed = new HashMap<String, Element>();
    int index = 0;
    for (JsonElement item : array(records, "edges")) {
      JsonObject edge = item.getAsJsonObject();
      index++;
      String from = endpointId(edge, "from", nodes, result);
      String to = endpointId(edge, "to", nodes, result);
      if (from == null || to == null) {
        continue;
      }
      appendPipeRun(document, root, edge, index, nodes, placed, from, to, sheet, result);
    }
    for (JsonElement item : array(records, "instruments")) {
      appendInstrument(document, root, item.getAsJsonObject(), sheet);
      result.count("instruments");
    }
    for (JsonElement warning : array(records, "warnings")) {
      result.warnings.add(warning.getAsString());
    }
    return document;
  }

  /**
   * Resolves the DEXPI identifier an edge end connects to.
   *
   * @param edge edge record
   * @param side "from" or "to"
   * @param nodes node records by id
   * @param result summary for warnings
   * @return nozzle or item identifier, or null when the node is unknown
   */
  private static String endpointId(JsonObject edge, String side, Map<String, JsonObject> nodes, Result result) {
    String nodeId = text(edge, side + "_node");
    JsonObject node = nodes.get(nodeId);
    if (node == null) {
      result.warnings.add("Edge " + text(edge, "edge_id") + " references unknown node " + nodeId);
      return null;
    }
    String kind = text(node, "kind");
    if ("off_page_connector".equals(kind) || "tee".equals(kind)) {
      return safeId(nodeId);
    }
    String port = text(edge, side + "_port");
    return safeId(port.isEmpty() ? nodeId : port);
  }

  /**
   * Appends the PlantInformation header.
   *
   * @param document target document
   * @param root plant model element
   */
  private static void appendPlantInformation(Document document, Element root) {
    Element info = child(document, root, "PlantInformation");
    Date now = new Date();
    info.setAttribute("Application", "NeqSim");
    info.setAttribute("ApplicationVersion", "1.0");
    info.setAttribute("Date", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(now));
    info.setAttribute("Time", new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(now));
    info.setAttribute("Discipline", "Process");
    info.setAttribute("Is3D", "no");
    info.setAttribute("OriginatingSystem", "NeqSim drawing records");
    info.setAttribute("OriginatingSystemVendor", "NeqSim");
    info.setAttribute("OriginatingSystemVersion", "1.0");
    info.setAttribute("SchemaVersion", "4.1.1");
    info.setAttribute("Units", "mm");
    child(document, info, "UnitsOfMeasure");
  }

  /**
   * Appends an equipment item with its outline, tag and nozzles.
   *
   * @param document target document
   * @param root plant model element
   * @param node equipment record
   * @param sheet coordinate conversion
   */
  private static void appendEquipment(Document document, Element root, JsonObject node, Sheet sheet) {
    double[] box = bbox(node);
    String componentClass = text(node, "component_class").isEmpty() ? "Vessel" : text(node, "component_class");
    Element equipment = child(document, root, "Equipment");
    equipment.setAttribute("ID", safeId(text(node, "node_id")));
    equipment.setAttribute("ComponentClass", componentClass);
    equipment.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/" + componentClass);
    position(document, equipment, sheet.x((box[0] + box[2]) / 2.0), sheet.y((box[1] + box[3]) / 2.0));
    polyline(document, equipment, sheet,
        new double[][] {{box[0], box[1]}, {box[2], box[1]}, {box[2], box[3]}, {box[0], box[3]}, {box[0], box[1]}}, 0.5,
        false);
    text(document, equipment, text(node, "tag"), sheet.x((box[0] + box[2]) / 2.0), sheet.y((box[1] + box[3]) / 2.0),
        5.0, "CenterCenter");
    for (JsonElement item : array(node, "ports")) {
      JsonObject port = item.getAsJsonObject();
      Element nozzle = child(document, equipment, "Nozzle");
      nozzle.setAttribute("ID", safeId(text(port, "port_id")));
      nozzle.setAttribute("ComponentClass", "Nozzle");
      nozzle.setAttribute("ComponentClassURI", "http://data.posccaesar.org/rdl/RDS415214");
      double x = number(port, "x", box[0]);
      double y = number(port, "y", box[1]);
      position(document, nozzle, sheet.x(x), sheet.y(y));
      circle(document, nozzle, sheet.x(x), sheet.y(y), 0.8);
      if (!text(port, "label").isEmpty()) {
        text(document, nozzle, text(port, "label"), sheet.x(x) + 1.5, sheet.y(y) + 1.5, 2.2, "LeftBottom");
      }
      Element portAttributes = genericSet(document, nozzle, "NeqSimPortAttributes");
      if (!text(port, "role").isEmpty()) {
        attribute(document, portAttributes, DexpiFlowsheetGraph.PORT_ROLE, text(port, "role"));
      }
      attribute(document, portAttributes, "SubTagName", text(port, "label"));
    }
    Element attributes = genericSet(document, equipment, "DexpiAttributes");
    attribute(document, attributes, DexpiMetadata.TAG_NAME, text(node, "tag"));
    JsonObject data = node.has("attributes") && node.get("attributes").isJsonObject()
        ? node.getAsJsonObject("attributes")
        : new JsonObject();
    if (data.has("operatingPressure") && data.get("operatingPressure").isJsonObject()) {
      JsonObject pressure = data.getAsJsonObject("operatingPressure");
      attribute(document, attributes, DexpiMetadata.OPERATING_PRESSURE_VALUE, text(pressure, "value"),
          text(pressure, "unit"));
    }
    if (data.has("operatingTemperature") && data.get("operatingTemperature").isJsonObject()) {
      JsonObject temperature = data.getAsJsonObject("operatingTemperature");
      attribute(document, attributes, DexpiMetadata.OPERATING_TEMPERATURE_VALUE, text(temperature, "value"),
          text(temperature, "unit"));
    }
    attribute(document, attributes, DexpiMetadata.INSIDE_DIAMETER, text(data, "insideDiameter_mm"), "mm");
    attribute(document, attributes, DexpiMetadata.TANGENT_TO_TANGENT_LENGTH, text(data, "tanTanLength_mm"), "mm");
    attribute(document, attributes, DexpiMetadata.DESIGN_PRESSURE, text(data, "designPressure"));
    attribute(document, attributes, DexpiMetadata.DESIGN_TEMPERATURE, text(data, "designTemperature"));
    attribute(document, attributes, DexpiMetadata.CONSTRUCTION_MATERIAL, text(data, "material"));
    Element provenance = genericSet(document, equipment, "NeqSimDrawingRecords");
    attribute(document, provenance, "RecordKind", text(node, "kind"));
    attribute(document, provenance, "Service", text(data, "service"));
    attribute(document, provenance, "ExtractionConfidence", text(node, "confidence"));
  }

  /**
   * Appends one traced pipe run as a piping network system with one segment.
   *
   * @param document target document
   * @param root plant model element
   * @param edge edge record
   * @param index running edge number
   * @param nodes node records by id
   * @param placed connector and tee elements already written, by node id
   * @param from DEXPI id the run starts at
   * @param to DEXPI id the run ends at
   * @param sheet coordinate conversion
   * @param result summary to update
   */
  private static void appendPipeRun(Document document, Element root, JsonObject edge, int index,
      Map<String, JsonObject> nodes, Map<String, Element> placed, String from, String to, Sheet sheet, Result result) {
    String edgeId = safeId(text(edge, "edge_id").isEmpty() ? "E-" + index : text(edge, "edge_id"));
    boolean normalFlow = !edge.has("normal_flow") || edge.get("normal_flow").getAsBoolean();
    Element system = child(document, root, "PipingNetworkSystem");
    system.setAttribute("ID", "PNS-" + edgeId);
    system.setAttribute("ComponentClass", "PipingNetworkSystem");
    Element systemAttributes = genericSet(document, system, "DexpiAttributes");
    attribute(document, systemAttributes, DexpiMetadata.LINE_NUMBER, text(edge, "line_number"));
    attribute(document, systemAttributes, DexpiMetadata.FLUID_CODE, text(edge, "fluid_code"));

    Element segment = child(document, system, "PipingNetworkSegment");
    segment.setAttribute("ID", "Segment-" + edgeId);
    segment.setAttribute("ComponentClass", "PipingNetworkSegment");
    double[][] path = path(edge);
    if (path.length >= 2) {
      Element centerLine = polyline(document, segment, sheet, path, normalFlow ? 0.7 : 0.35, !normalFlow);
      document.renameNode(centerLine, NAMESPACE, "CenterLine");
    }
    if (!text(edge, "line_number").isEmpty() && path.length >= 2) {
      double[] mid = longestLegMidpoint(path);
      text(document, segment, text(edge, "line_number"), sheet.x(mid[0]), sheet.y(mid[1]) + 1.0, 2.2, "CenterBottom");
    }
    Element attributes = genericSet(document, segment, "DexpiAttributes");
    attribute(document, attributes, DexpiMetadata.LINE_NUMBER, text(edge, "line_number"));
    attribute(document, attributes, DexpiMetadata.FLUID_CODE, text(edge, "fluid_code"));
    attribute(document, attributes, NORMAL_FLOW, normalFlow ? "true" : "false");
    Element provenance = genericSet(document, segment, "NeqSimDrawingRecords");
    attribute(document, provenance, "EdgeId", text(edge, "edge_id"));
    attribute(document, provenance, "ExtractionConfidence", text(edge, "confidence"));
    attribute(document, provenance, "GapCount", text(edge, "gap_count"));

    for (String nodeId : new String[] {text(edge, "from_node"), text(edge, "to_node")}) {
      JsonObject node = nodes.get(nodeId);
      String kind = text(node, "kind");
      if (placed.containsKey(nodeId)) {
        continue;
      }
      if ("off_page_connector".equals(kind)) {
        placed.put(nodeId, appendConnector(document, segment, node, sheet));
        result.count("connectors");
      } else if ("tee".equals(kind)) {
        placed.put(nodeId, appendTee(document, segment, node, sheet));
        result.count("tees");
      }
    }

    String previous = from;
    int itemIndex = 0;
    for (JsonElement item : array(edge, "inline_items")) {
      JsonObject inline = item.getAsJsonObject();
      String kind = text(inline, "kind");
      if (!INLINE_KINDS.contains(kind)) {
        continue;
      }
      itemIndex++;
      String itemId = edgeId + "-" + kind.substring(0, 1).toUpperCase(Locale.ROOT) + itemIndex;
      appendInlineItem(document, segment, inline, itemId, sheet);
      connection(document, segment, previous, itemId + "-N1");
      previous = itemId + "-N2";
      result.count("valve".equals(kind) ? "valves" : kind + "s");
      if ("closed".equalsIgnoreCase(text(inline, "normal_position"))) {
        result.count("normallyClosedItems");
      }
    }
    connection(document, segment, previous, to);
    result.count(normalFlow ? "pipeRuns" : "noFlowPipeRuns");
  }

  /**
   * Appends an off-page connector element with its pentagon outline and reference text.
   *
   * @param document target document
   * @param parent piping network segment
   * @param node connector record
   * @param sheet coordinate conversion
   * @return the connector element
   */
  private static Element appendConnector(Document document, Element parent, JsonObject node, Sheet sheet) {
    double[] box = bbox(node);
    String componentClass = text(node, "component_class").isEmpty() ? "PipeOffPageConnector"
        : text(node, "component_class");
    Element connector = child(document, parent, "PipeOffPageConnector");
    connector.setAttribute("ID", safeId(text(node, "node_id")));
    connector.setAttribute("ComponentClass", componentClass);
    connector.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/" + componentClass);
    position(document, connector, sheet.x((box[0] + box[2]) / 2.0), sheet.y((box[1] + box[3]) / 2.0));
    Element reference = child(document, connector, "ConnectorReference");
    reference.setAttribute("DrawingName", text(node, "tag"));
    appendConnectorShape(document, connector, node, sheet);
    Element attributes = genericSet(document, connector, "NeqSimDrawingRecords");
    attribute(document, attributes, "ContinuationSheet", text(node, "tag"));
    attribute(document, attributes, "Label", text(node, "label"));
    attribute(document, attributes, "Direction", text(node, "direction"));
    attribute(document, attributes, "Service", text(node, "service"));
    return connector;
  }

  /**
   * Draws an unconnected connector (for example an instrument-signal continuation) without a model element.
   *
   * @param document target document
   * @param root plant model element
   * @param node connector record
   * @param sheet coordinate conversion
   */
  private static void appendConnectorGraphics(Document document, Element root, JsonObject node, Sheet sheet) {
    appendConnectorShape(document, root, node, sheet);
  }

  /**
   * Draws the connector box, its sheet reference and its label.
   *
   * @param document target document
   * @param parent element receiving the graphics
   * @param node connector record
   * @param sheet coordinate conversion
   */
  private static void appendConnectorShape(Document document, Element parent, JsonObject node, Sheet sheet) {
    double[] box = bbox(node);
    double mid = (box[1] + box[3]) / 2.0;
    double tip = "box".equals(text(node, "shape")) ? 0.0 : Math.min(8.0, (box[2] - box[0]) / 4.0);
    double[][] outline = new double[][] {{box[0], box[1]}, {box[2] - tip, box[1]}, {box[2], mid},
        {box[2] - tip, box[3]}, {box[0], box[3]}, {box[0], box[1]}};
    polyline(document, parent, sheet, outline, 0.35, "signal".equals(text(node, "service")));
    text(document, parent, text(node, "tag"), sheet.x((box[0] + box[2]) / 2.0), sheet.y(mid), 2.4, "CenterCenter");
    if (!text(node, "label").isEmpty()) {
      text(document, parent, text(node, "label"), sheet.x(box[0]), sheet.y(box[1]) + 1.0, 2.2, "LeftBottom");
    }
  }

  /**
   * Appends a pipe tee.
   *
   * @param document target document
   * @param parent piping network segment
   * @param node tee record
   * @param sheet coordinate conversion
   * @return the tee element
   */
  private static Element appendTee(Document document, Element parent, JsonObject node, Sheet sheet) {
    double[] box = bbox(node);
    double x = (box[0] + box[2]) / 2.0;
    double y = (box[1] + box[3]) / 2.0;
    Element tee = child(document, parent, "PipingComponent");
    tee.setAttribute("ID", safeId(text(node, "node_id")));
    tee.setAttribute("ComponentClass", "PipeTee");
    tee.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/PipeTee");
    position(document, tee, sheet.x(x), sheet.y(y));
    Element dot = circle(document, tee, sheet.x(x), sheet.y(y), 0.6);
    dot.setAttribute("Filled", "Solid");
    return tee;
  }

  /**
   * Appends an inline piping item (valve, flange, reducer or blind) with two nozzles and its symbol.
   *
   * <p>
   * Valves and blinds carry {@code NormalPosition} ({@code Open} or {@code Closed}) and, when read from the drawing,
   * {@code LockingStatus} (LO, LC, CSO, CSC). A normally closed valve is drawn filled.
   * </p>
   *
   * @param document target document
   * @param parent piping network segment
   * @param inline inline item record
   * @param itemId DEXPI id of the item
   * @param sheet coordinate conversion
   */
  private static void appendInlineItem(Document document, Element parent, JsonObject inline, String itemId,
      Sheet sheet) {
    String kind = text(inline, "kind");
    String componentClass = text(inline, "component_class");
    if (componentClass.isEmpty() || "GenericValve".equals(componentClass)) {
      componentClass = "valve".equals(kind) ? "CustomValve"
          : "flange".equals(kind) ? "Flange" : "reducer".equals(kind) ? "ConcentricPipeReducer" : "SpectacleBlind";
    }
    String position = text(inline, "normal_position");
    boolean closed = "closed".equalsIgnoreCase(position);
    double x = number(inline, "x", 0.0);
    double y = number(inline, "y", 0.0);
    boolean horizontal = !"vertical".equals(text(inline, "orientation"));
    double half = Math.max(3.0, Math.min(8.0, number(inline, "gap", 10.0) / 2.0));
    Element item = child(document, parent, "PipingComponent");
    item.setAttribute("ID", itemId);
    item.setAttribute("ComponentClass", componentClass);
    item.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/" + componentClass);
    position(document, item, sheet.x(x), sheet.y(y));
    for (double[][] shape : symbol(kind, x, y, half, horizontal)) {
      Element line = polyline(document, item, sheet, shape, 0.35, false);
      if ("valve".equals(kind) && closed) {
        line.setAttribute("Filled", "Solid");
      }
    }
    if ("blind".equals(kind)) {
      Element ring = circle(document, item, sheet.x(x), sheet.y(y), half * PT_TO_MM * 0.6);
      if (closed) {
        ring.setAttribute("Filled", "Solid");
      }
    }
    for (int k = 1; k <= 2; k++) {
      double sign = k == 1 ? -1.0 : 1.0;
      Element nozzle = child(document, item, "Nozzle");
      nozzle.setAttribute("ID", itemId + "-N" + k);
      nozzle.setAttribute("ComponentClass", "Nozzle");
      position(document, nozzle, sheet.x(horizontal ? x + sign * half : x), sheet.y(horizontal ? y : y + sign * half));
    }
    String tag = text(inline, "tag");
    String lock = text(inline, "locking");
    String caption = tag + (lock.isEmpty() ? "" : (tag.isEmpty() ? "" : " ") + lock);
    if (!caption.isEmpty()) {
      text(document, item, caption, sheet.x(x), sheet.y(y) + 3.0, 2.2, "CenterBottom");
    }
    Element attributes = genericSet(document, item, "DexpiAttributes");
    attribute(document, attributes, DexpiMetadata.TAG_NAME, tag);
    attribute(document, attributes, "PipingComponentNumberAssignmentClass", tag.isEmpty() ? itemId : tag);
    attribute(document, attributes, DexpiFlowsheetGraph.NORMAL_POSITION,
        position.isEmpty() ? "" : closed ? "Closed" : "Open");
    attribute(document, attributes, "LockingStatus", lock);
    Element provenance = genericSet(document, item, "NeqSimDrawingRecords");
    attribute(document, provenance, "ExtractionConfidence", text(inline, "confidence"));
    attribute(document, provenance, "NormalPositionSource", text(inline, "position_source"));
  }

  /**
   * Returns the polylines of an inline item symbol in source drawing units.
   *
   * @param kind valve, flange, reducer or blind
   * @param x centre x
   * @param y centre y
   * @param half half the symbol length along the pipe
   * @param horizontal true when the pipe runs horizontally
   * @return polylines
   */
  private static List<double[][]> symbol(String kind, double x, double y, double half, boolean horizontal) {
    List<double[][]> shapes = new ArrayList<double[][]>();
    double w = half * 0.6;
    if ("flange".equals(kind)) {
      double g = Math.min(1.5, half / 3.0);
      shapes.add(rotate(new double[][] {{-g, -w}, {-g, w}}, x, y, horizontal));
      shapes.add(rotate(new double[][] {{g, -w}, {g, w}}, x, y, horizontal));
    } else if ("reducer".equals(kind)) {
      shapes.add(rotate(new double[][] {{-half, -w}, {half, -w * 0.5}, {half, w * 0.5}, {-half, w}, {-half, -w}}, x, y,
          horizontal));
    } else if ("blind".equals(kind)) {
      shapes.add(rotate(new double[][] {{0.0, -w * 1.8}, {0.0, -w}}, x, y, horizontal));
    } else {
      shapes
          .add(rotate(new double[][] {{-half, -w}, {half, w}, {half, -w}, {-half, w}, {-half, -w}}, x, y, horizontal));
    }
    return shapes;
  }

  /**
   * Places a symbol defined along +x at (x, y), turned 90 degrees for vertical pipes.
   *
   * @param local symbol points with the pipe along +x
   * @param x centre x
   * @param y centre y
   * @param horizontal true when the pipe runs horizontally
   * @return placed points
   */
  private static double[][] rotate(double[][] local, double x, double y, boolean horizontal) {
    double[][] placed = new double[local.length][];
    for (int i = 0; i < local.length; i++) {
      placed[i] = horizontal ? new double[] {x + local[i][0], y + local[i][1]}
          : new double[] {x + local[i][1], y + local[i][0]};
    }
    return placed;
  }

  /**
   * Appends an instrument bubble as a process instrumentation function.
   *
   * @param document target document
   * @param root plant model element
   * @param instrument instrument record
   * @param sheet coordinate conversion
   */
  private static void appendInstrument(Document document, Element root, JsonObject instrument, Sheet sheet) {
    double x = number(instrument, "x", 0.0);
    double y = number(instrument, "y", 0.0);
    Element function = child(document, root, "ProcessInstrumentationFunction");
    function.setAttribute("ID", safeId(text(instrument, "node_id")));
    function.setAttribute("ComponentClass", "ProcessInstrumentationFunction");
    function.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunction");
    position(document, function, sheet.x(x), sheet.y(y));
    circle(document, function, sheet.x(x), sheet.y(y), 3.5);
    text(document, function, text(instrument, "function"), sheet.x(x), sheet.y(y) + 0.6, 2.0, "CenterBottom");
    text(document, function, text(instrument, "number"), sheet.x(x), sheet.y(y) - 0.6, 2.0, "CenterTop");
    Element attributes = genericSet(document, function, "DexpiAttributes");
    attribute(document, attributes, DexpiMetadata.TAG_NAME, text(instrument, "tag"));
    attribute(document, attributes, DexpiMetadata.INSTRUMENTATION_FUNCTIONS, text(instrument, "function"));
    attribute(document, attributes, DexpiMetadata.INSTRUMENTATION_NUMBER, text(instrument, "number"));
  }

  /**
   * Appends a material connection.
   *
   * @param document target document
   * @param segment piping network segment
   * @param from source id
   * @param to target id
   */
  private static void connection(Document document, Element segment, String from, String to) {
    Element connection = child(document, segment, "Connection");
    connection.setAttribute("FromID", from);
    connection.setAttribute("ToID", to);
  }

  /**
   * Appends a polyline in sheet coordinates.
   *
   * @param document target document
   * @param parent receiving element
   * @param sheet coordinate conversion
   * @param points points in source drawing units
   * @param weight line weight in mm
   * @param dashed true for a dashed line
   * @return the polyline element
   */
  private static Element polyline(Document document, Element parent, Sheet sheet, double[][] points, double weight,
      boolean dashed) {
    Element line = child(document, parent, "PolyLine");
    line.setAttribute("NumPoints", String.valueOf(points.length));
    Element presentation = child(document, line, "Presentation");
    presentation.setAttribute("LineType", dashed ? "2" : "0");
    presentation.setAttribute("LineWeight", format(weight));
    presentation.setAttribute("R", PROCESS_RGB);
    presentation.setAttribute("G", PROCESS_RGB);
    presentation.setAttribute("B", PROCESS_RGB);
    for (double[] point : points) {
      Element coordinate = child(document, line, "Coordinate");
      coordinate.setAttribute("X", format(sheet.x(point[0])));
      coordinate.setAttribute("Y", format(sheet.y(point[1])));
    }
    return line;
  }

  /**
   * Appends a circle in sheet coordinates.
   *
   * @param document target document
   * @param parent receiving element
   * @param x centre x in mm
   * @param y centre y in mm
   * @param radius radius in mm
   * @return the circle element
   */
  private static Element circle(Document document, Element parent, double x, double y, double radius) {
    Element circle = child(document, parent, "Circle");
    circle.setAttribute("Radius", format(radius));
    Element presentation = child(document, circle, "Presentation");
    presentation.setAttribute("LineType", "0");
    presentation.setAttribute("LineWeight", "0.25");
    presentation.setAttribute("R", PROCESS_RGB);
    presentation.setAttribute("G", PROCESS_RGB);
    presentation.setAttribute("B", PROCESS_RGB);
    position(document, circle, x, y);
    return circle;
  }

  /**
   * Appends a text element in sheet coordinates.
   *
   * @param document target document
   * @param parent receiving element
   * @param value text
   * @param x anchor x in mm
   * @param y anchor y in mm
   * @param height text height in mm
   * @param justification DEXPI justification keyword
   */
  private static void text(Document document, Element parent, String value, double x, double y, double height,
      String justification) {
    if (value == null || value.isEmpty()) {
      return;
    }
    Element text = child(document, parent, "Text");
    text.setAttribute("String", value);
    text.setAttribute("Font", "Arial");
    text.setAttribute("Height", format(height));
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", justification);
    Element presentation = child(document, text, "Presentation");
    presentation.setAttribute("R", PROCESS_RGB);
    presentation.setAttribute("G", PROCESS_RGB);
    presentation.setAttribute("B", PROCESS_RGB);
    position(document, text, x, y);
  }

  /**
   * Appends a DEXPI position.
   *
   * @param document target document
   * @param parent receiving element
   * @param x x in mm
   * @param y y in mm
   */
  private static void position(Document document, Element parent, double x, double y) {
    Element position = child(document, parent, "Position");
    Element location = child(document, position, "Location");
    location.setAttribute("X", format(x));
    location.setAttribute("Y", format(y));
    location.setAttribute("Z", "0");
    Element axis = child(document, position, "Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    Element reference = child(document, position, "Reference");
    reference.setAttribute("X", "1");
    reference.setAttribute("Y", "0");
    reference.setAttribute("Z", "0");
  }

  /**
   * Appends an extent corner point.
   *
   * @param document target document
   * @param parent extent element
   * @param name Min or Max
   * @param x x in mm
   * @param y y in mm
   */
  private static void point(Document document, Element parent, String name, double x, double y) {
    Element point = child(document, parent, name);
    point.setAttribute("X", format(x));
    point.setAttribute("Y", format(y));
    point.setAttribute("Z", "0");
  }

  /**
   * Appends a generic attribute set.
   *
   * @param document target document
   * @param parent receiving element
   * @param set set name
   * @return the set element
   */
  private static Element genericSet(Document document, Element parent, String set) {
    Element attributes = child(document, parent, "GenericAttributes");
    attributes.setAttribute("Set", set);
    return attributes;
  }

  /**
   * Appends a generic string attribute when the value is not empty.
   *
   * @param document target document
   * @param set attribute set
   * @param name attribute name
   * @param value attribute value
   */
  private static void attribute(Document document, Element set, String name, String value) {
    if (value == null || value.isEmpty()) {
      return;
    }
    Element attribute = child(document, set, "GenericAttribute");
    attribute.setAttribute("Name", name);
    attribute.setAttribute("Value", value);
    attribute.setAttribute("Format", "string");
  }

  /**
   * Appends a generic attribute with a unit when the value is not empty.
   *
   * @param document target document
   * @param set attribute set
   * @param name attribute name
   * @param value attribute value
   * @param unit unit text, or empty
   */
  private static void attribute(Document document, Element set, String name, String value, String unit) {
    if (value == null || value.isEmpty()) {
      return;
    }
    Element attribute = child(document, set, "GenericAttribute");
    attribute.setAttribute("Name", name);
    attribute.setAttribute("Value", value);
    attribute.setAttribute("Format", "double");
    if (unit != null && !unit.isEmpty()) {
      attribute.setAttribute("Unit", unit);
    }
  }

  /**
   * Creates a namespaced child element.
   *
   * @param document target document
   * @param parent parent element
   * @param name local name
   * @return the child
   */
  private static Element child(Document document, Element parent, String name) {
    Element element = document.createElementNS(NAMESPACE, name);
    parent.appendChild(element);
    return element;
  }

  /**
   * Returns the midpoint of the longest leg of a path, used to place the line number.
   *
   * @param path path points
   * @return midpoint
   */
  private static double[] longestLegMidpoint(double[][] path) {
    double best = -1.0;
    double[] mid = path[0];
    for (int i = 1; i < path.length; i++) {
      double length = Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]);
      if (length > best) {
        best = length;
        mid = new double[] {(path[i][0] + path[i - 1][0]) / 2.0, (path[i][1] + path[i - 1][1]) / 2.0};
      }
    }
    return mid;
  }

  /**
   * Reads an edge path.
   *
   * @param edge edge record
   * @return points in source units
   */
  private static double[][] path(JsonObject edge) {
    JsonArray points = array(edge, "path");
    double[][] result = new double[points.size()][];
    for (int i = 0; i < points.size(); i++) {
      JsonArray point = points.get(i).getAsJsonArray();
      result[i] = new double[] {point.get(0).getAsDouble(), point.get(1).getAsDouble()};
    }
    return result;
  }

  /**
   * Reads a node bounding box.
   *
   * @param node node record
   * @return x0, y0, x1, y1 in source units
   */
  private static double[] bbox(JsonObject node) {
    JsonArray box = array(node, "bbox");
    if (box.size() != 4) {
      return new double[] {0.0, 0.0, 1.0, 1.0};
    }
    return new double[] {box.get(0).getAsDouble(), box.get(1).getAsDouble(), box.get(2).getAsDouble(),
        box.get(3).getAsDouble()};
  }

  /**
   * Reads an array member, or an empty array.
   *
   * @param object JSON object
   * @param name member name
   * @return array
   */
  private static JsonArray array(JsonObject object, String name) {
    return object != null && object.has(name) && object.get(name).isJsonArray() ? object.getAsJsonArray(name)
        : new JsonArray();
  }

  /**
   * Reads a member as text, or an empty string.
   *
   * @param object JSON object
   * @param name member name
   * @return text value
   */
  private static String text(JsonObject object, String name) {
    if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
      return "";
    }
    JsonElement value = object.get(name);
    return value.isJsonPrimitive() ? value.getAsString() : value.toString();
  }

  /**
   * Reads a numeric member.
   *
   * @param object JSON object
   * @param name member name
   * @param fallback value when absent
   * @return number
   */
  private static double number(JsonObject object, String name, double fallback) {
    return object != null && object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsDouble()
        : fallback;
  }

  /**
   * Makes a value usable as an XML ID.
   *
   * @param value raw identifier
   * @return identifier with unsupported characters replaced
   */
  private static String safeId(String value) {
    String id = value.replaceAll("[^A-Za-z0-9_.-]", "-");
    return id.isEmpty() || !Character.isLetter(id.charAt(0)) && id.charAt(0) != '_' ? "ID-" + id : id;
  }

  /**
   * Formats a number for XML.
   *
   * @param value number
   * @return text with at most three decimals
   */
  private static String format(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  /**
   * Converts source drawing coordinates (points, y down) to DEXPI sheet coordinates (mm, y up).
   *
   * @author NeqSim
   * @version 1.0
   */
  private static final class Sheet {
    private final double pageHeight;

    /**
     * Creates the conversion.
     *
     * @param pageHeight source page height in points
     */
    Sheet(double pageHeight) {
      this.pageHeight = pageHeight;
    }

    /**
     * Converts x.
     *
     * @param x source x in points
     * @return x in mm
     */
    double x(double x) {
      return x * PT_TO_MM;
    }

    /**
     * Converts y.
     *
     * @param y source y in points (down)
     * @return y in mm (up)
     */
    double y(double y) {
      return (pageHeight - y) * PT_TO_MM;
    }
  }
}
