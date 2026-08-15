package neqsim.process.processmodel.diagram;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.model.EngineeringProvenance;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.graph.ProcessEdge;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessGraphBuilder;

/**
 * Adapts runnable process topology to the canonical, exchange-independent {@link EngineeringGraph} used by diagram and
 * DEXPI workflows.
 *
 * <p>
 * The adapter does not render a drawing and does not copy execution scheduling state. It creates stable plant, area,
 * equipment, port, and physical-connection identities that later DOT, SVG/PDF, and DEXPI exporters can consume. The
 * returned {@link Result} stores an immutable JSON snapshot and returns a defensive graph copy to every caller.
 * </p>
 */
public final class ProcessDiagramGraphAdapter {
  private ProcessDiagramGraphAdapter() {
  }

  /** Severity of a structured topology-adaptation diagnostic. */
  public enum Severity {
    /** Information about an intentional mapping or reconciliation. */
    INFO,
    /** A recoverable loss, ambiguity, or unsupported topology detail. */
    WARNING,
    /** A topology item that could not be represented safely. */
    ERROR
  }

  /** Immutable structured diagnostic produced while adapting process topology. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String area;
    private final String subject;

    private Diagnostic(Severity severity, String code, String message, String area, String subject) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.area = area == null ? "" : area;
      this.subject = subject == null ? "" : subject;
    }

    /** @return diagnostic severity */
    public Severity getSeverity() {
      return severity;
    }

    /** @return stable machine-readable diagnostic code */
    public String getCode() {
      return code;
    }

    /** @return human-readable diagnostic message */
    public String getMessage() {
      return message;
    }

    /** @return process area associated with the diagnostic, or an empty string */
    public String getArea() {
      return area;
    }

