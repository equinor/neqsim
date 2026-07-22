package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/** Writes the official DEXPI 2.0 Process information model for PFD/BFD data exchange. */
public final class Dexpi20ProcessModelWriter {
  static final String CORE_MODEL = "https://data.dexpi.org/models/2.0.0/Core.xml";
  static final String PROCESS_MODEL = "https://data.dexpi.org/models/2.0.0/Process.xml";

  private Dexpi20ProcessModelWriter() {
  }

  /** Writes and validates a native DEXPI 2.0 Process model. */
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
      throw new IOException("Generated DEXPI 2.0 Process XML failed schema validation", ex);
    }
  }

  /** Writes a Process exchange and returns its auditable conformance assessment. */
  public static Dexpi20ConformanceAssessment.Report writeAndAssess(ProcessSystem processSystem, File file)
      throws IOException {
    write(processSystem, file);
    return Dexpi20ConformanceAssessment.assess(file.toPath(), Dexpi20ConformanceAssessment.Profile.PROCESS_PFD_BFD);
  }

  /** Writes a native DEXPI 2.0 Process model to a stream. */
  public static void write(ProcessSystem processSystem, OutputStream outputStream) throws IOException {
    if (processSystem == null || outputStream == null) {
      throw new IllegalArgumentException("processSystem and outputStream must not be null");
    }
    try {
      Document document = createDocument();
      Element model = document.createElement("Model");
      String modelName = identifier(processSystem.getName(), "NeqSimProcessModel");
      model.setAttribute("name", modelName);
      model.setAttribute("uri", "urn:neqsim:dexpi:2.0:process:" + modelName);
      document.appendChild(model);
      appendImport(document, model, "Core", CORE_MODEL);
      appendImport(document, model, "Process", PROCESS_MODEL);

      Element engineeringModel = object(document, null, "Core/EngineeringModel");
      model.appendChild(engineeringModel);
      Element conceptualModel = components(document, engineeringModel, "ConceptualModel");
      Element processModel = object(document, "ProcessModel1", "Process/ProcessModel");
      conceptualModel.appendChild(processModel);

      ModelTopology topology = topology(processSystem);
      Element processSteps = components(document, processModel, "ProcessSteps");
      Map<ProcessEquipmentInterface, Step> steps = new IdentityHashMap<ProcessEquipmentInterface, Step>();
      int stepNumber = 1;
      for (ProcessEquipmentInterface unit : topology.units) {
        Step step = appendStep(document, processSteps, "ProcessStep" + stepNumber++, unit.getName(), type(unit));
        steps.put(unit, step);
      }
      for (Link link : topology.links) {
        if (link.target != null) {
          continue;
        }
        String sinkName = link.source.getName() + " product " + link.number;
        link.syntheticSink = appendStep(document, processSteps, "ProcessStep" + stepNumber++, sinkName,
            "Process/Process.Sink");
      }

      Element processConnections = components(document, processModel, "ProcessConnections");
      for (Link link : topology.links) {
        String connectionId = "Stream" + link.number;
        Step source = steps.get(link.source);
        Step target = link.target == null ? link.syntheticSink : steps.get(link.target);
        String sourcePort = appendPort(document, source, connectionId + "SourcePort", connectionId, "Outlet");
        String targetPort = appendPort(document, target, connectionId + "TargetPort", connectionId, "Inlet");
        Element stream = object(document, connectionId, "Process/Process.Stream");
        data(document, stream, "Identifier", connectionId);
        data(document, stream, "Label", link.stream.getName());
        references(document, stream, "Source", sourcePort);
        references(document, stream, "Target", targetPort);
        physicalQuantity(document, stream, "MassFlow", finite(new ValueSupplier() {
          @Override
          public double value() {
            return link.stream.getFlowRate("kg/hr");
          }
        }), "Core/PhysicalQuantities.MassFlowRateUnit.KilogramPerHour");
        physicalQuantity(document, stream, "Pressure", finite(new ValueSupplier() {
          @Override
          public double value() {
            return link.stream.getPressure("bara");
          }
        }), "Core/PhysicalQuantities.PressureAbsoluteUnit.Bar");
        physicalQuantity(document, stream, "Temperature", finite(new ValueSupplier() {
          @Override
          public double value() {
            return link.stream.getTemperature("C");
          }
        }), "Core/PhysicalQuantities.TemperatureUnit.DegreeCelsius");
        processConnections.appendChild(stream);
      }
      transform(document, outputStream);
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not create DEXPI 2.0 Process XML document", ex);
    } catch (TransformerException ex) {
      throw new IOException("Could not serialize DEXPI 2.0 Process XML document", ex);
    }
  }

  private static ModelTopology topology(ProcessSystem processSystem) {
    List<ProcessEquipmentInterface> units = new ArrayList<ProcessEquipmentInterface>();
    Map<StreamInterface, ProcessEquipmentInterface> sourceByStream = new IdentityHashMap<StreamInterface, ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit == null) {
        continue;
      }
      type(unit);
      units.add(unit);
      if (unit instanceof StreamInterface) {
        sourceByStream.put((StreamInterface) unit, unit);
      } else {
        for (StreamInterface outlet : unit.getOutletStreams()) {
          if (outlet != null) {
            sourceByStream.put(outlet, unit);
          }
        }
      }
    }
    List<Link> links = new ArrayList<Link>();
    Map<StreamInterface, Boolean> consumed = new IdentityHashMap<StreamInterface, Boolean>();
    int number = 1;
    for (ProcessEquipmentInterface target : units) {
      if (target instanceof StreamInterface) {
        continue;
      }
      for (StreamInterface inlet : target.getInletStreams()) {
        ProcessEquipmentInterface source = sourceByStream.get(inlet);
        if (source != null) {
          links.add(new Link(number++, source, target, inlet));
          consumed.put(inlet, Boolean.TRUE);
        }
      }
    }
    for (ProcessEquipmentInterface source : units) {
      if (source instanceof StreamInterface) {
        continue;
      }
      for (StreamInterface outlet : source.getOutletStreams()) {
        if (outlet != null && !consumed.containsKey(outlet)) {
          links.add(new Link(number++, source, null, outlet));
        }
      }
    }
    if (links.isEmpty()) {
      throw new IllegalArgumentException("DEXPI Process export requires at least one material connection");
    }
    return new ModelTopology(units, links);
  }

  private static Step appendStep(Document document, Element processSteps, String id, String name, String type) {
    Element step = object(document, id, type);
    data(document, step, "Identifier", name);
    data(document, step, "Label", name);
    Element ports = components(document, step, "Ports");
    processSteps.appendChild(step);
    return new Step(ports);
  }

  private static String appendPort(Document document, Step step, String id, String connectionId, String direction) {
    Element port = object(document, id, "Process/Process.MaterialPort");
    data(document, port, "Identifier", id);
    dataReference(document, port, "NominalDirection", "Process/Enumerations.PortDirection." + direction);
    references(document, port, "ConnectorReference", connectionId);
    step.ports.appendChild(port);
    return id;
  }

  private static String type(ProcessEquipmentInterface unit) {
    if (unit instanceof StreamInterface) {
      return "Process/Process.Source";
    }
    if (unit instanceof Expander) {
      return "Process/Process.TransportingFluids";
    }
    if (unit instanceof PipeLineInterface) {
      return "Process/Process.TransportingFluids";
    }
    if (unit instanceof Compressor) {
      return "Process/Process.Compressing";
    }
    if (unit instanceof Pump) {
      return "Process/Process.Pumping";
    }
    if (unit instanceof DistillationColumn) {
      return "Process/Process.Distilling";
    }
    if (unit instanceof Separator) {
      return "Process/Process.SeparatingByGravity";
    }
    if (unit instanceof Cooler) {
      return "Process/Process.Cooling";
    }
    if (unit instanceof HeatExchanger) {
      return "Process/Process.ExchangingThermalEnergy";
    }
    if (unit instanceof Heater) {
      return "Process/Process.HeatingInFurnace";
    }
    if (unit instanceof Tank) {
      return "Process/Process.StoringFluids";
    }
    if (unit instanceof ThrottlingValve) {
      return "Process/Process.RegulatingFlow";
    }
    if (unit instanceof Mixer) {
      return "Process/Process.MixingSimple";
    }
    if (unit instanceof Splitter) {
      return "Process/Process.SplittingMaterial";
    }
    throw new IllegalArgumentException(
        "No reviewed DEXPI 2.0 Process type mapping for " + unit.getClass().getName() + " (" + unit.getName() + ")");
  }

  private static void physicalQuantity(Document document, Element stream, String property, Double value,
      String unitReference) {
    if (value == null) {
      return;
    }
    Element values = components(document, stream, property);
    Element qualified = object(document, null, "Core/QualifiedValue");
    Element data = document.createElement("Data");
    data.setAttribute("property", "Value");
    Element quantity = document.createElement("AggregatedDataValue");
    quantity.setAttribute("type", "Core/PhysicalQuantities.PhysicalQuantity");
    Element unitData = document.createElement("Data");
    unitData.setAttribute("property", "Unit");
    Element unit = document.createElement("DataReference");
    unit.setAttribute("data", unitReference);
    unitData.appendChild(unit);
    quantity.appendChild(unitData);
    Element valueData = document.createElement("Data");
    valueData.setAttribute("property", "Value");
    Element number = document.createElement("Double");
    number.setTextContent(Double.toString(value.doubleValue()));
    valueData.appendChild(number);
    quantity.appendChild(valueData);
    data.appendChild(quantity);
    qualified.appendChild(data);
    values.appendChild(qualified);
  }

  private static Double finite(ValueSupplier supplier) {
    try {
      double value = supplier.value();
      return Double.isFinite(value) ? Double.valueOf(value) : null;
    } catch (RuntimeException ex) {
      return null;
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
    Element result = document.createElement("Components");
    result.setAttribute("property", property);
    parent.appendChild(result);
    return result;
  }

  private static void data(Document document, Element parent, String property, String value) {
    Element data = document.createElement("Data");
    data.setAttribute("property", property);
    Element string = document.createElement("String");
    string.setTextContent(value == null ? "" : value);
    data.appendChild(string);
    parent.appendChild(data);
  }

  private static void dataReference(Document document, Element parent, String property, String value) {
    Element data = document.createElement("Data");
    data.setAttribute("property", property);
    Element reference = document.createElement("DataReference");
    reference.setAttribute("data", value);
    data.appendChild(reference);
    parent.appendChild(data);
  }

  private static void references(Document document, Element parent, String property, String id) {
    Element reference = document.createElement("References");
    reference.setAttribute("property", property);
    reference.setAttribute("objects", "#" + identifier(id, "Object"));
    parent.appendChild(reference);
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
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.transform(new DOMSource(document), new StreamResult(outputStream));
  }

  private interface ValueSupplier {
    double value();
  }

  private static final class Step {
    private final Element ports;

    Step(Element ports) {
      this.ports = ports;
    }
  }

  private static final class Link {
    private final int number;
    private final ProcessEquipmentInterface source;
    private final ProcessEquipmentInterface target;
    private final StreamInterface stream;
    private Step syntheticSink;

    Link(int number, ProcessEquipmentInterface source, ProcessEquipmentInterface target, StreamInterface stream) {
      this.number = number;
      this.source = source;
      this.target = target;
      this.stream = stream;
    }
  }

  private static final class ModelTopology {
    private final List<ProcessEquipmentInterface> units;
    private final List<Link> links;

    ModelTopology(List<ProcessEquipmentInterface> units, List<Link> links) {
      this.units = units;
      this.links = links;
    }
  }
}
