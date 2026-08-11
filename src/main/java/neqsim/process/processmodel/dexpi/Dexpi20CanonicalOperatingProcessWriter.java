package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Opt-in DEXPI 2.0 Process export that sources selected operating values from the canonical
 * engineering-diagram snapshot.
 *
 * <p>The established {@link Dexpi20ProcessModelWriter} APIs remain unchanged. This writer first
 * creates the canonical operating-case snapshot, emits the established assessed material topology,
 * removes direct simulation-stream quantities from the XML, and then writes only values that are
 * present as calculated canonical nodes. This prevents stale or unsuccessful-run values from being
 * exported while preserving the current DEXPI topology and compatibility path.</p>
 */
public final class Dexpi20CanonicalOperatingProcessWriter {
  private Dexpi20CanonicalOperatingProcessWriter() {
  }

  /**
   * Writes and assesses a DEXPI 2.0 Process exchange with canonical operating values.
   *
   * @param processSystem source process system
   * @param file destination DEXPI XML file
   * @param plantId persistent plant/project identifier
   * @param revision controlled source-model revision
   * @param operatingCaseId stable operating-case identifier
   * @return schema/profile, supported topology, and canonical adaptation evidence
   * @throws IOException if writing, canonical projection, or validation fails
   */
  public static Dexpi20ProcessTopologyAssessment.Report writeAndAssessTopology(ProcessSystem processSystem, File file,
      String plantId, String revision, String operatingCaseId) throws IOException {
    ProcessDiagramGraphAdapter.Result canonical = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision, operatingCaseId);
    return writeAndAssessTopology(processSystem, file, canonical);
  }

  static Dexpi20ProcessTopologyAssessment.Report writeAndAssessTopology(ProcessSystem processSystem, File file,
      ProcessDiagramGraphAdapter.Result canonical) throws IOException {
    if (processSystem == null || file == null || canonical == null) {
      throw new IllegalArgumentException("processSystem, file, and canonical must not be null");
    }

    Dexpi20ProcessModelWriter.writeAndAssessTopology(processSystem, file, canonicalPlantId(canonical),
        canonicalRevision(canonical));
    replaceStreamQuantitiesWithCanonicalValues(file.toPath(), canonical);
    validate(file.toPath());

    Dexpi20ConformanceAssessment.Report conformance = Dexpi20ConformanceAssessment.assess(file.toPath(),
        Dexpi20ConformanceAssessment.Profile.PROCESS_PFD_BFD);
    return Dexpi20ProcessTopologyAssessment.assess(processSystem, file.toPath(), conformance, canonical,
        "CANONICAL_ENGINEERING_GRAPH_OPERATING_CASE");
  }

  private static void replaceStreamQuantitiesWithCanonicalValues(Path file,
      ProcessDiagramGraphAdapter.Result canonical) throws IOException {
    Document document = parse(file);
    Map<String, String> lineIdByName = canonicalLineIds(canonical.getGraph());
    Map<String, Map<String, CanonicalValue>> valuesBySubject = canonicalValues(canonical.getGraph());

    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (!"Process/Process.Stream".equals(object.getAttribute("type"))) {
        continue;
      }
      removeQuantity(object, "MassFlow");
      removeQuantity(object, "Pressure");
      removeQuantity(object, "Temperature");

      String label = dataString(object, "Label");
      String subjectId = lineIdByName.get(label);
      Map<String, CanonicalValue> values = subjectId == null ? null : valuesBySubject.get(subjectId);
      if (values == null) {
        continue;
      }
      appendConvertedQuantity(document, object, "MassFlow", values.get("massFlow"));
      appendConvertedQuantity(document, object, "Pressure", values.get("pressure"));
      appendConvertedQuantity(document, object, "Temperature", values.get("temperature"));
    }
    write(document, file);
  }

  private static Map<String, String> canonicalLineIds(EngineeringGraph graph) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.LINE) {
        Object equipmentName = node.getProperties().get("equipmentName");
        if (equipmentName != null) {
          result.put(String.valueOf(equipmentName), node.getId());
        }
      }
    }
    return result;
  }

  private static Map<String, Map<String, CanonicalValue>> canonicalValues(EngineeringGraph graph) {
    Map<String, Map<String, CanonicalValue>> result = new LinkedHashMap<String, Map<String, CanonicalValue>>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() != EngineeringNode.Kind.CALCULATION
          || !"CALCULATED".equals(String.valueOf(node.getProperties().get("status")))) {
        continue;
      }
      Object subject = node.getProperties().get("subjectNodeId");
      Object quantity = node.getProperties().get("quantity");
      Object value = node.getProperties().get("resultValue");
      Object unit = node.getProperties().get("resultUnit");
      if (subject == null || quantity == null || !(value instanceof Number) || unit == null) {
        continue;
      }
      String subjectId = String.valueOf(subject);
      Map<String, CanonicalValue> subjectValues = result.get(subjectId);
      if (subjectValues == null) {
        subjectValues = new LinkedHashMap<String, CanonicalValue>();
        result.put(subjectId, subjectValues);
      }
      subjectValues.put(String.valueOf(quantity),
          new CanonicalValue(((Number) value).doubleValue(), String.valueOf(unit)));
    }
    return result;
  }

  private static void appendConvertedQuantity(Document document, Element stream, String property,
      CanonicalValue canonical) {
    if (canonical == null || !Double.isFinite(canonical.value)) {
      return;
    }
    if ("MassFlow".equals(property) && "kg/s".equals(canonical.unit)) {
      physicalQuantity(document, stream, property, canonical.value * 3600.0,
          "Core/PhysicalQuantities.MassFlowRateUnit.KilogramPerHour");
    } else if ("Pressure".equals(property) && "bara".equals(canonical.unit)) {
      physicalQuantity(document, stream, property, canonical.value,
          "Core/PhysicalQuantities.PressureAbsoluteUnit.Bar");
    } else if ("Temperature".equals(property) && "K".equals(canonical.unit)) {
      physicalQuantity(document, stream, property, canonical.value - 273.15,
          "Core/PhysicalQuantities.TemperatureUnit.DegreeCelsius");
    }
  }

  private static void removeQuantity(Element stream, String property) {
    List<Node> removals = new ArrayList<Node>();
    NodeList children = stream.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child instanceof Element && "Components".equals(child.getNodeName())
          && property.equals(((Element) child).getAttribute("property"))) {
        removals.add(child);
      }
    }
    for (Node removal : removals) {
      stream.removeChild(removal);
    }
  }

  private static String dataString(Element parent, String property) {
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (!(child instanceof Element) || !"Data".equals(child.getNodeName())
          || !property.equals(((Element) child).getAttribute("property"))) {
        continue;
      }
      NodeList values = child.getChildNodes();
      for (int valueIndex = 0; valueIndex < values.getLength(); valueIndex++) {
        Node value = values.item(valueIndex);
        if (value instanceof Element && "String".equals(value.getNodeName())) {
          return value.getTextContent();
        }
      }
    }
    return "";
  }

  private static void physicalQuantity(Document document, Element stream, String property, double value,
      String unitReference) {
    Element values = components(document, stream, property);
    Element qualified = object(document, "Core/QualifiedValue");
    Element data = document.createElement("Data");
    data.setAttribute("property", "Value");
    Element quantity = document.createElement("AggregatedDataValue");
    quantity.setAttribute("type", "Core/PhysicalQuantities.PhysicalQuantity");
    Element unitData = document.createElement("Data");
    unitData.setAttribute("property", "Unit");
    Element unit = document.createElement("DataReference");
    unit.setAttribute("data", unitReference);
    unitData.appendChild(unit);
    quantity.appendChild(unitData);
    Element valueData = document.createElement("Data");
    valueData.setAttribute("property", "Value");
    Element number = document.createElement("Double");
    number.setTextContent(Double.toString(value));
    valueData.appendChild(number);
    quantity.appendChild(valueData);
    data.appendChild(quantity);
    qualified.appendChild(data);
    values.appendChild(qualified);
  }

  private static Element object(Document document, String type) {
    Element object = document.createElement("Object");
    object.setAttribute("type", type);
    return object;
  }

  private static Element components(Document document, Element parent, String property) {
    Element result = document.createElement("Components");
    result.setAttribute("property", property);
    parent.appendChild(result);
    return result;
  }

  private static String canonicalPlantId(ProcessDiagramGraphAdapter.Result canonical) {
    EngineeringGraph graph = canonical.getGraph();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.PROJECT) {
        return node.getExternalKey();
      }
    }
    throw new IllegalArgumentException("canonical graph contains no project node");
  }

  private static String canonicalRevision(ProcessDiagramGraphAdapter.Result canonical) {
    Object revision = canonical.getGraph().toMap().get("revision");
    if (revision == null || String.valueOf(revision).trim().isEmpty()) {
      throw new IllegalArgumentException("canonical graph contains no revision");
    }
    return String.valueOf(revision);
  }

  private static Document parse(Path file) throws IOException {
    try {
      DocumentBuilderFactory factory = secureFactory();
      return factory.newDocumentBuilder().parse(file.toFile());
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not configure DEXPI XML parser", ex);
    } catch (SAXException ex) {
      throw new IOException("Could not parse generated DEXPI Process XML", ex);
    }
  }

  private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory;
  }

  private static void write(Document document, Path file) throws IOException {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.transform(new DOMSource(document), new StreamResult(file.toFile()));
    } catch (TransformerException ex) {
      throw new IOException("Could not serialize canonical DEXPI Process XML", ex);
    }
  }

  private static void validate(Path file) throws IOException {
    try {
      Dexpi20XmlValidator.validate(file);
      Dexpi20SemanticValidator.validateOrThrow(file);
    } catch (SAXException ex) {
      throw new IOException("Canonical operating-value DEXPI Process XML failed schema validation", ex);
    }
  }

  private static final class CanonicalValue implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double value;
    private final String unit;

    private CanonicalValue(double value, String unit) {
      this.value = value;
      this.unit = unit;
    }
  }
}
