package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
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
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
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
import neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter;

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

  /**
   * Writes a Process exchange and compares supported material topology with the canonical diagram graph.
   *
   * @param processSystem source simulation topology
   * @param file destination DEXPI XML file
   * @param plantId persistent plant or project identifier used by the canonical graph
   * @param revision controlled source-model revision
   * @return schema/profile conformance plus structured topology-equivalence and scope evidence
   * @throws IOException if serialization, validation, or assessment fails
   */
  public static Dexpi20ProcessTopologyAssessment.Report writeAndAssessTopology(ProcessSystem processSystem, File file,
      String plantId, String revision) throws IOException {
    ProcessDiagramGraphAdapter.Result canonical = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision);
    writeCanonical(processSystem, file, canonical);
    Dexpi20ConformanceAssessment.Report conformance = Dexpi20ConformanceAssessment.assess(file.toPath(),
        Dexpi20ConformanceAssessment.Profile.PROCESS_PFD_BFD);
    return Dexpi20ProcessTopologyAssessment.assess(processSystem, file.toPath(), conformance, canonical,
        "CANONICAL_ENGINEERING_GRAPH");
  }

  /**
   * Writes an assessed Process exchange whose operating values come from one canonical case snapshot.
   *
   * <p>
   * This opt-in overload does not change the established topology-only overload. Only finite, unit-explicit calculation
   * nodes for {@code operatingCaseId} are serialized; missing values are omitted with structured diagnostics and are
   * never read directly from streams as a fallback. Generated values remain calculated, review-required engineering
   * evidence.
   * </p>
   *
   * @param processSystem source simulation topology with a successful completed run
   * @param file destination DEXPI XML file
   * @param plantId persistent plant or project identifier used by the canonical graph
   * @param revision controlled source-model revision
   * @param operatingCaseId stable operating-case identifier
   * @return schema/profile conformance plus topology, value-source, and loss evidence
   * @throws IOException if serialization, validation, or assessment fails
   */
  public static Dexpi20ProcessTopologyAssessment.Report writeAndAssessTopology(ProcessSystem processSystem, File file,
      String plantId, String revision, String operatingCaseId) throws IOException {
    ProcessDiagramGraphAdapter.Result canonical = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision, operatingCaseId);
    CanonicalOperatingValues operatingValues = new CanonicalOperatingValues(canonical.getGraph(), operatingCaseId);
    writeCanonical(processSystem, file, canonical, operatingValues);
    Dexpi20ConformanceAssessment.Report conformance = Dexpi20ConformanceAssessment.assess(file.toPath(),
        Dexpi20ConformanceAssessment.Profile.PROCESS_PFD_BFD);
    return Dexpi20ProcessTopologyAssessment.assess(processSystem, file.toPath(), conformance, canonical,
        "CANONICAL_ENGINEERING_GRAPH", "CANONICAL_ENGINEERING_GRAPH_CALCULATION_NODES",
        operatingValues.getDiagnostics());
  }

  /** Writes a native DEXPI 2.0 Process model to a stream. */
  public static void write(ProcessSystem processSystem, OutputStream outputStream) throws IOException {
    if (processSystem == null || outputStream == null) {
      throw new IllegalArgumentException("processSystem and outputStream must not be null");
    }
    write(processSystem, outputStream, topology(processSystem), new DirectOperatingValues());
  }

  private static void write(ProcessSystem processSystem, OutputStream outputStream, ModelTopology topology,
      OperatingValues operatingValues) throws IOException {
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
        physicalQuantity(document, stream, "MassFlow", operatingValues.value(link, "massFlow"),
            "Core/PhysicalQuantities.MassFlowRateUnit.KilogramPerHour");
        physicalQuantity(document, stream, "Pressure", operatingValues.value(link, "pressure"),
            "Core/PhysicalQuantities.PressureAbsoluteUnit.Bar");
        physicalQuantity(document, stream, "Temperature", operatingValues.value(link, "temperature"),
            "Core/PhysicalQuantities.TemperatureUnit.DegreeCelsius");
        processConnections.appendChild(stream);
      }
      transform(document, outputStream);
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not create DEXPI 2.0 Process XML document", ex);
    } catch (TransformerException ex) {
      throw new IOException("Could not serialize DEXPI 2.0 Process XML document", ex);
    }
  }

  private static void writeCanonical(ProcessSystem processSystem, File file,
      ProcessDiagramGraphAdapter.Result canonical) throws IOException {
    writeCanonical(processSystem, file, canonical, new DirectOperatingValues());
  }

  private static void writeCanonical(ProcessSystem processSystem, File file,
      ProcessDiagramGraphAdapter.Result canonical, OperatingValues operatingValues) throws IOException {
    if (file == null || canonical == null) {
      throw new IllegalArgumentException("file and canonical must not be null");
    }
    FileOutputStream stream = new FileOutputStream(file);
    try {
      write(processSystem, stream, topology(processSystem, canonical), operatingValues);
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

  private static ModelTopology topology(ProcessSystem processSystem, ProcessDiagramGraphAdapter.Result canonical) {
    ModelTopology direct = topology(processSystem);
    EngineeringGraph graph = canonical.getGraph();
    List<Link> projected = new ArrayList<Link>();
    Map<Link, Boolean> consumed = new IdentityHashMap<Link, Boolean>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() != EngineeringNode.Kind.PIPE_SEGMENT) {
        continue;
      }
      Link match = findDirectLink(direct.links, consumed, node);
      if (match != null) {
        match.canonicalValueSubjectId = sourceOwnerNodeId(graph, node);
        projected.add(match);
        consumed.put(match, Boolean.TRUE);
      }
    }
    for (Link link : direct.links) {
      if (!consumed.containsKey(link)) {
        projected.add(link);
      }
    }
    return new ModelTopology(direct.units, projected);
  }

  private static String sourceOwnerNodeId(EngineeringGraph graph, EngineeringNode connection) {
    EngineeringNode endpoint = graph.getNode(property(connection, "sourceEndpointId"));
    return endpoint == null ? "" : property(endpoint, "ownerNodeId");
  }

  private static Link findDirectLink(List<Link> links, Map<Link, Boolean> consumed, EngineeringNode connection) {
    String sourceName = property(connection, "sourceEquipment");
    String targetName = property(connection, "targetEquipment");
    String carriedName = property(connection, "carriedObjectName");
    for (Link link : links) {
      if (link.target != null && !consumed.containsKey(link) && sourceName.equals(link.source.getName())
          && targetName.equals(link.target.getName()) && carriedName.equals(streamName(link.stream))) {
        return link;
      }
    }
    return null;
  }

  private static String property(EngineeringNode node, String name) {
    Object value = node.getProperties().get(name);
    return value == null ? "" : String.valueOf(value);
  }

  private static String streamName(StreamInterface stream) {
    try {
      return stream == null || stream.getName() == null ? "" : stream.getName();
    } catch (RuntimeException ex) {
      return "";
    }
  }

  private static Step appendStep(Document document, Element processSteps, String id, String name, String type) {
    Element step = object(document, id, type);
    data(document, step, "Identifier", name);
    data(document, step, "Label", name);
    processSteps.appendChild(step);
    return new Step(step);
  }

  private static String appendPort(Document document, Step step, String id, String connectionId, String direction) {
    Element port = object(document, id, "Process/Process.MaterialPort");
    data(document, port, "Identifier", id);
    dataReference(document, port, "NominalDirection", "Process/Enumerations.PortDirection." + direction);
    references(document, port, "ConnectorReference", connectionId);
    step.ports(document).appendChild(port);
    return id;
  }

  private static String type(ProcessEquipmentInterface unit) {
    if (unit instanceof StreamInterface) {
      return hasMaterialInlet(unit) ? "Process/Process.Sink" : "Process/Process.Source";
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

  private static boolean hasMaterialInlet(ProcessEquipmentInterface unit) {
    try {
      List<StreamInterface> inlets = unit.getInletStreams();
      if (inlets == null) {
        return false;
      }
      for (StreamInterface inlet : inlets) {
        if (inlet != null && inlet != unit) {
          return true;
        }
      }
    } catch (RuntimeException ex) {
      // An unconfigured stream remains a source and is serialized without ports.
    }
    return false;
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

  private interface OperatingValues {
    Double value(Link link, String quantity);
  }

  private static final class DirectOperatingValues implements OperatingValues {
    @Override
    public Double value(final Link link, String quantity) {
      if ("massFlow".equals(quantity)) {
        return finite(new ValueSupplier() {
          @Override
          public double value() {
            return link.stream.getFlowRate("kg/hr");
          }
        });
      }
      if ("pressure".equals(quantity)) {
        return finite(new ValueSupplier() {
          @Override
          public double value() {
            return link.stream.getPressure("bara");
          }
        });
      }
      if ("temperature".equals(quantity)) {
        return finite(new ValueSupplier() {
          @Override
          public double value() {
            return link.stream.getTemperature("C");
          }
        });
      }
      return null;
    }
  }

  private static final class CanonicalOperatingValues implements OperatingValues {
    private final Map<String, Map<String, Double>> values = new LinkedHashMap<String, Map<String, Double>>();
    private final List<Dexpi20ProcessTopologyAssessment.Diagnostic> diagnostics = new ArrayList<Dexpi20ProcessTopologyAssessment.Diagnostic>();

    CanonicalOperatingValues(EngineeringGraph graph, String operatingCaseId) {
      for (EngineeringNode node : graph.getNodes().values()) {
        if (node.getKind() != EngineeringNode.Kind.CALCULATION
            || !operatingCaseId.equals(property(node, "designCaseId"))
            || !"CALCULATED".equals(property(node, "status"))) {
          continue;
        }
        String subjectNodeId = property(node, "subjectNodeId");
        String quantity = property(node, "quantity");
        Double converted = convertedValue(node, quantity);
        if (subjectNodeId.isEmpty() || quantity.isEmpty() || converted == null) {
          continue;
        }
        Map<String, Double> subjectValues = values.get(subjectNodeId);
        if (subjectValues == null) {
          subjectValues = new LinkedHashMap<String, Double>();
          values.put(subjectNodeId, subjectValues);
        }
        subjectValues.put(quantity, converted);
      }
    }

    @Override
    public Double value(Link link, String quantity) {
      String subject = link.canonicalValueSubjectId.isEmpty() ? streamName(link.stream) : link.canonicalValueSubjectId;
      Map<String, Double> subjectValues = values.get(link.canonicalValueSubjectId);
      Double result = subjectValues == null ? null : subjectValues.get(quantity);
      if (result == null) {
        diagnostics.add(new Dexpi20ProcessTopologyAssessment.Diagnostic(
            Dexpi20ProcessTopologyAssessment.Severity.WARNING, "DEXPI_PROCESS_OPERATING_VALUE_MISSING",
            "No matching canonical calculation node was available; the value was omitted without stream fallback",
            subject + "/" + quantity));
      }
      return result;
    }

    List<Dexpi20ProcessTopologyAssessment.Diagnostic> getDiagnostics() {
      return Collections.unmodifiableList(diagnostics);
    }

    private static Double convertedValue(EngineeringNode node, String quantity) {
      Object rawValue = node.getProperties().get("resultValue");
      if (!(rawValue instanceof Number)) {
        return null;
      }
      double value = ((Number) rawValue).doubleValue();
      String unit = property(node, "resultUnit");
      String basis = property(node, "quantityBasis");
      if ("massFlow".equals(quantity) && "kg/s".equals(unit) && "MASS".equals(basis)) {
        value *= 3600.0;
      } else if ("pressure".equals(quantity) && "bara".equals(unit) && "ABSOLUTE".equals(basis)) {
        // DEXPI Process uses bar with an absolute-pressure unit reference.
      } else if ("temperature".equals(quantity) && "K".equals(unit) && "THERMODYNAMIC_ABSOLUTE".equals(basis)) {
        value -= 273.15;
      } else {
        return null;
      }
      return Double.isFinite(value) ? Double.valueOf(value) : null;
    }
  }

  private static final class Step {
    private final Element processStep;
    private Element ports;

    Step(Element processStep) {
      this.processStep = processStep;
    }

    Element ports(Document document) {
      if (ports == null) {
        ports = components(document, processStep, "Ports");
      }
      return ports;
    }
  }

  private static final class Link {
    private final int number;
    private final ProcessEquipmentInterface source;
    private final ProcessEquipmentInterface target;
    private final StreamInterface stream;
    private String canonicalValueSubjectId = "";
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
