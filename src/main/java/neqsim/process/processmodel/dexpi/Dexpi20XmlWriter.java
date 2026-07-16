package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /** Writes and validates a native DEXPI 2.0 model. */
  public static void write(ProcessSystem processSystem, File file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    FileOutputStream stream = new FileOutputStream(file);
    try {
      write(processSystem, stream);
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

  /** Writes a native DEXPI 2.0 model to a stream. */
  public static void write(ProcessSystem processSystem, OutputStream outputStream) throws IOException {
    if (processSystem == null || outputStream == null) {
      throw new IllegalArgumentException("processSystem and outputStream must not be null");
    }
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
      Element conceptualModel = components(document, engineeringModel, "ConceptualModel");
      Element plant = object(document, "PlantModel1", "Plant/PlantModel");
      conceptualModel.appendChild(plant);

      Element taggedItems = components(document, plant, "TaggedPlantItems");
      Element pipingSystems = components(document, plant, "PipingNetworkSystems");
      Element pipingSystem = object(document, "PipingNetworkSystem1", "Plant/Piping.PipingNetworkSystem");
      data(document, pipingSystem, "LineNumber", "NeqSimProcessConnections");
      Element segments = components(document, pipingSystem, "Segments");
      pipingSystems.appendChild(pipingSystem);

      Map<StreamInterface, String> outletNodes = new IdentityHashMap<StreamInterface, String>();
      Map<String, String> inletNodes = new LinkedHashMap<String, String>();
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
        appendNozzle(document, nozzles, equipmentId + "Inlet", inletNode, "INLET");
        inletNodes.put(unit.getName(), inletNode);

        List<StreamInterface> outlets = unit.getOutletStreams();
        if (outlets == null || outlets.isEmpty()) {
          String outletNode = "PipingNode" + nodeNumber++;
          appendNozzle(document, nozzles, equipmentId + "Outlet", outletNode, "OUTLET");
        } else {
          for (int i = 0; i < outlets.size(); i++) {
            String outletNode = "PipingNode" + nodeNumber++;
            appendNozzle(document, nozzles, equipmentId + "Outlet" + (i + 1), outletNode, "OUTLET_" + (i + 1));
            if (outlets.get(i) != null) {
              outletNodes.put(outlets.get(i), outletNode);
            }
          }
        }
        taggedItems.appendChild(equipment);
      }

      int segmentNumber = 1;
      for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
        if (unit == null || unit instanceof Stream) {
          continue;
        }
        String targetNode = inletNodes.get(unit.getName());
        for (StreamInterface inlet : unit.getInletStreams()) {
          String sourceNode = outletNodes.get(inlet);
          if (sourceNode == null || targetNode == null) {
            continue;
          }
          appendSegment(document, segments, segmentNumber++, sourceNode, targetNode);
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

  private static void appendNozzle(Document document, Element nozzles, String nozzleId, String nodeId,
      String subTag) {
    Element nozzle = object(document, nozzleId, "Plant/ProcessEquipment.Nozzle");
    Element nodes = components(document, nozzle, "Nodes");
    nodes.appendChild(object(document, nodeId, "Plant/Piping.PipingNode"));
    data(document, nozzle, "SubTagName", subTag);
    nozzles.appendChild(nozzle);
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
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.transform(new DOMSource(document), new StreamResult(outputStream));
  }
}
