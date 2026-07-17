package neqsim.process.engineering.dexpi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import neqsim.process.engineering.EngineeringBoundary;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.processmodel.dexpi.Dexpi20SemanticValidator;
import neqsim.process.processmodel.dexpi.Dexpi20XmlValidator;

/** Adds governed instrumentation, safeguards and explicit boundaries to native DEXPI 2.0 XML. */
final class Dexpi20EngineeringMaterializer {
  private Dexpi20EngineeringMaterializer() {
  }

  static void materialize(EngineeringProject project, Path file) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      Document document = factory.newDocumentBuilder().parse(file.toFile());
      addEngineeringModel(project, document);
      removeWhitespaceNodes(document.getDocumentElement());
      write(document, file);
      Dexpi20XmlValidator.validate(file);
      Dexpi20SemanticValidator.validateOrThrow(file);
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not configure native DEXPI engineering materialization", ex);
    } catch (SAXException ex) {
      throw new IOException("Native DEXPI engineering XML failed schema validation", ex);
    } catch (TransformerException ex) {
      throw new IOException("Could not serialize native DEXPI engineering XML", ex);
    }
  }

  private static void addEngineeringModel(EngineeringProject project, Document document) {
    Element plant = findObjectByType(document, "Plant/PlantModel");
    Element engineeringModel = findObjectByType(document, "Core/EngineeringModel");
    if (plant == null || engineeringModel == null) {
      throw new IllegalArgumentException("DEXPI document has no Plant/PlantModel");
    }
    Set<String> usedIds = collectIds(document);
    Map<String, Element> equipment = collectEquipmentByTag(document);
    Element functions = directComponents(document, plant, "ProcessInstrumentationFunctions");
    Element diagramGroups = createDiagram(document, engineeringModel, plant.getAttribute("id"), "NeqSim governed P&ID",
        "PIPING_AND_INSTRUMENTATION_DIAGRAM");
    Element pfdGroups = createDiagram(document, engineeringModel, plant.getAttribute("id"), "NeqSim governed PFD",
        "PROCESS_FLOW_DIAGRAM");
    int pfdRepresentationNumber = 1;
    for (Map.Entry<String, Element> item : equipment.entrySet()) {
      appendRepresentation(document, pfdGroups, item.getValue().getAttribute("id"), item.getKey(),
          pfdRepresentationNumber++);
    }
    Element systems = directComponents(document, plant, "PipingNetworkSystems");
    Element safeguardSystem = object(document, uniqueId("EngineeringSafeguards", usedIds),
        "Plant/Piping.PipingNetworkSystem");
    data(document, safeguardSystem, "LineNumber", "ENGINEERING_SAFEGUARDS");
    Element safeguardSegments = components(document, safeguardSystem, "Segments");
    int segmentNumber = 1;

    int representationNumber = 1;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      Element protectedEquipment = equipment.get(requirement.getEquipmentTag());
      if (protectedEquipment == null) {
        continue;
      }
      appendInstrumentationFunction(document, functions, diagramGroups, usedIds, requirement, protectedEquipment,
          representationNumber++);
      if (requiresFinalElement(requirement)) {
        appendSafeguardSegment(document, safeguardSegments, diagramGroups, usedIds, requirement, protectedEquipment,
            segmentNumber++, representationNumber++);
      }
    }
    for (EngineeringBoundary boundary : project.getBoundaries()) {
      Element protectedEquipment = equipment.get(boundary.getEquipmentTag());
      if (protectedEquipment != null) {
        appendBoundarySegment(document, safeguardSegments, diagramGroups, usedIds, boundary, protectedEquipment,
            segmentNumber++, representationNumber++);
      }
    }
    if (segmentNumber > 1) {
      systems.appendChild(safeguardSystem);
    }
  }

  private static void appendBoundarySegment(Document document, Element segments, Element diagramGroups,
      Set<String> usedIds, EngineeringBoundary boundary, Element equipment, int number, int representationNumber) {
    String base = safe(boundary.getId());
    boolean inlet = isInlet(boundary.getType());
    String connectorType = inlet ? "FlowInPipeOffPageConnector" : "FlowOutPipeOffPageConnector";
    String connectorId = uniqueId(connectorType + "_" + base, usedIds);
    String connectorNode = uniqueId(connectorId + "_Node", usedIds);
    String equipmentNode = appendDedicatedNozzle(document, equipment, usedIds, base);
    Element segment = object(document, uniqueId("BoundarySegment_" + base, usedIds),
        "Plant/Piping.PipingNetworkSegment");
    data(document, segment, "FluidCode", boundary.getType().name());
    data(document, segment, "SegmentNumber", "BOUNDARY-" + number);
    Element items = components(document, segment, "Items");
    Element connector = object(document, connectorId, "Plant/Piping." + connectorType);
    data(document, connector, "PositionNumber", boundary.getId());
    Element nodes = components(document, connector, "Nodes");
    nodes.appendChild(object(document, connectorNode, "Plant/Piping.PipingNode"));
    items.appendChild(connector);
    appendRepresentation(document, diagramGroups, connectorId, boundary.getId(), representationNumber);
    Element connections = components(document, segment, "Connections");
    String equipmentId = equipment.getAttribute("id");
    if (inlet) {
      appendPipe(document, connections, usedIds, base, connectorId, connectorNode, equipmentId, equipmentNode);
      references(document, segment, "SourceItem", connectorId);
      references(document, segment, "SourceNode", connectorNode);
      references(document, segment, "TargetItem", equipmentId);
      references(document, segment, "TargetNode", equipmentNode);
    } else {
      appendPipe(document, connections, usedIds, base, equipmentId, equipmentNode, connectorId, connectorNode);
      references(document, segment, "SourceItem", equipmentId);
      references(document, segment, "SourceNode", equipmentNode);
      references(document, segment, "TargetItem", connectorId);
      references(document, segment, "TargetNode", connectorNode);
    }
    segments.appendChild(segment);
  }

  private static boolean isInlet(EngineeringBoundary.Type type) {
    return type == EngineeringBoundary.Type.PROCESS_INLET || type == EngineeringBoundary.Type.UTILITY_INLET;
  }

  private static void appendInstrumentationFunction(Document document, Element functions, Element diagramGroups,
      Set<String> usedIds, EngineeringRequirement requirement, Element protectedEquipment, int representationNumber) {
    String base = safe(requirement.getId());
    String functionId = uniqueId("ProcessInstrumentationFunction_" + base, usedIds);
    Element function = object(document, functionId, "Plant/Instrumentation.ProcessInstrumentationFunction");
    dataReference(document, function, "Location", "Plant/Enumerations.LocationClassification.CentralLocation");
    data(document, function, "ProcessInstrumentationFunctionCategory", category(requirement));
    data(document, function, "ProcessInstrumentationFunctionNumber", requirement.getId());
    data(document, function, "ProcessInstrumentationFunctions", functionLetters(requirement));
    data(document, function, "SafetyRelevanceClass", requirement.getSilTarget());

    Element generators = components(document, function, "ProcessSignalGeneratingFunctions");
    String generatorId = uniqueId("ProcessSignalGeneratingFunction_" + base, usedIds);
    Element generator = object(document, generatorId, "Plant/Instrumentation.ProcessSignalGeneratingFunction");
    data(document, generator, "ProcessSignalGeneratingFunctionNumber", sensingTag(requirement));
    references(document, generator, "SensingLocation", protectedEquipment.getAttribute("id"));
    generators.appendChild(generator);

    Element signals = components(document, function, "SignalConveyingFunctions");
    Element measuringLine = object(document, uniqueId("MeasuringLineFunction_" + base, usedIds),
        "Plant/Instrumentation.MeasuringLineFunction");
    references(document, measuringLine, "Source", generatorId);
    references(document, measuringLine, "Target", functionId);
    signals.appendChild(measuringLine);
    functions.appendChild(function);
    appendRepresentation(document, diagramGroups, functionId, requirement.getId(), representationNumber);
  }

  private static void appendSafeguardSegment(Document document, Element segments, Element diagramGroups,
      Set<String> usedIds, EngineeringRequirement requirement, Element protectedEquipment, int number,
      int representationNumber) {
    String base = safe(requirement.getId());
    String valveId = uniqueId(finalElementPrefix(requirement) + "_" + base, usedIds);
    String valveInletNode = uniqueId(valveId + "_InletNode", usedIds);
    String valveOutletNode = uniqueId(valveId + "_OutletNode", usedIds);
    String boundaryId = uniqueId(boundaryDirection(requirement) + "_" + base, usedIds);
    String boundaryNode = uniqueId(boundaryId + "_Node", usedIds);
    String equipmentNode = appendDedicatedNozzle(document, protectedEquipment, usedIds, base);

    Element segment = object(document, uniqueId("SafeguardSegment_" + base, usedIds),
        "Plant/Piping.PipingNetworkSegment");
    data(document, segment, "FluidCode", boundaryService(requirement));
    data(document, segment, "SegmentNumber", "SAFEGUARD-" + number);
    Element items = components(document, segment, "Items");

    Element valve = object(document, valveId, finalElementType(requirement));
    data(document, valve, "PositionNumber", finalElementTag(requirement));
    Element valveNodes = components(document, valve, "Nodes");
    valveNodes.appendChild(object(document, valveInletNode, "Plant/Piping.PipingNode"));
    valveNodes.appendChild(object(document, valveOutletNode, "Plant/Piping.PipingNode"));
    items.appendChild(valve);
    appendRepresentation(document, diagramGroups, valveId, finalElementTag(requirement), representationNumber);

    Element boundary = object(document, boundaryId, "Plant/Piping." + boundaryDirection(requirement));
    data(document, boundary, "PositionNumber", boundaryService(requirement) + "_BOUNDARY");
    Element boundaryNodes = components(document, boundary, "Nodes");
    boundaryNodes.appendChild(object(document, boundaryNode, "Plant/Piping.PipingNode"));
    items.appendChild(boundary);

    Element connections = components(document, segment, "Connections");
    String equipmentId = protectedEquipment.getAttribute("id");
    appendPipe(document, connections, usedIds, base + "_EquipmentToValve", equipmentId, equipmentNode, valveId,
        valveInletNode);
    appendPipe(document, connections, usedIds, base + "_ValveToBoundary", valveId, valveOutletNode, boundaryId,
        boundaryNode);
    references(document, segment, "SourceItem", equipmentId);
    references(document, segment, "SourceNode", equipmentNode);
    references(document, segment, "TargetItem", boundaryId);
    references(document, segment, "TargetNode", boundaryNode);
    segments.appendChild(segment);
  }

  private static Element createDiagram(Document document, Element engineeringModel, String plantId, String name,
      String diagramType) {
    Element diagramProperty = directComponents(document, engineeringModel, "Diagram");
    Element diagram = objectWithoutId(document, "Core/Diagram.Diagram");
    data(document, diagram, "Name", name);
    data(document, diagram, "DiagramType", diagramType);
    doubleData(document, diagram, "MinX", 0.0);
    doubleData(document, diagram, "MinY", 0.0);
    doubleData(document, diagram, "MaxX", 420.0);
    doubleData(document, diagram, "MaxY", 297.0);
    Element groups = components(document, diagram, "Groups");
    references(document, diagram, "Represents", plantId);
    diagramProperty.appendChild(diagram);
    return groups;
  }

  private static void appendRepresentation(Document document, Element groups, String representedId, String label,
      int number) {
    double x = 20.0 + (number % 8) * 45.0;
    double y = 30.0 + (number / 8) * 24.0;
    Element group = objectWithoutId(document, "Core/Diagram.RepresentationGroup");
    Element elements = components(document, group, "Elements");
    Element text = objectWithoutId(document, "Core/Diagram.Text");
    dataReference(document, text, "Alignment", "Core/Diagram.TextAlignment.CenterCenter");
    pointData(document, text, "Position", x, y);
    doubleData(document, text, "Rotation", 0.0);
    doubleData(document, text, "Size", 3.0);
    data(document, text, "Text", label);
    elements.appendChild(text);
    references(document, group, "Represents", representedId);
    groups.appendChild(group);
  }

  private static void appendPipe(Document document, Element connections, Set<String> usedIds, String name,
      String sourceItem, String sourceNode, String targetItem, String targetNode) {
    Element pipe = object(document, uniqueId("Pipe_" + safe(name), usedIds), "Plant/Piping.Pipe");
    references(document, pipe, "SourceItem", sourceItem);
    references(document, pipe, "SourceNode", sourceNode);
    references(document, pipe, "TargetItem", targetItem);
    references(document, pipe, "TargetNode", targetNode);
    connections.appendChild(pipe);
  }

  private static String appendDedicatedNozzle(Document document, Element equipment, Set<String> usedIds, String base) {
    Element nozzles = directComponents(document, equipment, "Nozzles");
    String nozzleId = uniqueId(equipment.getAttribute("id") + "_SafeguardNozzle_" + base, usedIds);
    String nodeId = uniqueId(nozzleId + "_Node", usedIds);
    Element nozzle = object(document, nozzleId, "Plant/ProcessEquipment.Nozzle");
    data(document, nozzle, "SubTagName", "SAFEGUARD_" + base);
    Element nodes = components(document, nozzle, "Nodes");
    nodes.appendChild(object(document, nodeId, "Plant/Piping.PipingNode"));
    nozzles.appendChild(nozzle);
    return nodeId;
  }

  private static boolean requiresFinalElement(EngineeringRequirement requirement) {
    return requirement.getType() == EngineeringRequirement.Type.RELIEF
        || requirement.getType() == EngineeringRequirement.Type.TRIP
        || requirement.getType() == EngineeringRequirement.Type.MECHANICAL_PROTECTION
        || requirement.getId().toUpperCase(Locale.ROOT).contains("ANTISURGE");
  }

  private static String finalElementType(EngineeringRequirement requirement) {
    if (requirement.getType() == EngineeringRequirement.Type.RELIEF) {
      return "Plant/Piping.SpringLoadedGlobeSafetyValve";
    }
    if (requirement.getType() == EngineeringRequirement.Type.MECHANICAL_PROTECTION) {
      return "Plant/Piping.SwingCheckValve";
    }
    return "Plant/Piping.GlobeValve";
  }

  private static String finalElementPrefix(EngineeringRequirement requirement) {
    if (requirement.getType() == EngineeringRequirement.Type.RELIEF) {
      return "PSV";
    }
    if (requirement.getType() == EngineeringRequirement.Type.MECHANICAL_PROTECTION) {
      return "NRV";
    }
    if (requirement.getId().toUpperCase(Locale.ROOT).contains("ANTISURGE")) {
      return "ASCV";
    }
    return "ESDV";
  }

  private static String finalElementTag(EngineeringRequirement requirement) {
    return finalElementPrefix(requirement) + "-" + safe(requirement.getEquipmentTag());
  }

  private static String boundaryDirection(EngineeringRequirement requirement) {
    return requirement.getId().toUpperCase(Locale.ROOT).contains("SUCTION") ? "FlowInPipeOffPageConnector"
        : "FlowOutPipeOffPageConnector";
  }

  private static String boundaryService(EngineeringRequirement requirement) {
    String id = requirement.getId().toUpperCase(Locale.ROOT);
    if (requirement.getType() == EngineeringRequirement.Type.RELIEF) {
      return "FLARE_HEADER";
    }
    if (id.contains("BLOWDOWN") || id.contains("BDV")) {
      return "DEPRESSURIZATION_HEADER";
    }
    if (id.contains("DRAIN")) {
      return "CLOSED_DRAIN";
    }
    if (id.contains("VENT")) {
      return "VENT_HEADER";
    }
    if (id.contains("UTILITY")) {
      return "UTILITY_TIE_IN";
    }
    if (id.contains("ANTISURGE")) {
      return "RECYCLE_TIE_IN";
    }
    return "PROCESS_LINE_TIE_IN";
  }

  private static String category(EngineeringRequirement requirement) {
    String id = requirement.getId().toUpperCase(Locale.ROOT);
    if (id.contains("LEVEL")) {
      return "L";
    }
    if (id.contains("TEMPERATURE") || id.contains("-T-")) {
      return "T";
    }
    if (id.contains("FLOW") || id.contains("ANTISURGE")) {
      return "F";
    }
    return "P";
  }

  private static String functionLetters(EngineeringRequirement requirement) {
    if (requirement.getType() == EngineeringRequirement.Type.TRIP) {
      return "ISA";
    }
    if (requirement.getType() == EngineeringRequirement.Type.ALARM) {
      return "IA";
    }
    if (requirement.getType() == EngineeringRequirement.Type.CONTROL) {
      return "IC";
    }
    return "I";
  }

  private static String sensingTag(EngineeringRequirement requirement) {
    return category(requirement) + "T-" + safe(requirement.getEquipmentTag());
  }

  private static Element findObjectByType(Document document, String type) {
    NodeList objects = document.getElementsByTagName("Object");
    for (int i = 0; i < objects.getLength(); i++) {
      Element object = (Element) objects.item(i);
      if (type.equals(object.getAttribute("type"))) {
        return object;
      }
    }
    return null;
  }

  private static Map<String, Element> collectEquipmentByTag(Document document) {
    Map<String, Element> result = new LinkedHashMap<String, Element>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int i = 0; i < objects.getLength(); i++) {
      Element object = (Element) objects.item(i);
      if (!object.getAttribute("type").startsWith("Plant/ProcessEquipment.")) {
        continue;
      }
      String tag = directStringData(object, "TagName");
      if (tag != null) {
        result.put(tag, object);
      }
    }
    return result;
  }

  private static Set<String> collectIds(Document document) {
    Set<String> result = new LinkedHashSet<String>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int i = 0; i < objects.getLength(); i++) {
      String id = ((Element) objects.item(i)).getAttribute("id");
      if (!id.isEmpty()) {
        result.add(id);
      }
    }
    return result;
  }

  private static Element directComponents(Document document, Element parent, String property) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Components".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return (Element) child;
      }
    }
    return components(document, parent, property);
  }

  private static String directStringData(Element object, String property) {
    for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Data".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        NodeList strings = ((Element) child).getElementsByTagName("String");
        return strings.getLength() == 0 ? null : strings.item(0).getTextContent();
      }
    }
    return null;
  }

  private static Element object(Document document, String id, String type) {
    Element result = document.createElement("Object");
    result.setAttribute("id", id);
    result.setAttribute("type", type);
    return result;
  }

  private static Element objectWithoutId(Document document, String type) {
    Element result = document.createElement("Object");
    result.setAttribute("type", type);
    return result;
  }

  private static Element components(Document document, Element parent, String property) {
    Element result = document.createElement("Components");
    result.setAttribute("property", property);
    parent.appendChild(result);
    return result;
  }

  private static void data(Document document, Element parent, String property, String value) {
    Element item = document.createElement("Data");
    item.setAttribute("property", property);
    Element string = document.createElement("String");
    string.setTextContent(value);
    item.appendChild(string);
    parent.appendChild(item);
  }

  private static void dataReference(Document document, Element parent, String property, String value) {
    Element item = document.createElement("Data");
    item.setAttribute("property", property);
    Element reference = document.createElement("DataReference");
    reference.setAttribute("data", value);
    item.appendChild(reference);
    parent.appendChild(item);
  }

  private static void doubleData(Document document, Element parent, String property, double value) {
    Element item = document.createElement("Data");
    item.setAttribute("property", property);
    Element number = document.createElement("Double");
    number.setTextContent(Double.toString(value));
    item.appendChild(number);
    parent.appendChild(item);
  }

  private static void pointData(Document document, Element parent, String property, double x, double y) {
    Element item = document.createElement("Data");
    item.setAttribute("property", property);
    Element point = document.createElement("AggregatedDataValue");
    point.setAttribute("type", "Core/Diagram.Point");
    doubleData(document, point, "X", x);
    doubleData(document, point, "Y", y);
    item.appendChild(point);
    parent.appendChild(item);
  }

  private static void references(Document document, Element parent, String property, String id) {
    Element reference = document.createElement("References");
    reference.setAttribute("property", property);
    reference.setAttribute("objects", "#" + id);
    parent.appendChild(reference);
  }

  private static String uniqueId(String value, Set<String> usedIds) {
    String base = safe(value);
    String result = base;
    int number = 2;
    while (!usedIds.add(result)) {
      result = base + "_" + number++;
    }
    return result;
  }

  private static String safe(String value) {
    String result = value == null ? "UNASSIGNED" : value.trim().replaceAll("[^A-Za-z0-9_]", "_");
    if (result.isEmpty()) {
      return "UNASSIGNED";
    }
    return Character.isLetter(result.charAt(0)) || result.charAt(0) == '_' ? result : "N_" + result;
  }

  private static void write(Document document, Path file) throws TransformerException {
    TransformerFactory factory = TransformerFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    Transformer transformer = factory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.transform(new DOMSource(document), new StreamResult(file.toFile()));
  }

  private static void removeWhitespaceNodes(Node parent) {
    for (Node child = parent.getFirstChild(); child != null;) {
      Node next = child.getNextSibling();
      if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
        parent.removeChild(child);
      } else {
        removeWhitespaceNodes(child);
      }
      child = next;
    }
  }
}
