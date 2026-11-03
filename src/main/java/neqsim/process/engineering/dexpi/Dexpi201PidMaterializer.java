package neqsim.process.engineering.dexpi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidPhysicalProjection;
import neqsim.process.engineering.pid.PidPhysicalProjection.Attachment;
import neqsim.process.engineering.pid.PidPhysicalProjection.Kind;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.Dexpi201XmlWriter;
import neqsim.process.processmodel.dexpi.Dexpi20ConformanceAssessment;

/**
 * Projects declared P&amp;ID proposal functions and physical attachments into native DEXPI 2.0.1.
 *
 * <p>
 * Inline devices split the actual source pipe. Explicit auxiliary routes introduce connected tee nodes. Missing route
 * evidence is retained as a HOLD rather than a disconnected tagged object. A projected design proposal does not
 * implement its simulation or approve its engineering design.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class Dexpi201PidMaterializer {
  /** Prevents construction. */
  private Dexpi201PidMaterializer() {
  }

  /** Immutable native projection evidence. */
  public static final class Result {
    private final Map<String, String> nativeIds;
    private final Map<String, String> unresolved;
    private final List<Map<String, String>> signalLinks;
    private final List<Map<String, String>> proposedOwnedNozzles;
    private final Dexpi20ConformanceAssessment.Report assessment;

    /**
     * Creates retained evidence.
     *
     * @param context actual projection context
     * @param report assessment of the final bytes
     */
    private Result(Context context, Dexpi20ConformanceAssessment.Report report) {
      nativeIds = Collections.unmodifiableMap(new LinkedHashMap<String, String>(context.nativeIds));
      unresolved = Collections.unmodifiableMap(new LinkedHashMap<String, String>(context.unresolved));
      signalLinks = Collections.unmodifiableList(new ArrayList<Map<String, String>>(context.signalLinks));
      proposedOwnedNozzles = Collections
          .unmodifiableList(new ArrayList<Map<String, String>>(context.proposedOwnedNozzles));
      assessment = report;
    }

    /** @return mapped P&amp;ID tag to actual native object ID */
    public Map<String, String> getNativeIds() {
      return nativeIds;
    }

    /** @return missing tag to explicit unresolved physical or semantic reason */
    public Map<String, String> getUnresolved() {
      return unresolved;
    }

    /** @return actual native directed signal-link evidence */
    public List<Map<String, String>> getSignalLinks() {
      return signalLinks;
    }

    /** @return exact final-byte assessment */
    public Dexpi20ConformanceAssessment.Report getAssessment() {
      return assessment;
    }

    /** @return whether every proposal has a supported native attachment and every declared signal is resolved */
    public boolean isComplete() {
      if (!unresolved.isEmpty() || !assessment.isSchemaAndProfileConformant()) {
        return false;
      }
      for (Map<String, String> link : signalLinks) {
        if ("UNRESOLVED".equals(link.get("status"))) {
          return false;
        }
      }
      return true;
    }

    /** @return machine-readable scope and unresolved evidence */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_dexpi_201_pid_projection.v1");
      result.put("nativeIdsByPidTag", nativeIds);
      result.put("unresolvedTags", unresolved);
      result.put("signalLinks", signalLinks);
      result.put("proposedOwnedNozzles", proposedOwnedNozzles);
      result.put("completeSupportedProjection", Boolean.valueOf(isComplete()));
      result.put("nativeSchemaAndProfileConformant", Boolean.valueOf(assessment.isSchemaAndProfileConformant()));
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("fullPidSemanticEquivalence", "NAMED_TOOL_QUALIFICATION_REQUIRED");
      return result;
    }

    /** @return indented JSON evidence */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  /**
   * Materializes only source-backed proposal attachments and writes exact-byte projection evidence.
   *
   * @param project controlled engineering project
   * @param model final P&amp;ID proposal model
   * @param bindings explicit runtime bindings
   * @param file existing native DEXPI 2.0.1 Plant file
   * @return supported projection and conformance evidence
   * @throws IOException if parsing, projection or serialization fails
   */
  public static Result materialize(EngineeringProject project, PidDesignModel model, PidSimulationBindings bindings,
      Path file) throws IOException {
    if (project == null || model == null || bindings == null || file == null) {
      throw new IllegalArgumentException("project, model, bindings and file are required");
    }
    bindings.assertCurrent(project, model);
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      Document document = factory.newDocumentBuilder().parse(file.toFile());
      if (!Dexpi201XmlWriter.isSpecification201(document)) {
        throw new IllegalArgumentException("Native P&ID projection requires the exact DEXPI 2.0.1 Plant profile");
      }
      Context context = new Context(project.getEngineeringProcessSystem(), model, document,
          PidPhysicalProjection.resolve(project.getEngineeringProcessSystem(), model, bindings));
      context.project();
      TransformerFactory transforms = TransformerFactory.newInstance();
      transforms.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = transforms.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      removeWhitespace(document.getDocumentElement());
      transformer.transform(new DOMSource(document), new StreamResult(file.toFile()));
      Result result = new Result(context, Dexpi201XmlWriter.assess(file));
      Files.write(file.resolveSibling(file.getFileName().toString() + ".pid-projection.json"),
          result.toJson().getBytes(StandardCharsets.UTF_8));
      return result;
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Could not materialize connected native DEXPI 2.0.1 P&ID proposals", ex);
    }
  }

  /** Source-backed mutable document transformation used only for one export. */
  private static final class Context {
    private final ProcessSystem process;
    private final PidDesignModel model;
    private final Document document;
    private final PidPhysicalProjection projection;
    private final Map<String, Element> byId = new LinkedHashMap<String, Element>();
    private final Map<String, Element> byTag = new LinkedHashMap<String, Element>();
    private final Map<String, String> nativeIds = new LinkedHashMap<String, String>();
    private final Map<String, String> functionalIds = new LinkedHashMap<String, String>();
    private final Map<String, Element> functionOwners = new LinkedHashMap<String, Element>();
    private final Map<String, String> unresolved = new LinkedHashMap<String, String>();
    private final Map<String, Element> tails = new LinkedHashMap<String, Element>();
    private final Map<String, Element> designedNozzles = new LinkedHashMap<String, Element>();
    private final Map<String, String> designedNozzleInputs = new LinkedHashMap<String, String>();
    private final List<Map<String, String>> proposedOwnedNozzles = new ArrayList<Map<String, String>>();
    private final List<Map<String, String>> signalLinks = new ArrayList<Map<String, String>>();
    private final Element plant;

    /**
     * Indexes the actual existing native objects.
     *
     * @param source source process
     * @param pid source P&amp;ID
     * @param xml existing native document
     * @param attachments shared attachment register
     */
    private Context(ProcessSystem source, PidDesignModel pid, Document xml, PidPhysicalProjection attachments) {
      process = source;
      model = pid;
      document = xml;
      projection = attachments;
      Element found = null;
      NodeList objects = document.getElementsByTagName("Object");
      for (int i = 0; i < objects.getLength(); i++) {
        Element object = (Element) objects.item(i);
        byId.put(object.getAttribute("id"), object);
        if ("Plant/PlantModel".equals(object.getAttribute("type"))) {
          found = object;
        }
        for (String key : new String[] {"TagName", "PipingComponentNumber", "PositionNumber", "PipeConnectorNumber"}) {
          String tag = value(object, key);
          if (!tag.isEmpty()) {
            byTag.put(tag, object);
          }
        }
      }
      if (found == null) {
        throw new IllegalArgumentException("Native file has no PlantModel");
      }
      plant = found;
    }

    /** Projects physical objects before functional signal references. */
    private void project() {
      Map<String, List<PidElement>> groups = new LinkedHashMap<String, List<PidElement>>();
      for (PidElement element : model.getElements()) {
        String routeId = projection.getAttachments().get(element.getTag()).getPhysicalRouteId();
        if (!routeId.isEmpty()) {
          if (!groups.containsKey(routeId)) {
            groups.put(routeId, new ArrayList<PidElement>());
          }
          groups.get(routeId).add(element);
        }
      }
      Set<String> projectedGroups = new LinkedHashSet<String>();
      for (PidElement element : model.getElements()) {
        Attachment attachment = projection.getAttachments().get(element.getTag());
        if (!attachment.getPhysicalRouteId().isEmpty()) {
          if (projectedGroups.add(attachment.getPhysicalRouteId())) {
            projectSeries(groups.get(attachment.getPhysicalRouteId()));
          }
          continue;
        }
        try {
          projectOne(element, attachment);
        } catch (IllegalArgumentException ex) {
          unresolved.put(element.getTag(), ex.getMessage());
        }
      }
      for (PidElement element : model.getElements()) {
        if (!nativeIds.containsKey(element.getTag()) && byTag.containsKey(element.getTag())) {
          nativeIds.put(element.getTag(), byTag.get(element.getTag()).getAttribute("id"));
          unresolved.remove(element.getTag());
        }
      }
      for (Map<String, String> link : projection.getSignals()) {
        signal(link);
      }
    }

    /**
     * Projects one already-resolved proposal.
     *
     * @param element semantic proposal
     * @param attachment shared physical attachment
     */
    private void projectOne(PidElement element, Attachment attachment) {
      if (attachment.getKind() == Kind.UNRESOLVED) {
        unresolved.put(element.getTag(), attachment.getReason());
        return;
      }
      if (attachment.getKind() == Kind.FUNCTION) {
        function(element, false, "");
        return;
      }
      if (attachment.getKind() == Kind.SENSING) {
        String location = sensingLocation(attachment);
        function(element, true, location);
        return;
      }
      if (attachment.getKind() == Kind.PROCESS_NOZZLE) {
        Element port = port(attachment.getEquipmentTag(), attachment.getDirection(), attachment.getStreamIndex());
        if (!port.getAttribute("type").contains("Nozzle")) {
          throw new IllegalArgumentException("PIPING_COMPONENT_NODE_HAS_NO_NATIVE_NOZZLE_TAG_PROPERTY");
        }
        setData(port, "SubTagName", element.getTag());
        nativeIds.put(element.getTag(), port.getAttribute("id"));
        return;
      }
      if (attachment.getKind() == Kind.EXISTING_EQUIPMENT) {
        Element existing = byTag.get(attachment.getEquipmentTag());
        if (existing == null) {
          throw new IllegalArgumentException("BOUND_EQUIPMENT_NOT_IN_NATIVE_EXPORT");
        }
        nativeIds.put(element.getTag(), existing.getAttribute("id"));
        if ("Plant/Piping.OperatedValve".equals(existing.getAttribute("type"))) {
          actuator(element, existing, ancestor(existing, "Plant/Piping.PipingNetworkSegment"));
        }
        return;
      }
      if (attachment.getKind() == Kind.INLINE_VALVE) {
        Element existing = byTag.get(element.getTag());
        if (existing != null) {
          nativeIds.put(element.getTag(), existing.getAttribute("id"));
          if ("Plant/Piping.OperatedValve".equals(existing.getAttribute("type"))) {
            actuator(element, existing, ancestor(existing, "Plant/Piping.PipingNetworkSegment"));
          }
          return;
        }
        String key = attachment.getEquipmentTag() + ":" + attachment.getDirection() + ":" + attachment.getStreamTag();
        Element pipe = tails.get(key);
        if (pipe == null) {
          pipe = pipe(attachment.getEquipmentTag(), attachment.getDirection(), attachment.getStreamIndex());
        }
        Element segment = ancestor(pipe, "Plant/Piping.PipingNetworkSegment");
        Element valve = valve(element, segment);
        Element tail = insert(pipe, valve, "Inlet", "Outlet");
        tails.put(key, tail);
        if ("Plant/Piping.OperatedValve".equals(valve.getAttribute("type"))) {
          actuator(element, valve, segment);
        }
        return;
      }
      if (attachment.getKind() == Kind.AUXILIARY_ROUTE) {
        auxiliary(element, attachment);
      }
    }

    /**
     * Creates the typed native sensor/function and its true sensing reference.
     *
     * @param element semantic function or measurement
     * @param measurement whether a process signal generator is required
     * @param location native sensing-location ID
     */
    private void function(PidElement element, boolean measurement, String location) {
      String type = element.getType() == PidElementType.TRIP || element.getType() == PidElementType.SAFETY_FUNCTION
          ? "Plant/Instrumentation.ProcessSafetyFunction"
          : "Plant/Instrumentation.ProcessInstrumentationFunction";
      Element function = object("PidFunction_" + safe(element.getId()), type);
      setData(function, "ProcessInstrumentationFunctionNumber", element.getTag());
      setData(function, "DeviceInformation", "NeqSim design proposal; " + element.getType().name() + "; "
          + element.getStatus().name() + "; source rule " + element.getRuleId());
      Element locationData = data(function, "Location");
      Element locationValue = document.createElement("DataReference");
      locationValue.setAttribute("data",
          "Plant/Enumerations.LocationClassification." + (measurement ? "Field" : "CentralLocation"));
      locationData.appendChild(locationValue);
      components(plant, "ProcessInstrumentationFunctions").appendChild(function);
      functionOwners.put(element.getTag(), function);
      String endpoint = function.getAttribute("id");
      if (measurement) {
        Element generator = object("PidSensor_" + safe(element.getId()),
            "Plant/Instrumentation.ProcessSignalGeneratingFunction");
        setData(generator, "ProcessSignalGeneratingFunctionNumber", element.getTag());
        reference(generator, "SensingLocation", location);
        components(function, "ProcessSignalGeneratingFunctions").appendChild(generator);
        endpoint = generator.getAttribute("id");
      }
      nativeIds.put(element.getTag(), function.getAttribute("id"));
      functionalIds.put(element.getTag(), endpoint);
    }

    /**
     * Resolves a sensor to the actual pipe segment or an equipment instrument nozzle.
     *
     * @param attachment source location
     * @return native sensing location ID
     */
    private String sensingLocation(Attachment attachment) {
      if ("DESIGNED_NOZZLE".equals(attachment.getDirection())) {
        return designedNozzle(attachment.getSourceEquipmentTag(), attachment.getSourceNozzleTag(),
            attachment.getSourcePortRole(), attachment.getDesignEvidenceReference()).getAttribute("id");
      }
      if (!"VESSEL".equals(attachment.getDirection())) {
        return ancestor(pipe(attachment.getEquipmentTag(), attachment.getDirection(), attachment.getStreamIndex()),
            "Plant/Piping.PipingNetworkSegment").getAttribute("id");
      }
      Element owner = byTag.get(attachment.getEquipmentTag());
      if (owner == null) {
        throw new IllegalArgumentException("SENSING_EQUIPMENT_NOT_IN_NATIVE_EXPORT");
      }
      if (owner.getAttribute("type").startsWith("Plant/Piping.")) {
        return owner.getAttribute("id");
      }
      Element nozzle = object("PidInstrumentNozzle_" + safe(attachment.getPidTag()),
          "Plant/ProcessEquipment.InstrumentNozzle");
      setData(nozzle, "SubTagName", attachment.getPidTag() + "-SENSING");
      components(owner, "Nozzles").appendChild(nozzle);
      return nozzle.getAttribute("id");
    }

    /**
     * Creates a physical valve in a real native piping segment.
     *
     * @param element valve proposal
     * @param segment physical containing segment
     * @return actual native valve object
     */
    private Element valve(PidElement element, Element segment) {
      String family = element.getType() == PidElementType.CHECK_VALVE ? "CheckValve"
          : element.getType() == PidElementType.SAFETY_RELIEF_VALVE ? "SafetyValveOrFitting"
              : element.getType() == PidElementType.RUPTURE_DISK ? "RuptureDisc" : "OperatedValve";
      Element valve = object("PidValve_" + safe(element.getId()), "Plant/Piping." + family);
      setData(valve, "SafetyValveOrFitting".equals(family) || "RuptureDisc".equals(family) ? "PositionNumber"
          : "PipingComponentNumber", element.getTag());
      Element onHold = data(valve, "OnHold");
      Element holdValue = document.createElement("DataReference");
      holdValue.setAttribute("data", "Plant/Enumerations.OnHoldClassification.OnHold");
      onHold.appendChild(holdValue);
      Element nodes = components(valve, "Nodes");
      nodes.appendChild(object(valve.getAttribute("id") + "Inlet", "Plant/Piping.PipingNode"));
      nodes.appendChild(object(valve.getAttribute("id") + "Outlet", "Plant/Piping.PipingNode"));
      components(segment, "Items").appendChild(valve);
      byTag.put(element.getTag(), valve);
      nativeIds.put(element.getTag(), valve.getAttribute("id"));
      return valve;
    }

    /**
     * Inserts one two-port valve and preserves the complete original source-to-target material path.
     *
     * @param pipe original physical pipe
     * @param valve inserted component
     * @param inlet local inlet node suffix
     * @param outlet local outlet node suffix
     * @return downstream remainder of the original pipe
     */
    private Element insert(Element pipe, Element valve, String inlet, String outlet) {
      String targetItem = ref(pipe, "TargetItem");
      String targetNode = ref(pipe, "TargetNode");
      reference(pipe, "TargetItem", valve.getAttribute("id"));
      reference(pipe, "TargetNode", valve.getAttribute("id") + inlet);
      Element tail = object(pipe.getAttribute("id") + "After_" + valve.getAttribute("id"), "Plant/Piping.Pipe");
      reference(tail, "SourceItem", valve.getAttribute("id"));
      reference(tail, "SourceNode", valve.getAttribute("id") + outlet);
      reference(tail, "TargetItem", targetItem);
      reference(tail, "TargetNode", targetNode);
      pipe.getParentNode().appendChild(tail);
      return tail;
    }

    /**
     * Creates actual final-element actuation relationships without assuming actuator or fail-action design.
     *
     * @param element final-element proposal
     * @param valve actual native operated valve
     * @param segment actual containing valve segment
     */
    private void actuator(PidElement element, Element valve, Element segment) {
      function(element, false, "");
      Element function = functionOwners.get(element.getTag());
      Element actuating = object("PidActuating_" + safe(element.getId()), "Plant/Instrumentation.ActuatingFunction");
      setData(actuating, "ActuatingFunctionNumber", element.getTag());
      if (segment != null) {
        reference(actuating, "ActuatingLocation", segment.getAttribute("id"));
      }
      Element system = object("PidActuatingSystem_" + safe(element.getId()), "Plant/Instrumentation.ActuatingSystem");
      setData(system, "ActuatingSystemNumber", element.getTag());
      Element valveReference = object("PidOperatedValveReference_" + safe(element.getId()),
          "Plant/Instrumentation.OperatedValveReference");
      reference(valveReference, "Valve", valve.getAttribute("id"));
      components(system, "OperatedValveReference").appendChild(valveReference);
      components(plant, "ActuatingSystems").appendChild(system);
      reference(actuating, "Systems", system.getAttribute("id"));
      components(function, "ActuatingFunctions").appendChild(actuating);
      nativeIds.put(element.getTag(), valve.getAttribute("id"));
      functionalIds.put(element.getTag(), actuating.getAttribute("id"));
    }

    /**
     * Builds an explicitly declared auxiliary material route using owned tee nodes.
     *
     * @param element auxiliary valve or connector proposal
     * @param attachment controlled source/target inputs
     */
    private void auxiliary(PidElement element, Attachment attachment) {
      if (element.getType() == PidElementType.OFF_PAGE_CONNECTOR) {
        Element connector = byTag.get(element.getTag());
        if (connector == null) {
          throw new IllegalArgumentException("CONNECTOR_REQUIRES_AN_EXPLICIT_UPSTREAM_DEVICE_ROUTE");
        }
        nativeIds.put(element.getTag(), connector.getAttribute("id"));
        return;
      }
      if (element.getType() == PidElementType.SAMPLE_POINT) {
        throw new IllegalArgumentException("NO_REVIEWED_NATIVE_SAMPLE_POINT_PROJECTION");
      }
      auxiliaryRoute(Collections.singletonList(element), attachment);
    }

    /**
     * Validates and projects an explicitly ordered auxiliary series group as one atomic route.
     *
     * @param members all proposals declaring one physicalRouteId
     */
    private void projectSeries(List<PidElement> members) {
      try {
        TreeMap<Integer, PidElement> ordered = new TreeMap<Integer, PidElement>();
        Attachment first = projection.getAttachments().get(members.get(0).getTag());
        Set<String> plannedIds = new LinkedHashSet<String>();
        for (PidElement member : members) {
          Attachment current = projection.getAttachments().get(member.getTag());
          if (current.getKind() != Kind.AUXILIARY_ROUTE || current.getPhysicalRouteOrder() < 0) {
            throw new IllegalArgumentException("AUXILIARY_SERIES_REQUIRES_RESOLVED_ENDPOINTS_AND_EXPLICIT_ORDER");
          }
          if (member.getType() != PidElementType.CONTROL_VALVE && member.getType() != PidElementType.SHUTDOWN_VALVE
              && member.getType() != PidElementType.ISOLATION_VALVE) {
            throw new IllegalArgumentException("UNSUPPORTED_AUXILIARY_SERIES_DEVICE_TYPE");
          }
          for (String field : new String[] {"physicalSourceEquipmentTag", "physicalSourceStreamTag",
              "physicalSourceNozzleTag", "physicalSourcePortRole", "physicalTargetEquipmentTag",
              "physicalTargetStreamTag", "physicalTargetNozzleTag", "physicalTargetPortRole", "destinationTag",
              "designEvidenceReference"}) {
            if (!String.valueOf(first.toMap().get(field)).equals(String.valueOf(current.toMap().get(field)))) {
              throw new IllegalArgumentException("AUXILIARY_SERIES_ENDPOINT_OR_DESIGN_EVIDENCE_CONFLICT");
            }
          }
          if (ordered.put(Integer.valueOf(current.getPhysicalRouteOrder()), member) != null) {
            throw new IllegalArgumentException("DUPLICATE_EXPLICIT_AUXILIARY_SERIES_ORDER");
          }
          String id = "PidValve_" + safe(member.getId());
          if (!plannedIds.add(id) || byId.containsKey(id) || byTag.containsKey(member.getTag())) {
            throw new IllegalArgumentException("AUXILIARY_SERIES_NATIVE_ID_CONFLICT");
          }
        }
        auxiliaryRoute(new ArrayList<PidElement>(ordered.values()), first);
      } catch (IllegalArgumentException ex) {
        for (PidElement member : members) {
          unresolved.put(member.getTag(), ex.getMessage());
        }
      }
    }

    /**
     * Creates one material path containing only the explicitly ordered proposed devices.
     *
     * @param routeElements devices in declared material-flow order
     * @param attachment shared controlled route definition
     */
    private void auxiliaryRoute(List<PidElement> routeElements, Attachment attachment) {
      PidElement element = routeElements.get(0);
      // Resolve both endpoints before any mutation, so incomplete inputs cannot leave a stray tee or nozzle.
      if (attachment.getSourceNozzleTag().isEmpty()) {
        pipeForStream(attachment.getSourceEquipmentTag(), attachment.getSourceStreamTag());
      } else {
        validateDesignedNozzle(attachment.getSourceEquipmentTag(), attachment.getSourceNozzleTag(),
            attachment.getSourcePortRole(), attachment.getDesignEvidenceReference());
        requireUnusedDesignedPort(attachment.getSourceEquipmentTag(), attachment.getSourceNozzleTag(), "SourceItem");
      }
      if (!attachment.getTargetEquipmentTag().isEmpty()) {
        if (attachment.getTargetNozzleTag().isEmpty()) {
          pipeForStream(attachment.getTargetEquipmentTag(), attachment.getTargetStreamTag());
        } else {
          validateDesignedNozzle(attachment.getTargetEquipmentTag(), attachment.getTargetNozzleTag(),
              attachment.getTargetPortRole(), attachment.getDesignEvidenceReference());
          requireUnusedDesignedPort(attachment.getTargetEquipmentTag(), attachment.getTargetNozzleTag(), "TargetItem");
        }
      }
      if (!attachment.getSourceNozzleTag().isEmpty()
          && attachment.getSourceEquipmentTag().equals(attachment.getTargetEquipmentTag())
          && attachment.getSourceNozzleTag().equals(attachment.getTargetNozzleTag())) {
        throw new IllegalArgumentException("SAME_PROPOSED_NOZZLE_CANNOT_BE_BOTH_ROUTE_ENDPOINTS");
      }
      Element sourceEndpoint = attachment.getSourceNozzleTag().isEmpty()
          ? tee(attachment.getSourceEquipmentTag(), attachment.getSourceStreamTag(), "Source_" + safe(element.getId()))
          : designedNozzle(attachment.getSourceEquipmentTag(), attachment.getSourceNozzleTag(),
              attachment.getSourcePortRole(), attachment.getDesignEvidenceReference());
      String sourceNode = sourceEndpoint.getAttribute("id")
          + (attachment.getSourceNozzleTag().isEmpty() ? "Branch" : "Node");
      Element segment = object("PidAuxiliarySegment_" + safe(element.getId()), "Plant/Piping.PipingNetworkSegment");
      setData(segment, "SegmentNumber",
          attachment.getPhysicalRouteId().isEmpty() ? element.getTag() + "-ROUTE" : attachment.getPhysicalRouteId());
      Element system = object("PidAuxiliarySystem_" + safe(element.getId()), "Plant/Piping.PipingNetworkSystem");
      setData(system, "LineNumber",
          attachment.getPhysicalRouteId().isEmpty() ? element.getTag() + "-ROUTE" : attachment.getPhysicalRouteId());
      components(system, "Segments").appendChild(segment);
      components(plant, "PipingNetworkSystems").appendChild(system);
      List<Element> valves = new ArrayList<Element>();
      for (PidElement member : routeElements) {
        valves.add(valve(member, segment));
      }
      Element target;
      String targetNode;
      if (!attachment.getTargetEquipmentTag().isEmpty()) {
        target = attachment.getTargetNozzleTag().isEmpty()
            ? tee(attachment.getTargetEquipmentTag(), attachment.getTargetStreamTag(),
                "Target_" + safe(element.getId()))
            : designedNozzle(attachment.getTargetEquipmentTag(), attachment.getTargetNozzleTag(),
                attachment.getTargetPortRole(), attachment.getDesignEvidenceReference());
        targetNode = target.getAttribute("id") + (attachment.getTargetNozzleTag().isEmpty() ? "Branch" : "Node");
      } else {
        String destination = attachment.getDestinationTag();
        target = object("PidDestination_" + safe(element.getId()), "Plant/Piping.FlowOutPipeOffPageConnector");
        setData(target, "PipeConnectorNumber", destination);
        targetNode = target.getAttribute("id") + "Node";
        components(target, "Nodes").appendChild(object(targetNode, "Plant/Piping.PipingNode"));
        components(segment, "Items").appendChild(target);
        byTag.put(destination, target);
      }
      Element firstValve = valves.get(0);
      appendPipe(segment, "PidAuxiliaryIn_" + safe(element.getId()), sourceEndpoint.getAttribute("id"), sourceNode,
          firstValve.getAttribute("id"), firstValve.getAttribute("id") + "Inlet");
      for (int index = 0; index + 1 < valves.size(); index++) {
        Element upstream = valves.get(index);
        Element downstream = valves.get(index + 1);
        appendPipe(segment, "PidAuxiliarySeries_" + safe(element.getId()) + "_" + index, upstream.getAttribute("id"),
            upstream.getAttribute("id") + "Outlet", downstream.getAttribute("id"),
            downstream.getAttribute("id") + "Inlet");
      }
      Element lastValve = valves.get(valves.size() - 1);
      appendPipe(segment, "PidAuxiliaryOut_" + safe(element.getId()), lastValve.getAttribute("id"),
          lastValve.getAttribute("id") + "Outlet", target.getAttribute("id"), targetNode);
      reference(segment, "SourceItem", sourceEndpoint.getAttribute("id"));
      reference(segment, "SourceNode", sourceNode);
      reference(segment, "TargetItem", target.getAttribute("id"));
      reference(segment, "TargetNode", targetNode);
      for (int index = 0; index < valves.size(); index++) {
        if ("Plant/Piping.OperatedValve".equals(valves.get(index).getAttribute("type"))) {
          actuator(routeElements.get(index), valves.get(index), segment);
        }
      }
    }

    /**
     * Validates a new proposed owned nozzle without reusing an actual process-fluid port.
     *
     * @param ownerTag actual equipment owner
     * @param nozzleTag controlled proposed nozzle designation
     * @param role explicit PROCESS or UTILITY designation
     * @param evidence controlled source design reference
     */
    private void validateDesignedNozzle(String ownerTag, String nozzleTag, String role, String evidence) {
      Element owner = byTag.get(ownerTag);
      if (owner == null || !owner.getAttribute("type").startsWith("Plant/ProcessEquipment.")) {
        throw new IllegalArgumentException("PROPOSED_NOZZLE_REQUIRES_NATIVE_EQUIPMENT_OWNER");
      }
      if (nozzleTag.isEmpty() || !("UTILITY".equals(role) || "PROCESS".equals(role)) || evidence.trim().isEmpty()) {
        throw new IllegalArgumentException("CONTROLLED_PROPOSED_NOZZLE_DESIGN_INPUT_REQUIRED");
      }
      String key = ownerTag + ":" + nozzleTag;
      if (designedNozzles.containsKey(key)) {
        if (!(role + "\n" + evidence).equals(designedNozzleInputs.get(key))) {
          throw new IllegalArgumentException("CONFLICTING_PROPOSED_NOZZLE_ROLE_OR_DESIGN_EVIDENCE");
        }
        return;
      }
      Element nozzles = direct(owner, "Components", "Nozzles");
      if (nozzles != null) {
        NodeList items = nozzles.getElementsByTagName("Object");
        for (int i = 0; i < items.getLength(); i++) {
          if (nozzleTag.equals(value((Element) items.item(i), "SubTagName"))) {
            throw new IllegalArgumentException("PROPOSED_NOZZLE_TAG_CONFLICTS_WITH_AN_EXISTING_PROCESS_PORT");
          }
        }
      }
      if (byId.containsKey("PidDesignedNozzle_" + safe(ownerTag) + "_" + safe(nozzleTag))) {
        throw new IllegalArgumentException("PROPOSED_NOZZLE_ID_COLLISION");
      }
    }

    /**
     * Prevents accidental parallel utility bypasses when two proposed devices claim the same new nozzle.
     *
     * @param ownerTag equipment owner
     * @param nozzleTag proposed nozzle designation
     * @param direction SourceItem or TargetItem
     */
    private void requireUnusedDesignedPort(String ownerTag, String nozzleTag, String direction) {
      Element nozzle = designedNozzles.get(ownerTag + ":" + nozzleTag);
      if (nozzle == null) {
        return;
      }
      for (Element object : byId.values()) {
        if ("Plant/Piping.Pipe".equals(object.getAttribute("type"))
            && nozzle.getAttribute("id").equals(ref(object, direction))) {
          throw new IllegalArgumentException("MULTIPLE_PROPOSED_ROUTES_REQUIRE_EXPLICIT_SERIAL_OR_BRANCH_DESIGN");
        }
      }
    }

    /**
     * Creates or reuses one separately evidenced proposed equipment nozzle and its own piping node.
     *
     * @param ownerTag actual equipment owner
     * @param nozzleTag controlled new nozzle designation
     * @param role explicit UTILITY or PROCESS role
     * @param evidence controlled design reference
     * @return owned native ProcessNozzle that is never aliased to a source process pipe
     */
    private Element designedNozzle(String ownerTag, String nozzleTag, String role, String evidence) {
      validateDesignedNozzle(ownerTag, nozzleTag, role, evidence);
      String key = ownerTag + ":" + nozzleTag;
      if (designedNozzles.containsKey(key)) {
        return designedNozzles.get(key);
      }
      Element nozzle = object("PidDesignedNozzle_" + safe(ownerTag) + "_" + safe(nozzleTag),
          "Plant/ProcessEquipment.ProcessNozzle");
      setData(nozzle, "SubTagName", nozzleTag);
      components(nozzle, "Nodes").appendChild(object(nozzle.getAttribute("id") + "Node", "Plant/Piping.PipingNode"));
      components(byTag.get(ownerTag), "Nozzles").appendChild(nozzle);
      designedNozzles.put(key, nozzle);
      designedNozzleInputs.put(key, role + "\n" + evidence);
      Map<String, String> row = new LinkedHashMap<String, String>();
      row.put("equipmentTag", ownerTag);
      row.put("nozzleTag", nozzleTag);
      row.put("portRole", role);
      row.put("nativeId", nozzle.getAttribute("id"));
      row.put("designEvidenceReference", evidence);
      row.put("simulationStatus", "PROPOSED_NOZZLE_NOT_PRESENT_IN_RUNTIME_SOURCE");
      proposedOwnedNozzles.add(Collections.unmodifiableMap(row));
      return nozzle;
    }

    /**
     * Inserts a real three-node branch on a declared actual process line.
     *
     * @param owner equipment owning the declared stream
     * @param stream actual port stream name
     * @param suffix stable proposal-derived identity
     * @return connected native tee with one unused branch node
     */
    private Element tee(String owner, String stream, String suffix) {
      Element pipe = pipeForStream(owner, stream);
      Element segment = ancestor(pipe, "Plant/Piping.PipingNetworkSegment");
      Element tee = object("PidTee_" + suffix, "Plant/Piping.PipeTee");
      for (String node : new String[] {"Inlet", "Outlet", "Branch"}) {
        components(tee, "Nodes").appendChild(object(tee.getAttribute("id") + node, "Plant/Piping.PipingNode"));
      }
      components(segment, "Items").appendChild(tee);
      insert(pipe, tee, "Inlet", "Outlet");
      return tee;
    }

    /**
     * Finds one actual native pipe using both explicit equipment and stream identities.
     *
     * @param owner declared actual equipment
     * @param stream declared actual owned stream
     * @return unique actual pipe
     */
    private Element pipeForStream(String owner, String stream) {
      ProcessEquipmentInterface unit = process.getUnit(owner);
      if (unit == null) {
        throw new IllegalArgumentException("UNKNOWN_AUXILIARY_EQUIPMENT");
      }
      String direction = "";
      int index = -1;
      int matches = 0;
      for (String side : new String[] {"INLET", "OUTLET"}) {
        List<StreamInterface> ports = "INLET".equals(side) ? unit.getInletStreams() : unit.getOutletStreams();
        if (ports == null) {
          continue;
        }
        for (int i = 0; i < ports.size(); i++) {
          if (ports.get(i) != null && stream.equals(ports.get(i).getName())) {
            direction = side;
            index = i;
            matches++;
          }
        }
      }
      if (matches != 1) {
        throw new IllegalArgumentException("NO_UNAMBIGUOUS_AUXILIARY_SOURCE_PORT");
      }
      return pipe(owner, direction, index);
    }

    /**
     * Records one declared signal as native typed signal semantics or a visible unresolved link.
     *
     * @param link declared source and target P&amp;ID tags
     */
    private void signal(Map<String, String> link) {
      String sourceTag = link.get("sourceTag");
      String targetTag = link.get("targetTag");
      Map<String, String> evidence = new LinkedHashMap<String, String>(link);
      String source = functionalIds.get(sourceTag);
      String target = functionalIds.get(targetTag);
      PidElement sourceElement = null;
      PidElement targetElement = null;
      for (PidElement candidate : model.getElements()) {
        if (candidate.getTag().equals(sourceTag)) {
          sourceElement = candidate;
        }
        if (candidate.getTag().equals(targetTag)) {
          targetElement = candidate;
        }
      }
      if (sourceElement != null && targetElement != null && targetElement.getType() == PidElementType.OFF_PAGE_CONNECTOR
          && (sourceElement.getType() == PidElementType.SAFETY_RELIEF_VALVE
              || sourceElement.getType() == PidElementType.RUPTURE_DISK)
          && nativeIds.containsKey(sourceTag) && nativeIds.containsKey(targetTag)) {
        evidence.put("status", "PROJECTED_PHYSICAL_DISPOSAL_ROUTE");
      } else if (source == null || target == null) {
        evidence.put("status", "UNRESOLVED");
        evidence.put("reason", "NO_TYPED_FUNCTIONAL_ENDPOINTS");
      } else {
        Element signal = object("PidSignal_" + safe(sourceTag) + "_" + safe(targetTag),
            "Plant/Instrumentation.SignalLineFunction");
        reference(signal, "Source", source);
        reference(signal, "Target", target);
        components(functionOwners.get(sourceTag), "SignalConveyingFunctions").appendChild(signal);
        evidence.put("status", "PROJECTED");
        evidence.put("nativeId", signal.getAttribute("id"));
      }
      signalLinks.add(Collections.unmodifiableMap(evidence));
    }

    /**
     * Finds the actual native pipe at one actual equipment port.
     *
     * @param owner source equipment tag
     * @param direction actual INLET or OUTLET
     * @param index zero-based actual stream index
     * @return native pipe with exactly matching owned endpoint
     */
    private Element pipe(String owner, String direction, int index) {
      Element port = port(owner, direction, index);
      String endpoint = port.getAttribute("id");
      List<Element> candidates = new ArrayList<Element>();
      for (Element value : byId.values()) {
        if (!"Plant/Piping.Pipe".equals(value.getAttribute("type"))) {
          continue;
        }
        String key = "INLET".equals(direction) ? "TargetItem" : "SourceItem";
        if (endpoint.equals(ref(value, key))) {
          candidates.add(value);
        }
      }
      if (candidates.size() != 1) {
        throw new IllegalArgumentException("NO_UNAMBIGUOUS_NATIVE_PIPE_FOR_SOURCE_PORT");
      }
      return candidates.get(0);
    }

    /**
     * Locates native nozzles using source equipment and source port identities, not drawing order.
     *
     * @param owner actual source equipment tag
     * @param direction actual INLET or OUTLET
     * @param index zero-based actual port index
     * @return actual node-owning native object
     */
    private Element port(String owner, String direction, int index) {
      Element equipment = byTag.get(owner);
      if (equipment == null || index < 0) {
        throw new IllegalArgumentException("SOURCE_PORT_NOT_IN_NATIVE_EXPORT");
      }
      if (equipment.getAttribute("type").startsWith("Plant/Piping.")) {
        return equipment;
      }
      String suffix = "INLET".equals(direction) ? "Inlet" + (index == 0 ? "" : Integer.toString(index + 1))
          : "Outlet" + (index + 1);
      Element port = byId.get(equipment.getAttribute("id") + suffix);
      if (port == null) {
        throw new IllegalArgumentException("SOURCE_PORT_NOT_IN_NATIVE_EXPORT");
      }
      return port;
    }

    /**
     * Appends one fully owned native pipe.
     *
     * @param segment containing physical segment
     * @param id pipe identity
     * @param sourceItem actual source owner
     * @param sourceNode actual source node
     * @param targetItem actual target owner
     * @param targetNode actual target node
     */
    private void appendPipe(Element segment, String id, String sourceItem, String sourceNode, String targetItem,
        String targetNode) {
      Element pipe = object(id, "Plant/Piping.Pipe");
      reference(pipe, "SourceItem", sourceItem);
      reference(pipe, "SourceNode", sourceNode);
      reference(pipe, "TargetItem", targetItem);
      reference(pipe, "TargetNode", targetNode);
      components(segment, "Connections").appendChild(pipe);
    }

    /**
     * Creates a unique typed object, rejecting accidental repeated mutation.
     *
     * @param id controlled identity
     * @param type official native type
     * @return new object
     */
    private Element object(String id, String type) {
      if (byId.containsKey(id)) {
        throw new IllegalArgumentException("DUPLICATE_NATIVE_PROPOSAL_ID: " + id);
      }
      Element result = document.createElement("Object");
      result.setAttribute("id", id);
      result.setAttribute("type", type);
      byId.put(id, result);
      return result;
    }

    /**
     * Returns a direct composition holder.
     *
     * @param parent containing object
     * @param property official composition property
     * @return existing or new holder
     */
    private Element components(Element parent, String property) {
      Element existing = direct(parent, "Components", property);
      if (existing != null) {
        return existing;
      }
      Element result = document.createElement("Components");
      result.setAttribute("property", property);
      parent.appendChild(result);
      return result;
    }

    /**
     * Appends one typed data property.
     *
     * @param parent owning object
     * @param property official property
     * @return data holder
     */
    private Element data(Element parent, String property) {
      Element result = document.createElement("Data");
      result.setAttribute("property", property);
      parent.appendChild(result);
      return result;
    }

    /**
     * Sets one supported scalar string without duplicate single-valued properties.
     *
     * @param parent owner
     * @param property official property
     * @param value scalar value
     */
    private void setData(Element parent, String property, String value) {
      Element existing = direct(parent, "Data", property);
      if (existing != null) {
        parent.removeChild(existing);
      }
      Element string = document.createElement("String");
      string.setTextContent(value);
      data(parent, property).appendChild(string);
    }

    /**
     * Sets one actual object reference.
     *
     * @param parent owner
     * @param property official property
     * @param id target native object ID
     */
    private void reference(Element parent, String property, String id) {
      Element existing = direct(parent, "References", property);
      if (existing == null) {
        existing = document.createElement("References");
        existing.setAttribute("property", property);
        parent.appendChild(existing);
      }
      existing.setAttribute("objects", "#" + id);
    }
  }

  /**
   * Finds a direct property holder.
   *
   * @param parent owner
   * @param tag XML holder tag
   * @param property official property
   * @return holder or null
   */
  private static Element direct(Element parent, String tag, String property) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element && tag.equals(((Element) node).getTagName())
          && property.equals(((Element) node).getAttribute("property"))) {
        return (Element) node;
      }
    }
    return null;
  }

  /**
   * Reads an actual string-valued property.
   *
   * @param parent owner
   * @param property official property
   * @return string value or empty
   */
  private static String value(Element parent, String property) {
    Element holder = direct(parent, "Data", property);
    return holder == null ? "" : holder.getTextContent().trim();
  }

  /**
   * Reads one actual reference.
   *
   * @param parent owner
   * @param property official reference property
   * @return target ID without hash or empty
   */
  private static String ref(Element parent, String property) {
    Element holder = direct(parent, "References", property);
    return holder == null ? "" : holder.getAttribute("objects").replace("#", "");
  }

  /**
   * Finds the physical composition owner of a given type.
   *
   * @param child native child object
   * @param type desired official owner type
   * @return actual owner or null
   */
  private static Element ancestor(Element child, String type) {
    for (Node node = child.getParentNode(); node != null; node = node.getParentNode()) {
      if (node instanceof Element && type.equals(((Element) node).getAttribute("type"))) {
        return (Element) node;
      }
    }
    return null;
  }

  /**
   * Produces deterministic XML-ID-compatible proposal identities.
   *
   * @param value source identity
   * @return safe stable identity
   */
  private static String safe(String value) {
    return value.replaceAll("[^A-Za-z0-9_]", "_");
  }

  /**
   * Removes existing pretty-print whitespace so repeated serialization does not multiply it.
   *
   * @param parent XML subtree
   */
  private static void removeWhitespace(Node parent) {
    for (Node child = parent.getFirstChild(); child != null;) {
      Node next = child.getNextSibling();
      if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
        parent.removeChild(child);
      } else {
        removeWhitespace(child);
      }
      child = next;
    }
  }
}
