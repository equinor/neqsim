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
import java.util.function.BiConsumer;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Utility for reading DEXPI XML files and converting them into NeqSim process models.
 */
public final class DexpiXmlReader {
  private static final Logger logger = LogManager.getLogger(DexpiXmlReader.class);

  private static final Map<String, EquipmentEnum> EQUIPMENT_CLASS_MAP;
  private static final Map<String, EquipmentEnum> PIPING_COMPONENT_MAP;

  static {
    Map<String, EquipmentEnum> equipmentMap = new HashMap<>();
    equipmentMap.put("PlateHeatExchanger", EquipmentEnum.HeatExchanger);
    equipmentMap.put("ShellAndTubeHeatExchanger", EquipmentEnum.HeatExchanger);
    equipmentMap.put("TubularHeatExchanger", EquipmentEnum.HeatExchanger);
    equipmentMap.put("AirCooledHeatExchanger", EquipmentEnum.HeatExchanger);
    equipmentMap.put("CentrifugalPump", EquipmentEnum.Pump);
    equipmentMap.put("ReciprocatingPump", EquipmentEnum.Pump);
    equipmentMap.put("CentrifugalCompressor", EquipmentEnum.Compressor);
    equipmentMap.put("ReciprocatingCompressor", EquipmentEnum.Compressor);
    equipmentMap.put("Tank", EquipmentEnum.Tank);
    equipmentMap.put("StirredTankReactor", EquipmentEnum.Reactor);
    equipmentMap.put("PlugFlowReactor", EquipmentEnum.Reactor);
    equipmentMap.put("PackedBedReactor", EquipmentEnum.Reactor);
    equipmentMap.put("Column", EquipmentEnum.Column);
    equipmentMap.put("Agitator", EquipmentEnum.Mixer);
    equipmentMap.put("Boiler", EquipmentEnum.Heater);
    equipmentMap.put("Filter", EquipmentEnum.Separator);
    equipmentMap.put("Cyclone", EquipmentEnum.Separator);
    equipmentMap.put("InlineAnalyzer", EquipmentEnum.Calculator);
    equipmentMap.put("GasAnalyzer", EquipmentEnum.Calculator);
    equipmentMap.put("Spectrometer", EquipmentEnum.Calculator);
    EQUIPMENT_CLASS_MAP = Collections.unmodifiableMap(equipmentMap);

    Map<String, EquipmentEnum> pipingMap = new HashMap<>();
    pipingMap.put("GlobeValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("ButterflyValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("CheckValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("ControlValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("PressureSafetyValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("PressureReliefValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("PressureReducingValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("BallValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("GateValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("PlugValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("DiaphragmValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("NeedleValve", EquipmentEnum.ThrottlingValve);
    pipingMap.put("OrificePlate", EquipmentEnum.Calculator);
    pipingMap.put("FlowMeter", EquipmentEnum.Calculator);
    pipingMap.put("RuptureDisk", EquipmentEnum.ThrottlingValve);
    PIPING_COMPONENT_MAP = Collections.unmodifiableMap(pipingMap);
  }

  private DexpiXmlReader() {}

  /**
   * Reads the provided DEXPI XML file and returns a populated {@link ProcessSystem}.
   *
   * @param file DEXPI XML file
   * @return a process system populated with units found in the XML
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static ProcessSystem read(File file) throws IOException, DexpiXmlReaderException {
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
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static ProcessSystem read(File file, Stream templateStream)
      throws IOException, DexpiXmlReaderException {
    Objects.requireNonNull(file, "file");
    logger.info("Reading DEXPI XML file: {}", file.getAbsolutePath());
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
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static ProcessSystem read(InputStream inputStream)
      throws IOException, DexpiXmlReaderException {
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
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static ProcessSystem read(InputStream inputStream, Stream templateStream)
      throws IOException, DexpiXmlReaderException {
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
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static void load(File file, ProcessSystem processSystem)
      throws IOException, DexpiXmlReaderException {
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
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static void load(File file, ProcessSystem processSystem, Stream templateStream)
      throws IOException, DexpiXmlReaderException {
    Objects.requireNonNull(file, "file");
    logger.info("Loading DEXPI XML file: {}", file.getAbsolutePath());
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
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static void load(InputStream inputStream, ProcessSystem processSystem)
      throws IOException, DexpiXmlReaderException {
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
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static void load(InputStream inputStream, ProcessSystem processSystem,
      Stream templateStream) throws IOException, DexpiXmlReaderException {
    Objects.requireNonNull(inputStream, "inputStream");
    Objects.requireNonNull(processSystem, "processSystem");

    Document document = parseDocument(inputStream);
    if (document == null) {
      return;
    }

    Stream streamTemplate = templateOrDefault(templateStream);

    addUnits(document, processSystem, "Equipment", EQUIPMENT_CLASS_MAP, DexpiMetadata.TAG_NAME);
    addUnits(document, processSystem, "PipingComponent", PIPING_COMPONENT_MAP,
        "PipingComponentNumberAssignmentClass");
    addPipingSegments(document, processSystem, streamTemplate);
  }

  private static Document parseDocument(InputStream inputStream) throws DexpiXmlReaderException {
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
      builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
          throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
          throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
          throw exception;
        }
      });
      Document document = builder.parse(inputStream);
      document.getDocumentElement().normalize();
      return document;
    } catch (ParserConfigurationException | SAXException | IOException
        | IllegalArgumentException e) {
      throw new DexpiXmlReaderException("Unable to parse DEXPI XML", e);
    }
  }

  private static void addUnits(Document document, ProcessSystem processSystem, String tagName,
      Map<String, EquipmentEnum> equipmentMap, String nameAttribute) {
    NodeList parentNodes = document.getElementsByTagName(tagName);
    logger.info("Found {} {} parent elements", parentNodes.getLength(), tagName);

    int totalUnits = 0;
    for (int i = 0; i < parentNodes.getLength(); i++) {
      Node parentNode = parentNodes.item(i);
      if (parentNode.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element parentElement = (Element) parentNode;

      // Look for all child elements of the parent (Equipment or PipingComponent)
      NodeList childNodes = parentElement.getChildNodes();
      for (int j = 0; j < childNodes.getLength(); j++) {
        Node childNode = childNodes.item(j);
        if (childNode.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        Element element = (Element) childNode;
        String componentClass = element.getAttribute("ComponentClass");
        EquipmentEnum equipmentEnum = equipmentMap.get(componentClass);
        if (equipmentEnum == null) {
          logger.warn("Unsupported component class: {}", componentClass);
          continue;
        }

        String baseName = firstNonEmpty(attributeValue(element, nameAttribute),
            attributeValue(element, DexpiMetadata.TAG_NAME), element.getAttribute("ID"));
        addDexpiUnit(processSystem, element, equipmentEnum, baseName,
            element.getAttribute("ComponentClass"));
        totalUnits++;
      }
    }
    logger.info("Added {} units from {} elements", totalUnits, tagName);
  }

  private static void addPipingSegments(Document document, ProcessSystem processSystem,
      Stream templateStream) {
    NodeList segmentNodes = document.getElementsByTagName("PipingNetworkSegment");
    logger.info("Found {} PipingNetworkSegments", segmentNodes.getLength());
    for (int i = 0; i < segmentNodes.getLength(); i++) {
      Node node = segmentNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) node;
      String baseName = firstNonEmpty(attributeValue(element, DexpiMetadata.SEGMENT_NUMBER),
          element.getAttribute("ID"));
      addDexpiStream(processSystem, element, templateStream, baseName);
    }
  }

  private static void addDexpiStream(ProcessSystem processSystem, Element element,
      Stream templateStream, String baseName) {
    String contextualName = prependLineOrFluid(element, baseName);
    String uniqueName = ensureUniqueName(processSystem, contextualName);
    String lineNumber = attributeValue(element, DexpiMetadata.LINE_NUMBER);
    String fluidCode = attributeValue(element, DexpiMetadata.FLUID_CODE);

    SystemInterface baseFluid = templateStream.getThermoSystem();
    SystemInterface fluid = baseFluid == null ? createDefaultFluid() : baseFluid.clone();

    DexpiStream stream = new DexpiStream(uniqueName, fluid, element.getAttribute("ComponentClass"),
        lineNumber, fluidCode);
    stream.setSpecification(templateStream.getSpecification());
    stream.setPressure(templateStream.getPressure(DexpiMetadata.DEFAULT_PRESSURE_UNIT),
        DexpiMetadata.DEFAULT_PRESSURE_UNIT);
    stream.setTemperature(templateStream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT),
        DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
    stream.setFlowRate(templateStream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT),
        DexpiMetadata.DEFAULT_FLOW_UNIT);

    applyStreamMetadata(element, stream);
    processSystem.addUnit(uniqueName, stream);
  }

  private static void addDexpiUnit(ProcessSystem processSystem, Element element,
      EquipmentEnum equipmentEnum, String baseName, String componentClass) {
    String contextualName = prependLineOrFluid(element, baseName);
    String uniqueName = ensureUniqueName(processSystem, contextualName);
    String lineNumber = attributeValue(element, DexpiMetadata.LINE_NUMBER);
    String fluidCode = attributeValue(element, DexpiMetadata.FLUID_CODE);
    DexpiProcessUnit unit =
        new DexpiProcessUnit(uniqueName, componentClass, equipmentEnum, lineNumber, fluidCode);
    processSystem.addUnit(uniqueName, unit);
  }

  private static String prependLineOrFluid(Element element, String baseName) {
    String trimmedBase = baseName == null ? "" : baseName.trim();
    String lineNumber = attributeValue(element, DexpiMetadata.LINE_NUMBER);
    if (!isBlank(lineNumber)) {
      return lineNumber.trim() + "-" + trimmedBase;
    }
    String fluidCode = attributeValue(element, DexpiMetadata.FLUID_CODE);
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

  private static String attributeValue(Element element, String attributeName) {
    return firstNonEmpty(getGenericAttribute(element, attributeName),
        findAttributeInAncestors(element, attributeName));
  }

  private static void applyStreamMetadata(Element element, DexpiStream stream) {
    applyNumericAttribute(element, DexpiMetadata.OPERATING_PRESSURE_VALUE,
        DexpiMetadata.OPERATING_PRESSURE_UNIT, stream::setPressure,
        DexpiMetadata.DEFAULT_PRESSURE_UNIT);
    applyNumericAttribute(element, DexpiMetadata.OPERATING_TEMPERATURE_VALUE,
        DexpiMetadata.OPERATING_TEMPERATURE_UNIT, stream::setTemperature,
        DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
    applyNumericAttribute(element, DexpiMetadata.OPERATING_FLOW_VALUE,
        DexpiMetadata.OPERATING_FLOW_UNIT, stream::setFlowRate, DexpiMetadata.DEFAULT_FLOW_UNIT);
  }

  private static void applyNumericAttribute(Element element, String valueAttribute,
      String unitAttribute, BiConsumer<Double, String> consumer, String defaultUnit) {
    String valueText = firstNonEmpty(getGenericAttribute(element, valueAttribute),
        findAttributeInAncestors(element, valueAttribute));
    Double value = parseNumeric(valueText);
    if (value == null) {
      return;
    }
    String unit = firstNonEmpty(getGenericAttribute(element, unitAttribute),
        findAttributeInAncestors(element, unitAttribute), defaultUnit);
    consumer.accept(value, unit);
  }

  private static Double parseNumeric(String valueText) {
    if (isBlank(valueText)) {
      return null;
    }
    String trimmed = valueText.trim();
    try {
      return Double.parseDouble(trimmed);
    } catch (NumberFormatException ex) {
      int spaceIndex = trimmed.indexOf(' ');
      if (spaceIndex > 0) {
        String candidate = trimmed.substring(0, spaceIndex);
        try {
          return Double.parseDouble(candidate);
        } catch (NumberFormatException ignored) {
          return null;
        }
      }
      return null;
    }
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
