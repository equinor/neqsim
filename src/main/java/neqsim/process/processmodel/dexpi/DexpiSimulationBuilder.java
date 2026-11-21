package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
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
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.DexpiTopologyResolver.ResolvedTopology;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * High-level builder that converts a DEXPI P&amp;ID XML file into a runnable NeqSim {@link ProcessSystem}.
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
 * fluid.addComponent("methane", 0.9);
 * fluid.addComponent("ethane", 0.1);
 * fluid.setMixingRule("classic");
 *
 * ProcessSystem process = new DexpiSimulationBuilder(new File("plant.xml")).setFluidTemplate(fluid)
 *     .setFeedPressure(50.0, "bara").setFeedTemperature(30.0, "C").setFeedFlowRate(1.0, "MSm3/day").build();
 *
 * process.run();
 * </pre>
 *
 * <p>
 * The builder performs these steps:
 * </p>
 * <ol>
 * <li>Parse the DEXPI XML document securely (XXE protection)</li>
 * <li>Resolve topology (nozzle/connection graph)</li>
 * <li>Identify equipment and piping components with their sizing attributes</li>
 * <li>Create a feed stream from the fluid template</li>
 * <li>Walk the topology in upstream-to-downstream order, instantiating real NeqSim equipment via
 * {@link DexpiEquipmentFactory}</li>
 * <li>Wire outlet streams of upstream equipment to inlet of downstream equipment</li>
 * <li>Optionally auto-instrument the process with transmitters and PID controllers</li>
 * </ol>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiSimulationBuilder {
  private static final Logger logger = LogManager.getLogger(DexpiSimulationBuilder.class);

  private final File dexpiFile;
  private SystemInterface fluidTemplate;
  private double feedPressure = 50.0;
  private String feedPressureUnit = "bara";
  private double feedTemperature = 30.0;
  private String feedTemperatureUnit = "C";
  private double feedFlowRate = 1.0;
  private String feedFlowRateUnit = "MSm3/day";
  private boolean autoInstrument = false;
  private boolean namespaceAware = false;
  private final List<BuildDiagnostic> diagnostics = new ArrayList<>();

  /**
   * Creates a new simulation builder for the given DEXPI XML file.
   *
   * @param dexpiFile the DEXPI XML file to process
   */
  public DexpiSimulationBuilder(File dexpiFile) {
    this.dexpiFile = Objects.requireNonNull(dexpiFile, "dexpiFile");
  }

  /**
   * Sets the thermodynamic fluid template to use for all streams. If not set, a default methane/ethane system with SRK
   * EOS is used.
   *
   * @param fluidTemplate the fluid system to clone for each stream
   * @return this builder for chaining
   */
  public DexpiSimulationBuilder setFluidTemplate(SystemInterface fluidTemplate) {
    this.fluidTemplate = fluidTemplate;
    return this;
  }

  /**
   * Sets the feed stream pressure.
   *
   * @param pressure the pressure value
   * @param unit the pressure unit (e.g. "bara", "barg", "psia")
   * @return this builder for chaining
   */
  public DexpiSimulationBuilder setFeedPressure(double pressure, String unit) {
    this.feedPressure = pressure;
    this.feedPressureUnit = unit;
    return this;
  }

  /**
   * Sets the feed stream temperature.
   *
   * @param temperature the temperature value
   * @param unit the temperature unit (e.g. "C", "K", "F")
   * @return this builder for chaining
   */
  public DexpiSimulationBuilder setFeedTemperature(double temperature, String unit) {
    this.feedTemperature = temperature;
    this.feedTemperatureUnit = unit;
    return this;
  }

  /**
   * Sets the feed stream flow rate.
   *
   * @param flowRate the flow rate value
   * @param unit the flow rate unit (e.g. "MSm3/day", "kg/hr")
   * @return this builder for chaining
   */
  public DexpiSimulationBuilder setFeedFlowRate(double flowRate, String unit) {
    this.feedFlowRate = flowRate;
    this.feedFlowRateUnit = unit;
    return this;
  }

  /**
   * Enables automatic instrumentation (transmitters and PID controllers) on separators, compressors, and heat
   * exchangers.
   *
   * @param autoInstrument true to enable auto-instrumentation
   * @return this builder for chaining
   */
  public DexpiSimulationBuilder setAutoInstrument(boolean autoInstrument) {
    this.autoInstrument = autoInstrument;
    return this;
  }

  /**
   * Enables namespace-aware XML parsing. When enabled, the parser will respect namespace prefixes in element names. By
   * default, namespace awareness is disabled for compatibility with DEXPI files that may use local names without
   * namespace declarations.
   *
   * @param namespaceAware true to enable namespace-aware parsing
   * @return this builder for chaining
   */
  public DexpiSimulationBuilder setNamespaceAware(boolean namespaceAware) {
    this.namespaceAware = namespaceAware;
    return this;
  }

  /**
   * Builds a runnable {@link ProcessSystem} from the DEXPI file.
   *
   * <p>
   * The flowsheet is reconstructed from the material connections in the P&amp;ID. In-line throttling valves that carry
   * an operating outlet pressure become {@code ThrottlingValve} units; other piping items are collapsed. Each connection
   * leaves the correct outlet of multi-outlet equipment (separator gas, liquid or water; splitter branch). Units with
   * several inlets are fed through a mixer, and connections that close a loop are torn and closed with a
   * {@code Recycle}. Operating outlet pressures and temperatures carried in the file are applied as unit
   * specifications. Every judgement the builder makes is recorded in {@link #getDiagnostics()}.
   * </p>
   *
   * <p>
   * The DEXPI file does not carry fluid composition. The fluid template and the feed state set on this builder are
   * used for every boundary feed. Native DEXPI 2.x files (root element {@code Model}) are rejected with an explicit
   * error because they cannot yet be built into a runnable simulation.
   * </p>
   *
   * @return a fully wired ProcessSystem ready for {@code process.run()}
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the XML cannot be parsed or uses an unsupported DEXPI profile
   */
  public ProcessSystem build() throws IOException, DexpiXmlReaderException {
    logger.info("Building ProcessSystem from DEXPI file: {}", dexpiFile.getAbsolutePath());
    diagnostics.clear();

    Document document;
    try (InputStream is = new FileInputStream(dexpiFile)) {
      document = parseDocument(is, namespaceAware);
    }
    if (document == null) {
      throw new DexpiXmlReaderException("Failed to parse DEXPI XML document");
    }
    Element root = document.getDocumentElement();
    String rootName = root.getLocalName() != null ? root.getLocalName() : root.getTagName();
    if ("Model".equals(rootName)) {
      throw new DexpiXmlReaderException("Native DEXPI 2.x Model files cannot yet be built into a simulation: "
          + dexpiFile.getName() + ". Export the Proteus-compatible profile with DexpiXmlWriter.write(...) for "
          + "simulation round trips, or read instruments with DexpiXmlReader.readInstruments(...).");
    }

    ResolvedTopology topology = DexpiTopologyResolver.resolve(document);
    Map<String, DexpiProcessUnit> unitMap = buildUnitMap(document, topology);
    Map<String, EquipmentEnum> equipmentTypes = new HashMap<>();
    for (Map.Entry<String, DexpiProcessUnit> entry : unitMap.entrySet()) {
      equipmentTypes.put(entry.getKey(), entry.getValue().getMappedEquipment());
    }
    DexpiFlowsheetGraph graph = DexpiFlowsheetGraph.extract(document, topology, equipmentTypes,
        DexpiMappingLoader.loadPipingComponentMapping());
    for (String note : graph.getNotes()) {
      String[] parts = note.split("\\|", 3);
      addDiagnostic(BuildDiagnostic.Severity.INFO, parts[0], parts[1], parts[2]);
    }
    for (Map.Entry<String, Element> node : graph.nodes.entrySet()) {
      if (!unitMap.containsKey(node.getKey())) {
        Element element = node.getValue();
        String tag = getGenericAttribute(element, DexpiMetadata.TAG_NAME);
        String name = tag != null ? tag : node.getKey();
        DexpiProcessUnit valve = new DexpiProcessUnit(name, element.getAttribute("ComponentClass"),
            EquipmentEnum.ThrottlingValve, null, null);
        valve.setDexpiId(node.getKey());
        unitMap.put(node.getKey(), valve);
      }
    }
    logger.info("Flowsheet graph: {} units, {} material connections", graph.nodes.size(), graph.edges.size());

    ProcessSystem processSystem = new ProcessSystem("DEXPI process");
    SystemInterface fluid = getOrCreateFluidTemplate();
    Map<String, ProcessEquipmentInterface> created = new LinkedHashMap<>();
    Map<DexpiFlowsheetGraph.Edge, Stream> tears = new LinkedHashMap<>();
    Set<String> usedNames = new HashSet<>();
    boolean feedAssigned = false;

    List<String> remaining = new ArrayList<>();
    for (String id : topology.getOrderedEquipmentIds()) {
      if (graph.nodes.containsKey(id)) {
        remaining.add(id);
      }
    }
    for (String id : graph.nodes.keySet()) {
      if (!remaining.contains(id)) {
        remaining.add(id);
      }
    }

    while (!remaining.isEmpty()) {
      String id = pickNext(remaining, graph, created.keySet());
      remaining.remove(id);
      DexpiProcessUnit unit = unitMap.get(id);

      List<StreamInterface> inlets = new ArrayList<>();
      for (DexpiFlowsheetGraph.Edge edge : graph.incoming(id)) {
        ProcessEquipmentInterface source = created.get(edge.source);
        if (source != null) {
          inlets.add(outletFor(source, edge, graph));
        } else {
          Stream tear = new Stream(uniqueName(unitMap.get(edge.source).getName() + "-to-" + unit.getName() + "-tear",
              usedNames), fluid.clone());
          tear.setFlowRate(Math.max(1.0e-6, 0.01 * feedFlowRate), feedFlowRateUnit);
          double p = DexpiFlowsheetGraph.operatingPressureBara(graph.nodes.get(edge.source));
          double t = DexpiFlowsheetGraph.operatingTemperatureK(graph.nodes.get(edge.source));
          tear.setPressure(Double.isNaN(p) ? feedPressure : p, Double.isNaN(p) ? feedPressureUnit : "bara");
          tear.setTemperature(Double.isNaN(t) ? feedTemperature : t, Double.isNaN(t) ? feedTemperatureUnit : "K");
          processSystem.add(tear);
          tears.put(edge, tear);
          inlets.add(tear);
          addDiagnostic(BuildDiagnostic.Severity.INFO, "DEXPI_BUILD_RECYCLE_TEAR", id,
              "loop torn at the connection " + unitMap.get(edge.source).getName() + " -> " + unit.getName()
                  + "; closed with a Recycle");
        }
      }
      if (inlets.isEmpty()) {
        Stream feed = new Stream(feedAssigned ? uniqueName(unit.getName() + "-feed", usedNames) : "DEXPI-Feed",
            fluid.clone());
        feed.setPressure(feedPressure, feedPressureUnit);
        feed.setTemperature(feedTemperature, feedTemperatureUnit);
        feed.setFlowRate(feedFlowRate, feedFlowRateUnit);
        processSystem.add(feed);
        inlets.add(feed);
        if (feedAssigned) {
          addDiagnostic(BuildDiagnostic.Severity.WARNING, "DEXPI_BUILD_ADDITIONAL_FEED", id,
              "unit has no upstream connection; it received its own copy of the builder feed state and rate");
        }
        feedAssigned = true;
      }

      ProcessEquipmentInterface equipment;
      if (unit.getMappedEquipment() == EquipmentEnum.Mixer) {
        equipment = DexpiEquipmentFactory.create(unit, inlets.get(0));
        for (int i = 1; i < inlets.size(); i++) {
          ((Mixer) equipment).addStream(inlets.get(i));
        }
      } else {
        StreamInterface inlet = inlets.get(0);
        if (inlets.size() > 1) {
          Mixer header = new Mixer(uniqueName(unit.getName() + "-inlet-mixer", usedNames));
          for (StreamInterface stream : inlets) {
            header.addStream(stream);
          }
          processSystem.add(header);
          inlet = header.getOutletStream();
          addDiagnostic(BuildDiagnostic.Severity.INFO, "DEXPI_BUILD_INLET_MIXER", id,
              inlets.size() + " connections enter the unit; they were combined in an inlet mixer");
        }
        equipment = DexpiEquipmentFactory.create(unit, inlet);
      }
      usedNames.add(equipment.getName());
      configureSplitter(equipment, id, graph);
      applyOperatingSpecification(equipment, id, graph.nodes.get(id));
      processSystem.add(equipment);
      created.put(id, equipment);
    }

    for (Map.Entry<DexpiFlowsheetGraph.Edge, Stream> tear : tears.entrySet()) {
      ProcessEquipmentInterface source = created.get(tear.getKey().source);
      Recycle recycle = new Recycle(uniqueName(tear.getValue().getName().replace("-tear", "-recycle"), usedNames));
      recycle.addStream(outletFor(source, tear.getKey(), graph));
      recycle.setOutletStream(tear.getValue());
      processSystem.add(recycle);
    }

    if (autoInstrument) {
      List<DexpiInstrumentInfo> dexpiInstruments = parseDexpiInstruments(document);
      applyAutoInstrumentation(processSystem, dexpiInstruments);
    }

    logger.info("Built ProcessSystem with {} units from DEXPI ({} diagnostics)", processSystem.getUnitOperations().size(),
        diagnostics.size());
    return processSystem;
  }

  /**
   * Returns the diagnostics recorded by the last {@link #build()} call.
   *
   * @return unmodifiable diagnostics in recording order
   */
  public List<BuildDiagnostic> getDiagnostics() {
    return Collections.unmodifiableList(diagnostics);
  }

  /** A judgement or loss recorded while building a simulation from DEXPI. */
  public static final class BuildDiagnostic implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /** Diagnostic severity. */
    public enum Severity {
      /** Informational note on a deliberate mapping. */
      INFO,
      /** Possible loss of fidelity that the user should review. */
      WARNING
    }

    private final Severity severity;
    private final String code;
    private final String elementId;
    private final String message;

    /**
     * Creates a diagnostic.
     *
     * @param severity severity
     * @param code stable code
     * @param elementId DEXPI element id, possibly null
     * @param message human-readable message
     */
    BuildDiagnostic(Severity severity, String code, String elementId, String message) {
      this.severity = severity;
      this.code = code;
      this.elementId = elementId;
      this.message = message;
    }

    /**
     * Returns the severity.
     *
     * @return severity
     */
    public Severity getSeverity() {
      return severity;
    }

    /**
     * Returns the stable diagnostic code.
     *
     * @return code
     */
    public String getCode() {
      return code;
    }

    /**
     * Returns the DEXPI element id the diagnostic refers to.
     *
     * @return element id, possibly null
     */
    public String getElementId() {
      return elementId;
    }

    /**
     * Returns the human-readable message.
     *
     * @return message
     */
    public String getMessage() {
      return message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return severity + " " + code + " [" + elementId + "] " + message;
    }
  }

  /**
   * Records a build diagnostic.
   *
   * @param severity severity
   * @param code stable code
   * @param elementId DEXPI element id
   * @param message human-readable message
   */
  private void addDiagnostic(BuildDiagnostic.Severity severity, String code, String elementId, String message) {
    diagnostics.add(new BuildDiagnostic(severity, code, elementId, message));
  }

  /**
   * Picks the next unit whose upstream units exist; when only loops remain, the unit with most inputs ready.
   *
   * @param remaining units not yet built, in preferred order
   * @param graph material-flow graph
   * @param created ids of units already built
   * @return the next unit id
   */
  private static String pickNext(List<String> remaining, DexpiFlowsheetGraph graph, Set<String> created) {
    String best = null;
    int bestReady = -1;
    for (String id : remaining) {
      int ready = 0;
      int missing = 0;
      for (DexpiFlowsheetGraph.Edge edge : graph.incoming(id)) {
        if (created.contains(edge.source)) {
          ready++;
        } else {
          missing++;
        }
      }
      if (missing == 0) {
        return id;
      }
      if (ready > bestReady) {
        best = id;
        bestReady = ready;
      }
    }
    return best;
  }

  /**
   * Returns the outlet stream an edge leaves from.
   *
   * @param source built source unit
   * @param edge material edge
   * @param graph material-flow graph
   * @return the source outlet stream for the edge
   */
  private static StreamInterface outletFor(ProcessEquipmentInterface source, DexpiFlowsheetGraph.Edge edge,
      DexpiFlowsheetGraph graph) {
    List<StreamInterface> outlets = new ArrayList<>();
    if (source instanceof ThreePhaseSeparator) {
      ThreePhaseSeparator separator = (ThreePhaseSeparator) source;
      outlets.add(separator.getGasOutStream());
      outlets.add(separator.getOilOutStream());
      outlets.add(separator.getWaterOutStream());
    } else if (source instanceof Separator) {
      outlets.add(((Separator) source).getGasOutStream());
      outlets.add(((Separator) source).getLiquidOutStream());
    } else if (source instanceof Splitter) {
      Splitter splitter = (Splitter) source;
      for (int i = 0; i < graph.outgoing(edge.source).size(); i++) {
        outlets.add(splitter.getSplitStream(i));
      }
    }
    if (outlets.isEmpty()) {
      return DexpiStreamUtils.getGasOutletStream(source);
    }
    if (source instanceof Splitter) {
      return outlets.get(Math.max(0, graph.outgoing(edge.source).indexOf(edge)));
    }
    return outlets.get(graph.resolveOutletIndex(edge, outlets.size()));
  }

  /**
   * Gives a splitter one branch per outgoing connection, split by exported branch flows when available.
   *
   * @param equipment built unit; ignored unless it is a splitter
   * @param id DEXPI id of the unit
   * @param graph material-flow graph
   */
  private void configureSplitter(ProcessEquipmentInterface equipment, String id, DexpiFlowsheetGraph graph) {
    if (!(equipment instanceof Splitter)) {
      return;
    }
    List<DexpiFlowsheetGraph.Edge> outgoing = graph.outgoing(id);
    int branches = Math.max(1, outgoing.size());
    double[] factors = new double[branches];
    boolean allFlows = outgoing.size() == branches;
    for (int i = 0; i < outgoing.size(); i++) {
      String value = getGenericAttribute(outgoing.get(i).segment, "OperatingFlowValue");
      try {
        factors[i] = value == null ? Double.NaN : Double.parseDouble(value);
      } catch (NumberFormatException e) {
        factors[i] = Double.NaN;
      }
      allFlows &= Double.isFinite(factors[i]) && factors[i] >= 0.0;
    }
    if (!allFlows) {
      java.util.Arrays.fill(factors, 1.0);
      addDiagnostic(BuildDiagnostic.Severity.WARNING, "DEXPI_BUILD_SPLIT_FACTORS_ASSUMED", id,
          "branch flows not available; equal split assumed over " + branches + " branches");
    }
    ((Splitter) equipment).setSplitFactors(factors);
  }

  /**
   * Applies exported operating outlet conditions as unit specifications.
   *
   * @param equipment built unit
   * @param id DEXPI id of the unit
   * @param element DEXPI element carrying the operating values
   */
  private void applyOperatingSpecification(ProcessEquipmentInterface equipment, String id, Element element) {
    double pressure = DexpiFlowsheetGraph.operatingPressureBara(element);
    double temperature = DexpiFlowsheetGraph.operatingTemperatureK(element);
    boolean needsPressure = equipment instanceof Compressor || equipment instanceof Pump
        || equipment instanceof ThrottlingValve;
    if (needsPressure) {
      if (Double.isNaN(pressure)) {
        addDiagnostic(BuildDiagnostic.Severity.WARNING, "DEXPI_BUILD_SPECIFICATION_MISSING", id,
            equipment.getName() + " has no operating outlet pressure in the file; set it before running");
        return;
      }
      if (equipment instanceof Compressor) {
        ((Compressor) equipment).setOutletPressure(pressure);
      } else if (equipment instanceof Pump) {
        ((Pump) equipment).setOutletPressure(pressure);
      } else {
        ((ThrottlingValve) equipment).setOutletPressure(pressure);
      }
    } else if (equipment instanceof Heater) {
      if (Double.isNaN(temperature)) {
        addDiagnostic(BuildDiagnostic.Severity.WARNING, "DEXPI_BUILD_SPECIFICATION_MISSING", id,
            equipment.getName() + " has no operating outlet temperature in the file; set it before running");
        return;
      }
      ((Heater) equipment).setOutTemperature(temperature);
    }
  }

  /**
   * Returns a unit name not yet used in the built process and reserves it.
   *
   * @param base preferred name
   * @param used names already used, updated with the result
   * @return unique name
   */
  private static String uniqueName(String base, Set<String> used) {
    String name = base;
    int index = 2;
    while (used.contains(name)) {
      name = base + "-" + index++;
    }
    used.add(name);
    return name;
  }
  /**
   * Builds a map of equipment ID to DexpiProcessUnit with sizing attributes populated.
   *
   * @param document the parsed XML document
   * @param topology the resolved topology
   * @return map of equipment ID to DexpiProcessUnit
   */
  private Map<String, DexpiProcessUnit> buildUnitMap(Document document, ResolvedTopology topology) {
    Map<String, DexpiProcessUnit> unitMap = new HashMap<>();
    Map<String, EquipmentEnum> equipmentMapping = DexpiMappingLoader.loadEquipmentMapping();
    Map<String, EquipmentEnum> pipingMapping = DexpiMappingLoader.loadPipingComponentMapping();

    for (Map.Entry<String, Element> entry : topology.getEquipmentElements().entrySet()) {
      String equipId = entry.getKey();
      Element element = entry.getValue();
      String componentClass = element.getAttribute("ComponentClass");

      EquipmentEnum equipEnum = equipmentMapping.get(componentClass);
      if (equipEnum == null) {
        equipEnum = pipingMapping.get(componentClass);
      }
      if (equipEnum == null) {
        logger.debug("No mapping for ComponentClass '{}', skipping '{}'", componentClass, equipId);
        continue;
      }

      String tagName = getGenericAttribute(element, DexpiMetadata.TAG_NAME);
      String name = tagName != null ? tagName : equipId;
      String lineNumber = getGenericAttribute(element, DexpiMetadata.LINE_NUMBER);
      String fluidCode = getGenericAttribute(element, DexpiMetadata.FLUID_CODE);

      DexpiProcessUnit unit = new DexpiProcessUnit(name, componentClass, equipEnum, lineNumber, fluidCode);
      unit.setDexpiId(equipId);

      // Extract sizing attributes
      for (String attr : DexpiMetadata.sizingAttributes()) {
        String value = getGenericAttribute(element, attr);
        if (value != null) {
          unit.setSizingAttribute(attr, value);
        }
      }

      unitMap.put(equipId, unit);
    }

    logger.debug("Built {} DexpiProcessUnit entries", unitMap.size());
    return unitMap;
  }


  /**
   * Applies auto-instrumentation using the DynamicProcessHelper, then logs associations between DEXPI instrument tags
   * and auto-generated transmitters/controllers.
   *
   * @param processSystem the process system to instrument
   * @param dexpiInstruments the DEXPI instrument info list (may be empty)
   */
  private void applyAutoInstrumentation(ProcessSystem processSystem, List<DexpiInstrumentInfo> dexpiInstruments) {
    try {
      neqsim.process.util.DynamicProcessHelper helper = new neqsim.process.util.DynamicProcessHelper(processSystem);
      helper.instrumentAndControl();
      logger.info("Auto-instrumentation applied");

      // Wire DEXPI instrument tags: rename auto-generated tags to match DEXPI names
      if (!dexpiInstruments.isEmpty()) {
        Map<String, MeasurementDeviceInterface> transmitters = helper.getTransmitters();
        Map<String, ControllerDeviceInterface> controllers = helper.getControllers();

        for (DexpiInstrumentInfo info : dexpiInstruments) {
          String category = info.getCategory();
          if (category == null) {
            continue;
          }
          // Match DEXPI instrument to auto-generated tag by category (P->PT, L->LT,
          // T->TT, F->FT)
          String autoPrefix = category + "T-";
          for (Map.Entry<String, MeasurementDeviceInterface> entry : transmitters.entrySet()) {
            if (entry.getKey().startsWith(autoPrefix) && info.getTagName() != null) {
              entry.getValue().setName(info.getTagName());
              logger.info("Renamed transmitter '{}' to DEXPI tag '{}'", entry.getKey(), info.getTagName());
              break;
            }
          }
          if (info.hasControlFunction()) {
            String autoControlPrefix = category + "C-";
            for (Map.Entry<String, ControllerDeviceInterface> entry : controllers.entrySet()) {
              if (entry.getKey().startsWith(autoControlPrefix) && info.getTagName() != null) {
                String controlTag = info.getTagName().replaceFirst("^(" + category + ")T", "$1C");
                if (entry.getValue() instanceof ControllerDeviceBaseClass) {
                  ((ControllerDeviceBaseClass) entry.getValue()).setName(controlTag);
                }
                logger.info("Renamed controller '{}' to DEXPI tag '{}'", entry.getKey(), controlTag);
                break;
              }
            }
          }
        }
      }
    } catch (Exception e) {
      logger.warn("Auto-instrumentation failed: {}", e.getMessage());
    }
  }

  /**
   * Parses DEXPI instrument info from the XML document.
   *
   * @param document the parsed XML document
   * @return list of DEXPI instrument info records (may be empty)
   */
  private List<DexpiInstrumentInfo> parseDexpiInstruments(Document document) {
    try {
      return DexpiXmlReader.parseInstrumentsFromDocument(document);
    } catch (Exception e) {
      logger.debug("No instruments parsed from DEXPI: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * Returns the fluid template, creating a default if none was set.
   *
   * @return the fluid template
   */
  private SystemInterface getOrCreateFluidTemplate() {
    if (fluidTemplate != null) {
      return fluidTemplate;
    }
    SystemSrkEos system = new SystemSrkEos(298.15, feedPressure);
    system.addComponent("methane", 0.9);
    system.addComponent("ethane", 0.1);
    system.setMixingRule(2);
    system.init(0);
    return system;
  }

  /**
   * Parses a DEXPI XML document with security hardening.
   *
   * @param inputStream the input stream
   * @param nsAware whether to enable namespace-aware parsing
   * @return the parsed Document
   * @throws DexpiXmlReaderException if parsing fails
   */
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

  /**
   * Reads a GenericAttribute value from the given element.
   *
   * @param element the XML element
   * @param attributeName the attribute name to search for
   * @return the attribute value, or null if not found
   */
  private static String getGenericAttribute(Element element, String attributeName) {
    NodeList genericNodes = element.getChildNodes();
    for (int i = 0; i < genericNodes.getLength(); i++) {
      Node child = genericNodes.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element childEl = (Element) child;
      if ("GenericAttributes".equals(childEl.getTagName())) {
        NodeList attributes = childEl.getElementsByTagName("GenericAttribute");
        for (int j = 0; j < attributes.getLength(); j++) {
          Node attrNode = attributes.item(j);
          if (attrNode.getNodeType() != Node.ELEMENT_NODE) {
            continue;
          }
          Element attr = (Element) attrNode;
          if (attributeName.equals(attr.getAttribute("Name"))) {
            String value = attr.getAttribute("Value");
            if (value == null || value.isEmpty()) {
              value = attr.getAttribute("ValueURI");
            }
            if (value != null && !value.isEmpty()) {
              return value;
            }
          }
        }
      }
    }
    return null;
  }
}
