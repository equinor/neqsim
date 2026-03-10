package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.DexpiTopologyResolver.ResolvedTopology;
import neqsim.process.processmodel.dexpi.DexpiTopologyResolver.TopologyEdge;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * High-level builder that converts a DEXPI P&amp;ID XML file into a runnable NeqSim
 * {@link ProcessSystem}.
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
 *     .setFeedPressure(50.0, "bara").setFeedTemperature(30.0, "C").setFeedFlowRate(1.0, "MSm3/day")
 *     .build();
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

  /**
   * Creates a new simulation builder for the given DEXPI XML file.
   *
   * @param dexpiFile the DEXPI XML file to process
   */
  public DexpiSimulationBuilder(File dexpiFile) {
    this.dexpiFile = Objects.requireNonNull(dexpiFile, "dexpiFile");
  }

  /**
   * Sets the thermodynamic fluid template to use for all streams. If not set, a default
   * methane/ethane system with SRK EOS is used.
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
   * Enables automatic instrumentation (transmitters and PID controllers) on separators,
   * compressors, and heat exchangers.
   *
   * @param autoInstrument true to enable auto-instrumentation
   * @return this builder for chaining
   */
  public DexpiSimulationBuilder setAutoInstrument(boolean autoInstrument) {
    this.autoInstrument = autoInstrument;
    return this;
  }

  /**
   * Builds the NeqSim {@link ProcessSystem} from the DEXPI XML file.
   *
   * @return a fully wired ProcessSystem ready for {@code process.run()}
   * @throws IOException if the file cannot be read
   * @throws DexpiXmlReaderException if the XML cannot be parsed
   */
  public ProcessSystem build() throws IOException, DexpiXmlReaderException {
    logger.info("Building ProcessSystem from DEXPI file: {}", dexpiFile.getAbsolutePath());

    Document document;
    try (InputStream is = new FileInputStream(dexpiFile)) {
      document = parseDocument(is);
    }
    if (document == null) {
      throw new DexpiXmlReaderException("Failed to parse DEXPI XML document");
    }

    // Step 1: Resolve topology
    ResolvedTopology topology = DexpiTopologyResolver.resolve(document);
    logger.info("Topology: {} equipment, {} edges", topology.getOrderedEquipmentIds().size(),
        topology.getEdges().size());

    // Step 2: Build DexpiProcessUnit map with sizing attributes
    Map<String, DexpiProcessUnit> unitMap = buildUnitMap(document, topology);

    // Step 3: Create process system and feed stream
    ProcessSystem processSystem = new ProcessSystem("DEXPI process");
    SystemInterface fluid = getOrCreateFluidTemplate();
    Stream feedStream = new Stream("DEXPI-Feed", fluid.clone());
    feedStream.setPressure(feedPressure, feedPressureUnit);
    feedStream.setTemperature(feedTemperature, feedTemperatureUnit);
    feedStream.setFlowRate(feedFlowRate, feedFlowRateUnit);
    processSystem.add(feedStream);

    // Step 4: Walk topology and instantiate equipment
    Map<String, ProcessEquipmentInterface> createdEquipment = new HashMap<>();
    Map<String, StreamInterface> outletStreams = new HashMap<>();

    // The feed stream is available for the first equipment
    List<String> orderedIds = topology.getOrderedEquipmentIds();
    boolean feedAssigned = false;

    for (String equipId : orderedIds) {
      DexpiProcessUnit unit = unitMap.get(equipId);
      if (unit == null) {
        logger.debug("Skipping equipment '{}' (no unit mapping)", equipId);
        continue;
      }

      // Find inlet stream for this equipment
      StreamInterface inletStream = null;
      List<TopologyEdge> incoming = topology.getIncomingEdges(equipId);
      for (TopologyEdge edge : incoming) {
        StreamInterface upstreamOutlet = outletStreams.get(edge.getSourceEquipmentId());
        if (upstreamOutlet != null) {
          inletStream = upstreamOutlet;
          break;
        }
      }

      // If no upstream connection found, use the feed stream
      if (inletStream == null && !feedAssigned) {
        inletStream = feedStream;
        feedAssigned = true;
      }

      // If still no inlet, create a clone of the feed
      if (inletStream == null) {
        Stream clonedFeed = new Stream(equipId + "-feed", fluid.clone());
        clonedFeed.setPressure(feedPressure, feedPressureUnit);
        clonedFeed.setTemperature(feedTemperature, feedTemperatureUnit);
        clonedFeed.setFlowRate(feedFlowRate, feedFlowRateUnit);
        processSystem.add(clonedFeed);
        inletStream = clonedFeed;
      }

      // Create the equipment
      ProcessEquipmentInterface equipment = DexpiEquipmentFactory.create(unit, inletStream);
      processSystem.add(equipment);
      createdEquipment.put(equipId, equipment);

      // Capture outlet stream for downstream wiring
      StreamInterface outletStream = getOutletStream(equipment);
      if (outletStream != null) {
        outletStreams.put(equipId, outletStream);
      }
    }

    // Step 5: Optionally auto-instrument
    if (autoInstrument) {
      applyAutoInstrumentation(processSystem);
    }

    logger.info("Built ProcessSystem with {} units from DEXPI",
        processSystem.getUnitOperations().size());
    return processSystem;
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

      DexpiProcessUnit unit =
          new DexpiProcessUnit(name, componentClass, equipEnum, lineNumber, fluidCode);
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
   * Gets the outlet stream from a process equipment instance.
   *
   * @param equipment the equipment
   * @return the outlet stream, or null if not available
   */
  private StreamInterface getOutletStream(ProcessEquipmentInterface equipment) {
    if (equipment instanceof Separator) {
      return ((Separator) equipment).getGasOutStream();
    }
    if (equipment instanceof Splitter) {
      return ((Splitter) equipment).getSplitStream(0);
    }
    if (equipment instanceof Stream) {
      return (StreamInterface) equipment;
    }
    // For TwoPortEquipment (Compressor, Pump, Valve, HeatExchanger, etc.)
    try {
      return (StreamInterface) equipment.getClass().getMethod("getOutletStream").invoke(equipment);
    } catch (Exception e1) {
      try {
        return (StreamInterface) equipment.getClass().getMethod("getOutStream").invoke(equipment);
      } catch (Exception e2) {
        logger.debug("No outlet stream method on {}", equipment.getClass().getSimpleName());
        return null;
      }
    }
  }

  /**
   * Applies auto-instrumentation using the DynamicProcessHelper if available.
   *
   * @param processSystem the process system to instrument
   */
  private void applyAutoInstrumentation(ProcessSystem processSystem) {
    try {
      neqsim.process.util.DynamicProcessHelper helper =
          new neqsim.process.util.DynamicProcessHelper(processSystem);
      helper.instrumentAndControl();
      logger.info("Auto-instrumentation applied");
    } catch (Exception e) {
      logger.warn("Auto-instrumentation failed: {}", e.getMessage());
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
   * @return the parsed Document
   * @throws DexpiXmlReaderException if parsing fails
   */
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
