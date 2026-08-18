package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.valve.ThrottlingValve;
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
      Dexpi20XmlValidator.validate(file.toPath());
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
    return Dexpi20ConformanceAssessment.assess(file.toPath(), Dexpi20ConformanceAssessment.Profile.PLANT_P_ID);
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
    boolean explicitBoundaryConnectors = options != null && options
        .getBoundaryConnectionMode() == Dexpi20PlantExportOptions.BoundaryConnectionMode.EXPLICIT_OFF_PAGE_CONNECTORS;
    try {
      Document document = createDocument();
      Element model = document.createElement("Model");
      model.setAttribute("name", identifier(processSystem.getName(), "NeqSimProcess"));
      model.setAttribute("uri", "urn:neqsim:dexpi:2.0:" + identifier(processSystem.getName(), "process"));
      document.appendChild(model);
      appendImport(document, model, "Core", CORE_MODEL);
      appendImport(document, model, "Plant", PLANT_MODEL);

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

      Map<StreamInterface, String> outletNodes = new IdentityHashMap<StreamInterface, String>();
      Map<String, String> inletNodes = new LinkedHashMap<String, String>();
      Map<String, String> nodeOwners = new LinkedHashMap<String, String>();
      Map<String, String> boundaryOutletNodes = new LinkedHashMap<String, String>();
      int equipmentNumber = 1;
      int nodeNumber = 1;
      for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
        if (unit == null || unit instanceof Stream) {
          continue;
        }
        String equipmentId = "Equipment" + equipmentNumber++;
        Element equipment = object(document, equipmentId, dexpiType(unit));
        data(document, equipment, "TagName", unit.getName());
        Element nozzles = components(document, equipment, "Nozzles");

        String inletNode = "PipingNode" + nodeNumber++;
        String inletNozzleId = equipmentId + "Inlet";
        appendNozzle(document, nozzles, inletNozzleId, inletNode, "INLET");
        inletNodes.put(unit.getName(), inletNode);
        nodeOwners.put(inletNode, inletNozzleId);

        List<StreamInterface> outlets = unit.getOutletStreams();
        if (outlets == null || outlets.isEmpty()) {
          String outletNode = "PipingNode" + nodeNumber++;
          String outletNozzleId = equipmentId + "Outlet";
          appendNozzle(document, nozzles, outletNozzleId, outletNode, "OUTLET");
          nodeOwners.put(outletNode, outletNozzleId);
          boundaryOutletNodes.put(outletNode, unit.getName() + " product");
        } else {
          for (int i = 0; i < outlets.size(); i++) {
            String outletNode = "PipingNode" + nodeNumber++;
            String outletNozzleId = equipmentId + "Outlet" + (i + 1);
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
        taggedItems.appendChild(equipment);
      }

      int segmentNumber = 1;
      Set<String> connectedOutletNodes = new LinkedHashSet<String>();
      for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
        if (unit == null || unit instanceof Stream) {
          continue;
        }
        String targetNode = inletNodes.get(unit.getName());
        List<StreamInterface> inlets = unit.getInletStreams();
        if ((inlets == null || inlets.isEmpty()) && explicitBoundaryConnectors && targetNode != null) {
          appendBoundarySegment(document, segments, segmentNumber++, true, targetNode, nodeOwners.get(targetNode),
              unit.getName() + " feed");
        } else if (inlets != null) {
          for (StreamInterface inlet : inlets) {
            String sourceNode = outletNodes.get(inlet);
            if (sourceNode == null || targetNode == null) {
              if (explicitBoundaryConnectors && targetNode != null) {
                appendBoundarySegment(document, segments, segmentNumber++, true, targetNode,
                    nodeOwners.get(targetNode), boundaryLabel(inlet, unit.getName() + " feed"));
              }
              continue;
            }
            appendSegment(document, segments, segmentNumber++, sourceNode, targetNode);
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

      if (equipmentNumber == 1) {
        plant.removeChild(taggedItems);
      }
      if (segmentNumber == 1) {
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

  private static void appendEngineeringModelMetadata(Document document, Element engineeringModel,
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
    Element nozzle = object(document, nozzleId, "Plant/ProcessEquipment.Nozzle");
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
      String targetNode) {
    Element segment = object(document, "PipingNetworkSegment" + number, "Plant/Piping.PipingNetworkSegment");
    Element connections = components(document, segment, "Connections");
    Element pipe = object(document, "Pipe" + number, "Plant/Piping.Pipe");
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
