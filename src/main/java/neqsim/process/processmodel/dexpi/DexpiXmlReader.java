package neqsim.process.processmodel.dexpi;

import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Utility for reading DEXPI XML files and converting them into NeqSim process models.
 *
 * <p>
 * This reader recognises major equipment such as pumps, heat exchangers, tanks and control valves as well as complex
 * reactors, compressors and inline analysers. Piping segments are imported as runnable {@link DexpiStream} units tagged
 * with the source line number.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see <a href="https://dexpi.org/">DEXPI Standard</a>
 */
public final class DexpiXmlReader {
  private static final Logger logger = LogManager.getLogger(DexpiXmlReader.class);

  private static final Map<String, EquipmentEnum> EQUIPMENT_CLASS_MAP;
  private static final Map<String, EquipmentEnum> PIPING_COMPONENT_MAP;

  /** Severity of one structured import diagnostic. */
  public enum ImportDiagnosticSeverity {
    /** Informational evidence that does not describe a loss. */
    INFO,
    /** A source object was unsupported or deliberately skipped. */
    WARNING,
    /** A condition prevented a reliable supported-subset import. */
    ERROR
  }

  /** Immutable evidence for one unsupported or deliberately skipped source object. */
  public static final class ImportDiagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final ImportDiagnosticSeverity severity;
    private final String code;
    private final String elementId;
    private final String componentClass;
    private final String elementName;
    private final String message;

    private ImportDiagnostic(ImportDiagnosticSeverity severity, String code, String elementId, String componentClass,
        String elementName, String message) {
      this.severity = severity;
      this.code = code;
      this.elementId = elementId == null ? "" : elementId;
      this.componentClass = componentClass == null ? "" : componentClass;
      this.elementName = elementName == null ? "" : elementName;
      this.message = message;
    }

    /** @return diagnostic severity */
    public ImportDiagnosticSeverity getSeverity() {
      return severity;
    }

    /** @return stable machine-readable diagnostic code */
    public String getCode() {
      return code;
    }

    /** @return source XML element ID, or an empty string when absent */
    public String getElementId() {
      return elementId;
    }

    /** @return source component class, or an empty string when absent */
    public String getComponentClass() {
      return componentClass;
    }

    /** @return source XML element name */
    public String getElementName() {
      return elementName;
    }

