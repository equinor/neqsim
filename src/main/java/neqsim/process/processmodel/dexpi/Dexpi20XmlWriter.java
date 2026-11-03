package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.AirCooler;
import neqsim.process.equipment.heatexchanger.FiredHeater;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.CheckValve;
import neqsim.process.equipment.valve.RuptureDisk;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.processmodel.ProcessSystem;

/** Writes the native object/property/reference serialization introduced by DEXPI 2.0. */
public final class Dexpi20XmlWriter {
  private static final String CORE_MODEL = "https://data.dexpi.org/models/2.0.0/Core.xml";
  private static final String PLANT_MODEL = "https://data.dexpi.org/models/2.0.0/Plant.xml";

  private Dexpi20XmlWriter() {
  }

  /** Writes and validates a native DEXPI 2.0 model using the compatibility path. */
  public static void write(ProcessSystem processSystem, File file) throws IOException {
    writeValidated(processSystem, file, null);
  }

  /**
   * Writes and validates a native DEXPI 2.0 Plant model with controlled export metadata.
   *
   * @param processSystem source simulation topology
   * @param file destination DEXPI XML file
   * @param metadata caller-supplied export provenance and plant identity
   * @throws IOException if serialization or validation fails
   */
  public static void write(ProcessSystem processSystem, File file, Dexpi20PlantExportMetadata metadata)
      throws IOException {
    if (metadata == null) {
      throw new IllegalArgumentException("metadata must not be null");
    }
    writeValidated(processSystem, file, Dexpi20PlantExportOptions.builder(metadata).build());
  }

  /**
   * Writes and validates a native DEXPI 2.0 Plant model using explicit export options.
   *
   * @param processSystem source simulation topology
   * @param file destination DEXPI XML file
   * @param options controlled metadata and opt-in boundary handling
   * @throws IOException if serialization or validation fails
   */
  public static void write(ProcessSystem processSystem, File file, Dexpi20PlantExportOptions options)
      throws IOException {
    if (options == null) {
      throw new IllegalArgumentException("options must not be null");
    }
    writeValidated(processSystem, file, options);
  }

