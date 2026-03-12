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
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
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
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
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

    // Compute auto-layout positions for graphical rendering
    Map<String, DexpiLayoutEngine.EquipmentPosition> layoutPositions =
        DexpiLayoutEngine.computeLayout(processSystem);
    int labelCounter = 1;

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
    // Nozzle positions for connection line geometry (nozzle ID -> {x, y})
    Map<String, double[]> nozzlePositions = new HashMap<>();

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
        registerNozzlePositions(layoutPositions.get(unit.getName()), inNozzle, outNozzles,
            nozzlePositions);
        appendProcessUnit(document, root, (DexpiProcessUnit) unit, usedIds, inNozzle, outNozzles,
            layoutPositions.get(unit.getName()), labelCounter++, nozzlePositions);
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
        registerNozzlePositions(layoutPositions.get(unit.getName()), inNozzle, outNozzles,
            nozzlePositions);
        if (isValveType(unit)) {
          // Valves are PipingComponents in DEXPI — embed in PipingNetworkSegment, not
          // top-level
          Element valveElement = buildValvePipingComponent(document, unit, usedIds, inNozzle,
              outNozzles, layoutPositions.get(unit.getName()), labelCounter++, nozzlePositions);
          valvePipingComponents.put(inNozzle, valveElement);
        } else {
          appendNativeEquipment(document, root, unit, usedIds, inNozzle, outNozzles,
              layoutPositions.get(unit.getName()), labelCounter++, nozzlePositions);
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
      appendConnectionSystem(document, root, connections, usedIds, valvePipingComponents,
          nozzlePositions);
    }

    // Auto-collect instruments from ProcessSystem if not explicitly provided
    Map<String, MeasurementDeviceInterface> effectiveTransmitters = transmitters;
    Map<String, ControllerDeviceInterface> effectiveControllers = controllers;
    if ((effectiveTransmitters == null || effectiveTransmitters.isEmpty())
        && !processSystem.getMeasurementDevices().isEmpty()) {
      effectiveTransmitters = new LinkedHashMap<>();
      for (MeasurementDeviceInterface md : processSystem.getMeasurementDevices()) {
        if (md.getName() != null && !md.getName().trim().isEmpty()) {
          effectiveTransmitters.put(md.getName(), md);
        }
      }
    }
    if ((effectiveControllers == null || effectiveControllers.isEmpty())
        && !processSystem.getControllerDevices().isEmpty()) {
      effectiveControllers = new LinkedHashMap<>();
      for (ControllerDeviceInterface cd : processSystem.getControllerDevices()) {
        if (cd.getName() != null && !cd.getName().trim().isEmpty()) {
          effectiveControllers.put(cd.getName(), cd);
        }
      }
    }

    if (effectiveTransmitters != null && !effectiveTransmitters.isEmpty()) {
      appendInstruments(document, root, effectiveTransmitters, effectiveControllers, usedIds,
          layoutPositions, nozzlePositions, processSystem);
    }

    // Collect stream data for stream table
    List<DexpiLayoutEngine.StreamTableEntry> streamData = collectStreamTableData(processSystem);
    if (!streamData.isEmpty()) {
      DexpiLayoutEngine.appendStreamTable(document, root, streamData);
    }

    // Append ShapeCatalogue with ISO 10628 standard shapes
    DexpiShapeCatalog.appendShapeCatalogue(document, root);

    // Append Drawing with professional border, zone markers, and title block
    double[] sheetSize = DexpiLayoutEngine.computeSheetSize(layoutPositions);
    String drawingName = processSystem.getName();
    String today = LocalDate.now().toString();
    DexpiLayoutEngine.appendDrawing(document, root, drawingName, "PID-001", "0", today,
        sheetSize[0], sheetSize[1]);

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
      List<String> outletNozzleIds, DexpiLayoutEngine.EquipmentPosition position, int labelIndex,
      Map<String, double[]> nozzlePositions) {
    EquipmentEnum mapped = processUnit.getMappedEquipment();
    String elementName = "Equipment";
    Element element = document.createElement(elementName);

    String componentClass =
        firstNonBlank(processUnit.getDexpiClass(), defaultComponentClass(mapped, elementName));
    element.setAttribute("ComponentClass", componentClass);
    String equipmentId = uniqueIdentifier(elementName, processUnit.getName(), usedIds);
    element.setAttribute("ID", equipmentId);

    // Add ComponentName referencing shape in ShapeCatalogue
    String shapeName = DexpiShapeCatalog.getShapeName(componentClass);
    if (shapeName != null) {
      element.setAttribute("ComponentName", shapeName);
    }

    // Add graphical layout (Position, Scale, Label)
    if (position != null) {
      DexpiLayoutEngine.appendPosition(document, element, position);
      DexpiLayoutEngine.appendScale(document, element, position);
      String labelId = "EquipmentTagNameLabel-" + labelIndex;
      DexpiLayoutEngine.appendTagNameLabel(document, element, processUnit.getName(), position,
          labelId, equipmentId);
    }

    // Add Nozzle children
    appendNozzle(document, element, inletNozzleId, usedIds, nozzlePositions);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds, nozzlePositions);
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
   * @param position the computed layout position (may be null)
   * @param labelIndex label counter for unique IDs
   * @param nozzlePositions map of nozzle positions for graphical rendering
   */
  private static void appendNativeEquipment(Document document, Element parent,
      ProcessEquipmentInterface unit, Set<String> usedIds, String inletNozzleId,
      List<String> outletNozzleIds, DexpiLayoutEngine.EquipmentPosition position, int labelIndex,
      Map<String, double[]> nozzlePositions) {
    String componentClass = reverseMapComponentClass(unit);
    String elementName = "Equipment";
    Element element = document.createElement(elementName);

    element.setAttribute("ComponentClass", componentClass);
    String equipmentId = uniqueIdentifier(elementName, unit.getName(), usedIds);
    element.setAttribute("ID", equipmentId);

    // Add ComponentName referencing shape in ShapeCatalogue
    String shapeName = DexpiShapeCatalog.getShapeName(componentClass);
    if (shapeName != null) {
      element.setAttribute("ComponentName", shapeName);
    }

    // Add graphical layout (Position, Scale, Label, EquipmentBarLabel)
    if (position != null) {
      DexpiLayoutEngine.appendPosition(document, element, position);
      DexpiLayoutEngine.appendScale(document, element, position);
      String labelId = "EquipmentTagNameLabel-" + labelIndex;
      DexpiLayoutEngine.appendTagNameLabel(document, element, unit.getName(), position, labelId,
          equipmentId);
      // Add equipment bar label with P/T/flow data
      appendEquipmentBarFromSimulation(document, element, unit, position,
          "EquipmentBarLabel-" + labelIndex, equipmentId);
    }

    // Add Nozzle children
    appendNozzle(document, element, inletNozzleId, usedIds, nozzlePositions);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds, nozzlePositions);
    }

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.TAG_NAME, unit.getName());

    // Export simulation results if the equipment has been run
    appendSimulationResults(document, genericAttributes, unit);

    if (genericAttributes.hasChildNodes()) {
      element.appendChild(genericAttributes);
    }

    // Export mechanical design attributes
    appendMechanicalDesignAttributes(document, element, unit);

    parent.appendChild(element);
  }

  /**
   * Appends a Nozzle child element to the parent equipment element with optional position.
   *
   * @param document the XML document
   * @param parent the equipment element
   * @param nozzleId the nozzle ID
   * @param usedIds set of used IDs
   * @param nozzlePositions map of nozzle positions (may be null)
   */
  private static void appendNozzle(Document document, Element parent, String nozzleId,
      Set<String> usedIds, Map<String, double[]> nozzlePositions) {
    if (isBlank(nozzleId)) {
      return;
    }
    Element nozzle = document.createElement("Nozzle");
    nozzle.setAttribute("ID", nozzleId);
    nozzle.setAttribute("ComponentClass", "Nozzle");
    nozzle.setAttribute("ComponentClassURI", "http://data.posccaesar.org/rdl/RDS415214");
    nozzle.setAttribute("ComponentName", DexpiShapeCatalog.NOZZLE_SHAPE);

    // Add graphical position if available
    if (nozzlePositions != null) {
      double[] pos = nozzlePositions.get(nozzleId);
      if (pos != null) {
        DexpiLayoutEngine.appendNozzlePosition(document, nozzle, pos[0], pos[1]);
      }
    }

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
   * Appends a GenericAttributes set with mechanical design parameters for the equipment. Only
   * attributes with non-zero/non-default values are exported.
   *
   * @param document the XML document
   * @param parent the equipment element
   * @param unit the process equipment
   */
  private static void appendMechanicalDesignAttributes(Document document, Element parent,
      ProcessEquipmentInterface unit) {
    neqsim.process.mechanicaldesign.MechanicalDesign md = unit.getMechanicalDesign();
    if (md == null) {
      return;
    }

    Element attrs = document.createElement("GenericAttributes");
    attrs.setAttribute("Set", "MechanicalDesign");

    if (md.getInnerDiameter() > 0) {
      appendNumericAttribute(document, attrs, "InnerDiameter", md.getInnerDiameter(), "mm");
    }
    if (md.getOuterDiameter() > 0) {
      appendNumericAttribute(document, attrs, "OuterDiameter", md.getOuterDiameter(), "mm");
    }
    if (md.getWallThickness() > 0) {
      appendNumericAttribute(document, attrs, "WallThickness", md.getWallThickness(), "mm");
    }
    if (md.getTantanLength() > 0) {
      appendNumericAttribute(document, attrs, "TanTanLength", md.getTantanLength(), "mm");
    }
    String material = md.getConstrutionMaterial();
    if (material != null && !material.trim().isEmpty()) {
      appendGenericAttribute(document, attrs, "ConstructionMaterial", material);
    }
    if (md.getMaxOperationPressure() > 0) {
      appendNumericAttribute(document, attrs, "MaxOperationPressure", md.getMaxOperationPressure(),
          "bara");
    }
    if (md.getMaxDesignPressure() > 0 && md.getMaxOperationPressure() > 0) {
      appendNumericAttribute(document, attrs, "MaxDesignPressure", md.getMaxDesignPressure(),
          "bara");
    }
    if (md.getWeightTotal() > 0) {
      appendNumericAttribute(document, attrs, "WeightTotal", md.getWeightTotal(), "kg");
    }
    if (md.getPower() > 0) {
      appendNumericAttribute(document, attrs, "DesignPower", md.getPower(), "kW");
    }
    if (md.getDuty() > 0) {
      appendNumericAttribute(document, attrs, "DesignDuty", md.getDuty(), "kW");
    }

    if (attrs.hasChildNodes()) {
      parent.appendChild(attrs);
    }
  }

  /**
   * Appends an EquipmentBarLabel using simulation results from the equipment outlet.
   *
   * @param document the XML document
   * @param element the equipment element
   * @param unit the process equipment
   * @param position the equipment position
   * @param labelId the unique label ID
   * @param equipmentId the equipment element ID
   */
  private static void appendEquipmentBarFromSimulation(Document document, Element element,
      ProcessEquipmentInterface unit, DexpiLayoutEngine.EquipmentPosition position, String labelId,
      String equipmentId) {
    StreamInterface outStream = getEquipmentOutlet(unit);
    double pressure = Double.NaN;
    double temperature = Double.NaN;
    double flowRate = Double.NaN;
    if (outStream != null) {
      try {
        pressure = outStream.getPressure(DexpiMetadata.DEFAULT_PRESSURE_UNIT);
        temperature = outStream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
        flowRate = outStream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT);
      } catch (RuntimeException ignored) {
        // Simulation unavailable
      }
    }

    // Collect mechanical design parameters
    List<String[]> mechRows = collectMechanicalDesignRows(unit);

    boolean hasSimData =
        !Double.isNaN(pressure) || !Double.isNaN(temperature) || !Double.isNaN(flowRate);
    if (!hasSimData && mechRows.isEmpty()) {
      return;
    }

    DexpiLayoutEngine.appendEquipmentBarLabel(document, element, unit.getName(), position, labelId,
        equipmentId, pressure, temperature, flowRate, mechRows.isEmpty() ? null : mechRows);
  }

  /**
   * Collects mechanical design parameters from equipment for display in the bar label. Returns rows
   * only for parameters that have been set (non-zero, non-default).
   *
   * @param unit the process equipment
   * @return list of label-value pairs (may be empty)
   */
  private static List<String[]> collectMechanicalDesignRows(ProcessEquipmentInterface unit) {
    List<String[]> rows = new ArrayList<>();
    neqsim.process.mechanicaldesign.MechanicalDesign md = unit.getMechanicalDesign();
    if (md == null) {
      return rows;
    }

    if (md.getInnerDiameter() > 0) {
      rows.add(new String[] {"ID", formatMechValue(md.getInnerDiameter()) + " mm"});
    }
    if (md.getWallThickness() > 0) {
      rows.add(new String[] {"Wall Thk.", formatMechValue(md.getWallThickness()) + " mm"});
    }
    if (md.getTantanLength() > 0) {
      rows.add(new String[] {"Length", formatMechValue(md.getTantanLength()) + " mm"});
    }
    String material = md.getConstrutionMaterial();
    if (material != null && !material.trim().isEmpty() && !"steel".equals(material)) {
      rows.add(new String[] {"Material", material});
    }
    if (md.getMaxDesignPressure() > 0 && md.getMaxOperationPressure() > 0) {
      rows.add(new String[] {"Design P.", formatMechValue(md.getMaxDesignPressure()) + " bara"});
    }
    if (md.getWeightTotal() > 0) {
      rows.add(new String[] {"Weight", formatMechValue(md.getWeightTotal()) + " kg"});
    }
    return rows;
  }

  /**
   * Formats a mechanical design value for display.
   *
   * @param value the value to format
   * @return formatted string
   */
  private static String formatMechValue(double value) {
    if (Math.abs(value - Math.round(value)) < 0.01) {
      return String.valueOf((long) Math.round(value));
    }
    return String.format(Locale.ROOT, "%.1f", value);
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
   * @param position the computed layout position (may be null)
   * @param labelIndex label counter for unique IDs
   * @param nozzlePositions map of nozzle positions for graphical rendering
   * @return the PipingComponent element
   */
  private static Element buildValvePipingComponent(Document document,
      ProcessEquipmentInterface unit, Set<String> usedIds, String inletNozzleId,
      List<String> outletNozzleIds, DexpiLayoutEngine.EquipmentPosition position, int labelIndex,
      Map<String, double[]> nozzlePositions) {
    String componentClass = reverseMapComponentClass(unit);
    Element element = document.createElement("PipingComponent");
    element.setAttribute("ComponentClass", componentClass);
    String valveId = uniqueIdentifier("PipingComponent", unit.getName(), usedIds);
    element.setAttribute("ID", valveId);
    String shapeName = DexpiShapeCatalog.getShapeName(componentClass);
    if (shapeName != null) {
      element.setAttribute("ComponentName", shapeName);
    }

    // Add graphical layout (Position, Scale, Label)
    if (position != null) {
      DexpiLayoutEngine.appendPosition(document, element, position);
      DexpiLayoutEngine.appendScale(document, element, position);
      String labelId = "ValveTagNameLabel-" + labelIndex;
      DexpiLayoutEngine.appendTagNameLabel(document, element, unit.getName(), position, labelId,
          valveId);
    }

    appendNozzle(document, element, inletNozzleId, usedIds, nozzlePositions);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds, nozzlePositions);
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
   * Registers nozzle positions for connection line geometry.
   *
   * <p>
   * Inlet nozzles are placed at the left edge of the equipment shape (x - 10), and outlet nozzles
   * at the right edge (x + 10). Multiple outlets are vertically offset to match the layout.
   * </p>
   *
   * @param position the equipment position (may be null)
   * @param inNozzle the inlet nozzle ID
   * @param outNozzles the outlet nozzle IDs
   * @param nozzlePositions map to populate with nozzle ID to {x, y} coordinates
   */
  private static void registerNozzlePositions(DexpiLayoutEngine.EquipmentPosition position,
      String inNozzle, List<String> outNozzles, Map<String, double[]> nozzlePositions) {
    if (position == null) {
      return;
    }
    // Inlet nozzle at left edge of equipment shape (center height)
    nozzlePositions.put(inNozzle, new double[] {position.x - 18.0, position.y});

    // Phase-aware nozzle placement per P&ID convention (ISO 10628):
    // Gas exits TOP of equipment (highest Y in Y-up coords)
    // Oil/liquid exits BOTTOM of equipment
    // Water exits below oil (heaviest phase at very bottom)
    if (outNozzles.size() == 1) {
      // Single outlet — same height as center (TwoPortEquipment)
      nozzlePositions.put(outNozzles.get(0), new double[] {position.x + 18.0, position.y});
    } else if (outNozzles.size() == 2) {
      // 2-phase separator: gas at top, liquid at bottom
      nozzlePositions.put(outNozzles.get(0), new double[] {position.x + 18.0, position.y + 8.0}); // gas
                                                                                                  // (top)
      nozzlePositions.put(outNozzles.get(1), new double[] {position.x + 18.0, position.y - 8.0}); // liquid
                                                                                                  // (bottom)
    } else if (outNozzles.size() >= 3) {
      // 3-phase separator: gas top, oil middle, water bottom
      nozzlePositions.put(outNozzles.get(0), new double[] {position.x + 18.0, position.y + 8.0}); // gas
                                                                                                  // (top)
      nozzlePositions.put(outNozzles.get(1), new double[] {position.x + 18.0, position.y - 4.0}); // oil
                                                                                                  // (middle-low)
      nozzlePositions.put(outNozzles.get(2), new double[] {position.x + 18.0, position.y - 12.0}); // water
                                                                                                   // (bottom)
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
    // For each plain Stream in the process, check if its fluid delegates to a
    // registered outlet
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
      List<StreamInterface> inStreams = DexpiLayoutEngine.resolveInletStreams(unit);
      for (StreamInterface inletStream : inStreams) {
        String fromNozzle = outletStreamToNozzle.get(System.identityHashCode(inletStream));
        String toNozzle = inletNozzles.get(unit.getName());
        if (fromNozzle != null && toNozzle != null) {
          connections.add(new NozzleConnection(fromNozzle, toNozzle));
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
   * @param nozzlePositions map of nozzle ID to {x, y} coordinates for line geometry
   */
  private static void appendConnectionSystem(Document document, Element parent,
      List<NozzleConnection> connections, Set<String> usedIds,
      Map<String, Element> valvePipingComponents, Map<String, double[]> nozzlePositions) {
    Element systemElement = document.createElement("PipingNetworkSystem");
    systemElement.setAttribute("ComponentClass", "PipingNetworkSystem");
    systemElement.setAttribute("ID",
        uniqueIdentifier("PipingNetworkSystem", "Connections", usedIds));

    Set<String> consumedValveNozzles = new HashSet<>();
    int streamCounter = 1;

    for (NozzleConnection conn : connections) {
      Element segmentElement = document.createElement("PipingNetworkSegment");
      segmentElement.setAttribute("ComponentClass", "PipingNetworkSegment");
      segmentElement.setAttribute("ID", uniqueIdentifier("PipingNetworkSegment", "Conn", usedIds));

      // Add connection line geometry if nozzle positions are available
      double[] fromPos = nozzlePositions.get(conn.fromNozzle);
      double[] toPos = nozzlePositions.get(conn.toNozzle);
      if (fromPos != null && toPos != null) {
        DexpiLayoutEngine.appendConnectionLine(document, segmentElement, fromPos[0], fromPos[1],
            toPos[0], toPos[1]);
        // Add flow direction arrow at midpoint of connection line
        DexpiLayoutEngine.appendFlowArrow(document, segmentElement, fromPos[0], fromPos[1],
            toPos[0], toPos[1]);
        // Add stream number label above line midpoint
        DexpiLayoutEngine.appendStreamLabel(document, segmentElement,
            String.valueOf(streamCounter++), fromPos[0], fromPos[1], toPos[0], toPos[1]);
      }

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

    // Add standalone segments for any valve PipingComponents not consumed by
    // connections
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
   * @param layoutPositions equipment layout positions keyed by equipment name
   * @param nozzlePositions nozzle positions keyed by nozzle ID
   * @param processSystem the process system for stream-to-equipment matching
   */
  private static void appendInstruments(Document document, Element parent,
      Map<String, MeasurementDeviceInterface> transmitters,
      Map<String, ControllerDeviceInterface> controllers, Set<String> usedIds,
      Map<String, DexpiLayoutEngine.EquipmentPosition> layoutPositions,
      Map<String, double[]> nozzlePositions, ProcessSystem processSystem) {

    // Group transmitters by parent equipment for correct positioning
    Map<String, List<String>> equipmentTransmitters = new LinkedHashMap<>();
    Map<String, String> tagToEquipment = new HashMap<>();
    for (Map.Entry<String, MeasurementDeviceInterface> entry : transmitters.entrySet()) {
      String parentName = findParentEquipment(entry.getValue(), processSystem);
      if (parentName != null) {
        tagToEquipment.put(entry.getKey(), parentName);
        List<String> list = equipmentTransmitters.get(parentName);
        if (list == null) {
          list = new ArrayList<>();
          equipmentTransmitters.put(parentName, list);
        }
        list.add(entry.getKey());
      }
    }

    int loopIndex = 1;
    int nodeCounter = 1;
    for (Map.Entry<String, MeasurementDeviceInterface> entry : transmitters.entrySet()) {
      String tag = entry.getKey();
      MeasurementDeviceInterface device = entry.getValue();

      String[] parsed = parseIsaTag(tag);
      String category = parsed[0];
      String functions = parsed[1];
      String loopNumber = parsed[2];

      // Compute instrument position above parent equipment
      String parentName = tagToEquipment.get(tag);
      DexpiLayoutEngine.EquipmentPosition eqPos =
          parentName != null ? layoutPositions.get(parentName) : null;
      double cx = 0;
      double cy = 0;
      boolean hasPosition = false;
      if (eqPos != null && equipmentTransmitters.containsKey(parentName)) {
        List<String> siblings = equipmentTransmitters.get(parentName);
        int idx = siblings.indexOf(tag);
        double[] pos = DexpiLayoutEngine.computeInstrumentPosition(eqPos, idx, siblings.size());
        cx = pos[0];
        cy = pos[1];
        hasPosition = true;
      }

      // ProcessInstrumentationFunction (the instrument bubble)
      String pifId = uniqueIdentifier("ProcessInstrumentationFunction", tag, usedIds);
      Element pif = document.createElement("ProcessInstrumentationFunction");
      pif.setAttribute("ID", pifId);
      pif.setAttribute("ComponentClass", "ProcessInstrumentationFunction");
      pif.setAttribute("ComponentClassURI",
          "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunction");
      pif.setAttribute("ComponentName", DexpiShapeCatalog.INSTRUMENT_BUBBLE_FIELD_SHAPE);

      // Position element
      if (hasPosition) {
        appendInstrumentPosition(document, pif, cx, cy);
      }

      // Label with function letters and loop number
      if (hasPosition) {
        String labelId = uniqueIdentifier("ProcessInstrumentationFunctionLabel", tag, usedIds);
        Element label = document.createElement("Label");
        label.setAttribute("ID", labelId);
        label.setAttribute("ComponentClass", "ProcessInstrumentationFunctionLabel");
        label.setAttribute("ComponentClassURI",
            "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunctionLabel");

        String topText = category + functions;
        appendInstrumentLabelText(document, label, topText, cx, cy + 2.25, pifId, new String[] {
            "ProcessInstrumentationFunctionCategory", "ProcessInstrumentationFunctions"});
        appendInstrumentLabelText(document, label, loopNumber, cx, cy - 1.2, pifId,
            new String[] {"ProcessInstrumentationFunctionNumber"});

        pif.appendChild(label);
      }

      // GenericAttributes
      Element pifAttrs = document.createElement("GenericAttributes");
      pifAttrs.setAttribute("Set", "DexpiAttributes");
      appendGenericAttribute(document, pifAttrs,
          "ProcessInstrumentationFunctionCategoryAssignmentClass", category);
      appendGenericAttribute(document, pifAttrs,
          "ProcessInstrumentationFunctionNumberAssignmentClass", loopNumber);
      appendGenericAttribute(document, pifAttrs, "ProcessInstrumentationFunctionsAssignmentClass",
          functions);
      appendGenericAttribute(document, pifAttrs, DexpiMetadata.TAG_NAME, tag);
      appendGenericAttribute(document, pifAttrs, "MeasurementUnit", device.getUnit());
      pif.appendChild(pifAttrs);

      // ConnectionPoints (5 signal nodes)
      if (hasPosition) {
        double r = DexpiLayoutEngine.INSTRUMENT_BUBBLE_RADIUS;
        double halfW = 6.75;
        Element connPoints = document.createElement("ConnectionPoints");
        connPoints.setAttribute("NumPoints", "5");

        Element defaultNode = document.createElement("Node");
        defaultNode.setAttribute("ID", pifId + "-DefaultNode");
        connPoints.appendChild(defaultNode);

        appendSignalNode(document, connPoints,
            uniqueIdentifier("InstrumentationNodePosition", String.valueOf(nodeCounter++), usedIds),
            cx, cy - r);
        appendSignalNode(document, connPoints,
            uniqueIdentifier("InstrumentationNodePosition", String.valueOf(nodeCounter++), usedIds),
            cx, cy + r);
        appendSignalNode(document, connPoints,
            uniqueIdentifier("InstrumentationNodePosition", String.valueOf(nodeCounter++), usedIds),
            cx - halfW, cy);
        appendSignalNode(document, connPoints,
            uniqueIdentifier("InstrumentationNodePosition", String.valueOf(nodeCounter++), usedIds),
            cx + halfW, cy);

        pif.appendChild(connPoints);
      }

      // MeasuringLineFunction as InformationFlow child
      String mlfId = uniqueIdentifier("MeasuringLineFunction", tag, usedIds);
      String psgfId = uniqueIdentifier("ProcessSignalGeneratingFunction", tag, usedIds);

      if (hasPosition) {
        Element assocEnd = document.createElement("Association");
        assocEnd.setAttribute("Type", "is logical end of");
        assocEnd.setAttribute("ItemID", mlfId);
        pif.appendChild(assocEnd);
      }

      // InformationFlow with CenterLine from process line to instrument bubble
      if (hasPosition && eqPos != null) {
        Element infoFlow = document.createElement("InformationFlow");
        infoFlow.setAttribute("ID", mlfId);
        infoFlow.setAttribute("ComponentClass", "MeasuringLineFunction");
        infoFlow.setAttribute("ComponentClassURI",
            "http://sandbox.dexpi.org/rdl/MeasuringLineFunction");

        Element assocStart = document.createElement("Association");
        assocStart.setAttribute("Type", "has logical start");
        assocStart.setAttribute("ItemID", psgfId);
        infoFlow.appendChild(assocStart);

        Element assocEndInner = document.createElement("Association");
        assocEndInner.setAttribute("Type", "has logical end");
        assocEndInner.setAttribute("ItemID", pifId);
        infoFlow.appendChild(assocEndInner);

        DexpiLayoutEngine.appendMeasuringLine(document, infoFlow, cx, eqPos.y, cx, cy);

        pif.appendChild(infoFlow);
      }

      // ProcessSignalGeneratingFunction (the sensor)
      Element psgf = document.createElement("ProcessSignalGeneratingFunction");
      psgf.setAttribute("ID", psgfId);
      psgf.setAttribute("ComponentClass", "ProcessSignalGeneratingFunction");
      psgf.setAttribute("ComponentClassURI",
          "http://sandbox.dexpi.org/rdl/ProcessSignalGeneratingFunction");

      Element psgfAttrs = document.createElement("GenericAttributes");
      psgfAttrs.setAttribute("Set", "DexpiAttributes");
      appendGenericAttribute(document, psgfAttrs,
          "ProcessSignalGeneratingFunctionNumberAssignmentClass", tag);
      psgf.appendChild(psgfAttrs);

      if (hasPosition) {
        Element startAssoc = document.createElement("Association");
        startAssoc.setAttribute("Type", "is logical start of");
        startAssoc.setAttribute("ItemID", mlfId);
        psgf.appendChild(startAssoc);
      }

      pif.appendChild(psgf);

      // System assignment (DCS by default; could be SIS for safety tags)
      String systemAssignment = "DCS";
      Element sysAttrs = document.createElement("GenericAttributes");
      sysAttrs.setAttribute("Set", "SystemAssignment");
      appendGenericAttribute(document, sysAttrs, "ControlSystem", systemAssignment);
      pif.appendChild(sysAttrs);

      // Look for matching controller (e.g. PT-xxx -> PC-xxx)
      String controllerTag = deriveControllerTag(tag);
      ControllerDeviceInterface matchedController = null;
      if (controllers != null && controllerTag != null) {
        matchedController = controllers.get(controllerTag);
      }

      String controllerPifId = null;
      if (matchedController != null) {
        // SignalConveyingFunction on the transmitter PIF
        String scfId = uniqueIdentifier("SignalConveyingFunction", tag, usedIds);
        Element scf = document.createElement("SignalConveyingFunction");
        scf.setAttribute("ID", scfId);
        scf.setAttribute("ComponentClass", "SignalConveyingFunction");
        pif.appendChild(scf);

        // ActuatingFunction on the transmitter PIF
        String afId = uniqueIdentifier("ActuatingFunction", controllerTag, usedIds);
        Element af = document.createElement("ActuatingFunction");
        af.setAttribute("ID", afId);
        af.setAttribute("ComponentClass", "ActuatingFunction");

        Element afAttrs = document.createElement("GenericAttributes");
        afAttrs.setAttribute("Set", "DexpiAttributes");
        appendGenericAttribute(document, afAttrs, "ActuatingFunctionNumberAssignmentClass",
            controllerTag);
        af.appendChild(afAttrs);
        pif.appendChild(af);

        // Controller bubble (separate ProcessInstrumentationFunction)
        String[] ctrlParsed = parseIsaTag(controllerTag);
        String ctrlCategory = ctrlParsed[0];
        String ctrlFunctions = ctrlParsed[1];

        controllerPifId =
            uniqueIdentifier("ProcessInstrumentationFunction", controllerTag, usedIds);
        Element ctrlPif = document.createElement("ProcessInstrumentationFunction");
        ctrlPif.setAttribute("ID", controllerPifId);
        ctrlPif.setAttribute("ComponentClass", "ProcessInstrumentationFunction");
        ctrlPif.setAttribute("ComponentClassURI",
            "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunction");
        ctrlPif.setAttribute("ComponentName", DexpiShapeCatalog.INSTRUMENT_BUBBLE_FIELD_SHAPE);

        // Position controller bubble to the right of the transmitter bubble
        double ctrlCx = cx + DexpiLayoutEngine.INSTRUMENT_X_SPACING;
        double ctrlCy = cy;
        if (hasPosition) {
          appendInstrumentPosition(document, ctrlPif, ctrlCx, ctrlCy);

          // Controller label
          String ctrlLabelId =
              uniqueIdentifier("ProcessInstrumentationFunctionLabel", controllerTag, usedIds);
          Element ctrlLabel = document.createElement("Label");
          ctrlLabel.setAttribute("ID", ctrlLabelId);
          ctrlLabel.setAttribute("ComponentClass", "ProcessInstrumentationFunctionLabel");
          ctrlLabel.setAttribute("ComponentClassURI",
              "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunctionLabel");

          String ctrlTopText = ctrlCategory + ctrlFunctions;
          appendInstrumentLabelText(document, ctrlLabel, ctrlTopText, ctrlCx, ctrlCy + 2.25,
              controllerPifId, new String[] {"ProcessInstrumentationFunctionCategory",
                  "ProcessInstrumentationFunctions"});
          appendInstrumentLabelText(document, ctrlLabel, loopNumber, ctrlCx, ctrlCy - 1.2,
              controllerPifId, new String[] {"ProcessInstrumentationFunctionNumber"});
          ctrlPif.appendChild(ctrlLabel);
        }

        // Controller GenericAttributes with PID parameters
        Element ctrlAttrSet = document.createElement("GenericAttributes");
        ctrlAttrSet.setAttribute("Set", "DexpiAttributes");
        appendGenericAttribute(document, ctrlAttrSet,
            "ProcessInstrumentationFunctionCategoryAssignmentClass", ctrlCategory);
        appendGenericAttribute(document, ctrlAttrSet,
            "ProcessInstrumentationFunctionNumberAssignmentClass", loopNumber);
        appendGenericAttribute(document, ctrlAttrSet,
            "ProcessInstrumentationFunctionsAssignmentClass", ctrlFunctions);
        appendGenericAttribute(document, ctrlAttrSet, DexpiMetadata.TAG_NAME, controllerTag);
        ctrlPif.appendChild(ctrlAttrSet);

        // PID tuning parameters
        Element pidAttrs = document.createElement("GenericAttributes");
        pidAttrs.setAttribute("Set", "PIDParameters");
        appendGenericAttribute(document, pidAttrs, "ControllerSetPoint",
            String.valueOf(matchedController.getControllerSetPoint()));
        if (matchedController instanceof ControllerDeviceBaseClass) {
          ControllerDeviceBaseClass pidCtrl = (ControllerDeviceBaseClass) matchedController;
          appendGenericAttribute(document, pidAttrs, "ProportionalGain",
              String.valueOf(pidCtrl.getKp()));
          appendGenericAttribute(document, pidAttrs, "IntegralTime",
              String.valueOf(pidCtrl.getTi()), "s");
          appendGenericAttribute(document, pidAttrs, "DerivativeTime",
              String.valueOf(pidCtrl.getTd()), "s");
        }
        if (matchedController.isReverseActing()) {
          appendGenericAttribute(document, pidAttrs, "ControlAction", "Reverse");
        } else {
          appendGenericAttribute(document, pidAttrs, "ControlAction", "Direct");
        }
        ctrlPif.appendChild(pidAttrs);

        // System assignment on controller
        Element ctrlSysAttrs = document.createElement("GenericAttributes");
        ctrlSysAttrs.setAttribute("Set", "SystemAssignment");
        appendGenericAttribute(document, ctrlSysAttrs, "ControlSystem", systemAssignment);
        ctrlPif.appendChild(ctrlSysAttrs);

        // Signal line between transmitter bubble and controller bubble (dashed blue)
        if (hasPosition) {
          String sigFlowId = uniqueIdentifier("SignalInformationFlow", controllerTag, usedIds);
          Element sigFlow = document.createElement("InformationFlow");
          sigFlow.setAttribute("ID", sigFlowId);
          sigFlow.setAttribute("ComponentClass", "SignalConveyingFunction");
          sigFlow.setAttribute("ComponentClassURI",
              "http://sandbox.dexpi.org/rdl/SignalConveyingFunction");

          DexpiLayoutEngine.appendSignalLine(document, sigFlow, cx, cy, ctrlCx, ctrlCy);
          ctrlPif.appendChild(sigFlow);

          // Signal line from controller bubble down to the process line (to the valve)
          String actuateFlowId =
              uniqueIdentifier("ActuatingInformationFlow", controllerTag, usedIds);
          Element actuateFlow = document.createElement("InformationFlow");
          actuateFlow.setAttribute("ID", actuateFlowId);
          actuateFlow.setAttribute("ComponentClass", "ActuatingSystemFunction");
          actuateFlow.setAttribute("ComponentClassURI",
              "http://sandbox.dexpi.org/rdl/ActuatingSystemFunction");

          DexpiLayoutEngine.appendSignalLine(document, actuateFlow, ctrlCx, ctrlCy, ctrlCx,
              eqPos != null ? eqPos.y : cy - DexpiLayoutEngine.INSTRUMENT_OFFSET_Y);
          ctrlPif.appendChild(actuateFlow);
        }

        parent.appendChild(ctrlPif);
      }

      parent.appendChild(pif);

      // InstrumentationLoopFunction (groups the loop)
      String loopId = uniqueIdentifier("InstrumentationLoopFunction", "Loop-" + loopIndex, usedIds);
      Element loop = document.createElement("InstrumentationLoopFunction");
      loop.setAttribute("ID", loopId);
      loop.setAttribute("ComponentClass", "InstrumentationLoopFunction");

      Element loopAttrs = document.createElement("GenericAttributes");
      loopAttrs.setAttribute("Set", "DexpiAttributes");
      appendGenericAttribute(document, loopAttrs, DexpiMetadata.LOOP_NUMBER, loopNumber);
      loop.appendChild(loopAttrs);

      Element loopAssoc = document.createElement("Association");
      loopAssoc.setAttribute("Type", "is a collection including");
      loopAssoc.setAttribute("ItemID", pifId);
      loop.appendChild(loopAssoc);

      // Include controller in the loop group
      if (controllerPifId != null) {
        Element ctrlLoopAssoc = document.createElement("Association");
        ctrlLoopAssoc.setAttribute("Type", "is a collection including");
        ctrlLoopAssoc.setAttribute("ItemID", controllerPifId);
        loop.appendChild(ctrlLoopAssoc);
      }

      parent.appendChild(loop);
      loopIndex++;
    }
  }

  /**
   * Finds the name of the equipment whose inlet or outlet stream matches the transmitter's stream.
   *
   * @param device the measurement device
   * @param processSystem the process system
   * @return the equipment name, or null if not found
   */
  private static String findParentEquipment(MeasurementDeviceInterface device,
      ProcessSystem processSystem) {
    if (!(device instanceof StreamMeasurementDeviceBaseClass) || processSystem == null) {
      return null;
    }
    StreamInterface measuredStream = ((StreamMeasurementDeviceBaseClass) device).getStream();
    if (measuredStream == null) {
      return null;
    }
    int streamHash = System.identityHashCode(measuredStream);
    int fluidHash =
        measuredStream.getFluid() != null ? System.identityHashCode(measuredStream.getFluid()) : -1;

    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Stream) {
        continue;
      }
      for (StreamInterface s : eq.getOutletStreams()) {
        if (System.identityHashCode(s) == streamHash) {
          return eq.getName();
        }
        if (fluidHash > 0 && s.getFluid() != null
            && System.identityHashCode(s.getFluid()) == fluidHash) {
          return eq.getName();
        }
      }
      for (StreamInterface s : eq.getInletStreams()) {
        if (System.identityHashCode(s) == streamHash) {
          return eq.getName();
        }
        if (fluidHash > 0 && s.getFluid() != null
            && System.identityHashCode(s.getFluid()) == fluidHash) {
          return eq.getName();
        }
      }
    }
    return null;
  }

  /**
   * Appends a Position element with Location, Axis, and Reference for an instrument bubble.
   *
   * @param document the XML document
   * @param parent the element to append to
   * @param x the X coordinate
   * @param y the Y coordinate
   */
  private static void appendInstrumentPosition(Document document, Element parent, double x,
      double y) {
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);

    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);

    Element reference = document.createElement("Reference");
    reference.setAttribute("X", "1");
    reference.setAttribute("Y", "0");
    reference.setAttribute("Z", "0");
    position.appendChild(reference);

    parent.appendChild(position);
  }

  /**
   * Appends a Text element inside an instrument label.
   *
   * @param document the XML document
   * @param label the Label element
   * @param text the display text
   * @param x X position of the text
   * @param y Y position of the text
   * @param pifId the parent ProcessInstrumentationFunction ID
   * @param dependantAttributes the DependantAttribute names for TextStringFormatSpecification
   */
  private static void appendInstrumentLabelText(Document document, Element label, String text,
      double x, double y, String pifId, String[] dependantAttributes) {
    Element textElem = document.createElement("Text");
    textElem.setAttribute("String", text);
    textElem.setAttribute("Font", "Calibri");
    textElem.setAttribute("Height", "3");
    textElem.setAttribute("Width", "0");
    textElem.setAttribute("Justification", "CenterCenter");

    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    textElem.appendChild(pres);

    appendInstrumentPosition(document, textElem, x, y);

    Element tsfs = document.createElement("TextStringFormatSpecification");
    for (String attr : dependantAttributes) {
      Element objRef = document.createElement("ObjectAttributesReference");
      objRef.setAttribute("ItemID", pifId);
      objRef.setAttribute("DependantAttribute", attr);
      objRef.setAttribute("DependantAttributeContents", "Value");
      tsfs.appendChild(objRef);
    }
    textElem.appendChild(tsfs);

    label.appendChild(textElem);
  }

  /**
   * Appends a signal Node with position inside a ConnectionPoints element.
   *
   * @param document the XML document
   * @param parent the ConnectionPoints element
   * @param nodeId the unique node ID
   * @param x the X coordinate
   * @param y the Y coordinate
   */
  private static void appendSignalNode(Document document, Element parent, String nodeId, double x,
      double y) {
    Element node = document.createElement("Node");
    node.setAttribute("Type", "signal");
    node.setAttribute("ID", nodeId);
    appendInstrumentPosition(document, node, x, y);
    parent.appendChild(node);
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

  /**
   * Collects stream data for the stream table from process system equipment.
   *
   * <p>
   * Iterates through all equipment in the process system, extracting temperature, pressure, flow
   * rate, and phase information from each outlet stream.
   * </p>
   *
   * @param processSystem the process system to extract stream data from
   * @return list of stream table entries for rendering
   */
  private static List<DexpiLayoutEngine.StreamTableEntry> collectStreamTableData(
      ProcessSystem processSystem) {
    List<DexpiLayoutEngine.StreamTableEntry> entries = new ArrayList<>();
    DecimalFormat df = DECIMAL_FORMAT.get();
    int streamNum = 1;

    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof Stream || unit instanceof DexpiStream) {
        continue;
      }

      // Collect primary (gas) outlet
      StreamInterface gasOut = DexpiStreamUtils.getGasOutletStream(unit);
      if (gasOut != null) {
        DexpiLayoutEngine.StreamTableEntry entry =
            buildStreamEntry(df, String.valueOf(streamNum++), gasOut);
        if (entry != null) {
          entries.add(entry);
        }
      }

      // Collect liquid outlet for multi-outlet equipment
      if (DexpiStreamUtils.isMultiOutlet(unit)) {
        StreamInterface liqOut = DexpiStreamUtils.getLiquidOutletStream(unit);
        if (liqOut != null) {
          DexpiLayoutEngine.StreamTableEntry entry =
              buildStreamEntry(df, String.valueOf(streamNum++), liqOut);
          if (entry != null) {
            entries.add(entry);
          }
        }
      }
    }
    return entries;
  }

  /**
   * Builds a single stream table entry from a stream's simulation results.
   *
   * @param df the decimal format for numeric values
   * @param streamLabel the stream label/number
   * @param stream the stream to extract data from
   * @return the stream table entry, or null if data is unavailable
   */
  private static DexpiLayoutEngine.StreamTableEntry buildStreamEntry(DecimalFormat df,
      String streamLabel, StreamInterface stream) {
    try {
      double tempC = stream.getTemperature("C");
      double pressBara = stream.getPressure("bara");
      double flowKgHr = stream.getFlowRate("kg/hr");
      int numPhases = stream.getFluid() != null ? stream.getFluid().getNumberOfPhases() : 1;
      String phase;
      if (numPhases <= 1) {
        boolean hasGas = stream.getFluid() != null && stream.getFluid().hasPhaseType("gas");
        phase = hasGas ? "Gas" : "Liquid";
      } else {
        phase = "Two-phase";
      }
      return new DexpiLayoutEngine.StreamTableEntry(streamLabel,
          String.format(Locale.ROOT, "%.1f", tempC), String.format(Locale.ROOT, "%.1f", pressBara),
          String.format(Locale.ROOT, "%.0f", flowKgHr), phase);
    } catch (RuntimeException ignored) {
      // Simulation may not have been run
      return null;
    }
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
