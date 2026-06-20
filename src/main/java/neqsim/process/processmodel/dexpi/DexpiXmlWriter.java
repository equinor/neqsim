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
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.filter.Filter;
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
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Utility for exporting {@link ProcessSystem}s created from DEXPI data back into a lightweight DEXPI XML
 * representation.
 *
 * <p>
 * The writer groups all discovered {@link DexpiStream} segments by line number (or fluid code when a line is not
 * available) to generate simple {@code <PipingNetworkSystem>} elements with associated {@code <PipingNetworkSegment>}
 * children.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see <a href="https://dexpi.org/">DEXPI Standard</a>
 */
public final class DexpiXmlWriter {
  private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager
      .getLogger(DexpiXmlWriter.class);
  private static final Pattern NON_IDENTIFIER = Pattern.compile("[^A-Za-z0-9_-]");
  private static final transient ThreadLocal<DecimalFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> {
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
    DecimalFormat format = new DecimalFormat("0.############", symbols);
    format.setMaximumFractionDigits(12);
    format.setGroupingUsed(false);
    return format;
  });

  /**
   * Thread-local flag controlling whether the root {@code PlantModel} element declares the DEXPI default XML namespace.
   * When {@code true} the default {@code xmlns} attribute is omitted so the resulting document can be parsed directly
   * by tools (such as pyDEXPI) whose Proteus reader looks up element tags without a namespace prefix.
   */
  private static final transient ThreadLocal<Boolean> OMIT_DEFAULT_NAMESPACE = ThreadLocal
      .withInitial(() -> Boolean.FALSE);

  /**
   * Thread-local flag controlling whether standard ISA-5.1 instrumentation (pressure, temperature, level and flow loops
   * with matched controllers) is automatically synthesized for a {@link ProcessSystem} that defines no measurement
   * devices or controllers of its own. When {@code true} (the default) every separator, compressor, cooler/heater and
   * pump receives a realistic set of indicating/controlling loops so the exported P&amp;ID resembles a real engineering
   * diagram instead of a bare block flow sketch. Set to {@code false} to export only the instrumentation that is
   * explicitly present in the model.
   */
  private static final transient ThreadLocal<Boolean> AUTO_SYNTHESIZE_INSTRUMENTS = ThreadLocal
      .withInitial(() -> Boolean.TRUE);

  /**
   * Enables or disables automatic synthesis of standard ISA-5.1 instrumentation for process systems that contain no
   * explicit measurement devices or controllers.
   *
   * @param enabled {@code true} to synthesize standard control loops (default), {@code false} to export only explicitly
   * modelled instrumentation
   */
  public static void setAutoSynthesizeInstrumentation(boolean enabled) {
    AUTO_SYNTHESIZE_INSTRUMENTS.set(Boolean.valueOf(enabled));
  }

  private DexpiXmlWriter() {
  }

  /**
   * Performs a bi-directional DEXPI round-trip: reads an existing DEXPI XML P&amp;ID, creates a NeqSim process model
   * from it using the provided fluid template, runs the simulation, and writes the enriched results back to DEXPI XML.
   *
   * <p>
   * This enables a "digital twin" workflow where P&amp;ID designs are imported, simulated with rigorous thermodynamics,
   * and exported back with updated process data (temperatures, pressures, flow rates, compositions).
   * </p>
   *
   * @param inputFile the source DEXPI XML file to read
   * @param outputFile the destination file for the enriched DEXPI XML
   * @param templateStream the template stream providing fluid composition for simulation
   * @throws IOException if reading or writing fails
   * @throws DexpiXmlReaderException if the DEXPI XML cannot be parsed
   */
  public static void roundTrip(File inputFile, File outputFile, Stream templateStream)
      throws IOException, DexpiXmlReaderException {
    Objects.requireNonNull(inputFile, "inputFile");
    Objects.requireNonNull(outputFile, "outputFile");
    Objects.requireNonNull(templateStream, "templateStream");

    ProcessSystem process = DexpiXmlReader.read(inputFile, templateStream);
    process.run();
    write(process, outputFile);
  }

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
   * Writes the provided {@link ProcessSystem} to a DEXPI XML file using a namespace-free root element so the document
   * can be parsed directly by pyDEXPI (and other Proteus readers that resolve element tags without a namespace prefix).
   *
   * <p>
   * NeqSim normally declares the DEXPI default namespace {@code http://sandbox.dexpi.org/xml} on the root
   * {@code PlantModel}. pyDEXPI's Proteus parser performs unqualified tag lookups and therefore fails to find
   * {@code PlantInformation} when that namespace is present. This method emits the same content with the default
   * {@code xmlns} attribute omitted, removing the need to post-process the file with a manual namespace strip.
   * </p>
   *
   * @param processSystem process model to export
   * @param file output file
   * @throws IOException if writing fails
   */
  public static void writeForPyDexpi(ProcessSystem processSystem, File file) throws IOException {
    Objects.requireNonNull(processSystem, "processSystem");
    Objects.requireNonNull(file, "file");
    OMIT_DEFAULT_NAMESPACE.set(Boolean.TRUE);
    try {
      write(processSystem, file, null, null);
    } finally {
      OMIT_DEFAULT_NAMESPACE.remove();
    }
  }

  /**
   * Writes the provided {@link ProcessSystem} to a DEXPI XML stream using a namespace-free root element so the document
   * can be parsed directly by pyDEXPI.
   *
   * @param processSystem process model to export
   * @param outputStream destination stream
   * @throws IOException if writing fails
   * @see #writeForPyDexpi(ProcessSystem, File)
   */
  public static void writeForPyDexpi(ProcessSystem processSystem, OutputStream outputStream) throws IOException {
    Objects.requireNonNull(processSystem, "processSystem");
    Objects.requireNonNull(outputStream, "outputStream");
    OMIT_DEFAULT_NAMESPACE.set(Boolean.TRUE);
    try {
      write(processSystem, outputStream, null, null);
    } finally {
      OMIT_DEFAULT_NAMESPACE.remove();
    }
  }

  /**
   * Writes a multi-area {@link ProcessModel} to a single combined DEXPI XML file.
   *
   * <p>
   * All process areas are flattened into one transient {@link ProcessSystem} (sharing equipment by object identity so
   * cross-area streams are not duplicated) and exported through the standard single-system writer. Equipment whose name
   * collides with an item already collected from an earlier area is skipped with a logged warning, since a DEXPI
   * {@code PlantModel} requires unique tags.
   * </p>
   *
   * @param plant the process model to export
   * @param file output file
   * @throws IOException if writing fails
   */
  public static void write(ProcessModel plant, File file) throws IOException {
    Objects.requireNonNull(plant, "plant");
    Objects.requireNonNull(file, "file");
    Path parent = file.toPath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (OutputStream outputStream = new FileOutputStream(file)) {
      write(plant, outputStream);
    }
  }

  /**
   * Writes a multi-area {@link ProcessModel} to a single combined DEXPI XML stream.
   *
   * @param plant the process model to export
   * @param outputStream destination stream
   * @throws IOException if writing fails
   * @see #write(ProcessModel, File)
   */
  public static void write(ProcessModel plant, OutputStream outputStream) throws IOException {
    Objects.requireNonNull(plant, "plant");
    Objects.requireNonNull(outputStream, "outputStream");
    write(flattenModel(plant), outputStream, null, null);
  }

  /**
   * Writes each process area of a {@link ProcessModel} to its own DEXPI XML sheet in the given directory. Each file is
   * named after the (sanitised) area name with a {@code .xml} extension.
   *
   * <p>
   * Per-sheet export keeps the equipment count per drawing manageable and mirrors the multi-sheet convention used in
   * real P&amp;ID packages. Streams that originate in another area appear as boundary feeds on the receiving sheet and
   * are terminated with off-page connectors.
   * </p>
   *
   * @param plant the process model to export
   * @param outputDirectory the directory to write the per-area sheets into (created if absent)
   * @return the list of files written, one per area
   * @throws IOException if writing fails
   */
  public static List<File> writeSheets(ProcessModel plant, File outputDirectory) throws IOException {
    Objects.requireNonNull(plant, "plant");
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    Files.createDirectories(outputDirectory.toPath());
    List<File> written = new ArrayList<>();
    int index = 1;
    for (String areaName : plant.getProcessSystemNames()) {
      ProcessSystem area = plant.get(areaName);
      if (area == null) {
	continue;
      }
      String safe = sanitizeFileName(areaName, "Area-" + index);
      File sheet = new File(outputDirectory, safe + ".xml");
      write(area, sheet, null, null);
      written.add(sheet);
      index++;
    }
    return written;
  }

  /**
   * Flattens a {@link ProcessModel} into a single {@link ProcessSystem} for combined export.
   *
   * <p>
   * Equipment is added by object identity so a stream shared between two areas is registered once. Genuine name
   * collisions between distinct equipment from different areas are skipped (with a logged warning) because the DEXPI
   * export requires unique tags.
   * </p>
   *
   * @param plant the process model to flatten
   * @return a transient process system containing all areas' equipment
   */
  private static ProcessSystem flattenModel(ProcessModel plant) {
    ProcessSystem combined = new ProcessSystem();
    combined.setName("ProcessModel");
    Set<String> usedNames = new HashSet<>();
    Set<Integer> addedIdentities = new HashSet<>();
    for (ProcessSystem area : plant.getAllProcesses()) {
      if (area == null) {
	continue;
      }
      for (ProcessEquipmentInterface unit : area.getUnitOperations()) {
	if (unit == null) {
	  continue;
	}
	if (!addedIdentities.add(System.identityHashCode(unit))) {
	  continue;
	}
	String name = unit.getName();
	if (name != null && !usedNames.add(name)) {
	  logger.warn("Skipping duplicate equipment name '{}' from area '{}' during" + " ProcessModel DEXPI flatten",
	      name, area.getName());
	  continue;
	}
	try {
	  combined.add(unit);
	} catch (RuntimeException ex) {
	  logger.warn("Could not add equipment '{}' to combined DEXPI model: {}", name, ex.getMessage());
	}
      }
    }
    return combined;
  }

  /**
   * Produces a filesystem-safe file name from an area name.
   *
   * @param name the raw area name (may be null or blank)
   * @param fallback the fallback base name when {@code name} is blank
   * @return a sanitised base file name containing only identifier-safe characters
   */
  private static String sanitizeFileName(String name, String fallback) {
    if (isBlank(name)) {
      return fallback;
    }
    String safe = NON_IDENTIFIER.matcher(name.trim().replace(' ', '_')).replaceAll("_");
    return safe.isEmpty() ? fallback : safe;
  }

  /**
   * Writes the provided {@link ProcessSystem} to a DEXPI XML file, including instrument and controller data when
   * provided.
   *
   * @param processSystem process model to export
   * @param file output file
   * @param transmitters map of tag name to transmitter (may be null)
   * @param controllers map of tag name to controller (may be null)
   * @throws IOException if writing fails
   */
  public static void write(ProcessSystem processSystem, File file, Map<String, MeasurementDeviceInterface> transmitters,
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
  public static void write(ProcessSystem processSystem, OutputStream outputStream) throws IOException {
    write(processSystem, outputStream, null, null);
  }

  /**
   * Writes the provided {@link ProcessSystem} to a DEXPI XML stream, including instrument and controller data when
   * provided.
   *
   * @param processSystem process model to export
   * @param outputStream destination stream
   * @param transmitters map of tag name to transmitter (may be null)
   * @param controllers map of tag name to controller (may be null)
   * @throws IOException if writing fails
   */
  public static void write(ProcessSystem processSystem, OutputStream outputStream,
      Map<String, MeasurementDeviceInterface> transmitters, Map<String, ControllerDeviceInterface> controllers)
      throws IOException {
    Objects.requireNonNull(processSystem, "processSystem");
    Objects.requireNonNull(outputStream, "outputStream");

    Document document = createDocument();
    Element root = document.createElement("PlantModel");
    if (!Boolean.TRUE.equals(OMIT_DEFAULT_NAMESPACE.get())) {
      root.setAttribute("xmlns", "http://sandbox.dexpi.org/xml");
      root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
      root.setAttribute("xsi:schemaLocation",
	  "http://sandbox.dexpi.org/xml http://sandbox.dexpi.org/xml/dexpi-4.1.1.xsd");
    }
    document.appendChild(root);

    root.appendChild(createPlantInformation(document));

    // Compute auto-layout positions for graphical rendering
    Map<String, DexpiLayoutEngine.EquipmentPosition> layoutPositions = DexpiLayoutEngine.computeLayout(processSystem);
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
	registerNozzlePositions(layoutPositions.get(unit.getName()), inNozzle, outNozzles, nozzlePositions);
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
	registerNozzlePositions(layoutPositions.get(unit.getName()), inNozzle, outNozzles, nozzlePositions);
	if (isValveType(unit)) {
	  // Valves are PipingComponents in DEXPI — embed in PipingNetworkSegment, not
	  // top-level
	  Element valveElement = buildValvePipingComponent(document, unit, usedIds, inNozzle, outNozzles,
	      layoutPositions.get(unit.getName()), labelCounter++, nozzlePositions);
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
      appendConnectionSystem(document, root, connections, usedIds, valvePipingComponents, nozzlePositions);
    }

    // Off-page connectors (ISO 10628): mark battery-limit feeds and products that are not wired to
    // another unit on this sheet, so the reader knows the line continues off the diagram.
    appendBoundaryConnectors(document, root, connections, equipmentInletNozzle, outletStreamToNozzle, nozzlePositions);

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

    // When the model carries no instrumentation of its own, synthesize a standard set of ISA-5.1
    // control loops so the exported P&ID looks like a real engineering diagram (control valves,
    // transmitters and PID controllers) rather than a bare block flow sketch. Synthesis is limited
    // to the rich pyDEXPI/P&ID export path; the plain write(...) overloads stay backwards
    // compatible and emit only the instrumentation explicitly present in the model.
    if ((effectiveTransmitters == null || effectiveTransmitters.isEmpty())
	&& AUTO_SYNTHESIZE_INSTRUMENTS.get().booleanValue() && Boolean.TRUE.equals(OMIT_DEFAULT_NAMESPACE.get())) {
      effectiveTransmitters = new LinkedHashMap<>();
      if (effectiveControllers == null) {
	effectiveControllers = new LinkedHashMap<>();
      }
      synthesizeStandardInstrumentation(processSystem, effectiveTransmitters, effectiveControllers);
    }

    if (effectiveTransmitters != null && !effectiveTransmitters.isEmpty()) {
      appendInstruments(document, root, effectiveTransmitters, effectiveControllers, usedIds, layoutPositions,
	  nozzlePositions, processSystem);
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
    DexpiLayoutEngine.appendDrawing(document, root, drawingName, "PID-001", "0", today, sheetSize[0], sheetSize[1]);

    // Battery limit boundary (NORSOK Z-003)
    DexpiLayoutEngine.appendBatteryLimitBoundary(document, root, layoutPositions, drawingName);

    // Symbol legend (ISO 10628)
    List<String[]> legendEntries = new ArrayList<>();
    legendEntries.add(new String[] { "Process", "Process Line", "0" });
    legendEntries.add(new String[] { "Signal", "Signal Line (dashed)", "1" });
    legendEntries.add(new String[] { "Utility", "Utility Line (dash-dot)", "3" });
    DexpiLayoutEngine.appendSymbolLegend(document, root, legendEntries);

    // Revision history (NORSOK Z-003)
    List<String[]> revisions = new ArrayList<>();
    revisions.add(new String[] { "0", today, "Issued for Design", "NeqSim", "" });
    DexpiLayoutEngine.appendRevisionHistory(document, root, revisions, sheetSize[0]);

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
      factory.setNamespaceAware(true);
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
    String applicationVersion = ProcessSystem.class.getPackage().getImplementationVersion() == null ? "1.0"
	: ProcessSystem.class.getPackage().getImplementationVersion();
    plantInformation.setAttribute("Application", "NeqSim");
    plantInformation.setAttribute("ApplicationVersion", applicationVersion);
    plantInformation.setAttribute("OriginatingSystem", "NeqSim");
    plantInformation.setAttribute("OriginatingSystemVendor", "Equinor / NeqSim");
    plantInformation.setAttribute("OriginatingSystemVersion", applicationVersion);
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

  private static void appendProcessUnit(Document document, Element parent, DexpiProcessUnit processUnit,
      Set<String> usedIds, String inletNozzleId, List<String> outletNozzleIds,
      DexpiLayoutEngine.EquipmentPosition position, int labelIndex, Map<String, double[]> nozzlePositions) {
    EquipmentEnum mapped = processUnit.getMappedEquipment();
    String elementName = "Equipment";
    Element element = document.createElement(elementName);

    String componentClass = firstNonBlank(processUnit.getDexpiClass(), defaultComponentClass(mapped, elementName));
    element.setAttribute("ComponentClass", componentClass);
    String equipmentId = uniqueIdentifier(elementName, processUnit.getName(), usedIds);
    element.setAttribute("ID", equipmentId);

    // DEXPI RDL ComponentClassURI
    String classUri = mapComponentClassUri(componentClass);
    if (classUri != null) {
      element.setAttribute("ComponentClassURI", classUri);
    }

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
      DexpiLayoutEngine.appendTagNameLabel(document, element, processUnit.getName(), position, labelId, equipmentId);
    }

    // Add Nozzle children
    appendNozzle(document, element, inletNozzleId, usedIds, nozzlePositions);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds, nozzlePositions);
    }

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.TAG_NAME, processUnit.getName());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.LINE_NUMBER, processUnit.getLineNumber());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.FLUID_CODE, processUnit.getFluidCode());

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
  private static void appendNativeEquipment(Document document, Element parent, ProcessEquipmentInterface unit,
      Set<String> usedIds, String inletNozzleId, List<String> outletNozzleIds,
      DexpiLayoutEngine.EquipmentPosition position, int labelIndex, Map<String, double[]> nozzlePositions) {
    String componentClass = reverseMapComponentClass(unit);
    String elementName = "Equipment";
    Element element = document.createElement(elementName);

    element.setAttribute("ComponentClass", componentClass);
    String equipmentId = uniqueIdentifier(elementName, unit.getName(), usedIds);
    element.setAttribute("ID", equipmentId);

    // DEXPI RDL ComponentClassURI
    String classUri = mapComponentClassUri(componentClass);
    if (classUri != null) {
      element.setAttribute("ComponentClassURI", classUri);
    }

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
      DexpiLayoutEngine.appendTagNameLabel(document, element, unit.getName(), position, labelId, equipmentId);
      // Add equipment bar label with P/T/flow data
      appendEquipmentBarFromSimulation(document, element, unit, position, "EquipmentBarLabel-" + labelIndex,
	  equipmentId);
      // Equipment orientation marker (ISO 10628)
      String orientation = detectOrientation(unit);
      if (orientation != null) {
	DexpiLayoutEngine.appendOrientationMarker(document, element, orientation, position.x, position.y);
      }
    }

    // Add Nozzle children
    appendNozzle(document, element, inletNozzleId, usedIds, nozzlePositions);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds, nozzlePositions);
    }

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.TAG_NAME, unit.getName());

    // Export IEC 81346 reference designation if set
    appendIEC81346Attributes(document, genericAttributes, unit);

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
  private static void appendNozzle(Document document, Element parent, String nozzleId, Set<String> usedIds,
      Map<String, double[]> nozzlePositions) {
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
      appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_PRESSURE_VALUE, pressure,
	  DexpiMetadata.DEFAULT_PRESSURE_UNIT);

      double temperature = outStream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
      appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_TEMPERATURE_VALUE, temperature,
	  DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);

      double flowRate = outStream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT);
      appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_FLOW_VALUE, flowRate,
	  DexpiMetadata.DEFAULT_FLOW_UNIT);
    } catch (RuntimeException ignored) {
      // Simulation may not have been run — properties unavailable
    }
  }

  /**
   * Appends a GenericAttributes set with mechanical design parameters for the equipment. Only attributes with
   * non-zero/non-default values are exported.
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
      appendNumericAttribute(document, attrs, "MaxOperationPressure", md.getMaxOperationPressure(), "bara");
    }
    if (md.getMaxDesignPressure() > 0 && md.getMaxOperationPressure() > 0) {
      appendNumericAttribute(document, attrs, "MaxDesignPressure", md.getMaxDesignPressure(), "bara");
    }
    if (md.getMaxOperationTemperature() > 0) {
      // Design temperature is typically 1.1x max operating temperature
      double designTemp = md.getMaxOperationTemperature();
      appendNumericAttribute(document, attrs, "DesignTemperature", designTemp, "K");
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

    boolean hasSimData = !Double.isNaN(pressure) || !Double.isNaN(temperature) || !Double.isNaN(flowRate);
    if (!hasSimData && mechRows.isEmpty()) {
      return;
    }

    DexpiLayoutEngine.appendEquipmentBarLabel(document, element, unit.getName(), position, labelId, equipmentId,
	pressure, temperature, flowRate, mechRows.isEmpty() ? null : mechRows);
  }

  /**
   * Collects mechanical design parameters from equipment for display in the bar label. Returns rows only for parameters
   * that have been set (non-zero, non-default).
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

    // Mechanical-design lengths (inner diameter, wall thickness, tan-to-tan length) are stored
    // internally in metres. Convert to millimetres for the P&ID data bar.
    if (md.getInnerDiameter() > 0) {
      rows.add(new String[] { "ID", formatMechValue(md.getInnerDiameter() * 1000.0) + " mm" });
    }
    if (md.getWallThickness() > 0) {
      rows.add(new String[] { "Wall Thk.", formatMechValue(md.getWallThickness() * 1000.0) + " mm" });
    }
    if (md.getTantanLength() > 0) {
      rows.add(new String[] { "Length", formatMechValue(md.getTantanLength() * 1000.0) + " mm" });
    }
    String material = md.getConstrutionMaterial();
    if (material != null && !material.trim().isEmpty() && !"steel".equals(material)) {
      rows.add(new String[] { "Material", material });
    }
    boolean designPressureSet = md.getMaxDesignPressure() > 0 && md.getMaxOperationPressure() > 0;
    if (designPressureSet) {
      rows.add(new String[] { "Design P.", formatMechValue(md.getMaxDesignPressure()) + " bara" });
    }
    // Only emit a design temperature when it has been meaningfully set. The field defaults to the
    // placeholder value 100.0 K (-173.1 C); emitting that for every unmodified unit produced a
    // misleading constant on the P&ID, so it is suppressed unless a real design basis exists.
    boolean designTemperatureSet = Math.abs(md.getMaxOperationTemperature() - 100.0) > 0.5;
    if (designTemperatureSet) {
      double designTempC = md.getMaxOperationTemperature() - 273.15;
      rows.add(new String[] { "Design T.", formatMechValue(designTempC) + " \u00B0C" });
    }
    if (md.getWeightTotal() > 0) {
      rows.add(new String[] { "Weight", formatMechValue(md.getWeightTotal()) + " kg" });
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
      return String.valueOf(Math.round(value));
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
    if (unit instanceof Tank) {
      return "Tank";
    }
    if (unit instanceof Filter) {
      return "Filter";
    }
    if (unit instanceof ThrottlingValve) {
      return reverseMapValveClass(unit);
    }
    if (unit instanceof Expander) {
      return "Expander";
    }
    if (unit instanceof DistillationColumn) {
      return "DistillationColumn";
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
   * Returns {@code true} if the unit represents a DEXPI piping component (valve) rather than an Equipment element. In
   * the DEXPI schema, valves are PipingComponent elements and should be embedded inside PipingNetworkSegment elements
   * rather than exported as top-level Equipment.
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
  private static Element buildValvePipingComponent(Document document, ProcessEquipmentInterface unit,
      Set<String> usedIds, String inletNozzleId, List<String> outletNozzleIds,
      DexpiLayoutEngine.EquipmentPosition position, int labelIndex, Map<String, double[]> nozzlePositions) {
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
      DexpiLayoutEngine.appendTagNameLabel(document, element, unit.getName(), position, labelId, valveId);
    }

    appendNozzle(document, element, inletNozzleId, usedIds, nozzlePositions);
    for (String outNozzle : outletNozzleIds) {
      appendNozzle(document, element, outNozzle, usedIds, nozzlePositions);
    }

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.TAG_NAME, unit.getName());
    appendSimulationResults(document, genericAttributes, unit);

    // Valve fail position marker (NORSOK Z-003)
    String failPos = detectFailPosition(unit);
    if (failPos != null) {
      appendGenericAttribute(document, genericAttributes, DexpiMetadata.FAIL_POSITION, failPos);
      if (position != null) {
	DexpiLayoutEngine.appendFailPositionMarker(document, element, failPos, position.x, position.y);
      }
    }

    // Export valve Cv (flow coefficient)
    if (unit instanceof ThrottlingValve) {
      double cv = ((ThrottlingValve) unit).getCv();
      if (cv > 0 && !Double.isNaN(cv) && !Double.isInfinite(cv)) {
	appendNumericAttribute(document, genericAttributes, DexpiMetadata.VALVE_CV, cv, "");
      }
    }

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
    private final StreamInterface stream;

    NozzleConnection(String fromNozzle, String toNozzle) {
      this(fromNozzle, toNozzle, null);
    }

    NozzleConnection(String fromNozzle, String toNozzle, StreamInterface stream) {
      this.fromNozzle = fromNozzle;
      this.toNozzle = toNozzle;
      this.stream = stream;
    }
  }

  /**
   * Registers outlet stream identity hashes to their nozzle IDs.
   *
   * <p>
   * For single-outlet equipment, the first (and only) nozzle maps to the gas/primary outlet. For separators, the first
   * nozzle maps to the gas outlet, the second to the liquid outlet, and for three-phase separators, the third maps to
   * the water outlet.
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
   * Inlet nozzles are placed at the left edge of the equipment shape (x - 10), and outlet nozzles at the right edge (x
   * + 10). Multiple outlets are vertically offset to match the layout.
   * </p>
   *
   * @param position the equipment position (may be null)
   * @param inNozzle the inlet nozzle ID
   * @param outNozzles the outlet nozzle IDs
   * @param nozzlePositions map to populate with nozzle ID to {x, y} coordinates
   */
  private static void registerNozzlePositions(DexpiLayoutEngine.EquipmentPosition position, String inNozzle,
      List<String> outNozzles, Map<String, double[]> nozzlePositions) {
    if (position == null) {
      return;
    }
    // Inlet nozzle at left edge of equipment shape (center height)
    nozzlePositions.put(inNozzle, new double[] { position.x - 18.0, position.y });

    // Phase-aware nozzle placement per P&ID convention (ISO 10628):
    // Gas exits TOP of equipment (highest Y in Y-up coords)
    // Oil/liquid exits BOTTOM of equipment
    // Water exits below oil (heaviest phase at very bottom)
    if (outNozzles.size() == 1) {
      // Single outlet — same height as center (TwoPortEquipment)
      nozzlePositions.put(outNozzles.get(0), new double[] { position.x + 18.0, position.y });
    } else if (outNozzles.size() == 2) {
      // 2-phase separator: gas at top, liquid at bottom
      nozzlePositions.put(outNozzles.get(0), new double[] { position.x + 18.0, position.y + 8.0 }); // gas
												    // (top)
      nozzlePositions.put(outNozzles.get(1), new double[] { position.x + 18.0, position.y - 8.0 }); // liquid
												    // (bottom)
    } else if (outNozzles.size() >= 3) {
      // 3-phase separator: gas top, oil middle, water bottom
      nozzlePositions.put(outNozzles.get(0), new double[] { position.x + 18.0, position.y + 8.0 }); // gas
												    // (top)
      nozzlePositions.put(outNozzles.get(1), new double[] { position.x + 18.0, position.y - 4.0 }); // oil
												    // (middle-low)
      nozzlePositions.put(outNozzles.get(2), new double[] { position.x + 18.0, position.y - 12.0 }); // water
												     // (bottom)
    }
  }

  /**
   * Registers pass-through Streams in the outlet-stream-to-nozzle map.
   *
   * <p>
   * When a user creates {@code new Stream("gas-out", separator.getGasOutStream())}, the wrapping Stream delegates
   * {@code getFluid()} to the source. This method detects such wrappers by matching fluid identity and registers the
   * wrapper itself so downstream equipment that took the wrapper as its inlet can be connected.
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
	if (outStream != null && System.identityHashCode(outStream) == entry.getKey() && outStream.getFluid() != null) {
	  fluidToNozzle.put(System.identityHashCode(outStream.getFluid()), entry.getValue());
	}
	StreamInterface liqStream = DexpiStreamUtils.getLiquidOutletStream(unit);
	if (liqStream != null && System.identityHashCode(liqStream) == entry.getKey() && liqStream.getFluid() != null) {
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
   * Builds connections between equipment by matching inlet stream identity to outlet stream nozzles.
   *
   * <p>
   * For each equipment that extends {@link TwoPortEquipment}, the inlet stream is looked up in the
   * outlet-stream-to-nozzle map to find the upstream nozzle. This correctly resolves branching (e.g. separator gas and
   * liquid outlets going to different downstream equipment).
   * </p>
   *
   * @param processSystem the process system
   * @param outletStreamToNozzle map of outlet stream identity hash to nozzle ID
   * @param inletNozzles map of equipment name to inlet nozzle ID
   * @param connections list to populate with connections
   */
  private static void buildConnections(ProcessSystem processSystem, Map<Integer, String> outletStreamToNozzle,
      Map<String, String> inletNozzles, List<NozzleConnection> connections) {
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof Stream || unit instanceof DexpiStream) {
	continue;
      }
      List<StreamInterface> inStreams = DexpiLayoutEngine.resolveInletStreams(unit);
      for (StreamInterface inletStream : inStreams) {
	String fromNozzle = outletStreamToNozzle.get(System.identityHashCode(inletStream));
	String toNozzle = inletNozzles.get(unit.getName());
	if (fromNozzle != null && toNozzle != null) {
	  connections.add(new NozzleConnection(fromNozzle, toNozzle, inletStream));
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
   * @param valvePipingComponents pre-built PipingComponent elements for valves, keyed by inlet nozzle ID
   * @param nozzlePositions map of nozzle ID to {x, y} coordinates for line geometry
   */
  private static void appendConnectionSystem(Document document, Element parent, List<NozzleConnection> connections,
      Set<String> usedIds, Map<String, Element> valvePipingComponents, Map<String, double[]> nozzlePositions) {
    Element systemElement = document.createElement("PipingNetworkSystem");
    systemElement.setAttribute("ComponentClass", "PipingNetworkSystem");
    systemElement.setAttribute("ID", uniqueIdentifier("PipingNetworkSystem", "Connections", usedIds));

    Set<String> consumedValveNozzles = new HashSet<>();
    int streamCounter = 1;

    // Collected horizontal and vertical sub-segments for the line-crossing-hop post-pass.
    List<double[]> horizontalSegments = new ArrayList<>();
    List<double[]> verticalSegments = new ArrayList<>();

    for (NozzleConnection conn : connections) {
      Element segmentElement = document.createElement("PipingNetworkSegment");
      segmentElement.setAttribute("ComponentClass", "PipingNetworkSegment");
      segmentElement.setAttribute("ID", uniqueIdentifier("PipingNetworkSegment", "Conn", usedIds));

      // Add connection line geometry if nozzle positions are available
      double[] fromPos = nozzlePositions.get(conn.fromNozzle);
      double[] toPos = nozzlePositions.get(conn.toNozzle);
      DexpiServiceClassifier.ServiceType service = DexpiServiceClassifier.classify(conn.stream, 0.0);
      if (fromPos != null && toPos != null) {
	DexpiLayoutEngine.appendServiceConnectionLine(document, segmentElement, fromPos[0], fromPos[1], toPos[0],
	    toPos[1], service);
	// Add flow direction arrow at midpoint of connection line
	DexpiLayoutEngine.appendFlowArrow(document, segmentElement, fromPos[0], fromPos[1], toPos[0], toPos[1]);
	// Add stream number label above line midpoint
	DexpiLayoutEngine.appendStreamLabel(document, segmentElement, String.valueOf(streamCounter), fromPos[0],
	    fromPos[1], toPos[0], toPos[1]);
	// Add a NORSOK Z-003 line-identification label (size-fluidcode-sequence) below the line.
	String lineId = new NorsokLineNumber().fluidCode(service.getFluidCode())
	    .sequence(String.format(Locale.ROOT, "%03d", streamCounter)).build();
	DexpiLayoutEngine.appendLineIdLabel(document, segmentElement, lineId, fromPos[0], fromPos[1], toPos[0],
	    toPos[1]);
	// Record the routed sub-segments for the crossing-hop post-pass.
	collectRouteSegments(fromPos[0], fromPos[1], toPos[0], toPos[1], horizontalSegments, verticalSegments);
      }

      // Attach operating line data (service, P, T, flow) as DEXPI generic attributes.
      appendConnectionLineAttributes(document, segmentElement, conn.stream, service);
      streamCounter++;

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
	segmentElement.setAttribute("ID", uniqueIdentifier("PipingNetworkSegment", "Valve", usedIds));
	segmentElement.appendChild(entry.getValue());
	systemElement.appendChild(segmentElement);
      }
    }

    // Line-crossing post-pass (ISO 10628): draw a small "hop" arc wherever a horizontal pipe run
    // crosses the interior of a vertical riser belonging to a different connection. Connected
    // tee-junctions (shared endpoints) are not hopped because appendCrossingHop excludes endpoints.
    appendCrossingHops(document, systemElement, horizontalSegments, verticalSegments);

    parent.appendChild(systemElement);
  }

  /**
   * Splits an orthogonal connection route into its horizontal and vertical sub-segments and records them for the later
   * line-crossing-hop pass.
   *
   * @param fromX source X coordinate
   * @param fromY source Y coordinate
   * @param toX target X coordinate
   * @param toY target Y coordinate
   * @param horizontalSegments collector of {x1, x2, y} horizontal sub-segments
   * @param verticalSegments collector of {x, y1, y2} vertical sub-segments
   */
  private static void collectRouteSegments(double fromX, double fromY, double toX, double toY,
      List<double[]> horizontalSegments, List<double[]> verticalSegments) {
    double[][] points = DexpiLayoutEngine.routeConnection(fromX, fromY, toX, toY);
    for (int i = 0; i + 1 < points.length; i++) {
      double x1 = points[i][0];
      double y1 = points[i][1];
      double x2 = points[i + 1][0];
      double y2 = points[i + 1][1];
      if (Math.abs(y1 - y2) < 0.5) {
	horizontalSegments.add(new double[] { x1, x2, y1 });
      } else if (Math.abs(x1 - x2) < 0.5) {
	verticalSegments.add(new double[] { x1, y1, y2 });
      }
    }
  }

  /**
   * Appends line-crossing hop arcs for every horizontal sub-segment that crosses the interior of a vertical
   * sub-segment.
   *
   * @param document the XML document
   * @param parent the PipingNetworkSystem element to append the hop arcs to
   * @param horizontalSegments {x1, x2, y} horizontal sub-segments
   * @param verticalSegments {x, y1, y2} vertical sub-segments
   */
  private static void appendCrossingHops(Document document, Element parent, List<double[]> horizontalSegments,
      List<double[]> verticalSegments) {
    for (double[] h : horizontalSegments) {
      for (double[] v : verticalSegments) {
	DexpiLayoutEngine.appendCrossingHop(document, parent, h[0], h[1], h[2], v[0], v[1], v[2]);
      }
    }
  }

  /**
   * Appends off-page connector symbols for battery-limit feeds and products. A feed is a unit inlet nozzle that is
   * never the destination of an internal connection; a product is an outlet nozzle that is never the source of an
   * internal connection.
   *
   * @param document the XML document
   * @param parent the root PlantModel element
   * @param connections the internal nozzle-to-nozzle connections
   * @param equipmentInletNozzle map of unit name to its inlet nozzle ID
   * @param outletStreamToNozzle map of outlet stream identity hash to its nozzle ID
   * @param nozzlePositions map of nozzle ID to {x, y} coordinates
   */
  private static void appendBoundaryConnectors(Document document, Element parent, List<NozzleConnection> connections,
      Map<String, String> equipmentInletNozzle, Map<Integer, String> outletStreamToNozzle,
      Map<String, double[]> nozzlePositions) {
    Set<String> wiredToNozzles = new HashSet<>();
    Set<String> wiredFromNozzles = new HashSet<>();
    for (NozzleConnection conn : connections) {
      wiredToNozzles.add(conn.toNozzle);
      wiredFromNozzles.add(conn.fromNozzle);
    }

    // Feed connectors: an inlet nozzle that nothing on this sheet feeds.
    for (Map.Entry<String, String> entry : equipmentInletNozzle.entrySet()) {
      String inNozzle = entry.getValue();
      if (wiredToNozzles.contains(inNozzle)) {
	continue;
      }
      double[] pos = nozzlePositions.get(inNozzle);
      if (pos == null) {
	continue;
      }
      String reference = "FEED " + entry.getKey();
      DexpiLayoutEngine.appendOffPageConnector(document, parent, pos[0] - 18.0, pos[1], reference, true);
    }

    // Product connectors: an outlet nozzle that feeds nothing on this sheet.
    for (String outNozzle : outletStreamToNozzle.values()) {
      if (wiredFromNozzles.contains(outNozzle)) {
	continue;
      }
      double[] pos = nozzlePositions.get(outNozzle);
      if (pos == null) {
	continue;
      }
      DexpiLayoutEngine.appendOffPageConnector(document, parent, pos[0] + 18.0, pos[1], "PRODUCT", true);
    }
  }

  /**
   * Appends the operating line data (service category, fluid code, pressure, temperature and flow) for a connection as
   * DEXPI {@code GenericAttribute} elements so the pipe carries real process data rather than only geometry.
   *
   * @param document the XML document
   * @param segmentElement the PipingNetworkSegment element to attach attributes to
   * @param stream the process stream carried by the connection (may be null)
   * @param service the classified service category (may be null)
   */
  private static void appendConnectionLineAttributes(Document document, Element segmentElement, StreamInterface stream,
      DexpiServiceClassifier.ServiceType service) {
    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    if (service != null) {
      appendGenericAttribute(document, genericAttributes, DexpiMetadata.FLUID_CODE, service.getFluidCode());
      appendGenericAttribute(document, genericAttributes, "ServiceCategory", service.name());
    }
    if (stream != null) {
      try {
	appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_PRESSURE_VALUE,
	    stream.getPressure(DexpiMetadata.DEFAULT_PRESSURE_UNIT), DexpiMetadata.DEFAULT_PRESSURE_UNIT);
	appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_PRESSURE_UNIT,
	    DexpiMetadata.DEFAULT_PRESSURE_UNIT);
      } catch (RuntimeException ignored) {
	// pressure not available on this stream — skip
      }
      try {
	appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_TEMPERATURE_VALUE,
	    stream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT), DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
	appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_TEMPERATURE_UNIT,
	    DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
      } catch (RuntimeException ignored) {
	// temperature not available on this stream — skip
      }
      try {
	appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_FLOW_VALUE,
	    stream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT), DexpiMetadata.DEFAULT_FLOW_UNIT);
	appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_FLOW_UNIT,
	    DexpiMetadata.DEFAULT_FLOW_UNIT);
      } catch (RuntimeException ignored) {
	// flow not available on this stream — skip
      }
    }
    if (genericAttributes.hasChildNodes()) {
      segmentElement.appendChild(genericAttributes);
    }
  }

  private static void appendPipingNetworkSystem(Document document, Element parent, String key,
      List<DexpiStream> streams, Set<String> usedIds) {
    Element systemElement = document.createElement("PipingNetworkSystem");
    systemElement.setAttribute("ComponentClass", "PipingNetworkSystem");
    systemElement.setAttribute("ID", uniqueIdentifier("Line", key, usedIds));

    Element systemAttributes = document.createElement("GenericAttributes");
    systemAttributes.setAttribute("Set", "DexpiAttributes");
    String lineNumber = streams.stream().map(DexpiStream::getLineNumber).filter(value -> !isBlank(value)).findFirst()
	.orElse(null);
    appendGenericAttribute(document, systemAttributes, DexpiMetadata.LINE_NUMBER, lineNumber);
    String fluidCode = streams.stream().map(DexpiStream::getFluidCode).filter(value -> !isBlank(value)).findFirst()
	.orElse(null);
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

  private static void appendPipingNetworkSegment(Document document, Element parent, DexpiStream stream,
      Set<String> usedIds) {
    Element segmentElement = document.createElement("PipingNetworkSegment");
    String componentClass = firstNonBlank(stream.getDexpiClass(), "PipingNetworkSegment");
    segmentElement.setAttribute("ComponentClass", componentClass);
    segmentElement.setAttribute("ID", uniqueIdentifier("Segment", stream.getName(), usedIds));

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.SEGMENT_NUMBER, stream.getName());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.LINE_NUMBER, stream.getLineNumber());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.FLUID_CODE, stream.getFluidCode());
    appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_PRESSURE_VALUE,
	stream.getPressure(DexpiMetadata.DEFAULT_PRESSURE_UNIT), DexpiMetadata.DEFAULT_PRESSURE_UNIT);
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_PRESSURE_UNIT,
	DexpiMetadata.DEFAULT_PRESSURE_UNIT);
    appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_TEMPERATURE_VALUE,
	stream.getTemperature(DexpiMetadata.DEFAULT_TEMPERATURE_UNIT), DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_TEMPERATURE_UNIT,
	DexpiMetadata.DEFAULT_TEMPERATURE_UNIT);
    appendNumericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_FLOW_VALUE,
	stream.getFlowRate(DexpiMetadata.DEFAULT_FLOW_UNIT), DexpiMetadata.DEFAULT_FLOW_UNIT);
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.OPERATING_FLOW_UNIT,
	DexpiMetadata.DEFAULT_FLOW_UNIT);

    // Piping class and line size (exported when DexpiStream carries metadata)
    // These are currently placeholder attributes — populated when the stream source provides
    // piping class or line size data via generic attributes on the imported segment.
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.PIPING_CLASS_CODE, null);
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.LINE_SIZE, null);

    if (genericAttributes.hasChildNodes()) {
      segmentElement.appendChild(genericAttributes);
    }

    parent.appendChild(segmentElement);
  }

  private static void appendGenericAttribute(Document document, Element parent, String name, String value) {
    appendGenericAttribute(document, parent, name, value, null);
  }

  private static void appendGenericAttribute(Document document, Element parent, String name, String value,
      String unit) {
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

  private static void appendNumericAttribute(Document document, Element parent, String name, double value,
      String unit) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return;
    }
    appendGenericAttribute(document, parent, name, DECIMAL_FORMAT.get().format(value), unit);
  }

  /**
   * Appends DEXPI instrumentation elements for transmitters and controllers.
   *
   * <p>
   * Each transmitter becomes a {@code ProcessInstrumentationFunction} with a {@code ProcessSignalGeneratingFunction}
   * child. Controllers that share a loop tag with a transmitter are linked via {@code SignalConveyingFunction} and
   * {@code ActuatingFunction}. Finally, an {@code InstrumentationLoopFunction} groups each loop's elements.
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
      Map<String, MeasurementDeviceInterface> transmitters, Map<String, ControllerDeviceInterface> controllers,
      Set<String> usedIds, Map<String, DexpiLayoutEngine.EquipmentPosition> layoutPositions,
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
      DexpiLayoutEngine.EquipmentPosition eqPos = parentName != null ? layoutPositions.get(parentName) : null;
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
      pif.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunction");
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
	label.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunctionLabel");

	String topText = category + functions;
	appendInstrumentLabelText(document, label, topText, cx, cy + 2.25, pifId,
	    new String[] { "ProcessInstrumentationFunctionCategory", "ProcessInstrumentationFunctions" });
	appendInstrumentLabelText(document, label, loopNumber, cx, cy - 1.2, pifId,
	    new String[] { "ProcessInstrumentationFunctionNumber" });

	pif.appendChild(label);
      }

      // GenericAttributes
      Element pifAttrs = document.createElement("GenericAttributes");
      pifAttrs.setAttribute("Set", "DexpiAttributes");
      appendGenericAttribute(document, pifAttrs, "ProcessInstrumentationFunctionCategoryAssignmentClass", category);
      appendGenericAttribute(document, pifAttrs, "ProcessInstrumentationFunctionNumberAssignmentClass", loopNumber);
      appendGenericAttribute(document, pifAttrs, "ProcessInstrumentationFunctionsAssignmentClass", functions);
      appendGenericAttribute(document, pifAttrs, DexpiMetadata.TAG_NAME, tag);
      appendGenericAttribute(document, pifAttrs, "MeasurementUnit", device.getUnit());
      // ISA-5.1 tag conformance check: flag non-conforming instrument tags so the diagram and the
      // exported data record the deviation rather than silently emitting an invalid tag.
      IsaTagValidator.ValidationResult tagCheck = IsaTagValidator.validate(tag);
      if (!tagCheck.isValid()) {
	logger.warn("Instrument tag '{}' is not ISA-5.1 conformant: {}", tag, tagCheck.getMessage());
	appendGenericAttribute(document, pifAttrs, "TagConformanceWarning", tagCheck.getMessage());
      }
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
	    uniqueIdentifier("InstrumentationNodePosition", String.valueOf(nodeCounter++), usedIds), cx, cy - r);
	appendSignalNode(document, connPoints,
	    uniqueIdentifier("InstrumentationNodePosition", String.valueOf(nodeCounter++), usedIds), cx, cy + r);
	appendSignalNode(document, connPoints,
	    uniqueIdentifier("InstrumentationNodePosition", String.valueOf(nodeCounter++), usedIds), cx - halfW, cy);
	appendSignalNode(document, connPoints,
	    uniqueIdentifier("InstrumentationNodePosition", String.valueOf(nodeCounter++), usedIds), cx + halfW, cy);

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
	infoFlow.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/MeasuringLineFunction");

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
      psgf.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ProcessSignalGeneratingFunction");

      Element psgfAttrs = document.createElement("GenericAttributes");
      psgfAttrs.setAttribute("Set", "DexpiAttributes");
      appendGenericAttribute(document, psgfAttrs, "ProcessSignalGeneratingFunctionNumberAssignmentClass", tag);
      psgf.appendChild(psgfAttrs);

      if (hasPosition) {
	Element startAssoc = document.createElement("Association");
	startAssoc.setAttribute("Type", "is logical start of");
	startAssoc.setAttribute("ItemID", mlfId);
	psgf.appendChild(startAssoc);
      }

      pif.appendChild(psgf);

      // System assignment (DCS by default; SIS for safety tags)
      String systemAssignment = detectSafetySystem(tag) ? "SIS" : "DCS";
      Element sysAttrs = document.createElement("GenericAttributes");
      sysAttrs.setAttribute("Set", "SystemAssignment");
      appendGenericAttribute(document, sysAttrs, "ControlSystem", systemAssignment);
      pif.appendChild(sysAttrs);

      // SIL marking for safety-instrumented functions (NORSOK Z-003 / IEC 61511)
      if ("SIS".equals(systemAssignment) && hasPosition) {
	DexpiLayoutEngine.appendSilMarker(document, pif, 1, cx, cy);
      }

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
	appendGenericAttribute(document, afAttrs, "ActuatingFunctionNumberAssignmentClass", controllerTag);
	af.appendChild(afAttrs);
	pif.appendChild(af);

	// Controller bubble (separate ProcessInstrumentationFunction)
	String[] ctrlParsed = parseIsaTag(controllerTag);
	String ctrlCategory = ctrlParsed[0];
	String ctrlFunctions = ctrlParsed[1];

	controllerPifId = uniqueIdentifier("ProcessInstrumentationFunction", controllerTag, usedIds);
	Element ctrlPif = document.createElement("ProcessInstrumentationFunction");
	ctrlPif.setAttribute("ID", controllerPifId);
	ctrlPif.setAttribute("ComponentClass", "ProcessInstrumentationFunction");
	ctrlPif.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunction");
	ctrlPif.setAttribute("ComponentName", DexpiShapeCatalog.INSTRUMENT_BUBBLE_FIELD_SHAPE);

	// Position controller bubble to the right of the transmitter bubble
	double ctrlCx = cx + DexpiLayoutEngine.INSTRUMENT_X_SPACING;
	double ctrlCy = cy;
	if (hasPosition) {
	  appendInstrumentPosition(document, ctrlPif, ctrlCx, ctrlCy);

	  // Controller label
	  String ctrlLabelId = uniqueIdentifier("ProcessInstrumentationFunctionLabel", controllerTag, usedIds);
	  Element ctrlLabel = document.createElement("Label");
	  ctrlLabel.setAttribute("ID", ctrlLabelId);
	  ctrlLabel.setAttribute("ComponentClass", "ProcessInstrumentationFunctionLabel");
	  ctrlLabel.setAttribute("ComponentClassURI",
	      "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunctionLabel");

	  String ctrlTopText = ctrlCategory + ctrlFunctions;
	  appendInstrumentLabelText(document, ctrlLabel, ctrlTopText, ctrlCx, ctrlCy + 2.25, controllerPifId,
	      new String[] { "ProcessInstrumentationFunctionCategory", "ProcessInstrumentationFunctions" });
	  appendInstrumentLabelText(document, ctrlLabel, loopNumber, ctrlCx, ctrlCy - 1.2, controllerPifId,
	      new String[] { "ProcessInstrumentationFunctionNumber" });
	  ctrlPif.appendChild(ctrlLabel);
	}

	// Controller GenericAttributes with PID parameters
	Element ctrlAttrSet = document.createElement("GenericAttributes");
	ctrlAttrSet.setAttribute("Set", "DexpiAttributes");
	appendGenericAttribute(document, ctrlAttrSet, "ProcessInstrumentationFunctionCategoryAssignmentClass",
	    ctrlCategory);
	appendGenericAttribute(document, ctrlAttrSet, "ProcessInstrumentationFunctionNumberAssignmentClass",
	    loopNumber);
	appendGenericAttribute(document, ctrlAttrSet, "ProcessInstrumentationFunctionsAssignmentClass", ctrlFunctions);
	appendGenericAttribute(document, ctrlAttrSet, DexpiMetadata.TAG_NAME, controllerTag);
	ctrlPif.appendChild(ctrlAttrSet);

	// PID tuning parameters
	Element pidAttrs = document.createElement("GenericAttributes");
	pidAttrs.setAttribute("Set", "PIDParameters");
	appendGenericAttribute(document, pidAttrs, "ControllerSetPoint",
	    String.valueOf(matchedController.getControllerSetPoint()));
	if (matchedController instanceof ControllerDeviceBaseClass) {
	  ControllerDeviceBaseClass pidCtrl = (ControllerDeviceBaseClass) matchedController;
	  appendGenericAttribute(document, pidAttrs, "ProportionalGain", String.valueOf(pidCtrl.getKp()));
	  appendGenericAttribute(document, pidAttrs, "IntegralTime", String.valueOf(pidCtrl.getTi()), "s");
	  appendGenericAttribute(document, pidAttrs, "DerivativeTime", String.valueOf(pidCtrl.getTd()), "s");
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
	  sigFlow.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/SignalConveyingFunction");

	  // Measurement side (sensor -> controller): ISA-5.1 electric signal.
	  DexpiLayoutEngine.appendSignalLine(document, sigFlow, cx, cy, ctrlCx, ctrlCy,
	      DexpiLayoutEngine.SignalLineKind.ELECTRIC);
	  ctrlPif.appendChild(sigFlow);

	  // Signal line from controller bubble down to the process line (to the valve)
	  String actuateFlowId = uniqueIdentifier("ActuatingInformationFlow", controllerTag, usedIds);
	  Element actuateFlow = document.createElement("InformationFlow");
	  actuateFlow.setAttribute("ID", actuateFlowId);
	  actuateFlow.setAttribute("ComponentClass", "ActuatingSystemFunction");
	  actuateFlow.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ActuatingSystemFunction");

	  // Command side (controller -> final control element): ISA-5.1 pneumatic signal.
	  DexpiLayoutEngine.appendSignalLine(document, actuateFlow, ctrlCx, ctrlCy, ctrlCx,
	      eqPos != null ? eqPos.y : cy - DexpiLayoutEngine.INSTRUMENT_OFFSET_Y,
	      DexpiLayoutEngine.SignalLineKind.PNEUMATIC);
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
   * Synthesizes a standard set of ISA-5.1 instrumentation loops for a process system that defines no measurement
   * devices or controllers of its own. The generated loops give every common piece of equipment a realistic
   * indicating/controlling scheme so the exported P&amp;ID resembles a real engineering diagram:
   *
   * <table>
   * <caption>Synthesized control loops by equipment type</caption>
   * <tr>
   * <th>Equipment</th>
   * <th>Loops</th>
   * </tr>
   * <tr>
   * <td>Separator</td>
   * <td>PT/PIC on gas outlet, LT/LIC on liquid outlet, TT on gas outlet</td>
   * </tr>
   * <tr>
   * <td>Compressor</td>
   * <td>PT/PIC and TT on discharge, FT on suction</td>
   * </tr>
   * <tr>
   * <td>Cooler / Heater / HeatExchanger</td>
   * <td>TT/TIC on the process outlet</td>
   * </tr>
   * <tr>
   * <td>Pump</td>
   * <td>PT/PIC on discharge</td>
   * </tr>
   * </table>
   *
   * <p>
   * The transmitters are bound to live process streams (or, for level, to the separator) so the downstream positioning
   * logic can place each bubble above its parent equipment. Controller set-points are seeded from the current simulated
   * stream conditions.
   * </p>
   *
   * @param processSystem the process system to instrument
   * @param transmitters the (initially empty) transmitter map to populate, keyed by ISA tag
   * @param controllers the controller map to populate, keyed by derived ISA controller tag
   */
  private static void synthesizeStandardInstrumentation(ProcessSystem processSystem,
      Map<String, MeasurementDeviceInterface> transmitters, Map<String, ControllerDeviceInterface> controllers) {
    int eqIndex = 0;
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof Stream || unit instanceof Mixer || unit instanceof Splitter) {
	continue;
      }
      int base = 2000 + eqIndex * 10;
      eqIndex++;

      if (unit instanceof Separator) {
	Separator sep = (Separator) unit;
	StreamInterface gas = sep.getGasOutStream();
	if (gas != null) {
	  String pt = "PT-" + (base + 1);
	  transmitters.put(pt, new PressureTransmitter(pt, gas));
	  controllers.put("PC-" + (base + 1), makeSynthController("PC-" + (base + 1), safePressure(gas)));
	  String tt = "TT-" + (base + 3);
	  transmitters.put(tt, new TemperatureTransmitter(tt, gas));
	}
	String lt = "LT-" + (base + 2);
	transmitters.put(lt, new LevelTransmitter(lt, sep));
	controllers.put("LC-" + (base + 2), makeSynthController("LC-" + (base + 2), 0.5));
      } else if (unit instanceof Compressor) {
	StreamInterface out = firstOutlet(unit);
	if (out != null) {
	  String pt = "PT-" + (base + 1);
	  transmitters.put(pt, new PressureTransmitter(pt, out));
	  controllers.put("PC-" + (base + 1), makeSynthController("PC-" + (base + 1), safePressure(out)));
	  String tt = "TT-" + (base + 3);
	  transmitters.put(tt, new TemperatureTransmitter(tt, out));
	}
	StreamInterface in = firstInlet(unit);
	if (in != null) {
	  String ft = "FT-" + (base + 4);
	  transmitters.put(ft, new VolumeFlowTransmitter(ft, in));
	}
      } else if (unit instanceof Heater) {
	// Cooler and HeatExchanger both extend Heater, so a single instanceof Heater check
	// covers every cooling/heating service (a Cooler is not an instanceof HeatExchanger).
	StreamInterface out = firstOutlet(unit);
	if (out != null) {
	  String tt = "TT-" + (base + 3);
	  transmitters.put(tt, new TemperatureTransmitter(tt, out));
	  controllers.put("TC-" + (base + 3), makeSynthController("TC-" + (base + 3), safeTemperature(out)));
	}
      } else if (unit instanceof Pump) {
	StreamInterface out = firstOutlet(unit);
	if (out != null) {
	  String pt = "PT-" + (base + 1);
	  transmitters.put(pt, new PressureTransmitter(pt, out));
	  controllers.put("PC-" + (base + 1), makeSynthController("PC-" + (base + 1), safePressure(out)));
	}
      }
    }
  }

  /**
   * Builds a PID controller seeded with a realistic set-point for synthesized instrumentation.
   *
   * @param name the ISA controller tag
   * @param setPoint the controller set-point value
   * @return a configured controller device
   */
  private static ControllerDeviceInterface makeSynthController(String name, double setPoint) {
    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(name);
    controller.setControllerSetPoint(setPoint);
    controller.setControllerParameters(0.5, 200.0, 0.0);
    return controller;
  }

  /**
   * Returns the first outlet stream of an equipment unit, or {@code null} when it has none.
   *
   * @param unit the equipment unit
   * @return the first outlet stream, or {@code null}
   */
  private static StreamInterface firstOutlet(ProcessEquipmentInterface unit) {
    List<StreamInterface> outlets = unit.getOutletStreams();
    return outlets != null && !outlets.isEmpty() ? outlets.get(0) : null;
  }

  /**
   * Returns the first inlet stream of an equipment unit, or {@code null} when it has none.
   *
   * @param unit the equipment unit
   * @return the first inlet stream, or {@code null}
   */
  private static StreamInterface firstInlet(ProcessEquipmentInterface unit) {
    List<StreamInterface> inlets = unit.getInletStreams();
    return inlets != null && !inlets.isEmpty() ? inlets.get(0) : null;
  }

  /**
   * Reads the pressure of a stream in bara, returning {@code 0.0} if the value cannot be obtained.
   *
   * @param stream the stream to read
   * @return the pressure in bara, or {@code 0.0} on error
   */
  private static double safePressure(StreamInterface stream) {
    try {
      return stream.getPressure("bara");
    } catch (RuntimeException e) {
      return 0.0;
    }
  }

  /**
   * Reads the temperature of a stream in degrees Celsius, returning {@code 0.0} if the value cannot be obtained.
   *
   * @param stream the stream to read
   * @return the temperature in degrees Celsius, or {@code 0.0} on error
   */
  private static double safeTemperature(StreamInterface stream) {
    try {
      return stream.getTemperature("C");
    } catch (RuntimeException e) {
      return 0.0;
    }
  }

  /**
   * Finds the name of the equipment whose inlet or outlet stream matches the transmitter's stream.
   *
   * @param device the measurement device
   * @param processSystem the process system
   * @return the equipment name, or null if not found
   */
  private static String findParentEquipment(MeasurementDeviceInterface device, ProcessSystem processSystem) {
    if (processSystem == null) {
      return null;
    }
    if (device instanceof LevelTransmitter) {
      Separator sep = ((LevelTransmitter) device).getSeparator();
      return sep != null ? sep.getName() : null;
    }
    if (!(device instanceof StreamMeasurementDeviceBaseClass)) {
      return null;
    }
    StreamInterface measuredStream = ((StreamMeasurementDeviceBaseClass) device).getStream();
    if (measuredStream == null) {
      return null;
    }
    int streamHash = System.identityHashCode(measuredStream);
    int fluidHash = measuredStream.getFluid() != null ? System.identityHashCode(measuredStream.getFluid()) : -1;

    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Stream) {
	continue;
      }
      for (StreamInterface s : eq.getOutletStreams()) {
	if (System.identityHashCode(s) == streamHash) {
	  return eq.getName();
	}
	if (fluidHash > 0 && s.getFluid() != null && System.identityHashCode(s.getFluid()) == fluidHash) {
	  return eq.getName();
	}
      }
      for (StreamInterface s : eq.getInletStreams()) {
	if (System.identityHashCode(s) == streamHash) {
	  return eq.getName();
	}
	if (fluidHash > 0 && s.getFluid() != null && System.identityHashCode(s.getFluid()) == fluidHash) {
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
  private static void appendInstrumentPosition(Document document, Element parent, double x, double y) {
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
  private static void appendInstrumentLabelText(Document document, Element label, String text, double x, double y,
      String pifId, String[] dependantAttributes) {
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
  private static void appendSignalNode(Document document, Element parent, String nodeId, double x, double y) {
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
      return new String[] { "", "", tag };
    }
    int dashIndex = tag.indexOf('-');
    if (dashIndex <= 0) {
      return new String[] { "", "", tag };
    }
    String prefix = tag.substring(0, dashIndex);
    String loopNumber = tag.substring(dashIndex + 1);

    // First letter is the category (P, T, L, F, etc.)
    String category = prefix.substring(0, 1);
    // Remaining letters are the function (T, IC, C, etc.)
    String functions = prefix.length() > 1 ? prefix.substring(1) : "T";

    return new String[] { category, functions, loopNumber };
  }

  /**
   * Derives the controller tag from a transmitter tag. For example, "PT-HP sep" becomes "PC-HP sep", "LT-HP sep"
   * becomes "LC-HP sep".
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
   * Iterates through all equipment in the process system, extracting temperature, pressure, flow rate, and phase
   * information from each outlet stream.
   * </p>
   *
   * @param processSystem the process system to extract stream data from
   * @return list of stream table entries for rendering
   */
  private static List<DexpiLayoutEngine.StreamTableEntry> collectStreamTableData(ProcessSystem processSystem) {
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
	DexpiLayoutEngine.StreamTableEntry entry = buildStreamEntry(df, String.valueOf(streamNum++), gasOut);
	if (entry != null) {
	  entries.add(entry);
	}
      }

      // Collect liquid outlet for multi-outlet equipment
      if (DexpiStreamUtils.isMultiOutlet(unit)) {
	StreamInterface liqOut = DexpiStreamUtils.getLiquidOutletStream(unit);
	if (liqOut != null) {
	  DexpiLayoutEngine.StreamTableEntry entry = buildStreamEntry(df, String.valueOf(streamNum++), liqOut);
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
  private static DexpiLayoutEngine.StreamTableEntry buildStreamEntry(DecimalFormat df, String streamLabel,
      StreamInterface stream) {
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
      return new DexpiLayoutEngine.StreamTableEntry(streamLabel, String.format(Locale.ROOT, "%.1f", tempC),
	  String.format(Locale.ROOT, "%.1f", pressBara), String.format(Locale.ROOT, "%.0f", flowKgHr), phase);
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

  /**
   * Appends IEC 81346 reference designation attributes to the generic attributes element.
   *
   * <p>
   * If the equipment has an IEC 81346 reference designation set (via
   * {@link neqsim.process.equipment.iec81346.ReferenceDesignationGenerator}), the following attributes are added:
   * </p>
   * <ul>
   * <li>{@code IEC81346ReferenceDesignation}: The full reference designation string</li>
   * <li>{@code IEC81346FunctionDesignation}: The function aspect</li>
   * <li>{@code IEC81346ProductDesignation}: The product aspect</li>
   * <li>{@code IEC81346LocationDesignation}: The location aspect</li>
   * <li>{@code IEC81346LetterCode}: The equipment letter code (e.g. "B", "K", "Q")</li>
   * </ul>
   *
   * @param document the XML document
   * @param genericAttributes the parent element for generic attributes
   * @param unit the process equipment
   */
  private static void appendIEC81346Attributes(Document document, Element genericAttributes,
      ProcessEquipmentInterface unit) {
    neqsim.process.equipment.iec81346.ReferenceDesignation refDes = unit.getReferenceDesignation();
    if (refDes == null || !refDes.isSet()) {
      return;
    }
    appendGenericAttribute(document, genericAttributes, "IEC81346ReferenceDesignation",
	refDes.toReferenceDesignationString());
    appendGenericAttribute(document, genericAttributes, "IEC81346FunctionDesignation",
	refDes.getFormattedFunctionDesignation());
    appendGenericAttribute(document, genericAttributes, "IEC81346ProductDesignation",
	refDes.getFormattedProductDesignation());
    appendGenericAttribute(document, genericAttributes, "IEC81346LocationDesignation",
	refDes.getFormattedLocationDesignation());
    appendGenericAttribute(document, genericAttributes, "IEC81346LetterCode", refDes.getLetterCode().name());
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

  /**
   * Detects equipment orientation from the type. Per ISO 10628, separators are typically marked vertical (V) while
   * rotating and heat-transfer equipment are horizontal (H).
   *
   * @param unit the process equipment
   * @return "V" for vertical, "H" for horizontal, or null if not applicable
   */
  private static String detectOrientation(ProcessEquipmentInterface unit) {
    if (unit instanceof ThreePhaseSeparator || unit instanceof Separator) {
      return "V";
    }
    if (unit instanceof Compressor || unit instanceof Pump || unit instanceof HeatExchanger || unit instanceof Cooler
	|| unit instanceof Heater) {
      return "H";
    }
    return null;
  }

  /**
   * Detects valve fail position from the tag name convention per NORSOK Z-003.
   *
   * <p>
   * Tags starting with "XV", "ESD", or "HIPPS" are assumed fail-closed (FC). Tags starting with "HV" are assumed
   * fail-open (FO). Other valves return null (no automatic fail position).
   * </p>
   *
   * @param unit the valve equipment
   * @return fail position code (FC, FO), or null
   */
  private static String detectFailPosition(ProcessEquipmentInterface unit) {
    String name = unit.getName();
    if (name == null) {
      return null;
    }
    String upper = name.toUpperCase(Locale.ROOT);
    if (upper.startsWith("XV") || upper.contains("ESD") || upper.contains("HIPPS") || upper.contains("SDV")) {
      return "FC";
    }
    if (upper.startsWith("HV")) {
      return "FO";
    }
    return null;
  }

  /**
   * Determines the DEXPI valve ComponentClass from the tag name. Different valve prefixes indicate different valve
   * types per ISA/NORSOK conventions.
   *
   * @param unit the valve equipment
   * @return the DEXPI ComponentClass (GlobeValve, GateValve, BallValve, CheckValve, ButterflyValve)
   */
  private static String reverseMapValveClass(ProcessEquipmentInterface unit) {
    String name = unit.getName();
    if (name != null) {
      String upper = name.toUpperCase(Locale.ROOT);
      if (upper.startsWith("XV") || upper.contains("ESD") || upper.contains("SDV")) {
	return "GateValve";
      }
      if (upper.startsWith("BV")) {
	return "BallValve";
      }
      if (upper.startsWith("NRV") || upper.contains("CHECK")) {
	return "CheckValve";
      }
      if (upper.startsWith("BFV") || upper.contains("BUTTERFLY")) {
	return "ButterflyValve";
      }
    }
    return "GlobeValve";
  }

  /**
   * Detects whether an instrument tag indicates a Safety Instrumented System (SIS) device rather than a DCS device.
   * Safety tags typically have key letters like XV (shutdown), SD (shutdown), ZS (limit switch on safety valve), or SV
   * (safety valve).
   *
   * @param tag the ISA instrument tag
   * @return true if the tag indicates a safety-instrumented function
   */
  private static boolean detectSafetySystem(String tag) {
    if (tag == null || tag.isEmpty()) {
      return false;
    }
    int dashIndex = tag.indexOf('-');
    String prefix = dashIndex > 0 ? tag.substring(0, dashIndex) : tag;
    String upper = prefix.toUpperCase(Locale.ROOT);
    return upper.startsWith("XV") || upper.startsWith("SD") || upper.startsWith("ZS") || upper.startsWith("SV")
	|| upper.contains("ESD") || upper.contains("HIPPS");
  }

  /**
   * Maps a DEXPI ComponentClass name to its RDL URI.
   *
   * @param componentClass the DEXPI ComponentClass
   * @return the RDL URI, or null if no mapping exists
   */
  private static String mapComponentClassUri(String componentClass) {
    if (componentClass == null) {
      return null;
    }
    switch (componentClass) {
    case "Separator":
      return "http://data.posccaesar.org/rdl/RDS327962";
    case "ThreePhaseSeparator":
      return "http://data.posccaesar.org/rdl/RDS327962";
    case "CentrifugalCompressor":
      return "http://data.posccaesar.org/rdl/RDS414622";
    case "CentrifugalPump":
      return "http://data.posccaesar.org/rdl/RDS415550";
    case "AirCoolingSystem":
      return "http://data.posccaesar.org/rdl/RDS327938";
    case "ShellAndTubeHeatExchanger":
      return "http://data.posccaesar.org/rdl/RDS327918";
    case "FiredHeater":
      return "http://data.posccaesar.org/rdl/RDS327914";
    case "Tank":
      return "http://data.posccaesar.org/rdl/RDS328462";
    case "Filter":
      return "http://data.posccaesar.org/rdl/RDS327894";
    case "GlobeValve":
      return "http://data.posccaesar.org/rdl/RDS415212";
    case "GateValve":
      return "http://data.posccaesar.org/rdl/RDS415208";
    case "BallValve":
      return "http://data.posccaesar.org/rdl/RDS415196";
    case "CheckValve":
      return "http://data.posccaesar.org/rdl/RDS415204";
    case "ButterflyValve":
      return "http://data.posccaesar.org/rdl/RDS415200";
    case "Expander":
      return "http://data.posccaesar.org/rdl/RDS414776";
    case "Mixer":
      return "http://data.posccaesar.org/rdl/RDS4149564";
    case "Splitter":
      return "http://data.posccaesar.org/rdl/RDS4112354";
    case "DistillationColumn":
      return "http://data.posccaesar.org/rdl/RDS327902";
    case "ProcessColumn":
      return "http://data.posccaesar.org/rdl/RDS327902";
    case "ReciprocatingPump":
      return "http://data.posccaesar.org/rdl/RDS415554";
    case "PositiveDisplacementPump":
      return "http://data.posccaesar.org/rdl/RDS415554";
    case "ReciprocatingCompressor":
      return "http://data.posccaesar.org/rdl/RDS414626";
    case "PlateHeatExchanger":
      return "http://data.posccaesar.org/rdl/RDS327922";
    case "HeatExchanger":
      return "http://data.posccaesar.org/rdl/RDS327918";
    case "Cooler":
      return "http://data.posccaesar.org/rdl/RDS327938";
    case "Heater":
      return "http://data.posccaesar.org/rdl/RDS327914";
    case "Vessel":
      return "http://data.posccaesar.org/rdl/RDS328462";
    case "PressureVessel":
      return "http://data.posccaesar.org/rdl/RDS328462";
    case "ControlValve":
      return "http://data.posccaesar.org/rdl/RDS415212";
    case "PipingComponent":
      return "http://data.posccaesar.org/rdl/RDS415018";
    case "PipingNetworkSegment":
      return "http://data.posccaesar.org/rdl/RDS14313438";
    default:
      return null;
    }
  }

  private static void writeDocument(Document document, OutputStream outputStream) throws IOException {
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