  private static void writeValidated(ProcessSystem processSystem, File file, Dexpi20PlantExportOptions options)
      throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    FileOutputStream stream = new FileOutputStream(file);
    try {
      writeDocument(processSystem, stream, options);
    } finally {
      stream.close();
    }
    try {
      Dexpi20XmlValidator.validate(file.toPath(), options != null && options.isSpecification201());
      Dexpi20SemanticValidator.validateOrThrow(file.toPath());
    } catch (org.xml.sax.SAXException ex) {
      throw new IOException("Generated DEXPI 2.0 XML failed schema validation", ex);
    }
  }

  /** Writes a Plant exchange and returns its auditable conformance assessment. */
  public static Dexpi20ConformanceAssessment.Report writeAndAssess(ProcessSystem processSystem, File file)
      throws IOException {
    write(processSystem, file);
    return Dexpi20ConformanceAssessment.assess(file.toPath(), Dexpi20ConformanceAssessment.Profile.PLANT_P_ID);
  }

  /**
   * Writes a metadata-bearing Plant exchange and returns its auditable conformance assessment.
   *
   * @param processSystem source simulation topology
   * @param file destination DEXPI XML file
   * @param metadata caller-supplied export provenance and plant identity
   * @return schema and supported-profile assessment
   * @throws IOException if serialization, validation, or assessment fails
   */
  public static Dexpi20ConformanceAssessment.Report writeAndAssess(ProcessSystem processSystem, File file,
      Dexpi20PlantExportMetadata metadata) throws IOException {
    write(processSystem, file, metadata);
    return Dexpi20ConformanceAssessment.assess(file.toPath(), Dexpi20ConformanceAssessment.Profile.PLANT_P_ID);
  }

  /**
   * Writes an option-controlled Plant exchange and returns its auditable conformance assessment.
   *
   * @param processSystem source simulation topology
   * @param file destination DEXPI XML file
   * @param options controlled metadata and opt-in boundary handling
   * @return schema and supported-profile assessment
   * @throws IOException if serialization, validation, or assessment fails
   */
  public static Dexpi20ConformanceAssessment.Report writeAndAssess(ProcessSystem processSystem, File file,
      Dexpi20PlantExportOptions options) throws IOException {
    write(processSystem, file, options);
    return Dexpi20ConformanceAssessment.assess(file.toPath(), Dexpi20ConformanceAssessment.Profile.PLANT_P_ID,
        options.isSpecification201());
  }

  /** Writes a native DEXPI 2.0 model to a stream using the compatibility path. */
  public static void write(ProcessSystem processSystem, OutputStream outputStream) throws IOException {
    writeDocument(processSystem, outputStream, null);
  }

  /**
   * Writes a native DEXPI 2.0 Plant model with controlled export metadata to a stream.
   *
   * @param processSystem source simulation topology
   * @param outputStream destination stream
   * @param metadata caller-supplied export provenance and plant identity
   * @throws IOException if serialization fails
   */
  public static void write(ProcessSystem processSystem, OutputStream outputStream, Dexpi20PlantExportMetadata metadata)
      throws IOException {
    if (metadata == null) {
      throw new IllegalArgumentException("metadata must not be null");
    }
    writeDocument(processSystem, outputStream, Dexpi20PlantExportOptions.builder(metadata).build());
  }

  /**
   * Writes a native DEXPI 2.0 Plant model using explicit export options to a stream.
   *
   * @param processSystem source simulation topology
   * @param outputStream destination stream
   * @param options controlled metadata and opt-in boundary handling
   * @throws IOException if serialization fails
   */
  public static void write(ProcessSystem processSystem, OutputStream outputStream, Dexpi20PlantExportOptions options)
      throws IOException {
    if (options == null) {
      throw new IllegalArgumentException("options must not be null");
    }
    writeDocument(processSystem, outputStream, options);
  }

  private static void writeDocument(ProcessSystem processSystem, OutputStream outputStream,
      Dexpi20PlantExportOptions options) throws IOException {
    if (processSystem == null || outputStream == null) {
      throw new IllegalArgumentException("processSystem and outputStream must not be null");
    }
    Dexpi20PlantExportMetadata metadata = options == null ? null : options.getMetadata();
    boolean specification201 = options != null && options.isSpecification201();
    boolean explicitBoundaryConnectors = options != null && options
        .getBoundaryConnectionMode() == Dexpi20PlantExportOptions.BoundaryConnectionMode.EXPLICIT_OFF_PAGE_CONNECTORS;
    try {
      Document document = createDocument();
      Element model = document.createElement("Model");
      model.setAttribute("name", identifier(processSystem.getName(), "NeqSimProcess"));
      model.setAttribute("uri", "urn:neqsim:dexpi:" + (specification201 ? "2.0.1:" : "2.0:")
          + identifier(processSystem.getName(), "process"));
      document.appendChild(model);
      appendImport(document, model, "Core", specification201 ? Dexpi201XmlWriter.CORE_MODEL : CORE_MODEL);
      appendImport(document, model, "Plant", specification201 ? Dexpi201XmlWriter.PLANT_MODEL : PLANT_MODEL);

      Element engineeringModel = object(document, null, "Core/EngineeringModel");
      model.appendChild(engineeringModel);
      if (metadata != null) {
        appendEngineeringModelMetadata(document, engineeringModel, metadata);
      }
      Element conceptualModel = components(document, engineeringModel, "ConceptualModel");
      Element plant = object(document, "PlantModel1", "Plant/PlantModel");
      conceptualModel.appendChild(plant);
      if (metadata != null) {
        appendPlantMetadata(document, plant, metadata);
      }

      Element taggedItems = components(document, plant, "TaggedPlantItems");
      Element pipingSystems = components(document, plant, "PipingNetworkSystems");
      Element pipingSystem = object(document, "PipingNetworkSystem1", "Plant/Piping.PipingNetworkSystem");
      data(document, pipingSystem, "LineNumber", "NeqSimProcessConnections");
      Element segments = components(document, pipingSystem, "Segments");
      pipingSystems.appendChild(pipingSystem);

      Map<StreamInterface, StreamInterface> aliases = specification201 ? numericalAliases(processSystem)
          : Collections.<StreamInterface, StreamInterface>emptyMap();
      Map<String, List<String>> distinctInletNodes = new LinkedHashMap<String, List<String>>();
      Map<StreamInterface, String> outletNodes = new IdentityHashMap<StreamInterface, String>();
      Map<String, String> inletNodes = new LinkedHashMap<String, String>();
      Map<String, String> nodeOwners = new LinkedHashMap<String, String>();
      Map<String, String> boundaryOutletNodes = new LinkedHashMap<String, String>();
      int equipmentNumber = 1;
      int nodeNumber = 1;
      for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
        if (unit == null || unit instanceof Stream || specification201 && isNumericalHelper(unit)) {
          continue;
        }
        String equipmentId = "Equipment" + equipmentNumber++;
        String equipmentType = specification201 ? dexpi201Type(unit) : dexpiType(unit);
        if (specification201 && "Plant/ProcessEquipment.ProcessEquipment".equals(equipmentType)) {
          throw new IOException(
              "No reviewed DEXPI 2.0.1 Plant mapping for " + unit.getClass().getName() + " (" + unit.getName() + ")");
        }
        Element equipment = object(document, equipmentId, equipmentType);
        boolean pipingComponent = specification201 && (unit instanceof ThrottlingValve || unit instanceof Splitter);
        data(document, equipment,
            pipingComponent
                ? ("Plant/Piping.SafetyValveOrFitting".equals(equipmentType)
                    || "Plant/Piping.RuptureDisc".equals(equipmentType) ? "PositionNumber" : "PipingComponentNumber")
                : "TagName",
            unit.getName());
        Element nozzles = components(document, equipment, pipingComponent ? "Nodes" : "Nozzles");

        List<StreamInterface> actualInlets = unit.getInletStreams();
        int inletCount = specification201 && actualInlets != null ? Math.max(1, actualInlets.size()) : 1;
        List<String> unitInletNodes = new ArrayList<String>();
        for (int inletIndex = 0; inletIndex < inletCount; inletIndex++) {
          String inletNode = "PipingNode" + nodeNumber++;
          String inletNozzleId = pipingComponent ? equipmentId
              : equipmentId + "Inlet" + (inletIndex == 0 ? "" : Integer.toString(inletIndex + 1));
          appendNozzle(document, nozzles, inletNozzleId, inletNode,
              inletCount == 1 ? "INLET" : "INLET_" + (inletIndex + 1));
          if (inletIndex == 0) {
            inletNodes.put(unit.getName(), inletNode);
          }
          unitInletNodes.add(inletNode);
          nodeOwners.put(inletNode, inletNozzleId);
        }
        distinctInletNodes.put(unit.getName(), unitInletNodes);

        List<StreamInterface> outlets = unit.getOutletStreams();
        if (outlets == null || outlets.isEmpty()) {
          String outletNode = "PipingNode" + nodeNumber++;
          String outletNozzleId = pipingComponent ? equipmentId : equipmentId + "Outlet";
          appendNozzle(document, nozzles, outletNozzleId, outletNode, "OUTLET");
          nodeOwners.put(outletNode, outletNozzleId);
          boundaryOutletNodes.put(outletNode, unit.getName() + " product");
        } else {
          for (int i = 0; i < outlets.size(); i++) {
            String outletNode = "PipingNode" + nodeNumber++;
            String outletNozzleId = pipingComponent ? equipmentId : equipmentId + "Outlet" + (i + 1);
            appendNozzle(document, nozzles, outletNozzleId, outletNode, "OUTLET_" + (i + 1));
            nodeOwners.put(outletNode, outletNozzleId);
            if (outlets.get(i) != null) {
              outletNodes.put(outlets.get(i), outletNode);
              boundaryOutletNodes.put(outletNode,
                  boundaryLabel(outlets.get(i), unit.getName() + " product " + (i + 1)));
            } else {
              boundaryOutletNodes.put(outletNode, unit.getName() + " product " + (i + 1));
            }
          }
        }
        if (pipingComponent) {
          Element valveSegment = object(document, "ValveSegment_" + equipmentId, "Plant/Piping.PipingNetworkSegment");
          components(document, valveSegment, "Items").appendChild(equipment);
          segments.appendChild(valveSegment);
        } else {
          taggedItems.appendChild(equipment);
        }
      }

      int segmentNumber = 1;
      Set<String> connectedOutletNodes = new LinkedHashSet<String>();
      for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
        if (unit == null || unit instanceof Stream || specification201 && isNumericalHelper(unit)) {
          continue;
        }
        String targetNode = inletNodes.get(unit.getName());
        List<StreamInterface> inlets = unit.getInletStreams();
        if ((inlets == null || inlets.isEmpty()) && explicitBoundaryConnectors && targetNode != null) {
          appendBoundarySegment(document, segments, segmentNumber++, true, targetNode, nodeOwners.get(targetNode),
              unit.getName() + " feed");
        } else if (inlets != null) {
          for (int inletIndex = 0; inletIndex < inlets.size(); inletIndex++) {
            StreamInterface inlet = inlets.get(inletIndex);
            if (specification201) {
              targetNode = distinctInletNodes.get(unit.getName()).get(inletIndex);
            }
            String sourceNode = outletNodes.get(specification201 ? resolveAlias(inlet, aliases) : inlet);
            if (sourceNode == null || targetNode == null) {
              if (explicitBoundaryConnectors && targetNode != null) {
                appendBoundarySegment(document, segments, segmentNumber++, true, targetNode, nodeOwners.get(targetNode),
                    boundaryLabel(inlet, unit.getName() + " feed"));
              }
              continue;
            }
            appendSegment(document, segments, segmentNumber++, sourceNode, targetNode, nodeOwners.get(sourceNode),
                nodeOwners.get(targetNode));
            connectedOutletNodes.add(sourceNode);
          }
        }
      }
      if (explicitBoundaryConnectors) {
        for (Map.Entry<String, String> boundary : boundaryOutletNodes.entrySet()) {
          if (!connectedOutletNodes.contains(boundary.getKey())) {
            appendBoundarySegment(document, segments, segmentNumber++, false, boundary.getKey(),
                nodeOwners.get(boundary.getKey()), boundary.getValue());
          }
        }
      }

      if (equipmentNumber == 1 || specification201 && !taggedItems.hasChildNodes()) {
        plant.removeChild(taggedItems);
      }
      if (segmentNumber == 1 && (!specification201 || !segments.hasChildNodes())) {
        plant.removeChild(pipingSystems);
      }

      transform(document, outputStream);
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not create DEXPI 2.0 XML document", ex);
    } catch (TransformerException ex) {
      throw new IOException("Could not serialize DEXPI 2.0 XML document", ex);
    }
  }

  private static Document createDocument() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().newDocument();
  }

  private static void appendImport(Document document, Element model, String prefix, String source) {
    Element item = document.createElement("Import");
    item.setAttribute("prefix", prefix);
    item.setAttribute("source", source);
    model.appendChild(item);
  }

  private static Element object(Document document, String id, String type) {
    Element object = document.createElement("Object");
    if (id != null) {
      object.setAttribute("id", identifier(id, "Object"));
    }
    object.setAttribute("type", type);
    return object;
  }

  private static Element components(Document document, Element parent, String property) {
    Element components = document.createElement("Components");
    components.setAttribute("property", property);
    parent.appendChild(components);
    return components;
  }

  private static void data(Document document, Element parent, String property, String value) {
    Element data = document.createElement("Data");
    data.setAttribute("property", property);
    Element string = document.createElement("String");
    string.setTextContent(value == null ? "" : value);
    data.appendChild(string);
    parent.appendChild(data);
  }

  static void appendEngineeringModelMetadata(Document document, Element engineeringModel,
      Dexpi20PlantExportMetadata metadata) {
    data(document, engineeringModel, "OriginatingSystemName", metadata.getOriginatingSystemName());
    data(document, engineeringModel, "OriginatingSystemVendorName", metadata.getOriginatingSystemVendorName());
    data(document, engineeringModel, "OriginatingSystemVersion", metadata.getOriginatingSystemVersion());
    dateTimeData(document, engineeringModel, "ExportDateTime", metadata.getExportDateTime());
  }

  private static void appendPlantMetadata(Document document, Element plant, Dexpi20PlantExportMetadata metadata) {
    Element metadataObjects = components(document, plant, "MetaData");
    Element plantMetadata = object(document, "PlantMetaData1", "Plant/Diagram.PlantMetaData");
    for (Map.Entry<Dexpi20PlantExportMetadata.PlantProperty, String> entry : metadata.getPlantProperties().entrySet()) {
      data(document, plantMetadata, entry.getKey().getDexpiProperty(), entry.getValue());
    }
    metadataObjects.appendChild(plantMetadata);
  }

  private static void dateTimeData(Document document, Element parent, String property, String value) {
    Element data = document.createElement("Data");
    data.setAttribute("property", property);
    Element dateTime = document.createElement("DateTime");
    dateTime.setTextContent(value);
    data.appendChild(dateTime);
    parent.appendChild(data);
  }

  private static void appendNozzle(Document document, Element nozzles, String nozzleId, String nodeId, String subTag) {
    if ("Nodes".equals(nozzles.getAttribute("property"))) {
      nozzles.appendChild(object(document, nodeId, "Plant/Piping.PipingNode"));
      return;
    }
    Element nozzle = object(document, nozzleId,
        Dexpi201XmlWriter.isSpecification201(document) ? "Plant/ProcessEquipment.ProcessNozzle"
            : "Plant/ProcessEquipment.Nozzle");
    Element nodes = components(document, nozzle, "Nodes");
    nodes.appendChild(object(document, nodeId, "Plant/Piping.PipingNode"));
    data(document, nozzle, "SubTagName", subTag);
    nozzles.appendChild(nozzle);
  }

  private static void appendBoundarySegment(Document document, Element segments, int number, boolean inbound,
      String equipmentNode, String equipmentItem, String label) {
    Element segment = object(document, "PipingNetworkSegment" + number, "Plant/Piping.PipingNetworkSegment");
    Element items = components(document, segment, "Items");
    String connectorId = "BoundaryConnector" + number;
    String boundaryNode = "BoundaryPipingNode" + number;
    String connectorType = inbound ? "Plant/Piping.FlowInPipeOffPageConnector"
        : "Plant/Piping.FlowOutPipeOffPageConnector";
    Element connector = object(document, connectorId, connectorType);
    Element nodes = components(document, connector, "Nodes");
    nodes.appendChild(object(document, boundaryNode, "Plant/Piping.PipingNode"));
    data(document, connector, "PipeConnectorNumber", label);
    items.appendChild(connector);

    String sourceNode = inbound ? boundaryNode : equipmentNode;
    String targetNode = inbound ? equipmentNode : boundaryNode;
    String sourceItem = inbound ? connectorId : equipmentItem;
    String targetItem = inbound ? equipmentItem : connectorId;
    Element connections = components(document, segment, "Connections");
    Element pipe = object(document, "Pipe" + number, "Plant/Piping.Pipe");
    references(document, pipe, "SourceItem", sourceItem);
    references(document, pipe, "SourceNode", sourceNode);
    references(document, pipe, "TargetItem", targetItem);
    references(document, pipe, "TargetNode", targetNode);
    connections.appendChild(pipe);
    references(document, segment, "SourceItem", sourceItem);
    references(document, segment, "SourceNode", sourceNode);
    references(document, segment, "TargetItem", targetItem);
    references(document, segment, "TargetNode", targetNode);
    data(document, segment, "SegmentNumber", Integer.toString(number));
    segments.appendChild(segment);
  }

  private static void appendSegment(Document document, Element segments, int number, String sourceNode,
      String targetNode, String sourceItem, String targetItem) {
    Element segment = object(document, "PipingNetworkSegment" + number, "Plant/Piping.PipingNetworkSegment");
    Element connections = components(document, segment, "Connections");
    Element pipe = object(document, "Pipe" + number, "Plant/Piping.Pipe");
    if (Dexpi201XmlWriter.isSpecification201(document)) {
      references(document, pipe, "SourceItem", sourceItem);
      references(document, pipe, "TargetItem", targetItem);
      references(document, segment, "SourceItem", sourceItem);
      references(document, segment, "TargetItem", targetItem);
    }
    references(document, pipe, "SourceNode", sourceNode);
    references(document, pipe, "TargetNode", targetNode);
    connections.appendChild(pipe);
    references(document, segment, "SourceNode", sourceNode);
    references(document, segment, "TargetNode", targetNode);
    data(document, segment, "SegmentNumber", Integer.toString(number));
    segments.appendChild(segment);
  }

  private static void references(Document document, Element parent, String property, String id) {
    Element reference = document.createElement("References");
    reference.setAttribute("property", property);
    reference.setAttribute("objects", "#" + identifier(id, "Object"));
    parent.appendChild(reference);
  }

  private static String boundaryLabel(StreamInterface stream, String fallback) {
    try {
      return stream == null || stream.getName() == null || stream.getName().trim().isEmpty() ? fallback
          : stream.getName();
    } catch (RuntimeException ex) {
      return fallback;
    }
  }

  /**
   * Recognizes convergence/control utilities that have no physical equipment symbol.
   *
   * @param unit source simulation object
   * @return whether the unit is numerical rather than physical
   */
  static boolean isNumericalHelper(ProcessEquipmentInterface unit) {
    return unit instanceof Recycle || unit instanceof Calculator || unit instanceof Adjuster;
  }

  /**
   * Builds stream-identity aliases without introducing physical recycle equipment.
   *
   * @param process source model
   * @return directed aliases for single-stream tear utilities and stream wrappers
   * @throws IOException when a recycle cannot be projected without inventing a mixer
   */
  static Map<StreamInterface, StreamInterface> numericalAliases(ProcessSystem process) throws IOException {
    Map<StreamInterface, StreamInterface> result = new IdentityHashMap<StreamInterface, StreamInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Recycle || unit instanceof StreamInterface) {
        List<StreamInterface> inlets = unit.getInletStreams();
        List<StreamInterface> outlets = unit instanceof Recycle ? unit.getOutletStreams()
            : Collections.<StreamInterface>emptyList();
        if (unit instanceof Recycle) {
          if (inlets == null || inlets.size() != 1 || outlets == null || outlets.size() != 1 || inlets.get(0) == null
              || outlets.get(0) == null) {
            throw new IOException(
                "DEXPI 2.0.1 recycle projection requires one actual inlet and outlet: " + unit.getName());
          }
          putAlias(result, outlets.get(0), inlets.get(0));
        } else if (inlets != null && inlets.size() == 1 && inlets.get(0) != null) {
          putAlias(result, (StreamInterface) unit, inlets.get(0));
        }
      }
    }
    for (StreamInterface stream : result.keySet()) {
      resolveAlias(stream, result);
    }
    return result;
  }

  /**
   * Adds an unambiguous directed alias.
   *
   * @param aliases identity map
   * @param target downstream stream identity
   * @param source actual upstream stream identity
   * @throws IOException when aliases conflict or refer directly to themselves
   */
  private static void putAlias(Map<StreamInterface, StreamInterface> aliases, StreamInterface target,
      StreamInterface source) throws IOException {
    if (target == source || aliases.containsKey(target) && aliases.get(target) != source) {
      throw new IOException("Ambiguous or self-referential DEXPI 2.0.1 stream alias: " + target.getName());
    }
    aliases.put(target, source);
  }

  /**
   * Resolves a tear/wrapper stream to its physical upstream identity.
   *
   * @param stream requested identity
   * @param aliases directed identity aliases
   * @return physical stream identity
   * @throws IOException when alias chains form a cycle
   */
  private static StreamInterface resolveAlias(StreamInterface stream, Map<StreamInterface, StreamInterface> aliases)
      throws IOException {
    Set<StreamInterface> visited = Collections.newSetFromMap(new IdentityHashMap<StreamInterface, Boolean>());
    StreamInterface current = stream;
    while (aliases.containsKey(current)) {
      if (!visited.add(current)) {
        throw new IOException("Cyclic DEXPI 2.0.1 stream alias: " + current.getName());
      }
      current = aliases.get(current);
    }
    return current;
  }

  /**
   * Maps functional valve families without inventing an unprovided body or spring design.
   *
   * @param unit source equipment
   * @return official native 2.0.1 class
   */
  private static String dexpi201Type(ProcessEquipmentInterface unit) {
    if (unit instanceof AirCooler) {
      return "Plant/ProcessEquipment.AirCoolingSystem";
    }
    if (unit instanceof FiredHeater) {
      return "Plant/ProcessEquipment.FiredHeater";
    }
    if (unit instanceof Cooler || unit instanceof HeatExchanger || unit instanceof Heater) {
      // A generic duty model does not establish air cooling, tube construction or firing.
      return "Plant/ProcessEquipment.HeatExchanger";
    }
    if (unit instanceof Mixer) {
      return "Plant/ProcessEquipment.Mixer";
    }
    if (unit instanceof Splitter) {
      List<StreamInterface> outlets = unit.getOutletStreams();
      if (outlets == null || outlets.size() != 2) {
        throw new IllegalArgumentException("DEXPI 2.0.1 PipeTee requires a two-outlet Splitter: " + unit.getName());
      }
      return "Plant/Piping.PipeTee";
    }
    if (unit instanceof RuptureDisk) {
      return "Plant/Piping.RuptureDisc";
    }
    if (unit instanceof SafetyValve || unit instanceof SafetyReliefValve) {
      return "Plant/Piping.SafetyValveOrFitting";
    }
    if (unit instanceof CheckValve) {
      return "Plant/Piping.CheckValve";
    }
    if (unit instanceof ThrottlingValve) {
      return "Plant/Piping.OperatedValve";
    }
    return dexpiType(unit);
  }

  private static String dexpiType(ProcessEquipmentInterface unit) {
    if (unit instanceof Compressor) {
      return "Plant/ProcessEquipment.CentrifugalCompressor";
    }
    if (unit instanceof Pump) {
      return "Plant/ProcessEquipment.CentrifugalPump";
    }
    if (unit instanceof Separator) {
      return "Plant/ProcessEquipment.Separator";
    }
    if (unit instanceof Cooler) {
      return "Plant/ProcessEquipment.AirCoolingSystem";
    }
    if (unit instanceof HeatExchanger) {
      return "Plant/ProcessEquipment.TubularHeatExchanger";
    }
    if (unit instanceof Heater) {
      return "Plant/ProcessEquipment.FiredHeater";
    }
    if (unit instanceof Tank) {
      return "Plant/ProcessEquipment.Tank";
    }
    if (unit instanceof ThrottlingValve) {
      return "Plant/Piping.GlobeValve";
    }
    return "Plant/ProcessEquipment.ProcessEquipment";
  }

  private static String identifier(String value, String fallback) {
    String result = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9_]", "_");
    if (result.isEmpty()) {
      result = fallback;
    }
    if (!Character.isLetter(result.charAt(0)) && result.charAt(0) != '_') {
      result = "N_" + result;
    }
    return result;
  }

  private static void transform(Document document, OutputStream outputStream) throws TransformerException {
    TransformerFactory factory = TransformerFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    Transformer transformer = factory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    // The JDK 8 transformer requires an explicit indentation width, while newer JDKs default
    // to four spaces. Declare it so golden DEXPI documents are byte-stable across supported JDKs.
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.transform(new DOMSource(document), new StreamResult(outputStream));
  }
}
