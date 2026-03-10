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
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;

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

    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit instanceof DexpiProcessUnit) {
        appendProcessUnit(document, root, (DexpiProcessUnit) unit, usedIds);
      } else if (unit instanceof DexpiStream) {
        DexpiStream stream = (DexpiStream) unit;
        String systemKey = stream.getLineNumber();
        if (isBlank(systemKey)) {
          systemKey = stream.getFluidCode();
        }
        if (isBlank(systemKey)) {
          systemKey = "Segment";
        }
        segmentsBySystem.computeIfAbsent(systemKey, key -> new ArrayList<>()).add(stream);
      }
    }

    for (Map.Entry<String, List<DexpiStream>> entry : segmentsBySystem.entrySet()) {
      appendPipingNetworkSystem(document, root, entry.getKey(), entry.getValue(), usedIds);
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
      DexpiProcessUnit processUnit, Set<String> usedIds) {
    EquipmentEnum mapped = processUnit.getMappedEquipment();
    boolean isPipingComponent = mapped == EquipmentEnum.ThrottlingValve;
    String elementName = isPipingComponent ? "PipingComponent" : "Equipment";
    Element element = document.createElement(elementName);

    String componentClass =
        firstNonBlank(processUnit.getDexpiClass(), defaultComponentClass(mapped, elementName));
    element.setAttribute("ComponentClass", componentClass);
    element.setAttribute("ID", uniqueIdentifier(elementName, processUnit.getName(), usedIds));

    Element genericAttributes = document.createElement("GenericAttributes");
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.TAG_NAME,
        processUnit.getName());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.LINE_NUMBER,
        processUnit.getLineNumber());
    appendGenericAttribute(document, genericAttributes, DexpiMetadata.FLUID_CODE,
        processUnit.getFluidCode());

    if (genericAttributes.hasChildNodes()) {
      element.appendChild(genericAttributes);
    }

    parent.appendChild(element);
  }

  private static void appendPipingNetworkSystem(Document document, Element parent, String key,
      List<DexpiStream> streams, Set<String> usedIds) {
    Element systemElement = document.createElement("PipingNetworkSystem");
    systemElement.setAttribute("ComponentClass", "PipingNetworkSystem");
    systemElement.setAttribute("ID", uniqueIdentifier("Line", key, usedIds));

    Element systemAttributes = document.createElement("GenericAttributes");
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
        // ActuatingFunction
        String afId = uniqueIdentifier("ActuatingFunction", controllerTag, usedIds);
        Element af = document.createElement("ActuatingFunction");
        af.setAttribute("ID", afId);
        af.setAttribute("ComponentClass", "ActuatingFunction");

        Element afAttrs = document.createElement("GenericAttributes");
        appendGenericAttribute(document, afAttrs, DexpiMetadata.ACTUATING_FUNCTION_NUMBER,
            controllerTag);
        if (afAttrs.hasChildNodes()) {
          af.appendChild(afAttrs);
        }

        // Signal conveying function (signal line from instrument to actuator)
        String scfId = uniqueIdentifier("SignalConveyingFunction", controllerTag, usedIds);
        Element scf = document.createElement("InformationFlow");
        scf.setAttribute("ID", scfId);
        scf.setAttribute("ComponentClass", "SignalConveyingFunction");

        Element scfAssocStart = document.createElement("Association");
        scfAssocStart.setAttribute("Type", "has logical start");
        scfAssocStart.setAttribute("ItemID", pifId);
        scf.appendChild(scfAssocStart);

        Element scfAssocEnd = document.createElement("Association");
        scfAssocEnd.setAttribute("Type", "has logical end");
        scfAssocEnd.setAttribute("ItemID", afId);
        scf.appendChild(scfAssocEnd);

        pif.appendChild(scf);
        pif.appendChild(af);
      }

      parent.appendChild(pif);

      // InstrumentationLoopFunction (groups everything in the loop)
      String loopId = uniqueIdentifier("InstrumentationLoopFunction", "Loop-" + loopIndex, usedIds);
      Element loop = document.createElement("InstrumentationLoopFunction");
      loop.setAttribute("ID", loopId);
      loop.setAttribute("ComponentClass", "InstrumentationLoopFunction");

      Element loopAttrs = document.createElement("GenericAttributes");
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
