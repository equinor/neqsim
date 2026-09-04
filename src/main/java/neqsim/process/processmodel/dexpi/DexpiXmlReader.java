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
    private final List<DexpiInstrumentInfo> instruments;
    private final List<DexpiInstrumentationLoopInfo> instrumentationLoops;
    private final List<DexpiInformationFlowInfo> informationFlows;
    private final List<DexpiConnectionInfo> connections;
    private final List<DexpiConnectionEndpointInfo> connectionEndpoints;
    private final List<DexpiConnectionComponentInfo> connectionComponents;
    private final List<DexpiConnectionCycleInfo> connectionCycles;
    private final List<DexpiConnectionCycleTransitionInfo> connectionCycleTransitions;
    private final List<ImportDiagnostic> diagnostics;

    private ImportResult(ProcessSystem processSystem, List<DexpiInstrumentInfo> instruments,
        List<DexpiInstrumentationLoopInfo> instrumentationLoops, List<DexpiInformationFlowInfo> informationFlows,
        List<DexpiConnectionInfo> connections, List<DexpiConnectionEndpointInfo> connectionEndpoints,
        List<DexpiConnectionComponentInfo> connectionComponents, List<DexpiConnectionCycleInfo> connectionCycles,
        List<DexpiConnectionCycleTransitionInfo> connectionCycleTransitions, List<ImportDiagnostic> diagnostics) {
      this.processSystem = processSystem;
      this.instruments = Collections.unmodifiableList(new ArrayList<DexpiInstrumentInfo>(instruments));
      this.instrumentationLoops = Collections
          .unmodifiableList(new ArrayList<DexpiInstrumentationLoopInfo>(instrumentationLoops));
      this.informationFlows = Collections.unmodifiableList(new ArrayList<DexpiInformationFlowInfo>(informationFlows));
      this.connections = Collections.unmodifiableList(new ArrayList<DexpiConnectionInfo>(connections));
      this.connectionEndpoints = Collections
          .unmodifiableList(new ArrayList<DexpiConnectionEndpointInfo>(connectionEndpoints));
      this.connectionComponents = Collections
          .unmodifiableList(new ArrayList<DexpiConnectionComponentInfo>(connectionComponents));
      this.connectionCycles = Collections.unmodifiableList(new ArrayList<DexpiConnectionCycleInfo>(connectionCycles));
      this.connectionCycleTransitions = Collections
          .unmodifiableList(new ArrayList<DexpiConnectionCycleTransitionInfo>(connectionCycleTransitions));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<ImportDiagnostic>(diagnostics));
    }

    /** @return reconstructed process system using the same behavior as {@link #read(File)} */
    public ProcessSystem getProcessSystem() {
      return processSystem;
    }

    /**
     * Returns the source instrument inventory parsed from the same XML document as the process and diagnostics.
     *
     * <p>
     * These are metadata records only. Import does not construct live transmitters, controllers, or control intent.
     * </p>
     *
     * @return immutable instrument records in source-document order
     */
    public List<DexpiInstrumentInfo> getInstruments() {
      return instruments;
    }

    /**
     * Returns instrumentation-loop grouping evidence in source-document order.
     *
     * <p>
     * Membership occurrences retain only explicit source references and resolution evidence. They do not construct live
     * control topology, infer control intent, verify loop function, or classify safeguards.
     * </p>
     *
     * @return immutable instrumentation-loop records
     */
    public List<DexpiInstrumentationLoopInfo> getInstrumentationLoops() {
      return instrumentationLoops;
    }

    /**
     * Returns supported instrumentation information-flow occurrences in source-document order.
     *
     * <p>
     * These records retain logical references and resolution evidence only. They do not construct live control
     * topology, infer control intent, or verify loop function.
     * </p>
     *
     * @return immutable signal-line and measuring-line evidence
     */
    public List<DexpiInformationFlowInfo> getInformationFlows() {
      return informationFlows;
    }

    /**
     * Returns the source material-connection inventory without reconstructing live process topology.
     *
     * @return immutable connection records in source-document order
     */
    public List<DexpiConnectionInfo> getConnections() {
      return connections;
    }

    /**
     * Returns distinct non-empty connection endpoints in first-reference order.
     *
     * <p>
     * Counts and connection IDs preserve every source occurrence, including parallel connections. They are evidence
     * only and do not classify branches or reconstruct live process topology.
     * </p>
     *
     * @return immutable endpoint-incidence records
     */
    public List<DexpiConnectionEndpointInfo> getConnectionEndpoints() {
      return connectionEndpoints;
    }

    /**
     * Returns weakly connected groups of explicit non-empty material-connection endpoint references.
     *
     * <p>
     * Components preserve source evidence only. They do not establish hydraulic continuity, fitting identity, process
     * intent, or live process topology.
     * </p>
     *
     * @return immutable source-reference components in first-endpoint order
     */
    public List<DexpiConnectionComponentInfo> getConnectionComponents() {
      return connectionComponents;
    }

    /**
     * Returns directed strongly connected groups from explicit non-empty material-connection references.
     *
     * <p>
     * A group is included when it contains more than one endpoint, or when a single endpoint has an explicit
     * self-reference. This is source evidence only and does not establish a hydraulic recycle, process intent,
     * convergence behavior, or live process topology.
     * </p>
     *
     * @return immutable directed-cycle evidence in first-endpoint order
     */
    public List<DexpiConnectionCycleInfo> getConnectionCycles() {
      return connectionCycles;
    }

    /**
     * Returns source-ordered connection occurrences crossing directed-cycle boundaries.
     *
     * <p>
     * Each occurrence appears once even when it leaves one cyclic group and enters another. These records preserve
     * source evidence only; they do not establish hydraulic continuity, a physical recycle, or live process topology.
     * </p>
     *
     * @return immutable cycle-transition evidence in source-document order
     */
    public List<DexpiConnectionCycleTransitionInfo> getConnectionCycleTransitions() {
      return connectionCycleTransitions;
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
      result.put("instrumentCount", Integer.valueOf(instruments.size()));
      result.put("instrumentationLoopCount", Integer.valueOf(instrumentationLoops.size()));
      List<Map<String, Object>> instrumentationLoopMaps = new ArrayList<Map<String, Object>>();
      for (DexpiInstrumentationLoopInfo instrumentationLoop : instrumentationLoops) {
        instrumentationLoopMaps.add(instrumentationLoop.toMap());
      }
      result.put("instrumentationLoops", instrumentationLoopMaps);
      result.put("informationFlowCount", Integer.valueOf(informationFlows.size()));
      List<Map<String, Object>> informationFlowMaps = new ArrayList<Map<String, Object>>();
      for (DexpiInformationFlowInfo informationFlow : informationFlows) {
        informationFlowMaps.add(informationFlow.toMap());
      }
      result.put("informationFlows", informationFlowMaps);
      result.put("connectionCount", Integer.valueOf(connections.size()));
      List<Map<String, Object>> connectionMaps = new ArrayList<Map<String, Object>>();
      for (DexpiConnectionInfo connection : connections) {
        connectionMaps.add(connection.toMap());
      }
      result.put("connections", connectionMaps);
      result.put("connectionEndpointCount", Integer.valueOf(connectionEndpoints.size()));
      List<Map<String, Object>> endpointMaps = new ArrayList<Map<String, Object>>();
      for (DexpiConnectionEndpointInfo endpoint : connectionEndpoints) {
        endpointMaps.add(endpoint.toMap());
      }
      result.put("connectionEndpoints", endpointMaps);
      result.put("connectionComponentCount", Integer.valueOf(connectionComponents.size()));
      List<Map<String, Object>> componentMaps = new ArrayList<Map<String, Object>>();
      for (DexpiConnectionComponentInfo component : connectionComponents) {
        componentMaps.add(component.toMap());
      }
      result.put("connectionComponents", componentMaps);
      result.put("connectionCycleCount", Integer.valueOf(connectionCycles.size()));
      List<Map<String, Object>> cycleMaps = new ArrayList<Map<String, Object>>();
      for (DexpiConnectionCycleInfo cycle : connectionCycles) {
        cycleMaps.add(cycle.toMap());
      }
      result.put("connectionCycles", cycleMaps);
      result.put("connectionCycleTransitionCount", Integer.valueOf(connectionCycleTransitions.size()));
      List<Map<String, Object>> transitionMaps = new ArrayList<Map<String, Object>>();
      for (DexpiConnectionCycleTransitionInfo transition : connectionCycleTransitions) {
        transitionMaps.add(transition.toMap());
      }
      result.put("connectionCycleTransitions", transitionMaps);
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
    List<DexpiInstrumentInfo> instruments = new ArrayList<DexpiInstrumentInfo>();
    List<DexpiInstrumentationLoopInfo> instrumentationLoops = new ArrayList<DexpiInstrumentationLoopInfo>();
    List<DexpiInformationFlowInfo> informationFlows = new ArrayList<DexpiInformationFlowInfo>();
    List<DexpiConnectionInfo> connections = new ArrayList<DexpiConnectionInfo>();
    List<ImportDiagnostic> diagnostics = new ArrayList<ImportDiagnostic>();
    loadInternal(inputStream, processSystem, templateStream, false, diagnostics, instruments, instrumentationLoops,
        informationFlows, connections);
    List<DexpiConnectionEndpointInfo> connectionEndpoints = summarizeConnectionEndpoints(connections);
    List<DexpiConnectionComponentInfo> connectionComponents = summarizeConnectionComponents(connections,
        connectionEndpoints);
    List<DexpiConnectionCycleInfo> connectionCycles = summarizeConnectionCycles(connections, connectionEndpoints,
        connectionComponents);
    List<DexpiConnectionCycleTransitionInfo> connectionCycleTransitions = summarizeConnectionCycleTransitions(
        connections, connectionEndpoints, connectionCycles);
    return new ImportResult(processSystem, instruments, instrumentationLoops, informationFlows, connections,
        connectionEndpoints, connectionComponents, connectionCycles, connectionCycleTransitions, diagnostics);
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
    loadInternal(inputStream, processSystem, templateStream, namespaceAware, null, null, null, null, null);
  }

  private static void loadInternal(InputStream inputStream, ProcessSystem processSystem, Stream templateStream,
      boolean namespaceAware, List<ImportDiagnostic> diagnostics, List<DexpiInstrumentInfo> instruments,
      List<DexpiInstrumentationLoopInfo> instrumentationLoops, List<DexpiInformationFlowInfo> informationFlows,
      List<DexpiConnectionInfo> connections) throws IOException, DexpiXmlReaderException {
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
    if (instruments != null) {
      instruments.addAll(parseInstruments(document));
    }
    if (instrumentationLoops != null) {
      instrumentationLoops.addAll(parseInstrumentationLoops(document));
    }
    if (informationFlows != null) {
      informationFlows.addAll(parseInformationFlows(document));
    }
    if (connections != null) {
      connections.addAll(parseConnections(document, diagnostics));
    }
    addInstrumentationDiagnostics(document, diagnostics);
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

  private static List<DexpiInstrumentationLoopInfo> parseInstrumentationLoops(Document document) {
    List<DexpiInstrumentationLoopInfo> result = new ArrayList<DexpiInstrumentationLoopInfo>();
    Map<String, Element> elementsById = new HashMap<String, Element>();
    NodeList allElements = document.getElementsByTagName("*");
    for (int i = 0; i < allElements.getLength(); i++) {
      Node node = allElements.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || isInsideShapeCatalogue(node)) {
        continue;
      }
      Element element = (Element) node;
      String id = element.getAttribute("ID");
      if (!isBlank(id) && !elementsById.containsKey(id)) {
        elementsById.put(id, element);
      }
    }

    NodeList loopNodes = document.getElementsByTagName("InstrumentationLoopFunction");
    for (int i = 0; i < loopNodes.getLength(); i++) {
      Node node = loopNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || isInsideShapeCatalogue(node)) {
        continue;
      }
      Element loop = (Element) node;
      List<DexpiInstrumentationLoopInfo.Member> members = new ArrayList<DexpiInstrumentationLoopInfo.Member>();
      for (Element association : directChildElements(loop, "Association")) {
        if (!"is a collection including".equals(association.getAttribute("Type"))) {
          continue;
        }
        String memberId = association.getAttribute("ItemID");
        Element member = elementsById.get(memberId);
        members.add(new DexpiInstrumentationLoopInfo.Member(memberId, member != null, elementName(member)));
      }
      result.add(new DexpiInstrumentationLoopInfo(loop.getAttribute("ID"), loop.getAttribute("ComponentClass"),
          getGenericAttribute(loop, DexpiMetadata.LOOP_NUMBER), members));
    }
    return result;
  }

  private static List<DexpiInformationFlowInfo> parseInformationFlows(Document document) {
    List<DexpiInformationFlowInfo> result = new ArrayList<DexpiInformationFlowInfo>();
    Map<String, Element> elementsById = new HashMap<String, Element>();
    NodeList allElements = document.getElementsByTagName("*");
    for (int i = 0; i < allElements.getLength(); i++) {
      Node node = allElements.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || isInsideShapeCatalogue(node)) {
        continue;
      }
      Element element = (Element) node;
      String id = element.getAttribute("ID");
      if (!isBlank(id) && !elementsById.containsKey(id)) {
        elementsById.put(id, element);
      }
    }

    NodeList informationFlowNodes = document.getElementsByTagName("InformationFlow");
    for (int i = 0; i < informationFlowNodes.getLength(); i++) {
      Node node = informationFlowNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || isInsideShapeCatalogue(node)) {
        continue;
      }
      Element element = (Element) node;
      String componentClass = element.getAttribute("ComponentClass");
      DexpiInformationFlowInfo.Kind kind;
      if ("SignalLineFunction".equals(componentClass)) {
        kind = DexpiInformationFlowInfo.Kind.SIGNAL_LINE;
      } else if ("MeasuringLineFunction".equals(componentClass)) {
        kind = DexpiInformationFlowInfo.Kind.MEASURING_LINE;
      } else {
        continue;
      }

      String sourceId = associationItemId(element, "has logical start");
      String targetId = associationItemId(element, "has logical end");
      String attachmentId = associationItemId(element, "is attached to");
      Element sourceElement = elementsById.get(sourceId);
      Element targetElement = elementsById.get(targetId);
      Element attachmentElement = elementsById.get(attachmentId);
      result.add(new DexpiInformationFlowInfo(element.getAttribute("ID"), kind, componentClass, sourceId,
          sourceElement != null, elementName(sourceElement), targetId, targetElement != null,
          elementName(targetElement), attachmentId, attachmentElement != null, elementName(attachmentElement),
          getGenericAttribute(element, "SignalConveyingTypeSpecialization")));
    }
    return result;
  }

  private static String associationItemId(Element element, String associationType) {
    for (Element association : directChildElements(element, "Association")) {
      if (associationType.equals(association.getAttribute("Type"))) {
        return association.getAttribute("ItemID");
      }
    }
    return "";
  }

  private static String elementName(Element element) {
    return element == null ? "" : element.getTagName();
  }

  private static List<DexpiConnectionInfo> parseConnections(Document document, List<ImportDiagnostic> diagnostics) {
    List<DexpiConnectionInfo> connections = new ArrayList<DexpiConnectionInfo>();
    NodeList allElements = document.getElementsByTagName("*");
    Map<String, Element> elementsById = new HashMap<String, Element>();
    for (int i = 0; i < allElements.getLength(); i++) {
      Node node = allElements.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || isInsideShapeCatalogue(node)) {
        continue;
      }
      Element element = (Element) node;
      String id = element.getAttribute("ID");
      if (!isBlank(id) && !elementsById.containsKey(id)) {
        elementsById.put(id, element);
      }
    }

    Map<String, Integer> segmentOrdinals = new LinkedHashMap<String, Integer>();
    Map<String, Integer> sourceIdCounts = new LinkedHashMap<String, Integer>();
    NodeList connectionNodes = document.getElementsByTagName("Connection");
    for (int i = 0; i < connectionNodes.getLength(); i++) {
      Node node = connectionNodes.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || isInsideShapeCatalogue(node)) {
        continue;
      }
      Element connection = (Element) node;
      Element segment = findAncestorElement(connection, "PipingNetworkSegment");
      String segmentId = "";
      if (segment != null) {
        segmentId = firstNonEmpty(segment.getAttribute("ID"), attributeValue(segment, DexpiMetadata.SEGMENT_NUMBER));
        if (segmentId == null) {
          segmentId = "";
        }
      }

      String segmentKey = isBlank(segmentId) ? "unscoped" : segmentId;
      Integer previousOrdinal = segmentOrdinals.get(segmentKey);
      int ordinal = previousOrdinal == null ? 1 : previousOrdinal.intValue() + 1;
      segmentOrdinals.put(segmentKey, Integer.valueOf(ordinal));

      String sourceId = connection.getAttribute("ID");
      String evidenceId;
      if (isBlank(sourceId)) {
        evidenceId = segmentKey + "/connection-" + ordinal;
        addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.INFO,
            "DEXPI_IMPORT_CONNECTION_ID_SYNTHESIZED",
            "Connection has no source ID; deterministic evidence ID '" + evidenceId + "' is used");
      } else {
        Integer previousCount = sourceIdCounts.get(sourceId);
        int count = previousCount == null ? 1 : previousCount.intValue() + 1;
        sourceIdCounts.put(sourceId, Integer.valueOf(count));
        evidenceId = count == 1 ? sourceId : sourceId + "#" + count;
        if (count > 1) {
          addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
              "DEXPI_IMPORT_CONNECTION_ID_DUPLICATE",
              "Connection ID '" + sourceId + "' is duplicated; evidence ID '" + evidenceId + "' is used");
        }
      }

      if (segment == null) {
        addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_CONNECTION_SEGMENT_MISSING", "Connection is not owned by a PipingNetworkSegment");
      } else if (isBlank(segmentId)) {
        addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_CONNECTION_SEGMENT_ID_MISSING", "Owning PipingNetworkSegment has no source identity");
      }

      String fromId = connection.getAttribute("FromID");
      String toId = connection.getAttribute("ToID");
      Element fromElement = elementsById.get(fromId);
      Element toElement = elementsById.get(toId);
      Element fromOwner = resolveConnectionOwner(fromElement);
      Element toOwner = resolveConnectionOwner(toElement);
      if (isBlank(fromId)) {
        addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_CONNECTION_SOURCE_MISSING", "Connection FromID is missing");
      } else if (fromElement == null) {
        addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_CONNECTION_SOURCE_UNRESOLVED",
            "Connection FromID '" + fromId + "' does not resolve to a source object");
      }
      if (isBlank(toId)) {
        addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_CONNECTION_TARGET_MISSING", "Connection ToID is missing");
      } else if (toElement == null) {
        addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_CONNECTION_TARGET_UNRESOLVED",
            "Connection ToID '" + toId + "' does not resolve to a source object");
      }
      if (!isBlank(fromId) && fromId.equals(toId)) {
        addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_CONNECTION_SELF_REFERENCE", "Connection source and target both reference '" + fromId + "'");
      }
      addConnectionOwnerDiagnostic(connection, fromElement, fromOwner, true, diagnostics);
      addConnectionOwnerDiagnostic(connection, toElement, toOwner, false, diagnostics);

      connections.add(new DexpiConnectionInfo(evidenceId, sourceId, segmentId, fromId, toId,
          fromElement == null ? "" : fromElement.getTagName(), toElement == null ? "" : toElement.getTagName(),
          fromOwner == null ? "" : fromOwner.getAttribute("ID"), toOwner == null ? "" : toOwner.getAttribute("ID"),
          fromOwner == null ? "" : fromOwner.getTagName(), toOwner == null ? "" : toOwner.getTagName(),
          fromElement != null, toElement != null));
    }
    logger.info("Parsed {} material connections from DEXPI XML", connections.size());
    return connections;
  }

  private static List<DexpiConnectionEndpointInfo> summarizeConnectionEndpoints(List<DexpiConnectionInfo> connections) {
    Map<String, ConnectionEndpointAccumulator> endpoints = new LinkedHashMap<String, ConnectionEndpointAccumulator>();
    for (DexpiConnectionInfo connection : connections) {
      addConnectionEndpointOccurrence(endpoints, connection, true);
      addConnectionEndpointOccurrence(endpoints, connection, false);
    }

    List<DexpiConnectionEndpointInfo> result = new ArrayList<DexpiConnectionEndpointInfo>();
    for (ConnectionEndpointAccumulator endpoint : endpoints.values()) {
      result.add(endpoint.toInfo());
    }
    return result;
  }

  private static List<DexpiConnectionComponentInfo> summarizeConnectionComponents(List<DexpiConnectionInfo> connections,
      List<DexpiConnectionEndpointInfo> connectionEndpoints) {
    Map<String, List<String>> adjacentEndpointIds = new LinkedHashMap<String, List<String>>();
    for (DexpiConnectionEndpointInfo endpoint : connectionEndpoints) {
      adjacentEndpointIds.put(endpoint.getEndpointId(), new ArrayList<String>());
    }
    for (DexpiConnectionInfo connection : connections) {
      String fromId = connection.getFromId();
      String toId = connection.getToId();
      if (!isBlank(fromId) && !isBlank(toId)) {
        adjacentEndpointIds.get(fromId).add(toId);
        adjacentEndpointIds.get(toId).add(fromId);
      }
    }

    List<ConnectionComponentAccumulator> components = new ArrayList<ConnectionComponentAccumulator>();
    Map<String, Integer> componentByEndpointId = new HashMap<String, Integer>();
    for (DexpiConnectionEndpointInfo endpoint : connectionEndpoints) {
      if (componentByEndpointId.containsKey(endpoint.getEndpointId())) {
        continue;
      }
      int componentIndex = components.size();
      components.add(new ConnectionComponentAccumulator("component-" + (componentIndex + 1)));
      List<String> pendingEndpointIds = new ArrayList<String>();
      pendingEndpointIds.add(endpoint.getEndpointId());
      componentByEndpointId.put(endpoint.getEndpointId(), Integer.valueOf(componentIndex));
      for (int pendingIndex = 0; pendingIndex < pendingEndpointIds.size(); pendingIndex++) {
        String endpointId = pendingEndpointIds.get(pendingIndex);
        for (String adjacentEndpointId : adjacentEndpointIds.get(endpointId)) {
          if (!componentByEndpointId.containsKey(adjacentEndpointId)) {
            componentByEndpointId.put(adjacentEndpointId, Integer.valueOf(componentIndex));
            pendingEndpointIds.add(adjacentEndpointId);
          }
        }
      }
    }

    for (DexpiConnectionEndpointInfo endpoint : connectionEndpoints) {
      Integer componentIndex = componentByEndpointId.get(endpoint.getEndpointId());
      components.get(componentIndex.intValue()).addEndpoint(endpoint);
    }
    for (DexpiConnectionInfo connection : connections) {
      Integer componentIndex = componentByEndpointId.get(connection.getFromId());
      if (componentIndex == null) {
        componentIndex = componentByEndpointId.get(connection.getToId());
      }
      if (componentIndex != null) {
        components.get(componentIndex.intValue()).addConnection(connection.getId());
      }
    }

    List<DexpiConnectionComponentInfo> result = new ArrayList<DexpiConnectionComponentInfo>();
    for (ConnectionComponentAccumulator component : components) {
      result.add(component.toInfo());
    }
    return result;
  }

  private static List<DexpiConnectionCycleInfo> summarizeConnectionCycles(List<DexpiConnectionInfo> connections,
      List<DexpiConnectionEndpointInfo> connectionEndpoints, List<DexpiConnectionComponentInfo> connectionComponents) {
    Map<String, List<String>> outgoingEndpointIds = new LinkedHashMap<String, List<String>>();
    Map<String, List<String>> incomingEndpointIds = new LinkedHashMap<String, List<String>>();
    Map<String, DexpiConnectionEndpointInfo> endpointById = new LinkedHashMap<String, DexpiConnectionEndpointInfo>();
    for (DexpiConnectionEndpointInfo endpoint : connectionEndpoints) {
      outgoingEndpointIds.put(endpoint.getEndpointId(), new ArrayList<String>());
      incomingEndpointIds.put(endpoint.getEndpointId(), new ArrayList<String>());
      endpointById.put(endpoint.getEndpointId(), endpoint);
    }
    for (DexpiConnectionInfo connection : connections) {
      String fromId = connection.getFromId();
      String toId = connection.getToId();
      if (!isBlank(fromId) && !isBlank(toId)) {
        outgoingEndpointIds.get(fromId).add(toId);
        incomingEndpointIds.get(toId).add(fromId);
      }
    }

    Set<String> visitedEndpointIds = new HashSet<String>();
    List<String> finishOrder = new ArrayList<String>();
    for (DexpiConnectionEndpointInfo endpoint : connectionEndpoints) {
      String startId = endpoint.getEndpointId();
      if (visitedEndpointIds.contains(startId)) {
        continue;
      }
      List<String> endpointStack = new ArrayList<String>();
      List<Integer> nextAdjacentIndexStack = new ArrayList<Integer>();
      endpointStack.add(startId);
      nextAdjacentIndexStack.add(Integer.valueOf(0));
      visitedEndpointIds.add(startId);
      while (!endpointStack.isEmpty()) {
        int stackIndex = endpointStack.size() - 1;
        String endpointId = endpointStack.get(stackIndex);
        int nextAdjacentIndex = nextAdjacentIndexStack.get(stackIndex).intValue();
        List<String> adjacentEndpointIds = outgoingEndpointIds.get(endpointId);
        if (nextAdjacentIndex < adjacentEndpointIds.size()) {
          String adjacentEndpointId = adjacentEndpointIds.get(nextAdjacentIndex);
          nextAdjacentIndexStack.set(stackIndex, Integer.valueOf(nextAdjacentIndex + 1));
          if (!visitedEndpointIds.contains(adjacentEndpointId)) {
            visitedEndpointIds.add(adjacentEndpointId);
            endpointStack.add(adjacentEndpointId);
            nextAdjacentIndexStack.add(Integer.valueOf(0));
          }
        } else {
          endpointStack.remove(stackIndex);
          nextAdjacentIndexStack.remove(stackIndex);
          finishOrder.add(endpointId);
        }
      }
    }

    visitedEndpointIds.clear();
    List<Set<String>> cyclicStrongComponents = new ArrayList<Set<String>>();
    Map<String, Integer> cyclicComponentByEndpointId = new HashMap<String, Integer>();
    for (int finishIndex = finishOrder.size() - 1; finishIndex >= 0; finishIndex--) {
      String startId = finishOrder.get(finishIndex);
      if (visitedEndpointIds.contains(startId)) {
        continue;
      }
      Set<String> strongComponentEndpointIds = new HashSet<String>();
      List<String> pendingEndpointIds = new ArrayList<String>();
      pendingEndpointIds.add(startId);
      visitedEndpointIds.add(startId);
      for (int pendingIndex = 0; pendingIndex < pendingEndpointIds.size(); pendingIndex++) {
        String endpointId = pendingEndpointIds.get(pendingIndex);
        strongComponentEndpointIds.add(endpointId);
        for (String adjacentEndpointId : incomingEndpointIds.get(endpointId)) {
          if (!visitedEndpointIds.contains(adjacentEndpointId)) {
            visitedEndpointIds.add(adjacentEndpointId);
            pendingEndpointIds.add(adjacentEndpointId);
          }
        }
      }

      boolean cyclic = strongComponentEndpointIds.size() > 1;
      if (!cyclic) {
        String endpointId = strongComponentEndpointIds.iterator().next();
        for (String adjacentEndpointId : outgoingEndpointIds.get(endpointId)) {
          if (endpointId.equals(adjacentEndpointId)) {
            cyclic = true;
            break;
          }
        }
      }
      if (cyclic) {
        int cyclicComponentIndex = cyclicStrongComponents.size();
        cyclicStrongComponents.add(strongComponentEndpointIds);
        for (String endpointId : strongComponentEndpointIds) {
          cyclicComponentByEndpointId.put(endpointId, Integer.valueOf(cyclicComponentIndex));
        }
      }
    }

    Map<String, String> connectionComponentByEndpointId = new HashMap<String, String>();
    for (DexpiConnectionComponentInfo component : connectionComponents) {
      for (String endpointId : component.getEndpointIds()) {
        connectionComponentByEndpointId.put(endpointId, component.getId());
      }
    }

    List<ConnectionCycleAccumulator> cycles = new ArrayList<ConnectionCycleAccumulator>();
    Map<Integer, Integer> cycleByStrongComponent = new HashMap<Integer, Integer>();
    Map<String, Integer> cycleByEndpointId = new HashMap<String, Integer>();
    for (DexpiConnectionEndpointInfo endpoint : connectionEndpoints) {
      Integer strongComponentIndex = cyclicComponentByEndpointId.get(endpoint.getEndpointId());
      if (strongComponentIndex == null) {
        continue;
      }
      Integer cycleIndex = cycleByStrongComponent.get(strongComponentIndex);
      if (cycleIndex == null) {
        cycleIndex = Integer.valueOf(cycles.size());
        cycleByStrongComponent.put(strongComponentIndex, cycleIndex);
        cycles.add(new ConnectionCycleAccumulator("cycle-" + (cycleIndex.intValue() + 1),
            connectionComponentByEndpointId.get(endpoint.getEndpointId())));
      }
      cycles.get(cycleIndex.intValue()).addEndpoint(endpoint);
      cycleByEndpointId.put(endpoint.getEndpointId(), cycleIndex);
    }

    for (DexpiConnectionInfo connection : connections) {
      if (isBlank(connection.getFromId()) || isBlank(connection.getToId())) {
        continue;
      }
      Integer fromCycleIndex = cycleByEndpointId.get(connection.getFromId());
      Integer toCycleIndex = cycleByEndpointId.get(connection.getToId());
      if (fromCycleIndex != null && fromCycleIndex.equals(toCycleIndex)) {
        cycles.get(fromCycleIndex.intValue()).addConnection(connection);
      } else {
        if (toCycleIndex != null) {
          cycles.get(toCycleIndex.intValue()).addIncomingBoundaryConnection(connection,
              endpointById.get(connection.getToId()), endpointById.get(connection.getFromId()));
        }
        if (fromCycleIndex != null) {
          cycles.get(fromCycleIndex.intValue()).addOutgoingBoundaryConnection(connection,
              endpointById.get(connection.getFromId()), endpointById.get(connection.getToId()));
        }
      }
    }

    List<DexpiConnectionCycleInfo> result = new ArrayList<DexpiConnectionCycleInfo>();
    for (ConnectionCycleAccumulator cycle : cycles) {
      result.add(cycle.toInfo());
    }
    return result;
  }

  private static List<DexpiConnectionCycleTransitionInfo> summarizeConnectionCycleTransitions(
      List<DexpiConnectionInfo> connections, List<DexpiConnectionEndpointInfo> connectionEndpoints,
      List<DexpiConnectionCycleInfo> connectionCycles) {
    Map<String, DexpiConnectionEndpointInfo> endpointById = new HashMap<String, DexpiConnectionEndpointInfo>();
    for (DexpiConnectionEndpointInfo endpoint : connectionEndpoints) {
      endpointById.put(endpoint.getEndpointId(), endpoint);
    }

    Map<String, String> cycleByEndpointId = new HashMap<String, String>();
    for (DexpiConnectionCycleInfo cycle : connectionCycles) {
      for (String endpointId : cycle.getEndpointIds()) {
        cycleByEndpointId.put(endpointId, cycle.getId());
      }
    }

    List<DexpiConnectionCycleTransitionInfo> result = new ArrayList<DexpiConnectionCycleTransitionInfo>();
    for (DexpiConnectionInfo connection : connections) {
      if (isBlank(connection.getFromId()) || isBlank(connection.getToId())) {
        continue;
      }
      String fromCycleId = cycleByEndpointId.get(connection.getFromId());
      String toCycleId = cycleByEndpointId.get(connection.getToId());
      if (fromCycleId == null && toCycleId == null) {
        continue;
      }
      if (fromCycleId != null && fromCycleId.equals(toCycleId)) {
        continue;
      }
      result.add(new DexpiConnectionCycleTransitionInfo(connection, endpointById.get(connection.getFromId()),
          endpointById.get(connection.getToId()), fromCycleId, toCycleId));
    }
    return result;
  }

  private static final class ConnectionCycleAccumulator {
    private final String id;
    private final String connectionComponentId;
    private final List<String> endpointIds = new ArrayList<String>();
    private final List<DexpiConnectionEndpointInfo> endpoints = new ArrayList<DexpiConnectionEndpointInfo>();
    private final List<String> connectionIds = new ArrayList<String>();
    private final List<DexpiConnectionInfo> connections = new ArrayList<DexpiConnectionInfo>();
    private final List<String> incomingBoundaryConnectionIds = new ArrayList<String>();
    private final List<String> outgoingBoundaryConnectionIds = new ArrayList<String>();
    private final List<DexpiConnectionCycleBoundaryInfo> boundaryConnections = new ArrayList<DexpiConnectionCycleBoundaryInfo>();
    private final List<String> unresolvedEndpointIds = new ArrayList<String>();
    private boolean selfReference;

    private ConnectionCycleAccumulator(String id, String connectionComponentId) {
      this.id = id;
      this.connectionComponentId = connectionComponentId;
    }

    private void addEndpoint(DexpiConnectionEndpointInfo endpoint) {
      endpointIds.add(endpoint.getEndpointId());
      endpoints.add(endpoint);
      if (!endpoint.isResolved()) {
        unresolvedEndpointIds.add(endpoint.getEndpointId());
      }
    }

    private void addConnection(DexpiConnectionInfo connection) {
      connectionIds.add(connection.getId());
      connections.add(connection);
      if (connection.getFromId().equals(connection.getToId())) {
        selfReference = true;
      }
    }

    private void addIncomingBoundaryConnection(DexpiConnectionInfo connection,
        DexpiConnectionEndpointInfo internalEndpoint, DexpiConnectionEndpointInfo externalEndpoint) {
      incomingBoundaryConnectionIds.add(connection.getId());
      addBoundaryConnection(connection, DexpiConnectionCycleBoundaryInfo.Direction.INCOMING, internalEndpoint,
          externalEndpoint);
    }

    private void addOutgoingBoundaryConnection(DexpiConnectionInfo connection,
        DexpiConnectionEndpointInfo internalEndpoint, DexpiConnectionEndpointInfo externalEndpoint) {
      outgoingBoundaryConnectionIds.add(connection.getId());
      addBoundaryConnection(connection, DexpiConnectionCycleBoundaryInfo.Direction.OUTGOING, internalEndpoint,
          externalEndpoint);
    }

    private void addBoundaryConnection(DexpiConnectionInfo connection,
        DexpiConnectionCycleBoundaryInfo.Direction direction, DexpiConnectionEndpointInfo internalEndpoint,
        DexpiConnectionEndpointInfo externalEndpoint) {
      boundaryConnections
          .add(new DexpiConnectionCycleBoundaryInfo(connection, direction, internalEndpoint, externalEndpoint));
    }

    private DexpiConnectionCycleInfo toInfo() {
      return new DexpiConnectionCycleInfo(id, connectionComponentId, endpointIds, endpoints, connectionIds, connections,
          incomingBoundaryConnectionIds, outgoingBoundaryConnectionIds, boundaryConnections, unresolvedEndpointIds,
          selfReference);
    }
  }

  private static final class ConnectionComponentAccumulator {
    private final String id;
    private final List<DexpiConnectionEndpointInfo> endpoints = new ArrayList<DexpiConnectionEndpointInfo>();
    private final List<String> connectionIds = new ArrayList<String>();

    private ConnectionComponentAccumulator(String id) {
      this.id = id;
    }

    private void addEndpoint(DexpiConnectionEndpointInfo endpoint) {
      endpoints.add(endpoint);
    }

    private void addConnection(String connectionId) {
      connectionIds.add(connectionId);
    }

    private DexpiConnectionComponentInfo toInfo() {
      List<String> endpointIds = new ArrayList<String>();
      List<String> sourceEndpointIds = new ArrayList<String>();
      List<String> sinkEndpointIds = new ArrayList<String>();
      List<String> potentialMultiConnectionEndpointIds = new ArrayList<String>();
      List<String> unresolvedEndpointIds = new ArrayList<String>();
      for (DexpiConnectionEndpointInfo endpoint : endpoints) {
        endpointIds.add(endpoint.getEndpointId());
        if (endpoint.getIncidenceRole() == DexpiConnectionEndpointInfo.IncidenceRole.SOURCE) {
          sourceEndpointIds.add(endpoint.getEndpointId());
        }
        if (endpoint.getIncidenceRole() == DexpiConnectionEndpointInfo.IncidenceRole.SINK) {
          sinkEndpointIds.add(endpoint.getEndpointId());
        }
        if (endpoint.isPotentialMultiConnectionNode()) {
          potentialMultiConnectionEndpointIds.add(endpoint.getEndpointId());
        }
        if (!endpoint.isResolved()) {
          unresolvedEndpointIds.add(endpoint.getEndpointId());
        }
      }
      return new DexpiConnectionComponentInfo(id, endpointIds, connectionIds, sourceEndpointIds, sinkEndpointIds,
          potentialMultiConnectionEndpointIds, unresolvedEndpointIds);
    }
  }

  private static void addConnectionEndpointOccurrence(Map<String, ConnectionEndpointAccumulator> endpoints,
      DexpiConnectionInfo connection, boolean source) {
    String endpointId = source ? connection.getFromId() : connection.getToId();
    if (isBlank(endpointId)) {
      return;
    }
    ConnectionEndpointAccumulator endpoint = endpoints.get(endpointId);
    if (endpoint == null) {
      endpoint = new ConnectionEndpointAccumulator(endpointId,
          source ? connection.getFromElementName() : connection.getToElementName(),
          source ? connection.getFromOwnerId() : connection.getToOwnerId(),
          source ? connection.getFromOwnerElementName() : connection.getToOwnerElementName(),
          source ? connection.isFromResolved() : connection.isToResolved());
      endpoints.put(endpointId, endpoint);
    }
    endpoint.add(connection.getId(), source);
  }

  private static final class ConnectionEndpointAccumulator {
    private final String endpointId;
    private final String elementName;
    private final String ownerId;
    private final String ownerElementName;
    private final boolean resolved;
    private final List<String> incomingConnectionIds = new ArrayList<String>();
    private final List<String> outgoingConnectionIds = new ArrayList<String>();

    private ConnectionEndpointAccumulator(String endpointId, String elementName, String ownerId,
        String ownerElementName, boolean resolved) {
      this.endpointId = endpointId;
      this.elementName = elementName;
      this.ownerId = ownerId;
      this.ownerElementName = ownerElementName;
      this.resolved = resolved;
    }

    private void add(String connectionId, boolean source) {
      if (source) {
        outgoingConnectionIds.add(connectionId);
      } else {
        incomingConnectionIds.add(connectionId);
      }
    }

    private DexpiConnectionEndpointInfo toInfo() {
      return new DexpiConnectionEndpointInfo(endpointId, elementName, ownerId, ownerElementName, resolved,
          incomingConnectionIds, outgoingConnectionIds);
    }
  }

  private static Element resolveConnectionOwner(Element endpoint) {
    if (endpoint == null) {
      return null;
    }
    if (isConnectionOwnerElement(endpoint)) {
      return endpoint;
    }
    Node parent = endpoint.getParentNode();
    while (parent != null) {
      if (parent.getNodeType() == Node.ELEMENT_NODE) {
        Element candidate = (Element) parent;
        if (isConnectionOwnerElement(candidate)) {
          return candidate;
        }
      }
      parent = parent.getParentNode();
    }
    return null;
  }

  private static boolean isConnectionOwnerElement(Element element) {
    return "Equipment".equals(element.getTagName()) || "PipingComponent".equals(element.getTagName());
  }

  private static void addConnectionOwnerDiagnostic(Element connection, Element endpoint, Element owner, boolean source,
      List<ImportDiagnostic> diagnostics) {
    if (endpoint == null || !"Nozzle".equals(endpoint.getTagName())) {
      return;
    }
    String direction = source ? "SOURCE" : "TARGET";
    if (owner == null) {
      addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_CONNECTION_" + direction + "_OWNER_MISSING",
          (source ? "Source" : "Target") + " nozzle has no explicit Equipment or PipingComponent owner");
    } else if (isBlank(owner.getAttribute("ID"))) {
      addConnectionDiagnostic(connection, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_CONNECTION_" + direction + "_OWNER_ID_MISSING",
          (source ? "Source" : "Target") + " nozzle owner has no source identity");
    }
  }

  private static Element findAncestorElement(Node node, String tagName) {
    Node parent = node.getParentNode();
    while (parent != null) {
      if (parent.getNodeType() == Node.ELEMENT_NODE && tagName.equals(((Element) parent).getTagName())) {
        return (Element) parent;
      }
      parent = parent.getParentNode();
    }
    return null;
  }

  private static void addConnectionDiagnostic(Element connection, List<ImportDiagnostic> diagnostics,
      ImportDiagnosticSeverity severity, String code, String message) {
    if (diagnostics == null) {
      return;
    }
    diagnostics.add(new ImportDiagnostic(severity, code, connection.getAttribute("ID"),
        connection.getAttribute("ComponentClass"), connection.getTagName(), message));
  }

  private static void addInstrumentationDiagnostics(Document document, List<ImportDiagnostic> diagnostics) {
    if (diagnostics == null) {
      return;
    }

    NodeList allElements = document.getElementsByTagName("*");
    Map<String, Element> elementsById = new HashMap<String, Element>();
    for (int i = 0; i < allElements.getLength(); i++) {
      Node node = allElements.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || isInsideShapeCatalogue(node)) {
        continue;
      }
      Element element = (Element) node;
      String id = element.getAttribute("ID");
      if (!isBlank(id) && !elementsById.containsKey(id)) {
        elementsById.put(id, element);
      }
    }

    for (int i = 0; i < allElements.getLength(); i++) {
      Node node = allElements.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE || isInsideShapeCatalogue(node)) {
        continue;
      }
      Element element = (Element) node;
      String elementName = element.getTagName();
      String componentClass = element.getAttribute("ComponentClass");
      if ("ProcessInstrumentationFunction".equals(elementName)) {
        addProcessInstrumentationDiagnostics(element, diagnostics, elementsById);
      } else if ("InstrumentationLoopFunction".equals(elementName)) {
        addInstrumentationLoopDiagnostics(element, diagnostics, elementsById);
      } else if ("InformationFlow".equals(elementName) && "SignalLineFunction".equals(componentClass)) {
        addSignalFlowDiagnostics(element, diagnostics, elementsById);
      } else if ("InformationFlow".equals(elementName) && "MeasuringLineFunction".equals(componentClass)) {
        addMeasuringLineDiagnostics(element, diagnostics, elementsById);
      } else if ("ActuatingFunction".equals(elementName) || "ActuatingElectricalFunction".equals(elementName)
          || "ActuatingFunction".equals(componentClass) || "ActuatingElectricalFunction".equals(componentClass)) {
        addActuatingFunctionDiagnostics(element, diagnostics, elementsById);
      }
    }
  }

  private static void addProcessInstrumentationDiagnostics(Element element, List<ImportDiagnostic> diagnostics,
      Map<String, Element> elementsById) {
    if (isBlank(element.getAttribute("ID"))) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_INSTRUMENT_ID_MISSING", "Instrument function has no source ID");
    }

    String category = getGenericAttribute(element, DexpiMetadata.INSTRUMENTATION_CATEGORY);
    String functions = getGenericAttribute(element, DexpiMetadata.INSTRUMENTATION_FUNCTIONS);
    String number = getGenericAttribute(element, DexpiMetadata.INSTRUMENTATION_NUMBER);
    String tagName = getGenericAttribute(element, DexpiMetadata.TAG_NAME);
    if (isBlank(category) || isBlank(functions)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_INSTRUMENT_FUNCTION_METADATA_MISSING",
          "Instrument category or function-letter metadata is missing");
    }
    if (isBlank(number)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_INSTRUMENT_NUMBER_MISSING", "Instrument number metadata is missing");
    }
    if (isBlank(tagName)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.INFO,
          "DEXPI_IMPORT_INSTRUMENT_TAG_SYNTHESIZED",
          "TagName is missing; the metadata view synthesizes a display tag from category, functions, number, and ID");
    }

    String attachmentStatus = getGenericAttribute(element, "MeasurementAttachmentStatus");
    if ("MISSING_SOURCE_DATA".equalsIgnoreCase(attachmentStatus)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_SENSING_ATTACHMENT_MISSING",
          "The source explicitly marks this measurement as missing a process sensing attachment");
    }
    String attachmentTarget = getGenericAttribute(element, "MeasurementAttachmentTargetID");
    if (!isBlank(attachmentTarget) && !elementsById.containsKey(attachmentTarget)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_SENSING_ATTACHMENT_UNRESOLVED",
          "Measurement attachment target '" + attachmentTarget + "' does not resolve to a source object");
    }
  }

  private static void addInstrumentationLoopDiagnostics(Element element, List<ImportDiagnostic> diagnostics,
      Map<String, Element> elementsById) {
    if (isBlank(element.getAttribute("ID"))) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_LOOP_ID_MISSING", "Instrumentation loop has no source ID");
    }
    if (isBlank(getGenericAttribute(element, DexpiMetadata.LOOP_NUMBER))) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_LOOP_NUMBER_MISSING", "Instrumentation loop number is missing");
    }

    int memberCount = 0;
    for (Element association : directChildElements(element, "Association")) {
      if (!"is a collection including".equals(association.getAttribute("Type"))) {
        continue;
      }
      memberCount++;
      String memberId = association.getAttribute("ItemID");
      if (isBlank(memberId)) {
        addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_LOOP_MEMBER_ID_MISSING", "Instrumentation loop contains a member without an ItemID");
      } else if (!elementsById.containsKey(memberId)) {
        addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
            "DEXPI_IMPORT_LOOP_MEMBER_UNRESOLVED",
            "Instrumentation loop member '" + memberId + "' does not resolve to a source object");
      }
    }
    if (memberCount == 0) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_LOOP_MEMBER_MISSING", "Instrumentation loop contains no instrument members");
    }
  }

  private static void addSignalFlowDiagnostics(Element element, List<ImportDiagnostic> diagnostics,
      Map<String, Element> elementsById) {
    addAssociationReferenceDiagnostic(element, diagnostics, elementsById, "has logical start",
        "DEXPI_IMPORT_SIGNAL_SOURCE_MISSING", "DEXPI_IMPORT_SIGNAL_SOURCE_UNRESOLVED", "Signal source");
    addAssociationReferenceDiagnostic(element, diagnostics, elementsById, "has logical end",
        "DEXPI_IMPORT_SIGNAL_TARGET_MISSING", "DEXPI_IMPORT_SIGNAL_TARGET_UNRESOLVED", "Signal target");
    if (isBlank(getGenericAttribute(element, "SignalConveyingTypeSpecialization"))) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_SIGNAL_MEDIUM_MISSING", "Signal conveying medium or type is missing");
    }
  }

  private static void addMeasuringLineDiagnostics(Element element, List<ImportDiagnostic> diagnostics,
      Map<String, Element> elementsById) {
    addAssociationReferenceDiagnostic(element, diagnostics, elementsById, "has logical start",
        "DEXPI_IMPORT_MEASURING_SOURCE_MISSING", "DEXPI_IMPORT_MEASURING_SOURCE_UNRESOLVED", "Measuring-line source");
    addAssociationReferenceDiagnostic(element, diagnostics, elementsById, "has logical end",
        "DEXPI_IMPORT_MEASURING_TARGET_MISSING", "DEXPI_IMPORT_MEASURING_TARGET_UNRESOLVED", "Measuring-line target");
    addAssociationReferenceDiagnostic(element, diagnostics, elementsById, "is attached to",
        "DEXPI_IMPORT_MEASURING_ATTACHMENT_MISSING", "DEXPI_IMPORT_MEASURING_ATTACHMENT_UNRESOLVED",
        "Measuring-line process attachment");
  }

  private static void addActuatingFunctionDiagnostics(Element element, List<ImportDiagnostic> diagnostics,
      Map<String, Element> elementsById) {
    String finalElementId = getGenericAttribute(element, "FinalControlElementID");
    if (isBlank(finalElementId)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_FINAL_ELEMENT_MISSING", "Actuating function does not identify a final control element");
    } else if (!elementsById.containsKey(finalElementId)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING,
          "DEXPI_IMPORT_FINAL_ELEMENT_UNRESOLVED",
          "Final control element '" + finalElementId + "' does not resolve to a source object");
    }
    addAssociationReferenceDiagnostic(element, diagnostics, elementsById, "is located in",
        "DEXPI_IMPORT_ACTUATION_LOCATION_MISSING", "DEXPI_IMPORT_ACTUATION_LOCATION_UNRESOLVED", "Actuation location");
  }

  private static void addAssociationReferenceDiagnostic(Element element, List<ImportDiagnostic> diagnostics,
      Map<String, Element> elementsById, String associationType, String missingCode, String unresolvedCode,
      String referenceDescription) {
    String itemId = null;
    for (Element association : directChildElements(element, "Association")) {
      if (associationType.equals(association.getAttribute("Type"))) {
        itemId = association.getAttribute("ItemID");
        break;
      }
    }
    if (isBlank(itemId)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING, missingCode,
          referenceDescription + " is missing");
    } else if (!elementsById.containsKey(itemId)) {
      addInstrumentationDiagnostic(element, diagnostics, ImportDiagnosticSeverity.WARNING, unresolvedCode,
          referenceDescription + " '" + itemId + "' does not resolve to a source object");
    }
  }

  private static void addInstrumentationDiagnostic(Element element, List<ImportDiagnostic> diagnostics,
      ImportDiagnosticSeverity severity, String code, String message) {
    diagnostics.add(new ImportDiagnostic(severity, code, element.getAttribute("ID"),
        element.getAttribute("ComponentClass"), element.getTagName(), message));
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
