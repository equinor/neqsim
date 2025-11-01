package neqsim.process.processmodel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Utility for reading DEXPI XML files and converting them into NeqSim process models.
 */
public final class DexpiXmlReader {
  private static final Map<String, EquipmentEnum> EQUIPMENT_CLASS_MAP;
  private static final Map<String, EquipmentEnum> PIPING_COMPONENT_MAP;

  static {
    Map<String, EquipmentEnum> equipmentMap = new HashMap<>();
    equipmentMap.put("PlateHeatExchanger", EquipmentEnum.HeatExchanger);
    equipmentMap.put("TubularHeatExchanger", EquipmentEnum.HeatExchanger);
    equipmentMap.put("CentrifugalPump", EquipmentEnum.Pump);
    equipmentMap.put("ReciprocatingPump", EquipmentEnum.Pump);
    equipmentMap.put("Tank", EquipmentEnum.Tank);
    EQUIPMENT_CLASS_MAP = Collections.unmodifiableMap(equipmentMap);

    Map<String, EquipmentEnum> pipingMap = new HashMap<>();
    pipingMap.put("GlobeValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("ButterflyValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("CheckValve", EquipmentEnum.ThrottlingValve);
    PIPING_COMPONENT_MAP = Collections.unmodifiableMap(pipingMap);
  }

  private DexpiXmlReader() {}

  /**
   * Reads the provided DEXPI XML file and returns a populated {@link ProcessSystem}.
   *
   * @param file DEXPI XML file
   * @return a process system populated with units found in the XML
   * @throws IOException if the file cannot be read
   */
  public static ProcessSystem read(File file) throws IOException {
    return read(file, null);
  }

  /**
   * Reads the provided DEXPI XML file and returns a populated {@link ProcessSystem} using a
   * template {@link Stream} for generated piping segments.
   *
   * @param file DEXPI XML file
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for
   *        generated piping segments. If {@code null}, a methane/ethane default is used.
   * @return a process system populated with units found in the XML
   * @throws IOException if the file cannot be read
   */
  public static ProcessSystem read(File file, Stream templateStream) throws IOException {
    Objects.requireNonNull(file, "file");
    try (InputStream inputStream = new FileInputStream(file)) {
      return read(inputStream, templateStream);
    }
  }

  /**
   * Reads the provided DEXPI XML stream and returns a populated {@link ProcessSystem}.
   *
   * @param inputStream stream containing DEXPI XML data
   * @return a process system populated with units found in the XML
   * @throws IOException if the stream cannot be read
   */
  public static ProcessSystem read(InputStream inputStream) throws IOException {
    return read(inputStream, null);
  }

  /**
   * Reads the provided DEXPI XML stream and returns a populated {@link ProcessSystem} using a
   * template {@link Stream} for any generated piping segments.
   *
   * @param inputStream stream containing DEXPI XML data
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for
   *        generated piping segments. If {@code null}, a methane/ethane default is used.
   * @return a process system populated with units found in the XML
   * @throws IOException if the stream cannot be read
   */
  public static ProcessSystem read(InputStream inputStream, Stream templateStream)
      throws IOException {
    ProcessSystem processSystem = new ProcessSystem("DEXPI process");
    load(inputStream, processSystem, templateStream);
    return processSystem;
  }

  /**
   * Populates an existing {@link ProcessSystem} with units parsed from a DEXPI XML file.
   *
   * @param file XML file to parse
   * @param processSystem target process system
   * @throws IOException if reading fails
   */
  public static void load(File file, ProcessSystem processSystem) throws IOException {
    load(file, processSystem, null);
  }

  /**
   * Populates an existing {@link ProcessSystem} with units parsed from a DEXPI XML file.
   *
   * @param file XML file to parse
   * @param processSystem target process system
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for
   *        generated piping segments. If {@code null}, a methane/ethane default is used.
   * @throws IOException if reading fails
   */
  public static void load(File file, ProcessSystem processSystem, Stream templateStream)
      throws IOException {
    Objects.requireNonNull(file, "file");
    try (InputStream inputStream = new FileInputStream(file)) {
      load(inputStream, processSystem, templateStream);
    }
  }

  /**
   * Populates an existing {@link ProcessSystem} with units parsed from a DEXPI XML stream.
   *
   * @param inputStream XML input stream
   * @param processSystem target process system
   * @throws IOException if reading fails
   */
  public static void load(InputStream inputStream, ProcessSystem processSystem) throws IOException {
    load(inputStream, processSystem, null);
  }

  /**
   * Populates an existing {@link ProcessSystem} with units parsed from a DEXPI XML stream.
   *
   * @param inputStream XML input stream
   * @param processSystem target process system
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for
   *        generated piping segments. If {@code null}, a methane/ethane default is used.
   * @throws IOException if reading fails
   */
  public static void load(InputStream inputStream, ProcessSystem processSystem,
      Stream templateStream) throws IOException {
    Objects.requireNonNull(inputStream, "inputStream");
    Objects.requireNonNull(processSystem, "processSystem");

    Document document = parseDocument(inputStream);
    if (document == null) {
      return;
    }

    Stream streamTemplate = templateOrDefault(templateStream);

    addEquipmentUnits(document, processSystem);
    addPipingComponents(document, processSystem);
    addPipingSegments(document, processSystem, streamTemplate);
  }

  private static Document parseDocument(InputStream inputStream) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setNamespaceAware(false);
      factory.setExpandEntityReferences(false);
      factory.setXIncludeAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(inputStream);
      document.getDocumentElement().normalize();
      return document;
    } catch (ParserConfigurationException | SAXException | IllegalArgumentException e) {
      throw new IOException("Unable to parse DEXPI XML", e);
    }
  }

  private static void addEquipmentUnits(Document document, ProcessSystem processSystem) {
    NodeList equipmentNodes = document.getElementsByTagName("Equipment");
    for (int i = 0; i < equipmentNodes.getLength(); i++) {
      Node node = equipmentNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) node;
      String componentClass = element.getAttribute("ComponentClass");
      EquipmentEnum equipmentEnum = EQUIPMENT_CLASS_MAP.get(componentClass);
      if (equipmentEnum == null) {
        continue;
      }

      String baseName = firstNonEmpty(getGenericAttribute(element, "TagNameAssignmentClass"),
          element.getAttribute("ID"));
      addDexpiUnit(processSystem, element, equipmentEnum, baseName,
          element.getAttribute("ComponentClass"));
    }
  }

  private static void addPipingComponents(Document document, ProcessSystem processSystem) {
    NodeList componentNodes = document.getElementsByTagName("PipingComponent");
    for (int i = 0; i < componentNodes.getLength(); i++) {
      Node node = componentNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) node;
      EquipmentEnum equipmentEnum = PIPING_COMPONENT_MAP.get(element.getAttribute("ComponentClass"));
      if (equipmentEnum == null) {
        continue;
      }

      String baseName = firstNonEmpty(
          getGenericAttribute(element, "PipingComponentNumberAssignmentClass"),
          getGenericAttribute(element, "TagNameAssignmentClass"), element.getAttribute("ID"));
      addDexpiUnit(processSystem, element, equipmentEnum, baseName,
          element.getAttribute("ComponentClass"));
    }
  }

  private static void addPipingSegments(Document document, ProcessSystem processSystem,
      Stream templateStream) {
    NodeList segmentNodes = document.getElementsByTagName("PipingNetworkSegment");
    for (int i = 0; i < segmentNodes.getLength(); i++) {
      Node node = segmentNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) node;
      String baseName = firstNonEmpty(getGenericAttribute(element, "SegmentNumberAssignmentClass"),
          element.getAttribute("ID"));
      addDexpiStream(processSystem, element, templateStream, baseName);
    }
  }

  private static void addDexpiStream(ProcessSystem processSystem, Element element,
      Stream templateStream, String baseName) {
    String contextualName = prependLineOrFluid(element, baseName);
    String uniqueName = ensureUniqueName(processSystem, contextualName);
    String lineNumber = findAttributeInAncestors(element, "LineNumberAssignmentClass");
    String fluidCode = firstNonEmpty(getGenericAttribute(element, "FluidCodeAssignmentClass"),
        findAttributeInAncestors(element, "FluidCodeAssignmentClass"));

    SystemInterface baseFluid = templateStream.getThermoSystem();
    SystemInterface fluid = baseFluid == null ? createDefaultFluid() : baseFluid.clone();

    DexpiStream stream = new DexpiStream(uniqueName, fluid, element.getAttribute("ComponentClass"),
        lineNumber, fluidCode);
    stream.setSpecification(templateStream.getSpecification());
    processSystem.addUnit(uniqueName, stream);
  }

  private static void addDexpiUnit(ProcessSystem processSystem, Element element,
      EquipmentEnum equipmentEnum, String baseName, String componentClass) {
    String contextualName = prependLineOrFluid(element, baseName);
    String uniqueName = ensureUniqueName(processSystem, contextualName);
    String lineNumber = findAttributeInAncestors(element, "LineNumberAssignmentClass");
    String fluidCode = firstNonEmpty(getGenericAttribute(element, "FluidCodeAssignmentClass"),
        findAttributeInAncestors(element, "FluidCodeAssignmentClass"));
    DexpiProcessUnit unit =
        new DexpiProcessUnit(uniqueName, componentClass, equipmentEnum, lineNumber, fluidCode);
    processSystem.addUnit(uniqueName, unit);
  }

  private static String prependLineOrFluid(Element element, String baseName) {
    String trimmedBase = baseName == null ? "" : baseName.trim();
    String lineNumber = findAttributeInAncestors(element, "LineNumberAssignmentClass");
    if (!isBlank(lineNumber)) {
      return lineNumber.trim() + "-" + trimmedBase;
    }
    String fluidCode = firstNonEmpty(getGenericAttribute(element, "FluidCodeAssignmentClass"),
        findAttributeInAncestors(element, "FluidCodeAssignmentClass"));
    if (!isBlank(fluidCode)) {
      return fluidCode.trim() + "-" + trimmedBase;
    }
    return trimmedBase.isEmpty() ? element.getAttribute("ID") : trimmedBase;
  }

  private static String ensureUniqueName(ProcessSystem processSystem, String candidate) {
    String base = isBlank(candidate) ? "Unit" : candidate;
    Set<String> existingNames = new HashSet<>(processSystem.getAllUnitNames());
    if (!existingNames.contains(base)) {
      return base;
    }
    int index = 2;
    String nextName = base + "-" + index;
    while (existingNames.contains(nextName)) {
      index++;
      nextName = base + "-" + index;
    }
    return nextName;
  }

  private static Stream templateOrDefault(Stream templateStream) {
    if (templateStream != null) {
      return templateStream;
    }
    return createDefaultTemplateStream();
  }

  private static Stream createDefaultTemplateStream() {
    SystemInterface fluid = createDefaultFluid();
    Stream template = new Stream("dexpi-template", fluid);
    template.setPressure(50.0, "bara");
    template.setTemperature(30.0, "C");
    template.setFlowRate(1.0, "MSm3/day");
    template.setSpecification("TP");
    return template;
  }

  private static SystemInterface createDefaultFluid() {
    SystemSrkEos system = new SystemSrkEos(298.15, 50.0);
    system.addComponent("methane", 0.9);
    system.addComponent("ethane", 0.1);
    system.setMixingRule(2);
    system.init(0);
    return system;
  }

  private static String getGenericAttribute(Element element, String attributeName) {
    List<Element> genericNodes = directChildElements(element, "GenericAttributes");
    for (Element generic : genericNodes) {
      NodeList attributes = generic.getElementsByTagName("GenericAttribute");
      for (int i = 0; i < attributes.getLength(); i++) {
        Node attributeNode = attributes.item(i);
        if (attributeNode.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        Element attribute = (Element) attributeNode;
        if (attributeName.equals(attribute.getAttribute("Name"))) {
          String value = attribute.getAttribute("Value");
          if (value == null || value.isEmpty()) {
            value = attribute.getAttribute("ValueURI");
          }
          if (value != null && !value.isEmpty()) {
            return value;
          }
        }
      }
    }
    return null;
  }

  private static List<Element> directChildElements(Element element, String tagName) {
    if (element == null) {
      return Collections.emptyList();
    }
    NodeList children = element.getChildNodes();
    List<Element> result = new ArrayList<>();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE
          && tagName.equals(((Element) child).getTagName())) {
        result.add((Element) child);
      }
    }
    return result;
  }

  private static String findAttributeInAncestors(Node node, String attributeName) {
    Node current = node;
    while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
      Element element = (Element) current;
      String value = getGenericAttribute(element, attributeName);
      if (!isBlank(value)) {
        return value;
      }
      current = current.getParentNode();
    }
    return null;
  }

  private static String firstNonEmpty(String... candidates) {
    if (candidates == null) {
      return null;
    }
    for (String candidate : candidates) {
      if (!isBlank(candidate)) {
        return candidate.trim();
      }
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
