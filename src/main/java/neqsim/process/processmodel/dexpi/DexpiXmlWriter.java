package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.TwoPortEquipment;

/**
 * Utility for exporting {@link ProcessSystem}s created from DEXPI data back into a lightweight
 * DEXPI XML representation.
 *
 * <p>
 * The writer groups all discovered {@link DexpiStream} segments by line number (or fluid code when
 * a line is not available) to generate simple {@code <PipingNetworkSystem>} elements with
 * associated {@code <PipingNetworkSegment>} children.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see <a href="https://dexpi.org/">DEXPI Standard</a>
 */
public final class DexpiXmlWriter {
  private static final Pattern NON_IDENTIFIER = Pattern.compile("[^A-Za-z0-9_-]");
  private static final transient ThreadLocal<DecimalFormat> DECIMAL_FORMAT =
      ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        DecimalFormat format = new DecimalFormat("0.############", symbols);
        format.setMaximumFractionDigits(12);
        format.setGroupingUsed(false);
        return format;
      });

  private DexpiXmlWriter() {}

  /**
   * Writes the provided {@link ProcessSystem} to a DEXPI XML file.
   *
   * @param processSystem process model to export
   * @param file output file
   * @throws IOException if writing fails
   */
  public static void write(ProcessSystem processSystem, File file) throws IOException {
    write(processSystem, file, null, null);
  }

  /**
   * Writes the provided {@link ProcessSystem} to a DEXPI XML file, including instrument and
   * controller data when provided.
   *
   * @param processSystem process model to export
   * @param file output file
   * @param transmitters map of tag name to transmitter (may be null)
   * @param controllers map of tag name to controller (may be null)
   * @throws IOException if writing fails
   */
  public static void write(ProcessSystem processSystem, File file,
      Map<String, MeasurementDeviceInterface> transmitters,
      Map<String, ControllerDeviceInterface> controllers) throws IOException {
    Objects.requireNonNull(processSystem, "processSystem");
    Objects.requireNonNull(file, "file");
    Path parent = file.toPath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (OutputStream outputStream = new FileOutputStream(file)) {
      write(processSystem, outputStream, transmitters, controllers);
    }
  }

  /**
   * Writes the provided {@link ProcessSystem} to a DEXPI XML stream.
   *
   * @param processSystem process model to export
   * @param outputStream destination stream
   * @throws IOException if writing fails
   */
  public static void write(ProcessSystem processSystem, OutputStream outputStream)
      throws IOException {
    write(processSystem, outputStream, null, null);
  }

  /**
   * Writes the provided {@link ProcessSystem} to a DEXPI XML stream, including instrument and
   * controller data when provided.
   *
   * @param processSystem process model to export
   * @param outputStream destination stream
   * @param transmitters map of tag name to transmitter (may be null)
   * @param controllers map of tag name to controller (may be null)
   * @throws IOException if writing fails
   */
  public static void write(ProcessSystem processSystem, OutputStream outputStream,
      Map<String, MeasurementDeviceInterface> transmitters,
      Map<String, ControllerDeviceInterface> controllers) throws IOException {
    Objects.requireNonNull(processSystem, "processSystem");
    Objects.requireNonNull(outputStream, "outputStream");

    Document document = createDocument();
    Element root = document.createElement("PlantModel");
    document.appendChild(root);

    root.appendChild(createPlantInformation(document));

    Set<String> usedIds = new LinkedHashSet<>();
    Map<String, List<DexpiStream>> segmentsBySystem = new LinkedHashMap<>();
    List<NozzleConnection> connections = new ArrayList<>();
    int nozzleCounter = 1;

    // Track equipment IDs -> outlet nozzle IDs for connection wiring
    Map<String, String> equipmentInletNozzle = new LinkedHashMap<>();
    // Map from outlet stream identity hash to its nozzle ID
    Map<Integer, String> outletStreamToNozzle = new HashMap<>();
    // Pre-built PipingComponent elements for valves, keyed by inlet nozzle ID
    Map<String, Element> valvePipingComponents = new LinkedHashMap<>();

    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof DexpiStream) {
        DexpiStream stream = (DexpiStream) unit;
        String systemKey = stream.getLineNumber();
        if (isBlank(systemKey)) {
          systemKey = stream.getFluidCode();
        }
        if (isBlank(systemKey)) {
          systemKey = "Segment";
        }
        segmentsBySystem.computeIfAbsent(systemKey, key -> new ArrayList<>()).add(stream);
      } else if (unit instanceof DexpiProcessUnit) {
        String inNozzle = "Nozzle-" + nozzleCounter++;
        List<String> outNozzles = new ArrayList<>();
        outNozzles.add("Nozzle-" + nozzleCounter++);
        if (DexpiStreamUtils.isMultiOutlet(unit)) {
          outNozzles.add("Nozzle-" + nozzleCounter++);
          if (unit instanceof ThreePhaseSeparator) {
            outNozzles.add("Nozzle-" + nozzleCounter++);
          }
        }
        appendProcessUnit(document, root, (DexpiProcessUnit) unit, usedIds, inNozzle, outNozzles);
        equipmentInletNozzle.put(unit.getName(), inNozzle);
        registerOutletNozzles(unit, outNozzles, outletStreamToNozzle);
      } else if (!(unit instanceof Stream)) {
        // Native NeqSim equipment — use reverse mapping
        String inNozzle = "Nozzle-" + nozzleCounter++;
        List<String> outNozzles = new ArrayList<>();
        outNozzles.add("Nozzle-" + nozzleCounter++);
        if (DexpiStreamUtils.isMultiOutlet(unit)) {
          outNozzles.add("Nozzle-" + nozzleCounter++);
          if (unit instanceof ThreePhaseSeparator) {
            outNozzles.add("Nozzle-" + nozzleCounter++);
          }
        }
        if (isValveType(unit)) {
          // Valves are PipingComponents in DEXPI — embed in PipingNetworkSegment, not top-level
          Element valveElement =
              buildValvePipingComponent(document, unit, usedIds, inNozzle, outNozzles);
          valvePipingComponents.put(inNozzle, valveElement);
        } else {
          appendNativeEquipment(document, root, unit, usedIds, inNozzle, outNozzles);
        }
        equipmentInletNozzle.put(unit.getName(), inNozzle);
        registerOutletNozzles(unit, outNozzles, outletStreamToNozzle);
      }
    }

    // Register pass-through streams (e.g., Stream wrapping a separator outlet)
    registerPassThroughStreams(processSystem, outletStreamToNozzle);

    // Build connections from process wiring using stream identity matching
    buildConnections(processSystem, outletStreamToNozzle, equipmentInletNozzle, connections);

    for (Map.Entry<String, List<DexpiStream>> entry : segmentsBySystem.entrySet()) {
      appendPipingNetworkSystem(document, root, entry.getKey(), entry.getValue(), usedIds);
    }

    // Write Connection elements and valve PipingComponents in a PipingNetworkSystem
    if (!connections.isEmpty() || !valvePipingComponents.isEmpty()) {
      appendConnectionSystem(document, root, connections, usedIds, valvePipingComponents);
    }

    if (transmitters != null && !transmitters.isEmpty()) {
      appendInstruments(document, root, transmitters, controllers, usedIds);
    }

    writeDocument(document, outputStream);
  }

  private static Document createDocument() throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setNamespaceAware(false);
      factory.setExpandEntityReferences(false);
      factory.setXIncludeAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.newDocument();
    } catch (ParserConfigurationException e) {
      throw new IOException("Unable to create XML document", e);
    }
  }

  private static Element createPlantInformation(Document document) {
    Element plantInformation = document.createElement("PlantInformation");
    plantInformation.setAttribute("Application", "NeqSim");
    plantInformation.setAttribute("ApplicationVersion",
        ProcessSystem.class.getPackage().getImplementationVersion() == null ? "1.0"
            : ProcessSystem.class.getPackage().getImplementationVersion());
    LocalDate date = LocalDate.now();
    LocalTime time = LocalTime.now();
    plantInformation.setAttribute("Date", date.toString());
    plantInformation.setAttribute("Time", time.format(DateTimeFormatter.ISO_LOCAL_TIME));
    plantInformation.setAttribute("Discipline", "Process");
    plantInformation.setAttribute("Is3D", "no");
    plantInformation.setAttribute("SchemaVersion", "4.1.1");
    plantInformation.setAttribute("Units", "mm");

    Element unitsOfMeasure = document.createElement("UnitsOfMeasure");
    plantInformation.appendChild(unitsOfMeasure);
    return plantInformation;
  }

  private static void appendProcessUnit(Document document, Element parent,
      DexpiProcessUnit processUnit, Set<String> usedIds, String inletNozzleId,
      List<String> outletNozzleIds) {
    EquipmentEnum mapped = processUnit.getMappedEquipment();
    String elementName = "Equipment";
    Element element = document.createElement(elementName);

    String componentClass =
        firstNonBlank(processUnit.getDexpiClass(), defaultComponentClass(mapped, elementName));
    element.setAttribute("ComponentClass", componentClass);
    element.setAttribute("ID", uniqueIdentifier(elementName, processUnit.getName(), usedIds));

    // Add Nozzle children
    appendNozzle(document, element, inletNozzleId, usedIds);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds);
    }

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.TAG_NAME,
        processUnit.getName());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.LINE_NUMBER,
        processUnit.getLineNumber());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.FLUID_CODE,
        processUnit.getFluidCode());

    // Export sizing attributes
    for (Map.Entry<String, String> sizing : processUnit.getSizingAttributes().entrySet()) {
      appendGenericAttribute(document, genericAttributes, sizing.getKey(), sizing.getValue());
    }

    if (genericAttributes.hasChildNodes()) {
      element.appendChild(genericAttributes);
    }

    parent.appendChild(element);
  }

  /**
   * Appends a native NeqSim equipment (non-DEXPI-origin) to the document using reverse mapping.
   *
   * @param document the XML document
   * @param parent the parent element
   * @param unit the process equipment
   * @param usedIds set of used IDs
   * @param inletNozzleId the inlet nozzle ID to create
   * @param outletNozzleIds the outlet nozzle IDs to create
   */
  private static void appendNativeEquipment(Document document, Element parent,
      ProcessEquipmentInterface unit, Set<String> usedIds, String inletNozzleId,
      List<String> outletNozzleIds) {
    String componentClass = reverseMapComponentClass(unit);
    String elementName = "Equipment";
    Element element = document.createElement(elementName);

    element.setAttribute("ComponentClass", componentClass);
    element.setAttribute("ID", uniqueIdentifier(elementName, unit.getName(), usedIds));

    // Add Nozzle children
    appendNozzle(document, element, inletNozzleId, usedIds);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds);
    }

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.TAG_NAME, unit.getName());

    // Export simulation results if the equipment has been run
    appendSimulationResults(document, genericAttributes, unit);

    if (genericAttributes.hasChildNodes()) {
      element.appendChild(genericAttributes);
    }

    parent.appendChild(element);
  }

  /**
   * Appends a Nozzle child element to the parent equipment element.
   *
   * @param document the XML document
   * @param parent the equipment element
   * @param nozzleId the nozzle ID
   * @param usedIds set of used IDs
   */
  private static void appendNozzle(Document document, Element parent, String nozzleId,
      Set<String> usedIds) {
    if (isBlank(nozzleId)) {
      return;
    }
    Element nozzle = document.createElement("Nozzle");
    nozzle.setAttribute("ID", nozzleId);
    usedIds.add(nozzleId);
    parent.appendChild(nozzle);
  }

  /**
   * Appends simulation result attributes (P, T, flow) from equipment outlet streams.
   *
   * @param document the XML document
   * @param genericAttributes the GenericAttributes element to append to
   * @param unit the process equipment
   */
  private static void appendSimulationResults(Document document, Element genericAttributes,
      ProcessEquipmentInterface unit) {
    StreamInterface outStream = getEquipmentOutlet(unit);
    if (outStream == null) {
      return;
    }
    try {
      double pressure = outStream.getPressure(DexpiMetadata.DEFAULT_PRESSURE_UNIT);
      appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_PRESSURE_VALUE,
          pressure, DexpiMetadata.DEFAULT_PRESSURE_UNIT);

      double temperature = outStream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
      appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_TEMPERATURE_VALUE,
          temperature, DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);

      double flowRate = outStream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT);
      appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_FLOW_VALUE,
          flowRate, DexpiMetadata.DEFAULT_FLOW_UNIT);
    } catch (RuntimeException ignored) {
      // Simulation may not have been run — properties unavailable
    }
  }

  /**
   * Gets the outlet stream of a process equipment for simulation result extraction.
   *
   * @param unit the process equipment
   * @return the outlet stream, or null if not available
   */
  private static StreamInterface getEquipmentOutlet(ProcessEquipmentInterface unit) {
    return DexpiStreamUtils.getGasOutletStream(unit);
  }

  /**
   * Returns the DEXPI ComponentClass string for a native NeqSim equipment type.
   *
   * @param unit the process equipment
   * @return the DEXPI ComponentClass name
   */
  private static String reverseMapComponentClass(ProcessEquipmentInterface unit) {
    if (unit instanceof ThreePhaseSeparator) {
      return "ThreePhaseSeparator";
    }
    if (unit instanceof Separator) {
      return "Separator";
    }
    if (unit instanceof Compressor) {
      return "CentrifugalCompressor";
    }
    if (unit instanceof Pump) {
      return "CentrifugalPump";
    }
    if (unit instanceof Cooler) {
      return "AirCoolingSystem";
    }
    if (unit instanceof HeatExchanger) {
      return "ShellAndTubeHeatExchanger";
    }
    if (unit instanceof Heater) {
      return "FiredHeater";
    }
    if (unit instanceof ThrottlingValve) {
      return "GlobeValve";
    }
    if (unit instanceof Expander) {
      return "Expander";
    }
    if (unit instanceof Mixer) {
      return "Mixer";
    }
    if (unit instanceof Splitter) {
      return "Splitter";
    }
    return "Equipment";
  }

  /**
   * Returns {@code true} if the unit represents a DEXPI piping component (valve) rather than an
   * Equipment element. In the DEXPI schema, valves are PipingComponent elements and should be
   * embedded inside PipingNetworkSegment elements rather than exported as top-level Equipment.
   *
   * @param unit the process equipment to check
   * @return true if the unit should be rendered as a PipingComponent
   */
  private static boolean isValveType(ProcessEquipmentInterface unit) {
    return unit instanceof ThrottlingValve;
  }

  /**
   * Builds a PipingComponent XML element for a valve unit.
   *
   * @param document the XML document
   * @param unit the valve unit
   * @param usedIds set of used IDs
   * @param inletNozzleId the inlet nozzle ID
   * @param outletNozzleIds the outlet nozzle IDs
   * @return the PipingComponent element
   */
  private static Element buildValvePipingComponent(Document document,
      ProcessEquipmentInterface unit, Set<String> usedIds, String inletNozzleId,
      List<String> outletNozzleIds) {
    String componentClass = reverseMapComponentClass(unit);
    Element element = document.createElement("PipingComponent");
    element.setAttribute("ComponentClass", componentClass);
    element.setAttribute("ID", uniqueIdentifier("PipingComponent", unit.getName(), usedIds));

    appendNozzle(document, element, inletNozzleId, usedIds);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds);
    }

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.TAG_NAME, unit.getName());
    appendSimulationResults(document, genericAttributes, unit);
    if (genericAttributes.hasChildNodes()) {
      element.appendChild(genericAttributes);
    }

    return element;
  }

  /**
   * Simple connection descriptor linking an outlet nozzle to an inlet nozzle.
   */
  private static class NozzleConnection {
    private final String fromNozzle;
    private final String toNozzle;

    NozzleConnection(String fromNozzle, String toNozzle) {
      this.fromNozzle = fromNozzle;
      this.toNozzle = toNozzle;
    }
  }

  /**
   * Registers outlet stream identity hashes to their nozzle IDs.
   *
   * <p>
   * For single-outlet equipment, the first (and only) nozzle maps to the gas/primary outlet. For
   * separators, the first nozzle maps to the gas outlet, the second to the liquid outlet, and for
   * three-phase separators, the third maps to the water outlet.
   * </p>
   *
   * @param unit the process equipment
   * @param outNozzles the list of outlet nozzle IDs
   * @param outletStreamToNozzle map to populate with identity hash to nozzle ID
   */
  private static void registerOutletNozzles(ProcessEquipmentInterface unit, List<String> outNozzles,
      Map<Integer, String> outletStreamToNozzle) {
    StreamInterface gasOut = DexpiStreamUtils.getGasOutletStream(unit);
    if (gasOut != null && !outNozzles.isEmpty()) {
      outletStreamToNozzle.put(System.identityHashCode(gasOut), outNozzles.get(0));
    }
    if (outNozzles.size() > 1) {
      StreamInterface liqOut = DexpiStreamUtils.getLiquidOutletStream(unit);
      if (liqOut != null) {
        outletStreamToNozzle.put(System.identityHashCode(liqOut), outNozzles.get(1));
      }
    }
    if (outNozzles.size() > 2) {
      StreamInterface waterOut = DexpiStreamUtils.getWaterOutletStream(unit);
      if (waterOut != null) {
        outletStreamToNozzle.put(System.identityHashCode(waterOut), outNozzles.get(2));
      }
    }
  }

  /**
   * Registers pass-through Streams in the outlet-stream-to-nozzle map.
   *
   * <p>
   * When a user creates {@code new Stream("gas-out", separator.getGasOutStream())}, the wrapping
   * Stream delegates {@code getFluid()} to the source. This method detects such wrappers by
   * matching fluid identity and registers the wrapper itself so downstream equipment that took the
   * wrapper as its inlet can be connected.
   * </p>
   *
   * @param processSystem the process system
   * @param outletStreamToNozzle map of outlet stream identity hash to nozzle ID
   */
  private static void registerPassThroughStreams(ProcessSystem processSystem,
      Map<Integer, String> outletStreamToNozzle) {
    // Build a lookup of registered outlet fluid identity -> nozzle ID
    Map<Integer, String> fluidToNozzle = new HashMap<>();
    for (Map.Entry<Integer, String> entry : outletStreamToNozzle.entrySet()) {
      // Find the stream object matching this identity hash
      for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
        StreamInterface outStream = DexpiStreamUtils.getGasOutletStream(unit);
        if (outStream != null && System.identityHashCode(outStream) == entry.getKey()
            && outStream.getFluid() != null) {
          fluidToNozzle.put(System.identityHashCode(outStream.getFluid()), entry.getValue());
        }
        StreamInterface liqStream = DexpiStreamUtils.getLiquidOutletStream(unit);
        if (liqStream != null && System.identityHashCode(liqStream) == entry.getKey()
            && liqStream.getFluid() != null) {
          fluidToNozzle.put(System.identityHashCode(liqStream.getFluid()), entry.getValue());
        }
        StreamInterface waterStream = DexpiStreamUtils.getWaterOutletStream(unit);
        if (waterStream != null && System.identityHashCode(waterStream) == entry.getKey()
            && waterStream.getFluid() != null) {
          fluidToNozzle.put(System.identityHashCode(waterStream.getFluid()), entry.getValue());
        }
      }
    }
    // For each plain Stream in the process, check if its fluid delegates to a registered outlet
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof Stream && !(unit instanceof DexpiStream)) {
        Stream stream = (Stream) unit;
        if (stream.getFluid() != null) {
          String nozzle = fluidToNozzle.get(System.identityHashCode(stream.getFluid()));
          if (nozzle != null) {
            outletStreamToNozzle.put(System.identityHashCode(stream), nozzle);
          }
        }
      }
    }
  }

  /**
   * Builds connections between equipment by matching inlet stream identity to outlet stream
   * nozzles.
   *
   * <p>
   * For each equipment that extends {@link TwoPortEquipment}, the inlet stream is looked up in the
   * outlet-stream-to-nozzle map to find the upstream nozzle. This correctly resolves branching
   * (e.g. separator gas and liquid outlets going to different downstream equipment).
   * </p>
   *
   * @param processSystem the process system
   * @param outletStreamToNozzle map of outlet stream identity hash to nozzle ID
   * @param inletNozzles map of equipment name to inlet nozzle ID
   * @param connections list to populate with connections
   */
  private static void buildConnections(ProcessSystem processSystem,
      Map<Integer, String> outletStreamToNozzle, Map<String, String> inletNozzles,
      List<NozzleConnection> connections) {
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof Stream || unit instanceof DexpiStream) {
        continue;
      }
      if (unit instanceof TwoPortEquipment) {
        StreamInterface inletStream = ((TwoPortEquipment) unit).getInletStream();
        if (inletStream != null) {
          String fromNozzle = outletStreamToNozzle.get(System.identityHashCode(inletStream));
          String toNozzle = inletNozzles.get(unit.getName());
          if (fromNozzle != null && toNozzle != null) {
            connections.add(new NozzleConnection(fromNozzle, toNozzle));
          }
        }
      }
    }
  }

  /**
   * Appends a PipingNetworkSystem containing Connection elements for equipment wiring.
   *
   * @param document the XML document
   * @param parent the root element
   * @param connections the list of connections
   * @param usedIds set of used IDs
   * @param valvePipingComponents pre-built PipingComponent elements for valves, keyed by inlet
   *        nozzle ID
   */
  private static void appendConnectionSystem(Document document, Element parent,
      List<NozzleConnection> connections, Set<String> usedIds,
      Map<String, Element> valvePipingComponents) {
    Element systemElement = document.createElement("PipingNetworkSystem");
    systemElement.setAttribute("ComponentClass", "PipingNetworkSystem");
    systemElement.setAttribute("ID",
        uniqueIdentifier("PipingNetworkSystem", "Connections", usedIds));

    Set<String> consumedValveNozzles = new HashSet<>();

    for (NozzleConnection conn : connections) {
      Element segmentElement = document.createElement("PipingNetworkSegment");
      segmentElement.setAttribute("ComponentClass", "PipingNetworkSegment");
      segmentElement.setAttribute("ID", uniqueIdentifier("PipingNetworkSegment", "Conn", usedIds));

      // Check if the target nozzle belongs to a pre-built valve PipingComponent
      Element valveElement = valvePipingComponents.get(conn.toNozzle);
      if (valveElement != null) {
        // Embed the valve as a PipingComponent inside this segment
        segmentElement.appendChild(valveElement);
        consumedValveNozzles.add(conn.toNozzle);
      } else {
        // Add an inline PipingComponent so the segment has at least one item.
        // pyDEXPI requires non-empty segment items for connection resolution.
        String pipeId = uniqueIdentifier("PipingComponent", "Conn", usedIds);
        Element pipeElement = document.createElement("PipingComponent");
        pipeElement.setAttribute("ComponentClass", "PipingComponent");
        pipeElement.setAttribute("ID", pipeId);
        String pipeNozzleIn = uniqueIdentifier("Nozzle", pipeId + "-in", usedIds);
        String pipeNozzleOut = uniqueIdentifier("Nozzle", pipeId + "-out", usedIds);
        Element pipeNzIn = document.createElement("Nozzle");
        pipeNzIn.setAttribute("ID", pipeNozzleIn);
        pipeElement.appendChild(pipeNzIn);
        Element pipeNzOut = document.createElement("Nozzle");
        pipeNzOut.setAttribute("ID", pipeNozzleOut);
        pipeElement.appendChild(pipeNzOut);
        segmentElement.appendChild(pipeElement);
      }

      // Single Connection element per segment (pyDEXPI requirement)
      Element connElement = document.createElement("Connection");
      connElement.setAttribute("FromID", conn.fromNozzle);
      connElement.setAttribute("ToID", conn.toNozzle);
      segmentElement.appendChild(connElement);

      systemElement.appendChild(segmentElement);
    }

    // Add standalone segments for any valve PipingComponents not consumed by connections
    for (Map.Entry<String, Element> entry : valvePipingComponents.entrySet()) {
      if (!consumedValveNozzles.contains(entry.getKey())) {
        Element segmentElement = document.createElement("PipingNetworkSegment");
        segmentElement.setAttribute("ComponentClass", "PipingNetworkSegment");
        segmentElement.setAttribute("ID",
            uniqueIdentifier("PipingNetworkSegment", "Valve", usedIds));
        segmentElement.appendChild(entry.getValue());
        systemElement.appendChild(segmentElement);
      }
    }

    parent.appendChild(systemElement);
  }

  private static void appendPipingNetworkSystem(Document document, Element parent, String key,
      List<DexpiStream> streams, Set<String> usedIds) {
    Element systemElement = document.createElement("PipingNetworkSystem");
    systemElement.setAttribute("ComponentClass", "PipingNetworkSystem");
    systemElement.setAttribute("ID", uniqueIdentifier("Line", key, usedIds));

    Element systemAttributes = document.createElement("GenericAttributes");
    systemAttributes.setAttribute("Set", "DexpiAttributes");
    String lineNumber = streams.stream().map(DexpiStream::getLineNumber)
        .filter(value -> !isBlank(value)).findFirst().orElse(null);
    appendGenericAttribute(document, systemAttributes, DexpiMetadata.LINE_NUMBER, lineNumber);
    String fluidCode = streams.stream().map(DexpiStream::getFluidCode)
        .filter(value -> !isBlank(value)).findFirst().orElse(null);
    appendGenericAttribute(document, systemAttributes, DexpiMetadata.FLUID_CODE, fluidCode);
    appendGenericAttribute(document, systemAttributes, "NeqSimGroupingKey", key);
    if (systemAttributes.hasChildNodes()) {
      systemElement.appendChild(systemAttributes);
    }

    for (DexpiStream stream : streams) {
      appendPipingNetworkSegment(document, systemElement, stream, usedIds);
    }

    parent.appendChild(systemElement);
  }

  private static void appendPipingNetworkSegment(Document document, Element parent,
      DexpiStream stream, Set<String> usedIds) {
    Element segmentElement = document.createElement("PipingNetworkSegment");
    String componentClass = firstNonBlank(stream.getDexpiClass(), "PipingNetworkSegment");
    segmentElement.setAttribute("ComponentClass", componentClass);
    segmentElement.setAttribute("ID", uniqueIdentifier("Segment", stream.getName(), usedIds));

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.SEGMENT_NUMBER,
        stream.getName());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.LINE_NUMBER,
        stream.getLineNumber());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.FLUID_CODE,
        stream.getFluidCode());
    appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_PRESSURE_VALUE,
        stream.getPressure(DexpiMetadata.DEFAULT_PRESSURE_UNIT),
        DexpiMetadata.DEFAULT_PRESSURE_UNIT);
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_PRESSURE_UNIT,
        DexpiMetadata.DEFAULT_PRESSURE_UNIT);
    appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_TEMPERATURE_VALUE,
        stream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT),
        DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_TEMPERATURE_UNIT,
        DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
    appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_FLOW_VALUE,
        stream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT), DexpiMetadata.DEFAULT_FLOW_UNIT);
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_FLOW_UNIT,
        DexpiMetadata.DEFAULT_FLOW_UNIT);
    if (genericAttributes.hasChildNodes()) {
      segmentElement.appendChild(genericAttributes);
    }

    parent.appendChild(segmentElement);
  }

  private static void appendGenericAttribute(Document document, Element parent, String name,
      String value) {
    appendGenericAttribute(document, parent, name, value, null);
  }

  private static void appendGenericAttribute(Document document, Element parent, String name,
      String value, String unit) {
    if (isBlank(value)) {
      return;
    }
    Element attribute = document.createElement("GenericAttribute");
    attribute.setAttribute("Name", name);
    attribute.setAttribute("Value", value.trim());
    if (!isBlank(unit)) {
      attribute.setAttribute("Unit", unit.trim());
    }
    parent.appendChild(attribute);
  }

  private static void appendNumericAttribute(Document document, Element parent, String name,
      double value, String unit) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return;
    }
    appendGenericAttribute(document, parent, name, DECIMAL_FORMAT.get().format(value), unit);
  }

  /**
   * Appends DEXPI instrumentation elements for transmitters and controllers.
   *
   * <p>
   * Each transmitter becomes a {@code ProcessInstrumentationFunction} with a
   * {@code ProcessSignalGeneratingFunction} child. Controllers that share a loop tag with a
   * transmitter are linked via {@code SignalConveyingFunction} and {@code ActuatingFunction}.
   * Finally, an {@code InstrumentationLoopFunction} groups each loop's elements.
   * </p>
   *
   * @param document the XML document
   * @param parent the root element to append to
   * @param transmitters map of tag to transmitter
   * @param controllers map of tag to controller (may be null)
   * @param usedIds set of already used XML IDs
   */
  private static void appendInstruments(Document document, Element parent,
      Map<String, MeasurementDeviceInterface> transmitters,
      Map<String, ControllerDeviceInterface> controllers, Set<String> usedIds) {

    int loopIndex = 1;
    for (Map.Entry<String, MeasurementDeviceInterface> entry : transmitters.entrySet()) {
      String tag = entry.getKey();
      MeasurementDeviceInterface device = entry.getValue();

      String[] parsed = parseIsaTag(tag);
      String category = parsed[0];
      String functions = parsed[1];
      String loopNumber = parsed[2];

      // ProcessInstrumentationFunction (the instrument bubble)
      String pifId = uniqueIdentifier("ProcessInstrumentationFunction", tag, usedIds);
      Element pif = document.createElement("ProcessInstrumentationFunction");
      pif.setAttribute("ID", pifId);
      pif.setAttribute("ComponentClass", "ProcessInstrumentationFunction");

      Element pifAttrs = document.createElement("GenericAttributes");
      pifAttrs.setAttribute("Set", "DexpiAttributes");
      appendGenericAttribute(document, pifAttrs, DexpiMetadata.INSTRUMENTATION_CATEGORY, category);
      appendGenericAttribute(document, pifAttrs, DexpiMetadata.INSTRUMENTATION_FUNCTIONS,
          functions);
      appendGenericAttribute(document, pifAttrs, DexpiMetadata.INSTRUMENTATION_NUMBER, loopNumber);
      appendGenericAttribute(document, pifAttrs, DexpiMetadata.TAG_NAME, tag);
      appendGenericAttribute(document, pifAttrs, "MeasurementUnit", device.getUnit());
      if (pifAttrs.hasChildNodes()) {
        pif.appendChild(pifAttrs);
      }

      // ProcessSignalGeneratingFunction (the sensor)
      String psgfId = uniqueIdentifier("ProcessSignalGeneratingFunction", tag, usedIds);
      Element psgf = document.createElement("ProcessSignalGeneratingFunction");
      psgf.setAttribute("ID", psgfId);
      psgf.setAttribute("ComponentClass", "ProcessSignalGeneratingFunction");

      Element psgfAttrs = document.createElement("GenericAttributes");
      psgfAttrs.setAttribute("Set", "DexpiAttributes");
      appendGenericAttribute(document, psgfAttrs, DexpiMetadata.SIGNAL_GENERATING_NUMBER, tag);
      if (psgfAttrs.hasChildNodes()) {
        psgf.appendChild(psgfAttrs);
      }
      pif.appendChild(psgf);

      // Look for matching controller (e.g. PT-xxx -> PC-xxx, LT-xxx -> LC-xxx)
      String controllerTag = deriveControllerTag(tag);
      ControllerDeviceInterface matchedController = null;
      if (controllers != null && controllerTag != null) {
        matchedController = controllers.get(controllerTag);
      }

      if (matchedController != null) {
        // Record controller tag as a generic attribute on the PIF.
        // Full ActuatingSystem/ActuatingFunction modelling requires valve linkage
        // data not yet available in the NeqSim controller model.
        appendGenericAttribute(document, pifAttrs, "ControllerTag", controllerTag);
      }

      parent.appendChild(pif);

      // InstrumentationLoopFunction (groups everything in the loop)
      String loopId = uniqueIdentifier("InstrumentationLoopFunction", "Loop-" + loopIndex, usedIds);
      Element loop = document.createElement("InstrumentationLoopFunction");
      loop.setAttribute("ID", loopId);
      loop.setAttribute("ComponentClass", "InstrumentationLoopFunction");

      Element loopAttrs = document.createElement("GenericAttributes");
      loopAttrs.setAttribute("Set", "DexpiAttributes");
      appendGenericAttribute(document, loopAttrs, DexpiMetadata.LOOP_NUMBER, loopNumber);
      if (loopAttrs.hasChildNodes()) {
        loop.appendChild(loopAttrs);
      }

      Element loopAssoc = document.createElement("Association");
      loopAssoc.setAttribute("Type", "is a collection including");
      loopAssoc.setAttribute("ItemID", pifId);
      loop.appendChild(loopAssoc);

      parent.appendChild(loop);
      loopIndex++;
    }
  }

  /**
   * Parses an ISA-style tag (e.g. "PT-HP sep") into category, function letters, and loop number.
   *
   * @param tag the ISA tag string
   * @return array of [category, functions, loopNumber]
   */
  private static String[] parseIsaTag(String tag) {
    if (tag == null || tag.isEmpty()) {
      return new String[] {"", "", tag};
    }
    int dashIndex = tag.indexOf('-');
    if (dashIndex <= 0) {
      return new String[] {"", "", tag};
    }
    String prefix = tag.substring(0, dashIndex);
    String loopNumber = tag.substring(dashIndex + 1);

    // First letter is the category (P, T, L, F, etc.)
    String category = prefix.substring(0, 1);
    // Remaining letters are the function (T, IC, C, etc.)
    String functions = prefix.length() > 1 ? prefix.substring(1) : "T";

    return new String[] {category, functions, loopNumber};
  }

  /**
   * Derives the controller tag from a transmitter tag. For example, "PT-HP sep" becomes "PC-HP
   * sep", "LT-HP sep" becomes "LC-HP sep".
   *
   * @param transmitterTag the transmitter tag
   * @return the expected controller tag, or null if not derivable
   */
  private static String deriveControllerTag(String transmitterTag) {
    if (transmitterTag == null || transmitterTag.isEmpty()) {
      return null;
    }
    int dashIndex = transmitterTag.indexOf('-');
    if (dashIndex <= 0) {
      return null;
    }
    String prefix = transmitterTag.substring(0, dashIndex);
    String suffix = transmitterTag.substring(dashIndex);

    // Map transmitter prefix to controller prefix
    if ("PT".equals(prefix)) {
      return "PC" + suffix;
    } else if ("LT".equals(prefix)) {
      return "LC" + suffix;
    } else if ("FT".equals(prefix)) {
      return "FC" + suffix;
    } else if ("TT".equals(prefix)) {
      return "TC" + suffix;
    }
    return null;
  }

  private static String defaultComponentClass(EquipmentEnum mapped, String elementName) {
    if (mapped == null) {
      return elementName;
    }
    switch (mapped) {
      case HeatExchanger:
        return "HeatExchanger";
      case Pump:
        return "Pump";
      case Tank:
        return "Tank";
      case ThrottlingValve:
        return "GlobeValve";
      default:
        return elementName;
    }
  }

  private static String uniqueIdentifier(String prefix, String name, Set<String> usedIds) {
    String sanitizedName = sanitizeIdentifier(name);
    String base = sanitizedName.isEmpty() ? prefix : sanitizedName;
    String candidate = base;
    int index = 2;
    while (usedIds.contains(candidate)) {
      candidate = base + "-" + index;
      index++;
    }
    usedIds.add(candidate);
    return candidate;
  }

  private static String sanitizeIdentifier(String name) {
    if (name == null) {
      return "";
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    String replaced = NON_IDENTIFIER.matcher(trimmed).replaceAll("-");
    replaced = replaced.replaceAll("-+", "-");
    replaced = stripHyphens(replaced);
    if (replaced.isEmpty()) {
      return "";
    }
    if (!Character.isLetter(replaced.charAt(0))) {
      replaced = "ID-" + replaced;
    }
    return replaced;
  }

  private static String stripHyphens(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && value.charAt(start) == '-') {
      start++;
    }
    while (end > start && value.charAt(end - 1) == '-') {
      end--;
    }
    return value.substring(start, end);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (!isBlank(value)) {
        return value.trim();
      }
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static void writeDocument(Document document, OutputStream outputStream)
      throws IOException {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      transformer.transform(new DOMSource(document), new StreamResult(outputStream));
    } catch (TransformerException e) {
      throw new IOException("Unable to serialize DEXPI document", e);
    } catch (TransformerFactoryConfigurationError e) {
      throw new IOException("Unable to configure XML transformer", e);
    }
  }
}