    /** @return equipment, connection, or other subject, or an empty string */
    public String getSubject() {
      return subject;
    }
  }

  /** Immutable canonical topology snapshot and its structured adaptation diagnostics. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String graphJson;
    private final String fingerprint;
    private final int nodeCount;
    private final int edgeCount;
    private final List<Diagnostic> diagnostics;

    private Result(EngineeringGraph graph, List<Diagnostic> diagnostics) {
      graphJson = graph.toJson();
      fingerprint = String.valueOf(graph.toMap().get("fingerprint"));
      nodeCount = graph.getNodes().size();
      edgeCount = graph.getEdges().size();
      this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
    }

    /**
     * Returns a defensive copy of the canonical engineering graph.
     *
     * @return reconstructed graph copy owned by the caller
     */
    public EngineeringGraph getGraph() {
      return EngineeringGraph.fromJson(graphJson);
    }

    /** @return deterministic canonical graph JSON */
    public String getGraphJson() {
      return graphJson;
    }

    /** @return SHA-256 fingerprint of the canonical graph content */
    public String getFingerprint() {
      return fingerprint;
    }

    /** @return number of canonical nodes */
    public int getNodeCount() {
      return nodeCount;
    }

    /** @return number of canonical relationships */
    public int getEdgeCount() {
      return edgeCount;
    }

    /** @return immutable structured diagnostic list */
    public List<Diagnostic> getDiagnostics() {
      return diagnostics;
    }

    /** @return true when no adaptation error was recorded */
    public boolean isComplete() {
      for (Diagnostic diagnostic : diagnostics) {
        if (diagnostic.getSeverity() == Severity.ERROR) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * Builds one canonical diagram-topology snapshot from a {@link ProcessSystem}.
   *
   * @param processSystem runnable process system
   * @param plantId persistent plant or project identifier
   * @param revision controlled source-model revision
   * @return immutable canonical snapshot and diagnostics
   */
  public static Result fromProcessSystem(ProcessSystem processSystem, String plantId, String revision) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    Builder builder = new Builder(plantId, revision, "PROCESS_SYSTEM");
    builder.addArea(areaName(processSystem), processSystem);
    builder.addLocalTopology();
    builder.addExplicitConnections();
    return builder.result();
  }

  /**
   * Builds one canonical diagram snapshot with selected current stream operating values.
   *
   * <p>
   * This opt-in overload preserves the topology-only output of the established three-argument method. Values are
   * captured only after a successful process run and are represented as unit-explicit, case-scoped calculation nodes
   * with simulation-result provenance and review-required status.
   * </p>
   *
   * @param processSystem process system; unsuccessful run state produces a diagnostic rather than values
   * @param plantId persistent plant or project identifier
   * @param revision controlled source-model revision
   * @param operatingCaseId stable operating-case identifier
   * @return immutable canonical snapshot and diagnostics
   */
  public static Result fromProcessSystem(ProcessSystem processSystem, String plantId, String revision,
      String operatingCaseId) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    Builder builder = new Builder(plantId, revision, "PROCESS_SYSTEM");
    builder.addArea(areaName(processSystem), processSystem);
    builder.addLocalTopology();
    builder.addExplicitConnections();
    builder.addOperatingCase(operatingCaseId);
    return builder.result();
  }

  /**
   * Builds one plant-wide canonical diagram-topology snapshot from all areas in a {@link ProcessModel}.
   *
   * @param processModel runnable multi-area process model
   * @param plantId persistent plant or project identifier
   * @param revision controlled source-model revision
   * @return immutable plant-wide canonical snapshot and diagnostics
   */
  public static Result fromProcessModel(ProcessModel processModel, String plantId, String revision) {
    if (processModel == null) {
      throw new IllegalArgumentException("processModel must not be null");
    }
    Builder builder = new Builder(plantId, revision, "PROCESS_MODEL");
    for (String areaName : processModel.getProcessSystemNames()) {
      ProcessSystem processSystem = processModel.get(areaName);
      if (processSystem != null) {
        builder.addArea(areaName, processSystem);
      }
    }
    builder.addLocalTopology();
    builder.addExplicitConnections();
    builder.addCrossAreaTopology();
    return builder.result();
  }

  /**
   * Builds one plant-wide canonical diagram snapshot with selected current stream operating values.
   *
   * <p>
   * One operating-case node owns the values captured across every successfully run area. An area that has not completed
   * successfully is retained topologically and reported through a structured diagnostic; stale values are not published
   * for that area.
   * </p>
   *
   * @param processModel multi-area process model; each unsuccessful area produces a diagnostic rather than values
   * @param plantId persistent plant or project identifier
   * @param revision controlled source-model revision
   * @param operatingCaseId stable plant-wide operating-case identifier
   * @return immutable plant-wide canonical snapshot and diagnostics
   */
  public static Result fromProcessModel(ProcessModel processModel, String plantId, String revision,
      String operatingCaseId) {
    if (processModel == null) {
      throw new IllegalArgumentException("processModel must not be null");
    }
    Builder builder = new Builder(plantId, revision, "PROCESS_MODEL");
    for (String areaName : processModel.getProcessSystemNames()) {
      ProcessSystem processSystem = processModel.get(areaName);
      if (processSystem != null) {
        builder.addArea(areaName, processSystem);
      }
    }
    builder.addLocalTopology();
    builder.addExplicitConnections();
    builder.addCrossAreaTopology();
    builder.addOperatingCase(operatingCaseId);
    return builder.result();
  }

  private static final class ElementReference {
    private final String areaName;
    private final String areaNodeId;
    private final ProcessEquipmentInterface equipment;
    private final String nodeId;
    private final String externalKey;

    private ElementReference(String areaName, String areaNodeId, ProcessEquipmentInterface equipment, String nodeId,
        String externalKey) {
      this.areaName = areaName;
      this.areaNodeId = areaNodeId;
      this.equipment = equipment;
      this.nodeId = nodeId;
      this.externalKey = externalKey;
    }
  }

  private static final class AreaReference {
    private final String name;
    private final String nodeId;
    private final ProcessSystem processSystem;
    private final Map<String, ElementReference> elementsByName = new LinkedHashMap<String, ElementReference>();

    private AreaReference(String name, String nodeId, ProcessSystem processSystem) {
      this.name = name;
      this.nodeId = nodeId;
      this.processSystem = processSystem;
    }
  }

  private static final class Builder {
    private final String plantId;
    private final EngineeringGraph graph;
    private final String projectNodeId;
    private final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    private final List<AreaReference> areas = new ArrayList<AreaReference>();
    private final Map<ProcessEquipmentInterface, ElementReference> elements = new IdentityHashMap<ProcessEquipmentInterface, ElementReference>();
    private final Set<String> representedEndpointPairs = new LinkedHashSet<String>();
    private final Map<String, String> resolvedExternalKeys = new LinkedHashMap<String, String>();

    private Builder(String plantId, String revision, String sourceType) {
      this.plantId = requireText(plantId, "plantId");
      graph = new EngineeringGraph(this.plantId, requireText(revision, "revision"));
      projectNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.PROJECT, this.plantId);
      graph.addNode(new EngineeringNode(projectNodeId, EngineeringNode.Kind.PROJECT, this.plantId, this.plantId)
          .putProperty("source", sourceType).putProperty("engineeringState", "CALCULATED")
          .putProperty("approvalStatus", "REVIEW_REQUIRED")
          .addProvenance(new EngineeringProvenance("SIMULATION_MODEL", this.plantId)
              .setMethod("Neutral process diagram topology adaptation").setApprovalStatus("REVIEW_REQUIRED")));
    }

    private void addArea(String requestedName, ProcessSystem processSystem) {
      String name = requireText(requestedName, "areaName");
      String areaExternalKey = resolveExternalKey(EngineeringNode.Kind.AREA, plantId + "/" + name, name);
      String areaNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.AREA, areaExternalKey);
      graph.addNode(new EngineeringNode(areaNodeId, EngineeringNode.Kind.AREA, areaExternalKey, name)
          .putProperty("areaName", name).putProperty("processSystemName", processSystem.getName())
          .putProperty("source", "PROCESS_SYSTEM")
          .addProvenance(new EngineeringProvenance("PROCESS_SYSTEM", processSystem.getName())
              .setMethod("ProcessModel area hierarchy")));
      addEdgeIfAbsent(EngineeringEdge.Kind.CONTAINS, projectNodeId, areaNodeId, "processArea");
      AreaReference area = new AreaReference(name, areaNodeId, processSystem);
      areas.add(area);
      for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
        if (equipment == null || !hasText(equipment.getName())) {
          diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_TOPOLOGY_UNNAMED_ELEMENT",
              "Skipped a process element without a stable name", name, ""));
          continue;
        }
        EngineeringNode.Kind kind = equipment instanceof StreamInterface ? EngineeringNode.Kind.LINE
            : EngineeringNode.Kind.EQUIPMENT;
        String externalKey = resolveExternalKey(kind, plantId + "/" + name + "/" + equipment.getName(), name);
        String nodeId = EngineeringIds.nodeId(kind, externalKey);
        graph.addNode(new EngineeringNode(nodeId, kind, externalKey, equipment.getName()).putProperty("areaName", name)
            .putProperty("equipmentName", equipment.getName()).putProperty("javaClass", equipment.getClass().getName())
            .putProperty("physicalCategory", kind == EngineeringNode.Kind.LINE ? "LINE" : "EQUIPMENT")
            .putProperty("source", "PROCESS_SYSTEM")
            .addProvenance(new EngineeringProvenance("SIMULATION_MODEL", processSystem.getName())
                .setMethod("ProcessSystem equipment inventory")));
        addEdgeIfAbsent(EngineeringEdge.Kind.CONTAINS, areaNodeId, nodeId, "processElement");
        ElementReference reference = new ElementReference(name, areaNodeId, equipment, nodeId, externalKey);
        elements.put(equipment, reference);
        area.elementsByName.put(equipment.getName(), reference);
      }
      if (area.elementsByName.isEmpty()) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_TOPOLOGY_EMPTY_AREA",
            "Process area contains no named process elements", name, name));
      }
    }

    private void addLocalTopology() {
      for (AreaReference area : areas) {
        ProcessGraph processGraph = ProcessGraphBuilder.buildGraph(area.processSystem);
        for (ProcessEdge edge : processGraph.getEdges()) {
          ElementReference source = elements.get(edge.getSource().getEquipment());
          ElementReference target = elements.get(edge.getTarget().getEquipment());
          if (source == null || target == null) {
            diagnostics.add(new Diagnostic(Severity.ERROR, "DIAGRAM_TOPOLOGY_UNRESOLVED_GRAPH_ENDPOINT",
                "Process graph edge references an element outside the canonical area inventory", area.name,
                edge.getName()));
            continue;
          }
          ProcessConnection.ConnectionType type = connectionType(edge);
          if (type == null) {
            diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_TOPOLOGY_UNSUPPORTED_GRAPH_EDGE",
                "Skipped a process graph edge without material, energy, or signal semantics", area.name,
                edge.getName()));
            continue;
          }
          String carriedName = carriedName(edge);
          addConnection(source, target, type, carriedName,
              portName(source.equipment, edge.getStream(), false, edge.getIndex()),
              portName(target.equipment, edge.getStream(), true, edge.getIndex()), "PROCESS_GRAPH", false,
              edge.isRecycle());
        }
      }
    }

    private void addExplicitConnections() {
      for (AreaReference area : areas) {
        for (ProcessConnection connection : area.processSystem.getConnections()) {
          ElementReference source = area.elementsByName.get(connection.getSourceEquipment());
          ElementReference target = area.elementsByName.get(connection.getTargetEquipment());
          if (source == null || target == null) {
            diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_TOPOLOGY_UNRESOLVED_EXPLICIT_ENDPOINT",
                "Explicit process connection references an unknown area element", area.name, connection.toString()));
            continue;
          }
          String pair = endpointPair(connection.getType(), source.nodeId, target.nodeId);
          if (connection.getType() == ProcessConnection.ConnectionType.MATERIAL
              && representedEndpointPairs.contains(pair)) {
            diagnostics.add(new Diagnostic(Severity.INFO, "DIAGRAM_TOPOLOGY_EXPLICIT_MATERIAL_RECONCILED",
                "Explicit material metadata was reconciled with stream-discovered topology", area.name,
                connection.toString()));
            continue;
          }
          addConnection(source, target, connection.getType(), "", connection.getSourcePort(),
              connection.getTargetPort(), "PROCESS_CONNECTION", false, false);
        }
      }
    }

    private void addCrossAreaTopology() {
      Map<StreamInterface, List<ElementReference>> producers = new IdentityHashMap<StreamInterface, List<ElementReference>>();
      Map<StreamInterface, List<ElementReference>> consumers = new IdentityHashMap<StreamInterface, List<ElementReference>>();
      Map<StreamInterface, Boolean> seenStreams = new IdentityHashMap<StreamInterface, Boolean>();
      List<StreamInterface> orderedStreams = new ArrayList<StreamInterface>();
      for (AreaReference area : areas) {
        for (ElementReference element : area.elementsByName.values()) {
          collectEndpoints(producers, safeStreams(element.equipment, false), element, seenStreams, orderedStreams);
          collectEndpoints(consumers, safeStreams(element.equipment, true), element, seenStreams, orderedStreams);
        }
      }
      for (StreamInterface stream : orderedStreams) {
        List<ElementReference> sourceReferences = producers.get(stream);
        List<ElementReference> targets = consumers.get(stream);
        if (sourceReferences == null) {
          continue;
        }
        if (targets == null) {
          continue;
        }
        List<ElementReference> sources = effectiveSources(sourceReferences);
        for (ElementReference source : sources) {
          for (ElementReference target : targets) {
            if (source == target || source.areaName.equals(target.areaName)) {
              continue;
            }
            addConnection(source, target, ProcessConnection.ConnectionType.MATERIAL, safeStreamName(stream),
                portName(source.equipment, stream, false, 0), portName(target.equipment, stream, true, 0),
                "STREAM_IDENTITY_CROSS_AREA", true, false);
          }
        }
      }
    }

    private void addOperatingCase(String requestedCaseId) {
      String caseId = requireText(requestedCaseId, "operatingCaseId");
      String caseExternalKey = resolveExternalKey(EngineeringNode.Kind.DESIGN_CASE,
          plantId + "/operating-case/" + caseId, "");
      String caseNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.DESIGN_CASE, caseExternalKey);
      EngineeringNode caseNode = new EngineeringNode(caseNodeId, EngineeringNode.Kind.DESIGN_CASE, caseExternalKey,
          caseId).putProperty("caseId", caseId).putProperty("type", "OPERATING")
          .putProperty("engineeringState", "CALCULATED").putProperty("approvalStatus", "REVIEW_REQUIRED")
          .putProperty("simulationStatus", "CALCULATED").addProvenance(
              new EngineeringProvenance("SIMULATION_CASE", caseId).setMethod("Current process operating-state capture")
                  .setDesignCaseId(caseId).setApprovalStatus("REVIEW_REQUIRED"));
      graph.addNode(caseNode);
      addEdgeIfAbsent(EngineeringEdge.Kind.CONTAINS, projectNodeId, caseNodeId, "operatingCase");

      for (AreaReference area : areas) {
        if (!area.processSystem.getRunStatus().isSuccess()) {
          caseNode.putProperty("simulationStatus", "INCOMPLETE");
          diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_OPERATING_CASE_NOT_SUCCESSFUL",
              "Operating values were not captured because the process area has no successful completed run", area.name,
              caseId));
          continue;
        }
        for (ElementReference element : area.elementsByName.values()) {
          if (element.equipment instanceof StreamInterface) {
            addStreamOperatingValues(element, (StreamInterface) element.equipment, caseId, caseNodeId);
          }
        }
      }
    }

    private void addStreamOperatingValues(ElementReference element, StreamInterface stream, String caseId,
        String caseNodeId) {
      try {
        addOperatingValue(element, caseId, caseNodeId, "temperature", stream.getTemperature("K"), "K",
            "THERMODYNAMIC_ABSOLUTE");
      } catch (RuntimeException exception) {
        operatingValueUnavailable(element, caseId, "temperature", exception);
      }
      try {
        addOperatingValue(element, caseId, caseNodeId, "pressure", stream.getPressure("bara"), "bara", "ABSOLUTE");
      } catch (RuntimeException exception) {
        operatingValueUnavailable(element, caseId, "pressure", exception);
      }
      try {
        addOperatingValue(element, caseId, caseNodeId, "massFlow", stream.getFlowRate("kg/sec"), "kg/s", "MASS");
      } catch (RuntimeException exception) {
        operatingValueUnavailable(element, caseId, "massFlow", exception);
      }
      try {
        addOperatingValue(element, caseId, caseNodeId, "specificEnthalpy", stream.getThermoSystem().getEnthalpy("J/kg"),
            "J/kg", "MASS_SPECIFIC");
      } catch (RuntimeException exception) {
        operatingValueUnavailable(element, caseId, "specificEnthalpy", exception);
      }
    }

    private void addOperatingValue(ElementReference element, String caseId, String caseNodeId, String quantity,
        double value, String unit, String quantityBasis) {
      if (!Double.isFinite(value)) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_OPERATING_VALUE_NONFINITE",
            "A non-finite simulation result was excluded from the canonical operating case", element.areaName,
            element.equipment.getName() + "/" + quantity));
        return;
      }
      String requestedExternalKey = element.externalKey + "/operating-case/" + caseId + "/" + quantity;
      String externalKey = resolveExternalKey(EngineeringNode.Kind.CALCULATION, requestedExternalKey, element.areaName);
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.CALCULATION, externalKey);
      graph.addNode(new EngineeringNode(nodeId, EngineeringNode.Kind.CALCULATION, externalKey,
          element.equipment.getName() + " " + quantity).putProperty("subjectNodeId", element.nodeId)
          .putProperty("subjectName", element.equipment.getName()).putProperty("areaName", element.areaName)
          .putProperty("quantity", quantity).putProperty("quantityBasis", quantityBasis)
          .putProperty("resultValue", Double.valueOf(value)).putProperty("resultUnit", unit)
          .putProperty("designCaseId", caseId).putProperty("status", "CALCULATED")
          .putProperty("engineeringState", "CALCULATED").putProperty("approvalStatus", "REVIEW_REQUIRED").addProvenance(
              new EngineeringProvenance("SIMULATION_RESULT", element.areaName + "/" + element.equipment.getName())
                  .setMethod("Current ProcessSystem stream operating result snapshot").setDesignCaseId(caseId)
                  .setApprovalStatus("REVIEW_REQUIRED")));
      addEdgeIfAbsent(EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "operatingValue");
      addEdgeIfAbsent(EngineeringEdge.Kind.GOVERNS, nodeId, element.nodeId, quantity);
      addEdgeIfAbsent(EngineeringEdge.Kind.GENERATED_FROM, nodeId, caseNodeId, "operatingCase");
    }

    private void operatingValueUnavailable(ElementReference element, String caseId, String quantity,
        RuntimeException exception) {
      diagnostics
          .add(new Diagnostic(Severity.WARNING, "DIAGRAM_OPERATING_VALUE_UNAVAILABLE",
              "A simulation result could not be captured for operating case " + caseId + ": "
                  + exception.getClass().getSimpleName(),
              element.areaName, element.equipment.getName() + "/" + quantity));
    }

    private void addConnection(ElementReference source, ElementReference target, ProcessConnection.ConnectionType type,
        String carriedName, String sourcePortName, String targetPortName, String discovery, boolean crossArea,
        boolean recycle) {
      String normalizedSourcePort = fallbackPort(sourcePortName, "outlet");
      String normalizedTargetPort = fallbackPort(targetPortName, "inlet");
      String sourceEndpointId = ensureEndpoint(source, type, normalizedSourcePort, "OUTLET", carriedName);
      String targetEndpointId = ensureEndpoint(target, type, normalizedTargetPort, "INLET", carriedName);
      String requestedConnectionExternalKey = source.externalKey + "." + normalizedSourcePort + "->"
          + target.externalKey + "." + normalizedTargetPort + "[" + type.name() + "]"
          + (hasText(carriedName) ? "/" + carriedName : "");
      EngineeringNode.Kind connectionKind = connectionNodeKind(type);
      String connectionExternalKey = resolveExternalKey(connectionKind, requestedConnectionExternalKey,
          source.areaName + "->" + target.areaName);
      String connectionId = EngineeringIds.nodeId(connectionKind, connectionExternalKey);
      if (graph.getNode(connectionId) != null) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_TOPOLOGY_DUPLICATE_CONNECTION_COLLAPSED",
            "Multiple connection declarations resolved to the same type and port endpoints; one canonical connection was retained",
            source.areaName, requestedConnectionExternalKey));
        return;
      }
      EngineeringNode connectionNode = new EngineeringNode(connectionId, connectionKind, connectionExternalKey,
          hasText(carriedName) ? carriedName : source.equipment.getName() + " to " + target.equipment.getName())
          .putProperty("connectionType", type.name()).putProperty("sourceEndpointId", sourceEndpointId)
          .putProperty("targetEndpointId", targetEndpointId).putProperty("sourceEquipment", source.equipment.getName())
          .putProperty("targetEquipment", target.equipment.getName()).putProperty("sourceArea", source.areaName)
          .putProperty("targetArea", target.areaName).putProperty("sourcePort", normalizedSourcePort)
          .putProperty("targetPort", normalizedTargetPort).putProperty("carriedObjectName", carriedName)
          .putProperty("crossArea", Boolean.valueOf(crossArea)).putProperty("recycle", Boolean.valueOf(recycle))
          .putProperty("discovery", discovery).addProvenance(new EngineeringProvenance(discovery, connectionExternalKey)
              .setMethod("Canonical process diagram connection adaptation"));
      graph.addNode(connectionNode);
      String ownerId = crossArea ? projectNodeId : source.areaNodeId;
      addEdgeIfAbsent(EngineeringEdge.Kind.CONTAINS, ownerId, connectionId, "physicalConnection");
      EngineeringEdge.Kind flowKind = flowEdgeKind(type);
      addEdgeIfAbsent(flowKind, sourceEndpointId, connectionId, "source");
      addEdgeIfAbsent(flowKind, connectionId, targetEndpointId, "target");
      addEdgeIfAbsent(EngineeringEdge.Kind.CONNECTS_TO, source.nodeId, target.nodeId, connectionId);
      representedEndpointPairs.add(endpointPair(type, source.nodeId, target.nodeId));
    }

    private String ensureEndpoint(ElementReference owner, ProcessConnection.ConnectionType type, String portName,
        String direction, String carriedName) {
      EngineeringNode.Kind kind = type == ProcessConnection.ConnectionType.MATERIAL
          && graph.getNode(owner.nodeId).getKind() == EngineeringNode.Kind.EQUIPMENT ? EngineeringNode.Kind.NOZZLE
              : EngineeringNode.Kind.PORT;
      String endpointExternalKey = resolveExternalKey(kind,
          owner.externalKey + "." + portName + "[" + type.name() + "]", owner.areaName);
      String endpointId = EngineeringIds.nodeId(kind, endpointExternalKey);
      EngineeringNode endpoint = graph.getNode(endpointId);
      if (endpoint == null) {
        graph.addNode(new EngineeringNode(endpointId, kind, endpointExternalKey, portName)
            .putProperty("ownerNodeId", owner.nodeId).putProperty("areaName", owner.areaName)
            .putProperty("equipmentName", owner.equipment.getName()).putProperty("portName", portName)
            .putProperty("direction", direction).putProperty("connectionType", type.name())
            .putProperty("carriedObjectName", carriedName)
            .addProvenance(new EngineeringProvenance("SIMULATION_TOPOLOGY", endpointExternalKey)
                .setMethod("Derived process equipment port")));
      } else if (!direction.equals(endpoint.getProperties().get("direction"))) {
        endpoint.putProperty("direction", "BIDIRECTIONAL").putProperty("directionConflict", Boolean.TRUE);
        diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_TOPOLOGY_PORT_DIRECTION_CONFLICT",
            "A derived endpoint is used as both inlet and outlet", owner.areaName, endpointExternalKey));
      }
      addEdgeIfAbsent(EngineeringEdge.Kind.HAS_PORT, owner.nodeId, endpointId, portName);
      return endpointId;
    }

    private String resolveExternalKey(EngineeringNode.Kind kind, String requestedExternalKey, String areaName) {
      String lookupKey = kind.name() + "|" + requestedExternalKey;
      String resolved = resolvedExternalKeys.get(lookupKey);
      if (resolved != null) {
        return resolved;
      }
      String nodeId = EngineeringIds.nodeId(kind, requestedExternalKey);
      EngineeringNode existing = graph.getNode(nodeId);
      if (existing == null || existing.getKind() == kind && existing.getExternalKey().equals(requestedExternalKey)) {
        resolvedExternalKeys.put(lookupKey, requestedExternalKey);
        return requestedExternalKey;
      }
      String disambiguated = requestedExternalKey + "/canonical-disambiguator-" + shortHash(lookupKey);
      resolvedExternalKeys.put(lookupKey, disambiguated);
      diagnostics.add(new Diagnostic(Severity.WARNING, "DIAGRAM_TOPOLOGY_CANONICAL_ID_COLLISION",
          "Canonicalized names collided; the external identity received a stable content-hash suffix", areaName,
          requestedExternalKey));
      return disambiguated;
    }

    private void addEdgeIfAbsent(EngineeringEdge.Kind kind, String sourceId, String targetId, String role) {
      String edgeId = EngineeringIds.edgeId(kind, sourceId, targetId, role);
      if (!graph.getEdges().containsKey(edgeId)) {
        graph.addEdge(new EngineeringEdge(edgeId, sourceId, targetId, kind, role));
      }
    }

    private Result result() {
      return new Result(graph, diagnostics);
    }
  }

  private static ProcessConnection.ConnectionType connectionType(ProcessEdge edge) {
    if (edge.getEdgeType() == ProcessEdge.EdgeType.ENERGY || edge.getEnergyStream() != null) {
      return ProcessConnection.ConnectionType.ENERGY;
    }
    if (edge.getEdgeType() == ProcessEdge.EdgeType.SIGNAL) {
      return ProcessConnection.ConnectionType.SIGNAL;
    }
    if (edge.getEdgeType() == ProcessEdge.EdgeType.MATERIAL || edge.getEdgeType() == ProcessEdge.EdgeType.RECYCLE
        || edge.getStream() != null) {
      return ProcessConnection.ConnectionType.MATERIAL;
    }
    return null;
  }

  private static EngineeringNode.Kind connectionNodeKind(ProcessConnection.ConnectionType type) {
    if (type == ProcessConnection.ConnectionType.SIGNAL) {
      return EngineeringNode.Kind.SIGNAL_CONNECTION;
    }
    if (type == ProcessConnection.ConnectionType.ENERGY) {
      return EngineeringNode.Kind.ENERGY_CONNECTION;
    }
    return EngineeringNode.Kind.PIPE_SEGMENT;
  }

  private static EngineeringEdge.Kind flowEdgeKind(ProcessConnection.ConnectionType type) {
    if (type == ProcessConnection.ConnectionType.SIGNAL) {
      return EngineeringEdge.Kind.SIGNAL_FLOW;
    }
    if (type == ProcessConnection.ConnectionType.ENERGY) {
      return EngineeringEdge.Kind.ENERGY_FLOW;
    }
    return EngineeringEdge.Kind.PROCESS_FLOW;
  }

  private static String endpointPair(ProcessConnection.ConnectionType type, String sourceId, String targetId) {
    return type.name() + "|" + sourceId + "|" + targetId;
  }

  private static String carriedName(ProcessEdge edge) {
    if (edge.getStream() != null) {
      return safeStreamName(edge.getStream());
    }
    if (edge.getEnergyStream() != null && hasText(edge.getEnergyStream().getName())) {
      return edge.getEnergyStream().getName();
    }
    return edge.getName() == null ? "" : edge.getName();
  }

  private static String portName(ProcessEquipmentInterface equipment, StreamInterface stream, boolean inlet,
      int fallbackIndex) {
    String prefix = inlet ? "inlet" : "outlet";
    if (stream == null) {
      return prefix + "-" + (fallbackIndex + 1);
    }
    List<StreamInterface> streams = safeStreams(equipment, inlet);
    for (int index = 0; index < streams.size(); index++) {
      if (streams.get(index) == stream) {
        return streams.size() == 1 ? prefix : prefix + "-" + (index + 1);
      }
    }
    String streamName = safeStreamName(stream);
    return hasText(streamName) ? prefix + "-" + EngineeringIds.canonical(streamName)
        : prefix + "-" + (fallbackIndex + 1);
  }

  private static List<StreamInterface> safeStreams(ProcessEquipmentInterface equipment, boolean inlet) {
    try {
      List<StreamInterface> streams = inlet ? equipment.getInletStreams() : equipment.getOutletStreams();
      return streams == null ? Collections.<StreamInterface>emptyList() : streams;
    } catch (Exception exception) {
      return Collections.emptyList();
    }
  }

  private static void collectEndpoints(Map<StreamInterface, List<ElementReference>> endpoints,
      List<StreamInterface> streams, ElementReference element, Map<StreamInterface, Boolean> seenStreams,
      List<StreamInterface> orderedStreams) {
    for (StreamInterface stream : streams) {
      if (stream == null) {
        continue;
      }
      if (!seenStreams.containsKey(stream)) {
        seenStreams.put(stream, Boolean.TRUE);
        orderedStreams.add(stream);
      }
      List<ElementReference> references = endpoints.get(stream);
      if (references == null) {
        references = new ArrayList<ElementReference>();
        endpoints.put(stream, references);
      }
      if (!references.contains(element)) {
        references.add(element);
      }
    }
  }

  private static List<ElementReference> effectiveSources(List<ElementReference> sources) {
    List<ElementReference> equipmentSources = new ArrayList<ElementReference>();
    for (ElementReference source : sources) {
      if (!(source.equipment instanceof StreamInterface)) {
        equipmentSources.add(source);
      }
    }
    return equipmentSources.isEmpty() ? sources : equipmentSources;
  }

  private static String safeStreamName(StreamInterface stream) {
    try {
      return stream.getName() == null ? "" : stream.getName();
    } catch (Exception exception) {
      return "";
    }
  }

  private static String fallbackPort(String value, String fallback) {
    return hasText(value) ? value.trim() : fallback;
  }

  private static String areaName(ProcessSystem processSystem) {
    return hasText(processSystem.getName()) ? processSystem.getName() : "Process Area";
  }

  private static String shortHash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (int index = 0; index < 4; index++) {
        result.append(String.format("%02x", hash[index] & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private static String requireText(String value, String name) {
    if (!hasText(value)) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