    /** @return human-readable explanation */
    public String getMessage() {
      return message;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("elementId", elementId);
      result.put("componentClass", componentClass);
      result.put("elementName", elementName);
      result.put("message", message);
      return result;
    }
  }

  /** Reconstructed process plus deterministic supported-subset import evidence. */
  public static final class ImportResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final ProcessSystem processSystem;
    private final List<ImportDiagnostic> diagnostics;

    private ImportResult(ProcessSystem processSystem, List<ImportDiagnostic> diagnostics) {
      this.processSystem = processSystem;
      this.diagnostics = Collections.unmodifiableList(new ArrayList<ImportDiagnostic>(diagnostics));
    }

    /** @return reconstructed process system using the same behavior as {@link #read(File)} */
    public ProcessSystem getProcessSystem() {
      return processSystem;
    }

    /** @return immutable diagnostics in deterministic source-document order */
    public List<ImportDiagnostic> getDiagnostics() {
      return diagnostics;
    }

    /** @return whether unsupported or deliberately skipped source content was observed */
    public boolean hasLosses() {
      for (ImportDiagnostic diagnostic : diagnostics) {
        if (diagnostic.getSeverity() != ImportDiagnosticSeverity.INFO) {
          return true;
        }
      }
      return false;
    }

    /** @return whether any import diagnostic has error severity */
    public boolean hasErrors() {
      for (ImportDiagnostic diagnostic : diagnostics) {
        if (diagnostic.getSeverity() == ImportDiagnosticSeverity.ERROR) {
          return true;
        }
      }
      return false;
    }

    /** @return deterministic automation-friendly representation excluding live simulation state */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_dexpi_proteus_import.v1");
      result.put("profile", "Proteus-compatible DEXPI Plant/P&ID 4.1.1 supported subset");
      result.put("importedUnitCount", Integer.valueOf(processSystem.getAllUnitNames().size()));
      result.put("hasLosses", Boolean.valueOf(hasLosses()));
      result.put("hasErrors", Boolean.valueOf(hasErrors()));
      List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
      for (ImportDiagnostic diagnostic : diagnostics) {
        diagnosticMaps.add(diagnostic.toMap());
      }
      result.put("diagnostics", diagnosticMaps);
      return result;
    }

    /** @return deterministic pretty-printed JSON evidence */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  static {
    // Load from properties files; fall back to built-in defaults if load fails
    Map<String, EquipmentEnum> equipFromProps = DexpiMappingLoader.loadEquipmentMapping();
    Map<String, EquipmentEnum> pipingFromProps = DexpiMappingLoader.loadPipingComponentMapping();

    if (equipFromProps.isEmpty()) {
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
    } else {
      EQUIPMENT_CLASS_MAP = equipFromProps;
    }

    if (pipingFromProps.isEmpty()) {
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
    } else {
      PIPING_COMPONENT_MAP = pipingFromProps;
    }
  }

  private DexpiXmlReader() {
  }

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
   * Reads the provided DEXPI XML file and returns a populated {@link ProcessSystem} using a template {@link Stream} for
   * generated piping segments.
   *
   * @param file DEXPI XML file
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for generated piping
   * segments. If {@code null}, a methane/ethane default is used.
   * @return a process system populated with units found in the XML
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static ProcessSystem read(File file, Stream templateStream) throws IOException, DexpiXmlReaderException {
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
  public static ProcessSystem read(InputStream inputStream) throws IOException, DexpiXmlReaderException {
    return read(inputStream, null);
  }

  /**
   * Reads the provided DEXPI XML stream and returns a populated {@link ProcessSystem} using a template {@link Stream}
   * for any generated piping segments.
   *
   * @param inputStream stream containing DEXPI XML data
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for generated piping
   * segments. If {@code null}, a methane/ethane default is used.
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
   * Reads a DEXPI XML file and returns the reconstructed process together with structured evidence for unsupported or
   * skipped source objects.
   *
   * @param file DEXPI XML file
   * @return reconstructed process and deterministic import diagnostics
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static ImportResult readWithDiagnostics(File file) throws IOException, DexpiXmlReaderException {
    return readWithDiagnostics(file, null);
  }

  /**
   * Reads a DEXPI XML file with a template stream and returns structured supported-subset evidence.
   *
   * @param file DEXPI XML file
   * @param templateStream optional template for generated piping segments
   * @return reconstructed process and deterministic import diagnostics
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static ImportResult readWithDiagnostics(File file, Stream templateStream)
      throws IOException, DexpiXmlReaderException {
    Objects.requireNonNull(file, "file");
    logger.info("Reading DEXPI XML file with diagnostics: {}", file.getAbsolutePath());
    try (InputStream inputStream = new FileInputStream(file)) {
      return readWithDiagnostics(inputStream, templateStream);
    }
  }

  /**
   * Reads a DEXPI XML stream and returns structured supported-subset import evidence.
   *
   * @param inputStream stream containing DEXPI XML data
   * @return reconstructed process and deterministic import diagnostics
   * @throws IOException if the stream cannot be read
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static ImportResult readWithDiagnostics(InputStream inputStream) throws IOException, DexpiXmlReaderException {
    return readWithDiagnostics(inputStream, null);
  }

  /**
   * Reads a DEXPI XML stream with a template stream and returns structured supported-subset evidence.
   *
   * @param inputStream stream containing DEXPI XML data
   * @param templateStream optional template for generated piping segments
   * @return reconstructed process and deterministic import diagnostics
   * @throws IOException if the stream cannot be read
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static ImportResult readWithDiagnostics(InputStream inputStream, Stream templateStream)
      throws IOException, DexpiXmlReaderException {
    ProcessSystem processSystem = new ProcessSystem("DEXPI process");
    List<ImportDiagnostic> diagnostics = new ArrayList<ImportDiagnostic>();
    loadInternal(inputStream, processSystem, templateStream, false, diagnostics);
    return new ImportResult(processSystem, diagnostics);
  }

  /**
   * Populates an existing {@link ProcessSystem} with units parsed from a DEXPI XML file.
   *
   * @param file XML file to parse
   * @param processSystem target process system
   * @throws IOException if reading fails
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static void load(File file, ProcessSystem processSystem) throws IOException, DexpiXmlReaderException {
    load(file, processSystem, null);
  }

  /**
   * Populates an existing {@link ProcessSystem} with units parsed from a DEXPI XML file.
   *
   * @param file XML file to parse
   * @param processSystem target process system
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for generated piping
   * segments. If {@code null}, a methane/ethane default is used.
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
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for generated piping
   * segments. If {@code null}, a methane/ethane default is used.
   * @throws IOException if reading fails
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static void load(InputStream inputStream, ProcessSystem processSystem, Stream templateStream)
      throws IOException, DexpiXmlReaderException {
    load(inputStream, processSystem, templateStream, false);
  }

  /**
   * Populates an existing {@link ProcessSystem} with units parsed from a DEXPI XML stream.
   *
   * @param inputStream XML input stream
   * @param processSystem target process system
   * @param templateStream stream providing default fluid, temperature, pressure, and flow rate for generated piping
   * segments. If {@code null}, a methane/ethane default is used.
   * @param namespaceAware whether to enable namespace-aware XML parsing
   * @throws IOException if reading fails
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static void load(InputStream inputStream, ProcessSystem processSystem, Stream templateStream,
      boolean namespaceAware) throws IOException, DexpiXmlReaderException {
    loadInternal(inputStream, processSystem, templateStream, namespaceAware, null);
  }

  private static void loadInternal(InputStream inputStream, ProcessSystem processSystem, Stream templateStream,
      boolean namespaceAware, List<ImportDiagnostic> diagnostics) throws IOException, DexpiXmlReaderException {
    Objects.requireNonNull(inputStream, "inputStream");
    Objects.requireNonNull(processSystem, "processSystem");

    Document document = parseDocument(inputStream, namespaceAware);
    if (document == null) {
      return;
    }

    Stream streamTemplate = templateOrDefault(templateStream);
    if (diagnostics != null && templateStream == null
        && document.getElementsByTagName("PipingNetworkSegment").getLength() > 0) {
      diagnostics.add(new ImportDiagnostic(ImportDiagnosticSeverity.WARNING, "DEXPI_IMPORT_DEFAULT_TEMPLATE_USED", "",
          "", "PlantModel",
          "No template stream was supplied; imported piping segments use the reader's synthetic default fluid and state"));
    }

    addUnits(document, processSystem, "Equipment", EQUIPMENT_CLASS_MAP, DexpiMetadata.TAG_NAME, diagnostics);
    addUnits(document, processSystem, "PipingComponent", PIPING_COMPONENT_MAP, "PipingComponentNumberAssignmentClass",
        diagnostics);
    addPipingSegments(document, processSystem, streamTemplate, diagnostics);
  }

  /**
   * Reads instrument metadata from a DEXPI XML file. This returns structured {@link DexpiInstrumentInfo} records that
   * describe the P&amp;ID instrumentation without creating live transmitter/controller objects (which require connected
   * streams).
   *
   * @param file DEXPI XML file
   * @return list of instrument info records parsed from the file
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the file cannot be parsed
   */
  public static List<DexpiInstrumentInfo> readInstruments(File file) throws IOException, DexpiXmlReaderException {
    Objects.requireNonNull(file, "file");
    try (InputStream inputStream = new FileInputStream(file)) {
      return readInstruments(inputStream);
    }
  }

  /**
   * Reads instrument metadata from a DEXPI XML stream.
   *
   * @param inputStream stream containing DEXPI XML data
   * @return list of instrument info records parsed from the stream
   * @throws IOException if the stream cannot be read
   * @throws DexpiXmlReaderException if the stream cannot be parsed
   */
  public static List<DexpiInstrumentInfo> readInstruments(InputStream inputStream)
      throws IOException, DexpiXmlReaderException {
    Objects.requireNonNull(inputStream, "inputStream");
    Document document = parseDocument(inputStream, false);
    if (document == null) {
      return Collections.emptyList();
    }
    return parseInstruments(document);
  }

  /**
   * Parses instruments from an already-parsed DEXPI XML Document. Package-visible for use by
   * {@link DexpiSimulationBuilder}.
   *
   * @param document the parsed XML document
   * @return list of instrument info records
   */
  static List<DexpiInstrumentInfo> parseInstrumentsFromDocument(Document document) {
    return parseInstruments(document);
  }

  /**
   * Parses all ProcessInstrumentationFunction elements from the document, resolving loop and actuator associations.
   *
   * @param document the parsed XML document
   * @return list of instrument info records
   */
  private static List<DexpiInstrumentInfo> parseInstruments(Document document) {
    // Build a map of loop ID -> loop element for association resolution
    Map<String, String> loopNumbers = new HashMap<>();
    NodeList loopNodes = document.getElementsByTagName("InstrumentationLoopFunction");
    for (int i = 0; i < loopNodes.getLength(); i++) {
      Node node = loopNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element loopElement = (Element) node;
      String loopId = loopElement.getAttribute("ID");
      String loopNum = getGenericAttribute(loopElement, DexpiMetadata.LOOP_NUMBER);
      if (loopNum == null) {
        loopNum = loopId;
      }
      // Find which instrument IDs are in this loop via Association elements
      List<Element> associations = directChildElements(loopElement, "Association");
      for (Element assoc : associations) {
        String itemId = assoc.getAttribute("ItemID");
        if (!isBlank(itemId)) {
          loopNumbers.put(itemId, loopNum);
        }
      }
    }

    // Build a map of instrument ID -> actuating function tag
    Map<String, String> actuatingTags = new HashMap<>();
    NodeList allElements = document.getElementsByTagName("*");
    for (int i = 0; i < allElements.getLength(); i++) {
      Node node = allElements.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element element = (Element) node;
      if ("ActuatingFunction".equals(element.getTagName())
          || "ActuatingFunction".equals(element.getAttribute("ComponentClass"))) {
        String afId = element.getAttribute("ID");
        String afNumber = getGenericAttribute(element, DexpiMetadata.ACTUATING_FUNCTION_NUMBER);
        if (afNumber == null) {
          afNumber = afId;
        }
        // Find parent instrument via traversal
        Node parentNode = element.getParentNode();
        while (parentNode != null && parentNode.getNodeType() == Node.ELEMENT_NODE) {
          Element parentEl = (Element) parentNode;
          if ("ProcessInstrumentationFunction".equals(parentEl.getTagName())) {
            actuatingTags.put(parentEl.getAttribute("ID"), afNumber);
            break;
          }
          parentNode = parentNode.getParentNode();
        }
      }
    }

    // Parse all ProcessInstrumentationFunction elements
    List<DexpiInstrumentInfo> instruments = new ArrayList<>();
    NodeList pifNodes = document.getElementsByTagName("ProcessInstrumentationFunction");
    for (int i = 0; i < pifNodes.getLength(); i++) {
      Node node = pifNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      // Skip shape definitions inside ShapeCatalogue
      if (isInsideShapeCatalogue(node)) {
        continue;
      }
      Element pif = (Element) node;
      String id = pif.getAttribute("ID");
      String category = getGenericAttribute(pif, DexpiMetadata.INSTRUMENTATION_CATEGORY);
      String functions = getGenericAttribute(pif, DexpiMetadata.INSTRUMENTATION_FUNCTIONS);
      String number = getGenericAttribute(pif, DexpiMetadata.INSTRUMENTATION_NUMBER);
      String tagName = getGenericAttribute(pif, DexpiMetadata.TAG_NAME);
      String unit = getGenericAttribute(pif, "MeasurementUnit");

      if (tagName == null) {
        tagName = (category != null ? category : "") + (functions != null ? functions : "") + " "
            + (number != null ? number : id);
      }

      String loopNum = loopNumbers.get(id);
      String actuator = actuatingTags.get(id);

      instruments
          .add(new DexpiInstrumentInfo(id, tagName.trim(), category, functions, number, loopNum, unit, actuator));
    }

    logger.info("Parsed {} instruments from DEXPI XML", instruments.size());
    return instruments;
  }

  private static Document parseDocument(InputStream inputStream, boolean nsAware) throws DexpiXmlReaderException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setNamespaceAware(nsAware);
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
    } catch (ParserConfigurationException | SAXException | IOException | IllegalArgumentException e) {
      throw new DexpiXmlReaderException("Unable to parse DEXPI XML", e);
    }
  }

  private static void addUnits(Document document, ProcessSystem processSystem, String tagName,
      Map<String, EquipmentEnum> equipmentMap, String nameAttribute, List<ImportDiagnostic> diagnostics) {
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
          if (diagnostics != null) {
            boolean missingClass = isBlank(componentClass);
            diagnostics.add(new ImportDiagnostic(ImportDiagnosticSeverity.WARNING,
                missingClass ? "DEXPI_IMPORT_COMPONENT_CLASS_MISSING" : "DEXPI_IMPORT_COMPONENT_UNSUPPORTED",
                element.getAttribute("ID"), componentClass, element.getTagName(),
                missingClass ? "Source object was skipped because ComponentClass is missing"
                    : "Source object was skipped because its ComponentClass is not supported by this reader"));
          }
          continue;
        }

        String baseName = firstNonEmpty(attributeValue(element, nameAttribute),
            attributeValue(element, DexpiMetadata.TAG_NAME), element.getAttribute("ID"));
        addDexpiUnit(processSystem, element, equipmentEnum, baseName, element.getAttribute("ComponentClass"));
        totalUnits++;
      }
    }
    logger.info("Added {} units from {} elements", totalUnits, tagName);
  }

  private static void addPipingSegments(Document document, ProcessSystem processSystem, Stream templateStream,
      List<ImportDiagnostic> diagnostics) {
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
      addPipingSegmentDiagnostics(element, diagnostics);
      addDexpiStream(processSystem, element, templateStream, baseName);
    }
  }

  private static void addPipingSegmentDiagnostics(Element element, List<ImportDiagnostic> diagnostics) {
    if (diagnostics == null) {
      return;
    }

    String segmentNumber = attributeValue(element, DexpiMetadata.SEGMENT_NUMBER);
    if (isBlank(segmentNumber) && isBlank(element.getAttribute("ID"))) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_SEGMENT_IDENTITY_MISSING", "Piping segment has neither an ID nor a segment-number assignment");
    }
    if (isBlank(element.getAttribute("ComponentClass"))) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING, "DEXPI_IMPORT_SEGMENT_CLASS_MISSING",
          "Piping segment ComponentClass is missing");
    }
    addMissingPipingMetadataDiagnostic(element, diagnostics, DexpiMetadata.LINE_NUMBER,
        "DEXPI_IMPORT_LINE_NUMBER_MISSING", "Source line number is missing");
    addMissingPipingMetadataDiagnostic(element, diagnostics, DexpiMetadata.FLUID_CODE,
        "DEXPI_IMPORT_SERVICE_CODE_MISSING", "Source service or fluid code is missing");
    if (isBlank(firstNonEmpty(attributeValue(element, DexpiMetadata.NOMINAL_DIAMETER_REPRESENTATION),
        attributeValue(element, DexpiMetadata.LINE_SIZE)))) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING, "DEXPI_IMPORT_NOMINAL_SIZE_MISSING",
          "Source nominal-size representation is missing; no DN, NPS, or schedule is inferred");
    }
    if (isBlank(firstNonEmpty(attributeValue(element, DexpiMetadata.PIPING_CLASS_CODE_ASSIGNMENT),
        attributeValue(element, DexpiMetadata.PIPING_CLASS_CODE)))) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING, "DEXPI_IMPORT_PIPING_CLASS_MISSING",
          "Source piping-class code is missing");
    }
    if (isBlank(firstNonEmpty(attributeValue(element, DexpiMetadata.INSULATION_TYPE_ASSIGNMENT),
        attributeValue(element, DexpiMetadata.INSULATION_CODE)))) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING, "DEXPI_IMPORT_INSULATION_UNSPECIFIED",
          "Source insulation is unspecified; absence is not interpreted as uninsulated service");
    }

    addOperatingValueDiagnostic(element, diagnostics, DexpiMetadata.OPERATING_PRESSURE_VALUE,
        DexpiMetadata.OPERATING_PRESSURE_UNIT, DexpiMetadata.DEFAULT_PRESSURE_UNIT, "PRESSURE");
    addOperatingValueDiagnostic(element, diagnostics, DexpiMetadata.OPERATING_TEMPERATURE_VALUE,
        DexpiMetadata.OPERATING_TEMPERATURE_UNIT, DexpiMetadata.DEFAULT_TEMPERATURE_UNIT, "TEMPERATURE");
    addOperatingValueDiagnostic(element, diagnostics, DexpiMetadata.OPERATING_FLOW_VALUE,
        DexpiMetadata.OPERATING_FLOW_UNIT, DexpiMetadata.DEFAULT_FLOW_UNIT, "FLOW");
  }

  private static void addMissingPipingMetadataDiagnostic(Element element, List<ImportDiagnostic> diagnostics,
      String attributeName, String code, String message) {
    if (isBlank(attributeValue(element, attributeName))) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING, code, message);
    }
  }

  private static void addOperatingValueDiagnostic(Element element, List<ImportDiagnostic> diagnostics,
      String valueAttribute, String unitAttribute, String defaultUnit, String quantity) {
    String valueText = firstNonEmpty(getGenericAttribute(element, valueAttribute),
        findAttributeInAncestors(element, valueAttribute));
    if (isBlank(valueText)) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.INFO,
          "DEXPI_IMPORT_" + quantity + "_FROM_TEMPLATE",
          "Source operating " + quantity.toLowerCase() + " is missing; the template-stream value is retained");
      return;
    }
    if (parseNumeric(valueText) == null) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_" + quantity + "_INVALID",
          "Source operating " + quantity.toLowerCase() + " value is invalid; the template-stream value is retained");
      return;
    }
    String unit = firstNonEmpty(getGenericAttribute(element, unitAttribute),
        findAttributeInAncestors(element, unitAttribute));
    if (isBlank(unit)) {
      addPipingDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_" + quantity + "_UNIT_DEFAULTED",
          "Source operating " + quantity.toLowerCase() + " unit is missing; " + defaultUnit + " is used");
    }
  }

  private static void addPipingDiagnostic(Element element, List<ImportDiagnostic> diagnostics,
      ImportDiagnosticSeverity severity, String code, String message) {
    diagnostics.add(new ImportDiagnostic(severity, code, element.getAttribute("ID"),
        element.getAttribute("ComponentClass"), element.getTagName(), message));
  }

  private static void addDexpiStream(ProcessSystem processSystem, Element element, Stream templateStream,
      String baseName) {
    String contextualName = prependLineOrFluid(element, baseName);
    String uniqueName = ensureUniqueName(processSystem, contextualName);
    String lineNumber = attributeValue(element, DexpiMetadata.LINE_NUMBER);
    String fluidCode = attributeValue(element, DexpiMetadata.FLUID_CODE);

    SystemInterface baseFluid = templateStream.getThermoSystem();
    SystemInterface fluid = baseFluid == null ? createDefaultFluid() : baseFluid.clone();

    DexpiStream stream = new DexpiStream(uniqueName, fluid, element.getAttribute("ComponentClass"), lineNumber,
        fluidCode);
    stream.setNominalDiameterRepresentation(
        firstNonEmpty(attributeValue(element, DexpiMetadata.NOMINAL_DIAMETER_REPRESENTATION),
            attributeValue(element, DexpiMetadata.LINE_SIZE)));
    stream.setPipingClassCode(firstNonEmpty(attributeValue(element, DexpiMetadata.PIPING_CLASS_CODE_ASSIGNMENT),
        attributeValue(element, DexpiMetadata.PIPING_CLASS_CODE)));
    stream.setInsulationType(firstNonEmpty(attributeValue(element, DexpiMetadata.INSULATION_TYPE_ASSIGNMENT),
        attributeValue(element, DexpiMetadata.INSULATION_CODE)));
    stream.setSpecification(templateStream.getSpecification());
    stream.setPressure(templateStream.getPressure(DexpiMetadata.DEFAULT_PRESSURE_UNIT),
        DexpiMetadata.DEFAULT_PRESSURE_UNIT);
    stream.setTemperature(templateStream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT),
        DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
    stream.setFlowRate(templateStream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT), DexpiMetadata.DEFAULT_FLOW_UNIT);

    applyStreamMetadata(element, stream);
    processSystem.addUnit(uniqueName, stream);
  }

  private static void addDexpiUnit(ProcessSystem processSystem, Element element, EquipmentEnum equipmentEnum,
      String baseName, String componentClass) {
    String contextualName = prependLineOrFluid(element, baseName);
    String uniqueName = ensureUniqueName(processSystem, contextualName);
    String lineNumber = attributeValue(element, DexpiMetadata.LINE_NUMBER);
    String fluidCode = attributeValue(element, DexpiMetadata.FLUID_CODE);
    DexpiProcessUnit unit = new DexpiProcessUnit(uniqueName, componentClass, equipmentEnum, lineNumber, fluidCode);
    unit.setDexpiId(element.getAttribute("ID"));

    // Extract sizing attributes from GenericAttributes
    for (String sizingAttr : DexpiMetadata.sizingAttributes()) {
      String value = attributeValue(element, sizingAttr);
      if (value != null) {
        unit.setSizingAttribute(sizingAttr, value);
      }
    }

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
    return firstNonEmpty(getGenericAttribute(element, attributeName), findAttributeInAncestors(element, attributeName));
  }

  private static void applyStreamMetadata(Element element, DexpiStream stream) {
    applyNumericAttribute(element, DexpiMetadata.OPERATING_PRESSURE_VALUE, DexpiMetadata.OPERATING_PRESSURE_UNIT,
        stream::setPressure, DexpiMetadata.DEFAULT_PRESSURE_UNIT);
    applyNumericAttribute(element, DexpiMetadata.OPERATING_TEMPERATURE_VALUE, DexpiMetadata.OPERATING_TEMPERATURE_UNIT,
        stream::setTemperature, DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
    applyNumericAttribute(element, DexpiMetadata.OPERATING_FLOW_VALUE, DexpiMetadata.OPERATING_FLOW_UNIT,
        stream::setFlowRate, DexpiMetadata.DEFAULT_FLOW_UNIT);
  }

  private static void applyNumericAttribute(Element element, String valueAttribute, String unitAttribute,
      BiConsumer<Double, String> consumer, String defaultUnit) {
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
      if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(((Element) child).getTagName())) {
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

  /**
   * Checks whether the given node is inside a ShapeCatalogue element.
   *
   * @param node the XML node to check
   * @return true if the node has a ShapeCatalogue ancestor
   */
  private static boolean isInsideShapeCatalogue(Node node) {
    Node parent = node.getParentNode();
    while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
      if ("ShapeCatalogue".equals(((Element) parent).getTagName())) {
        return true;
      }
      parent = parent.getParentNode();
    }
    return false;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
